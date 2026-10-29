package com.kpj.tests.AncillaryServices_page.Linen_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The page object is also named LinenCategoryMaster - referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Linen &gt; <b>Linen Category Master</b>.
 *
 * <p><b>READ-ONLY by default.</b> Every {@code /api/CommonMaster/IUD} (insert/update/delete) request is aborted by
 * a network guard, and the real save is skipped (reported SKIPPED). Data is created only when the run is started
 * with {@code -Ddevhis.allowSave=true} (or env {@code DEVHIS_ALLOWSAVE=true}).</p>
 *
 * <ol>
 *   <li><b>T1 Verify page</b> - open Ancillary Services -> Linen -> Linen Category Master; assert title, route, H1
 *       and the form / listing controls.</li>
 *   <li><b>T2 Add category (validation)</b> - Form Name pre-set to Linen Category; Submit with nothing ->
 *       "Please Enter Code"; Submit with only Code -> "Please Enter Description"; no write request may fire; Clear
 *       empties the form. With allowSave: enter a unique Code + Remark and Submit -> "Master Saved Successfully".</li>
 *   <li><b>T3 Listing</b> - columns, filters, pager, search/refresh controls; grid rows must equal what the GRID
 *       API returned; Code filter narrows the grid. With allowSave: the new category appears in the listing.</li>
 * </ol>
 *
 * <p>JUnit status: the test FAILS when any report step is FAIL (the existing tests only flag failures in the HTML
 * report and FAILED_STEPS.html; this class additionally fails the JUnit test so {@code mvn test} reports it).</p>
 */
public class LinenCategoryMaster extends DevHisBase {

    /** Save gate: -Ddevhis.allowSave=true, or environment DEVHIS_ALLOWSAVE=true. */
    static boolean allowSave() {
        String v = System.getProperty("devhis.allowSave");
        if (v == null || v.isBlank()) v = System.getenv("DEVHIS_ALLOWSAVE");
        return v != null && Boolean.parseBoolean(v.trim());
    }

    private final List<String> failedSteps = new ArrayList<>();
    private int passed = 0, skipped = 0;

    public LinenCategoryMaster() { super("AncillaryServices_Linen_LinenCategoryMaster"); }

    public static void main(String[] args) {
        LinenCategoryMaster t = new LinenCategoryMaster();
        try { t.run(); } finally { t.stop(); }
        t.printTotals();
        if (!t.failedSteps.isEmpty()) System.exit(1);
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
        printTotals();
        if (!failedSteps.isEmpty())
            org.junit.jupiter.api.Assertions.fail(failedSteps.size() + " step(s) FAILED: " + String.join(" ; ", failedSteps));
    }

    private void printTotals() {
        System.out.println("[LinenCategoryMaster] steps PASS=" + passed + " SKIPPED=" + skipped + " FAIL=" + failedSteps.size()
                + " (allowSave=" + allowSave() + ")");
    }

    /** Record a step with a screenshot and keep the JUnit tally in step with the report. */
    private void check(String name, String desc, String expected, String actual, boolean ok) {
        step(page, name, desc, expected, actual, ok ? "PASS" : "FAIL");
        if (ok) passed++; else failedSteps.add(name);
    }

    private void skip(String name, String desc, String expected, String why) {
        step(page, name, desc, expected, why, "SKIPPED");
        skipped++;
    }

    @Override
    protected void body() {
        meta("Ancillary Services - Linen - Linen Category Master",
                "Ancillary Services > Linen > Linen Category Master",
                "T1 page verification, T2 add-category form + mandatory-field validation, T3 listing grid. "
                        + (allowSave() ? "SAVE ENABLED (-Ddevhis.allowSave=true): one Linen Category will be created."
                                       : "READ-ONLY run: write requests are blocked and the save is skipped."));
        try {
            runScenarios();
        } catch (Throwable e) {
            failedSteps.add("ERROR: " + e.getMessage());   // DevHisBase.run() records the ERROR row + screenshot
            throw e;
        }
    }

