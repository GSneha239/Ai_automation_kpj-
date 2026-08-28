package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LabMachine — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Machine</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Lab Machine</b>.</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Enter <b>Machine Para. Code</b>, <b>Machine Para. Name</b>, <b>Parameter Name</b>.</li>
 *   <li>Click <b>Add</b> to append the parameter row.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code already exists, change the
 *       details and Submit again.</li>
 * </ol>
 */
public class LabMachine extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public LabMachine() { super("ApplicationConfig_Investigation_LabMachine"); }

    public static void main(String[] args) {
        LabMachine t = new LabMachine();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Lab Machine",
                "Application Configuration > Investigation > Lab Machine",
                "Add a Lab Machine: Add, enter Code + Remark, enter Machine Para. Code / Machine Para. Name / "
                        + "Parameter Name, click Add to append the parameter row, then Submit and wait for the "
                        + "success toast. On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabMachine lm =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabMachine(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = lm.navigateViaMenu();
        String landed = lm.currentScreen();
        step(page, "Open Lab Machine screen", "Application Configuration -> Investigation -> Lab Machine",
                "The Lab Machine screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Lab Machine but the app opened: " + landed + "\n" + lm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = lm.sampleCodeStyle();
        System.out.println("LabMachine code style => " + codeStyle);

        // 2) Add (inline-add screens already show the form).
        String addHow = lm.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Lab Machine form", "The Lab Machine form is shown",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("LabMachine form => " + lm.describeForm());

        // 3) .. 6) Code / Remark -> parameter line -> inner Add -> Submit; retry on "already exists".
        String master = "", param = "", rowAdd = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            master = lm.fillMaster(attempt);
            param = lm.fillParameter(attempt);
            rowAdd = lm.clickAddDetail();
            used++;
            toast = lm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("LabMachine: attempt " + used + " (" + master + ") already exists — changing the details");
        }

        boolean masterOk = !master.contains("(no field)") && !master.contains("Code=(");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                master + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                masterOk ? "PASS" : "FAIL");

        boolean paramOk = !param.contains("(no field)");
        step(page, "Enter the machine parameter",
                "Enter Machine Para. Code, Machine Para. Name and Parameter Name",
                "The three parameter fields are entered", param, paramOk ? "PASS" : "FAIL");

        // The detail line only reaches the server once the inner Add has appended it to the list.
        boolean rowOk = rowAdd.contains("row appended");
        step(page, "Click Add (append the parameter row)", "Click Add under the parameter fields",
                "The parameter row is appended to the list",
                rowOk ? rowAdd : rowAdd + "\n" + lm.describeForm(), rowOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + lm.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + lm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (lm.toastPng != null && lm.toastPng.length > 0) {
            step(lm.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Lab Machine Code", lm.lastCode);
        addSummary("Remark", lm.lastRemark);
        addSummary("Machine Para. Code", lm.lastParaCode);
        addSummary("Machine Para. Name", lm.lastParaName);
        addSummary("Parameter Name", lm.lastParameterName);
        addSummary("Field models", lm.lastCodeModel + " / " + lm.lastRemarkModel);
        addSummary("Parameter row", rowAdd);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
