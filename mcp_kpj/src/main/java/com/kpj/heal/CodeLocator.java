package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Step 3 (identify affected code) — given a test class and a failing step's name, finds:
 * <ol>
 *   <li>the {@code step(...)} call site in the test source,</li>
 *   <li>the Page Object variable/method it called just before recording that step,</li>
 *   <li>that method's source in the Page Object file.</li>
 * </ol>
 *
 * <p>Text/regex based (no AST) — deliberately narrow: it only needs to hand ONE method body to
 * {@link PatchProposer}, not to understand the whole file. Only handles the common shape used throughout
 * this codebase: {@code PageType var = new PageType(page); ... var.someMethod(...); ... step(..., "step name", ...);}
 * — {@code PageType} may be a simple (imported) name or written fully-qualified inline with no import (the
 * pattern used when a Page Object's simple name collides with its test class's own name, e.g.
 * {@code HospitalLocation}). If the step was recorded directly in the test's own body() (no Page Object
 * call), or the step name can't be matched back to a literal in source, it returns {@code null} — the
 * caller reports and stops rather than guessing.</p>
 */
public class CodeLocator {

    private static final Path TEST_ROOT = Paths.get("src/test/java");

    /** Source root the test and Page Object files are read from (always {@code src/test/java} outside tests). */
    private final Path testRoot;

    /** Test seam: when non-null, the default constructor reads sources from this folder instead of {@code src/test/java}.
     *  Always null in production, so behaviour is unchanged; synthetic tests point it at a temp folder. */
    static volatile Path testRootOverride = null;

    public CodeLocator() { this(sourceRoot()); }

    /** The folder test sources are read from: {@code src/test/java}, or the test seam's temp folder. */
    static Path sourceRoot() { return testRootOverride != null ? testRootOverride : TEST_ROOT; }

    /** Package-private: lets tests point the locator at a temp folder instead of the real source tree. */
    CodeLocator(Path testRoot) { this.testRoot = testRoot; }

    public static class CodeLocation {
        public final Path testFile;
        public final Path pageObjectFile;
        public final String variableName;
        public final String methodName;
        public final String methodSource;
        /** The test file's own source, but bounded to the immediate vicinity of THIS failure's step(...) call
         *  (see {@link #locate}) — never the whole file. A large, multi-section test file can contain code for
         *  many unrelated steps; scanning the whole file for a hardcoded/local-path signal would let a path
         *  used by a completely different, unrelated step (e.g. a different section of the same test) wrongly
         *  explain THIS failure. Empty when not applicable (e.g. a display-only reconstruction that never
         *  re-read the file). */
        public final String nearbyTestSource;

        CodeLocation(Path testFile, Path pageObjectFile, String variableName, String methodName, String methodSource,
                     String nearbyTestSource) {
            this.testFile = testFile;
            this.pageObjectFile = pageObjectFile;
            this.variableName = variableName;
            this.methodName = methodName;
            this.methodSource = methodSource;
            this.nearbyTestSource = nearbyTestSource == null ? "" : nearbyTestSource;
        }
    }

