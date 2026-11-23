package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>EMR Protocol</b> ({@code #/EMRProtocolList}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>EMR Protocol</b> → <b>New</b> → enter the <b>template name</b> →
 * select <b>Gender</b> → select the <b>form</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load): several Nursing Station menu
 * links render an empty shell when clicked, while the same route reached another way renders fine.</p>
 *
 * <p><b>Controls are resolved by what they are, not by a guessed ng-model.</b> Gender is identified by its
 * options (Male/Female/…) rather than by name, and the template-name and form controls by their label or
 * placeholder — {@link #describeControls()} prints the real ng-models so they can be pinned once seen.</p>
 */
public class EMRProtocol extends BasePage {

    public EMRProtocol(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/EMRProtocolList";

    public String lastControls = "", lastBodyText = "";
    public String lastTemplateName = "", lastGender = "", lastForm = "", lastSaveDiagnostics = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
            + "  return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||e.getAttribute('title')||''); };"
            + "const attrs=e=>[e.getAttribute('ng-model'),e.getAttribute('id'),e.getAttribute('name')].filter(Boolean).join(' ');"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
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
            // THE SCREEN HAS TWO PANELS with duplicate controls: a search filter at the top and the New
            // form below. Both carry EMRProtocol.GenderID and EMRProtocol.FormID, and there are two name
            // inputs (TemplateName and TempName). Targeting by ng-model alone fills the SEARCH panel and
            // leaves the form empty — which looks like the form silently ignoring input.
            //
            // So everything is scoped to the panel that owns the Save button: walk up from Save to the
            // nearest ancestor that also holds a select and a text input, and search only inside it.
            + "const formScope=()=>{ const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
            + "  const save=btns.find(b=>/SaveTemplate/i.test(b.getAttribute('ng-click')||''))"
            + "    || btns.find(b=>/^\\s*save\\s*$/i.test(norm(b.textContent||b.value)));"
            + "  if(!save) return document;"
            + "  let n=save.parentElement;"
            + "  while(n && n!==document.body){"
            + "    if(n.querySelector('select') && n.querySelector('input[type=text], input:not([type])')) return n;"
            + "    n=n.parentElement; }"
            + "  return document; };"
            + "const inScope=sel=>[...formScope().querySelectorAll(sel)].filter(vis);"
            // Gender is recognised by its OPTIONS, which is far more reliable than any name guess.
            + "const genderSelect=()=>inScope('select')"
            + "  .find(s=>{ const t=[...s.options].map(o=>norm(o.textContent).toLowerCase());"
            + "    return t.includes('male') && t.includes('female'); });";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to EMR Protocol, escalating menu click → hash route → full page load. */
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
                + "   .find(x=>/^emr\\s*protocol$/i.test(norm(x.textContent))"
                + "        || /#\\/EMRProtocol/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("EMRProtocol.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("EMRProtocol.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + LIST_ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("EMRProtocol.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("emrprotocol")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text and every control, so the real ng-models are visible. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length"
                + "     +' {'+[...e.options].slice(0,4).map(o=>norm(o.textContent)).join(' / ')+'}');"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)"
                + "   .map(e=>'TEXTAREA \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,18)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(ta).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,200)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("EMRProtocol CONTROLS:\n" + lastControls);
        return lastControls;
    }

    /** Click <b>New</b> to open the protocol form; polled, as it renders after the grid. */
    public boolean clickNew() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/^\\s*(new|add)\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/(new|add).*protocol|protocol.*(new|add)/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^(add|new)/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__emrNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("EMRProtocol.clickNew: New control not found"); return false; }
        try { page.locator("#__emrNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("EMRProtocol.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__emrNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('select').length > 0"
                    + " && document.querySelectorAll('input[type=text]').length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("EMRProtocol.clickNew: form did not render"); }
        waitForAngular(1500);
        return true;
    }

    // ---- fields ------------------------------------------------------------

    /** Enter the <b>template name</b> — matched by label/placeholder/model naming template or name. */
    public String enterTemplateName(String name) {
        Object r = page.evaluate("(v) => {" + JS
                + " const ins=inScope('input[type=text], input:not([type])')"
                + "   .filter(e=>!/colFilter|pagination/i.test(attrs(e)+' '+labelOf(e)));"
                + " let e=ins.find(x=>/template\\s*name|protocol\\s*name|tempname/i.test(attrs(x)+' '+labelOf(x)));"
                + " if(!e) e=ins.find(x=>/template|protocol/i.test(attrs(x)+' '+labelOf(x)));"
                + " if(!e) e=ins.find(x=>/name/i.test(attrs(x)+' '+labelOf(x)));"
                + " if(!e) e=ins[0];"
                + " if(!e) return '(no-field)';"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " return setEl(e, v)+' '+which; }", name);
        lastTemplateName = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("EMRProtocol: Template name = " + lastTemplateName);
        return lastTemplateName;
    }

    /** Select <b>Gender</b>, preferring Male/Female. */
    public String selectGender() { return selectGender(null); }

    /**
     * Select <b>Gender</b> — found by its options containing Male and Female.
     *
     * <p>Defaults to a real gender (Male, then Female) rather than the first option, which here is
     * "Ambiguous". That matters beyond realism: the Form list is filtered by gender, and Ambiguous yields
     * no forms at all.</p>
     *
     * @param prefer option text to prefer, or null for Male → Female → first real option
     */
    public String selectGender(String prefer) {
        Object r = page.evaluate("(want) => new Promise(async resolve => {" + JS
                + " const e=genderSelect();"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " const opts=[...e.options];"
                + " const choose=rx=>opts.findIndex(o=>real(o) && rx.test(norm(o.textContent)));"
                + " let i=-1;"
                + " if(want) i=choose(new RegExp('^\\\\s*'+String(want)+'\\\\s*$','i'));"
                + " if(i<0) i=choose(/^male$/i);"
                + " if(i<0) i=choose(/^female$/i);"
                + " if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ A.element(e).triggerHandler('change'); }catch(x){}"
                + "   if($){ try{ $(e).trigger('change'); }catch(x){} }"
                + "   resolve(norm(opts[i].textContent)+' '+which); return; }"
                + " const v=await pickEl(e);"
                + " resolve(v+' '+which); })", prefer);
        lastGender = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("EMRProtocol: Gender = " + lastGender);
        return lastGender;
    }

    /**
     * Select the <b>form</b> — the form/template dropdown, excluding the gender one.
     *
     * <p>The list arrives asynchronously and can reload when Gender changes, so a real option is waited
     * for rather than sampled once: a single read lands on the still-empty list and reports
     * {@code (no-option)} for a screen that is only still loading.</p>
     */
    public String selectForm() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')]"
                    + " .filter(e=>/FormID/i.test(e.getAttribute('ng-model')||''))"
                    + " .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " .some(e=>[...e.options].some(o=>o.value"
                    + "   && !/^-*\\s*select/i.test((o.text||'').trim())))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("EMRProtocol.selectForm: no form option within 15s");
            // Say WHY: an empty list is a configuration gap, a missing element is a selector problem, and
            // a list that never loads is a screen defect. They read identically without this.
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const all=[...document.querySelectorAll('select')].filter(vis);"
                    + " const f=all.filter(e=>/FormID/i.test(e.getAttribute('ng-model')||''));"
                    + " const desc=all.map(e=>(e.getAttribute('ng-model')||'?')+'(opts='+e.options.length+')');"
                    + " if(!f.length) return 'no visible select with ng-model FormID. Selects on screen: '"
                    + "   +desc.join(', ');"
                    + " const e=f[0];"
                    + " return 'FormID options='+e.options.length+' ['"
                    + "   +[...e.options].slice(0,5).map(o=>norm(o.text)).join(', ')+']'"
                    + "   +' | other selects: '+desc.join(', '); }");
            System.out.println("EMRProtocol.selectForm: " + diag);
        }

        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const g=genderSelect();"
                + " const sels=inScope('select').filter(s=>s!==g && !/pagination/i.test(attrs(s)));"
                + " let e=sels.find(s=>/form/i.test(attrs(s)+' '+labelOf(s)));"
                + " if(!e) e=sels.find(s=>s.options.length>1);"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " const v=await pickEl(e);"
                + " resolve(v+' '+which); })");
        lastForm = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("EMRProtocol: Form = " + lastForm);
        return lastForm;
    }

    // ---- save --------------------------------------------------------------

    /** Click <b>Save</b> and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__emrToasts=[]; if(window.__emrObs) window.__emrObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__emrToasts.includes(t)) window.__emrToasts.push(t); }); };"
                + " window.__emrObs=new MutationObserver(grab); window.__emrObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__emrPrior=(window.__emrToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__emrSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("EMRProtocol.save: Save button not found"); return ""; }

        try { page.locator("#__emrSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("EMRProtocol.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__emrSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__emrToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__emrPrior||[];"
                + " const a=(window.__emrToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__emrToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("EMRProtocol.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
