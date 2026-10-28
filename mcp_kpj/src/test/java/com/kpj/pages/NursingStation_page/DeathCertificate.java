package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Death Certificate</b> ({@code #/DeathCertificate}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Death Certificate</b> → <b>Add</b> → enter <b>MRN</b> + search →
 * select the <b>certificate template</b> → enter the <b>form template body</b> → select <b>Department</b>
 * and <b>Doctor</b> → tick <b>Authenticate</b> → <b>Save</b> → toast.</p>
 *
 * <p>Structurally the sibling of {@link BirthCertificate}: same certificate shape, same CKEditor body,
 * same discharge-shaped ng-model holding the template list. Field names are resolved with a prefix
 * fallback ({@code DeathCertificate1.} then {@code DeathCertificate.}) so a naming difference between the
 * two screens surfaces as a reported {@code (no-field)} rather than a silent no-op — and
 * {@link #describeControls()} prints what the screen really offers.</p>
 *
 * <p><b>A death certificate needs a deceased patient</b>, so the MRN matters: {@link #harvestMrnsFromList()}
 * takes one the screen has already accepted rather than assuming a general-purpose MRN attaches. (That
 * assumption is exactly what blocks the Birth Certificate flow.)</p>
 */
public class DeathCertificate extends BasePage {

    public DeathCertificate(Page page) { super(page); }

    public static final String ROUTE = "#/DeathCertificate";

    public String lastControls = "", lastBodyText = "";
    public String lastFormSearch = "", lastTemplate = "", lastBody = "";
    public String lastDepartment = "", lastDoctor = "", lastSaveDiagnostics = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            // Prefix fallback. Verified live 2026-08-10: this screen REUSES THE BIRTH CERTIFICATE
            // CONTROLLER — its fields are ng-model="BirthCertificate1.*" / "BirthCertificate.MRNo", not
            // DeathCertificate*. The Death* prefixes are tried first in case that is ever corrected, then
            // the Birth* ones it actually uses.
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const byField=f=>byNg('DeathCertificate1.'+f) || byNg('DeathCertificate.'+f)"
            + "  || byNg('BirthCertificate1.'+f) || byNg('BirthCertificate.'+f)"
            + "  || byNg('CertiDetails.'+f);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pickEl=async(e)=>{ if(!e) return '(no-field)';"
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

    /** Navigate, escalating menu click → hash route → full page load (the menu link alone renders blank). */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { }

        // Open "Nursing Station" only if it is closed (a second click on an open entry CLOSES it, and the sidebar is built lazily after login),
        // then look the link up INSIDE that entry — an unscoped lookup is how this walk could end on the Patient Dashboard.
        expandMenuEntry(null, "^\\s*nursing\\s*station\\s*$");
        waitForAngular(800);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const scope=(top&&top.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^death\\s*certificate$/i.test(norm(x.textContent))"
                + "        || /#\\/DeathCertificate/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("DeathCertificate.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("DeathCertificate.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("DeathCertificate.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("deathcertificate")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text plus every control, so the real field names are visible. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)"
                + "   .map(e=>'TEXTAREA \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,18)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " const ck=(window.CKEDITOR && CKEDITOR.instances)? Object.keys(CKEDITOR.instances).length : 0;"
                + " return 'ckeditors='+ck+'\\n'+sel.concat(ta).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,200)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("DeathCertificate CONTROLS:\n" + lastControls);
        return lastControls;
    }

    // ---- list -> harvest a usable MRN --------------------------------------

    /** MRNs already present in the death certificate list — patients this screen has accepted. */
    public java.util.List<String> harvestMrnsFromList() {
        page.evaluate("() => {" + JS
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
                + " const b=c.find(x=>/search/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__dcHarvest'; }");
        try { page.locator("#__dcHarvest").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__dcHarvest'); if(e) e.removeAttribute('id'); }");
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
        System.out.println("DeathCertificate: harvested " + list.size() + " MRN(s) -> " + list);
        return list;
    }

    /** Click <b>Add</b> to open the certificate form. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/AddDeathCertificate|AddCertificate/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/add/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__dcAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DeathCertificate.clickAdd: Add control not found"); return false; }
        try { page.locator("#__dcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DeathCertificate.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dcAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('select').length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        waitForAngular(2000);
        return true;
    }

    // ---- form --------------------------------------------------------------

    /** Enter the <b>MRN</b> and search; reports whether a patient actually attached to the model. */
    public String searchByMrn(String mrn) {
        page.evaluate("(m) => {" + JS
                + " setEl(byField('MRNo'), m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__dcSearch'; }", mrn);
        try { page.locator("#__dcSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DeathCertificate.searchByMrn: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dcSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);

        Object r = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=byField('Dischargeid');"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=byField('MRNo'); const s=window.angular.element(e).scope();"
                + "   const o=s && (s.DeathCertificate || s.DeathCertificate1"
                + "        || s.BirthCertificate || s.BirthCertificate1);"
                + "   if(o){ const keys=Object.keys(o).filter(k=>/name|patient|mrn|age|gender|id$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient = keys.length? keys.slice(0,8).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ')"
                + "                          : '(model has no patient fields set)'; } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " return 'templateOptions='+(t?t.options.length:-1)+' | patient: '+patient"
                + "   +(msg?' | msg='+msg:''); }");
        lastFormSearch = r == null ? "" : r.toString();
        System.out.println("DeathCertificate: search MRN " + mrn + " -> " + lastFormSearch);
        return lastFormSearch;
    }

    public boolean patientAttached() {
        if (lastFormSearch == null || lastFormSearch.isEmpty()) return false;
        String s = lastFormSearch.toLowerCase();
        if (s.contains("not found") || s.contains("no patient")) return false;
        return !s.contains("(model has no patient fields set)")
                && !s.contains("(no scope)") && !s.contains("scope read failed");
    }

    /**
     * Select the <b>certificate template</b> (the discharge-shaped model, as on Birth Certificate).
     *
     * <p>The list is filled asynchronously after the patient search, so a real option is WAITED for rather
     * than sampled once. A single read can land on the still-empty list and report {@code (no-option)} for
     * a screen that is merely loading — and because Save succeeds regardless (the template is optional),
     * that failure looks inexplicable rather than like the race it is.</p>
     */
    public String selectTemplate() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')]"
                    + " .filter(e=>/Dischargeid/i.test(e.getAttribute('ng-model')||''))"
                    + " .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " .some(e=>[...e.options].some(o=>o.value"
                    + "   && !/^-*\\s*select/i.test((o.text||'').trim())))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("DeathCertificate.selectTemplate: no template option within 15s");
        }

        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pickEl(byField('Dischargeid'))); })");
        lastTemplate = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("DeathCertificate: Template = " + lastTemplate);
        return lastTemplate;
    }

    /** Enter the <b>form template body</b> (a CKEditor — writing the element does nothing). */
    public String enterTemplateBody(String text) {
        Object r = page.evaluate("(t) => {" + JS
                + " if(window.CKEDITOR && CKEDITOR.instances){"
                + "   const keys=Object.keys(CKEDITOR.instances);"
                + "   if(keys.length){ for(const k of keys){ try{ CKEDITOR.instances[k].setData(t);"
                + "       CKEDITOR.instances[k].updateElement(); }catch(e){} }"
                + "     return 'CKEDITOR('+keys.length+'): '+keys.join(','); } }"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].find(vis);"
                + " if(ce){ ce.innerHTML=t; ce.dispatchEvent(new Event('input',{bubbles:true})); return 'contenteditable'; }"
                + " return '(no-editor)'; }", text);
        lastBody = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("DeathCertificate: template body -> " + lastBody);
        return lastBody;
    }

    /**
     * {@code page.evaluate} that survives the page navigating underneath it (Save moves the app on while the next call is already in flight:
     * "Execution context was destroyed, most likely because of a navigation" — not a failure of the save). Repeated on the new page.
     */
    private Object evalSafe(String js) {
        for (int i = 0; i < 4; i++) {
            try { return page.evaluate(js); }
            catch (com.microsoft.playwright.PlaywrightException e) {
                String m = String.valueOf(e.getMessage());
                if (m.contains("Execution context was destroyed") || m.contains("navigation")) { page.waitForTimeout(1500); continue; }
                throw e;
            }
        }
        return null;
    }

    // ---- patient: the screen's own lookup ----------------------------------

    /** What the lookup route ended up doing, for the report. */
    public String lastLookupNote = "";

    /** The patient fields the form's model holds right now: name, gender, age, admission id, patient id, MRN ("" when unreadable). */
    public java.util.Map<String, String> readFormFields() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        Object r = page.evaluate("() => {" + JS
                + " const e=byField('MRNo'); if(!e) return null;"
                + " const s=A.element(e).scope();"
                + " const o=(s && (s.DeathCertificate || s.DeathCertificate1 || s.BirthCertificate || s.BirthCertificate1)) || {};"
                + " const full=String(o.PatientFullDtl||'');"
                + " const g=re=>{ const m=full.match(re); return m? norm(m[1]) : ''; };"
                + " return { name: g(/Name\\s*:\\s*(.*?)\\s+MRN/i) || String(o.FirstName||''),"
                + "   gender: g(/Gen\\w*\\s*:\\s*([A-Za-z-]+)/i) || String(o.Gender||''),"
                + "   age: g(/Age\\s*:\\s*(.*?)\\s+Passport/i) || String(o.PatientAge||''),"
                + "   admid: String(o.admid||0), patientid: String(o.patientid||0), mrn: e.value||'' }; }");
        if (r instanceof java.util.Map) for (java.util.Map.Entry<?, ?> en : ((java.util.Map<?, ?>) r).entrySet())
            out.put(String.valueOf(en.getKey()), en.getValue() == null ? "" : String.valueOf(en.getValue()).trim());
        return out;
    }

    /** The form's patient summary, in the same words {@link #searchByMrn} uses (so {@link #patientAttached()} can judge it). */
    public String readFormPatient() {
        Object r = page.evaluate("() => {" + JS + TOAST_ELS
                + " const t=byField('Dischargeid');"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=byField('MRNo'); const s=window.angular.element(e).scope();"
                + "   const o=s && (s.DeathCertificate || s.DeathCertificate1 || s.BirthCertificate || s.BirthCertificate1);"
                + "   if(o){ const keys=Object.keys(o).filter(k=>/name|patient|mrn|age|gender|id$/i.test(k) && !/^mrno$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient = keys.length? keys.slice(0,8).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ')"
                + "                          : '(model has no patient fields set)'; } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " return 'templateOptions='+(t?t.options.length:-1)+' | patient: '+patient"
                + "   +(msg?' | msg='+msg:''); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Open the lookup (the search icon beside the MRN box), type {@code fragment} in its MRN box, click Search and click the select icon of
     * row {@code row}. Returns the row's text, or {@code null} when there is no such row (or no rows at all).
     */
    private String lookupPick(String fragment, int row) {
        final String vis = "const vis=e=>!!e&&!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";
        boolean open = Boolean.TRUE.equals(page.evaluate("() => {" + vis
                + " return [...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].some(vis); }"));
        if (!open) {
            Object tagged = page.evaluate("() => {" + vis
                    + " const b=[...document.querySelectorAll(\"[ng-click*='openPopupScreen']\")].find(vis);"
                    + " if(!b) return false; b.id='__dcLookupOpen'; return true; }");
            if (!Boolean.TRUE.equals(tagged)) return null;
            page.locator("#__dcLookupOpen").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            page.evaluate("() => { const e=document.getElementById('__dcLookupOpen'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        Object box = page.evaluate("() => {" + vis
                + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0]||document;"
                + " const i=[...dlg.querySelectorAll('input')].filter(vis).find(x=>/mrno?$/i.test(x.getAttribute('ng-model')||''));"
                + " if(!i) return false; i.id='__dcLookupMrn'; return true; }");
        if (!Boolean.TRUE.equals(box)) return null;
        page.locator("#__dcLookupMrn").fill(fragment, new com.microsoft.playwright.Locator.FillOptions().setTimeout(5000));
        page.evaluate("() => { const e=document.getElementById('__dcLookupMrn'); if(e) e.removeAttribute('id'); }");
        Object sb = page.evaluate("() => {" + vis
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0]||document;"
                + " const b=[...dlg.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/searchpatient\\s*\\(/i.test(x.getAttribute('ng-click')||'') || /^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return false; b.id='__dcLookupSearch'; return true; }");
        if (!Boolean.TRUE.equals(sb)) return null;
        page.locator("#__dcLookupSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
        page.evaluate("() => { const e=document.getElementById('__dcLookupSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll(\"[ng-click*='SetSearchPatient']\")].some(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception noRows) { return null; }
        Object pick = page.evaluate("(n) => {" + vis
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll(\"[ng-click*='SetSearchPatient']\")].filter(vis)[n]; if(!b) return null;"
                + " b.id='__dcLookupPick'; const r=b.closest('tr,.ui-grid-row'); return r? norm(r.textContent).slice(0,90) : ''; }", row);
        if (pick == null) return null;
        page.locator("#__dcLookupPick").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
        page.evaluate("() => { const e=document.getElementById('__dcLookupPick'); if(e) e.removeAttribute('id'); }");
        waitForAngular(3500);
        return String.valueOf(pick);
    }

    /**
     * Pick the patient the way a person does: through the screen's own <b>patient lookup</b> (search icon beside the MRN box) — type a few
     * digits, Search, click a row's select icon. The form's own MRN box answers "Patient Not Found!" for most MRNs and, for a short
     * fragment, attaches the first living patient it gets; the lookup is the route that works for any listed patient.
     *
     * <p>The certificate report is built from the patient's ADMISSION, so rows are tried in turn (up to {@code maxRows}) until the form holds a
     * patient with an admission ({@code admid > 0}). If none of those rows has one, the first row is picked again so the flow can still
     * go on, and {@link #lastLookupNote} says so.</p>
     */
    public String pickPatientFromLookup(java.util.List<String> fragments, int maxRows) {
        StringBuilder tried = new StringBuilder();
        for (String frag : fragments) {
            boolean anyRow = false;
            for (int row = 0; row < maxRows; row++) {
                String text;
                try { text = lookupPick(frag, row); }
                catch (Exception e) { tried.append(frag).append('#').append(row).append(" -> ").append(String.valueOf(e.getMessage()).split("\n")[0]).append("; "); break; }
                if (text == null) { if (row == 0) tried.append(frag).append(" -> no rows; "); break; }
                anyRow = true;
                lastFormSearch = readFormPatient();
                java.util.Map<String, String> f = readFormFields();
                int adm = 0;
                try { adm = Integer.parseInt(f.getOrDefault("admid", "0")); } catch (Exception ignore) { }
                boolean ok = patientAttached();
                tried.append(frag).append('#').append(row).append(" -> ").append(ok ? "attached, admid=" + adm : "no patient bound").append(" (").append(text).append("); ");
                if (ok && adm > 0) {
                    lastLookupNote = "typed \"" + frag + "\" in the lookup and picked row " + (row + 1) + " (" + text + "), a patient with an admission (admid " + adm + ")";
                    System.out.println("DeathCertificate: lookup attached " + f.get("mrn") + " — " + lastLookupNote);
                    return lastFormSearch;
                }
            }
            if (anyRow) {
                // Rows exist but none carries an admission: fall back to the first row so the rest of the flow has a patient.
                try {
                    String text = lookupPick(frag, 0);
                    lastFormSearch = readFormPatient();
                    lastLookupNote = "typed \"" + frag + "\" in the lookup; none of the first " + maxRows + " rows has an admission, so row 1 was used"
                            + (text == null ? "" : " (" + text + ")") + " — its report may print without content";
                } catch (Exception e) { lastLookupNote = "lookup fallback failed: " + String.valueOf(e.getMessage()).split("\n")[0]; }
                System.out.println("DeathCertificate: " + lastLookupNote);
                return lastFormSearch;
            }
        }
        lastLookupNote = "no patient could be picked from the lookup: " + tried;
        System.out.println("DeathCertificate: " + lastLookupNote);
        return lastLookupNote;
    }

    // ---- template body ------------------------------------------------------

    /**
     * Fill the blanks of the <b>Death Certificate</b> template the editor is loaded with.
     *
     * <p>Choosing the template loads a real certificate into a CKEditor: "DEATH CERTIFICATE" with fifteen lines ending in {@code ________}
     * (Government of, Registration No., Name of Deceased, Sex, Age, Date / Time / Place / Cause of Death, Nationality, Occupation, Address,
     * Name of Father, Name of Mother, Date of Registration) and a Registrar line. Replacing all of that with one line of text prints a report
     * that says nothing, so each blank is filled in place and the rest is kept. This screen shares its controller with Birth Certificate and
     * carries SEVERAL editors, so the one holding the "DEATH CERTIFICATE" text is chosen by its content — not "all of them".</p>
     *
     * <p>The chosen editor's data is also pushed into its Angular model ({@code setData()} + {@code updateElement()} only change the textarea;
     * Save posts the model).</p>
     *
     * @param values 15 values in template order: government, regNo, name, sex, age, dateOfDeath, timeOfDeath, place, cause, nationality,
     *               occupation, address, father, mother, dateOfRegistration
     * @return {@code filled n of 15 blanks; model synced=...; text: ...}, or a string starting with "(" when nothing could be done
     */
    public String fillTemplateBlanks(String... values) {
        String[] labels = {"Government of", "Registration No\\.", "Name of Deceased", "Sex", "Age", "Date of Death", "Time of Death",
                "Place of Death", "Cause of Death", "Nationality", "Occupation", "Address", "Name of Father", "Name of Mother",
                "Date of Registration"};
        java.util.List<java.util.List<String>> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < labels.length && i < values.length; i++) pairs.add(java.util.List.of(labels[i], values[i] == null ? "" : values[i]));
        Object r = page.evaluate("async (pairs) => {"
                + " const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " if(!(window.CKEDITOR && CKEDITOR.instances)) return '(no-editor)';"
                + " const eds=Object.keys(CKEDITOR.instances).map(k=>CKEDITOR.instances[k]);"
                + " const ed=eds.find(e=>/death\\s*certificate/i.test(String(e.getData()).replace(/<[^>]+>/g,' ')))"
                + "   || eds.slice().sort((a,b)=>(String(b.getData()).match(/_{3,}/g)||[]).length-(String(a.getData()).match(/_{3,}/g)||[]).length)[0];"
                + " if(!ed) return '(no-editor)';"
                + " let html=ed.getData();"
                + " const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');"
                + " let done=0;"
                + " for(const [label,value] of pairs){"
                + "   const rx=new RegExp('('+label+'(?:&nbsp;|\\\\s)*:?(?:&nbsp;|\\\\s)*)_{3,}','i');"
                + "   if(rx.test(html)){ html=html.replace(rx,(m,g)=>g+esc(value)); done++; } }"
                + " if(!done) return '(no-blanks in the template body)';"
                + " ed.setData(html); await sleep(500); ed.updateElement();"
                + " const data=ed.getData(); const el=ed.element && ed.element.$;"
                + " let synced='n/a (no model)';"
                + " if(el){ const ng=el.getAttribute('ng-model'); const ctrl=angular.element(el).controller('ngModel'); const sc=angular.element(el).scope();"
                + "   if(ng && ctrl && sc){ try{ sc.$apply(()=>ctrl.$setViewValue(data)); }catch(e){ ctrl.$setViewValue(data); try{ sc.$applyAsync(); }catch(x){} }"
                + "     await sleep(300); synced=String(String(sc.$eval(ng))===data); } }"
                + " const plain=data.replace(/<br\\s*\\/?>/gi,'\\n').replace(/<\\/p>/gi,'\\n').replace(/<[^>]+>/g,'').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&');"
                + " return 'filled '+done+' of '+pairs.length+' blanks; model synced='+synced+'; text: '+plain.replace(/\\s*\\n\\s*/g,' / ').replace(/\\s+/g,' ').trim().slice(0,640); }",
                pairs);
        lastBody = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("DeathCertificate: template blanks -> " + lastBody);
        return lastBody;
    }

    // ---- the report Save generates -----------------------------------------

    public String lastReportUrl = "", lastReportDiagnostics = "";
    public byte[] lastReportPng = null;
    public boolean reportBlank = true;

    /**
     * The report opens as a PDF in a NEW tab and is judged on the file itself, read from inside the browser ({@code PdfReport}): the check
     * this used to make ({@code ReportCheck}) downloaded the file through a separate client that fails on this site's certificate chain and
     * then measured a SCREENSHOT of Chrome's PDF viewer — which cannot see inside the viewer and is white however good the report is.
     * {@link #lastReportPng} is page 1 of the PDF rendered to an image, so the report shows in the test report.
     */
    public String captureReport(int tabsBefore, int waitMs) {
        com.kpj.pages.PdfReport.Result pr = com.kpj.pages.PdfReport.capture(page, tabsBefore, waitMs);
        if (pr.url != null && !pr.url.isEmpty()) {
            lastReportUrl = pr.url; lastReportDiagnostics = pr.diagnostics; lastReportPng = pr.png; reportBlank = pr.blank;
            return lastReportDiagnostics;
        }
        com.kpj.pages.ReportCheck rc = com.kpj.pages.ReportCheck.after(page, tabsBefore, 3000, "Nursing Station - Death Certificate - generated report.png");
        lastReportUrl = rc.url; lastReportDiagnostics = rc.diagnostics; lastReportPng = rc.png; reportBlank = rc.blank;
        return lastReportDiagnostics;
    }

    public boolean reportProduced() { return lastReportUrl != null && !lastReportUrl.isEmpty(); }

    /** Select <b>Department</b> then <b>Doctor</b>. */
    public String selectDepartmentAndDoctor() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const d=await pickEl(byField('departmentid')); await sleep(1000);"
                + " const dr=await pickEl(byField('payableid') || byField('doctorid'));"
                + " resolve('Department='+d+' | Doctor='+dr); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("Department=")) lastDepartment = t.substring(11).trim();
            else if (t.startsWith("Doctor=")) lastDoctor = t.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("DeathCertificate: " + out);
        return out;
    }

    /** Tick <b>Authenticate</b>. */
    public boolean tickAuthenticate() {
        Object r = page.evaluate("() => {" + JS
                + " const cb=byField('isfinalized');"
                + " if(!cb) return false; if(!cb.checked) cb.click(); return cb.checked; }");
        waitForAngular(500);
        boolean ok = Boolean.TRUE.equals(r);
        System.out.println("DeathCertificate: Authenticate = " + ok);
        return ok;
    }

    /** Click <b>Save</b> and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__dcToasts=[]; if(window.__dcObs) window.__dcObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__dcToasts.includes(t)) window.__dcToasts.push(t); }); };"
                + " window.__dcObs=new MutationObserver(grab); window.__dcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__dcPrior=(window.__dcToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUD.*(Death|Certificate|Dischare)/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__dcSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("DeathCertificate.save: Save button not found"); return ""; }

        try { page.locator("#__dcSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DeathCertificate.save: Save click failed - " + e.getMessage()); }
        try { evalSafe("() => { const e=document.getElementById('__dcSave'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
        try { waitForAngular(800); } catch (Exception ignore) { }
        try { acceptSaveDialog(); } catch (Exception ignore) { }
        try { waitForAngular(500); } catch (Exception ignore) { }

        try {
            page.waitForFunction("() => (window.__dcToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        // The toasts the observer caught, plus whatever is on screen NOW (the observer is lost if the page navigated after Save).
        Object r = evalSafe("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " const prior=window.__dcPrior||[];"
                + " const a=(window.__dcToasts||[]).concat(toastEls().map(x=>norm(x.textContent))).filter(x=>x && !prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = evalSafe("() => { const seen=(window.__dcToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("DeathCertificate.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
