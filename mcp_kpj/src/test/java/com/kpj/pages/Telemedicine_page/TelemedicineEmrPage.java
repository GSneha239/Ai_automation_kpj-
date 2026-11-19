package com.kpj.pages.Telemedicine_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Telemedicine &gt; <b>EMR</b> ({@code #/EMRTele}) — Page Object. READ-ONLY: nothing here clicks Save, OK, Send or any
 * other control that writes data.
 *
 * <p>Confirmed live on devhis (19/11/2026, user Employee 229):</p>
 * <ul>
 *   <li>The route is only reachable through the menu: opening {@code #/EMRTele} directly bounces back to
 *       {@code #/LandingPage}. {@link #openViaMenu(String)} therefore always goes Telemedicine &rarr; EMR.</li>
 *   <li>The header's icon buttons (Search, Find Patient, Video Meeting) have no text, and Bootstrap tooltips strip their
 *       {@code title} attribute on first hover, so they are located by their stable AngularJS {@code ng-click} /
 *       {@code data-target} attributes. Text buttons (ICU Management, OT Room, Bills, Print, ...) are located by role
 *       and exact name — each name is unique on the visible screen.</li>
 *   <li>Messages are raised through the global {@code JAlert(msg, type)}; {@link #armAlerts()} records them.</li>
 *   <li>"Loaded" is judged on AngularJS's own {@code $http.pendingRequests}, not on fixed sleeps
 *       ({@link #waitForHttpIdle(int)}).</li>
 * </ul>
 */
public class TelemedicineEmrPage extends BasePage {

    public static final String ROUTE = "#/EMRTele";

    public TelemedicineEmrPage(Page page) { super(page); }

    // ---- locators -----------------------------------------------------------------------------------------------

    // The screen keeps further copies of some controls inside hidden dialogs (devhis exposed a second "MRN" box), and a
    // locator that matches two elements makes Playwright throw a strict-mode error. Each header control is therefore
    // the VISIBLE match only.
    public Locator mrnInput()            { return page.locator("input[ng-model='EMR.mrno']:visible").first(); }
    public Locator searchButton()        { return page.locator("button[ng-click='SearchPatientByMRNo()']:visible").first(); }
    public Locator findPatientButton()   { return page.locator("button[ng-click='OpenPatientSearchPopupScreen()']:visible").first(); }
    public Locator videoMeetingButton()  { return page.locator("button[data-target='.WebCamPopup']:visible").first(); }
    public Locator emrMenuLink()         { return page.locator("a[href='" + ROUTE + "']:visible").first(); }

    /** A footer button by its exact visible text (ICU Management, OT Room, Email To Refer Doctor, Bills, Discharge Summary, Print, Back). */
    public Locator footerButton(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator patientSearchPopup()  { return page.locator(".modal.in:has(input[ng-model='PatientData.FirstName'])"); }
    public Locator videoEmailPopup()     { return page.locator(".modal.in:has(input[ng-model='VideoChat.EmailID'])"); }
    public Locator viewCaseModal()       { return page.locator("#EMRnewCase"); }

    public Locator caseRow(String caseNo) {
        return page.locator("main table tbody tr").filter(new Locator.FilterOptions().setHasText(caseNo));
    }

    // ---- navigation & readiness ---------------------------------------------------------------------------------

    /**
     * Open Telemedicine &rarr; EMR through the side menu, on a freshly created EMR controller.
     *
     * <p>The screen is left first, through the app's own Home link, so the EMR controller is built again (clicking the EMR
     * link while already on {@code #/EMRTele} keeps the old screen and its loaded patient), which keeps each check
     * independent. A browser navigation is not used for this: nHIS answers one with its "Reload page - you will be
     * logged out" prompt.</p>
     *
     * @return what the MRN box held when the screen opened ("" normally — a value means the app restored a patient)
     */
    public String openViaMenu(String baseUrl) {
        leaveToHome(baseUrl);
        page.waitForFunction("() => !!window.angular && [...document.querySelectorAll('a')]"
                        + ".some(a => (a.textContent || '').trim() === 'Telemedicine' && a.offsetParent !== null)",
                null, new Page.WaitForFunctionOptions().setTimeout(30000));
        if (!expandMenuEntry(null, "^Telemedicine$")) {
            throw new IllegalStateException("The Telemedicine menu did not open (no visible sub-entries)");
        }
        Locator link = emrMenuLink();
        link.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        link.click();
        page.waitForURL(u -> u.contains(ROUTE), new Page.WaitForURLOptions().setTimeout(20000));
        waitUntilReady();
        armAlerts();
        return mrnInput().inputValue();
    }

    /**
     * Leave the current screen through the side menu's Home link (in-app routing, no page reload). Right after login the
     * app is already on its landing page and nothing is done. Only when no Home link is on screen does it fall back to a
     * browser navigation to the landing page.
     */
    private void leaveToHome(String baseUrl) {
        String url = page.url();
        if (url.contains("#/LandingPage") || url.contains("#/home") || url.contains("#/PatientDashboard")) return;
        Locator home = page.locator("a[href='#/home']:visible").first();
        if (home.count() > 0) {
            home.click();
            page.waitForURL(u -> !u.contains(ROUTE) && !u.contains("#/BillList") && !u.contains("#/OTDetailsList"),
                    new Page.WaitForURLOptions().setTimeout(20000));
            waitForHttpIdle(20000);
            return;
        }
        System.out.println("TelemedicineEmrPage: no Home link on screen (" + url + ") - opening the landing page by URL");
        page.navigate(baseUrl + "/#/LandingPage");
    }

    /** The MRN box is on screen, bound to AngularJS, and the screen's initial requests have finished. */
    public void waitUntilReady() {
        mrnInput().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
        page.waitForFunction("() => { const i = [...document.querySelectorAll(\"input[ng-model='EMR.mrno']\")]"
                        + ".find(e => e.offsetWidth || e.offsetHeight || e.getClientRects().length);"
                        + " return !!(i && window.angular && angular.element(i).controller('ngModel')); }",
                null, new Page.WaitForFunctionOptions().setTimeout(20000));
        waitForHttpIdle(30000);
    }

    /**
     * Wait until AngularJS has had no request in flight for {@code quietMs} (800 ms). The EMR screen fires dozens of
     * overlapping requests after a patient loads; a single "0 pending" sample can fall in a gap between two bursts.
     *
     * @return false when the screen was still busy at the timeout (the caller decides whether that matters)
     */
    public boolean waitForHttpIdle(int timeoutMs) {
        page.evaluate("() => { window.__emrBusyAt = undefined; }");
        try {
            page.waitForFunction("(quietMs) => { let n = 0;"
                            + " try { const root = document.querySelector('[ng-app]') || document.documentElement;"
                            + "       n = angular.element(root).injector().get('$http').pendingRequests.length; } catch (e) { n = 0; }"
                            + " const now = Date.now();"
                            + " if (n > 0 || window.__emrBusyAt === undefined) { window.__emrBusyAt = now; return false; }"
                            + " return now - window.__emrBusyAt >= quietMs; }",
                    800, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(200));
            return true;
        } catch (PlaywrightException e) {
            System.out.println("TelemedicineEmrPage: requests still in flight after " + timeoutMs + " ms");
            return false;
        }
    }

    // ---- messages (JAlert) --------------------------------------------------------------------------------------

    /** Start recording JAlert messages (cleared each call; the wrapper is installed once per document). */
    public void armAlerts() {
        page.evaluate("() => { window.__emrAlerts = [];"
                + " if (!window.__emrAlertWrapped && typeof window.JAlert === 'function') {"
                + "   const original = window.JAlert;"
                + "   window.JAlert = function (m, t) { try { window.__emrAlerts.push(String(m)); } catch (e) {} return original.apply(this, arguments); };"
                + "   window.__emrAlertWrapped = true; } }");
    }

    /** Messages raised since {@link #armAlerts()}, plus any toast text still on screen. */
    public List<String> alerts() {
        Object r = page.evaluate("() => { const out = [...(window.__emrAlerts || [])];"
                + " document.querySelectorAll('#toast-container .toast-message, .toast-message').forEach(t => {"
                + "   const s = (t.innerText || '').trim(); if (s && !out.includes(s)) out.push(s); });"
                + " return out; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Wait for a message matching {@code regex} (case-insensitive). Returns false at the timeout. */
    public boolean waitForAlert(String regex, int timeoutMs) {
        try {
            page.waitForFunction("(re) => { const rx = new RegExp(re, 'i');"
                            + " if ((window.__emrAlerts || []).some(m => rx.test(m))) return true;"
                            + " return [...document.querySelectorAll('#toast-container .toast-message, .toast-message')].some(t => rx.test(t.innerText || '')); }",
                    regex, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(150));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    // ---- patient search -----------------------------------------------------------------------------------------

    /** Type the MRN (real keystrokes, so ng-model updates) without searching. */
    public void typeMrn(String mrn) {
        mrnInput().fill(mrn);
    }

    /** Click Search and wait for the screen to answer (patient shown, or a message raised). */
    public void clickSearch() {
        armAlerts();
        searchButton().click();
        waitForPatientOrMessage(30000);
        waitForHttpIdle(60000);
    }

    /** Wait until the header shows a patient name or a JAlert message was raised. */
    public boolean waitForPatientOrMessage(int timeoutMs) {
        try {
            page.waitForFunction("() => { if ((window.__emrAlerts || []).length) return true;"
                            + " const main = document.querySelector('main') || document.body;"
                            + " const top = (main.innerText || '').split(/\\n\\s*Visits\\s*\\n/)[0];"
                            + " const m = top.match(/(^|\\n)\\s*Name\\s*:[ \\t]*([^\\n]*)/i); return !!(m && m[2].trim()); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(200));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** Header fields: name, dob, age, gender, nric, passport ("" when blank). */
    public Map<String, String> patientHeader() {
        Object r = page.evaluate("() => { const main = document.querySelector('main') || document.body;"
                + " const top = (main.innerText || '').split(/\\n\\s*Visits\\s*\\n/)[0];"
                + " const get = l => { const m = top.match(new RegExp('(^|\\\\n)\\\\s*' + l + '\\\\s*:[ \\\\t]*([^\\\\n]*)', 'i')); return m ? m[2].trim() : ''; };"
                + " return { name: get('Name'), dob: get('Date of Birth'), age: get('Age'), gender: get('Gender'),"
                + "          nric: get('NRIC'), passport: get('Passport No\\\\.?') }; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) r).entrySet()) out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        return out;
    }

    /** Rows (flattened text) of the visible table whose header contains a column named {@code headerCell}. */
    public List<String> tableRows(String headerCell) {
        Object r = page.evaluate("(hdr) => { const t = [...document.querySelectorAll('main table')]"
                + "   .find(t => [...t.querySelectorAll('thead th')].some(h => (h.innerText || '').trim() === hdr));"
                + " if (!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(r => (r.innerText || '').replace(/\\s+/g, ' ').trim()).filter(Boolean); }",
                headerCell);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public List<String> visitRows() { return tableRows("Visit No"); }
    public List<String> caseRows()  { return tableRows("Encounter No."); }

    // ---- Find Patient popup -------------------------------------------------------------------------------------

    /**
     * Find Patient &rarr; search by name &rarr; select the row holding {@code mrn}. Pages through at most
     * {@code maxPages} result pages (10 rows each). Never presses anything but Search, &gt;&gt; and the row's Select.
     *
     * @return "" on success, otherwise why it could not select the patient (the popup is closed again in that case)
     */
    public String selectPatientFromPopup(String name, String mrn, int maxPages) {
        findPatientButton().click();
        Locator popup = patientSearchPopup();
        popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        popup.locator("input[ng-model='PatientData.FirstName']").fill(name);
        armAlerts();
        Response searched = page.waitForResponse(res -> res.url().contains("FetchPatientData"),
                new Page.WaitForResponseOptions().setTimeout(30000),
                () -> popup.locator("button[ng-click^='SearchPatient(0)']").click());
        if (searched.status() >= 400) return "the patient search answered HTTP " + searched.status();
        waitForHttpIdle(20000);

        for (int p = 1; p <= maxPages; p++) {
            Locator row = popup.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(mrn));
            if (row.count() > 0) {
                row.first().locator("button[ng-click^='SetSearchPatient']").click();
                popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
                waitForPatientOrMessage(30000);
                waitForHttpIdle(60000);
                return "";
            }
            if (p == maxPages || popup.locator("tbody tr").count() < 10) break;
            try {
                page.waitForResponse(res -> res.url().contains("FetchPatientData"),
                        new Page.WaitForResponseOptions().setTimeout(15000),
                        () -> popup.locator("button[ng-click^='NextBtn']").click());
                waitForHttpIdle(15000);
            } catch (PlaywrightException e) {
                break;
            }
        }
        closePatientSearchPopup();
        return "MRN " + mrn + " was not in the first " + maxPages + " page(s) of results for name '" + name + "'";
    }

    public void closePatientSearchPopup() {
        Locator popup = patientSearchPopup();
        if (popup.count() == 0) return;
        popup.locator("button[ng-click^='CloseModel']").filter(new Locator.FilterOptions().setHasText("Close")).first().click();
        popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
    }

    // ---- clinical sections --------------------------------------------------------------------------------------

    /**
     * Every clinical section tile ({@code ng-show="!XxxSectionhide"}): name, its visibility flag, the extra
     * {@code ng-hide} expression and its value, and whether it is on screen. Also returns the two screen-wide flags
     * {@code TeleEMRHide} / {@code OPDEMRHide} and whether the Allergies / Medical-Family History blocks are present.
     * Read only — no flag is changed.
     */
    public Map<String, Object> clinicalSections() {
        Object r = page.evaluate("() => { const vis = e => !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length);"
                + " const tiles = [...document.querySelectorAll('main [ng-show*=\"sectionhide\"]')];"
                + " const sections = tiles.map(el => { const flag = (el.getAttribute('ng-show') || '').replace('!', '').trim();"
                + "   const hideExpr = (el.getAttribute('ng-hide') || '').trim(); let flagVal = null, hideVal = null;"
                + "   try { const s = angular.element(el).scope(); flagVal = s.$eval(flag); if (hideExpr) hideVal = s.$eval(hideExpr); } catch (e) {}"
                + "   return { name: flag.replace(/sectionhide$/i, ''), flag: flag, flagValue: String(flagVal), hideExpr: hideExpr,"
                + "            hideValue: hideExpr ? String(hideVal) : '', visible: vis(el) }; });"
                + " let tele = null, opd = null;"
                + " try { const s = angular.element(tiles[0] || document.querySelector(\"input[ng-model='EMR.mrno']\")).scope();"
                + "       tele = s.$eval('TeleEMRHide'); opd = s.$eval('OPDEMRHide'); } catch (e) {}"
                + " const block = re => { const els = [...document.querySelectorAll('main *')].filter(e => e.children.length === 0 && re.test((e.textContent || '').trim()));"
                + "   return { present: els.length > 0, visible: els.some(vis) }; };"
                + " return { sections: sections, teleEmrHide: String(tele), opdEmrHide: String(opd),"
                + "          allergies: block(/^Allergies$/i), history: block(/^Medical\\/Family History$/i) }; }");
        if (r instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) r;
            return m;
        }
        return Collections.emptyMap();
    }

    // ---- View Case ----------------------------------------------------------------------------------------------

    /** Click View on the case row and wait for the View Case dialog. */
    public void openViewCase(String caseNo) {
        caseRow(caseNo).first().locator("a[ng-click^='SelectedCaseDataView']").click();
        page.locator("#EMRnewCase.in").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        waitForHttpIdle(15000);
    }

    /**
     * Values in the View Case dialog (inputs by ng-model, selects by their selected text) and a label layout check:
     * a label counts as clipped when it starts left of / ends right of the dialog, or its text overflows its box.
     */
    public Map<String, Object> viewCaseDetails() {
        Object r = page.evaluate("() => { const m = document.querySelector('#EMRnewCase'); if (!m) return null;"
                + " const vis = e => !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length);"
                + " const inputs = {}; m.querySelectorAll('input[ng-model]').forEach(i => { inputs[i.getAttribute('ng-model')] = i.value; });"
                + " const selects = [...m.querySelectorAll('select')].map(s => { const o = s.options[s.selectedIndex];"
                + "   return { model: s.getAttribute('ng-model') || '', text: o ? (o.text || '').trim() : '' }; });"
                + " const box = (m.querySelector('.modal-content') || m).getBoundingClientRect();"
                + " const labels = [...m.querySelectorAll('label')].filter(vis).map(l => { const r = l.getBoundingClientRect();"
                + "   return { text: (l.innerText || '').trim(), clipped: r.left < box.left - 0.5 || r.right > box.right + 0.5 || l.scrollWidth > l.clientWidth + 1 }; });"
                + " return { inputs: inputs, selects: selects, labels: labels }; }");
        if (r instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) r;
            return m;
        }
        return Collections.emptyMap();
    }

    /** Close View Case with its Close button (never Save). */
    public void closeViewCase() {
        viewCaseModal().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close").setExact(true)).click();
        page.locator("#EMRnewCase.in").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
    }

    // ---- Video Meeting ------------------------------------------------------------------------------------------

    /** Close the Video Meeting e-mail dialog with Cancel (never OK, which sends the invitation). */
    public void cancelVideoEmailPopup() {
        Locator popup = videoEmailPopup();
        popup.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Cancel").setExact(true)).click();
        popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
    }

    /** Title of the open dialog that holds {@code locator} ("" when none). */
    public String dialogTitle(Locator dialog) {
        Locator t = dialog.locator(".modal-title, .modal-header h4, .modal-header h3").first();
        return t.count() > 0 ? t.innerText().trim() : "";
    }

    /** Number of open (".in") bootstrap dialogs. */
    public int openDialogCount() {
        Object n = page.evaluate("() => [...document.querySelectorAll('.modal.in')].filter(m => m.offsetWidth || m.offsetHeight || m.getClientRects().length).length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }
}
