package com.kpj.tests.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MealTypeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Meal Type Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Diet and Nutrition</b> → <b>Meal Type Master</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Meal Type</b>, <b>Stipulated Time</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class MealTypeMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / meal type already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public MealTypeMaster() { super("ApplicationConfig_DietNutrition_MealTypeMaster"); }

    public static void main(String[] args) {
        MealTypeMaster t = new MealTypeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Diet and Nutrition - Meal Type Master",
                "Application Configuration > Diet and Nutrition > Meal Type Master",
                "Add a Meal Type: enter Code, Meal Type, Stipulated Time, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.MealTypeMaster mt =
                new com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.MealTypeMaster(page);

        boolean onScreen = mt.navigateViaMenu();
        step(page, "Open Meal Type Master screen", "Click Application Configuration -> Diet and Nutrition -> Meal Type Master",
                "The Meal Type Master screen is shown",
                onScreen ? "Opened " + mt.currentScreen() : "Did NOT reach the screen (" + mt.currentScreen() + ")",
                onScreen && mt.onScreen() ? "PASS" : "FAIL");
        if (!onScreen || !mt.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter Code, Meal Type, Stipulated Time, then Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = mt.fillDetails(attempt);
            used++;
            toast = mt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("MealTypeMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=MT") && !fill.contains("(not found)");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Meal Type, Stipulated Time", "Enter Code, Meal Type and Stipulated Time",
                "All three fields are entered", fillActual + (fillOk ? "" : "\n" + mt.describeForm()), fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + mt.describeForm()
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Code", mt.lastCode);
        addSummary("Meal Type", mt.lastMealType);
        addSummary("Stipulated Time", mt.lastStipulatedTime);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
