package com.kpj.pages.Inventory_page.Transfer_page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; <b>Receive Return Item</b> — the WRITING flow (select Received From / Received To, Search, click a return, type the Received Quantity, Save).
 *
 * <p>It is the same controller and the same add form ({@code #/add-ReceiveReturnItem}, {@code ReceiveReturnItem.*} models, grid {@code #grid1}, item table with {@code Itm.receivedquantity}, Save {@code IUDSave()}) as
 * <b>Ancillary Services &gt; CSSD &gt; Receive Return Item</b>, so everything after the navigation is inherited from {@link com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem}: only the menu route (Inventory's own
 * list {@code #/ReceiveReturnItemList}, reached through the existing read-only page object's menu walk) and the list's state differ. The read-only suite {@link ReceiveReturnItem} is left as it was.</p>
 *
 * <p>Saving is REAL: the return is received into the store and stops being listed as pending.</p>
 */
public class ReceiveReturnItemFlow extends com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem {

    public ReceiveReturnItemFlow(Page page) { super(page); }

    /** Why the last menu walk failed ("" when it did not). */
    public String navigationProblem = "";

    private static final String NJS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private boolean newButtonVisible() {
        Object r = page.evaluate("() => {" + NJS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && /^\\s*new\\s*$/i.test(norm(b.textContent)) && /AddReceiveReturnItem/.test(b.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    /** Inventory &gt; Receive Return Item (the sidebar walk of the read-only page object). */
    @Override
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        navigationProblem = new ReceiveReturnItem(page).open();
        if (!navigationProblem.isEmpty()) System.out.println("Inventory ReceiveReturnItemFlow.nav: " + navigationProblem);
        for (int i = 0; i < 60 && !newButtonVisible(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    @Override
    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/receivereturnitemlist/?$") && newButtonVisible(); }

    /**
     * The toasts shown since Save. On this screen Save sends the app back to the list with a page load, so the toast appears in the NEW document (the observer armed before Save is gone with the old one) and a
     * read made while the page is loading throws "Execution context was destroyed" - found on the first live run (2026-11-18), where the toast "Receive Return Item added successfully." was on screen but the test
     * died reading it. So this reads the toast elements of whatever document is there and treats a failed read as "not yet".
     */
    @Override
    public String toasts() {
        try {
            Object r = page.evaluate("() => { const a=[...(window.__rrToasts||[])]; document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !a.includes(m)) a.push(m); }); return a.join(' | '); }");
            return r == null ? "" : r.toString();
        } catch (Exception navigating) { return ""; }
    }

    /** The same read-only check as the parent's, retried while the page is still loading after Save. */
    @Override
    public int stillPending(String returnNumber, int fromStoreId, int toStoreId) {
        for (int i = 0; i < 5; i++) {
            try {
                waitForAngular(1500);
                int r = super.stillPending(returnNumber, fromStoreId, toStoreId);
                if (r >= 0) return r;
            } catch (Exception navigating) { /* the page is loading: try again */ }
            page.waitForTimeout(1500);
        }
        return -1;
    }

    /** Click <b>Search</b> on the form (the screen also searches by itself when Received To is chosen); returns how many returns are listed afterwards (waits for the search call). */
    public int clickSearch() {
        problems.clear();
        try {
            page.waitForResponse(x -> x.url().contains("/ReturnItem/Fetch") && String.valueOf(x.request().postData()).contains("GetReturndItemSearch"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("button").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*Search\\s*$"))).first().click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Search ran no search call: " + e.getMessage().split("\n")[0]); }
        waitForAngular(1200);
        for (int i = 0; i < 24 && transfers().isEmpty(); i++) page.waitForTimeout(250);
        return transfers().size();
    }
}
