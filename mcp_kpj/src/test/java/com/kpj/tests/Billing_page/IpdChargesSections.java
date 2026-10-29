package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Billing_page.IpdCharges;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>IPD Charges</b> — section coverage: <b>Patient Details</b>, <b>Bill Details</b>,
 * <b>Package Services</b>, <b>Package Item</b>.
 *
 * <p><b>Read-only by design.</b> This test never clicks Save ({@code IUDBill}), never submits a bill, and never
 * touches a charge row that existed before it ran (a posted row's Delete asks for a cancellation reason — a real
 * cancellation). The only rows it removes are rows it added itself, still unsaved ({@code chargeid == 0}); that
 * cleanup runs in {@code finally}, so it happens even when a step fails. The last step proves no write call was
 * made. (The saving end-to-end flow stays in {@link com.kpj.tests.Billing_page.IpdCharges}, unchanged.)</p>
 *
 * <p><b>BLOCKED</b> is reported — never PASS — when the environment cannot supply what a scenario needs, e.g. the
 * package break-up lookups ({@code GetPackageServices} / {@code GetPackageGroupServices}) return no lines (the case
 * on HIS Demo, 26/10/2026, for every package tried under the SELFPAY tariff / In-Patient billing class).</p>
 *
 * <p>Environment: {@code DEVHIS_URL} / {@code DEVHIS_USER} / {@code DEVHIS_PASS} (or {@code -Ddevhis.url=…}, or
 * config.properties) — resolved by {@link DevHisBase}. Report: {@code test-output/reports/Billing_IpdCharges_Sections.html}.</p>
 */
public class IpdChargesSections extends DevHisBase {

    public IpdChargesSections() { super("Billing_IpdCharges_Sections"); }

    public static void main(String[] args) {
        IpdChargesSections t = new IpdChargesSections();
        try { t.run(); } finally { t.stop(); }
    }

