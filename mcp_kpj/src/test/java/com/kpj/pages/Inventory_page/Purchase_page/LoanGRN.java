package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; Purchase &gt; <b>Loan GRN</b> — Page Object ({@code #/LoanGRNNew} → New → {@code #/add-addloangrnnew}).
 *
 * <p>Confirmed live (devhis build v2026.10.22.279). The <b>list</b> has From/To date (today), Store, Supplier, a Type pair (<b>Receive Loan In</b> —
 * the default — / Loan Return), an Item Name filter, <b>Search</b> ({@code SearchGRN()}) and <b>New</b> ({@code AddGRN()}). <b>New</b> opens the form
 * (the same controller shape as the ordinary GRN):</p>
 * <ul>
 *   <li>Header: <b>Supplier*</b> ({@code GRN.SupplierID}), <b>Store*</b> ({@code GRN.StoreID}), Invoice No. ({@code #InvoiceNoidfocus}, optional),
 *       Tax Type, Request Type, and the Receive Loan In / Loan Return radios ({@code GRN.GrnType}; Receive Loan In = 4). The two selects are
 *       select2-enhanced, so a choice is made on the underlying select with a change event and the select2 display synced.</li>
 *   <li><b>Item Search</b> strip: <b>Item Name</b> ({@code #txtServiceName}) is a SMART SEARCH — typing calls the server and lists
 *       {@code li.auto-complete-item} suggestions (code : name : unit : tax, available stock); clicking one fills the code, selling price and
 *       purchase rate and, for a batch-tracked item, ENABLES <b>Batch</b> and <b>Expiry Date</b> (both disabled until then).
 *       <b>Expiry Date</b> ({@code #txtexpirydate}, {@code MM/yy}) is a month/year DATEPICKER: typed characters are discarded, so a date is picked in
 *       its calendar (open it by clicking the box, open the year list, click a year, click a day — the box then reads {@code MM/yy}).
 *       <b>Quantity</b> ({@code #PSqty}): Enter adds the line to the <b>List of Items</b> grid ({@code gridOptionsaddgrn}) and clears the strip.</li>
 *   <li>The Batch box is not required by the screen (an empty batch passes the client validation; the server decides).</li>
 *   <li><b>Save</b> ({@code IUDSave()}) posts {@code /api/GRN/IUD}; on {@code ResultStatus 1} the toast reads "&lt;Message&gt; &lt;TransNo&gt;", the app opens the GRN
 *       report in a NEW TAB ({@code SQLReport/InventoryReport/frmInventoryReport.aspx?GrnID=..}) and returns to the list. {@code ResultStatus 2} means
 *       "invoice no already exist" (toast, form stays).</li>
 * </ul>
 */
public class LoanGRN extends BasePage {

    public LoanGRN(Page page) { super(page); }

    public String lastSupplier = "", lastStore = "", lastLoanType = "", lastItemCode = "", lastItemName = "", lastSuggestion = "", lastExpiry = "",
            lastQty = "", lastRow = "", lastSave = "", lastToast = "", lastGrnNo = "", lastSaveAnswer = "", lastReport = "";
    /** ResultStatus of the last {@code GRN/IUD} answer (-1 = no answer). 1 = saved, 2 = invoice no already exists. */
    public int lastResultStatus = -1;
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public boolean reportBlank = true;
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String GRID = "[ui-grid=\"gridOptionsaddgrn\"] .ui-grid-canvas .ui-grid-row";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Purchase &gt; Loan GRN. Only opens the groups that are shut; the menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 6 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/loangrnnew$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__lgLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const find=t=>[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t);"
                    + " const purchase=find('Purchase'); if(purchase){ purchase.click(); return 'purchase'; }"
                    + " const inv=find('Inventory'); if(inv){ inv.click(); return 'inventory'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                try { page.locator("#__lgLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("LoanGRN.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__lgLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddGRN/.test(b.getAttribute('ng-click')||'') && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("LoanGRN.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/loangrnnew/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-addloangrnnew"); }

    /** Click <b>New</b> ({@code AddGRN()}) and wait for the form. */
    public boolean clickNew() {
        if (!clickByHandler("AddGRN", null)) return false;
        try {
            page.waitForFunction("() => /add-addloangrnnew/i.test(location.hash) && document.getElementById('PSqty') && document.getElementById('txtServiceName')",
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
                + " if(!b) return ''; b.id='__lgBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("LoanGRN: no visible button for " + handlerRe); return false; }
        try { page.locator("#__lgBtn").scrollIntoViewIfNeeded(); page.locator("#__lgBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("LoanGRN: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__lgBtn'); if(e) e.removeAttribute('id'); }"); }
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

    // ---- header -------------------------------------------------------------------------------------------------

    public String selectSupplier() { problems.clear(); lastSupplier = choose("GRN.SupplierID", null, null, true); return lastSupplier; }

    /** Select the store: the exact name, else the first store with "pharmacy" in its name. */
    public String selectStore(String want) {
        problems.clear();
        lastStore = choose("GRN.StoreID", want, "pharmacy", false);
        waitForAngular(800);
        return lastStore;
    }

    /** The Receive Loan In / Loan Return radio that is ticked (the form opens on Receive Loan In). */
    public String loanType() {
        Object r = page.evaluate("() => {" + JS
                + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='GRN.GrnType']\")].find(x=>x.checked);"
                + " return r ? norm(r.parentElement.textContent) : ''; }");
        lastLoanType = r == null ? "" : r.toString();
        return lastLoanType;
    }

    // ---- item search ---------------------------------------------------------------------------------------------

    /**
     * Type {@code term} in <b>Item Name</b> (real key presses, so the smart search fires) and wait for its suggestion list. The first suggestion
     * whose text contains the term is clicked; the item code and prices fill in and (for a batch-tracked item) Batch / Expiry Date enable. Returns
     * the suggestion clicked ("" when none appeared / matched).
     */
    public String searchAndPickItem(String term) {
        problems.clear();
        Locator in = page.locator("#txtServiceName");
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("");
            in.pressSequentially(term, new Locator.PressSequentiallyOptions().setDelay(60));
        } catch (Exception e) { problems.add("the item name could not be typed"); return ""; }
        try {
            page.waitForFunction("() => document.querySelectorAll('li.auto-complete-item').length>0", null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { problems.add("no suggestion appeared for \"" + term + "\""); return ""; }
        waitForAngular(600);
        Object s = page.evaluate("(t) => {" + JS
                + " const lis=[...document.querySelectorAll('li.auto-complete-item')].filter(vis);"
                + " const li=lis.find(l=>norm(l.textContent).toLowerCase().indexOf(t.toLowerCase())>=0); if(!li) return '';"
                + " li.id='__lgSugg'; return norm(li.textContent); }", term);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + term + "\""); return ""; }
        try { page.locator("#__lgSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__lgSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastItemCode = model("#txtItemCode", "GRNDetails.itemcode");
        lastItemName = model("#txtServiceName", "GRNDetails.itemname");
        if (lastItemCode.isEmpty()) problems.add("choosing the suggestion did not fill the item code");
        return lastSuggestion;
    }

    // ---- expiry date (a month/year datepicker) -------------------------------------------------------------------

    /** True once the item has enabled the Expiry Date box. */
    public boolean expiryEnabled() {
        Object r = page.evaluate("() => { const e=document.getElementById('txtexpirydate'); return !!e && !e.disabled; }");
        return Boolean.TRUE.equals(r);
    }

    private boolean clickTagged(String js, String what) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const cal=document.getElementById('txtexpirydate').closest('.datepicker').querySelector('._720kb-datepicker-calendar');"
                + " const el=(" + js + ")(cal); if(!el) return ''; el.id='__lgCal'; return 'ok'; }", what);
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#__lgCal").click(new Locator.ClickOptions().setTimeout(5000)); return true; }
        catch (Exception e) { return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__lgCal'); if(e) e.removeAttribute('id'); }"); }
    }

    /**
     * Enter the expiry date the way the screen takes it: click the box (the calendar opens), open the year list, click the year, click a day.
     * The box then reads {@code MM/yy} (the month on show — the current one — and the chosen year). Returns the value ("" when it did not take).
     */
    public String pickExpiryDate(int year) {
        problems.clear();
        Locator box = page.locator("#txtexpirydate");
        try {
            box.scrollIntoViewIfNeeded();
            box.click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForFunction("() => !!document.querySelector('._720kb-datepicker-open')", null, new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the expiry calendar did not open"); return ""; }
        if (!clickTagged("(c)=>c.querySelector('a[ng-click*=\"showYearsPagination\"]')", "")) { problems.add("the year list could not be opened"); return ""; }
        waitForAngular(400);
        if (!clickTagged("(c)=>[...c.querySelectorAll('a[ng-click^=\"setNewYear\"]')].find(a=>norm(a.textContent)==='" + year + "' && !/disabled/.test(a.className))", "")) {
            problems.add("the year " + year + " is not offered by the calendar"); return "";
        }
        waitForAngular(400);
        if (!clickTagged("(c)=>[...c.querySelectorAll('a[ng-click^=\"setDatepickerDay\"]')].find(a=>norm(a.textContent)==='15' && !/disabled/.test(a.className))", "")) {
            problems.add("a day could not be clicked in the calendar"); return "";
        }
        waitForAngular(700);
        lastExpiry = model("#txtexpirydate", "GRNDetails.expirydate");
        String shown = String.valueOf(page.evaluate("() => document.getElementById('txtexpirydate').value"));
        if (!lastExpiry.matches("\\d{2}/\\d{2}")) problems.add("the expiry date box reads \"" + shown + "\" (model \"" + lastExpiry + "\")");
        else if (!lastExpiry.equals(shown)) problems.add("the expiry box shows \"" + shown + "\" but the model holds \"" + lastExpiry + "\"");
        return lastExpiry;
    }

    // ---- quantity + Enter -> the line ----------------------------------------------------------------------------

    public int itemRows() {
        Object n = page.evaluate("(g) => document.querySelectorAll(g).length", GRID);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Type the quantity and press Enter — that adds the line to the List of Items. Returns the grid's row count. */
    public int enterQuantityAndEnter(String qty) {
        problems.clear();
        int before = itemRows();
        try {
            Locator l = page.locator("#PSqty");
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(qty);
            lastQty = model("#PSqty", "GRNDetails.quantity");
            l.press("Enter");
        } catch (Exception e) { problems.add("the quantity could not be entered"); return before; }
        try {
            page.waitForFunction("(a) => document.querySelectorAll(a.g).length>a.n", Map.of("g", GRID, "n", before), new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("Enter in Quantity did not add a line to the List of Items"); }
        waitForAngular(800);
        int after = itemRows();
        lastRow = rowSummary();
        return after;
    }

    /** The first line as the grid data holds it: code, name, expiry, quantity, total. */
    public String rowSummary() {
        Object r = page.evaluate("() => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); const d=s.gridOptionsaddgrn.data[0]; if(!d) return '';"
                + " return d.itemcode+' '+d.itemname+' | exp '+d.expirydate+' | batch '+(d.batchcode||'-')+' | qty '+d.quantity+' @ '+d.rate+' = '+d.totalamount+' (net '+d.netamount+')'; }catch(e){ return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** True when the first grid line carries this expiry and quantity and a code and name, and the strip was cleared. */
    public boolean rowHolds(String expiry, String qty) {
        Object r = page.evaluate("(a) => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); const d=s.gridOptionsaddgrn.data[0]; if(!d) return false;"
                + " const strip=document.getElementById('txtServiceName').value;"
                + " return !!d.itemcode && !!d.itemname && d.expirydate===a.e && String(d.quantity)===a.q && strip===''; }catch(x){ return false; } }",
                Map.of("e", expiry, "q", qty));
        return Boolean.TRUE.equals(r);
    }

    /** Total / tax / net amount boxes of the form. */
    public String totals() {
        return "Total " + model("#PSqty", "GRN.totalamount") + ", Tax " + model("#PSqty", "GRN.totaltax") + ", Net " + model("#PSqty", "GRN.netamount");
    }

    // ---- Save + report --------------------------------------------------------------------------------------------

    /**
     * Records every toast to sessionStorage (so the record survives a page reload): the screen can reload / navigate right after a successful
     * Save, which destroys any in-page array and fails an {@code evaluate} that happens to run at that moment.
     */
    private static final String TOAST_RECORDER =
            "(function(){ try{ const KEY='__lgToasts';"
          + " const get=()=>{ try{ return JSON.parse(sessionStorage.getItem(KEY)||'[]'); }catch(e){ return []; } };"
          + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
          + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(!t) return;"
          + "   const a=get(); if(!a.includes(t)){ a.push(t); sessionStorage.setItem(KEY, JSON.stringify(a)); } }); };"
          + " const start=()=>{ if(window.__lgObs) return; window.__lgObs=new MutationObserver(grab); window.__lgObs.observe(document.documentElement,{childList:true,subtree:true}); grab(); };"
          + " if(document.documentElement) start(); else document.addEventListener('DOMContentLoaded', start); }catch(e){} })();";

    /** The toasts recorded so far; {@code null} when the page was mid-navigation (ask again). */
    private List<String> toastsSeen() {
        try {
            Object o = page.evaluate("() => { try{ return JSON.parse(sessionStorage.getItem('__lgToasts')||'[]'); }catch(e){ return []; } }");
            List<String> l = new ArrayList<>();
            if (o instanceof List) for (Object x : (List<?>) o) l.add(String.valueOf(x));
            return l;
        } catch (Exception e) { return null; }
    }

    private static boolean isAnswer(String t) {
        return t.toLowerCase().matches(".*(succe|added|saved|error|fail|please|invalid|required|already|exist).*");
    }

    /**
     * Click <b>Save</b> ({@code IUDSave()}). Captures the toast, the {@code GRN/IUD} answer (so a failure reports the server's own reason) and the
     * report tab the app opens, which is judged like any DevHIS report ({@link PdfReport}). Returns the toast.
     */
    public String clickSaveAndCapture() {
        lastToast = ""; lastGrnNo = ""; lastSaveAnswer = ""; lastReport = ""; reportBlank = true; reportPng = null; lastResultStatus = -1;
        final java.util.List<com.microsoft.playwright.Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> watcher = r -> {
            try { if (r.url().contains("GRN/IUD")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { try{ sessionStorage.removeItem('__lgToasts'); }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        page.addInitScript(TOAST_RECORDER);   // a document the save loads next is recorded too
        page.evaluate(TOAST_RECORDER);        // and the one on screen now
        int tabsBefore = page.context().pages().size();
        List<String> seen = new ArrayList<>();
        try {
            if (!clickByHandler("IUDSave", "^\\s*save\\s*$")) System.out.println("LoanGRN: the Save button was not found");
            for (int i = 0; i < 40; i++) {
                List<String> t = toastsSeen();
                if (t != null) { seen = t; if (seen.stream().anyMatch(LoanGRN::isAnswer)) break; }
                page.waitForTimeout(500);
            }
            lastSave = "clicked Save [IUDSave]";
            // the report opens in a new tab: wait until that tab carries the report's own address, then judge it like every other DevHIS report
            long end = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < end) {
                boolean found = false;
                for (Page pg : new ArrayList<>(page.context().pages())) {
                    if (pg == page) continue;
                    try { if (pg.url().contains("frmInventoryReport")) found = true; } catch (Exception ignore) { }
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
        String pick = seen.stream().filter(x -> x.toLowerCase().matches(".*(succe|added|saved).*")).findFirst()
                .orElse(seen.stream().filter(LoanGRN::isAnswer).findFirst().orElse(seen.isEmpty() ? "" : seen.get(0)));
        lastToast = pick.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (com.microsoft.playwright.Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            String shortBody = body.replaceAll("\\s+", " ");
            shortBody = shortBody.substring(0, Math.min(160, shortBody.length()));
            if (a.status() == 200) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) lastGrnNo = m.group(1);
                java.util.regex.Matcher st = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                if (st.find()) lastResultStatus = Integer.parseInt(st.group(1));
                lastSaveAnswer = "HTTP 200 " + shortBody;
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : shortBody);
            }
        }
        if (answers.isEmpty()) lastSaveAnswer = "no /api/GRN/IUD call was made (the screen's own validation stopped the Save)";
        // close the report tab so the main page is the one in front again
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        System.out.println("LoanGRN: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer + "; report: " + lastReport);
        return lastToast;
    }

    /** The save answered with a success message (and not an error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|added|saved).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }

    /** The server confirmed the save ({@code ResultStatus 1}). */
    public boolean savedOnServer() { return lastResultStatus == 1; }

    // ---- afterwards: the new GRN on the list -----------------------------------------------------------------------------

    /**
     * After a successful Save the app returns to the list. Search it (the dates default to today) and look for the new GRN number. Returns the
     * row's text ("" when it was not listed).
     */
    public String findInList(String grnNo) {
        if (grnNo == null || grnNo.isEmpty()) return "";
        try {
            page.waitForFunction("() => /loangrnnew$/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(1200);
        if (!clickByHandler("SearchGRN", "search")) return "";
        waitForAngular(2500);
        Object r = page.evaluate("(n) => {" + JS
                + " const row=[...document.querySelectorAll('.ui-grid-canvas .ui-grid-row')].find(x=>norm(x.textContent).indexOf(n)>=0);"
                + " return row ? norm(row.textContent) : ''; }", grnNo);
        return r == null ? "" : r.toString();
    }
}
