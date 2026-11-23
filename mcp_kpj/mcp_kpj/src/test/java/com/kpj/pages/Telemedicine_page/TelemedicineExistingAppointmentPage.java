package com.kpj.pages.Telemedicine_page;

import com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage;
import com.microsoft.playwright.Page;

/**
 * Telemedicine &gt; <b>Existing Appointment</b> — the SAME form/route as OP &gt; Appointment &gt; Existing
 * ({@code #/Existing}; menu label is literally "ExstingAppointment", a typo in the app itself), confirmed live by
 * reading the Telemedicine submenu DOM: {@code Appointment (#/New), OPDRegistration (#/VisitScreen),
 * ExstingAppointment (#/Existing), Appointment List (#/AppointmentList), EMR (#/EMRTele)} — "ExstingAppointment"
 * has the exact same href as OP's "Existing" link. This class only changes the entry point (the
 * <b>Telemedicine</b> menu instead of OP → Appointment) and reuses every search / fill / slot / mandatory-check /
 * add / save-&amp;-report method from {@link ExistingPatientAppointmentPage} unchanged, the same way
 * {@link TelemedicineAppointmentPage} reuses {@code BookAppointmentPage} for the "new appointment" case.
 */
public class TelemedicineExistingAppointmentPage extends ExistingPatientAppointmentPage {

    public TelemedicineExistingAppointmentPage(Page page) { super(page); }

    /** Open <b>Telemedicine</b> → <b>ExstingAppointment</b> ({@code #/Existing}) and wait for the form to load. */
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
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x => x.textContent.trim() === 'Telemedicine')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            // expand Telemedicine menu, then click the ExstingAppointment child (#/Existing)
            page.evaluate("() => { const t=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='Telemedicine'); if(t) t.click(); }");
            waitForAngular(800);
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/Existing'); if(a) a.click(); }");
            waitForAngular(1000);
            try {
                page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(20000));
                formUp = true;
            } catch (Exception e) {
                System.out.println("TelemedicineExistingAppointmentPage.navigateTo: form not up via menu (attempt " + (attempt + 1) + ") — trying the direct #/Existing route");
                try {
                    page.navigate(baseUrl + "/#/Existing",
                            new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                } catch (Exception ex) { System.out.println("TelemedicineExistingAppointmentPage.navigateTo: direct route failed - " + ex.getMessage()); }
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
}
