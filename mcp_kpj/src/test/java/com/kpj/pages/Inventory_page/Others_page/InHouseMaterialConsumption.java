package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory &gt; Others &gt; <b>In-house material consumption</b> — Page Object ({@code #/MaterialConsumption}, form {@code #/add-MaterialConsumption}).
 *
 * <p>Confirmed live (devhis build v2026.10.26.293). The list ({@code MaterialConsumptionList.*}) has a <b>New</b> button (its handler is named {@code AddExpiredItemReturn()}); the form
 * ({@code MaterialConsumption.*}) has:</p>
 * <ul>
 *   <li><b>MRN No.</b> ({@code #textinput}, {@code MaterialConsumption.MRNO}; OPD is the default Reg. Type) with a magnifier button ({@code SearchPatientByMRNo()}): the patient loads silently — no
 *       dialog, no toast — into {@code MaterialConsumption.PatientFullDtl} ("Name : Mrs James Wilson MRN: 1100339490 Mob. No ... Gen ... Age ...") and the visit id ({@code visitadmid}).</li>
 *   <li><b>Store</b> ({@code MaterialConsumption.SearchStoreID}, select2, empty by default) and <b>Add Item</b> ({@code OpenItemList()}), which opens the <b>Item Search</b> dialog.</li>
 *   <li>The dialog ({@code DirectReturn.*}): <b>Item Code</b> ({@code DirectReturn.ItemCode}), <b>Item Name</b> ({@code #txtServiceName}, a TYPE-AHEAD: {@code li.auto-complete-item} from
 *       {@code /api/GRN/fetchItemByStore}; picking a suggestion fills the item grid — typing the code alone does nothing), <b>Search</b> ({@code CommonSearch()}), the item grid (ui-grid; clicking a
 *       row runs {@code getItemBatchdetails} and, when the item's selling price is below its purchase price, raises an alert whose only button is <b>OK</b>), the <b>Items Batch List</b>
 *       (a checkbox per batch, {@code SelectAllCheckBox(List,$index)}, fills the next table) and the <b>Selected Item</b> table (a checkbox that must be ticked before <b>OK</b>
 *       ({@code OKClick()}) accepts the item).</li>
 *   <li>The form's grid then shows one row per chosen batch: Item Name, Batch Code, Expiry Date, Available Stock and Selling Price are read-only; <b>Used Quantity</b> is
 *       {@code Itm.AdjustmentQty} (empty), with optional Start Date / End Date ({@code Itm.startdate / enddate}) and Remark ({@code Itm.remarks}); Total Amount fills in from the quantity.
 *       <b>Save</b> ({@code IUDSave()}).</li>
 * </ul>
 * Saving is REAL: a consumption record is created for the patient and the stock of the batch goes down.
 */
public class InHouseMaterialConsumption extends BasePage {

    /** Every POST the screen made that is not a plain fetch, plus any API call that failed: {status, method, decoded url, body head}. */
    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final List<String[]> searchCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean search = low.contains("fetchitembystore");
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/");
            boolean bad = r.status() >= 400;
            if (!search && !write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (search) searchCalls.add(rec); if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public InHouseMaterialConsumption(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastMrn = "", lastPatient = "", lastCode = "", lastName = "", lastSuggestion = "", lastItemRow = "", lastAlert = "", lastAlertButton = "", lastBatch = "",
            lastBatchCode = "", lastSelected = "", lastGridRow = "", lastAvailableStock = "", lastSellingPrice = "", lastQty = "", lastTotals = "", lastDialog = "", lastToast = "",
            lastSaveMessage = "", lastSaveId = "", searchDiagnosis = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const modal=()=>[...document.querySelectorAll('.modal')].filter(vis).pop();"
          + "const th=(t)=>[...t.querySelectorAll('th')].map(h=>norm(h.textContent)).join('|');"
          + "const modalTable=re=>{ const m=modal(); return m? [...m.querySelectorAll('table')].filter(vis).find(t=>re.test(th(t))) : null; };"
          + "const BATCH=/Batch Code.*Available Quantity/, SELECTED=/Available Stock/;";

    private static final String STORE_SELECT = "MaterialConsumption.SearchStoreID";
    private static final String CODE_BOX = "input[ng-model='DirectReturn.ItemCode']";
    private static final String NAME_BOX = "#txtServiceName";
    private static final String QTY_BOX = "input[ng-model='Itm.AdjustmentQty']";
    private static final String HREF = "#/materialconsumption";

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    /** Is the menu group with this label expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int groupState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Inventory &gt; Others &gt; In-house material consumption. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the
     * group again, and a click on Others straight after Inventory is lost), so Inventory gets a settling pause before Others is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Others") < 0) {                                // Others is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("Others") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before Others is clicked
                }
                if (groupState("Others") == 0 && !linkVisible()) clickGroup("Others");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__mpaLink'; }", HREF);
                try { page.locator("#__mpaLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("InHouseMaterialConsumption.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__mpaLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('button')].find(b=>/AddExpiredItemReturn/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("InHouseMaterialConsumption.nav: the New button is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/materialconsumption/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-materialconsumption/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", STORE_SELECT));
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    /** Tag the first visible button whose ng-click matches {@code re} (inside the open dialog when there is one) and click it with a real mouse click. */
    private boolean clickByNg(String re, String what, boolean inDialog) {
        Object tagged = page.evaluate("(a) => {" + JS + " const root=a.d? (modal()||document) : document;"
                + " const b=[...root.querySelectorAll('button')].filter(vis).find(x=>new RegExp(a.re).test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mpaBtn'; return true; }",
                Map.of("re", re, "d", inDialog));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__mpaBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__mpaBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String boxValue(String css) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", css);
        return v == null ? "" : v.toString().trim();
    }

    private boolean typeInto(String css, String text, String what) {
        Locator in = page.locator(css).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(60));
            return true;
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
    }

    /** A visible alert / confirmation dialog's text ("" if none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.jconfirm, .ng-confirm-box')].filter(vis).map(x=>norm(x.textContent).replace(/^[^A-Za-z0-9]+/,''));"
                + " return d.length? d[d.length-1] : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click the affirmative button (YES, else OK) of the visible alert / confirmation; returns its label, "" when there is none. These dialogs can be STACKED (the same alert raised
     * twice, one on top of the other at the same spot), so the button tagged is the one that is actually on top at its own centre, and the answer repeats while a dialog is still showing.
     */
    private String answerDialog() {
        String first = "";
        for (int round = 0; round < 4; round++) {
            Object label = page.evaluate("() => {" + JS + " const bs=[...document.querySelectorAll('.jconfirm button, .ng-confirm-box button')].filter(vis);"
                    + " const want=bs.filter(x=>/^yes$/i.test(norm(x.textContent))); const alt=bs.filter(x=>/^(ok|okay|confirm)$/i.test(norm(x.textContent)));"
                    + " const cand=(want.length? want : alt).reverse(); if(!cand.length) return '';"
                    + " const onTop=x=>{ const r=x.getBoundingClientRect(); const t=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return !!t && x.contains(t); };"
                    + " const b=cand.find(onTop) || cand[0]; b.id='__mpaAns'; return norm(b.textContent); }");
            String l = label == null ? "" : label.toString();
            if (l.isEmpty()) break;
            if (first.isEmpty()) first = l;
            try { page.locator("#__mpaAns").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                System.out.println("InHouseMaterialConsumption.answerDialog: \"" + l + "\" click failed - " + e.getMessage().split("\n")[0]);
                problems.add("\"" + l + "\" could not be clicked");
                break;
            }
            finally { page.evaluate("() => { const e=document.getElementById('__mpaAns'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(700);
            if (confirmText().isEmpty()) break;
        }
        for (int i = 0; i < 20 && !confirmText().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(500);
        return first;
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- list -> New -> store -> Get Items -----------------------------------------------------------------------

    /** Click New on the list and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        if (!clickByNg("AddExpiredItemReturn", "New", false)) return false;
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (!onForm()) problems.add("the In-house material consumption form did not open (" + page.url() + ")");
        return ok();
    }

    private static final String MRN_BOX = "input[ng-model='MaterialConsumption.MRNO']";

    /** Type the MRN into the MRN No. box. */
    public boolean enterMrn(String mrn) {
        problems.clear();
        if (!typeInto(MRN_BOX, mrn, "MRN")) return false;
        waitForAngular(500);
        lastMrn = boxValue(MRN_BOX);
        if (!mrn.equals(lastMrn)) problems.add("the MRN box shows \"" + lastMrn + "\" instead of \"" + mrn + "\"");
        Object held = page.evaluate("(s) => { const e=document.querySelector(s); try{ return String(angular.element(e).scope().MaterialConsumption.MRNO); }catch(x){ return ''; } }", MRN_BOX);
        if (!mrn.equals(held == null ? "" : held.toString())) problems.add("the MRN is not held by the model");
        return ok();
    }

    /** The patient text the form holds after a search ("Name : Mrs James Wilson MRN: ... Mob. No ... Gen ... Age ...", "" before one). */
    public String patientDetails() {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); try{ const o=angular.element(e).scope().MaterialConsumption; return o && o.PatientFullDtl ? String(o.PatientFullDtl).replace(/\\s+/g,' ').trim() : ''; }catch(x){ return ''; } }", MRN_BOX);
        return v == null ? "" : v.toString().trim();
    }

    /**
     * Click the MRN's Search (the magnifier, {@code SearchPatientByMRNo()}) and wait for the patient to load. The load is silent (no dialog, no toast), so it is judged on the model: the
     * patient text carries the MRN and a visit id is set. Returns the patient text, "" when nothing loaded.
     */
    public String clickSearchPatient() {
        problems.clear();
        if (!clickByNg("SearchPatientByMRNo", "MRN Search", false)) return "";
        String d = "";
        for (int i = 0; i < 48 && d.isEmpty(); i++) {
            d = patientDetails();
            if (d.isEmpty()) {
                String c = confirmText();
                if (!c.isEmpty()) { problems.add("an unexpected dialog appeared after Search: \"" + c + "\""); return ""; }
                page.waitForTimeout(250);
            }
        }
        waitForAngular(800);
        lastPatient = d;
        if (d.isEmpty()) problems.add("no patient loaded for MRN " + lastMrn + (toastsNow().isEmpty() ? "" : " — the screen said \"" + toastsNow() + "\""));
        else if (!d.contains(lastMrn)) problems.add("the patient loaded (\"" + d + "\") is not MRN " + lastMrn);
        return ok() ? d : "";
    }

    /** Choose the Store (empty by default; Get Items and the item search need it). Matches ignoring case and spaces. */
    public String selectStore(String want) {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", STORE_SELECT, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>k(x.t)===k(a.w)); if(!p) p=o.find(x=>k(x.t).indexOf(k(a.w))>=0); if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", STORE_SELECT, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        Object held = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", STORE_SELECT);
        if (held == null || held.toString().isEmpty() || "0".equals(held.toString())) problems.add("the Store is not held by the model");
        return lastStore;
    }

    /** Click Add Item and wait for the Item Search dialog. */
    public boolean clickAddItem() {
        problems.clear();
        if (!clickByNg("OpenItemList", "Add Item", false)) return false;
        try { page.waitForFunction("(s) => { const e=document.querySelector(s); return !!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length); }", CODE_BOX, new Page.WaitForFunctionOptions().setTimeout(15000)); }
        catch (Exception e) { problems.add("the Item Search dialog did not open" + (toastsNow().isEmpty() ? "" : " — the screen said \"" + toastsNow() + "\"")); return false; }
        waitForAngular(1200);
        return true;
    }

    // ---- Item Search dialog: code, name (type-ahead), Search, row ------------------------------------------------

    public boolean enterCode(String code) {
        problems.clear();
        if (!typeInto(CODE_BOX, code, "Item Code")) return false;
        waitForAngular(500);
        lastCode = boxValue(CODE_BOX);
        if (!code.equals(lastCode)) problems.add("the Item Code box shows \"" + lastCode + "\" instead of \"" + code + "\"");
        return ok();
    }

    public boolean typeItemName(String name) {
        problems.clear();
        searchDiagnosis = "";
        synchronized (saveCalls) { searchCalls.clear(); }
        if (!typeInto(NAME_BOX, name, "Item Name")) return false;
        waitForAngular(400);
        lastName = boxValue(NAME_BOX);
        return ok();
    }

    /** The visible type-ahead suggestions, trimmed. */
    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li.auto-complete-item')].filter(vis).map(x=>norm(x.textContent)); }");
        return toList(r);
    }

    /** Wait up to {@code ms} for a suggestion that mentions {@code term}; empty when none appears (the reason is then in {@link #searchDiagnosis}). */
    public List<String> awaitSuggestions(String term, int ms) {
        long end = System.currentTimeMillis() + ms;
        List<String> s = new ArrayList<>();
        while (System.currentTimeMillis() < end) {
            s = suggestions();
            if (s.stream().anyMatch(x -> x.toLowerCase().contains(term.toLowerCase()))) return s;
            page.waitForTimeout(300);
        }
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) { for (String[] c : searchCalls) sb.append("[").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", ""), 0, Math.min(140, c[2].replaceAll("^https?://[^/]+", "").length())).append(" -> ").append(c[3].replaceAll("\\s+", " "), 0, Math.min(120, c[3].replaceAll("\\s+", " ").length())).append("] "); }
        searchDiagnosis = sb.length() == 0 ? "the item search was never called" : sb.toString().trim();
        return new ArrayList<>();
    }

    /** Click the suggestion that mentions {@code mustContain}; the item grid fills. Returns the suggestion's text, "" when none could be clicked. */
    public String pickSuggestion(String mustContain) {
        problems.clear();
        Object tagged = page.evaluate("(t) => {" + JS + " const s=[...document.querySelectorAll('li.auto-complete-item')].filter(vis);"
                + " const x=s.find(y=>norm(y.textContent).toLowerCase().indexOf(t.toLowerCase())>=0) || s[0]; if(!x) return ''; x.id='__mpaSug'; return norm(x.textContent); }", mustContain);
        lastSuggestion = tagged == null ? "" : tagged.toString();
        if (lastSuggestion.isEmpty()) { problems.add("there is no suggestion to click"); return ""; }
        try { page.locator("#__mpaSug").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mpaSug'); if(e) e.removeAttribute('id'); }"); }
        waitItemRows(8000);
        return lastSuggestion;
    }

    /** Click the dialog's Search (used when no suggestion appears); waits for the item grid. */
    public boolean clickSearch() {
        problems.clear();
        if (!clickByNg("CommonSearch", "Search", true)) return false;
        waitItemRows(10000);
        if (itemRows().isEmpty()) problems.add("Search listed no item" + (toastsNow().isEmpty() ? "" : " — the screen said \"" + toastsNow() + "\""));
        return ok();
    }

    /** The item grid's rows (the grid's body container only; the pinned selection column repeats every row). */
    public List<String> itemRows() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return []; return [...m.querySelectorAll('.ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')].map(x=>norm(x.textContent)).filter(Boolean); }");
        return toList(r);
    }

    private void waitItemRows(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && itemRows().isEmpty()) page.waitForTimeout(250);
        waitForAngular(400);
    }

    /**
     * Click the first item row (the click selects it and loads its batches). When the screen raises an alert (the item's selling price is below its purchase price) its text is kept in
     * {@link #lastAlert}. Returns the row's text, "" if there was no row to click.
     */
    public String clickItemRow() {
        problems.clear();
        lastAlert = "";
        Object t = page.evaluate("() => {" + JS + " const m=modal(); const r=m&&[...m.querySelectorAll('.ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')][0]; if(!r) return '';"
                + " const c=[...r.querySelectorAll('.ui-grid-cell')].find(x=>norm(x.textContent)) || r; c.id='__mpaRow'; return norm(r.textContent); }");
        lastItemRow = t == null ? "" : t.toString();
        if (lastItemRow.isEmpty()) { problems.add("the item grid has no row to click"); return ""; }
        try { page.locator("#__mpaRow").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the item row could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mpaRow'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 12 && lastAlert.isEmpty(); i++) { lastAlert = confirmText(); if (lastAlert.isEmpty()) page.waitForTimeout(250); }
        waitForAngular(500);
        return lastItemRow;
    }

    /** Answer the alert the row click raised (YES, else OK). Returns the label of the button clicked, "" when no alert was showing. */
    public String answerAlert() {
        problems.clear();
        lastAlertButton = answerDialog();
        waitBatchRows(8000);
        return lastAlertButton;
    }

    // ---- Items Batch List -> Selected Item -> OK -----------------------------------------------------------------

    /** The Items Batch List rows as cell lists: [checkbox cell, Date, Batch Code, Available Quantity, Unit Purchase Price, Conversion, Selling Price, Expiry Date]. */
    public List<String> batchRows() {
        Object r = page.evaluate("() => {" + JS + " const t=modalTable(BATCH); return t? [...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(Boolean) : []; }");
        return toList(r);
    }

    private void waitBatchRows(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && batchRows().isEmpty()) page.waitForTimeout(250);
        waitForAngular(400);
    }

    public List<String> selectedRows() {
        Object r = page.evaluate("() => {" + JS + " const t=modalTable(SELECTED); return t? [...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(Boolean) : []; }");
        return toList(r);
    }

    /**
     * Tick the checkbox of one Items Batch List row — the first batch that has stock (Available Quantity &gt; 0), else the first — and wait for the Selected Item table to fill.
     * Returns the batch row's text, "" when there is no batch.
     */
    public String tickBatchRow() {
        problems.clear();
        Object t = page.evaluate("() => {" + JS + " const t=modalTable(BATCH); if(!t) return ''; const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox]'));"
                + " if(!rows.length) return ''; const qty=r=>{ const c=[...r.querySelectorAll('td')].map(x=>norm(x.textContent)); return parseFloat(c[3]); };"
                + " const row=rows.find(r=>qty(r)>0) || rows[0]; const cb=row.querySelector('input[type=checkbox]'); cb.id='__mpaBatchCb';"
                + " return JSON.stringify({text:norm(row.textContent), batch:[...row.querySelectorAll('td')].map(x=>norm(x.textContent))[2]||'', checked:cb.checked}); }");
        if (t == null || t.toString().isEmpty()) { problems.add("Items Batch List has no row to tick"); return ""; }
        lastBatch = jsonField(t.toString(), "text");
        lastBatchCode = jsonField(t.toString(), "batch");
        boolean already = t.toString().contains("\"checked\":true");
        if (!already) {
            try { page.locator("#__mpaBatchCb").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { problems.add("the batch checkbox could not be clicked"); return ""; }
            finally { page.evaluate("() => { const e=document.getElementById('__mpaBatchCb'); if(e) e.removeAttribute('id'); }"); }
        } else page.evaluate("() => { const e=document.getElementById('__mpaBatchCb'); if(e) e.removeAttribute('id'); }");
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && selectedRows().isEmpty()) page.waitForTimeout(250);
        waitForAngular(400);
        if (selectedRows().isEmpty()) problems.add("ticking the batch did not fill the Selected Item table");
        return lastBatch;
    }

    /** Tick the Selected Item row's checkbox (only when it is not already ticked: clicking a ticked box would clear it). Returns the row's text. */
    public String tickSelectedItemRow() {
        problems.clear();
        Object t = page.evaluate("() => {" + JS + " const t=modalTable(SELECTED); const row=t&&[...t.querySelectorAll('tbody tr')].find(r=>r.querySelector('input[type=checkbox]')); if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); if(cb.checked) return 'ALREADY|'+norm(row.textContent); cb.id='__mpaSelCb'; return 'TICK|'+norm(row.textContent); }");
        String s = t == null ? "" : t.toString();
        if (s.isEmpty()) { problems.add("the Selected Item table has no row to tick"); return ""; }
        lastSelected = s.substring(s.indexOf('|') + 1);
        if (s.startsWith("TICK|")) {
            try { page.locator("#__mpaSelCb").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { problems.add("the Selected Item checkbox could not be clicked"); return ""; }
            finally { page.evaluate("() => { const e=document.getElementById('__mpaSelCb'); if(e) e.removeAttribute('id'); }"); }
        }
        waitForAngular(500);
        Object on = page.evaluate("() => {" + JS + " const t=modalTable(SELECTED); const cb=t&&t.querySelector('tbody tr input[type=checkbox]'); return !!(cb&&cb.checked); }");
        if (!Boolean.TRUE.equals(on)) problems.add("the Selected Item row did not stay ticked");
        return lastSelected;
    }

    /** Click OK; the dialog closes and the item reaches the form's grid. Returns true when it did. */
    public boolean clickOk() {
        problems.clear();
        if (!clickByNg("OKClick", "OK", true)) return false;
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end && (dialogOpen() || gridRowCount() == 0)) page.waitForTimeout(250);
        waitForAngular(800);
        if (dialogOpen()) problems.add("the dialog stayed open after OK" + (toastsNow().isEmpty() ? "" : " — the screen said \"" + toastsNow() + "\""));
        else if (gridRowCount() == 0) problems.add("OK closed the dialog but no item reached the grid");
        return ok();
    }

    private boolean dialogOpen() {
        Object r = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); return !!e && vis(e); }", CODE_BOX);
        return Boolean.TRUE.equals(r);
    }

    // ---- the form's grid: used quantity ----------------------------------------------------------------------------

    public int gridRowCount() {
        Object r = page.evaluate("(s) => {" + JS + " return [...document.querySelectorAll('table tbody tr')].filter(r=>vis(r) && r.querySelector(s)).length; }", QTY_BOX);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The first grid row's cells: [Sr. No., Item Name, Batch Code, Expiry Date, Available Stock, Used Quantity, Start Date, End Date, Selling Price, Total Amount, Remark]. */
    public List<String> gridCells() {
        Object r = page.evaluate("(s) => {" + JS + " const r=[...document.querySelectorAll('table tbody tr')].filter(x=>vis(x) && x.querySelector(s))[0]; if(!r) return [];"
                + " return [...r.querySelectorAll('td')].map(td=>{ const i=td.querySelector('input,textarea'); return i? i.value : norm(td.textContent).slice(0,80); }); }", QTY_BOX);
        List<String> cells = toList(r);
        lastGridRow = String.join(" | ", cells);
        if (cells.size() > 8) { lastAvailableStock = cells.get(4); lastSellingPrice = cells.get(8); }
        return cells;
    }

    /** The form's footer totals: "No. of Items 1, Total Amount 21.10". */
    public String totals() {
        Object r = page.evaluate("() => { const g=n=>{ const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? e.value : '?'; };"
                + " return 'No. of Items ' + g('MaterialConsumption.NoOFItems') + ', Total Amount ' + g('MaterialConsumption.TotalAmt'); }");
        lastTotals = r == null ? "" : r.toString();
        return lastTotals;
    }

    /** Enter the Used Quantity of the first grid row (typed, then Tab so the row's Total Amount recalculates). */
    public boolean enterUsedQuantity(String qty) {
        problems.clear();
        if (!typeInto(QTY_BOX, qty, "Used Quantity")) return false;
        page.locator(QTY_BOX).first().press("Tab");
        waitForAngular(800);
        lastQty = boxValue(QTY_BOX);
        if (!qty.equals(lastQty)) problems.add("the Used Quantity box shows \"" + lastQty + "\" instead of \"" + qty + "\"");
        Object held = page.evaluate("(s) => { const e=document.querySelector(s); try{ const v=angular.element(e).scope().$eval('Itm.AdjustmentQty'); return v==null?'':String(v); }catch(x){ return ''; } }", QTY_BOX);
        if (held == null || held.toString().isEmpty()) problems.add("the Used Quantity is not held by the model");
        gridCells();
        totals();
        return ok();
    }

    // ---- save ------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { window.__mpaToasts=[]; if(window.__mpaObs) window.__mpaObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__mpaToasts.includes(m)) window.__mpaToasts.push(m); });"
                + " window.__mpaObs=new MutationObserver(add); window.__mpaObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__mpaToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /**
     * Click Save, answer a confirmation if the screen asks for one (YES, else OK), and wait for the toast. Returns the toast text ("" if none appeared); {@link #lastDialog} holds the
     * confirmation's text (empty when Save went straight through).
     */
    public String saveAndGetToast() {
        problems.clear();
        lastDialog = lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Locator save = page.locator("button[ng-click^='IUDSave']").first();
        try { save.scrollIntoViewIfNeeded(); save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        for (int i = 0; i < 24; i++) {
            lastDialog = confirmText();
            if (!lastDialog.isEmpty()) break;
            if (!watchedToasts().isEmpty() && i > 4) break;
            page.waitForTimeout(250);
        }
        if (!lastDialog.isEmpty() && answerDialog().isEmpty()) problems.add("a dialog (\"" + lastDialog + "\") has no YES/OK button");
        for (int i = 0; i < 56; i++) {                                           // up to ~14 s for the toast
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(250);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = lastSaveId = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        lastSaveMessage = jsonField(c[3], "Message");
        Matcher id = Pattern.compile("\"ResponseId\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (id.find()) lastSaveId = id.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(200, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    private static String jsonField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : "";
    }

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
