package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named PharmacySale — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Pharmacy Sale/Dispense</b>.
 *
 * <ol>
 *   <li>Enter an <b>MRN</b> and click <b>Search</b>; a confirmation dialog appears ("Today's Visit of this Patient is Not Mark…") — click <b>Yes</b>,
 *       and <b>Yes</b> again if the dialog is still there (it is raised twice and the second one stays over the Mark Visit dialog).</li>
 *   <li>In the <b>Mark Visit</b> dialog select the <b>Department</b> and click <b>Mark Visit</b>.</li>
 *   <li>Item search (smart search — Panadol): pick one of the options, enter the <b>Quantity</b>, press Enter.</li>
 *   <li><b>Save</b>; click <b>Yes</b> in the confirmation; verify the report opens in another window and the success toast.</li>
 * </ol>
 *
 * <p><b>The MRN is not hardcoded</b> (regression use). The screen only raises the "visit not marked" dialog for an OPD patient with no visit today, and
 * marking the visit makes that patient ineligible for the rest of the day — so the MRNs come from the Patient Search popup beside the MRN box
 * ({@code -Dpharmacysale.maxtries}, default 30) and are entered and searched in turn until one shows that dialog. If none does (every patient tried
 * already has a visit today) the dialog steps are reported MANUAL and the sale goes ahead with the last patient that loaded. {@code -Dpharmacysale.mrn=} pins one.</p>
 *
 * <p><b>Two things the steps do not say, both needed on devhis:</b> the <b>Store</b> (mandatory and empty by default, and the item search returns nothing
 * without it — Pharmacy Main Store) and, only if blank, the <b>Payor</b> (auto-filled on the mark-visit path, not for a patient who already has a visit).</p>
 *
 * <p>Pin the department with {@code -Dpharmacysale.dept=}, the store with {@code -Dpharmacysale.store=}, the item search term with
 * {@code -Dpharmacysale.item=}. This test MARKS A REAL VISIT and SAVES A REAL SALE (stock goes down, a bill is generated). Run it headed (the default):
 * the report is a PDF window that headless Chromium cannot show.</p>
 */
public class PharmacySale extends DevHisBase {

    static final String DEPT = System.getProperty("pharmacysale.dept", "PHYSICIAN");
    static final String STORE = System.getProperty("pharmacysale.store", "Pharmacy Main Store");
    static final String ITEM = System.getProperty("pharmacysale.item", "Panadol");
    static final String QUANTITY = "2";
    static final int MAX_TRIES = Integer.getInteger("pharmacysale.maxtries", 30);
    static final String PINNED_MRN = System.getProperty("pharmacysale.mrn", "");

    public PharmacySale() { super("Inventory_POS_PharmacySale"); }

