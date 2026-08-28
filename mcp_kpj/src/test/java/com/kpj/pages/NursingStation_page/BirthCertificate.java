package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Birth Certificate</b> ({@code #/BirthCertificate}) — Page Object.
 *
 * <p>Intended flow: enter <b>MRN</b> + search → select the <b>certificate template</b> → fill <b>Child
 * Details</b> (name of child, name of father, address, gender, date of birth, place of birth) → enter the
 * <b>form template body</b> → select <b>Department</b> and <b>Doctor</b> → tick <b>Authenticate</b> →
 * <b>Save</b>.</p>
 *
 * <p><b>Navigation is deliberately stubborn.</b> On devhis this route resolves but renders an empty shell
 * (body text just "Welcome: Employee 1306:(KPJ)", one stray input, no form), so this class tries three
 * ways in turn — menu click, hash change, then a FULL page load of the route — before giving up. The
 * sibling <b>Certificate</b> screen ({@code #/Certificate}) renders normally, so this is specific to
 * Birth Certificate.</p>
 *
 * <p>{@link #describeControls()} dumps whatever the screen does render; that is what identifies the real
 * field names once the screen comes up.</p>
 */
public class BirthCertificate extends BasePage {

    public BirthCertificate(Page page) { super(page); }

    public static final String ROUTE = "#/BirthCertificate";

    public String lastControls = "", lastBodyText = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;";

    /**
     * Navigate to Birth Certificate, escalating until the screen actually renders:
     * menu click → hash change → full page load.
     *
     * @return true only when real form controls are present, not merely when the URL matches
     */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("BirthCertificate.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        // 1) via the menu
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^birth\\s*certificate$/i.test(norm(x.textContent))"
                + "        || /#\\/BirthCertificate/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;
        System.out.println("BirthCertificate.nav: menu click left the screen blank — trying the hash route");

        // 2) hash change
        try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;
        System.out.println("BirthCertificate.nav: hash route left the screen blank — trying a full page load");

        // 3) full page load — a hash change alone does not re-bootstrap a broken view
        try {
            page.navigate(baseUrl + "/" + ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("BirthCertificate.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        try {
            page.waitForFunction("() => document.querySelectorAll('select,input[type=text]').length > 2",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        return screenRendered();
    }

    /** True when the route is up AND the screen rendered real controls (not just the empty shell). */
    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("birthcertificate")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], textarea').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    public boolean onRoute() {
        return page.url().toLowerCase().contains("birthcertificate");
    }

    /**
     * Enter the <b>MRN</b> on the list screen and click <b>Search</b>.
     *
     * @return a summary of what the search returned
     */
    public String searchByMrn(String mrn) {
        page.evaluate("(m) => {" + JS
                + " const A=window.angular;"
                + " const e=[...document.querySelectorAll(\"input[ng-model='BirthCertificate.MRNo']\")].find(vis);"
                + " if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){ c.$setViewValue(m); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const c2=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis);"
                + " const b=c2.find(x=>/search/i.test(x.getAttribute('ng-click')||''))"
                + "   || c2.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__bcSearch'; }", mrn);
        try { page.locator("#__bcSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BirthCertificate.searchByMrn: Search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bcSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);

        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('.ui-grid-row, table tbody tr').length;"
                + " const msg=[...document.querySelectorAll('.toast-message')].map(x=>norm(x.textContent)).join(' | ');"
                + " return 'rows='+rows+(msg?' | msg='+msg:''); }");
        String out = r == null ? "" : r.toString();
        System.out.println("BirthCertificate: search MRN " + mrn + " -> " + out);
        return out;
    }

    /**
     * Search the certificate list over a wide date window and return the MRNs it already holds.
     *
     * <p>Those patients have already been accepted by this screen, so they satisfy whatever it requires —
     * far more reliable than assuming a general-purpose MRN works here. (A birth certificate plausibly
     * needs a patient with a delivery record, which most MRNs will not have.)</p>
     *
     * @return distinct MRNs from the list, or empty when it holds none
     */
    public java.util.List<String> harvestMrnsFromList() {
        page.evaluate("() => {" + FORM_JS
                + " const today=new Date(); const pad=n=>String(n).padStart(2,'0');"
                + " const fmt=d=>pad(d.getMonth()+1)+'-'+pad(d.getDate())+'-'+d.getFullYear();"   // mm-dd-yyyy here
                + " const from=new Date(today.getFullYear()-3, today.getMonth(), today.getDate());"
                + " setInp('BirthCertificate1.fromdate', fmt(from));"
                + " setInp('BirthCertificate1.todate', fmt(today));"
                + " const e=byNg('BirthCertificate.MRNo');"
                + " if(e){ const c=A.element(e).controller('ngModel'); e.value=''; if(c){ c.$setViewValue(''); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); }"
                + " const c2=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
                + " const b=c2.find(x=>/search/i.test(x.getAttribute('ng-click')||''))"
                + "   || c2.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__bcHarvest'; }");
        try { page.locator("#__bcHarvest").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BirthCertificate.harvest: search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bcHarvest'); if(e) e.removeAttribute('id'); }");
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
        System.out.println("BirthCertificate: harvested " + list.size() + " MRN(s) from the list -> " + list);
        return list;
    }

    /** Click <b>Add</b> ({@code AddBirthCertificate()}) to open the certificate form. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/AddBirthCertificate/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__bcAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BirthCertificate.clickAdd: Add button not found"); return false; }
        try { page.locator("#__bcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BirthCertificate.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bcAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('select').length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("BirthCertificate.clickAdd: form did not render more selects"); }
        waitForAngular(2000);
        return true;
    }

    // ---- form fields -------------------------------------------------------

    /** Shared JS helpers for the form: ngModel-aware setters and option pickers. */
    private static final String FORM_JS = JS
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

    public String lastTemplate = "", lastGender = "", lastDepartment = "", lastDoctor = "";
    public String lastChildDetails = "", lastDobPlace = "", lastBody = "";

    /** Enter the <b>MRN</b> on the FORM and click its search icon ({@code SearchPatientByMRNo()}). */
    public String searchPatientOnForm(String mrn) {
        page.evaluate("(m) => {" + FORM_JS
                + " setInp('BirthCertificate.MRNo', m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__bcFormSearch'; }", mrn);
        try { page.locator("#__bcFormSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BirthCertificate.searchPatientOnForm: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bcFormSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);

        // Did a PATIENT actually attach? The template list filling up proves nothing (it may load
        // regardless), and this screen shows no patient-name field — so read the Angular model, which is
        // what Save posts. Without this, a save that fails for want of a patient looks like a broken Save.
        Object r = page.evaluate("() => {" + FORM_JS + TOAST_ELS
                + " const t=byNg('BirthCertificate1.Dischargeid');"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=byNg('BirthCertificate.MRNo');"
                + "   const s=window.angular.element(e).scope();"
                + "   const o=s && (s.BirthCertificate || s.BirthCertificate1);"
                + "   if(o){ const keys=Object.keys(o).filter(k=>/name|patient|mrn|age|gender|id$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient = keys.length? keys.slice(0,8).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ')"
                + "                          : '(model has no patient fields set)'; } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " return 'templateOptions='+(t?t.options.length:-1)+' | patient: '+patient"
                + "   +(msg?' | msg='+msg:''); }");
        String out = r == null ? "" : r.toString();
        lastFormSearch = out;
        System.out.println("BirthCertificate: form MRN search -> " + out);
        return out;
    }

    /** Result of the last form-level MRN search, including whether a patient bound to the model. */
    public String lastFormSearch = "";

    /** The MRN that actually attached, and a note of everything tried on the way. */
    public String workingMrn = "", mrnAttempts = "";

    /**
     * Search MRNs in turn until one actually attaches a patient.
     *
     * <p>This screen answers <b>"Patient Not Found!"</b> for an MRN that resolves perfectly well elsewhere
     * (Vitals Details, Intake Output Chart), so a single hard-coded MRN makes the whole flow look broken
     * when it is really the wrong patient. Each candidate is judged by {@link #patientAttached()} — the
     * Angular model, not the toast — and the one that works is reported.</p>
     *
     * @return the search result for the MRN that attached, or the last failure when none did
     */
    public String searchPatientTryingMrns(java.util.List<String> candidates) {
        StringBuilder tried = new StringBuilder();
        String last = "";
        for (String mrn : candidates) {
            if (mrn == null || mrn.isBlank()) continue;
            clearToasts();
            last = searchPatientOnForm(mrn.trim());
            boolean ok = patientAttached();
            tried.append(tried.length() == 0 ? "" : "; ").append(mrn.trim())
                 .append(ok ? " -> ATTACHED" : " -> " + shortReason(last));
            if (ok) {
                workingMrn = mrn.trim();
                mrnAttempts = tried.toString();
                System.out.println("BirthCertificate: MRN " + workingMrn + " attached (tried: " + mrnAttempts + ")");
                return last;
            }
        }
        workingMrn = "";
        mrnAttempts = tried.toString();
        System.out.println("BirthCertificate: no MRN attached (tried: " + mrnAttempts + ")");
        return last;
    }

    private static String shortReason(String searchResult) {
        if (searchResult == null) return "?";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("msg=(.*)$").matcher(searchResult);
        if (m.find()) return m.group(1).trim();
        return searchResult.contains("(model has no patient fields set)") ? "no patient bound" : "not attached";
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
     * Ask the app for real MRNs, via the patient-lookup icon beside the MRN box.
     *
     * <p>Used when none of the supplied candidates attach: rather than guessing more numbers, this opens
     * the screen's own patient search and reads MRNs out of its results, so the retry uses patients this
     * environment actually has.</p>
     */
    public java.util.List<String> discoverMrns(int max) {
        java.util.List<String> found = new java.util.ArrayList<>();
        Object opened = page.evaluate("() => {"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis)"
                + "   .find(x=>{ const c=x.getAttribute('ng-click')||'';"
                + "     return /popup|patientsearch|searchpatientby(?!mrno)/i.test(c)"
                + "         || (/searchpatient/i.test(c) && !/ByMRNo/i.test(c)); });"
                + " if(!b) return '(no patient-lookup button)';"
                + " b.click(); return 'clicked [ng-click='+(b.getAttribute('ng-click')||'-')+']'; }");
        System.out.println("BirthCertificate.discoverMrns: " + opened);
        waitForAngular(2500);

        Object r = page.evaluate("(max) => {"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                + " const root=dlg||document;"
                + " const text=[...root.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).join(' ');"
                + " const hits=[...new Set((text.match(/\\b1\\d{8}\\b/g)||[]))].slice(0,max);"
                + " return hits; }", max);
        if (r instanceof java.util.List) {
            for (Object o : (java.util.List<?>) r) found.add(o.toString());
        }

        if (found.isEmpty()) {
            // The popup is a patient SEARCH: it lists nothing until its own Search is run. Drive that,
            // then read the results — otherwise this reports "no MRNs" for a lookup that simply had not
            // been asked anything yet.
            Object searched = page.evaluate("() => {"
                    + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const b=[...root.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /search/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Search in the popup)';"
                    + " b.click(); return 'popup Search clicked [ng-click='+(b.getAttribute('ng-click')||'-')+']'; }");
            System.out.println("BirthCertificate.discoverMrns: " + searched);
            waitForAngular(3000);

            Object again = page.evaluate("(max) => {"
                    + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const text=[...root.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).join(' ');"
                    + " return [...new Set((text.match(/\\b1\\d{8}\\b/g)||[]))].slice(0,max); }", max);
            if (again instanceof java.util.List) {
                for (Object o : (java.util.List<?>) again) found.add(o.toString());
            }

            if (found.isEmpty()) {
                Object dump = page.evaluate("() => {"
                        + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                        + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                        + " if(!dlg) return '(no popup open)';"
                        + " const ctl=[...dlg.querySelectorAll('select,input,button')].filter(vis)"
                        + "   .map(e=>e.tagName+' \"'+norm(e.placeholder||e.textContent||'').slice(0,25)+'\" [ng='"
                        + "     +(e.getAttribute('ng-model')||e.getAttribute('ng-click')||'?')+']');"
                        + " const rows=[...dlg.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                        + "   .map(t=>norm(t.textContent).slice(0,70)).filter(t=>t).slice(0,4);"
                        + " return 'popup controls: '+[...new Set(ctl)].slice(0,12).join(' | ')"
                        + "   +' || rows: '+(rows.length? rows.join(' / ') : '(none)'); }");
                System.out.println("BirthCertificate.discoverMrns: " + dump);
            }
        }

        // Close whatever opened, so the form is usable again.
        try {
            page.evaluate("() => {"
                    + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " if(!dlg) return;"
                    + " const b=[...dlg.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/^\\s*(close|cancel|x)\\s*$/i.test(norm(x.textContent))"
                    + "        || /resetForm|closePopup|cancel/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
        } catch (Exception ignore) { }
        waitForAngular(1500);

        System.out.println("BirthCertificate.discoverMrns -> " + found);
        return found;
    }

    /** True when the form-level MRN search actually attached patient data to the model. */
    public boolean patientAttached() {
        if (lastFormSearch == null || lastFormSearch.isEmpty()) return false;
        String s = lastFormSearch.toLowerCase();
        if (s.contains("not found") || s.contains("no patient")) return false;
        return !s.contains("(model has no patient fields set)")
                && !s.contains("(no scope)") && !s.contains("scope read failed");
    }

    /**
     * Select the <b>Certificate Template</b>.
     *
     * <p>Its ng-model is {@code BirthCertificate1.Dischargeid} — the name says discharge, the list holds
     * certificate templates. (The Patient Feedback screen names its template list the same way.)</p>
     */
    public String selectTemplate() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + FORM_JS
                + " resolve(await pick('BirthCertificate1.Dischargeid')); })");
        lastTemplate = r == null ? "" : r.toString();
        waitForAngular(1500);   // the template drives the body content
        System.out.println("BirthCertificate: Template = " + lastTemplate);
        return lastTemplate;
    }

    /** Enter <b>Name of child</b>, <b>Name of Father</b> and <b>Address</b> (a textarea). */
    public String fillChildDetails(String child, String father, String address) {
        Object r = page.evaluate("(args) => {" + FORM_JS
                + " const [c, f, a] = args;"
                + " const nc=setInp('CertiDetails.nameofchild', c);"
                + " const nf=setInp('CertiDetails.childfathername', f);"
                + " const ad=setInp('CertiDetails.childaddress', a);"
                + " return 'Child='+nc+' | Father='+nf+' | Address='+ad; }",
                java.util.List.of(child, father, address));
        lastChildDetails = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("BirthCertificate: " + lastChildDetails);
        return lastChildDetails;
    }

    /** Select <b>Gender</b> ({@code CertiDetails.childgenderid}). */
    public String selectGender() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + FORM_JS
                + " resolve(await pick('CertiDetails.childgenderid')); })");
        lastGender = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("BirthCertificate: Gender = " + lastGender);
        return lastGender;
    }

    /** Enter <b>Date of Birth</b> ({@code dd/MM/yyyy}) and <b>Place of Birth</b>. */
    public String enterBirthDateAndPlace(String dob, String place) {
        Object r = page.evaluate("(args) => {" + FORM_JS
                + " const [d, p] = args;"
                + " const bd=setInp('CertiDetails.childbirthdate', d);"
                + " const pb=setInp('CertiDetails.placeofbirth', p);"
                + " return 'DOB='+bd+' | PlaceOfBirth='+pb; }", java.util.List.of(dob, place));
        lastDobPlace = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("BirthCertificate: " + lastDobPlace);
        return lastDobPlace;
    }

    /**
     * Enter the <b>form template body</b>.
     *
     * <p>It is a <b>CKEditor</b>, not a textarea — its content lives in an editor instance backed by an
     * iframe, so writing to the underlying element does nothing. This drives {@code CKEDITOR.instances}
     * directly and falls back to the contenteditable body.</p>
     */
    public String enterTemplateBody(String text) {
        Object r = page.evaluate("(t) => {" + JS
                + " if(window.CKEDITOR && CKEDITOR.instances){"
                + "   const keys=Object.keys(CKEDITOR.instances);"
                + "   if(keys.length){ for(const k of keys){ try{ CKEDITOR.instances[k].setData(t);"
                + "       CKEDITOR.instances[k].updateElement(); }catch(e){} }"
                + "     return 'CKEDITOR('+keys.length+'): '+keys.join(','); } }"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].find(vis);"
                + " if(ce){ ce.innerHTML=t; ce.dispatchEvent(new Event('input',{bubbles:true})); return 'contenteditable'; }"
                + " const fr=[...document.querySelectorAll('iframe')].filter(f=>/cke|editor/i.test(f.className||f.id||''));"
                + " for(const f of fr){ try{ const d=f.contentDocument; if(d && d.body){ d.body.innerHTML=t; return 'iframe-body'; } }catch(e){} }"
                + " return '(no-editor)'; }", text);
        lastBody = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("BirthCertificate: template body -> " + lastBody);
        return lastBody;
    }

    /** Select <b>Department</b> then <b>Doctor</b> ({@code payableid} — the doctor list). */
    public String selectDepartmentAndDoctor() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + FORM_JS
                + " const d=await pick('BirthCertificate1.departmentid'); await sleep(1000);"
                + " const dr=await pick('BirthCertificate1.payableid');"
                + " resolve('Department='+d+' | Doctor='+dr); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("Department=")) lastDepartment = t.substring(11).trim();
            else if (t.startsWith("Doctor=")) lastDoctor = t.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("BirthCertificate: " + out);
        return out;
    }

    /** Tick <b>Authenticate</b> ({@code BirthCertificate1.isfinalized}). */
    public boolean tickAuthenticate() {
        Object r = page.evaluate("() => {" + FORM_JS
                + " const cb=byNg('BirthCertificate1.isfinalized');"
                + " if(!cb) return false; if(!cb.checked) cb.click(); return cb.checked; }");
        waitForAngular(500);
        boolean ok = Boolean.TRUE.equals(r);
        System.out.println("BirthCertificate: Authenticate = " + ok);
        return ok;
    }

    /** Click <b>Save</b> ({@code IUDDischaresummary()}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__bcToasts=[]; if(window.__bcObs) window.__bcObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__bcToasts.includes(t)) window.__bcToasts.push(t); }); };"
                + " window.__bcObs=new MutationObserver(grab); window.__bcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__bcPrior=(window.__bcToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDDischaresummary/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__bcSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("BirthCertificate.save: Save button not found"); return ""; }

        try { page.locator("#__bcSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BirthCertificate.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bcSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__bcToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        Object r = page.evaluate("() => { const prior=window.__bcPrior||[];"
                + " const a=(window.__bcToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();

        // A silent Save tells us nothing on its own — capture the surrounding state so the report can say
        // WHY: did the form close (saved without a toast), is a validation dialog up, were toasts seen and
        // filtered, or did the click simply do nothing?
        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const seen=(window.__bcToasts||[]).join(' ; ');"
                    + " const prior=(window.__bcPrior||[]).join(' ; ');"
                    + " const modal=[...document.querySelectorAll('.modal,.modal-dialog,.jAlert,[role=dialog],.sweet-alert')]"
                    + "   .filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent).slice(0,120));"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error, .error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " const stillOnForm=!![...document.querySelectorAll(\"[ng-model='CertiDetails.nameofchild']\")]"
                    + "   .find(e=>e.offsetParent!==null);"
                    // Decisive check: does the ng-click handler actually EXIST on the scope? A missing
                    // function makes AngularJS swallow the click — which looks exactly like this: button
                    // clicked, nothing happens, no error surfaced to the user.
                    + " let handler='(not checked)';"
                    + " try { const el=document.querySelector(\"[ng-click*='IUDDischaresummary']\");"
                    + "   if(el){ const s=window.angular.element(el).scope();"
                    + "     handler = s ? (typeof s.IUDDischaresummary) : '(no scope)'; } else handler='(button gone)'; }"
                    + " catch(e){ handler='(scope read failed: '+e.message+')'; }"
                    + " return 'url='+location.hash+' stillOnForm='+stillOnForm+' invalidFields='+invalid"
                    + "   +' | saveHandler IUDDischaresummary='+handler"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none')"
                    + "   +(prior?' | excludedAsPrior='+prior:'')"
                    + "   +(modal.length?' | popup='+modal[0]:''); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("BirthCertificate.save: no toast — " + lastSaveDiagnostics);
        }
        return toast;
    }

    /** Populated when Save produced no toast: the surrounding state, to explain the silence. */
    public String lastSaveDiagnostics = "";

    // ---- the certificate report Save is supposed to generate -------------

    public String lastReportUrl = "", lastReportDiagnostics = "", lastReportFile = "";
    public byte[] lastReportPng = null;
    /** True when a report WAS produced but carries no content — the case this flow must fail on. */
    public boolean reportBlank = false;

    /**
     * Find the report Save is supposed to generate, and judge whether it is blank.
     *
     * <p>Looked for in the two shapes DevHIS uses: a new tab (usually an {@code .aspx} PDF) and an
     * in-page {@code iframe}/{@code embed}/{@code object}. The bytes are fetched over the session's own
     * request context, and the rendered tab is screenshotted.</p>
     *
     * <p>"Blank" is decided on evidence, not on the absence of text — a PDF viewer legitimately exposes
     * no innerText, so a text check would call every PDF blank. A report counts as blank when its bytes
     * are empty or trivially small, or when the rendered page is a single flat colour.</p>
     *
     * @param tabsBefore how many tabs existed before Save was clicked
     */
    public String captureReport(int tabsBefore, int waitMs) {
        com.kpj.pages.ReportCheck rc = com.kpj.pages.ReportCheck.after(page, tabsBefore, waitMs,
                "Nursing Station - Birth Certificate - generated report.png");
        lastReportUrl = rc.url;
        lastReportDiagnostics = rc.diagnostics;
        lastReportFile = rc.file;
        lastReportPng = rc.png;
        reportBlank = rc.blank;
        return lastReportDiagnostics;
    }

    /** A report was produced at all — a new tab or an embedded frame. */
    public boolean reportProduced() {
        return lastReportUrl != null && !lastReportUrl.isEmpty();
    }

    /**
     * After a silent Save, go back to the list and look for the certificate.
     *
     * <p>"No toast" is ambiguous on its own — it could be a saved record with no confirmation, or a save
     * that never happened. This settles it by searching the list for the child's name, so the report can
     * say which.</p>
     *
     * @return e.g. {@code FOUND in the list (saved silently)} or {@code NOT in the list (save did nothing)}
     */
    public String verifySavedInList(String mrn, String childName) {
        // Back to the list
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/Backbutton/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*back\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        if (!page.url().toLowerCase().contains("birthcertificate")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }

        searchByMrn(mrn);
        waitForAngular(1500);

        Object r = page.evaluate("(name) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const txt=norm(document.body.innerText).toLowerCase();"
                + " const rows=document.querySelectorAll('.ui-grid-row, table tbody tr').length;"
                + " const hit=txt.includes(String(name).toLowerCase());"
                + " return (hit? 'FOUND in the list (the certificate WAS saved — the screen simply shows no toast)'"
                + "            : 'NOT in the list (the save did nothing)')+' | listRows='+rows; }", childName);
        String out = r == null ? "" : r.toString();
        System.out.println("BirthCertificate: post-save check -> " + out);
        return out;
    }

    /** Diagnostics: body text plus every control the screen rendered. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)"
                + "   .map(e=>'TEXTAREA \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click'))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,20)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " const ck=[...document.querySelectorAll('.cke, .note-editor, [contenteditable=true], iframe.cke_wysiwyg_frame')].length;"
                + " return 'richTextEditors='+ck+'\\n'+sel.concat(ta).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("BirthCertificate BODY: " + lastBodyText);
        System.out.println("BirthCertificate CONTROLS:\n" + lastControls);
        return lastControls;
    }
}
