package com.kpj.pages.Billing_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>Charge Conversion</b> — Page Object for READ-ONLY use.
 *
 * <p>Extends {@link ReadOnlyScreen}, so the browser-level write guard, the API / console monitors and the JAlert capture
 * apply. Nothing in this class clicks Convert ({@code FnChargeConvert();}) or changes a service's sponsor; the Convert
 * Sponsor dropdown is only read.</p>
 *
 * <p>Locators confirmed in the live DOM (devhis v2026.11.26.546, 26/11/2026):</p>
 * <ul>
 *   <li>Sidebar: {@code #li0Billing} &gt; {@code #li1ChargeConversion > a[href='#/ChargeConversion']}; header
 *       "CHARGE CONVERSION", browser tab "Charge Conversion".</li>
 *   <li>Patient panel: MRN {@code #textinput} ({@code ChargeConversion.MRNo}), Search {@code #btnSearch}
 *       ({@code SearchPatientByMRNo();}), Search Patient {@code #btnSearchPatient} ({@code OpenPopupScreen()}), radios
 *       {@code ChargeConversion.opd_ipd} (0 OPD, 1 IPD), OPD/IPD No. {@code ChargeConversion.OPDNo}, Payor
 *       {@code ChargeConversion.receivableid}, Convert Sponsor {@code ChargeConversion.convertreceivableid}.</li>
 *   <li>Services grid: Select All {@code ChargeConversion.SelectAll} ({@code FnSeleteAll()}), row checkbox
 *       {@code Serv.IsSelected}; Unit Purchase Price / Self Amt. / Company Amt. are disabled inputs in the row.</li>
 *   <li>Buttons: Convert {@code FnChargeConvert();} (NEVER clicked), Cancel {@code CancelForm();} (clears the form only).</li>
 *   <li>Read calls (all pass the guard): FetchPatientData GetPatientInfo, GetSponserByVisit, GetReceivableByOPDNo,
 *       api/ChargeConversion/Fetch GetServices.</li>
 * </ul>
 */
public class ChargeConversion extends ReadOnlyScreen {

    public static final String ROUTE = "#/ChargeConversion";
    public static final String READY = "#btnSearch";
    public static final String MENU_LINK = "#li0Billing a[href='#/ChargeConversion']";
    public static final String PATIENT_API = "FetchPatientData";
    public static final String SERVICES_API = "api/ChargeConversion/Fetch";

    public static final String MRN = "ChargeConversion.MRNo";
    public static final String OPD_NO = "ChargeConversion.OPDNo";
    public static final String PAYOR = "ChargeConversion.receivableid";
    public static final String CONVERT_SPONSOR = "ChargeConversion.convertreceivableid";
    public static final String SELECT_ALL = "input[ng-model='ChargeConversion.SelectAll']";
    public static final String SERVICE_CHECKBOX = "input[ng-model='Serv.IsSelected']";

    /** Patient panel, label -&gt; CSS. */
    public static final Map<String, String> PATIENT_PANEL = new LinkedHashMap<>();
    /** Footer buttons, label -&gt; CSS. Convert is only checked for presence - never clicked. */
    public static final Map<String, String> BUTTONS = new LinkedHashMap<>();
    static {
        PATIENT_PANEL.put("MRN", "[ng-model='ChargeConversion.MRNo']");
        PATIENT_PANEL.put("Search", READY);
        PATIENT_PANEL.put("Search Patient", "#btnSearchPatient");
        PATIENT_PANEL.put("OPD / IPD", "input[type='radio'][ng-model='ChargeConversion.opd_ipd']");
        PATIENT_PANEL.put("OPD/IPD No.", "[ng-model='ChargeConversion.OPDNo']");
        PATIENT_PANEL.put("Payor", "select[ng-model='ChargeConversion.receivableid']");
        PATIENT_PANEL.put("Convert Sponsor", "select[ng-model='ChargeConversion.convertreceivableid']");

        BUTTONS.put("Select All", SELECT_ALL);
        BUTTONS.put("Convert", "[ng-click='FnChargeConvert();']");
        BUTTONS.put("Cancel", "[ng-click='CancelForm();']");
    }

    public ChargeConversion(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Billing &gt; Charge Conversion through the sidebar (in-app hash change; a reload logs nHIS out). */
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        expandMenuEntry(null, "^\\s*Billing\\s*$");
        Object clicked = page.evaluate("(s) => { const a = [...document.querySelectorAll(s)].find(x => x.offsetParent !== null) || document.querySelector(s);"
                + " if (!a) return false; a.click(); return true; }", MENU_LINK);
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("ChargeConversion.open: Billing > Charge Conversion link not found");
            return false;
        }
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", READY,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("ChargeConversion.open: Search button did not appear within 90 s");
        }
        waitForAngular(800);
        hookAlerts();
        return isOnRoute(ROUTE) && isVisible(READY);
    }

    /** Leave through Home and open again, so each scenario starts with a fresh controller. */
    public boolean reopen() { goHome(); return open(); }

    // ---- reading the screen ---------------------------------------------------------------

    public Map<String, Boolean> presence(Map<String, String> controls) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : controls.entrySet()) out.put(e.getKey(), isVisible(e.getValue()));
        return out;
    }

    /** Value of the checked OPD / IPD radio ("0" OPD, "1" IPD), "" when none. */
    public String visitTypeChecked() {
        Object r = page.evaluate("() => { const c = [...document.querySelectorAll(\"input[type='radio'][ng-model='ChargeConversion.opd_ipd']\")].find(x => x.checked);"
                + " return c ? c.value : ''; }");
        return r == null ? "" : r.toString();
    }

    public String opdNo() { String v = inputValue(OPD_NO); return v == null ? "" : v.trim(); }
    public String payorSelected() { return selectedText(PAYOR); }

    /** Convert Sponsor options as shown (blank placeholder kept as ""). */
    @SuppressWarnings("unchecked")
    public List<String> convertSponsorOptions() {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return null;"
                + " return [...s.options].map(o => o.text.replace(/\\s+/g, ' ').trim()); }", CONVERT_SPONSOR);
        return r instanceof List ? (List<String>) r : null;
    }

    /** Services grid headers; the Select All column is reported as "Select All checkbox". */
    @SuppressWarnings("unchecked")
    public List<String> serviceGridHeaders() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Service Code/.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return [];"
                + " return [...t.querySelectorAll('thead th')].map(h => { const txt = h.innerText.replace(/\\s+/g, ' ').trim();"
                + "   return txt || (h.querySelector('input[type=checkbox]') ? 'Select All checkbox' : ''); }).filter(x => x); }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /**
     * Service rows, each header -&gt; cell value. A cell holding an input (Unit Purchase Price, Self Amt., Company Amt.) is
     * read from the input's value; "__disabled" lists the headers whose input is disabled.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> serviceRows() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Service Code/.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " return [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.querySelector(\"input[ng-model='Serv.IsSelected']\"))"
                + "   .map(tr => { const o = {}; const dis = [];"
                + "     hs.forEach((h, i) => { const c = tr.cells[i]; if (!h || !c) return;"
                + "       const inp = c.querySelector('input:not([type=checkbox]),textarea');"
                + "       o[h] = (inp ? inp.value : c.innerText).replace(/\\s+/g, ' ').trim(); if (inp && inp.disabled) dis.push(h); });"
                + "     o['__disabled'] = dis.join('|'); return o; }); }");
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, String>) o));
        return out;
    }

    /** Ticked state of every service row, in order. */
    @SuppressWarnings("unchecked")
    public List<Boolean> serviceTicks() {
        Object r = page.evaluate("(s) => [...document.querySelectorAll(s)].filter(x => x.offsetParent !== null).map(x => x.checked)", SERVICE_CHECKBOX);
        return r instanceof List ? (List<Boolean>) r : new ArrayList<>();
    }

    public boolean isSelectAllTicked() {
        try { return page.locator(SELECT_ALL).first().isChecked(); } catch (Exception e) { return false; }
    }

    /** The patient banner text ("Name : ... MRN : ..."), "" when no patient is shown. */
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

    // ---- read-only actions ------------------------------------------------------------------

    public void setMrn(String mrn) { setNgInput(MRN, mrn); }

    /**
     * Search by MRN; waits for the patient lookup, then for the services fetch when the patient has a visit. Returns the
     * lookup's HTTP status, 0 = no lookup was sent (validation stopped it).
     */
    public int searchByMrn() {
        clearCaptured();
        int logBefore = apiLog.size();
        Response r = clickAndWaitForApi("SearchPatientByMRNo();", PATIENT_API, 20000);
        if (r != null) {
            // The services fetch follows the visit / sponsor lookups; a patient without a visit never sends it.
            for (int waited = 0; waited < 8000 && !servicesFetchedSince(logBefore); waited += 250) page.waitForTimeout(250);
        }
        waitForAngular(2500);
        return r == null ? 0 : r.status();
    }

    private boolean servicesFetchedSince(int logIndex) {
        synchronized (apiLog) {
            for (int i = logIndex; i < apiLog.size(); i++) if (apiLog.get(i).contains(SERVICES_API)) return true;
        }
        return false;
    }

    /** Click Search where validation is expected to stop it (no wait for an API). */
    public void clickSearchOnly() { clearCaptured(); clickNg("SearchPatientByMRNo();"); waitForAngular(1500); }

    /** Tick / untick Select All with a real click (client-side selection only). */
    public void setSelectAll(boolean ticked) {
        if (isSelectAllTicked() == ticked) return;
        page.locator(SELECT_ALL).first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(15000));
        waitForAngular(800);
    }
}
