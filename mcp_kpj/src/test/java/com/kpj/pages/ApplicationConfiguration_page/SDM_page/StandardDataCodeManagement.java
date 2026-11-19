package com.kpj.pages.ApplicationConfiguration_page.SDM_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Configuration &gt; SDM &gt; <b>Standard Data Code Management</b> — Page Object ({@code #/StandardDataCodeManagement}).
 *
 * <p>Read-only use: filter <b>Master</b> → <b>Search</b> (posts {@code /api/SDMCodeRequest/fetch}, ExecFlag GRID) → the request
 * table (Location, Master Type, Code, Description, Type, Request Status, Requestor, … — 25 rows per page).</p>
 */
public class StandardDataCodeManagement extends BasePage {

    private static final String HREF = "#/standarddatacodemanagement";
    private static final String SEARCH_BTN = "button[ng-click='fnSearch()']";
    private static final String MASTER = "select[ng-model='Search.mastertypeid']";
    private static final String ROW = "tr[ng-repeat^='row in fnPageList()']";

    /** Rows the server returned for the last Search (-1 when the answer could not be read). */
    public int serverRows = -1;
    /** "POST /api/SDMCodeRequest/fetch 200 -> n row(s)" for the last Search. */
    public String searchHttp = "";
    /** Screenshot taken right after the last Search's rows were drawn. */
    public byte[] searchPng;

    public StandardDataCodeManagement(Page page) { super(page); }

    // ---- navigation -------------------------------------------------------

    /** Application Configuration → SDM → Standard Data Code Management through the sidebar. True only when on the screen. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*application configuration\\s*$");
            expandMenuEntry("^\\s*application configuration\\s*$", "^\\s*sdm\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application configuration\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__sdmLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__sdmLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("SDM.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__sdmLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(SEARCH_BTN, new Page.WaitForSelectorOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("SDM.nav: the Search button is not up"); }
        if (onScreen()) prepare();
        return onScreen();
    }

    /** On this screen — the route AND its Search button. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(HREF)) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", SEARCH_BTN));
    }

    /** Scope finder; waits for the screen's own first load (Master list + first search) so it cannot overwrite our Search. */
    private void prepare() {
        page.evaluate("() => { window.__sdmScope = function(){ const b=document.querySelector(\"" + SEARCH_BTN + "\"); if(!b||!window.angular) return null;"
                + " let s=angular.element(b).scope(); for(let i=0;i<10&&s;i++){ if(s.Search!==undefined && s.fnSearch) return s; s=s.$parent; } return null; }; }");
        try {
            page.waitForFunction("() => { const s=window.__sdmScope(); return s && s.searched === true && (s.MasterTypeList||[]).length > 0; }", null,
                    new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { System.out.println("SDM.prepare: the first load did not finish"); }
        waitForAngular(1000);
    }

    // ---- filter + search ---------------------------------------------------

    /** The Master dropdown's options (without "--Select--"). */
    @SuppressWarnings("unchecked")
    public List<String> masterNames() {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); return e ? [...e.options].map(o=>o.text.trim()).filter(t=>t && !/^--/.test(t)) : []; }", MASTER);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Choose a Master (real select). Returns "" when the dropdown now shows it, else why not. */
    public String selectMaster(String name) {
        Locator sel = page.locator(MASTER).first();
        if (sel.count() == 0) return "the Master dropdown is not on the screen";
        try { sel.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(name)); }
        catch (Exception e) { return String.valueOf(e.getMessage()).split("\n")[0]; }
        waitForAngular(500);
        String shown = masterShown();
        return name.equalsIgnoreCase(shown) ? "" : "the dropdown shows \"" + shown + "\"";
    }

    public String masterShown() {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.trim() : ''; }", MASTER);
        return r == null ? "" : r.toString();
    }

    /** Click Search (real click); record the server's answer and wait until the table has redrawn. False when it could not be clicked. */
    public boolean clickSearch() {
        serverRows = -1; searchHttp = ""; searchPng = null;
        Locator btn = page.locator(SEARCH_BTN + ":visible").first();
        if (btn.count() == 0) return false;
        try {
            com.microsoft.playwright.Response res = page.waitForResponse(
                    r -> r.url().toLowerCase().contains("/api/sdmcoderequest/fetch") && String.valueOf(r.request().postData()).contains("\"GRID\""),
                    new Page.WaitForResponseOptions().setTimeout(40000),
                    () -> btn.click(new Locator.ClickOptions().setTimeout(6000)));
            String body = res.text();
            Object n = page.evaluate("(b) => { try { const j=JSON.parse(b); return Array.isArray(j) ? j.length : -1; } catch(e) { return -1; } }", body == null ? "" : body);
            serverRows = n instanceof Number ? ((Number) n).intValue() : -1;
            searchHttp = "POST /api/SDMCodeRequest/fetch " + res.status() + " -> " + (serverRows >= 0 ? serverRows + " row(s)" : "(answer not readable)");
        } catch (Exception e) {
            searchHttp = "no answer from the server (" + String.valueOf(e.getMessage()).split("\n")[0] + ")";
        }
        try { page.waitForFunction("() => { const s=window.__sdmScope(); return s && s.searched === true; }", null, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception ignore) { }
        waitForAngular(1200);
        try { page.locator(ROW).first().scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(2000)); } catch (Exception ignore) { }
        searchPng = page.screenshot();
        return true;
    }

    // ---- table -------------------------------------------------------------

    /** The request table's column headers, as shown. */
    @SuppressWarnings("unchecked")
    public List<String> headers() {
        Object r = page.evaluate("(row) => { const r=document.querySelector(row); const t=r ? r.closest('table') : [...document.querySelectorAll('table')].find(x=>/Request Status/.test(x.textContent));"
                + " return t ? [...t.querySelectorAll('thead th')].filter(h=>h.offsetParent!==null).map(h=>h.textContent.replace(/\\s+/g,' ').trim()) : []; }", ROW);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** The rows drawn on this page of the table (cell texts; tick / radio cells read as "ticked" / ""). */
    @SuppressWarnings("unchecked")
    public List<List<String>> rows() {
        Object r = page.evaluate("(row) => [...document.querySelectorAll(row)].filter(x=>x.offsetParent!==null).map(x=>[...x.querySelectorAll('td')].map(td=>{"
                + " const c=td.querySelector('input[type=checkbox],input[type=radio]'); if(c && !td.textContent.trim()) return c.checked ? 'ticked' : '';"
                + " return td.textContent.replace(/\\s+/g,' ').trim(); }))", ROW);
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(o instanceof List ? (List<String>) o : new ArrayList<>());
        return out;
    }

    /** How many requests the table holds in total (all pages, after the Search queue box), -1 when unknown. */
    public int totalItems() {
        Object r = page.evaluate("() => { const s=window.__sdmScope(); return s && s.fnFilteredList ? s.fnFilteredList().length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Rows per page of the table. */
    public int pageSize() {
        Object r = page.evaluate("() => { const s=window.__sdmScope(); return s && s.PageSize ? s.PageSize : 25; }");
        return r instanceof Number ? ((Number) r).intValue() : 25;
    }

    /** The table's own "nothing found" text, or "". */
    public String emptyMessage() {
        Object r = page.evaluate("() => { const td=[...document.querySelectorAll('td[colspan]')].find(t=>t.offsetParent!==null && /no records/i.test(t.textContent)); return td ? td.textContent.trim() : ''; }");
        return r == null ? "" : r.toString();
    }
}
