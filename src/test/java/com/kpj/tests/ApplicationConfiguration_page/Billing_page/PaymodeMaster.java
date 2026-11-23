package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PaymodeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Paymode Master</b>.
 *
 * <ol>
 *   <li>Open the screen; the <b>List of Paymode</b> grid is empty until a search is made.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Verify the rows of data in the table: the grid lists paymodes, and every one has a Paymode Id, a Code and a Paymode Type (the Ledger Name and GL Code fills are reported).</li>
 * </ol>
 *
 * <p>This screen is read-only: the test changes no data.</p>
 */
public class PaymodeMaster extends DevHisBase {

    public PaymodeMaster() { super("ApplicationConfiguration_Billing_PaymodeMaster"); }

    public static void main(String[] args) {
        PaymodeMaster t = new PaymodeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Paymode Master", "Application Configuration > Billing > Paymode Master",
                "Search; verify the rows of data in the List of Paymode table (Paymode Id, Code, Paymode Type on every row). READ-ONLY.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PaymodeMaster pm = new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PaymodeMaster(page);

        boolean on = pm.navigateViaMenu();
        step("Open Paymode Master", "Click Application Configuration -> Billing -> Paymode Master", "The Paymode Master screen is shown",
                on ? "Opened " + page.url() + " (Location \"" + pm.location() + "\"; the list holds " + pm.dataRows() + " row(s) before any search)" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Paymode Master not reached"); return; }

        int n = pm.clickSearch();
        boolean searchOk = pm.ok();
        step("Click Search", "Click Search", "The search answers with the list of paymodes",
                searchOk ? "Search answered after " + pm.lastSearchMs / 1000.0 + " s: " + n + " row(s) (pager: \"" + pm.pagerText() + "\")" + (pm.lastToast.isEmpty() ? "" : "; the screen said \"" + pm.lastToast + "\"")
                         : "Problem — " + pm.problems, searchOk ? "PASS" : "FAIL");
        if (!searchOk) { addSummary("Result", "FAILED — Search: " + pm.problems); return; }

        com.google.gson.JsonObject st = pm.stats(3);
        int total = st == null ? 0 : st.get("total").getAsInt();
        java.util.List<String> columns = new java.util.ArrayList<>();
        String missing = "";
        String fills = "";
        // the grid's column definitions also carry hidden fields (Isvisible..., Background Color, ...): report only the columns the screen shows
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (String h : pm.headers()) shown.add(h.toLowerCase().replaceAll("\\s+", ""));
        if (st != null) {
            for (com.google.gson.JsonElement e : st.get("columns").getAsJsonArray()) if (shown.contains(e.getAsString().toLowerCase().replaceAll("\\s+", ""))) columns.add(e.getAsString());
            com.google.gson.JsonObject filled = st.get("filled").getAsJsonObject();
            for (String c : columns) {
                int f = filled.get(c).getAsInt();
                fills += (fills.isEmpty() ? "" : ", ") + c + " " + f + "/" + total;
                // Paymode Id, Code and Paymode Type must be present on every row; Ledger Name and GL Code are reported only
                if (f < total && (c.equalsIgnoreCase("Paymode Id") || c.equalsIgnoreCase("Code") || c.equalsIgnoreCase("Paymode Type")))
                    missing += (missing.isEmpty() ? "" : "; ") + (total - f) + " row(s) have no " + c;
            }
        }
        boolean hasKeyColumns = columns.stream().anyMatch(c -> c.equalsIgnoreCase("Code")) && columns.stream().anyMatch(c -> c.equalsIgnoreCase("Paymode Type"));
        int distinct = st == null ? -1 : st.get("distinctIds").getAsInt();
        boolean populated = total > 0 && hasKeyColumns && missing.isEmpty();
        java.util.List<String> firstRows = new java.util.ArrayList<>();
        if (st != null) for (com.google.gson.JsonElement e : st.get("first").getAsJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (String part : e.getAsString().split(" \\| ")) {
                String head = part.contains(":") ? part.substring(0, part.indexOf(':')) : part;
                if (shown.contains(head.trim().toLowerCase().replaceAll("\\s+", ""))) sb.append(sb.length() == 0 ? "" : " | ").append(part.trim());
            }
            firstRows.add(sb.toString());
        }
        String first = firstRows.toString();
        step("Verify row of data in the table — list of paymode", "Read the List of Paymode table after Search",
                "Rows are listed and each has a Paymode Id, a Code and a Paymode Type",
                populated ? "PASSES because the table (columns " + pm.headers() + ") lists " + total + " paymode(s) (" + pm.paintedRows() + " painted in the viewport; pager: \"" + pm.pagerText()
                        + "\"); values filled per column: " + fills + (distinct >= 0 ? "; " + distinct + " distinct Paymode Id(s)" : "") + "; first rows: " + first
                          : "FAILS because " + (total == 0 ? "the table is empty after Search" + (pm.lastToast.isEmpty() ? "" : " — the screen said \"" + pm.lastToast + "\"") : !hasKeyColumns ? "the grid has no Code / Paymode Type column (columns " + columns + ")" : missing)
                                + "; first rows: " + first,
                populated ? "PASS" : "FAIL");

        addSummary("Location", pm.lastLocation);
        addSummary("Paymodes listed", total + " (pager: " + pm.pagerText() + ")");
        addSummary("Values filled per column", fills);
        addSummary("First rows", first);
        addSummary("Result", populated ? total + " paymode(s) listed" : (total == 0 ? "No rows" : "Rows present but a check failed"));
    }
}
