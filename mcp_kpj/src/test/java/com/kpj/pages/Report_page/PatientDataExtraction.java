package com.kpj.pages.Report_page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Report &gt; <b>Patient Data Extraction</b> ({@code #/PatientAnalysisReport}) - the SAME screen as Ancillary Services &gt; CRM &gt; Patient Data
 * Extraction, reached through the sidebar's top-level <b>Report</b> group (Report Console, Patient Data Extraction, Patient Extraction Report).
 *
 * <p>Everything but the navigation (Visit Date range, Search, the grid, Save Data ...) is inherited from the CRM page object.</p>
 */
public class PatientDataExtraction extends com.kpj.pages.AncillaryServices_page.CRM_page.PatientDataExtraction {

    public PatientDataExtraction(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    /**
     * Sidebar: <b>Report</b> (EXACT text - "Report Console" is an entry INSIDE it) &rarr; Patient Data Extraction. The link is looked up inside the
     * Report group's own {@code li}, because the CRM group lists the same route.
     */
    @Override
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        page.waitForTimeout(1000);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*report\\s*$");
            waitForAngular(800);
            Object tagged = page.evaluate("() => {" + JS
                    + " const top=[...document.querySelectorAll('a')].filter(vis).find(x=>/^\\s*report\\s*$/i.test(norm(x.textContent)));"
                    + " const li=top&&top.closest('li'); if(!li) return false;"
                    + " const l=[...li.querySelectorAll('a[href]')].find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/patientanalysisreport'); if(!l) return false;"
                    + " l.id='__pdrLink'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__pdrLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("Report.PatientDataExtraction.nav: click failed - " + e.getMessage().split("\n")[0]);
                    page.evaluate("() => { const e=document.getElementById('__pdrLink'); if(e) e.click(); }");
                }
                page.evaluate("() => { const e=document.getElementById('__pdrLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** Scroll the results grid to the top of the window so a screenshot shows the data. */
    public void scrollGridIntoView() {
        page.evaluate("() => { const g=document.querySelector('#grid1'); if(g) g.scrollIntoView({block:'start'}); }");
        page.waitForTimeout(600);
    }

    /** Which table title the screen shows above the results: "List Of Item Enquiry", "Clinical Filter", or "" when neither is on screen. */
    public String tableTitle() {
        Object r = page.evaluate("() => { const t=(document.body.innerText||'').toLowerCase();"
                + " return t.includes('list of item enquiry') ? 'List Of Item Enquiry' : t.includes('clinical filter') ? 'Clinical Filter' : ''; }");
        return r == null ? "" : r.toString();
    }
}
