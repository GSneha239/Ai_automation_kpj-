package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DrugInstruction — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Drug Instruction</b> — list → Add → 2-field form.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Drug Instruction</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Drug Instruction*</b> (the description).</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code/description already exists,
 *       change the details and Save again.</li>
 * </ol>
 *
 * <p><b>NOTE</b> — this screen has no Diagnosis Code look-up, no inner Add row and no Submit button: the add
 * form is only {@code DrugInstruction.code} + {@code DrugInstruction.description} with a <b>Save</b> button
 * (established by {@code DrugInstructionProbe}).</p>
 */
public class DrugInstruction extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DrugInstruction() { super("ApplicationConfig_Nursing_DrugInstruction"); }

    public static void main(String[] args) {
        DrugInstruction t = new DrugInstruction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Drug Instruction",
                "Application Configuration > Nursing > Drug Instruction",
                "Add a Drug Instruction: Add, enter Code + Drug Instruction, Save; wait for the success toast. "
                        + "On 'already exists', change the details and Save again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DrugInstruction di =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DrugInstruction(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = di.navigateViaMenu();
        String landed = di.currentScreen();
        step(page, "Open Drug Instruction screen", "Application Configuration -> Nursing -> Drug Instruction",
                "The Drug Instruction list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Drug Instruction but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add
        boolean added = di.clickAdd();
        step(page, "Click Add", "Click Add", "The Drug Instruction add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - Add did not open"); return; }

        // 3) + 4) Code / Drug Instruction, Save — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = di.fillDetails(attempt);
            used++;
            toast = di.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("DrugInstruction: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=DI") && !details.contains("=(no)");
        step(page, "Enter Code and Drug Instruction", "Enter Code* (unique) and Drug Instruction*",
                "Both fields are entered",
                details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + di.lastSaveHttp
                                          + "\n" + di.describeState()));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (di.toastPng != null && di.toastPng.length > 0) {
            step(di.toastPng, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", di.lastCode);
        addSummary("Drug Instruction", di.lastDescription);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
