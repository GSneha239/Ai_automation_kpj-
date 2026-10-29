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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests of per-step eligibility: the step {@code propose} selects must itself be eligible according to
 * Failure Analysis (and still pass every existing self-healing check) - another step of the same test being eligible is never
 * enough. Also covers {@link HealingWorkflowOrchestrator} and {@code SelfHealingAgent propose} end to end.
 *
 * <p>Everything is a temp fixture: a synthetic multi-step test and Page Object that the real {@link CodeLocator} resolves, the
 * failure results and DOM captures the framework would have written, and a fake {@link SelfHealingAgent#mavenLauncher} standing
 * in for the "test run". No Maven, browser, DevHIS or real project file is touched, and nothing is ever applied.</p>
 *
 * <p>A step has <i>evidence</i> when it has a DOM capture whose live ng-models include a near-match for the stale literal in its
 * Page Object method - then the real Failure Analysis says "eligible" and the real PatchProposer offers a fix. Without a capture
 * both agree it is not eligible. To simulate Failure Analysis disagreeing with the proposer for one step, a scripted subclass of
 * {@link FailureCategoryClassifier} is installed through the {@link SelfHealingAgent#categoryClassifierOverride} seam.</p>
 */
class ProposeEligibilityTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_MULTI";
    private static final String FQCN = "com.kpj.tests.Syn.SynMultiTest";
    private static final String SIMPLE = "SynMultiTest";
    private static final String LITERAL_LINE = "        select('staffScheduleInfo.LocationId');";

    private static final String S1 = "Select first location";
    private static final String S2 = "Select second location";

    /** One failing step of the synthetic test. */
    private static final class Step {
        final String name, method;
        final boolean evidence;
        Step(String name, String method, boolean evidence) { this.name = name; this.method = method; this.evidence = evidence; }
    }

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
    private Path out, page;
    private List<Step> steps;
    private boolean testRunPasses = false;

    @BeforeEach
    void setUp() {
        realLauncher = SelfHealingAgent.mavenLauncher;
        out = tmp.resolve("out");
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) writeRun();   // the fake "test run"
            return 0;
        };
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        SelfHealingAgent.categoryClassifierOverride = null;
        ImpactAnalyzer.testRootOverride = null;
        CodeLocator.testRootOverride = null;
    }

    // ------------------------------------------------------------------ fixture

    private void fixture(Step... s) throws IOException {
        steps = List.of(s);
        page = tmp.resolve("src").resolve("SynMultiPage.java");
        Files.createDirectories(page.getParent());
        StringBuilder p = new StringBuilder("package src;\npublic class SynMultiPage {\n    public SynMultiPage(Object page) { }\n");
        for (Step st : steps) p.append("    public boolean ").append(st.method).append("() {\n").append(LITERAL_LINE).append("\n        return true;\n    }\n");
        Files.writeString(page, p.append("}\n").toString());

        StringBuilder t = new StringBuilder("package com.kpj.tests.Syn;\nimport src.SynMultiPage;\npublic class " + SIMPLE + " {\n"
                + "    public " + SIMPLE + "() { super(\"" + TEST_ID + "\"); }\n    protected void body() {\n"
                + "        SynMultiPage p = new SynMultiPage(page);\n");
        for (Step st : steps) {
            t.append("        p.").append(st.method).append("();\n")
             .append("        step(\"").append(st.name).append("\", \"desc\", \"expected\", \"actual\", \"FAIL\");\n");
        }
        Path test = tmp.resolve("com/kpj/tests/Syn/" + SIMPLE + ".java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, t.append("    }\n}\n").toString());

        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");
        CodeLocator.testRootOverride = tmp;
    }

    /** What a run of the test leaves behind: the result rows plus a DOM capture for each step that has evidence. */
    private void writeRun() throws IOException {
        Files.createDirectories(out.resolve("aggregate-data"));
        StringBuilder tsv = new StringBuilder(row("PASS", "Login", "Authenticated", "Logged in"));
        for (Step st : steps) {
            tsv.append(testRunPasses ? row("PASS", st.name, "element found", "element found")
                    : row("FAIL", st.name, "element found", "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'"));
        }
        Files.writeString(out.resolve("aggregate-data").resolve("SYN_MULTI.tsv"), tsv.toString());

        Path failures = out.resolve("failures");
        Files.createDirectories(failures);
        int n = 0;
        for (Step st : steps) {
            n++;
            if (!st.evidence || testRunPasses) continue;
            Files.writeString(failures.resolve(TEST_ID + "_" + n + ".txt"),
                    "testId=" + TEST_ID + "\npage=Synthetic page\nstep=" + st.name + "\ndesc=d\nexpected=element found\n"
                            + "actual=FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'\n"
                            + "url=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff\n");
        }
    }

    private static String row(String status, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, expected, actual, "") + "\n";
    }

    // ------------------------------------------------------------------ scripted Failure Analysis verdicts

    /** The real classifier, except that named steps answer from a script: one verdict per successive call (the last repeats). */
    private static final class ScriptedFa extends FailureCategoryClassifier {
        final Map<String, List<Boolean>> script = new HashMap<>();
        final Map<String, Integer> calls = new HashMap<>();

        ScriptedFa on(String step, Boolean... verdicts) { script.put(step, Arrays.asList(verdicts)); return this; }

        @Override
        public Result classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
            Result real = super.classify(failure, loc);
            List<Boolean> s = script.get(failure.step);
            if (s == null) return real;
            int n = calls.merge(failure.step, 1, Integer::sum);
            boolean v = s.get(Math.min(n, s.size()) - 1);
            return new Result(real.category, real.rootCause, real.evidenceAvailable, real.recommendedOwner, real.severity,
                    real.suggestedAction, v, v ? "scripted: eligible" : "scripted: not eligible", real.candidates);
        }
    }

    // ------------------------------------------------------------------ helpers

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

    private String propose() throws Exception {
        return capture(() -> SelfHealingAgent.main(new String[]{"propose", FQCN, out.toString()}));
    }

    private String orchestrate(String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of(FQCN, out.toString()));
        args.addAll(Arrays.asList(extra));
        return capture(() -> HealingWorkflowOrchestrator.main(args.toArray(new String[0])));
    }

    private List<Path> pendingFiles() throws IOException {
        Path dir = out.resolve("self-healing").resolve("pending");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".properties")).sorted().collect(Collectors.toList()); }
    }

    private PendingHeal onlyPending() throws IOException {
        List<Path> files = pendingFiles();
        assertEquals(1, files.size(), "exactly one proposal expected: " + files);
        return PendingHeal.load(files.get(0));
    }

    private List<Map<String, String>> auditRows() throws IOException {
        Path f = out.resolve("failures").resolve("healing-history.tsv");
        List<Map<String, String>> rows = new ArrayList<>();
        if (!Files.isRegularFile(f)) return rows;
        List<String> lines = Files.readAllLines(f);
        String[] header = lines.get(0).split("\t", -1);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split("\t", -1);
            Map<String, String> m = new HashMap<>();
            for (int k = 0; k < header.length; k++) m.put(header[k], k < c.length ? c[k] : "");
            rows.add(m);
        }
        return rows;
    }

    private Map<String, String> auditFor(String step) throws IOException {
        List<Map<String, String>> rows = auditRows();
        return rows.stream().filter(r -> step.equals(r.get("step"))).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no audit row for step \"" + step + "\": " + rows));
    }

    private void assertSourceUntouched() throws IOException {
        String src = Files.readString(page);
        for (Step st : steps) assertTrue(src.contains(LITERAL_LINE), "page object unchanged");
        assertFalse(src.contains("Locationid"), "no patch was written");
        assertFalse(Files.exists(out.resolve("self-healing").resolve("backups"))
                && Files.walk(out.resolve("self-healing").resolve("backups")).anyMatch(Files::isRegularFile), "no backup was taken");
    }

    // ================================================================== propose: the SELECTED step must itself be eligible

    @Test
    void ineligibleFirst_eligibleSecond_proposesOnlyTheEligibleStep() throws Exception {
        fixture(new Step(S1, "locationOne", false), new Step(S2, "locationTwo", true));   // S1 has no DOM evidence at all
        writeRun();
        String log = propose();
        PendingHeal p = onlyPending();
        assertEquals(S2, p.step, "the proposal is for the eligible step, not the first failing one");
        assertTrue(log.contains("PENDING_HEAL_FILE"));
        assertEquals("NEEDS_TRIAGE", auditFor(S1).get("finalStatus"));
        assertSourceUntouched();
    }

    @Test
    void whenFailureAnalysisAloneRejectsTheFirstStep_itIsNeverProposed_evenThoughTheProposerHasAFixForIt() throws Exception {
        // BOTH steps have real evidence, so the proposer has a fix for each - but Failure Analysis rules out S1 only
        fixture(new Step(S1, "locationOne", true), new Step(S2, "locationTwo", true));
        writeRun();
        SelfHealingAgent.categoryClassifierOverride = new ScriptedFa().on(S1, false);
        String log = propose();
        assertEquals(S2, onlyPending().step, "S1 is skipped although a deterministic fix exists for it");
        assertTrue(log.contains("Failure Analysis marks THIS step not eligible"), log);
        Map<String, String> s1 = auditFor(S1);
        assertEquals("NEEDS_TRIAGE", s1.get("finalStatus"));
        assertTrue(s1.get("reason").contains("Failure Analysis marks this step not eligible"), s1.get("reason"));
        assertSourceUntouched();
    }

    @Test
    void allStepsIneligible_noProposal() throws Exception {
        fixture(new Step(S1, "locationOne", false), new Step(S2, "locationTwo", false));
        writeRun();
        String log = propose();
        assertTrue(log.contains("NO_PENDING_PROPOSAL"), log);
        assertEquals(List.of(), pendingFiles());
        assertSourceUntouched();
    }

    @Test
    void allStepsRuledOutByFailureAnalysis_noProposal_evenWithFixesAvailable() throws Exception {
        fixture(new Step(S1, "locationOne", true), new Step(S2, "locationTwo", true));
        writeRun();
        SelfHealingAgent.categoryClassifierOverride = new ScriptedFa().on(S1, false).on(S2, false);
        String log = propose();
        assertTrue(log.contains("NO_PENDING_PROPOSAL"), log);
        assertEquals(List.of(), pendingFiles());
        assertEquals("NEEDS_TRIAGE", auditFor(S1).get("finalStatus"));
        assertEquals("NEEDS_TRIAGE", auditFor(S2).get("finalStatus"));
        assertSourceUntouched();
    }

    @Test
    void aStepEligibleInBothSystems_isProposed_andTheProposalIsSealed() throws Exception {
        fixture(new Step(S1, "locationOne", true), new Step(S2, "locationTwo", true));
        writeRun();
        // Failure Analysis (the real classifier) independently agrees that S1 is eligible
        FailureRecord s1 = new FailureScanner(out.toString()).scan(TEST_ID).stream().filter(f -> f.step.equals(S1)).findFirst().orElseThrow();
        FailureCategoryClassifier.Result fa = new FailureCategoryClassifier().classify(s1, new CodeLocator().locate(FQCN, S1));
        assertTrue(fa.selfHealingEligible, fa.eligibilityReason);
        assertEquals(FailureClassifier.Category.CANDIDATE_FOR_AUTO_HEAL,
                new FailureClassifier().classify(s1, new CodeLocator().locate(FQCN, S1)).category);

        String log = propose();
        PendingHeal p = onlyPending();
        assertEquals(S1, p.step, "the first eligible step is the one proposed");
        assertTrue(p.structuralProblems().isEmpty(), "" + p.structuralProblems());
        assertEquals(p.proposalHash, p.computeProposalHash());
        assertEquals(PendingHeal.hashFile(page), p.sourceSha256, "bound to the exact source file");
        assertTrue(log.contains("PROPOSAL_HASH: " + p.proposalHash), log);
        assertSourceUntouched();
    }

    @Test
    void eligibilityThatChangesBetweenAnalysisAndProposal_isHonouredAtProposalTime() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        writeRun();
        // eligible when the orchestrator reads Failure Analysis, no longer eligible when the proposer checks the same step
        ScriptedFa fa = new ScriptedFa().on(S1, true, false);
        SelfHealingAgent.categoryClassifierOverride = fa;
        String log = orchestrate("--skip-run");
        assertTrue(log.contains("Failure Analysis: 1 of 1 failed step(s) are Self-Healing Eligible"), log);
        assertTrue(log.contains("NO_PENDING_PROPOSAL"), log);
        assertEquals(List.of(), pendingFiles(), "the later, authoritative verdict wins - nothing is proposed");
        assertEquals(2, (int) fa.calls.get(S1), "once for the orchestrator's gate, once for the proposal");
        assertSourceUntouched();
    }

    @Test
    void aStepWhoseEligibilityChangesButAnotherStaysEligible_theOtherOneIsProposed() throws Exception {
        fixture(new Step(S1, "locationOne", true), new Step(S2, "locationTwo", true));
        writeRun();
        SelfHealingAgent.categoryClassifierOverride = new ScriptedFa().on(S1, true, false);
        orchestrate("--skip-run");
        assertEquals(S2, onlyPending().step);
    }

    @Test
    void anApprovalIsRefused_ifFailureAnalysisStopsMarkingTheStepEligible_afterTheProposal() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        writeRun();
        propose();
        Path pendingFile = pendingFiles().get(0);
        PendingHeal p = PendingHeal.load(pendingFile);

        SelfHealingAgent.categoryClassifierOverride = new ScriptedFa().on(S1, false);
        capture(() -> SelfHealingAgent.main(new String[]{"apply-pending", pendingFile.toString(), "approved", p.proposalHash}));
        Map<String, String> r = auditFor(S1);
        assertEquals("NOT_APPLIED", r.get("finalStatus"));
        assertTrue(r.get("reason").contains("Failure Analysis no longer marks this step eligible"), r.get("reason"));
        assertSourceUntouched();
        assertTrue(mavenCalls.isEmpty(), "no compile or re-run for a refused approval");
    }

    // ================================================================== the orchestrator end to end (fake test run)

    @Test
    void orchestrator_runsTheCompleteChain_andStopsAtTheProposal() throws Exception {
        fixture(new Step(S1, "locationOne", false), new Step(S2, "locationTwo", true));
        String log = orchestrate();   // no --skip-run: the test is "run" through the fake launcher

        assertEquals(List.of("-q -Dtest=" + FQCN + " test"), mavenCalls, "exactly one test run, of the named class, and nothing else");
        assertTrue(log.indexOf("[1/4] TEST EXECUTION") < log.indexOf("[2/4] FAILURE DETECTION")
                && log.indexOf("[2/4] FAILURE DETECTION") < log.indexOf("[3/4] FAILURE ANALYSIS")
                && log.indexOf("[3/4] FAILURE ANALYSIS") < log.indexOf("[4/4] SELF-HEALING TRIGGER"), "stages run in order");
        assertTrue(log.contains("Failure Analysis: 1 of 2 failed step(s) are Self-Healing Eligible: [" + S2 + "]"), log);

        // test report evidence -> Failure Analysis report -> proposal, all on disk
        assertTrue(Files.isRegularFile(out.resolve("aggregate-data").resolve("SYN_MULTI.tsv")));
        try (Stream<Path> s = Files.walk(out.resolve("failure-analysis").resolve("reports"))) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().endsWith("_FailureAnalysisReport.html")), "Failure Analysis report written");
        }
        PendingHeal p = onlyPending();
        assertEquals(S2, p.step);

        // it stops at the human-approval pause: nothing applied, no backup, no compile, no re-run
        assertSourceUntouched();
        assertTrue(log.contains("PENDING_HEAL_FILE") && log.contains("PROPOSAL_HASH: " + p.proposalHash));
        assertFalse(log.contains("Backup created"), "the orchestrator never applies");
        try (Stream<Path> s = Files.list(out.resolve("unified-report"))) {
            String unified = Files.readString(s.sorted().reduce((a, b) -> b).orElseThrow());
            assertTrue(unified.contains("PROPOSED, awaiting approval"), "the unified report shows a pending proposal");
        }
    }

    @Test
    void orchestrator_stopsAtFailureAnalysis_whenNoStepIsEligible_andNeverCallsPropose() throws Exception {
        fixture(new Step(S1, "locationOne", false), new Step(S2, "locationTwo", false));
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: FAILURE_ANALYSIS_ONLY"), log);
        assertFalse(log.contains("SelfHealingAgent (propose)"), "propose was not invoked");
        assertFalse(Files.exists(out.resolve("self-healing").resolve("pending")));
        assertFalse(Files.exists(out.resolve("self-healing").resolve("session")), "propose never ran, so no session was started");
        assertTrue(Files.isDirectory(out.resolve("failure-analysis")), "but the Failure Analysis report exists");
        assertSourceUntouched();
    }

    @Test
    void orchestrator_reportsNoFailures_whenTheTestPasses() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        testRunPasses = true;
        String log = orchestrate();
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FAILURES"), log);
        assertFalse(Files.exists(out.resolve("failure-analysis")), "nothing to analyze, so no report");
        assertEquals(List.of(), pendingFiles());
    }

    @Test
    void orchestrator_skipRunUsesTheRecordedResultsWithoutLaunchingMaven() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        writeRun();
        orchestrate("--skip-run");
        assertEquals(List.of(), mavenCalls, "--skip-run launches nothing");
        assertEquals(S1, onlyPending().step);
    }

    @Test
    void orchestrator_stopsWhenTheTestIdCannotBeResolved() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        String log = capture(() -> HealingWorkflowOrchestrator.main(new String[]{"com.kpj.tests.Syn.DoesNotExist", out.toString(), "--skip-run"}));
        assertTrue(log.contains("could not resolve the testId"), log);
        assertEquals(List.of(), pendingFiles());
    }

    // ================================================================== propose guards that already existed still hold

    @Test
    void aSecondProposeForTheSameStep_doesNotOverwriteTheUnresolvedProposal() throws Exception {
        fixture(new Step(S1, "locationOne", true));
        writeRun();
        propose();
        String hash = onlyPending().proposalHash;
        String log = propose();
        assertTrue(log.contains("PENDING_PROPOSAL_ALREADY_EXISTS"), log);
        assertEquals(hash, onlyPending().proposalHash, "the first proposal is untouched");
    }

    @Test
    void aProtectedPageObject_isNeverProposed() throws Exception {
        // the located Page Object is named LoginPage: ProtectedResourcePolicy (and Failure Analysis) rule it out
        fixture(new Step(S1, "locationOne", true));
        Path login = tmp.resolve("src").resolve("LoginPage.java");
        Files.writeString(login, Files.readString(page).replace("SynMultiPage", "LoginPage"));
        Files.writeString(tmp.resolve("com/kpj/tests/Syn/" + SIMPLE + ".java"),
                Files.readString(tmp.resolve("com/kpj/tests/Syn/" + SIMPLE + ".java")).replace("SynMultiPage", "LoginPage"));
        writeRun();
        String log = propose();
        assertTrue(log.contains("NO_PENDING_PROPOSAL"), log);
        assertEquals(List.of(), pendingFiles());
    }
}
