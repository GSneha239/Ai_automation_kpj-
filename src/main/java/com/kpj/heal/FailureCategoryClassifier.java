package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * Failure Analysis Agent - core categorization engine. Translates {@link FailureClassifier}'s 5 internal
 * technical categories (used only to gate automatic healing) into the 12 business-facing {@link
 * FailureCategory} values a project manager, QA tester, developer, or automation tester can act on without
 * reading a stack trace.
 *
 * <p>Deterministic, rule-based - no LLM, matching this codebase's "deterministic preferred" convention. Reuses
 * {@link FailureClassifier} (technical gating), {@link PatchProposer#proposeCandidates} (read-only analysis -
 * proposes nothing, applies nothing), and {@link ProtectedResourcePolicy} as raw evidence rather than
 * re-deriving any of that logic. This class NEVER proposes, approves, or applies a patch - it only reads and
 * reports. {@code Self-Healing Eligible} is display/routing information only; the real, safe decision of
 * whether to actually attempt a fix is still made independently and only by {@code SelfHealingAgent}'s own
 * unchanged pipeline when a human explicitly requests healing.</p>
 */
public class FailureCategoryClassifier {

    public static class Result {
        public final FailureCategory category;
        public final String rootCause;
        public final String evidenceAvailable;
        public final String recommendedOwner;
        public final String severity;
        public final String suggestedAction;
        public final boolean selfHealingEligible;
        public final String eligibilityReason;
        public final List<PatchProposer.Proposal> candidates;

        Result(FailureCategory category, String rootCause, String evidenceAvailable, String recommendedOwner,
               String severity, String suggestedAction, boolean selfHealingEligible, String eligibilityReason,
               List<PatchProposer.Proposal> candidates) {
            this.category = category;
            this.rootCause = rootCause;
            this.evidenceAvailable = evidenceAvailable;
            this.recommendedOwner = recommendedOwner;
            this.severity = severity;
            this.suggestedAction = suggestedAction;
            this.selfHealingEligible = selfHealingEligible;
            this.eligibilityReason = eligibilityReason;
            this.candidates = candidates;
        }
    }

    private static final Pattern AUTH_MSG = Pattern.compile("(?i)"
            + "login failed|authentication failed|invalid credentials|unauthorized|access is denied");
    private static final Pattern MISSING_DATA_MSG = Pattern.compile("(?i)"
            + "\\bnot found\\b|no (patient|record|rows?|data|appointment|matches?) found|no matching|empty result");
    private static final Pattern DATA_MISMATCH_MSG = Pattern.compile("(?i)"
            + "does not match|\\bmismatch\\b|incorrect value|unexpected value|value did not match|wrong value");
    private static final Pattern APP_BUG_MSG = Pattern.compile("(?i)"
            + "save failed|server returned|message not found|error occurred|internal server error|"
            + "unexpected (value|result)|incorrect (value|result)");

    private final FailureClassifier classifier;
    private final PatchProposer proposer;
    private final SemanticValidator semanticValidator;

    public FailureCategoryClassifier() {
        this.classifier = new FailureClassifier();
        this.proposer = new PatchProposer();
        this.semanticValidator = new SemanticValidator();
    }

    /** @param loc may be {@code null} (CodeLocator couldn't identify the responsible method). */
    public Result classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
        FailureClassifier.Classification technical = classifier.classify(failure, loc);
        String evidence = evidenceSummary(failure, loc);

