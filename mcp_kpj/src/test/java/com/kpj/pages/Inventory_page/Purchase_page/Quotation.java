package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Quotation</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → select the <b>Supplier</b> → enter the <b>Item Name</b> → open the
 * <b>New Item</b> tab → click <b>New Item</b> → in <b>Quotation Details</b> enter the <b>Item Code</b>,
 * <b>Item Name</b>, <b>Quantity</b>, <b>Cost Price</b>, <b>Excise</b> and <b>Tax %</b> → click
 * <b>Add Terms and Condition</b> (select <b>Payment Terms</b> and <b>Terms and Condition</b>, click
 * <b>Add</b>, click <b>OK</b>) → click <b>Save</b>.</p>
 *
 * <p>This screen has not been inspected live. It is a sibling of {@link ItemEnquiry} in the same
 * Purchase module, so proven fixes from that screen are applied from the start rather than
 * re-discovered:</p>
 * <ul>
 *   <li>Checkbox ticks (e.g. the supplier picker, if it turns out to be the same button+panel widget)
 *       are done as ONE atomic JS call (find, click, sync Angular's {@code $setViewValue}, all in a
 *       single {@code page.evaluate}) — Playwright's own two-round-trip {@code Locator.check()} was
 *       observed to report success while the screen's own validation still saw nothing selected, because
 *       the live app re-renders the element between the "locate" and "act" round-trips.</li>
 *   <li>The Terms and Condition dialog's two {@code select} elements are targeted by ORDINAL position
 *       (first = Payment Terms, second = Terms and Condition), not by label/ng-model text — neither
 *       carried a name resembling its own field on Item Enquiry's identical dialog.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form or whichever dialog is open) so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.
 */
public class Quotation extends BasePage {

    public Quotation(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastSupplier = "",
            lastItemName = "", lastNewItemTab = "", lastNewItemBtn = "", lastQuotationDetails = "",
            lastAddTerms = "", lastPaymentTerms = "", lastTermsCondition = "", lastTermsAdd = "",
            lastTermsOk = "", lastSave = "", lastSaveDiagnostics = "",
            lastGetItems = "", lastPickerSearch = "", lastPickerTick = "", lastPickerOk = "";

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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Quotation. */
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
                    + "   .find(x=>/^\\s*quotation\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__qtMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__qtMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Quotation.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__qtMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("quotation")) return true;
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
        System.out.println("=== Quotation CONTROLS ===\n" + s);
        return s;
    }