    private void runScenarios() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        check("Login", USER + " login on " + BASE, "Authenticated; Patient Dashboard", "Logged in - " + page.url(),
                !loginPage.isLoginFormVisible());

        com.kpj.pages.AncillaryServices_page.Linen_page.LinenCategoryMaster lc =
                new com.kpj.pages.AncillaryServices_page.Linen_page.LinenCategoryMaster(page);
        if (!allowSave()) lc.enableReadOnlyGuard();

        // ================= T1: Verify Linen Category Master page =================
        boolean on = lc.navigateViaMenu();
        String landed = lc.currentScreen();
        String how = "Menu: " + (lc.navMethod.isEmpty() ? "(not reached)" : lc.navMethod)
                + (lc.navProblem.isEmpty() ? "" : "\nNavigation problem: " + lc.navProblem);
        check("T1.1 Open Linen Category Master", "Ancillary Services -> Linen -> Linen Category Master",
                "Title 'Linen Category Master', route #/LinenCategoryMaster, heading 'Linen Category'",
                (on ? "Opened: " + landed : "WRONG PAGE - " + landed + "\n" + lc.describeForm()) + "\n" + how, on);
        if (on && !"menu click".equals(lc.navMethod)) {
            // The page opened, but not through a real click on the submenu link - record it for a human check.
            step(page, "T1.1b Menu link clickable", "Real click on Ancillary Services > Linen > Linen Category Master",
                    "The submenu link is clickable", how
                            + "\nManual check: open the menu by hand at this window size (collapsed sidebar) and confirm the link works.",
                    "MANUAL");
        }
        addSummary("Page title / heading", page.title() + " / " + lc.heading());
        addSummary("Route", page.url());
        if (!on) { addSummary("Result", "FAILED - Linen Category Master did not open"); return; }

        interceptAlerts();   // DevHisBase helper: records every JAlert into window.__alerts (install ONCE)

        Map<String, Object> f = lc.formInfo();
        String labels = String.valueOf(f.get("labels"));
        boolean formOk = labels.contains("Form Name*") && labels.contains("Code*") && labels.contains("Remark*")
                && Boolean.TRUE.equals(f.get("codeVisible")) && Boolean.TRUE.equals(f.get("remarkVisible"))
                && Boolean.TRUE.equals(f.get("submit")) && Boolean.TRUE.equals(f.get("clear"));
        check("T1.2 Form controls present", "Inspect the entry form",
                "Form Name*, Code*, Remark* (mandatory, starred), MIMS fields, Submit and Clear",
                "Labels: " + labels + "\nSubmit=" + f.get("submit") + ", Clear=" + f.get("clear")
                        + ", MIMS GUID=" + f.get("mimsGuid") + ", MIMS Description=" + f.get("mimsDesc") + ", MIMS Type=" + f.get("mimsType")
                        + "\nRights: Add=" + f.get("canAdd") + ", Edit=" + f.get("canEdit"),
                formOk);

        boolean formNameOk = "Linen Category".equals(String.valueOf(f.get("formName")))
                && "LinenCategoryMaster".equals(String.valueOf(f.get("masterCodeFor")));
        check("T1.3 Form Name defaults to Linen Category", "Read the Form Name drop-down and the screen's master code",
                "Form Name = 'Linen Category' (MasterCodeFor = LinenCategoryMaster)",
                "Form Name = '" + f.get("formName") + "', value = " + f.get("formNameValue") + ", MasterCodeFor = " + f.get("masterCodeFor"),
                formNameOk);

        Map<String, Object> g0 = lc.gridInfo();
        boolean gridShown = "Code | Remark | Status | Edit".equals(String.valueOf(g0.get("headers")));
        check("T1.4 Listing grid present", "Inspect the listing grid", "Grid with columns Code | Remark | Status | Edit",
                "Columns: " + g0.get("headers") + " | pager: " + g0.get("pagerCount"), gridShown);

