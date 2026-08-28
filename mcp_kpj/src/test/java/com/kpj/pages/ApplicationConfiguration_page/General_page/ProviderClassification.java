package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Provider Classification</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b> and <b>Provider Classification</b> → select the
 * <b>Service</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The screen's models are {@code providerClassification.Code}, {@code providerClassification.Description}
 * and {@code providerClassification.ServiceItemID}; Submit is {@code fnIUDProviderClassification()}, and
 * the route is {@code #/ProviderClassificationMaster}.</p>
 *
 * <p>Fields are matched on the <b>last segment</b> of the ng-model ({@code providerClassification.Code}
 * → {@code Code}), never on a keyword that also appears in the prefix. Every model here starts with the
 * screen's own name, so a search for "classification" matches the Code box just as readily as the
 * Description box — that exact trap put the area name into the code field on Area/Town and the contact's
 * phone into the agent's on Service Agent. The same rule finds Service by its tail {@code ServiceItemID}
 * rather than by "service" anywhere in the model.</p>
 */
public class ProviderClassification extends BasePage {

    public ProviderClassification(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastService = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

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
     * Application Configuration &rarr; General &rarr; Provider Classification.
     *
     * <p>The route is read off the menu link rather than guessed — this module's labels do not predict its
     * URLs (the sibling "Complaint Type" is routed {@code #/ComplaintListType}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*provider\\s*classification\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__pcMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__pcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ProviderClassification.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__pcMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '#/ProviderClassificationMaster'; }"); }
            catch (Exception ignore) { }
            waitForAngular(3000);
        }
        if (!onScreen()) {
            try { page.navigate(baseUrl + "#/ProviderClassificationMaster"); } catch (Exception ignore) { }
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

    /** On the Provider Classification screen (its own route, list or entry form). */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        return u.contains("providerclassification");
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
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,40)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== ProviderClassification CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?provider|AddProvider/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__pcAdd'; }");
        try {
            page.locator("#__pcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("ProviderClassification.clickAdd: " + e.getMessage().split("\n")[0]);
            try { page.evaluate("() => { window.location.hash = '#/add-ProviderClassification'; }"); }
            catch (Exception ignore) { }
        }
        page.evaluate("() => { const e=document.getElementById('__pcAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("ProviderClassification.clickAdd: Code field never appeared");
        }
        waitForAngular(900);
        return formOpen();
    }

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/^code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Enter <b>Code</b> and <b>Provider Classification</b>.
     *
     * <p>The two boxes are told apart by the ng-model's last segment. Matching "classification" anywhere in
     * the model would hit {@code ProviderClassification.Code} first and write the name over the code —
     * the same defect that overwrote the code on Area/Town.</p>
     */
    public String enterDetails(String code, String name) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, nm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^code$/i) || boxes.find(e=>/^\\s*code\\s*$/i.test(norm(e.placeholder||'')));"
                + " const nameEl=boxes.filter(e=>e!==codeEl)"
                + "   .find(e=>/^(providerclassification|classification|description|name|remark)$/i.test(tail(e)))"
                + "   || boxes.filter(e=>e!==codeEl)"
                + "        .find(e=>/classification|description|name|remark/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-code-field)';"
                + " const rn = nameEl? setEl(nameEl,nm)+' ['+(ngOf(nameEl)||'?')+']' : '(no-classification-field)';"
                + " return 'Code='+rc+' | ProviderClassification='+rn; }",
                java.util.List.of(code, name));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ProviderClassification: " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold what was typed — read back from the field, not from the fact a click happened. */
    public boolean detailsEntered(String code, String name) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code)
                && lastEntry.contains("ProviderClassification=" + name);
    }

    /**
     * Select the <b>Service</b>.
     *
     * <p>The list is filled asynchronously, so it is waited for: a single read lands on the empty list and
     * reports "(no-option)" for a screen that is merely still loading — the false negative that made
     * Postal's City look permanently empty.</p>
     */
    public String selectService() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')]"
                    + " .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " .filter(e=>!/pagination/i.test(e.getAttribute('ng-model')||''))"
                    + " .some(e=>[...e.options].some(o=>o.value"
                    + "   && !/^-*\\s*select/i.test((o.text||'').trim())))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("ProviderClassification.selectService: no populated dropdown within 15s");
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__pcService').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(e=>!/pagination/i.test(ngOf(e)));"
                + " const e=sels.find(s=>/service/i.test(tail(s)))"
                + "   || sels.find(s=>/service/i.test(ngOf(s)+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__pcService';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length}; }");
        if (info == null) { lastService = "(no service dropdown)"; return lastService; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) { lastService = "(no-option) [" + m.get("ng") + "]"; return lastService; }
        try {
            page.locator("#__pcService").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            lastService = "(select failed: " + e.getMessage().split("\n")[0] + ")";
            return lastService;
        }
        waitForAngular(900);
        // Read the choice back off the control, so a click that did not bind cannot pass as a selection.
        Object t = page.evaluate("() => { const e=document.getElementById('__pcService');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastService = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("ProviderClassification: Service = " + lastService);
        return lastService;
    }

    public boolean serviceSelected() {
        return lastService != null && !lastService.isEmpty() && !lastService.startsWith("(")
                && !lastService.matches("^-*\\s*[Ss]elect.*");
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

    /**
     * Click <b>Submit</b> and return the toast.
     *
     * <p>Toasts are CLEARED first rather than snapshot-and-excluded, so a repeated message still reads, and
     * every call is guarded — Submit on these screens can be a real form post that reloads the page.</p>
     */
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
                    + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis)"
                    + "   .filter(x=>!/cke/i.test(x.className||''))"
                    + "   .find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /submit|fnIUD/i.test(x.getAttribute('ng-click')||''));"
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
                        + "|added successfully|updated successfully|message not found|please (enter|select))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("ProviderClassification: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model. Taking the
     * first one filters whichever column happens to come first and empties the grid — that is what
     * "0 rows shown" meant on Postal. The Code column's own box is used, and the row must carry the code
     * AND the classification name, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String name) {
        waitForAngular(1500);
        // A refused Submit leaves the entry form on screen, where there is no grid to read at all.
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
            page.evaluate("(c) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.name? withCode.find(t=>t.includes(a.name)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "name", name == null ? "" : name));
            lastListCheck = "looked for \"" + code + "\" + \"" + name + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("ProviderClassification: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
