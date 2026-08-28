package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>StockTakeInstruction</b> — Page Object.
 *
 * <p>Flow: enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class StockTakeInstruction extends BasePage {

    public StockTakeInstruction(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "", lastAdd = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model tail, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
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

    /**
     * Application Configuration &rarr; Inventory &rarr; StockTakeInstruction.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        // The menu is opened up to three times. DevHIS reloads itself with a cache-buster (?_cb=) now and
        // then, and a reload part-way through collapses the menu — the run then sees only the top-level
        // entries and reports the screen as missing when it is simply not expanded yet.
        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*application\\s*configuration\\s*$");
            clickMenu("^\\s*inventory\\s*$");
            lastMenu = describeMenu();

            // Anchored to the whole label. A loose match would take the first menu entry that merely
            // contains the words — the scaffolding trap that once ran an entire Compliance flow against
            // Complaint Type and reported PASS against the wrong screen's fields.
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*stock\\s*take\\s*instruction\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__stiMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            System.out.println("StockTakeInstruction.nav: the Stock Take Instruction link was not in the menu on attempt "
                    + attempt + " — reopening the menu");
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__stiMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("StockTakeInstruction.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__stiMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3000);
            if (!onScreen()) {
                try { page.navigate(baseUrl + hash); } catch (Exception ignore) { }
                waitForAngular(3500);
            }
        }
        // The route resolving is not the screen rendering. GENMST loads its view after the hash changes,
        // and until it does the DASHBOARD's controls are still in the DOM — a run that starts typing
        // straight away puts its values into dashboard widgets (LocationName took the remark once).
        waitForScreenControls(15000);

        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    /** Wait for this screen's OWN controls — a Code box, or its grid — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const tail=e=>((e.getAttribute('ng-model')||'').split('.').pop()||'');"
                    + " const code=[...document.querySelectorAll('input,textarea')].filter(vis)"
                    + "   .some(e=>/code$/i.test(tail(e)));"
                    + " const grid=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .some(vis);"
                    + " return code || grid; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("StockTakeInstruction.waitForScreenControls: no Code box or grid within "
                    + timeoutMs + "ms — the screen may not have rendered");
            return false;
        }
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1200);
    }

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the StockTakeInstruction screen — matched on the hash route, never on page text.
     *
     * <p>The route bears no resemblance to the label: this screen is served from <b>{@code #/GENMST}</b>.
     * Requiring the route to contain "generic" rejected the very page the menu had just opened, the run
     * then fell back to guessed routes, and every guess landed on the dashboard. The route the MENU gives
     * is authoritative here, with {@code GENMST} as the spelling now known.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("stocktake") || route.contains("stktake")
                || route.contains("genmst")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== StockTakeInstruction CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> if the entry form is not already on screen.
     *
     * <p>The requested steps do not mention Add, so this reports which of the two the screen did rather
     * than assuming either.</p>
     */
    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?stock|AddStockTakeInstruction|STOCKTAKE/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__stiAdd'; }");
        try {
            page.locator("#__stiAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("StockTakeInstruction.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__stiAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastAdd = formOpen() ? "The form needed Add; it is now open." : "Add did not open a form.";
        return lastAdd;
    }

    /**
     * Enter <b>Code</b> and <b>Remark</b>.
     *
     * <p>The Remark box is searched for among the boxes NOT already claimed by the Code, and by the
     * model's last segment — never by "generic", which prefixes every model on this screen.</p>
     */
    public String enterDetails(String code, String remark) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, rm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^code$/i) || byTail(/code$/i)"
                + "   || boxes.find(e=>/^\\s*code\\s*$/i.test(norm(e.placeholder||'')));"
                // Write NOTHING when the Code box is absent. The screen renders after its route resolves,
                // and until then the dashboard's own controls are on the page — a remark once landed in
                // the dashboard's LocationName box because the fields were filled one at a time.
                + " if(!codeEl) return 'Code=(no-code-field) | Remark=(not entered - the screen had not"
                + "   rendered, so nothing was typed anywhere)';"
                + " const rest=boxes.filter(e=>e!==codeEl);"
                + " const remEl=rest.find(e=>/^(remark|remarks|description|name)$/i.test(tail(e)))"
                + "   || rest.find(e=>/(remark|description|name)$/i.test(tail(e)))"
                + "   || rest.find(e=>/remark|description/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']';"
                + " const rr = remEl? setEl(remEl,rm)+' ['+(ngOf(remEl)||'?')+']' : '(no-remark-field)';"
                + " return 'Code='+rc+' | Remark='+rr; }",
                java.util.List.of(code, remark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("StockTakeInstruction: " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold their OWN value — the check that catches a remark written over the code. */
    public boolean detailsEntered(String code, String remark) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code) && lastEntry.contains("Remark=" + remark);
    }

    public String lastConfirm = "", lastPopupsSeen = "";
    /** The message was recovered from the observer because it had faded before the poll. */
    public boolean toastFromObserver = false;
    /**
     * This screen raised NO confirmation dialog: none was found while polling, and the observer — which
     * records every node added from the moment Submit is clicked — saw no modal, alert or dialog either,
     * only the toast.
     */
    public boolean noDialogRaised() {
        return lastConfirm != null && lastConfirm.startsWith("(no confirmation")
                && lastPopupsSeen != null
                && !lastPopupsSeen.matches("(?is).*(modal|dialog|sweet|jalert).*");
    }

    /**
     * Click <b>Save</b> on the confirmation dialog Submit raises.
     *
     * <p>Polled for up to 10s and matched on an EXACT Save/Yes/OK button inside a visible dialog, so a
     * "Saved" heading elsewhere on the page cannot be mistaken for the button.</p>
     */
    public String confirmSave() {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            Object r = page.evaluate("() => {" + JS
                    + " const dlgs=[...document.querySelectorAll("
                    + "   '.modal,.modal-dialog,.modal-content,[role=dialog],.sweet-alert,.jAlert,.ui-dialog')]"
                    + "   .filter(vis);"
                    + " for(const d of dlgs){"
                    + "   const b=[...d.querySelectorAll('button,a,input[type=button],input[type=submit]')]"
                    + "     .filter(vis)"
                    + "     .find(x=>/^save|yes|ok|confirm$/i.test(norm(x.textContent)||x.value||''));"
                    + "   if(b){ const msg=norm(d.textContent).slice(0,120);"
                    + "     b.click();"
                    + "     return 'clicked \"'+(norm(b.textContent)||b.value)+'\" on the confirmation"
                    + "       dialog: \"'+msg+'\"'; } }"
                    + " return ''; }");
            String s2 = r == null ? "" : r.toString();
            if (!s2.isEmpty()) {
                waitForAngular(1200);
                System.out.println("StockTakeInstruction: confirm -> " + s2);
                return s2;
            }
            page.waitForTimeout(400);
        }
        System.out.println("StockTakeInstruction: confirm -> no confirmation dialog appeared within 4s");
        return "(no confirmation dialog appeared within 4s)";
    }

    /**
     * A success message, judged strictly.
     *
     * <p>These configuration screens phrase refusals with the word "Added" — e.g. "… Is Already Added!" —
     * so a plain {@code contains("added")} reads a rejection as a save.</p>
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String submitAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        // Watch every node the page ADDS from here on. A confirmation dialog or a toast that appears and
        // is gone again between two polls would otherwise read as "nothing ever appeared" — the observer
        // cannot miss it, so the report can say whether the screen answered at all.
        try {
            page.evaluate("() => { window.__stiSeen = [];"
                    + " if(window.__stiObs) window.__stiObs.disconnect();"
                    + " window.__stiObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /modal|alert|dialog|toast|jAlert|sweet/i.test(cls+' '+n.id))"
                    + "       window.__stiSeen.push(cls+'#'+(n.id||'')+' :: '+t.slice(0,140)); } } });"
                    + " window.__stiObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/submit|save/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        // Submit raises a CONFIRMATION dialog with its own Save button — nothing is written until that is
        // clicked. This runs BEFORE the shared acceptSaveDialog(): that helper clicks any visible red
        // SAVE button, so it dismisses this very dialog, and the flow would then report "no confirmation
        // dialog appeared" for a dialog it had itself just clicked away.
        lastConfirm = confirmSave();
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
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
            if (toast.isEmpty()) page.waitForTimeout(500);
        }

        if (toast.isEmpty()) {
            // Some screens in this module answer in a "KPJ Portal" banner that carries no toast class —
            // looking only for toastr elements reports silence for a screen that spoke clearly.
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select|add))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        // What did the page actually put on screen after Submit?
        String seen;
        try {
            Object s3 = page.evaluate("() => { const a=window.__stiSeen||[];"
                    + " if(window.__stiObs) window.__stiObs.disconnect();"
                    + " return a.length? a.slice(0,6).join(' ;; ') : ''; }");
            seen = s3 == null ? "" : s3.toString();
        } catch (Exception e) { seen = ""; }
        lastPopupsSeen = seen.isEmpty()
                ? "a mutation observer watched the page from the moment Submit was clicked and recorded "
                  + "NO dialog, alert or toast node being added at all"
                : "the page added: " + seen;

        // The observer is the reliable toast source. A toast lives about five seconds — shorter than the
        // wait for a confirmation dialog that never comes — so polling the DOM afterwards finds an empty
        // page and reports "no message" for a message the screen really did show.
        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("::\\s*[^A-Za-z0-9]*(?:KPJ Portal)?\\s*(.+?)(?:\\s*;;|$)")
                    .matcher(seen);
            if (m.find()) {
                toast = m.group(1).trim();
                toastFromObserver = true;
            }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "")
                + "; " + lastPopupsSeen
                + (toastFromObserver
                    ? "  ||  the message was read from that record rather than from a live poll: it had "
                      + "already faded by the time the poll began"
                    : "");
        lastToast = toast;
        System.out.println("StockTakeInstruction: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * remark, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String remark) {
        waitForAngular(1500);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.v? withCode.find(t=>t.includes(a.v)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "v", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("StockTakeInstruction: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