        switch (technical.category) {
            case ENVIRONMENT_FILE_PATH:
                return new Result(FailureCategory.ENVIRONMENT_CONFIGURATION_ISSUE, technical.reason, evidence,
                        "DevOps / Environment Team", "HIGH",
                        "Check environment configuration (hardcoded/local file paths, environment variables, server config).",
                        false, "Environment/configuration issues are never automated - a machine-specific path is not something automatic healing can fix.",
                        List.of());

            case INFRASTRUCTURE_OR_AUTH_FAILURE: {
                String msg = safe(failure.expected) + " " + safe(failure.actual);
                if (AUTH_MSG.matcher(msg).find()) {
                    return new Result(FailureCategory.AUTHENTICATION_PERMISSION_ISSUE, technical.reason, evidence,
                            "Admin / Environment Team", "HIGH",
                            "Verify test account credentials, permissions, and counter/organization selection with the environment/admin team.",
                            false, "Authentication/permission failures are never automated - credentials and login logic are never touched.",
                            List.of());
                }
                return new Result(FailureCategory.NETWORK_INFRASTRUCTURE_ISSUE, technical.reason, evidence,
                        "DevOps / Environment Team", "HIGH",
                        "Check network connectivity, DNS, certificates, and server/database availability.",
                        false, "Network/infrastructure failures are never automated - no code change can fix a down server or network issue.",
                        List.of());
            }

            case VALIDATION_STATE_CHANGE:
                return new Result(FailureCategory.VALIDATION_FAILURE, technical.reason, evidence,
                        "Manual Tester / QA", "MEDIUM",
                        "Confirm whether this enabled/visible/required state change is a real application defect or a stale automation assumption, then route accordingly.",
                        false, "A state-check mismatch could equally be a real application defect - never auto-fixed without human judgement.",
                        List.of());

            case INSUFFICIENT_EVIDENCE:
                return new Result(FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION, technical.reason, evidence,
                        "Manual Tester / QA", "MEDIUM",
                        "Manually investigate - insufficient evidence (no DOM/URL capture and/or the responsible method could not be located) for automatic categorization.",
                        false, "Not enough evidence was captured to safely categorize or attempt a fix.",
                        List.of());

            case CANDIDATE_FOR_AUTO_HEAL:
                return classifyCandidate(failure, loc, evidence);

            default:
                return new Result(FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION, technical.reason, evidence,
                        "Manual Tester / QA", "MEDIUM", "Manually investigate this failure.", false,
                        "Unrecognized technical classification.", List.of());
        }
    }

    private Result classifyCandidate(FailureRecord failure, CodeLocator.CodeLocation loc, String evidence) {
        List<PatchProposer.Proposal> candidates = loc != null
                ? proposer.proposeCandidates(loc.methodSource, failure) : List.of();

        boolean protectedResource = loc != null && ProtectedResourcePolicy.isProtected(loc.pageObjectFile);
        if (protectedResource) {
            return new Result(FailureCategory.AUTOMATION_SCRIPT_ISSUE,
                    "A candidate fix may exist, but the affected file is a protected resource.", evidence,
                    "Automation Tester", "MEDIUM",
                    "Have an automation tester manually review and update this file - it is never auto-modified.",
                    false, "Target file is a protected resource (credentials/login/framework infrastructure) - never auto-modified.",
                    candidates);
        }

        String rejectedLocatorReason = null; // set below when a candidate exists but fails semantic validation -
                                              // reused as "candidate locator evidence" by DropdownDiagnostics.
        Optional<PatchProposer.Proposal> locatorHit = candidates.stream()
                .filter(p -> p.kind == PatchProposer.Proposal.Kind.LOCATOR).findFirst();
        if (locatorHit.isPresent()) {
            PatchProposer.Proposal p = locatorHit.get();
            // Requirement: never report "eligible: YES" for a candidate we already know Self-Healing's OWN
            // semantic gate will reject — that gap is exactly what let this agent say "eligible: YES" for the
            // bad SelectedMonth->LocationID / AppointmentDate->AppointmentTypeID matches while `heal` correctly
            // refused them. Same gate, same verdict, checked once, here, before eligibility is ever claimed.
            // This IS case A of the Dropdown Diagnostics taxonomy: missing/stale locator + strong (semantically
            // valid) replacement evidence -> eligible. Handled here, once, for every failure shape - not
            // re-decided inside DropdownDiagnostics.
            SemanticValidator.Result sv = semanticValidator.validate(p, failure);
            if (sv.valid) {
                return new Result(FailureCategory.LOCATOR_SELECTOR_FAILURE, p.reason, evidence,
                        "Automation Tester", "MEDIUM", "Review and update the locator to match the current UI.",
                        true, "A deterministic locator-mismatch fix was found (confidence " + pct(p.confidence)
                                + ") and passed semantic validation (" + sv.justification
                                + ") - eligible for automatic healing with human approval.", candidates);
            }
            rejectedLocatorReason = sv.reason;
            // Falls through — do NOT dead-end here just because a Proposal object exists. The interaction/
            // data-loading diagnostic path below, or the text/DOM heuristics after it, decide the real category.
        }

        Optional<PatchProposer.Proposal> timingHit = candidates.stream()
                .filter(p -> p.kind == PatchProposer.Proposal.Kind.TIMING).findFirst();
        if (timingHit.isPresent()) {
            PatchProposer.Proposal p = timingHit.get();
            return new Result(FailureCategory.AUTOMATION_SCRIPT_ISSUE, p.reason, evidence,
                    "Automation Tester", "MEDIUM", "Review the automation's wait/timing strategy for this step.",
                    true, "A deterministic timing/race-condition fix was found (confidence " + pct(p.confidence)
                            + ") - eligible for automatic healing with human approval.", candidates);
        }

        Optional<PatchProposer.Proposal> workflowHit = candidates.stream()
                .filter(p -> p.kind == PatchProposer.Proposal.Kind.WORKFLOW).findFirst();
        if (workflowHit.isPresent()) {
            PatchProposer.Proposal p = workflowHit.get();
            return new Result(FailureCategory.AUTOMATION_SCRIPT_ISSUE, p.reason, evidence,
                    "Automation Tester", "MEDIUM", "Review the automation's handling of a new confirmation dialog/workflow step.",
                    true, "A deterministic workflow-change fix was found (confidence " + pct(p.confidence)
                            + ") - eligible for automatic healing with human approval.", candidates);
        }

        // Requirement (Dropdown Failure diagnostics, cases B-E) - dropdown-shaped evidence gets the richer,
        // structured diagnosis (target control / ng-model / options / interaction result / dependent control /
        // validation message / candidate locator evidence) instead of the generic mixed-evidence dump below.
        // Case A (missing locator + a semantically-valid replacement) was already fully decided above, before
        // this is ever reached - this call can only land on B, C, D, or E.
        boolean dropdownShaped = !failure.selects.isBlank() || !failure.selectOptions.isBlank();
        if (loc != null && dropdownShaped) {
            DropdownDiagnostics.Finding dd = DropdownDiagnostics.diagnose(loc.methodSource, failure, candidates, rejectedLocatorReason);
            if (dd != null) {
                String enrichedEvidence = evidence + " | " + String.join(" | ", dd.lines());
                return new Result(FailureCategory.DROPDOWN_FAILURE, dd.conclusionText, enrichedEvidence,
                        "Automation Tester", "MEDIUM",
                        "Investigate using the Dropdown Diagnostics above - target control, interaction result, "
                        + "dependent data, and validation message.",
                        false, dd.conclusionText, candidates);
            }
        }

        // Requirement (distinguish "missing locator evidence" from "interaction/data-loading failure") - for
        // NON-dropdown-shaped failures (buttons/ng-click/headings only), when the located method references
        // field(s) that DO resolve live (so the locator itself is fine) but no safe replacement literal
        // applies, that is itself evidence the SELECTION/INTERACTION failed, not the locator - never worth a
        // fabricated locator patch. Attach the raw observations (never a patch) so a human has real evidence.
        if (loc != null) {
            LocatorDiagnostics.Finding diag = LocatorDiagnostics.diagnose(loc.methodSource, failure);
            if (diag != null && diag.anyPresent) {
                String enrichedEvidence = evidence + " | Diagnostic observations: " + String.join(" ; ", diag.observations);
                String rootCause = diag.pureInteraction
                        ? "The referenced locator(s) resolve in the live DOM, but the expected selection/value/"
                          + "state was not reached - consistent with an interaction or data-loading failure, not a locator mismatch."
                        : "Some referenced locator(s) resolve live and some do not - a mixed picture that does not "
                          + "support a single confident locator replacement; see diagnostic observations for what "
                          + "actually is/isn't present.";
                return new Result(FailureCategory.AUTOMATION_SCRIPT_ISSUE,
                        rootCause, enrichedEvidence, "Automation Tester", "MEDIUM",
                        "Investigate using the diagnostic observations above - which fields resolved, their live "
                        + "values, and where evidence is missing.",
                        false, "No safe, semantically-supported automatic fix applies here - manual investigation "
                        + "required (see diagnostic observations).", candidates);
            }
        }

        // No deterministic candidate found - fall back to text/DOM heuristics for the remaining business categories.
        String msg = safe(failure.expected) + " " + safe(failure.actual);

        if (MISSING_DATA_MSG.matcher(msg).find()) {
            return new Result(FailureCategory.MISSING_TEST_DATA,
                    "The expected record/data was not found by the application.", evidence,
                    "Manual Tester / QA", "MEDIUM", "Create or seed the required test data record, then re-run.",
                    false, "No automation code change should be attempted for missing test data.", candidates);
        }

        if (DATA_MISMATCH_MSG.matcher(msg).find() && !APP_BUG_MSG.matcher(msg).find()) {
            return new Result(FailureCategory.TEST_DATA_ISSUE,
                    "The test data used produced a value that does not match what was expected.", evidence,
                    "Manual Tester / QA", "MEDIUM", "Review and correct the test data value used for this test.",
                    false, "No automation code change should be attempted for a test-data value issue.", candidates);
        }

        boolean hasSelectEvidence = !failure.selects.isBlank() || !failure.selectOptions.isBlank();
        // The bare word "select"/"selected" also appears in messages about choosing a ROW or record ("No appointment
        // to select"), which say nothing about a dropdown - yet any screen with filter dropdowns has select evidence.
        // "dropdown"/"option" always count; "select" counts unless the message is a "no <thing> to select" statement.
        // A message that is neither falls through to the later rules and, failing those, to "Unknown / Needs Manual
        // Investigation" - the cause stays undetermined instead of being guessed.
        boolean dropdownWording = containsAny(msg, "dropdown", "option")
                || (containsAny(msg, "select") && !NOTHING_TO_SELECT.matcher(msg).find());
        if (hasSelectEvidence && dropdownWording) {
            return new Result(FailureCategory.DROPDOWN_FAILURE,
                    "A dropdown/select field did not resolve to the expected option.", evidence,
                    "Automation Tester", "MEDIUM", "Verify the dropdown's live options and update automation if the option list changed.",
                    false, "No deterministic automatic fix was found for this dropdown mismatch.", candidates);
        }

        if (APP_BUG_MSG.matcher(msg).find()) {
            return new Result(FailureCategory.APPLICATION_BUG,
                    "The application returned an error or a result inconsistent with the expected business outcome.", evidence,
                    "Developer", "HIGH", "Report to development - the application's behavior does not match the expected business rule.",
                    false, "This looks like an application-side defect, not an automation issue - never auto-fixed.", candidates);
        }

        // Full cross-check of SCRIPT vs LIVE PAGE (only here, after every more specific rule above has had its say, so no existing category
        // changes): what does the script look for, what did the page actually show, and is this a script miss or an application/state problem?
        if (loc != null && failure.hasDomCapture()) {
            ScriptVsDomDiagnostics.Finding sd = ScriptVsDomDiagnostics.diagnose(loc.methodSource, failure);
            if (sd != null) {
                String enrichedEvidence = evidence + " | " + String.join(" | ", sd.lines);
                switch (sd.verdict) {
                    case SCRIPT_STALE_LIKELY:
                        return new Result(FailureCategory.AUTOMATION_SCRIPT_ISSUE, "VERDICT: " + sd.headline, enrichedEvidence,
                                "Automation Tester", "MEDIUM", sd.suggestedAction,
                                false, "The script looks stale, but a similar label is not proof of the same function - a person must confirm the "
                                + "replacement control before any change; no automatic edit is proposed.", candidates);
                    case APPLICATION_OR_STATE_LIKELY:
                        return new Result(FailureCategory.APPLICATION_BUG, "VERDICT: " + sd.headline, enrichedEvidence,
                                "Developer / Manual Tester (confirm)", "MEDIUM", sd.suggestedAction,
                                false, "Not a script problem: the control is missing from the application itself or hidden by state/permission - "
                                + "a script edit would not help, so nothing is healed automatically.", candidates);
                    case SCRIPT_LOCATOR_OK:
                    default:
                        return new Result(FailureCategory.BUTTON_UI_FAILURE, "VERDICT: " + sd.headline, enrichedEvidence,
                                "Automation Tester", "MEDIUM", sd.suggestedAction,
                                false, "The script's locator is fine (its target is on the page), so changing the locator would not fix this; "
                                + "no deterministic automatic fix applies.", candidates);
                }
            }
        }

        boolean hasButtonEvidence = !failure.buttons.isBlank();
        if (hasButtonEvidence && containsAny(msg, "click", "button", "toast", "save")) {
            return new Result(FailureCategory.BUTTON_UI_FAILURE,
                    "A button/UI control interaction did not complete as expected.", evidence,
                    "Automation Tester", "MEDIUM", "Verify the button/control is present and enabled; check for an unhandled overlay or new dialog.",
                    false, "No deterministic automatic fix was found for this UI interaction.", candidates);
        }

        return new Result(FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION,
                "No deterministic rule matched this failure, and no clear text/DOM signal points to a specific category.", evidence,
                "Manual Tester / QA", "MEDIUM", "Manually investigate this failure.",
                false, "No confident automatic fix or clear category signal was found.", candidates);
    }

    /** "No <thing> to select" - says a list/grid had nothing to choose from; it is not evidence about a select control. */
    private static final Pattern NOTHING_TO_SELECT = Pattern.compile("(?i)\\bno\\s+[a-z ]{1,40}\\s+to\\s+select\\b");

    private static boolean containsAny(String haystack, String... needles) {
        String lower = haystack.toLowerCase();
        for (String n : needles) if (lower.contains(n)) return true;
        return false;
    }

    private static String evidenceSummary(FailureRecord failure, CodeLocator.CodeLocation loc) {
        List<String> parts = new ArrayList<>();
        if (!failure.url.isBlank()) parts.add("URL captured");
        if (failure.hasDomCapture()) parts.add("DOM capture (buttons/selects/headings) at failure time");
        if (!failure.settledButtons.isBlank() || !failure.settledSelects.isBlank() || !failure.settledHeadings.isBlank()) {
            parts.add("settled (few-seconds-later) DOM snapshot");
        }
        if (loc != null) parts.add("responsible Page Object method located: " + loc.pageObjectFile.getFileName() + " :: " + loc.methodName + "()");
        else parts.add("responsible Page Object method NOT located");
        return parts.isEmpty() ? "none captured" : String.join("; ", parts);
    }

    private static String pct(double v) { return Math.round(v * 100) + "%"; }

    private static String safe(String s) { return s == null ? "" : s; }
}
