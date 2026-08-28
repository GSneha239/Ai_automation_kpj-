package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named NursingStationDefinition — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Nursing Station Definition</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Nursing Station Definition</b>; click <b>Add</b>.</li>
 *   <li>Enter Code, Remark, Floor, Ward; tick Department / Room List / Bed List.</li>
 *   <li>Click inner <b>Add</b> ({@code AddDetails}), then <b>Save</b> ({@code IUDSaveTree}); wait for the success toast.</li>
 * </ol>
 */
public class NursingStationDefinition extends DevHisBase {

    public NursingStationDefinition() { super("NursingStation_NursingStationDefinition"); }

    public static void main(String[] args) {
        NursingStationDefinition t = new NursingStationDefinition();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Nursing Station Definition", "Nursing Station > Nursing Station Definition",
                "Add a Nursing Station Definition: Code/Remark/Floor/Ward + Department/Room/Bed selection, inner Add, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.NursingStationDefinition ns =
                new com.kpj.pages.NursingStation_page.NursingStationDefinition(page);

        boolean onScreen = ns.navigateTo(BASE);
        step(page, "Open Nursing Station Definition screen", "Nursing Station -> Nursing Station Definition",
                "The list screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = ns.clickTopAdd();
        step(page, "Click Add", "Click Add (AddNursingStationDefination) to open the definition form",
                "The add form is shown", added ? "Add form opened" : "Add form did not open",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add form not opened"); return; }

        String header = ns.fillHeader();
        boolean headerOk = !header.contains("Floor=(no") && !header.contains("Ward=(no") && header.contains("Code=NSD");
        step(page, "Enter Code, Remark, Floor, Ward", "Enter Code + Remark; select Location, Floor, Ward",
                "The header fields are filled", header, headerOk ? "PASS" : "FAIL");

        // Find a Floor+Ward whose Room List is enabled (empty wards won't work — try others).
        String fw = ns.findWardWithRoomList(14);
        step(page, "Find a Ward with a Room List", "Iterate Floor/Ward until the Room List has selectable items",
                "A ward whose Room List is enabled is selected",
                fw.isEmpty() ? "No ward with a Room List found across the floors" : "Using " + fw,
                fw.isEmpty() ? "FAIL" : "PASS");
        if (fw.isEmpty()) { addSummary("Result", "No ward has a Room List configured in this environment"); return; }

        // Select Department List, Room List and Bed List.
        // In IPD the Department List is disabled by design — that is reported, not forced.
        String sel = ns.selectDepartmentRoomBed();
        boolean selOk = !sel.contains("RoomChecked=0");
        step(page, "Select Department / Room / Bed List",
                "Tick the checkboxes in the Department List, Room List and Bed List sections",
                "The department/room/bed selections are ticked", sel, selOk ? "PASS" : "FAIL");

        // Click Add (AddDetails) after selecting Room List + Bed List.
        String addDetails = ns.clickAddDetails();
        boolean addBlocked = addDetails.toLowerCase().matches(".*(please|enter|select|required|mandatory|fill).*");
        step(page, "Click Add", "Click Add (AddDetails) to add the Floor/Ward/Room/Bed selection to the tree",
                "The selection is added", (addDetails.isEmpty() ? "Add clicked" : addDetails) + " | treeRows=" + ns.treeRows(),
                addBlocked ? "FAIL" : "PASS");

        String toast = ns.clickSave();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (IUDSaveTree); wait for the success toast",
                "A success toast appears", actual, ok ? "PASS" : "FAIL");

        addSummary("Code", ns.lastCode);
        addSummary("Floor / Ward", ns.lastFloor + " / " + ns.lastWard);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
