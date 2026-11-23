package com.kpj.heal;

/**
 * Renders a single healing ATTEMPT as a readable, self-contained HTML block: a full facts table
 * ({@link #renderMeta}) plus the before/after diff ({@link #renderDiff}). No external JavaScript or CDN
 * libraries - plain HTML/CSS only, embedded by {@link HealingDashboard} in both the per-test individual
 * report and (in compact table-row form) the session summary report, so this is the ONE place attempt fields
 * are formatted for HTML - neither report re-derives or duplicates this rendering.
 *
 * <p>Source/free-text is ALWAYS passed through {@link HealingAuditTrail#sanitize} (the same redaction the
 * append-only audit trail already applies) before being HTML-escaped and placed inside a {@code <pre>}/table
 * cell - defence in depth alongside {@link PatchQualityGuard}, which already refuses to let an obviously
 * credential-shaped candidate reach this far in the first place.</p>
 */
public final class HtmlDiffRenderer {

    private HtmlDiffRenderer() {}

    /** Every fact captured for one healing attempt - the complete field list a report needs, in one table. */
    public static String renderMeta(HealingAttemptRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class='meta'>");
        row(sb, "Test Class", r.testClass);
        row(sb, "Test ID", r.testId);
        row(sb, "Run Timestamp", r.timestamp.toString());
        row(sb, "Failed Step", r.step);
        row(sb, "Failure", "expected=" + safe(r.failureExpected) + " | actual=" + safe(r.failureActual));
        row(sb, "Classification", r.classification);
        row(sb, "File", r.affectedFile);
        row(sb, "Method", r.affectedMethod);
        row(sb, "Root Cause", safe(r.rootCause));
        row(sb, "Evidence", r.evidence.isEmpty() ? "" : String.join(" | ", r.evidence.stream().map(HtmlDiffRenderer::safe).toList()));
        row(sb, "Candidate Count", r.candidateTotal > 0 ? String.valueOf(r.candidateTotal) : "0");
        row(sb, "Proposal Type", r.proposalKind);
        row(sb, "Proposal Source", r.source);
        row(sb, "Semantic Validation", r.semanticValidation
                + (r.semanticJustification.isBlank() ? "" : " — " + safe(r.semanticJustification)));
        row(sb, "Confidence", r.confidence >= 0 ? Math.round(r.confidence * 100) + "%" : "N/A");
        row(sb, "Adaptive Confidence", r.adaptiveScore != null
                ? Math.round(r.adaptiveScore * 100) + "% (" + r.adaptiveLevel + ")" : "N/A");
        row(sb, "Risk", r.risk.isEmpty() ? "N/A" : r.risk);
        row(sb, "Attempt", r.attemptNumber > 0 ? (r.attemptNumber + " of " + r.attemptsMax) : "N/A (no attempt budget consumed)");
        row(sb, "Human Approval", r.humanDecision);
        row(sb, "Backup Location", r.backupCreated ? r.backupPath : "not created");
        row(sb, "Compile Result", r.compileResult);
        row(sb, "Target Test Result", r.targetStepResult);
        row(sb, "Downstream Regression Result", r.downstreamRegression);
        row(sb, "Final Result", r.finalStatus);
        if (!r.reason.isBlank()) row(sb, "Reason", safe(r.reason));
        if (r.restorationVerified != null) row(sb, "Restoration Verified", r.restorationVerified ? "YES" : "NO");
        sb.append("</table>");
        return sb.toString();
    }

    public static String renderDiff(String oldSnippet, String newSnippet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='diff'>");
        sb.append("<div class='diff-col'><div class='diff-label before'>BEFORE</div><pre>")
          .append(esc(safe(oldSnippet))).append("</pre></div>");
        sb.append("<div class='diff-col'><div class='diff-label after'>AFTER</div><pre>")
          .append(esc(safe(newSnippet))).append("</pre></div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        String v = (value == null || value.isBlank()) ? "Not available" : value;
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(esc(v)).append("</td></tr>");
    }

    /** Redacts anything that looks like a secret before it's even escaped - reuses the exact same rule
     *  {@link HealingAuditTrail} already applies to the TSV audit trail, so a value never treated as sensitive
     *  in one place and exposed in the other. */
    private static String safe(String s) { return HealingAuditTrail.sanitize(s); }

    /** Standard HTML entity escaping - never render source as raw HTML. */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
