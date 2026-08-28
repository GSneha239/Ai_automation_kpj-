package com.kpj.pages.AncillaryServices_page.Ambulance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Usage</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Usage</b>
 * ({@code #/AmbulanceUsage}, a search/list grid) → <b>Add</b> → the usage form
 * ({@code #/add-AmbulanceUsage}) → select <b>Vehicle Type</b> → enter <b>MRN No.</b> + search →
 * select <b>Vehicle</b> and <b>Driver</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>This is a different controller from Ambulance Booking / Requisition</b> (verified live 2026-08-06):
 * its own {@code AmbulanceUsage.*} model, its own add route with no mode suffix, and its own save handler.
 * Hence a standalone Page Object rather than a sibling of {@link AmbulanceBooking}.</p>
 *
 * <p><b>Fields (discovered live):</b> {@code vehicletypeid}, {@code bookingdetail}, {@code vehicleid},
 * {@code driverid} (selects — note {@code driverid}, singular, unlike Booking's {@code driverid1});
 * {@code MRNo}, {@code patientname}, {@code date}, {@code referencename}, {@code kilometer} (number),
 * {@code vehiclecharge} (number), {@code locationfrom}, {@code locationto} (inputs);
 * {@code opd_ipd_other} (OPD radio) and an {@code against} "Against Booking" checkbox.
 * Buttons: {@code SearchPatientByMRNo()}, {@code fnIUDAmbulanceUsage()} (Save <i>and</i> Modify),
 * {@code resetForm()} (Back).</p>
 *
 * <p><b>Two traps this screen sets.</b> (1) Only the OPD radio carries {@code ng-model=opd_ipd_other} —
 * IPD and External have none, so the patient-type radios are matched by their label text, not by model.
 * (2) <b>Save and Modify share the identical {@code ng-click}</b>, so the Save button is matched on its
 * visible text first; matching on {@code ng-click} alone can click Modify.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class AmbulanceUsage extends BasePage {

    public AmbulanceUsage(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/AmbulanceUsage";
    public static final String ADD_ROUTE = "#/add-AmbulanceUsage";
    private static final String M = "AmbulanceUsage.";

    public String lastControls = "";
    public String lastMrn = "", lastSearchResult = "", lastPatientName = "", lastPatientType = "";
    public String lastVehicleType = "", lastVehicle = "", lastDriver = "";

    // ---- shared JS ---------------------------------------------------------

    /** {@code vis} (rendered), {@code byNg} (visible element for an ng-model), {@code setInp} (write through
     *  the ngModel controller so Angular sees it), {@code pick} (choose an option, firing change through both
     *  Angular and jQuery, polling while the list still loads). */
    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
            + "const pick=async(ng)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Usage</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("AmbulanceUsage.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ambulance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        // Exact text match first — Booking / Requisition / Usage / AmbulanceStaff are siblings in this submenu.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>norm(x.textContent).toLowerCase()==='ambulance usage')"
                + "   || links.find(x=>/ambulance\\s*usage/i.test(norm(x.textContent)));"
                + " if(!a) return false; a.id='__usgMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__usgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AmbulanceUsage.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__usgMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("AmbulanceUsage.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fngetAmbulanceUsageGrid/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("AmbulanceUsage.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    /** True on the Ambulance Usage list/search screen (not the add form, which shares the route stem). */
    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("ambulanceusage") && !url.contains("add-");
    }

    /** True on the usage form ({@code #/add-AmbulanceUsage}). */
    public boolean onUsageForm() {
        if (page.url().toLowerCase().contains("add-ambulanceusage")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)"));
    }

    /**
     * Click <b>Add</b> to open the usage form, waiting for the MRN field rather than a fixed delay.
     *
     * <p>Add is polled for: on this screen it renders <i>after</i> the grid finishes loading, so a single
     * look-up loses the race intermittently (observed failing on one run and passing on the next).</p>
     */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null)"
                    + "   .find(x=>/AddAmbulanceUsage|AddAmbulance/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__usgAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("AmbulanceUsage.clickAdd: Add button not found"); return false; }
        try { page.locator("#__usgAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AmbulanceUsage.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__usgAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("AmbulanceUsage.clickAdd: usage form did not render"); }
        waitForAngular(1200);   // let the driver master list finish loading
        return onUsageForm();
    }

    /** Diagnostics: every visible control on the form with its ng-model. Never fails the flow. */
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
        System.out.println("AmbulanceUsage controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: vehicle type ---------------------------------------------

    /** Select <b>Vehicle Type</b> ({@code AmbulanceUsage.vehicletypeid}) — it cascades into Vehicle. */
    public String selectVehicleType() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "vehicletypeid')); })");
        lastVehicleType = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("AmbulanceUsage: Vehicle Type = " + lastVehicleType);
        return lastVehicleType;
    }

    // ---- step 2: MRN + search ----------------------------------------------

    /**
     * Enter the <b>MRN No.</b> and click the search icon ({@code SearchPatientByMRNo()}).
     *
     * <p>The OPD / IPD / External radio scopes which register is searched, and on this screen only the OPD
     * radio carries an {@code ng-model} — so the radios are located by their label text and each mode is
     * tried in turn until one resolves a patient (judged on {@code patientname} being filled).</p>
     */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;
        String matched = "";
        for (String mode : new String[]{"OPD", "IPD", "External"}) {
            page.evaluate("(args) => {" + JS
                    + " const [m, mode] = args;"
                    + " const radios=[...document.querySelectorAll('input[type=radio]')].filter(vis);"
                    + " const r=radios.find(x=>{ const g=x.closest('label,div,td,span');"
                    + "   return g && new RegExp('^\\\\s*'+mode+'\\\\s*$','i').test(norm(g.textContent)); })"
                    + "   || radios.find(x=>new RegExp(mode,'i').test(norm((x.closest('label,div,td,span')||{}).textContent||'')));"
                    + " if(r && !r.checked) r.click();"
                    + " setInp('" + M + "MRNo', m);"
                    + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                    + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.id='__usgMrnSearch'; }", java.util.List.of(mrn, mode));
            try { page.locator("#__usgMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AmbulanceUsage.searchByMrn[" + mode + "]: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__usgMrnSearch'); if(e) e.removeAttribute('id'); }");

            try {
                page.waitForFunction("() => { const e=[...document.querySelectorAll(\"[ng-model='" + M + "patientname']\")].find(x=>x.offsetParent!==null);"
                        + " return e && e.value && e.value.trim().length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                matched = mode;
                System.out.println("AmbulanceUsage: MRN resolved under " + mode);
                break;
            } catch (Exception ignore) {
                System.out.println("AmbulanceUsage: MRN " + mrn + " not found under " + mode);
            }
        }
        lastPatientType = matched;
        waitForAngular(600);

        Object r = page.evaluate("() => {" + JS
                + " const val=ng=>{ const e=byNg(ng); return e?norm(e.value):''; };"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'patient='+val('" + M + "patientname')+(msg?' | msg='+msg:''); }");
        lastSearchResult = (matched.isEmpty() ? "" : matched + ": ") + (r == null ? "" : r.toString());
        Object pn = page.evaluate("() => {" + JS + " const e=byNg('" + M + "patientname'); return e?norm(e.value):''; }");
        lastPatientName = pn == null ? "" : pn.toString();
        System.out.println("AmbulanceUsage: search MRN " + mrn + " -> " + lastSearchResult);
        return lastSearchResult;
    }

    /** True when the MRN resolved to a patient (the screen filled Patient Name). */
    public boolean patientLoaded() {
        return lastPatientName != null && !lastPatientName.trim().isEmpty();
    }

    // ---- step 3: vehicle + driver ------------------------------------------

    /**
     * Select <b>Vehicle</b> ({@code AmbulanceUsage.vehicleid}) then <b>Driver</b>
     * ({@code AmbulanceUsage.driverid}) — Vehicle is filtered by the Vehicle Type chosen earlier, so it is
     * given a moment to load first.
     */
    public String selectVehicleAndDriver() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const v=await pick('" + M + "vehicleid'); await sleep(500);"
                + " const d=await pick('" + M + "driverid');"
                + " resolve('Vehicle='+v+' | Driver='+d); })");
        String out = r == null ? "" : r.toString();
        for (String part : out.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("Vehicle=")) lastVehicle = p.substring(8).trim();
            else if (p.startsWith("Driver=")) lastDriver = p.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("AmbulanceUsage: " + out);
        return out;
    }

    /** True when both Vehicle and Driver took a real value. */
    public boolean vehicleAndDriverSelected() {
        return isReal(lastVehicle) && isReal(lastDriver);
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }

    // ---- step 4: save + toast ----------------------------------------------

    /**
     * Click <b>Save</b> and return the toast ("" if none appeared).
     *
     * <p>Save and <b>Modify</b> carry the identical {@code ng-click="fnIUDAmbulanceUsage()"}, so the button
     * is matched on its visible text <i>first</i> — matching on the handler alone can hit Modify.</p>
     */
    public String saveAndGetToast() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__usgToasts=[]; if(window.__usgObs) window.__usgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__usgToasts.includes(t)) window.__usgToasts.push(t); }); };"
                + " window.__usgObs=new MutationObserver(grab); window.__usgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const btns=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                // exact text 'Save' wins; Modify shares the same ng-click and must not be clicked
                + " const b=btns.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || btns.find(x=>/fnIUDAmbulanceUsage/i.test(x.getAttribute('ng-click')||'') && !/modify/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__usgSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("AmbulanceUsage.save: Save button not found"); return ""; }

        try { page.locator("#__usgSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AmbulanceUsage.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__usgSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);

        acceptSaveDialog();   // some DevHIS screens gate the save behind a "Do You Want To Save" confirm
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__usgToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__usgToasts||[]).includes(t)) (window.__usgToasts=window.__usgToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__usgToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
