package com.kpj.heal;

import java.util.ArrayList;
import java.util.List;

/**
 * V4 Step 2 — combines a proposal's own match confidence with OTHER available evidence into a single
 * adaptive score, without inventing evidence that doesn't exist. Every component below is a weighted term;
 * a component whose evidence is UNAVAILABLE (no capture, no history, regression not yet run, ...) is simply
 * left OUT of the weighted average rather than assigned an invented neutral number — so the final score is
 * always an honest average of only what was actually observed.
 *
 * <p>This EXTENDS the existing V1/V2/V3 confidence values (from {@link PatchProposer.Proposal#confidence} /
 * the LLM's own self-reported confidence) rather than replacing them — that number is itself one of the
 * weighted inputs here, so the carefully-tuned Levenshtein-based V1/V2 confidence formulas are reused, not
 * duplicated or second-guessed. It is deliberately no longer the DOMINANT term: {@link SemanticValidator}'s
 * consistency score now carries equal or greater weight, so confidence is evidence-driven (semantic fit, DOM
 * evidence, failure-message/history/code-context consistency, regression/impact risk) rather than primarily
 * edit/string-similarity-driven.</p>
 *
 * <p>This score is display-only context for the human. It NEVER gates or auto-approves anything — approval
 * is always the same explicit y/Y prompt in {@link PatchApplier}, regardless of this result.</p>
 */
public class AdaptiveConfidenceScorer {

    public enum Level { HIGH, MEDIUM, LOW }

    public static class Result {
        public final double score;
        public final Level level;
        public final List<String> explanation;

        Result(double score, Level level, List<String> explanation) {
            this.score = score;
            this.level = level;
            this.explanation = explanation;
        }
    }

