package com.kpj.heal;

/**
 * One FAILED step, assembled from the framework's existing TSV report ({@code test-output/aggregate-data})
 * plus (when available) the failure-detail sidecar {@code DevHisBase} writes for FAIL steps
 * ({@code test-output/failures}) — URL and a live-DOM summary captured at the moment of failure.
 */
public class FailureRecord {
    public final String testId;
    public final String page;
    public final String step;
    public final String expected;
    public final String actual;

    /** Populated from the failure-detail sidecar, when one exists for this step. Empty otherwise. */
    public String url = "";
    public String buttons = "";
    public String selects = "";
    public String headings = "";

    /** The SAME three, captured a few seconds later (DevHisBase.FAILURE_SETTLE_DELAY_MS) — lets a timing
     *  race (field empty at failure, populated moments later) be told apart from a genuine locator change. */
    public String settledButtons = "";
    public String settledSelects = "";
    public String settledHeadings = "";

    /** V2: visible clickable elements' ng-click handler values (T0) — enables ng-click locator healing. */
    public String ngClicks = "";
    public String settledNgClicks = "";

    /** V2: each captured select's FULL live option-text list (ngModel -> opt1~opt2~...), not just the
     *  currently-selected one — enables dropdown option/text healing. */
    public String selectOptions = "";
    public String settledSelectOptions = "";

    public FailureRecord(String testId, String page, String step, String expected, String actual) {
        this.testId = testId;
        this.page = page;
        this.step = step;
        this.expected = expected;
        this.actual = actual;
    }

    public boolean hasDomCapture() {
        return !buttons.isEmpty() || !selects.isEmpty() || !headings.isEmpty();
    }

    @Override
    public String toString() {
        return "[" + testId + "] " + step + " -- expected: " + expected + " | actual: " + actual
                + (url.isEmpty() ? "" : " | url: " + url);
    }
}
