package com.kpj.heal;

import java.util.*;
import java.util.regex.*;

/**
 * The full cross-check behind "is this a SCRIPT miss or an APPLICATION problem?": it reads what the failing Page Object method LOOKS FOR on
 * the screen (button / link labels, {@code ng-click} handlers), reads what the live page ACTUALLY SHOWED when the step failed (the buttons and
 * click handlers captured at failure time and again a few seconds later), compares the two, and reaches an explicit, evidence-backed verdict.
 *
 * <p>Verdicts:</p>
 * <ul>
 *   <li>{@link Verdict#SCRIPT_STALE_LIKELY} - none of the things the script looks for is on the page, but a similarly-named control is (for
 *       example the script wants "Print Barcode" and the page now offers "Print"). The application still has the feature; the script is out
 *       of date.</li>
 *   <li>{@link Verdict#APPLICATION_OR_STATE_LIKELY} - none of the things the script looks for is on the page and nothing similar is either,
 *       although the page itself rendered. The control is gone or hidden (removed by the application, or hidden by permission / patient
 *       state) - not something a script edit explains.</li>
 *   <li>{@link Verdict#SCRIPT_LOCATOR_OK} - what the script looks for IS on the page, so the locator is not the problem; whatever went wrong
 *       happened after (the application's response, the data, or timing).</li>
 * </ul>
 *
 * <p><b>Analysis only.</b> Nothing here is shaped like a patch (no old/new snippet), so nothing in it can ever be applied; a similar label is
 * evidence, not proof that it is the same function, so the verdict is always worded as "likely" and always needs a person to confirm.</p>
 */
public final class ScriptVsDomDiagnostics {

    private ScriptVsDomDiagnostics() {}

    public enum Verdict { SCRIPT_STALE_LIKELY, APPLICATION_OR_STATE_LIKELY, SCRIPT_LOCATOR_OK }

    public static final class Finding {
        public final Verdict verdict;
        /** One plain sentence stating the verdict - safe to show as the headline. */
        public final String headline;
        /** The itemised evidence: what the script looks for, what the page showed, what matched. */
        public final List<String> lines;
        public final String suggestedAction;

        Finding(Verdict verdict, String headline, List<String> lines, String suggestedAction) {
            this.verdict = verdict;
            this.headline = headline;
            this.lines = lines;
            this.suggestedAction = suggestedAction;
        }
    }

    /** The capture holds at most this many click handlers (see DevHisBase), so a full list proves nothing about a handler that is missing. */
    static final int CLICK_CAPTURE_LIMIT = 60;

    // ---- what the script looks for (read from the Page Object method's source text) ----------------------------------------------
    /** {@code /^\s*print barcode\s*$/i} - a whole-label match written as a regular expression. */
    private static final Pattern LABEL_REGEX = Pattern.compile("/\\^(?:\\\\s\\*)?([^/$^\\\\]{2,50}?)(?:\\\\s\\*)?\\$/i");
    private static final Pattern LABEL_HAS_TEXT = Pattern.compile("has-text\\(\\s*['\"]([^'\"]{2,40})['\"]\\s*\\)");
    private static final Pattern LABEL_GET_BY_TEXT = Pattern.compile("getByText\\(\\s*\"([^\"]{2,40})\"");
    private static final Pattern LABEL_ROLE_NAME = Pattern.compile("setName\\(\\s*\"([^\"]{2,40})\"");
    private static final Pattern LABEL_TEXT_EQUALS = Pattern.compile("(?:textContent|innerText)[^;{}]{0,70}?===?\\s*'([^']{2,40})'");
    /** {@code ng-click ... .indexOf('printpatient')} - a handler looked up by name inside the ng-click attribute. */
    private static final Pattern HANDLER_INDEXOF = Pattern.compile("ng-click[^;]{0,70}?indexOf\\(\\s*'([A-Za-z_][A-Za-z0-9_]{2,})'\\s*\\)");
    /** {@code [ng-click*='saveBarcode']} / {@code [ng-click="fn()"]} - a handler in an attribute selector. */
    private static final Pattern HANDLER_SELECTOR = Pattern.compile("ng-click[*^$|~]?=\\s*['\"]?([A-Za-z_][A-Za-z0-9_]{2,})");

