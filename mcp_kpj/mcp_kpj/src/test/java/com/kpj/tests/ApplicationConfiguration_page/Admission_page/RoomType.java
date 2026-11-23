package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named RoomType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Room Type</b>.
 *
 * <ol>
 *   <li>Read the Room Types that already exist in the table (all pages), so the new ones can differ from them.</li>
 *   <li>Click <b>Add</b>; enter the <b>Code</b>; enter the <b>Room Type</b> — a name and a code that are not in the table (ignoring case); click <b>Submit</b>.</li>
 *   <li>Verify the success toast, and that the new room type now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate (a check the screen does and the table cannot show), the next unused name is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL ROOM TYPE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class RoomType extends DevHisBase {

    public RoomType() { super("ApplicationConfiguration_Admission_RoomType"); }

    public static void main(String[] args) {
        RoomType t = new RoomType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Room Type", "Application Configuration > Admission > Room Type",
                "Read the existing room types; Add; enter a new Code; enter a new Room Type (not in the table); Submit; verify the success toast. SAVES A REAL ROOM TYPE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.RoomType rt = new com.kpj.pages.ApplicationConfiguration_page.Admission_page.RoomType(page);

        boolean on = rt.navigateViaMenu();
        step("Open Room Type", "Click Application Configuration -> Admission -> Room Type", "The Room Type list is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Room Type not reached"); return; }

        List<String[]> rows = rt.awaitExisting();
        boolean haveList = !rows.isEmpty();
        String sample = rows.stream().limit(5).map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the room types already in the table", "Read the Room Type table (all pages)", "The existing codes and room types are known",
                haveList ? rows.size() + " room type(s) exist (pager: \"" + rt.pagerText() + "\"), e.g. " + sample : "The table listed no rows", haveList ? "PASS" : "FAIL");
        if (!haveList) { addSummary("Result", "FAILED — the existing room types could not be read"); return; }

        boolean opened = rt.clickAdd();
        step("Click Add", "Click Add", "The Room Type form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + rt.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + rt.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", name = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = rt.pickNew(rows, attempt);
            code = pick[0];
            name = pick[1];
            boolean codeOk = rt.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (not among the " + rows.size() + " existing codes)" : "Problem — " + rt.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { if (attempt != 0) step("Enter code (retry)", "Enter the Code " + code, "The code is accepted", "Problem — " + rt.problems, "FAIL"); addSummary("Result", "FAILED — code: " + rt.problems); return; }
            boolean typeOk = rt.enterRoomType(name);
            if (attempt == 0) step("Enter room type", "Enter a Room Type that is not in the table (" + name + ")", "The room type is accepted",
                    typeOk ? "Room Type \"" + name + "\" entered (different from every one of the " + rows.size() + " existing room types)" : "Problem — " + rt.problems, typeOk ? "PASS" : "FAIL");
            if (!typeOk) { if (attempt != 0) step("Enter room type (retry)", "Enter the Room Type " + name, "The room type is accepted", "Problem — " + rt.problems, "FAIL"); addSummary("Result", "FAILED — room type: " + rt.problems); return; }

            toast = rt.submitAndGetToast();
            ok = rt.ok() && rt.saveSucceeded();
            if (!ok && rt.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + name + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The room type is submitted", rt.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + rt.problems,
                rt.problems.isEmpty() ? "PASS" : "FAIL");

        // the new room type should now be in the list (the screen returns to it after a save)
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : rt.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(name)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (rt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + rt.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + rt.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + rt.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Existing room types before", String.valueOf(rows.size()));
        addSummary("Code / Room Type added", code + " / " + name);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
