package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.util.List;

// The page object is also named ItemSaleReturn — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Item Sale Return</b>.
 *
 * <ol>
 *   <li>Open <b>Inventory</b> → <b>POS</b> → <b>Item Sale Return</b>; click <b>New</b>.</li>
 *   <li>Enter the <b>MRN</b>, select the <b>Store</b> (Pharmacy Main Store), click <b>Get Sales</b>.</li>
 *   <li>In the <b>Sales Item Search</b> popup enter the <b>From</b> and <b>To</b> date, click <b>Search</b>, tick the sale's checkbox, tick the checkbox of the
 *       line that fills the table below, click <b>OK</b>.</li>
 *   <li>Enter the <b>Remark</b>; <b>Save</b>; verify the report opens in another tab and the success toast.</li>
 * </ol>
 *
 * <p><b>The MRN is not hardcoded</b> (regression use). A return needs a patient who has a sale with something left to return, so the MRNs come from the Patient
 * Search popup beside the MRN box ({@code -Dsalereturn.maxtries}, default 15) and each is tried — MRN, store, Get Sales, dates, Search, tick — until one has a sale
 * whose line still has a balance. That scan is silent; the steps are then run in order on the patient it found. {@code -Dsalereturn.mrn=} pins one. The dates are
 * picked in the popup's calendar (typed characters are discarded): From = today minus {@code -Dsalereturn.daysback} (default 30), To = today.</p>
 *
 * <p>The line's Return Quantity is not in the steps and stays blank — the screen returns the whole balance. The return is created "Awaiting approval"; this test
 * never verifies / approves it. It SAVES a real item sale return (stock is not changed until it is approved). Run it headed (the default): the report is a PDF tab
 * that headless Chromium cannot show. Pin the store with {@code -Dsalereturn.store=}.</p>
 */
public class ItemSaleReturn extends DevHisBase {

    static final String STORE = System.getProperty("salereturn.store", "Pharmacy Main Store");
    static final int DAYS_BACK = Integer.getInteger("salereturn.daysback", 30);
    static final int MAX_TRIES = Integer.getInteger("salereturn.maxtries", 15);
    static final String PINNED_MRN = System.getProperty("salereturn.mrn", "");

    public ItemSaleReturn() { super("Inventory_POS_ItemSaleReturn"); }

    public static void main(String[] args) {
        ItemSaleReturn t = new ItemSaleReturn();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** A screenshot of the main page: brought to the front first and retried — a headed window left in the background can stall Playwright's capture. */
    private byte[] shot() {
        for (int i = 0; i < 2; i++) {
            try {
                page.bringToFront();
                return page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(i == 0 ? 5000 : 8000));
            } catch (Exception e) { System.out.println("shot attempt " + (i + 1) + " failed: " + String.valueOf(e.getMessage()).split("\\n")[0]); }
        }
        return null;
    }

    private void stepShot(String name, String desc, String expected, String actual, String status) {
        byte[] png = shot();
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(page, name, desc, expected, actual, status);
    }

