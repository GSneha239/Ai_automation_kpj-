package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;

// The page object is also named MonitoringChart — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Monitoring Chart</b> ({@code #/MonitoringChartList}).
 *
 * <ol>
 *   <li>Click <b>Nursing Station</b> → <b>Monitoring Chart</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>MRN</b> through the form's <b>Patient Search</b> popup (filter by MRN, Search, Select).</li>
 *   <li>Select a <b>Monitoring Template</b> (one this patient has no chart for today, so the save is not refused as
 *       a duplicate).</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 *   <li>Back on the list, <b>Search</b> and verify the table holds the entered data (MRN, patient, template, date).</li>
 * </ol>
 *
 * <p>Knob: {@code -Dmrn=} the patient (default {@link #DEFAULT_MRN} — the admitted patient whose chart is already on
 * the list). &#9888; A successful run RECORDS a REAL monitoring chart for that patient.</p>
 */
public class MonitoringChart extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied: an admitted patient (the list already holds a chart for them). */
    public static final String DEFAULT_MRN = "1100339278";

    public MonitoringChart() { super("NursingStation_MonitoringChart"); }

    public static void main(String[] args) {
        MonitoringChart t = new MonitoringChart();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static boolean sameText(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }

    @Override
    protected void body() {
        meta("Nursing Station - Monitoring Chart", "Nursing Station > Monitoring Chart",
                "&#9888; Records a REAL monitoring chart: Add, enter the MRN with the Patient Search popup, select a Monitoring "
                        + "Template, Save, verify the success toast and the list.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.MonitoringChart mc =
                new com.kpj.pages.NursingStation_page.MonitoringChart(page);

        // 1) Navigate — FAIL and stop if the app serves a different screen.
        boolean on = mc.navigateViaMenu();
        step(page, "Open Monitoring Chart screen", "Click Nursing Station -> Monitoring Chart",
                "The Monitoring Chart list is shown",
                on ? "Opened " + mc.currentScreen() : "Did NOT reach the screen (" + mc.currentScreen() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Monitoring Chart screen not reached"); return; }

        // What the list holds today for this patient BEFORE the save (to prove the new row afterwards).
        mc.clickSearch();
        List<String[]> before = mc.listRows();
        List<String> alreadyCharted = new ArrayList<>();
        for (String[] r : before) if (sameText(r[0], mrn)) alreadyCharted.add(r[2]);

        // 2) Add
        boolean added = mc.clickAdd();
        step(page, "Click Add", "Click Add (AddMonitoringChart)",
                "The Monitoring Chart entry form is shown (MRN, Date, Monitoring Template)",
                added ? "Entry form opened (" + page.url() + ")" : "The entry form did not open (" + mc.currentScreen() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - entry form not opened"); return; }

        // 3) MRN through the Patient Search popup
        String pat = mc.selectPatientViaPopup(mrn);
        boolean patOk = !pat.startsWith("(");
        step(page, "Enter MRN using the Patient Search popup",
                "Click the Patient Search button beside MRN, filter by MRN " + mrn + ", Search, click Select on the row",
                "The popup closes, the MRN box holds " + mrn + " and the patient is shown",
                patOk ? pat : "Could not enter the MRN - " + pat, patOk ? "PASS" : "FAIL");
        if (!patOk) { addSummary("Result", "FAILED - patient not selected: " + pat); return; }

        // 4) Template + Save. A template this patient already has a chart for today is refused as a duplicate, so the
        // templates without a chart come first; if the app still answers "already exists", the next one is tried.
        List<String> all = mc.templateOptions();
        List<String> order = new ArrayList<>();
        for (String t : all) if (alreadyCharted.stream().noneMatch(x -> sameText(x, t))) order.add(t);
        for (String t : all) if (!order.contains(t)) order.add(t);

        String tplActual = "", toast = "";
        boolean tplOk = false, ok = false, exists = false;
        int tried = 0;
        for (String cand : order) {
            tried++;
            tplActual = mc.selectTemplate(cand);
            tplOk = !tplActual.startsWith("(");
            if (!tplOk) break;
            mc.formDate();
            toast = mc.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("MonitoringChart: \"" + toast + "\" for template " + cand + " - trying the next template");
        }
        String tplText = tplOk ? "Template " + tplActual + (tried > 1 ? "  [" + (tried - 1) + " template(s) refused as 'already exists' first]" : "")
                               : "Could not select a Monitoring Template - " + tplActual + "  (form: " + mc.describeForm() + ")";
        byte[] filled = mc.filledPng;
        if (filled != null && filled.length > 0) {
            step(filled, "Select Monitoring Template", "Select a Monitoring Template in the dropdown",
                    "A template is selected (MRN " + mrn + " + template on the form)", tplText, tplOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Monitoring Template", "Select a Monitoring Template in the dropdown",
                    "A template is selected (MRN " + mrn + " + template on the form)", tplText, tplOk ? "PASS" : "FAIL");
        }
        if (!tplOk) { addSummary("Result", "FAILED - no Monitoring Template selected"); return; }

        String saveActual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' for every template tried (" + tried + "): \"" + toast + "\""
                                         : "Save not confirmed - server returned: \"" + toast + "\""));
        if (mc.toastPng != null && mc.toastPng.length > 0) {
            step(mc.toastPng, "Click Save & success toast", "Click Save (IUDSaveMonitoringChart); wait for the success toast",
                    "A '... saved successfully' success toast is shown", saveActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast", "Click Save (IUDSaveMonitoringChart); wait for the success toast",
                    "A '... saved successfully' success toast is shown", saveActual, ok ? "PASS" : "FAIL");
        }

        // 5) The table must now hold the entered data.
        boolean listBack = mc.backToList();
        mc.clickSearch();
        List<String[]> after = mc.listRows();
        String template = mc.lastTemplate, date = mc.lastDate;
        int cntBefore = 0, cntAfter = 0;
        String[] hit = null;
        for (String[] r : before) if (sameText(r[0], mrn) && sameText(r[2], template) && sameText(r[3], date)) cntBefore++;
        for (String[] r : after)  if (sameText(r[0], mrn) && sameText(r[2], template) && sameText(r[3], date)) { cntAfter++; hit = r; }
        boolean nameOk = hit != null && !hit[1].isEmpty()
                && (mc.lastPatientName.toLowerCase().contains(hit[1].toLowerCase()) || hit[1].toLowerCase().contains(mc.lastPatientName.toLowerCase()));
        boolean tableOk = ok && listBack && hit != null && cntAfter > cntBefore && nameOk;
        String tableActual;
        if (!ok) tableActual = "Nothing to verify - the chart was not saved (see the previous step)";
        else if (!listBack) tableActual = "Could not get back to the Monitoring Chart list (" + mc.currentScreen() + ")";
        else if (hit == null) tableActual = "The list (" + after.size() + " row(s) for " + date + ") has NO row with MRN " + mrn + ", template \"" + template + "\", date " + date;
        else tableActual = "Row found - MRN " + hit[0] + " | " + hit[1] + " | " + hit[2] + " | " + hit[3]
                + "  (rows with this MRN + template + date: " + cntBefore + " -> " + cntAfter + "; list " + before.size() + " -> " + after.size() + " row(s))"
                + (nameOk ? "" : "  - but the patient name does not match the selected patient (" + mc.lastPatientName + ")");
        step(page, "Verify the table for the entered data",
                "Search the list for today and check the new row: MRN, Patient Name, Monitoring Template, Date",
                "The table lists a row with MRN " + mrn + ", the selected patient, template \"" + template + "\" and today's date",
                tableActual, tableOk ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient", mc.lastPatientName.isEmpty() ? "(not shown)" : mc.lastPatientName);
        addSummary("Monitoring Template", template);
        addSummary("Date", date);
        addSummary("Rows on the list", before.size() + " -> " + after.size());
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
