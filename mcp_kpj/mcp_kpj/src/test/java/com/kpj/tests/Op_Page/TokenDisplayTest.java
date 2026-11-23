package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.OutPatientQueueManagementPage;
import com.kpj.pages.Op_page.TokenDisplayPage;

import java.util.ArrayList;
import java.util.List;

/**
 * OP &gt; <b>Token Display</b> (#/TokenDisplay) — the waiting-area display board. Two sections:
 * <ol>
 *   <li><b>Board</b> — the screen opens; the Waiting Area list loads; a normal area shows the Token Number /
 *       Consultation Room board (max 5 rows); <b>Emergency</b> shows the emergency card board.</li>
 *   <li><b>Call Patient</b> — on OP &gt; Outpatient Queue Management (today) select a patient who has a Queue No and
 *       click Call Patient (the team's {@link OutPatientQueueManagementPage} steps); then on Token Display find that
 *       token as CALLED in one of the waiting areas.</li>
 * </ol>
 * <p>The bell and the spoken announcement cannot be checked by automation; the board's "called" flag and flashing
 * row are checked instead.</p>
 */
public class TokenDisplayTest extends DevHisBase {

    /** Junk/test areas tried last when hunting for the called token. */
    private static final String LATE_AREAS = "(?i)^automated waiting area|^test$";

    public TokenDisplayTest() { super("TokenDisplay"); }

