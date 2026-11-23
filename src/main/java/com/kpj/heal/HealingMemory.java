package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Requirement 7 - reads the EXISTING {@code healing-history.tsv} (written by {@link HealingAuditTrail}) to
 * see whether a similar failure (same affected file + method) was healed successfully or reverted before.
 * Deliberately read-only and reuses the existing audit-trail file rather than persisting a separate "memory"
 * store — the audit trail already records everything a "knowledge base" needs once it also carries the
 * old/new snippet columns (added this pass).
 */
public class HealingMemory {

    public static class Summary {
        public final int timesKept;
        public final int timesReverted;
        /** Attempts that failed verification AND whose rollback could not be verified. Never counted as a revert or a success. */
        public final int timesRestoreFailed;

        Summary(int timesKept, int timesReverted) { this(timesKept, timesReverted, 0); }

        Summary(int timesKept, int timesReverted, int timesRestoreFailed) {
            this.timesKept = timesKept;
            this.timesReverted = timesReverted;
            this.timesRestoreFailed = timesRestoreFailed;
        }

        public boolean hasSuccessfulPrecedent() { return timesKept > 0; }
        /** A reverted OR restore-failed attempt is a failed precedent. */
        public boolean hasFailedPrecedent() { return timesReverted > 0 || timesRestoreFailed > 0; }
        public boolean hasAnyPrecedent() { return timesKept > 0 || timesReverted > 0 || timesRestoreFailed > 0; }
    }

    private final Path historyFile;

    public HealingMemory(String outDir) {
        this.historyFile = Paths.get(outDir, "failures", "healing-history.tsv");
    }

    /** Matches prior rows by the SAME (affectedFile, affectedMethod) — a file/method-level match, not
     *  requiring byte-identical snippets, since those can shift slightly (line numbers, whitespace) across
     *  otherwise-equivalent runs. Column indices (5=affectedFile, 6=affectedMethod, 20=finalStatus) match
     *  {@link HealingAuditTrail}'s column order. */
    public Summary forLocation(String affectedFile, String affectedMethod) {
        int kept = 0, reverted = 0, restoreFailed = 0;
        if (Files.isRegularFile(historyFile)) {
            try {
                List<String> lines = Files.readAllLines(historyFile);
                for (int i = 1; i < lines.size(); i++) { // row 0 is the header
                    String[] c = lines.get(i).split("\t", -1);
                    if (c.length < 21) continue;
                    String rowFile = c[5], rowMethod = c[6], finalStatus = c[20];
                    if (!rowFile.equals(affectedFile) || !rowMethod.equals(affectedMethod)) continue;
                    if ("KEPT".equals(finalStatus)) kept++;
                    else if ("REVERTED".equals(finalStatus)) reverted++;
                    else if ("RESTORE_FAILED".equals(finalStatus)) restoreFailed++;
                }
            } catch (IOException ignore) { /* memory is best-effort context, not a source of truth */ }
        }
        return new Summary(kept, reverted, restoreFailed);
    }
}
