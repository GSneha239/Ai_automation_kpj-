package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>Blocked Patient Listing</b> — Page Object ({@code #/BlockedPatientListing}, header "Blocked Patient Listing").
 *
 * <p>Confirmed live in a Playwright probe (devhis build v2026.11.03.387). One screen:</p>
 * <ul>
 *   <li><b>Search / Filter</b>: Record Type (All / Patient / Payor), Payor Name, Patient Name, UUID, MRN No., NRIC/Passport, <b>From Date</b> / <b>To Date</b> ({@code #FromDate}, {@code #ToDate},
 *       {@code Search.FromDate} / {@code Search.ToDate}, dd/MM/yyyy picker boxes, today by default), <b>Search</b> ({@code fnSearch()}) and <b>Block</b> ({@code fnOpenManualBlock()});</li>
 *   <li>two tabs under the filter: <b>Blocked Patient Listing Detail</b> (ui-grid {@code gridBlockList}: Blocked Date &amp; Time | Record Type | Location | MRN No. | UUID | Patient Name / Payor Name |
 *       Payor Code | View Details) filled by Search ({@code POST /api/PatientBlock/fetch}), and <b>Block/Unblock History</b> ({@code fnFetchHistory()}, ui-grid {@code gridBlockHistory}: Date | MRN No. |
 *       Patient Name | DOB | NRIC/Passport | Contact Number | Status | Reason | Remarks) filled by {@code POST /api/PatientBlock/FetchBlockHistory} with the same dates.</li>
 * </ul>
 */
public class BlockedPatientListing extends BasePage {

    public BlockedPatientListing(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastToast = "";
    public int lastSearchStatus = 0, lastHistoryStatus = 0;

    private static final String FROM_NG = "Search.FromDate", TO_NG = "Search.ToDate";
    private static final String HREF = "#/blockedpatientlisting";
    public static final String DETAIL = "gridBlockList", HISTORY = "gridBlockHistory";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const grid=n=>document.querySelector(\"[ui-grid='\"+n+\"']\");"
          + "const data=n=>{ try{ return angular.element(grid(n)).scope().$eval(n+'.data')||[]; }catch(e){ return []; } };";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Billing &gt; Blocked Patient Listing — Billing opened only if shut, the leaf looked up by its exact route inside the Billing group. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*billing\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__bpLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__bpLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("BlockedPatientListing.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__bpLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("() => !!document.getElementById('FromDate') && !!document.getElementById('ToDate')", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("BlockedPatientListing.nav: the date boxes are not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    /** On this screen — the route AND the Search button the screen carries, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/blockedpatientlisting/?$")) return false;
        Object b = page.evaluate("() => [...document.querySelectorAll('button')].some(x=>/fnSearch\\(/.test(x.getAttribute('ng-click')||''))");
        return Boolean.TRUE.equals(b);
    }

    protected String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean))].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- dates -----------------------------------------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> (dd/MM/yyyy) — typed first, set through the box's own model when the keystrokes are discarded. */
    public String enterDateRange(String from, String to) {
        problems.clear();
        String a = typeDate("FromDate", FROM_NG, from);
        String b = typeDate("ToDate", TO_NG, to);
        lastFrom = model(FROM_NG);
        lastTo = model(TO_NG);
        if (!from.equals(lastFrom)) problems.add("From Date holds \"" + lastFrom + "\" instead of " + from);
        if (!to.equals(lastTo)) problems.add("To Date holds \"" + lastTo + "\" instead of " + to);
        String r = "From Date " + a + " | To Date " + b;
        System.out.println("BlockedPatientListing: " + r);
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
            // The box is a picker box (class txtDate): when its typed keystrokes are discarded, set the value through the box's own model.
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

    // ---- search + Blocked Patient Listing Detail --------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer ({@code /api/PatientBlock/fetch}) and return how many records Blocked Patient Listing Detail holds. */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnSearch\\(/.test(x.getAttribute('ng-click')||'') && /^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__bpSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains("/api/patientblock/fetch") && !x.url().toLowerCase().contains("history"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__bpSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search did not get an answer from /api/PatientBlock/fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__bpSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(900);
        int n = rowCount(DETAIL);
        for (int i = 0; answered && i < 10 && n == 0; i++) { page.waitForTimeout(300); n = rowCount(DETAIL); }
        lastToast = toastsNow();
        return n;
    }

    /** The records the grid holds (its own data list — the DOM only draws the rows that fit). */
    public int rowCount(String grid) {
        Object n = page.evaluate("(g) => {" + JS + " return data(g).length; }", grid);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The grid's column headers (the sort marker the grid appends is dropped). */
    public List<String> headers(String grid) {
        Object r = page.evaluate("(g) => {" + JS + " const e=grid(g); return e? [...e.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean) : []; }", grid);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The cell texts of the rows the grid draws, one list per row, in column order (the View Details button cell is empty text). */
    @SuppressWarnings("unchecked")
    public List<List<String>> drawnRows(String grid, int max) {
        Object r = page.evaluate("([g,n]) => {" + JS + " const e=grid(g); if(!e) return [];"
                + " return [...e.querySelectorAll('.ui-grid-row')].slice(0,n).map(row=>[...row.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent))); }", java.util.Arrays.asList(grid, max));
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) {
            List<String> cells = new ArrayList<>();
            if (o instanceof List) for (Object c : (List<Object>) o) cells.add(String.valueOf(c));
            out.add(cells);
        }
        return out;
    }

    /** "col=value | …" for the first drawn row of a grid, keyed by its headers — a readable sample for the report. */
    public String sample(String grid) {
        List<String> heads = headers(grid);
        List<List<String>> rows = drawnRows(grid, 1);
        if (rows.isEmpty()) return "";
        List<String> cells = rows.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size() && i < heads.size(); i++) {
            if (cells.get(i).isEmpty()) continue;
            sb.append(sb.length() == 0 ? "" : " | ").append(heads.get(i)).append(" ").append(cells.get(i));
        }
        return sb.toString();
    }

    /**
     * The first column of every drawn row of the grid that is NOT a date inside {@code from}..{@code to} (dd/MM/yyyy, the time part ignored): the rows that break the date range
     * the Search was asked for. Empty = every drawn row lies in the range.
     */
    public List<String> rowsOutsideRange(String grid, String from, String to) {
        List<String> bad = new ArrayList<>();
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate a = java.time.LocalDate.parse(from, f), b = java.time.LocalDate.parse(to, f);
        for (List<String> cells : drawnRows(grid, 200)) {
            String first = cells.isEmpty() ? "" : cells.get(0);
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(first.substring(0, 10), f);
                if (d.isBefore(a) || d.isAfter(b)) bad.add(first);
            } catch (Exception e) { bad.add("\"" + first + "\" (not a date)"); }
        }
        return bad;
    }

    /** The MRN column of every record of the grid (its own data list), blank MRNs left out — payor records carry none. */
    @SuppressWarnings("unchecked")
    public List<String> mrns(String grid) {
        Object r = page.evaluate("(g) => {" + JS + " return data(g).map(x=>String(x.mrn||'').trim()).filter(Boolean); }", grid);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- Block/Unblock History ----------------------------------------------------------------------------------

    /** Open the <b>Block/Unblock History</b> tab, wait for its answer ({@code FetchBlockHistory}) and return how many records its grid holds. */
    public int openHistory() {
        problems.clear();
        lastHistoryStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>/fnFetchHistory/.test(x.getAttribute('ng-click')||''));"
                + " if(!a) return false; a.id='__bpHist'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Block/Unblock History tab is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains("fetchblockhistory"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__bpHist").click(new Locator.ClickOptions().setTimeout(6000)));
            lastHistoryStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("the history answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("the history did not get an answer from FetchBlockHistory: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__bpHist'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(1200);
        int n = rowCount(HISTORY);
        for (int i = 0; answered && i < 10 && n == 0; i++) { page.waitForTimeout(300); n = rowCount(HISTORY); }
        return n;
    }

    /** True when the history grid is on screen (its tab is the open one). */
    public boolean historyShown() {
        Object r = page.evaluate("() => {" + JS + " return vis(grid('" + HISTORY + "')); }");
        return Boolean.TRUE.equals(r);
    }

    /** The distinct values of the history's Status column, read from the rows the grid draws (e.g. Blocked, Unblocked). */
    public List<String> historyStatuses() {
        List<String> out = new ArrayList<>();
        List<String> heads = headers(HISTORY);
        int col = -1;
        for (int i = 0; i < heads.size(); i++) if (heads.get(i).equalsIgnoreCase("Status")) col = i;
        if (col < 0) return out;
        for (List<String> cells : drawnRows(HISTORY, 200)) if (col < cells.size() && !cells.get(col).isEmpty() && !out.contains(cells.get(col))) out.add(cells.get(col));
        return out;
    }
}
