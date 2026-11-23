package com.kpj.heal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests for {@link PatchApplier} and {@link BackupManager}. Every source file and every output
 * folder is a temp file: no real project source is read or patched, and there is no browser, Maven or DevHIS.
 *
 * <p>Guarantees under test: a declined or refused change never touches the source; an approved change is backed up
 * first (the backup holds the exact original); revert restores the exact original bytes.</p>
 */
class PatchApplierTest {

    @TempDir
    Path tmp;

    private static final String OLD = "        const e = find('staffScheduleInfo.LocationId');";
    private static final String NEW = "        const e = find('staffScheduleInfo.Locationid');";

    /** Original source with CRLF line endings and a non-ASCII character, so "exact restore" is a real test. */
    private static final String ORIGINAL = "// synthetic page object é\r\nclass Page {\r\n    void probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";

    private Path source(String content) throws IOException {
        Path f = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(f.getParent());
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private PatchApplier applier() { return new PatchApplier(tmp.resolve("out").toString()); }

    private long backupCount() throws IOException {
        Path dir = tmp.resolve("out").resolve("self-healing").resolve("backups");
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) { return s.count(); }
    }

    // ------------------------------------------------------------------ declined / refused: source never changes

    @Test
    void declined_neverTouchesTheSource_andTakesNoBackup() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertFalse(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, false), "a declined change is not applied");
        assertArrayEquals(before, Files.readAllBytes(f), "the source file is byte-identical");
        assertEquals(0, backupCount(), "no backup is taken for a change that is not applied");
        assertFalse(a.lastBackupCreated);
        assertNull(a.lastBackupPath);
    }

    @Test
    void snippetNotPresent_isRefused_andNothingChanges() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertFalse(a.confirmAndApply(f, "this text is not in the file", NEW, "reason", 0.75, true));
        assertArrayEquals(before, Files.readAllBytes(f));
        assertEquals(0, backupCount());
    }

    @Test
    void ambiguousSnippet_occurringTwice_isRefused() throws IOException {
        Path f = source(ORIGINAL + OLD + "\r\n");
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertFalse(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true), "an ambiguous change is never applied");
        assertArrayEquals(before, Files.readAllBytes(f));
        assertEquals(0, backupCount());
    }

    @Test
    void ifTheBackupCannotBeCreated_theChangeIsNotApplied() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        // make the backup location impossible: "self-healing" is a regular FILE, so backups/ cannot be created under it
        Path out = tmp.resolve("out");
        Files.createDirectories(out);
        Files.writeString(out.resolve("self-healing"), "blocker");
        PatchApplier a = applier();
        assertFalse(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true), "no verified backup means no patch");
        assertArrayEquals(before, Files.readAllBytes(f), "the source is untouched when the backup fails");
        assertFalse(a.lastBackupCreated);
    }

    // ------------------------------------------------------------------ approved: backup first, then patch

    @Test
    void approved_createsAVerifiedBackupOfTheExactOriginal_thenApplies() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true));
        assertTrue(a.lastBackupCreated);
        assertTrue(Files.isRegularFile(a.lastBackupPath), "the backup file exists");
        assertArrayEquals(before, Files.readAllBytes(a.lastBackupPath),
                "the backup holds the exact ORIGINAL - which can only be so if it was taken before the write");
        String patched = Files.readString(f);
        assertTrue(patched.contains(NEW) && !patched.contains(OLD), "the change is applied");
        assertTrue(a.lastBackupPath.toString().replace('\\', '/').contains("/self-healing/backups/SynPage.java."),
                "backups live under <outDir>/self-healing/backups/<File>.<timestamp>.bak");
        assertTrue(a.lastBackupPath.toString().endsWith(".bak"));
    }

    @Test
    void onlyTheTargetSnippetChanges() throws IOException {
        Path f = source(ORIGINAL);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true));
        assertEquals(ORIGINAL.replace(OLD, NEW), Files.readString(f), "every other byte of the file is unchanged");
    }

    // ------------------------------------------------------------------ revert restores the exact original

    @Test
    void revert_restoresTheExactOriginalBytes() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true));
        assertFalse(Arrays.equals(before, Files.readAllBytes(f)), "sanity: the file really was changed");
        assertTrue(a.revert(), "the restoration is verified");
        assertArrayEquals(before, Files.readAllBytes(f), "CRLF endings and non-ASCII text are restored byte for byte");
        assertTrue(Files.isRegularFile(a.lastBackupPath), "revert never deletes the backup");
    }

    @Test
    void revert_fallsBackToTheInMemoryOriginal_whenTheBackupFileIsGone() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(f, OLD, NEW, "reason", 0.75, true));
        Files.delete(a.lastBackupPath);
        assertTrue(a.revert(), "defence in depth: the in-memory copy restores it");
        assertArrayEquals(before, Files.readAllBytes(f));
    }

    @Test
    void revert_withNothingApplied_isANoOp() throws IOException {
        Path f = source(ORIGINAL);
        byte[] before = Files.readAllBytes(f);
        assertFalse(applier().revert(), "nothing was applied, so nothing is restored");
        assertArrayEquals(before, Files.readAllBytes(f));
    }

    @Test
    void backupsAreNeverOverwritten_acrossTwoApprovedChanges() throws IOException, InterruptedException {
        Path f = source(ORIGINAL);
        PatchApplier a = applier();
        assertTrue(a.confirmAndApply(f, OLD, NEW, "first", 0.75, true));
        Path first = a.lastBackupPath;
        Thread.sleep(20); // backup names carry a millisecond timestamp
        assertTrue(a.confirmAndApply(f, NEW, OLD, "second", 0.75, true));
        assertTrue(Files.exists(first), "the first backup still exists");
        assertEquals(2, backupCount(), "each approved change gets its own backup file");
        String names = Files.list(tmp.resolve("out").resolve("self-healing").resolve("backups")).map(p -> p.getFileName().toString())
                .collect(Collectors.joining(","));
        assertTrue(names.contains("SynPage.java."), names);
    }
}
