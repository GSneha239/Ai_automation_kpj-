package com.kpj.tests.Investigation_page.Radiology_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AcceptRadiologyOrder — referenced by its fully-qualified name.

/**
 * Investigation &gt; Radiology &gt; <b>Accept Radiology Order</b>.
 *
 * <p>One screen with two tabs, both driven here: <b>Accept</b> then <b>Reject</b>. The screen is opened
 * once; each tab does its own sub-group search, since Accept consumes whichever record it saves and
 * Reject needs a record still awaiting a decision.</p>
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Radiology</b> → <b>Accept Radiology Order</b>.</li>
 *   <li><b>Accept tab</b>: enter the From/To date, click <b>Search</b>, select a Sub Group that returns
 *       records, verify records exist in <b>Test</b>, tick the checkbox on one record, click
 *       <b>Accept</b> then <b>Yes</b> on the confirmation, fill <b>Mark Visit</b> (Department, Sub
 *       Department, Patient Type) then click <b>Mark Visit</b>, fill the <b>Acceptance Remark</b>
 *       (Remark, No of Films), click <b>OK</b>, verify the success toast.</li>
 *   <li><b>Reject tab</b>: re-search under its own Sub Group, verify records exist, tick a record, click
 *       <b>Reject</b>, select the <b>Rejection Reason</b> and enter the <b>Remark</b>, click <b>OK</b>,
 *       verify the success toast.</li>
 * </ol>
 *
 * <p>Both tabs are DATA-DEPENDENT: each needs a radiology order in the window searched, under whichever
 * sub group actually carries one. The flow will NOT click Accept/Reject unless a record is really
 * ticked — this screen opens its dialogs even with nothing selected, acting on the last patient it
 * holds, so a tab stops rather than acting on a record it never chose.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}, {@code -Dremark=}, {@code -Dfilms=} (Accept),
 * {@code -DrejectRemark=} (Reject).</p>
 *
 * <p>&#9888; A successful run ACCEPTS A REAL RADIOLOGY ORDER (and MARKS A REAL VISIT) on the Accept tab,
 * and REJECTS A REAL RADIOLOGY ORDER on the Reject tab.</p>
 */
