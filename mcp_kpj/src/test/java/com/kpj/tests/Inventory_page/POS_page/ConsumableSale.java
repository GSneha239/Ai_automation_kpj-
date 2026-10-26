package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ConsumableSale — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Consumable Sale</b>.
 *
 * <ol>
 *   <li>Enter the <b>MRN No.</b> (1100339415) and click <b>Search</b>; a confirmation dialog appears ("Today's Visit of this Patient is Not Mark…") — click <b>Yes</b>.</li>
 *   <li>In the <b>Mark Visit</b> dialog click <b>Mark Visit</b>; the patient loads.</li>
 *   <li>Select the <b>Store</b> (Pharmacy Main Store); enter the <b>Last Name</b> if it is empty.</li>
 *   <li>Enter the item <b>Code</b> (PH10000000); enter the item <b>Name</b> (a smart select — wait for the suggestions, click the option); enter the <b>Quantity</b>; press Enter.</li>
 *   <li>Verify the row in <b>Counter Sale Information</b>; <b>Save</b>; click <b>Yes</b> in the confirmation; verify the success toast and that the report opens in another tab.</li>
 * </ol>
 *
 * <p><b>The MRN is a starting point, not a promise.</b> The screen only raises the "visit not marked" dialog for an OPD patient with no visit today, and marking the visit makes that
 * patient ineligible for the rest of the day — so 1100339415 (already marked) and any patient used by an earlier run load WITHOUT the dialog. By default the test then looks for another
 * patient who still raises it, from the Patient Search popup ({@code -Dconsumablesale.maxtries}, default 30), and says so in the report. {@code -Dconsumablesale.exactmrn=true} keeps
 * the given patient whatever happens: the dialog steps are then reported MANUAL and the sale goes ahead.</p>
 *
 * <p>This test MARKS A REAL VISIT (when the dialog appears) and SAVES A REAL SALE (stock goes down, a bill is generated). Run it headed (the default): the report is a PDF tab that
 * headless Chromium cannot show. Pin the store with {@code -Dconsumablesale.store=}, the patient with {@code -Dconsumablesale.mrn=}, the last name with
 * {@code -Dconsumablesale.lastname=}, the item with {@code -Dconsumablesale.code=} / {@code .item=} and the quantity with {@code -Dconsumablesale.qty=}.</p>
 */
public class ConsumableSale extends DevHisBase {

    static final String STORE = System.getProperty("consumablesale.store", "Pharmacy Main Store");
    static final String MRN = System.getProperty("consumablesale.mrn", "1100339415");
    static final boolean EXACT_MRN = Boolean.getBoolean("consumablesale.exactmrn");
    static final int MAX_TRIES = Integer.getInteger("consumablesale.maxtries", 30);
    static final String LAST_NAME = System.getProperty("consumablesale.lastname", "Test");
    static final String CODE = System.getProperty("consumablesale.code", "PH10000000");
    static final String ITEM = System.getProperty("consumablesale.item", "Panadol Optizorb Caplet 500mg");
    static final String QTY = System.getProperty("consumablesale.qty", "2");

    public ConsumableSale() { super("Inventory_POS_ConsumableSale"); }

    public static void main(String[] args) {
        ConsumableSale t = new ConsumableSale();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - POS - Consumable Sale", "Inventory > POS > Consumable Sale",
                "MRN " + MRN + " + Search; Yes in the visit-not-marked confirmation; Mark Visit; store; last name if empty; item code " + CODE + "; item name (" + ITEM
                        + ") — wait for the suggestions, click the option; quantity + Enter; verify the row in Counter Sale Information; Save; Yes in the confirmation; verify the success toast and "
                        + "the report in another tab. MARKS A REAL VISIT and SAVES A REAL SALE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.ConsumableSale cs = new com.kpj.pages.Inventory_page.POS_page.ConsumableSale(page);
        com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome MARK = com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome.MARK_VISIT_CONFIRM;
        com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome LOADED = com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome.LOADED;

        // 1) Inventory > POS > Consumable Sale
        boolean on = cs.navigateViaMenu();
        step("Open Consumable Sale", "Click Inventory -> POS -> Consumable Sale", "The Consumable Sale screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Consumable Sale not reached"); return; }

