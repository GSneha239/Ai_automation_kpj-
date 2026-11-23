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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests that a restoration is only ever called verified when the file is byte-for-byte (SHA-256) the exact
 * pre-patch source - not merely equal to the backup file, and never judged from text snippets. Covers a successful restoration, a
 * failed one, a missing and a corrupted backup, a source modified after the patch, and safe manual recovery. Temp files and a fake
 * Maven launcher only.
 */
class RestoreVerificationTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    /** CRLF endings and a non-ASCII character, so "exact" is a real test. */
    private static final String ORIGINAL = "// synthetic page object é\r\nclass SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private SelfHealingAgent.MavenLauncher realLauncher;
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

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static String sha(byte[] b) { return PendingHeal.sha256Hex(b); }

    private PatchApplier applier() { return new PatchApplier(out.toString()); }

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

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    /** Seals a proposal for the standard patch and runs {@code apply-pending ... approved} with the fake re-run doing {@code duringRerun}. */
    private interface Rerun { void run() throws IOException; }

    private void applyApproved(Rerun duringRerun) throws Exception { applyApproved(true, duringRerun); }

    private void applyApproved(boolean targetStillFails, Rerun duringRerun) throws Exception {
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
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);
        SelfHealingAgent.mavenLauncher = args -> {
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) {
                if (targetStillFails) writeFailingRun();   // the target still fails -> the patch must be rolled back
                else Files.writeString(out.resolve("aggregate-data").resolve("SYN.tsv"),
                        String.join("\t", "PASS", "Synthetic page", TEST_ID, "Login", "Authenticated", "Logged in", "") + "\n"
                                + String.join("\t", "PASS", "Synthetic page", TEST_ID, STEP, "element found", "element found", "") + "\n");
                duringRerun.run();
            }
            return 0;
        };
        ApplyTestSupport.apply(pendingFile, "approved");
    }

    private Path backupOf(Map<String, String> row) { return Path.of(row.get("backupPath")); }

    // ================================================================== PatchApplier: restoration is verified against the original

    @Test
    void successfulRestoration_isVerifiedByteForByte() throws Exception {
        byte[] before = Files.readAllBytes(source);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        assertEquals(sha(before), a.lastOriginalSha256, "the exact pre-patch fingerprint is recorded");
        assertEquals(sha(Files.readAllBytes(source)), a.lastPatchedSha256, "and the exact patched fingerprint");
        assertFalse(Arrays.equals(before, Files.readAllBytes(source)));

        PatchApplier.RestoreResult r = a.revertVerified();
        assertTrue(r.verified, r.detail);
        assertEquals("BACKUP", r.method);
        assertEquals(sha(before), r.finalSha256);
        assertArrayEquals(before, Files.readAllBytes(source), "CRLF endings and the non-ASCII character are back exactly");
    }

    @Test
    void failedRestoration_isNeverReportedVerified_andLeavesThePatch() throws Exception {
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        byte[] patched = Files.readAllBytes(source);
        assertTrue(source.toFile().setReadOnly());
        PatchApplier.RestoreResult r = a.revertVerified();
        assertFalse(r.verified);
        assertEquals("", r.method);
        assertFalse(r.detail.isBlank());
        assertArrayEquals(patched, Files.readAllBytes(source), "the file is exactly as the patch left it");
        assertFalse(a.revert(), "and it still is not verified on a retry");
    }

    @Test
    void aMissingBackup_isRestoredFromTheInMemoryCopy_andVerifiedAgainstTheOriginal() throws Exception {
        byte[] before = Files.readAllBytes(source);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        Files.delete(a.lastBackupPath);
        PatchApplier.RestoreResult r = a.revertVerified();
        assertTrue(r.verified, r.detail);
        assertEquals("MEMORY", r.method, "the method says which copy was used");
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    void aCorruptedBackup_isNeverTrusted_theOriginalIsVerifiedNotTheBackup() throws Exception {
        byte[] before = Files.readAllBytes(source);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        Files.writeString(a.lastBackupPath, "class SynPage { /* a damaged or tampered backup */ }");
        PatchApplier.RestoreResult r = a.revertVerified();
        assertTrue(r.verified, r.detail);
        assertEquals("MEMORY", r.method, "the damaged backup was refused");
        assertArrayEquals(before, Files.readAllBytes(source), "the file holds the real original, not the backup's content");
        assertEquals(sha(before), r.finalSha256);
    }

    @Test
    void aMissingBackup_andNoWayToWrite_isNotVerified() throws Exception {
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        Files.delete(a.lastBackupPath);
        source.toFile().setReadOnly();
        PatchApplier.RestoreResult r = a.revertVerified();
        assertFalse(r.verified);
        assertTrue(r.detail.contains("backup was missing"), r.detail);
    }

    @Test
    void aSourceModifiedAfterThePatch_isNeverOverwritten_andNeverVerified() throws Exception {
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        String edited = Files.readString(source) + "// a developer added this after the patch\r\n";
        Files.write(source, bytes(edited));
        PatchApplier.RestoreResult r = a.revertVerified();
        assertFalse(r.verified);
        assertTrue(r.detail.contains("changed after the patch was applied") && r.detail.contains("NOT overwritten"), r.detail);
        assertArrayEquals(bytes(edited), Files.readAllBytes(source), "the later edit is preserved");
    }

    @Test
    void safeManualRecovery_aFileAlreadyRestoredByAPerson_isVerifiedWithoutBeingTouched() throws Exception {
        byte[] before = Files.readAllBytes(source);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(source, OLD, NEW, "r", 0.75, true));
        Files.copy(a.lastBackupPath, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);   // a person copies the backup back
        long modifiedBefore = Files.getLastModifiedTime(source).toMillis();
        PatchApplier.RestoreResult r = a.revertVerified();
        assertTrue(r.verified, r.detail);
        assertEquals("ALREADY_RESTORED", r.method);
        assertArrayEquals(before, Files.readAllBytes(source));
        assertEquals(modifiedBefore, Files.getLastModifiedTime(source).toMillis(), "nothing was written");
    }

    @Test
    void aFileThatDoesNotRoundTripAsUtf8_isNeverPatched() throws Exception {
        byte[] latin1 = new byte[]{'c', 'l', 'a', 's', 's', ' ', (byte) 0xE9, ' ', '\n'};
        Path f = tmp.resolve("src").resolve("Latin1.java");
        Files.write(f, latin1);
        try {
            assertFalse(applier().confirmAndApply(f, "class", "klass", "r", 0.75, true));
        } catch (IOException expected) {
            // refusing by exception is equally safe: nothing was written
        }
        assertArrayEquals(latin1, Files.readAllBytes(f), "the file is untouched");
    }

    // ================================================================== BackupManager: byte-exact and pinned to the expected content

    @Test
    void theBackupIsByteExact_andARestoreIsPinnedToTheExpectedContent() throws Exception {
        byte[] weird = new byte[]{1, 2, 3, (byte) 0xFF, (byte) 0xFE, '\r', '\n', 0};   // not valid UTF-8 text at all
        Path f = tmp.resolve("src").resolve("Binary.dat");
        Files.write(f, weird);
        BackupManager bm = new BackupManager(out.toString());
        BackupManager.BackupResult b = bm.backup(f);
        assertTrue(b.success, b.reason);
        assertArrayEquals(weird, Files.readAllBytes(b.backupPath), "a text round-trip would have corrupted these bytes");
        assertEquals(sha(weird), b.sha256);

        Files.write(f, new byte[]{9, 9, 9});
        assertFalse(bm.restore(b.backupPath, f, "0".repeat(64)), "a backup that does not hold the expected content is refused");
        assertArrayEquals(new byte[]{9, 9, 9}, Files.readAllBytes(f), "and nothing was written");
        assertTrue(bm.restore(b.backupPath, f, sha(weird)));
        assertArrayEquals(weird, Files.readAllBytes(f));
    }

    // ================================================================== end to end: what the audit trail and reports record

    @Test
    void aVerifiedRollback_recordsTheBackupTheFingerprintsAndTheMethod() throws Exception {
        byte[] before = Files.readAllBytes(source);
        applyApproved(() -> { });
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("true", r.get("restorationVerified"));
        assertEquals("BACKUP", r.get("restorationMethod"));
        assertEquals(sha(before), r.get("originalSha256"), "not redacted, and equal to the exact original");
        assertEquals(sha(bytes(ORIGINAL.replace(OLD, NEW))), r.get("patchedSha256"));
        assertTrue(Files.isRegularFile(backupOf(r)), "the backup reference points at a real file");
        assertArrayEquals(before, Files.readAllBytes(backupOf(r)));
        assertArrayEquals(before, Files.readAllBytes(source));

        Path dir = out.resolve("unified-report");
        try (Stream<Path> s = Files.list(dir)) {
            String u = Files.readString(s.sorted().reduce((a, b) -> b).orElseThrow());
            assertTrue(u.contains("Created: ") && u.contains(backupOf(r).getFileName().toString()), "the report names the exact backup");
            assertTrue(u.contains("via BACKUP") && u.contains(sha(before).substring(0, 12)), "and the verification method");
        }
    }

    @Test
    void aKeptPatch_recordsTheBackupAndFingerprints_butNoRestoration() throws Exception {
        applyApprovedKept();
        Map<String, String> r = lastAuditRow();
        assertEquals("KEPT", r.get("finalStatus"));
        assertEquals("", r.get("restorationVerified"));
        assertEquals("", r.get("restorationMethod"));
        assertTrue(r.get("originalSha256").matches("[0-9a-f]{64}") && r.get("patchedSha256").matches("[0-9a-f]{64}"));
        assertTrue(Files.isRegularFile(backupOf(r)));
    }

    private void applyApprovedKept() throws Exception { applyApproved(false, () -> { }); }

    @Test
    void aMissingBackup_endToEnd_isStillVerifiedAgainstTheOriginal_viaTheInMemoryCopy() throws Exception {
        byte[] before = Files.readAllBytes(source);
        applyApproved(() -> {
            try (Stream<Path> s = Files.walk(out.resolve("self-healing").resolve("backups"))) {
                for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) Files.delete(p);
            }
        });
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertEquals("MEMORY", r.get("restorationMethod"));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    void aSourceModifiedDuringTheRerun_isNotOverwritten_andIsReportedAsRestoreFailed() throws Exception {
        String[] edited = new String[1];
        applyApproved(() -> {
            edited[0] = Files.readString(source) + "// edited during the re-run\r\n";
            Files.write(source, bytes(edited[0]));
        });
        Map<String, String> r = lastAuditRow();
        assertEquals("RESTORE_FAILED", r.get("finalStatus"), "an unverifiable rollback is never REVERTED");
        assertEquals("false", r.get("reverted"));
        assertEquals("false", r.get("restorationVerified"));
        assertTrue(r.get("reason").contains("changed after the patch was applied") && r.get("reason").contains("MAY STILL CONTAIN THE PATCH"), r.get("reason"));
        assertArrayEquals(bytes(edited[0]), Files.readAllBytes(source), "the later edit was not clobbered");
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")), "nothing is marked fixed");
    }

    @Test
    void safeManualRecovery_afterAFailedRestoration_isConfirmedByTheExactFingerprint() throws Exception {
        byte[] before = Files.readAllBytes(source);
        applyApproved(() -> source.toFile().setReadOnly());   // the restoration cannot write
        Map<String, String> r = lastAuditRow();
        assertEquals("RESTORE_FAILED", r.get("finalStatus"));
        assertEquals(sha(before), r.get("originalSha256"));
        HealingAuditTrail trail = new HealingAuditTrail(out.toString());
        String blocked = trail.unresolvedRestoreFailure(source);
        assertNotNull(blocked);
        assertTrue(blocked.contains("still exactly the patched content") && blocked.contains(backupOf(r).getFileName().toString()), blocked);

        String before1 = capture(() -> SelfHealingAgent.main(new String[]{"verify-restore", source.toString(), out.toString()}));
        assertTrue(before1.contains("RESTORE_STATUS: UNRESOLVED"), before1);
        assertTrue(before1.contains(sha(before)), "it tells the person which SHA-256 a correct restoration has: " + before1);

        // the person fixes the file by hand from the recorded backup
        source.toFile().setWritable(true);
        Files.copy(backupOf(r), source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertNull(trail.unresolvedRestoreFailure(source), "the block lifts only because the file now hashes to the original");
        String after = capture(() -> SelfHealingAgent.main(new String[]{"verify-restore", source.toString(), out.toString()}));
        assertTrue(after.contains("RESTORE_STATUS: RECOVERED"), after);
    }

    @Test
    void aHandEditedFile_isNotMistakenForARecovery_whenItStillHoldsThePatch() throws Exception {
        applyApproved(() -> source.toFile().setReadOnly());
        source.toFile().setWritable(true);
        // someone appends a line but leaves the patched text in place: neither the original nor the patched fingerprint, yet unresolved
        Files.write(source, bytes(Files.readString(source) + "// unrelated edit\r\n"));
        assertNotNull(new HealingAuditTrail(out.toString()).unresolvedRestoreFailure(source));
    }

    @Test
    void verifyRestore_withNothingRecorded_saysSo() throws Exception {
        String log = capture(() -> SelfHealingAgent.main(new String[]{"verify-restore", source.toString(), out.toString()}));
        assertTrue(log.contains("RESTORE_STATUS: NO_RECORD"), log);
    }
}
