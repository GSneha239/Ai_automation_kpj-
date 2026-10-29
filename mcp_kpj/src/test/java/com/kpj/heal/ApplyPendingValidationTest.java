package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests of the apply-time proposal validation. An approval is honoured only for the exact, unmodified,
 * still-current, still-eligible proposal it quotes; every refusal must happen BEFORE any backup or write, leave the source file
 * byte-identical, run no Maven command, and say why. The "healed" source, every output folder and the failure evidence are temp
 * files and {@link SelfHealingAgent#mavenLauncher} is a fake: no Maven, browser or DevHIS is used.
 */
class ApplyPendingValidationTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> mavenCalls = new ArrayList<>();
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

        writeFailingRun(ACTUAL);
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", ACTUAL);
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        PendingHeal p = newPending(source, OLD, NEW);
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);

        // a Maven launcher that succeeds and records every call - it must NEVER be reached by a refused approval
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) writePassingRun();
            return 0;
        };
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        ApplyTestSupport.uninstallLocatorFixture();
    }

    // ------------------------------------------------------------------ fixtures

    private PendingHeal newPending(Path target, String oldSnippet, String newSnippet) {
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = TEST_ID;
        p.testClass = ApplyTestSupport.TEST_FQCN;
        p.simpleName = "SynTest";
        p.step = STEP;
        p.affectedFile = target.toString();
        p.affectedMethod = "probe()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = oldSnippet;
        p.newSnippet = newSnippet;
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
        return p;
    }

    private Path tsv() { return out.resolve("aggregate-data").resolve("SYN.tsv"); }

    private static String row(String status, String step, String expected, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, expected, actual, "") + "\n";
    }

    private void writeFailingRun(String actual) throws IOException {
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(tsv(), row("PASS", "Login", "Authenticated", "Logged in") + row("FAIL", STEP, "element found", actual));
    }

    private void writePassingRun() throws IOException {
        Files.writeString(tsv(), row("PASS", "Login", "Authenticated", "Logged in") + row("PASS", STEP, "element found", "element found"));
    }

    private PendingHeal reload() throws IOException { return PendingHeal.load(pendingFile); }

    private void resealAndSave(PendingHeal p) throws IOException {
        ApplyTestSupport.reseal(p, source, out);
        p.save(pendingFile);
    }

    private void tamperWithoutResealing(java.util.function.Consumer<PendingHeal> change) throws IOException {
        PendingHeal p = reload();
        change.accept(p);
        p.save(pendingFile);   // the stored hash is left as it was
    }

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private List<Path> refusedProposals() throws IOException {
        Path dir = out.resolve("self-healing").resolve("refused-proposals");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) { return s.collect(Collectors.toList()); }
    }

    /** A refusal: nothing written, no backup, no Maven, a recorded reason, the step still open for a fresh proposal. */
    private void assertRefused(String reasonFragment, byte[] expectedSourceBytes) throws Exception {
        assertArrayEquals(expectedSourceBytes, Files.readAllBytes(source), "the source file is untouched");
        assertTrue(mavenCalls.isEmpty(), "no Maven command may run for a refused approval: " + mavenCalls);
        assertNoBackupFiles();
        Map<String, String> r = lastAuditRow();
        assertEquals("NOT_APPLIED", r.get("finalStatus"));
        assertEquals("false", r.get("patchApplied"));
        assertEquals("false", r.get("backupCreated"));
        assertTrue(r.get("reason").startsWith("APPLY REFUSED - "), r.get("reason"));
        assertTrue(r.get("reason").contains(reasonFragment), "reason should mention \"" + reasonFragment + "\": " + r.get("reason"));
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")), "nothing is marked fixed");
        Path session = out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties");
        assertFalse(HealingSession.load(session, 3, false).isStepExhausted(TEST_ID, STEP),
                "a refusal does not use up the step - a fresh, valid proposal can still be made");
    }

    private byte[] original() { return ORIGINAL.getBytes(StandardCharsets.UTF_8); }

    /** The report scaffolding creates an empty backups folder; what matters is that no backup FILE was written. */
    private void assertNoBackupFiles() throws IOException {
        Path dir = out.resolve("self-healing").resolve("backups");
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            assertEquals(List.of(), s.filter(Files::isRegularFile).collect(Collectors.toList()), "no backup file is written");
        }
    }

    // ================================================================== a correct approval still works

    @Test
    void anApprovalQuotingTheProposalsHash_isHonoured() throws Exception {
        ApplyTestSupport.apply(pendingFile, "approved");
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
        assertTrue(Files.readString(source).contains(NEW) && !Files.readString(source).contains(OLD));
        assertFalse(Files.exists(pendingFile), "the pending proposal is consumed");
        assertTrue(refusedProposals().isEmpty());
    }

    @Test
    void theHashSurvivesSaveAndLoad_andChangesWhenAnyFieldChanges() throws Exception {
        PendingHeal loaded = reload();
        assertEquals(loaded.proposalHash, loaded.computeProposalHash(), "stable across save/load");
        assertTrue(loaded.structuralProblems().isEmpty());
        String base = loaded.computeProposalHash();
        PendingHeal a = reload(); a.newSnippet = a.newSnippet + " "; assertNotEquals(base, a.computeProposalHash());
        PendingHeal b = reload(); b.affectedFile = b.affectedFile + "x"; assertNotEquals(base, b.computeProposalHash());
        PendingHeal c = reload(); c.step = c.step + "x"; assertNotEquals(base, c.computeProposalHash());
        PendingHeal d = reload(); d.sourceSha256 = "0".repeat(64); assertNotEquals(base, d.computeProposalHash());
        PendingHeal e = reload(); e.confidence = 0.76; assertNotEquals(base, e.computeProposalHash());
        PendingHeal f = reload(); f.evidence = new ArrayList<>(List.of("extra")); assertNotEquals(base, f.computeProposalHash());
    }

    // ================================================================== approval bound to the exact proposal

    @Test
    void anApprovalWithoutAHash_isRefused_andLeavesTheProposalPending() throws Exception {
        ApplyTestSupport.applyQuoting(pendingFile, null);
        assertRefused("did not quote a valid proposal hash", original());
        assertTrue(Files.exists(pendingFile), "the intact proposal stays pending for a correct approval");
        assertTrue(refusedProposals().isEmpty());
        // ... and the correct approval then works
        ApplyTestSupport.apply(pendingFile, "approved");
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
    }

    @Test
    void anApprovalWithAMalformedHash_isRefused() throws Exception {
        ApplyTestSupport.applyQuoting(pendingFile, "approved-yes-please");
        assertRefused("did not quote a valid proposal hash", original());
        assertTrue(Files.exists(pendingFile));
    }

    @Test
    void anApprovalForAnotherProposal_cannotAuthorizeThisOne() throws Exception {
        // proposal B: same test and file, a different replacement
        Path other = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Other.properties");
        PendingHeal b = newPending(source, OLD, NEW.replace("Locationid", "LocationID"));
        ApplyTestSupport.seal(b, out, source);
        b.save(other);
        assertNotEquals(reload().proposalHash, PendingHeal.load(other).proposalHash);

        // the human approved A (its hash) - that approval is presented against B
        ApplyTestSupport.applyQuoting(other, reload().proposalHash);
        assertRefused("different proposal's hash", original());
        assertTrue(Files.exists(other), "B stays pending, unapplied");
        assertTrue(Files.exists(pendingFile), "A is untouched too");
    }

    @Test
    void aRandomWellFormedHash_isRefused() throws Exception {
        ApplyTestSupport.applyQuoting(pendingFile, "sha256:" + "ab".repeat(32));
        assertRefused("different proposal's hash", original());
        assertTrue(Files.exists(pendingFile));
    }

    // ================================================================== tampered / malformed proposals

    @Test
    void aTamperedProposal_isRefused_evenWithItsOriginalHash() throws Exception {
        String originalHash = reload().proposalHash;
        // an attacker (or a stray edit) swaps in a different replacement but cannot know to fix the hash
        tamperWithoutResealing(p -> p.newSnippet = NEW.replace("Locationid", "SomethingElse"));
        ApplyTestSupport.applyQuoting(pendingFile, originalHash);
        assertRefused("modified after it was created", original());
        assertFalse(Files.exists(pendingFile), "a tampered proposal is moved aside so a fresh one can be made");
        assertEquals(1, refusedProposals().size(), "and it is kept for inspection, not deleted");
    }

    @Test
    void aTamperedTargetFile_isRefused() throws Exception {
        PendingHeal p = reload();
        Path elsewhere = tmp.resolve("src").resolve("Elsewhere.java");
        Files.writeString(elsewhere, ORIGINAL);
        String hash = p.proposalHash;
        tamperWithoutResealing(x -> x.affectedFile = elsewhere.toString());
        ApplyTestSupport.applyQuoting(pendingFile, hash);
        assertRefused("modified after it was created", original());
        assertEquals(ORIGINAL, Files.readString(elsewhere), "the other file was not touched either");
    }

    @Test
    void aMalformedProposal_isRefused() throws Exception {
        PendingHeal p = reload();
        p.newSnippet = "";           // nothing to apply
        p.proposalHash = "";         // and unsealed
        p.save(pendingFile);
        ApplyTestSupport.applyQuoting(pendingFile, "ab".repeat(32));
        assertRefused("malformed", original());
        assertTrue(lastAuditRow().get("reason").contains("newSnippet is missing"));
        assertTrue(lastAuditRow().get("reason").contains("proposalHash is missing"));
    }

    @Test
    void aProposalNamingAnotherOutputFolder_isRefused_andWritesNothingThere() throws Exception {
        Path elsewhere = tmp.resolve("elsewhere");
        PendingHeal p = reload();
        p.outDir = elsewhere.toString();
        resealAndSave(p);   // internally consistent, but it points outside the folder it is stored in
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("output folder", original());
        assertFalse(Files.exists(elsewhere), "nothing is written to the folder named inside the file");
    }

    @Test
    void aPendingFileNotUnderSelfHealingPending_isRefused_andWritesNothing() throws Exception {
        Path stray = tmp.resolve("stray").resolve("SYN_TEST__Probe.properties");
        Files.createDirectories(stray.getParent());
        Files.copy(pendingFile, stray);
        ApplyTestSupport.apply(stray, "approved");
        assertArrayEquals(original(), Files.readAllBytes(source));
        assertTrue(mavenCalls.isEmpty());
        assertFalse(Files.exists(out.resolve("failures").resolve("healing-history.tsv")), "nothing is recorded for a proposal this agent did not create");
        assertTrue(Files.exists(stray));
    }

    // ================================================================== the source file / the evidence must be unchanged

    @Test
    void aChangedSourceFile_isRefused() throws Exception {
        String edited = ORIGINAL + "// someone edited this file after the proposal was made\r\n";
        Files.write(source, edited.getBytes(StandardCharsets.UTF_8));   // the snippet still occurs exactly once
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("has changed since the proposal was created", edited.getBytes(StandardCharsets.UTF_8));
        assertFalse(Files.exists(pendingFile));
        assertEquals(1, refusedProposals().size());
    }

    @Test
    void aSourceFileThatIsOnlyRewrittenWithIdenticalBytes_isStillAccepted() throws Exception {
        Files.write(source, original());   // same bytes, new timestamp
        ApplyTestSupport.apply(pendingFile, "approved");
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
    }

    @Test
    void staleEvidence_theStepNoLongerFails_isRefused() throws Exception {
        writePassingRun();   // a later run of the test already passes
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("stale evidence", original());
    }

    @Test
    void staleEvidence_theFailureChanged_isRefused() throws Exception {
        writeFailingRun("FAILED: a completely different failure than the one analysed");
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("failure evidence", original());
        assertTrue(lastAuditRow().get("reason").contains("stale evidence"));
    }

    // ================================================================== existing policies are re-applied

    @Test
    void aProtectedFile_isRefused_evenWhenTheProposalIsOtherwiseValid() throws Exception {
        Path login = tmp.resolve("src").resolve("LoginPage.java");
        Files.write(login, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        PendingHeal p = newPending(login, OLD, NEW);
        ApplyTestSupport.seal(p, out, login);   // hash, source hash and evidence all consistent
        p.save(pendingFile);
        ApplyTestSupport.apply(pendingFile, "approved");
        assertArrayEquals(original(), Files.readAllBytes(login), "the protected file is untouched");
        assertTrue(mavenCalls.isEmpty());
        assertTrue(lastAuditRow().get("reason").contains("protected resource"), lastAuditRow().get("reason"));
        assertEquals("NOT_APPLIED", lastAuditRow().get("finalStatus"));
        assertNoBackupFiles();
    }

    @Test
    void aProposalThatFailsTheQualityGuard_isRefused() throws Exception {
        PendingHeal p = reload();
        p.newSnippet = NEW + " // TODO remove";
        resealAndSave(p);
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("quality guard", original());
    }

    @Test
    void aProposalThatFailsSemanticValidation_isRefused() throws Exception {
        PendingHeal p = reload();
        p.newSnippet = OLD.replace("staffScheduleInfo.LocationId", "staffScheduleInfo.SelectedMonth");
        resealAndSave(p);
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("semantic validation", original());
    }

    @Test
    void aFailureNowClassifiedAsInfrastructure_isRefused() throws Exception {
        String timeout = "Timeout 30000ms exceeded waiting for selector";
        writeFailingRun(timeout);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", timeout);
        resealAndSave(reload());   // the evidence the proposal is bound to is now the timeout
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("infrastructure", original());
    }

    @Test
    void aProposalWhoseTargetIsNotTheFileTheStepResolvesTo_isRefused() throws Exception {
        Path other = tmp.resolve("src").resolve("OtherPage.java");
        Files.write(other, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        PendingHeal p = newPending(other, OLD, NEW);
        ApplyTestSupport.seal(p, out, other);
        p.save(pendingFile);
        ApplyTestSupport.apply(pendingFile, "approved");
        assertArrayEquals(original(), Files.readAllBytes(other));
        assertTrue(mavenCalls.isEmpty());
        assertTrue(lastAuditRow().get("reason").contains("resolves to SynPage.java"), lastAuditRow().get("reason"));
    }

    // ================================================================== loop protection and attempt budget

    @Test
    void anExhaustedAttemptBudget_inTheSession_isRefused() throws Exception {
        HealingSession s = new HealingSession(3, false);
        s.attempts = 4;   // more attempts were consumed than the budget allows
        s.save(out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties"));
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("attempt budget is exhausted", original());
    }

    @Test
    void aProposalNumberedBeyondTheBudget_isRefused() throws Exception {
        PendingHeal p = reload();
        p.attemptNumber = 4;
        p.attemptsMax = 3;
        resealAndSave(p);
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("attempt budget is exhausted", original());
    }

    @Test
    void theLastPermittedAttempt_isStillAllowed() throws Exception {
        PendingHeal p = reload();
        p.attemptNumber = 3;
        p.attemptsMax = 3;
        resealAndSave(p);
        HealingSession s = new HealingSession(3, false);
        s.attempts = 3;   // exactly the budget: this very proposal consumed the last attempt
        s.save(out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties"));
        ApplyTestSupport.apply(pendingFile, "approved");
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
    }

    @Test
    void anExactPatchThatAlreadyFailed_isRefusedByLoopProtection() throws Exception {
        HealingSession s = new HealingSession(3, false);
        s.markSignatureFailed(new HealingCandidate(new PatchProposer.Proposal(PatchProposer.Proposal.Kind.LOCATOR, 0.75, OLD, NEW,
                "r", PatchProposer.Proposal.Source.DETERMINISTIC, "rc", new ArrayList<>(), source.toString(), "r", null), null)
                .signature(TEST_ID, STEP));
        s.save(out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties"));
        ApplyTestSupport.apply(pendingFile, "approved");
        assertRefused("loop protection", original());
    }

    @Test
    void aStepAlreadyMarkedForManualTriage_isRefused() throws Exception {
        HealingSession s = new HealingSession(3, false);
        s.markStepExhausted(TEST_ID, STEP);
        s.save(out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties"));
        ApplyTestSupport.apply(pendingFile, "approved");
        assertArrayEquals(original(), Files.readAllBytes(source));
        assertTrue(mavenCalls.isEmpty());
        assertTrue(lastAuditRow().get("reason").contains("manual triage"), lastAuditRow().get("reason"));
    }

    // ================================================================== declining never needs a hash

    @Test
    void declining_needsNoHash_andStillChangesNothing() throws Exception {
        ApplyTestSupport.apply(pendingFile, "declined");
        assertArrayEquals(original(), Files.readAllBytes(source));
        assertEquals("NOT_APPLIED", lastAuditRow().get("finalStatus"));
        assertTrue(mavenCalls.isEmpty());
    }
}