    // ---- New / Supplier / Item Name -----------------------------------------

    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__qtNew'; }");
        try {
            page.locator("#__qtNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Quotation.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__qtNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("Quotation: " + lastNew);
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
     * Select the <b>Supplier</b>.
     *
     * <p>Tries a plain {@code <select>} first; falls back to the same button+checkbox-panel widget
     * confirmed live on Item Enquiry ("Select Supplier" opens a popover table headed "Select | Supplier
     * Name") if no select carries "supplier". Either path's tick is done as ONE atomic JS call — see the
     * class doc for why.</p>
     */
    public String selectSupplier() {
        Object plain = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__qtSupplier').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(x=>/supplier/i.test(tail(x)+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " e.id='__qtSupplier';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length}) : ''; }");
        String plainInfo = plain == null ? "" : plain.toString();
        if (!plainInfo.isEmpty()) {
            try {
                java.util.Map<String, Object> m = parseFlatJson(plainInfo);
                page.locator("#__qtSupplier").selectOption(
                        new com.microsoft.playwright.options.SelectOption()
                                .setIndex(((Number) m.get("index")).intValue()));
                waitForAngular(900);
                Object t = page.evaluate("() => { const e=document.getElementById('__qtSupplier');"
                        + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
                page.evaluate("() => { const e=document.getElementById('__qtSupplier'); if(e) e.removeAttribute('id'); }");
                lastSupplier = (t == null ? "" : t.toString()) + " [select, " + plainInfo + "]";
                System.out.println("Quotation: supplier -> " + lastSupplier);
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
                + " if(b) b.id='__qtSupplierBtn'; }");
        try {
            page.locator("#__qtSupplierBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            lastSupplier = "(no Supplier select and the Select Supplier button click failed: "
                    + e.getMessage().split("\n")[0] + ")";
            System.out.println("Quotation: supplier -> " + lastSupplier);
            return lastSupplier;
        }
        page.evaluate("() => { const e=document.getElementById('__qtSupplierBtn'); if(e) e.removeAttribute('id'); }");
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
                + "   if(!cb.id) cb.id='__qtSupplierCb';"
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
        System.out.println("Quotation: supplier -> " + lastSupplier);
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

    /** Enter the main form's <b>Item Name</b> field (distinct from Quotation Details' own Item Name). */
    public String enterItemName(String name) {
        Object r = page.evaluate("(n) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const e=boxes.find(x=>/item.?name/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " return e? setEl(e,n)+' ['+(ngOf(e)||'?')+']' : '(no-item-name-field)'; }",
                name);
        lastItemName = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Quotation: " + lastItemName);
        return lastItemName;
    }

    public boolean itemNameEntered() {
        return lastItemName != null && !lastItemName.contains("(no-item-name-field)");
    }

    // ---- New Item tab / button ----------------------------------------------

    /** Open the <b>New Item</b> tab. */
    public String clickNewItemTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*item\\s*$/i.test(norm(x.textContent)));"
                + " if(!t) return '(no New Item tab found)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked the New Item tab'; }");
        lastNewItemTab = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("Quotation: " + lastNewItemTab);
        return lastNewItemTab;
    }

    public boolean newItemTabOpened() {
        return lastNewItemTab != null && lastNewItemTab.startsWith("clicked the New Item tab");
    }

    /**
     * Click <b>New Item</b> — a button distinct from the tab, expected to open (or reveal)
     * <b>Quotation Details</b>. If no separate button exists, the tab click alone is treated as having
     * satisfied this step, and that is said plainly rather than reported as a failure.
     */
    public String clickNewItemButton() {
        Object r = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*item\\s*$/i.test(norm(x.textContent))"
                + "        && x.tagName==='BUTTON');"
                + " if(!b) return '';"
                + " b.click(); return 'clicked the New Item button'; }");
        String clicked = r == null ? "" : r.toString();
        if (clicked.isEmpty()) {
            lastNewItemBtn = "(no separate New Item button — the tab click already opened Quotation Details)";
            System.out.println("Quotation: " + lastNewItemBtn);
            return lastNewItemBtn;
        }
        lastNewItemBtn = clicked;
        waitForAngular(1500);
        System.out.println("Quotation: " + lastNewItemBtn);
        return lastNewItemBtn;
    }

    public boolean newItemButtonHandled() {
        return lastNewItemBtn != null && (lastNewItemBtn.startsWith("clicked the New Item button")
                || lastNewItemBtn.startsWith("(no separate New Item button"));
    }

    /** Quotation Details is showing — judged on any of its six expected fields being visible. */
    public boolean quotationDetailsOpen() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); const root=d||document;"
                + " return [...root.querySelectorAll('input,textarea')].filter(vis)"
                + "   .some(x=>/item.?code|cost.?price|excise|tax.?per|quantity/i"
                + "     .test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x))); }");
        return Boolean.TRUE.equals(r);
    }

    // ---- Get Items (the alternative to New Item — pick an existing item) --

