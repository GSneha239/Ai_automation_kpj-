package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ConsultationRoom — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Consultation Room</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Location</b> → <b>Consultation Room</b>.</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>, <b>matching the format the table already uses</b> — the list grid
 *       is read first and its sequence continued.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the details already exist, move
 *       further along the sequence and Submit again.</li>
 * </ol>
 */
public class ConsultationRoom extends DevHisBase {

    /** How many times to move further along the sequence when the server says the record already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ConsultationRoom() { super("ApplicationConfig_Location_ConsultationRoom"); }

    public static void main(String[] args) {
        ConsultationRoom t = new ConsultationRoom();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Consultation Room",
                "Application Configuration > Location > Consultation Room",
                "Add a Consultation Room: Add, enter Code + Remark in the SAME format the existing table rows use, Submit; "
                        + "wait for the success toast. On 'already exists', move along the sequence and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.ConsultationRoom rn =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.ConsultationRoom(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = rn.navigateViaMenu();
        String landed = rn.currentScreen();
        step(page, "Open Consultation Room screen", "Application Configuration -> Location -> Consultation Room",
                "The Consultation Room screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Consultation Room but the app opened: " + landed + "\n" + rn.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // #/CABIN is a generic CommonMaster screen — the Form Name drop-down chooses WHICH master it edits, and
        // the page header reads "ROOM NO". Set it explicitly rather than trusting the default.
        String formName = rn.selectFormName("Room No");
        step(page, "Select the Form Name", "Set Form Name to \"Room No\"",
                "The screen is editing the Room No master", formName,
                formName.startsWith("Form Name = Room No") ? "PASS" : "FAIL");

        // Read the table BEFORE Add — the new row has to look like the rows already there. The grid is filled by
        // its own call, so wait for a row first or the sample reads an empty grid.
        // NB: do NOT widen "items per page" first — switching the grid to 125 refetches it and it never
        // repopulates, so the table reads as empty. The first page is enough: a code that collides is handled by
        // moving onto a different letter series, not by needing the table's true maximum.
        // NOT a reported step: reading the table is how the values are chosen, not something the flow asks a
        // tester to do. It goes to the console and to the summary instead.
        String pageSize = rn.resetGridPageSize();
        String pattern = pageSize + " | " + rn.sampleTablePatternPolling(4);
        System.out.println("ConsultationRoom table => " + pattern);

        // 2) Add (inline-add screens already show the form).
        String addHow = rn.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Consultation Room form", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("ConsultationRoom form => " + rn.describeForm());

        // 3) + 4) Code / Remark, Submit — move along the sequence on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = rn.fillDetails(attempt);
            used++;
            toast = rn.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            // "succes" (one s) is deliberate — sibling screens here return "Record added succesfully".
            ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("ConsultationRoom: attempt " + used + " (" + details + ") already exists — moving along the sequence");
        }

        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* and Remark* in the same format as the existing rows",
                "Code and Remark are entered and match the table's format",
                details + (used > 1 ? "  [moved " + (used - 1) + "x along the sequence after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + rn.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s): \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + rn.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (rn.toastPng != null && rn.toastPng.length > 0) {
            step(rn.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' move along the sequence and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' move along the sequence and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Consultation Room Code", rn.lastCode);
        addSummary("Remark", rn.lastRemark);
        addSummary("Copied from the table", rn.patternNote);
        addSummary("Field models", rn.lastCodeModel + " / " + rn.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