    public static void main(String[] args) {
        PharmacySale t = new PharmacySale();
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

    @Override
    protected void body() {
        meta("Inventory - POS - Pharmacy Sale/Dispense", "Inventory > POS > Pharmacy Sale/Dispense",
                "MRN + Search; Yes (and Yes again) in the visit-not-marked confirmation; Mark Visit dialog (department, Mark Visit); smart-search an item (Panadol), "
                        + "quantity + Enter; Save; Yes in the confirmation; verify the report in another window and the success toast. MARKS A REAL VISIT and SAVES A REAL SALE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.PharmacySale ps = new com.kpj.pages.Inventory_page.POS_page.PharmacySale(page);
        com.kpj.pages.Inventory_page.POS_page.PharmacySale.Outcome MARK = com.kpj.pages.Inventory_page.POS_page.PharmacySale.Outcome.MARK_VISIT_CONFIRM;
        com.kpj.pages.Inventory_page.POS_page.PharmacySale.Outcome LOADED = com.kpj.pages.Inventory_page.POS_page.PharmacySale.Outcome.LOADED;

        // 1) Inventory > POS > Pharmacy Sale/Dispense
        boolean on = ps.navigateViaMenu();
        stepShot("Open Pharmacy Sale/Dispense", "Click Inventory -> POS -> Pharmacy Sale/Dispense", "The Pharmacy Sale/Dispense screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Pharmacy Sale/Dispense not reached"); return; }

        // 2) Store (not in the written steps: mandatory, empty by default, and the Patient Search popup and the item search both need it)
        String store = ps.selectStore(STORE);
        stepShot("Select store", "Select the Store (" + STORE + ") — not in the written steps: it is mandatory, empty by default, and the patient search popup and the item search need it",
                "The store is selected", store.isEmpty() || !ps.ok() ? "Not selected — " + ps.problems : "Selected \"" + store + "\"", store.isEmpty() || !ps.ok() ? "FAIL" : "PASS");
        if (store.isEmpty() || !ps.ok()) { addSummary("Result", "FAILED — store: " + ps.problems); return; }

        // 3) MRN + Search — look for a patient whose search raises the "visit not marked" confirmation
        List<String> candidates = PINNED_MRN.isEmpty() ? ps.candidateMrns(MAX_TRIES, (MAX_TRIES + 9) / 10) : List.of(PINNED_MRN);
        String chosen = "", fallback = "";
        StringBuilder tried = new StringBuilder();
        int n = 0;
        for (String mrn : candidates) {
            n++;
            com.kpj.pages.Inventory_page.POS_page.PharmacySale.Outcome o = ps.searchMrn(mrn);
            System.out.println("PharmacySale: candidate " + n + "/" + candidates.size() + " " + mrn + " -> " + o);
            if (o == MARK) { chosen = mrn; break; }
            tried.append(tried.length() == 0 ? "" : ", ").append(mrn).append(" (").append(o == LOADED ? "already has a visit today" : o.toString().toLowerCase().replace('_', ' ')).append(")");
            if (o == LOADED && fallback.isEmpty()) fallback = mrn;
            if (o != LOADED) ps.dismissConfirms();
        }
        boolean dialogFlow = !chosen.isEmpty();
        if (!dialogFlow && !fallback.isEmpty()) ps.searchMrn(fallback);   // leave a patient loaded for the sale
        String mrnUsed = dialogFlow ? chosen : fallback;
        boolean found = !mrnUsed.isEmpty();
        String skipped = tried.length() == 0 ? "" : "; skipped before it: " + tried;
        stepShot("Enter MRN and click Search", "Enter an MRN (from the Patient Search popup — not hardcoded) and click Search",
                "The confirmation dialog appears",
                dialogFlow ? "MRN " + chosen + " raised \"" + ps.lastDialog + "\" — shown " + ps.dialogsShown + " time(s)" + skipped
                           : found ? "NO patient out of " + candidates.size() + " raised the confirmation — every one already has a visit today (" + tried + "); using " + fallback + ", who loaded without it"
                                   : "NO usable patient among " + candidates.size() + " candidate(s)" + (tried.length() == 0 ? "" : ": " + tried),
                dialogFlow ? "PASS" : found ? "MANUAL" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — no patient could be loaded"); return; }

        if (dialogFlow) {
            // 3) Yes — and Yes again while a confirmation is still there
            String yes1 = ps.clickYes();
            boolean open = ps.awaitMarkVisit(8000);
            stepShot("Click Yes — confirmation dialog", "Click Yes in the confirmation dialog", "The Mark Visit dialog opens",
                    yes1.isEmpty() ? "No confirmation with a Yes button was on screen"
                                   : "Clicked Yes on \"" + yes1 + "\"; " + (open ? "the Mark Visit dialog is open" : "the Mark Visit dialog did NOT open"),
                    !yes1.isEmpty() && open ? "PASS" : "FAIL");
            if (yes1.isEmpty() || !open) { addSummary("Result", "FAILED — Yes did not lead to the Mark Visit dialog"); return; }

            int again = 0;
            String againText = "";
            while (!ps.confirms().isEmpty() && again < 3) {
                if (again == 0) {
                    // the evidence is the screen as it is NOW: the second confirmation over the Mark Visit dialog
                    stepShot("Confirmation dialog is still there", "Look at the screen after the first Yes", "The confirmation dialog may still be showing",
                            "A second confirmation is still showing over the Mark Visit dialog: \"" + ps.confirms().get(ps.confirms().size() - 1) + "\"", "PASS");
                }
                againText = ps.clickYes();
                if (againText.isEmpty()) break;
                again++;
            }
            stepShot("Click Yes again — if the confirmation dialog is still there", "Click Yes again when the confirmation dialog is still showing",
                    "The confirmation is dismissed and the Mark Visit dialog stays open",
                    again == 0 ? "Not needed — the confirmation was gone after the first Yes" : "Clicked Yes " + again + " more time(s); " + (ps.confirms().isEmpty() ? "no confirmation is left" : "a confirmation is STILL showing") + "; Mark Visit dialog " + (ps.markVisitOpen() ? "open" : "closed"),
                    ps.confirms().isEmpty() && ps.markVisitOpen() ? "PASS" : "FAIL");
            if (!ps.confirms().isEmpty() || !ps.markVisitOpen()) { addSummary("Result", "FAILED — confirmations / Mark Visit dialog state"); return; }

            // 4) Mark Visit dialog: department, Mark Visit
            String dept = ps.selectDepartment(DEPT);
            stepShot("Mark Visit dialog — select Department", "Select the Department (" + DEPT + ")", "The department is selected",
                    dept.isEmpty() || !ps.ok() ? "Not selected — " + ps.problems : "Selected \"" + dept + "\"" + (dept.equalsIgnoreCase(DEPT) ? "" : " (\"" + DEPT + "\" is not offered, so the first one was taken)"),
                    dept.isEmpty() || !ps.ok() ? "FAIL" : "PASS");
            if (dept.isEmpty() || !ps.ok()) { addSummary("Result", "FAILED — department: " + ps.problems); return; }

            String err = ps.clickMarkVisit();
            stepShot("Click Mark Visit", "Click Mark Visit", "Today's visit is marked and the patient loads",
                    err.isEmpty() ? "Visit marked: " + ps.lastPatient + ", visit " + ps.lastVisit : "Problem — " + err, err.isEmpty() ? "PASS" : "FAIL");
            if (!err.isEmpty()) { addSummary("Result", "FAILED — Mark Visit: " + err); return; }
        } else {
            stepShot("Confirmation and Mark Visit dialogs", "Click Yes (and Yes again); in the Mark Visit dialog select the department and click Mark Visit",
                    "The dialogs appear and today's visit is marked",
                    "Not exercised: every candidate patient already had a visit today, so the screen raised no confirmation and no Mark Visit dialog (patient " + ps.patientName() + ", visit already marked)",
                    "MANUAL");
        }

        // 5) Payor (only if blank; not in the written steps)
        String payor = ps.ensurePayor();
        if (ps.payorWasBlank) {
            stepShot("Select payor", "Select the Payor — not in the written steps: it was left blank (it is only auto-filled on the mark-visit path)", "The payor is selected",
                    payor.isEmpty() || !ps.ok() ? "Not selected — " + ps.problems : "Selected \"" + payor + "\"", payor.isEmpty() || !ps.ok() ? "FAIL" : "PASS");
            if (payor.isEmpty() || !ps.ok()) { addSummary("Result", "FAILED — payor: " + ps.problems); return; }
        }

        // 6) item name (smart search), quantity, Enter
        String sugg = ps.searchAndPickItem(ITEM);
        stepShot("Enter item name and select one of the options", "Type \"" + ITEM + "\" in the item Name (smart search) and select one of the options",
                "The suggestions appear and the chosen item fills in the item code",
                sugg.isEmpty() ? "No item chosen — " + ps.problems
                        : "Chose \"" + sugg + "\" → item code " + ps.lastItemCode + ", name \"" + ps.lastItemName + "\"" + (ps.ok() ? "" : " | " + ps.problems),
                sugg.isEmpty() || !ps.ok() ? "FAIL" : "PASS");
        if (sugg.isEmpty() || !ps.ok()) { addSummary("Result", "FAILED — item search: " + ps.problems); return; }

        int rows = ps.enterQuantityAndEnter(QUANTITY);
        boolean held = ps.ok() && rows >= 1 && ps.rowHolds(QUANTITY);
        stepShot("Enter quantity and click Enter", "Enter the Quantity (" + QUANTITY + ") and press Enter", "The line is added to the Counter Sale Information grid",
                (held ? "Quantity " + ps.lastQty + " entered; Enter added the line — the grid has " + rows + " line(s): " : "Problem — " + (ps.ok() ? "the line does not carry the quantity: " : ps.problems + " | ")) + ps.lastRow,
                held ? "PASS" : "FAIL");
        if (!held) { addSummary("Result", "FAILED — quantity / add line: " + ps.problems); return; }

        // 7) Save -> Yes -> report window + toast
        String dlg = ps.clickSave();
        stepShot("Click Save", "Click Save (IUDSave)", "A confirmation dialog appears",
                dlg.isEmpty() ? "No confirmation appeared" + (ps.toastAfterSaveClick().isEmpty() ? "" : "; the screen said \"" + ps.toastAfterSaveClick() + "\"") : "Confirmation: \"" + dlg + "\"",
                dlg.isEmpty() ? "FAIL" : "PASS");
        if (dlg.isEmpty()) { addSummary("Result", "FAILED — Save raised no confirmation"); return; }

        String toast = ps.confirmSaveAndCapture();
        boolean ok = ps.saveSucceeded() && ps.savedOnServer();
        stepShot("Click Yes — confirmation", "Click Yes in the save confirmation", "The sale is saved",
                ps.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"") + "; the server answered " + ps.lastSaveAnswer,
                toast.isEmpty() ? "FAIL" : "PASS");

        boolean reportOk = !ps.reportBlank;
        if (ps.reportPng != null && ps.reportPng.length > 0) {
            step(ps.reportPng, "Verify the report is generated in another tab", "Look for the sale report in the new window", "A report opens in another window and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + ps.lastReport, reportOk ? "PASS" : "FAIL");
        } else {
            stepShot("Verify the report is generated in another tab", "Look for the sale report in the new window", "A report opens in another window and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + ps.lastReport, reportOk ? "PASS" : "FAIL");
        }

        stepShot("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed the sale (ResultStatus 1, " + ps.lastSaleNo + ")"
                   : (toast.isEmpty() ? "FAILS because no toast appeared — server: " + ps.lastSaveAnswer
                                      : "FAILS because the toast is not a success message: \"" + toast + "\" — server: " + ps.lastSaveAnswer),
                ok ? "PASS" : "FAIL");

        addSummary("Patient", ps.lastMrn + " — " + ps.lastPatient + (dialogFlow ? " (visit marked: " + ps.lastVisit + ", department " + ps.lastDepartment + ")" : " (already had a visit today)"));
        addSummary("Store / payor", ps.lastStore + " / " + ps.lastPayor);
        addSummary("Item", ps.lastRow);
        addSummary("Sale no.", ps.lastSaleNo.isEmpty() ? "(none — not saved)" : ps.lastSaleNo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + ps.lastSaveAnswer));
    }
}
