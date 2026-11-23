package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Billing &gt; <b>Doctor Addition / Deduction</b> — Page Object.
 *
 * <p>Route {@code #/DoctorAddition-Deduction} (template {@code /Billing/Load/DoctorAddition_Deduction}); page heading
 * <b>"Addition Deduction"</b>. Verified live on devhis 2026-09-29. The screen has three areas:</p>
 * <ul>
 *   <li><b>Search</b>: From Date {@code DoctorAdditionDeduction.fromdate}, To Date {@code .todate}, Payable Type
 *       (select2, {@code .departmentid1}), Payable Name (select2, {@code .doctorid1}, filled by the type), radios
 *       Addition/Deduction/ALL ({@code #radios-0/1/2}, {@code .adddeductflag1}), <b>Search</b>
 *       ({@code fngetadddeductGrid()} → POST {@code api/DoctorAddition-Deduction/fetch}).</li>
 *   <li><b>List of Addition / Deduction</b> grid: Payable Type / Payable Name / Date / Amount /
 *       Addition / Deduction / Remark / Delete. The row's red <b>Delete</b> button ({@code changeStatus}) removes the
 *       entry immediately — there is NO confirmation dialog — and is hidden once the entry is billed.</li>
 *   <li><b>Entry form</b>: radios Addition/Deduction ({@code #radios-3/4}, {@code .adddeductflag}), Date*
 *       ({@code .date}), Payable Type* (select2 {@code .departmentid}), Payable Name* (select2 {@code .doctorid}),
 *       Amount* ({@code #Amount}, placeholder "Enter Amount", maxlength 10, digits/"."/"-" only), Remark
 *       ({@code #remark}), <b>Save</b> ({@code IUDDoctorAdditionDeduction()} → POST
 *       {@code api/DoctorAddition-Deduction/IUD}). Save has NO "Do you want to save" confirmation.</li>
 * </ul>
 * <p>There is no Edit, Clear or Cancel control on this screen. Messages are toastr toasts (JAlert).</p>
 */
public class DoctorAdditionDeduction extends BasePage {

    public static final String ROUTE = "#/DoctorAddition-Deduction";
    public static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final String NG_FROM = "DoctorAdditionDeduction.fromdate";
    public static final String NG_TO = "DoctorAdditionDeduction.todate";
    public static final String NG_SEARCH_TYPE = "DoctorAdditionDeduction.departmentid1";
    public static final String NG_SEARCH_NAME = "DoctorAdditionDeduction.doctorid1";
    public static final String NG_DATE = "DoctorAdditionDeduction.date";
    public static final String NG_TYPE = "DoctorAdditionDeduction.departmentid";
    public static final String NG_NAME = "DoctorAdditionDeduction.doctorid";

    public DoctorAdditionDeduction(Page page) { super(page); }

    public static String today() { return LocalDate.now().format(DMY); }

    // ---- navigation ------------------------------------------------------

    /** Open the screen by its hash route and wait for its heading and the Save button. */
    public void navigateTo(String baseUrl) {
        page.navigate(baseUrl + "/" + ROUTE);
        heading().waitFor(new Locator.WaitForOptions().setTimeout(30000));
        saveButton().waitFor(new Locator.WaitForOptions().setTimeout(30000));
    }

    // ---- locators (user-facing first; ng-model CSS only where the markup has no label/role) ----

    public Locator heading() { return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Addition Deduction")); }
    public Locator main() { return page.locator("main"); }
    public Locator searchButton() { return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true)); }
    public Locator saveButton() { return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save").setExact(true)); }
    public Locator amount() { return page.getByPlaceholder("Enter Amount"); }
    public Locator remark() { return page.getByPlaceholder("Remark"); }
    public Locator gridTitle() { return main().getByText("List of Addition / Deduction"); }
    public Locator gridHeaders() { return main().locator("table thead th"); }
    public Locator gridRows() { return main().locator("table tbody tr"); }
    /** Labels have no {@code for}, so the date inputs are addressed by their Angular model. */
    public Locator input(String ngModel) { return page.locator("input[ng-model='" + ngModel + "']"); }
    public Locator nativeSelect(String ngModel) { return page.locator("select[ng-model='" + ngModel + "']"); }
    /** The visible select2 widget rendered immediately before its (offscreen) native select. */
    public Locator select2(String ngModel) { return page.locator("div.select2-container:has(+ select[ng-model='" + ngModel + "'])"); }
    public Locator select2Chosen(String ngModel) { return select2(ngModel).locator(".select2-chosen"); }

    // Radios: the search group is Addition/Deduction/ALL; the entry-form group is Addition/Deduction.
    public Locator searchAddition() { return page.locator("#radios-0"); }
    public Locator searchDeduction() { return page.locator("#radios-1"); }
    public Locator searchAll() { return page.locator("#radios-2"); }
    public Locator entryAddition() { return page.locator("#radios-3"); }
    public Locator entryDeduction() { return page.locator("#radios-4"); }

    public Locator toastMessage() { return page.locator("#toast-container .toast-message").last(); }

    public Locator rowWithRemark(String remark) { return gridRows().filter(new Locator.FilterOptions().setHasText(remark)); }

    // ---- actions ---------------------------------------------------------

    /** Remove toasts already on screen so the next assertion reads only the message the next action raises. */
    public void clearToasts() {
        page.evaluate("() => document.querySelectorAll('#toast-container .toast').forEach(t => t.remove())");
    }

    /**
     * Pick an option in a select2 dropdown the way a user does: open it, type in its search box, click the match.
     * Returns the text shown in the widget afterwards.
     */
    public String pickSelect2(String ngModel, String optionText) {
        select2(ngModel).click();
        Locator drop = page.locator("#select2-drop");
        drop.waitFor();
        drop.locator("input.select2-input").fill(optionText);
        drop.locator(".select2-result-label").filter(new Locator.FilterOptions().setHasText(optionText)).first().click();
        return select2Chosen(ngModel).innerText().trim();
    }

    /** Wait until a cascading Payable Name select has been populated by its Payable Type; returns option count. */
    public int waitForOptions(String ngModel) {
        page.waitForFunction("(ng) => { const s=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return s && s.options.length > 1; }",
                ngModel, new Page.WaitForFunctionOptions().setTimeout(15000));
        return ((Number) nativeSelect(ngModel).evaluate("s => s.options.length - 1")).intValue();
    }

    /** True if the Payable Name list (for the chosen type) contains an option with exactly this text. */
    public boolean hasOption(String ngModel, String text) {
        return Boolean.TRUE.equals(nativeSelect(ngModel).evaluate(
                "(s, t) => [...s.options].some(o => o.text.trim() === t)", text));
    }

    /** First real (non "--Select--") option text of a select. */
    public String firstOption(String ngModel) {
        return String.valueOf(nativeSelect(ngModel).evaluate(
                "s => ([...s.options].find(o => o.value && !/^-*\\s*select/i.test(o.text.trim())) || {text:''}).text.trim()"));
    }

    /** Type into a date text box, then click a neutral label so the datepicker closes and the model is committed. */
    public void setDate(String ngModel, String ddMMyyyy) {
        input(ngModel).fill(ddMMyyyy);
        gridTitle().click();
    }

    /** Click Save; returns the IUD response (null if no request fired, i.e. blocked by client-side validation). */
    public Response clickSaveExpectingRequest() {
        return page.waitForResponse(r -> r.url().contains("/api/DoctorAddition-Deduction/IUD"),
                new Page.WaitForResponseOptions().setTimeout(20000), () -> saveButton().click());
    }

    /** Click Search and wait for the grid fetch; returns the raw JSON response body. */
    public String search() {
        Response r = page.waitForResponse(x -> x.url().contains("/api/DoctorAddition-Deduction/fetch"),
                new Page.WaitForResponseOptions().setTimeout(20000), () -> searchButton().click());
        return r.text();
    }

    /** Delete the grid row carrying {@code remark}; waits for the IUD (STATUS) call. */
    public Response deleteRow(String remark) {
        Locator del = rowWithRemark(remark).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete"));
        return page.waitForResponse(r -> r.url().contains("/api/DoctorAddition-Deduction/IUD"),
                new Page.WaitForResponseOptions().setTimeout(20000), del::click);
    }

    /** Pattern helper for exact-ish text assertions that tolerate trailing punctuation/whitespace. */
    public static Pattern starts(String text) { return Pattern.compile("^\\s*" + Pattern.quote(text)); }
}
