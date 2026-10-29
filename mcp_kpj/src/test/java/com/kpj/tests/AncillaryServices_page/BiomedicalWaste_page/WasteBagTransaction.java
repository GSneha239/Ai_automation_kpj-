package com.kpj.tests.AncillaryServices_page.BiomedicalWaste_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The page object is also named WasteBagTransaction — it is referenced by its
// fully-qualified name (same convention as the Linen / CSSD tests).

/**
 * Ancillary Services &gt; Biomedical Waste &gt; <b>Waste Bag Transaction</b>.
 *
 * <p><b>Group A – Search Transaction</b></p>
 * <ol>
 *   <li><b>T1</b> Open the screen through the sidebar menu.</li>
 *   <li><b>T2</b> Page elements: From/To Date (default today), Search, Add, grid columns, pagination.</li>
 *   <li><b>T3</b> Search with a valid date range → request carries the dates; every row is inside the range.</li>
 *   <li><b>T4</b> Search a range that cannot hold data (2099) → empty grid; the app's own response is recorded.</li>
 *   <li><b>T5</b> Search with both dates empty → what the app does (records / validation / other) is recorded.</li>
 *   <li><b>T6</b> When rows exist: cells match the data, pagination and sorting work. SKIPPED when there are none.</li>
 * </ol>
 * <p><b>Group B – Add Bag and Save</b></p>
 * <ol start="7">
 *   <li><b>T7</b> Add opens the "Add / Update Bag Details" form.</li>
 *   <li><b>T8</b> Form fields: Date, Bag Type (+ its options), Quantity, Weight, Save, Back; mandatory markers.</li>
 *   <li><b>T9</b> Save with every field empty → a validation message and NO save call.</li>
 *   <li><b>T10</b> Invalid input: letters, negative values, over-long values in Quantity / Weight.</li>
 *   <li><b>T11</b> Prepare a valid transaction (no save).</li>
 *   <li><b>T12</b> Save it. <b>WRITE — runs only with {@code -Ddevhis.allowSave=true}</b>, otherwise SKIPPED.</li>
 *   <li><b>T13</b> Search for the saved transaction. Needs T12.</li>
 * </ol>
 *
 * <p><b>Write safety.</b> T9 and T10 click Save with the IUD call aborted at the network layer (the page object's
 * {@code saveWithSaveBlocked()}), so they cannot create a record even though the page has no client-side checks. Only
 * T12 lets a save through, and only when {@code devhis.allowSave=true}. Nothing is edited or deleted; the screen has no
 * delete. A T12 record is recognisable by today's date, the chosen Bag Type and the quantity/weight in the summary.</p>
 *
 * <p><b>Configuration</b> (never hard-coded; values are never printed): {@code DEVHIS_URL}, {@code DEVHIS_USER},
 * {@code DEVHIS_PASS} env vars (or {@code -Ddevhis.url/user/pass}). Optional test data:
 * {@code -Ddevhis.wbt.fromDate / toDate} (dd/MM/yyyy, T3 range; default one year back → today),
 * {@code -Ddevhis.wbt.bagType} (Bag Type text; default the first option), {@code -Ddevhis.wbt.quantity} (default 1),
 * {@code -Ddevhis.wbt.weight} (default 1.5).</p>
 *
 * <pre>
 *   read-only : mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction"
 *   with save : mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction" "-Ddevhis.allowSave=true"
 * </pre>
 */
public class WasteBagTransaction extends DevHisBase {

    /** Write gate — T12/T13 create a real record only when this is true. */
    static final boolean ALLOW_SAVE = Boolean.getBoolean("devhis.allowSave");

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction wbt;
    private final List<String> defects = new ArrayList<>();

    public WasteBagTransaction() { super("AncillaryServices_BiomedicalWaste_WasteBagTransaction"); }

