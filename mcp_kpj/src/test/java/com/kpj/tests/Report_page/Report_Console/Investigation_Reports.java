package com.kpj.tests.Report_page.Report_Console;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

// The page object is also named Investigation_Reports - referenced by its fully-qualified name.

/**
 * Report &gt; Report Console &gt; <b>Investigation Reports</b>. Each report is a section ("tab") of this test, run one after the other in one browser session:
 *
 * <ol>
 *   <li><b>Cancer Summary</b> (Cancer Summary Report): filters From Date_To Date, Ordering Doctor (Location, Payable Type, a user), Diagnosis (smart search + one diagnosis); Show Report;
 *       verify the PDF opens in another tab and is the Cancer Summary report for the chosen period.</li>
 *   <li><b>Critical Alert</b> (Critical Alert Reported Test): filters From Date_To Date, Department (one), SubGroup (Group "Bed", one), OPD/IPD/All (one), From Time_To Time; Show Report;
 *       verify the PDF opens in another tab and is the Critical Alert report.</li>
 * </ol>
 *
 * <p>Each filter: choose it in the Filter dropdown, <b>Add</b>, verify it appears in the Filter Name | Filter Value table, <b>Add Value</b> (its popup), OK, verify the Filter Value. Nothing is saved (the console only asks for a report).</p>
 *
 * <p>Options: {@code -Dir.only=cancer|critical} (run one section), {@code -Dir.months=1|2} (months before To Date, default random 1-2), {@code -Dir.payable=}, {@code -Dir.diag=} (smart search text, default "m"),
 * {@code -Dir.group=} (SubGroup's Group, default "Bed"), {@code -Dir.opd=OPD|IPD|EXT|ALL} (default random), {@code -Dir.timefrom=HH:mm} / {@code -Dir.timeto=HH:mm} (24 h, default 08:00 / 18:00).</p>
 */
public class Investigation_Reports extends DevHisBase {

    private static final String PG = "com.kpj.pages.Report_page.Report_Console.Investigation_Reports";

    public Investigation_Reports() { super("ReportConsole_InvestigationReports"); }

