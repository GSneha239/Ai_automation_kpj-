package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Blood Bag Opening Stock</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>Blood Bag Opening Stock</b> (list, route
 * {@code #/BloodBagOpeningStockList}: From / To date, Search, Add and a grid Blood Bag No. | Blood Bag Type | Component Name |
 * Blood Group) → <b>Add</b> ({@code AddBloodBagOpeningStock()}, route {@code #/add-BloodBagOpeningStock}).</p>
 *
 * <p><b>The add form is a two-stage form.</b> The entry fields — <b>Blood Bag No.</b> ({@code BloodBagOpeningStock.bloodbagno},
 * max 20), <b>Bag Weight(gm)</b>, <b>Blood Bag Type</b> / <b>Component</b> / <b>Blood Group</b> / <b>Technician</b> / <b>BTO</b>
 * (select2-wrapped native selects, ids {@code bbBloodBagType} / {@code bbComponent} / {@code bbBloodGrp} / {@code bbTechnician} /
 * {@code bbBTO}) and <b>Remark</b> — are staged with the form's own <b>Add</b> ({@code AddDetails()}), which moves them into the
 * grid {@code #Bloodbagstock} above the form and clears the fields (no toast). Only <b>Save</b> ({@code IUDOperations()}) sends
 * them to the server: it answers "Record Save successfully." and returns to the list. <b>Collected Date</b> / <b>Collected
 * Time</b> default to now and <b>Expiry Date</b> (read-only) is worked out by the screen from the chosen Component.</p>
 *
 * <p>The dropdowns carry leftover test data ("Packed Red Blood Cells 011388", blood groups "A Negative MVEHURNJ0", components
 * "test" / "new"), so they are picked from the clean entries only. The list grid shows only Bag No., Type, Component and Blood
 * Group — weight, technician, BTO and remark are checked on the staged row, before Save.</p>
 *
 * <p><b>Known defect (2026-10-29):</b> 32 of the 33 Blood Bag Type entries share one id, so only the first of them ("Apheresis Bag")
 * can be held by the select and the model, and the list shows a saved bag once per type that shares its id. The page picks only
 * entries that can be held, and {@link #describeTypeIds()} names the clash for the report.</p>
 */
public class BloodBagOpeningStock extends BasePage {

    public BloodBagOpeningStock(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/BloodBagOpeningStockList";

    private static final String NG = "BloodBagOpeningStock.";
    private static final String ADD_LIST = "[ng-click='AddBloodBagOpeningStock()']";
    private static final String ADD_ROW = "button[ng-click='AddDetails()']";
    private static final String SAVE = "button[ng-click='IUDOperations()']";
    private static final String SEARCH = "[ng-click='GetBloodBagOpeningStockdetails()']";

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastAdd = "", lastEntry = "", lastStaged = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastDefaults = "";
    public String lastBagNo = "", lastWeight = "", lastType = "", lastComponent = "", lastGroup = "", lastTechnician = "",
            lastBto = "", lastRemark = "", lastExpiry = "", lastTypeIds = "";
    /** How many grid rows the saved bag came back as, and how many distinct records / blood bag types those rows carry. */
    public int lastRowsForBag = 0, lastRecordsForBag = 0;

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Ancillary Services &rarr; Blood Bank &rarr; Blood Bag Opening Stock.
     *
     * <p>Each level is opened only if it is closed (a second click on an open entry CLOSES it) and the next one is looked up
     * INSIDE its parent's {@code li}: the sidebar is built lazily and other top-level modules share words with these labels. The
     * app bounces a typed hash back to the dashboard, so the menu link is the only way in.</p>
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*ancillary\\s*services?\\s*$");
        expandMenuEntry("^\\s*ancillary\\s*services?\\s*$", "^\\s*blood\\s*bank\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*ancillary\\s*services?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bb=[...topLi.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(bb&&bb.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*blood\\s*bag\\s*opening\\s*stock\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__bbMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__bbMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("BloodBagOpeningStock.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__bbMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onListScreen();
    }

    /** Every visible menu entry — used to pin the real label when the menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On the list screen: the route AND the screen's own Add button must be there (a bounce to the dashboard has neither). */
    public boolean onListScreen() {
        if (!page.url().toLowerCase().contains("bloodbagopeningstocklist")) return false;
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + ADD_LIST + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return true;
    }

    /** On the add form: its own Blood Bag No. box is showing. */
    public boolean onAddForm() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + NG + "bloodbagno')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(!vis(e) && !(e.tagName==='SELECT' && ng)) continue;"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options|datepicker|Day\\(|Month\\(|Year/i.test(ng+' '+click)) continue;"
                + "   if(/page(First|Previous|Next|Last)PageClick|openMic|toggle\\(/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+' maxlength='+(e.getAttribute('maxlength')||'-')); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " if(hdr.length) out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== BloodBagOpeningStock CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the list's <b>Add</b> and wait for the form's Blood Bag No. box. Lands on {@code #/add-BloodBagOpeningStock}. */
    public boolean clickAdd() {
        try { page.locator(ADD_LIST).first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { lastAdd = "the Add button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        boolean open = onAddForm();
        waitForAngular(800);
        lastAdd = open ? "Add clicked; the form opened at " + page.url() : "Add clicked but the form did not open (" + page.url() + ")";
        return open;
    }

    /** The form's <b>Back</b> ({@code closeForm()}) — used to reach the list again without typing a hash (that raises "Reload page"). */
    public boolean clickBack() {
        try { page.locator("[ng-click='closeForm()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        waitForAngular(1500);
        return onListScreen();
    }

    /**
     * Fill the whole entry form. Bag No. and weight are TYPED (fill fires the input event the model listens to); the selects are
     * driven the way the screen's other pages do it — set the option, then raise change for Angular and for select2 so the widget
     * shows it. Type, Component and Blood Group rotate with the attempt through the clean entries; Technician and BTO are two
     * different people so a swap between them would show up in the read-back.
     *
     * @return a one-line summary of what was entered
     */
    public String fillDetails(int attempt) {
        // The unique code is the Blood Bag No.: "BB" + the wall-clock date and time to the second (ddMMyyHHmmss, 14 characters, the box
        // takes 20), plus the attempt number on a retry. nanoTime is NOT used: on Windows it ticks in 100 ns steps, so every stamp ended
        // in "00" and only 10,000 distinct numbers were possible.
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        lastBagNo = "BB" + stamp + (attempt > 0 ? String.valueOf(attempt) : "");
        int seed = Integer.parseInt(stamp.substring(stamp.length() - 6)) / 7 + attempt;
        lastWeight = String.valueOf(350 + (seed % 151));
        lastRemark = "Auto test blood bag opening stock " + lastBagNo;

        page.locator("input[ng-model='" + NG + "bloodbagno']").fill(lastBagNo);
        page.locator("input[ng-model='" + NG + "bloodbagweight']").fill(lastWeight);
        lastType = pick("bloodbagtypeid", seed);
        lastComponent = pick("componentid", seed + 1);
        lastGroup = pick("bloodgroupid", seed + 2);
        lastTechnician = pick("technicianid", 0);
        lastBto = pick("btoid", 1);
        page.locator("textarea[ng-model='" + NG + "remark']").fill(lastRemark);
        waitForAngular(500);

        Object d = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e?e.value:''; };"
                + " return 'Collected Date '+g('" + NG + "collecteddate')+', Collected Time '+g('inputTime')+', Expiry Date '+g('" + NG + "expirydate'); }");
        lastDefaults = d == null ? "" : d.toString();
        Object x = page.evaluate("() => { const e=document.querySelector(\"[ng-model='" + NG + "expirydate']\"); return e?e.value:''; }");
        lastExpiry = x == null ? "" : x.toString();
        describeTypeIds();

        lastEntry = "Blood Bag No. \"" + lastBagNo + "\", Bag Weight \"" + lastWeight + "\" gm, Blood Bag Type \"" + lastType
                + "\", Component \"" + lastComponent + "\", Blood Group \"" + lastGroup + "\", Technician \"" + lastTechnician
                + "\", BTO \"" + lastBto + "\", Remark \"" + lastRemark + "\" (" + lastDefaults + ")";
        System.out.println("BloodBagOpeningStock: entered -> " + lastEntry);
        return lastEntry;
    }

    /**
     * Which Blood Bag Type entries share an id. Seen 2026-10-29: 32 of the 33 types carry the same value ({@code number:14282}), so the
     * select and the form's model cannot hold any of them but the first ("Apheresis Bag"), the saved bag is stored under that one id,
     * and the list — which finds the types by id — returns the one bag once per type that shares it. Empty when every id is its own.
     */
    public String describeTypeIds() {
        Object r = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + NG + "bloodbagtypeid']\"); if(!e) return '';"
                + " const by={}; [...e.options].filter(o=>o.value).forEach(o=>{ (by[o.value]=by[o.value]||[]).push(norm(o.textContent)); });"
                + " const all=Object.values(by).reduce((n,l)=>n+l.length,0);"
                + " const big=Object.entries(by).sort((a,b)=>b[1].length-a[1].length)[0];"
                + " if(!big || big[1].length<2) return '';"
                + " return big[1].length+' of the '+all+' Blood Bag Type entries share the id \"'+big[0]+'\" ('+big[1].slice(0,4).join(', ')+', ...)'; }");
        lastTypeIds = r == null ? "" : r.toString();
        return lastTypeIds;
    }

    /**
     * Choose a CLEAN entry of one of the form's selects: not a number-suffixed or token-suffixed leftover and not a placeholder
     * name. {@code index} wraps around the clean entries, so a retry gets a different one.
     */
    private String pick(String field, int index) {
        Object r = page.evaluate("async ([ng, idx]) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '';"
                + " const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
                + " const clean=t=>!/\\d{4,}/.test(t) && !/\\b(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]{7,}\\b/.test(t)"
                + "   && !/^(new|test|test1|tested|testvasanth|bbbb|0 negative)$/i.test(t);"
                + " for(let k=0;k<20 && ![...e.options].some(real);k++) await new Promise(r=>setTimeout(r,400));"
                // An entry that shares its value with an earlier one can never be held: the select (and the model) snap to the FIRST
                // entry with that value, whatever was chosen — so only the first entry of each value is offered.
                + " const firstOfValue=o=>[...e.options].find(x=>x.value===o.value)===o;"
                + " let opts=[...e.options].filter(o=>real(o) && clean(norm(o.textContent)) && firstOfValue(o));"
                + " if(!opts.length) opts=[...e.options].filter(o=>real(o) && firstOfValue(o));"
                + " if(!opts.length) opts=[...e.options].filter(real);"
                + " if(!opts.length) return '';"
                + " const o=opts[((idx%opts.length)+opts.length)%opts.length];"
                + " e.selectedIndex=[...e.options].indexOf(o); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ window.angular.element(e).triggerHandler('change'); }catch(x){}"
                + " try{ window.jQuery(e).trigger('change'); }catch(x){}"
                + " await new Promise(r=>setTimeout(r,900));"
                + " return norm(o.textContent); }",
                java.util.Arrays.asList(NG + field, index));
        return r == null ? "" : r.toString();
    }

    /** Read the entry form back and confirm every box holds what was entered (the selects by their chosen option's text). */
    public boolean detailsEntered() {
        Object r = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"[ng-model='" + NG + "\"+n+\"']\"); if(!e) return '(missing)';"
                + "   return e.tagName==='SELECT' ? (e.selectedIndex>0 ? norm(e.options[e.selectedIndex].textContent) : '') : norm(e.value); };"
                + " return JSON.stringify({no:g('bloodbagno'),wt:g('bloodbagweight'),type:g('bloodbagtypeid'),comp:g('componentid'),"
                + "   grp:g('bloodgroupid'),tech:g('technicianid'),bto:g('btoid'),rem:g('remark')}); }");
        String s = r == null ? "" : r.toString();
        boolean ok = s.contains("\"no\":\"" + lastBagNo + "\"") && s.contains("\"wt\":\"" + lastWeight + "\"")
                && s.contains("\"type\":\"" + lastType + "\"") && s.contains("\"comp\":\"" + lastComponent + "\"")
                && s.contains("\"grp\":\"" + lastGroup + "\"") && s.contains("\"tech\":\"" + lastTechnician + "\"")
                && s.contains("\"bto\":\"" + lastBto + "\"") && s.contains("\"rem\":\"" + lastRemark + "\"");
        if (!ok) lastEntry += "  ||  READ BACK: " + s;
        return ok;
    }

    /**
     * Click the form's <b>Add</b> and check the staged row. The bag leaves the entry boxes and appears in the grid above the form
     * (Bag No | Component | Technician | BTO | Collection Date | Collection Time | Blood Group | Weight | Blood Bag Type | Remark |
     * Expiry Date | Delete) — every value is compared with what was entered. A message toast (if the screen raises one instead,
     * e.g. a missing mandatory field) is reported.
     */
    public boolean addToStock() {
        armToasts();
        try { page.locator(ADD_ROW).first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { lastStaged = "the form's Add button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        waitForAngular(1500);
        Object r = page.evaluate("(a) => {" + JS
                + " const t=document.getElementById('Bloodbagstock');"
                + " const rows=t?[...t.querySelectorAll('tbody tr')].map(r=>[...r.querySelectorAll('td')].map(d=>norm(d.textContent))).filter(c=>c.some(x=>x)):[];"
                + " const mine=rows.find(c=>c[0]===a.no);"
                + " const toast=(window.__bbToasts||[]).join(' || ');"
                + " if(!mine) return 'NOT STAGED: the grid holds '+rows.length+' row(s)'+(rows.length?' ['+rows.map(c=>c[0]).join(', ')+']':'')+(toast?' - message: '+toast:'');"
                + " const want=[a.no,a.comp,a.tech,a.bto,null,null,a.grp,a.wt,a.type,a.rem,null];"
                + " const names=['Bag No','Component','Technician','BTO','Collection Date','Collection Time','Blood Group','Weight','Blood Bag Type','Remark','Expiry Date'];"
                + " const bad=[]; want.forEach((w,i)=>{ if(w!==null && mine[i]!==w) bad.push(names[i]+' is \"'+mine[i]+'\" (entered \"'+w+'\")'); });"
                + " const left=['bloodbagno','bloodbagweight'].map(n=>{ const e=document.querySelector(\"[ng-model='" + NG + "\"+n+\"']\"); return e?e.value:''; });"
                + " return (bad.length?'MISMATCH: '+bad.join('; '):'STAGED: '+names.map((n,i)=>n+'=\"'+mine[i]+'\"').join(' | '))"
                + "   + ' | entry boxes cleared='+(!left[0]&&!left[1]); }",
                java.util.Map.of("no", lastBagNo, "comp", lastComponent, "tech", lastTechnician, "bto", lastBto, "grp", lastGroup,
                        "wt", lastWeight, "type", lastType, "rem", lastRemark));
        lastStaged = r == null ? "" : r.toString();
        System.out.println("BloodBagOpeningStock: add to stock -> " + lastStaged);
        return lastStaged.startsWith("STAGED:");
    }

    /** Installs a fresh toast collector. Must run BEFORE the click — toasts fade in about a second. */
    private void armToasts() {
        page.evaluate("() => {" + JS
                + " window.__bbToasts=[]; if(window.__bbObs) window.__bbObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__bbToasts.includes(t)) window.__bbToasts.push(t); }); };"
                + " window.__bbObs=new MutationObserver(grab); window.__bbObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /**
     * A success message, judged strictly: these screens phrase refusals with words like "Already Added!" — so a plain
     * {@code contains("added")} would read a rejection as a save.
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ") || t.contains("fail") || t.contains("error")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** "… already exists / already added" — the cue to retry with a different bag number. */
    public static boolean isDuplicate(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /**
     * Click <b>Save</b> ({@code IUDOperations()}) and return the toast. The server call ({@code /api/BloodBagOpeningStock/IUD}) is
     * watched too, so a refusal without a toast is still explained. Answers the "Do You Want To Save" dialog if one is raised.
     */
    public String saveAndGetToast() {
        final StringBuilder net = new StringBuilder();
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            if (r.url().toLowerCase().contains("/api/bloodbagopeningstock/iud")) {
                String body = "";
                try { body = r.text(); } catch (Exception ignore) { }
                net.append("IUD answered HTTP ").append(r.status()).append(body.isBlank() ? "" : " " + body.substring(0, Math.min(160, body.length())));
            }
        };
        page.onResponse(onResp);
        try {
            armToasts();
            try { page.locator(SAVE).first().click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                lastSaveDiagnostics = "the Save button could not be clicked: " + e.getMessage().split("\n")[0];
                lastToast = "";
                return "";
            }
            // A confirm may or may not appear — answer it whenever it is there, then wait for the toast.
            long deadline = System.currentTimeMillis() + 20000;
            String toast = "";
            while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
                try { acceptSaveDialog(); } catch (Exception ignore) { }
                try {
                    Object now = page.evaluate("() => (window.__bbToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                    toast = now == null ? "" : now.toString();
                } catch (Exception ignore) { }
                if (toast.isEmpty()) page.waitForTimeout(500);
            }
            // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
            toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").replaceFirst("^\\s*\\W*KPJ Portal\\s*", "").trim();
            lastSaveDiagnostics = (toast.isEmpty() ? "Save clicked; no message within 20s" : "Save clicked")
                    + (net.length() > 0 ? " (" + net + ")" : " (no IUD call was seen)");
            lastToast = toast;
            System.out.println("BloodBagOpeningStock: save -> toast=\"" + toast + "\" " + lastSaveDiagnostics);
            waitForAngular(1200);
            return toast;
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
    }

    /**
     * Look the saved bag up in the list. A successful Save returns to the list by itself; otherwise the form's Back is used.
     * <b>Search</b> is pressed (From / To default to today, which is the collected date), the grid's Blood Bag No. filter is set to
     * the bag number, and the grid's rows are read: the bag must come back with the Blood Bag Type, Component Name AND Blood Group
     * that were entered. The rows are read from the grid's data (the body is virtualised, so only part of it is in the DOM) and the
     * filtered grid is left showing for the screenshot.
     *
     * <p>The count of rows the one bag comes back as, and of distinct records among them, is kept in {@link #lastRowsForBag} /
     * {@link #lastRecordsForBag}: the list should show a saved bag once.</p>
     */
    public boolean verifyInTable() {
        waitForAngular(1200);
        try {
            if (page.url().toLowerCase().contains("add-")) clickBack();
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')].some(r=>/\\S/.test(r.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        try {
            try { page.locator(SEARCH).first().click(new Locator.ClickOptions().setTimeout(5000)); waitForAngular(2500); }
            catch (Exception e) { System.out.println("BloodBagOpeningStock.verifyInTable: Search click failed - " + e.getMessage().split("\n")[0]); }
            Object where = page.evaluate("(c) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis);"
                    + " const inBag=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell'); return h && /blood\\s*bag\\s*no/i.test(norm(h.textContent)); });"
                    + " const f=inBag||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel'); if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'filtered '+(inBag?'the Blood Bag No. column':(f?'the first column':'NOTHING - no filter box')); }",
                    lastBagNo);
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const g=document.querySelector('[ui-grid]'); const sc=g?window.angular.element(g).scope():null;"
                    + " const data=(sc&&sc.gridOptions&&sc.gridOptions.data)||[];"
                    + " const same=(x,y)=>norm(x).toLowerCase()===norm(y).toLowerCase();"
                    + " const mine=data.filter(d=>same(d.bloodbagno,a.no));"
                    + " const ids=new Set(mine.map(d=>d.id)); const types=new Set(mine.map(d=>d.bloodbagtypename));"
                    + " const full=mine.find(d=>same(d.bloodbagtypename,a.type)&&same(d.componentname,a.comp)&&same(d.bloodgroupname,a.grp));"
                    + " const shown=[...document.querySelectorAll('.ui-grid-row')].filter(vis).filter(r=>/\\S/.test(r.textContent||'')).length;"
                    + " const pager=norm((document.querySelector('.ui-grid-pager-count')||{}).textContent);"
                    + " const head=mine.length+'|'+ids.size+'|'+types.size+'|';"
                    + " if(full) return head+'FOUND: Blood Bag No=\"'+full.bloodbagno+'\" | Blood Bag Type=\"'+full.bloodbagtypename+'\" | Component Name=\"'+full.componentname+'\" | Blood Group=\"'+full.bloodgroupname+'\" ('+data.length+' rows in the list, '+shown+' showing after the filter, '+pager+')';"
                    + " if(mine.length) return head+'the bag is listed but none of its '+mine.length+' row(s) reads Type \"'+a.type+'\" + Component \"'+a.comp+'\" + Group \"'+a.grp+'\" (first row: '+mine[0].bloodbagtypename+' / '+mine[0].componentname+' / '+mine[0].bloodgroupname+')';"
                    + " return head+'not in the list ('+data.length+' rows listed for the day)'; }",
                    java.util.Map.of("no", lastBagNo, "type", lastType, "comp", lastComponent, "grp", lastGroup));
            String h = hit == null ? "" : hit.toString();
            String[] parts = h.split("\\|", 4);
            try {
                lastRowsForBag = Integer.parseInt(parts[0]);
                lastRecordsForBag = Integer.parseInt(parts[1]);
            } catch (Exception e) { lastRowsForBag = 0; lastRecordsForBag = 0; }
            String text = parts.length > 3 ? parts[3] : h;
            lastListCheck = "looked for Blood Bag No \"" + lastBagNo + "\" + Type \"" + lastType + "\" + Component \"" + lastComponent
                    + "\" + Group \"" + lastGroup + "\" (" + (where == null ? "" : where) + ") -> " + text
                    + "  [rows for this bag: " + lastRowsForBag + ", distinct records: " + lastRecordsForBag
                    + (parts.length > 2 ? ", distinct types shown: " + parts[2] : "") + "]";
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("BloodBagOpeningStock: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
