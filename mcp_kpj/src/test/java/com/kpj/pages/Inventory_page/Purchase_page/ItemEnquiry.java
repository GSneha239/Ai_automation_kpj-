package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Purchase &gt; <b>Item Enquiry</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → click <b>Get Items</b> (item-picker dialog: enter <b>Item Name</b>, click
 * <b>Search</b>, tick the checkbox beside the item code, click <b>OK</b>) → select the <b>Supplier</b>
 * and tick its checkbox → click <b>Add Terms and Condition</b> (a second dialog: select <b>Payment
 * Terms</b> and <b>Terms and Condition</b>, click <b>Add</b>, click <b>OK</b>) → enter the listed
 * item's <b>Quantity</b> and <b>Remarks</b> → click <b>Save</b>.</p>
 *
 * <p>This screen has not been inspected live: every control is found by FUZZY matching first, with
 * exact ng-models pinned in place of the fuzzy guess wherever a prior live run on a sibling screen
 * (Opening Balance, same Inventory module) already confirmed one — the item-picker dialog is the same
 * shared "Item Search" component, so its lessons apply here too:</p>
 * <ul>
 *   <li>Checkbox ticks use Playwright's own {@link com.microsoft.playwright.Locator#check()}, not raw
 *       JS {@code .click()} — plain DOM manipulation reported success on Opening Balance while the
 *       screen's own validation still refused it.</li>
 *   <li>Checkbox state is retried up to 3 times — it registered inconsistently across otherwise-identical
 *       runs on the sibling screen, which pointed to real render/digest timing rather than a wrong
 *       selector.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form or whichever dialog is open) so the
 * remaining fuzzy matches can be pinned exactly once this has run against the live screen.
 */
public class ItemEnquiry extends BasePage {

    public ItemEnquiry(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastGetItems = "",
            lastItemSearch = "", lastSearch = "", lastRows = "", lastTick = "", lastOk = "",
            lastSupplier = "", lastSupplierTick = "", lastAddTerms = "", lastPaymentTerms = "",
            lastTermsCondition = "", lastTermsAdd = "", lastTermsOk = "", lastQtyRemarks = "",
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

    /** Application menu &rarr; Inventory &rarr; Purchase &rarr; Item Enquiry. */
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
                    + "   .find(x=>/^\\s*item\\s*enquiry\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__ieMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__ieMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ItemEnquiry.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__ieMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("itemenquiry")) return true;
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
        System.out.println("=== ItemEnquiry CONTROLS ===\n" + s);
        return s;
    }

    // ---- New / Get Items ---------------------------------------------------

    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ieNew'; }");
        try {
            page.locator("#__ieNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("ItemEnquiry.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__ieNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("ItemEnquiry: " + lastNew);
        return lastNew;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('button,a')].filter(vis)"
                + "   .some(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /getitem/i.test(x.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    public String clickGetItems() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/get\\s*items?/i.test(norm(x.textContent))"
                + "        || /getitem/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ieGetItems'; }");
        try {
            page.locator("#__ieGetItems").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("ItemEnquiry.clickGetItems: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__ieGetItems'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the item picker opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Get Items'; }");
        lastGetItems = r == null ? "" : r.toString();
        System.out.println("ItemEnquiry: " + lastGetItems);
        return lastGetItems;
    }

    public boolean itemPickerOpen() {
        return lastGetItems != null && lastGetItems.startsWith("the item picker opened");
    }

    // ---- item search (inside the dialog) -----------------------------------

