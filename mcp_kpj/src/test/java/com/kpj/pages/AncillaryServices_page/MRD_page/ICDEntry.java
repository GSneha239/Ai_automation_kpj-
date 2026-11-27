package com.kpj.pages.AncillaryServices_page.MRD_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ancillary Services &gt; MRD &gt; <b>ICD Entry</b> ({@code #/ICDEntry}) — Page Object for READ-ONLY use.
 *
 * <p>Extends {@link ReadOnlyScreen}: write requests are aborted in the browser, API failures and console errors are recorded
 * and JAlert messages are captured. The ICD / procedure pickers are opened and searched but no result is ever picked, so the
 * grid is never changed; Save is only clicked by {@link #clickSaveWithNoRowSelected()}, which first unticks every row (the
 * screen then stops at "Select at least one row" before any request).</p>
 *
 * <p>Locators read from the live DOM (devhis v2026.11.27.558, 27/11/2026):</p>
 * <ul>
 *   <li>Sidebar: {@code #li0AncillaryServices} &gt; {@code #li1MRD} &gt; {@code #li2ICDEntry a[href='#/ICDEntry']} (a second
 *       "MRD" link, {@code #li2MRD}, opens the MRD file list - it is never used here); header "ICD Entry (v...)".</li>
 *   <li>Search panel: Select Date {@code enableDateFilter}; {@code ICDEntry.fromdate / todate} (disabled until Select Date);
 *       Reg. Type {@code ICDEntry.opd_ipd} (0 OPD, 1 IPD, -1 All); {@code ICDEntry.mrno} + search icon
 *       {@code SearchPatientByMRNo()}; Ward {@code ICDEntry.wardid}; Search {@code fnSearchICDEntry()}; Save
 *       {@code fnSaveICDEntry()}; Clear {@code fnClearForm()}.</li>
 *   <li>Grid {@code table.icd-entry-table}: select-all {@code selectAll}, rows {@code item in ICDEntryList} with
 *       {@code item.selected}; Audit Trail {@code span[title='Audit Trail']} -&gt; modal {@code #icdEntryAudit}; ICD launchers
 *       {@code input.icd-launcher} ({@code fnOpenIcdPicker(item, 'primary' | 'secondary' | 'external', $event)}); Procedure
 *       type-ahead {@code item.procedureSearchText} (uib-typeahead, popup appended to body).</li>
 *   <li>ICD picker {@code .icd-picker}: version buttons {@code .icd-ver-btn} (ICD-10 / ICD-11), search {@code #icdPickerSearch},
 *       results {@code a.icd-result}; closed through {@code .icd-picker-backdrop}.</li>
 *   <li>Read calls: {@code /api/ICDEntry/Fetch} (GRID, AUDIT), {@code /api/ICDEntry/SearchDiagnosis}, {@code /api/ICDEntry/SearchProcedure}.</li>
 * </ul>
 */
public class ICDEntry extends ReadOnlyScreen {

    public static final String ROUTE = "#/ICDEntry";
    public static final String READY = "[ng-click='fnSearchICDEntry()']";
    public static final String FETCH_API = "api/ICDEntry/Fetch";

    public static final String MRN = "ICDEntry.mrno";
    public static final String FROM = "ICDEntry.fromdate";
    public static final String TO = "ICDEntry.todate";
    public static final String REG_TYPE = "ICDEntry.opd_ipd";
    public static final String WARD = "ICDEntry.wardid";
    public static final String DATE_FILTER = "input[ng-model='enableDateFilter']";
    public static final String SELECT_ALL = "input[ng-model='selectAll']";
    public static final String ROW_CHECKBOX = "input[ng-model='item.selected']";
    public static final String GRID = "table.icd-entry-table";
    public static final String AUDIT_MODAL = "#icdEntryAudit";
    public static final String PICKER = ".icd-picker";

    /** Search panel and buttons, label -&gt; CSS. */
    public static final Map<String, String> SEARCH_PANEL = new LinkedHashMap<>();
    static {
        SEARCH_PANEL.put("Select Date", DATE_FILTER);
        SEARCH_PANEL.put("Discharge From Date", "[ng-model='ICDEntry.fromdate']");
        SEARCH_PANEL.put("Discharge To Date", "[ng-model='ICDEntry.todate']");
        SEARCH_PANEL.put("Reg. Type", "input[type='radio'][ng-model='ICDEntry.opd_ipd']");
        SEARCH_PANEL.put("MRN No", "[ng-model='ICDEntry.mrno']");
        SEARCH_PANEL.put("MRN search icon", "[ng-click='SearchPatientByMRNo()']");
        SEARCH_PANEL.put("Ward", "select[ng-model='ICDEntry.wardid']");
        SEARCH_PANEL.put("Search", READY);
        SEARCH_PANEL.put("Save", "[ng-click='fnSaveICDEntry()']");
        SEARCH_PANEL.put("Clear", "[ng-click='fnClearForm()']");
        SEARCH_PANEL.put("Grid", GRID);
    }

    public ICDEntry(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Ancillary Services &gt; MRD &gt; ICD Entry through the sidebar (in-app hash change; a reload logs nHIS out). */
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        expandMenuEntry(null, "^\\s*Ancillary Services\\s*$");
        // "MRD" exists twice (sub-menu #li1MRD and the MRD file-list link #li2MRD): expand the sub-menu by its id, then click the entry
        Object clicked = page.evaluate("async () => { const vis = e => e && e.offsetParent !== null; const wait = ms => new Promise(r => setTimeout(r, ms));"
                + " let link = document.querySelector(\"#li2ICDEntry a[href='#/ICDEntry']\");"
                + " if (!vis(link)) { const mrd = document.querySelector('#li1MRD > a'); if (mrd) { mrd.click(); await wait(1200); } link = document.querySelector(\"#li2ICDEntry a[href='#/ICDEntry']\"); }"
                + " if (!link) return false; link.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("ICDEntry.open: Ancillary Services > MRD > ICD Entry link not found");
            return false;
        }
        return waitUntilShown(READY) && isOnRoute(ROUTE);
    }

    /** Leave through Home and open again, so each scenario starts with a fresh controller. */
    public boolean reopen() { goHome(); return open(); }

    private boolean waitUntilShown(String css) {
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", css,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("ICDEntry: '" + css + "' did not appear within 90 s");
            return false;
        }
        waitForAngular(800);
        hookAlerts();
        return true;
    }

    // ---- reading the screen ---------------------------------------------------------------

    public Map<String, Boolean> presence(Map<String, String> controls) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : controls.entrySet()) out.put(e.getKey(), isVisible(e.getValue()));
        return out;
    }

    public boolean isChecked(String css) {
        try { return page.locator(css).first().isChecked(); } catch (Exception e) { return false; }
    }

    public boolean isDisabled(String ngModel) {
        Object r = page.evaluate("(ng) => { const e = document.querySelector('[ng-model=\"' + ng + '\"]'); return !!e && e.disabled; }", ngModel);
        return Boolean.TRUE.equals(r);
    }

    /** Value of the checked Reg. Type radio ("0" OPD, "1" IPD, "-1" All), "" when none. */
    public String regTypeChecked() {
        Object r = page.evaluate("() => { const c = [...document.querySelectorAll(\"input[type='radio'][ng-model='ICDEntry.opd_ipd']\")].find(x => x.checked);"
                + " return c ? c.value : ''; }");
        return r == null ? "" : r.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> gridHeaders() {
        Object r = page.evaluate("(g) => { const t = document.querySelector(g); if (!t) return null;"
                + " return [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim() || (h.querySelector('input[type=checkbox]') ? 'Select checkbox' : '')).filter(x => x); }", GRID);
        return r instanceof List ? (List<String>) r : null;
    }

    /** Visit rows, each header -&gt; cell text, plus "__selected" (row tick) and "__icdInputs" (categories offering an ICD box). */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> rows() {
        Object r = page.evaluate("(g) => { const t = document.querySelector(g); if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " return [...t.querySelectorAll('tbody tr')].filter(tr => tr.offsetParent !== null && tr.querySelector(\"input[ng-model='item.selected']\"))"
                + "   .map(tr => { const o = {}; hs.forEach((h, i) => { if (h && tr.cells[i]) o[h] = tr.cells[i].innerText.replace(/\\s+/g, ' ').trim(); });"
                + "     o['__selected'] = String(tr.querySelector(\"input[ng-model='item.selected']\").checked);"
                + "     o['__icdInputs'] = [...tr.querySelectorAll('input.icd-launcher')].map(x => ((x.getAttribute('ng-click') || '').match(/'(\\w+)'/) || [])[1]).filter(Boolean).join('|');"
                + "     return o; }); }", GRID);
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, String>) o));
        return out;
    }

    public Map<String, String> row(String visitNo) {
        for (Map<String, String> r : rows()) if (visitNo.equalsIgnoreCase(r.getOrDefault("Visit / Adm. No", ""))) return r;
        return new LinkedHashMap<>();
    }

    /** The patient banner ("Name : ... MRN : ..."), "" when none is shown. */
    public String patientBanner() {
        Object r = page.evaluate("() => { const v = document.querySelector('[ui-view]') || document.body;"
                + " const b = [...v.querySelectorAll('div,table,section')].filter(e => e.offsetParent !== null && /Name\\s*:/.test(e.innerText || '')"
                + "   && /MRN\\s*:/.test(e.innerText || '') && (e.innerText || '').length < 700).pop();"
                + " return b ? b.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Error text shown on the screen (server error body or a visible error dialog), "" = none. */
    public String visibleErrorText() {
        Object r = page.evaluate("() => { const body = document.body ? document.body.innerText || '' : '';"
                + " const srv = body.match(/server error in|unhandled exception|runtime error|an error has occurred|the resource cannot be found/i); if (srv) return srv[0];"
                + " const box = [...document.querySelectorAll('.jconfirm-box, .toast-error, .alert-danger, .sweet-alert')]"
                + "   .find(e => e.getBoundingClientRect().height > 0 && /error|exception|failed/i.test(e.innerText || ''));"
                + " return box ? box.innerText.replace(/\\s+/g, ' ').trim().slice(0, 200) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- read-only actions: search panel ---------------------------------------------------

    public void setMrn(String mrn) { setNgInput(MRN, mrn); }

    public void setDates(String from, String to) {
        setNgInput(FROM, from);
        setNgInput(TO, to);
    }

    public void setRegType(String value) {
        page.locator("input[type='radio'][ng-model='ICDEntry.opd_ipd'][value='" + value + "']").first().click();
        waitForAngular(400);
    }

    public void setDateFilter(boolean on) {
        if (isChecked(DATE_FILTER) == on) return;
        page.locator(DATE_FILTER).first().click();
        waitForAngular(500);
    }

    /** Search; waits for the grid request. Returns its HTTP status, 0 = no request (validation stopped it). */
    public int search() {
        clearCaptured();
        Response r = clickAndWaitForApi("fnSearchICDEntry()", FETCH_API, 20000);
        waitForAngular(1500);
        return r == null ? 0 : r.status();
    }

    /** Click Search where validation is expected to stop it (no request wait). */
    public void clickSearchOnly() { clearCaptured(); clickNg("fnSearchICDEntry()"); waitForAngular(1200); }

    public void clickClear() { clearCaptured(); clickNg("fnClearForm()"); waitForAngular(1200); }

    // ---- read-only actions: grid ------------------------------------------------------------

    private Locator rowLocator(String visitNo) {
        return page.locator(GRID + " tbody tr").filter(new Locator.FilterOptions().setHasText(visitNo)).first();
    }

    /** Tick / untick the select-all box with a real click (client-side selection only). */
    public void setSelectAll(boolean on) {
        if (isChecked(SELECT_ALL) == on) return;
        page.locator(SELECT_ALL).first().click();
        waitForAngular(500);
    }

    /** Tick / untick one visit row with a real click (client-side selection only). */
    public void setRowSelected(String visitNo, boolean on) {
        Locator cb = rowLocator(visitNo).locator(ROW_CHECKBOX).first();
        if (cb.isChecked() == on) return;
        cb.click();
        waitForAngular(500);
    }

    /** Untick every row (and select-all), so Save cannot carry a selected visit. True when nothing is left ticked. */
    public boolean untickEveryRow() {
        page.evaluate("(s) => [...document.querySelectorAll(s)].filter(c => c.checked).forEach(c => c.click())", ROW_CHECKBOX);
        if (isChecked(SELECT_ALL)) page.locator(SELECT_ALL).first().click();
        waitForAngular(500);
        Object left = page.evaluate("(s) => [...document.querySelectorAll(s)].filter(c => c.checked).length", ROW_CHECKBOX);
        return left instanceof Number && ((Number) left).intValue() == 0 && !isChecked(SELECT_ALL);
    }

    /**
     * Click Save ONLY after every row is unticked - the screen then stops at its "Select at least one row" check. Returns false
     * (and does not click) if any row could not be unticked.
     */
    public boolean clickSaveWithNoRowSelected() {
        if (!untickEveryRow()) return false;
        clearCaptured();
        clickNg("fnSaveICDEntry()");
        waitForAngular(1200);
        return true;
    }

    // ---- read-only actions: audit trail -----------------------------------------------------

    /** Open the Audit Trail of a visit; true when the modal is showing. */
    public boolean openAuditTrail(String visitNo) {
        clearCaptured();
        rowLocator(visitNo).locator("span[title='Audit Trail']").first().click();
        try {
            page.waitForFunction("(m) => { const e = document.querySelector(m); return !!e && getComputedStyle(e).display === 'block' && e.classList.contains('in'); }",
                    AUDIT_MODAL, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        waitForAngular(1000);
        return true;
    }

    /** Audit modal title text and column headers. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> auditView() {
        Object r = page.evaluate("(m) => { const e = document.querySelector(m); if (!e) return {};"
                + " const title = ((e.querySelector('.modal-title, .modal-header') || {}).innerText || '').replace(/\\s+/g, ' ').trim();"
                + " return { title, headers: [...e.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim()).filter(x => x),"
                + "   rows: [...e.querySelectorAll('tbody tr')].filter(tr => tr.innerText.trim() && !/no record/i.test(tr.innerText)).length }; }", AUDIT_MODAL);
        return r instanceof Map ? new LinkedHashMap<>((Map<String, Object>) r) : new LinkedHashMap<>();
    }

    /** Close the audit modal with its × ; true when it is gone. */
    public boolean closeAuditTrail() {
        page.locator(AUDIT_MODAL + " button.close").first().click();
        try {
            page.waitForFunction("(m) => { const e = document.querySelector(m); return !e || getComputedStyle(e).display === 'none'; }",
                    AUDIT_MODAL, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) {
            // fall back to the page's own Bootstrap API (still only hides the read-only pop-up)
            page.evaluate("(m) => { if (window.jQuery) window.jQuery(m).modal('hide'); }", AUDIT_MODAL);
            waitForAngular(800);
            Object hidden = page.evaluate("(m) => { const e = document.querySelector(m); return !e || getComputedStyle(e).display === 'none'; }", AUDIT_MODAL);
            if (!Boolean.TRUE.equals(hidden)) return false;
        }
        waitForAngular(500);
        return true;
    }

    // ---- read-only actions: ICD picker -------------------------------------------------------

    /** Click a visit's ICD box for {@code category} ("primary", "secondary", "external"). True when the picker opened. */
    public boolean openIcdPicker(String visitNo, String category) {
        clearCaptured();
        Locator launcher = rowLocator(visitNo).locator("input.icd-launcher[ng-click*=\"'" + category + "'\"]").first();
        if (launcher.count() == 0) return false;
        launcher.click();
        try {
            page.waitForSelector(PICKER, new Page.WaitForSelectorOptions().setTimeout(3000));
        } catch (Exception e) { return false; }
        waitForAngular(400);
        return isPickerOpen();
    }

    public boolean isPickerOpen() { return page.locator(PICKER).count() > 0 && page.locator(PICKER).first().isVisible(); }

    /** Version buttons in the open picker, label -&gt; "active|enabled" flags. */
    @SuppressWarnings("unchecked")
    public Map<String, String> pickerVersions() {
        Object r = page.evaluate("(p) => { const o = {}; document.querySelectorAll(p + ' .icd-ver-btn').forEach(b => {"
                + " o[b.innerText.trim()] = (b.classList.contains('btn-primary') ? 'active' : 'inactive') + '|' + (b.disabled ? 'disabled' : 'enabled'); }); return o; }", PICKER);
        return r instanceof Map ? new LinkedHashMap<>((Map<String, String>) r) : new LinkedHashMap<>();
    }

    /** Type into the picker's search the way a user does; returns the results shown (nothing is picked). */
    @SuppressWarnings("unchecked")
    public List<String> searchInPicker(String text) {
        Locator box = page.locator("#icdPickerSearch").first();
        box.click();
        box.fill("");
        box.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(60));
        try {
            page.waitForFunction("(p) => document.querySelectorAll(p + ' a.icd-result').length > 0", PICKER,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception noResults) { /* reported by the caller as an empty list */ }
        waitForAngular(500);
        Object r = page.evaluate("(p) => [...document.querySelectorAll(p + ' a.icd-result')].map(a => a.innerText.replace(/\\s+/g, ' ').trim())", PICKER);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Close the picker without picking (backdrop click). True when it is closed. */
    public boolean closeIcdPicker() {
        if (page.locator(".icd-picker-backdrop").count() > 0) page.locator(".icd-picker-backdrop").first().click(new Locator.ClickOptions().setPosition(5, 5));
        waitForAngular(500);
        return !isPickerOpen();
    }

    // ---- read-only actions: procedure type-ahead ---------------------------------------------

    /** Type into a visit's Procedure box; returns the suggestions shown. The box is then emptied - nothing is picked. */
    @SuppressWarnings("unchecked")
    public List<String> searchProcedure(String visitNo, String text) {
        Locator box = rowLocator(visitNo).locator("input[ng-model='item.procedureSearchText']").first();
        box.click();
        box.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(60));
        try {
            page.waitForFunction("() => [...document.querySelectorAll('ul.dropdown-menu[uib-typeahead-popup] li')].some(li => li.offsetParent !== null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception noResults) { /* reported by the caller as an empty list */ }
        Object r = page.evaluate("() => [...document.querySelectorAll('ul.dropdown-menu[uib-typeahead-popup] li')].filter(li => li.offsetParent !== null)"
                + ".map(li => li.innerText.replace(/\\s+/g, ' ').trim())");
        box.press("Escape");
        box.fill("");
        waitForAngular(400);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Number of procedures recorded on the visit's row (must stay unchanged by these read-only checks). */
    public int procedureCount(String visitNo) {
        Object r = rowLocator(visitNo).evaluate("tr => { const s = angular.element(tr).scope(); return s && s.item && s.item.ProcedureList ? s.item.ProcedureList.length : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }
}
