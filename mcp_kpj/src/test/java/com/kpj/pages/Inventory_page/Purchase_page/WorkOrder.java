package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inventory &gt; Purchase &gt; <b>Work Order</b> — Page Object ({@code #/InventoryWorkOrderList} → New → {@code #/add-InventoryWorkOrder}).
 *
 * <p>Confirmed live (devhis build v2026.10.22.279).</p>
 * <ul>
 *   <li><b>List</b>: From/To date (today), optional Store/Supplier, <b>Search</b> ({@code SearchWO()}), <b>New</b>
 *       ({@code AddNewInventoryWorkOrder()}). Nothing is listed until Search is clicked. The rows are
 *       {@code WO in InventoryWorkOrderListNEWLIST} (WO No, WO Date, Supplier Name, Cancel WO, Attachment); clicking a row's cell
 *       ({@code GetItemList(WO)}) fills the second table ({@code Itm in WOItemList}) with the order's lines.</li>
 *   <li><b>Form</b>: <b>Store*</b> ({@code InventoryWorkOrder.StoreID}), <b>Supplier*</b> ({@code .SupplierID}), Authorised By (defaults to
 *       the user), WO No (blank until saved), Date (today). Both selects are select2-enhanced, so a choice is made on the underlying
 *       select with a change event and the select2 display synced.</li>
 *   <li><b>Item strip</b>: <b>Code</b> ({@code .itemcode}), <b>Name</b> ({@code .itemname}) and <b>Quantity</b> ({@code #PSqty}) are PLAIN text
 *       boxes — there is no smart search and nothing is looked up: whatever is typed becomes the line. {@code AddDetails($event)} runs on
 *       the Quantity key-up: with a Name and a Quantity &gt; 0, <b>Enter</b> pushes the line to {@code ItemList} and clears the strip
 *       (with no Name it warns "Please select item first").</li>
 *   <li><b>Work Details</b> is a plain table ({@code Itm in ItemList}): Sr. No., Item Code, Item Name, then editable <b>Quantity</b>,
 *       UOM, <b>Price</b> ({@code Itm.rate}), Discount %, Tax %, Remark; Amount / Disc Amount / Tax Amount / Net Amount are computed
 *       by {@code RowTotal(Itm)} when a cell loses focus, and roll up into the Total / Disc / Tax / Net Amount boxes.</li>
 *   <li><b>Save</b> ({@code IUDWorkOrder()}) needs Store, Supplier, a Price and Quantity on every line and a non-zero Net Amount, then posts
 *       {@code /api/InventoryWorkOrder/IUD}; on {@code ResultStatus == 1} the toast reads "Record Saved Successfully" and the app returns to
 *       the list. There is no report.</li>
 * </ul>
 *
 * <p><b>Quirk worth knowing.</b> The Save button has no {@code type}, so it is the form's default submit button and EVERY Enter pressed in
 * a box of the form (Quantity, the row's Price) also runs {@code IUDWorkOrder()}. After the first Enter in Price the totals are still empty,
 * so that implicit Save only warns "Please Enter Net Amount!"; a SECOND Enter once the totals exist would really save. The page object
 * therefore presses Enter in Price exactly once and never again, and clears the stray warning before the real Save.</p>
 */
public class WorkOrder extends BasePage {

    public WorkOrder(Page page) { super(page); }

    public String lastStore = "", lastSupplier = "", lastCode = "", lastName = "", lastQty = "", lastPrice = "", lastRow = "", lastTotals = "",
            lastSave = "", lastToast = "", lastSaveAnswer = "", lastNewWo = "", lastWoLines = "";
    /** Toasts seen after an Enter (the implicit-submit warnings), for the report. */
    public String lastStrayToast = "";
    public boolean savedOnServer;
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE = "input[ng-model='InventoryWorkOrder.itemcode']";
    private static final String NAME = "input[ng-model='InventoryWorkOrder.itemname']";
    private static final String QTY = "#PSqty";
    private static final String ROW = "tr[ng-repeat='Itm in ItemList']";
    private static final String RATE = ROW + " input[ng-model='Itm.rate']";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Purchase &gt; Work Order. Only opens the groups that are shut; the menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 6 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/inventoryworkorderlist$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__woLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const find=t=>[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t);"
                    + " const purchase=find('Purchase'); if(purchase){ purchase.click(); return 'purchase'; }"
                    + " const inv=find('Inventory'); if(inv){ inv.click(); return 'inventory'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                try { page.locator("#__woLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("WorkOrder.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__woLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddNewInventoryWorkOrder/.test(b.getAttribute('ng-click')||'') && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("WorkOrder.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/inventoryworkorderlist/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-inventoryworkorder"); }

    /** Click <b>New</b> ({@code AddNewInventoryWorkOrder()}) and wait for the form. */
    public boolean clickNew() {
        if (!clickByHandler("AddNewInventoryWorkOrder", null)) return false;
        try {
            page.waitForFunction("() => /add-inventoryworkorder/i.test(location.hash) && document.getElementById('PSqty')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm();
    }

    // ---- low-level helpers -----------------------------------------------------------------------------------

    private boolean clickByHandler(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__woBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("WorkOrder: no visible button for " + handlerRe); return false; }
        try { page.locator("#__woBtn").scrollIntoViewIfNeeded(); page.locator("#__woBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("WorkOrder: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__woBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    /** An Angular expression evaluated in the scope of the element matching {@code css}. */
    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }",
                Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String sel(String ng) { return "select[ng-model='" + ng + "']"; }

    /**
     * Choose an option of a (select2-enhanced) select: set the underlying select, fire its change event and sync the select2 display.
     * {@code want} matches the option text exactly (case-insensitive); else the first option matching {@code fallbackRe} (when given); else the
     * first real option when {@code anyIfMissing}. Returns the option text; the model is checked afterwards.
     */
    private String choose(String ng, String want, String fallbackRe, boolean anyIfMissing) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", sel(ng), new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add(ng + " has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.f) p=o.find(x=>new RegExp(a.f,'i').test(x.t));"
                + " if(!p && a.any) p=o[0]; if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }", Map.of("s", sel(ng), "w", want == null ? "" : want, "f", fallbackRe == null ? "" : fallbackRe, "any", anyIfMissing));
        String chosen = t == null ? "" : t.toString();
        if (chosen.isEmpty()) { problems.add(ng + " has no option" + (want == null ? "" : " \"" + want + "\"")); return ""; }
        waitForAngular(1000);
        if (model(sel(ng), ng).isEmpty() || "0".equals(model(sel(ng), ng))) problems.add(ng + " is not held by the model");
        return chosen;
    }

    /** Remove every toast on screen so the next one read is the one the next action raised. */
    public void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    /** The toasts on screen now (text only). */
    private String toastsNow() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('#toast-container .toast')].map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- the list: what exists before / after -----------------------------------------------------------------------

    /** Click <b>Search</b> ({@code SearchWO()}; the dates default to today) and return the WO numbers listed, in order. */
    public Set<String> searchList() {
        Set<String> numbers = new LinkedHashSet<>();
        if (!onList()) return numbers;
        clearToasts();
        if (!clickByHandler("SearchWO", "search")) return numbers;
        waitForAngular(2500);
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"tr[ng-repeat='WO in InventoryWorkOrderListNEWLIST']\")].map(r=>{ const c=r.querySelector('td'); return c? norm(c.textContent):''; }).filter(Boolean); }");
        if (r instanceof List) for (Object o : (List<?>) r) numbers.add(String.valueOf(o));
        return numbers;
    }

    // ---- header -------------------------------------------------------------------------------------------------

    /** Select the store: the exact name, else the first store with "pharmacy" in its name. */
    public String selectStore(String want) {
        problems.clear();
        lastStore = choose("InventoryWorkOrder.StoreID", want, "pharmacy", false);
        waitForAngular(800);
        return lastStore;
    }

    public String selectSupplier() { problems.clear(); lastSupplier = choose("InventoryWorkOrder.SupplierID", null, null, true); return lastSupplier; }

    // ---- the item strip ------------------------------------------------------------------------------------------

    private String typeInto(String css, String text, String expr) {
        problems.clear();
        try {
            Locator l = page.locator(css).first();
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(text);
        } catch (Exception e) { problems.add("could not type into " + css); return ""; }
        waitForAngular(400);
        String held = model(css, expr);
        if (!held.equals(text)) problems.add("the box reads \"" + held + "\" instead of \"" + text + "\"");
        return held;
    }

    public String enterCode(String code) { lastCode = typeInto(CODE, code, "InventoryWorkOrder.itemcode"); return lastCode; }

    public String enterName(String name) { lastName = typeInto(NAME, name, "InventoryWorkOrder.itemname"); return lastName; }

    /** Type the quantity (no Enter yet). */
    public String enterQuantity(String qty) { lastQty = typeInto(QTY, qty, "InventoryWorkOrder.quantity"); return lastQty; }

    /** How many lines Work Details shows. */
    public int rows() {
        Object n = page.evaluate("(r) => document.querySelectorAll(r).length", ROW);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Press Enter in Quantity ({@code AddDetails}) and wait for the line to appear in Work Details. Returns the number of lines. Any warning the
     * Enter also raised (it is a form-wide implicit Save) is kept in {@link #lastStrayToast}.
     */
    public int pressEnterInQuantity() {
        problems.clear();
        int before = rows();
        clearToasts();
        try { page.locator(QTY).first().press("Enter"); }
        catch (Exception e) { problems.add("Enter could not be pressed in Quantity"); return before; }
        try {
            page.waitForFunction("(a) => document.querySelectorAll(a.r).length>a.n", Map.of("r", ROW, "n", before), new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Enter in Quantity did not add a line to Work Details"); }
        waitForAngular(700);
        lastStrayToast = toastsNow();
        lastRow = rowSummary();
        return rows();
    }

    /** The first line as Work Details shows it: code, name, quantity, price, amount, net amount. */
    public String rowSummary() {
        Object r = page.evaluate("(r) => {" + JS
                + " const tr=document.querySelector(r); if(!tr) return '';"
                + " const c=[...tr.querySelectorAll('td')].map(td=>{ const i=td.querySelector('input'); return i? i.value : norm(td.textContent); });"
                + " return 'Sr '+c[0]+' | code '+c[1]+' | name '+c[2]+' | qty '+c[3]+' | price '+(c[5]||'-')+' | amount '+(c[6]||'-')+' | net '+(c[11]||'-'); }", ROW);
        return r == null ? "" : r.toString();
    }

    /** True when the first line shows exactly this code and name and quantity. */
    public boolean rowHolds(String code, String name, String qty) {
        Object r = page.evaluate("(a) => {" + JS
                + " const tr=document.querySelector(a.r); if(!tr) return false;"
                + " const c=[...tr.querySelectorAll('td')].map(td=>{ const i=td.querySelector('input'); return i? i.value : norm(td.textContent); });"
                + " return c[1]===a.c && c[2]===a.n && String(c[3])===a.q; }", Map.of("r", ROW, "c", code, "n", name, "q", qty));
        return Boolean.TRUE.equals(r);
    }

    // ---- the line's price ---------------------------------------------------------------------------------------

    /**
     * Type the price in the line's <b>Price</b> cell and press Enter — once (see the class note: a second Enter would save). The line's
     * amounts compute when the cell loses focus; if Enter did not move focus the cell is left by clicking the Work Details heading.
     * Returns the line summary.
     */
    public String enterPriceAndEnter(String price) {
        problems.clear();
        clearToasts();
        try {
            Locator l = page.locator(RATE).first();
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(price);
            l.press("Enter");
        } catch (Exception e) { problems.add("the price could not be entered"); return ""; }
        waitForAngular(900);
        lastStrayToast = toastsNow();
        // amounts compute on blur: Enter normally moves focus away; otherwise leave the cell without pressing anything
        if (amount().isEmpty()) {
            try { page.locator("h5:has-text('Work Details'), .box-title:has-text('Work Details'), :text-is('Work Details')").first().click(new Locator.ClickOptions().setTimeout(3000)); }
            catch (Exception ignore) { page.evaluate("() => { if(document.activeElement) document.activeElement.blur(); }"); }
            waitForAngular(700);
        }
        lastPrice = page.locator(RATE).first().inputValue();
        lastRow = rowSummary();
        lastTotals = totals();
        if (!price.equals(lastPrice)) problems.add("the price cell reads \"" + lastPrice + "\"");
        if (amount().isEmpty()) problems.add("the line's amount was not calculated");
        return lastRow;
    }

    /** The line's computed Amount cell (blank until the price has been left). */
    public String amount() {
        Object r = page.evaluate("(r) => { const tr=document.querySelector(r); if(!tr) return ''; const tds=tr.querySelectorAll('td'); return tds[6]? (tds[6].textContent||'').trim() : ''; }", ROW);
        return r == null ? "" : r.toString();
    }

    /** Total / Disc / Tax / Net Amount boxes of the form. */
    public String totals() {
        return "Total " + model(QTY, "InventoryWorkOrder.TotalAmount") + ", Disc " + model(QTY, "InventoryWorkOrder.DiscountAmount")
                + ", Tax " + model(QTY, "InventoryWorkOrder.TaxAmount") + ", Net " + model(QTY, "InventoryWorkOrder.NetAmount");
    }

    public String netAmount() { return model(QTY, "InventoryWorkOrder.NetAmount"); }

    // ---- Save ----------------------------------------------------------------------------------------------------

    /**
     * Click <b>Save</b> ({@code IUDWorkOrder()}). Captures the toast and the {@code /api/InventoryWorkOrder/IUD} answer (so a failure reports the
     * server's own reason). Stray toasts from the earlier Enters are cleared first — a full-width warning would otherwise cover the button and
     * be mistaken for the answer. Returns the toast.
     */
    public String clickSaveAndCapture() {
        lastToast = ""; lastSaveAnswer = ""; lastSave = ""; savedOnServer = false;
        final List<com.microsoft.playwright.Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> watcher = r -> {
            try { if (r.url().toLowerCase().contains("inventoryworkorder/iud")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { window.__woToasts=[]; if(window.__woObs) window.__woObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__woToasts.includes(t)) window.__woToasts.push(t); }); };"
                + " window.__woObs=new MutationObserver(grab); window.__woObs.observe(document.body,{childList:true,subtree:true}); }");
        try {
            if (!clickByHandler("IUDWorkOrder", "^\\s*save\\s*$")) System.out.println("WorkOrder: the Save button was not found");
            for (int i = 0; i < 30; i++) {
                page.evaluate("() => {" + JS
                        + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                        + "   const t=norm(el.textContent); window.__woToasts=window.__woToasts||[];"
                        + "   if(t && !window.__woToasts.includes(t)) window.__woToasts.push(t); }); }");
                boolean got = Boolean.TRUE.equals(page.evaluate(
                        "() => (window.__woToasts||[]).some(a=>/succe|saved|error|fail|please|invalid|required|already|exist/i.test(a))"));
                if (got) break;
                page.waitForTimeout(500);
            }
            lastSave = "clicked Save [IUDWorkOrder]";
            waitForAngular(1500);
        } finally {
            page.offResponse(watcher);
        }
        Object r = page.evaluate("() => { const a=window.__woToasts||[];"
                + " return a.find(x=>/succe|saved/i.test(x)) || a.find(x=>/error|fail|please|invalid|required|already|exist/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (com.microsoft.playwright.Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            String shortBody = body.replaceAll("\\s+", " ");
            shortBody = shortBody.substring(0, Math.min(160, shortBody.length()));
            if (a.status() == 200) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                if (m.find() && "1".equals(m.group(1))) savedOnServer = true;
                lastSaveAnswer = "HTTP 200 " + shortBody;
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : shortBody);
            }
        }
        if (answers.isEmpty()) lastSaveAnswer = "no /api/InventoryWorkOrder/IUD call was made (the screen's own validation stopped the Save)";
        System.out.println("WorkOrder: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer);
        return lastToast;
    }

    /** The save answered with a success message (and not an error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|saved).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }

    // ---- afterwards: the new work order on the list ---------------------------------------------------------------------

    /**
     * After Save the app returns to the list. Search it again; the WO number that was not listed before is the new order. Its row is opened
     * ({@code GetItemList}) and the lines read back. Returns the new WO number ("" when none appeared).
     */
    public String findNewWorkOrder(Set<String> before) {
        lastNewWo = ""; lastWoLines = "";
        try {
            page.waitForFunction("() => /inventoryworkorderlist/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Set<String> after = searchList();
        for (String n : after) if (before == null || !before.contains(n)) { lastNewWo = n; break; }
        if (lastNewWo.isEmpty()) return "";
        page.evaluate("(n) => { const rows=[...document.querySelectorAll(\"tr[ng-repeat='WO in InventoryWorkOrderListNEWLIST']\")];"
                + " const r=rows.find(x=>((x.querySelector('td')||{}).textContent||'').trim()===n); if(r){ const c=r.querySelector('td[ng-click]'); if(c) c.click(); } }", lastNewWo);
        waitForAngular(1800);
        Object lines = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"tr[ng-repeat='Itm in WOItemList']\")].map(r=>norm(r.textContent)).join(' || '); }");
        lastWoLines = lines == null ? "" : lines.toString();
        return lastNewWo;
    }
}
