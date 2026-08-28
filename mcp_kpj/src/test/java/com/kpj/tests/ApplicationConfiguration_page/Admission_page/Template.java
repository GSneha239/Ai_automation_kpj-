package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Template — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Template</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Template</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Select a <b>Template</b>.</li>
 *   <li>Enter <b>text</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Template extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Template() { super("ApplicationConfig_Template"); }

    public static void main(String[] args) {
        Template t = new Template();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Template", "Application Configuration > Admission > Template",
                "Add a Template: Add, enter Code + Remark, select a Template, enter text, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.Template tpl =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.Template(page);

        // 1) Navigate
        tpl.navigateViaMenu();
        boolean onScreen = tpl.onScreen();
        step(page, "Open Template screen", "Click Application Configuration -> Template",
                "The Template screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = tpl.clickAdd();
        step(page, "Click Add", "Click Add", "The Template add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3)-6) Enter Code + Remark, select a template, enter text, Submit — retry with different details on
        // "already exists".
        String fill = "", tsel = "", textWhere = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = tpl.fillCodeAndRemark(attempt);
            used++;
            tsel = tpl.selectTemplate();
            textWhere = tpl.enterText();
            toast = tpl.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Template: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=TP") && !fill.contains("Remark=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Select a template
        boolean tselOk = tsel != null && !tsel.startsWith("(");
        step(page, "Select a template", "Select a Template from the dropdown", "A template is selected",
                tsel, tselOk ? "PASS" : "FAIL");

        // 5) Enter text
        boolean textOk = textWhere != null && !textWhere.startsWith("(");
        step(page, "Enter text", "Enter text into the template body", "Text entered",
                "Text entered into: " + textWhere, textOk ? "PASS" : "FAIL");

        // 6) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Template Code", tpl.lastCode);
        addSummary("Remark", tpl.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
