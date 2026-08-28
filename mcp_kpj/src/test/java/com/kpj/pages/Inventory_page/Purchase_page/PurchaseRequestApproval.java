package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Request Approval</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → verify results appear →
 * click a result row to open it → (if present) click <b>Get Items</b> → click <b>Search</b> → verify
 * rows → open <b>Approve PR</b> → tick the <b>Approved PR</b> checkbox for the item(s) → click
 * <b>Approve PR</b> → verify the success toast.</p>
 *
 * <p>This screen has not been inspected live, and several of the requested steps are themselves
 * ambiguous ("click on the" — the row to open is not named; whether a "Get Items" picker/button and an
 * "Approve PR" tab literally exist here, as opposed to being reused terminology from the sibling
 * New/Quotation/Purchase Request screens, is unconfirmed). Every control is therefore found by FUZZY
 * matching, and each ambiguous step is implemented as a best-effort, non-fatal attempt that reports
 * plainly what it found rather than assuming a specific structure. It is a sibling of
 * {@link ItemEnquiry}, {@link Quotation} and {@link PurchaseRequest} in the same Purchase module, so
 * proven fixes from those screens (atomic-JS checkbox ticks, targeting the LAST matching element for
 * per-row fields, the shared "Item Search" picker and its blank-search fallback) are applied from the
 * start. {@link #describeControls()} dumps every visible control at each stage so the real structure can
 * replace these guesses once this has run against the live screen.</p>
 */
public class PurchaseRequestApproval extends BasePage {

    public PurchaseRequestApproval(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "",
            lastRows = "", lastRowClick = "", lastGetItems = "", lastPickerSearch = "",
            lastPickerRows = "", lastApproveTab = "", lastApproveTick = "", lastApprovePr = "",
            lastSave = "", lastSaveDiagnostics = "",
            lastRejectRowClick = "", lastRejectTab = "", lastRejectTick = "", lastRejectPr = "",
            lastRejectSave = "", lastRejectDiagnostics = "";

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
          + "const approvedPrHeader=()=>[...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "  .find(x=>/^approved\\s*pr$/i.test(ownText(x)))"
          + "  || [...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "    .find(x=>/^approved\\s*pr$/i.test(norm(x.textContent)));"
          + "const dataRows=()=>[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
          + "  .filter(x=>norm(x.textContent))"
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));"
          + "const rejectHeader=()=>[...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "  .find(x=>/^reject$/i.test(ownText(x)))"
          + "  || [...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "    .find(x=>/^reject$/i.test(norm(x.textContent)));"
          + "const approvedCbInRow=(row,aRect)=>[...row.querySelectorAll('input[type=checkbox]')].filter(vis)"
          + "  .find(c=>{ const rc=c.getBoundingClientRect(); const cx=rc.left+rc.width/2;"
          + "    return cx>=aRect.left-5 && cx<=aRect.right+5; });"
          // A row is only genuinely PENDING if NEITHER its Approved PR checkbox NOR its Reject checkbox has
          // already been decided — confirmed live: a row already Approved still shows its Reject checkbox
          // as enabled/unchecked, so checking only the target column let Reject PR silently pick an
          // already-approved PR (the server refuses to reject an already-decided PR, hence no toast).
          + "const isRowUndecided=(row)=>{ const aHead=approvedPrHeader(), rHead=rejectHeader();"
          + "  if(!aHead || !rHead) return false;"
          + "  const acb=approvedCbInRow(row,aHead.getBoundingClientRect());"
          + "  const rcb=approvedCbInRow(row,rHead.getBoundingClientRect());"
          + "  return !!(acb && !acb.checked && !acb.disabled && rcb && !rcb.checked && !rcb.disabled); };"
          + "const findPendingRow=(excludeText)=>{ const aHead=approvedPrHeader(); if(!aHead) return null;"
          + "  const rows=dataRows().filter(x=>x.querySelector('a,button'))"
          + "    .filter(x=>!excludeText || !norm(x.textContent).includes(excludeText));"
          + "  return rows.find(isRowUndecided) || null; };"
          + "const findPendingRowForReject=(excludeText)=>{ const rHead=rejectHeader(); if(!rHead) return null;"
          + "  const rows=dataRows().filter(x=>x.querySelector('a,button'))"
          + "    .filter(x=>!excludeText || !norm(x.textContent).includes(excludeText));"
          + "  return rows.find(isRowUndecided) || null; };"
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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Purchase Request Approval. */
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
                    + "   .find(x=>/^\\s*purchase\\s*request\\s*approval\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__praMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__praMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PurchaseRequestApproval.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__praMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("purchaserequestapproval") || route.contains("prapproval")) return true;
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
        System.out.println("=== PurchaseRequestApproval CONTROLS ===\n" + s);
        return s;
    }

    // ---- search ------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> — both cleared before typing (pickers arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("PurchaseRequestApproval: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__pra'+(r==='fromdate'?'From':'To');"
                + " return e.id; }", re);
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            box.press("Escape");
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
        System.out.println("PurchaseRequestApproval: " + lastSearch);
        return lastSearch;
    }

    public int rowCount() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /**
     * Click a result row to open it — the requested step's target ("click on the ...") was not named, so
     * this opens the first row in the results list. Re-running against the same PR after it has already
     * been approved fails with "Please select at least one Purchase Request." (its Approved PR checkbox
     * becomes disabled), so this requires a row that is genuinely UNDECIDED (neither Approved PR nor
     * Reject already actioned) rather than blindly taking rows[0] — otherwise every rerun would target
     * whichever PR a previous run already decided. Falling back to "any" row here previously picked an
     * already-decided one and silently failed downstream instead of reporting exhaustion plainly (the
     * same bug fixed in {@link #clickAnotherPendingRow(String)}) — with enough repeated live runs against
     * one fixed date range, every PR eventually gets decided and none remain.
     */
    public String clickFirstRow() {
        // rows[0] with a bare 'tr' selector was landing on the header row (the column-label row), not a
        // real data row. Real rows carry a per-row clickable button, e.g. ng-click="OpenPOStatusModal(PO)";
        // header rows never do. Skip thead/th rows and prefer one that actually has such a button.
        Object r = page.evaluate("() => {" + JS
                + " const row=findPendingRow();"
                + " if(!row) return '(no undecided row left to open; dataRows='+dataRows().length+')';"
                + " const text=norm(row.textContent).slice(0,90);"
                + " const clickable=row.querySelector('a,button')||row;"
                + " clickable.click();"
                + " return 'clicked the first pending row: '+text; }");
        lastRowClick = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("PurchaseRequestApproval: " + lastRowClick);
        return lastRowClick;
    }

    public boolean rowClicked() { return lastRowClick != null && lastRowClick.startsWith("clicked the first pending row"); }

    /**
     * Confirmed live: opening a row shows a READ-ONLY "Purchase Request Status" modal (only a Close
     * button — no approval action lives inside it). Left open, it sits over the real list and is a
     * plausible reason the later checkbox-tick / Approve PR clicks (fired via JS against the underlying
     * page) get no response. Close it immediately so the rest of the flow runs against the real list.
     */
    public String closeStatusModalIfOpen() {
        Object r = page.evaluate("() => {" + JS
                + " const btn=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*close\\s*$/i.test(norm(x.textContent)));"
                + " if(!btn) return '(no status modal open)';"
                + " btn.click();"
                + " return 'closed the status modal'; }");
        String s = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseRequestApproval: " + s);
        return s;
    }

    // ---- Get Items (if present on this screen; same shared "Item Search" picker) --

    public boolean getItemsButtonPresent() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('button,a')].filter(vis)"
                + "   .some(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /OpenItemSearch/i.test(x.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    public String clickGetItems() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /OpenItemSearch/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__praGetItems'; }");
        try {
            page.locator("#__praGetItems").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("PurchaseRequestApproval.clickGetItems: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__praGetItems'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the item picker opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Get Items'; }");
        lastGetItems = r == null ? "" : r.toString();
        System.out.println("PurchaseRequestApproval: " + lastGetItems);
        return lastGetItems;
    }

    public boolean itemPickerOpen() {
        return lastGetItems != null && lastGetItems.startsWith("the item picker opened");
    }

    public String clickSearchAndVerifyRowsInPicker() {
        clickSearchInPicker();
        int rows = pickerRowCount();
        String[] fallbacks = { "a", "e", "i" };
        for (int i = 0; rows == 0 && i < fallbacks.length; i++) {
            String term = fallbacks[i];
            page.evaluate("(t) => { const e=document.querySelector(\"[ng-model='ItemSearch.ItemName']\");"
                    + " if(e){ e.focus(); e.value=t;"
                    + "   try{ const c=angular.element(e).controller('ngModel');"
                    + "        if(c){ c.$setViewValue(t); c.$render(); } }catch(err){}"
                    + "   e.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   e.dispatchEvent(new Event('change',{bubbles:true})); } }", term);
            clickSearchInPicker();
            rows = pickerRowCount();
        }
        lastPickerRows = pickerRowCount() + " row(s) after search";
        return lastPickerRows;
    }

    private void clickSearchInPicker() {
        page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return;"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/SearchItem/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...d.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        lastPickerSearch = "clicked Search -> " + pickerRowCount() + " row(s) in the item picker";
        System.out.println("PurchaseRequestApproval: " + lastPickerSearch);
    }

    public int pickerRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " return [...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "   .filter(e=>e.closest('tr,.ui-grid-row')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean pickerRowsFound() {
        return lastPickerRows != null && !lastPickerRows.startsWith("0 ");
    }

    // ---- Approve PR ----------------------------------------------------------

    /**
     * Open <b>Approve PR</b> — reported plainly whether this is a real tab, a button, or absent, rather
     * than assumed. Non-fatal: the flow continues to the tick/approve steps regardless, the same way
     * "add a new tab" was handled on Quotation (which turned out to have no separate tab either).
     */
    public String clickApprovePrTab() {
        // Confirmed live: this screen has no separate "Approve PR" tab — "Approve PR" is the same
        // confirming action button used in clickApprovePrAndGetToast(). Clicking it here, before the
        // Approved PR checkboxes are ticked, fires the real approval prematurely (with nothing selected)
        // and leaves the screen in a state where the later, correctly-sequenced click gets no response.
        // So this step only looks for a DISTINCT tab/section control, never the "Approve PR" action itself.
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*approve\\s*pr\\s*$/i.test(norm(x.textContent))"
                + "        && !/approve/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Approve PR tab exists on this screen — \"Approve PR\" is "
                + "the same action button used in the final step, so it is left untouched here to avoid "
                + "firing the approval before the checkboxes are ticked)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Approve PR tab'; }");
        lastApproveTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseRequestApproval: " + lastApproveTab);
        return lastApproveTab;
    }

    /**
     * Tick the <b>Approved PR</b> checkbox for the item(s) — "for the respective items only", i.e. every
     * row's own checkbox, not a single global one. Ticks every visible checkbox whose row/label mentions
     * "approve" as ONE atomic JS call per checkbox (find-and-click together, no separate round-trip —
     * see the class doc for why that matters).
     */
    public String tickApprovedPrCheckboxes() {
        // Confirmed live via screenshot: every checkbox's accessible label/tail on this grid resolves to
        // the WHOLE concatenated header row text ("...Approved PR Approved By Approved Date Reject
        // Rejected By..."), so a text/label match for /approv/i hits BOTH the "Approved PR" checkbox AND
        // the "Reject" checkbox on every row, and the previous version ticked BOTH for all 40 rows —
        // approving and rejecting the same PR at once, plus re-ticking rows already approved by someone
        // else. That contradictory bulk state is the likely reason Approve PR silently did nothing.
        // Fixed with geometry: find the "Approved PR" header cell by its own exact text, use its X
        // position to pick only the checkbox in that column, and scope to the single row that was opened
        // (the "respective item") rather than all rows.
        // Reuses the SAME findPendingRow() logic clickFirstRow() used, so this ticks the checkbox on
        // whichever row is still pending — the row that was actually opened — rather than assuming
        // rows[0] (which, on a rerun, is often a PR a previous run already approved and is now disabled).
        Object r = page.evaluate("() => {" + JS
                + " const aHead=approvedPrHeader();"
                + " if(!aHead) return '(no Approved PR column header found)';"
                + " const aRect=aHead.getBoundingClientRect();"
                + " const row=findPendingRow();"
                + " if(!row) return '(no pending row left to tick — every PR in range is already approved)';"
                + " const cb=approvedCbInRow(row,aRect);"
                + " if(!cb) return '(no Approved PR checkbox found in the row)';"
                + " if(!cb.checked) cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel');"
                + "      if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + " return cb.checked ? 'ticked 1 of 1 Approved PR checkbox (the opened row only)'"
                + "                   : '(the Approved PR checkbox would not stay checked)'; }");
        lastApproveTick = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseRequestApproval: " + lastApproveTick);
        return lastApproveTick;
    }

    public boolean approvedPrTicked() {
        return lastApproveTick != null && lastApproveTick.startsWith("ticked ")
                && !lastApproveTick.startsWith("ticked 0 of");
    }

    // ---- Approve PR (the confirming action) + toast -------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("approved") || t.contains("updated") || t.contains("rejected");
    }

    /** Click <b>Approve PR</b> (the confirming action button) and return the toast. */
    public String clickApprovePrAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*approve\\s*pr\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /approve/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Approve PR button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        String clickedText = clicked == null ? "" : clicked.toString();
        waitForAngular(2500);
        try { acceptSaveDialog(); } catch (Exception ignore) { }

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
        lastApprovePr = clickedText;
        lastSaveDiagnostics = clickedText + (toast.isEmpty() ? "; no message within 15s" : "");
        lastSave = toast;
        System.out.println("PurchaseRequestApproval: approve -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public boolean approvePrClicked() { return lastApprovePr != null && lastApprovePr.startsWith("clicked \""); }

    // ---- Reject PR (second tab of this same screen — mirrors Approve PR above) ----------------------

    /**
     * Open <b>Reject PR</b> — like {@link #clickApprovePrTab()}, reported plainly rather than assumed.
     * Non-fatal, and deliberately never clicks the real "Reject PR" action button here (only a genuinely
     * separate tab/section control), for the same reason clickApprovePrTab() avoids the Approve PR button:
     * firing the action before the checkbox is ticked leaves the screen unable to answer the later,
     * correctly-sequenced click.
     */
    public String clickRejectPrTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*reject\\s*pr\\s*$/i.test(norm(x.textContent))"
                + "        && !/reject/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Reject PR tab exists on this screen — \"Reject PR\" is "
                + "the same action button used in the final step, so it is left untouched here to avoid "
                + "firing the rejection before the checkbox is ticked)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Reject PR tab'; }");
        lastRejectTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseRequestApproval: " + lastRejectTab);
        return lastRejectTab;
    }

    /**
     * Open another result row for the Reject PR item — same "click on the ..." pattern as Approve PR,
     * but excluding whichever PR the Approve tab already processed in this same run. Requires a row that
     * is genuinely UNDECIDED (neither its Approved PR nor its Reject checkbox already actioned) — a row
     * already approved still shows its Reject checkbox as enabled/unchecked, but the app refuses to
     * reject an already-decided PR, so falling back to "any" row (rather than a genuinely pending one)
     * previously picked exactly such a row and silently failed the final step. With enough repeated live
     * runs against the same fixed date range, every PR eventually gets decided and none remain — reported
     * plainly rather than masked by a bad fallback.
     */
    public String clickAnotherPendingRow(String excludeText) {
        Object r = page.evaluate("(excl) => {" + JS
                + " const row=findPendingRowForReject(excl);"
                + " if(!row) return '(no undecided row left to reject; dataRows='+dataRows().length"
                + "   +', withButton='+dataRows().filter(x=>x.querySelector(\"a,button\")).length"
                + "   +', rejectHeader='+(rejectHeader()?'found':'MISSING')+')';"
                + " const text=norm(row.textContent).slice(0,90);"
                + " const clickable=row.querySelector('a,button')||row;"
                + " clickable.click();"
                + " return 'clicked another pending row: '+text; }", excludeText == null ? "" : excludeText);
        lastRejectRowClick = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("PurchaseRequestApproval: " + lastRejectRowClick);
        return lastRejectRowClick;
    }

    public boolean rejectRowClicked() {
        return lastRejectRowClick != null && lastRejectRowClick.startsWith("clicked another pending row");
    }

    /**
     * Tick the <b>Reject</b> checkbox for the item — "for the respective items only", scoped to the same
     * single opened row, using the same column-geometry fix (matching the checkbox by the "Reject" header
     * cell's X position rather than by label text, since every checkbox's accessible label on this grid
     * resolves to the whole concatenated header row) that Approve PR's tick needed.
     */
    public String tickRejectCheckbox(String excludeText) {
        Object r = page.evaluate("(excl) => {" + JS
                + " const rHead=rejectHeader();"
                + " if(!rHead) return '(no Reject column header found)';"
                + " const rRect=rHead.getBoundingClientRect();"
                + " const row=findPendingRowForReject(excl);"
                + " if(!row) return '(no pending row left to tick — every PR in range is already rejected)';"
                + " const cb=approvedCbInRow(row,rRect);"
                + " if(!cb) return '(no Reject checkbox found in the row)';"
                + " if(!cb.checked) cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel');"
                + "      if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + " return cb.checked ? 'ticked 1 of 1 Reject checkbox (the opened row only)'"
                + "                   : '(the Reject checkbox would not stay checked)'; }",
                excludeText == null ? "" : excludeText);
        lastRejectTick = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseRequestApproval: " + lastRejectTick);
        return lastRejectTick;
    }

    public boolean rejectTicked() {
        return lastRejectTick != null && lastRejectTick.startsWith("ticked ")
                && !lastRejectTick.startsWith("ticked 0 of");
    }

    /** Click <b>Reject PR</b> (the confirming action button) and return the toast. */
    public String clickRejectPrAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*reject\\s*pr\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /RejectPR/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Reject PR button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        String clickedText = clicked == null ? "" : clicked.toString();
        waitForAngular(2500);

        // Confirmed live: clicking Reject PR opens a confirmation dialog with a REQUIRED "Reason"
        // textarea (ng-model=POList.Remark) and its own Save button (class btn-primary, not the
        // btn-danger acceptSaveDialog() looks for). Left blank/unanswered, the dialog just sits there —
        // no request is ever sent, hence no toast. Fill the reason, then click this dialog's Save.
        Object dialogResult = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog open)';"
                + " const remark=d.querySelector(\"[ng-model='POList.Remark']\");"
                + " if(remark) setEl(remark, 'Rejected via automated test');"
                + " const save=[...d.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)));"
                + " if(!save) return '(reject-reason dialog open, but no Save button found)';"
                + " save.click();"
                + " return remark? 'filled reason and clicked Save' : 'clicked Save (no Remark field found)'; }");
        String dialogText = dialogResult == null ? "" : dialogResult.toString();
        System.out.println("PurchaseRequestApproval: reject-reason dialog -> " + dialogText);
        waitForAngular(1500);

        try { acceptSaveDialog(); } catch (Exception ignore) { }

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
        lastRejectPr = clickedText;
        lastRejectDiagnostics = clickedText + (toast.isEmpty() ? "; no message within 15s" : "");
        lastRejectSave = toast;
        System.out.println("PurchaseRequestApproval: reject -> " + lastRejectDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public boolean rejectPrClicked() { return lastRejectPr != null && lastRejectPr.startsWith("clicked \""); }
}