    /**
     * Click <b>Get Items</b> — opens the same shared "Item Search" picker dialog confirmed live on
     * {@link ItemEnquiry} (same Purchase module, same {@code ItemSearch.*}/{@code SearchItem()} models).
     */
    public String clickGetItems() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /OpenItemSearch/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__qtGetItems'; }");
        try {
            page.locator("#__qtGetItems").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Quotation.clickGetItems: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__qtGetItems'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the item picker opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Get Items'; }");
        lastGetItems = r == null ? "" : r.toString();
        System.out.println("Quotation: " + lastGetItems);
        return lastGetItems;
    }

    public boolean itemPickerOpen() {
        return lastGetItems != null && lastGetItems.startsWith("the item picker opened");
    }

    /** Click <b>Search</b> inside the item-picker dialog. */
    public String clickSearchInPicker() {
        page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return;"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/SearchItem/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...d.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        lastPickerSearch = "clicked Search -> " + pickerRowCount() + " row(s) in the item picker";
        System.out.println("Quotation: " + lastPickerSearch);
        return lastPickerSearch;
    }

    /** How many SELECTABLE items the picker lists — same filter/row-scoping proven on Item Enquiry. */
    public int pickerRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " return [...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "   .filter(e=>e.closest('tr,.ui-grid-row')).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Tick the first real item row's checkbox, beside the Item Code column.
     *
     * <p>Find-and-click in ONE atomic JS call — Playwright's own two-round-trip {@code Locator.check()}
     * was observed on Item Enquiry's identical dialog to report success while the dialog's own
     * {@code OKClick()} validation still refused with "Please Select Item !!!!", because the live app
     * re-renders the row between the round-trip that locates it and the one that acts on it.</p>
     */
    public String tickFirstItemInPicker() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const d=topDialog(); if(!d) return '';"
                + "   const cb=[...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "     .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "     .filter(e=>e.closest('tr,.ui-grid-row'))[0];"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__qtItemCb';"
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
        System.out.println("Quotation: picker tick -> " + lastPickerTick);
        return lastPickerTick;
    }

    public boolean pickerItemTicked() { return lastPickerTick != null && lastPickerTick.contains("checked=true"); }

    /** Click <b>OK</b> in the item-picker dialog — adds the ticked item to the Quotation Details grid. */
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
            System.out.println("Quotation: picker ok -> " + lastPickerOk);
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
        System.out.println("Quotation: picker ok -> " + lastPickerOk);
        return lastPickerOk;
    }

    public boolean pickerOkClicked() { return lastPickerOk != null && lastPickerOk.startsWith("clicked \""); }

    // ---- Quotation Details -----------------------------------------------

    /**
     * Enter the <b>Item Code</b>, <b>Item Name</b>, <b>Quantity</b>, <b>Cost Price</b>, <b>Excise</b> and
     * <b>Tax %</b> in Quotation Details (a dialog, or an inline section revealed by New Item).
     */
    public String enterQuotationDetails(String itemCode, String itemName, String qty, String costPrice,
                                          String excise, String taxPercent) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [ic, iname, q, cost, exc, tax] = a;"
                + " const d=topDialog(); const root=d||document;"
                + " const boxes=[...root.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const pick=(re)=>boxes.find(x=>re.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                // Pinned from a live run: the real fields are Itm.serviceitemcode, Itm.serviceitemname,
                // Itm.poqty (Quantity), Itm.costprize (Cost Price — the app's OWN spelling has a typo,
                // "prize" not "price") and Itm.Tax (Tax %, no "per"/"%" suffix in the model). A
                // word-anchored fuzzy guess missed poqty, costprize and Tax for exactly that reason.
                //
                // pinnedLast: with TWO item rows on screen (New Item + Get Items, both added to the same
                // quotation), each row's ng-repeat scope reuses the SAME model name "Itm.*" — querySelector
                // returns the FIRST match, i.e. row 1, silently leaving a just-added row 2 untouched. The
                // LAST visible match is the newest row, which is the one these values belong to.
                + " const pinnedLast=(m)=>{ const all=[...document.querySelectorAll(\"[ng-model='\"+m+\"']\")].filter(vis);"
                + "   return all.length? all[all.length-1] : null; };"
                + " const codeEl=pinnedLast('Itm.serviceitemcode') || pick(/item.?code/i);"
                + " const nameEl=pinnedLast('Itm.serviceitemname')"
                + "   || boxes.filter(x=>x!==codeEl).find(x=>/item.?name/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " const qtyEl=pinnedLast('Itm.poqty') || pick(/^quantity$|^qty$|econom.*qty|poqty/i);"
                + " const costEl=pinnedLast('Itm.costprize') || pick(/cost.?pri[cz]e|^rate$/i);"
                + " const exciseEl=pinnedLast('Itm.Excise') || pick(/excise/i);"
                + " const taxEl=pinnedLast('Itm.Tax') || pick(/^tax$|tax.?per|tax.?%|taxpercentage/i);"
                + " const set=(e,v,name)=>e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-'+name+'-field)';"
                + " return JSON.stringify(["
                + "   set(codeEl,ic,'item-code'), set(nameEl,iname,'item-name'), set(qtyEl,q,'quantity'),"
                + "   set(costEl,cost,'cost-price'), set(exciseEl,exc,'excise'), set(taxEl,tax,'tax-percent') ]); }",
                java.util.List.of(itemCode, itemName, qty, costPrice, excise, taxPercent));
        String json = r == null ? "[]" : r.toString();
        String[] fields = parseJsonArray(json);
        lastQuotationDetails = "ItemCode=" + at(fields, 0) + " | ItemName=" + at(fields, 1)
                + " | Quantity=" + at(fields, 2) + " | CostPrice=" + at(fields, 3)
                + " | Excise=" + at(fields, 4) + " | TaxPercent=" + at(fields, 5);
        waitForAngular(500);
        System.out.println("Quotation: " + lastQuotationDetails);
        return lastQuotationDetails;
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

    public boolean quotationDetailsEntered() {
        return lastQuotationDetails != null && !lastQuotationDetails.contains("(no-");
    }

    // ---- terms and condition --------------------------------------------------

    /** Click <b>Add Terms and Condition</b> — opens the Terms and Condition dialog. */
    public String clickAddTermsAndCondition() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/add.*terms?.*(and|&).*condition/i.test(norm(x.textContent))"
                + "        || /terms?.*condition/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__qtAddTerms'; }");
        try {
            page.locator("#__qtAddTerms").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Quotation.clickAddTermsAndCondition: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__qtAddTerms'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1800);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the Terms and Condition dialog opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared'; }");
        lastAddTerms = r == null ? "" : r.toString();
        System.out.println("Quotation: " + lastAddTerms);
        return lastAddTerms;
    }

    public boolean termsDialogOpen() {
        return lastAddTerms != null && lastAddTerms.startsWith("the Terms and Condition dialog opened");
    }

    /**
     * Select the first real option of the Nth visible {@code <select>} in the dialog.
     *
     * <p>Pinned by ORDINAL position, the same as Item Enquiry's identical dialog: neither select there
     * name-matched what it actually was (label/ng-model text told nothing useful), so the dialog's own
     * text order (Payment Terms label, then this select) is the only reliable signal.</p>
     */
    private String selectRealOptionByOrdinal(int ordinal, String tempId) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [ord, id] = a;"
                + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const sels=[...d.querySelectorAll('select')].filter(vis);"
                + " const e=sels[ord]; if(!e) return '(only '+sels.length+' select(s) in the dialog)';"
                + " e.id=id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.i>0 && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.text)));"
                + " return reals.length? JSON.stringify({index:reals[0].i, count:reals.length})"
                + "   : '(the list has no real option)'; }",
                java.util.List.of(ordinal, tempId));
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

    /** Select the <b>Payment Terms</b> dropdown — the FIRST select in the Terms and Condition dialog. */
    public String selectPaymentTerms() {
        lastPaymentTerms = selectRealOptionByOrdinal(0, "__qtPayTerms");
        System.out.println("Quotation: payment terms -> " + lastPaymentTerms);
        return lastPaymentTerms;
    }

    public boolean paymentTermsSelected() {
        return lastPaymentTerms != null && !lastPaymentTerms.isEmpty() && !lastPaymentTerms.startsWith("(");
    }

    /** Select the <b>Terms and Condition</b> dropdown — the SECOND select in the dialog. */
    public String selectTermsCondition() {
        lastTermsCondition = selectRealOptionByOrdinal(1, "__qtTermsCond");
        System.out.println("Quotation: terms and condition -> " + lastTermsCondition);
        return lastTermsCondition;
    }

    public boolean termsConditionSelected() {
        return lastTermsCondition != null && !lastTermsCondition.isEmpty() && !lastTermsCondition.startsWith("(");
    }

    /** Click <b>Add</b> inside the Terms and Condition dialog — adds the chosen terms to its own list. */
    public String clickAddInTermsDialog() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no Add button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        lastTermsAdd = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("Quotation: " + lastTermsAdd);
        return lastTermsAdd;
    }

    public boolean termsAdded() { return lastTermsAdd != null && lastTermsAdd.startsWith("clicked \""); }

    /** Click <b>OK</b> to close the Terms and Condition dialog. */
    public String clickOkTermsDialog() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*ok\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        lastTermsOk = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("Quotation: " + lastTermsOk);
        return lastTermsOk;
    }

    public boolean termsOkClicked() { return lastTermsOk != null && lastTermsOk.startsWith("clicked \""); }

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
        System.out.println("Quotation: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }
}
