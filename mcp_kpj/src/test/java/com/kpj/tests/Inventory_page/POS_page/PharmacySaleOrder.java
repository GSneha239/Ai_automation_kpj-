package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named PharmacySaleOrder — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Pharmacy Sale Order/Consignment</b>.
 *
 * <ol>
 *   <li>Select the <b>Store</b> (Pharmacy Main Store).</li>
 *   <li>Enter the <b>MRN No.</b> (1100339490) and click <b>Search</b> — the patient loads.</li>
 *   <li>Enter the <b>Ward/Room/Bed</b> (a mandatory free-text box).</li>
 *   <li>Enter the item name (Panadol Optizorb Caplet 500mg), <b>wait for the suggestions to load</b> and <b>click the option</b> to select the item.</li>
 *   <li>Enter the <b>Quantity</b> and press Enter: the line is added; verify the row in <b>Counter Sale Information</b> carries the data added.</li>
 *   <li>Click <b>Save</b>, click <b>YES</b> in the confirmation dialog, and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES A REAL SALE ORDER for the patient on every run (stock goes down). Pin the store with {@code -Dpharmacysaleorder.store=}, the patient with
 * {@code -Dpharmacysaleorder.mrn=}, the ward text with {@code -Dpharmacysaleorder.ward=}, the item with {@code -Dpharmacysaleorder.item=} and the quantity with
 * {@code -Dpharmacysaleorder.qty=}.</p>
 */
public class PharmacySaleOrder extends DevHisBase {

    static final String STORE = System.getProperty("pharmacysaleorder.store", "Pharmacy Main Store");
    static final String MRN = System.getProperty("pharmacysaleorder.mrn", "1100339490");
    static final String WARD = System.getProperty("pharmacysaleorder.ward", "GENERAL WARD / ROOM 1 / BED 1");
    static final String ITEM = System.getProperty("pharmacysaleorder.item", "Panadol Optizorb Caplet 500mg");
    static final String QTY = System.getProperty("pharmacysaleorder.qty", "2");

    public PharmacySaleOrder() { super("Inventory_POS_PharmacySaleOrder"); }

    public static void main(String[] args) {
        PharmacySaleOrder t = new PharmacySaleOrder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - POS - Pharmacy Sale Order/Consignment", "Inventory > POS > Pharmacy Sale Order/Consignment",
                "Select the store; MRN " + MRN + " + Search; Ward/Room/Bed; item name (" + ITEM + ") — wait for the suggestions, click the option; quantity + Enter; verify the row in "
                        + "Counter Sale Information; Save; Yes in the confirmation; verify the success toast. SAVES A REAL SALE ORDER.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.PharmacySaleOrder ps = new com.kpj.pages.Inventory_page.POS_page.PharmacySaleOrder(page);

        // 1) Inventory > POS > Pharmacy Sale Order/Consignment
        boolean on = ps.navigateViaMenu();
        step("Open Pharmacy Sale Order/Consignment", "Click Inventory -> POS -> Pharmacy Sale Order/Consignment", "The Pharmacy Sale Order /Consignment Sale screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Pharmacy Sale Order/Consignment not reached"); return; }

