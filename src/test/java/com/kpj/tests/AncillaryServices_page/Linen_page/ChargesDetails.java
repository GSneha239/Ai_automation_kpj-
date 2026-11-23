package com.kpj.tests.AncillaryServices_page.Linen_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// The page object is also named ChargesDetails — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Linen &gt; Linen Item Master &gt; <b>Charges Details</b> tab.
 *
 * <ol>
 *   <li><b>T1 Screen Navigation</b> — menu to Linen Item Master, open an item (existing one if any, else Add), open
 *       the Charges Details tab.</li>
 *   <li><b>T2 Category Selection</b> — Category field shown, options listed, a valid category selected and verified.</li>
 *   <li><b>T3 Charges Added</b> — enter a charge, click Add, the row appears in the list.</li>
 *   <li><b>T4 List Charges Details</b> — columns View / Category / Charges, rows and their values.</li>
 *   <li><b>T5 Validation</b> — empty Category, empty Charges, duplicate Category, non-numeric Charges.</li>
 *   <li><b>T6 Data Consistency</b> — selected category / entered charge = table row = controller list; View loads the
 *       row back into the fields.</li>
 * </ol>
 *
 * <p><b>Read-only by design.</b> Add on this tab only changes an in-memory list; <b>Save</b> would INSERT a Linen
 * Item immediately (no confirmation dialog), so neither Save nor Modify is ever clicked. The test also watches the
 * network and fails the safety step if any save/insert/update/delete request is sent.</p>
 *
 * <p>Form state: after every validation click the test checks that the form is still on the Charges Details tab
 * (re-opening it if not) and that the Charges box is visible, enabled and editable before typing; a field that is
 * not available makes the dependent step SKIPPED with the reason, never a 30s timeout.</p>
 *
 * <p>Data-dependent: the Category dropdown lists Linen Categories (Linen Category Master). When the environment has
 * none, T2/T3/T6 and the category-dependent T5 checks are reported as SKIPPED (BLOCKED) rather than failed.</p>
 *
 * <p>Optional flags: {@code -Dcharges.category=<exact option text>} (default: first option),
 * {@code -Dcharges.amount=<value>} (default 25.00).</p>
 */
public class ChargesDetails extends DevHisBase {

    public ChargesDetails() { super("LinenItemMaster_ChargesDetails"); }