        // 2) MRN + Search — the given patient first; if it raises no dialog, look for one that does
        com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome o = cs.searchMrn(MRN);
        System.out.println("ConsumableSale: given MRN " + MRN + " -> " + o);
        String chosen = o == MARK ? MRN : "", skipped = "";
        boolean usedOther = false;
        if (o != MARK && !EXACT_MRN) {
            skipped = MRN + " (" + (o == LOADED ? "already has a visit today" : o.toString().toLowerCase().replace('_', ' ')) + ")";
            if (o != LOADED) cs.dismissConfirms();
            String s = cs.selectStore(STORE);                      // the Patient Search popup needs a Store
            if (s.isEmpty()) System.out.println("ConsumableSale: no store, the Patient Search popup may not open - " + cs.problems);
            List<String> candidates = cs.candidateMrns(MAX_TRIES, (MAX_TRIES + 9) / 10);
            int n = 0;
            for (String c : candidates) {
                if (c.equals(MRN)) continue;
                n++;
                com.kpj.pages.Inventory_page.POS_page.ConsumableSale.Outcome co = cs.searchMrn(c);
                System.out.println("ConsumableSale: candidate " + n + "/" + candidates.size() + " " + c + " -> " + co);
                if (co == MARK) { chosen = c; usedOther = true; break; }
                skipped += ", " + c + " (" + (co == LOADED ? "already has a visit today" : co.toString().toLowerCase().replace('_', ' ')) + ")";
                if (co != LOADED) cs.dismissConfirms();
            }
            if (!usedOther) { o = cs.searchMrn(MRN); }                 // leave the given patient loaded
        }
        boolean dialogFlow = !chosen.isEmpty();
        String mrnUsed = dialogFlow ? chosen : cs.lastMrn;
        boolean found = dialogFlow || (!cs.lastPatient.isEmpty() && o == LOADED);
        String skippedNote = skipped.isEmpty() ? "" : "; skipped before it: " + skipped;
        step("Enter MRN no and click Search", "Enter the MRN No. (" + MRN + ") and click Search", "The confirmation dialog to mark today's visit appears",
                dialogFlow ? (usedOther ? "The given MRN " + MRN + " already has a visit today (no dialog), so MRN " + chosen + " was used: " : "MRN " + chosen + ": ")
                        + "the screen raised \"" + cs.lastDialog + "\" — shown " + cs.dialogsShown + " time(s)" + (usedOther ? skippedNote : "")
                        : found ? "NO patient raised the confirmation — " + (EXACT_MRN ? "the given MRN " + MRN + " already has a visit today (-Dconsumablesale.exactmrn=true)" : skipped)
                                + "; using " + MRN + ", " + cs.lastPatient + ", who loaded without it"
                                : "No usable patient — " + (cs.problems.isEmpty() ? skipped : cs.problems),
                dialogFlow ? "PASS" : found ? "MANUAL" : "FAIL");
        if (!found && !dialogFlow) { addSummary("Result", "FAILED — no patient could be loaded"); return; }

