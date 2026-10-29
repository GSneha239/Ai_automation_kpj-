package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>ConsentTXT</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> → <b>ConsentTXT</b> (list, route {@code #/ConsentTXT}: grid Code |
 * Template Name | Patients Guardians Sign | Witnessed By Technician | User Sign | Other Sign | Status | Edit) → <b>Add</b>
 * ({@code AddConsent()}, route {@code #/addConsentNew}) → enter <b>Code</b> ({@code consent.code}, max 100), <b>Template Name</b>
 * ({@code consent.description}) and <b>Consent Type</b> ({@code consent.consenttypeid}, a select2-wrapped select), type the consent
 * text into the editor → <b>Submit</b> ({@code fnIUDConsent()}) → toast → look the record up in the list grid.</p>
 *
 * <p><b>The consent text</b> is a CKEditor ({@code editor1}) inside {@code //*[@id="myConsentForm"]/div[2]/div}, standing in for the
 * hidden {@code textarea[ck-editor][ng-model='consent.template']}. It is typed into the way a user does — click into the editor's
 * iframe body and type — which the editor's own change handling carries into {@code consent.template}; the model is read back to
 * prove it (a template with no text is rejected).</p>
 *
 * <p>The menu has three similar entries — <b>Consent</b> ({@code #/Consent}), <b>ConsentTXT</b> ({@code #/ConsentTXT}) and
 * <b>Consent Type</b> ({@code #/CONSNTTYP}); the first one shares this add form but is a different screen and has its own script.</p>
 *
 * <p>The Consent Type list carries leftover test data ("Treatment Consent MV10LWSF0" …), so it is picked from the clean entries only.</p>
 */
public class ConsentTXT extends BasePage {

    public ConsentTXT(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/ConsentTXT";

    private static final String NG = "consent.";
    private static final String ADD_LIST = "[ng-click='AddConsent()']";
    private static final String SUBMIT = "button[ng-click='fnIUDConsent();'],button[ng-click='fnIUDConsent()']";
    private static final String EDITOR_FRAME = "iframe.cke_wysiwyg_frame";

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastAdd = "", lastEntry = "", lastEditor = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "";
    public String lastCode = "", lastTemplateName = "", lastConsentType = "", lastText = "";

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Patient &rarr; ConsentTXT.
     *
     * <p>Each level is opened only if it is closed (a second click on an open entry CLOSES it) and the next one is looked up
     * INSIDE its parent's {@code li}: the sidebar is built lazily and "Patient" also names other modules' entries. The link is matched
     * on its EXACT text — "Consent" and "Consent Type" are different screens. The app bounces a typed hash back to the dashboard,
     * so the menu link is the only way in.</p>
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*patient\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const pat=[...topLi.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(pat&&pat.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*consent\\s*txt\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__ctxMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__ctxMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ConsentTXT.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__ctxMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onListScreen();
    }

    /** Every visible menu entry — used to pin the real label when the menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On the list screen: the route AND the screen's own Add button must be there (a bounce to the dashboard has neither). */
    public boolean onListScreen() {
        if (!page.url().toLowerCase().endsWith("#/consenttxt")) return false;
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + ADD_LIST + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return true;
    }

    /** On the add form: its own Code box is showing. */
    public boolean onAddForm() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + NG + "code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(!vis(e) && !(e.tagName==='SELECT' && ng) && !(e.tagName==='TEXTAREA' && ng)) continue;"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options|datepicker/i.test(ng+' '+click)) continue;"
                + "   if(/page(First|Previous|Next|Last)PageClick|openMic|editConsent/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+' maxlength='+(e.getAttribute('maxlength')||'-')+(vis(e)?'':' (hidden: the editor replaces it)')); }"
                + " out.push('EDITOR: '+(window.CKEDITOR?('CKEditor instances '+Object.keys(CKEDITOR.instances).join(',')):'none'));"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " if(hdr.length) out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== ConsentTXT CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the list's <b>Add</b> and wait for the form's Code box. Lands on {@code #/addConsentNew}. */
    public boolean clickAdd() {
        try { page.locator(ADD_LIST).first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { lastAdd = "the Add button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        boolean open = onAddForm();
        waitForAngular(1500);   // the editor builds a moment after the form
        lastAdd = open ? "Add clicked; the form opened at " + page.url() : "Add clicked but the form did not open (" + page.url() + ")";
        return open;
    }

    /** The form's <b>Back</b> ({@code closeForm()}) — used to reach the list again without typing a hash (that raises "Reload page"). */
    public boolean clickBack() {
        try { page.locator("[ng-click='closeForm()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        waitForAngular(1500);
        return onListScreen();
    }

    /**
     * Fill <b>Code</b>, <b>Template Name</b> and <b>Consent Type</b>. The Code is a unique code — "CTX" + the wall-clock date and
     * time to the second ({@code ddMMyyHHmmss}), plus the attempt number on a retry — and the Template Name carries the same stamp,
     * so a re-run never meets a name an earlier run used (the shared QA environment keeps everything). The Consent Type rotates with
     * the attempt through the clean entries.
     *
     * @return a one-line summary of what was entered
     */
    public String fillDetails(int attempt) {
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        lastCode = "CTX" + stamp + (attempt > 0 ? String.valueOf(attempt) : "");
        lastTemplateName = "Auto Consent Template " + lastCode;
        int seed = Integer.parseInt(stamp.substring(stamp.length() - 6)) / 7 + attempt;

        Locator code = page.locator("input[ng-model='" + NG + "code']");
        code.fill("");
        code.fill(lastCode);
        Locator name = page.locator("input[ng-model='" + NG + "description']");
        name.fill("");
        name.fill(lastTemplateName);
        lastConsentType = pickConsentType(seed);
        waitForAngular(500);
        lastEntry = "Code \"" + lastCode + "\", Template Name \"" + lastTemplateName + "\", Consent Type \"" + lastConsentType + "\"";
        System.out.println("ConsentTXT: entered -> " + lastEntry);
        return lastEntry;
    }

    /**
     * Choose a CLEAN Consent Type: not a leftover with a generated token suffix ("… MV10LWSF0"). The select is hidden behind select2,
     * so the option is set and change raised for Angular and for select2 (which then shows it). {@code index} wraps around the clean
     * entries, so a retry gets a different one.
     */
    private String pickConsentType(int index) {
        Object r = page.evaluate("async ([ng, idx]) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '';"
                + " const real=o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent));"
                + " const clean=t=>!/\\d{4,}/.test(t) && !/\\b(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]{7,}\\b/.test(t);"
                + " for(let k=0;k<20 && ![...e.options].some(real);k++) await new Promise(r=>setTimeout(r,400));"
                + " const firstOfValue=o=>[...e.options].find(x=>x.value===o.value)===o;"
                + " let opts=[...e.options].filter(o=>real(o) && clean(norm(o.textContent)) && firstOfValue(o));"
                + " if(!opts.length) opts=[...e.options].filter(real);"
                + " if(!opts.length) return '';"
                + " const o=opts[((idx%opts.length)+opts.length)%opts.length];"
                + " e.selectedIndex=[...e.options].indexOf(o); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ window.angular.element(e).triggerHandler('change'); }catch(x){}"
                + " try{ window.jQuery(e).trigger('change'); }catch(x){}"
                + " await new Promise(r=>setTimeout(r,700));"
                + " return norm(o.textContent); }",
                java.util.Arrays.asList(NG + "consenttypeid", index));
        return r == null ? "" : r.toString();
    }

    /** Read the three boxes back: each must hold what was entered (the select by its chosen option's text). */
    public boolean detailsEntered() {
        Object r = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"[ng-model='" + NG + "\"+n+\"']\"); if(!e) return '(missing)';"
                + "   return e.tagName==='SELECT' ? (e.selectedIndex>0 ? norm(e.options[e.selectedIndex].textContent) : '') : norm(e.value); };"
                + " return JSON.stringify({code:g('code'),name:g('description'),type:g('consenttypeid')}); }");
        String s = r == null ? "" : r.toString();
        boolean ok = s.contains("\"code\":\"" + lastCode + "\"") && s.contains("\"name\":\"" + lastTemplateName + "\"")
                && s.contains("\"type\":\"" + lastConsentType + "\"") && !lastConsentType.isEmpty();
        if (!ok) lastEntry += "  ||  READ BACK: " + s;
        return ok;
    }

    /**
     * Type the consent text into the editor ({@code //*[@id="myConsentForm"]/div[2]/div} → CKEditor {@code editor1}) the way a user
     * does: the editor is emptied first (a retry must not stack text), then its iframe body is clicked and the text typed. The text
     * is then read three ways — the editor's own data, the Angular model {@code consent.template}, and the editor's visible body — and
     * all must contain it.
     */
    public boolean enterConsentText(int attempt) {
        lastText = "Automated consent text for " + lastCode + ": the patient has been informed about the procedure and agrees to it.";
        try {
            page.waitForFunction("() => window.CKEDITOR && CKEDITOR.instances && CKEDITOR.instances.editor1 && CKEDITOR.instances.editor1.status==='ready'",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { lastEditor = "the editor never became ready: " + e.getMessage().split("\n")[0]; return false; }
        page.evaluate("() => { try{ CKEDITOR.instances.editor1.setData(''); }catch(e){} }");
        waitForAngular(400);
        try {
            Locator body = page.frameLocator(EDITOR_FRAME).first().locator("body");
            body.click(new Locator.ClickOptions().setTimeout(8000));
            page.keyboard().type(lastText, new com.microsoft.playwright.Keyboard.TypeOptions().setDelay(8));
        } catch (Exception e) { lastEditor = "typing into the editor failed: " + e.getMessage().split("\n")[0]; return false; }
        waitForAngular(900);
        Object r = page.evaluate("(t) => {" + JS
                + " const ed=window.CKEDITOR&&CKEDITOR.instances.editor1;"
                + " const ta=document.querySelector(\"textarea[ng-model='" + NG + "template']\");"
                + " const model=((window.angular.element(ta).scope().consent||{}).template)||'';"
                + " const plain=h=>norm(String(h).replace(/<[^>]*>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&'));"
                + " const inEditor=ed?plain(ed.getData()):''; const inModel=plain(model);"
                + " const fr=document.querySelector('" + EDITOR_FRAME + "'); const inBody=fr&&fr.contentDocument?norm(fr.contentDocument.body.textContent):'';"
                + " return JSON.stringify({editor:inEditor.includes(t), model:inModel.includes(t), body:inBody.includes(t), len:inModel.length}); }", lastText);
        String s = r == null ? "" : r.toString();
        boolean ok = s.contains("\"editor\":true") && s.contains("\"model\":true") && s.contains("\"body\":true");
        lastEditor = (ok ? "typed " + lastText.length() + " characters; the editor shows them, its data holds them and consent.template (the model that is saved) holds them"
                         : "the text did not land everywhere: " + s);
        System.out.println("ConsentTXT: editor -> " + lastEditor);
        return ok;
    }

    /** Installs a fresh toast collector. Must run BEFORE the click — toasts fade in about a second. */
    private void armToasts() {
        page.evaluate("() => {" + JS
                + " window.__ctxToasts=[]; if(window.__ctxObs) window.__ctxObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__ctxToasts.includes(t)) window.__ctxToasts.push(t); }); };"
                + " window.__ctxObs=new MutationObserver(grab); window.__ctxObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /**
     * A success message, judged strictly: these screens phrase refusals with words like "Already Added!" — so a plain
     * {@code contains("added")} would read a rejection as a save.
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ") || t.contains("fail") || t.contains("error")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** "… already exists / already added" — the cue to retry with a different code. */
    public static boolean isDuplicate(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /**
     * Click <b>Submit</b> ({@code fnIUDConsent()}) and return the toast. The server call is watched too, so a refusal without a toast
     * is still explained. Answers the "Do You Want To Save" dialog if one is raised.
     */
    public String submitAndGetToast() {
        final StringBuilder net = new StringBuilder();
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            if (r.request().method().equals("POST") && r.url().toLowerCase().contains("/api/consent")) {
                String body = "";
                try { body = r.text(); } catch (Exception ignore) { }
                if (net.length() > 0) net.append(" ; ");
                net.append(r.url().substring(r.url().toLowerCase().indexOf("/api/"))).append(" answered HTTP ").append(r.status())
                        .append(body.isBlank() ? "" : " " + body.substring(0, Math.min(140, body.length())));
            }
        };
        page.onResponse(onResp);
        try {
            armToasts();
            try { page.locator(SUBMIT).first().click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                lastSaveDiagnostics = "the Submit button could not be clicked: " + e.getMessage().split("\n")[0];
                lastToast = "";
                return "";
            }
            // A confirm may or may not appear — answer it whenever it is there, then wait for the toast.
            long deadline = System.currentTimeMillis() + 20000;
            String toast = "";
            while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
                try { acceptSaveDialog(); } catch (Exception ignore) { }
                try {
                    Object now = page.evaluate("() => (window.__ctxToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                    toast = now == null ? "" : now.toString();
                } catch (Exception ignore) { }
                if (toast.isEmpty()) page.waitForTimeout(500);
            }
            // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
            toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").replaceFirst("^\\s*\\W*KPJ Portal\\s*", "").trim();
            lastSaveDiagnostics = (toast.isEmpty() ? "Submit clicked; no message within 20s" : "Submit clicked")
                    + (net.length() > 0 ? " (" + net + ")" : " (no consent API call was seen)");
            lastToast = toast;
            System.out.println("ConsentTXT: submit -> toast=\"" + toast + "\" " + lastSaveDiagnostics);
            waitForAngular(1200);
            return toast;
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
    }

    /**
     * Look the saved record up in the list. After a save the screen may stay on the (cleared) form, so the form's own <b>Back</b> is
     * used to reach the list. The grid has one {@code colFilter.term} box PER COLUMN, so the box inside the <b>Code</b> header is
     * used; the body rows are read from the rendered cells (after the filter only the matching rows remain), and a row counts only
     * when its Code AND its Template Name equal what was entered — a code alone could belong to a record that already existed.
     */
    public boolean verifyInTable() {
        waitForAngular(1200);
        try {
            if (page.url().toLowerCase().contains("addconsent")) clickBack();
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')].some(r=>/\\S/.test(r.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis).filter(r=>/\\S/.test(r.textContent||'')).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis);"
                    + " const inCode=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell'); return h && /^\\s*code\\b/i.test(norm(h.textContent)); });"
                    + " const f=inCode||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel'); if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered '+(inCode?'the Code column':(f?'the first column':'NOTHING - no filter box, rows read as listed')); }",
                    lastCode);
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                    + "   .map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent)))"
                    + "   .filter(cells=>cells.some(t=>t));"
                    + " const pager=norm((document.querySelector('.ui-grid-pager-count')||{}).textContent);"
                    + " const withCode=rows.filter(c=>c.some(t=>t.toUpperCase()===a.code.toUpperCase()));"
                    + " const mine=withCode.find(c=>c.includes(a.name));"
                    + " if(mine) return 'FOUND: '+mine.filter(t=>t).join(' | ')+' ('+pager+')';"
                    + " if(withCode.length) return 'the code is listed but the row reads: '+withCode[0].filter(t=>t).join(' | ')+' ('+pager+')';"
                    + " return 'not in the list ('+rows.length+' rows shown, '+pager+')'; }",
                    java.util.Map.of("code", lastCode, "name", lastTemplateName));
            lastListCheck = "looked for Code \"" + lastCode + "\" + Template Name \"" + lastTemplateName + "\" with "
                    + (where == null ? "" : where) + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("ConsentTXT: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
