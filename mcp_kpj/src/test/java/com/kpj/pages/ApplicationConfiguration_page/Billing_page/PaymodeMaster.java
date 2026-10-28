package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Application Configuration &gt; Billing &gt; <b>Paymode Master</b> — Page Object ({@code #/PaymodeMasterN}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). A read-only list screen ({@code paymodeMaster.*}): a <b>Location</b> select ({@code paymodeMaster.locationid}, pre-selected to the logged-in hospital),
 * <b>Search</b> ({@code fngetMaterialConsuptionList()}, under a second) and the <b>List of Paymode</b> ui-grid ({@code #grid1}: Paymode Id, Code, Paymode Type, Ledger Name, GL Code; 25 rows a page, 60 rows
 * in all at the time, e.g. {@code 1 | H-AMEX | Card | AMEX - HSBC}). The grid is EMPTY until Search is clicked. There is no Add button on this screen. The grid paints only the rows that fit its
 * viewport (about 13), so the checks read the grid's own data array.</p>
 *
 * <p>"Billing" is also a top-level module of the sidebar, listed before Application Configuration, so the Billing menu group is looked up INSIDE the Application Configuration entry only.</p>
 *
 * <p>Nothing on this screen changes data.</p>
 */
public class PaymodeMaster extends BasePage {

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

    public PaymodeMaster(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastLocation = "", lastToast = "";
    public int lastRowCount = 0;
    public long lastSearchMs = 0;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/paymodemastern";
    private static final String PARENT = "Application Configuration", GROUP = "Billing";

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

    /** Is the top-level menu entry {@code label} expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /** Open/shut state of the group {@code label} looked up INSIDE the entry {@code parent}: 1 open, 0 shut, -1 not on screen. */
    private int innerState(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                java.util.Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", java.util.Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /**
     * Application Configuration &gt; Billing &gt; Paymode Master. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on Billing straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked. "Billing" is looked up inside the
     * Application Configuration entry because it is also a top-level module.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {                            // Billing is not on screen inside Application Configuration: it is shut
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__pmLink'; }", HREF);
                try { page.locator("#__pmLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PaymodeMaster.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__pmLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('button')].find(b=>/fngetMaterialConsuptionList/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PaymodeMaster.nav: the Search button is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/paymodemastern/?$"); }

    // ---- the screen ----------------------------------------------------------------------------------------------

    /** The Location select's chosen option ("KPJ DAMANSARA SPECIALIST HOSPITAL"), "" when none. */
    public String location() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"[ng-model='paymodeMaster.locationid']\"); if(!e) return ''; const o=e.options[e.selectedIndex]; return o? norm(o.textContent) : ''; }");
        lastLocation = r == null ? "" : r.toString();
        return lastLocation;
    }

    /** The grid's column headers as shown (the sort-order digit the grid appends is dropped). */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-header-cell .ui-grid-cell-contents')].map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Rows the grid has PAINTED (its viewport shows only about 13 of the page's 25). */
    public int paintedRows() {
        Object n = page.evaluate("() => document.querySelectorAll('#grid1 .ui-grid-render-container-body .ui-grid-canvas .ui-grid-row').length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Rows in the grid's data array (every row the Search returned, over all pages). */
    public int dataRows() {
        Object n = page.evaluate("() => { try{ const g=document.getElementById('grid1'); return angular.element(g).scope().gridOptions.data.length; }catch(e){ return 0; } }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The pager text ("1 - 25 of 60 items"), "" when there is none. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click Search and wait for the answer (rows, or the screen saying there is nothing). Returns the number of rows in the grid's data (also {@link #lastRowCount}); a failed API call is reported as a
     * problem.
     */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        synchronized (failedCalls) { failedCalls.clear(); }
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fngetMaterialConsuptionList/.test(x.getAttribute('ng-click')||'') && /^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return false; b.id='__pmSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        long start = System.currentTimeMillis();
        try { page.locator("#__pmSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked"); return 0; }
        finally { page.evaluate("() => { const e=document.getElementById('__pmSearch'); if(e) e.removeAttribute('id'); }"); }
        long end = start + 90000;
        while (System.currentTimeMillis() < end) {
            lastRowCount = dataRows();
            if (lastRowCount > 0 && paintedRows() > 0) break;
            String t = toastsNow();
            if (!t.isEmpty()) { lastToast = t; page.waitForTimeout(1200); break; }
            synchronized (failedCalls) { if (!failedCalls.isEmpty()) break; }
            page.waitForTimeout(300);
        }
        lastSearchMs = System.currentTimeMillis() - start;
        waitForAngular(600);
        lastRowCount = dataRows();
        if (lastToast.isEmpty()) lastToast = toastsNow();
        synchronized (failedCalls) { for (String[] c : failedCalls) problems.add("a call failed: HTTP " + c[0] + " " + c[1].replaceAll("^https?://[^/]+", "") + " " + c[2].replaceAll("\\s+", " ")); }
        return lastRowCount;
    }

    /**
     * Statistics over ALL rows of the grid's data array, keyed by the grid's own columns (its {@code columnDefs}: header text -&gt; field): the total, and for each column how many rows have a value;
     * plus the first rows as {@code Header: value | ...} and the number of distinct Paymode Ids. Null when the grid holds nothing.
     */
    public com.google.gson.JsonObject stats(int sample) {
        Object r = page.evaluate("(m) => { try{" + JS + " const g=document.getElementById('grid1'); const s=angular.element(g).scope(); const d=s.gridOptions.data; if(!d||!d.length) return '';"
                + " const cols=(s.gridOptions.columnDefs||[]).filter(c=>c.field && (c.displayName||c.name)).map(c=>({h:norm(c.displayName||c.name), f:c.field}));"
                + " const filled={}; cols.forEach(c=>filled[c.h]=d.filter(x=>x[c.f]!=null && String(x[c.f]).trim()!=='').length);"
                + " const idCol=cols.find(c=>/id$/i.test(c.h)); const ids=idCol? new Set(d.map(x=>String(x[idCol.f]))).size : -1;"
                + " const first=d.slice(0,m).map(x=>cols.map(c=>c.h+': '+(x[c.f]==null? '' : String(x[c.f]).trim())).join(' | '));"
                + " return JSON.stringify({total:d.length, columns:cols.map(c=>c.h), filled:filled, distinctIds:ids, first:first}); }catch(e){ return ''; } }", sample);
        String s = r == null ? "" : r.toString();
        return s.isEmpty() ? null : com.google.gson.JsonParser.parseString(s).getAsJsonObject();
    }
}