        // ================= T2: Add a Linen Category (validation) =================
        lc.closeStraySelect2();
        lc.fillCode("");
        lc.fillRemark("");
        String m1 = lc.submitAndGetMessage();
        String k1 = lc.toastKind();
        List<String> submitCalls = new ArrayList<>(lc.lastSubmitApiCalls);
        check("T2.1 Mandatory: Code", "Leave Code and Remark empty, click Submit",
                "Warning 'Please Enter Code'; nothing is saved", "Message: '" + m1 + "' (" + k1 + ")",
                "Please Enter Code".equalsIgnoreCase(m1));

        String probeCode = "LC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));   // 12 chars <= maxlength 15
        lc.fillCode(probeCode);
        String m2 = lc.submitAndGetMessage();
        String k2 = lc.toastKind();
        submitCalls.addAll(lc.lastSubmitApiCalls);
        check("T2.2 Mandatory: Remark", "Enter Code '" + probeCode + "', leave Remark empty, click Submit",
                "Warning asking for the Remark; nothing is saved", "Message: '" + m2 + "' (" + k2 + ")"
                        + ("Please Enter Description".equalsIgnoreCase(m2) ? "\nNOTE: the field is labelled 'Remark' but the message says 'Description'." : ""),
                "Please Enter Description".equalsIgnoreCase(m2));

        // Runtime proof on WHATEVER environment this runs against: the guard blocked nothing, and no request sent
        // during the two validation submits targets the Common-Master write endpoint or any other write-like API.
        List<String> writeLike = new ArrayList<>();
        for (String c : submitCalls) {
            String url = c.substring(c.indexOf(' ') + 1);
            if (com.kpj.pages.AncillaryServices_page.Linen_page.LinenCategoryMaster.WRITE_API.matcher(url).matches()
                    || url.toLowerCase().matches(".*/api/commonmaster/(?!fetch).*")) writeLike.add(c);
        }
        boolean noWrite = lc.blockedWrites.isEmpty() && writeLike.isEmpty();
        check("T2.3 No data written by validation", "Watch every /api/ request during the two validation submits",
                "No insert/update/delete request is sent (validation stops before /api/CommonMaster/IUD)",
                (noWrite ? "No write request was sent" : "WRITE request(s) seen: " + writeLike + " | blocked by guard: " + lc.blockedWrites)
                        + "\nAPI calls during the submits: " + (submitCalls.isEmpty() ? "none" : submitCalls),
                noWrite);

        lc.fillRemark("Automation probe - not saved");
        boolean limitsOk = Long.valueOf(15).equals(toLong(f.get("codeMax"))) && Long.valueOf(250).equals(toLong(f.get("remarkMax")));
        check("T2.4 Field limits", "Read Code / Remark maxlength", "Code max 15, Remark max 250 characters",
                "Code maxlength=" + f.get("codeMax") + ", Remark maxlength=" + f.get("remarkMax"), limitsOk);

        boolean cleared = lc.clickClear();
        check("T2.5 Clear resets the form", "Click Clear", "Code and Remark are emptied",
                "Code='" + lc.codeValue() + "', Remark='" + lc.remarkValue() + "'", cleared);

        String newCode = "LC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));   // 14 chars
        String newRemark = "Auto Linen Category " + newCode;
        boolean saved = false;
        if (allowSave()) {
            lc.disableReadOnlyGuard();
            lc.fillCode(newCode);
            lc.fillRemark(newRemark);
            String m3 = lc.submitAndGetMessage();
            saved = m3.toLowerCase().contains("saved successfully");
            check("T2.6 Save new Linen Category", "Enter Code '" + newCode + "', Remark '" + newRemark + "', click Submit",
                    "Toast 'Master Saved Successfully'", "Message: '" + m3 + "' (" + lc.toastKind() + ")", saved);
            addSummary("Created Code / Remark", newCode + " / " + newRemark);
        } else {
            skip("T2.6 Save new Linen Category", "Enter a unique Code + Remark and click Submit",
                    "Toast 'Master Saved Successfully'",
                    "SKIPPED - read-only run. Re-run with -Ddevhis.allowSave=true to create '" + newCode + "' (manual verification otherwise).");
        }

