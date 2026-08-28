package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Loan Supplier Return Note</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → select the <b>Supplier</b> → click <b>Search Item</b> (opens a "Search"
 * dialog — the same transaction-based dialog confirmed on the sibling {@link SupplierReturnNote}
 * screen) → enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → in <b>Search Details</b>
 * tick a transaction row's checkbox → its items populate <b>Item List</b> below → click <b>OK</b> → enter
 * the <b>Return Qty (Pack)</b> for each item → click <b>Save</b> → verify report generation → verify the
 * success toast.</p>
 *
 * <p>Confirmed via a screenshot: unlike {@link SupplierReturnNote}, where ticking a transaction only
 * populated Item List and each item's own checkbox still needed ticking separately, THIS screen's items
 * arrived already ticked once a transaction was selected — the screenshot shows every Item List row's
 * checkbox pre-checked. {@link #ensureItemsTicked()} still ticks any that aren't, defensively, in case
 * that does not hold for every transaction.</p>
 *
 * <p>Everything else has not been inspected live and is found by FUZZY matching, reusing every proven
 * pattern from that sibling screen and the rest of this module:</p>
 * <ul>
 *   <li>Checkbox ticks are done as ONE atomic JS call (find, click, sync Angular's
 *       {@code $setViewValue}, all in a single {@code page.evaluate}) — Playwright's own two-round-trip
 *       {@code Locator.check()} was observed elsewhere in this module to report success while the
 *       screen's own validation still saw nothing selected, because the live app re-renders the element
 *       between the "locate" and "act" round-trips.</li>
 *   <li>Date fields INSIDE this dialog are blurred via plain JS rather than an Escape keypress —
 *       confirmed on {@link SupplierReturnNote} that Escape can bubble up and close the whole dialog
 *       instead of just the calendar overlay.</li>
 *   <li>Ticking a transaction is tried across several rows in turn (not assumed to work on the first),
 *       since a given transaction may have nothing left to return.</li>
 *   <li>The Supplier select mirrors {@link Quotation}/{@link PurchaseOrder}'s: a plain {@code <select>}.</li>
 *   <li>"Return Qty (Pack)" is matched preferring a field whose label/model ALSO mentions "pack", in case
 *       a sibling "Loose" quantity field exists alongside it.</li>
 *   <li>"Verify report generation" mirrors the same exploratory check used on
 *       {@link PurchaseOrderApproval} and {@link SupplierReturnNote}.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form or whichever dialog is open) so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.
 */
public class LoanSupplierReturnNote extends BasePage {

