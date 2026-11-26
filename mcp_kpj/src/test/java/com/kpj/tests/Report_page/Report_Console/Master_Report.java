package com.kpj.tests.Report_page.Report_Console;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Report &gt; Report Console &gt; <b>Master Report</b> - one section ("tab") per report of the Reports dropdown, each built from a recording of a user's own steps.
 *
 * <p><b>How a Master report differs from the Investigation Reports:</b> Show Report does not open a PDF tab - the console builds the report in the browser and the browser <b>downloads</b> it (a CSV). Every section ends with four
 * verdicts on that file: it was generated (a non-empty {@code .csv} was downloaded), it has no error (a download happened, it is data and not an error page / the console's "No Data Available!"), it is the report that was added
 * (its file name / first line say so) and it lists records (at least one data line under its header) - otherwise FAIL. The file is saved in {@code <outdir>/downloads/MasterReport/} and its first lines are in the test report.</p>
 *
 * <ol>
 *   <li><b>Admission - Admission Type</b> (Master Report &gt; "Admission - Admission Type"; the steps of a recording): filter Location (one, {@code loc.IsSelected}); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Amenities</b> (Master Report &gt; "Admission - Bed Amenities"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Class</b> (Master Report &gt; "Admission - Bed Class"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Details</b> (Master Report &gt; "Admission - Bed Details"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Release Checklist</b> (Master Report &gt; "Admission - Bed Release Checklist"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Discharge Destination</b> (Master Report &gt; "Admission - Discharge Destination"; the steps of a recording - asked for as "Discharge Checklist", the recording chose this neighbouring entry): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Discharge Checklist</b> (Master Report &gt; "Admission - Discharge Checklist"; asked for by the user, derived from the Discharge Destination recording - same steps, the report offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 * </ol>
 *
 * <p>Each filter: choose it in the Filter dropdown, <b>Add</b>, verify it appears in the Filter Name | Filter Value table, <b>Add Value</b> (its popup), tick, OK, verify the Filter Value. Nothing is saved in the application.</p>
 *
 * <p>Options: {@code -Dmst.only=admissiontype|bedamenities|bedclass|beddetails|bedrelease|dischargedestination|dischargechecklist} (run one section; its own report {@code ReportConsole_MasterReport_<Section>.html}, no option = every section = {@code ReportConsole_MasterReport.html}),
 * {@code -Dmst.at.report=} / {@code -Dmst.at.header=}, {@code -Dmst.ba.*}, {@code -Dmst.bc.*}, {@code -Dmst.bd.*}, {@code -Dmst.br.*}, {@code -Dmst.dd.*} and {@code -Dmst.dc.*} (the same two options for the other sections; regexes: the file name / first line must match the report, the first line must match the header; defaults in the section).</p>
 */
public class Master_Report extends DevHisBase {

    /** The sections ("tabs") in the order they run: -Dmst.only value, report id suffix, title. */
    private static final String[][] SECTIONS = {
            {"admissiontype", "AdmissionAdmissionType", "Admission - Admission Type"},
            {"bedamenities", "AdmissionBedAmenities", "Admission - Bed Amenities"},
            {"bedclass", "AdmissionBedClass", "Admission - Bed Class"},
            {"beddetails", "AdmissionBedDetails", "Admission - Bed Details"},
            {"bedrelease", "AdmissionBedReleaseChecklist", "Admission - Bed Release Checklist"},
            {"dischargedestination", "AdmissionDischargeDestination", "Admission - Discharge Destination"},
            {"dischargechecklist", "AdmissionDischargeChecklist", "Admission - Discharge Checklist"},
    };

    private static String[] sectionOf(String only) {
        for (String[] s : SECTIONS) if (s[0].equals(only)) return s;
        return null;
    }

    /** One report per section while a tab is being added ({@code -Dmst.only=<name>}); the whole file (no option) writes {@code ReportConsole_MasterReport.html}. */
    private static String sectionReportId() {
        final String base = "ReportConsole_MasterReport";
        String only = System.getProperty("mst.only", "").trim().toLowerCase();
        if (only.isEmpty()) return base;
        String[] s = sectionOf(only);
        return base + "_" + (s != null ? s[1] : only.replaceAll("[^a-z0-9]", ""));
    }

    public Master_Report() { super(sectionReportId()); }

    public static void main(String[] args) {
        Master_Report t = new Master_Report();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** A step whose screenshot is taken here with a 40 s limit (the framework's 10 s one times out now and then on the heavy console screens). */
    private void stepL(String name, String desc, String expected, String actual, String status) {
        byte[] png = null;
        try { png = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { png = null; }
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(name, desc, expected, actual, status);
    }

    @Override
    protected void body() {
        final String only = System.getProperty("mst.only", "").trim().toLowerCase();

        final String[] one = sectionOf(only);
        if (!only.isEmpty() && one != null)
            meta("Report Console - Master Report - " + one[2], "Report > Report Console > Master Report > " + one[2],
                    "ONE TAB ONLY (-Dmst.only=" + only + "): " + one[2] + ". Show Report; the report is downloaded as a CSV: it must be generated, without an error, be the report that was added and list records. Nothing is saved in the application.");
        else
            meta("Report Console - Master Report", "Report > Report Console > Master Report",
                    "Every tab of the Master Report file: " + SECTIONS.length + " section(s) (" + java.util.Arrays.stream(SECTIONS).map(x -> x[2]).collect(java.util.stream.Collectors.joining(", ")) + "). "
                            + "Each: Show Report; the report is downloaded as a CSV: it must be generated, without an error, be the report that was added and list records. Nothing is saved in the application.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Report_page.Report_Console.Master_Report rc = new com.kpj.pages.Report_page.Report_Console.Master_Report(page);

        boolean on = rc.navigateViaMenu();
        stepL("Open Report Console", "Click Report -> Report Console", "The Report Console (Reports + Filter dropdowns, category buttons) is shown",
                on ? "Opened " + page.url() + "; categories: " + rc.categories() : "Did NOT reach the Report Console (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Report Console not reached"); return; }

        if (only.isEmpty() || only.equals("admissiontype")) admissionType(rc);
        if (only.isEmpty() || only.equals("bedamenities")) bedAmenities(rc);
        if (only.isEmpty() || only.equals("bedclass")) bedClass(rc);
        if (only.isEmpty() || only.equals("beddetails")) bedDetails(rc);
        if (only.isEmpty() || only.equals("bedrelease")) bedReleaseChecklist(rc);
        if (only.isEmpty() || only.equals("dischargedestination")) dischargeDestination(rc);
        if (only.isEmpty() || only.equals("dischargechecklist")) dischargeChecklist(rc);
    }

    // =====================================================================================================================================================
    //  Admission - Admission Type (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void admissionType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Admission Type", "ADMISSION TYPE",
                System.getProperty("mst.at.report", "ADMISSION\\W*TYPE"), System.getProperty("mst.at.header", "^\\W*admissiontype\\W*,\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Amenities (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedAmenities(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Amenities", "BED AMENITIES",
                System.getProperty("mst.ba.report", "BED\\W*AMENIT"), System.getProperty("mst.ba.header", "^\\W*bedamenitiesid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Class (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedClass(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Class", "BED CLASS",
                System.getProperty("mst.bc.report", "BED\\W*CLASS"), System.getProperty("mst.bc.header", "^\\W*bedclassid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Details (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedDetails(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Details", "BED DETAILS",
                System.getProperty("mst.bd.report", "BED\\W*DETAIL"), System.getProperty("mst.bd.header", "^\\W*ID\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Ward\\s*,\\s*Bed\\s*Class\\s*,\\s*Room\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Release Checklist (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedReleaseChecklist(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Release Checklist", "BED RELEASE CHECKLIST",
                System.getProperty("mst.br.report", "BED\\W*RELEASE"), System.getProperty("mst.br.header", "^\\W*id\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Discharge Destination (recorded: Master Report; Location; Show Report -> a CSV is downloaded).
    //  NOTE: the user asked for "Admission - Discharge Checklist" but the recording chose the neighbouring dropdown entry "Admission - Discharge Destination" (option number:1658) - this is what was recorded.
    // =====================================================================================================================================================

    private void dischargeDestination(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Discharge Destination", "DISCHARGE DESTINATION",
                System.getProperty("mst.dd.report", "DISCHARGE\\W*DESTINATION"), System.getProperty("mst.dd.header", "^\\W*dischargedestinationid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Discharge Checklist (asked for by the user; NOT separately recorded - the recording of the neighbouring "Discharge Destination" has exactly these steps and a probe showed the report offers only the Location filter)
    // =====================================================================================================================================================

    private void dischargeChecklist(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Discharge Checklist", "DISCHARGE CHECKLIST",
                System.getProperty("mst.dc.report", "DISCHARGE\\W*CHECKLIST"), System.getProperty("mst.dc.header", "^\\W*id\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    /**
     * A Master report whose only filter is Location (as recorded for Admission - Admission Type and Admission - Bed Amenities): Master Report tab, the report, filter Location (the popup's first record, id 1),
     * Show Report -> the CSV is verified ({@link #showAndVerifyDownload}).
     */
    private void locationCsvReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String reportLabel, String reportRegex, String headerRegex) {
        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, sec)) return;   // the Reports dropdown entry is named like the section

        // 1) Location: the Select Location popup (checkbox loc.IsSelected, record id 1)
        final String fLoc = "Location";
        if (!addFilter(rc, sec, fLoc)) return;
        if (!openValue(rc, sec, fLoc, "The Select Location popup opens (a checkbox per location)")) return;
        List<String> locs = rc.rowsOf("loc.IsSelected");
        stepL(sec + " - List the locations", "Look at the locations the popup offers", "The popup lists the locations",
                !locs.isEmpty() ? locs.size() + " location(s) listed, first: " + locs.get(0) : "No location is listed " + rc.problems, !locs.isEmpty() ? "PASS" : "FAIL");
        if (locs.isEmpty()) { addSummary(sec + " - Result", "FAILED - no location listed"); return; }
        List<String> locTicked = tickIds(rc, sec, "loc.IsSelected", "a location", "1");
        if (locTicked == null) return;
        if (!okStep(rc, sec, fLoc, "Location", v -> locTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen location")) return;

        // 2) Show Report -> a CSV is downloaded: it must be generated, without an error, be this report and list records
        boolean related = showAndVerifyDownload(rc, sec, reportRegex, reportLabel, headerRegex);

        addSummary(sec + " - Report", sec + " (Master Report)");
        addSummary(sec + " - Location", String.valueOf(locTicked));
        addSummary(sec + " - Result", related ? sec + " CSV generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  shared steps
    // =====================================================================================================================================================

    /** Tick the rows of the open popup by record id (the row at the same position when an id is not listed); returns the ticked names or null when a step failed. */
    private List<String> tickIds(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String ngModel, String what, String... ids) {
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String n = rc.tickRowById(ngModel, ids[i]);
            boolean fallback = false;
            if (n.isEmpty() && rc.rowsOf(ngModel).size() > i) { n = rc.tickRowOf(ngModel, i); fallback = true; }
            stepL(sec + " - Tick " + what + " (" + (i + 1) + " of " + ids.length + ")", "Tick the checkbox of " + what + " (recorded record id " + ids[i] + ")", "The row is ticked",
                    !n.isEmpty() ? "Ticked \"" + n + "\"" + (fallback ? " (record id " + ids[i] + " is not listed - row " + (i + 1) + " used instead)" : " (id " + ids[i] + ")") : "Problem - " + rc.problems, !n.isEmpty() ? "PASS" : "FAIL");
            if (n.isEmpty()) { addSummary(sec + " - Result", "FAILED - " + what + ": " + rc.problems); return null; }
            names.add(n);
        }
        return names;
    }

    /** Master Report tab -> the report in the Reports dropdown. */
    private boolean openReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String report) {
        boolean cat = rc.selectCategory(com.kpj.pages.Report_page.Report_Console.Master_Report.CATEGORY);
        stepL(sec + " - Open the Master Report tab", "Click the \"Master Report\" button of the Report Console", "The Reports dropdown lists the master reports",
                cat ? "Reports offered (" + rc.reportOptions().size() + "): " + rc.reportOptions() : "Problem - " + rc.problems, cat ? "PASS" : "FAIL");
        if (!cat) { addSummary(sec + " - Result", "FAILED - Master Report: " + rc.problems); return false; }
        String rep = rc.selectReport(report);
        stepL(sec + " - Select the report", "In the Reports dropdown select \"" + report + "\"", "The report is selected and its filters are offered",
                !rep.isEmpty() ? "Reports = \"" + rep + "\"; Filter dropdown offers " + rc.filterOptions() : "Problem - " + rc.problems, !rep.isEmpty() ? "PASS" : "FAIL");
        if (rep.isEmpty()) { addSummary(sec + " - Result", "FAILED - report: " + rc.problems); return false; }
        return true;
    }

    /** Filter dropdown -> Add -> verify the filter appears in the table. */
    private boolean addFilter(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter) {
        String f = rc.selectFilter(filter);
        stepL(sec + " - Select the filter " + filter, "In the Filter dropdown select \"" + filter + "\"", "The filter is selected", !f.isEmpty() ? "Filter = \"" + f + "\"" : "Problem - " + rc.problems, !f.isEmpty() ? "PASS" : "FAIL");
        if (f.isEmpty()) { addSummary(sec + " - Result", "FAILED - filter " + filter + ": " + rc.problems); return false; }
        boolean a = rc.clickAdd(filter);
        stepL(sec + " - Click Add (" + filter + ")", "Click Add (AddFilter())", "The filter is added to the table", a ? "Add clicked" : "Problem - " + rc.problems, a ? "PASS" : "FAIL");
        boolean in = rc.hasFilter(filter);
        stepL(sec + " - Verify the filter appears in the table (" + filter + ")", "In the Filter Name | Filter Value table look for \"" + filter + "\"", "The added filter appears",
                in ? "The table lists " + rc.filterRows().stream().map(r -> r[0]).toList() : "\"" + filter + "\" is not in the table (" + rc.filterRows().stream().map(r -> r[0]).toList() + ")", in ? "PASS" : "FAIL");
        if (!in) { addSummary(sec + " - Result", "FAILED - the " + filter + " filter was not added"); return false; }
        return true;
    }

    /** Add Value on the filter's row -> its popup. */
    private boolean openValue(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter, String expected) {
        boolean v = rc.clickAddValue(filter);
        stepL(sec + " - Click Add Value (" + filter + ")", "Click Add Value on the filter's row", expected, v ? "The popup opened" : "Problem - " + rc.problems, v ? "PASS" : "FAIL");
        if (!v) { addSummary(sec + " - Result", "FAILED - popup of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    /** OK in the popup -> it closes and the Filter Value shows what was chosen. */
    private boolean okStep(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter, String kind, java.util.function.Predicate<String> valueCheck, String what) {
        boolean ok = rc.clickOk(kind);
        String val = rc.filterValue(filter);
        boolean good = ok && valueCheck.test(val);
        stepL(sec + " - Click OK (" + filter + ")", "Click OK in the popup", "The popup closes and the filter's value shows " + what,
                good ? "Filter Value = \"" + val + "\"" : "Problem - " + rc.problems + " Filter Value = \"" + val + "\"", good ? "PASS" : "FAIL");
        if (!good) { addSummary(sec + " - Result", "FAILED - OK of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    /**
     * Show Report; the report is DOWNLOADED as a file. Four verdicts: it was generated (a non-empty .csv), it has no error (a download happened - the console's "No Data Available!" or no file at all = FAIL - and the file is data,
     * not an HTML error page), it is the report that was added (file name / first line match {@code reportRegex}; the first line matches {@code headerRegex}) and it lists records (at least one data line under the header).
     * Returns true when all held.
     */
    private boolean showAndVerifyDownload(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String reportRegex, String reportLabel, String headerRegex) {
        Path dir = reportDir.getParent() != null ? reportDir.getParent().resolve("downloads").resolve("MasterReport") : Paths.get("downloads", "MasterReport");
        com.kpj.pages.Report_page.Report_Console.Master_Report.DownloadResult dr = rc.showReportAndCaptureDownload(dir, 60000);
        boolean clicked = dr.downloaded || !dr.diagnostics.startsWith("Show Report could not be clicked");
        stepL(sec + " - Click Show Report", "Click Show Report (ShowReport($event))", "The report is requested", clicked ? "Show Report clicked (" + rc.filterRows().size() + " filter(s) set)" : "Problem - " + rc.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary(sec + " - Result", "FAILED - Show Report: " + rc.problems); return false; }

        // 1) generated: a non-empty .csv was downloaded
        final boolean isCsv = dr.filename.toLowerCase().endsWith(".csv");
        boolean generated = dr.downloaded && dr.failure.isEmpty() && dr.bytes > 0 && isCsv && !dr.looksLikeZip;
        stepL(sec + " - Verify the report is generated (downloaded as CSV)", "Check a file was downloaded after Show Report", "A non-empty CSV file is downloaded",
                dr.diagnostics + (dr.downloaded ? (isCsv ? "" : " - FAILS: the file is not a .csv") : " - FAILS: nothing was downloaded") + (dr.savedTo.isEmpty() ? "" : ". Saved to " + dr.savedTo), generated ? "PASS" : "FAIL");

        // 2) no error: a download happened, it is data and not an error page / the console's refusal
        boolean errored = !dr.downloaded || !dr.failure.isEmpty() || dr.looksLikeHtml
                || Pattern.compile("server error|failed to retrieve|error in file|exception|database vendor code", Pattern.CASE_INSENSITIVE).matcher(dr.text).find();
        final String errAct = !dr.downloaded ? "FAILS: the report was NOT downloaded - " + dr.diagnostics
                : errored ? "FAILS: the downloaded file is an ERROR, not data - " + (dr.failure.isEmpty() ? (dr.text.length() > 300 ? dr.text.substring(0, 300) + " ..." : dr.text) : dr.failure)
                : "The report was downloaded without an error (" + dr.bytes + " bytes)";
        stepL(sec + " - Verify the report has no error", "Look at what Show Report produced for an error message", "The report is downloaded without an error", errAct, errored ? "FAIL" : "PASS");
        addSummary(sec + " - Report error", errored ? "YES - " + (dr.downloaded ? "error text in the file" : (dr.errorText.isEmpty() ? "nothing downloaded" : dr.errorText)) : "none");

        // 3) it is the report that was added: the file name or its first line says so, and the first line is the report's header
        String first = dr.lines.isEmpty() ? "" : dr.lines.get(0);
        // the browser reports the file name with underscores for spaces ("Admission_-_Bed_Details.csv"): they count as separators here
        boolean named = Pattern.compile(reportRegex, Pattern.CASE_INSENSITIVE).matcher(dr.filename.replace('_', ' ') + " " + first).find();
        boolean headed = Pattern.compile(headerRegex, Pattern.CASE_INSENSITIVE).matcher(first).find();
        boolean related = generated && named && headed;
        String head = first.length() > 300 ? first.substring(0, 300) + " ..." : first;
        stepL(sec + " - Verify the report is the " + reportLabel + " report", "Read the file's name and first line", "The file is the " + reportLabel + " report",
                !dr.downloaded ? "No readable file to judge (" + dr.diagnostics + ")"
                        : (named ? "The file name / first line mention \"" + reportRegex + "\"" : "FAILS: neither the file name \"" + dr.filename + "\" nor the first line matches \"" + reportRegex + "\"")
                          + "; " + (headed ? "the first line is the report's header" : "FAILS: the first line does not match the header pattern \"" + headerRegex + "\"") + ". First line: \"" + head + "\"",
                related ? "PASS" : "FAIL");

        // 4) it lists records: at least one data line (not only commas / blanks) under the header
        int dataLines = 0;
        for (int i = 1; i < dr.lines.size(); i++) if (!dr.lines.get(i).replaceAll("[,;\\s\"]", "").isEmpty()) dataLines++;
        boolean records = generated && dataLines >= 1;
        final String recAct = !dr.downloaded ? "No readable file to judge (" + dr.diagnostics + ")"
                : records ? dataLines + " data line(s) under the header; the first: \"" + (dr.lines.get(1).length() > 200 ? dr.lines.get(1).substring(0, 200) + " ..." : dr.lines.get(1)) + "\""
                : "FAILS: the file is generated but lists NO RECORD - " + (dr.lines.size() <= 1 ? "only its header line (" + dr.lines.size() + " line)" : "only empty lines under its header") + ". No record matches the chosen filters (or the report returns none).";
        stepL(sec + " - Verify the report shows records", "Look for data lines under the CSV's header", "The CSV lists the records matching the filters", recAct, records ? "PASS" : "FAIL");

        addSummary(sec + " - Downloaded file", dr.downloaded ? dr.filename + " (" + dr.bytes + " bytes) - " + dr.savedTo : "(none)");
        addSummary(sec + " - Full report text", dr.text.isEmpty() ? "(none)" : (dr.text.length() > 1500 ? dr.text.substring(0, 1500) + " ..." : dr.text));
        addSummary(sec + " - Records in the report", records ? dataLines + " data line(s)" : "NONE");
        return related && records;
    }
}
