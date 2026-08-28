package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BillTemplateMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Bill Template Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Bill Template Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Template Name</b>, <b>Print Name</b>, <b>Group</b>, <b>Sub Group</b>,
 *       <b>Services</b>, <b>Quantity</b>, <b>Unit Purchase Price</b>.</li>
 *   <li>Click <b>Add</b> (add the service line), then <b>Submit</b>; wait for the success toast. If the toast
 *       says the code/template name already exists, change the details and Submit again.</li>
 * </ol>
 */
public class BillTemplateMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / template name already exists.
     *  Kept lower than the simplest masters — this screen re-walks the Group/Sub Group/Service cascade each
     *  attempt, which is comparatively expensive. */
    private static final int MAX_ATTEMPTS = 40;

    public BillTemplateMaster() { super("ApplicationConfig_Billing_BillTemplateMaster"); }

    public static void main(String[] args) {
        BillTemplateMaster t = new BillTemplateMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Bill Template Master", "Application Configuration > Billing > Bill Template Master",
                "Add a Bill Template: Add, enter Code + Template Name + Print Name + Group + Sub Group + Services + Quantity + Unit Purchase Price, Add the service line, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.BillTemplateMaster bt =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.BillTemplateMaster(page);

        // 1) Navigate
        bt.navigateViaMenu();
        boolean onScreen = bt.onScreen();
        step(page, "Open Bill Template Master screen", "Click Application Configuration -> Billing -> Bill Template Master",
                "The Bill Template Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = bt.clickAdd();
        step(page, "Click Add", "Click Add", "The Bill Template add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, add the service line, Submit — retry with different details on "already exists".
        String fill = "", lineToast = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bt.fillDetails(attempt);
            used++;
            lineToast = bt.addServiceLine();
            toast = bt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BillTemplateMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=BT") && !fill.contains("TemplateName=(no")
                && !fill.contains("Group=(no") && !fill.contains("Services=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Bill Template details",
                "Enter Code, Template Name, Print Name, Group, Sub Group, Services, Quantity, Unit Purchase Price",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        // 4a) Click Add (add the service line to the grid)
        boolean lineOk = lineToast != null && (lineToast.toLowerCase().contains("added")
                || lineToast.toLowerCase().contains("service row") || lineToast.toLowerCase().contains("success"));
        step(page, "Click Add (add the service line)", "Click Add (fnAddListOfServices) to add the service line",
                "The service line is added to the template grid",
                lineToast == null || lineToast.isEmpty() ? "No confirmation after Add" : lineToast, lineOk ? "PASS" : "FAIL");

        // 4b) Submit -> success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDBillTemplate); on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Bill Template Code", bt.lastCode);
        addSummary("Template Name", bt.lastTemplateName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
