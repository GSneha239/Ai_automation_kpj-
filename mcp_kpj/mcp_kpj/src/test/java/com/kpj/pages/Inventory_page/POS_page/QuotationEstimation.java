package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory &gt; POS &gt; <b>Quotation Estimation</b> — Page Object (read-only use).
 *
 * <p>Locators from the live DOM (hisdemo v2026.11.13.455, manual test 13/11/2026):</p>
 * <ul>
 *   <li>List {@code #/QuotationEstimation} (BillEstimationListController): {@code BillEstimation.fromdate / .todate},
 *       Search = {@code fngetBillEstimationMasterGrid()}, Add = link {@code AddEstimationMaster()} (shown when
 *       Access.IsAdd); ui-grid {@code gridOptions}. API {@code api/BillEstimation/Fetch} ExecFlag GRID.</li>
 *   <li>Add {@code #/add-QuotationEstimation} (BillEstimationController): MRN {@code BillEstimation.MRNo} + search
 *       {@code SearchPatientByMRNo();} + popup {@code openPopupScreen();}; Reg. Type radios
 *       {@code BillEstimations.opd_ipd} 0=OPD 1=IPD 2=External Patient (IPD and External share id radios-1);
 *       names {@code BillEstimation.firstName / lastName}; selects {@code .genderid .companyid .tariffid .classid
 *       .storeid .billtemplateid}; External-Patient item row {@code #txtItemCode} / {@code #txtServiceName}
 *       (autocomplete, API {@code api/GRN/fetchItemByStore}); Add Bill Template = {@code GetBillTemplateServices()};
 *       Submit = {@code IUDBillEstimation()} and Print = {@code PrintBillEstimation()} - BOTH post
 *       {@code api/BillEstimation/IUD} (blocked by the read-only guard); Cancel = {@code Clear()}, Back = {@code fnBack()}.</li>
 * </ul>
 */
public class QuotationEstimation extends ReadOnlyScreen {

    public static final String LIST_ROUTE = "#/QuotationEstimation";
    public static final String ADD_ROUTE = "#/add-QuotationEstimation";
    public static final String LIST_READY = "[ng-controller='BillEstimationListController'] [ui-grid='gridOptions']";
    public static final String LIST_API = "api/BillEstimation/Fetch";
    public static final String ITEM_API = "api/GRN/fetchItemByStore";

    public static final String TEMPLATE = "BillEstimation.billtemplateid";
    public static final String TARIFF = "BillEstimation.tariffid";
    public static final String CLASS = "BillEstimation.classid";
    public static final String TOTAL = "BillEstimation.totalbillamount";
    public static final String FIRST_NAME = "BillEstimation.firstName";
    public static final String LAST_NAME = "BillEstimation.lastName";

    public QuotationEstimation(Page page) { super(page); }

    public boolean openList() {
        if (isOnRoute(ADD_ROUTE)) { clickNg("fnBack()"); waitForAngular(2000); }
        return openViaMenu("^\\s*POS\\s*$", LIST_ROUTE, LIST_READY);
    }

    public void setListDates(String from, String to) {
        setNgInput("BillEstimation.fromdate", from);
        setNgInput("BillEstimation.todate", to);
    }

    public int searchList() {
        Response r = clickAndWaitForApi("fngetBillEstimationMasterGrid()", LIST_API, 30000);
        waitForAngular(1000);
        return r == null ? 0 : r.status();
    }

    /** List → Add; a fresh form each time (the list's Add link resets the controller). */
    public boolean openAddForm() {
        if (isOnRoute(ADD_ROUTE)) { clickNg("fnBack()"); waitForAngular(2000); }
        if (!openList()) return false;
        clickNg("AddEstimationMaster()");
        try {
            page.waitForFunction("() => !!document.querySelector('[ng-click=\"IUDBillEstimation()\"]')", null,
                    new Page.WaitForFunctionOptions().setTimeout(60000));
        } catch (Exception ignore) { }
        waitForAngular(2500);
        hookAlerts();
        return isOnRoute(ADD_ROUTE);
    }

    /** Current Reg. Type model value ("0" OPD, "1" IPD, "2" External Patient). */
    public String regType() {
        Object r = page.evaluate("() => { const e = document.querySelector('input[ng-model=\"BillEstimations.opd_ipd\"]');"
                + " const s = e && angular.element(e).scope(); return s && s.BillEstimations ? String(s.BillEstimations.opd_ipd) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the TEXT label "External Patient." (what a user clicks most of the time). */
    public void clickExternalPatientLabel() {
        page.locator("label.radio-inline", new Page.LocatorOptions().setHasText("External Patient")).first().click(new com.microsoft.playwright.Locator.ClickOptions().setPosition(60, 8));
        waitForAngular(800);
    }

    /** Click the radio circle of a Reg. Type value directly ("0", "1", "2"). */
    public void clickRegTypeRadio(String value) {
        page.locator("input[type='radio'][ng-model='BillEstimations.opd_ipd'][value='" + value + "']").click();
        waitForAngular(800);
    }

    public boolean itemRowVisible() { return isVisible("#txtItemCode") && isVisible("#txtServiceName"); }

    /** Type into an autocomplete box like a user and wait for the item API. Returns {status, body} or null. */
    public String[] typeItemLookup(String css, String text, int timeoutMs) {
        page.locator(css).fill("");
        try {
            Response r = page.waitForResponse(x -> x.url().contains(ITEM_API), new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> page.locator(css).type(text, new com.microsoft.playwright.Locator.TypeOptions().setDelay(120)));
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            return new String[]{String.valueOf(r.status()), trim(body, 200)};
        } catch (Exception e) {
            System.out.println("typeItemLookup(" + css + "): no " + ITEM_API + " request - " + trim(e.getMessage(), 120));
            return null;
        }
    }

    /** Autocomplete suggestions currently shown. */
    public int suggestionCount() {
        Object r = page.evaluate("() => [...document.querySelectorAll('.auto-complete-container li, .auto-complete-item, ul.auto-complete-list li')]"
                + ".filter(e => e.offsetParent !== null && e.innerText.trim()).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    public void clickAddBillTemplate() { clearCaptured(); clickNg("GetBillTemplateServices()"); waitForAngular(2500); }

    /** Service grid rows: [code, name, qty, unitPrice, total]. */
    @SuppressWarnings("unchecked")
    public List<String[]> serviceRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll('[ng-click^=\"RemoveService\"]')].filter(b => b.offsetParent !== null)"
                + ".map(b => [...b.closest('tr').querySelectorAll('td')].slice(1).map(td => td.innerText.trim()))");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<Object>) r) out.add(((List<String>) row).toArray(new String[0]));
        return out;
    }

    public void removeServiceRow(int index) {
        page.locator("[ng-click^='RemoveService']").nth(index).click();
        waitForAngular(1200);
    }

    public String total() { String v = inputValue(TOTAL); return v == null ? "" : v.trim(); }

    public static double money(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    public void clickSubmit() { clearCaptured(); clickNg("IUDBillEstimation()"); waitForAngular(2500); }

    public void clickPrint() { clearCaptured(); clickNg("PrintBillEstimation()"); waitForAngular(2500); }

    public void clickCancel() { clickNg("Clear()"); waitForAngular(1500); }

    public void clickBack() { clickNg("fnBack()"); waitForAngular(2000); }
}
