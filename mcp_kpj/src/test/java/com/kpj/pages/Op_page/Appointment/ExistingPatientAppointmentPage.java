package com.kpj.pages.Op_page.Appointment;

import com.microsoft.playwright.Page;

/**
 * OP &gt; Appointment &gt; <b>Existing</b> ({@code #/Existing}) — a screen genuinely distinct from
 * {@link BookAppointmentPage}'s "Book Appointment" ({@code #/New}), not the same route with an MRN search bolted
 * on (an earlier version of this class assumed that; corrected live 2026-08-26 — "Existing" is its own sibling
 * menu entry right next to "Book Appointment" under OP → Appointment, confirmed by reading the menu DOM:
 * {@code Appointment List, Existing, Book Appointment, Doctorwise Appointment Schedule, ...}).</p>
 *
 * <p>The two screens share the underlying form/controller though (same {@code #txtMRNo} /
 * {@code ng-model="PatientData.MRNO"}, same {@code #btnSearch} / {@code ng-click="FindPatient();"}, same
 * {@code #txtFirstName} / {@code ng-model="PatientData.FirstName"} — checked live field-by-field) — only the
 * ROUTE to reach it differs. So this class extends {@link BookAppointmentPage} and reuses
 * {@link #selectFreeSlot}, {@link #clickAdd}, {@link #saveAndCaptureReport} and {@link #capturedAlerts} as-is,
 * and only overrides {@link #navigateTo} to click the real "Existing" menu link instead of "Book Appointment".</p>
 *
 * <p><b>Checked live 2026-08-26: "Next Schedule" already IS tomorrow's date</b> ("Current Schedule" is always
 * today, "Next Schedule" the day after) — so the inherited {@link #selectFreeSlot()}, which already picks from
 * Next Schedule, needs no changes to satisfy "Appointment Date = tomorrow".</p>
 *
 * <p><b>Two of {@link BookAppointmentPage}'s hardcoded field values don't actually exist on this screen — checked
 * live, this is a real gap, not new-patient-vs-existing-patient behavior:</b></p>
 * <ul>
 *   <li><b>Payor Type</b> — {@code __setSel(..., 'Self')} silently returns {@code ':no-opt'}. The real option
 *       list (ALL FINANCIAL CLASS, ASSOCIATE COMPANY, COMPANY, … PATIENT, RELATED KPJ, …) has no "Self" and no
 *       text containing "self" either — there is no substring fallback that saves it.</li>
 *   <li><b>Department</b> — {@code __setSel(..., 'Cardiology')} also returns {@code ':no-opt'}: the real list has
 *       "CARDIAC SURGERY" and "CARDIOTHORACIC SURGERY" but no "CARDIOLOGY" and no substring match either.</li>
 * </ul>
 * <p>Neither failure is visible in {@code BookAppointmentTest} today because its "Fill mandatory details" step
 * never checks {@code __setSel}'s return value — it reports PASS unconditionally. This class instead selects the
 * <b>first real (non-"-Select-") option</b> for both, which is guaranteed to exist, and reports the actual value
 * picked rather than assuming a hardcoded name landed. Booking Type ('KPJ') and Appointment Type ('Department')
 * are left as-is — checked live, both resolve correctly (Booking Type via the existing substring fallback,
 * matching "KPJ Portal"). Doctor also uses first-real-option: checked live the Doctor list (263 entries) is the
 * SAME full master list regardless of which Department is selected, so there is no department→doctor pairing to
 * satisfy here (unlike the Change Doctor screen elsewhere in this suite).</p>
 */
public class ExistingPatientAppointmentPage extends BookAppointmentPage {

    public ExistingPatientAppointmentPage(Page page) {
        super(page);
    }

    public String lastPatientName = "";

    // ---- navigation ----------------------------------------------------------

