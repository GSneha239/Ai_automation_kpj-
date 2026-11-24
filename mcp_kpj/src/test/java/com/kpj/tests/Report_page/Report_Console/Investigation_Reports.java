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
 *   <li><b>Critical Value Result</b> (Critical Value Result Report; the steps of a recording): Investigation Reports; Add with no report (warning); the report; filter From Date_To Date (previous month twice, day 6);
 *       filter SubGroup (Group "Bed", two sub groups ticked); Show Report; verify the PDF opens in another tab, is the Critical Value Result report AND lists records (no record = FAIL).</li>
 *   <li><b>Department Analysis Doctor</b> (Department Analysis Doctor Report; the steps of a recording): filters Year 2026; Doctor (Location, Payable Type Agent, two users); Location (one); Group (three);
 *       SubGroup (Group "Bed", three); Month May; Services (one); Encounter Type (two); Show Report; the report must open WITHOUT AN ERROR, be the Department Analysis Doctor report and list records.</li>
 *   <li><b>Department Analysis</b> (Department Analysis Report; the steps of a recording): filters Doctor (Location, Payable Type Agent, three users); From Date_To Date (previous month x3, day 2); Department (two ticked = refused with
 *       "Can select only one Group at a time!", the second unticked); Service (All Services); Show Report - the console answers "No Data Available!" and opens nothing, so, as recorded, the From Date is moved earlier and Show Report is
 *       clicked again. The report must OPEN, without an error, and list records - otherwise FAIL.</li>
 *   <li><b>Doctor Wise OPD &amp; IPD</b> (Doctor Wise OPD &amp; IPD Report; the steps of a recording): filters From Date_To Date (previous month x2, day 6); Doctor (Location, Payable Type Agent, one user); Location (one);
 *       OPD/IPD/All (OPD, then ALL); Show Report; the report must open in another tab, without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Endoscope Census</b> (Endoscope Census - Patient Type Report; the steps of a recording): filters From Date_To Date (previous month x2, day 6); Location (one); Show Report; the report must open in another tab,
 *       without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Laboratory Census</b> (Laboratory Census; the steps of a recording): filters From Date_To Date (previous month x4, day 5); SubGroup (Group "Bed", one); Department (one); Test Name (twice: the Filter dropdown
 *       offers two different filters of that name, a test ticked in each); Speciality (one); Ordering Doctor (Location, Payable Type Agent, one user); Reporting Doctor (OK with nothing chosen); Show Report (clicked twice, as
 *       recorded); the report must open in another tab, without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Monthly Radiology Census</b> (Monthly Radiology Census Revenue; the steps of a recording): filters From Date_To Date (previous month x4, day 5); SubGroup (Group "Catherisation Laboratory", one); Department (one);
 *       Test Name (one); Ordering Doctor (Location, Payable Type Agent, one user); Reporting Doctor (Location, Payable Types Agent / Broker / Broker MWMYYLJU0 / Consultant browsed, one user); Modality (one); Show Report; the report
 *       must open in another tab, without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Path Audit</b> (Path Audit; the steps of a recording): filters From Date_To Date (previous month x4, day 5); SubGroup (the Group dropdown browsed Consignments / Bed / Catherisation Laboratory, one sub group ticked);
 *       Show Report; the report must open in another tab, without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Pathology Test Count</b> (Pathology Test Count; the steps of a recording): filters From Date_To Date (From picked first 3 months back day 2, then changed to 4 months back day 5); SubGroup (Group "Bed", one);
 *       From Time_To Time (the From picker's hour arrow once, the To picker's three times - both start at the current time); Show Report; the report must open in another tab, without an error, be this report and
 *       list records - otherwise FAIL.</li>
 *   <li><b>Pathology Test Revenue</b> (Pathology Test Revenue Report; the steps of a recording): filters From Date_To Date (previous month x3, day 2); SubGroup (Group "Bed", one); From Time_To Time (the From picker's hour
 *       arrow once, the To picker's three times - both start at the current time); Show Report; the report must open in another tab, without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Radiology Test Count</b> (Radiology Test Count Report; the steps of a recording): filters From Date_To Date (previous month x2, day 6); SubGroup (Group "Bed", one); From Time_To Time (the From picker's hour
 *       arrow once, the To picker's three times - both start at the current time); Show Report; the report must open in another tab WITHOUT A SERVER ERROR (HTTP 4xx/5xx or an error page = FAIL), be this report and
 *       list records - otherwise FAIL.</li>
 *   <li><b>Radiology Test Revenue</b> (Radiology Test Revenue Report; the steps of a recording): filters From Date_To Date (previous month x3, day 2); SubGroup (Group "Bed", one); From Time_To Time (the From picker's hour
 *       arrow once, the To picker's three times - both start at the current time); Show Report; the report must open in another tab WITHOUT A SERVER ERROR (HTTP 4xx/5xx or an error page = FAIL), be this report and
 *       list records - otherwise FAIL.</li>
 *   <li><b>Radiotherapy Monthly Detailed Census</b> (the steps of a recording): filters Location (one, {@code loc.IsSelected}); Group (one); SubGroup (Group "Bed", one); Blood Group (one); From Date_To Date (previous month x3, day 2);
 *       Show Report; the report must open in another tab without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Revenue Report</b> (the steps of a recording): filters From Date_To Date (previous month x2, day 6); SubGroup (Group "Bed", one); Department (one); Test Name (one); Ordering Doctor (Location, Payable Type "Agent", one user);
 *       Reporting Doctor (a user ticked with Location / Payable Type left as the Ordering Doctor popup left them); Modality (one); Show Report; the report must open in another tab without an error, be this report and list records - otherwise FAIL.</li>
 *   <li><b>Samplewise Organisms Isolated</b> (the steps of a recording): filters From Date_To Date (previous month x3, day 2); SubGroup (Group "Bed", one); Show Report; the report must open in another tab without an error, be this report
 *       and list records - otherwise FAIL.</li>
 *   <li><b>STAT Report</b> (the steps of a recording): filters From Date_To Date (previous month x2, day 6); Visit Type (one); Department (four); Show Report; the report must open in another tab without an error, be this report and
 *       list records - otherwise FAIL (the console may answer "No Data Available!" and open nothing: FAIL).</li>
 * </ol>
 *
 * <p>Every section ends with three verdicts on the report tab: it has no error (HTTP 4xx/5xx or an error page = FAIL), it is the report that was added, and it lists records (none = FAIL).</p>
 *
 * <p>Each filter: choose it in the Filter dropdown, <b>Add</b>, verify it appears in the Filter Name | Filter Value table, <b>Add Value</b> (its popup), OK, verify the Filter Value. Nothing is saved (the console only asks for a report).</p>
 *
 * <p>Options: {@code -Dir.only=cancer|critical|value|analysis|department|doctorwise|endoscope|laboratory|radiology|pathaudit|pathtest|pathrevenue|radtest|radrev|radiotherapy|revenue|organisms|stat} (run one section), {@code -Dir.year=} / {@code -Dir.month=} (Department Analysis Doctor, default 2026 / May), {@code -Dir.months=1|2} (months before To Date, default random 1-2), {@code -Dir.payable=}, {@code -Dir.diag=} (smart search text, default "m"),
 * {@code -Dir.group=} (SubGroup's Group, default "Bed"), {@code -Dir.opd=OPD|IPD|EXT|ALL} (default random), {@code -Dir.timefrom=HH:mm} / {@code -Dir.timeto=HH:mm} (24 h, default 08:00 / 18:00).</p>
 */
public class Investigation_Reports extends DevHisBase {

    private static final String PG = "com.kpj.pages.Report_page.Report_Console.Investigation_Reports";

    /** The sections ("tabs") in the order they run: -Dir.only value, report id suffix, title. */
    private static final String[][] SECTIONS = {
            {"cancer", "CancerSummary", "Cancer Summary"},
            {"critical", "CriticalAlert", "Critical Alert"},
            {"value", "CriticalValueResult", "Critical Value Result"},
            {"analysis", "DepartmentAnalysisDoctor", "Department Analysis Doctor"},
            {"department", "DepartmentAnalysis", "Department Analysis"},
            {"doctorwise", "DoctorWiseOPDIPD", "Doctor Wise OPD & IPD"},
            {"endoscope", "EndoscopeCensus", "Endoscope Census"},
            {"laboratory", "LaboratoryCensus", "Laboratory Census"},
            {"radiology", "MonthlyRadiologyCensus", "Monthly Radiology Census"},
            {"pathaudit", "PathAudit", "Path Audit"},
            {"pathtest", "PathologyTestCount", "Pathology Test Count"},
            {"pathrevenue", "PathologyTestRevenue", "Pathology Test Revenue"},
            {"radtest", "RadiologyTestCount", "Radiology Test Count"},
            {"radrev", "RadiologyTestRevenue", "Radiology Test Revenue"},
            {"radiotherapy", "RadiotherapyMonthlyDetailedCensus", "Radiotherapy Monthly Detailed Census"},
            {"revenue", "RevenueReport", "Revenue Report"},
            {"organisms", "SamplewiseOrganismsIsolated", "Samplewise Organisms Isolated"},
            {"stat", "STATReport", "STAT Report"},
    };

    private static String[] sectionOf(String only) {
        for (String[] s : SECTIONS) if (s[0].equals(only)) return s;
        return null;
    }

    /**
     * ONE REPORT PER SECTION while a tab is being added ({@code -Dir.only=<name>} -> {@code ReportConsole_InvestigationReports_<Section>.html}, the other sections' reports are left alone); the whole file
     * (no {@code -Dir.only}: every tab) writes {@code ReportConsole_InvestigationReports.html}.
     */
    private static String sectionReportId() {
        final String base = "ReportConsole_InvestigationReports";
        String only = System.getProperty("ir.only", "").trim().toLowerCase();
        if (only.isEmpty()) return base;
        String[] s = sectionOf(only);
        return base + "_" + (s != null ? s[1] : only.replaceAll("[^a-z0-9]", ""));
    }

    public Investigation_Reports() { super(sectionReportId()); }

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

        final String[] one = sectionOf(only);
        if (!only.isEmpty() && one != null)
            meta("Report Console - Investigation Reports - " + one[2], "Report > Report Console > Investigation Reports > " + one[2],
                    "ONE TAB ONLY (-Dir.only=" + only + "): " + one[2] + ". Show Report; the report must open in another tab, without an error, be the report that was added and list records. Nothing is saved.");
        else
            meta("Report Console - Investigation Reports", "Report > Report Console > Investigation Reports",
                    "Every tab of the Investigation Reports file: " + SECTIONS.length + " sections (" + java.util.Arrays.stream(SECTIONS).map(x -> x[2]).collect(java.util.stream.Collectors.joining(", ")) + "). "
                            + "Each: Show Report; the report must open in another tab, without an error, be the report that was added and list records. Nothing is saved.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc = new com.kpj.pages.Report_page.Report_Console.Investigation_Reports(page);

        boolean on = rc.navigateViaMenu();
        stepL("Open Report Console", "Click Report -> Report Console", "The Report Console (Reports + Filter dropdowns, category buttons) is shown",
                on ? "Opened " + page.url() + "; categories: " + rc.categories() : "Did NOT reach the Report Console (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Report Console not reached"); return; }

        if (only.isEmpty() || only.equals("cancer")) cancerSummary(rc);
        if (only.isEmpty() || only.equals("critical")) criticalAlert(rc);
        if (only.isEmpty() || only.equals("value")) criticalValue(rc);
        if (only.isEmpty() || only.equals("analysis")) departmentAnalysisDoctor(rc);
        if (only.isEmpty() || only.equals("department")) departmentAnalysis(rc);
        if (only.isEmpty() || only.equals("doctorwise")) doctorWiseOpdIpd(rc);
        if (only.isEmpty() || only.equals("endoscope")) endoscopeCensus(rc);
        if (only.isEmpty() || only.equals("laboratory")) laboratoryCensus(rc);
        if (only.isEmpty() || only.equals("radiology")) monthlyRadiologyCensus(rc);
        if (only.isEmpty() || only.equals("pathaudit")) pathAudit(rc);
        if (only.isEmpty() || only.equals("pathtest")) pathologyTestCount(rc);
        if (only.isEmpty() || only.equals("pathrevenue")) pathologyTestRevenue(rc);
        if (only.isEmpty() || only.equals("radtest")) radiologyTestCount(rc);
        if (only.isEmpty() || only.equals("radrev")) radiologyTestRevenue(rc);
        if (only.isEmpty() || only.equals("radiotherapy")) radiotherapyMonthlyDetailedCensus(rc);
        if (only.isEmpty() || only.equals("revenue")) revenueReport(rc);
        if (only.isEmpty() || only.equals("organisms")) samplewiseOrganismsIsolated(rc);
        if (only.isEmpty() || only.equals("stat")) statReport(rc);
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
    //  Critical Value Result (recorded: Investigation Reports; Add with no report; Critical Value Result Report; From Date_To Date; SubGroup / Bed / two sub groups; Show Report)
    // =====================================================================================================================================================

    private void criticalValue(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Critical Value Result";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        boolean cat = rc.selectCategory(com.kpj.pages.Report_page.Report_Console.Investigation_Reports.CATEGORY);
        stepL(sec + " - Open the Investigation Reports tab", "Click the \"Investigation Reports\" button of the Report Console", "The Reports dropdown lists the investigation reports",
                cat ? "Reports offered (" + rc.reportOptions().size() + "): " + rc.reportOptions() : "Problem - " + rc.problems, cat ? "PASS" : "FAIL");
        if (!cat) { addSummary(sec + " - Result", "FAILED - Investigation Reports: " + rc.problems); return; }

        // as recorded: Add is clicked BEFORE any report is chosen - the screen must refuse
        String warn = rc.clickAddWithoutReport();
        boolean warned = warn.toLowerCase().contains("select the report");
        stepL(sec + " - Click Add without a report", "Click Add (AddFilter()) before a report is selected", "The warning \"Please select the Report!\" appears",
                warned ? "Warning: \"" + warn + "\"" : (warn.isEmpty() ? "No warning appeared" : "Unexpected message: \"" + warn + "\"") + " " + rc.problems, warned ? "PASS" : "FAIL");

        String rep = rc.selectReport("Critical Value Result Report");
        stepL(sec + " - Select the report", "In the Reports dropdown select \"Critical Value Result Report\"", "The report is selected and its filters are offered",
                !rep.isEmpty() ? "Reports = \"" + rep + "\"; Filter dropdown offers " + rc.filterOptions() : "Problem - " + rc.problems, !rep.isEmpty() ? "PASS" : "FAIL");
        if (rep.isEmpty()) { addSummary(sec + " - Result", "FAILED - report: " + rc.problems); return; }

        // 1) From Date_To Date: as recorded - previous month twice, day 6
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", two sub groups ticked
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && subs.size() >= 2 ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && subs.size() >= 2 ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.size() < 2) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        String sub1 = rc.tickRow(0);
        stepL(sec + " - Tick the first sub group", "Tick the checkbox of the first sub group", "The sub group is ticked", !sub1.isEmpty() ? "Ticked \"" + sub1 + "\"" : "Problem - " + rc.problems, !sub1.isEmpty() ? "PASS" : "FAIL");
        if (sub1.isEmpty()) { addSummary(sec + " - Result", "FAILED - sub group 1: " + rc.problems); return; }
        String sub2 = rc.tickRow(1);
        stepL(sec + " - Tick the second sub group", "Tick the checkbox of the second sub group", "The sub group is ticked", !sub2.isEmpty() ? "Ticked \"" + sub2 + "\"" : "Problem - " + rc.problems, !sub2.isEmpty() ? "PASS" : "FAIL");
        if (sub2.isEmpty()) { addSummary(sec + " - Result", "FAILED - sub group 2: " + rc.problems); return; }
        if (!okStep(rc, sec, fSub, "SubGroup", v -> v.contains(sub1) && v.contains(sub2), "both chosen sub groups")) return;

        // 3) Show Report -> another tab: is it the Critical Value Result report, and does it list records?
        boolean related = showAndVerify(rc, sec, "CRITICAL\\s+VALUE", "CRITICAL VALUE RESULT", range, System.getProperty("ir.cv.header", "Critical\\s+Values\\s*\\(Low-High\\)"), System.getProperty("ir.cv.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Critical Value Result Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + sub1 + ", " + sub2);
        addSummary(sec + " - Result", related ? "Critical Value Result report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Department Analysis Doctor Report (recorded: Year; Doctor; Location; Group; SubGroup; Month; Services; Encounter Type; Show Report)
    // =====================================================================================================================================================

    private void departmentAnalysisDoctor(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Department Analysis Doctor";
        final String year = System.getProperty("ir.year", "2026");
        final String month = System.getProperty("ir.month", "May");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Department Analysis Doctor Report")) return;

        // 1) Year
        final String fYear = "Year";
        if (!addFilter(rc, sec, fYear)) return;
        if (!openValue(rc, sec, fYear, "The Year popup opens")) return;
        String y = rc.selectInPopup("RptColsole.Year", "Year", year);
        stepL(sec + " - Select the year", "In the popup select Year \"" + year + "\"", "The year is selected", !y.isEmpty() ? "Year = \"" + y + "\"" : "Problem - " + rc.problems, !y.isEmpty() ? "PASS" : "FAIL");
        if (y.isEmpty()) { addSummary(sec + " - Result", "FAILED - year: " + rc.problems); return; }
        if (!okStep(rc, sec, fYear, "Year", v -> v.contains(year), "the year")) return;

        // 2) Doctor: Location, Payable Type (Agent), two users
        final String fDoc = "Doctor";
        if (!addFilter(rc, sec, fDoc)) return;
        if (!openValue(rc, sec, fDoc, "The Doctor popup opens (Location, Payable Type, Select User)")) return;
        String loc = rc.selectLocation("KPJ DAMANSARA SPECIALIST HOSPITAL");
        stepL(sec + " - Select the location", "Select Location \"KPJ DAMANSARA SPECIALIST HOSPITAL\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
        if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location: " + rc.problems); return; }
        String pay = rc.selectPayableType("Agent");
        List<String> users = rc.users();
        stepL(sec + " - Select the Payable Type", "Select Payable Type \"Agent\"", "The type is selected and its users are listed",
                !pay.isEmpty() && !users.isEmpty() ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed" : "Problem - " + rc.problems + " (" + users.size() + " user(s))", !pay.isEmpty() && !users.isEmpty() ? "PASS" : "FAIL");
        if (pay.isEmpty() || users.isEmpty()) { addSummary(sec + " - Result", "FAILED - payable type: " + rc.problems); return; }
        List<String> docs = tickIds(rc, sec, "DL.IsSelected", "a user", "15767", "15769");
        if (docs == null) return;
        if (!okStep(rc, sec, fDoc, "Doctor", v -> docs.stream().allMatch(d -> v.contains(d)), "both chosen users")) return;

        // 3) Location - one
        final String fLoc = "Location";
        if (!addFilter(rc, sec, fLoc)) return;
        if (!openValue(rc, sec, fLoc, "The Location popup opens (a checkbox per location)")) return;
        List<String> locs = tickIds(rc, sec, "loc.IsSelected", "the location", "1");
        if (locs == null) return;
        if (!okStep(rc, sec, fLoc, "Location", v -> locs.stream().allMatch(d -> v.contains(d)), "the chosen location")) return;

        // 4) Group - three
        final String fGrp = "Group";
        if (!addFilter(rc, sec, fGrp)) return;
        if (!openValue(rc, sec, fGrp, "The Group popup opens (a checkbox per group)")) return;
        List<String> grps = tickIds(rc, sec, "G.IsSelected", "a group", "1", "2", "18");
        if (grps == null) return;
        if (!okStep(rc, sec, fGrp, "Group", v -> grps.stream().allMatch(d -> v.contains(d)), "the chosen groups")) return;

        // 5) SubGroup - Group "Bed", three sub groups
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup("Bed");
        stepL(sec + " - Select the Group of the sub groups", "In the popup select the Group \"Bed\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() ? "Group = \"" + grp + "\"; " + rc.checkRows().size() + " sub group(s) listed" : "Problem - " + rc.problems, !grp.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty()) { addSummary(sec + " - Result", "FAILED - sub group's group: " + rc.problems); return; }
        List<String> subs = tickIds(rc, sec, "G.IsSelected", "a sub group", "10255", "10240", "10257");
        if (subs == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subs.stream().allMatch(d -> v.contains(d)), "the chosen sub groups")) return;

        // 6) Month
        final String fMon = "Month";
        if (!addFilter(rc, sec, fMon)) return;
        if (!openValue(rc, sec, fMon, "The Month popup opens")) return;
        String mo = rc.selectInPopup("RptColsole.Month", "Month", month);
        stepL(sec + " - Select the month", "In the popup select Month \"" + month + "\"", "The month is selected", !mo.isEmpty() ? "Month = \"" + mo + "\"" : "Problem - " + rc.problems, !mo.isEmpty() ? "PASS" : "FAIL");
        if (mo.isEmpty()) { addSummary(sec + " - Result", "FAILED - month: " + rc.problems); return; }
        if (!okStep(rc, sec, fMon, "Month", v -> v.contains(month), "the month")) return;

        // 7) Services - one
        final String fSvc = "Services";
        if (!addFilter(rc, sec, fSvc)) return;
        if (!openValue(rc, sec, fSvc, "The Services popup opens (a checkbox per service)")) return;
        List<String> svcs = tickIds(rc, sec, "G.IsSelected", "a service", "3");
        if (svcs == null) return;
        if (!okStep(rc, sec, fSvc, "ServiceName", v -> svcs.stream().allMatch(d -> v.contains(d)), "the chosen service")) return;

        // 8) Encounter Type - two
        final String fEnc = "Encounter Type";
        if (!addFilter(rc, sec, fEnc)) return;
        if (!openValue(rc, sec, fEnc, "The Encounter Type popup opens (a checkbox per type)")) return;
        List<String> encs = tickIds(rc, sec, "G.IsSelected", "an encounter type", "113", "107");
        if (encs == null) return;
        if (!okStep(rc, sec, fEnc, "EncounterTypeAll", v -> encs.stream().allMatch(d -> v.contains(d)), "the chosen encounter types")) return;

        // 9) Show Report -> another tab: no error, the right report, records
        LocalDate today = LocalDate.now();
        boolean related = showAndVerify(rc, sec, "DEPARTMENT\\s+ANALYSIS", "DEPARTMENT ANALYSIS DOCTOR", new LocalDate[]{today, today}, System.getProperty("ir.da.header", "Total"), System.getProperty("ir.da.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Department Analysis Doctor Report (Investigation Reports)");
        addSummary(sec + " - Year / Month", year + " / " + month);
        addSummary(sec + " - Doctor", loc + " / " + pay + " / " + docs);
        addSummary(sec + " - Location / Group", locs + " / " + grps);
        addSummary(sec + " - SubGroup", grp + " -> " + subs);
        addSummary(sec + " - Services / Encounter Type", svcs + " / " + encs);
        addSummary(sec + " - Result", related ? "Department Analysis Doctor report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Endoscope Census - Patient Type Report (recorded: From Date_To Date; Location; Show Report)
    // =====================================================================================================================================================

    private void endoscopeCensus(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Endoscope Census";

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Endoscope Census - Patient Type Report")) return;

        // 1) From Date_To Date: previous month x2, day 6 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) Location - one
        final String fLoc = "Location";
        if (!addFilter(rc, sec, fLoc)) return;
        if (!openValue(rc, sec, fLoc, "The Location popup opens (a checkbox per location)")) return;
        List<String> locs = tickIds(rc, sec, "loc.IsSelected", "the location", "1");
        if (locs == null) return;
        if (!okStep(rc, sec, fLoc, "Location", v -> locs.stream().allMatch(d -> v.contains(d)), "the chosen location")) return;

        // 3) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.ec.title", "ENDOSCOP\\w*\\s+CENSUS"), "ENDOSCOPY CENSUS", range, System.getProperty("ir.ec.header", "31\\s+TOTAL"), System.getProperty("ir.ec.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Endoscope Census - Patient Type Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - Location", String.valueOf(locs));
        addSummary(sec + " - Result", related ? "Endoscope Census report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Laboratory Census (recorded: From Date_To Date; SubGroup; Department; Test Name x2 (two different filters with the same name); Speciality; Ordering Doctor; Reporting Doctor (nothing ticked);
    //  Show Report twice)
    // =====================================================================================================================================================

    private void laboratoryCensus(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Laboratory Census";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Laboratory Census")) return;

        // 1) From Date_To Date: previous month x4, day 5 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 4, 5);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10236)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10236");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) Department - one (record id 5)
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Select Department popup opens (a checkbox per department)")) return;
        List<String> deps = tickIds(rc, sec, "G.IsSelected", "a department", "5");
        if (deps == null) return;
        if (!okStep(rc, sec, fDep, "Department", v -> deps.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen department")) return;

        // 4) Test Name - the Filter dropdown offers this name TWICE (two different filters): the first (value 611), then the second (value 783), a test ticked in each
        final String fTest = "Test Name";
        if (!addFilterN(rc, sec, fTest, 1)) return;
        if (!openValueN(rc, sec, fTest, 1, "The Select Test Name popup opens (a checkbox per test)")) return;
        List<String> tests1 = tickIds(rc, sec, "G.IsSelected", "a test (first Test Name filter)", "3319");
        if (tests1 == null) return;
        if (!okStepN(rc, sec, fTest, 1, "TestName", v -> tests1.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen test")) return;

        if (!addFilterN(rc, sec, fTest, 2)) return;
        if (!openValueN(rc, sec, fTest, 2, "The Select Test Name popup opens again (for the second Test Name filter)")) return;
        List<String> tests2 = tickIds(rc, sec, "G.IsSelected", "a test (second Test Name filter)", "7174");
        if (tests2 == null) return;
        if (!okStepN(rc, sec, fTest, 2, "TestName", v -> tests2.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen test")) return;

        // 5) Speciality - one (record id 38)
        final String fSpec = "Speciality";
        if (!addFilter(rc, sec, fSpec)) return;
        if (!openValue(rc, sec, fSpec, "The Select Speciality popup opens (a checkbox per speciality)")) return;
        List<String> specs = tickIds(rc, sec, "G.IsSelected", "a speciality", "38");
        if (specs == null) return;
        if (!okStep(rc, sec, fSpec, "Speciality", v -> specs.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen speciality")) return;

        // 6) Ordering Doctor: Location, Payable Type (Agent), one user (record id 15692)
        final String fOrd = "Ordering Doctor";
        if (!addFilter(rc, sec, fOrd)) return;
        if (!openValue(rc, sec, fOrd, "The Doctor popup opens (Location, Payable Type, Select User)")) return;
        String loc = rc.selectLocation("KPJ DAMANSARA SPECIALIST HOSPITAL");
        stepL(sec + " - Select the location", "Select Location \"KPJ DAMANSARA SPECIALIST HOSPITAL\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
        if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location: " + rc.problems); return; }
        String pay = rc.selectPayableType("Agent");
        List<String> users = rc.users();
        stepL(sec + " - Select the Payable Type", "Select Payable Type \"Agent\"", "The type is selected and its users are listed",
                !pay.isEmpty() && !users.isEmpty() ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed" : "Problem - " + rc.problems + " (" + users.size() + " user(s))", !pay.isEmpty() && !users.isEmpty() ? "PASS" : "FAIL");
        if (pay.isEmpty() || users.isEmpty()) { addSummary(sec + " - Result", "FAILED - payable type: " + rc.problems); return; }
        List<String> docs = tickIds(rc, sec, "DL.IsSelected", "a user", "15692");
        if (docs == null) return;
        if (!okStep(rc, sec, fOrd, "Doctor", v -> docs.stream().allMatch(d -> v.contains(d)), "the chosen user")) return;

        // 7) Reporting Doctor: the popup is opened and OK is clicked with NOTHING chosen (as recorded)
        final String fRep = "Reporting Doctor";
        if (!addFilter(rc, sec, fRep)) return;
        if (!openValue(rc, sec, fRep, "The Doctor popup opens (Location, Payable Type, Select User)")) return;
        boolean okRep = rc.clickOk("Doctor");
        String repVal = rc.filterValue(fRep);
        String repProblems = rc.problems.toString();
        boolean stuck = rc.modalOpen();
        stepL(sec + " - Click OK (" + fRep + ", nothing chosen)", "Click OK in the popup without choosing a location, a type or a user", "The popup closes (no doctor = every reporting doctor)",
                okRep ? "The popup closed; Filter Value = \"" + repVal + "\"" : "The popup did NOT close - " + repProblems, okRep ? "PASS" : "FAIL");
        if (stuck) rc.closePopup();   // so that Show Report can still be clicked and the report judged

        // 8) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.lc.title", "LABORATORY\\s+CENSUS"), "LABORATORY CENSUS", range, System.getProperty("ir.lc.header", "Revenue"), System.getProperty("ir.lc.footer", "(Total\\s+Printed\\s+Date|Printed\\s+Date|Page\\s+\\d)"));

        // 9) Show Report was clicked a second time in the recording: a second report tab opens
        final int tabsNow = rc.tabCount();
        boolean again = rc.clickShowReport();
        com.kpj.pages.Report_page.Report_Console.Investigation_Reports.ReportResult r2 = again ? rc.captureReport(tabsNow, 45000) : new com.kpj.pages.Report_page.Report_Console.Investigation_Reports.ReportResult();
        boolean ok2 = again && !r2.url.isEmpty() && r2.isPdf && r2.status < 400 && r2.bytes > 3000 && Pattern.compile(System.getProperty("ir.lc.title", "LABORATORY\\s+CENSUS"), Pattern.CASE_INSENSITIVE).matcher(r2.text == null ? "" : r2.text).find();
        final String act2 = !again ? "Problem - " + rc.problems : r2.url.isEmpty() ? "FAILS: no second report tab opened - " + r2.diagnostics
                : ok2 ? "A second report tab opened without an error: " + r2.diagnostics : "FAILS: the second report tab is not the Laboratory Census PDF - " + r2.diagnostics + (r2.errorText.isEmpty() ? "" : " - " + r2.errorText);
        if (r2.png != null && r2.png.length > 0) step(r2.png, sec + " - Click Show Report again", "Click Show Report (ShowReport($event)) a second time, as recorded", "A second report tab opens, again the Laboratory Census report without an error", act2, ok2 ? "PASS" : "FAIL");
        else stepL(sec + " - Click Show Report again", "Click Show Report (ShowReport($event)) a second time, as recorded", "A second report tab opens, again the Laboratory Census report without an error", act2, ok2 ? "PASS" : "FAIL");

        addSummary(sec + " - Report", "Laboratory Census (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup / Department", grp + " -> " + subTicked + " / " + deps);
        addSummary(sec + " - Test Name (1st / 2nd filter)", tests1 + " / " + tests2);
        addSummary(sec + " - Speciality", String.valueOf(specs));
        addSummary(sec + " - Ordering Doctor", loc + " / " + pay + " / " + docs);
        addSummary(sec + " - Reporting Doctor", "nothing chosen" + (repVal.isEmpty() ? "" : " (Filter Value \"" + repVal + "\")"));
        addSummary(sec + " - Result", related && ok2 ? "Laboratory Census report generated with records" : "See the failed step(s)");
    }

    // ---- a filter that the Filter dropdown offers more than once (same name, different filter): pick it by occurrence -------------------------------------------------

    /** Filter dropdown (the {@code occurrence}-th entry of this name) -> Add -> verify a new row of that name appears in the table. */
    private boolean addFilterN(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, int occurrence) {
        final String tag = filter + " #" + occurrence;
        String f = rc.selectFilter(filter, occurrence);
        stepL(sec + " - Select the filter " + tag, "In the Filter dropdown select the " + occurrence + (occurrence == 1 ? "st" : occurrence == 2 ? "nd" : "th") + " entry named \"" + filter + "\"", "The filter is selected",
                !f.isEmpty() ? "Filter = \"" + f + "\" - " + rc.lastFilterPick : "Problem - " + rc.problems, !f.isEmpty() ? "PASS" : "FAIL");
        if (f.isEmpty()) { addSummary(sec + " - Result", "FAILED - filter " + tag + ": " + rc.problems); return false; }
        final int before = rc.filterCount(filter);
        boolean a = rc.clickAdd(filter);
        stepL(sec + " - Click Add (" + tag + ")", "Click Add (AddFilter())", "The filter is added to the table", a ? "Add clicked" : "Problem - " + rc.problems, a ? "PASS" : "FAIL");
        final int now = rc.filterCount(filter);
        boolean in = now > before;
        stepL(sec + " - Verify the filter appears in the table (" + tag + ")", "In the Filter Name | Filter Value table look for a new row \"" + filter + "\"", "A new \"" + filter + "\" row appears",
                in ? "The table lists " + rc.filterRows().stream().map(r -> r[0]).toList() + " (" + now + " row(s) named \"" + filter + "\")" : "no new \"" + filter + "\" row (" + rc.filterRows().stream().map(r -> r[0]).toList() + ")", in ? "PASS" : "FAIL");
        if (!in) { addSummary(sec + " - Result", "FAILED - the " + tag + " filter was not added"); return false; }
        return true;
    }

    /** Add Value on the {@code occurrence}-th row of this name -> its popup. */
    private boolean openValueN(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, int occurrence, String expected) {
        boolean v = rc.clickAddValue(filter, occurrence);
        stepL(sec + " - Click Add Value (" + filter + " #" + occurrence + ")", "Click Add Value on the " + occurrence + (occurrence == 1 ? "st" : occurrence == 2 ? "nd" : "th") + " \"" + filter + "\" row", expected, v ? "The popup opened" : "Problem - " + rc.problems, v ? "PASS" : "FAIL");
        if (!v) { addSummary(sec + " - Result", "FAILED - popup of " + filter + " #" + occurrence + ": " + rc.problems); return false; }
        return true;
    }

    /** OK in the popup -> it closes and the {@code occurrence}-th row of this name shows what was chosen. */
    private boolean okStepN(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, int occurrence, String kind, java.util.function.Predicate<String> valueCheck, String what) {
        boolean ok = rc.clickOk(kind);
        String val = rc.filterValue(filter, occurrence);
        boolean good = ok && valueCheck.test(val);
        stepL(sec + " - Click OK (" + filter + " #" + occurrence + ")", "Click OK in the popup", "The popup closes and the filter's value shows " + what,
                good ? "Filter Value = \"" + val + "\"" : "Problem - " + rc.problems + " Filter Value = \"" + val + "\"", good ? "PASS" : "FAIL");
        if (!good) { addSummary(sec + " - Result", "FAILED - OK of " + filter + " #" + occurrence + ": " + rc.problems); return false; }
        return true;
    }

    // =====================================================================================================================================================
    //  Monthly Radiology Census Revenue (recorded: From Date_To Date; SubGroup; Department; Test Name; Ordering Doctor; Reporting Doctor; Modality; Show Report)
    // =====================================================================================================================================================

    private void monthlyRadiologyCensus(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Monthly Radiology Census";
        final String reportOption = "Monthly Radiology Census Revenue";
        final String groupName = System.getProperty("ir.mr.group", "Catherisation Laboratory");   // the app's own spelling

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, reportOption)) return;

        // 1) From Date_To Date: previous month x4, day 5 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 4, 5);
        if (range == null) return;

        // 2) SubGroup: Group "Catherisation Laboratory", one sub group (record id 32)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "32");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) Department - one (record id 2)
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Select Department popup opens (a checkbox per department)")) return;
        List<String> deps = tickIds(rc, sec, "G.IsSelected", "a department", "2");
        if (deps == null) return;
        if (!okStep(rc, sec, fDep, "Department", v -> deps.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen department")) return;

        // 4) Test Name - one (record id 2837)
        final String fTest = "Test Name";
        if (!addFilter(rc, sec, fTest)) return;
        if (!openValue(rc, sec, fTest, "The Select Test Name popup opens (a checkbox per test)")) return;
        List<String> tests = tickIds(rc, sec, "G.IsSelected", "a test", "2837");
        if (tests == null) return;
        if (!okStep(rc, sec, fTest, "TestName", v -> tests.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen test")) return;

        // 5) Ordering Doctor: Location, Payable Type Agent, one user (record id 15769)
        String ord = doctorFilter(rc, sec, "Ordering Doctor", "KPJ DAMANSARA SPECIALIST HOSPITAL", new String[]{"Agent"}, "15769");
        if (ord == null) return;

        // 6) Reporting Doctor: Location, Payable Type browsed Agent -> Broker -> Broker MWMYYLJU0 -> Consultant (as recorded), one user (record id 15647)
        String rep = doctorFilter(rc, sec, "Reporting Doctor", "KPJ DAMANSARA SPECIALIST HOSPITAL", new String[]{"Agent", "Broker", "Broker MWMYYLJU0", "Consultant"}, "15647");
        if (rep == null) return;

        // 7) Modality - one (record id 3)
        final String fMod = "Modality";
        if (!addFilter(rc, sec, fMod)) return;
        if (!openValue(rc, sec, fMod, "The Select Modality popup opens (a checkbox per modality)")) return;
        List<String> mods = tickIds(rc, sec, "G.IsSelected", "a modality", "3");
        if (mods == null) return;
        if (!okStep(rc, sec, fMod, "Modality", v -> mods.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen modality")) return;

        // 8) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.mr.title", "RADIOLOGY\\s+CENSUS"), "MONTHLY RADIOLOGY CENSUS", range, System.getProperty("ir.mr.header", "Modality\\s+Sub-?group"), System.getProperty("ir.mr.footer", "(Printed\\s+Date|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", reportOption + " (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup / Department", grp + " -> " + subTicked + " / " + deps);
        addSummary(sec + " - Test Name", String.valueOf(tests));
        addSummary(sec + " - Ordering Doctor", ord);
        addSummary(sec + " - Reporting Doctor", rep);
        addSummary(sec + " - Modality", String.valueOf(mods));
        addSummary(sec + " - Result", related ? "Monthly Radiology Census report generated with records" : "See the failed step(s)");
    }

    /**
     * The Doctor popup of an Ordering Doctor / Reporting Doctor filter: Add the filter, Add Value, Location, the Payable Type(s) (each chosen in turn, as a recording browsed them - only the last must list users),
     * one user ticked by record id, OK. Returns "Location / types / users" or null when a step failed.
     */
    private String doctorFilter(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String filter, String location, String[] payTypes, String userId) {
        if (!addFilter(rc, sec, filter)) return null;
        if (!openValue(rc, sec, filter, "The Doctor popup opens (Location, Payable Type, Select User)")) return null;
        String loc;
        if (location == null) {
            // as a recording did for a second doctor filter: Location / Payable Type are NOT touched, the popup keeps what the previous doctor popup left in them; the users listed are used as they are
            loc = "(unchanged)";
            List<String> listed = rc.users();
            stepL(sec + " - List the users (" + filter + ")", "Look at the users the popup lists (Location and Payable Type left as they are)", "The popup lists users",
                    !listed.isEmpty() ? listed.size() + " user(s) listed, first: " + listed.get(0) : "No user is listed " + rc.problems, !listed.isEmpty() ? "PASS" : "FAIL");
            if (listed.isEmpty()) { addSummary(sec + " - Result", "FAILED - no user listed for " + filter); return null; }
        } else {
            loc = rc.selectLocation(location);
            stepL(sec + " - Select the location (" + filter + ")", "Select Location \"" + location + "\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
            if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location of " + filter + ": " + rc.problems); return null; }
        }
        for (int i = 0; i < payTypes.length; i++) {
            final boolean last = i == payTypes.length - 1;
            String pay = rc.selectPayableType(payTypes[i]);
            List<String> users = rc.users();
            boolean ok = !pay.isEmpty() && (!last || !users.isEmpty());
            stepL(sec + " - Select the Payable Type " + payTypes[i] + " (" + filter + ")", "Select Payable Type \"" + payTypes[i] + "\"" + (last ? "" : " (browsed on the way, as recorded)"),
                    last ? "The type is selected and its users are listed" : "The type is selected",
                    ok ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed" : "Problem - " + rc.problems + " (" + users.size() + " user(s))", ok ? "PASS" : "FAIL");
            if (!ok) { addSummary(sec + " - Result", "FAILED - payable type of " + filter + ": " + rc.problems); return null; }
        }
        List<String> docs = tickIds(rc, sec, "DL.IsSelected", "a user (" + filter + ")", userId);
        if (docs == null) return null;
        if (!okStep(rc, sec, filter, "Doctor", v -> docs.stream().allMatch(d -> v.contains(d)), "the chosen user")) return null;
        return loc + " / " + (payTypes.length == 0 ? "(unchanged)" : String.join(" -> ", payTypes)) + " / " + docs;
    }

    // =====================================================================================================================================================
    //  Path Audit (recorded: From Date_To Date; SubGroup (Group browsed Consignments -> Bed -> Catherisation Laboratory, one sub group); Show Report)
    // =====================================================================================================================================================

    private void pathAudit(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Path Audit";

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Path Audit")) return;

        // 1) From Date_To Date: previous month x4, day 5 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 4, 5);
        if (range == null) return;

        // 2) SubGroup: the Group dropdown is browsed Consignments -> Bed -> Catherisation Laboratory (as recorded), one sub group ticked (record id 33)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        final String[] groups = {"Consignments", "Bed", "Catherisation Laboratory"};
        String grp = "";
        for (int i = 0; i < groups.length; i++) {
            final boolean last = i == groups.length - 1;
            grp = rc.selectGroup(groups[i]);
            List<String> subs = rc.checkRows();
            boolean ok = !grp.isEmpty() && (!last || !subs.isEmpty());
            stepL(sec + " - Select the Group " + groups[i], "In the popup select the Group \"" + groups[i] + "\"" + (last ? "" : " (browsed on the way, as recorded)"),
                    last ? "The group is selected and its sub groups are listed" : "The group is selected",
                    ok ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed" + (subs.isEmpty() ? "" : ", first: " + subs.get(0)) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", ok ? "PASS" : "FAIL");
            if (!ok) { addSummary(sec + " - Result", "FAILED - group " + groups[i] + ": " + rc.problems); return; }
        }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "33");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.pa.title", "AUDIT\\s+REPORT\\s+FOR\\s+PATHOLOGY"), "PATH AUDIT (Audit Report For Pathology)", range, System.getProperty("ir.pa.header", "Old\\s*Value\\s+New\\s*Value"), System.getProperty("ir.pa.footer", "(Printed\\s+Date|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Path Audit (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - Result", related ? "Path Audit report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Pathology Test Count (recorded: From Date_To Date (From picked twice); SubGroup / Bed; From Time_To Time (hour arrows); Show Report)
    // =====================================================================================================================================================

    private void pathologyTestCount(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Pathology Test Count";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Pathology Test Count")) return;

        // 1) From Date_To Date: as recorded the From Date was first picked 3 months back, day 2, then changed to 4 months back, day 5
        final String fDate = "From Date_To Date";
        if (!addFilter(rc, sec, fDate)) return;
        if (!openValue(rc, sec, fDate, "The From Date / To Date popup opens")) return;
        final LocalDate to = LocalDate.parse(rc.toDate(), DMY);
        final LocalDate first = to.withDayOfMonth(1).minusMonths(3).withDayOfMonth(2);
        final LocalDate want = to.withDayOfMonth(1).minusMonths(4).withDayOfMonth(5);
        String f1 = rc.pickFromDate(first);
        boolean ok1 = f1.equals(first.format(DMY));
        stepL(sec + " - Enter the From Date", "Pick From Date: go back 3 month(s) with the previous-month arrow and click day 2 = " + first.format(DMY), "From Date is " + first.format(DMY),
                ok1 ? "From " + f1 + ", To " + rc.toDate() : "Problem - From shows \"" + f1 + "\" instead of " + first.format(DMY) + " " + rc.problems, ok1 ? "PASS" : "FAIL");
        if (!ok1) { addSummary(sec + " - Result", "FAILED - From Date: " + rc.problems); return; }
        String f2 = rc.pickFromDate(want);
        boolean ok2 = f2.equals(want.format(DMY));
        stepL(sec + " - Change the From Date", "Open the From Date again, go back one more month and click day 5 = " + want.format(DMY), "From Date is " + want.format(DMY),
                ok2 ? "From " + f2 + ", To " + rc.toDate() : "Problem - From shows \"" + f2 + "\" instead of " + want.format(DMY) + " " + rc.problems, ok2 ? "PASS" : "FAIL");
        if (!ok2) { addSummary(sec + " - Result", "FAILED - From Date: " + rc.problems); return; }
        if (!okStep(rc, sec, fDate, "FromToDate", v -> v.contains(want.format(DMY)) && v.contains(to.format(DMY)), "the date range")) return;
        final LocalDate[] range = {want, to};

        // 2) SubGroup: Group "Bed", one sub group (record id 10247)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10247");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) From Time_To Time: as recorded the hour arrow of the From picker is clicked once and the one of the To picker three times (the pickers start at the current time)
        final String fTime = "From Time_To Time";
        if (!addFilter(rc, sec, fTime)) return;
        if (!openValue(rc, sec, fTime, "The From Time / To Time popup opens")) return;
        String fromBox = rc.incrementTimeHours(0, 1);
        stepL(sec + " - Set the From Time", "Open the From Time picker and click its hour arrow once (the picker starts at the current time)", "From Time shows the current time + 1 hour",
                !fromBox.isEmpty() ? "From Time = " + fromBox : "Problem - " + rc.problems, !fromBox.isEmpty() ? "PASS" : "FAIL");
        if (fromBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - From Time: " + rc.problems); return; }
        String toBox = rc.incrementTimeHours(1, 3);
        stepL(sec + " - Set the To Time", "Open the To Time picker and click its hour arrow 3 times (the picker starts at the current time)", "To Time shows the current time + 3 hours",
                !toBox.isEmpty() ? "To Time = " + toBox : "Problem - " + rc.problems, !toBox.isEmpty() ? "PASS" : "FAIL");
        if (toBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - To Time: " + rc.problems); return; }
        if (!okStep(rc, sec, fTime, "FromToTime", v -> v.contains(fromBox) && v.contains(toBox), "the time range")) return;

        // 4) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.pt.title", "PATHOLOGY\\s+TEST\\s+COUNT"), "PATHOLOGY TEST COUNT", range, System.getProperty("ir.pt.header", "Test\\s+Name\\s+Work\\s+Order\\s+Count"), System.getProperty("ir.pt.footer", "(Page\\s+\\d+\\s+of\\s+\\d+|Print\\s+By)"));

        addSummary(sec + " - Report", "Pathology Test Count (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY) + " (From first picked " + first.format(DMY) + ")");
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - From / To Time", fromBox + " - " + toBox);
        addSummary(sec + " - Result", related ? "Pathology Test Count report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Pathology Test Revenue Report (recorded: From Date_To Date; SubGroup / Bed; From Time_To Time (hour arrows); Show Report)
    // =====================================================================================================================================================

    private void pathologyTestRevenue(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Pathology Test Revenue";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Pathology Test Revenue Report")) return;

        // 1) From Date_To Date: previous month x3, day 2 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 3, 2);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10253)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10253");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) From Time_To Time: as recorded the hour arrow of the From picker is clicked once and the one of the To picker three times (the pickers start at the current time)
        final String fTime = "From Time_To Time";
        if (!addFilter(rc, sec, fTime)) return;
        if (!openValue(rc, sec, fTime, "The From Time / To Time popup opens")) return;
        String fromBox = rc.incrementTimeHours(0, 1);
        stepL(sec + " - Set the From Time", "Open the From Time picker and click its hour arrow once (the picker starts at the current time)", "From Time shows the current time + 1 hour",
                !fromBox.isEmpty() ? "From Time = " + fromBox : "Problem - " + rc.problems, !fromBox.isEmpty() ? "PASS" : "FAIL");
        if (fromBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - From Time: " + rc.problems); return; }
        String toBox = rc.incrementTimeHours(1, 3);
        stepL(sec + " - Set the To Time", "Open the To Time picker and click its hour arrow 3 times (the picker starts at the current time)", "To Time shows the current time + 3 hours",
                !toBox.isEmpty() ? "To Time = " + toBox : "Problem - " + rc.problems, !toBox.isEmpty() ? "PASS" : "FAIL");
        if (toBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - To Time: " + rc.problems); return; }
        if (!okStep(rc, sec, fTime, "FromToTime", v -> v.contains(fromBox) && v.contains(toBox), "the time range")) return;

        // 4) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.pr.title", "PATHOLOGY\\s+TEST\\s+REVENUE"), "PATHOLOGY TEST REVENUE", range, System.getProperty("ir.pr.header", "Total\\s+Count\\s+Test\\s+Count\\s+Total\\s+Amount"), System.getProperty("ir.pr.footer", "(Printed\\s+Date|Page\\s+\\d+\\s+of\\s+\\d+)"));

        addSummary(sec + " - Report", "Pathology Test Revenue Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - From / To Time", fromBox + " - " + toBox);
        addSummary(sec + " - Result", related ? "Pathology Test Revenue report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Radiology Test Count Report (recorded: From Date_To Date; SubGroup / Bed; From Time_To Time (hour arrows); Show Report -> the report tab shows a SERVER ERROR)
    // =====================================================================================================================================================

    private void radiologyTestCount(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Radiology Test Count";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Radiology Test Count Report")) return;

        // 1) From Date_To Date: previous month x2, day 6 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10255)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10255");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) From Time_To Time: as recorded the hour arrow of the From picker is clicked once and the one of the To picker three times (the pickers start at the current time)
        final String fTime = "From Time_To Time";
        if (!addFilter(rc, sec, fTime)) return;
        if (!openValue(rc, sec, fTime, "The From Time / To Time popup opens")) return;
        String fromBox = rc.incrementTimeHours(0, 1);
        stepL(sec + " - Set the From Time", "Open the From Time picker and click its hour arrow once (the picker starts at the current time)", "From Time shows the current time + 1 hour",
                !fromBox.isEmpty() ? "From Time = " + fromBox : "Problem - " + rc.problems, !fromBox.isEmpty() ? "PASS" : "FAIL");
        if (fromBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - From Time: " + rc.problems); return; }
        String toBox = rc.incrementTimeHours(1, 3);
        stepL(sec + " - Set the To Time", "Open the To Time picker and click its hour arrow 3 times (the picker starts at the current time)", "To Time shows the current time + 3 hours",
                !toBox.isEmpty() ? "To Time = " + toBox : "Problem - " + rc.problems, !toBox.isEmpty() ? "PASS" : "FAIL");
        if (toBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - To Time: " + rc.problems); return; }
        if (!okStep(rc, sec, fTime, "FromToTime", v -> v.contains(fromBox) && v.contains(toBox), "the time range")) return;

        // 4) Show Report -> another tab: it must open, WITHOUT A SERVER ERROR (HTTP 4xx/5xx or an error page = FAIL), be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.rt.title", "RADIOLOG\\w*\\s+TEST\\s+COUNT"), "RADIOLOGY TEST COUNT", range, System.getProperty("ir.rt.header", "ZZZ_NO_HEADING_YET"), System.getProperty("ir.rt.footer", "(Printed\\s+Date|Page\\s+\\d+\\s+of\\s+\\d+)"));

        addSummary(sec + " - Report", "Radiology Test Count Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - From / To Time", fromBox + " - " + toBox);
        addSummary(sec + " - Result", related ? "Radiology Test Count report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Radiology Test Revenue Report (recorded: From Date_To Date; SubGroup / Bed; From Time_To Time (hour arrows); Show Report -> the report tab shows a SERVER ERROR)
    // =====================================================================================================================================================

    private void radiologyTestRevenue(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Radiology Test Revenue";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Radiology Test Revenue Report")) return;

        // 1) From Date_To Date: previous month x3, day 2 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 3, 2);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10255)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10255");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) From Time_To Time: as recorded the hour arrow of the From picker is clicked once and the one of the To picker three times (the pickers start at the current time)
        final String fTime = "From Time_To Time";
        if (!addFilter(rc, sec, fTime)) return;
        if (!openValue(rc, sec, fTime, "The From Time / To Time popup opens")) return;
        String fromBox = rc.incrementTimeHours(0, 1);
        stepL(sec + " - Set the From Time", "Open the From Time picker and click its hour arrow once (the picker starts at the current time)", "From Time shows the current time + 1 hour",
                !fromBox.isEmpty() ? "From Time = " + fromBox : "Problem - " + rc.problems, !fromBox.isEmpty() ? "PASS" : "FAIL");
        if (fromBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - From Time: " + rc.problems); return; }
        String toBox = rc.incrementTimeHours(1, 3);
        stepL(sec + " - Set the To Time", "Open the To Time picker and click its hour arrow 3 times (the picker starts at the current time)", "To Time shows the current time + 3 hours",
                !toBox.isEmpty() ? "To Time = " + toBox : "Problem - " + rc.problems, !toBox.isEmpty() ? "PASS" : "FAIL");
        if (toBox.isEmpty()) { addSummary(sec + " - Result", "FAILED - To Time: " + rc.problems); return; }
        if (!okStep(rc, sec, fTime, "FromToTime", v -> v.contains(fromBox) && v.contains(toBox), "the time range")) return;

        // 4) Show Report -> another tab: it must open, WITHOUT A SERVER ERROR (HTTP 4xx/5xx or an error page = FAIL), be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.rr.title", "RADIOLOG\\w*\\s+TEST\\s+REVENUE"), "RADIOLOGY TEST REVENUE", range, System.getProperty("ir.rr.header", "ZZZ_NO_HEADING_YET"), System.getProperty("ir.rr.footer", "(Printed\\s+Date|Page\\s+\\d+\\s+of\\s+\\d+)"));

        addSummary(sec + " - Report", "Radiology Test Revenue Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - From / To Time", fromBox + " - " + toBox);
        addSummary(sec + " - Result", related ? "Radiology Test Revenue report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Radiotherapy Monthly Detailed Census (recorded: Location; Group; SubGroup / Bed; Blood Group; From Date_To Date; Show Report)
    // =====================================================================================================================================================

    private void radiotherapyMonthlyDetailedCensus(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Radiotherapy Monthly Detailed Census";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Radiotherapy Monthly Detailed Census")) return;

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

        // 2) Group: the Select Group popup (checkbox G.IsSelected, record id 1)
        final String fGroup = "Group";
        if (!addFilter(rc, sec, fGroup)) return;
        if (!openValue(rc, sec, fGroup, "The Select Group popup opens (a checkbox per group)")) return;
        List<String> groups = rc.checkRows();
        stepL(sec + " - List the groups", "Look at the groups the popup offers", "The popup lists the groups",
                !groups.isEmpty() ? groups.size() + " group(s) listed, first: " + groups.get(0) : "No group is listed " + rc.problems, !groups.isEmpty() ? "PASS" : "FAIL");
        if (groups.isEmpty()) { addSummary(sec + " - Result", "FAILED - no group listed"); return; }
        List<String> groupTicked = tickIds(rc, sec, "G.IsSelected", "a group", "1");
        if (groupTicked == null) return;
        if (!okStep(rc, sec, fGroup, "Group", v -> groupTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen group")) return;

        // 3) SubGroup: Group "Bed", one sub group (record id 10236)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group of the sub groups", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - sub group's group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10236");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 4) Blood Group: the Blood Groups popup (checkbox G.IsSelected, record id 2)
        final String fBlood = "Blood Group";
        if (!addFilter(rc, sec, fBlood)) return;
        if (!openValue(rc, sec, fBlood, "The Blood Groups popup opens (a checkbox per blood group)")) return;
        List<String> bloods = rc.checkRows();
        stepL(sec + " - List the blood groups", "Look at the blood groups the popup offers", "The popup lists the blood groups",
                !bloods.isEmpty() ? bloods.size() + " blood group(s) listed, first: " + bloods.get(0) : "No blood group is listed " + rc.problems, !bloods.isEmpty() ? "PASS" : "FAIL");
        if (bloods.isEmpty()) { addSummary(sec + " - Result", "FAILED - no blood group listed"); return; }
        List<String> bloodTicked = tickIds(rc, sec, "G.IsSelected", "a blood group", "2");
        if (bloodTicked == null) return;
        if (!okStep(rc, sec, fBlood, "BloodGroup", v -> bloodTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen blood group")) return;

        // 5) From Date_To Date: previous month x3, day 2 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 3, 2);
        if (range == null) return;

        // 6) Show Report -> another tab: it must open, WITHOUT AN ERROR, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.rm.title", "RADIOTHERAPY\\s+MONTHLY\\s+DETAILED\\s+CENSUS"), "RADIOTHERAPY MONTHLY DETAILED CENSUS", range, System.getProperty("ir.rm.header", "Payment\\s+Method\\s+Payer\\s+Remark"), System.getProperty("ir.rm.footer", "Printed\\s+Date"));

        addSummary(sec + " - Report", "Radiotherapy Monthly Detailed Census (Investigation Reports)");
        addSummary(sec + " - Location", String.valueOf(locTicked));
        addSummary(sec + " - Group", String.valueOf(groupTicked));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - Blood Group", String.valueOf(bloodTicked));
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - Result", related ? "Radiotherapy Monthly Detailed Census report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Revenue Report (recorded: From Date_To Date; SubGroup / Bed; Department; Test Name; Ordering Doctor; Reporting Doctor; Modality; Show Report)
    // =====================================================================================================================================================

    private void revenueReport(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Revenue Report";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Revenue Report")) return;

        // 1) From Date_To Date: previous month x2, day 6 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10236)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10236");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) Department - one (record id 1)
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Select Department popup opens (a checkbox per department)")) return;
        List<String> deps = tickIds(rc, sec, "G.IsSelected", "a department", "1");
        if (deps == null) return;
        if (!okStep(rc, sec, fDep, "Department", v -> deps.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen department")) return;

        // 4) Test Name - one (record id 7174)
        final String fTest = "Test Name";
        if (!addFilter(rc, sec, fTest)) return;
        if (!openValue(rc, sec, fTest, "The Select Test Name popup opens (a checkbox per test)")) return;
        List<String> tests = tickIds(rc, sec, "G.IsSelected", "a test", "7174");
        if (tests == null) return;
        if (!okStep(rc, sec, fTest, "TestName", v -> tests.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen test")) return;

        // 5) Ordering Doctor: Location, Payable Type Agent, one user (record id 15692)
        String ord = doctorFilter(rc, sec, "Ordering Doctor", "KPJ DAMANSARA SPECIALIST HOSPITAL", new String[]{"Agent"}, "15692");
        if (ord == null) return;

        // 6) Reporting Doctor: as recorded NO Location / Payable Type is chosen - the popup keeps the previous doctor's - one user ticked (record id 15767)
        String rep = doctorFilter(rc, sec, "Reporting Doctor", null, new String[0], "15767");
        if (rep == null) return;

        // 7) Modality - one (record id 3)
        final String fMod = "Modality";
        if (!addFilter(rc, sec, fMod)) return;
        if (!openValue(rc, sec, fMod, "The Select Modality popup opens (a checkbox per modality)")) return;
        List<String> mods = tickIds(rc, sec, "G.IsSelected", "a modality", "3");
        if (mods == null) return;
        if (!okStep(rc, sec, fMod, "Modality", v -> mods.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen modality")) return;

        // 8) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.rv.title", "DAILY\\s+RADIOLOGY\\s+CENSUS\\s+REVENUE"), "DAILY RADIOLOGY CENSUS REVENUE", range, System.getProperty("ir.rv.header", "Modality\\s+Sub-?group"), System.getProperty("ir.rv.footer", "(Printed\\s+Date|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Revenue Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup / Department", grp + " -> " + subTicked + " / " + deps);
        addSummary(sec + " - Test Name", String.valueOf(tests));
        addSummary(sec + " - Ordering Doctor", ord);
        addSummary(sec + " - Reporting Doctor", rep);
        addSummary(sec + " - Modality", String.valueOf(mods));
        addSummary(sec + " - Result", related ? "Revenue Report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Samplewise Organisms Isolated (recorded: From Date_To Date; SubGroup / Bed; Show Report)
    // =====================================================================================================================================================

    private void samplewiseOrganismsIsolated(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Samplewise Organisms Isolated";
        final String groupName = System.getProperty("ir.group", "Bed");

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Samplewise Organisms Isolated")) return;

        // 1) From Date_To Date: previous month x3, day 2 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 3, 2);
        if (range == null) return;

        // 2) SubGroup: Group "Bed", one sub group (record id 10257)
        final String fSub = "SubGroup";
        if (!addFilter(rc, sec, fSub)) return;
        if (!openValue(rc, sec, fSub, "The Select Sub Group popup opens (Group dropdown + a checkbox per sub group)")) return;
        String grp = rc.selectGroup(groupName);
        List<String> subs = rc.checkRows();
        stepL(sec + " - Select the Group", "In the popup select the Group \"" + groupName + "\"", "The group is selected and its sub groups are listed",
                !grp.isEmpty() && !subs.isEmpty() ? "Group = \"" + grp + "\"; " + subs.size() + " sub group(s) listed, first: " + subs.get(0) : "Problem - " + rc.problems + " (" + subs.size() + " sub group(s))", !grp.isEmpty() && !subs.isEmpty() ? "PASS" : "FAIL");
        if (grp.isEmpty() || subs.isEmpty()) { addSummary(sec + " - Result", "FAILED - group: " + rc.problems); return; }
        List<String> subTicked = tickIds(rc, sec, "G.IsSelected", "a sub group", "10257");
        if (subTicked == null) return;
        if (!okStep(rc, sec, fSub, "SubGroup", v -> subTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen sub group")) return;

        // 3) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.so.title", "SAMPLEWISE\\s+ORGANISMS\\s+ISOLATED"), "SAMPLEWISE ORGANISMS ISOLATED", range, System.getProperty("ir.so.header", "Organism\\s+Name"),System.getProperty("ir.so.footer", "(Printed\\s+Date|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Samplewise Organisms Isolated (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - SubGroup", grp + " -> " + subTicked);
        addSummary(sec + " - Result", related ? "Samplewise Organisms Isolated report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  STAT Report (recorded: From Date_To Date; Visit Type; Department x4; Show Report -> the console answers "No Data Available!" and opens NO report)
    // =====================================================================================================================================================

    private void statReport(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "STAT Report";

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "STAT Report")) return;

        // 1) From Date_To Date: previous month x2, day 6 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) Visit Type - one (record id 2)
        final String fVisit = "Visit Type";
        if (!addFilter(rc, sec, fVisit)) return;
        if (!openValue(rc, sec, fVisit, "The Select Visit Type popup opens (a checkbox per visit type)")) return;
        List<String> visits = tickIds(rc, sec, "G.IsSelected", "a visit type", "2");
        if (visits == null) return;
        if (!okStep(rc, sec, fVisit, "VisitType", v -> visits.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen visit type")) return;

        // 3) Department - four (record ids 1, 13, 25, 28)
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Select Department popup opens (a checkbox per department)")) return;
        List<String> deps = tickIds(rc, sec, "G.IsSelected", "a department", "1", "13", "25", "28");
        if (deps == null) return;
        if (!okStep(rc, sec, fDep, "Department", v -> deps.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen departments")) return;

        // 4) Show Report -> as recorded the console may answer "No Data Available!" and open nothing: that is a FAIL (the report must open, without an error, be this report and list records)
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.st.title", "STAT\\s+REPORT"), "STAT REPORT", range, System.getProperty("ir.st.header", "Test\\s*Name\\s*Ward/?\\s*Department"), System.getProperty("ir.st.footer", "(Printed\\s+Date|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "STAT Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - Visit Type", String.valueOf(visits));
        addSummary(sec + " - Department", String.valueOf(deps));
        addSummary(sec + " - Result", related ? "STAT Report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Doctor Wise OPD & IPD Report (recorded: From Date_To Date; Doctor; Location; OPD/IPD/All; Show Report)
    // =====================================================================================================================================================

    private void doctorWiseOpdIpd(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Doctor Wise OPD & IPD";

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Doctor Wise OPD & IPD Report")) return;

        // 1) From Date_To Date: previous month x2, day 6 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 2, 6);
        if (range == null) return;

        // 2) Doctor: Location, Payable Type (Agent), one user
        final String fDoc = "Doctor";
        if (!addFilter(rc, sec, fDoc)) return;
        if (!openValue(rc, sec, fDoc, "The Doctor popup opens (Location, Payable Type, Select User)")) return;
        String loc = rc.selectLocation("KPJ DAMANSARA SPECIALIST HOSPITAL");
        stepL(sec + " - Select the location", "Select Location \"KPJ DAMANSARA SPECIALIST HOSPITAL\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
        if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location: " + rc.problems); return; }
        String pay = rc.selectPayableType("Agent");
        List<String> users = rc.users();
        stepL(sec + " - Select the Payable Type", "Select Payable Type \"Agent\"", "The type is selected and its users are listed",
                !pay.isEmpty() && !users.isEmpty() ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed" : "Problem - " + rc.problems + " (" + users.size() + " user(s))", !pay.isEmpty() && !users.isEmpty() ? "PASS" : "FAIL");
        if (pay.isEmpty() || users.isEmpty()) { addSummary(sec + " - Result", "FAILED - payable type: " + rc.problems); return; }
        List<String> docs = tickIds(rc, sec, "DL.IsSelected", "a user", "15692");
        if (docs == null) return;
        if (!okStep(rc, sec, fDoc, "Doctor", v -> docs.stream().allMatch(d -> v.contains(d)), "the chosen user")) return;

        // 3) Location - one
        final String fLoc = "Location";
        if (!addFilter(rc, sec, fLoc)) return;
        if (!openValue(rc, sec, fLoc, "The Location popup opens (a checkbox per location)")) return;
        List<String> locs = tickIds(rc, sec, "loc.IsSelected", "the location", "1");
        if (locs == null) return;
        if (!okStep(rc, sec, fLoc, "Location", v -> locs.stream().allMatch(d -> v.contains(d)), "the chosen location")) return;

        // 4) OPD/IPD/All: OPD first, then ALL (as recorded)
        final String fOpd = "OPD/IPD/All";
        if (!addFilter(rc, sec, fOpd)) return;
        if (!openValue(rc, sec, fOpd, "The OPD / IPD / EXT / ALL popup opens (radio buttons)")) return;
        String r1 = rc.chooseRadio("OPD");
        stepL(sec + " - Choose OPD", "Choose \"OPD\" of " + rc.radioOptions(), "The option is selected", !r1.isEmpty() ? "Chosen \"" + r1 + "\"" : "Problem - " + rc.problems, !r1.isEmpty() ? "PASS" : "FAIL");
        if (r1.isEmpty()) { addSummary(sec + " - Result", "FAILED - option OPD: " + rc.problems); return; }
        String r2 = rc.chooseRadio("ALL");
        stepL(sec + " - Choose ALL", "Choose \"ALL\" (instead of OPD)", "The option is selected", !r2.isEmpty() ? "Chosen \"" + r2 + "\"" : "Problem - " + rc.problems, !r2.isEmpty() ? "PASS" : "FAIL");
        if (r2.isEmpty()) { addSummary(sec + " - Result", "FAILED - option ALL: " + rc.problems); return; }
        if (!okStep(rc, sec, fOpd, "OPDIPDALL", v -> !v.isEmpty(), "a value")) return;
        final String opdValue = rc.filterValue(fOpd);

        // 5) Show Report -> another tab: it must open, without an error, be this report and list records
        boolean related = showAndVerify(rc, sec, System.getProperty("ir.dw.title", "DOCTOR\\s*WISE"), "DOCTOR WISE OPD & IPD", range, System.getProperty("ir.dw.header", "IPD\\s+Discharges"), System.getProperty("ir.dw.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));

        addSummary(sec + " - Report", "Doctor Wise OPD & IPD Report (Investigation Reports)");
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY));
        addSummary(sec + " - Doctor", loc + " / " + pay + " / " + docs);
        addSummary(sec + " - Location / OPD/IPD/All", locs + " / " + opdValue);
        addSummary(sec + " - Result", related ? "Doctor Wise OPD & IPD report generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  Department Analysis Report (recorded: Doctor; From Date_To Date; Department; Service; Show Report -> "No Data Available!"; wider dates; Show Report again)
    // =====================================================================================================================================================

    private void departmentAnalysis(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc) {
        final String sec = "Department Analysis";

        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, "Department Analysis Report")) return;

        // 1) Doctor: Location, Payable Type (Agent), three users
        final String fDoc = "Doctor";
        if (!addFilter(rc, sec, fDoc)) return;
        if (!openValue(rc, sec, fDoc, "The Doctor popup opens (Location, Payable Type, Select User)")) return;
        String loc = rc.selectLocation("KPJ DAMANSARA SPECIALIST HOSPITAL");
        stepL(sec + " - Select the location", "Select Location \"KPJ DAMANSARA SPECIALIST HOSPITAL\"", "The location is selected", !loc.isEmpty() ? "Location = \"" + loc + "\"" : "Problem - " + rc.problems, !loc.isEmpty() ? "PASS" : "FAIL");
        if (loc.isEmpty()) { addSummary(sec + " - Result", "FAILED - location: " + rc.problems); return; }
        String pay = rc.selectPayableType("Agent");
        List<String> users = rc.users();
        stepL(sec + " - Select the Payable Type", "Select Payable Type \"Agent\"", "The type is selected and its users are listed",
                !pay.isEmpty() && !users.isEmpty() ? "Payable Type = \"" + pay + "\"; " + users.size() + " user(s) listed" : "Problem - " + rc.problems + " (" + users.size() + " user(s))", !pay.isEmpty() && !users.isEmpty() ? "PASS" : "FAIL");
        if (pay.isEmpty() || users.isEmpty()) { addSummary(sec + " - Result", "FAILED - payable type: " + rc.problems); return; }
        List<String> docs = tickIds(rc, sec, "DL.IsSelected", "a user", "15692", "15767", "15769");
        if (docs == null) return;
        if (!okStep(rc, sec, fDoc, "Doctor", v -> docs.stream().allMatch(d -> v.contains(d)), "the chosen users")) return;

        // 2) From Date_To Date: previous month x3, day 2 (as recorded)
        LocalDate[] range = dateFilter(rc, sec, 3, 2);
        if (range == null) return;

        // 3) Department: ticking two is refused ("Can select only one Group at a time!"), so the second is unticked
        final String fDep = "Department";
        if (!addFilter(rc, sec, fDep)) return;
        if (!openValue(rc, sec, fDep, "The Department popup opens (a checkbox per department)")) return;
        String d1 = rc.tickRowById("G.IsSelected", "1");
        stepL(sec + " - Tick a department", "Tick the checkbox of a department (recorded record id 1)", "The department is ticked", !d1.isEmpty() ? "Ticked \"" + d1 + "\"" : "Problem - " + rc.problems, !d1.isEmpty() ? "PASS" : "FAIL");
        if (d1.isEmpty()) { addSummary(sec + " - Result", "FAILED - department: " + rc.problems); return; }
        String d2 = rc.tickRowById("G.IsSelected", "2");
        stepL(sec + " - Tick a second department", "Tick the checkbox of a second department (recorded record id 2)", "The second department is ticked", !d2.isEmpty() ? "Ticked \"" + d2 + "\"" : "Problem - " + rc.problems, !d2.isEmpty() ? "PASS" : "FAIL");
        if (d2.isEmpty()) { addSummary(sec + " - Result", "FAILED - second department: " + rc.problems); return; }
        String warn = rc.clickOkAndToast("Group");
        boolean refused = warn.toLowerCase().contains("only one");
        stepL(sec + " - Click OK with two departments", "Click OK in the popup with two departments ticked", "The screen refuses: \"Can select only one Group at a time!\" and the popup stays open",
                refused && rc.modalOpen() ? "Warning: \"" + warn + "\"; the popup is still open" : "Unexpected - toast \"" + warn + "\", popup " + (rc.modalOpen() ? "open" : "closed") + " " + rc.problems, refused && rc.modalOpen() ? "PASS" : "FAIL");
        if (!(refused && rc.modalOpen())) { addSummary(sec + " - Result", "FAILED - the two-department refusal: " + warn); return; }
        String un = rc.untickRowById("G.IsSelected", "2");
        stepL(sec + " - Untick the second department", "Untick the checkbox of the second department", "Only the first department stays ticked", !un.isEmpty() ? "Unticked \"" + un + "\"" : "Problem - " + rc.problems, !un.isEmpty() ? "PASS" : "FAIL");
        if (un.isEmpty()) { addSummary(sec + " - Result", "FAILED - untick: " + rc.problems); return; }
        if (!okStep(rc, sec, fDep, "Group", v -> v.contains(d1), "the chosen department")) return;

        // 4) Service: All Services
        final String fSvc = "Service";
        if (!addFilter(rc, sec, fSvc)) return;
        if (!openValue(rc, sec, fSvc, "The Service popup opens (service type options)")) return;
        List<String> kinds = rc.radioLabelsOf("RptColsole.ServiceType");
        String kind = rc.chooseRadioByLabel("RptColsole.ServiceType", "All Services");
        stepL(sec + " - Choose All Services", "Choose \"All Services\" of " + kinds, "The option is selected", !kind.isEmpty() ? "Chosen \"All Services\" (value " + kind + ")" : "Problem - " + rc.problems, !kind.isEmpty() ? "PASS" : "FAIL");
        if (kind.isEmpty()) { addSummary(sec + " - Result", "FAILED - service type: " + rc.problems); return; }
        if (!okStep(rc, sec, fSvc, "ServiceType", v -> !v.isEmpty(), "a value")) return;

        // 5) Show Report -> the report must open without an error and list records
        boolean related = showAndVerify(rc, sec, "DEPARTMENT\\s+ANALYSIS", "DEPARTMENT ANALYSIS", range, System.getProperty("ir.dr.header", "Total"), System.getProperty("ir.dr.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));

        // 6) as recorded, when nothing opened: a wider date range (previous month x3 again from where the picker stays, day 3), then Show Report again
        LocalDate[] range2 = range;
        if (!related) {
            final String retry = sec + " (retry, wider dates)";
            boolean av = rc.clickAddValue("From Date_To Date");
            stepL(retry + " - Click Add Value (From Date_To Date)", "Click Add Value on the date filter's row", "The From Date / To Date popup opens", av ? "The popup opened" : "Problem - " + rc.problems, av ? "PASS" : "FAIL");
            if (av) {
                LocalDate to = LocalDate.parse(rc.toDate(), DMY);
                LocalDate wider = range[0].withDayOfMonth(1).minusMonths(3).withDayOfMonth(3);
                String from = rc.pickFromDate(wider);
                boolean ok = from.equals(wider.format(DMY));
                stepL(retry + " - Enter an earlier From Date", "Pick From Date " + wider.format(DMY) + " (previous month x3 from " + range[0].format(DMY) + ", day 3)", "From Date is " + wider.format(DMY),
                        ok ? "From " + from + ", To " + rc.toDate() : "Problem - From shows \"" + from + "\" " + rc.problems, ok ? "PASS" : "FAIL");
                if (ok && okStep(rc, retry, "From Date_To Date", "FromToDate", v -> v.contains(wider.format(DMY)) && v.contains(to.format(DMY)), "the wider date range")) {
                    range2 = new LocalDate[]{wider, to};
                    related = showAndVerify(rc, retry, "DEPARTMENT\\s+ANALYSIS", "DEPARTMENT ANALYSIS", range2, System.getProperty("ir.dr.header", "Total"), System.getProperty("ir.dr.footer", "(Printed|Print\\s+By|Page\\s+\\d)"));
                }
            }
        }

        addSummary(sec + " - Report", "Department Analysis Report (Investigation Reports)");
        addSummary(sec + " - Doctor", loc + " / " + pay + " / " + docs);
        addSummary(sec + " - From / To Date", range[0].format(DMY) + " - " + range[1].format(DMY) + (range2 != range ? " (retry " + range2[0].format(DMY) + " - " + range2[1].format(DMY) + ")" : ""));
        addSummary(sec + " - Department / Service", d1 + " / All Services");
        addSummary(sec + " - Result", related ? "Department Analysis report generated with records" : "See the failed step(s)");
    }

    /** Tick the rows with these record ids in the open popup, one step each (a recorded id that is not listed is replaced by the row at the same position). Returns the names ticked, null when a step failed. */
    private List<String> tickIds(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, String ngModel, String what, String... ids) {
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
    private LocalDate[] dateFilter(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, int months) { return dateFilter(rc, sec, months, 0); }

    /**
     * Same, with a fixed day: {@code day > 0} = go back {@code months} month(s) from the month of To Date and click that day of the month (as a recording did: previous month twice, day 6);
     * {@code day == 0} = the same day of the month, {@code months} before To Date.
     */
    private LocalDate[] dateFilter(com.kpj.pages.Report_page.Report_Console.Investigation_Reports rc, String sec, int months, int day) {
        final String fDate = "From Date_To Date";
        if (!addFilter(rc, sec, fDate)) return null;
        if (!openValue(rc, sec, fDate, "The From Date / To Date popup opens")) return null;
        LocalDate to = LocalDate.parse(rc.toDate(), DMY);
        LocalDate wantFrom = day > 0 ? to.withDayOfMonth(1).minusMonths(months).withDayOfMonth(day) : to.minusMonths(months);
        String from = rc.pickFromDate(wantFrom);
        boolean fromOk = from.equals(wantFrom.format(DMY));
        final String how = day > 0 ? "go back " + months + " month(s) with the previous-month arrow and click day " + day + " = " + wantFrom.format(DMY) : months + " month(s) before To Date (" + to.format(DMY) + ") = " + wantFrom.format(DMY);
        stepL(sec + " - Enter the From Date", "Pick From Date: " + how, "From Date is " + (day > 0 ? wantFrom.format(DMY) : months + " month(s) before To Date"),
                fromOk ? "From " + from + ", To " + rc.toDate() + (day > 0 ? "" : " (" + months + " month(s) apart)") : "Problem - From shows \"" + from + "\" instead of " + wantFrom.format(DMY) + " " + rc.problems, fromOk ? "PASS" : "FAIL");
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
                    (rr.diagnostics.isEmpty() ? "no report tab opened" : rr.diagnostics) + (rr.isPdf ? ". The screenshot is the report's first page rendered from the PDF itself." : ". The screenshot is the report tab itself (an error page, not a PDF)."), generated ? "PASS" : "FAIL");
        } else {
            stepL(sec + " - Verify the report is generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", rr.diagnostics.isEmpty() ? "no report tab opened" : rr.diagnostics, generated ? "PASS" : "FAIL");
        }

        String txt = rr.text == null ? "" : rr.text;

        // The report must not be an ERROR: HTTP 4xx/5xx, an HTML page instead of the PDF, or error wording in what it prints.
        boolean errored = rr.url.isEmpty() || rr.status >= 400 || !rr.isPdf || Pattern.compile("failed to retrieve|error in file|exception|database vendor code", Pattern.CASE_INSENSITIVE).matcher(txt).find();
        final String errAct = rr.url.isEmpty() ? "FAILS: the report did NOT open - " + (rr.diagnostics.isEmpty() ? "no report tab opened" : rr.diagnostics)
                : errored ? "FAILS: the report shows an ERROR instead of data - HTTP " + rr.status + (rr.errorText.isEmpty() ? "" : " - " + (rr.errorText.length() > 420 ? rr.errorText.substring(0, 420) + " ..." : rr.errorText))
                : "The report opened without an error (HTTP " + rr.status + ", a PDF of " + rr.pdfPages + " page(s))";
        if (rr.png != null && rr.png.length > 0) step(rr.png, sec + " - Verify the report has no error", "Look at the report tab for an error message", "The report opens without an error", errAct, errored ? "FAIL" : "PASS");
        else stepL(sec + " - Verify the report has no error", "Look at the report tab for an error message", "The report opens without an error", errAct, errored ? "FAIL" : "PASS");
        addSummary(sec + " - Report error", errored ? (rr.errorText.isEmpty() ? "yes (no report tab)" : "YES - HTTP " + rr.status + " - " + (rr.errorText.length() > 300 ? rr.errorText.substring(0, 300) + " ..." : rr.errorText)) : "none");

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
        final String reportName = sec.replaceAll(" \\(retry.*\\)$", "");   // "Department Analysis (retry, wider dates)" -> "Department Analysis"
        if (rr.png != null && rr.png.length > 0) step(rr.png, sec + " - Verify the report is the " + reportName + " report", relDesc, relExp, relAct, related ? "PASS" : "FAIL");
        else stepL(sec + " - Verify the report is the " + reportName + " report", relDesc, relExp, relAct, related ? "PASS" : "FAIL");
        // Does the report list any record? Its table is empty when nothing sits between the last column heading and the footer. (A manual check with a 6-month window showed no records either.)
        java.util.regex.Matcher hm = Pattern.compile(lastHeaderRegex, Pattern.CASE_INSENSITIVE).matcher(txt);
        int hEnd = -1;
        while (hm.find()) hEnd = hm.end();
        int fStart = -1;
        if (hEnd >= 0) { java.util.regex.Matcher fm = Pattern.compile(footerRegex, Pattern.CASE_INSENSITIVE).matcher(txt); if (fm.find(hEnd)) fStart = fm.start(); }
        String between = hEnd < 0 ? "" : txt.substring(hEnd, fStart >= 0 ? fStart : txt.length()).replaceAll("(?i)page\\s*\\d+\\s*(of|/)\\s*\\d+", " ")
                .replaceAll("(?<![\\d/])\\d{1,3}\\s*/\\s*(?!\\d)", " ")   // a bare page counter such as "1 /" (a date like 23/11/2026 has digits after the slash and is kept)
                .replaceAll("\\s+", " ").trim();
        // a record row always carries words (patient name, department, test ...); a bare number left after the headings is only the page counter / an empty first serial number
        boolean records = generated && hEnd >= 0 && between.replaceAll("[^A-Za-z]", "").length() >= 3;
        final String recAct = !generated ? "No readable report to judge (" + rr.diagnostics + ")"
                : hEnd < 0 ? "FAILS: the report's column headings (\"" + lastHeaderRegex + "\") are not in its text - no record table is printed at all, so it lists NO RECORD (some reports print their table only when they have data; if a report WITH data ever shows this, the heading pattern needs updating)"
                : records ? "The report lists record data after its column headings: \"" + (between.length() > 160 ? between.substring(0, 160) + " ..." : between) + "\""
                : "FAILS: the report is generated but lists NO RECORD - there is nothing between its last column heading and its footer (only the title, the filters, the headings and the footer are printed). "
                  + "No record matches the chosen filters (or the report returns none).";
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
