package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Billing_page.ChargesScreenSections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>OPD Charges</b> — section coverage: <b>Patient Details</b>, <b>Bill Details</b>,
 * <b>Package Services</b>, <b>Package Item</b>.
 *
 * <p><b>Read-only by design.</b> Never clicks Save ({@code IUDBill}) or Validated, never answers a confirm with
 * anything but <b>No</b>, never touches a charge row that existed before the test. On OPD Charges deleting a
 * <i>posted</i> row cancels it on the server ({@code OPDBillFactory.IUDBill}, ExecFlag {@code CancelService}); the
 * cleanup therefore only removes rows this test added that are unsaved AND unpaired, and runs in {@code finally}.
 * The last step proves no write request was sent. (The saving end-to-end flow stays in
 * {@link com.kpj.tests.Billing_page.OpdCharges}, unchanged.)</p>
 *
 * <p>Mapped live on HIS Demo, 26/10/2026: the Package Service tab is fed by <b>health plans</b>
 * ({@code ishealthplan}); a <b>package</b> ({@code ispackage}) is refused on OPD with "You cannot add a Package
 * Service". <b>BLOCKED</b> (never PASS) is reported when the environment cannot supply what a scenario needs.</p>
 *
 * <p>Environment: {@code DEVHIS_URL} / {@code DEVHIS_USER} / {@code DEVHIS_PASS}. Report:
 * {@code test-output/reports/Billing_OpdCharges_Sections.html}. Run:
 * {@code mvn test -Dtest=OpdChargesSections -Dmaven.test.failure.ignore=false}.</p>
 */
public class OpdChargesSections extends DevHisBase {

    public OpdChargesSections() { super("Billing_OpdCharges_Sections"); }

    public static void main(String[] args) {
        OpdChargesSections t = new OpdChargesSections();
        try { t.run(); } finally { t.stop(); }
    }