        // ================= T3: Verify Linen Category Listing =================
        Map<String, Object> g = lc.gridInfo();
        check("T3.1 Grid columns", "Read the grid header", "Code | Remark | Status | Edit",
                String.valueOf(g.get("headers")), "Code | Remark | Status | Edit".equals(String.valueOf(g.get("headers"))));

        check("T3.2 Column filters", "Find per-column filter boxes", "Filter boxes on Code, Remark, Status",
                "Filter boxes on: " + g.get("filterColumns"), "Code | Remark | Status".equals(String.valueOf(g.get("filterColumns"))));

        boolean pagerOk = Boolean.TRUE.equals(g.get("pagerVisible")) && "25".equals(String.valueOf(g.get("pageSize")))
                && "25/50/75/100/125".equals(String.valueOf(g.get("pageSizes")));
        check("T3.3 Pagination", "Inspect the pager", "Pager shown; 25 per page; sizes 25/50/75/100/125",
                "Pager '" + g.get("pagerCount") + "', page size " + g.get("pageSize") + ", sizes " + g.get("pageSizes"), pagerOk);

        step(page, "T3.4 Search / refresh controls", "Look for a global search box and a refresh control",
                "Recorded for the listing inventory",
                "Global search boxes: " + g.get("globalSearch") + "; refresh controls: " + g.get("refreshControls")
                        + "; grid column menu: " + g.get("gridMenu")
                        + ". Searching is done with the column filters; the list re-loads after a save / Form Name change.",
                "MANUAL");

        int apiCount = lc.gridApiCount();
        long gridCount = toLong(g.get("dataLength"));
        boolean countOk = apiCount >= 0 && (allowSave() && saved ? gridCount >= apiCount : gridCount == apiCount);
        check("T3.5 Existing categories displayed", "Compare grid rows with the GRID API response (CommonMaster/fetch, MasterCodeFor=LinenCategoryMaster)",
                "Grid shows every category the API returned",
                "API returned " + (apiCount < 0 ? "(not captured, HTTP " + lc.gridApiStatus + ")" : apiCount) + " record(s); grid holds " + gridCount
                        + " (pager '" + g.get("pagerCount") + "')"
                        + (gridCount == 0 ? "\nThe Linen Category master is EMPTY on this environment - no existing categories to list." : "\nFirst rows: " + g.get("firstRows")),
                countOk);
        addSummary("Existing Linen Categories", String.valueOf(gridCount));

        int none = lc.filterByCode("ZZ_NO_SUCH_" + newCode);
        int all = lc.filterByCode("");
        check("T3.6 Code filter narrows the list", "Type a non-existent code in the Code filter, then clear it",
                "0 rows while filtered; all rows back after clearing",
                "Filtered rows = " + none + ", after clearing = " + all + " (of " + gridCount + ")",
                none == 0 && all == gridCount);

        if (allowSave() && saved) {
            boolean listed = lc.waitForGridCode(newCode, 15000);
            int hit = lc.filterByCode(newCode);
            check("T3.7 New category in listing", "Filter the grid by the new Code '" + newCode + "'",
                    "The saved category is listed", listed ? "Listed - filter shows " + hit + " row(s)" : "NOT listed after save", listed && hit >= 1);
            lc.filterByCode("");
        } else {
            skip("T3.7 New category in listing", "Filter the grid by the newly saved Code",
                    "The saved category is listed",
                    allowSave() ? "SKIPPED - the save did not succeed" : "SKIPPED - read-only run (no category was created)");
        }

        if (!allowSave()) {
            boolean stillClean = lc.blockedWrites.isEmpty();
            check("T3.8 Read-only run left data untouched", "Check the write guard at the end of the run",
                    "No /api/CommonMaster/IUD request was attempted", stillClean ? "None attempted" : "Attempted: " + lc.blockedWrites, stillClean);
            lc.disableReadOnlyGuard();
        }
        addSummary("Mode", allowSave() ? "SAVE ENABLED" : "READ-ONLY");
        addSummary("Result", failedSteps.isEmpty() ? "PASS" : "FAILED: " + String.join(", ", failedSteps));
    }

    private static Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return -1L; }
    }
}
