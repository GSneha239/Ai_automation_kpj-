package com.kpj.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Plain-language REASONS a page object could not do what the step asked — typically a dropdown with no usable
 * value: the list never loaded, it is empty, the wanted option does not exist on this environment (so the first
 * option was taken), or no department/sub-department offers a doctor.
 *
 * <p>Page objects used to print these to the console only, so the HTML report showed a bare PASS/FAIL with no
 * explanation and every reader had to re-derive the cause from the logs. Page objects now also call
 * {@link #add(String)}; {@link DevHisBase#step} drains whatever was collected since the previous step and
 * appends it to that step's <em>Actual</em> text as {@code — CAUSE: …}, so the reason lands next to the step
 * it explains — on PASS steps (fallback data was saved) as well as FAIL steps.</p>
 *
 * <p>Static on purpose: page objects live in {@code src/test} and only hold a {@code Page}, not the test base,
 * and every test runs single-threaded.</p>
 */
public final class Reasons {
    private static final LinkedHashSet<String> PENDING = new LinkedHashSet<>();

    private Reasons() {}

    /** Record one reason (duplicates within the same step are collapsed). Also echoed to the console. */
    public static synchronized void add(String reason) {
        if (reason == null || reason.trim().isEmpty()) return;
        String r = reason.trim();
        if (PENDING.add(r)) System.out.println("REASON: " + r);
    }

    /** Take (and clear) everything collected since the last drain. */
    public static synchronized List<String> drain() {
        List<String> out = new ArrayList<>(PENDING);
        PENDING.clear();
        return out;
    }

    /** Drop anything pending (used when a test starts, so a prior test's leftovers never leak in). */
    public static synchronized void clear() { PENDING.clear(); }

    /**
     * {@code actual} with the pending reasons appended as {@code — CAUSE: r1; r2}. Reasons already present in the
     * text (some steps build their own CAUSE line) are not repeated.
     */
    public static synchronized String appendTo(String actual) {
        List<String> rs = drain();
        if (rs.isEmpty()) return actual;
        String base = actual == null ? "" : actual;
        StringBuilder sb = new StringBuilder();
        for (String r : rs) {
            if (base.contains(r)) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(r);
        }
        if (sb.length() == 0) return base;
        return base.isEmpty() ? "CAUSE: " + sb : base + " — CAUSE: " + sb;
    }
}
