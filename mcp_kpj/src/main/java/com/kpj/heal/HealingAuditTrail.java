package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * V4 Step 5 - durable, append-only audit history of every healing ATTEMPT (not just successful ones), at
 * {@code <outDir>/failures/healing-history.tsv}. Survives across runs (append, never overwritten/truncated) -
 * a genuinely different log from {@link FailureHistory}'s {@code history-<testId>.tsv} (simple PASS/FAIL per
 * step, written by {@code DevHisBase} for every test step regardless of healing). This class does not
 * duplicate that reading/writing logic; it is a separate, purpose-built log for healing DECISIONS
 * specifically, and {@code SelfHealingAgent} continues to use {@link FailureHistory} unchanged for the
 * existing V2 timing-corroboration feature.
 */
public class HealingAuditTrail {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Column order is load-bearing: HealingMemory reads specific indices (5=affectedFile, 6=affectedMethod,
    // 20=finalStatus) directly. oldSnippet/newSnippet were added at the END (23,24), and
    // semanticValidation/semanticJustification (25,26) likewise, precisely so those existing indices — and any
    // already-written rows from before this change — stay valid.
    private static final String[] COLUMNS = {
            "timestamp", "testId", "testClass", "step", "failure", "affectedFile", "affectedMethod", "source",
            "proposalKind", "candidate", "confidence", "risk", "rootCause", "humanDecision", "approval",
            "backupCreated", "patchApplied", "compileResult", "rerunResult", "regressionResult", "finalStatus",
            "reverted", "reason", "oldSnippet", "newSnippet", "semanticValidation", "semanticJustification",
            "restorationVerified", "backupPath", "originalSha256", "patchedSha256", "restorationMethod"
    };
    private static final int COL_RESTORATION_VERIFIED = 27, COL_BACKUP_PATH = 28, COL_ORIGINAL_SHA = 29, COL_PATCHED_SHA = 30, COL_METHOD = 31;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    // ---- redaction: the VALUE of an actual secret is replaced, never a whole cell because it merely mentions a word ----
    // (Redacting any cell containing "auth" / "credential" / "token" masked legitimate text such as the Failure Analysis
    // category "Authentication/Permission Issue", "credentials and login logic are never touched", or "authorizes".)

    private static final String SECRET_WORDS = "pass(?:word|wd)|pwd|secret|client[_-]?secret|(?:access|auth|refresh|api|session)[_-]?token"
            + "|token|api[_-]?key|access[_-]?key|private[_-]?key|authorization|set-cookie|cookie";
    /** {@code Bearer <token>}. */
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{10,}");
    /** A secret-named key assigned a value: {@code password = "x"}, {@code "apiKey": "x"}, {@code token=abc}. The key stays, the value goes. */
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(" + SECRET_WORDS + ")\\b([\"']?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;|&]+)");
    /** A secret-named field filled with a literal: {@code fill("#password", "Secret1")}. The field name stays, the literal goes. */
    private static final Pattern SECRET_ARGUMENT = Pattern.compile(
            "(?i)((?:" + SECRET_WORDS + ")[A-Za-z0-9_#.\\[\\]-]*[\"']?\\s*,\\s*)(\"[^\"]*\"|'[^']*')");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*");
    /** A long run of letters+digits with no separators - an opaque key or token even with no key name nearby. */
    private static final Pattern OPAQUE_RUN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9]{32,}(?![A-Za-z0-9])");
    private static final String REDACTED = "[REDACTED]";

    private final Path outDir;
    private final Path file;

    public HealingAuditTrail(String outDir) {
        this.outDir = Paths.get(outDir);
        this.file = this.outDir.resolve("failures").resolve("healing-history.tsv");
    }

    public void append(HealingAttemptRecord r) {
        try {
            Path dir = outDir.resolve("failures");
            Files.createDirectories(dir);
            boolean writeHeader = !Files.isRegularFile(file);
            if (!writeHeader) upgradeHeaderIfNeeded();
            StringBuilder sb = new StringBuilder();
            if (writeHeader) sb.append(String.join("\t", COLUMNS)).append('\n');

            String[] row = {
                    r.timestamp.format(TS), r.testId, r.testClass, r.step,
                    "expected=" + r.failureExpected + " | actual=" + r.failureActual,
                    r.affectedFile, r.affectedMethod, r.source, r.proposalKind,
                    r.candidateTotal > 0 ? (r.candidateIndex + "/" + r.candidateTotal) : "",
                    r.confidence >= 0 ? Math.round(r.confidence * 100) + "%" : "",
                    r.risk, r.rootCause, r.humanDecision,
                    r.humanDecision.equals("APPROVED") ? "y" : (r.humanDecision.equals("DECLINED") ? "declined" : ""),
                    String.valueOf(r.backupCreated), String.valueOf(r.patchApplied),
                    r.compileResult, r.targetStepResult, r.downstreamRegression, r.finalStatus,
                    String.valueOf(r.reverted), r.reason, r.oldSnippet, r.newSnippet,
                    r.semanticValidation, r.semanticJustification,
                    r.restorationVerified == null ? "" : String.valueOf(r.restorationVerified),
                    r.backupPath, r.originalSha256, r.patchedSha256, r.restorationMethod
            };
            // A bare SHA-256 is a content fingerprint, not a secret (and recovery needs it intact): it bypasses redaction.
            for (String cell : row) sb.append(cell != null && SHA256.matcher(cell).matches() ? cell : sanitize(cell)).append('\t');
            sb.setLength(sb.length() - 1); // drop trailing tab
            sb.append('\n');

            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("HealingAuditTrail: could not append audit record (" + e.getMessage() + ")");
        }
    }

    /** An audit file written before {@code restorationVerified} existed has a shorter header. The new column is only ever
     *  APPENDED at the end, so rewriting just that first line keeps every existing row and column index valid while letting
     *  header-based readers (the unified report) see the new column. Best-effort: on failure rows still align by index. */
    private void upgradeHeaderIfNeeded() {
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) return;
            String expected = String.join("\t", COLUMNS);
            String header = lines.get(0);
            if (header.equals(expected) || !expected.startsWith(header + "\t")) return; // current, or not a header we recognise
            lines.set(0, expected);
            Files.writeString(file, String.join("\n", lines) + "\n");
        } catch (IOException ignore) { /* appending still works; the new column is simply not named in the header */ }
    }

    /** Flattens a cell for TSV (no tabs/newlines) and redacts anything that looks like a secret, matching
     *  this project's existing convention of never persisting credentials to any output artifact. */
    static String sanitize(String s) {
        if (s == null) return "";
        String flat = s.replace('\t', ' ').replace("\r", " ").replace("\n", " ").trim();
        flat = BEARER.matcher(flat).replaceAll("Bearer " + REDACTED);
        flat = JWT.matcher(flat).replaceAll(java.util.regex.Matcher.quoteReplacement(REDACTED));
        flat = SECRET_ASSIGNMENT.matcher(flat).replaceAll(m ->
                java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2) + REDACTED));
        flat = SECRET_ARGUMENT.matcher(flat).replaceAll(m ->
                java.util.regex.Matcher.quoteReplacement(m.group(1) + REDACTED));
        flat = OPAQUE_RUN.matcher(flat).replaceAll(m -> {
            String run = m.group();
            boolean mixed = run.chars().anyMatch(Character::isDigit) && run.chars().anyMatch(Character::isLetter);
            boolean hex = run.matches("[0-9a-fA-F]{32,}");
            return java.util.regex.Matcher.quoteReplacement(mixed || hex ? REDACTED : run);
        });
        return flat;
    }

    List<String> columns() { return List.of(COLUMNS); }

    /** A prior REVERTED or REJECTED_VERIFICATION_FAILED attempt for the same (testId, step) - read back so the
     *  LLM fallback (and the semantic gate's own reasoning) can be told "this was already tried and didn't
     *  work", instead of risking the exact same bad candidate again with no memory of the earlier failure. */
    public static class PriorAttempt {
        public final String timestamp, finalStatus, reason, oldSnippet, newSnippet;
        PriorAttempt(String timestamp, String finalStatus, String reason, String oldSnippet, String newSnippet) {
            this.timestamp = timestamp; this.finalStatus = finalStatus; this.reason = reason;
            this.oldSnippet = oldSnippet; this.newSnippet = newSnippet;
        }
    }

    /** Requirement (context supplied to the reasoning layer) — "previous healing attempts for the same
     *  failure, previous rejected/reverted candidates". Reads the SAME append-only TSV this class already
     *  owns; never a second, parallel history file. */
    public List<PriorAttempt> priorAttempts(String testId, String step) {
        List<PriorAttempt> out = new ArrayList<>();
        if (!Files.isRegularFile(file)) return out;
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 1; i < lines.size(); i++) {
                String[] c = lines.get(i).split("\t", -1);
                if (c.length < 25 || !c[1].equals(testId) || !c[3].equals(step)) continue;
                if (!"REVERTED".equals(c[20]) && !"RESTORE_FAILED".equals(c[20]) && !c[20].startsWith("REJECTED")) continue;
                out.add(new PriorAttempt(c[0], c[20], c[22], c[23], c[24]));
            }
        } catch (IOException ignore) { }
        return out;
    }

    /**
     * A rollback that could not be verified leaves the source file possibly still patched. While that is true for
     * {@code target}, nothing else may be proposed or applied to it. Reads the same audit file: finds a {@code RESTORE_FAILED}
     * row for this file and checks the file's CURRENT text - a file that still shows the row's new text (and not its old text)
     * is unresolved; one that was restored (old text back) or reworked by hand is not, so the block lifts itself once a person
     * has dealt with the file. Snippets are compared ignoring whitespace (the audit stores them on one line); a snippet that was
     * redacted cannot be compared, so it is treated as unresolved.
     *
     * @return why the file is blocked, or {@code null} if no unresolved restore failure is recorded for it
     */
    public String unresolvedRestoreFailure(Path target) {
        if (!Files.isRegularFile(file)) return null;
        try {
            byte[] bytes = Files.readAllBytes(target);
            String currentSha = PendingHeal.sha256Hex(bytes);
            String current = squash(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            Path wanted = target.toAbsolutePath().normalize();
            List<String> lines = Files.readAllLines(file);
            for (int i = lines.size() - 1; i >= 1; i--) {   // newest first
                String[] c = lines.get(i).split("\t", -1);
                if (c.length < 25 || !"RESTORE_FAILED".equals(c[20])) continue;
                try {
                    if (!Paths.get(c[5]).toAbsolutePath().normalize().equals(wanted)) continue;
                } catch (java.nio.file.InvalidPathException e) { continue; }
                String backup = cell(c, COL_BACKUP_PATH);
                String hint = " - restore it from its backup" + (backup.isEmpty() ? "" : " (" + backup + ")") + " before any further change";
                String origSha = cell(c, COL_ORIGINAL_SHA), patchedSha = cell(c, COL_PATCHED_SHA);

                // Exact check first: recorded fingerprints of the pre-patch and patched files.
                if (SHA256.matcher(origSha).matches() && SHA256.matcher(patchedSha).matches()) {
                    if (currentSha.equals(origSha)) continue;   // byte-for-byte the original again: recovered
                    if (currentSha.equals(patchedSha)) {
                        return "an earlier rollback of " + target.getFileName() + " (" + c[0] + ") could not be verified and the file "
                                + "is still exactly the patched content" + hint;
                    }
                    // neither: fall through to the text comparison below (the file may have been edited around the change)
                }

                String oldText = squash(c[23]), newText = squash(c[24]);
                boolean unverifiable = newText.isEmpty() || newText.contains(REDACTED);
                boolean stillPatched = !unverifiable && current.contains(newText)
                        && (!current.contains(oldText) || newText.contains(oldText));
                if (unverifiable || stillPatched) {
                    return "an earlier rollback of " + target.getFileName() + " (" + c[0] + ") could not be verified and the file "
                            + (unverifiable ? "cannot be confirmed restored" : "still contains that change") + hint;
                }
            }
        } catch (IOException ignore) { /* an unreadable audit file or target cannot establish a block */ }
        return null;
    }

    /** The newest {@code RESTORE_FAILED} row recorded for {@code target}, or {@code null}: what a person needs to recover by hand. */
    public RestoreRecord latestRestoreFailure(Path target) {
        if (!Files.isRegularFile(file)) return null;
        try {
            Path wanted = target.toAbsolutePath().normalize();
            List<String> lines = Files.readAllLines(file);
            for (int i = lines.size() - 1; i >= 1; i--) {
                String[] c = lines.get(i).split("\t", -1);
                if (c.length < 25 || !"RESTORE_FAILED".equals(c[20])) continue;
                try {
                    if (!Paths.get(c[5]).toAbsolutePath().normalize().equals(wanted)) continue;
                } catch (java.nio.file.InvalidPathException e) { continue; }
                return new RestoreRecord(c[0], cell(c, COL_BACKUP_PATH), cell(c, COL_ORIGINAL_SHA), cell(c, COL_PATCHED_SHA));
            }
        } catch (IOException ignore) { }
        return null;
    }

    /** What the audit trail recorded about an unverified rollback (fingerprints are empty for rows written before they existed). */
    public static final class RestoreRecord {
        public final String timestamp, backupPath, originalSha256, patchedSha256;
        RestoreRecord(String timestamp, String backupPath, String originalSha256, String patchedSha256) {
            this.timestamp = timestamp; this.backupPath = backupPath; this.originalSha256 = originalSha256; this.patchedSha256 = patchedSha256;
        }
    }

    private static String cell(String[] c, int index) { return index < c.length ? c[index].trim() : ""; }

    private static String squash(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }
}
