package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Education Management</b> ({@code #/EducationManagementList}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Education Management</b> → <b>New</b> → enter <b>file date</b> and
 * <b>file time</b> → select the <b>document category</b> → <b>upload a file</b> → <b>Submit</b> → toast.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load) — several Nursing Station menu
 * links render an empty shell.</p>
 *
 * <p><b>The upload targets the {@code <input type="file">} directly.</b> DevHIS styles these inputs and
 * usually hides them behind a button, so clicking the visible control would open the OS file chooser,
 * which Playwright cannot drive. Setting the input's files bypasses the chooser entirely, and works even
 * when the input is hidden — hence {@code setInputFiles} on the element rather than a click.</p>
 */
public class EducationManagement extends BasePage {

    public EducationManagement(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/EducationManagementList";

    public String lastControls = "", lastBodyText = "";
    public String lastFileDate = "", lastFileTime = "", lastCategory = "", lastUpload = "";
    public String lastSaveDiagnostics = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const attrs=e=>[e.getAttribute('ng-model'),e.getAttribute('id'),e.getAttribute('name')].filter(Boolean).join(' ');"
            + "const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
            + "  return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||e.getAttribute('title')||''); };"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  if(e.readOnly||e.disabled) return '(read-only)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pickEl=async(e)=>{ if(!e) return '(no-field)';"
            + "  for(let k=0;k<12;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            + "const findInput=rx=>{ const re=new RegExp(rx,'i');"
            + "  return [...document.querySelectorAll('input[type=text], input:not([type]), textarea')].filter(vis)"
            + "    .filter(e=>!/colFilter|pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };"
            + "const findSelect=rx=>{ const re=new RegExp(rx,'i');"
            + "  return [...document.querySelectorAll('select')].filter(vis)"
            + "    .filter(e=>!/pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to Education Management, escalating menu click → hash route → full page load. */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^education\\s*management$/i.test(norm(x.textContent))"
                + "        || /#\\/EducationManagement/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("EducationManagement.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("EducationManagement.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + LIST_ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("EducationManagement.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("educationmanagement")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input, button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text, every control, and any file inputs (which are often hidden). */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                // file inputs are listed WITHOUT the visibility filter — DevHIS hides them behind a button
                + " const files=[...document.querySelectorAll('input[type=file]')]"
                + "   .map((e,i)=>'FILEINPUT#'+i+' [ng='+(e.getAttribute('ng-model')||'?')+'] id='+(e.id||'-')"
                + "     +' name='+(e.name||'-')+' visible='+(e.offsetParent!==null));"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,20)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(inp).concat(files).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,220)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("EducationManagement CONTROLS:\n" + lastControls);
        return lastControls;
    }

    /** Click <b>New</b> to open the entry form. */
    public boolean clickNew() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/^\\s*(new|add)\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/^(add|new)/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__edmNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("EducationManagement.clickNew: New control not found"); return false; }
        try { page.locator("#__edmNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("EducationManagement.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__edmNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('input[type=file]').length > 0"
                    + " || document.querySelectorAll('select').length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("EducationManagement.clickNew: form did not render"); }
        waitForAngular(1500);
        return true;
    }

    // ---- fields ------------------------------------------------------------

    /** Enter the <b>file date</b> and <b>file time</b>. */
    public String enterFileDateAndTime(String date, String time) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [d, t] = args;"
                + " const de=findInput('filedate|file\\\\s*date|date');"
                + " let te=findInput('filetime|file\\\\s*time');"
                + " if(!te){ const ins=[...document.querySelectorAll('input')].filter(vis);"
                + "   te=ins.find(e=>(e.getAttribute('ng-model')||'')==='inputTime'); }"
                + " const rd=de? setEl(de,d)+' ['+(de.getAttribute('ng-model')||'?')+']' : '(no-date-field)';"
                + " const rt=te? setEl(te,t)+' ['+(te.getAttribute('ng-model')||'?')+']' : '(no-time-field)';"
                + " return 'FileDate='+rd+' | FileTime='+rt; }", java.util.List.of(date, time));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String s = p.trim();
            if (s.startsWith("FileDate=")) lastFileDate = s.substring(9).trim();
            else if (s.startsWith("FileTime=")) lastFileTime = s.substring(9).trim();
        }
        waitForAngular(400);
        System.out.println("EducationManagement: " + out);
        return out;
    }

    public boolean dateTimeSet() {
        return lastFileDate != null && !lastFileDate.startsWith("(")
                && lastFileTime != null && !lastFileTime.startsWith("(");
    }

    /** Select the <b>document category</b>. */
    public String selectDocumentCategory() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " let e=findSelect('documentcategory|doccategory|category');"
                + " if(!e) e=findSelect('document');"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastCategory = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("EducationManagement: Document category = " + lastCategory);
        return lastCategory;
    }

    /**
     * Upload a file.
     *
     * <p>Sets the {@code <input type="file">} directly rather than clicking the visible control: that
     * control opens the OS file chooser, which Playwright cannot drive. Hidden inputs are fine for
     * {@code setInputFiles}, so the input is located, given a temporary id, then filled.</p>
     *
     * @return a description of what was uploaded, or {@code (no-file-input)}
     */
    public String uploadFile(java.nio.file.Path file) {
        Object tagged = page.evaluate("() => { const fs=[...document.querySelectorAll('input[type=file]')];"
                + " if(!fs.length) return false; fs[0].id='__edmFile'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) {
            lastUpload = "(no-file-input)";
            System.out.println("EducationManagement.uploadFile: no <input type=file> on the form");
            return lastUpload;
        }
        try {
            page.locator("#__edmFile").setInputFiles(file);
            // Read the input IMMEDIATELY: this screen's Angular directive consumes the selection and
            // clears the input, so a delayed read reports "no file attached" for an upload that worked.
            Object immediate = page.evaluate("() => { const e=document.getElementById('__edmFile');"
                    + " return (e && e.files && e.files.length)? e.files[0].name+' ('+e.files[0].size+' bytes)' : ''; }");
            String seen = immediate == null ? "" : immediate.toString();
            waitForAngular(1500);

            if (seen.isEmpty()) {
                // Fall back to the app's own evidence: the model, or a filename it echoes into the form.
                Object model = page.evaluate("(want) => {" + JS
                        + " let out='';"
                        + " const dn=findInput('documentname|document\\\\s*name');"
                        + " if(dn && dn.value) out='documentname='+dn.value;"
                        + " try { const e=document.querySelector(\"input[type=file]\");"
                        + "   const s=window.angular.element(e).scope();"
                        + "   const m=s && s.EducationManagement;"
                        + "   if(m && m.report) out+=(out?' | ':'')+'model.report set'; } catch(err){}"
                        + " const shown=norm(document.body.innerText).includes(String(want)) ? 'filename shown on screen' : '';"
                        + " if(shown) out+=(out?' | ':'')+shown;"
                        + " return out; }", file.getFileName().toString());
                String ev = model == null ? "" : model.toString();
                lastUpload = ev.isEmpty()
                        ? "(no file attached — input empty and no filename echoed)"
                        : file.getFileName() + " — input cleared by the app, but " + ev;
            } else {
                lastUpload = seen;
            }
        } catch (Exception e) {
            lastUpload = "(upload failed: " + e.getMessage() + ")";
        }
        page.evaluate("() => { const e=document.getElementById('__edmFile'); if(e) e.removeAttribute('id'); }");
        System.out.println("EducationManagement: upload -> " + lastUpload);
        return lastUpload;
    }

    public boolean fileUploaded() {
        return lastUpload != null && !lastUpload.startsWith("(");
    }

    /**
     * Click the form's <b>Add</b> ({@code AddFileDetails(linklist)}), which moves the chosen file into the
     * attachment list.
     *
     * <p>Distinct from the list screen's New. Submit ({@code fnSaveFilesonServer()}) saves the files that
     * are in that list, so skipping this can leave nothing to submit.</p>
     */
    public String clickAddFile() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/AddFileDetails/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__edmAddFile'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("EducationManagement.clickAddFile: Add not found"); return "(no Add button)"; }
        try { page.locator("#__edmAddFile").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("EducationManagement.clickAddFile: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__edmAddFile'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message')].map(x=>norm(x.textContent)).join(' | ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        String out = r == null ? "" : r.toString();
        System.out.println("EducationManagement: Add file -> " + out);
        return out;
    }

    // ---- submit ------------------------------------------------------------

    /** Click <b>Submit</b> and return the toast ("" if none appeared). */
    public String submitAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__edmToasts=[]; if(window.__edmObs) window.__edmObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__edmToasts.includes(t)) window.__edmToasts.push(t); }); };"
                + " window.__edmObs=new MutationObserver(grab); window.__edmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__edmPrior=(window.__edmToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/submit|save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__edmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("EducationManagement.submit: Submit not found"); return ""; }

        try { page.locator("#__edmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("EducationManagement.submit: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__edmSubmit'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1000);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__edmToasts||[]).some(a=>/saved|success|added|updated|upload|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__edmPrior||[];"
                + " const a=(window.__edmToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated|uploaded)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__edmToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("EducationManagement.submit: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
