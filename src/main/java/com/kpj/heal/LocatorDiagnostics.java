package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * Requirement (distinguish missing-locator vs. interaction/data-loading failures) — when a LOCATOR/SELECTOR
 * -classified failure's referenced ng-model literal(s) are actually PRESENT in the live capture (so no
 * "literal genuinely absent" replacement even applies, or the one candidate {@link PatchProposer} found
 * didn't survive {@link SemanticValidator}), this walks the SAME evidence {@link PatchProposer} already has —
 * never a new DOM-capture channel, never a Page Object read, never a modification to it — and reports what IS
 * known: which referenced fields resolved, their live value/option-count, and how that compares to the
 * settled (few-seconds-later) capture.
 *
 * <p><b>Analysis only.</b> This class never returns anything shaped like a patch — there is no {@code
 * oldSnippet}/{@code newSnippet} anywhere in {@link Finding}, so there is nothing here {@link PatchApplier}
 * could ever apply even by accident. It becomes an auto-applicable change only if a SEPARATE, deterministic,
 * semantically-validated {@link PatchProposer.Proposal} is actually found — this class never manufactures
 * one itself.</p>
 */
public final class LocatorDiagnostics {

    private LocatorDiagnostics() {}

    /** Same literal shape {@link PatchProposer} recognises — a quoted "Namespace.Field" Angular ng-model
     *  reference, however the surrounding call is written. */
    private static final Pattern RAW_NG_MODEL_LITERAL =
            Pattern.compile("'([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)'");

    public static final class Finding {
        /** True when AT LEAST ONE referenced field's ng-model resolved LIVE — so at least part of this
         *  method's locator surface is fine, and the failure evidence is worth surfacing even if another
         *  referenced field is ALSO missing (a real, common mixed case: most of the form resolved correctly,
         *  one field didn't — the deterministic proposer's concern for that one field, this class's concern
         *  for reporting what the OTHERS actually show). */
        public final boolean anyPresent;
        /** True when at least one referenced field's ng-model did NOT resolve live — still a genuine
         *  locator-mismatch signal for {@link PatchProposer} to reason about, never silently dropped just
         *  because other fields on the same form are fine. */
        public final boolean anyMissing;
        /** {@link #anyPresent} with NO {@link #anyMissing} at all — every referenced field resolved live, so
         *  nothing here even hints at a locator problem; the failure is squarely an interaction/data-loading
         *  question. Convenience for callers that only want to route the "clean" case. */
        public final boolean pureInteraction;
        public final List<String> observations;

        Finding(boolean anyPresent, boolean anyMissing, List<String> observations) {
            this.anyPresent = anyPresent;
            this.anyMissing = anyMissing;
            this.pureInteraction = anyPresent && !anyMissing;
            this.observations = observations;
        }
    }

    /** @return {@code null} if the method source references no "Namespace.Field"-shaped ng-model literal at
     *          all — nothing this class can meaningfully observe. */
    public static Finding diagnose(String methodSource, FailureRecord failure) {
        if (methodSource == null || methodSource.isBlank() || failure == null) return null;

        LinkedHashSet<String> literals = new LinkedHashSet<>();
        Matcher m = RAW_NG_MODEL_LITERAL.matcher(methodSource);
        while (m.find()) literals.add(m.group(1));
        if (literals.isEmpty()) return null;

        Map<String, String> t0Selects = PatchProposer.splitPairs(failure.selects, ";", ":");
        Map<String, String> settledSelects = PatchProposer.splitPairs(failure.settledSelects, ";", ":");
        Map<String, List<String>> t0Options = PatchProposer.splitSelectOptions(failure.selectOptions);
        Map<String, List<String>> settledOptions = PatchProposer.splitSelectOptions(failure.settledSelectOptions);

        List<String> observations = new ArrayList<>();
        boolean anyPresent = false, anyMissing = false;

        for (String field : literals) {
            boolean presentT0 = t0Selects.containsKey(field) || t0Options.containsKey(field);
            if (!presentT0) {
                anyMissing = true;
                observations.add(field + ": NOT found in the live DOM at failure time (a candidate for a "
                        + "locator replacement — subject to semantic validation before it is ever proposed).");
                continue;
            }
            anyPresent = true;
            String t0Value = t0Selects.getOrDefault(field, "");
            int t0Count = t0Options.getOrDefault(field, List.of()).size();
            String settledValue = settledSelects.getOrDefault(field, "");
            int settledCount = settledOptions.getOrDefault(field, List.of()).size();

            StringBuilder line = new StringBuilder(field).append(": present live — selected value=\"")
                    .append(t0Value).append("\", ").append(t0Count).append(" option(s) at failure time");
            if (!settledValue.equals(t0Value) || settledCount != t0Count) {
                line.append("; settled (a few seconds later): value=\"").append(settledValue).append("\", ")
                        .append(settledCount).append(" option(s)");
            }
            observations.add(line.toString());
        }

        observations.add("Actual: \"" + safe(failure.actual) + "\"");
        observations.add("Expected: \"" + safe(failure.expected) + "\"");
        if (!safe(failure.buttons).isBlank()) observations.add("Live buttons captured: " + failure.buttons);
        if (!safe(failure.headings).isBlank()) observations.add("Live headings captured: " + failure.headings);
        if (!safe(failure.ngClicks).isBlank()) observations.add("Live ng-click handlers captured: " + failure.ngClicks);

        return new Finding(anyPresent, anyMissing, observations);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