    private static String dmy(LocalDate d) { return d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    @Override
    protected void body() {
        meta("Inventory - POS - Item Sale Return", "Inventory > POS > Item Sale Return",
                "New: MRN, store (Pharmacy Main Store), Get Sales; in the Sales Item Search popup pick from/to date, Search, tick the sale and its line, OK; "
                        + "remark; Save; verify the report in another tab and the success toast. SAVES a real item sale return (awaiting approval).");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.ItemSaleReturn isr = new com.kpj.pages.Inventory_page.POS_page.ItemSaleReturn(page);

        // 1) Inventory > POS > Item Sale Return, New
        boolean list = isr.navigateViaMenu();
        stepShot("Open Item Sale Return", "Click Inventory -> POS -> Item Sale Return", "The Item Sale Return list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — Item Sale Return not reached"); return; }

        boolean form = isr.clickNew();
        stepShot("Click New", "Click New (AddItemsalereturn)", "The new Item Sale Return form is shown",
                form ? "Form opened (" + page.url() + ")" : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the Item Sale Return form did not open"); return; }

        // 2) find a patient with something to return (silent scan: MRN, store, Get Sales, dates, Search, tick)
        LocalDate to = LocalDate.now(), from = to.minusDays(DAYS_BACK);
        List<String> candidates = PINNED_MRN.isEmpty() ? isr.candidateMrns(MAX_TRIES, (MAX_TRIES + 9) / 10) : List.of(PINNED_MRN);
        String chosen = "", chosenSale = "";
        StringBuilder skipped = new StringBuilder();
        int n = 0;
        for (String mrn : candidates) {
            n++;
            isr.enterMrnAndSearch(mrn);
            if (!isr.storeSelected()) isr.selectStore(STORE);
            String why;
            if (!isr.clickGetSales()) {
                isr.dismissConfirms();
                why = "no Sales Item Search popup";
            } else {
                isr.pickDate("ItemSale.fromdate", from);
                isr.pickDate("ItemSale.todate", to);
                List<String> sales = isr.clickSearchInPopup();
                why = sales.isEmpty() ? "no sales in the range" : "sales found but nothing left to return";
                for (int i = 0; i < sales.size(); i++) {
                    if (isr.tickSale(i) && isr.firstBalance() > 0) { chosen = mrn; chosenSale = isr.lastSale; break; }
                    isr.untickSale(i);
                }
                isr.closePopup();
            }
            System.out.println("ItemSaleReturn: candidate " + n + "/" + candidates.size() + " " + mrn + " -> " + (chosen.isEmpty() ? why : "returnable sale " + chosenSale));
            if (!chosen.isEmpty()) break;
            skipped.append(skipped.length() == 0 ? "" : ", ").append(mrn).append(" (").append(why).append(")");
        }
        if (chosen.isEmpty()) {
            stepShot("Enter MRN", "Enter an MRN (from the Patient Search popup — not hardcoded)", "A patient with a sale that still has a balance is found",
                    "NO patient out of " + candidates.size() + " candidate(s) has a returnable sale between " + dmy(from) + " and " + dmy(to) + (skipped.length() == 0 ? "" : ": " + skipped)
                            + " — run the Pharmacy Sale/Dispense test (or sell something) first", "FAIL");
            addSummary("Result", "FAILED — no patient with a returnable sale");
            return;
        }

        // 3) the steps, on that patient
        String held = isr.enterMrnAndSearch(chosen);
        stepShot("Enter MRN", "Enter an MRN (from the Patient Search popup — not hardcoded) and search", "The MRN is entered",
                isr.ok() ? "Entered " + held + " (found by trying the popup's patients in turn" + (skipped.length() == 0 ? "" : "; skipped before it: " + skipped) + ")" : "Problem — " + isr.problems,
                isr.ok() ? "PASS" : "FAIL");
        if (!isr.ok()) { addSummary("Result", "FAILED — MRN: " + isr.problems); return; }

        String store = isr.selectStore(STORE);
        stepShot("Select store", "Select the Store (" + STORE + ")", "The store is selected",
                store.isEmpty() || !isr.ok() ? "Not selected — " + isr.problems : "Selected \"" + store + "\"", store.isEmpty() || !isr.ok() ? "FAIL" : "PASS");
        if (store.isEmpty() || !isr.ok()) { addSummary("Result", "FAILED — store: " + isr.problems); return; }

        boolean popup = isr.clickGetSales();
        stepShot("Click Get Sales", "Click Get Sales", "The Sales Item Search popup opens",
                popup ? "The popup is open" + (isr.lastPatient.isEmpty() ? "" : " — " + isr.patientName()) : "Problem — " + isr.problems, popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED — Get Sales: " + isr.problems); return; }

        // 4) in the popup: from / to date, Search, the two ticks, OK
        String f = isr.pickDate("ItemSale.fromdate", from);
        String t = isr.ok() ? isr.pickDate("ItemSale.todate", to) : "";
        stepShot("Enter from date and to date", "Pick the From date (" + dmy(from) + ") and the To date (" + dmy(to) + ") in the popup's calendars", "Both dates are entered",
                isr.ok() && !f.isEmpty() && !t.isEmpty() ? "From " + f + ", To " + t : "Problem — " + isr.problems, isr.ok() && !f.isEmpty() && !t.isEmpty() ? "PASS" : "FAIL");
        if (!isr.ok() || f.isEmpty() || t.isEmpty()) { addSummary("Result", "FAILED — dates: " + isr.problems); return; }

        List<String> sales = isr.clickSearchInPopup();
        int idx = -1;
        for (int i = 0; i < sales.size(); i++) if (sales.get(i).contains(chosenSale)) idx = i;
        stepShot("Click Search", "Click Search in the popup", "The patient's sales are listed in the table",
                idx >= 0 ? sales.size() + " sale(s) listed, including " + chosenSale + ": " + sales : "The sale " + chosenSale + " is not listed (" + sales + ")", idx >= 0 ? "PASS" : "FAIL");
        if (idx < 0) { addSummary("Result", "FAILED — the sale was not listed"); return; }

        boolean sale = isr.tickSale(idx);
        List<String> lines = isr.itemRows();
        stepShot("Tick the checkbox to select the row in the table", "Tick the checkbox of sale " + chosenSale, "The row is ticked and its line(s) populate the table below",
                sale && !lines.isEmpty() ? "Ticked " + chosenSale + "; the table below shows " + lines.size() + " line(s): " + lines : "Problem — " + (isr.ok() ? "the table below is empty" : isr.problems),
                sale && !lines.isEmpty() ? "PASS" : "FAIL");
        if (!sale || lines.isEmpty()) { addSummary("Result", "FAILED — sale row: " + isr.problems); return; }

        boolean item = isr.tickItem();
        stepShot("Tick the checkbox to select the row populated in the table below", "Tick the checkbox of the line in the table below", "The line is ticked",
                item ? "Ticked: " + isr.lastItem : "Problem — " + isr.problems, item ? "PASS" : "FAIL");
        if (!item) { addSummary("Result", "FAILED — line tick: " + isr.problems); return; }

        int rows = isr.clickOk();
        boolean landed = isr.ok() && rows >= 1 && isr.mainRowTicked();
        stepShot("Click OK", "Click OK in the popup", "The popup closes and the line is listed in Item Sales Return Details",
                landed ? "The popup closed; the details table has " + rows + " line(s): " + isr.lastMainRow : "Problem — " + (isr.ok() ? "the line is not ticked in the details table: " + isr.lastMainRow : isr.problems),
                landed ? "PASS" : "FAIL");
        if (!landed) { addSummary("Result", "FAILED — OK: " + isr.problems); return; }

        // 5) remark, Save
        String remark = isr.enterRemark("Item sale return - automation " + java.time.LocalTime.now().withNano(0));
        stepShot("Enter remark", "Enter the Remark", "The remark is entered",
                isr.ok() ? "Entered \"" + remark + "\"" : "Problem — " + isr.problems, isr.ok() ? "PASS" : "FAIL");
        if (!isr.ok()) { addSummary("Result", "FAILED — remark: " + isr.problems); return; }

        String toast = isr.clickSaveAndCapture();
        boolean ok = isr.saveSucceeded() && isr.savedOnServer();
        stepShot("Click Save", "Click Save (IUDSaleReturn)", "The item sale return is saved",
                isr.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"") + "; the server answered " + isr.lastSaveAnswer,
                toast.isEmpty() ? "FAIL" : "PASS");

        boolean reportOk = !isr.reportBlank;
        if (isr.reportPng != null && isr.reportPng.length > 0) {
            step(isr.reportPng, "Verify the report is generated in another tab", "Look for the sales return report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + isr.lastReport, reportOk ? "PASS" : "FAIL");
        } else {
            stepShot("Verify the report is generated in another tab", "Look for the sales return report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + isr.lastReport, reportOk ? "PASS" : "FAIL");
        }

        stepShot("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed the return (ResultStatus 1, " + isr.lastReturnNo + ")"
                   : (toast.isEmpty() ? "FAILS because no toast appeared — server: " + isr.lastSaveAnswer
                                      : "FAILS because the toast is not a success message: \"" + toast + "\" — server: " + isr.lastSaveAnswer),
                ok ? "PASS" : "FAIL");

        // 6) the new return on the list
        String row = "";
        if (ok && !isr.lastReturnNo.isEmpty()) {
            row = isr.findOnList(isr.lastReturnNo);
            stepShot("Verify the new item sale return is listed", "After Save the app returns to the list: Search today and look for the new return", "The new return is listed",
                    row.isEmpty() ? "FAILS because " + isr.lastReturnNo + " was not found on the list for today" : "PASSES because the list shows: " + row, row.isEmpty() ? "FAIL" : "PASS");
        }

        addSummary("Patient / store", chosen + " — " + isr.patientName() + " / " + isr.lastStore);
        addSummary("Sale returned", chosenSale + " — " + isr.lastItem);
        addSummary("Return no.", isr.lastReturnNo.isEmpty() ? "(none — not saved)" : isr.lastReturnNo + " (awaiting approval)");
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + isr.lastSaveAnswer));
    }
}
