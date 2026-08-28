package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>SMS/WhatsApp Template</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b>, <b>Template Name</b> and <b>Remark</b> → tick the
 * <b>SMS</b> or <b>WhatsApp</b> checkbox → <b>Submit</b> → success toast.</p>
 *
 * <p>Fields are matched on the <b>last segment</b> of the ng-model, never on a keyword that also appears
 * in the prefix — every model here is prefixed with the screen's own name, and a loose match has
 * previously written the name over the code on a sibling screen.</p>
 *
 * <p>The Remark may be a CKEditor: writing the underlying element does nothing, because the editor keeps
 * its own document and overwrites it, so {@code setData} + {@code updateElement} is used when one is
 * present. CKEditor also contributes its own <b>Save</b> button with {@code ng-click="-"} — clicking that
 * does nothing and reads as a screen that will not save, so editor chrome is excluded when submitting.</p>
 */
public class SmsWhatsappTemplate extends BasePage {

    public SmsWhatsappTemplate(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastRemark = "",
            lastTick = "", lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

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
     * Application Configuration &rarr; General &rarr; SMS/WhatsApp Template.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*sms\\s*\\/?\\s*whats\\s*app\\s*template\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__swMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__swMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SmsWhatsappTemplate.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__swMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On the SMS/WhatsApp Template screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        // Must not match the sibling "SMS E-mail Configuration" screen.
        if (route.contains("config") && !route.contains("template")) return false;
        return route.contains("sms") || route.contains("whatsapp");
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
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,40)+'\"'); }"
                + " return out.join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== SmsWhatsappTemplate CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(x=>!/cke/i.test(x.className||''))"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?sms|AddSms|AddWhats/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__swAdd'; }");
        try {
            page.locator("#__swAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("SmsWhatsappTemplate.clickAdd: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__swAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("SmsWhatsappTemplate.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    /**
     * The entry form is showing — judged on a Code box, which the list screen does not have.
     *
     * <p>The model is {@code SmsTemplate.smscode}, so the tail is "smscode" rather than "code": demanding
     * an exact "code" reported the form as never opened while it was plainly on screen.</p>
     */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Enter <b>Code</b> and <b>Template Name</b>.
     *
     * <p>Told apart by the ng-model's last segment, and the Name lookup explicitly excludes the box
     * already used for the Code — matching "template" anywhere in the model would hit the Code box first
     * and overwrite it.</p>
     */
    public String enterDetails(String code, String name) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, nm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio'"
                + "            && e.type!=='color')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^(code|templatecode|smscode)$/i)"
                + "   || byTail(/code$/i)"
                + "   || boxes.find(e=>/^\\s*code\\s*$/i.test(norm(e.placeholder||'')));"
                + " const rest=boxes.filter(e=>e!==codeEl);"
                + " const nameEl=rest.find(e=>/^(templatename|name|smstemplatename)$/i.test(tail(e)))"
                + "   || rest.find(e=>/name$/i.test(tail(e)))"
                + "   || rest.find(e=>/template\\s*name|name/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-code-field)';"
                + " const rn = nameEl? setEl(nameEl,nm)+' ['+(ngOf(nameEl)||'?')+']' : '(no-template-name-field)';"
                + " return 'Code='+rc+' | TemplateName='+rn; }",
                java.util.List.of(code, name));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("SmsWhatsappTemplate: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code, String name) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code) && lastEntry.contains("TemplateName=" + name);
    }

    /**
     * Enter the <b>Remark</b>.
     *
     * <p>Prefers a plain Remark box addressed by its model tail; falls back to the CKEditor body, where
     * writing the underlying element is ignored and only {@code setData} + {@code updateElement} reaches
     * the model.</p>
     */
    public String enterRemark(String text) {
        Object r = page.evaluate("(t) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                // The model is SmsTemplate.smsdescrption — the screen's own spelling — so an exact
                // "description" finds nothing. Matched on the stem instead, which cannot collide with
                // smscode or smstemplatename.
                + " const el=boxes.find(e=>/(remark|descr|message|body)/i.test(tail(e)))"
                + "   || boxes.find(e=>/remark|description|message|body/i.test((e.placeholder||'')+' '+labelOf(e)))"
                + "   || boxes.find(e=>e.tagName==='TEXTAREA');"
                + " if(el) return setEl(el,t)+' ['+(ngOf(el)||'?')+']';"
                + " if(window.CKEDITOR && CKEDITOR.instances){"
                + "   const keys=Object.keys(CKEDITOR.instances);"
                + "   if(keys.length){ for(const k of keys){ try{ CKEDITOR.instances[k].setData(t);"
                + "       CKEDITOR.instances[k].updateElement(); }catch(e){} }"
                + "     return t+' [CKEDITOR: '+keys.join(',')+']'; } }"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].filter(vis)[0];"
                + " if(ce){ ce.innerHTML=t; ce.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   return t+' [contenteditable]'; }"
                + " return '(no-remark-field)'; }", text);
        lastRemark = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("SmsWhatsappTemplate: Remark -> " + lastRemark);
        return lastRemark;
    }

    public boolean remarkEntered(String text) {
        return lastRemark != null && !lastRemark.startsWith("(no-") && lastRemark.startsWith(text);
    }

    /**
     * Tick the <b>SMS</b> or <b>WhatsApp</b> checkbox, and report whether it actually responded.
     *
     * <p>The point of the step is that the box is clickable, so the state is read back after the click
     * rather than the click alone being reported: a disabled or unbound box accepts the click silently
     * and would otherwise pass. Every candidate box is listed with its state, so a box that refuses to
     * change is visible in the report rather than hidden behind a pass.</p>
     */
    public String tickSmsOrWhatsapp() {
        Object r = page.evaluate("() => {" + JS
                + " const boxes=[...document.querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/row\\.entity|colFilter|IsSelected$/i.test(ngOf(e)));"
                + " const near=e=>norm(tail(e)+' '+(e.getAttribute('name')||'')+' '+labelOf(e));"
                + " const cand=boxes.filter(e=>/sms|whats\\s*app/i.test(near(e)));"
                + " const pool=cand.length? cand : boxes;"
                + " if(!pool.length) return '(no checkbox on the form)';"
                + " const target=pool[0];"
                + " const before=target.checked;"
                + " const disabled=target.disabled || target.readOnly;"
                + " target.click();"
                + " const after=target.checked;"
                + " const all=pool.map(e=>(ngOf(e)||labelOf(e)||'?')+'='+(e.checked?'ticked':'unticked')"
                + "   +(e.disabled?' (disabled)':'')).join(', ');"
                + " return (after!==before? 'CLICKABLE - ' : 'DID NOT RESPOND - ')"
                + "   +'['+(ngOf(target)||'?')+'] \"'+labelOf(target).slice(0,40)+'\" '"
                + "   +before+' -> '+after+(disabled?' (control is disabled)':'')"
                + "   +' | all boxes: '+all; }");
        lastTick = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("SmsWhatsappTemplate: tick -> " + lastTick);
        return lastTick;
    }

    /** The checkbox responded to the click — its state changed, which is what "clickable" means here. */
    public boolean ticked() {
        return lastTick != null && lastTick.startsWith("CLICKABLE");
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
     * <p>CKEditor's toolbar carries its own Save button with {@code ng-click="-"}: clicking it does
     * nothing and looks like a screen that will not save, so editor chrome is excluded and an
     * {@code fnIUD} handler is preferred.</p>
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
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''))"
                    + "   .filter(x=>!x.closest('.cke,.cke_toolbox,.cke_top'));"
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
        System.out.println("SmsWhatsappTemplate: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid. The Code column's own box
     * is used, and the row must carry the code AND the template name — a code alone can belong to a
     * record that already existed.</p>
     */
    public boolean codeInList(String code, String name) {
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
                    + " const mine=a.n? withCode.find(t=>t.includes(a.n)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "n", name == null ? "" : name));
            lastListCheck += "looked for \"" + code + "\" + \"" + name + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("SmsWhatsappTemplate: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
