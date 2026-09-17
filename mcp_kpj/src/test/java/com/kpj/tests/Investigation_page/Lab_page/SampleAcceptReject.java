package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SampleAcceptReject — referenced by its fully-qualified name.

/**
 * Investigation &gt; Lab &gt; <b>Sample Accept/Reject</b>.
 *
 * <p>One screen with two tabs, both driven here: <b>Accept</b> then <b>Reject</b>. The screen is opened
 * once and each tab runs its own search, since Accept consumes whichever record it saves and Reject
 * needs a record still awaiting a decision.</p>
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Lab</b> → <b>Sample Accept/Reject</b>.</li>
 *   <li><b>Accept tab</b>: enter From/To date, enter the MRN and click the search symbol, click
 *       <b>Search</b>, verify records exist in <b>Test Details</b>, click the tick symbol on one record,
 *       click <b>Accept</b>, fill the <b>Acceptance Remark</b> dialog (Remark, Sample Suitability, Sample
 *       Suitability Remark, Sample Accepted Date Time), click <b>Save</b>, verify the success toast.</li>
 *   <li><b>Reject tab</b>: re-search (its own date range and MRN, or the Not Accepted filter across all
 *       patients), verify records exist, click the tick symbol on one record, click <b>Reject</b>, fill
 *       the <b>Rejection Details</b> dialog (Rejection Reason, Remark), click <b>Save</b>, verify the
 *       generated sample rejection report.</li>
 * </ol>
 *
 * <p>Both tabs are DATA-DEPENDENT. Accept needs a lab order whose sample has not been accepted yet: if
 * the requested MRN returns nothing, it falls back to discovering another off an unfiltered search
 * (reporting which MRN it used). Reject needs a sample still awaiting a decision under its own filter;
 * it runs on its own fresh search regardless of how the Accept tab went, since a sample the Accept tab
 * just accepted no longer qualifies.</p>
 *
 * <p>Pin values with {@code -Dmrn=}, {@code -Dfrom=}, {@code -Dto=}, {@code -Dremark=} (Accept),
 * {@code -DrejectMrn=}, {@code -DrejectRemark=}, {@code -Dstatus=} (Reject).</p>
 *
 * <p>&#9888; A successful run ACCEPTS A REAL SAMPLE and REJECTS A REAL SAMPLE in the target
 * environment.</p>
 */
