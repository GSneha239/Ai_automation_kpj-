package com.kpj.pages.Inventory_page.StockTakingActivity_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>Stock Take Approval</b> — Page Object ({@code #/StockTakeApproval}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). One screen ({@code PhysicalStock.*}), used here READ-ONLY:</p>
 * <ul>
 *   <li>filters: From Date / To Date (default today; 720kb datepickers that discard typed text), <b>Store*</b> ({@code PhysicalStock.SearchStoreID}, select2, empty by default), Category, Sub Category
 *       and an Approval tick; <b>Search</b> ({@code getPhysicalStockSearchList1()}, POST {@code /api/PhysicalStock/fetch});</li>
 *   <li>the upper table lists the stock takes found: Stock No, Store Name, User, Date Time, <b>Approval</b> (a checkbox — NEVER touched here, it approves), Approval By, Approval Date Time and a
 *       <b>Close</b> column with a "Forcefully Close" button (also never touched). Clicking a row ({@code getPhysicalStockSearchList(Itm)}) loads its items into the grid below ({@code #grid1});</li>
 *   <li><b>Print</b> ({@code printStockTakeReport()}) answers the toast "Please Select Stock Take!" until a row has been selected; with one selected it opens a NEW TAB titled "Stock Take Approval Report".
 *       That tab stays on {@code about:blank} and holds an HTML document written by the page (hospital header, Stock No, Store Name, User, Date Time, Approval By, an item table with System / Physical /
 *       Adjusted Qty, Variance Type, WAC and Variance, and "Print Date &amp; Time ... Page 1 of 1") — not a PDF, so the shared {@code PdfReport} check cannot judge it; it is judged on its content.</li>
 * </ul>
 */
public class StockTakeApproval extends BasePage {

    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/") || low.contains("getinternalmsg") || low.contains("commondropdown") || r.status() < 400) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            synchronized (failedCalls) { failedCalls.add(new String[]{String.valueOf(r.status()), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 300 ? body.substring(0, 300) : body}); }
        } catch (Exception ignore) { }
    };

    public StockTakeApproval(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastFrom = "", lastTo = "", lastToast = "", lastStockNo = "", lastRowText = "";
    public int lastRowCount = 0, lastDetailRows = 0;

    /** What the Print document held (see {@link #clickPrintAndCapture()}). */
    public static final class Doc {
        public boolean opened;
        public String title = "", text = "", diagnostics = "";
        public int textLen, tables, rows, images;
        public byte[] png;
    }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const listTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>/Stock No/.test(norm(t.textContent)) && /Approval By/.test(norm(t.textContent)));";

    private static final String STORE_NG = "PhysicalStock.SearchStoreID", FROM_NG = "PhysicalStock.FromDate", TO_NG = "PhysicalStock.ToDate";
    private static final String HREF = "#/stocktakeapproval";

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    /** Is the menu group with this label expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int groupState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Inventory &gt; Stock Taking Activity &gt; Stock Take Approval. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the
     * group again, and a click on Stock Taking Activity straight after Inventory is lost), so Inventory gets a settling pause before the next group is clicked.
     */
    public boolean navigateViaMenu() {
        final String group = "Stock Taking Activity";
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState(group) < 0) {                                   // the group is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState(group) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before the group is clicked
                }
                if (groupState(group) == 0 && !linkVisible()) clickGroup(group);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__staLink'; }", HREF);
                try { page.locator("#__staLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("StockTakeApproval.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__staLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!e && e.options.length>1; }", STORE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("StockTakeApproval.nav: the Store list is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/stocktakeapproval/?$"); }

    // ---- small helpers ---------------------------------------------------------------------------------------

    private boolean clickByNg(String re, String what) {
        Object tagged = page.evaluate("(re) => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>new RegExp(re).test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__staBtn'; return true; }", re);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__staBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__staBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    public String fromDate() { lastFrom = model(FROM_NG); return lastFrom; }
    public String toDate() { lastTo = model(TO_NG); return lastTo; }

    /** Parse a "dd/MM/yyyy" box value; null when it is not one. */
    public static LocalDate parse(String ddMMyyyy) {
        try {
            String[] p = ddMMyyyy.trim().split("/");
            return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        } catch (Exception e) { return null; }
    }

    private static String fmt(LocalDate d) { return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()); }

    // ---- filters ---------------------------------------------------------------------------------------------

    /** Choose the Store (empty by default). Matches ignoring case and spaces. */
    public String selectStore(String want) {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", STORE_NG, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>k(x.t)===k(a.w)); if(!p) p=o.find(x=>k(x.t).indexOf(k(a.w))>=0); if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", STORE_NG, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String held = model(STORE_NG);
        if (held.isEmpty() || "0".equals(held)) problems.add("the Store is not held by the model");
        return ok() ? lastStore : "";
    }

    /**
     * Pick {@code target} as the <b>From Date</b>, the way the datepicker takes it: click the box (its calendar opens), use the month arrows until the calendar shows the month, click the day.
     * Returns the value the box then holds ("" with a problem when it could not be picked).
     */
    public String pickFromDate(LocalDate target) {
        problems.clear();
        String box = "input[ng-model='" + FROM_NG + "']";
        try {
            Locator b = page.locator(box).first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForFunction("(s) => { const e=document.querySelector(s); const d=e && e.closest('.datepicker'); return !!d && !!d.querySelector('._720kb-datepicker-open'); }",
                    box, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the From Date calendar did not open"); return ""; }
        for (int i = 0; i < 40; i++) {
            Object diff = page.evaluate("(a) => {" + JS
                    + " const d=document.querySelector(a.s).closest('.datepicker'); const h=norm(d.querySelector('._720kb-datepicker-calendar-header-middle').textContent);"
                    + " const m=h.match(/([A-Za-z]+)\\s+(\\d{4})/); if(!m) return 999;"
                    + " const mi=['january','february','march','april','may','june','july','august','september','october','november','december'].indexOf(m[1].toLowerCase());"
                    + " return (a.y*12+a.m) - (parseInt(m[2])*12+mi); }", Map.of("s", box, "y", target.getYear(), "m", target.getMonthValue() - 1));
            int d = diff instanceof Number ? ((Number) diff).intValue() : 999;
            if (d == 0) break;
            if (d == 999) { problems.add("the calendar header could not be read"); return ""; }
            Object clicked = page.evaluate("(a) => { const dp=document.querySelector(a.s).closest('.datepicker');"
                    + " const arrow=dp.querySelector(a.d>0 ? 'a[ng-click=\"nextMonth()\"]' : 'a[ng-click=\"prevMonth()\"]'); if(!arrow) return false; arrow.click(); return true; }",
                    Map.of("s", box, "d", d));
            if (!Boolean.TRUE.equals(clicked)) { problems.add("the calendar has no month arrow"); return ""; }
            page.waitForTimeout(250);
        }
        Object ok = page.evaluate("(a) => { const dp=document.querySelector(a.s).closest('.datepicker');"
                + " const day=[...dp.querySelectorAll('a[ng-click^=\"setDatepickerDay\"]')].find(x=>(x.textContent||'').trim()===String(a.day) && !/disabled|_720kb-datepicker-disabled/.test(x.className));"
                + " if(!day) return false; day.id='__staDay'; return true; }", Map.of("s", box, "day", target.getDayOfMonth()));
        if (!Boolean.TRUE.equals(ok)) { problems.add("day " + target.getDayOfMonth() + " is not selectable in the calendar"); return ""; }
        try { page.locator("#__staDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__staDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        fromDate();
        if (!lastFrom.equals(fmt(target))) problems.add("From Date reads \"" + lastFrom + "\" instead of " + fmt(target));
        return lastFrom;
    }

    // ---- search + the stock takes found ---------------------------------------------------------------------------------

    /** The stock takes listed in the upper table: each row as its cell texts (the Approval checkbox and the Close button shown as "[ ]" / the button's label). */
    public List<List<String>> stockTakes() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(r=>[...r.querySelectorAll('td')].map(td=>{ const c=td.querySelector('input[type=checkbox]'); return c? (c.checked? '[x]' : '[ ]') : norm(td.textContent); })); }");
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<String> cells = new ArrayList<>(); for (Object c : (List<?>) row) cells.add(String.valueOf(c)); out.add(cells); }
        return out;
    }

    /** The upper table's column headers. */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click Search and wait for the answer (rows, or the screen saying there is nothing). Returns the number of stock takes listed (also {@link #lastRowCount}). */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        synchronized (failedCalls) { failedCalls.clear(); }
        if (!clickByNg("getPhysicalStockSearchList1", "Search")) return 0;
        long end = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < end) {
            lastRowCount = stockTakes().size();
            if (lastRowCount > 0) break;
            String t = toastsNow();
            if (!t.isEmpty()) { lastToast = t; page.waitForTimeout(1200); lastRowCount = stockTakes().size(); break; }
            synchronized (failedCalls) { if (!failedCalls.isEmpty()) break; }
            page.waitForTimeout(400);
        }
        waitForAngular(800);
        lastRowCount = stockTakes().size();
        if (lastToast.isEmpty()) lastToast = toastsNow();
        synchronized (failedCalls) { for (String[] c : failedCalls) problems.add("a call failed: HTTP " + c[0] + " " + c[1].replaceAll("^https?://[^/]+", "") + " " + c[2].replaceAll("\\s+", " ")); }
        return lastRowCount;
    }

    // ---- Print -----------------------------------------------------------------------------------------------------------

    /** Click Print and report what the screen answered: the toast text ("" when none). A report tab it may have opened is left for {@link #clickPrintAndCapture()}'s caller to deal with. */
    public String clickPrint() {
        problems.clear();
        lastToast = "";
        if (!clickByNg("printStockTakeReport", "Print")) return "";
        for (int i = 0; i < 20 && toastsNow().isEmpty(); i++) page.waitForTimeout(250);
        lastToast = toastsNow();
        return lastToast;
    }

    /** Number of browser tabs open. */
    public int tabCount() { return page.context().pages().size(); }

    /**
     * Select a stock take by clicking its row — on the Stock No cell, never on the Approval checkbox or the Close button — and wait for its items to load into the grid below. Returns the Stock No
     * of the row ("" when there is none).
     */
    public String selectFirstRow() {
        problems.clear();
        Object t = page.evaluate("() => {" + JS + " const t=listTable(); const r=t&&t.querySelector('tbody tr'); if(!r) return '';"
                + " const c=[...r.querySelectorAll('td')].find(td=>vis(td) && norm(td.textContent) && !td.querySelector('input,button')); if(!c) return ''; c.id='__staRow';"
                + " return norm(c.textContent) + '||' + norm(r.textContent).slice(0,200); }");
        String s = t == null ? "" : t.toString();
        if (s.isEmpty()) { problems.add("there is no stock take row to click"); return ""; }
        lastStockNo = s.substring(0, s.indexOf("||"));
        lastRowText = s.substring(s.indexOf("||") + 2);
        try { page.locator("#__staRow").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the row could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__staRow'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 60; i++) {
            lastDetailRows = detailRowCount();
            if (lastDetailRows > 0) break;
            page.waitForTimeout(250);
        }
        waitForAngular(500);
        lastDetailRows = detailRowCount();
        if (lastDetailRows == 0) problems.add("clicking " + lastStockNo + " loaded no items into the grid below");
        return ok() ? lastStockNo : "";
    }

    /** Rows in the item grid below the list. */
    public int detailRowCount() {
        Object n = page.evaluate("() => document.querySelectorAll('#grid1 .ui-grid-render-container-body .ui-grid-row').length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Click Print (with a row selected) and capture the document it opens: a new tab (titled "Stock Take Approval Report") whose page is written by the app, so its URL stays {@code about:blank}.
     * Waits for the tab to carry text, reads its title, text, tables, rows and images, takes its screenshot, then closes it and brings the main page back. {@link Doc#opened} is false when no tab
     * opened.
     */
    public Doc clickPrintAndCapture() {
        problems.clear();
        Doc d = new Doc();
        int before = tabCount();
        lastToast = "";
        if (!clickByNg("printStockTakeReport", "Print")) { d.diagnostics = "the Print button could not be clicked"; return d; }
        Page tab = null;
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            List<Page> pages = page.context().pages();
            if (pages.size() > before) {
                Page t = pages.get(pages.size() - 1);
                try {
                    Object len = t.evaluate("() => (document.body? (document.body.innerText||'').replace(/\\s+/g,' ').trim().length : 0)");
                    if (len instanceof Number && ((Number) len).intValue() > 50) { tab = t; break; }
                } catch (Exception ignore) { }
            } else {
                String toast = toastsNow();
                if (!toast.isEmpty()) { lastToast = toast; break; }
            }
            page.waitForTimeout(400);
        }
        if (tab == null) {
            d.diagnostics = tabCount() > before ? "a tab opened but it never carried any text" : "no report tab opened" + (lastToast.isEmpty() ? "" : " — the screen said \"" + lastToast + "\"");
            closeExtraTabs();
            return d;
        }
        d.opened = true;
        try {
            tab.waitForTimeout(1500);                                             // let a late image / table finish
            Object o = tab.evaluate("() => { const q=s=>document.querySelectorAll(s).length; const t=(document.body.innerText||'').replace(/\\s+/g,' ').trim();"
                    + " return JSON.stringify({title:document.title, text:t, tables:q('table'), rows:q('tr'), imgs:q('img')}); }");
            com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(String.valueOf(o)).getAsJsonObject();
            d.title = j.get("title").getAsString();
            d.text = j.get("text").getAsString();
            d.textLen = d.text.length();
            d.tables = j.get("tables").getAsInt();
            d.rows = j.get("rows").getAsInt();
            d.images = j.get("imgs").getAsInt();
            try { d.png = tab.screenshot(new Page.ScreenshotOptions().setFullPage(true)); } catch (Exception e) { d.png = null; }
        } catch (Exception e) { d.diagnostics = "the report tab could not be read: " + String.valueOf(e.getMessage()).split("\n")[0]; }
        closeExtraTabs();
        return d;
    }

    private void closeExtraTabs() {
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
    }
}
