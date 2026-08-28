package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>E-mail Template</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter the <b>Code</b>, <b>Template Name</b> and <b>Subject</b> → type the
 * remark into the <b>template body</b> → <b>Save</b> → success toast.</p>
 */
public class EmailTemplate extends BasePage {

    public EmailTemplate(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastBody = "",
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
     * Application Configuration &rarr; General &rarr; E-mail Template.
     *
     * <p>The route is taken from the menu link, never guessed — labels in this module do not predict URLs
     * ("Complaint Type" is routed {@code #/ComplaintListType}). The menu click is retried: these menus are
     * built after login and a click that lands too early expands nothing.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) page.waitForTimeout(1500);
            clickMenu("^\\s*application\\s*configuration\\s*$");
            clickMenu("^\\s*general\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    // "E-mail Template" / "Email Template" — the hyphen is not dependable.
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*e\\s*-?\\s*mail\\s*template\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__etMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__etMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("EmailTemplate.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__etMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On the E-mail Template screen — matched against the route the MENU gave, not a guessed keyword. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("mailtemplate") || u.contains("emailtemplate");
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
        System.out.println("=== EmailTemplate CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?mail|AddMail|AddEmail|AddTemplate/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__etAdd'; }");
        try {
            page.locator("#__etAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("EmailTemplate.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__etAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea,select')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code|subject|template/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("EmailTemplate.clickAdd: form fields never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea,select')].filter(vis)"
                + "   .some(e=>/code|subject|template/i.test((e.getAttribute('ng-model')||'')+' '"
                + "     +(e.placeholder||'')+' '+labelOf(e))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Enter the <b>Code</b>, <b>Template Name</b> and <b>Subject</b>.
     *
     * <p>Each is taken by its own ng-model, and a control is used only once — on these forms every model
     * shares a prefix, so a keyword match can hand back the same box twice and one value silently
     * overwrites the other (that is exactly what happened on Area/Town). Dropdowns are handled too, since
     * Code can be a picker rather than a text box on this screen.</p>
     */
    public String enterCodeNameSubject(String code, String templateName, String subject) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, n, s] = a;"
                + " const used=new Set();"
                + " const fields=[...document.querySelectorAll('input,textarea,select')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|pagination/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const find=(re)=>fields.find(e=>!used.has(e)"
                + "   && re.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')+' '+labelOf(e)));"
                + " const put=(e,v)=>{ if(!e) return '(no-field)'; used.add(e);"
                + "   if(e.tagName==='SELECT'){"
                + "     const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.text)));"
                + "     if(i<0) return '(no-option)';"
                + "     e.selectedIndex=i;"
                + "     try{ const ct=angular.element(e).controller('ngModel');"
                + "          if(ct){ ct.$setViewValue(e.value); ct.$render(); } }catch(x){}"
                + "     e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     return norm(e.options[i].text); }"
                + "   return setEl(e, v); };"
                + " const codeEl=find(/code/i);"
                + " const nameEl=find(/template\\s*name|templatename|(?:^|\\.)name$/i);"
                + " const subjEl=find(/subject/i);"
                + " const ng=e=>e? ' ['+(e.getAttribute('ng-model')||'?')+']' : '';"
                + " const rc=put(codeEl,c), rn=put(nameEl,n), rs=put(subjEl,s);"
                + " return 'Code='+rc+ng(codeEl)+' | TemplateName='+rn+ng(nameEl)"
                + "   +' | Subject='+rs+ng(subjEl); }",
                java.util.List.of(code, templateName, subject));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("EmailTemplate: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered() {
        return lastEntry != null && !lastEntry.contains("(no-field)") && !lastEntry.contains("(no-option)");
    }

    /**
     * Type the remark into the <b>template body</b>.
     *
     * <p>The body is a CKEditor on these screens, and writing the underlying element does nothing — the
     * editor keeps its own document and overwrites it. {@code setData} + {@code updateElement} is what
     * reaches the model, with a plain contenteditable or textarea as the fallback.</p>
     */
    public String enterTemplateBody(String text) {
        Object r = page.evaluate("(t) => {" + JS
                + " if(window.CKEDITOR && CKEDITOR.instances){"
                + "   const keys=Object.keys(CKEDITOR.instances);"
                + "   if(keys.length){ for(const k of keys){ try{ CKEDITOR.instances[k].setData(t);"
                + "       CKEDITOR.instances[k].updateElement(); }catch(e){} }"
                + "     return 'CKEDITOR('+keys.length+'): '+keys.join(','); } }"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].filter(vis)[0];"
                + " if(ce){ ce.innerHTML=t; ce.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   return 'contenteditable'; }"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)[0];"
                + " if(ta) return 'textarea: '+setEl(ta, t);"
                + " return '(no-editor)'; }", text);
        lastBody = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("EmailTemplate: template body -> " + lastBody);
        return lastBody;
    }

    public boolean bodyEntered() { return lastBody != null && !lastBody.startsWith("(no-"); }

    /** A success message, judged strictly — these screens phrase refusals with the word "Added". */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /**
     * Click <b>Save</b> and return the toast.
     *
     * <p>This screen's button is Save, not Submit. Toasts are cleared first so a repeated message still
     * reads, and every call is guarded — Save on these screens can be a real form post that reloads.</p>
     */
    public String saveAndGetToast() {
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
                    // CKEditor's toolbar carries its OWN "Save" button, and it comes first in the DOM —
                    // clicking it does nothing to the form. Anything inside the editor chrome (.cke*) is
                    // excluded, and the button carrying the form's own handler is preferred outright.
                    + " const inEditor=e=>!!e.closest('.cke, .cke_toolbox, .cke_top, .cke_inner');"
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(e=>!inEditor(e));"
                    + " const b=all.find(x=>/fnIUD|save|submit/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return '(no Save button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Save click threw (the page navigated): " + e.getMessage().split("\n")[0];
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
            // Screens in this module can answer in a banner that carries no toast class.
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
        System.out.println("EmailTemplate: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the template up in the screen's own grid.
     *
     * <p>The grid is widened and scanned rather than filtered: the District screen's column filter turned
     * out to be bound to one column only, and typing a code into it emptied the grid — reporting a record
     * that had just saved as missing.</p>
     */
    public boolean inList(String needle) {
        waitForAngular(1500);
        try {
            page.evaluate("() => {" + JS
                    + " const onForm=[...document.querySelectorAll('input')].filter(vis)"
                    + "   .some(e=>/subject/i.test(e.getAttribute('ng-model')||''));"
                    + " if(onForm){ const b=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "     .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                    + "          || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                    + "   if(b) b.click(); }"
                    + " const size=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                    + " if(size){ size.selectedIndex=size.options.length-1;"
                    + "   size.dispatchEvent(new Event('change',{bubbles:true})); } }");
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,120) : 'not in the list ('+rows.length+' rows shown)'; }",
                    needle);
            lastListCheck = "looked for \"" + needle + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("EmailTemplate: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