    /**
     * Open OP → Appointment → <b>Existing</b> ({@code #/Existing}) — the real distinct menu entry, not
     * {@code #/New}. Same retry/fallback shape as {@link BookAppointmentPage#navigateTo}, just targeting the
     * "Existing" link (and route) instead of "Book Appointment".
     */
    @Override
    public void navigateTo(String baseUrl) {
        page.navigate(baseUrl + "/#/PatientDashboard",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT)
                        .setTimeout(60000));
        waitForAngular(2000);

        boolean formUp = false;
        for (int attempt = 0; attempt < 3 && !formUp; attempt++) {
            try {
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x => x.textContent.trim() === 'OP')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const op = [...document.querySelectorAll('a')].find(x => x.textContent.trim() === 'OP'); if (op) op.click(); }");
            waitForAngular(800);
            page.evaluate("() => { const a = [...document.querySelectorAll('a')].find(x => x.getAttribute('href') === '#/Existing'); if (a) a.click(); }");
            waitForAngular(1000);
            try {
                page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(20000));
                formUp = true;
            } catch (Exception e) {
                System.out.println("ExistingPatientAppointmentPage.navigateTo: form not up via menu (attempt " + (attempt + 1) + ") — trying the direct #/Existing route");
                try {
                    page.navigate(baseUrl + "/#/Existing",
                            new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                } catch (Exception ex) { System.out.println("ExistingPatientAppointmentPage.navigateTo: direct route failed - " + ex.getMessage()); }
                waitForAngular(2000);
            }
        }
        page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(40000));
        page.waitForFunction(
                "() => { const e = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'PatientData.receivabletypeid'); return e && e.options.length > 1; }",
                null, new Page.WaitForFunctionOptions().setTimeout(40000));
        waitForAngular(1500);
        installHelpers();
    }

    /** True once the URL is really on {@code #/Existing} (not just any screen sharing the same form fields). */
    public boolean onExistingScreen() {
        return page.url().toLowerCase().contains("existing");
    }

    // ---- existing-patient search --------------------------------------------

    /**
     * Enter the MRN and click <b>Search</b> ({@code FindPatient()}); returns the patient Name that loaded (empty
     * if the search didn't populate one).
     */
    public String searchExistingPatientByMrn(String mrn) {
        Object tagged = page.evaluate("() => !!document.getElementById('txtMRNo') && !!document.getElementById('btnSearch')");
        if (!Boolean.TRUE.equals(tagged)) return "";
        page.evaluate("(v) => { const e=document.getElementById('txtMRNo'); const c=angular.element(e).controller('ngModel');"
                + " e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", mrn);
        try { page.locator("#btnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("searchExistingPatientByMrn: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => { const e=document.getElementById('txtFirstName'); return e && e.value && e.value.trim().length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("searchExistingPatientByMrn: patient name never populated"); }
        waitForAngular(800);
        Object name = page.evaluate("() => { const e=document.getElementById('txtFirstName'); return e ? e.value : ''; }");
        lastPatientName = name == null ? "" : name.toString().trim();
        return lastPatientName;
    }

    // ---- appointment configuration (existing patient — Name/NRIC/DOB/etc. are the patient's own, untouched) ----

    /**
     * Set Payor Type, Booking Type, Appointment Type, Department and Doctor. Payor Type and Department use the
     * first real option (see class Javadoc — their previously-hardcoded values don't exist on this screen);
     * Booking Type/Appointment Type keep the same values {@link BookAppointmentPage} uses (both resolve
     * correctly). Returns a summary of what was actually selected for each.
     */
    public String fillAppointmentConfig() {
        Object r = page.evaluate("() => {"
                + " const setSelFirst=(ng)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return ng+':no-select';"
                + "   const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && (o.text||'').trim() && !/^-select-$/i.test((o.text||'').trim()));"
                + "   if(i<0) return ng+':no-real-opt'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}}"
                + "   return ng+':'+e.options[i].text.trim(); };"
                + " const payor=setSelFirst('PatientData.receivabletypeid');"
                + " const booking=window.__setSel('PatientData.BookingTypeID','KPJ');"
                + " const apptType=window.__setSel('PatientData.AppointmentTypeID','Department');"
                + " const dept=setSelFirst('PatientData.DepartmentID');"
                + " return {payor, booking, apptType, dept}; }");
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        waitForAngular(1000);   // Doctor list (re)loads after Department changes
        Object doctor = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.DoctorModalityID'); if(!e) return 'PatientData.DoctorModalityID:no-select';"
                + " const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && !/^-select-$/i.test((o.text||'').trim())); if(i<0) return 'PatientData.DoctorModalityID:no-real-opt';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}}"
                + " return 'PatientData.DoctorModalityID:'+e.options[i].text.trim(); }");
        waitForAngular(500);
        return "Payor=" + m.get("payor") + " | Booking=" + m.get("booking") + " | ApptType=" + m.get("apptType")
                + " | Department=" + m.get("dept") + " | Doctor=" + doctor;
    }

    /** Set Appointment Date to TOMORROW. (Live-confirmed: this only affects display — "Next Schedule" already
     *  represents tomorrow regardless, which is what {@link #selectFreeSlot()} picks from.) */
    public String setAppointmentDateTomorrow() {
        Object v = page.evaluate("() => { const d=new Date(Date.now()+86400000); const dd=String(d.getDate()).padStart(2,'0'), mm=String(d.getMonth()+1).padStart(2,'0'), yyyy=d.getFullYear();"
                + " const val=dd+'/'+mm+'/'+yyyy; window.__setInp('PatientData.AppointmentDate', val); return val; }");
        waitForAngular(1000);
        return v == null ? "" : v.toString();
    }

    // ---- mandatory-field verification ---------------------------------------

    /**
     * Re-check the fields this flow depends on (Payor Type, Booking Type, Appointment Type, Department, Doctor,
     * Appointment Date, From/To time) and fill the FIRST REAL option into any select still on "-Select-" (an
     * empty text/date field is reported but not guessed at, since there is no safe generic default for those).
     * Returns "" when everything was already filled, else a description of what was found empty and/or fixed.
     */
    public String checkAndFillMandatoryFields() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const selNgModels=['PatientData.receivabletypeid','PatientData.BookingTypeID','PatientData.AppointmentTypeID','PatientData.DepartmentID','PatientData.DoctorModalityID'];"
                + " const fixed=[], stillEmpty=[];"
                + " selNgModels.forEach(ng => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                + "   const cur=norm(e.options[e.selectedIndex] ? e.options[e.selectedIndex].text : '');"
                + "   if(!cur || /^-select-$/i.test(cur)) {"
                + "     const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && (o.text||'').trim() && !/^-select-$/i.test((o.text||'').trim()));"
                + "     if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}}"
                + "       fixed.push(ng+' -> '+e.options[i].text.trim()); } else stillEmpty.push(ng+' (no real option to fall back to)'); } });"
                + " const inpNgModels=['PatientData.AppointmentDate','PatientData.FromApptTime','PatientData.ToApptTime'];"
                + " inpNgModels.forEach(ng => { const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + "   if(e && !(e.value||'').trim()) stillEmpty.push(ng+' (empty text field)'); });"
                + " return {fixed, stillEmpty}; }");
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        waitForAngular(500);
        java.util.List<?> fixed = (java.util.List<?>) m.get("fixed");
        java.util.List<?> stillEmpty = (java.util.List<?>) m.get("stillEmpty");
        if ((fixed == null || fixed.isEmpty()) && (stillEmpty == null || stillEmpty.isEmpty())) return "";
        StringBuilder sb = new StringBuilder();
        if (fixed != null && !fixed.isEmpty()) sb.append("Filled: ").append(fixed);
        if (stillEmpty != null && !stillEmpty.isEmpty()) sb.append(sb.length() > 0 ? " | " : "").append("STILL EMPTY: ").append(stillEmpty);
        return sb.toString();
    }
}
