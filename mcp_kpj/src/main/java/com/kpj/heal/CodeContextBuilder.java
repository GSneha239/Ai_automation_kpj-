package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3 Step 6 — assembles a BOUNDED, multi-file text bundle for the LLM prompt: the failing test's {@code
 * body()} method, the located Page Object method (already available from {@link CodeLocator}), a few helper
 * methods that method itself calls in the SAME file, and {@code BasePage.java} (the shared class every Page
 * Object extends).
 *
 * <p>This is CONTEXT ONLY. It never becomes multiple patch targets — {@link CodeLocator} itself is not
 * modified by this class, and only the ONE {@code (file, oldSnippet, newSnippet)} the LLM names in its
 * structured response is ever validated ({@link LlmProposalValidator}) or applied ({@link PatchApplier}).</p>
 */
public class CodeContextBuilder {

    private static final int MAX_CONTEXT_CHARS = 12000;
    private static final int MAX_HELPER_METHODS = 3;
    private static final Path BASE_PAGE = Paths.get("src/test/java/com/kpj/pages/BasePage.java");

    public String build(String testClassName, CodeLocator.CodeLocation loc, FailureRecord failure) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Failing test method (").append(testClassName).append(") ===\n");
        appendBounded(sb, extractMethod(readBestEffort(loc.testFile), "body"));

        sb.append("\n=== Located Page Object method: ").append(loc.pageObjectFile).append(" :: ")
          .append(loc.methodName).append("() ===\n");
        appendBounded(sb, loc.methodSource);

        String poSrc = readBestEffort(loc.pageObjectFile);
        for (String helper : findCalledHelperMethods(loc.methodSource, loc.methodName, poSrc)) {
            String helperSrc = extractMethod(poSrc, helper);
            if (helperSrc == null) continue;
            sb.append("\n=== Helper method called by ").append(loc.methodName).append("(): ").append(helper).append("() ===\n");
            appendBounded(sb, helperSrc);
        }

        if (Files.isRegularFile(BASE_PAGE)) {
            sb.append("\n=== Shared base class: BasePage.java ===\n");
            appendBounded(sb, readBestEffort(BASE_PAGE));
        }

        String context = sb.toString();
        if (context.length() > MAX_CONTEXT_CHARS) {
            context = context.substring(0, MAX_CONTEXT_CHARS) + "\n...[context truncated at " + MAX_CONTEXT_CHARS + " chars]...";
        }
        return context;
    }

    /** Method names declared in {@code poSrc} that {@code methodSource} appears to call — excludes the
     *  located method itself. Best-effort/heuristic: this only shapes PROMPT CONTEXT, never what gets
     *  patched, so a missed or spurious helper here has no safety consequence. */
    private static LinkedHashSet<String> findCalledHelperMethods(String methodSource, String excludeMethodName, String poSrc) {
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        Matcher declM = Pattern.compile("\\b(?:public|private|protected)\\b[^;{]*?\\b([a-zA-Z_]\\w*)\\s*\\(").matcher(poSrc);
        while (declM.find()) declared.add(declM.group(1));

        LinkedHashSet<String> called = new LinkedHashSet<>();
        Matcher callM = Pattern.compile("\\b([a-zA-Z_]\\w*)\\s*\\(").matcher(methodSource);
        while (callM.find() && called.size() < MAX_HELPER_METHODS) {
            String name = callM.group(1);
            if (name.equals(excludeMethodName)) continue;
            if (declared.contains(name)) called.add(name);
        }
        return called;
    }

    /** Same brace-counting approach as {@code CodeLocator.extractMethod} — kept as a private local copy
     *  rather than exposing CodeLocator's internals, so CodeLocator itself needed zero changes for V3. */
    private static String extractMethod(String src, String methodName) {
        if (src == null || src.isEmpty()) return null;
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
        return null;
    }

    private static void appendBounded(StringBuilder sb, String text) {
        sb.append(text == null || text.isEmpty() ? "(not available)\n" : text);
    }

    private static String readBestEffort(Path file) {
        if (file == null) return "";
        try { return Files.readString(file); } catch (IOException e) { return ""; }
    }
}
