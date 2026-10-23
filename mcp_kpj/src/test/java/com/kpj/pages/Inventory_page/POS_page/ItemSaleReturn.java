package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PatientSearchPopup;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inventory &gt; POS &gt; <b>Item Sale Return</b> — Page Object ({@code #/ItemSaleReturn} list → New → {@code #/add-ItemSaleReturn}).
 *
 * <p>Confirmed live (devhis build v2026.10.22.279).</p>
 * <ul>
 *   <li><b>List</b>: From/To date, MRN, Store, Search ({@code GetSaleReturn()}), <b>New</b> ({@code AddItemsalereturn()}); rows {@code item in drpitemreturnlist}
 *       (Item Sale Return No, MRN, patient, … Total / Net Amount, Remark, Verify/Reject, Approve/Decline).</li>
 *   <li><b>New form</b>: <b>MRN No.</b> ({@code #newISRmrn}) + search ({@code SearchPatientByMRNo()}) or the shared Patient Search popup
 *       ({@code #btnSearchPatient}, {@code openPopupScreen()}), <b>Store*</b> ({@code itemsalereturn.storeid}, select2), <b>Get Sales</b> ({@code GetSale()}),
 *       <b>Remark*</b> ({@code itemsalereturn.remarks}), <b>Save</b> ({@code IUDSaleReturn()}).</li>
 *   <li><b>Get Sales</b> opens the <b>Sales Item Search</b> modal: From / To date ({@code ItemSale.fromdate/todate}, both default to today) and <b>Search</b>
 *       ({@code GetSale()} again). The dates are a 720kb DATEPICKER that discards typed characters — a date is picked in its calendar (month arrows, then a day).
 *       The upper table lists the patient's sales ({@code item in drpItemSale}, checkbox {@code item.isselected}); ticking one fills the lower table
 *       ({@code Itm in pharmacySaledetails}: code, unit, name, batch, Supply / Administered / <b>Balance</b> / Pending quantity, prices; checkbox {@code Itm.isselected});
 *       <b>OK</b> ({@code btnOK()}) closes the modal and copies the ticked line to the main table ({@code lst in drpitemsalereturnlist}).</li>
 *   <li>Save needs only the page <b>Remark</b>: the line's Return Quantity box stays blank but the whole balance is returned (a 2-unit line saved as 1.40 at 0.70);
 *       the server answers "Item sale return created successfully. Awaiting approval." ({@code TransNo} SALER…, {@code ResponseId} = bill id), the toast reads
 *       "Record Saved Successfully.", the app opens {@code SQLReport/InventoryReport/frmSalesReturnReport.aspx?BillID=..} in a NEW TAB and returns to the list. The return
 *       then waits for Verify / Approve — this page object never approves it.</li>
 * </ul>
 */
public class ItemSaleReturn extends BasePage {

    public ItemSaleReturn(Page page) { super(page); }

    public String lastMrn = "", lastStore = "", lastPatient = "", lastFrom = "", lastTo = "", lastSale = "", lastItem = "", lastMainRow = "", lastRemark = "",
            lastSave = "", lastToast = "", lastReturnNo = "", lastSaveAnswer = "", lastReport = "", lastListRow = "";
    public int lastResultStatus = -1;
    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public boolean reportBlank = true;
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String MRN_BOX = "#newISRmrn";
    private static final String MODAL = ".modal.in ";
    private static final String UPPER = "tr[ng-repeat='item in drpItemSale']";
    private static final String LOWER = "tr[ng-repeat='Itm in pharmacySaledetails']";
    private static final String MAIN = "tr[ng-repeat='lst in drpitemsalereturnlist']";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; POS &gt; Item Sale Return (the list). The menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 6 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/itemsalereturn$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__isrLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const find=t=>[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t);"
                    + " const pos=find('POS'); if(pos){ pos.click(); return 'pos'; }"
                    + " const inv=find('Inventory'); if(inv){ inv.click(); return 'inventory'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                try { page.locator("#__isrLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ItemSaleReturn.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__isrLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddItemsalereturn/.test(b.getAttribute('ng-click')||'') && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ItemSaleReturn.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/itemsalereturn/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-itemsalereturn"); }

    /** Click <b>New</b> ({@code AddItemsalereturn()}) and wait for the form. */
    public boolean clickNew() {
        if (!clickButton("AddItemsalereturn", null, false)) return false;
        try {
            page.waitForFunction("() => /add-itemsalereturn/i.test(location.hash) && document.getElementById('newISRmrn')", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm();
    }

    // ---- low-level helpers -----------------------------------------------------------------------------------

    /** Click a visible button by its ng-click; {@code inModal} limits it to the open modal, otherwise buttons inside a modal are ignored. */
    private boolean clickButton(String handlerRe, String textRe, boolean inModal) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).filter(x=>!!x.closest('.modal.in')===a.m)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__isrBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe, "m", inModal));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("ItemSaleReturn: no visible button for " + handlerRe); return false; }
        try { page.locator("#__isrBtn").scrollIntoViewIfNeeded(); page.locator("#__isrBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("ItemSaleReturn: click failed (" + handlerRe + ") - " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__isrBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }",
                Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    public void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    private String toastsNow() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('#toast-container .toast')].map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** Text of the ng-confirm / JAlert dialogs on screen (bottom first). */
    public List<String> confirms() {
        List<String> out = new ArrayList<>();
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.ng-confirm')].filter(vis).map(d=>norm((d.querySelector('.ng-confirm-content-pane')||d).textContent)); }");
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Dismiss any dialog with its OK / No / Close / Cancel button. */
    public void dismissConfirms() {
        for (int i = 0; i < 4 && !confirms().isEmpty(); i++) {
            Object t = page.evaluate("() => {" + JS
                    + " const boxes=[...document.querySelectorAll('.ng-confirm')].filter(vis);"
                    + " const hit=boxes.map(d=>[...d.querySelectorAll('button')].find(b=>/^\\s*(ok|no|close|cancel)\\s*$/i.test(norm(b.textContent)))).filter(Boolean);"
                    + " if(!hit.length) return ''; hit[hit.length-1].id='__isrDlg'; return 'ok'; }");
            if (t == null || t.toString().isEmpty()) break;
            try { page.locator("#__isrDlg").click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception e) { break; }
            finally { page.evaluate("() => { const e=document.getElementById('__isrDlg'); if(e) e.removeAttribute('id'); }"); }
            waitForAngular(600);
        }
    }

    private String sel(String ng) { return "select[ng-model='" + ng + "']"; }

    // ---- MRN, store, Get Sales -------------------------------------------------------------------------------------------

    /** MRNs of registered patients from the Patient Search popup beside the MRN box (nothing is hardcoded). */
    public List<String> candidateMrns(int target, int maxPages) {
        return new PatientSearchPopup(page).collectMrns("openPopupScreen", target, maxPages, true);
    }

    /** Type the MRN (a real fill) and click the search button beside it. */
    public String enterMrnAndSearch(String mrn) {
        problems.clear();
        lastMrn = mrn;
        clearToasts();
        try {
            Locator box = page.locator(MRN_BOX).first();
            box.scrollIntoViewIfNeeded();
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(mrn);
        } catch (Exception e) { problems.add("the MRN could not be typed"); return ""; }
        if (!clickButton("SearchPatientByMRNo\\(\\)", null, false)) { problems.add("the MRN search button was not found"); return ""; }
        waitForAngular(1500);
        String held = model(MRN_BOX, "itemsalereturn.mrno");
        if (!held.equals(mrn)) problems.add("the MRN box reads \"" + held + "\"");
        return held;
    }

    /** Select the Store: the exact name, else the first with "pharmacy" in it. */
    public String selectStore(String want) {
        problems.clear();
        String css = sel("itemsalereturn.storeid");
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", css, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the store list is empty"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " const p=o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) || o.find(x=>/pharmacy/i.test(x.t)); if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }", Map.of("s", css, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("no store \"" + want + "\""); return ""; }
        waitForAngular(1000);
        String id = model(css, "itemsalereturn.storeid");
        if (id.isEmpty() || "0".equals(id)) problems.add("the store is not held by the model");
        return lastStore;
    }

    public boolean storeSelected() {
        String id = model(sel("itemsalereturn.storeid"), "itemsalereturn.storeid");
        return !id.isEmpty() && !"0".equals(id);
    }

    public boolean salesPopupOpen() {
        Object r = page.evaluate("() => !![...document.querySelectorAll('.modal.in')].find(m=>(m.offsetWidth||m.offsetHeight) && m.querySelector(\"input[ng-model='ItemSale.fromdate']\"))");
        return Boolean.TRUE.equals(r);
    }

    /** Click <b>Get Sales</b> ({@code GetSale()}) and wait for the Sales Item Search popup. */
    public boolean clickGetSales() {
        problems.clear();
        clearToasts();
        if (!clickButton("GetSale\\(\\)", "get sales", false)) { problems.add("the Get Sales button was not found"); return false; }
        for (int i = 0; i < 12; i++) {
            if (salesPopupOpen()) { waitForAngular(700); lastPatient = patientLine(); return true; }
            page.waitForTimeout(400);
        }
        String why = toastsNow();
        List<String> c = confirms();
        problems.add("the Sales Item Search popup did not open" + (!why.isEmpty() ? " (" + why + ")" : !c.isEmpty() ? " (" + c.get(c.size() - 1) + ")" : ""));
        return false;
    }

    /** The patient banner at the top of the form ("Name : … MRN.No : …"), blank until the patient has loaded. */
    public String patientLine() {
        Object r = page.evaluate("() => {" + JS
                + " const e=[...document.querySelectorAll('*')].filter(vis).find(x=>x.children.length<4 && /^\\s*Name\\s*:/.test(x.textContent||'') && /MRN\\.No/.test(x.textContent||'') && (x.textContent||'').length<300);"
                + " return e ? norm(e.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Just the patient's name out of the banner read by {@link #clickGetSales()} ("Name : … MRN.No : …"). */
    public String patientName() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Name\\s*:\\s*(.*?)\\s*MRN\\.No").matcher(lastPatient);
        return m.find() ? m.group(1) : lastPatient;
    }

    // ---- the popup: dates ---------------------------------------------------------------------------------------------

    /**
     * Pick {@code target} in the date box {@code ng} of the popup, the way the datepicker takes it: click the box (its calendar opens), use the month arrows until
     * the calendar shows the target month, click the day. Returns the value the box then holds.
     */
    public String pickDate(String ng, LocalDate target) {
        problems.clear();
        String box = MODAL + "input[ng-model='" + ng + "']";
        try {
            Locator b = page.locator(box).first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForFunction("(s) => { const e=document.querySelector(s); const d=e && e.closest('.datepicker'); return !!d && !!d.querySelector('._720kb-datepicker-open'); }",
                    box, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the calendar of " + ng + " did not open"); return ""; }
        for (int i = 0; i < 40; i++) {
            Object diff = page.evaluate("(a) => {" + JS
                    + " const d=document.querySelector(a.s).closest('.datepicker'); const h=norm(d.querySelector('._720kb-datepicker-calendar-header-middle').textContent);"
                    + " const m=h.match(/([A-Za-z]+)\\s+(\\d{4})/); if(!m) return 999;"
                    + " const mi=['january','february','march','april','may','june','july','august','september','october','november','december'].indexOf(m[1].toLowerCase());"
                    + " return (a.y*12+a.m) - (parseInt(m[2])*12+mi); }", Map.of("s", box, "y", target.getYear(), "m", target.getMonthValue() - 1));
            int d = diff instanceof Number ? ((Number) diff).intValue() : 999;
            if (d == 0) break;
            if (d == 999) { problems.add("the calendar header could not be read"); return ""; }
            Object clicked = page.evaluate("(a) => { const dp=document.querySelector(a.s).closest('.datepicker');"
                    + " const arrow=dp.querySelector(a.d>0 ? 'a[ng-click=\"nextMonth()\"]' : 'a[ng-click=\"prevMonth()\"]'); if(!arrow) return false; arrow.click(); return true; }",
                    Map.of("s", box, "d", d));
            if (!Boolean.TRUE.equals(clicked)) { problems.add("the calendar has no month arrow"); return ""; }
            page.waitForTimeout(250);
        }
        Object ok = page.evaluate("(a) => { const dp=document.querySelector(a.s).closest('.datepicker');"
                + " const day=[...dp.querySelectorAll('a[ng-click^=\"setDatepickerDay\"]')].find(x=>(x.textContent||'').trim()===String(a.day) && !/disabled/.test(x.className));"
                + " if(!day) return false; day.id='__isrDay'; return true; }", Map.of("s", box, "day", target.getDayOfMonth()));
        if (!Boolean.TRUE.equals(ok)) { problems.add("day " + target.getDayOfMonth() + " is not selectable in the calendar"); return ""; }
        try { page.locator("#__isrDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__isrDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(500);
        String held = model(box, ng);
        String want = String.format("%02d/%02d/%04d", target.getDayOfMonth(), target.getMonthValue(), target.getYear());
        if (!held.equals(want)) problems.add(ng + " reads \"" + held + "\" instead of " + want);
        if (ng.endsWith("fromdate")) lastFrom = held; else lastTo = held;
        return held;
    }

    /** Click <b>Search</b> in the popup and wait for the answer; returns the sales listed in the upper table ("SALE… net amount"). */
    public List<String> clickSearchInPopup() {
        problems.clear();
        if (!clickButton("GetSale\\(\\)", "^\\s*search\\s*$", true)) { problems.add("the popup's Search button was not found"); return new ArrayList<>(); }
        waitForAngular(2500);
        return saleRows();
    }

    public List<String> saleRows() {
        List<String> out = new ArrayList<>();
        Object r = page.evaluate("(u) => {" + JS + " return [...document.querySelectorAll('.modal.in ' + u)].map(r=>norm(r.textContent)); }", UPPER);
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- the popup: the two ticks, OK ---------------------------------------------------------------------------------------

    /** Tick (a real click) the checkbox of the n-th sale in the upper table; returns true when it reads as ticked. */
    public boolean tickSale(int index) {
        problems.clear();
        try {
            Locator cb = page.locator(MODAL + UPPER + " input[type=checkbox]").nth(index);
            cb.scrollIntoViewIfNeeded();
            if (!cb.isChecked()) cb.click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the sale's checkbox could not be ticked"); return false; }
        waitForAngular(1800);
        Object on = page.evaluate("(a) => { const c=document.querySelectorAll('.modal.in ' + a.u + ' input[type=checkbox]')[a.i]; return !!c && c.checked; }", Map.of("u", UPPER, "i", index));
        if (!Boolean.TRUE.equals(on)) problems.add("the sale's tick did not stick");
        String row = saleRows().size() > index ? saleRows().get(index) : "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(SALE\\w+)").matcher(row);
        lastSale = m.find() ? m.group(1) : row;
        return ok();
    }

    public void untickSale(int index) {
        try {
            Locator cb = page.locator(MODAL + UPPER + " input[type=checkbox]").nth(index);
            if (cb.isChecked()) cb.click(new Locator.ClickOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        waitForAngular(700);
    }

    /** The lines of the lower table as "item | batch | supply / balance / pending" strings. */
    public List<String> itemRows() {
        List<String> out = new ArrayList<>();
        Object r = page.evaluate("(l) => {" + JS
                + " const rows=[...document.querySelectorAll('.modal.in ' + l)]; if(!rows.length) return [];"
                + " const th=[...rows[0].closest('table').querySelectorAll('thead th')].map(h=>norm(h.textContent));"
                + " const ix=re=>th.findIndex(t=>re.test(t));"
                + " const code=ix(/item code/i), name=ix(/item name/i), batch=ix(/batch/i), sup=ix(/supply/i), bal=ix(/balance/i), pen=ix(/pending/i);"
                + " return rows.map(r=>{ const c=[...r.querySelectorAll('td')].map(td=>norm(td.textContent));"
                + "   return c[code]+' '+c[name]+' | batch '+c[batch]+' | supply '+c[sup]+' / balance '+c[bal]+' / pending '+c[pen]; }); }", LOWER);
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The first lower-table line's Balance Quantity (0 when there is none). */
    public double firstBalance() {
        Object r = page.evaluate("(l) => {" + JS
                + " const row=document.querySelector('.modal.in ' + l); if(!row) return 0;"
                + " const th=[...row.closest('table').querySelectorAll('thead th')].map(h=>norm(h.textContent)); const ix=th.findIndex(t=>/balance/i.test(t));"
                + " const c=[...row.querySelectorAll('td')].map(td=>norm(td.textContent)); const v=parseFloat(c[ix]); return isNaN(v)?0:v; }", LOWER);
        return r instanceof Number ? ((Number) r).doubleValue() : 0;
    }

    /** Tick the checkbox of the (first) populated line of the lower table. */
    public boolean tickItem() {
        problems.clear();
        try {
            Locator cb = page.locator(MODAL + LOWER + " input[type=checkbox]").first();
            cb.scrollIntoViewIfNeeded();
            if (!cb.isChecked()) cb.click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the line's checkbox could not be ticked"); return false; }
        waitForAngular(700);
        Object on = page.evaluate("(l) => { const c=document.querySelector('.modal.in ' + l + ' input[type=checkbox]'); return !!c && c.checked; }", LOWER);
        if (!Boolean.TRUE.equals(on)) problems.add("the line's tick did not stick");
        lastItem = itemRows().isEmpty() ? "" : itemRows().get(0);
        return ok();
    }

    /** Click <b>OK</b> ({@code btnOK()}): the modal closes and the ticked line lands in the main table. Returns the number of lines there. */
    public int clickOk() {
        problems.clear();
        if (!clickButton("btnOK\\(\\)", null, true)) { problems.add("the OK button was not found"); return 0; }
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal.in')].some(m=>m.querySelector(\"input[ng-model='ItemSale.fromdate']\"))", null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("the popup did not close"); }
        waitForAngular(1200);
        Object n = page.evaluate("(m) => document.querySelectorAll(m).length", MAIN);
        int rows = n instanceof Number ? ((Number) n).intValue() : 0;
        lastMainRow = mainRow();
        if (rows == 0) problems.add("no line reached the main table" + (toastsNow().isEmpty() ? "" : " (" + toastsNow() + ")"));
        return rows;
    }

    /** The main table's first line: Sr, unit, item, batch, … and whether it is ticked. */
    public String mainRow() {
        Object r = page.evaluate("(m) => {" + JS
                + " const row=document.querySelector(m); if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); const t=norm(row.textContent).replace(/--Select--.*$/,'').trim();"
                + " return (cb&&cb.checked?'[x] ':'[ ] ') + t; }", MAIN);
        return r == null ? "" : r.toString();
    }

    public boolean mainRowTicked() { return mainRow().startsWith("[x]"); }

    /** Close the popup without choosing (Close button). */
    public void closePopup() {
        if (!clickButton("FnClose\\(\\)", "^\\s*close\\s*$", true)) clickButton("FnClose\\(\\)", null, true);
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal.in')].some(m=>m.querySelector(\"input[ng-model='ItemSale.fromdate']\"))", null, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception ignore) { }
        waitForAngular(500);
    }

    // ---- remark, Save -> report + toast --------------------------------------------------------------------------------------

    public String enterRemark(String text) {
        problems.clear();
        try {
            Locator l = page.locator("textarea[ng-model='itemsalereturn.remarks']").first();
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(text);
        } catch (Exception e) { problems.add("the remark could not be typed"); return ""; }
        waitForAngular(400);
        lastRemark = model("textarea[ng-model='itemsalereturn.remarks']", "itemsalereturn.remarks");
        if (!lastRemark.equals(text)) problems.add("the remark reads \"" + lastRemark + "\"");
        return lastRemark;
    }

    /** Records every toast to sessionStorage (it survives a reload): the screen can navigate right after a save. */
    private static final String TOAST_RECORDER =
            "(function(){ try{ const KEY='__isrToasts';"
          + " const get=()=>{ try{ return JSON.parse(sessionStorage.getItem(KEY)||'[]'); }catch(e){ return []; } };"
          + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
          + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(!t) return;"
          + "   const a=get(); if(!a.includes(t)){ a.push(t); sessionStorage.setItem(KEY, JSON.stringify(a)); } }); };"
          + " const start=()=>{ if(window.__isrObs) return; window.__isrObs=new MutationObserver(grab); window.__isrObs.observe(document.documentElement,{childList:true,subtree:true}); grab(); };"
          + " if(document.documentElement) start(); else document.addEventListener('DOMContentLoaded', start); }catch(e){} })();";

    private List<String> toastsSeen() {
        try {
            Object o = page.evaluate("() => { try{ return JSON.parse(sessionStorage.getItem('__isrToasts')||'[]'); }catch(e){ return []; } }");
            List<String> l = new ArrayList<>();
            if (o instanceof List) for (Object x : (List<?>) o) l.add(String.valueOf(x));
            return l;
        } catch (Exception e) { return null; }
    }

    private static boolean isAnswer(String t) {
        return t.toLowerCase().matches(".*(succe|saved|created|error|fail|please|invalid|required|already|exist).*");
    }

    /**
     * Click <b>Save</b> ({@code IUDSaleReturn()}). Captures the toast, the {@code itemsalereturn/IUD} answer (a failure reports the server's own reason) and the
     * report tab the app opens, judged like any DevHIS report ({@link PdfReport}). Returns the toast.
     */
    public String clickSaveAndCapture() {
        lastToast = ""; lastReturnNo = ""; lastSaveAnswer = ""; lastReport = ""; reportBlank = true; reportPng = null; lastResultStatus = -1;
        final java.util.List<com.microsoft.playwright.Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> watcher = r -> {
            try { if (r.url().toLowerCase().contains("itemsalereturn/iud")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { try{ sessionStorage.removeItem('__isrToasts'); }catch(e){} try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        page.addInitScript(TOAST_RECORDER);
        page.evaluate(TOAST_RECORDER);
        int tabsBefore = page.context().pages().size();
        List<String> seen = new ArrayList<>();
        try {
            if (!clickButton("IUDSaleReturn", "^\\s*save\\s*$", false)) System.out.println("ItemSaleReturn: the Save button was not found");
            for (int i = 0; i < 40; i++) {
                List<String> t = toastsSeen();
                if (t != null) { seen = t; if (seen.stream().anyMatch(ItemSaleReturn::isAnswer)) break; }
                page.waitForTimeout(500);
            }
            lastSave = "clicked Save [IUDSaleReturn]";
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
            try { waitForAngular(800); } catch (Exception ignore) { }
        } finally {
            page.offResponse(watcher);
        }
        for (int i = 0; i < 6; i++) { List<String> t = toastsSeen(); if (t != null) { seen = t; break; } page.waitForTimeout(500); }
        String pick = seen.stream().filter(x -> x.toLowerCase().matches(".*(succe|saved|created).*")).findFirst()
                .orElse(seen.stream().filter(ItemSaleReturn::isAnswer).findFirst().orElse(seen.isEmpty() ? "" : seen.get(0)));
        lastToast = pick.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (com.microsoft.playwright.Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            String shortBody = body.replaceAll("\\s+", " ");
            shortBody = shortBody.substring(0, Math.min(190, shortBody.length()));
            if (a.status() == 200) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) lastReturnNo = m.group(1);
                java.util.regex.Matcher st = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                if (st.find()) lastResultStatus = Integer.parseInt(st.group(1));
                lastSaveAnswer = "HTTP 200 " + shortBody;
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : shortBody);
            }
        }
        if (answers.isEmpty()) lastSaveAnswer = "no /api/itemsalereturn/IUD call was made (the screen's own validation stopped the Save)";
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        System.out.println("ItemSaleReturn: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer + "; report: " + lastReport);
        return lastToast;
    }

    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|saved|created).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }

    public boolean savedOnServer() { return lastResultStatus == 1; }

    // ---- afterwards: the return on the list ---------------------------------------------------------------------------------

    /** After Save the app returns to the list: Search (dates default to today) and return the row of the new return ("" when it is not listed). */
    public String findOnList(String returnNo) {
        if (returnNo == null || returnNo.isEmpty()) return "";
        try {
            page.waitForFunction("() => /#\\/itemsalereturn$/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(1200);
        if (!clickButton("GetSaleReturn\\(\\)", "^\\s*search\\s*$", false)) return "";
        waitForAngular(2500);
        Object r = page.evaluate("(n) => {" + JS
                + " const row=[...document.querySelectorAll(\"tr[ng-repeat^='item in drpitemreturnlist']\")].find(x=>norm(x.textContent).indexOf(n)>=0);"
                + " return row ? norm(row.textContent) : ''; }", returnNo);
        lastListRow = r == null ? "" : r.toString();
        return lastListRow;
    }
}
