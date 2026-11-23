package com.kpj.heal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kpj.heal.PostRunTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of {@link PostRunCoordinator}: what it analyses, what it refuses, and that it always stops at the human-approval
 * checkpoint. The "test run" is a set of synthetic result files with controlled timestamps in temp folders; the sources are a temp tree.
 * No Maven, browser or DevHIS is started, and no project test (AppointmentListTest included) is ever run. Every test proves that the
 * project's own sources, jars and {@code test-output*} folders were not touched, and that no Maven run was launched by the analysis.
 */
class PostRunCoordinatorTest {

    @TempDir
    Path tmp;

    private static String outputsBefore;
    private static String jarsBefore;

    private Path testRoot, out, flowPage;
    private String sourcesBefore;
    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private long now;

    @BeforeAll
    static void fingerprintTheRealOutputs() throws IOException {
        outputsBefore = outputsContentFingerprint();
        jarsBefore = jarsFingerprint();
    }

    @AfterAll
    static void realOutputsAndJarsAreByteIdentical() throws IOException {
        assertEquals(outputsBefore, outputsContentFingerprint(), "every test-output* folder is byte-identical to before");
        assertEquals(jarsBefore, jarsFingerprint(), "the shaded jars were not rebuilt or replaced");
    }

    @BeforeEach
    void setUp() throws IOException {
        sourcesBefore = sourcesFingerprint();
        testRoot = tmp.resolve("tests");
        out = tmp.resolve("out");
        Files.createDirectories(out);
        writeAllFlows(testRoot);
        flowPage = testRoot.resolve("src").resolve("SynFlowPage.java");
        CodeLocator.testRootOverride = testRoot;
        TestClassResolver.rootOverride = testRoot;
        ImpactAnalyzer.testRootOverride = testRoot.resolve("no-dependents");
        realLauncher = SelfHealingAgent.mavenLauncher;
        SelfHealingAgent.mavenLauncher = args -> { mavenCalls.add(String.join(" ", args)); return 0; };
        now = System.currentTimeMillis();
    }

    @AfterEach
    void restoreAndProveNothingRealWasTouched() throws IOException {
        SelfHealingAgent.mavenLauncher = realLauncher;
        SelfHealingAgent.categoryClassifierOverride = null;
        CodeLocator.testRootOverride = null;
        TestClassResolver.rootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
        System.clearProperty("devhis.postrun");
        System.clearProperty("devhis.postrun.llm");
        System.clearProperty("LLM_ENABLED");
        System.clearProperty("LLM_ENDPOINT");
        System.clearProperty("LLM_API_KEY");
        assertEquals(sourcesBefore, sourcesFingerprint(), "no project test or page-object source was modified");
        assertEquals(List.of(), mavenCalls, "the post-run analysis launches no Maven run at all (no test, no compile, no re-run)");
        assertFalse(LlmConfig.suppressedForAutomaticPostRun, "the LLM suppression flag never leaks out of a coordinator run");
        assertEquals(null, FailureScanner.sidecarNotBeforeMillis, "the sidecar freshness floor never leaks out of a coordinator run");
        assertFalse(PostRunCoordinator.isActive(), "the coordinator is not left 'active'");
    }

    // ------------------------------------------------------------------ helpers

    private Instant inRun() { return Instant.ofEpochMilli(now - 5000); }

    private RunContext ctx(String entry) {
        RunContext c = new RunContext();
        c.entryPoint = entry;
        c.outDir = out.toString();
        c.startedAtMillis = now - 10_000;
        c.endedAtMillis = now;
        c.exitCodes = new ArrayList<>(List.of(0));
        return c;
    }

