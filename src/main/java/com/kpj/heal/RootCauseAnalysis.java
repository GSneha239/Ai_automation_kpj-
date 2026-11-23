package com.kpj.heal;

import java.util.ArrayList;
import java.util.List;

/**
 * V3 Step 5 — an explicit root-cause analysis block, shown for EVERY proposal (deterministic or LLM) before
 * the diff and the approval prompt. For deterministic V1/V2 proposals, {@link #forDeterministic} derives it
 * from evidence the pipeline already has (no new capture, no guessing). For an LLM proposal, {@link #forLlm}
 * uses the LLM's own structured, already-validated fields. Never claims a root cause when the evidence
 * behind it is empty.
 *
 * <p>{@code impact} (added this pass) is a plain, pre-formatted string built by the caller from {@link
 * ImpactAnalyzer} — kept as a string rather than passing the analyzer's own type through, so this class
 * doesn't need to know anything about how impact is computed, only how to display it.</p>
 */
public class RootCauseAnalysis {

    public final String rootCause;
    public final List<String> evidence;
    public final String affectedMethod;
    public final String suggestedFix;
    public final double confidence;
    public final String risk;
    public final String impact;

    private RootCauseAnalysis(String rootCause, List<String> evidence, String affectedMethod,
                               String suggestedFix, double confidence, String risk, String impact) {
        this.rootCause = rootCause;
        this.evidence = evidence;
        this.affectedMethod = affectedMethod;
        this.suggestedFix = suggestedFix;
        this.confidence = confidence;
        this.risk = risk;
        this.impact = impact;
    }

    public static RootCauseAnalysis forDeterministic(PatchProposer.Proposal p, FailureRecord failure,
                                                       CodeLocator.CodeLocation loc, String impactSummary) {
        String rootCause;
        String risk;
        switch (p.kind) {
            case LOCATOR:
                rootCause = "Stale locator literal — the app's live UI no longer matches a string literal hardcoded in the automation code.";
                risk = "LOW";
                break;
            case TIMING:
                rootCause = "Timing/race condition — the field resolves correctly, just after the fixed wait the code allows for it.";
                risk = "LOW";
                break;
            case WORKFLOW:
                rootCause = "UI workflow change — a confirmation step/dialog now appears that this method doesn't yet handle.";
                risk = "MEDIUM";
                break;
            default:
                rootCause = "Automation code appears out of date with the live application.";
                risk = "MEDIUM";
        }
        List<String> evidence = new ArrayList<>();
        if (failure.expected != null && !failure.expected.isBlank()) evidence.add("Expected: " + failure.expected);
        if (failure.actual != null && !failure.actual.isBlank()) evidence.add("Actual: " + failure.actual);
        evidence.add(p.reason);
        String affectedMethod = loc != null
                ? (loc.pageObjectFile.getFileName() + " :: " + loc.methodName + "()")
                : "(unknown — method could not be located)";
        return new RootCauseAnalysis(rootCause, evidence, affectedMethod, p.reason, p.confidence, risk, impactSummary);
    }

    public static RootCauseAnalysis forLlm(PatchProposer.Proposal p, String impactSummary) {
        String rootCause = p.rootCause.isBlank() ? "(LLM did not provide a root cause)" : p.rootCause;
        List<String> evidence = p.evidence.isEmpty() ? List.of("(LLM did not provide supporting evidence)") : p.evidence;
        String affectedMethod = p.affectedFile.isBlank() ? "(unspecified)" : p.affectedFile;
        String suggestedFix = p.suggestedFix.isBlank() ? p.reason : p.suggestedFix;
        String risk = p.risk == null ? "HIGH" : p.risk.name();
        return new RootCauseAnalysis(rootCause, evidence, affectedMethod, suggestedFix, p.confidence, risk, impactSummary);
    }

    /** For a HUMAN_TRIAGE / non-actionable classification — no patch is ever proposed, so this explains
     *  clearly WHY automatic healing was blocked instead of showing a (nonexistent) suggested fix. */
    public static RootCauseAnalysis forTriage(String rootCause, List<String> evidence, String affectedMethod) {
        return new RootCauseAnalysis(rootCause, evidence, affectedMethod,
                "(none — routed to human triage, never auto-fixed)", -1, "N/A", "(not analyzed — no patch proposed)");
    }

    public void print() {
        System.out.println("Root Cause:");
        System.out.println(rootCause);
        System.out.println("Evidence:");
        for (String e : evidence) System.out.println("- " + e);
        System.out.println("Affected Method:");
        System.out.println(affectedMethod);
        System.out.println("Suggested Fix:");
        System.out.println(suggestedFix);
        System.out.println("Confidence:");
        System.out.println(confidence >= 0 ? Math.round(confidence * 100) + "%" : "N/A");
        System.out.println("Risk:");
        System.out.println(risk);
        System.out.println("Impact:");
        System.out.println(impact == null || impact.isBlank() ? "(not analyzed)" : impact);
    }
}
