package com.kpj.heal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared fixtures for the SYNTHETIC post-run integration tests: a throw-away source tree whose flows look exactly like the project's
 * ({@code super("id")} + a page object with one locator literal), synthetic result files with controlled timestamps, and read-only
 * fingerprints that prove the project's own {@code test-output*} folders and sources were never touched. Nothing here starts Maven,
 * a browser or DevHIS.
 */
final class PostRunTestSupport {

    private PostRunTestSupport() { }

    static final String STEP = "Select flow location";
    static final String LITERAL_LINE = "        select('staffScheduleInfo.LocationId');";
    /** A failure the real classifier treats as a locator problem it can propose a fix for (given the sidecar below). */
    static final String ELIGIBLE_ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";
    /** A failure the real classifier treats as an environment problem: never eligible. */
    static final String ENVIRONMENT_ACTUAL = "Report FAILED to render - Server Error in '/' Application. | The remote certificate is invalid according to the validation procedure.";

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    interface Action { void run() throws Exception; }

    // ------------------------------------------------------------------ synthetic sources

    /** One flow: a test class (package {@code com.kpj.tests.<pkg>}) and its own page object under {@code src/}. */
    static void writeFlow(Path testRoot, String pkg, String simple, String testId, String pageSimple) throws IOException {
        Path page = testRoot.resolve("src").resolve(pageSimple + ".java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package src;\npublic class " + pageSimple + " {\n    public " + pageSimple + "(Object page) { }\n"
                + "    public boolean pick() {\n" + LITERAL_LINE + "\n        return true;\n    }\n}\n");
        Path test = testRoot.resolve("com/kpj/tests/" + pkg + "/" + simple + ".java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.kpj.tests." + pkg + ";\nimport src." + pageSimple + ";\npublic class " + simple + " {\n"
                + "    public " + simple + "() { super(\"" + testId + "\"); }\n    protected void body() {\n"
                + "        " + pageSimple + " p = new " + pageSimple + "(page);\n        p.pick();\n"
                + "        step(\"" + STEP + "\", \"desc\", \"expected\", \"actual\", \"FAIL\");\n    }\n}\n");
    }

    /** The standard synthetic tree: SYN_FLOW, SYN_OTHER, and two classes that share the simple name {@code Transfer}. */
    static void writeAllFlows(Path testRoot) throws IOException {
        writeFlow(testRoot, "Syn", "SynFlowTest", "SYN_FLOW", "SynFlowPage");
        writeFlow(testRoot, "Syn", "SynOtherTest", "SYN_OTHER", "SynOtherPage");
        writeFlow(testRoot, "SynA", "Transfer", "SYN_TA", "SynTaPage");
        writeFlow(testRoot, "SynB", "Transfer", "SYN_TB", "SynTbPage");
        Files.createDirectories(testRoot.resolve("no-dependents"));
    }

    // ------------------------------------------------------------------ synthetic results

    static String row(String status, String testId, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", testId, step, expected, actual, "") + "\n";
    }

    /** {@code <out>/aggregate-data/<file>} with a Login PASS and, for a failing flow, the failing step - stamped with {@code mtime}. */
    static Path writeTsv(Path out, String file, String testId, boolean failing, String actual, Instant mtime) throws IOException {
        Path dir = out.resolve("aggregate-data");
        Files.createDirectories(dir);
        Path f = dir.resolve(file);
        Files.writeString(f, row("PASS", testId, "Login", "Authenticated", "Logged in")
                + (failing ? row("FAIL", testId, STEP, "element found", actual) : row("PASS", testId, STEP, "element found", "element found")));
        Files.setLastModifiedTime(f, FileTime.from(mtime));
        return f;
    }

    /** The page-state sidecar the framework writes for a FAIL step - stamped with {@code mtime}. */
    static Path writeSidecar(Path out, String testId, String actual, Instant mtime) throws IOException {
        Path dir = out.resolve("failures");
        Files.createDirectories(dir);
        Path f = dir.resolve(testId + "_1.txt");
        Files.writeString(f, "testId=" + testId + "\npage=Synthetic page\nstep=" + STEP + "\ndesc=d\nexpected=element found\nactual=" + actual
                + "\nurl=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff\n");
        Files.setLastModifiedTime(f, FileTime.from(mtime));
        return f;
    }

    static void setMtime(Path f, Instant t) throws IOException { Files.setLastModifiedTime(f, FileTime.from(t)); }

    // ------------------------------------------------------------------ helpers

    static String capture(Action a) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try { a.run(); } finally { System.setOut(old); }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    static List<Path> files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
    }

    // ------------------------------------------------------------------ fingerprints (read-only proof that nothing real was touched)

    /** SHA-256 over the CONTENT of every file in every {@code test-output*} folder under the project, the scripts folder and Executables. */
    static String outputsContentFingerprint() throws IOException {
        MessageDigest md;
        try { md = MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IllegalStateException(e); }
        List<Path> roots = new ArrayList<>();
        for (String base : new String[]{".", "devhis-tests-share", "Executables"}) {
            Path b = Paths.get(base);
            if (!Files.isDirectory(b)) continue;
            try (Stream<Path> s = Files.list(b)) {
                s.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("test-output")).sorted().forEach(roots::add);
            }
        }
        for (Path r : roots) {
            for (Path f : files(r)) {
                md.update(f.toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(Files.readAllBytes(f));
                md.update((byte) 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return roots.size() + " folders / " + sb;
    }

    /** Hash of the project's own test/page/core/heal sources, so a test can prove none was modified. */
    static String sourcesFingerprint() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String root : new String[]{"src/test/java/com/kpj/tests", "src/test/java/com/kpj/pages", "src/main/java/com/kpj/core"}) {
            for (Path p : files(Paths.get(root))) sb.append(p).append('=').append(PendingHeal.hashFile(p)).append('\n');
        }
        return PendingHeal.sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The shaded jars must never be rebuilt or replaced by this work. */
    static String jarsFingerprint() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{"devhis-tests-share/mcp-kpj-1.0.0-shaded.jar", "target/mcp-kpj-1.0.0-shaded.jar", "Executables/mcp-kpj-1.0.0-shaded.jar"}) {
            Path f = Paths.get(p);
            sb.append(p).append('=').append(Files.isRegularFile(f) ? PendingHeal.hashFile(f) : "absent").append('\n');
        }
        return sb.toString();
    }
}