    /** FAIL steps (not BLOCKED) fail the JUnit test — DevHisBase.run() itself never throws. */
    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
        if (!failedSteps.isEmpty())
            org.junit.jupiter.api.Assertions.fail(failedSteps.size() + " step(s) FAILED in " + testId + ":\n - "
                    + String.join("\n - ", failedSteps) + "\nReport: " + reportDir.resolve(testId + ".html").toAbsolutePath());
    }

    private final List<String> failedSteps = new ArrayList<>();

    private void track(String name, String actual, String status) {
        if ("FAIL".equalsIgnoreCase(status)) failedSteps.add(name + " -> " + (actual == null ? "" : actual.replace('\n', ' ')));
    }

    @Override
    public void step(com.microsoft.playwright.Page p, String name, String desc, String expected, String actual, String status) {
        track(name, actual, status);
        super.step(p, name, desc, expected, actual, status);
    }

    @Override
    public void step(byte[] png, String name, String desc, String expected, String actual, String status) {
        track(name, actual, status);
        super.step(png, name, desc, expected, actual, status);
    }

    // Confirmed live on HIS Demo, 26/10/2026.
    private static final List<String> BILL_GRID_CORE_COLUMNS = Arrays.asList(
            "Delete", "Cost Center", "Payor", "Service Code", "Service Name", "Date", "Qty", "Price", "Total Amount", "Net Amount");
    private static final List<String> PACKAGE_SERVICE_COLUMNS = Arrays.asList(
            "Service Code", "Service Name", "Quantity", "Unit Purchase Price", "Total Amount", "Discount%", "Discount Amount",
            "Service Tax Amount", "Net Amount", "Self Amount", "Non-Self Amount", "Doctor Share", "Hospital Share", "Doctor", "Remark");
    private static final List<String> PACKAGE_ITEM_COLUMNS = Arrays.asList(
            "Item Code", "Item Name", "Store Name", "Batch Code", "Available Quantity", "Quantity", "Total Amount", "Remark");
    private static final List<String> SERVICE_ENTRY_FIELDS = Arrays.asList("ServiceCode", "ServiceName", "CPTCode", "quantity", "rate");
    /** Search terms tried to find an item with the wanted flag. Not test data: the item used is whatever the
     *  environment returns. */
    private static final String[] PKG_QUERIES = { "PKG", "Package", "Pkg" };

    @Override
    protected void body() {
        try { sections(); }
        catch (RuntimeException | Error e) { failedSteps.add("ERROR (unexpected exception) -> " + e); throw e; }
    }

    private void sections() {
        meta("Billing - OPD Charges (Sections)", "Billing > OPD Charges",
                "Patient Details, Bill Details, Package Services and Package Item on the OPD Charges screen. Read-only: "
              + "never clicks Save, never touches posted charges; rows the test adds are removed in cleanup. "
              + "BLOCKED = the environment has no data for that scenario (not a pass).");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login (DEVHIS_USER / DEVHIS_PASS)", "Authenticated", "Logged in to " + BASE, "PASS");

        com.kpj.pages.Billing_page.OpdCharges opc = new com.kpj.pages.Billing_page.OpdCharges(page);
        ChargesScreenSections cs = opc.sections();
        cs.startNetworkAudit();
        List<String> baseline = null;
        int baselineBreakdown = -1;
        int writeMark = 0;
        String planCode = "";
        try {
            // =========================== PATIENT DETAILS ===========================
            boolean onScreen = opc.navigateTo(BASE);
            cs.installCaptureHooks();
            writeMark = cs.networkMark();
            Map<String, Object> tabs = cs.chargeTabs();
            boolean tabsOk = tabs.values().stream().noneMatch(v -> String.valueOf(v).contains("(missing)") || String.valueOf(v).contains("(hidden)"));
            step("[Patient Details] Open OPD Charges", "Billing -> OPD Charges (menu link #/OPDCharges)",
                    "Screen opens; Bill Details / Package Service / Package Item / Cancelled Charges tabs present",
                    (onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")") + " | route " + opc.route + "\nTabs: " + tabs,
                    onScreen && tabsOk ? "PASS" : "FAIL");
            if (!onScreen) { addSummary("Result", "Screen not reached"); return; }

            String emptyMsg = cs.searchByMrn("");
            step("[Patient Details] Search with MRN empty", "Clear MRN No., click Find Patient (SearchPatientByMRNo)",
                    "Warning to enter the MRN", emptyMsg.isEmpty() ? "No message shown" : "Message: " + emptyMsg,
                    emptyMsg.toLowerCase().matches(".*please enter.*mr.*") ? "PASS" : "FAIL");

            String badMrn = "ZZ" + (System.currentTimeMillis() % 1000000);
            String badMsg = cs.searchByMrn(badMrn);
            step("[Patient Details] Search with an unknown MRN", "MRN \"" + badMrn + "\" (generated, cannot exist) -> Find Patient",
                    "Warning \"Patient Not Found\"", badMsg.isEmpty() ? "No message shown" : "Message: " + badMsg,
                    badMsg.toLowerCase().contains("patient not found") ? "PASS" : "FAIL");

            Map<String, Object> today = opc.searchInPatientPopup(false);
            Map<String, Object> any = opc.searchInPatientPopup(true);
            @SuppressWarnings("unchecked") List<String> heads = (List<String>) any.getOrDefault("heads", new ArrayList<String>());
            boolean resultsOk = ((Number) any.getOrDefault("rows", 0)).intValue() > 0 && heads.contains("MRN") && heads.contains("Name");
            step("[Patient Details] Patient search results", "Search Patient popup: default search (" + today.get("filter") + "), then without the visit-date filter",
                    "Results grid lists patients with MRN / Name columns",
                    "Default: " + (String.valueOf(today.get("alert")).isEmpty() ? today.get("rows") + " row(s)" : "\"" + today.get("alert") + "\"")
                            + "\nAny visit date (" + any.get("filter") + "): " + any.get("rows") + " row(s); columns " + heads + "; e.g. " + any.get("sample"),
                    resultsOk ? "PASS" : (((Number) any.getOrDefault("rows", 0)).intValue() == 0 ? "BLOCKED" : "FAIL"));
            opc.closePatientPopup();

            Map<String, Object> found = opc.findPatientWithOpenOpVisit(8);
            String mrn = String.valueOf(found.get("mrn"));
            step("[Patient Details] Find a patient with an open OP visit", "Load each candidate MRN; keep the first whose OP Visit No. list has a visit",
                    "A patient with an OP visit that can take charges", mrn.isEmpty() ? "None of the candidates has an OP visit: " + found.get("tried")
                            : "Using " + mrn + " (" + found.get("visit") + ") | tried: " + found.get("tried"),
                    mrn.isEmpty() ? "BLOCKED" : "PASS");
            if (mrn.isEmpty()) { addSummary("Result", "No patient with an open OP visit on this environment - remaining sections not run"); return; }

            cs.searchByMrn("ZZ" + (System.currentTimeMillis() % 1000000));   // clear the screen so the pick below is a real load
            opc.searchInPatientPopup(true);
            Map<String, Object> pick = opc.selectInPatientPopup(mrn);
            Map<String, String> hdr = cs.readPatientHeader();
            boolean pickOk = Boolean.TRUE.equals(pick.get("selected")) && mrn.equals(hdr.get("MRNo")) && !hdr.getOrDefault("visit", "").isEmpty()
                    && hdr.getOrDefault("banner", "").contains(mrn) && !opc.popupShown();
            step("[Patient Details] Select the patient from the search results", "Search Patient popup -> any visit date -> Search -> select " + mrn,
                    "MRN, OP Visit No., Payor and the patient banner (Name / MRN / Mobile / Gender / Age) are filled",
                    "Loaded MRN " + hdr.get("MRNo") + " | OP Visit No. " + hdr.get("visit") + " | Payor " + hdr.get("payor")
                            + "\nBanner: " + hdr.get("banner")
                            + (Boolean.TRUE.equals(pick.get("popupStayedOpen")) ? "\nNote: the popup stayed open after selecting the patient (closed by the test"
                               + (Boolean.TRUE.equals(pick.get("popupClosedByTest")) ? ")" : " - FAILED to close)") : ""),
                    pickOk ? "PASS" : "FAIL");

            String reMsg = cs.searchByMrn(mrn);
            Map<String, String> hdr2 = cs.readPatientHeader();
            boolean same = mrn.equals(hdr2.get("MRNo")) && hdr.getOrDefault("visit", "x").equals(hdr2.get("visit"));
            step("[Patient Details] Search directly by MRN", "Type " + mrn + " in MRN No. -> Find Patient",
                    "The same patient and OP visit load", "MRN " + hdr2.get("MRNo") + " | OP Visit No. " + hdr2.get("visit") + (reMsg.isEmpty() ? "" : " | message: " + reMsg),
                    same ? "PASS" : "FAIL");

            baseline = cs.gridFingerprint();
            baselineBreakdown = cs.breakdownEntries();
            addSummary("Patient", mrn + " / " + hdr2.get("visit"));
            addSummary("Charge rows before the test", baseline.size() + " " + baseline);

            // ============================ BILL DETAILS =============================
            List<String> cols = cs.billGridColumns();
            List<String> missingCols = missing(BILL_GRID_CORE_COLUMNS, cols);
            step("[Bill Details] Charge grid columns", "Read the Bill Details grid's column definitions",
                    "Includes " + BILL_GRID_CORE_COLUMNS, cols.size() + " columns: " + cols + (missingCols.isEmpty() ? "" : "\nMISSING: " + missingCols),
                    missingCols.isEmpty() ? "PASS" : "FAIL");

            List<String> entry = cs.serviceEntryFields();
            List<String> missingEntry = new ArrayList<>();
            for (String f : SERVICE_ENTRY_FIELDS) if (entry.stream().noneMatch(e -> e.startsWith(f))) missingEntry.add(f);
            Map<String, Object> bc = cs.billControls();
            boolean bcOk = missingEntry.isEmpty() && !String.valueOf(bc.get("PatientDetails.OPDNo")).startsWith("(empty)")
                    && !String.valueOf(bc.get("PatientDetails.ReceivableID")).startsWith("(empty)") && String.valueOf(bc.get("ChargeDate")).contains("(read-only)");
            step("[Bill Details] Billing controls load", "Service entry row, OP Visit No., Payor, Cash Counter, Validated By, Charges Date",
                    "Service entry inputs present; OP Visit No. and Payor selected; Charges Date filled and read-only",
                    "Service entry: " + entry + (missingEntry.isEmpty() ? "" : " MISSING: " + missingEntry) + "\nBill controls: " + bc,
                    bcOk ? "PASS" : "FAIL");

            String gbMsg = cs.getBillWithoutNumber();
            step("[Bill Details] Get Bill without a Bill No.", "Leave Bill No. empty, click Get Bill",
                    "Warning \"Please enter bill no to fetch the details\"", gbMsg.isEmpty() ? "No message shown" : "Message: " + gbMsg,
                    gbMsg.toLowerCase().contains("please enter bill no") ? "PASS" : "FAIL");

            List<String> before = cs.gridFingerprint();
            String noName = cs.addServiceWithNoName();
            List<String> after = cs.gridFingerprint();
            step("[Bill Details] Add a service with no name", "Service Name empty -> Enter in Quantity",
                    "Warning \"Please Enter Atleast One Search Criteria !!!\"; no row added",
                    (noName.isEmpty() ? "No message shown" : "Message: " + noName) + " | rows " + before.size() + " -> " + after.size(),
                    noName.toLowerCase().contains("search criteria") && before.equals(after) ? "PASS" : "FAIL");

            Map<String, String> tot = cs.totals();
            step("[Bill Details] Total Amount = sum of charge rows", "Compare the Total Amount with the sum of the grid rows' Total Amount",
                    "Total Amount equals the row sum", "Totals " + tot, sameAmount(tot.get("TotalAmount"), tot.get("RowSum")) ? "PASS" : "FAIL");

            Map<String, String> h1 = cs.readPatientHeader();
            cs.readPlainTable("#HealthPlanDetails");
            cs.readPlainTable("#pkgItem");
            cs.showTab("#PatientServiceDetails");
            Map<String, String> h2 = cs.readPatientHeader();
            List<String> rowsAfterTabs = cs.gridFingerprint();
            boolean consistent = h1.get("MRNo").equals(h2.get("MRNo")) && h1.get("visit").equals(h2.get("visit"))
                    && h1.get("payor").equals(h2.get("payor")) && after.equals(rowsAfterTabs);
            step("[Bill Details] Details stay consistent across tabs", "Switch Bill Details -> Package Service -> Package Item -> Bill Details",
                    "MRN, OP Visit No., Payor and the charge rows are unchanged",
                    "Before: " + h1.get("MRNo") + " / " + h1.get("visit") + " / " + h1.get("payor") + " / " + after
                            + "\nAfter:  " + h2.get("MRNo") + " / " + h2.get("visit") + " / " + h2.get("payor") + " / " + rowsAfterTabs,
                    consistent ? "PASS" : "FAIL");

            // ========================== PACKAGE SERVICES ===========================
            Map<String, Object> psTab = cs.readPlainTable("#HealthPlanDetails");
            @SuppressWarnings("unchecked") List<String> psHeads = (List<String>) psTab.getOrDefault("heads", new ArrayList<String>());
            List<String> psMissing = missing(PACKAGE_SERVICE_COLUMNS, psHeads);
            step("[Package Services] Package Service tab", "Open the Package Service tab (data-target #HealthPlanDetails)",
                    "Tab opens; columns " + PACKAGE_SERVICE_COLUMNS, "Tab shown=" + psTab.get("tabShown") + " | columns: " + psHeads
                            + " | rows: " + psTab.get("rows") + (psMissing.isEmpty() ? "" : "\nMISSING: " + psMissing),
                    Boolean.TRUE.equals(psTab.get("tabShown")) && psMissing.isEmpty() ? "PASS" : "FAIL");
            cs.showTab("#PatientServiceDetails");

            List<String> beforePkg = cs.gridFingerprint();
            ChargesScreenSections.AddResult pk = cs.addFirstFlagged("ispackage", false, PKG_QUERIES);
            List<String> afterPkg = cs.gridFingerprint();
            step("[Package Services] A package (ispackage) is refused on OPD", "Search, pick the first result flagged ispackage=true, Enter in Quantity",
                    "Warning \"You cannot add a Package Service\"; no row added",
                    pk.code.isEmpty() ? "No search result flagged ispackage=true (" + pk.searched + ")"
                            : "Tried " + pk.code + " - " + pk.name + " | message: " + (pk.alert.isEmpty() ? "(none)" : pk.alert)
                              + " | rows " + beforePkg.size() + " -> " + afterPkg.size() + (pk.added.isEmpty() ? "" : " | ADDED: " + pk.added),
                    pk.code.isEmpty() ? "BLOCKED" : (pk.alert.toLowerCase().contains("cannot add a package") && beforePkg.equals(afterPkg) ? "PASS" : "FAIL"));

            int bdMark = cs.xhrMark();
            Map<String, String> totBefore = cs.totals();
            ChargesScreenSections.AddResult hp = cs.addFirstFlagged("ishealthplan", true, PKG_QUERIES);
            planCode = hp.code;
            Map<String, String> totAfter = cs.totals();
            double rowTotal = 0;
            boolean inGrid = false;
            for (Map<String, Object> r : cs.gridRows())
                if (hp.code.equalsIgnoreCase(String.valueOf(r.get("code"))) && Boolean.TRUE.equals(r.get("addedByTest"))) { inGrid = true; rowTotal = ((Number) r.get("total")).doubleValue(); }
            double delta = num(totAfter.get("TotalAmount")) - num(totBefore.get("TotalAmount"));
            boolean hpOk = inGrid && Math.abs(delta - rowTotal) < 0.01 && sameAmount(totAfter.get("TotalAmount"), totAfter.get("RowSum"));
            step("[Package Services] Search and add a health-plan package", "Pick the first result flagged ishealthplan=true with a price, Enter in Quantity (no Save)",
                    "Added as a new Bill Details row; Total Amount rises by its amount",
                    hp.code.isEmpty() ? "No search result flagged ishealthplan=true with a price (" + hp.searched + ")"
                            : "Added " + hp.code + " - " + hp.name + " (search rate " + hp.rate + ") | new rows " + hp.added
                              + (hp.alert.isEmpty() ? "" : " | message: " + hp.alert) + (hp.dialog.isEmpty() ? "" : " | dialog answered No: " + hp.dialog)
                              + "\nTotal " + totBefore.get("TotalAmount") + " -> " + totAfter.get("TotalAmount") + " (row " + String.format("%.2f", rowTotal) + ", row sum " + totAfter.get("RowSum") + ")",
                    hp.code.isEmpty() ? "BLOCKED" : (hpOk ? "PASS" : "FAIL"));

            Map<String, Object> bd = hp.code.isEmpty() || !inGrid ? null : cs.packageBreakdown(bdMark, 10000);
            if (bd != null) {
                @SuppressWarnings("unchecked") List<Map<String, Object>> lines = (List<Map<String, Object>>) bd.getOrDefault("lines", new ArrayList<>());
                int tableRows = count(bd.get("tableRows"));
                List<String> bad = new ArrayList<>();
                StringBuilder ls = new StringBuilder();
                for (Map<String, Object> l : lines) {
                    double q = ((Number) l.get("qty")).doubleValue(), rate = ((Number) l.get("rate")).doubleValue(), t = ((Number) l.get("total")).doubleValue();
                    double net = ((Number) l.get("net")).doubleValue(), disc = ((Number) l.get("discount")).doubleValue(), tax = ((Number) l.get("tax")).doubleValue();
                    ls.append("\n  ").append(l.get("code")).append(" ").append(l.get("name")).append(" | qty ").append(fmt(q)).append(" x ").append(fmt(rate))
                      .append(" = ").append(fmt(t)).append(" | net ").append(fmt(net));
                    if (Math.abs(q * rate - t) > 0.01) bad.add(l.get("code") + " total " + fmt(t) + " != qty x rate " + fmt(q * rate));
                    if (Math.abs(t - disc + tax - net) > 0.01) bad.add(l.get("code") + " net " + fmt(net) + " != total - discount + tax " + fmt(t - disc + tax));
                }
                String st = lines.isEmpty() ? "BLOCKED" : (tableRows == lines.size() && bad.isEmpty() ? "PASS" : "FAIL");
                step("[Package Services] Package break-up and amounts", "Package Service tab after adding " + hp.code,
                        "Every service line of the plan is listed; each line's Total = Qty x Unit Price and Net = Total - Discount + Tax",
                        (lines.isEmpty() ? "BLOCKED - the environment returned no service lines for " + hp.code + ". " : "")
                                + "Lookups: " + bd.get("lookups") + " | plan: " + bd.get("entries") + " | table rows: " + tableRows + " / lines: " + lines.size()
                                + ls + (bad.isEmpty() ? "" : "\nMISMATCH: " + bad),
                        st);
            } else {
                step("[Package Services] Package break-up and amounts", "Needs a health-plan package added first",
                        "Every service line of the plan is listed", "BLOCKED - no health-plan package could be added on this environment", "BLOCKED");
            }

            if (!hp.code.isEmpty() && inGrid) {
                Map<String, Object> dup = cs.addDuplicateAndAnswerNo(hp.code);
                String dlg = String.valueOf(dup.get("dialog"));
                boolean dupOk = dlg.toLowerCase().contains("already exists") && Boolean.TRUE.equals(dup.get("answeredNo"))
                        && count(dup.get("rowsBefore")) == count(dup.get("rowsAfter"));
                step("[Package Services] Duplicate selection is caught", "Add " + hp.code + " again -> confirm -> answer No",
                        "Confirm \"The selected Service/Package already exists. Do you want to proceed?\"; after No the row count is unchanged",
                        "Dialog: " + (dlg.isEmpty() ? "(none)" : "\"" + dlg + "\"") + " | answered No=" + dup.get("answeredNo")
                                + " | rows " + dup.get("rowsBefore") + " -> " + dup.get("rowsAfter") + (dup.containsKey("note") ? " | " + dup.get("note") : ""),
                        dupOk ? "PASS" : "FAIL");
            } else {
                step("[Package Services] Duplicate selection is caught", "Needs a health-plan package added first",
                        "Duplicate confirm appears", "BLOCKED - no health-plan package could be added on this environment", "BLOCKED");
            }

            // ============================ PACKAGE ITEM =============================
            Map<String, Object> pi = cs.readPlainTable("#pkgItem");
            @SuppressWarnings("unchecked") List<String> piHeads = (List<String>) pi.getOrDefault("heads", new ArrayList<String>());
            List<String> piMissing = missing(PACKAGE_ITEM_COLUMNS, piHeads);
            int piRows = count(pi.get("rows"));
            String piEmpty = String.valueOf(pi.getOrDefault("empty", ""));
            step("[Package Item] Package Item tab", "Open the Package Item tab (data-target #pkgItem)",
                    "Columns " + PACKAGE_ITEM_COLUMNS + "; rows, or the message \"No package items for this visit\"",
                    "Tab shown=" + pi.get("tabShown") + " | columns: " + piHeads + " | rows: " + piRows + (piEmpty.isEmpty() ? "" : " | message: \"" + piEmpty + "\"")
                            + (piMissing.isEmpty() ? "" : "\nMISSING: " + piMissing),
                    Boolean.TRUE.equals(pi.get("tabShown")) && piMissing.isEmpty() && (piRows > 0 || !piEmpty.isEmpty()) ? "PASS" : "FAIL");

            int itemLines = bd == null ? 0 : count(bd.get("itemLines"));
            if (itemLines > 0) {
                step("[Package Item] Items of the added package", "Package Item tab after adding " + planCode,
                        "Each item shows Item / Store / Batch / Available Qty / Qty / Total", "Item lines: " + itemLines + " | table rows: " + piRows + " | " + pi.get("sample"),
                        piRows == itemLines ? "PASS" : "FAIL");
                step("[Package Item] Quantity cannot exceed the package quantity", "Raise an item's Quantity above its configured quantity",
                        "Warning \"Quantity cannot be increased above the package quantity (N).\"",
                        "Not automated: package items exist here, so this can now be scripted (editing an item's Quantity in #pkgItem). Verify manually.", "MANUAL");
            } else {
                String why = planCode.isEmpty() ? "no health-plan package could be added on this environment"
                        : "the environment returned no package items for " + planCode + (bd == null ? "" : " (" + bd.get("lookups") + ")");
                step("[Package Item] Items of the added package (store / batch / quantity)", "Package Item tab after adding a package",
                        "Each item shows Item / Store / Batch / Available Qty / Qty / Total", "BLOCKED - " + why, "BLOCKED");
                step("[Package Item] Quantity cannot exceed the package quantity", "Raise an item's Quantity above its configured quantity",
                        "Warning \"Quantity cannot be increased above the package quantity (N).\"", "BLOCKED - " + why, "BLOCKED");
            }
            cs.showTab("#PatientServiceDetails");
        } finally {
            // ============================== CLEANUP ================================
            try {
                Map<String, Object> cl = cs.removeRowsAddedByTest();
                @SuppressWarnings("unchecked") List<String> remaining = (List<String>) cl.get("remaining");
                int bdNow = cs.breakdownEntries();
                Map<String, String> tot = cs.totals();
                boolean restored = (baseline == null || baseline.equals(remaining)) && (baselineBreakdown < 0 || bdNow == baselineBreakdown);
                step("[Cleanup] Remove the rows this test added", "Delete only rows the test added that are unsaved and unpaired (never posted rows)",
                        "Grid and Package Service tab back to what they held before the test" + (baseline == null ? "" : " " + baseline),
                        "Removed " + cl.get("removed") + " | now " + remaining + " | Package Service entries " + bdNow
                                + (baselineBreakdown < 0 ? "" : " (before: " + baselineBreakdown + ")") + " | Total " + tot.get("TotalAmount")
                                + (((List<?>) cl.get("refused")).isEmpty() ? "" : "\nNOT removed: " + cl.get("refused"))
                                + (baseline == null ? "\n(no baseline - the test stopped before one was taken)" : ""),
                        restored ? "PASS" : "FAIL");
                List<String> all = cs.networkSince(writeMark);
                List<String> writes = cs.writeCallsSince(writeMark);
                java.util.Set<String> reads = new java.util.TreeSet<>();
                for (String c : all) if (c.endsWith("-> READ")) reads.add(c.replaceFirst(" -> READ$", "").replaceFirst("\\?[^ ]*", ""));
                step("[Cleanup] No server writes", "Every request the page sent since the screen opened (recorded by Playwright at send time), "
                                + "classified by endpoint + ExecFlag: READ / WRITE / UNCLASSIFIED",
                        "Every request is a READ - no save, insert, update, delete or cancel, and nothing unrecognised",
                        writes.isEmpty() ? "No write calls: " + all.size() + " request(s), all READ. Distinct: " + reads
                                : writes.size() + " request(s) NOT confirmed as reads (WRITE = mutation endpoint/ExecFlag; UNCLASSIFIED = name not recognised, review it):\n"
                                  + String.join("\n", writes),
                        writes.isEmpty() ? "PASS" : "FAIL");
                addSummary("Health-plan package used", planCode.isEmpty() ? "(none available)" : planCode);
                addSummary("Rows removed in cleanup", String.valueOf(cl.get("removed")));
            } catch (Exception e) {
                step("[Cleanup] Remove the rows this test added", "Cleanup", "Grid back to its starting rows",
                        "Cleanup could not complete: " + e.getMessage() + " - the added rows are unsaved and disappear when the screen is left", "FAIL");
            } finally {
                cs.stopNetworkAudit();
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static List<String> missing(List<String> expected, List<String> actual) {
        List<String> out = new ArrayList<>();
        for (String e : expected) {
            String ne = e.replaceAll("\\s+", " ").trim().toLowerCase();
            if (actual.stream().noneMatch(a -> a.replaceAll("\\s+", " ").trim().toLowerCase().equals(ne))) out.add(e);
        }
        return out;
    }

    private static boolean sameAmount(String a, String b) {
        try { return Math.abs(Double.parseDouble(a.replace(",", "")) - Double.parseDouble(b.replace(",", ""))) < 0.01; }
        catch (Exception e) { return false; }
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return 0; }
    }

    private static int count(Object o) { return o instanceof Number ? ((Number) o).intValue() : (int) num(String.valueOf(o)); }

    private static String fmt(double d) { return String.format("%.2f", d); }
}
