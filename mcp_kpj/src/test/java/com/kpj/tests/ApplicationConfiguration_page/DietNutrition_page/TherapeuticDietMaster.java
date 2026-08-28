package com.kpj.tests.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named TherapeuticDietMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Therapeutic Diet Master</b> (inline-add).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Diet and Nutrition</b> → <b>Therapeutic Diet Master</b>.</li>
 *   <li>Enter Code, Remark.</li>
 *   <li>Click <b>Submit</b> ({@code IUDTherapeuticDietMaster}); wait for the success toast.</li>
 * </ol>
 */
public class TherapeuticDietMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public TherapeuticDietMaster() { super("ApplicationConfig_Diet_TherapeuticDietMaster"); }

    public static void main(String[] args) {
        TherapeuticDietMaster t = new TherapeuticDietMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Diet and Nutrition - Therapeutic Diet Master",
                "Application Configuration > Diet and Nutrition > Therapeutic Diet Master",
                "Add a Therapeutic Diet Master: enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.TherapeuticDietMaster td =
                new com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.TherapeuticDietMaster(page);

        boolean onScreen = td.navigateViaMenu();
        step(page, "Open Therapeutic Diet Master screen", "Application Configuration -> Diet and Nutrition -> Therapeutic Diet Master",
                "The screen is shown", td.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                td.onScreen() ? "PASS" : "FAIL");
        if (!td.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter Code, Remark, then Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = td.fillDetails(attempt);
            used++;
            toast = td.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("TherapeuticDietMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill.contains("Code=TD") && !fill.contains("=(no)");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark", "Enter Code and Remark",
                "Code and Remark are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Submit not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (IUDTherapeuticDietMaster); on 'already exists' change the details and Submit again",
                "'... saved successfully' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Therapeutic Diet Code", td.lastCode);
        addSummary("Remark", td.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
