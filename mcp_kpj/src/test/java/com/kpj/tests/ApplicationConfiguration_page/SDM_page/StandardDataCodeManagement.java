package com.kpj.tests.ApplicationConfiguration_page.SDM_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The page object is also named StandardDataCodeManagement — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; SDM &gt; <b>Standard Data Code Management</b> — data validation only.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>SDM</b> → <b>Standard Data Code Management</b>.</li>
 *   <li>Select a <b>Master</b> — a DIFFERENT one every run (the next in the list; the position is kept between runs). Masters
 *       with no requests are skipped.</li>
 *   <li>Click <b>Search</b> — the requests of that master are listed in the table.</li>
 *   <li>Verify the table columns, and that the data is displayed (every row is the selected master's, and the table agrees with
 *       what the server returned).</li>
 * </ol>
 * Creates no data. Pin a master with {@code -Dsdm.master="NAME"} or a list position with {@code -Dsdm.index=N} (1-based).
 */
public class StandardDataCodeManagement extends DevHisBase {

    private static final String COLUMNS = "Location|Master Type|Code|Description|Type|Request Status|Requestor|Request Reason|"
            + "Requested Date & Time|Approve|Approver|Approve Date & Time|Completed By|Completed Date & Time|Decline|Decline Reason|"
            + "Decline By|Decline Date & Time";

    public StandardDataCodeManagement() { super("ApplicationConfig_SDM_StandardDataCodeManagement"); }

