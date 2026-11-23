package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Post Mortem Report</b> ({@code #/PostMortemTemplate}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Post Mortem Report</b> → <b>Add</b> → the form
 * ({@code #/add-PostMortemTemplate}) → enter <b>MRN</b> + search → select the <b>post mortem
 * template</b> → <b>Save</b> ({@code IUDPostMortemDetail()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-10):</b> {@code PostMortemTemplate.MRNo} with a
 * {@code SearchPatientByMRNo()} icon; {@code PostMortemTemplate.Dischargeid} — <b>the template list</b>,
 * despite the discharge-shaped name (Patient Feedback and Birth Certificate name theirs the same way);
 * {@code PostMortemTemplate.iscancel} checkbox. Buttons: {@code IUDPostMortemDetail()} (Save),
 * {@code fnGoBack()} (Back).</p>
 *
 * <p><b>Patient attachment is verified, not assumed.</b> These certificate-style screens accept an MRN in
 * the box and silently attach nobody, after which Save does nothing and looks broken. {@link
 * #patientAttached()} reads the Angular model, and {@link #harvestMrnsFromList()} takes an MRN the screen
 * has already accepted — a post mortem plausibly requires a deceased patient, which most MRNs are not.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class PostMortem extends BasePage {

    public PostMortem(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PostMortemTemplate";
    public static final String ADD_ROUTE = "#/add-PostMortemTemplate";
    private static final String M = "PostMortemTemplate.";

    public String lastControls = "", lastBodyText = "";
    public String lastMrn = "", lastFormSearch = "", lastTemplate = "", lastSaveDiagnostics = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pick=async(ng)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /**
     * Navigate to Post Mortem Report, escalating menu click → hash route → full page load.
     *
     * <p>The escalation is deliberate: sibling Nursing Station screens render an empty shell when reached
     * by the menu link alone.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("PostMortem.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^post\\s*mortem(\\s*report)?$/i.test(norm(x.textContent))"
                + "        || /#\\/PostMortemTemplate/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("PostMortem.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("PostMortem.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + LIST_ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("PostMortem.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    /** True when the route is up AND real controls rendered (not merely the empty shell). */
    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("postmortemtemplate")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text and every control the screen rendered. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " return sel.concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,200)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("PostMortem controls: " + lastControls);
        return lastControls;
    }

    // ---- list: harvest a usable MRN ----------------------------------------

    /**
     * Search the post mortem list over a wide window and return the MRNs it already holds.
     *
     * <p>Those patients are already accepted by this screen. Guessing an MRN is unreliable here — a post
     * mortem plausibly requires a deceased patient.</p>
     */
    public java.util.List<String> harvestMrnsFromList() {
        page.evaluate("() => {" + JS
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
                + " const b=c.find(x=>/search/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__pmHarvest'; }");
        try { page.locator("#__pmHarvest").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__pmHarvest'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);

        Object r = page.evaluate("() => { let best=-1, arr=null;"
                + " document.querySelectorAll('*').forEach(el=>{ try{"
                + "   const s=window.angular.element(el).scope(); if(!s) return;"
                + "   const scan=a=>{ if(Array.isArray(a) && a.length && typeof a[0]==='object'"
                + "        && Object.keys(a[0]).some(k=>/^mrno$|^mrn$/i.test(k)) && a.length>best){ best=a.length; arr=a; } };"
                + "   if(s.grid && s.grid.options && s.grid.options.data) scan(s.grid.options.data);"
                + "   for(const k of Object.keys(s)){ try{ scan(s[k]); }catch(e){} } }catch(e){} });"
                + " const out=[], seen=new Set();"
                + " if(arr){ for(const row of arr){ const key=Object.keys(row).find(k=>/^mrno$|^mrn$/i.test(k));"
                + "   const m=key && row[key] && String(row[key]).trim();"
                + "   if(m && !seen.has(m)){ seen.add(m); out.push(m); } } }"
                + " return out; }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        System.out.println("PostMortem: harvested " + list.size() + " MRN(s) from the list -> " + list);
        return list;
    }

    // ---- add ---------------------------------------------------------------

    /** Click <b>Add</b> to open the post mortem form; polled, as it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/addpostmortem|addPostMortem/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/add/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__pmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PostMortem.clickAdd: Add control not found"); return false; }
        try { page.locator("#__pmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PostMortem.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("PostMortem.clickAdd: form did not render"); }
        waitForAngular(1200);
        return onForm();
    }

    public boolean onForm() {
        if (page.url().toLowerCase().contains("add-postmortemtemplate")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)"));
    }

    // ---- MRN + search ------------------------------------------------------

    /** Enter the <b>MRN</b> and click the search icon; reports whether a patient actually attached. */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;
        page.evaluate("(m) => {" + JS
                + " setInp('" + M + "MRNo', m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__pmSearch'; }", mrn);
        try { page.locator("#__pmSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PostMortem.searchByMrn: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);

        Object r = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=byNg('" + M + "Dischargeid');"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=byNg('" + M + "MRNo'); const s=window.angular.element(e).scope();"
                + "   const o=s && s.PostMortemTemplate;"
                + "   if(o){ const keys=Object.keys(o).filter(k=>/name|patient|mrn|age|gender|id$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient = keys.length? keys.slice(0,8).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ')"
                + "                          : '(model has no patient fields set)'; } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " return 'templateOptions='+(t?t.options.length:-1)+' | patient: '+patient"
                + "   +(msg?' | msg='+msg:''); }");
        lastFormSearch = r == null ? "" : r.toString();
        System.out.println("PostMortem: search MRN " + mrn + " -> " + lastFormSearch);
        return lastFormSearch;
    }

    /** True when the MRN search actually attached patient data to the model. */
    public boolean patientAttached() {
        if (lastFormSearch == null || lastFormSearch.isEmpty()) return false;
        String s = lastFormSearch.toLowerCase();
        if (s.contains("not found") || s.contains("no patient")) return false;
        return !s.contains("(model has no patient fields set)")
                && !s.contains("(no scope)") && !s.contains("scope read failed");
    }

    // ---- template ----------------------------------------------------------

    /** Select the <b>post mortem template</b> ({@code PostMortemTemplate.Dischargeid}). */
    public String selectTemplate() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "Dischargeid')); })");
        lastTemplate = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("PostMortem: Template = " + lastTemplate);
        return lastTemplate;
    }

    public boolean templateSelected() {
        return lastTemplate != null && !lastTemplate.isEmpty() && !lastTemplate.startsWith("(");
    }

    // ---- save --------------------------------------------------------------

    /** Click <b>Save</b> ({@code IUDPostMortemDetail()}) and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__pmToasts=[]; if(window.__pmObs) window.__pmObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__pmToasts.includes(t)) window.__pmToasts.push(t); }); };"
                + " window.__pmObs=new MutationObserver(grab); window.__pmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__pmPrior=(window.__pmToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDPostMortemDetail/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__pmSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PostMortem.save: Save button not found"); return ""; }

        try { page.locator("#__pmSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PostMortem.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__pmToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__pmPrior||[];"
                + " const a=(window.__pmToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const seen=(window.__pmToasts||[]).join(' ; ');"
                    + " const stillOnForm=!![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")]"
                    + "   .find(e=>e.offsetParent!==null);"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' stillOnForm='+stillOnForm+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("PostMortem.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
