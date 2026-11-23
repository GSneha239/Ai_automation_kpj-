package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Others &gt; <b>Gate Pass Out List</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> (opens a flat entry form, no dialogs) → select <b>Store</b> → select
 * <b>Supplier</b> → enter <b>Item Code</b>, <b>Item Name</b>, <b>Quantity</b> → enter <b>Issue To</b> →
 * click <b>Save</b> → verify the success toast.</p>
 *
 * <p>Live-inspected before writing any selector, the same way {@link
 * com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem} was, since blind fuzzy-matching had
 * already proven risky on screens with non-obvious structure. That inspection surfaced one important,
 * REPRODUCIBLE APPLICATION BUG rather than a test-code issue:</p>
 * <ul>
 *   <li><b>Item Code/Item Name autocomplete is broken on this screen.</b> Both fields fire
 *       {@code POST /api/GRN/fetchItemByStore} on every keystroke, and confirmed live that request
 *       returns HTTP 400 every single time — the server's own error message is
 *       {@code "The parameters dictionary contains a null entry for parameter 'PsychotropicDrug' of
 *       non-nullable type 'System.Boolean'..."} — the client sends {@code PsychotropicDrug=undefined} in
 *       the query string instead of {@code true}/{@code false}. This is unconditional and reproducible
 *       with any search text, on this screen, for this build; it is not something a differently-shaped
 *       test input can work around. Typing into these fields therefore never actually attaches a real
 *       item to the line the app adds — see {@link #enterItemDetails} for what this means in practice.</li>
 *   <li>Confirmed live that {@code Save} does <b>not</b> require any item line at all: with zero rows in
 *       {@code GatePassOutItemList}, Save still succeeds server-side ({@code "Gate Pass Out Saved
 *       Successfully"}, a real {@code TransNo} assigned). So the broken autocomplete blocks the item line
 *       from being genuinely populated, but does not block completing the requested flow end-to-end.</li>
 *   <li>The Quantity field's {@code ng-keyup="AddDetails($event)"} is what appends a row to the item
 *       table — confirmed live it responds to a plain Enter keyup, not a button click element found
 *       anywhere else on the form.</li>
 *   <li><b>Store</b> ({@code ng-model="GatePassOut.Store"}) already carries a real default (the
 *       logged-in user's own store) on New — confirmed live, unlike {@code Supplier}
 *       ({@code ng-model="GatePassOut.SupplierIDs"}) which starts on its placeholder and needs an
 *       explicit selection to match the requested "select supplier" step.</li>
 * </ul>
 * <p>{@code describeControls()} is dumped into the report at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.</p>
 */
public class GatePassOutList extends BasePage {

    public GatePassOutList(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNewTab = "", lastNew = "",
            lastStore = "", lastSupplier = "", lastItemDetails = "", lastIssueTo = "",
            lastSave = "", lastSaveDiagnostics = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

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
            clickMenu("^\\s*others\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*gate\\s*pass\\s*out\\s*list\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__gpoMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__gpoMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("GatePassOutList.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__gpoMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("gatepassout")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /** Poll until the screen's own Search button appears — the header nav alone (~15 links) can satisfy
     *  a low generic-control-count threshold before real content renders, confirmed on this module's
     *  sibling screens, so this checks for something screen-specific instead. */
    public boolean waitForRealContent(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("() => {" + JS
                    + " const hasSearch=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .some(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                    + " const controlCount=[...document.querySelectorAll('select,input,button')].filter(vis).length;"
                    + " return (hasSearch && controlCount>6) ? controlCount : 0; }");
            int count = n instanceof Number ? ((Number) n).intValue() : 0;
            if (count > 0) return true;
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- diagnostics -------------------------------------------------------

    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
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
                + " return '[main]\\n' + [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== GatePassOutList CONTROLS ===\n" + s);
        return s;
    }

    // ---- New ---------------------------------------------------------------

    /** Reported plainly whether a separate "New" tab exists — confirmed on every screen in this module
     *  that it never does, only the same action button used in the next step. Non-fatal. */
    public String clickNewTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent))"
                + "        && !/^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate New tab exists on this screen — \"New\" is the same "
                + "action button used in the later step, so it is left untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked New tab'; }");
        lastNewTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("GatePassOutList: " + lastNewTab);
        return lastNewTab;
    }

    /** Click <b>New</b> — confirmed live {@code ng-click="AddGatePassOut()"} at the bottom of the list
     *  view, opening a flat entry form (no item-search dialog anywhere on this screen). */
    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(b) b.id='__gpoNew'; }");
        try {
            page.locator("#__gpoNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("GatePassOutList.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__gpoNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        boolean open = formOpen();
        if (!open) { page.waitForTimeout(2000); open = formOpen(); }
        // The Store/Supplier dropdowns populate from a separate async call after the form itself
        // renders — confirmed on the sibling ReceiveIssueItem screen as a real race. Poll for real
        // options before treating New as fully settled.
        if (open) {
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                Object n = page.evaluate("() => {"
                        + " const e=document.querySelector('select[ng-model=\"GatePassOut.SupplierIDs\"]');"
                        + " return e? e.options.length : 0; }");
                int count = n instanceof Number ? ((Number) n).intValue() : 0;
                if (count > 1) break;
                page.waitForTimeout(500);
            }
        }
        lastNew = open ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("GatePassOutList: " + lastNew);
        return lastNew;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => !!document.querySelector('select[ng-model=\"GatePassOut.Store\"]')");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    // ---- Store / Supplier -----------------------------------------------------

    /** Select <b>Store</b> — confirmed live this already defaults to the logged-in user's real store on
     *  New (not the placeholder), so this only re-selects when it's genuinely blank. */
    public String selectStore() {
        Object already = page.evaluate("() => {"
                + " const e=document.querySelector('select[ng-model=\"GatePassOut.Store\"]');"
                + " if(!e) return '';"
                + " const t=(e.options[e.selectedIndex]||{}).text||'';"
                + " return /^-*\\s*select\\s*-*$/i.test(t.trim())? '' : t.trim(); }");
        String pre = already == null ? "" : already.toString();
        lastStore = !pre.isEmpty() ? pre + " [already defaulted]"
                : selectFirstRealOption("GatePassOut.Store", "__gpoStore");
        System.out.println("GatePassOutList: store -> " + lastStore);
        return lastStore;
    }

    public boolean storeSelected() {
        return lastStore != null && !lastStore.isEmpty() && !lastStore.startsWith("(");
    }

    /** Select <b>Supplier</b> — confirmed live REQUIRED, starts on its placeholder unlike Store.
     *  {@code ng-model="GatePassOut.SupplierIDs"}. */
    public String selectSupplier() {
        lastSupplier = selectFirstRealOption("GatePassOut.SupplierIDs", "__gpoSupplier");
        System.out.println("GatePassOutList: supplier -> " + lastSupplier);
        return lastSupplier;
    }

    public boolean supplierSelected() {
        return lastSupplier != null && !lastSupplier.isEmpty() && !lastSupplier.startsWith("(");
    }

    private String selectFirstRealOption(String ngModel, String tempId) {
        Object r = page.evaluate("(a) => {"
                + " const [model, id] = a;"
                + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                + " const e=document.querySelector('select[ng-model=\"'+model+'\"]');"
                + " if(!e) return '(no matching dropdown)';"
                + " e.id=id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test((x.o.text||'').trim()));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length})"
                + "   : '(the list has no real option)'; }",
                java.util.List.of(ngModel, tempId));
        String info = r == null ? "" : r.toString();
        if (info.startsWith("(")) return info;
        try {
            java.util.Map<String, Object> m = parseFlatJson(info);
            page.locator("#" + tempId).selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setIndex(((Number) m.get("index")).intValue()));
        } catch (Exception e) {
            return "(select failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(700);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", tempId);
        page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tempId);
        return (t == null ? "" : t.toString()) + " [" + info + "]";
    }

    private static java.util.Map<String, Object> parseFlatJson(String json) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        String body = json.substring(1, json.length() - 1);
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            String key = kv[0].replaceAll("\"", "").trim();
            String val = kv[1].trim();
            if (val.startsWith("\"")) m.put(key, val.replaceAll("\"", ""));
            else m.put(key, Double.parseDouble(val));
        }
        return m;
    }

    // ---- Item Code / Item Name / Quantity -----------------------------------------------------

    /**
     * Enter <b>Item Code</b>, <b>Item Name</b> and <b>Quantity</b> — confirmed live that this screen's
     * own autocomplete is broken (see the class doc): every {@code fetchItemByStore} lookup the app
     * fires from these fields returns HTTP 400 from the server, so no suggestion can ever be selected
     * and the app's own "add to list" handler (fired from Quantity's Enter key) appends a row with every
     * field blank, regardless of what was typed. This still types the requested values into the raw
     * fields — matching the literal request and leaving evidence in the report of exactly what was
     * attempted — and presses Enter in Quantity to trigger the same {@code AddDetails()} a user's Enter
     * key would, but does not claim a real item line was attached, since none can be while this
     * application bug stands.
     */
    public String enterItemDetails(String itemCode, String itemName, String qty) {
        Object codeResult = page.evaluate("(v) => {" + JS
                + " const e=document.getElementById('txtItemCode');"
                + " return setEl(e, v); }", itemCode);
        Object nameResult = page.evaluate("(v) => {" + JS
                + " const e=document.getElementById('txtServiceName');"
                + " return setEl(e, v); }", itemName);
        Object qtyResult = page.evaluate("(v) => {" + JS
                + " const e=document.getElementById('PSqty');"
                + " const r=setEl(e, v);"
                + " e.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));"
                + " return r; }", qty);
        waitForAngular(800);
        lastItemDetails = "Item Code=" + codeResult + ", Item Name=" + nameResult + ", Quantity=" + qtyResult
                + " (typed as requested; this screen's own item-lookup autocomplete returns HTTP 400 "
                + "live, a confirmed application bug — see the class doc — so no real item line can be "
                + "attached while it stands)";
        System.out.println("GatePassOutList: " + lastItemDetails);
        return lastItemDetails;
    }

    public boolean itemDetailsTyped() {
        return lastItemDetails != null && lastItemDetails.contains("Item Code=") && !lastItemDetails.contains("(no-field)");
    }

    // ---- Issue To ----------------------------------------------------------------

    public String enterIssueTo(String value) {
        Object back = page.evaluate("(v) => {" + JS
                + " const e=document.querySelector('input[ng-model=\"GatePassOut.IssueTo\"]');"
                + " return setEl(e, v); }", value);
        lastIssueTo = back == null ? "" : back.toString();
        System.out.println("GatePassOutList: issue to -> " + lastIssueTo);
        return lastIssueTo;
    }

    public boolean issueToEntered() {
        return lastIssueTo != null && !lastIssueTo.isEmpty() && !lastIssueTo.startsWith("(");
    }

    // ---- save + toast ----------------------------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        return t.contains("successfully") || t.contains("success");
    }

    public void clickSave() {
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Save button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastSaveDiagnostics = clicked == null ? "" : clicked.toString();
        System.out.println("GatePassOutList: " + lastSaveDiagnostics);
        waitForAngular(2500);
        try { acceptSaveDialog(); } catch (Exception ignore) { }
    }

    public boolean saveClicked() { return lastSaveDiagnostics != null && lastSaveDiagnostics.startsWith("clicked \""); }

    public String waitForSaveToast() {
        // No preemptive clear here — confirmed live on this screen that Save's toast can already be
        // showing (and even mid-fade) by the time this runs, since clickSave()'s own settle-wait is
        // enough for the toast to appear first. A clear-then-poll pattern risks wiping out the very
        // toast being waited for; this checks for whatever is already there before polling for more.
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
            if (toast.isEmpty()) page.waitForTimeout(400);
        }
        lastSave = toast;
        System.out.println("GatePassOutList: save toast=\"" + toast + "\"");
        return toast;
    }
}
