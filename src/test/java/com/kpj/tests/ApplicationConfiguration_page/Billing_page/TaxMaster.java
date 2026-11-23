package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named TaxMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Tax</b> (Tax Master).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Billing</b> → <b>Tax</b>; click <b>Add</b>.</li>
 *   <li>Enter Code, Remark, Percentage, Ledger Name.</li>
 *   <li>Click <b>Save</b> ({@code IUDTaxMaster}); wait for the success toast.</li>
 * </ol>
 */
public class TaxMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public TaxMaster() { super("ApplicationConfig_Billing_TaxMaster"); }

    public static void main(String[] args) {
        TaxMaster t = new TaxMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Tax", "Application Configuration > Billing > Tax",
                "Add a Tax (Tax Master): enter Code, Remark, Percentage, Ledger Name, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.TaxMaster tax =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.TaxMaster(page);

        boolean onScreen = tax.navigateViaMenu();
        System.out.println("TAX url => " + page.url());
        step(page, "Open Tax screen", "Application Configuration -> Billing -> Tax",
                "The Tax Master screen is shown", tax.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                tax.onScreen() ? "PASS" : "FAIL");
        if (!tax.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = tax.clickAdd();
        step(page, "Click Add", "Click Add -> #/add-TaxMaster",
                "The add form is shown", added ? "Add form opened (" + page.url() + ")" : "Add form did not open",
                added ? "PASS" : "FAIL");

        // Enter Code, Remark, Percentage, Ledger Name; click Save — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = tax.fillDetails(attempt);
            used++;
            toast = tax.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("TaxMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill.contains("Code=TAX") && !fill.contains("=(no)");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Percentage, Ledger Name", "Fill the Tax Master fields",
                "The Tax Master fields are filled", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Save & success toast", "Click Save (IUDTaxMaster); on 'already exists' change the details and Save again",
                "'... saved successfully' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Tax Code", tax.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
