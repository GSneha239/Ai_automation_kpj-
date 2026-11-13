package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; POS &gt; <b>CDR Dispense Process</b> — READ-ONLY automated checks (CDR-01 … CDR-22).
 *
 * <p>Built from the manual execution of 13/11/2026 (POS_CDRDispenseProcess_Manual_Report.html). Nothing is screened,
 * packed, checked, dispensed, collected or acknowledged: the only row element ever clicked is the selection checkbox,
 * write calls are aborted in the browser, and the dispense workflow is reported SKIPPED. Known defects (date range not
 * applied, search box not filtering, duplicate list request, Reg. Type switching, hidden columns, debug log flood) are
 * expected to FAIL.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.CDRDispenseProcess" -Ddevhis.postrun=off
 *   report: test-output/reports/POS_CDRDispenseProcess.html
 * </pre>
 */
public class CDRDispenseProcess extends DevHisBase {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TODAY = LocalDate.now().format(DMY);
    private static final String YEAR_START = "01/01/" + LocalDate.now().getYear();
    private static final String TWO_WEEKS_AGO = LocalDate.now().minusDays(14).format(DMY);
    private int pass, fail, skipped, blocked;

    public CDRDispenseProcess() { super("POS_CDRDispenseProcess"); }

    public static void main(String[] args) {
        CDRDispenseProcess t = new CDRDispenseProcess();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private interface Check { String[] run() throws Exception; }

    private void tc(String id, String name, String desc, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "FAILURE REASON - exception: " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, id + " " + name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] r(boolean ok, String actual) { return new String[]{ok ? "PASS" : "FAIL", actual}; }

    /** Fresh screen searched with {@code progress} over [from, today], Reg. Type ALL. Returns {status, list calls}. */
    private int[] searched(com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess cdr, String progress, String from) {
        cdr.reopen();
        cdr.clickRegType("2");
        cdr.pickOption(com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess.PROGRESS, progress);
        cdr.setDates(from, TODAY);
        return cdr.search();
    }

    @Override
    protected void body() {
        meta("Inventory - POS - CDR Dispense Process (read-only)",
                "Inventory > POS > CDR Dispense Process",
                "Read-only checks derived from the 13/11/2026 manual execution. Only the row selection checkbox is ever clicked; "
                        + "write calls are blocked in the browser; the dispense workflow is SKIPPED.");

        com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess cdr = new com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess(page);
        cdr.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("CDR-01", "Page navigation", "Inventory > POS > CDR Dispense Process (sidebar)",
                "#/CDRDispenseProcess opens with its Search button",
                () -> { boolean ok = cdr.open(); return r(ok, ok ? "Opened " + page.url() : "FAILURE REASON - screen did not open; at " + page.url()); });

        tc("CDR-02", "Page title and layout", "Read header and sections", "Header contains 'CDR DISPENSE PROCESS'; prescription and drug lists present",
                () -> {
                    cdr.open();
                    assertThat(page.locator(".page_title").first()).containsText("CDR DISPENSE PROCESS",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    boolean sections = Boolean.TRUE.equals(page.evaluate("() => /List Of Prescription/.test(document.body.innerText) && /List Of Prescribed Drugs/.test(document.body.innerText)"));
                    return r(sections, "Header '" + cdr.headerText() + "', tab '" + page.title() + "', sections present=" + sections);
                });

        tc("CDR-03", "Filters and dropdowns", "Inspect date/time, MRN, Progress, Prescription Type, Reg. Type, Doctor, Dispensing Store",
                "All present; lists populated; dates default to today",
                () -> {
                    cdr.open();
                    int prog = cdr.optionCount(com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess.PROGRESS);
                    int doc = cdr.optionCount("MedicationOrder.DoctorID"), type = cdr.optionCount("MedicationOrder.PrescriptionTypeID");
                    String from = cdr.fieldValue("#txtFromDate"), store = cdr.selectedText("MedicationOrder.DispensingStore");
                    boolean ok = prog >= 6 && doc > 1 && type > 1 && TODAY.equals(from) && cdr.isVisible("#txtMRNo");
                    return r(ok, "Progress options=" + prog + ", Doctor=" + doc + ", Prescription Type=" + type + ", From=" + from + ", Dispensing Store='" + store + "'");
                });

        tc("CDR-04", "Search validation (Progress)", "Click Search with Progress = --Select--", "'Please Select Progress'; no list request",
                () -> {
                    cdr.reopen();
                    cdr.clickSearchOnly();
                    List<String> a = cdr.waitForAlert(3000);
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("progress")), "Messages: " + a);
                });

        tc("CDR-05", "Prescription data loads", "Progress Dispensing, " + YEAR_START + " - today, Reg. Type ALL, Search",
                "List API 200 and prescriptions listed",
                () -> {
                    int[] s = searched(cdr, "Dispensing", YEAR_START);
                    int n = cdr.listTotal();
                    if (s[0] == 200 && n == 0) return new String[]{"BLOCKED", "API 200 but no prescriptions in Dispensing since " + YEAR_START};
                    return r(s[0] == 200 && n > 0, "API " + s[0] + "; " + cdr.listInfo());
                });

        tc("CDR-06", "One list request per Search", "Count GetPrescriptionList responses for a single Search click (Screening)",
                "Exactly 1 request",
                () -> {
                    int[] s = searched(cdr, "Screening", YEAR_START);
                    return r(s[1] == 1, "GetPrescriptionList responses for one click: " + s[1] + " (HTTP " + s[0] + ")");
                });

        tc("CDR-07", "Date filter applied", "Screening, From " + TWO_WEEKS_AGO + " To today; check every PrescriptionDate",
                "All prescriptions fall in the range",
                () -> {
                    int[] s = searched(cdr, "Screening", TWO_WEEKS_AGO);
                    List<String> d = cdr.prescriptionDates();
                    LocalDate from = LocalDate.parse(TWO_WEEKS_AGO, DMY);
                    long outside = d.stream().filter(x -> x.length() >= 10).filter(x -> {
                        try { return LocalDate.parse(x.substring(0, 10), DMY).isBefore(from); } catch (Exception e) { return false; }
                    }).count();
                    if (d.isEmpty()) return new String[]{"BLOCKED", "No prescriptions returned (HTTP " + s[0] + ")"};
                    return r(outside == 0, d.size() + " prescriptions; " + outside + " dated before " + TWO_WEEKS_AGO + " (request " + cdr.lastListRequest + ")");
                });

        tc("CDR-08", "Reg. Type stays as chosen", "Reg. Type ALL, then Progress = Collection",
                "Reg. Type remains ALL (2)",
                () -> {
                    cdr.reopen();
                    cdr.clickRegType("2");
                    cdr.pickOption(com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess.PROGRESS, "Collection");
                    String v = cdr.regType();
                    return r("2".equals(v), "Reg. Type after choosing Collection = " + v + ("1".equals(v) ? " (switched to IPD silently)" : ""));
                });

        tc("CDR-09", "Table data and displayed columns", "Dispensing search; read the visible prescription headers",
                "Key columns visible: Prescription No, MRN No., Patient Name, Prescription Date",
                () -> {
                    searched(cdr, "Dispensing", YEAR_START);
                    List<String> h = cdr.visiblePrescriptionHeaders();
                    boolean ok = h.contains("Prescription No") && h.contains("MRN No.") && h.contains("Patient Name");
                    return r(ok, "Visible headers: " + h + (ok ? "" : " - FAILURE REASON: key columns hidden by the user's saved column settings"));
                });

        tc("CDR-10", "Pagination and page size", "Screening " + YEAR_START + " - today; page size 50; page 2",
                "Pager text follows (1-50, then 51-100)",
                () -> {
                    searched(cdr, "Screening", YEAR_START);
                    int total = cdr.listTotal();
                    if (total <= 50) return new String[]{"BLOCKED", "Only " + total + " prescriptions - not enough for 2 pages of 50"};
                    cdr.setPageSize("50");
                    String p1 = cdr.listInfo();
                    boolean moved = cdr.goToPage("2");
                    String p2 = cdr.listInfo();
                    return r(p1.matches(".*\\b1\\D+50\\b.*") && moved && p2.matches(".*\\b51\\D+100\\b.*"), "'" + p1 + "' -> '" + p2 + "'");
                });

        tc("CDR-11", "Search / filter box", "Type a text that matches no prescription into 'Search prescriptions'",
                "The list count drops (to 0 for no match)",
                () -> {
                    searched(cdr, "Dispensing", YEAR_START);
                    int before = cdr.listTotal();
                    if (before <= 0) return new String[]{"BLOCKED", "No prescriptions to filter"};
                    cdr.typeListSearch("ZZZNOMATCH");
                    int after = cdr.listTotal();
                    cdr.typeListSearch("");
                    return r(after == 0 || (after >= 0 && after < before), "Total " + before + " -> " + after + " after typing 'ZZZNOMATCH'");
                });

        tc("CDR-12", "Sorting", "Prescription Date header shows a sort indicator", "(not exercised)",
                () -> new String[]{"SKIPPED", "Sort order not asserted in this read-only pass"});

        tc("CDR-13", "View details: select a prescription", "Screening from " + TWO_WEEKS_AGO + "; tick the first prescription's checkbox",
                "Patient banner and the prescription's drugs load",
                () -> {
                    searched(cdr, "Screening", TWO_WEEKS_AGO);
                    if (cdr.listTotal() <= 0) return new String[]{"BLOCKED", "No prescriptions in Screening"};
                    cdr.selectRow(0);
                    String box = cdr.visibleMessageBox();
                    if (box.toLowerCase().contains("expired")) { cdr.closeMessageBox(); return new String[]{"BLOCKED", "First prescription is expired: " + box}; }
                    boolean banner = cdr.patientBannerShown();
                    int drugs = cdr.drugCount();
                    String toast = String.valueOf(cdr.alerts());
                    cdr.unselectRows();
                    return r(banner && drugs > 0, "Banner=" + banner + ", drugs=" + drugs + " (" + cdr.firstDrug() + ") | messages " + toast);
                });

        tc("CDR-14", "Expired prescription validation", "Dispensing " + YEAR_START + " - today; tick the oldest prescription on the page",
                "Message 'This prescription has expired ... Not allowed to dispense'",
                () -> {
                    searched(cdr, "Dispensing", YEAR_START);
                    int i = cdr.oldestRowIndex();
                    if (i < 0) return new String[]{"BLOCKED", "No prescriptions listed"};
                    cdr.selectRow(i);
                    String box = cdr.visibleMessageBox();
                    cdr.closeMessageBox();
                    cdr.unselectRows();
                    if (!box.toLowerCase().contains("expired")) return new String[]{"BLOCKED", "Oldest prescription on the page is not expired - message: '" + box + "'"};
                    return r(true, "Message: " + box);
                });

        tc("CDR-15", "Print / Dispense List before dispensing", "Select a Screening prescription, click Dispense List and Print",
                "Both answer 'Dispense has not been done against this Prescription'; no report opens",
                () -> {
                    searched(cdr, "Screening", TWO_WEEKS_AGO);
                    if (cdr.listTotal() <= 0) return new String[]{"BLOCKED", "No prescriptions in Screening"};
                    cdr.selectRow(0);
                    cdr.closeMessageBox();
                    cdr.click("PrintDispenseList();");
                    List<String> a1 = cdr.alerts();
                    cdr.click("printReport()");
                    List<String> a2 = cdr.alerts();
                    List<String> opened = cdr.openedUrls();
                    cdr.unselectRows();
                    boolean ok = String.valueOf(a1).contains("Dispense has not been done") && String.valueOf(a2).contains("Dispense has not been done") && opened.isEmpty();
                    return r(ok, "Dispense List: " + a1 + " | Print: " + a2 + " | report URLs " + opened);
                });

        tc("CDR-16", "Drug Label validation", "Prescription selected, click Drug Label with no language", "'Please select a language for the drug label'",
                () -> {
                    searched(cdr, "Screening", TWO_WEEKS_AGO);
                    if (cdr.listTotal() <= 0) return new String[]{"BLOCKED", "No prescriptions in Screening"};
                    cdr.selectRow(0);
                    cdr.closeMessageBox();
                    cdr.click("PrintDrugLabelAll();");
                    List<String> a = cdr.alerts();
                    cdr.unselectRows();
                    return r(String.valueOf(a).toLowerCase().contains("language"), "Messages: " + a);
                });

        tc("CDR-17", "Medication Summary", "Prescription selected; Medication Summary button", "Disabled when nothing is flagged, or a message / report",
                () -> {
                    searched(cdr, "Screening", TWO_WEEKS_AGO);
                    if (cdr.listTotal() <= 0) return new String[]{"BLOCKED", "No prescriptions in Screening"};
                    cdr.selectRow(0);
                    cdr.closeMessageBox();
                    boolean disabled = cdr.buttonDisabled("fnMedicationSummary()");
                    String result;
                    if (disabled) result = "Button disabled (no medicines flagged for Medication Summary)";
                    else { cdr.click("fnMedicationSummary()"); result = "Messages " + cdr.alerts() + ", report URLs " + cdr.openedUrls(); }
                    cdr.unselectRows();
                    return r(true, result);
                });

        tc("CDR-18", "Company Approved Amount popup (view)", "Prescription selected; click Company Approved Amount; close",
                "Read-only popup opens and closes",
                () -> {
                    searched(cdr, "Screening", TWO_WEEKS_AGO);
                    if (cdr.listTotal() <= 0) return new String[]{"BLOCKED", "No prescriptions in Screening"};
                    cdr.selectRow(0);
                    cdr.closeMessageBox();
                    cdr.click("OpenCompanyApprovedAmount()");
                    int m = cdr.openModals();
                    cdr.dismissModals();
                    cdr.unselectRows();
                    return r(m > 0, "Popups opened=" + m + ", open after close=" + cdr.openModals());
                });

        tc("CDR-19", "Clear", "After a search, click Clear", "Dates back to today, Progress cleared, list empty",
                () -> {
                    searched(cdr, "Dispensing", YEAR_START);
                    cdr.click("ClearFilter();");
                    String from = cdr.fieldValue("#txtFromDate"), prog = cdr.selectedText(com.kpj.pages.Inventory_page.POS_page.CDRDispenseProcess.PROGRESS);
                    int total = cdr.listTotal();
                    return r(TODAY.equals(from) && prog.startsWith("--") && total == 0, "From=" + from + ", Progress='" + prog + "', " + cdr.listInfo());
                });

        tc("CDR-20", "Patient lookup by MRN", "MRN blank, then unknown MRN ZZ999999", "Blank -> 'Please Enter MRN'; unknown -> a not-found message",
                () -> {
                    cdr.reopen();
                    cdr.setNgInput("MedicationOrder.MRNO", "");
                    cdr.click("SearchPatientByMRNo();");
                    List<String> blank = cdr.alerts();
                    cdr.setNgInput("MedicationOrder.MRNO", "ZZ999999");
                    cdr.click("SearchPatientByMRNo();");
                    List<String> unknown = cdr.waitForAlert(2500);
                    cdr.setNgInput("MedicationOrder.MRNO", "");
                    return r(String.valueOf(blank).toLowerCase().contains("mrn") && !unknown.isEmpty(),
                            "Blank: " + blank + " | unknown: " + (unknown.isEmpty() ? "(no message)" : unknown));
                });

        tc("CDR-21", "Dispense workflow", "Screen / prepare / pack / verify / check / dispense / collect / acknowledge",
                "(write operations - not executed)",
                () -> new String[]{"SKIPPED", "Read-only run: no workflow step is clicked. Covered as MANUAL."});

        tc("CDR-22", "API requests and console errors", "Everything logged during this run",
                "No HTTP >= 400, no JS errors, no write call, no debug-log flood",
                () -> r(cdr.apiFailures.isEmpty() && cdr.consoleErrors.isEmpty() && cdr.blockedWrites.isEmpty() && cdr.debugLogLines.get() < 100,
                        "HTTP>=400: " + cdr.apiFailures + " | console errors: " + cdr.consoleErrors + " | blocked writes: " + cdr.blockedWrites
                                + " | 'BW-360' debug log lines: " + cdr.debugLogLines.get() + " | API calls logged: " + cdr.apiLog.size()));

        addSummary("Mode", "READ-ONLY (only row selection checkboxes clicked; write calls aborted)");
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("Failed API responses", cdr.apiFailures.isEmpty() ? "none" : String.join(" ; ", cdr.apiFailures));
        addSummary("Console errors", cdr.consoleErrors.isEmpty() ? "none" : String.join(" ; ", cdr.consoleErrors));
        addSummary("Blocked write calls", cdr.blockedWrites.isEmpty() ? "none" : String.join(" ; ", cdr.blockedWrites));
        addSummary("Debug log lines (BW-360)", String.valueOf(cdr.debugLogLines.get()));
    }
}
