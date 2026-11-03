package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Transfer</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>CSSD</b> (flyout) → <b>Transfer</b> (a list/search screen, no
 * hash route — see the note below) → <b>New</b> ({@code AddIssueToStore()}) → the add-transfer form →
 * <b>Get Indent</b> ({@code OpenIndentdetailsList()}, id {@code GetIndent}) → an <b>Item Search</b> modal
 * with FOUR grids stacked top to bottom:</p>
 * <ol>
 *   <li><b>Selected Item</b> (top) — indent HEADERS matching the search (array {@code IndentNumberList}).
 *       Ticking a row ({@code GetIndentItemlist(Itm,0)}) populates grid 2.</li>
 *   <li><b>Indent Item List</b> — the ticked indent's line items (array {@code StoreIndentItemList}).
 *       Ticking a row ({@code GetBatchList(Itm,0)}) populates grid 3 and, per the controller source,
 *       usually grid 4 as well (see below).</li>
 *   <li><b>Item Batch List</b> — the item's available batches at the issuing store (array
 *       {@code BatchSearchList}).</li>
 *   <li><b>Selected Item</b> (bottom, same heading text as #1 — told apart by DOM order) — the batch(es)
 *       actually being transferred (array {@code StockItemList}). <b>OK</b> ({@code OKClick()}) rejects
 *       with "Please Select Item !!!" unless at least one row here has {@code IsSelected=true}.</li>
 * </ol>
 *
 * <p><b>Grid 2 → grids 3+4 is usually automatic — confirmed from
 * {@code IdentItemSearchController.js}'s real {@code GetBatchList}</b>: it fetches batches
 * ({@code ExecFlag: "BatcList"}); if exactly one batch comes back it is auto-ticked and pushed into grid 4
 * via {@code SelectAllCheckBox(...)}; if several come back, the one with the nearest non-expired expiry
 * date is picked the same way. So ticking grid 2 alone typically satisfies grids 3 <i>and</i> 4 — this page
 * object still exposes a method to tick grid 4 manually as a defensive fallback, matching the literal
 * requested flow, in case a given batch shape doesn't auto-select.</p>
 *
 * <p><b>"Unit Quantity" is a real field despite no matching visible column header.</b> The add-transfer
 * form's grid header reads "Link Quantity", but {@code IssueToStoreController.js}'s
 * {@code CalculateTransferQuantity(Row)} reads {@code Row.unitqty} — so the input to fill is whichever one
 * carries {@code ng-model} containing {@code unitqty}, not the one under the "Link Quantity" label.</p>
 *
 * <p><b>No hash route.</b> Unlike most other Ancillary Services screens, Transfer/Store Indent never set
 * {@code window.location.hash} in this environment (confirmed live — it stayed {@code "#"} throughout).
 * There is therefore no direct-route fallback here; navigation must go through the menu, and
 * {@code document.title} (reliably "Transfer" once this screen loads) is used to confirm arrival instead
 * of a URL check.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu. The default {@code tieba}/devhis account can
 * intermittently reject login ("Invalid username or password") — {@code LoginPage}'s built-in fallback
 * credential list (sandhya, farisha, …) already handles this.</p>
 */
public class Transfer extends BasePage {

    public Transfer(Page page) {
        super(page);
        // Remember the server's last answer to the stock query that fills the bottom Selected Item grid: when it is [] there is no stock to transfer, whatever the batch list shows.
        page.onResponse(r -> {
            try {
                if (!r.url().contains("/api/IssueToStore/Fetch")) return;
                String post = r.request().postData();
                if (post == null || !post.contains("GetIndentStockItemList")) return;
                int rows = com.google.gson.JsonParser.parseString(r.text().trim()).getAsJsonArray().size();
                java.util.regex.Matcher a = java.util.regex.Pattern.compile("\"tostoreid\":(\\d+)").matcher(post);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"mrp\":([0-9.]+)").matcher(post);
                java.util.regex.Matcher p = java.util.regex.Pattern.compile("\"purchaserate\":([0-9.]+)").matcher(post);
                lastStockQuery = "GetIndentStockItemList (issuing store id " + (a.find() ? a.group(1) : "?") + ", MRP " + (m.find() ? m.group(1) : "?")
                        + ", purchase rate " + (p.find() ? p.group(1) : "?") + ") answered " + rows + " stock row(s)";
            } catch (Exception ignore) { }
        });
    }

    /** The server's last answer to the stock query behind the bottom Selected Item grid ("" if none was asked). */
    public String lastStockQuery = "";

    /** Every Indent Item List row {@link #tickIndentItemWithStock()} ticked while looking for one with stock. */
    public final List<String> triedItems = new java.util.ArrayList<>();

    public String lastIssuingStore = "", lastRequestingStore = "", lastIndentNumber = "";
    public String lastUnitQty = "";
    /** What {@link #tickIndentItemListRow()} found in Item Batch List right after ticking — "" if none. */
    public String lastBatchListState = "";

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
            + "const pick=(ng,text)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const i=text ? [...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===String(text).toLowerCase())"
            + "               : [...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
            + "  if(i<0) return '(no-option)';"
            + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "  return norm(e.options[i].textContent); };"
            + "const headings=(text)=>[...document.querySelectorAll('*')].filter(e=>norm(e.textContent)===text && e.children.length===0);"
            + "const tableAfter=(heading)=>{ let el=heading;"
            + "  for(let i=0;i<20 && el;i++){ el=el.nextElementSibling || (el.parentElement && el.parentElement.nextElementSibling);"
            + "    if(el && el.querySelector && el.querySelector('table')) return el.querySelector('table');"
            + "    if(el && el.tagName==='TABLE') return el; }"
            + "  return null; };"
            + "const rowsOf=(tbl)=>tbl ? [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(t=>t) : [];";

    private static final String ARM_TOASTS = ""
            + " window.__xferToasts=[]; if(window.__xferObs) window.__xferObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__xferToasts.includes(t)) window.__xferToasts.push(t); }); };"
            + " window.__xferObs=new MutationObserver(grab); window.__xferObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation ----------------------------------------------------------

    /**
     * <b>Ancillary Services</b> → <b>CSSD</b> (flyout) → <b>Transfer</b>. No hash-route fallback exists —
     * see class javadoc.
     *
     * <p><b>The sidebar must be explicitly EXPANDED first, or the 3-level flyout never renders reliably.</b>
     * Confirmed live: on a fresh session the sidebar loads collapsed to icon-only, and in that state the
     * nested CSSD → Transfer flyout is flaky to open programmatically (a real click on Ancillary Services
     * toggles {@code menu-open} on its {@code <li>}, but the grandchild "Transfer" link can stay
     * non-actionable). Clicking the hamburger ({@code a.sidebar-toggle[data-toggle=offcanvas]}) to force
     * the full labeled sidebar first, THEN clicking through, is what actually works — this exact 2-level
     * flyout has no hash-route fallback to fall back on if it doesn't.</p>
     */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("CSSD.Transfer.nav: nav menu never appeared");
        }

        expandSidebar();
        clickByText("ancillary\\s*services?");
        boolean cssdVisible = waitForVisibleText("^cssd$", 12);
        if (!cssdVisible) { System.out.println("CSSD.Transfer.nav: 'CSSD' submenu never became visible"); return false; }
        clickByText("^cssd$");
        boolean transferVisible = waitForVisibleText("^transfer$", 12);
        if (!transferVisible) { System.out.println("CSSD.Transfer.nav: 'Transfer' menu link never became visible under CSSD"); return false; }
        clickByText("^transfer$");

        boolean reached = false;
        for (int i = 0; i < 12 && !reached; i++) {
            waitForAngular(500);
            reached = onListScreen();
        }
        if (!reached) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return 'title=\"'+document.title+'\" | body has \"Transfer\" text: '"
                    + " +document.body.innerText.split('\\n').some(l=>norm(l)==='Transfer'); }");
            System.out.println("CSSD.Transfer.nav: did not detect the list screen after clicking through. " + diag);
        }
        return reached;
    }

    /** Click the hamburger to force the full labeled sidebar (not icon-only) — see {@link #navigateViaMenu()}. */
    private void expandSidebar() {
        try {
            page.locator("a.sidebar-toggle[data-toggle='offcanvas']").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("CSSD.Transfer.nav: sidebar-toggle click failed - " + e.getMessage());
        }
        // Confirm expansion: the "Home" link's own width is only ~48px (icon-only) vs full-width once expanded.
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^home$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                    + " return !!a && a.getBoundingClientRect().width > 80; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) {
            System.out.println("CSSD.Transfer.nav: sidebar did not visibly expand — proceeding anyway");
        }
        waitForAngular(400);
    }

    /**
     * Click the visible {@code <a>} whose normalized text matches {@code regex} — tags it with a unique id
     * (filtering for {@code offsetParent!==null} first) and clicks that id. This sidebar renders more than
     * one element matching the same menu text at once (a collapsed/expanded pair, or similar), so a plain
     * {@code Locator.filter({hasText})} can permanently latch onto a non-visible duplicate and time out
     * forever — confirmed live. Filtering to the genuinely visible one before clicking is what other page
     * objects in this repo already do for exactly this kind of menu.
     */
    private void clickByText(String regex) {
        Object tagged = page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a')].find(x=>rx.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!a) return false; a.id='__cssdNavTarget'; return true; }", regex);
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.Transfer.nav: no visible <a> matches /" + regex + "/"); return; }
        try { page.locator("#__cssdNavTarget").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.nav: click on /" + regex + "/ failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cssdNavTarget'); if(e) e.removeAttribute('id'); }");
    }

    /** Poll (up to {@code attempts} x 400ms) for a visible {@code <a>} matching {@code regex}. */
    private boolean waitForVisibleText(String regex, int attempts) {
        for (int i = 0; i < attempts; i++) {
            Object r = page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const rx=new RegExp(re,'i');"
                    + " return [...document.querySelectorAll('a')].some(a=>rx.test(norm(a.textContent)) && a.offsetParent!==null); }", regex);
            if (Boolean.TRUE.equals(r)) return true;
            page.waitForTimeout(400);
        }
        return false;
    }

    /** True on the Transfer list screen — detected structurally (the {@code New} button), since there is no URL to check. */
    public boolean onListScreen() {
        Object r = page.evaluate("() => !![...document.querySelectorAll('button')].find(b=>/AddIssueToStore/i.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)");
        return Boolean.TRUE.equals(r);
    }

    /** True on the add-Transfer form — detected via the {@code Get Indent} button (id {@code GetIndent}). */
    public boolean onTransferForm() {
        Object r = page.evaluate("() => { const e=document.getElementById('GetIndent'); return !!(e && e.offsetParent!==null); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click <b>New</b> ({@code AddIssueToStore()}) and wait for the add-transfer form. */
    public boolean clickNew() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/AddIssueToStore/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__xferNew'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.Transfer.clickNew: New button not found"); return false; }
        try { page.locator("#__xferNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => { const e=document.getElementById('GetIndent'); return !!(e && e.offsetParent!==null); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("CSSD.Transfer.clickNew: add-transfer form did not render"); }
        waitForAngular(800);
        return onTransferForm();
    }

    // ---- Get Indent -> Item Search modal --------------------------------------

    /**
     * Click <b>Get Indent</b> ({@code OpenIndentdetailsList()}) and wait for the Item Search modal.
     *
     * <p>Detected via the modal's own <b>Issuing Store</b> select ({@code ng-model="IdentItemSearch.ToStore"})
     * becoming visible, not the "Item Search" title text — that title sits in an {@code <h4>} alongside a
     * close button, so it never satisfies a "no child elements" text match.</p>
     */
    public boolean clickGetIndent() {
        try { page.locator("#GetIndent").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.clickGetIndent: click failed - " + e.getMessage()); return false; }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='IdentItemSearch.ToStore']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CSSD.Transfer.clickGetIndent: Item Search modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Select the modal's <b>Issuing Store</b> ({@code IdentItemSearch.ToStore}). */
    public String selectModalIssuingStore(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('IdentItemSearch.ToStore', t); }", text);
        lastIssuingStore = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("CSSD.Transfer: modal Issuing Store = " + lastIssuingStore);
        return lastIssuingStore;
    }

    /** Select the modal's <b>Requesting Store</b> ({@code IdentItemSearch.FromStore}). */
    public String selectModalRequestingStore(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('IdentItemSearch.FromStore', t); }", text);
        lastRequestingStore = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("CSSD.Transfer: modal Requesting Store = " + lastRequestingStore);
        return lastRequestingStore;
    }

    /**
     * Click the modal's <b>Search</b> ({@code SearchIndentItem()}). Retries up to 3 times if the
     * <b>Selected Item</b> grid comes back empty on the first attempt — confirmed live that the modal's
     * own Angular scope can still be finishing initialization right after it opens, especially headless,
     * so a search fired immediately can silently return nothing even though the server genuinely has
     * matching indents (re-clicking once it has settled reliably finds them).
     */
    public boolean clickModalSearch() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__xferModalSearch'; return true; }");
            if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.Transfer.clickModalSearch: Search button not found"); return false; }
            try { page.locator("#__xferModalSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("CSSD.Transfer.clickModalSearch: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__xferModalSearch'); if(e) e.removeAttribute('id'); }");

            try {
                page.waitForFunction("() => {" + JS + " return rowsOf(tableAfter(headings('Selected Item')[0])).length > 0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(5000));
                return true;
            } catch (Exception ignore) {
                System.out.println("CSSD.Transfer.clickModalSearch: attempt " + attempt + " came back empty"
                        + (attempt < 3 ? " — retrying" : ""));
                waitForAngular(600);
            }
        }
        return true;
    }

    // ---- grid 1: Selected Item (indent headers) --------------------------------

    /** The top <b>Selected Item</b> grid's rows (indent headers matching the search). */
    public List<String> selectedIndentRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableAfter(headings('Selected Item')[0])); }");
        return toList(r);
    }

    /**
     * Tick the checkbox on the top <b>Selected Item</b> grid's first row ({@code Itm.IsSelected},
     * {@code GetIndentItemlist(Itm,0)}), which populates <b>Indent Item List</b>.
     *
     * @return the row's text, or {@code ""} if no row was available to tick
     */
    public String tickFirstSelectedIndentRow() {
        Object r = page.evaluate("() => {" + JS
                + " const tbl=tableAfter(headings('Selected Item')[0]); const row=tbl?tbl.querySelector('tbody tr'):null;"
                + " if(!row) return ''; const cb=row.querySelector('input[type=checkbox]'); if(!cb) return '';"
                + " const t=norm(row.textContent); cb.click(); return t; }");
        String rowText = r == null ? "" : r.toString();
        try {
            page.waitForFunction("() => {" + JS + " return rowsOf(tableAfter(headings('Indent Item List')[0])).length > 0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { /* genuinely empty, or slower than 8s — caller checks the row count either way */ }
        waitForAngular(400);
        System.out.println("CSSD.Transfer: ticked Selected Item (indent) row -> " + (rowText.isEmpty() ? "(none available)" : rowText));
        return rowText;
    }

    // ---- grid 2: Indent Item List -----------------------------------------------

    /** The <b>Indent Item List</b> grid's rows. */
    public List<String> indentItemListRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableAfter(headings('Indent Item List')[0])); }");
        return toList(r);
    }

    /**
     * Tick the checkbox on the <b>Indent Item List</b> grid's first row ({@code Itm.IsSelected},
     * {@code GetBatchList(Itm,0)}), which fetches <b>Item Batch List</b> and — per the controller source —
     * usually auto-selects a batch straight into the bottom <b>Selected Item</b> grid too (see class
     * javadoc). Updates {@link #lastBatchListState}.
     *
     * @return the row's text, or {@code ""} if no row was available to tick
     */
    public String tickFirstIndentItemRow() {
        Object r = page.evaluate("() => {" + JS
                + " const tbl=tableAfter(headings('Indent Item List')[0]); const row=tbl?tbl.querySelector('tbody tr'):null;"
                + " if(!row) return ''; const cb=row.querySelector('input[type=checkbox]'); if(!cb) return '';"
                + " const t=norm(row.textContent); cb.click(); return t; }");
        String rowText = r == null ? "" : r.toString();
        try {
            page.waitForFunction("() => {" + JS
                    + " return rowsOf(tableAfter(headings('Item Batch List')[0])).length > 0"
                    + "   || (headings('Selected Item').length>1 && rowsOf(tableAfter(headings('Selected Item')[1])).length > 0); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { /* genuinely no batch stock, or slower than 8s — caller checks the row counts either way */ }
        waitForAngular(400);

        List<String> batchRows = itemBatchListRows();
        List<String> bottomRows = bottomSelectedItemRows();
        lastBatchListState = "Item Batch List: " + batchRows.size() + " row(s) " + batchRows
                + " | Selected Item (bottom): " + bottomRows.size() + " row(s) " + bottomRows;
        System.out.println("CSSD.Transfer: ticked Indent Item List row -> " + (rowText.isEmpty() ? "(none available)" : rowText)
                + " | " + lastBatchListState);
        return rowText;
    }

    /** Click the checkbox of row {@code idx} (0-based) in the grid under {@code heading}; "" when there is no such row. */
    private String clickRowCheckbox(String heading, int idx) {
        Object r = page.evaluate("(a) => {" + JS
                + " const tbl=tableAfter(headings(a[0])[0]); const rows=tbl?[...tbl.querySelectorAll('tbody tr')]:[];"
                + " const row=rows[a[1]]; if(!row) return ''; const cb=row.querySelector('input[type=checkbox]'); if(!cb) return '';"
                + " const t=norm(row.textContent); cb.click(); return t; }", java.util.Arrays.asList(heading, idx));
        return r == null ? "" : r.toString();
    }

    /**
     * The first indent's first item can be one with no batch stock at the Issuing Store (a data gap, not a defect), which
     * left the batch step permanently red. Walk the indents (top <b>Selected Item</b> grid) and their items (<b>Indent Item
     * List</b>) until ticking an item queues a batch in the bottom <b>Selected Item</b> grid; un-tick what had no stock.
     * Call with the first indent already ticked and the first item NOT yet ticked.
     *
     * @return the text of the item that has stock, or {@code ""} when no item in any indent has a batch
     */
    public String tickIndentItemWithStock() {
        int indents = selectedIndentRows().size();
        for (int i = 0; i < Math.min(indents, 10); i++) {
            if (i > 0) {
                clickRowCheckbox("Selected Item", i - 1);   // un-tick the previous indent
                if (clickRowCheckbox("Selected Item", i).isEmpty()) continue;
                try {
                    page.waitForFunction("() => {" + JS + " return rowsOf(tableAfter(headings('Indent Item List')[0])).length > 0; }",
                            null, new Page.WaitForFunctionOptions().setTimeout(6000));
                } catch (Exception ignore) { }
                waitForAngular(400);
            }
            int items = indentItemListRows().size();
            for (int j = 0; j < Math.min(items, 10); j++) {
                String text = clickRowCheckbox("Indent Item List", j);
                if (text.isEmpty()) continue;
                triedItems.add(text.length() > 90 ? text.substring(0, 90) + "…" : text);
                try {
                    page.waitForFunction("() => {" + JS + " const hs=headings('Selected Item');"
                            + " return hs.length>1 && rowsOf(tableAfter(hs[hs.length-1])).length > 0; }",
                            null, new Page.WaitForFunctionOptions().setTimeout(5000));
                } catch (Exception ignore) { }
                waitForAngular(300);
                List<String> batchRows = itemBatchListRows();
                List<String> bottomRows = bottomSelectedItemRows();
                lastBatchListState = "Item Batch List: " + batchRows.size() + " row(s) " + batchRows
                        + " | Selected Item (bottom): " + bottomRows.size() + " row(s) " + bottomRows;
                System.out.println("CSSD.Transfer: indent " + (i + 1) + " item " + (j + 1) + " \"" + text + "\" -> " + lastBatchListState);
                if (!batchRows.isEmpty() && !bottomRows.isEmpty()) return text;
                clickRowCheckbox("Indent Item List", j);   // no stock: un-tick and try the next item
                waitForAngular(300);
            }
        }
        return "";
    }

    // ---- grid 3: Item Batch List --------------------------------------------------

    /** The <b>Item Batch List</b> grid's rows (available batches for the ticked item). */
    public List<String> itemBatchListRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableAfter(headings('Item Batch List')[0])); }");
        return toList(r);
    }

    // ---- grid 4: Selected Item (bottom, batch-level = StockItemList) --------------

    /** The bottom <b>Selected Item</b> grid's rows (batches actually queued for transfer). */
    public List<String> bottomSelectedItemRows() {
        Object r = page.evaluate("() => {" + JS
                + " const hs=headings('Selected Item'); const h=hs[hs.length-1]; return rowsOf(tableAfter(h)); }");
        return toList(r);
    }

    /**
     * Ensure the bottom <b>Selected Item</b> grid's first row is ticked. Usually already true — the
     * controller auto-selects a batch when {@link #tickFirstIndentItemRow()} runs — so this only clicks
     * if it finds an unticked checkbox; otherwise it's a no-op that confirms the existing state.
     *
     * @return "" if there is no row at all, else the row's text with its checked state noted
     */
    public String ensureBottomSelectedItemTicked() {
        Object r = page.evaluate("() => {" + JS
                + " const hs=headings('Selected Item'); const h=hs[hs.length-1]; const tbl=tableAfter(h);"
                + " const row=tbl?tbl.querySelector('tbody tr'):null; if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); if(!cb) return norm(row.textContent)+' [no checkbox]';"
                + " if(!cb.checked) cb.click();"
                + " return norm(row.textContent)+' [checked='+cb.checked+']'; }");
        String out = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("CSSD.Transfer: bottom Selected Item row -> " + (out.isEmpty() ? "(none)" : out));
        return out;
    }

    /** Click <b>OK</b> ({@code OKClick()}) to close the modal and carry the selection to the main form. */
    public boolean clickOK() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button')].find(x=>/^ok$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__xferOk'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.Transfer.clickOK: OK button not found"); return false; }
        try { page.locator("#__xferOk").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.clickOK: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferOk'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('*')].some(e=>e.children.length===0 && (e.textContent||'').trim()==='Item Search' && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("CSSD.Transfer.clickOK: modal did not close (likely 'Please Select Item !!!')"); }
        waitForAngular(700);
        return onTransferForm();
    }

    // ---- Without Indent -> Get Items (the route that reads real stock) ---------------

    /**
     * Confirmed live 2026-11-02: when no CSSD indent item has stock, the transfer can still be made WITHOUT an indent. On the add-transfer form the <b>Indent Number / Without Indent</b>
     * radio ({@code IssueToStore.IndentNumber} 1 / 2) switches which button is enabled — Get Indent (1) or <b>Get Items</b> ({@code #GetItem}, 2; a real click on it does NOTHING while it is
     * disabled). Get Items needs the form's <b>Issuing Store</b> ({@code IssueToStore.SearchStoreID}) and <b>Requesting Store</b> ({@code IssueToStore.ToStore}) first (its own message:
     * "Please Select Requesting Store"); choosing the Issuing Store may raise an "Urgent Indent(s) Received" alert that must be closed. Its Item Search modal ({@code DirectReturn.*},
     * Search = {@code CommonSearch()}) lists the issuing store's items with <b>AvailableQty</b> (CSSD: 15 of 46 had stock); clicking any cell of an item row
     * ({@code getItemBatchdetails(row)}) fills <b>Items Batch List</b> (checkbox {@code List.IsSelected}), ticking a batch queues it in the bottom <b>Selected Item</b> grid, and OK adds it to the form.
     */
    public String lastStockedItem = "";

    private static final String ISSUE_NG = "IssueToStore.SearchStoreID", REQUEST_NG = "IssueToStore.ToStore";

    /** Close the Get Indent / Item Search modal that is open (its Close button, {@code Cancel()}). */
    public boolean closeModal() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); if(!m) return;"
                + " const b=[...m.querySelectorAll('button')].find(x=>/^\\s*close\\s*$/i.test(x.textContent||'') || /Cancel\\(/.test(x.getAttribute('ng-click')||'')); if(b) b.click(); }");
        waitForAngular(800);
        return !Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal.in')].some(x=>x.offsetWidth||x.offsetHeight)"));
    }

    /** Close an "Alert" dialog (for example "Urgent Indent(s) Received") if one is showing. */
    private void dismissAlertModal() {
        page.waitForTimeout(900);
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>(x.offsetWidth||x.offsetHeight) && /Alert/i.test(x.textContent||''));"
                + " if(!m) return; const b=[...m.querySelectorAll('button')].find(x=>/close|ok/i.test(x.textContent||'')); if(b) b.click(); }");
        waitForAngular(600);
    }

    /** Select the form's Issuing Store and Requesting Store from their lists. Returns "" when both hold, else what went wrong. */
    public String selectMainStores(String issuing, String requesting) {
        String p = com.kpj.pages.Select2Pick.pick(page, ISSUE_NG, issuing);
        dismissAlertModal();
        if (!p.isEmpty()) return "Issuing Store: " + p;
        lastIssuingStore = issuing;
        p = com.kpj.pages.Select2Pick.pick(page, REQUEST_NG, requesting);
        dismissAlertModal();
        if (!p.isEmpty()) return "Requesting Store: " + p;
        lastRequestingStore = requesting;
        return "";
    }

    /** Choose <b>Without Indent</b> and wait for <b>Get Items</b> to become enabled. */
    public boolean chooseWithoutIndent() {
        page.evaluate("() => { const rb=[...document.querySelectorAll(\"input[type=radio][ng-model='IssueToStore.IndentNumber']\")].find(x=>x.value==='2'); if(rb) rb.click(); }");
        try {
            page.waitForFunction("() => { const b=document.getElementById('GetItem'); return !!b && !b.disabled; }", null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { System.out.println("CSSD.Transfer.chooseWithoutIndent: Get Items stayed disabled"); return false; }
        waitForAngular(500);
        return true;
    }

    /** Click <b>Get Items</b> and wait for the Item Search modal ({@code DirectReturn.ItemCode} visible). */
    public boolean clickGetItems() {
        try { page.locator("#GetItem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.clickGetItems: click failed - " + e.getMessage()); return false; }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='DirectReturn.ItemCode']\")].find(e=>e.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CSSD.Transfer.clickGetItems: the Item Search modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Click the Item Search modal's <b>Search</b> ({@code CommonSearch()}) and wait for item rows. */
    public boolean itemSearch() {
        Object tagged = page.evaluate("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button')].find(x=>/CommonSearch/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__xferItemSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#__xferItemSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.itemSearch: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferItemSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); return !!m && m.querySelectorAll('.ui-grid-body .ui-grid-row, .ui-grid-render-container-body .ui-grid-row').length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        waitForAngular(500);
        return true;
    }

    /**
     * The first item of the Item Search result that has stock, read from the grid's own data (every page, not only the rows drawn): {@code "code | name | AvailableQty"}, or "" when none has any.
     */
    public String findStockedItem() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); const row=m&&m.querySelector('.ui-grid-row'); if(!row) return '';"
                + " const s=window.angular.element(row).scope(); const d=(s.grid&&s.grid.options&&s.grid.options.data)||[];"
                + " const hit=d.find(x=>+x.AvailableQty>0); return hit ? (hit.serviceitemcode+' | '+hit.serviceitemname+' | '+hit.AvailableQty) : ''; }");
        lastStockedItem = r == null ? "" : r.toString();
        return lastStockedItem;
    }

    /**
     * Narrow the modal to one item by its code (Item Code box + Search), click that item's row — any cell, {@code getItemBatchdetails(row)} — and wait for <b>Items Batch List</b> to fill.
     *
     * @return the number of batches listed (0 = the click did nothing)
     */
    public int tickStockedItem(String code) {
        page.evaluate("(c) => {" + JS + " const e=byNg('DirectReturn.ItemCode'); if(e){ const k=A.element(e).controller('ngModel'); e.value=c; if(k){ k.$setViewValue(c); k.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
        if (!itemSearch()) return 0;
        // The row's own tick in the LEFT ("Sel") container is what fires getItemBatchdetails(row) — a click on the Item Name cell only lands in that cell's editor and lists no batch.
        Object tagged = page.evaluate("(c) => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight);"
                + " const body=[...m.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')]; const left=[...m.querySelectorAll('.ui-grid-render-container-left .ui-grid-row')];"
                + " let i=body.findIndex(r=>(r.textContent||'').indexOf(c)>=0); if(i<0) i=0; const row=left[i]; if(!row) return false;"
                + " const cell=row.querySelector('.ui-grid-cell:has(i), .ui-grid-cell:has(.fa)') || [...row.querySelectorAll('.ui-grid-cell')].find(x=>/getItemBatchdetails/.test(x.getAttribute('ng-click')||'')) || row.querySelector('.ui-grid-cell'); if(!cell) return false;"
                + " cell.scrollIntoView({block:'center'}); cell.id='__xferItemCell'; return true; }", code);
        if (!Boolean.TRUE.equals(tagged)) return 0;
        try { page.locator("#__xferItemCell").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.tickStockedItem: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferItemCell'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Quantity']); return !!t && rowsOf(t).length > 0; }", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(400);
        return stockedBatchRows().size();
    }

    /**
     * The modal's tables are found by their COLUMN HEADERS: the "Items Batch List" / "Selected Item" headings are not reliable anchors in this window's layout (the heading lookup used by the Get
     * Indent window finds no table here, so the batches looked absent although the server had answered them).
     */
    private static final String MODAL_TABLES =
            "const mod=()=>[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight);"
          + "const tblWith=(parts)=>{ const m=mod(); if(!m) return null; return [...m.querySelectorAll('table')].find(t=>{"
          + "  const h=norm([...t.querySelectorAll('th')].map(x=>x.textContent).join(' ')); return parts.every(p=>h.indexOf(p)>=0); }) || null; };";

    /** The rows of the Get Items modal's <b>Items Batch List</b> (the table with Batch Code + Available Quantity). */
    public List<String> stockedBatchRows() {
        Object r = page.evaluate("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Quantity']); return t? rowsOf(t) : []; }");
        return toList(r);
    }

    /** The rows of the Get Items modal's bottom <b>Selected Item</b> grid (the table with Batch Code + Available Stock). */
    public List<String> stockedSelectedRows() {
        Object r = page.evaluate("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Stock']); return t? rowsOf(t) : []; }");
        return toList(r);
    }

    /** Make sure the first row of the Get Items modal's Selected Item grid is ticked (OK only takes ticked rows). Returns the row's text with its state, "" when there is no row. */
    public String ensureStockedSelectionTicked() {
        Object r = page.evaluate("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Stock']); const row=t&&t.querySelector('tbody tr'); if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); if(!cb) return norm(row.textContent)+' [no checkbox]'; if(!cb.checked) cb.click(); return norm(row.textContent)+' [checked='+cb.checked+']'; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Tick the first batch that has available quantity and wait for it to be queued in the bottom <b>Selected Item</b> grid. Returns that grid's first row ("" if nothing was queued). */
    public String tickFirstStockedBatch() {
        Object tagged = page.evaluate("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Quantity']); const rows=t?[...t.querySelectorAll('tbody tr')]:[];"
                + " const row=rows.find(r=>r.querySelector('input[type=checkbox]') && [...r.querySelectorAll('td')].some(td=>/^\\d+(\\.\\d+)?$/.test(norm(td.textContent)) && +norm(td.textContent)>0)) || rows[0];"
                + " if(!row) return false; const cb=row.querySelector('input[type=checkbox]'); if(!cb) return false; cb.scrollIntoView({block:'center'}); cb.id='__xferBatchCb'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return "";
        try { page.locator("#__xferBatchCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.tickFirstStockedBatch: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferBatchCb'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => {" + JS + MODAL_TABLES + " const t=tblWith(['Batch Code','Available Stock']); return !!t && rowsOf(t).length > 0; }", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(400);
        List<String> bottom = stockedSelectedRows();
        return bottom.isEmpty() ? "" : bottom.get(0);
    }

    // ---- main form: Unit Quantity + Save ------------------------------------------

    /**
     * Enter the <b>Unit Quantity</b> on the item row the modal just added to the main form's grid.
     *
     * <p>The visible column header reads "Link Quantity", but the bound field is really
     * {@code Row.unitqty} (confirmed in {@code IssueToStoreController.js}'s
     * {@code CalculateTransferQuantity}) — so this targets any grid input whose {@code ng-model} contains
     * "unitqty", not the "Link Quantity"-labelled one.</p>
     */
    public String enterUnitQuantity(String qty) {
        Object r = page.evaluate("(v) => {" + JS
                + " const e=[...document.querySelectorAll('input')].find(i=>vis(i) && /unitqty/i.test(i.getAttribute('ng-model')||''));"
                + " if(!e) return '(no-field)';"
                + " const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }", qty);
        lastUnitQty = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("CSSD.Transfer: Unit Quantity = " + lastUnitQty);
        return lastUnitQty;
    }

    /** Click <b>Save</b> ({@code IUDSave($event)}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDSave/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__xferSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("CSSD.Transfer.save: Save button not found"); return ""; }

        try { page.locator("#__xferSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CSSD.Transfer.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__xferSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__xferToasts||[]).some(a=>/saved|success|please|select|enter|required|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__xferToasts||[]).includes(t)) (window.__xferToasts=window.__xferToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__xferToasts||[];"
                + " return a.find(x=>/saved\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim().replaceFirst("^[^A-Za-z0-9]*KPJ\\s*Portal\\s*", "");
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("CSSD.Transfer: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
