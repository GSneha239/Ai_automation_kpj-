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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kpj.heal.PostRunTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WHOLE flow of the diagram, chained in one synthetic test, end to end:
 * <pre>
 *   finished run -> fresh results -> Failure Analysis -> report -> eligibility -> Self-Healing proposal -> STOP
 *   -> human approval (bound to the printed hash) -> backup -> apply -> compile -> re-run -> PASS: KEEP | FAIL: ROLLBACK -> final reports
 * </pre>
 * Only the Maven launcher is replaced by a fake that writes synthetic results; the coordinator, the agents, the proposal, the approval gate,
 * the backup, the patch, the verification and the reports are all the REAL code. No browser, no DevHIS, no real project test.
 */
class PostRunFullFlowTest {

    @TempDir
    Path tmp;

    private static String outputsBefore;

    private Path testRoot, out, page;
    private byte[] originalPage;
    private String sourcesBefore;
    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private boolean reRunPasses = true;
    private long now;

    @BeforeAll
    static void before() throws IOException { outputsBefore = outputsContentFingerprint(); }

    @AfterAll
    static void after() throws IOException {
        assertEquals(outputsBefore, outputsContentFingerprint(), "every real test-output* folder is byte-identical to before");
    }

    @BeforeEach
    void setUp() throws IOException {
        sourcesBefore = sourcesFingerprint();
        testRoot = tmp.resolve("tests");
        out = tmp.resolve("out");
        Files.createDirectories(out);
        writeAllFlows(testRoot);
        page = testRoot.resolve("src").resolve("SynFlowPage.java");
        originalPage = Files.readAllBytes(page);
        CodeLocator.testRootOverride = testRoot;
        TestClassResolver.rootOverride = testRoot;
        ImpactAnalyzer.testRootOverride = testRoot.resolve("no-dependents");
        realLauncher = SelfHealingAgent.mavenLauncher;
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) {   // the re-run of the patched test
                writeTsv(out, "Syn.tsv", "SYN_FLOW", !reRunPasses, ELIGIBLE_ACTUAL, Instant.now());
                if (!reRunPasses) writeSidecar(out, "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.now());
            }
            return 0;
        };
        now = System.currentTimeMillis();
        writeTsv(out, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 5000));
        writeSidecar(out, "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.ofEpochMilli(now - 5000));
    }

    @AfterEach
    void restore() throws IOException {
        SelfHealingAgent.mavenLauncher = realLauncher;
        SelfHealingAgent.categoryClassifierOverride = null;
        CodeLocator.testRootOverride = null;
        TestClassResolver.rootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
        assertEquals(sourcesBefore, sourcesFingerprint(), "no project source was modified");
    }

    // ------------------------------------------------------------------ the front half: finished run -> proposal -> STOP

    private PostRunCoordinator.Result proposeFromFinishedRun() throws Exception {
        RunContext c = new RunContext();
        c.entryPoint = "batch-module-ip-devhis";
        c.outDir = out.toString();
        c.startedAtMillis = now - 10_000;
        c.endedAtMillis = now;
        c.exitCodes = new ArrayList<>(List.of(0));
        PostRunCoordinator.Result[] r = new PostRunCoordinator.Result[1];
        String log = capture(() -> r[0] = PostRunCoordinator.run(c));

        // test report generated -> Failure Analysis -> Failure Analysis report -> eligibility -> proposal -> STOP
        assertTrue(log.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), log);
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("reports")), "the Failure Analysis report was written");
        assertTrue(log.contains("STOP - HUMAN APPROVAL REQUIRED"), log);
        assertEquals(1, r[0].pendingFiles.size());
        assertTrue(r[0].pendingHashes.get(0).matches("[0-9a-f]{64}"));
        assertEquals(List.of(), mavenCalls, "up to the proposal nothing is compiled or re-run");
        assertArrayEquals(originalPage, Files.readAllBytes(page), "the page object is untouched at the approval checkpoint");
        assertTrue(files(out.resolve("self-healing").resolve("backups")).isEmpty(), "no backup file yet");
        return r[0];
    }

    private String approve(PostRunCoordinator.Result r, String decision, String hash) throws Exception {
        String[] args = hash == null ? new String[]{"apply-pending", r.pendingFiles.get(0), decision}
                : new String[]{"apply-pending", r.pendingFiles.get(0), decision, hash};
        return capture(() -> SelfHealingAgent.main(args));
    }

    // ------------------------------------------------------------------ the back half

    @Test
    void approvedAndVerified_isBackedUp_applied_compiled_rerun_andKept_withFinalReports() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        String log = approve(r, "approved", r.pendingHashes.get(0));

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynFlowTest test"), mavenCalls,
                "compile, then re-run of exactly the fully qualified class, in that order");
        assertTrue(log.contains("Backup created") || Files.isDirectory(out.resolve("self-healing").resolve("backups")), "a backup was taken before the write: " + log);
        assertTrue(files(out.resolve("self-healing").resolve("backups")).size() >= 1, "the backup file exists");
        assertTrue(Files.readString(page).contains("Locationid") && !Files.readString(page).contains(LITERAL_LINE), "the approved fix was applied");
        assertTrue(log.contains("Verification passed.") && log.contains("final result = KEPT"), log);
        assertFalse(Files.exists(Paths.get(r.pendingFiles.get(0))), "the pending proposal was consumed and cannot be re-applied");
        // final reports
        assertTrue(files(out.resolve("failure-analysis")).stream().anyMatch(p -> p.toString().endsWith(".html")), "Failure Analysis reports exist");
        assertTrue(files(out.resolve("self-healing")).stream().anyMatch(p -> p.toString().endsWith(".html")), "the healing report exists");
        assertTrue(Files.isDirectory(out.resolve("unified-report")) && !files(out.resolve("unified-report")).isEmpty(), "the unified report exists");
    }

    @Test
    void approvedButTheRerunStillFails_isRolledBack_andTheOriginalIsRestoredByteForByte() throws Exception {
        reRunPasses = false;
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        String log = approve(r, "approved", r.pendingHashes.get(0));

        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynFlowTest test"), mavenCalls);
        assertTrue(log.contains("Verification failed.") && log.contains("final result = REVERTED"), log);
        assertArrayEquals(originalPage, Files.readAllBytes(page), "ROLLBACK: the original file is back, byte for byte");
        assertTrue(files(out.resolve("self-healing").resolve("backups")).size() >= 1, "the backup it restored from still exists");
        assertTrue(files(out).stream().anyMatch(p -> p.toString().replace('\\', '/').contains("unified-report")), "final reports are written for a rollback too");
    }

    @Test
    void anApprovalQuotingTheWrongHash_isRefused_andNothingIsChanged() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        String wrong = "0".repeat(64);
        String log = approve(r, "approved", wrong);
        assertTrue(log.contains("REFUSED"), log);
        assertEquals(List.of(), mavenCalls, "no compile, no re-run");
        assertArrayEquals(originalPage, Files.readAllBytes(page));
        assertTrue(files(out.resolve("self-healing").resolve("backups")).isEmpty(), "no backup FILE was taken (the report scaffold may create an empty backups folder)");
        assertTrue(Files.exists(Paths.get(r.pendingFiles.get(0))), "the proposal is still pending, waiting for a correct approval");
    }

    @Test
    void anApprovalWithNoHash_isRefused_andNothingIsChanged() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        String log = approve(r, "approved", null);
        assertTrue(log.contains("REFUSED"), log);
        assertEquals(List.of(), mavenCalls);
        assertArrayEquals(originalPage, Files.readAllBytes(page));
    }

    @Test
    void aDeclinedProposal_changesNothing_andIsConsumed() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        approve(r, "declined", null);
        assertEquals(List.of(), mavenCalls);
        assertArrayEquals(originalPage, Files.readAllBytes(page));
        assertFalse(Files.exists(Paths.get(r.pendingFiles.get(0))), "the declined proposal can never be re-applied later");
    }

    @Test
    void aChangedSourceBetweenProposalAndApproval_isRefused() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        Files.writeString(page, Files.readString(page) + "\n// edited by someone after the proposal\n");
        byte[] edited = Files.readAllBytes(page);
        String log = approve(r, "approved", r.pendingHashes.get(0));
        assertTrue(log.contains("REFUSED"), log);
        assertEquals(List.of(), mavenCalls);
        assertArrayEquals(edited, Files.readAllBytes(page), "the changed file was not touched by the refused approval");
    }

    @Test
    void theWholeFlowNeverLaunchesAnythingElse() throws Exception {
        PostRunCoordinator.Result r = proposeFromFinishedRun();
        approve(r, "approved", r.pendingHashes.get(0));
        for (String call : mavenCalls) {
            assertTrue(call.equals("-q test-compile") || call.equals("-q -Dtest=com.kpj.tests.Syn.SynFlowTest test"), "only the named synthetic test was run: " + call);
            assertFalse(call.contains("AppointmentList"));
        }
        try (Stream<Path> s = Files.list(tmp)) {
            assertFalse(s.map(p -> p.getFileName().toString()).collect(Collectors.toList()).contains("test-output"), "nothing was written to a default test-output folder");
        }
    }
}
