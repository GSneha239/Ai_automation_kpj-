package com.kpj.tests.SystemConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.SystemConfiguration_page.AlertsPage;

/**
 * System Configuration &gt; <b>Alerts</b> ({@code #/AlertsConfiguration}).
 *
 * <ol>
 *   <li>Open <b>System Configuration</b> → <b>Alerts</b>.</li>
 *   <li>Select <b>Location Name*</b>.</li>
 *   <li>In <b>Alerts and notifications</b>, select the <b>Event</b>.</li>
 *   <li>Tick a channel checkbox (SMS / WhatsApp / Email).</li>
 *   <li>Click <b>Add</b> ({@code AddAlertConfig}), then <b>Save</b> ({@code fnSaveAlertsConfig}).</li>
 *   <li>The toast must be a SUCCESS message — any other message fails the step.</li>
 * </ol>
 */
public class Alerts extends DevHisBase {

    public Alerts() { super("SystemConfig_Alerts"); }

    public static void main(String[] args) {
        Alerts t = new Alerts();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("System Configuration - Alerts", "System Configuration > Alerts",
                "Configure an alert: select Location Name and Event, tick a notification channel, Add, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        AlertsPage al = new AlertsPage(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = al.navigateViaMenu();
        String landed = al.currentScreen();
        step(page, "Open Alerts screen", "System Configuration -> Alerts",
                "The Alerts configuration screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Alerts but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String sel = al.selectLocationAndEvent();
        boolean selOk = !sel.contains("(no select)") && !sel.contains("(no options)");
        step(page, "Select Location Name and Event",
                "Select Location Name*, then the Event in Alerts and notifications",
                "Location Name and Event are selected", sel, selOk ? "PASS" : "FAIL");

        String ch = al.tickChannel();
        boolean chOk = ch.contains("(ticked)");
        step(page, "Select a checkbox", "Tick one notification channel (SMS / WhatsApp / Email) on the entry row",
                "One channel checkbox is ticked", ch, chOk ? "PASS" : "FAIL");

        String added = al.clickAdd();
        boolean addOk = added.startsWith("Add clicked") && !added.endsWith("=0") && !added.endsWith("=-1");
        step(page, "Click Add", "Click Add (AddAlertConfig) to commit the alert row",
                "The alert row is added to the list", added, addOk ? "PASS" : "FAIL");

        String toast = al.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        if (al.toastPng != null && al.toastPng.length > 0) {
            step(al.toastPng, "Click Save & success toast", "Click Save (fnSaveAlertsConfig); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast", "Click Save (fnSaveAlertsConfig); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Location Name", al.lastLocation);
        addSummary("Event", al.lastEvent);
        addSummary("Channel", al.lastChannel);
        addSummary("Route", "#/AlertsConfiguration");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
