package com.kpj.pages.Inventory_page.Transfer_page;

import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Transfer &gt; <b>Return Item</b> - the WRITING flow (Type Of Return, Return From / To Store, Get Issue, Search, pick an issue, tick the item, OK, Return Quantity, Remarks, Save).
 *
 * <p>It is the same controller and the same add form ({@code #/add-ReturnItem}, {@code ReturnItem.*} models, the Issue Item Search dialog, Save {@code IUDSave()}) as
 * <b>Ancillary Services &gt; CSSD &gt; Return Item</b>, so everything after the navigation is inherited from {@link com.kpj.pages.AncillaryServices_page.CSSD_page.ReturnItem}: only the menu route differs (Inventory's own list
 * {@code #/ReturnItemList}, reached through the read-only page object's sidebar walk Inventory &gt; Transfer &gt; Return Item). The read-only suite {@link ReturnItem} is left as it was.</p>
 *
 * <p>Saving is REAL: a Return Item is created and stock moves from the Return From store to the Return To store's side of the issue.</p>
 */
public class ReturnItemFlow extends com.kpj.pages.AncillaryServices_page.CSSD_page.ReturnItem {

    public ReturnItemFlow(Page page) { super(page); }

    /** Why the last menu walk failed ("" when it did not). */
    public String navigationProblem = "";

    private static final String NJS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private boolean newButtonVisible() {
        Object r = page.evaluate("() => {" + NJS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && /^\\s*new\\s*$/i.test(norm(b.textContent)) && /AddReturnItem/.test(b.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    /** Inventory &gt; Transfer &gt; Return Item (the sidebar walk of the read-only page object). */
    @Override
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        navigationProblem = new ReturnItem(page).navigateViaMenu();
        if (!navigationProblem.isEmpty()) System.out.println("Inventory ReturnItemFlow.nav: " + navigationProblem);
        for (int i = 0; i < 60 && !newButtonVisible(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    @Override
    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/returnitemlist/?$") && newButtonVisible(); }

    /**
     * The toasts shown since Save. After Save the app goes back to the list, so a read made while the page is changing can throw ("Execution context was destroyed"); this reads the toast elements of whatever document
     * is there and treats a failed read as "not yet" (the same safeguard as {@code ReceiveReturnItemFlow}).
     */
    @Override
    public String toasts() {
        try {
            Object r = page.evaluate("() => { const a=[...(window.__rtToasts||[])]; document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !a.includes(m)) a.push(m); }); return a.join(' | '); }");
            return r == null ? "" : r.toString();
        } catch (Exception navigating) { return ""; }
    }
}
