package com.kpj.pages.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.Map;

/**
 * Inventory &gt; <b>Current Item Stock</b> — Page Object ({@code #/CurentItemStock} — the app spells the route that way).
 *
 * <p>Confirmed live (devhis build v2026.10.21.266). The screen opens with its list already filled: a ui-grid of stock
 * lines (Location Name, Item Code, Item Name, UOM, Store Name, Batch Code, Available Stock, Minimum, Reorder, Expiry
 * Date, Purchase Quantity, Sale Quantity, Barcode = {@code <item code>_<batch>}, Issue/Transfer). Clicking a cell of a row
 * SELECTS the row ({@code ui-grid-row-selected}). The footer carries <b>Print</b>, <b>Print Item Label</b>
 * ({@code CreateQR()}), <b>Freeze &amp; Recall</b> and <b>Print Shelf Label</b>.</p>
 *
 * <p><b>Print Item Label</b> for the selected row calls {@code CurrentItemStock/GetQRCode} and opens a NEW tab,
 * {@code SQLReport/InventoryReport/frmcurrentstockbarcodeReport.aspx?itemid=..&batchid=..&StoreId=..}, titled "Item Label":
 * the item code, batch code, expiry and item name as text, a 300×300 QR image ({@code alt="QR"}) and a Print button. It is
 * an HTML page, not a PDF, so it is judged on what it shows (and screenshotted), not through {@code PdfReport}.</p>
 *
 * <p>Like the other ui-grids in this app, the grid has a pinned left container whose rows carry no text, so data rows are the
 * rows that DO have text.</p>
 */
public class CurrentItemStock extends BasePage {

    public CurrentItemStock(Page page) { super(page); }

