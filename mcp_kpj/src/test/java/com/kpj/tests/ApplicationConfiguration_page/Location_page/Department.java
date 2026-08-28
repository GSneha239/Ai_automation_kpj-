package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Department — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Department</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Department</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Department</b>, <b>Store</b>.</li>
 *   <li>Tick the <b>Department Location Details</b> checkbox.</li>
 *   <li>Fill <b>Case Template</b>; select <b>Diagnosis Code</b>; enter <b>Patient Count</b> + <b>Remark</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class Department extends DevHisBase {

    public Department() { super("ApplicationConfig_Location_Department"); }

    public static void main(String[] args) {
        Department t = new Department();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Department", "Application Configuration > Location > Department",
                "Add a Department: Code, Department, Store, Department Location Details, Case Template line, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.Department dep =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.Department(page);

        boolean onScreen = dep.navigateViaMenu();
        step(page, "Open Department screen", "Click Application Configuration -> Location -> Department",
                "The Department screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + dep.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("--- LIST SCREEN ---\n" + dep.describeForm());
            System.out.println("department links => " + dep.findDepartmentLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }

        // The master rejects a duplicate name ("Description already exist"), so learn what it already holds first.
        System.out.println("departments already on file: " + dep.harvestExistingNamesFromApi());

        boolean added = dep.clickAdd() && dep.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- LIST SCREEN AFTER ADD ---\n" + dep.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }

        // 3) Code, Department, Store
        String hdr = dep.fillCodeDepartmentStore(0);
        boolean hdrOk = hdr.contains("Code=DP") && !hdr.contains("(no field)") && !hdr.contains("Store=(no option)");
        step(page, "Enter Code, Department, Store", "Enter the Code and Department, and pick a Store",
                "Code, Department and Store are set", hdr, hdrOk ? "PASS" : "FAIL");

        // 4) Department Location Details — tick the row's Select checkbox
        String loc = dep.tickDepartmentLocationDetails();
        boolean locOk = !loc.isEmpty();
        step(page, "Select Department Location Details", "Tick the checkbox in the Department Location Details table",
                "The location row is ticked", locOk ? "Ticked: " + loc : "No checkbox ticked", locOk ? "PASS" : "FAIL");

        // 5) Case Template
        String tpl = dep.fillCaseTemplate();
        boolean tplOk = !tpl.isEmpty();
        step(page, "Fill Case Template", "Type the Case Template into its rich-text editor",
                "The Case Template editor holds the text",
                tplOk ? "Case Template = " + tpl : "Case Template editor not filled",
                tplOk ? "PASS" : "FAIL");

        // 6) Diagnosis Code — a typeahead that commits on ArrowDown + Enter
        String diag = dep.pickDiagnosisCode();
        boolean diagOk = dep.lastDiagnosisCode != null && !dep.lastDiagnosisCode.isEmpty();
        step(page, "Select Diagnosis Code", "Type into Diagnosis Code and pick a suggestion",
                "A Diagnosis Code is selected", diag, diagOk ? "PASS" : "FAIL");

        // 7) Patient Count + Remark
        String pcr = dep.fillPatientCountAndRemark();
        boolean pcrOk = !pcr.contains("(no field)");
        step(page, "Enter Patient Count and Remark", "Enter the Patient Count and the Remark",
                "Patient Count and Remark are entered", pcr, pcrOk ? "PASS" : "FAIL");

        // 8) Inner Add — commits the diagnosis line into the detail grid
        String inner = dep.clickInnerAdd();
        System.out.println("diagnosis grid now: " + dep.diagnosisRows());
        // The row must be added AND the click must not have raised a validation message.
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(dep.lastAddToasts);
        step(page, "Click Add (diagnosis line)", "Click the Add button of the diagnosis section",
                "The diagnosis line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // 9) Submit — retry with a different name while the master says the description is taken.
        String toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            toast = dep.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            tries.append(attempt == 0 ? "" : " | ").append(dep.lastDepartment).append(" -> \"").append(toast).append('"');
            System.out.println("Submit attempt " + (attempt + 1) + " toasts: " + dep.lastToasts);
            // Decide on ALL of Submit's toasts, not just the one shown first.
            if (ok || !dep.lastToasts.toLowerCase().contains("already exist")) break;
            dep.markNameTaken(dep.lastDepartment);
            String again = dep.fillCodeDepartmentStore(attempt + 1);
            System.out.println("Submit: name taken, retrying with " + again);
        }

        String saved = dep.fetchSavedRow(dep.lastCode);
        boolean savedOk = saved != null && !saved.isEmpty();
        step(page, "Click Submit", "Click Submit; verify the department row exists in the list",
                "The Department row is created",
                (savedOk ? "Saved: " + saved : "Row NOT created (" + dep.lastCode + ")") + "  [attempts: " + tries + "]",
                savedOk ? "PASS" : "FAIL");

        // 10) Success toast (screenshot taken at toast time, before the row check navigated away)
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        if (!ok) actual += " — the save API returned " + dep.lastSaveApi;
        step(dep.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Department details added successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department Code", dep.lastCode);
        addSummary("Department", dep.lastDepartment);
        addSummary("Store", dep.lastStore);
        addSummary("Case Template", dep.lastCaseTemplate);
        addSummary("Diagnosis", dep.lastDiagnosisCode + " " + dep.lastDiagnosisDescription
                + " (Patient Count " + dep.lastPatientCount + ")");
        addSummary("Saved row", savedOk ? saved : "not found");
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
