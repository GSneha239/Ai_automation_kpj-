package com.kpj.tests.Billing_page.DoctorShare_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named DoctorBill — referenced by its fully-qualified name.

/**
 * Billing &gt; Doctor Share &gt; <b>Doctor Bill</b> ({@code #/Doctorbill}).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Billing</b> → <b>Doctor Share</b> → <b>Doctor Bill</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b> — a one-year range ending today.</li>
 *   <li>Click <b>Search</b>; verify the records are displayed in <b>Charges Details</b>.</li>
 *   <li>Select a <b>Cash Counter</b>; enter the <b>Remark</b>; click <b>Save</b>; verify the success toast.</li>
 * </ol>
 *
 * <p><b>One step the written steps do not say:</b> a charge has to be ticked in Charges Details before Save (that is what fills Bill Details and the Total Service Rate / Payble Share
 * boxes) — the first charge that is not settled yet is ticked. The screen's OPD / IPD / ALL radio is left on its default, OPD.</p>
 *
 * <p>&#9888; A passing run SAVES a real doctor bill (it settles the ticked charge). Pin the Cash Counter with {@code -Ddb.counter=}, the Remark with {@code -Ddb.remark=}.</p>
 */
public class DoctorBill extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DoctorBill() { super("Billing_DoctorShare_DoctorBill"); }

    public static void main(String[] args) {
        DoctorBill t = new DoctorBill();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - Doctor Share - Doctor Bill", "Billing > Doctor Share > Doctor Bill",
                "&#9888; SAVES one REAL doctor bill: enter a one-year date range, Search, charges are listed in Charges Details, (tick a charge), select a Cash Counter, "
                        + "enter the Remark, click Save, verify the success toast.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        String remark = System.getProperty("db.remark", "Automated doctor bill remark " + stamp);
        String to = LocalDate.now().format(DMY), from = LocalDate.now().minusYears(1).format(DMY);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Billing_page.DoctorShare_page.DoctorBill db = new com.kpj.pages.Billing_page.DoctorShare_page.DoctorBill(page);

        // 1) Navigate
        boolean on = db.navigateViaMenu();
        step(page, "Open Doctor Bill screen", "Click Billing -> Doctor Share -> Doctor Bill", "The Doctor Bill screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Doctor Bill not reached"); return; }

        // 2) Dates
        String dates = db.enterDateRange(from, to);
        boolean datesOk = db.ok();
        step(page, "Enter from date and to date", "Enter the From Date " + from + " and the To Date " + to + " (a one-year range)", "Both dates are entered",
                datesOk ? "From Date " + db.lastFrom + ", To Date " + db.lastTo + " (" + dates + ")" : "FAILED — " + db.problems, datesOk ? "PASS" : "FAIL");
        if (!datesOk) { addSummary("Result", "FAILED — dates: " + db.problems); return; }

        // 3) Search
        int n = db.clickSearch();
        boolean searched = db.lastSearchStatus > 0 && db.lastSearchStatus < 400;
        step(page, "Click search", "Click Search", "The search is answered", searched ? "Search answered HTTP " + db.lastSearchStatus : "FAILED — " + db.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — Search: " + db.problems); return; }

        // 4) Charges Details
        boolean listed = n > 0 && db.chargeHeaders().stream().anyMatch(h -> h.equalsIgnoreCase("Bill No."));
        step(page, "Verify records are displayed in Charges Details", "Read Charges Details after Search", "The charges are listed with their bill no., service and doctor share",
                listed ? n + " charge(s) listed (" + db.lastUnsettled + " not settled yet); columns " + db.chargeHeaders() + "; first: " + db.sample(2)
                       : "FAILED — Charges Details shows " + n + " charge(s) for " + from + " - " + to + " (OPD)" + (db.lastToast.isEmpty() ? "" : "; the screen said \"" + db.lastToast + "\""),
                listed ? "PASS" : "FAIL");
        if (!listed) { addSummary("Result", "FAILED — no charges listed"); return; }

        // 5) Tick a charge (not in the written steps: Save needs one)
        String charge = db.tickFirstUnsettledCharge();
        boolean ticked = !charge.isEmpty() && db.ok() && db.billDetailsFilled();
        step(page, "Tick a charge in Charges Details", "Tick the first charge that is not settled — not in the written steps: Bill Details and Save need a ticked charge",
                "The charge is ticked and Bill Details shows its doctor and amounts",
                ticked ? "Ticked: " + charge + "; " + db.lastBillDetails : "FAILED — " + (db.ok() ? "ticking did not fill Bill Details (" + db.billDetails() + ")" : db.problems), ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no charge could be ticked"); return; }

        // 6) Cash counter
        List<String> counters = db.cashCounters();
        String pinned = System.getProperty("db.counter", "");
        String pick = !pinned.isEmpty() && counters.contains(pinned) ? pinned : counters.isEmpty() ? "" : counters.get(0);
        boolean counterOk = !pick.isEmpty() && db.selectCashCounter(pick);
        step(page, "Select cash counter", "Open the Cash Counter list and click \"" + pick + "\"", "The cash counter is selected from the list",
                counterOk ? "Cash Counter \"" + db.lastCounter + "\" selected (" + counters.size() + " counters offered)" : "FAILED — " + (counters.isEmpty() ? "the Cash Counter list is empty" : db.problems),
                counterOk ? "PASS" : "FAIL");
        if (!counterOk) { addSummary("Result", "FAILED — cash counter: " + (counters.isEmpty() ? "empty list" : db.problems)); return; }

        // 7) Remark
        String typed = db.enterRemark(remark);
        boolean remarkOk = typed.equals(remark) && db.ok();
        step(page, "Enter remark", "Enter the Remark " + remark, "The remark is entered", remarkOk ? typed : "FAILED — " + (db.ok() ? "the box holds \"" + typed + "\"" : db.problems), remarkOk ? "PASS" : "FAIL");

        // 8) Save
        boolean clicked = db.clickSave();
        step(page, "Click save", "Click Save", "The Save action fires", clicked ? "Clicked Save" : "FAILED — " + db.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save was not clicked"); return; }

        // 9) Success toast
        String toast = db.waitForSaveToast();
        boolean saved = com.kpj.pages.Billing_page.DoctorShare_page.DoctorBill.isSuccess(toast);
        step(page, "Verify success toast message", "Wait for the success toast", "'... successfully' toast",
                toast.isEmpty() ? "FAILED — no message appeared after Save" + (db.lastSaveAnswer.isEmpty() ? "" : " [server: " + db.lastSaveAnswer + "]")
                        : saved ? toast + (db.lastSaveAnswer.isEmpty() ? "" : "  [server: " + db.lastSaveAnswer + "]")
                        : "FAILED — the screen answered \"" + toast + "\"" + (db.lastSaveAnswer.isEmpty() ? "" : " [server: " + db.lastSaveAnswer + "]"),
                saved ? "PASS" : "FAIL");

        int extra = db.closeExtraTabs();
        addSummary("[Doctor Bill] Saved", "Charge " + db.lastCharge + " | Cash Counter " + db.lastCounter + " | Remark " + db.lastRemark + " | " + (saved ? toast : "not confirmed (\"" + toast + "\")")
                + (extra > 0 ? " | the save opened " + extra + " extra tab(s) (closed)" : ""));
    }
}
