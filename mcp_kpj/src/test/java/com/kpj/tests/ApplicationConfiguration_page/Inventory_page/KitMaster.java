package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named KitMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>KIT Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>KIT Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>KIT Code</b> and <b>KIT Name</b>.</li>
 *   <li>In the <b>KIT Information</b> list: select the <b>Drug Name</b> and enter the <b>Quantity</b>.</li>
 *   <li>Click <b>Add</b> — the drug must appear as a row.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Drug Name is a uib-typeahead ({@code kit.itemPicker} over {@code drpItemList}): it opens only
 * for real keystrokes, so the flow reads an existing drug out of the list, types it, and clicks the
 * suggestion rather than writing the model.</p>
 *
 * <p>The KIT Code is generated per run, since configuration screens reject a duplicate. Pin values with
 * {@code -Dcode=}, {@code -Dname=}, {@code -Dqty=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL kit in the target environment.</p>
 */
public class KitMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public KitMaster() { super("ApplicationConfiguration_KitMaster"); }

    public static void main(String[] args) {
        KitMaster t = new KitMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("KIT Master", "Application Configuration > Inventory > KIT Master",
                "&#9888; Creates a REAL kit: Add, enter KIT Code and KIT Name, then in KIT Information "
                        + "select a Drug Name and Quantity, Add the line, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "KIT" + stamp);
        String name = System.getProperty("name", "Auto KIT " + stamp);
        // A quantity unlikely to occur by chance, so the added row can be told from any other.
        String qty = System.getProperty("qty", "7");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.KitMaster km =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.KitMaster(page);

        // 1) Navigate
        boolean rendered = km.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", km.lastMenu);
        step(page, "Open KIT Master screen",
                "Click Application Configuration -> Inventory -> KIT Master",
                "The KIT Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (km.lastRoute.isEmpty() ? "" : " (menu route " + km.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", km.describeControls());

        // 2) Add
        String add = km.openFormIfNeeded();
        boolean formOpen = km.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", km.describeControls());

        // 3) KIT Code + KIT Name
        String entry = km.enterKitDetails(code, name);
        boolean entered = km.kitDetailsEntered(code, name);
        step(page, "Enter KIT code and KIT name",
                "Enter the KIT Code " + code + " and the KIT Name " + name,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) KIT Information: drug name
        String drug = km.selectDrug();
        boolean drugOk = km.drugSelected();
        step(page, "Select the drug name", "In KIT Information, select the Drug Name",
                "A drug is selected from the list",
                (drugOk
                    ? "PASSES because the typeahead opened and the chosen item is BOUND to the model, not "
                      + "merely typed: " + drug
                    : "FAILS — the drug is not bound: " + drug
                      + ". The picker is a uib-typeahead: a value written straight into the model leaves "
                      + "it holding a plain string and the Add has nothing to add."),
                drugOk ? "PASS" : "FAIL");

        // 5) Quantity
        String qtyEntry = km.enterQuantity(qty);
        boolean qtyOk = km.quantityEntered(qty);
        step(page, "Enter the quantity", "Enter the Quantity " + qty,
                "The quantity is entered",
                (qtyOk
                    ? "PASSES because the field read the value back: " + qtyEntry
                    : "FAILS because the quantity did not land in its field: " + qtyEntry),
                qtyOk ? "PASS" : "FAIL");

        // 6) Add the line
        String itemAdd = km.addKitItem();
        boolean rowAdded = km.itemRowAdded();
        step(page, "Click Add", "Click Add to put the drug and quantity into the KIT Information list",
                "A new row appears in the KIT Information list",
                (rowAdded
                    ? "PASSES because the list grew — " + itemAdd + ". The click alone is not credited: "
                      + "the row count before and after is what says the drug was added."
                    : "FAILS because the list did not grow — " + itemAdd),
                rowAdded ? "PASS" : "FAIL");

        // 7) Save -> toast
        String toast = km.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.KitMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = km.codeInList(code, name);
        addSummary("List check", km.lastListCheck);
        addSummary("What the screen put on screen after Save", km.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + km.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the kit IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Save answered \"" + toast + "\" and the record is NOT in the list. "
                              + km.lastListCheck)
                        : "Save not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (km.toastFromObserver
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

        addSummary("KIT Code / Name", code + " / " + name);
        addSummary("Drug", km.lastDrug);
        addSummary("KIT Information line", km.lastItemAdd);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
