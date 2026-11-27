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
 * Ancillary Services &gt; MRD &gt; <b>MRD</b> (MRD file list, {@code #/MRDFileList}) — Page Object for READ-ONLY use.
 *
 * <p>Extends {@link ReadOnlyScreen}: write requests are aborted in the browser, API failures and console errors are recorded and
 * JAlert messages are captured. Nothing here clicks Add, Edit, a row's Scanning Status / Update, Scrap, the Non Allocated
 * List's Print, File Linking's Add / Submit or the Investigation View's Show.</p>
 *
 * <p>Locators read from the live DOM (devhis v2026.11.27.558, 27/11/2026):</p>
 * <ul>
 *   <li>Sidebar: {@code #li0AncillaryServices} &gt; {@code #li1MRD} &gt; {@code #li2MRD a[href='#/MRDFileList']} (the sub-menu and the
 *       screen are both called "MRD"); header "MRD (v...)".</li>
 *   <li>Search panel: Select Date {@code enableDateFilter}; {@code MRDFileList.fromdate / todate}; {@code MRDFileList.mrno} +
 *       {@code SearchPatientByMRNo();} + {@code openPopupScreen()}; Reg. Type {@code MRDFileList.opd_ipd} (0 OPD, 1 IPD, 2 All);
 *       {@code mrdfiletypeid}, {@code rackid}, {@code fileno}, {@code filetypeno}, {@code classificationtag}, {@code rowid},
 *       {@code boxid}; Diagnosis tick {@code #DiagnosisChk} + {@code #Diagnosis}; {@code scanningstatus} ("" / 1 Yes / 0 No);
 *       Search {@code fngetMRDFileGrid(true)}; Add {@code AddMRDFile()} (presence only).</li>
 *   <li>Grid (ui-grid, 25 per page): row selection buttons in the left container; per row EMR ({@code ShowEMR}),
 *       File Linking ({@code ViewFileLinking}, needs the row selected - opens {@code #/FileLinking}).</li>
 *   <li>List buttons: {@code nonallocatedlist()} -&gt; {@code #nonallocatedlist}; {@code openFileMovementHistory()} -&gt;
 *       {@code #fileMovementHistory}; {@code ViewInvestigation()} (needs a selected row) -&gt; uib modal "Investigation View";
 *       {@code fnOpenPendingFileReturnList()} -&gt; {@code #pendingFileReturnList}. Patient Search pop-up: uib modal "Patient Search",
 *       its own MRN {@code PatientData.MRNo}, {@code SearchPatient(0);}, row button {@code title='Select'}.</li>
 *   <li>Read call: {@code /api/MRDFile/Fetch} (list, non-allocated, file movement, pending returns).</li>
 * </ul>
 */
public class MRD extends ReadOnlyScreen {

    public static final String ROUTE = "#/MRDFileList";
    public static final String READY = "[ng-click='fngetMRDFileGrid(true)']";
    public static final String FETCH_API = "api/MRDFile/Fetch";

    public static final String MRN = "MRDFileList.mrno";
    public static final String FROM = "MRDFileList.fromdate";
    public static final String TO = "MRDFileList.todate";
    public static final String FILE_TYPE = "MRDFileList.mrdfiletypeid";
    public static final String RACK = "MRDFileList.rackid";
    public static final String ROW = "MRDFileList.rowid";
    public static final String BOX = "MRDFileList.boxid";
    public static final String FILE_NO = "MRDFileList.fileno";
    public static final String DIAGNOSIS = "MRDFileList.Diagnosis";
    public static final String SCANNING = "MRDFileList.scanningstatus";
    public static final String DATE_FILTER = "input[ng-model='enableDateFilter']";
    public static final String DIAGNOSIS_FILTER = "#DiagnosisChk";

    public static final String NON_ALLOCATED_MODAL = "#nonallocatedlist";
    public static final String FILE_MOVEMENT_MODAL = "#fileMovementHistory";
    public static final String PENDING_RETURN_MODAL = "#pendingFileReturnList";

    /** Search panel and screen buttons, label -&gt; CSS. Add is checked for presence only. */
    public static final Map<String, String> SEARCH_PANEL = new LinkedHashMap<>();
    /** List buttons under the grid, label -&gt; CSS. */
    public static final Map<String, String> LIST_BUTTONS = new LinkedHashMap<>();
    static {
        SEARCH_PANEL.put("Select Date", DATE_FILTER);
        SEARCH_PANEL.put("From Date", "[ng-model='MRDFileList.fromdate']");
        SEARCH_PANEL.put("To Date", "[ng-model='MRDFileList.todate']");
        SEARCH_PANEL.put("MRN", "[ng-model='MRDFileList.mrno']");
        SEARCH_PANEL.put("Find Patient", "[ng-click='SearchPatientByMRNo();']");
        SEARCH_PANEL.put("Search Patient", "[ng-click='openPopupScreen()']");
        SEARCH_PANEL.put("Reg. Type", "input[type='radio'][ng-model='MRDFileList.opd_ipd']");
        SEARCH_PANEL.put("File Type", "select[ng-model='MRDFileList.mrdfiletypeid']");
        SEARCH_PANEL.put("Rack", "select[ng-model='MRDFileList.rackid']");
        SEARCH_PANEL.put("File No.", "[ng-model='MRDFileList.fileno']");
        SEARCH_PANEL.put("File Type No.", "[ng-model='MRDFileList.filetypeno']");
        SEARCH_PANEL.put("Classification Tag", "[ng-model='MRDFileList.classificationtag']");
        SEARCH_PANEL.put("Row", "select[ng-model='MRDFileList.rowid']");
        SEARCH_PANEL.put("Box", "select[ng-model='MRDFileList.boxid']");
        SEARCH_PANEL.put("Diagnosis filter", DIAGNOSIS_FILTER);
        SEARCH_PANEL.put("Diagnosis", "#Diagnosis");
        SEARCH_PANEL.put("Scanning Status", "select[ng-model='MRDFileList.scanningstatus']");
        SEARCH_PANEL.put("Search", READY);
        SEARCH_PANEL.put("Add", "[ng-click='AddMRDFile()']");

        LIST_BUTTONS.put("MRD Non Allocated List", "[ng-click='nonallocatedlist()']");
        LIST_BUTTONS.put("File Movement History", "[ng-click='openFileMovementHistory()']");
        LIST_BUTTONS.put("Investigation View", "[ng-click='ViewInvestigation()']");
        LIST_BUTTONS.put("Pending File Return List", "[ng-click='fnOpenPendingFileReturnList()']");
    }

    public MRD(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Ancillary Services &gt; MRD &gt; MRD through the sidebar (in-app hash change; a reload logs nHIS out). */
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        dismissModals();
        expandMenuEntry(null, "^\\s*Ancillary Services\\s*$");
        // "MRD" is both the sub-menu (#li1MRD) and the screen (#li2MRD): expand the sub-menu by id, then click the screen link
        Object clicked = page.evaluate("async () => { const vis = e => e && e.offsetParent !== null; const wait = ms => new Promise(r => setTimeout(r, ms));"
                + " let link = document.querySelector(\"#li2MRD a[href='#/MRDFileList']\") || document.querySelector(\"a[href='#/MRDFileList']\");"
                + " if (!vis(link)) { const mrd = document.querySelector('#li1MRD > a'); if (mrd) { mrd.click(); await wait(1200); }"
                + "   link = document.querySelector(\"#li2MRD a[href='#/MRDFileList']\") || document.querySelector(\"a[href='#/MRDFileList']\"); }"
                + " if (!link) return false; link.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("MRD.open: Ancillary Services > MRD > MRD link not found");
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
            System.out.println("MRD: '" + css + "' did not appear within 90 s");
            return false;
        }
        waitForAngular(1000);
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

    public boolean isDisabled(String css) {
        Object r = page.evaluate("(s) => { const e = document.querySelector(s); return !!e && e.disabled; }", css);
        return Boolean.TRUE.equals(r);
    }

    public String regTypeChecked() {
        Object r = page.evaluate("() => { const c = [...document.querySelectorAll(\"input[type='radio'][ng-model='MRDFileList.opd_ipd']\")].find(x => x.checked);"
                + " return c ? c.value : ''; }");
        return r == null ? "" : r.toString();
    }

    /** How many visible labels read exactly {@code text}. */
    public int labelCount(String text) {
        Object r = page.evaluate("(t) => [...document.querySelectorAll('[ui-view] label')].filter(l => l.offsetParent !== null"
                + " && l.textContent.replace(/\\s+/g, ' ').trim() === t).length", text);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Options of a select (placeholder left out). */
    @SuppressWarnings("unchecked")
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return [];"
                + " return [...s.options].map(o => o.text.replace(/\\s+/g, ' ').trim()).filter(t => t && !/^-*\\s*select\\s*-*$/i.test(t)); }", ngModel);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Header labels of the file grid's body container (the selection column is not included). */
    @SuppressWarnings("unchecked")
    public List<String> gridHeaders() {
        Object r = page.evaluate("() => { const g = [...document.querySelectorAll('[ui-view] .ui-grid')].find(x => x.offsetParent !== null); if (!g) return null;"
                + " return [...g.querySelectorAll('.ui-grid-render-container-body .ui-grid-header-cell')].map(h => h.innerText.replace(/\\s+/g, ' ').trim()).filter(x => x); }");
        return r instanceof List ? (List<String>) r : null;
    }

    /** The grid's data (the screen's model), each file as field -&gt; text: mrno, patientname, fileno, filetype, opd_ipd, scanningstatus, diagnosis, rackid, rowid, boxid. */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> files() {
        Object r = page.evaluate("() => { const e = document.querySelector(\"[ng-model='MRDFileList.mrno']\"); let s = e && angular.element(e).scope();"
                + " while (s && !s.fngetMRDFileGrid) s = s.$parent; if (!s || !s.gridOptions) return [];"
                + " return (s.gridOptions.data || []).map(x => { const o = {}; ['mrno','patientname','fileno','filetype','opd_ipd','scanningstatus','diagnosis','rackid','rowid','boxid','visitadmid']"
                + "   .forEach(k => o[k] = x[k] == null ? '' : String(x[k]).trim()); return o; }); }");
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, String>) o));
        return out;
    }

    /** The grid pager's count text, e.g. "1 - 25 of 499 items". */
    public String pagerText() {
        Object r = page.evaluate("() => { const g = [...document.querySelectorAll('[ui-view] .ui-grid')].find(x => x.offsetParent !== null);"
                + " const c = g && g.querySelector('.ui-grid-pager-count'); return c ? c.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Rows currently drawn in the grid body. */
    public int renderedRows() {
        Object r = page.evaluate("() => { const g = [...document.querySelectorAll('[ui-view] .ui-grid')].find(x => x.offsetParent !== null);"
                + " return g ? [...g.querySelectorAll('.ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')].filter(x => x.innerText.trim()).length : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    public String currentPage() { String v = inputValue("grid.options.paginationCurrentPage"); return v == null ? "" : v.trim(); }

    /** The patient banner ("Name : ... MRN : ..."), "" when none is shown. */
    public String patientBanner() {
        Object r = page.evaluate("() => { const v = document.querySelector('[ui-view]') || document.body;"
                + " const b = [...v.querySelectorAll('div,table,section')].filter(e => e.offsetParent !== null && /MRN\\s*:/.test(e.innerText || '')"
                + "   && (e.innerText || '').length < 700).pop(); return b ? b.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
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

    public void setRegType(String value) {
        page.locator("input[type='radio'][ng-model='MRDFileList.opd_ipd'][value='" + value + "']").first().click();
        waitForAngular(400);
    }

    public void setDateFilter(boolean on) {
        if (isChecked(DATE_FILTER) == on) return;
        page.locator(DATE_FILTER).first().click();
        waitForAngular(500);
    }

    public void setDiagnosisFilter(boolean on) {
        if (isChecked(DIAGNOSIS_FILTER) == on) return;
        page.locator(DIAGNOSIS_FILTER).first().click();
        waitForAngular(400);
    }

    /** Search; waits for the file list. Returns its HTTP status, 0 = no request (the screen's validation stopped it). */
    public int search() {
        clearCaptured();
        Response r = clickAndWaitForApi("fngetMRDFileGrid(true)", FETCH_API, 25000);
        waitForAngular(1500);
        return r == null ? 0 : r.status();
    }

    /** Find Patient; returns the patient lookup's HTTP status, 0 = no request (validation stopped it). */
    public int findPatient() {
        clearCaptured();
        Response r = clickAndWaitForApi("SearchPatientByMRNo();", "FetchPatientData", 15000);
        waitForAngular(2500);
        return r == null ? 0 : r.status();
    }

    // ---- read-only actions: grid ------------------------------------------------------------

    private Locator grid() { return page.locator("[ui-view] .ui-grid").first(); }

    /** Select the first grid row with its row-selection button (client-side only). */
    public void selectFirstRow() {
        grid().locator(".ui-grid-render-container-left .ui-grid-row .ui-grid-selection-row-header-buttons").first().click();
        waitForAngular(600);
    }

    public void clickFirstRowButton(String title) {
        grid().locator(".ui-grid-render-container-body .ui-grid-row button[title='" + title + "']").first().click();
        waitForAngular(800);
    }

    public void setPageSize(String size) {
        page.evaluate("(v) => { const g = [...document.querySelectorAll('[ui-view] .ui-grid')].find(x => x.offsetParent !== null);"
                + " const s = g && g.querySelector('.ui-grid-pager-panel select'); if (!s) return; const o = [...s.options].find(x => x.text.trim() === v);"
                + " if (o) { s.value = o.value; s.dispatchEvent(new Event('change', {bubbles: true})); } }", size);
        waitForAngular(1200);
    }

    public void clickPager(String cssClass) {
        grid().locator(".ui-grid-pager-panel button." + cssClass).first().click();
        waitForAngular(1000);
    }

    // ---- read-only actions: pop-ups ----------------------------------------------------------

    /** Open a list pop-up with its button; true when a modal is showing. */
    public boolean openPopup(String ngClick) {
        clearCaptured();
        if (!clickNg(ngClick)) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m => getComputedStyle(m).display === 'block' && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        waitForAngular(2500);
        return true;
    }

    /** Title, headers and row count of the open modal (by CSS, or the visible one when null). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> modalView(String modalCss) {
        Object r = page.evaluate("(sel) => { const m = sel ? document.querySelector(sel) : [...document.querySelectorAll('.modal')].find(x => getComputedStyle(x).display === 'block');"
                + " if (!m) return {}; const T = t => (t || '').replace(/\\s+/g, ' ').trim();"
                + " return { title: T((m.querySelector('.modal-title, .modal-header h4, .modal-header') || {}).innerText).replace(/^×\\s*/, ''),"
                + "   headers: [...m.querySelectorAll('thead th')].map(h => T(h.innerText)).filter(x => x),"
                + "   rows: [...m.querySelectorAll('tbody tr')].filter(r => T(r.innerText) && r.offsetParent !== null).length,"
                + "   firstRow: T((([...m.querySelectorAll('tbody tr')].find(r => T(r.innerText) && r.offsetParent !== null)) || {}).innerText).slice(0, 200),"
                + "   pager: T((m.querySelector('.ui-grid-pager-count, [class*=pager-count]') || {}).innerText) }; }", modalCss);
        return r instanceof Map ? new LinkedHashMap<>((Map<String, Object>) r) : new LinkedHashMap<>();
    }

    /** Text of every visible body row of a modal. */
    @SuppressWarnings("unchecked")
    public List<String> modalRows(String modalCss) {
        Object r = page.evaluate("(sel) => { const m = document.querySelector(sel); if (!m) return [];"
                + " return [...m.querySelectorAll('tbody tr')].filter(r => r.offsetParent !== null && r.innerText.trim()).map(r => r.innerText.replace(/\\s+/g, ' ').trim()); }", modalCss);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Close the open modal with its × (never Print / Show); falls back to the app's modal stack. True when none is left. */
    public boolean closePopup() {
        page.evaluate("() => { const m = [...document.querySelectorAll('.modal')].find(x => getComputedStyle(x).display === 'block');"
                + " const x = m && [...m.querySelectorAll('button.close')].find(b => b.offsetParent !== null); if (x) x.click(); }");
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m => getComputedStyle(m).display === 'block' && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception e) {
            page.evaluate("() => { document.querySelectorAll('.modal.in').forEach(m => { if (window.jQuery) window.jQuery(m).modal('hide'); }); }");
            dismissModals();
        }
        waitForAngular(500);
        return openModals() == 0;
    }

    /** File Movement History: type an MRN into the pop-up's own MRN box and use its Find Patient. */
    public void fileMovementFilterByMrn(String mrn) {
        setNgInput("fileMovementMrn", mrn);
        clearCaptured();
        page.locator(FILE_MOVEMENT_MODAL + " [ng-click='fnFileMovementFindPatient();']").first().click();
        waitForAngular(4000);
    }

    /** Pending File Return List: type an MRN into the pop-up's MRN box and press Enter (its own key handler filters). */
    public void pendingReturnFilterByMrn(String mrn) {
        Locator box = page.locator(PENDING_RETURN_MODAL + " [ng-model='pendingReturn.mrno']").first();
        box.click();
        box.fill(mrn);
        box.press("Enter");
        waitForAngular(3000);
    }

    /** Patient Search pop-up: search by MRN and press the first result's Select (client-side: fills the screen's MRN box). */
    public boolean pickPatientInSearchPopup(String mrn) {
        if (!openPopup("openPopupScreen()")) return false;
        Locator modal = page.locator(".modal.in").first();
        Locator box = modal.locator("[ng-model='PatientData.MRNo']").first();
        box.fill(mrn);
        box.dispatchEvent("change");
        modal.locator("[ng-click='SearchPatient(0);']").first().click();
        try {
            page.waitForFunction("() => { const m = [...document.querySelectorAll('.modal')].find(x => getComputedStyle(x).display === 'block');"
                    + " return !!m && !!m.querySelector(\"tbody tr button[title='Select']\"); }", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { closePopup(); return false; }
        modal.locator("tbody tr button[title='Select']").first().click();
        waitForAngular(2000);
        return true;
    }

    /** Current route, e.g. "#/NursingEMR". */
    public String route() { return String.valueOf(page.evaluate("() => location.hash")); }
}
