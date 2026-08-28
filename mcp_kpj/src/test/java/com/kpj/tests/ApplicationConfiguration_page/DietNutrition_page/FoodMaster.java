package com.kpj.tests.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FoodMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Food Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Diet and Nutrition</b> → <b>Food Master</b>, click
 *       <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Food Name</b>, <b>Service Name</b> in <b>Food &amp; Beverage Details</b>.</li>
 *   <li>Enter <b>Calories</b>, <b>Protein</b>, <b>Carbohydrates</b>, <b>Fats</b> in <b>Nutritional Details</b>.</li>
 *   <li>Select <b>Veg</b>/<b>Non-Veg</b> in <b>Other Details</b>.</li>
 *   <li>Select <b>Diet Linking</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class FoodMaster extends DevHisBase {

    /** How many times to re-enter fresh Food & Beverage details when the server says the code / food name
     *  already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public FoodMaster() { super("ApplicationConfig_DietNutrition_FoodMaster"); }

    public static void main(String[] args) {
        FoodMaster t = new FoodMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Diet and Nutrition - Food Master",
                "Application Configuration > Diet and Nutrition > Food Master",
                "Add: Food & Beverage Details (Code, Food Name, Service Name), Nutritional Details (Calories, "
                        + "Protein, Carbohydrates, Fats), Other Details (Veg/Non-Veg), Diet Linking, Submit; "
                        + "wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.FoodMaster fm =
                new com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page.FoodMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = fm.navigateViaMenu();
        String landed = fm.currentScreen();
        step(page, "Open Food Master screen", "Application Configuration -> Diet and Nutrition -> Food Master",
                "The Food Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Food Master but the app opened: " + landed + "\n" + fm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add.
        String addHow = fm.clickAdd();
        step(page, "Click Add", "Click Add to open the Food Master form", "The form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Nutritional Details — filled once; not the field the "already exists" toast would flag.
        String nut = fm.fillNutritionalDetails();
        boolean nutOk = !nut.contains("(not found") && !nut.contains("(section not found");
        step(page, "Enter Calories, Protein, Carbohydrates, Fats (Nutritional Details)",
                "Enter Calories, Protein, Carbohydrates, Fats within Nutritional Details",
                "All four fields are entered", nut + (nutOk ? "" : "\n" + fm.describeForm()), nutOk ? "PASS" : "FAIL");

        // 4) Other Details — Veg/Non-Veg.
        String veg = fm.selectVegNonVeg(true);
        boolean vegOk = !veg.startsWith("(");
        step(page, "Select Veg/Non-Veg (Other Details)", "Select Veg within Other Details",
                "A Veg/Non-Veg option is selected", veg + (vegOk ? "" : "\n" + fm.describeForm()), vegOk ? "PASS" : "FAIL");

        // 5) Diet Linking.
        String diet = fm.selectDietLinking(0);
        boolean dietOk = !diet.startsWith("(");
        step(page, "Select Diet Linking", "Select an option within Diet Linking",
                "A Diet Linking option is selected", diet + (dietOk ? "" : "\n" + fm.describeForm()), dietOk ? "PASS" : "FAIL");

        // 6) + 7) Food & Beverage Details (Code, Food Name, Service Name), Submit — retry with different Code /
        // Food Name on "already exists".
        String bev = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            bev = fm.fillFoodBeverageDetails(attempt);
            used++;
            toast = fm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("FoodMaster: attempt " + used + " (" + bev + ") already exists — changing the details");
        }

        boolean bevOk = bev.contains("Code=FD") && !bev.contains("(not found") && !bev.contains("(section not found") && !bev.contains("(no-options)");
        String bevActual = bev + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Food Name, Service Name (Food & Beverage Details)",
                "Enter Code, Food Name, select Service Name within Food & Beverage Details",
                "All three fields are entered", bevActual + (bevOk ? "" : "\n" + fm.describeForm()), bevOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + fm.describeForm()
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + fm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (fm.toastPng != null && fm.toastPng.length > 0) {
            step(fm.toastPng, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", fm.lastCode);
        addSummary("Food Name", fm.lastFoodName);
        addSummary("Service Name", fm.lastServiceName);
        addSummary("Nutritional", "Calories=" + fm.lastCalories + " Protein=" + fm.lastProtein
                + " Carbohydrates=" + fm.lastCarbs + " Fats=" + fm.lastFats);
        addSummary("Veg/Non-Veg", fm.lastVegNonVeg);
        addSummary("Diet Linking", fm.lastDietLinking);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
