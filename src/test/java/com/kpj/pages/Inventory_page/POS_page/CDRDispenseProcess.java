package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inventory &gt; POS &gt; <b>CDR Dispense Process</b> — Page Object (read-only use).
 *
 * <p>Locators from the live DOM (hisdemo v2026.11.13.455, manual test 13/11/2026). Route {@code #/CDRDispenseProcess},
 * controllers MedicationOrderController + MIMSInfoController (template {@code Load/MedicationOrder}).</p>
 * <ul>
 *   <li>Filters: {@code #txtFromDate}/{@code #txtToDate} ({@code MedicationOrder.FromDate/ToDate}), {@code #txtMRNo} +
 *       {@code SearchPatientByMRNo();}, Progress {@code MedicationOrder.Progress} (mandatory for Search), Prescription Type,
 *       Reg. Type radios {@code MedicationOrder.OPDIPD} (0 OPD, 1 IPD, 2 ALL), Doctor, advanced filters.</li>
 *   <li>Search = {@code Getprescriptionlistsearch();} → {@code api/MedicationOrder/fetch} ExecFlag GetPrescriptionList;
 *       Clear = {@code ClearFilter();}; list search box {@code cdrSearch}; page size {@code cdrPageSize}; pager
 *       {@code selectPage(...)}; "Showing a–b of n item(s)".</li>
 *   <li>Row selection = the row checkbox {@code GetprescriptionDruglistsearch(Pl)} (loads banner and drugs).</li>
 * </ul>
 *
 * <p><b>Safety:</b> every prescription row also carries workflow cells (Prepared, Packed, Verify, Checked, Dispensed,
 * Collected, Acknowledged, TCA, MHD ...). This page object only ever clicks the row's selection checkbox - never any
 * other cell - and never toggles the column picker (that saves a user preference via
 * {@code api/ColumnSetting/SaveAdminColumn}).</p>
 */
public class CDRDispenseProcess extends ReadOnlyScreen {

    public static final String ROUTE = "#/CDRDispenseProcess";
    public static final String READY = "[ng-click='Getprescriptionlistsearch();']";
    public static final String LIST_API = "api/MedicationOrder/fetch";
    public static final String PROGRESS = "MedicationOrder.Progress";

    /** console.log lines from the screen's column-setting debug output ("BW-360 ..."). */
    public final AtomicInteger debugLogLines = new AtomicInteger();
    private final AtomicInteger listResponses = new AtomicInteger();
    public String lastListRequest = "";

    public CDRDispenseProcess(Page page) {
        super(page);
        page.onConsoleMessage(m -> { if (m.text() != null && m.text().contains("BW-360")) debugLogLines.incrementAndGet(); });
        page.onResponse(r -> {
            try {
                if (r.url().contains(LIST_API) && String.valueOf(r.request().postData()).contains("GetPrescriptionList")) listResponses.incrementAndGet();
            } catch (Exception ignore) { }
        });
    }

    public boolean open() { return openViaMenuPath(ROUTE, READY, "^\\s*Inventory\\s*$", "^\\s*POS\\s*$"); }

    public boolean reopen() { goHome(); return open(); }

    public void setDates(String from, String to) {
        setNgInput("MedicationOrder.FromDate", from);
        setNgInput("MedicationOrder.ToDate", to);
    }

    public void clickRegType(String value) {
        page.locator("input[type='radio'][ng-model='MedicationOrder.OPDIPD'][value='" + value + "']").click();
        waitForAngular(500);
    }

    public String regType() {
        Object r = page.evaluate("() => { const e = document.querySelector('input[ng-model=\"MedicationOrder.OPDIPD\"]');"
                + " const s = e && angular.element(e).scope(); return s && s.MedicationOrder ? String(s.MedicationOrder.OPDIPD) : ''; }");
        return String.valueOf(r);
    }

    /**
     * Search and wait for the prescription list. Returns {status, number of GetPrescriptionList responses this click
     * produced} (status 0 = no request, e.g. validation stopped it).
     */
    public int[] search() {
        clearCaptured();
        int before = listResponses.get();
        Response r = null;
        try {
            r = page.waitForResponse(x -> x.url().contains(LIST_API) && String.valueOf(x.request().postData()).contains("GetPrescriptionList"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> clickNg("Getprescriptionlistsearch();"));
        } catch (Exception e) {
            System.out.println("CDR.search: no list response - " + trim(e.getMessage(), 120));
        }
        waitForAngular(3500);   // let any duplicate request land before counting
        if (r == null) { lastListRequest = ""; return new int[]{0, listResponses.get() - before}; }
        try { lastListRequest = String.valueOf(r.request().postData()); } catch (Exception e) { lastListRequest = ""; }
        return new int[]{r.status(), listResponses.get() - before};
    }

    public void clickSearchOnly() { clearCaptured(); clickNg("Getprescriptionlistsearch();"); waitForAngular(1500); }

    /** "Showing a–b of n item(s)" for the prescription list (first occurrence). */
    public String listInfo() {
        Object r = page.evaluate("() => (document.body.innerText.match(/Showing [^\\n]*item\\(s\\)/) || [''])[0]");
        return String.valueOf(r);
    }

    public int listTotal() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+(\\d+)\\s+item").matcher(listInfo());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** PrescriptionDate of every prescription in the controller's list (not just the visible page). */
    @SuppressWarnings("unchecked")
    public List<String> prescriptionDates() {
        Object r = page.evaluate("() => { const t = document.querySelector('[ng-click=\"GetprescriptionDruglistsearch(Pl)\"]');"
                + " let s = t && angular.element(t).scope(); while (s && !Array.isArray(s.PrescriptionList)) s = s.$parent;"
                + " return s ? s.PrescriptionList.map(x => String(x.PrescriptionDate || '')) : []; }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Visible header labels of the prescription table. */
    @SuppressWarnings("unchecked")
    public List<String> visiblePrescriptionHeaders() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)[0];"
                + " return t ? [...t.querySelectorAll('thead th')].filter(h => h.offsetParent !== null).map(h => h.innerText.replace(/\\s+/g, ' ').trim()).filter(x => x) : []; }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Typed like a user into the "Search prescriptions" box. */
    public void typeListSearch(String text) {
        page.locator("input[ng-model='cdrSearch']").fill("");
        page.locator("input[ng-model='cdrSearch']").type(text, new com.microsoft.playwright.Locator.TypeOptions().setDelay(60));
        waitForAngular(1200);
    }

    public void setPageSize(String size) { pickOption("cdrPageSize", size); waitForAngular(1200); }

    public boolean goToPage(String number) {
        Object r = page.evaluate("(n) => { const a = [...document.querySelectorAll('[ng-click=\"selectPage(page.number, $event)\"]')].find(x => x.offsetParent !== null && x.innerText.trim() === n);"
                + " if (!a) return false; a.click(); return true; }", number);
        waitForAngular(1200);
        return Boolean.TRUE.equals(r);
    }

    /** Index (on the visible page) of the prescription with the oldest PrescriptionDate, or -1. */
    public int oldestRowIndex() {
        Object r = page.evaluate("() => { const boxes = [...document.querySelectorAll('[ng-click=\"GetprescriptionDruglistsearch(Pl)\"]')].filter(b => b.offsetParent !== null);"
                + " const p = d => { const m = String(d).match(/(\\d{2})\\/(\\d{2})\\/(\\d{4})/); return m ? new Date(+m[3], +m[2] - 1, +m[1]).getTime() : Infinity; };"
                + " let best = -1, bt = Infinity; boxes.forEach((b, i) => { const pl = angular.element(b).scope().Pl; const t = p(pl && pl.PrescriptionDate); if (t < bt) { bt = t; best = i; } });"
                + " return best; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Tick the selection checkbox of visible row {@code index} (ONLY that checkbox). */
    public void selectRow(int index) {
        clearCaptured();
        page.locator("[ng-click='GetprescriptionDruglistsearch(Pl)']").nth(index).click();
        waitForAngular(5000);
    }

    /** Untick whatever row is ticked (selection only - no data change). */
    public void unselectRows() {
        page.evaluate("() => { [...document.querySelectorAll('[ng-click=\"GetprescriptionDruglistsearch(Pl)\"]')].filter(b => b.checked).forEach(b => b.click()); }");
        waitForAngular(1500);
    }

    public boolean patientBannerShown() {
        return Boolean.TRUE.equals(page.evaluate("() => /ALLERGY DESCRIPTION/.test(document.body.innerText)"));
    }

    /** Drugs loaded for the selected prescription (controller data, independent of column visibility). */
    public int drugCount() {
        Object r = page.evaluate("() => { const rows = [...document.querySelectorAll('tr')].filter(t => { try { return !!angular.element(t).scope().PDl; } catch (e) { return false; } });"
                + " return rows.length; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** First drug's name / quantity, from the row scope. */
    public String firstDrug() {
        Object r = page.evaluate("() => { const tr = [...document.querySelectorAll('tr')].find(t => { try { return !!angular.element(t).scope().PDl; } catch (e) { return false; } });"
                + " if (!tr) return ''; const d = angular.element(tr).scope().PDl; return (d.serviceitemcode || '') + ' ' + (d.serviceitemname || '') + ' qty ' + (d.quantity || ''); }");
        return String.valueOf(r);
    }

    /** Text of a visible ngConfirm / custom modal message (e.g. "This prescription has expired ..."), or "". */
    public String visibleMessageBox() {
        Object r = page.evaluate("() => { const c = [...document.querySelectorAll('.jconfirm-box, .modal.in .modal-body, .sweet-alert, .bootbox-body, .custom-alert, .modal-content')]"
                + ".filter(e => e.getBoundingClientRect().height > 0).map(e => e.innerText.replace(/\\s+/g, ' ').trim()).filter(t => t);"
                + " const exp = [...document.querySelectorAll('div')].filter(e => e.getBoundingClientRect().height > 0 && /has expired on/i.test(e.innerText) && e.children.length < 6).map(e => e.innerText.replace(/\\s+/g, ' ').trim());"
                + " return (exp[0] || c[0] || ''); }");
        return String.valueOf(r);
    }

    /** Close an "OK" message box if one is open. */
    public void closeMessageBox() {
        page.evaluate("() => { const b = [...document.querySelectorAll('button, a.btn')].find(x => x.getBoundingClientRect().height > 0 && /^\\s*ok\\s*$/i.test(x.innerText)); if (b) b.click(); }");
        waitForAngular(800);
    }

    public boolean buttonDisabled(String ngClick) {
        return Boolean.TRUE.equals(page.evaluate("(n) => { const b = document.querySelector('[ng-click=\"' + n + '\"]'); return !!b && b.disabled; }", ngClick));
    }

    public void click(String ngClick) { clearCaptured(); clickNg(ngClick); waitForAngular(2500); }

    public String fieldValue(String css) {
        Object r = page.evaluate("(s) => { const e = document.querySelector(s); return e ? e.value : null; }", css);
        return String.valueOf(r);
    }
}
