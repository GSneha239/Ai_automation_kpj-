package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; Purchase &gt; <b>GRN</b> — Page Object ({@code #/GRNList} → New → {@code #/add-GRN}).
 *
 * <p>Confirmed live (devhis build v2026.10.21.269). <b>New</b> ({@code AddGRN()}) opens one form:</p>
 * <ul>
 *   <li>Header: <b>Store*</b> ({@code GRN.StoreID}), <b>Supplier*</b> ({@code GRN.SupplierID}), Invoice Date* (today),
 *       <b>Invoice No.*</b> ({@code #InvoiceNoidfocus}), <b>PR Type*</b> ({@code GRN.PRType}: Inventory, Non-Inventory,
 *       Fixed Asset, Consignment, F&amp;B), Request Type (Material/Services), Direct Purchase / Purchase Order. The
 *       dropdowns are select2-enhanced, so a choice is made on the underlying select with a change event and the select2
 *       display synced.</li>
 *   <li>Item Search strip: <b>Item Name</b> ({@code #txtServiceName}) is a SMART SEARCH — typing calls
 *       {@code GRN/fetchItembystoreForPR} and shows a list ({@code li.auto-complete-item}: code, name, UOM, tax, available
 *       stock) from which one is clicked, filling the code and prices. <b>Quantity</b> ({@code #PSqty}): Enter moves to
 *       <b>FOC</b> ({@code #FreeQtyid}); Enter THERE ({@code AddGRNDetails}) adds the line to the <b>List of Items</b> grid
 *       ({@code gridOptionsaddgrn}) and clears the strip.</li>
 *   <li>The line carries its own editable cells: <b>Batch Number</b> ({@code row.entity.batchcode}, a mass-autocomplete
 *       that also takes a new batch), <b>Exp Date</b> ({@code row.entity.expirydate}) — BOTH mandatory when the item needs a
 *       batch (the validation-engine balloon says "This field is required", and the controller adds "Please Enter Expiry Date
 *       For Item …") — and the line's only checkbox, <b>Returnable</b> ({@code row.entity.isreturnable}).</li>
 *   <li><b>Save</b> ({@code IUDSave()}) posts {@code GRN/IUD}; on success the toast reads "GRN added Successfully
 *       &lt;GRN no&gt;." and the app opens the GRN report in a NEW TAB ({@code SQLReport/InventoryReport/frmInventoryReport.aspx?GrnID=..})
 *       and returns to the list.</li>
 * </ul>
 *
 * <p><b>PR Type matters on this environment.</b> Each type draws its GRN number from its own series, and the DIRECT-GRN
 * series of Pharmacy Main Store ("Inventory", prefix PGR) is stuck: the server keeps generating PGR260000104, which exists
 * (the series runs to PGR260000113), so every save fails with "Violation of UNIQUE KEY constraint 'grnno'". Non-Inventory
 * (PNGR) saves normally. The response of {@code GRN/IUD} is captured so a failed save reports the server's own reason.</p>
 */
public class GRN extends BasePage {

    public GRN(Page page) { super(page); }

    public String lastRoute = "", lastSupplier = "", lastStore = "", lastInvoice = "", lastPrType = "", lastItemCode = "", lastItemName = "",
            lastSuggestion = "", lastBatch = "", lastExpiry = "", lastQty = "", lastFoc = "", lastRow = "", lastSave = "", lastToast = "",
            lastGrnNo = "", lastSaveAnswer = "", lastReport = "";
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public boolean reportBlank = true;
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Purchase &gt; GRN. Only opens the groups that are shut; the menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 6 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/grnlist$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__grnLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const find=t=>[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t);"
                    + " const purchase=find('Purchase'); if(purchase){ purchase.click(); return 'purchase'; }"
                    + " const inv=find('Inventory'); if(inv){ inv.click(); return 'inventory'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__grnLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("GRN.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__grnLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddGRN/.test(b.getAttribute('ng-click')||'') && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("GRN.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/grnlist/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-grn"); }

    /** Click <b>New</b> ({@code AddGRN()}) and wait for the form. */
    public boolean clickNew() {
        if (!clickByHandler("AddGRN", null)) return false;
        try {
            page.waitForFunction("() => /add-grn/i.test(location.hash) && document.getElementById('InvoiceNoidfocus') && document.getElementById('PSqty')",
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
                + " if(!b) return ''; b.id='__grnBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("GRN: no visible button for " + handlerRe); return false; }
        try { page.locator("#__grnBtn").scrollIntoViewIfNeeded(); page.locator("#__grnBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("GRN: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__grnBtn'); if(e) e.removeAttribute('id'); }"); }
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
     * Choose an option of a (select2-enhanced) select: set the underlying select, fire its change event and sync the select2
     * display. {@code want} matches the option text exactly (case-insensitive); else the first real option when
     * {@code anyIfMissing}. Returns the option text; the model is checked afterwards.
     */
    private String choose(String ng, String want, boolean anyIfMissing) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", sel(ng), new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add(ng + " has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.any) p=o[0]; if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }", Map.of("s", sel(ng), "w", want == null ? "" : want, "any", anyIfMissing));
        String chosen = t == null ? "" : t.toString();
        if (chosen.isEmpty()) { problems.add(ng + " has no option" + (want == null ? "" : " \"" + want + "\"")); return ""; }
        waitForAngular(1000);
        if (model(sel(ng), ng).isEmpty() || "0".equals(model(sel(ng), ng))) problems.add(ng + " is not held by the model");
        return chosen;
    }

    private void scrollToCss(String css) {
        page.evaluate("(c) => { const e=document.querySelector(c); if(e) e.scrollIntoView({block:'center'}); }", css);
        page.waitForTimeout(250);
    }

    // ---- header -------------------------------------------------------------------------------------------------

    public String selectSupplier() { problems.clear(); lastSupplier = choose("GRN.SupplierID", null, true); return lastSupplier; }

    public String selectStore(String want) {
        problems.clear();
        lastStore = choose("GRN.StoreID", want, false);
        waitForAngular(800);
        return lastStore;
    }

    /** Type the invoice number (a real fill) and read it back from the model. */
    public String enterInvoiceNo(String invoice) {
        problems.clear();
        try {
            Locator l = page.locator("#InvoiceNoidfocus");
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(invoice);
        } catch (Exception e) { problems.add("the invoice number could not be typed"); return ""; }
        waitForAngular(400);
        lastInvoice = model("#InvoiceNoidfocus", "GRN.InvoiceNo");
        if (!lastInvoice.equals(invoice)) problems.add("the invoice number reads \"" + lastInvoice + "\"");
        return lastInvoice;
    }

    public String selectPrType(String want) {
        problems.clear();
        lastPrType = choose("GRN.PRType", want, false);
        return lastPrType;
    }

    // ---- item search ---------------------------------------------------------------------------------------------

    /**
     * Type {@code term} in <b>Item Name</b> (real key presses, so the smart search fires) and wait for its suggestion list. The
     * first suggestion whose text contains the term is clicked; the item code and prices fill in. Returns the suggestion clicked
     * ("" when none appeared / matched).
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
                + " li.id='__grnSugg'; return norm(li.textContent); }", term);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + term + "\""); return ""; }
        try { page.locator("#__grnSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__grnSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastItemCode = model("#txtItemCode", "GRNDetails.itemcode");
        lastItemName = model("#txtServiceName", "GRNDetails.itemname");
        if (lastItemCode.isEmpty()) problems.add("choosing the suggestion did not fill the item code");
        return lastSuggestion;
    }

    /** Enter the quantity and press Enter (focus moves on to FOC). */
    public String enterQuantityAndEnter(String qty) {
        problems.clear();
        try {
            Locator l = page.locator("#PSqty");
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(qty);
            l.press("Enter");
        } catch (Exception e) { problems.add("the quantity could not be entered"); return ""; }
        waitForAngular(700);
        lastQty = model("#PSqty", "GRNDetails.quantity");
        String focus = String.valueOf(page.evaluate("() => document.activeElement ? document.activeElement.id : ''"));
        if (!"FreeQtyid".equals(focus)) problems.add("Enter did not move focus to FOC (focus is on \"" + focus + "\")");
        return lastQty;
    }

    /** Enter the FOC and press Enter in the same field — that adds the line to the List of Items. Returns the grid's row count. */
    public int enterFocAndEnter(String foc) {
        problems.clear();
        int before = itemRows();
        try {
            Locator l = page.locator("#FreeQtyid");
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(foc);
            lastFoc = model("#FreeQtyid", "GRNDetails.freequantity");
            l.press("Enter");
        } catch (Exception e) { problems.add("the FOC could not be entered"); return before; }
        try {
            page.waitForFunction("(n) => document.querySelectorAll('[ui-grid=\"gridOptionsaddgrn\"] .ui-grid-canvas .ui-grid-row').length>n", before, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("Enter in FOC did not add a line to the List of Items"); }
        waitForAngular(800);
        int after = itemRows();
        lastRow = rowSummary();
        return after;
    }

    public int itemRows() {
        Object n = page.evaluate("() => document.querySelectorAll('[ui-grid=\"gridOptionsaddgrn\"] .ui-grid-canvas .ui-grid-row').length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The first line's data as the grid holds it: code, name, quantity, FOC, total. */
    public String rowSummary() {
        Object r = page.evaluate("() => { let d=null; document.querySelectorAll('*').forEach(el=>{ if(d) return; try{ const s=angular.element(el).scope();"
                + " if(s && s.gridOptionsaddgrn && s.gridOptionsaddgrn.data && s.gridOptionsaddgrn.data.length) d=s.gridOptionsaddgrn.data[0]; }catch(e){} });"
                + " return d? (d.itemcode+' '+d.itemname+' x '+d.quantity+' + FOC '+d.freequantity+' @ '+d.rate+' = '+d.totalamount) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- the line's own cells ----------------------------------------------------------------------------------

    /**
     * Fill the line's <b>Batch Number</b> and <b>Exp Date</b>. They are mandatory when the item needs a batch (the line's batch box is
     * {@code required}); a line that does not need one is left alone. Returns a description ("" when nothing was needed).
     */
    public String fillBatchAndExpiry(String batch, String expiryDmy) {
        problems.clear();
        Object req = page.evaluate("() => { const b=document.querySelector(\"input[ng-model='row.entity.batchcode']\"); return !!b && !!b.required; }");
        if (!Boolean.TRUE.equals(req)) return "";
        try {
            Locator b = page.locator("input[ng-model='row.entity.batchcode']").first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(5000));
            b.fill(batch);
            b.press("Tab");
            waitForAngular(800);
            Locator e = page.locator("input[ng-model='row.entity.expirydate']").first();
            e.click(new Locator.ClickOptions().setTimeout(5000));
            e.fill(expiryDmy);
            e.press("Tab");
        } catch (Exception ex) { problems.add("the batch / expiry could not be typed: " + ex.getMessage().split("\n")[0]); return ""; }
        waitForAngular(800);
        lastBatch = model("input[ng-model='row.entity.batchcode']", "row.entity.batchcode");
        lastExpiry = model("input[ng-model='row.entity.expirydate']", "row.entity.expirydate");
        if (!lastBatch.equals(batch)) problems.add("the batch reads \"" + lastBatch + "\"");
        if (lastExpiry.isEmpty()) problems.add("the expiry date was not accepted");
        return "Batch Number " + lastBatch + ", Exp Date " + lastExpiry;
    }

    /** Tick the line's checkbox (a real click) and read it back. Returns true when ticked. */
    public boolean tickItemCheckbox() {
        problems.clear();
        Locator cb = page.locator("[ui-grid='gridOptionsaddgrn'] .ui-grid-canvas .ui-grid-row input[type=checkbox]").first();
        try {
            cb.scrollIntoViewIfNeeded();
            cb.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the checkbox could not be clicked"); return false; }
        waitForAngular(500);
        boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector(\"[ui-grid='gridOptionsaddgrn'] .ui-grid-canvas .ui-grid-row input[type=checkbox]\"); return !!c && c.checked; }"));
        if (!on) problems.add("the tick did not stick");
        return on;
    }

    /** What the line's checkbox is called (its column header). */
    public String checkboxColumn() {
        Object r = page.evaluate("() => { const g=document.querySelector(\"[ui-grid='gridOptionsaddgrn']\"); const h=g && g.querySelector('.ui-grid-header-cell');"
                + " return h? (h.textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- Save + report --------------------------------------------------------------------------------------------

    /**
     * Click <b>Save</b> ({@code IUDSave()}). Captures the toast, the {@code GRN/IUD} answer (so a failure reports the server's own
     * reason) and the report tab the app opens, which is judged like any DevHIS report ({@link PdfReport}). Returns the toast.
     */
    public String clickSaveAndCapture() {
        lastToast = ""; lastGrnNo = ""; lastSaveAnswer = ""; lastReport = ""; reportBlank = true; reportPng = null;
        final java.util.List<com.microsoft.playwright.Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> watcher = r -> {
            try { if (r.url().contains("GRN/IUD")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { window.__grnToasts=[]; if(window.__grnObs) window.__grnObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__grnToasts.includes(t)) window.__grnToasts.push(t); }); };"
                + " window.__grnObs=new MutationObserver(grab); window.__grnObs.observe(document.body,{childList:true,subtree:true}); }");
        int tabsBefore = page.context().pages().size();
        try {
            if (!clickByHandler("IUDSave", "^\\s*save\\s*$")) System.out.println("GRN: the Save button was not found");
            for (int i = 0; i < 40; i++) {
                Object ans = page.evaluate("() => {" + JS
                        + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.bootbox,.sweet-alert')].filter(vis)"
                        + "   .find(m=>/save|submit|confirm|sure|proceed/i.test(norm(m.textContent)));"
                        + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                        + "     .find(b=>/^\\s*(yes|ok|save|submit|confirm)\\s*$/i.test(norm(b.textContent)));"
                        + "   if(y){ y.click(); return 'confirmed'; } }"
                        + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                        + "   const t=norm(el.textContent); window.__grnToasts=window.__grnToasts||[];"
                        + "   if(t && !window.__grnToasts.includes(t)) window.__grnToasts.push(t); });"
                        + " return ''; }");
                boolean got = Boolean.TRUE.equals(page.evaluate(
                        "() => (window.__grnToasts||[]).some(a=>/succe|added|saved|error|fail|please|invalid|required|already|exist/i.test(a))"));
                if (got) break;
                page.waitForTimeout(500);
            }
            lastSave = "clicked Save [IUDSave]";
            // the report opens in a new tab: judge it like every other DevHIS report
            PdfReport.Result rep = PdfReport.capture(page, tabsBefore, 20000);
            reportBlank = rep.blank; lastReport = rep.diagnostics; reportPng = rep.png;
            waitForAngular(800);
        } finally {
            page.offResponse(watcher);
        }
        Object r = page.evaluate("() => { const a=window.__grnToasts||[];"
                + " return a.find(x=>/succe|added|saved/i.test(x)) || a.find(x=>/error|fail|please|invalid|required|already|exist/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (com.microsoft.playwright.Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            if (a.status() == 200) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) lastGrnNo = m.group(1);
                lastSaveAnswer = "HTTP 200 " + body.replaceAll("\\s+", " ").substring(0, Math.min(140, body.length()));
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : body.replaceAll("\\s+", " ").substring(0, Math.min(160, body.length())));
            }
        }
        // close the report tab so the main page is the one in front again
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        System.out.println("GRN: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer + "; report: " + lastReport);
        return lastToast;
    }

    /** The save answered with a success message (and not an error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|added|saved).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }
}