        if (dialogFlow) {
            // 3) Yes -> Mark Visit dialog
            String yes1 = cs.clickYes();
            boolean open = cs.awaitMarkVisit(8000);
            for (int i = 0; i < 3 && !open && !cs.confirms().isEmpty(); i++) { cs.clickYes(); open = cs.awaitMarkVisit(5000); }     // a stacked second confirmation
            step("Click Yes — confirmation dialog to mark today's visit", "Click Yes in the confirmation dialog", "The Mark Visit dialog opens",
                    yes1.isEmpty() ? "No confirmation with a Yes button was on screen"
                                   : "Clicked Yes on \"" + yes1 + "\"; " + (open ? "the Mark Visit dialog is open (" + cs.markVisitSummary() + ")" : "the Mark Visit dialog did NOT open"),
                    !yes1.isEmpty() && open ? "PASS" : "FAIL");
            if (yes1.isEmpty() || !open) { addSummary("Result", "FAILED — Yes did not lead to the Mark Visit dialog"); return; }

            // 4) Mark Visit
            String err = cs.clickMarkVisit();
            step("Click Mark Visit", "Click Mark Visit in the Mark Visit dialog", "Today's visit is marked and the patient loads",
                    err.isEmpty() ? "Visit marked: " + cs.lastPatient + " (" + cs.lastAge + ", " + cs.lastGender + "), payor " + (cs.lastPayor.isEmpty() ? "(none)" : cs.lastPayor)
                                  : "Problem — " + err, err.isEmpty() ? "PASS" : "FAIL");
            if (!err.isEmpty()) { addSummary("Result", "FAILED — Mark Visit: " + err); return; }
        } else {
            step("Confirmation and Mark Visit dialogs", "Click Yes in the confirmation; in the Mark Visit dialog click Mark Visit", "The dialogs appear and today's visit is marked",
                    "Not exercised: the patient already had a visit today, so the screen raised no confirmation and no Mark Visit dialog (patient " + cs.lastPatient + ")", "MANUAL");
        }

