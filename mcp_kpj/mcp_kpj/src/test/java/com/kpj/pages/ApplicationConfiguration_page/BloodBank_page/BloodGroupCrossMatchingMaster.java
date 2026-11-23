package com.kpj.pages.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Group Cross Matching Master</b> (route {@code #/BloodGroupCrossMatchingMaster}) — Page Object.
 *
 * <p>The screen lists the donor / recipient blood-group combinations in a grid (Donor, Applicability, Recipient) with a
 * <b>Save</b> button ({@code fnIUDBloodGroupCrossMatching()}). It is under <b>Blood Bank</b> in the menu.</p>
 */
public class BloodGroupCrossMatchingMaster extends BasePage {

    public BloodGroupCrossMatchingMaster(Page page) { super(page); }

    public String routeSeen = "";
    public byte[] tablePng;

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        expandMenuEntry(null, "^application\\s*configuration$");
        expandMenuEntry("^application\\s*configuration$", "^blood\\s*bank$");
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); const scope=(top&&top.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*blood\\s*group\\s*cross\\s*matching(\\s*master)?\\s*$/i.test(norm(x.textContent))); if(!a) return '';"
                + " a.id='__bgcmMenu'; return a.getAttribute('href')||'link'; }");
        routeSeen = r == null ? "" : r.toString();
        if (!routeSeen.isEmpty()) {
            try { page.locator("#__bgcmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BloodGroupCrossMatchingMaster.nav: click failed - " + e.getMessage().split("\n")[0]); }
            waitForAngular(2000);
        }
        // wait for the grid to be filled
        try {
            page.waitForFunction("() => document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row').length>0", null,
                    new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("BloodGroupCrossMatchingMaster.nav: the table showed no rows"); }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("bloodgroupcrossmatching"); }

    // ---- table -------------------------------------------------------------

    /** Header texts of the table (sort counters / blanks left out). */
    public List<String> headers() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('.ui-grid-header-cell')].filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t); }");
        return strings(r);
    }

    /** Rows currently drawn in the table body, each as its cell texts; a checkbox / toggle cell shows as "[x]" / "[ ]". */
    public List<List<String>> rows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(x=>x.offsetParent!==null)"
                + "   .map(row=>[...row.querySelectorAll('.ui-grid-cell')].map(c=>{ const cb=c.querySelector('input[type=checkbox],input[type=radio]');"
                + "     const t=norm(c.textContent); return cb ? (t?t+' ':'')+(cb.checked?'[x]':'[ ]') : t; })); }");
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) out.add(strings(row));
        return out;
    }

    /** Total items the grid holds (its data length), -1 when unreadable. */
    public int totalItems() {
        Object r = page.evaluate("() => { try { const el=document.querySelector('.ui-grid, [ui-grid]'); if(!el) return -1;"
                + " const sc=angular.element(el).isolateScope()||angular.element(el).scope(); const g=sc.grid; if(g&&g.options&&g.options.data) return g.options.data.length;"
                + " const o=sc.gridOptions||(sc.$parent&&sc.$parent.gridOptions); if(o&&o.data) return o.data.length; return -1; } catch(e){ return -1; } }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Expand the first donor group (click its row's expander, else the row itself); true when more rows appeared. */
    public boolean expandFirstGroup() {
        int before = rows().size();
        // The expander sits in the row-header container beside the body, not inside the body row: look in the whole grid.
        Object tagged = page.evaluate("() => { const vis=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " let t=[...document.querySelectorAll('.ui-grid-tree-base-row-header-buttons,.ui-grid-icon-plus-squared')].find(vis);"
                + " if(!t){ const row=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].find(vis); if(!row) return false; t=row.querySelector('.ui-grid-cell'); }"
                + " console.log('BGCM expander: '+t.outerHTML.slice(0,300)); t.id='__bgcmExp'; return t.outerHTML.slice(0,300); }");
        System.out.println("BloodGroupCrossMatchingMaster.expandFirstGroup target => " + tagged);
        if (tagged == null || Boolean.FALSE.equals(tagged)) return false;
        try { page.locator("#__bgcmExp").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("BloodGroupCrossMatchingMaster.expandFirstGroup: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__bgcmExp'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        return rows().size() != before || rows().stream().anyMatch(r -> r.size() > 2 && !r.get(r.size() - 1).isEmpty());
    }

    /** Take the screenshot of the table as it is shown. */
    public void shootTable() {
        try { tablePng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); } catch (Exception e) { tablePng = null; }
    }

    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }
}
