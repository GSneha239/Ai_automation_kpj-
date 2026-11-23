package com.kpj.tests.Report_page.Report_Console;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Report_page.ReportConsolePage;
import com.kpj.pages.Report_page.ReportConsolePage.Fill;
import com.kpj.pages.Report_page.ReportConsolePage.Selection;
import com.kpj.pages.Report_page.ReportConsolePage.Shown;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * READ-ONLY suite for one Report Console category. For every report the live screen lists:
 * page load + filters, mandatory filters filled, Show Report, report grid/data. One HTML row per case.
 * Status BLOCKED = NO DATA (report ran, nothing for the filters). Reasons tagged [APPLICATION DEFECT] /
 * [BACKEND/API] / [AUTOMATION] / [NO DATA] / [PERMISSION] / [MANUAL].
 * Switches: -Dreports.only=regex  -Dreports.limit=N  -Dreports.from / -Dreports.to (dd/MM/yyyy).
 */
public abstract class ReportConsoleSuite extends DevHisBase {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String prefix, categoryBtn, categoryName;
    protected ReportConsolePage rc;
    protected String fromDate, toDate;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<String> noData = new ArrayList<>(), failed = new ArrayList<>();
    private List<String> listed = new ArrayList<>();                       // reports the category lists
    private final Map<String, String> outcome = new LinkedHashMap<>();     // report -> worst case status
    private String current = null;                                         // report being tested

    protected ReportConsoleSuite(String testId, String prefix, String categoryBtn, String categoryName) {
        super(testId);
        this.prefix = prefix; this.categoryBtn = categoryBtn; this.categoryName = categoryName;
    }

    private String id(int num) { return String.format("%s-%03d", prefix, num); }

    @Override
    protected void body() {
        LocalDate today = LocalDate.now();
        fromDate = System.getProperty("reports.from", today.minusMonths(1).withDayOfMonth(1).format(DMY));
        toDate = System.getProperty("reports.to", today.format(DMY));
        meta(categoryName + "s", "Report > Report Console > " + categoryName,
                "READ-ONLY: write API calls are aborted in the browser. Status BLOCKED = <b>NO DATA</b> (not a failure).");
        addSummary("Test class", getClass().getName());
        addSummary("Date range", fromDate + " - " + toDate);

        rc = new ReportConsolePage(page);
        rc.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);

        // 001 navigation
        String nav = rc.openReportConsole();
        List<String> reports = nav.isEmpty() ? rc.openCategory(categoryBtn) : null;
        String navSteps = "1) Login  2) Report > Report Console  3) '" + categoryName + "' button";
        if (reports == null || reports.isEmpty()) {
            row(0, id(1), categoryName, "Navigation", navSteps, "Report list loads", "", "FAIL",
                    "[PERMISSION] " + (nav.isEmpty() ? "category #" + categoryBtn + " missing or lists no reports" : nav), true);
            summary();
            return;
        }
        row(0, id(1), categoryName, "Navigation", navSteps, "Report list loads",
                reports.size() + " reports: " + reports, "PASS", "", true);

