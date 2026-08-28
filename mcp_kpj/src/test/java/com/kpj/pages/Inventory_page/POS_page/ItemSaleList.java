package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; POS &gt; <b>Item Sale List</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → in the list of item sales,
 * tick a row's own checkbox to select it → open <b>Print Drug Label</b> → click <b>Print Drug Label</b>
 * → verify a PDF drug label is generated.</p>
 *
 * <p>This is the first screen in the {@code Inventory_page.POS_page} submodule — a search/report screen
 * (no New/Save), so it has no create flow and no success toast, only a search-and-print action. It has
 * not been inspected live and every control is found by FUZZY matching, following the same proven
 * patterns as the {@code Purchase_page} submodule of this same module:</p>
 * <ul>
 *   <li>Checkbox ticks are done as ONE atomic JS call (find, click, sync Angular's
 *       {@code $setViewValue}, all in a single {@code page.evaluate}) — Playwright's own two-round-trip
 *       {@code Locator.check()} was observed elsewhere in this module to report success while the
 *       screen's own validation still saw nothing selected, because the live app re-renders the element
 *       between the "locate" and "act" round-trips.</li>
 *   <li>"Add a new tab - Print Drug Label" is handled the same defensive way as every other "add a
 *       [new/another] tab" step in this module ({@link com.kpj.pages.Inventory_page.Purchase_page
 *       .PurchaseRequestApproval}, {@link com.kpj.pages.Inventory_page.Purchase_page
 *       .PurchaseOrderApproval}, {@link com.kpj.pages.Inventory_page.Purchase_page
 *       .SupplierReturnNoteApproval}) — none of those turned out to be a real tab, only the same action
 *       button used later, and clicking it early misfired the action on sibling screens. So this never
 *       clicks anything that could be the real Print Drug Label button before the row is selected.</li>
 *   <li>"Verify PDF generation" reuses {@link com.kpj.pages.PdfReport}, the shared PDF-judging helper
 *       already proven on the Radiology report screens — it fetches the newly opened tab's bytes and
 *       judges its {@code %PDF} header, byte count and content streams, since Chrome's PDF viewer exposes
 *       no innerText/screenshot-able content to check any other way.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.
 */
public class ItemSaleList extends BasePage {

