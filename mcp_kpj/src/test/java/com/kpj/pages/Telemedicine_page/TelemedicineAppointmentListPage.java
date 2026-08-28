package com.kpj.pages.Telemedicine_page;

import com.kpj.pages.Op_page.Appointment.AppointmentListPage;
import com.microsoft.playwright.Page;

/**
 * Telemedicine &gt; <b>Appointment List</b> — Page Object.
 *
 * <p>Telemedicine Appointment List is the SAME screen as OP &gt; Appointment &gt; Appointment List (both route to
 * {@code #/AppointmentList}); this class only changes the entry point (the <b>Telemedicine</b> menu) and reuses
 * every search / select / MRD / history / executor / reschedule / cancel method from {@link AppointmentListPage}.</p>
 */
public class TelemedicineAppointmentListPage extends AppointmentListPage {

    public TelemedicineAppointmentListPage(Page page) { super(page); }

    /** Open <b>Telemedicine</b> → <b>Appointment List</b> ({@code #/AppointmentList}), then reuse the parent's
     *  readiness (hash-route + Search button wait + helper install). */
    @Override
    public void navigateTo(String baseUrl) {
        // Enter via the Telemedicine menu (faithful entry point) — BEST-EFFORT: a menu click can trigger an SPA
        // route change that destroys the JS context mid-evaluate ("Execution context was destroyed"). Swallow the
        // whole block (incl. the waitForAngular pumps); super.navigateTo() below is the real, reliable readiness
        // gate (it hash-routes to the same #/AppointmentList).
        try {
            try {
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x => x.textContent.trim() === 'Telemedicine')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const t=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='Telemedicine'); if(t) t.click(); }");
            waitForAngular(900);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/appointment\\s*list/i.test(norm(x.textContent)) && x.offsetParent!==null) || [...document.querySelectorAll(\"a[href='#/AppointmentList']\")][0]; if(a) a.click(); }");
            waitForAngular(1200);
        } catch (Exception e) {
            System.out.println("TelemedAppointmentList.navigateTo: menu entry raced a navigation - continuing (" + e.getClass().getSimpleName() + ")");
        }
        super.navigateTo(baseUrl);
    }
}
