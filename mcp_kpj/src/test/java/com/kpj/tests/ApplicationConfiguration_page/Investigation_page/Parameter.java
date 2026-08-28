package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Parameter — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Parameter</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Parameter</b>.</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li><b>Parameter Details</b>: Parameter Code, Parameter Name, Print Name, Parameter Unit,
 *       Parameter SSI Unit, LOINC Code, Conver. Factor.</li>
 *   <li><b>Age Wise Range</b>: the age band, Gender, Lower Value / Alert Low / Critical Low and
 *       Upper Value / Alert High / Critical High.</li>
 *   <li>Click <b>Add</b> to append the range row.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the details already exist, change
 *       them and Submit again.</li>
 * </ol>
 */
public class Parameter extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the record already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Parameter() { super("ApplicationConfig_Investigation_Parameter"); }

    public static void main(String[] args) {
        Parameter t = new Parameter();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Parameter",
                "Application Configuration > Investigation > Parameter",
                "Add a Parameter: Add, fill Parameter Details (Code, Name, Print Name, Unit, SSI Unit, LOINC, "
                        + "Conver. Factor), fill the Age Wise Range (Gender + the six limits), click Add to append "
                        + "the range row, then Submit and wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Parameter pr =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Parameter(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = pr.navigateViaMenu();
        String landed = pr.currentScreen();
        step(page, "Open Parameter screen", "Application Configuration -> Investigation -> Parameter",
                "The Parameter screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Parameter but the app opened: " + landed + "\n" + pr.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = pr.sampleCodeStyle();
        System.out.println("Parameter code style => " + codeStyle);

        // 2) Add (inline-add screens already show the form).
        String addHow = pr.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Parameter form", "The Parameter form is shown",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("Parameter form => " + pr.describeForm());

        // 3) .. 6) details -> range -> inner Add -> Submit; retry with different details on "already exists".
        String details = "", range = "", rowAdd = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = pr.fillParameterDetails(attempt);
            range = pr.fillAgeWiseRange(attempt);
            rowAdd = pr.clickAddDetail();
            used++;
            toast = pr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            // "succes" (one s) is deliberate — sibling screens here return "Record added succesfully".
            ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Parameter: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Parameter Details",
                "Enter Parameter Code, Parameter Name, Print Name, Parameter Unit, Parameter SSI Unit, "
                        + "LOINC Code and Conver. Factor",
                "All seven Parameter Details values are entered",
                details + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        boolean rangeOk = !range.contains("(no field)") && !range.contains("(not selected)");
        step(page, "Enter the Age Wise Range",
                "Select Gender and enter Lower Value, Alert Low, Critical Low, Upper Value, Alert High and Critical High",
                "The age-wise range is entered",
                rangeOk ? range : range + "\n" + pr.describeForm(), rangeOk ? "PASS" : "FAIL");

        // The range only reaches the server once the inner Add has appended it to the list.
        boolean rowOk = pr.hasRangeRow();
        step(page, "Click Add (append the range row)", "Click Add under the Age Wise Range fields",
                "The range row is appended to the list",
                rowOk ? rowAdd : rowAdd + "\n" + pr.describeForm(), rowOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + pr.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + pr.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (pr.toastPng != null && pr.toastPng.length > 0) {
            step(pr.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Parameter Code", pr.lastCode);
        addSummary("Parameter Name", pr.lastName);
        addSummary("Print Name", pr.lastPrintName);
        addSummary("Parameter Unit / SSI Unit", pr.lastUnit + " / " + pr.lastSsiUnit);
        addSummary("LOINC Code", pr.lastLoinc);
        addSummary("Conver. Factor", pr.lastConvFactor);
        addSummary("Age Wise Range", pr.lastAgeRange + " (" + pr.lastGender + ")");
        addSummary("Range values", pr.lastRange);
        addSummary("Range row", rowAdd);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
