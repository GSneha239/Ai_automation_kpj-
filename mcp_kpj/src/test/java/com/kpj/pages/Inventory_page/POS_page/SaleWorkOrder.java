package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; POS &gt; <b>Sale Work Order</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → in <b>Sale Work Order
 * Details</b>, tick a row's own checkbox to select it → <b>Drug Details</b> populates below, tick the
 * checkbox for the item added → open <b>Print</b> → click <b>Print</b> → verify PDF generation.</p>
 *
 * <p>Sibling of {@link ItemSaleList} in the same {@code POS_page} submodule — a search/report screen
 * with no New/Save, so it ends at PDF generation rather than a success toast. It has not been inspected
 * live and every control is found by FUZZY matching, applying fixes proven on that sibling screen from
 * the start rather than re-discovering them:</p>
 * <ul>
 *   <li>Checkbox ticks are done as ONE atomic JS call (find, click, sync Angular's
 *       {@code $setViewValue}, all in a single {@code page.evaluate}) — Playwright's own two-round-trip
 *       {@code Locator.check()} was observed elsewhere in this module to report success while the
 *       screen's own validation still saw nothing selected, because the live app re-renders the element
 *       between the "locate" and "act" round-trips.</li>
 *   <li>Button matching requires an open-paren right after the action name in {@code ng-click}
 *       (e.g. {@code /^print\s*\(/i}, not a bare {@code /^print/i} prefix) — on {@link ItemSaleList}, a
 *       bare-prefix match on "search" hit an unrelated {@code SearchPatientByMRNo()} button instead of
 *       the real {@code GetSaleReturn()} Search button, and the same shape of collision existed between
 *       {@code printReport()} and {@code printDrugLabelReport()}.</li>
 *   <li>Navigation waits for real visible content (not just the header shell) — {@link ItemSaleList}
 *       needed noticeably longer than the fixed wait used elsewhere in this module before its form
 *       actually rendered.</li>
 *   <li>Store, if present, is selected preferring "Pharmacy Main Store" — {@link ItemSaleList}'s search
 *       returned zero rows with Store left on its placeholder or defaulted to whichever store sorts
 *       first alphabetically.</li>
 *   <li>"Verify PDF generation" reuses {@link com.kpj.pages.PdfReport}, the shared PDF-judging helper
 *       already proven on the Radiology report screens and on {@link ItemSaleList} — it fetches the
 *       newly opened tab's bytes and judges its {@code %PDF} header, byte count and content streams,
 *       since Chrome's PDF viewer exposes no innerText/screenshot-able content to check any other way.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.
 */
public class SaleWorkOrder extends BasePage {

    public SaleWorkOrder(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastStore = "",
            lastSearch = "", lastRows = "", lastRowTick = "", lastDrugTick = "", lastPrintTab = "",
            lastPrint = "", lastPrintDrugLabelTab = "", lastPrintDrugLabel = "";

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
          + "const ownText=(el)=>{ let t=''; for(const n of el.childNodes)"
          + "  if(n.nodeType===3) t+=n.textContent; return norm(t); };"
          + "const dataRows=()=>[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
          + "  .filter(x=>norm(x.textContent))"
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));";

    // ---- navigation ------------------------------------------------------

    /** Application menu &rarr; Inventory &rarr; POS &rarr; Sale Work Order. */
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
                    + "   .find(x=>/^\\s*sale\\s*work\\s*order\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__swoMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__swoMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SaleWorkOrder.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__swoMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("saleworkorder") || route.contains("workorder")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /** Poll until the screen shows more than just its header shell — confirmed live on {@link
     *  ItemSaleList} that a POS screen can briefly render nothing else right after navigation, for
     *  noticeably longer than the fixed wait every other screen in this module needs. */
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
        System.out.println("=== SaleWorkOrder CONTROLS ===\n" + s);
        return s;
    }

    // ---- search --------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> — both cleared before typing (pickers arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("SaleWorkOrder: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__swo'+(r==='fromdate'?'From':'To');"
                + " return e.id; }", re);
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            // NOT Escape — confirmed on sibling Purchase_page screens that a date-picker calendar
            // overlay's Escape keydown can bubble up and close a surrounding dialog/section. A plain JS
            // blur dismisses the calendar without that risk.
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

    /** Select the <b>Store</b>, if present — preferring "Pharmacy Main Store" (confirmed live on
     *  {@link ItemSaleList} to be the store that actually holds sale records), with a retry loop in case
     *  the option list has not finished loading yet. Best-effort/non-fatal: this screen may not have a
     *  Store field, or may not need one, so an empty result is not itself a failure. */
    public String selectStore() {
        String tempId = "__swoStore";
        for (int attempt = 0; attempt < 6; attempt++) {
            Object plain = page.evaluate("(id) => {" + JS
                    + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(x=>/storeid|^store$/i.test(tail(x)+' '+labelOf(x)));"
                    + " if(!e) return 'NO_FIELD';"
                    + " e.id=id;"
                    + " const reals=[...e.options].map((o,i)=>({o,i}))"
                    + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                    + " const pharmacy=reals.find(x=>/pharmacy.*main.*store/i.test(norm(x.o.text)));"
                    + " const pick=pharmacy||reals[0];"
                    + " return pick? JSON.stringify({index:pick.i, count:reals.length}) : ''; }", tempId);
            String info = plain == null ? "" : plain.toString();
            if ("NO_FIELD".equals(info)) return "(no Store field on this screen)";
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
                    lastStore = (t == null ? "" : t.toString()) + " [" + info + "]";
                    return lastStore;
                } catch (Exception e) {
                    lastStore = "(select failed: " + e.getMessage().split("\n")[0] + ")";
                    return lastStore;
                }
            }
            page.waitForTimeout(800);
        }
        lastStore = "(no Store option ever populated — the dropdown stayed at its placeholder)";
        return lastStore;
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

    /** Click the main <b>Search</b> button and report how many result rows came back. */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))"
                + "        || /^(fn)?search\\s*\\(/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        lastSearch = "clicked Search -> " + rowCount() + " row(s)";
        System.out.println("SaleWorkOrder: " + lastSearch);
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

    // ---- select a row, then tick its Drug Details ------------------------------

    /**
     * Tick the FIRST row's own "select row" checkbox in <b>Sale Work Order Details</b> — the requested
     * step ("tick checkbox to choose any one of the item") did not name a specific row, so the first one
     * found is used. Its items are expected to populate <b>Drug Details</b> below.
     */
    public String tickFirstRowCheckbox() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const rows=dataRows()"
                + "     .filter(rr=>rr.querySelector('input[type=checkbox]'));"
                + "   const cb=rows.length? rows[0].querySelector('input[type=checkbox]') : null;"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__swoRowCb';"
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
        waitForAngular(1500);
        lastRowTick = result.startsWith("checked=")
                ? "ticked the first row -> " + result
                : "(" + result + ")";
        System.out.println("SaleWorkOrder: row tick -> " + lastRowTick);
        return lastRowTick;
    }

    public boolean rowChecked() { return lastRowTick != null && lastRowTick.contains("checked=true"); }

    /** How many item rows populated into <b>Drug Details</b> after selecting a Sale Work Order row. */
    public int drugDetailsRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const header=[...document.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*drug\\s*details\\s*$/i.test(ownText(x)));"
                + " if(!header) return -1;"
                + " const isAfter=(a,b)=>!!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);"
                + " return dataRows().filter(r=>isAfter(header,r) && r.querySelector('input[type=checkbox]')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean drugDetailsPopulated() { return drugDetailsRowCount() > 0; }

    /** Tick the checkbox for the item added in <b>Drug Details</b> — the requested step ("tick the
     *  checkbox for item added") did not name a specific item, so every row that populated is ticked. */
    public String tickDrugDetailsCheckbox() {
        Object r = page.evaluate("() => {" + JS
                + " const header=[...document.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*drug\\s*details\\s*$/i.test(ownText(x)));"
                + " if(!header) return '(no Drug Details section found)';"
                + " const isAfter=(a,b)=>!!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);"
                + " const rows=dataRows().filter(rr=>isAfter(header,rr) && rr.querySelector('input[type=checkbox]'));"
                + " let ticked=0;"
                + " for (const row of rows) {"
                + "   const cb=row.querySelector('input[type=checkbox]');"
                + "   if(!cb) continue;"
                + "   if(!cb.checked) cb.click();"
                + "   try{ const c=angular.element(cb).controller('ngModel');"
                + "        if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + "   cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "   if(cb.checked) ticked++; }"
                + " return 'ticked '+ticked+' of '+rows.length+' Drug Details item(s)'; }");
        lastDrugTick = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("SaleWorkOrder: " + lastDrugTick);
        return lastDrugTick;
    }

    public boolean drugDetailsTicked() {
        return lastDrugTick != null && lastDrugTick.startsWith("ticked ") && !lastDrugTick.startsWith("ticked 0 of");
    }

    // ---- Print (tab, then the real action) + PDF verification ------------------

    /**
     * Open <b>Print</b> — reported plainly whether this is a real tab, a button, or absent, rather than
     * assumed. Deliberately never clicks the real Print action button (only a genuinely separate
     * tab/section control) — see the class doc for why. Non-fatal: the flow continues to the real click
     * regardless.
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
        lastPrintTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("SaleWorkOrder: " + lastPrintTab);
        return lastPrintTab;
    }

    /**
     * Click <b>Print</b> and return how many browser tabs existed just before the click — needed by
     * {@link com.kpj.pages.PdfReport#capture} to find the tab it opens.
     *
     * <p>Matched on EXACT visible text "Print" only, with no ng-click fallback: on {@link ItemSaleList},
     * a wildcard fallback like {@code /print\w*\(/i} would have matched BOTH {@code printReport()} and
     * {@code printDrugLabelReport()} — the exact-text match was the precise, reliable part both times a
     * button collision happened there, so it is trusted alone here rather than risking the same
     * over-broad-fallback mistake on a screen not yet inspected live.</p>
     */
    public int clickPrint() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*print\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Print button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastPrint = clicked == null ? "" : clicked.toString();
        System.out.println("SaleWorkOrder: " + lastPrint);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean printClicked() { return lastPrint != null && lastPrint.startsWith("clicked \""); }

    // ---- Print Drug Label (second tab of this same screen — mirrors Print above) ----------------------

    /**
     * Open <b>Print Drug Label</b> — same defensive pattern as {@link #clickPrintTab()}: never clicks the
     * real "Print Drug Label" action button ({@code printDrugLabelReport()}, confirmed live on the main
     * form), only a genuinely separate tab/section control, reported plainly if none exists.
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
        lastPrintDrugLabelTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("SaleWorkOrder: " + lastPrintDrugLabelTab);
        return lastPrintDrugLabelTab;
    }

    /**
     * Click <b>Print Drug Label</b> and return how many browser tabs existed just before the click —
     * needed by {@link com.kpj.pages.PdfReport#capture} to find the tab it opens.
     *
     * <p>Matched on EXACT visible text "Print Drug Label" only, with no ng-click fallback — same
     * reasoning as {@link #clickPrint()}: this screen's "Print" button sits right next to it, and a
     * wildcard ng-click fallback risks the same button-collision class of bug seen on
     * {@link ItemSaleList}.</p>
     */
    public int clickPrintDrugLabel() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*print\\s*drug\\s*label\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Print Drug Label button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastPrintDrugLabel = clicked == null ? "" : clicked.toString();
        System.out.println("SaleWorkOrder: " + lastPrintDrugLabel);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean printDrugLabelClicked() {
        return lastPrintDrugLabel != null && lastPrintDrugLabel.startsWith("clicked \"");
    }
}
