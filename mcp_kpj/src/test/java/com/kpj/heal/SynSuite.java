package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tiny synthetic "project" for the interactive / --all tests: several test classes, each with its own Page Object, all under one temp
 * root that {@link CodeLocator} and {@link TestClassResolver} are pointed at. A test either has DOM <i>evidence</i> (so the real
 * classifiers find a fixable stale locator and a deterministic fix exists) or has none (so nothing may be proposed for it).
 * Nothing here touches Maven, a browser or DevHIS.
 */
final class SynSuite {

    static final String LITERAL_LINE = "        select('staffScheduleInfo.LocationId');";
    static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    /** One synthetic test class. */
    static final class SynTest {
        final String simple, testId, step, fqcn;
        final boolean evidence;
        final Path page;
        SynTest(String simple, String testId, String step, boolean evidence, Path page) {
            this.simple = simple; this.testId = testId; this.step = step; this.evidence = evidence; this.page = page;
            this.fqcn = "com.kpj.tests.Syn." + simple;
        }
    }

    final Path root;
    final Path out;
    final List<SynTest> tests = new ArrayList<>();

    SynSuite(Path root, Path out) { this.root = root; this.out = out; }

    /** Writes a test class + Page Object under the root. */
    SynTest add(String simple, String testId, String step, boolean evidence) throws IOException {
        Path page = root.resolve("src").resolve(simple + "Page.java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package src;\npublic class " + simple + "Page {\n    public " + simple + "Page(Object page) { }\n"
                + "    public boolean pick() {\n" + LITERAL_LINE + "\n        return true;\n    }\n}\n");
        Path test = root.resolve("com/kpj/tests/Syn/" + simple + ".java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.kpj.tests.Syn;\nimport src." + simple + "Page;\npublic class " + simple + " {\n"
                + "    public " + simple + "() { super(\"" + testId + "\"); }\n    protected void body() {\n"
                + "        " + simple + "Page p = new " + simple + "Page(page);\n        p.pick();\n"
                + "        step(\"" + step + "\", \"desc\", \"expected\", \"actual\", \"FAIL\");\n    }\n}\n");
        SynTest t = new SynTest(simple, testId, step, evidence, page);
        tests.add(t);
        return t;
    }

    /** Points the agents at this suite. */
    void install() {
        CodeLocator.testRootOverride = root;
        TestClassResolver.rootOverride = root.resolve("com/kpj/tests");
        try {
            Path none = root.resolve("no-dependents");
            Files.createDirectories(none);
            ImpactAnalyzer.testRootOverride = none;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static void uninstall() {
        CodeLocator.testRootOverride = null;
        TestClassResolver.rootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
    }

    private static String row(String testId, String status, String step, String actual) {
        return String.join("\t", status, "Synthetic page", testId, step, "element found", actual, "") + "\n";
    }

    /** The result of a run of {@code t}: a failing or passing step, plus the DOM capture when the test has evidence. */
    void writeRun(SynTest t, boolean passes) throws IOException {
        writeRunInto(out, t, passes);
    }

    static void writeRunInto(Path dir, SynTest t, boolean passes) throws IOException {
        Files.createDirectories(dir.resolve("aggregate-data"));
        Files.writeString(dir.resolve("aggregate-data").resolve(t.testId + ".tsv"),
                row(t.testId, "PASS", "Login", "Logged in")
                        + (passes ? row(t.testId, "PASS", t.step, "element found") : row(t.testId, "FAIL", t.step, ACTUAL)));
        if (!passes && t.evidence) {
            Files.createDirectories(dir.resolve("failures"));
            Files.writeString(dir.resolve("failures").resolve(t.testId + "_1.txt"),
                    "testId=" + t.testId + "\npage=Synthetic page\nstep=" + t.step + "\ndesc=d\nexpected=element found\nactual=" + ACTUAL
                            + "\nurl=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff\n");
        }
    }

    /** Backdates every file under {@code dir} by an hour: an OLD run's leftovers. */
    static void makeOld(Path dir) throws IOException {
        FileTime old = FileTime.from(Instant.now().minusSeconds(3600));
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) Files.setLastModifiedTime(p, old);
        }
    }
}
