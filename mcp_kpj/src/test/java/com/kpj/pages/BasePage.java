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

    /** JS that finds any OPEN date-picker calendar popup (a visible "Month YYYY" header and its popup container). */
    private static final String PICKER_ROOTS_JS =
            "const mon=/^(January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}$/i;"
          + "const pickerRoots=()=>{ const out=[];"
          + "  for(const h of document.querySelectorAll('th,span,div,button,a,td,strong,b')){"
          + "    if(!(h.offsetWidth||h.offsetHeight)) continue;"
          + "    const t=(h.textContent||'').replace(/[^A-Za-z0-9 ]/g,' ').replace(/\\s+/g,' ').trim();"
          + "    if(t.length>25 || !mon.test(t)) continue;"
          + "    let r=h; for(let i=0;i<8&&r.parentElement;i++){ const cs=getComputedStyle(r);"
          + "      if(cs.position==='absolute'||cs.position==='fixed'||/datepicker|dropdown-menu|popover/i.test(String(r.className||''))) break;"
          + "      r=r.parentElement; }"
          + "    if(!out.includes(r)) out.push(r); }"
          + "  return out; };";

    /**
     * Close a date-picker calendar that is still open after a date was entered.
     *
     * <p>Setting the date through the box's model, or typing into it, leaves the calendar popup showing over the form (it
     * only closes on a click outside it), where it hides the controls beneath and ends up in screenshots. The box is
     * blurred and a harmless spot is clicked — the dialog's own title when a dialog is open, never Escape (that closes the
     * dialog) — and if the calendar is still showing it is hidden outright.</p>
     *
     * @return {@code true} when no calendar is showing afterwards
     */
    protected boolean dismissDatePicker() {
        try {
            Object was = page.evaluate("() => {" + PICKER_ROOTS_JS + " return pickerRoots().length; }");
            int open = was instanceof Number ? ((Number) was).intValue() : 0;
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
            Object modal = page.evaluate("() => { const m=[...document.querySelectorAll('.modal')].filter(e=>e.offsetWidth||e.offsetHeight).pop();"
                    + " if(!m) return false; const t=m.querySelector('.modal-title,.modal-header,h3,h4'); if(t) { t.click(); return true; } return true; }");
            if (!Boolean.TRUE.equals(modal)) { try { page.mouse().click(2, 2); } catch (Exception ignore) { } }
            page.waitForTimeout(300);
            Object still = page.evaluate("() => {" + PICKER_ROOTS_JS + " return pickerRoots().length; }");
            int left = still instanceof Number ? ((Number) still).intValue() : 0;
            if (left > 0) {
                page.evaluate("() => {" + PICKER_ROOTS_JS + " pickerRoots().forEach(r=>{ r.style.display='none'; }); }");
                page.waitForTimeout(200);
            }
            System.out.println("dismissDatePicker: " + (open == 0 ? "no calendar was open" : "calendar was open"
                    + (left > 0 ? ", a click did not close it so it was hidden" : ", closed by a click away")));
            return true;
        } catch (Exception e) {
            System.out.println("dismissDatePicker: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return false;
        }
    }

    /**
     * Make sure a sidebar menu entry is <b>expanded</b>, clicking it only when it is not already open.
     *
     * <p>A sidebar entry toggles: clicking one that is already open CLOSES it. A flow that walks the menu a second
     * time in the same session (e.g. a setup step opened House Keeping Schedule, then the page under test walks
     * Ancillary Services &gt; House Keeping &gt; ... again) used to collapse the menu it needed and end on the
     * dashboard. The entry's children are also filled in after the click and the click is sometimes swallowed, so
     * this waits for visible children and clicks again (up to 3 times) if none appear.</p>
     *
     * @param parentRegex text regex of the entry to search INSIDE (its {@code li}), or {@code null} to search the whole
     *                    sidebar — scoping stops a same-named top-level module (e.g. "Billing") being matched instead
     * @param entryRegex  text regex of the entry to expand (matched against the whole, trimmed link text)
     * @return true when the entry is open with visible children
     */
    protected boolean expandMenuEntry(String parentRegex, String entryRegex) {
        final String find = "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const scopeA=pr?[...document.querySelectorAll('a')].find(x=>new RegExp(pr,'i').test(norm(x.textContent)) && x.offsetParent!==null):null;"
                + " const scope=(scopeA&&scopeA.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a')].find(x=>new RegExp(er,'i').test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const kids=a?(a.closest('li')?[...a.closest('li').querySelectorAll('a')].filter(x=>x!==a&&x.offsetParent!==null).length:0):-1;";
        for (int attempt = 1; attempt <= 3; attempt++) {
            Object state = page.evaluate("([pr, er]) => {" + find
                    + " if(!a) return 'missing'; if(kids>0) return 'open'; a.click(); return 'clicked'; }",
                    java.util.Arrays.asList(parentRegex, entryRegex));
            if ("missing".equals(String.valueOf(state))) {
                // The sidebar is built lazily after login (and each level's children after its click), so "missing" right
                // now may only mean "not built yet" — give it a few seconds to appear before concluding it is absent.
                try {
                    page.waitForFunction("([pr, er]) => {" + find + " return !!a; }",
                            java.util.Arrays.asList(parentRegex, entryRegex), new Page.WaitForFunctionOptions().setTimeout(12000));
                    continue;
                } catch (Exception notThere) {
                    return false;
                }
            }
            if ("open".equals(String.valueOf(state))) return true;
            waitForAngular(1000);
            try {
                page.waitForFunction("([pr, er]) => {" + find + " return kids>0; }",
                        java.util.Arrays.asList(parentRegex, entryRegex), new Page.WaitForFunctionOptions().setTimeout(7000));
                return true;
            } catch (Exception ignore) {
                System.out.println("expandMenuEntry: '" + entryRegex + "' still has no visible children after click " + attempt
                        + (attempt < 3 ? " - clicking again" : ""));
            }
        }
        return false;
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
