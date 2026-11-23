package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// The page object is also named SterilizationMachineQualityControl — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Sterilization Machine Quality Control</b> ({@code #/SterilizationMachineQualityControl} -&gt; New {@code #/add-SterilizationMachineQualityControl}).
 *
 * <ol>
 *   <li>Click <b>New</b>. The form has two REAL tabs, so "add a new tab" is a click on them: first <b>Quality Template</b> - select a template, click <b>Add</b> (its text loads into <b>Remark</b>), fill in the
 *       details in Remark.</li>
 *   <li>Click the <b>Sterilization Machine Quality Control</b> tab: enter the <b>Time</b>, select the <b>Machine</b> and the <b>Test Type</b>, tick the <b>Status</b> radio.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>Options: {@code -Dsm.template=}, {@code -Dsm.machine=}, {@code -Dsm.type=}, {@code -Dsm.status=Success|Fail}.</p>
 *
 * <p>&#9888; A successful run CREATES a real Sterilization Machine Quality Control record (it cannot be deleted from the screen).</p>
 */
public class SterilizationMachineQualityControl extends DevHisBase {

    public SterilizationMachineQualityControl() { super("AncillaryServices_CSSD_SterilizationMachineQualityControl"); }

    public static void main(String[] args) {
        SterilizationMachineQualityControl t = new SterilizationMachineQualityControl();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Sterilization Machine Quality Control", "Ancillary Services > CSSD > Sterilization Machine Quality Control",
                "&#9888; Creates a REAL record. New; Quality Template tab: select a template, Add, fill in the details in Remark; Sterilization Machine Quality Control tab: Time, Machine, Test Type, Status radio; Save; verify the success toast.");

        final String template = System.getProperty("sm.template", "Sterilization Quality Assurance");
        final String machine = System.getProperty("sm.machine", "Autoclave01");
        final String type = System.getProperty("sm.type", "Biological");
        final String status = System.getProperty("sm.status", "Success");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.SterilizationMachineQualityControl sm = new com.kpj.pages.AncillaryServices_page.CSSD_page.SterilizationMachineQualityControl(page);

