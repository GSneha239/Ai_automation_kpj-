package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DmsFolder — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>DMS Folder</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>DMS Folder</b>.</li>
 *   <li>Enter the <b>Folder Name</b>.</li>
 *   <li>Set the <b>Colour</b> (an RGB value).</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The folder name is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -DfolderName=}; the colour comes from {@code -Drgb=} / {@code -Dhex=}.</p>
 *
 * <p>The requested steps do not mention Add — this screen may present its form directly. The flow clicks
 * Add only if the form is not already on screen, and reports which it found, rather than failing on a
 * button that is not part of the design.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL DMS folder in the target environment.</p>
 */
public class DmsFolder extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public DmsFolder() { super("ApplicationConfiguration_DmsFolder"); }

    public static void main(String[] args) {
        DmsFolder t = new DmsFolder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("DMS Folder", "Application Configuration > General > DMS Folder",
                "&#9888; Creates a REAL DMS folder: enter the Folder Name, set the Colour, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String folderName = System.getProperty("folderName", "Auto Folder " + stamp);
        String rgb = System.getProperty("rgb", "255,0,0");
        String hex = System.getProperty("hex", "#ff0000");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.DmsFolder df =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.DmsFolder(page);

        // 1) Navigate
        boolean rendered = df.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", df.lastMenu);
        step(page, "Open DMS Folder screen",
                "Click Application Configuration -> General -> DMS Folder",
                "The DMS Folder screen is shown",
                rendered ? "Opened " + page.url()
                           + (df.lastRoute.isEmpty() ? "" : " (menu route " + df.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", df.describeControls());

        // The steps do not mention Add: click it only if the entry form is not already showing.
        boolean formReady = df.formOpen();
        if (!formReady) {
            df.clickAdd();
            formReady = df.formOpen();
            addSummary("Add", formReady ? "The form needed Add; it is now open." : "Add did not open a form.");
        } else {
            addSummary("Add", "Not needed — the route opens the entry form directly.");
        }
        addSummary("Form controls", df.describeControls());

        // 2) Folder name
        String name = df.enterFolderName(folderName);
        step(page, "Enter folder name", "Enter the Folder Name " + folderName,
                "The folder name is entered", name,
                df.folderNameEntered(folderName) ? "PASS" : "FAIL");

        // 3) Colour (RGB)
        String colour = df.setColour(rgb, hex);
        step(page, "Select colour - enter RGB", "Set the colour to RGB " + rgb + " (" + hex + ")",
                "The colour is set", colour, df.colourSet() ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = df.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.DmsFolder.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = df.codeInList(folderName);
        addSummary("List check", df.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + df.lastSaveDiagnostics
                  + (inList ? " BUT the folder IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the folder IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the folder is NOT in the list. "
                              + df.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the folder was created", "Look for " + folderName + " in the list",
                "The new folder is listed", df.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Folder name", folderName);
        addSummary("Colour", rgb + " / " + hex + " -> " + df.lastCity);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
