package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>BloodBag Discard/Return</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>BloodBag Discard/Return</b> (list, route {@code #/BloodBagDiscardList}:
 * From / To date, Search and a grid Extracted Blood Bag No. | Discard Date | Discard Time | Discard Reason) → <b>Add</b> (a LINK,
 * {@code href="#add-BloodBagDiscard"}, not a button) → form {@code #/add-BloodBagDiscard}.</p>
 *
 * <p><b>The form is two-stage, like Blood Bag Opening Stock.</b> <b>Extract Blood Bag No.</b> ({@code BloodBagDiscard.extractionid}),
 * <b>BTO</b> ({@code btoid}) and <b>Reason Of Discarded</b> ({@code discardreasonid}) are select2-wrapped selects; Discarded Date / Time
 * default to now, and Technician, Attendant, Return / Return To and Technical Remark are optional and left alone. The form's own
 * <b>Add</b> ({@code FnAddBloodBagDiscard()}) stages the bag in the table {@code #Bloodbagstock} (Blood Bag No. | Discard Date | Discard
 * Time | Discard Reason | BTO | Attendant | Technician | Technician Remark | Return | Return To) and clears the boxes — no toast. Only
 * <b>Save</b> ({@code fnIUDBloodBagDiscard()}, {@code /api/BloodBagDiscard/IUD}) sends it.</p>
 *
 * <p><b>A discard CONSUMES a bag.</b> The Extract Blood Bag No. list holds "{component}-{bag no}" entries; a discarded one leaves it. The
 * page picks only bags the test suite itself created (Blood Bag Opening Stock numbers {@code BB…}) so nobody else's stock is touched,
 * and says so when none is left. The Reason list carries leftovers ("Broken Bag MV0XWTVD0"), so it is picked from the clean entries.</p>
 *
 * <p>The list shows the saved discard as {@code bloodbagno} / {@code discardreason} (grid data). To tell the new row from older ones,
 * the row count and the highest {@code id} are recorded BEFORE the form is opened ({@link #baseline}).</p>
 */
public class BloodBagDiscardReturn extends BasePage {

    public BloodBagDiscardReturn(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/BloodBagDiscardList";

    private static final String NG = "BloodBagDiscard.";
    private static final String ADD_LINK = "a[href='#add-BloodBagDiscard']";
    private static final String ADD_ROW = "button[ng-click='FnAddBloodBagDiscard()']";
    private static final String SAVE = "button[ng-click='fnIUDBloodBagDiscard()']";
    private static final String SEARCH = "[ng-click='fnGetRequisitionGridWithFilter()']";

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastAdd = "", lastBaseline = "", lastEntry = "", lastStaged = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastBagOptions = "";
    public String lastBag = "", lastBagNo = "", lastBto = "", lastReason = "";
    /** Row count and highest {@code id} seen in the list BEFORE the new discard was saved. */
    public int baselineMaxId = -1, baselineRows = -1;

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Ancillary Services &rarr; Blood Bank &rarr; BloodBag Discard/Return. Each level is opened only if it is closed (a second click on
     * an open entry CLOSES it) and the next one is looked up INSIDE its parent's {@code li}; the app bounces a typed hash back to the
     * dashboard, so the menu link is the only way in.
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
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*bloodbag\\s*discard\\s*\\/?\\s*return\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__bdMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__bdMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("BloodBagDiscardReturn.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__bdMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On the list screen: the route AND the screen's own Add link must be there (a bounce to the dashboard has neither). */
    public boolean onListScreen() {
        if (!page.url().toLowerCase().endsWith("#/bloodbagdiscardlist")) return false;
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + ADD_LINK + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return true;
    }

    /** On the form: its own Add button (the one that stages a bag) is showing. */
    public boolean onForm() {
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + ADD_ROW + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[href]')){"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(e.tagName==='A' && !/^#add-/.test(e.getAttribute('href')||'')) continue;"
                + "   if(!vis(e) && !(e.tagName==='SELECT' && ng)) continue;"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options|datepicker|^hours$|^minutes$|^seconds$/i.test(ng+' '+click)) continue;"
                + "   if(/page(First|Previous|Next|Last)PageClick|openMic|toggle\\(|setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push((e.tagName==='A'?'LINK \"':'BTN \"')+t.slice(0,40)+'\" ['+(e.tagName==='A'?'href='+e.getAttribute('href'):'ng-click='+click)+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+(e.disabled?' (disabled)':'')); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " if(hdr.length) out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== BloodBagDiscardReturn CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- list: baseline ---------------------------------------------------

    /** One JS pass over the grid's raw data: row count, highest id and, optionally, the rows newer than that id. */
    private static final String LIST_READ = "(a) => {" + JS
            + " const g=document.querySelector('[ui-grid]'); const sc=g?window.angular.element(g).scope():null;"
            + " const data=(sc&&sc.gridOptions&&sc.gridOptions.data)||[];"
            + " const max=data.reduce((n,d)=>Math.max(n,parseInt(d.id,10)||0),0);"
            + " if(!a) return JSON.stringify({rows:data.length,max:max});"
            + " const fresh=data.filter(d=>(parseInt(d.id,10)||0)>a.max);"
            + " const mine=fresh.filter(d=>norm(d.bloodbagno).toLowerCase().includes(a.bag.toLowerCase()) && norm(d.discardreason).toLowerCase()===a.reason.toLowerCase());"
            + " const row=mine[0];"
            + " return JSON.stringify({rows:data.length,max:max,fresh:fresh.length,mine:mine.length,"
            + "   id:row?row.id:0, bag:row?norm(row.bloodbagno):'', reason:row?norm(row.discardreason):'', date:row?norm(row.discarddate):'', time:row?norm(row.discardtime):'',"
            + "   firstFresh:fresh[0]?[fresh[0].id,fresh[0].bloodbagno,fresh[0].discardreason].join(' / '):''}); }";

    /**
     * Record how the list looks BEFORE anything is saved (the list defaults to today, which is the Discarded Date the form will
     * carry): the row count and the highest {@code id}. Call it on the list screen, before {@link #clickAdd}.
     */
    public String baseline() {
        waitForAngular(1500);
        Object r = page.evaluate(LIST_READ, (Object) null);
        String s = r == null ? "" : r.toString();
        baselineRows = intOf(s, "rows");
        baselineMaxId = intOf(s, "max");
        lastBaseline = "the list holds " + baselineRows + " discard(s) for today; highest id " + baselineMaxId;
        System.out.println("BloodBagDiscardReturn: baseline -> " + lastBaseline);
        return lastBaseline;
    }

    private static int intOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":(-?\\d+)").matcher(json == null ? "" : json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String strOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : "";
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the list's <b>Add</b> link and wait for the form. Lands on {@code #/add-BloodBagDiscard}. */
    public boolean clickAdd() {
        try { page.locator(ADD_LINK).first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { lastAdd = "the Add link could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        boolean open = onForm();
        waitForAngular(1200);   // the select lists fill after the form opens
        lastAdd = open ? "Add clicked; the form opened at " + page.url() : "Add clicked but the form did not open (" + page.url() + ")";
        return open;
    }

    /** The form's <b>Back</b> ({@code CloseForm()}) — used to reach the list again without typing a hash (that raises "Reload page"). */
    public boolean clickBack() {
        try { page.locator("[ng-click='CloseForm()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        waitForAngular(1500);
        return onListScreen();
    }

    /**
     * Choose <b>Extract Blood Bag No.</b>, <b>BTO</b> and <b>Reason Of Discarded</b>. The bag is the first "{component}-BB{digits}" entry —
     * a bag the test suite created, never someone else's stock; if there is none the entry is left empty and {@link #lastBagOptions}
     * says what the list offered. The BTO is the second person in the list ("Employee 2" — the staged row shows the logged-in user for
     * Technician and Attendant, so a different BTO shows up on its own) and the reason rotates through the clean entries.
     *
     * @return a one-line summary of what was chosen
     */
    public String fillDetails(int attempt) {
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        int seed = Integer.parseInt(stamp.substring(stamp.length() - 6)) / 7 + attempt;

        Object opts = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + NG + "extractionid']\"); if(!e) return '(no select)';"
                + " return [...e.options].filter(o=>o.value).map(o=>norm(o.textContent)).join(' | '); }");
        lastBagOptions = opts == null ? "" : opts.toString();
        lastBag = pick("extractionid", "-BB\\d+$", 0, false);
        lastBagNo = lastBag.contains("-") ? lastBag.substring(lastBag.lastIndexOf('-') + 1) : lastBag;
        lastBto = pick("btoid", null, 1, false);
        lastReason = pick("discardreasonid", null, seed, true);
        waitForAngular(500);
        lastEntry = "Extract Blood Bag No. \"" + lastBag + "\", BTO \"" + lastBto + "\", Reason Of Discarded \"" + lastReason + "\"";
        System.out.println("BloodBagDiscardReturn: entered -> " + lastEntry);
        return lastEntry;
    }

    /**
     * Choose an entry of one of the form's selects. The select is hidden behind select2, so the option is set and change raised for
     * Angular and for select2 (which then shows it).
     *
     * @param pattern   regex the entry's text must match (or {@code null} for any real entry)
     * @param index     which match to take (wraps around the matches)
     * @param cleanOnly skip leftovers carrying a generated token ("… MV0XWTVD0") or a long number
     * @return the entry's text, or {@code ""} when nothing matched
     */
    private String pick(String field, String pattern, int index, boolean cleanOnly) {
        Object r = page.evaluate("async ([ng, pat, idx, clean]) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '';"
                + " const real=o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent));"
                + " const isClean=t=>!/\\d{4,}/.test(t) && !/\\b(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]{7,}\\b/.test(t);"
                + " for(let k=0;k<20 && ![...e.options].some(real);k++) await new Promise(r=>setTimeout(r,400));"
                + " const re=pat?new RegExp(pat):null;"
                + " const opts=[...e.options].filter(o=>real(o) && (!re||re.test(norm(o.textContent))) && (!clean||isClean(norm(o.textContent))));"
                + " if(!opts.length) return '';"
                + " const o=opts[((idx%opts.length)+opts.length)%opts.length];"
                + " e.selectedIndex=[...e.options].indexOf(o); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ window.angular.element(e).triggerHandler('change'); }catch(x){}"
                + " try{ window.jQuery(e).trigger('change'); }catch(x){}"
                + " await new Promise(r=>setTimeout(r,800));"
                + " return norm(o.textContent); }",
                java.util.Arrays.asList(NG + field, pattern, index, cleanOnly));
        return r == null ? "" : r.toString();
    }

    /** Read the three selects back: each must hold what was chosen (by its chosen option's text). */
    public boolean detailsEntered() {
        Object r = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"select[ng-model='" + NG + "\"+n+\"']\"); return e&&e.selectedIndex>0 ? norm(e.options[e.selectedIndex].textContent) : ''; };"
                + " return JSON.stringify({bag:g('extractionid'),bto:g('btoid'),reason:g('discardreasonid')}); }");
        String s = r == null ? "" : r.toString();
        boolean ok = !lastBag.isEmpty() && !lastBto.isEmpty() && !lastReason.isEmpty()
                && s.contains("\"bag\":\"" + lastBag + "\"") && s.contains("\"bto\":\"" + lastBto + "\"") && s.contains("\"reason\":\"" + lastReason + "\"");
        if (!ok) lastEntry += "  ||  READ BACK: " + s + (lastBag.isEmpty() ? "  ||  NO TEST-CREATED BAG IN THE LIST — the Extract Blood Bag No. list offers: " + lastBagOptions : "");
        return ok;
    }

    /** Installs a fresh toast collector. Must run BEFORE the click — toasts fade in about a second. */
    private void armToasts() {
        page.evaluate("() => {" + JS
                + " window.__bdToasts=[]; if(window.__bdObs) window.__bdObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__bdToasts.includes(t)) window.__bdToasts.push(t); }); };"
                + " window.__bdObs=new MutationObserver(grab); window.__bdObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /**
     * Click the form's <b>Add</b> and check the staged row in {@code #Bloodbagstock}: Blood Bag No. (shown as "{component}-{bag no}"),
     * Discard Reason and BTO must equal what was chosen, and the boxes must clear. A toast (if the screen raises one instead, e.g. a
     * missing mandatory field) is reported.
     */
    public boolean addToList() {
        armToasts();
        try { page.locator(ADD_ROW).first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { lastStaged = "the form's Add button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        waitForAngular(1500);
        Object r = page.evaluate("(a) => {" + JS
                + " const t=document.getElementById('Bloodbagstock');"
                + " const rows=t?[...t.querySelectorAll('tbody tr')].map(r=>[...r.querySelectorAll('td')].map(d=>norm(d.textContent))).filter(c=>c.some(x=>x)):[];"
                + " const mine=rows.find(c=>c[0]===a.bag && c[3]===a.reason && c[4]===a.bto);"
                + " const toast=(window.__bdToasts||[]).join(' || ');"
                + " const sel=document.querySelector(\"select[ng-model='" + NG + "extractionid']\"); const cleared=!!sel && sel.selectedIndex<=0;"
                + " if(!mine) return 'NOT STAGED: the table holds '+rows.length+' row(s)'+(rows.length?' ['+rows.map(c=>c.slice(0,5).join(\"/\")).join('; ')+']':'')+(toast?' - message: '+toast:'');"
                + " const names=['Blood Bag No.','Discard Date','Discard Time','Discard Reason','BTO','Attendant','Technician','Technician Remark','Return','Return To'];"
                + " return 'STAGED: '+names.map((n,i)=>n+'=\"'+(mine[i]||'')+'\"').join(' | ')+' | entry boxes cleared='+cleared; }",
                java.util.Map.of("bag", lastBag, "reason", lastReason, "bto", lastBto));
        lastStaged = r == null ? "" : r.toString();
        System.out.println("BloodBagDiscardReturn: add -> " + lastStaged);
        return lastStaged.startsWith("STAGED:");
    }

    /**
     * A success message, judged strictly: these screens phrase refusals with words like "Already Added!" — so a plain
     * {@code contains("added")} would read a rejection as a save.
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ") || t.contains("fail") || t.contains("error")
                || t.contains("please") || t.contains("select") || t.contains("enter")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated") || t.contains("discard");
    }

    /**
     * Click <b>Save</b> ({@code fnIUDBloodBagDiscard()}) and return the toast. Every API answer during the save is noted, so a refusal
     * without a toast is still explained, and when no message appears the screen's state (disabled Save, validation prompts, invalid
     * fields) is reported. Answers the "Do You Want To Save" dialog if one is raised.
     */
    public String saveAndGetToast() {
        final StringBuilder net = new StringBuilder();
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            String u = r.url().toLowerCase();
            if (r.request().method().equals("POST") && u.contains("/api/") && !u.contains("getinternalmsgcount")
                    && !u.contains("admindashboard") && !u.contains("commondropdown") && !u.contains("userrolemenu")) {
                String body = "";
                try { body = r.text(); } catch (Exception ignore) { }
                if (net.length() > 0) net.append(" ; ");
                net.append(r.url().substring(u.indexOf("/api/"))).append(" answered HTTP ").append(r.status())
                        .append(body.isBlank() ? "" : " " + body.substring(0, Math.min(150, body.length())));
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
            long deadline = System.currentTimeMillis() + 20000;
            String toast = "";
            while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
                try { acceptSaveDialog(); } catch (Exception ignore) { }
                try {
                    Object now = page.evaluate("() => (window.__bdToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                    toast = now == null ? "" : now.toString();
                } catch (Exception ignore) { }
                if (toast.isEmpty()) page.waitForTimeout(500);
            }
            // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
            toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").replaceFirst("^\\s*\\W*KPJ Portal\\s*", "").trim();
            String why = "";
            if (toast.isEmpty()) {
                try {
                    Object d = page.evaluate("() => {" + JS
                            + " const save=document.querySelector(\"" + SAVE + "\");"
                            + " const prompts=[...document.querySelectorAll('.formError,.formErrorContent,.alert,.jAlert,.modal.in,.sweet-alert,.bootbox')].filter(vis).map(x=>norm(x.textContent).slice(0,120));"
                            + " const bad=[...document.querySelectorAll('.ng-invalid')].filter(x=>vis(x)||x.tagName==='SELECT').map(x=>(x.getAttribute('ng-model')||x.id||x.tagName)).slice(0,8);"
                            + " return 'Save disabled='+(save?save.disabled:'(no button)')+'; prompts=['+prompts.join(' | ')+']; invalid fields=['+bad.join(', ')+']; url='+location.hash; }");
                    why = " | screen state: " + d;
                } catch (Exception ignore) { }
            }
            lastSaveDiagnostics = (toast.isEmpty() ? "Save clicked; no message within 20s" : "Save clicked")
                    + (net.length() > 0 ? " (" + net + ")" : " (no API call was seen after Save)") + why;
            lastToast = toast;
            System.out.println("BloodBagDiscardReturn: save -> toast=\"" + toast + "\" " + lastSaveDiagnostics);
            waitForAngular(1500);
            return toast;
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
    }

    /**
     * Look the saved discard up in the list. After a save the screen returns to the list by itself; otherwise the form's Back is used.
     * <b>Search</b> is pressed (From / To default to today, the Discarded Date), the grid's data is read, and a row counts only when its
     * {@code id} is HIGHER than the highest seen before the save AND its Extracted Blood Bag No. holds the bag number AND its Discard
     * Reason equals the one chosen. The grid is also filtered by the bag number so the screenshot shows the row.
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
            catch (Exception e) { System.out.println("BloodBagDiscardReturn.verifyInTable: Search click failed - " + e.getMessage().split("\n")[0]); }
            Object hit = page.evaluate(LIST_READ, java.util.Map.of("max", baselineMaxId, "bag", lastBagNo, "reason", lastReason));
            String h = hit == null ? "" : hit.toString();
            int rows = intOf(h, "rows"), fresh = intOf(h, "fresh"), mine = intOf(h, "mine");
            // leave the grid showing the row: filter the Extracted Blood Bag No. column
            page.evaluate("(c) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis);"
                    + " const f=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell'); return h && /extracted|blood\\s*bag\\s*no/i.test(norm(h.textContent)); })||boxes[0];"
                    + " if(f){ f.focus(); f.value=c; try{ const ct=angular.element(f).controller('ngModel'); if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastBagNo);
            waitForAngular(1500);
            String counts = "[list now holds " + rows + " discard(s) for today (before the save: " + baselineRows + "), " + fresh
                    + " with an id above " + baselineMaxId + "]";
            if (mine == 1) {
                lastListCheck = "FOUND: id " + intOf(h, "id") + " | Extracted Blood Bag No. \"" + strOf(h, "bag") + "\" | Discard Date " + strOf(h, "date")
                        + " | Discard Time " + strOf(h, "time") + " | Discard Reason \"" + strOf(h, "reason") + "\"  " + counts;
            } else if (mine > 1) {
                lastListCheck = "LISTED " + mine + " TIMES: " + mine + " new rows carry bag " + lastBagNo + " and reason \"" + lastReason
                        + "\" — one save must give one row  " + counts;
            } else if (fresh > 0) {
                lastListCheck = "a new discard appeared (" + strOf(h, "firstFresh") + ") but it does not match the entry (bag " + lastBagNo
                        + ", reason \"" + lastReason + "\")  " + counts;
            } else {
                lastListCheck = "no new discard in the list  " + counts;
            }
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("BloodBagDiscardReturn: list check -> " + lastListCheck);
        return lastListCheck.startsWith("FOUND:");
    }
}
