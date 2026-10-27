package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Inventory &gt; Others &gt; <b>Stock Movement Screen</b> — Page Object ({@code #/StockMovementScreen}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). A single read-only screen ({@code Stock.*}):</p>
 * <ul>
 *   <li><b>Location</b> ({@code Stock.LocationID}, pre-selected), <b>From Date</b> ({@code Stock.FromDate}) and <b>To Date</b> ({@code Stock.ToDate}), both default to today. The dates are
 *       720kb DATEPICKERS that discard typed text: click the box (its calendar opens), use the month arrows ({@code prevMonth()} / {@code nextMonth()}) until the calendar shows the month,
 *       click the day ({@code setDatepickerDay}).</li>
 *   <li><b>Store</b> ({@code Stock.SearchStoreID}, select2, empty by default; its change handler is {@code ClearData()}) and <b>Search</b> ({@code fngetMaterialConsuptionList()}).</li>
 *   <li><b>List of Stock Movement</b> — a ui-grid ({@code grid1}): No, Item Name, Item Code, Batch Code, Expirydate, Subgroup Name, Transaction No, Descript, UOM, Transaction Date, From Storename,
 *       Rec Qty, To Storename, Issued Qty, Trans Type, ViewDetails.</li>
 * </ul>
 * Nothing on this screen changes data.
 */
public class StockMovementScreen extends BasePage {

    /** Every API call the screen made that failed, and the Search call's answer: {status, decoded url, body head}. */
    private final List<String[]> failedCalls = new ArrayList<>();
    private final List<String[]> searchCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/") || low.contains("getinternalmsg") || low.contains("commondropdown")) return;
            boolean bad = r.status() >= 400;
            boolean search = "POST".equalsIgnoreCase(r.request().method()) && low.contains("stock");
            if (!bad && !search) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 400 ? body.substring(0, 400) : body};
            synchronized (failedCalls) { if (bad) failedCalls.add(rec); if (search) searchCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public StockMovementScreen(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastStore = "", lastLocation = "", lastNotice = "";
    public int lastRowCount = 0;
    /** How long the last Search took from the click to the grid (or to giving up), in ms. */
    public long lastSearchMs = 0;
    /** Longest the page waits for a Search to answer. */
    private static final long SEARCH_TIMEOUT_MS = Long.getLong("stockmovement.timeoutms", 150000L);

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String FROM_NG = "Stock.FromDate", TO_NG = "Stock.ToDate", STORE_NG = "Stock.SearchStoreID", LOCATION_NG = "Stock.LocationID";
    private static final String HREF = "#/stockmovementscreen";
    private static final String BODY_ROWS = "[ui-grid] .ui-grid-render-container-body .ui-grid-canvas .ui-grid-row";

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
     * Inventory &gt; Others &gt; Stock Movement Screen. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on Others straight after Inventory is lost), so Inventory gets a settling pause before Others is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Others") < 0) {                                // Others is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("Others") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before Others is clicked
                }
                if (groupState("Others") == 0 && !linkVisible()) clickGroup("Others");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__smLink'; }", HREF);
                try { page.locator("#__smLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("StockMovementScreen.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__smLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!e && e.options.length>1; }", STORE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("StockMovementScreen.nav: the Store list is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/stockmovementscreen/?$"); }

    // ---- small helpers ---------------------------------------------------------------------------------------

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String selectedText(String ng) {
        Object v = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o? norm(o.textContent) : '';"
                + " return /^-*\\s*select\\s*-*$/i.test(t)? '' : t; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private static String fmt(LocalDate d) { return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()); }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- dates ---------------------------------------------------------------------------------------------------------

    /** The value a date box holds ("dd/MM/yyyy"), read from its model. */
    public String dateValue(String ng) { return model(ng); }

    public String fromDate() { lastFrom = model(FROM_NG); return lastFrom; }
    public String toDate() { lastTo = model(TO_NG); return lastTo; }

    /** Parse a "dd/MM/yyyy" box value; null when it is not one. */
    public static LocalDate parse(String ddMMyyyy) {
        try {
            String[] p = ddMMyyyy.trim().split("/");
            return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        } catch (Exception e) { return null; }
    }

    /**
     * Pick {@code target} as the <b>From Date</b>, the way the datepicker takes it: click the box (its calendar opens), use the month arrows until the calendar shows the target month, click
     * the day. Returns the value the box then holds ("" with a problem when it could not be picked).
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
                + " if(!day) return false; day.id='__smDay'; return true; }", Map.of("s", box, "day", target.getDayOfMonth()));
        if (!Boolean.TRUE.equals(ok)) { problems.add("day " + target.getDayOfMonth() + " is not selectable in the calendar"); return ""; }
        try { page.locator("#__smDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__smDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        fromDate();
        if (!lastFrom.equals(fmt(target))) problems.add("From Date reads \"" + lastFrom + "\" instead of " + fmt(target));
        return lastFrom;
    }

    // ---- store, search, list --------------------------------------------------------------------------------------------

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
        waitForAngular(1200);
        String held = model(STORE_NG);
        if (held.isEmpty() || "0".equals(held)) problems.add("the Store is not held by the model");
        lastLocation = selectedText(LOCATION_NG);
        return lastStore;
    }

    /** The grid's column headers (the sort-order digit the grid appends is dropped). */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('[ui-grid] .ui-grid-header-cell .ui-grid-cell-contents')].map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean); }");
        return toList(r);
    }

    /** Number of rows in the grid's body. */
    public int rowCount() {
        Object n = page.evaluate("(s) => document.querySelectorAll(s).length", BODY_ROWS);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The grid's rows as header -> cell text. */
    public List<Map<String, String>> rows() {
        List<String> heads = headers();
        Object r = page.evaluate("(s) => {" + JS + " return [...document.querySelectorAll(s)].map(row=>[...row.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent))); }", BODY_ROWS);
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) {
            for (Object row : (List<?>) r) {
                Map<String, String> m = new LinkedHashMap<>();
                List<?> cells = (List<?>) row;
                for (int i = 0; i < cells.size(); i++) m.put(i < heads.size() ? heads.get(i) : "col" + i, String.valueOf(cells.get(i)));
                out.add(m);
            }
        }
        return out;
    }

    /** The pager text under the grid ("1 - 25 of 2623 items"), "" when there is none. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Statistics over ALL rows the grid holds (its data array, not just the ~13 rows the viewport paints): total, how many are dated inside / outside {@code from}-{@code to} (dd/MM/yyyy), how
     * many rows lack an item or a Transaction No or name neither the {@code store} as From nor To store, the rows per transaction type, and the first row's fields. Null when the grid has no data.
     */
    public com.google.gson.JsonObject stats(String from, String to, String store) {
        Object r = page.evaluate("(a) => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; if(!d||!d.length) return '';"
                + " const parse=s=>{ const m=/^(\\d{2})\\/(\\d{2})\\/(\\d{4})/.exec(s||''); return m? new Date(+m[3], +m[2]-1, +m[1]) : null; };"
                + " const f=parse(a.from), t=parse(a.to); const byType={}; let inR=0, out=0, noDate=0, noItem=0, noNo=0, storeMiss=0;"
                + " d.forEach(r=>{ const ty=r.TransType||'(no type)'; byType[ty]=(byType[ty]||0)+1; const dt=parse(r.TransactionDate); if(!dt) noDate++; else if(dt<f||dt>t) out++; else inR++;"
                + "   if(!r.ItemName && !r.Itemcode) noItem++; if(!r.TransactionNo) noNo++;"
                + "   const k=x=>String(x||'').toLowerCase().replace(/\\s+/g,''); if(k(r.FromStorename)!==k(a.store) && k(r.ToStorename)!==k(a.store)) storeMiss++; });"
                + " const x=d[0]; const first={ItemName:x.ItemName, Itemcode:x.Itemcode, batchcode:x.batchcode, TransactionNo:x.TransactionNo, TransactionDate:x.TransactionDate, TransType:x.TransType,"
                + "   FromStorename:x.FromStorename, ToStorename:x.ToStorename, RecQty:x.RecQty, IssuedQty:x.IssuedQty};"
                + " return JSON.stringify({total:d.length, inRange:inR, outsideRange:out, noDate:noDate, noItem:noItem, noTransactionNo:noNo, storeMiss:storeMiss, byType:byType, first:first}); }catch(e){ return ''; } }",
                Map.of("from", from, "to", to, "store", store));
        String s = r == null ? "" : r.toString();
        return s.isEmpty() ? null : com.google.gson.JsonParser.parseString(s).getAsJsonObject();
    }

    /**
     * Click Search and wait for the answer: rows in the grid, or the screen saying there is nothing. Returns the number of rows in the grid body (also {@link #lastRowCount}); a failed
     * API call is reported as a problem.
     */
    public int clickSearch() {
        problems.clear();
        lastNotice = "";
        synchronized (failedCalls) { failedCalls.clear(); searchCalls.clear(); }
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fngetMaterialConsuptionList/.test(x.getAttribute('ng-click')||'') && /^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return false; b.id='__smSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        try { page.locator("#__smSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked"); return 0; }
        finally { page.evaluate("() => { const e=document.getElementById('__smSearch'); if(e) e.removeAttribute('id'); }"); }
        // The answer is slow and its time varies a lot (a few seconds to over 30 s for ~2600 rows), so wait for the server's reply first, then for the grid to paint it.
        long start = System.currentTimeMillis(), end = start + SEARCH_TIMEOUT_MS, answeredAt = 0;
        while (System.currentTimeMillis() < end) {
            lastRowCount = rowCount();
            if (lastRowCount > 0) break;
            String t = toastsNow();
            if (!t.isEmpty()) { lastNotice = t; page.waitForTimeout(1500); break; }                // the screen answered with a message: give a late grid a moment
            synchronized (failedCalls) {
                if (!failedCalls.isEmpty()) break;
                if (answeredAt == 0 && !searchCalls.isEmpty()) {
                    answeredAt = System.currentTimeMillis();
                    String body = searchCalls.get(searchCalls.size() - 1)[2].trim();
                    if (body.equals("[]") || body.equals("null") || body.isEmpty()) { page.waitForTimeout(1500); break; }   // the server found nothing
                }
            }
            if (answeredAt != 0 && System.currentTimeMillis() - answeredAt > 15000) break;         // answered, but the grid never painted a row
            page.waitForTimeout(400);
        }
        lastSearchMs = System.currentTimeMillis() - start;
        waitForAngular(800);
        lastRowCount = rowCount();
        if (lastNotice.isEmpty()) lastNotice = toastsNow();
        synchronized (failedCalls) { for (String[] c : failedCalls) problems.add("a call failed: HTTP " + c[0] + " " + c[1].replaceAll("^https?://[^/]+", "") + " " + c[2].replaceAll("\\s+", " ")); }
        return lastRowCount;
    }

    /** What the Search call answered (status + a short body head), for the report. */
    public String searchAnswer() {
        StringBuilder sb = new StringBuilder();
        synchronized (failedCalls) {
            for (String[] c : searchCalls) {
                String u = c[1].replaceAll("^https?://[^/]+", ""), b = c[2].replaceAll("\\s+", " ");
                sb.append("[HTTP ").append(c[0]).append(" ").append(u, 0, Math.min(120, u.length())).append(" -> ").append(b, 0, Math.min(120, b.length())).append("] ");
            }
        }
        return sb.toString().trim();
    }

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
