package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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
 * Safe, LOCAL, SYNTHETIC tests of the two verification guarantees of {@code apply-pending}:
 * <ol>
 *   <li>a patch is KEPT only on POSITIVE evidence - a clean Maven exit code AND a PASS row for the target step written by the
 *       re-run; a missing step, missing or stale output, or a non-zero exit code can never produce a KEEP;</li>
 *   <li>REVERTED is reported only when the restoration was verified; otherwise the status is RESTORE_FAILED and every report
 *       says the source file may still contain the patch.</li>
 * </ol>
 * The "healed" source and every output folder are temp files and {@link SelfHealingAgent#mavenLauncher} is a fake: no Maven,
 * browser or DevHIS is used, and no real project file is patched.
 */
class ApplyPendingEvidenceTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private Path source, out, pendingFile;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");
        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");

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

    private Path tsv() { return out.resolve("aggregate-data").resolve("SYN.tsv"); }

    private void writeResult(boolean targetFails, String extraFailingStep) throws IOException {
        Files.createDirectories(out.resolve("aggregate-data"));
        StringBuilder sb = new StringBuilder();
        sb.append(row("PASS", "Login", "Authenticated", "Logged in"));
        sb.append(row(targetFails ? "FAIL" : "PASS", STEP, "element found",
                targetFails ? "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'" : "element found"));
        if (extraFailingStep != null) sb.append(row("FAIL", extraFailingStep, "something", "broke after the patch"));
        Files.writeString(tsv(), sb.toString());
    }

    private static String row(String status, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, expected, actual, "") + "\n";
    }

    /** What the fake "re-run" of the test does and returns. Compile always succeeds (exit 0). */
    private interface Rerun { int run() throws IOException; }

    private void fakeMaven(Rerun rerun) {
        SelfHealingAgent.mavenLauncher = args -> {
            if (Arrays.asList(args).contains("test-compile")) return 0;
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) return rerun.run();
            return 0;
        };
    }

    private void apply() throws Exception {
        ApplyTestSupport.apply(pendingFile, "approved");   // quotes the proposal's own hash
    }

    // ------------------------------------------------------------------ helpers

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

    /** Every HTML report the healing dashboard wrote. */
    private String dashboardHtml() throws IOException {
        try (Stream<Path> s = Files.walk(out.resolve("self-healing"))) {
            return s.filter(p -> p.toString().endsWith(".html")).map(p -> {
                try { return Files.readString(p); } catch (IOException e) { return ""; }
            }).collect(Collectors.joining("\n"));
        }
    }

    private void assertPatchedAndKept() throws IOException {
        String now = Files.readString(source);
        assertTrue(now.contains(NEW) && !now.contains(OLD), "the verified fix is in the source");
    }

    private void assertCleanRollback(String reasonFragment) throws Exception {
        assertArrayEquals(ORIGINAL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source),
                "the source file is restored byte-for-byte");
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertTrue(r.get("reason").contains(reasonFragment), "reason: " + r.get("reason"));
        assertEquals("true", r.get("reverted"));
        assertEquals("true", r.get("restorationVerified"), "the verified restoration is recorded in the audit trail");
        assertFalse(Files.exists(out.resolve("failure-analysis")), "nothing is marked fixed / no Failure Analysis refresh");
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")));
        String u = unifiedReport();
        assertTrue(u.contains("ROLLBACK - NOT HEALED") && !u.contains("HEALED AND VERIFIED"), "the unified report shows a rollback");
    }

    // ================================================================== KEEP needs positive PASS evidence

    @Test
    void anExistingSuccessfulKeep_stillWorks_withPositiveEvidence() throws Exception {
        fakeMaven(() -> { writeResult(false, null); return 0; });
        apply();
        assertPatchedAndKept();
        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"));
        assertEquals("PASS", r.get("compileResult"));
        assertEquals("PASS", r.get("rerunResult"));
        assertEquals("", r.get("restorationVerified"), "no rollback happened, so no restoration is claimed");
        assertTrue(unifiedReport().contains("HEALED AND VERIFIED - KEEP"));
        assertTrue(Files.readString(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")).contains("RESOLVED"));
    }

    @Test
    void aMissingTargetStep_withNoFailRow_isNotAKeep() throws Exception {
        // the re-run records only Login: the target step is absent, and there is no FAIL row anywhere
        fakeMaven(() -> {
            Files.writeString(tsv(), row("PASS", "Login", "Authenticated", "Logged in"));
            return 0;
        });
        apply();
        assertCleanRollback("insufficient PASS evidence");
        Map<String, String> r = lastAuditRow();
        assertTrue(r.get("reason").contains("no PASS result for step"), r.get("reason"));
        assertEquals("UNVERIFIED", r.get("rerunResult"), "the target step is reported as unverified, never as PASS");
    }

    @Test
    void missingOutput_isNotAKeep() throws Exception {
        // the re-run produced no result file at all (and the earlier one is gone)
        fakeMaven(() -> { Files.deleteIfExists(tsv()); return 0; });
        apply();
        assertCleanRollback("insufficient PASS evidence");
        assertTrue(lastAuditRow().get("reason").contains("no new test result"), lastAuditRow().get("reason"));
    }

    @Test
    void aStalePassResult_writtenBeforeTheRerun_isNotEvidence() throws Exception {
        // an old result file that says PASS for the step, left behind by an earlier run; the re-run itself writes nothing
        // (the original failing result is gone afterwards, so only the stale PASS file remains)
        Path stale = out.resolve("aggregate-data").resolve("SYN_OLD.tsv");
        Files.writeString(stale, row("PASS", "Login", "Authenticated", "Logged in") + row("PASS", STEP, "element found", "element found"));
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minusSeconds(3600)));
        fakeMaven(() -> { Files.deleteIfExists(tsv()); return 0; });
        apply();
        assertCleanRollback("insufficient PASS evidence");
        assertTrue(lastAuditRow().get("reason").contains("no new test result"), lastAuditRow().get("reason"));
    }

    @Test
    void aNonZeroMavenExitCode_isNotAKeep_evenIfTheResultFileSaysPass() throws Exception {
        fakeMaven(() -> { writeResult(false, null); return 1; });
        apply();
        assertCleanRollback("exit code was 1");
        assertEquals("UNVERIFIED", lastAuditRow().get("rerunResult"));
    }

    @Test
    void aStepStillFailing_keepsItsOwnReason_andRollsBack() throws Exception {
        fakeMaven(() -> { writeResult(true, null); return 0; });
        apply();
        assertCleanRollback("target step still fails");
        assertEquals("FAIL", lastAuditRow().get("rerunResult"));
    }

    // ================================================================== REVERTED only when restoration is verified

    @Test
    void aVerifiedRollback_isReportedAsReverted_withTheRestorationRecorded() throws Exception {
        fakeMaven(() -> { writeResult(true, null); return 0; });
        apply();
        assertCleanRollback("target step still fails");
        assertTrue(unifiedReport().contains("restoration was verified"));
        String d = dashboardHtml();
        assertTrue(d.contains("restored from the backup, and the restored file was verified"), "the healing report states the verified restoration");
        assertFalse(d.contains("ROLLBACK FAILED"));
    }

    @Test
    void aFailedRollback_isRestoreFailed_andSaysThePatchMayStillBeInTheSource() throws Exception {
        // the file cannot be restored: during the re-run the source file is replaced by a directory, so both the
        // backup restore and the in-memory fallback fail to write it
        fakeMaven(() -> {
            writeResult(true, null);
            Files.delete(source);
            Files.createDirectory(source);
            return 0;
        });
        apply();

        Map<String, String> r = lastAuditRow();
        assertEquals("RESTORE_FAILED", r.get("finalStatus"), "never REVERTED when the restoration was not verified");
        assertEquals("false", r.get("reverted"));
        assertEquals("false", r.get("restorationVerified"));
        assertTrue(r.get("reason").contains("target step still fails"), "the original reason is kept: " + r.get("reason"));
        assertTrue(r.get("reason").contains("MAY STILL CONTAIN THE PATCH"), r.get("reason"));
        assertTrue(r.get("backupCreated").equals("true") && r.get("patchApplied").equals("true"));
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")), "nothing is marked fixed");

        String u = unifiedReport();
        assertTrue(u.contains("RESTORE_FAILED - MANUAL RESTORE REQUIRED"), "the unified report banner");
        assertTrue(u.contains("MAY STILL CONTAIN THE PATCH"));
        assertFalse(u.contains("ROLLBACK - NOT HEALED"), "it must not claim a clean rollback");
        assertFalse(u.contains("HEALED AND VERIFIED"));
        assertTrue(u.contains("Restoration verified"));

        String d = dashboardHtml();
        assertTrue(d.contains("ROLLBACK FAILED"), "the healing dashboard states the failed rollback");
        assertTrue(d.contains("RESTORE_FAILED"));
        assertTrue(d.contains("may still contain the patch"));
        assertFalse(d.contains("the original source code was restored from the backup, and the restored file was verified"),
                "and does not claim a verified restoration");
    }

    @Test
    void aFailedRollbackAfterACompileFailure_isAlsoRestoreFailed() throws Exception {
        SelfHealingAgent.mavenLauncher = args -> {
            if (Arrays.asList(args).contains("test-compile")) {
                Files.delete(source);
                Files.createDirectory(source);
                return 1;   // the compile fails and the file then cannot be restored
            }
            return 0;
        };
        apply();
        Map<String, String> r = lastAuditRow();
        assertEquals("RESTORE_FAILED", r.get("finalStatus"));
        assertEquals("FAIL", r.get("compileResult"));
        assertTrue(r.get("reason").startsWith("compile failed after patch"), r.get("reason"));
        assertTrue(unifiedReport().contains("RESTORE_FAILED - MANUAL RESTORE REQUIRED"));
    }

    // ================================================================== audit file compatibility

    @Test
    void anOlderAuditFile_getsTheNewColumnInItsHeader_withoutChangingExistingRows() throws Exception {
        HealingAttemptRecord probe = new HealingAttemptRecord();
        List<String> cols = new HealingAuditTrail(out.toString()).columns();
        String oldHeader = String.join("\t", cols.subList(0, 27));
        String oldRow = "2026-01-01T00:00:00\tOLD_TEST\tc\ts\tf\ta\tm\tDETERMINISTIC\tLOCATOR\t1/1\t75%\tLOW\trc\tAPPROVED\ty\ttrue\ttrue\tPASS\tPASS\tNONE\tKEPT\tfalse\treason\told\tnew\tPASSED\tj";
        Path file = out.resolve("failures").resolve("healing-history.tsv");
        Files.createDirectories(file.getParent());
        Files.writeString(file, oldHeader + "\n" + oldRow + "\n");

        probe.testId = "NEW_TEST";
        probe.finalStatus = "RESTORE_FAILED";
        probe.restorationVerified = false;
        new HealingAuditTrail(out.toString()).append(probe);

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).endsWith("\trestorationMethod"), "the header names the new columns");
        assertEquals(oldRow, lines.get(1), "the existing row is untouched");
        assertEquals("false", lines.get(2).split("\t", -1)[27], "the new row carries restorationVerified=false");
        assertEquals(cols.size(), lines.get(2).split("\t", -1).length, "and has exactly one cell per column");
    }
}
