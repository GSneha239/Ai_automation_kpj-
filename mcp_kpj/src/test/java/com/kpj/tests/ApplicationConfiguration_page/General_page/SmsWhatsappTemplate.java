package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SmsWhatsappTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>SMS/WhatsApp Template</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>SMS/WhatsApp Template</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Template Name</b> and <b>Remark</b>.</li>
 *   <li>Tick the <b>SMS</b> or <b>WhatsApp</b> checkbox — verifying it is clickable.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL SMS/WhatsApp template in the target environment.</p>
 */
public class SmsWhatsappTemplate extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SmsWhatsappTemplate() { super("ApplicationConfiguration_SmsWhatsappTemplate"); }

    public static void main(String[] args) {
        SmsWhatsappTemplate t = new SmsWhatsappTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("SMS/WhatsApp Template", "Application Configuration > General > SMS/WhatsApp Template",
                "&#9888; Creates a REAL SMS/WhatsApp template: Add, enter Code, Template Name and Remark, "
                        + "tick the SMS or WhatsApp checkbox, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "SW" + stamp);
        String name = System.getProperty("name", "Auto SMS Template " + stamp);
        String remark = System.getProperty("remark", "Automated template remark " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.SmsWhatsappTemplate sw =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.SmsWhatsappTemplate(page);

        // 1) Navigate
        boolean rendered = sw.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", sw.lastMenu);
        step(page, "Open SMS/WhatsApp Template screen",
                "Click Application Configuration -> General -> SMS/WhatsApp Template",
                "The SMS/WhatsApp Template screen is shown",
                rendered ? "Opened " + page.url()
                           + (sw.lastRoute.isEmpty() ? "" : " (menu route " + sw.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", sw.describeControls());

        // 2) Add
        boolean formOpen = sw.clickAdd();
        addSummary("Form controls", sw.describeControls());
        step(page, "Click Add", "Click Add to open the template form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Template Name + Remark
        String entry = sw.enterDetails(code, name);
        String rem = sw.enterRemark(remark);
        boolean entered = sw.detailsEntered(code, name);
        boolean remOk = sw.remarkEntered(remark);
        step(page, "Enter code, template name and remark",
                "Enter the Code " + code + ", the Template Name " + name + " and the Remark",
                "All three are entered",
                ((entered && remOk)
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                      + " | Remark=" + rem
                    : "FAILS because a value did not land in its own field: " + entry + " | Remark=" + rem),
                (entered && remOk) ? "PASS" : "FAIL");

        // 4) SMS / WhatsApp checkbox — the point is that it responds to the click.
        String tick = sw.tickSmsOrWhatsapp();
        boolean tickOk = sw.ticked();
        step(page, "Tick the SMS or WhatsApp checkbox",
                "Tick the SMS or WhatsApp checkbox to verify it is clickable",
                "The checkbox responds to the click and changes state",
                (tickOk
                    ? "PASSES because the box changed state when clicked, which is what makes it "
                      + "clickable: " + tick
                    : "FAILS because the box did not change state when clicked — a click alone proves "
                      + "nothing if the control never responds: " + tick),
                tickOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = sw.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsWhatsappTemplate.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = sw.codeInList(code, name);
        addSummary("List check", sw.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + sw.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the template IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + sw.lastListCheck)
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

        step(page, "Verify the template was created", "Look for " + code + " in the list",
                "The new template is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its template name — was found in "
                      + "the list: " + sw.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + sw.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Template Name", code + " / " + name);
        addSummary("Remark", sw.lastRemark);
        addSummary("SMS / WhatsApp checkbox", sw.lastTick);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
