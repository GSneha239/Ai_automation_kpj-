package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Evaluation Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> → <b>Evaluation Master</b> (list, route {@code #/EvaluationMaster}) →
 * <b>Add</b> ({@code AddEvaluationMaster()}, route {@code #/add-EvaluationMaster}) → enter <b>Code</b> ({@code EvaluationMaster.Code},
 * max 10 characters) and <b>Evaluation Description</b> ({@code EvaluationMaster.EvaluationDesc}), choose the <b>Intervention Description</b>
 * from its search list → <b>Save</b> ({@code fnIUDEvaluationMaster()}) → toast → look the record up in the list grid
 * (Code | Evaluation Description | Intervention Desc | Status | Edit).</p>
 *
 * <p><b>Intervention Description</b> is a {@code uib-typeahead} ({@code InterventionMaster.selected}, minimum 2 characters) over
 * {@code interventionList}, and that list is filled by the box's own {@code ng-change="searchIntervention(...)"} server call. Setting
 * the value through the model therefore shows "No Results Found" — the popup evaluated the still-empty list before the answer
 * arrived. It works the way a user drives it: type two characters, wait for the list to load, type on so the next keystroke
 * re-filters the loaded list, then CLICK an entry. The box is only counted as filled when its model holds the chosen
 * <i>object</i> (typed text alone leaves a plain string and would save no intervention).</p>
 */
public class EvaluationMaster extends BasePage {

    public EvaluationMaster(Page page) { super(page); }

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastAdd = "", lastEntry = "", lastIntervention = "",
            lastInterventionDiag = "", lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastCode = "", lastDescription = "";

    private static final String CODE_MODEL = "EvaluationMaster.Code";
    private static final String DESC_MODEL = "EvaluationMaster.EvaluationDesc";
    private static final String INTERVENTION_MODEL = "InterventionMaster.selected";

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Nursing &rarr; Evaluation Master.
     *
     * <p>Each level is opened only if it is closed (a second click on an open entry CLOSES it) and the next one is looked up
     * INSIDE its parent's {@code li}: the sidebar is built lazily and "Nursing" is close to the top-level "Nursing Station". The
     * app bounces a typed hash back to the dashboard, so the menu link is the only way in.</p>
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*nursing\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const nur=[...topLi.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(nur&&nur.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*evaluation\\s*master\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__evMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__evMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("EvaluationMaster.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__evMenu'); if(e) e.removeAttribute('id'); }");
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
        String u = page.url().toLowerCase();
        if (!u.contains("evaluationmaster") || u.contains("add-")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a,input[type=button]')].some(x=>(x.offsetWidth||x.offsetHeight||x.getClientRects().length)"
                            + " && /^\\s*add\\s*$/i.test(((x.textContent||x.value)||'').replace(/\\s+/g,' ').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return page.url().toLowerCase().contains("evaluationmaster");
    }

    /** On the add form: its own Code box is showing. */
    public boolean onAddForm() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + CODE_MODEL + "')",
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
                + "   if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options/.test(ng+' '+click)) continue;"
                + "   if(/page(First|Previous|Next|Last)PageClick/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+' maxlength='+(e.getAttribute('maxlength')||'-')"
                + "     +' placeholder=\"'+(e.placeholder||'')+'\"'); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " if(hdr.length) out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== EvaluationMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click <b>Add</b> (polls) and wait for the add form's Code box. Lands on {@code #/add-EvaluationMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return false; b.id='__evAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { lastAdd = "the Add button was not found"; return false; }
        try { page.locator("#__evAdd").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("EvaluationMaster.clickAdd: click failed - " + e.getMessage().split("\n")[0]); }
        try { page.evaluate("() => { const e=document.getElementById('__evAdd'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
        boolean open = onAddForm();
        waitForAngular(800);
        lastAdd = open ? "Add clicked; the form opened at " + page.url() : "Add clicked but the form did not open (" + page.url() + ")";
        return open;
    }

    /**
     * Enter <b>Code</b> and <b>Evaluation Description</b> (each by its exact ng-model) and read both boxes back.
     *
     * <p>{@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different details. The Code
     * is stamped from the wall clock ({@code System.nanoTime()} remainders repeat on this machine) and kept inside the box's
     * 10-character limit; the description carries the run time too, since the shared QA environment keeps every record ever saved.</p>
     */
    public String fillDetails(int attempt) {
        lastCode = "EV" + String.format("%05d", (System.currentTimeMillis() + attempt * 7919L) % 100000);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        lastDescription = "Auto evaluation " + stamp + (attempt > 0 ? "-" + attempt : "");
        Object r = page.evaluate("(a) => {"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")]"
                + "   .find(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length);"
                + "   if(!e) return '(no-field)'; e.focus(); e.value=v;"
                + "   try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true}));"
                + "   return e.value; };"
                + " const c=set('" + CODE_MODEL + "',a.code); const d=set('" + DESC_MODEL + "',a.desc);"
                + " return 'Code='+c+' | EvaluationDescription='+d; }",
                java.util.Map.of("code", lastCode, "desc", lastDescription));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("EvaluationMaster: " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold their OWN value — the check that catches a description written over the code. */
    public boolean detailsEntered() {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + lastCode + " |")
                && lastEntry.endsWith("EvaluationDescription=" + lastDescription);
    }

    /**
     * Choose the <b>Intervention Description</b> from the search list.
     *
     * <ol>
     *   <li>Type a two-letter prefix — the search starts at two characters — and wait for {@code interventionList} to load.</li>
     *   <li>Take the entry at {@code attempt} (starting with entries that begin with the prefix) and keep typing so that the box
     *       holds a stretch of that entry's own text: the next keystroke re-filters the loaded list and the popup opens with it in.</li>
     *   <li>Click that entry in the popup.</li>
     *   <li>Read the model back: it must be the chosen <i>object</i>, not typed text.</li>
     * </ol>
     *
     * @return the chosen Intervention Description, or an empty string with {@link #lastInterventionDiag} saying what went wrong
     */
    public String selectIntervention(int attempt) {
        lastIntervention = "";
        // Interventions are the records of Nursing > Intervention Master; the search answers with the existing ones whatever two letters are
        // typed, and the client-side filter then keeps the entries that contain the typed text. "in" matches "intervention Test"; the rest are fallbacks.
        String[] prefixes = {"in", "te", "au", "ra", "ca"};
        StringBuilder diag = new StringBuilder();
        Locator box = page.locator("input[ng-model='" + INTERVENTION_MODEL + "']");
        for (int p = 0; p < prefixes.length; p++) {
            String prefix = prefixes[(attempt + p) % prefixes.length];
            try {
                box.click(new Locator.ClickOptions().setTimeout(5000));
                box.fill("");
                page.waitForTimeout(300);
                box.pressSequentially(prefix, new Locator.PressSequentiallyOptions().setDelay(150));
            } catch (Exception e) {
                diag.append("[").append(prefix).append("] could not type: ").append(e.getMessage().split("\n")[0]).append("; ");
                continue;
            }
            // The list arrives from the server after the keystrokes — wait for it, never for a fixed time alone.
            try {
                page.waitForFunction("() => { const e=document.querySelector(\"[ng-model='" + INTERVENTION_MODEL + "']\"); if(!e) return false;"
                        + " try{ const l=angular.element(e).scope().interventionList; return !!l && l.length>0; }catch(x){ return false; } }",
                        null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception e) { diag.append("[").append(prefix).append("] the list never loaded; "); continue; }
            page.waitForTimeout(500);

            // Which entry to take, and which stretch of it to type so the filter keeps it.
            Object plan = page.evaluate("(a) => {"
                    + " const e=document.querySelector(\"[ng-model='" + INTERVENTION_MODEL + "']\");"
                    + " const list=(angular.element(e).scope().interventionList||[]).map(x=>String(x.InterventionDesc||'').replace(/\\s+/g,' ').trim()).filter(t=>t);"
                    + " if(!list.length) return null;"
                    + " const starts=list.filter(t=>t.toLowerCase().startsWith(a.prefix));"
                    + " const pool=starts.length?starts:list;"
                    + " const pick=pool[a.attempt % pool.length];"
                    + " const at=Math.max(0,pick.toLowerCase().indexOf(a.prefix));"
                    + " return {pick:pick, typed:pick.substring(at, Math.min(pick.length, at+5)), total:list.length}; }",
                    java.util.Map.of("prefix", prefix, "attempt", attempt));
            if (plan == null) { diag.append("[").append(prefix).append("] the list was empty; "); continue; }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) plan;
            String pick = String.valueOf(m.get("pick")), typed = String.valueOf(m.get("typed"));
            try {
                box.fill("");
                page.waitForTimeout(200);
                box.pressSequentially(typed, new Locator.PressSequentiallyOptions().setDelay(150));
            } catch (Exception e) { diag.append("[").append(prefix).append("] could not type on: ").append(e.getMessage().split("\n")[0]).append("; "); continue; }

            // The popup's entries; the one whose text is the picked intervention is clicked.
            Object tagged = null;
            try {
                page.waitForFunction("() => [...document.querySelectorAll('ul.dropdown-menu li')].some(l=>(l.offsetWidth||l.offsetHeight||l.getClientRects().length))",
                        null, new Page.WaitForFunctionOptions().setTimeout(6000));
                tagged = page.evaluate("(want) => {" + JS
                        + " document.querySelectorAll('#__evIntervention').forEach(x=>x.removeAttribute('id'));"
                        + " const items=[...document.querySelectorAll('ul.dropdown-menu li')].filter(vis);"
                        + " const hit=items.find(l=>norm(l.textContent).toLowerCase()===want.toLowerCase())||items[0];"
                        + " if(!hit) return null; const a=hit.querySelector('a')||hit; a.id='__evIntervention';"
                        + " return {text:norm(hit.textContent), shown:items.length}; }", pick);
            } catch (Exception e) { diag.append("[").append(prefix).append("] the popup never opened for \"").append(typed).append("\"; "); continue; }
            if (tagged == null) { diag.append("[").append(prefix).append("] no entry to click; "); continue; }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> t = (java.util.Map<String, Object>) tagged;
            try { page.locator("#__evIntervention").click(new Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { diag.append("[").append(prefix).append("] the entry click failed: ").append(e.getMessage().split("\n")[0]).append("; "); continue; }
            waitForAngular(700);

            // Read it back: the box text AND the model (an object only when an entry was really chosen).
            Object back = page.evaluate("() => {"
                    + " const e=document.querySelector(\"[ng-model='" + INTERVENTION_MODEL + "']\");"
                    + " const mv=angular.element(e).controller('ngModel').$modelValue;"
                    + " return {text:(e.value||'').replace(/\\s+/g,' ').trim(), chosen:(mv && typeof mv==='object')? String(mv.InterventionDesc||'').replace(/\\s+/g,' ').trim() : ''}; }");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> b = (java.util.Map<String, Object>) back;
            String chosen = b == null ? "" : String.valueOf(b.get("chosen"));
            String text = b == null ? "" : String.valueOf(b.get("text"));
            if (!chosen.isEmpty()) {
                lastIntervention = chosen;
                lastInterventionDiag = "typed \"" + prefix + "\" -> the list loaded (" + m.get("total") + " entries) -> typed on to \"" + typed
                        + "\" -> the popup showed " + t.get("shown") + " entr" + (String.valueOf(t.get("shown")).equals("1") ? "y" : "ies")
                        + " -> clicked \"" + t.get("text") + "\" -> the box reads \"" + text + "\" and its model holds that entry";
                System.out.println("EvaluationMaster: intervention -> " + lastInterventionDiag);
                return lastIntervention;
            }
            diag.append("[").append(prefix).append("] clicked \"").append(t.get("text")).append("\" but the model holds no chosen entry (box reads \"").append(text).append("\"); ");
        }
        lastInterventionDiag = "no Intervention Description could be chosen from the list: " + diag;
        System.out.println("EvaluationMaster: " + lastInterventionDiag);
        return "";
    }

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

    /**
     * Click <b>Save</b> ({@code fnIUDEvaluationMaster}) and return the toast. Toasts are cleared first so a repeat still reads, and a
     * MutationObserver collects every toast that appears (they fade within a second or two). Answers the "Do You Want To Save"
     * dialog if the screen raises one.
     */
    public String saveAndGetToast() {
        Object tagged;
        try {
            tagged = page.evaluate("() => {" + JS
                    + " window.__evToasts=[]; if(window.__evObs) window.__evObs.disconnect();"
                    + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                    + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); if(t && !window.__evToasts.includes(t)) window.__evToasts.push(t); }); };"
                    + " window.__evObs=new MutationObserver(grab); window.__evObs.observe(document.body,{childList:true,subtree:true}); grab();"
                    + " const all=[...document.querySelectorAll('button,input[type=submit]')].filter(vis);"
                    + " const b=all.find(x=>/fnIUDEvaluationMaster/.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return false; b.id='__evSave'; return true; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Save lookup threw: " + e.getMessage().split("\n")[0];
            return "";
        }
        if (!Boolean.TRUE.equals(tagged)) {
            lastSaveDiagnostics = "no Save button is visible";
            lastToast = "";
            return "";
        }
        try { page.locator("#__evSave").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("EvaluationMaster: Save click failed - " + e.getMessage().split("\n")[0]); }
        try { page.evaluate("() => { const e=document.getElementById('__evSave'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }

        // A confirm may or may not appear — answer it whenever it is there, then wait for the toast.
        long deadline = System.currentTimeMillis() + 20000;
        String toast = "";
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try { acceptSaveDialog(); } catch (Exception ignore) { }
            try {
                Object now = page.evaluate("() => (window.__evToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                toast = now == null ? "" : now.toString();
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
        lastSaveDiagnostics = toast.isEmpty() ? "Save clicked; no message within 20s" : "Save clicked";
        lastToast = toast;
        System.out.println("EvaluationMaster: save -> toast=\"" + toast + "\"");
        waitForAngular(600);
        return toast;
    }

    /**
     * Look the saved record up in the list grid.
     *
     * <p>After a save the screen may stay on the (cleared) add form, so the form's own <b>Back</b> button is used to reach the list —
     * setting {@code location.hash} from the form raises the app's "Reload page" popup. The grid has one {@code colFilter.term} box
     * PER COLUMN, so the box inside the <b>Code</b> header is used. The body rows live in the main render container (the pinned left
     * container holds only empty cells), so a row is judged on its own cells: the Code, the Evaluation Description AND the Intervention
     * Description must all equal what was entered — a code alone could belong to a record that already existed.</p>
     */
    public boolean verifyInTable(String intervention) {
        waitForAngular(1200);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                Object tagged = page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent)||x.value||''));"
                        + " if(!b) return false; b.id='__evBack'; return true; }");
                if (Boolean.TRUE.equals(tagged)) {
                    try { page.locator("#__evBack").click(new Locator.ClickOptions().setTimeout(5000)); }
                    catch (Exception e) { System.out.println("EvaluationMaster.verifyInTable: Back click failed - " + e.getMessage().split("\n")[0]); }
                    page.evaluate("() => { const e=document.getElementById('__evBack'); if(e) e.removeAttribute('id'); }");
                    waitForAngular(2000);
                }
            }
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
                    + " return 'rowsBeforeFilter='+rows+', filtered='+(inCode?'the Code column':(f?'the first column':'NOTHING - no filter box, rows read as listed')); }",
                    lastCode);
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                    + "   .map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent)))"
                    + "   .filter(cells=>cells.some(t=>t));"
                    + " const pager=norm((document.querySelector('.ui-grid-pager-count')||{}).textContent);"
                    + " const withCode=rows.filter(c=>c[0] && c[0].toUpperCase()===a.code.toUpperCase());"
                    + " const mine=withCode.find(c=>c[1]===a.desc && c[2]===a.diag);"
                    + " if(mine) return 'FOUND: Code=\"'+mine[0]+'\" | Evaluation Description=\"'+mine[1]+'\" | Intervention Desc=\"'+mine[2]+'\" | Status=\"'+(mine[3]||'')+'\" ('+pager+')';"
                    + " if(withCode.length) return 'the code is listed but the row reads Evaluation Description=\"'+withCode[0][1]+'\", Intervention Desc=\"'+withCode[0][2]+'\" ('+pager+')';"
                    + " return 'not in the list ('+rows.length+' rows shown, '+pager+')'; }",
                    java.util.Map.of("code", lastCode, "desc", lastDescription, "diag", intervention == null ? "" : intervention));
            lastListCheck = "looked for Code \"" + lastCode + "\" + Evaluation Description \"" + lastDescription + "\" + Intervention Description \""
                    + intervention + "\" with " + (where == null ? "" : where) + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("EvaluationMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