    /**
     * JUnit entry. {@link DevHisBase#run()} records step results in the HTML report but never throws, so on its own
     * the JUnit test always passed — even with a FAIL step such as "[Cleanup] No server writes". The FAIL steps are
     * collected (see {@link #step}) and asserted here, so surefire marks the test FAILED and lists them. BLOCKED
     * steps (environment data missing) do not fail the test. Note: this project's pom sets
     * {@code maven.test.failure.ignore=true}, so Maven still prints BUILD SUCCESS unless run with
     * {@code -Dmaven.test.failure.ignore=false}.
     */
    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
        if (!failedSteps.isEmpty())
            org.junit.jupiter.api.Assertions.fail(failedSteps.size() + " step(s) FAILED in " + testId + ":\n - "
                    + String.join("\n - ", failedSteps) + "\nReport: " + reportDir.resolve(testId + ".html").toAbsolutePath());
    }

    /** Names (and actual text) of every FAIL step — asserted by {@link #execute()}. */
    private final List<String> failedSteps = new ArrayList<>();

    private void track(String name, String actual, String status) {
        if ("FAIL".equalsIgnoreCase(status))
            failedSteps.add(name + " -> " + (actual == null ? "" : actual.replace('\n', ' ')));
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
            "Delete", "Cost Center", "Package", "Service Code", "Service Name", "Date");
    private static final List<String> PACKAGE_SERVICE_COLUMNS = Arrays.asList(
            "Service Code", "Service Name", "Date", "Qty / Day's", "Unit Purchase Price", "Total Amount", "Discount%",
            "Discount Amount", "Service Tax Amount", "Net Amount", "Self Amount", "Non-Self Amount", "Doctor Share",
            "Hospital Share", "Doctor", "Remark");          // "Cost Centre" is conditional (IsEnableCostcenter)
    private static final List<String> PACKAGE_ITEM_COLUMNS = Arrays.asList(
            "Item Code", "Item Name", "Store Name", "Batch Code", "Available Quantity", "Quantity", "Total Amount", "Remark");
    private static final List<String> SERVICE_ENTRY_FIELDS = Arrays.asList(
            "ServiceCode", "ServiceName", "CPTCode", "quantity", "rate");
    /** Search terms tried, in order, to find a service the server flags ispackage=true. Not test data — the
     *  package used is whichever one the environment returns. */
    private static final String[] PACKAGE_QUERIES = { "PKG", "Package", "Pkg" };

    @Override
    protected void body() {
        try { sections(); }
        catch (RuntimeException | Error e) { failedSteps.add("ERROR (unexpected exception) -> " + e); throw e; }
    }

    private void sections() {
        meta("Billing - IPD Charges (Sections)", "Billing > IPD Charges",
                "Patient Details, Bill Details, Package Services and Package Item on the IPD Charges screen. Read-only: "
              + "never clicks Save, never touches posted charges; rows the test adds are removed in cleanup. "
              + "BLOCKED = the environment has no data for that scenario (not a pass).");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login (DEVHIS_USER / DEVHIS_PASS)", "Authenticated", "Logged in to " + BASE, "PASS");

        IpdCharges ipc = new IpdCharges(page);
        ipc.startNetworkAudit();   // records every request the page sends, from here on, at send time
        List<String> baseline = null;
        int writeMark = 0;
        String pkgCode = "";
        try {
            // =========================== PATIENT DETAILS ===========================
            boolean onScreen = ipc.navigateTo(BASE);
            Map<String, Object> ctl = ipc.patientDetailsControls();
            boolean ctlOk = Boolean.TRUE.equals(ctl.get("mrnInput")) && Boolean.TRUE.equals(ctl.get("mrnSearchButton"))
                    && Boolean.TRUE.equals(ctl.get("pickerButton"));
            step("[Patient Details] Open IPD Charges", "Billing -> IPD Charges (#/IPDCharges)",
                    "Screen opens with the MRN box, its Search button and the patient picker",
                    (onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")") + "\nControls: " + ctl,
                    onScreen && ctlOk ? "PASS" : "FAIL");
            if (!onScreen) { addSummary("Result", "Screen not reached"); return; }
            ipc.installCaptureHooks();
            writeMark = ipc.networkMark();

            Map<String, Object> tabs = ipc.chargeTabs();
            boolean tabsOk = tabs.values().stream().noneMatch(v -> String.valueOf(v).contains("(missing)") || String.valueOf(v).contains("(hidden)"));
            step("[Patient Details] All four sections load", "Check the Bill Details / Package Service / Package Item / Cancelled Charges tabs",
                    "All four tabs present and visible", "Tabs (data-target -> label): " + tabs, tabsOk ? "PASS" : "FAIL");

            String emptyMsg = ipc.searchByMrn("");
            step("[Patient Details] Search with MRN empty", "Clear MRN, click Search (SearchPatientByMRNo)",
                    "Warning \"Please Enter MRN No.\"", emptyMsg.isEmpty() ? "No message shown" : "Message: " + emptyMsg,
                    emptyMsg.toLowerCase().contains("please enter mrn") ? "PASS" : "FAIL");

            String badMrn = "ZZ" + (System.currentTimeMillis() % 1000000);
            String badMsg = ipc.searchByMrn(badMrn);
            step("[Patient Details] Search with an unknown MRN", "MRN \"" + badMrn + "\" (generated, cannot exist) -> Search",
                    "Warning \"Patient Not Found\"", badMsg.isEmpty() ? "No message shown" : "Message: " + badMsg,
                    badMsg.toLowerCase().contains("patient not found") ? "PASS" : "FAIL");

            String ipd = ipc.selectIpdPatient();
            Map<String, String> hdr = ipc.readPatientHeader();
            @SuppressWarnings("unchecked") Map<String, Object> ro = (Map<String, Object>) ipc.patientDetailsControls().get("readOnly");
            List<String> emptyFields = new ArrayList<>();
            for (String k : Arrays.asList("IPDNo", "visitadmissiondate", "ward", "bed", "department", "doctorname"))
                if (hdr.getOrDefault(k, "").isEmpty()) emptyFields.add(k);
            List<String> editable = new ArrayList<>();
            if (ro != null) ro.forEach((k, v) -> { if (!"read-only".equals(String.valueOf(v))) editable.add(k + "=" + v); });
            boolean pickOk = !ipd.isEmpty() && emptyFields.isEmpty() && editable.isEmpty();
            step("[Patient Details] Select an admitted patient from the picker",
                    "Picker -> IPD filter -> Search -> select the first row that loads an admission",
                    "IPD No., Admission Date, Ward, Bed, Department, Doctor filled and read-only",
                    ipd.isEmpty() ? "No IPD patient could be loaded from the picker"
                            : "Loaded " + hdr + (emptyFields.isEmpty() ? "" : "\nEMPTY: " + emptyFields) + (editable.isEmpty() ? "" : "\nNOT read-only: " + editable),
                    pickOk ? "PASS" : (ipd.isEmpty() ? "BLOCKED" : "FAIL"));
            if (ipd.isEmpty()) { addSummary("Result", "No admitted IPD patient available — remaining sections not run"); return; }

            String mrn = hdr.getOrDefault("MRNo", "");
            String reMsg = ipc.searchByMrn(mrn);
            Map<String, String> hdr2 = ipc.readPatientHeader();
            boolean sameAdm = ipd.equals(hdr2.getOrDefault("IPDNo", ""));
            step("[Patient Details] Search directly by MRN", "Type the selected patient's MRN " + mrn + " -> Search",
                    "The same admission (" + ipd + ") loads", "Loaded IPD No. " + hdr2.getOrDefault("IPDNo", "(none)")
                            + (reMsg.isEmpty() ? "" : " | message: " + reMsg), sameAdm ? "PASS" : "FAIL");

            baseline = ipc.gridFingerprint();
            addSummary("Patient", mrn + " / " + hdr2.getOrDefault("IPDNo", ipd) + " (" + hdr2.getOrDefault("ward", "") + ")");
            addSummary("Charge rows before the test", baseline.size() + " " + baseline);

            // ============================ BILL DETAILS =============================
            List<String> cols = ipc.billGridColumns();
            List<String> missingCols = missing(BILL_GRID_CORE_COLUMNS, cols);
            step("[Bill Details] Charge grid columns", "Read the Bill Details grid's column definitions",
                    "Includes " + BILL_GRID_CORE_COLUMNS, cols.size() + " columns: " + cols + (missingCols.isEmpty() ? "" : "\nMISSING: " + missingCols),
                    missingCols.isEmpty() ? "PASS" : "FAIL");

            List<String> entry = ipc.serviceEntryFields();
            List<String> missingEntry = new ArrayList<>();
            for (String f : SERVICE_ENTRY_FIELDS) if (entry.stream().noneMatch(e -> e.startsWith(f))) missingEntry.add(f);
            String chargeDate = hdr2.getOrDefault("ChargeDate", "");
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String todayMy = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            boolean dateOk = chargeDate.equals(today) || chargeDate.equals(todayMy);
            step("[Bill Details] Service entry row and Charges Date", "Check the visible service-entry inputs and the Charges Date default",
                    "Service Code / Service Name / CPT Code / Quantity / Unit Price present; Charges Date = today",
                    "Inputs: " + entry + (missingEntry.isEmpty() ? "" : "\nMISSING: " + missingEntry) + "\nCharges Date: " + chargeDate + " (today " + today + ")",
                    missingEntry.isEmpty() && dateOk ? "PASS" : "FAIL");

            String gbMsg = ipc.getBillWithoutNumber();
            step("[Bill Details] Get Bill without a Bill No.", "Leave Bill No. empty, click Get Bill",
                    "Warning \"Please enter bill no to fetch the details\"", gbMsg.isEmpty() ? "No message shown" : "Message: " + gbMsg,
                    gbMsg.toLowerCase().contains("please enter bill no") ? "PASS" : "FAIL");

            List<String> before = ipc.gridFingerprint();
            String noNameMsg = ipc.addServiceWithNoName();
            List<String> after = ipc.gridFingerprint();
            step("[Bill Details] Add a service with no name", "Service Name empty -> Enter in Quantity",
                    "Warning \"Please Enter Atleast One Search Criteria !!!\"; no row added",
                    (noNameMsg.isEmpty() ? "No message shown" : "Message: " + noNameMsg) + " | rows " + before.size() + " -> " + after.size(),
                    noNameMsg.toLowerCase().contains("search criteria") && before.equals(after) ? "PASS" : "FAIL");

            Map<String, String> tot = ipc.totals();
            step("[Bill Details] Total Amount = sum of charge rows", "Compare the Total Amount footer with the sum of the grid's Total Amount column",
                    "Total Amount equals the row sum", "Footer " + tot, sameAmount(tot.get("TotalAmount"), tot.get("RowSum")) ? "PASS" : "FAIL");

            // ========================== PACKAGE SERVICES ===========================
            Map<String, Object> psTab = ipc.packageServiceTable();
            @SuppressWarnings("unchecked") List<String> psHeads = (List<String>) psTab.getOrDefault("heads", new ArrayList<String>());
            List<String> psMissing = missing(PACKAGE_SERVICE_COLUMNS, psHeads);
            step("[Package Services] Package Service tab", "Open the Package Service tab (data-target #pkg) and read its table",
                    "Tab opens; columns " + PACKAGE_SERVICE_COLUMNS, "Tab shown=" + psTab.get("tabShown") + " | columns: " + psHeads
                            + " | rows: " + psTab.get("rows") + (psMissing.isEmpty() ? "" : "\nMISSING: " + psMissing),
                    Boolean.TRUE.equals(psTab.get("tabShown")) && psMissing.isEmpty() ? "PASS" : "FAIL");
            ipc.switchToBillDetailsTab();

            int pkgMark = ipc.xhrMark();
            Map<String, String> totBefore = ipc.totals();
            IpdCharges.PackageAdd pa = ipc.addFirstPackage(PACKAGE_QUERIES);
            pkgCode = pa.code;
            boolean inGrid = false;
            for (Map<String, Object> r : ipc.gridRows())
                if (pa.code.equalsIgnoreCase(String.valueOf(r.get("code"))) && Boolean.TRUE.equals(r.get("ispackage")) && Boolean.TRUE.equals(r.get("addedByTest"))) inGrid = true;
            Map<String, String> totAfter = ipc.totals();
            String paActual = pa.code.isEmpty()
                    ? "No search result flagged ispackage=true (" + pa.searched + ")"
                    : "Added " + pa.code + " - " + pa.name + " (search rate " + pa.rate + ", query '" + pa.query + "') | new rows " + pa.added
                      + (pa.dialog.isEmpty() ? "" : " | dialog answered No: \"" + pa.dialog + "\"")
                      + "\nTotals before " + totBefore.get("TotalAmount") + " -> after " + totAfter.get("TotalAmount") + " (row sum " + totAfter.get("RowSum") + ")";
            step("[Package Services] Search and add a package", "Type in Service Name, pick the first result flagged ispackage=true, Enter in Quantity (no Save)",
                    "The package appears as a new Bill Details row", paActual,
                    pa.code.isEmpty() ? "BLOCKED" : (inGrid ? "PASS" : "FAIL"));

            Map<String, Object> bd = pa.code.isEmpty() ? null : ipc.packageBreakdown(pkgMark, 10000);
            Map<String, Object> psAfter = pa.code.isEmpty() ? null : ipc.packageServiceTable();
            if (bd != null) {
                int lines = num(bd.get("serviceLines"));
                int tableRows = num(psAfter.get("rows"));
                String st = lines == 0 ? "BLOCKED" : (tableRows > 0 ? "PASS" : "FAIL");
                step("[Package Services] Package break-up shown in Package Service tab", "After adding " + pa.code + ", open the Package Service tab",
                        "The package's service lines are listed",
                        (lines == 0 ? "BLOCKED - the environment returned no package lines for " + pa.code + ". " : "")
                                + "Lookups: " + bd.get("lookups") + " | PackageList: " + bd.get("packages") + " | table rows: " + tableRows
                                + (tableRows > 0 ? " | sample: " + psAfter.get("sample") : ""),
                        st);
                ipc.switchToBillDetailsTab();
            } else {
                step("[Package Services] Package break-up shown in Package Service tab", "Needs a package added first",
                        "The package's service lines are listed", "BLOCKED - no package could be added on this environment", "BLOCKED");
            }

            if (!pa.code.isEmpty()) {
                Map<String, Object> dup = ipc.addDuplicateAndAnswerNo(pa.code);
                String dlg = String.valueOf(dup.get("dialog"));
                boolean dupOk = dlg.toLowerCase().contains("already exists") && Boolean.TRUE.equals(dup.get("answeredNo"))
                        && num(dup.get("rowsBefore")) == num(dup.get("rowsAfter"));
                step("[Package Services] Duplicate package is caught", "Add " + pa.code + " again -> confirm -> answer No",
                        "Confirm \"The selected Service/Package already exists. Do you want to proceed?\"; after No the row count is unchanged",
                        "Dialog: " + (dlg.isEmpty() ? "(none)" : "\"" + dlg + "\"") + " | answered No=" + dup.get("answeredNo")
                                + " | rows " + dup.get("rowsBefore") + " -> " + dup.get("rowsAfter") + (dup.containsKey("note") ? " | " + dup.get("note") : ""),
                        dupOk ? "PASS" : "FAIL");
            } else {
                step("[Package Services] Duplicate package is caught", "Needs a package added first",
                        "Duplicate confirm appears", "BLOCKED - no package could be added on this environment", "BLOCKED");
            }

            // ============================ PACKAGE ITEM =============================
            Map<String, Object> piTab = ipc.packageItemTable();
            @SuppressWarnings("unchecked") List<String> piHeads = (List<String>) piTab.getOrDefault("heads", new ArrayList<String>());
            List<String> piMissing = missing(PACKAGE_ITEM_COLUMNS, piHeads);
            int piRows = num(piTab.get("rows"));
            String piEmpty = String.valueOf(piTab.getOrDefault("empty", ""));
            boolean piOk = Boolean.TRUE.equals(piTab.get("tabShown")) && piMissing.isEmpty() && (piRows > 0 || !piEmpty.isEmpty());
            step("[Package Item] Package Item tab", "Open the Package Item tab (data-target #pkgItem) and read its table",
                    "Columns " + PACKAGE_ITEM_COLUMNS + "; rows, or the message \"No package items for this visit\"",
                    "Tab shown=" + piTab.get("tabShown") + " | columns: " + piHeads + " | rows: " + piRows
                            + (piEmpty.isEmpty() ? "" : " | message: \"" + piEmpty + "\"") + (piMissing.isEmpty() ? "" : "\nMISSING: " + piMissing),
                    piOk ? "PASS" : "FAIL");

            int itemLines = bd == null ? 0 : num(bd.get("itemLines"));
            if (itemLines > 0) {
                step("[Package Item] Items of the added package", "Package Item tab after adding " + pkgCode,
                        "Each item shows Item / Store / Batch / Available Qty / Qty", "Item lines: " + itemLines + " | table rows: " + piRows
                                + " | sample: " + piTab.get("sample"), piRows > 0 ? "PASS" : "FAIL");
                step("[Package Item] Quantity cannot exceed the package quantity", "Raise an item's Quantity above its configured quantity",
                        "Warning \"Quantity cannot be increased above the package quantity (N).\"",
                        "Not automated yet: package items exist on this environment, so this check can now be scripted (editing an item's "
                                + "Quantity input in #pkgItem). Verify manually for now.", "MANUAL");
            } else {
                String why = pkgCode.isEmpty() ? "no package could be added on this environment"
                        : "the environment returned no package items for " + pkgCode + (bd == null ? "" : " (" + bd.get("lookups") + ")");
                step("[Package Item] Items of the added package (store / batch / quantity)", "Package Item tab after adding a package",
                        "Each item shows Item / Store / Batch / Available Qty / Qty", "BLOCKED - " + why, "BLOCKED");
                step("[Package Item] Quantity cannot exceed the package quantity", "Raise an item's Quantity above its configured quantity",
                        "Warning \"Quantity cannot be increased above the package quantity (N).\"", "BLOCKED - " + why, "BLOCKED");
            }
            ipc.switchToBillDetailsTab();
        } finally {
            // ============================== CLEANUP ================================
            // Always runs — even after a failed step or an exception — and only ever removes rows this test added
            // that are still unsaved. A row that existed before the test is never touched.
            try {
                Map<String, Object> cl = ipc.removeRowsAddedByTest();
                @SuppressWarnings("unchecked") List<String> remaining = (List<String>) cl.get("remaining");
                boolean restored = baseline == null || baseline.equals(remaining);
                Map<String, String> tot = ipc.totals();
                step("[Cleanup] Remove the rows this test added", "Delete only rows tagged as added by this test with chargeid 0 (never posted rows)",
                        "Grid back to the rows it had before the test" + (baseline == null ? "" : " " + baseline),
                        "Removed " + cl.get("removed") + " | now " + remaining + " | Total " + tot.get("TotalAmount")
                                + (((List<?>) cl.get("refusedPosted")).isEmpty() ? "" : "\nNOT removed (already posted): " + cl.get("refusedPosted"))
                                + (baseline == null ? "\n(no baseline - the test stopped before one was taken)" : ""),
                        restored ? "PASS" : "FAIL");
                List<String> all = ipc.networkSince(writeMark);
                List<String> writes = ipc.writeCallsSince(writeMark);
                java.util.Set<String> readEndpoints = new java.util.TreeSet<>();
                for (String c : all) if (c.endsWith("-> READ")) readEndpoints.add(c.replaceFirst(" -> READ$", "").replaceFirst("\\?[^ ]*", ""));
                step("[Cleanup] No server writes", "Every request the page sent since the screen opened (recorded by Playwright at send time), "
                                + "classified by endpoint + ExecFlag: READ / WRITE / UNCLASSIFIED",
                        "Every request is a READ - no save, insert, update, delete or cancel, and nothing unrecognised",
                        writes.isEmpty()
                                ? "No write calls: " + all.size() + " request(s), all READ. Distinct: " + readEndpoints
                                : writes.size() + " request(s) NOT confirmed as reads (WRITE = mutation endpoint/ExecFlag; "
                                  + "UNCLASSIFIED = name not recognised, review it):\n" + String.join("\n", writes),
                        writes.isEmpty() ? "PASS" : "FAIL");
                addSummary("Package used", pkgCode.isEmpty() ? "(none available)" : pkgCode);
                addSummary("Rows removed in cleanup", String.valueOf(cl.get("removed")));
            } catch (Exception e) {
                step("[Cleanup] Remove the rows this test added", "Cleanup", "Grid back to its starting rows",
                        "Cleanup could not complete: " + e.getMessage() + " — the added rows are unsaved and disappear when the screen is left", "FAIL");
            } finally {
                ipc.stopNetworkAudit();
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    /** Expected entries not found in {@code actual} (case/space-insensitive exact match). */
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

    private static int num(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
