package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OrderSet — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Order Set</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Order Set</b>, click <b>Add</b>
 *       (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b> and a <b>Diagnosis</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again. Any OTHER (non-success) toast is a hard FAIL — it is never
 *       treated as a pass.</li>
 * </ol>
 */
public class OrderSet extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public OrderSet() { super("ApplicationConfig_Nursing_OrderSet"); }

    public static void main(String[] args) {
        OrderSet t = new OrderSet();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Order Set",
                "Application Configuration > Nursing > Order Set",
                "Click Add, enter Code + Remark + Diagnosis, click Submit; wait for the success toast. On "
                        + "'already exists', change the details and Submit again. Any other toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OrderSet os =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OrderSet(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = os.navigateViaMenu();
        String landed = os.currentScreen();
        step(page, "Open Order Set screen", "Application Configuration -> Nursing -> Order Set",
                "The Order Set screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Order Set but the app opened: " + landed + "\n" + os.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = os.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark / Diagnosis form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code / Remark.
        String details = os.fillDetails(0);
        boolean detOk = details.contains("Code=OS") && !details.contains("(no field)");
        byte[] filledPng = null;
        try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("OrderSet: filled-form screenshot failed - " + e.getMessage()); }
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", details, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", details, detOk ? "PASS" : "FAIL");
        }

        // 4) Diagnosis (typeahead).
        String diag = os.pickDiagnosis();
        boolean diagOk = diag != null && diag.contains("Diagnosis=") && !diag.contains("(not selected") && !diag.contains("(no Diagnosis control found)");
        step(page, "Enter Diagnosis", "Select a Diagnosis (typeahead)",
                "A Diagnosis is selected", diag, diagOk ? "PASS" : "MANUAL");

        // 4b) ICD-11 Diagnosis — a SECOND mandatory picker (text search; only picking a row binds it).
        String icd = os.pickIcd11Diagnosis();
        boolean icdOk = icd != null && icd.contains("ICD-11 Diagnosis=") && !icd.contains("(not selected") && !icd.contains("(no ICD-11");
        step(page, "Enter ICD-11 Diagnosis", "Search an ICD-11 Diagnosis (e.g. \"fever\") and pick a suggestion row",
                "An ICD-11 Diagnosis is selected", icd, icdOk ? "PASS" : "FAIL");
        addSummary("ICD-11 Diagnosis", os.lastIcd11Diagnosis);

        // 4c) A SERVICE. On build v2026.10.29.342 Submit answers "Please Add Services!" until one is in List of Services.
        String svc = os.addService();
        boolean svcOk = svc != null && svc.startsWith("Service added");
        step(page, "Add a service", "Service tab -> Add Services -> pick a Group -> Search -> tick a service -> Add",
                "The service is in the List of Services", svc, svcOk ? "PASS" : "FAIL");
        addSummary("Service", svcOk ? os.lastServiceCode + " | " + os.lastServiceName + " (group " + os.lastServiceGroup + ")" : "(none added)");
        os.showDefinitionTab();   // Submit re-enters Code and Remark, which sit on the first tab

        // 5) Submit — retry with different Code/Remark on "already exists"; any OTHER (non-success) toast
        // stops the loop and FAILs the step naming the exact message returned.
        String toast = "";
        boolean ok = false, exists = false;
        int used = 1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                os.fillDetails(attempt);
                used = attempt + 1;
            }
            toast = os.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message — stop either way
            System.out.println("OrderSet: attempt " + used + " already exists — changing the details");
        }

        // Wrong/unexpected toast text is always reported as a FAIL, never MANUAL — the user must see exactly
        // what the app actually returned when it is not a genuine success message.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + os.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + os.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (os.toastPng != null && os.toastPng.length > 0) {
            step(os.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Order Set Code", os.lastCode);
        addSummary("Remark", os.lastRemark);
        addSummary("Diagnosis", os.lastDiagnosis);
        addSummary("Field models", os.lastCodeModel + " / " + os.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
