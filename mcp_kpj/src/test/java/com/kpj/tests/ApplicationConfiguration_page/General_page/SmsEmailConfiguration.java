package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SmsEmailConfiguration — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>SMS E-mail Configuration</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>SMS E-mail Configuration</b>.</li>
 *   <li>In <b>SMS E-mail Configuration Search</b>: select the <b>Location</b>.</li>
 *   <li>In <b>Add SMS/E-mail Configuration</b>: select the <b>Event</b>, <b>SMS</b>, <b>E-mail</b> and
 *       <b>SMS E-mail To</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The screen has two panels carrying similar controls, so each selection is scoped to its own panel and
 * the run reports which controls each panel owns before touching any of them.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL SMS/e-mail configuration in the target environment.</p>
 */
public class SmsEmailConfiguration extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SmsEmailConfiguration() { super("ApplicationConfiguration_SmsEmailConfiguration"); }

    public static void main(String[] args) {
        SmsEmailConfiguration t = new SmsEmailConfiguration();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("SMS E-mail Configuration", "Application Configuration > General > SMS E-mail Configuration",
                "&#9888; Creates a REAL SMS/e-mail configuration: select the Location in the search panel, "
                        + "then the Event, SMS, E-mail and SMS E-mail To in the Add panel, and Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration se =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration(page);

        // 1) Navigate
        boolean rendered = se.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", se.lastMenu);
        step(page, "Open SMS E-mail Configuration screen",
                "Click Application Configuration -> General -> SMS E-mail Configuration",
                "The SMS E-mail Configuration screen is shown",
                rendered ? "Opened " + page.url()
                           + (se.lastRoute.isEmpty() ? "" : " (menu route " + se.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", se.describeControls());
        addSummary("Panel scoping", se.describePanels());

        // 2) Search panel -> Location
        String loc = se.selectLocation();
        boolean locOk = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.chosen(loc);
        step(page, "In SMS E-mail Configuration Search, select location",
                "Select the Location in the search panel",
                "A location is selected",
                (locOk
                    ? "PASSES because the search panel's dropdown holds the chosen location: " + loc
                    : "FAILS because no location could be selected: " + loc),
                locOk ? "PASS" : "FAIL");

        // 3) Add panel -> an Event that is not configured yet (one configuration per event).
        String event = se.selectEvent(0);
        boolean eventOk = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.chosen(event);
        step(page, "In Add SMS/E-mail Configuration, select event",
                "Select the Event in the Add panel",
                "An event is selected",
                (eventOk
                    ? "PASSES because the Add panel's Event dropdown holds the choice: " + event
                    : "FAILS because no event could be selected: " + event),
                eventOk ? "PASS" : "FAIL");

        // 4) Add panel -> SMS + E-mail
        String flags = se.setSmsAndEmail();
        boolean smsOk = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.chosen(se.lastSms);
        boolean mailOk = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.chosen(se.lastEmail);
        step(page, "Select SMS and E-mail", "Set the SMS and the E-mail options",
                "Both are set",
                ((smsOk && mailOk)
                    ? "PASSES because each control holds its own value: " + flags
                    : "FAILS because a value did not land on its own control: " + flags),
                (smsOk && mailOk) ? "PASS" : "FAIL");

        // 5) Add panel -> SMS E-mail To
        String to = se.selectSendTo();
        boolean toOk = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.chosen(to);
        step(page, "Select SMS E-mail To", "Select the SMS E-mail To",
                "A recipient is selected",
                (toOk
                    ? "PASSES because the dropdown holds the chosen recipient: " + to
                    : "FAILS because no recipient could be selected: " + to),
                toOk ? "PASS" : "FAIL");

        // 6) Save -> toast. "Record already exist!" is the screen refusing a duplicate, not a defect: this
        // environment already carries configurations, so the run steps to the next unconfigured event and
        // saves again rather than reporting a working screen as broken.
        String toast = se.saveAndGetToast();
        StringBuilder retries = new StringBuilder();
        for (int attempt = 1; attempt <= 3
                && toast != null && toast.toLowerCase().contains("already exist"); attempt++) {
            retries.append(retries.length() == 0 ? "" : "; ")
                   .append("\"").append(toast).append("\" for ").append(event);
            event = se.selectEvent(attempt);
            toast = se.saveAndGetToast();
        }
        if (retries.length() > 0) addSummary("Duplicate retries", retries + " -> retried with " + event);
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.SmsEmailConfiguration.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = se.rowInList(event, se.lastSms, se.lastEmail, se.lastSendTo);
        addSummary("List check", se.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + se.lastSaveDiagnostics
                  + (inList ? " BUT the row IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the configuration IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Save answered \"" + toast + "\" and the row is NOT in the list. "
                              + se.lastListCheck)
                        : "Save not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        step(page, "Verify the configuration was created",
                "Look for a row carrying the event, SMS template, e-mail template and recipient just saved",
                "The new configuration is listed",
                (inList
                    ? "PASSES because a single row carries every value chosen: " + se.lastListCheck
                    : "FAILS because no row carries the whole configuration that was saved. "
                      + se.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Location", se.lastLocation);
        addSummary("Event", se.lastEvent);
        addSummary("SMS / E-mail", se.lastSms + " / " + se.lastEmail);
        addSummary("SMS E-mail To", se.lastSendTo);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
