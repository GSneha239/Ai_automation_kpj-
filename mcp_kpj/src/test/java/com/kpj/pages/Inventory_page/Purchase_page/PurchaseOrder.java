package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Order</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → select the <b>Supplier</b> → select <b>Purchase Based On</b> → click
 * <b>Search Item</b> (opens the "Item Search" dialog) → click <b>Search</b> → tick the checkbox beside
 * the item code → click <b>OK</b> → verify the result appears → enter the <b>PR Quantity</b>,
 * <b>Free Qty</b>, <b>Unit Price</b> and <b>Net Unit Purchase Price</b> → in the Purchase Order section
 * select the <b>Delivery Place</b> and <b>PR Type</b>, enter the <b>Planned Delivery Date</b> and
 * <b>Delivery Lead Time</b> → click <b>Save</b>.</p>
 *
 * <p>This screen has not been inspected live. It is a sibling of {@link ItemEnquiry}, {@link Quotation}
 * and {@link PurchaseRequest} in the same Purchase module, so proven fixes from those screens are applied
 * from the start rather than re-discovered:</p>
 * <ul>
 *   <li>"Search Item" is expected to open the same shared "Item Search" dialog confirmed on every sibling
 *       screen ({@code ItemSearch.*} models, {@code SearchItem()}), including its blank-search-returns-
 *       nothing behaviour and its checkbox beside the Item Code column.</li>
 *   <li>Checkbox ticks are done as ONE atomic JS call (find, click, sync Angular's
 *       {@code $setViewValue}, all in a single {@code page.evaluate}) — Playwright's own two-round-trip
 *       {@code Locator.check()} was observed on Item Enquiry to report success while the dialog's own
 *       validation still saw nothing selected, because the live app re-renders the row between the
 *       "locate" and "act" round-trips.</li>
 *   <li>Per-item fields are matched against the LAST visible element for a given model, not the first —
 *       Quotation's identical two-item-row screen showed {@code querySelector} on a repeated
 *       {@code ng-repeat} model name silently grabbing an EARLIER row instead of the one just added.</li>
 *   <li>The Supplier select mirrors Quotation's: a plain {@code <select>} first, falling back to the
 *       button+checkbox-panel widget confirmed live on Item Enquiry.</li>
 *   <li>"Net Unit Purchase Price" is matched BEFORE the plain "Unit Price" field and excluded from that
 *       field's own candidate pool, since "unit price" is a text/model substring of "net unit purchase
 *       price" and a naive single fuzzy pass would let the more specific field steal the generic one's
 *       match (or vice versa).</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form or whichever dialog is open) so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.
 */
public class PurchaseOrder extends BasePage {

    public PurchaseOrder(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastSupplier = "",
            lastPurchaseBasedOn = "", lastSearchItem = "", lastPickerSearch = "", lastPickerRows = "",
            lastPickerTick = "", lastPickerOk = "", lastItemFields = "", lastDeliveryPlace = "",
            lastPrType = "", lastPlannedDeliveryDate = "", lastLeadTime = "",
            lastSave = "", lastSaveDiagnostics = "";

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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Purchase Order. */
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
                    + "   .find(x=>/^\\s*purchase\\s*order\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__poMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__poMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PurchaseOrder.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__poMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("purchaseorder")) return true;
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
        System.out.println("=== PurchaseOrder CONTROLS ===\n" + s);
        return s;
    }

