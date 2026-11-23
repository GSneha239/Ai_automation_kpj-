package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Debit Note Code Paymode</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> → <b>Debit Note Code Paymode</b> (route
 * {@code #/DebitNoteCodePaymode}) → enter <b>Code</b> and <b>Description</b> → <b>Submit</b> → toast → look the record up in
 * the grid under the form.</p>
 *
 * <p>Layout (discovered live): a common-master screen. The entry form is ALWAYS open — there is no Add button — and carries
 * {@code commonmaster.Code} (max 15 characters) and {@code commonmaster.Description}, then <b>Submit</b>
 * ({@code <button type=submit>} of {@code fnIUDCommonMaster()}) and <b>Clear</b>. The grid below lists Code | Description |
 * Status | Edit with one {@code colFilter.term} box per column. The master chooser select at the top
 * ({@code selectedform.value}) is disabled and fixed to this master; it is never touched.</p>
 */
public class DebitNoteCodePaymode extends BasePage {

    public DebitNoteCodePaymode(Page page) { super(page); }

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastCode = "", lastDescription = "";

    private static final String CODE_MODEL = "commonmaster.Code";
    private static final String DESC_MODEL = "commonmaster.Description";

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Patient &rarr; Debit Note Code Paymode.
     *
     * <p>Each level is opened only if it is closed (a second click on an open entry CLOSES it) and the next one is looked up
     * INSIDE its parent's {@code li}: the sidebar is built lazily and has same-named entries ("Patient" and "Billing" also exist
     * as top-level modules). The app bounces a typed hash back to the dashboard, so the menu link is the only way in.</p>
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*patient\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const pat=[...topLi.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(pat&&pat.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*debit\\s*note\\s*code\\s*paymode\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__dncpMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__dncpMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DebitNoteCodePaymode.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__dncpMenu'); if(e) e.removeAttribute('id'); }");
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
        if (!page.url().toLowerCase().contains("debitnotecodepaymode")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + CODE_MODEL + "')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return page.url().toLowerCase().contains("debitnotecodepaymode");
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
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+' maxlength='+(e.getAttribute('maxlength')||'-')); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== DebitNoteCodePaymode CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- actions ---------------------------------------------------------

    /**
     * Enter <b>Code</b> and <b>Description</b> (each by its exact ng-model) and read both boxes back.
     *
     * <p>{@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different details.
     * The Code is stamped from the wall clock ({@code System.nanoTime()} remainders end in "00" on this machine and repeat),
     * and the Description carries the run time as well: the shared QA environment keeps every record ever saved.</p>
     *
     * @return {@code Code=<read back> | Description=<read back>}
     */
    public String fillDetails(int attempt) {
        lastCode = "DNCP" + String.format("%05d", (System.currentTimeMillis() + attempt * 7919L) % 100000);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        lastDescription = "Auto debit note paymode " + stamp + (attempt > 0 ? "-" + attempt : "");
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
                + " return 'Code='+c+' | Description='+d; }",
                java.util.Map.of("code", lastCode, "desc", lastDescription));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("DebitNoteCodePaymode: " + lastEntry);
        return lastEntry;
    }

    /** Both boxes hold their OWN value — the check that catches a description written over the code. */
    public boolean detailsEntered() {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + lastCode + " |")
                && lastEntry.endsWith("Description=" + lastDescription);
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
     * Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads, and a MutationObserver collects
     * every toast that appears (they fade within a second or two). Answers the "Do You Want To Save" dialog if the screen raises one.
     */
    public String submitAndGetToast() {
        Object tagged;
        try {
            tagged = page.evaluate("() => {" + JS
                    + " window.__dncpToasts=[]; if(window.__dncpObs) window.__dncpObs.disconnect();"
                    + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                    + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); if(t && !window.__dncpToasts.includes(t)) window.__dncpToasts.push(t); }); };"
                    + " window.__dncpObs=new MutationObserver(grab); window.__dncpObs.observe(document.body,{childList:true,subtree:true}); grab();"
                    + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis)"
                    + "   .find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return false; b.id='__dncpSubmit'; return true; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit lookup threw: " + e.getMessage().split("\n")[0];
            return "";
        }
        if (!Boolean.TRUE.equals(tagged)) {
            lastSaveDiagnostics = "no Submit button is visible";
            lastToast = "";
            return "";
        }
        try { page.locator("#__dncpSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("DebitNoteCodePaymode: Submit click failed - " + e.getMessage().split("\n")[0]); }
        try { page.evaluate("() => { const e=document.getElementById('__dncpSubmit'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }

        // A confirm may or may not appear — answer it whenever it is there, then wait for the toast.
        long deadline = System.currentTimeMillis() + 20000;
        String toast = "";
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try { acceptSaveDialog(); } catch (Exception ignore) { }
            try {
                Object now = page.evaluate("() => (window.__dncpToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                toast = now == null ? "" : now.toString();
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
        lastSaveDiagnostics = toast.isEmpty() ? "Submit clicked; no message within 20s" : "Submit clicked";
        lastToast = toast;
        System.out.println("DebitNoteCodePaymode: submit -> toast=\"" + toast + "\"");
        waitForAngular(600);
        return toast;
    }

    /**
     * Look the saved record up in the grid.
     *
     * <p>The grid has one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so the box inside the <b>Code</b> column
     * header is used (the first box would filter whichever column comes first). The body rows live in the main render container
     * — the pinned left container holds only the empty checkbox cells — so a row is judged on its own Code and Description cells,
     * and BOTH must equal what was entered: a code alone could belong to a record that already existed.</p>
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
                    + " const mine=withCode.find(c=>c[1]===a.desc);"
                    + " if(mine) return 'FOUND: Code=\"'+mine[0]+'\" | Description=\"'+mine[1]+'\" | Status=\"'+(mine[2]||'')+'\" ('+pager+')';"
                    + " if(withCode.length) return 'the code is listed but with a different description: \"'+withCode[0][1]+'\" ('+pager+')';"
                    + " return 'not in the list ('+rows.length+' rows shown, '+pager+')'; }",
                    java.util.Map.of("code", lastCode, "desc", lastDescription));
            lastListCheck = "looked for Code \"" + lastCode + "\" + Description \"" + lastDescription + "\" with "
                    + (where == null ? "" : where) + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("DebitNoteCodePaymode: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
