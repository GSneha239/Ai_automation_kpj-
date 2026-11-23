package com.kpj.pages.Telemedicine_page;

import com.kpj.pages.Op_page.Appointment.BookAppointmentPage;
import com.microsoft.playwright.Page;

/**
 * Telemedicine &gt; <b>Appointment</b> — Page Object.
 *
 * <p>Telemedicine Appointment is the SAME form as OP &gt; Book Appointment (both route to {@code #/New}); this
 * class only changes the entry point (the <b>Telemedicine</b> menu instead of OP) and reuses every fill / slot /
 * add / save-&amp;-report method from {@link BookAppointmentPage}.</p>
 */
public class TelemedicineAppointmentPage extends BookAppointmentPage {

    public TelemedicineAppointmentPage(Page page) { super(page); }

    /** Open <b>Telemedicine</b> → <b>Appointment</b> ({@code #/New}) and wait for the form + dropdown master-data. */
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
            // expand Telemedicine menu, then click the Appointment child (#/New)
            page.evaluate("() => { const t=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='Telemedicine'); if(t) t.click(); }");
            waitForAngular(800);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*appointment\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null) || [...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/New'); if(a) a.click(); }");
            waitForAngular(1000);
            try {
                page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(20000));
                formUp = true;
            } catch (Exception e) {
                System.out.println("TelemedicineAppointment.navigateTo: form not up via menu (attempt " + (attempt + 1) + ") — trying the direct #/New route");
                try {
                    page.navigate(baseUrl + "/#/New",
                            new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                } catch (Exception ex) { System.out.println("TelemedicineAppointment.navigateTo: direct route failed - " + ex.getMessage()); }
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
