package com.kpj.pages.OT_page;

import com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * OT &gt; <b>OT Store Indent Approval</b> — Page Object (list {@code #/OTStoreIndentList}; the Manual Indent form is the shared {@code #/add-StoreIndent}).
 *
 * <p>Confirmed live (devhis build v2026.11.17.477). The top-level menu is called <b>OT</b> (the menu data calls the module "Operation Theatre"); the entry sits directly under it, between OT Schedule / OT Details / ... and
 * the Cath Lab group. The screen is the SAME Store Indent controller as Ancillary Services &gt; CSSD &gt; Store Indent - same list (Indent List / Indent Details, Requesting / Issuing Store, Search, Export To Excel,
 * <b>Auto Indent</b>, <b>Manual Indent</b>, Print), same Manual Indent form (heading "OT Store Indent Approval") and the same Item Search dialog - so everything after the navigation is inherited from
 * {@link StoreIndent}: the two store selects ({@code StoreIndent.SearchStoreID} = Requesting Store, {@code StoreIndent.ToStore} = Issuing Store), Get Items, the dialog (its Store* starts on the Requesting Store; Search
 * lists that store's items, 100 a page), tick, OK, the grid (Unit Quantity, Indent Quantity, Minimum Indent Quantity), Remarks, Save, the toast, the Indent Number dialog and the report opened by the save.</p>
 *
 * <p>Saving is REAL: it creates a Store Indent.</p>
 */
public class OTStoreIndentApproval extends StoreIndent {

    public OTStoreIndentApproval(Page page) { super(page); }

    private static final String NJS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String TOP = "OT", MENU = "OT Store Indent Approval";

    private int topState() {
        Object r = page.evaluate("(t) => {" + NJS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", TOP);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop() {
        Object r = page.evaluate("(t) => {" + NJS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", TOP);
        return Boolean.TRUE.equals(r);
    }

    /** Tag the "OT Store Indent Approval" link INSIDE the OT menu entry. */
    private boolean tagLink() {
        Object r = page.evaluate("(a) => {" + NJS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const l=[...pli.querySelectorAll('a[href]')].filter(vis).find(x=>norm(x.textContent)===a[1]); if(!l) return false; l.id='__otLink'; return true; }", java.util.Arrays.asList(TOP, MENU));
        return Boolean.TRUE.equals(r);
    }

    private boolean tagLinkProbe() {
        boolean t = tagLink();
        page.evaluate("() => { const e=document.getElementById('__otLink'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagLinkProbe()) return true; page.waitForTimeout(250); }
        return tagLinkProbe();
    }

    private boolean manualIndentButtonVisible() {
        Object r = page.evaluate("() => {" + NJS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && (b.getAttribute('ng-click')||'').indexOf('AddStoreIndent')===0); }");
        return Boolean.TRUE.equals(r);
    }

    /** OT &gt; OT Store Indent Approval. The OT entry is opened ONCE and awaited (the menu builds its level lazily; a second click while it is still building closes it again). */
    @Override
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='OT')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagLinkProbe()) {
                if (topState() != 1) clickTop();
                waitLink(15000);
            }
            if (tagLink()) {
                try { page.locator("#__otLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("OTStoreIndentApproval.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__otLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !manualIndentButtonVisible(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    @Override
    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/otstoreindentlist/?$") && manualIndentButtonVisible(); }

    /** The Store* the Item Search dialog shows now (it starts on the Requesting Store and is left alone by this screen's flow). */
    public String dialogStoreShown() {
        Object r = page.evaluate("() => {" + NJS + " const e=document.querySelector(\"select[ng-model='ItemSearch.SearchStoreID']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The heading the Manual Indent form carries ("OT Store Indent Approval" here, "Store Indent" under CSSD). */
    public String formHeading() {
        Object r = page.evaluate("() => {" + NJS + " const h=[...document.querySelectorAll('h1,h2,h3,h4,h5,.panel-title,.box-title,.panel-heading')].filter(vis).map(e=>norm(e.textContent)).filter(Boolean); return h.length? h[0] : ''; }");
        return r == null ? "" : r.toString();
    }
}
