package com.kpj.pages.ApplicationConfiguration_page.Maintenance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Maintenance &gt; <b>Type of Return</b> — Page Object.
 *
 * <p>Flow: enter <b>Code</b> and <b>Remark</b> → enter the <b>MIMS GUID</b> and <b>MIMS
 * Description</b> → select the <b>MIMS Type</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class MaintenanceComplaintType extends BasePage {

    public MaintenanceComplaintType(Page page) { super(page); }

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
     * Application Configuration &rarr; Maintenance &rarr; MaintenanceComplaintType.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*maintenance\\s*$");
        lastMenu = describeMenu();

        // Anchored to the whole label. A loose match on "generic" would take the first menu entry that
        // merely contains the word — the scaffolding trap that once ran an entire Compliance flow
        // against Complaint Type and reported PASS against the wrong screen's fields.
        Object href = page.evaluate("() => {" + JS
                    + " const named=[...document.querySelectorAll('a[href]')]"
                    + "   .filter(x=>/^\\s*complaint\\s*type\\s*$/i.test(norm(x.textContent)));"
                    // Equipment has a screen of the same name; take the MAINTENANCE one by its route,
                    // then the visible one, and only then fall back to the first.
                    + " const a=named.find(x=>/main/i.test(x.getAttribute('href')||''))"
                    + "   || named.filter(vis)[0] || named[0];"
                    + " if(!a) return ''; a.id='__mctMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__mctMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("MaintenanceComplaintType.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__mctMenu'); if(e) e.removeAttribute('id'); }");
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
            System.out.println("MaintenanceComplaintType.waitForScreenControls: no Code box or grid within "
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
     * On the MaintenanceComplaintType screen — matched on the hash route, never on page text.
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
        if (route.contains("maincomplainttype") || route.contains("maincomplaint")
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
        System.out.println("=== MaintenanceComplaintType CONTROLS ===\n" + lastControls);
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
                + "        || /add-?complaint|AddMaintenanceComplaintType|MAINCOMPLAINT/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__mctAdd'; }");
        try {
            page.locator("#__mctAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("MaintenanceComplaintType.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__mctAdd'); if(e) e.removeAttribute('id'); }");
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
                // Anything MIMS is off limits here — mimsDescription is a different field with its own
                // step, and "description" matches both.
                + " const rest=boxes.filter(e=>e!==codeEl && !/mims/i.test(tail(e)+' '+(e.placeholder||'')));"
                + " const remEl=rest.find(e=>/^(remark|remarks|description|name)$/i.test(tail(e)))"
                + "   || rest.find(e=>/(remark|description|name)$/i.test(tail(e)))"
                + "   || rest.find(e=>/remark|description/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']';"
                + " const rr = remEl? setEl(remEl,rm)+' ['+(ngOf(remEl)||'?')+']' : '(no-remark-field)';"
                + " return 'Code='+rc+' | Remark='+rr; }",
                java.util.List.of(code, remark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("MaintenanceComplaintType: " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold their OWN value — the check that catches a remark written over the code. */
    public boolean detailsEntered(String code, String remark) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code) && lastEntry.contains("Remark=" + remark);
    }

    public String lastMims = "", lastMimsType = "";

    /**
     * Enter the <b>MIMS GUID</b> and <b>MIMS Description</b>.
     *
     * <p>Pinned to {@code commonmaster.mimsGUID} and {@code commonmaster.mimsDescription}, and the two are
     * excluded from each other so the GUID cannot land in the description box. The record's own Remark is
     * excluded from both — this form carries two description boxes.</p>
     */
    public String enterMims(String guid, String description) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [g, d] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const guidEl=boxes.find(e=>/^mimsguid$/i.test(tail(e)))"
                + "   || boxes.find(e=>/guid/i.test(tail(e)+' '+(e.placeholder||'')));"
                + " const descEl=boxes.filter(e=>e!==guidEl)"
                + "     .find(e=>/^mimsdescription$/i.test(tail(e)))"
                + "   || boxes.filter(e=>e!==guidEl).find(e=>/mims.*desc/i.test(tail(e)+' '+(e.placeholder||'')));"
                + " const rg = guidEl? setEl(guidEl,g)+' ['+(ngOf(guidEl)||'?')+']' : '(no-MIMS-GUID-field)';"
                + " const rd = descEl? setEl(descEl,d)+' ['+(ngOf(descEl)||'?')+']'"
                + "                  : '(no-MIMS-Description-field)';"
                + " return 'MIMSGUID='+rg+' | MIMSDescription='+rd; }",
                java.util.List.of(guid, description));
        lastMims = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("MaintenanceComplaintType: " + lastMims);
        return lastMims;
    }

    public boolean mimsEntered(String guid, String description) {
        return lastMims != null && !lastMims.contains("(no-")
                && lastMims.contains("MIMSGUID=" + guid)
                && lastMims.contains("MIMSDescription=" + description);
    }

    /**
     * Select the <b>MIMS Type</b> ({@code commonmaster.mimsTypeId}).
     *
     * <p>Pinned to that model. The screen's OTHER select is the master chooser at the top, and picking
     * that one switches the whole screen to a different master rather than setting a field. The list is
     * waited for, since a single early read reports "(no-option)" for a list still loading.</p>
     */
    public String selectMimsType() {
        long deadline = System.currentTimeMillis() + 12000;
        int n = 0;
        while (n == 0 && System.currentTimeMillis() < deadline) {
            Object c = page.evaluate("() => {" + JS
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(s=>/mimstype/i.test(tail(s)));"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }");
            n = c instanceof Number ? ((Number) c).intValue() : 0;
            if (n == 0) page.waitForTimeout(400);
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__mctMims').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>/mimstype/i.test(tail(s)));"
                + " if(!e) return null; e.id='__mctMims';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length}; }");
        if (info == null) { lastMimsType = "(no MIMS Type dropdown)"; return lastMimsType; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) { lastMimsType = "(no-option) [" + m.get("ng") + "]"; return lastMimsType; }
        try {
            page.locator("#__mctMims").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            lastMimsType = "(select failed: " + e.getMessage().split("\n")[0] + ")";
            return lastMimsType;
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__mctMims');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastMimsType = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("MaintenanceComplaintType: MIMS Type = " + lastMimsType);
        return lastMimsType;
    }

    public boolean mimsTypeSelected() {
        return lastMimsType != null && !lastMimsType.isEmpty() && !lastMimsType.startsWith("(")
                && !lastMimsType.matches("^-*\\s*[Ss]elect.*");
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

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("MaintenanceComplaintType: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
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

            // The column filter can empty the grid instead of narrowing it - the box belongs to whichever
            // column ui-grid rendered it in, and on a short list that leaves nothing showing while the
            // record is plainly there. So when the filtered read finds nothing, CLEAR the filter and read
            // the rows as they stand. A save that really did not happen still reports "not in the list".
            if (hit != null && !hit.toString().contains("FOUND:")) {
                page.evaluate("() => {" + JS
                        + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                        + "   .filter(vis);"
                        + " boxes.forEach(f=>{ f.value='';"
                        + "   try{ const ct=angular.element(f).controller('ngModel');"
                        + "        if(ct){ ct.$setViewValue(''); ct.$render(); } }catch(e){}"
                        + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "   f.dispatchEvent(new Event('change',{bubbles:true})); }); }");
                waitForAngular(2000);
                Object unfiltered = page.evaluate("(a) => {" + JS
                        + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                        + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                        + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                        + " const mine=a.v? withCode.find(t=>t.includes(a.v)) : withCode[0];"
                        + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                        + " return 'still not there ('+rows.length+' rows read unfiltered)'; }",
                        java.util.Map.of("c", code, "v", remark == null ? "" : remark));
                lastListCheck += "  ||  the filter had emptied the grid, so it was cleared and the rows "
                        + "read as they stand -> " + (unfiltered == null ? "" : unfiltered);
            }
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("MaintenanceComplaintType: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
