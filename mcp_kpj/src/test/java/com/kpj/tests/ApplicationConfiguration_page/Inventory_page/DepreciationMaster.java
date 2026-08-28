package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DepreciationMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Depreciation Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Depreciation Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Depreciation Code</b> and <b>Depreciation Description</b>.</li>
 *   <li>Select the <b>Depreciation Name</b>.</li>
 *   <li>Click <b>Add Depreciation</b> to put the name into the formula.</li>
 *   <li>Click the <b>mathematical symbols</b> to build the formula.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}; the operators with {@code -Dsymbols=}.</p>
 *
 * <p>The formula is built by <b>concatenation</b>, not evaluated: Add appends the number to the formula
 * box and each symbol key appends that character. So the steps are judged on the formula box growing by
 * the right characters, never on any arithmetic result.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL depreciation record in the target environment.</p>
 */
public class DepreciationMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public DepreciationMaster() { super("ApplicationConfiguration_DepreciationMaster"); }

    public static void main(String[] args) {
        DepreciationMaster t = new DepreciationMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Depreciation Master", "Application Configuration > Inventory > Depreciation Master",
                "&#9888; Creates a REAL depreciation record: Add, enter the code and description, select "
                        + "the depreciation name, click Add Depreciation, build the formula from "
                        + "the mathematical symbols, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "DP" + stamp);
        String description = System.getProperty("description", "Auto depreciation " + stamp);
        String[] symbols = System.getProperty("symbols", "+").split(",");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DepreciationMaster dm =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DepreciationMaster(page);

        // 1) Navigate
        boolean rendered = dm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", dm.lastMenu);
        step(page, "Open Depreciation Master screen",
                "Click Application Configuration -> Inventory -> Depreciation Master",
                "The Depreciation Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (dm.lastRoute.isEmpty() ? "" : " (menu route " + dm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", dm.describeControls());

        // 2) Add
        boolean formOpen = dm.clickAdd();
        addSummary("Form controls", dm.describeControls());
        step(page, "Click Add", "Click Add to open the depreciation form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Description
        String entry = dm.enterDetails(code, description);
        boolean entered = dm.detailsEntered(code, description);
        step(page, "Enter depreciation code and description",
                "Enter the Depreciation Code " + code + " and the Depreciation Description",
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Depreciation Name. Which one is selectable with -DnameIndex=, because varying it is how the
        // formula's contents were traced back to their real source.
        String name = dm.selectDepreciationName(Integer.getInteger("nameIndex", 0));
        boolean nameOk = dm.nameSelected();
        step(page, "Select depreciation name", "Select the Depreciation Name",
                "A depreciation name is selected",
                (nameOk
                    ? "PASSES because the dropdown holds the chosen name: " + name
                    : "FAILS because no depreciation name could be selected: " + name),
                nameOk ? "PASS" : "FAIL");

        // 5) Add Depreciation — puts the selected name into the formula.
        String addDep = dm.clickAddDepreciation();
        boolean addDepOk = dm.depreciationAdded();
        step(page, "Click Add Depreciation",
                "Click Add Depreciation (funSetDepreciationName) to put the selected name into the formula",
                "The depreciation name is added to the formula",
                (addDepOk
                    ? "PASSES because the formula grew when the button was clicked — judged on the formula "
                      + "box, not on the click: " + addDep
                    : "FAILS because the formula did not change when Add Depreciation was clicked: "
                      + addDep),
                addDepOk ? "PASS" : "FAIL");

        // 6) Mathematical symbols -> formula
        String steps = dm.clickSymbols(symbols);
        boolean formulaOk = dm.formulaBuilt();
        step(page, "Click the mathematical symbols to create the formula",
                "Click the mathematical symbols " + String.join(" ", symbols),
                "Each symbol is added to the formula",
                (formulaOk
                    ? "PASSES because every symbol clicked changed the formula: " + steps
                      + "  ||  final formula: \"" + dm.lastFormula + "\""
                    : "FAILS because a symbol did not reach the formula — checked one at a time, since a "
                      + "pad that swallows one operator still looks clicked. Note the pad's handlers are "
                      + "crossed: \"(\" is wired to funSetRightparenthesis() and \")\" to "
                      + "funSetLeftparenthesis(). Each key was clicked by its own handler, not by "
                      + "matching text, so this is the button itself doing nothing. Evidence: " + steps),
                formulaOk ? "PASS" : "FAIL");

        // 7) Submit -> toast
        String toast = dm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DepreciationMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = dm.codeInList(code, description);
        addSummary("List check", dm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + dm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + dm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        step(page, "Verify the depreciation record was created", "Look for " + code + " in the list",
                "The new depreciation record is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its description — was found in the "
                      + "list: " + dm.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + dm.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Description", code + " / " + description);
        addSummary("Depreciation Name", dm.lastName);
        addSummary("Add Depreciation", dm.lastAddDepreciation);
        addSummary("Formula", dm.lastFormula + "  ||  " + dm.lastFormulaSteps);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
