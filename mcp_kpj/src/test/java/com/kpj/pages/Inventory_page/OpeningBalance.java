package com.kpj.pages.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; <b>Opening Balance</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → click <b>Get Items</b> (opens an item-picker dialog) → enter the
 * <b>Item Code</b> and <b>Item Name</b> → click <b>Search</b> → tick the checkbox on one item → click
 * <b>OK</b> (adds the item to the form) → enter its <b>Barcode</b>, <b>Batch Code</b>, <b>Expiry Date</b>,
 * <b>Loose Qty</b>, <b>Pack Cost</b> and <b>Pack MRP</b> → click <b>Save</b>.</p>
 *
 * <p>This screen has not been inspected live: every control is found by FUZZY matching (ng-model tail,
 * placeholder, or label containing the obvious word) rather than a pinned exact model, the way other
 * freshly-added screens in this framework are pinned once their real controls are dumped.
 * {@link #describeControls()} (and {@link #describeDialogControls()} for the item-picker dialog) dump
 * every visible control so the real ng-models can replace the fuzzy matches once this has run against
 * the live screen.</p>
 */
public class OpeningBalance extends BasePage {

    public OpeningBalance(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastGetItems = "",
            lastItemSearch = "", lastSearch = "", lastRows = "", lastTick = "", lastOk = "",
            lastDetails = "", lastSave = "", lastSaveDiagnostics = "";
    public boolean toastFromObserver = false;

    /** Shared JS helpers: visibility, text normalising, ng-model tail, top dialog, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const topDialog=()=>[...document.querySelectorAll("
          + "  '.modal,.modal-content,[role=dialog],.sweet-alert')].filter(vis).pop();"
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

    /** Application menu &rarr; Inventory &rarr; Opening Balance. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*inventory\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*opening\\s*balance\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__obMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__obMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("OpeningBalance.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__obMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("openingbalance")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    // ---- diagnostics -------------------------------------------------------

    /** Dump every visible control on the main form. */
    public String describeControls() { return describeControlsIn(""); }

    /** Dump every visible control inside the current top dialog (the item picker). */
    public String describeDialogControls() { return describeControlsIn("dialog"); }

    private String describeControlsIn(String scope) {
        Object r = page.evaluate("(sc) => {" + JS
                + " const root=(sc==='dialog')? (topDialog()||document) : document;"
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
                + " return [...new Set(out)].join('\\n'); }", scope);
        String s = r == null ? "" : r.toString();
        System.out.println("=== OpeningBalance CONTROLS (" + (scope.isEmpty() ? "form" : scope) + ") ===\n" + s);
        return s;
    }

    // ---- New / Get Items ---------------------------------------------------

    /** Click <b>New</b> and wait for the entry form to render. */
    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__obNew'; }");
        try {
            page.locator("#__obNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("OpeningBalance.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__obNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("OpeningBalance: " + lastNew);
        return lastNew;
    }

    /** The entry form looks open — judged on a Get Items button being visible. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('button,a')].filter(vis)"
                + "   .some(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /getitem/i.test(x.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    /** Click <b>Get Items</b> and wait for the item-picker dialog to open. */
    public String clickGetItems() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /getitem/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__obGetItems'; }");
        try {
            page.locator("#__obGetItems").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("OpeningBalance.clickGetItems: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__obGetItems'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the item picker opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Get Items'; }");
        lastGetItems = r == null ? "" : r.toString();
        System.out.println("OpeningBalance: " + lastGetItems);
        return lastGetItems;
    }

    public boolean itemPickerOpen() {
        return lastGetItems != null && lastGetItems.startsWith("the item picker opened");
    }

    // ---- item search (inside the dialog) -----------------------------------

    /**
     * Enter the <b>Item Code</b> and <b>Item Name</b> inside the item-picker dialog.
     *
     * <p>Both boxes are found within the dialog only, so a same-named field on the main form behind it
     * is never mistaken for these.</p>
     */
    public String enterItemCodeAndName(String code, String name) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, n] = a;"
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const boxes=[...d.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const codeEl=boxes.find(x=>/item.?code|^code$/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " const nameEl=boxes.filter(x=>x!==codeEl)"
                + "   .find(x=>/item.?name|^name$/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " const rc=codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-item-code-field)';"
                + " const rn=nameEl? setEl(nameEl,n)+' ['+(ngOf(nameEl)||'?')+']' : '(no-item-name-field)';"
                + " return 'ItemCode='+rc+' | ItemName='+rn; }",
                java.util.List.of(code, name));
        lastItemSearch = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("OpeningBalance: " + lastItemSearch);
        return lastItemSearch;
    }

    public boolean itemCodeAndNameEntered() {
        return lastItemSearch != null && !lastItemSearch.contains("(no-item-code-field)")
                && !lastItemSearch.contains("(no-item-name-field)");
    }

    /** Click <b>Search</b> inside the item-picker dialog. */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return;"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/SearchItem/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...d.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        lastSearch = "clicked Search -> " + rowCount() + " row(s) in the item picker";
        System.out.println("OpeningBalance: " + lastSearch);
        return lastSearch;
    }

    /** Rows currently listed inside the dialog (falls back to the whole page if no dialog is open). */
    /**
     * How many SELECTABLE items the picker lists.
     *
     * <p>Counting every visible {@code tr}/{@code .ui-grid-row} over-counts: the dialog's own column
     * FILTER row (bound to {@code Search.*}) is present whether or not a search found anything, and
     * reads as "1 row" even on zero results. Counted on the same per-row tick control
     * {@link #tickFirstItem()} would actually click, so a 0 here always means nothing is selectable.</p>
     */
    public int rowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " return [...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "   .filter(e=>e.closest('tr,.ui-grid-row')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); const root=d||document;"
                + " const rows=[...root.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /** Tick the checkbox on the first result row inside the dialog. */
    /**
     * Tick the first real item row's checkbox.
     *
     * <p>Raw JS ({@code cb.click()} / {@code $setViewValue}) reported success here but the box stayed
     * visually and functionally unchecked — Angular's own {@code OKClick()} still refused with "Please
     * Select Item !!!!". This checkbox needs a REAL browser click, so Playwright's own
     * {@link com.microsoft.playwright.Locator#check()} drives it instead of DOM manipulation: it scrolls
     * the control into view, clicks the actual hit-testable target, and waits for the check to land,
     * which JS alone was not reliably doing on this screen.</p>
     */
    public String tickFirstItem() {
        // The dialog's own SEARCH FILTERS carry checkboxes too - IsSpecialOrder, isPsychotropicdrug, and
        // the grid's own ChkSelectAll header - none of these select an ITEM. Excluding them by ng-model
        // leaves only real per-row checkboxes, and only one actually inside a table row counts.
        Object found = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '';"
                + " const cb=[...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "   .filter(e=>e.closest('tr,.ui-grid-row'))[0];"
                + " if(!cb) return '';"
                + " if(!cb.id) cb.id='__obItemCb';"
                + " return cb.id; }");
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) {
            Object sbResult = page.evaluate("() => {" + JS
                    + " const d=topDialog(); if(!d) return '';"
                    + " const sb=[...d.querySelectorAll('.ui-grid-selection-row-header-buttons')].filter(vis)[0];"
                    + " if(!sb) return '';"
                    + " const row=sb.closest('.ui-grid-row');"
                    + " const text=row? norm(row.textContent).slice(0,90) : '?';"
                    + " sb.click();"
                    + " return 'clicked the tick [selectButtonClick]; the row reads: '+text; }");
            lastTick = sbResult == null || sbResult.toString().isEmpty()
                    ? "(no tick control on any item - the picker has no results)" : sbResult.toString();
            waitForAngular(1200);
            System.out.println("OpeningBalance: tick -> " + lastTick);
            return lastTick;
        }

        String rowText = "?";
        try {
            Object rt = page.evaluate("(i) => { const e=document.getElementById(i);"
                    + " const row=e? e.closest('tr,.ui-grid-row') : null;"
                    + " return row? row.textContent.replace(/\\s+/g,' ').trim().slice(0,90) : '?'; }", id);
            rowText = rt == null ? "?" : rt.toString();
        } catch (Exception ignore) { }

        // The check has been observed to land inconsistently across otherwise-identical runs — checked
        // true on one run, false on the next, no code difference — pointing to real render/digest timing
        // in the live screen rather than a wrong selector. Retried rather than accepted on the first try.
        Object checkedNow = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.locator("#" + id).check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(6000));
            } catch (Exception e) {
                lastTick = "(check() failed: " + e.getMessage().split("\n")[0] + ")";
                System.out.println("OpeningBalance: tick -> " + lastTick);
                return lastTick;
            }
            waitForAngular(1200);
            checkedNow = page.evaluate("(i) => { const e=document.getElementById(i);"
                    + " return e? e.checked : false; }", id);
            if (Boolean.TRUE.equals(checkedNow)) break;
            System.out.println("OpeningBalance: tick attempt " + attempt + " did not register — retrying");
        }

        // The row also carries an Issue/Transfer radio (Item.issue_transfer) that OKClick() apparently
        // validates alongside the checkbox — ticking the box alone still drew "Please Select Item !!!!"
        // on a run where the box was confirmed visually checked. "Issue" is picked as the one applicable
        // to Opening Balance (this dialog is shared with genuine stock Issue/Transfer screens).
        String issueResult = "(not attempted — checkbox did not register)";
        if (Boolean.TRUE.equals(checkedNow)) {
            Object radioId = page.evaluate("(i) => { const e=document.getElementById(i);"
                    + " const row=e? e.closest('tr,.ui-grid-row') : null; if(!row) return '';"
                    + " const r=[...row.querySelectorAll(\"input[type=radio]\")]"
                    + "   .find(x=>/issue_transfer/i.test(x.getAttribute('ng-model')||'')"
                    + "        && /^\\s*issue\\s*$/i.test((x.value||'')+' '+"
                    + "          (x.closest('label')? x.closest('label').textContent : '')));"
                    + " if(!r){ const any=[...row.querySelectorAll(\"input[type=radio]\")]"
                    + "   .find(x=>/issue_transfer/i.test(x.getAttribute('ng-model')||'')); if(!any) return '';"
                    + "   if(!any.id) any.id='__obIssueRadio'; return any.id; }"
                    + " if(!r.id) r.id='__obIssueRadio'; return r.id; }", id);
            String rid = radioId == null ? "" : radioId.toString();
            if (rid.isEmpty()) {
                issueResult = "(no Issue/Transfer radio found in this row)";
            } else {
                try {
                    page.locator("#" + rid).check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000));
                    waitForAngular(500);
                    issueResult = "selected";
                } catch (Exception e) {
                    issueResult = "(check() failed: " + e.getMessage().split("\n")[0] + ")";
                }
                if ("__obIssueRadio".equals(rid)) {
                    page.evaluate("() => { const e=document.getElementById('__obIssueRadio'); if(e) e.removeAttribute('id'); }");
                }
            }
        }

        if ("__obItemCb".equals(id)) {
            page.evaluate("() => { const e=document.getElementById('__obItemCb'); if(e) e.removeAttribute('id'); }");
        }
        lastTick = "ticked the checkbox [Item.IsSelected] via Locator.check() -> checked="
                + checkedNow + "; Issue/Transfer -> " + issueResult + "; the row reads: " + rowText;
        System.out.println("OpeningBalance: tick -> " + lastTick);
        return lastTick;
    }

    public boolean itemTicked() {
        return lastTick != null && (lastTick.contains("checked=true") || lastTick.startsWith("clicked the tick"));
    }

    /** Click <b>OK</b> in the item-picker dialog — adds the ticked item(s) to the main form. */
    public String clickOk() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*ok\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        lastOk = r == null ? "" : r.toString();
        waitForAngular(1500);

        // The item picker refuses OK with a "Please Select Item !!!!" toast when nothing is really
        // selected in the model, and the dialog stays open — that toast is decisive over the click
        // itself having landed.
        Object refusal = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=toastEls().map(x=>norm(x.textContent)).find(x=>x); return t||''; }");
        String refusalText = refusal == null ? "" : refusal.toString();
        if (!refusalText.isEmpty()) {
            lastOk = "clicked, but the dialog refused: \"" + refusalText + "\"";
            System.out.println("OpeningBalance: ok -> " + lastOk);
            return lastOk;
        }

        // Wait for the item picker to actually go away, rather than assuming a click closed it.
        boolean closed = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Object stillOpen = page.evaluate("() => {" + JS
                    + " const d=topDialog(); return !!(d && norm(d.textContent).includes('Item Search')); }");
            if (!Boolean.TRUE.equals(stillOpen)) { closed = true; break; }
            page.waitForTimeout(400);
        }
        lastOk += closed ? "; the item picker closed" : "; the item picker is STILL open after 8s";
        System.out.println("OpeningBalance: ok -> " + lastOk);
        return lastOk;
    }

    public boolean okClicked() { return lastOk != null && lastOk.startsWith("clicked \""); }

    // ---- item detail fields (main form) -------------------------------------

    /**
     * Enter the item row's <b>Barcode</b>, <b>Batch Code</b>, <b>Expiry Date</b>, <b>Loose Qty</b>,
     * <b>Pack Cost</b> and <b>Pack MRP</b>.
     *
     * <p>Pinned from a live run: the added row's fields are {@code Itm.barcode}, {@code Itm.batchcode},
     * {@code Itm.expirydate}, {@code Itm.quantity} (not "loose qty" — that is only the label), {@code
     * Itm.rate} (not "pack cost") and {@code Itm.mrp} (not "pack mrp"). A word-matching fuzzy guess
     * missed three of these on the first live run for exactly that reason. Expiry Date is a datepicker,
     * so it gets the same click-clear-type treatment other date fields in this framework need — a plain
     * value assignment does not register with the picker widget.</p>
     */
    public String enterItemDetails(String barcode, String batchCode, String expiryDate, String looseQty,
                                     String packCost, String packMrp) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [bc, bch, qty, cost, mrp] = a;"
                + " const pinned=(m)=>document.querySelector(\"[ng-model='\"+m+\"']\");"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const pick=(re)=>boxes.find(x=>re.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " const barcodeEl=pinned('Itm.barcode') || pick(/barcode/i);"
                + " const batchEl=pinned('Itm.batchcode') || pick(/batch.?code|batch.?no/i);"
                + " const qtyEl=pinned('Itm.quantity') || pick(/loose.?qty|loose.?quantity|^quantity$/i);"
                + " const costEl=pinned('Itm.rate') || pick(/pack.?cost|^rate$/i);"
                + " const mrpEl=pinned('Itm.mrp') || pick(/pack.?mrp|^mrp$/i);"
                + " const set=(e,v,name)=>e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-'+name+'-field)';"
                + " return [set(barcodeEl,bc,'barcode'), set(batchEl,bch,'batch-code'),"
                + "   set(qtyEl,qty,'loose-qty'), set(costEl,cost,'pack-cost'),"
                + "   set(mrpEl,mrp,'pack-mrp')]; }",
                java.util.List.of(barcode, batchCode, looseQty, packCost, packMrp));
        waitForAngular(400);

        String expResult = typeItemExpiryDate(expiryDate);

        @SuppressWarnings("unchecked")
        java.util.List<String> parts = r instanceof java.util.List ? (java.util.List<String>) r
                : java.util.List.of("?", "?", "?", "?", "?");
        lastDetails = "Barcode=" + parts.get(0) + " | BatchCode=" + parts.get(1)
                + " | ExpiryDate=" + expResult + " | LooseQty=" + parts.get(2)
                + " | PackCost=" + parts.get(3) + " | PackMRP=" + parts.get(4);
        System.out.println("OpeningBalance: " + lastDetails);
        return lastDetails;
    }

    /** Type the item row's Expiry Date ({@code Itm.expirydate}) — a datepicker, cleared before typing. */
    private String typeItemExpiryDate(String value) {
        Object found = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"[ng-model='Itm.expirydate']\");"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__obExpiry';"
                + " return e.id; }");
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-expiry-date-field)";
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
        return (back == null ? "" : back.toString()) + " [Itm.expirydate]";
    }

    public boolean detailsEntered() {
        return lastDetails != null && !lastDetails.contains("(no-");
    }

    // ---- save ---------------------------------------------------------------

    /** A success message, judged strictly — configuration/transaction screens phrase refusals oddly too. */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Save</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String saveAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

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
        lastSaveDiagnostics = clickedText + (toast.isEmpty() ? "; no message within 15s" : "");
        lastSave = toast;
        System.out.println("OpeningBalance: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }
}
