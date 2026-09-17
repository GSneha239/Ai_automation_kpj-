package com.kpj.pages.AncillaryServices_page.Ambulance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Booking</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Booking</b> ({@code #/AmbulanceBooking},
 * a search/list grid) → <b>Add</b> ({@code AddAmbulanceRequisition()}) → the booking form
 * ({@code #/add-AmbulanceRequisition/2}) → select <b>Vehicle Type</b> → enter <b>MRN No.</b> + search →
 * select <b>Vehicle</b>, <b>Driver1</b>, <b>Doctor</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06, all under the {@code AmbulanceRequisition} model):</b>
 * {@code vehicletypeid}, {@code vehicleid}, {@code driverid1}, {@code driverid2}, {@code doctorid},
 * {@code attendentid}, {@code nurseid} (selects); {@code MRNo}, {@code patientname}, {@code callingno},
 * {@code relativename}, {@code requisitionno}, {@code requisitiondatetime}, {@code locationfrom},
 * {@code locationto}, {@code refname}, {@code bookingno}, {@code bookingdatetime}, {@code kilometer},
 * {@code vehiclecharge} (inputs); {@code OPDIPD} (OPD/IPD/External radios). Buttons:
 * {@code SearchPatientByMRNo()}, {@code fnIUDAmbulanceRequisition()} (Save), {@code CancelForm()} (Back).</p>
 *
 * <p><b>Login note.</b> This screen only exists when the session has a menu, and DevHIS only builds the menu
 * for an <i>outpatient</i> cash counter — logging in with the first counter offered (a ward counter) lands on
 * a menu-less "Transfer / Welcome" shell. Log in with {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 *
 * <p>DevHIS gotchas handled here: {@code ng-options} selects are set by {@code selectedIndex} + a native
 * {@code change} (never the encoded "number:1" value); Vehicle cascades from Vehicle Type so each list is
 * polled; the MRN is written through the ngModel controller and the search icon really clicked so its
 * {@code ng-click} runs; toasts fade in ~1s so a MutationObserver is installed before Save.</p>
 */
public class AmbulanceBooking extends BasePage {

    public AmbulanceBooking(Page page) { super(page); }

    /** The list/search screen. The form lives behind its Add button. Overridden by sibling screens
     *  (Ambulance Requisition) that share this controller. */
    protected String listRoute() { return "#/AmbulanceBooking"; }

    /** Menu link text to click under Ancillary Services &gt; Ambulance. */
    protected String menuLabel() { return "Ambulance Booking"; }

    /** Lower-case fragment identifying this screen's list route in the URL. */
    protected String listUrlToken() { return "ambulancebooking"; }

    /** The form the Add button opens — same controller for both screens, distinguished by a mode suffix
     *  ({@code /2} for Booking, {@code /1} for Requisition). */
    public static final String ADD_ROUTE = "#/add-AmbulanceRequisition";

    protected static final String M = "AmbulanceRequisition.";

    public String lastControls = "";
    public String lastMrn = "", lastSearchResult = "", lastPatientName = "", lastPatientType = "";
    public String lastVehicleType = "", lastVehicle = "", lastDriver1 = "", lastDoctor = "";

    // ---- shared JS ---------------------------------------------------------

    /**
     * Helpers injected into the evaluates below: {@code vis} (rendered — DevHIS keeps hidden twins of many
     * fields), {@code byNg} (visible element for an ng-model), {@code setInp} (write through the ngModel
     * controller so Angular actually sees the value) and {@code pick} (choose an option and fire change
     * through both Angular and jQuery, polling while the list is still loading).
     */
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

    /** <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Booking</b>, else the direct route. */
    public boolean navigateViaMenu() {
        // The nav menu is built after login completes; without it the menu walk silently does nothing.
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("AmbulanceBooking.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ambulance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        // Exact text match first — "Ambulance Booking" and "Ambulance Requisition" are siblings in the
        // same submenu, so a loose /ambulance/ match would pick whichever comes first.
        Object tagged = page.evaluate("(want) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>norm(x.textContent).toLowerCase()===String(want).toLowerCase())"
                + "   || links.find(x=>new RegExp(String(want).replace(/\\s+/g,'\\\\s*'),'i').test(norm(x.textContent)));"
                + " if(!a) return false; a.id='__ambMenu'; return true; }", menuLabel());
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ambMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println(menuLabel() + ".nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ambMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println(menuLabel() + ".nav: falling back to direct route " + listRoute());
            try { page.evaluate("(h) => { window.location.hash = h; }", listRoute()); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        // The list screen renders its filter row + grid well after the route change.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddAmbulance/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("AmbulanceBooking.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    /** True on this screen's list/search grid (not the add form, which shares the route stem). */
    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains(listUrlToken()) && !url.contains("add-");
    }

    /** True on the booking form ({@code #/add-AmbulanceRequisition/...}). */
    public boolean onBookingForm() {
        if (page.url().toLowerCase().contains("add-ambulancerequisition")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='AmbulanceRequisition.MRNo']\")].find(e=>e.offsetParent!==null)"));
    }

    /**
     * Click <b>Add</b> ({@code AddAmbulanceRequisition()}) to open the booking form, then wait for the
     * form to actually render (the MRN field) rather than for a fixed delay.
     */
    public boolean clickAdd() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/AddAmbulance/i.test(x.getAttribute('ng-click')||'') || /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__ambAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("AmbulanceBooking.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ambAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AmbulanceBooking.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ambAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='AmbulanceRequisition.MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("AmbulanceBooking.clickAdd: booking form did not render"); }
        waitForAngular(1200);   // let the driver/doctor master lists finish loading
        return onBookingForm();
    }

    /** Diagnostics: every visible control on the booking form with its ng-model. Never fails the flow. */
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
        System.out.println("AmbulanceBooking controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: vehicle type ---------------------------------------------

    /** Select <b>Vehicle Type</b> ({@code AmbulanceRequisition.vehicletypeid}) — it cascades into Vehicle. */
    public String selectVehicleType() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "vehicletypeid')); })");
        lastVehicleType = r == null ? "" : r.toString();
        waitForAngular(1500);   // Vehicle list loads off the back of this
        System.out.println("AmbulanceBooking: Vehicle Type = " + lastVehicleType);
        return lastVehicleType;
    }

    /**
     * Best-effort harvest of MRNs already sitting in this screen's list grid — those patients were
     * already accepted by a prior real booking, so they are known to resolve. Used as a fallback when
     * the pinned/default MRN does not exist in the target environment. Never fails the flow; returns
     * an empty list if the grid holds nothing or no MRN-shaped column is found.
     */
    public java.util.List<String> harvestMrnsFromList() {
        Object r = page.evaluate("() => { let best=-1, arr=null;"
                + " document.querySelectorAll('*').forEach(el=>{ try{"
                + "   const s=window.angular.element(el).scope(); if(!s) return;"
                + "   const scan=a=>{ if(Array.isArray(a) && a.length && typeof a[0]==='object'"
                + "        && Object.keys(a[0]).some(k=>/^mrno$/i.test(k)) && a.length>best){ best=a.length; arr=a; } };"
                + "   if(s.grid && s.grid.options && s.grid.options.data) scan(s.grid.options.data);"
                + "   for(const k of Object.keys(s)){ try{ scan(s[k]); }catch(e){} } }catch(e){} });"
                + " const out=[], seen=new Set();"
                + " if(arr){ for(const row of arr){ const key=Object.keys(row).find(k=>/^mrno$/i.test(k));"
                + "   const m=key && row[key] && String(row[key]).trim();"
                + "   if(m && !seen.has(m)){ seen.add(m); out.push(m); } } }"
                + " return out; }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        System.out.println("AmbulanceBooking: harvested " + list.size() + " MRN(s) from the list -> " + list);
        return list;
    }

    // ---- step 2: MRN + search ----------------------------------------------

    /**
     * Enter the <b>MRN No.</b> ({@code AmbulanceRequisition.MRNo}) and click the search icon
     * ({@code SearchPatientByMRNo()}). Success is judged on {@code patientname} being populated, which is
     * what the screen fills when the MRN resolves.
     */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;

        // The OPD / IPD / External radio scopes WHICH register the MRN is looked up in — searching without
        // one set returns nothing. Try each in turn and stop at the first that resolves a patient.
        String matched = "";
        for (String mode : new String[]{"OPD", "IPD", "External"}) {
            page.evaluate("(args) => {" + JS
                    + " const [m, mode] = args;"
                    + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='" + M + "OPDIPD']\")].filter(vis)"
                    + "   .find(x=>{ const g=x.closest('label,div,td'); return g && new RegExp('^\\\\s*'+mode+'\\\\s*$','i').test(norm(g.textContent)); })"
                    + "   || [...document.querySelectorAll(\"input[type=radio][ng-model='" + M + "OPDIPD']\")].filter(vis)"
                    + "        .find(x=>new RegExp(mode,'i').test(norm((x.closest('label,div,td')||{}).textContent||'')));"
                    + " if(r && !r.checked) r.click();"
                    + " setInp('" + M + "MRNo', m);"
                    + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                    + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.id='__ambMrnSearch'; }", java.util.List.of(mrn, mode));
            try { page.locator("#__ambMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AmbulanceBooking.searchByMrn[" + mode + "]: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ambMrnSearch'); if(e) e.removeAttribute('id'); }");

            try {
                page.waitForFunction("() => { const e=[...document.querySelectorAll(\"[ng-model='" + M + "patientname']\")].find(x=>x.offsetParent!==null);"
                        + " return e && e.value && e.value.trim().length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                matched = mode;
                System.out.println("AmbulanceBooking: MRN resolved under " + mode);
                break;
            } catch (Exception ignore) {
                System.out.println("AmbulanceBooking: MRN " + mrn + " not found under " + mode);
            }
        }
        lastPatientType = matched;
        waitForAngular(600);

        Object r = page.evaluate("() => {" + JS
                + " const val=ng=>{ const e=byNg(ng); return e?norm(e.value):''; };"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'patient='+val('" + M + "patientname')+' | calling='+val('" + M + "callingno')"
                + "   +(msg?' | msg='+msg:''); }");
        if (!lastPatientType.isEmpty()) r = lastPatientType + ": " + r;
        lastSearchResult = r == null ? "" : r.toString();
        Object pn = page.evaluate("() => {" + JS
                + " const e=byNg('" + M + "patientname'); return e?norm(e.value):''; }");
        lastPatientName = pn == null ? "" : pn.toString();
        System.out.println("AmbulanceBooking: search MRN " + mrn + " -> " + lastSearchResult);
        return lastSearchResult;
    }

    /** True when the MRN resolved to a patient (the screen filled Patient Name). */
    public boolean patientLoaded() {
        return lastPatientName != null && !lastPatientName.trim().isEmpty();
    }

    /**
     * Alternative to {@link #searchByMrn}: open the <b>Search Patient</b> popup
     * ({@code OpenPopupScreen()}, the person-icon button beside the MRN field) instead of typing an exact
     * MRN, search it with a short MRN filter, and pick the first real row from the results table
     * ({@code SetSearchPatient(PR)}).
     *
     * <p>Only works under <b>OPD</b> — the popup button and the popup's own MRN field are
     * {@code ng-disabled} unless the OPD/IPD/External radio is set to OPD, and inside the popup itself the
     * "IPD" and "ALL" registration-scope radios are themselves disabled, leaving OPD the only usable
     * scope. Verified live 2026-09-02: a full/exact MRN belonging to a real patient elsewhere in this
     * environment ({@code 100000684}) still answers "Record not found!" here, and so does a "10"-prefixed
     * filter — but "11" (the prefix this OPD counter's own patients actually use, e.g. the row this test
     * itself has already booked) returns real rows. Tries each of {@code filters} in turn and stops at the
     * first that yields a selectable row, so a config change to the underlying data does not need a code
     * change here.
     */
    public String searchByPopup(String... filters) {
        // OPD must be selected for the popup button (and its own MRN field) to be enabled at all.
        page.evaluate("() => {" + JS
                + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='" + M + "OPDIPD']\")].filter(vis)"
                + "   .find(x=>{ const g=x.closest('label,div,td'); return g && /^\\s*opd\\s*$/i.test(norm(g.textContent)); });"
                + " if(r && !r.checked) r.click(); }");
        waitForAngular(500);

        for (String filter : filters) {
            Object opened = page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .find(x=>/OpenPopupScreen/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b || b.disabled) return false; b.id='__ambPopupOpen'; return true; }");
            if (!Boolean.TRUE.equals(opened)) {
                System.out.println("AmbulanceBooking.searchByPopup: Search Patient button not found/disabled");
                break;
            }
            try { page.locator("#__ambPopupOpen").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AmbulanceBooking.searchByPopup: open failed - " + e.getMessage()); continue; }
            page.evaluate("() => { const e=document.getElementById('__ambPopupOpen'); if(e) e.removeAttribute('id'); }");

            try {
                page.waitForFunction(
                        "() => [...document.querySelectorAll(\"input[ng-model='PatientData.MRNo']\")].some(e=>e.offsetParent!==null && !e.disabled)",
                        null, new Page.WaitForFunctionOptions().setTimeout(6000));
            } catch (Exception e) {
                System.out.println("AmbulanceBooking.searchByPopup: popup MRN field never became usable for filter " + filter);
                continue;
            }

            page.evaluate("() => {" + JS
                    + " const e=[...document.querySelectorAll(\"input[ng-model='PatientData.MRNo']\")].filter(vis)[0];"
                    + " if(e) e.id='__ambPopupMrn';"
                    + " const b=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.id='__ambPopupSearch'; }");
            try {
                page.locator("#__ambPopupMrn").fill(filter, new com.microsoft.playwright.Locator.FillOptions().setTimeout(6000));
                page.locator("#__ambPopupSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            } catch (Exception e) {
                System.out.println("AmbulanceBooking.searchByPopup: search failed for filter " + filter + " - " + e.getMessage());
            }
            page.evaluate("() => { ['__ambPopupMrn','__ambPopupSearch'].forEach(id => {"
                    + " const e=document.getElementById(id); if(e) e.removeAttribute('id'); }); }");
            waitForAngular(1200);

            Object picked = page.evaluate("() => {" + JS
                    + " const tbl=[...document.querySelectorAll('table')].filter(vis)"
                    + "   .find(t=>t.querySelector(\"button[ng-click*='SetSearchPatient']\"));"
                    + " if(!tbl) return '(no-table)';"
                    + " const rows=[...tbl.querySelectorAll('tr')].filter(r=>r.querySelector(\"button[ng-click*='SetSearchPatient']\"));"
                    + " if(!rows.length) return '(no-rows)';"
                    + " rows[0].querySelector(\"button[ng-click*='SetSearchPatient']\").click();"
                    + " return 'picked row 0 of ' + rows.length; }");
            String pickedStr = picked == null ? "" : picked.toString();
            System.out.println("AmbulanceBooking.searchByPopup[" + filter + "]: " + pickedStr);

            if (pickedStr.startsWith("picked")) {
                waitForAngular(800);
                lastMrn = filter;
                Object pn = page.evaluate("() => {" + JS + " const e=byNg('" + M + "patientname'); return e?norm(e.value):''; }");
                lastPatientName = pn == null ? "" : pn.toString();
                lastSearchResult = "popup[filter=" + filter + "] -> patient=" + lastPatientName;
                System.out.println("AmbulanceBooking: " + lastSearchResult);
                return lastSearchResult;
            }

            // No rows for this filter — close the popup before trying the next one.
            page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .find(x=>/CloseModel/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            waitForAngular(500);
        }
        lastSearchResult = "popup: no patient found among filters " + java.util.Arrays.toString(filters);
        System.out.println("AmbulanceBooking: " + lastSearchResult);
        return lastSearchResult;
    }

    // ---- step 3: booking details -------------------------------------------

    /**
     * In <b>Booking Details</b> select <b>Vehicle</b> ({@code vehicleid}), <b>Driver1</b>
     * ({@code driverid1}) and <b>Doctor</b> ({@code doctorid}), in that order — Vehicle is filtered by the
     * Vehicle Type chosen earlier, so it is given time to load first.
     */
    public String fillBookingDetails() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const v=await pick('" + M + "vehicleid'); await sleep(500);"
                + " const d1=await pick('" + M + "driverid1'); await sleep(300);"
                + " const dc=await pick('" + M + "doctorid');"
                + " resolve('Vehicle='+v+' | Driver1='+d1+' | Doctor='+dc); })");
        String out = r == null ? "" : r.toString();
        for (String part : out.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("Vehicle=")) lastVehicle = p.substring(8).trim();
            else if (p.startsWith("Driver1=")) lastDriver1 = p.substring(8).trim();
            else if (p.startsWith("Doctor=")) lastDoctor = p.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("AmbulanceBooking: " + out);
        return out;
    }

    /** True when all three Booking Details dropdowns took a real value. */
    public boolean bookingDetailsComplete() {
        return isReal(lastVehicle) && isReal(lastDriver1) && isReal(lastDoctor);
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }

    // ---- step 4: save + toast ----------------------------------------------

    /** Click <b>Save</b> ({@code fnIUDAmbulanceRequisition()}) and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__ambToasts=[]; if(window.__ambObs) window.__ambObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__ambToasts.includes(t)) window.__ambToasts.push(t); }); };"
                + " window.__ambObs=new MutationObserver(grab); window.__ambObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/fnIUDAmbulanceRequisition/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null)"
                + "        .find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return false; b.id='__ambSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("AmbulanceBooking.save: Save button not found"); return ""; }

        try { page.locator("#__ambSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AmbulanceBooking.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ambSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);

        // Some DevHIS screens gate the save behind a "Do You Want To Save" confirm.
        acceptSaveDialog();
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__ambToasts||[]).some(a=>/saved|success|booked|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__ambToasts||[]).includes(t)) (window.__ambToasts=window.__ambToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ambToasts||[];"
                + " return a.find(x=>/(saved|booked|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
