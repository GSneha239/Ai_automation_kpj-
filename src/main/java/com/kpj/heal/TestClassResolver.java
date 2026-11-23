package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Full-project mode support - builds the testId -&gt; fully-qualified-test-class-name map by scanning every
 * {@code .java} file under {@code src/test/java/com/kpj/tests} for its {@code super("testId")} call. This is
 * the exact REVERSE of what a single-test invocation already does (class name given -&gt; parse the file -&gt;
 * extract testId): here we start from a testId found in a TSV and need to find which class it belongs to,
 * since {@code CodeLocator} can only work from a class name.
 *
 * <p>Read-only. Never writes anything.</p>
 */
public class TestClassResolver {

    private static final Path TEST_ROOT = Paths.get("src/test/java/com/kpj/tests");
    private static final Pattern SUPER_CALL = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern PACKAGE_DECL = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** testId -> fully-qualified class name, for every test source file that declares one via super(...).
     *  If two files somehow declare the same testId, the first one found wins (best-effort; this codebase's
     *  own convention is one testId per test class). */
    /** Test seam: when non-null, test classes are looked up under this folder instead of {@code src/test/java/com/kpj/tests}.
     *  Always null in production, so behaviour is unchanged; synthetic tests point it at a temp folder. */
    static volatile Path rootOverride = null;

    public Map<String, String> resolveAll() {
        Map<String, String> map = new LinkedHashMap<>();
        Path root = rootOverride != null ? rootOverride : TEST_ROOT;
        if (!Files.isDirectory(root)) return map;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        } catch (IOException e) {
            return map;
        }
        for (Path f : files) {
            try {
                String src = Files.readString(f);
                Matcher sm = SUPER_CALL.matcher(src);
                if (!sm.find()) continue;
                String testId = sm.group(1);
                String fqcn = classNameFor(f, src);
                map.putIfAbsent(testId, fqcn);
            } catch (IOException ignore) { /* unreadable file - skip, best-effort */ }
        }
        return map;
    }

    /** Derives a fully-qualified class name from a test source file's own package declaration + file name.
     *  Shared with {@code SelfHealingAgent} (e.g. for impacted-regression-test lookups) so this "path/source
     *  text -> FQCN" logic exists in exactly one place. */
    public static String classNameFor(Path javaFile) throws IOException {
        return classNameFor(javaFile, Files.readString(javaFile));
    }

    private static String classNameFor(Path javaFile, String src) {
        Matcher pm = PACKAGE_DECL.matcher(src);
        String pkg = pm.find() ? pm.group(1) : null;
        String simple = javaFile.getFileName().toString().replaceFirst("\\.java$", "");
        return pkg != null ? pkg + "." + simple : simple;
    }
}
