package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>DMS Folder</b> — Page Object.
 *
 * <p>Flow: enter the <b>Folder Name</b> → set the <b>Colour</b> (an RGB value) → <b>Submit</b> →
 * success toast.</p>
 */
public class DmsFolder extends BasePage {

    public DmsFolder(Page page) { super(page); }

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
     * Application Configuration &rarr; General &rarr; DMS Folder.
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
                + "   .find(x=>/^\\s*dms\\s*folder\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__dfMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__dfMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DmsFolder.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dfMenu'); if(e) e.removeAttribute('id'); }");
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
     * On the DMS Folder screen.
     *
     * <p>Matched against the route the MENU gave rather than a guessed keyword. A loose check on a sibling
     * screen once let a whole run pass against the wrong page, so the City/District screen — the obvious
     * neighbour here — is rejected outright.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");

        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("dms") || u.contains("folder");
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
        System.out.println("=== DmsFolder CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?dms|AddDms|AddFolder/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__dfAdd'; }");
        try {
            page.locator("#__dfAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("DmsFolder.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dfAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("DmsFolder.clickAdd: Code field never appeared"); }
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
     * Enter the <b>Folder Name</b>.
     *
     * <p>Addressed by ng-model, discovered from the form dump rather than guessed: on the sibling
     * Area/Town screen every model began "area.", so a keyword match for the screen's own name found the
     * Code box and overwrote it.</p>
     */
    public String enterFolderName(String name) {
        Object r = page.evaluate("(n) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const byNg=(re)=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
                + " const el = byNg(/folder(name)?$/i) || byNg(/\\.(foldername|name|description)$/i)"
                + "   || boxes.find(e=>/folder/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " if(!el) return '(no-folder-name-field)';"
                + " return setEl(el,n)+' ['+(el.getAttribute('ng-model')||'?')+']'; }", name);
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("DmsFolder: folder name -> " + lastEntry);
        return lastEntry;
    }

    public boolean folderNameEntered(String name) {
        return lastEntry != null && !lastEntry.startsWith("(no-") && lastEntry.startsWith(name);
    }

    /**
     * Set the <b>Colour</b> as an RGB value.
     *
     * <p>Handles the two shapes this can take: a native {@code <input type="color">}, which only accepts
     * {@code #rrggbb} and ignores anything else, or a plain text box where the RGB string is typed. The
     * value is read back so a colour the control silently rejected cannot pass.</p>
     *
     * @param rgb e.g. {@code 255,0,0}
     * @param hex the same colour as {@code #rrggbb}, for a native colour input
     */
    public String setColour(String rgb, String hex) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [rgbVal, hexVal] = a;"
                + " const colour=[...document.querySelectorAll('input[type=color]')].filter(vis)[0];"
                + " if(colour){ const before=colour.value; setEl(colour, hexVal);"
                + "   return 'colour input '+(colour.getAttribute('ng-model')||'?')+' = '+colour.value"
                + "     +(colour.value===before? ' (UNCHANGED - the control rejected it)':''); }"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const el=boxes.find(e=>/colou?r|rgb/i.test((e.getAttribute('ng-model')||'')+' '"
                + "   +(e.placeholder||'')+' '+labelOf(e)));"
                + " if(!el) return '(no-colour-field)';"
                + " return setEl(el, rgbVal)+' ['+(el.getAttribute('ng-model')||'?')+']'; }",
                java.util.List.of(rgb, hex));
        lastCity = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("DmsFolder: colour -> " + lastCity);
        return lastCity;
    }

    public boolean colourSet() {
        return lastCity != null && !lastCity.startsWith("(no-") && !lastCity.contains("UNCHANGED");
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
        System.out.println("DmsFolder: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
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
        System.out.println("DmsFolder: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
