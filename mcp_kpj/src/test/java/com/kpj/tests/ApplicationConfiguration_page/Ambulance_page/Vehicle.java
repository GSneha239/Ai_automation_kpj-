package com.kpj.tests.ApplicationConfiguration_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Vehicle — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Ambulance &gt; <b>Vehicle</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Ambulance</b> → <b>Vehicle</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Vehicle Type</b>, <b>Vehicle Name</b>, <b>Vehicle Make</b>, <b>Vehicle No</b>,
 *       <b>Chassis No</b>, <b>Invoice No</b>, <b>Purchase Amount</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the vehicle no/name already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Vehicle extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the vehicle no / name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Vehicle() { super("ApplicationConfig_Ambulance_Vehicle"); }

    public static void main(String[] args) {
        Vehicle t = new Vehicle();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Ambulance - Vehicle", "Application Configuration > Ambulance > Vehicle",
                "Add a Vehicle: Add, enter Vehicle Type + Name + Make + No + Chassis No + Invoice No + Purchase Amount, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Ambulance_page.Vehicle veh =
                new com.kpj.pages.ApplicationConfiguration_page.Ambulance_page.Vehicle(page);

        // 1) Navigate
        veh.navigateViaMenu();
        boolean onScreen = veh.onScreen();
        step(page, "Open Vehicle screen", "Click Application Configuration -> Ambulance -> Vehicle",
                "The Vehicle screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = veh.clickAdd();
        step(page, "Click Add", "Click Add", "The Vehicle add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter the fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = veh.fillDetails(attempt);
            used++;
            toast = veh.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Vehicle: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("No=VH") && !fill.contains("Name=(no")
                && !fill.contains("Invoice=(no") && !fill.contains("Amount=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Vehicle details",
                "Enter Vehicle Type, Vehicle Name, Vehicle Make, Vehicle No, Chassis No, Invoice No, Purchase Amount",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Submit -> success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDVehicle); on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Vehicle No", veh.lastVehicleNo);
        addSummary("Vehicle Name", veh.lastVehicleName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
