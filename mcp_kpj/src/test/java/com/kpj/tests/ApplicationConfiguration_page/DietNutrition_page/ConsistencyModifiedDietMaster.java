package com.kpj.tests.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ConsistencyModifiedDietMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Consistency Modified Diet Master</b> (inline-add).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Diet and Nutrition</b> → <b>Consistency Modified Diet Master</b>.</li>
 *   <li>Enter Code, Remark.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class ConsistencyModifiedDietMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ConsistencyModifiedDietMaster() { super("ApplicationConfig_Diet_ConsistencyModifiedDietMaster"); }

    public static void main(String[] args) {
        ConsistencyModifiedDietMaster t = new ConsistencyModifiedDietMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Diet and Nutrition - Consistency Modified Diet Master",
                "Application Configuration > Diet and Nutrition > Consistency Modified Diet Master",
                "Add a Consistency Modified Diet Master: enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.ConsistencyModifiedDietMaster cm =
                new com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.ConsistencyModifiedDietMaster(page);

        boolean onScreen = cm.navigateViaMenu();
        System.out.println("CONSISTENCY url => " + page.url());
        step(page, "Open Consistency Modified Diet Master screen", "Application Configuration -> Diet and Nutrition -> Consistency Modified Diet Master",
                "The screen is shown", cm.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                cm.onScreen() ? "PASS" : "FAIL");
        if (!cm.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter Code, Remark, then Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = cm.fillDetails(attempt);
            used++;
            System.out.println("CONSISTENCY prefix => " + cm.prefix + " | fill => " + fill);
            toast = cm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("ConsistencyModifiedDietMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill.contains("Code=CMD") && !fill.contains("Code=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark", "Enter Code and Remark (ng-model prefix " + cm.prefix + ")",
                "Code and Remark are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Submit not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Consistency Diet Code", cm.lastCode);
        addSummary("Remark", cm.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
