package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named StoreIndentApproval — referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Store Indent Approval</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Inventory</b> → <b>Transfer</b> → <b>Store Indent Approval</b>.</li>
 *   <li>Select the <b>Requesting Store</b> from its list.</li>
 *   <li>Enter the <b>From Date</b>/<b>To Date</b> — a one-year range ending today.</li>
 *   <li>Click <b>Search</b>. A store with no pending indent answers with an empty <b>Indent List</b>; the next store of the list is then selected and searched, and so on through the whole
 *       list until one returns a record (the report says which stores were tried).</li>
 *   <li>Verify the record is displayed in the <b>Indent List</b> table.</li>
 *   <li>Click that record → verify it is displayed in <b>Indent Details</b>.</li>
 *   <li>Click <b>Print</b> → the report opens in a new tab (a PDF) → its screenshot is taken.</li>
 *   <li>Click <b>Approve</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A passing run APPROVES a real store indent: it leaves the Pending list, so each run uses up one pending indent. When no store has a pending indent in the range the test fails at the
 * Search step and says so. Pin the first store with {@code -Dsia.store=}, the range with {@code -Dsia.from=} / {@code -Dsia.to=} (dd/MM/yyyy).</p>
 */
public class StoreIndentApproval extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public StoreIndentApproval() { super("Inventory_Transfer_StoreIndentApproval"); }

    public static void main(String[] args) {
        StoreIndentApproval t = new StoreIndentApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Transfer - Store Indent Approval", "Inventory > Transfer > Store Indent Approval",
                "&#9888; APPROVES a real store indent: select the Requesting Store (next store of the list until one has records), one-year date range, Search, "
                        + "click the record, Print (report tab screenshot), Approve, verify the success toast.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate today = LocalDate.now();
        String to = System.getProperty("sia.to", today.format(f));
        String from = System.getProperty("sia.from", today.minusYears(1).format(f));
        String pinned = System.getProperty("sia.store", "");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.StoreIndentApproval sia = new com.kpj.pages.Inventory_page.Transfer_page.StoreIndentApproval(page);

        // 1) Navigate
        boolean on = sia.navigateViaMenu();
        step(page, "Open Store Indent Approval screen", "Click Inventory -> Transfer -> Store Indent Approval", "The Store Indent Approval screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Store Indent Approval not reached"); return; }

        List<String> stores = sia.storeNames();
        if (stores.isEmpty()) {
            step(page, "Select requesting store", "Open the Requesting Store list", "The list offers stores", "FAILED — the Requesting Store list is empty", "FAIL");
            addSummary("Result", "FAILED — no stores in the Requesting Store list");
            return;
        }
        // the order the stores are tried in: the pinned one first, then the list order
        List<String> order = new ArrayList<>();
        if (!pinned.isEmpty() && stores.contains(pinned)) order.add(pinned);
        for (String s : stores) if (!s.equals(pinned)) order.add(s);

        // 2) Requesting store (the first one; the Search step moves on through the list when it has no record)
        String first = order.get(0);
        boolean storeOk = sia.selectStore(first);
        step(page, "Select requesting store", "Open the Requesting Store list and click \"" + first + "\"", "The store is selected from the list",
                storeOk ? "Requesting Store \"" + sia.lastStore + "\" selected from the list (" + stores.size() + " stores offered)"
                        : "FAILED — " + sia.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + sia.problems); return; }

        // 3) Date range
        String dates = sia.enterDateRange(from, to);
        boolean datesOk = sia.ok();
        step(page, "Enter date (1 year range)", "Enter the From Date " + from + " and the To Date " + to, "Both dates are entered",
                datesOk ? dates : "FAILED — " + sia.problems + " (" + dates + ")",
                datesOk ? "PASS" : "FAIL");
        if (!datesOk) { addSummary("Result", "FAILED — dates: " + sia.problems); return; }

        // 4) Search; store after store until a record comes back
        List<String> tried = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        tried.add(first);
        int n = sia.clickSearch();
        boolean answered = sia.lastSearchStatus > 0 && sia.lastSearchStatus < 400;
        String foundStore = n > 0 ? sia.lastStore : "";
        System.out.println("StoreIndentApproval: [1/" + order.size() + "] " + first + " -> " + n + " indent(s)");
        for (int i = 1; i < order.size() && n == 0; i++) {
            String next = order.get(i);
            if (!sia.selectStore(next)) { skipped.add(next + " (" + sia.problems + ")"); continue; }
            tried.add(next);
            n = sia.clickSearch();
            if (!(sia.lastSearchStatus > 0 && sia.lastSearchStatus < 400)) skipped.add(next + " (search: " + sia.problems + ")");
            System.out.println("StoreIndentApproval: [" + (i + 1) + "/" + order.size() + "] " + next + " -> " + n + " indent(s)");
            if (n > 0) foundStore = sia.lastStore;
        }
        boolean found = n > 0;
        String triedText = tried.size() == 1 ? "\"" + tried.get(0) + "\"" : tried.size() + " store(s) in list order, from \"" + tried.get(0) + "\" to \"" + tried.get(tried.size() - 1) + "\"";
        step(page, "Click Search (try the next store until a record comes back)", "Click Search; if the store has no record select the next store of the list and search again",
                "The Indent List shows a record",
                found ? n + " indent(s) found for \"" + foundStore + "\" after trying " + triedText
                          + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped)
                      : "FAILED — none of the " + tried.size() + " store(s) tried (" + triedText + ") returned a pending indent between " + from + " and " + to
                          + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped) + (answered ? "" : "; last Search problem: " + sia.problems),
                found ? "PASS" : "FAIL");
        addSummary("Stores tried", tried.size() + " of " + order.size() + (found ? " — records found for \"" + foundStore + "\"" : " — no record in any store"));
        if (!found) { addSummary("Result", "FAILED — no store has a pending indent in " + from + " - " + to); return; }

        // 5) The record is displayed in the Indent List
        List<List<String>> rows = sia.indentRows();
        int bad = sia.incompleteRows(rows);                                // date-time, indent no., status, location, type, requesting store
        boolean populated = !rows.isEmpty() && bad == 0;
        String sample = sia.sample(rows, 2);
        step(page, "Verify records are displayed in the Indent List table", "Read the Indent List after Search", "The indent is listed with its date, number, status and stores",
                populated ? rows.size() + " indent(s) listed, each with date-time, indent no., status, location, type and requesting store; "
                          + "first: " + sample
                          : "FAILED — " + (rows.isEmpty() ? "the table is empty" : bad + " of " + rows.size() + " row(s) lack a date, number, status, location, type or requesting store") + "; " + sample,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Result", "FAILED — Indent List not populated"); return; }

        // 6) Click that record
        String no = sia.selectFirstRecord();
        boolean rowOk = !no.isEmpty() && sia.ok();
        step(page, "Click the record", "Click the Indent No. of the first record in the Indent List", "The indent is selected (highlighted) and its items load",
                rowOk ? "Selected " + no + " (" + sia.lastRowText + ") — row highlighted, " + sia.lastDetailRows + " item row(s) loaded" : "FAILED — " + sia.problems,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — record: " + sia.problems); return; }

        // 7) Indent Details
        List<String> det = sia.detailRows();
        boolean detOk = !det.isEmpty() && det.get(0).trim().matches("^\\d+\\s+\\S+.*");          // a numbered item line: Sr.No. then the item code
        step(page, "Verify the record is displayed in Indent Details", "Read Indent Details after selecting the indent", "The indent's item lines are shown (Sr.No., Item Code, Item Name ...)",
                detOk ? "Indent Details lists " + det.size() + " item line(s) for " + no + "; first: " + det.get(0)
                      : "FAILED — Indent Details " + (det.isEmpty() ? "is empty" : "holds no numbered item line: " + det),
                detOk ? "PASS" : "FAIL");

        // 8) Print -> report tab -> screenshot
        PdfReport.Result pdf = sia.clickPrintAndCapture();
        boolean tab = !pdf.url.isEmpty();
        boolean reportOk = tab && !pdf.blank && pdf.url.toLowerCase().contains("indentid=");
        String evidence = tab ? pdf.diagnostics + (pdf.png != null ? " The screenshot is the report's first page rendered from the file itself." : "")
                              : "no report tab opened" + (sia.lastToast.isEmpty() ? "" : " — the screen said \"" + sia.lastToast + "\"");
        String verdict = (reportOk ? "Report opened in a new tab for indent " + no + ": " : "FAILED — ") + evidence;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Click Print; report opens in a new tab (screenshot)", "Click Print with the indent selected; take the report screenshot",
                    "The indent report opens in a new tab", verdict, reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Click Print; report opens in a new tab (screenshot)", "Click Print with the indent selected; take the report screenshot",
                    "The indent report opens in a new tab", verdict, reportOk ? "PASS" : "FAIL");
        }

        // 9) Approve -> success toast
        String toast = sia.clickApprove();
        String low = toast.toLowerCase();
        boolean approved = low.contains("approved successfully") && !low.contains("undefined");
        boolean left = approved && sia.indentLeftPendingList(no);
        step(page, "Click Approve; verify the success toast", "Click Approve (the selected indent is highlighted)", "'Store Indent Approved Successfully' toast",
                approved ? "The screen answered \"" + toast + "\" for " + no + "; " + (left ? "the indent then left the Pending list" : "the indent is still listed after the screen searched again")
                         : toast.isEmpty() ? "FAILED — no message appeared after Approve" + (sia.ok() ? "" : " (" + sia.problems + ")") : "FAILED — the screen answered \"" + toast + "\"",
                approved ? "PASS" : "FAIL");

        addSummary("Dates", from + " - " + to);
        addSummary("Store / indent", foundStore + " / " + no + " — " + sia.lastRowText);
        addSummary("Items", sia.lastDetailText);
        addSummary("Report", tab ? pdf.url : "(none)");
        addSummary("Result", approved ? toast : "Approve not confirmed (\"" + toast + "\")");
    }
}
