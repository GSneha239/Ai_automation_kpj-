package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named EmailTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>E-mail Template</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>E-mail Template</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Template Name</b> and <b>Subject</b>.</li>
 *   <li>Enter the remark in the <b>template body</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The template body is a CKEditor: writing the underlying element does nothing, because the editor
 * keeps its own document and overwrites it — {@code setData} + {@code updateElement} is what reaches the
 * model. Each header field is taken by its own ng-model and used only once, so two values cannot land in
 * the same box (which is how Area/Town's code was overwritten by its town name).</p>
 *
 * <p>&#9888; A successful run CREATES a REAL e-mail template in the target environment.</p>
 */
public class EmailTemplate extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public EmailTemplate() { super("ApplicationConfiguration_General_EmailTemplate"); }

    public static void main(String[] args) {
        EmailTemplate t = new EmailTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - E-mail Template", "Application Configuration > General > E-mail Template",
                "&#9888; Creates a REAL e-mail template: Add, enter the Code, Template Name and Subject, "
                        + "fill the template body, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "ET" + stamp);
        String templateName = System.getProperty("templateName", "Auto Template " + stamp);
        String subject = System.getProperty("subject", "Automated subject " + stamp);
        String remark = System.getProperty("remark", "Automated e-mail template body " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.EmailTemplate et =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.EmailTemplate(page);

        // 1) Navigate
        boolean rendered = et.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", et.lastMenu);
        step(page, "Open E-mail Template screen",
                "Click Application Configuration -> General -> E-mail Template",
                "The E-mail Template screen is shown",
                rendered ? "Opened " + page.url()
                           + (et.lastRoute.isEmpty() ? "" : " (menu route " + et.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", et.describeControls());

        // 2) Add
        boolean formOpen = et.clickAdd();
        addSummary("Form controls", et.describeControls());
        step(page, "Click Add", "Click Add to open the e-mail template form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Template Name + Subject
        String entry = et.enterCodeNameSubject(code, templateName, subject);
        step(page, "Enter code, template name and subject",
                "Enter the Code " + code + ", the Template Name and the Subject",
                "All three are entered", entry, et.detailsEntered() ? "PASS" : "FAIL");

        // 4) Template body
        String body = et.enterTemplateBody(remark);
        step(page, "Enter the remark in the template body",
                "Type the remark into the template body editor",
                "The template body holds the remark", body, et.bodyEntered() ? "PASS" : "FAIL");

        // 5) Save -> toast
        String toast = et.saveAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.EmailTemplate.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = et.inList(code) || et.inList(templateName);
        addSummary("List check", et.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + et.lastSaveDiagnostics
                  + (inList ? " BUT the template IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the template IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Save answered \"" + toast + "\" and the template is NOT in the list. "
                              + et.lastListCheck)
                        : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the template was created", "Look for " + code + " in the list",
                "The new e-mail template is listed", et.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Code / Name / Subject", code + " / " + templateName + " / " + subject);
        addSummary("Template body", et.lastBody);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
