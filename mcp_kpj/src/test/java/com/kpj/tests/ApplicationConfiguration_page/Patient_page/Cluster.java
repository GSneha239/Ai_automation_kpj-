package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Cluster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Cluster</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Cluster</b> ({@code #/ClusterMasterList}).</li>
 *   <li>Click <b>Add</b> ({@code AddCluster}) → {@code #/add-ClusterMaster}.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Enter a <b>Doctor</b> and select it, then <b>Add</b> ({@code AddDoctor}) so it lands in the grid.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCluster}); wait for the success toast.</li>
 * </ol>
 */
public class Cluster extends DevHisBase {

    public Cluster() { super("ApplicationConfig_Patient_Cluster"); }

    public static void main(String[] args) {
        Cluster t = new Cluster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Cluster", "Application Configuration > Patient > Cluster",
                "Add a Cluster: Add, enter Code + Remark, enter and select a Doctor, add it to the grid, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.Cluster cl =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.Cluster(page);

        boolean on = cl.navigateViaMenu();
        step(page, "Open Cluster screen", "Application Configuration -> Patient -> Cluster",
                "The Cluster list screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        boolean added = cl.clickAdd();
        step(page, "Click Add", "Click Add (AddCluster) -> #/add-ClusterMaster", "The Cluster add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String codeRemark = cl.fillCodeAndRemark();
        boolean crOk = codeRemark.contains("Code=CL") && !codeRemark.contains("Remark=(no)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are set on the form", codeRemark, crOk ? "PASS" : "FAIL");

        String doctor = cl.enterDoctorName("Demo Doctor");
        boolean docOk = !doctor.startsWith("(");
        step(page, "Enter the doctor", "Type the doctor into the Doctor box (#txtDoctorName)",
                "The doctor is entered", doctor, docOk ? "PASS" : "FAIL");

        String rows = cl.clickAddDoctor();
        boolean rowOk = rows.contains("->") && !rows.endsWith("-> 0");
        step(page, "Click Add", "Click Add (AddDoctor) to push the doctor into the grid",
                "A doctor row is added to the Select / Doctor Name / Delete grid", rows,
                rowOk ? "PASS" : "FAIL");

        String ticked = cl.selectDoctorRowCheckbox();
        boolean tickOk = ticked.contains("checked=true");
        step(page, "Select the doctor's checkbox in the table",
                "Tick the Select checkbox of the doctor row in the Select / Doctor Name / Delete grid",
                "The doctor row is selected", ticked, tickOk ? "PASS" : "FAIL");

        String toast = cl.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit (fnIUDCluster); wait for the success toast",
                "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");

        addSummary("Cluster Code", cl.lastCode);
        addSummary("Doctor", cl.lastDoctor.isEmpty() ? "(none)" : cl.lastDoctor);
        addSummary("Route", "#/ClusterMasterList -> #/add-ClusterMaster");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
