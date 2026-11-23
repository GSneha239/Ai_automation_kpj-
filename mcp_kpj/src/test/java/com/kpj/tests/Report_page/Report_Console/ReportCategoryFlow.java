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
 *   <li>For each report = ONE test step: select it; for EVERY filter in the Filter dropdown: select it → <b>Add</b> →
 *       <b>AddValue</b> → pick values (a tick list: several random entries; a dropdown: a random one; dates: two months before
 *       today → today) → Add.</li>
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
    private final List<String> failed = new ArrayList<>(), noData = new ArrayList<>(), issues = new ArrayList<>(), notes = new ArrayList<>();

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
                + " - " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        addSummary("Evidence folder", evidence().toString());
    }

    /** One report of the category = one test step. */
    private void testReport(ReportConsole rc, String report, String id) {
        StringBuilder filters = new StringBuilder();
        List<String> problems = new ArrayList<>();
        Page tab = null;
        byte[] shot = null;
        String actual, status;
        try {
            // fresh start for every report: Clear, then pick the report
            rc.clear();
            String why = rc.selectReport(report);
            if (!why.isEmpty()) problems.add("could not select the report: " + why);
            List<String> fl = why.isEmpty() ? rc.filterNames() : new ArrayList<>();
            for (String f : fl) {
                String add = rc.addFilter(f);
                if (!add.isEmpty()) { problems.add(f + ": not added (" + add + ")"); filters.append(f).append(" = NOT ADDED; "); continue; }
                ReportConsole.ValueResult v = rc.addValue(f);
                filters.append(f).append(" = ").append(v.value.isEmpty() ? "(none)" : v.value).append("; ");
                if (!v.ok()) problems.add(f + ": " + v.problem + (v.popup.isEmpty() ? "" : " [" + v.popup + "]"));
            }
            if (fl.isEmpty() && why.isEmpty()) filters.append("(the report has no filters) ");

            if (!problems.isEmpty()) {
                shot = page.screenshot();
                actual = "Filters: " + filters + "| NOT RUN - " + String.join("; ", problems);
                status = "FAIL";
            } else {
                byte[] filled = page.screenshot();   // evidence: the filter table with every filter and its values, before Show Report
                tab = rc.showReport(90000);
                if (tab == null) {
                    String msg = rc.messages();
                    shot = page.screenshot();
                    if (msg.toLowerCase().contains("no data")) {
                        actual = "Filters: " + filters + "| Show Report did NOT open the report - the screen says \"" + msg + "\"";
                        status = "FAIL";   // team rule: "No Data Available" instead of a report is a failure
                        noData.add(report);
                    } else if (!rc.downloaded.isEmpty()) {
                        actual = "Filters: " + filters + "| Show Report downloaded a file (" + rc.downloaded + ") instead of opening the report";
                        status = "FAIL";
                    } else {
                        actual = "Filters: " + filters + "| Show Report did not open a report" + (msg.isEmpty() ? "" : " - screen says: " + msg);
                        status = "FAIL";
                    }
                } else if (!rc.serverError(tab).isEmpty() || (!rc.waitForViewer(tab, 90000) && !rc.serverError(tab).isEmpty())) {
                    String err = rc.serverError(tab);   // the report crashed on the server
                    try { tab.setViewportSize(1920, 1075); } catch (Exception ignore) { }
                    shot = ReportConsole.stitch(java.util.Arrays.asList(filled, tab.screenshot()));
                    actual = "Filters: " + filters + "| Show Report opened a SERVER ERROR page instead of the report: \"" + err + "\"";
                    status = "FAIL";
                    issues.add(report + ": server error - " + err);
                } else {
                    boolean drawn = rc.waitForViewer(tab, 1000) || rc.waitForViewer(tab, 90000);
                    String box = rc.pageBox(tab);
                    String text = rc.reportText(tab);   // read on page 1 — the title is printed on the first page only
                    List<byte[]> pages = drawn ? rc.capturePages(tab, MAX_PAGES) : java.util.Collections.singletonList(tab.screenshot());
                    List<byte[]> evidenceShots = new ArrayList<>();
                    evidenceShots.add(filled);          // 1st: the console with all filters added
                    evidenceShots.addAll(pages);        // then every page of the report
                    shot = ReportConsole.stitch(evidenceShots);
                    String title = titleFound(text, report);
                    String printed = printedTitle(text);
                    // same meaning = a match: the printed title may be shorter or worded slightly differently — only a report with NO
                    // title, or an unrelated one, fails
                    boolean sameMeaning = title == null && !printed.isEmpty() && sameReport(printed, report);
                    boolean titleOk = title != null || sameMeaning;
                    actual = "Filters: " + filters + "| Report opened (" + (drawn ? "page box \"" + box + "\", " + pages.size() + " page(s) captured" : "viewer did not finish drawing") + ")"
                            + " | title on the report: " + (title != null ? "\"" + title + "\" - matches"
                                : sameMeaning ? "\"" + printed + "\" - same report (worded slightly differently from \"" + report + "\")"
                                : printed.isEmpty() ? "NO TITLE printed on the report" : "\"" + printed + "\" - a DIFFERENT report title (selected \"" + report + "\")");
                    status = drawn && titleOk ? "PASS" : "FAIL";
                    if (!titleOk) issues.add(report + ": " + (printed.isEmpty() ? "no title printed" : "printed title \"" + printed + "\""));
                    if (sameMeaning) notes.add(report + ": printed as \"" + printed + "\"");
                }
            }
        } catch (Exception e) {
            String em = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
            actual = "Filters: " + filters + "| ERROR - " + em.substring(0, Math.min(250, em.length()));
            status = "FAIL";
            try { shot = page.screenshot(); } catch (Exception ignore) { }
        } finally {
            rc.closeTab(tab);
        }

        if ("PASS".equals(status)) pass++; else { fail++; failed.add(report); }
        saveEvidence(id, report, shot);
        step(shot != null ? shot : page.screenshot(), id + " " + report,
                category() + " -> select \"" + report + "\"; add every filter with values (random; dates = last two months); Show Report; capture every page",
                "The report opens with the title \"" + report + "\"",
                actual, status);

        // keep the console usable for the next report
        if (!rc.onScreen()) { rc.navigateViaMenu(); rc.openCategory(category()); }
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
