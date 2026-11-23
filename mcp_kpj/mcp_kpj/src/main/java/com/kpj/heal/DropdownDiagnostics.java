package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * Requirement (Dropdown Failure diagnostics) — a purpose-built, richer analysis for the specific "Dropdown
 * Failure" shape, distinguishing WHY a dropdown-related step failed instead of collapsing every case into one
 * generic "not eligible" verdict. Reuses the exact same captured evidence every other agent in this package
 * reads ({@link FailureRecord#selects}/{@code selectOptions}/settled variants, plus {@link PatchProposer}'s
 * existing candidate search) — no new DOM-capture channel, no Page Object read beyond the already-located
 * method source, and this class NEVER proposes or applies anything itself.
 *
 * <p><b>Case A is handled entirely upstream</b> (in {@link FailureCategoryClassifier#classifyCandidate}, via
 * the existing {@code locatorHit}+{@link SemanticValidator} check) — this class is only ever invoked once that
 * check has already failed to find an eligible fix, so it never itself returns "eligible: YES"; its job is to
 * explain, with real evidence, which of cases B/C/D/E actually applies.</p>
 */
public final class DropdownDiagnostics {

    private DropdownDiagnostics() {}

    private static final Pattern RAW_NG_MODEL_LITERAL =
            Pattern.compile("'([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)'");
    private static final Pattern REQUIRED_FIELD_MSG =
            Pattern.compile("(?i)please\\s+(?:select|enter|choose|fill|provide)\\s+([a-z ]{2,30})");

    public enum Conclusion { B_INTERACTION_FAILURE, C_DEPENDENT_DATA_ISSUE, D_UNRELATED_VALIDATION_MESSAGE, E_INSUFFICIENT_EVIDENCE }

    private enum Role { MISSING, UNRESOLVED, NO_OPTIONS, RESOLVED }

    public static final class Finding {
        public final String targetControl;
        public final String ngModel;
        public final String locatorPresent;
        public final String optionsDetected;
        public final String selectedValue;
        public final String interactionResult;
        public final String dependentControl;
        public final String dependentData;
        public final String validationMessage;
        public final String candidateLocatorEvidence;
        public final Conclusion conclusion;
        public final String conclusionText;

        Finding(String targetControl, String ngModel, String locatorPresent, String optionsDetected,
                String selectedValue, String interactionResult, String dependentControl, String dependentData,
                String validationMessage, String candidateLocatorEvidence, Conclusion conclusion, String conclusionText) {
            this.targetControl = targetControl;
            this.ngModel = ngModel;
            this.locatorPresent = locatorPresent;
            this.optionsDetected = optionsDetected;
            this.selectedValue = selectedValue;
            this.interactionResult = interactionResult;
            this.dependentControl = dependentControl;
            this.dependentData = dependentData;
            this.validationMessage = validationMessage;
            this.candidateLocatorEvidence = candidateLocatorEvidence;
            this.conclusion = conclusion;
            this.conclusionText = conclusionText;
        }

        /** The exact structured template requested — "Label: value" pairs, in order, for a caller to either
         *  join into one evidence-string line (HTML-safe) or print one-per-line to the console. */
        public List<String> lines() {
            List<String> l = new ArrayList<>();
            l.add("Dropdown Diagnostics");
            l.add("Target control: " + targetControl);
            l.add("ng-model: " + ngModel);
            l.add("Locator present: " + locatorPresent);
            l.add("Options detected: " + optionsDetected);
            l.add("Selected value: " + selectedValue);
            l.add("Interaction result: " + interactionResult);
            l.add("Dependent control: " + dependentControl);
            l.add("Dependent data: " + dependentData);
            l.add("Validation/toast message: " + validationMessage);
            l.add("Candidate locator evidence: " + candidateLocatorEvidence);
            l.add("Diagnostic conclusion: " + conclusionText);
            return l;
        }
    }

    /**
     * @param rejectedCandidates deterministic LOCATOR candidates {@link FailureCategoryClassifier} already
     *                           found for this failure (possibly empty) — never recomputed here.
     * @param rejectedReason     why the best of those candidates failed {@link SemanticValidator}, or {@code
     *                           null} if none were found at all. Never recomputed here either.
     * @return {@code null} if the method source references no "Namespace.Field" ng-model literal at all —
     *         nothing this class can meaningfully diagnose.
     */
    public static Finding diagnose(String methodSource, FailureRecord failure,
                                    List<PatchProposer.Proposal> rejectedCandidates, String rejectedReason) {
        if (methodSource == null || methodSource.isBlank() || failure == null) return null;

        LinkedHashSet<String> literals = new LinkedHashSet<>();
        Matcher lm = RAW_NG_MODEL_LITERAL.matcher(methodSource);
        while (lm.find()) literals.add(lm.group(1));
        if (literals.isEmpty()) return null;

        Map<String, String> t0 = PatchProposer.splitPairs(failure.selects, ";", ":");
        Map<String, List<String>> t0Opts = PatchProposer.splitSelectOptions(failure.selectOptions);
        Map<String, String> settled = PatchProposer.splitPairs(failure.settledSelects, ";", ":");
        Map<String, List<String>> settledOpts = PatchProposer.splitSelectOptions(failure.settledSelectOptions);

        LinkedHashMap<String, Role> roles = new LinkedHashMap<>();
        for (String field : literals) {
            boolean present = t0.containsKey(field) || t0Opts.containsKey(field);
            if (!present) { roles.put(field, Role.MISSING); continue; }
            int optCount = t0Opts.getOrDefault(field, List.of()).size();
            if (optCount == 0) { roles.put(field, Role.NO_OPTIONS); continue; }
            roles.put(field, isResolved(t0.getOrDefault(field, "")) ? Role.RESOLVED : Role.UNRESOLVED);
        }

        String unresolvedField = firstWithRole(roles, Role.UNRESOLVED);
        String noOptionsField = firstWithRole(roles, Role.NO_OPTIONS);
        String missingField = firstWithRole(roles, Role.MISSING);
        String resolvedField = firstWithRole(roles, Role.RESOLVED);

        Conclusion conclusion;
        String conclusionText;
        String targetControl;

        if (unresolvedField != null) {
            conclusion = Conclusion.B_INTERACTION_FAILURE;
            targetControl = unresolvedField;
            conclusionText = "Case B - \"" + unresolvedField + "\" is present live and has option(s), but no "
                    + "value was selected/resolved - a selection/interaction failure, not a locator problem. "
                    + "No locator replacement is proposed for this.";
        } else if (noOptionsField != null) {
            conclusion = Conclusion.C_DEPENDENT_DATA_ISSUE;
            targetControl = noOptionsField;
            conclusionText = "Case C - \"" + noOptionsField + "\" is present live but currently has ZERO "
                    + "option(s) - a dependent-data/availability issue, not a locator mismatch. Not eligible "
                    + "for automatic healing.";
        } else if (missingField != null && resolvedField == null) {
            conclusion = Conclusion.E_INSUFFICIENT_EVIDENCE;
            targetControl = missingField;
            conclusionText = "Case E - \"" + missingField + "\" has no live match, no OTHER referenced control "
                    + "resolved either, and " + (rejectedReason == null ? "no candidate locator was found" : "the "
                    + "only candidate found was rejected (" + rejectedReason + ")") + " - insufficient evidence "
                    + "to safely propose a fix. Manual investigation required.";
        } else if (missingField != null) {
            // Missing, but at least one OTHER referenced control resolved correctly, and (by construction -
            // this class is only reached after that check) no semantically-supported replacement exists for
            // it - consistent with a dependent-data/business condition (the located method mostly works;
            // the missing piece isn't what's actually blocking the observed failure) rather than a broken
            // locator worth an automatic patch.
            conclusion = Conclusion.C_DEPENDENT_DATA_ISSUE;
            targetControl = resolvedField;
            conclusionText = "Case C - \"" + missingField + "\" has no live match and " + (rejectedReason == null
                    ? "no semantically-supported replacement was found"
                    : "its only candidate replacement was rejected (" + rejectedReason + ")")
                    + ", but \"" + resolvedField + "\" (and any other referenced control) resolved correctly - "
                    + "consistent with a dependent-data/business condition rather than a broken locator. Not "
                    + "eligible for automatic healing.";
        } else {
            String requiredThing = extractRequiredThing(failure.actual);
            boolean relatesToAnyField = requiredThing != null && roles.keySet().stream()
                    .anyMatch(f -> SemanticConcepts.shareConcept(SemanticConcepts.fieldPart(f), requiredThing));
            if (requiredThing != null && !relatesToAnyField) {
                conclusion = Conclusion.D_UNRELATED_VALIDATION_MESSAGE;
                targetControl = roles.keySet().iterator().next();
                conclusionText = "Case D - every referenced control resolved live, and the actual failure "
                        + "message points at \"" + requiredThing + "\" - unrelated to any referenced locator. "
                        + "Do not propose an unrelated locator change. Not eligible for automatic healing.";
            } else {
                conclusion = Conclusion.E_INSUFFICIENT_EVIDENCE;
                targetControl = roles.keySet().iterator().next();
                conclusionText = "Case E - every referenced control resolved live and the failure message does "
                        + "not clearly name an unrelated missing field - insufficient evidence to distinguish "
                        + "the real cause. Manual investigation required.";
            }
        }

        Role targetRole = roles.get(targetControl);
        String locatorPresent = targetRole == Role.MISSING ? "NO" : "YES";
        String optionsDetected = targetRole == Role.MISSING ? "N/A"
                : t0Opts.getOrDefault(targetControl, List.of()).size() + " option(s)"
                  + (settledOpts.containsKey(targetControl) && settledOpts.get(targetControl).size() != t0Opts.getOrDefault(targetControl, List.of()).size()
                        ? " (settled: " + settledOpts.get(targetControl).size() + ")" : "");
        String selectedValue = targetRole == Role.MISSING ? "N/A" : safe(t0.get(targetControl));
        String interactionResult = switch (targetRole) {
            case MISSING -> "N/A - locator not found live";
            case UNRESOLVED -> "No option was selected despite " + t0Opts.getOrDefault(targetControl, List.of()).size() + " being available";
            case NO_OPTIONS -> "Selection not possible - zero options available";
            case RESOLVED -> "Resolved to \"" + safe(t0.get(targetControl)) + "\"";
        };

        // Dependent control: the last OTHER referenced field, in source order — a cascading-dropdown heuristic
        // (department -> doctor style forms overwhelmingly reference the dependent field after the one it
        // depends on), not a real dependency graph; reported as exactly that, not asserted as certain.
        String dependentControl = "N/A (only one control referenced)";
        String dependentData = "N/A";
        List<String> others = new ArrayList<>(roles.keySet());
        others.remove(targetControl);
        if (!others.isEmpty()) {
            String dep = others.get(others.size() - 1);
            dependentControl = dep + " (heuristic: last other control referenced in source order, not a confirmed dependency graph)";
            Role depRole = roles.get(dep);
            dependentData = depRole == Role.MISSING ? "not present live"
                    : t0Opts.getOrDefault(dep, List.of()).size() + " option(s), value=\"" + safe(t0.get(dep)) + "\"";
        }

        String candidateLocatorEvidence = rejectedCandidates == null || rejectedCandidates.isEmpty()
                ? "No deterministic locator candidate was found for any referenced control."
                : rejectedCandidates.size() + " candidate(s) considered; best: \"" + rejectedCandidates.get(0).oldSnippet
                  + "\" -> \"" + rejectedCandidates.get(0).newSnippet + "\" - "
                  + (rejectedReason == null ? "not used" : "REJECTED (" + rejectedReason + ")");

        return new Finding(targetControl, targetControl, locatorPresent, optionsDetected, selectedValue,
                interactionResult, dependentControl, dependentData, safe(failure.actual), candidateLocatorEvidence,
                conclusion, conclusionText);
    }

    private static String firstWithRole(LinkedHashMap<String, Role> roles, Role role) {
        for (Map.Entry<String, Role> e : roles.entrySet()) if (e.getValue() == role) return e.getKey();
        return null;
    }

    private static boolean isResolved(String value) {
        return value != null && !value.isBlank() && !value.matches("(?i)^-*\\s*select\\s*-*$");
    }

    private static String extractRequiredThing(String actual) {
        if (actual == null) return null;
        Matcher m = REQUIRED_FIELD_MSG.matcher(actual);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "(empty)" : s; }
}
