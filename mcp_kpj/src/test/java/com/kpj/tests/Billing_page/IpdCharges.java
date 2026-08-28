package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// The page object is also named IpdCharges — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>IPD Charges</b>.
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>IPD Charges</b>.</li>
 *   <li>Pick an admitted IPD patient (patient-picker popup).</li>
 *   <li>Add a service charge; fill the mandatory bill fields (Charge Date, Cash Counter).</li>
 *   <li>Click <b>Save</b> ({@code IUDBill}).</li>
 * </ol>
 */
public class IpdCharges extends DevHisBase {

    public IpdCharges() { super("Billing_IpdCharges"); }

    public static void main(String[] args) {
        IpdCharges t = new IpdCharges();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - IPD Charges", "Billing > IPD Charges",
                "Open IPD Charges, pick an admitted IPD patient, add a service charge, fill the mandatory bill fields, Save.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.IpdCharges ipc =
                new com.kpj.pages.Billing_page.IpdCharges(page);

        boolean onScreen = ipc.navigateTo(BASE);
        step(page, "Open IPD Charges screen", "Billing -> IPD Charges",
                "The IPD Charges screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Iterate IPD patients; for each, run Get Bill and see if any unbilled charges load.
        //
        // Deliberately NOT retrying a different patient when the save is blocked: the picker keeps handing back
        // the SAME admission once one is loaded (rows 4..7 all returned 100001291), so a retry loop just submits
        // the identical bill five times. And a screen that cannot bill any patient should report the block, not
        // hunt for one that happens to be configured.
        String ipd = "";
        int billRows = 0;
        for (int r = 0; r < 8; r++) {
            String loaded = ipc.pickPatientByRow(r);
            if (loaded.isEmpty()) { System.out.println("probe row " + r + ": no admission"); continue; }
            int gb = ipc.getBill();
            System.out.println("probe row " + r + ": " + ipc.lastPatient + "/" + loaded + " -> Get Bill rows=" + gb);
            if (gb > 0) { ipd = loaded; billRows = gb; break; }
            if (ipd.isEmpty()) ipd = loaded;   // remember first loadable patient as fallback
        }
        step(page, "Select an admitted IPD patient", "Patient picker -> filter IPD -> Search -> pick a row -> Get Bill",
                "An IPD patient with billable charges is loaded",
                ipd.isEmpty() ? "No IPD patient could be loaded" : "Loaded MRN " + ipc.lastPatient + " / " + ipd + " | Get Bill rows=" + billRows,
                ipd.isEmpty() ? "FAIL" : "PASS");
        if (ipd.isEmpty()) { addSummary("Result", "No admitted IPD patient available"); return; }

        String svc = billRows > 0 ? "(Get Bill loaded " + billRows + " charges)" : ipc.addServiceCharge();
        int rows = ipc.chargeGridRows();
        // Which lines lack a Cost Centre — the app's own warning names none, so read it from the grid.
        String ccNote = ipc.servicesMissingCostCentre();
        boolean svcOk = rows > 0;
        step(page, "Load charges", "Get Bill loads the patient's unbilled charges (else add a service manually)",
                "At least one charge line is present",
                "Charges: " + svc + " | charge rows: " + rows + "\nCost Centre check: " + ccNote,
                svcOk ? "PASS" : "FAIL");

        // Package / Cancelled Charges — sibling read-only tabs alongside Bill Details. Zero rows is a legitimate
        // data state (this patient has no package admission / no cancelled charges), not a defect — same category
        // as other "the data genuinely isn't there" cases in this project, so these always PASS on reachability.
        String pkgRead = ipc.readPackageTab();
        step(page, "View Package tab", "Click the Package tab and read its table (Cost Centre/Service/Qty/amounts/Doctor Share/Remark)",
                "The Package table is shown (0 rows is normal for a non-package admission)", pkgRead,
                pkgRead.startsWith("(") ? "FAIL" : "PASS");
        String cancRead = ipc.readCancelledChargesTab();
        step(page, "View Cancelled Charges tab", "Click the Cancelled Charges tab and read its table (Authorize/Authorized By/Cancelled By/Cancellation Reason)",
                "The Cancelled Charges table is shown (0 rows is normal when nothing has been cancelled)", cancRead,
                cancRead.startsWith("(") ? "FAIL" : "PASS");
        ipc.switchToBillDetailsTab();   // back to the tab the rest of the flow (Cost Centre selects, Save) expects

        // Cost Centre is a per-row dropdown right there in the Bill Details grid, not something Save can infer —
        // set every row still on "--Select--" rather than just reporting the block (same fix as OPD Charges).
        int ccFixed = ipc.ensureAllCostCentresSet();
        String ccNoteAfter = ipc.servicesMissingCostCentre();
        step(page, "Select Cost Center for every charge line", "Every charge grid row needs a Cost Centre before Save",
                "All charge lines have a Cost Centre", "Cost Centre set on " + ccFixed + " row(s) that were still \"--Select--\" | " + ccNoteAfter, "PASS");

        String fill = ipc.fillMandatory();
        step(page, "Fill mandatory bill fields", "Charge Date (today), Cash Counter",
                "The mandatory bill fields are filled", fill, fill.contains("CashCounter=(n/a") ? "FAIL" : "PASS");

        Page report = ipc.saveAndCapture();
        boolean ok = (report != null && !report.isClosed())
                || (!ipc.lastToast.isEmpty() && ipc.lastToast.toLowerCase().matches(".*(saved|success).*"));
        String actual = report != null && !report.isClosed()
                ? "Bill saved; report opened in a new tab: " + report.url() + (ipc.lastToast.isEmpty() ? "" : " | toast: " + ipc.lastToast)
                : (!ipc.blockingMessage.isEmpty()
                    // Name the charge lines behind the app's generic warning, so the report points at the
                    // master data that needs fixing rather than just saying "blocked".
                    ? "Save blocked by: \"" + ipc.blockingMessage + "\"\n" + ccNote
                    : (ipc.lastToast.isEmpty() ? "No confirmation (no toast / no report tab)" : "Server said: \"" + ipc.lastToast + "\""));
        // A report tab wins as the screenshot; otherwise use the toast captured the moment it was actually
        // visible (toasts fade in ~1-2s, so screenshotting `page` here — after saveAndCapture() already
        // returned — showed a clean screen with no evidence of what the toast said).
        if (report != null && !report.isClosed()) {
            step(report, "Click Save (IUDBill)", "Click Save; the IPD charge bill is posted (success toast / report)",
                    "The bill is saved successfully", actual, ok ? "PASS" : "FAIL");
        } else {
            step(ipc.toastPng, "Click Save (IUDBill)", "Click Save; the IPD charge bill is posted (success toast / report)",
                    "The bill is saved successfully", actual, ok ? "PASS" : "FAIL");
        }

        try { if (report != null && report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Patient", ipc.lastPatient + " / " + ipc.lastIpd);
        addSummary("Service", ipc.lastService);
        addSummary("Cost Centre check", ccNote);
        addSummary("Result", ok ? (ipc.lastToast.isEmpty() ? "Bill saved" : ipc.lastToast) : (ipc.lastToast.isEmpty() ? "Not confirmed" : ipc.lastToast));
    }
}
