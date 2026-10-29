package com.kpj.heal;

/**
 * V4 Step 3 - a common, source-agnostic wrapper around ONE proposed fix, so deterministic rule hits
 * ({@link PatchProposer#proposeCandidates}) and an LLM proposal ({@link LlmFallbackProposer}) can be ranked
 * and presented together as a list the human explicitly chooses from.
 *
 * <p>Deliberately a thin WRAPPER, not a copy: it carries the already-built, already-validated {@link
 * PatchProposer.Proposal} plus the {@link CodeLocator.CodeLocation} it came from, rather than duplicating
 * fields. Only {@code proposal.oldSnippet}/{@code newSnippet} and {@code loc.pageObjectFile} - identical to
 * what V1/V2/V3 already pass to {@link PatchApplier} - ever reach PatchApplier; a candidate is never itself
 * "applied".</p>
 */
public class HealingCandidate {

    public final PatchProposer.Proposal proposal;
    public final CodeLocator.CodeLocation loc;

    public HealingCandidate(PatchProposer.Proposal proposal, CodeLocator.CodeLocation loc) {
        this.proposal = proposal;
        this.loc = loc;
    }

    public PatchProposer.Proposal.Kind kind() { return proposal.kind; }
    public PatchProposer.Proposal.Source source() { return proposal.source; }
    public double confidence() { return proposal.confidence; }
    public String affectedMethod() { return loc != null ? loc.methodName + "()" : "(unknown)"; }
    public String affectedFile() { return loc != null ? loc.pageObjectFile.toString() : proposal.affectedFile; }

    /** Stable de-duplication/loop-protection key: same test, same step, same exact edit. Field lengths are
     *  interleaved into the key so no combination of ordinary field values can collide. */
    public String signature(String testId, String step) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{testId, step, proposal.oldSnippet, proposal.newSnippet}) {
            sb.append(part.length()).append(':').append(part).append(';');
        }
        return sb.toString();
    }
}
