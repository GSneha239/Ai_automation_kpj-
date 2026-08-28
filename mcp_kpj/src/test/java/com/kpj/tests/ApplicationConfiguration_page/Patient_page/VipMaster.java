package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VipMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>VIP Master</b> ({@code #/VIPMaster}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>VIP Master</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddVIPMaster}) — the form opens in place (the route does not change).</li>
 *   <li>Fill all the details (Code, Name, NRIC, Passport, Gender, Marital Status, Religion, Mobile,
 *       Designation, Expiry Date, VIP Category).</li>
 *   <li>Click <b>Save</b> ({@code fnSaveVIPMaster}); wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class VipMaster extends DevHisBase {

    public VipMaster() { super("ApplicationConfig_Patient_VIPMaster"); }

    public static void main(String[] args) {
        VipMaster t = new VipMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - VIP Master",
                "Application Configuration > Patient > VIP Master",
                "Add a VIP: Add, fill all the details, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.VipMaster vip =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.VipMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = vip.navigateViaMenu();
        String landed = vip.currentScreen();
        step(page, "Open VIP Master screen", "Application Configuration -> Patient -> VIP Master",
                "The VIP Master list screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected VIP Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean added = vip.clickAdd();
        step(page, "Click Add", "Click Add (AddVIPMaster) — the VIP form opens in place",
                "The VIP add form is shown",
                added ? "VIP form opened" : "VIP form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String details = vip.fillDetails();
        boolean detOk = details.contains("Code=VIP") && !details.contains("=(no)") && !details.contains("=(no-opt)");
        step(page, "Fill all the details",
                "Code*, Name*, NRIC*, Passport*, Gender*, Marital Status, Religion, Mobile No*, Designation, Expiry Date, "
                        + "VIP Category, Organization, DOB*, Address*, Post Code*, Remark*, State*, City*",
                "Every field on the VIP form is filled", details, detOk ? "PASS" : "FAIL");

        String dep = vip.fillDependentAndAdd();
        boolean depOk = dep.contains("->") && !dep.endsWith("-> 0") && !dep.contains("=(no)") && !dep.contains("=(no-opt)");
        // Evidence screenshot is the one taken BEFORE Add — Add moves the values into the grid and blanks the
        // entry fields, so a screenshot taken afterwards shows them empty.
        if (vip.dependentPng != null && vip.dependentPng.length > 0) {
            step(vip.dependentPng, "Fill VIP Family / Dependent Information",
                    "Enter the dependent's Name*, Relationship*, Date Of Birth*, NRIC*, Passport* and Remark*, then click Add (addDependent)",
                    "The dependent details are entered and the row is added to the VIP Family grid", dep,
                    depOk ? "PASS" : "FAIL");
        } else {
            step(page, "Fill VIP Family / Dependent Information",
                    "Enter the dependent's Name*, Relationship*, Date Of Birth*, NRIC*, Passport* and Remark*, then click Add (addDependent)",
                    "The dependent details are entered and the row is added to the VIP Family grid", dep,
                    depOk ? "PASS" : "FAIL");
        }

        String toast = vip.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (vip.toastPng != null && vip.toastPng.length > 0) {
            step(vip.toastPng, "Click Save & success toast", "Click Save (fnSaveVIPMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast", "Click Save (fnSaveVIPMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("VIP Code", vip.lastCode);
        addSummary("VIP Name", vip.lastName);
        addSummary("NRIC", vip.lastNric);
        addSummary("Route", "#/VIPMaster (Add opens the form in place)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