    /** @return the located Page Object method, or {@code null} (reason printed to stdout) if it can't be resolved. */
    public CodeLocation locate(String testClassName, String stepName) {
        Path testFile = testRoot.resolve(testClassName.replace('.', '/') + ".java");
        if (!Files.isRegularFile(testFile)) {
            System.out.println("CodeLocator: test source not found: " + testFile);
            return null;
        }
        String testSrc;
        try { testSrc = Files.readString(testFile); }
        catch (IOException e) { System.out.println("CodeLocator: cannot read " + testFile + ": " + e.getMessage()); return null; }

        // The recorded step text often carries a runtime prefix (e.g. "[1/1] "); the source only has the literal.
        String bareStep = stepName.replaceFirst("^\\[[^\\]]*\\]\\s*", "").trim();
        String[] lines = testSrc.split("\n", -1);

        int stepLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("\"" + bareStep + "\"")) { stepLine = i; break; }
        }
        if (stepLine < 0) {
            System.out.println("CodeLocator: no step(...) literal matching \"" + bareStep + "\" in " + testFile);
            return null;
        }

        // The test source in the immediate vicinity of THIS failure's own step(...) call — same 15-line
        // backward locality window used just below for finding the responsible Page Object call, reused here
        // so a hardcoded/local-path signal is only attributed to a failure when it's actually near the step
        // that failed, not anywhere else in a large, multi-section test file.
        StringBuilder nearby = new StringBuilder();
        for (int i = Math.max(0, stepLine - 15); i <= stepLine; i++) nearby.append(lines[i]).append('\n');
        String nearbyTestSource = nearby.toString();

        // Page Object variables: "Type var = new Type(page..." — reliable since every Page Object constructor
        // takes the Playwright Page as its argument (see BasePage). The type may be a simple name (the common
        // case) OR a fully-qualified one written inline with no import — the escape hatch this codebase uses
        // when a Page Object's simple name collides with the test class's own name (e.g. HospitalLocation,
        // where both the test and its Page Object are named "HospitalLocation"). The backreference (\1)
        // still requires the exact same text to repeat after "new", so allowing dots here doesn't loosen the
        // match for the existing simple-name case at all — it only adds coverage for the FQN case.
        Map<String, String> varTypes = new LinkedHashMap<>();
        Matcher dm = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*new\\s+\\1\\s*\\(\\s*page\\b")
                .matcher(testSrc);
        while (dm.find()) varTypes.put(dm.group(2), dm.group(1));
        addFactoryCreatedPageObjects(testSrc, varTypes);
        if (varTypes.isEmpty()) {
            System.out.println("CodeLocator: no Page Object variable declarations found in " + testFile);
            return null;
        }

        // Scan backwards from the step(...) line for the last call on a known Page Object variable.
        Pattern callPat = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        String variableName = null, methodName = null;
        for (int i = stepLine; i >= Math.max(0, stepLine - 15) && variableName == null; i--) {
            Matcher cm = callPat.matcher(lines[i]);
            while (cm.find()) {
                if (varTypes.containsKey(cm.group(1))) { variableName = cm.group(1); methodName = cm.group(2); }
            }
        }
        if (variableName == null) {
            System.out.println("CodeLocator: no Page Object call found near the step(...) for \"" + bareStep + "\" "
                    + "(likely recorded directly in the test body — out of scope for this MVP)");
            return null;
        }

        String typeName = varTypes.get(variableName);
        String fqcn;
        if (typeName.contains(".")) {
            // Already fully qualified, captured straight from the declaration — no import to look up.
            fqcn = typeName;
        } else {
            Matcher im = Pattern.compile("import\\s+([\\w.]+\\." + Pattern.quote(typeName) + ")\\s*;").matcher(testSrc);
            if (!im.find()) {
                System.out.println("CodeLocator: no import found for Page Object type " + typeName + " in " + testFile);
                return null;
            }
            fqcn = im.group(1);
        }
        Path pageObjectFile = testRoot.resolve(fqcn.replace('.', '/') + ".java");
        if (!Files.isRegularFile(pageObjectFile)) {
            System.out.println("CodeLocator: Page Object source not found: " + pageObjectFile);
            return null;
        }

        String poSrc;
        try { poSrc = Files.readString(pageObjectFile); }
        catch (IOException e) { System.out.println("CodeLocator: cannot read " + pageObjectFile + ": " + e.getMessage()); return null; }

        String methodSource = extractMethod(poSrc, methodName);
        if (methodSource == null) {
            System.out.println("CodeLocator: method " + methodName + "(...) not found in " + pageObjectFile);
            return null;
        }

        return new CodeLocation(testFile, pageObjectFile, variableName, methodName, methodSource, nearbyTestSource);
    }

    /**
     * Page Objects created through a factory method instead of a direct constructor call, e.g.
     * <pre>
     *   protected AppointmentListPage newListPage() { return new AppointmentListPage(page); }   // the factory
     *   listPage = newListPage();                                                              // variable/field from it
     * </pre>
     * Only this exact same-file shape is accepted, with the same strictness as the direct rule above: the factory's
     * declared return type must be the very type it constructs (back-reference), and the variable must be assigned
     * directly from a call to that factory. {@code putIfAbsent}, so a direct {@code Type var = new Type(page)}
     * declaration for the same variable always wins and existing behaviour is unchanged.
     */
    private static void addFactoryCreatedPageObjects(String testSrc, Map<String, String> varTypes) {
        Map<String, String> factories = new LinkedHashMap<>();
        Matcher fm = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+new\\s+\\1\\s*\\(\\s*page\\b")
                .matcher(testSrc);
        while (fm.find()) factories.put(fm.group(2), fm.group(1));
        for (Map.Entry<String, String> f : factories.entrySet()) {
            Matcher am = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*" + Pattern.quote(f.getKey()) + "\\s*\\(\\s*\\)\\s*;")
                    .matcher(testSrc);
            while (am.find()) varTypes.putIfAbsent(am.group(1), f.getValue());
        }
    }

    /** Extracts a method's full source (declaration through matching closing brace) by simple brace counting. */
    private static String extractMethod(String src, String methodName) {
        String[] lines = src.split("\n", -1);
        Pattern declPat = Pattern.compile("\\b(public|private|protected)\\b.*\\b" + Pattern.quote(methodName) + "\\s*\\(");
        int declLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (declPat.matcher(lines[i]).find()) { declLine = i; break; }
        }
        if (declLine < 0) return null;

        StringBuilder body = new StringBuilder();
        int depth = 0;
        boolean started = false;
        for (int i = declLine; i < lines.length; i++) {
            String line = lines[i];
            for (char c : line.toCharArray()) {
                if (c == '{') { depth++; started = true; }
                else if (c == '}') depth--;
            }
            body.append(line).append('\n');
            if (started && depth == 0) return body.toString();
        }
        return null; // unbalanced — refuse rather than return a partial method
    }
}
