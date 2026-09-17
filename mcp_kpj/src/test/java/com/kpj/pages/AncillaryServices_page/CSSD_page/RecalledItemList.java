package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Recalled Item List</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>CSSD</b> (flyout) → <b>Recalled Item List</b> (real hash route,
 * {@code #/RecalledItemList}) → <b>New</b> ({@code fnAddRecalledItem()}, route {@code #/add-RecalledItem})
 * → the Recall form → enter <b>From Date</b> / <b>To Date</b> → <b>Search</b>
 * ({@code fnFetchHeaderList()}) → a <b>Transfer List</b> table ({@code HeaderList}, one row per transfer
 * issued from CSSD) → click a row ({@code fnSelectHeader(header)}) to populate the <b>Item List</b> table
 * ({@code ItemList}) below it → tick the item's checkbox ({@code fnSelectItem(item)}) → pick a
 * <b>Recall Reason</b> (required, select2) → <b>Recall</b> ({@code fnSaveRecall()}) → success toast with
 * the new Recall Number.</p>
 *
 * <p><b>Unlike CSSD &gt; Transfer, this screen has a real route</b> for both the list
 * ({@code #/RecalledItemList}) and the add-form ({@code #/add-RecalledItem}) — confirmed live that
 * {@code page.navigate(BASE + "/#/RecalledItemList")} lands directly on the list screen when already
 * authenticated, so {@link #navigateViaMenu()} still drives the sidebar (for a cold/collapsed session) but
 * arrival is checked via the URL rather than a structural probe.</p>
 *
 * <p><b>The "Select" column in both grids is the row itself, not the radio/checkbox alone.</b> Confirmed
 * live from the Angular markup: {@code tr[ng-repeat="header in HeaderList"]} carries
 * {@code ng-click="fnSelectHeader(header)"} on the whole row (the radio input itself has no
 * {@code ng-model}/{@code ng-click} — it is purely decorative), and
 * {@code tr[ng-repeat="item in ItemList"]} carries {@code ng-click="fnSelectItem(item)"} with the checkbox
 * additionally carrying its own {@code ng-click="fnSelectItem(item); $event.stopPropagation()"}. Either
 * clicking the row or the input works; these methods click the input directly (closer to "click the row in
 * the select column" / "tick checkbox").</p>
 *
 * <p><b>Recall Reason and Remarks are disabled until a Transfer List row is selected</b>
 * ({@code ng-disabled="!SelectedItem"} on {@code #ddlRecallReason}) — the same flag also gates the
 * <b>Recall</b> button itself. Recall Reason is marked required (*) on screen even though the button's
 * {@code ng-disabled} doesn't check it directly; {@link #selectRecallReason(String)} must still be called
 * before {@link #clickRecallAndGetToast()} or the save is rejected.</p>
 *
 * <h2>Data dependency, confirmed live 2026-09-14</h2>
 * <p>The Transfer List is NOT filtered to exclude transfers already recalled — a transfer that was recalled
 * in an earlier run (e.g. {@code ITS000000123} → Recall Number {@code RN-26-0012}) still appears in a
 * later Search over the same date range. {@link #selectFirstTransferRow()} always picks row 1; a repeat run
 * against the same data may therefore hit an already-recalled transfer and get a different
 * (non-"success") toast back from {@code fnSaveRecall()} — that is real server behavior, not a script
 * defect. Prefer a wide {@code From Date}/{@code To Date} range so an unrecalled transfer is more likely to
 * sort first, or pass a specific transfer-number match to {@link #selectFirstTransferRow()}'s overload.</p>
 */
public class RecalledItemList extends BasePage {

    public RecalledItemList(Page page) { super(page); }

    public String lastFromDate = "", lastToDate = "";
    public String lastTransferNumber = "", lastItemRow = "", lastRecallReason = "";
    /** Populated by {@link #clickRecallAndGetToast()} — the "RN-..." number parsed out of the success toast, "" if none. */
    public String lastRecallNumber = "";

    // ---- shared JS -----------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;";

    // ---- navigation ------------------------------------------------------

    /**
     * <b>Ancillary Services</b> → <b>CSSD</b> (flyout) → <b>Recalled Item List</b>. Expands the sidebar
     * first (same reasoning as {@link Transfer#navigateViaMenu()}: a collapsed icon-only sidebar makes the
     * nested flyout unreliable to click through programmatically), then confirms arrival via the URL since
     * this screen — unlike Transfer — has a real hash route.
     */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("CSSD.RecalledItemList.nav: nav menu never appeared");
        }

        expandSidebar();
        clickByText("ancillary\\s*services?");
        boolean cssdVisible = waitForVisibleText("^cssd$", 12);
        if (!cssdVisible) { System.out.println("CSSD.RecalledItemList.nav: 'CSSD' submenu never became visible"); return false; }
        clickByText("^cssd$");
        boolean linkVisible = waitForVisibleText("^recalled\\s*item\\s*list$", 12);
        if (!linkVisible) { System.out.println("CSSD.RecalledItemList.nav: 'Recalled Item List' link never became visible under CSSD"); return false; }
        clickByText("^recalled\\s*item\\s*list$");

        try {
            page.waitForURL("**/RecalledItemList", new Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CSSD.RecalledItemList.nav: URL never reached #/RecalledItemList (got " + page.url() + ")"); }
        waitForAngular(600);
        return onListScreen();
    }

    /** Click the hamburger to force the full labeled sidebar (not icon-only) — see {@link #navigateViaMenu()}. */
    private void expandSidebar() {
        try {
            page.locator("a.sidebar-toggle[data-toggle='offcanvas']").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("CSSD.RecalledItemList.nav: sidebar-toggle click failed - " + e.getMessage());
        }
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^home$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                    + " return !!a && a.getBoundingClientRect().width > 80; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) {
            System.out.println("CSSD.RecalledItemList.nav: sidebar did not visibly expand — proceeding anyway");
        }
        waitForAngular(400);
    }

    /** Click the visible {@code <a>} whose normalized text matches {@code regex} (filtered to genuinely visible, since this sidebar can render duplicate matches at once). */
    private void clickByText(String regex) {
        Object tagged = page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a')].find(x=>rx.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!a) return false; a.id='__ril_navTarget'; return true; }", regex);
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.RecalledItemList.nav: no visible <a> matches /" + regex + "/"); return; }
        try { page.locator("#__ril_navTarget").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("CSSD.RecalledItemList.nav: click on /" + regex + "/ failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ril_navTarget'); if(e) e.removeAttribute('id'); }");
    }

    /** Poll (up to {@code attempts} x 400ms) for a visible {@code <a>} matching {@code regex}. */
    private boolean waitForVisibleText(String regex, int attempts) {
        for (int i = 0; i < attempts; i++) {
            Object r = page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const rx=new RegExp(re,'i');"
                    + " return [...document.querySelectorAll('a')].some(a=>rx.test(norm(a.textContent)) && a.offsetParent!==null); }", regex);
            if (Boolean.TRUE.equals(r)) return true;
            page.waitForTimeout(400);
        }
        return false;
    }

    /** True on the Recalled Item List screen (checked via URL — this screen has a real route, unlike CSSD &gt; Transfer). */
    public boolean onListScreen() {
        String url = page.url();
        return url.contains("/RecalledItemList") && !url.contains("add-RecalledItem");
    }

    /** True on the add-Recall form (checked via URL / the From Date field's presence). */
    public boolean onRecallForm() {
        if (page.url().contains("add-RecalledItem")) return true;
        Object r = page.evaluate("() => !![...document.querySelectorAll('[ng-model=\"RecalledItem.FromDate\"]')].find(e=>e.offsetParent!==null)");
        return Boolean.TRUE.equals(r);
    }

    // ---- New -> Recall form ------------------------------------------------

    /** Click <b>New</b> ({@code fnAddRecalledItem()}) and wait for the Recall form. */
    public boolean clickNew() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>norm(x.textContent)==='New' && x.offsetParent!==null); if(!a) return false; a.id='__ril_new'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.RecalledItemList.clickNew: New link not found"); return false; }
        try { page.locator("#__ril_new").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.RecalledItemList.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ril_new'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForURL("**/add-RecalledItem", new Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CSSD.RecalledItemList.clickNew: URL never reached #/add-RecalledItem"); }
        waitForAngular(600);
        return onRecallForm();
    }

    // ---- date range + Search -----------------------------------------------

    /** Set <b>From Date</b> ({@code RecalledItem.FromDate}) by typing directly into the Angular-bound field (bypasses the date-picker popup). */
    public String setFromDate(String ddMMyyyy) {
        Object r = setNgField("RecalledItem.FromDate", ddMMyyyy);
        lastFromDate = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("CSSD.RecalledItemList: From Date = " + lastFromDate);
        return lastFromDate;
    }

    /** Set <b>To Date</b> ({@code RecalledItem.ToDate}). */
    public String setToDate(String ddMMyyyy) {
        Object r = setNgField("RecalledItem.ToDate", ddMMyyyy);
        lastToDate = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("CSSD.RecalledItemList: To Date = " + lastToDate);
        return lastToDate;
    }

    private Object setNgField(String ngModel, String value) {
        return page.evaluate("([ng,v]) => {" + JS
                + " const e=[...document.querySelectorAll('[ng-model=\"'+ng+'\"]')].find(vis); if(!e) return '(no-field)';"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }",
                java.util.Arrays.asList(ngModel, value));
    }

    /** Click <b>Search</b> ({@code fnFetchHeaderList()}) and return the Transfer List's row count after settling. */
    public int clickSearch() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__ril_search'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.RecalledItemList.clickSearch: Search button not found"); return 0; }
        try { page.locator("#__ril_search").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.RecalledItemList.clickSearch: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ril_search'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        List<String> rows = transferListRows();
        System.out.println("CSSD.RecalledItemList: Search -> " + rows.size() + " transfer(s): " + rows);
        return rows.size();
    }

    // ---- Transfer List (HeaderList) ----------------------------------------

    /** The <b>Transfer List</b> grid's rows (one per transfer issued from CSSD in the searched date range). */
    public List<String> transferListRows() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('tr[ng-repeat=\"header in HeaderList\"]')].map(row=>norm(row.textContent)).filter(Boolean); }");
        return toList(r);
    }

    /**
     * Click the <b>first</b> Transfer List row (the "Select" column — {@code fnSelectHeader(header)} fires
     * on the row itself), which populates the Item List grid. See the class javadoc's data-dependency note:
     * this always picks row 1, which may already have been recalled by a prior run.
     *
     * @return the clicked row's text, or {@code ""} if the Transfer List is empty
     */
    public String selectFirstTransferRow() {
        Object r = page.evaluate("() => {" + JS
                + " const row=document.querySelector('tr[ng-repeat=\"header in HeaderList\"]'); if(!row) return '';"
                + " const t=norm(row.textContent); row.click(); return t; }");
        String rowText = r == null ? "" : r.toString();
        lastTransferNumber = rowText;
        try {
            page.waitForFunction("() => document.querySelectorAll('tr[ng-repeat=\"item in ItemList\"]').length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { /* genuinely no items on this transfer, or slower than 8s — caller checks the row count either way */ }
        waitForAngular(400);
        System.out.println("CSSD.RecalledItemList: selected Transfer List row -> " + (rowText.isEmpty() ? "(none available)" : rowText));
        return rowText;
    }

    // ---- Item List ----------------------------------------------------------

    /** The <b>Item List</b> grid's rows (the item(s) on the selected transfer). */
    public List<String> itemListRows() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('tr[ng-repeat=\"item in ItemList\"]')].map(row=>norm(row.textContent)).filter(Boolean); }");
        return toList(r);
    }

    /**
     * Tick the checkbox on the Item List grid's first row ({@code item.selected}, fires
     * {@code fnSelectItem(item)}), which enables Recall Reason / Remarks / the Recall button.
     *
     * @return the row's text, or {@code ""} if the Item List is empty
     */
    public String tickFirstItemRow() {
        Object r = page.evaluate("() => {" + JS
                + " const row=document.querySelector('tr[ng-repeat=\"item in ItemList\"]'); if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); if(!cb) return '';"
                + " const t=norm(row.textContent); cb.click(); return t; }");
        String rowText = r == null ? "" : r.toString();
        lastItemRow = rowText;
        try {
            page.waitForFunction("() => { const e=document.getElementById('ddlRecallReason'); return !!(e && !e.disabled); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { System.out.println("CSSD.RecalledItemList: Recall Reason did not enable within 8s after ticking the item"); }
        waitForAngular(400);
        System.out.println("CSSD.RecalledItemList: ticked Item List row -> " + (rowText.isEmpty() ? "(none available)" : rowText));
        return rowText;
    }

    // ---- Recall Reason + Recall ---------------------------------------------

    /**
     * Select the required <b>Recall Reason</b> ({@code #ddlRecallReason}, select2 bound to
     * {@code RecalledItem.recallreasonid}) by visible option text (e.g. "Other", "Incorrect Set/Item",
     * "Item Contamination").
     */
    public String selectRecallReason(String optionText) {
        Object r = page.evaluate("(txt) => {" + JS + " const $=window.jQuery;"
                + " const e=document.getElementById('ddlRecallReason'); if(!e) return '(no-field)';"
                + " const o=[...e.options].find(x=>norm(x.textContent).toLowerCase()===String(txt).toLowerCase()); if(!o) return '(no-option)';"
                + " e.value=o.value; const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(o.value); c.$render(); }"
                + " if($){ try{ $(e).trigger('change'); $(e).select2('val', o.value); }catch(err){} }"
                + " e.dispatchEvent(new Event('change',{bubbles:true})); return norm(o.textContent); }", optionText);
        lastRecallReason = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("CSSD.RecalledItemList: Recall Reason = " + lastRecallReason);
        return lastRecallReason;
    }

    /**
     * Click <b>Recall</b> ({@code fnSaveRecall()}) and return the toast text ("" if none). Parses the new
     * Recall Number (e.g. "RN-26-0012") out of the toast into {@link #lastRecallNumber} when present.
     *
     * <p>The toast here is a standard toastr widget ({@code #toast-container .toast .toast-message}) —
     * confirmed live it does NOT auto-dismiss quickly on this screen (still present in the DOM minutes
     * later), unlike the "~1s fade" documented for other DevHIS screens, but this still captures it
     * immediately after the click rather than relying on that.</p>
     */
    public String clickRecallAndGetToast() {
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>el.remove()); }");

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button')].find(x=>/^recall$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__ril_recall'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.RecalledItemList.recall: Recall button not found"); return ""; }
        try { page.locator("#__ril_recall").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.RecalledItemList.recall: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ril_recall'); if(e) e.removeAttribute('id'); }");

        String toast = "";
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('.toast-message')].find(e=>(e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            Object r = page.evaluate("() => { const e=[...document.querySelectorAll('.toast-message')].find(x=>(x.textContent||'').trim()); return e?e.textContent.trim():''; }");
            toast = r == null ? "" : r.toString();
        } catch (Exception ignore) {
            System.out.println("CSSD.RecalledItemList.recall: no toast appeared within 15s");
        }
        waitForAngular(500);

        lastRecallNumber = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(RN-\\d{2,4}-\\d+)").matcher(toast);
        if (m.find()) lastRecallNumber = m.group(1);

        System.out.println("CSSD.RecalledItemList: Recall toast = \"" + toast + "\" | Recall Number = " + lastRecallNumber);
        return toast;
    }

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
