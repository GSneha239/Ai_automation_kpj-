package com.kpj.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, deterministic (no LLM) translation from this package's existing technical facts — {@link
 * FailureCategory}, eligibility reasons, confidence/risk, KEEP/REVERTED — into short, plain-English sentences
 * a non-technical reader can follow. Used by both {@link FailureAnalysisReport} and {@link HealingDashboard}
 * so the two reports never phrase the same fact two different ways.
 *
 * <p>Never invents a fact — every method here only rewords something the classifier/proposer/agent already
 * decided; it adds no new judgement of its own. The existing technical text (root cause, evidence, reason)
 * is never removed — it stays available in each report's own "Technical Details" section; this class only
 * adds a plain-language layer in front of it.</p>
 */
public final class PlainLanguage {
    private PlainLanguage() {}

    /** "InfectionTypeMaster" / "com.kpj.tests...Alerts" -&gt; "Infection Type Master" / "Alerts". */
    public static String friendlyName(String classOrId) {
        if (classOrId == null || classOrId.isBlank()) return "this screen";
        String simple = classOrId.contains(".") ? classOrId.substring(classOrId.lastIndexOf('.') + 1) : classOrId;
        simple = simple.replaceFirst("^TC\\d+_", "").replace('_', ' ');
        // insert a space before each internal capital that follows a lowercase letter or digit (camelCase -> words)
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && (Character.isLowerCase(simple.charAt(i - 1)) || Character.isDigit(simple.charAt(i - 1)))) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /** @param screenName the ACTUAL application screen (from {@link #deriveScreenName}), never the test/fixture
     *                     class name — empty when it could not be reliably determined. */
    public static String whatWasTested(String screenName) {
        if (screenName == null || screenName.isBlank()) {
            return "This test exercises a specific screen in the application, step by step, the same way a "
                    + "person using it would. " + SCREEN_NAME_UNKNOWN;
        }
        return "This checks the \"" + screenName + "\" screen in the application, step by step, "
                + "the same way a person using it would.";
    }

    /** Exact fallback sentence when the real application screen cannot be reliably determined — used
     *  everywhere a screen name would otherwise be needed, never silently substituting the test/fixture name. */
    public static final String SCREEN_NAME_UNKNOWN = "The application screen name could not be determined from the available evidence.";

    /** Page Object names that are clearly NOT a real application screen (test scaffolding, fixtures, probes,
     *  demos) — a generic keyword filter, not any specific class name, so a fixture's Page Object is never
     *  mistaken for a real screen while a genuine one (e.g. "AlertsPage", "StaffSchedule") always is trusted. */
    private static final Pattern NOT_A_REAL_SCREEN = Pattern.compile("(?i)fixture|probe|demo|mock|stub|sandbox|sample|dummy|harness|scratch");