    public String lastRoute = "", lastItemCode = "", lastItemName = "", lastBatch = "", lastStore = "", lastExpiry = "",
            lastRow = "", lastLabel = "", lastLabelUrl = "", lastToast = "";
    /** True when the last Print Item Label produced a label that matches the selected row. */
    public boolean labelOk = false;
    /** The label tab as an image (a screenshot of the tab), or null. */
    public byte[] labelPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Current Item Stock. The nav menu is built seconds after login, so its Inventory entry is awaited first. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 4 && !onScreen(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/curentitemstock$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__cisLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const g=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='Inventory');"
                    + " if(g){ g.click(); return 'opened'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__cisLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CurrentItemStock.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__cisLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        return onScreen() && waitGrid();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/curentitemstock/?$"); }

    /** Wait for the stock grid to hold data rows. */
    public boolean waitGrid() {
        try {
            page.waitForFunction("() => { const g=document.querySelector('[ui-grid]'); if(!g) return false;"
                    + " return [...g.querySelectorAll('.ui-grid-canvas .ui-grid-row')].some(r=>(r.textContent||'').trim()); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { return false; }
        waitForAngular(600);
        return true;
    }

    public int stockRows() {
        Object n = page.evaluate("() => {" + JS + " const g=document.querySelector('[ui-grid]'); if(!g) return 0;"
                + " return [...g.querySelectorAll('.ui-grid-canvas .ui-grid-row')].filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    // ---- select a row -------------------------------------------------------------------------------------------------

    /**
     * Select a row BY CLICKING it: a real click on the Item Name cell of the first data row that carries an item code and a
     * batch code (so there is a label to print). The columns are found by their headers. The selection is read back from the
     * row's {@code ui-grid-row-selected} class. Returns a description ("" when nothing could be selected).
     */
    public String selectRowByClick() {
        Object r = page.evaluate("() => {" + JS
                + " const g=document.querySelector('[ui-grid]'); if(!g) return null;"
                + " const heads=[...g.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/ ?\\d$/,'')).filter(Boolean);"
                + " const ix=n=>heads.findIndex(h=>h.toLowerCase()===n);"
                + " const iCode=ix('item code'), iName=ix('item name'), iBatch=ix('batch code'), iStore=ix('store name'), iExp=ix('expiry date');"
                + " if(iCode<0||iName<0||iBatch<0) return {err:'columns not found: '+heads.join('|')};"
                + " const rows=[...g.querySelectorAll('.ui-grid-canvas .ui-grid-row')].filter(r=>norm(r.textContent));"
                + " for(const r of rows){ const c=[...r.querySelectorAll('.ui-grid-cell')].map(x=>norm(x.textContent));"
                + "   if(c[iCode] && c[iBatch]){ r.querySelectorAll('.ui-grid-cell')[iName].id='__cisCell';"
                + "     return {code:c[iCode],name:c[iName],batch:c[iBatch],store:iStore>=0?c[iStore]:'',expiry:iExp>=0?c[iExp]:'',row:c.slice(0,8).join(' | ')}; } }"
                + " return {err:'no row with an item code and batch code'}; }");
        if (!(r instanceof Map)) return "";
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) r;
        if (m.get("err") != null) { System.out.println("CurrentItemStock: " + m.get("err")); return ""; }
        lastItemCode = String.valueOf(m.get("code")); lastItemName = String.valueOf(m.get("name"));
        lastBatch = String.valueOf(m.get("batch")); lastStore = String.valueOf(m.get("store"));
        lastExpiry = String.valueOf(m.get("expiry")); lastRow = String.valueOf(m.get("row"));
        try {
            page.locator("#__cisCell").scrollIntoViewIfNeeded();
            page.locator("#__cisCell").click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("CurrentItemStock: row click failed - " + e.getMessage()); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__cisCell'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(700);
        Object sel = page.evaluate("() => {" + JS + " const g=document.querySelector('[ui-grid]');"
                + " return [...g.querySelectorAll('.ui-grid-canvas .ui-grid-row.ui-grid-row-selected')].filter(r=>norm(r.textContent)).map(r=>norm(r.textContent).slice(0,120)); }");
        java.util.List<?> sl = sel instanceof java.util.List ? (java.util.List<?>) sel : new java.util.ArrayList<>();
        if (sl.size() != 1 || !String.valueOf(sl.get(0)).contains(lastItemCode)) return "";
        return "clicked the row — item " + lastItemCode + " \"" + lastItemName + "\", store " + lastStore + ", batch " + lastBatch
                + ", expiry " + lastExpiry + " — and it reads back as the one selected row";
    }

    // ---- Print Item Label -----------------------------------------------------------------------------------------

    /**
     * Click <b>Print Item Label</b> ({@code CreateQR()}) and judge the label it opens. The label is a new tab (HTML, not a
     * PDF): it must open at {@code frmcurrentstockbarcodeReport.aspx}, be titled "Item Label", carry a loaded QR image, and show
     * the SELECTED row's item code, batch code and item name. The tab is screenshotted ({@link #labelPng}) and closed. Returns a
     * sentence of evidence; {@link #labelOk} says whether it passed.
     */
    public String clickPrintItemLabelAndVerify() {
        labelOk = false; labelPng = null; lastLabel = ""; lastLabelUrl = ""; lastToast = "";
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => { window.__cisToasts=[]; if(window.__cisObs) window.__cisObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__cisToasts.includes(t)) window.__cisToasts.push(t); }); };"
                + " window.__cisObs=new MutationObserver(grab); window.__cisObs.observe(document.body,{childList:true,subtree:true}); }");
        Object tag = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/CreateQR/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return ''; b.id='__cisPrint'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) { lastLabel = "the Print Item Label button was not found"; return lastLabel; }
        try { page.locator("#__cisPrint").scrollIntoViewIfNeeded(); page.locator("#__cisPrint").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { lastLabel = "Print Item Label could not be clicked: " + e.getMessage().split("\n")[0]; return lastLabel; }
        finally { page.evaluate("() => { const e=document.getElementById('__cisPrint'); if(e) e.removeAttribute('id'); }"); }

        // the label opens in a new tab, which first shows about:blank and is then filled in
        Page tab = null;
        long deadline = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < deadline && tab == null) {
            java.util.List<Page> pages = page.context().pages();
            if (pages.size() > tabsBefore) {
                Page t = pages.get(pages.size() - 1);
                try { if (t.url() != null && t.url().toLowerCase().contains("frmcurrentstockbarcodereport")) tab = t; } catch (Exception ignore) { }
            }
            if (tab == null) page.waitForTimeout(400);
        }
        Object toasts = page.evaluate("() => (window.__cisToasts||[]).join(' / ')");
        lastToast = toasts == null ? "" : toasts.toString();
        if (tab == null) {
            lastLabel = "no Item Label tab opened" + (lastToast.isEmpty() ? "" : " — the screen said: " + lastToast);
            return lastLabel;
        }
        lastLabelUrl = tab.url();
        try { tab.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10000)); } catch (Exception ignore) { }
        try { tab.waitForFunction("() => document.querySelector('img') && document.querySelector('img').naturalWidth>0", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) tab.evaluate("() => { const d=document;"
                    + " const txt=(d.body.innerText||'').replace(/\\s+/g,' ').trim();"
                    + " const imgs=[...d.querySelectorAll('img')].map(i=>({w:i.naturalWidth,h:i.naturalHeight,alt:i.alt||''}));"
                    + " return {title:d.title,text:txt,imgs:imgs}; }");
            String title = String.valueOf(info.get("title")), text = String.valueOf(info.get("text"));
            @SuppressWarnings("unchecked") java.util.List<Map<String, Object>> imgs = (java.util.List<Map<String, Object>>) info.get("imgs");
            boolean qr = imgs != null && imgs.stream().anyMatch(i -> ((Number) i.get("w")).intValue() > 0 && ((Number) i.get("h")).intValue() > 0);
            String qrDesc = imgs == null || imgs.isEmpty() ? "no image" : imgs.get(0).get("w") + "x" + imgs.get(0).get("h") + " px \"" + imgs.get(0).get("alt") + "\" image";
            boolean hasCode = text.contains(lastItemCode), hasBatch = text.contains(lastBatch), hasName = text.toLowerCase().contains(lastItemName.toLowerCase());
            boolean titled = title.equalsIgnoreCase("Item Label");
            labelOk = titled && qr && hasCode && hasBatch && hasName;
            lastLabel = "a new tab opened at " + lastLabelUrl + " — titled \"" + title + "\"" + (titled ? "" : " (expected \"Item Label\")")
                    + "; a " + qrDesc + (qr ? " (loaded)" : " (NOT loaded)")
                    + "; the label reads \"" + text + "\" — " + (hasCode ? "it shows" : "it does NOT show") + " the selected item code " + lastItemCode
                    + ", " + (hasBatch ? "the batch " : "NOT the batch ") + lastBatch + " and " + (hasName ? "the item name" : "NOT the item name") + ".";
        } catch (Exception e) {
            lastLabel = "a tab opened at " + lastLabelUrl + " but its content could not be read: " + e.getMessage().split("\n")[0];
        }
        // a picture of the label — the step's screenshot would otherwise show the stock list, not the label
        try {
            tab.bringToFront();
            labelPng = tab.screenshot(new Page.ScreenshotOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("CurrentItemStock: the label screenshot failed - " + e.getMessage().split("\n")[0]); }
        try { tab.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }
        System.out.println("CurrentItemStock: label -> " + lastLabel);
        return lastLabel;
    }
}
