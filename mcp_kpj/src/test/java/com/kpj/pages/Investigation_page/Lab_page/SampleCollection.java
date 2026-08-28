package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Lab &gt; <b>Sample Collection</b> — Page Object.
 *
 * <p>The screen carries TWO ui-grids: the <b>List of Orders</b> and, below it, the <b>Test Details</b>
 * of whichever order is ticked. Both use ui-grid's selection button as their tick, not a checkbox.</p>
 */
public class SampleCollection extends BasePage {

    public SampleCollection(Page page) { super(page); }

    private static final String M = "SampleCollection.";

    public String lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "", lastOrders = "",
            lastOrderTick = "", lastTests = "", lastTestTick = "", lastCollect = "", lastDialog = "",
            lastSave = "", lastBarcode = "";
    public boolean barcodeBlank = true;
    public boolean toastFromObserver = false;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const grids=()=>[...document.querySelectorAll('[ui-grid]')].filter(vis);"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*investigation\\s*$");
            clickMenu("^\\s*lab\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*sample\\s*collection\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__scMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__scMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SampleCollection.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__scMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '/SampleCollection'; }"); }
            catch (Exception ignore) { }
            waitForAngular(4500);
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='" + M + "MRNO']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        return onScreen();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1500);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        return h >= 0 && u.substring(h).contains("samplecollection");
    }

    // ---- search ----------------------------------------------------------

    /** Enter the <b>From</b> and <b>To</b> dates — cleared first, since these pickers append. */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(M + "FromDate", from);
        String r2 = typeDate(M + "ToDate", to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("SampleCollection: " + lastDates);
        return lastDates;
    }

    private String typeDate(String model, String value) {
        boolean inDialog = Boolean.TRUE.equals(page.evaluate(
                "(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return !!(e && e.closest('.modal,[role=dialog]')); }", model));
        try {
            com.microsoft.playwright.Locator box = page.locator("input[ng-model='" + model + "']").first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            if (inDialog) {
                // Escape would close the DIALOG, not just the picker - and the Save button goes with
                // it, which is how this first read as "no Save button in the dialog". Click the
                // dialog's own heading instead: it dismisses the picker and leaves the dialog open.
                page.evaluate("() => {" + JS
                        + " const d=[...document.querySelectorAll('.modal,[role=dialog]')].filter(vis).pop();"
                        + " if(!d) return;"
                        + " const h=d.querySelector('.modal-title,.modal-header,h3,h4') || d;"
                        + " h.click(); }");
            } else {
                box.press("Escape");
            }
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(600);
        Object back = page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return e? (e.value||'') : '(gone)'; }", model);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from)
                && lastDates.contains("ToDate=" + to);
    }

    /** Click <b>Search</b> ({@code GetOrderTestDetails}). */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/GetOrderTestDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(5000);
        lastSearch = "clicked Search [GetOrderTestDetails] -> " + orderRows() + " order(s), "
                + testRows() + " test(s)";
        System.out.println("SampleCollection: " + lastSearch);
        return lastSearch;
    }

    /** Rows in a grid by index: 0 = List of Orders, 1 = Test Details. */
    private int rowsIn(int gridIndex) {
        Object n = page.evaluate("(i) => {" + JS
                + " const g=grids()[i]; if(!g) return 0;"
                + " return [...g.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }", gridIndex);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public int orderRows() { return rowsIn(0); }
    public int testRows() { return rowsIn(1); }

    public String describeOrders() {
        lastOrders = describeGrid(0);
        return lastOrders;
    }

    public String describeTests() {
        lastTests = describeGrid(1);
        return lastTests;
    }

    private String describeGrid(int i) {
        Object r = page.evaluate("(i) => {" + JS
                + " const g=grids()[i]; if(!g) return '(no grid)';"
                + " const rows=[...g.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }", i);
        return r == null ? "" : r.toString();
    }

    // ---- selection --------------------------------------------------------

    /**
     * Click the tick symbol on the first row of a grid.
     *
     * <p>Scoped to DATA rows of that grid: the same class marks the header's select-all, and both grids
     * carry one, so an unscoped lookup ticks the wrong grid.</p>
     */
    private String tick(int gridIndex, String what) {
        Object r = page.evaluate("(i) => {" + JS
                + " const g=grids()[i]; if(!g) return '(no grid)';"
                + " const t=[...g.querySelectorAll('.ui-grid-row .ui-grid-selection-row-header-buttons')]"
                + "   .filter(vis)[0];"
                + " if(!t) return '(no tick symbol on any row)';"
                + " const row=t.closest('.ui-grid-row');"
                + " const text=row? norm(row.textContent).slice(0,90) : '?';"
                + " t.click(); return text; }", gridIndex);
        String rowText = r == null ? "" : r.toString();
        waitForAngular(2500);
        Object after = page.evaluate("(i) => {" + JS
                + " const g=grids()[i]; if(!g) return '0|(unreadable)';"
                + " const marked=[...g.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>/ui-grid-row-selected/.test(r.className)"
                + "     || r.querySelector('.ui-grid-row-selected')).length;"
                + " let api='(unreadable)';"
                + " try{ const sc=angular.element(g).scope();"
                + "      const a=sc && (sc.gridApi || (sc.grid && sc.grid.api));"
                + "      if(a && a.selection) api=String(a.selection.getSelectedRows().length); }catch(e){}"
                + " return marked+'|'+api; }", gridIndex);
        String[] parts = String.valueOf(after).split("\\|");
        String result = "ticked the first " + what + " -> " + parts[0] + " row element(s) marked"
                + (parts.length > 1 && !parts[1].equals("(unreadable)")
                    ? ", the grid's selection API reports " + parts[1] + " selected" : "")
                + "; the row reads: " + rowText;
        System.out.println("SampleCollection: " + result);
        return result;
    }

    /**
     * Tick the first order, then judge it by what it PRODUCED: Test Details reloads to that order's
     * tests. The orders grid does not expose a selection API in this build, so "0 selected" there says
     * nothing about whether the click landed - but the tests changing does.
     */
    public String tickFirstOrder() {
        int testsBefore = testRows();
        String base = tick(0, "order");
        int testsAfter = testRows();
        lastOrderTick = base + "; Test Details went from " + testsBefore + " to " + testsAfter
                + " row(s) for the selected order";
        return lastOrderTick;
    }
    public String tickFirstTest() { lastTestTick = tick(1, "test"); return lastTestTick; }

    private static boolean selected(String s) {
        if (s == null) return false;
        java.util.regex.Matcher api = java.util.regex.Pattern
                .compile("selection API reports (\\d+) selected").matcher(s);
        if (api.find()) return Integer.parseInt(api.group(1)) > 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("-> (\\d+) row element\\(s\\) marked").matcher(s);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
    }

    public boolean orderSelected() {
        if (selected(lastOrderTick)) return true;
        // Fall back to the consequence: the tests list reloaded for the order that was ticked.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("from (\\d+) to (\\d+) row").matcher(lastOrderTick == null ? "" : lastOrderTick);
        return m.find() && Integer.parseInt(m.group(2)) > 0
                && !m.group(1).equals(m.group(2));
    }
    public boolean testSelected() { return selected(lastTestTick); }

    // ---- collect ----------------------------------------------------------

    /** Click <b>Collect</b> ({@code OpenSampleCollection}) and confirm its dialog came up. */
    public String clickCollect() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/OpenSampleCollection/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        Object r = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')]"
                + "   .filter(vis).pop();"
                + " if(!d) return 'the Sample Collection dialog did NOT open';"
                + " const save=[...d.querySelectorAll('button')]"
                + "   .some(b=>/IUDSampleCollection/i.test(b.getAttribute('ng-click')||''));"
                + " return 'the Sample Collection dialog opened: \"'+norm(d.textContent).slice(0,60)+'\"'"
                + "   +(save? ' (its Save is IUDSampleCollection)' : ' (no Save button in it)'); }");
        lastCollect = r == null ? "" : r.toString();
        System.out.println("SampleCollection: collect -> " + lastCollect);
        return lastCollect;
    }

    public boolean collectDialogOpen() {
        return lastCollect != null && lastCollect.startsWith("the Sample Collection dialog opened");
    }

    /**
     * Enter the <b>Sample Collection Date</b> and <b>Time</b>.
     *
     * <p>The date box is a picker that arrives pre-filled, so it is cleared first; the time is the
     * separate {@code inputTime} control beside it, which parses its own display format.</p>
     */
    public String enterCollectionDateTime(String date, String time) {
        String d = typeDate(M + "samplecollecteddatetime", date);
        String t = "(no time box)";
        Object tagged = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__scTime').forEach(e=>e.removeAttribute('id'));"
                + " const d=[...document.querySelectorAll('.modal,[role=dialog]')].filter(vis).pop();"
                + " if(!d) return 'no dialog on screen';"
                + " const e=[...d.querySelectorAll(\"input[ng-model='inputTime']\")].filter(vis)[0];"
                + " if(!e) return 'no time box in the dialog';"
                + " e.id='__scTime'; return 'tagged'; }");
        if ("tagged".equals(String.valueOf(tagged))) {
            try {
                com.microsoft.playwright.Locator box = page.locator("#__scTime");
                box.click();
                box.press("Control+a");
                box.press("Delete");
                box.type(time, new com.microsoft.playwright.Locator.TypeOptions().setDelay(110));
                waitForAngular(600);
                Object back = page.evaluate("() => { const e=document.getElementById('__scTime');"
                        + " return e? (e.value||'') : '(gone)'; }");
                t = back == null ? "" : back.toString();
            } catch (Exception e) {
                t = "(typing the time failed: " + e.getMessage().split("\n")[0] + ")";
            }
        } else {
            t = "(" + tagged + ")";
        }

        lastDialog = "CollectionDate=" + d + " | CollectionTime=" + t;
        waitForAngular(500);
        System.out.println("SampleCollection: dialog -> " + lastDialog);
        return lastDialog;
    }

    public boolean dateTimeEntered(String date) {
        return lastDialog != null && lastDialog.contains("CollectionDate=" + date)
                && !lastDialog.contains("(no time box)") && !lastDialog.contains("typing the time failed");
    }

    /** Click <b>Save</b> ({@code IUDSampleCollection}) and return the toast. */
    public String saveAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove());"
                    + " window.__scSeen = [];"
                    + " if(window.__scObs) window.__scObs.disconnect();"
                    + " window.__scObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /toast|alert|jAlert|sweet/i.test(cls+' '+n.id))"
                    + "       window.__scSeen.push(t.slice(0,140)); } } });"
                    + " window.__scObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/IUDSampleCollection/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Save button in the dialog)';"
                + " b.click(); return 'clicked Save [IUDSampleCollection]'; }");
        waitForAngular(3500);

        String toast = "";
        long deadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            Object now = page.evaluate("() => {" + JS
                    + " const t=[...document.querySelectorAll('.toast-message,.toast')].filter(vis)"
                    + "   .map(x=>norm(x.textContent)).filter(x=>x);"
                    + " return t.length? t[0] : ''; }");
            toast = now == null ? "" : now.toString();
            if (toast.isEmpty()) page.waitForTimeout(400);
        }
        String seen = "";
        try {
            Object s = page.evaluate("() => { const a=window.__scSeen||[];"
                    + " if(window.__scObs) window.__scObs.disconnect();"
                    + " return a.length? a.slice(0,4).join(' ;; ') : ''; }");
            seen = s == null ? "" : s.toString();
        } catch (Exception ignore) { }
        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[^A-Za-z0-9]*(?:KPJ Portal)?\\s*([A-Za-z][^;]{3,120})").matcher(seen);
            if (m.find()) { toast = m.group(1).trim(); toastFromObserver = true; }
        }
        toast = toast.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastSave = (clicked == null ? "" : clicked.toString()) + "; "
                + (seen.isEmpty() ? "nothing was recorded appearing after Save" : "the page added: " + seen);
        System.out.println("SampleCollection: save -> " + lastSave + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Verify the barcode the collection produces.
     *
     * <p>If saving did not open one, the screen's own <b>Print Barcode</b> is used — the report is a PDF
     * either way, so it is judged as a file rather than screenshotted.</p>
     */
    public String captureBarcode(int tabsBefore) {
        com.kpj.pages.PdfReport.Result r = com.kpj.pages.PdfReport.capture(page, tabsBefore, 9000);
        String how = "the save opened it";
        if (r.url.isEmpty()) {
            int before = page.context().pages().size();
            page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .find(x=>/fnPrintBarCode/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            waitForAngular(3000);
            r = com.kpj.pages.PdfReport.capture(page, before, 9000);
            how = "the save opened nothing, so the screen's own Print Barcode was used";
        }
        barcodeBlank = r.blank;
        lastBarcode = how + " — " + r.diagnostics;
        System.out.println("SampleCollection: barcode -> " + lastBarcode);
        return lastBarcode;
    }

    /** A success message, judged strictly. */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("collected")
                || t.contains("updated");
    }
}
