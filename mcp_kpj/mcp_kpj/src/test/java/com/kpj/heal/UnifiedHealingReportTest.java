package com.kpj.heal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests for {@link UnifiedHealingReport}. Every artifact is fabricated in a temp folder using
 * the real audit-trail column schema. No browser, no login, no network, no DevHIS, no application test is run.
 *
 * <p>The central guarantee under test: the report says "HEALED AND VERIFIED" only when the audit trail records KEPT with
 * compile PASS and re-run PASS - it never invents a successful heal.</p>
 */
class UnifiedHealingReportTest {

    @TempDir
    Path out;

    private static final String OLD = "  + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "  + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final DateTimeFormatter BACKUP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    // ------------------------------------------------------------------ helpers

    private Map<String, String> row(String testId, String finalStatus, String compile, String rerun,
                                    boolean applied, boolean reverted, LocalDateTime ts) {
        Map<String, String> m = new HashMap<>();
        m.put("timestamp", ts.toString());
        m.put("testId", testId);
        m.put("testClass", "com.kpj.tests.Synthetic.SyntheticDemoTest");
        m.put("step", "Probe Location selector");
        m.put("failure", "expected=element found | actual=FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'");
        m.put("affectedFile", "src\\test\\java\\com\\kpj\\pages\\Synthetic\\SyntheticPage.java");
        m.put("affectedMethod", "probeLocationSelect()");
        m.put("source", "DETERMINISTIC");
        m.put("proposalKind", "LOCATOR");
        m.put("candidate", "1/1");
        m.put("confidence", "75%");
        m.put("risk", "LOW");
        m.put("rootCause", "Stale locator literal - one-character case typo");
        m.put("humanDecision", "APPROVED");
        m.put("approval", "y");
        m.put("backupCreated", String.valueOf(applied));
        m.put("patchApplied", String.valueOf(applied));
        m.put("compileResult", compile);
        m.put("rerunResult", rerun);
        m.put("regressionResult", "NONE");
        m.put("finalStatus", finalStatus);
        m.put("reverted", String.valueOf(reverted));
        m.put("reason", "");
        m.put("oldSnippet", OLD);
        m.put("newSnippet", NEW);
        m.put("semanticValidation", "PASSED");
        m.put("semanticJustification", "Id and id share the concept identifier");
        return m;
    }

    @SafeVarargs
    private void writeAudit(Map<String, String>... rows) throws IOException {
        Path dir = out.resolve("failures");
        Files.createDirectories(dir);
        List<String> cols = new HealingAuditTrail(out.toString()).columns();
        StringBuilder sb = new StringBuilder(String.join("\t", cols)).append('\n');
        for (Map<String, String> r : rows) {
            for (int i = 0; i < cols.size(); i++) sb.append(r.getOrDefault(cols.get(i), "")).append(i < cols.size() - 1 ? "\t" : "");
            sb.append('\n');
        }
        Files.writeString(dir.resolve("healing-history.tsv"), sb.toString());
    }

