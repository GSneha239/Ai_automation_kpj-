package com.kpj.pages.Telemedicine_page;

import com.kpj.pages.Op_page.RegistrationPage;
import com.microsoft.playwright.Page;

/**
 * Telemedicine &gt; <b>Registration</b> — Page Object.
 *
 * <p>Telemedicine Registration is the SAME form/flow as OP &gt; Registration (both the {@code VisitScreen}
 * {@code Registration.*}/{@code Visit.*} form + {@code IUDRegistration()} save + auto Registration Report /
 * Consent tabs); this class only changes the entry point (the <b>Telemedicine</b> menu) and reuses every
 * fill / kin / payor / visit / save / report method from {@link RegistrationPage}.</p>
 */
public class TelemedicineRegistrationPage extends RegistrationPage {

    public TelemedicineRegistrationPage(Page page) { super(page); }

    /** Open <b>Telemedicine</b> → <b>Registration</b> (the VisitScreen form) and wait for master-data. */
    @Override
    public boolean open(String baseUrl) {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('li > a')].length > 3",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        // Telemedicine menu -> Registration
        try { page.evaluate("() => { const t=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='Telemedicine'); if(t) t.click(); }"); }
        catch (Exception ignore) { System.out.println("TelemedRegistration.open: Telemedicine menu click raced a navigation - continuing"); }
        waitForAngular(1200);
        try { page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*registration\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null) || [...document.querySelectorAll(\"a[href='#/VisitScreen']\")][0]; if(a) a.click(); }"); }
        catch (Exception ignore) { System.out.println("TelemedRegistration.open: Registration link click raced a navigation - continuing"); }
        waitForAngular(1500);
        try {
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(8000));
        } catch (Exception e) {
            page.evaluate("() => { window.location.hash = '#/VisitScreen'; }");
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(40000));
        }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) { }
        waitForAngular(2500);
        boolean masterLoaded = false;
        for (int i = 0; i < 4 && !masterLoaded; i++) {
            try {
                page.waitForFunction(
                        "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.GenderID'); return e && e.options.length>1; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                masterLoaded = true;
            } catch (com.microsoft.playwright.PlaywrightException ex) { waitForAngular(1500); }
        }
        return masterLoaded;
    }
}
