package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of the orchestrator's real behaviour around the test run: exit-code handling, fresh-result detection,
 * output-folder consistency, the stop at the human-approval checkpoint, the printed resume command, and refusal of missing, stale or
 * tampered approvals. The test run is a fake launcher that writes (or does not write) synthetic results into temp folders. No Maven,
 * browser or DevHIS is started; every test also proves the project's own sources and its {@code test-output*} folders were not touched.
 */
class OrchestrationFlowTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_FLOW";
    private static final String FQCN = "com.kpj.tests.Syn.SynFlowTest";
    private static final String SIMPLE = "SynFlowTest";
    private static final String STEP = "Select flow location";
    private static final String LITERAL_LINE = "        select('staffScheduleInfo.LocationId');";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private enum Run { FAILING, PASSING, ELSEWHERE, NOTHING }

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private Run run = Run.FAILING;
    private int exitCode = 0;
    private boolean launchFails = false;
    private Path out, elsewhere, page;
    private String sourcesBefore;
    private String projectOutputsBefore;
    private List<String> cwdEntriesBefore;
    private String differentActual = ACTUAL;

    @BeforeEach
    void setUp() throws IOException {
        sourcesBefore = projectSourceFingerprint();
        projectOutputsBefore = projectOutputFingerprint();
        cwdEntriesBefore = cwdEntries();

        realLauncher = SelfHealingAgent.mavenLauncher;
        out = tmp.resolve("custom-out");
        elsewhere = tmp.resolve("elsewhere-out");
        page = tmp.resolve("src").resolve("SynFlowPage.java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package src;\npublic class SynFlowPage {\n    public SynFlowPage(Object page) { }\n"
                + "    public boolean pick() {\n" + LITERAL_LINE + "\n        return true;\n    }\n}\n");
        Path test = tmp.resolve("com/kpj/tests/Syn/" + SIMPLE + ".java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.kpj.tests.Syn;\nimport src.SynFlowPage;\npublic class " + SIMPLE + " {\n"
                + "    public " + SIMPLE + "() { super(\"" + TEST_ID + "\"); }\n    protected void body() {\n"
                + "        SynFlowPage p = new SynFlowPage(page);\n        p.pick();\n"
                + "        step(\"" + STEP + "\", \"desc\", \"expected\", \"actual\", \"FAIL\");\n    }\n}\n");
        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");
        CodeLocator.testRootOverride = tmp;

        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (launchFails) throw new IOException("synthetic: mvn could not be started");
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) writeResults(run == Run.ELSEWHERE ? elsewhere : out, run);
            return exitCode;
        };
    }

    @AfterEach
    void restoreAndProveNothingRealWasTouched() throws IOException {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        CodeLocator.testRootOverride = null;
        assertEquals(sourcesBefore, projectSourceFingerprint(), "no project test or page-object source was modified");
        assertEquals(projectOutputsBefore, projectOutputFingerprint(), "the project's test-output folders were not touched");
        assertEquals(cwdEntriesBefore, cwdEntries(), "nothing was created in the working directory");
        for (String call : mavenCalls) {
            assertFalse(call.contains("AppointmentList"), "AppointmentListTest was never launched: " + call);
            assertTrue(call.equals("-q -Dtest=" + FQCN + " test") || call.equals("-q test-compile"), "only the synthetic test was launched: " + call);
        }
    }

    // ------------------------------------------------------------------ synthetic results

    private void writeResults(Path dir, Run mode) throws IOException {
        if (mode == Run.NOTHING) return;
        Files.createDirectories(dir.resolve("aggregate-data"));
        boolean passes = mode == Run.PASSING;
        Files.writeString(dir.resolve("aggregate-data").resolve("SYN_FLOW.tsv"),
                row("PASS", "Login", "Authenticated", "Logged in")
                        + (passes ? row("PASS", STEP, "element found", "element found") : row("FAIL", STEP, "element found", differentActual)));
        if (!passes) {
            Files.createDirectories(dir.resolve("failures"));
            Files.writeString(dir.resolve("failures").resolve(TEST_ID + "_1.txt"),
                    "testId=" + TEST_ID + "\npage=Synthetic page\nstep=" + STEP + "\ndesc=d\nexpected=element found\nactual=" + differentActual
                            + "\nurl=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff\n");
        }
    }

    private static String row(String status, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, expected, actual, "") + "\n";
    }

    /** An OLD run's results already sitting in the output folder (an hour old). */
    private void seedStaleFailingResults() throws IOException {
        writeResults(out, Run.FAILING);
        FileTime old = FileTime.from(Instant.now().minusSeconds(3600));
        try (Stream<Path> s = Files.walk(out)) {
            for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) Files.setLastModifiedTime(p, old);
        }
    }

    // ------------------------------------------------------------------ running the orchestrator

    private interface Action { void run() throws Exception; }

    private String capture(Action a) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try { a.run(); } finally { System.setOut(old); }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    private String orchestrate(String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of(FQCN, out.toString()));
        args.addAll(Arrays.asList(extra));
        return capture(() -> HealingWorkflowOrchestrator.main(args.toArray(new String[0])));
    }

    private Path onlyPending() throws IOException {
        Path dir = out.resolve("self-healing").resolve("pending");
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> l = s.filter(p -> p.toString().endsWith(".properties")).collect(Collectors.toList());
            assertEquals(1, l.size(), "exactly one proposal: " + l);
            return l.get(0);
        }
    }

    private boolean hasPending() throws IOException {
        Path dir = out.resolve("self-healing").resolve("pending");
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.list(dir)) { return s.findAny().isPresent(); }
    }

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private void assertTemporarySourceUntouched() throws IOException {
        assertTrue(Files.readString(page).contains(LITERAL_LINE) && !Files.readString(page).contains("Locationid"), "the (temporary) page object is unpatched");
    }

    // ------------------------------------------------------------------ project-level proof (read-only fingerprints)

    private static String projectSourceFingerprint() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String root : new String[]{"src/test/java/com/kpj/tests", "src/test/java/com/kpj/pages", "src/main/java/com/kpj/core"}) {
            Path r = Paths.get(root);
            if (!Files.isDirectory(r)) continue;
            try (Stream<Path> s = Files.walk(r)) {
                for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    sb.append(p).append('=').append(PendingHeal.hashFile(p)).append('\n');
                }
            }
        }
        return PendingHeal.sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String projectOutputFingerprint() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String root : new String[]{"test-output", "test-output-demo"}) {
            Path r = Paths.get(root);
            if (!Files.isDirectory(r)) continue;
            try (Stream<Path> s = Files.walk(r)) {
                for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    sb.append(p).append('@').append(Files.getLastModifiedTime(p).toMillis()).append('#').append(Files.size(p)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static List<String> cwdEntries() throws IOException {
        try (Stream<Path> s = Files.list(Paths.get("."))) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    // ================================================================== exit code and fresh-result detection

    @Test
    void aNonZeroMavenExit_stopsTheWorkflow_andNeverAnalyzesTheOldResults() throws Exception {
        seedStaleFailingResults();
        exitCode = 1;
        run = Run.NOTHING;
        String log = orchestrate();
        assertTrue(log.contains("Maven exited with code 1"), log);
        assertTrue(log.contains("WORKFLOW_COMPLETE: TEST_RUN_FAILED"), log);
        assertFalse(Files.exists(out.resolve("failure-analysis")), "the old failing results were not analyzed");
        assertFalse(hasPending());
        assertFalse(log.contains("FAILURE ANALYSIS"), log);
    }

    @Test
    void aNonZeroExit_isNotRescuedByResultsThatHappenToBeFresh() throws Exception {
        exitCode = 2;   // the run wrote results, but the build reported failure
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: TEST_RUN_FAILED"), log);
        assertFalse(hasPending());
    }

    @Test
    void aMavenThatCannotBeLaunched_stopsTheWorkflow() throws Exception {
        seedStaleFailingResults();
        launchFails = true;
        String log = orchestrate();
        assertTrue(log.contains("Maven could not be launched") && log.contains("WORKFLOW_COMPLETE: TEST_RUN_FAILED"), log);
        assertFalse(Files.exists(out.resolve("failure-analysis")));
    }

    @Test
    void resultsWrittenToADifferentFolder_areNotFresh_andTheOldOnesAreNotAnalyzed() throws Exception {
        seedStaleFailingResults();
        run = Run.ELSEWHERE;   // DEVHIS_OUTDIR pointing somewhere else than this command's output folder
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FRESH_RESULTS"), log);
        assertTrue(log.contains(out.toString()) && log.contains("DEVHIS_OUTDIR"), "it names the folder and the setting to align: " + log);
        assertTrue(Files.isRegularFile(elsewhere.resolve("aggregate-data").resolve("SYN_FLOW.tsv")), "sanity: the run did write somewhere");
        assertFalse(Files.exists(out.resolve("failure-analysis")), "stale results in the orchestrator's folder were not analyzed");
        assertFalse(hasPending());
    }

    @Test
    void aRunThatWritesNothing_isNoFreshResults() throws Exception {
        run = Run.NOTHING;
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FRESH_RESULTS"), log);
    }

    @Test
    void freshResults_replaceOldOnes_soAnOldFailureIsNotReportedForAPassingRun() throws Exception {
        seedStaleFailingResults();
        run = Run.PASSING;
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FAILURES"), log);
        assertFalse(Files.exists(out.resolve("failure-analysis")));
    }

    @Test
    void skipRun_isTheOnlyWayToUseRecordedResults_andLaunchesNothing() throws Exception {
        seedStaleFailingResults();
        String log = orchestrate("--skip-run");
        assertEquals(List.of(), mavenCalls);
        assertTrue(log.contains("skipped (--skip-run)"), log);
        assertTrue(hasPending(), "the recorded results were analyzed because the user explicitly asked for that");
    }

    // ================================================================== output-folder consistency

    @Test
    void everyArtifact_landsInTheOrchestratorsOutputFolder_andNowhereElse() throws Exception {
        String log = orchestrate();
        for (String rel : new String[]{"aggregate-data", "failures", "failure-analysis/reports", "failure-analysis/summary", "failure-analysis/index",
                "self-healing/pending", "self-healing/session", "unified-report"}) {
            assertTrue(Files.isDirectory(out.resolve(rel)), rel + " is under the output folder");
        }
        // every report/proposal path the workflow printed is inside that folder
        Matcher m = Pattern.compile("([A-Za-z]:[\\\\/][^\\s\"']*(?:failure-analysis|self-healing|unified-report)[^\\s\"']*|/[^\\s\"']*(?:failure-analysis|self-healing|unified-report)[^\\s\"']*)").matcher(log);
        int seen = 0;
        while (m.find()) {
            seen++;
            assertTrue(Paths.get(m.group(1)).toAbsolutePath().normalize().startsWith(tmp.toAbsolutePath().normalize()), "printed path outside the output folder: " + m.group(1));
        }
        assertTrue(seen > 0, "paths were printed");
        try (Stream<Path> s = Files.walk(tmp)) {
            assertEquals(List.of(), s.filter(Files::isRegularFile).map(Path::toString)
                    .filter(p -> p.contains("elsewhere-out")).collect(Collectors.toList()), "nothing went to the other folder");
        }
    }

    // ================================================================== the human-approval checkpoint

    private String resumeCommandLine(String log) {
        for (String line : log.split("\\R")) if (line.startsWith("To approve THIS exact proposal: ")) return line;
        throw new AssertionError("no resume command was printed:\n" + log);
    }

    @Test
    void theWorkflow_stopsAtTheApprovalCheckpoint_andPrintsTheExactResumeCommand() throws Exception {
        String log = orchestrate();
        assertEquals(List.of("-q -Dtest=" + FQCN + " test"), mavenCalls, "one test run - no compile, no re-run, nothing applied");
        assertTemporarySourceUntouched();
        assertFalse(Files.exists(out.resolve("self-healing").resolve("backups")) && Files.walk(out.resolve("self-healing").resolve("backups")).anyMatch(Files::isRegularFile));
        assertFalse(log.contains("Patch applied"), log);

        Path pending = onlyPending();
        PendingHeal p = PendingHeal.load(pending);
        String cmd = resumeCommandLine(log);
        assertEquals("To approve THIS exact proposal: apply-pending \"" + pending + "\" approved " + p.proposalHash, cmd);
        assertTrue(log.contains("PENDING_HEAL_FILE: " + pending) && log.contains("PROPOSAL_HASH: " + p.proposalHash));
        assertEquals(64, p.proposalHash.length());
    }

    @Test
    void theExactPrintedResumeCommand_isAcceptedAndCompletesTheHealing_onTheTemporaryFixture() throws Exception {
        String cmd = resumeCommandLine(orchestrate());
        Matcher m = Pattern.compile("apply-pending \"([^\"]+)\" approved ([0-9a-f]{64})").matcher(cmd);
        assertTrue(m.find(), cmd);
        run = Run.PASSING;   // the post-patch re-run of the synthetic test passes
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", m.group(1), "approved", m.group(2)}));
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
        assertTrue(Files.readString(page).contains("Locationid"), "the temporary page object was patched");
        assertEquals(List.of("-q -Dtest=" + FQCN + " test", "-q test-compile", "-q -Dtest=" + FQCN + " test"), mavenCalls,
                "resume = compile then re-run of the same test, nothing else");
        assertFalse(hasPending(), "the proposal is consumed");
    }

    @Test
    void anApprovalWithoutTheHash_isRefused_andNothingChanges() throws Exception {
        orchestrate();
        Path pending = onlyPending();
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pending.toString(), "approved"}));
        assertTrue(lastAuditRow().get("reason").startsWith("APPLY REFUSED - "));
        assertTemporarySourceUntouched();
        assertEquals(1, mavenCalls.size(), "no compile, no re-run");
        assertTrue(Files.exists(pending), "the proposal stays pending");
    }

    @Test
    void anApprovalQuotingAnotherHash_isRefused() throws Exception {
        orchestrate();
        Path pending = onlyPending();
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pending.toString(), "approved", "ab".repeat(32)}));
        assertTrue(lastAuditRow().get("reason").contains("different proposal's hash"), lastAuditRow().get("reason"));
        assertTemporarySourceUntouched();
        assertEquals(1, mavenCalls.size());
    }

    @Test
    void aTamperedPendingFile_isRefusedEvenWithItsOwnHash() throws Exception {
        orchestrate();
        Path pending = onlyPending();
        PendingHeal p = PendingHeal.load(pending);
        String hash = p.proposalHash;
        p.newSnippet = p.newSnippet + " // injected";
        p.save(pending);   // content changed, hash left as it was
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pending.toString(), "approved", hash}));
        assertTrue(lastAuditRow().get("reason").contains("modified after it was created"), lastAuditRow().get("reason"));
        assertTemporarySourceUntouched();
        assertEquals(1, mavenCalls.size());
    }

    @Test
    void aStaleProposal_theEvidenceChangedAfterwards_isRefused() throws Exception {
        orchestrate();
        Path pending = onlyPending();
        String hash = PendingHeal.load(pending).proposalHash;
        differentActual = "FAILED: a different failure than the one the proposal was based on";
        writeResults(out, Run.FAILING);   // a later run recorded something else
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pending.toString(), "approved", hash}));
        assertTrue(lastAuditRow().get("reason").contains("stale evidence"), lastAuditRow().get("reason"));
        assertTemporarySourceUntouched();
        assertEquals(1, mavenCalls.size());
    }

    @Test
    void aStaleProposal_theSourceChangedAfterwards_isRefused() throws Exception {
        orchestrate();
        Path pending = onlyPending();
        String hash = PendingHeal.load(pending).proposalHash;
        String edited = Files.readString(page) + "// edited after the proposal\n";
        Files.writeString(page, edited);
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pending.toString(), "approved", hash}));
        assertTrue(lastAuditRow().get("reason").contains("has changed since the proposal was created"), lastAuditRow().get("reason"));
        assertEquals(edited, Files.readString(page), "the file is exactly as the last editor left it");
        assertEquals(1, mavenCalls.size());
    }
}