    public static void main(String[] args) {
        Investigation_Reports t = new Investigation_Reports();
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

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Random rnd = new Random();

    @Override
    protected void body() {
        final String only = System.getProperty("ir.only", "").trim().toLowerCase();

        meta("Report Console - Investigation Reports", "Report > Report Console > Investigation Reports",
                "Sections: Cancer Summary Report (From Date_To Date, Ordering Doctor, Diagnosis) and Critical Alert Reported Test (From Date_To Date, Department, SubGroup, OPD/IPD/All, From Time_To Time). "
                        + "Each: Show Report; verify the report opens in another tab and is the report that was added. Nothing is saved.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc = new com.kpj.pages.Report_page.Report_Console.Investigation_Reports(page);

        boolean on = rc.navigateViaMenu();
        stepL("Open Report Console", "Click Report -> Report Console", "The Report Console (Reports + Filter dropdowns, category buttons) is shown",
                on ? "Opened " + page.url() + "; categories: " + rc.categories() : "Did NOT reach the Report Console (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Report Console not reached"); return; }

        if (only.isEmpty() || only.equals("cancer")) cancerSummary(rc);
        if (only.isEmpty() || only.equals("critical")) criticalAlert(rc);
    }

    // =====================================================================================================================================================
    //  Cancer Summary
    // =====================================================================================================================================================

    private void cancerSummary(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Cancer Summary";
        final int months = Integer.getInteger("ir.months", 1 + rnd.nextInt(2));
        final String payable = System.getProperty("ir.payable", "Agent");
        final String diagText = System.getProperty("ir.diag", "m");

        if (!openReport(rc, sec, "Cancer Summary Report")) return;

        // 1) From Date_To Date
        LocalDate[] range = dateFilter(rc, sec, months);
        if (range == null) return;

        // 2) Ordering Doctor
        final String fDoc = "Ordering Doctor";
        if (!addFilter(rc, sec, fDoc)) return;
        if (!openValue(rc, sec, fDoc, "The Ordering Doctor popup opens (Location, Payable Type, Select User)")) return;
        String locWanted = rc.popupOptions("RptColsole.SelectedLocationID").stream().filter(s -> !s.startsWith("--")).findFirst().orElse("--ALL--");
        String loc = rc.selectLocation(locWanted);
        stepL(sec + " - Select the location", "Select Location \"" + locWanted + "\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
        if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location: " + rc.problems); return; }
        String pay = rc.selectPayableType(payable);
        List<String> users = rc.users();
        stepL(sec + " - Select the Payable Type", "Select Payable Type \"" + payable + "\"", "The type is selected and its users are listed",
                !pay.isEmpty() && !users.isEmpty() ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed, first: " + users.get(0) : "Problem - " + rc.problems + " (" + users.size() + " user(s))", !pay.isEmpty() && !users.isEmpty() ? "PASS" : "FAIL");
        if (pay.isEmpty() || users.isEmpty()) { addSummary(sec + " - Result", "FAILED - payable type: " + rc.problems); return; }
        String user = rc.tickUser(0);
        stepL(sec + " - Tick a user", "Tick the checkbox of a user in Select User", "The user is ticked", !user.isEmpty() ? "Ticked \"" + user + "\"" : "Problem - " + rc.problems, !user.isEmpty() ? "PASS" : "FAIL");
        if (user.isEmpty()) { addSummary(sec + " - Result", "FAILED - user: " + rc.problems); return; }
        if (!okStep(rc, sec, fDoc, "Doctor", v -> v.contains(user) || user.contains(v), "the chosen user")) return;

        // 3) Diagnosis
        final String fDia = "Diagnosis";
        if (!addFilter(rc, sec, fDia)) return;
        if (!openValue(rc, sec, fDia, "The Diagnosis popup opens (smart search + Select Diagnosis)")) return;
        int found = rc.smartSearch(diagText);
        stepL(sec + " - Smart search the diagnosis", "Enter \"" + diagText + "\" in the smart search box and click Search", "Diagnoses matching the text are listed",
                found > 0 ? found + " diagnosis row(s) listed, first: " + rc.checkRows().get(0) : "Problem - nothing listed " + rc.problems, found > 0 ? "PASS" : "FAIL");
        if (found == 0) { addSummary(sec + " - Result", "FAILED - smart search: " + rc.problems); return; }
        String dia = rc.tickRow(0);
        stepL(sec + " - Tick a diagnosis", "Tick the checkbox of one diagnosis", "The diagnosis is ticked", !dia.isEmpty() ? "Ticked \"" + dia + "\"" : "Problem - " + rc.problems, !dia.isEmpty() ? "PASS" : "FAIL");
        if (dia.isEmpty()) { addSummary(sec + " - Result", "FAILED - diagnosis: " + rc.problems); return; }
        if (!okStep(rc, sec, fDia, "Diagnosis", v -> v.contains(dia) || dia.contains(v), "the chosen diagnosis")) return;

        // 4) Show Report -> another tab; is it the Cancer Summary report for the chosen period?
        boolean related = showAndVerify(rc, sec, "CANCER\\s+SUMMARY", "CANCER SUMMARY", range, "ICD-11\\s+CODE\\s+DESCRIPTION", "CONFIDENTIAL");

        addSummary(sec + " - Report", "Cancer Summary Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY) + " (" + months + " month(s))");
        addSummary(sec + " - Ordering Doctor", loc + " / " + pay + " / " + user);
        addSummary(sec + " - Diagnosis", "smart search \"" + diagText + "\" -> " + dia);
        addSummary(sec + " - Result", related ? "Cancer Summary report generated for " + range[0].format(DMY) + " - " + range[1].format(DMY) : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Critical Alert Reported Test
    // =====================================================================================================================================================

    private void criticalAlert(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Critical Alert";
        final int months = Integer.getInteger("ir.months", 1 + rnd.nextInt(2));
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Critical Alert Reported Test")) return;

        // 1) From Date_To Date
        LocalDate[] range = dateFilter(rc, sec, months);
        if (range == null) return;

        // 2) Department - one
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Select Department popup opens (a checkbox per department)")) return;
        List<String> deps = rc.checkRows();
        int di = deps.isEmpty() ? -1 : rnd.nextInt(deps.size());
        String dep = di < 0 ? "" : rc.tickRow(di);
        stepL(sec + " - Tick a department", "Tick the checkbox of one department (row " + (di + 1) + " of " + deps.size() + ")", "The department is ticked",
                !dep.isEmpty() ? "Ticked \"" + dep + "\"" : "Problem - " + (deps.isEmpty() ? "no department listed " : "") + rc.problems, !dep.isEmpty() ? "PASS" : "FAIL");
        if (dep.isEmpty()) { addSummary(sec + " - Result", "FAILED - department: " + rc.problems); return; }
        if (!okStep(rc, sec, fDep, "Department", v -> v.contains(dep) || dep.contains(v), "the chosen department")) return;

        // 3) SubGroup - Group "Bed", one sub group
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        int si = rnd.nextInt(subs.size());
        String sub = rc.tickRow(si);
        stepL(sec + " - Tick a sub group", "Tick the checkbox of one sub group (row " + (si + 1) + " of " + subs.size() + ")", "The sub group is ticked", !sub.isEmpty() ? "Ticked \"" + sub + "\"" : "Problem - " + rc.problems, !sub.isEmpty() ? "PASS" : "FAIL");
        if (sub.isEmpty()) { addSummary(sec + " - Result", "FAILED - sub group: " + rc.problems); return; }
        if (!okStep(rc, sec, fSub, "SubGroup", v -> v.contains(sub) || sub.contains(v), "the chosen sub group")) return;

        // 4) OPD/IPD/All - one option
        final String fOpd = "OPD/IPD/All";
        if (!addFilter(rc, sec, fOpd)) return;
        if (!openValue(rc, sec, fOpd, "The OPD / IPD / EXT / ALL popup opens (radio buttons)")) return;
        List<String> radios = rc.radioOptions();
        String wantRadio = System.getProperty("ir.opd", radios.isEmpty() ? "ALL" : radios.get(rnd.nextInt(radios.size())));
        String radio = rc.chooseRadio(wantRadio);
        stepL(sec + " - Choose an option", "Choose one of " + radios + " (\"" + wantRadio + "\")", "The option is selected", !radio.isEmpty() ? "Chosen \"" + radio + "\"" : "Problem - " + rc.problems, !radio.isEmpty() ? "PASS" : "FAIL");
        if (radio.isEmpty()) { addSummary(sec + " - Result", "FAILED - option: " + rc.problems); return; }
        if (!okStep(rc, sec, fOpd, "OPDIPDALL", v -> !v.isEmpty(), "a value")) return;
        final String opdValue = rc.filterValue(fOpd);

        // 5) From Time_To Time
        final String fTime = "From Time_To Time";
        if (!addFilter(rc, sec, fTime)) return;
        if (!openValue(rc, sec, fTime, "The From Time / To Time popup opens")) return;
        String[] tf = System.getProperty("ir.timefrom", "08:00").split(":");
        String[] tt = System.getProperty("ir.timeto", "18:00").split(":");
        int fh = Integer.parseInt(tf[0]), fm = Integer.parseInt(tf[1]), th = Integer.parseInt(tt[0]), tm = Integer.parseInt(tt[1]);
        String fromBox = rc.enterTime(0, fh % 12 == 0 ? 12 : fh % 12, fm, fh >= 12);
        String toBox = rc.enterTime(1, th % 12 == 0 ? 12 : th % 12, tm, th >= 12);
        String wantFromBox = String.format("%02d:%02d %s", fh % 12 == 0 ? 12 : fh % 12, fm, fh >= 12 ? "PM" : "AM");
        String wantToBox = String.format("%02d:%02d %s", th % 12 == 0 ? 12 : th % 12, tm, th >= 12 ? "PM" : "AM");
        boolean timeOk = wantFromBox.equals(fromBox) && wantToBox.equals(toBox);
        stepL(sec + " - Enter the From Time and To Time", "Enter From Time " + wantFromBox + " and To Time " + wantToBox, "Both times are accepted",
                timeOk ? "From Time " + fromBox + ", To Time " + toBox : "Problem - From \"" + fromBox + "\", To \"" + toBox + "\" " + rc.problems, timeOk ? "PASS" : "FAIL");
        if (!timeOk) { addSummary(sec + " - Result", "FAILED - times: " + rc.problems); return; }
        if (!okStep(rc, sec, fTime, "FromToTime", v -> v.contains(wantFromBox) && v.contains(wantToBox), "the time range")) return;

        // 6) Show Report -> another tab; is it the Critical Alert report?
        boolean related = showAndVerify(rc, sec, "CRITICAL", "CRITICAL ALERT", range, "Age/Gender", "(Printed|Print\\s+By|Page\\s+\\d)");

        addSummary(sec + " - Report", "Critical Alert Reported Test (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY) + " (" + months + " month(s))");
        addSummary(sec + " - Department", dep);
        addSummary(sec + " - SubGroup", grp + " -> " + sub);
        addSummary(sec + " - OPD/IPD/All", opdValue);
        addSummary(sec + " - From / To Time", wantFromBox + " - " + wantToBox);
        addSummary(sec + " - Result", related ? "Critical Alert report generated" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  shared steps
    // =====================================================================================================================================================

    /** Investigation Reports tab -> the report in the Reports dropdown. */
    private boolean openReport(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String report) {
        boolean cat = rc.selectCategory(com.kpj.pages.Report_page.Report_Console.Investigation_Reports.CATEGORY);
        stepL(sec + " - Open the Investigation Reports tab", "Click the \"Investigation Reports\" button of the Report Console", "The Reports dropdown lists the investigation reports",
                cat ? "Reports offered (" + rc.reportOptions().size() + "): " + rc.reportOptions() : "Problem - " + rc.problems, cat ? "PASS" : "FAIL");
        if (!cat) { addSummary(sec + " - Result", "FAILED - Investigation Reports: " + rc.problems); return false; }
        String rep = rc.selectReport(report);
        stepL(sec + " - Select the report", "In the Reports dropdown select \"" + report + "\"", "The report is selected and its filters are offered",
                !rep.isEmpty() ? "Reports = \"" + rep + "\"; Filter dropdown offers " + rc.filterOptions() : "Problem - " + rc.problems, !rep.isEmpty() ? "PASS" : "FAIL");
        if (rep.isEmpty()) { addSummary(sec + " - Result", "FAILED - report: " + rc.problems); return false; }
        return true;
    }

    /** Filter dropdown -> Add -> verify the filter appears in the table. */
    private boolean addFilter(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter) {
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
    private boolean openValue(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, String expected) {
        boolean v = rc.clickAddValue(filter);
        stepL(sec + " - Click Add Value (" + filter + ")", "Click Add Value on the filter's row", expected, v ? "The popup opened" : "Problem - " + rc.problems, v ? "PASS" : "FAIL");
        if (!v) { addSummary(sec + " - Result", "FAILED - popup of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    /** OK in the popup -> it closes and the Filter Value shows what was chosen. */
    private boolean okStep(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, String kind, java.util.function.Predicate<String> valueCheck, String what) {
        boolean ok = rc.clickOk(kind);
        String val = rc.filterValue(filter);
        boolean good = ok && valueCheck.test(val);
        stepL(sec + " - Click OK (" + filter + ")", "Click OK in the popup", "The popup closes and the filter's value shows " + what,
                good ? "Filter Value = \"" + val + "\"" : "Problem - " + rc.problems + " Filter Value = \"" + val + "\"", good ? "PASS" : "FAIL");
        if (!good) { addSummary(sec + " - Result", "FAILED - OK of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    /** From Date_To Date: Add, verify, Add Value, From Date {@code months} before To Date, OK. Returns {from, to} or null when a step failed. */
    private LocalDate[] dateFilter(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, int months) {
        final String fDate = "From Date_To Date";
        if (!addFilter(rc, sec, fDate)) return null;
        if (!openValue(rc, sec, fDate, "The From Date / To Date popup opens")) return null;
        LocalDate to = LocalDate.parse(rc.toDate(), DMY);
        LocalDate wantFrom = to.minusMonths(months);
        String from = rc.pickFromDate(wantFrom);
        boolean fromOk = from.equals(wantFrom.format(DMY));
        stepL(sec + " - Enter the From Date", "Pick From Date " + months + " month(s) before To Date (" + to.format(DMY) + ") = " + wantFrom.format(DMY), "From Date is " + months + " month(s) before To Date",
                fromOk ? "From " + from + ", To " + rc.toDate() + " (" + months + " month(s) apart)" : "Problem - From shows \"" + from + "\" instead of " + wantFrom.format(DMY) + " " + rc.problems, fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary(sec + " - Result", "FAILED - From Date: " + rc.problems); return null; }
        if (!okStep(rc, sec, fDate, "FromToDate", v -> v.contains(wantFrom.format(DMY)) && v.contains(to.format(DMY)), "the date range")) return null;
        return new LocalDate[]{wantFrom, to};
    }

    /** Show Report; verify the report opened in another tab (a non-blank PDF) and that it is the report that was added (its title and the chosen period). Returns true when both held. */
    private boolean showAndVerify(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String titleRegex, String titleLabel, LocalDate[] range, String lastHeaderRegex, String footerRegex) {
        int tabsBefore = rc.tabCount();
        boolean shown = rc.clickShowReport();
        com.kpj.pages.Report_page.Report_Console.Investigation_Reports.ReportResult rr = shown ? rc.captureReport(tabsBefore, 45000) : new com.kpj.pages.Report_page.Report_Console.Investigation_Reports.ReportResult();
        stepL(sec + " - Click Show Report", "Click Show Report (ShowReport($event))", "The report is requested", shown ? "Show Report clicked (" + rc.filterRows().size() + " filters set)" : "Problem - " + rc.problems, shown ? "PASS" : "FAIL");
        if (!shown) { addSummary(sec + " - Result", "FAILED - Show Report: " + rc.problems); return false; }

        boolean generated = !rr.url.isEmpty() && rr.isPdf && rr.bytes > 3000 && rr.pdfPages >= 1;
        if (rr.png != null && rr.png.length > 0) {
            step(rr.png, sec + " - Verify the report is generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    (rr.diagnostics.isEmpty() ? "no report tab opened" : rr.diagnostics) + ". The screenshot is the report's first page rendered from the PDF itself.", generated ? "PASS" : "FAIL");
        } else {
            stepL(sec + " - Verify the report is generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", rr.diagnostics.isEmpty() ? "no report tab opened" : rr.diagnostics, generated ? "PASS" : "FAIL");
        }

        String txt = rr.text == null ? "" : rr.text;
        boolean titled = Pattern.compile(titleRegex, Pattern.CASE_INSENSITIVE).matcher(txt).find();
        boolean periodOk = containsDate(txt, range[0]) && containsDate(txt, range[1]);
        DateTimeFormatter longDate = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
        // The report's own header must name the report; when the report prints a period ("REPORTING PERIOD ..."), it must be the chosen one (reported either way).
        boolean related = generated && titled && (!txt.toUpperCase().contains("PERIOD") || periodOk);
        String head = txt.length() > 300 ? txt.substring(0, 300) + " ..." : txt;
        final String relDesc = "Read the report's text: its title" + (periodOk ? " and the reporting period" : "");
        final String relExp = "The report is the " + titleLabel + " report";
        final String relAct = !generated ? "No readable report to judge (" + rr.diagnostics + ")"
                : (titled ? "The report says \"" + titleLabel + "\"" : "The text does not mention " + titleLabel)
                + "; " + (periodOk ? "it prints the chosen dates (" + range[0].format(longDate) + " - " + range[1].format(longDate) + ")" : "the chosen dates are not printed in it")
                + ". Report text: \"" + head + "\"";
        // the report's own page is the picture (the console tab behind it cannot be screenshotted while the report tab is in front)
        if (rr.png != null && rr.png.length > 0) step(rr.png, sec + " - Verify the report is the " + sec + " report", relDesc, relExp, relAct, related ? "PASS" : "FAIL");
        else stepL(sec + " - Verify the report is the " + sec + " report", relDesc, relExp, relAct, related ? "PASS" : "FAIL");
        // Does the report list any record? Its table is empty when nothing sits between the last column heading and the footer. (A manual check with a 6-month window showed no records either.)
        java.util.regex.Matcher hm = Pattern.compile(lastHeaderRegex, Pattern.CASE_INSENSITIVE).matcher(txt);
        int hEnd = -1;
        while (hm.find()) hEnd = hm.end();
        int fStart = -1;
        if (hEnd >= 0) { java.util.regex.Matcher fm = Pattern.compile(footerRegex, Pattern.CASE_INSENSITIVE).matcher(txt); if (fm.find(hEnd)) fStart = fm.start(); }
        String between = hEnd < 0 ? "" : txt.substring(hEnd, fStart >= 0 ? fStart : txt.length()).replaceAll("(?i)page\\s*\\d+\\s*(of|/)\\s*\\d+", " ")
                .replaceAll("(?<![\\d/])\\d{1,3}\\s*/\\s*(?!\\d)", " ")   // a bare page counter such as "1 /" (a date like 23/11/2026 has digits after the slash and is kept)
                .replaceAll("\\s+", " ").trim();
        boolean records = generated && hEnd >= 0 && between.length() >= 3;
        final String recAct = !generated ? "No readable report to judge (" + rr.diagnostics + ")"
                : hEnd < 0 ? "The report's column headings (\"" + lastHeaderRegex + "\") were not found in its text, so its records cannot be judged"
                : records ? "The report lists record data after its column headings: \"" + (between.length() > 160 ? between.substring(0, 160) + " ..." : between) + "\""
                : "FAILS: the report is generated but lists NO RECORD - there is nothing between its last column heading and its footer (only the title, the filters, the headings and the footer are printed). "
                  + "The same was seen manually: even with a 6-month window no record is shown.";
        if (rr.png != null && rr.png.length > 0) step(rr.png, sec + " - Verify the report shows records", "Look for record rows under the report's column headings", "The report lists the records matching the filters", recAct, records ? "PASS" : "FAIL");
        else stepL(sec + " - Verify the report shows records", "Look for record rows under the report's column headings", "The report lists the records matching the filters", recAct, records ? "PASS" : "FAIL");

        addSummary(sec + " - Report tab", rr.url.isEmpty() ? "(none)" : rr.url);
        addSummary(sec + " - Report text", head.isEmpty() ? "(none)" : head);
        addSummary(sec + " - Full report text", txt.length() > 1500 ? txt.substring(0, 1500) + " ..." : (txt.isEmpty() ? "(none)" : txt));
        addSummary(sec + " - Records in the report", records ? "yes" : "NONE (" + (hEnd < 0 ? "headings not found" : "nothing after the column headings") + ")");
        return related && records;
    }

    /** The date as the report prints it ("October 23, 2026", "23/10/2026" or "23-Oct-2026"). */
    private static boolean containsDate(String text, LocalDate d) {
        String month = d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String mon = d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String long1 = month + "\\s+0?" + d.getDayOfMonth() + ",\\s+" + d.getYear();
        String dmy = "0?" + d.getDayOfMonth() + "/0?" + d.getMonthValue() + "/" + d.getYear();
        String dMonY = "0?" + d.getDayOfMonth() + "[-\\s]" + mon + "[a-z]*[-\\s,]+" + d.getYear();
        return Pattern.compile("(" + long1 + ")|(" + dmy + ")|(" + dMonY + ")", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
