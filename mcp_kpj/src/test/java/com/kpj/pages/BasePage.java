package com.kpj.pages;

import com.microsoft.playwright.Page;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * BasePage — foundation for all Page Object Model classes.
 * Holds the Playwright Page instance and provides common shared helpers
 * (JS evaluators, random data generators, waits, screenshots).
 *
 * All concrete Page classes extend this.
 */
public abstract class BasePage {

    protected final Page page;
    protected final Random random = new Random();

    protected BasePage(Page page) {
        this.page = page;
    }

    // ---- waits -----------------------------------------------------------

    protected void waitForAngular(int millis) {
        page.waitForTimeout(millis);
    }

    /**
     * Click a harmless, non-interactive spot on the page (top-left corner) to un-stick a rendering glitch.
     *
     * <p>Confirmed manually: this AngularJS + select2 UI occasionally renders into a visually "stuck" state
     * (a widget not reflecting its real value, a panel not repainting after a digest) that a single click
     * anywhere on the page clears — it doesn't fix the underlying cause, just forces a repaint/reflow. Call
     * this after an operation known to trigger the glitch (an accordion toggle, a cascading dropdown change)
     * and before reading the page's state, rather than adding longer waits that don't address a rendering
     * issue.</p>
     */
    protected void nudgePage() {
        try { page.mouse().click(2, 2); } catch (Exception ignore) { }
    }

    // ---- JS helpers shared across many pages -----------------------------

    /** Dispatch a native 'change' event on a DOM element found by selector string. */
    protected void dispatchChange(String selector) {
        page.evaluate("(function(s) { " +
                "var el = document.querySelector(s);" +
                "if (el) { el.dispatchEvent(new Event('change', {bubbles: true})); } })('" + selector + "')");
    }

    /** Click a visible button whose trimmed text matches (case-insensitive). */
    protected void clickButtonByText(String text) {
        page.evaluate("(function(t) { " +
                "var b = [].slice.call(document.querySelectorAll('button'))" +
                ".find(function(x) { return x.innerText.trim().toLowerCase() === t.toLowerCase() && x.offsetParent !== null; });" +
                "if (b) b.click(); })('" + text + "')");
    }

    /** Click a visible button whose trimmed text starts with given prefix. */
    protected void clickButtonByTextPrefix(String prefix) {
        page.evaluate("(function(p) { " +
                "var b = [].slice.call(document.querySelectorAll('button'))" +
                ".find(function(x) { return x.innerText.trim().toLowerCase().startsWith(p.toLowerCase()) && x.offsetParent !== null; });" +
                "if (b) b.click(); })('" + prefix + "')");
    }

    /** Set a &lt;select&gt; by matching its ng-model attribute and option text. */
    protected void setSelectByNgModelAndText(String ngModel, String optionText) {
        page.evaluate("([" + escapeForJs(ngModel) + ", " + escapeForJs(optionText) + "], ee) => {" +
                "var sel = [].slice.call(document.querySelectorAll('select'))" +
                ".find(function(s) { return s.getAttribute('ng-model') === ngModel; });" +
                "if (!sel) return;" +
                "var opt = [].slice.call(sel.querySelectorAll('option'))" +
                ".find(function(o) { return o.textContent.trim() === optionText; });" +
                "if (!opt) return;" +
                "sel.value = opt.value;" +
                "sel.dispatchEvent(new Event('change', {bubbles: true})); }",
                List.of(ngModel, optionText));
    }

    /** Select the first available option in a &lt;select&gt; by index (1-based). */
    protected void selectOptionByIndex(String selector, int index) {
        page.evaluate("(function(s, idx) { " +
                "var sel = document.querySelector(s);" +
                "if (sel && sel.options.length > idx) { sel.selectedIndex = idx; sel.dispatchEvent(new Event('change', {bubbles: true})); } })('" + selector + "', " + index + ")");
    }

    /** Select a &lt;select&gt; by label text. */
    protected void selectOptionByLabel(String selector, String label) {
        page.evaluate("(function(s, lbl) { " +
                "var sel = document.querySelector(s); if (!sel) return;" +
                "var opt = [].slice.call(sel.options).find(function(o) { return o.text.trim() === lbl; });" +
                "if (opt) { sel.value = opt.value; sel.dispatchEvent(new Event('change', {bubbles: true})); } })('" + selector + "', '" + label + "')");
    }

    /** Select an option by text match (contains) in any select element. */
    protected void selectOptionInAnySelectByText(String text) {
        page.evaluate("(function(t) { " +
                "var s = [].slice.call(document.querySelectorAll('select')).find(function(sel) {" +
                "  return [].slice.call(sel.options).some(function(o) { return o.text.includes(t); }); });" +
                "if (!s) return;" +
                "var o = [].slice.call(s.options).find(function(opt) { return opt.text.includes(t); });" +
                "s.value = o.value; s.dispatchEvent(new Event('change', {bubbles: true})); })('" + text + "')");
    }

    /** Accept the "Do You Want To Save" dialog by clicking the red SAVE button. */
    protected void acceptSaveDialog() {
        page.evaluate("function() { " +
                "var b = [].slice.call(document.querySelectorAll('button'))" +
                ".find(function(x) { return /^SAVE$/i.test(x.innerText.trim()) && /danger/.test(x.className) && x.offsetParent !== null; });" +
                "if (b) b.click(); }()");
    }

    // ---- random test data generators -------------------------------------

    protected String uniqueId() {
        return String.format("%04d", random.nextInt(10000));
    }

    protected String randomName() {
        String[] firsts = {"Arjun", "Priya", "Ravi", "Siti", "Wei", "Mei", "Raj", "Nina", "Kim", "Suresh"};
        String[] lasts = {"Kumar", "Singh", "Patel", "Ali", "Chen", "Lee", "Tan", "Lim", "Wong", "Ahmad"};
        return firsts[random.nextInt(firsts.length)] + " " + lasts[random.nextInt(lasts.length)] + " " + uniqueId();
    }

    protected String randomNRIC() {
        return "90051514" + String.format("%04d", random.nextInt(10000));
    }

    protected String randomEmail() {
        return "patient" + uniqueId() + "@test.com";
    }

    protected String randomMobile() {
        return String.format("1%08d", random.nextInt(100000000));
    }

    protected String randomDOB() {
        String[] days = {"10", "15", "20", "25"};
        String[] months = {"01", "05", "08", "12"};
        return days[random.nextInt(days.length)] + "/" + months[random.nextInt(months.length)] + "/1990";
    }

    // ---- screenshot ------------------------------------------------------

    protected void screenshot(String path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(path)));
    }

    protected void screenshot(Path path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(path));
    }

    // ---- accessor ---------------------------------------------------------

    public Page getPage() { return page; }

    // ---- utility ----------------------------------------------------------

    private static String escapeForJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