public class SampleAcceptReject extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SampleAcceptReject() { super("Investigation_SampleAcceptReject"); }

    public static void main(String[] args) {
        SampleAcceptReject t = new SampleAcceptReject();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Investigation - Lab - Sample Accept/Reject", "Investigation > Lab > Sample Accept/Reject",
                "&#9888; ACCEPTS A REAL SAMPLE on the Accept tab and REJECTS A REAL SAMPLE on the Reject "
                        + "tab: search, tick a record, act, then confirm on each tab.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));

        // Accept tab
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");
        String mrn = System.getProperty("mrn", "100000072");
        String remark = System.getProperty("remark", "Auto acceptance remark " + stamp);
        String suitRemark = System.getProperty("suitRemark", "Auto suitability remark " + stamp);
        String acceptedOn = System.getProperty("acceptedOn",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // Reject tab
        String rejectMrn = System.getProperty("rejectMrn", "");
        String status = System.getProperty("status", "Not Accepted");
        String rejectRemark = System.getProperty("rejectRemark", "Auto rejection remark " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Lab_page.SampleAcceptReject sar =
                new com.kpj.pages.Investigation_page.Lab_page.SampleAcceptReject(page);

        // Navigate once — both tabs live on this one screen
        boolean rendered = sar.navigateViaMenu(BASE);
        if (!rendered) addSummary("Lab submenu offered", sar.lastMenu);
        step(page, "Open Sample Accept/Reject screen",
                "Click Investigation -> Lab -> Sample Accept/Reject",
                "The Sample Accept/Reject screen is shown",
                rendered ? "Opened " + page.url()
                           + (sar.lastRoute.isEmpty() ? "" : " (menu route " + sar.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Lab submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", sar.describeControls());

        // ================= Accept tab =================

        String dates = sar.enterDateRange(from, to);
        boolean datesOk = sar.datesEntered(from, to);
        step(page, "[Accept] Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates
                      + ". They are datepickers that arrive pre-filled, so each is cleared before typing "
                      + "— typing into them without clearing appends to what is there and the picker "
                      + "then discards the whole value."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        String mrnSearch = sar.enterMrnAndClickSearchSymbol(mrn);
        boolean mrnOk = sar.mrnEntered(mrn);
        step(page, "[Accept] Enter MRN and click the search symbol",
                "Enter the MRN " + mrn + " and click the search symbol beside it",
                "The MRN is entered and the search symbol is clicked",
                (mrnOk
                    ? "PASSES because the box read the value back and the symbol was clicked: " + mrnSearch
                    : "FAILS: " + mrnSearch),
                mrnOk ? "PASS" : "FAIL");

        String search = sar.clickSearch();
        int acceptRows = sar.rowCount();

        // The environment may hold no un-accepted sample for that patient. Fall back to an MRN that does
        // have one, and say so — that is a data gap, not a fault in the screen.
        String mrnUsed = mrn;
        String fallbackNote = "";
        if (acceptRows == 0) {
            String discovered = sar.findMrnWithSamples(from, to);
            if (!discovered.isEmpty()) {
                sar.enterMrnAndClickSearchSymbol(discovered);
                search = sar.clickSearch();
                acceptRows = sar.rowCount();
                mrnUsed = discovered;
                fallbackNote = "  ||  MRN " + mrn + " returned nothing in this window, so the flow searched "
                        + "with the MRN box empty, took MRN " + discovered + " off the first row and "
                        + "repeated the search. That is a data gap for " + mrn + ", not a fault in the "
                        + "screen.";
            }
        }
        step(page, "[Accept] Click Search", "Click Search",
                "The Test Details grid is filled",
                (acceptRows > 0 ? "PASSES because the search returned rows: " + search + fallbackNote
                          : "FAILS because the search returned nothing: " + search
                            + "  ||  no lab order was found for MRN " + mrn + " between " + from + " and "
                            + to + ", and an unfiltered search found none either. The screen lists only "
                            + "samples that have NOT been accepted yet, so this means every sample in "
                            + "this environment has already been accepted - a data gap, not a fault in "
                            + "the screen. Order a lab test and collect its sample to give this flow "
                            + "something to accept."),
                acceptRows > 0 ? "PASS" : "FAIL");
        addSummary("[Accept] Sample Status filter", sar.describeStatusCounts());
        addSummary("[Accept] MRN used", mrnUsed + (fallbackNote.isEmpty() ? "" : " (substituted)"));

        String acceptRowsText = sar.describeRows();
        step(page, "[Accept] Verify records exist in Test Details",
                "Read the Test Details grid",
                "At least one record is listed",
                (acceptRows > 0
                    ? "PASSES because Test Details lists " + acceptRowsText
                    : "FAILS because Test Details is empty: " + acceptRowsText),
                acceptRows > 0 ? "PASS" : "FAIL");

        boolean acceptOk = false;
        String acceptToast = "";
        if (acceptRows == 0) {
            addSummary("Result (Accept)", "FAILED — nothing to accept");
        } else {
            String tick = sar.tickFirstRow();
            boolean ticked = sar.rowSelected();
            step(page, "[Accept] Click the tick symbol on one record",
                    "Click the tick symbol on the first record",
                    "The record is selected",
                    (ticked
                        ? "PASSES because the row reports itself selected: " + tick
                          + ". The tick is ui-grid's own selection button, not a checkbox — a search for "
                          + "an input would find nothing — and selection is read back off the row's "
                          + "scope, so a click that lands without selecting is not counted."
                        : "FAILS because the row did not become selected: " + tick),
                    ticked ? "PASS" : "FAIL");

            if (!ticked) {
                addSummary("Result (Accept)", "FAILED — no record could be selected");
            } else {
                String accept = sar.clickAccept();
                boolean dialogOpen = sar.acceptDialogOpen();
                step(page, "[Accept] Click Accept", "Click Accept",
                        "The acceptance dialog opens",
                        (dialogOpen
                            ? "PASSES because the dialog came up: " + accept
                            : "FAILS because no dialog appeared: " + accept),
                        dialogOpen ? "PASS" : "FAIL");

                if (!dialogOpen) {
                    addSummary("Result (Accept)", "FAILED — the acceptance dialog never opened");
                } else {
                    String dialog = sar.fillDialog(remark, suitRemark, acceptedOn);
                    boolean filled = sar.dialogFilled(remark, suitRemark, acceptedOn);
                    step(page, "[Accept] Enter the acceptance details",
                            "Enter the Acceptance Remark, select the Sample Suitability, enter the "
                                    + "Sample Suitability Remark and the Sample Accepted Date Time",
                            "All four are entered",
                            (filled
                                ? "PASSES because each control read its OWN value back: " + dialog
                                  + ". The two remarks are separate models with no labels of their own, "
                                  + "so both are pinned by ng-model — note the application's spelling, "
                                  + "samplesutabilityremark."
                                : "FAILS — a value did not land in its own field: " + dialog),
                            filled ? "PASS" : "FAIL");

                    acceptToast = sar.saveAndGetToast();
                    acceptOk = com.kpj.pages.Investigation_page.Lab_page.SampleAcceptReject.isSuccess(acceptToast);
                    addSummary("[Accept] What the screen put on screen after Save", sar.lastPopupsSeen);

                    String actual = acceptToast == null || acceptToast.isEmpty()
                            ? "No message appeared — " + sar.lastSave
                            : (acceptOk ? acceptToast
                                        : "Save not confirmed — the screen answered: \"" + acceptToast + "\"");
                    boolean malformed = acceptToast != null && acceptToast.toLowerCase().contains("undefined");
                    step(page, "[Accept] Click Save & success toast", "Click Save; wait for the success toast",
                            "'... saved successfully' toast",
                            (acceptOk ? "PASSES because the screen answered with a success message: "
                                      : "FAILS — ") + actual
                                + (sar.toastFromObserver
                                    ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five "
                                      + "seconds. This one was recorded by a mutation observer at the "
                                      + "moment the screen showed it."
                                    : "")
                                + (malformed
                                    ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + acceptToast + "\", so "
                                      + "the screen cannot name what it saved."
                                    : ""),
                            (acceptOk && !malformed) ? "PASS" : "FAIL");

                    addSummary("[Accept] Date range", from + " to " + to);
                    addSummary("[Accept] Test Details", sar.lastRows);
                    addSummary("[Accept] Selected record", sar.lastTick);
                    addSummary("[Accept] Acceptance dialog", sar.lastDialog);
                    addSummary("Result (Accept)", acceptOk ? acceptToast : "Not confirmed (\"" + acceptToast + "\")");
                }
            }
        }

        // ================= Reject tab =================
        // A fresh, independent search: the record Accept just saved (if any) no longer qualifies, so
        // Reject cannot reuse it and must find its own.

        addSummary("[Reject] Sample Status filter", sar.selectStatus(status));

        String rejectMrnSearch = rejectMrn.isEmpty()
                ? "no MRN was given, so the search runs across all patients"
                : sar.enterMrnAndClickSearchSymbol(rejectMrn);
        boolean rejectMrnOk = rejectMrn.isEmpty() || sar.mrnEntered(rejectMrn);
        step(page, "[Reject] Enter MRN and click the search symbol",
                rejectMrn.isEmpty() ? "No MRN pinned — search across all patients"
                              : "Enter the MRN " + rejectMrn + " and click the search symbol beside it",
                "The MRN is entered and the search symbol is clicked",
                (rejectMrnOk ? "PASSES: " + rejectMrnSearch : "FAILS: " + rejectMrnSearch),
                rejectMrnOk ? "PASS" : "FAIL");

        String rejectSearch = sar.clickSearch();
        int rejectRows = sar.rowCount();
        step(page, "[Reject] Click Search", "Click Search",
                "The Test Details grid is filled",
                (rejectRows > 0
                    ? "PASSES because the search returned rows: " + rejectSearch
                    : "FAILS because the search returned nothing under the \"" + status + "\" filter: "
                      + rejectSearch),
                rejectRows > 0 ? "PASS" : "FAIL");

        String rejectRowsText = sar.describeRows();
        step(page, "[Reject] Verify records exist in Test Details",
                "Read the Test Details grid",
                "At least one record is listed",
                (rejectRows > 0
                    ? "PASSES because Test Details lists " + rejectRowsText
                    : "FAILS because Test Details is empty: " + rejectRowsText
                      + "  ||  WHAT THIS MEANS: the screen lists samples by status, and under \"" + status
                      + "\" there is nothing left in this environment — every sample has already been "
                      + "accepted. There is no sample awaiting a decision to reject. Order a lab test "
                      + "and collect its sample, or pass -Dstatus=All to work with a decided one."),
                rejectRows > 0 ? "PASS" : "FAIL");

        if (rejectRows == 0) {
            addSummary("Result (Reject)", "FAILED — no sample awaiting a decision, so nothing could be rejected");
        } else {
            String tick = sar.tickFirstRow();
            boolean ticked = sar.rowSelected();
            step(page, "[Reject] Click the tick symbol on one record",
                    "Click the tick symbol on the first record",
                    "The record is selected",
                    (ticked
                        ? "PASSES because the row reports itself selected: " + tick
                          + ". The tick is ui-grid's own selection button, not a checkbox, and the count "
                          + "is of selected ROWS — ui-grid draws its marker in more than one container."
                        : "FAILS because the row did not become selected: " + tick),
                    ticked ? "PASS" : "FAIL");

            if (!ticked) {
                addSummary("Result (Reject)", "FAILED — no record could be selected");
            } else {
                String reject = sar.clickReject();
                boolean dialogOpen = sar.rejectDialogOpen();
                step(page, "[Reject] Click Reject", "Click Reject",
                        "The Rejection Details dialog opens",
                        (dialogOpen
                            ? "PASSES because the dialog came up: " + reject
                            : "FAILS because no dialog appeared: " + reject),
                        dialogOpen ? "PASS" : "FAIL");

                if (!dialogOpen) {
                    addSummary("Result (Reject)", "FAILED — the rejection dialog never opened");
                } else {
                    String dialog = sar.fillRejectDialog(rejectRemark);
                    boolean filled = sar.rejectDialogFilled(rejectRemark);
                    step(page, "[Reject] Select the rejection reason and enter the remark",
                            "In Rejection Details, select the Rejection Reason and enter the Remark",
                            "Both are entered",
                            (filled
                                ? "PASSES because the reason holds its choice and the remark read back: "
                                  + dialog
                                : "FAILS — the dialog did not take the values: " + dialog),
                            filled ? "PASS" : "FAIL");

                    int tabsBefore = page.context().pages().size();
                    String rejectToast = sar.saveRejectAndGetToast();
                    boolean rejectOk = com.kpj.pages.Investigation_page.Lab_page.SampleAcceptReject.isSuccess(rejectToast);
                    addSummary("[Reject] What the screen put on screen after Save", sar.lastRejectSave);
                    step(page, "[Reject] Click Save", "Click Save in the Rejection Details dialog",
                            "The rejection is saved",
                            (rejectOk
                                ? "PASSES because the screen answered with a success message: " + rejectToast
                                : "FAILS — " + (rejectToast == null || rejectToast.isEmpty()
                                    ? "no message appeared: " + sar.lastRejectSave
                                    : "the screen answered: \"" + rejectToast + "\"")),
                            rejectOk ? "PASS" : "FAIL");

                    String report = sar.captureRejectionReport(tabsBefore, 9000);
                    boolean reportOk = !sar.reportBlank && sar.lastReportUrl != null && !sar.lastReportUrl.isEmpty();
                    addSummary("[Reject] Rejection report", report);
                    if (sar.lastReportFile != null && !sar.lastReportFile.isEmpty()) {
                        addSummary("[Reject] Rejection report image", sar.lastReportFile);
                    }
                    step(page, "[Reject] Verify the sample rejection report is generated",
                            "Save should generate the sample rejection report",
                            "A report opens and carries content",
                            (reportOk
                                ? "PASSES because a report was produced and it has content on it: " + report
                                : "FAILS — " + (sar.lastReportUrl == null || sar.lastReportUrl.isEmpty()
                                    ? "NO report was generated by the save: " + report
                                    : "the report opened at " + sar.lastReportUrl + " but it is BLANK: "
                                      + report + ". A document with nothing printed on it is not a "
                                      + "generated report, so this FAILS on the report even though the "
                                      + "rejection itself was saved.")),
                            reportOk ? "PASS" : "FAIL");

                    addSummary("[Reject] Test Details", sar.lastRows);
                    addSummary("[Reject] Selected record", sar.lastTick);
                    addSummary("[Reject] Rejection details", sar.lastRejectDialog);
                    addSummary("Result (Reject)", rejectOk ? rejectToast : "Not confirmed (\"" + rejectToast + "\")");
                }
            }
        }
    }
}