    /** Derives the real application screen name from the responsible Page Object file {@link CodeLocator}
     *  already resolved (never from the test/fixture class name) — e.g. {@code "AlertsPage.java"} -&gt;
     *  {@code "Alerts"}, {@code "StaffSchedule.java"} -&gt; {@code "Staff Schedule"}. Returns {@code ""} when
     *  the Page Object itself looks like test scaffolding rather than a real screen, or when none was
     *  resolved at all — callers must show {@link #SCREEN_NAME_UNKNOWN} in that case, never fall back to the
     *  test name. */
    public static String deriveScreenName(String affectedFile) {
        if (affectedFile == null || affectedFile.isBlank()) return "";
        String fileName = affectedFile.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) fileName = fileName.substring(slash + 1);
        fileName = fileName.replaceFirst("(?i)\\.java$", "");
        if (NOT_A_REAL_SCREEN.matcher(fileName).find()) return "";
        String humanized = friendlyName(fileName);
        humanized = humanized.replaceFirst("(?i)\\s*Page$", "").trim(); // "Alerts Page" -> "Alerts"
        return humanized;
    }

    /** From the SAME quoted, dot-qualified reference {@link #explainDynamic} already mines for a field name
     *  (e.g. {@code "staffScheduleInfo.cabinid"}), the NAMESPACE prefix humanized as a candidate screen/form
     *  name (e.g. {@code "staffScheduleInfo"} -&gt; {@code "Staff Schedule"}) — the Angular convention of
     *  naming a form's bound model object after the screen it belongs to. This reflects the LIVE
     *  application's own DOM structure captured in the evidence, not any test code, so it stays valid even
     *  when the responsible Page Object is test scaffolding reusing a real screen. Purely generic string
     *  parsing of whatever reference this one failure's own evidence happens to contain — never a fixed
     *  example, and returns {@code ""} when the prefix carries no usable content. */
    private static String namespaceScreenNameFrom(String raw) {
        if (raw == null || !raw.contains(".")) return "";
        String ns = raw.substring(0, raw.lastIndexOf('.')).trim();
        if (ns.isEmpty()) return "";
        String humanized = friendlyName(ns);
        // Strip a trailing Angular-modeling suffix that isn't part of the screen's real name — NOT "Master",
        // which is frequently a genuine part of this application's own screen names (e.g. "Bed Master").
        humanized = humanized.replaceFirst("(?i)\\s*(Info|Model|Data|Form)$", "").trim();
        if (humanized.isEmpty()) return "";
        return Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1);
    }

    /** The full screen-name resolution used by both reports: prefer the responsible Page Object (most
     *  reliable when it's a genuine screen, not test scaffolding — see {@link #deriveScreenName}); when that
     *  gives nothing trustworthy, fall back to the Angular-model namespace of whatever quoted reference the
     *  failure's OWN technical root-cause/evidence text already contains (see {@link
     *  #namespaceScreenNameFrom}) — evidence from the live application itself, not test code. Returns
     *  {@code ""} only when NEITHER source yields anything, so callers show {@link #SCREEN_NAME_UNKNOWN}
     *  strictly as a last resort, never as a substitute for evidence that was actually available. */
    public static String resolveScreenName(String affectedFile, String technicalRootCause, List<String> technicalEvidence) {
        String fromPageObject = deriveScreenName(affectedFile);
        if (!fromPageObject.isBlank()) return fromPageObject;
        String sourceText = richestQuotedText(technicalRootCause, technicalEvidence);
        for (String q : extractQuoted(sourceText)) {
            String candidate = namespaceScreenNameFrom(q);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    /** A concrete "what the test was trying to do" sentence — screen + field/control + intended action +
     *  verification — dynamically from whichever of screen/field is actually known; never the generic
     *  "exercises a specific screen" filler when more is available. */
    public static String whatWasTriedPlain(String screenName, String fieldName) {
        boolean hasScreen = screenName != null && !screenName.isBlank();
        boolean hasField = fieldName != null && !fieldName.isBlank();
        if (hasField && hasScreen) {
            return "The test was trying to use the \"" + fieldName + "\" field on the \"" + screenName
                    + "\" screen and verify that it could be accessed correctly.";
        }
        if (hasScreen) {
            return "The test was trying to complete an action on the \"" + screenName + "\" screen and verify "
                    + "that it completed correctly.";
        }
        if (hasField) {
            return "The test was trying to use the \"" + fieldName + "\" field and verify that it could be "
                    + "accessed correctly. " + SCREEN_NAME_UNKNOWN;
        }
        return "The test was trying to complete a specific action in the application and verify that it "
                + "succeeded. " + SCREEN_NAME_UNKNOWN;
    }

    /** One plain sentence per business-facing category — what this MEANS, not how it was detected. */
    public static String whyItFailedPlain(FailureCategory category) {
        if (category == null) return "The system could not confidently determine why this failed.";
        switch (category) {
            case MISSING_TEST_DATA:
                return "The system looked for a specific record that doesn't exist yet in the application. "
                        + "This isn't a defect — the right test data just needs to be created first.";
            case TEST_DATA_ISSUE:
                return "The information used for this test didn't match what the application returned. "
                        + "The test data itself needs to be reviewed.";
            case DROPDOWN_FAILURE:
                return "A dropdown or selection field on the screen didn't behave as expected — either an "
                        + "option was missing, or the field didn't load in time.";
            case BUTTON_UI_FAILURE:
                return "A button or on-screen control didn't respond the way the test expected — it may be "
                        + "missing, disabled, or temporarily covered by something else on screen.";
            case LOCATOR_SELECTOR_FAILURE:
                return "The automated test was looking for a screen element using an outdated internal "
                        + "reference. The screen itself still works fine for a real user — only the test's "
                        + "own reference to it needs updating.";
            case VALIDATION_FAILURE:
                return "The screen's behavior (for example, whether a field was enabled, visible, or required) "
                        + "didn't match what the test expected. This could be a genuine application issue, or "
                        + "simply an outdated test expectation — a person needs to check which.";
            case APPLICATION_BUG:
                return "The application itself returned a result that doesn't match the expected business "
                        + "rule. This looks like a genuine issue with the application, not with the test.";
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                return "Something about the test's own environment (not the application) is misconfigured — "
                        + "for example, a file path or setting specific to this machine.";
            case AUTHENTICATION_PERMISSION_ISSUE:
                return "The test could not log in, or did not have the right permissions. This is a login/"
                        + "access issue, not a problem with the feature being tested.";
            case NETWORK_INFRASTRUCTURE_ISSUE:
                return "There was a connectivity or server-availability problem — the network, database, or "
                        + "server wasn't reachable at the time this test ran.";
            case AUTOMATION_SCRIPT_ISSUE:
                return "The automated test script itself needs attention — for example, its timing, or how it "
                        + "handles a new screen change — rather than the application having a defect.";
            case UNKNOWN_NEEDS_MANUAL_INVESTIGATION:
            default:
                return "The system could not confidently determine why this failed. A person should take a look.";
        }
    }

    /** Plain "can this fix itself?" sentence, layered in front of the existing technical eligibilityReason —
     *  exposed as {@link FailureExplanation#whatHappensNext}. Handles BOTH the eligible and not-eligible case
     *  generically by category, so a caller never needs its own hardcoded YES-case sentence alongside this
     *  method's NO-case one (a real inconsistency this replaced — see {@link #explain}). */
    private static String healingPossiblePlain(boolean eligible, FailureCategory category) {
        if (eligible) {
            return "Yes — this looks like a simple, low-risk reference update, and the system found a safe "
                    + "replacement already in use on the live screen. A person must still review and approve "
                    + "the exact change before anything is written.";
        }
        switch (category == null ? FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION : category) {
            case AUTHENTICATION_PERMISSION_ISSUE:
            case NETWORK_INFRASTRUCTURE_ISSUE:
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                return "No — this is an access/environment problem, not something a code reference change "
                        + "could ever fix.";
            case MISSING_TEST_DATA:
            case TEST_DATA_ISSUE:
                return "No — the test's automation isn't wrong; the underlying data needs attention first, "
                        + "which isn't something the system should change on its own.";
            case APPLICATION_BUG:
                return "No — this looks like a genuine application defect, not an automation reference "
                        + "problem, so it needs a developer, not an automatic fix.";
            case VALIDATION_FAILURE:
                return "No — a state/behavior mismatch like this could just as easily be a real application "
                        + "issue as a stale test assumption, so the system never guesses here.";
            default:
                return "No — the system could not find a safe, confident fix for this on its own. A person "
                        + "needs to look into it.";
        }
    }

    /** Plain-English Root Cause + Evidence, generated dynamically from the actual failure — never one fixed
     *  example. See {@link #explainDynamic}. */
    public static final class RootCauseEvidence {
        public final String rootCausePlain;
        public final String evidencePlain;
        /** The specific field/control name this was traced to, when the technical text supported one —
         *  e.g. "Cabin", "FRA Type" — or {@code ""} when no field-level evidence was found. Reused everywhere
         *  else a report needs to name the affected control, so it's derived exactly once per record. */
        public final String affectedFieldName;
        RootCauseEvidence(String rc, String ev) { this(rc, ev, ""); }
        RootCauseEvidence(String rc, String ev, String field) { rootCausePlain = rc; evidencePlain = ev; affectedFieldName = field == null ? "" : field; }
    }

    /**
     * Builds a plain-English Root Cause + Evidence pair for ANY failure, dynamically, from whatever technical
     * text this run actually produced — never a hardcoded example.
     *
     * <p>Every deterministic locator-healing rule in {@link PatchProposer} phrases its reason the same way:
     * "no live X matches/has '&lt;old reference&gt;' ...  closest live ... is '&lt;new reference&gt;'" — always
     * exactly the old (missing) reference quoted first and the new (matching) one quoted last, regardless of
     * whether the underlying idiom is a button, an ng-model, an ng-click handler, or a panel title. So: scan
     * {@code technicalRootCause} and every line of {@code technicalEvidence} for whichever one contains the
     * MOST quoted values; if it has two or more, the first and last ARE that old/new reference pair — turn
     * them into one human field name each and describe the mismatch in business terms, without ever printing
     * the technical wording itself. If nothing in this failure's text contains a comparable pair (most
     * non-locator categories won't), this makes no claim of its own — it returns the caller's own plain
     * fallback text unchanged, so a category with no field-level evidence still gets a clear sentence instead
     * of an empty or overly technical one.</p>
     *
     * @param technicalRootCause the record's existing rootCause text (may be null/blank)
     * @param technicalEvidence the record's existing evidence list, if any (may be null/empty)
     * @param fallbackRootCause plain sentence to use verbatim when no field-level pair is found
     * @param fallbackEvidence plain sentence to use verbatim when no field-level pair is found
     */
    private static final Pattern CLOSEST_LIVE = Pattern.compile("(?i)closest live [a-z0-9\\- ]*?is \"([^\"]+)\"");

    public static RootCauseEvidence explainDynamic(String technicalRootCause, List<String> technicalEvidence,
                                                     String fallbackRootCause, String fallbackEvidence) {
        String sourceText = richestQuotedText(technicalRootCause, technicalEvidence);
        List<String> quoted = extractQuoted(sourceText);
        if (quoted.isEmpty()) return new RootCauseEvidence(fallbackRootCause, fallbackEvidence);

        // The FIRST quoted value is always the stale/missing reference. The "new" one is NOT reliably the
        // last quoted value — some proposer phrasings quote a third, incidental value afterwards (e.g. the
        // namespace in "... same object namespace (\"X.\") currently unaccounted for"). Every proposer rule
        // instead phrases the actual replacement as "closest live ... is \"Y\"" — match that specifically
        // first, and only fall back to "the last quoted value" when there are exactly two (unambiguous).
        String oldRaw = quoted.get(0);
        Matcher closest = CLOSEST_LIVE.matcher(sourceText);
        String newRaw = closest.find() ? closest.group(1) : quoted.size() == 2 ? quoted.get(1) : null;
        if (newRaw == null) return new RootCauseEvidence(fallbackRootCause, fallbackEvidence);

        String oldField = humanizeFieldName(oldRaw);
        String newField = humanizeFieldName(newRaw);
        String label = !newField.isBlank() ? newField : !oldField.isBlank() ? oldField : "";
        if (label.isBlank()) return new RootCauseEvidence(fallbackRootCause, fallbackEvidence);
        boolean sameAfterCleanup = oldField.equalsIgnoreCase(newField);

        String rc = sameAfterCleanup
                ? ("The reference the test uses for the " + label + " field is almost identical to the correct "
                        + "one, but not an exact match — for example, a small spelling or capitalization "
                        + "difference — so the test could not find it, even though the field itself is unchanged.")
                : ("The application's " + label + " field reference no longer matches what the test was still "
                        + "using — a previous, outdated reference. Because of this mismatch, the test could not "
                        + "identify the required field.");
        String ev = sameAfterCleanup
                ? ("The required " + label + " field could not be found using the test's exact reference. The "
                        + "system found a field on the page whose reference differs only very slightly, which is "
                        + "why it's confident this is simply an outdated or mistyped reference to the same field.")
                : ("The required " + label + " field could not be found on the page using the test's existing "
                        + "reference. The system found a closely matching field reference that IS on the page, "
                        + "providing evidence that the test was referring to an outdated version of the same field.");
        return new RootCauseEvidence(rc, ev, label);
    }

    /** Among {@code rootCause} and every line of {@code evidence}, the one string with the most quoted
     *  values — the richest candidate for a field-name comparison. */
    private static String richestQuotedText(String rootCause, List<String> evidence) {
        String best = rootCause == null ? "" : rootCause;
        int bestCount = extractQuoted(best).size();
        if (evidence != null) {
            for (String e : evidence) {
                int c = extractQuoted(e).size();
                if (c > bestCount) { best = e; bestCount = c; }
            }
        }
        return best;
    }

    private static List<String> extractQuoted(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (!v.isBlank()) out.add(v);
        }
        return out;
    }

    /** "alertconfig.locationld" -&gt; "Location"; "NursingStationId" -&gt; "Nursing Station". Strips the
     *  namespace (everything up to the last dot), drops a trailing "id"/"Id" when something meaningful
     *  remains, then spaces out camelCase boundaries and capitalizes — a best-effort, non-technical label,
     *  never shown alongside its raw source. */
    static String humanizeFieldName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String field = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
        field = field.replaceAll("[^A-Za-z0-9]", " ").trim();
        if (field.isEmpty()) return "";
        if (field.length() > 4 && field.toLowerCase().endsWith("id")) {
            String stripped = field.substring(0, field.length() - 2);
            if (stripped.length() >= 3) field = stripped;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(field.charAt(i - 1))) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return out.toString().trim();
    }

    /** Plain-English fallback for "what did the system observe" when no field-level pair could be extracted
     *  (most non-locator categories) — paired with {@link #whyItFailedPlain} so a category with no field
     *  evidence still reads as one clear, non-technical story instead of an empty or raw Evidence field. */
    public static String evidenceFallbackPlain(FailureCategory category) {
        if (category == null) return "The system's observations are recorded above (what was expected versus "
                + "what actually happened), but were not specific enough to point to one clear cause automatically.";
        switch (category) {
            case MISSING_TEST_DATA:
                return "The system looked for the expected record and confirmed it simply isn't present in the "
                        + "application yet — that absence is the evidence this is a data issue, not a code problem.";
            case TEST_DATA_ISSUE:
                return "The value the application returned did not match what the test expected, even though the "
                        + "relevant screen and fields were found correctly — pointing at the data itself, not the automation.";
            case DROPDOWN_FAILURE:
                return "The system checked the dropdown's live options at the moment of failure and found either "
                        + "a missing option or a field that had not finished loading in time.";
            case BUTTON_UI_FAILURE:
                return "The system checked the screen's buttons and controls at the moment of failure and did not "
                        + "find one matching what the test needed to click.";
            case LOCATOR_SELECTOR_FAILURE:
                return "The system compared what the test was looking for against everything actually present on "
                        + "the screen at that exact moment.";
            case VALIDATION_FAILURE:
                return "The system compared the field's actual on-screen state (enabled, visible, or required) "
                        + "against what the test expected, and found a mismatch that could equally be a real "
                        + "issue or a stale test assumption.";
            case APPLICATION_BUG:
                return "The application's own response did not match the expected business outcome, even though "
                        + "the automation correctly reached the right screen and fields.";
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                return "The failure pointed at something specific to this machine or environment (such as a file "
                        + "path), not at the application's behavior.";
            case AUTHENTICATION_PERMISSION_ISSUE:
                return "The system's login or access attempt itself did not succeed, before the actual feature "
                        + "being tested was ever reached.";
            case NETWORK_INFRASTRUCTURE_ISSUE:
                return "The system could not reach the network, server, or database it needed at the time — a "
                        + "connectivity issue, not an application defect.";
            case AUTOMATION_SCRIPT_ISSUE:
                return "The system's observations point at how the test itself is written (its timing or "
                        + "workflow handling), not at the application.";
            case UNKNOWN_NEEDS_MANUAL_INVESTIGATION:
            default:
                return "The system's observations are recorded above, but were not specific enough to point to "
                        + "one clear, confident cause automatically.";
        }
    }

    /** Technical vocabulary that must never reach a report's main explanation (only the raw "expected"/
     *  "actual" text a test itself recorded can contain this — the rest of this class already avoids it). */
    private static final Pattern TECHNICAL_MARKER =
            Pattern.compile("(?i)ng-model|<select|\\bdom\\b|raw match|\\blocator\\b|\\bselector\\b");

    /** One "Key=Value" segment of the common raw field-dump shape dozens of Page Objects use for a step's
     *  actual/expected text (e.g. {@code "Code=FR12700 | Remark=... | FRAType=(no-opt)"}) — an internal
     *  debug format, not natural language, and NOT specific to any one test/field/screen. */
    private static final Pattern FIELD_DUMP_PAIR = Pattern.compile("([A-Za-z][A-Za-z0-9]*)=([^|]*)");
    /** A field-dump value that means "this one wasn't found/set" across every Page Object's own convention —
     *  {@code (no)}, {@code (no field)}, {@code (no-opt)}, {@code (no options)}, blank, or {@code null}. */
    private static final Pattern DUMP_PROBLEM_VALUE = Pattern.compile("(?i)^\\s*\\(no[^)]*\\)\\s*$|^\\s*$|^\\s*null\\s*$");

    /** True when {@code text} contains at least two {@code Key=Value} segments — the signature of the raw
     *  field-dump format, distinguishing it from an ordinary natural-language sentence (which essentially
     *  never contains this shape). */
    private static boolean looksLikeFieldDump(String text) {
        Matcher m = FIELD_DUMP_PAIR.matcher(text);
        int count = 0;
        while (m.find()) if (++count >= 2) return true;
        return false;
    }

    /** Converts a raw field-dump string into one plain sentence, dynamically, for ANY set of fields — never
     *  naming a specific field in this method itself. Each {@code Key=Value} segment's key is humanized via
     *  {@link #humanizeFieldName} and sorted into "entered fine" vs. "could not be completed" by whether its
     *  value matches {@link #DUMP_PROBLEM_VALUE}. */
    private static String summarizeFieldDump(String text) {
        List<String> ok = new ArrayList<>();
        List<String> problem = new ArrayList<>();
        Matcher m = FIELD_DUMP_PAIR.matcher(text);
        while (m.find()) {
            String rawKey = m.group(1).trim();
            String rawVal = m.group(2).trim();
            String label = humanizeFieldName(rawKey);
            if (label.isBlank()) label = rawKey;
            (DUMP_PROBLEM_VALUE.matcher(rawVal).matches() ? problem : ok).add(label);
        }
        if (problem.isEmpty() && ok.isEmpty()) return text; // could not parse meaningfully — leave untouched
        if (problem.isEmpty()) return "The " + joinPlain(ok) + " were entered successfully.";
        if (ok.isEmpty()) return "The " + joinPlain(problem) + " could not be completed.";
        return "The " + joinPlain(ok) + " were entered, but the " + joinPlain(problem) + " could not be completed.";
    }

    private static String joinPlain(List<String> items) {
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);
        return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
    }

    /**
     * Turns a test's raw "expected" or "actual" text into a plain sentence, dynamically, for ANY failure —
     * never a fixed example. If the raw text mentions a specific field reference (in quotes) alongside a
     * recognizable technical marker, that reference is turned into a human field name the same way
     * {@link #explainDynamic} does. Otherwise, if it matches the common raw "Key=Value | Key=Value" field-
     * dump shape many Page Objects use, it is summarized field-by-field (see {@link #summarizeFieldDump}).
     * Text that is ALREADY plain (most non-locator categories' messages already read fine, e.g. "Login
     * failed: still on login page...") is left as-is (only a redundant leading "FAILED: " is trimmed).
     */
    public static String humanizeOutcomeText(String raw, boolean isExpected) {
        if (raw == null || raw.isBlank()) {
            return isExpected ? "No specific expected result was recorded for this check."
                    : "No specific result was recorded for this check.";
        }
        if (TECHNICAL_MARKER.matcher(raw).find()) {
            return isExpected
                    ? "The screen should have shown the specific element or value this step was checking for."
                    : "The system could not find a screen element the test needed, using the reference it currently has.";
        }
        String cleaned = raw.replaceFirst("(?i)^FAILED:\\s*", "").trim();
        if (cleaned.isEmpty()) {
            return isExpected ? "No specific expected result was recorded for this check."
                    : "No specific result was recorded for this check.";
        }
        if (!isExpected) {
            NavFailure nf = parseNavFailure(cleaned);
            if (nf != null) {
                return "The application showed a different screen (\"" + nf.actualLanded + "\") instead of the "
                        + "expected \"" + nf.expectedScreen + "\" screen.";
            }
        }
        if (looksLikeFieldDump(cleaned)) return summarizeFieldDump(cleaned);
        // A bare boolean/assertion-result shape ("assertion true", "true", "false", "FAILED: false"...) carries
        // no descriptive content of its own — this is the exact raw-leak shape ("Expected: assertion true")
        // that must never reach a non-technical reader, generically, for any test that checks a bare
        // true/false condition rather than describing what it checked.
        if (BARE_ASSERTION.matcher(cleaned).matches()) {
            return isExpected
                    ? "The test was checking that a specific condition on the screen was met before this step could continue."
                    : "That condition was not met when the system checked it, so this step did not pass.";
        }
        // A raw "ERR:<code>" (or ERR_/ERR-) automation shorthand — never natural language, and never any one
        // specific test's code — must not reach a non-technical reader as-is either.
        Matcher errCode = ERR_CODE_SHAPE.matcher(cleaned);
        if (errCode.matches()) {
            return humanizeErrCode(errCode.group(1), isExpected);
        }
        // A raw wait-timeout message ("Timeout 30000ms exceeded...") — Playwright's own wording, not a
        // specific test's — must not reach a non-technical reader unexplained.
        if (TIMEOUT_SHAPE.matcher(cleaned).find()) {
            return isExpected
                    ? "The test expected this step to finish within the normal amount of time it allows for the application to respond."
                    : "The application did not respond in time, so the test gave up waiting and this step could not be completed.";
        }
        // A raw, uncaught exception's message (stack trace / package.ClassException shape / Playwright's own
        // "=== logs ===" block) — recorded the same way for ANY unexpected error in ANY test — must not reach
        // a non-technical reader unexplained either. The raw text still appears in Technical Details.
        if (EXCEPTION_SHAPE.matcher(cleaned).find()) {
            return isExpected
                    ? "The test expected this step to complete normally, without any unexpected technical error."
                    : "An unexpected technical error interrupted the test before this step could be completed. "
                            + "(The exact error is recorded in Technical Details below.)";
        }
        // An expected value recorded as a quoted confirmation message plus "toast" (this project's own
        // shorthand for "a confirmation popup should have shown this text") — explain what that means instead
        // of showing the shorthand literally. Generic by shape (any quoted text + "toast"), never one message.
        if (isExpected) {
            Matcher toast = EXPECTED_TOAST_SHAPE.matcher(cleaned);
            if (toast.find()) {
                return "The application should have shown a confirmation message saying: \"" + toast.group(1).trim() + "\".";
            }
        }
        return normalizeUiText(cleaned);
    }

    /** {@code "'X.' toast"} — this project's own shorthand for "a confirmation popup should have shown this
     *  exact text" — matched generically by shape (quoted text + the word "toast"), never one fixed message. */
    private static final Pattern EXPECTED_TOAST_SHAPE = Pattern.compile("(?i)^['\"](.+?)['\"]\\s*toast\\s*$");

    /** Matches a raw expected/actual value that is ONLY a bare assertion result — "true", "false", "assertion
     *  true", etc. — with no descriptive content of its own. Generic by shape, not by any specific wording a
     *  particular test happens to use. */
    private static final Pattern BARE_ASSERTION = Pattern.compile("(?i)^\\s*(assertion\\s+)?(true|false)\\s*\\.?\\s*$");

    /** Matches the shape of a raw wait-timeout message (e.g. {@code "Timeout 30000ms exceeded."}) — the
     *  vocabulary Playwright itself uses for any wait, on any screen, never one specific test's wording. */
    private static final Pattern TIMEOUT_SHAPE = Pattern.compile("(?i)\\btimeout\\s+\\d+\\s*ms\\b.*\\bexceeded\\b");

    /** Matches the general SHAPE of a raw, uncaught exception's message — a {@code package.ClassException}
     *  identifier, a stack-trace "at package.Class.method(" line, or Playwright's own
     *  "=== logs ===" separator — never one specific test's or exception's wording. This is the generic
     *  fallback for the "Unexpected error during the test" step every test records the same way on any
     *  uncaught exception, so the raw {@code Throwable.getMessage()} text never reaches a non-technical
     *  reader unexplained. */
    private static final Pattern EXCEPTION_SHAPE =
            Pattern.compile("(?i)\\b[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+(Exception|Error)\\b|\\bat\\s+[a-zA-Z0-9_$]+(\\.[a-zA-Z0-9_$]+)+\\(|=+\\s*logs\\s*=+");

    /** Matches the common {@code "ERR:<code>"} (or {@code ERR_<code>}/{@code ERR-<code>}) shorthand many Page
     *  Objects use as their own internal automation-level return value (e.g. {@code "ERR:no-option"}) — a raw
     *  code, never natural language, and not specific to any one screen/field/test. */
    private static final Pattern ERR_CODE_SHAPE = Pattern.compile("(?i)^ERR[:_-]\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*$");

    /** Turns a recognized {@code ERR:<code>} shorthand into a plain sentence, generically, by the KEYWORDS the
     *  code itself contains — never one fixed code (e.g. never just "no-option"). An unrecognized code still
     *  gets a safe, honest generic sentence instead of ever showing the raw code to a non-technical reader —
     *  the raw code itself remains available in Technical Details, untouched. */
    private static String humanizeErrCode(String code, boolean isExpected) {
        String c = code.toLowerCase();
        boolean noneOrMissing = c.contains("no") || c.contains("empty") || c.contains("missing");
        if (noneOrMissing && (c.contains("option") || c.contains("select") || c.contains("choice"))) {
            return isExpected ? "The step expected at least one selectable option to be available."
                    : "The application did not provide a selectable option for this step.";
        }
        if (c.contains("notfound") || (c.contains("not") && c.contains("found"))) {
            return isExpected ? "The step expected to find a specific item on the screen."
                    : "The application could not find what this step was looking for.";
        }
        if (c.contains("timeout") || c.contains("timedout")) {
            return isExpected ? "The step expected to finish within the normal amount of time it allows for the application to respond."
                    : "The application did not respond in time, so this step could not be completed.";
        }
        if (c.contains("empty") || c.contains("blank") || c.contains("nodata")) {
            return isExpected ? "The step expected some data to be available."
                    : "The application had no data available for this step.";
        }
        return isExpected ? "The step expected to complete normally, without an internal automation error."
                : "The step returned an internal error instead of the expected result. "
                        + "(The exact code is recorded in Technical Details below.)";
    }

    /** Cleans a raw recorded step name for display — strips a trailing parenthetical annotation many test
     *  authors add for their OWN internal notes (e.g. {@code "(deliberate rollback-verification fixture)"}) —
     *  a generic, shape-based cleanup that applies to any step name with that pattern, never one specific
     *  test's wording. */
    public static String cleanStepName(String rawStep) {
        if (rawStep == null) return "";
        String cleaned = rawStep.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        String result = cleaned.isEmpty() ? rawStep.trim() : cleaned;
        // "ERROR" is the generic placeholder step name every test records the same way when it fails from an
        // unexpected/uncaught exception before reaching any named step — never a real step description, and
        // never specific to one test, so every display site that shows a short step label (not just Section B)
        // must not show this raw placeholder unexplained.
        if ("ERROR".equalsIgnoreCase(result)) return "Unexpected error (before any named step)";
        return result;
    }

    /** Describes the failed step for a non-technical reader — names the affected field/control and the real
     *  screen when both are known (from {@link RootCauseEvidence#affectedFieldName} / {@link
     *  #deriveScreenName}), otherwise falls back to the recorded step name with only its internal-notes
     *  parenthetical stripped. Never invents an action verb ("select"/"click"/"type") the evidence doesn't
     *  support — uses the neutral "use". */
    public static String describeFailedStep(String rawStep, String screenName, String fieldName) {
        if (rawStep != null && "ERROR".equalsIgnoreCase(rawStep.trim())) {
            return "The test stopped unexpectedly before it reached a specific, named step in the workflow — "
                    + "see the evidence below for what was happening at the time.";
        }
        String clean = cleanStepName(rawStep);
        boolean hasField = fieldName != null && !fieldName.isBlank();
        boolean hasScreen = screenName != null && !screenName.isBlank();
        if (hasField && hasScreen) {
            return "Use the \"" + fieldName + "\" field on the \"" + screenName + "\" screen (recorded test step: \"" + clean + "\").";
        }
        if (hasField) {
            return "Use the \"" + fieldName + "\" field as part of this step (recorded test step: \"" + clean + "\").";
        }
        return clean;
    }

    /** Enriches the already-humanized "expected" text with the specific field/screen when {@link
     *  #humanizeOutcomeText} could only produce the generic bare-assertion fallback — never overrides a
     *  fallback that's already specific (e.g. a field-dump summary), and never invents business meaning
     *  beyond "this field should have been usable". */
    public static String expectedOutcomePlain(String rawExpected, String fieldName, String screenName) {
        String base = humanizeOutcomeText(rawExpected, true);
        if (fieldName == null || fieldName.isBlank()) return base;
        if (!base.startsWith("The test was checking that a specific condition")) return base;
        boolean hasScreen = screenName != null && !screenName.isBlank();
        return "The \"" + fieldName + "\" field should have been available so the test could use it and continue "
                + "with the workflow" + (hasScreen ? " on the \"" + screenName + "\" screen." : ".");
    }

    /** Same as {@link #expectedOutcomePlain}, for the "actual" side. */
    public static String actualOutcomePlain(String rawActual, String fieldName) {
        String base = humanizeOutcomeText(rawActual, false);
        if (fieldName == null || fieldName.isBlank()) return base;
        if (!base.equals("The system could not find a screen element the test needed, using the reference it currently has.")
                && !base.startsWith("That condition was not met")) {
            return base;
        }
        return "The test could not access the \"" + fieldName + "\" field because the reference currently used by "
                + "the test no longer matched the control available on the screen.";
    }

    /** When the raw "actual" text is the common field-dump shape (see above), the humanized name of the
     *  FIRST field whose value indicates a problem — e.g. "FRA Type" from {@code "...FRAType=(no-opt)"} —
     *  or {@code ""} when not a field-dump / no problem field could be identified. Never hardcodes a field
     *  name; purely extracts whichever one the actual failure data names. Used to make a category's generic
     *  explanation name the SPECIFIC field/control involved. */
    public static String extractProblemFieldName(String rawActual) {
        if (rawActual == null) return "";
        String cleaned = rawActual.replaceFirst("(?i)^FAILED:\\s*", "").trim();
        if (!looksLikeFieldDump(cleaned)) return "";
        Matcher m = FIELD_DUMP_PAIR.matcher(cleaned);
        while (m.find()) {
            String rawKey = m.group(1).trim();
            String rawVal = m.group(2).trim();
            if (DUMP_PROBLEM_VALUE.matcher(rawVal).matches()) {
                String label = humanizeFieldName(rawKey);
                return label.isBlank() ? rawKey : label;
            }
        }
        return "";
    }

    /** Same as {@link #whyItFailedPlain}, but when a specific field/control name is known (from {@link
     *  #extractProblemFieldName}), names it directly for the categories where that reads naturally —
     *  otherwise falls back to the unchanged generic category sentence. */
    public static String whyItFailedPlainForField(FailureCategory category, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return whyItFailedPlain(category);
        if (category == FailureCategory.DROPDOWN_FAILURE) {
            return "The test was trying to use the " + fieldName + " selection field on this screen, but it "
                    + "could not complete that step — either the option it needed had not finished loading "
                    + "yet, or the field could not be found the way the test expects.";
        }
        if (category == FailureCategory.BUTTON_UI_FAILURE) {
            return "The test was trying to use the " + fieldName + " control on this screen, but it did not "
                    + "respond the way the test expected — it may be missing, disabled, or not yet visible.";
        }
        return whyItFailedPlain(category);
    }

    /** Same as {@link #evidenceFallbackPlain}, field-name-aware — see {@link #whyItFailedPlainForField}. */
    public static String evidenceFallbackPlainForField(FailureCategory category, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return evidenceFallbackPlain(category);
        if (category == FailureCategory.DROPDOWN_FAILURE) {
            return "The system checked the " + fieldName + " field's live options at the moment of failure and "
                    + "found either a missing option or a field that had not finished loading in time — the "
                    + "other fields on this same step were entered normally, which points specifically at " + fieldName + ".";
        }
        if (category == FailureCategory.BUTTON_UI_FAILURE) {
            return "The system checked the screen's controls at the moment of failure and did not find " + fieldName
                    + " in a usable state, while the rest of this same step completed normally.";
        }
        return evidenceFallbackPlain(category);
    }

    // ---- navigation-failure detection: the common "WRONG PAGE - expected X but the app opened: Y" shape ----

    /** Dozens of Page Objects report a wrong-screen failure the same way: {@code "WRONG PAGE - expected <X>
     *  but the app opened: <Y>"}. Detecting this ONE shared shape (not any one test's wording) lets navigation
     *  failures get their own specific explanation instead of falling through to the generic category text. */
    private static final Pattern WRONG_PAGE = Pattern.compile("(?i)wrong page\\s*-\\s*expected\\s+(.+?)\\s+but the app opened:\\s*(.+)$");

    private static final class NavFailure {
        final String expectedScreen, actualLanded;
        NavFailure(String e, String a) { expectedScreen = e; actualLanded = a; }
    }

    private static NavFailure parseNavFailure(String rawActual) {
        if (rawActual == null) return null;
        Matcher m = WRONG_PAGE.matcher(rawActual.trim());
        if (!m.find()) return null;
        String landed = m.group(2).trim();
        int at = landed.indexOf(" @ ");
        if (at > 0) landed = landed.substring(0, at).trim(); // drop the trailing raw URL — kept in Technical Details only
        return new NavFailure(m.group(1).trim(), landed);
    }

    /** True when {@code rawActual} is a navigation ("wrong screen") failure this class can explain specifically. */
    public static boolean isNavigationFailure(String rawActual) {
        return parseNavFailure(rawActual) != null;
    }

    /** Plain-English root cause for a detected navigation failure — dynamically names whichever screens the
     *  actual failure recorded, never a fixed example. */
    public static String navigationRootCausePlain(String rawActual) {
        NavFailure nf = parseNavFailure(rawActual);
        if (nf == null) return null;
        return "The test attempted to open the \"" + nf.expectedScreen + "\" screen, but that screen was not "
                + "reached. Instead, the application showed a different screen (\"" + nf.actualLanded + "\"). "
                + "Because the required screen was not available, the remaining steps in this test could not be completed.";
    }

    public static String navigationEvidencePlain(String rawActual) {
        NavFailure nf = parseNavFailure(rawActual);
        if (nf == null) return null;
        return "At the moment this step ran, the application's own on-screen content did not match the \""
                + nf.expectedScreen + "\" screen the test expected — it showed \"" + nf.actualLanded + "\" instead, "
                + "which is direct evidence the navigation did not complete as expected.";
    }

    // ---- application-message evidence: the raw Expected/Actual TEXT itself outranks the assigned category ---
    // A test's raw "actual" text is frequently the application's OWN on-screen message (a toast/alert/inline
    // validation message), captured verbatim by whatever Page Object recorded the failure. That message is
    // first-hand evidence of what the application actually did — stronger evidence than the failure category a
    // separate, purely technical classifier attached to it (e.g. a raw-diagnostic keyword match that produced
    // ENVIRONMENT_CONFIGURATION_ISSUE even though the application clearly loaded and responded on its own).
    // These patterns recognize the SHAPE of four common kinds of application-emitted message — never one
    // field's/screen's/test's specific wording — so this works for any future test/category the same way.

    /** A UI toast/alert scraped without the whitespace between two separate elements (e.g. a toast's title
     *  glued directly to its body) reads as one run-on word — the same camelCase-boundary rule {@link
     *  #friendlyName} already uses repairs it generically, for any application's text, not just one call site. */
    private static String insertWordBoundarySpaces(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && (Character.isLowerCase(s.charAt(i - 1)) || Character.isDigit(s.charAt(i - 1)))) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Best-effort generic cleanup of a raw expected/actual value before it is pattern-matched or displayed:
     *  repairs glued-together UI text (see {@link #insertWordBoundarySpaces}) and strips a leading run of
     *  non-letter/digit "chrome" symbols (a toast's close icon, a leading bullet, etc.). Never removes,
     *  reorders, or invents a word — purely whitespace/symbol repair, safe to apply to any raw value. */
    private static String normalizeUiText(String raw) {
        if (raw == null) return "";
        String s = insertWordBoundarySpaces(raw.trim());
        s = s.replaceFirst("^[^\\p{L}\\p{N}\"']+", "").trim();
        return s.replaceAll("\\s+", " ");
    }

    private static final Pattern VALIDATION_MESSAGE_SHAPE = Pattern.compile("(?i)"
            + "\\bplease\\s+(select|choose|enter|provide|fill|complete)\\b|\\bis\\s+required\\b|\\bare\\s+required\\b|"
            + "\\bmandatory\\b|\\bmust\\s+(select|choose|enter|provide)\\b|\\bcannot\\s+be\\s+(empty|blank|null)\\b|"
            + "\\binvalid\\b|\\bfield\\s+is\\s+required\\b");
    private static final Pattern AUTH_MESSAGE_SHAPE = Pattern.compile("(?i)"
            + "login\\s+failed|authentication\\s+failed|invalid\\s+credentials|unauthorized|access\\s+(is\\s+)?denied|"
            + "not\\s+authorized|session\\s+expired|please\\s+log\\s?in");
    private static final Pattern MISSING_DATA_MESSAGE_SHAPE = Pattern.compile("(?i)"
            + "\\bnot\\s+found\\b|no\\s+(patient|record|rows?|data|appointment|matches?)\\s+found|no\\s+matching|empty\\s+result");
    private static final Pattern APP_ERROR_MESSAGE_SHAPE = Pattern.compile("(?i)"
            + "save\\s+failed|server\\s+(returned|error)|internal\\s+server\\s+error|something\\s+went\\s+wrong|"
            + "unexpected\\s+error|operation\\s+failed|message\\s+not\\s+found");
    /** Any of the ALREADY-handled raw/technical shapes — when one of these matches, the text is not a plain
     *  application message and must be left to its own existing handling instead. */
    private static boolean isKnownRawShape(String rawActual) {
        if (rawActual == null) return false;
        return TECHNICAL_MARKER.matcher(rawActual).find() || EXCEPTION_SHAPE.matcher(rawActual).find()
                || TIMEOUT_SHAPE.matcher(rawActual).find() || BARE_ASSERTION.matcher(rawActual.trim()).matches()
                || ERR_CODE_SHAPE.matcher(rawActual.trim()).matches() || looksLikeFieldDump(rawActual);
    }

    private static final Pattern VALIDATION_FIELD_PLEASE = Pattern.compile(
            "(?i)please\\s+(?:select|choose|enter|provide|fill(?:\\s+in)?|complete)\\s+(?:a\\s+|an\\s+|the\\s+)?([A-Za-z][A-Za-z ]{0,30}?)\\s*[!.]?\\s*$");
    private static final Pattern VALIDATION_FIELD_REQUIRED = Pattern.compile(
            "(?i)\\b([A-Za-z][A-Za-z ]{0,30}?)\\s+(?:is|are)\\s+required\\b");
    /** Whether the raw EXPECTED text itself describes a positive/success confirmation (a completion toast, a
     *  "successfully" message) — used only to phrase the contrast in a validation-message root cause, never
     *  to invent an expectation that wasn't recorded. */
    private static final Pattern SUCCESS_EXPECTATION_SHAPE = Pattern.compile("(?i)success|\\btoast\\b|confirmation|completed");

    private static String extractValidationFieldName(String normalizedActual) {
        Matcher m1 = VALIDATION_FIELD_PLEASE.matcher(normalizedActual);
        if (m1.find()) return cleanFieldPhrase(m1.group(1));
        Matcher m2 = VALIDATION_FIELD_REQUIRED.matcher(normalizedActual);
        if (m2.find()) return cleanFieldPhrase(m2.group(1));
        return "";
    }

    private static String cleanFieldPhrase(String raw) {
        String s = raw.trim().replaceAll("(?i)^(a|an|the)\\s+", "");
        return s.isBlank() ? "" : s;
    }

    /** What the raw Expected/Actual text ITSELF establishes, read as evidence — independent of, and able to
     *  override, whatever category a separate technical classifier assigned. {@code null} when neither value
     *  reads as one of the four recognized application-message shapes, so the caller falls back to
     *  category-based reasoning unchanged. */
    private static final class EvidenceReading {
        final FailureCategory effectiveCategory;
        final String rootCausePlain, evidencePlain, actualDisplay, fieldGuess;
        EvidenceReading(FailureCategory cat, String rc, String ev, String actualDisplay, String field) {
            this.effectiveCategory = cat;
            this.rootCausePlain = rc;
            this.evidencePlain = ev;
            this.actualDisplay = actualDisplay;
            this.fieldGuess = field;
        }
    }

    private static EvidenceReading readApplicationMessageEvidence(String rawActual, String rawExpected) {
        if (rawActual == null || rawActual.isBlank() || isKnownRawShape(rawActual)) return null;
        String msg = normalizeUiText(rawActual);
        if (msg.isBlank()) return null;

        if (AUTH_MESSAGE_SHAPE.matcher(msg).find()) {
            return new EvidenceReading(FailureCategory.AUTHENTICATION_PERMISSION_ISSUE,
                    "When this step ran, the application responded with its own message: \"" + msg + "\". This is "
                            + "direct evidence of a login or permissions problem — the application itself declined "
                            + "the action, rather than the test being unable to find something on the screen.",
                    "The exact wording of the application's own message, captured at the moment of failure, is the "
                            + "evidence for this conclusion.",
                    "The application responded with its own message: \"" + msg + "\" — application feedback about "
                            + "a login or permissions problem, not a technical error.",
                    "");
        }
        if (MISSING_DATA_MESSAGE_SHAPE.matcher(msg).find()) {
            return new EvidenceReading(FailureCategory.MISSING_TEST_DATA,
                    "When this step ran, the application reported: \"" + msg + "\" — direct evidence that the "
                            + "specific record this test needed does not currently exist in the application, rather "
                            + "than the automation being unable to locate a working screen element.",
                    "The application's own response confirms the record was not found — direct evidence pointing "
                            + "at the test data used, not the application's behavior or the test's own references.",
                    "The application reported: \"" + msg + "\" — application feedback that the expected record "
                            + "does not exist, not a technical error.",
                    "");
        }
        if (VALIDATION_MESSAGE_SHAPE.matcher(msg).find()) {
            String field = extractValidationFieldName(msg);
            String fieldPhrase = field.isBlank() ? "" : " (naming \"" + field + "\")";
            String contrast = SUCCESS_EXPECTATION_SHAPE.matcher(rawExpected == null ? "" : rawExpected).find()
                    ? ", instead of the success confirmation the test expected" : "";
            return new EvidenceReading(FailureCategory.VALIDATION_FAILURE,
                    "When this step ran, the application itself displayed its own message: \"" + msg + "\"" + fieldPhrase
                            + ". This is direct evidence that the application's own validation stopped the action"
                            + contrast + ". The available evidence does not, on its own, establish whether an earlier "
                            + "step in the test should have already satisfied this condition, or whether the "
                            + "application is enforcing it incorrectly here — a person needs to check which.",
                    "The application's own on-screen message at the moment of failure is direct evidence of what "
                            + "the application actually did in response to this step, rather than evidence of a "
                            + "connectivity, environment, or outdated-test-reference problem.",
                    "The application displayed its own message: \"" + msg + "\" — application feedback that a "
                            + "required condition was not met, not a technical error.",
                    field);
        }
        if (APP_ERROR_MESSAGE_SHAPE.matcher(msg).find()) {
            return new EvidenceReading(FailureCategory.APPLICATION_BUG,
                    "When this step ran, the application itself reported: \"" + msg + "\" — direct evidence the "
                            + "application encountered a problem completing this action on its own side, rather "
                            + "than the test being unable to find or use a screen element.",
                    "The application's own error response, captured at the moment of failure, is the evidence for "
                            + "this conclusion.",
                    "The application reported its own error: \"" + msg + "\" — application feedback, not a "
                            + "technical/automation error.",
                    "");
        }
        return null;
    }

    /** The generic owner for a category, used ONLY when application-message evidence (above) overrides the
     *  category assigned upstream — so "Who should investigate" never contradicts the evidence-based Root
     *  Cause it sits next to. Mirrors the same owner {@link FailureCategoryClassifier} itself already assigns
     *  for each of these categories; never a test-specific value. */
    private static String ownerForCategory(FailureCategory category) {
        if (category == null) return "Manual Tester / QA";
        switch (category) {
            case AUTHENTICATION_PERMISSION_ISSUE: return "Admin / Environment Team";
            case NETWORK_INFRASTRUCTURE_ISSUE:
            case ENVIRONMENT_CONFIGURATION_ISSUE: return "DevOps / Environment Team";
            case APPLICATION_BUG: return "Developer";
            case MISSING_TEST_DATA:
            case TEST_DATA_ISSUE:
            case VALIDATION_FAILURE: return "Manual Tester / QA";
            case LOCATOR_SELECTOR_FAILURE:
            case DROPDOWN_FAILURE:
            case BUTTON_UI_FAILURE:
            case AUTOMATION_SCRIPT_ISSUE: return "Automation Tester";
            default: return "Manual Tester / QA";
        }
    }

    // ---- "who should fix it" / "recommended fix" — layered on top of the category already computed --------

    /** Why {@code owner} (the category's already-computed {@code recommendedOwner}) is the right team —
     *  purely explanatory, derived from the same category, never a new judgement of its own. */
    public static String whyThisOwnerPlain(FailureCategory category) {
        if (category == null) return "the available evidence does not clearly point to one team on its own, so an initial investigation is needed first.";
        switch (category) {
            case LOCATOR_SELECTOR_FAILURE:
                return "the available evidence indicates the test's own reference to a screen element may no longer match the application, which is an automation-side reference to review.";
            case DROPDOWN_FAILURE:
            case BUTTON_UI_FAILURE:
            case AUTOMATION_SCRIPT_ISSUE:
                return "the failure involves how the automated test interacts with the screen, which is an automation-side concern.";
            case MISSING_TEST_DATA:
            case TEST_DATA_ISSUE:
                return "the evidence points at the data used for this test, not at the application or the automation itself.";
            case APPLICATION_BUG:
                return "the evidence points at the application's own behavior not matching the expected business rule.";
            case VALIDATION_FAILURE:
                return "the evidence shows a mismatch that could be a real application issue or a stale test assumption, so someone needs to confirm which before this can be routed further.";
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                return "the evidence points at something specific to this machine or environment's own setup, not the application's logic.";
            case NETWORK_INFRASTRUCTURE_ISSUE:
                return "the evidence points at connectivity or server availability at the time this test ran, not the application's own logic.";
            case AUTHENTICATION_PERMISSION_ISSUE:
                return "the evidence points at a login or access problem, before the actual feature being tested was ever reached.";
            case UNKNOWN_NEEDS_MANUAL_INVESTIGATION:
            default:
                return "the available evidence is not specific enough to point at one team on its own, so an initial investigation is needed first.";
        }
    }

    /** What should actually be done next — dynamically by category, never exposing a raw locator/code value
     *  (those stay in Technical Details only). */
    public static String recommendedFixPlain(FailureCategory category, String fieldName) {
        String field = (fieldName == null || fieldName.isBlank()) ? "affected field or control" : "\"" + fieldName + "\" field";
        if (category == null) return "Review the failed step's evidence manually to determine the appropriate next action.";
        switch (category) {
            case LOCATOR_SELECTOR_FAILURE:
                return "Review the test's stored reference for the " + field + " (see Technical Details) and "
                        + "compare it with the control currently exposed by the application on that screen. If the "
                        + "application's control is unchanged and only the test's reference is outdated or "
                        + "misspelled, update the test's reference to match the current control — this is a "
                        + "low-risk, automation-only change since the underlying screen itself is not being modified.";
            case DROPDOWN_FAILURE:
                return "Check whether the " + field + " actually finishes loading its options before the test "
                        + "tries to use it (a timing issue), and separately confirm the test's stored reference for "
                        + "it still matches the current screen (a reference issue) — the evidence above indicates "
                        + "which of the two applies here.";
            case BUTTON_UI_FAILURE:
                return "Confirm the " + field + " is present, enabled, and visible on the screen at the time this "
                        + "step runs, and separately confirm the test's reference to it is still correct — the "
                        + "evidence above indicates which of the two applies here.";
            case MISSING_TEST_DATA:
                return "Create or confirm the specific record this test expects exists in the application before re-running the test.";
            case TEST_DATA_ISSUE:
                return "Review the test data used for this step and confirm it matches what the application currently expects.";
            case APPLICATION_BUG:
                return "Have a developer confirm whether the application's behavior matches the intended business rule for this screen.";
            case VALIDATION_FAILURE:
                return "Have a tester confirm whether this mismatch is a genuine application issue or an outdated "
                        + "test expectation. If it turns out to be a genuine application issue, route it to a "
                        + "developer to fix; if it's a stale test expectation, update the test.";
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                return "Review the environment or configuration this test ran in for anything specific to this machine or setup.";
            case AUTHENTICATION_PERMISSION_ISSUE:
                return "Confirm the test's login credentials and permissions are still valid for this environment.";
            case NETWORK_INFRASTRUCTURE_ISSUE:
                return "Confirm the network, server, or database this test depends on was reachable at the time it ran.";
            case AUTOMATION_SCRIPT_ISSUE:
                return "Review the automation script's own timing or workflow handling for this step.";
            case UNKNOWN_NEEDS_MANUAL_INVESTIGATION:
            default:
                return "A person should manually review this failure's evidence to determine the appropriate next step.";
        }
    }

    // ---- "Failure Summary" narrative paragraph — a distinct story, never Expected+Actual concatenated ------

    /** A short, human narrative of what happened — FACT (what the test was doing) -&gt; FAILURE/IMPACT (why it
     *  stopped) -&gt; likely CAUSE (the same root-cause sentence Section E already shows, restated here as the
     *  closing clause of one cohesive story) — dynamically generated from the test name, step, category, and
     *  the already-computed plain root cause; never repeats the raw Expected/Actual text verbatim (those
     *  already have their own C/D sections). */
    private static String failureSummary(String testClass, String step, FailureCategory category, String causePlain) {
        String test = friendlyName(testClass);
        String cleanStep = cleanStepName(step);
        String consequence;
        if (category == null) category = FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION;
        switch (category) {
            case MISSING_TEST_DATA:
            case TEST_DATA_ISSUE:
                consequence = "the information the test needed was not available in the application, so the test could not continue.";
                break;
            case DROPDOWN_FAILURE:
            case BUTTON_UI_FAILURE:
            case LOCATOR_SELECTOR_FAILURE:
                consequence = "the test could not find or use a required part of the screen, so this step did not complete successfully.";
                break;
            case VALIDATION_FAILURE:
            case APPLICATION_BUG:
                consequence = "what the application showed did not match what the test expected, so the check did not pass.";
                break;
            case AUTHENTICATION_PERMISSION_ISSUE:
                consequence = "the test could not sign in or was not permitted to continue, before the actual feature being tested was reached.";
                break;
            case NETWORK_INFRASTRUCTURE_ISSUE:
            case ENVIRONMENT_CONFIGURATION_ISSUE:
                consequence = "the application or environment the test depends on was not available in time, so the test could not continue.";
                break;
            default:
                consequence = "the test could not confirm that this step completed successfully, and the available evidence does not clearly explain why on its own.";
        }
        StringBuilder out = new StringBuilder("The \"" + test + "\" test was attempting to complete the \"" + cleanStep
                + "\" step. The test could not complete this step because " + consequence + " As a result, the "
                + "test stopped here, and the remaining steps in this test were not carried out.");
        if (causePlain != null && !causePlain.isBlank()) {
            out.append(" ").append(causePlain);
        }
        return out.toString();
    }

    // ---- "what the evidence inventory tells us" — a generic, keyword-based plain summary of the rich, ------
    // ---- semi-structured Evidence Available text, never replacing it, always alongside it. ------------------

    /** Turns the rich, semi-structured {@code evidenceAvailable} inventory (URL/DOM capture, Dropdown
     *  Diagnostics, locator-present flags, etc. — the same text kept verbatim in Technical Details) into a
     *  short plain sentence naming WHAT KINDS of evidence were gathered, purely by recognizing the same
     *  handful of phrase shapes every category's evidence text already uses — never a specific field/test
     *  name. Returns {@code ""} when nothing recognizable is found, so callers can omit the row entirely
     *  rather than render a filler sentence. */
    public static String evidenceInventoryPlain(String evidenceAvailable) {
        if (evidenceAvailable == null || evidenceAvailable.isBlank()) return "";
        String e = evidenceAvailable;
        List<String> facts = new ArrayList<>();
        if (Pattern.compile("(?i)url captured").matcher(e).find()) {
            facts.add("which screen the application was actually showing");
        }
        if (Pattern.compile("(?i)dom capture").matcher(e).find()) {
            facts.add("the buttons, selection fields, and headings actually present on the page at that moment");
        }
        if (Pattern.compile("(?i)settled.{0,20}dom snapshot").matcher(e).find()) {
            facts.add("a second check taken a few seconds later, in case the page was still loading");
        }
        if (Pattern.compile("(?i)locator present:\\s*no\\b").matcher(e).find()) {
            facts.add("confirmation that the test's reference did NOT match anything live on the page");
        } else if (Pattern.compile("(?i)locator present:\\s*yes\\b").matcher(e).find()) {
            facts.add("confirmation that the test's reference DID match something live on the page");
        }
        if (Pattern.compile("(?i)options detected:\\s*(?!n/a)\\S").matcher(e).find()) {
            facts.add("the list of options actually available in the field at that moment");
        }
        if (Pattern.compile("(?i)responsible page object method located").matcher(e).find()) {
            facts.add("which part of the automated test is responsible for this step");
        }
        if (Pattern.compile("(?i)candidate locator evidence").matcher(e).find()) {
            facts.add("whether a closely-matching, currently-live alternative reference could be found");
        }
        if (facts.isEmpty()) return "";
        return "To reach this conclusion, the system also captured " + joinPlain(facts) + " — see Technical "
                + "Details for the complete, itemized evidence.";
    }

    // ---- screenshot "what it proves / does not prove" — never claims more than the image itself shows -----

    public static final class ScreenshotExplanation {
        public final String whatItShows, whatItTellsUs, whyItMatters, whatItDoesNotProve;
        ScreenshotExplanation(String shows, String tellsUs, String whyMatters, String notProves) {
            this.whatItShows = shows;
            this.whatItTellsUs = tellsUs;
            this.whyItMatters = whyMatters;
            this.whatItDoesNotProve = notProves;
        }
    }

    /** Honest, dynamic, 4-part explanation of a failure screenshot — never claims the image itself proves the
     *  cause (that conclusion always comes from the execution evidence, never from pixels alone), and never
     *  substitutes the test/fixture name for a screen it could not actually identify. Depends only on the
     *  already-resolved screen/field — the same explanation applies to every screenshot taken for this one
     *  failure (original, after-healing, after-fix), so {@link #explain} computes it once and every caller
     *  reuses that one result rather than re-deriving it per screenshot.
     *  @param screenName the real application screen from {@link #deriveScreenName}, or "" when unknown
     *  @param fieldName the affected field/control, or "" when unknown — see {@link RootCauseEvidence#affectedFieldName} */
    private static ScreenshotExplanation screenshotExplanation(String screenName, String fieldName) {
        boolean hasScreen = screenName != null && !screenName.isBlank();
        boolean hasField = fieldName != null && !fieldName.isBlank();
        String shows = hasScreen
                ? "The application is displaying the \"" + screenName + "\" screen" + (hasField
                        ? ", which contains the \"" + fieldName + "\" field/control involved in the failed step."
                        : ".")
                : "The application's on-screen state at the moment of failure. (" + SCREEN_NAME_UNKNOWN + ")";
        String tellsUs = hasField
                ? "This confirms which application screen was open and shows the on-screen state of the \""
                        + fieldName + "\" field/control at the moment of failure, for context alongside the "
                        + "evidence described above."
                : "This confirms the application's on-screen state at the moment the step failed, for context "
                        + "alongside the evidence described above.";
        String whyMatters = "It establishes what the application actually looked like at the moment the test "
                + "could not complete the requested action, supporting the explanation given above.";
        String notProves = "The screenshot alone cannot determine WHY this happened — for example, whether an "
                + "earlier step should have already set this up, whether the application is enforcing a rule it "
                + "shouldn't, whether a technical/environment problem was involved, or something else entirely — "
                + "that conclusion comes only from the execution evidence described above, never from the image "
                + "by itself.";
        return new ScreenshotExplanation(shows, tellsUs, whyMatters, notProves);
    }

    public static String confidenceInWords(double confidence) {
        if (confidence < 0) return "not applicable";
        if (confidence >= 0.85) return "very confident";
        if (confidence >= 0.65) return "fairly confident";
        if (confidence >= 0.5) return "somewhat confident";
        return "not very confident";
    }

    public static String riskInWords(String risk) {
        if (risk == null) return "";
        switch (risk.toUpperCase()) {
            case "LOW":
                return "This is a LOW-risk change — it only affects how the test recognizes one screen "
                        + "element; it does not touch any business data or logic.";
            case "MEDIUM":
                return "This is a MEDIUM-risk change — worth a careful look before approving.";
            case "HIGH":
                return "This is a HIGH-risk change and deserves close review before approval.";
            default:
                return "";
        }
    }

    /** Exact wording requested for the two possible outcomes — used verbatim, never reworded per-run. */
    public static final String KEEP_EXPLANATION =
            "The proposed fix was applied, the test passed, and no downstream regression was detected, "
                    + "so the fix was retained.";
    public static final String ROLLBACK_EXPLANATION =
            "The proposed fix was applied, but verification failed or another problem was detected, "
                    + "so the system restored the original source code from the backup.";

    /** The rollback itself could not be verified - so the report must NOT say the original code was restored. */
    public static final String RESTORE_FAILED_EXPLANATION =
            "The proposed fix was applied and then failed verification, but the original source code could NOT be "
                    + "restored and verified. The source file may still contain the patch and needs a manual restore from the backup.";

    public static String finalOutcomePlain(String finalStatus) {
        if (finalStatus == null) return "";
        switch (finalStatus) {
            case "KEPT": return KEEP_EXPLANATION;
            case "REVERTED": return ROLLBACK_EXPLANATION;
            case "RESTORE_FAILED": return RESTORE_FAILED_EXPLANATION;
            case "NOT_APPLIED": return "The proposal was not applied — the reviewer declined it, or it was never approved.";
            case "REJECTED_VERIFICATION_FAILED": return "A human reviewer explicitly rejected this exact proposal; it will not be offered again.";
            case "DRY_RUN": return "This was a preview only — nothing was ever written to a source file.";
            default: return "This step needs a person to look at it — the system could not safely decide on its own.";
        }
    }

    /** Standard product branding for both reports — combined heading, distinct internal-stage subtitle. */
    public static final String PRODUCT_NAME = "Intelligent Test Healing &amp; Failure Analysis";

    // ---- one shared "Final Result" vocabulary for both report types (section I) --------------------------

    public static final String FINAL_FAILURE_ANALYSIS_ONLY = "FAILED — FAILURE ANALYSIS ONLY";
    public static final String FINAL_FIX_RETAINED = "FIX APPLIED AND RETAINED";
    public static final String FINAL_FIX_ROLLED_BACK = "FIX APPLIED BUT ROLLED BACK";
    public static final String FINAL_PASSED_NO_FIX = "PASSED — NO FIX REQUIRED";
    public static final String FINAL_RESTORE_FAILED = "ROLLBACK FAILED — MANUAL RESTORE REQUIRED";

    /** One short plain-English sentence explaining whichever {@code FINAL_*} label applies — the same wording
     *  regardless of which report (Failure Analysis or Self-Healing) is showing it. */
    public static String finalResultExplanation(String label) {
        if (FINAL_FIX_RETAINED.equals(label)) {
            return "A correction was proposed, approved, applied, and it passed when checked again, so it was kept.";
        }
        if (FINAL_FIX_ROLLED_BACK.equals(label)) {
            return "A correction was proposed, approved, and applied, but it did not pass when checked again, so it "
                    + "was undone and the original code was restored.";
        }
        if (FINAL_RESTORE_FAILED.equals(label)) {
            return "A correction was applied and did not pass verification, and the original code could not be restored and "
                    + "verified. The source file may still contain the patch - restore it manually from the backup.";
        }
        if (FINAL_PASSED_NO_FIX.equals(label)) {
            return "The test completed successfully, so no correction was ever needed.";
        }
        return "The test failed and was analyzed, but no automatic correction was applied for this failure.";
    }

    /** Same name as {@link #PRODUCT_NAME}, but raw (un-entity-encoded) — for a context (like an HTML
     *  {@code <title>}) that is itself passed through an {@code esc()} call; using the already-encoded
     *  {@link #PRODUCT_NAME} there would double-encode the ampersand ("&amp;amp;"). */
    public static final String PRODUCT_NAME_PLAIN = "Intelligent Test Healing & Failure Analysis";

    /** The standard {@code <title>} format shared by every report: {@code "<product> | <part> | <part> ..."}.
     *  Callers pass raw (un-escaped) parts; the whole string is escaped once by the caller's own
     *  {@code htmlHead()}. */
    public static String pipeTitle(String... parts) {
        StringBuilder sb = new StringBuilder(PRODUCT_NAME_PLAIN);
        for (String p : parts) if (p != null && !p.isBlank()) sb.append(" | ").append(p);
        return sb.toString();
    }

    /** Plain, non-jargon label for a {@link FailureCategory} — exposed as {@link FailureExplanation#categoryLabel}
     *  for the ONE place a category name appears outside a "Technical Details" section (the Overall Summary's
     *  "Problem" column). The raw {@code FailureCategory.displayName()} ("Locator/Selector Failure", etc.)
     *  stays exactly as-is everywhere else, including every per-test report's own Technical Details table —
     *  this is purely an additional, friendlier label for that one summary-table context. */
    private static String categoryPlain(FailureCategory category) {
        if (category == null) return "Needs Investigation";
        switch (category) {
            case MISSING_TEST_DATA: return "Missing Test Data";
            case TEST_DATA_ISSUE: return "Test Data Mismatch";
            case DROPDOWN_FAILURE: return "Dropdown / Selection Issue";
            case BUTTON_UI_FAILURE: return "Button / Screen Control Issue";
            case LOCATOR_SELECTOR_FAILURE: return "Outdated Screen Reference";
            case VALIDATION_FAILURE: return "Unexpected Screen Behavior";
            case APPLICATION_BUG: return "Possible Application Issue";
            case ENVIRONMENT_CONFIGURATION_ISSUE: return "Environment / Setup Issue";
            case AUTHENTICATION_PERMISSION_ISSUE: return "Login / Access Issue";
            case NETWORK_INFRASTRUCTURE_ISSUE: return "Connectivity Issue";
            case AUTOMATION_SCRIPT_ISSUE: return "Test Script Needs Attention";
            case UNKNOWN_NEEDS_MANUAL_INVESTIGATION:
            default: return "Needs Investigation";
        }
    }

    // ============================================================================================
    // ONE SHARED HUMAN-READABLE FAILURE MODEL — both FailureAnalysisReport and HealingDashboard
    // derive their entire A-G business-facing story from THIS one builder, never by independently
    // re-deriving the same fact in two places. Every field below is composed from the exact same
    // already-existing methods above (nothing duplicated, nothing re-implemented) — this class only
    // fixes the ORDER/COMPOSITION problem: previously each report file called several of these
    // methods separately, with real risk that one row (e.g. "Failed Step") used a cleaned value
    // while another (e.g. "Failure Summary") used the raw one — the exact class of bug found and
    // fixed repeatedly while this logic was scattered. Now every row in both reports reads from
    // ONE object, computed ONCE per failure, so that can no longer happen by construction.
    // ============================================================================================

    public static final class FailureExplanation {
        /** The real application screen, or {@code ""} when genuinely undeterminable — see {@link #resolveScreenName}. */
        public final String applicationScreen;
        /** "What the test was trying to do" — screen + field + action + verification, never the test name. */
        public final String whatWasTried;
        /** The failed step, cleaned of scaffolding annotations — see {@link #describeFailedStep}. */
        public final String failedStep;
        public final String expected;
        public final String actual;
        /** Why it failed — one grounded sentence, never repeating {@link #actual} verbatim. */
        public final String whyItFailed;
        /** What the evidence tells us — proves/supports {@link #whyItFailed}. */
        public final String evidencePlain;
        /** How the evidence was gathered — "" when the raw inventory had nothing recognizable to summarize. */
        public final String evidenceGathered;
        /** The specific field/control this was traced to, or {@code ""} — exposed so callers (e.g. a
         *  screenshot caption, or an SH-specific extra sentence) can stay consistent with this same value. */
        public final String affectedField;
        /** Who should investigate/fix this — the category's already-computed recommendation, passed through verbatim. */
        public final String responsibleOwner;
        public final String whyOwner;
        public final String recommendedFix;
        public final String whyFix;
        /** The FACT -&gt; FAILURE/IMPACT -&gt; CAUSE narrative paragraph — a distinct story, never Expected+Actual
         *  concatenated. See the (now-internal) {@code failureSummary} builder this composes. */
        public final String failureSummary;
        /** Short, plain, non-jargon label for the failure's category — for a compact context (e.g. a summary
         *  table's "Problem" column) where {@link #whyItFailed}'s full sentence would be too long. */
        public final String categoryLabel;
        /** 4-part screenshot explanation — same for every screenshot taken of this one failure (original,
         *  after-healing, after-fix), since all four only ever depend on {@link #applicationScreen} /
         *  {@link #affectedField}, already resolved once above. */
        public final String screenshotWhatItShows, screenshotWhatItTellsUs, screenshotWhyItMatters, screenshotWhatItDoesNotProve;
        /** "YES"/"NO" — passed through verbatim from the caller's own already-decided eligibility (a
         *  classification/gating decision this class never makes itself; see {@link #explain}). */
        public final String automaticFixPossible;
        /** Plain-English reason paired with {@link #automaticFixPossible}, for either outcome — generic by
         *  category, from {@code healingPossiblePlain}. */
        public final String whatHappensNext;
        /** The category this whole explanation was actually built from — the caller's own {@code category}
         *  argument UNLESS application-message evidence (see {@link #explain}) overrode it. This is the ONE
         *  authoritative category every field above (whyItFailed, responsibleOwner, whyOwner, recommendedFix,
         *  categoryLabel, whatHappensNext) is already consistent with — callers that also display a raw,
         *  classifier-only category (e.g. a Technical Details table) must use THIS field wherever that display
         *  is presented as authoritative, never the caller's original, possibly-overridden category, so a
         *  report can never show two different categories for the same failure. */
        public final FailureCategory effectiveCategory;

        private FailureExplanation(String applicationScreen, String whatWasTried, String failedStep, String expected,
                                    String actual, String whyItFailed, String evidencePlain, String evidenceGathered,
                                    String affectedField, String responsibleOwner, String whyOwner,
                                    String recommendedFix, String whyFix, String failureSummary, String categoryLabel,
                                    ScreenshotExplanation screenshot, String automaticFixPossible, String whatHappensNext,
                                    FailureCategory effectiveCategory) {
            this.applicationScreen = applicationScreen;
            this.whatWasTried = whatWasTried;
            this.failedStep = failedStep;
            this.expected = expected;
            this.actual = actual;
            this.whyItFailed = whyItFailed;
            this.evidencePlain = evidencePlain;
            this.evidenceGathered = evidenceGathered;
            this.affectedField = affectedField;
            this.responsibleOwner = responsibleOwner;
            this.whyOwner = whyOwner;
            this.recommendedFix = recommendedFix;
            this.whyFix = whyFix;
            this.failureSummary = failureSummary;
            this.categoryLabel = categoryLabel;
            this.screenshotWhatItShows = screenshot.whatItShows;
            this.screenshotWhatItTellsUs = screenshot.whatItTellsUs;
            this.screenshotWhyItMatters = screenshot.whyItMatters;
            this.screenshotWhatItDoesNotProve = screenshot.whatItDoesNotProve;
            this.automaticFixPossible = automaticFixPossible;
            this.whatHappensNext = whatHappensNext;
            this.effectiveCategory = effectiveCategory;
        }
    }

    /**
     * The ONE builder both reports call — never re-derive any of these facts independently elsewhere.
     *
     * @param testClass the test's fully-qualified class name (used ONLY for {@code friendlyName}-style
     *                   labeling elsewhere, e.g. the "Test" row — never as the application screen)
     * @param rawStep the failure's recorded step name, exactly as captured
     * @param rawExpected the failure's recorded expected text, exactly as captured
     * @param rawActual the failure's recorded actual text, exactly as captured
     * @param category the business-facing {@link FailureCategory} (for SH, always LOCATOR_SELECTOR_FAILURE —
     *                  see the call site comment in HealingDashboard for why that's accurate there)
     * @param technicalRootCause the raw root-cause diagnostic text, exactly as captured (may be null/blank)
     * @param technicalEvidence the raw evidence lines already available as a list (FA callers split their
     *                           own semicolon/pipe-joined inventory string first; SH already has a List)
     * @param affectedFile the responsible Page Object file {@link CodeLocator} resolved, or "" if none
     * @param evidenceInventoryRaw the FA-only semi-structured evidence inventory string (for "how this
     *                             evidence was gathered") — pass "" when not applicable (e.g. from SH)
     * @param recommendedOwnerRaw the category's already-computed owner recommendation (never invented here)
     * @param selfHealingEligible the ALREADY-DECIDED eligibility for this failure (from {@code
     *                            FailureCategoryClassifier}/{@code SelfHealingAgent}'s own gating — this
     *                            class never re-derives eligibility itself, only turns the decision into
     *                            plain language; see {@link FailureExplanation#automaticFixPossible})
     */
    public static FailureExplanation explain(String testClass, String rawStep, String rawExpected, String rawActual,
                                              FailureCategory category, String technicalRootCause,
                                              List<String> technicalEvidence, String affectedFile,
                                              String evidenceInventoryRaw, String recommendedOwnerRaw,
                                              boolean selfHealingEligible) {
        return explain(testClass, rawStep, rawExpected, rawActual, category, technicalRootCause, technicalEvidence,
                affectedFile, evidenceInventoryRaw, recommendedOwnerRaw, selfHealingEligible, null, null);
    }

    /** Same as the 10-arg {@link #explain}, but lets a caller supply its OWN root-cause/evidence fallback text
     *  instead of the category-only default — needed by the Self-Healing report, whose candidate can be
     *  LOCATOR/TIMING/WORKFLOW/LLM shaped (richer than any single {@link FailureCategory} value expresses;
     *  see {@code HealingDashboard}'s own {@code proposalKind}-aware fallback methods). Pass {@code null} for
     *  either to fall back to the plain category-based default (what the 10-arg overload always does). */
    public static FailureExplanation explain(String testClass, String rawStep, String rawExpected, String rawActual,
                                              FailureCategory category, String technicalRootCause,
                                              List<String> technicalEvidence, String affectedFile,
                                              String evidenceInventoryRaw, String recommendedOwnerRaw,
                                              boolean selfHealingEligible,
                                              String customRootCauseFallback, String customEvidenceFallback) {
        String dumpField = extractProblemFieldName(rawActual);
        boolean navFailure = isNavigationFailure(rawActual);
        // EVIDENCE PRIORITY: the raw Expected/Actual TEXT itself — what the application actually displayed —
        // outranks the category a separate technical classifier already assigned (see readApplicationMessageEvidence's
        // own javadoc). Checked BEFORE the classifier-driven fallback, so evidence can override a wrong category
        // instead of being overridden BY it; never checked when a navigation failure already has its own, even
        // more specific, explanation.
        EvidenceReading appMsg = navFailure ? null : readApplicationMessageEvidence(rawActual, rawExpected);
        FailureCategory effectiveCategory = appMsg != null ? appMsg.effectiveCategory : category;

        String rootFallback = customRootCauseFallback != null ? customRootCauseFallback : whyItFailedPlainForField(category, dumpField);
        String evFallback = customEvidenceFallback != null ? customEvidenceFallback : evidenceFallbackPlainForField(category, dumpField);
        RootCauseEvidence rce = navFailure
                ? new RootCauseEvidence(navigationRootCausePlain(rawActual), navigationEvidencePlain(rawActual))
                : appMsg != null
                        ? new RootCauseEvidence(appMsg.rootCausePlain, appMsg.evidencePlain, appMsg.fieldGuess)
                        : explainDynamic(technicalRootCause, technicalEvidence, rootFallback, evFallback);
        String affectedField = !rce.affectedFieldName.isBlank() ? rce.affectedFieldName : dumpField;
        String screenName = resolveScreenName(affectedFile, technicalRootCause, technicalEvidence);
        String expected = expectedOutcomePlain(rawExpected, affectedField, screenName);
        // The application's own on-screen message (when found above) already explains itself in Section D's
        // own words — reusing the generic field-aware fallback here would re-describe the SAME evidence in a
        // second, less specific way and risk sounding inconsistent with the Root Cause right below it.
        String actual = appMsg != null ? appMsg.actualDisplay : actualOutcomePlain(rawActual, affectedField);
        String whatWasTried = whatWasTriedPlain(screenName, affectedField);
        String failedStep = describeFailedStep(rawStep, screenName, affectedField);
        String evidenceGathered = evidenceInventoryPlain(evidenceInventoryRaw);
        // Owner/fix/category-label/"what happens next" all key off effectiveCategory (not the raw, possibly
        // evidence-contradicted, category) so this section can never disagree with the Root Cause above it —
        // the CONSISTENCY requirement this whole layer exists to satisfy. Only when evidence did NOT override
        // anything does the owner fall back to the classifier's own already-computed recommendation verbatim.
        String owner = appMsg != null ? ownerForCategory(effectiveCategory) : (recommendedOwnerRaw == null ? "" : recommendedOwnerRaw);
        String whyOwner = whyThisOwnerPlain(effectiveCategory);
        String fix = recommendedFixPlain(effectiveCategory, affectedField);
        String summary = failureSummary(testClass, rawStep, effectiveCategory, rce.rootCausePlain);
        String categoryLabel = categoryPlain(effectiveCategory);
        ScreenshotExplanation screenshot = screenshotExplanation(screenName, affectedField);
        // Eligibility itself is NEVER re-derived here — selfHealingEligible is passed straight through, exactly
        // as SelfHealingAgent/FailureCategoryClassifier already decided it. Only the WORDING of "why" is kept
        // consistent with the evidence-based category above, so this line can't contradict the Root Cause either.
        String automaticFixPossible = selfHealingEligible ? "YES" : "NO";
        String whatHappensNext = healingPossiblePlain(selfHealingEligible, effectiveCategory);

        return new FailureExplanation(screenName, whatWasTried, failedStep, expected, actual, rce.rootCausePlain,
                rce.evidencePlain, evidenceGathered, affectedField, owner, whyOwner, fix, rce.rootCausePlain,
                summary, categoryLabel, screenshot, automaticFixPossible, whatHappensNext, effectiveCategory);
    }
}