public class AcceptRadiologyOrder extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AcceptRadiologyOrder() { super("Investigation_AcceptRadiologyOrder"); }

    public static void main(String[] args) {
        AcceptRadiologyOrder t = new AcceptRadiologyOrder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Investigation - Radiology - Accept Radiology Order", "Investigation > Radiology > Accept Radiology Order",
                "&#9888; ACCEPTS A REAL ORDER and MARKS A REAL VISIT on the Accept tab, and REJECTS A "
                        + "REAL ORDER on the Reject tab.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        // Accept tab
        String remark = System.getProperty("remark", "Auto acceptance remark " + stamp);
        String films = System.getProperty("films", "1");

        // Reject tab
        String rejectRemark = System.getProperty("rejectRemark", "Auto rejection remark " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Radiology_page.AcceptRadiologyOrder ar =
                new com.kpj.pages.Investigation_page.Radiology_page.AcceptRadiologyOrder(page);
        // Native confirms are recorded and accepted; left to Playwright's default they are dismissed
        // and the screen's question never reaches the report.
        ar.captureDialogs();

        boolean rendered = ar.navigateViaMenu(BASE);
        if (!rendered) addSummary("Menu offered", ar.lastMenu);
        step(page, "Open Accept Radiology Order screen",
                "Click Investigation -> Radiology -> Accept Radiology Order",
                "The Accept Radiology Order screen is shown",
                rendered ? "Opened " + page.url()
                           + (ar.lastRoute.isEmpty() ? "" : " (menu route " + ar.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // ================= Accept tab =================

        String dates = ar.enterDateRange(from, to);
        boolean datesOk = ar.datesEntered(from, to);
        step(page, "[Accept] Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        String search = ar.clickSearch();
        // The Sub Group is REQUIRED: without it the screen answers "Please select sub group!" and
        // never searches, so an empty grid would say nothing about the data. Each sub group is tried
        // until one returns records, and the report says which were empty.
        String subGroup = ar.selectSubGroupWithRecords();
        boolean subOk = ar.subGroupSelected();
        step(page, "[Accept] Select the sub group", "Select the Sub Group",
                "A sub group is selected",
                (subOk
                    ? "PASSES because the dropdown holds the choice: " + subGroup
                    : "FAILS: " + subGroup
                      + "  ||  the screen refuses to search without a sub group, answering \"Please "
                      + "select sub group!\", so nothing could be listed."),
                subOk ? "PASS" : "FAIL");

        int acceptRows = ar.rowCount();
        step(page, "[Accept] Click Search", "Click Search",
                "The Test grid is filled",
                (acceptRows > 0 ? "PASSES because the search returned records: " + search
                          : "FAILS because the search returned nothing: " + search),
                acceptRows > 0 ? "PASS" : "FAIL");

        String acceptRowsText = ar.describeRows();
        if (acceptRows == 0) {
            // Say WHY it is empty: which statuses hold anything at all.
            addSummary("[Accept] Records per Sample Status", ar.describeStatusCounts());
        }
        step(page, "[Accept] Verify records exist in Test",
                "Read the Test grid",
                "At least one record is listed",
                (acceptRows > 0
                    ? "PASSES because the Test grid lists " + acceptRowsText
                    : "FAILS because the Test grid is empty: " + acceptRowsText
                      + "  ||  WHAT THIS MEANS: no radiology order exists in this window under ANY of "
                      + "the screen's status filters — see the per-status counts in the summary. There "
                      + "is nothing to accept in this environment; order a radiology test to give this "
                      + "flow something to work with."),
                acceptRows > 0 ? "PASS" : "FAIL");

        boolean acceptOk = false;
        String acceptToast = "";
        if (acceptRows == 0) {
            addSummary("Result (Accept)", "FAILED — no radiology order to accept");
        } else {
            String tick = ar.tickFirstRecord();
            boolean ticked = ar.recordTicked();
            step(page, "[Accept] Tick the checkbox on one record",
                    "Click the tick in the Select column of one record",
                    "The record is ticked",
                    (ticked
                        ? "PASSES because the box and its model both read back as set: " + tick
                        : "FAILS because the record did not tick: " + tick),
                    ticked ? "PASS" : "FAIL");

            if (!ticked) {
                // Deliberate stop: Accept opens its Mark Visit chain even with nothing selected, acting
                // on whichever patient the screen still holds. Going on would mark a visit against a
                // patient this test never chose.
                addSummary("Result (Accept)", "STOPPED — no record was ticked, and Accept would have "
                        + "acted on the screen's leftover patient rather than on a chosen record");
            } else {
                String accept = ar.clickAccept();
                String confirm = ar.confirmYes();
                boolean confirmed = ar.confirmed();
                step(page, "[Accept] Click Accept and confirm",
                        "Click Accept, then Yes on the confirmation",
                        "The confirmation is accepted",
                        (confirmed
                            ? "PASSES: " + accept + " — " + confirm
                            : "FAILS: " + accept + " — " + confirm),
                        confirmed ? "PASS" : "FAIL");
                addSummary("[Accept] Native dialogs raised", ar.lastDialogs.isEmpty() ? "none" : ar.lastDialogs);

                String mv = ar.fillMarkVisit();
                boolean mvOk = ar.markVisitFilled();
                step(page, "[Accept] In Mark Visit, select department, sub department and patient type",
                        "Select the Department, the Sub Department and the Patient Type",
                        "All three are selected",
                        (mvOk
                            ? "PASSES because each dropdown holds its choice: " + mv
                            : "FAILS — a list could not be used: " + mv),
                        mvOk ? "PASS" : "FAIL");

                String mvSave = ar.clickMarkVisit();
                step(page, "[Accept] Click Mark Visit", "Click Mark Visit",
                        "The visit is marked and the acceptance dialog follows",
                        "Clicked Mark Visit [fnMarkVisit] — " + mvSave,
                        mvSave.startsWith("the next dialog") ? "PASS" : "FAIL");

                String rem = ar.fillAcceptanceRemark(remark, films);
                boolean remOk = ar.remarkFilled(remark, films);
                step(page, "[Accept] Enter the remark and the number of films",
                        "In Acceptance Remark, enter the Remark " + remark + " and No of Films " + films,
                        "Both are entered",
                        (remOk
                            ? "PASSES because both boxes read their value back: " + rem
                            : "FAILS — " + rem),
                        remOk ? "PASS" : "FAIL");

                acceptToast = ar.clickOkAndGetToast();
                acceptOk = com.kpj.pages.Investigation_page.Radiology_page.AcceptRadiologyOrder.isSuccess(acceptToast);
                addSummary("[Accept] What the screen put on screen after OK", ar.lastSave);
                step(page, "[Accept] Click OK & success toast", "Click OK; wait for the success toast",
                        "'... accepted successfully' toast",
                        (acceptOk ? "PASSES because the screen answered with a success message: " + acceptToast
                            : "FAILS — " + (acceptToast == null || acceptToast.isEmpty()
                                ? "no message appeared: " + ar.lastSave
                                : "the screen answered: \"" + acceptToast + "\""))
                            + (ar.toastFromObserver
                                ? "  ||  the message was recorded by a mutation observer as the screen "
                                  + "showed it"
                                : ""),
                        acceptOk ? "PASS" : "FAIL");

                addSummary("[Accept] Date range", from + " to " + to);
                addSummary("[Accept] Records", ar.lastRows);
                addSummary("[Accept] Ticked record", ar.lastTick);
                addSummary("[Accept] Mark Visit", ar.lastMarkVisit);
                addSummary("[Accept] Acceptance remark", ar.lastRemarkDialog);
                addSummary("Result (Accept)", acceptOk ? acceptToast : "Not confirmed (\"" + acceptToast + "\")");
            }
        }

        // ================= Reject tab =================
        // A fresh, independent search: the record Accept just saved (if any) no longer qualifies, so
        // Reject cannot reuse it and must find its own.

        String rejectSubGroup = ar.selectSubGroupWithRecords();
        boolean rejectSubOk = ar.subGroupSelected();
        step(page, "[Reject] Select the sub group", "Select the Sub Group",
                "A sub group is selected",
                (rejectSubOk
                    ? "PASSES because the dropdown holds the choice: " + rejectSubGroup
                    : "FAILS: " + rejectSubGroup
                      + "  ||  the screen refuses to search without a sub group, answering \"Please "
                      + "select sub group!\", so nothing could be listed."),
                rejectSubOk ? "PASS" : "FAIL");

        String rejectSearch = ar.clickSearch();
        int rejectRows = ar.rowCount();
        step(page, "[Reject] Click Search", "Click Search",
                "The Test grid is filled",
                (rejectRows > 0 ? "PASSES because the search returned records: " + rejectSearch
                          : "FAILS because the search returned nothing: " + rejectSearch),
                rejectRows > 0 ? "PASS" : "FAIL");

        String rejectRowsText = ar.describeRows();
        step(page, "[Reject] Verify records exist in Test",
                "Read the Test grid",
                "At least one record is listed",
                (rejectRows > 0 ? "PASSES because Test lists " + rejectRowsText
                          : "FAILS because Test is empty: " + rejectRowsText
                            + "  ||  " + ar.describeStatusCounts()),
                rejectRows > 0 ? "PASS" : "FAIL");

        if (rejectRows == 0) {
            addSummary("Result (Reject)", "FAILED — nothing to reject");
        } else {
            String tick = ar.tickFirstRecord();
            boolean ticked = ar.recordTicked();
            step(page, "[Reject] Tick the checkbox on one record",
                    "Click the tick in the Select column of the first record",
                    "The record is ticked",
                    (ticked
                        ? "PASSES because the checkbox and its model both read back as set: " + tick
                          + ". This grid has a real Select column, unlike the Lab screens whose tick is "
                          + "ui-grid's own selection button."
                        : "FAILS because the record did not tick: " + tick),
                    ticked ? "PASS" : "FAIL");

            if (!ticked) {
                addSummary("Result (Reject)", "STOPPED — no record was ticked, and Reject would have "
                        + "acted on the screen's leftover patient rather than on a chosen record");
            } else {
                String reject = ar.clickReject();
                boolean dialogOpen = ar.rejectDialogOpen();
                step(page, "[Reject] Click Reject", "Click Reject",
                        "The Rejection Reason dialog opens",
                        (dialogOpen
                            ? "PASSES because the dialog came up: " + reject
                              + ". Rejecting does NOT pass through Mark Visit, unlike accepting."
                            : "FAILS because the rejection dialog did not open: " + reject),
                        dialogOpen ? "PASS" : "FAIL");

                if (!dialogOpen) {
                    addSummary("Result (Reject)", "FAILED — the rejection dialog never opened");
                } else {
                    String dialog = ar.fillRejectionReason(rejectRemark);
                    boolean filled = ar.rejectionFilled(rejectRemark);
                    step(page, "[Reject] Select the rejection reason and enter the remark",
                            "Select the Rejection Reason and enter the Remark",
                            "Both are entered",
                            (filled
                                ? "PASSES because the reason holds its choice and the remark read back: "
                                  + dialog
                                : "FAILS — the dialog did not take the values: " + dialog),
                            filled ? "PASS" : "FAIL");

                    String rejectToast = ar.clickOkAndGetToast();
                    boolean rejectOk = com.kpj.pages.Investigation_page.Radiology_page.AcceptRadiologyOrder.isSuccess(rejectToast);
                    addSummary("[Reject] What the screen put on screen after OK", ar.lastSave);
                    step(page, "[Reject] Click OK & success toast", "Click OK; wait for the success toast",
                            "'... rejected successfully' toast",
                            (rejectOk ? "PASSES because the screen answered with a success message: "
                                        + rejectToast
                                : "FAILS — " + (rejectToast == null || rejectToast.isEmpty()
                                    ? "no message appeared: " + ar.lastSave
                                    : "the screen answered: \"" + rejectToast + "\""))
                                + (ar.toastFromObserver
                                    ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five "
                                      + "seconds, so it was recorded by a mutation observer as the screen "
                                      + "showed it."
                                    : ""),
                            rejectOk ? "PASS" : "FAIL");

                    addSummary("[Reject] Date range", from + " to " + to);
                    addSummary("[Reject] Sub group", ar.lastSubGroup);
                    addSummary("[Reject] Test records", ar.lastRows);
                    addSummary("[Reject] Selected record", ar.lastTick);
                    addSummary("[Reject] Rejection details", ar.lastRejectDialog);
                    addSummary("[Reject] Native dialogs raised",
                            ar.lastDialogs == null || ar.lastDialogs.isEmpty() ? "none" : ar.lastDialogs);
                    addSummary("Result (Reject)", rejectOk ? rejectToast : "Not confirmed (\"" + rejectToast + "\")");
                }
            }
        }
    }
}
