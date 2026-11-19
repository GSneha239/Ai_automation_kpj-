package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Current BloodBag Stock</b> — Page Object (read-only screen).
 *
 * <p>Route {@code #/CurrentBloodBagStock}. Filters: Location ({@code CurrentBloodBagStock.locationid}, KPJ pre-selected), Component
 * ({@code componentid}), Blood Group ({@code bloodgroupid}); <b>Search</b> ({@code fnfetchlist()}) fills a ui-grid (Location Name |
 * Component Name | Blood Group | Available Stock) whose footer reads "n of n". The first column ("Sel") is a pinned row selector
 * ({@code selectButtonClick(row,$event)}). <b>Print</b> ({@code PrintReport()}) does not need a selected row: it opens
 * {@code /SQLReport/InventoryReport/frmcurrentbloodbagstockReport.aspx?locationid=&componentid=&bloodgroupid=} in a new tab.</p>
 *
 * <p>The grid rows are read from the grid's own data ({@code gridOptions.data}), then compared with what the table actually renders.</p>
 */
public class CurrentBloodBagStock extends BasePage {

    public CurrentBloodBagStock(Page page) { super(page); }

    private static final String SEARCH = "button[ng-click='fnfetchlist()']";
    private static final String PRINT = "button[ng-click='PrintReport()']";
    private static final String SEL = ".ui-grid-selection-row-header-buttons";

    public String lastMenu = "", lastRoute = "", lastSearch = "", lastData = "", lastSelect = "", lastProblems = "";
    public int rowCount = -1, tabsBeforePrint = 1;
    public String selectedText = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const gridOpts=()=>{ const el=document.querySelector('.ui-grid'); if(!el) return null; let s=angular.element(el).scope();"
          + "  for(let i=0;i<10&&s;i++){ const o=s.gridOptions; if(o && Array.isArray(o.data) && o.columnDefs) return o; s=s.$parent; } return null; };";

    // ---- navigation ------------------------------------------------------

    /** Ancillary Services &rarr; Blood Bank &rarr; Current BloodBag Stock (a typed hash bounces to the dashboard, so the menu link is the way in). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*ancillary\\s*services?\\s*$");
        expandMenuEntry("^\\s*ancillary\\s*services?\\s*$", "^\\s*blood\\s*bank\\s*$");
        waitForAngular(800);
        Object menu = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis).map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }");
        lastMenu = menu == null ? "" : menu.toString();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*ancillary\\s*services?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bb=[...topLi.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(bb&&bb.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*current\\s*blood\\s*bag\\s*stock\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__cbsMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__cbsMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("CurrentBloodBagStock.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__cbsMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the screen: the route AND its own Search button must be there (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        if (!page.url().toLowerCase().endsWith("#/currentbloodbagstock")) return false;
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + SEARCH + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- search + table --------------------------------------------------

    /** Click Search and wait for the grid to hold rows (or settle empty). Returns the number of rows the grid's data holds. */
    public int clickSearch() {
        page.locator(SEARCH).first().click();
        waitForAngular(1500);
        long end = System.currentTimeMillis() + 15000;
        int n = 0;
        while (System.currentTimeMillis() < end) {
            Object r = page.evaluate("() => {" + JS + " const o=gridOpts(); return o ? o.data.length : -1; }");
            n = r instanceof Number ? ((Number) r).intValue() : -1;
            if (n > 0) break;
            page.waitForTimeout(500);
        }
        rowCount = n;
        lastSearch = "Search clicked; the grid holds " + n + " row(s)";
        return n;
    }

    /** The column headings the table shows (the "Sel" selector column included). */
    public String headings() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(t=>t).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** The footer count the grid shows ("24 of 24"), or "". */
    public String footerCount() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s+of\\s+\\d+/); return m?m[0]:''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Judge the data in the table. Every row must carry a Location, Component and Blood Group and a whole-number Available Stock
     * that is not negative; no Location+Component+Blood Group may repeat; the footer count must equal the number of rows; and
     * every row the table renders must match the grid's own data (so nothing on screen is invented or shifted).
     *
     * @return empty when everything holds, else the problems found
     */
    public String verifyData() {
        Object r = page.evaluate("() => {" + JS
                + " const o=gridOpts(); if(!o) return {err:'no grid found'};"
                + " const d=o.data, probs=[], seen={}, nameSeen={}, sameName=[]; let total=0, withStock=0, zero=0;"
                + " d.forEach((x,i)=>{"
                + "   const loc=norm(x.locationname), com=norm(x.componentname), bg=norm(x.bloodgroupname), st=x.availabelstock;"
                + "   if(!loc) probs.push('row '+(i+1)+': Location Name empty');"
                + "   if(!com) probs.push('row '+(i+1)+': Component Name empty');"
                + "   if(!bg) probs.push('row '+(i+1)+': Blood Group empty');"
                + "   if(typeof st!=='number' || !isFinite(st) || st<0 || Math.floor(st)!==st) probs.push('row '+(i+1)+' ('+com+' / '+bg+'): Available Stock is \"'+st+'\"');"
                + "   else { total+=st; if(st>0) withStock++; else zero++; }"
                // a repeat is judged on the IDS: two components may share a NAME (master-data leftover) without the table repeating itself
                + "   const k=x.locationid+'|'+x.componentid+'|'+x.bloodgroupid; if(seen[k]) probs.push('duplicate row: '+com+' / '+bg+' (same component id and blood group id)'); seen[k]=1;"
                + "   const nk=com+'|'+bg; if(nameSeen[nk] && nameSeen[nk]!==x.componentid) sameName.push(com+' / '+bg+' (component ids '+nameSeen[nk]+' and '+x.componentid+')'); nameSeen[nk]=x.componentid; });"
                + " const foot=(document.body.innerText||'').match(/(\\d+)\\s+of\\s+(\\d+)/);"
                + " if(foot && (+foot[2])!==d.length) probs.push('footer says '+foot[0]+' but the grid holds '+d.length+' rows');"
                // what the table renders (the non-pinned canvas) against the data, row by row
                + " const rendered=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')].filter(vis)"
                + "   .map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent)));"
                + " let matched=0; const keyOf=x=>[norm(x.locationname),norm(x.componentname),norm(x.bloodgroupname),String(x.availabelstock)].join('|');"
                + " const keys=new Set(d.map(keyOf));"
                + " rendered.forEach((c,i)=>{ if(c.length<4) return; const k=c.slice(0,4).join('|'); if(keys.has(k)) matched++; else probs.push('table row '+(i+1)+' shows \"'+k+'\" which is not in the grid data'); });"
                + " return {n:d.length, probs:probs, total:total, withStock:withStock, zero:zero, rendered:rendered.length, matched:matched, foot:foot?foot[0]:'',"
                + "   sameName:sameName.join('; '), sample:d.slice(0,3).map(x=>norm(x.componentname)+' '+norm(x.bloodgroupname)+'='+x.availabelstock).join('; ')}; }");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        if (m == null || m.get("err") != null) { lastProblems = "the grid could not be read"; lastData = lastProblems; return lastProblems; }
        @SuppressWarnings("unchecked")
        java.util.List<Object> probs = (java.util.List<Object>) m.get("probs");
        lastData = m.get("n") + " row(s); footer \"" + m.get("foot") + "\"; " + m.get("withStock") + " with stock, " + m.get("zero")
                + " with zero stock, " + m.get("total") + " bag(s) in total; " + m.get("matched") + " of " + m.get("rendered")
                + " rendered table rows match the grid data. First rows: " + m.get("sample")
                + (String.valueOf(m.get("sameName")).isEmpty() ? "" : ". Note: two components share one name (different ids) - " + m.get("sameName"));
        if ((m.get("n") instanceof Number) && ((Number) m.get("n")).intValue() == 0) probs.add("the table has no rows");
        StringBuilder sb = new StringBuilder();
        for (Object p : probs) { if (sb.length() > 0) sb.append("; "); sb.append(p); if (sb.length() > 600) { sb.append(" ..."); break; } }
        lastProblems = sb.toString();
        return lastProblems;
    }

    // ---- select + print --------------------------------------------------

    /**
     * Tick a record: the one with the most stock (so there is something real in it). The selector is a pinned-column icon
     * ({@code selectButtonClick}); its row turns {@code ui-grid-row-selected}.
     */
    public boolean selectRecord() {
        Object idx = page.evaluate("() => {" + JS
                + " const o=gridOpts(); if(!o||!o.data.length) return -1; let best=0; o.data.forEach((x,i)=>{ if((x.availabelstock||0)>(o.data[best].availabelstock||0)) best=i; });"
                + " return best; }");
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) { lastSelect = "no row to select"; return false; }
        // ROW icons only: the header carries a select-all icon of the same class, which would tick every row
        Locator icons = page.locator(".ui-grid-pinned-container .ui-grid-canvas " + SEL);
        if (icons.count() == 0) icons = page.locator(".ui-grid-canvas " + SEL);
        if (i >= icons.count()) { lastSelect = "the row selector for row " + (i + 1) + " is not rendered (" + icons.count() + " selectors)"; return false; }
        try {
            icons.nth(i).scrollIntoViewIfNeeded();
            icons.nth(i).click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { lastSelect = "click failed - " + e.getMessage().split("\n")[0]; return false; }
        waitForAngular(700);
        Object r = page.evaluate("(n) => {" + JS
                + " const o=gridOpts(); const x=o.data[n];"
                + " const sel=[...document.querySelectorAll('.ui-grid-canvas " + SEL + "')].filter(e=>e.classList.contains('ui-grid-row-selected')).length;"
                + " return {sel:sel, text:norm(x.locationname)+' | '+norm(x.componentname)+' | '+norm(x.bloodgroupname)+' | stock '+x.availabelstock}; }", i);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        int sel = m.get("sel") instanceof Number ? ((Number) m.get("sel")).intValue() : 0;
        selectedText = String.valueOf(m.get("text"));
        lastSelect = "row " + (i + 1) + " ticked: " + selectedText + " (" + sel + " row(s) now marked selected)";
        return sel == 1;
    }

    /** Click Print and judge the report it opens in a new tab (then close that tab). */
    public PdfReport.Result clickPrintAndCapture() {
        tabsBeforePrint = page.context().pages().size();
        page.locator(PRINT).first().click();
        PdfReport.Result r = PdfReport.capture(page, tabsBeforePrint, 25000);
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        return r;
    }
}