    /**
     * @param regressionPassed {@code null} if not yet known (pre-approval display), {@code true}/{@code false}
     *                          once the post-apply rerun has actually happened (post-hoc audit scoring).
     * @param impact           dependency/impact analysis result, or {@code null} if not computed.
     * @param priorHistory     prior healing outcomes for this same (file, method), or {@code null}/no
     *                         precedent if none exist yet.
     */
    public Result score(PatchProposer.Proposal proposal, FailureRecord failure, FailureHistory.StepHistory history,
                         Boolean regressionPassed, ImpactAnalyzer.Result impact, HealingMemory.Summary priorHistory,
                         SemanticValidator.Result semantic) {
        List<String> explanation = new ArrayList<>();
        double weightedSum = 0, totalWeight = 0;

        // 1. Base match confidence — locator/DOM similarity for deterministic rules, or the LLM's own
        //    self-reported confidence for an LLM proposal. Always available; the strongest single signal.
        boolean isLlm = proposal.source == PatchProposer.Proposal.Source.LLM;
        double w = 0.20;
        weightedSum += proposal.confidence * w;
        totalWeight += w;
        explanation.add((isLlm ? "LLM confidence" : "Locator/string similarity") + ": " + pct(proposal.confidence));

        // 1b. Semantic consistency (SemanticValidator) - the strongest single term now, deliberately outweighing
        //     raw string similarity above: this judges whether old/new actually mean the same thing, not just
        //     how similar their characters are. A candidate that failed semantic validation never reaches this
        //     method at all, so a low-but-passing score here still reflects a genuinely-considered risk.
        if (semantic != null) {
            w = 0.22;
            weightedSum += semantic.consistencyScore * w;
            totalWeight += w;
            explanation.add("Semantic consistency: " + pct(semantic.consistencyScore)
                    + (semantic.justification.isBlank() ? "" : " (" + semantic.justification + ")"));
        } else {
            explanation.add("Semantic consistency: not evaluated — neutral (not scored)");
        }

        // 2. DOM evidence presence.
        if (failure.hasDomCapture()) {
            w = 0.15;
            weightedSum += 0.85 * w;
            totalWeight += w;
            explanation.add("DOM evidence: strong (live capture available at failure time)");
        } else {
            explanation.add("DOM evidence: none captured — neutral (not scored)");
        }

        // 3. Failure history / repeated-failure frequency.
        if (history != null && history.total() > 0) {
            w = 0.15;
            if (proposal.kind == PatchProposer.Proposal.Kind.TIMING && history.isIntermittent()) {
                weightedSum += 0.90 * w;
                explanation.add("Historical failures: " + history.failCount + " of " + history.total()
                        + " run(s) — intermittent, corroborates a TIMING diagnosis");
            } else {
                weightedSum += 0.55 * w;
                explanation.add("Historical failures: " + history.failCount + " of " + history.total() + " run(s)");
            }
            totalWeight += w;
        } else {
            explanation.add("Historical failures: no prior data — neutral (not scored)");
        }

        // 4. Root-cause evidence quality — does the proposal actually carry a reason/evidence, or is it empty?
        boolean hasEvidence = isLlm ? !proposal.evidence.isEmpty() : (proposal.reason != null && !proposal.reason.isBlank());
        if (hasEvidence) {
            w = 0.10;
            weightedSum += 0.80 * w;
            totalWeight += w;
            explanation.add("Root cause evidence: provided (" + (isLlm ? proposal.evidence.size() + " item(s)" : "reason given") + ")");
        } else {
            explanation.add("Root cause evidence: none provided — neutral (not scored)");
        }

        // 5. Affected scope — always exactly one file by construction (LlmProposalValidator / CodeLocator).
        w = 0.10;
        weightedSum += 0.90 * w;
        totalWeight += w;
        explanation.add("Patch scope: single file (enforced)");

        // 6. Patch complexity/risk.
        String risk = deriveRisk(proposal);
        double riskScore = "LOW".equals(risk) ? 0.90 : "MEDIUM".equals(risk) ? 0.55 : 0.25;
        w = 0.10;
        weightedSum += riskScore * w;
        totalWeight += w;
        explanation.add("Patch risk: " + risk);

        // 7. Regression result — only known AFTER the post-apply rerun; absent during pre-approval display.
        if (regressionPassed != null) {
            w = 0.15;
            weightedSum += (regressionPassed ? 1.0 : 0.0) * w;
            totalWeight += w;
            explanation.add("Regression: " + (regressionPassed ? "passed" : "FAILED"));
        } else {
            explanation.add("Regression: not yet run — neutral (not scored)");
        }

        // 8. Dependency/impact — a widely-shared Page Object nudges confidence DOWN slightly (more surface
        //    area for something to go unexpectedly wrong), never up. Display-only; never expands patch scope.
        if (impact != null) {
            int n = impact.dependentCount();
            String label = n <= 1 ? "isolated (" + n + " dependent test file)"
                    : n <= 5 ? "moderate (" + n + " dependent test files)"
                    : "widely shared (" + n + " dependent test files)";
            double impactScore = n <= 1 ? 0.85 : n <= 5 ? 0.65 : 0.40;
            w = 0.08;
            weightedSum += impactScore * w;
            totalWeight += w;
            explanation.add("Dependency impact: " + label);
        } else {
            explanation.add("Dependency impact: not analyzed — neutral (not scored)");
        }

        // 9. Prior healing outcome for this same (file, method) — boosts confidence ONLY when prior evidence
        //    was actually successful; a prior revert is a genuine negative signal, never ignored or hidden.
        if (priorHistory != null && priorHistory.hasAnyPrecedent()) {
            w = 0.07;
            double priorScore;
            if (priorHistory.hasFailedPrecedent() && !priorHistory.hasSuccessfulPrecedent()) priorScore = 0.15;
            else if (priorHistory.hasSuccessfulPrecedent() && !priorHistory.hasFailedPrecedent()) priorScore = 0.90;
            else priorScore = 0.50; // mixed history — neither confirms nor rules out this attempt
            weightedSum += priorScore * w;
            totalWeight += w;
            explanation.add("Prior healing outcome: " + priorHistory.timesKept + " kept, "
                    + priorHistory.timesReverted + " reverted, " + priorHistory.timesRestoreFailed
                    + " with an unverified rollback for this method previously");
        } else {
            explanation.add("Prior healing outcome: no precedent — neutral (not scored)");
        }

        double score = totalWeight > 0 ? weightedSum / totalWeight : proposal.confidence;
        score = Math.max(0.0, Math.min(1.0, score));
        Level level = score >= 0.80 ? Level.HIGH : score >= 0.55 ? Level.MEDIUM : Level.LOW;
        return new Result(score, level, explanation);
    }

    /** Same coarse mapping {@link RootCauseAnalysis} uses for deterministic kinds; the LLM's own
     *  self-reported, already-validated risk for an LLM proposal. */
    private static String deriveRisk(PatchProposer.Proposal p) {
        if (p.source == PatchProposer.Proposal.Source.LLM) {
            return p.risk == null ? "HIGH" : p.risk.name();
        }
        switch (p.kind) {
            case LOCATOR:
            case TIMING:
                return "LOW";
            case WORKFLOW:
                return "MEDIUM";
            default:
                return "MEDIUM";
        }
    }

    private static String pct(double v) { return Math.round(v * 100) + "%"; }
}
