package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PatientSearchPopup;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory &gt; POS &gt; <b>Consumable Sale</b> — Page Object ({@code #/PharmacyConsumableMaster}).
 *
 * <p>Confirmed live (devhis build v2026.10.26.291). Same family as Pharmacy Sale/Dispense, but its own model ({@code PharmacyConsumable.*}):</p>
 * <ul>
 *   <li><b>MRN No.</b> ({@code PharmacyConsumable.mrno}) + <b>Search</b> ({@code #example1}, {@code SearchPatientByMRNo()}) — or the Patient Search popup beside it. For an OPD
 *       patient with NO visit today the search raises an ng-confirm <i>"Today's Visit of this Patient is Not Mark Do you want to Mark Today's Visit with Previous Visit
 *       Data?"</i> (Yes / No; it can be stacked twice). <b>Yes</b> opens the <b>Mark Visit</b> modal ({@code #VisitMarkModal}: Department is PRE-SELECTED, so only
 *       <b>Mark Visit</b> {@code fnMarkVisit()} is needed); the patient then loads (name, age, gender, payor). A patient who already has a visit today loads with no dialog.</li>
 *   <li><b>Store*</b> ({@code PharmacyConsumable.storeid}, select2; empty by default — the item search and the Patient Search popup need it) and <b>Last Name*</b>
 *       ({@code PharmacyConsumable.lastname}, empty after the patient loads, mandatory).</li>
 *   <li>Item strip: <b>Code</b> ({@code #txtItemCode}, model {@code pharmacy.itemcode}) is typed; <b>Name</b> ({@code #txtServiceName}) is a TYPE-AHEAD
 *       ({@code li.auto-complete-item}, from {@code /api/GRN/fetchItemByStore}); clicking a suggestion fills the name and puts focus on <b>Quantity</b> ({@code #PSqty}); <b>Enter</b>
 *       adds the line to <b>Counter Sale Information</b> (ui-grid, every cell an input: {@code row.entity.itemname / batchcode / expirydate / quantity / mrp / totalamount /
 *       taxpercentage}).</li>
 *   <li><b>Save</b> ({@code IUDSave()}) raises a confirmation ("PharmacySale:Details Do you want to Save Charges?", stacked twice); YES posts {@code /api/pharmacysale/IUD}; on
 *       {@code ResultStatus 1} ("Item sale done successfully", {@code TransNo} SALE...) the toast shows, the screen goes back to the dashboard and the sale report
 *       ({@code SQLReport/InventoryReport/frmSaleDrugLabelReport.aspx?billheaderid=..}) opens in ANOTHER TAB.</li>
 * </ul>
 * Marking a visit and saving a sale are REAL: the patient's visit is created and stock goes down.
 */
public class ConsumableSale extends BasePage {

    /** What a patient search ended with. */
    public enum Outcome { MARK_VISIT_CONFIRM, OTHER_CONFIRM, LOADED, NOT_FOUND, NO_ANSWER }

    /** Every {@code fetchItemByStore} response seen: {status, decoded url, body head}. */
    private final List<String[]> searchCalls = new ArrayList<>();
    private final Consumer<Response> searchListener = r -> {
        try {
            String u = r.url();
            if (!u.contains("fetchItemByStore")) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            synchronized (searchCalls) {
                searchCalls.add(new String[]{String.valueOf(r.status()), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 700 ? body.substring(0, 700) : body});
            }
        } catch (Exception ignore) { }
    };

    public ConsumableSale(Page page) {
        super(page);
        page.onResponse(searchListener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastMrn = "", lastPatient = "", lastAge = "", lastGender = "", lastPayor = "", lastDialog = "", lastStore = "", storeToast = "", lastLastName = "", lastCode = "",
            lastName = "", lastSuggestion = "", lastQty = "", lastRow = "", lastSaveDialog = "", lastToast = "", lastSaleNo = "", lastSaveAnswer = "", lastReport = "", searchDiagnosis = "";
    /** True when the last name box was empty and had to be typed. */
    public boolean lastNameWasEmpty;
    /** How many confirmation dialogs were showing right after the search. */
    public int dialogsShown;
    public int lastResultStatus = -1;
    public boolean reportBlank = true;
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String MRN_BOX = "input[ng-model='PharmacyConsumable.mrno']";
    private static final String STORE_SELECT = "select[ng-model='PharmacyConsumable.storeid']";
    private static final String LAST_NAME_BOX = "input[ng-model='PharmacyConsumable.lastname']";

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("() => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()==='#/pharmacyconsumablemaster'); }");
        return Boolean.TRUE.equals(r);
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
     * Inventory &gt; POS &gt; Consumable Sale. Each group is opened ONCE and awaited: the menu builds each level lazily, a second click while it is still building closes the
     * group again, and a click on POS straight after Inventory is lost — so Inventory gets a settling pause before POS is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState("POS") < 0) {
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("POS") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (groupState("POS") == 0 && !linkVisible()) clickGroup("POS");
                for (int i = 0; i < 48 && !linkVisible(); i++) page.waitForTimeout(250);
            }
            if (linkVisible()) {
                page.evaluate("() => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/pharmacyconsumablemaster'); if(l) l.id='__cslLink'; }");
                try { page.locator("#__cslLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ConsumableSale.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__cslLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(m) => !!document.querySelector(m) && !!document.getElementById('PSqty')", MRN_BOX, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ConsumableSale.nav: the MRN box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/pharmacyconsumablemaster/?$"); }

    // ---- small helpers ---------------------------------------------------------------------------------------

    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }", Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String boxValue(String css) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", css);
        return v == null ? "" : v.toString().trim();
    }

    private String selectedText(String css) {
        Object v = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o? norm(o.textContent) : ''; return /^-*\\s*select\\s*-*$/i.test(t)? '' : t; }", css);
        return v == null ? "" : v.toString().trim();
    }

    public String activeId() {
        Object v = page.evaluate("() => { const e=document.activeElement; return e? (e.id||e.tagName) : ''; }");
        return v == null ? "" : v.toString();
    }

    private boolean typeInto(String css, String text, String what) {
        Locator in = page.locator(css).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(50));
            return true;
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
    }

    public void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- confirmation dialogs (ng-confirm) -------------------------------------------------------------------

    /** The text of every confirmation dialog on screen, bottom one first. */
    public List<String> confirms() {
        List<String> out = new ArrayList<>();
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ng-confirm')].filter(vis).map(d=>norm((d.querySelector('.ng-confirm-content-pane')||d).textContent)); }");
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click a button ({@code labelRe}) of the TOPMOST confirmation dialog (a stacked one underneath cannot be clicked). Returns its text, "" when none. */
    private String clickConfirmButton(String labelRe) {
        Object t = page.evaluate("(re) => {" + JS
                + " const boxes=[...document.querySelectorAll('.ng-confirm')].filter(vis);"
                + " const hit=boxes.map(d=>({d:d,b:[...d.querySelectorAll('button')].find(b=>new RegExp(re,'i').test(norm(b.textContent)))})).filter(x=>x.b);"
                + " if(!hit.length) return ''; const x=hit[hit.length-1]; x.b.id='__cslConfirm';"
                + " return norm((x.d.querySelector('.ng-confirm-content-pane')||x.d).textContent); }", labelRe);
        String text = t == null ? "" : t.toString();
        if (text.isEmpty()) return "";
        try { page.locator("#__cslConfirm").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("ConsumableSale: the confirmation button could not be clicked - " + e.getMessage().split("\n")[0]); text = ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__cslConfirm'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(900);
        return text;
    }

    /** Click <b>Yes</b> on the topmost confirmation dialog. Returns the dialog's text ("" when none was showing). */
    public String clickYes() { return clickConfirmButton("^\\s*yes\\s*$"); }

    /** Dismiss every confirmation on screen with its No / Ok / Close button (used for a candidate patient that is not wanted). */
    public void dismissConfirms() {
        for (int i = 0; i < 5 && !confirms().isEmpty(); i++) {
            if (clickConfirmButton("^\\s*(no|ok|close|cancel)\\s*$").isEmpty()) break;
        }
    }

    // ---- patient ---------------------------------------------------------------------------------------------

    public String patientName() {
        String f = boxValue("input[ng-model='PharmacyConsumable.firstname']"), l = boxValue(LAST_NAME_BOX);
        return (f + " " + l).trim();
    }

    /** MRNs of registered patients from the Patient Search popup beside the MRN box (needs a Store). */
    public List<String> candidateMrns(int target, int maxPages) {
        List<String> mrns = new ArrayList<>();
        for (int attempt = 0; attempt < 2 && mrns.isEmpty(); attempt++) {
            if (attempt > 0) waitForAngular(2500);
            // the popup lists only patients who visited TODAY by default, and those have no "visit not marked" dialog — untick Visit Date
            mrns = new PatientSearchPopup(page).collectMrns("OpenPatientSearchPopupScreen", target, maxPages, true);
        }
        return mrns;
    }

    /**
     * Type the MRN and click <b>Search</b>; wait for the screen to answer (a confirmation, or the patient's name), let it settle, then say what happened. Sets
     * {@link #dialogsShown} and {@link #lastDialog} when a confirmation appeared.
     */
    public Outcome searchMrn(String mrn) {
        problems.clear();
        lastMrn = mrn; lastDialog = ""; dialogsShown = 0;
        clearToasts();
        if (!typeInto(MRN_BOX, mrn, "MRN")) return Outcome.NO_ANSWER;
        if (!mrn.equals(boxValue(MRN_BOX))) problems.add("the MRN box shows \"" + boxValue(MRN_BOX) + "\" instead of \"" + mrn + "\"");
        try { page.locator("#example1").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked"); return Outcome.NO_ANSWER; }
        for (int i = 0; i < 80; i++) {
            if (!confirms().isEmpty()) break;
            if (!patientName().isEmpty()) break;
            if (toastsNow().toLowerCase().contains("not found")) break;
            page.waitForTimeout(250);
        }
        waitForAngular(2000);                       // the patient can fill in a moment BEFORE the confirmation appears
        List<String> c = confirms();
        dialogsShown = c.size();
        if (!c.isEmpty()) {
            lastDialog = c.get(c.size() - 1);
            return lastDialog.toLowerCase().contains("mark") && lastDialog.toLowerCase().contains("visit") ? Outcome.MARK_VISIT_CONFIRM : Outcome.OTHER_CONFIRM;
        }
        String toast = toastsNow();
        if (toast.toLowerCase().contains("not found")) { lastDialog = toast; return Outcome.NOT_FOUND; }
        recordPatient();
        return !lastPatient.isEmpty() && mrn.equals(model(MRN_BOX, "PharmacyConsumable.mrno")) ? Outcome.LOADED : Outcome.NO_ANSWER;
    }

    private void recordPatient() {
        lastPatient = patientName();
        lastAge = boxValue("input[ng-model='PharmacyConsumable.year']") + "y";
        lastGender = selectedText("select[ng-model='PharmacyConsumable.genderid']");
        lastPayor = selectedText("select[ng-model='PharmacyConsumable.receivableid']");
    }

    // ---- the Mark Visit modal --------------------------------------------------------------------------------

    public boolean markVisitOpen() {
        Object r = page.evaluate("() => { const m=document.getElementById('VisitMarkModal'); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean awaitMarkVisit(int ms) {
        try {
            page.waitForFunction("() => { const m=document.getElementById('VisitMarkModal'); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(ms));
            waitForAngular(700);
            return true;
        } catch (Exception e) { return false; }
    }

    /** What the Mark Visit modal currently holds (Department etc.) — shown in the report. */
    public String markVisitSummary() {
        Object r = page.evaluate("() => {" + JS + " const m=document.getElementById('VisitMarkModal'); if(!m) return '';"
                + " const s=ng=>{ const e=m.querySelector('select[ng-model=\"'+ng+'\"]'); const o=e&&e.options[e.selectedIndex]; return o? norm(o.textContent) : ''; };"
                + " return 'Department ' + s('PharmacyConsumable.DepartmentID') + ' | Patient source ' + s('PharmacyConsumable.PatientSourceID') + ' | Encounter type ' + s('PharmacyConsumable.EncounterTypeID'); }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>Mark Visit</b>, wait for the modal to close and the patient to load. Returns "" when it did, else what went wrong. */
    public String clickMarkVisit() {
        problems.clear();
        Locator btn = page.locator("#VisitMarkModal button[ng-click^='fnMarkVisit']").first();
        try { btn.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return "the Mark Visit button was not found"; }
        try {
            page.waitForFunction("() => { const m=document.getElementById('VisitMarkModal'); return !m || !m.classList.contains('in'); }", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return "the Mark Visit dialog did not close (" + toastsNow() + ")"; }
        waitForAngular(2500);
        List<String> c = confirms();
        if (!c.isEmpty()) return "a confirmation is on screen after Mark Visit: " + c.get(c.size() - 1);
        recordPatient();
        if (lastPatient.isEmpty()) return "the patient did not load after Mark Visit";
        return "";
    }

    // ---- store, last name ------------------------------------------------------------------------------------

    /** Choose the Store: the exact name, else the first containing it. Empty by default; the item search and the Patient Search popup need it. */
    public String selectStore(String want) {
        problems.clear();
        storeToast = "";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", STORE_SELECT, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        clearToasts();
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()); if(!p) p=o.find(x=>x.t.toLowerCase().indexOf(a.w.toLowerCase())>=0); if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} } return p.t; }", Map.of("s", STORE_SELECT, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(2000);
        storeToast = toastsNow();                    // the screen answers a store change with a toast ("Store sale no do not found!") — noted, not judged
        String m = model(STORE_SELECT, "PharmacyConsumable.storeid");
        if (m.isEmpty() || "0".equals(m)) problems.add("the Store is not held by the model");
        return lastStore;
    }

    public boolean storeSelected(String want) { return selectedText(STORE_SELECT).toLowerCase().contains(want.toLowerCase()); }

    /** Type the Last Name only if it is empty. Returns what was done: "" on a problem, else the value the box holds. */
    public String enterLastNameIfEmpty(String value) {
        problems.clear();
        String now = boxValue(LAST_NAME_BOX);
        lastNameWasEmpty = now.isEmpty();
        if (!lastNameWasEmpty) { lastLastName = now; return now; }
        if (!typeInto(LAST_NAME_BOX, value, "Last Name")) return "";
        waitForAngular(500);
        lastLastName = boxValue(LAST_NAME_BOX);
        if (!value.equals(lastLastName)) problems.add("the Last Name box shows \"" + lastLastName + "\" instead of \"" + value + "\"");
        if (!value.equals(model(LAST_NAME_BOX, "PharmacyConsumable.lastname"))) problems.add("the Last Name is not held by the model");
        return ok() ? lastLastName : "";
    }

    // ---- item ------------------------------------------------------------------------------------------------

    public boolean enterCode(String code) {
        problems.clear();
        if (!typeInto("#txtItemCode", code, "item code")) return false;
        waitForAngular(500);
        lastCode = boxValue("#txtItemCode");
        if (!code.equals(lastCode)) problems.add("the Code box shows \"" + lastCode + "\" instead of \"" + code + "\"");
        if (!code.equals(model("#txtItemCode", "pharmacy.itemcode"))) problems.add("the Code is not held by the model");
        return ok();
    }

    public boolean typeItemName(String term) {
        problems.clear();
        searchDiagnosis = "";
        synchronized (searchCalls) { searchCalls.clear(); }
        return typeInto("#txtServiceName", term, "item name");
    }

    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li.auto-complete-item')].filter(vis).map(l=>norm(l.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private String[] searchCall(String term, boolean failedOnly) {
        synchronized (searchCalls) {
            for (int i = searchCalls.size() - 1; i >= 0; i--) {
                String[] c = searchCalls.get(i);
                if (!c[1].contains("IN=" + term)) continue;
                if (failedOnly && Integer.parseInt(c[0]) < 400) continue;
                return c;
            }
        }
        return null;
    }

    private static String jsonField(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }

    /** "Wait for it to load": the suggestion list for the typed term. Fills {@link #searchDiagnosis} (and a problem) when none appears. */
    public List<String> awaitSuggestions(String term, int ms) {
        problems.clear();
        long start = System.currentTimeMillis();
        List<String> sugg = new ArrayList<>();
        while (System.currentTimeMillis() - start < ms) {
            sugg = suggestions();
            if (!sugg.isEmpty()) break;
            if (searchCall(term, true) != null && System.currentTimeMillis() - start > 3000) break;
            page.waitForTimeout(250);
        }
        if (sugg.isEmpty()) {
            String[] bad = searchCall(term, true), any = searchCall(term, false);
            if (bad != null) {
                String detail = jsonField(bad[2], "MessageDetail");
                if (detail.isEmpty()) detail = jsonField(bad[2], "Message");
                if (detail.length() > 200) detail = detail.substring(0, 200) + "…";
                searchDiagnosis = "the item search request (/api/GRN/fetchItemByStore) failed with HTTP " + bad[0] + (detail.isEmpty() ? "" : " — " + detail);
            } else if (any != null) searchDiagnosis = "the item search answered HTTP " + any[0] + " but offered no suggestion for \"" + term + "\" (does the selected Store hold it?)";
            else searchDiagnosis = "no item search request was made when \"" + term + "\" was typed (is a Store selected?)";
            problems.add(searchDiagnosis);
        }
        return sugg;
    }

    /** Click the first suggestion containing {@code mustContain}; verifies the name filled in and focus moved to Quantity. */
    public String pickSuggestion(String mustContain) {
        problems.clear();
        Object s = page.evaluate("(t) => {" + JS
                + " const li=[...document.querySelectorAll('li.auto-complete-item')].filter(vis).find(l=>norm(l.textContent).toLowerCase().indexOf(t.toLowerCase())>=0); if(!li) return '';"
                + " li.id='__cslSugg'; return norm(li.textContent); }", mustContain);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + mustContain + "\""); return ""; }
        try { page.locator("#__cslSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__cslSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastCode = boxValue("#txtItemCode");
        lastName = boxValue("#txtServiceName");
        if (lastName.isEmpty()) problems.add("choosing the suggestion did not fill the item name");
        if (!"PSqty".equals(activeId())) problems.add("focus did not move to Quantity (it is on " + activeId() + ")");
        return lastSuggestion;
    }

    // ---- quantity, add, the grid -----------------------------------------------------------------------------

    public boolean enterQuantity(String qty) {
        problems.clear();
        if (!typeInto("#PSqty", qty, "Quantity")) return false;
        waitForAngular(500);
        lastQty = boxValue("#PSqty");
        if (!qty.equals(lastQty)) problems.add("the Quantity box shows \"" + lastQty + "\" instead of \"" + qty + "\"");
        if (!qty.equals(model("#PSqty", "PharmacyConsumable.quantity"))) problems.add("the Quantity is not held by the model");
        return ok();
    }

    /** Enter in the Quantity box adds the line. Returns the number of lines in Counter Sale Information afterwards. */
    public int pressEnterToAdd(int linesBefore) {
        problems.clear();
        try { page.keyboard().press("Enter"); } catch (Exception e) { problems.add("Enter could not be pressed"); return linesBefore; }
        int n = linesBefore;
        for (int i = 0; i < 32; i++) {
            n = gridLines();
            if (n > linesBefore) break;
            page.waitForTimeout(250);
        }
        waitForAngular(800);
        if (n <= linesBefore) problems.add("no line was added to Counter Sale Information" + (toastsNow().isEmpty() ? "" : "; the screen said \"" + toastsNow() + "\""));
        return n;
    }

    /** The lines of Counter Sale Information, each as property -> value (the cells are inputs bound to {@code row.entity.<property>}). */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> gridRows() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-canvas .ui-grid-row')].filter(vis).map(tr=>{ const o={};"
                + "   [...tr.querySelectorAll('input')].forEach(i=>{ const k=(i.getAttribute('ng-model')||'').replace(/^row\\.entity\\./,''); if(k) o[k]=i.value; }); return o; }); }");
        List<Map<String, String>> out = new ArrayList<>();
        if (!(r instanceof List)) return out;
        for (Object o : (List<Object>) r) {
            Map<String, String> m = new LinkedHashMap<>();
            if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) m.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            out.add(m);
        }
        return out;
    }

    public int gridLines() { return gridRows().size(); }

    public void showGrid() {
        page.evaluate("() => { const g=document.querySelector('.ui-grid-canvas'); if(g) g.scrollIntoView({block:'center'}); }");
        waitForAngular(500);
    }

    private static double num(String s) {
        if (s == null) return Double.NaN;
        String t = s.replaceAll("[^0-9.\\-]", "");
        try { return t.isEmpty() ? Double.NaN : Double.parseDouble(t); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /**
     * After the add: Counter Sale Information carries one more line holding the item name, a batch code and expiry date, the quantity entered, a selling price and an
     * amount of about quantity x price; "No.of Items" counts the lines when the screen fills it in.
     */
    public boolean verifyLine(int linesBefore, String name, String qty) {
        problems.clear();
        List<Map<String, String>> rows = gridRows();
        lastRow = "";
        if (rows.size() != linesBefore + 1) problems.add("Counter Sale Information has " + rows.size() + " line(s), expected " + (linesBefore + 1));
        Map<String, String> row = null;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).getOrDefault("itemname", "").toLowerCase().contains(name.toLowerCase())) { row = rows.get(i); break; }
        if (row == null) { problems.add("no line carries the item \"" + name + "\"" + (rows.isEmpty() ? "" : " — the grid holds " + rows)); return false; }
        double q = num(row.get("quantity")), price = num(row.get("mrp")), amount = num(row.get("totalamount"));
        lastRow = row.getOrDefault("itemname", "") + " | batch " + row.getOrDefault("batchcode", "") + " | expiry " + row.getOrDefault("expirydate", "") + " | qty " + row.getOrDefault("quantity", "")
                + " | selling price " + row.getOrDefault("mrp", "") + " | amount " + row.getOrDefault("totalamount", "") + " | tax% " + row.getOrDefault("taxpercentage", "");
        if (row.getOrDefault("batchcode", "").isEmpty()) problems.add("the line has no Batch Code");
        if (row.getOrDefault("expirydate", "").isEmpty()) problems.add("the line has no Expiry Date");
        if (q != num(qty)) problems.add("the line's Quantity is \"" + row.getOrDefault("quantity", "") + "\" instead of " + qty);
        if (!(price > 0)) problems.add("the line's Selling Price is \"" + row.getOrDefault("mrp", "") + "\" (expected a positive price)");
        if (!(amount > 0)) problems.add("the line's Amount is \"" + row.getOrDefault("totalamount", "") + "\" (expected a positive amount)");
        else if (price > 0 && Math.abs(amount - q * price) > 0.02) problems.add("the line's Amount " + amount + " is not Quantity x Selling Price (" + q * price + ")");
        String items = boxValue("input[ng-model='PharmacyConsumable.noofitems']");
        if (!items.isEmpty() && num(items) != rows.size()) problems.add("\"No.of Items\" shows " + items + " for " + rows.size() + " line(s)");
        return ok();
    }

    // ---- save ------------------------------------------------------------------------------------------------

    /** Click <b>Save</b>. Returns the confirmation dialog's text ("" when none appeared). */
    public String clickSave() {
        problems.clear();
        clearToasts();
        lastSaveDialog = "";
        Locator save = page.locator("button[ng-click='IUDSave()']").first();
        try { save.scrollIntoViewIfNeeded(); save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        for (int i = 0; i < 16; i++) {
            List<String> c = confirms();
            if (!c.isEmpty()) { lastSaveDialog = c.get(c.size() - 1); return lastSaveDialog; }
            if (!toastsNow().isEmpty()) break;
            page.waitForTimeout(500);
        }
        problems.add("Save raised no confirmation" + (toastsNow().isEmpty() ? "" : "; the screen said \"" + toastsNow() + "\""));
        return "";
    }

    public String toastAfterSaveClick() { return toastsNow(); }

    /** Records every toast to sessionStorage (it survives a reload): the screen leaves for the dashboard right after a save, destroying any in-page record. */
    private static final String TOAST_RECORDER =
            "(function(){ try{ const KEY='__cslToasts';"
          + " const get=()=>{ try{ return JSON.parse(sessionStorage.getItem(KEY)||'[]'); }catch(e){ return []; } };"
          + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
          + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(!t) return;"
          + "   const a=get(); if(!a.includes(t)){ a.push(t); sessionStorage.setItem(KEY, JSON.stringify(a)); } }); };"
          + " const start=()=>{ if(window.__cslObs) return; window.__cslObs=new MutationObserver(grab); window.__cslObs.observe(document.documentElement,{childList:true,subtree:true}); grab(); };"
          + " if(document.documentElement) start(); else document.addEventListener('DOMContentLoaded', start); }catch(e){} })();";

    private List<String> toastsSeen() {
        try {
            Object o = page.evaluate("() => { try{ return JSON.parse(sessionStorage.getItem('__cslToasts')||'[]'); }catch(e){ return []; } }");
            List<String> l = new ArrayList<>();
            if (o instanceof List) for (Object x : (List<?>) o) l.add(String.valueOf(x));
            return l;
        } catch (Exception e) { return null; }
    }

    private static boolean isAnswer(String t) {
        return t.toLowerCase().matches(".*(succe|done|added|saved|error|fail|please|invalid|required|already|exist).*");
    }

    /**
     * Click <b>Yes</b> in the save confirmation (again while one is still stacked). Captures the toast, the {@code pharmacysale/IUD} answer (a failure reports the server's own
     * reason) and the report the app opens in another tab, judged like any DevHIS report ({@link PdfReport}). Returns the toast.
     */
    public String confirmSaveAndCapture() {
        lastToast = ""; lastSaleNo = ""; lastSaveAnswer = ""; lastReport = ""; reportBlank = true; reportPng = null; lastResultStatus = -1;
        final List<Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        Consumer<Response> watcher = r -> {
            try { if (r.url().toLowerCase().contains("pharmacysale/iud")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { try{ sessionStorage.removeItem('__cslToasts'); }catch(e){} try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        page.addInitScript(TOAST_RECORDER);
        page.evaluate(TOAST_RECORDER);
        int tabsBefore = page.context().pages().size();
        List<String> seen = new ArrayList<>();
        try {
            String yes = clickYes();
            if (yes.isEmpty()) System.out.println("ConsumableSale: no confirmation Yes was found");
            for (int i = 0; i < 4 && !confirms().isEmpty(); i++) clickYes();         // a stacked second confirmation
            for (int i = 0; i < 40; i++) {
                List<String> t = toastsSeen();
                if (t != null) { seen = t; if (seen.stream().anyMatch(ConsumableSale::isAnswer)) break; }
                page.waitForTimeout(500);
            }
            // the report opens in another tab: wait until it carries the report's own address, then judge it
            long end = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < end) {
                boolean found = false;
                for (Page pg : new ArrayList<>(page.context().pages())) {
                    if (pg == page) continue;
                    try { if (pg.url().toLowerCase().contains("report")) found = true; } catch (Exception ignore) { }
                }
                if (found) break;
                page.waitForTimeout(500);
            }
            PdfReport.Result rep = PdfReport.capture(page, tabsBefore, 20000);
            reportBlank = rep.blank; lastReport = rep.diagnostics; reportPng = rep.png;
            if (reportBlank) {
                String says = reportTabSays();
                if (!says.isEmpty()) lastReport += " | THE PAGE SAYS: \"" + says + "\"";
            }
            try { waitForAngular(800); } catch (Exception ignore) { }
        } finally {
            page.offResponse(watcher);
        }
        for (int i = 0; i < 6; i++) { List<String> t = toastsSeen(); if (t != null) { seen = t; break; } page.waitForTimeout(500); }
        String pick = seen.stream().filter(x -> x.toLowerCase().matches(".*(succe|done|added|saved).*")).findFirst()
                .orElse(seen.stream().filter(ConsumableSale::isAnswer).findFirst().orElse(seen.isEmpty() ? "" : seen.get(0)));
        lastToast = pick.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            String shortBody = body.replaceAll("\\s+", " ");
            shortBody = shortBody.substring(0, Math.min(170, shortBody.length()));
            if (a.status() == 200) {
                Matcher m = Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) lastSaleNo = m.group(1);
                Matcher st = Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                if (st.find()) lastResultStatus = Integer.parseInt(st.group(1));
                lastSaveAnswer = "HTTP 200 " + shortBody;
            } else {
                Matcher m = Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : shortBody);
            }
        }
        if (answers.isEmpty()) lastSaveAnswer = "no /api/pharmacysale/IUD call was made";
        // close the report tab so the main page is the one in front again
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        System.out.println("ConsumableSale: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer + "; report: " + lastReport);
        return lastToast;
    }

    /** What the report tab itself shows (an ASP.NET error page's title, else its first words) — "" when there is no other tab or it says nothing. */
    public String reportTabSays() {
        for (Page pg : new ArrayList<>(page.context().pages())) {
            if (pg == page || pg.isClosed()) continue;
            try {
                String title = String.valueOf(pg.title()).trim();
                Object body = pg.evaluate("() => (document.body ? (document.body.innerText||'') : '').replace(/\\s+/g,' ').trim().slice(0,240)");
                String says = !title.isEmpty() && !title.toLowerCase().startsWith("http") ? title : String.valueOf(body);
                return says == null || "null".equals(says) ? "" : says;
            } catch (Exception ignore) { }
            break;
        }
        return "";
    }

    /** The save answered with a success message (and not an error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|done|added|saved).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }

    /** The server confirmed the sale ({@code ResultStatus 1}). */
    public boolean savedOnServer() { return lastResultStatus == 1; }
}
