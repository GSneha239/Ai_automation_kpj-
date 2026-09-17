package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ItemFactorizationTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Item Factorization Compounding Template</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Item Factorization Compounding
 *       Template</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Template Code</b> and <b>Template Name</b>.</li>
 *   <li>Select the <b>Store</b>.</li>
 *   <li>In the list of items: enter the <b>Item Code</b>, <b>Item Name</b> and <b>Quantity</b>, and
 *       select <b>Added/Deducted</b>.</li>
 *   <li>Click the <b>+</b> control — the item must appear as a row.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The template's models and the item line's models share a prefix
 * ({@code …Template.} and {@code …TemplateDetails.}), so every field is pinned to its EXACT model: a
 * keyword match on "code" would put the template code into the item code box with both steps still
 * looking right.</p>
 *
 * <p>Values are generated per run, since configuration screens reject a duplicate. Pin them with
 * {@code -Dcode=}, {@code -Dname=}, {@code -DitemCode=}, {@code -DitemName=}, {@code -Dqty=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL template in the target environment.</p>
 */
public class ItemFactorizationTemplate extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemFactorizationTemplate() { super("ApplicationConfiguration_Inventory_ItemFactorizationTemplate"); }

    public static void main(String[] args) {
        ItemFactorizationTemplate t = new ItemFactorizationTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Item Factorization Compounding Template",
                "Application Configuration > Inventory > Item Factorization Compounding Template",
                "&#9888; Creates a REAL template: Add, enter the Template Code and Name, select the "
                        + "Store, enter an item line (code, name, quantity, Added/Deducted), click +, "
                        + "then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        int seed = Integer.getInteger("seed", Integer.parseInt(stamp));
        String code = System.getProperty("code", "IFT" + stamp);
        String name = System.getProperty("name", "Auto template " + stamp);
        // Each value carries the run's stamp, so a value landing in a neighbouring box is visible.
        String itemCode = System.getProperty("itemCode", "ITC" + stamp);
        String itemName = System.getProperty("itemName", "Auto item " + stamp);
        String qty = System.getProperty("qty", "3");
        // What to type into the item lookup. Any prefix with several matches will do — two DIFFERENT
        // items are needed, one for the addition line and one for the deduction line.
        String itemSearch = System.getProperty("itemSearch", "para");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemFactorizationTemplate ift =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemFactorizationTemplate(page);
        // Native confirms are recorded and accepted; left to Playwright's default they are dismissed and
        // the screen's question never reaches the report.
        ift.captureDialogs();

        // 1) Navigate
        boolean rendered = ift.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", ift.lastMenu);
        step(page, "Open Item Factorization Compounding Template screen",
                "Click Application Configuration -> Inventory -> Item Factorization Compounding Template",
                "The screen is shown",
                rendered ? "Opened " + page.url()
                           + (ift.lastRoute.isEmpty() ? "" : " (menu route " + ift.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ift.describeControls());

        // 2) Add
        String add = ift.openFormIfNeeded();
        boolean formOpen = ift.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", ift.describeControls());

        // 3) Template code + name
        String tpl = ift.enterTemplate(code, name);
        boolean tplOk = ift.templateEntered(code, name);
        step(page, "Enter template code and template name",
                "Enter the Template Code " + code + " and the Template Name " + name,
                "Both are entered",
                (tplOk
                    ? "PASSES because each box accepted its own value and read it back: " + tpl
                      + ". Both are pinned to their exact model — the item line's models start with the "
                      + "same text, so a keyword match would fill the wrong box."
                    : "FAILS because a value did not land in its own field: " + tpl),
                tplOk ? "PASS" : "FAIL");

        // 4) Store
        String store = ift.selectStore(seed);
        boolean storeOk = ift.storeSelected();
        step(page, "Select the store", "Select the Store",
                "A store is selected",
                (storeOk
                    ? "PASSES because the dropdown holds the choice: " + store
                    : "FAILS because no store could be selected: " + store),
                storeOk ? "PASS" : "FAIL");

        // 5) Item line — the code and name boxes are an autocomplete over the item master
        String pick = ift.pickItem(itemSearch, 0);
        String line = ift.enterQuantityOnly(qty);
        boolean lineOk = ift.itemLineBound(qty);
        step(page, "Enter item code, item name and quantity",
                "In the list of items, enter the Item Code and Item Name (typed into the item lookup) and "
                        + "the Quantity " + qty,
                "The item and the quantity are on the line",
                (lineOk
                    ? "PASSES because the item was taken from the screen's own item lookup and the "
                      + "quantity read back: " + pick + " -> " + line
                      + ". WHY THE ITEM IS PICKED RATHER THAN TYPED: the Item Code and Item Name boxes "
                      + "are an auto-complete over the item master. Free text leaves the line's itemid "
                      + "EMPTY, and the + control compares lines by itemid — two typed lines both carry "
                      + "\"\" and the screen treats them as the same item."
                    : "FAILS — the line is not complete: " + pick + " -> " + line),
                lineOk ? "PASS" : "FAIL");

        // 6) Added / Deducted
        // Named explicitly, never "the first real option": this line must be the ADDITION, and the
        // screen refuses to save unless it holds one addition and one deduction.
        String ad = ift.selectAddedDeducted("added");
        boolean adOk = ift.addedDeductedSelected();
        step(page, "Select added/deducted", "Select Added or Deducted",
                "Added or Deducted is selected",
                (adOk
                    ? "PASSES because the dropdown holds the choice: " + ad
                    : "FAILS because neither Added nor Deducted could be selected: " + ad),
                adOk ? "PASS" : "FAIL");

        // 7) The + control
        String plus = ift.clickPlus();
        boolean rowAdded = ift.itemRowAdded();
        step(page, "Click + to add the item",
                "Click the + button to add the item to the list",
                "A new row appears in the item list",
                (rowAdded
                    ? "PASSES because the list grew — " + plus + ". The + carries no text at all, so it is "
                      + "found by its handler rather than a label, and the click alone is not credited: "
                      + "the row count before and after is what says the item was added."
                    : "FAILS because the list did not grow — " + plus),
                rowAdded ? "PASS" : "FAIL");

        // The screen refuses to save a template that has only one kind of line: Submit answers "Please
        // add atleast one addition behavior and one deduction behavior!". So a DEDUCTED line is added
        // too. That is the screen's own rule, not an extra step invented here, and it is reported as its
        // own step rather than folded silently into the one above.
        // A DIFFERENT item — the second suggestion. Two lines carrying the same item make the screen ask
        // "Same item already added" instead of adding the line.
        String pick2 = ift.pickItem(itemSearch, 1);
        String line2 = ift.enterQuantityOnly(qty) + " (" + pick2 + ")";
        String ad2 = ift.selectAddedDeducted("deduct");
        String plus2 = ift.clickPlus();
        boolean secondAdded = ift.itemRowAdded();
        step(page, "Add a second item line as Deducted",
                "Enter a second item line and select Deducted, then click +",
                "A second line is added, so the template holds one addition and one deduction",
                (secondAdded
                    ? "PASSES because the controller took a second line — " + plus2
                      + ". WHY THIS STEP EXISTS: Submit refuses a template with only one kind of line, "
                      + "answering \"Please add atleast one addition behavior and one deduction "
                      + "behavior!\". The line: " + line2 + " | Added/Deducted = " + ad2
                    : "FAILS because the second line was not taken — " + plus2),
                secondAdded ? "PASS" : "FAIL");

        // 8) Submit -> toast
        String toast = ift.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemFactorizationTemplate.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = ift.codeInList(code, name);
        addSummary("List check", ift.lastListCheck);
        addSummary("What the screen put on screen after Submit", ift.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ift.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the template IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + ift.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (ift.toastFromObserver
                        ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five seconds. This one "
                          + "was recorded by a mutation observer at the moment the screen showed it, so it "
                          + "is the screen's own text and not a reconstruction."
                        : "")
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Template", code + " / " + name);
        addSummary("Store", ift.lastStore);
        addSummary("Item line", ift.lastItemLine + " | " + ift.lastAddedDeducted);
        addSummary("Native dialogs raised", ift.lastDialogs.isEmpty() ? "none" : ift.lastDialogs);
        addSummary("Added row", ift.lastPlus);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
