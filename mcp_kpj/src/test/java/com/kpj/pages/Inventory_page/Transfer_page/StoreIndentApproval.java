package com.kpj.pages.Inventory_page.Transfer_page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * Inventory &gt; Transfer &gt; <b>Store Indent Approval</b> — Page Object ({@code #/StoreIndentList2}, header "STORE INDENT APPROVAL").
 *
 * <p>The search, Indent List, Indent Details and Print behaviour is shared with the Store Indent list and lives in {@link StoreIndentList}. What is specific to this screen
 * (confirmed live in the built-in browser, devhis build v2026.11.02.365): the radios default to <b>Pending For Approval</b> ({@code StoreIndent.Criteria}=1), the screen lists the pending
 * indents at once, and <b>Approve</b> ({@code ApproveStoreIndent()}) approves the indents whose Approve checkbox is ticked OR, when none is, the highlighted row; on success the screen says
 * "Store Indent Approved Successfully" (a toastr success toast) and searches again. It CONSUMES the indent: an approved indent leaves the Pending list.</p>
 */
public class StoreIndentApproval extends StoreIndentList {

    public StoreIndentApproval(Page page) { super(page); }

    private static final String HREF = "#/storeindentlist2";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Transfer &gt; Store Indent Approval — each level opened only if shut, the leaf looked up by its exact route (the sibling "STORE INDENT" is {@code #/StoreIndentList}). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*inventory\\s*$");
            expandMenuEntry("^\\s*inventory\\s*$", "^\\s*transfer\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS + " const a=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__siaLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__siaLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("StoreIndentApproval.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__siaLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!e && e.options.length>1; }", STORE_NG,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("StoreIndentApproval.nav: the Requesting Store list is not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    /** On this screen — the route AND the screen's own header, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/storeindentlist2/?$")) return false;
        Object t = page.evaluate("() => { const h=document.querySelector('h1'); return h? h.textContent.replace(/\\s+/g,' ').trim() : ''; }");
        return t != null && t.toString().toLowerCase().contains("store indent approval");
    }

    // ---- Approve ---------------------------------------------------------------------------------------------

    /**
     * Click <b>Approve</b> (the selected indent is highlighted; none of the Approve checkboxes is ticked — the screen approves the highlighted row) and return the toast the screen answers
     * with ("" when none appeared). Waits for a success or failure message rather than the first thing on screen.
     */
    public String clickApprove() {
        problems.clear();
        lastToast = "";
        if (!clickByNg("ApproveStoreIndent", "Approve")) return "";
        String seen = "";
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            String t = toastsNow();
            if (!t.isEmpty()) {
                seen = t;
                String low = t.toLowerCase();
                if (low.contains("success") || low.contains("fail") || low.contains("error") || low.contains("select")) break;
            }
            page.waitForTimeout(250);
        }
        lastToast = seen;
        return seen;
    }

    /** After Approve the screen searches again: wait for the approved indent to leave the Pending list and say whether it did. */
    public boolean indentLeftPendingList(String indentNo) {
        // the search clears the table before it refills it, so "absent" only counts once the refill has had time to land and holds on two looks
        page.waitForTimeout(2500);
        waitForAngular(1000);
        for (int i = 0; i < 20; i++) {
            if (!listed(indentNo)) {
                page.waitForTimeout(700);
                if (!listed(indentNo)) return true;
            }
            page.waitForTimeout(500);
        }
        return false;
    }

    private boolean listed(String indentNo) {
        for (List<String> row : indentRows()) if (String.join(" ", row).contains(indentNo)) return true;
        return false;
    }
}
