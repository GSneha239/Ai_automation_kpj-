package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Certificate Template</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Certificate Template</b>
 * ({@code #/CertificateTemplate}) → <b>Add</b> ({@code AddCertificateTemplateMaster()} → {@code #/add-CertificateTemplate})
 * → enter <b>Code*</b> + <b>Remark*</b> → tick the <b>Certificate Template</b> type → fill the mandatory
 * <b>Field Name*</b> / <b>Control Binding*</b> (+ Parameter Name, Font) → inner <b>Add</b>
 * ({@code AddTemplateDetails}) → <b>Submit</b> ({@code fnIUDCertificateTemplate()}) → success toast.</p>
 *
 * <p>The add form is the shared Discharge Template Master form, so Code/Remark live on {@code discharge1.*} and the
 * template-type checkboxes are split across {@code discharge1.*} / {@code discharge2.*}.</p>
 */
public class CertificateTemplate extends BasePage {

    public CertificateTemplate(Page page) { super(page); }

    public static final String ROUTE = "#/CertificateTemplate";
    public String lastCode = "", lastRemark = "";

    /** Remarks are real certificate-template names, not generic filler text. */
    private static final String[] REMARKS = {
            "Medical Fitness Certificate", "Sick Leave Certificate", "Discharge Summary Certificate",
            "Birth Certificate Notification", "Death Certificate", "Vaccination Certificate",
            "Medical Leave Certificate", "Fit to Fly Certificate"
    };

    // ---- discovery -------------------------------------------------------

    /** Dump the add-form fields / checkboxes / buttons (diagnostics only). */
    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.col-md-3,.col-md-4,.col-md-6,td,div'); for(let i=0;i<4&&n;i++){ const l=n.querySelector('label,.control-label'); if(l) return norm(l.textContent).slice(0,32); n=n.parentElement; } return ''; };"
                + " const out=['URL='+location.href];"
                + " [...document.querySelectorAll(\"input[type=checkbox][ng-model^='discharge']\")].forEach(e=>out.push('CB \"'+labelOf(e)+'\" ng='+e.getAttribute('ng-model')+' vis='+(e.offsetParent!==null)+' checked='+e.checked+' click='+(e.getAttribute('ng-click')||'-')));"
                + " [...document.querySelectorAll('input[type=text],select,textarea')].filter(e=>/^discharge/.test(e.getAttribute('ng-model')||'')).forEach(e=>out.push(e.tagName+' \"'+labelOf(e)+'\" ng='+e.getAttribute('ng-model')+' vis='+(e.offsetParent!==null)+(e.tagName==='SELECT'?(' opts='+e.options.length):'')));"
                + " out.push('ck='+(window.CKEDITOR?Object.keys(CKEDITOR.instances||{}).join(','):'none'));"
                + " [...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /add|modify|submit|save/i.test(b.textContent||b.value||'')).forEach(b=>out.push('BTN \"'+norm(b.textContent||b.value)+'\" {'+(b.getAttribute('ng-click')||'')+'}'));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Patient (submenu) → Certificate Template. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // expand the "Patient" submenu parent
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/certificate\\s*template/i.test(norm(x.textContent)) || /certificatetemplate/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ctMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__ctMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("CertificateTemplate.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("CertificateTemplate.nav: menu link not found"); }
        if (!onScreen()) {
            System.out.println("CertificateTemplate.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(1200);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("certificatetemplate"); }

    /** Real-click the top <b>Add</b> ({@code AddCertificateTemplateMaster}) → {@code #/add-CertificateTemplate}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddCertificateTemplateMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__ctAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("CertificateTemplate.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ctAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CertificateTemplate.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__ctAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='discharge1.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CertificateTemplate.clickAdd: discharge1.code never appeared"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-certificatetemplate");
    }

    /**
     * Enter a unique <b>Code*</b> + <b>Remark*</b> ({@code discharge1.code} / {@code discharge1.description}).
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different
     * details.
     */
    public String fillCodeAndRemark(int attempt) {
        String code = "CT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setNg('discharge1.code', a.code); const rm=setNg('discharge1.description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Select the template — real-click the <b>Certificate Template</b> checkbox
     * ({@code discharge2.iscertificatetemplate}) so its {@code toggleSelection()} handler fires. The checkbox is a
     * plain toggle and its wrapping label can swallow/repeat the click, so click until it reads ticked (both the DOM
     * and the Angular model) rather than assuming one click sticks. Returns the label + resulting checked state.
     */
    public String selectTemplate() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.col-md-3,.col-md-4,.col-md-6,td,div,label'); for(let i=0;i<4&&n;i++){ const l=n.querySelector('label,.control-label'); if(l) return norm(l.textContent).slice(0,32); n=n.parentElement; } return ''; };"
                + " let cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='discharge2.iscertificatetemplate']\")].find(x=>x.offsetParent!==null)"
                + "  || [...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && /certificate\\s*template/i.test(labelOf(x)));"
                + " if(!cb) return ''; cb.id='__ctTypeCb'; return labelOf(cb)||'Certificate Template'; }");
        String label = tagged == null ? "" : tagged.toString();
        if (label.isEmpty()) { System.out.println("selectTemplate: Certificate Template checkbox not found"); return "(no template checkbox)"; }
        boolean on = false;
        for (int i = 0; i < 4 && !on; i++) {
            if (isTemplateTicked()) { on = true; break; }
            try { page.locator("#__ctTypeCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
            catch (Exception e) { System.out.println("selectTemplate: click failed - " + e.getMessage()); }
            waitForAngular(700);
            on = isTemplateTicked();
        }
        return label + " (checked=" + on + ")";
    }

    /** True when the Certificate Template checkbox is ticked in BOTH the DOM and the Angular model. */
    private boolean isTemplateTicked() {
        Object r = page.evaluate("() => { const e=document.getElementById('__ctTypeCb'); if(!e) return false; let m=e.checked;"
                + " try{ const sc=window.angular.element(e).scope(); if(sc && sc.discharge2) m = m && !!sc.discharge2.iscertificatetemplate; }catch(x){}"
                + " return !!m; }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Fill the remaining mandatory template details: <b>Field Name*</b> ({@code discharge.fieldname}),
     * <b>Control Binding*</b> ({@code discharge.bindingcontrol} — options load async, so poll), plus
     * <b>Parameter Name</b> ({@code discharge.paraid}) and <b>Font</b> ({@code discharge.fontid}), and the template
     * body text (CKEditor {@code editor1} / textarea {@code discharge1.textdata}).
     */
    public String fillTemplateDetails() {
        Object r = page.evaluate("(code) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const setNg=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng,prefer)=>{ for(let k=0;k<15;k++){ const e=[...document.querySelectorAll(\"select[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(e){ let i=prefer?[...e.options].findIndex(o=>o.value && prefer.test(norm(o.textContent))):-1; if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)) && norm(o.textContent)); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no)'; };"
                + " const fn=setNg('discharge.fieldname', 'AutoField'+code.slice(-4));"
                + " const bc=await pick('discharge.bindingcontrol', /^text$/i);"
                + " const pn=await pick('discharge.paraid', null);"
                + " const ft=await pick('discharge.fontid', /arial/i);"
                + " let body='(no)'; const txt='<p>Automated certificate template '+code+'.</p>';"
                + " if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances||{}); const inst=insts[0]; if(inst){ try{ inst.setData(txt); body='CKEDITOR:'+inst.name; }catch(x){} } }"
                + " if(body==='(no)'){ const r2=setNg('discharge1.textdata','Automated certificate template '+code+'.'); if(r2!=='(no)') body='textarea:discharge1.textdata'; }"
                + " resolve('FieldName=AutoField'+code.slice(-4)+' | ControlBinding='+bc+' | ParameterName='+pn+' | Font='+ft+' | Body='+body); })", lastCode);
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click the inner <b>Add</b> ({@code AddTemplateDetails}) that appends the Field Name / Control Binding row to the
     * detail list — Submit rejects the master without at least one detail row. Returns the detail row count after.
     */
    public String clickAddDetail() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /AddTemplateDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__ctAddDetail'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddDetail: AddTemplateDetails button not found"); return "(no detail Add)"; }
        try { page.locator("#__ctAddDetail").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAddDetail: click failed - " + e.getMessage()); }
        waitForAngular(900);
        Object rows = page.evaluate("() => { const s=A=>A; try{ const el=document.querySelector(\"[ng-click*='AddTemplateDetails']\"); const sc=window.angular.element(el).scope(); const l=sc && (sc.dischargetemplatelist||sc.$parent&&sc.$parent.dischargetemplatelist); if(l && l.length!==undefined) return l.length; }catch(x){}"
                + " return [...document.querySelectorAll('table tbody tr')].filter(tr=>tr.offsetParent!==null && (tr.textContent||'').trim()).length; }");
        return "detail rows=" + rows;
    }

    /** Click <b>Submit</b> ({@code fnIUDCertificateTemplate}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__ctToasts=[]; if(window.__ctObs) window.__ctObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ctToasts.includes(t)) window.__ctToasts.push(t); }); };"
                + " window.__ctObs=new MutationObserver(grab); window.__ctObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCertificateTemplate/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__ctSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__ctSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        // answer any "do you want to save" confirm
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__ctToasts||[]).some(a=>/certificate|template|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ctToasts||[]).includes(t)) (window.__ctToasts=window.__ctToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ctToasts||[]; return a.find(x=>/(certificate|template|master).*(saved|added)|added success|saved success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
