package com.kpj.pages.AncillaryServices_page.Linen_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ancillary Services &gt; Linen &gt; <b>Linen Machine</b> — master-list screen Page Object.
 *
 * <p>Verified live on HIS Demo (v2026.10.28.322, 29/10/2026):</p>
 * <ul>
 *   <li>Route {@code #/LinenMachine} (ui-router state {@code LinenMachine}), template
 *       {@code GeneralConfig/Load/CommonMasterList} — the shared Common Master screen, the same one Linen Category
 *       Master uses. The sidebar link is {@code a[href='#/LinenMachine']} under Ancillary Services &gt; Linen.</li>
 *   <li>Inline add form {@code form[name=myCommonMasterForm]} (no separate Add button): a disabled
 *       <b>Form Name*</b> select showing "Linen Machine", <b>Code*</b> ({@code commonmaster.Code}, maxlength 15),
 *       <b>Remark*</b> ({@code commonmaster.Description}, maxlength 250), optional MIMS GUID / MIMS Description /
 *       MIMS Type, and <b>Submit</b> / <b>Clear</b> buttons.</li>
 *   <li>List: ui-grid {@code gridOptions} with columns <b>Code</b>, <b>Remark</b>, <b>Status</b> (Active/Inactive
 *       checkbox) and <b>Edit</b>; per-column filter boxes are enabled. There is NO delete action.</li>
 *   <li>List API {@code POST /api/CommonMaster/fetch} body {@code {"ExecFlag":"GRID","MasterCodeFor":"LinenMachine"}};
 *       save API {@code POST /api/CommonMaster/IUD}.</li>
 *   <li>Client validation in {@code fnIUDCommonMaster()} (runs BEFORE the save call): empty Code -&gt;
 *       "Please Enter Code"; Code without Remark -&gt; "Please Enter Description". Server outcomes: "Master Saved
 *       Successfully", "Code already exists", "Description already exists", "Failed", "Error" — all via JAlert.</li>
 * </ul>
 */
public class LinenMachine extends BasePage {

    public static final String ROUTE = "#/LinenMachine";
    public static final String MASTER_CODE_FOR = "LinenMachine";
    public static final List<String> EXPECTED_COLUMNS = List.of("Code", "Remark", "Status", "Edit");

    public static final String MSG_SAVED = "Master Saved Successfully";
    public static final String MSG_ENTER_CODE = "Please Enter Code";
    public static final String MSG_ENTER_REMARK = "Please Enter Description";

    private static final String LIST_API = "/api/CommonMaster/fetch";
    private static final String SAVE_API = "/api/CommonMaster/IUD";

    private static final String FORM = "form[name='myCommonMasterForm']";
    private static final String CODE_INPUT = FORM + " input[ng-model='commonmaster.Code']";
    private static final String REMARK_INPUT = FORM + " input[ng-model='commonmaster.Description']";
    private static final String GRID = "div[ui-grid='gridOptions']";

    /** Last list-API result seen (status + row count), e.g. "HTTP 200, 0 row(s)". */
    public String lastListApi = "(list API not observed)";
    public int lastListStatus = -1;
    /** Last save-API result seen (status + body). */
    public String lastSaveApi = "(no save call made)";

    private final List<String> listCalls = Collections.synchronizedList(new ArrayList<>());
    private final Consumer<Response> listWatcher = resp -> {
        try {
            if (!resp.url().contains(LIST_API)) return;
            String body = resp.request().postData();
            if (body == null || !body.contains(MASTER_CODE_FOR) || !body.contains("GRID")) return;
            String text = "";
            try { text = resp.text(); } catch (Exception ignore) { }
            int rows = text.trim().startsWith("[") ? countJsonRows(text) : -1;
            lastListStatus = resp.status();
            lastListApi = "HTTP " + resp.status() + ", " + (rows < 0 ? "unparsed body" : rows + " row(s)");
            listCalls.add(lastListApi);
        } catch (Exception ignore) { }
    };

    public LinenMachine(Page page) {
        super(page);
        page.onResponse(listWatcher);
    }

    /** Stop listening for list-API responses (call when the test is done with the page object). */
    public void dispose() {
        try { page.offResponse(listWatcher); } catch (Exception ignore) { }
    }

    // ---- navigation ------------------------------------------------------

    /** Ancillary Services -&gt; Linen -&gt; Linen Machine via the sidebar; falls back to the hash route. */
    public boolean navigateViaMenu() {
        boolean menu = expandMenuEntry(null, "^ancillary services$") && expandMenuEntry("^ancillary services$", "^linen$");
        if (menu) {
            Locator link = page.locator("a[href='" + ROUTE + "']").first();
            try {
                link.click(new Locator.ClickOptions().setTimeout(8000));
            } catch (Exception e) {
                System.out.println("LinenMachine.nav: menu link not clickable - " + e.getMessage());
                menu = false;
            }
        } else {
            System.out.println("LinenMachine.nav: Ancillary Services > Linen menu could not be expanded");
        }
        if (!waitForScreen(15000)) {
            System.out.println("LinenMachine.nav: falling back to direct route " + ROUTE);
            page.evaluate("(h) => { window.location.hash = h.replace(/^#/, ''); }", ROUTE);
            waitForScreen(15000);
        }
        usedMenu = menu;
        return onScreen();
    }

    /** True when the last navigateViaMenu() reached the screen through the sidebar link. */
    public boolean usedMenu = false;

    /** Waits for the route, the Common Master form and the grid to be rendered. */
    public boolean waitForScreen(int timeoutMs) {
        try {
            page.waitForFunction("([form, grid]) => /#\\/LinenMachine$/i.test(location.hash)"
                    + " && !!document.querySelector(form) && document.querySelector(form).offsetParent !== null"
                    + " && !!document.querySelector(grid)", List.of(FORM, GRID),
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** On the Linen Machine screen: route, page header and the Form Name select all say Linen Machine. */
    public boolean onScreen() {
        Object r = page.evaluate("(form) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " if(!/#\\/LinenMachine$/i.test(location.hash)) return false;"
                + " const f=document.querySelector(form); if(!f || f.offsetParent===null) return false;"
                + " const sel=f.querySelector('select'); const opt=sel && sel.selectedIndex>=0 ? norm(sel.options[sel.selectedIndex].textContent) : '';"
                + " return /^linen machine$/i.test(opt) || /linen machine/i.test(document.title||''); }", FORM);
        return Boolean.TRUE.equals(r);
    }

    /** Header + URL actually shown — for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => (document.title || '(no title)')");
        return r + " @ " + page.url();
    }

    // ---- list ------------------------------------------------------------

    /** Waits until the grid's list call has answered (or the timeout passes). */
    public void waitForList(int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end && listCalls.isEmpty()) page.waitForTimeout(250);
        page.waitForTimeout(400); // let ui-grid render the rows it just received
    }

    /** Number of list-API answers seen so far (used to wait for the post-save refresh). */
    public int listCallCount() { return listCalls.size(); }

    /** Column header labels rendered in the visible grid, in display order (the selection column excluded). */
    @SuppressWarnings("unchecked")
    public List<String> gridColumns() {
        Object r = page.evaluate("(grid) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const g=document.querySelector(grid); if(!g) return [];"
                + " return [...g.querySelectorAll('.ui-grid-header-cell .ui-grid-header-cell-label')]"
                + "   .map(e=>norm(e.textContent)).filter(t=>t && !/^sel\\.?$/i.test(t)); }", GRID);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** All rows the grid holds (all pages) as {Code, Remark, Status}. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridData() {
        Object r = page.evaluate("(grid) => { const g=document.querySelector(grid); if(!g) return [];"
                + " const s=angular.element(g).scope(); const o=s && s.$eval('gridOptions'); const d=(o && o.data) || [];"
                + " return d.map(x=>({Code:String(x.Code==null?'':x.Code), Remark:String(x.Description==null?'':x.Description),"
                + "   Status: x.Status ? 'Active' : 'Inactive'})); }", GRID);
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
    }

    /** Code cell texts of the rows currently RENDERED (i.e. after any filter, on the current page). */
    @SuppressWarnings("unchecked")
    public List<String> visibleCodes() {
        Object r = page.evaluate("(grid) => { const g=document.querySelector(grid); if(!g) return [];"
                + " const body=g.querySelector('.ui-grid-render-container-body') || g;"
                + " return [...body.querySelectorAll('.ui-grid-viewport .ui-grid-row')].map(row=>{"
                + "   const c=row.querySelector('.ui-grid-cell .ui-grid-cell-contents'); return c ? c.textContent.replace(/\\s+/g,' ').trim() : ''; })"
                + "   .filter(t=>t); }", GRID);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Type into the Code column's filter box (the grid's built-in search). Returns false if there is none. */
    public boolean filterByCode(String term) {
        Locator box = page.locator(GRID + " .ui-grid-header-cell input.ui-grid-filter-input").first();
        if (box.count() == 0) return false;
        box.fill(term);
        page.waitForTimeout(800);
        return true;
    }

    public void clearCodeFilter() {
        Locator box = page.locator(GRID + " .ui-grid-header-cell input.ui-grid-filter-input").first();
        if (box.count() > 0) box.fill("");
        page.waitForTimeout(500);
    }

    // ---- form ------------------------------------------------------------

    /** Which add-form controls are present and usable — "" when everything is there, else what is missing. */
    public String formProblems() {
        List<String> missing = new ArrayList<>();
        if (!page.locator(CODE_INPUT).isVisible()) missing.add("Code input");
        if (!page.locator(REMARK_INPUT).isVisible()) missing.add("Remark input");
        if (!submitButton().isVisible()) missing.add("Submit button");
        if (!clearButton().isVisible()) missing.add("Clear button");
        return String.join(", ", missing);
    }

    public Locator submitButton() {
        return page.locator(FORM).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Submit").setExact(true));
    }

    public Locator clearButton() {
        return page.locator(FORM).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Clear").setExact(true));
    }

    /** Clear the form with the screen's own Clear button. */
    public void clearForm() {
        clearButton().click();
        page.waitForTimeout(500);
    }

    public void fill(String code, String remark) {
        page.locator(CODE_INPUT).fill(code == null ? "" : code);
        page.locator(REMARK_INPUT).fill(remark == null ? "" : remark);
    }

    public String codeValue() { return page.locator(CODE_INPUT).inputValue(); }
    public String remarkValue() { return page.locator(REMARK_INPUT).inputValue(); }

    /** Start recording JAlert messages (type:message) into window.__lmAlerts. Idempotent per page load. */
    public void captureAlerts() {
        page.evaluate("() => { window.__lmAlerts = [];"
                + " if (window.JAlert && !window.JAlert.__lmWrapped) { const o = window.JAlert;"
                + "   window.JAlert = function (m, t) { try { window.__lmAlerts.push((t || '') + ':' + m); } catch (e) {} return o.apply(this, arguments); };"
                + "   window.JAlert.__lmWrapped = true; } }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__lmAlerts || []");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /**
     * Click Submit while the save API is BLOCKED at the network layer, so the click can never create a record —
     * used for the mandatory-field checks. Returns the alert raised; {@link #saveCallAttempted} tells whether the
     * page tried to call the save API anyway (that would mean the client-side validation is missing).
     */
    public String submitWithSaveBlocked() {
        saveCallAttempted = false;
        Consumer<Route> blocker = route -> { saveCallAttempted = true; route.abort(); };
        page.route("**" + SAVE_API + "**", blocker);
        try {
            captureAlerts();
            submitButton().click();
            waitForAlert(5000);
            return String.join(" | ", alerts());
        } finally {
            try { page.unroute("**" + SAVE_API + "**", blocker); } catch (Exception ignore) { }
        }
    }

    public volatile boolean saveCallAttempted = false;

    /**
     * REAL save: click Submit and wait for the save API answer and the JAlert it raises. Only call this when
     * the test has been explicitly authorised to write (-Ddevhis.allowSave=true).
     */
    public String submitForReal() {
        captureAlerts();
        try {
            Response resp = page.waitForResponse(rs -> rs.url().contains(SAVE_API),
                    new Page.WaitForResponseOptions().setTimeout(20000), () -> submitButton().click());
            String body = "";
            try { body = resp.text(); } catch (Exception ignore) { }
            if (body.length() > 300) body = body.substring(0, 300) + "...";
            lastSaveApi = "HTTP " + resp.status() + " " + body.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            lastSaveApi = "no save call answered within 20s (" + String.valueOf(e.getMessage()).split("\n")[0] + ")";
        }
        waitForAlert(8000);
        return String.join(" | ", alerts());
    }

    private void waitForAlert(int timeoutMs) {
        try {
            page.waitForFunction("() => (window.__lmAlerts || []).length > 0", null,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
        page.waitForTimeout(300);
    }

    // ---- utility ---------------------------------------------------------

    /** Counts top-level objects in a JSON array without a JSON library. */
    private static int countJsonRows(String json) {
        String t = json.trim();
        if (t.equals("[]")) return 0;
        int depth = 0, rows = 0;
        boolean inStr = false, esc = false;
        for (char c : t.toCharArray()) {
            if (inStr) {
                if (esc) esc = false; else if (c == '\\') esc = true; else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') { if (depth == 1) rows++; depth++; }
            else if (c == '}') depth--;
            else if (c == '[') depth++;
            else if (c == ']') depth--;
        }
        return rows;
    }
}
