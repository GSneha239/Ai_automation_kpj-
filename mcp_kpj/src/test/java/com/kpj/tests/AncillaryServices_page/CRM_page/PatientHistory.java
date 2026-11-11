package com.kpj.tests.AncillaryServices_page.CRM_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named PatientHistory — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CRM &gt; <b>Patient History</b> ({@code #/PatientHelpDesk}).
 *
 * <ol>
 *   <li>Enter the MRN (default 1100339415, pin another with {@code -Dpatienthistory.mrn=}) and click search.</li>
 *   <li>Click the <b>Patient Task</b> button (a button at the foot of the screen; there is no tab of that name).</li>
 *   <li>In the popup: enter a unique <b>Patient Task</b>, click <b>Add</b> (puts it in the list on screen), click <b>Save</b> and verify the success toast.</li>
 *   <li>Click <b>Close</b> (Save leaves the popup open).</li>
 * </ol>
 *
 * <p>This test SAVES A REAL PATIENT TASK for the patient on every run; it stays.</p>
 */
public class PatientHistory extends DevHisBase {

    public PatientHistory() { super("AncillaryServices_CRM_PatientHistory"); }

    public static void main(String[] args) {
        PatientHistory t = new PatientHistory();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - CRM - Patient History", "Ancillary Services > CRM > Patient History",
                "Enter the MRN and search; click Patient Task; enter a unique task; Add; Save; verify the success toast; Close. SAVES A REAL PATIENT TASK.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CRM_page.PatientHistory ph = new com.kpj.pages.AncillaryServices_page.CRM_page.PatientHistory(page);
        String mrn = System.getProperty("patienthistory.mrn", "1100339415");

        boolean on = ph.navigateViaMenu();
        step("Open Patient History", "Click Ancillary Services -> CRM -> Patient History", "The Patient History screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Patient History not reached"); return; }

        boolean typed = ph.enterMrn(mrn);
        step("Enter MRN", "Enter the MRN " + mrn, "The MRN is accepted", typed ? "MRN \"" + mrn + "\" entered; the box and the screen's model hold it" : "Problem — " + ph.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — MRN: " + ph.problems); return; }

        boolean searched = ph.clickSearch();
        step("Click search", "Click the search button beside the MRN box", "The patient is loaded",
                searched ? "Search clicked; patient \"" + ph.lastPatient + "\" (id " + ph.lastPatientId + ") loaded; the OPD table lists " + ph.opdVisits() + " visit(s)" : "Problem — " + ph.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — search: " + ph.problems); return; }

        boolean popup = ph.clickPatientTask();
        List<String[]> existing = popup ? ph.taskRows() : List.of();
        step("Click Patient Task", "Click Patient Task (a button at the foot of the screen; there is no separate tab)", "The Patient Task popup opens",
                popup ? "The Patient Task popup is open (\"" + ph.popupPatient() + "\"); its table lists " + existing.size() + " existing task(s)" + (existing.isEmpty() ? "" : ", latest: \"" + existing.get(0)[1] + "\"") : "Problem — " + ph.problems, popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED — Patient Task: " + ph.problems); return; }

        String task = "Call the patient to confirm the follow-up visit, QA " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        for (String[] e : existing) if (e[1].equalsIgnoreCase(task)) { task += " B"; break; }
        boolean entered = ph.enterTask(task);
        step("Enter patient task", "Enter a unique Patient Task (" + task + ")", "The task is accepted",
                entered ? "Patient Task \"" + task + "\" entered (stamped with the time; none of the " + existing.size() + " existing tasks has it)" : "Problem — " + ph.problems, entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — task: " + ph.problems); return; }

        boolean added = ph.clickAdd();
        step("Click Add", "Click Add in the popup", "The task joins the list",
                added ? "Add clicked; the list now holds " + ph.taskRows().size() + " task(s) and has the new row \"" + ph.lastAddedRow + "\" (Add only puts it in the list on screen; nothing is stored yet)" : "Problem — " + ph.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add: " + ph.problems); return; }

        String toast = ph.saveAndGetToast();
        boolean ok = ph.problems.isEmpty() && ph.saveSucceeded();
        step("Click Save", "Click Save in the popup", "The task is saved",
                ph.problems.isEmpty() ? "Save clicked" + (ph.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + ph.lastDialog + "\"") : "Problem — " + ph.problems, ph.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = ph.savedByServer();
        boolean stillOpen = ph.popupOpen();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (ph.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + ph.lastSaveMessage.trim() + "\"") + "; the popup " + (stillOpen ? "stays open" : "closed")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the task (ResultStatus 1, \"" + ph.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + ph.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + ph.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // not a step: read the tasks the server holds for the patient (the call the popup itself makes)
        List<String[]> saved = (ok || savedByServer) ? ph.savedTasks() : null;
        String[] mine = null;
        if (saved != null) for (String[] s : saved) if (s[1].equals(task)) mine = s;

        boolean closed = ph.closePopup();
        step("Click Close", "Click Close in the popup", "The popup closes",
                closed ? "Close clicked; the Patient Task popup closed" + (stillOpen ? " (Save had left it open)" : "") : "Problem — " + ph.problems, closed ? "PASS" : "FAIL");

        addSummary("MRN / Patient", mrn + " / " + ph.lastPatient + " (id " + ph.lastPatientId + ")");
        addSummary("Tasks before", existing.size() + " listed in the popup");
        addSummary("Patient task added", task);
        addSummary("Save request", ph.lastSaveRequest.isEmpty() ? "(not seen)" : ph.lastSaveRequest);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1" + (ph.lastSaveMessage.isEmpty() ? "" : ", \"" + ph.lastSaveMessage.trim() + "\"") + ")" : "(no save confirmed" + (ph.lastSaveMessage.isEmpty() ? "" : ": " + ph.lastSaveMessage) + ")");
        addSummary("Held by the server afterwards", saved == null ? "(not checked)" : mine != null ? "yes - task id " + mine[0] + " (" + saved.size() + " task(s) in all)" : "NOT FOUND among the " + saved.size() + " task(s) the server returns");
        addSummary("Popup after Save", stillOpen ? "stayed open (the screen hides '#patientremark', the popup's id is '#patremark')" : "closed by itself");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
