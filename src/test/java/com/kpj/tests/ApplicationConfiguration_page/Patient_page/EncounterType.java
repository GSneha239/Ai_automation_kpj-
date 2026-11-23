package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;
import java.util.Map;

// The page object is also named EncounterType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Encounter Type</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Encounter Type</b> (an inline screen: no Add button).</li>
 *   <li>Enter a unique <b>Code</b> and <b>Encounter Type</b>.</li>
 *   <li>Select the <b>Store</b>.</li>
 *   <li>Click <b>Submit</b> and verify the success message.</li>
 *   <li>Verify the new record in the table (Code, Encounter Type as Remark, Active).</li>
 * </ol>
 *
 * <p>&#9888; A passing run SAVES one REAL Encounter Type. Pin the store with {@code -Det.store=}.</p>
 */
public class EncounterType extends DevHisBase {

    private static final int MAX_ATTEMPTS = 10;

    public EncounterType() { super("ApplicationConfig_Patient_EncounterType"); }

    public static void main(String[] args) {
        EncounterType t = new EncounterType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Encounter Type", "Application Configuration > Patient > Encounter Type",
                "&#9888; SAVES one REAL Encounter Type: enter a unique Code and Encounter Type, select the Store, Submit, verify the success message and the new row in the table.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.EncounterType et =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.EncounterType(page);

        // 1) Navigate
        boolean on = et.navigateViaMenu();
        step(page, "Open Encounter Type screen", "Application Configuration -> Patient -> Encounter Type", "The Encounter Type screen is shown",
                on ? "Opened " + et.currentScreen() : "Did NOT reach the screen (" + et.currentScreen() + ")\n" + et.lastMenu, on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        // 2) Code + Encounter Type
        String filled = et.fillCodeAndType(0);
        Map<String, Object> m = et.model();
        boolean filledOk = et.lastCode.equals(String.valueOf(m.get("code"))) && et.lastType.equals(String.valueOf(m.get("desc")));
        byte[] filledPng = null;
        step(page, "Enter Code and Encounter Type", "Enter a unique Code and Encounter Type",
                "Both are entered", filledOk ? filled : "FAILED - the form holds " + m + " (" + filled + ")", filledOk ? "PASS" : "FAIL");
        if (!filledOk) { addSummary("Result", "FAILED - Code / Encounter Type not entered"); return; }

        // 3) Store
        List<String> stores = et.storeOptions();
        boolean stOk = et.selectStore(System.getProperty("et.store", ""));
        step(page, "Select Store", "Select a Store from the dropdown", "A Store is selected",
                stOk ? "Store '" + et.lastStore + "' selected (storeid " + et.model().get("storeid") + ")"
                     : "FAILED - no store selected; options " + stores, stOk ? "PASS" : "FAIL");
        if (!stOk) { addSummary("Result", "FAILED - Store not selected"); return; }

        // 4) Submit + success message — on 'already exists' a new Code / Encounter Type is entered and the store reselected
        String toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) { et.fillCodeAndType(attempt); et.selectStore(System.getProperty("et.store", "")); }
            used++;
            toast = et.submitAndGetToast();
            String tl = toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
        }
        String actual = toast.isEmpty() ? "No toast appeared | HTTP: " + et.lastSaveHttp
                : ok ? toast + (used > 1 ? " (saved on attempt " + used + ")" : "")
                : "Wrong/unexpected message - server returned: \"" + toast + "\" | HTTP: " + et.lastSaveHttp;
        if (et.toastPng != null && et.toastPng.length > 0) {
            step(et.toastPng, "Verify the success message", "Click Submit and read the success message", "A '... saved successfully' message",
                    actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Verify the success message", "Click Submit and read the success message", "A '... saved successfully' message",
                    actual, ok ? "PASS" : "FAIL");
        }
        if (!ok) { addSummary("Result", "FAILED - not saved: " + toast); return; }

        // 5) The table
        page.waitForTimeout(1500);
        List<Map<String, Object>> rows = et.tableRows();
        Map<String, Object> row = rows.stream().filter(r -> et.lastCode.equals(String.valueOf(r.get("code")))).findFirst().orElse(null);
        String problems = "";
        if (row == null) problems = "the new Code " + et.lastCode + " is not among the " + rows.size() + " row(s)";
        else {
            if (!et.lastType.equals(String.valueOf(row.get("remark")))) problems += "Encounter Type shows \"" + row.get("remark") + "\" not \"" + et.lastType + "\"; ";
            String st = String.valueOf(row.get("status"));
            if (!(st.equalsIgnoreCase("active") || st.equalsIgnoreCase("true") || st.equals("1"))) problems += "Status is \"" + st + "\" not Active; ";
        }
        boolean tableOk = problems.isEmpty();
        step(page, "Verify the table", "Look for the new Code in the table and check its Encounter Type and Status",
                "The saved record is listed with its Code, Encounter Type and Active status",
                tableOk ? "Listed: " + et.lastCode + " | " + row.get("remark") + " | " + row.get("status") + " (" + rows.size() + " row(s), columns [" + et.tableHeadings() + "])"
                        : "FAILED - " + problems.trim() + " (columns [" + et.tableHeadings() + "]; first rows " + rows.subList(0, Math.min(3, rows.size())) + ")",
                tableOk ? "PASS" : "FAIL");

        addSummary("Code", et.lastCode);
        addSummary("Encounter Type", et.lastType);
        addSummary("Store", et.lastStore);
        addSummary("Result", toast);
    }
}
