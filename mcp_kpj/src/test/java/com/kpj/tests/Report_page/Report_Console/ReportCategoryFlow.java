package com.kpj.tests.Report_page.Report_Console;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Report_page.Report_Console.ReportConsole;
import com.microsoft.playwright.Page;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Report &gt; Report Console — the test flow shared by every report category (MIS Report, Miscellaneous Reports, …). Each category
 * has its own small test class (its own test report); the steps live here once. Abstract, so JUnit runs only the categories.
 *
 * <ol>
 *   <li>Report → Report Console → click the category button.</li>
 *   <li>Read ALL reports from the Reports dropdown at run time (new reports are picked up automatically).</li>
 *   <li>For each report = a SECTION of steps named "&lt;report&gt; - &lt;action&gt;" (like the Investigation Reports test): open the
 *       category tab, select the report, then for EVERY filter in the Filter dropdown: select it → <b>Add</b> → verify it is in the
 *       table → <b>Add Value</b> → choose values (a tick list: several random entries; a dropdown: a random one; dates: two months
 *       before today → today) → <b>OK</b>.</li>
 *   <li><b>Show Report</b> → the report opens in a new tab. Evidence = the console with the filled filter table, then EVERY page of
 *       the report, joined into one image. The title on the report is checked against the selected name — the same meaning is a
 *       match ("Date Wise" = "Date-wise"; "IPD New &amp; Follow Up Report" = "IPD New &amp; Follow Up Patient Count Report").</li>
 *   <li>PASS: the report opened with its title. FAIL: a filter could not get a value, the app said "No Data Available" instead of
 *       opening the report, no report opened, no title, or an unrelated title.</li>
 *   <li>Close the tab, Clear, next report.</li>
 * </ol>
 * Read-only — creates no data. Run one report only with {@code -Dreport.pin="NAME"}.
 */
public abstract class ReportCategoryFlow extends DevHisBase {

    private static final int MAX_PAGES = 50;

    private int pass, fail;
    private final List<String> failed = new ArrayList<>(), noData = new ArrayList<>(), issues = new ArrayList<>(), notes = new ArrayList<>(), wider = new ArrayList<>();

    protected ReportCategoryFlow(String reportId) { super(reportId); }

    /** The category button on the right of the console, e.g. "MIS Report". */
    protected abstract String category();

    /** Short id put in front of each step, e.g. "MIS" → "MIS-01". */
    protected abstract String idPrefix();

