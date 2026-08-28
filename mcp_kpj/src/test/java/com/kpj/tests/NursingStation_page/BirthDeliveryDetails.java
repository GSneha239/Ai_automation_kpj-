package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BirthDeliveryDetails — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Birth Delivery Details</b> — fill the mandatory Newborn Registration details, Add, Submit.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Birth Delivery Details</b>.</li>
 *   <li>(Enter mother's MRN.) Fill the mandatory Newborn Registration fields.</li>
 *   <li>Click <b>Add</b>, then <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class BirthDeliveryDetails extends DevHisBase {

    public BirthDeliveryDetails() { super("NursingStation_BirthDeliveryDetails"); }

    public static void main(String[] args) {
        BirthDeliveryDetails t = new BirthDeliveryDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Birth Delivery Details", "Nursing Station > Birth Delivery Details",
                "Open Birth Delivery Details, fill the mandatory Newborn Registration details, Add, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.BirthDeliveryDetails bd =
                new com.kpj.pages.NursingStation_page.BirthDeliveryDetails(page);

        boolean onScreen = bd.navigateTo(BASE);
        step(page, "Open Birth Delivery Details screen", "Nursing Station -> Birth Delivery Details",
                "The Birth Delivery Details screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter mother's MRN (best-effort; the newborn is registered against the mother).
        String[] candidates = { "100000273", "100000025", "100000034", "100000990", "100001062" };
        String mrn = "";
        for (String c : candidates) { if (bd.enterMrn(c)) { mrn = c; break; } }
        step(page, "Enter mother's MRN", "Type the MRN and click search (SearchPatientByMRNo)",
                "The mother patient is loaded by MRN",
                mrn.isEmpty() ? "No MRN loaded (proceeding — MRN may be optional)" : "Loaded MRN " + mrn,
                mrn.isEmpty() ? "WARN" : "PASS");

        String fill = bd.fillNewbornRegistration();
        boolean fillOk = !fill.contains("Gender=(no") && !fill.contains("Dept=(no") && fill.contains("Name=BABY");
        step(page, "Fill mandatory Newborn Registration", "Name, Date of Birth, To Birth Time, Gender, Birth Weight, Department, Paediatrician, Paediatric Doctor/Nurse",
                "The mandatory Newborn Registration fields are filled", fill, fillOk ? "PASS" : "FAIL");

        String add = bd.clickAdd();
        boolean addBlocked = add.toLowerCase().matches(".*(please|enter|select|required|mandatory|fill).*");
        step(page, "Click Add", "Click Add (AddFileDetails)",
                "The newborn entry is added", add, addBlocked ? "FAIL" : "PASS");

        String toast = bd.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("report");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit (IUDSaveReport); wait for the success toast",
                "A success toast appears", actual, ok ? "PASS" : "FAIL");

        addSummary("Mother MRN", bd.lastMrn);
        addSummary("Baby Name", bd.lastBaby);
        addSummary("Department", bd.lastDept);
        addSummary("Paediatrician", bd.lastDoctor);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
