package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * V3 Step 3 — the LLM fallback path. Called ONLY when {@link PatchProposer#propose} has already returned
 * {@code null} for this failure (every deterministic V1/V2 rule found nothing) AND an {@link LlmProvider} is
 * enabled — {@code SelfHealingAgent} enforces that ordering; this class does not call {@code PatchProposer}
 * itself.
 *
 * <p>Builds bounded multi-file context ({@link CodeContextBuilder}), sends it to the provider, then parses
 * and VALIDATES the structured response ({@link LlmResponseParser}, {@link LlmProposalValidator}) before
 * ever constructing a {@link PatchProposer.Proposal}. An invalid, unsafe, or out-of-scope response produces
 * {@code null} — exactly like a deterministic rule finding nothing, never a guess. The Proposal this returns
 * (when non-null) flows through the IDENTICAL show-RCA/diff/approve/apply/compile/rerun/revert pipeline as
 * every other proposal kind; this class never touches a source file itself — only {@link PatchApplier} does.</p>
 */
public class LlmFallbackProposer {

    private static final String SYSTEM_PROMPT =
            "You are a read-only root-cause analysis assistant for a Playwright Java UI test framework. "
            + "You NEVER execute anything and you NEVER modify files yourself — you only propose. "
            + "Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly this shape: "
            + "{\"rootCause\":\"...\",\"explanation\":\"...\",\"evidence\":[\"...\"],\"affectedFiles\":[\"...\"],"
            + "\"oldSnippet\":\"...\",\"newSnippet\":\"...\",\"suggestedFix\":\"...\",\"confidence\":0.0,\"risk\":\"LOW|MEDIUM|HIGH\","
            + "\"semanticJustification\":\"...\",\"expectedBehaviorAfterFix\":\"...\"}. "
            + "affectedFiles must contain exactly ONE file — the same Page Object file given in the prompt, never a "
            + "different or additional file. oldSnippet must be copied EXACTLY (verbatim, including whitespace) from "
            + "the source given to you, and must be a small, uniquely-identifying span — never the whole file, never "
            + "vague. semanticJustification must explain, in terms of BUSINESS MEANING (not string similarity), why "
            + "the old and new field/locator refer to the SAME concept, and must explicitly connect the fix to the "
            + "actual failure message given to you — never propose a field that belongs to a different business "
            + "concept than what actually failed, and never propose a fix the actual failure message contradicts. "
            + "expectedBehaviorAfterFix must describe the observable behaviour once the fix is applied. If you "
            + "cannot identify a safe, narrowly-scoped, semantically-justified fix from the given context, set "
            + "oldSnippet and newSnippet to empty strings and explain why in rootCause instead of guessing — do "
            + "NOT invent a fix merely because a field name looks similar.";

    private final LlmProvider provider;
    private final CodeContextBuilder contextBuilder = new CodeContextBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final LlmProposalValidator validator = new LlmProposalValidator();

    public LlmFallbackProposer(LlmProvider provider) {
        this.provider = provider;
    }

    /** @param faResult      Failure Analysis Agent's category/owner/eligibility for this SAME failure —
     *                       requirement (Self-Healing must use FA info as context). May be {@code null}.
     * @param priorAttempts  prior REVERTED/REJECTED attempts for this same (testId, step), so the LLM is told
     *                       what was already tried and didn't work, instead of risking the same bad candidate
     *                       again with no memory of the earlier failure. May be empty.
     * @return a validated LLM-sourced Proposal, or {@code null} if disabled, the call failed, or the
     *          response didn't pass {@link LlmProposalValidator}. */
    public PatchProposer.Proposal propose(String testClassName, CodeLocator.CodeLocation loc, FailureRecord failure,
                                           FailureCategoryClassifier.Result faResult,
                                           List<HealingAuditTrail.PriorAttempt> priorAttempts) {
        if (!provider.isEnabled()) return null;

        String context = contextBuilder.build(testClassName, loc, failure);
        String userPrompt = buildUserPrompt(testClassName, loc, failure, context, faResult, priorAttempts);

        String raw;
        try {
            raw = provider.complete(SYSTEM_PROMPT, userPrompt);
        } catch (IOException e) {
            System.out.println("LlmFallbackProposer: LLM call failed (" + e.getMessage() + ") — no proposal.");
            return null;
        }
        if (raw == null || raw.isBlank()) {
            System.out.println("LlmFallbackProposer: LLM returned no response — no proposal.");
            return null;
        }

        LlmResponseParser.ParsedLlmResponse resp = parser.parse(raw);
        LlmProposalValidator.Result validation = validator.validate(resp, loc.pageObjectFile, Paths.get("").toAbsolutePath());
        if (!validation.valid) {
            System.out.println("LlmFallbackProposer: LLM proposal REJECTED (" + validation.reason + ") — no proposal.");
            return null;
        }

        PatchProposer.Proposal.Risk risk;
        try {
            risk = PatchProposer.Proposal.Risk.valueOf(resp.risk.toUpperCase());
        } catch (Exception e) {
            risk = PatchProposer.Proposal.Risk.HIGH; // unknown/missing risk is never silently treated as low
        }

        // Fold the LLM's own semantic account into the reason/suggestedFix text the human sees - it never
        // bypasses SemanticValidator (applied uniformly afterwards, same as every other candidate), but it is
        // useful, attributable context for the human reviewer and for the report.
        StringBuilder reason = new StringBuilder(resp.explanation.isBlank() ? resp.suggestedFix : resp.explanation);
        if (!resp.semanticJustification.isBlank()) reason.append(" | Semantic justification: ").append(resp.semanticJustification);
        if (!resp.expectedBehaviorAfterFix.isBlank()) reason.append(" | Expected after fix: ").append(resp.expectedBehaviorAfterFix);

        return new PatchProposer.Proposal(
                PatchProposer.Proposal.Kind.LLM,
                resp.confidence,
                resp.oldSnippet,
                resp.newSnippet,
                reason.toString(),
                PatchProposer.Proposal.Source.LLM,
                resp.rootCause,
                resp.evidence,
                loc.pageObjectFile.toString(),
                resp.suggestedFix,
                risk);
    }

    private static String buildUserPrompt(String testClassName, CodeLocator.CodeLocation loc, FailureRecord failure,
                                           String context, FailureCategoryClassifier.Result faResult,
                                           List<HealingAuditTrail.PriorAttempt> priorAttempts) {
        StringBuilder sb = new StringBuilder();
        sb.append("A Playwright UI test failed. Deterministic pattern-based healing found no safe fix. ")
          .append("Analyse the failure and propose the SMALLEST possible source patch to ONE file only.\n\n");
        sb.append("Test class: ").append(testClassName).append('\n');
        sb.append("Failed step: ").append(failure.step).append('\n');
        sb.append("Expected: ").append(failure.expected).append('\n');
        sb.append("Actual: ").append(failure.actual).append('\n');
        sb.append("URL: ").append(failure.url).append('\n');
        sb.append("Located Page Object file (the ONLY file you may propose changing): ").append(loc.pageObjectFile).append('\n');
        sb.append("Located method: ").append(loc.methodName).append("()\n\n");
        if (faResult != null) {
            sb.append("Failure Analysis category: ").append(faResult.category.displayName()).append('\n');
            sb.append("Failure Analysis recommended owner: ").append(faResult.recommendedOwner).append('\n');
            sb.append("Failure Analysis eligibility: ").append(faResult.selfHealingEligible ? "eligible" : "NOT eligible")
              .append(" — ").append(faResult.eligibilityReason).append("\n\n");
        }
        if (priorAttempts != null && !priorAttempts.isEmpty()) {
            sb.append("Previous healing attempts for this SAME failed step (do NOT repeat a candidate below — it "
                    + "already failed verification or was rejected by a human):\n");
            for (HealingAuditTrail.PriorAttempt a : priorAttempts) {
                sb.append("- [").append(a.finalStatus).append("] old=\"").append(a.oldSnippet)
                  .append("\" new=\"").append(a.newSnippet).append("\" reason=\"").append(a.reason).append("\"\n");
            }
            sb.append('\n');
        }
        sb.append("T0 buttons: ").append(failure.buttons).append('\n');
        sb.append("Settled buttons: ").append(failure.settledButtons).append('\n');
        sb.append("T0 selects: ").append(failure.selects).append('\n');
        sb.append("Settled selects: ").append(failure.settledSelects).append('\n');
        sb.append("T0 headings: ").append(failure.headings).append('\n');
        sb.append("Settled headings: ").append(failure.settledHeadings).append('\n');
        sb.append("ng-click (T0): ").append(failure.ngClicks).append('\n');
        sb.append("Select option lists: ").append(failure.selectOptions).append("\n\n");
        sb.append(context).append('\n');
        sb.append("\nRespond with ONLY the structured JSON object described in the system prompt. ")
          .append("affectedFiles must contain EXACTLY the located Page Object file path above, and nothing else.");
        return sb.toString();
    }
}