    /**
     * @return {@code null} when the method names no button/link label or click handler to compare (nothing to cross-check), or when the page
     *         captured at failure time shows no controls at all (nothing to compare it with). Never throws.
     */
    public static Finding diagnose(String methodSource, FailureRecord failure) {
        try {
            return diagnoseInternal(methodSource, failure);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Finding diagnoseInternal(String methodSource, FailureRecord f) {
        if (methodSource == null || methodSource.isBlank() || f == null) return null;
        // the Page Object text has regex escapes doubled inside its Java strings ("\\s*"); fold them back so the patterns above see "\s*"
        String src = methodSource.replace("\\\\", "\\");

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (Pattern p : new Pattern[]{LABEL_REGEX, LABEL_HAS_TEXT, LABEL_GET_BY_TEXT, LABEL_ROLE_NAME, LABEL_TEXT_EQUALS}) {
            Matcher m = p.matcher(src);
            while (m.find()) {
                String l = norm(m.group(1));
                if (l.length() >= 2) labels.add(l);
            }
        }
        LinkedHashSet<String> handlers = new LinkedHashSet<>();
        for (Pattern p : new Pattern[]{HANDLER_INDEXOF, HANDLER_SELECTOR}) {
            Matcher m = p.matcher(src);
            while (m.find()) handlers.add(m.group(1));
        }
        if (labels.isEmpty() && handlers.isEmpty()) return null;

        // ---- what the page showed (at failure time, and a few seconds later) --------------------------------------------------------
        LinkedHashSet<String> domLabels = new LinkedHashSet<>();
        addAll(domLabels, f.buttons);
        addAll(domLabels, f.settledButtons);
        if (domLabels.isEmpty()) return null;   // no controls captured - nothing to compare against, say nothing
        List<String> clicks = new ArrayList<>();
        for (String c : split(f.ngClicks)) clicks.add(c);
        for (String c : split(f.settledNgClicks)) if (!clicks.contains(c)) clicks.add(c);

        List<String> lines = new ArrayList<>();
        lines.add("The script (" + methodNameHint(methodSource) + ") looks for: "
                + join(quote(labels), "button/link label ") + (labels.isEmpty() || handlers.isEmpty() ? "" : " ; ")
                + join(quote(handlers), "click handler "));
        lines.add("The step reported: \"" + safe(f.actual) + "\"");
        lines.add("The page captured at failure time showed " + domLabels.size() + " button/link control(s): " + String.join(" | ", domLabels));

        boolean anyExactLabel = false;
        for (String want : labels) {
            for (String have : domLabels) if (norm(have).equals(want)) { anyExactLabel = true; lines.add("MATCH: the label \"" + want + "\" IS on the page."); }
        }
        boolean anyHandlerSeen = false;
        for (String h : handlers) {
            boolean seen = false;
            for (String c : clicks) if (c.toLowerCase().contains(h.toLowerCase())) { seen = true; break; }
            if (seen) { anyHandlerSeen = true; lines.add("MATCH: the click handler \"" + h + "\" IS on the page."); }
            else lines.add("NOT SEEN: the click handler \"" + h + "\" is not among the " + clicks.size() + " captured handler(s)"
                    + (clicks.size() >= CLICK_CAPTURE_LIMIT
                    ? " - the capture is full (limit " + CLICK_CAPTURE_LIMIT + "), so this alone does not prove it is absent."
                    : "."));
        }

        if (anyExactLabel || anyHandlerSeen) {
            lines.add("The caveat: this assumes the captured page is the page the script searched.");
            return new Finding(Verdict.SCRIPT_LOCATOR_OK,
                    "SCRIPT LOCATOR OK - the control the script looks for is on the page, so the script found its target; the failure came after "
                            + "that (the application's response, the data, or timing).",
                    lines,
                    "Do not edit the locator. Check what the application did after the control was used (a message, a missing popup, empty data) "
                            + "and whether it needs more time.");
        }

        // nothing the script looks for is on the page: is something similar there?
        List<String[]> near = new ArrayList<>();   // {score, label, wanted}
        for (String want : labels) {
            for (String have : domLabels) {
                double s = similarity(want, norm(have));
                if (s >= 0.34) near.add(new String[]{String.format(Locale.ROOT, "%.2f", s), have, want});
            }
        }
        near.sort((a, b) -> Double.compare(Double.parseDouble(b[0]), Double.parseDouble(a[0])));

        if (!near.isEmpty()) {
            String[] best = near.get(0);
            StringBuilder others = new StringBuilder();
            for (int i = 1; i < Math.min(near.size(), 3); i++) others.append(i > 1 ? ", " : "").append('"').append(near.get(i)[1]).append('"');
            lines.add("NOT FOUND: no control on the page carries any label the script looks for.");
            lines.add("CLOSEST live control to \"" + best[2] + "\": \"" + best[1] + "\" (similarity " + best[0] + ")"
                    + (others.length() > 0 ? "; other similar controls: " + others : "") + ".");
            lines.add("The caveat: a similar label is evidence of a rename, not proof that it is the same function.");
            return new Finding(Verdict.SCRIPT_STALE_LIKELY,
                    "SCRIPT LIKELY OUT OF DATE - the page has no \"" + best[2] + "\" control, but it does have a similarly named \"" + best[1]
                            + "\", so the feature probably still exists under a new label and the script is looking for the old one.",
                    lines,
                    "Confirm that \"" + best[1] + "\" is the same function as \"" + best[2] + "\" (for example by its click handler), then update "
                            + methodNameHint(methodSource) + " to look for it. Not changed automatically: a person must confirm it is the same control.");
        }

        lines.add("NOT FOUND: no control on the page carries any label the script looks for, and nothing similar is there either.");
        lines.add("The page itself did render (" + domLabels.size() + " controls), so this is not a blank or error page.");
        return new Finding(Verdict.APPLICATION_OR_STATE_LIKELY,
                "APPLICATION OR STATE LIKELY - the control the script needs is not on the page at all, and nothing resembling it is. It was "
                        + "removed by the application, or is hidden by the user's permission or the selected record's state; a script edit does not explain it.",
                lines,
                "Open the screen manually with the same user and record: if the control is genuinely gone, report it to development; if it only "
                        + "appears for another record or role, fix the test data or the account the test uses.");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------------

    private static double similarity(String want, String have) {
        if (want.equals(have)) return 1.0;
        Set<String> a = tokens(want), b = tokens(have);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        if (inter.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        double jaccard = (double) inter.size() / union.size();
        boolean contained = want.contains(have) || have.contains(want);
        return contained ? Math.max(jaccard, 0.5) : jaccard;
    }

    private static final Set<String> STOP = Set.of("the", "a", "an", "of", "to", "and", "for", "in", "on");

    private static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : s.split("[^a-z0-9]+")) if (t.length() >= 2 && !STOP.contains(t)) out.add(t);
        return out;
    }

    private static String norm(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT); }

    private static void addAll(Set<String> into, String pipeList) { for (String s : split(pipeList)) into.add(s); }

    private static List<String> split(String pipeList) {
        List<String> out = new ArrayList<>();
        if (pipeList == null) return out;
        for (String s : pipeList.split("\\|")) { String t = s.trim(); if (!t.isEmpty()) out.add(t); }
        return out;
    }

    private static List<String> quote(Collection<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add("\"" + s + "\"");
        return out;
    }

    private static String join(List<String> items, String prefix) { return items.isEmpty() ? "" : prefix + String.join(", ", items); }

    private static String methodNameHint(String methodSource) {
        Matcher m = Pattern.compile("(?:public|private|protected)?\\s*[A-Za-z<>\\[\\]]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(methodSource);
        return m.find() ? m.group(1) + "()" : "the Page Object method";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
