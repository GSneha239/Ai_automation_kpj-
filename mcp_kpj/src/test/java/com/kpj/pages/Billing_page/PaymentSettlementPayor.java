package com.kpj.pages.Billing_page;

import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Payment Settlement &gt; <b>Payor</b> — Page Object (read-only use).
 *
 * <p>The Billing menu entry opens the SAME screen as Inventory &gt; POS &gt; Payment Settlement &gt; Payor
 * (route {@code #/CompanyPaymentSettlement}, header "PAYMENT SETTLEMENT - COMPANY", tab title "Payor"), so this class
 * reuses {@link com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor} - its locators, search, the read-only
 * write guard and the alert capture - and only changes how the screen is reached, plus a few row-level read helpers.</p>
 *
 * <p>Locators confirmed in the live DOM (devhis v2026.11.26.546, 26/11/2026):</p>
 * <ul>
 *   <li>Sidebar: {@code #li0Billing} &gt; {@code #li1PaymentSettlement} &gt; {@code #li2Payor > a[href="#/CompanyPaymentSettlement"]}.</li>
 *   <li>Search panel: {@code [ng-model='IsChecked']}, {@code #FromDate}, {@code #ToDate},
 *       {@code input[ng-model='PatientData.PatientType']} (0 OPD / 1 IPD / 2 All), {@code #mrn},
 *       {@code [ng-click='FindPatient();']}, {@code [ng-click='OpenPopupScreen()']}, Payor select2 {@code #cmpny} / {@code #s2id_cmpny},
 *       {@code select[ng-model='PatientData.billtype']}, {@code [ng-click='fnFillBillDetails();']},
 *       {@code [ng-click='PrintCompanySettlementReport()']}.</li>
 *   <li>Bill table = second visible table; row {@code ng-repeat="bill in PharmacyServiceBillDetails ..."}, row checkbox
 *       {@code input[ng-model='bill.IsSelected']} (ng-click {@code SelectPharmacyBillDetails(bill,$index)} - client side only),
 *       per-row amount {@code input[ng-model='bill.totalsettledamount']}; footer total
 *       {@code [ng-model='PharmacyServiceBillDetails.TotalSettledAmount1']}.</li>
 * </ul>
 *
 * <p>Nothing here clicks Settle Bill ({@code fnIUDBill()}), Demand Letter, or any save/confirm control.</p>
 */
public class PaymentSettlementPayor extends com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor {

    /** The Payor link inside the Billing menu only (Inventory &gt; POS has a link with the same href). */
    public static final String BILLING_PAYOR_LINK = "#li0Billing a[href='#/CompanyPaymentSettlement']";
    public static final String BILL_CHECKBOX = "input[ng-model='bill.IsSelected']";
    public static final String BILL_ROW_AMOUNT = "input[ng-model='bill.totalsettledamount']";

    /** Search-panel controls, label -&gt; CSS (all confirmed in the live DOM). */
    public static final Map<String, String> SEARCH_PANEL = new LinkedHashMap<>();
    static {
        SEARCH_PANEL.put("From Date checkbox", "[ng-model='IsChecked']");
        SEARCH_PANEL.put("From Date", "#FromDate");
        SEARCH_PANEL.put("To Date", "#ToDate");
        SEARCH_PANEL.put("Reg. Type", "input[type='radio'][ng-model='PatientData.PatientType']");
        SEARCH_PANEL.put("MRN", "#mrn");
        SEARCH_PANEL.put("Find Patient", "[ng-click='FindPatient();']");
        SEARCH_PANEL.put("Search Patient", "[ng-click='OpenPopupScreen()']");
        SEARCH_PANEL.put("Payor", "#s2id_cmpny");
        SEARCH_PANEL.put("Bill Type", "select[ng-model='PatientData.billtype']");
        SEARCH_PANEL.put("Search button", "[ng-click='fnFillBillDetails();']");
        SEARCH_PANEL.put("Demand Letter button", "[ng-click='PrintCompanySettlementReport()']");
    }

    public PaymentSettlementPayor(Page page) { super(page); }

    /**
     * Billing &gt; Payment Settlement &gt; Payor through the sidebar (an in-app hash change - a page reload logs nHIS out).
     * Waits up to 90 s for the Search button, as the other Payment Settlement page object does.
     */
    @Override
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        expandMenuEntry(null, "^\\s*Billing\\s*$");
        expandMenuEntry("^\\s*Billing\\s*$", "^\\s*Payment Settlement\\s*$");
        Object clicked = page.evaluate("(s) => { const a = [...document.querySelectorAll(s)].find(x => x.offsetParent !== null) || document.querySelector(s);"
                + " if (!a) return false; a.click(); return true; }", BILLING_PAYOR_LINK);
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("Billing PaymentSettlementPayor.open: Billing > Payment Settlement > Payor link not found");
            return false;
        }
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", READY,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("Billing PaymentSettlementPayor.open: Search button did not appear within 90 s");
        }
        waitForAngular(800);
        hookAlerts();
        return isOnRoute(ROUTE) && isVisible(READY);
    }

    /** Which search-panel controls are visible: label -&gt; true/false. */
    public Map<String, Boolean> searchPanelPresence() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : SEARCH_PANEL.entrySet()) out.put(e.getKey(), isVisible(e.getValue()));
        return out;
    }

    /** Visible label texts of the search panel (e.g. "From Date", "Payor*"). */
    @SuppressWarnings("unchecked")
    public List<String> visibleLabels() {
        Object r = page.evaluate("() => [...document.querySelectorAll('label')].filter(l => l.offsetParent !== null)"
                + ".map(l => l.innerText.replace(/\\s+/g, ' ').trim()).filter(t => t)");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Error text the screen is showing, if any: an ASP.NET / server error body or a visible error dialog. "" = none. */
    public String visibleErrorText() {
        Object r = page.evaluate("() => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const body = document.body ? document.body.innerText || '' : '';"
                + " const srv = body.match(/server error in|unhandled exception|runtime error|an error has occurred|the resource cannot be found/i);"
                + " if (srv) return srv[0];"
                + " const box = [...document.querySelectorAll('.jconfirm-box, .toast-error, .alert-danger, .sweet-alert')]"
                + "   .find(e => e.getBoundingClientRect().height > 0 && /error|exception|failed/i.test(e.innerText || ''));"
                + " return box ? norm(box.innerText).slice(0, 200) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Bill numbers in the bill table (column "Bill No."). */
    @SuppressWarnings("unchecked")
    public List<String> billNumbers() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Bill No\\./.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " const i = hs.indexOf('Bill No.'); if (i < 0) return [];"
                + " return [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.innerText.trim())"
                + "   .map(x => ((x.cells[i] || {}).innerText || '').trim()); }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /**
     * The row of {@code billNo} as header -&gt; displayed cell text (input cells give their value). Empty map = row not listed.
     * Values are read by column HEADER, so a shifted or mislabelled column shows up as a wrong value.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> billRow(String billNo) {
        Object r = page.evaluate("(no) => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Bill No\\./.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return {};"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " const bi = hs.indexOf('Bill No.');"
                + " const tr = [...t.querySelectorAll('tbody tr')].find(x => x.offsetParent !== null && ((x.cells[bi] || {}).innerText || '').trim() === no);"
                + " if (!tr) return {}; const out = {};"
                + " hs.forEach((h, i) => { if (!h) return; const c = tr.cells[i]; if (!c) return; const inp = c.querySelector('input:not([type=checkbox])');"
                + "   out[h] = inp ? String(inp.value).trim() : c.innerText.replace(/\\s+/g, ' ').trim(); });"
                + " return out; }", billNo);
        return r instanceof Map ? new LinkedHashMap<>((Map<String, String>) r) : new LinkedHashMap<>();
    }

    /** The row checkbox of {@code billNo} in the bill table. */
    private com.microsoft.playwright.Locator billCheckbox(String billNo) {
        return page.locator("table tbody tr")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(billNo))
                .locator(BILL_CHECKBOX).first();
    }

    public boolean isBillTicked(String billNo) {
        try { return billCheckbox(billNo).isChecked(); } catch (Exception e) { return false; }
    }

    /**
     * Tick / untick the row checkbox of {@code billNo} with a real click (the app's SelectPharmacyBillDetails recalculates
     * the "Amount to be settled" in the browser only). No-op when it is already in the wanted state.
     */
    public void setBillTicked(String billNo, boolean ticked) {
        if (isBillTicked(billNo) == ticked) return;
        com.microsoft.playwright.Locator cb = billCheckbox(billNo);
        cb.scrollIntoViewIfNeeded();
        cb.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(15000));
        waitForAngular(1000);
    }

    /** Footer "Amount to be settled". */
    public String amountToBeSettled() { return inputValue(SETTLE_TOTAL); }

    /**
     * Find a payor whose search returns no bills, trying the Payor list in order (skipping {@code exclude}) for at most
     * {@code maxTries} payors. Searching is read-only. Returns {payor, messages} or null when none was found.
     */
    public String[] findPayorWithoutBills(String exclude, int maxTries) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]');"
                + " return s ? [...s.options].map(o => o.text.trim()).filter(t => t && !/select/i.test(t)) : []; }", PAYOR);
        int tried = 0;
        for (String name : names) {
            if (name.equalsIgnoreCase(exclude)) continue;
            if (++tried > maxTries) break;
            if (pickOption(PAYOR, name).isEmpty()) continue;
            int status = search();
            if (status == 200 && billRows() == 0) return new String[]{name, String.valueOf(alerts())};
        }
        return null;
    }
}