    private Path evidence() {
        return Paths.get(System.getProperty("user.dir"), "test-output", "report-console-" + category().replaceAll("[^A-Za-z0-9]+", "-"));
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        String cat = category();
        meta("Report - Report Console - " + cat, "Report > Report Console > " + cat,
                "Every " + cat + " report: every filter gets values (random; dates = last two months), Show Report, all pages captured, report title checked. Read-only.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        ReportConsole rc = new ReportConsole(page);
        boolean on = rc.navigateViaMenu();
        step(page, "Open Report Console", "Click Report -> Report Console", "The Report Console screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Report Console not reached"); return; }

        boolean opened = rc.openCategory(cat);
        List<String> reports = rc.reportNames();
        step(page, "Open " + cat, "Click the " + cat + " button; read the Reports dropdown", "The " + cat + " reports are listed",
                reports.size() + " report(s): " + String.join(" | ", reports), opened && !reports.isEmpty() ? "PASS" : "FAIL");
        if (reports.isEmpty()) { addSummary("Result", "FAILED - no " + cat + " reports listed"); return; }

        String pin = System.getProperty("report.pin");
        int n = 0;
        for (String report : reports) {
            n++;
            if (pin != null && !pin.equalsIgnoreCase(report)) continue;
            testReport(rc, report, String.format("%s-%02d", idPrefix(), n));
        }

        addSummary("Category", cat);
        addSummary("Reports tested", String.valueOf(pass + fail) + " of " + reports.size());
        addSummary("Passed / Failed", pass + " / " + fail);
        addSummary("Failed reports", failed.isEmpty() ? "none" : String.join(" ; ", failed));
        addSummary("Report issues found", issues.isEmpty() ? "none" : String.join(" ; ", issues));
        addSummary("Titles worded differently (PASS)", notes.isEmpty() ? "none" : String.join(" ; ", notes));
        addSummary("Reports with \"No Data Available\" (FAIL)", noData.isEmpty() ? "none" : String.join(" ; ", noData));
        addSummary("Date range used", java.time.LocalDate.now().minusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " - " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (retryFrom() == null ? "" : "; on \"No Data Available\" a second try " + retryFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - today"));
        if (retryFrom() != null) addSummary("Passed only on the second (wider) try", wider.isEmpty() ? "none" : String.join(" ; ", wider));
        addSummary("Evidence folder", evidence().toString());
    }

    /** A step with a screenshot of the console (40 s limit — the console is a heavy screen). */
    private void shotStep(String name, String desc, String expected, String actual, boolean ok) {
        byte[] png = null;
        try { png = page.screenshot(new Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception ignore) { }
        if (png != null) step(png, name, desc, expected, actual, ok ? "PASS" : "FAIL");
        else step(name, desc, expected, actual, ok ? "PASS" : "FAIL");
    }

    /**
     * Start date of a second try when the report says "No Data Available" (new random values, dates from this day to today);
     * null = no second try (the default). A category may override it, e.g. Patient Report → 01/01/2020.
     */
    protected java.time.LocalDate retryFrom() { return null; }

    /**
     * One report of the category = a section of steps, "&lt;report&gt; - &lt;action&gt;" (the same layout as the Investigation Reports test):
     * open the category tab, select the report, then for every filter: select it, Add, verify it is in the table, Add Value,
     * choose the value(s), OK; then Show Report, verify the report is generated in another tab (every page captured), verify the
     * report is the selected report (its title). The first failing step ends the section. When the report says "No Data
     * Available" and {@link #retryFrom()} gives a date, the section is run once more with that wider date range.
     */
    private void testReport(ReportConsole rc, String report, String id) {
        StringBuilder filters = new StringBuilder();
        String result = "";
        Page[] tab = {null};
        boolean[] nd = {false};
        System.out.println("  " + id + " " + report);
        java.time.LocalDate wide = retryFrom();
        try {
            rc.fromDate = null;
            result = runOnce(rc, report, "", filters, tab, nd, id);
            if (nd[0] && wide != null) {
                rc.closeTab(tab[0]);
                tab[0] = null;
                rc.fromDate = wide;
                String tag = " (try 2: " + wide.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - today)";
                filters.append("| try 2: ");
                result = runOnce(rc, report, tag, filters, tab, nd, id);
                if (result.startsWith("PASS")) wider.add(report + tag);
            }
            if (nd[0]) noData.add(report);
        } catch (Exception e) {
            String em = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
            step(page.screenshot(), report + " - Unexpected error", "-", "The report is tested", em.substring(0, Math.min(250, em.length())), "FAIL");
            result = "FAILED - error: " + em.substring(0, Math.min(120, em.length()));
        } finally {
            rc.fromDate = null;
            rc.closeTab(tab[0]);
            if (result.startsWith("PASS")) pass++; else { fail++; failed.add(report); }
            addSummary(report + " - Filters", filters.length() == 0 ? "-" : filters.toString());
            addSummary(report + " - Result", result.isEmpty() ? "FAILED" : result);
            if (!rc.onScreen()) { rc.navigateViaMenu(); }   // keep the console usable for the next report
        }
    }

    /** One try of a report (see {@link #testReport}). {@code tag} is added to every step name; nd[0] = the app said "No Data Available". */
    private String runOnce(ReportConsole rc, String report, String tag, StringBuilder filters, Page[] tabOut, boolean[] nd, String id) {
        final String sec = report;
        final String cat = category();
        nd[0] = false;
        String dates = (rc.fromDate != null ? rc.fromDate : java.time.LocalDate.now().minusMonths(2)).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - today";

        // fresh start: Clear, open the category tab again
        rc.clear();
        boolean opened = rc.openCategory(cat);
        List<String> listed = rc.reportNames();
        shotStep(sec + " - Open the " + cat + " tab" + tag, "Click the \"" + cat + "\" button of the Report Console", "The Reports dropdown lists the " + cat + " reports",
                opened ? "Reports offered (" + listed.size() + "): " + listed : "Problem - the " + cat + " reports were not listed", opened);
        if (!opened) return "FAILED - " + cat + " tab";

        String why = rc.selectReport(report);
        List<String> fl = why.isEmpty() ? rc.filterNames() : new ArrayList<>();
        shotStep(sec + " - Select the report" + tag, "In the Reports dropdown select \"" + report + "\"", "The report is selected and its filters are offered",
                why.isEmpty() ? "Reports = \"" + report + "\"; Filter dropdown offers " + fl : "Problem - " + why, why.isEmpty());
        if (!why.isEmpty()) return "FAILED - report: " + why;

        for (String f : fl) {
            String sel = rc.selectFilter(f);
            shotStep(sec + " - Select the filter " + f + tag, "In the Filter dropdown select \"" + f + "\"", "The filter is selected",
                    sel.isEmpty() ? "Filter = \"" + rc.filterShown() + "\"" : "Problem - " + sel, sel.isEmpty());
            if (!sel.isEmpty()) return "FAILED - filter " + f + ": " + sel;

            String add = rc.clickAdd();
            shotStep(sec + " - Click Add (" + f + ")" + tag, "Click Add (AddFilter())", "The filter is added to the table",
                    add.isEmpty() ? "Add clicked" : "Problem - " + add, add.isEmpty());
            List<String> inTable = rc.filtersInTable();
            boolean in = inTable.contains(f);
            shotStep(sec + " - Verify the filter appears in the table (" + f + ")" + tag, "In the Filter Name | Filter Value table look for \"" + f + "\"", "The added filter appears",
                    in ? "The table lists " + inTable : "\"" + f + "\" is not in the table (" + inTable + ")", in);
            if (!in) return "FAILED - the " + f + " filter was not added" + (add.isEmpty() ? "" : " (" + add + ")");

            ReportConsole.ValueResult v = rc.openAddValue(f);
            shotStep(sec + " - Click Add Value (" + f + ")" + tag, "Click Add Value on the filter's row", "The " + f + " popup opens",
                    v.problem.isEmpty() ? "The popup opened (" + v.popup + ")" : "Problem - " + v.problem, v.problem.isEmpty());
            if (!v.problem.isEmpty()) { issues.add(report + ": " + f + " - " + v.problem); return "FAILED - popup of " + f + ": " + v.problem; }

            rc.chooseValue(v);
            shotStep(sec + " - Choose the value (" + f + ")" + tag, "In the popup choose the value(s) (lists: several at random; dates: " + dates + ")",
                    "The value(s) are chosen", v.problem.isEmpty() ? "Chosen \"" + v.chosen + "\"" : "Problem - " + v.problem, v.problem.isEmpty());
            if (!v.problem.isEmpty()) { issues.add(report + ": " + f + " - " + v.problem); return "FAILED - value of " + f + ": " + v.problem; }

            rc.clickOk(v, f);
            boolean good = v.ok();
            shotStep(sec + " - Click OK (" + f + ")" + tag, "Click OK / Add in the popup", "The popup closes and the filter's value shows the chosen value(s)",
                    good ? "Filter Value = \"" + v.value + "\"" : "Problem - " + v.problem + " Filter Value = \"" + v.value + "\"", good);
            if (!good) { issues.add(report + ": " + f + " - " + v.problem); return "FAILED - OK of " + f + ": " + v.problem; }
            filters.append(f).append(" = ").append(v.value).append("; ");
        }

        // Show Report -> another tab
        byte[] filled = page.screenshot();   // the console with every filter and its value(s)
        Page tab = rc.showReport(90000);
        tabOut[0] = tab;
        String msg = rc.messages();
        step(filled, sec + " - Click Show Report" + tag, "Click Show Report (ShowReport($event))", "The report is requested",
                "Show Report clicked (" + fl.size() + " filter(s) set)", "PASS");

        if (tab == null) {
            nd[0] = msg.toLowerCase().contains("no data");
            String why2 = nd[0] ? "the screen says \"" + msg + "\" - the report did NOT open"
                    : !rc.downloaded.isEmpty() ? "a file was downloaded (" + rc.downloaded + ") instead of opening the report"
                    : "no report tab opened" + (msg.isEmpty() ? "" : " - the screen says \"" + msg + "\"");
            step(page.screenshot(), sec + " - Verify the report is generated in another tab" + tag, "Check a report opened in another tab", "A non-blank report is generated",
                    why2, "FAIL");
            saveEvidence(id, report + tag, page.screenshot());
            return "FAILED - " + why2;
        }
        String err = rc.serverError(tab);
        boolean drawn = err.isEmpty() && rc.waitForViewer(tab, 90000);
        if (err.isEmpty() && !drawn) err = rc.serverError(tab);
        if (!err.isEmpty()) {
            try { tab.setViewportSize(1920, 1075); } catch (Exception ignore) { }
            byte[] errShot = tab.screenshot();
            issues.add(report + ": server error - " + err);
            step(errShot, sec + " - Verify the report is generated in another tab" + tag, "Check a report opened in another tab", "A non-blank report is generated",
                    "A SERVER ERROR page opened instead of the report: \"" + err + "\"", "FAIL");
            saveEvidence(id, report + tag, ReportConsole.stitch(java.util.Arrays.asList(filled, errShot)));
            return "FAILED - server error (" + err + ")";
        }
        String box = rc.pageBox(tab);
        String text = rc.reportText(tab);   // page 1 — the title is printed on the first page only
        List<byte[]> pages = drawn ? rc.capturePages(tab, MAX_PAGES) : java.util.Collections.singletonList(tab.screenshot());
        byte[] allPages = ReportConsole.stitch(pages);
        step(allPages, sec + " - Verify the report is generated in another tab" + tag, "Check a report opened in another tab; capture every page", "A non-blank report is generated",
                drawn ? "The report opened in the viewer (page box \"" + box + "\"); " + pages.size() + " page(s) captured - the screenshot shows every page"
                      : "The report tab opened but the viewer did not finish drawing", drawn ? "PASS" : "FAIL");
        saveEvidence(id, report + tag, ReportConsole.stitch(java.util.Arrays.asList(filled, allPages)));
        if (!drawn) return "FAILED - the viewer did not finish drawing";

        String title = titleFound(text, report);
        String printed = printedTitle(text);
        // same meaning = a match: the printed title may be shorter or worded slightly differently — only NO title, or an unrelated one, fails
        boolean sameMeaning = title == null && !printed.isEmpty() && sameReport(printed, report);
        boolean titleOk = title != null || sameMeaning;
        step(pages.get(0), sec + " - Verify the report is the " + report + " report" + tag, "Read the report's title", "The report is the \"" + report + "\" report",
                title != null ? "The report says \"" + title + "\" - matches"
                        : sameMeaning ? "The report says \"" + printed + "\" - the same report, worded slightly differently"
                        : printed.isEmpty() ? "NO TITLE is printed on the report" : "The report says \"" + printed + "\" - a DIFFERENT report",
                titleOk ? "PASS" : "FAIL");
        if (!titleOk) issues.add(report + ": " + (printed.isEmpty() ? "no title printed" : "printed title \"" + printed + "\""));
        if (sameMeaning) notes.add(report + ": printed as \"" + printed + "\"");
        return titleOk ? "PASS - report generated (" + pages.size() + " page(s)), title \"" + (title != null ? title : printed) + "\"" + tag
                       : "FAILED - " + (printed.isEmpty() ? "no title" : "title \"" + printed + "\"");
    }

    /**
     * The report's title as printed, when it matches the selected name: letters and digits compared without spaces, hyphens or
     * capitals ("&" = "and"), so "Date-wise Death Report" matches "Date Wise Death Report". Null when no match.
     */
    private static String titleFound(String reportText, String selected) {
        String want = norm(selected);
        if (want.isEmpty()) return null;
        if (!norm(reportText).contains(want)) return null;
        String[] words = reportText.split(" ");   // give back the printed words that matched
        for (int i = 0; i < words.length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < words.length && norm(sb.toString()).length() < want.length(); j++) sb.append(j > i ? " " : "").append(words[j]);
            if (norm(sb.toString()).equals(want)) return sb.toString();
        }
        return selected;
    }

    /** The title as printed at the top of the report: the words up to the first "Report" (the viewer's own "Main Report" tab skipped). "" when none. */
    private static String printedTitle(String reportText) {
        String t = reportText.replaceFirst("^\\s*Main Report\\s*", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.{3,90}?\\bReport)\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        if (!m.find()) return "";
        String title = m.group(1).trim();
        return title.matches(".*(\\d{2}/\\d{2}/\\d{4}|Date\\s*:).*") ? "" : title;   // ran into the header (dates / labels): no real title
    }

    /**
     * True when the printed title is the same report worded differently: every word of the printed title (except "report") is in
     * the selected name, e.g. "IPD New & Follow Up Report" for "IPD New & Follow Up Patient Count Report".
     */
    private static boolean sameReport(String printed, String selected) {
        java.util.Set<String> sel = words(selected);
        java.util.Set<String> pr = words(printed);
        pr.remove("report");
        return !pr.isEmpty() && sel.containsAll(pr);
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String w : s.toLowerCase().replace("&", " and ").split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replace("&", "and").replaceAll("[^a-z0-9]", "");
    }

    private void saveEvidence(String id, String report, byte[] png) {
        if (png == null) return;
        try {
            Files.createDirectories(evidence());
            Files.write(evidence().resolve(id + "_" + report.replaceAll("[^A-Za-z0-9]+", "_") + ".png"), png);
        } catch (Exception ignore) { }
    }
}
