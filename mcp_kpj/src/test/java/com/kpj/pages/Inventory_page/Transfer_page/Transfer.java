package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Transfer &gt; <b>Transfer</b> — Page Object.
 *
 * <p>Flow: click <b>New</b> → click <b>Get Indent</b> (opens an "Item Search" dialog) → click
 * <b>Search</b> → tick a result item's checkbox to choose it → open <b>Mark Unavailable</b> → click
 * <b>Mark Unavailable</b> → verify the success toast.</p>
 *
 * <p>First screen in the {@code Inventory_page.Transfer_page} submodule. It has not been inspected live
 * and every control is found by FUZZY matching, applying fixes proven across the rest of the
 * {@code Inventory_page} module from the start rather than re-discovering them:</p>
 * <ul>
 *   <li>Checkbox ticks are done as ONE atomic JS call (find, click, sync Angular's
 *       {@code $setViewValue}, all in a single {@code page.evaluate}) — Playwright's own two-round-trip
 *       {@code Locator.check()} was observed elsewhere in this module to report success while the
 *       screen's own validation still saw nothing selected, because the live app re-renders the element
 *       between the "locate" and "act" round-trips.</li>
 *   <li>Button matching requires an open-paren right after the action name in {@code ng-click}
 *       (e.g. {@code /^markunavailable\s*\(/i}), not a bare prefix — on {@link
 *       com.kpj.pages.Inventory_page.POS_page.ItemSaleList}, a bare-prefix match on "search" hit an
 *       unrelated button instead of the real one, and the same shape of collision hit "print" there too.
 *       Where a screen has TWO buttons whose names could collide even with an anchored prefix (e.g. a
 *       "Print"/"Print Drug Label" pair), matching falls back to EXACT visible text only.</li>
 *   <li>"Add a new tab - Mark Unavailable" is handled the same defensive way every other "add a tab" step
 *       in this module has been — none turned out to be a real tab, only the same action button used
 *       later, and clicking it early misfired the action on sibling screens. So this never clicks
 *       anything that could be the real Mark Unavailable action button before the item is ticked.</li>
 *   <li>Navigation waits for real visible content (not just the header shell) — {@link
 *       com.kpj.pages.Inventory_page.POS_page.ItemSaleList} needed noticeably longer than the fixed wait
 *       used elsewhere in this module before its form actually rendered.</li>
 * </ul>
 * {@link #describeControls()} dumps every visible control (main form or whichever dialog is open) so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.
 */
public class Transfer extends BasePage {

    public Transfer(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastNew = "", lastGetIndent = "",
            lastDialogSearch = "", lastItemTick = "", lastMarkUnavailableTab = "", lastMarkUnavailable = "",
            lastSave = "", lastSaveDiagnostics = "", lastMarkCloseTab = "", lastMarkClose = "",
            lastListDates = "", lastListSearch = "", lastRowTick = "", lastPrintTab = "", lastPrint = "",
            lastPatientPrintTab = "", lastPatientPrint = "";

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
          + "  .filter(x=>!x.closest('thead') && !x.querySelector('th'));";

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /** Application menu &rarr; Inventory &rarr; Transfer &rarr; Transfer. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*inventory\\s*$");
            clickMenu("^\\s*transfer\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    // Confirmed live: TWO links read exactly "Transfer" — the submenu TOGGLE itself
                    // (href="#", opens/closes the "Transfer" submenu) and the actual SCREEN link nested
                    // inside it (href="#/IssueToStoreList"). Taking the first DOM match landed on the
                    // toggle, whose click does nothing but expand the menu, leaving the route at "#" and
                    // the screen never reached. Require a real, non-placeholder route instead.
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .filter(x=>/^\\s*transfer\\s*$/i.test(norm(x.textContent)))"
                    + "   .find(x=>{ const h=x.getAttribute('href')||'';"
                    + "     return h && h!=='#' && !/^javascript:/i.test(h); });"
                    + " if(!a) return ''; a.id='__trfMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__trfMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Transfer.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__trfMenu'); if(e) e.removeAttribute('id'); }");
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
        if (route.contains("transfer")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /** Poll until the screen shows more than just its header shell — confirmed live on {@link
     *  com.kpj.pages.Inventory_page.POS_page.ItemSaleList} that a screen can briefly render nothing else
     *  right after navigation, for noticeably longer than the fixed wait used elsewhere in this module. */
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
        System.out.println("=== Transfer CONTROLS ===\n" + s);
        return s;
    }

    // ---- New / Get Indent ------------------------------------------------------

    public String clickNew() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /^(fn)?(add|new)\\s*\\(/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__trfNew'; }");
        try {
            page.locator("#__trfNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Transfer.clickNew: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__trfNew'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastNew = formOpen() ? "New clicked; the form is open" : "New clicked; no form detected";
        System.out.println("Transfer: " + lastNew);
        return lastNew;
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('button,a')].filter(vis)"
                + "   .some(x=>/get\\s*indent/i.test(norm(x.textContent))); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean newClicked() { return lastNew != null && lastNew.contains("form is open"); }

    /** Click <b>Get Indent</b> — opens an "Item Search" dialog. */
    public String clickGetIndent() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/get\\s*indent/i.test(norm(x.textContent))"
                + "        || /getindent/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__trfGetIndent'; }");
        try {
            page.locator("#__trfGetIndent").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Transfer.clickGetIndent: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__trfGetIndent'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); return d? ('the Item Search dialog opened: \"'"
                + "   +norm(d.textContent).slice(0,80)+'\"') : 'no dialog appeared after Get Indent'; }");
        lastGetIndent = r == null ? "" : r.toString();
        System.out.println("Transfer: " + lastGetIndent);
        return lastGetIndent;
    }

    public boolean dialogOpen() {
        return lastGetIndent != null && lastGetIndent.startsWith("the Item Search dialog opened");
    }

    // ---- Item Search dialog: Search, tick, (OK if present) ---------------------

    /** Click <b>Search</b> inside the Item Search dialog and report how many result rows came back. */
    public String clickSearchInDialog() {
        page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return;"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))"
                + "        || /^(fn)?search\\s*\\(/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        lastDialogSearch = "clicked Search -> " + dialogItemRowCount() + " item row(s)";
        System.out.println("Transfer: " + lastDialogSearch);
        return lastDialogSearch;
    }

    /** How many selectable item rows the dialog lists — a row counts if it carries its own checkbox,
     *  excluding known filter/select-all checkboxes seen elsewhere in this module. */
    public int dialogItemRowCount() {
        Object n = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 0;"
                + " return dataRows(d).filter(r=>{"
                + "   const cb=r.querySelector('input[type=checkbox]');"
                + "   return cb && !/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll|SelectAll/i.test(ngOf(cb)); }).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean dialogItemRowsFound() { return dialogItemRowCount() > 0; }

    /**
     * Tick the first real item row's checkbox in the dialog — the requested step ("tick checkbox to
     * choose any one of the item") did not name a specific item, so the first one is used.
     */
    public String tickFirstItemInDialog() {
        Object r = page.evaluate("() => {" + JS
                + " const find=() => { const d=topDialog(); if(!d) return '';"
                + "   const cb=dataRows(d).map(rr=>rr.querySelector('input[type=checkbox]'))"
                + "     .find(e=>e && !/IsSpecialOrder|isPsychotropicdrug|ChkSelectAll|SelectAll/i.test(ngOf(e)));"
                + "   if(!cb) return '';"
                + "   if(!cb.id) cb.id='__trfItemCb';"
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
                + " return '(the checkbox would not stay attached across 8 attempts, or the dialog has no results)'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(800);
        lastItemTick = result.startsWith("checked=")
                ? "ticked the first item -> " + result
                : "(" + result + ")";
        System.out.println("Transfer: item tick -> " + lastItemTick);
        return lastItemTick;
    }

    public boolean itemTicked() { return lastItemTick != null && lastItemTick.contains("checked=true"); }

    /**
     * Click <b>OK</b> in the Item Search dialog, IF one exists — some pickers in this module close on
     * their own tick (no separate OK), so this is best-effort/non-fatal rather than assumed required.
     */
    public String clickOkInDialogIfPresent() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*ok\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog — nothing to click)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        String result = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("Transfer: " + result);
        return result;
    }

    // ---- Mark Unavailable (tab, then the real action) + toast -------------------

    /**
     * Open <b>Mark Unavailable</b> — reported plainly whether this is a real tab, a button, or absent,
     * rather than assumed. Deliberately never clicks the real Mark Unavailable action button (only a
     * genuinely separate tab/section control) — see the class doc for why. Non-fatal: the flow continues
     * to the real click regardless.
     */
    public String clickMarkUnavailableTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*mark\\s*unavailable\\s*$/i.test(norm(x.textContent))"
                + "        && !/unavailable/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Mark Unavailable tab exists on this screen — \"Mark "
                + "Unavailable\" is the same action button used in the later step, so it is left "
                + "untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Mark Unavailable tab'; }");
        lastMarkUnavailableTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("Transfer: " + lastMarkUnavailableTab);
        return lastMarkUnavailableTab;
    }

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        // Confirmed live: the real success toast is "store Indent Not Available update Successfully." —
        // this action's own name is "Mark Unavailable" / "Not Available", so the "not " exclusion below
        // (meant to catch genuine failures like "could not save") self-triggers a false negative on the
        // very phrase that names the action. A definitive "successfully" is checked FIRST and short-
        // circuits that exclusion, since it is a much stronger positive signal than "not " is a negative
        // one here.
        if (t.contains("successfully")) return true;
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated") || t.contains("unavailable");
    }

    /** Click <b>Mark Unavailable</b> (the confirming action) and return the toast. */
    public String clickMarkUnavailableAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*mark\\s*unavailable\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Mark Unavailable button)';"
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
        lastMarkUnavailable = clickedText;
        lastSaveDiagnostics = clickedText + (toast.isEmpty() ? "; no message within 15s" : "");
        lastSave = toast;
        System.out.println("Transfer: mark unavailable -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public boolean markUnavailableClicked() {
        return lastMarkUnavailable != null && lastMarkUnavailable.startsWith("clicked \"");
    }

    // ---- Mark Close (second tab of this same screen — mirrors Mark Unavailable above) -----------------

    /**
     * Open <b>Mark Close</b> — same defensive pattern as {@link #clickMarkUnavailableTab()}: never clicks
     * the real "Mark Close" action button ({@code IsCloseIssueItems()}, confirmed live sitting right next
     * to Mark Unavailable in the Item Search dialog), only a genuinely separate tab/section control,
     * reported plainly if none exists.
     */
    public String clickMarkCloseTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*mark\\s*close\\s*$/i.test(norm(x.textContent))"
                + "        && !/close/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Mark Close tab exists on this screen — \"Mark Close\" is "
                + "the same action button used in the later step, so it is left untouched here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Mark Close tab'; }");
        lastMarkCloseTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("Transfer: " + lastMarkCloseTab);
        return lastMarkCloseTab;
    }

    /** Click <b>Mark Close</b> (the confirming action) and return the toast. */
    public String clickMarkCloseAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*mark\\s*close\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Mark Close button)';"
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
        lastMarkClose = clickedText;
        lastSaveDiagnostics = clickedText + (toast.isEmpty() ? "; no message within 15s" : "");
        lastSave = toast;
        System.out.println("Transfer: mark close -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public boolean markCloseClicked() {
        return lastMarkClose != null && lastMarkClose.startsWith("clicked \"");
    }

    // ---- Print (third tab of this same screen — operates on the LIST VIEW, not the New-entry dialog) --

    /**
     * Enter the <b>From Date</b>/<b>To Date</b> on the main LIST view (confirmed live:
     * {@code IssueToStore.FromDate}/{@code ToDate}) — distinct from the New-entry form's own fields,
     * which only exist while that form is open. This tab searches the list directly, so it never opens
     * New.
     */
    public String enterListDateRange(String from, String to) {
        String r1 = typeListDate(true, from);
        String r2 = typeListDate(false, to);
        lastListDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("Transfer: " + lastListDates);
        return lastListDates;
    }

    private String typeListDate(boolean from, String value) {
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(r) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__trfList'+(r==='fromdate'?'From':'To');"
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

    public boolean listDatesEntered(String from, String to) {
        return lastListDates != null && lastListDates.contains("FromDate=" + from) && lastListDates.contains("ToDate=" + to);
    }

    /** Click the LIST view's <b>Search</b> button (confirmed live: {@code getissueindentitemslistSearch()})
     *  and report how many result rows came back. */
    public String clickListSearch() {
        // Confirmed live: this button's own ng-click is "getissueindentitemslistSearch()" — it does not
        // even start with "search", so no ng-click fallback would match it anyway; the exact text
        // "Search" is what actually finds it, and no fallback is added here to avoid the same class of
        // over-broad-match risk documented on clickPrint() below.
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        lastListSearch = "clicked Search -> " + listRowCount() + " row(s)";
        System.out.println("Transfer: " + lastListSearch);
        return lastListSearch;
    }

    public int listRowCount() {
        Object n = page.evaluate("() => {" + JS + " return dataRows(document).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean listRowsFound() { return listRowCount() > 0; }

    /**
     * Click on the first row "at the tick symbol" — the requested step ("click on the row at the tick
     * symbol to choose any one of the item") did not name a specific row, so the first one found is
     * used. Mirrors the identical leading tick-icon column pattern confirmed live on the sibling
     * {@link com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNoteApproval} screen.
     */
    public String clickFirstRowTick() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=dataRows(document);"
                + " const row=rows.find(x=>x.querySelector('a,button')) || rows[0];"
                + " if(!row) return '(no row to click)';"
                + " const text=norm(row.textContent).slice(0,90);"
                + " const tick=row.querySelector('td:first-child,.ui-grid-cell:first-child')"
                + "   || row.firstElementChild;"
                + " (tick||row).click();"
                + " return 'clicked the row at the tick symbol: '+text; }");
        lastRowTick = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("Transfer: " + lastRowTick);
        return lastRowTick;
    }

    public boolean rowTickClicked() {
        return lastRowTick != null && lastRowTick.startsWith("clicked the row at the tick symbol");
    }

    /**
     * Open <b>Print</b> — same defensive pattern as {@link #clickMarkUnavailableTab()}: never clicks the
     * real "Print" action button, only a genuinely separate tab/section control, reported plainly if none
     * exists.
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
        System.out.println("Transfer: " + lastPrintTab);
        return lastPrintTab;
    }

    /**
     * Click <b>Print</b> and return how many browser tabs existed just before the click — needed by
     * {@link com.kpj.pages.PdfReport#capture} to find the tab it opens.
     *
     * <p>Matched on EXACT visible text "Print" only, with no ng-click fallback: confirmed live this
     * screen ALSO has a "Patient Print" button ({@code PtntprintReport()}) right next to it — the exact
     * same class of collision risk seen on {@link com.kpj.pages.Inventory_page.POS_page.ItemSaleList}
     * between "Print" and "Print Drug Label".</p>
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
        System.out.println("Transfer: " + lastPrint);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean printClicked() { return lastPrint != null && lastPrint.startsWith("clicked \""); }

    // ---- Patient Print (fourth tab of this same screen — mirrors Print above) --------------------------

    /**
     * Open <b>Patient Print</b> — same defensive pattern as {@link #clickPrintTab()}: never clicks the
     * real "Patient Print" action button ({@code PtntprintReport()}, confirmed live sitting right next to
     * Print on the list view), only a genuinely separate tab/section control, reported plainly if none
     * exists.
     */
    public String clickPatientPrintTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('li,a,.nav-tabs *,[role=tab]')].filter(vis)"
                + "   .find(x=>/^\\s*patient\\s*print\\s*$/i.test(norm(x.textContent))"
                + "        && !/print/i.test(x.getAttribute('ng-click')||'')"
                + "        && !x.closest('tr,.ui-grid-row') && !x.closest('button') && x.tagName!=='BUTTON');"
                + " if(!t) return '(no separate Patient Print tab exists on this screen — \"Patient "
                + "Print\" is the same action button used in the later step, so it is left untouched "
                + "here)';"
                + " (t.tagName==='A'? t : (t.querySelector('a')||t)).click();"
                + " return 'clicked Patient Print tab'; }");
        lastPatientPrintTab = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("Transfer: " + lastPatientPrintTab);
        return lastPatientPrintTab;
    }

    /**
     * Click <b>Patient Print</b> and return how many browser tabs existed just before the click — needed
     * by {@link com.kpj.pages.PdfReport#capture} to find the tab it opens.
     *
     * <p>Matched on EXACT visible text "Patient Print" only, with no ng-click fallback — same reasoning
     * as {@link #clickPrint()}: this screen's plain "Print" button sits right next to it, and a wildcard
     * ng-click fallback risks the same button-collision class of bug seen on {@link
     * com.kpj.pages.Inventory_page.POS_page.ItemSaleList}.</p>
     */
    public int clickPatientPrint() {
        int pagesBefore = page.context().pages().size();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(vis).find(x=>/^\\s*patient\\s*print\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Patient Print button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        lastPatientPrint = clicked == null ? "" : clicked.toString();
        System.out.println("Transfer: " + lastPatientPrint);
        waitForAngular(1500);
        return pagesBefore;
    }

    public boolean patientPrintClicked() {
        return lastPatientPrint != null && lastPatientPrint.startsWith("clicked \"");
    }
}
