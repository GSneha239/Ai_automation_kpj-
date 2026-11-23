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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC end-to-end tests of the REAL approval path: {@code SelfHealingAgent.main("apply-pending", file,
 * decision)} - approval handling, backup, apply, compile, re-run, verification, KEEP / ROLLBACK, audit trail and the final
 * reports. The source file being "healed" and every output folder are temp files.
 *
 * <p>The only substitute is {@link SelfHealingAgent#mavenLauncher}: a fake that records every Maven call and, for the
 * test re-run, writes the synthetic result the scenario wants into {@code aggregate-data}. So there is no Maven, no
 * browser and no DevHIS, and no real project file is ever patched.</p>
 */
class ApplyPendingWorkflowTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "// synthetic page object é\r\nclass SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private Path source;
    private Path out;
    private Path pendingFile;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");
        // the ORIGINAL failing run, as the agents would have found it
        writeResult(true, null);
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
        p.reason = "Raw ng-model match: closest live ng-model is staffScheduleInfo.Locationid";
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
        ApplyTestSupport.uninstallLocatorFixture();
    }

    // ------------------------------------------------------------------ fake Maven + synthetic results

    /** Writes the synthetic result of a (re-)run of the test into aggregate-data, as DevHisBase would. */
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

    /** compileExit: 0 = compiles. targetStillFails / extraFailingStep describe what the fake "re-run" reports. */
    private void fakeMaven(int compileExit, boolean targetStillFails, String extraFailingStep) {
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.asList(args).contains("test-compile")) return compileExit;
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) writeResult(targetStillFails, extraFailingStep);
            return 0;
        };
    }

    private void apply(String decision) throws Exception {
        ApplyTestSupport.apply(pendingFile, decision);   // an "approved" decision quotes the proposal's own hash
    }

    // ------------------------------------------------------------------ result helpers

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private String unifiedReport() throws IOException {
        Path dir = out.resolve("unified-report");
        assertTrue(Files.isDirectory(dir), "a unified report is generated after apply-pending");
        try (Stream<Path> s = Files.list(dir)) {
            Path newest = s.sorted().reduce((a, b) -> b).orElseThrow();
            return Files.readString(newest);
        }
    }

    private long backups() throws IOException {
        Path dir = out.resolve("self-healing").resolve("backups");
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) { return s.count(); }
    }

    private Path theBackup() throws IOException {
        try (Stream<Path> s = Files.list(out.resolve("self-healing").resolve("backups"))) { return s.findFirst().orElseThrow(); }
    }

    private boolean anyHtmlUnderSelfHealingContains(String text) throws IOException {
        Path dir = out.resolve("self-healing");
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".html")).collect(Collectors.toList())) {
                if (Files.readString(p).contains(text)) return true;
            }
        }
        return false;
    }

    // ================================================================== declined / not applied

    @Test
    void declined_neverChangesTheSource_runsNoMaven_andIsReportedAsNotApplied() throws Exception {
        fakeMaven(0, false, null);
        byte[] before = Files.readAllBytes(source);
        apply("declined");
        assertArrayEquals(before, Files.readAllBytes(source), "source is byte-identical after a declined approval");
        assertEquals(0, backups(), "no backup for a change that was never applied");
        assertTrue(mavenCalls.isEmpty(), "no compile and no re-run for a declined change: " + mavenCalls);
        Map<String, String> r = lastAuditRow();
        assertEquals("NOT_APPLIED", r.get("finalStatus"));
        assertEquals("false", r.get("patchApplied"));
        assertTrue(unifiedReport().contains("NOT APPLIED - NOT HEALED"));
        assertFalse(Files.exists(pendingFile), "the resolved pending proposal is removed so it can never be re-applied");
    }

    @Test
    void anUnrecognisedDecisionWord_isTreatedAsDeclined() throws Exception {
        fakeMaven(0, false, null);
        byte[] before = Files.readAllBytes(source);
        apply("yes");   // only the exact word "approved" approves
        assertArrayEquals(before, Files.readAllBytes(source));
        assertTrue(mavenCalls.isEmpty());
        assertEquals("NOT_APPLIED", lastAuditRow().get("finalStatus"));
    }

    @Test
    void aStalePendingProposal_whoseSnippetNoLongerMatches_isRefusedEvenWhenApproved() throws Exception {
        fakeMaven(0, false, null);
        Files.writeString(source, ORIGINAL.replace(OLD, "// the file was edited after the proposal was made"));
        byte[] before = Files.readAllBytes(source);
        apply("approved");
        assertArrayEquals(before, Files.readAllBytes(source), "an approval cannot patch text that is no longer there");
        assertEquals(0, backups());
        assertTrue(mavenCalls.isEmpty());
        assertEquals("NOT_APPLIED", lastAuditRow().get("finalStatus"));
    }

    // ================================================================== approved -> KEEP

    @Test
    void approved_backsUpFirst_compiles_reruns_verifies_andKeeps() throws Exception {
        fakeMaven(0, false, null);
        byte[] before = Files.readAllBytes(source);
        apply("approved");

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test"), mavenCalls, "compile, then re-run - in that order");
        assertEquals(1, backups());
        assertArrayEquals(before, Files.readAllBytes(theBackup()), "the backup is the exact original");
        String patched = Files.readString(source);
        assertTrue(patched.contains(NEW) && !patched.contains(OLD), "the approved change is kept");

        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"));
        assertEquals("true", r.get("backupCreated"));
        assertEquals("true", r.get("patchApplied"));
        assertEquals("PASS", r.get("compileResult"));
        assertEquals("PASS", r.get("rerunResult"));
        assertEquals("false", r.get("reverted"));

        String report = unifiedReport();
        assertTrue(report.contains("HEALED AND VERIFIED - KEEP"), "the final report states the real outcome");
        assertTrue(anyHtmlUnderSelfHealingContains("KEPT"), "the Self-Healing report records KEPT");
        assertFalse(Files.exists(pendingFile));
    }

    @Test
    void aSuccessfulKeep_updatesTheResolutionTracker_andNowAlsoRefreshesTheFailureAnalysisReport() throws Exception {
        fakeMaven(0, false, null);
        apply("approved");
        Path tracker = out.resolve("failure-analysis").resolve("resolution-tracking.tsv");
        assertTrue(Files.isRegularFile(tracker), "the fix is recorded in the resolution tracker");
        assertTrue(Files.readString(tracker).contains("RESOLVED"));
        // Previously a documented gap (no Failure Analysis report was written on KEEP). Fixed: a KEEP now refreshes the
        // report, summary and index; the detailed assertions live in ApplyPendingFaRefreshTest.
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("reports")), "a Failure Analysis report is written on KEEP");
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("summary")));
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("index")));
    }

    // ================================================================== approved -> ROLLBACK

    @Test
    void compileFailure_rollsBackToTheExactOriginal_andNeverReruns() throws Exception {
        fakeMaven(1, false, null);
        byte[] before = Files.readAllBytes(source);
        apply("approved");

        assertEquals(List.of("-q test-compile"), mavenCalls, "a compile failure stops before any re-run");
        assertArrayEquals(before, Files.readAllBytes(source), "rollback restores the exact original bytes");
        assertEquals(1, backups(), "the backup was taken before the change and is kept");
        assertArrayEquals(before, Files.readAllBytes(theBackup()));

        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("FAIL", r.get("compileResult"));
        assertEquals("true", r.get("reverted"));
        assertTrue(r.get("reason").contains("compile failed"));
        String report = unifiedReport();
        assertTrue(report.contains("ROLLBACK - NOT HEALED"));
        assertFalse(report.contains("HEALED AND VERIFIED"));
    }

    @Test
    void targetStepStillFailing_afterTheRerun_rollsBack() throws Exception {
        fakeMaven(0, true, null);
        byte[] before = Files.readAllBytes(source);
        apply("approved");

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test"), mavenCalls);
        assertArrayEquals(before, Files.readAllBytes(source), "a failed verification restores the original");
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("PASS", r.get("compileResult"));
        assertEquals("FAIL", r.get("rerunResult"));
        assertEquals("target step still fails", r.get("reason"));
        assertTrue(unifiedReport().contains("ROLLBACK - NOT HEALED"));
        assertTrue(anyHtmlUnderSelfHealingContains("REVERTED"), "the Self-Healing report records REVERTED");
    }

    @Test
    void aNewDownstreamFailure_afterThePatch_rollsBack_evenThoughTheTargetStepNowPasses() throws Exception {
        fakeMaven(0, false, "A later step");
        byte[] before = Files.readAllBytes(source);
        apply("approved");

        assertArrayEquals(before, Files.readAllBytes(source));
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("PASS", r.get("rerunResult"), "the target step itself passed");
        assertTrue(r.get("reason").contains("downstream regression"), r.get("reason"));
        assertTrue(unifiedReport().contains("ROLLBACK - NOT HEALED"), "a regression is never reported as a heal");
    }

    @Test
    void aRolledBackProposal_isRememberedSoItIsNotOfferedAgain() throws Exception {
        fakeMaven(0, true, null);
        apply("approved");
        Path session = out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties");
        assertTrue(Files.isRegularFile(session), "the session records the failed attempt");
        String s = Files.readString(session);
        assertTrue(s.contains("triedFailedSignatures"), s);
        assertFalse(s.matches("(?s).*triedFailedSignatures=\\s*\\n.*"), "the rolled-back candidate signature is stored: " + s);
    }
}
