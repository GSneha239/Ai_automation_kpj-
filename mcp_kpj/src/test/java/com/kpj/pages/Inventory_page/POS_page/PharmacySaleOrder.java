package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
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
 * Inventory &gt; POS &gt; <b>Pharmacy Sale Order/Consignment</b> — Page Object ({@code #/pharmacysaleorder}).
 *
 * <p>Confirmed live (devhis build v2026.10.26.291). One screen (the same {@code pharmacysale} scope as Pharmacy Sale/Dispense, but a different page: no Mark Visit
 * dialog, a mandatory <b>Ward/Room/Bed</b> text box, a Sale Order / Consignment switch):</p>
 * <ul>
 *   <li><b>Store*</b> ({@code pharmacysale.storeid}, select2; EMPTY by default, and the item search needs it).</li>
 *   <li><b>MRN No.</b> ({@code pharmacysale.mrno}) + the <b>Search</b> button ({@code #example1}, {@code SearchPatientByMRNo()}): the patient's Name, Age, Gender, Payor, mobile
 *       and address fill in. OPD is the default patient type. <b>Ward/Room/Bed*</b> ({@code pharmacysale.ward}) is a free-text box that stays empty and must be typed.</li>
 *   <li>Item strip: <b>Name</b> ({@code #txtServiceName}) is a TYPE-AHEAD ({@code li.auto-complete-item}, from {@code /api/GRN/fetchItemByStore}); clicking a suggestion fills the
 *       code and name and puts focus on <b>Quantity</b> ({@code #PSqty}); <b>Enter</b> adds the line to the <b>Counter Sale Information</b> grid (ui-grid, every cell an input:
 *       {@code row.entity.itemname / quantity / mrp / totalamount / discountpercentage / discount / taxpercentage / taxamount / netamount / OwnInstruction}) and clears the strip.</li>
 *   <li><b>Save</b> ({@code IUDSave()}) raises a confirmation ("PharmacySale:Details Do you want to Save Charges?", YES / NO); YES posts {@code /api/pharmacysale/IUDo}; on
 *       {@code ResultStatus 1} the toast "Item sale done successfully." shows and the whole form resets. This screen opens no report window.</li>
 * </ul>
 * Saving a sale is REAL: stock goes down and a sale order is created.
 */
public class PharmacySaleOrder extends BasePage {

    /** Every {@code fetchItemByStore} and {@code /api/pharmacysale/IUD*} response seen: {status, decoded url, body head}. */
    private final List<String[]> searchCalls = new ArrayList<>();
    private final List<String[]> saveCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            boolean search = u.contains("fetchItemByStore"), save = low.contains("/api/pharmacysale/iud");
            if (!search && !save) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (searchCalls) { (search ? searchCalls : saveCalls).add(rec); }
        } catch (Exception ignore) { }
    };

    public PharmacySaleOrder(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastMrn = "", lastPatient = "", lastAge = "", lastGender = "", lastPayor = "", lastWard = "", lastSuggestion = "", lastCode = "", lastName = "",
            lastQty = "", lastRow = "", lastDialog = "", lastToast = "", lastSaveMessage = "", lastSaveId = "", searchDiagnosis = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String STORE_SELECT = "select[ng-model='pharmacysale.storeid']";
    private static final String MRN_BOX = "input[ng-model='pharmacysale.mrno']";
    private static final String WARD_BOX = "input[ng-model='pharmacysale.ward']";

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("() => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()==='#/pharmacysaleorder'); }");
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
     * Inventory &gt; POS &gt; Pharmacy Sale Order/Consignment. Each group is opened ONCE and awaited: the menu builds each level lazily, a second click while it is still
     * building closes the group again, and a click on POS straight after Inventory is lost — so Inventory gets a settling pause before POS is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState("POS") < 0) {                                  // POS is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("POS") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                // let the Inventory level finish building before POS is clicked
                }
                if (groupState("POS") == 0 && !linkVisible()) clickGroup("POS");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("() => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/pharmacysaleorder'); if(l) l.id='__psoLink'; }");
                try { page.locator("#__psoLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PharmacySaleOrder.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__psoLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(m) => !!document.querySelector(m) && !!document.getElementById('PSqty')", MRN_BOX, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PharmacySaleOrder.nav: the MRN box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/pharmacysaleorder/?$"); }

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

    /** The selected option's text of a select (empty for the -Select- placeholder). */
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

    /** A visible confirmation dialog's text ("" if none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.jconfirm, .ng-confirm-box')].filter(vis).map(x=>norm(x.textContent).replace(/^[^A-Za-z0-9]+/,''));"
                + " return d.length? d[d.length-1] : ''; }");
        return r == null ? "" : r.toString();
    }

    private void installToastWatch() {
        page.evaluate("() => { window.__psoToasts=[]; if(window.__psoObs) window.__psoObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__psoToasts.includes(m)) window.__psoToasts.push(m); });"
                + " window.__psoObs=new MutationObserver(add); window.__psoObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__psoToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- store, patient, ward --------------------------------------------------------------------------------

    /** Choose the Store (empty by default; the item search is keyed by it). */
    public String selectStore(String want) {
        problems.clear();
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", STORE_SELECT, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()); if(!p) p=o.find(x=>x.t.toLowerCase().indexOf(a.w.toLowerCase())>=0); if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} } return p.t; }", Map.of("s", STORE_SELECT, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String m = model(STORE_SELECT, "pharmacysale.storeid");
        if (m.isEmpty() || "0".equals(m)) problems.add("the Store is not held by the model");
        return lastStore;
    }

    public boolean enterMrn(String mrn) {
        problems.clear();
        if (!typeInto(MRN_BOX, mrn, "MRN")) return false;
        waitForAngular(500);
        lastMrn = boxValue(MRN_BOX);
        if (!mrn.equals(lastMrn)) problems.add("the MRN box shows \"" + lastMrn + "\" instead of \"" + mrn + "\"");
        if (!mrn.equals(model(MRN_BOX, "pharmacysale.mrno"))) problems.add("the MRN is not held by the model");
        return ok();
    }

    /** Click Search and wait for the patient to load. Returns the patient's name, "" when none loaded. */
    public String clickSearch() {
        problems.clear();
        try { page.locator("#example1").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked"); return ""; }
        String name = "";
        for (int i = 0; i < 60 && name.isEmpty(); i++) {
            name = boxValue("input[ng-model='pharmacysale.firstname']");
            if (name.isEmpty()) {
                String d = confirmText();
                if (!d.isEmpty()) { problems.add("an unexpected confirmation appeared after Search: \"" + d + "\""); return ""; }
                page.waitForTimeout(250);
            }
        }
        waitForAngular(1000);
        lastPatient = name;
        lastAge = boxValue("input[ng-model='pharmacysale.year']") + "y " + boxValue("input[ng-model='pharmacysale.month']") + "m " + boxValue("input[ng-model='pharmacysale.day']") + "d";
        lastGender = selectedText("select[ng-model='pharmacysale.genderid']");
        lastPayor = selectedText("select[ng-model='pharmacysale.receivableid']");
        if (name.isEmpty()) problems.add("no patient loaded for MRN " + lastMrn + (toastsNow().isEmpty() ? "" : " — the screen said \"" + toastsNow() + "\""));
        return name;
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    public boolean enterWard(String ward) {
        problems.clear();
        if (!typeInto(WARD_BOX, ward, "Ward/Room/Bed")) return false;
        waitForAngular(500);
        lastWard = boxValue(WARD_BOX);
        if (!ward.equals(lastWard)) problems.add("the Ward/Room/Bed box shows \"" + lastWard + "\" instead of \"" + ward + "\"");
        if (!ward.equals(model(WARD_BOX, "pharmacysale.ward"))) problems.add("the Ward/Room/Bed is not held by the model");
        return ok();
    }

    // ---- item (type-ahead) -----------------------------------------------------------------------------------

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

    /** Click the first suggestion containing {@code mustContain}; verifies the code and name filled in and focus moved to Quantity. */
    public String pickSuggestion(String mustContain) {
        problems.clear();
        Object s = page.evaluate("(t) => {" + JS
                + " const li=[...document.querySelectorAll('li.auto-complete-item')].filter(vis).find(l=>norm(l.textContent).toLowerCase().indexOf(t.toLowerCase())>=0); if(!li) return '';"
                + " li.id='__psoSugg'; return norm(li.textContent); }", mustContain);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + mustContain + "\""); return ""; }
        try { page.locator("#__psoSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__psoSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastCode = boxValue("#txtItemCode");
        lastName = boxValue("#txtServiceName");
        if (lastCode.isEmpty()) problems.add("choosing the suggestion did not fill the item code");
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
        if (!qty.equals(model("#PSqty", "pharmacysale.quantity"))) problems.add("the Quantity is not held by the model");
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

    /** The lines of Counter Sale Information, each as property -> value (the grid cells are inputs bound to {@code row.entity.<property>}). */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> gridRows() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-canvas .ui-grid-row')].filter(vis).map(tr=>{ const o={};"
                + "   [...tr.querySelectorAll('input')].forEach(i=>{ const k=(i.getAttribute('ng-model')||'').replace(/^row\\.entity\\./,''); if(k) o[k]=i.value; });"
                + "   const first=tr.querySelector('.ui-grid-cell'); o['_srno']=norm(first? first.textContent : ''); return o; }); }");
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
     * After the add: Counter Sale Information carries one more line holding the item name, the quantity entered, a selling price, a total of about quantity x price, and
     * a net amount of total + tax - discount.
     */
    public boolean verifyLine(int linesBefore, String name, String qty) {
        problems.clear();
        List<Map<String, String>> rows = gridRows();
        lastRow = "";
        if (rows.size() != linesBefore + 1) problems.add("Counter Sale Information has " + rows.size() + " line(s), expected " + (linesBefore + 1));
        Map<String, String> row = null;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).getOrDefault("itemname", "").toLowerCase().contains(name.toLowerCase())) { row = rows.get(i); break; }
        if (row == null) { problems.add("no line carries the item \"" + name + "\"" + (rows.isEmpty() ? "" : " — the grid holds " + rows)); return false; }
        double q = num(row.get("quantity")), price = num(row.get("mrp")), total = num(row.get("totalamount")), net = num(row.get("netamount")),
                tax = num(row.get("taxamount")), disc = num(row.get("discount"));
        lastRow = row.getOrDefault("itemname", "") + " | qty " + row.getOrDefault("quantity", "") + " | selling price " + row.getOrDefault("mrp", "") + " | total " + row.getOrDefault("totalamount", "")
                + " | disc " + row.getOrDefault("discount", "") + " | tax " + row.getOrDefault("taxamount", "") + " | net " + row.getOrDefault("netamount", "");
        if (q != num(qty)) problems.add("the line's Quantity is \"" + row.getOrDefault("quantity", "") + "\" instead of " + qty);
        if (!(price > 0)) problems.add("the line's Selling Price is \"" + row.getOrDefault("mrp", "") + "\" (expected a positive price)");
        if (!(total > 0)) problems.add("the line's Total Amt is \"" + row.getOrDefault("totalamount", "") + "\" (expected a positive amount)");
        if (price > 0 && total > 0 && Math.abs(total - q * price) > 0.02) problems.add("the line's Total Amt " + total + " is not Quantity x Selling Price (" + q * price + ")");
        double d = Double.isNaN(disc) ? 0 : disc, x = Double.isNaN(tax) ? 0 : tax;
        if (!(net > 0)) problems.add("the line's Net Amt is \"" + row.getOrDefault("netamount", "") + "\" (expected a positive amount)");
        else if (total > 0 && Math.abs(net - (total + x - d)) > 0.02) problems.add("the line's Net Amt " + net + " is not Total + Tax - Discount (" + (total + x - d) + ")");
        return ok();
    }

    // ---- save ------------------------------------------------------------------------------------------------

    /** Click Save; returns the confirmation dialog's text, "" when none appeared (a validation message then sits in {@link #lastToast}). */
    public String clickSave() {
        problems.clear();
        lastDialog = "";
        lastToast = "";
        synchronized (searchCalls) { saveCalls.clear(); }
        installToastWatch();
        Locator save = page.locator("button[ng-click='IUDSave()']").first();
        try { save.scrollIntoViewIfNeeded(); save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        for (int i = 0; i < 32; i++) {
            lastDialog = confirmText();
            if (!lastDialog.isEmpty()) return lastDialog;
            if (!watchedToasts().isEmpty() && i > 6) break;
            page.waitForTimeout(250);
        }
        lastToast = watchedToasts();
        problems.add("Save raised no confirmation" + (lastToast.isEmpty() ? "" : "; the screen said \"" + lastToast + "\""));
        return "";
    }

    /** Click YES in the confirmation and wait for the toast (and the save call's answer). Returns the toast text. */
    public String confirmYes() {
        problems.clear();
        lastToast = "";
        synchronized (searchCalls) { saveCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('.jconfirm button, .ng-confirm-box button')].filter(vis).find(x=>/^yes$/i.test(norm(x.textContent)));"
                + " if(!b) return false; b.id='__psoYes'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("no YES button is on screen"); return ""; }
        try { page.locator("#__psoYes").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("YES could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__psoYes'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 48; i++) {                                       // up to ~12 s for the toast
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
        lastSaveMessage = "";
        lastSaveId = "";
        String[] c = null;
        synchronized (searchCalls) { if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1); }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[2]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        lastSaveMessage = jsonField(c[2], "Message");
        Matcher id = Pattern.compile("\"ResponseId\"\\s*:\\s*(-?\\d+)").matcher(c[2]);
        if (id.find()) lastSaveId = id.group(1);
        if (Integer.parseInt(c[0]) >= 400) lastSaveMessage = "HTTP " + c[0] + " " + lastSaveMessage;
    }

    /** The toast is a success message AND the server confirmed the sale (ResultStatus 1). */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = (t.contains("success") || t.contains("done") || t.contains("saved")) && !t.contains("error") && !t.contains("fail") && !t.contains("please");
        return toastOk && lastSaveStatus == 1;
    }
}
