package com.kpj.tests.ApplicationConfiguration_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VehicleType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Ambulance &gt; <b>Vehicle Type</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Ambulance</b> → <b>Vehicle Type</b>.</li>
 *   <li>Enter <b>Code</b> and <b>Remark</b> (inline-add: the fields are on the list page, there is no Add).</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class VehicleType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public VehicleType() { super("ApplicationConfig_Ambulance_VehicleType"); }

    public static void main(String[] args) {
        VehicleType t = new VehicleType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Ambulance - Vehicle Type",
                "Application Configuration > Ambulance > Vehicle Type",
                "Add a Vehicle Type: enter Code + Remark, Submit, expect the success toast.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Ambulance_page.VehicleType vt =
                new com.kpj.pages.ApplicationConfiguration_page.Ambulance_page.VehicleType(page);

        // 1) Navigate. Judged on the FORM being there, not on the URL — the hash reads #/Vehicletype on KS and
        //    DSH while the screen renders nothing, and a URL check would call that a pass.
        boolean onScreen = vt.navigateViaMenu();
        step(page, "Open Vehicle Type screen",
                "Click Application Configuration -> Ambulance -> Vehicle Type",
                "The Vehicle Type screen is shown (its Code field is present)",
                onScreen ? "Opened " + page.url()
                         : "Screen did NOT render — " + vt.describeScreen(),
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            addSummary("Result", "FAILED — the Vehicle Type route rendered no form on this environment");
            return;
        }

        // 2) The form must offer what the flow asks for. Every run so far renders ONLY Code and Store — no Remark
        //    control at all — so state that as its own failure instead of letting it surface as a vague fill error.
        String shape = vt.describeScreen();
        boolean hasRemark = vt.hasRemarkField();
        step(page, "Verify the form offers Code and Remark",
                "The Vehicle Type form must provide both a Code and a Remark field",
                "Code and Remark are both present",
                hasRemark ? "Code and Remark are both present — " + shape
                          : "DEFECT: the screen offers only Code and Store — there is NO Remark field — " + shape,
                hasRemark ? "PASS" : "FAIL");

        // 3) Code + Remark (inline-add — no Add button on this screen), Submit — retry with different details
        // on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = vt.fillDetails(attempt);
            used++;
            // Submit -> toast. Run it even when the Remark is missing: what the screen answers is the evidence for
            // whether that field is genuinely required here.
            toast = vt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.matches(".*(saved|added|success).*");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("VehicleType: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill.contains("Code=VT") && !fill.contains("Remark=(NOT set");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code and Remark", "Enter the Code and the Remark",
                "Both Code and Remark are accepted", fillActual, fillOk ? "PASS" : "FAIL");
        addSummary("Vehicle Type · Code", vt.lastCode);
        addSummary("Vehicle Type · Remark", vt.lastRemark);
        addSummary("Vehicle Type · Attempts", String.valueOf(used));

        String actual = toast == null || toast.isEmpty() ? "No toast appeared — the Submit raised no message at all"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\"" : toast));
        step(page, "Click Submit", "Click Submit; on 'already exists' change the details and Submit again",
                "A success toast is shown", actual, ok ? "PASS" : "FAIL");

        addSummary("Vehicle Type · Result", ok ? toast : "Not saved — " + (toast == null || toast.isEmpty() ? "no toast" : toast));
    }
}
