package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PdfReport;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory &gt; Transfer &gt; <b>Auto Indent</b> — Page Object ({@code #/AutoIndent}, header "Auto Indent", reached from the menu entry of the same name).
 *
 * <p>Confirmed live in the built-in browser (devhis build v2026.11.02.367). One screen ({@code AutoIndent.*}):</p>
 * <ul>
 *   <li><b>Requesting Store*</b> ({@code AutoIndent.FromStoreID}) and <b>Issuing Store*</b> ({@code AutoIndent.ToStoreID}) — select2 lists, both empty by default; also Date, Floor, an Indent Type
 *       radio (All / Issue / Transfer) and Special Order / Psychotropic Drug filters;</li>
 *   <li><b>Search</b> ({@code getStockAutoIndentSearchList()}, POST {@code /api/AutoIndent/Fetch}, {@code ExecFlag GetAutoIndentList}) sends ONLY the Requesting Store (plus the Indent Type and the
 *       two flags) — the Issuing Store plays no part in which items come back, so "no record" is a property of the Requesting Store. It answers asynchronously into <b>Auto Indent Details</b>
 *       (Item Code | Item Name | UOM | Unit Quantity | ... | Remark | Issue / Transfer), a plain HTML table with a tick box ({@code Itm.isselected}) on every row;</li>
 *   <li><b>Remarks*</b> ({@code AutoIndent.Remarks}) and <b>Save Indent</b> ({@code IUDAutoIndent()}), gated by the jQuery validationEngine, so genuine Playwright clicks / fills are used. A save says
 *       "Record Saved Successfully." (toastr) and opens {@code frmIndentPrintReport.aspx} in a new tab (a PDF).</li>
 * </ul>
 * The Issuing Store must differ from the Requesting Store (the app rejects a pair that match).
 */
public class AutoIndent extends BasePage {

    public AutoIndent(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastRequesting = "", lastIssuing = "", lastItem = "", lastRemarks = "", lastToast = "";
    public int lastRowCount = 0, lastSearchStatus = 0, pagesBeforeSave = 0;

    private static final String FROM_NG = "AutoIndent.FromStoreID", TO_NG = "AutoIndent.ToStoreID", REMARKS_NG = "AutoIndent.Remarks";
    private static final String HREF = "#/autoindent";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const detailTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>/Item Code/.test(norm([...t.querySelectorAll('th')].map(h=>h.textContent).join(' '))) && /Item Name/.test(norm(t.textContent)));";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Transfer &gt; Auto Indent — each level opened only if shut, the leaf looked up by its exact route (the Store Indent screen has an "Auto Indent" BUTTON; this is the menu entry). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*inventory\\s*$");
            expandMenuEntry("^\\s*inventory\\s*$", "^\\s*transfer\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS + " const a=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__aiLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__aiLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("AutoIndent.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__aiLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("([a, b]) => { const f=document.querySelector(\"[ng-model='\"+a+\"']\"), t=document.querySelector(\"[ng-model='\"+b+\"']\"); return !!f && !!t && f.options.length>1 && t.options.length>1; }",
                    java.util.Arrays.asList(FROM_NG, TO_NG), new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("AutoIndent.nav: the store lists are not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    /** On this screen — the route AND the screen's own header, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/autoindent/?$")) return false;
        Object t = page.evaluate("() => { const h=document.querySelector('h1'); return h? h.textContent.replace(/\\s+/g,' ').trim() : ''; }");
        return t != null && t.toString().toLowerCase().contains("auto indent");
    }

    // ---- stores ----------------------------------------------------------------------------------------------

    public List<String> requestingStores() { return Select2Pick.names(page, FROM_NG); }

    public List<String> issuingStores() { return Select2Pick.names(page, TO_NG); }

    /** Choose the Requesting Store from its list; true when the box and the screen's model both hold it. */
    public boolean selectRequesting(String name) {
        problems.clear();
        String p = Select2Pick.pick(page, FROM_NG, name);
        if (!p.isEmpty()) problems.add("Requesting Store: " + p);
        else lastRequesting = name;
        return ok();
    }

    /** Choose the Issuing Store from its list; true when the box and the screen's model both hold it. */
    public boolean selectIssuing(String name) {
        problems.clear();
        String p = Select2Pick.pick(page, TO_NG, name);
        if (!p.isEmpty()) problems.add("Issuing Store: " + p);
        else lastIssuing = name;
        return ok();
    }

    public String chosenIssuing() { return Select2Pick.chosen(page, TO_NG); }

    // ---- search + Auto Indent Details ------------------------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer ({@code /api/AutoIndent/Fetch}) and count the items listed in Auto Indent Details (also {@link #lastRowCount}). */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/getStockAutoIndentSearchList/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__aiSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().contains("/api/AutoIndent/Fetch"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__aiSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search did not get an answer from /api/AutoIndent/Fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__aiSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(700);
        lastRowCount = itemRowCount();
        for (int i = 0; answered && i < 8 && lastRowCount == 0; i++) {            // the answer is in; let the table render it before calling it empty
            page.waitForTimeout(300);
            lastRowCount = itemRowCount();
        }
        lastToast = toastsNow();
        return lastRowCount;
    }

    /** Items listed in Auto Indent Details — one tick box per row. */
    public int itemRowCount() {
        Object n = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(vis).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The column headers of Auto Indent Details. */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const t=detailTable(); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** "Item Code | Item Name | UOM" of the first {@code n} listed items. */
    public String sample(int n) {
        Object r = page.evaluate("(n) => {" + JS + " const cbs=[...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(vis).slice(0,n);"
                + " return cbs.map(cb=>{ const tds=[...cb.closest('tr').querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean); return tds.slice(0,2).join(' | '); }).join(' ;; '); }", n);   // item code | item name (the UOM cell is a drop-down listing every UOM)
        return r == null ? "" : r.toString();
    }

    public String toastsNow() {
        // a toast and its inner .toast-message carry the same text: each message is listed once
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean))].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- select the record, remarks, save --------------------------------------------------------------------------

    /** Tick the first listed item (the atomic click + Angular-sync pattern proven on this module's tick boxes) and read back which item it was. */
    public String tickFirstItem() {
        problems.clear();
        Object r = page.evaluate("() => {" + JS
                + " const cb=[...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(vis)[0]; if(!cb) return '';"
                + " cb.scrollIntoView({block:'center'}); cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel'); if(c){ c.$setViewValue(true); c.$render(); } }catch(e){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(!cb.checked) return '';"
                + " const tds=[...cb.closest('tr').querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean); return tds.slice(0,2).join(' | '); }");
        lastItem = r == null ? "" : r.toString();
        waitForAngular(500);
        if (lastItem.isEmpty()) problems.add("the first item's tick box did not register");
        return lastItem;
    }

    /** How many items are ticked now. */
    public int tickedCount() {
        Object n = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(c=>c.checked).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String enterRemarks(String value) {
        problems.clear();
        try {
            Locator box = page.locator("textarea[ng-model=\"" + REMARKS_NG + "\"]");
            box.scrollIntoViewIfNeeded();
            box.fill(value, new Locator.FillOptions().setTimeout(6000));
            lastRemarks = box.inputValue();
        } catch (Exception e) {
            lastRemarks = "";
            problems.add("Remarks could not be filled: " + String.valueOf(e.getMessage()).split("\n")[0]);
        }
        waitForAngular(300);
        if (lastRemarks.isEmpty() && problems.isEmpty()) problems.add("Remarks stayed empty");
        return lastRemarks;
    }

    /** Click <b>Save Indent</b> (a genuine click — the screen's validation ignores script clicks) and accept a confirm dialog if one appears. */
    public boolean clickSaveIndent() {
        problems.clear();
        pagesBeforeSave = page.context().pages().size();
        try {
            page.locator("button:has-text('Save Indent')").first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            problems.add("the Save Indent button could not be clicked: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return false;
        }
        waitForAngular(300);
        try { acceptSaveDialog(); } catch (Exception ignore) { }
        return true;
    }

    /** Wait for the answer to Save Indent: a success or failure toast ("" when none appeared). */
    public String waitForSaveToast() {
        String seen = "";
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            String t = toastsNow();
            if (!t.isEmpty()) {
                seen = t;
                String low = t.toLowerCase();
                if (low.contains("success") || low.contains("fail") || low.contains("error") || low.contains("please")) break;
            }
            page.waitForTimeout(250);
        }
        lastToast = seen;
        return seen;
    }

    public static boolean isSuccess(String toast) { return toast != null && toast.toLowerCase().contains("success"); }

    /** Judge the report Save Indent opens (a PDF in a new tab) and close that tab. */
    public PdfReport.Result captureReport() {
        PdfReport.Result r = PdfReport.capture(page, pagesBeforeSave, 20000);
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        return r;
    }
}
