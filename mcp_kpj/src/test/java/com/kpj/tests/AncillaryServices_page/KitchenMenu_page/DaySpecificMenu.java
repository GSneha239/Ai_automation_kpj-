package com.kpj.tests.AncillaryServices_page.KitchenMenu_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named DaySpecificMenu — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Kitchen &amp; Menu &gt; <b>Day Specific Menu</b> ({@code #/DaySpecificMenuList} -&gt; Add Menu {@code #/add-DaySpecificMenu}).
 *
 * <ol>
 *   <li>Click <b>Add Menu</b>; select a <b>Meal Type</b> (one with a unique name, "Lunch") and a <b>Day</b> that has no menu of that meal type covering the dates below.</li>
 *   <li><b>Valid From</b> = today; <b>Valid To</b> = Valid From + 1 month and 15 days (inside "1 to 2 months from the valid from date").</li>
 *   <li>Select a <b>Food Item</b> (it joins the Menu Item List), click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES A REAL DAY-SPECIFIC MENU on every run; it stays and applies from its Valid From to its Valid To (a menu can only be edited, which this test does not do).</p>
 */
public class DaySpecificMenu extends DevHisBase {

    public DaySpecificMenu() { super("AncillaryServices_KitchenMenu_DaySpecificMenu"); }

    public static void main(String[] args) {
        DaySpecificMenu t = new DaySpecificMenu();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Kitchen & Menu - Day Specific Menu", "Ancillary Services > Kitchen & Menu > Day Specific Menu",
                "Add Menu; select meal type, day, valid from (today) and valid to (1.5 months later) and a food item; Save; verify the success toast. SAVES A REAL MENU.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.KitchenMenu_page.DaySpecificMenu dm = new com.kpj.pages.AncillaryServices_page.KitchenMenu_page.DaySpecificMenu(page);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        boolean on = dm.navigateViaMenu();
        List<String[]> before = on ? dm.existingMenus() : null;
        step("Open Day Specific Menu", "Click Ancillary Services -> Kitchen & Menu -> Day Specific Menu", "The Day Specific Menu list is shown",
                on ? "Opened " + page.url() + (before == null ? "" : "; the server holds " + before.size() + " menu(s)") : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Day Specific Menu not reached"); return; }

        boolean added = dm.clickAddMenu();
        step("Click Add Menu", "Click Add Menu", "The Add New Menu form opens",
                added ? "The form is open (" + page.url() + "): " + dm.optionNames("DaySpecificMenu.MealTypeId").size() + " meal type(s), " + dm.foodNames().size() + " food item(s)" : "Problem — " + dm.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — Add Menu: " + dm.problems); return; }

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(1).plusDays(15);

        String meal = dm.selectMealType(List.of("Lunch", "Evening Snacks", "Dinner"));
        step("Select meal type", "Select the Meal Type", "A meal type is selected",
                !meal.isEmpty() ? "Meal Type \"" + meal + "\" selected (a name the list offers only once; \"Breakfast\" is offered " + dm.optionNames("DaySpecificMenu.MealTypeId").stream().filter(x -> x.equalsIgnoreCase("Breakfast")).count() + " times)"
                        : "Problem — " + dm.problems, !meal.isEmpty() ? "PASS" : "FAIL");
        if (meal.isEmpty()) { addSummary("Result", "FAILED — meal type: " + dm.problems); return; }

        String freeDay = dm.freeDay(meal, from, to);
        String day = dm.selectDay(freeDay.isEmpty() ? "Monday" : freeDay);
        step("Select day", "Select the Day", "A day is selected",
                !day.isEmpty() ? "Day \"" + day + "\" selected" + (freeDay.isEmpty() ? " (every day already has a " + meal + " menu in that period; Monday used)" : " (the first day with no " + meal + " menu covering " + from.format(dmy) + " - " + to.format(dmy) + ")")
                        : "Problem — " + dm.problems, !day.isEmpty() ? "PASS" : "FAIL");
        if (day.isEmpty()) { addSummary("Result", "FAILED — day: " + dm.problems); return; }

        boolean fromOk = dm.enterValidFrom(from);
        step("Select valid from date", "Select Valid From (today, " + from.format(dmy) + ")", "The valid from date is accepted",
                fromOk ? "Valid From " + dm.lastFrom + " entered; the box and the screen's model hold it" : "Problem — " + dm.problems, fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary("Result", "FAILED — valid from: " + dm.problems); return; }

        boolean toOk = dm.enterValidTo(to);
        step("Select valid to date", "Select Valid To (" + to.format(dmy) + ", 1 month and 15 days after Valid From)", "Valid To is 1 to 2 months after Valid From",
                toOk ? "Valid To " + dm.lastTo + " entered; the box and the screen's model hold it (1 month and 15 days after Valid From " + dm.lastFrom + ", inside the 1 to 2 months asked)" : "Problem — " + dm.problems, toOk ? "PASS" : "FAIL");
        if (!toOk) { addSummary("Result", "FAILED — valid to: " + dm.problems); return; }

        String food = dm.selectFoodItem(List.of("Chicken Porridge", "Salmon", "Beans", "Boiled Egg", "Egg"));
        step("Select food item", "Select a Food Item", "The food item is selected and listed in the Menu Item List",
                !food.isEmpty() ? "Food Item \"" + food + "\" selected (of " + dm.foodNames().size() + "); the Menu Item List now holds \"" + dm.lastItemRow + "\"" : "Problem — " + dm.problems, !food.isEmpty() ? "PASS" : "FAIL");
        if (food.isEmpty()) { addSummary("Result", "FAILED — food item: " + dm.problems); return; }

        String toast = dm.saveAndGetToast();
        boolean ok = dm.ok() && dm.saveSucceeded();
        step("Click Save", "Click Save", "The menu is saved",
                dm.problems.isEmpty() ? "Save clicked" + (dm.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + dm.lastDialog + "\"") : "Problem — " + dm.problems, dm.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = dm.savedByServer();
        boolean back = (ok || savedByServer) && dm.awaitList(10000);
        boolean onServer = (ok || savedByServer) && dm.serverHolds(meal, day, from, to);
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dm.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dm.lastSaveMessage.trim() + "\"")
                        + (back ? "; back on the list" : "; the list was not reached") + (onServer ? "; the server now holds the menu (id " + dm.lastMenuId + ")" : "; the server does not list the new menu")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the menu (ResultStatus 1, \"" + dm.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dm.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dm.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Meal Type / Day", meal + " / " + day);
        addSummary("Valid From / Valid To", from.format(dmy) + " / " + to.format(dmy) + " (Valid To is 1 month 15 days after Valid From)");
        addSummary("Food item", food);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + dm.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("On the server afterwards", onServer ? "yes - menu id " + dm.lastMenuId : "(not found)");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