    public static void main(String[] args) {
        WasteBagTransaction t = new WasteBagTransaction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Biomedical Waste - Waste Bag Transaction",
                "Ancillary Services > Biomedical Waste > Waste Bag Transaction",
                "Search Transaction (T1-T6) and Add Bag and Save (T7-T13). Saving runs only with -Ddevhis.allowSave=true; "
                        + "validation checks block the save API at the network layer so they cannot create records.");
        addSummary("Environment", BASE);
        addSummary("Save mode", ALLOW_SAVE ? "WRITE enabled (-Ddevhis.allowSave=true)" : "READ-ONLY (T12/T13 skipped)");

        String missing = missingConfig();
        if (!missing.isEmpty()) {
            step("Configuration", "Read DEVHIS_URL / DEVHIS_USER / DEVHIS_PASS",
                    "All three are set", "Not set: " + missing + " — set them as environment variables (or -Ddevhis.url/user/pass)", "FAIL");
            addSummary("Result", "FAILED - missing configuration: " + missing);
            return;
        }
        // Use ONLY the supplied account: an empty, non-blank fallback list makes LoginPage try nothing else.
        if (System.getProperty("devhis.cred.fallbacks") == null) System.setProperty("devhis.cred.fallbacks", ",");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", "Log in as the DEVHIS_USER account", "Authenticated; dashboard shown", "Logged in as " + USER, "PASS");

