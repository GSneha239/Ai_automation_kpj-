package com.kpj.tests.Home_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Home — referenced by its fully-qualified name.

/**
 * <b>Home</b> (the house icon at the top of the sidebar).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the sidebar menu exists).</li>
 *   <li>Move off the dashboard: open another screen (Application Configuration &gt; Nursing &gt; Allergen Type), so that clicking Home is a real navigation.</li>
 *   <li>Click <b>Home</b> in the sidebar &rarr; the <b>Patient Dashboard</b> ({@code #/PatientDashboard}) opens.</li>
 *   <li>Date Range: From and To show today.</li>
 *   <li>The KPI tiles show a number: Total No. of Appointments, Pending Appointments, Confirmed Appointments, Total No. of Registered Patients, and the Patient Journey TAT tile.</li>
 *   <li>The widgets are there: Patient Journey, Diagnosis Trend, Pharmacy Dispense Completed / Pending, Billing.</li>
 * </ol>
 *
 * <p>Read-only: nothing is saved. A widget that has no data ("No Data Available") is reported, not failed - the data depends on the day.</p>
 */
public class Home extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Home() { super("Home_Home"); }

    public static void main(String[] args) {
        Home t = new Home();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Home", "Home (sidebar) > Patient Dashboard",
                "Open another screen, click Home in the sidebar and verify the Patient Dashboard opens: Date Range, KPI tiles and widgets. Read-only.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Home_page.Home home = new com.kpj.pages.Home_page.Home(page);

        // 1) Leave the dashboard first - after login the app is already on it, so clicking Home would prove nothing.
        boolean away = new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.AllergenType(page).navigateViaMenu();
        boolean offDashboard = !page.url().toLowerCase().contains("patientdashboard");
        step(page, "Open another screen first", "Click Application Configuration -> Nursing -> Allergen Type", "Another screen is shown (not the dashboard)",
                away && offDashboard ? "Opened " + page.url() : "FAILED to leave the dashboard - the app is showing " + page.url(),
                away && offDashboard ? "PASS" : "FAIL");

        // 2) Click Home
        boolean clicked = home.clickHome();
        boolean opened = clicked && home.onDashboard();
        step(page, "Click Home", "Click the Home icon at the top of the sidebar", "The Patient Dashboard page opens (#/PatientDashboard)",
                opened ? "PASSES because " + home.lastClick + " and the app opened " + page.url() + " (top bar: \"" + home.header() + "\")"
                       : "FAILS because " + (clicked ? "the dashboard did not open - the app is showing " + page.url() + " (top bar: \"" + home.header() + "\")" : home.lastClick),
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED - Home did not open the Patient Dashboard"); return; }
        home.waitForTiles();

        // 3) Date range
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String[] range = home.dateRange();
        boolean rangeOk = today.equals(range[0].trim()) && today.equals(range[1].trim());
        step(page, "Verify the Date Range", "Read the From and To dates of the Date Range panel", "Both show today (" + today + ")",
                (rangeOk ? "PASSES because " : "FAILS because ") + "From = " + range[0] + ", To = " + range[1],
                rangeOk ? "PASS" : "FAIL");

        // 4) KPI tiles
        StringBuilder tiles = new StringBuilder();
        boolean tilesOk = true;
        for (String label : com.kpj.pages.Home_page.Home.TILES) {
            String v = home.tileValue(label);
            boolean ok = v.matches("\\d+");
            tilesOk &= ok;
            tiles.append(label).append(" = ").append(v.isEmpty() ? "(tile missing)" : v.startsWith("?") ? "(not a number: " + v.substring(1) + ")" : v).append("; ");
        }
        String[] tat = home.tat();
        boolean tatOk = !tat[0].isEmpty() && !tat[1].isEmpty() && !tat[2].isEmpty();
        tilesOk &= tatOk;
        tiles.append("Patient Journey TAT (").append(tat[0].isEmpty() ? "?" : tat[0]).append(" Min): Within TAT = ").append(tat[1].isEmpty() ? "(missing)" : tat[1])
             .append(", Exceeding TAT = ").append(tat[2].isEmpty() ? "(missing)" : tat[2]);
        step(page, "Verify the KPI tiles", "Read the four count tiles and the Patient Journey TAT tile", "Each tile shows its number",
                (tilesOk ? "PASSES because every tile shows a number: " : "FAILS because a tile has no number: ") + tiles,
                tilesOk ? "PASS" : "FAIL");

        // 5) Widgets
        StringBuilder widgets = new StringBuilder();
        java.util.List<String> blankWidgets = new java.util.ArrayList<>();
        boolean widgetsOk = true;
        for (String w : com.kpj.pages.Home_page.Home.WIDGETS) {
            String st = home.widgetState(w);
            if (st.equals("missing")) widgetsOk = false;
            if (st.equals("blank") || st.equals("empty")) blankWidgets.add(w);
            widgets.append(w).append(" = ").append(st.equals("chart") ? "chart drawn" : st.equals("no-data") ? "No Data Available"
                    : st.equals("blank") ? "chart area shown but EMPTY (nothing drawn, no message)" : st.equals("empty") ? "title only (no chart / no message)" : "NOT SHOWN").append("; ");
        }
        step(page, "Verify the dashboard widgets", "Look for the Patient Journey, Diagnosis Trend, Pharmacy Dispense Completed / Pending and Billing widgets",
                "All four widgets are shown",
                (widgetsOk ? "PASSES because all four widgets are shown: " : "FAILS because a widget is missing: ") + widgets
                        + (blankWidgets.isEmpty() ? "" : " NOTE: " + blankWidgets + " drew nothing and showed no 'No Data' message - data for the day, or the widget did not render."),
                widgetsOk ? "PASS" : "FAIL");

        addSummary("Opened", page.url());
        addSummary("Date range", range[0] + " - " + range[1]);
        addSummary("KPI tiles", tiles.toString());
        addSummary("Widgets", widgets.toString());
        if (!blankWidgets.isEmpty()) addSummary("Blank widgets", blankWidgets + " - chart area empty, no 'No Data' message");
        addSummary("Result", opened && rangeOk && tilesOk && widgetsOk ? "Home opens the Patient Dashboard with its date range, tiles and widgets" : "See the failed step(s)");
    }
}
