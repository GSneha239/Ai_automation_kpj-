package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Mortuary</b> ({@code #/MortuaryList}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Mortuary</b> → <b>Add</b> → enter <b>MRN</b> + search → enter the
 * <b>mortuary cabin no</b> → select <b>Relationship</b> → enter <b>In Date Time</b> and <b>Handover Date
 * Time</b> → enter <b>Handover To</b> → select the <b>template</b> → <b>Add</b> (the row) → <b>Save</b> →
 * toast.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load) — several Nursing Station menu
 * links render an empty shell.</p>
 *
 * <p><b>Controls are scoped and label-driven.</b> These DevHIS screens routinely repeat an ng-model across
 * a search panel and a form, so fields are resolved inside the panel that owns Save where one exists, and
 * otherwise by label. {@link #describeControls()} prints the real ng-models.</p>
 *
 * <p><b>Patient attachment is verified against the model</b>, not the input box — a mortuary record needs
 * a deceased patient, and these screens accept an MRN in the field while attaching nobody.</p>
 */
public class Mortuary extends BasePage {

    public Mortuary(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/MortuaryList";

    public String lastControls = "", lastBodyText = "";
    public String lastFormSearch = "", lastCabin = "", lastRelationship = "";
    public String lastInDateTime = "", lastHandoverDateTime = "", lastHandoverTo = "";
    public String lastTemplate = "", lastAddRow = "", lastSaveDiagnostics = "";

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
            // Scope to the panel owning Save, so a duplicated search panel cannot be filled by mistake.
            + "const formScope=()=>{ const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
            + "  const save=btns.find(b=>/^\\s*save\\s*$/i.test(norm(b.textContent||b.value)))"
            + "    || btns.find(b=>/save|IUD/i.test(b.getAttribute('ng-click')||''));"
            + "  if(!save) return document;"
            + "  let n=save.parentElement;"
            + "  while(n && n!==document.body){"
            + "    if(n.querySelectorAll('input').length>2) return n;"
            + "    n=n.parentElement; }"
            + "  return document; };"
            + "const inScope=sel=>[...formScope().querySelectorAll(sel)].filter(vis);"
            // Includes TEXTAREA: Handover To and Notes are textareas on this screen, so an input-only
            // search silently misses them.
            + "const findInput=rx=>{ const re=new RegExp(rx,'i');"
            + "  const ins=inScope('input[type=text], input:not([type]), input[type=number], textarea')"
            + "    .filter(e=>!/colFilter|pagination/i.test(attrs(e)));"
            + "  return ins.find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };"
            + "const findSelect=(rx,avoid)=>{ const re=new RegExp(rx,'i'); const bad=avoid?new RegExp(avoid,'i'):null;"
            + "  const sels=inScope('select').filter(e=>!/pagination/i.test(attrs(e)));"
            + "  return sels.find(e=>re.test(attrs(e)+' '+labelOf(e)) && (!bad || !bad.test(attrs(e)+' '+labelOf(e)))) || null; };";

    /**
     * Message elements.
     *
     * <p>This screen does NOT use toastr for its result. A refusal such as "Mortuary already added for this
     * patient (active record exists).!" appears in a <b>"KPJ Portal"</b> banner pinned to the bottom of the
     * window — a different element entirely. Looking only for {@code .toast-message} reports "no toast" for
     * a screen that answered perfectly clearly, which is exactly how a duplicate refusal got mistaken for a
     * broken Save.</p>
     */
    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            // The KPJ Portal banner: match on its text, since its markup carries no toast class.
            + "  if(!n.length){ n=[...document.querySelectorAll('div,span,p,section,aside')].filter(_tv)"
            + "      .filter(e=>/kpj portal|already added|active record exists|successfully/i"
            + "        .test((e.textContent||'')))"
            + "      .filter(e=>(e.textContent||'').length<400)"
            + "      .filter(e=>![...e.children].some(c=>/kpj portal|already added|active record exists"
            + "|successfully/i.test(c.textContent||''))); }"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to Mortuary, escalating menu click → hash route → full page load. */
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
                + "   .find(x=>/^mortuary$/i.test(norm(x.textContent))"
                + "        || /#\\/MortuaryList/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("Mortuary.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("Mortuary.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + LIST_ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("Mortuary.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("mortuary")) return false;
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
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,18)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(ta).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,200)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("Mortuary CONTROLS:\n" + lastControls);
        return lastControls;
    }

    /** Click <b>Add</b> on the list to open the mortuary form. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/^\\s*(add|new)\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/^(add|new)/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return false; b.id='__mtAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Mortuary.clickAdd: Add control not found"); return false; }
        try { page.locator("#__mtAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Mortuary.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mtAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('input[type=text]').length > 2",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("Mortuary.clickAdd: form did not render"); }
        waitForAngular(1500);
        return true;
    }

    // ---- MRN + search ------------------------------------------------------

    /** Enter the <b>MRN</b> and search; reports whether a patient actually attached to the model. */
    public String searchByMrn(String mrn) {
        page.evaluate("(m) => {" + JS
                + " const e=findInput('mrno|mr\\\\s*no|prn');"
                + " if(e) setEl(e, m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__mtSearch'; }", mrn);
        try { page.locator("#__mtSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Mortuary.searchByMrn: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mtSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);

        // Read the Mortuary model itself and require its MRNo to MATCH what we searched.
        //
        // An earlier version scanned the scope for "any object with an MRNo or FirstName key" and latched
        // onto the app's LABEL DICTIONARY (AccLedgerName='Acc Ledger Name', Age='Age', ...), reporting a
        // patient as attached when none was. Matching on the value, not the key, is what makes this real.
        Object r = page.evaluate("(wantMrn) => {" + JS + TOAST_ELS
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " let patient='(no scope)';"
                + " try { const e=findInput('mrno|mr\\\\s*no|prn'); const s=window.angular.element(e).scope();"
                + "   const o=s && s.Mortuary;"
                + "   if(!o) patient='(no Mortuary model)';"
                + "   else if(String(o.MRNo||'').trim() !== String(wantMrn).trim())"
                + "     patient='(model MRNo=\"'+String(o.MRNo||'').trim()+'\" does not match the MRN searched)';"
                + "   else { const keys=Object.keys(o).filter(k=>/^(firstname|patientname|lastname|age|gender|patientid|id)$/i.test(k)"
                + "        && o[k]!==null && o[k]!=='' && o[k]!==0 && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "     patient = keys.length? 'MRNo='+o.MRNo+', '+keys.slice(0,6).map(k=>k+'='+String(o[k]).slice(0,24)).join(', ')"
                + "                          : '(MRNo set but no patient detail loaded)'; } }"
                + " catch(err) { patient='(scope read failed: '+err.message+')'; }"
                + " return 'patient: '+patient+(msg?' | msg='+msg:''); }", mrn);
        lastFormSearch = r == null ? "" : r.toString();
        System.out.println("Mortuary: search MRN " + mrn + " -> " + lastFormSearch);
        return lastFormSearch;
    }

    public boolean patientAttached() {
        if (lastFormSearch == null || lastFormSearch.isEmpty()) return false;
        String s = lastFormSearch.toLowerCase();
        if (s.contains("not found") || s.contains("no patient")) return false;
        // Anything in parentheses is a failure reason from the reader above.
        return s.contains("patient: mrno=")
                && !s.contains("does not match") && !s.contains("no patient detail loaded");
    }

    // ---- form fields -------------------------------------------------------

    /** Enter the <b>mortuary cabin no</b>. */
    public String enterCabinNo(String cabin) {
        Object r = page.evaluate("(v) => {" + JS
                + " const e=findInput('cabin');"
                + " if(!e) return '(no-field)';"
                + " return setEl(e, v)+' ['+(e.getAttribute('ng-model')||'?')+']'; }", cabin);
        lastCabin = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("Mortuary: Cabin No = " + lastCabin);
        return lastCabin;
    }

    /** Select <b>Relationship</b>. */
    public String selectRelationship() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const e=findSelect('relation', null);"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastRelationship = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("Mortuary: Relationship = " + lastRelationship);
        return lastRelationship;
    }

    /** Enter <b>In Date Time</b> and <b>Handover Date Time</b>. */
    public String enterDateTimes(String inDateTime, String handoverDateTime) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [inDt, hoDt] = args;"
                + " const a=findInput('indate|in\\\\s*date');"
                + " const b=findInput('handover.*date|hodate|date.*handover');"
                + " const ra=a? setEl(a, inDt)+' ['+(a.getAttribute('ng-model')||'?')+']' : '(no-field)';"
                + " const rb=b? setEl(b, hoDt)+' ['+(b.getAttribute('ng-model')||'?')+']' : '(no-field)';"
                + " return 'InDateTime='+ra+' | HandoverDateTime='+rb; }",
                java.util.List.of(inDateTime, handoverDateTime));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("InDateTime=")) lastInDateTime = t.substring(11).trim();
            else if (t.startsWith("HandoverDateTime=")) lastHandoverDateTime = t.substring(17).trim();
        }
        waitForAngular(400);
        System.out.println("Mortuary: " + out);
        return out;
    }

    /** Enter <b>Handover To</b>. */
    public String enterHandoverTo(String who) {
        Object r = page.evaluate("(v) => {" + JS
                + " const e=findInput('handoverto|handover\\\\s*to|hoto');"
                + " if(!e) return '(no-field)';"
                + " return setEl(e, v)+' ['+(e.getAttribute('ng-model')||'?')+']'; }", who);
        lastHandoverTo = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("Mortuary: Handover To = " + lastHandoverTo);
        return lastHandoverTo;
    }

    public String lastPolice = "", lastFuneral = "", lastBody = "";

    /**
     * Police Department block: <b>Name</b>, <b>Police ID No</b> and <b>Vehicle No</b>.
     *
     * <p>Addressed by their exact models ({@code Mortuary.policename}, {@code .policeIDno},
     * {@code .policevehicleno}) rather than by hunting for a "Police Department" heading — this form has
     * a matching Funeral Service block with identically-shaped fields right below it, and a
     * heading-scoped lookup would fill one block twice.</p>
     */
    public String enterPoliceDetails(String name, String idNo, String vehicleNo) {
        lastPolice = setThree("Mortuary.policename", name,
                              "Mortuary.policeIDno", idNo,
                              "Mortuary.policevehicleno", vehicleNo,
                              "Name", "PoliceIDNo", "VehicleNo");
        System.out.println("Mortuary: police -> " + lastPolice);
        return lastPolice;
    }

    /** Funeral Service Company block: <b>Name</b>, <b>IC No</b> and <b>Vehicle No</b>. */
    public String enterFuneralDetails(String name, String icNo, String vehicleNo) {
        lastFuneral = setThree("Mortuary.funeralname", name,
                               "Mortuary.funeralICno", icNo,
                               "Mortuary.funeralvehicleno", vehicleNo,
                               "Name", "ICNo", "VehicleNo");
        System.out.println("Mortuary: funeral -> " + lastFuneral);
        return lastFuneral;
    }

    private String setThree(String ng1, String v1, String ng2, String v2, String ng3, String v3,
                            String l1, String l2, String l3) {
        Object r = page.evaluate("(a) => {" + JS
                + " const put=(ng,v)=>{ const e=[...document.querySelectorAll("
                + "     \"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")]"
                + "   .find(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length);"
                + "   return e? setEl(e,v) : '(no-field)'; };"
                + " return a.l1+'='+put(a.ng1,a.v1)+' | '+a.l2+'='+put(a.ng2,a.v2)"
                + "   +' | '+a.l3+'='+put(a.ng3,a.v3); }",
                java.util.Map.of("ng1", ng1, "v1", v1, "ng2", ng2, "v2", v2, "ng3", ng3, "v3", v3,
                        "l1", l1, "l2", l2, "l3", l3));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    public static boolean allThreeSet(String result) {
        return result != null && !result.contains("(no-field)") && !result.contains("=|") && !result.endsWith("=");
    }

    /**
     * Fill the <b>template body</b> ({@code Mortuary.notes}).
     *
     * <p>Set after the template is chosen: selecting a template rewrites the body with that template's
     * text, so filling it first is silently discarded.</p>
     */
    public String fillTemplateBody(String text) {
        Object r = page.evaluate("(v) => {" + JS
                + " const e=[...document.querySelectorAll(\"textarea[ng-model='Mortuary.notes']\")]"
                + "   .find(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length);"
                + " if(!e) return '(no-field)';"
                + " const before=(e.value||'').replace(/\\s+/g,' ').trim().slice(0,40);"
                + " setEl(e, v);"
                + " return 'body=\"'+(e.value||'').replace(/\\s+/g,' ').trim().slice(0,60)+'\"'"
                + "   +(before? ' (template text it replaced: \"'+before+'...\")' : ''); }", text);
        lastBody = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("Mortuary: template body -> " + lastBody);
        return lastBody;
    }

    /** Select the <b>template</b> (often a discharge-shaped model on these DevHIS screens). */
    public String selectTemplate() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " let e=findSelect('template|dischargeid', 'relation');"
                + " if(!e){ const sels=inScope('select').filter(s=>!/pagination/i.test(attrs(s)));"
                + "   e=sels.find(s=>s.options.length>1 && !/relation/i.test(attrs(s)+' '+labelOf(s))); }"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve(await pickEl(e)+' '+which); })");
        lastTemplate = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("Mortuary: Template = " + lastTemplate);
        return lastTemplate;
    }

    /** Click the form's <b>Add</b> (adds the row) and report the grid delta. */
    public String clickAddRow() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/AddTemplateList|addrow|adddetail/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__mtAddRow'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("Mortuary.clickAddRow: Add not found"); return "(no Add button)"; }
        try { page.locator("#__mtAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Mortuary.clickAddRow: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mtAddRow'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message')].map(x=>norm(x.textContent)).join(' | ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        lastAddRow = r == null ? "" : r.toString();
        System.out.println("Mortuary: Add row -> " + lastAddRow);
        return lastAddRow;
    }

    /**
     * MRNs that already have a mortuary record — read from the LIST screen, before Add is clicked.
     *
     * <p>Save is silent for a patient that already has a record, so knowing these up front is what lets the
     * run pick a patient the screen will actually accept instead of reporting a duplicate refusal as a
     * broken Save.</p>
     */
    public java.util.List<String> collectListMrns() {
        java.util.List<String> out = new java.util.ArrayList<>();
        // Go to the LIST first. The Mortuary menu entry can land straight on the entry form, and reading
        // for rows there finds none — which reads as "no patient has a record" and sends the run at a
        // patient who does.
        try {
            if (!page.url().toLowerCase().contains("mortuarylist")) {
                page.evaluate("() => { window.location.hash = '#/MortuaryList'; }");
                waitForAngular(3000);
            }
        } catch (Exception ignore) { }

        // Wait for the grid BEFORE touching it.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')]"
                    + " .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length).length > 0",
                    null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            System.out.println("Mortuary.collectListMrns: no list rows within 20s");
        }
        waitForAngular(1000);

        // Then show EVERY record: the list defaults to 20 per page over 23 records, so the default page
        // misses the tail — and a patient whose record sits there looks free until Save refuses them.
        try {
            page.evaluate("() => { const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const size=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                    + " if(size){ size.selectedIndex=size.options.length-1;"
                    + "   size.dispatchEvent(new Event('change',{bubbles:true})); } }");
            waitForAngular(2000);
        } catch (Exception ignore) { }
        // Wait for the grid: read too early and this returns nothing, which reads as "no patient has a
        // record" and sends the run at a patient that already does.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')]"
                    + " .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length).length > 0",
                    null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("Mortuary.collectListMrns: no list rows within 15s");
        }
        waitForAngular(800);
        try {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const text=[...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).join(' ');"
                    + " return [...new Set((text.match(/\\b100\\d{6}\\b/g)||[]))]; }");
            if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(o.toString());
        } catch (Exception ignore) { }
        System.out.println("Mortuary: MRNs already in the list -> " + out);
        return out;
    }

    /**
     * Ask the screen's own patient lookup for MRNs ({@code openPopupScreen()} &rarr; {@code SearchPatient(0)}).
     *
     * <p>Same mechanism as the Birth Certificate screen: rather than guessing numbers, take patients this
     * environment actually has.</p>
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
            System.out.println("Mortuary.discoverMrns: " + e.getMessage().split("\n")[0]);
        }
        System.out.println("Mortuary.discoverMrns -> " + found);
        return found;
    }

    public String lastRedirect = "", lastListSearch = "";

    /**
     * Wait for Save to take the screen back to the mortuary list.
     *
     * <p>Save is expected to redirect out of {@code #/add-Mortuary} on its own. If it has not after the
     * wait, Back is used so the list can still be inspected — and the fact that no redirect happened is
     * reported, since that is itself a signal about whether Save did anything.</p>
     */
    public String waitForRedirectToList(int timeoutMs) {
        // Must land on the mortuary LIST specifically. "Anything other than #/add-Mortuary" is not good
        // enough — a run that drifted onto an unrelated screen would pass that.
        boolean redirected = false;
        try {
            page.waitForFunction("() => /mortuarylist/i.test(location.hash)", null,
                    new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            redirected = true;
        } catch (Exception ignore) { }
        waitForAngular(1500);

        if (!redirected) {
            try {
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
            } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        lastRedirect = redirected
                ? "Save redirected to " + page.url()
                : "Save did NOT redirect within " + timeoutMs / 1000 + "s; went Back manually (now " + page.url() + ")";
        System.out.println("Mortuary: " + lastRedirect);
        return lastRedirect;
    }

    /**
     * On the list screen, put the MRN in the list's own search and run it.
     *
     * <p>The list opens filtered by a date range, so a record outside that window is simply not displayed.
     * Searching by MRN is how the screen is meant to surface a specific patient — without this, scanning
     * the default page can report a saved record as missing.</p>
     */
    /**
     * How many rows the list already holds for this patient.
     *
     * <p>Taken on the list screen BEFORE the record is created and again after Save. Comparing the two is
     * the only way to show that <i>this</i> run added a row: the patient may already have mortuary records
     * from earlier runs, and simply finding a row afterwards would credit Save with someone else's work.</p>
     */
    public int countRowsForMrn(String mrn) {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')]"
                    + " .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length).length > 0",
                    null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        searchListByMrn(mrn);
        Object n = page.evaluate("(m) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " return [...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t && m && t.includes(m)).length; }", mrn);
        int c = n instanceof Number ? ((Number) n).intValue() : 0;
        System.out.println("Mortuary: rows for MRN " + mrn + " = " + c);
        return c;
    }

    /** Leave the entry form and return to {@code #/MortuaryList}. */
    public void backToList() {
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                    + " if(b) b.click(); else window.location.hash='#/MortuaryList'; }");
            waitForAngular(2500);
        } catch (Exception ignore) { }
    }

    /** Dump the list screen's own filters — the mortuary list is #/MortuaryList and has its own controls. */
    public String describeListControls() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const lab=e=>{ const g=e.closest('.form-group,.row,td,div');"
                + "   return g? norm(g.textContent).slice(0,45):''; };"
                + " const out=[];"
                + " for(const e of document.querySelectorAll('input,select,button')){"
                + "   if(!vis(e)) continue;"
                + "   const c=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYears/.test(c)) continue;"
                + "   if(e.tagName==='BUTTON') out.push('BTN \"'+norm(e.textContent).slice(0,25)+'\" [ng-click='+c+']');"
                + "   else if(e.tagName==='SELECT') out.push('SELECT [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + "   else out.push('INPUT \"'+(e.placeholder||lab(e)).slice(0,35)+'\" [ng='"
                + "     +(e.getAttribute('ng-model')||'?')+'] type='+e.type); }"
                + " return 'at '+location.hash+': '+[...new Set(out)].slice(0,20).join(' | '); }");
        String s = r == null ? "" : r.toString();
        System.out.println("Mortuary: list controls -> " + s);
        return s;
    }

    public String searchListByMrn(String mrn) {
        Object r = page.evaluate("(m) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const lab=e=>{ const g=e.closest('.form-group,.row,td,div');"
                + "   return g? norm(g.textContent).slice(0,60):''; };"
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden');"
                // #/MortuaryList has no dedicated MRN field — entering the MRN means the grid's own column
                // filter (colFilter.term), which is the first column, MRN. Dedicated boxes are still
                // preferred first in case another list screen has one.
                + " const el=boxes.find(e=>/mrno|prn/i.test(e.getAttribute('ng-model')||''))"
                + "   || boxes.find(e=>/mrn|prn/i.test(e.placeholder||''))"
                + "   || boxes.find(e=>/\\bmrn\\b/i.test(lab(e)))"
                + "   || boxes.find(e=>/colFilter\\.term/i.test(e.getAttribute('ng-model')||''));"
                + " if(!el) return '(no MRN box and no column filter on the list)';"
                + " el.focus(); el.value=m;"
                + " try{ const c=angular.element(el).controller('ngModel');"
                + "      if(c){ c.$setViewValue(m); c.$render(); } }catch(e){}"
                + " el.dispatchEvent(new Event('input',{bubbles:true}));"
                + " el.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')]"
                + "   .filter(vis).find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /search/i.test(x.getAttribute('ng-click')||''));"
                // A column filter needs no Search button — typing filters the grid live.
                + " if(!btn) return 'searched MRN '+el.value+' via the column filter [ng='"
                + "   +(el.getAttribute('ng-model')||'?')+']';"
                + " btn.click();"
                + " return 'searched MRN '+el.value+' [ng='+(el.getAttribute('ng-model')||'?')+']'; }", mrn);
        lastListSearch = r == null ? "" : r.toString();
        waitForAngular(3000);
        System.out.println("Mortuary: list search -> " + lastListSearch);
        return lastListSearch;
    }

    public String lastSavedRow = "";

    /**
     * After Save, look for the record as a new row in the mortuary <b>list</b>.
     *
     * <p>Used instead of a success toast, because this screen shows none. "Saved" is judged by the row
     * existing in the list — the form's own grid holds the line that Add appended, which is there whether
     * or not Save wrote anything, so checking that would pass regardless.</p>
     *
     * @param mrn      the patient whose row is expected
     * @param expected other values that should appear in it, when the grid shows them
     */
    public boolean savedRowInList(String mrn, java.util.List<String> expected) {
        try {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')]"
                        + " .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length).length > 0",
                        null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            waitForAngular(800);

            // Widen the grid before concluding anything: take the largest page size the list offers.
            // Reading page 1 of a date-ordered grid is how a saved record gets reported as missing.
            try {
                page.evaluate("() => { const norm=s=>(s||'').replace(/\s+/g,' ').trim();"
                        + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                        + " const size=[...document.querySelectorAll('select')].filter(vis)"
                        + "   .find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                        + " if(size){ size.selectedIndex=size.options.length-1;"
                        + "   size.dispatchEvent(new Event('change',{bubbles:true})); } }");
                waitForAngular(2000);
            } catch (Exception ignore) { }

            Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const rows=[...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const mine=rows.filter(t=>a.mrn && t.includes(a.mrn));"
                    + " if(!mine.length) return 'NO row for MRN '+a.mrn+' in the list ('+rows.length+' rows shown)'"
                    + "   +(rows.length? '. Sample: '+rows.slice(0,3).map(t=>'\"'+t.slice(0,70)+'\"').join(' / ') : '');"
                    + " const best=mine[mine.length-1];"
                    + " const hits=a.want.filter(w=>w && best.includes(w));"
                    + " return 'ROW FOUND for MRN '+a.mrn+' ('+mine.length+' row(s) for this patient)'"
                    + "   +' | carries '+hits.length+'/'+a.want.length+' of the entered values'"
                    + "   +(hits.length? ' ['+hits.join(', ')+']':'')"
                    + "   +' | row: \"'+best.slice(0,140)+'\"'; }",
                    java.util.Map.of("mrn", mrn, "want", expected));
            lastSavedRow = r == null ? "" : r.toString();
        } catch (Exception e) {
            lastSavedRow = "(could not read the list: " + e.getMessage().split("\n")[0] + ")";
        }
        System.out.println("Mortuary: saved row -> " + lastSavedRow);
        return lastSavedRow.startsWith("ROW FOUND");
    }

    public String lastDuplicateCheck = "";

    /**
     * Does the mortuary list already hold a record for this patient?
     *
     * <p>Asked when Save is silent. These DevHIS screens refuse a duplicate without saying so, and the
     * difference between "Save is broken" and "this patient already has a mortuary record" matters — the
     * Birth Certificate screen looked broken for exactly that kind of reason.</p>
     *
     * <p>Navigates to the list, so call it after the save attempt.</p>
     */
    public boolean patientAlreadyInList(String mrn) {
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                    + " if(b) b.click(); }");
            waitForAngular(2500);
            Object r = page.evaluate("(m) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const rows=[...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const hit=rows.find(t=>m && t.includes(m));"
                    + " return hit? 'ALREADY LISTED: '+hit.slice(0,110)"
                    + "   : 'not listed for this patient ('+rows.length+' rows)'; }", mrn);
            lastDuplicateCheck = r == null ? "" : r.toString();
        } catch (Exception e) {
            lastDuplicateCheck = "(could not read the list: " + e.getMessage().split("\n")[0] + ")";
        }
        System.out.println("Mortuary: duplicate check -> " + lastDuplicateCheck);
        return lastDuplicateCheck.startsWith("ALREADY LISTED");
    }

    public String lastRowData = "";

    /**
     * Confirm the added row actually carries the values that were entered.
     *
     * <p>A row count going up only proves a row appeared. This looks for the entered values inside it, so
     * an empty or wrongly-populated row cannot pass as "row added with the data".</p>
     *
     * @param expected values that should appear in the row, in any order
     */
    public boolean rowHasData(java.util.List<String> expected) {
        Object r = page.evaluate("(want) => {" + JS
                + " const rows=[...document.querySelectorAll('table tbody tr, .ui-grid-row')]"
                + "   .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                + " const hits=want.filter(w=>w && rows.some(t=>t.includes(w)));"
                + " const miss=want.filter(w=>w && !rows.some(t=>t.includes(w)));"
                + " const best=rows.find(t=>hits.length && hits.every(h=>t.includes(h)))"
                + "   || rows.find(t=>hits.some(h=>t.includes(h))) || '';"
                + " let out='matched '+hits.length+'/'+want.length+' ['+hits.join(', ')+']'"
                + "   +(miss.length? ' | missing: '+miss.join(', ') : '');"
                + " out += best? ' | row: \"'+best.slice(0,140)+'\"'"
                // With nothing matched, show what the grid DOES hold — otherwise the failure says only
                // that the values are absent, not what the row actually contains.
                + "   : ' | grid holds '+rows.length+' row(s): '"
                + "     +rows.slice(-4).map(t=>'\"'+t.slice(0,90)+'\"').join(' / ');"
                + " return out; }", expected);
        lastRowData = r == null ? "" : r.toString();
        System.out.println("Mortuary: row data -> " + lastRowData);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("matched (\\d+)/(\\d+)").matcher(lastRowData);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
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
                + " window.__mtToasts=[]; if(window.__mtObs) window.__mtObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__mtToasts.includes(t)) window.__mtToasts.push(t); }); };"
                + " window.__mtObs=new MutationObserver(grab); window.__mtObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__mtPrior=(window.__mtToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/IUDMortuary|save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__mtSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("Mortuary.save: Save button not found"); return ""; }

        try { page.locator("#__mtSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Mortuary.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mtSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__mtToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__mtPrior||[];"
                + " const a=(window.__mtToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            // Last resort: read the page text. This screen answers in a "KPJ Portal" banner that carries no
            // toast class, so a class-based observer never sees it — which is how a clear refusal
            // ("Mortuary already added for this patient (active record exists).!") was mistaken for silence.
            Object banner = page.evaluate("() => {"
                    + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                    + " const m=t.match(/[^.!]*\\b(already added|active record exists|saved successfully"
                    + "|added successfully|updated successfully)\\b[^.!]*[.!]?/i);"
                    + " return m? m[0].trim().slice(0,160) : ''; }");
            String fromPage = banner == null ? "" : banner.toString().trim();
            if (!fromPage.isEmpty()) {
                toast = fromPage;
                System.out.println("Mortuary.save: message read from the page banner -> " + toast);
            }
        }

        if (toast.isEmpty()) {
            // POLL for the banner instead of reading once. The screen does answer a duplicate with
            // "Mortuary already added for this patient (active record exists).!", but it is not a toastr
            // element and it can be gone before a single post-hoc read — which is how a clear refusal ends
            // up recorded as silence.
            long until = System.currentTimeMillis() + 12000;
            while (System.currentTimeMillis() < until && toast.isEmpty()) {
                try {
                    Object hit = page.evaluate("() => {"
                            + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                            + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                            + " const want=/already added|active record exists|saved successfully"
                            + "|added successfully|updated successfully|please (enter|select)/i;"
                            + " const els=[...document.querySelectorAll('div,span,p,section,aside,td')]"
                            + "   .filter(vis).filter(e=>want.test(e.textContent||''))"
                            + "   .filter(e=>(e.textContent||'').length<300)"
                            + "   .filter(e=>![...e.children].some(c=>want.test(c.textContent||'')));"
                            + " if(els.length) return norm(els[0].textContent).slice(0,160);"
                            + " const t=norm(document.body?document.body.innerText:'');"
                            + " const m=t.match(/[^.!]*(already added|active record exists)[^.!]*[.!]?/i);"
                            + " return m? m[0].trim().slice(0,160) : ''; }");
                    String s = hit == null ? "" : hit.toString().trim();
                    if (!s.isEmpty()) { toast = s; break; }
                } catch (Exception ignore) { }
                page.waitForTimeout(400);
            }
            if (!toast.isEmpty()) {
                System.out.println("Mortuary.save: banner captured by polling -> " + toast);
            }
        }

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__mtToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("Mortuary.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }

    /**
     * After a silent Save, go back to the list and look for the record.
     *
     * <p>"No toast" alone cannot distinguish a saved record with no confirmation from a save that never
     * happened — and on the Birth Certificate screen that distinction was the whole finding.</p>
     *
     * @param needle text expected in the row (e.g. the cabin no)
     */
    public String verifySavedInList(String needle) {
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/closeForm|fnGoBack|Backbutton/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*back\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        if (page.url().toLowerCase().contains("add-mortuary")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }

        Object r = page.evaluate("(n) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('.ui-grid-row, table tbody tr').length;"
                + " const hit=norm(document.body.innerText).toLowerCase().includes(String(n).toLowerCase());"
                + " return (hit? 'FOUND in the list (saved — the screen just shows no toast)'"
                + "            : 'NOT in the list (the save did nothing)')+' | listRows='+rows; }", needle);
        String out = r == null ? "" : r.toString();
        System.out.println("Mortuary: post-save check -> " + out);
        return out;
    }
}
