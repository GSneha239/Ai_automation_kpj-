package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RedCellSerologyMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Red Cell Serology Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Red Cell Serology Master</b>, click
 *       <b>Add</b>.</li>
 *   <li>Select <b>Blood Group</b>.</li>
 *   <li>Select <b>Red Cell Serology Group</b> + <b>Result</b> for <b>Cell Grouping</b>; click that section's
 *       <b>Add</b>.</li>
 *   <li>Select <b>Red Cell Serology Group</b> + <b>Result</b> for <b>Serum Grouping</b>; click that section's
 *       <b>Add</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class RedCellSerologyMaster extends DevHisBase {

    /**
     * How many times to re-pick a Blood Group when the server says a Red Cell Serology Master row for that
     * Blood Group already exists. This screen has no Code/Name field, so the Blood Group selection is the only
     * thing a retry can vary — capped near the number of real Blood Group options (A+, A-, B+, B-, O+, O-, AB+,
     * AB-) since cycling past that just repeats earlier picks.
     */
    private static final int MAX_ATTEMPTS = 40;

    public RedCellSerologyMaster() { super("ApplicationConfig_BloodBank_RedCellSerologyMaster"); }

    public static void main(String[] args) {
        RedCellSerologyMaster t = new RedCellSerologyMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Red Cell Serology Master",
                "Application Configuration > Blood Bank > Red Cell Serology Master",
                "Add: select Blood Group, add a Cell Grouping line (Group + Result), add a Serum Grouping "
                        + "line (Group + Result), Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.RedCellSerologyMaster rcm =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.RedCellSerologyMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = rcm.navigateViaMenu();
        String landed = rcm.currentScreen();
        step(page, "Open Red Cell Serology Master screen", "Application Configuration -> Blood Bank -> Red Cell Serology Master",
                "The Red Cell Serology Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Red Cell Serology Master but the app opened: " + landed + "\n" + rcm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add.
        String addHow = rcm.clickAdd();
        step(page, "Click Add", "Click Add to open the Red Cell Serology Master form", "The form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Select Blood Group, 4) Cell Grouping, 5) Serum Grouping, 6) Submit — this screen has no Code/Name
        // field, so a retry on "already exists" (that Blood Group is already configured) cycles the Blood
        // Group selection instead; the Cell/Serum grouping lines only need to be entered once (attempt 0).
        String bg = "", cell = "", serum = "", toast = "";
        byte[] cellPng = null, serumPng = null;
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            bg = rcm.selectBloodGroup(attempt);
            used++;
            if (attempt == 0) {
                cell = rcm.fillGroupingSection("cell\\s*grouping");
                try { cellPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("RedCellSerologyMaster: cell grouping screenshot failed - " + e.getMessage()); }

                serum = rcm.fillGroupingSection("serum\\s*grouping");
                try { serumPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("RedCellSerologyMaster: serum grouping screenshot failed - " + e.getMessage()); }
            }
            toast = rcm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("RedCellSerologyMaster: attempt " + used + " (BloodGroup=" + bg + ") already exists — picking a different Blood Group");
        }

        boolean bgOk = !bg.startsWith("(not found)") && !bg.startsWith("(no-options)");
        String bgActual = bg + (used > 1 ? "  [Blood Group changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Select Blood Group", "Select the Blood Group dropdown", "A Blood Group is selected",
                bgActual + (bgOk ? "" : "\n" + rcm.describeForm()), bgOk ? "PASS" : "FAIL");

        boolean cellOk = cell.contains("Add=clicked") && !cell.contains("(not found)") && !cell.contains("(no-options)") && !cell.contains("not found)");
        if (cellPng != null && cellPng.length > 0) {
            step(cellPng, "Select Red Cell Serology Group + Result for Cell Grouping, click Add",
                    "Select Group and Result within the Cell Grouping section; click that section's Add",
                    "The Cell Grouping line is added", cell + (cellOk ? "" : "\n" + rcm.describeForm()), cellOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Red Cell Serology Group + Result for Cell Grouping, click Add",
                    "Select Group and Result within the Cell Grouping section; click that section's Add",
                    "The Cell Grouping line is added", cell + (cellOk ? "" : "\n" + rcm.describeForm()), cellOk ? "PASS" : "FAIL");
        }

        boolean serumOk = serum.contains("Add=clicked") && !serum.contains("(not found)") && !serum.contains("(no-options)") && !serum.contains("not found)");
        if (serumPng != null && serumPng.length > 0) {
            step(serumPng, "Select Red Cell Serology Group + Result for Serum Grouping, click Add",
                    "Select Group and Result within the Serum Grouping section; click that section's Add",
                    "The Serum Grouping line is added", serum + (serumOk ? "" : "\n" + rcm.describeForm()), serumOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Red Cell Serology Group + Result for Serum Grouping, click Add",
                    "Select Group and Result within the Serum Grouping section; click that section's Add",
                    "The Serum Grouping line is added", serum + (serumOk ? "" : "\n" + rcm.describeForm()), serumOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + rcm.describeForm()
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different Blood Groups: \"" + toast + "\""
                                         : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + rcm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (rcm.toastPng != null && rcm.toastPng.length > 0) {
            step(rcm.toastPng, "Click Submit & success toast", "Click Submit; on 'already exists' pick a different Blood Group and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; on 'already exists' pick a different Blood Group and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Blood Group", rcm.lastBloodGroup);
        addSummary("Cell Grouping", cell);
        addSummary("Serum Grouping", serum);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
