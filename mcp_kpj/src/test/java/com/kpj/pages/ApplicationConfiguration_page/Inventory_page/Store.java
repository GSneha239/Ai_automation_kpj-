package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>Store</b> — Page Object.
 *
 * <p>Flow: enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class Store extends BasePage {

    public Store(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "", lastAdd = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model tail, model-aware setter. */
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

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Inventory &rarr; Store.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*inventory\\s*$");
        lastMenu = describeMenu();

        // Anchored to the WHOLE label, and hidden anchors are included on purpose: these menu links sit in
        // the DOM before the submenu shows them. A loose match would take the first entry that merely
        // contains the words — the scaffolding trap that once ran an entire Compliance flow against
        // Complaint Type and reported PASS against the wrong screen's fields.
        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*store\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__stMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__stMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Store.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__stMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3000);
            if (!onScreen()) {
                try { page.navigate(baseUrl + hash); } catch (Exception ignore) { }
                waitForAngular(3500);
            }
        }
        // The route resolving is not the screen rendering. GENMST loads its view after the hash changes,
        // and until it does the DASHBOARD's controls are still in the DOM — a run that starts typing
        // straight away puts its values into dashboard widgets (LocationName took the remark once).
        waitForScreenControls(15000);

        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    /** Wait for this screen's OWN controls — a Code box, or its grid — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const tail=e=>((e.getAttribute('ng-model')||'').split('.').pop()||'');"
                    + " const code=[...document.querySelectorAll('input,textarea')].filter(vis)"
                    + "   .some(e=>/code$/i.test(tail(e)));"
                    + " const grid=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .some(vis);"
                    + " return code || grid; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("Store.waitForScreenControls: no Code box or grid within "
                    + timeoutMs + "ms — the screen may not have rendered");
            return false;
        }
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1200);
    }

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the Store screen — matched on the hash route, never on page text.
     *
     * <p>Routes in this section bear no resemblance to their labels — the sibling "Generic" is served
     * from {@code #/GENMST} — so the route the MENU gives is authoritative and a label-derived route is
     * never assumed. Demanding that the route contain the label is what rejected the page the menu had
     * just opened on that screen.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        if (!menuRoute.isEmpty() && route.contains(menuRoute)) return true;
        return route.contains("store");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== Store CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> if the entry form is not already on screen.
     *
     * <p>The requested steps do not mention Add, so this reports which of the two the screen did rather
     * than assuming either.</p>
     */
    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?store|AddStore/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__stAdd'; }");
        try {
            page.locator("#__stAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("Store.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__stAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastAdd = formOpen() ? "The form needed Add; it is now open." : "Add did not open a form.";
        return lastAdd;
    }

    public String lastLocation = "", lastFloor = "", lastPoLevel = "", lastHod = "";
    public String lastTextFields = "", lastTxnTick = "", lastProfit = "", lastAddRow = "";

    /**
     * Every field on this screen, pinned to the model dumped from the live form on 2026-08-17.
     *
     * <p>Nothing here is matched by keyword. The form carries near-identical names — {@code code} beside
     * {@code cosledger}/{@code grnledger}, {@code description} beside {@code storeaddress}, and
     * {@code StoreDetails.issue} (a checkbox) beside {@code issue_transfer} (radios) — so a keyword match
     * would fill the wrong box and still look like a pass.</p>
     */
    private static final String M = "StoreDetails.";

    /** Select a dropdown by its EXACT ng-model, reading the choice back off the control. */
    private String pickExact(String model, String id) {
        Object info = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+a.m+\"']\")].filter(vis)[0];"
                + " if(!e) return null; e.id=a.id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, count:reals.length}; }",
                java.util.Map.of("m", model, "id", id));
        if (info == null) return "(no dropdown " + model + ")";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) return "(no-option) [" + model + "]";
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return (t == null ? "" : t.toString()) + " [" + model + ", " + m.get("count") + " options]";
    }

    /** Wait for a dropdown to hold a real option — these lists load asynchronously. */
    private void waitForOptions(String model, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("(m) => {" + JS
                    + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\s*select/i.test(norm(o.text))).length : 0; }", model);
            if (n instanceof Number && ((Number) n).intValue() > 0) return;
            page.waitForTimeout(300);
        }
    }

    public String selectLocation() {
        waitForOptions(M + "locationid", 12000);
        lastLocation = pickExact(M + "locationid", "__stLoc");
        System.out.println("Store: Location = " + lastLocation);
        return lastLocation;
    }

    public String selectFloor() {
        waitForOptions(M + "floorid", 12000);
        lastFloor = pickExact(M + "floorid", "__stFloor");
        System.out.println("Store: Floor = " + lastFloor);
        return lastFloor;
    }

    public String selectPoApprovalLevel() {
        waitForOptions(M + "poapprovelevel", 12000);
        lastPoLevel = pickExact(M + "poapprovelevel", "__stPo");
        System.out.println("Store: PO Approval Level = " + lastPoLevel);
        return lastPoLevel;
    }

    public String selectHod() {
        waitForOptions(M + "hodid", 15000);
        lastHod = pickExact(M + "hodid", "__stHod");
        System.out.println("Store: HOD = " + lastHod);
        return lastHod;
    }

    /** A value that is really set — not a placeholder, not a lookup failure. */
    public static boolean chosen(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(")
                && !v.matches("^-*\s*[Ss]elect.*");
    }

    /**
     * Enter the seven text fields, each written to its OWN model and read back.
     *
     * <p>Reported field by field so a value that lands in a neighbour is visible in the report rather
     * than hidden behind one blanket "details entered" pass.</p>
     */
    public String enterTextFields(String code, String remark, String address, String contact,
                                  String cosLedger, String grnLedger, String dlNo, String taxNo,
                                  String personName) {
        Object r = page.evaluate("(a) => {" + JS
                + " const put=(model,v,name)=>{"
                + "   const e=[...document.querySelectorAll(\"[ng-model='\"+model+\"']\")].filter(vis)"
                + "     .filter(x=>x.tagName==='INPUT'||x.tagName==='TEXTAREA')[0];"
                + "   return name+'='+(e? setEl(e,v) : '(no field '+model+')'); };"
                + " return [put(a.M+'code', a.code, 'Code'),"
                + "         put(a.M+'description', a.remark, 'Remark'),"
                + "         put(a.M+'storeaddress', a.address, 'StoreAddress'),"
                + "         put(a.M+'storecontactnumber', a.contact, 'StoreContactNo'),"
                + "         put(a.M+'cosledger', a.cos, 'COSLedger'),"
                + "         put(a.M+'grnledger', a.grn, 'GRNLedger'),"
                + "         put(a.M+'dlNo', a.dl, 'DrugLicenceNo'),"
                + "         put(a.M+'TaxNo', a.tax, 'TaxNo'),"
                + "         put(a.M+'ContactPName', a.person, 'PersonName')].join(' | '); }",
                java.util.Map.of("M", M, "code", code, "remark", remark, "address", address,
                        "contact", contact, "cos", cosLedger, "grn", grnLedger, "dl", dlNo,
                        "tax", taxNo, "person", personName));
        lastTextFields = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("Store: " + lastTextFields);
        return lastTextFields;
    }

    /** Every named field read its own value back. */
    public boolean textFieldsEntered(String... namesAndValues) {
        if (lastTextFields == null || lastTextFields.contains("(no field")) return false;
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (!lastTextFields.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) return false;
        }
        return true;
    }

    /** Which named fields did NOT take their value. */
    public String textFieldsMissing(String... namesAndValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (lastTextFields == null
                    || !lastTextFields.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) {
                sb.append(sb.length() == 0 ? "" : ", ").append(namesAndValues[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Tick a checkbox in <b>Transaction Details</b>.
     *
     * <p>Pinned to {@code StoreDetails.openingbalance} — the first transaction option — and read back.
     * The screen also has {@code StoreDetails.issue} (a checkbox) and {@code issue_transfer} (radios)
     * both labelled "Transfer", so a label match here would be ambiguous.</p>
     */
    public String tickTransactionDetail() {
        Object r = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"input[type=checkbox][ng-model='\"+m+\"']\")]"
                + "   .filter(vis)[0];"
                + " if(!e) return '(no transaction checkbox '+m+')';"
                + " const before=e.checked; if(!e.checked) e.click();"
                + " return (e.checked? 'ticked' : 'NOT ticked')+' ['+m+'] '+before+' -> '+e.checked"
                + "   +' | label: '+labelOf(e).slice(0,40); }", M + "openingbalance");
        lastTxnTick = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("Store: transaction detail -> " + lastTxnTick);
        return lastTxnTick;
    }

    public boolean transactionTicked() {
        return lastTxnTick != null && lastTxnTick.startsWith("ticked");
    }

    /** Enter Low Profit, High Profit and Per Discount, each pinned to its own model. */
    public String enterProfitFields(String low, String high, String discount) {
        Object r = page.evaluate("(a) => {" + JS
                + " const put=(model,v,name)=>{"
                + "   const e=[...document.querySelectorAll(\"[ng-model='\"+model+\"']\")].filter(vis)"
                + "     .filter(x=>x.tagName==='INPUT')[0];"
                + "   return name+'='+(e? setEl(e,v) : '(no field '+model+')'); };"
                + " return [put(a.M+'LowProfit', a.low, 'LowProfit'),"
                + "         put(a.M+'HighProfit', a.high, 'HighProfit'),"
                + "         put(a.M+'PerDiscount', a.disc, 'PerDiscount')].join(' | '); }",
                java.util.Map.of("M", M, "low", low, "high", high, "disc", discount));
        lastProfit = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Store: " + lastProfit);
        return lastProfit;
    }

    public boolean profitEntered(String low, String high, String discount) {
        return lastProfit != null && !lastProfit.contains("(no field")
                && lastProfit.contains("LowProfit=" + low)
                && lastProfit.contains("HighProfit=" + high)
                && lastProfit.contains("PerDiscount=" + discount);
    }

    /**
     * Click <b>Add</b> ({@code AddIDDetails}) and verify a row really joined the grid.
     *
     * <p>Rows are counted before and after: Add on these screens can succeed silently with zero rows,
     * and the click alone proves nothing.</p>
     */
    public String clickAddRow() {
        Object before = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length; }");
        int rowsBefore = before instanceof Number ? ((Number) before).intValue() : 0;

        Object r = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/AddIDDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Add button for the transaction row)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        waitForAngular(1200);

        Object after = page.evaluate("(n) => {" + JS
                + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                + " return (rows.length-n)+'|'+rows.length+'|'+(rows.length? rows[rows.length-1].slice(0,90):''); }",
                rowsBefore);
        String[] p2 = (after == null ? "0|0|" : after.toString()).split("\\|", 3);
        lastAddRow = (r == null ? "" : r.toString()) + " | rows " + rowsBefore + " -> " + p2[1]
                + " (added " + p2[0] + ")" + (p2.length > 2 && !p2[2].isEmpty() ? ", newest row: " + p2[2] : "");
        System.out.println("Store: add row -> " + lastAddRow);
        return lastAddRow;
    }

    /** A row really joined the grid — judged on the count, not on the click. */
    public boolean rowAdded() {
        if (lastAddRow == null) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(added (-?\\d+)\\)").matcher(lastAddRow);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
    }

    /**
     * A success message, judged strictly.
     *
     * <p>These configuration screens phrase refusals with the word "Added" — e.g. "… Is Already Added!" —
     * so a plain {@code contains("added")} reads a rejection as a save.</p>
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String submitAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/submit|save/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
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
            if (toast.isEmpty()) page.waitForTimeout(500);
        }

        if (toast.isEmpty()) {
            // Some screens in this module answer in a "KPJ Portal" banner that carries no toast class —
            // looking only for toastr elements reports silence for a screen that spoke clearly.
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select|add))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("Store: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * remark, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String remark) {
        waitForAngular(1500);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.v? withCode.find(t=>t.includes(a.v)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "v", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("Store: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
