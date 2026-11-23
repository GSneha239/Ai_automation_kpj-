package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SubTaxMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>SubTax Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Billing</b> → <b>SubTax Master</b>; click <b>Add</b>.</li>
 *   <li>Enter Code, Remark, Base Tax, Percentage.</li>
 *   <li>Click <b>Save</b> ({@code fnIUDSubTaxMaster}); wait for the success toast.</li>
 * </ol>
 */
public class SubTaxMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public SubTaxMaster() { super("ApplicationConfig_Billing_SubTaxMaster"); }

    public static void main(String[] args) {
        SubTaxMaster t = new SubTaxMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - SubTax Master", "Application Configuration > Billing > SubTax Master",
                "Add a SubTax Master: enter Code, Remark, Base Tax, Percentage, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.SubTaxMaster st =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.SubTaxMaster(page);

        st.navigateViaMenu();
        step(page, "Open SubTax Master screen", "Application Configuration -> Billing -> SubTax Master",
                "The SubTax Master screen is shown", st.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                st.onScreen() ? "PASS" : "FAIL");
        if (!st.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = st.clickAdd();
        step(page, "Click Add", "Click Add -> #/add-SubTaxMaster",
                "The add form is shown", added ? "Add form opened (" + page.url() + ")" : "Add form did not open",
                added ? "PASS" : "FAIL");

        // Enter Code, Remark, Base Tax, Percentage; click Save — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = st.fillDetails(attempt);
            used++;
            toast = st.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("SubTaxMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill.contains("Code=STX") && !fill.contains("BaseTax=(n/a");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Base Tax, Percentage", "Fill the SubTax Master fields",
                "The SubTax Master fields are filled", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Save & success toast", "Click Save (fnIUDSubTaxMaster); on 'already exists' change the details and Save again",
                "'... saved successfully' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("SubTax Code", st.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
