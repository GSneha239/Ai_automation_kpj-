package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * V4 Step 7 — creates a verified on-disk backup of a source file BEFORE {@link PatchApplier} writes to it,
 * and restores from it on revert. Used only by {@code PatchApplier} — this class never decides whether to
 * apply anything, it only backs up and restores bytes on request.
 *
 * <p>Backups live under {@code <outDir>/self-healing/backups/<FileName>.<timestamp>.bak} and are NEVER
 * deleted automatically, by design (Step 7: "Do NOT delete backups automatically"). Relocated from the
 * original {@code <outDir>/failures/backups} into the dedicated {@code self-healing/} report area — a pure
 * storage-location change; the verified copy/restore behaviour below is unchanged.</p>
 */
public class BackupManager {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path backupDir;

    public BackupManager(String outDir) {
        this.backupDir = Paths.get(outDir, "self-healing", "backups");
    }

    public static class BackupResult {
        public final boolean success;
        public final Path backupPath;
        public final String reason;
        /** SHA-256 of the exact bytes that were backed up (the pre-patch source). Empty on failure. */
        public final String sha256;

        private BackupResult(boolean success, Path backupPath, String reason, String sha256) {
            this.success = success;
            this.backupPath = backupPath;
            this.reason = reason;
            this.sha256 = sha256;
        }

        static BackupResult ok(Path backupPath, String sha256) { return new BackupResult(true, backupPath, "", sha256); }
        static BackupResult fail(String reason) { return new BackupResult(false, null, reason, ""); }
    }

    /** Copies {@code sourceFile}'s exact BYTES to a timestamped backup, then re-reads the backup and verifies its SHA-256
     *  equals the source's before reporting success (a text round-trip is never involved, so any encoding or line ending
     *  survives unchanged). */
    public BackupResult backup(Path sourceFile) {
        try {
            Files.createDirectories(backupDir);
            String ts = LocalDateTime.now().format(TS);
            Path backupPath = backupDir.resolve(sourceFile.getFileName() + "." + ts + ".bak");
            byte[] original = Files.readAllBytes(sourceFile);
            String sha = PendingHeal.sha256Hex(original);
            Files.write(backupPath, original);

            if (!Files.isRegularFile(backupPath) || !Files.isReadable(backupPath)) {
                return BackupResult.fail("backup file missing or unreadable after write: " + backupPath);
            }
            if (!sha.equals(PendingHeal.sha256Hex(Files.readAllBytes(backupPath)))) {
                return BackupResult.fail("backup content does not match source — refusing to trust it: " + backupPath);
            }
            return BackupResult.ok(backupPath, sha);
        } catch (IOException e) {
            return BackupResult.fail("backup failed: " + e.getMessage());
        }
    }

    /** Restores {@code targetFile} from {@code backupPath}, then verifies the restored bytes equal the backup's bytes.
     *  Never deletes the backup. Prefer {@link #restore(Path, Path, String)}, which also pins the expected content. */
    public boolean restore(Path backupPath, Path targetFile) {
        try {
            byte[] content = Files.readAllBytes(backupPath);
            Files.write(targetFile, content);
            return java.util.Arrays.equals(content, Files.readAllBytes(targetFile));
        } catch (IOException e) {
            System.out.println("BackupManager: restore FAILED (" + e.getMessage() + ") for " + targetFile);
            return false;
        }
    }

    /**
     * Restores {@code targetFile} from {@code backupPath} ONLY if the backup still holds exactly the content whose SHA-256 is
     * {@code expectedSha256} (the pre-patch source) - a missing, truncated or altered backup is refused, never trusted - and
     * then re-reads the target and verifies it hashes to {@code expectedSha256}. Never deletes the backup.
     */
    public boolean restore(Path backupPath, Path targetFile, String expectedSha256) {
        try {
            byte[] content = Files.readAllBytes(backupPath);
            if (!PendingHeal.sha256Hex(content).equals(expectedSha256)) {
                System.out.println("BackupManager: the backup " + backupPath + " no longer matches the original content - not used.");
                return false;
            }
            Files.write(targetFile, content);
            return PendingHeal.sha256Hex(Files.readAllBytes(targetFile)).equals(expectedSha256);
        } catch (IOException e) {
            System.out.println("BackupManager: restore FAILED (" + e.getMessage() + ") for " + targetFile);
            return false;
        }
    }
}