        Pattern only = Pattern.compile(System.getProperty("reports.only", ".*"), Pattern.CASE_INSENSITIVE);
        int limit = Integer.getInteger("reports.limit", Integer.MAX_VALUE), n = 0;
        listed = new ArrayList<>(reports);
        for (String r : reports) {
            if (!only.matcher(r).find()) continue;
            if (++n > limit) break;
            current = r;
            outcome.put(r, "PASS");
            try { report(n, r); }
            catch (Throwable t) {
                row(n, id(9), r, "Report runs without script error", "-", "Report tested", "", "FAIL",
                        "[AUTOMATION] " + String.valueOf(t.getMessage()).split("\\R")[0], true);
                for (Page p : ctx.pages()) if (p != page) try { p.close(); } catch (Exception ignore) { }
            }
        }
        summary();
    }

    private void report(int n, String r) {
        rc.takeApiCalls();
        // 002 page load + filters
        Selection sel = rc.selectReport(categoryBtn, r);
        String loadActual = "filters " + sel.filters + "; mandatory " + sel.mandatory + "; GetReportParameter HTTP " + sel.paramApiStatus;
        String loadReason = !sel.ok ? "[APPLICATION DEFECT] " + sel.message
                : sel.paramApiStatus >= 400 ? "[BACKEND/API] GetReportParameter HTTP " + sel.paramApiStatus
                : sel.filters.isEmpty() ? "[APPLICATION DEFECT] no filters offered" : "";
        row(n, id(2), r, "Report page loading / filters", "Reports = '" + r + "'", "Report selected, filters load",
                loadActual, loadReason.isEmpty() ? "PASS" : "FAIL", loadReason, !loadReason.isEmpty());
        if (!loadReason.isEmpty()) return;

        // 003 filters: mandatory ones (or one date filter when none is mandatory)
        List<String> want = new ArrayList<>(sel.mandatory);
        if (want.isEmpty()) want.add(sel.filters.stream().filter(f -> f.toLowerCase().contains("date")).findFirst().orElse(sel.filters.get(0)));
        List<String> filled = new ArrayList<>(), manual = new ArrayList<>();
        for (String f : want) {
            String add = rc.addFilter(f);
            if (!add.isEmpty()) { manual.add(f + ": " + add); continue; }
            Fill fl = rc.fillFilter(f, fromDate, toDate);
            if (fl.ok) filled.add(f + "=" + fl.value); else manual.add(f + ": " + fl);
        }
        String fSteps = "Add + Add Value for " + want + " (dates " + fromDate + " - " + toDate + ", lists: first entry)";
        if (!manual.isEmpty()) {
            row(n, id(3), r, "Filter values", fSteps, "Filters accept values", "filled " + filled, "MANUAL",
                    "[MANUAL] needs input the script must not invent: " + manual, false);
            row(n, id(4), r, "Report generation", "Show Report", "Report opens", "", "MANUAL", "[MANUAL] not run: filter needs manual input", false);
            return;
        }
        row(n, id(3), r, "Filter values", fSteps, "Filters accept values", "filled " + filled, "PASS", "", false);

        // 004 generation
        Shown s = rc.showReport(40000);
        String gActual = "ShowReport HTTP " + s.showApiStatus + "; tab " + (s.tabUrl.isEmpty() ? "(none)" : s.tabUrl) + (s.alerts.isEmpty() ? "" : "; alerts " + s.alerts);
        List<String> bad = rc.takeApiCalls().stream().filter(c -> c.matches(".*-> [45]\\d\\d$")).toList();
        if (s.noData) {
            row(n, id(4), r, "Report generation", "Show Report", "Report opens", "NO DATA — " + gActual, "BLOCKED",
                    "[NO DATA] 'No Data Available' for " + filled, false);
            noData.add(r);
        } else if (!s.serverError.isEmpty() || s.showApiStatus >= 400 || !bad.isEmpty()) {
            row(s.tab != null && !s.tab.isClosed() ? s.tab : page, n, id(4), r, "Report generation", "Show Report", "Report opens", gActual, "FAIL",
                    "[BACKEND/API] " + (s.serverError.isEmpty() ? "failed calls " + bad + " ShowReport HTTP " + s.showApiStatus : s.serverError));
        } else if (!s.produced()) {
            row(n, id(4), r, "Report generation", "Show Report", "Report opens", gActual, "FAIL",
                    s.alerts.isEmpty() ? "[APPLICATION DEFECT] no report and no message" : "[APPLICATION DEFECT] refused with all mandatory filters filled: " + s.alerts, true);
        } else {
            row(s.tab, n, id(4), r, "Report generation", "Show Report", "Report opens in the Crystal viewer",
                    gActual + "; page " + ReportConsolePage.pageField(s.tab), "PASS", "");
            // 005 grid / data
            String text = ReportConsolePage.reportText(s.tab);
            boolean ok = text.length() > 40;
            row(n, id(5), r, "Report grid / data", "Read the report page", "Heading, columns and values render",
                    text.length() > 300 ? text.substring(0, 300) + " ..." : text, ok ? "PASS" : "FAIL",
                    ok ? "" : "[APPLICATION DEFECT] viewer shows no report content", !ok);
        }
        if (s.tab != null && !s.tab.isClosed()) try { s.tab.close(); } catch (Exception ignore) { }
        page.bringToFront();
    }

    private void row(int n, String cid, String r, String what, String steps, String expected, String actual,
                     String status, String reason, boolean shot) {
        row(shot ? page : null, n, cid, r, what, steps, expected, actual, status, reason);
    }

    /** One report row; screenshot of {@code p} when p != null. */
    private void row(Page p, int n, String cid, String r, String what, String steps, String expected, String actual,
                     String status, String reason) {
        String act = reason.isEmpty() ? actual : (actual.isEmpty() ? "" : actual + " — ") + "Failure reason: " + reason;
        // user-facing description only; the internal case id (cid) stays in the code and console log, never in the HTML
        String name = describe(n, r, what);
        System.out.println("  case " + cid + (n > 0 ? " [" + n + "]" : "") + " -> " + status);
        if (p != null) step(p, name, what + ". Steps: " + steps, expected, act, status);
        else step((byte[]) null, name, what + ". Steps: " + steps, expected, act, status);
        counts.merge(status, 1, Integer::sum);
        if (n > 0 && current != null && outcome.containsKey(current)) outcome.merge(current, status, ReportConsoleSuite::worse);
        if ("FAIL".equals(status)) failed.add(name);
    }

    /** "Verify report generation — Allocation Report"; module-level rows: "Verify Financial Reports navigation". */
    private String describe(int n, String r, String what) {
        String w = what.isEmpty() ? what : Character.toLowerCase(what.charAt(0)) + what.substring(1);
        return n > 0 ? "Verify " + w + " — " + r : "Verify " + categoryName + "s " + w;
    }

    /**
     * Presentation only, applied after the framework has written this module's HTML: the shared HtmlReport labels the
     * first text column "Step Name"; this module's report shows it as "Test Description" (no test case IDs anywhere).
     */
    public void relabelReport() {
        try {
            java.nio.file.Path f = reportDir.resolve(testId + ".html");
            if (!java.nio.file.Files.exists(f)) return;
            String h = java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8);
            h = h.replace("<th>Step Name</th>", "<th>Test Description</th>").replace("<th>Description</th>", "<th>Test Steps</th>");
            java.nio.file.Files.writeString(f, h, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { System.out.println("relabelReport: " + e.getMessage()); }
    }

    /** FAIL > MANUAL > BLOCKED > SKIPPED > PASS: a report's outcome is its worst case. */
    private static String worse(String a, String b) {
        List<String> order = List.of("PASS", "SKIPPED", "BLOCKED", "MANUAL", "FAIL");
        return order.indexOf(b) > order.indexOf(a) ? b : a;
    }

    private void summary() {
        List<String> remaining = new ArrayList<>();
        for (String r : listed) if (!outcome.containsKey(r)) remaining.add(r);
        Map<String, Integer> byReport = new LinkedHashMap<>();
        for (String k : List.of("PASS", "FAIL", "BLOCKED", "MANUAL")) byReport.put(k, 0);
        outcome.values().forEach(v -> byReport.merge(v, 1, Integer::sum));
        addSummary("Reports listed by the screen", String.valueOf(listed.size()));
        addSummary("Reports tested", outcome.size() + " of " + listed.size()
                + (remaining.isEmpty() ? " (all attempted)" : " — NOT COMPLETE"));
        addSummary("Reports remaining (" + remaining.size() + ")", remaining.isEmpty() ? "none" : String.join("; ", remaining));
        addSummary("Report outcome (worst case per report)", "PASS " + byReport.get("PASS") + " / FAIL " + byReport.get("FAIL")
                + " / BLOCKED (NO DATA) " + byReport.get("BLOCKED") + " / MANUAL " + byReport.get("MANUAL"));
        addSummary("Case results", counts.toString());
        addSummary("NO DATA reports (" + noData.size() + ")", noData.isEmpty() ? "-" : String.join("; ", noData));
        addSummary("FAILED cases (" + failed.size() + ")", failed.isEmpty() ? "-" : String.join("; ", failed));
        addSummary("Write calls aborted (read-only guard)", rc == null || rc.abortedWrites().isEmpty() ? "none" : String.join("; ", rc.abortedWrites()));
    }
}
