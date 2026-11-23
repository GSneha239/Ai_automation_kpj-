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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests of the Failure Analysis refresh after a verified KEEP. The "healed" source, every output
 * folder and the failure evidence are temp files; {@link SelfHealingAgent#mavenLauncher} is a fake. No Maven, browser,
 * DevHIS or data-changing test is run, and no real project file is patched.
 *
 * <p>Guarantees under test: a KEEP refreshes the Failure Analysis report, summary and index so they show the resolution;
 * nothing is refreshed (or marked fixed) before verification completes, or for a rollback / declined / refused change; and a
 * problem while writing the report never changes the code-fix outcome.</p>
 */
class ApplyPendingFaRefreshTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private boolean faReportExistedDuringRerun = false;
    private Path source, out, pendingFile;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");
        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");   // keep this class about the refresh only

        writeResult(true, null);   // the ORIGINAL failing run
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'");
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

    // ------------------------------------------------------------------ fake Maven + synthetic results

    private void writeResult(boolean targetFails, String extraFailingStep) throws IOException {
        Path dir = out.resolve("aggregate-data");
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append(row("PASS", "Login", "Authenticated", "Logged in"));
        sb.append(row(targetFails ? "FAIL" : "PASS", STEP, "element found",
                targetFails ? "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'" : "element found"));
        if (extraFailingStep != null) sb.append(row("FAIL", extraFailingStep, "something", "broke after the patch"));
        Files.writeString(dir.resolve("SYN.tsv"), sb.toString());
    }

    private static String row(String status, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, expected, actual, "") + "\n";
    }

    private void fakeMaven(int compileExit, boolean targetStillFails, String extraFailingStep) {
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.asList(args).contains("test-compile")) return compileExit;
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) {
                // at the moment of the re-run verification has NOT completed: nothing may be refreshed yet
                faReportExistedDuringRerun |= Files.exists(out.resolve("failure-analysis").resolve("reports"))
                        || Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv"));
                writeResult(targetStillFails, extraFailingStep);
            }
            return 0;
        };
    }

    private void apply(String decision) throws Exception {
        ApplyTestSupport.apply(pendingFile, decision);   // an "approved" decision quotes the proposal's own hash
    }

    // ------------------------------------------------------------------ helpers

    private Path faReport() throws IOException {
        Path dir = out.resolve("failure-analysis").resolve("reports");
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> l = s.filter(p -> p.getFileName().toString().endsWith("_FailureAnalysisReport.html")).collect(Collectors.toList());
            assertEquals(1, l.size(), "exactly one refreshed Failure Analysis report: " + l);
            return l.get(0);
        }
    }

    private long countFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) { return s.count(); }
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

    private void assertNothingMarkedFixed() {
        assertFalse(Files.exists(out.resolve("failure-analysis")),
                "no Failure Analysis report, summary, index or tracker row exists - nothing is marked fixed");
    }

    // ================================================================== a verified KEEP refreshes the report

    @Test
    void keep_refreshesTheFailureAnalysisReportSummaryAndIndex_withTheResolution() throws Exception {
        assertFalse(Files.exists(out.resolve("failure-analysis")), "sanity: nothing exists before the approval");
        fakeMaven(0, false, null);
        apply("approved");

        String html = Files.readString(faReport());
        assertTrue(html.contains("<th>Final Status</th><td>RESOLVED</td>"), "the report now shows the resolution");
        assertTrue(html.contains("<th>Fixed By</th><td>Self-Healing Agent (automated, human-approved)</td>"));
        assertTrue(html.contains("<th>Retest Result</th><td>PASS</td>"));
        assertTrue(html.contains("LocationId"), "it still describes the ORIGINAL failure, not the fixed state");

        assertEquals(1, countFiles(out.resolve("failure-analysis").resolve("summary")), "a summary is written");
        assertEquals(1, countFiles(out.resolve("failure-analysis").resolve("index")), "an index is written");
        Path runs = out.resolve("failure-analysis").resolve("history").resolve("runs.tsv");
        assertTrue(Files.isRegularFile(runs) && Files.readAllLines(runs).size() >= 2, "the manifest the index is built from has a row");

        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"));
        assertTrue(r.get("reason").startsWith("Failure Analysis report refreshed"), r.get("reason"));
        assertTrue(unifiedReport().contains("<th>Failure Analysis status</th><td>RESOLVED</td>"),
                "the unified report picks up the refreshed Failure Analysis status");
    }

    @Test
    void nothingIsRefreshedOrRecordedBeforeVerificationCompletes() throws Exception {
        fakeMaven(0, false, null);
        apply("approved");
        assertFalse(faReportExistedDuringRerun,
                "while the test was being re-run, no Failure Analysis report and no tracker row existed yet");
    }

    @Test
    void earlierFailureAnalysisReports_areNeverOverwritten() throws Exception {
        Path old = out.resolve("failure-analysis").resolve("reports").resolve("SynTest_20200101_000000");
        Files.createDirectories(old);
        Path oldReport = old.resolve("SynTest_FailureAnalysisReport.html");
        Files.writeString(oldReport, "<html>ORIGINAL ANALYSIS - status OPEN</html>");
        byte[] before = Files.readAllBytes(oldReport);
        fakeMaven(0, false, null);
        apply("approved");
        assertArrayEquals(before, Files.readAllBytes(oldReport), "the earlier report is byte-identical");
        assertEquals(2, countFiles(out.resolve("failure-analysis").resolve("reports")), "the refresh is a NEW report beside it");
    }

    // ================================================================== nothing is marked fixed when the fix is not kept

    @Test
    void compileFailure_rollsBack_andMarksNothingFixed() throws Exception {
        fakeMaven(1, false, null);
        apply("approved");
        assertEquals("REVERTED", lastAuditRow().get("finalStatus"));
        assertNothingMarkedFixed();
    }

    @Test
    void targetStillFailing_rollsBack_andMarksNothingFixed() throws Exception {
        fakeMaven(0, true, null);
        apply("approved");
        assertEquals("REVERTED", lastAuditRow().get("finalStatus"));
        assertNothingMarkedFixed();
    }

    @Test
    void aNewDownstreamFailure_rollsBack_andMarksNothingFixed() throws Exception {
        fakeMaven(0, false, "A later step");
        apply("approved");
        assertEquals("REVERTED", lastAuditRow().get("finalStatus"));
        assertNothingMarkedFixed();
    }

    @Test
    void declinedOrRefused_marksNothingFixed() throws Exception {
        fakeMaven(0, false, null);
        apply("declined");
        assertEquals("NOT_APPLIED", lastAuditRow().get("finalStatus"));
        assertNothingMarkedFixed();
    }

    // ================================================================== a report problem never changes the code-fix outcome

    @Test
    void ifTheReportCannotBeWritten_thePatchStaysKept_andTheProblemIsRecorded() throws Exception {
        // make the report location impossible: failure-analysis/reports is a regular FILE, so reports cannot be created in it
        Files.createDirectories(out.resolve("failure-analysis"));
        Files.writeString(out.resolve("failure-analysis").resolve("reports"), "blocker");
        fakeMaven(0, false, null);
        apply("approved");

        String patched = Files.readString(source);
        assertTrue(patched.contains(NEW) && !patched.contains(OLD), "the verified code fix is kept regardless");
        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"), "the outcome is unchanged by a report problem");
        assertEquals("PASS", r.get("rerunResult"));
        assertTrue(r.get("reason").startsWith("Failure Analysis report"), r.get("reason"));
        assertTrue(r.get("reason").contains("NOT refreshed") || r.get("reason").contains("FAILED"),
                "the problem is recorded clearly: " + r.get("reason"));
        assertTrue(r.get("reason").contains("kept"), "and it says the fix was kept: " + r.get("reason"));
        assertTrue(Files.readString(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")).contains("RESOLVED"),
                "the resolution itself is still recorded in the tracker");
        assertTrue(unifiedReport().contains("HEALED AND VERIFIED - KEEP"), "and the final report still states the real outcome");
    }

    // ================================================================== the building blocks

    @Test
    void refreshAfterFix_withNoSnapshot_writesNothing() {
        assertNull(FailureAnalysisAgent.refreshAfterFix(out.toString(), "com.kpj.tests.Syn.SynTest", null));
        assertNull(FailureAnalysisAgent.refreshAfterFix(out.toString(), "com.kpj.tests.Syn.SynTest", List.of()));
        assertFalse(Files.exists(out.resolve("failure-analysis")), "nothing to refresh means no files");
    }

    @Test
    void snapshotBeforeFix_describesTheOpenFailure_andRefreshShowsItResolvedOnlyOnceTheTrackerSaysSo() throws Exception {
        List<FailureAnalysisRecord> snap = FailureAnalysisAgent.snapshotBeforeFix(out.toString(), TEST_ID, "com.kpj.tests.Syn.SynTest");
        assertEquals(1, snap.size());
        assertEquals(STEP, snap.get(0).step);
        assertEquals("OPEN", snap.get(0).finalStatus, "before any fix the tracker says OPEN");
        assertTrue(snap.get(0).failureActual.contains("LocationId"), "the snapshot holds the ORIGINAL failure");

        // the tracker row is what turns the refreshed report to RESOLVED
        new ResolutionTracker(out.toString()).recordFix(ResolutionTracker.signatureFor(TEST_ID, STEP),
                "Self-Healing Agent (automated, human-approved)", "Automation Tester", "fix", "PASS", "RESOLVED");
        FailureAnalysisReport.Result res = FailureAnalysisAgent.refreshAfterFix(out.toString(), "com.kpj.tests.Syn.SynTest", snap);
        assertNotNull(res);
        assertTrue(Files.readString(faReport()).contains("<th>Final Status</th><td>RESOLVED</td>"));
    }
}
