package com.kpj.tests.AncillaryServices_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AmbulanceStaff — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Ambulance &gt; <b>AmbulanceStaff</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Ambulance</b> → <b>AmbulanceStaff</b>.</li>
 *   <li>Select <b>Staff Type</b> and <b>Staff Name</b>.</li>
 *   <li>Click <b>Add</b> → verify the success toast.</li>
 *   <li>Tick the row's <b>select</b> box.</li>
 *   <li>Click <b>Edit</b>.</li>
 *   <li>Select <b>Staff Name</b> (a different one where the list offers more than one).</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES and then EDITS a real ambulance staff roster entry.</p>
 */
public class AmbulanceStaff extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AmbulanceStaff() { super("AncillaryServices_Ambulance_AmbulanceStaff"); }

    public static void main(String[] args) {
        AmbulanceStaff t = new AmbulanceStaff();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ambulance Staff", "Ancillary Services > Ambulance > AmbulanceStaff",
                "&#9888; Creates then edits a REAL ambulance staff roster entry: select Staff Type + Staff Name, "
                        + "Add, tick the row, Edit, change Staff Name, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceStaff staff =
                new com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceStaff(page);

        // 1) Navigate
        boolean onScreen = staff.navigateViaMenu();
        step(page, "Open AmbulanceStaff screen",
                "Click Ancillary Services -> Ambulance -> AmbulanceStaff",
                "The AmbulanceStaff roster screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        staff.describeControls();   // diagnostics only

        // 2) Staff Type + Staff Name
        String picked = staff.selectStaffTypeAndName();
        boolean pickedOk = staff.staffTypeAndNameSelected();
        step(page, "Select Staff Type and Staff Name", "Select a Staff Type, then a Staff Name",
                "Staff Type and Staff Name are both selected", picked, pickedOk ? "PASS" : "FAIL");
        if (!pickedOk) { addSummary("Result", "FAILED — could not select Staff Type / Staff Name"); return; }

        // 3) Add -> toast
        String addToast = staff.clickAddAndGetToast();
        String at = addToast == null ? "" : addToast.toLowerCase();
        boolean addOk = at.contains("success") || at.contains("added") || at.contains("saved");
        step(page, "Click Add & success toast", "Click Add (addStaffRoster); wait for the success toast",
                "'... added successfully' toast",
                addToast == null || addToast.isEmpty() ? "No toast appeared"
                        : (addOk ? addToast : "Add not confirmed — server returned: \"" + addToast + "\""),
                addOk ? "PASS" : "FAIL");

        // 4) Tick the row's select box — must be the row we just added, not merely any row
        String ticked = staff.tickSelectCheckbox();
        boolean tickOk = ticked != null && !ticked.startsWith("(") && !ticked.startsWith("WARNING");
        step(page, "Click select tick box", "Tick the roster row's select checkbox (staff.selected)",
                "The row just added is selected", ticked, tickOk ? "PASS" : "FAIL");

        // 5) Edit — report the element actually clicked so a wrong match cannot pass silently
        String edited = staff.clickEdit();
        boolean editOk = edited != null && !edited.isEmpty();
        step(page, "Click Edit", "Click Edit for the selected row",
                "The row becomes editable",
                editOk ? "Edit clicked -> " + edited
                       : "No Edit control found on this screen (see the controls dump in the console)",
                editOk ? "PASS" : "FAIL");

        // 6) Change Staff Name
        String newName = staff.selectStaffNameForEdit();
        boolean nameOk = newName != null && !newName.isEmpty() && !newName.startsWith("(");
        step(page, "Select Staff Name", "Select a Staff Name for the row being edited",
                "A Staff Name is selected",
                nameOk ? "Staff Name = " + newName : "Staff Name NOT selected " + newName,
                nameOk ? "PASS" : "FAIL");

        // 7) Save -> toast
        String saveToast = staff.clickSaveAndGetToast();
        String st = saveToast == null ? "" : saveToast.toLowerCase();
        boolean saveOk = st.contains("success") || st.contains("saved") || st.contains("updated");
        step(page, "Click Save & success toast", "Click Save (saveStaffRoster); wait for the success toast",
                "'... saved successfully' toast",
                saveToast == null || saveToast.isEmpty() ? "No toast appeared"
                        : (saveOk ? saveToast : "Save not confirmed — server returned: \"" + saveToast + "\""),
                saveOk ? "PASS" : "FAIL");

        addSummary("Staff Type", staff.lastStaffType);
        addSummary("Staff Name (added)", staff.lastStaffName);
        addSummary("Staff Name (edited)", staff.lastStaffNameEdited);
        addSummary("Add result", addOk ? addToast : "Not confirmed (\"" + addToast + "\")");
        addSummary("Save result", saveOk ? saveToast : "Not confirmed (\"" + saveToast + "\")");
    }
}
