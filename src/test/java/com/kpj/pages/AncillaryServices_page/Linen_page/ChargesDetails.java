package com.kpj.pages.AncillaryServices_page.Linen_page;

import com.kpj.core.Reasons;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ancillary Services &gt; Linen &gt; Linen Item Master &gt; <b>Charges Details</b> tab — Page Object.
 *
 * <p>What "Charges Details" is in nHIS (confirmed from the live template
 * {@code AncillaryServices/Load/LinenItemMaster} and {@code LinenItemMasterController.js}, 29/10/2026):</p>
 * <ul>
 *   <li>Menu: <b>Ancillary Services → Linen → Linen Item Master</b> ({@code #/LinenItemMasterList}).
 *       <b>Add</b> opens {@code #/add-linenitem}; a row's Edit opens {@code #/edit-linenitem/:ItemId}.</li>
 *   <li>The form has two tabs: <i>Item Details</i> and <i>Charges Details</i> ({@code a[data-target='#Dtls']}).</li>
 *   <li><b>Category</b> = select2 {@code #equipConfigCmplTyp} bound to {@code CategoryList.linencategoryid}, options from
 *       {@code /api/CommonDropdown/FetchListForAncillary} with ExecFlag {@code CategoryMaster} (Linen Category Master).</li>
 *   <li><b>Charges</b> = free-text input {@code CategoryList.Charges}.</li>
 *   <li><b>Add</b> ({@code AddCompliance()}) only pushes a row into the in-memory list {@code lstComplianceDtlsList} —
 *       no API call. Its validations: "Please Select Category Name", "Please Select Charges",
 *       "Please Select Different Category" (duplicate category).</li>
 *   <li><b>List</b> = table <i>View / Category / Charges</i>; the eye button ({@code fnEditComplianceDtls}) loads a row
 *       back into the Category/Charges fields, <b>Modify</b> ({@code ModifyCompliance()}) updates that row in memory.</li>
 *   <li><b>Save</b> ({@code fnIUDLinenItemMaster()}) POSTs an INSERT straight away — there is NO "Do You Want To Save"
 *       confirmation. This page object deliberately has no method that clicks Save or Modify.</li>
 * </ul>
 */
public class ChargesDetails extends BasePage {

    public ChargesDetails(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/LinenItemMasterList";
    public static final String ADD_ROUTE = "#/add-linenitem";
    public static final String EDIT_ROUTE = "#/edit-linenitem/";

    // ---- locators (stable ids / ng-click / ng-model taken from the live template) ----
    private static final String LIST_ADD_BTN   = "button[ng-click='AddButtunClick()']";
    private static final String CHARGES_TAB    = "a[data-target='#Dtls']";
    private static final String CHARGES_PANE   = "#Dtls";
    private static final String CATEGORY_SEL   = "#equipConfigCmplTyp";
    private static final String CATEGORY_S2    = "#s2id_equipConfigCmplTyp";
    // The template has exactly ONE Charges input and ONE Add button, both inside the #Dtls tab pane. They are
    // "hidden" only when that pane is not the active tab, so scope them to the ACTIVE pane: a match then means the
    // control is really on screen, and no match means the form is on another tab (never a hidden duplicate).
    private static final String ACTIVE_PANE    = "#Dtls.tab-pane.active";
    private static final String CHARGES_INPUT  = ACTIVE_PANE + " input[ng-model='CategoryList.Charges']";
    private static final String ADD_CHARGE_BTN = ACTIVE_PANE + " button[ng-click='AddCompliance()']";
    private static final String BACK_BTN       = "button[ng-click='CloseForm()']";
    private static final String TABLE_ROWS     = "#Dtls table tbody tr";

    /** "-Select-" style placeholder option text. */
    private static final String IS_PLACEHOLDER_JS = "t => !t || /^-*\\s*select\\s*-*$/i.test(t)";

    // ---- navigation ------------------------------------------------------

    /** Ancillary Services → Linen → Linen Item Master, via the sidebar (falls back to the route only if the link is absent). */
    public boolean navigateViaMenu() {
        boolean anc = expandMenuEntry(null, "^Ancillary Services$");
        boolean lin = anc && expandMenuEntry("^Ancillary Services$", "^Linen$");
        Locator link = page.locator("a[href='" + LIST_ROUTE + "']").first();
        boolean clicked = false;
        if (lin) {
            try {
                link.waitFor(new Locator.WaitForOptions().setTimeout(8000));
                link.click(new Locator.ClickOptions().setTimeout(8000));
                clicked = true;
            } catch (Exception e) {
                System.out.println("ChargesDetails.nav: menu link click failed - " + e.getMessage());
            }
        }
        if (!clicked) {
            Reasons.add("Sidebar path Ancillary Services > Linen > Linen Item Master was not clickable (Ancillary=" + anc
                    + ", Linen=" + lin + ") - opened the route " + LIST_ROUTE + " directly");
            page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE);
        }
        return waitForHash("LinenItemMasterList", 15000);
    }

    public boolean onListScreen() {
        if (!page.url().contains("LinenItemMasterList")) return false;
        try {
            page.locator(LIST_ADD_BTN).waitFor(new Locator.WaitForOptions().setTimeout(10000));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Visible heading / browser title for the screen. */
    public String screenTitle() {
        Object r = page.evaluate("() => { const h=[...document.querySelectorAll('h1,h2,h3,h4,.page-title')].find(e=>e.offsetParent!==null && /linen item master/i.test(e.textContent||''));"
                + " return h ? h.textContent.replace(/\\s+/g,' ').trim() : document.title; }");
        return r == null ? "" : r.toString();
    }

    /** Linen Item Master list grid: {columns, rowCount}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listGridInfo() {
        // ui-grid keeps its grid on the directive's ISOLATE scope, not on .scope() of the element.
        Object r = page.evaluate("() => { let gs=null; document.querySelectorAll('.ui-grid,[ui-grid]').forEach(el=>{ if(gs) return; try{ const e=angular.element(el);"
                + " for (const s of [e.isolateScope && e.isolateScope(), e.scope()]) { if(s && s.grid && s.grid.options){ gs=s; break; } } }catch(x){} });"
                + " if(!gs) return {columns:[], rows:-1};"
                + " return {columns: gs.grid.options.columnDefs.map(c=>c.displayName||c.field||''), rows: (gs.grid.options.data||[]).length}; }");
        return r instanceof Map ? (Map<String, Object>) r : Map.of("columns", List.of(), "rows", -1);
    }

    /** Open the first existing Linen Item (Edit) — read-only use. Returns the item name, or "" when the list is empty. */
    public String openFirstExistingItem() {
        Object r = page.evaluate("() => { const row=document.querySelector('.ui-grid-render-container-body .ui-grid-row'); if(!row) return '';"
                + " const b=[...row.querySelectorAll('[ng-click]')].find(x=>/edit/i.test(x.getAttribute('ng-click')||'')||/edit|pencil/i.test(x.className||'')||/edit/i.test(x.textContent||''));"
                + " if(!b) return ''; b.id='__cdEdit'; const cells=[...row.querySelectorAll('.ui-grid-cell')].map(c=>c.textContent.replace(/\\s+/g,' ').trim());"
                + " return cells[1]||cells[0]||'item'; }");
        String name = r == null ? "" : r.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__cdEdit").click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("ChargesDetails.openFirstExistingItem: " + e.getMessage()); return ""; }
        return waitForHash("edit-linenitem", 15000) ? name : "";
    }

    /** Click the list's <b>Add</b> button and wait for the Linen Item form. */
    public boolean clickAddNewItem() {
        try {
            page.locator(LIST_ADD_BTN).click(new Locator.ClickOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("ChargesDetails.clickAddNewItem: " + e.getMessage());
            return false;
        }
        return waitForHash("add-linenitem", 15000);
    }

    // ---- Charges Details tab ---------------------------------------------

    /** Close any open select2 drop (its mask covers the tabs) and open the Charges Details tab. */
    public boolean openChargesDetailsTab() {
        try {
            page.locator(CHARGES_TAB).waitFor(new Locator.WaitForOptions().setTimeout(15000));
            closeSelect2Drops();
            page.locator(CHARGES_TAB).click(new Locator.ClickOptions().setTimeout(8000));
            page.locator(CHARGES_PANE + ".active").waitFor(new Locator.WaitForOptions().setTimeout(8000));
            return true;
        } catch (Exception e) {
            System.out.println("ChargesDetails.openChargesDetailsTab: " + e.getMessage());
            return false;
        }
    }

    public boolean isChargesTabActive() {
        return Boolean.TRUE.equals(page.evaluate("() => { const p=document.querySelector('#Dtls'); const a=document.querySelector(\"a[data-target='#Dtls']\");"
                + " return !!p && p.classList.contains('active') && !!a && a.parentElement.classList.contains('active'); }"));
    }

    /** Text of the "Charges Details" panel heading inside the tab. */
    public String panelHeading() {
        Object r = page.evaluate("() => { const h=document.querySelector('#Dtls .panel-heading'); return h ? h.textContent.replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    public boolean categoryFieldVisible() {
        try { return page.locator(CATEGORY_S2).isVisible() || page.locator(CATEGORY_SEL).isVisible(); }
        catch (Exception e) { return false; }
    }

    public boolean chargesFieldVisible() {
        try { return page.locator(CHARGES_INPUT).isVisible(); } catch (Exception e) { return false; }
    }

    /** Which tab pane is active now: "Dtls" (Charges Details), "itemDtls" (Item Details), or "" when none. */
    public String activePane() {
        Object r = page.evaluate("() => { const p=document.querySelector('#myLinenItemMasterForm .tab-pane.active'); return p ? p.id : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Make sure the form is on the Charges Details tab (re-opening it only if another tab became active).
     * Returns true when the Charges Details pane is the active one afterwards.
     */
    public boolean ensureChargesTabActive() {
        if (isChargesTabActive()) return true;
        System.out.println("ChargesDetails: form is on tab '" + activePane() + "' - re-opening Charges Details");
        return openChargesDetailsTab() && isChargesTabActive();
    }

    /**
     * Explicit readiness check for the Charges box: present in the ACTIVE Charges Details pane, visible, enabled and
     * editable, waiting at most {@code timeoutMs}. Returns "" when ready, otherwise the reason it is not.
     * Never forces a hidden field visible.
     */
    public String chargesFieldNotReadyReason(int timeoutMs) {
        Locator in = page.locator(CHARGES_INPUT);
        try {
            in.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        } catch (Exception e) {
            return "Charges input not visible in the active Charges Details pane (active tab: '" + activePane() + "')";
        }
        try {
            if (!in.isEnabled()) return "Charges input is disabled";
            if (!in.isEditable()) return "Charges input is read-only";
        } catch (Exception e) {
            return "Charges input state unreadable: " + String.valueOf(e.getMessage()).split("\n")[0];
        }
        return "";
    }

    /** Visible Add / Modify / Save buttons in the tab. */
    public List<String> tabButtons() {
        return toStringList(page.evaluate("() => [...document.querySelectorAll('#Dtls button')].filter(b=>b.offsetParent!==null)"
                + ".map(b=>b.textContent.replace(/\\s+/g,' ').trim()).filter(Boolean)"));
    }

    /**
     * The Category dropdown's real options. Waits (explicitly, up to {@code timeoutMs}) for the CategoryMaster dropdown
     * call to fill {@code drpCategory}; an empty result after the wait means the API returned no Linen Categories.
     */
    public List<String> categoryOptions(int timeoutMs) {
        try {
            page.waitForFunction("() => { const s=document.querySelector('#equipConfigCmplTyp'); return !!s && s.options.length > 1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception timeout) {
            Reasons.add("Category dropdown has no options after " + (timeoutMs / 1000) + "s - FetchListForAncillary('CategoryMaster') returned no Linen Categories on this environment");
        }
        return toStringList(page.evaluate("() => { const isPh=" + IS_PLACEHOLDER_JS + "; const s=document.querySelector('#equipConfigCmplTyp'); if(!s) return [];"
                + " return [...s.options].map(o=>o.textContent.replace(/\\s+/g,' ').trim()).filter(t=>!isPh(t)); }"));
    }

    /** Select a Category by visible text (select2 + Angular ngModel). Returns the text shown by select2 afterwards. */
    public String selectCategory(String optionText) {
        // ng-options option values are encoded ("number:9001"); let Angular's own select directive decode them by
        // firing 'change' (calling $setViewValue with o.value would store the encoded string and break Add).
        page.evaluate("(txt) => { const e=document.querySelector('#equipConfigCmplTyp'); if(!e) return;"
                + " const o=[...e.options].find(x=>x.textContent.replace(/\\s+/g,' ').trim()===txt); if(!o) return;"
                + " e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(window.jQuery){ try{ jQuery(e).select2('val', o.value); }catch(x){} } }", optionText);
        try {
            page.waitForFunction("(txt) => { const c=document.querySelector('#s2id_equipConfigCmplTyp .select2-chosen'); return !!c && c.textContent.trim()===txt; }",
                    optionText, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception ignore) { }
        return selectedCategoryText();
    }

    /** What the Category select2 box currently shows. */
    public String selectedCategoryText() {
        Object r = page.evaluate("() => { const c=document.querySelector('#s2id_equipConfigCmplTyp .select2-chosen'); if(c) return c.textContent.trim();"
                + " const s=document.querySelector('#equipConfigCmplTyp'); return s && s.selectedIndex>=0 ? s.options[s.selectedIndex].textContent.trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The bound model value {@code CategoryList.linencategoryid} ("" when nothing selected). */
    public String selectedCategoryModel() {
        Object r = page.evaluate("() => { const s=angular.element(document.querySelector('#equipConfigCmplTyp')).scope();"
                + " const v=s && s.CategoryList ? s.CategoryList.linencategoryid : null; return v==null ? '' : String(v); }");
        return r == null ? "" : r.toString();
    }

    /** Reset the Category back to "-Select-". */
    public void clearCategory() {
        page.evaluate("() => { const e=document.querySelector('#equipConfigCmplTyp'); if(!e) return; const s=angular.element(e).scope();"
                + " const set=()=>{ s.CategoryList = s.CategoryList || {}; s.CategoryList.linencategoryid = null; };"
                + " if(s.$root.$$phase) set(); else s.$apply(set);"
                + " if(window.jQuery){ try{ jQuery(e).select2('val',''); }catch(x){} } }");
    }

    /**
     * Type into the Charges box — only after {@link #chargesFieldNotReadyReason} says it is visible, enabled and
     * editable. Returns "" on success, otherwise why it could not be filled (no 30s Playwright timeout).
     */
    public String enterCharges(String value) {
        String notReady = chargesFieldNotReadyReason(5000);
        if (!notReady.isEmpty()) return notReady;
        page.locator(CHARGES_INPUT).fill(value == null ? "" : value, new Locator.FillOptions().setTimeout(5000));
        return "";
    }

    public String chargesValue() {
        Locator in = page.locator(CHARGES_INPUT);
        try { return in.count() == 1 ? in.inputValue() : ""; } catch (Exception e) { return ""; }
    }

    /** Click the tab's <b>Add</b> (adds to the in-memory list only). Returns false when it is not on screen. */
    public boolean clickAddCharge() {
        closeSelect2Drops();
        try {
            page.locator(ADD_CHARGE_BTN).click(new Locator.ClickOptions().setTimeout(5000));
            return true;
        } catch (Exception e) {
            System.out.println("ChargesDetails.clickAddCharge: Add not clickable (active tab '" + activePane() + "')");
            return false;
        }
    }

    /** Click the eye (View) button of row {@code index} (0-based). */
    public boolean clickViewRow(int index) {
        Locator btn = page.locator(TABLE_ROWS).nth(index).locator("button");
        try { btn.click(new Locator.ClickOptions().setTimeout(5000)); return true; }
        catch (Exception e) { System.out.println("ChargesDetails.clickViewRow: " + e.getMessage()); return false; }
    }

    /** Header cells of the charges table. */
    public List<String> chargesTableHeaders() {
        return toStringList(page.evaluate("() => [...document.querySelectorAll('#Dtls table thead th')].map(t=>t.textContent.replace(/\\s+/g,' ').trim())"));
    }

    /** Rows of the charges table as {category, charges}. */
    public List<String[]> chargesRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll('#Dtls table tbody tr')].map(tr=>{ const td=[...tr.querySelectorAll('td')].map(c=>c.textContent.replace(/\\s+/g,' ').trim()); return [td[1]||'', td[2]||'']; })");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> cells = (List<?>) row;
            out.add(new String[]{String.valueOf(cells.get(0)), String.valueOf(cells.get(1))});
        }
        return out;
    }

    /** Wait until the charges table has exactly {@code count} rows. */
    public boolean waitForRowCount(int count, int timeoutMs) {
        try {
            page.waitForFunction("(n) => document.querySelectorAll('#Dtls table tbody tr').length === n", count,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) { return false; }
    }

    /** The controller's in-memory list as {category, charges} (cross-check for the rendered table). */
    public List<String[]> modelRows() {
        Object r = page.evaluate("() => { const s=angular.element(document.querySelector('#equipConfigCmplTyp')).scope(); const l=(s&&s.lstComplianceDtlsList)||[];"
                + " return l.map(x=>[String(x.CategorName==null?(x.CategoryName==null?'':x.CategoryName):x.CategorName), String(x.ChargesName==null?'':x.ChargesName)]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> cells = (List<?>) row;
            out.add(new String[]{String.valueOf(cells.get(0)), String.valueOf(cells.get(1))});
        }
        return out;
    }

    /** Leave the form with <b>Back</b> — never Save. In-memory rows are discarded. */
    public void backWithoutSaving() {
        try { page.locator(BACK_BTN).click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("ChargesDetails.backWithoutSaving: " + e.getMessage()); }
    }

    // ---- helpers ---------------------------------------------------------

    /**
     * select2 leaves {@code #select2-drop-mask} over the page when a drop is open; it swallows tab/button clicks.
     *
     * <p>Closes ONLY a drop that is actually open. Calling {@code select2('close')} on every select2 (the previous
     * version) re-focuses each control — including the hidden Item Details dropdowns — and on hisdemo
     * (build 2026.10.28 ui-ux) focusing a field brings its tab forward, so the form jumped back to Item Details and
     * the Charges box went hidden (the 30s fill timeout).</p>
     */
    public void closeSelect2Drops() {
        page.evaluate("() => { const mask=document.getElementById('select2-drop-mask');"
                + " const maskOn = !!mask && getComputedStyle(mask).display!=='none';"
                + " const open=[...document.querySelectorAll('.select2-container.select2-dropdown-open')];"
                + " if(!maskOn && !open.length) return;"
                + " if(window.jQuery){ open.forEach(c=>{ const sel=c.nextElementSibling && c.nextElementSibling.tagName==='SELECT' ? c.nextElementSibling : document.getElementById((c.id||'').replace(/^s2id_/,''));"
                + "   try{ if(sel) jQuery(sel).select2('close'); }catch(e){} }); }"
                + " if(mask && getComputedStyle(mask).display!=='none') mask.style.display='none'; }");
    }

    private boolean waitForHash(String fragment, int timeoutMs) {
        try {
            page.waitForFunction("(f) => location.hash.toLowerCase().includes(f.toLowerCase())", fragment,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            page.waitForLoadState();
            return true;
        } catch (Exception e) { return false; }
    }

    private static List<String> toStringList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }

    /** Small helper for report text. */
    public static String join(List<String> l) { return l == null || l.isEmpty() ? "(none)" : String.join(", ", l); }

    public static String rowsText(List<String[]> rows) {
        if (rows.isEmpty()) return "(no rows)";
        List<String> parts = new ArrayList<>();
        for (String[] r : rows) parts.add(Arrays.toString(r));
        return String.join(" ", parts);
    }
}