        // 5) Store
        boolean already = cs.storeSelected(STORE);
        String store = already ? cs.lastStore.isEmpty() ? STORE : cs.lastStore : cs.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && cs.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected",
                storeOk ? (already ? "Already selected: \"" + store + "\" (it was needed earlier to find a patient)" : "Selected \"" + store + "\"")
                        + (cs.storeToast.isEmpty() ? "" : " — the screen answered with a toast \"" + cs.storeToast + "\"") : "Not selected — " + cs.problems,
                storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + cs.problems); return; }

        // 6) Last name if empty
        String last = cs.enterLastNameIfEmpty(LAST_NAME);
        boolean lastOk = !last.isEmpty() && cs.ok();
        step("Enter last name if empty", "Enter the Last Name (" + LAST_NAME + ") if the box is empty", "The last name is filled",
                lastOk ? (cs.lastNameWasEmpty ? "The box was empty; entered \"" + last + "\"" : "Already filled (\"" + last + "\") — nothing to enter") : "Problem — " + cs.problems,
                lastOk ? "PASS" : "FAIL");
        if (!lastOk) { addSummary("Result", "FAILED — last name: " + cs.problems); return; }

        // 7) Item code
        boolean codeOk = cs.enterCode(CODE);
        step("Enter code", "Enter the item Code (" + CODE + ")", "The code is accepted", codeOk ? "Code " + cs.lastCode + " entered" : "Problem — " + cs.problems, codeOk ? "PASS" : "FAIL");
        if (!codeOk) { addSummary("Result", "FAILED — code: " + cs.problems); return; }

        // 8) Item name (smart select)
        boolean typed = cs.typeItemName(ITEM);
        step("Enter name", "Type \"" + ITEM + "\" in the item Name box (a smart select)", "The item name is typed", typed ? "\"" + ITEM + "\" typed" : "Problem — " + cs.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — item name: " + cs.problems); return; }

        // 9) Wait for the suggestions
        List<String> sugg = cs.awaitSuggestions(ITEM, 15000);
        boolean loaded = !sugg.isEmpty();
        step("Wait for the item to load", "Wait for the suggestions for \"" + ITEM + "\" to load", "The suggestion list appears",
                loaded ? sugg.size() + " suggestion(s) loaded: \"" + sugg.get(0).replaceAll("\\s+", " ") + "\"" : "No suggestion appeared — " + cs.searchDiagnosis, loaded ? "PASS" : "FAIL");
        if (!loaded) { addSummary("Result", "FAILED — item suggestions: " + cs.searchDiagnosis); return; }

        // 10) Click the option
        String picked = cs.pickSuggestion(ITEM);
        boolean pickedOk = !picked.isEmpty() && cs.ok();
        step("Click the option to select the item name", "Click the suggestion for \"" + ITEM + "\"", "The item is selected: its name fills in and focus moves to Quantity",
                pickedOk ? "Chose it → code " + cs.lastCode + ", name \"" + cs.lastName + "\"; focus is on Quantity" : "Not selected — " + cs.problems, pickedOk ? "PASS" : "FAIL");
        if (!pickedOk) { addSummary("Result", "FAILED — selecting the item: " + cs.problems); return; }

        // 11) Quantity (loading is done: the item is selected and focus is on Quantity)
        boolean qtyOk = cs.enterQuantity(QTY);
        step("Enter quantity", "Enter the Quantity (" + QTY + ") once the item has loaded", "The quantity is accepted", qtyOk ? "Quantity " + cs.lastQty + " entered" : "Problem — " + cs.problems,
                qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — quantity: " + cs.problems); return; }

        // 12) Enter -> the line is added
        int before = cs.gridLines();
        int after = cs.pressEnterToAdd(before);
        boolean added = cs.ok() && after > before;
        step("Click Enter", "Press Enter in the Quantity box", "The line is added to Counter Sale Information",
                added ? "Counter Sale Information went from " + before + " to " + after + " line(s)" : "Problem — " + cs.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add line: " + cs.problems); return; }

        // 13) Verify the row
        boolean rowOk = cs.verifyLine(before, ITEM, QTY);
        cs.showGrid();
        step("Verify the row added in Counter Sale Information", "Look at the Counter Sale Information grid",
                "A line with the item name, a batch, the quantity entered, a selling price and an amount is listed",
                rowOk ? "PASSES because the grid shows: " + cs.lastRow : "FAILS because " + String.join("; ", cs.problems) + (cs.lastRow.isEmpty() ? "" : " — row: " + cs.lastRow), rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row not as added: " + cs.problems); return; }

        // 14) Save -> confirmation
        String dlg = cs.clickSave();
        step("Click Save", "Click Save", "A confirmation dialog appears", dlg.isEmpty() ? "No confirmation appeared — " + cs.problems : "Confirmation: \"" + dlg + "\"", dlg.isEmpty() ? "FAIL" : "PASS");
        if (dlg.isEmpty()) { addSummary("Result", "FAILED — Save raised no confirmation: " + cs.problems); return; }

        // 15) Yes -> toast + report tab
        String toast = cs.confirmSaveAndCapture();
        boolean answered = !cs.lastSaveAnswer.startsWith("no /api");
        step("Click Yes — confirmation dialog", "Click Yes in the save confirmation", "The sale is saved",
                "Clicked Yes in the confirmation; the server answered " + cs.lastSaveAnswer + (toast.isEmpty() ? "; no toast appeared" : "; the screen said \"" + toast + "\""), answered ? "PASS" : "FAIL");

        // 16) success toast
        boolean ok = cs.saveSucceeded() && cs.savedOnServer();
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed the sale (ResultStatus 1, " + cs.lastSaleNo + ")"
                   : (toast.isEmpty() ? "FAILS because no toast appeared — server: " + cs.lastSaveAnswer
                                      : "FAILS because the toast is not a success message: \"" + toast + "\" — server: " + cs.lastSaveAnswer),
                ok ? "PASS" : "FAIL");

        // 17) report in another tab
        boolean reportOk = !cs.reportBlank;
        if (cs.reportPng != null && cs.reportPng.length > 0) {
            step(cs.reportPng, "Verify the report is generated in another tab", "Look for the sale report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + cs.lastReport, reportOk ? "PASS" : "FAIL");
        } else {
            step("Verify the report is generated in another tab", "Look for the sale report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + cs.lastReport, reportOk ? "PASS" : "FAIL");
        }

        addSummary("Patient", mrnUsed + " — " + cs.lastPatient + " (" + cs.lastAge + ", " + cs.lastGender + ")"
                + (dialogFlow ? (usedOther ? " — visit marked; NOT the given MRN " + MRN + " (it already had a visit today)" : " — visit marked") : " — already had a visit today"));
        addSummary("Store / last name", cs.lastStore + " / " + cs.lastLastName);
        addSummary("Item", CODE + " — " + cs.lastName + " x " + QTY);
        addSummary("Counter Sale Information", cs.lastRow);
        addSummary("Sale no.", cs.lastSaleNo.isEmpty() ? "(none — not saved)" : cs.lastSaleNo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + cs.lastSaveAnswer));
    }
}
