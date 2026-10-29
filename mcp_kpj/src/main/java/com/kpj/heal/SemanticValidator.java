package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * Requirement (semantic validation gate) — the safety gate EVERY candidate (deterministic OR LLM) must pass
 * BEFORE it is ever shown for human approval. Deliberately deterministic/rule-based, no LLM call here — this
 * is what keeps even an ENABLED LLM's own output honest, and is exactly what protects the pre-existing
 * deterministic path too (which previously had no defense against a locator swap that was merely
 * string-similar but semantically unrelated — see {@code SelfHealingAgent}'s class doc for the real examples
 * this was built to catch).
 *
 * <p>A rejection here never modifies anything — it just removes that ONE candidate from consideration,
 * exactly like {@link PatchQualityGuard} already does; it never proposes an alternative and never guesses.</p>
 *
 * <p><b>How it works:</b> isolates the actual changed span between {@code oldSnippet} and {@code newSnippet}
 * via {@link SemanticConcepts#diffCore}, classifies both sides into coarse "business concept" buckets via
 * {@link SemanticConcepts#conceptsFor} (the SAME dictionary {@link PatchProposer} now uses to guide candidate
 * generation, so the two never disagree), and rejects when the two sides share NO concept — the same
 * string-similarity-but-semantically-wrong pattern behind every real example this was built from
 * (SelectedMonth→LocationID, AppointmentDate→AppointmentTypeID, FirstName→KinStateID). It also cross-checks
 * the actual failure message for an explicit "please select/enter X" requirement the candidate doesn't
 * address at all.</p>
 */
public class SemanticValidator {

    public static class Result {
        public final boolean valid;
        public final String reason;          // why REJECTED — empty when valid
        public final String justification;   // why ACCEPTED — empty when rejected
        public final double consistencyScore; // 0.0-1.0 — adaptive-confidence INPUT only, never a gate bypass

        private Result(boolean valid, String reason, String justification, double consistencyScore) {
            this.valid = valid;
            this.reason = reason;
            this.justification = justification;
            this.consistencyScore = consistencyScore;
        }

        static Result pass(String justification, double score) { return new Result(true, "", justification, score); }
        static Result reject(String reason) { return new Result(false, reason, "", 0.0); }
    }

    private static final Pattern REQUIRED_FIELD_MSG =
            Pattern.compile("(?i)please\\s+(?:select|enter|choose|fill|provide)\\s+([a-z ]{2,30})");

    /** @param failure the failure this candidate is meant to fix — used for the message-contradiction check. */
    public Result validate(PatchProposer.Proposal proposal, FailureRecord failure) {
        if (proposal.oldSnippet == null || proposal.newSnippet == null
                || proposal.oldSnippet.isBlank() || proposal.newSnippet.isBlank()) {
            return Result.reject("missing old/new snippet — nothing to semantically validate");
        }
        if (proposal.oldSnippet.equals(proposal.newSnippet)) {
            return Result.reject("proposed change is a no-op (old and new snippet are identical)");
        }

        String[] core = SemanticConcepts.diffCore(proposal.oldSnippet, proposal.newSnippet);
        String oldCore = core[0].trim(), newCore = core[1].trim();

        if (newCore.isEmpty()) {
            return Result.reject("proposed change removes content without adding a replacement — refusing an unexplained deletion");
        }
        if (oldCore.isEmpty()) {
            // A pure addition (e.g. widening a button-text regex with a new alternative, or inserting a
            // bounded dismiss-click before a return) never REPLACES one field's meaning with another's —
            // there is no field-concept swap risk to evaluate.
            return Result.pass("adds new content without replacing or removing existing matched text — no semantic swap risk", 0.75);
        }

        boolean simpleTokenSwap = oldCore.length() <= 80 && newCore.length() <= 80
                && !oldCore.contains("\n") && !newCore.contains("\n");
        if (!simpleTokenSwap) {
            // A structural, multi-line change (TIMING poll insertion, WORKFLOW dismiss block, a large LLM
            // rewrite) isn't a simple "field A -> field B" swap - the business-concept comparison below
            // doesn't apply. PatchQualityGuard already bounds the size/shape of a change like this.
            return Result.pass("structural change, not a simple field/token swap — business-concept comparison not applicable", 0.6);
        }

        Set<String> oldConcepts = SemanticConcepts.conceptsFor(oldCore);
        Set<String> newConcepts = SemanticConcepts.conceptsFor(newCore);

        if (!oldConcepts.isEmpty() && !newConcepts.isEmpty() && Collections.disjoint(oldConcepts, newConcepts)) {
            return Result.reject("\"" + oldCore + "\" (" + oldConcepts + ") and \"" + newCore + "\" (" + newConcepts
                    + ") belong to different business concepts — this looks like a string-similarity coincidence, "
                    + "not a genuine rename of the same field");
        }

        String requiredConcept = extractRequiredConceptFromMessage(failure == null ? null : failure.actual);
        if (requiredConcept != null && !oldConcepts.isEmpty()
                && !oldConcepts.contains(requiredConcept) && !newConcepts.contains(requiredConcept)) {
            return Result.reject("the actual failure message points at a \"" + requiredConcept
                    + "\"-related requirement, but neither the old nor the proposed field relates to it — "
                    + "the candidate doesn't explain how it resolves the observed failure");
        }

        if (oldConcepts.isEmpty() && newConcepts.isEmpty()) {
            return Result.pass("no recognized business-concept keywords in either token — proceeding on DOM/locator "
                    + "evidence alone, since nothing here contradicts it", 0.55);
        }
        boolean sameConcepts = oldConcepts.equals(newConcepts);
        Set<String> shared = new LinkedHashSet<>(oldConcepts);
        shared.retainAll(newConcepts);
        return Result.pass("\"" + oldCore + "\" and \"" + newCore + "\" share the business concept " + shared,
                sameConcepts ? 0.9 : 0.75);
    }

    private static String extractRequiredConceptFromMessage(String actual) {
        if (actual == null) return null;
        Matcher m = REQUIRED_FIELD_MSG.matcher(actual);
        if (!m.find()) return null;
        Set<String> concepts = SemanticConcepts.conceptsFor(m.group(1));
        return concepts.isEmpty() ? null : concepts.iterator().next();
    }
}
