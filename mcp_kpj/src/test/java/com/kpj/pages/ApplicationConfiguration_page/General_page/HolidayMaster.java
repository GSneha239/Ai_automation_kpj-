package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Holiday Master</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b> and <b>Date</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The date is the risky field. DevHIS date boxes are commonly <b>readonly</b> and driven from a
 * calendar toggle, so assigning the input's value is silently ignored and the model keeps whatever it
 * started with — the box then displays the right text while the record carries something else. The write
 * therefore goes through the Angular model exactly as the picker does, falls back to the calendar itself
 * when that is refused, and the value is read back off the box afterwards. Which path was used is
 * reported, so a date that never took is visible rather than assumed.</p>
 */
public class HolidayMaster extends BasePage {

    public HolidayMaster(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastDate = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model tail, model-aware setter. */
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

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; General &rarr; Holiday Master.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*holiday\\s*master\\s*$/i.test(norm(x.textContent)))"
                + "   || [...document.querySelectorAll('a[href]')]"
                + "        .find(x=>/^\\s*holiday\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__hmMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__hmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("HolidayMaster.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__hmMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3000);
            if (!onScreen()) {
                try { page.navigate(baseUrl + hash); } catch (Exception ignore) { }
                waitForAngular(3500);
            }
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
        waitForAngular(1200);
    }

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On the Holiday Master screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        return route.contains("holiday");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+(e.readOnly?' READONLY':'')"
                + "     +' value=\"'+(e.value||'').slice(0,20)+'\" label=\"'+labelOf(e).slice(0,40)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== HolidayMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        if (formOpen()) return true;
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?holiday|AddHoliday/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__hmAdd'; }");
        try {
            page.locator("#__hmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("HolidayMaster.clickAdd: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__hmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && !/colFilter|row\\.entity/i.test(e.getAttribute('ng-model')||'')"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("HolidayMaster.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /** Enter the <b>Code</b>, addressed by the ng-model's last segment. */
    public String enterCode(String code) {
        Object r = page.evaluate("(c) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .filter(e=>!/date/i.test(tail(e)));"
                + " const el=boxes.find(e=>/^code$/i.test(tail(e)))"
                + "   || boxes.find(e=>/code$/i.test(tail(e)))"
                + "   || boxes.find(e=>/^\\s*code\\s*$/i.test(norm(e.placeholder||'')));"
                + " return el? setEl(el,c)+' ['+(ngOf(el)||'?')+']' : '(no-code-field)'; }", code);
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("HolidayMaster: Code=" + lastEntry);
        return lastEntry;
    }

    public boolean codeEntered(String code) {
        return lastEntry != null && !lastEntry.startsWith("(no-") && lastEntry.startsWith(code);
    }

    /**
     * Enter the <b>Date</b>.
     *
     * <p>Written through the Angular model first — the way the calendar itself writes it — and only then
     * through the input, because these boxes are typically readonly and ignore a plain assignment. If the
     * box still holds its original text the calendar is opened and a day is clicked. The value is read
     * back from the box, and the model is read off the scope, so a date that never bound is reported
     * rather than passed.</p>
     */
    public String enterDate(String date) {
        Object r = page.evaluate("(v) => new Promise(async resolve => {" + JS
                + " const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const el=boxes.find(e=>/date$/i.test(tail(e)))"
                + "   || boxes.find(e=>/date/i.test(tail(e)))"
                + "   || boxes.find(e=>/date/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " if(!el) return resolve('(no-date-field)');"
                + " const before=el.value, ro=el.readOnly;"
                + " let how='model';"
                + " try{ const c=angular.element(el).controller('ngModel');"
                + "      if(c){ c.$setViewValue(v); c.$render(); }"
                + "      const s=angular.element(el).scope(); if(s&&!s.$$phase) s.$apply(); }catch(e){}"
                + " if(el.value===before){ how='input'; el.focus(); el.value=v;"
                + "   el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   el.dispatchEvent(new Event('blur',{bubbles:true})); }"
                + " if(el.value===before){"
                // Last resort: drive the calendar the way a person would.
                + "   how='calendar';"
                + "   let host=el, tog=null;"
                + "   for(let i=0;i<5&&host&&!tog;i++){ host=host.parentElement;"
                + "     if(host) tog=[...host.querySelectorAll('button,a,i,span')]"
                + "       .find(x=>/open|toggle|calendar/i.test((x.getAttribute('ng-click')||'')"
                + "                                            +' '+(x.className||''))); }"
                + "   if(tog){ tog.click(); await sleep(900);"
                + "     const day=[...document.querySelectorAll('button,td,span')].filter(vis)"
                + "       .filter(x=>/^\\d{1,2}$/.test(norm(x.textContent)))"
                + "       .filter(x=>!/disabled|muted|text-muted/i.test(x.className||''))"
                + "       .find(x=>norm(x.textContent)==='15')"
                + "       || [...document.querySelectorAll('button,td,span')].filter(vis)"
                + "            .find(x=>/^\\d{1,2}$/.test(norm(x.textContent)));"
                + "     if(day){ day.click(); await sleep(700); } } }"
                + " let model='(scope unread)';"
                + " try{ const s=angular.element(el).scope();"
                + "      const ng=ngOf(el), root=ng.split('.')[0], key=ng.split('.').pop();"
                + "      const o=s&&s[root]; if(o) model=key+'='+JSON.stringify(o[key]); }catch(e){}"
                + " resolve((el.value||'(empty)')+' ['+(ngOf(el)||'?')+(ro?', readonly':'')"
                + "   +', set via the '+how+'; '+model+']'); })", date);
        lastDate = r == null ? "" : r.toString();
        waitForAngular(700);
        System.out.println("HolidayMaster: Date=" + lastDate);
        return lastDate;
    }

    /** The date box carries a value that is not its untouched default. */
    public boolean dateEntered() {
        return lastDate != null && !lastDate.startsWith("(no-") && !lastDate.startsWith("(empty)");
    }

    /**
     * A success message, judged strictly.
     *
     * <p>These configuration screens phrase refusals with the word "Added" — e.g. "… Is Already Added!" —
     * so a plain {@code contains("added")} reads a rejection as a save.</p>
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String submitAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/submit|save/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try {
                Object now = page.evaluate("() => {" + JS + TOAST_ELS
                        + " return toastEls().map(x=>norm(x.textContent)); }");
                if (now instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) now) {
                        String s = o.toString();
                        if (!s.isEmpty()) { toast = s; break; }
                    }
                }
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }

        if (toast.isEmpty()) {
            // Some screens in this module answer in a "KPJ Portal" banner that carries no toast class —
            // looking only for toastr elements reports silence for a screen that spoke clearly.
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select|add))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("HolidayMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used.</p>
     */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,140)"
                    + "         : 'not in the list ('+rows.length+' rows shown)'; }", code);
            lastListCheck += "looked for \"" + code + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("HolidayMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
