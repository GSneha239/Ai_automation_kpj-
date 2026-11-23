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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests that a {@code RESTORE_FAILED} attempt (a patch whose rollback could not be verified) is tracked as
 * an unsuccessful, UNRESOLVED attempt everywhere: the audit lookups, the healing memory, the summary counters, loop protection and
 * proposal selection - never as a successful revert or a heal. Temp files and a fake Maven launcher only.
 */
class RestoreFailedTrackingTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    // LF line endings, so the proposer's whole-line snippets equal OLD/NEW exactly (needed to match loop-protection signatures)
    private static final String ORIGINAL = "class SynPage {\n    public boolean probe() {\n" + OLD + "\n    }\n}\n";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private Path source, out, pendingFile;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, ORIGINAL);
        out = tmp.resolve("out");
        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");
        writeFailingRun();
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", ACTUAL);
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        ApplyTestSupport.uninstallLocatorFixture();
        source.toFile().setWritable(true);
    }

    // ------------------------------------------------------------------ helpers

    private void writeFailingRun() throws IOException {
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(out.resolve("aggregate-data").resolve("SYN.tsv"),
                String.join("\t", "PASS", "Synthetic page", TEST_ID, "Login", "Authenticated", "Logged in", "") + "\n"
                        + String.join("\t", "FAIL", "Synthetic page", TEST_ID, STEP, "element found", ACTUAL, "") + "\n");
    }

    private HealingAttemptRecord record(String finalStatus, Path file, String oldSnippet, String newSnippet) {
        HealingAttemptRecord r = new HealingAttemptRecord();
        r.testId = TEST_ID;
        r.testClass = ApplyTestSupport.TEST_FQCN;
        r.step = STEP;
        r.affectedFile = file.toString();
        r.affectedMethod = "probe()";
        r.oldSnippet = oldSnippet;
        r.newSnippet = newSnippet;
        r.finalStatus = finalStatus;
        r.reason = "r";
        r.humanDecision = "APPROVED";
        r.patchApplied = true;
        r.reverted = "REVERTED".equals(finalStatus);
        return r;
    }

    private HealingAuditTrail trail() { return new HealingAuditTrail(out.toString()); }

    private String capture(Action a) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try { a.run(); } finally { System.setOut(old); }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    private interface Action { void run() throws Exception; }

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private static long stat(String html, String label) {
        Matcher m = Pattern.compile("<div class='n'>(\\d+)</div><div class='l'>" + Pattern.quote(label) + "</div>").matcher(html);
        assertTrue(m.find(), "summary shows \"" + label + "\"");
        return Long.parseLong(m.group(1));
    }

    // ================================================================== lookups: audit prior attempts + healing memory

    @Test
    void priorAttempts_includeARestoreFailedAttempt_alongsideRevertedAndRejected() {
        trail().append(record("KEPT", source, OLD, NEW));
        trail().append(record("REVERTED", source, OLD, NEW));
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        trail().append(record("REJECTED_VERIFICATION_FAILED", source, OLD, NEW));
        trail().append(record("NOT_APPLIED", source, OLD, NEW));
        List<String> statuses = new ArrayList<>();
        for (HealingAuditTrail.PriorAttempt p : trail().priorAttempts(TEST_ID, STEP)) statuses.add(p.finalStatus);
        assertEquals(List.of("REVERTED", "RESTORE_FAILED", "REJECTED_VERIFICATION_FAILED"), statuses,
                "a restore-failed attempt is told to the reasoning layer as an unsuccessful earlier attempt; KEPT / NOT_APPLIED are not");
    }

    @Test
    void healingMemory_countsARestoreFailedAttemptSeparately_neverAsAKeepOrARevert() {
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        HealingMemory.Summary s = new HealingMemory(out.toString()).forLocation(source.toString(), "probe()");
        assertEquals(0, s.timesKept);
        assertEquals(0, s.timesReverted, "not a successful revert");
        assertEquals(1, s.timesRestoreFailed);
        assertFalse(s.hasSuccessfulPrecedent(), "and certainly not a success");
        assertTrue(s.hasFailedPrecedent(), "it is an unsuccessful precedent");
        assertTrue(s.hasAnyPrecedent());
    }

    @Test
    void healingMemory_mixedHistory() {
        trail().append(record("KEPT", source, OLD, NEW));
        trail().append(record("REVERTED", source, OLD, NEW));
        trail().append(record("REVERTED", source, OLD, NEW));
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        HealingMemory.Summary s = new HealingMemory(out.toString()).forLocation(source.toString(), "probe()");
        assertEquals(1, s.timesKept);
        assertEquals(2, s.timesReverted);
        assertEquals(1, s.timesRestoreFailed);
    }

    // ================================================================== summary counters

    @Test
    void summaryCounters_neverCountARestoreFailedAttemptAsRolledBackOrHealed() throws Exception {
        List<HealingAttemptRecord> attempts = new ArrayList<>();
        attempts.add(record("KEPT", source, OLD, NEW));
        attempts.add(record("REVERTED", source, OLD, NEW));                       // reverted=true
        HealingAttemptRecord failed = record("RESTORE_FAILED", source, OLD, NEW); // reverted=false
        attempts.add(failed);
        HealingAttemptRecord wronglyFlagged = record("RESTORE_FAILED", source, OLD, NEW);
        wronglyFlagged.reverted = true;                                           // even a stray flag must not make it a rollback
        attempts.add(wronglyFlagged);
        HealingDashboard.Result res = HealingDashboard.write(out.toString(), "SynTest", GitStatus.capture(), List.of(), attempts, false, 3);
        assertNotNull(res);
        String html = Files.readString(res.summaryReportPath);
        assertEquals(1, stat(html, "Fixes retained (KEEP)"));
        assertEquals(1, stat(html, "Fixes rolled back"));
        assertEquals(2, stat(html, "Rollbacks NOT verified (manual restore needed)"));
    }

    // ================================================================== the unresolved-restore lookup

    @Test
    void aFileThatStillShowsTheChange_isUnresolved() throws Exception {
        Files.writeString(source, ORIGINAL.replace(OLD, NEW));   // the patch is still in the file
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        String why = trail().unresolvedRestoreFailure(source);
        assertNotNull(why);
        assertTrue(why.contains("could not be verified") && why.contains("still contains that change"), why);
    }

    @Test
    void aFileThatWasRestored_isNoLongerUnresolved() throws Exception {
        Files.writeString(source, ORIGINAL.replace(OLD, NEW));
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        Files.writeString(source, ORIGINAL);   // a person restored it from the backup
        assertNull(trail().unresolvedRestoreFailure(source), "the block lifts itself");
    }

    @Test
    void aFileReworkedByHand_isNoLongerUnresolved() throws Exception {
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        Files.writeString(source, "class SynPage { /* rewritten by a person */ }\r\n");
        assertNull(trail().unresolvedRestoreFailure(source));
    }

    @Test
    void anotherFile_isUnaffected() throws Exception {
        Files.writeString(source, ORIGINAL.replace(OLD, NEW));
        trail().append(record("RESTORE_FAILED", source, OLD, NEW));
        Path other = tmp.resolve("src").resolve("OtherPage.java");
        Files.writeString(other, ORIGINAL.replace(OLD, NEW));
        assertNull(trail().unresolvedRestoreFailure(other));
    }

    @Test
    void aMultiLineChange_isRecognisedDespiteTheAuditStoringItOnOneLine() throws Exception {
        String oldText = "line one\n    line two";
        String newText = "line ONE\n    line TWO";
        Files.writeString(source, "class P {\n    " + newText + "\n}\n");
        trail().append(record("RESTORE_FAILED", source, oldText, newText));
        assertNotNull(trail().unresolvedRestoreFailure(source));
    }

    @Test
    void aChangeThatCannotBeCompared_isTreatedAsUnresolved_neverAssumedRestored() throws Exception {
        trail().append(record("RESTORE_FAILED", source, OLD, "[REDACTED]"));
        String why = trail().unresolvedRestoreFailure(source);
        assertNotNull(why);
        assertTrue(why.contains("cannot be confirmed restored"), why);
    }

    // ================================================================== end to end: a real failed rollback

    /** The restoration fails because the file is read-only by the time the patch has to be undone - and the patch is still in it. */
    private void applyWithUnrestorableFile() throws Exception {
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = TEST_ID;
        p.testClass = ApplyTestSupport.TEST_FQCN;
        p.simpleName = "SynTest";
        p.step = STEP;
        p.affectedFile = source.toString();
        p.affectedMethod = "probe()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = OLD;
        p.newSnippet = NEW;
        p.reason = "closest live ng-model is staffScheduleInfo.Locationid";
        p.confidence = 0.75;
        p.risk = "LOW";
        p.rootCause = "Stale locator literal";
        p.semanticValidation = "PASSED";
        p.groupKey = "SynPage.java::probe::locationid";
        p.originalFailingSteps = new ArrayList<>(List.of(STEP));
        p.attemptNumber = 1;
        p.attemptsMax = 3;
        p.sessionFile = out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties").toString();
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);
        SelfHealingAgent.mavenLauncher = args -> {
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) {
                writeFailingRun();                     // the target still fails -> a rollback is needed
                source.toFile().setReadOnly();         // ... and the file can no longer be written
            }
            return 0;
        };
        ApplyTestSupport.apply(pendingFile, "approved");
    }

    @Test
    void aRealFailedRollback_isTrackedEverywhereAsUnsuccessfulAndUnresolved() throws Exception {
        applyWithUnrestorableFile();

        Map<String, String> r = lastAuditRow();
        assertEquals("RESTORE_FAILED", r.get("finalStatus"));
        assertEquals("false", r.get("reverted"));
        assertTrue(Files.readString(source).contains(NEW), "the patch really is still in the file");

        // lookups
        assertEquals(1, trail().priorAttempts(TEST_ID, STEP).stream().filter(p -> "RESTORE_FAILED".equals(p.finalStatus)).count());
        HealingMemory.Summary s = new HealingMemory(out.toString()).forLocation(source.toString(), "probe()");
        assertEquals(0, s.timesKept + s.timesReverted);
        assertEquals(1, s.timesRestoreFailed);
        assertNotNull(trail().unresolvedRestoreFailure(source));

        // loop protection: the exact patch is recorded as tried-and-failed
        HealingSession session = HealingSession.load(out.resolve("self-healing").resolve("session").resolve("SYN_TEST.session.properties"), 3, false);
        HealingCandidate c = new HealingCandidate(new PatchProposer.Proposal(PatchProposer.Proposal.Kind.LOCATOR, 0.75, OLD, NEW, "r",
                PatchProposer.Proposal.Source.DETERMINISTIC, "rc", new ArrayList<>(), source.toString(), "r", null), null);
        assertTrue(session.isSignatureTriedAndFailed(c.signature(TEST_ID, STEP)));
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")), "never marked fixed");
    }

    @Test
    void afterARestoreFailure_noFurtherProposalIsMadeForThatFile_untilItIsRestored() throws Exception {
        applyWithUnrestorableFile();
        assertTrue(Files.readString(source).contains(NEW));
        SelfHealingAgent.mavenLauncher = realLauncher;   // propose never launches Maven
        writeFailingRun();

        String blocked = capture(() -> SelfHealingAgent.main(new String[]{"propose", ApplyTestSupport.TEST_FQCN, out.toString()}));
        assertTrue(blocked.contains("could not be verified"), blocked);
        assertTrue(blocked.contains("NO_PENDING_PROPOSAL"), blocked);
        assertFalse(Files.exists(out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties")));
        assertEquals("NEEDS_TRIAGE", lastAuditRow().get("finalStatus"));

        // a person restores the file (it is writable again): the block lifts. The exact patch that failed is still not re-offered.
        source.toFile().setWritable(true);
        Files.writeString(source, ORIGINAL);
        assertNull(trail().unresolvedRestoreFailure(source));
        String after = capture(() -> SelfHealingAgent.main(new String[]{"propose", ApplyTestSupport.TEST_FQCN, out.toString()}));
        assertFalse(after.contains("could not be verified"), "the restore-failure block is gone: " + after);
        assertTrue(after.contains("loop protection"), "but the identical failed patch is not offered again: " + after);
    }

    @Test
    void aProposalForAFileWithAnUnresolvedRestoreFailure_isNotProposedThenIsAllowedOnceRestored() throws Exception {
        // an earlier, unrelated rollback of this file could not be verified; its change text is still in the file
        String marker = "// patched-by-an-earlier-attempt";
        Files.writeString(source, ORIGINAL + marker + "\r\n");
        trail().append(record("RESTORE_FAILED", source, "// original", marker));
        writeFailingRun();
        String blocked = capture(() -> SelfHealingAgent.main(new String[]{"propose", ApplyTestSupport.TEST_FQCN, out.toString()}));
        assertTrue(blocked.contains("NO_PENDING_PROPOSAL"), blocked);

        Files.writeString(source, ORIGINAL);   // the file is restored
        String ok = capture(() -> SelfHealingAgent.main(new String[]{"propose", ApplyTestSupport.TEST_FQCN, out.toString()}));
        assertTrue(ok.contains("PENDING_HEAL_FILE"), "no longer blocked, and not stuck by a saved 'exhausted' mark: " + ok);
    }

    @Test
    void anApprovalForAFileWithAnUnresolvedRestoreFailure_isRefused() throws Exception {
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = TEST_ID;
        p.testClass = ApplyTestSupport.TEST_FQCN;
        p.simpleName = "SynTest";
        p.step = STEP;
        p.affectedFile = source.toString();
        p.affectedMethod = "probe()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = OLD;
        p.newSnippet = NEW;
        p.reason = "closest live ng-model is staffScheduleInfo.Locationid";
        p.confidence = 0.75;
        p.risk = "LOW";
        p.rootCause = "x";
        p.semanticValidation = "PASSED";
        p.groupKey = "g";
        p.originalFailingSteps = new ArrayList<>(List.of(STEP));
        p.attemptNumber = 1;
        p.attemptsMax = 3;
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);
        trail().append(record("RESTORE_FAILED", source, "// original", "class SynPage"));   // its change text is present in the file
        ApplyTestSupport.apply(pendingFile, "approved");
        assertEquals(ORIGINAL, Files.readString(source), "nothing was applied");
        Map<String, String> r = lastAuditRow();
        assertEquals("NOT_APPLIED", r.get("finalStatus"));
        assertTrue(r.get("reason").startsWith("APPLY REFUSED - ") && r.get("reason").contains("could not be verified"), r.get("reason"));
    }
}