    public static void main(String[] args) {
        TokenDisplayTest t = new TokenDisplayTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private List<String> areas = new ArrayList<>();

    @Override
    protected void body() {
        meta("OP - Token Display", "OP > Token Display",
                "Waiting-area display board: Waiting Area list, normal board (Token Number / Consultation Room), Emergency board; "
                        + "a patient called from Outpatient Queue Management shows on the board as called.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // Call the patient FIRST: Token Display hides the sidebar, so the queue screen cannot be opened from it afterwards.
        String token = sectionCallPatient();
        if (!sectionBoard()) { addSummary("Result", "FAILED - Token Display not reached"); return; }
        if (token != null) sectionTokenOnBoard(token);
    }

    private String calledPatient = null;

    // ===== Section: Board ===============================================
    private boolean sectionBoard() {
        TokenDisplayPage td = new TokenDisplayPage(page);
        boolean opened = td.navigateViaMenu();
        step(page, "Board · Open Token Display", "OP > Token Display", "The display board opens with the Waiting Area drop-down",
                opened ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", opened ? "PASS" : "FAIL");
        if (!opened) return false;

        areas = td.waitingAreas();
        step(page, "Board · Waiting Area list", "Open the Waiting Area drop-down", "The waiting areas are listed",
                areas.size() + " waiting areas" + (areas.isEmpty() ? "" : " (e.g. " + String.join(", ", areas.subList(0, Math.min(4, areas.size()))) + ")"),
                areas.isEmpty() ? "FAIL" : "PASS");
        if (areas.isEmpty()) return true;

        // The board draws its Emergency card layout for waiting-area id 6 ("Accident & Emergency"), not for the area named "Emergency".
        String emergency = td.areaNameById(6);
        String normal = areas.stream().filter(a -> !a.matches(LATE_AREAS) && !a.equals(emergency)).findFirst().orElse(areas.get(0));
        int n = td.selectWaitingArea(normal);
        boolean headings = td.normalHeadingsShown();
        int rows = td.drawnRows();
        step(page, "Board · Normal waiting area", "Select Waiting Area = " + normal,
                "The board shows Token Number / Consultation Room and at most 5 rows",
                "fetch " + (n >= 0 ? "answered (" + td.boardSummary() + ")" : "did NOT answer") + " | headings shown=" + headings + " | rows drawn=" + rows,
                n >= 0 && headings && rows <= 5 ? "PASS" : "FAIL");

        if (emergency != null) {
            int e = td.selectWaitingArea(emergency);
            boolean board = td.emergencyBoardShown();
            boolean noHeadings = !td.normalHeadingsShown();
            step(page, "Board · Emergency waiting area", "Select Waiting Area = " + emergency + " (id 6)",
                    "The Emergency card board shows instead of the Token Number / Consultation Room board",
                    "fetch " + (e >= 0 ? "answered (" + td.boardSummary() + ")" : "did NOT answer") + " | emergency board=" + board
                            + " | normal headings hidden=" + noHeadings + " | cards drawn=" + td.drawnEmergencyCards(),
                    e >= 0 && board && noHeadings ? "PASS" : "FAIL");
        } else {
            step(page, "Board · Emergency waiting area", "Select the Emergency waiting area (id 6)", "The Emergency card board shows",
                    "No waiting area with id 6 in the list", "FAIL");
        }
        return true;
    }

    // ===== Section: Call Patient ========================================
    /** Queue screen part: select (or give a Queue No to) a today patient and click Call Patient. Returns the called Queue No, or null. */
    private String sectionCallPatient() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        boolean onQueue = qm.onQueueScreen(5);
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (onQueue) {
            qm.searchQueue(today, today);
            qm.waitForQueueLoaded();
        }
        step(page, "Call Patient · Open queue & search (today)", "OP > Outpatient Queue Management; search today (" + today + ")",
                "Today's queued patients are listed", onQueue ? "Queue searched for " + today : "Queue screen did NOT open (" + page.url() + ")",
                onQueue ? "PASS" : "FAIL");
        if (!onQueue) return null;

        // A token reaches the board only when the patient has a Queue No — take the first today patient that has one;
        // when none has, assign one with the team's Generate Queue steps first.
        String patient = null, token = "", how = "";
        for (int i = 0; i < 15; i++) {
            String p = qm.selectNthQueueRow(i);
            if (p == null) break;
            String q = qm.getSelectedQueueNo();
            if (!q.isEmpty() && !"0".equals(q)) { patient = p; token = q; how = "already had a Queue No"; break; }
        }
        if (patient == null) {
            for (int i = 0; i < 12 && patient == null; i++) {
                String p = qm.selectNthQueueRow(i);
                if (p == null) break;
                if (!qm.clickGenerateQueue()) continue;
                String res = qm.fillGenerateQueueAndSave("");
                boolean ok = res != null && res.toLowerCase().matches(".*(token updated|updated successfully|success).*");
                if (!ok) { qm.dismissAnyModal(); continue; }
                qm.searchQueue(today, today);
                qm.waitForQueueLoaded();
                // selectQueueRow takes a JavaScript regex (and falls back to the first row) — escape the name and check it is the same patient
                String again = qm.selectQueueRow("^" + p.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0") + "$");
                String q = again != null && again.equalsIgnoreCase(p) ? qm.getSelectedQueueNo() : "";
                if (!q.isEmpty() && !"0".equals(q)) { patient = p; token = q; how = "Queue No assigned with Generate Queue (" + res + ")"; }
            }
        }
        step(page, "Call Patient · Select a patient with a Queue No", "Select a today patient whose Queue No is set (else assign one with Generate Queue)",
                "One patient with a Queue No is selected",
                patient == null ? "No today patient has a Queue No, and Generate Queue did not assign one" : patient + " (Queue No " + token + ") - " + how,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return null;

        String toast = qm.callPatientAndGetToast();
        boolean called = toast != null && !toast.isEmpty() && !toast.toLowerCase().matches(".*please\\s+(select|enter).*");
        step(page, "Call Patient · Click Call Patient", "Click Call Patient (team script OutPatientQueueManagementPage.callPatientAndGetToast)",
                "Token/display confirmation toast", toast == null || toast.isEmpty() ? "No toast appeared" : toast, called ? "PASS" : "FAIL");
        if (!called) return null;
        calledPatient = patient;
        return token;
    }

    // ===== Section: Token on the board ==================================
    /** Find the called Queue No on the board: named waiting areas first, the "Automated waiting area …" / TEST ones last. */
    private void sectionTokenOnBoard(String token) {
        TokenDisplayPage td = new TokenDisplayPage(page);
        boolean opened = td.navigateViaMenu();
        if (!opened) {
            step(page, "Call Patient · Token on the display board", "OP > Token Display", "The board opens", "Did NOT reach Token Display", "FAIL");
            return;
        }
        List<String> order = new ArrayList<>();
        for (String a : td.waitingAreas()) if (!a.matches(LATE_AREAS)) order.add(a);
        for (String a : td.waitingAreas()) if (a.matches(LATE_AREAS)) order.add(a);

        String foundArea = null, found = null;
        int tried = 0;
        List<String> withTokens = new ArrayList<>();
        for (String a : order) {
            tried++;
            if (td.selectWaitingArea(a) <= 0) continue;
            withTokens.add(a + " [" + td.boardSummary() + "]");
            String f = td.findToken(token);
            if (f != null && f.contains("called=true")) { foundArea = a; found = f; break; }
        }
        if (found != null) {
            td.waitForCalled(token, 6000);
            found = td.findToken(token);
        }
        step(page, "Call Patient · Token on the display board", "Open Token Display; go through the Waiting Areas until Queue No " + token + " is listed",
                "Queue No " + token + " is on a waiting area's board as CALLED (flashing; bell and voice are not checkable)",
                found == null ? "Queue No " + token + " NOT found as called in any of " + tried + " waiting areas; "
                        + (withTokens.isEmpty() ? "every waiting area's board was EMPTY" : withTokens.size() + " area(s) had tokens: " + String.join("; ", withTokens.subList(0, Math.min(5, withTokens.size()))))
                        : "Waiting Area '" + foundArea + "' (after " + tried + " area(s)): " + found,
                found != null ? "PASS" : "FAIL");

        addSummary("Called patient", calledPatient + " (Queue No " + token + ")");
        addSummary("Shown on waiting area", foundArea == null ? "(not found)" : foundArea);
    }
}
