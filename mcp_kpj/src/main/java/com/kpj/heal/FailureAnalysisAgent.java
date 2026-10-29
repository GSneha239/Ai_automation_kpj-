package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Failure Analysis Agent - a logically SEPARATE entry point from {@link SelfHealingAgent}. Its pipeline is
 * exactly: detect -> analyze -> categorize -> identify root cause -> recommend an owner -> generate a report.
 * It NEVER locates-and-patches, never calls {@code PatchProposer} to actually propose an applied change beyond
 * read-only analysis, never touches {@code PatchApplier}, never asks for approval, and never modifies any
 * source file. Whether a failure looks "Self-Healing Eligible" is computed and DISPLAYED only - the real
 * decision to attempt healing is made independently, later, only by {@code SelfHealingAgent}'s own unchanged
 * pipeline, and only when a human explicitly asks for it (a separate command/run, never triggered from here).
 *
 * <p>Usage:</p>
 * <pre>
 *   REM single test
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.FailureAnalysisAgent \
 *       -Dexec.args="com.kpj.tests.ApplicationConfiguration_page.Location_page.HospitalLocation"
 *
 *   REM every currently-failing test
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.FailureAnalysisAgent -Dexec.args="--all"
 * </pre>
 * Reads failures the exact same way {@code SelfHealingAgent} does ({@code test-output/aggregate-data}) - run
 * the target test(s) at least once first.
 */
public class FailureAnalysisAgent {

    public static void main(String[] args) throws Exception {
        boolean allMode = false;
        List<String> positional = new ArrayList<>();
        for (String a : args) {
            if ("--all".equalsIgnoreCase(a)) allMode = true;
            else positional.add(a);
        }

        String outDir = "test-output";
        String className = null;
        if (allMode) {
            if (!positional.isEmpty()) outDir = positional.get(0);
        } else {
            if (positional.isEmpty()) {
                System.out.println("Usage: FailureAnalysisAgent <fully.qualified.TestClassName> [outDir]");
                System.out.println("       FailureAnalysisAgent --all [outDir]");
                return;
            }
            className = positional.get(0);
            if (positional.size() > 1) outDir = positional.get(1);
        }

        FailureScanner scanner = new FailureScanner(outDir);
        CodeLocator locator = new CodeLocator();
        FailureCategoryClassifier categoryClassifier = new FailureCategoryClassifier();
        ResolutionTracker tracker = new ResolutionTracker(outDir);

        List<String[]> testIdAndClass = new ArrayList<>(); // [testId, fqcn]
        String sessionLabel;

        if (allMode) {
            Set<String> failingIds = scanner.listFailingTestIds();
            if (failingIds.isEmpty()) {
                System.out.println("FailureAnalysisAgent: --all - no FAILED steps found anywhere under " + outDir + "/aggregate-data.");
                return;
            }
            Map<String, String> testIdToClass = new TestClassResolver().resolveAll();
            for (String id : failingIds) {
                if (testIdToClass.containsKey(id)) testIdAndClass.add(new String[]{id, testIdToClass.get(id)});
                else System.out.println("FailureAnalysisAgent: could not resolve testId \"" + id + "\" to a test class - skipping.");
            }
            sessionLabel = "Full Project";
            System.out.println("FailureAnalysisAgent: --all mode - " + testIdAndClass.size() + " of " + failingIds.size()
                    + " failing testId(s) resolved to a class.");
        } else {
            Path testFile = CodeLocator.sourceRoot().resolve(className.replace('.', '/') + ".java");
            if (!Files.isRegularFile(testFile)) {
                System.out.println("FailureAnalysisAgent: test source not found: " + testFile);
                return;
            }
            String testSrc = Files.readString(testFile);
            Matcher idm = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(testSrc);
            if (!idm.find()) {
                System.out.println("FailureAnalysisAgent: could not find the testId (super(\"...\")) in " + testFile);
                return;
            }
            String testId = idm.group(1);
            testIdAndClass.add(new String[]{testId, className});
            sessionLabel = className.substring(className.lastIndexOf('.') + 1);
            System.out.println("FailureAnalysisAgent: target " + className + " (testId=" + testId + ")");
        }

        List<FailureAnalysisRecord> allRecords = new ArrayList<>();
        int testsExecuted = 0;

        for (String[] pair : testIdAndClass) {
            String testId = pair[0];
            String fqcn = pair[1];

            List<FailureRecord> failures = scanner.scan(testId);
            if (failures.isEmpty()) {
                System.out.println("FailureAnalysisAgent: no FAILED steps found for testId=" + testId + " under " + outDir + "/aggregate-data.");
                continue;
            }
            testsExecuted++;
            System.out.println();
            System.out.println("---- Analyzing: [" + testId + "] " + failures.size() + " failed step(s) ----");

            for (FailureRecord failure : failures) {
                FailureAnalysisRecord rec = analyzeStep(failure, testId, fqcn, outDir, locator, categoryClassifier, tracker);

                System.out.println("  - " + failure.step);
                System.out.println("    Category: " + rec.category + " | Owner: " + rec.recommendedOwner
                        + " | Self-Healing Eligible: " + (rec.selfHealingEligible ? "YES" : "NO") + " | Status: " + rec.finalStatus);
                if (rec.rootCause != null && rec.rootCause.startsWith("VERDICT: ")) {
                    // the script-vs-application cross-check reached a verdict: say it right here, not only inside the report
                    System.out.println("    " + rec.rootCause);
                }

                allRecords.add(rec);
            }
        }

        if (allRecords.isEmpty()) {
            System.out.println("FailureAnalysisAgent: no failures analyzed - reports not generated.");
            return;
        }

        FailureAnalysisReport.Result result = FailureAnalysisReport.write(outDir, sessionLabel, testsExecuted, allRecords);
        if (result != null) {
            System.out.println();
            System.out.println("Failure analysis completed. Analysis only - nothing was modified, nothing was applied,");
            System.out.println("no approval was requested.");
            System.out.println();
            for (Path p : result.individualReportPaths.values()) {
                System.out.println("Individual report:");
                System.out.println(p);
            }
            System.out.println();
            System.out.println("Overall summary:");
            System.out.println(result.summaryPath);
            System.out.println();
            System.out.println("Report index:");
            System.out.println(result.indexPath);
        }
    }

