package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * Step 4 (analyse &amp; propose) — rule-based, no LLM. Tries independent rule families against the located
 * Page Object method, in order:
 * <ol>
 *   <li><b>LOCATOR</b> (unchanged) — {@code clickButtonByText}/{@code clickButtonByTextPrefix}/
 *       {@code setSelectByNgModelAndText} string literals checked against the DOM captured AT failure
 *       time. A literal that no longer matches anything live is swapped for the closest match.</li>
 *   <li><b>TIMING</b> — a field that was absent/unresolved at failure time but resolved to a real value in
 *       the SETTLED capture (a few seconds later) points at a race, not a locator change. If the method
 *       reads that same field after a fixed {@code waitForAngular(n)}, that fixed wait is replaced with a
 *       bounded {@code page.waitForFunction(...)} poll on the field actually resolving.</li>
 *   <li><b>LOCATOR (raw JS)</b> — Page Objects overwhelmingly interact via hand-written {@code page.evaluate()}
 *       JS rather than the helpers above (confirmed: those helpers are defined in {@code BasePage} but never
 *       actually called anywhere in this codebase). Idioms recognised directly in the method's source text:
 *       an {@code ng-model} literal ({@code getAttribute('ng-model')==='Registration.Field'}), an ng-model
 *       PLUS a desired option-text literal passed together to a local JS helper ({@code setSelIfEmpty('ng.model',
 *       'Self')}) checked against a live captured option LIST (V2), a button-text regex alternation
 *       ({@code /^(save|yes|ok)$/i.test(...)}), an {@code ng-click} handler literal
 *       ({@code getAttribute('ng-click')==='fnSave()'}, V2), and an anchored panel/section title regex
 *       ({@code /^\s*visit information\s*$/i.test(...)}).</li>
 *   <li><b>WORKFLOW</b> — a confirmation-dialog button that appeared in the settled capture but wasn't there
 *       at failure time AND isn't already handled anywhere in the method — a likely new required dialog.
 *       Proposes a bounded, best-effort, no-op-if-absent dismiss click inserted right before the method
 *       returns. Confidence is boosted when a NEW panel/section heading also appeared alongside it (V2) —
 *       stronger, corroborating evidence of an actual workflow change rather than incidental UI noise.</li>
 * </ol>
 *
 * <p>Each rule family proposes a single, narrowly-scoped patch ({@code oldSnippet} -&gt; {@code newSnippet}
 * within the ONE located method) and nothing else, and attaches a heuristic {@code confidence} (never a
 * guarantee — always requires human approval). If none finds a confident fix, {@link #propose} returns
 * {@code null} and the caller reports "no confident fix" rather than guessing. (Failures where a patch is
 * never appropriate at all — hardcoded file paths, insufficient evidence, enabled/visible/required STATE
 * checks that could just as easily be a real application defect — are filtered out earlier, by
 * {@link FailureClassifier}, before this class is even called.)</p>
 *
 * <p><b>V3</b>: this class remains 100% deterministic — no LLM call happens here or anywhere it calls into.
 * When {@link #propose} returns {@code null}, the caller (only if an LLM provider is enabled) may separately
 * try {@link LlmFallbackProposer}, which returns the SAME {@link Proposal} type (kind {@code LLM}) through
 * the identical show-diff/approve/apply/compile/rerun/revert pipeline.</p>
 */
public class PatchProposer {

    public static class Proposal {
        /** V3 adds LLM as a fourth kind, alongside the three deterministic ones. */
        public enum Kind { LOCATOR, TIMING, WORKFLOW, LLM }
        /** V3 — who produced this proposal. Always DETERMINISTIC for every V1/V2 rule below. */
        public enum Source { DETERMINISTIC, LLM }
        /** V3 — coarse risk label shown alongside every proposal. */
        public enum Risk { LOW, MEDIUM, HIGH }

        public final Kind kind;
        /** Heuristic, 0.0-1.0 — how confident this rule is in its own proposal. NOT a guarantee of
         *  correctness; every proposal still requires an explicit human y/n approval regardless of this value. */
        public final double confidence;
        public final String oldSnippet, newSnippet, reason;

        /** V3 fields — populated only for LLM proposals (see {@link LlmFallbackProposer}). Every V1/V2 rule
         *  below uses the original 5-arg constructor, which defaults these; {@link RootCauseAnalysis} derives
         *  an equivalent display for deterministic proposals from kind/reason/confidence instead, so nothing
         *  here needed to change to add V3. */
        public final Source source;
        public final String rootCause;
        public final List<String> evidence;
        public final String affectedFile;
        public final String suggestedFix;
        public final Risk risk;

        /** V1/V2 constructor — UNCHANGED signature. Every deterministic rule in this class still calls this
         *  exact overload; none of those call sites needed to change for V3. */
        Proposal(Kind kind, double confidence, String oldSnippet, String newSnippet, String reason) {
            this(kind, confidence, oldSnippet, newSnippet, reason, Source.DETERMINISTIC, "", List.of(), "", "", null);
        }

        /** V3 constructor — used only by {@link LlmFallbackProposer}. */
        Proposal(Kind kind, double confidence, String oldSnippet, String newSnippet, String reason,
                 Source source, String rootCause, List<String> evidence, String affectedFile, String suggestedFix, Risk risk) {
            this.kind = kind;
            this.confidence = confidence;
            this.oldSnippet = oldSnippet;
            this.newSnippet = newSnippet;
            this.reason = reason;
            this.source = source;
            this.rootCause = rootCause == null ? "" : rootCause;
            this.evidence = evidence == null ? List.of() : evidence;
            this.affectedFile = affectedFile == null ? "" : affectedFile;
            this.suggestedFix = suggestedFix == null ? "" : suggestedFix;
            this.risk = risk;
        }
    }

    /** Literal must be within 40% edit distance of a live candidate (and not blank) to be proposed. */
    private static final double MAX_NORMALIZED_DISTANCE = 0.4;

    /** P1 fix (candidate-selection ordering) — how close a same-namespace candidate's spelling must be,
     *  relative to the target, to count as a "very close" match that {@link #closestStructural} prefers
     *  OUTRIGHT over business-concept matching. Derived from {@link #MAX_NORMALIZED_DISTANCE} (half of the
     *  general "acceptable edit-distance match" threshold already used throughout this class) rather than a
     *  new, unrelated magic number — a candidate this close is a spelling/case variant of the SAME field
     *  (e.g. "LocationId"/"Locationid", edit distance 0 case-insensitively; "Deparmentid"/"Departmentid",
     *  one dropped letter), not a coincidentally-similar different field. */
    private static final double NS_CLOSE_MATCH_THRESHOLD = MAX_NORMALIZED_DISTANCE / 2;

    /** How many lines above a field reference to look for the wait call that precedes it. */
    private static final int TIMING_SEARCH_WINDOW = 20;

    /** Bounded timeout for the polling fix this proposer generates — never an indefinite wait. */
    private static final int TIMING_POLL_TIMEOUT_MS = 5000;

    public Proposal propose(String methodSource, FailureRecord failure) {
        List<String> buttons = split(failure.buttons, "|");
        Map<String, String> selects = splitPairs(failure.selects, ";", ":"); // ngModel -> currently selected option text
        List<String> headings = split(failure.headings, "|");
        List<String> ngClicks = split(failure.ngClicks, "|");
        Map<String, List<String>> selectOptions = splitSelectOptions(failure.selectOptions); // ngModel -> ALL live option texts

        // ---- LOCATOR rules, helper-based (unchanged behaviour/output) ----
        Proposal p;
        if ((p = tryButtonCall(methodSource, "clickButtonByText", buttons, false)) != null) return p;
        if ((p = tryButtonCall(methodSource, "clickButtonByTextPrefix", buttons, true)) != null) return p;
        if ((p = trySelectNgModel(methodSource, selects)) != null) return p;

        // ---- TIMING rule — tried before raw-JS LOCATOR, so a field that will simply resolve on its own is
        // never misdiagnosed as renamed ----
        if ((p = proposeTimingFix(methodSource, failure)) != null) return p;

        // ---- LOCATOR rules, raw-JS ----
        if ((p = tryRawNgModelLiteral(methodSource, selects)) != null) return p;
        if ((p = tryDropdownOptionText(methodSource, selectOptions)) != null) return p;      // V2 (capability 2)
        if ((p = tryRawButtonRegex(methodSource, buttons)) != null) return p;
        if ((p = tryRawNgClickLiteral(methodSource, ngClicks)) != null) return p;             // V2 (capability 1)
        if ((p = tryRawPanelTitleRegex(methodSource, headings)) != null) return p;

        // ---- WORKFLOW rule — only tried once nothing else matched ----
        return proposeWorkflowFix(methodSource, failure);
    }

    /**
     * V4 Step 3 — collects EVERY rule's match (not just the first, priority-ordered one {@link #propose}
     * returns) and ranks them by confidence, for the "multiple fix candidates" UX. Deliberately additive and
     * separate from {@link #propose}: that method's short-circuit ORDER encodes real safety reasoning (e.g.
     * TIMING is tried before raw-JS LOCATOR specifically so a field that will simply resolve on its own is
     * never misdiagnosed as a rename) and its single-result behavior must not change for any existing
     * caller. This method reuses the exact same underlying rule checks — no rule logic is duplicated — it
     * only aggregates their results differently, for callers that want to show a human several options
     * instead of one.
     *
     * @return every rule match, highest confidence first. Empty (never {@code null}) if none matched.
     */
    public List<Proposal> proposeCandidates(String methodSource, FailureRecord failure) {
        List<String> buttons = split(failure.buttons, "|");
        Map<String, String> selects = splitPairs(failure.selects, ";", ":");
        List<String> headings = split(failure.headings, "|");
        List<String> ngClicks = split(failure.ngClicks, "|");
        Map<String, List<String>> selectOptions = splitSelectOptions(failure.selectOptions);

        List<Proposal> found = new ArrayList<>();
        addIfPresent(found, tryButtonCall(methodSource, "clickButtonByText", buttons, false));
        addIfPresent(found, tryButtonCall(methodSource, "clickButtonByTextPrefix", buttons, true));
        addIfPresent(found, trySelectNgModel(methodSource, selects));
        addIfPresent(found, proposeTimingFix(methodSource, failure));
        addIfPresent(found, tryRawNgModelLiteral(methodSource, selects));
        addIfPresent(found, tryDropdownOptionText(methodSource, selectOptions));
        addIfPresent(found, tryRawButtonRegex(methodSource, buttons));
        addIfPresent(found, tryRawNgClickLiteral(methodSource, ngClicks));
        addIfPresent(found, tryRawPanelTitleRegex(methodSource, headings));
        addIfPresent(found, proposeWorkflowFix(methodSource, failure));

        found.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        return found;
    }

    private static void addIfPresent(List<Proposal> list, Proposal p) {
        if (p != null) list.add(p);
    }

    // =======================================================================
    // LOCATOR healing — unchanged.
    // =======================================================================

    private Proposal tryButtonCall(String src, String fnName, List<String> buttons, boolean prefix) {
        if (buttons.isEmpty()) return null;
        Matcher m = Pattern.compile(Pattern.quote(fnName) + "\\s*\\(\\s*\"([^\"]*)\"\\s*\\)").matcher(src);
        while (m.find()) {
            String literal = m.group(1);
            if (stillValidButton(buttons, literal, prefix)) continue; // present in the live UI — not this failure's cause
            String best = closest(literal, buttons);
            double confidence;
            String evidence;
            if (best != null) {
                confidence = confidenceFromMatch(literal, best);
                evidence = "closest live button text is \"" + best + "\" (edit-distance match)";
            } else {
                // No candidate close enough by raw edit distance — try a structural, word-overlap match
                // instead (e.g. "Save Registration" -> "Save & Continue Registration") before giving up.
                best = closestByTokenOverlap(literal, buttons);
                if (best == null) continue;
                confidence = confidenceFromTokenOverlap(literal, best);
                evidence = "no button text is close by raw spelling, but \"" + best + "\" is the one uniquely "
                        + "sharing significant word(s) with \"" + literal + "\" among the currently visible buttons";
            }
            String oldSnippet = m.group(0);
            String newSnippet = oldSnippet.replaceFirst(Pattern.quote("\"" + literal + "\""), Matcher.quoteReplacement("\"" + best + "\""));
            return new Proposal(Proposal.Kind.LOCATOR, confidence, oldSnippet, newSnippet,
                    "No visible button matches \"" + literal + "\" in the current UI; " + evidence + ".");
        }
        return null;
    }

    private Proposal trySelectNgModel(String src, Map<String, String> selects) {
        if (selects.isEmpty()) return null;
        Matcher m = Pattern.compile("setSelectByNgModelAndText\\s*\\(\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*\\)").matcher(src);
        while (m.find()) {
            String ngModel = m.group(1);
            if (selects.containsKey(ngModel)) continue; // the select is still present — not this failure's cause
            List<String> candidates = new ArrayList<>(selects.keySet());
            String bestNgModel = closestStructural(ngModel, candidates);
            if (bestNgModel == null) continue;
            String oldSnippet = m.group(0);
            String newSnippet = oldSnippet.replaceFirst(Pattern.quote("\"" + ngModel + "\""), Matcher.quoteReplacement("\"" + bestNgModel + "\""));
            return new Proposal(Proposal.Kind.LOCATOR, confidenceFromStructural(ngModel, bestNgModel), oldSnippet, newSnippet,
                    "No <select ng-model=\"" + ngModel + "\"> found in the current UI; " + structuralExplanation(ngModel, bestNgModel));
        }
        return null;
    }

    private boolean stillValidButton(List<String> buttons, String literal, boolean prefix) {
        for (String b : buttons) {
            if (prefix ? b.toLowerCase().startsWith(literal.toLowerCase()) : b.equalsIgnoreCase(literal)) return true;
        }
        return false;
    }

    // =======================================================================
    // LOCATOR healing, raw-JS. Recognises the idioms Page Objects actually use (hand-written page.evaluate()
    // JS), checked against the SAME capture as everything else. Each rule requires the candidate span to be
    // unique within the method (defence in depth) — PatchApplier re-checks uniqueness against the whole file
    // before writing anything.
    // =======================================================================

    /** A quoted "Namespace.Field"-shaped literal — how an Angular ng-model reads wherever it appears in the
     *  raw JS, regardless of the surrounding call syntax (getAttribute('ng-model')===..., a local JS helper
     *  like setSel(...)/setSelIfEmpty(ng,txt), etc). */
    private static final Pattern RAW_NG_MODEL_LITERAL =
            Pattern.compile("'([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)'");

    /** An ng-model literal immediately followed by a second quoted literal — the "set this field to this
     *  option text" call shape, e.g. {@code setSelIfEmpty('Registration.receivabletypeid','Self')}. */
    private static final Pattern RAW_NG_MODEL_WITH_OPTION_TEXT =
            Pattern.compile("'([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)'\\s*,\\s*'([^']+)'");

    /** A button-text regex test, e.g. {@code /^(save|yes|ok)$/i.test(...)} or {@code /^cancel$/i.test(...)}. */
    private static final Pattern RAW_BUTTON_REGEX = Pattern.compile("/\\^\\(?([a-zA-Z|]+)\\)?\\$/i\\.test\\(");

    /** An ng-click handler literal, e.g. {@code getAttribute('ng-click')==='fnSave()'}. */
    private static final Pattern RAW_NG_CLICK_LITERAL =
            Pattern.compile("getAttribute\\(['\"]ng-click['\"]\\)\\s*===\\s*'([^']+)'");

    /** An anchored panel/section-title regex as it appears in the RAW SOURCE TEXT — the two backslashes in
     *  e.g. {@code /^\\s*visit information\\s*$/i} are literal characters in the .java file (Java's own
     *  string-literal escaping of a single {@code \s}), so this matches one-or-more literal backslashes
     *  rather than assuming exactly two. */
    private static final Pattern RAW_PANEL_TITLE_REGEX = Pattern.compile("/\\^\\\\+s\\*([^$]{2,60}?)\\\\+s\\*\\$/i");

    /**
     * Raw ng-model healing: only verifies the ng-model ATTRIBUTE itself against the live capture. The
     * desired option TEXT, if the same call also carries one, is left to {@link #tryDropdownOptionText} —
     * which has the live option-list evidence needed to check it safely.
     */
    private Proposal tryRawNgModelLiteral(String methodSource, Map<String, String> selects) {
        if (selects.isEmpty()) return null;
        String[] lines = methodSource.split("\n", -1);
        for (String line : lines) {
            Matcher m = RAW_NG_MODEL_LITERAL.matcher(line);
            while (m.find()) {
                String literal = m.group(1);
                if (selects.containsKey(literal)) continue; // present live — not this failure's cause
                String bestNgModel = closestStructural(literal, new ArrayList<>(selects.keySet()));
                if (bestNgModel == null) continue;
                if (countOccurrences(methodSource, line) != 1) continue; // ambiguous within the method — skip
                String newLine = line.replaceFirst(Pattern.quote("'" + literal + "'"), Matcher.quoteReplacement("'" + bestNgModel + "'"));
                return new Proposal(Proposal.Kind.LOCATOR, confidenceFromStructural(literal, bestNgModel), line, newLine,
                        "Raw ng-model match: no live element has ng-model=\"" + literal + "\"; "
                        + structuralExplanation(literal, bestNgModel));
            }
        }
        return null;
    }

    /**
     * V2 capability 2 — dropdown option/text healing. Unlike V1, the DOM capture now records each select's
     * FULL live option list (not just its currently-selected text), so a stale option-text literal can be
     * checked and healed directly — instead of being left unverified. Only acts when the ng-model itself IS
     * found live (an absent ng-model is {@link #tryRawNgModelLiteral}'s concern) and a live option LIST was
     * actually captured for it — no live list captured means no evidence, so nothing is proposed.
     */
    private Proposal tryDropdownOptionText(String methodSource, Map<String, List<String>> selectOptions) {
        if (selectOptions.isEmpty()) return null;
        String[] lines = methodSource.split("\n", -1);
        for (String line : lines) {
            Matcher m = RAW_NG_MODEL_WITH_OPTION_TEXT.matcher(line);
            while (m.find()) {
                String ngModel = m.group(1);
                String optionText = m.group(2);
                List<String> opts = selectOptions.get(ngModel);
                if (opts == null || opts.isEmpty()) continue;           // no live option-list evidence — do not guess
                if (containsIgnoreCase(opts, optionText)) continue;     // still a valid option — not this failure's cause
                String best = closest(optionText, opts);
                if (best == null) continue;
                if (countOccurrences(methodSource, line) != 1) continue;
                String newLine = line.replaceFirst(Pattern.quote("'" + optionText + "'"), Matcher.quoteReplacement("'" + best + "'"));
                return new Proposal(Proposal.Kind.LOCATOR, confidenceFromMatch(optionText, best), line, newLine,
                        "Dropdown option-text match: \"" + optionText + "\" is not among the " + opts.size()
                        + " live option(s) captured for ng-model=\"" + ngModel + "\"; closest live option is \""
                        + best + "\".");
            }
        }
        return null;
    }

    /**
     * Raw button-regex healing: when NONE of the regex's alternatives matches any visible button, proposes
     * ADDING the closest live button text as a new alternative rather than replacing the existing ones — the
     * old alternatives may still be valid for other UI states this method also has to handle, so removing
     * them is a bigger, less safe change than this rule is willing to make.
     */
    private Proposal tryRawButtonRegex(String methodSource, List<String> buttons) {
        if (buttons.isEmpty()) return null;
        String[] lines = methodSource.split("\n", -1);
        for (String line : lines) {
            Matcher m = RAW_BUTTON_REGEX.matcher(line);
            while (m.find()) {
                List<String> alts = Arrays.asList(m.group(1).split("\\|"));
                if (alts.stream().anyMatch(a -> containsIgnoreCase(buttons, a))) continue; // still matches something live

                String bestButton = null;
                double bestDist = Double.MAX_VALUE;
                for (String a : alts) {
                    for (String b : buttons) {
                        if (b.isBlank()) continue;
                        double d = normalizedLevenshtein(a, b);
                        if (d < bestDist) { bestDist = d; bestButton = b; }
                    }
                }
                if (bestButton == null || bestDist > MAX_NORMALIZED_DISTANCE) continue;

                String oldMatched = m.group(0);
                if (countOccurrences(line, oldMatched) != 1 || countOccurrences(methodSource, line) != 1) continue;
                String newMatched = "/^(" + m.group(1) + "|" + bestButton + ")$/i.test(";
                String newLine = line.replaceFirst(Pattern.quote(oldMatched), Matcher.quoteReplacement(newMatched));

                return new Proposal(Proposal.Kind.LOCATOR, confidenceFromDistance(bestDist), line, newLine,
                        "Raw button-text match: none of (" + m.group(1) + ") matches any visible button; closest "
                        + "live button text is \"" + bestButton + "\". Proposing to ADD it as a new alternative "
                        + "(the existing alternatives are kept, in case they still apply to other states this "
                        + "method handles).");
            }
        }
        return null;
    }

    /**
     * V2 capability 1 — ng-click locator healing. Buttons/links in this codebase are overwhelmingly matched
     * by their {@code ng-click} handler string, not by visible text — confirmed the dominant click idiom
     * (46+ occurrences of this exact {@code getAttribute('ng-click')===...} shape vs. zero uses of
     * innerText-equality button matching anywhere in the Page Objects).
     */
    private Proposal tryRawNgClickLiteral(String methodSource, List<String> ngClicks) {
        if (ngClicks.isEmpty()) return null;
        String[] lines = methodSource.split("\n", -1);
        for (String line : lines) {
            Matcher m = RAW_NG_CLICK_LITERAL.matcher(line);
            while (m.find()) {
                String literal = m.group(1);
                if (ngClicks.contains(literal)) continue; // present live — not this failure's cause
                String best = closest(literal, ngClicks);
                double confidence;
                String evidence;
                if (best != null) {
                    confidence = confidenceFromMatch(literal, best);
                    evidence = "closest live ng-click handler is \"" + best + "\" (edit-distance match)";
                } else {
                    // Handler names often gain/lose a qualifying word (fnSave() -> fnSaveVisit()) without
                    // becoming textually "close" by raw edit distance - a shared significant word is still
                    // strong, explainable structural evidence of the same underlying action.
                    best = closestByTokenOverlap(literal, ngClicks);
                    if (best == null) continue;
                    confidence = confidenceFromTokenOverlap(literal, best);
                    evidence = "no ng-click handler is close by raw spelling, but \"" + best + "\" is the one "
                            + "uniquely sharing significant word(s) with \"" + literal + "\" among the currently live handlers";
                }
                if (countOccurrences(methodSource, line) != 1) continue;
                String newLine = line.replaceFirst(Pattern.quote("'" + literal + "'"), Matcher.quoteReplacement("'" + best + "'"));
                return new Proposal(Proposal.Kind.LOCATOR, confidence, line, newLine,
                        "Raw ng-click match: no visible element has ng-click=\"" + literal + "\"; " + evidence + ".");
            }
        }
        return null;
    }

    private Proposal tryRawPanelTitleRegex(String methodSource, List<String> headings) {
        if (headings.isEmpty()) return null;
        String[] lines = methodSource.split("\n", -1);
        for (String line : lines) {
            Matcher m = RAW_PANEL_TITLE_REGEX.matcher(line);
            while (m.find()) {
                String title = m.group(1).trim();
                if (containsIgnoreCase(headings, title)) continue; // still matches a live heading
                String best = closest(title, headings);
                if (best == null) continue;
                if (countOccurrences(line, title) < 1 || countOccurrences(methodSource, line) != 1) continue;
                String newLine = line.replaceFirst(Pattern.quote(title), Matcher.quoteReplacement(best));
                return new Proposal(Proposal.Kind.LOCATOR, confidenceFromMatch(title, best), line, newLine,
                        "Raw panel/section-title match: no visible heading matches \"" + title + "\"; closest live "
                        + "heading is \"" + best + "\".");
            }
        }
        return null;
    }

    // =======================================================================
    // TIMING healing.
    // =======================================================================

    /** Base confidence for a TIMING proposal — the safety gate already requires the settled value to be
     *  genuinely resolved (not blank, not a placeholder), which is strong evidence on its own. */
    private static final double TIMING_BASE_CONFIDENCE = 0.80;

    /**
     * Looks for a select field that was NOT resolved (missing, blank, or a "--Select--"-style placeholder)
     * in the failure-time capture, but IS resolved to a real value in the settled (few-seconds-later)
     * capture — i.e. an actual race, not a permanently broken/renamed field. Only then does it look for a
     * fixed {@code waitForAngular(n)} in the method that precedes the code reading that same field.
     */
    private Proposal proposeTimingFix(String methodSource, FailureRecord failure) {
        Map<String, String> t0 = splitPairs(failure.selects, ";", ":");
        Map<String, String> settled = splitPairs(failure.settledSelects, ";", ":");
        if (settled.isEmpty()) return null;

        for (Map.Entry<String, String> e : settled.entrySet()) {
            String ngModel = e.getKey();
            String settledVal = e.getValue();

            // Safety requirement: the delayed snapshot must show the field actually RESOLVED — not just
            // "different text" — before a timing fix is even considered.
            if (!isResolvedValue(settledVal)) continue;
            if (isResolvedValue(t0.get(ngModel))) continue; // already fine at failure time — not this failure's cause
            if (!methodSource.contains(ngModel)) continue;  // this method doesn't reference that field at all

            Proposal p = buildWaitPollProposal(methodSource, ngModel, settledVal);
            if (p != null) return p;
        }
        return null;
    }

    /** A field is "resolved" the same way the framework's own code judges it elsewhere: non-blank and not
     *  a "--Select--"/"-Select-" style placeholder. */
    private static boolean isResolvedValue(String value) {
        return value != null && !value.isBlank() && !value.matches("(?i)^-*\\s*select\\s*-*$");
    }

    /**
     * Builds a TIMING proposal: {@code oldSnippet} spans from the nearest preceding {@code waitForAngular(n);}
     * through the line that reads {@code ngModel} (inclusive) — wide enough to be uniquely identifiable in
     * the file, per {@link PatchApplier}'s exact-occurrence check. {@code newSnippet} is identical except the
     * wait call is replaced with a bounded {@code page.waitForFunction(...)} poll on that field resolving.
     */
    private Proposal buildWaitPollProposal(String methodSource, String ngModel, String settledVal) {
        String[] lines = methodSource.split("\n", -1);

        int fieldLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(ngModel)) { fieldLine = i; break; }
        }
        if (fieldLine < 0) return null;

        Pattern waitPat = Pattern.compile("waitForAngular\\s*\\(\\s*\\d+\\s*\\)\\s*;");
        int waitLine = -1;
        String indent = "";
        for (int i = fieldLine; i >= Math.max(0, fieldLine - TIMING_SEARCH_WINDOW); i--) {
            if (waitPat.matcher(lines[i]).find()) {
                waitLine = i;
                Matcher ind = Pattern.compile("^(\\s*)").matcher(lines[i]);
                if (ind.find()) indent = ind.group(1);
                break;
            }
        }
        if (waitLine < 0) return null; // no fixed wait found to replace — nothing safe to propose

        StringBuilder oldSnippet = new StringBuilder();
        for (int i = waitLine; i <= fieldLine; i++) {
            oldSnippet.append(lines[i]);
            if (i < fieldLine) oldSnippet.append('\n');
        }
        String oldSnippetStr = oldSnippet.toString();

        // Safety: the wait line's exact text must appear exactly once inside the span we're about to patch —
        // if it doesn't (e.g. the same fixed-duration call repeats), refuse rather than risk an ambiguous edit.
        // PatchApplier re-checks uniqueness against the WHOLE FILE before writing anything.
        if (countOccurrences(oldSnippetStr, lines[waitLine]) != 1) return null;

        String pollBlock = indent + "try {\n"
                + indent + "    page.waitForFunction(\n"
                + indent + "            \"() => { const e=[...document.querySelectorAll('select,input')].find(x=>(x.getAttribute('ng-model')||'')==='" + ngModel + "');\"\n"
                + indent + "            + \" if(!e) return false; if(e.tagName!=='SELECT') return !!(e.value||'').trim();\"\n"
                + indent + "            + \" const o=e.options[e.selectedIndex]; const t=o?(o.textContent||'').trim():'';\"\n"
                + indent + "            + \" return !!(o && o.value && !/^-*\\\\s*select\\\\s*-*$/i.test(t)); }\",\n"
                + indent + "            null, new Page.WaitForFunctionOptions().setTimeout(" + TIMING_POLL_TIMEOUT_MS + "));\n"
                + indent + "} catch (Exception ignore) { System.out.println(\"selfHealPoll: " + ngModel + " did not resolve in time\"); }";

        String newSnippetStr = oldSnippetStr.replaceFirst(Pattern.quote(lines[waitLine]), Matcher.quoteReplacement(pollBlock));

        String reason = "Field \"" + ngModel + "\" was not resolved when this step failed, but resolved to \""
                + settledVal + "\" in the settled (few-seconds-later) capture — replacing the fixed wait with a "
                + "bounded poll (timeout " + TIMING_POLL_TIMEOUT_MS + "ms) on that field actually resolving, "
                + "instead of guessing at a longer sleep.";
        return new Proposal(Proposal.Kind.TIMING, TIMING_BASE_CONFIDENCE, oldSnippetStr, newSnippetStr, reason);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }

    // =======================================================================
    // WORKFLOW healing. V2 (capability 3): a wider but still bounded, reviewable dismiss vocabulary, and
    // confidence is boosted when a NEW panel/section heading ALSO appeared in the settled capture — two
    // independent signals (a new button AND a new heading) are much stronger evidence of an actual inserted
    // workflow step than a new button alone, which could just be incidental UI noise (e.g. a validation
    // message rendered as a dismissible element). The actual patch action stays exactly as narrow as V1: a
    // single bounded, no-op-if-absent dismiss click.
    // =======================================================================

    private static final List<String> WORKFLOW_DISMISS_BUTTONS =
            List.of("OK", "Yes", "Close", "Confirm", "Continue", "Proceed", "Accept", "Got It");

    private static final double WORKFLOW_BASE_CONFIDENCE = 0.50;
    private static final double WORKFLOW_HEADING_CORROBORATED_CONFIDENCE = 0.70;

    private Proposal proposeWorkflowFix(String methodSource, FailureRecord failure) {
        List<String> t0Buttons = split(failure.buttons, "|");
        List<String> settledButtons = split(failure.settledButtons, "|");
        if (settledButtons.isEmpty()) return null;

        // Corroborating signal: did a NEW panel/section heading also appear? (Same T0-vs-settled comparison
        // already used for buttons, just applied to headings — no new DOM capture needed for this.)
        List<String> t0Headings = split(failure.headings, "|");
        List<String> settledHeadings = split(failure.settledHeadings, "|");
        boolean newHeadingAppeared = settledHeadings.stream().anyMatch(h -> !containsIgnoreCase(t0Headings, h));

        for (String candidate : WORKFLOW_DISMISS_BUTTONS) {
            boolean newlyAppeared = containsIgnoreCase(settledButtons, candidate) && !containsIgnoreCase(t0Buttons, candidate);
            if (!newlyAppeared) continue;
            if (methodSource.toLowerCase().contains(candidate.toLowerCase())) continue; // already handled in this method
            Proposal p = buildDismissInsertProposal(methodSource, candidate, newHeadingAppeared);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Builds a WORKFLOW proposal: finds the method's last {@code return} statement, walks back to the
     * nearest real (non-blank, non-comment, non-brace-only) preceding line as an anchor — together these are
     * very likely unique within the file, satisfying {@link PatchApplier}'s exact-occurrence check — and
     * inserts a bounded, try/catch-wrapped, no-op-if-absent dismiss click for {@code buttonText} right
     * before the return.
     */
    private Proposal buildDismissInsertProposal(String methodSource, String buttonText, boolean headingCorroborated) {
        String[] lines = methodSource.split("\n", -1);

        int returnLine = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].trim().startsWith("return ") || lines[i].trim().equals("return;")) { returnLine = i; break; }
        }
        if (returnLine < 0) return null;

        int anchor = returnLine - 1;
        while (anchor >= 0) {
            String t = lines[anchor].trim();
            if (!t.isEmpty() && !t.startsWith("//") && !t.equals("}") && !t.equals("{")) break;
            anchor--;
        }
        if (anchor < 0) return null;

        StringBuilder oldSnippet = new StringBuilder();
        for (int i = anchor; i <= returnLine; i++) {
            oldSnippet.append(lines[i]);
            if (i < returnLine) oldSnippet.append('\n');
        }
        String oldSnippetStr = oldSnippet.toString();

        // Safety: this anchor..return span must be unique within the method before we even hand it to
        // PatchApplier (which re-checks uniqueness against the WHOLE FILE before writing anything).
        if (countOccurrences(methodSource, oldSnippetStr) != 1) return null;

        Matcher ind = Pattern.compile("^(\\s*)").matcher(lines[returnLine]);
        String indent = ind.find() ? ind.group(1) : "";

        String dismissBlock = indent + "try { page.evaluate(\"(t)=>{const b=[...document.querySelectorAll('button')]"
                + ".find(x=>x.innerText.trim().toLowerCase()===t.toLowerCase()&&x.offsetParent!==null); if(b) b.click();}\", \""
                + buttonText + "\"); } catch (Exception ignore) { }";

        String newSnippetStr = oldSnippetStr.replaceFirst(Pattern.quote(lines[returnLine]),
                Matcher.quoteReplacement(dismissBlock + "\n" + lines[returnLine]));

        String reason = "A \"" + buttonText + "\" button appeared in the settled UI state that was not present when "
                + "this step failed, and is not handled anywhere in this method — consistent with a new required "
                + "confirmation dialog." + (headingCorroborated
                        ? " A new panel/section heading also appeared alongside it, corroborating an actual "
                          + "inserted workflow step rather than incidental UI noise."
                        : " No corroborating new heading was observed, so this is treated as lower confidence.")
                + " Proposing a bounded, best-effort dismiss click (a no-op if the dialog is not present) inserted "
                + "right before the method returns.";
        double confidence = headingCorroborated ? WORKFLOW_HEADING_CORROBORATED_CONFIDENCE : WORKFLOW_BASE_CONFIDENCE;
        return new Proposal(Proposal.Kind.WORKFLOW, confidence, oldSnippetStr, newSnippetStr, reason);
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) if (s.equalsIgnoreCase(value)) return true;
        return false;
    }

    // =======================================================================
    // Shared helpers.
    // =======================================================================

    private String closest(String target, List<String> candidates) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String c : candidates) {
            if (c.isBlank()) continue;
            double d = normalizedLevenshtein(target, c);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return bestDist <= MAX_NORMALIZED_DISTANCE ? best : null;
    }

    /**
     * Structural, concept-aware candidate selection for "Namespace.Field"-shaped literals (ng-model). Prefers,
     * in order:
     * <ol>
     *   <li><b>P1 fix (candidate-selection ordering)</b> — a same-namespace candidate whose spelling is
     *       "very close" ({@link #NS_CLOSE_MATCH_THRESHOLD}) to the target, chosen ONLY when EXACTLY one such
     *       candidate exists. A spelling/case variant this close (e.g. "LocationId" vs the live "Locationid";
     *       "Deparmentid" vs the live "Departmentid") is almost certainly the SAME field with a typo, not a
     *       different field entirely — checked first, before concept-matching gets a chance to outrank it
     *       with an unrelated, spelling-distant field that merely happens to tokenize into a shared concept
     *       (e.g. a stray capital letter turning "cabinId" into tokens ["cabin","id"], sharing the
     *       "identifier" concept with "NursingStationId" even though "cabinid" — the actual live field — is
     *       one character away by spelling). Requires uniqueness for the same reason every rule below does:
     *       if two-or-more same-namespace candidates are ALL this close, that's genuine ambiguity, not a
     *       confident pick — falls through to concept-matching instead of guessing among them.</li>
     *   <li>Same namespace AND same {@link SemanticConcepts business concept}, chosen only when EXACTLY one
     *       such candidate exists — uniqueness removes ambiguity, the same defence-in-depth principle every
     *       rule in this class already applies. This remains the primary mechanism for a genuine field
     *       RENAME, where the old and new names are conceptually related but not spelled alike at all (e.g.
     *       {@code SelectedMonth->LocationID}) — exactly the case {@link #NS_CLOSE_MATCH_THRESHOLD} above
     *       does NOT fire for, since neither side is spelling-close to the other.</li>
     *   <li>Same namespace only, again only when unique.</li>
     *   <li>Falls back to the existing whole-pool closest-edit-distance behaviour.</li>
     * </ol>
     *
     * <p>{@link SemanticValidator} still independently re-checks whatever this returns before it is ever
     * shown to a human — this method only makes generation more principled, it is never itself the safety
     * boundary.</p>
     */
    private String closestStructural(String target, List<String> candidates) {
        String ns = SemanticConcepts.namespaceOf(target);
        List<String> nsAndConcept = new ArrayList<>();
        List<String> nsOnly = new ArrayList<>();
        List<String> nsClose = new ArrayList<>();
        if (ns != null) {
            for (String c : candidates) {
                if (c == null || c.isBlank() || c.equals(target) || !c.startsWith(ns)) continue;
                nsOnly.add(c);
                if (SemanticConcepts.shareConcept(SemanticConcepts.fieldPart(target), SemanticConcepts.fieldPart(c))) {
                    nsAndConcept.add(c);
                }
                // Compared on fieldPart, not the full "Namespace.Field" string — the shared namespace prefix
                // (identical for every same-namespace candidate) would otherwise dilute the normalized
                // distance and make an unrelated field look deceptively "close" purely because most of the
                // compared string is the common namespace, not the field name itself.
                if (normalizedLevenshtein(SemanticConcepts.fieldPart(target), SemanticConcepts.fieldPart(c)) <= NS_CLOSE_MATCH_THRESHOLD) {
                    nsClose.add(c);
                }
            }
        }
        if (nsClose.size() == 1) return nsClose.get(0);
        if (nsAndConcept.size() == 1) return nsAndConcept.get(0);
        if (nsOnly.size() == 1) return nsOnly.get(0);
        return closest(target, candidates);
    }

    /** Confidence for a {@link #closestStructural} pick — reflects WHY the candidate was chosen (structural
     *  evidence, not just string luck), then nudged by how textually close it also happens to be. */
    private double confidenceFromStructural(String target, String matched) {
        String ns = SemanticConcepts.namespaceOf(target);
        boolean sameNs = ns != null && matched.startsWith(ns);
        boolean concept = SemanticConcepts.shareConcept(SemanticConcepts.fieldPart(target), SemanticConcepts.fieldPart(matched));
        double base = sameNs && concept ? 0.72 : sameNs ? 0.55 : confidenceFromMatch(target, matched);
        double editBoost = Math.max(0, (1 - normalizedLevenshtein(target, matched)) - 0.5) * 0.4;
        return Math.max(0.5, Math.min(0.95, base + editBoost));
    }

    private String structuralExplanation(String target, String matched) {
        String ns = SemanticConcepts.namespaceOf(target);
        boolean sameNs = ns != null && matched.startsWith(ns);
        boolean concept = SemanticConcepts.shareConcept(SemanticConcepts.fieldPart(target), SemanticConcepts.fieldPart(matched));
        if (sameNs && concept) {
            return "the closest live ng-model is \"" + matched + "\" — same object namespace (\"" + ns
                    + "\") AND the same business concept, and the only such field currently unaccounted for.";
        }
        if (sameNs) {
            return "the closest live ng-model is \"" + matched + "\" — the only other field in the same object "
                    + "namespace (\"" + ns + "\") currently unaccounted for.";
        }
        return "closest live ng-model is \"" + matched + "\" (edit-distance match).";
    }

    /**
     * Secondary candidate lookup for free-text values (button text, ng-click handler names) where a plain
     * "Namespace.Field" split doesn't apply. Used ONLY when {@link #closest} (raw edit distance) already
     * found nothing within its bound — looks for a candidate sharing enough significant word-tokens with the
     * target to be a plausible structural match (e.g. {@code fnSaveVisitInformation} -&gt; {@code
     * fnSaveVisitDetails} share {save, visit}) even though their character-level edit distance may exceed
     * {@link #MAX_NORMALIZED_DISTANCE}. Requires the overlap to be UNIQUE among the candidate pool — an
     * ambiguous multi-candidate overlap proposes nothing rather than guessing among them.
     */
    private String closestByTokenOverlap(String target, List<String> candidates) {
        Set<String> targetTokens = SemanticConcepts.tokensOf(target);
        if (targetTokens.isEmpty()) return null;
        List<String> matches = new ArrayList<>();
        for (String c : candidates) {
            if (c == null || c.isBlank() || c.equalsIgnoreCase(target)) continue;
            Set<String> cTokens = SemanticConcepts.tokensOf(c);
            Set<String> shared = new LinkedHashSet<>(targetTokens);
            shared.retainAll(cTokens);
            // A real, meaningful overlap: at least 2 shared tokens, or 1 shared token that is itself a large
            // fraction of both short token sets (avoids matching on one incidental common word like "the").
            boolean strongOverlap = shared.size() >= 2
                    || (shared.size() == 1 && targetTokens.size() <= 2 && cTokens.size() <= 2);
            if (strongOverlap) matches.add(c);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** Lower, honestly-labeled confidence for a token-overlap match — real structural evidence, but weaker
     *  than either an edit-distance match or a namespace+concept match, since word overlap alone doesn't rule
     *  out two genuinely different actions that happen to share a common verb/noun. */
    private double confidenceFromTokenOverlap(String target, String matched) {
        return 0.55;
    }

    /** Confidence purely from how close two strings are (used wherever {@link #closest} picked the match). */
    private static double confidenceFromMatch(String target, String matched) {
        return confidenceFromDistance(normalizedLevenshtein(target, matched));
    }

    /** Maps an edit distance already known to be within {@link #MAX_NORMALIZED_DISTANCE} to a 0.5-0.99
     *  confidence range — closer match = higher confidence, but a heuristic string match never claims
     *  certainty. */
    private static double confidenceFromDistance(double normalizedDistance) {
        double c = 1.0 - normalizedDistance;
        return Math.max(0.5, Math.min(0.99, c));
    }

    private static double normalizedLevenshtein(String a, String b) {
        int dist = levenshtein(a.toLowerCase(), b.toLowerCase());
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 0 : (double) dist / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    /* package-private (was private) - reused by LocatorDiagnostics for the same "Namespace.Field" parsing, avoiding a duplicate copy. */
    static List<String> split(String s, String sep) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(Pattern.quote(sep))) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static Map<String, String> splitPairs(String s, String sep, String kvSep) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : split(s, sep)) {
            int i = part.indexOf(kvSep);
            if (i < 0) continue;
            String k = part.substring(0, i).trim();
            if (!k.isEmpty()) out.put(k, part.substring(i + kvSep.length()).trim());
        }
        return out;
    }

    /** Parses the "selectopts" capture: {@code ngModel1:opt1~opt2~opt3;ngModel2:...} -> ngModel -> option list. */
    static Map<String, List<String>> splitSelectOptions(String s) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String part : split(s, ";")) {
            int i = part.indexOf(':');
            if (i < 0) continue;
            String ngModel = part.substring(0, i).trim();
            if (ngModel.isEmpty()) continue;
            List<String> opts = new ArrayList<>();
            for (String o : part.substring(i + 1).split("~")) {
                String t = o.trim();
                if (!t.isEmpty()) opts.add(t);
            }
            out.put(ngModel, opts);
        }
        return out;
    }
}
