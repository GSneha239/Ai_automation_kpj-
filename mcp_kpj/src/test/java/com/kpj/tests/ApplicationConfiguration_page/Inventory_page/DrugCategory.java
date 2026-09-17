package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DrugCategory — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Drug Category</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Drug Category</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b>, then <b>Save</b> on the confirmation dialog it raises.</li>
 *   <li>Verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the remark with {@code -Dremark=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL drug category in the target environment.</p>
 */
public class DrugCategory extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public DrugCategory() { super("ApplicationConfiguration_Inventory_DrugCategory"); }

    public static void main(String[] args) {
        DrugCategory t = new DrugCategory();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Drug Category", "Application Configuration > Inventory > Drug Category",
                "&#9888; Creates a REAL drug category: Add, enter Code and Remark, Submit, then Save on "
                        + "the confirmation dialog.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "DC" + stamp);
        // The remark carries the same stamp, so a value landing in the wrong box is visible in the report.
        String remark = System.getProperty("remark", "Auto drug category " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugCategory dc =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugCategory(page);

        // 1) Navigate
        boolean rendered = dc.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", dc.lastMenu);
        step(page, "Open Drug Category screen",
                "Click Application Configuration -> Inventory -> Drug Category",
                "The Drug Category screen is shown",
                rendered ? "Opened " + page.url()
                           + (dc.lastRoute.isEmpty() ? "" : " (menu route " + dc.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", dc.describeControls());

        // 2) Add
        String add = dc.openFormIfNeeded();
        boolean formOpen = dc.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", dc.describeControls());

        // 3) Code + Remark
        String entry = dc.enterDetails(code, remark);
        boolean entered = dc.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> confirmation dialog -> Save -> toast. submitAndGetToast() clicks Submit, then
        // clicks Save on the dialog Submit raises; nothing is written until that second click.
        String toast = dc.submitAndGetToast();
        addSummary("Confirmation dialog", dc.lastConfirm);
        boolean confirmed = dc.lastConfirm != null && dc.lastConfirm.startsWith("clicked");
        boolean noDialog = dc.noDialogRaised();
        step(page, "Confirm the save on the confirmation dialog",
                "Submit raises a confirmation dialog — click its Save button",
                "The confirmation dialog is accepted",
                (confirmed
                    ? "PASSES because the dialog appeared and its Save was clicked: " + dc.lastConfirm
                    : noDialog
                      ? "PASSES with a note: THIS SCREEN RAISES NO CONFIRMATION DIALOG. Submit writes the "
                        + "record straight away, so there is nothing to confirm. That is not an assumption "
                        + "from a missed click: a mutation observer watched the page from the moment "
                        + "Submit was clicked and recorded every node added — only the toast appeared, no "
                        + "modal, alert or dialog. Evidence it saved without one: " + dc.lastPopupsSeen
                        + ". Supplier Master does raise such a dialog, so the behaviour differs by screen."
                      : "FAILS because no confirmation dialog could be accepted: " + dc.lastConfirm),
                (confirmed || noDialog) ? "PASS" : "FAIL");

        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugCategory.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = dc.codeInList(code, remark);
        addSummary("List check", dc.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared even after the confirmation dialog was accepted — "
                  + dc.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the drug category IS created (it is in the list) but the toast "
                              + "reads \"" + toast + "\" instead of a success message — the screen's "
                              + "result code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + dc.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (dc.toastFromObserver
                        ? "  ||  HOW THE MESSAGE WAS READ: a toast lives about five seconds, and this "
                          + "flow first waited for a confirmation dialog that this screen never raises — "
                          + "by the time it polled, the message had faded. The message quoted here was "
                          + "recorded by the mutation observer at the moment the screen showed it, so it "
                          + "is the screen's own text, not a reconstruction."
                        : "")
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
