package com.kpj.pages.Billing_page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Discount Manager/CR &gt; <b>Add</b> ({@code #/add-concessionManager}) — Page Object for READ-ONLY use.
 *
 * <p>Reached from the list's Add button, so it extends {@link DiscountManagerList} (same write guard, monitors and table
 * readers). Values may be entered and discounts calculated on screen; Save ({@code IUDBill()}) is only checked for presence and
 * is never clicked.</p>
 *
 * <p>Locators read from the live DOM (devhis v2026.11.26.546, 26/11/2026):</p>
 * <ul>
 *   <li>Patient: MRN {@code ConcessionManager1.MRNO}, Reg. Type {@code ConcessionManager1.PatientType} (0 OPD, 1 IPD),
 *       Find Patient {@code FindPatient();}, Search Patient {@code OpenPopupScreen()}.</li>
 *   <li>Discount: {@code ConcessionManager.ConcessionPercentage}, Discount Amount {@code ConcessionManager.TotalConcessionAmount},
 *       Amount checkbox {@code ConcessionManager.IsConAmountOn}, Bill Wise / Service Wise {@code ConcessionManager.ConcessionOn}
 *       (1 / 0), {@code ConcessionManager.ConcessionTemplateID} + Apply, {@code ConcessionManager.ConcessionAuthorizedByID},
 *       {@code ConcessionManager.ConcessionReasonID}, {@code ConcessionManager.CashCounterId}, Exclude Dr.Amount
 *       {@code ConcessionManager.IsExcludeAmt}.</li>
 *   <li>Grids: bills (row checkbox {@code bill.IsSelected}, ng-click {@code SelectBillDetails(bill,$index)}), previous discounts,
 *       services. Buttons: Save {@code IUDBill()} (NEVER clicked), Clear {@code CancelForm()}, Back {@code CloseForm()}.</li>
 *   <li>Discounts are rounded to the nearest 0.05 ({@code roundToNearestPoint02}).</li>
 * </ul>
 */
public class DiscountManagerAdd extends DiscountManagerList {

    public static final String ROUTE = "#/add-concessionManager";
    public static final String READY = "[ng-click='FindPatient();']";

    public static final String MRN = "ConcessionManager1.MRNO";
    public static final String REG_TYPE = "ConcessionManager1.PatientType";
    public static final String PERCENTAGE = "ConcessionManager.ConcessionPercentage";
    public static final String DISCOUNT_AMOUNT = "ConcessionManager.TotalConcessionAmount";
    public static final String AMOUNT_CHECKBOX = "input[ng-model='ConcessionManager.IsConAmountOn']";
    public static final String EXCLUDE_DR = "input[ng-model='ConcessionManager.IsExcludeAmt']";
    public static final String CONCESSION_ON = "ConcessionManager.ConcessionOn";
    public static final String AUTHORIZED_BY = "ConcessionManager.ConcessionAuthorizedByID";
    public static final String REASON = "ConcessionManager.ConcessionReasonID";
    public static final String CASH_COUNTER = "ConcessionManager.CashCounterId";
    public static final String TEMPLATE = "ConcessionManager.ConcessionTemplateID";

    /** Header regexes of the three grids. */
    public static final String BILL_GRID = "Self Balance";
    public static final String PREVIOUS_GRID = "^\\s*Bill No\\.\\s+Date";
    public static final String SERVICE_GRID = "Service Code";

    /** Add screen controls, label -&gt; CSS. Save is checked for presence only. */
    public static final Map<String, String> LAYOUT = new LinkedHashMap<>();
    static {
        LAYOUT.put("MRN", "[ng-model='ConcessionManager1.MRNO']");
        LAYOUT.put("Find Patient", READY);
        LAYOUT.put("Search Patient", "[ng-click='OpenPopupScreen()']");
        LAYOUT.put("Reg. Type", "input[type='radio'][ng-model='ConcessionManager1.PatientType']");
        LAYOUT.put("Concession Percentage", "[ng-model='ConcessionManager.ConcessionPercentage']");
        LAYOUT.put("Discount Amount", "[ng-model='ConcessionManager.TotalConcessionAmount']");
        LAYOUT.put("Amount", AMOUNT_CHECKBOX);
        LAYOUT.put("Bill Wise / Service Wise", "input[type='radio'][ng-model='ConcessionManager.ConcessionOn']");
        LAYOUT.put("Concession Template", "select[ng-model='ConcessionManager.ConcessionTemplateID']");
        LAYOUT.put("Apply", "[ng-click='fnGetServicesForConByTemplateID();']");
        LAYOUT.put("Dis. Authorized By", "select[ng-model='ConcessionManager.ConcessionAuthorizedByID']");
        LAYOUT.put("Discount Reason", "select[ng-model='ConcessionManager.ConcessionReasonID']");
        LAYOUT.put("Cash Counter", "select[ng-model='ConcessionManager.CashCounterId']");
        LAYOUT.put("Exclude Dr.Amount", EXCLUDE_DR);
        LAYOUT.put("Save", "[ng-click='IUDBill()']");
        LAYOUT.put("Clear", "[ng-click='CancelForm()']");
        LAYOUT.put("Back", "[ng-click='CloseForm()']");
    }

    public DiscountManagerAdd(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Discount list (sidebar) &gt; Add. */
    @Override
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        if (!super.open()) return false;
        return clickAdd();
    }

    /** Back to the list; true when the list is shown. */
    public boolean clickBack() {
        if (!clickNg("CloseForm()")) return false;
        return waitUntilShown(DiscountManagerList.READY) && isOnRoute(DiscountManagerList.ROUTE);
    }

    // ---- reading the screen ---------------------------------------------------------------

    public String addRegTypeChecked() { return checkedRadio(REG_TYPE); }
    public String concessionOnChecked() { return checkedRadio(CONCESSION_ON); }

    public boolean isDisabled(String ngModel) {
        Object r = page.evaluate("(ng) => { const e = [...document.querySelectorAll('[ng-model=\"' + ng + '\"]')].find(x => x.offsetParent !== null)"
                + " || document.querySelector('[ng-model=\"' + ng + '\"]'); return !!e && e.disabled; }", ngModel);
        return Boolean.TRUE.equals(r);
    }

    public boolean isChecked(String css) {
        try { return page.locator(css).first().isChecked(); } catch (Exception e) { return false; }
    }

    public String percentage() { String v = inputValue(PERCENTAGE); return v == null ? "" : v.trim(); }
    public String discountAmount() { String v = inputValue(DISCOUNT_AMOUNT); return v == null ? "" : v.trim(); }

    public List<Map<String, String>> billRows() { List<Map<String, String>> r = tableRows(BILL_GRID); return r == null ? new ArrayList<>() : r; }
    public List<Map<String, String>> previousDiscountRows() { return tableRows(PREVIOUS_GRID); }
    public List<Map<String, String>> serviceRows() { List<Map<String, String>> r = tableRows(SERVICE_GRID); return r == null ? new ArrayList<>() : r; }

    public Map<String, String> billRow(String billNo) {
        for (Map<String, String> r : billRows()) if (billNo.equalsIgnoreCase(r.getOrDefault("Bill No.", ""))) return r;
        return new LinkedHashMap<>();
    }

    // ---- read-only actions ------------------------------------------------------------------

    @Override
    public void setMrn(String mrn) { setNgInput(MRN, mrn); }

    public void setAddRegType(String value) { clickRadio(REG_TYPE, value); }

    /** Find Patient where validation should stop it (no request expected). */
    @Override
    public void findPatientExpectingValidation() { clearCaptured(); clickNg("FindPatient();"); waitForAngular(1500); }

    /** Find Patient; waits for the lookup and, when found, the bill list it loads. Returns the lookup's HTTP status, 0 = none. */
    @Override
    public int findPatient() {
        clearCaptured();
        int logBefore = apiLog.size();
        Response r = clickAndWaitForApi("FindPatient();", PATIENT_API, 20000);
        if (r != null) {
            // A found patient's bills load after the lookup; an unknown MRN loads nothing, so this wait simply runs out.
            try {
                page.waitForFunction("(re) => { const rx = new RegExp(re); const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                        + " .find(x => rx.test((x.querySelector('thead') || {}).innerText || '')); return !!t && [...t.querySelectorAll('tbody tr')].some(tr => tr.innerText.trim()); }",
                        BILL_GRID, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception noBills) {
                System.out.println("DiscountManagerAdd.findPatient: no bills listed (apiLog +" + (apiLog.size() - logBefore) + ")");
            }
        }
        waitForAngular(1500);
        return r == null ? 0 : r.status();
    }

    /** Tick a bill row (client-side selection) and wait until its services are listed. True when services appeared. */
    public boolean selectBill(String billNo) {
        Locator cb = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(billNo))
                .locator("input[ng-model='bill.IsSelected']").first();
        cb.scrollIntoViewIfNeeded();
        cb.click(new Locator.ClickOptions().setTimeout(15000));
        try {
            page.waitForFunction("(re) => { const rx = new RegExp(re); const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                    + " .find(x => rx.test((x.querySelector('thead') || {}).innerText || '')); return !!t && t.querySelectorAll('tbody tr input[type=checkbox]').length > 0; }",
                    SERVICE_GRID, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            return false;
        }
        waitForAngular(1000);
        return true;
    }

    /** Type a header Concession Percentage the way a user does (fill, then Tab so blur / change handlers run). */
    public void typePercentage(String value) {
        clearCaptured();
        Locator box = page.locator("input[ng-model='" + PERCENTAGE + "']").first();
        box.click();
        box.fill(value);
        box.press("Tab");
        waitForAngular(1200);
    }

    /** Tick / untick the Amount checkbox with a real click. */
    public void setAmountMode(boolean on) {
        if (isChecked(AMOUNT_CHECKBOX) == on) return;
        page.locator(AMOUNT_CHECKBOX).first().click();
        waitForAngular(800);
    }

    /** Bill Wise ("1") or Service Wise ("0"). */
    public void setConcessionOn(String value) { clickRadio(CONCESSION_ON, value); waitForAngular(500); }

    /** Clear (resets the form on the client; no request). */
    public void clickClear() { clearCaptured(); clickNg("CancelForm()"); waitForAngular(1200); }
}