    /**
     * Builds the Failure Analysis record for ONE failed step. This is the single place the record is assembled
     * (extracted unchanged from {@link #main}), shared by a normal analysis run and by the post-fix snapshot/refresh
     * below, so a report never differs depending on how it was produced.
     */
    static FailureAnalysisRecord analyzeStep(FailureRecord failure, String testId, String fqcn, String outDir,
                                             CodeLocator locator, FailureCategoryClassifier categoryClassifier,
                                             ResolutionTracker tracker) {
        CodeLocator.CodeLocation loc = locator.locate(fqcn, failure.step);
        FailureCategoryClassifier.Result cat = categoryClassifier.classify(failure, loc);

        FailureAnalysisRecord rec = new FailureAnalysisRecord();
        rec.testId = testId;
        rec.testClass = fqcn;
        rec.step = failure.step;
        rec.failureExpected = failure.expected;
        rec.failureActual = failure.actual;
        rec.category = cat.category.displayName();
        rec.failureDescription = "Expected: " + failure.expected + " | Actual: " + failure.actual;
        rec.evidenceAvailable = cat.evidenceAvailable;
        rec.rootCause = cat.rootCause;
        rec.severity = cat.severity;
        rec.recommendedOwner = cat.recommendedOwner;
        rec.suggestedAction = cat.suggestedAction;
        rec.selfHealingEligible = cat.selfHealingEligible;
        rec.eligibilityReason = cat.eligibilityReason;
        String shot = ScreenshotExtractor.extractStepScreenshot(outDir, testId, failure.step);
        rec.screenshotBase64 = shot == null ? "" : shot;
        // Display-only: loc was already computed above for classification; surface it here for
        // Technical Details instead of discarding it, without changing any classification behavior.
        if (loc != null) {
            rec.affectedFile = loc.pageObjectFile.toString();
            rec.affectedMethod = loc.methodName + "()";
        }
        applyTrackerStatus(rec, tracker);
        return rec;
    }

    /** Fixed By / Fixing Role / Fixed Date / Resolution / Retest Result / Final Status, straight from the tracker
     *  ("Pending"/"OPEN" when nothing was ever recorded - never invented). */
    private static void applyTrackerStatus(FailureAnalysisRecord rec, ResolutionTracker tracker) {
        ResolutionTracker.Summary res = tracker.forSignature(ResolutionTracker.signatureFor(rec.testId, rec.step));
        rec.fixedBy = res.fixedBy;
        rec.fixingRole = res.fixingRole;
        rec.fixedDate = res.fixedDate;
        rec.resolution = res.resolution;
        rec.retestResult = res.retestResult;
        rec.finalStatus = res.finalStatus;
        rec.currentStatus = res.finalStatus;
    }

    /**
     * The Failure Analysis records of every FAILED step of one test as recorded RIGHT NOW. {@code SelfHealingAgent}
     * takes this BEFORE it writes a patch, so the category / root cause describe the ORIGINAL code and failure (not the
     * fixed code, which would read as a different problem) and the original screenshot is captured before the re-run
     * overwrites the test report. Read-only; an empty list when nothing is recorded.
     */
    static List<FailureAnalysisRecord> snapshotBeforeFix(String outDir, String testId, String fqcn) throws IOException {
        FailureScanner scanner = new FailureScanner(outDir);
        CodeLocator locator = new CodeLocator();
        FailureCategoryClassifier categoryClassifier = new FailureCategoryClassifier();
        ResolutionTracker tracker = new ResolutionTracker(outDir);
        List<FailureAnalysisRecord> records = new ArrayList<>();
        for (FailureRecord failure : scanner.scan(testId)) {
            records.add(analyzeStep(failure, testId, fqcn, outDir, locator, categoryClassifier, tracker));
        }
        return records;
    }

    /**
     * Re-generates this test's Failure Analysis report, summary and index from a {@link #snapshotBeforeFix} so each
     * step's Fixed By / Fixed Date / Resolution / Retest Result / Final Status now come from the
     * {@link ResolutionTracker}. {@code SelfHealingAgent} calls it ONLY after a fix was verified and KEPT (the tracker
     * row is written first), so a report can never show success before verification. It never overwrites an earlier
     * report: {@link FailureAnalysisReport#write} always creates new timestamped files.
     *
     * @return the written report set, or {@code null} if there was nothing to refresh or the files could not be written
     */
    static FailureAnalysisReport.Result refreshAfterFix(String outDir, String fqcn, List<FailureAnalysisRecord> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return null;
        ResolutionTracker tracker = new ResolutionTracker(outDir);
        for (FailureAnalysisRecord rec : snapshot) applyTrackerStatus(rec, tracker);
        String label = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return FailureAnalysisReport.write(outDir, label, 1, snapshot);
    }
}
