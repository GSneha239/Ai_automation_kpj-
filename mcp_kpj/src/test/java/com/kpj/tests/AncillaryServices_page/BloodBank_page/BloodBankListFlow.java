package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBankListScreen;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

/**
 * Shared flow of the Ancillary Services &gt; Blood Bank screens that are a list plus a bag-number driven Add form
 * (Red Cell Serology, Blood Bag Component Extraction). Nothing is saved.
 *
 * <ol>
 *   <li>Login; open the screen from the sidebar.</li>
 *   <li>Verify the list (columns, From Date / To Date / Blood Bag No., Search, Add); set From Date and Search; Search a bag number that does not exist.</li>
 *   <li>Click Add; verify the form's fields; search a bag number that does not exist on the form (no donor may appear).</li>
 *   <li>Click each button with nothing entered: the screen must refuse with its message and call no server write.</li>
 *   <li>Back to the list. The save itself needs a collected blood bag number and is left as a MANUAL step.</li>
 * </ol>
 */
abstract class BloodBankListFlow extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    protected BloodBankListFlow(String id) { super(id); }

    protected abstract BloodBankListScreen newScreen(Page page);
    protected abstract String title();
    protected abstract String path();
    protected abstract String[] listHeadings();
    protected abstract String[] addFormModels();
    /** {button text, fragment of the message it must show} for each button clicked on the empty form. */
    protected abstract String[][] validations();
    protected abstract String manualText();

    @Override
    protected void body() {
        meta(title(), path(),
                "Open the screen, check the list and its Search, open Add, check the form's fields, search a bag number that does not exist, and click the buttons "
                        + "with nothing entered (each must refuse with its message and send nothing). Nothing is saved.");
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.valueOf(System.currentTimeMillis() % 100000000L);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        BloodBankListScreen sc = newScreen(page);

        // 1) Navigate
        boolean on = sc.navigateViaMenu();
        if (!on) on = sc.navigateViaMenu();   // the sidebar / view load is slow on a busy day: one more walk
        if (!on) addSummary("Menu offered", sc.lastMenu);
        step(page, "Open " + shortName() + " screen", "Click Ancillary Services -> Blood Bank -> " + shortName(),
                "The " + shortName() + " list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ") — see the menu dump", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) The list
        String heads = sc.headings();
        StringBuilder noHead = new StringBuilder();
        for (String h : listHeadings()) if (!heads.toLowerCase().contains(h.toLowerCase())) noHead.append(noHead.length() > 0 ? ", " : "").append(h);
        String noCtl = sc.missingListControls();
        boolean listOk = noHead.length() == 0 && noCtl.isEmpty();
        step(page, "Verify the list screen", "Check the columns and the From Date, To Date, Blood Bag No. boxes, Search and Add",
                "All the columns and controls are there",
                listOk ? "Columns [" + heads + "]; From Date, To Date, Blood Bag No., Search and Add are present"
                       : "FAILED — " + (noHead.length() > 0 ? "columns missing: " + noHead + " (shown: " + heads + "); " : "") + (noCtl.isEmpty() ? "" : "controls missing: " + noCtl),
                listOk ? "PASS" : "FAIL");

        // 3) Search over a wide date range
        String set = sc.setFromDate("01/01/2020");
        int rows = sc.clickSearch();
        boolean dateOk = set.startsWith("01/01/2020");
        boolean searchOk = dateOk && rows >= 0 && sc.lastSearch.startsWith("Search sent its request");
        String first = rows > 0 ? sc.firstRows(5) : "";
        boolean bagsOk = !first.contains("(no bag no)");
        step(page, "Set From Date and click Search", "Set From Date to 01/01/2020 and click Search", "The search runs and the table shows what was found",
                searchOk && bagsOk ? "From Date \"" + set + "\"; " + sc.lastSearch + (rows > 0 ? "; first bag(s): " + first : " (nothing has been saved on this screen yet)")
                        : "FAILED — From Date \"" + set + "\"; " + sc.lastSearch + (bagsOk ? "" : "; a row has no Blood Bag No.: " + first),
                searchOk && bagsOk ? "PASS" : "FAIL");

        // 4) A bag number that does not exist
        sc.fillListBag("NOBAG" + stamp);
        int none = sc.clickSearch();
        step(page, "Search a bag number that does not exist", "Type Blood Bag No. NOBAG" + stamp + " and click Search", "The table is empty",
                none == 0 ? "The table holds 0 rows (" + sc.lastSearch + ")" : "FAILED — " + sc.lastSearch,
                none == 0 ? "PASS" : "FAIL");

        // 5) Add form
        boolean add = sc.clickAdd();
        step(page, "Click Add", "Click Add", "The add form opens", add ? "The add form opened (" + page.url() + ")" : "FAILED — the add form did not open (" + page.url() + ")", add ? "PASS" : "FAIL");
        if (!add) { addSummary("Result", "FAILED — add form not reached"); return; }

        String models = sc.formModels();
        StringBuilder noField = new StringBuilder();
        for (String m : addFormModels()) if (!(("," + models.toLowerCase() + ",").contains("," + m.toLowerCase() + ","))) noField.append(noField.length() > 0 ? ", " : "").append(m);
        step(page, "Verify the add form fields", "Check every entry field of the form is shown", "All the form's fields are shown",
                noField.length() == 0 ? "All " + addFormModels().length + " fields are shown" : "FAILED — missing: " + noField + " (shown: " + models + ")",
                noField.length() == 0 ? "PASS" : "FAIL");

        // 6) Buttons with nothing entered (BEFORE any bag number is typed: with a bag number in the form the screen asks for other fields instead)
        for (String[] v : validations()) {
            String msg = sc.clickAndRead(v[0]);
            boolean okMsg = msg.toLowerCase().contains(v[1].toLowerCase());
            boolean noWrite = sc.lastWrites.isEmpty();
            step(page, "Click " + v[0] + " with nothing entered", "Click " + v[0] + " on the empty form", "The screen says \"" + v[1] + "\" and sends nothing to the server",
                    okMsg && noWrite ? "Message \"" + msg + "\"; no server request was sent"
                            : "FAILED — message \"" + msg + "\"" + (noWrite ? "" : "; the click sent: " + sc.lastWrites),
                    okMsg && noWrite ? "PASS" : "FAIL");
        }

        // 7) A bag number that does not exist, on the form
        String ans = sc.searchBagOnForm("NOBAG" + stamp);
        String donor = sc.donorNameValue();
        boolean noDonor = donor.isEmpty() || donor.startsWith("(no donor box");
        boolean asked = ans.startsWith("Search for bag");
        step(page, "Search a bag number that does not exist on the form", "Type Blood Bag No. NOBAG" + stamp + " and click the form's Search",
                "No bag is found and no donor appears",
                asked && noDonor ? ans + "; the donor name box stays empty" : "FAILED — " + ans + "; donor name box shows \"" + donor + "\"",
                asked && noDonor ? "PASS" : "FAIL");

        // 8) Back
        boolean back = sc.clickBack();
        step(page, "Click Back", "Click Back on the form", "The list is shown again", back ? "Back on " + page.url() : "FAILED — still on " + page.url(), back ? "PASS" : "FAIL");

        // 9) The save needs data this environment does not hold
        step(page, "Save a record for a collected blood bag", manualText(),
                "A record is saved and listed", "Not run: the form finds a blood bag by its exact number and no collected bag number is available here (the list is empty since 01/01/2020)", "MANUAL");

        addSummary("Records since 01/01/2020", rows < 0 ? "(no grid)" : String.valueOf(rows));
    }

    private String shortName() {
        String p = path();
        return p.substring(p.lastIndexOf('>') + 1).trim();
    }
}
