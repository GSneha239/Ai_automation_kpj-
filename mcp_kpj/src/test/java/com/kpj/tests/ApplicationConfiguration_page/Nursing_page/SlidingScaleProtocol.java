package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SlidingScaleProtocol — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Sliding Scale Protocol</b>
 * ({@code #/SlidingScaleProtocolList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Sliding Scale Protocol</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Protocol Name*</b>.</li>
 *   <li>Select <b>Parameter*</b> and <b>Unit*</b>.</li>
 *   <li>Enter <b>Range From</b>, <b>Range To</b> and <b>Dose</b>; select the <b>Dose Unit</b>.</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code / name already exists, enter a
 *       new Code and Protocol Name and Save again. If Save asks for a step, <b>Add Step</b> is clicked and Save
 *       is repeated.</li>
 * </ol>
 */
public class SlidingScaleProtocol extends DevHisBase {

    public SlidingScaleProtocol() { super("ApplicationConfig_Nursing_SlidingScaleProtocol"); }

    public static void main(String[] args) {
        SlidingScaleProtocol t = new SlidingScaleProtocol();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static boolean isOk(String toast) {
        String tl = toast == null ? "" : toast.toLowerCase();
        return tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
    }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Sliding Scale Protocol",
                "Application Configuration > Nursing > Sliding Scale Protocol",
                "Add a Sliding Scale Protocol: Add, Code + Protocol Name, Parameter + Unit, Range From/To + Dose + "
                        + "Dose Unit, Save; wait for the success toast. On 'already exists', use a new Code / Name.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.SlidingScaleProtocol ss =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.SlidingScaleProtocol(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = ss.navigateViaMenu();
        String landed = ss.currentScreen();
        step(page, "Open Sliding Scale Protocol screen",
                "Application Configuration -> Nursing -> Sliding Scale Protocol",
                "The Sliding Scale Protocol list screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Sliding Scale Protocol but the app opened: " + landed + "\n" + ss.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add
        String addHow = ss.clickAddIfPresent();
        boolean addOk = !addHow.startsWith("no Add button");
        step(page, "Click Add", "Click Add", "The Sliding Scale Protocol add form is shown", addHow, addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - add form not opened"); return; }

        // 3) Fill the form, Save — on "already exists" enter a new Code / Protocol Name.
        String codeName = "", paramUnit = "", range = "", doseUnit = "", toast = "", stepNote = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < 10; attempt++) {
            codeName = ss.enterCodeAndName(attempt);
            paramUnit = ss.selectParameterAndUnit();
            range = ss.enterRangeAndDose();
            doseUnit = ss.selectDoseUnit();
            used++;
            toast = ss.submitAndGetToast();
            ok = isOk(toast);
            // A protocol may need its step added to the grid before Save is accepted.
            if (!ok && (toast == null || toast.isEmpty() || toast.toLowerCase().contains("step"))) {
                boolean added = ss.clickAddStep();
                stepNote = added ? " [Save asked for a step -> Add Step clicked, saved again]" : " [no Add Step button]";
                if (added) { toast = ss.submitAndGetToast(); ok = isOk(toast); }
            }
            String tl = toast == null ? "" : toast.toLowerCase();
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("SlidingScaleProtocol: attempt " + used + " already exists — entering a new Code / Name");
        }

        boolean cnOk = codeName.contains("Code=SS") && !codeName.contains("(no field)");
        step(page, "Enter Code and Protocol Name", "Enter Code* and Protocol Name*", "Code and Protocol Name are entered",
                codeName + (used > 1 ? "  [changed " + (used - 1) + "x after 'already exists']" : ""), cnOk ? "PASS" : "FAIL");

        boolean puOk = !paramUnit.contains("(no ") && !paramUnit.contains("Parameter= |") && !paramUnit.endsWith("Unit=");
        step(page, "Select Parameter and Unit", "Select the Parameter* and the Unit*", "A Parameter and a Unit are selected",
                paramUnit, puOk ? "PASS" : "FAIL");

        boolean rgOk = range.contains("Range From=1") && range.contains("Range To=10") && range.contains("Dose=2");
        step(page, "Enter Range From, Range To and Dose", "Enter Range From 1, Range To 10 and Dose 2",
                "Range From, Range To and Dose are entered", range, rgOk ? "PASS" : "FAIL");

        boolean duOk = doseUnit.startsWith("Dose Unit=") && !doseUnit.contains("(no ");
        step(page, "Select Dose Unit", "Select the Dose Unit", "A Dose Unit is selected", doseUnit, duOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + ss.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s): \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + ss.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual + stepNote;
        if (ss.toastPng != null && ss.toastPng.length > 0) {
            step(ss.toastPng, "Click Save & success toast", "Click Save; wait for the success toast",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code / Protocol Name", ss.lastProtoCode + " / " + ss.lastProtoName);
        addSummary("Parameter / Unit", ss.lastParameter + " / " + ss.lastUnit);
        addSummary("Step", ss.lastStep + " | Dose Unit=" + ss.lastDoseUnit);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
