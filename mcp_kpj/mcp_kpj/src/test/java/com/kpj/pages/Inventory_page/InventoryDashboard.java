package com.kpj.pages.Inventory_page;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; <b>Inventory Dashboard</b> — Page Object (read-only use).
 *
 * <p>Locators from the live DOM (hisdemo v2026.11.13.455, manual test 13/11/2026). Route {@code #/InventoryDashboard},
 * controller InventoryDashboardController. Filters: Store select2 {@code #locationid}
 * ({@code InvDashboardList.storeid}), {@code InvDashboardList.FromDate / ToDate}; Search icon =
 * {@code fetchInventoryDashboardlist()} → {@code api/InventoryDashboard/Fetch} (ExecFlag InventoryDasboardData).
 * Three cards: Purchase Request ({@code fnRedirecttoPR()}), Purchase Order ({@code fnRedirecttoPO()}), Good Receive Note
 * ({@code fnRedirecttoGrn()}); 11 counters bound to scope fields listed in {@link #COUNTERS}.</p>
 */
public class InventoryDashboard extends ReadOnlyScreen {

    public static final String ROUTE = "#/InventoryDashboard";
    public static final String READY = "[ng-click='fetchInventoryDashboardlist()']";
    public static final String API = "api/InventoryDashboard/Fetch";

    /** Scope field → label shown on the card. */
    public static final Map<String, String> COUNTERS = new LinkedHashMap<>();
    static {
        COUNTERS.put("PRapprovedqty", "Approved");
        COUNTERS.put("PRcanceldqty", "Rejected");
        COUNTERS.put("PRAuthpending", "PR Authorized Pending");
        COUNTERS.put("PRApprovalpending", "PR Approval Pending");
        COUNTERS.put("PoOpen", "Open");
        COUNTERS.put("PoClose", "Close");
        COUNTERS.put("Authpopending", "PO Authorized Pending");
        COUNTERS.put("PoApprovalpending", "PO Approval Pending");
        COUNTERS.put("grnCompleted", "Complete Received");
        COUNTERS.put("grnpartial", "Partial Received");
        COUNTERS.put("grnReturn", "Return");
    }

    public String lastRequest = "";

    public InventoryDashboard(Page page) { super(page); }

    public boolean open() { return openViaMenuPath(ROUTE, READY, "^\\s*Inventory\\s*$"); }

    public boolean reopen() { goHome(); return open(); }

    public String pickStore(String text) { return pickOption("InvDashboardList.storeid", text); }

    public void setDates(String from, String to) {
        setNgInput("InvDashboardList.FromDate", from);
        setNgInput("InvDashboardList.ToDate", to);
    }

    /** Click Search and wait for the counters API; returns its status (0 = no request). */
    public int search() {
        clearCaptured();
        Response r = clickAndWaitForApi("fetchInventoryDashboardlist()", API, 20000);
        waitForAngular(1200);
        if (r == null) { lastRequest = ""; return 0; }
        try { lastRequest = String.valueOf(r.request().postData()); } catch (Exception e) { lastRequest = ""; }
        return r.status();
    }

    /** Counter values as held by the controller (null = not loaded yet). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> counters() {
        Object r = page.evaluate("(keys) => { const el = document.querySelector('[ng-controller=\"InventoryDashboardController\"]');"
                + " const s = el && angular.element(el).scope(); const o = {}; keys.forEach(k => o[k] = s && s[k] !== undefined ? s[k] : null); return o; }",
                new java.util.ArrayList<>(COUNTERS.keySet()));
        return r instanceof Map ? new LinkedHashMap<>((Map<String, Object>) r) : new LinkedHashMap<>();
    }

    public static long total(Map<String, Object> c) {
        long t = 0;
        for (Object v : c.values()) if (v instanceof Number) t += ((Number) v).longValue();
        return t;
    }

    /** Counter texts as rendered on the cards, e.g. "Approved : 28". */
    @SuppressWarnings("unchecked")
    public List<String> renderedCounters() {
        Object r = page.evaluate("() => { const root = document.querySelector('[ng-controller=\"InventoryDashboardController\"]'); if (!root) return [];"
                + " return [...root.querySelectorAll('[ng-click^=\"fnRedirect\"] *')].filter(e => e.children.length === 0 && /:/.test(e.innerText || ''))"
                + ".map(e => e.innerText.replace(/\\s+/g, ' ').trim()); }");
        return r instanceof List ? (List<String>) r : new java.util.ArrayList<>();
    }

    public int cardCount() {
        Object r = page.evaluate("() => document.querySelectorAll('[ng-controller=\"InventoryDashboardController\"] [ng-click^=\"fnRedirect\"]').length");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Click a card and wait for the route to change; returns the new URL. */
    public String clickCard(String ngClick) {
        String before = page.url();
        clickNg(ngClick);
        try {
            page.waitForFunction("(b) => location.href !== b", before, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(2500);
        return page.url();
    }
}