        boolean on = sm.navigateViaMenu();
        step("Open Sterilization Machine Quality Control", "Click Ancillary Services -> CSSD -> Sterilization Machine Quality Control", "The Sterilization Machine Quality Control list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Sterilization Machine Quality Control not reached"); return; }

        // 1) New
        boolean opened = sm.clickNew();
        step("Click New", "Click New", "The form is shown with its two tabs",
                opened ? "The form opened at " + page.url() + "; tabs " + sm.tabNames() + ", the active one is \"" + sm.activeTab() + "\"" : "Problem — " + sm.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — the form did not open: " + sm.problems); return; }

        // 2) the Quality Template tab (a real tab, already the active one)
        boolean tab1 = sm.clickTab("Quality Template");
        step("Click Quality Template", "Add a new tab - Quality Template; click Quality Template", "The Quality Template tab is shown",
                tab1 ? "The form already has this tab (tabs " + sm.tabNames() + "); clicked it, it is the active tab" : "Problem — " + sm.problems, tab1 ? "PASS" : "FAIL");
        if (!tab1) { addSummary("Result", "FAILED — Quality Template tab: " + sm.problems); return; }

        String tpl = sm.selectQualityTemplate(template);
        step("Select quality template", "Select the Quality Template \"" + template + "\"", "The template is selected",
                !tpl.isEmpty() ? "Quality Template \"" + tpl + "\" selected (of " + sm.options("QualityTemplate.qualitytemplateid") + "); the screen's model holds it" : "Problem — " + sm.problems, !tpl.isEmpty() ? "PASS" : "FAIL");
        if (tpl.isEmpty()) { addSummary("Result", "FAILED — template: " + sm.problems); return; }

        String loaded = sm.clickAddTemplate();
        step("Click Add", "Click Add", "The template text is loaded into Remark",
                !loaded.isEmpty() ? "Add loaded the template into Remark (" + loaded.length() + " characters): \"" + (loaded.length() > 160 ? loaded.substring(0, 160) + "..." : loaded) + "\"" : "Problem — " + sm.problems, !loaded.isEmpty() ? "PASS" : "FAIL");
        if (loaded.isEmpty()) { addSummary("Result", "FAILED — Add: " + sm.problems); return; }

        String details = "QA auto details " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"))
                + ": administrative controls reviewed, chemical and biological indicators checked, mechanical indicators within limits.";
        boolean detailsOk = sm.appendDetails(details);
        step("Fill in the details in Remark", "In Remark, fill in the details (" + details + ")", "The details are held by Remark",
                detailsOk ? "Details typed at the end of the template text in the Remark editor; CKEditor's data and the screen's model hold them (" + sm.modelText().length() + " characters in all)" : "Problem — " + sm.problems,
                detailsOk ? "PASS" : "FAIL");
        if (!detailsOk) { addSummary("Result", "FAILED — Remark: " + sm.problems); return; }

        // 3) the machine tab
        boolean tab2 = sm.clickTab("Sterilization Machine Quality Control");
        step("Click Sterilization Machine Quality Control", "Add another tab - Sterilization Machine Control Quality; click Sterilization Machine Quality Control", "The Sterilization Machine Quality Control tab is shown",
                tab2 ? "The form already has this tab (tabs " + sm.tabNames() + "); clicked it, it is the active tab" : "Problem — " + sm.problems, tab2 ? "PASS" : "FAIL");
        if (!tab2) { addSummary("Result", "FAILED — machine tab: " + sm.problems); return; }

        LocalTime now = LocalTime.now().minusMinutes(5);
        String timeText = now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
        String hhmm = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        boolean timeOk = sm.enterTime(timeText, hhmm);
        step("Enter time", "Enter the Time (" + timeText + ")", "The time is accepted",
                timeOk ? "Time \"" + sm.timeBox() + "\" typed; the screen's model holds " + sm.timeModel() : "Problem — " + sm.problems, timeOk ? "PASS" : "FAIL");
        if (!timeOk) { addSummary("Result", "FAILED — time: " + sm.problems); return; }

        String mach = sm.selectMachine(machine);
        step("Select machine", "Select the Machine \"" + machine + "\"", "The machine is selected",
                !mach.isEmpty() ? "Machine \"" + mach + "\" selected (of " + sm.options("MachineQualityControl.machineid").size() + " machines); the screen's model holds it" : "Problem — " + sm.problems, !mach.isEmpty() ? "PASS" : "FAIL");
        if (mach.isEmpty()) { addSummary("Result", "FAILED — machine: " + sm.problems); return; }

        String tt = sm.selectTestType(type);
        step("Select test type", "Select the Test Type \"" + type + "\"", "The test type is selected",
                !tt.isEmpty() ? "Test Type \"" + tt + "\" selected (of " + sm.options("MachineQualityControl.testtype").size() + " test types); the screen's model holds it" : "Problem — " + sm.problems, !tt.isEmpty() ? "PASS" : "FAIL");
        if (tt.isEmpty()) { addSummary("Result", "FAILED — test type: " + sm.problems); return; }

        boolean st = sm.tickStatus(status);
        step("Tick the status radio", "Tick the Status radio \"" + status + "\"", "The status is selected", st ? "Status \"" + status + "\" ticked; the screen's model holds it" : "Problem — " + sm.problems, st ? "PASS" : "FAIL");
        if (!st) { addSummary("Result", "FAILED — status: " + sm.problems); return; }

        // 4) Save
        LocalDate today = LocalDate.now();
        List<String[]> before = sm.records(today);
        final int machineId = sm.machineId();
        sm.clickSave();
        boolean clicked = sm.ok();
        String toast = clicked ? sm.awaitToast(25000) : "";
        step("Click Save", "Click Save", "The record is saved", clicked ? "Save clicked" + (sm.lastSaveRequest.isEmpty() ? "" : "; request " + sm.lastSaveRequest) : "Problem — " + sm.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save: " + sm.problems); return; }

        boolean success = sm.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (sm.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + sm.lastSaveMessage.trim() + "\"")
                      : sm.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED it (ResultStatus 1)"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + sm.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + sm.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // read back (not a step of the spec): back on the list, and the record is there
        boolean back = success && sm.backOnList(8000);
        List<String[]> after = (success || sm.savedByServer()) ? sm.records(today) : List.of();
        String found = "", foundId = "";
        for (String[] r : after) if (r.length > 6 && r[6].equals(String.valueOf(machineId)) && r[1].equals(hhmm) && r[3].equalsIgnoreCase(status) && r[2].equalsIgnoreCase(tt)) { found = String.join(" / ", r[0], r[1], r[2], r[3]); foundId = r[5]; }
        String remarkSaved = foundId.isEmpty() ? "" : sm.savedRemark(foundId);
        boolean remarkHeld = !remarkSaved.isEmpty() && remarkSaved.contains(details.substring(0, 40));

        addSummary("Quality Template", tpl);
        addSummary("Remark details", details);
        addSummary("Time / Machine / Test Type / Status", timeText + " (" + hhmm + ") / " + mach + " / " + tt + " / " + status);
        addSummary("Save request", sm.lastSaveRequest.isEmpty() ? "(not seen)" : sm.lastSaveRequest);
        addSummary("Server answer", sm.savedByServer() ? "saved (ResultStatus 1" + (sm.lastResponseId.isEmpty() ? "" : ", id " + sm.lastResponseId) + ", \"" + sm.lastSaveMessage.trim() + "\")" : "(no save confirmed" + (sm.lastSaveMessage.isEmpty() ? "" : ": " + sm.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Back on the list afterwards", success ? (back ? "yes - " + page.url() : "no (" + page.url() + ")") : "n/a");
        addSummary("Records of today before / after", before.size() + " / " + after.size());
        addSummary("The new record in the list", found.isEmpty() ? "(not found among today's records - machine id " + machineId + ", " + hhmm + ")" : found + " (record id " + foundId + "; the grid names machine id " + machineId + " \"" + found.split(" / ")[0] + "\", the Machine dropdown \"" + mach + "\")");
        addSummary("Remark stored by the server", foundId.isEmpty() ? "(record not found)" : remarkHeld ? "yes - the record read back through the Edit call holds the template text and the typed details" : "NO - the record read back through the Edit call does not hold the typed details");
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (sm.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
