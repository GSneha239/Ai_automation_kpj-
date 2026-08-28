package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Store — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Store</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Store</b>.</li>
 *   <li>Select the <b>Location</b> and the <b>Floor</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Remark</b>, <b>Store Address</b> and <b>Store Contact No</b>.</li>
 *   <li>Select the <b>PO Approval Level</b>.</li>
 *   <li>Enter the <b>COS Ledger</b>, <b>GRN Ledger</b>, <b>Drug Licence No</b>, <b>Tax No</b> and
 *       <b>Person Name</b>.</li>
 *   <li>Select the <b>HOD</b>.</li>
 *   <li>In <b>Transaction Details</b>, tick a transaction checkbox.</li>
 *   <li>Enter the <b>Low Profit</b>, <b>High Profit</b> and <b>Per Discount</b>, click <b>Add</b> and
 *       verify the row was added.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Every field is pinned to its exact ng-model, dumped from the live form. This screen makes keyword
 * matching untrustworthy: {@code code} sits beside {@code cosledger} and {@code grnledger},
 * {@code description} beside {@code storeaddress}, and three different controls are labelled
 * "Transfer".</p>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin values with
 * {@code -Dcode=}, {@code -Dlow=}, {@code -Dhigh=}, {@code -Ddiscount=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL store in the target environment.</p>
 */
public class Store extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Store() { super("ApplicationConfiguration_Store"); }

    public static void main(String[] args) {
        Store t = new Store();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Store", "Application Configuration > Inventory > Store",
                "&#9888; Creates a REAL store: Location, Floor, Code, Remark, Address, Contact, PO "
                        + "Approval Level, ledgers, licence and tax numbers, Person Name, HOD, a "
                        + "transaction tick, the profit/discount row, Add, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "ST" + stamp);
        // Each value carries its own field name, so a value landing in a neighbouring box is visible.
        String remark = System.getProperty("remark", "Auto store " + stamp);
        String address = System.getProperty("address", "12 Jalan Auto Store " + stamp);
        String contact = System.getProperty("contact", "03" + stamp + "1");
        String cosLedger = System.getProperty("cos", "COS-" + stamp);
        String grnLedger = System.getProperty("grn", "GRN-" + stamp);
        String dlNo = System.getProperty("dl", "DL-" + stamp);
        String taxNo = System.getProperty("tax", "TAX-" + stamp);
        String personName = System.getProperty("person", "Auto Person " + stamp);
        String low = System.getProperty("low", "10");
        String high = System.getProperty("high", "20");
        String discount = System.getProperty("discount", "5");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store st =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store(page);

        // 1) Navigate
        boolean rendered = st.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", st.lastMenu);
        step(page, "Open Store screen",
                "Click Application Configuration -> Inventory -> Store",
                "The Store screen is shown",
                rendered ? "Opened " + page.url()
                           + (st.lastRoute.isEmpty() ? "" : " (menu route " + st.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", st.describeControls());
        addSummary("Add", st.openFormIfNeeded());

        // 2) Location + Floor
        String location = st.selectLocation();
        String floor = st.selectFloor();
        boolean locFloorOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store.chosen(location)
                && com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store.chosen(floor);
        step(page, "Select location and floor", "Select the Location, then the Floor",
                "Both are selected",
                (locFloorOk
                    ? "PASSES because both dropdowns hold their choice: Location = " + location
                      + " | Floor = " + floor
                    : "FAILS because a selection did not take: Location = " + location
                      + " | Floor = " + floor),
                locFloorOk ? "PASS" : "FAIL");

        // 3) Code, Remark, Address, Contact No (entered together with the ledger block below, but
        //    reported as its own step so the requested order is visible in the report).
        String texts = st.enterTextFields(code, remark, address, contact,
                cosLedger, grnLedger, dlNo, taxNo, personName);
        String[] firstFour = { "Code", code, "Remark", remark,
                               "StoreAddress", address, "StoreContactNo", contact };
        boolean firstOk = st.textFieldsEntered(firstFour);
        step(page, "Enter code, remark, store address and store contact no",
                "Enter the Code " + code + ", the Remark, the Store Address and the Store Contact No",
                "All four are entered",
                (firstOk
                    ? "PASSES because each box read back its OWN value: " + texts
                    : "FAILS — these did not receive their value: " + st.textFieldsMissing(firstFour)
                      + ". Detail: " + texts),
                firstOk ? "PASS" : "FAIL");

        // 4) PO Approval Level
        String poLevel = st.selectPoApprovalLevel();
        boolean poOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store.chosen(poLevel);
        step(page, "Select PO approval level", "Select the PO Approval Level",
                "A PO approval level is selected",
                (poOk
                    ? "PASSES because the dropdown holds the choice: " + poLevel
                    : "FAILS because no PO approval level could be selected: " + poLevel),
                poOk ? "PASS" : "FAIL");

        // 5) COS Ledger, GRN Ledger, Drug Licence No, Tax No, Person Name
        String[] ledgerBlock = { "COSLedger", cosLedger, "GRNLedger", grnLedger,
                                 "DrugLicenceNo", dlNo, "TaxNo", taxNo, "PersonName", personName };
        boolean ledgerOk = st.textFieldsEntered(ledgerBlock);
        step(page, "Enter COS ledger, GRN ledger, drug licence no, tax no and person name",
                "Enter the COS Ledger, GRN Ledger, Drug Licence No, Tax No and Person Name",
                "All five are entered",
                (ledgerOk
                    ? "PASSES because each box read back its OWN value — checked individually, since "
                      + "the ledger boxes sit next to the Code and a keyword match confuses them: " + texts
                    : "FAILS — these did not receive their value: " + st.textFieldsMissing(ledgerBlock)
                      + ". Detail: " + texts),
                ledgerOk ? "PASS" : "FAIL");

        // 6) HOD
        String hod = st.selectHod();
        boolean hodOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store.chosen(hod);
        step(page, "Select HOD", "Select the HOD",
                "A HOD is selected",
                (hodOk
                    ? "PASSES because the dropdown holds the choice: " + hod
                    : "FAILS because no HOD could be selected: " + hod),
                hodOk ? "PASS" : "FAIL");

        // 7) Transaction Details -> tick
        String txn = st.tickTransactionDetail();
        boolean txnOk = st.transactionTicked();
        step(page, "In Transaction Details, tick the checkbox for a transaction",
                "Tick a transaction checkbox in Transaction Details",
                "A transaction is ticked",
                (txnOk
                    ? "PASSES because the box changed state when clicked, read back from the control: "
                      + txn
                    : "FAILS because the transaction checkbox did not tick: " + txn),
                txnOk ? "PASS" : "FAIL");

        // 8) Low profit / High profit / Per discount
        String profit = st.enterProfitFields(low, high, discount);
        boolean profitOk = st.profitEntered(low, high, discount);
        step(page, "Enter low profit, high profit and per discount",
                "Enter the Low Profit " + low + ", High Profit " + high + " and Per Discount " + discount,
                "All three are entered",
                (profitOk
                    ? "PASSES because each box read back its own value: " + profit
                    : "FAILS because a value did not land in its own field: " + profit),
                profitOk ? "PASS" : "FAIL");

        // 9) Add -> the row must really join the grid
        String addRow = st.clickAddRow();
        boolean rowOk = st.rowAdded();
        step(page, "Click Add and verify the record was added",
                "Click Add (AddIDDetails) and check the grid gained the row",
                "A row is added to the grid",
                (rowOk
                    ? "PASSES because the grid actually gained a row — counted before and after, so the "
                      + "click alone is not what passed: " + addRow
                    : "FAILS because no row joined the grid: " + addRow),
                rowOk ? "PASS" : "FAIL");

        // 10) Submit -> toast
        String toast = st.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Store.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = st.codeInList(code, remark);
        addSummary("List check", st.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + st.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the store IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + st.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Location / Floor", st.lastLocation + " / " + st.lastFloor);
        addSummary("Text fields", st.lastTextFields);
        addSummary("PO Approval Level", st.lastPoLevel);
        addSummary("HOD", st.lastHod);
        addSummary("Transaction tick", st.lastTxnTick);
        addSummary("Profit / discount", st.lastProfit);
        addSummary("Added row", st.lastAddRow);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