        // 2) Store
        String store = ps.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && ps.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + ps.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + ps.problems); return; }

        // 3) MRN
        boolean mrnOk = ps.enterMrn(MRN);
        step("Enter MRN no", "Enter the MRN No. (" + MRN + ")", "The MRN is accepted", mrnOk ? "MRN " + ps.lastMrn + " entered" : "Problem — " + ps.problems, mrnOk ? "PASS" : "FAIL");
        if (!mrnOk) { addSummary("Result", "FAILED — MRN: " + ps.problems); return; }

        // 4) Search
        String patient = ps.clickSearch();
        boolean found = !patient.isEmpty() && ps.ok();
        step("Click Search", "Click Search", "The patient's details load (name, age, gender, payor)",
                found ? "Loaded " + patient + " — " + ps.lastAge + ", " + ps.lastGender + ", payor " + (ps.lastPayor.isEmpty() ? "(none)" : ps.lastPayor) : "No patient loaded — " + ps.problems,
                found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — Search: " + ps.problems); return; }

        // 5) Ward / Room / Bed
        boolean wardOk = ps.enterWard(WARD);
        step("Enter ward/room/bed", "Enter the Ward/Room/Bed (" + WARD + ")", "The ward/room/bed is accepted", wardOk ? "Ward/Room/Bed \"" + ps.lastWard + "\" entered" : "Problem — " + ps.problems,
                wardOk ? "PASS" : "FAIL");
        if (!wardOk) { addSummary("Result", "FAILED — ward/room/bed: " + ps.problems); return; }

        // 6) Item name
        boolean typed = ps.typeItemName(ITEM);
        step("Enter item name", "Type \"" + ITEM + "\" in the item Name box", "The item name is typed", typed ? "\"" + ITEM + "\" typed" : "Problem — " + ps.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — item name: " + ps.problems); return; }

        // 7) Wait for it to load
        List<String> sugg = ps.awaitSuggestions(ITEM, 15000);
        boolean loaded = !sugg.isEmpty();
        step("Wait for the item to load", "Wait for the suggestions for \"" + ITEM + "\" to load", "The suggestion list appears",
                loaded ? sugg.size() + " suggestion(s) loaded: \"" + sugg.get(0).replaceAll("\\s+", " ") + "\"" : "No suggestion appeared — " + ps.searchDiagnosis, loaded ? "PASS" : "FAIL");
        if (!loaded) { addSummary("Result", "FAILED — item suggestions: " + ps.searchDiagnosis); return; }

        // 8) Click the option
        String picked = ps.pickSuggestion(ITEM);
        boolean pickedOk = !picked.isEmpty() && ps.ok();
        step("Click the option to select the item name", "Click the suggestion for \"" + ITEM + "\"", "The item is selected: its code and name fill in and focus moves to Quantity",
                pickedOk ? "Chose it → code " + ps.lastCode + ", name \"" + ps.lastName + "\"; focus is on Quantity" : "Not selected — " + ps.problems, pickedOk ? "PASS" : "FAIL");
        if (!pickedOk) { addSummary("Result", "FAILED — selecting the item: " + ps.problems); return; }

        // 9) Quantity
        boolean qtyOk = ps.enterQuantity(QTY);
        step("Enter quantity", "Enter the Quantity (" + QTY + ")", "The quantity is accepted", qtyOk ? "Quantity " + ps.lastQty + " entered" : "Problem — " + ps.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — quantity: " + ps.problems); return; }

        // 10) Enter -> the line is added
        int before = ps.gridLines();
        int after = ps.pressEnterToAdd(before);
        boolean added = ps.ok() && after > before;
        step("Click Enter", "Press Enter in the Quantity box", "The line is added to Counter Sale Information",
                added ? "Counter Sale Information went from " + before + " to " + after + " line(s)" : "Problem — " + ps.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add line: " + ps.problems); return; }

        // 11) Verify the row
        boolean rowOk = ps.verifyLine(before, ITEM, QTY);
        ps.showGrid();
        step("Verify the row in Counter Sale Information", "Look at the Counter Sale Information grid",
                "A line with the item name, the quantity entered, a selling price, and consistent total and net amounts is listed",
                rowOk ? "PASSES because the grid shows: " + ps.lastRow : "FAILS because " + String.join("; ", ps.problems) + (ps.lastRow.isEmpty() ? "" : " — row: " + ps.lastRow),
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row not as added: " + ps.problems); return; }

        // 12) Save -> confirmation
        String dlg = ps.clickSave();
        step("Click Save", "Click Save", "A confirmation dialog appears", dlg.isEmpty() ? "No confirmation appeared — " + ps.problems : "Confirmation: \"" + dlg + "\"", dlg.isEmpty() ? "FAIL" : "PASS");
        if (dlg.isEmpty()) { addSummary("Result", "FAILED — Save raised no confirmation: " + ps.problems); return; }

        // 13) Yes
        String toast = ps.confirmYes();
        boolean yesOk = ps.ok();
        step("Click Yes — confirmation dialog", "Click YES in the save confirmation", "The sale order is saved",
                yesOk ? "Clicked YES; the server answered ResultStatus " + ps.lastSaveStatus + (ps.lastSaveMessage.isEmpty() ? "" : " (\"" + ps.lastSaveMessage + "\")")
                        + (ps.lastSaveId.isEmpty() ? "" : ", sale id " + ps.lastSaveId) : "Problem — " + ps.problems, yesOk ? "PASS" : "FAIL");

        // 14) Success toast
        boolean ok = yesOk && ps.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed the sale (ResultStatus 1" + (ps.lastSaveId.isEmpty() ? "" : ", sale id " + ps.lastSaveId) + ")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — server: ResultStatus " + ps.lastSaveStatus + " " + ps.lastSaveMessage
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — server: ResultStatus " + ps.lastSaveStatus + " " + ps.lastSaveMessage,
                ok ? "PASS" : "FAIL");

        addSummary("Store / patient", ps.lastStore + " / " + ps.lastMrn + " — " + ps.lastPatient + " (" + ps.lastAge + ", " + ps.lastGender + ")");
        addSummary("Ward/Room/Bed", ps.lastWard);
        addSummary("Item", ps.lastCode + " — " + ps.lastName + " x " + QTY);
        addSummary("Counter Sale Information", ps.lastRow);
        addSummary("Sale id", ps.lastSaveId.isEmpty() ? "(none — not saved)" : ps.lastSaveId);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — ResultStatus " + ps.lastSaveStatus));
    }
}
