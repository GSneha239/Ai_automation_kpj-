package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PricingPolicyMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Pricing Policy Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, tick a <b>Location Details</b> checkbox.</li>
 *   <li>Select <b>Class</b>, <b>Group</b>, <b>Subgroup</b>, enter <b>Discount</b> → click <b>Add</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class PricingPolicyMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PricingPolicyMaster() { super("ApplicationConfig_Billing_PricingPolicyMaster"); }

    public static void main(String[] args) {
        PricingPolicyMaster t = new PricingPolicyMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Pricing Policy Master", "Application Configuration > Billing > Pricing Policy Master",
                "Add a Pricing Policy: Add, enter Code + Remark + Location, select Class + Group + Subgroup + Discount, Add the detail line, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyMaster pp =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyMaster(page);

        // 1) Navigate
        pp.navigateViaMenu();
        boolean onScreen = pp.onScreen();
        step(page, "Open Pricing Policy Master screen", "Click Application Configuration -> Billing -> Pricing Policy Master",
                "The Pricing Policy Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = pp.clickAdd();
        step(page, "Click Add", "Click Add", "The Pricing Policy add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;
        addSummary("Form controls (after Add)", pp.describeForm());
        System.out.println("PricingPolicyMaster FORM => " + pp.describeForm());

        // 3a) - 4) Header, detail line, Submit — retry with different details on "already exists".
        String hdr = "", det = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            hdr = pp.fillHeader(attempt);
            // The detail line stays in the grid after a refused Submit, so it is added once; a retry only re-enters the header.
            if (attempt == 0) det = pp.addDetailLine();
            used++;
            toast = pp.submitAndGetToast();
            // Drop the toast frame's close "×" and "KPJ Portal" title so the report shows just the message.
            if (toast != null) toast = toast.replaceFirst("^[\\u00d7x\\s]*KPJ\\s*Portal\\s*", "").trim();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("PricingPolicyMaster: attempt " + used + " (" + hdr + ") already exists — changing the details");
        }

        boolean hdrOk = hdr.contains("Code=PP") && !hdr.contains("BasePricingPolicy=(") && !hdr.contains("Location=(none)");
        String hdrActual = hdr + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Description, Base Pricing Policy, Location Details",
                "Enter Code and Description; select Base Pricing Policy; tick a Location Details checkbox",
                "Code / Description / Base Pricing Policy / Location entered", hdrActual, hdrOk ? "PASS" : "FAIL");

        // The line is only real when it reached the grid: every field filled AND the row count went up after Add.
        boolean detOk = det.contains("detail row added") && !det.contains("=(") && !det.contains("Service=(no") && !det.contains("Service=,")
                && !pp.lastService.isEmpty();
        // The screenshot is the one taken BEFORE Add — Add moves the values into the grid and blanks the entry fields.
        step(pp.detailPng != null ? pp.detailPng : page.screenshot(),
                "Select Service Group, Sub - Group, Service, Type; enter Discount Percentage, Hospital Rate; click Add",
                "Select Service Group, Service Sub - Group, Service and Type; enter Discount Percentage and Hospital Rate "
                        + "(Tariff/Payor and Amount fill in by themselves); click Add (AddPolicyDetails)",
                "The detail line is added to the grid", det, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(pp.toastPng != null ? pp.toastPng : page.screenshot(), "Click Submit & success toast",
                "Click Submit (fnSavePricingPolicy); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 5) The Service search only works for a policy that has been saved (the screen has no policy id until the first Submit), so
        // the service line is added by reopening the policy that was just saved, then Submit again.
        boolean ok2 = false;
        String toast2 = "";
        if (ok) {
            String opened = pp.openSavedPolicy(pp.lastCode);
            boolean openOk = opened.startsWith("opened");
            step(page, "Open the saved policy to add its detail line",
                    "Filter the list by the new Code and click Edit",
                    "The policy opens with its id, which switches on the Service search", opened, openOk ? "PASS" : "FAIL");
            if (openOk) {
                String det2 = pp.addDetailLine();
                boolean det2Ok = det2.contains("detail row added") && !pp.lastService.isEmpty() && !det2.contains("=(");
                step(pp.detailPng != null ? pp.detailPng : page.screenshot(),
                        "Select Service Group, Sub - Group, Service, Type; enter Discount Percentage, Hospital Rate; click Add (saved policy)",
                        "Select Service Group, Service Sub - Group, Service and Type; enter Discount Percentage and Hospital Rate; click Add",
                        "The detail line is added to the grid", det2, det2Ok ? "PASS" : "FAIL");
                if (det2Ok) {
                    toast2 = pp.submitAndGetToast();
                    if (toast2 != null) toast2 = toast2.replaceFirst("^[\\u00d7x\\s]*KPJ\\s*Portal\\s*", "").trim();
                    String tl2 = toast2 == null ? "" : toast2.toLowerCase();
                    ok2 = tl2.contains("saved") || tl2.contains("success") || tl2.contains("updated");
                    step(pp.toastPng != null ? pp.toastPng : page.screenshot(), "Click Submit & success toast (with the detail line)",
                            "Click Submit (fnSavePricingPolicy) on the saved policy; wait for the success toast",
                            "'... saved / updated successfully.' toast",
                            toast2 == null || toast2.isEmpty() ? "No toast appeared" : (ok2 ? toast2 : "Save not confirmed — server returned: \"" + toast2 + "\""),
                            ok2 ? "PASS" : "FAIL");
                }
            }
        }

        addSummary("Pricing Policy Code", pp.lastCode);
        addSummary("Description", pp.lastRemark);
        addSummary("Base Pricing Policy", pp.lastBasePolicy);
        addSummary("Service", pp.lastService);
        addSummary("Type / Amount (calculated)", pp.lastType + " / " + pp.lastAmount);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