    private void writeFailureCapture(String testId) throws IOException {
        Path dir = out.resolve("failures");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(testId + "_3.txt"),
                "testId=" + testId + "\nstep=Probe Location selector\nexpected=element found\n"
                        + "actual=FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'\n"
                        + "url=file:///synthetic/staff-schedule-local.html\n");
    }

    private void writePending(String testId, LocalDateTime createdAt) throws IOException {
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = testId;
        p.testClass = "com.kpj.tests.Synthetic.SyntheticDemoTest";
        p.simpleName = "SyntheticDemoTest";
        p.step = "Probe Location selector";
        p.affectedFile = "src\\test\\java\\com\\kpj\\pages\\Synthetic\\SyntheticPage.java";
        p.affectedMethod = "probeLocationSelect()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = OLD;
        p.newSnippet = NEW;
        p.confidence = 0.75;
        p.risk = "LOW";
        p.rootCause = "Stale locator literal - one-character case typo";
        p.semanticValidation = "PASSED";
        p.createdAt = createdAt.toString();
        Path dir = out.resolve("self-healing").resolve("pending");
        Files.createDirectories(dir);
        p.save(dir.resolve(testId + "__Probe.properties"));
    }

    private String read(Path p) throws IOException { return Files.readString(p); }

    /** Copies a report into the build folder so a sample can be opened later (target/ is untracked build output). */
    private void sample(Path report, String name) throws IOException {
        Path dir = Paths.get("target", "unified-report-sample");
        Files.createDirectories(dir);
        Files.copy(report, dir.resolve(name + ".html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void keptWithPassingCompileAndRerun_isHealedAndVerified() throws IOException {
        LocalDateTime ts = LocalDateTime.now().minusMinutes(2);
        String id = "SYNTHETIC_SAMPLE_Verified";
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, ts));
        writeFailureCapture(id);
        Path backups = out.resolve("self-healing").resolve("backups");
        Files.createDirectories(backups);
        Files.writeString(backups.resolve("SyntheticPage.java." + BACKUP_FMT.format(ts.plusSeconds(3)) + ".bak"), "x");

        Path report = UnifiedHealingReport.generate(out.toString(), id);
        assertNotNull(report);
        assertTrue(report.toString().contains("unified-report"));
        String html = read(report);
        assertTrue(html.contains("HEALED AND VERIFIED - KEEP"), "verified heal must be labelled");
        assertTrue(html.contains("staffScheduleInfo.LocationId") && html.contains("staffScheduleInfo.Locationid"), "diff shows both locators");
        assertTrue(html.contains("UNVERIFIED (inferred)"), "backup path is inferred and must be labelled so");
        assertTrue(html.contains("SyntheticPage.java."), "inferred backup file is named");
        assertFalse(html.contains("ROLLBACK"), "a kept heal must not claim rollback");
        sample(report, "SAMPLE_1_healed_and_verified");
    }

    @Test
    void reverted_isRollbackAndNeverHealed() throws IOException {
        String id = "SYNTHETIC_Reverted";
        writeAudit(row(id, "REVERTED", "PASS", "FAIL", true, true, LocalDateTime.now().minusMinutes(2)));
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("ROLLBACK - NOT HEALED"));
        assertFalse(html.contains("HEALED AND VERIFIED"));
    }

    @Test
    void keptWithoutRerunEvidence_isNotClaimedVerified() throws IOException {
        String id = "SYNTHETIC_KeptNoRerun";
        writeAudit(row(id, "KEPT", "PASS", "N/A", true, false, LocalDateTime.now().minusMinutes(2)));
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("KEPT - UNVERIFIED"));
        assertFalse(html.contains("HEALED AND VERIFIED"), "missing re-test evidence must never read as verified");
        assertTrue(html.contains("NOT AVAILABLE"), "the missing re-test is labelled");
    }

    @Test
    void proposalOnly_isProposedAwaitingApproval() throws IOException {
        String id = "SYNTHETIC_SAMPLE_Proposed";
        writeFailureCapture(id);
        writePending(id, LocalDateTime.now().minusMinutes(1));
        Path report = UnifiedHealingReport.generate(out.toString(), id);
        String html = read(report);
        assertTrue(html.contains("PROPOSED, awaiting approval"));
        assertTrue(html.contains("NOT RUN YET"), "compile / re-test must read as not run yet");
        assertTrue(html.contains("Proposed change only - not applied"));
        assertFalse(html.contains("HEALED AND VERIFIED"));
        assertFalse(html.contains("ROLLBACK"));
        sample(report, "SAMPLE_2_proposed_awaiting_approval");
    }

    @Test
    void laterDecisionSupersedesOlderPending() throws IOException {
        String id = "SYNTHETIC_PendingThenDecided";
        writePending(id, LocalDateTime.now().minusMinutes(10));
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, LocalDateTime.now().minusMinutes(2)));
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("HEALED AND VERIFIED - KEEP"));
        assertFalse(html.contains("PROPOSED, awaiting approval"));
    }

    @Test
    void dryRunOnly_hasNoHealingResult() throws IOException {
        String id = "SYNTHETIC_DryRun";
        writeAudit(row(id, "DRY_RUN", "N/A", "N/A", false, false, LocalDateTime.now().minusMinutes(2)));
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("NO HEALING RESULT - analysis only"));
        assertFalse(html.contains("HEALED AND VERIFIED"));
    }

    @Test
    void failureCaptureOnly_hasNoHealingResultButShowsFailure() throws IOException {
        String id = "SYNTHETIC_CaptureOnly";
        writeFailureCapture(id);
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("NO HEALING RESULT"));
        assertTrue(html.contains("element found"));
        assertFalse(html.contains("HEALED AND VERIFIED"));
    }

    @Test
    void noEvidence_writesNothing() throws IOException {
        assertNull(UnifiedHealingReport.generate(out.toString(), "SYNTHETIC_DoesNotExist"));
        assertFalse(Files.exists(out.resolve("unified-report")), "no folder or file is created without evidence");
    }

    @Test
    void neverOverwritesAnExistingReport() throws IOException {
        String id = "SYNTHETIC_NoOverwrite";
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, LocalDateTime.now().minusMinutes(2)));
        Path first = UnifiedHealingReport.generate(out.toString(), id);
        Path second = UnifiedHealingReport.generate(out.toString(), id);
        assertNotEquals(first, second, "a second report in the same second gets a new name");
        assertTrue(Files.exists(first) && Files.exists(second));
    }

    @Test
    void resolvesByClassNameAndLeavesExistingArtifactsUntouched() throws IOException {
        String id = "SYNTHETIC_ByClassName";
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, LocalDateTime.now().minusMinutes(2)));
        Path audit = out.resolve("failures").resolve("healing-history.tsv");
        String before = read(audit);
        Path report = UnifiedHealingReport.generate(out.toString(), "SyntheticDemoTest");
        assertNotNull(report, "found by simple class name");
        assertEquals(before, read(audit), "the audit trail is read-only for the generator");
    }

    // ------------------------------------------------------------------ failed step from the failure capture

    /** A failure capture whose recorded step is {@code step}. */
    private void writeCaptureWithStep(String testId, String step) throws IOException {
        Path dir = out.resolve("failures");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(testId + "_6.txt"),
                "testId=" + testId + "\nstep=" + step + "\nexpected=The search returns matching complaints\n"
                        + "actual=FAILS - no rows loaded\nurl=file:///synthetic/page.html\n");
    }

    @Test
    void captureOnly_failedStepComesFromTheCapture() throws IOException {
        String id = "SYNTHETIC_StepFromCapture";
        writeCaptureWithStep(id, "Click Search & verify the result");
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("<th>Failed step</th><td>Click Search &amp; verify the result</td>"),
                "the step recorded in the capture is shown");
        assertFalse(html.contains("no step recorded"));
        assertTrue(html.contains("NO HEALING RESULT"), "no healing evidence, so still no healing result");
    }

    @Test
    void auditStepStillWinsOverTheCapture() throws IOException {
        String id = "SYNTHETIC_AuditStepWins";
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, LocalDateTime.now().minusMinutes(2)));
        writeCaptureWithStep(id, "A different step recorded only in the capture");
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("<th>Failed step</th><td>Probe Location selector</td>"), "audit behaviour is unchanged");
        assertFalse(html.contains("<th>Failed step</th><td>A different step"));
    }

    // ------------------------------------------------------------------ Failure Analysis details

    /** A Failure Analysis report laid out like the real one: a "Failed Step" row opens each section. */
    private void writeFailureAnalysisReport(String simple, String[]... steps) throws IOException {
        StringBuilder h = new StringBuilder("<html><body><table class='plain'><tr><th>Failure Summary</th><td>summary</td></tr>");
        for (String[] s : steps) { // {step, category, owner, rootCause}
            h.append("<tr><th>Failed Step</th><td>").append(s[0]).append("</td></tr>")
             .append("<tr><th>Root Cause</th><td>").append(s[3]).append("</td></tr>")
             .append("<tr><th>Failed Step</th><td>").append(s[0]).append("</td></tr>")
             .append("<tr><th>Automatic Fixing Possible</th><td>NO</td></tr>")
             .append("<tr><th>Failed Step</th><td>").append(s[0]).append("</td></tr>")
             .append("<tr><th>Failure Category</th><td>").append(s[1]).append("</td></tr>")
             .append("<tr><th>Severity</th><td>MEDIUM</td></tr>")
             .append("<tr><th>Recommended Owner</th><td>").append(s[2]).append("</td></tr>")
             .append("<tr><th>Current Status</th><td>OPEN</td></tr>");
        }
        h.append("</table></body></html>");
        Path dir = out.resolve("failure-analysis").resolve("reports").resolve(simple + "_20260101_000000");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(simple + "_FailureAnalysisReport.html"), h.toString());
    }

    @Test
    void failureAnalysis_categoryOwnerAndExplicitRootCauseAreShown() throws IOException {
        String id = "SYNTHETIC_FaExplicit";
        writeCaptureWithStep(id, "Select Store");
        writeFailureAnalysisReport(id, new String[]{"Select Store", "Locator / Automation Script Issue", "Automation Engineer",
                "A hard-coded locator no longer matches the live page."});
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("Locator / Automation Script Issue"));
        assertTrue(html.contains("Automation Engineer"));
        assertTrue(html.contains("A hard-coded locator no longer matches the live page."));
        assertTrue(html.contains("(source: Failure Analysis report)"), "the root cause is attributed to its source");
        assertTrue(html.contains("NO HEALING RESULT"), "analysis details never turn into a healing result");
    }

    @Test
    void failureAnalysis_undeterminedRootCause_isShownAsNotAvailable() throws IOException {
        String id = "SYNTHETIC_FaUndetermined";
        writeCaptureWithStep(id, "Click Search & verify the result");
        writeFailureAnalysisReport(id, new String[]{"Click Search &amp; verify the result", "Unknown / Needs Manual Investigation",
                "Manual Tester / QA", "The system could not confidently determine why this failed. A person should take a look."});
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("<th>Root cause</th><td><span class='tag na'>NOT AVAILABLE</span>"), "no root cause is invented");
        assertTrue(html.contains("has not determined one"), "the Failure Analysis statement is quoted, not hidden");
        assertFalse(html.contains("(source: Failure Analysis report)"));
        assertTrue(html.contains("Unknown / Needs Manual Investigation") && html.contains("Manual Tester / QA"),
                "category and owner are still shown because they are explicit");
    }

    @Test
    void failureAnalysis_missingReport_isLabelledNotInvented() throws IOException {
        String id = "SYNTHETIC_FaMissing";
        writeCaptureWithStep(id, "Select Store");
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("no Failure Analysis report found for this test"));
        assertTrue(html.contains("<th>Root cause</th><td><span class='tag na'>NOT AVAILABLE</span>"));
    }

    @Test
    void failureAnalysis_usesOnlyTheRowsOfTheMatchingStep() throws IOException {
        String id = "SYNTHETIC_FaTwoSteps";
        writeCaptureWithStep(id, "Step B");
        writeFailureAnalysisReport(id,
                new String[]{"Step A", "Category A", "Owner A", "Cause A is explicit."},
                new String[]{"Step B", "Category B", "Owner B", "Cause B is explicit."});
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("Owner B") && html.contains("Category B") && html.contains("Cause B is explicit."));
        assertFalse(html.contains("Owner A") || html.contains("Category A") || html.contains("Cause A"),
                "rows of another step are never borrowed");
    }

    @Test
    void healingRootCauseWinsOverFailureAnalysis() throws IOException {
        String id = "SYNTHETIC_RootCausePrecedence";
        writeAudit(row(id, "KEPT", "PASS", "PASS", true, false, LocalDateTime.now().minusMinutes(2)));
        writeFailureAnalysisReport("SyntheticDemoTest", new String[]{"Probe Location selector", "Locator Issue", "Automation Engineer",
                "A different cause stated only by Failure Analysis."});
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("Stale locator literal - one-character case typo"), "healing analysis root cause is kept");
        assertTrue(html.contains("(source: healing analysis)"));
        assertFalse(html.contains("A different cause stated only by Failure Analysis."));
        assertTrue(html.contains("Automation Engineer"), "its owner is still shown");
    }

    @Test
    void failureAnalysisText_isHtmlEscapedInTheReport() throws IOException {
        String id = "SYNTHETIC_FaEscaping";
        writeCaptureWithStep(id, "Select Store");
        writeFailureAnalysisReport(id, new String[]{"Select Store", "Category &lt;x&gt;", "Ops &amp; QA", "Cause &lt;b&gt;bold&lt;/b&gt; is explicit."});
        String html = read(UnifiedHealingReport.generate(out.toString(), id));
        assertTrue(html.contains("Ops &amp; QA"));
        assertFalse(html.contains("<b>bold</b>"), "markup in the source report is never injected as HTML");
    }
}
