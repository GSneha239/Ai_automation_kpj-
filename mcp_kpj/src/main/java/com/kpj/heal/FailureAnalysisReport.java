package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Failure Analysis Agent - reporting layer. Writes ONLY reports - this class (and everything it calls) never
 * touches a source file, never proposes or applies a patch, and never asks for approval. Deliberately kept
 * separate from {@link HealingDashboard} (its own CSS/rendering, its own folder tree) even though the two
 * look similar, because the two agents that produce them are architecturally separate: this one only reads,
 * analyzes, and reports; {@code SelfHealingAgent} is the only place healing is ever attempted, and only when
 * a human explicitly asks for it. Both reports share one product identity ({@link PlainLanguage#PRODUCT_NAME})
 * so a reader sees ONE combined workflow, with "Failure Analysis" and "Self-Healing" as its two internal
 * stages, not two unrelated tools.
 *
 * <p>Every report leads with a plain-language story (what was tested, what failed, why, in business terms)
 * built by {@link PlainLanguage} and a screenshot at the failure point (via {@link ScreenshotExtractor}) —
 * the existing technical fields are never removed, only moved into a collapsible "Technical Details /
 * Evidence" section ({@code &lt;details&gt;}, no JavaScript) underneath.</p>
 *
 * <p>Writes under {@code <outDir>/failure-analysis/}:</p>
 * <ol>
 *   <li>{@code reports/<TestName>_<timestamp>/<TestName>_FailureAnalysisReport.html} - one per test, every
 *       run, never overwritten.</li>
 *   <li>{@code summary/FailureAnalysisOverallSummary_<timestamp>.html} - one per run: an executive summary
 *       (counts, not a technical table) plus one compact row per test/step.</li>
 *   <li>{@code index/index_<timestamp>.html} - a cross-run index snapshot (newest first), built from an
 *       append-only manifest ({@code history/runs.tsv}) this class also maintains.</li>
 * </ol>
 */
public final class FailureAnalysisReport {

    private FailureAnalysisReport() {}

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String CSS =
            "*{box-sizing:border-box}body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f5f7;color:#2d2d2d;line-height:1.5}"
            + ".brand{font-size:12px;letter-spacing:.06em;text-transform:uppercase;opacity:.85;margin:0 0 4px}"
            + ".header{background:linear-gradient(135deg,#b05a1e 0%,#8a3ba8 100%);color:#fff;padding:32px 40px}"
            + ".header h1{margin:0 0 6px;font-size:26px}.header p{margin:2px 0;opacity:.9;font-size:13px}"
            + ".container{max-width:1200px;margin:26px auto;padding:0 20px 60px}"
            + ".noteBox{background:#fff3d6;color:#8a5a00;border:1px solid #f0c674;border-radius:6px;padding:10px 16px;margin-bottom:22px}"
            + ".summary{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px;margin-bottom:28px}"
            + ".stat{background:#fff;border-radius:8px;padding:14px 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + ".stat .n{font-size:22px;font-weight:700;color:#b05a1e}.stat .l{font-size:12px;color:#666}"
            + "h2{font-size:18px;color:#5c2e73;border-left:4px solid #8a3ba8;padding-left:10px;margin:30px 0 12px}"
            + ".story{background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.08);margin-bottom:20px;overflow:hidden}"
            + ".story-head{padding:12px 16px;background:#f6ecf9;font-weight:600;display:flex;justify-content:space-between;align-items:center}"
            + ".story-body{padding:16px}"
            + "table.plain{width:100%;border-collapse:collapse;margin-bottom:14px}"
            + "table.plain th{text-align:left;color:#5c2e73;width:230px;padding:8px 10px;font-size:13.5px;vertical-align:top;background:#faf6fb}"
            + "table.plain td{padding:8px 10px;font-size:13.5px;vertical-align:top;word-break:break-word;border-bottom:1px solid #f2ecf5}"
            + "details{margin-top:10px;border:1px solid #eee;border-radius:6px}"
            + "details summary{cursor:pointer;padding:10px 14px;font-weight:600;color:#5c2e73;background:#faf9fb;border-radius:6px}"
            + "details[open] summary{border-bottom:1px solid #eee;border-radius:6px 6px 0 0}"
            + "details .tech-body{padding:12px 14px}"
            + "table.meta{width:100%;border-collapse:collapse;margin-bottom:10px}"
            + "table.meta th{text-align:left;color:#5c2e73;width:190px;padding:4px 8px;font-size:13px;vertical-align:top}"
            + "table.meta td{padding:4px 8px;font-size:13px;vertical-align:top;word-break:break-word}"
            + "table.catTable,table.resultsTable{width:100%;border-collapse:collapse;margin-bottom:20px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + "table.catTable th,table.resultsTable th{background:#f6ecf9;color:#5c2e73;text-align:left;padding:8px 10px;font-size:13px}"
            + "table.catTable td,table.resultsTable td{padding:8px 10px;font-size:13px;border-top:1px solid #eee;vertical-align:top}"
            + "table.resultsTable a{color:#8a3ba8;text-decoration:none;font-weight:600}table.resultsTable a:hover{text-decoration:underline}"
            + ".pill{display:inline-block;padding:2px 9px;border-radius:10px;font-size:11px;font-weight:700}"
            + ".pYES{background:#e3f7e8;color:#1b8a3d}.pNO{background:#eee;color:#555}"
            + ".pOPEN{background:#fde2e2;color:#b3261e}.pRESOLVED{background:#e3f7e8;color:#1b8a3d}.pREOPENED{background:#fff3d6;color:#9a6b00}"
            + ".sevHIGH{background:#fde2e2;color:#b3261e}.sevMEDIUM{background:#fff3d6;color:#9a6b00}.sevLOW{background:#eee;color:#555}"
            + ".shot{margin:12px 0}.shot img{max-width:100%;border:1px solid #eee;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.1)}"
            + ".shot .cap{font-size:12.5px;color:#555;margin-top:6px;line-height:1.45}.shot .cap strong{color:#5c2e73}"
            + ".sectionHead{font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#8a3ba8;margin:18px 0 8px;padding-bottom:4px;border-bottom:1px solid #eee}"
            + ".techIntro{margin:0 0 10px;color:#666;font-size:13px}"
            + ".footer{text-align:center;color:#9a9aa5;font-size:12px;margin-top:34px}";

    public static class Result {
        public final Path summaryPath;
        public final Map<String, Path> individualReportPaths; // testId -> path
        public final Path indexPath;

        Result(Path summaryPath, Map<String, Path> individualReportPaths, Path indexPath) {
            this.summaryPath = summaryPath;
            this.individualReportPaths = individualReportPaths;
            this.indexPath = indexPath;
        }
    }

    public static Result write(String outDir, String sessionLabel, int testsExecuted, List<FailureAnalysisRecord> records) {
        Path base = Paths.get(outDir, "failure-analysis");
        Path reportsDir = base.resolve("reports");
        Path summaryDir = base.resolve("summary");
        Path indexDir = base.resolve("index");
        Path historyDir = base.resolve("history");
        try {
            Files.createDirectories(reportsDir);
            Files.createDirectories(summaryDir);
            Files.createDirectories(indexDir);
            Files.createDirectories(historyDir);
        } catch (IOException e) {
            System.out.println("FailureAnalysisReport: could not create report directories - " + e.getMessage());
            return null;
        }

        LocalDateTime runTs = LocalDateTime.now();
        String tsStr = FILE_TS.format(runTs);

        Map<String, List<FailureAnalysisRecord>> byTest = new LinkedHashMap<>();
        for (FailureAnalysisRecord r : records) byTest.computeIfAbsent(r.testId, k -> new ArrayList<>()).add(r);

        Map<String, Path> individualPaths = new LinkedHashMap<>();
        List<String[]> manifestRows = new ArrayList<>();

        for (Map.Entry<String, List<FailureAnalysisRecord>> e : byTest.entrySet()) {
            String testId = e.getKey();
            List<FailureAnalysisRecord> testRecords = e.getValue();
            String testClass = testRecords.get(0).testClass;
            String simpleName = testClass.contains(".") ? testClass.substring(testClass.lastIndexOf('.') + 1) : testClass;
            if (simpleName.isBlank()) simpleName = testId;
            String safe = safeName(simpleName);

            Path reportFolder = uniqueDir(reportsDir, safe + "_" + tsStr);
            Path individualPath = reportFolder.resolve(safe + "_FailureAnalysisReport.html");
            writeIndividualReport(individualPath, simpleName, testClass, testId, testRecords);
            individualPaths.put(testId, individualPath);

            long eligibleCount = testRecords.stream().filter(r -> r.selfHealingEligible).count();
            manifestRows.add(new String[]{
                    runTs.toString(), simpleName, testId, String.valueOf(testRecords.size()),
                    String.valueOf(eligibleCount),
                    base.relativize(individualPath).toString().replace('\\', '/'),
                    "" // summaryPath - filled in below once known
            });
        }

        Path summaryPath = uniquePath(summaryDir, "FailureAnalysisOverallSummary_" + tsStr, ".html");
        writeSummaryReport(summaryPath, sessionLabel, testsExecuted, byTest.size(), records, individualPaths, runTs);

        String summaryRel = "summary/" + summaryPath.getFileName();
        Path manifestFile = historyDir.resolve("runs.tsv");
        appendManifest(manifestFile, manifestRows, summaryRel);

        Path indexPath = uniquePath(indexDir, "index_" + tsStr, ".html");
        writeIndex(indexPath, manifestFile);

        return new Result(summaryPath, individualPaths, indexPath);
    }

    // =======================================================================
    // Individual (per-test) report
    // =======================================================================

    private static void writeIndividualReport(Path path, String simpleName, String testClass, String testId,
                                                List<FailureAnalysisRecord> testRecords) {
        StringBuilder sb = new StringBuilder();
        // One representative screen name for the report HEADER (a report normally covers one test = one
        // screen) — from the first failed step's own evidence, same resolver used per-story below. Never the
        // test/fixture class name; falls back to it in the header ONLY as a last-resort label, never claimed
        // to be "the screen" (the header still shows the real Test Class/Test ID right below either way).
        FailureAnalysisRecord first = testRecords.get(0);
        String headerScreen = PlainLanguage.resolveScreenName(first.affectedFile, first.rootCause,
                List.of(first.evidenceAvailable.split("\\s*\\|\\s*")));
        String headerTitle = (headerScreen.isBlank() ? simpleName : headerScreen) + " - Failure Analysis";
        sb.append(htmlHead(PlainLanguage.pipeTitle(headerScreen.isBlank() ? simpleName : headerScreen, "Failure Analysis")));
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Stage 1 &mdash; Failure Analysis</p>")
          .append("<h1>").append(esc(headerTitle)).append("</h1>")
          .append("<p><strong>Test Class:</strong> ").append(esc(testClass)).append("</p>")
          .append("<p><strong>Test ID:</strong> ").append(esc(testId)).append("</p>")
          .append("<p><strong>Test Result:</strong> FAILED — ").append(testRecords.size())
          .append(testRecords.size() == 1 ? " step did not pass</p>" : " steps did not pass</p>")
          .append("<p><strong>Run Timestamp:</strong> ").append(DISPLAY_TS.format(LocalDateTime.now())).append("</p>")
          .append("</div><div class='container'>");
        sb.append(lifecycleTrackerFailureAnalysisOnly("Failure Analysis"));
        sb.append("<div class='noteBox'>This is an analysis-only report - nothing was changed and nothing was applied. "
                + "To start a real, human-approved healing attempt for an eligible problem, ask Claude Code: \"heal ")
          .append(esc(simpleName)).append("\".</div>");

        for (FailureAnalysisRecord r : testRecords) {
            sb.append("<div class='story'><div class='story-head'><span>Step: ").append(esc(PlainLanguage.cleanStepName(r.step))).append("</span>")
              .append("<span class='pill p").append(r.selfHealingEligible ? "YES" : "NO").append("'>Self-Healing Eligible: ")
              .append(r.selfHealingEligible ? "YES" : "NO").append("</span></div><div class='story-body'>");

            FailureCategory cat = categoryFor(r.category);
            List<String> evidenceLines = List.of(r.evidenceAvailable.split("\\s*\\|\\s*"));
            // ONE shared derivation for every business-facing fact this story renders — see PlainLanguage's
            // own javadoc for why this replaced scattered individual calls (a real cross-file consistency bug).
            // This is the ONLY interpretation step: collect data above, call explain() once, render fx.* below.
            PlainLanguage.FailureExplanation fx = PlainLanguage.explain(testClass, r.step, r.failureExpected,
                    r.failureActual, cat, r.rootCause, evidenceLines, r.affectedFile, r.evidenceAvailable,
                    r.recommendedOwner, r.selfHealingEligible);

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
            if (!fx.evidenceGathered.isBlank()) {
                plainRow(sb, "How this evidence was gathered", fx.evidenceGathered);
            }
            sb.append("</table>");
            if (!r.screenshotBase64.isBlank()) {
                sb.append("<div class='shot'><img alt='Failure screenshot' src='data:image/png;base64,")
                  .append(r.screenshotBase64).append("'><div class='cap'><strong>FAILURE SCREENSHOT &mdash; ORIGINAL FAILURE</strong><br>")
                  .append("<strong>Failed Step:</strong> \"").append(esc(fx.failedStep)).append("\"<br>")
                  .append("<strong>What this screenshot shows:</strong> ").append(esc(fx.screenshotWhatItShows)).append("<br>")
                  .append("<strong>What this screenshot tells us:</strong> ").append(esc(fx.screenshotWhatItTellsUs)).append("<br>")
                  .append("<strong>Why this screenshot matters:</strong> ").append(esc(fx.screenshotWhyItMatters)).append("<br>")
                  .append("<strong>What this screenshot does NOT prove:</strong> ").append(esc(fx.screenshotWhatItDoesNotProve))
                  .append("</div></div>");
            } else {
                sb.append("<p class='techIntro'><strong>Failure Screenshot:</strong> Not available.<br>"
                        + "The failure was recorded, but a screenshot could not be captured at the failure point.</p>");
            }

            // G is the LAST section of a Failure Analysis report by design — this stage only ever decides
            // eligibility, it never itself proposes, applies, or verifies a fix. That entire lifecycle belongs
            // only to the separate Self-Healing report (HealingDashboard), never duplicated here.
            sb.append("<div class='sectionHead'>G. Can It Be Automatically Fixed?</div><table class='plain'>");
            plainRow(sb, "Who should investigate / fix this?", fx.responsibleOwner);
            plainRow(sb, "Why this team?", fx.whyOwner);
            plainRow(sb, "Recommended fix", fx.recommendedFix);
            plainRow(sb, "Why this fix is recommended", fx.whyFix);
            plainRow(sb, "Automatic Fixing Possible", fx.automaticFixPossible);
            plainRow(sb, "What Happens Next", fx.whatHappensNext);
            sb.append("</table>");

            sb.append("<div class='sectionHead'>Technical Details / Developer Evidence</div>");
            sb.append("<details><summary>Technical Details / Developer Evidence</summary><div class='tech-body'>");
            sb.append("<p class='techIntro'>The fields below are the same failure, in its original technical form — "
                    + "for a developer investigating this directly in code.</p><table class='meta'>");
            row(sb, "Test Class", r.testClass);
            row(sb, "Test ID", r.testId);
            row(sb, "Failed Step", r.step);
            // Failure Category/Recommended Owner/Suggested Action are the fields a developer treats as
            // authoritative, so they must be the SAME resolved category everything else in this report
            // (Root Cause, Owner/Why/Fix, the summary table) already uses — never the classifier's own,
            // possibly-overridden-by-stronger-evidence category. The classifier's original category is kept
            // too, immediately below, clearly labeled, so nothing is lost for a developer who wants it.
            row(sb, "Failure Category", fx.effectiveCategory.displayName());
            row(sb, "Failure Category (raw classifier, before evidence resolution)", r.category);
            row(sb, "Failure Description", r.failureDescription);
            row(sb, "Expected Result (raw)", r.failureExpected);
            row(sb, "Actual Result (raw)", r.failureActual);
            row(sb, "Responsible Page Object", r.affectedFile);
            row(sb, "Responsible Method", r.affectedMethod);
            row(sb, "Screenshot Captured", r.screenshotBase64.isBlank() ? "No" : "Yes");
            row(sb, "Evidence Available", r.evidenceAvailable);
            row(sb, "Root Cause (raw diagnostic)", r.rootCause);
            row(sb, "Root Cause — Technical Explanation", fx.whyItFailed);
            row(sb, "Severity", r.severity);
            row(sb, "Recommended Owner", fx.responsibleOwner);
            row(sb, "Suggested Action", fx.recommendedFix);
            row(sb, "Current Status", r.currentStatus);
            sb.append("<tr><th>Self-Healing Eligibility Reason (raw diagnostic)</th><td>").append(esc(notBlank(r.eligibilityReason))).append("</td></tr>");
            row(sb, "Final Status", r.finalStatus);
            sb.append("</table></div></details>");

            // Kept, never deleted — ResolutionTracker's own record of whether THIS failure SIGNATURE was
            // resolved by a SEPARATE, earlier Self-Healing run. Relocated into its own clearly-labeled,
            // separately-collapsed section (never inside the main Technical Details, and never in the A-G
            // non-technical story) so a reader can never mistake it for THIS execution having already gone
            // through Fix Applied / Re-test / Verification / KEEP / ROLLBACK — this analysis run did none
            // of those; it only looked up whether a past run did, for a failure that looks the same.
            sb.append("<details><summary>Historical Resolution Information (from a previous, separate Self-Healing run)</summary><div class='tech-body'>");
            sb.append("<p class='techIntro'>This does NOT describe the current analysis run above &mdash; it is "
                    + "whatever ResolutionTracker last recorded for a failure with this same signature, if any "
                    + "earlier, separate Self-Healing attempt ever resolved one.</p><table class='meta'>");
            row(sb, "Fixed By", r.fixedBy);
            row(sb, "Fixing Role", r.fixingRole);
            row(sb, "Fixed Date", r.fixedDate);
            row(sb, "Resolution", r.resolution);
            row(sb, "Retest Result", r.retestResult);
            sb.append("</table></div></details>");

            sb.append("</div></div>");
        }

        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Failure Analysis stage &middot; generated locally, no external service dependency</div>");
        sb.append("</div></body></html>");
        writeFile(path, sb.toString());
    }

    // =======================================================================
    // Overall session summary report
    // =======================================================================

    private static void writeSummaryReport(Path path, String sessionLabel, int testsExecuted, long testsWithFailures,
                                            List<FailureAnalysisRecord> records, Map<String, Path> individualPaths,
                                            LocalDateTime runTs) {
        Map<FailureCategory, Long> byCategory = new LinkedHashMap<>();
        for (FailureCategory c : FailureCategory.values()) byCategory.put(c, 0L);
        long fixed = 0, pending = 0, retested = 0, reopened = 0, eligible = 0;

        for (FailureAnalysisRecord r : records) {
            for (FailureCategory c : FailureCategory.values()) {
                if (c.displayName().equals(r.category)) byCategory.merge(c, 1L, Long::sum);
            }
            switch (r.finalStatus == null ? "OPEN" : r.finalStatus) {
                case "RESOLVED": fixed++; break;
                case "REOPENED": reopened++; break;
                default: pending++;
            }
            if (!r.retestResult.isBlank()) retested++;
            if (r.selfHealingEligible) eligible++;
        }

        StringBuilder sb = new StringBuilder();
        String title = "Failure Analysis - Executive Summary";
        sb.append(htmlHead(PlainLanguage.pipeTitle("Failure Analysis", "Executive Summary")));
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Stage 1 &mdash; Failure Analysis</p>")
          .append("<h1>").append(esc(title)).append("</h1>")
          .append("<p><strong>Scope:</strong> ").append(esc(sessionLabel)).append("</p>")
          .append("<p><strong>Run Timestamp:</strong> ").append(DISPLAY_TS.format(runTs)).append("</p>")
          .append("</div><div class='container'>");
        sb.append("<div class='noteBox'>Analysis only. Nothing in this report was applied automatically - use each "
                + "individual report's guidance (or ask Claude Code to \"heal &lt;TestName&gt;\") to start a real, "
                + "human-approved healing run for an eligible failure.</div>");

        sb.append("<div class='summary'>");
        stat(sb, "Tests executed", testsExecuted);
        stat(sb, "Tests with problems", testsWithFailures);
        stat(sb, "Problems found", records.size());
        stat(sb, "Automatically healable", eligible);
        stat(sb, "Needs a person to look", records.size() - eligible);
        stat(sb, "Already fixed", fixed);
        stat(sb, "Re-tested", retested);
        sb.append("</div>");

        sb.append("<h2>Problems Found, In Plain Terms</h2>");
        if (records.isEmpty()) {
            sb.append("<p>No problems found for this scope.</p>");
        } else {
            sb.append("<div style='overflow-x:auto'><table class='resultsTable'>")
              .append("<tr><th>Test</th><th>Failed Step</th><th>Problem</th><th>Can It Self-Heal?</th><th>Who Should Look</th><th>Status</th></tr>");
            for (FailureAnalysisRecord r : records) {
                String testLabel = PlainLanguage.friendlyName(r.testClass);
                Path link = individualPaths.get(r.testId);
                List<String> evidenceLines = List.of(r.evidenceAvailable.split("\\s*\\|\\s*"));
                // Same shared model the individual report uses for this record — never a separate derivation
                // for the summary row, so this table can never disagree with the linked individual report.
                PlainLanguage.FailureExplanation rowFx = PlainLanguage.explain(r.testClass, r.step, r.failureExpected,
                        r.failureActual, categoryFor(r.category), r.rootCause, evidenceLines, r.affectedFile,
                        r.evidenceAvailable, r.recommendedOwner, r.selfHealingEligible);
                sb.append("<tr><td>");
                if (link != null) sb.append("<a target='_blank' rel='noopener' href='../reports/").append(esc(relativeUnder(link, "reports"))).append("'>").append(esc(testLabel)).append("</a>");
                else sb.append(esc(testLabel));
                sb.append("</td><td>").append(esc(PlainLanguage.cleanStepName(r.step))).append("</td>")
                  .append("<td>").append(esc(rowFx.categoryLabel)).append("</td>")
                  .append("<td><span class='pill p").append(rowFx.automaticFixPossible).append("'>")
                  .append(rowFx.automaticFixPossible).append("</span></td>")
                  .append("<td>").append(esc(rowFx.responsibleOwner)).append("</td>")
                  .append("<td><span class='pill p").append(esc(cssSafe(r.finalStatus))).append("'>").append(esc(r.finalStatus)).append("</span></td></tr>");
            }
            sb.append("</table></div>");
        }

        sb.append("<details><summary>Technical Details / Evidence — category and owner breakdown</summary><div class='tech-body'>");
        sb.append("<h2>Failure Count by Category</h2><table class='catTable'><tr><th>Category</th><th>Count</th></tr>");
        for (FailureCategory c : FailureCategory.values()) {
            sb.append("<tr><td>").append(esc(c.displayName())).append("</td><td>").append(byCategory.get(c)).append("</td></tr>");
        }
        sb.append("</table></div></details>");

        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; Failure Analysis stage &middot; generated locally, no external service dependency</div>");
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
                sb.append(String.join("\t", "timestamp", "testName", "testId", "failureCount", "eligibleCount",
                        "reportPath", "summaryPath")).append('\n');
            }
            for (String[] row : rows) {
                row[6] = summaryRel;
                for (int i = 0; i < row.length; i++) {
                    sb.append(row[i] == null ? "" : row[i].replace('\t', ' ').replace('\n', ' '));
                    sb.append(i < row.length - 1 ? '\t' : '\n');
                }
            }
            Files.writeString(manifestFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("FailureAnalysisReport: could not append report manifest - " + e.getMessage());
        }
    }

    private static void writeIndex(Path indexPath, Path manifestFile) {
        List<String[]> rows = new ArrayList<>();
        if (Files.isRegularFile(manifestFile)) {
            try {
                List<String> lines = Files.readAllLines(manifestFile);
                for (int i = 1; i < lines.size(); i++) {
                    String[] c = lines.get(i).split("\t", -1);
                    if (c.length >= 7) rows.add(c);
                }
            } catch (IOException ignore) { /* index is a best-effort derived view */ }
        }
        Collections.reverse(rows);

        StringBuilder sb = new StringBuilder();
        sb.append(htmlHead(PlainLanguage.pipeTitle("Failure Analysis", "Report Index")));
        sb.append("<div class='header'><p class='brand'>").append(PlainLanguage.PRODUCT_NAME).append("</p>")
          .append("<h1>Failure Analysis Report Index</h1><p>Recent analysis runs, newest first.</p></div>")
          .append("<div class='container'>");
        sb.append("<div style='overflow-x:auto'><table class='resultsTable'>")
          .append("<tr><th>Timestamp</th><th>Test</th><th>Failures</th><th>Eligible for Healing</th><th>Report</th><th>Summary</th></tr>");
        for (String[] c : rows) {
            String ts = c[0], testName = c[1], failureCount = c[3], eligibleCount = c[4], reportPath = c[5], summaryPath = c[6];
            sb.append("<tr><td>").append(esc(ts)).append("</td><td>").append(esc(testName)).append("</td>")
              .append("<td>").append(esc(failureCount)).append("</td><td>").append(esc(eligibleCount)).append("</td>")
              .append("<td><a target='_blank' rel='noopener' href='../").append(esc(reportPath)).append("'>open</a></td>")
              .append("<td><a target='_blank' rel='noopener' href='../").append(esc(summaryPath)).append("'>open</a></td></tr>");
        }
        sb.append("</table></div>");
        sb.append("<div class='footer'>").append(PlainLanguage.PRODUCT_NAME).append(" &middot; generated locally, no external service dependency</div>");
        sb.append("</div></body></html>");
        writeFile(indexPath, sb.toString());
    }

    // =======================================================================
    // Shared helpers
    // =======================================================================

    private static FailureCategory categoryFor(String displayName) {
        for (FailureCategory c : FailureCategory.values()) if (c.displayName().equals(displayName)) return c;
        return FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION;
    }

    /** Simple horizontal breadcrumb of the full lifecycle, with the current stage highlighted — used ONLY by
     *  the Self-Healing report ({@link HealingDashboard}), which legitimately moves through every one of
     *  these stages for a single eligible failure. */
    static String lifecycleTracker(String currentStage) {
        String[] stages = {"Test Execution", "Test Report", "Failure Analysis", "Root Cause", "Healing Proposal",
                "Human Approval", "Backup", "Fix Applied", "Compile", "Re-test", "Verification", "KEEP or ROLLBACK", "Final Report"};
        return lifecycleTrackerFrom(stages, currentStage);
    }

    /** The SAME breadcrumb, but scoped to ONLY the Failure Analysis stage's own sub-steps — this report never
     *  proposes, approves, applies, or verifies a fix, so it must never NAME those later Self-Healing stages
     *  (even as greyed-out labels), per the strict separation between the two reports. */
    static String lifecycleTrackerFailureAnalysisOnly(String currentStage) {
        String[] stages = {"Test Execution", "Test Report", "Failure Analysis", "Root Cause", "Evidence", "Eligibility Decision"};
        return lifecycleTrackerFrom(stages, currentStage);
    }

    private static String lifecycleTrackerFrom(String[] stages, String currentStage) {
        StringBuilder sb = new StringBuilder("<div style='display:flex;flex-wrap:wrap;gap:6px;margin-bottom:22px;font-size:11.5px'>");
        for (String s : stages) {
            boolean current = s.equalsIgnoreCase(currentStage);
            sb.append("<span style='padding:4px 9px;border-radius:12px;background:")
              .append(current ? "#8a3ba8;color:#fff;font-weight:700" : "#eee;color:#777")
              .append("'>").append(esc(s)).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static Path uniquePath(Path dir, String baseName, String ext) {
        Path p = dir.resolve(baseName + ext);
        int n = 2;
        while (Files.exists(p)) {
            p = dir.resolve(baseName + "_" + n + ext);
            n++;
        }
        return p;
    }

    private static Path uniqueDir(Path parent, String baseName) {
        Path p = parent.resolve(baseName);
        int n = 2;
        while (Files.exists(p)) {
            p = parent.resolve(baseName + "_" + n);
            n++;
        }
        try { Files.createDirectories(p); } catch (IOException e) {
            System.out.println("FailureAnalysisReport: could not create report folder " + p + " - " + e.getMessage());
        }
        return p;
    }

    private static String safeName(String s) {
        String cleaned = s.replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isBlank() ? "test" : cleaned;
    }

    private static String cssSafe(String status) {
        return status == null ? "N_A" : status.replace("/", "_").replace(" ", "_");
    }

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
            System.out.println("FailureAnalysisReport: written to " + path.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("FailureAnalysisReport: could not write " + path + " - " + e.getMessage());
        }
    }

    private static void stat(StringBuilder sb, String label, long value) {
        sb.append("<div class='stat'><div class='n'>").append(value).append("</div><div class='l'>").append(esc(label)).append("</div></div>");
    }

    /** Never renders a truly empty cell — every "not filled in" value must say WHY (per requirement: no
     *  silently blank required field), never a bare empty `<td></td>`. */
    static String notBlank(String value) {
        return (value == null || value.isBlank()) ? "Not available" : value;
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(esc(notBlank(value))).append("</td></tr>");
    }

    private static void plainRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(esc(notBlank(value))).append("</td></tr>");
    }

    private static String esc(String s) { return HtmlDiffRenderer.esc(s); }
}
