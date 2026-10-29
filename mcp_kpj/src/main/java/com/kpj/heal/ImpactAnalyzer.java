package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Requirement 4 - analysis-only dependency/impact check: finds which test classes reference (instantiate) a
 * given Page Object, so the human sees the blast radius of a proposed change BEFORE approving it. This is
 * display/confidence input ONLY - it never expands what gets patched. {@link PatchApplier} only ever writes
 * the ONE file {@link CodeLocator} already identified, regardless of how many dependents exist.
 */
public class ImpactAnalyzer {

    private static final Path TEST_ROOT = Paths.get("src/test/java/com/kpj/tests");

    /** Test seam: when non-null, dependents are searched under this root instead of the real test tree. Always null in
     *  production, so behaviour is unchanged; tests point it at a temp folder of synthetic dependents. */
    static volatile Path testRootOverride = null;

    public static class Result {
        public final List<String> dependentTestFiles;
        /** Subset of dependents whose class-level doc comment says "read-only" - the only ones this
         *  framework is willing to treat as safe to auto-verify post-patch (see requirement 6). */
        public final List<String> readOnlyDependents;

        Result(List<String> dependentTestFiles, List<String> readOnlyDependents) {
            this.dependentTestFiles = dependentTestFiles;
            this.readOnlyDependents = readOnlyDependents;
        }

        public int dependentCount() { return dependentTestFiles.size(); }
    }

    public Result analyze(Path pageObjectFile) {
        List<String> dependents = new ArrayList<>();
        List<String> readOnly = new ArrayList<>();
        String simpleName = pageObjectFile.getFileName().toString().replaceFirst("\\.java$", "");
        Pattern usage = Pattern.compile("\\bnew\\s+(?:[\\w.]+\\.)?" + Pattern.quote(simpleName) + "\\s*\\(");

        Path root = testRootOverride != null ? testRootOverride : TEST_ROOT;
        if (!Files.isDirectory(root)) return new Result(dependents, readOnly);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        } catch (IOException e) {
            return new Result(dependents, readOnly);
        }
        for (Path f : files) {
            try {
                String src = Files.readString(f);
                if (!usage.matcher(src).find()) continue;
                dependents.add(f.toString());
                if (declaredReadOnly(src)) readOnly.add(f.toString());
            } catch (IOException ignore) { }
        }
        return new Result(dependents, readOnly);
    }

    private static final Pattern CLASS_DECL = Pattern.compile("(?m)^[ \\t]*(?:(?:public|protected|private|abstract|final|static)\\s+)*class\\s+\\w+");
    /** Case-sensitive on purpose: the declaration starts a sentence/paragraph; a wrapped lower-case "read-only report generation like..." does not. */
    private static final Pattern DECLARATION = Pattern.compile("^(?:Read-only|READ-ONLY)\\b");
    private static final Pattern NEGATED_READ_ONLY = Pattern.compile("(?i)\\b(?:not|non|never|isn't|aren't|no longer)[\\s-]+(?:a\\s+)?read-only\\b");

    /**
     * Whether a test class DECLARES itself read-only: a line of its class-level doc comment (the {@code /** ... *}{@code /} block directly
     * above the class, annotations allowed between) STARTS with "Read-only" - the project's own way of stating what the whole flow
     * does ("Read-only: this flow prints and navigates, it creates no records") - and the doc does not negate it. Only such a class is
     * ever re-run against the application after a patch. The word anywhere else does NOT count, because there it describes a
     * detail, not the test: an inline comment, a step description, a string such as "the form becomes read-only", "one extra,
     * read-only check follows the toast" in the doc of a test that makes a real change, or "NOT read-only".
     */
    static boolean declaredReadOnly(String src) {
        Matcher cm = CLASS_DECL.matcher(src);
        if (!cm.find()) return false;
        String prefix = src.substring(0, cm.start());
        int end = prefix.lastIndexOf("*/");
        if (end < 0) return false;
        if (!prefix.substring(end + 2).matches("(?s)(?:\\s|@\\w+(?:\\([^)]*\\))?)*")) return false;   // the doc must sit right above the class
        int start = prefix.lastIndexOf("/**", end);
        if (start < 0) return false;
        String doc = prefix.substring(start, end);
        if (NEGATED_READ_ONLY.matcher(doc).find()) return false;
        for (String line : doc.split("\\R")) {
            String text = line.replaceFirst("^\\s*(?:/\\*\\*|\\*+)?\\s*", "").replaceFirst("^(?:<[A-Za-z]+>\\s*)+", "");
            if (DECLARATION.matcher(text).find()) return true;
        }
        return false;
    }
}
