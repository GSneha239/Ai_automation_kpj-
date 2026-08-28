package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Order Approval</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b>/<b>To Date</b> → click <b>Search</b> → verify results appear → open
 * <b>Modify</b> → on a row, click <b>Modify</b> to open it for editing → select the <b>Delivery Place</b>
 * → enter the <b>Planned Delivery Date</b> → in Purchase Order Details enter the <b>PO Quantity</b>,
 * <b>Free Qty</b>, <b>Unit Price</b> and <b>Net Unit Purchase Price</b> → click <b>Modify</b> → verify the
 * success toast.</p>
 *
 * <p>This screen has not been inspected live, and the request itself is ambiguous in the same way
 * {@link PurchaseRequestApproval} was — "add new tab - modify" does not confirm whether a separate tab
 * literally exists here (that sibling screen's identical-sounding step turned out to be the SAME action
 * button as the final step, and clicking it early fired the action prematurely and broke the later,
 * correctly-sequenced click), and "the row of data you want to select" does not name its target. Both are
 * handled the same defensive way as on that sibling screen:</p>
 * <ul>
 *   <li>{@link #clickModifyTab()} never clicks anything that could be the real per-row or final "Modify"
 *       action — only a genuinely separate tab/section control, reported plainly if none exists.</li>
 *   <li>{@link #clickModifyOnRow()} opens the first row's own Modify control.</li>
 *   <li>Per-item fields (PO Quantity, Free Qty, Unit Price, Net Unit Purchase Price) are pinned to the
 *       same real models confirmed live on {@link PurchaseOrder} ({@code Itm.poqty}, {@code Itm.freeqty},
 *       {@code Itm.costprize}, {@code Itm.netpurchaserate}), on the assumption Modify reuses the identical
 *       edit form New does — with the same fuzzy fallback in case it does not.</li>
 *   <li>Delivery Place and Planned Delivery Date are likewise pinned to {@code PurchaseOrder.deliverylocation}
 *       and {@code PurchaseOrder.planneddeliverydate}, confirmed live on {@link PurchaseOrder}.</li>
 *   <li>Checkbox/click interactions, where needed, are done as ONE atomic JS call — see
 *       {@link PurchaseRequestApproval} for why Playwright's own two-round-trip {@code Locator.check()}
 *       is unreliable on this live app.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form, or whichever dialog is open) so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.
 */
public class PurchaseOrderApproval extends BasePage {

    public PurchaseOrderApproval(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "",
            lastRows = "", lastModifyTab = "", lastRowModify = "", lastDeliveryPlace = "",
            lastPlannedDeliveryDate = "", lastItemFields = "", lastSave = "", lastSaveDiagnostics = "",
            lastPendingFilter = "", lastApproveTab = "", lastApprovedPoTick = "", lastApproveReport = "",
            lastConfirmDialog = "", lastConfirmOk = "", lastApproveSave = "", lastApproveDiagnostics = "";

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
          + "const pinnedLast=(m)=>{ const all=[...document.querySelectorAll(\"[ng-model='\"+m+\"']\")].filter(vis);"
          + "  return all.length? all[all.length-1] : null; };"
          + "const dataRows=()=>[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
          + "  .filter(x=>norm(x.textContent))"
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));"
          + "const ownText=(el)=>{ let t=''; for(const n of el.childNodes)"
          + "  if(n.nodeType===3) t+=n.textContent; return norm(t); };"
          // Confirmed on the sibling PurchaseRequestApproval screen: every checkbox's accessible
          // label/tail on these grids resolves to the WHOLE concatenated header row text, so a
          // text/label match cannot tell columns apart. Match by the header cell's own X position
          // instead — see PurchaseRequestApproval's class doc for the full story.
          + "const columnHeader=(text)=>{ const t=text.toLowerCase();"
          + "  return [...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "    .find(x=>ownText(x).toLowerCase()===t)"
          + "  || [...document.querySelectorAll('div,span,th,td,label,a')].filter(vis)"
          + "    .find(x=>norm(x.textContent).toLowerCase()===t); };"
          + "const cbInRow=(row,rect)=>[...row.querySelectorAll('input[type=checkbox]')].filter(vis)"
          + "  .find(c=>{ const rc=c.getBoundingClientRect(); const cx=rc.left+rc.width/2;"
          + "    return cx>=rect.left-5 && cx<=rect.right+5; });"
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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Purchase Order Approval. */
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
                    + "   .find(x=>/^\\s*purchase\\s*order\\s*approval\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__poaMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__poaMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PurchaseOrderApproval.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__poaMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("purchaseorderapproval") || route.contains("poapproval")) return true;
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
        System.out.println("=== PurchaseOrderApproval CONTROLS ===\n" + s);
        return s;
    }

    // ---- search --------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> — both cleared before typing (pickers arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("PurchaseOrderApproval: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__poa'+(r==='fromdate'?'From':'To');"
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
            // Escape alone was observed live to sometimes leave the datepicker calendar visually open
            // (still covering later controls, including the Approve button, in a screenshot taken well
            // after this call returned) — a real click well away from any control reliably dismisses it.
            page.mouse().click(5, 5);
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
        System.out.println("PurchaseOrderApproval: " + lastSearch);
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

    // ---- Modify (tab, then per-row action) -----------------------------------

    /**
     * Open <b>Modify</b> — reported plainly whether this is a real tab, a button, or absent, rather than
     * assumed. Deliberately never clicks the per-row or final "Modify" action button (only a genuinely
     * separate tab/section control) — see the class doc for why. Non-fatal: the flow continues to the
     * per-row click regardless.
     */
    public String clickModifyTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*modify\\s*$/i.test(norm(x.textContent))"
                + "        && !/modify/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Modify tab exists on this screen — \"Modify\" is the "
                + "same per-row / action button used in the later steps, so it is left untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Modify tab'; }");
        lastModifyTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseOrderApproval: " + lastModifyTab);
        return lastModifyTab;
    }

    /**
     * Click <b>Modify</b> on the first result row — the requested target ("the row of data you want to
     * select") was not named, so the first row's own Modify control is opened.
     */
    public String clickModifyOnRow() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=dataRows();"
                + " let target=null, btn=null;"
                + " for(const row of rows){"
                + "   const b=[...row.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/^\\s*modify\\s*$/i.test(norm(x.textContent))"
                + "          || /modify/i.test(x.getAttribute('ng-click')||''));"
                + "   if(b){ target=row; btn=b; break; } }"
                + " if(!btn) return '(no Modify control found in any row)';"
                + " const text=norm(target.textContent).slice(0,90);"
                + " btn.click();"
                + " return 'clicked Modify on a row: '+text; }");
        lastRowModify = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("PurchaseOrderApproval: " + lastRowModify);
        return lastRowModify;
    }

    public boolean rowModifyClicked() {
        return lastRowModify != null && lastRowModify.startsWith("clicked Modify on a row");
    }

    // ---- Delivery Place / Planned Delivery Date -------------------------------

    /**
     * Select the <b>Delivery Place</b> — pinned to {@code PurchaseOrder.deliverylocation} (confirmed live
     * on {@link PurchaseOrder}), with a fuzzy fallback in case Modify uses a different form.
     */
    public String selectDeliveryPlace() {
        lastDeliveryPlace = selectFirstRealOption("delivery.*(place|location)", "__poaDeliveryPlace");
        System.out.println("PurchaseOrderApproval: delivery place -> " + lastDeliveryPlace);
        return lastDeliveryPlace;
    }

    public boolean deliveryPlaceSelected() {
        return lastDeliveryPlace != null && !lastDeliveryPlace.isEmpty() && !lastDeliveryPlace.startsWith("(");
    }

    private String selectFirstRealOption(String labelRegex, String tempId) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [re, id] = a;"
                + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                + " const d=topDialog(); const root=d||document;"
                + " const rx=new RegExp(re,'i');"
                + " const e=[...root.querySelectorAll('select')].filter(vis)"
                + "   .find(x=>rx.test(tail(x)+' '+labelOf(x)));"
                + " if(!e) return '(no matching dropdown)';"
                + " e.id=id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length})"
                + "   : '(the list has no real option)'; }",
                java.util.List.of(labelRegex, tempId));
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

    /**
     * Enter the <b>Planned Delivery Date</b> — pinned to {@code PurchaseOrder.planneddeliverydate}
     * (confirmed live on {@link PurchaseOrder}), typed via Playwright's Locator like every other date
     * picker in this module.
     */
    public String enterPlannedDeliveryDate(String value) {
        Object found = page.evaluate("() => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=pinnedLast('PurchaseOrder.planneddeliverydate')"
                + "   || boxes.find(x=>/planned.?delivery.?date|delivery.?date/i"
                + "     .test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__poaPlannedDate';"
                + " return e.id; }");
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) {
            lastPlannedDeliveryDate = "(no-planned-delivery-date-field)";
            System.out.println("PurchaseOrderApproval: " + lastPlannedDeliveryDate);
            return lastPlannedDeliveryDate;
        }
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            box.press("Escape");
        } catch (Exception e) {
            lastPlannedDeliveryDate = "(typing failed: " + e.getMessage().split("\n")[0] + ")";
            return lastPlannedDeliveryDate;
        }
        waitForAngular(500);
        Object back = page.evaluate("(i) => { const e=document.getElementById(i);"
                + " return e? (e.value||'') : '(gone)'; }", id);
        lastPlannedDeliveryDate = back == null ? "" : back.toString();
        System.out.println("PurchaseOrderApproval: planned delivery date -> " + lastPlannedDeliveryDate);
        return lastPlannedDeliveryDate;
    }

    public boolean plannedDeliveryDateEntered() {
        return lastPlannedDeliveryDate != null && !lastPlannedDeliveryDate.isEmpty()
                && !lastPlannedDeliveryDate.startsWith("(");
    }

    // ---- Purchase Order Details: PO Quantity / Free Qty / Unit Price / Net Unit Purchase Price --

    /**
     * Enter the <b>PO Quantity</b>, <b>Free Qty</b>, <b>Unit Price</b> and <b>Net Unit Purchase Price</b>
     * — pinned to the same real models confirmed live on {@link PurchaseOrder} ({@code Itm.poqty},
     * {@code Itm.freeqty}, {@code Itm.costprize}, {@code Itm.netpurchaserate}), on the assumption Modify
     * reuses the identical edit form New does, with a fuzzy fallback (and the same "match Net Unit
     * Purchase Price BEFORE the plain Unit Price field, excluded from its pool" fix that screen needed —
     * see its class doc) in case it does not.
     */
    public String enterItemFields(String poQty, String freeQty, String unitPrice, String netUnitPrice) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [pq, fq, up, nup] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const pickLast=(re,pool)=>{ const m=(pool||boxes).filter(x=>re.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + "   return m.length? m[m.length-1] : null; };"
                + " const qtyEl=pinnedLast('Itm.poqty') || pickLast(/^po.?qty$|^po.?quantity$|poqty|^quantity$/i);"
                + " const freeEl=pinnedLast('Itm.freeqty') || pickLast(/free.?qty|free.?quantity/i);"
                + " const netEl=pinnedLast('Itm.netpurchaserate') || pinnedLast('Itm.netunitprice')"
                + "   || pickLast(/net.*purchase.*rate|net.*unit.*price|net.?rate/i);"
                + " const remaining=boxes.filter(x=>x!==netEl);"
                + " const priceEl=pinnedLast('Itm.costprize') || pinnedLast('Itm.rate')"
                + "   || pickLast(/unit.?price|^rate$|costprize|cost.?pri[cz]e/i, remaining);"
                + " const set=(e,v,name)=>e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-'+name+'-field)';"
                + " return JSON.stringify(["
                + "   set(qtyEl,pq,'po-quantity'), set(freeEl,fq,'free-qty'),"
                + "   set(priceEl,up,'unit-price'), set(netEl,nup,'net-unit-purchase-price') ]); }",
                java.util.List.of(poQty, freeQty, unitPrice, netUnitPrice));
        String json = r == null ? "[]" : r.toString();
        String[] fields = parseJsonArray(json);
        lastItemFields = "POQuantity=" + at(fields, 0) + " | FreeQty=" + at(fields, 1)
                + " | UnitPrice=" + at(fields, 2) + " | NetUnitPurchasePrice=" + at(fields, 3);
        waitForAngular(500);
        System.out.println("PurchaseOrderApproval: " + lastItemFields);
        return lastItemFields;
    }

    public boolean itemFieldsEntered() {
        return lastItemFields != null && !lastItemFields.contains("(no-");
    }

    private static String at(String[] a, int i) { return i < a.length ? a[i] : "?"; }

    /**
     * Minimal parser for a flat JSON string array — no library, known shape.
     *
     * <p>Handles backslash-escaped characters WITHIN a quoted element (e.g. {@code \"}) by consuming the
     * escape and appending the literal character, rather than merely detecting it wasn't a real delimiter
     * and leaving the backslash in the output. An earlier version did the latter — it worked as long as no
     * element's own text ever contained a quote character, until one legitimately did (a button's own
     * quoted label, e.g. {@code clicked "Approve"}), which corrupted both that element's text (leaving a
     * stray backslash) AND the quote-tracking state for the rest of the string, silently misaligning which
     * array index held which value.</p>
     */
    private static String[] parseJsonArray(String json) {
        String body = json.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        if (body.isBlank()) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && inQuotes && i + 1 < body.length()) { cur.append(body.charAt(++i)); continue; }
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // ---- Modify (final action) + toast ----------------------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("modified") || t.contains("updated");
    }

    /** Click <b>Modify</b> (the confirming action button) and return the toast. */
    public String clickModifyAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*modify\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?modify/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Modify button)';"
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
        System.out.println("PurchaseOrderApproval: modify -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public boolean modifyClicked() { return lastSaveDiagnostics != null && lastSaveDiagnostics.startsWith("clicked \""); }

    // ---- Approve (second tab of this same screen) ----------------------------

    /**
     * Tick the <b>Pending PO</b> search filter, if present — confirmed live alongside <b>All PO</b>,
     * <b>Approved PO</b> and <b>Rejected PO</b> filter checkboxes on the search form. Restricting the
     * search to pending POs means every row the Approve tab sees is safe to approve, sidestepping the
     * per-row "is this one already decided" detection {@link PurchaseRequestApproval} needed. Best-effort
     * and non-fatal — reported plainly if this screen turns out not to have the filter.
     */
    public String tickPendingPoFilter() {
        Object r = page.evaluate("() => {" + JS
                + " const cb=pinnedLast('POAList.PendingPO');"
                + " if(!cb) return '(no Pending PO filter checkbox found)';"
                + " if(!cb.checked) cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel');"
                + "      if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return cb.checked ? 'ticked the Pending PO filter'"
                + "                   : '(the Pending PO filter would not stay checked)'; }");
        lastPendingFilter = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("PurchaseOrderApproval: " + lastPendingFilter);
        return lastPendingFilter;
    }

    /**
     * Open <b>Approve</b> — same defensive pattern as {@link #clickModifyTab()}: never clicks anything
     * that could be the real "Approve" action button ({@code savePOAuthoLevel()}, confirmed live on the
     * list view), only a genuinely separate tab/section control, reported plainly if none exists.
     */
    public String clickApprovePoTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*approve\\s*$/i.test(norm(x.textContent))"
                + "        && !/approve/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Approve tab exists on this screen — \"Approve\" is the "
                + "same action button used in the later steps, so it is left untouched here to avoid "
                + "firing the approval before the checkbox is ticked)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Approve tab'; }");
        lastApproveTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseOrderApproval: " + lastApproveTab);
        return lastApproveTab;
    }

    /**
     * Tick the <b>Approved PO</b> checkbox for the respective item(s) — "for the respective items only",
     * i.e. one specific row, not a bulk tick of every row (that turned out to be WRONG on the sibling
     * PurchaseRequestApproval screen: it ticked BOTH the Approved and Reject columns for all 40 rows at
     * once, since every checkbox's accessible label there resolved to the whole concatenated header row).
     *
     * <p>Confirmed live: unlike PurchaseRequestApproval's grid, this screen's per-row checkboxes have
     * unambiguous {@code ng-model} names — {@code PO.IsApprove}, {@code PO.isverified},
     * {@code PO.IsCancelled} — so column-header/geometry matching (needed there because every checkbox's
     * accessible label resolved to the same concatenated header text) is unnecessary here; a direct
     * {@code ng-model} tail match on "isapprove" is simpler and more robust.</p>
     */
    public String tickApprovedPoCheckboxes() {
        tickApprovedPoAndClickApprove();
        return lastApprovedPoTick;
    }

    public boolean approvedPoTicked() {
        return lastApprovedPoTick != null && lastApprovedPoTick.startsWith("ticked ")
                && !lastApprovedPoTick.startsWith("ticked 0 of");
    }

    /**
     * Tick the <b>Approved PO</b> checkbox AND click <b>Approve</b> in ONE atomic {@code page.evaluate()}
     * call, then check whether a report was generated — a new browser tab/window (a common pattern for an
     * auto-generated PDF), or a visible "report"-mentioning message.
     *
     * <p>Confirmed live: a gap between ticking the checkbox and clicking Approve — even just the time for
     * a Java round-trip and a screenshot, well under a second — let the tick silently revert (confirmed
     * via a diagnostic screenshot showing the checkbox unticked again, despite an in-script readback 600ms
     * after the click still reading {@code checked=true}). Something in this live app's own digest/refresh
     * cycle resets it shortly after, on a timescale longer than fits inside one script but shorter than a
     * Java round-trip. Merging the tick and the click into one call — the same "atomic, no round-trip"
     * fix used everywhere else in this module for a checkbox race — closes that window entirely.</p>
     */
    public String tickApprovedPoAndClickApprove() {
        int pagesBefore = page.context().pages().size();

        // Playwright auto-dismisses (Cancels) any unhandled native alert()/confirm()/prompt() dialog by
        // default, silently and with no trace in the DOM — the previous total silence after clicking
        // Approve (no toast, no DOM modal, checkbox visibly reverted moments later) is consistent with
        // savePOAuthoLevel() opening a native confirm() that got auto-Cancelled before this code ever saw
        // it, cancelling the approval it was meant to confirm. "In dialog pop up - confirmation, click OK"
        // is very likely this native dialog, not a DOM/Bootstrap modal. Registered for the click below so
        // it gets ACCEPTED (= OK) instead.
        java.util.List<String> dialogsSeen = new java.util.ArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Dialog> dialogHandler = d -> {
            dialogsSeen.add(d.type() + ": " + d.message());
            try { d.accept(); } catch (Exception ignore) { }
        };
        page.onDialog(dialogHandler);

        // Diagnostic: does clicking Approve fire any network request at all?
        java.util.List<String> requestsSeen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Request> requestHandler = req -> {
            if (!"get".equalsIgnoreCase(req.method()) || req.url().contains("/api/") || req.url().contains(".ashx")
                    || req.url().contains(".asmx")) {
                requestsSeen.add(req.method() + " " + req.url());
            }
        };
        page.onRequest(requestHandler);

        Object r;
        try {
        r = page.evaluate("() => {" + JS
                + " const row=dataRows().find(x=>{"
                + "   const cb=[...x.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "     .find(e=>/^isapprove$/i.test(tail(e)));"
                + "   return cb && !cb.checked && !cb.disabled; });"
                + " if(!row) return JSON.stringify(['(no pending row left to tick — every PO in range is "
                + "already approved)', '']);"
                + " const cb=[...row.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .find(e=>/^isapprove$/i.test(tail(e)));"
                + " if(!cb.checked) cb.click();"
                + " try{ const c=angular.element(cb).controller('ngModel');"
                + "      if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + " cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + " let scopeVal='?';"
                + " try{ const sc=angular.element(cb).scope(); scopeVal=JSON.stringify(sc.PO? sc.PO.IsApprove : '(no sc.PO)'); }catch(err){ scopeVal='(scope read failed: '+err+')'; }"
                + " const tickResult=(cb.checked? 'ticked 1 of 1 Approved PO checkbox' : '(the Approved PO checkbox would not stay checked)')"
                + "   +' [scope PO.IsApprove='+scopeVal+']';"
                + " if(!cb.checked) return JSON.stringify([tickResult, '']);"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*approve\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /savePOAuthoLevel/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return JSON.stringify([tickResult, '(no Approve button)']);"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " const clickResult='clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']';"
                + " return JSON.stringify([tickResult, clickResult]); }");
        waitForAngular(2000);
        } finally {
            page.offDialog(dialogHandler);
            page.offRequest(requestHandler);
        }
        System.out.println("PurchaseOrderApproval: network requests seen during/after the Approve click: "
                + (requestsSeen.isEmpty() ? "(none)" : requestsSeen));
        String[] parts = parseJsonArray(r == null ? "[]" : r.toString());
        lastApprovedPoTick = parts.length > 0 ? parts[0] : "";
        String clickedText = parts.length > 1 ? parts[1] : "";
        lastConfirmDialog = dialogsSeen.isEmpty() ? "" : String.join(" | ", dialogsSeen);
        if (!dialogsSeen.isEmpty()) {
            lastConfirmOk = "accepted (clicked OK on) the native dialog: " + lastConfirmDialog;
            System.out.println("PurchaseOrderApproval: " + lastConfirmOk);
        }
        waitForAngular(4000);

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
        lastApproveReport = clickedText + "; " + reportSignal;
        System.out.println("PurchaseOrderApproval: " + lastApproveReport);
        return lastApproveReport;
    }

    public boolean approveClicked() { return lastApproveReport != null && lastApproveReport.startsWith("clicked \""); }

    /**
     * The Approve click already happened atomically inside {@link #tickApprovedPoAndClickApprove()} (via
     * {@link #tickApprovedPoCheckboxes()}) — see that method's doc for why the tick and the click cannot
     * be split across two round-trips. This just returns the result already computed then, so the test
     * can still report "tick" and "click Approve / verify report" as two separate steps.
     */
    public String clickApproveAndVerifyReportGeneration() { return lastApproveReport; }

    /**
     * Whether a confirmation dialog appeared after clicking Approve. Checks the NATIVE browser dialog
     * first — see {@link #tickApprovedPoAndClickApprove()}'s doc: Playwright silently auto-Cancels any
     * unhandled {@code confirm()}, which is the likely reason clicking Approve previously produced no
     * visible effect at all — already captured and accepted there, before this method is even called.
     * Falls back to a DOM/Bootstrap modal check in case it turns out to be that instead.
     */
    public boolean confirmationDialogOpen() {
        if (lastConfirmDialog != null && !lastConfirmDialog.isEmpty()) {
            System.out.println("PurchaseOrderApproval: a native confirmation dialog was seen: " + lastConfirmDialog);
            return true;
        }
        Object r = page.evaluate("() => {" + JS + " return !!topDialog(); }");
        boolean domOpen = Boolean.TRUE.equals(r);
        lastConfirmDialog = domOpen ? "a DOM confirmation dialog is open" : "no dialog was seen (native or DOM)";
        System.out.println("PurchaseOrderApproval: " + lastConfirmDialog);
        return domOpen;
    }

    /**
     * Click <b>OK</b> in the confirmation dialog pop-up — already done if it was the native dialog (auto-
     * accepted inside {@link #tickApprovedPoAndClickApprove()}, since a native {@code confirm()} must be
     * answered the instant it fires, synchronously, with no chance for a later, separate click). Falls
     * back to clicking OK/Yes in a DOM modal if one is actually open instead.
     */
    public String clickOkInConfirmationDialog() {
        if (lastConfirmOk != null && lastConfirmOk.startsWith("accepted")) return lastConfirmOk;
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*ok\\s*$/i.test(norm(x.textContent))"
                + "        || /^\\s*yes\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        lastConfirmOk = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("PurchaseOrderApproval: " + lastConfirmOk);
        return lastConfirmOk;
    }

    public boolean confirmOkClicked() {
        return lastConfirmOk != null && (lastConfirmOk.startsWith("clicked \"") || lastConfirmOk.startsWith("accepted"));
    }

    /** Wait for and return the success toast after confirming Approve. */
    public String waitForApproveToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }

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
        lastApproveSave = toast;
        lastApproveDiagnostics = toast.isEmpty() ? "no message within 15s" : "";
        System.out.println("PurchaseOrderApproval: approve toast=\"" + toast + "\"");
        return toast;
    }
}
