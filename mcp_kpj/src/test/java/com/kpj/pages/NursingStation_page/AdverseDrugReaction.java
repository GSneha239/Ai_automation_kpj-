package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Adverse Drug Reaction</b> ({@code #/AdverseDrugReactionList}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Adverse Drug Reaction</b> → (<b>Add</b>, if the list opens first) →
 * enter <b>MRN</b> + search → select the <b>drug administered</b> → select <b>Severity</b> and
 * <b>Reaction Recorded By</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load) — several Nursing Station menu
 * links render an empty shell.</p>
 *
 * <p><b>Patient attachment is verified against the model</b>, requiring the loaded MRN to equal the one
 * searched. These screens will accept an MRN in the box and attach nobody, after which Save does nothing
 * and looks broken — and a naive scope scan can latch onto the app's label dictionary and report a patient
 * that is not there.</p>
 *
 * <p>The <b>drug administered</b> list is populated from the patient, so it is polled after the search
 * rather than read immediately.</p>
 */
public class AdverseDrugReaction extends BasePage {

    public AdverseDrugReaction(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/AdverseDrugReactionList";

    public String lastControls = "", lastBodyText = "";
    public String lastFormSearch = "", lastDrug = "", lastSeverity = "", lastRecordedBy = "";
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
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            // Scope to the panel that owns Save, so a duplicated search panel is never filled by mistake.
            + "const formScope=()=>{ const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
            + "  const save=btns.find(b=>/^\\s*save\\s*$/i.test(norm(b.textContent||b.value)))"
            + "    || btns.find(b=>/save|IUD/i.test(b.getAttribute('ng-click')||''));"
            + "  if(!save) return document;"
            + "  let n=save.parentElement;"
            + "  while(n && n!==document.body){"
            + "    if(n.querySelectorAll('select,input,textarea').length>2) return n;"
            + "    n=n.parentElement; }"
            + "  return document; };"
            + "const inScope=sel=>[...formScope().querySelectorAll(sel)].filter(vis);"
            + "const findInput=rx=>{ const re=new RegExp(rx,'i');"
            + "  return inScope('input[type=text], input:not([type]), input[type=number], textarea')"
            + "    .filter(e=>!/colFilter|pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };"
            // Document-wide variant. The MRN box sits in the patient header ABOVE the Save-owning panel,
            // so the scoped lookup cannot see it — searching only in scope left it empty and the app
            // answered "Please Enter MRN!". Safe here because the MRN field is unique on the screen.
            + "const findInputAnywhere=rx=>{ const re=new RegExp(rx,'i');"
            + "  return [...document.querySelectorAll('input[type=text], input:not([type]), textarea')].filter(vis)"
            + "    .filter(e=>!/colFilter|pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };"
            + "const findSelect=(rx,avoid)=>{ const re=new RegExp(rx,'i'); const bad=avoid?new RegExp(avoid,'i'):null;"
            + "  return inScope('select').filter(e=>!/pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e)) && (!bad||!bad.test(attrs(e)+' '+labelOf(e)))) || null; };";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to Adverse Drug Reaction, escalating menu click → hash route → full page load. */
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
                + "   .find(x=>/^adverse\\s*drug\\s*reaction$/i.test(norm(x.textContent))"
                + "        || /#\\/AdverseDrugReaction/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("AdverseDrugReaction.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("AdverseDrugReaction.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + LIST_ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("AdverseDrugReaction.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("adversedrugreaction")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text plus every control, so the real ng-models are visible. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)"
                + "   .map(e=>'TEXTAREA \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,20)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(ta).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,220)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("AdverseDrugReaction CONTROLS:\n" + lastControls);
        return lastControls;
    }

    /**
     * Open the entry form if the screen lands on a list.
     *
     * @return true when an Add/New was clicked, false when the form was already present (both are fine)
     */
    public boolean openFormIfNeeded() {
        Object hasMrn = page.evaluate("() => {" + JS
                + " return !!findInputAnywhere('mrno|mr\\\\s*no|prn'); }");
        if (Boolean.TRUE.equals(hasMrn)) {
            System.out.println("AdverseDrugReaction: entry form already present — no Add needed");
            return false;
        }
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/^\\s*(add|new)\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/^(add|new)/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__adrAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("AdverseDrugReaction: no Add/New control found"); return false; }
        try { page.locator("#__adrAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AdverseDrugReaction.openForm: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__adrAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return true;
    }

    // ---- MRN + search ------------------------------------------------------

    /**
     * Enter the <b>MRN</b> and search.
     *
     * <p>The <b>Reg. Type</b> radio ({@code PatientTypeOPDIPD}) scopes which register the MRN is looked up
     * in — the same pattern as the Ambulance screens. Each type is tried until a patient genuinely
     * attaches, because searching under the wrong one leaves the MRN echoed in the model while the app
     * still answers "Please Select Patient!" on save.</p>
     */
    public String searchByMrn(String mrn) {
        for (String mode : new String[]{"OPD", "IPD"}) {
            page.evaluate("(args) => {" + JS
                    + " const [m, mode] = args;"
                    + " const radios=[...document.querySelectorAll(\"input[type=radio]\")].filter(vis);"
                    + " const r=radios.find(x=>{ const g=x.closest('label,div,td,span');"
                    + "   return g && new RegExp('^\\\\s*'+mode+'\\\\s*$','i').test(norm(g.textContent)); })"
                    + "   || radios.find(x=>new RegExp(mode,'i').test(norm((x.closest('label,div,td,span')||{}).textContent||'')));"
                    + " if(r && !r.checked) r.click();"
                    + " const e=findInputAnywhere('mrno|mr\\\\s*no|prn');"
                    + " if(e) setEl(e, m);"
                    + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                    + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.id='__adrSearch'; }", java.util.List.of(mrn, mode));
            try { page.locator("#__adrSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AdverseDrugReaction.searchByMrn[" + mode + "]: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__adrSearch'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);

            // Stop as soon as real patient DETAIL loads — not merely the MRN we typed being echoed back.
            // SAME criterion as patientAttached(): the object carrying the searched MRN must ALSO carry
            // patient detail. An earlier version accepted any scope object with a name/id, which reported
            // "loaded" for an unrelated model while the app still refused to save.
            Object detail = page.evaluate("(wantMrn) => {" + JS
                    + " try { const e=findInputAnywhere('mrno|mr\\\\s*no|prn');"
                    + "   const s=window.angular.element(e).scope();"
                    + "   const mrnKeyOf=v=>Object.keys(v).find(k=>/^mrno$/i.test(k));"
                    + "   for(const k of Object.keys(s||{})){ const v=s[k];"
                    + "     if(!v || typeof v!=='object' || Array.isArray(v)) continue;"
                    + "     const mk=mrnKeyOf(v);"
                    + "     if(mk && String(v[mk]||'').trim()===String(wantMrn).trim()"
                    + "        && (v.PatientId||v.patientid||v.FirstName||v.PatientName)) return true; } }"
                    + " catch(err){} return false; }", mrn);
            lastPatientType = mode;
            if (Boolean.TRUE.equals(detail)) {
                System.out.println("AdverseDrugReaction: patient detail loaded under " + mode);
                break;
            }
            System.out.println("AdverseDrugReaction: no patient detail under " + mode);
        }

        // Match on the MRN VALUE, not merely on a model having an MRNo key — a label dictionary has one too.
        Object r = page.evaluate("(wantMrn) => {" + JS + TOAST_ELS
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=findInputAnywhere('mrno|mr\\\\s*no|prn'); const s=window.angular.element(e).scope();"
                // The MRN key is matched CASE-INSENSITIVELY: this screen spells it MRNO, others MRNo, and
                // reading v.MRNo directly silently found nothing on a model that was in fact populated.
                + "   const mrnKeyOf=v=>Object.keys(v).find(k=>/^mrno$/i.test(k));"
                + "   let o=null;"
                + "   for(const k of Object.keys(s||{})){ const v=s[k];"
                + "     if(v && typeof v==='object' && !Array.isArray(v)){"
                + "       const mk=mrnKeyOf(v);"
                + "       if(mk && String(v[mk]||'').trim()===String(wantMrn).trim()){ o=v; break; } } }"
                + "   if(!o) patient='(no model carries the searched MRN)';"
                + "   else { const mk=mrnKeyOf(o);"
                + "     const keys=Object.keys(o).filter(k=>/^(firstname|patientname|lastname|age|gender|patientid|id)$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient='MRNo='+o[mk]+(keys.length? ', '+keys.slice(0,6).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ') : ''); } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " const drugSel=findSelect('drug|medicine|administer', null);"
                + " return 'patient: '+patient+' | drugOptions='+(drugSel?drugSel.options.length:-1)"
                + "   +(msg?' | msg='+msg:''); }", mrn);
        lastFormSearch = r == null ? "" : r.toString();
        System.out.println("AdverseDrugReaction: search MRN " + mrn + " -> " + lastFormSearch);
        return lastFormSearch;
    }

    /** Result of the last search, plus which Reg. Type it was found under. */
    public String lastPatientType = "";

    /**
     * True only when real patient DETAIL loaded.
     *
     * <p>Deliberately stricter than "the model holds the MRN": the MRN in the model is just the value
     * typed in, so matching on it alone reported a patient as attached while the app still refused to save
     * with "Please Select Patient!". A name or patient id is what proves the lookup actually resolved.</p>
     */
    public boolean patientAttached() {
        if (lastFormSearch == null || lastFormSearch.isEmpty()) return false;
        String s = lastFormSearch.toLowerCase();
        if (s.contains("not found")) return false;
        return s.contains("patient: mrno=")
                && (s.contains("firstname=") || s.contains("patientname=") || s.contains("patientid="));
    }

    /** The MRN that actually attached, and a note of everything tried on the way. */
    public String workingMrn = "", mrnAttempts = "";

    /**
     * Search MRNs in turn until one actually attaches a patient.
     *
     * <p>This screen answers <b>"Please Select Patient!"</b> on Save when the lookup never resolved, and it
     * refuses MRNs that work perfectly well on other screens. A single hard-coded MRN therefore makes the
     * whole flow look broken when it is really the wrong patient. Each candidate is judged by
     * {@link #patientAttached()} — a real name or patient id in the model, not the echoed MRN.</p>
     */
    public String searchByMrnTrying(java.util.List<String> candidates) {
        StringBuilder tried = new StringBuilder();
        String last = "";
        for (String mrn : candidates) {
            if (mrn == null || mrn.isBlank()) continue;
            clearToasts();
            last = searchByMrn(mrn.trim());
            boolean ok = patientAttached();
            tried.append(tried.length() == 0 ? "" : "; ").append(mrn.trim())
                 .append(ok ? " -> ATTACHED" : " -> not attached");
            if (ok) {
                workingMrn = mrn.trim();
                mrnAttempts = tried.toString();
                System.out.println("AdverseDrugReaction: MRN " + workingMrn + " attached (tried: " + mrnAttempts + ")");
                return last;
            }
        }
        workingMrn = "";
        mrnAttempts = tried.toString();
        System.out.println("AdverseDrugReaction: no MRN attached (tried: " + mrnAttempts + ")");
        return last;
    }

    private void clearToasts() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);
    }

    /**
     * Ask the screen's own patient lookup for MRNs ({@code openPopupScreen()} &rarr; its Search).
     *
     * <p>Used when no supplied candidate attaches: rather than guessing more numbers, take patients this
     * environment actually has. The popup lists nothing until its own Search is run, so that is clicked
     * before the results are read.</p>
     */
    public java.util.List<String> discoverMrns(int max) {
        java.util.List<String> found = new java.util.ArrayList<>();
        try {
            page.evaluate("() => { const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis)"
                    + "   .find(x=>/openPopupScreen/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            waitForAngular(2500);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const b=[...root.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/SearchPatient\\(/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(b) b.click(); }");
            waitForAngular(3000);
            Object r = page.evaluate("(max) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const text=[...root.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).join(' ');"
                    // 100xxxxxx only: the popup rows also carry NRIC and phone numbers, and a looser
                    // pattern hands back numbers that are not MRNs at all.
                    + " return [...new Set((text.match(/\\b100\\d{6}\\b/g)||[]))].slice(0,max); }", max);
            if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) found.add(o.toString());
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " if(!dlg) return;"
                    + " const b=[...dlg.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/^\\s*(close|cancel|x)\\s*$/i.test(norm(x.textContent))"
                    + "        || /resetForm|closePopup|cancel/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("AdverseDrugReaction.discoverMrns: " + e.getMessage().split("\n")[0]);
        }
        System.out.println("AdverseDrugReaction.discoverMrns -> " + found);
        return found;
    }

    // ---- fields ------------------------------------------------------------

    /** Select the <b>drug administered</b> — its list is populated from the patient, so it is polled. */
    public String selectDrugAdministered() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const e=findSelect('drug|medicine|administer', 'severity|recorded');"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastDrug = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("AdverseDrugReaction: Drug administered = " + lastDrug);
        return lastDrug;
    }

    /** Select <b>Severity</b>. */
    public String selectSeverity() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const e=findSelect('severity', null);"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastSeverity = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("AdverseDrugReaction: Severity = " + lastSeverity);
        return lastSeverity;
    }

    /** Select <b>Reaction Recorded By</b>. */
    public String selectRecordedBy() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " let e=findSelect('recordedby|recorded\\\\s*by|reactionrecorded', 'severity');"
                + " if(!e) e=findSelect('recorded', 'severity');"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastRecordedBy = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("AdverseDrugReaction: Recorded by = " + lastRecordedBy);
        return lastRecordedBy;
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
                + " window.__adrToasts=[]; if(window.__adrObs) window.__adrObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__adrToasts.includes(t)) window.__adrToasts.push(t); }); };"
                + " window.__adrObs=new MutationObserver(grab); window.__adrObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__adrPrior=(window.__adrToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__adrSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("AdverseDrugReaction.save: Save button not found"); return ""; }

        try { page.locator("#__adrSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AdverseDrugReaction.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__adrSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__adrToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__adrPrior||[];"
                + " const a=(window.__adrToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__adrToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("AdverseDrugReaction.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
