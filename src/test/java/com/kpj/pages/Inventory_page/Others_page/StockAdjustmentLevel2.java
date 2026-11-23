package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Inventory &gt; Others &gt; <b>Stock Adjustment Level 2</b> — Page Object.
 *
 * <p>Flow: enter the <b>From Date</b> and <b>To Date</b> → click <b>Search</b> → in the list of stock,
 * click on the tick symbol to choose any one row → verify the selected row's items appear in
 * <b>Stock Management Details</b> → click <b>Print</b> → verify PDF report generation.</p>
 *
 * <p>Sibling of {@link GatePassOutList} in the {@code Inventory_page.Others_page} submodule, confirmed
 * at route {@code #/StockAdjustmentLevel2}. Live-inspected before writing any selector, the same
 * discipline applied to {@link com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem} and {@link
 * GatePassOutList}. That inspection surfaced two screen-specific things:</p>
 * <ul>
 *   <li>This screen has TWO {@code ui-grid} components: {@code gridOptions} ("list of stock") and
 *       {@code gridOptionsone} ("Stock Management Details"). Each row in the first grid also carries an
 *       {@code isapproved} checkbox ({@code ng-click="grid.appScope.changeisapproved($event,row)"}) —
 *       confirmed live that clicking THAT does nothing observable (no request, no grid update). The real
 *       "tick symbol" the request means is {@code ui-grid}'s OWN row-selection icon
 *       ({@code ng-click="selectButtonClick(row, $event)"}, class {@code ui-grid-icon-ok}) — clicking it
 *       is what populates {@code gridOptionsone} and sets the screen's own
 *       {@code StockAdjustment.stockadjid}, confirmed live via the Angular scope.</li>
 *   <li>Confirmed live from the app's own {@code printReport()} source: it opens
 *       {@code window.open(...frmStockAdjustment.aspx?ID=...)} in a new tab ONLY when
 *       {@code StockAdjustment.stockadjid} is set (i.e. a row was actually selected first) — otherwise it
 *       shows a "Please select Stock Adjustment List" confirm dialog instead. A synthetic
 *       (non-Playwright) click on Print during manual live inspection appeared to do nothing purely
 *       because Chrome's popup blocker silently drops {@code window.open} calls triggered by an
 *       untrusted synthetic click; a real {@link com.microsoft.playwright.Locator#click} — as this class
 *       and {@link PdfReport} always use — is a trusted event and does not have that problem, matching
 *       every other Print button already proven working across this module.</li>
 * </ul>
 * <p>{@code describeControls()} is dumped into the report at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.</p>
 */
public class StockAdjustmentLevel2 extends BasePage {

    public StockAdjustmentLevel2(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDateRange = "", lastSearch = "",
            lastRowTick = "", lastDetailsCount = "", lastPrint = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
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
            clickMenu("^\\s*inventory\\s*$");
            clickMenu("^\\s*others\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*stock\\s*adjustment\\s*level\\s*2\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__sal2Menu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__sal2Menu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("StockAdjustmentLevel2.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__sal2Menu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3500);
        }
        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1400);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("stockadjustmentlevel2")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    /** Poll until the screen's own Search button appears — the header nav alone (~15 links) can satisfy
     *  a low generic-control-count threshold before real content renders, confirmed on this module's
     *  sibling screens, so this checks for something screen-specific instead. */
    public boolean waitForRealContent(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("() => {" + JS
                    + " const hasSearch=[...document.querySelectorAll('button')].filter(vis)"
                    + "   .some(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                    + " const controlCount=[...document.querySelectorAll('select,input,button')].filter(vis).length;"
                    + " return (hasSearch && controlCount>6) ? controlCount : 0; }");
            int count = n instanceof Number ? ((Number) n).intValue() : 0;
            if (count > 0) return true;
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- diagnostics -------------------------------------------------------

    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return '[main]\\n' + [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== StockAdjustmentLevel2 CONTROLS ===\n" + s);
        return s;
    }

    // ---- From Date / To Date / Search ------------------------------------------------

    public String enterDateRange(String from, String to) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [f,t]=a;"
                + " const fromEl=document.querySelector('input[ng-model=\"StockAdjustment.FromDate\"]');"
                + " const toEl=document.querySelector('input[ng-model=\"StockAdjustment.ToDate\"]');"
                + " const rf=setEl(fromEl, f); const rt=setEl(toEl, t);"
                + " return 'From='+rf+' To='+rt; }", java.util.List.of(from, to));
        page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        lastDateRange = r == null ? "" : r.toString();
        System.out.println("StockAdjustmentLevel2: " + lastDateRange);
        return lastDateRange;
    }

    public boolean datesEntered(String from, String to) {
        return lastDateRange != null && lastDateRange.contains("From=" + from) && lastDateRange.contains("To=" + to);
    }

    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        lastSearch = "clicked Search -> " + rowCount() + " row(s)";
        System.out.println("StockAdjustmentLevel2: " + lastSearch);
        return lastSearch;
    }

    /** Row count read from the {@code ui-grid} Angular scope's own data array — the DOM cannot be
     *  trusted here since {@code ui-grid} only renders currently-scrolled-into-view columns, confirmed
     *  on the {@code ReceiveIssueItem} sibling screen to leave every cell's {@code textContent} empty
     *  even when rows genuinely exist. */
    public int rowCount() {
        Object n = page.evaluate("() => {"
                + " const g=document.querySelector('[ui-grid=\"gridOptions\"]'); if(!g) return 0;"
                + " try{ const sc=angular.element(g).scope(); return sc.gridOptions? sc.gridOptions.data.length:0;"
                + " }catch(e){ return 0; } }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean rowsFound() { return rowCount() > 0; }

    // ---- tick symbol / details grid --------------------------------------

    /** Click on the tick symbol of the first row — confirmed live this is {@code ui-grid}'s OWN
     *  row-selection icon ({@code ng-click="selectButtonClick(row, $event)"}, class
     *  {@code ui-grid-icon-ok}), NOT the neighbouring {@code isapproved} checkbox column (confirmed live
     *  that one does nothing observable when clicked). */
    public String clickFirstRowTick() {
        Object r = page.evaluate("() => {"
                + " const el=document.querySelector('[ng-click=\"selectButtonClick(row, $event)\"]');"
                + " if(!el) return '(no row tick to click)';"
                + " el.click();"
                + " return 'clicked the first row tick'; }");
        lastRowTick = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("StockAdjustmentLevel2: " + lastRowTick);
        return lastRowTick;
    }

    public boolean rowTickClicked() {
        return lastRowTick != null && lastRowTick.startsWith("clicked the first row tick");
    }

    /** Verify the selected row's items appear in the second grid, <b>Stock Management Details</b>
     *  ({@code gridOptionsone}). */
    public boolean detailsPopulated() {
        int n = detailsRowCount();
        lastDetailsCount = n + " row(s) in Stock Management Details";
        System.out.println("StockAdjustmentLevel2: " + lastDetailsCount);
        return n > 0;
    }

    public int detailsRowCount() {
        Object n = page.evaluate("() => {"
                + " const g=document.querySelector('[ui-grid=\"gridOptionsone\"]'); if(!g) return 0;"
                + " try{ const sc=angular.element(g).scope(); return sc.gridOptionsone? sc.gridOptionsone.data.length:0;"
                + " }catch(e){ return 0; } }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    // ---- print ----------------------------------------------------------------

    /** Click <b>Print</b> and return how many browser tabs existed just before the click — needed by
     *  {@link com.kpj.pages.PdfReport#capture} to find the tab it opens. Confirmed live from the app's
     *  own {@code printReport()} source that this only opens a report tab when a row was genuinely
     *  selected first (a "Please select Stock Adjustment List" confirm dialog appears otherwise). */
    public int clickPrint() {
        int pagesBefore = page.context().pages().size();
        try {
            page.locator("button:has-text('Print')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            lastPrint = "clicked Print";
        } catch (Exception e) {
            lastPrint = "(Print click failed: " + e.getMessage().split("\n")[0] + ")";
        }
        waitForAngular(1000);
        System.out.println("StockAdjustmentLevel2: " + lastPrint);
        return pagesBefore;
    }

    public boolean printClicked() { return "clicked Print".equals(lastPrint); }
}