    // ---- New / Supplier / Purchase Based On ------------------------------

    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__poNew'; }");
        try {
            page.locator("#__poNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("PurchaseOrder.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__poNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("PurchaseOrder: " + lastNew);
        return lastNew;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('select')].filter(vis)"
                + "   .some(x=>/supplier/i.test(tail(x)+' '+labelOf(x)))"
                + "   || [...document.querySelectorAll('button,a')].filter(vis)"
                + "     .some(x=>/select\\s*supplier/i.test(norm(x.textContent))); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    /**
     * Select the <b>Supplier</b> — a plain {@code <select>} first, falling back to the button+checkbox-
     * panel widget confirmed live on Item Enquiry ("Select Supplier" opens a popover table headed
     * "Select | Supplier Name"). Either path's tick is done as ONE atomic JS call — see the class doc.
     */
    public String selectSupplier() {
        Object plain = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__poSupplier').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(x=>/supplier/i.test(tail(x)+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " e.id='__poSupplier';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length}) : ''; }");
        String plainInfo = plain == null ? "" : plain.toString();
        if (!plainInfo.isEmpty()) {
            try {
                java.util.Map<String, Object> m = parseFlatJson(plainInfo);
                page.locator("#__poSupplier").selectOption(
                        new com.microsoft.playwright.options.SelectOption()
                                .setIndex(((Number) m.get("index")).intValue()));
                waitForAngular(900);
                Object t = page.evaluate("() => { const e=document.getElementById('__poSupplier');"
                        + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
                page.evaluate("() => { const e=document.getElementById('__poSupplier'); if(e) e.removeAttribute('id'); }");
                lastSupplier = (t == null ? "" : t.toString()) + " [select, " + plainInfo + "]";
                System.out.println("PurchaseOrder: supplier -> " + lastSupplier);
                return lastSupplier;
            } catch (Exception e) {
                lastSupplier = "(select failed: " + e.getMessage().split("\n")[0] + ")";
                return lastSupplier;
            }
        }

        // Fall back to the button+panel widget.
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/select\\s*supplier/i.test(norm(x.textContent))"
                + "        || /toggleDropdown/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__poSupplierBtn'; }");
        try {
            page.locator("#__poSupplierBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            lastSupplier = "(no Supplier select and the Select Supplier button click failed: "
                    + e.getMessage().split("\n")[0] + ")";
            System.out.println("PurchaseOrder: supplier -> " + lastSupplier);
            return lastSupplier;
        }
        page.evaluate("() => { const e=document.getElementById('__poSupplierBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);

        Object r = page.evaluate("() => {" + JS
                + " const find=() => {"
                + "   const header=[...document.querySelectorAll('th,td,div,span')].filter(vis)"
                + "     .find(x=>/^\\s*supplier\\s*name\\s*$/i.test(norm(x.textContent)));"
                + "   const table=header? header.closest('table,.ui-grid,div') : null;"
                + "   const inTable=table? [...table.querySelectorAll('input[type=checkbox]')].filter(vis)[0] : null;"
                + "   const cb=inTable || [...document.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "     .find(e2=>/supplier/i.test(tail(e2)+' '+labelOf(e2))"
                + "       || (e2.closest('tr,li,div') && /supplier/i.test(norm(e2.closest('tr,li,div').textContent))));"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__poSupplierCb';"
                + "   return cb.id; };"
                + " for (let attempt = 0; attempt < 8; attempt++) {"
                + "   const id=find();"
                + "   if(!id) continue;"
                + "   const cb=document.getElementById(id);"
                + "   if(!cb) continue;"
                + "   const row=cb.closest('tr,li,div');"
                + "   const rowText=row? norm(row.textContent).slice(0,90) : '?';"
                + "   if(!cb.checked) cb.click();"
                + "   try{ const c=angular.element(cb).controller('ngModel');"
                + "        if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + "   cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "   if(cb.checked) return 'ticked the supplier checkbox in the panel -> checked='"
                + "     +cb.checked+'; the row reads: '+rowText; }"
                + " return '(the panel checkbox would not stay attached across 8 attempts, or no panel opened)'; }");
        lastSupplier = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PurchaseOrder: supplier -> " + lastSupplier);
        return lastSupplier;
    }

    public boolean supplierSelected() {
        return lastSupplier != null && !lastSupplier.isEmpty() && !lastSupplier.startsWith("(");
    }

    /**
     * Select <b>Purchase Based On</b> — tries a {@code <select>} first (first real option), then falls
     * back to a radio group, since the request did not name which widget this screen actually uses.
     */
    public String selectPurchaseBasedOn() {
        String selectAttempt = selectFirstRealOption("based.?on|purchase.?based", "__poBasedOn");
        if (selectAttempt != null && !selectAttempt.startsWith("(")) {
            lastPurchaseBasedOn = selectAttempt;
            System.out.println("PurchaseOrder: purchase based on -> " + lastPurchaseBasedOn);
            return lastPurchaseBasedOn;
        }

        Object r = page.evaluate("() => {" + JS
                + " const radios=[...document.querySelectorAll('input[type=radio]')].filter(vis)"
                + "   .filter(x=>/based.?on|purchase.?based/i.test(ngOf(x)+' '+labelOf(x)"
                + "     +' '+(x.closest('.form-group,.row')? norm(x.closest('.form-group,.row').textContent):'')));"
                + " const first=radios[0]; if(!first) return '(no Purchase Based On control found)';"
                + " if(!first.checked) first.click();"
                + " try{ const c=angular.element(first).controller('ngModel');"
                + "      if(c){ c.$setViewValue(first.value); c.$render(); } }catch(err){}"
                + " first.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return 'selected radio: '+labelOf(first)+' ['+(first.value||'')+']'; }");
        lastPurchaseBasedOn = r == null ? "" : r.toString();
        waitForAngular(700);
        System.out.println("PurchaseOrder: purchase based on -> " + lastPurchaseBasedOn);
        return lastPurchaseBasedOn;
    }

    public boolean purchaseBasedOnSelected() {
        return lastPurchaseBasedOn != null && !lastPurchaseBasedOn.isEmpty() && !lastPurchaseBasedOn.startsWith("(");
    }

    // ---- Search Item (the shared "Item Search" picker) -----------------------

    /**
     * Click <b>Search Item</b> — expected to open the same shared "Item Search" picker dialog confirmed
     * live on {@link ItemEnquiry} and reused on {@link Quotation}/{@link PurchaseRequest} (same
     * {@code ItemSearch.*}/{@code SearchItem()} models).
     */
    public String clickSearchItem() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/search\\s*item/i.test(norm(x.textContent))"
                + "        || /OpenItemSearch|SearchItem/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__poSearchItem'; }");
        try {
            page.locator("#__poSearchItem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("PurchaseOrder.clickSearchItem: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__poSearchItem'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the item search dialog opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Search Item'; }");
        lastSearchItem = r == null ? "" : r.toString();
        System.out.println("PurchaseOrder: " + lastSearchItem);
        return lastSearchItem;
    }

    public boolean itemPickerOpen() {
        return lastSearchItem != null && lastSearchItem.startsWith("the item search dialog opened");
    }

    /** Click <b>Search</b> inside the item-picker dialog, with the blank-search-returns-nothing fallback. */
    public String clickSearchAndVerifyRows() {
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
        System.out.println("PurchaseOrder: " + lastPickerSearch);
    }

    /** How many SELECTABLE items the picker lists — same filter/row-scoping proven on sibling screens. */
    public int pickerRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " return [...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "   .filter(e=>e.closest('tr,.ui-grid-row')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean rowsFound() {
        return lastPickerRows != null && !lastPickerRows.startsWith("0 ");
    }

    /**
     * Tick the first real item row's checkbox, beside the Item Code column.
     *
     * <p>Find-and-click in ONE atomic JS call — see the class doc for why.</p>
     */
    public String tickFirstItemInPicker() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const d=topDialog(); if(!d) return '';"
                + "   const cb=[...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "     .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "     .filter(e=>e.closest('tr,.ui-grid-row'))[0];"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__poItemCb';"
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
                + " return '(the checkbox would not stay attached across 8 attempts, or the picker has no results)'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(800);
        lastPickerTick = result.startsWith("checked=")
                ? "ticked the checkbox beside the item code -> " + result
                : "(" + result + ")";
        System.out.println("PurchaseOrder: picker tick -> " + lastPickerTick);
        return lastPickerTick;
    }

    public boolean pickerItemTicked() { return lastPickerTick != null && lastPickerTick.contains("checked=true"); }

    /** Click <b>OK</b> in the item-picker dialog — adds the ticked item to the Purchase Order grid. */
    public String clickOkInPicker() {
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
            System.out.println("PurchaseOrder: picker ok -> " + lastPickerOk);
            return lastPickerOk;
        }

        boolean closed = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Object stillOpen = page.evaluate("() => {" + JS
                    + " const d=topDialog(); return !!(d && /item\\s*search/i.test(d.textContent||'')); }");
            if (!Boolean.TRUE.equals(stillOpen)) { closed = true; break; }
            page.waitForTimeout(400);
        }
        lastPickerOk += closed ? "; the item picker closed" : "; the item picker is STILL open after 8s";
        System.out.println("PurchaseOrder: picker ok -> " + lastPickerOk);
        return lastPickerOk;
    }

    public boolean pickerOkClicked() { return lastPickerOk != null && lastPickerOk.startsWith("clicked \""); }

    /** The item added by the picker actually shows up — judged on the per-item quantity/price fields. */
    public boolean resultsAppeared() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .some(x=>/pr.?qty|pr.?quantity|free.?qty|unit.?purchase.?price|unit.?price/i"
                + "     .test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x))); }");
        return Boolean.TRUE.equals(r);
    }

    // ---- item fields: PR Quantity / Free Qty / Unit Price / Net Unit Purchase Price --

    /**
     * Enter the <b>PR Quantity</b>, <b>Free Qty</b>, <b>Unit Price</b> and <b>Net Unit Purchase Price</b>
     * for the item just added — matched against the LAST visible element for each field, not the first.
     * "Net Unit Purchase Price" is matched and excluded BEFORE "Unit Price" is searched for, since the
     * latter's fuzzy pattern is a substring of the former's own text/model.
     */
    public String enterItemFields(String prQty, String freeQty, String unitPrice, String netUnitPrice) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [pq, fq, up, nup] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const pickLast=(re,pool)=>{ const m=(pool||boxes).filter(x=>re.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + "   return m.length? m[m.length-1] : null; };"
                + " const qtyEl=pinnedLast('Itm.poqty') || pickLast(/^pr.?qty$|^pr.?quantity$|poqty|^quantity$/i);"
                + " const freeEl=pinnedLast('Itm.freeqty') || pickLast(/free.?qty|free.?quantity/i);"
                // Pinned from a live run: the real field is Itm.netpurchaserate (not netunitprice/netrate
                // — the "net.?rate" fuzzy guess missed it because "purchase" sits between "net" and
                // "rate" in the model name, and "." only allows ONE character in between).
                + " const netEl=pinnedLast('Itm.netpurchaserate') || pinnedLast('Itm.netunitprice')"
                + "   || pickLast(/net.*purchase.*rate|net.*unit.*price|net.?rate/i);"
                + " const remaining=boxes.filter(x=>x!==netEl);"
                + " const priceEl=pinnedLast('Itm.rate') || pinnedLast('Itm.unitprice')"
                + "   || pickLast(/unit.?price|^rate$|costprize|cost.?pri[cz]e/i, remaining);"
                + " const set=(e,v,name)=>e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-'+name+'-field)';"
                + " return JSON.stringify(["
                + "   set(qtyEl,pq,'pr-quantity'), set(freeEl,fq,'free-qty'),"
                + "   set(priceEl,up,'unit-price'), set(netEl,nup,'net-unit-purchase-price') ]); }",
                java.util.List.of(prQty, freeQty, unitPrice, netUnitPrice));
        String json = r == null ? "[]" : r.toString();
        String[] fields = parseJsonArray(json);
        lastItemFields = "PRQuantity=" + at(fields, 0) + " | FreeQty=" + at(fields, 1)
                + " | UnitPrice=" + at(fields, 2) + " | NetUnitPurchasePrice=" + at(fields, 3);
        waitForAngular(500);
        System.out.println("PurchaseOrder: " + lastItemFields);
        return lastItemFields;
    }

    public boolean itemFieldsEntered() {
        return lastItemFields != null && !lastItemFields.contains("(no-");
    }

    private static String at(String[] a, int i) { return i < a.length ? a[i] : "?"; }

    /** Minimal parser for a flat JSON string array — no library, known shape, no nested quotes/commas. */
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
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // ---- Purchase Order section: Delivery Place / PR Type / Planned Delivery Date / Delivery Lead Time --

    /**
     * Select the <b>Delivery Place</b> — the first real option in whatever select carries "delivery
     * place". Pinned from a live run: the real model is {@code PurchaseOrder.deliverylocation} (not
     * "deliveryplace"), so the fuzzy pattern also matches "delivery location".
     */
    public String selectDeliveryPlace() {
        lastDeliveryPlace = selectFirstRealOption("delivery.*(place|location)", "__poDeliveryPlace");
        System.out.println("PurchaseOrder: delivery place -> " + lastDeliveryPlace);
        return lastDeliveryPlace;
    }

    public boolean deliveryPlaceSelected() {
        return lastDeliveryPlace != null && !lastDeliveryPlace.isEmpty() && !lastDeliveryPlace.startsWith("(");
    }

    /** Select the <b>PR Type</b> — the first real option in whatever select carries "PR Type". */
    public String selectPrType() {
        lastPrType = selectFirstRealOption("pr.?type", "__poPrType");
        System.out.println("PurchaseOrder: PR type -> " + lastPrType);
        return lastPrType;
    }

    public boolean prTypeSelected() {
        return lastPrType != null && !lastPrType.isEmpty() && !lastPrType.startsWith("(");
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

    /** Enter the <b>Planned Delivery Date</b> — a date field, typed via Playwright's Locator (like every
     *  other date picker in this module) rather than a plain value assignment. */
    public String enterPlannedDeliveryDate(String value) {
        Object found = page.evaluate("() => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>/planned.?delivery.?date|delivery.?date/i"
                + "   .test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__poPlannedDate';"
                + " return e.id; }");
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) {
            lastPlannedDeliveryDate = "(no-planned-delivery-date-field)";
            System.out.println("PurchaseOrder: " + lastPlannedDeliveryDate);
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
        System.out.println("PurchaseOrder: planned delivery date -> " + lastPlannedDeliveryDate);
        return lastPlannedDeliveryDate;
    }

    public boolean plannedDeliveryDateEntered() {
        return lastPlannedDeliveryDate != null && !lastPlannedDeliveryDate.isEmpty()
                && !lastPlannedDeliveryDate.startsWith("(");
    }

    /** Enter the <b>Delivery Lead Time</b>. */
    public String enterDeliveryLeadTime(String value) {
        Object r = page.evaluate("(v) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const e=boxes.find(x=>/delivery.?lead.?time|lead.?time/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " return e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-delivery-lead-time-field)'; }",
                value);
        lastLeadTime = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("PurchaseOrder: " + lastLeadTime);
        return lastLeadTime;
    }

    public boolean deliveryLeadTimeEntered() {
        return lastLeadTime != null && !lastLeadTime.contains("(no-delivery-lead-time-field)");
    }

    // ---- save ---------------------------------------------------------------

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

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
        System.out.println("PurchaseOrder: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }
}
