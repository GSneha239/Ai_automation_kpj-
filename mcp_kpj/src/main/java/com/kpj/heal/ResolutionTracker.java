package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The shared, append-only link between the two logically-separate agents: the Failure Analysis Agent READS
 * this (via {@link #forSignature}) to populate Fixed By / Fixing Role / Fixed Date / Resolution / Retest
 * Result / Final Status on every report - defaulting to "Pending" for anything never resolved, and NEVER
 * inventing a value. The Self-Healing Agent is the ONLY writer ({@link #recordFix}), and only calls it after
 * a real patch was approved by a human, applied, compiled, re-verified, and KEPT.
 *
 * <p>Append-only, matching this codebase's audit-trail convention ({@link HealingAuditTrail}): a later row
 * for the same signature always wins when reading, but no row is ever edited or deleted in place.</p>
 */
public class ResolutionTracker {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static class Summary {
        public final String fixedBy, fixingRole, fixedDate, resolution, retestResult, finalStatus;

        Summary(String fixedBy, String fixingRole, String fixedDate, String resolution, String retestResult, String finalStatus) {
            this.fixedBy = fixedBy;
            this.fixingRole = fixingRole;
            this.fixedDate = fixedDate;
            this.resolution = resolution;
            this.retestResult = retestResult;
            this.finalStatus = finalStatus;
        }

        public static Summary pending() { return new Summary("Pending", "Pending", "", "", "", "OPEN"); }
    }

    private final Path file;

    public ResolutionTracker(String outDir) {
        this.file = Paths.get(outDir, "failure-analysis", "resolution-tracking.tsv");
    }

    /** Stable, collision-safe key for one (testId, step) pair - length-prefixed so no combination of values
     *  can collide, matching {@link HealingCandidate#signature}'s convention. */
    public static String signatureFor(String testId, String step) {
        return testId.length() + ":" + testId + ";" + step.length() + ":" + step;
    }

    /** The LAST (most recently appended) recorded resolution for this signature, or Pending if none exists. */
    public Summary forSignature(String signature) {
        if (!Files.isRegularFile(file)) return Summary.pending();
        try {
            List<String> lines = Files.readAllLines(file);
            Summary latest = null;
            for (int i = 1; i < lines.size(); i++) { // row 0 is the header
                String[] c = lines.get(i).split("\t", -1);
                if (c.length < 8 || !c[1].equals(signature)) continue;
                latest = new Summary(c[2], c[3], c[4], c[5], c[6], c[7]);
            }
            return latest != null ? latest : Summary.pending();
        } catch (IOException e) {
            return Summary.pending();
        }
    }

    /** Called ONLY by SelfHealingAgent, and only after a real, human-approved patch was applied, compiled,
     *  re-verified, and KEPT. Never called for a proposal that was declined, reverted, or never applied. */
    public void recordFix(String signature, String fixedBy, String fixingRole, String resolution,
                           String retestResult, String finalStatus) {
        try {
            Files.createDirectories(file.getParent());
            boolean writeHeader = !Files.isRegularFile(file);
            StringBuilder sb = new StringBuilder();
            if (writeHeader) {
                sb.append(String.join("\t", "timestamp", "signature", "fixedBy", "fixingRole", "fixedDate",
                        "resolution", "retestResult", "finalStatus")).append('\n');
            }
            String now = LocalDateTime.now().format(TS);
            String[] row = {now, signature, fixedBy, fixingRole, now, resolution, retestResult, finalStatus};
            for (int i = 0; i < row.length; i++) {
                sb.append(sanitize(row[i]));
                sb.append(i < row.length - 1 ? '\t' : '\n');
            }
            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("ResolutionTracker: could not record fix - " + e.getMessage());
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return HealingAuditTrail.sanitize(s.replace('\t', ' ').replace('\n', ' '));
    }
}
