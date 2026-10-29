package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Final reporting layer — "Report-Driven Healing": every run ALWAYS just analyzes and reports; a real patch
 * is only ever attempted for a specific test when the user explicitly re-invokes {@code SelfHealingAgent} for
 * that one test (from the report's own "Heal this test" command, or by asking Claude Code to "heal
 * &lt;TestName&gt;") — report generation itself never triggers healing.
 *
 * <p>Shares one product identity with {@link FailureAnalysisReport} ({@link PlainLanguage#PRODUCT_NAME}) —
 * "Failure Analysis" and "Self-Healing" are the same combined workflow's two internal stages, not two
 * unrelated tools. Every report leads with a plain-language story (problem detected, what the system
 * understood, proposed fix, why, confidence/risk in words, approval, backup, compile, re-test, KEEP/ROLLBACK
 * explained in one sentence, screenshots before/after) built by {@link PlainLanguage} and
 * {@link ScreenshotExtractor} — the existing technical fields ({@link HtmlDiffRenderer}) are never removed,
 * only moved into a collapsible "Technical Details / Evidence" section ({@code &lt;details&gt;}, no
 * JavaScript) underneath.</p>
 *
 * <p>Writes under {@code <outDir>/self-healing/}:</p>
 * <ol>
 *   <li>{@code reports/<TestName>_<timestamp>/<TestName>_SelfHealingReport.html} — one INDIVIDUAL, analysis
 *       report per test processed this run, in its own uniquely-timestamped folder (never overwritten),
 *       titled with the actual test/page name, and carrying the exact copy-pasteable command to heal that
 *       test for real.</li>
 *   <li>{@code summary/SelfHealingOverallSummary_<timestamp>.html} — one OVERALL summary per run: an
 *       executive summary (counts, not a technical table) plus one compact
 *       Test / Failed Step / Problem / Action Taken / Final Result row per test.</li>
 *   <li>{@code index/index_<timestamp>.html} — a durable, cross-run index snapshot (newest first), built by
 *       re-reading an append-only manifest ({@code history/runs.tsv}) this class also maintains — the only
 *       new persisted state this layer introduces; the pre-existing {@code failures/healing-history.tsv}
 *       audit trail and {@code failures/history-<testId>.tsv} step history are completely untouched.</li>
 *   <li>{@code healing/<TestName>_<timestamp>/healing-report.html} — written ONLY for a test that actually
 *       reached a real human approval decision this run (i.e. {@code SelfHealingAgent} was invoked for that
 *       one test, NOT {@code --dry-run}, and got past every triage/protected/quality gate) — the record of an
 *       actual healing attempt's outcome (KEPT/REVERTED/NOT_APPLIED), kept separate from the analysis-only
 *       reports/ folder so "what was analyzed" and "what was actually healed" are never conflated.</li>
 * </ol>
 *
 * <p>No business logic is duplicated here: every fact rendered comes straight from {@link HealingAttemptRecord}
 * via {@link HtmlDiffRenderer} (the one place attempt fields are formatted for HTML), and grouping comes from
 * the same {@link FailureGrouper.Group} list {@code SelfHealingAgent} already built. The healing/ report reuses
 * the exact same per-test HTML body as the individual report — it is a second WRITE of the same rendering, not
 * a second rendering implementation.</p>
 */
public final class HealingDashboard {

    private HealingDashboard() {}

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String CSS =
            "*{box-sizing:border-box}body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f5f7;color:#2d2d2d;line-height:1.5}"
            + ".brand{font-size:12px;letter-spacing:.06em;text-transform:uppercase;opacity:.85;margin:0 0 4px}"
            + ".header{background:linear-gradient(135deg,#2b6cb0 0%,#4c51bf 100%);color:#fff;padding:32px 40px}"
            + ".header h1{margin:0 0 6px;font-size:26px}.header p{margin:2px 0;opacity:.9;font-size:13px}"
            + ".container{max-width:1200px;margin:26px auto;padding:0 20px 60px}"
            + ".dryRunBanner{background:#fff3d6;color:#8a5a00;border:1px solid #f0c674;border-radius:6px;padding:10px 16px;"
            + "font-weight:700;letter-spacing:.04em;margin-bottom:22px}"
            + ".noteBox{background:#eef1fb;color:#2b3a55;border:1px solid #c7cff2;border-radius:6px;padding:10px 16px;margin-bottom:14px}"
            + ".healBox{background:#eef7ee;border:1px solid #bfe3bf;border-radius:6px;padding:12px 16px;margin-bottom:22px}"
            + ".healBox .l{font-weight:700;color:#1b8a3d;margin-bottom:6px}"
            + ".healBox code{display:block;background:#1e1e2e;color:#e6e6e6;padding:8px 10px;border-radius:6px;font-size:12px;overflow-x:auto;white-space:pre}"
            + ".healingOutcome{background:#eef1fb;border:1px solid #c7cff2;border-radius:6px;padding:10px 16px;font-weight:700;color:#2b3a55;margin-bottom:22px}"
            + ".outcomeBox{border-radius:6px;padding:12px 16px;margin:14px 0;font-size:13.5px}"
            + ".outcomeBox.keep{background:#e3f7e8;border:1px solid #b6e6c4;color:#1b5e30}"
            + ".outcomeBox.rollback{background:#fde2e2;border:1px solid #f3b0b0;color:#8a1f1f}"
            + ".summary{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px;margin-bottom:28px}"
            + ".stat{background:#fff;border-radius:8px;padding:14px 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + ".stat .n{font-size:22px;font-weight:700;color:#2b6cb0}.stat .l{font-size:12px;color:#666}"
            + "h2{font-size:18px;color:#2b3a55;border-left:4px solid #4c51bf;padding-left:10px;margin:30px 0 12px}"
            + ".story{background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.08);margin-bottom:20px;overflow:hidden}"
            + ".story-head{padding:12px 16px;background:#eef1fb;font-weight:600;display:flex;justify-content:space-between;align-items:center}"
            + ".story-body{padding:16px}"
            + "table.plain{width:100%;border-collapse:collapse;margin-bottom:14px}"
            + "table.plain th{text-align:left;color:#2b3a55;width:230px;padding:8px 10px;font-size:13.5px;vertical-align:top;background:#f6f8fd}"
            + "table.plain td{padding:8px 10px;font-size:13.5px;vertical-align:top;word-break:break-word;border-bottom:1px solid #eef0f7}"
            + "details{margin-top:10px;border:1px solid #eee;border-radius:6px}"
            + "details summary{cursor:pointer;padding:10px 14px;font-weight:600;color:#2b3a55;background:#faf9fb;border-radius:6px}"
            + "details[open] summary{border-bottom:1px solid #eee;border-radius:6px 6px 0 0}"
            + "details .tech-body{padding:12px 14px}"
            + "table.meta{width:100%;border-collapse:collapse;margin-bottom:10px}"
            + "table.meta th{text-align:left;color:#4a3b78;width:190px;padding:4px 8px;font-size:13px;vertical-align:top}"
            + "table.meta td{padding:4px 8px;font-size:13px;vertical-align:top;word-break:break-word}"
            + "table.groups,table.resultsTable{width:100%;border-collapse:collapse;margin-bottom:20px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + "table.groups th,table.resultsTable th{background:#eef1fb;color:#2b3a55;text-align:left;padding:8px 10px;font-size:13px}"
            + "table.groups td,table.resultsTable td{padding:8px 10px;font-size:13px;border-top:1px solid #eee;vertical-align:top}"
            + "table.resultsTable a{color:#2b6cb0;text-decoration:none;font-weight:600}table.resultsTable a:hover{text-decoration:underline}"
            + ".diff{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px}"
            + ".diff-col pre{background:#1e1e2e;color:#e6e6e6;padding:10px;border-radius:6px;overflow-x:auto;font-size:12px;white-space:pre-wrap;word-break:break-word}"
            + ".diff-label{font-weight:700;font-size:12px;margin-bottom:4px}.diff-label.before{color:#c0392b}.diff-label.after{color:#1e8449}"
            + ".pill{display:inline-block;padding:2px 9px;border-radius:10px;font-size:11px;font-weight:700}"
            + ".pKEPT{background:#e3f7e8;color:#1b8a3d}.pREVERTED{background:#fde2e2;color:#b3261e}"
            + ".pNOT_APPLIED{background:#fff3d6;color:#9a6b00}.pNEEDS_TRIAGE{background:#eee;color:#555}.pDRY_RUN{background:#e3ecf7;color:#2b5a9a}"
            + ".pREJECTED_VERIFICATION_FAILED{background:#fde2e2;color:#b3261e}"
            + ".pRESTORE_FAILED{background:#b3261e;color:#fff}"
            + ".pPASS{background:#e3f7e8;color:#1b8a3d}.pFAIL{background:#fde2e2;color:#b3261e}.pN\\/A{background:#eee;color:#777}"
            + ".shot{margin:12px 0}.shot img{max-width:100%;border:1px solid #eee;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.1)}"
            + ".shot .cap{font-size:12.5px;color:#555;margin-top:6px;line-height:1.45}.shot .cap strong{color:#2b3a55}"
            + ".shotRow{display:flex;gap:16px;flex-wrap:wrap}.shotRow .shot{flex:1;min-width:260px}"
            + ".sectionHead{font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#2b3a55;margin:18px 0 8px;padding-bottom:4px;border-bottom:1px solid #eee}"
            + ".techIntro{margin:0 0 10px;color:#666;font-size:13px}"
            + ".failedAfterBox{border-radius:6px;padding:12px 16px;margin:14px 0;font-size:13.5px}"
            + ".failedAfterBox.yes{background:#fde2e2;border:1px solid #f3b0b0;color:#8a1f1f}"
            + ".failedAfterBox.no{background:#eef1fb;border:1px solid #c7cff2;color:#2b3a55}"
            + ".failedAfterBox table.plain{margin:10px 0 0}.failedAfterBox table.plain th{background:transparent}"
            + ".footer{text-align:center;color:#9a9aa5;font-size:12px;margin-top:34px}";

    public static class Result {
        public final Path summaryReportPath;
        public final Map<String, Path> individualReportPaths; // testId -> path
        public final Map<String, Path> healingReportPaths;     // testId -> path, only for tests actually healed this run
        public final Path indexPath;

        Result(Path summaryReportPath, Map<String, Path> individualReportPaths, Map<String, Path> healingReportPaths, Path indexPath) {
            this.summaryReportPath = summaryReportPath;
            this.individualReportPaths = individualReportPaths;
            this.healingReportPaths = healingReportPaths;
            this.indexPath = indexPath;
        }
    }

    /**
     * @param sessionLabel a short, clean run scope — the test's own simple name for a single-test run, or
     *                     "Full Project" for {@code --all} — shown as the "Scope" line on the summary report
     *                     (titled, always, "Self-Healing Overall Summary"). Individual reports are titled
     *                     "<test name> - Self Healing Report" using the actual per-test name instead.
     */
    public static Result write(String outDir, String sessionLabel, GitStatus git, List<FailureGrouper.Group> groups,
                                List<HealingAttemptRecord> attempts, boolean dryRun, int maxAttempts) {
        Path base = Paths.get(outDir, "self-healing");
        Path reportsDir = base.resolve("reports");
        Path summaryDir = base.resolve("summary");
        Path indexDir = base.resolve("index");
        Path healingDir = base.resolve("healing");
        Path historyDir = base.resolve("history");
        try {
            Files.createDirectories(reportsDir);
            Files.createDirectories(summaryDir);
            Files.createDirectories(indexDir);
            Files.createDirectories(healingDir);
            Files.createDirectories(historyDir);
            Files.createDirectories(base.resolve("backups")); // scaffolding; BackupManager also creates this lazily
        } catch (IOException e) {
            System.out.println("HealingDashboard: could not create report directories - " + e.getMessage());
            return null;
        }

        LocalDateTime runTs = LocalDateTime.now();
        String tsStr = FILE_TS.format(runTs);

        Map<String, List<HealingAttemptRecord>> byTest = new LinkedHashMap<>();
        for (HealingAttemptRecord a : attempts) byTest.computeIfAbsent(a.testId, k -> new ArrayList<>()).add(a);

        Map<String, Path> individualPaths = new LinkedHashMap<>();
        Map<String, Path> healingPaths = new LinkedHashMap<>();
        List<String[]> manifestRows = new ArrayList<>();

        for (Map.Entry<String, List<HealingAttemptRecord>> e : byTest.entrySet()) {
            String testId = e.getKey();
            List<HealingAttemptRecord> testAttempts = e.getValue();
            String testClass = testAttempts.get(0).testClass;
            String simpleName = testClass.contains(".") ? testClass.substring(testClass.lastIndexOf('.') + 1) : testClass;
            if (simpleName.isBlank()) simpleName = testId;
            String safe = safeName(simpleName);

            // Analysis report — ALWAYS written, dry-run or real, for every test processed. Report generation
            // itself never heals anything; it only ever shows what a candidate would be and how to heal for real.
            Path reportFolder = uniqueDir(reportsDir, safe + "_" + tsStr);
            Path individualPath = reportFolder.resolve(safe + "_SelfHealingReport.html");
            String analysisHtml = buildTestReportHtml(simpleName + " - Self Healing Report", testClass, testId,
                    git, testAttempts, dryRun, true);
            writeFile(individualPath, analysisHtml);
            individualPaths.put(testId, individualPath);

            // Healing report — ONLY when a real human approval decision was actually reached for this test
            // this run (i.e. SelfHealingAgent was invoked for it, not --dry-run, and it passed every gate).
            boolean realHealingAttempted = testAttempts.stream()
                    .anyMatch(a -> "APPROVED".equals(a.humanDecision) || "DECLINED".equals(a.humanDecision));
            Path healingPath = null;
            if (realHealingAttempted) {
                Path healingFolder = uniqueDir(healingDir, safe + "_" + tsStr);
                healingPath = healingFolder.resolve("healing-report.html");
                String healingHtml = buildTestReportHtml(simpleName + " - Healing Outcome", testClass, testId,
                        git, testAttempts, dryRun, false);
                writeFile(healingPath, healingHtml);
                healingPaths.put(testId, healingPath);
            }

            String overallStatus = overallStatus(testAttempts);
            double bestConfidence = testAttempts.stream().mapToDouble(a -> a.confidence).filter(c -> c >= 0).max().orElse(-1);
            manifestRows.add(new String[]{
                    runTs.toString(), simpleName, testId, overallStatus,
                    bestConfidence >= 0 ? String.valueOf(Math.round(bestConfidence * 100)) : "",
                    String.valueOf(testAttempts.size()),
                    base.relativize(individualPath).toString().replace('\\', '/'),
                    "", // summaryPath — filled in below once known
                    healingPath == null ? "" : base.relativize(healingPath).toString().replace('\\', '/')
            });
        }

        Path summaryPath = uniquePath(summaryDir, "SelfHealingOverallSummary_" + tsStr, ".html");
        writeSummaryReport(summaryPath, sessionLabel, git, groups, attempts, individualPaths, healingPaths, dryRun, maxAttempts, runTs);

        String summaryRel = "summary/" + summaryPath.getFileName();
        Path manifestFile = historyDir.resolve("runs.tsv");
        appendManifest(manifestFile, manifestRows, summaryRel);

        Path indexPath = uniquePath(indexDir, "index_" + tsStr, ".html");
        writeIndex(indexPath, manifestFile);

        return new Result(summaryPath, individualPaths, healingPaths, indexPath);
    }

    // =======================================================================
    // Per-test report body — shared by the analysis (reports/) and healing (healing/) destinations
    // =======================================================================

    /** @param includeHealCommand true for the analysis report (shows the copy-pasteable "heal this test"
     *                            command); false for the healing/ outcome report (a real attempt already
     *                            happened, so showing "how to heal" again would be misleading). */
    private static String buildTestReportHtml(String title, String testClass, String testId, GitStatus git,
                                               List<HealingAttemptRecord> testAttempts, boolean dryRun,
                                               boolean includeHealCommand) {
        StringBuilder sb = new StringBuilder();
        String simpleTestName = testClass.contains(".") ? testClass.substring(testClass.lastIndexOf('.') + 1) : testClass;
        // One representative screen name for the header — from the first attempt's own evidence, same
        // resolver used per-story below. Never the test/fixture class name as "the screen".
        HealingAttemptRecord firstAttempt = testAttempts.get(0);
        String headerScreen = PlainLanguage.resolveScreenName(firstAttempt.affectedFile, firstAttempt.rootCause, firstAttempt.evidence);
        String headerTitle = title.replaceFirst("^.*? - ", (headerScreen.isBlank() ? simpleTestName : headerScreen) + " - ");
        sb.append(htmlHead(PlainLanguage.pipeTitle(headerScreen.isBlank() ? simpleTestName : headerScreen, "Self-Healing")));
        String overallForHeader = overallStatus(testAttempts);
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Stage 2 &mdash; Self-Healing</p>")
          .append("<h1>").append(esc(headerTitle)).append("</h1>")
          .append("<p><strong>Test Class:</strong> ").append(esc(testClass)).append("</p>")
          .append("<p><strong>Test ID:</strong> ").append(esc(testId)).append("</p>")
          .append("<p><strong>Test Result:</strong> ").append(esc(testResultHeaderPlain(overallForHeader))).append("</p>")
          .append("<p><strong>Run Timestamp:</strong> ").append(DISPLAY_TS.format(LocalDateTime.now())).append("</p>")
          .append("<p><strong>Git:</strong> ").append(esc(gitSummary(git))).append("</p>")
          .append("</div><div class='container'>");
        boolean anyResolved = testAttempts.stream().anyMatch(a -> "KEPT".equals(a.finalStatus) || "REVERTED".equals(a.finalStatus)
                || "RESTORE_FAILED".equals(a.finalStatus));
        sb.append(FailureAnalysisReport.lifecycleTracker(anyResolved ? "KEEP or ROLLBACK" : includeHealCommand ? "Failure Analysis" : "Healing Proposal"));
        if (dryRun) sb.append("<div class='dryRunBanner'>DRY RUN — analysis only. No file was modified, no approval was requested.</div>");

        if (includeHealCommand) {
            sb.append("<div class='healBox'><div class='l'>Heal this test</div>")
              .append("<p>This report is analysis only — nothing above was applied. To start a real healing run for "
                      + "this specific test (with the normal approval/backup/verify flow), run:</p>")
              .append("<code>mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args=\"")
              .append(esc(testClass)).append("\"</code>")
              .append("<p>or, in Claude Code, ask: \"heal ").append(esc(testClass.substring(testClass.lastIndexOf('.') + 1)))
              .append("\". You will be shown the full proposal and asked to approve before anything is written.</p></div>");
        } else {
            String overall = overallStatus(testAttempts);
            sb.append("<div class='healingOutcome'>A real healing attempt was made for this test this run — outcome: ")
              .append(esc(overall)).append("</div>");
        }

        for (HealingAttemptRecord a : testAttempts) {
            sb.append("<div class='story'><div class='story-head'><span>Step: ").append(esc(PlainLanguage.cleanStepName(a.step))).append("</span>")
              .append("<span class='pill p").append(esc(cssSafe(a.finalStatus))).append("'>").append(esc(a.finalStatus)).append("</span></div>");
            sb.append("<div class='story-body'>");

            boolean failedAfterHealing = failedAfterHealing(a);
            // Every Self-Healing report is, by construction, for a failure PatchProposer already found a real
            // LOCATOR/TIMING/WORKFLOW/LLM candidate for — HealingAttemptRecord doesn't carry the 12-way
            // FailureCategory FailureAnalysisReport uses, so LOCATOR_SELECTOR_FAILURE (the shape this whole
            // pipeline targets) is the accurate category for every record reaching here — same structural
            // reason "Automation Tester" is the accurate owner (the exact owner FailureCategoryClassifier
            // itself already assigns that category during Failure Analysis), not a per-test guess.
            // ONE shared derivation for every business-facing fact this story renders — see PlainLanguage's
            // own javadoc for why this replaced scattered individual calls (a real cross-file consistency bug).
            // Custom fallbacks preserve the proposalKind-aware nuance (LOCATOR/TIMING/WORKFLOW/LLM) that the
            // plain LOCATOR_SELECTOR_FAILURE category alone can't express — see fallbackRootCausePlain/
            // fallbackEvidencePlain below, unchanged from before this consolidation.
            // NOT every attempt reaching HealingDashboard actually reached a real candidate — a NEEDS_TRIAGE
            // attempt (no candidate, or every candidate rejected by semantic validation) is processed and
            // reported here too. hasRealProposal(a) is this record's own already-computed outcome, the same
            // signal proposedFixPlain(a) already uses — never a new eligibility decision, only a display of
            // the existing one, so this can never disagree with "Proposed Fix" a few rows below it.
            PlainLanguage.FailureExplanation fx = PlainLanguage.explain(testClass, a.step, a.failureExpected,
                    a.failureActual, FailureCategory.LOCATOR_SELECTOR_FAILURE, a.rootCause, a.evidence,
                    a.affectedFile, "", "Automation Tester", hasRealProposal(a), fallbackRootCausePlain(a), fallbackEvidencePlain(a));

            sb.append("<div class='noteBox'>This failure was found eligible for automatic correction during Failure "
                    + "Analysis, and was passed to the separate Self-Healing process below. See the Failure Analysis "
                    + "report for how the failure itself was first detected and categorized.</div>");

            sb.append("<div class='sectionHead'>A. What Was Tested</div><table class='plain'>");
            plainRow(sb, "Failure Summary", fx.failureSummary);
            plainRow(sb, "Test", PlainLanguage.friendlyName(testClass));
            plainRow(sb, "Application Screen", fx.applicationScreen.isBlank() ? PlainLanguage.SCREEN_NAME_UNKNOWN : fx.applicationScreen);
            plainRow(sb, "What the test was trying to do", fx.whatWasTried);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>B. Which Step Failed</div><table class='plain'>");
            plainRow(sb, "Failed Step", fx.failedStep);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>C. What Was Expected</div><table class='plain'>");
            plainRow(sb, "Expected", fx.expected);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>D. What Actually Happened</div><table class='plain'>");
            plainRow(sb, "Actual", fx.actual);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>E. Why Did It Fail?</div><table class='plain'>");
            plainRow(sb, "Root Cause", fx.whyItFailed);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>F. Evidence</div><table class='plain'>");
            plainRow(sb, "Failed Step", fx.failedStep);
            plainRow(sb, "What the evidence tells us", fx.evidencePlain);
            sb.append("</table>");
            if (!a.failureScreenshotBase64.isBlank()) {
                sb.append("<div class='shot'><img alt='Original failure screenshot' src='data:image/png;base64,")
                  .append(a.failureScreenshotBase64).append("'><div class='cap'><strong>FAILURE SCREENSHOT &mdash; ORIGINAL FAILURE</strong><br>")
                  .append("<strong>Failed Step:</strong> \"").append(esc(fx.failedStep)).append("\"<br>")
                  .append("<strong>What this screenshot shows:</strong> ").append(esc(fx.screenshotWhatItShows)).append(" (before any change was made.)<br>")
                  .append("<strong>What this screenshot tells us:</strong> ").append(esc(fx.screenshotWhatItTellsUs)).append("<br>")
                  .append("<strong>Why this screenshot matters:</strong> ").append(esc(fx.screenshotWhyItMatters)).append("<br>")
                  .append("<strong>What this screenshot does NOT prove:</strong> ").append(esc(fx.screenshotWhatItDoesNotProve))
                  .append("</div></div>");
            } else {
                sb.append("<p class='techIntro'><strong>Failure Screenshot:</strong> Not available.<br>"
                        + "The failure was recorded, but a screenshot could not be captured at the failure point.</p>");
            }

            sb.append("<div class='sectionHead'>G. Could It Be Fixed?</div><table class='plain'>");
            plainRow(sb, "Who should investigate / fix this?", fx.responsibleOwner);
            plainRow(sb, "Why this team?", fx.whyOwner);
            plainRow(sb, "Recommended fix", fx.recommendedFix);
            plainRow(sb, "Why this fix is recommended", fx.whyFix);
            plainRow(sb, "Automatic Fixing Possible", fx.automaticFixPossible);
            plainRow(sb, "Why it was considered safe to attempt", "PASSED".equals(a.semanticValidation)
                    ? "The proposed change passed the system's safety checks — it targets only how the test recognizes "
                      + "one screen element, does not touch business data or logic, and nothing in the change contradicts "
                      + "the field's expected purpose."
                    : "The system proceeded on the available screen evidence; see Semantic Safety Check below for detail.");
            plainRow(sb, "Proposed Fix", proposedFixPlain(a));
            plainRow(sb, "Why This Fix Was Selected", whySelectedPlain(a));
            plainRow(sb, "Confidence", a.confidence >= 0
                    ? "The system is " + PlainLanguage.confidenceInWords(a.confidence) + " in this fix (" + Math.round(a.confidence * 100) + "%)."
                    : "Not applicable — no confidence score was produced.");
            plainRow(sb, "Risk", a.risk.isBlank() ? "Not applicable." : PlainLanguage.riskInWords(a.risk));
            plainRow(sb, "Semantic Safety Check", "PASSED".equals(a.semanticValidation)
                    ? "Passed — the system checked that this change makes sense for the type of field involved, and found nothing that contradicts it."
                    : "REJECTED".equals(a.semanticValidation)
                    ? "Not passed — the system found a reason this change might not be safe, so it was not offered."
                    : "Not applicable.");
            plainRow(sb, "Human Approval Required", "Yes — a person must review and approve the exact change before anything is written.");
            sb.append("</table>");

            sb.append("<div class='sectionHead'>H. After The Fix</div><table class='plain'>");
            plainRow(sb, "1. Was a fix proposed?", "Yes");
            plainRow(sb, "2. Was human approval given?", approvalPlain(a));
            plainRow(sb, "3. Was the fix applied?", applyResultPlain(a));
            plainRow(sb, "4. Did compilation succeed?", compilePlain(a));
            plainRow(sb, "5. Was the test re-run?", a.patchApplied ? "Yes" : "Not applicable — no change was applied, so there was nothing to re-test.");
            plainRow(sb, "6. Did the re-run pass or fail?", retestPlain(a));
            plainRow(sb, "Verification Result", verificationPlain(a));
            sb.append("</table>");

            sb.append("<div class='failedAfterBox ").append(failedAfterHealing ? "yes" : "no").append("'>");
            sb.append("<strong>Failure After Healing:</strong> ").append(!a.patchApplied
                    ? "Not applicable — no change was applied, so there was nothing to re-test."
                    : (failedAfterHealing ? "YES" : "NO"));
            if (failedAfterHealing) {
                String retestStep = notBlankHD(a.retestScreenshotStep.isBlank() ? a.step : a.retestScreenshotStep);
                sb.append("<table class='plain'>");
                plainRow(sb, "8. Failed Step", retestStep);
                plainRow(sb, "8. What Was Expected", "The step should have completed using the corrected reference the fix put in place.");
                plainRow(sb, "8. What Actually Happened", "The step was tested again for real and still did not complete successfully.");
                plainRow(sb, "8. Why It Failed", retestFailureReasonPlain(a));
                plainRow(sb, "7. Same problem or a different problem?", relatedToFixPlain(a));
                sb.append("</table>");
                if (!a.retestScreenshotBase64.isBlank()) {
                    sb.append("<div class='shot'><img alt='Rollback / restored state screenshot' src='data:image/png;base64,")
                      .append(a.retestScreenshotBase64).append("'><div class='cap'><strong>FAILURE SCREENSHOT AFTER HEALING &mdash; ROLLBACK / RESTORED STATE</strong><br>")
                      .append("<strong>Failed Step:</strong> \"").append(esc(a.retestScreenshotStep)).append("\"<br>")
                      .append("<strong>What this screenshot shows:</strong> ").append(esc(fx.screenshotWhatItShows))
                      .append(" (tested again with the fix in place, at the point where it still did not complete.)<br>")
                      .append("<strong>What this screenshot tells us:</strong> ").append(esc(fx.screenshotWhatItTellsUs)).append("<br>")
                      .append("<strong>Why this screenshot matters:</strong> ").append(esc(fx.screenshotWhyItMatters)).append("<br>")
                      .append("<strong>What this screenshot does NOT prove:</strong> The image alone does not show whether this is the same or a different problem — that conclusion is stated above, from the actual re-test evidence.")
                      .append("</div></div>");
                } else {
                    sb.append("<p class='techIntro'><strong>Failure Screenshot After Healing:</strong> Not available.<br>"
                            + "The re-test failure was recorded, but a screenshot could not be captured at that point.</p>");
                }
            } else if (a.patchApplied && !a.retestScreenshotBase64.isBlank()) {
                sb.append("<div class='shot'><img alt='After proposed fix screenshot' src='data:image/png;base64,")
                  .append(a.retestScreenshotBase64).append("'><div class='cap'><strong>AFTER PROPOSED FIX "
                  + "&mdash; Step: \"").append(esc(PlainLanguage.cleanStepName(a.step))).append("\"</strong><br>")
                  .append("<strong>What this screenshot shows:</strong> ").append(esc(fx.screenshotWhatItShows))
                  .append(" (tested again after the change was applied — this step now completed successfully.)<br>")
                  .append("<strong>What this screenshot tells us:</strong> ").append(esc(fx.screenshotWhatItTellsUs)).append("<br>")
                  .append("<strong>Why this screenshot matters:</strong> ").append(esc(fx.screenshotWhyItMatters))
                  .append("</div></div>");
            }
            sb.append("</div>");

            if ("REVERTED".equals(a.finalStatus) || "RESTORE_FAILED".equals(a.finalStatus)) {
                sb.append("<table class='plain'>");
                plainRow(sb, "9. Restoration Verified", Boolean.TRUE.equals(a.restorationVerified) ? "Yes"
                        : Boolean.FALSE.equals(a.restorationVerified) ? "No — could not be confirmed; needs a manual check"
                        : "Not available");
                sb.append("</table>");
            }

            if ("KEPT".equals(a.finalStatus)) {
                sb.append("<div class='outcomeBox keep'><strong>10. KEEP.</strong> ").append(PlainLanguage.KEEP_EXPLANATION).append("</div>");
            } else if ("REVERTED".equals(a.finalStatus)) {
                String specific = retestFailureReasonPlain(a);
                sb.append("<div class='outcomeBox rollback'><strong>9. ROLLBACK.</strong> ").append(PlainLanguage.ROLLBACK_EXPLANATION);
                if (!specific.isBlank()) sb.append(" ").append(specific);
                sb.append(" ").append(restorationPlain(a));
                sb.append("</div>");
            } else if ("RESTORE_FAILED".equals(a.finalStatus)) {
                String specific = retestFailureReasonPlain(a);
                sb.append("<div class='outcomeBox rollback'><strong>9. ROLLBACK FAILED.</strong> ").append(PlainLanguage.RESTORE_FAILED_EXPLANATION);
                if (!specific.isBlank()) sb.append(" ").append(specific);
                sb.append(" ").append(restorationPlain(a));
                if (!a.backupPath.isBlank()) sb.append(" Backup to restore from: ").append(esc(a.backupPath)).append(".");
                sb.append("</div>");
            }

            sb.append("<div class='sectionHead'>Technical Details / Developer Evidence</div>");
            sb.append("<details><summary>Technical Details / Developer Evidence</summary><div class='tech-body'>");
            sb.append("<p class='techIntro'>The fields below are the same healing attempt, in its original technical "
                    + "form &mdash; locator/reference values, confidence scoring, and validation output, for a "
                    + "developer investigating this directly in code.</p>");
            sb.append("<table class='meta'><tr><th>Root Cause — Technical Explanation</th><td>")
              .append(esc(fx.whyItFailed)).append("</td></tr></table>");
            sb.append(HtmlDiffRenderer.renderMeta(a));
            if (!a.oldSnippet.isBlank() || !a.newSnippet.isBlank()) sb.append(HtmlDiffRenderer.renderDiff(a.oldSnippet, a.newSnippet));
            sb.append("</div></details>");

            String finalLabel = finalResultLabel(a);
            sb.append("<div class='sectionHead'>I. Final Result</div>");
            sb.append("<div class='outcomeBox ").append(PlainLanguage.FINAL_FIX_RETAINED.equals(finalLabel) ? "keep" : "rollback")
              .append("'><strong>").append(finalLabel).append(".</strong> ").append(PlainLanguage.finalResultExplanation(finalLabel)).append("</div>");

            sb.append("</div></div>");
        }

        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Self-Healing stage &middot; no external service dependency</div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // =======================================================================
    // Overall session summary report
    // =======================================================================

    private static void writeSummaryReport(Path path, String sessionLabel, GitStatus git, List<FailureGrouper.Group> groups,
                                            List<HealingAttemptRecord> attempts, Map<String, Path> individualPaths,
                                            Map<String, Path> healingPaths, boolean dryRun, int maxAttempts, LocalDateTime runTs) {
        long testsWithFailures = attempts.stream().map(a -> a.testId).distinct().count();
        long analyzed = attempts.size();
        long approved = attempts.stream().filter(a -> "APPROVED".equals(a.humanDecision)).count();
        long rejected = attempts.stream().filter(a -> "DECLINED".equals(a.humanDecision)).count();
        long healed = attempts.stream().filter(a -> "KEPT".equals(a.finalStatus)).count();
        long restoreFailed = attempts.stream().filter(a -> "RESTORE_FAILED".equals(a.finalStatus)).count();
        // a rollback only counts as "rolled back" when its restoration was verified - never a RESTORE_FAILED attempt
        long reverted = attempts.stream().filter(a -> a.reverted && !"RESTORE_FAILED".equals(a.finalStatus)).count();
        long needsTriage = attempts.stream().filter(a -> "NEEDS_TRIAGE".equals(a.finalStatus)).count();
        long candidatesGenerated = attempts.stream().mapToLong(a -> a.candidateTotal).sum();

        StringBuilder sb = new StringBuilder();
        String title = "Self-Healing - Executive Summary";
        sb.append(htmlHead(PlainLanguage.pipeTitle("Self-Healing", "Executive Summary")));
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Stage 2 &mdash; Self-Healing</p>")
          .append("<h1>").append(esc(title)).append("</h1>")
          .append("<p><strong>Scope:</strong> ").append(esc(sessionLabel)).append("</p>")
          .append("<p><strong>Run Timestamp:</strong> ").append(DISPLAY_TS.format(runTs)).append("</p>")
          .append("<p><strong>Git:</strong> ").append(esc(gitSummary(git))).append("</p>")
          .append("</div><div class='container'>");
        if (dryRun) sb.append("<div class='dryRunBanner'>DRY RUN — analysis only. No file was modified, no approval was requested.</div>");
        else sb.append("<div class='healBox'><div class='l'>Report-Driven Healing</div>"
                + "<p>This summary is analysis only. Open an individual report below and use its \"Heal this test\" "
                + "command (or ask Claude Code to \"heal &lt;TestName&gt;\") to start a real healing run for one "
                + "specific test — nothing here was applied automatically.</p></div>");

        sb.append("<div class='summary'>");
        stat(sb, "Tests executed", testsWithFailures);
        stat(sb, "Tests failed", testsWithFailures);
        stat(sb, "Problems identified", analyzed);
        stat(sb, "Automatically healable problems", candidatesGenerated);
        stat(sb, "Fixes proposed", candidatesGenerated);
        stat(sb, "Human approvals", approved);
        stat(sb, "Fixes retained (KEEP)", healed);
        stat(sb, "Fixes rolled back", reverted);
        stat(sb, "Rollbacks NOT verified (manual restore needed)", restoreFailed);
        stat(sb, "Problems requiring manual investigation", needsTriage);
        sb.append("</div>");

        sb.append("<h2>What Happened, Test by Test</h2>");
        if (attempts.isEmpty()) {
            sb.append("<p>No healing attempts were made in this run.</p>");
        } else {
            sb.append("<div style='overflow-x:auto'><table class='resultsTable'>")
              .append("<tr><th>Test</th><th>Failed Step</th><th>Problem</th><th>Action Taken</th><th>Final Result</th></tr>");
            for (HealingAttemptRecord a : attempts) {
                String testLabel = PlainLanguage.friendlyName(a.testClass);
                Path link = individualPaths.get(a.testId);
                // Same shared model every individual report uses — never a separate derivation for the
                // summary row, so the "Problem" column can never disagree with the linked report's own text.
                // hasRealProposal(a) mirrors the individual report's own fx above — same existing per-attempt
                // outcome, not re-decided here.
                PlainLanguage.FailureExplanation rowFx = PlainLanguage.explain(a.testClass, a.step, a.failureExpected,
                        a.failureActual, FailureCategory.LOCATOR_SELECTOR_FAILURE, a.rootCause, a.evidence,
                        a.affectedFile, "", "Automation Tester", hasRealProposal(a), fallbackRootCausePlain(a), fallbackEvidencePlain(a));
                sb.append("<tr><td>");
                if (link != null) sb.append("<a href='../reports/").append(esc(relativeUnder(link, "reports"))).append("'>").append(esc(testLabel)).append("</a>");
                else sb.append(esc(testLabel));
                sb.append("</td><td>").append(esc(rowFx.failedStep)).append("</td>")
                  .append("<td>").append(esc(rowFx.whyItFailed)).append("</td>")
                  .append("<td>").append(esc(actionTakenPlain(a))).append("</td>")
                  .append("<td><span class='pill p").append(esc(cssSafe(a.finalStatus))).append("'>").append(esc(a.finalStatus)).append("</span></td></tr>");
            }
            sb.append("</table></div>");
        }

        sb.append("<details><summary>Technical Details / Evidence — grouped root causes &amp; confidence</summary><div class='tech-body'>");
        if (groups != null && !groups.isEmpty()) {
            sb.append("<h2>Grouped Root Causes</h2><table class='groups'>")
              .append("<tr><th>Affected File / Method</th><th>Failures in group</th><th>Steps</th></tr>");
            for (FailureGrouper.Group g : groups) {
                String fileMethod = g.representativeLocation != null
                        ? g.representativeLocation.pageObjectFile.getFileName() + " :: " + g.representativeLocation.methodName + "()"
                        : "(unresolved)";
                StringBuilder steps = new StringBuilder();
                for (FailureRecord f : g.failures) {
                    if (steps.length() > 0) steps.append("; ");
                    steps.append(f.testId).append(" / ").append(f.step);
                }
                sb.append("<tr><td>").append(esc(fileMethod)).append("</td><td>").append(g.failures.size())
                  .append("</td><td>").append(esc(steps.toString())).append("</td></tr>");
            }
            sb.append("</table>");
        }
        double avgConfidence = attempts.stream().mapToDouble(a -> a.confidence).filter(c -> c >= 0).average().orElse(0);
        double maxConfidence = attempts.stream().mapToDouble(a -> a.confidence).filter(c -> c >= 0).max().orElse(0);
        sb.append("<p>Average confidence: ").append(Math.round(avgConfidence * 100)).append("% &middot; Highest confidence: ")
          .append(Math.round(maxConfidence * 100)).append("% &middot; Attempt budget: ").append(maxAttempts)
          .append(dryRun ? " (not enforced - dry-run)" : "").append("</p>");
        sb.append("</div></details>");

        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Self-Healing stage &middot; no external service dependency</div>");
        sb.append("</div></body></html>");
        writeFile(path, sb.toString());
    }

    // =======================================================================
    // Cross-run index
    // =======================================================================

    private static void appendManifest(Path manifestFile, List<String[]> rows, String summaryRel) {
        if (rows.isEmpty()) return;
        try {
            boolean writeHeader = !Files.isRegularFile(manifestFile);
            StringBuilder sb = new StringBuilder();
            if (writeHeader) {
                sb.append(String.join("\t", "timestamp", "testName", "testId", "result", "confidencePct",
                        "attempts", "reportPath", "summaryPath", "healingReportPath")).append('\n');
            }
            for (String[] row : rows) {
                row[7] = summaryRel;
                for (int i = 0; i < row.length; i++) {
                    sb.append(row[i] == null ? "" : row[i].replace('\t', ' ').replace('\n', ' '));
                    sb.append(i < row.length - 1 ? '\t' : '\n');
                }
            }
            Files.writeString(manifestFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("HealingDashboard: could not append report manifest - " + e.getMessage());
        }
    }

    private static void writeIndex(Path indexPath, Path manifestFile) {
        List<String[]> rows = new ArrayList<>();
        if (Files.isRegularFile(manifestFile)) {
            try {
                List<String> lines = Files.readAllLines(manifestFile);
                for (int i = 1; i < lines.size(); i++) { // row 0 is the header
                    String[] c = lines.get(i).split("\t", -1);
                    if (c.length >= 9) rows.add(c);
                }
            } catch (IOException ignore) { /* index is a best-effort derived view */ }
        }
        Collections.reverse(rows); // manifest is append-only (oldest first) -> newest first for display

        StringBuilder sb = new StringBuilder();
        sb.append(htmlHead(PlainLanguage.pipeTitle("Self-Healing", "Report Index")));
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append("</p>")
          .append("<h1>Self-Healing Report Index</h1><p>Recent healing runs, newest first.</p></div>")
          .append("<div class='container'>");
        sb.append("<div style='overflow-x:auto'><table class='resultsTable'>")
          .append("<tr><th>Timestamp</th><th>Test</th><th>Result</th><th>Confidence</th><th>Attempts</th><th>Report</th><th>Summary</th><th>Healing</th></tr>");
        for (String[] c : rows) {
            String ts = c[0], testName = c[1], result = c[3], confPct = c[4], attemptsCount = c[5],
                    reportPath = c[6], summaryPath = c[7], healingPath = c.length > 8 ? c[8] : "";
            sb.append("<tr><td>").append(esc(ts)).append("</td><td>").append(esc(testName)).append("</td>")
              .append("<td><span class='pill p").append(esc(cssSafe(result))).append("'>").append(esc(result)).append("</span></td>")
              .append("<td>").append(confPct.isBlank() ? "N/A" : esc(confPct) + "%").append("</td>")
              .append("<td>").append(esc(attemptsCount)).append("</td>")
              .append("<td><a href='../").append(esc(reportPath)).append("'>open</a></td>")
              .append("<td><a href='../").append(esc(summaryPath)).append("'>open</a></td>")
              .append("<td>").append(healingPath.isBlank() ? "-" : "<a href='../" + esc(healingPath) + "'>open</a>").append("</td></tr>");
        }
        sb.append("</table></div>");
        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; no external service dependency</div>");
        sb.append("</div></body></html>");
        writeFile(indexPath, sb.toString());
    }

    // =======================================================================
    // Plain-language helpers specific to a healing attempt (need HealingAttemptRecord fields FailureAnalysisReport
    // doesn't have) — kept here rather than in PlainLanguage so that class stays free of this class's own type.
    // =======================================================================

    /** Plain-language fallback for "what the system understood the problem to be" when no field-level pair
     *  could be extracted from the technical text (see {@link PlainLanguage#explainDynamic}) — deliberately
     *  never reuses the raw technical rootCause text (e.g. "Stale locator literal..."), which itself contains
     *  the exact technical terms this report must avoid. */
    private static String fallbackRootCausePlain(HealingAttemptRecord a) {
        switch (a.proposalKind == null ? "" : a.proposalKind) {
            case "LOCATOR":
                return "The test's internal reference to a screen element no longer matches what's currently on the page.";
            case "TIMING":
                return "The screen needed a little more time to finish loading this field than the test allowed for.";
            case "WORKFLOW":
                return "A new confirmation step now appears on screen that the test wasn't expecting.";
            case "LLM":
                return "An AI-assisted comparison of what the test expected against the live screen at the moment of "
                        + "failure suggested this was the same kind of outdated screen reference — reviewed under "
                        + "the same safety checks as every other suggestion.";
            default:
                return classificationRootCausePlain(a.classification);
        }
    }

    private static String fallbackEvidencePlain(HealingAttemptRecord a) {
        switch (a.proposalKind == null ? "" : a.proposalKind) {
            case "LOCATOR":
                return "The system compared what the test expected against everything actually present on the screen at the moment of failure.";
            case "TIMING":
                return "The system noticed the field was empty right when the test checked it, but held a real value moments later — a timing gap, not a missing field.";
            case "WORKFLOW":
                return "The system noticed a new on-screen element appeared right after the point of failure that wasn't there before.";
            case "LLM":
                return "The suggestion was generated by comparing the test's own expectation against the live screen "
                        + "content captured at the moment of failure, the same way the system's built-in checks do.";
            default:
                return classificationEvidencePlain(a.classification);
        }
    }

    // ---- new plain-language helpers: apply/verification/failed-after-healing/restoration ------------------

    /** Whether a change was actually written to the source file — distinct from "approved" (approval can
     *  still fail to apply, e.g. if the target text could no longer be matched exactly once). */
    private static String applyResultPlain(HealingAttemptRecord a) {
        if (a.patchApplied) return "Yes — the approved change was written to the source file.";
        if (a.candidateTotal == 0) return "Not applicable — no proposal was generated for this problem.";
        if ("APPROVED".equals(a.humanDecision)) return "No — the system could not safely apply the change (the exact text to replace could not be matched).";
        if ("DECLINED".equals(a.humanDecision)) return "No — the change was declined, so nothing was written.";
        return "No — the change was not applied.";
    }

    /** One combined sentence for "did the whole post-apply verification (compile + re-test + downstream
     *  check) pass" — distinct from the individual compile/re-test/downstream fields below it. */
    private static String verificationPlain(HealingAttemptRecord a) {
        if (!a.patchApplied) return "Not applicable — no change was made, so there was nothing to verify.";
        if ("FAIL".equals(a.compileResult)) return "Failed — the project did not build after the change, so it could not be tested further.";
        if ("N/A".equals(a.targetStepResult)) return "Not completed yet.";
        if ("UNVERIFIED".equals(a.targetStepResult)) return "Not confirmed — the test was run again but did not record a clear PASS for the original step, so the fix could not be verified.";
        boolean downstreamClean = a.downstreamRegression == null || a.downstreamRegression.isBlank()
                || "NONE".equals(a.downstreamRegression) || "N/A".equals(a.downstreamRegression);
        if ("PASS".equals(a.targetStepResult) && downstreamClean) return "Passed — the original problem was fixed and nothing else broke.";
        if (!"PASS".equals(a.targetStepResult)) return "Failed — the original problem was still there when the system tested it again.";
        return "Failed — the original problem was fixed, but the change broke something else that was working before.";
    }

    /** Whether this attempt actually applied a change AND that change failed verification afterwards — the
     *  single condition every "failed after healing" field below is gated on. */
    private static boolean failedAfterHealing(HealingAttemptRecord a) {
        if (!a.patchApplied) return false;
        boolean downstreamClean = a.downstreamRegression == null || a.downstreamRegression.isBlank()
                || "NONE".equals(a.downstreamRegression) || "N/A".equals(a.downstreamRegression);
        return "FAIL".equals(a.targetStepResult) || "UNVERIFIED".equals(a.targetStepResult) || "FAIL".equals(a.compileResult) || !downstreamClean;
    }

    /** Plain-English "why did the re-test fail", generated dynamically from this attempt's own recorded
     *  compile/re-test/downstream results — never a fixed example, and never repeats the raw technical
     *  {@code a.reason} text (which stays in Technical Details). Echoes back only this attempt's own step/
     *  regression names, never a hardcoded field or test name. */
    private static String retestFailureReasonPlain(HealingAttemptRecord a) {
        if ("FAIL".equals(a.compileResult)) {
            return "The change did not even compile successfully, so it could never be tested — the system undid the change automatically.";
        }
        if ("FAIL".equals(a.targetStepResult)) {
            return "The exact step the fix was meant to correct (\"" + a.step + "\") was tested again for real, and it still did not pass.";
        }
        if ("UNVERIFIED".equals(a.targetStepResult)) {
            return "The test was run again, but there was no clear PASS result for the step the fix was meant to correct (\"" + a.step
                    + "\"), so the fix could not be confirmed and was undone.";
        }
        boolean downstreamClean = a.downstreamRegression == null || a.downstreamRegression.isBlank()
                || "NONE".equals(a.downstreamRegression) || "N/A".equals(a.downstreamRegression);
        if (!downstreamClean) {
            return "The original problem WAS fixed, but the change caused a different, previously-working step to fail: " + a.downstreamRegression + ".";
        }
        return "";
    }

    /** Whether the post-healing failure is the SAME problem the fix targeted, or a separate side effect —
     *  derived purely from this attempt's own target-step/downstream results. */
    private static String relatedToFixPlain(HealingAttemptRecord a) {
        if ("FAIL".equals(a.compileResult)) return "Not applicable — the change never even compiled.";
        if ("FAIL".equals(a.targetStepResult)) {
            return "Yes — this is the same problem the fix was trying to solve; the fix did not resolve it.";
        }
        if ("UNVERIFIED".equals(a.targetStepResult)) {
            return "Not confirmed — the original step produced no clear PASS result after the change.";
        }
        return "No — the original problem WAS fixed; this is a different, separate issue the change caused elsewhere.";
    }

    /** Whether — and whether verified — the original source was restored after a ROLLBACK, from {@link
     *  HealingAttemptRecord#restorationVerified} (only ever set on a real revert; see {@link
     *  PatchApplier#revert()}). */
    private static String restorationPlain(HealingAttemptRecord a) {
        if (Boolean.TRUE.equals(a.restorationVerified)) {
            return "Yes — the original source code was restored from the backup, and the restored file was verified to exactly match the backup.";
        }
        if (Boolean.FALSE.equals(a.restorationVerified)) {
            return "The system attempted to restore the original source code, but could NOT verify the restoration matched the backup exactly — "
                    + "the source file may still contain the patch, so this needs a manual restore from the backup.";
        }
        return "Whether the original source code was restored was not recorded for this attempt.";
    }

    /** {@code a.classification} holds one of {@link FailureClassifier.Category}'s five internal gating
     *  values (a triage-only record never reaches a proposal, so {@code proposalKind} is blank there). */
    private static String classificationRootCausePlain(String classification) {
        switch (classification == null ? "" : classification) {
            case "ENVIRONMENT_FILE_PATH":
                return "The failure pointed at something specific to this machine's own setup, not the application.";
            case "INFRASTRUCTURE_OR_AUTH_FAILURE":
                return "This looks like a login, permissions, or connectivity problem, not an application feature issue.";
            case "VALIDATION_STATE_CHANGE":
                return "A field's on-screen state (enabled, visible, or required) did not match what the test "
                        + "expected — this could be a real issue or a stale test assumption.";
            case "INSUFFICIENT_EVIDENCE":
                return "Not enough information was captured at the moment of failure to confidently explain what happened.";
            default:
                return "The system could not confidently determine the exact cause on its own — a person should look into it.";
        }
    }

    private static String classificationEvidencePlain(String classification) {
        switch (classification == null ? "" : classification) {
            case "ENVIRONMENT_FILE_PATH":
                return "The failure message referenced a path or setting specific to this machine.";
            case "INFRASTRUCTURE_OR_AUTH_FAILURE":
                return "The failure message matched the pattern of a login, permissions, or network/connectivity problem.";
            case "VALIDATION_STATE_CHANGE":
                return "The system compared the field's actual state against the expected one and found a mismatch.";
            case "INSUFFICIENT_EVIDENCE":
                return "No usable screen capture or located code was available at the moment of failure to compare against.";
            default:
                return "The system's observations were not specific enough to point to one clear cause automatically.";
        }
    }

    /** Whether THIS specific attempt actually reached a real, filtered candidate — the same existing condition
     *  {@link #proposedFixPlain} already uses to decide "no automatic fix could be proposed", reused here (not
     *  re-decided) as the source-of-truth eligibility to hand {@link PlainLanguage#explain}. A record can reach
     *  {@link #buildTestReportHtml} with {@code finalStatus == "NEEDS_TRIAGE"} (no candidate, or every candidate
     *  rejected by semantic validation) — that attempt is NOT eligible, regardless of how it got here. */
    private static boolean hasRealProposal(HealingAttemptRecord a) {
        return a.candidateTotal > 0 && !a.proposalKind.isBlank() && !"N/A".equals(a.proposalKind);
    }

    private static String proposedFixPlain(HealingAttemptRecord a) {
        if (a.candidateTotal == 0 || a.proposalKind.isBlank() || "N/A".equals(a.proposalKind)) {
            return "No automatic fix could be proposed for this problem.";
        }
        switch (a.proposalKind) {
            case "LOCATOR":
                return "Update the test's internal reference to this screen element to the one that's actually "
                        + "there now — the screen element itself hasn't gone away, the test's reference to it is just outdated.";
            case "TIMING":
                return "Give the screen a little more time to finish loading this field before the test checks "
                        + "it, instead of assuming it's ready immediately.";
            case "WORKFLOW":
                return "Handle a confirmation step that now appears on screen but that the test wasn't expecting.";
            default:
                return "A change was suggested for how the test recognizes part of the screen (see Technical "
                        + "Details for the exact before/after).";
        }
    }

    private static String whySelectedPlain(HealingAttemptRecord a) {
        if (a.candidateTotal == 0) return "No candidate met the bar for a safe, confident suggestion.";
        return "This was the closest, most confident match found on the live screen at the moment of failure — "
                + "and it passed the system's safety checks (see Technical Details for the full evidence).";
    }

    private static String approvalPlain(HealingAttemptRecord a) {
        switch (a.humanDecision) {
            case "APPROVED": return "You approved this exact change before anything was written.";
            case "DECLINED": return "This change was declined — nothing was written.";
            default: return "No approval has been requested yet — this is analysis only so far.";
        }
    }

    private static String compilePlain(HealingAttemptRecord a) {
        switch (a.compileResult) {
            case "PASS": return "Yes — the project still builds correctly after the change.";
            case "FAIL": return "No — the project failed to build after the change, so it was undone automatically.";
            default: return "Not applicable — no change was made to compile.";
        }
    }

    private static String retestPlain(HealingAttemptRecord a) {
        switch (a.targetStepResult) {
            case "PASS": return "The test was run again for real, and this time it passed.";
            case "FAIL": return "The test was run again for real, and it still did not pass.";
            case "UNVERIFIED": return "The test was run again, but it did not record a clear PASS for the original step, so the result could not be confirmed.";
            default: return "Not tested yet.";
        }
    }

    private static String downstreamPlain(HealingAttemptRecord a) {
        if (a.downstreamRegression == null || a.downstreamRegression.isBlank()
                || "NONE".equals(a.downstreamRegression) || "N/A".equals(a.downstreamRegression)) {
            return "No — nothing else broke because of this change.";
        }
        return "Yes — this change caused a problem elsewhere too: " + a.downstreamRegression;
    }

    private static String actionTakenPlain(HealingAttemptRecord a) {
        if (a.candidateTotal == 0 || a.proposalKind.isBlank() || "N/A".equals(a.proposalKind)) {
            return "No automatic fix could be proposed — sent for manual investigation.";
        }
        if ("APPROVED".equals(a.humanDecision)) {
            return "Fix proposed and approved — " + ("KEPT".equals(a.finalStatus) ? "kept."
                    : "RESTORE_FAILED".equals(a.finalStatus) ? "failed verification, and the rollback could not be verified (manual restore needed)."
                    : "rolled back after re-testing.");
        }
        if ("DECLINED".equals(a.humanDecision)) return "Fix proposed, but declined by the reviewer.";
        return "Fix proposed — awaiting approval.";
    }

    // =======================================================================
    // Shared helpers
    // =======================================================================

    /** Worst-to-best-known priority: a test that ended KEPT is shown as KEPT even if an earlier step in the
     *  same test needed triage first; a test that's still unresolved shows its most informative status. */
    /** Plain top-of-report summary of where this test currently stands, from the same {@code overallStatus}
     *  value the summary table already uses — dynamic, never hardcoded to a specific test/field. */
    /** The one shared "Final Result" label (section I) for a single healing attempt — reuses the exact same
     *  vocabulary {@link FailureAnalysisReport} uses for its own (always {@code FAILED_ANALYSIS_ONLY}) case. */
    private static String finalResultLabel(HealingAttemptRecord a) {
        if ("KEPT".equals(a.finalStatus)) return PlainLanguage.FINAL_FIX_RETAINED;
        if ("REVERTED".equals(a.finalStatus)) return PlainLanguage.FINAL_FIX_ROLLED_BACK;
        if ("RESTORE_FAILED".equals(a.finalStatus)) return PlainLanguage.FINAL_RESTORE_FAILED;
        return PlainLanguage.FINAL_FAILURE_ANALYSIS_ONLY;
    }

    private static String testResultHeaderPlain(String overall) {
        switch (overall) {
            case "KEPT": return "Originally FAILED — a fix was applied, verified, and kept.";
            case "REVERTED": return "Originally FAILED — a fix was tried but did not verify, so the original code was restored.";
            case "RESTORE_FAILED": return "Originally FAILED — a fix was tried and did not verify, and the original code could NOT be restored and verified: the source file may still contain the patch.";
            case "DRY_RUN": return "FAILED (preview only — nothing was applied).";
            case "NOT_APPLIED": return "FAILED — a fix was proposed but not applied.";
            default: return "FAILED — needs manual investigation.";
        }
    }

    private static String overallStatus(List<HealingAttemptRecord> forTest) {
        // A rollback that could not be verified outranks everything: the source may still hold an unverified patch.
        if (forTest.stream().anyMatch(a -> "RESTORE_FAILED".equals(a.finalStatus))) return "RESTORE_FAILED";
        if (forTest.stream().anyMatch(a -> "KEPT".equals(a.finalStatus))) return "KEPT";
        if (forTest.stream().anyMatch(a -> "REVERTED".equals(a.finalStatus))) return "REVERTED";
        if (forTest.stream().anyMatch(a -> "DRY_RUN".equals(a.finalStatus))) return "DRY_RUN";
        if (forTest.stream().anyMatch(a -> "NOT_APPLIED".equals(a.finalStatus))) return "NOT_APPLIED";
        return "NEEDS_TRIAGE";
    }

    /** Never overwrites an existing file: appends _2, _3, ... on collision (e.g. the same test healed twice
     *  within the same second). */
    private static Path uniquePath(Path dir, String baseName, String ext) {
        Path p = dir.resolve(baseName + ext);
        int n = 2;
        while (Files.exists(p)) {
            p = dir.resolve(baseName + "_" + n + ext);
            n++;
        }
        return p;
    }

    /** Same collision-avoidance as {@link #uniquePath}, but for a report FOLDER rather than a single file
     *  (the new nested {@code <TestName>_<timestamp>/} layout) — creates the directory before returning it. */
    private static Path uniqueDir(Path parent, String baseName) {
        Path p = parent.resolve(baseName);
        int n = 2;
        while (Files.exists(p)) {
            p = parent.resolve(baseName + "_" + n);
            n++;
        }
        try { Files.createDirectories(p); } catch (IOException e) {
            System.out.println("HealingDashboard: could not create report folder " + p + " - " + e.getMessage());
        }
        return p;
    }

    private static String safeName(String s) {
        String cleaned = s.replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isBlank() ? "test" : cleaned;
    }

    /** finalStatus values are already CSS-identifier-safe except "N/A", which never appears as a finalStatus
     *  in practice - kept defensive rather than assuming. */
    private static String cssSafe(String status) {
        return status == null ? "N_A" : status.replace("/", "_");
    }

    /** {@code path}'s location relative to its own top-level folder (e.g. "HospitalLocation_20260904_120000/
     *  HospitalLocation_SelfHealingReport.html" relative to "reports"), for building a "../<top>/<rest>" href
     *  from a sibling top-level folder (summary/, index/) without assuming a fixed nesting depth. */
    private static String relativeUnder(Path path, String topLevelFolderName) {
        Path p = path;
        Deque<String> parts = new ArrayDeque<>();
        while (p != null && !topLevelFolderName.equals(p.getFileName() == null ? null : p.getFileName().toString())) {
            parts.addFirst(p.getFileName().toString());
            p = p.getParent();
        }
        return String.join("/", parts);
    }

    private static String htmlHead(String title) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>" + esc(title) + "</title><style>" + CSS + "</style></head><body>";
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
            System.out.println("HealingDashboard: written to " + path.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("HealingDashboard: could not write " + path + " - " + e.getMessage());
        }
    }

    private static String gitSummary(GitStatus git) {
        if (git == null || !git.isRepo) return "Not a Git repository";
        String shortCommit = git.commit.length() > 10 ? git.commit.substring(0, 10) : git.commit;
        return "branch=" + git.branch + ", commit=" + shortCommit + ", "
                + git.modifiedOrUntrackedFiles.size() + " modified/untracked file(s)";
    }

    private static void stat(StringBuilder sb, String label, long value) { statStr(sb, label, String.valueOf(value)); }

    private static void statStr(StringBuilder sb, String label, String value) {
        sb.append("<div class='stat'><div class='n'>").append(esc(value)).append("</div><div class='l'>").append(esc(label)).append("</div></div>");
    }

    /** Never renders a truly empty cell — mirrors {@link FailureAnalysisReport#notBlank}. */
    private static String notBlankHD(String value) {
        return (value == null || value.isBlank()) ? "Not available" : value;
    }

    private static void plainRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(esc(notBlankHD(value))).append("</td></tr>");
    }

    private static String esc(String s) { return HtmlDiffRenderer.esc(s); }
}