    public ItemSaleList(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "",
            lastRows = "", lastRowTick = "", lastPrintTab = "", lastPrint = "",
            lastPrintOnlyTab = "", lastPrintOnly = "";

    /** Shared JS helpers: visibility, text normalising, ng-model tail, top dialog, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const dialogs=()=>[...document.querySelectorAll('.modal,.modal-content,[role=dialog],.sweet-alert')].filter(vis);"
          + "const topDialog=()=>dialogs().pop();"
          + "const dataRows=()=>[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
          + "  .filter(x=>norm(x.textContent))"
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));";

    // ---- navigation ------------------------------------------------------

    /** Application menu &rarr; Inventory &rarr; POS &rarr; Item Sale List. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*inventory\\s*$");
            clickMenu("^\\s*pos\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*item\\s*sale\\s*list\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__islMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__islMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ItemSaleList.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__islMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On this screen — matched on the route the MENU discovered, since the real route is not pinned. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("itemsalelist") || route.contains("itemsale")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /**
     * Poll until the screen shows more than just its header shell — confirmed live: right after
     * navigation this screen briefly renders NOTHING but the openMic button (no sidebar icons even),
     * for noticeably longer than the fixed wait every other screen in this module has needed. Rather
     * than raise that fixed wait everywhere (most screens render immediately), this polls specifically
     * here, up to {@code timeoutMs}.
     */
    public boolean waitForRealContent(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("() => {" + JS
                    + " return [...document.querySelectorAll('select,input,button')].filter(vis).length; }");
            int count = n instanceof Number ? ((Number) n).intValue() : 0;
            if (count > 2) return true;
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- diagnostics -------------------------------------------------------

    /** Dump every visible control — the main form, or whichever dialog currently sits on top. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); const root=d||document;"
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
                + " return (d? '[dialog: '+norm(d.textContent).slice(0,40)+']\\n' : '[main form]\\n')"
                + "   + [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== ItemSaleList CONTROLS ===\n" + s);
        return s;
    }

    // ---- search --------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> — both cleared before typing (pickers arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("ItemSaleList: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__isl'+(r==='fromdate'?'From':'To');"
                + " return e.id; }", re);
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            // NOT Escape — confirmed on the sibling Supplier/Loan Supplier Return Note screens that a
            // date-picker calendar overlay's Escape keydown can bubble up and close a surrounding
            // dialog/section. A plain JS blur dismisses the calendar without that risk.
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(500);
        Object back = page.evaluate("(i) => { const e=document.getElementById(i);"
                + " return e? (e.value||'') : '(gone)'; }", id);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from) && lastDates.contains("ToDate=" + to);
    }

    /**
     * Select the <b>Store</b> — confirmed live: the search returned zero rows across the full
     * 2020-2026 range with Store left on its placeholder, so a store is likely required. The dropdown
     * was ALSO seen with only its placeholder option present right after navigation (before the real
     * options had loaded) — {@link #waitForRealContent} already waits past that, but this retries its
     * own read a few times regardless, in case the store list populates later still.
     */
    public String selectStore() {
        String tempId = "__islStore";
        for (int attempt = 0; attempt < 6; attempt++) {
            Object plain = page.evaluate("(id) => {" + JS
                    + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(x=>/storeid|^store$/i.test(tail(x)+' '+labelOf(x)));"
                    + " if(!e) return '';"
                    + " e.id=id;"
                    + " const reals=[...e.options].map((o,i)=>({o,i}))"
                    + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                    // Prefer "Pharmacy Main Store" — every other Inventory screen this session that sells
                    // items operates against that store, so it is far likelier to hold real sale
                    // transactions than whichever store happens to sort first alphabetically.
                    + " const pharmacy=reals.find(x=>/pharmacy.*main.*store/i.test(norm(x.o.text)));"
                    + " const pick=pharmacy||reals[0];"
                    + " return pick? JSON.stringify({index:pick.i, count:reals.length}) : ''; }", tempId);
            String info = plain == null ? "" : plain.toString();
            if (!info.isEmpty()) {
                try {
                    java.util.Map<String, Object> m = parseFlatJson(info);
                    page.locator("#" + tempId).selectOption(
                            new com.microsoft.playwright.options.SelectOption()
                                    .setIndex(((Number) m.get("index")).intValue()));
                    waitForAngular(700);
                    Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                            + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", tempId);
                    page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tempId);
                    return (t == null ? "" : t.toString()) + " [" + info + "]";
                } catch (Exception e) {
                    return "(select failed: " + e.getMessage().split("\n")[0] + ")";
                }
            }
            page.waitForTimeout(800);
        }
        return "(no Store option ever populated — the dropdown stayed at its placeholder)";
    }

    /** Minimal parser for flat {key:number,...} JSON produced above — no library, known shape. */
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

    public boolean storeSelected(String result) {
        return result != null && !result.isEmpty() && !result.startsWith("(");
    }

    /** Select the <b>All</b> radio for the OPD/IPD/External scope (confirmed live: {@code
     *  ItemSaleList.PharmacyBillOPDIPD}) — none is selected by default, and search may need one chosen. */
    public String selectAllScope() {
        Object r = page.evaluate("() => {" + JS
                + " const radios=[...document.querySelectorAll('input[type=radio]')].filter(vis)"
                + "   .filter(x=>/PharmacyBillOPDIPD/i.test(ngOf(x)));"
                + " const all=radios.find(x=>/^\\s*all\\s*$/i.test(labelOf(x)));"
                + " if(!all) return '(no All radio found among '+radios.length+' scope radio(s))';"
                + " if(!all.checked) all.click();"
                + " try{ const c=angular.element(all).controller('ngModel');"
                + "      if(c){ c.$setViewValue(all.value); c.$render(); } }catch(err){}"
                + " all.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return all.checked? 'selected All' : '(All would not stay selected)'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ItemSaleList: scope -> " + result);
        return result;
    }

    /** Click the main <b>Search</b> button and report how many result rows came back. */
    public String clickSearch() {
        // Confirmed live: the broad ng-click fallback /^(fn)?search/i (a prefix check with no anchor on
        // what follows) matched "SearchPatientByMRNo();" — an icon-only MRN-lookup button that happens
        // to sit earlier in the DOM — instead of the real Search button (ng-click="GetSaleReturn();",
        // which does not even match "search" itself; only its exact text "Search" does). Clicking the
        // wrong button fired "Please Enter MRN No.!" validation instead of a real search. Requiring an
        // open-paren right after "search" (only whitespace allowed in between) excludes that collision.
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))"
                + "        || /^(fn)?search\\s*\\(/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Search button found)';"
                + " b.click();"
                + " return 'clicked \"'+norm(b.textContent)+'\" [ng-click='+(b.getAttribute('ng-click')||'-')"
                + "   +'] disabled='+b.disabled; }");
        System.out.println("ItemSaleList: search click -> " + clicked);
        waitForAngular(4000);
        Object errText = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('.toast-message,.toast,[id^=toast],.alert,"
                + "   .validation-summary-errors,.help-block,.error,[class*=invalid]')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).find(x=>x); return t||''; }");
        String errTextStr = errText == null ? "" : errText.toString();
        if (!errTextStr.isEmpty()) System.out.println("ItemSaleList: possible validation message -> " + errTextStr);
        lastSearch = "clicked Search -> " + rowCount() + " row(s)"
                + (errTextStr.isEmpty() ? "" : "; possible validation message: \"" + errTextStr + "\"");
        System.out.println("ItemSaleList: " + lastSearch);
        return lastSearch;
    }

    public int rowCount() {
        Object n = page.evaluate("() => {" + JS + " return dataRows().length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=dataRows().map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    public boolean rowsFound() { return rowCount() > 0; }

    // ---- select a row, then Print Drug Label -----------------------------------

    /**
     * Tick the FIRST row's own "select row" checkbox in the list of item sales — the requested step
     * ("tick checkbox to choose any one of the item") did not name a specific row, so the first one
     * found is used.
     */
    public String tickFirstRowCheckbox() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const rows=dataRows()"
                + "     .filter(rr=>rr.querySelector('input[type=checkbox]'));"
                + "   const cb=rows.length? rows[0].querySelector('input[type=checkbox]') : null;"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__islRowCb';"
                + "   return cb.id; };"
                + " for (let attempt = 0; attempt < 8; attempt++) {"
                + "   const id=find();"
                + "   if(!id) continue;"
                + "   const cb=document.getElementById(id);"
                + "   if(!cb) continue;"
                + "   const row=cb.closest('tr,.ui-grid-row');"
                + "   const rowText=row? norm(row.textContent).slice(0,90) : '?';"
                + "   if(!cb.checked) cb.click();"
                + "   try{ const c=angular.element(cb).controller('ngModel');"
                + "        if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + "   cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "   if(cb.checked) return 'checked='+cb.checked+'; the row reads: '+rowText; }"
                + " return '(the checkbox would not stay attached across 8 attempts, or no rows exist)'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(800);
        lastRowTick = result.startsWith("checked=")
                ? "ticked the first row -> " + result
                : "(" + result + ")";
        System.out.println("ItemSaleList: row tick -> " + lastRowTick);
        return lastRowTick;
    }

    public boolean rowChecked() { return lastRowTick != null && lastRowTick.contains("checked=true"); }

    /**
     * Open <b>Print Drug Label</b> — reported plainly whether this is a real tab, a button, or absent,
     * rather than assumed. Deliberately never clicks the real Print Drug Label action button (only a
     * genuinely separate tab/section control) — see the class doc for why. Non-fatal: the flow continues
     * to the real click regardless.
     */
    public String clickPrintDrugLabelTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*print\\s*drug\\s*label\\s*$/i.test(norm(x.textContent))"
                + "        && !/print/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Print Drug Label tab exists on this screen — "
                + "\"Print Drug Label\" is the same action button used in the later step, so it is left "
                + "untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Print Drug Label tab'; }");
        lastPrintTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("ItemSaleList: " + lastPrintTab);
        return lastPrintTab;
    }

    // ---- Print Drug Label (the real action) + PDF verification -----------------

    /** Click <b>Print Drug Label</b> and return how many browser tabs existed just before the click —
     *  needed by {@link com.kpj.pages.PdfReport#capture} to find the tab it opens. */
    public int clickPrintDrugLabel() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*print\\s*drug\\s*label\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /print.*drug.*label/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Print Drug Label button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastPrint = clicked == null ? "" : clicked.toString();
        System.out.println("ItemSaleList: " + lastPrint);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean printClicked() { return lastPrint != null && lastPrint.startsWith("clicked \""); }

    // ---- Print (second tab of this same screen — mirrors Print Drug Label above) ----------------------

    /**
     * Open <b>Print</b> — same defensive pattern as {@link #clickPrintDrugLabelTab()}: never clicks the
     * real "Print" action button ({@code printReport()}, confirmed live on the main form), only a
     * genuinely separate tab/section control, reported plainly if none exists.
     */
    public String clickPrintTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*print\\s*$/i.test(norm(x.textContent))"
                + "        && !/print/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Print tab exists on this screen — \"Print\" is the same "
                + "action button used in the later step, so it is left untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Print tab'; }");
        lastPrintOnlyTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("ItemSaleList: " + lastPrintOnlyTab);
        return lastPrintOnlyTab;
    }

    /** Click <b>Print</b> ({@code printReport()}) and return how many browser tabs existed just before
     *  the click — needed by {@link com.kpj.pages.PdfReport#capture} to find the tab it opens. */
    public int clickPrint() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*print\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?printreport\\s*\\(/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Print button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastPrintOnly = clicked == null ? "" : clicked.toString();
        System.out.println("ItemSaleList: " + lastPrintOnly);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean printOnlyClicked() { return lastPrintOnly != null && lastPrintOnly.startsWith("clicked \""); }
}
