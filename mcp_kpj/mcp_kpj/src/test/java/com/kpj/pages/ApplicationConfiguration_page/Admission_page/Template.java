package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Template</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Template</b> → <b>Add</b> → enter <b>Code</b> +
 * <b>Remark</b> → select a <b>Template</b> → enter <b>text</b> → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class Template extends BasePage {

    public Template(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic discharge-template names — a Remark reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] REMARKS = {
            "Discharge Summary Letter", "Post-Operative Care Instructions", "Diabetes Discharge Advice",
            "Cardiac Follow-Up Instructions", "Maternity Discharge Note", "Pediatric Discharge Advice",
            "Wound Care Instructions", "Physiotherapy Home Exercise Plan"
    };

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " out.push('tables='+document.querySelectorAll('table').length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,30).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); const l=g?g.querySelector('label,.control-label'):null; return norm(l?l.textContent:'').slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " out.push('ckeditor='+(window.CKEDITOR?Object.keys(CKEDITOR.instances||{}).length:'none')+' textAngular='+document.querySelectorAll('[text-angular],.ta-root,div[contenteditable=true]').length);"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Template.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Template" link (exact text) if present (capture its route)
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*template\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__templateMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__templateMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Template.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text is exactly "Template" (avoid Discharge Template etc.)
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*template\\s*$/i.test(norm(x.textContent))); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("Template.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("Template.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return !ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase());
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__tplAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Template.clickAdd: Add button not found"); return false; }
        try { page.locator("#__tplAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Template.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Enter <b>Code</b> (unique) + <b>Remark</b> (by label; polls for the form to render). {@code attempt} shifts
     *  BOTH values so a retry after an "already exists" toast submits genuinely different details.
     *  Returns a summary. */
    public String fillCodeAndRemark(int attempt) {
        String code = "TP" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model]')].some(e=>e.offsetParent!==null && /code|commonmaster|template/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("Template inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        // Discharge Template Master: Code = discharge1.code, Remark = discharge1.description.
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setNg('discharge1.code', a.code); const rm=setNg('discharge1.description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg=discharge1.code | remNg=discharge1.description'; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Select a template TYPE — tick the <b>Text Template</b> checkbox ({@code discharge1.istexttemplate}) so the text
     *  body applies (real click so its toggleSelection handler fires). Falls back to any template-labelled checkbox.
     *  Returns the ticked template label. */
    public String selectTemplate() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div,label'); const l=g?g.querySelector('label,.control-label'):null; return norm((l?l.textContent:'')||(e.parentElement?e.parentElement.textContent:'')).slice(0,30); };"
                + " let cb=document.querySelector(\"input[type=checkbox][ng-model='discharge1.istexttemplate']\")"
                + "   || [...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && /template/i.test(labelOf(x)) && !/filter/i.test(x.getAttribute('ng-model')||''));"
                + " if(!cb) return '(no template checkbox)'; cb.id='__tplTypeCb'; return labelOf(cb)||'(template)'; }");
        String label = String.valueOf(r);
        if (label.startsWith("(")) { System.out.println("selectTemplate: " + label); return label; }
        try { page.locator("#__tplTypeCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectTemplate: click failed - " + e.getMessage()); }
        waitForAngular(600);
        return label;
    }

    /** Enter <b>text</b> into the template body — a CKEditor / textAngular / contenteditable / textarea. Returns where it went. */
    public String enterText() {
        Object r = page.evaluate("() => { const txt='<p>Automated template text.</p>'; const plain='Automated template text.';"
                + " if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances||{}); const inst=insts.find(e=>{ try{ return e.container&&e.container.$&&e.container.$.offsetParent!==null; }catch(x){return false;} })||insts[0]; if(inst){ try{ inst.setData(txt); return 'CKEDITOR'; }catch(x){} } }"
                + " const ce=[...document.querySelectorAll('div[contenteditable=true],.ta-bind,.ta-root [contenteditable=true]')].find(e=>e.offsetParent!==null); if(ce){ ce.innerHTML=txt; ce.dispatchEvent(new Event('input',{bubbles:true})); ce.dispatchEvent(new Event('keyup',{bubbles:true})); return 'contenteditable'; }"
                + " const ta=[...document.querySelectorAll('textarea')].find(e=>e.offsetParent!==null && !/remark|description|filter/i.test((e.getAttribute('ng-model')||'')+(e.getAttribute('placeholder')||''))); if(ta){ const c=angular.element(ta).controller('ngModel'); ta.value=plain; if(c){c.$setViewValue(plain);c.$render();} ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); return 'textarea['+(ta.getAttribute('ng-model')||'')+']'; }"
                + " return '(no text field)'; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__tpToasts=[]; if(window.__tpObs) window.__tpObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tpToasts.includes(t)) window.__tpToasts.push(t); }); };"
                + " window.__tpObs=new MutationObserver(grab); window.__tpObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDDischargetemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__tplSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__tplSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        // Answer any "Do you want to save" confirm.
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__tpToasts||[]).some(a=>/template|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__tpToasts||[]).includes(t)) (window.__tpToasts=window.__tpToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__tpToasts||[]; return a.find(x=>/template.*(saved|added)|master saved|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