    public static void main(String[] args) {
        ChargesDetails t = new ChargesDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final String MSG_NO_CATEGORY = "Please Select Category Name";
    private static final String MSG_NO_CHARGES  = "Please Select Charges";
    private static final String MSG_DUPLICATE   = "Please Select Different Category";

    private int pass = 0, fail = 0, skipped = 0, manual = 0;
    private final List<String> defects = new ArrayList<>();

    @Override
    protected void body() {
        meta("Ancillary Services - Linen Item Master - Charges Details",
                "Ancillary Services > Linen > Linen Item Master > Charges Details",
                "Category selection, Charges Added and List Charges Details on the Charges Details tab. Read-only: Save is never clicked.");

        // Safety net: record any request that would persist data (the tab's Save posts an INSERT).
        final List<String> writeRequests = Collections.synchronizedList(new ArrayList<>());
        page.onRequest(req -> {
            String u = req.url();
            if ("POST".equalsIgnoreCase(req.method()) && u.contains("/api/")
                    && u.matches("(?i).*(IUD|Insert|Save|Update|Delete).*")) writeRequests.add(u);
        });

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in (" + page.url() + ")", "PASS");

        com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails cd =
                new com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails(page);

        // ================= T1 - Screen Navigation =================
        cd.navigateViaMenu();
        boolean onList = cd.onListScreen();
        Map<String, Object> grid = cd.listGridInfo();
        record("T1.1 Open Linen Item Master",
                "Click Ancillary Services -> Linen -> Linen Item Master",
                "Linen Item Master list (" + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.LIST_ROUTE + ") with an Add button",
                onList ? "Opened '" + cd.screenTitle() + "' at " + page.url() + "; grid columns " + grid.get("columns") + ", " + grid.get("rows") + " item(s)"
                        : "Did NOT reach the list (" + page.url() + ")",
                onList ? "PASS" : "FAIL");
        if (!onList) { finish(writeRequests); return; }

        String item = cd.openFirstExistingItem();
        String mode;
        boolean formOpen;
        if (!item.isEmpty()) {
            mode = "existing item '" + item + "' (Edit)";
            formOpen = true;
        } else {
            mode = "new item form (Add) - the list has no Linen Items";
            formOpen = cd.clickAddNewItem();
        }
        boolean tab = formOpen && cd.openChargesDetailsTab();
        String heading = tab ? cd.panelHeading() : "";
        boolean t1ok = tab && cd.isChargesTabActive() && "Charges Details".equalsIgnoreCase(heading);
        record("T1.2 Open Charges Details tab",
                "Open the " + mode + ", then click the 'Charges Details' tab",
                "Charges Details tab active with the 'Charges Details' panel",
                t1ok ? "Tab active, panel heading '" + heading + "', URL " + page.url() + ", buttons " + cd.tabButtons()
                        : "Tab not shown (form open=" + formOpen + ", tab=" + tab + ", heading='" + heading + "')",
                t1ok ? "PASS" : "FAIL");
        addSummary("Form opened", mode);
        if (!t1ok) { finish(writeRequests); return; }

        interceptAlerts();

        // ================= T4 (part 1) - list as loaded =================
        List<String> headers = cd.chargesTableHeaders();
        boolean colsOk = headers.equals(List.of("View", "Category", "Charges"));
        List<String[]> initialRows = cd.chargesRows();
        record("T4.1 Charges list columns & records",
                "Read the Charges Details table header and its records",
                "Columns: View | Category | Charges; records listed (an existing item shows its saved charges)",
                "Columns " + headers + "; " + initialRows.size() + " record(s): "
                        + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.rowsText(initialRows),
                colsOk ? "PASS" : "FAIL");

        // ================= T2 - Category Selection =================
        boolean catVisible = cd.categoryFieldVisible();
        boolean chgVisible = cd.chargesFieldVisible();
        List<String> options = cd.categoryOptions(10000);
        record("T2.1 Category field & options",
                "Check the Category dropdown is displayed and list its options",
                "Category (and Charges) fields displayed; Category lists the Linen Categories",
                "Category visible=" + catVisible + ", Charges visible=" + chgVisible + "; " + options.size() + " option(s): "
                        + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.join(options),
                !(catVisible && chgVisible) ? "FAIL" : options.isEmpty() ? "SKIPPED" : "PASS");

        // ================= T5a - validation that needs no data =================
        cd.clearCategory();
        int before = cd.chargesRows().size();
        String r1 = typeCharges(cd, "");
        if (!r1.isEmpty()) {
            record("T5.1 Add with empty Category and Charges", "Leave Category '-Select-' and Charges empty, click Add",
                    "Warning '" + MSG_NO_CATEGORY + "'; no row added", "SKIPPED: Charges field unavailable - " + r1, "SKIPPED");
        } else {
            clearAlerts();
            boolean clicked = cd.clickAddCharge();
            String a1 = waitAlert(MSG_NO_CATEGORY);
            boolean v1 = clicked && a1.contains(MSG_NO_CATEGORY) && cd.chargesRows().size() == before;
            record("T5.1 Add with empty Category and Charges",
                    "Leave Category '-Select-' and Charges empty, click Add",
                    "Warning '" + MSG_NO_CATEGORY + "'; no row added",
                    (clicked ? "" : "Add button not on screen; ") + "Alert: '" + a1 + "'; rows " + before + " -> " + cd.chargesRows().size(),
                    v1 ? "PASS" : "FAIL");
            checkFormState(cd, "T5.1");
        }

        String r2 = typeCharges(cd, "25.00");
        if (!r2.isEmpty()) {
            record("T5.2 Add with Charges but no Category", "Enter Charges 25.00, leave Category '-Select-', click Add",
                    "Warning '" + MSG_NO_CATEGORY + "'; no row added", "SKIPPED: Charges field unavailable - " + r2, "SKIPPED");
        } else {
            clearAlerts();
            boolean clicked = cd.clickAddCharge();
            String a2 = waitAlert(MSG_NO_CATEGORY);
            boolean v2 = clicked && a2.contains(MSG_NO_CATEGORY) && cd.chargesRows().size() == before;
            record("T5.2 Add with Charges but no Category",
                    "Enter Charges 25.00, leave Category '-Select-', click Add",
                    "Warning '" + MSG_NO_CATEGORY + "'; no row added",
                    (clicked ? "" : "Add button not on screen; ") + "Alert: '" + a2 + "'; rows " + before + " -> " + cd.chargesRows().size(),
                    v2 ? "PASS" : "FAIL");
            checkFormState(cd, "T5.2");
        }

        if (options.isEmpty()) {
            String why = "BLOCKED: the Category dropdown has no Linen Categories on this environment (Linen Category Master is empty), "
                    + "so a category cannot be selected. Needs one Linen Category configured - not created by this read-only test.";
            record("T2.2 Select a valid Category", "Select a Category", "Category selected and shown", why, "SKIPPED");
            record("T3 Charges Added", "Select Category, enter Charges, click Add", "Row added to the Charges Details list", why, "SKIPPED");
            record("T5.3-T5.5 Category-dependent validation", "Empty Charges / duplicate Category / non-numeric Charges",
                    "Warnings '" + MSG_NO_CHARGES + "' / '" + MSG_DUPLICATE + "'", why, "SKIPPED");
            record("T6 Data consistency", "Compare selected category/charge with the list", "Consistent", why, "SKIPPED");
            addSummary("Category options", "0 - BLOCKED (no Linen Categories)");
            leave(cd, writeRequests);
            return;
        }

        // ================= T2.2 - select a category =================
        String wanted = System.getProperty("charges.category", "").trim();
        List<String> usedCats = new ArrayList<>();
        for (String[] r : initialRows) usedCats.add(r[0]);
        String category = null;
        if (!wanted.isEmpty() && options.contains(wanted)) category = wanted;
        else {
            if (!wanted.isEmpty()) com.kpj.core.Reasons.add("-Dcharges.category='" + wanted + "' is not an option - picked another");
            for (String o : options) if (!usedCats.contains(o)) { category = o; break; }   // not already in the list
        }
        if (category == null) {
            record("T2.2 Select a valid Category", "Select a Category not yet in the list", "Category selected",
                    "BLOCKED: every category (" + options.size() + ") is already in this item's charges list", "SKIPPED");
            leave(cd, writeRequests);
            return;
        }
        String shown = cd.selectCategory(category);
        String model = cd.selectedCategoryModel();
        boolean t2ok = category.equals(shown) && !model.isEmpty();
        record("T2.2 Select a valid Category", "Select Category '" + category + "'",
                "select2 shows '" + category + "' and CategoryList.linencategoryid is set",
                "Shown '" + shown + "', model id '" + model + "'", t2ok ? "PASS" : "FAIL");

        // ================= T5.3 - category but empty charges =================
        if (!typeOrSkip(cd, "", "T5.3 Add with Category but empty Charges")) { leave(cd, writeRequests); return; }
        clearAlerts();
        cd.clickAddCharge();
        String a3 = waitAlert(MSG_NO_CHARGES);
        int rowsNow = cd.chargesRows().size();
        record("T5.3 Add with Category but empty Charges",
                "Category '" + category + "', Charges empty, click Add",
                "Warning '" + MSG_NO_CHARGES + "'; no row added",
                "Alert: '" + a3 + "'; rows " + before + " -> " + rowsNow,
                a3.contains(MSG_NO_CHARGES) && rowsNow == before ? "PASS" : "FAIL");

        // ================= T3 - Charges Added =================
        String amount = System.getProperty("charges.amount", "25.00");
        cd.selectCategory(category);
        if (!typeOrSkip(cd, amount, "T3 Charges Added")) { leave(cd, writeRequests); return; }
        String enteredCharge = cd.chargesValue();
        clearAlerts();
        cd.clickAddCharge();
        boolean grew = cd.waitForRowCount(before + 1, 5000);
        List<String[]> rows = cd.chargesRows();
        String[] added = rows.isEmpty() ? new String[]{"", ""} : rows.get(rows.size() - 1);
        boolean t3ok = grew && category.equals(added[0]) && amount.equals(added[1]);
        if (t3ok && !cd.chargesValue().isEmpty())
            defects.add("Observation: after Add the Category resets to -Select- but the Charges box keeps '" + cd.chargesValue() + "'");
        record("T3 Charges Added", "Category '" + category + "', Charges '" + amount + "', click Add",
                "New row [" + category + ", " + amount + "] in the list",
                "Rows " + before + " -> " + rows.size() + "; last row " + java.util.Arrays.toString(added)
                        + "; alerts " + capturedAlerts() + "; Category now '" + cd.selectedCategoryText() + "', Charges now '" + cd.chargesValue() + "'",
                t3ok ? "PASS" : "FAIL");

        // ================= T4.2 - list after add =================
        record("T4.2 List Charges Details after Add", "Read the list after adding",
                "Every row has a View button, a Category and a Charges value",
                rows.size() + " row(s): " + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.rowsText(rows),
                rowsComplete(rows) ? "PASS" : "FAIL");

        // ================= T5.4 - duplicate category =================
        cd.selectCategory(category);
        if (!typeOrSkip(cd, "30.00", "T5.4 Duplicate Category")) { leave(cd, writeRequests); return; }
        clearAlerts();
        cd.clickAddCharge();
        String a4 = waitAlert(MSG_DUPLICATE);
        int afterDup = cd.chargesRows().size();
        record("T5.4 Duplicate Category", "Select the same Category '" + category + "' again, Charges 30.00, click Add",
                "Warning '" + MSG_DUPLICATE + "'; no second row for the category",
                "Alert: '" + a4 + "'; rows " + rows.size() + " -> " + afterDup,
                a4.contains(MSG_DUPLICATE) && afterDup == rows.size() ? "PASS" : "FAIL");

        // ================= T5.5 - non-numeric charges =================
        String second = null;
        List<String> inList = new ArrayList<>();
        for (String[] r : cd.chargesRows()) inList.add(r[0]);
        for (String o : options) if (!inList.contains(o)) { second = o; break; }
        if (second == null) {
            record("T5.5 Non-numeric Charges", "Second category + Charges 'abc', click Add", "Rejected as not a number",
                    "BLOCKED: no second unused Linen Category to test with", "SKIPPED");
        } else {
            int b5 = cd.chargesRows().size();
            cd.selectCategory(second);
            boolean typed = typeOrSkip(cd, "abc", "T5.5 Non-numeric Charges");
            clearAlerts();
            if (typed) cd.clickAddCharge();
            String a5 = waitAlert(".");
            boolean accepted = typed && cd.waitForRowCount(b5 + 1, 3000);
            if (accepted) defects.add("Charges Details accepts a non-numeric Charges value ('abc') - row added for '" + second + "'");
            if (typed) record("T5.5 Non-numeric Charges", "Category '" + second + "', Charges 'abc', click Add",
                    "Charges must be numeric - 'abc' rejected with a warning",
                    accepted ? "ACCEPTED: row [" + second + ", abc] was added (alerts " + capturedAlerts() + ") - the field has no numeric validation; confirm the requirement"
                            : "Rejected; alert '" + a5 + "'",
                    accepted ? "MANUAL" : "PASS");
        }

        // ================= T6 - data consistency =================
        List<String[]> table = cd.chargesRows();
        List<String[]> mdl = cd.modelRows();
        boolean sameAsModel = table.size() == mdl.size();
        for (int i = 0; sameAsModel && i < table.size(); i++)
            sameAsModel = table.get(i)[0].equals(mdl.get(i)[0]) && table.get(i)[1].equals(mdl.get(i)[1]);
        int idx = -1;
        for (int i = 0; i < table.size(); i++) if (table.get(i)[0].equals(category)) { idx = i; break; }
        boolean matchesInput = idx >= 0 && table.get(idx)[1].equals(enteredCharge);
        record("T6.1 Selected values vs list vs data model",
                "Compare the selected Category / entered Charges with the table row and the controller's list",
                "Row for '" + category + "' shows '" + enteredCharge + "'; table == lstComplianceDtlsList",
                "Table " + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.rowsText(table)
                        + " | model " + com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails.rowsText(mdl),
                sameAsModel && matchesInput ? "PASS" : "FAIL");

        boolean viewed = idx >= 0 && cd.clickViewRow(idx);
        String vCat = cd.selectedCategoryText(), vChg = cd.chargesValue();
        boolean viewOk = viewed && category.equals(vCat) && enteredCharge.equals(vChg);
        record("T6.2 View (eye) loads the row back", "Click View on the '" + category + "' row",
                "Category '" + category + "' and Charges '" + enteredCharge + "' loaded into the fields",
                viewed ? "Fields show Category '" + vCat + "', Charges '" + vChg + "'" : "View button not clickable",
                viewOk ? "PASS" : "FAIL");

        // T6.3 (Modify) deliberately not automated: Modify is never clicked (read-only rule). Known issue from the
        // controller source: ModifyCompliance() writes CategoryName while the list binds CategorName.

        addSummary("Category used", category);
        addSummary("Charge added (in memory only)", amount);
        leave(cd, writeRequests);
    }

    // ---- helpers ---------------------------------------------------------

    private void record(String name, String desc, String expected, String actual, String status) {
        step(page, name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "MANUAL": manual++; break;
            default: fail++;
        }
    }

    /** Make sure the form is on Charges Details, then type into the Charges box if it is ready. "" = typed. */
    private String typeCharges(com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails cd, String value) {
        if (!cd.ensureChargesTabActive()) return "Charges Details tab could not be re-opened (active tab '" + cd.activePane() + "')";
        return cd.enterCharges(value);
    }

    /** Like {@link #typeCharges}, recording the step as SKIPPED (with the reason) when the field is unavailable. */
    private boolean typeOrSkip(com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails cd, String value, String stepName) {
        String why = typeCharges(cd, value);
        if (why.isEmpty()) return true;
        record(stepName, "Enter Charges '" + value + "'", "Charges box visible, enabled and editable",
                "SKIPPED: Charges field unavailable - " + why, "SKIPPED");
        return false;
    }

    /**
     * After a validation warning, the form must still be on the Charges Details tab. If the app moved it to another
     * tab, record that, put the form back on Charges Details and confirm the Charges box is usable again.
     */
    private void checkFormState(com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails cd, String after) {
        String pane = cd.activePane();
        if ("Dtls".equals(pane) && cd.chargesFieldNotReadyReason(3000).isEmpty()) {
            record(after + " form state", "Check the form after the validation warning",
                    "Still on Charges Details; Charges box visible and editable", "Still on Charges Details; Charges box ready", "PASS");
            return;
        }
        boolean back = cd.ensureChargesTabActive();
        String notReady = back ? cd.chargesFieldNotReadyReason(3000) : "Charges Details tab could not be re-opened";
        defects.add("After the " + after + " warning the form switched to tab '" + pane + "' (Charges Details lost focus)");
        record(after + " form state", "Check the form after the validation warning",
                "Still on Charges Details; Charges box visible and editable",
                "Form had switched to tab '" + pane + "'" + (notReady.isEmpty() ? "; Charges Details re-opened, Charges box ready" : "; " + notReady),
                notReady.isEmpty() ? "PASS" : "FAIL");
    }

    private void clearAlerts() { page.evaluate("() => { window.__alerts = []; }"); }

    /** Explicit wait for a JAlert containing {@code text} (2.5s), then return the captured alert text. */
    private String waitAlert(String text) {
        try {
            page.waitForFunction("(t) => (window.__alerts||[]).some(a => String(a).includes(t))", text,
                    new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(2500));
        } catch (Exception ignore) { }
        return String.join(" | ", capturedAlerts());
    }

    private static boolean rowsComplete(List<String[]> rows) {
        if (rows.isEmpty()) return false;
        for (String[] r : rows) if (r[0].isBlank() || r[1].isBlank()) return false;
        return true;
    }

    /** Back out without saving and prove nothing was written. */
    private void leave(com.kpj.pages.AncillaryServices_page.Linen_page.ChargesDetails cd, List<String> writes) {
        cd.backWithoutSaving();
        finish(writes);
    }

    private void finish(List<String> writes) {
        boolean clean = writes.isEmpty();
        record("Safety: no data saved", "Leave with Back; Save is never clicked",
                "No save/insert/update/delete request sent",
                clean ? "No write request observed" : "WRITE REQUEST(S) SENT: " + writes, clean ? "PASS" : "FAIL");
        addSummary("Passed / Failed / Skipped(Blocked) / Manual", pass + " / " + fail + " / " + skipped + " / " + manual);
        addSummary("Defects / observations", defects.isEmpty() ? "None" : String.join("; ", defects));
        System.out.println("[" + testId + "] PASS=" + pass + " FAIL=" + fail + " SKIPPED=" + skipped + " MANUAL=" + manual);
    }
}