    public static void main(String[] args) {
        StandardDataCodeManagement t = new StandardDataCodeManagement();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Where the "next master" position is kept between runs (next to the test output, so it survives a rebuild). */
    private static Path stateFile() {
        String out = System.getenv("OUTDIR");
        if (out == null || out.isBlank()) out = System.getProperty("user.dir") + "/test-output";
        return Paths.get(out, "state", "sdm-master-next.txt");
    }

    /** The 0-based list position to start from this run. */
    private static int startIndex(int count) {
        String pin = System.getProperty("sdm.index");
        if (pin != null && pin.matches("\\d+")) return (Integer.parseInt(pin) - 1 + count) % count;
        int idx = 0;
        try { if (Files.exists(stateFile())) idx = Integer.parseInt(Files.readString(stateFile()).trim()); } catch (Exception ignore) { }
        return ((idx % count) + count) % count;
    }

    /** Keep the position after the one used, for the next run. */
    private static void keepNext(int used, int count) {
        if (System.getProperty("sdm.index") != null || System.getProperty("sdm.master") != null) return;
        try { Files.createDirectories(stateFile().getParent()); Files.writeString(stateFile(), String.valueOf((used + 1) % count)); }
        catch (Exception e) { System.out.println("SDM: could not keep the position (" + e.getMessage() + ")"); }
    }

    @Override
    protected void body() {
        meta("Application Configuration - SDM - Standard Data Code Management", "Application Configuration > SDM > Standard Data Code Management",
                "Select a Master (a different one every run), click Search, verify the master's requests are displayed in the table. Creates no data.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.SDM_page.StandardDataCodeManagement sdm =
                new com.kpj.pages.ApplicationConfiguration_page.SDM_page.StandardDataCodeManagement(page);

        // 1) Navigate
        boolean onScreen = sdm.navigateViaMenu();
        step(page, "Open Standard Data Code Management screen", "Click Application Configuration -> SDM -> Standard Data Code Management",
                "The Standard Data Code Management screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Pick this run's Master (skip masters with no requests) and Search
        List<String> names = sdm.masterNames();
        if (names.isEmpty()) {
            step(page, "Select Master", "Open the Master dropdown", "The dropdown lists the masters", "The dropdown is empty", "FAIL");
            return;
        }
        String pinned = System.getProperty("sdm.master");
        int start = pinned != null && names.contains(pinned) ? names.indexOf(pinned) : startIndex(names.size());
        int idx = start, skipped = 0;
        String master = names.get(idx), why = "";
        boolean searched = false;
        for (int n = 0; n < names.size(); n++) {
            idx = (start + n) % names.size();
            master = names.get(idx);
            why = sdm.selectMaster(master);
            if (!why.isEmpty()) break;
            searched = sdm.clickSearch();
            if (!searched || sdm.serverRows != 0 || pinned != null) break;   // has requests (or the answer could not be read)
            skipped++;
        }
        keepNext(idx, names.size());
        boolean picked = why.isEmpty();
        step(page, "Select Master", "Select the Master \"" + master + "\" (master " + (idx + 1) + " of " + names.size() + "; the next run starts from the next one)",
                "The Master dropdown shows the selected master",
                picked ? "Selected \"" + sdm.masterShown() + "\" (master " + (idx + 1) + " of " + names.size() + ")"
                        + (skipped > 0 ? "; " + skipped + " earlier master(s) had no requests and were skipped" : "")
                       : "Could not select \"" + master + "\": " + why,
                picked ? "PASS" : "FAIL");
        if (!picked) return;

        // 3) Search
        step(sdm.searchPng != null ? sdm.searchPng : page.screenshot(), "Click Search", "Click Search",
                "The requests of the selected Master are listed in the table",
                searched ? "Search clicked; server: " + sdm.searchHttp : "The Search button could not be clicked", searched ? "PASS" : "FAIL");
        if (!searched) return;

        // 4) Verify the table
        List<String> heads = sdm.headers();
        StringBuilder missing = new StringBuilder();
        for (String h : COLUMNS.split("\\|")) if (heads.stream().noneMatch(x -> x.equalsIgnoreCase(h))) missing.append(missing.length() > 0 ? ", " : "").append(h);
        step(page, "Verify the table columns", "Read the table header", "Columns: " + COLUMNS.replace("|", ", "),
                missing.length() == 0 ? "Columns shown: " + String.join(", ", heads) : "Missing column(s): " + missing + " (shown: " + String.join(", ", heads) + ")",
                missing.length() == 0 ? "PASS" : "FAIL");

        List<List<String>> rows = sdm.rows();
        int shown = rows.size();
        int total = sdm.totalItems();
        int expect = sdm.serverRows;                 // -1 when the answer could not be read
        boolean serverKnown = expect >= 0;
        int tableCount = total >= 0 ? total : shown;
        boolean consistent = !serverKnown || tableCount == expect;
        boolean pageOk = shown == Math.min(tableCount, sdm.pageSize());

        int idxMaster = -1, idxStatus = -1;
        for (int i = 0; i < heads.size(); i++) {
            if (heads.get(i).equalsIgnoreCase("Master Type")) idxMaster = i;
            if (heads.get(i).equalsIgnoreCase("Request Status")) idxStatus = i;
        }
        int off = rows.isEmpty() ? 0 : Math.max(0, rows.get(0).size() - heads.size());   // align from the right if the counts differ
        int wrong = 0;
        Map<String, Integer> status = new LinkedHashMap<>();
        for (List<String> r : rows) {
            if (idxMaster >= 0 && idxMaster + off < r.size() && !r.get(idxMaster + off).equalsIgnoreCase(master)) wrong++;
            if (idxStatus >= 0 && idxStatus + off < r.size()) status.merge(r.get(idxStatus + off).isEmpty() ? "(blank)" : r.get(idxStatus + off), 1, Integer::sum);
        }

        String actual, verdict;
        String empty = sdm.emptyMessage();
        if (shown > 0) {
            actual = tableCount + " request(s) listed in the table for \"" + master + "\"" + (serverKnown ? " (server returned " + expect + ")" : "")
                    + (tableCount > shown ? "; " + shown + " on this page, the rest on the next page(s)" : "")
                    + "; Request Status of the rows on this page: " + (status.isEmpty() ? "n/a" : status.toString())
                    + (wrong > 0 ? "; " + wrong + " row(s) belong to a different master" : "; every row is this master's")
                    + "; first row: " + String.join(" | ", rows.get(0));
            verdict = (wrong == 0 && consistent && pageOk) ? "PASS" : "FAIL";
            if (!consistent) actual += " — the table does not match the server answer";
            if (!pageOk) actual += " — the page shows " + shown + " row(s), expected " + Math.min(tableCount, sdm.pageSize());
        } else if (serverKnown && expect > 0) {
            actual = "The server returned " + expect + " row(s) for \"" + master + "\" but the table shows none";
            verdict = "FAIL";
        } else {
            actual = "No rows: no master has any requests" + (serverKnown ? " (server returned 0)" : "") + (empty.isEmpty() ? "" : "; screen says: " + empty);
            verdict = "PASS";
        }
        step(sdm.searchPng != null ? sdm.searchPng : page.screenshot(), "Verify the data is displayed in the table",
                "Check the table lists the selected master's requests (rows, matching Master Type, Request Status) and agrees with the server answer",
                "Data is displayed in the table (or the master genuinely has no requests)", actual, verdict);

        addSummary("Master", master + " (" + (idx + 1) + " of " + names.size() + ")");
        addSummary("Rows in table", String.valueOf(tableCount));
        addSummary("Server rows", serverKnown ? String.valueOf(expect) : "n/a");
        addSummary("Request Status values", status.isEmpty() ? "n/a" : status.toString());
        addSummary("Result", shown > 0 ? "Data displayed in the table" : "No data for this master");
    }
}
