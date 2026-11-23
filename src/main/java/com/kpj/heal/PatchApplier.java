package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step 5 (apply only after showing the change) — prints the proposed one-snippet swap as a diff, requires
 * an explicit confirmation on stdin, then performs a plain text replace in the source file. Refuses to
 * apply if the target snippet doesn't appear exactly once (ambiguous edit — safer to do nothing).
 *
 * <p><b>V3 — this is the ONLY class in {@code com.kpj.heal} allowed to write to a source file.</b>
 * {@code SelfHealingAgent}, {@code PatchProposer}, {@code LlmFallbackProposer}, and every LLM-related class
 * only ever produce a {@code Proposal} (a proposed old/new snippet pair) — none of them touch disk. And
 * within this class, approval is intentionally the strictest possible check: ONLY an exact {@code "y"} or
 * {@code "Y"} applies the patch. Not {@code "yes"}, not empty (Enter), not anything else — no confidence
 * value, however high, and no proposal source (deterministic or LLM) changes this.</p>
 *
 * <p><b>V4 Step 7</b> — before writing the patch, a verified on-disk {@link BackupManager} backup is taken.
 * If the backup cannot be created and verified, the patch is NOT applied at all. {@link #revert()} restores
 * from that backup file (falling back to the in-memory copy this class already kept, as defence in depth,
 * if the backup file is somehow unavailable) and verifies the restoration succeeded. Backups are never
 * deleted by this class.</p>
 */
public class PatchApplier {

    private final BackupManager backupManager;

    private byte[] originalBytes;          // the exact pre-patch bytes, kept in memory as a second, independent copy
    private Path patchedFile;
    private Path backupPath;

    /** Whether the most recent {@link #confirmAndApply} call created a verified backup (V4 audit/dashboard). */
    public boolean lastBackupCreated = false;
    /** Path of that backup, or {@code null} if none was created. */
    public Path lastBackupPath = null;
    /** SHA-256 of the exact pre-patch bytes of the most recently patched file (empty if nothing was applied). A restoration
     *  is only ever called verified when the file hashes to this again. */
    public String lastOriginalSha256 = "";
    /** SHA-256 of the exact bytes this class wrote (what the file must still hold for a restore to be safe). */
    public String lastPatchedSha256 = "";

    public PatchApplier() { this("test-output"); }

    public PatchApplier(String outDir) {
        this.backupManager = new BackupManager(outDir);
    }

    /** Shows the diff and asks for confirmation ON STDIN. Returns {@code true} if the user approved it (and it
     *  was applied). {@code confidence} (0.0-1.0, V2) is shown alongside the diff — a heuristic only, never a
     *  substitute for the human's own judgement of the reason and the diff itself.
     *
     *  <p>Used by the classic terminal-driven flow ({@code SelfHealingAgent}'s interactive single-test/{@code
     *  --all} loop). For the chat-driven flow, where the human approves in Claude Code chat rather than at a
     *  terminal, use {@link #confirmAndApply(Path, String, String, String, double, boolean)} instead — the
     *  underlying diff-check/backup/write logic is IDENTICAL; only how the approval decision arrives differs.</p> */
    public boolean confirmAndApply(Path file, String oldSnippet, String newSnippet, String reason, double confidence) throws IOException {
        printProposedFix(file, oldSnippet, newSnippet, reason, confidence);

        System.out.print("Apply this change? [y/N]: ");
        // V3 ABSOLUTE HUMAN APPROVAL SAFETY RULE: ONLY an exact "y" or "Y" applies the patch. Anything else —
        // "N"/"n", Enter (empty), "yes"/"YES", or any other input — must leave the source file untouched. Not
        // even "yes" is accepted: this is deliberately stricter than plain English "yes" so a single wrong
        // keystroke can never be mistaken for approval. No confidence value, however high, bypasses this.
        // This check is identical for EVERY proposal source — V1/V2 deterministic, V3 LLM, and V4 candidates
        // all funnel through this exact same prompt; there is no alternate path that skips it.
        String answer = new Scanner(System.in).nextLine().trim();
        boolean approved = "y".equals(answer) || "Y".equals(answer);
        return applyIfApproved(file, oldSnippet, newSnippet, approved);
    }

    /** Requirement (chat-driven approval) — the exact same diff-check/backup/write pipeline as the interactive
     *  overload above, but the approval decision is passed in explicitly as {@code approved} instead of being
     *  read from stdin. This exists so an integration layer (e.g. {@code SelfHealingAgent}'s {@code
     *  apply-pending} mode) can relay a human's decision from a DIFFERENT channel — a Claude Code chat message
     *  — into this the ONE place a source file is ever written, without ever needing a live stdin channel.
     *
     *  <p><b>Absolute rule for every caller of this method:</b> {@code approved} must be {@code true} if and
     *  only if a human gave an explicit, unambiguous, current affirmative answer to the exact proposal just
     *  shown — never inferred, never defaulted, never carried over from an earlier approval. This is exactly
     *  as strict as the stdin path's "only an exact y/Y" rule; the channel changed, the standard didn't.</p> */
    public boolean confirmAndApply(Path file, String oldSnippet, String newSnippet, String reason, double confidence,
                                    boolean approved) throws IOException {
        printProposedFix(file, oldSnippet, newSnippet, reason, confidence);
        System.out.println("Approval (from Claude Code chat): " + (approved ? "APPROVED" : "DECLINED"));
        return applyIfApproved(file, oldSnippet, newSnippet, approved);
    }

    private void printProposedFix(Path file, String oldSnippet, String newSnippet, String reason, double confidence) {
        System.out.println();
        System.out.println("==== Proposed fix ====");
        System.out.println("File       : " + file);
        System.out.printf("Confidence : %.0f%%%n", confidence * 100);
        System.out.println("Reason     : " + reason);
        System.out.println("---- before ----");
        for (String line : oldSnippet.split("\n", -1)) System.out.println("- " + line);
        System.out.println("---- after -----");
        for (String line : newSnippet.split("\n", -1)) System.out.println("+ " + line);
        System.out.println("=======================");
    }

    private boolean applyIfApproved(Path file, String oldSnippet, String newSnippet, boolean approved) throws IOException {
        lastBackupCreated = false;
        lastBackupPath = null;
        lastOriginalSha256 = "";
        lastPatchedSha256 = "";

        String content = Files.readString(file);
        int count = countOccurrences(content, oldSnippet);
        if (count != 1) {
            System.out.println("PatchApplier: expected exactly ONE occurrence of the target snippet, found " + count
                    + " — refusing to apply an ambiguous change.");
            return false;
        }

        if (!approved) {
            System.out.println("PatchApplier: not applied (declined).");
            return false;
        }

        // The exact pre-patch bytes. The text patch below is only safe if this file round-trips through UTF-8 unchanged,
        // otherwise writing it back could alter bytes outside the snippet - and a restore could not be exact.
        byte[] before = Files.readAllBytes(file);
        if (!java.util.Arrays.equals(before, content.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            System.out.println("PatchApplier: " + file.getFileName() + " does not round-trip exactly as UTF-8 text — refusing to patch it.");
            return false;
        }

        // V4 Step 7 — verified backup BEFORE any write. If it fails, the patch is not applied.
        BackupManager.BackupResult backup = backupManager.backup(file);
        if (!backup.success) {
            System.out.println("PatchApplier: backup FAILED (" + backup.reason + ") — refusing to apply the patch.");
            return false;
        }
        String beforeSha = PendingHeal.sha256Hex(before);
        if (!beforeSha.equals(backup.sha256)) {
            System.out.println("PatchApplier: the file changed while it was being backed up — refusing to apply the patch.");
            return false;
        }
        lastBackupCreated = true;
        lastBackupPath = backup.backupPath;
        System.out.println("Backup created: " + backup.backupPath);

        originalBytes = before;
        patchedFile = file;
        backupPath = backup.backupPath;
        lastOriginalSha256 = beforeSha;
        String patched = content.replaceFirst(Pattern.quote(oldSnippet), Matcher.quoteReplacement(newSnippet));
        Files.writeString(file, patched);
        lastPatchedSha256 = PendingHeal.sha256Hex(Files.readAllBytes(file));   // what is actually on disk now
        System.out.println("Patch applied: " + file);
        return true;
    }

    /** How a {@link #revertVerified()} ended. {@code verified} is true ONLY when the file's bytes were confirmed, by SHA-256,
     *  to equal the exact pre-patch bytes. */
    public static final class RestoreResult {
        public final boolean verified;
        /** BACKUP, MEMORY or ALREADY_RESTORED when verified; empty otherwise. */
        public final String method;
        /** Human-readable account of what was checked and found (always set). */
        public final String detail;
        /** SHA-256 of the file as it stands after the attempt (empty if unreadable). */
        public final String finalSha256;

        RestoreResult(boolean verified, String method, String detail, String finalSha256) {
            this.verified = verified; this.method = method; this.detail = detail; this.finalSha256 = finalSha256;
        }
    }

    /** Boolean form of {@link #revertVerified()}: {@code true} only if the restoration was verified. */
    public boolean revert() { return revertVerified().verified; }

    /**
     * Reverts the last applied patch and VERIFIES it against the exact pre-patch bytes (their SHA-256, recorded at apply time),
     * not against the backup file alone:
     * <ol>
     *   <li>nothing applied: not verified (nothing to restore);</li>
     *   <li>the file already equals the original (e.g. a person restored it): verified, nothing is written;</li>
     *   <li>the file is neither the patched nor the original bytes (changed by someone/something since the patch): NOT
     *       overwritten, not verified - the caller must hand it to a person;</li>
     *   <li>otherwise restore from the backup if it still holds the original, else from the in-memory copy; verified only if
     *       the re-read file hashes to the original.</li>
     * </ol>
     * Never deletes the backup. The applier's state is kept after a failure, so the revert can be retried.
     */
    public RestoreResult revertVerified() {
        if (patchedFile == null) return new RestoreResult(false, "", "nothing was applied, so there is nothing to restore", "");
        System.out.println("Restoring backup...");

        String current = sha(patchedFile);
        if (current != null && current.equals(lastOriginalSha256)) {
            finish();
            System.out.println("Patch reverted (the file already holds the exact original content).");
            return new RestoreResult(true, "ALREADY_RESTORED", "the file already matches the original content (SHA-256 verified); nothing was overwritten", current);
        }
        if (current != null && !current.equals(lastPatchedSha256)) {
            String detail = "the file was changed after the patch was applied (it matches neither the patched nor the original content), "
                    + "so it was NOT overwritten";
            System.out.println("PatchApplier: " + detail + " — manual review required: " + patchedFile);
            return new RestoreResult(false, "", detail, current);
        }

        String method = "";
        String detail = "";
        if (backupPath != null && backupManager.restore(backupPath, patchedFile, lastOriginalSha256)) {
            method = "BACKUP";
        } else {
            detail = "the backup was missing, unreadable or no longer matched the original; ";
            if (originalBytes != null) {
                try {
                    Files.write(patchedFile, originalBytes);
                    if (lastOriginalSha256.equals(sha(patchedFile))) method = "MEMORY";
                    else detail += "the in-memory restore could not be verified";
                } catch (IOException e) {
                    detail += "the in-memory restore failed (" + e.getMessage() + ")";
                    System.out.println("PatchApplier: in-memory fallback restore FAILED: " + e.getMessage());
                }
            } else {
                detail += "no in-memory copy was available";
            }
        }
        String after = sha(patchedFile);
        if (!method.isEmpty() && lastOriginalSha256.equals(after)) {
            finish();
            System.out.println("Backup restored.");
            System.out.println("Patch reverted.");
            return new RestoreResult(true, method, "restored from the " + (method.equals("BACKUP") ? "backup file" : "in-memory copy")
                    + " and verified against the original content (SHA-256)", after);
        }
        System.out.println("PatchApplier: FAILED to restore " + patchedFile + " — manual revert required.");
        return new RestoreResult(false, "", detail.isEmpty() ? "the restored file could not be verified against the original content" : detail, after == null ? "" : after);
    }

    private void finish() { patchedFile = null; originalBytes = null; backupPath = null; }

    /** SHA-256 of the file's current bytes, or {@code null} if it cannot be read. */
    private static String sha(Path f) {
        try { return PendingHeal.sha256Hex(Files.readAllBytes(f)); } catch (IOException | RuntimeException e) { return null; }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }
}