    private void freshEligibleFailure() throws IOException {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, inRun());
        writeSidecar(out, "SYN_FLOW", ELIGIBLE_ACTUAL, inRun());
    }

    private String runCoordinator(RunContext c, PostRunCoordinator.Result[] holder) throws Exception {
        return capture(() -> holder[0] = PostRunCoordinator.run(c));
    }

    private String run(RunContext c) throws Exception { return runCoordinator(c, new PostRunCoordinator.Result[1]); }

    private boolean hasPending() throws IOException {
        Path dir = out.resolve("self-healing").resolve("pending");
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.list(dir)) { return s.findAny().isPresent(); }
    }

    private boolean analysisRan() {
        return Files.exists(out.resolve("failure-analysis"));
    }

    private void assertNothingWasAnalysed(String log) throws IOException {
        assertFalse(analysisRan(), "no Failure Analysis ran: " + log);
        assertFalse(hasPending(), "no proposal exists");
        assertFalse(log.contains("FAILURE ANALYSIS"), log);
    }

    private void assertPageUntouched() throws IOException {
        String src = Files.readString(flowPage);
        assertTrue(src.contains(LITERAL_LINE) && !src.contains("Locationid"), "the (temporary) page object is unpatched");
    }

    // ================================================================== passing and failing runs

    @Test
    void aPassingRun_createsNoProposal_andRunsNoFailureAnalysis() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", false, "", inRun());
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FAILURES"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void anEligibleFailure_reachesAProposal_andStopsForHumanApproval() throws Exception {
        freshEligibleFailure();
        PostRunCoordinator.Result[] r = new PostRunCoordinator.Result[1];
        String log = runCoordinator(ctx("batch-module-ip-devhis"), r);

        assertTrue(log.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), log);
        assertEquals(1, r[0].pendingFiles.size(), "exactly one proposal");
        String hash = r[0].pendingHashes.get(0);
        assertTrue(hash.matches("[0-9a-f]{64}"), "the exact 64-hex approval hash is reported: " + hash);
        assertTrue(Files.isRegularFile(Paths.get(r[0].pendingFiles.get(0))), "the proposal file exists");
        assertTrue(log.contains("PENDING_HEAL_FILE: " + r[0].pendingFiles.get(0)) && log.contains("PROPOSAL_HASH    : " + hash), "path and full hash are printed:\n" + log);
        assertTrue(log.contains("STOP - HUMAN APPROVAL REQUIRED") && log.contains("Nothing has been applied"), log);
        // approval is mandatory: nothing was applied, backed up or changed
        assertPageUntouched();
        assertFalse(Files.exists(out.resolve("self-healing").resolve("backups")), "no backup was taken");
        assertTrue(files(out).stream().noneMatch(p -> p.toString().endsWith(".bak") || p.toString().contains("backup")), "no backup artefact anywhere: " + files(out));
        assertFalse(log.contains("APPLIED") || log.contains("KEEP") || log.contains("ROLLBACK"), "no apply/keep/rollback happened: " + log);
    }

    @Test
    void anIneligibleFailure_neverReachesProposalGeneration() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ENVIRONMENT_ACTUAL, inRun());
        writeSidecar(out, "SYN_FLOW", ENVIRONMENT_ACTUAL, inRun());
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: FAILURE_ANALYSIS_ONLY"), log);
        assertTrue(log.contains("SelfHealingAgent will NOT be invoked"), "the eligibility gate stopped it: " + log);
        assertTrue(analysisRan(), "Failure Analysis itself did run");
        assertFalse(hasPending(), "no proposal");
        assertFalse(Files.exists(out.resolve("self-healing").resolve("session")), "SelfHealingAgent propose was never entered");
    }

    @Test
    void theEligibilityGateIsNotBypassed_evenWhenTheFailureLooksFixable() throws Exception {
        freshEligibleFailure();   // the real classifier would accept this one...
        SelfHealingAgent.categoryClassifierOverride = new FailureCategoryClassifier() {
            @Override
            public Result classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
                Result real = super.classify(failure, loc);
                return new Result(real.category, real.rootCause, real.evidenceAvailable, real.recommendedOwner, real.severity,
                        real.suggestedAction, false, "scripted: not eligible", real.candidates);
            }
        };   // ...but Failure Analysis says no, so nothing may be proposed
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: FAILURE_ANALYSIS_ONLY"), log);
        assertFalse(hasPending(), "an ineligible failure produces no proposal");
    }

    // ================================================================== stale / missing / mismatched output

    @Test
    void staleResults_areRejected_andNeverAnalysed() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 3_600_000));   // an hour before the run
        writeSidecar(out, "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 3_600_000));
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FRESH_RESULTS") && log.contains("older"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void aMissingOutputFolder_isRejected_andNotCreated() throws Exception {
        RunContext c = ctx("batch-module-ip-devhis");
        c.outDir = tmp.resolve("does-not-exist").toString();
        String log = run(c);
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FRESH_RESULTS"), log);
        assertFalse(Files.exists(tmp.resolve("does-not-exist")), "the folder was not created just to say so");
    }

    @Test
    void anOutputFolderWithNoResultFiles_isRejected() throws Exception {
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FRESH_RESULTS"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void resultsWrittenAfterTheRunEnded_areMismatchedOutput() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now + 60_000));   // another run still writing here
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_COMPLETE: MISMATCHED_OUTPUT") && log.contains("AFTER this run ended"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void freshResultsOutsideTheRunsScope_areMismatchedOutput() throws Exception {
        freshEligibleFailure();
        RunContext c = ctx("maven");
        c.scopeClasses = new ArrayList<>(List.of("com.kpj.tests.Syn.SynOtherTest"));   // the run executed SynOtherTest, yet SYN_FLOW results appeared
        String log = run(c);
        assertTrue(log.contains("POSTRUN_COMPLETE: MISMATCHED_OUTPUT") && log.contains("SYN_FLOW"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void aScopedTestThatWroteNothing_isNeverTreatedAsAPass() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", false, "", inRun());
        RunContext c = ctx("maven");
        c.scopeClasses = new ArrayList<>(List.of("com.kpj.tests.Syn.SynFlowTest", "com.kpj.tests.Syn.SynOtherTest"));
        String log = run(c);
        assertTrue(log.contains("SYN_OTHER wrote no result during this run") && log.contains("NOT treated as a pass"), log);
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FAILURES"), "the one scoped test that did write passed: " + log);
        // and when NONE of the scoped tests wrote anything, it is a rejection, not a success
        Files.delete(out.resolve("aggregate-data").resolve("Syn.tsv"));
        writeTsv(out, "Unrelated.tsv", "SYN_TA", false, "", inRun());   // unrelated fresh file keeps the folder "fresh"
        RunContext c2 = ctx("maven2");
        c2.scopeClasses = new ArrayList<>(List.of("com.kpj.tests.Syn.SynFlowTest"));
        String log2 = run(c2);
        assertTrue(log2.contains("MISMATCHED_OUTPUT"), log2);
    }

    @Test
    void anUnknownRunStart_cannotProveFreshness_soNothingIsAnalysed() throws Exception {
        freshEligibleFailure();
        RunContext c = ctx("batch-module-ip-devhis");
        c.startedAtMillis = 0;
        String log = run(c);
        assertTrue(log.contains("POSTRUN_COMPLETE: NO_FRESH_RESULTS") && log.contains("start time is unknown"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void aTestWhoseRowsAlsoSitInAnOlderResultFile_isRejected_notMixedWithItsStaleRows() throws Exception {
        freshEligibleFailure();
        writeTsv(out, "OldCopy.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 3_600_000));   // same test, an earlier run
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("REJECTED") && log.contains("also exist in an older result file"), log);
        assertFalse(hasPending(), "mixed-run evidence never becomes a proposal");
        assertFalse(analysisRan());
    }

    // ================================================================== exit status of the test JVM(s)

    @Test
    void aNonZeroExitWithNoFreshResult_isTestRunFailed_andOldResultsAreNotAnalysed() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 3_600_000));
        RunContext c = ctx("batch-module-ip-devhis");
        c.exitCodes = new ArrayList<>(List.of(1));
        String log = run(c);
        assertTrue(log.contains("POSTRUN_COMPLETE: TEST_RUN_FAILED"), log);
        assertNothingWasAnalysed(log);
    }

    @Test
    void aZeroExitWithNoFreshResult_isNotSuccess() throws Exception {
        String log = run(ctx("batch-module-ip-devhis"));   // exit 0, nothing written
        assertTrue(log.contains("NO_FRESH_RESULTS") && !log.contains("NO_FAILURES"), log);
    }

    @Test
    void aNonZeroExit_doesNotHideRealFreshResults_butIsNeverTreatedAsAPass() throws Exception {
        freshEligibleFailure();
        RunContext c = ctx("batch-module-ip-devhis");
        c.exitCodes = new ArrayList<>(List.of(0, 1, 0));   // one of three JVMs failed, the others wrote real results
        String log = run(c);
        assertTrue(log.contains("exited non-zero [0, 1, 0]"), log);
        assertTrue(log.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), "the fresh failing results are still analysed: " + log);
    }

    @Test
    void anUncapturedExitStatus_isSaidSo_andJudgedOnFreshResultsOnly() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", false, "", inRun());
        RunContext c = ctx("batch-module-ip-devhis");
        c.exitCodes = new ArrayList<>();
        String log = run(c);
        assertTrue(log.contains("exit status of the test JVM(s) was not captured"), log);
        assertTrue(log.contains("NO_FAILURES"), log);
    }

    // ================================================================== honest reporting of failure evidence

    @Test
    void whenNoPageStateEvidenceWasCaptured_theReportSaysSo_andNeverImpliesItWas() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, inRun());   // no failures/ sidecar at all (what the older jar writes)
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("detailed page-state evidence") && log.contains("for 0 of 1") && log.contains("NOT captured by the run that produced these results"), log);
        assertTrue(log.contains("recorded step text only"), log);
    }

    @Test
    void aSidecarFromAnEarlierRun_isNotAttachedToThisRunsFailure() throws Exception {
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, inRun());
        writeSidecar(out, "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 3_600_000));   // left over from an hour ago
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("for 0 of 1") && log.contains("NOT captured"), "stale evidence is not presented as this run's: " + log);
    }

    @Test
    void aFreshSidecar_isReportedAsCaptured() throws Exception {
        freshEligibleFailure();
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("for 1 of 1"), log);
        assertFalse(log.contains("NOT captured by the run"), log);
    }

    @Test
    void theJarThatRanTheTests_isDescribedFactually() throws Exception {
        freshEligibleFailure();
        RunContext c = ctx("batch-module-ip-devhis");
        c.jarPath = "C:\\somewhere\\mcp-kpj-1.0.0-shaded.jar";
        c.jarModifiedMillis = 1_700_000_000_000L;
        String log = run(c);
        assertTrue(log.contains("results produced by C:\\somewhere\\mcp-kpj-1.0.0-shaded.jar") && log.contains("judged below from the files it actually wrote"), log);
    }

    // ================================================================== one analysis per run, nesting, kill switch

    @Test
    void theSameRunTriggeredTwice_isAnalysedExactlyOnce() throws Exception {
        freshEligibleFailure();
        RunContext c = ctx("batch-module-ip-devhis");
        String first = run(c);
        assertTrue(first.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), first);
        String second = run(c);
        assertTrue(second.contains("POSTRUN_ALREADY_DONE") && !second.contains("FAILURE ANALYSIS"), second);
        assertEquals(1, files(out.resolve("self-healing").resolve("pending")).size(), "still exactly one proposal");
        try (Stream<Path> s = Files.list(out.resolve("self-healing").resolve("post-run"))) {
            assertEquals(1, s.filter(p -> p.toString().endsWith(".lock")).count(), "one run lock");
        }
    }

    @Test
    void aDifferentRunInTheSameFolder_isAnalysedOnItsOwn() throws Exception {
        freshEligibleFailure();
        RunContext first = ctx("batch-module-ip-devhis");
        run(first);
        RunContext second = ctx("batch-module-ip-devhis");
        second.startedAtMillis = first.startedAtMillis + 1;   // a new marker = a new run
        String log = run(second);
        assertFalse(log.contains("POSTRUN_ALREADY_DONE"), "a new run is not blocked by an old run's lock: " + log);
    }

    @Test
    void manyConcurrentTriggersForOneRun_yieldExactlyOneOwner() throws Exception {
        RunLedger ledger = new RunLedger(out);
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int id = i;
            fs.add(pool.submit(() -> {
                go.await();
                if (ledger.claim("maven-123", "trigger-" + id)) winners.incrementAndGet();
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(1, winners.get(), "exactly one trigger owns the run");
    }

    @Test
    void aTriggerFiredWhileAnAnalysisIsRunning_standsDown() throws Exception {
        freshEligibleFailure();
        List<String> nested = new ArrayList<>();
        SelfHealingAgent.categoryClassifierOverride = new FailureCategoryClassifier() {
            @Override
            public Result classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
                if (nested.isEmpty()) {   // while Failure Analysis is classifying, some nested runner / listener fires again
                    nested.add("active=" + PostRunCoordinator.isActive());
                    nested.add(PostRunCoordinator.triggerFor("maven", now - 10_000, List.of(0), List.of()).marker);
                }
                return super.classify(failure, loc);
            }
        };
        run(ctx("batch-module-ip-devhis"));
        assertEquals("active=true", nested.get(0));
        assertTrue(nested.get(1).startsWith("POSTRUN_SKIPPED: NESTED"), "the nested trigger stood down: " + nested);
    }

    @Test
    void theKillSwitch_stopsEverything_andSaysSo() throws Exception {
        freshEligibleFailure();
        System.setProperty("devhis.postrun", "off");
        PostRunCoordinator.Result[] r = new PostRunCoordinator.Result[1];
        String log = runCoordinator(ctx("batch-module-ip-devhis"), r);
        assertTrue(log.contains("POSTRUN_SKIPPED: DISABLED"), log);
        assertNothingWasAnalysed(log);
        assertFalse(Files.exists(out.resolve("self-healing")), "not even a run lock was written");
        System.setProperty("devhis.postrun", "OFF");
        assertTrue(PostRunCoordinator.disabled(), "case-insensitive");
        System.setProperty("devhis.postrun", "on");
        assertFalse(PostRunCoordinator.disabled());
    }

    @Test
    void withoutTheProjectSourceTree_itSkipsInsteadOfGuessing() throws Exception {
        freshEligibleFailure();
        CodeLocator.testRootOverride = tmp.resolve("no-such-source-tree");
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("POSTRUN_SKIPPED: NO_SOURCE_TREE"), log);
        assertNothingWasAnalysed(log);
    }

    // ================================================================== exact classes, never ambiguous simple names

    @Test
    void duplicateSimpleNames_areResolvedByFullyQualifiedClassName() throws Exception {
        // com.kpj.tests.SynA.Transfer and com.kpj.tests.SynB.Transfer share a simple name; only SYN_TB failed.
        writeTsv(out, "TB.tsv", "SYN_TB", true, ELIGIBLE_ACTUAL, inRun());
        writeSidecar(out, "SYN_TB", ELIGIBLE_ACTUAL, inRun());
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("(com.kpj.tests.SynB.Transfer)"), "the exact class is named: " + log);
        assertFalse(log.contains("com.kpj.tests.SynA.Transfer"), "the same-named class was never touched: " + log);
        assertTrue(log.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), log);
    }

    @Test
    void aFailingTestIdNoClassDeclares_isReported_andNothingIsGuessed() throws Exception {
        writeTsv(out, "Ghost.tsv", "SYN_GHOST", true, ELIGIBLE_ACTUAL, inRun());
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("no test class declares this testId"), log);
        assertFalse(hasPending());
    }

    // ================================================================== LLM fallback stays off

    @Test
    void theLlmFallback_isOffInTheAutomaticPath_evenWhenFullyConfigured() throws Exception {
        System.setProperty("LLM_ENABLED", "true");
        System.setProperty("LLM_ENDPOINT", "http://127.0.0.1:1/never-called");
        System.setProperty("LLM_API_KEY", "synthetic-not-a-real-key");
        assertTrue(LlmConfig.fromEnvironment().enabled, "sanity: the manual path would use the configured LLM");
        freshEligibleFailure();
        List<Boolean> llmSeenByTheAgents = new ArrayList<>();
        SelfHealingAgent.categoryClassifierOverride = new FailureCategoryClassifier() {
            @Override
            public Result classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
                llmSeenByTheAgents.add(LlmConfig.fromEnvironment().enabled);   // what the agents' own configuration lookup sees mid-analysis
                return super.classify(failure, loc);
            }
        };
        String log = run(ctx("batch-module-ip-devhis"));
        assertTrue(log.contains("LLM fallback disabled for the automatic post-run"), log);
        assertFalse(llmSeenByTheAgents.isEmpty(), "the agents ran");
        assertFalse(llmSeenByTheAgents.contains(true), "the agents' LLM configuration was off for the whole automatic analysis: " + llmSeenByTheAgents);
        assertFalse(log.contains("synthetic-not-a-real-key"), "no key is ever printed");
        assertTrue(LlmConfig.fromEnvironment().enabled, "and the manual path is unchanged afterwards");
    }

    @Test
    void theLlmFallback_needsAnExplicitDocumentedOptIn() throws Exception {
        System.setProperty("LLM_ENABLED", "true");
        System.setProperty("LLM_ENDPOINT", "http://127.0.0.1:1/never-called");
        System.setProperty("LLM_API_KEY", "synthetic-not-a-real-key");
        LlmConfig.suppressedForAutomaticPostRun = true;
        try {
            assertFalse(LlmConfig.fromEnvironment().enabled, "suppressed by default");
            System.setProperty("devhis.postrun.llm", "on");
            assertTrue(LlmConfig.fromEnvironment().enabled, "explicit opt-in re-enables it (still needs the usual LLM_* settings)");
            System.setProperty("devhis.postrun.llm", "yes please");
            assertFalse(LlmConfig.fromEnvironment().enabled, "only the exact word 'on' opts in");
        } finally {
            LlmConfig.suppressedForAutomaticPostRun = false;
        }
    }

    // ================================================================== approval stays mandatory

    @Test
    void theCoordinatorNeverCallsApplyPending_orAnyApplyPath() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/com/kpj/heal/PostRunCoordinator.java"));
        assertFalse(src.contains("applyPendingMode"), "no apply entry point is referenced");
        assertFalse(src.matches("(?s).*new String\\[\\]\\s*\\{\\s*\"apply-pending\".*"), "apply-pending is never invoked");
        assertFalse(src.contains("PatchApplier") || src.contains("BackupManager"), "no patch or backup machinery is referenced");
        assertFalse(src.contains("\"approved\""), "no approval word is ever supplied");
    }

    // ================================================================== the agents' own Maven runs cannot re-trigger the analysis

    @Test
    void everyMavenRunTheAgentsLaunch_carriesTheKillSwitch_soItCannotStartASecondAnalysis() throws Exception {
        Path report = tmp.resolve("child-env.txt");
        String java = Paths.get(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();
        String classes = Paths.get(PostRunEnvChild.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        SelfHealingAgent.mavenExecutableOverride = List.of(java, "-cp", classes, PostRunEnvChild.class.getName(), report.toString());
        try {
            assertEquals(0, SelfHealingAgent.launchMaven("-q", "test"));
        } finally {
            SelfHealingAgent.mavenExecutableOverride = null;
        }
        assertEquals("off", Files.readString(report), "the child Maven process sees DEVHIS_POSTRUN=off");
    }

    @Test
    void theTestRunItselfNeverIncludesAppointmentListTest() throws Exception {
        freshEligibleFailure();
        String log = run(ctx("batch-module-ip-devhis"));
        assertFalse(log.contains("AppointmentList"), log);
        assertNotEquals(null, log);
        Set<String> names = files(tmp).stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        assertFalse(names.stream().anyMatch(n -> n.contains("AppointmentList")), "nothing about AppointmentListTest was created: " + names);
    }
}
