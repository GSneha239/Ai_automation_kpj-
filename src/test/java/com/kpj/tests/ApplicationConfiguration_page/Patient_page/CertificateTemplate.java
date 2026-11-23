package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CertificateTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Certificate Template</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Certificate Template</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddCertificateTemplateMaster}).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Select the template — tick <b>Certificate Template</b>.</li>
 *   <li>Fill the remaining mandatory details (<b>Field Name*</b>, <b>Control Binding*</b>, Parameter Name, Font,
 *       template body) and click the inner <b>Add</b> ({@code AddTemplateDetails}).</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCertificateTemplate}); wait for the success toast. If the toast says the
 *       code / remark already exists, change the details and Submit again.</li>
 * </ol>
 */
public class CertificateTemplate extends DevHisBase {

    /** How many times to re-enter fresh Code/Remark when the server says they already exist. */
    private static final int MAX_ATTEMPTS = 40;

    public CertificateTemplate() { super("ApplicationConfig_Patient_CertificateTemplate"); }

    public static void main(String[] args) {
        CertificateTemplate t = new CertificateTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Certificate Template",
                "Application Configuration > Patient > Certificate Template",
                "Add a Certificate Template: Add, enter Code + Remark, select the Certificate Template type, fill the "
                        + "mandatory Field Name / Control Binding details, Add the detail row, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.CertificateTemplate ct =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.CertificateTemplate(page);

        boolean on = ct.navigateViaMenu();
        step(page, "Open Certificate Template screen",
                "Application Configuration -> Patient -> Certificate Template",
                "The Certificate Template list screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        boolean added = ct.clickAdd();
        step(page, "Click Add", "Click Add (AddCertificateTemplateMaster) -> #/add-CertificateTemplate",
                "The Certificate Template add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }
        System.out.println("[CertificateTemplate] add form =>\n" + ct.dumpAddForm());

        String codeRemark = ct.fillCodeAndRemark(0);
        boolean crOk = codeRemark.contains("Code=CT") && !codeRemark.contains("Remark=(no)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are set on the form", codeRemark, crOk ? "PASS" : "FAIL");

        String tpl = ct.selectTemplate();
        boolean tplOk = tpl.contains("checked=true");
        step(page, "Select template", "Tick the Certificate Template type (discharge2.iscertificatetemplate)",
                "The Certificate Template type is selected", tpl, tplOk ? "PASS" : "FAIL");

        String details = ct.fillTemplateDetails();
        boolean detOk = !details.contains("ControlBinding=(no)") && !details.contains("FieldName=(no)");
        step(page, "Fill mandatory details",
                "Enter Field Name*, select Control Binding*, Parameter Name, Font and the template body",
                "All mandatory template details are filled", details, detOk ? "PASS" : "FAIL");

        String detail = ct.clickAddDetail();
        boolean addDetOk = !detail.startsWith("(no");
        step(page, "Add the template detail row", "Click the inner Add (AddTemplateDetails)",
                "The Field Name / Control Binding row is appended to the detail list", detail,
                addDetOk ? "PASS" : "FAIL");

        // Submit — retry with a fresh Code/Remark on "already exists" (the template type / detail row already set
        // stay untouched).
        String toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            used++;
            toast = ct.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("CertificateTemplate: attempt " + used + " already exists — changing Code/Remark and retrying");
            codeRemark = ct.fillCodeAndRemark(attempt + 1);
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (fnIUDCertificateTemplate); on 'already exists' change the details and Submit again",
                "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Certificate Template Code", ct.lastCode);
        addSummary("Remark", ct.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/CertificateTemplate -> #/add-CertificateTemplate");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
