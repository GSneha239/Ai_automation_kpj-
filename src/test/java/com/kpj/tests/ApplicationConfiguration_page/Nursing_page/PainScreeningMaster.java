package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PainScreeningMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Pain Screening Master</b> ({@code #/PainScreeingList} — the app's
 * own misspelling).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>PainScreeingMaster</b>.</li>
 *   <li>Click <b>Add</b> ({@code addPainScreeingMaster}).</li>
 *   <li>Enter <b>Code</b>, <b>Category</b> and <b>Description</b>, plus <b>Category Description</b> and
 *       <b>Scale</b>.</li>
 *   <li>Click the inner <b>Add</b> to commit the detail row.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message. If the message says the code/description already
 *       exists, change the details (re-fill + re-commit the detail row) and Submit again.</li>
 * </ol>
 */
public class PainScreeningMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PainScreeningMaster() { super("ApplicationConfig_Nursing_PainScreeningMaster"); }

    public static void main(String[] args) {
        PainScreeningMaster t = new PainScreeningMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Pain Screening Master",
                "Application Configuration > Nursing > Pain Screening Master",
                "Add a Pain Screening: click Add, enter Code + Category + Description and the Category Description / Scale line, click Add, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PainScreeningMaster ps =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PainScreeningMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = ps.navigateViaMenu();
        String landed = ps.currentScreen();
        step(page, "Open Pain Screening Master screen",
                "Application Configuration -> Nursing -> PainScreeingMaster",
                "The Pain Screening Master list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Pain Screening Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = ps.clickAdd() || ps.onAddForm();
        step(page, "Click Add", "Click Add (addPainScreeingMaster) to open the entry form",
                "The Pain Screening entry form is shown",
                addOk ? "Add form opened - " + ps.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + ps.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + ps.currentScreen() + ")"); return; }

        // Enter fields + commit the detail row + Submit — retry with different details on "already exists".
        String details = "", added = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ps.fillDetails(attempt);
            used++;
            added = ps.clickInnerAdd();
            toast = ps.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("PainScreeningMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=PS") && !details.contains("(no field)") && !details.contains("(no option)");
        String detActual = details + (detOk ? "" : " || form offered: " + ps.describeForm())
                + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Category, Description, Category Description and Scale",
                "Enter Code (unique), Category and Description, then the Category Description and Scale line",
                "All the entry fields are filled", detActual, detOk ? "PASS" : "FAIL");

        boolean addRowOk = added.startsWith("Add clicked") && !added.contains("NO detail row");
        step(page, "Click Add (commit the detail row)",
                "Click the inner Add to commit the Category Description / Scale line",
                "The detail row is added to the table", added, addRowOk ? "PASS" : "FAIL");

        // The message itself is the assertion: ONLY a success toast passes.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (ps.toastPng != null && ps.toastPng.length > 0) {
            step(ps.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", ps.lastCode);
        addSummary("Category", ps.lastCategory);
        addSummary("Description", ps.lastDescription);
        addSummary("Category Description", ps.lastCatDescription);
        addSummary("Scale", ps.lastScale);
        addSummary("Route", "#/PainScreeingList");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
