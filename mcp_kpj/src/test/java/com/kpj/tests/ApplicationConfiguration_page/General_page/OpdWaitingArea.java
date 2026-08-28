package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OpdWaitingArea — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>OPD Waiting Area</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>OPD Waiting Area</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and <b>Remark</b>.</li>
 *   <li>In <b>Consultation Room</b>: tick a cabin.</li>
 *   <li>In <b>Modality</b>: tick a modality.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The two tick lists are the risk here: they carry the same kind of checkbox, so an unscoped lookup
 * ticks the first list twice and leaves the second empty while reporting both steps as passed. Each tick
 * is scoped to its own section and read back, and the run first reports how many boxes each section owns
 * so the scoping is visible rather than assumed.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL OPD waiting area in the target environment.</p>
 */
public class OpdWaitingArea extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public OpdWaitingArea() { super("ApplicationConfiguration_OpdWaitingArea"); }

    public static void main(String[] args) {
        OpdWaitingArea t = new OpdWaitingArea();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OPD Waiting Area", "Application Configuration > General > OPD Waiting Area",
                "&#9888; Creates a REAL OPD waiting area: Add, enter Code and Remark, tick a cabin and a "
                        + "modality, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "WA" + stamp);
        String remark = System.getProperty("remark", "Automated waiting area " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.OpdWaitingArea ow =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.OpdWaitingArea(page);

        // 1) Navigate
        boolean rendered = ow.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", ow.lastMenu);
        step(page, "Open OPD Waiting Area screen",
                "Click Application Configuration -> General -> OPD Waiting Area",
                "The OPD Waiting Area screen is shown",
                rendered ? "Opened " + page.url()
                           + (ow.lastRoute.isEmpty() ? "" : " (menu route " + ow.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ow.describeControls());

        // 2) Add
        boolean formOpen = ow.clickAdd();
        addSummary("Form controls", ow.describeControls());
        step(page, "Click Add", "Click Add to open the waiting area form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Remark
        String entry = ow.enterCodeAndRemark(code, remark);
        step(page, "Enter code and remark", "Enter the Code " + code + " and the Remark",
                "Both are entered", entry, ow.detailsEntered(code) ? "PASS" : "FAIL");

        // Show how many boxes each section owns BEFORE ticking, so the scoping is evidenced.
        addSummary("Tickable per section", ow.describeSections());

        // 4) Consultation Room -> cabin
        String cabin = ow.tickCabin();
        step(page, "In Consultation Room, tick the checkbox for a cabin",
                "Tick a cabin in the Consultation Room section",
                "A cabin is ticked", cabin,
                com.kpj.pages.ApplicationConfiguration_page.General_page.OpdWaitingArea.ticked(cabin) ? "PASS" : "FAIL");

        // 5) Modality -> modality
        String modality = ow.tickModality();
        step(page, "In Modality, tick the checkbox for a modality",
                "Tick a modality in the Modality section",
                "A modality is ticked", modality,
                com.kpj.pages.ApplicationConfiguration_page.General_page.OpdWaitingArea.ticked(modality) ? "PASS" : "FAIL");

        // 6) Save -> toast
        String toast = ow.saveAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.OpdWaitingArea.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = ow.codeInList(code);
        addSummary("List check", ow.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ow.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the waiting area IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Save answered \"" + toast + "\" and the record is NOT in the list. "
                              + ow.lastListCheck)
                        : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the waiting area was created", "Look for " + code + " in the list",
                "The new waiting area is listed", ow.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Cabin", ow.lastCabin);
        addSummary("Modality", ow.lastModality);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
