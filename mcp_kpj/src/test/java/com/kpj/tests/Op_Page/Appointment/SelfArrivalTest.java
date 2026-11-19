package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.SelfArrivalPage;

/**
 * OP &gt; <b>Self Arrival</b>: pick a patient through the Patient Search popup (PR No = MRN), Search, check the appointment is listed
 * in the table, click <b>Mark Arrival</b> and verify the success toast.
 *
 * <p>The screen lists a patient only when the patient has an appointment that is due, so the popup's patients are tried one after
 * another until one shows data. Pin a patient with {@code -Dmrn=}. &#9888; Mark Arrival changes a real appointment's status.</p>
 */
public class SelfArrivalTest extends DevHisBase {

    private static final int MAX_CANDIDATES = Integer.getInteger("selfarrival.candidates", 40);

    public SelfArrivalTest() { super("op_appointment_SelfArrival"); }

    public static void main(String[] args) {
        SelfArrivalTest t = new SelfArrivalTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OP - Self Arrival", "OP > Self Arrival",
                "&#9888; Marks a REAL appointment as arrived: pick the PR No (MRN) with the Patient Search popup, Search, check the table, "
                        + "click Mark Arrival, verify the success toast.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        SelfArrivalPage sa = new SelfArrivalPage(page);
        boolean on = sa.navigateViaMenu();
        step(page, "Open Self Arrival screen", "Click OP -> Self Arrival",
                "The Self Arrival screen (PR No, Name, Mobile No, Search, Mark Arrival) is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        // Candidate PR Nos: pinned with -Dmrn=, else the Patient Search popup's list
        String pinned = System.getProperty("mrn");
        java.util.List<String> mrns = new java.util.ArrayList<>();
        if (pinned != null && !pinned.isEmpty()) mrns.add(pinned);
        else mrns.addAll(sa.candidateMrns(MAX_CANDIDATES));
        step(page, "Search Patient popup - choose the PR No (MRN)", "Open the Patient Search popup beside PR No, search, read the patients' MRNs",
                "Patients are listed", mrns.isEmpty() ? "The popup listed no patient" : mrns.size() + " patient(s) to try, first " + mrns.get(0),
                mrns.isEmpty() ? "FAIL" : "PASS");
        if (mrns.isEmpty()) { addSummary("Result", "FAILED - no patient from the popup"); return; }

        // Enter the PR No and Search - next patient until one has an appointment listed
        String used = "";
        int tried = 0, rows = 0;
        for (String m : mrns) {
            tried++;
            sa.enterMrn(m);
            sa.clickSearch();
            rows = sa.rowCount();
            used = m;
            if (rows > 0) break;
        }
        boolean entered = sa.mrnBoxValue().equals(used);
        step(page, "Enter PR No and click Search", "Enter the PR No (MRN) chosen with the patient search and click Search",
                "The PR No is entered and the search runs",
                "PR No = " + used + (entered ? "" : " (box shows '" + sa.mrnBoxValue() + "')") + "; " + tried + " patient(s) searched", entered ? "PASS" : "FAIL");

        boolean data = rows > 0;
        step(page, "Verify the data is displayed in the table", "Look at the appointment table after Search",
                "The patient's appointment is listed in the table",
                data ? rows + " row(s): " + sa.rowsText()
                     : "The table is EMPTY for all " + tried + " patient(s) tried (last " + used + ") - none has an appointment due on this screen; "
                       + "book an appointment for today first or pin a patient that has one with -Dmrn=",
                data ? "PASS" : "FAIL");
        addSummary("PR No (MRN)", used);
        addSummary("Patients searched", String.valueOf(tried));
        // Mark Arrival is clicked even when no appointment is listed, so the screen's own answer is recorded (it is a FAIL either way unless a success toast shows).
        String toast = sa.clickMarkArrival();
        String tl = toast.toLowerCase();
        boolean ok = !toast.isEmpty() && tl.matches(".*(success|saved|updated|arrived|marked).*") && !tl.matches(".*(please|fail|error|invalid|not ).*");
        String markActual = (data ? "" : "(no appointment was listed) ") + (toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Not a success message: \"" + toast + "\""));
        // the screenshot was taken while the toast was on screen, so the report shows the message itself
        if (sa.toastPng != null && sa.toastPng.length > 0) {
            step(sa.toastPng, "Click Mark Arrival and verify the success toast", "Click Mark Arrival; read the toast",
                    "A success toast (arrival marked) is shown", markActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Mark Arrival and verify the success toast", "Click Mark Arrival; read the toast",
                    "A success toast (arrival marked) is shown", markActual, ok ? "PASS" : "FAIL");
        }
        addSummary("Result", toast.isEmpty() ? "No toast" : toast);
    }
}