    /** Enter the <b>Item Name</b> inside the item-picker dialog. */
    public String enterItemName(String name) {
        Object r = page.evaluate("(n) => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const pinned=d.querySelector(\"[ng-model='ItemSearch.ItemName']\");"
                + " const boxes=[...d.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const nameEl=pinned || boxes.find(x=>/item.?name/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " return nameEl? setEl(nameEl,n)+' ['+(ngOf(nameEl)||'?')+']' : '(no-item-name-field)'; }",
                name);
        lastItemSearch = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ItemEnquiry: " + lastItemSearch);
        return lastItemSearch;
    }

    public boolean itemNameEntered() {
        return lastItemSearch != null && !lastItemSearch.contains("(no-item-name-field)");
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
        System.out.println("ItemEnquiry: " + lastSearch);
        return lastSearch;
    }

    /**
     * How many SELECTABLE items the picker lists.
     *
     * <p>Counting every visible {@code tr}/{@code .ui-grid-row} over-counts: the dialog's own column
     * FILTER row is present whether or not a search found anything. Counted on the same per-row tick
     * control {@link #tickFirstItem()} would actually click, so a 0 here always means nothing to select.</p>
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

    /**
     * Tick the first real item row's checkbox, beside the Item Code column.
     *
     * <p>Same shared item-picker component as Opening Balance: a real browser click via
     * {@link com.microsoft.playwright.Locator#check()} is used rather than raw JS, and retried up to 3
     * times — raw JS reported success while the screen's own validation still saw nothing selected, and
     * the real click was itself observed to land inconsistently across otherwise-identical runs.</p>
     */
    /**
     * Find-and-click the item picker's tick, in ONE atomic JS call.
     *
     * <p>Playwright's own {@code Locator.check()} — even retried with re-location each attempt — was
     * observed to report {@code checked=true} while the dialog's OWN {@code OKClick()} validation still
     * refused with "Please Select Item !!!!": two separate round-trips (locate, then act) leave a window
     * for the results table to re-render between them. Doing both in one call closes that window, and
     * Angular's own {@code $setViewValue}/{@code $render}/change is fired in the same call so the tick is
     * real, not just a DOM property flip — the same fix that resolved the supplier checkbox.</p>
     */
    public String tickFirstItem() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const d=topDialog(); if(!d) return '';"
                + "   const cb=[...d.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "     .filter(e=>!/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll/i.test(ngOf(e)))"
                + "     .filter(e=>e.closest('tr,.ui-grid-row'))[0];"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__ieItemCb';"
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
        lastTick = result.startsWith("checked=")
                ? "ticked the checkbox beside the item code -> " + result
                : "(" + result + ")";
        System.out.println("ItemEnquiry: tick -> " + lastTick);
        return lastTick;
    }

    public boolean itemTicked() { return lastTick != null && lastTick.contains("checked=true"); }

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

        Object refusal = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=toastEls().map(x=>norm(x.textContent)).find(x=>x); return t||''; }");
        String refusalText = refusal == null ? "" : refusal.toString();
        if (!refusalText.isEmpty()) {
            lastOk = "clicked, but the dialog refused: \"" + refusalText + "\"";
            System.out.println("ItemEnquiry: ok -> " + lastOk);
            return lastOk;
        }

        boolean closed = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Object stillOpen = page.evaluate("() => {" + JS
                    + " const d=topDialog(); return !!(d && /item\\s*search/i.test(d.textContent||'')); }");
            if (!Boolean.TRUE.equals(stillOpen)) { closed = true; break; }
            page.waitForTimeout(400);
        }
        lastOk += closed ? "; the item picker closed" : "; the item picker is STILL open after 8s";
        System.out.println("ItemEnquiry: ok -> " + lastOk);
        return lastOk;
    }

    public boolean okClicked() { return lastOk != null && lastOk.startsWith("clicked \""); }

    // ---- supplier ------------------------------------------------------------

    /**
     * Click <b>Select Supplier</b> to open its dropdown panel.
     *
     * <p>Pinned from a live run: this is NOT a plain {@code <select>} — it is a button
     * ({@code toggleDropdown()}) that opens a checklist panel of supplier names, each with its own
     * checkbox. "Select the supplier" and "tick its checkbox" are therefore one interaction here:
     * opening the panel, then {@link #tickSupplierCheckbox()} ticks a name inside it.</p>
     */
    public String selectSupplier() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/select\\s*supplier/i.test(norm(x.textContent))"
                + "        || /toggleDropdown/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ieSupplierBtn'; }");
        try {
            page.locator("#__ieSupplierBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            lastSupplier = "(Select Supplier button click failed: " + e.getMessage().split("\n")[0] + ")";
            System.out.println("ItemEnquiry: supplier -> " + lastSupplier);
            return lastSupplier;
        }
        page.evaluate("() => { const e=document.getElementById('__ieSupplierBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        // Scope to the dropdown panel ITSELF — a container that opened right next to the button and
        // holds most of the page's checkboxes (as opposed to counting every checkbox anywhere on the
        // page, which one run found 799 of and is too broad to reliably tick the right one from).
        Object opened = page.evaluate("() => {" + JS
                + " const btn=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/select\\s*supplier/i.test(norm(x.textContent)));"
                + " const all=[...document.querySelectorAll('input[type=checkbox]')].filter(vis);"
                + " const near=all.filter(cb=>{ if(!btn) return false;"
                + "   const b=btn.getBoundingClientRect(), c=cb.getBoundingClientRect();"
                + "   return Math.abs(c.top-b.bottom)<600 && Math.abs(c.left-b.left)<400; });"
                + " return JSON.stringify({all:all.length, near:near.length}); }");
        String openedInfo = opened == null ? "{}" : opened.toString();
        lastSupplier = !openedInfo.equals("{\"all\":0,\"near\":0}")
                ? "opened the Select Supplier panel " + openedInfo
                : "(Select Supplier panel opened but no checkboxes appeared)";
        System.out.println("ItemEnquiry: supplier -> " + lastSupplier);
        return lastSupplier;
    }

    public boolean supplierSelected() {
        return lastSupplier != null && lastSupplier.startsWith("opened the Select Supplier panel");
    }

    /** Minimal parser for the flat {index, count, ng} JSON produced above — no library, known shape. */
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
     * Tick the checkbox for the supplier's name, in whatever list shows supplier names with a checkbox
     * beside them — separate from the Supplier dropdown itself.
     */
    /** Locates the supplier checkbox (or re-locates it after {@code prevId} has gone stale). */
    /**
     * Confirmed live (screenshot): the panel is a small popover table headed "Select | Supplier Name"
     * (Vendor 1, Vendor 1000, ...), fully visible with no scrolling needed. The checkbox is the FIRST
     * one in that table's body — found via the "Supplier Name" header, not by proximity to the button
     * (bounding-box proximity was matching the wrong element among the page's ~799 checkboxes).
     */
    private static final String FIND_SUPPLIER_CB_JS =
            "(prevId) => {"
          + " let e=prevId? document.getElementById(prevId) : null;"
          + " if(e && vis(e)) return prevId;"
          + " const header=[...document.querySelectorAll('th,td,div,span')].filter(vis)"
          + "   .find(x=>/^\\s*supplier\\s*name\\s*$/i.test(norm(x.textContent)));"
          + " const table=header? header.closest('table,.ui-grid,div') : null;"
          + " const inTable=table? [...table.querySelectorAll('input[type=checkbox]')].filter(vis)[0] : null;"
          + " const cb=inTable || [...document.querySelectorAll('input[type=checkbox]')].filter(vis)"
          + "   .find(e2=>/supplier/i.test(tail(e2)+' '+labelOf(e2))"
          + "     || (e2.closest('tr,li,div') && /supplier/i.test(norm(e2.closest('tr,li,div').textContent))));"
          + " if(!cb) return '';"
          + " if(!cb.id) cb.id='__ieSupplierCb';"
          + " return cb.id; }";

    public String tickSupplierCheckbox() {
        // Playwright's own Locator.check() — including with force:true and no scroll step — kept timing
        // out at the CLICK ITSELF (Frame.check, not just the earlier actionability wait), which reads as
        // the element being replaced between the round-trip that locates it and the round-trip that acts
        // on it. Find-and-click is therefore done as ONE atomic JS call instead of two separate
        // Playwright round-trips, closing that window; Angular's own $setViewValue/$render/change is
        // fired in the same call so the tick is real, not just a DOM property flip.
        Object r = page.evaluate("() => {" + JS
                + " const find=" + FIND_SUPPLIER_CB_JS + ";"
                + " for (let attempt = 0; attempt < 8; attempt++) {"
                + "   const id=find('');"
                + "   if(!id) continue;"
                + "   const cb=document.getElementById(id);"
                + "   if(!cb) continue;"
                + "   if(!cb.checked) cb.click();"
                + "   try{ const c=angular.element(cb).controller('ngModel');"
                + "        if(c){ c.$setViewValue(true); c.$render(); } }catch(err){}"
                + "   cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(cb).triggerHandler('change'); }catch(err){}"
                + "   if(cb.checked) return 'checked='+cb.checked; }"
                + " return '(the checkbox would not stay attached across 8 attempts)'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(800);
        lastSupplierTick = result.startsWith("checked=")
                ? "ticked the supplier checkbox -> " + result
                : "(" + result + ")";
        System.out.println("ItemEnquiry: supplier tick -> " + lastSupplierTick);
        return lastSupplierTick;
    }

    public boolean supplierTicked() {
        return lastSupplierTick != null && lastSupplierTick.contains("checked=true");
    }

    // ---- terms and condition --------------------------------------------------

    /** Click <b>Add Terms and Condition</b> — opens the Terms and Condition dialog. */
    public String clickAddTermsAndCondition() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/add.*terms?.*(and|&).*condition/i.test(norm(x.textContent))"
                + "        || /terms?.*condition/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ieAddTerms'; }");
        try {
            page.locator("#__ieAddTerms").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("ItemEnquiry.clickAddTermsAndCondition: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__ieAddTerms'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1800);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the Terms and Condition dialog opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared'; }");
        lastAddTerms = r == null ? "" : r.toString();
        System.out.println("ItemEnquiry: " + lastAddTerms);
        return lastAddTerms;
    }

    public boolean termsDialogOpen() {
        return lastAddTerms != null && lastAddTerms.startsWith("the Terms and Condition dialog opened");
    }

    /**
     * Select the first real option of the Nth visible {@code <select>} in the dialog.
     *
     * <p>Pinned from a live run BY POSITION rather than by label/ng-model text: the dialog carries two
     * selects and neither name-matches what it actually is — the first has no {@code ng-model} captured
     * at all (options read "Lump Sum" / "Milestone Payment"), and the second is bound to
     * {@code TermsCon.TermsConID} (options read "Due Immediately" / "Pay Immediately") — a value-sounding
     * name, not a label-sounding one. The dialog's own text order (Payment Terms label, then this select)
     * is the only reliable signal, so ordinal position is used instead of a name match.</p>
     */
    private String selectRealOptionByOrdinal(int ordinal, String tempId) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [ord, id] = a;"
                + " document.querySelectorAll('#'+id).forEach(e=>e.removeAttribute('id'));"
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const sels=[...d.querySelectorAll('select')].filter(vis);"
                + " const e=sels[ord]; if(!e) return '(only '+sels.length+' select(s) in the dialog)';"
                + " e.id=id;"
                // On the FIRST select in this dialog, every option's real HTML "value" attribute reads
                // empty even for genuine choices ("Lump Sum", "Milestone Payment") - only text tells a
                // real option from the placeholder here, so a truthy o.value is not required.
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
        lastPaymentTerms = selectRealOptionByOrdinal(0, "__iePayTerms");
        System.out.println("ItemEnquiry: payment terms -> " + lastPaymentTerms);
        return lastPaymentTerms;
    }

    public boolean paymentTermsSelected() {
        return lastPaymentTerms != null && !lastPaymentTerms.isEmpty() && !lastPaymentTerms.startsWith("(");
    }

    /** Select the <b>Terms and Condition</b> dropdown — the SECOND select in the dialog. */
    public String selectTermsCondition() {
        lastTermsCondition = selectRealOptionByOrdinal(1, "__ieTermsCond");
        System.out.println("ItemEnquiry: terms and condition -> " + lastTermsCondition);
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
        System.out.println("ItemEnquiry: " + lastTermsAdd);
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
        System.out.println("ItemEnquiry: " + lastTermsOk);
        return lastTermsOk;
    }

    public boolean termsOkClicked() { return lastTermsOk != null && lastTermsOk.startsWith("clicked \""); }

    // ---- item list: quantity + remarks -----------------------------------

    /** Enter the listed item's <b>Quantity</b> and <b>Remarks</b> on the main form. */
    public String enterQuantityAndRemarks(String qty, String remarks) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [q, rm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                // Pinned from a live run: the real field is item.EconomyQty, not a plain "quantity"/"qty"
                // model — a fuzzy match anchored to those exact words found nothing.
                + " const qtyEl=document.querySelector(\"[ng-model='item.EconomyQty']\")"
                + "   || boxes.find(x=>/qty|quantity/i.test(tail(x))"
                + "        || /quantity|qty/i.test((x.placeholder||'')+' '+labelOf(x)));"
                + " const remEl=boxes.filter(x=>x!==qtyEl)"
                + "   .find(x=>/remark/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " const set=(e,v,name)=>e? setEl(e,v)+' ['+(ngOf(e)||'?')+']' : '(no-'+name+'-field)';"
                + " return 'Quantity='+set(qtyEl,q,'quantity')+' | Remarks='+set(remEl,rm,'remarks'); }",
                java.util.List.of(qty, remarks));
        lastQtyRemarks = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ItemEnquiry: " + lastQtyRemarks);
        return lastQtyRemarks;
    }

    public boolean quantityAndRemarksEntered() {
        return lastQtyRemarks != null && !lastQtyRemarks.contains("(no-");
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
        System.out.println("ItemEnquiry: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }
}
