package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.LocatorAssertions;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

// The page object is also named DoctorAdditionDeduction — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Doctor Addition / Deduction</b> ({@code #/DoctorAddition-Deduction}).
 *
 * <p>Scenarios (all verified by manual exploration on devhis, 2026-09-29):</p>
 * <ol>
 *   <li>Page loads (heading "Addition Deduction").</li>
 *   <li>Search panel, grid and entry-form controls are visible, with their defaults (today's dates, Addition).</li>
 *   <li>Required-field validation chain on Save: Payable Type → Payable Name → Amount (empty and 0).</li>
 *   <li>Payable Type → Payable Name cascade loads names.</li>
 *   <li>Amount accepts numbers only and is capped at 10 characters.</li>
 *   <li>Invalid Date is rejected on Save.</li>
 *   <li>Add: valid entry saves → success toast → form resets.</li>
 *   <li>Search by Addition finds the saved entry with the entered values; Deduction filter excludes it.</li>
 *   <li>Grid shows the Payable Name of the saved entry.</li>
 *   <li>Search with ALL includes the saved entry.</li>
 *   <li>Search rejects an invalid From Date.</li>
 *   <li>Delete removes the entry (also the test's clean-up — always attempted when an entry was created).</li>
 * </ol>
 * <p>Not applicable: the screen has no Edit, Clear or Cancel control. Data: Payable Type "Doctor" and Payable Name
 * "Demo Doctor" come from the application's own lists (first available name is used if "Demo Doctor" is absent);
 * amount 1 with a unique "AUTO-TEST DAD &lt;timestamp&gt;" remark, deleted again at the end.</p>
 */
public class DoctorAdditionDeduction extends DevHisBase {

    private static final String TYPE = "Doctor";
    private static final String PREFERRED_NAME = "Demo Doctor";

    private com.kpj.pages.Billing_page.DoctorAdditionDeduction dad;
    private int passed = 0, failed = 0;

    public DoctorAdditionDeduction() { super("Billing_DoctorAdditionDeduction"); }

    public static void main(String[] args) {
        DoctorAdditionDeduction t = new DoctorAdditionDeduction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Runs one verification; records PASS with {@code okText} or FAIL with the assertion's message, and continues. */
    private boolean check(String name, String desc, String expected, String okText, Runnable assertion) {
        try {
            assertion.run();
            step(name, desc, expected, okText, "PASS");
            passed++;
            return true;
        } catch (AssertionError | RuntimeException e) {
            String msg = String.valueOf(e.getMessage()).split("\n")[0];
            for (String line : String.valueOf(e.getMessage()).split("\n"))
                if (line.trim().startsWith("Actual") || line.trim().startsWith("Received")) msg += " | " + line.trim();
            step(name, desc, expected, "FAILED: " + msg, "FAIL");
            failed++;
            return false;
        }
    }

    /** Assert the next toast raised by {@code action} starts with {@code text}. */
    private void expectToast(Runnable action, String text) {
        dad.clearToasts();
        action.run();
        assertThat(dad.toastMessage()).hasText(com.kpj.pages.Billing_page.DoctorAdditionDeduction.starts(text),
                new LocatorAssertions.HasTextOptions().setTimeout(8000));
    }

    @Override
    protected void body() {
        meta("Billing - Doctor Addition / Deduction", "Billing > Doctor Addition / Deduction",
                "Load, controls, required-field validation, Payable cascade, Amount input rules, Add (Save), Search "
                        + "filters (Addition / Deduction / ALL), grid content, Delete (clean-up).");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; application shell loads", "Logged in (" + page.url() + ")", "PASS");

        dad = new com.kpj.pages.Billing_page.DoctorAdditionDeduction(page);
        final String today = com.kpj.pages.Billing_page.DoctorAdditionDeduction.today();

        // 1) Page load
        boolean loaded = check("Open Doctor Addition / Deduction", "Navigate to " + BASE + "/" + com.kpj.pages.Billing_page.DoctorAdditionDeduction.ROUTE,
                "Heading 'Addition Deduction' is shown", "Screen opened: " + page.url(), () -> {
                    dad.navigateTo(BASE);
                    assertThat(dad.heading()).isVisible();
                    assertThat(page).hasURL(Pattern.compile("DoctorAddition-Deduction"));
                });
        if (!loaded) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Controls + defaults
        check("Search panel controls", "From Date, To Date, Payable Type, Payable Name, Addition/Deduction/ALL, Search",
                "All search controls visible; dates default to today; 'Addition' selected",
                "Visible; From/To = " + today + "; Addition checked", () -> {
                    assertThat(dad.main().getByText("From Date", new Locator.GetByTextOptions().setExact(true))).isVisible();
                    assertThat(dad.main().getByText("To Date", new Locator.GetByTextOptions().setExact(true))).isVisible();
                    assertThat(dad.input(dad.NG_FROM)).hasValue(today);
                    assertThat(dad.input(dad.NG_TO)).hasValue(today);
                    assertThat(dad.select2(dad.NG_SEARCH_TYPE)).isVisible();
                    assertThat(dad.select2(dad.NG_SEARCH_NAME)).isVisible();
                    assertThat(dad.searchAddition()).isChecked();
                    assertThat(dad.searchDeduction()).not().isChecked();
                    assertThat(dad.searchAll()).not().isChecked();
                    assertThat(dad.searchButton()).isEnabled();
                });

        check("Grid structure", "'List of Addition / Deduction' grid header row",
                "Columns: Payable Type, Payable Name, Date, Amount, Addition / Deduction, Remark, Delete",
                "Grid title and the 7 expected columns are present", () -> {
                    assertThat(dad.gridTitle()).isVisible();
                    assertThat(dad.gridHeaders()).hasText(new String[]{"Payable Type", "Payable Name", "Date", "Amount",
                            "Addition / Deduction", "Remark", "Delete"});
                });

        check("Entry form controls", "Addition/Deduction radios, Date*, Payable Type*, Payable Name*, Amount*, Remark, Save",
                "All entry controls visible; Date = today; 'Addition' selected; Amount/Remark empty",
                "Visible with expected defaults", () -> {
                    for (String lbl : new String[]{"Date*", "Payable Type*", "Payable Name*", "Amount*"})
                        assertThat(dad.main().getByText(lbl, new Locator.GetByTextOptions().setExact(true))).isVisible();
                    assertThat(dad.input(dad.NG_DATE)).hasValue(today);
                    assertThat(dad.entryAddition()).isChecked();
                    assertThat(dad.entryDeduction()).not().isChecked();
                    assertThat(dad.select2Chosen(dad.NG_TYPE)).hasText("--Select--");
                    assertThat(dad.select2Chosen(dad.NG_NAME)).hasText("--Select--");
                    assertThat(dad.amount()).isEmpty();
                    assertThat(dad.remark()).isEmpty();
                    assertThat(dad.saveButton()).isEnabled();
                });

        // 3) Required-field validation chain
        check("Validation: Payable Type required", "Click Save with nothing selected",
                "Warning 'Please Select Doctor Type' and nothing is saved", "Toast: 'Please Select Doctor Type!'",
                () -> expectToast(() -> dad.saveButton().click(), "Please Select Doctor Type"));

        final String[] name = {PREFERRED_NAME};
        check("Payable Type → Payable Name cascade", "Select Payable Type = " + TYPE,
                "Payable Name list is populated for the chosen type", "Payable Name list loaded", () -> {
                    dad.pickSelect2(dad.NG_TYPE, TYPE);
                    assertThat(dad.select2Chosen(dad.NG_TYPE)).hasText(TYPE);
                    int n = dad.waitForOptions(dad.NG_NAME);
                    if (n < 1) throw new AssertionError("Payable Name list is empty for type " + TYPE);
                    if (!dad.hasOption(dad.NG_NAME, PREFERRED_NAME)) name[0] = dad.firstOption(dad.NG_NAME);
                    addSummary("Payable Name options (" + TYPE + ")", String.valueOf(n));
                });

        check("Validation: Payable Name required", "Payable Type chosen, Payable Name empty → Save",
                "Warning 'Please Select Doctor'", "Toast: 'Please Select Doctor!'",
                () -> expectToast(() -> dad.saveButton().click(), "Please Select Doctor!"));

        check("Validation: Amount required", "Select Payable Name = " + name[0] + ", Amount empty → Save",
                "Warning 'Please Enter Amount'", "Toast: 'Please Enter Amount!'", () -> {
                    dad.pickSelect2(dad.NG_NAME, name[0]);
                    assertThat(dad.select2Chosen(dad.NG_NAME)).hasText(name[0]);
                    expectToast(() -> dad.saveButton().click(), "Please Enter Amount");
                });

        check("Validation: Amount must be non-zero", "Amount = 0 → Save",
                "Warning 'Please Enter Amount'", "Toast: 'Please Enter Amount!' for 0", () -> {
                    dad.amount().fill("0");
                    expectToast(() -> dad.saveButton().click(), "Please Enter Amount");
                });

        // 4) Amount input rules
        check("Amount accepts numbers only", "Type 'abc' then '12345678901234' into Amount",
                "Letters are rejected; input is limited to 10 characters", "Letters rejected; capped at 10 digits", () -> {
                    dad.amount().fill("");
                    dad.amount().pressSequentially("abc");
                    assertThat(dad.amount()).isEmpty();
                    dad.amount().fill("12345678901234");
                    assertThat(dad.amount()).hasValue("1234567890");
                });

        // 5) Invalid date
        check("Validation: invalid Date", "Date = 45/13/2026, Amount = 1 → Save",
                "Warning that the date is invalid; nothing is saved", "Toast: 'Invalid From Date!'", () -> {
                    dad.setDate(dad.NG_DATE, "45/13/2026");
                    dad.amount().fill("1");
                    expectToast(() -> dad.saveButton().click(), "Invalid From Date");
                });

        // 6) Add (valid) — creates one entry that is deleted again at the end
        final String remark = "AUTO-TEST DAD " + System.currentTimeMillis();
        final boolean[] created = {false};
        try {
            check("Add: save a valid Addition", "Addition, Date " + today + ", " + TYPE + " / " + name[0]
                            + ", Amount 1, Remark '" + remark + "' → Save",
                    "Success toast 'Doctor Addition deduction Details Added Successfully'",
                    "Saved; toast 'Doctor Addition deduction Details Added Successfully.'", () -> {
                        dad.setDate(dad.NG_DATE, today);
                        dad.entryAddition().check();
                        dad.amount().fill("1");
                        dad.remark().fill(remark);
                        dad.clearToasts();
                        Response r = dad.clickSaveExpectingRequest();
                        created[0] = r.ok() && r.text().contains("\"ResultStatus\":1");
                        assertThat(dad.toastMessage()).hasText(com.kpj.pages.Billing_page.DoctorAdditionDeduction
                                .starts("Doctor Addition deduction Details Added Successfully"));
                    });
            addSummary("Remark (test entry)", remark);

            if (created[0]) {
                check("Form resets after save", "Inspect the entry form after the successful save",
                        "Amount, Remark, Payable Type and Payable Name are cleared", "Form cleared", () -> {
                            assertThat(dad.amount()).isEmpty();
                            assertThat(dad.remark()).isEmpty();
                            assertThat(dad.select2Chosen(dad.NG_TYPE)).hasText("--Select--");
                            assertThat(dad.select2Chosen(dad.NG_NAME)).hasText("--Select--");
                        });

                // 7) Search: Addition finds it with the entered values
                check("Search (Addition) shows the new entry", "From/To = " + today + ", Addition → Search",
                        "A row with Doctor / " + today + " / 1 / addition / the remark and a Delete button",
                        "Row found with the entered values", () -> {
                            dad.setDate(dad.NG_FROM, today);
                            dad.setDate(dad.NG_TO, today);
                            dad.searchAddition().check();
                            dad.search();
                            Locator row = dad.rowWithRemark(remark);
                            assertThat(row).hasCount(1);
                            assertThat(row.locator("td")).hasText(new Pattern[]{Pattern.compile("^" + TYPE + "$"),
                                    Pattern.compile(".*"), Pattern.compile("^" + today + "$"), Pattern.compile("^1(\\.00)?$"),
                                    Pattern.compile("(?i)^addition$"), Pattern.compile("^" + Pattern.quote(remark) + "$"),
                                    Pattern.compile("Delete")});
                            assertThat(row.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                                    new Locator.GetByRoleOptions().setName("Delete"))).isVisible();
                        });

                check("Grid shows Payable Name", "Payable Name cell of the new row",
                        "Payable Name = " + name[0], "Payable Name shown", () ->
                                assertThat(dad.rowWithRemark(remark).locator("td").nth(1)).hasText(name[0]));

                check("Search (Deduction) excludes the entry", "Deduction → Search",
                        "The Addition entry is not listed", "Not listed under Deduction", () -> {
                            dad.searchDeduction().check();
                            dad.search();
                            assertThat(dad.rowWithRemark(remark)).hasCount(0);
                        });

                check("Search (ALL) includes the entry", "ALL → Search",
                        "The Addition entry is listed under ALL", "Listed under ALL", () -> {
                            dad.searchAll().check();
                            dad.search();
                            assertThat(dad.rowWithRemark(remark)).hasCount(1);
                        });
            }

            // 8) Search validation
            check("Search: invalid From Date", "From Date = 99/99/2026 → Search",
                    "Warning 'Invalid From Date' and no grid request", "Toast: 'Invalid From Date!'", () -> {
                        dad.setDate(dad.NG_FROM, "99/99/2026");
                        expectToast(() -> dad.searchButton().click(), "Invalid From Date");
                        dad.setDate(dad.NG_FROM, today);
                    });
        } finally {
            // 9) Delete — also the clean-up, so it runs whenever an entry was created
            if (created[0]) {
                check("Delete the entry", "Addition → Search → click Delete on the test row",
                        "Toast 'Deleted successfully' and the row disappears", "Deleted; row removed", () -> {
                            dad.setDate(dad.NG_FROM, today);
                            dad.setDate(dad.NG_TO, today);
                            dad.searchAddition().check();
                            dad.search();
                            assertThat(dad.rowWithRemark(remark)).hasCount(1);
                            dad.clearToasts();
                            dad.deleteRow(remark);
                            assertThat(dad.toastMessage()).hasText(com.kpj.pages.Billing_page.DoctorAdditionDeduction
                                    .starts("Deleted successfully"));
                            assertThat(dad.rowWithRemark(remark)).hasCount(0);
                        });
            }
            addSummary("Checks passed", String.valueOf(passed));
            addSummary("Checks failed", String.valueOf(failed));
            addSummary("Result", failed == 0 ? "All checks passed" : failed + " check(s) failed — see FAIL rows");
        }
    }
}
