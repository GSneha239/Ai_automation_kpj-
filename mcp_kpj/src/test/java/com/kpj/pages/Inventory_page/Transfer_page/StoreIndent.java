package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Transfer &gt; <b>Store Indent</b> — Page Object.
 *
 * <p>Flow: click <b>Manual Indent</b> → select <b>Issuing Store</b> → click <b>Get Items</b> (opens the
 * "Item Search" dialog) → click <b>Search</b> → tick a checkbox to select an item → click <b>OK</b> →
 * enter the <b>Unit Quantity</b> → enter <b>Remarks</b> → click <b>Save</b> → verify report generation
 * → verify the success toast → in the <b>Indent Number</b> confirmation popup, click <b>OK</b>.</p>
 *
 * <p>Sibling of {@link Transfer} in the {@code Inventory_page.Transfer_page} submodule, confirmed at
 * route {@code #/StoreIndentList}. Live-inspected before writing any selector — that inspection surfaced
 * one important, easy-to-get-backwards field pairing and a genuinely new validation layer this module
 * hadn't hit before:</p>
 * <ul>
 *   <li><b>The two store selects on the entry form are reversed from what their names suggest.</b>
 *       {@code ng-model="StoreIndent.SearchStoreID"} is actually labelled <b>"Requesting Store"</b> (and
 *       already carries a real default — the logged-in user's own store), while
 *       {@code ng-model="StoreIndent.ToStore"} is labelled <b>"Issuing Store"</b> (confirmed live it
 *       starts on its placeholder and needs an explicit selection) — confirmed live by reading each
 *       select's actual DOM label, not by trusting the ng-model name, the same lesson learned on {@code
 *       GatePassOutList}'s reversed "Transferred From"/"Received To" pair.</li>
 *   <li><b>Save on this screen is gated by a jQuery {@code validationEngine} check</b>
 *       ({@code $("#myStoreIndentForm").validationEngine('validate')}), confirmed live by reading
 *       {@code saveStoreIndent()}'s own source — a layer none of this module's other screens have. If
 *       that check fails the whole function is a silent no-op: no alert, no network call. Confirmed live
 *       that raw JS field-setting (this module's usual {@code setEl} pattern) left that check failing
 *       even though every Angular-bound value looked correct on inspection; genuine Playwright
 *       {@code fill()}/{@code click()} interactions are used for this screen's own fields for that
 *       reason, rather than the JS-atomic pattern used for dialog checkboxes elsewhere.</li>
 *   <li>The <b>Item Search</b> dialog's OK ({@code ng-click="OKClick()"}) filters
 *       {@code ItemSearchList} for {@code IsSelected} items and broadcasts them to the main form,
 *       confirmed live via its own source; a single genuine click closes it cleanly.</li>
 *   <li>After a successful Save, an <b>Indent Number</b> confirmation popup (class
 *       {@code .IndentNumberList}) appears with its own OK button that must be dismissed separately —
 *       confirmed live in the DOM (hidden until Save succeeds).</li>
 * </ul>
 * <p>{@code describeControls()} is dumped into the report at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.</p>
 */
public class StoreIndent extends BasePage {

    public StoreIndent(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastManualIndentTab = "",
            lastManualIndent = "", lastIssuingStore = "", lastGetItems = "", lastDialogSearch = "",
            lastItemTick = "", lastDialogOk = "", lastUnitQty = "", lastRemarks = "",
            lastSaveDiagnostics = "", lastSave = "", lastIndentNumberOk = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };";

    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*inventory\\s*$");
            clickMenu("^\\s*transfer\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*store\\s*indent\\s*$/i.test(norm(x.textContent))"
                    + "        && x.getAttribute('href') && x.getAttribute('href')!=='#');"
                    + " if(!a) return ''; a.id='__siMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__siMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("StoreIndent.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__siMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3500);
        }
        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1400);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("storeindentlist") && !route.contains("storeindentlist2")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /** Poll until the screen's own Manual Indent button appears — the header nav alone (~15 links) can
     *  satisfy a low generic-control-count threshold before real content renders, confirmed across this
     *  module's sibling screens, so this checks for something screen-specific instead. */
    public boolean waitForRealContent(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("() => {" + JS
                    + " const hasBtn=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .some(x=>/manual\\s*indent/i.test(norm(x.textContent)));"
                    + " const controlCount=[...document.querySelectorAll('select,input,button')].filter(vis).length;"
                    + " return (hasBtn && controlCount>4) ? controlCount : 0; }");
            int count = n instanceof Number ? ((Number) n).intValue() : 0;
            if (count > 0) return true;
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- diagnostics -------------------------------------------------------

    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal')].filter(vis).pop();"
                + " const root=d||document;"
                + " const out=[];"
                + " for(const e of root.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return (d? '[dialog]\\n' : '[main]\\n') + [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== StoreIndent CONTROLS ===\n" + s);
        return s;
    }

    // ---- Manual Indent ---------------------------------------------------------------

    /** Reported plainly whether a separate "Manual Indent" tab exists — confirmed across this module
     *  that it never does, only the same action button used in the next step. Non-fatal. */
    public String clickManualIndentTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/manual\\s*indent/i.test(norm(x.textContent))"
                + "        && !/^(fn)?add/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Manual Indent tab exists on this screen — \"Manual "
                + "Indent\" is the same action button used in the later step, so it is left untouched "
                + "here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Manual Indent tab'; }");
        lastManualIndentTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("StoreIndent: " + lastManualIndentTab);
        return lastManualIndentTab;
    }

    /** Click <b>Manual Indent</b> — confirmed live {@code ng-click="AddStoreIndent()"} on the list view. */
    public String clickManualIndent() {
        try {
            page.locator("button:has-text('Manual Indent')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastManualIndent = "clicked Manual Indent";
        } catch (Exception e) {
            lastManualIndent = "(Manual Indent click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(2000);
        boolean open = formOpen();
        if (!open) { page.waitForTimeout(2000); open = formOpen(); }
        lastManualIndent = open ? "Manual Indent clicked; the form is open" : "Manual Indent clicked; no form detected";
        System.out.println("StoreIndent: " + lastManualIndent);
        return lastManualIndent;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => !!document.querySelector('select[ng-model=\"StoreIndent.ToStore\"]')");
        return Boolean.TRUE.equals(r);
    }

    public boolean manualIndentClicked() { return lastManualIndent != null && lastManualIndent.contains("form is open"); }

    // ---- Issuing Store / Get Items --------------------------------------

    /** Select <b>Issuing Store</b> — confirmed live this is {@code ng-model="StoreIndent.ToStore"}
     *  (the store field named "ToStore" is the one visually labelled "Issuing Store"; the one named
     *  "SearchStoreID" is "Requesting Store" and already carries a real default). Uses Playwright's own
     *  {@code selectOption} rather than raw JS, matching this screen's other fields.
     *
     *  <p>Prefers an option whose text STARTS WITH "Pharmacy" over the alphabetically-first real option
     *  — confirmed live that picking the first real option ("Administration") left the Item Search
     *  dialog with zero results, since that store carries no stock, and confirmed live a SECOND time
     *  that a plain "contains" match picked "OPD - Fertility Pharmacy" (also zero stock) ahead of the
     *  real target since options are listed alphabetically. "Pharmacy (Central)" was confirmed live to
     *  have real, searchable stock, matching the same "Pharmacy Main Store" preference already proven
     *  across this module's other screens (e.g. {@code ItemSaleList#selectStore}).</p> */
    public String selectIssuingStore() {
        try {
            java.util.List<String> texts = page.locator("select[ng-model=\"StoreIndent.ToStore\"] option").allTextContents();
            int index = -1;
            int firstRealIndex = -1;
            for (int i = 1; i < texts.size(); i++) {
                String t = texts.get(i).trim();
                if (t.isEmpty() || t.matches("(?i)-*\\s*select\\s*-*")) continue;
                if (firstRealIndex < 0) firstRealIndex = i;
                if (t.toLowerCase().matches("pharmacy.*")) { index = i; break; }
            }
            if (index < 0) index = firstRealIndex;
            if (index < 0) { lastIssuingStore = "(the list has no real option)"; return lastIssuingStore; }
            page.locator("select[ng-model=\"StoreIndent.ToStore\"]").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
            waitForAngular(700);
            lastIssuingStore = texts.get(index).trim();
        } catch (Exception e) {
            lastIssuingStore = "(select failed: " + e.getMessage().split("\n")[0] + ")";
        }
        System.out.println("StoreIndent: issuing store -> " + lastIssuingStore);
        return lastIssuingStore;
    }

    public boolean issuingStoreSelected() {
        return lastIssuingStore != null && !lastIssuingStore.isEmpty() && !lastIssuingStore.startsWith("(");
    }

    public String clickGetItems() {
        try {
            page.locator("button:has-text('Get Items')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastGetItems = "clicked Get Items";
        } catch (Exception e) {
            lastGetItems = "(Get Items click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(1500);
        System.out.println("StoreIndent: " + lastGetItems);
        return lastGetItems;
    }

    public boolean dialogOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.modal')].some(vis); }");
        return Boolean.TRUE.equals(r);
    }

    // ---- Item Search dialog --------------------------------------

    public String clickSearchInDialog() {
        try {
            page.locator(".modal button:has-text('Search')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastDialogSearch = "clicked Search";
        } catch (Exception e) {
            lastDialogSearch = "(Search click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        // Confirmed live this dialog's own search (up to 100 rows) can take noticeably longer to render
        // than the fixed wait used elsewhere in this module — poll instead of a single fixed wait.
        waitForAngular(1500);
        long deadline = System.currentTimeMillis() + 8000;
        int count = dialogItemRowCount();
        while (count == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(500);
            count = dialogItemRowCount();
        }
        lastDialogSearch += " -> " + count + " row(s)";
        System.out.println("StoreIndent: " + lastDialogSearch);
        return lastDialogSearch;
    }

    public int dialogItemRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal')].filter(vis).pop(); if(!d) return 0;"
                + " return d.querySelectorAll('input[ng-model=\"Item.IsSelected\"]').length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean dialogItemRowsFound() { return dialogItemRowCount() > 0; }

    /** Tick the first item's checkbox in the dialog — the atomic JS find+click+Angular-sync pattern
     *  proven across this module for dialog checkboxes (confirmed live this part works fine; it was
     *  repeated OK clicks without waiting that caused trouble during manual live inspection, not the
     *  tick itself). */
    public String tickFirstItemInDialog() {
        Object r = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal')].filter(vis).pop(); if(!d) return '(no dialog)';"
                + " const cb=[...d.querySelectorAll('input[ng-model=\"Item.IsSelected\"]')].filter(vis)[0];"
                + " if(!cb) return '(no item checkbox)';"
                + " cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel'); if(c){ c.$setViewValue(true); c.$render(); } }catch(e){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return cb.checked? 'ticked the first item' : '(tick did not register)'; }");
        lastItemTick = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("StoreIndent: " + lastItemTick);
        return lastItemTick;
    }

    public boolean itemTicked() { return "ticked the first item".equals(lastItemTick); }

    public String clickOkInDialog() {
        try {
            page.locator(".modal button:has-text('OK')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastDialogOk = "clicked OK";
        } catch (Exception e) {
            lastDialogOk = "(OK click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(2000);
        System.out.println("StoreIndent: " + lastDialogOk);
        return lastDialogOk;
    }

    public boolean dialogOkClicked() { return "clicked OK".equals(lastDialogOk); }

    public boolean dialogClosed() { return !dialogOpen(); }

    // ---- Unit Quantity / Remarks -----------------------------------------------------

    /** Enter the <b>Unit Quantity</b> for the item row on the main form
     *  ({@code ng-model="Itm.unitqty"}). Uses Playwright's real {@code fill()} — confirmed live this
     *  screen's Save is gated by a jQuery {@code validationEngine} check that raw JS field-setting left
     *  failing even though the underlying Angular-bound value looked correct. */
    public String enterUnitQuantity(String qty) {
        try {
            page.locator("input[ng-model=\"Itm.unitqty\"]").first()
                    .fill(qty, new com.microsoft.playwright.Locator.FillOptions().setTimeout(6000));
            lastUnitQty = page.locator("input[ng-model=\"Itm.unitqty\"]").first().inputValue();
        } catch (Exception e) {
            lastUnitQty = "(fill failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(500);
        System.out.println("StoreIndent: unit quantity -> " + lastUnitQty);
        return lastUnitQty;
    }

    public boolean unitQuantityEntered(String expected) {
        return lastUnitQty != null && lastUnitQty.equals(expected);
    }

    public String enterRemarks(String value) {
        try {
            page.locator("textarea[ng-model=\"StoreIndent.Remarks\"]")
                    .fill(value, new com.microsoft.playwright.Locator.FillOptions().setTimeout(6000));
            lastRemarks = page.locator("textarea[ng-model=\"StoreIndent.Remarks\"]").inputValue();
        } catch (Exception e) {
            lastRemarks = "(fill failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(300);
        System.out.println("StoreIndent: remarks -> " + lastRemarks);
        return lastRemarks;
    }

    public boolean remarksEntered(String expected) {
        return lastRemarks != null && lastRemarks.equals(expected);
    }

    // ---- save + toast + Indent Number popup ----------------------------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        return t.contains("successfully") || t.contains("success");
    }

    /** Click <b>Save</b> and return how many browser tabs existed just before the click — needed by
     *  {@link com.kpj.pages.PdfReport#capture} to find the tab it opens (if this screen generates one on
     *  Save). Uses a genuine Playwright click for the same jQuery-validation reason as the fields above. */
    public int clickSave() {
        int pagesBefore = page.context().pages().size();
        try {
            page.locator("#SaveButton").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastSaveDiagnostics = "clicked Save";
        } catch (Exception e) {
            lastSaveDiagnostics = "(Save click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        // Kept short deliberately — confirmed live the success toast here is short-lived (possibly
        // cleared once the Indent Number popup opens) and a longer settle-wait here left
        // waitForSaveToast() starting its own poll too late to ever see it.
        waitForAngular(300);
        try { acceptSaveDialog(); } catch (Exception ignore) { }
        System.out.println("StoreIndent: " + lastSaveDiagnostics);
        return pagesBefore;
    }

    public boolean saveClicked() { return "clicked Save".equals(lastSaveDiagnostics); }

    public String waitForSaveToast() {
        String toast = "";
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try {
                Object now = page.evaluate("() => {" + JS + TOAST_ELS
                        + " return toastEls().map(x=>norm(x.textContent)); }");
                if (now instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) now) {
                        String s = o.toString();
                        if (!s.isEmpty()) { toast = s; break; }
                    }
                }
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(150);
        }
        lastSave = toast;
        System.out.println("StoreIndent: save toast=\"" + toast + "\"");
        return toast;
    }

    /** Wait for the <b>Indent Number</b> confirmation popup (class {@code .IndentNumberList}, confirmed
     *  live in the DOM, hidden until Save succeeds) and click its OK button. Non-fatal to call if it
     *  never appears — reported plainly either way. */
    public String clickIndentNumberOk() {
        long deadline = System.currentTimeMillis() + 10000;
        boolean appeared = false;
        while (System.currentTimeMillis() < deadline) {
            Object r = page.evaluate("() => {" + JS
                    + " const m=document.querySelector('.IndentNumberList');"
                    + " return m? vis(m) : false; }");
            if (Boolean.TRUE.equals(r)) { appeared = true; break; }
            page.waitForTimeout(400);
        }
        if (!appeared) {
            lastIndentNumberOk = "(the Indent Number popup never appeared)";
            System.out.println("StoreIndent: " + lastIndentNumberOk);
            return lastIndentNumberOk;
        }
        try {
            page.locator(".IndentNumberList button:has-text('OK')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastIndentNumberOk = "clicked OK on the Indent Number popup";
        } catch (Exception e) {
            lastIndentNumberOk = "(Indent Number OK click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(1000);
        System.out.println("StoreIndent: " + lastIndentNumberOk);
        return lastIndentNumberOk;
    }

    public boolean indentNumberOkClicked() {
        return "clicked OK on the Indent Number popup".equals(lastIndentNumberOk);
    }

    // ==================== [Auto Indent] tab ====================
    // A second tab on the same Store Indent list view. Confirmed live this is a genuinely SEPARATE
    // route (#/AutoIndent) with its own controller, not a dialog layered over the Manual Indent form —
    // "Auto Indent" (ng-click="openautoindent()") on the list view navigates there directly. Confirmed
    // live findings specific to this tab:
    //   - Unlike Manual Indent's reversed pair, THIS screen's two store selects match their labels:
    //     AutoIndent.FromStoreID really is "Requesting Store" and AutoIndent.ToStoreID really is
    //     "Issuing Store" — both start blank and must be selected explicitly (no default here).
    //   - The results grid is a PLAIN HTML table (not a ui-grid), so DOM row counting works directly,
    //     unlike the ui-grid screens elsewhere in this module.
    //   - Save Indent (ng-click="IUDAutoIndent()") is gated by the SAME jQuery validationEngine pattern
    //     as Manual Indent's Save (confirmed live by reading its source: $("#PurchaseOrder").
    //     validationEngine('validate')) — genuine Playwright fill()/click() are used here for the same
    //     reason.
    //   - Confirmed live via that same source: the success message goes through JAlert(...,'success'),
    //     which is itself only a thin wrapper around toastr.success(...) — the exact same toast
    //     mechanism {@link #waitForSaveToast} already reads, so it is reused as-is for this tab.
    //   - Confirmed live via that same source: on success it calls window.open(...frmIndentPrintReport
    //     .aspx...) directly — the same {@link com.kpj.pages.PdfReport} pattern applies. This screen has
    //     no Indent Number confirmation popup (that belongs only to the Manual Indent form).

    public String lastAutoIndentTab = "", lastAutoIndent = "", lastRequestingStore = "",
            lastAutoIssuingStore = "", lastAutoIndentSearch = "", lastAutoIndentItemTick = "",
            lastAutoIndentRemarks = "", lastSaveIndentDiagnostics = "";

    /** Reported plainly whether a separate "Auto Indent" tab exists — confirmed across this module that
     *  it never does, only the same action button used in the next step. Non-fatal. */
    public String clickAutoIndentTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/auto\\s*indent/i.test(norm(x.textContent))"
                + "        && !/^(fn)?open/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Auto Indent tab exists on this screen — \"Auto Indent\" "
                + "is the same action button used in the later step, so it is left untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Auto Indent tab'; }");
        lastAutoIndentTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("StoreIndent: " + lastAutoIndentTab);
        return lastAutoIndentTab;
    }

    /** Click <b>Auto Indent</b> — confirmed live {@code ng-click="openautoindent()"} on the list view,
     *  navigating to the separate {@code #/AutoIndent} route. */
    public String clickAutoIndent() {
        try {
            page.locator("button:has-text('Auto Indent')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastAutoIndent = "clicked Auto Indent";
        } catch (Exception e) {
            lastAutoIndent = "(Auto Indent click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(2000);
        boolean open = autoIndentFormOpen();
        if (!open) { page.waitForTimeout(2000); open = autoIndentFormOpen(); }
        lastAutoIndent = open ? "Auto Indent clicked; the form is open" : "Auto Indent clicked; no form detected";
        System.out.println("StoreIndent: " + lastAutoIndent);
        return lastAutoIndent;
    }

    public boolean autoIndentFormOpen() {
        Object r = page.evaluate("() => !!document.querySelector('select[ng-model=\"AutoIndent.ToStoreID\"]')");
        return Boolean.TRUE.equals(r);
    }

    public boolean autoIndentClicked() { return lastAutoIndent != null && lastAutoIndent.contains("form is open"); }

    /** Select <b>Requesting Store</b> and <b>Issuing Store</b> — confirmed live both are real labels on
     *  this screen (not reversed like Manual Indent's pair) and both start blank. Prefers an option
     *  whose text starts with "Pharmacy" for each, same reasoning as {@link #selectIssuingStore}, picking
     *  two DIFFERENT such stores since the app rejects a Requesting/Issuing pair that match. */
    public String selectRequestingAndIssuingStore() {
        lastRequestingStore = selectAutoIndentStore("AutoIndent.FromStoreID", null);
        lastAutoIssuingStore = selectAutoIndentStore("AutoIndent.ToStoreID", lastRequestingStore);
        String combined = "Requesting=" + lastRequestingStore + ", Issuing=" + lastAutoIssuingStore;
        System.out.println("StoreIndent: " + combined);
        return combined;
    }

    private String selectAutoIndentStore(String ngModel, String avoidText) {
        try {
            java.util.List<String> texts = page.locator("select[ng-model=\"" + ngModel + "\"] option").allTextContents();
            int index = -1;
            int firstRealIndex = -1;
            for (int i = 1; i < texts.size(); i++) {
                String t = texts.get(i).trim();
                if (t.isEmpty() || t.matches("(?i)-*\\s*select\\s*-*")) continue;
                if (avoidText != null && t.equals(avoidText)) continue;
                if (firstRealIndex < 0) firstRealIndex = i;
                if (t.toLowerCase().matches("pharmacy.*")) { index = i; break; }
            }
            if (index < 0) index = firstRealIndex;
            if (index < 0) return "(the list has no real option)";
            page.locator("select[ng-model=\"" + ngModel + "\"]").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
            waitForAngular(700);
            return texts.get(index).trim();
        } catch (Exception e) {
            return "(select failed: " + e.getMessage().split("\n")[0] + ")";
        }
    }

    public boolean requestingAndIssuingStoreSelected() {
        return lastRequestingStore != null && !lastRequestingStore.isEmpty() && !lastRequestingStore.startsWith("(")
                && lastAutoIssuingStore != null && !lastAutoIssuingStore.isEmpty() && !lastAutoIssuingStore.startsWith("(");
    }

    public String clickAutoIndentSearch() {
        try {
            page.locator("button:has-text('Search')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastAutoIndentSearch = "clicked Search";
        } catch (Exception e) {
            lastAutoIndentSearch = "(Search click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(1500);
        long deadline = System.currentTimeMillis() + 8000;
        int count = autoIndentRowCount();
        while (count == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(500);
            count = autoIndentRowCount();
        }
        lastAutoIndentSearch += " -> " + count + " row(s)";
        System.out.println("StoreIndent: " + lastAutoIndentSearch);
        return lastAutoIndentSearch;
    }

    /** Row count read directly from the DOM — confirmed live this results grid is a plain HTML table
     *  (not a {@code ui-grid}), so unlike the ui-grid screens elsewhere in this module, DOM counting is
     *  reliable here. */
    public int autoIndentRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const rows=[...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(vis);"
                + " return rows.length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean autoIndentRowsFound() { return autoIndentRowCount() > 0; }

    /** Click tick on the first row's checkbox — confirmed live {@code ng-model="Itm.isselected"}, the
     *  atomic JS find+click+Angular-sync pattern proven across this module for checkboxes. */
    public String tickFirstAutoIndentItem() {
        Object r = page.evaluate("() => {" + JS
                + " const cb=[...document.querySelectorAll('input[ng-model=\"Itm.isselected\"]')].filter(vis)[0];"
                + " if(!cb) return '(no item checkbox)';"
                + " cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel'); if(c){ c.$setViewValue(true); c.$render(); } }catch(e){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return cb.checked? 'ticked the first item' : '(tick did not register)'; }");
        lastAutoIndentItemTick = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("StoreIndent: " + lastAutoIndentItemTick);
        return lastAutoIndentItemTick;
    }

    public boolean autoIndentItemTicked() { return "ticked the first item".equals(lastAutoIndentItemTick); }

    public String enterAutoIndentRemarks(String value) {
        try {
            page.locator("textarea[ng-model=\"AutoIndent.Remarks\"]")
                    .fill(value, new com.microsoft.playwright.Locator.FillOptions().setTimeout(6000));
            lastAutoIndentRemarks = page.locator("textarea[ng-model=\"AutoIndent.Remarks\"]").inputValue();
        } catch (Exception e) {
            lastAutoIndentRemarks = "(fill failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(300);
        System.out.println("StoreIndent: auto indent remarks -> " + lastAutoIndentRemarks);
        return lastAutoIndentRemarks;
    }

    public boolean autoIndentRemarksEntered(String expected) {
        return lastAutoIndentRemarks != null && lastAutoIndentRemarks.equals(expected);
    }

    /** Click <b>Save Indent</b> and return how many browser tabs existed just before the click — needed
     *  by {@link com.kpj.pages.PdfReport#capture}. Uses a genuine Playwright click for the same
     *  jQuery-validation reason as Manual Indent's Save, and the same short settle-wait so
     *  {@link #waitForSaveToast} still catches the toast before it fades. */
    public int clickSaveIndent() {
        int pagesBefore = page.context().pages().size();
        try {
            page.locator("button:has-text('Save Indent')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastSaveIndentDiagnostics = "clicked Save Indent";
        } catch (Exception e) {
            lastSaveIndentDiagnostics = "(Save Indent click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(300);
        try { acceptSaveDialog(); } catch (Exception ignore) { }
        System.out.println("StoreIndent: " + lastSaveIndentDiagnostics);
        return pagesBefore;
    }

    public boolean saveIndentClicked() { return "clicked Save Indent".equals(lastSaveIndentDiagnostics); }
}
