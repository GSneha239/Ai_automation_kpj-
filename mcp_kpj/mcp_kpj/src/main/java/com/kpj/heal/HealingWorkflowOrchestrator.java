package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Requirement (automatic triggering into the test execution workflow) — a single, additional entry point that
 * chains the EXISTING, unchanged agents together instead of requiring three separate manual Maven commands:
 * <pre>
 *   Test Runner (unchanged)  -&gt;  FailureAnalysisAgent (unchanged)  -&gt;  SelfHealingAgent propose (unchanged)
 * </pre>
 * This class contains NO failure-analysis or healing logic of its own — every real decision (category, owner,
 * evidence, eligibility, candidate generation, semantic validation, loop protection, quality guard, confidence/
 * risk) is made by calling {@link FailureAnalysisAgent#main} and {@link SelfHealingAgent#main} directly,
 * in-process, exactly as a human typing the manual commands would invoke them. Nothing here is duplicated from
 * either agent; this is purely sequencing plus one eligibility read (via the SAME {@link
 * FailureCategoryClassifier} both agents already use) to decide WHETHER Self-Healing should be invoked at all.
 *
 * <p><b>Stops at propose, always.</b> This orchestrator NEVER calls {@code apply-pending} and NEVER supplies an
 * approval decision — {@code SelfHealingAgent}'s own {@code propose} mode already stops before any file is
 * written or any approval is requested (see its class doc), and this class adds nothing that could change
 * that. A human must review whatever {@code propose} produces (in Claude Code chat) and explicitly approve
 * before {@code apply-pending} is ever invoked, exactly like the existing manual chat-driven flow.</p>
 *
 * <p>Usage (additional capability — every existing manual command below still works unchanged):</p>
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.HealingWorkflowOrchestrator ^
 *       -Dexec.args="com.kpj.tests.Op_Page.Appointment.DoctorwiseAppointmentScheduleTest"
 *
 *   REM skip re-running the test (it was already run this session) and continue from Failure Analysis:
 *   ... -Dexec.args="com.kpj.tests.Op_Page.Appointment.DoctorwiseAppointmentScheduleTest test-output --skip-run"
 *
 *   REM every currently-failing test, one at a time - same discovery SelfHealingAgent/FailureAnalysisAgent
 *   REM --all already use ({@link FailureScanner#listFailingTestIds()} + {@link TestClassResolver}):
 *   ... -Dexec.args="--all"
 * </pre>
 */
public class HealingWorkflowOrchestrator {

    private static final Pattern TEST_ID_PATTERN = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)");

    /** One resolved test's terminal outcome - built only to render the {@code --all} aggregate summary; the
     *  single-test invocation computes the same value but never prints or otherwise uses it, so single-test
     *  console output is unchanged. */
    enum Terminal { RESOLUTION_FAILED, TEST_RUN_FAILED, NO_FRESH_RESULTS, NO_FAILURES, FAILURE_ANALYSIS_ONLY,
                    PENDING_PROPOSAL, NO_PENDING_PROPOSAL }

    static final class RunResult {
        final String fqcn;
        final Terminal terminal;
        RunResult(String fqcn, Terminal terminal) { this.fqcn = fqcn; this.terminal = terminal; }
    }

    public static void main(String[] args) throws Exception {
        boolean skipRun = false;
        boolean allMode = false;
        List<String> positional = new ArrayList<>();
        for (String a : args) {
            if ("--skip-run".equalsIgnoreCase(a)) skipRun = true;
            else if ("--all".equalsIgnoreCase(a)) allMode = true;
            else positional.add(a);
        }

        if (allMode) {
            String outDir = positional.isEmpty() ? "test-output" : positional.get(0);
            runAll(outDir, skipRun);
            return;
        }

        if (positional.isEmpty()) {
            System.out.println("Usage: HealingWorkflowOrchestrator <fully.qualified.TestClassName> [outDir] [--skip-run]");
            System.out.println("       HealingWorkflowOrchestrator --all [outDir] [--skip-run]");
            System.out.println("Chains, unchanged: test run -> FailureAnalysisAgent -> SelfHealingAgent propose.");
            System.out.println("Never applies anything - approval always happens separately, in chat, before apply-pending.");
            return;
        }
        String fqcn = positional.get(0);
        String outDir = positional.size() > 1 ? positional.get(1) : "test-output";
        runOne(fqcn, outDir, skipRun);
    }

    /**
     * Requirement 10 (native {@code --all}) — discovers every currently-failing testId via the SAME {@link
     * FailureScanner#listFailingTestIds()} {@code SelfHealingAgent --all} and {@code FailureAnalysisAgent
     * --all} already use, resolves each to a class via the SAME {@link TestClassResolver#resolveAll()}, then
     * runs the IDENTICAL single-test workflow ({@link #runOne}) for each in turn — no new discovery logic, no
     * new workflow logic, nothing duplicated. Never calls {@code apply-pending}; a per-test {@code
     * PENDING_HEAL_FILE} still requires its own separate, explicit chat approval before anything is ever
     * applied, exactly like the single-test path — this method only sequences and summarizes, same as {@code
     * SelfHealingAgent}'s own {@code --all} mode already does for the interactive path.
     */
    private static void runAll(String outDir, boolean skipRun) throws Exception {
        System.out.println("========================================================================");
        System.out.println("HealingWorkflowOrchestrator: --all");
        System.out.println("========================================================================");

        Set<String> failingIds = new FailureScanner(outDir).listFailingTestIds();
        if (failingIds.isEmpty()) {
            System.out.println();
            System.out.println("HealingWorkflowOrchestrator: --all — no FAILED steps found anywhere under " + outDir + "/aggregate-data.");
            System.out.println("WORKFLOW_COMPLETE: NO_FAILURES");
            return;
        }

        Map<String, String> testIdToClass = new TestClassResolver().resolveAll();
        List<String> resolvedFqcns = new ArrayList<>();
        for (String id : failingIds) {
            if (testIdToClass.containsKey(id)) resolvedFqcns.add(testIdToClass.get(id));
            else System.out.println("HealingWorkflowOrchestrator: could not resolve testId \"" + id + "\" to a test class — skipping.");
        }
        System.out.println();
        System.out.println("HealingWorkflowOrchestrator: --all mode — " + resolvedFqcns.size() + " of " + failingIds.size()
                + " failing testId(s) resolved to a class.");

        List<RunResult> results = new ArrayList<>();
        for (String fqcn : resolvedFqcns) {
            System.out.println();
            System.out.println("------------------------------------------------------------------------");
            results.add(runOne(fqcn, outDir, skipRun));
        }

        System.out.println();
        System.out.println("========================================================================");
        System.out.println("HealingWorkflowOrchestrator: --all summary");
        System.out.println("========================================================================");
        int pending = 0;
        for (RunResult r : results) {
            System.out.println("  " + r.fqcn + " -> " + r.terminal);
            if (r.terminal == Terminal.PENDING_PROPOSAL) pending++;
        }
        System.out.println();
        System.out.println(results.size() + " test(s) processed, " + pending + " pending proposal(s) awaiting human "
                + "approval in chat (review and approve/decline one at a time — never batch).");
        System.out.println("WORKFLOW_COMPLETE: ALL_DONE");
    }

    /** The exact single-test workflow — test execution → failure detection → Failure Analysis → eligibility
     *  gate → Self-Healing propose (only if eligible) — unchanged in behavior from before requirement 10.
     *  Called directly for a single-test invocation, and once per resolved test by {@link #runAll}; nothing
     *  about this method differs between the two callers. */
    private static RunResult runOne(String fqcn, String outDir, boolean skipRun) throws Exception {
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        System.out.println("========================================================================");
        System.out.println("HealingWorkflowOrchestrator: " + fqcn);
        System.out.println("========================================================================");

        // ---- STEP 1: TEST EXECUTION — the exact same Maven invocation a human would type; nothing about how
        // the test itself runs is changed by this class. ----
        Instant runStart = Instant.now();
        int testRunExit = 0;
        String launchProblem = null;
        if (skipRun) {
            System.out.println();
            System.out.println("[1/4] TEST EXECUTION — skipped (--skip-run): using the most recent recorded run for " + simpleName + ".");
        } else {
            System.out.println();
            System.out.println("[1/4] TEST EXECUTION — running " + simpleName + " (unchanged Maven Surefire invocation)...");
            try {
                testRunExit = SelfHealingAgent.runMaven("-q", "-Dtest=" + SelfHealingAgent.testSelector(fqcn), "test");
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                launchProblem = String.valueOf(e);
            }
        }

        String testId = resolveTestId(fqcn);
        if (testId == null) {
            System.out.println("HealingWorkflowOrchestrator: could not resolve the testId (super(\"...\")) for " + fqcn + " — stopping.");
            return new RunResult(fqcn, Terminal.RESOLUTION_FAILED);
        }

        // A fresh run is only usable if it ran and left a NEW result in THIS output folder. Surefire is configured to ignore test
        // failures, so a non-zero exit means the build/run itself broke (compile error, no such test, Maven missing) and whatever
        // is in <outDir>/aggregate-data is an OLD run - analysing it would present stale evidence as if it were this run's.
        if (!skipRun) {
            if (launchProblem != null || testRunExit != 0) {
                System.out.println();
                System.out.println("[1/4] TEST EXECUTION FAILED — " + (launchProblem != null ? "Maven could not be launched (" + launchProblem + ")"
                        : "Maven exited with code " + testRunExit) + ". No result from this run exists, so nothing is analyzed.");
                System.out.println("WORKFLOW_COMPLETE: TEST_RUN_FAILED — fix the build/run problem and start again.");
                return new RunResult(fqcn, Terminal.TEST_RUN_FAILED);
            }
            if (!new FailureScanner(outDir).evidenceSince(testId, runStart).freshOutputFound) {
                System.out.println();
                System.out.println("[1/4] TEST EXECUTION produced no new result for " + testId + " under " + outDir + "/aggregate-data. "
                        + "The tests write to the folder named by -Ddevhis.outdir / DEVHIS_OUTDIR (default test-output) - it must be the same "
                        + "folder as this command's output folder. Older results in " + outDir + " are NOT analyzed.");
                System.out.println("WORKFLOW_COMPLETE: NO_FRESH_RESULTS — make the test output folder and this output folder the same, then start again.");
                return new RunResult(fqcn, Terminal.NO_FRESH_RESULTS);
            }
        }

        return analyzeCompletedRun(fqcn, outDir, testId, simpleName);
    }

    /**
     * Steps 2-4 of the workflow (failure detection -&gt; Failure Analysis -&gt; eligibility gate -&gt; Self-Healing propose),
     * for a run that has ALREADY finished and whose results the caller has already proven fresh. It launches NO test and NO
     * Maven. {@link #runOne} calls it after its own step 1 (unchanged behaviour); {@link PostRunCoordinator} calls it after
     * validating the exact completed run it was handed. Still propose-only: it never calls {@code apply-pending}.
     */
    static RunResult analyzeCompletedRun(String fqcn, String outDir, String testId, String simpleName) throws Exception {
        // ---- STEP 2: AUTOMATIC FAILURE-DETECTION GATE — reads the SAME aggregate-data every existing agent
        // reads; invents nothing new about what counts as a failure. ----
        FailureScanner scanner = new FailureScanner(outDir);
        List<FailureRecord> failures = scanner.scan(testId);
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("[2/4] FAILURE DETECTION — no FAILED steps recorded for " + simpleName
                    + " under " + outDir + "/aggregate-data. Healing workflow stops here (nothing to analyze).");
            System.out.println("WORKFLOW_COMPLETE: NO_FAILURES");
            return new RunResult(fqcn, Terminal.NO_FAILURES);
        }
        System.out.println("[2/4] FAILURE DETECTION — " + failures.size() + " failed step(s) recorded for " + simpleName
                + " — automatically continuing to Failure Analysis.");

        // ---- STEP 3: AUTOMATIC FAILURE ANALYSIS TRIGGER — calls the REAL agent, unchanged, in-process.
        // FailureAnalysisAgent remains the sole source of category/owner/evidence/eligibility/status; this
        // class never computes or overrides any of that itself. ----
        System.out.println();
        System.out.println("[3/4] FAILURE ANALYSIS — automatically invoking FailureAnalysisAgent...");
        System.out.println();
        FailureAnalysisAgent.main(new String[]{fqcn, outDir});

        // ---- STEP 4: ELIGIBILITY GATE — reads eligibility via the EXACT SAME FailureCategoryClassifier call
        // FailureAnalysisAgent (and SelfHealingAgent) already make; never a second opinion, never invented
        // here. Self-Healing is triggered only if the SAME classifier that just produced the report above
        // says at least one step is eligible - environment/infrastructure/auth/business-validation/manual-
        // investigation failures are excluded by that SAME classifier, not by any logic added in this class. ----
        CodeLocator locator = new CodeLocator();
        FailureCategoryClassifier categoryClassifier = SelfHealingAgent.newCategoryClassifier();
        // This is only the coarse "is there anything worth proposing" gate. It does NOT choose the step: SelfHealingAgent
        // propose re-checks Failure Analysis eligibility for EACH step it considers and never proposes for an ineligible one.
        List<String> eligibleSteps = new ArrayList<>();
        for (FailureRecord f : failures) {
            CodeLocator.CodeLocation loc = locator.locate(fqcn, f.step);
            FailureCategoryClassifier.Result cat = categoryClassifier.classify(f, loc);
            if (cat.selfHealingEligible) eligibleSteps.add(f.step);
        }
        boolean anyEligible = !eligibleSteps.isEmpty();

        System.out.println();
        if (anyEligible) {
            System.out.println("Failure Analysis: " + eligibleSteps.size() + " of " + failures.size()
                    + " failed step(s) are Self-Healing Eligible: " + eligibleSteps
                    + ". Only those steps can be proposed; every other step stays with manual investigation.");
        }
        if (!anyEligible) {
            // Unified report - read-only over existing artifacts, never throws. Called BEFORE the final marker so the
            // WORKFLOW_COMPLETE line below stays the last line of this path. No proposal exists here, so it reports
            // "NO HEALING RESULT" and links the Failure Analysis report that was just written.
            UnifiedHealingReport.generateQuietly(outDir, testId);
            System.out.println("[4/4] SELF-HEALING TRIGGER — no failed step is Self-Healing Eligible per Failure Analysis "
                    + "above (environment/infrastructure/authentication/business-validation/manual-investigation, or no "
                    + "safe candidate shape at all). SelfHealingAgent will NOT be invoked.");
            System.out.println("WORKFLOW_COMPLETE: FAILURE_ANALYSIS_ONLY — Manual investigation required.");
            return new RunResult(fqcn, Terminal.FAILURE_ANALYSIS_ONLY);
        }

        // ---- AUTOMATIC SELF-HEALING TRIGGER — propose mode ONLY. This calls the REAL agent, unchanged,
        // in-process; every existing safety gate inside it (loop protection, ProtectedResourcePolicy,
        // PatchQualityGuard, SemanticValidator, confidence/risk scoring) runs exactly as it does when a human
        // types the command by hand. This method returns having written, at most, a PendingHeal file and
        // printed PENDING_HEAL_FILE: <path> — it NEVER applies anything and NEVER asks for or receives an
        // approval decision. ----
        System.out.println("[4/4] SELF-HEALING TRIGGER — at least one step is Self-Healing Eligible — automatically "
                + "invoking SelfHealingAgent (propose mode only)...");
        System.out.println();
        // Requirement 10 — snapshot the pending directory before/after, purely to know (for the --all
        // aggregate summary) whether THIS call produced a new proposal. Pure observation of what
        // SelfHealingAgent itself just wrote (or didn't) — never re-derives, second-guesses, or duplicates
        // its own PENDING_HEAL_FILE/NO_PENDING_PROPOSAL decision.
        Set<String> pendingBefore = listPendingFiles(outDir);
        SelfHealingAgent.main(new String[]{"propose", fqcn, outDir});
        Set<String> pendingAfter = listPendingFiles(outDir);
        pendingAfter.removeAll(pendingBefore);
        boolean pendingCreated = !pendingAfter.isEmpty();

        // Unified report - read-only over the existing artifacts, never throws. Shows "PROPOSED, awaiting approval"
        // when a proposal was just written; nothing is applied or verified at this stage.
        UnifiedHealingReport.generateQuietly(outDir, testId);

        System.out.println();
        System.out.println("========================================================================");
        System.out.println("HealingWorkflowOrchestrator: automatic analysis/proposal stage complete.");
        System.out.println("If PENDING_HEAL_FILE was printed above, review the proposal and reply with your explicit");
        System.out.println("approval in chat - nothing is ever applied without it. If NO_PENDING_PROPOSAL was printed,");
        System.out.println("no sufficiently supported automatic fix was found; manual investigation is required.");
        System.out.println("========================================================================");

        return new RunResult(fqcn, pendingCreated ? Terminal.PENDING_PROPOSAL : Terminal.NO_PENDING_PROPOSAL);
    }

    /** File names currently under {@code <outDir>/self-healing/pending/} — empty set if the directory doesn't
     *  exist yet (e.g. before the very first proposal this project has ever produced). */
    private static Set<String> listPendingFiles(String outDir) {
        Path dir = Paths.get(outDir, "self-healing", "pending");
        if (!Files.isDirectory(dir)) return new HashSet<>();
        try (var stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    /** Same resolution FailureAnalysisAgent/SelfHealingAgent already do independently - duplicated here (a
     *  small, already-3x-repeated regex read) rather than adding a shared dependency between them just for
     *  this one lookup. */
    private static String resolveTestId(String fqcn) throws IOException {
        Path testFile = CodeLocator.sourceRoot().resolve(fqcn.replace('.', '/') + ".java");
        if (!Files.isRegularFile(testFile)) return null;
        Matcher m = TEST_ID_PATTERN.matcher(Files.readString(testFile));
        return m.find() ? m.group(1) : null;
    }
}
