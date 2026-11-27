package com.kpj.pages.AncillaryServices_page.KitchenMenu_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ancillary Services &gt; Kitchen &amp; Menu &gt; <b>Kitchen Order Dispatch</b> — Page Object (route {@code #/KitchenOrderDispatch}, controller
 * {@code App/NursingStation/controller/KitchenOrderDispatchController.js}).
 *
 * <p>Confirmed live on devhis (v2026.11.27.558.1):</p>
 * <ul>
 *   <li>Tabs {@code fnSwitchTab('ListOfOrders')} / {@code fnSwitchTab('Preparation')}.</li>
 *   <li><b>List of Orders</b>: Date Range checkbox {@code ListFilter.DateRangeEnabled} with From / To ({@code ListFilter.FromDate|ToDate}, typed
 *       dd/MM/yyyy, {@code ng-change=fnApplyDateRange()}), Total Orders, buttons Print Inpatient Serving Checklist
 *       ({@code fnPrintInpatientServingChecklist()}, HTML in a new window), New Order ({@code fnOpenNewOrder()}), Print
 *       ({@code fnPrintListOfOrders()}, HTML), Refresh ({@code fngetKitchenOrder()}). Table {@code table.kod-order-table}, rows
 *       {@code Kitchen in KitchenOrderList}; column filters {@code Search.Ward|Bed|PatientName|MRN|Age|Allergy|Remarks|Diagnosis} (text),
 *       {@code Search.DietType|TherapeuticDiet|ConsistencyTexture} (selects), {@code AllergyFilter} checkbox — all filter in the browser
 *       ({@code fnApplyFilters}). One column per meal type: pending / taken icon ({@code fnOpenNewOrder(Kitchen, mt)}) and the Print Meal Label
 *       button ({@code fnOpenMealLabelPopup(Kitchen, mt.text)}, prints straight to a new window).</li>
 *   <li><b>New Order</b> popup {@code #OrderPopupModal}: MRN {@code OrderPopup.SearchMRN} + {@code fnSearchOrderPatient()}, Meal Type
 *       {@code OrderPopup.MealTypeId} (plain select), Meal For (Patient / Companion), menu items {@code item.Selected}, Special Instructions,
 *       Save {@code fnSaveNewOrder()}, Clear, × {@code fnCloseOrderPopup()}.</li>
 *   <li><b>Preparation</b>: Meal Type {@code PreparationFilter.mealTypeId}, Date Range, Print Taste Report (frmTasteReport.aspx), Print Item
 *       Preparation Queue (HTML), Save, Refresh {@code fnLoadPreparationList()}, Print Order List (KitchenOrderList.aspx).</li>
 * </ul>
 *
 * <p>Prints: {@link #armPrintCapture()} replaces {@code window.open} so a print writes into a captured document instead of a new window;
 * {@link #captured()} returns the opened URLs and the written HTML.</p>
 */
public class KitchenOrderDispatch extends BasePage {

    public static final String ROUTE = "#/KitchenOrderDispatch";
    public static final String NG_MEALTYPE = "OrderPopup.MealTypeId", NG_PREP_MEAL = "PreparationFilter.mealTypeId";

    public KitchenOrderDispatch(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ksc=()=>{ const e=document.querySelector(\"[ng-model='ListFilter.DateRangeEnabled']\")||document.querySelector(\"[ng-model='PreparationFilter.mealTypeId']\"); return e?angular.element(e).scope():null; };"
          + "const pop=()=>document.getElementById('OrderPopupModal');"
          + "const psc=()=>{ const e=document.querySelector(\"[ng-model='OrderPopup.SearchMRN']\"); return e?angular.element(e).scope():null; };";

    public final List<String> navNotes = new ArrayList<>();
    public long lastLoadMs = -1;

    // ---- navigation ------------------------------------------------------------------------------------------------

    public boolean navigateViaMenu() {
        navNotes.clear();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').trim()==='Ancillary Services' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(45000));
        } catch (Exception e) { navNotes.add("sidebar 'Ancillary Services' not shown within 45 s"); return false; }
        long t0 = System.currentTimeMillis();
        for (int attempt = 0; attempt < 3 && !linkVisible(); attempt++) {
            expandMenuEntry(null, "^Ancillary Services$");
            page.waitForTimeout(1500);
            expandMenuEntry("^Ancillary Services$", "^Kitchen\\s*&\\s*Menu$");
            page.waitForTimeout(1500);
        }
        try {
            Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
            link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            link.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            navNotes.add("'Kitchen Order Dispatch' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
            if (!onScreen()) return false;
        }
        boolean ok = waitList();
        lastLoadMs = System.currentTimeMillis() - t0;
        return ok;
    }

    public boolean reopen() {
        page.evaluate("() => { location.hash = '#/PatientDashboard'; }");
        try { page.waitForFunction("() => /PatientDashboard/i.test(location.href)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        return navigateViaMenu();
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    public boolean waitList() {
        try {
            page.waitForFunction("() => /#\\/KitchenOrderDispatch/i.test(location.href) && [...document.querySelectorAll('button')].some(b=>b.getAttribute('ng-click')==='fngetKitchenOrder();' && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("List of Orders not ready within 30 s (" + page.url() + ")"); return false; }
        return waitForHttpIdle(90000);
    }

    public boolean onScreen() { return page.url().matches("(?i).*#/KitchenOrderDispatch/?$"); }

    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { return angular.element(document.body).injector().get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- List of Orders ----------------------------------------------------------------------------------------------

    public void tab(String name) {
        page.locator("a[ng-click=\"fnSwitchTab('" + name + "')\"]").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(800);
        waitForHttpIdle(90000);
        waitForAngular(500);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> buttons() {
        Object r = page.evaluate("() => {" + JS + " const o={}; [...document.querySelectorAll('.content-wrapper button')].filter(vis).filter(b=>!b.closest('.modal') && norm(b.textContent))"
                + " .forEach(b=>o[norm(b.textContent)]=b.getAttribute('ng-click')||''); return o; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** Orders the screen holds after its filters, the unfiltered total, the "Total Orders" text and the list dates. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listState() {
        Object r = page.evaluate("() => {" + JS + " const s=ksc(); const t=norm((document.querySelector('.content-wrapper')||document.body).innerText);"
                + " const m=t.match(/Total Orders:\\s*(\\d+)/); const rows=[...document.querySelectorAll('table.kod-order-table tbody tr')].filter(vis).filter(r=>norm(r.innerText)).length;"
                + " return { list:(s&&s.KitchenOrderList||[]).length, all:(s&&s.AllKitchenOrderList||[]).length, total:m?+m[1]:-1, rows,"
                + " from:s&&s.ListFilter?String(s.ListFilter.FromDate):'', to:s&&s.ListFilter?String(s.ListFilter.ToDate):'', range:!!(s&&s.ListFilter&&s.ListFilter.DateRangeEnabled), orderdate:s&&s.KitchenOrder?s.KitchenOrder.Orderdate:'' }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Given fields of every order row the grid shows (after filters). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> data(String... fields) {
        Object r = page.evaluate("(f) => {" + JS + " const s=ksc(); return ((s&&s.KitchenOrderList)||[]).map(x=>{ const o={}; f.forEach(k=>o[k]=x[k]===undefined?null:x[k]); return o; }); }",
                Arrays.asList(fields));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Meal-type column headers of the order table, in order. */
    @SuppressWarnings("unchecked")
    public List<String> mealColumns() {
        Object r = page.evaluate("() => {" + JS + " const t=document.querySelector('table.kod-order-table'); if(!t) return [];"
                + " const fixed=['Ward','Bed','Patient Name','MRN','Age','Diet Type','Therapeutic Diet','Consistency','Allergy','Remarks','Diagnosis','Sel'];"
                + " return [...t.querySelectorAll('thead tr:first-child th')].map(h=>norm(h.innerText)).filter(x=>x && !fixed.some(f=>x.startsWith(f))); }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public void refresh() { clickNg("fngetKitchenOrder();", 90000); }

    /** Tick (or untick) Date Range and type From / To; the screen reloads the orders. Returns "" or what did not hold. */
    public String dateRange(boolean on, String from, String to) {
        Locator cb = page.locator("input[ng-model='ListFilter.DateRangeEnabled']");
        if (cb.isChecked() != on) cb.click();
        waitForAngular(600);
        waitForHttpIdle(60000);
        if (!on) return "";
        typeInto("input[ng-model='ListFilter.FromDate']", from);
        typeInto("input[ng-model='ListFilter.ToDate']", to);
        waitForHttpIdle(90000);
        waitForAngular(500);
        Map<String, Object> s = listState();
        return String.valueOf(s.get("orderdate")).isEmpty() ? "the order date did not change" : "";
    }

    private void typeInto(String selector, String value) {
        Locator box = page.locator(selector).first();
        box.fill(value);
        box.dispatchEvent("input");
        box.dispatchEvent("change");
        box.dispatchEvent("blur");
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(c=>c.classList.remove('_720kb-datepicker-open')); }");
        waitForAngular(800);
        waitForHttpIdle(60000);
    }

    /** Type into a column filter text box ({@code Search.MRN} etc.). */
    public void filterText(String field, String value) {
        Locator box = page.locator("input[ng-model='Search." + field + "']").first();
        box.fill(value);
        box.dispatchEvent("input");
        box.dispatchEvent("change");
        waitForAngular(600);
    }

    /** Pick a value in a column filter select ({@code DietType}, {@code TherapeuticDiet}, {@code ConsistencyTexture}); "All" clears it. */
    public String filterSelect(String field, String label) {
        try {
            page.locator("select[ng-model='Search." + field + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
            waitForAngular(600);
            return "";
        } catch (Exception e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }
    }

    @SuppressWarnings("unchecked")
    public List<String> filterOptions(String field) {
        Object r = page.evaluate("(f) => { const e=document.querySelector(\"select[ng-model='Search.\"+f+\"']\"); return e?[...e.options].map(o=>o.text.trim()).filter(t=>t && t!=='All'):[]; }", field);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public void allergyOnly(boolean on) {
        Locator cb = page.locator("input[ng-model='AllergyFilter']");
        if (cb.isChecked() != on) cb.click();
        waitForAngular(600);
    }

    /** Clear every column filter (text boxes, selects, Allergy). */
    public void clearFilters() {
        for (String f : List.of("Ward", "Bed", "PatientName", "MRN", "Age", "Allergy", "Remarks", "Diagnosis")) {
            Locator b = page.locator("input[ng-model='Search." + f + "']");
            if (b.count() > 0 && !b.first().inputValue().isEmpty()) filterText(f, "");
        }
        for (String f : List.of("DietType", "TherapeuticDiet", "ConsistencyTexture")) filterSelect(f, "All");
        allergyOnly(false);
    }

    /** Click the Print Meal Label button of the first row of {@code mrn} that has one (any meal). Returns the meal type, or "" when none. */
    public String clickMealLabel(String mrn) {
        Object meal = page.evaluate("(mrn) => {" + JS + " const rows=[...document.querySelectorAll('table.kod-order-table tbody tr')].filter(vis).filter(r=>!mrn || r.innerText.includes(mrn));"
                + " for(const r of rows){ const b=[...r.querySelectorAll(\"button[ng-click^='fnOpenMealLabelPopup']\")].filter(vis)[0]; if(b){ b.id='__kodLbl'; b.scrollIntoView({block:'center',inline:'center'}); try{ return angular.element(b).scope().mt.text; }catch(e){ return '?'; } } } return ''; }", mrn);
        String m = meal == null ? "" : meal.toString();
        if (m.isEmpty()) return "";
        try { page.locator("#__kodLbl").click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return ""; }
        finally { try { page.evaluate("() => { const e=document.getElementById('__kodLbl'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { } }
        waitForAngular(1500);
        return m;
    }

    // ---- prints -------------------------------------------------------------------------------------------------------

    /** Replace window.open: a print writes into a captured document; the URL and HTML are kept for {@link #captured()}. */
    public void armPrintCapture() {
        page.evaluate("() => { window.__kodPrint={urls:[], html:''}; if(!window.__kodOrigOpen) window.__kodOrigOpen=window.open;"
                + " window.open=function(u){ window.__kodPrint.urls.push(String(u||'')); const d={open(){}, close(){}, write(h){ window.__kodPrint.html+=h; }, writeln(h){ window.__kodPrint.html+=h; },"
                + " getElementById(){ return null; }, querySelector(){ return null; }, body:{innerHTML:'', appendChild(){}}, head:{appendChild(){}}, createElement(){ return {style:{}, setAttribute(){}, appendChild(){}}; } };"
                + " return { closed:false, document:d, focus(){}, print(){ window.__kodPrint.printed=true; }, close(){}, addEventListener(){}, onload:null, setTimeout:window.setTimeout.bind(window) }; }; }");
    }

    public void disarmPrintCapture() {
        page.evaluate("() => { if(window.__kodOrigOpen) window.open=window.__kodOrigOpen; }");
    }

    /** urls (List), html (String, text only), htmlLength (Number), tableCols (Number of header cells of the widest header row). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> captured() {
        Object r = page.evaluate("() => { const p=window.__kodPrint||{urls:[],html:''}; const d=new DOMParser().parseFromString(p.html||'','text/html');"
                + " const t=d.querySelector('table'); const hr=t?[...t.querySelectorAll('tr')].filter(r=>r.querySelector('th')).slice(0,3):[]; const cols=hr.length?Math.max(...hr.map(r=>r.querySelectorAll('th').length)):0;"
                + " return { urls:p.urls, htmlLength:(p.html||'').length, text:(d.body?d.body.innerText||d.body.textContent:'').replace(/\\s+/g,' ').trim().slice(0,4000), tableCols: cols }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Fetch a report URL inside the page (no download): status, content type and size. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchReport(String url) {
        Object r = page.evaluate("async (u) => { try { const x=await fetch(u,{credentials:'include'}); const b=await x.arrayBuffer(); return {status:x.status, type:x.headers.get('content-type')||'', size:b.byteLength}; }"
                + " catch(e) { return {status:-1, type:String(e), size:0}; } }", url);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    // ---- New Order popup ------------------------------------------------------------------------------------------------

    public boolean popupOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " const m=pop(); return !!m && vis(m) && getComputedStyle(m).display!=='none'; }"));
    }

    public String lastPopupNote = "";

    /** Click New Order (the toolbar button, not a meal cell) and wait for #OrderPopupModal; one retry. */
    public boolean openNewOrder() {
        for (int attempt = 0; attempt < 2 && !popupOpen(); attempt++) {
            Locator b = page.locator(".content-wrapper button[ng-click='fnOpenNewOrder();']").first();
            try { b.scrollIntoViewIfNeeded(); b.click(new Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { lastPopupNote = "New Order button not clickable: " + String.valueOf(e.getMessage()).split("\\R")[0]; }
            for (int i = 0; i < 60 && !popupOpen(); i++) page.waitForTimeout(250);
            waitForHttpIdle(30000);
        }
        if (!popupOpen()) {
            Object d = page.evaluate("() => { const m=document.getElementById('OrderPopupModal'); const b=document.querySelector(\".content-wrapper button[ng-click='fnOpenNewOrder();']\");"
                    + " return 'modal ' + (m ? ('class=' + m.className + ' display=' + getComputedStyle(m).display) : 'missing') + '; button ' + (b ? ('visible=' + !!(b.offsetWidth||b.offsetHeight) + ' disabled=' + b.disabled) : 'missing'); }");
            lastPopupNote = (lastPopupNote.isEmpty() ? "" : lastPopupNote + "; ") + d;
        }
        return popupOpen();
    }

    public void clickPopup(String ngClick) {
        page.locator("#OrderPopupModal button[ng-click='" + ngClick + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(800);
        waitForHttpIdle(30000);
        waitForAngular(400);
    }

    public void popupMrn(String mrn) {
        Locator box = page.locator("#OrderPopupModal input[ng-model='OrderPopup.SearchMRN']");
        box.fill(mrn);
        box.dispatchEvent("change");
        waitForAngular(300);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> popupState() {
        Object r = page.evaluate("() => {" + JS + " const s=psc(); const p=(s&&s.OrderPopup)||{}; const items=[...document.querySelectorAll(\"#OrderPopupModal [ng-model='item.Selected']\")];"
                + " return { admissionId:p.AdmissionId||0, patient:norm(p.PatientName||''), dietType:p.DietTypeName||p.DietType||'', mealType:p.MealTypeId||0, mealFor:p.MealFor||'', nbm:!!p.IsNBM,"
                + " items: items.map(c=>{ try{ const it=angular.element(c).scope().item; return it.ItemName||it.FoodItemName||it.Name||'?'; }catch(e){ return '?'; } }), selected:(s&&s.SelectedItems||[]).length }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Meal types the popup offers (distinct, in order). */
    @SuppressWarnings("unchecked")
    public List<String> mealTypes() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"#OrderPopupModal select[ng-model='OrderPopup.MealTypeId']\"); return e?[...new Set([...e.options].map(o=>o.text.trim()).filter(t=>t && t!=='Select'))]:[]; }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public String pickMealType(String label) {
        try {
            page.locator("#OrderPopupModal select[ng-model='OrderPopup.MealTypeId']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
            waitForAngular(800);
            waitForHttpIdle(30000);
            waitForAngular(600);
            return "";
        } catch (Exception e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }
    }

    /** Tick the n-th menu item of the popup. */
    public boolean tickItem(int n) {
        Locator c = page.locator("#OrderPopupModal input[ng-model='item.Selected']").nth(n);
        try { c.check(new Locator.CheckOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(600);
        return true;
    }

    public void closePopup() {
        if (!popupOpen()) return;
        try { clickPopup("fnCloseOrderPopup()"); } catch (Exception ignore) { }
        if (popupOpen()) page.evaluate("() => { try{ $('#OrderPopupModal').modal('hide'); }catch(e){} }");
        waitForAngular(600);
    }

    // ---- Preparation ------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> prepState() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"[ng-model='PreparationFilter.mealTypeId']\"); const s=e?angular.element(e).scope():null;"
                + " const t=[...document.querySelectorAll('.content-wrapper table')].filter(vis).find(x=>/Item Name/.test(x.innerText.slice(0,400)) && /Tasted/.test(x.innerText.slice(0,800)));"
                + " const rows=t?[...t.querySelectorAll('tbody tr')].filter(vis).filter(r=>norm(r.innerText) && r.querySelectorAll('td').length>3).length:-1;"
                + " return { meal: e&&e.options[e.selectedIndex]?e.options[e.selectedIndex].text.trim():'', list:(s&&(s.PreparationList||s.PreparationQueue)||[]).length, rows,"
                + " headers: t?[...t.querySelectorAll('thead th')].map(h=>norm(h.innerText)).filter(Boolean):[] }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public List<String> prepMealTypes() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='PreparationFilter.mealTypeId']\"); return e?[...new Set([...e.options].map(o=>o.text.trim()).filter(t=>t && !/^-*\\s*select/i.test(t)))]:[]; }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public String pickPrepMeal(String label) {
        try {
            page.locator("select[ng-model='PreparationFilter.mealTypeId']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
            waitForAngular(800);
            waitForHttpIdle(60000);
            return "";
        } catch (Exception e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }
    }

    // ---- common ---------------------------------------------------------------------------------------------------------

    public boolean clickNg(String ngClick, int idleMs) {
        Locator b = page.locator("button[ng-click='" + ngClick + "']:visible").first();
        try { b.scrollIntoViewIfNeeded(); b.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(600);
        waitForHttpIdle(idleMs);
        waitForAngular(300);
        return true;
    }

    public void spyAlerts() {
        page.evaluate("() => { if(!window.__kodAlerts) window.__kodAlerts=[]; const o=window.__kodOrigJAlert||window.JAlert; window.__kodOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__kodAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__kodAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }
}
