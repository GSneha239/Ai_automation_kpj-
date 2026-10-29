package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests proving that the chat-driven path ({@code apply-pending}) runs the DEPENDENT read-only
 * regression tests after the target test passes - keeping the patch when they pass and rolling it back when one fails.
 *
 * <p>Everything is temp: the "healed" source, the output folder and the dependent test classes (a temp test tree that
 * {@link ImpactAnalyzer#testRootOverride} points at). {@link SelfHealingAgent#mavenLauncher} is a fake that records calls and
 * writes synthetic results, so no Maven, browser, DevHIS or data-changing test is ever run.</p>
 */
class ApplyPendingRegressionTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private Path source, out, pendingFile;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");

        // synthetic test tree: who depends on SynPage?
        Path tests = tmp.resolve("tests").resolve("com").resolve("kpj").resolve("tests").resolve("Syn");
        Files.createDirectories(tests);
        dependent(tests, "DepReadOnlyTest", "DEP_RO", true);   // read-only marker + super(id): MUST be run
        dependent(tests, "DepWritesTest", "DEP_WR", false);     // no read-only marker: must NEVER be run
        dependent(tests, "SynTest", TEST_ID, true);             // the class just healed: must be skipped
        Files.writeString(tests.resolve("DepNoIdTest.java"),    // read-only marker but no super(id): skipped, not a failure
                "package com.kpj.tests.Syn;\n/** Read-only dependent without a test id. */\npublic class DepNoIdTest {\n    void body() { SynPage p = new SynPage(page); }\n}\n");
        ImpactAnalyzer.testRootOverride = tmp.resolve("tests");

        writeResult(TEST_ID, STEP, true, null);   // the original failing run
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "expected", "FAILED: still broken");
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = TEST_ID;
        p.testClass = "com.kpj.tests.Syn.SynTest";
        p.simpleName = "SynTest";
        p.step = STEP;
        p.affectedFile = source.toString();
        p.affectedMethod = "probe()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = OLD;
        p.newSnippet = NEW;
        p.reason = "closest live ng-model is staffScheduleInfo.Locationid";
        p.confidence = 0.75;
        p.risk = "LOW";
        p.rootCause = "Stale locator literal - one-character case typo";
        p.semanticValidation = "PASSED";
        p.groupKey = "SynPage.java::probe::locationid";
        p.originalFailingSteps = new ArrayList<>(List.of(STEP));
        p.candidateIndex = 1;
        p.candidateTotal = 1;
        p.attemptNumber = 1;
        p.attemptsMax = 3;
        p.sessionFile = out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties").toString();
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        ApplyTestSupport.uninstallLocatorFixture();
    }

    private static void dependent(Path dir, String name, String id, boolean readOnly) throws IOException {
        Files.writeString(dir.resolve(name + ".java"),
                "package com.kpj.tests.Syn;\n/**\n * " + (readOnly ? "Read-only dependent." : "A dependent that changes data.") + "\n */\n"
                        + "public class " + name + " {\n    public " + name + "() { super(\"" + id + "\"); }\n"
                        + "    void body() { SynPage p = new SynPage(page); }\n}\n");
    }

    // ------------------------------------------------------------------ fake Maven + synthetic results

    private void writeResult(String testId, String step, boolean fails, String failingStepAlso) throws IOException {
        Path dir = out.resolve("aggregate-data");
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append(row("PASS", testId, "Login", "Authenticated", "Logged in"));
        sb.append(row(fails ? "FAIL" : "PASS", testId, step, "expected", fails ? "FAILED: still broken" : "ok"));
        Files.writeString(dir.resolve(testId + ".tsv"), sb.toString());
    }

    private static String row(String status, String testId, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", testId, step, expected, actual, "") + "\n";
    }

    /**
     * compileExit: 0 compiles. targetFails: the healed test still fails after the patch. depROFails: the read-only
     * dependent now fails. A re-run of any test class writes its synthetic result into aggregate-data.
     */
    private void fakeMaven(int compileExit, boolean targetFails, boolean depROFails) {
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.asList(args).contains("test-compile")) return compileExit;
            for (String a : args) {
                if (a.equals("-Dtest=com.kpj.tests.Syn.SynTest")) writeResult(TEST_ID, STEP, targetFails, null);
                if (a.equals("-Dtest=com.kpj.tests.Syn.DepReadOnlyTest")) writeResult("DEP_RO", "Dependent step", depROFails, null);
                if (a.equals("-Dtest=com.kpj.tests.Syn.DepWritesTest")) writeResult("DEP_WR", "Dependent step", false, null);
            }
            return 0;
        };
    }

    private void apply(String decision) throws Exception {
        ApplyTestSupport.apply(pendingFile, decision);   // an "approved" decision quotes the proposal's own hash
    }

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private String unifiedReport() throws IOException {
        try (Stream<Path> s = Files.list(out.resolve("unified-report"))) {
            return Files.readString(s.sorted().reduce((a, b) -> b).orElseThrow());
        }
    }

    // ================================================================== the impact analysis itself

    @Test
    void impactAnalysis_findsTheDependents_andMarksOnlyReadOnlyOnesAsSafeToRun() {
        ImpactAnalyzer.Result r = new ImpactAnalyzer().analyze(source);
        assertEquals(4, r.dependentCount(), "DepReadOnly, DepWrites, DepNoId and the healed SynTest all use SynPage");
        assertEquals(3, r.readOnlyDependents.size(), "only the three marked read-only are eligible for auto-verification");
        assertTrue(r.readOnlyDependents.stream().noneMatch(f -> f.endsWith("DepWritesTest.java")), "a data-changing dependent is never eligible");
    }

    // ================================================================== apply-pending runs the dependents

    @Test
    void passingDependents_afterTheTargetPasses_keepThePatch() throws Exception {
        fakeMaven(0, false, false);
        apply("approved");

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test", "-q -Dtest=com.kpj.tests.Syn.DepReadOnlyTest test"), mavenCalls,
                "compile, the healed test, THEN the dependent read-only test - nothing else");
        assertFalse(mavenCalls.stream().anyMatch(c -> c.contains("DepWritesTest")), "a non-read-only dependent is never run");
        assertEquals(1, mavenCalls.stream().filter(c -> c.equals("-q -Dtest=com.kpj.tests.Syn.SynTest test")).count(),
                "the healed class itself is not re-run as its own dependent");

        String patched = Files.readString(source);
        assertTrue(patched.contains(NEW) && !patched.contains(OLD), "the patch is kept");
        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"));
        assertEquals("NONE", r.get("regressionResult"));
        assertTrue(unifiedReport().contains("HEALED AND VERIFIED - KEEP"));
    }

    @Test
    void aFailingDependent_rollsBackThePatch_andTheReasonNamesIt() throws Exception {
        fakeMaven(0, false, true);
        byte[] before = Files.readAllBytes(source);
        apply("approved");

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test", "-q -Dtest=com.kpj.tests.Syn.DepReadOnlyTest test"), mavenCalls);
        assertArrayEquals(before, Files.readAllBytes(source), "the patch is rolled back to the exact original");
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("PASS", r.get("rerunResult"), "the healed test itself passed - the dependent is what failed");
        assertTrue(r.get("reason").startsWith("impacted regression test(s) failed"), r.get("reason"));
        assertTrue(r.get("reason").contains("DepReadOnlyTest"), r.get("reason"));
        assertTrue(r.get("regressionResult").contains("DepReadOnlyTest"), r.get("regressionResult"));
        assertEquals("true", r.get("reverted"));
        String report = unifiedReport();
        assertTrue(report.contains("ROLLBACK - NOT HEALED"));
        assertFalse(report.contains("HEALED AND VERIFIED"), "a regression is never reported as a heal");
    }

    @Test
    void dependentsAreNotRun_whenTheTargetTestStillFails() throws Exception {
        fakeMaven(0, true, false);
        byte[] before = Files.readAllBytes(source);
        apply("approved");
        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test"), mavenCalls, "they run only AFTER the target test passes");
        assertArrayEquals(before, Files.readAllBytes(source));
        assertEquals("target step still fails", lastAuditRow().get("reason"));
    }

    @Test
    void dependentsAreNotRun_whenTheCompileFails() throws Exception {
        fakeMaven(1, false, false);
        byte[] before = Files.readAllBytes(source);
        apply("approved");
        assertEquals(List.of("-q test-compile"), mavenCalls);
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    void dependentsAreNotRun_whenTheChangeIsDeclined() throws Exception {
        fakeMaven(0, false, false);
        byte[] before = Files.readAllBytes(source);
        apply("declined");
        assertTrue(mavenCalls.isEmpty(), "nothing was applied, so nothing is verified: " + mavenCalls);
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    void withNoDependentsAtAll_theBehaviourIsExactlyAsBefore() throws Exception {
        ImpactAnalyzer.testRootOverride = tmp.resolve("empty-tests");   // no dependents here
        Files.createDirectories(tmp.resolve("empty-tests"));
        fakeMaven(0, false, false);
        apply("approved");
        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test"), mavenCalls);
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
    }
}
