package com.kpj.pages.Op_page;

import com.microsoft.playwright.Page;

/**
 * OP &gt; <b>Doctor Queue</b> — Page Object.
 *
 * <p>Doctor Queue is a queue screen sharing the same {@code queue.*} search models, ui-grid and footer actions
 * (incl. <b>Close Visit</b> = {@code OpenDischargeTypeModal()}) as OP Outpatient Queue Management. It is reached
 * from the <b>OP</b> menu. This page therefore <b>extends {@link OutPatientQueueManagementPage}</b> to inherit
 * every queue method (select patient, Close Visit, etc.) and only overrides {@link #navigateTo(String)} to enter
 * via the OP → Doctor Queue menu (capturing the route; falling back to the direct queue route).</p>
 */
public class DoctorQueuePage extends OutPatientQueueManagementPage {

    /** Route — Doctor Queue (a distinct screen from #/queueManagement). Captured/confirmed at runtime. */
    public static String ROUTE = "#/DoctorWiseQueue";

    public DoctorQueuePage(Page page) { super(page); }

    /** Enter via OP → Doctor Queue (menu href-mining). Falls back to the shared queue route if the menu isn't found. */
    @Override
    public void navigateTo(String baseUrl) {
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('li > a,a')].some(a=>/^\\s*OP\\s*$/i.test((a.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        // expand the OP menu
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const op=[...document.querySelectorAll('li > a,a')].find(a=>/^\\s*OP\\s*$/i.test(norm(a.textContent)) && a.offsetParent!==null); if(op) op.click(); }");
        waitForAngular(800);
        // click the "Doctor Queue" link + capture its route
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*doctor\\s*queue\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__docQueueMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__docQueueMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DoctorQueue.navigateTo: click failed - " + e.getMessage()); }
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        } else {
            // mine ANY anchor (even hidden) whose text is "Doctor Queue" / href mentions doctor queue
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*doctor\\s*queue\\s*$/i.test(norm(x.textContent)) || /doctorqueue/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("DoctorQueue.navigateTo: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) {
                ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
                try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            }
        }
        // wait for the queue screen (Search button present) — else fall back to the shared queue route
        boolean ready = false;
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            ready = true;
        } catch (Exception ignore) { }
        if (!ready) {
            System.out.println("DoctorQueue.navigateTo: Doctor Queue menu not ready — routing directly to " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            try {
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("DoctorQueue.navigateTo: direct route not ready in time"); }
        }
        waitForAngular(1500);
    }
}
