package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Supplier Return Note Approval</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → open <b>Print</b> → in
 * <b>List of GRN Return</b>, click on a row at the tick symbol to choose it → <b>Return Item Details</b>
 * shows that row's data → click <b>Print</b> → verify report generation.</p>
 *
 * <p>Confirmed via a screenshot: <b>List of GRN Return</b> has a leading tick-icon COLUMN present on
 * EVERY row (not a toggle-able checkbox like the row's own <b>Approved</b>/<b>Cancel</b> columns further
 * right) — clicking it is expected to SELECT that row and populate <b>Return Item Details</b> below with
 * its item lines (Item Name, Batch Code, Expiry Date, Received/Returned Quantity, Return Price, Net
 * Amount, Return Reason). Everything else has not been inspected live and is found by FUZZY matching,
 * following the same proven patterns as every other screen in this module:</p>
 * <ul>
 *   <li>"Add a new tab - Print" is handled the same defensive way as every other "add a [new/another]
 *       tab" step in this module ({@link PurchaseRequestApproval}, {@link PurchaseOrderApproval}) — none
 *       of those turned out to be a real tab, only the same action button used later, and clicking it
 *       early fired the action prematurely on sibling screens. So this never clicks anything that could
 *       be the real Print button, only a genuinely separate tab/section control, reported plainly if none
 *       exists.</li>
 *   <li>"Verify report generation" mirrors the same exploratory check used on
 *       {@link PurchaseOrderApproval} and {@link SupplierReturnNote} (a new browser tab/window, or a
 *       visible "report"-mentioning message) since it is equally unconfirmed here which form it takes.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.
 */
public class SupplierReturnNoteApproval extends BasePage {

    public SupplierReturnNoteApproval(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "",
            lastRows = "", lastPrintTab = "", lastRowClick = "", lastPrint = "", lastReportSignal = "";

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
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    // ---- navigation ------------------------------------------------------

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Supplier Return Note Approval. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*inventory\\s*$");
            clickMenu("^\\s*purchase\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*supplier\\s*return\\s*note\\s*approval\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__srnaMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__srnaMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SupplierReturnNoteApproval.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__srnaMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("supplierreturnnoteapproval") || route.contains("returnnoteapproval")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
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
        System.out.println("=== SupplierReturnNoteApproval CONTROLS ===\n" + s);
        return s;
    }

    // ---- search --------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> — both cleared before typing (pickers arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("SupplierReturnNoteApproval: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__srna'+(r==='fromdate'?'From':'To');"
                + " return e.id; }", re);
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            // NOT Escape — confirmed on the sibling SupplierReturnNote screen that a date-picker calendar
            // overlay's Escape keydown can bubble up and close a surrounding dialog/section unexpectedly.
            // A plain JS blur dismisses the calendar without that risk.
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

    /** Click the main <b>Search</b> button and report how many result rows came back. */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))"
                + "        || /^(fn)?search/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        lastSearch = "clicked Search -> " + rowCount() + " row(s)";
        System.out.println("SupplierReturnNoteApproval: " + lastSearch);
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

    // ---- Print (tab, then the row-tick / real action) --------------------------

    /**
     * Open <b>Print</b> — reported plainly whether this is a real tab, a button, or absent, rather than
     * assumed. Deliberately never clicks the real Print action button (only a genuinely separate
     * tab/section control) — see the class doc for why. Non-fatal: the flow continues to the row-click
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
        System.out.println("SupplierReturnNoteApproval: " + lastPrintTab);
        return lastPrintTab;
    }

    /**
     * Click on the first row "at the tick symbol" — the leading tick-icon column confirmed live on every
     * row of <b>List of GRN Return</b> (distinct from that row's own <b>Approved</b>/<b>Cancel</b>
     * checkbox columns further right). Expected to select the row and populate Return Item Details.
     */
    public String clickFirstRowTick() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=dataRows();"
                + " const row=rows.find(x=>x.querySelector('a,button')) || rows[0];"
                + " if(!row) return '(no row to click)';"
                + " const text=norm(row.textContent).slice(0,90);"
                + " const tick=row.querySelector('td:first-child,.ui-grid-cell:first-child')"
                + "   || row.firstElementChild;"
                + " (tick||row).click();"
                + " return 'clicked the row at the tick symbol: '+text; }");
        lastRowClick = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("SupplierReturnNoteApproval: " + lastRowClick);
        return lastRowClick;
    }

    public boolean rowTickClicked() {
        return lastRowClick != null && lastRowClick.startsWith("clicked the row at the tick symbol");
    }

    /** Whether Return Item Details actually populated after clicking the row's tick — judged on whether
     *  any data row with real text appears AFTER that section's own heading in document order. */
    public boolean returnItemDetailsShown() {
        Object r = page.evaluate("() => {" + JS
                + " const header=[...document.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*return\\s*item\\s*details\\s*$/i.test(ownText(x)));"
                + " if(!header) return false;"
                + " const isAfter=(a,b)=>!!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);"
                + " return dataRows().some(row=>isAfter(header,row) && norm(row.textContent).length>0); }");
        return Boolean.TRUE.equals(r);
    }

    // ---- Print (the real action) + report ---------------------------------

    /** Click <b>Print</b> (the confirming action) and check whether a report was generated — a new
     *  browser tab/window, or a visible "report"-mentioning message. */
    public String clickPrintAndVerifyReportGeneration() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*print\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?print/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Print button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        String clickedText = clicked == null ? "" : clicked.toString();
        waitForAngular(3500);

        int pagesAfter = page.context().pages().size();
        Object reportText = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('*')].filter(vis)"
                + "   .map(x=>ownText(x)).find(x=>/report/i.test(x) && x.length<120);"
                + " return t||''; }");
        String reportTextStr = reportText == null ? "" : reportText.toString();

        String reportSignal = pagesAfter > pagesBefore
                ? "a new tab/window opened (" + pagesBefore + " -> " + pagesAfter + " page(s))"
                : (!reportTextStr.isEmpty() ? "a \"report\"-mentioning message appeared: \"" + reportTextStr + "\""
                                             : "no new tab and no \"report\" message were observed");
        lastPrint = clickedText;
        lastReportSignal = clickedText + "; " + reportSignal;
        System.out.println("SupplierReturnNoteApproval: " + lastReportSignal);
        return lastReportSignal;
    }

    public boolean printClicked() { return lastPrint != null && lastPrint.startsWith("clicked \""); }
}
