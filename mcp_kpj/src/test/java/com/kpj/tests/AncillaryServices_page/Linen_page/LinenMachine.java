package com.kpj.tests.AncillaryServices_page.Linen_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The page object is also named LinenMachine — referenced by its fully-qualified name (same convention as the
// other Linen / CSSD master tests).

/**
 * Ancillary Services &gt; Linen &gt; <b>Linen Machine</b>.
 *
 * <ol>
 *   <li><b>T1</b> Open Ancillary Services → Linen → Linen Machine (sidebar menu).</li>
 *   <li><b>T2</b> The list loads (list API answers) and shows the Code / Remark / Status / Edit columns.</li>
 *   <li><b>T3</b> Add a Linen Machine (unique Code + Remark) → "Master Saved Successfully".
 *       <b>WRITE — runs only with {@code -Ddevhis.allowSave=true}</b>, otherwise SKIPPED.</li>
 *   <li><b>T4</b> The new machine is in the refreshed list (Active). Needs T3.</li>
 *   <li><b>T5</b> Search with the grid's Code column filter → only the matching machine is shown.</li>
 *   <li><b>T6</b> Mandatory fields: Submit with Code empty → "Please Enter Code"; Code without Remark →
 *       "Please Enter Description". Read-only: the save API is blocked at the network layer during T6, so even a
 *       missing client check cannot create a record (it is reported as a defect instead).</li>
 * </ol>
 *
 * <p>Configuration (never hard-coded here; nothing secret is printed or put in the report):
 * {@code DEVHIS_URL}, {@code DEVHIS_USER}, {@code DEVHIS_PASS} environment variables (or the framework's matching
 * {@code -Ddevhis.url / -Ddevhis.user / -Ddevhis.pass}). The test FAILS up front if any is missing, and it turns off
 * LoginPage's saved-account rotation so only the supplied account is used.</p>
 *
 * <p>Cleanup: the screen has no delete — only an Active/Inactive toggle — so a machine created by T3 stays in the
 * list. It is identifiable by its {@code LM} + timestamp code and the {@code AUTOTEST} remark.</p>
 *
 * <pre>
 *   read-only : mvn test -Dtest=com.kpj.tests.AncillaryServices_page.Linen_page.LinenMachine
 *   with save : mvn test -Dtest=com.kpj.tests.AncillaryServices_page.Linen_page.LinenMachine -Ddevhis.allowSave=true
 * </pre>
 */
public class LinenMachine extends DevHisBase {

    /** Write gate — T3/T4 create a real record only when this is true. */
    static final boolean ALLOW_SAVE = Boolean.getBoolean("devhis.allowSave");

    public LinenMachine() { super("AncillaryServices_Linen_LinenMachine"); }

    public static void main(String[] args) {
        LinenMachine t = new LinenMachine();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Linen - Linen Machine",
                "Ancillary Services > Linen > Linen Machine",
                "Open the screen, check the list and its columns, add a machine (only with -Ddevhis.allowSave=true), find it in "
                        + "the list and with the Code filter, and check the Code / Remark mandatory-field messages.");
        addSummary("Environment", BASE);
        addSummary("Save mode", ALLOW_SAVE ? "WRITE enabled (-Ddevhis.allowSave=true)" : "READ-ONLY (add steps skipped)");

        // ---- configuration: env vars / -D only, nothing hard-coded -------------------------------------------------
        String missing = missingConfig();
        if (!missing.isEmpty()) {
            step("Configuration", "Read DEVHIS_URL / DEVHIS_USER / DEVHIS_PASS",
                    "All three are set", "Not set: " + missing + " — set them as environment variables (or -Ddevhis.url/user/pass)", "FAIL");
            addSummary("Result", "FAILED - missing configuration: " + missing);
            return;
        }
        // Use ONLY the supplied account: an empty, non-blank fallback list makes LoginPage try nothing else.
        if (System.getProperty("devhis.cred.fallbacks") == null) System.setProperty("devhis.cred.fallbacks", ",");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", "Log in as the DEVHIS_USER account", "Authenticated; Patient Dashboard", "Logged in as " + USER, "PASS");

        com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm =
                new com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine(page);
        try {
            if (!t1Open(lm)) return;
            t2List(lm);
            String created = t3Add(lm);
            boolean listed = t4Listed(lm, created);
            t5Search(lm, listed ? created : null);
            t6Mandatory(lm);
        } finally {
            lm.dispose();
        }
    }

    // ---- T1 ---------------------------------------------------------------------------------------------------------
    private boolean t1Open(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm) {
        boolean on = lm.navigateViaMenu();
        String landed = lm.currentScreen();
        String how = lm.usedMenu ? "via the sidebar menu" : "via the direct route (sidebar link could not be used)";
        step(page, "T1 Open Linen Machine", "Ancillary Services -> Linen -> Linen Machine",
                "The Linen Machine screen opens (route #/LinenMachine, Form Name = Linen Machine)",
                on ? "Opened " + how + ": " + landed : "WRONG PAGE / not opened - app shows: " + landed + "\n" + lm.describeRoutes("LinenMachine"),
                on && lm.usedMenu ? "PASS" : "FAIL");
        if (!on) addSummary("Result", "FAILED - Linen Machine screen did not open: " + landed);
        return on;
    }

    // ---- T2 ---------------------------------------------------------------------------------------------------------
    private void t2List(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm) {
        lm.waitForList(15000);
        List<String> cols = lm.gridColumns();
        List<Map<String, Object>> rows = lm.gridData();
        List<String> absent = com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine.EXPECTED_COLUMNS.stream()
                .filter(c -> cols.stream().noneMatch(x -> x.equalsIgnoreCase(c))).collect(Collectors.toList());
        String formProblems = lm.formProblems();
        boolean apiOk = lm.lastListStatus == 200;
        boolean ok = apiOk && absent.isEmpty() && formProblems.isEmpty();
        String actual = "Columns: " + cols + " | List API: " + lm.lastListApi + " | Rows: " + rows.size()
                + (rows.isEmpty() ? " (no Linen Machine records exist on this environment)" : " e.g. " + rows.get(0))
                + (absent.isEmpty() ? "" : " | MISSING columns: " + absent)
                + (formProblems.isEmpty() ? "" : " | MISSING form controls: " + formProblems)
                + (apiOk ? "" : " | list API did not return HTTP 200");
        step(page, "T2 Linen Machine list & columns", "Wait for the list to load; read the grid header",
                "List loads; columns Code, Remark, Status, Edit are shown; Code/Remark/Submit/Clear are present", actual, ok ? "PASS" : "FAIL");
        addSummary("Existing records", String.valueOf(rows.size()));
    }

    // ---- T3 ---------------------------------------------------------------------------------------------------------
    /** @return the code of the machine that the app confirmed saving, or null */
    private String t3Add(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm) {
        if (!ALLOW_SAVE) {
            step(page, "T3 Add Linen Machine", "Enter a unique Code + Remark and Submit",
                    "'Master Saved Successfully'", "Not run - writing to HIS Demo needs -Ddevhis.allowSave=true", "SKIPPED");
            return null;
        }
        String code = null, remark = null, alerts = "";
        boolean saved = false;
        for (int attempt = 0; attempt < 3 && !saved; attempt++) {
            code = uniqueCode(attempt);
            remark = "AUTOTEST Linen Machine " + code;
            lm.fill(code, remark);
            alerts = lm.submitForReal();
            saved = alerts.contains(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine.MSG_SAVED);
            if (!saved && !alerts.toLowerCase().contains("already exists")) break; // a real failure — don't retry
        }
        step(page, "T3 Add Linen Machine", "Enter Code '" + code + "' and Remark '" + remark + "', click Submit",
                "'Master Saved Successfully' and the save API succeeds",
                (alerts.isEmpty() ? "No message shown" : "Message: " + alerts) + " | Save API: " + lm.lastSaveApi,
                saved ? "PASS" : "FAIL");
        addSummary("Created Code", saved ? code : "(not created)");
        addSummary("Created Remark", saved ? remark : "(not created)");
        return saved ? code : null;
    }

    // ---- T4 ---------------------------------------------------------------------------------------------------------
    private boolean t4Listed(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm, String code) {
        if (code == null) {
            step(page, "T4 New machine in the list", "Find the added Code in the refreshed list", "The new machine is listed (Active)",
                    ALLOW_SAVE ? "Not run - T3 did not confirm a save" : "Not run - no machine was added (read-only mode)", "SKIPPED");
            return false;
        }
        // The screen re-fetches the grid after a save; poll the grid data for the new code.
        Map<String, Object> row = null;
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end && row == null) {
            row = lm.gridData().stream().filter(r -> code.equals(String.valueOf(r.get("Code")))).findFirst().orElse(null);
            if (row == null) page.waitForTimeout(500);
        }
        boolean ok = row != null && "Active".equals(String.valueOf(row.get("Status")));
        step(page, "T4 New machine in the list", "Find Code '" + code + "' in the refreshed list",
                "The new machine is listed with its Remark and Status Active",
                row == null ? "Code '" + code + "' NOT in the list after the save (list API: " + lm.lastListApi + ")" : "Listed: " + row,
                ok ? "PASS" : "FAIL");
        return row != null;
    }

    // ---- T5 ---------------------------------------------------------------------------------------------------------
    private void t5Search(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm, String createdCode) {
        List<Map<String, Object>> rows = lm.gridData();
        String term = createdCode != null ? createdCode : rows.isEmpty() ? null : String.valueOf(rows.get(0).get("Code"));
        if (term == null || term.isBlank()) {
            step(page, "T5 Search / filter", "Filter the list by Code", "Only the matching machine is shown",
                    "Not run - the list has no Linen Machine records to search for", "SKIPPED");
            return;
        }
        if (!lm.filterByCode(term)) {
            step(page, "T5 Search / filter", "Type '" + term + "' in the Code column filter", "Only the matching machine is shown",
                    "The grid has no Code filter box", "FAIL");
            return;
        }
        List<String> shown = lm.visibleCodes();
        long expected = rows.stream().filter(r -> String.valueOf(r.get("Code")).toLowerCase().contains(term.toLowerCase())).count();
        boolean ok = !shown.isEmpty() && shown.contains(term)
                && shown.stream().allMatch(c -> c.toLowerCase().contains(term.toLowerCase()));
        step(page, "T5 Search / filter", "Type '" + term + "' in the Code column filter",
                "Only machines whose Code contains '" + term + "' are shown",
                "Shown: " + shown + " (grid data has " + expected + " matching row(s))", ok ? "PASS" : "FAIL");
        lm.clearCodeFilter();
    }

    // ---- T6 ---------------------------------------------------------------------------------------------------------
    private void t6Mandatory(com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine lm) {
        final String enterCode = com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine.MSG_ENTER_CODE;
        final String enterRemark = com.kpj.pages.AncillaryServices_page.Linen_page.LinenMachine.MSG_ENTER_REMARK;

        lm.fill("", "");
        String a1 = lm.submitWithSaveBlocked();
        boolean leak1 = lm.saveCallAttempted;
        step(page, "T6a Code is mandatory", "Leave Code and Remark empty; click Submit (save API blocked for this check)",
                "'" + enterCode + "' warning; no save call",
                (a1.isEmpty() ? "No message shown" : "Message: " + a1) + (leak1 ? " | DEFECT: the page called the save API with an empty Code" : ""),
                a1.contains(enterCode) && !leak1 ? "PASS" : "FAIL");

        lm.fill("LMVALCHK", "");
        String a2 = lm.submitWithSaveBlocked();
        boolean leak2 = lm.saveCallAttempted;
        step(page, "T6b Remark is mandatory", "Enter Code only (Remark empty); click Submit (save API blocked for this check)",
                "'" + enterRemark + "' warning; no save call",
                (a2.isEmpty() ? "No message shown" : "Message: " + a2) + (leak2 ? " | DEFECT: the page called the save API with an empty Remark" : ""),
                a2.contains(enterRemark) && !leak2 ? "PASS" : "FAIL");
        lm.fill("", ""); // leave the form empty
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** "LM" + yyMMddHHmmss (+ attempt) — 14-15 chars, within the Code box's maxlength of 15. */
    private static String uniqueCode(int attempt) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        return "LM" + ts + (attempt == 0 ? "" : String.valueOf(attempt));
    }

    /** Which of the URL / user / password settings were NOT supplied via env var or -D (names only, never values). */
    private static String missingConfig() {
        StringBuilder sb = new StringBuilder();
        if (!supplied("DEVHIS_URL", "devhis.url")) sb.append("DEVHIS_URL ");
        if (!supplied("DEVHIS_USER", "devhis.user")) sb.append("DEVHIS_USER ");
        if (!supplied("DEVHIS_PASS", "devhis.pass")) sb.append("DEVHIS_PASS ");
        return sb.toString().trim();
    }

    private static boolean supplied(String env, String prop) {
        String v = System.getProperty(prop);
        if (v != null && !v.isBlank()) return true;
        v = System.getenv(env);
        return v != null && !v.isBlank();
    }
}