    public LoanSupplierReturnNote(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastSupplier = "",
            lastSearchItem = "", lastDialogDates = "", lastDialogSearch = "", lastTransactionTick = "",
            lastItemTick = "", lastPickerOk = "", lastReturnQty = "", lastSave = "", lastSaveDiagnostics = "",
            lastReportSignal = "";

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
          + "const dataRows=(root)=>[...root.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
          + "  .filter(x=>norm(x.textContent))"
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));"
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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Loan Supplier Return Note. */
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
                    + "   .find(x=>/^\\s*loan\\s*supplier\\s*return\\s*note\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__lsrnMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__lsrnMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("LoanSupplierReturnNote.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__lsrnMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("loansupplierreturnnote") || route.contains("loanreturnnote")) return true;
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
        System.out.println("=== LoanSupplierReturnNote CONTROLS ===\n" + s);
        return s;
    }

    // ---- New / Supplier / Search Item ------------------------------------------

    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__lsrnNew'; }");
        try {
            page.locator("#__lsrnNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("LoanSupplierReturnNote.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__lsrnNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("LoanSupplierReturnNote: " + lastNew);
        return lastNew;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('select')].filter(vis)"
                + "   .some(x=>/supplier/i.test(tail(x)+' '+labelOf(x)))"
                + "   || [...document.querySelectorAll('button,a')].filter(vis)"
                + "     .some(x=>/search\\s*item/i.test(norm(x.textContent))); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    /** Select the <b>Supplier</b> — the first real option in whatever select carries "supplier". */
    public String selectSupplier() {
        Object plain = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__lsrnSupplier').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(x=>/supplier/i.test(tail(x)+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " e.id='__lsrnSupplier';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length}) : ''; }");
        String plainInfo = plain == null ? "" : plain.toString();
        if (plainInfo.isEmpty()) {
            lastSupplier = "(no Supplier select found)";
            System.out.println("LoanSupplierReturnNote: supplier -> " + lastSupplier);
            return lastSupplier;
        }
        try {
            java.util.Map<String, Object> m = parseFlatJson(plainInfo);
            page.locator("#__lsrnSupplier").selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setIndex(((Number) m.get("index")).intValue()));
            waitForAngular(900);
            Object t = page.evaluate("() => { const e=document.getElementById('__lsrnSupplier');"
                    + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
            page.evaluate("() => { const e=document.getElementById('__lsrnSupplier'); if(e) e.removeAttribute('id'); }");
            lastSupplier = (t == null ? "" : t.toString()) + " [select, " + plainInfo + "]";
        } catch (Exception e) {
            lastSupplier = "(select failed: " + e.getMessage().split("\n")[0] + ")";
        }
        System.out.println("LoanSupplierReturnNote: supplier -> " + lastSupplier);
        return lastSupplier;
    }

    public boolean supplierSelected() {
        return lastSupplier != null && !lastSupplier.isEmpty() && !lastSupplier.startsWith("(");
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

    /** Click <b>Search Item</b> — opens this screen's own transaction-based "Search" dialog (same shape
     *  as the sibling {@link SupplierReturnNote} screen's dialog). */
    public String clickSearchItem() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/search\\s*item/i.test(norm(x.textContent))"
                + "        || /OpenItemSearch|SearchItem|OpenGrndetailsList/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__lsrnSearchItem'; }");
        try {
            page.locator("#__lsrnSearchItem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("LoanSupplierReturnNote.clickSearchItem: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__lsrnSearchItem'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the Search dialog opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Search Item'; }");
        lastSearchItem = r == null ? "" : r.toString();
        System.out.println("LoanSupplierReturnNote: " + lastSearchItem);
        return lastSearchItem;
    }

    public boolean dialogOpen() {
        return lastSearchItem != null && lastSearchItem.startsWith("the Search dialog opened");
    }

    // ---- Search dialog: From Date / To Date / Search --------------------------

    /** Enter the <b>From Date</b>/<b>To Date</b> INSIDE the Search dialog (not the main page). */
    public String enterDialogDateRange(String from, String to) {
        String r1 = typeDialogDate(true, from);
        String r2 = typeDialogDate(false, to);
        lastDialogDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("LoanSupplierReturnNote: " + lastDialogDates);
        return lastDialogDates;
    }

    private String typeDialogDate(boolean from, String value) {
        String re = from ? "from" : "to";
        Object found = page.evaluate("(r) => {" + JS
                + " const d=topDialog(); if(!d) return '';"
                + " const boxes=[...d.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r+'.?date','i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__lsrn'+(r==='from'?'From':'To')+'Date';"
                + " return e.id; }", re);
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            // NOT Escape — confirmed on SupplierReturnNote that this dialog's date-picker calendar
            // overlay's Escape keydown can bubble up and close the whole dialog. A plain JS blur
            // dismisses the calendar without that risk.
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(500);
        Object back = page.evaluate("(i) => { const e=document.getElementById(i);"
                + " return e? (e.value||'') : '(gone)'; }", id);
        return back == null ? "" : back.toString();
    }

    public boolean dialogDatesEntered(String from, String to) {
        return lastDialogDates != null && lastDialogDates.contains("FromDate=" + from) && lastDialogDates.contains("ToDate=" + to);
    }

    /** Click <b>Search</b> inside the Search dialog and report how many transaction rows came back. */
    public String clickSearchInDialog() {
        page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return;"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))"
                + "        || /^(fn)?search/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        lastDialogSearch = "clicked Search -> " + transactionRowCount() + " transaction row(s)";
        System.out.println("LoanSupplierReturnNote: " + lastDialogSearch);
        return lastDialogSearch;
    }

    /** Rows in the "Search Details" grid — the OUTER grid (transactions), identified by a checkbox
     *  whose row also carries a Transaction No.-shaped cell (not the Item List grid below it). */
    public int transactionRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " const header=[...d.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*transaction\\s*no\\.?\\s*$/i.test(ownText(x)));"
                + " const grid=header? (header.closest('.ui-grid,table,div[class*=grid]')||d) : d;"
                + " return dataRows(grid).filter(r=>r.querySelector('input[type=checkbox]')"
                + "   && /\\d{4}/.test(norm(r.textContent))).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean transactionRowsFound() { return transactionRowCount() > 0; }

    // ---- tick a transaction row, then confirm its items populate ---------------

    /**
     * Tick a transaction row in <b>Search Details</b> — the requested step ("tick the checkbox for any
     * items to be chosen") did not name a specific transaction, so the first one is tried.
     *
     * <p>Same as the sibling {@link SupplierReturnNote} screen: ticking a transaction does not reliably
     * populate <b>Item List</b> (a given transaction may have nothing left to return), so each is tried
     * in turn until one does.</p>
     */
    public String tickFirstTransaction() {
        int total = transactionRowCount();
        int tries = Math.min(total, 10);
        for (int idx = 0; idx < tries; idx++) {
            String result = tickTransactionAtIndex(idx);
            if (!result.startsWith("checked=")) continue;
            waitForAngular(2500);
            if (itemListRowCount() > 0) {
                lastTransactionTick = "ticked transaction #" + (idx + 1) + " of " + total + " -> " + result;
                System.out.println("LoanSupplierReturnNote: transaction tick -> " + lastTransactionTick);
                return lastTransactionTick;
            }
            tickTransactionAtIndex(idx); // toggle back off before trying the next one
        }
        lastTransactionTick = "(none of the first " + tries + " of " + total
                + " transaction(s) produced any items in Item List)";
        System.out.println("LoanSupplierReturnNote: transaction tick -> " + lastTransactionTick);
        return lastTransactionTick;
    }

    private String tickTransactionAtIndex(int index) {
        Object r = page.evaluate("(idx) => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const header=[...d.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*transaction\\s*no\\.?\\s*$/i.test(ownText(x)));"
                + " const grid=header? (header.closest('.ui-grid,table,div[class*=grid]')||d) : d;"
                + " const rows=dataRows(grid).filter(rr=>rr.querySelector('input[type=checkbox]')"
                + "   && /\\d{4}/.test(norm(rr.textContent)));"
                + " const row=rows[idx]; if(!row) return '(no transaction row at index '+idx+')';"
                + " const cb=row.querySelector('input[type=checkbox]');"
                + " const rowText=norm(row.textContent).slice(0,90);"
                + " cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel');"
                + "      if(c){ c.$setViewValue(cb.checked); c.$render(); } }catch(err){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + " return 'checked='+cb.checked+'; the row reads: '+rowText; }", index);
        return r == null ? "" : r.toString();
    }

    public boolean transactionTicked() {
        return lastTransactionTick != null && lastTransactionTick.contains("checked=true");
    }

    /** How many item rows populated into <b>Item List</b> after ticking a transaction. */
    public int itemListRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " const header=[...d.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*item\\s*code\\s*$/i.test(ownText(x)));"
                + " const grid=header? (header.closest('.ui-grid,table,div[class*=grid]')||d) : d;"
                + " return dataRows(grid).filter(r=>r.querySelector('input[type=checkbox]')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean itemListPopulated() { return itemListRowCount() > 0; }

    /**
     * Ensure every item row's checkbox in <b>Item List</b> is ticked — confirmed via a screenshot that
     * this screen's items arrive already ticked once a transaction is selected (unlike
     * {@link SupplierReturnNote}, which needed a separate explicit tick), so this only ticks whichever
     * are NOT already checked. Purely defensive/best-effort — not itself a requested verification step.
     */
    public String ensureItemsTicked() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const header=[...d.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*item\\s*code\\s*$/i.test(ownText(x)));"
                + " const grid=header? (header.closest('.ui-grid,table,div[class*=grid]')||d) : d;"
                + " const rows=dataRows(grid).filter(rr=>rr.querySelector('input[type=checkbox]'));"
                + " let alreadyChecked=0, ticked=0;"
                + " for (const row of rows) {"
                + "   const cb=row.querySelector('input[type=checkbox]');"
                + "   if(!cb) continue;"
                + "   if(cb.checked) { alreadyChecked++; continue; }"
                + "   cb.click();"
                + "   try{ const c=angular.element(cb).controller('ngModel');"
                + "        if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + "   cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "   if(cb.checked) ticked++; }"
                + " return alreadyChecked+' of '+rows.length+' already ticked; ticked '+ticked+' more'; }");
        lastItemTick = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("LoanSupplierReturnNote: " + lastItemTick);
        return lastItemTick;
    }

    /**
     * Untick any item row whose Item Code repeats a code already kept — confirmed live: a transaction
     * can list the SAME item code across more than one row (e.g. separate batches), and since every
     * item arrives pre-ticked here, clicking OK with two rows sharing a code was refused outright with
     * "You Select Duplicate Item !!!!". Keeps the FIRST row for each code, unticks the rest.
     */
    public String deduplicateItemsByCode() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const header=[...d.querySelectorAll('div,span,th,td')].filter(vis)"
                + "   .find(x=>/^\\s*item\\s*code\\s*$/i.test(ownText(x)));"
                + " const grid=header? (header.closest('.ui-grid,table,div[class*=grid]')||d) : d;"
                + " const rows=dataRows(grid).filter(rr=>rr.querySelector('input[type=checkbox]'));"
                + " const codeOf=(row)=>{ const m=norm(row.textContent).match(/\\b[A-Za-z]{1,4}\\d{5,}\\b/);"
                + "   return m? m[0] : norm(row.textContent); };"
                + " const seen=new Set(); let unticked=0;"
                + " for (const row of rows) {"
                + "   const code=codeOf(row);"
                + "   const cb=row.querySelector('input[type=checkbox]');"
                + "   if(!cb) continue;"
                + "   if(seen.has(code)) {"
                + "     if(cb.checked) { cb.click();"
                + "       try{ const c=angular.element(cb).controller('ngModel');"
                + "            if(c){ c.$setViewValue(false); c.$render(); } }catch(err){}"
                + "       cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "       try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "       if(!cb.checked) unticked++; }"
                + "   } else { seen.add(code); } }"
                + " return 'unticked '+unticked+' duplicate-item-code row(s) of '+rows.length"
                + "   +' ('+seen.size+' unique code(s))'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("LoanSupplierReturnNote: " + result);
        return result;
    }

    /** Click <b>OK</b> in the Search dialog — adds the ticked items to the main form. */
    public String clickOkInDialog() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*ok\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        lastPickerOk = r == null ? "" : r.toString();
        waitForAngular(1500);

        Object refusal = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=toastEls().map(x=>norm(x.textContent)).find(x=>x); return t||''; }");
        String refusalText = refusal == null ? "" : refusal.toString();
        if (!refusalText.isEmpty()) {
            lastPickerOk = "clicked, but the dialog refused: \"" + refusalText + "\"";
            System.out.println("LoanSupplierReturnNote: picker ok -> " + lastPickerOk);
            return lastPickerOk;
        }

        boolean closed = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Object stillOpen = page.evaluate("() => {" + JS + " return !!topDialog(); }");
            if (!Boolean.TRUE.equals(stillOpen)) { closed = true; break; }
            page.waitForTimeout(400);
        }
        lastPickerOk += closed ? "; the Search dialog closed" : "; the Search dialog is STILL open after 8s";
        System.out.println("LoanSupplierReturnNote: picker ok -> " + lastPickerOk);
        return lastPickerOk;
    }

    public boolean okClicked() { return lastPickerOk != null && lastPickerOk.startsWith("clicked \""); }

    // ---- main form: Return Qty (Pack) per item ---------------------------------

    /**
     * Enter the <b>Return Qty (Pack)</b> for EVERY item row added to the main form. Prefers a field
     * whose label/model ALSO mentions "pack", in case a sibling "Loose" quantity field exists alongside
     * it — falls back to any return-qty field otherwise.
     */
    public String enterReturnQtyPackForAllItems(String qty) {
        Object r = page.evaluate("(v) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>/return.?qty|return.?quantity/i.test(tail(e)+' '+(e.placeholder||'')+' '+labelOf(e)));"
                + " const packBoxes=boxes.filter(e=>/pack/i.test(tail(e)+' '+(e.placeholder||'')+' '+labelOf(e)));"
                + " const target=packBoxes.length? packBoxes : boxes;"
                + " let count=0;"
                + " for (const e of target) { setEl(e, v); count++; }"
                + " return count+' Return Qty (Pack) field(s) set to '+v"
                + "   +(packBoxes.length? ' [pack-specific match]' : ' [generic return-qty match]'); }", qty);
        lastReturnQty = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("LoanSupplierReturnNote: " + lastReturnQty);
        return lastReturnQty;
    }

    public boolean returnQtyEntered() {
        return lastReturnQty != null && !lastReturnQty.startsWith("0 Return Qty");
    }

    // ---- save + report + toast -------------------------------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Save</b> and check whether a report was generated (a new browser tab/window, or a
     *  visible "report"-mentioning message) — same exploratory check as {@link PurchaseOrderApproval}. */
    public String saveAndVerifyReportGeneration() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?save/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Save button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        String clickedText = clicked == null ? "" : clicked.toString();
        waitForAngular(2500);
        try { acceptSaveDialog(); } catch (Exception ignore) { }

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
        lastReportSignal = clickedText + "; " + reportSignal;
        lastSaveDiagnostics = clickedText;
        System.out.println("LoanSupplierReturnNote: " + lastReportSignal);
        return lastReportSignal;
    }

    public boolean saveClicked() { return lastReportSignal != null && lastReportSignal.startsWith("clicked \""); }

    /** Wait for and return the success toast after Save. */
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
            if (toast.isEmpty()) page.waitForTimeout(400);
        }
        lastSave = toast;
        lastSaveDiagnostics = lastSaveDiagnostics + (toast.isEmpty() ? "; no message within 15s" : "");
        System.out.println("LoanSupplierReturnNote: save toast=\"" + toast + "\"");
        return toast;
    }
}
