package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The search / Indent List / Indent Details / Print part shared by two screens that run on the SAME controller ({@code StoreIndent.*}, told apart only by
 * {@code isCallingmode}): <b>Inventory &gt; Transfer &gt; Store Indent</b> ({@code #/StoreIndentList}) and <b>Store Indent Approval</b> ({@code #/StoreIndentList2},
 * see {@link StoreIndentApproval}). Confirmed live in the built-in browser (devhis build v2026.11.02.365):
 * <ul>
 *   <li>radios (Store Indent: All is the default; Approval: Pending For Approval), <b>Requesting Store</b> ({@code StoreIndent.SearchStoreID}, a select2 over ~100 stores, empty by default),
 *       <b>Issuing Store</b>, <b>From Date</b>/<b>To Date</b> ({@code #txtFromDate}/{@code #txtToDate}, dd/MM/yyyy; the Store Indent list pre-fills both with today), Indent Category (Store Indent
 *       only; defaults to Auto), Indent No., and <b>Search</b> ({@code OnSearchBtnClick(true)}, POST {@code /api/StoreIndent/Fetch}). The list answers ASYNCHRONOUSLY: a screenshot taken right after the
 *       click shows an empty table that fills a moment later, so this waits for the response instead of a fixed pause. A search that finds nothing is announced by a non-blocking toastr toast only;</li>
 *   <li>the <b>Indent List</b> table (Store Indent has an extra "Indent Category" column after Modify, so columns are found by header, never by position). Clicking a cell
 *       ({@code GetItemList(PO)}) highlights the row ({@code indent-row-selected}) and loads its items — again asynchronously — into <b>Indent Details</b> (Sr.No. | Item Code | Item Name | UOM | ...);</li>
 *   <li><b>Print</b> ({@code printReport()}) needs a selected indent and opens {@code /SQLReport/InventoryReport/frmIndentPrintReport.aspx?...IndentId=..} in a NEW TAB — a PDF, judged by {@link PdfReport}.</li>
 * </ul>
 */
public class StoreIndentList extends BasePage {

    public StoreIndentList(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastFrom = "", lastTo = "", lastToast = "", lastIndentNo = "", lastRowText = "", lastDetailText = "";
    public int lastRowCount = 0, lastDetailRows = 0, lastSearchStatus = 0;
    /** The tabs open before Print was clicked. */
    public int pagesBeforePrint = 0;

    /** The columns every listed indent must carry. */
    private static final String[] REQUIRED = {"Indent Date-Time", "Indent No.", "Indent Status", "Location", "Indent Type", "Requesting Store"};

    protected static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const hasHeads=(t,res)=>{ const h=norm([...t.querySelectorAll('th')].map(x=>x.textContent).join(' ')); return res.every(r=>new RegExp(r,'i').test(h)); };"
          + "const listTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>hasHeads(t,['Indent No','Indent Status','Requesting Store']));"
          + "const detailTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>hasHeads(t,['Item Code','Item Name']));";

    protected static final String STORE_NG = "StoreIndent.SearchStoreID", FROM_NG = "StoreIndent.FromDate", TO_NG = "StoreIndent.ToDate";

    // ---- small helpers ---------------------------------------------------------------------------------------

    protected boolean clickByNg(String re, String what) {
        Object tagged = page.evaluate("(re) => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>new RegExp(re).test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__siaBtn'; return true; }", re);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__siaBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__siaBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    protected String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** The toasts showing right now, each message once, joined with " | " (the portal title stripped). */
    public String toastsNow() {
        // a toast and its inner .toast-message carry the same text: each message is listed once
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean))].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    public int tabCount() { return page.context().pages().size(); }

    protected void closeExtraTabs() {
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
    }

    // ---- Requesting Store ------------------------------------------------------------------------------------

    /** Every store the Requesting Store list offers, in list order ("-Select-" left out). */
    public List<String> storeNames() {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)); }", STORE_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** What the Requesting Store box shows now. */
    public String chosenStore() {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " const c=e.previousElementSibling; const ch=c && c.querySelector('.select2-chosen'); return ch? norm(ch.textContent) : ''; }", STORE_NG);
        return r == null ? "" : r.toString();
    }

    /**
     * Choose a store the way a user does: open the Requesting Store dropdown and click the store in its list. Returns true when the box shows the store AND the screen's model holds it.
     */
    public boolean selectStore(String name) {
        problems.clear();
        Object tagged = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return false;"
                + " const c=e.previousElementSibling; const b=c && (c.querySelector('.select2-choice')||c); if(!b) return false; b.id='__siaStoreBox'; return true; }", STORE_NG);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Requesting Store box is not on screen"); return false; }
        try {
            page.locator("#__siaStoreBox").click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForFunction("() => [...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].length>1", null, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Requesting Store list did not open"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__siaStoreBox'); if(e) e.removeAttribute('id'); }"); }
        Object found = page.evaluate("(w) => {" + JS + " const li=[...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].find(x=>norm(x.textContent)===w);"
                + " if(!li) return false; li.id='__siaStoreOpt'; return true; }", name);
        if (!Boolean.TRUE.equals(found)) {
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            problems.add("the store \"" + name + "\" is not in the dropdown list");
            return false;
        }
        try { page.locator("#__siaStoreOpt").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the store \"" + name + "\" could not be clicked in the list"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__siaStoreOpt'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 20 && !name.equals(chosenStore()); i++) page.waitForTimeout(150);
        waitForAngular(300);
        lastStore = chosenStore();
        String held = model(STORE_NG);
        if (!name.equals(lastStore)) problems.add("the box shows \"" + lastStore + "\" instead of \"" + name + "\"");
        else if (held.isEmpty() || held.equals("null")) problems.add("the store is not held by the screen's model");
        return ok();
    }

    // ---- dates -----------------------------------------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> (dd/MM/yyyy) — typed first, set through the box's own model when the keystrokes are discarded. */
    public String enterDateRange(String from, String to) {
        problems.clear();
        String a = typeDate("txtFromDate", FROM_NG, from);
        String b = typeDate("txtToDate", TO_NG, to);
        lastFrom = model(FROM_NG);
        lastTo = model(TO_NG);
        if (!from.equals(lastFrom)) problems.add("From Date holds \"" + lastFrom + "\" instead of " + from);
        if (!to.equals(lastTo)) problems.add("To Date holds \"" + lastTo + "\" instead of " + to);
        String r = "From Date " + a + " | To Date " + b;
        System.out.println("StoreIndentList: " + r);
        return r;
    }

    private String typeDate(String id, String ng, String value) {
        try {
            Locator box = page.locator("#" + id);
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.press("Control+a");
            box.press("Delete");
            box.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(90));
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        } catch (Exception e) { return "(typing failed: " + String.valueOf(e.getMessage()).split("\n")[0] + ")"; }
        waitForAngular(400);
        String via = "typed";
        if (!value.equals(model(ng))) {
            // The box is a picker box (class txtDate, inputmode none): when its typed keystrokes are discarded, set the value through the box's own model.
            page.evaluate("(a) => { const e=document.getElementById(a.id); if(!e) return; e.value=a.v;"
                    + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }",
                    Map.of("id", id, "v", value));
            waitForAngular(400);
            via = "set through the model";
        }
        dismissDatePicker();
        return value + " (" + via + ")";
    }

    // ---- search + the Indent List ----------------------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer ({@code /api/StoreIndent/Fetch}) and count the indents listed (also {@link #lastRowCount}). */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/OnSearchBtnClick/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__siaSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().contains("/api/StoreIndent/Fetch"),
                    new Page.WaitForResponseOptions().setTimeout(25000), () -> page.locator("#__siaSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search did not get an answer from /api/StoreIndent/Fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__siaSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(700);
        lastRowCount = indentRows().size();
        for (int i = 0; answered && i < 6 && lastRowCount == 0; i++) {          // the answer is in; let the table render it before calling it empty
            page.waitForTimeout(300);
            lastRowCount = indentRows().size();
        }
        lastToast = toastsNow();
        return lastRowCount;
    }

    /** The indents listed in the Indent List: each row as its cell texts (checkboxes shown as "[x]" / "[ ]"). */
    public List<List<String>> indentRows() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent)).map(r=>[...r.querySelectorAll('td')].map(td=>{ const c=td.querySelector('input[type=checkbox]');"
                + "   if(c) return c.checked? '[x]' : '[ ]'; const b=td.querySelector('button'); return b? '(' + norm(b.textContent) + ')' : norm(td.textContent); })); }");
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<String> cells = new ArrayList<>(); for (Object c : (List<?>) row) cells.add(String.valueOf(c)); out.add(cells); }
        return out;
    }

    /** The Indent List's column headers. */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** How many of the rows lack a date-time, indent no., status, location, type or requesting store — columns found by header, since Store Indent has one more column than Store Indent Approval. */
    public int incompleteRows(List<List<String>> rows) {
        List<String> heads = headers();
        int bad = 0;
        for (List<String> r : rows) {
            boolean complete = !heads.isEmpty();
            for (String want : REQUIRED) {
                int i = heads.indexOf(want);
                if (i < 0 || i >= r.size() || r.get(i).isEmpty()) complete = false;
            }
            if (!complete) bad++;
        }
        return bad;
    }

    /** The required columns of the first {@code n} rows, as "date-time | indent no. | status | location | type | requesting store". */
    public String sample(List<List<String>> rows, int n) {
        List<String> heads = headers();
        List<String> out = new ArrayList<>();
        for (int k = 0; k < Math.min(n, rows.size()); k++) {
            List<String> parts = new ArrayList<>();
            for (String want : REQUIRED) { int i = heads.indexOf(want); parts.add(i >= 0 && i < rows.get(k).size() ? rows.get(k).get(i) : "?"); }
            out.add(String.join(" | ", parts));
        }
        return out.toString();
    }

    // ---- selecting a record + Indent Details ----------------------------------------------------------------------------

    /**
     * Click the first indent's <b>Indent No.</b> cell (found by its column header; never the Modify button or a checkbox) and wait for its items to load into <b>Indent Details</b>.
     * Returns the Indent No. ("" with a problem when it could not be selected).
     */
    public String selectFirstRecord() {
        problems.clear();
        Object t = page.evaluate("() => {" + JS + " const t=listTable(); const r=t&&[...t.querySelectorAll('tbody tr')].find(x=>norm(x.textContent)); if(!r) return '';"
                + " const heads=[...t.querySelectorAll('th')].map(h=>norm(h.textContent)); const col=n=>heads.indexOf(n);"
                + " const tds=[...r.querySelectorAll('td')]; const c=tds[col('Indent No.')]; if(!c || !norm(c.textContent)) return '';"
                + " c.scrollIntoView({block:'center'}); c.id='__siaRow';"
                + " return norm(c.textContent) + '||' + ['Indent Category','Indent Date-Time','Indent No.','Indent Status','Location','Indent Type','Requesting Store']"
                + "   .filter(n=>col(n)>=0).map(n=>norm((tds[col(n)]||{}).textContent)).join(' | '); }");
        String s = t == null ? "" : t.toString();
        if (s.isEmpty()) { problems.add("there is no indent row to click"); return ""; }
        lastIndentNo = s.substring(0, s.indexOf("||"));
        lastRowText = s.substring(s.indexOf("||") + 2);
        try { page.locator("#__siaRow").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the row could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__siaRow'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80; i++) {                                          // the items load asynchronously after the click
            lastDetailRows = detailRows().size();
            if (lastDetailRows > 0) break;
            page.waitForTimeout(250);
        }
        waitForAngular(500);
        List<String> det = detailRows();
        lastDetailRows = det.size();
        lastDetailText = det.isEmpty() ? "" : det.get(0);
        if (!rowHighlighted()) problems.add("the clicked row is not highlighted as the selected indent");
        if (lastDetailRows == 0) problems.add("clicking " + lastIndentNo + " loaded no items into Indent Details");
        return ok() ? lastIndentNo : "";
    }

    /** Is a row of the Indent List highlighted as the selected indent? Store Indent Approval marks it {@code indent-row-selected}, Store Indent {@code si-selected-row}. */
    public boolean rowHighlighted() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); return !!(t && t.querySelector('tbody tr.indent-row-selected, tbody tr.si-selected-row')); }");
        return Boolean.TRUE.equals(r);
    }

    /** The item rows of <b>Indent Details</b>, each as its cell texts joined with spaces. */
    public List<String> detailRows() {
        Object r = page.evaluate("() => {" + JS + " const t=detailTable(); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent)).map(r=>norm(r.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public List<String> detailHeaders() {
        Object r = page.evaluate("() => {" + JS + " const t=detailTable(); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- Print -----------------------------------------------------------------------------------------------

    /**
     * Click <b>Print</b> (an indent is selected) and judge the report it opens in a new tab: a PDF, fetched and rendered by {@link PdfReport} (its {@code png} is the report's first page — the
     * screenshot a PDF viewer tab cannot give). The report tab is closed afterwards and the screen brought back. {@code r.url} stays empty when no tab opened.
     */
    public PdfReport.Result clickPrintAndCapture() {
        problems.clear();
        lastToast = "";
        pagesBeforePrint = tabCount();
        if (!clickByNg("printReport", "Print")) return new PdfReport.Result();
        PdfReport.Result r = PdfReport.capture(page, pagesBeforePrint, 25000);
        closeExtraTabs();
        lastToast = toastsNow();
        return r;
    }
}