        wbt = new com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction(page);
        try {
            // ---- Group A: Search Transaction
            if (!t1Open()) return;
            t2Elements();
            int rowsInRange = t3ValidSearch();
            t4NoMatch();
            t5EmptyCriteria();
            t6Results(rowsInRange);

            // ---- Group B: Add Bag and Save
            if (!t7OpenAdd()) return;
            List<String> bagTypes = t8Fields();
            t9Mandatory();
            t10Invalid();
            String[] prepared = t11Prepare(bagTypes);
            String[] saved = t12Save(prepared);
            t13Verify(saved);
        } finally {
            List<String> jsErrors = wbt.appErrors();
            addSummary("App JS errors (console)", jsErrors.isEmpty() ? "none" : String.join(" || ", jsErrors));
            addSummary("Suspected application defects", defects.isEmpty() ? "none" : String.join(" || ", defects));
            wbt.dispose();
        }
    }

    // =============================================== Group A ========================================================

    private boolean t1Open() {
        boolean on = wbt.navigateViaMenu();
        String landed = wbt.currentScreen();
        String how = wbt.usedMenu ? "via the sidebar menu" : "via the direct route (sidebar link could not be used)";
        step(page, "T1 Open Waste Bag Transaction", "Ancillary Services -> Biomedical Waste -> Waste Bag Transaction",
                "The Waste Bag Transaction list opens (route #/WasteBagTransaction, title 'Waste Bag Transaction')",
                on ? "Opened " + how + ": " + landed : "WRONG PAGE / not opened - app shows: " + landed + "\n" + wbt.describeRoutes("WasteBag"),
                on && wbt.usedMenu ? "PASS" : "FAIL");
        if (!on) addSummary("Result", "FAILED - Waste Bag Transaction screen did not open: " + landed);
        return on;
    }

    private void t2Elements() {
        wbt.waitForInitialList(8000);
        String today = LocalDate.now().format(DMY);
        String from = wbt.fromDate(), to = wbt.toDate();
        List<String> cols = wbt.gridColumns();
        List<String> absent = com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.EXPECTED_COLUMNS.stream()
                .filter(c -> cols.stream().noneMatch(x -> x.equalsIgnoreCase(c))).collect(Collectors.toList());
        String problems = wbt.listProblems();
        Map<String, Object> pg = wbt.pagination();
        boolean ok = problems.isEmpty() && absent.isEmpty();
        String actual = "From Date='" + from + "', To Date='" + to + "'" + (today.equals(from) && today.equals(to) ? " (today)" : "")
                + " | Columns: " + cols + " | Pagination: page size " + pg.get("pageSize") + ", sizes " + pg.get("sizes")
                + " | Initial load: " + (wbt.lastFetch == null ? "(list API not observed)" : wbt.lastFetch)
                + (problems.isEmpty() ? "" : " | MISSING controls: " + problems)
                + (absent.isEmpty() ? "" : " | MISSING columns: " + absent);
        step(page, "T2 Page elements", "Read the search section, buttons, grid header and pager",
                "From Date, To Date, Search, Add; columns Transaction Date / Bag Type / Quantity / Weight / Edit; pagination",
                actual, ok ? "PASS" : "FAIL");
    }

    /** @return the number of rows the valid search returned (0 when none / failed) */
    private int t3ValidSearch() {
        LocalDate today = LocalDate.now();
        String from = System.getProperty("devhis.wbt.fromDate", today.minusYears(1).format(DMY));
        String to = System.getProperty("devhis.wbt.toDate", today.format(DMY));
        LocalDate fromD = parseDate(from), toD = parseDate(to);
        wbt.setDates(from, to);
        com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.FetchResult r = wbt.search();
        List<Map<String, Object>> rows = wbt.gridData();
        List<String> outside = new ArrayList<>(), unparsed = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String d = String.valueOf(row.get("date"));
            LocalDate ld = parseDate(d);
            if (ld == null) unparsed.add(d);
            else if (fromD != null && toD != null && (ld.isBefore(fromD) || ld.isAfter(toD))) outside.add(d);
        }
        boolean sentDates = r != null && r.requestBody.contains(from) && r.requestBody.contains(to);
        boolean ok = r != null && r.status == 200 && sentDates && outside.isEmpty();
        String actual = "Search " + from + " to " + to + " -> " + (r == null ? "(no list call)" : r)
                + " | Grid rows: " + rows.size()
                + (rows.isEmpty() ? " (no Waste Bag transactions exist in this range on this environment)" : " e.g. " + rows.get(0))
                + (sentDates ? "" : " | the request did NOT carry the entered dates")
                + (outside.isEmpty() ? "" : " | rows OUTSIDE the range: " + outside)
                + (unparsed.isEmpty() ? "" : " | dates not in a known format (not range-checked): " + unparsed);
        step(page, "T3 Search with valid criteria", "Enter From Date " + from + " and To Date " + to + "; click Search",
                "The list API is called with these dates (HTTP 200) and every returned transaction lies in the range",
                actual, ok ? "PASS" : "FAIL");
        addSummary("Transactions " + from + " - " + to, String.valueOf(rows.size()));
        return ok ? rows.size() : 0;
    }

    private void t4NoMatch() {
        String from = "01/01/2099", to = "31/01/2099";
        wbt.captureAlerts();
        wbt.setDates(from, to);
        com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.FetchResult r = wbt.search();
        int rows = wbt.gridData().size();
        int shown = wbt.visibleRows().size();
        List<String> alerts = wbt.alerts();
        boolean ok = r != null && r.status == 200 && rows == 0 && shown == 0;
        step(page, "T4 Search with no matching results", "Enter From Date " + from + " and To Date " + to + "; click Search",
                "No transactions are listed (empty grid); the application's own response is recorded",
                (r == null ? "(no list call)" : r) + " | Grid rows: " + rows + ", rendered: " + shown
                        + " | Message shown: " + (alerts.isEmpty() ? "none (the grid is simply empty - no 'no records' message)" : alerts),
                ok ? "PASS" : "FAIL");
    }

    private void t5EmptyCriteria() {
        wbt.captureAlerts();
        wbt.setDates("", "");
        com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.FetchResult r = wbt.search();
        int rows = wbt.gridData().size();
        List<String> alerts = wbt.alerts();
        String behaviour;
        if (r == null || r.status < 0) behaviour = "no search was sent";
        else if (!alerts.isEmpty()) behaviour = "message shown: " + alerts;
        else behaviour = "no validation message; the search ran with empty dates and returned " + rows + " row(s)";
        // Observational: the scenario asks what the app does with empty criteria. Only an outright failure (server error,
        // no answer) fails the step.
        boolean ok = r != null && (r.status == 200 || !alerts.isEmpty());
        step(page, "T5 Search with empty criteria", "Clear From Date and To Date; click Search",
                "The application either lists records, shows a validation message or responds in another defined way (recorded)",
                "Behaviour: " + behaviour + " | List API: " + (r == null ? "(no list call)" : r), ok ? "PASS" : "FAIL");
        addSummary("Empty-criteria search", behaviour);
    }

    private void t6Results(int rowsInRange) {
        // Re-run the valid search so the grid holds the T3 rows again.
        LocalDate today = LocalDate.now();
        String from = System.getProperty("devhis.wbt.fromDate", today.minusYears(1).format(DMY));
        String to = System.getProperty("devhis.wbt.toDate", today.format(DMY));
        wbt.setDates(from, to);
        wbt.search();
        List<Map<String, Object>> data = wbt.gridData();
        if (data.isEmpty()) {
            step(page, "T6 Verify transaction results", "Check the cells, pagination and sorting of the listed transactions",
                    "Cells show the transaction data; pagination and column sorting work",
                    "Not run - the search returned no transactions (" + rowsInRange + " in " + from + " - " + to
                            + "); there is nothing to verify on this environment", "SKIPPED");
            return;
        }
        List<String> problems = new ArrayList<>();
        // cells vs data (first rendered row must be one of the data rows)
        List<List<String>> shown = wbt.visibleRows();
        if (shown.isEmpty()) problems.add("grid holds " + data.size() + " row(s) but none is rendered");
        else {
            List<String> first = shown.get(0);
            boolean match = data.stream().anyMatch(d -> first.size() >= 4
                    && first.get(1).equals(String.valueOf(d.get("bagtype")))
                    && first.get(2).equals(String.valueOf(d.get("quantity")))
                    && first.get(3).equals(String.valueOf(d.get("weight"))));
            if (!match) problems.add("first rendered row " + first + " does not match any data row");
        }
        // pagination
        Map<String, Object> pg = wbt.pagination();
        int size = pg.get("pageSize") instanceof Number ? ((Number) pg.get("pageSize")).intValue() : 25;
        int expectedPages = Math.max(1, (int) Math.ceil(data.size() / (double) size));
        String totalPages = String.valueOf(pg.get("totalPages"));
        if (shown.size() > size) problems.add("rendered " + shown.size() + " rows on a page of " + size);
        if (!totalPages.isEmpty() && !totalPages.equals(String.valueOf(expectedPages)))
            problems.add("pager shows " + totalPages + " page(s), expected " + expectedPages);
        // sorting (meaningful with 2+ rows)
        String sortNote = "not checked (fewer than 2 rows)";
        if (data.size() >= 2 && wbt.clickColumnHeader("Quantity")) {
            List<Double> q = wbt.visibleRows().stream().filter(c -> c.size() >= 3).map(c -> toNumber(c.get(2)))
                    .filter(d -> d != null).collect(Collectors.toList());
            boolean asc = isSorted(q, true), desc = isSorted(q, false);
            sortNote = "Quantity after header click: " + q + (asc ? " (ascending)" : desc ? " (descending)" : " (NOT sorted)");
            if (!asc && !desc) problems.add("clicking the Quantity header did not sort the column");
        }
        step(page, "T6 Verify transaction results", "Compare rendered cells with the data, check the pager and sort by Quantity",
                "Cells show the transaction data; the pager matches the row count; sorting orders the column",
                "Rows: " + data.size() + ", rendered on page 1: " + shown.size() + " | Pager: " + pg + " | Sorting: " + sortNote
                        + (problems.isEmpty() ? "" : " | PROBLEMS: " + problems),
                problems.isEmpty() ? "PASS" : "FAIL");
    }

    // =============================================== Group B ========================================================

    private boolean t7OpenAdd() {
        boolean open = wbt.openAddForm();
        String heading = open ? wbt.formHeading() : "";
        step(page, "T7 Open Add Bag form", "Click Add on the Waste Bag Transaction list",
                "The 'Add / Update Bag Details' form opens (route #/addWasteBagTransaction)",
                open ? "Form opened: '" + heading + "' @ " + page.url() : "Form did not open - app shows: " + wbt.currentScreen(),
                open ? "PASS" : "FAIL");
        return open;
    }

    /** @return the Bag Type options offered */
    private List<String> t8Fields() {
        List<Map<String, Object>> fields = wbt.formFields();
        List<String> bagTypes = wbt.bagTypeOptions();
        List<String> labels = fields.stream().map(f -> String.valueOf(f.get("label")).replace("*", "").trim()).collect(Collectors.toList());
        List<String> expected = List.of("Date", "Bag Type", "Quantity", "Weight");
        List<String> absent = expected.stream().filter(e -> labels.stream().noneMatch(l -> l.equalsIgnoreCase(e))).collect(Collectors.toList());
        boolean buttons = wbt.saveButton().isVisible() && wbt.backButton().isVisible();
        List<String> mandatory = fields.stream().filter(f -> Boolean.TRUE.equals(f.get("mandatory")))
                .map(f -> String.valueOf(f.get("label"))).collect(Collectors.toList());
        String desc = fields.stream().map(f -> f.get("label") + " [" + f.get("control") + (String.valueOf(f.get("maxlength")).isEmpty() ? "" : ", maxlength " + f.get("maxlength"))
                + ", value '" + f.get("value") + "']").collect(Collectors.joining("; "));
        boolean ok = absent.isEmpty() && buttons;
        step(page, "T8 Verify form fields", "Read every field, its marker and the Bag Type options",
                "Date, Bag Type, Quantity, Weight, Save and Back are present; mandatory fields are identified",
                "Fields: " + desc + " | Save/Back: " + (buttons ? "present" : "MISSING")
                        + " | Marked mandatory: " + (mandatory.isEmpty() ? "none (no '*' or required marker on any field)" : mandatory)
                        + " | Bag Type options: " + (bagTypes.isEmpty() ? "NONE - the Waste Bag Type master returns no entries (" + wbt.bagTypeApi + ")" : bagTypes)
                        + (absent.isEmpty() ? "" : " | MISSING fields: " + absent),
                ok ? "PASS" : "FAIL");
        addSummary("Bag Type options", bagTypes.isEmpty() ? "none (" + wbt.bagTypeApi + ")" : String.valueOf(bagTypes));
        return bagTypes;
    }

    private void t9Mandatory() {
        wbt.clearForm();
        com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.BlockedSave s = wbt.saveWithSaveBlocked();
        boolean ok = !s.saveCallAttempted && !s.alerts.isEmpty();
        String actual;
        if (s.saveCallAttempted) {
            actual = "APPLICATION DEFECT (suspected): no validation - the page called the save API with empty fields, payload "
                    + s.postedBody + " (blocked by the test, so nothing was saved). Message afterwards: "
                    + (s.alerts.isEmpty() ? "none" : s.alerts + " (reaction to the blocked request, not validation)");
            defects.add("T9: Save with empty Date/Bag Type/Quantity/Weight is sent to the server without validation");
        } else {
            actual = s.alerts.isEmpty() ? "No save call and no message shown" : "Validation: " + s.alerts + "; no save call";
        }
        step(page, "T9 Mandatory-field validation", "Clear Date, Bag Type, Quantity and Weight; click Save (save API blocked for this check)",
                "A validation message names the missing field(s) and no save call is made", actual, ok ? "PASS" : "FAIL");
    }

    private void t10Invalid() {
        // a) letters - the page's key filter should drop them
        String q = wbt.typeQuantity("abc"), w = wbt.typeWeight("xyz");
        boolean lettersBlocked = q.isEmpty() && w.isEmpty();
        step(page, "T10a Invalid input - letters", "Type 'abc' in Quantity and 'xyz' in Weight",
                "Letters are rejected (fields stay numeric)", "Quantity='" + q + "', Weight='" + w + "'",
                lettersBlocked ? "PASS" : "FAIL");
        if (!lettersBlocked) defects.add("T10a: Quantity/Weight accept letters");

        // b) negative values - must not be savable
        wbt.setFormDate(LocalDate.now().format(DMY));
        String nq = wbt.typeQuantity("-5"), nw = wbt.typeWeight("-2.5");
        com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.BlockedSave s = wbt.saveWithSaveBlocked();
        boolean negRejected = !s.saveCallAttempted;
        String actualB = "Quantity='" + nq + "', Weight='" + nw + "' | "
                + (s.saveCallAttempted
                    ? "APPLICATION DEFECT (suspected): the page sent the negative values to the save API, payload " + s.postedBody
                      + " (blocked by the test, nothing saved)"
                    : "no save call; message: " + (s.alerts.isEmpty() ? "none" : s.alerts));
        step(page, "T10b Invalid input - negative values", "Enter Quantity -5 and Weight -2.5; click Save (save API blocked)",
                "Negative quantity/weight are rejected - not typed in, or a validation message and no save call", actualB,
                negRejected ? "PASS" : "FAIL");
        if (!negRejected) defects.add("T10b: negative Quantity/Weight (" + nq + " / " + nw + ") are sent to the server");

        // c) length limit
        String lq = wbt.typeQuantity("12345678"), lw = wbt.typeWeight("98765432");
        boolean limited = lq.length() <= 6 && lw.length() <= 6;
        step(page, "T10c Invalid input - length", "Type 8 digits into Quantity and Weight",
                "Each field stops at its maxlength (6)", "Quantity='" + lq + "' (" + lq.length() + "), Weight='" + lw + "' (" + lw.length() + ")",
                limited ? "PASS" : "FAIL");
        wbt.clearForm();
    }

    /** @return {date, bagType, quantity, weight} of a valid, filled-in transaction, or null when it cannot be prepared */
    private String[] t11Prepare(List<String> bagTypes) {
        if (bagTypes.isEmpty()) {
            step(page, "T11 Prepare a valid Add Bag transaction", "Fill Date, Bag Type, Quantity and Weight",
                    "The form holds a complete, valid transaction",
                    "BLOCKED by test data - the Bag Type dropdown has no options (Waste Bag Type master is empty: " + wbt.bagTypeApi
                            + "). Add a Waste Bag Type master entry on this environment, then re-run.", "SKIPPED");
            return null;
        }
        String date = LocalDate.now().format(DMY);
        String bag = System.getProperty("devhis.wbt.bagType", bagTypes.get(0));
        String qty = System.getProperty("devhis.wbt.quantity", "1");
        String wt = System.getProperty("devhis.wbt.weight", "1.5");
        wbt.setFormDate(date);
        boolean picked = wbt.selectBagType(bag);
        wbt.typeQuantity(qty);
        wbt.typeWeight(wt);
        Map<String, Object> m = wbt.formModel();
        boolean ok = picked && date.equals(m.get("date")) && bag.equals(m.get("bagtype")) && !String.valueOf(m.get("bagtypeid")).isEmpty()
                && qty.equals(m.get("quantity")) && wt.equals(m.get("weight"));
        step(page, "T11 Prepare a valid Add Bag transaction",
                "Date " + date + ", Bag Type '" + bag + "', Quantity " + qty + ", Weight " + wt + " (not saved)",
                "The form's model holds exactly these values, ready to save",
                (picked ? "" : "Bag Type '" + bag + "' is not an option " + bagTypes + " | ") + "Form model: " + m,
                ok ? "PASS" : "FAIL");
        return ok ? new String[]{date, bag, qty, wt} : null;
    }

    /** @return the transaction the app confirmed saving, or null */
    private String[] t12Save(String[] prepared) {
        if (!ALLOW_SAVE) {
            step(page, "T12 Save transaction", "Click Save on the prepared transaction",
                    "'" + com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.MSG_SAVED + "' and back on the list",
                    "Not run - writing to " + BASE + " needs -Ddevhis.allowSave=true (manual / approved run)", "SKIPPED");
            return null;
        }
        if (prepared == null) {
            step(page, "T12 Save transaction", "Click Save on the prepared transaction",
                    "'" + com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.MSG_SAVED + "'",
                    "Not run - T11 did not prepare a valid transaction", "SKIPPED");
            return null;
        }
        String alerts = wbt.saveForReal();
        boolean saved = alerts.contains(com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.MSG_SAVED);
        boolean backOnList = wbt.waitForListScreen(10000);
        step(page, "T12 Save transaction", "Click Save (date " + prepared[0] + ", " + prepared[1] + ", qty " + prepared[2] + ", weight " + prepared[3] + ")",
                "'" + com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction.MSG_SAVED + "' and the list is shown again",
                (alerts.isEmpty() ? "No message shown" : "Message: " + alerts) + " | Save API: " + wbt.lastSaveApi
                        + " | Back on list: " + backOnList,
                saved && backOnList ? "PASS" : "FAIL");
        if (saved) addSummary("Created transaction", prepared[0] + " / " + prepared[1] + " / qty " + prepared[2] + " / weight " + prepared[3]);
        return saved ? prepared : null;
    }

    private void t13Verify(String[] saved) {
        if (saved == null) {
            step(page, "T13 Verify saved transaction", "Search today's transactions for the saved bag",
                    "The saved transaction is listed",
                    ALLOW_SAVE ? "Not run - T12 did not confirm a save" : "Not run - nothing was saved (read-only mode)", "SKIPPED");
            return;
        }
        if (!wbt.onListScreen()) wbt.navigateViaMenu();
        wbt.setDates(saved[0], saved[0]);
        wbt.search();
        Map<String, Object> row = wbt.gridData().stream().filter(r -> saved[1].equals(String.valueOf(r.get("bagtype")))
                && sameNumber(saved[2], String.valueOf(r.get("quantity"))) && sameNumber(saved[3], String.valueOf(r.get("weight"))))
                .findFirst().orElse(null);
        step(page, "T13 Verify saved transaction", "Search " + saved[0] + " - " + saved[0] + " and look for the saved bag",
                "A row with Bag Type '" + saved[1] + "', Quantity " + saved[2] + ", Weight " + saved[3] + " is listed",
                row == null ? "NOT found among " + wbt.gridData().size() + " row(s) (" + wbt.lastFetch + ")" : "Listed: " + row,
                row != null ? "PASS" : "FAIL");
    }

    // =============================================== helpers ========================================================

    /** Parses dd/MM/yyyy, yyyy-MM-dd(THH:mm...) or dd-MM-yyyy; null when the text is in none of these. */
    static LocalDate parseDate(String s) {
        if (s == null) return null;
        String t = s.trim();
        try {
            if (t.matches("\\d{2}/\\d{2}/\\d{4}.*")) return LocalDate.parse(t.substring(0, 10), DMY);
            if (t.matches("\\d{4}-\\d{2}-\\d{2}.*")) return LocalDate.parse(t.substring(0, 10));
            if (t.matches("\\d{2}-\\d{2}-\\d{4}.*")) return LocalDate.parse(t.substring(0, 10), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception ignore) { }
        return null;
    }

    private static Double toNumber(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return null; }
    }

    private static boolean sameNumber(String a, String b) {
        Double x = toNumber(a), y = toNumber(b);
        return x != null && y != null ? Math.abs(x - y) < 1e-9 : a.equals(b);
    }

    private static boolean isSorted(List<Double> v, boolean asc) {
        for (int i = 1; i < v.size(); i++) if (asc ? v.get(i) < v.get(i - 1) : v.get(i) > v.get(i - 1)) return false;
        return true;
    }

    /** Which of the URL / user / password settings were NOT supplied via env var or -D (names only, never values). */
    private static String missingConfig() {
        StringBuilder sb = new StringBuilder();
        if (!supplied("DEVHIS_URL", "devhis.url")) sb.append("DEVHIS_URL ");
        if (!supplied("DEVHIS_USER", "devhis.user")) sb.append("DEVHIS_USER ");
        if (!supplied("DEVHIS_PASS", "devhis.pass")) sb.append("DEVHIS_PASS ");
        return sb.toString().trim();
    }

    private static boolean supplied(String env, String prop) {
        String v = System.getProperty(prop);
        if (v != null && !v.isBlank()) return true;
        v = System.getenv(env);
        return v != null && !v.isBlank();
    }
}
