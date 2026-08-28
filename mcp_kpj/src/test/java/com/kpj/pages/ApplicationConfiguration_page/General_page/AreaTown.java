package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Area/Town</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → select the <b>City/District</b> → enter the <b>Code</b> and
 * <b>Area/Town</b> → <b>Submit</b> → success toast.</p>
 */
public class AreaTown extends BasePage {

    public AreaTown(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastCity = "", lastEntry = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
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
     * Application Configuration &rarr; General &rarr; Area/Town.
     *
     * <p>The route is taken from the menu link, never guessed: labels in this module do not predict URLs —
     * "Complaint Type" is routed {@code #/ComplaintListType} — so a hand-written route lands on the
     * dashboard.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*area\\s*\\/?\\s*town\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__atMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__atMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AreaTown.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__atMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            final String r = lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", r); } catch (Exception ignore) { }
            waitForAngular(3000);
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

    /**
     * On the Area/Town screen.
     *
     * <p>Matched against the route the MENU gave rather than a guessed keyword. A loose check on a sibling
     * screen once let a whole run pass against the wrong page, so the City/District screen — the obvious
     * neighbour here — is rejected outright.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (u.contains("citydistrict") || u.contains("city")) return false;
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("area") || u.contains("town");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
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
        System.out.println("=== AreaTown CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?area|AddArea/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__atAdd'; }");
        try {
            page.locator("#__atAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("AreaTown.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__atAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("AreaTown.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .some(e=>/code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')+' '+labelOf(e))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Select the <b>City/District</b>.
     *
     * <p>The list is waited for — these dropdowns fill asynchronously, and a single read can land on the
     * empty list and report "(no-option)" for a screen that is merely still loading. Selected natively so
     * any ng-change fires.</p>
     */
    public String selectCityDistrict() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')]"
                    + " .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " .filter(e=>!/pagination/i.test(e.getAttribute('ng-model')||''))"
                    + " .some(e=>[...e.options].some(o=>o.value"
                    + "   && !/^-*\\s*select/i.test((o.text||'').trim())))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("AreaTown.selectCityDistrict: no populated dropdown within 15s");
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__atCity').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(e=>!/pagination/i.test(e.getAttribute('ng-model')||''));"
                + " const e=sels.find(s=>/city|district/i.test((s.getAttribute('ng-model')||'')+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__atCity';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return reals.length? {index:reals[0].i, ng:(e.getAttribute('ng-model')||'?'),"
                + "   count:reals.length} : null; }");
        if (info == null) { lastCity = "(no-option)"; return lastCity; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        try {
            page.locator("#__atCity").selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setIndex(((Number) m.get("index")).intValue()));
        } catch (Exception e) { lastCity = "(select failed: " + e.getMessage() + ")"; return lastCity; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__atCity');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastCity = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("AreaTown: City/District = " + lastCity);
        return lastCity;
    }

    public boolean citySelected() { return lastCity != null && !lastCity.startsWith("("); }

    /**
     * Enter the <b>Code</b> and the <b>Area/Town</b> name.
     *
     * <p>The area name is matched on an area/town model first. A bare "name" match is unsafe on these
     * screens — a sibling form has both an agent Name and a contact Name, and the wrong one silently took
     * the value there.</p>
     */
    public String enterCodeAndArea(String code, String area) {
        // Pinned to area.Code and area.Description. EVERY model on this form starts with "area.", so a
        // keyword match for "area" finds area.Code — the area name then overwrote the code, Description
        // stayed empty, and Submit answered "Please Enter Area!". Same trap as network.* on Network Master.
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, ar] = a;"
                + " const byModel=(ng)=>[...document.querySelectorAll("
                + "     \"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(vis);"
                + " const codeEl=byModel('area.Code'), areaEl=byModel('area.Description');"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(codeEl.getAttribute('ng-model')||'?')+']' : '(no-code-field)';"
                + " const ra = areaEl? setEl(areaEl,ar)+' ['+(areaEl.getAttribute('ng-model')||'?')+']' : '(no-area-field)';"
                + " return 'Code='+rc+' | AreaTown='+ra; }", java.util.List.of(code, area));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("AreaTown: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code) {
        return lastEntry != null && !lastEntry.contains("(no-") && lastEntry.contains("Code=" + code);
    }

    /** A success message, judged strictly — these screens phrase refusals with the word "Added". */
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
                    + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /submit/i.test(x.getAttribute('ng-click')||''));"
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
        System.out.println("AreaTown: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /** Look the code up on the screen's own grid — the toast alone is not proof of a write. */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        try {
            page.evaluate("(c) => {" + JS
                    + " const f=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis)[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,120) : 'not in the list ('+rows.length+' rows shown)'; }",
                    code);
            lastListCheck = "looked for \"" + code + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("AreaTown: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
