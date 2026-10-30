package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Quality Indicator</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>General</b> → <b>Quality Indicator</b> (route {@code #/QualityIndicator}) →
 * pick the <b>Form Name</b> → enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → toast → look the record up in the grid.</p>
 *
 * <p>Layout (discovered live): a generic common-master screen. The <b>Form Name</b> select
 * ({@code ng-options="item.value as item.text for item in drpFormNamesVals"}, model {@code selectedform.value}) chooses WHICH
 * common master the record is saved into — there is no "Quality Indicator" entry in it, the 180 options are the other masters.
 * Some VALUES appear twice ("IndentFrequencyMaster", "Severity"…) and cannot be told apart by the model, so only an option whose
 * value is unique is ever picked (labels may repeat: "Route" is both {@code Route} and {@code RouteMaster}). The entry form is always open (no Add button): {@code commonmaster.Code} (max 15), {@code commonmaster.Description}
 * (labelled <b>Remark</b>), optional MIMS GUID / MIMS Description / MIMS Type, then <b>Submit</b> and <b>Clear</b>. The grid below
 * lists Code | Remark | Status | Edit with one {@code colFilter.term} box per column.</p>
 */
public class QualityIndicator extends BasePage {

    public QualityIndicator(Page page) {
        this(page, "quality\\s*indicator", "qualityindicator", "QI", "Auto quality indicator ", "QualityIndicator");
    }

    /**
     * The same generic common-master screen is reached through several menu entries (Quality Indicator, Drug Admin Route …), so the
     * entry that is clicked, the route that proves it opened and the stamps written into the form are parameters.
     *
     * @param leafRegex     the menu entry's text (regex, matched whole, case-insensitive) inside Application Configuration &gt; General
     * @param routeFragment lower-case part of the URL that proves the screen opened
     * @param codePrefix    start of the generated Code (max 15 characters in all: 2 + 12 stamp digits leaves room)
     * @param remarkPrefix  start of the generated Remark
     * @param tag           the name used in console lines
     */
    protected QualityIndicator(Page page, String leafRegex, String routeFragment, String codePrefix, String remarkPrefix, String tag) {
        super(page);
        this.leafRegex = leafRegex;
        this.routeFragment = routeFragment;
        this.codePrefix = codePrefix;
        this.remarkPrefix = remarkPrefix;
        this.tag = tag;
    }

    private final String leafRegex, routeFragment, codePrefix, remarkPrefix, tag;

    /** Diagnostics dumped into the report when a step fails. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastEntry = "", lastToast = "", lastSaveDiagnostics = "",
            lastListCheck = "", lastCode = "", lastRemark = "", lastFormName = "", lastFormValue = "", lastFormSelection = "",
            lastApiCalls = "";

    private static final String CODE_MODEL = "commonmaster.Code";
    private static final String REMARK_MODEL = "commonmaster.Description";

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    /** The Form Name select, found by its ng-options source (the model sits in a {@code data-ng-model} attribute). */
    private static final String FORM_SELECT =
            "[...document.querySelectorAll('select')].filter(vis).find(x=>/drpFormNamesVals/.test(x.getAttribute('ng-options')||''))";

    private final java.util.List<String> apiLog = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean listening = false;

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; General &rarr; Quality Indicator.
     *
     * <p>Each level is opened only if it is closed and the next one is looked up INSIDE its parent's {@code li}: the sidebar is
     * built lazily and has same-named entries ("Quality Indicator" is also a Nursing Station screen, route
     * {@code #/QualityIndication}). The app bounces a typed hash back to the dashboard, so the menu link is the only way in.</p>
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*general\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("(re) => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const gen=[...topLi.querySelectorAll('a')].find(x=>/^\\s*general\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(gen&&gen.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>new RegExp('^\\\\s*'+re+'\\\\s*$','i').test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__qiMenu'; return a.getAttribute('href')||'link'; }", leafRegex);
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__qiMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println(tag + ".nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__qiMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** Every visible menu entry — used to pin the real label when the menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On the screen: the route AND the screen's own Code box must be there (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(routeFragment)) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + CODE_MODEL + "')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return page.url().toLowerCase().contains(routeFragment);
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||e.getAttribute('data-ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options/.test(ng+' '+click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+' maxlength='+(e.getAttribute('maxlength')||'-')); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== " + tag + " CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- step 1: Form Name -----------------------------------------------

    /**
     * Pick the <b>Form Name</b>: the first option whose label OR value (without its {@code string:} prefix) matches {@code labelRegex}
     * AND whose value is unique in the list (a duplicated value cannot be told apart by the model, so it would read back as its
     * twin). Falls back to the first unique option when nothing matches. The select is a plain native one, so it is set through Playwright's own
     * {@code selectOption}, which fires the events the screen's {@code ng-change} listens to.
     *
     * @return {@code Form Name=<label read back> (<value>)} or a {@code (no-…)} marker
     */
    public String selectFormName(String labelRegex) {
        Object found;
        try {
            found = page.evaluate("(re) => {" + JS
                    + " const s=" + FORM_SELECT + ";"
                    + " if(!s) return '(no-select)';"
                    + " s.id='__qiForm';"
                    + " const opts=[...s.options].filter(o=>o.value);"
                    + " const uniq=o=>opts.filter(x=>x.value===o.value).length===1;"
                    + " const rx=new RegExp(re,'i');"
                    + " const dupes=new Set(opts.filter(o=>!uniq(o)).map(o=>o.value));"
                    + " const pick=opts.find(o=>(rx.test(norm(o.textContent)) || rx.test(o.value.replace(/^string:/,''))) && uniq(o)) || opts.find(uniq);"
                    + " if(!pick) return '(no-option)';"
                    + " return norm(pick.textContent)+'\\u0001'+pick.value+'\\u0001'+opts.length+'\\u0001'+dupes.size; }", labelRegex);
        } catch (Exception e) {
            lastFormSelection = "(no-select) " + e.getMessage().split("\n")[0];
            return lastFormSelection;
        }
        String f = found == null ? "" : found.toString();
        if (f.startsWith("(")) { lastFormSelection = f; return f; }
        String[] p = f.split("\u0001");
        try {
            page.locator("#__qiForm").selectOption(new com.microsoft.playwright.options.SelectOption().setValue(p[1]),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            lastFormSelection = "(no-select) selectOption failed - " + e.getMessage().split("\n")[0];
            return lastFormSelection;
        }
        waitForAngular(1500);
        lastFormSelection = readFormName() + "  [the list has " + p[2] + " forms, " + p[3] + " of them have a value shared with another entry and are skipped]";
        System.out.println(tag + ": " + lastFormSelection);
        return lastFormSelection;
    }

    /** What the Form Name select shows now, and what the screen's model holds. Sets {@link #lastFormName} / {@link #lastFormValue}. */
    public String readFormName() {
        Object r = page.evaluate("() => {" + JS
                + " const s=" + FORM_SELECT + "; if(!s) return '';"
                + " const o=s.options[s.selectedIndex]; let m='';"
                + " try{ const sc=angular.element(s).scope(); m=(sc&&sc.selectedform&&sc.selectedform.value)||''; }catch(e){}"
                + " return (o?norm(o.textContent):'')+'\\u0001'+(o?o.value:'')+'\\u0001'+m; }");
        String[] p = (r == null ? "" : r.toString()).split("\u0001", -1);
        lastFormName = p.length > 0 ? p[0] : "";
        lastFormValue = p.length > 1 ? p[1] : "";
        String model = p.length > 2 ? p[2] : "";
        return "Form Name=" + lastFormName + " (" + lastFormValue + "; model " + (model.isEmpty() ? "empty" : model) + ")";
    }

    /** A real option is showing AND the model agrees with it (ng-options values read {@code string:<id>}, the model holds the bare id). */
    public boolean formNameSelected() {
        String r = readFormName();
        return !lastFormName.isBlank() && !lastFormName.startsWith("--") && !lastFormValue.isBlank()
                && r.contains("model " + lastFormValue.replaceFirst("^string:", ""));
    }

    // ---- step 2: Code + Remark -------------------------------------------

    /**
     * Enter <b>Code</b> and <b>Remark</b> (each by its exact ng-model) and read both boxes back.
     *
     * <p>{@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different details.
     * The Code (max 15) is stamped from the wall clock — {@code System.nanoTime()} remainders end in "00" on this machine and
     * repeat — and the Remark carries the run time too: the shared QA environment keeps every record ever saved.</p>
     *
     * @return {@code Code=<read back> | Remark=<read back>}
     */
    public String fillDetails(int attempt) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now().plusSeconds(attempt);
        lastCode = codePrefix + now.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        lastRemark = remarkPrefix + now.format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        Object r = page.evaluate("(a) => {"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")]"
                + "   .find(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length);"
                + "   if(!e) return '(no-field)'; e.focus(); e.value=v;"
                + "   try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true}));"
                + "   return e.value; };"
                + " const c=set('" + CODE_MODEL + "',a.code); const d=set('" + REMARK_MODEL + "',a.remark);"
                + " return 'Code='+c+' | Remark='+d; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println(tag + ": " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold their OWN value — the check that catches a remark written over the code. */
    public boolean detailsEntered() {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + lastCode + " |")
                && lastEntry.endsWith("Remark=" + lastRemark);
    }

    // ---- step 3: submit --------------------------------------------------

    /**
     * A success message, judged strictly: these configuration screens phrase refusals with the word "Added" — e.g. "… Is Already
     * Added!" — so a plain {@code contains("added")} reads a rejection as a save.
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** "… already exists / already added" — the cue to retry with different details. */
    public static boolean isDuplicate(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /** Remember every non-GET API answer, so a refused or silent save can be explained from the report. */
    private void listenForApi() {
        if (listening) return;
        listening = true;
        page.onResponse(r -> {
            try {
                String u = r.url();
                if (!u.contains("/api/") || "GET".equals(r.request().method()) || u.contains("Notification") || u.contains("getInternalmsg")) return;
                String body = "", sent = "";
                try { body = r.text().replaceAll("\\s+", " "); } catch (Exception ignore) { }
                try { String pd = r.request().postData(); sent = pd == null ? "" : pd.replaceAll("\\s+", " "); } catch (Exception ignore) { }
                apiLog.add(r.request().method() + " " + u.substring(u.indexOf("/api/"))
                        + (sent.isEmpty() ? "" : " sent " + sent.substring(0, Math.min(230, sent.length())))
                        + " -> " + r.status() + (body.isEmpty() ? "" : " " + body.substring(0, Math.min(160, body.length()))));
            } catch (Exception ignore) { }
        });
    }

    /**
     * Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads, and a MutationObserver collects
     * every toast that appears (they fade within a second or two). Answers the "Do You Want To Save" dialog if the screen raises one.
     */
    public String submitAndGetToast() {
        listenForApi();
        apiLog.clear();
        Object tagged;
        try {
            tagged = page.evaluate("() => {" + JS
                    + " window.__qiToasts=[]; if(window.__qiObs) window.__qiObs.disconnect();"
                    + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                    + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); if(t && !window.__qiToasts.includes(t)) window.__qiToasts.push(t); }); };"
                    + " window.__qiObs=new MutationObserver(grab); window.__qiObs.observe(document.body,{childList:true,subtree:true}); grab();"
                    + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis)"
                    + "   .find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return false; b.id='__qiSubmit'; return true; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit lookup threw: " + e.getMessage().split("\n")[0];
            return "";
        }
        if (!Boolean.TRUE.equals(tagged)) {
            lastSaveDiagnostics = "no Submit button is visible";
            lastToast = "";
            return "";
        }
        try { page.locator("#__qiSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println(tag + ": Submit click failed - " + e.getMessage().split("\n")[0]); }
        try { page.evaluate("() => { const e=document.getElementById('__qiSubmit'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }

        // A confirm may or may not appear — answer it whenever it is there, then wait for the toast.
        long deadline = System.currentTimeMillis() + 20000;
        String toast = "";
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try { acceptSaveDialog(); } catch (Exception ignore) { }
            try {
                Object now = page.evaluate("() => (window.__qiToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                toast = now == null ? "" : now.toString();
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
        lastApiCalls = String.join(" || ", apiLog);
        lastSaveDiagnostics = (toast.isEmpty() ? "Submit clicked; no message within 20s" : "Submit clicked")
                + (lastApiCalls.isEmpty() ? "; no write call to the server was seen" : "; server calls: " + lastApiCalls);
        lastToast = toast;
        System.out.println(tag + ": submit -> toast=\"" + toast + "\" | " + lastSaveDiagnostics);
        waitForAngular(600);
        return toast;
    }

    // ---- step 4: the table -----------------------------------------------

    /**
     * Look the saved record up in the grid.
     *
     * <p>The grid has one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so the box inside the <b>Code</b> column
     * header is used (the first box would filter whichever column comes first). The body rows live in the main render container
     * — the pinned left container holds only the empty checkbox cells — so a row is judged on its own Code and Remark cells, and
     * BOTH must equal what was entered: a code alone could belong to a record that already existed.</p>
     */
    public boolean verifyInTable() {
        waitForAngular(1200);
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')].some(r=>/\\S/.test(r.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis).filter(r=>/\\S/.test(r.textContent||'')).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis);"
                    + " const inCode=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell'); return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCode||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel'); if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='+(inCode?'the Code column':(f?'the first column':'NOTHING - no filter box')); }",
                    lastCode);
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                    + "   .map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent)))"
                    + "   .filter(cells=>cells.some(t=>t));"
                    + " const pager=norm((document.querySelector('.ui-grid-pager-count')||{}).textContent);"
                    + " const withCode=rows.filter(c=>c[0] && c[0].toUpperCase()===a.code.toUpperCase());"
                    + " const mine=withCode.find(c=>c[1]===a.remark);"
                    + " if(mine) return 'FOUND: Code=\"'+mine[0]+'\" | Remark=\"'+mine[1]+'\" | Status=\"'+(mine[2]||'')+'\" ('+pager+')';"
                    + " if(withCode.length) return 'the code is listed but with a different remark: \"'+withCode[0][1]+'\" ('+pager+')';"
                    + " return 'not in the list ('+rows.length+' rows shown, '+pager+')'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            lastListCheck = "looked for Code \"" + lastCode + "\" + Remark \"" + lastRemark + "\" with "
                    + (where == null ? "" : where) + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        if (!lastListCheck.contains("FOUND:")) lastListCheck += "  ||  " + explainEmptyGrid();
        System.out.println(tag + ": list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }

    /**
     * Why the grid shows nothing, from the screen's own scope: {@code GetGridData} asks the rights service for
     * {@code selectedform.CommonMasterPkID}, which this generic screen never sets, so the answer is empty and {@code Access} becomes
     * all-false — the rows are then never fetched. The same call the grid would make ({@code commonMasterFactory.fetchCommonMaster},
     * {@code ExecFlag=GRID}) is made here directly, to say whether the record IS on the server.
     */
    private String explainEmptyGrid() {
        try {
            Object r = page.evaluate("(a) => new Promise(resolve => { try {" + JS
                    + " const s=" + FORM_SELECT + "; const sc=angular.element(s).scope();"
                    + " const acc=JSON.stringify(sc.Access||null), pk=(sc.selectedform||{}).CommonMasterPkID,"
                    + "   data=(sc.gridOptions&&sc.gridOptions.data)?sc.gridOptions.data.length:'n/a';"
                    + " const head='grid data rows='+data+'; scope.Access='+acc+'; selectedform.CommonMasterPkID='+pk+'. ';"
                    + " const f=angular.element(document.body).injector().get('commonMasterFactory');"
                    + " f.fetchCommonMaster({ExecFlag:'GRID', MasterCodeFor:a.form}).then(res=>{"
                    + "   const rows=res.data||[]; const code=a.code.toUpperCase();"
                    + "   const hit=rows.find(x=>JSON.stringify(x).toUpperCase().includes(code));"
                    + "   resolve(head+(hit?'The server DOES hold the record (same call the grid makes, '+rows.length+' rows for the form): '+JSON.stringify(hit).slice(0,220)"
                    + "     :'The server answered '+rows.length+' rows for the form and none carries the code.')); },"
                    + "   e=>resolve(head+'Fetching the rows directly failed (HTTP '+(e&&e.status)+').'));"
                    + " } catch(e){ resolve('(could not inspect the screen: '+e.message+')'); } })",
                    java.util.Map.of("form", lastFormValue.replaceFirst("^string:", ""), "code", lastCode));
            return r == null ? "" : r.toString();
        } catch (Exception e) {
            return "(could not inspect the screen: " + e.getMessage().split("\n")[0] + ")";
        }
    }
}
