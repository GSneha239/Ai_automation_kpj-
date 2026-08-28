package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Shift</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b>, <b>Remark</b>, <b>From Time</b> and <b>To Time</b> →
 * select the <b>Cell Colour</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Fields are matched on the <b>last segment</b> of the ng-model, never on a keyword that also appears
 * in the prefix — every model on these screens is prefixed with the screen's own name, and a loose match
 * has previously written the name over the code.</p>
 *
 * <p>The two time boxes are the risk here. On the OT Assessment sheet eight time boxes shared one
 * {@code ng-model="inputTime"}, so setting the visible text left the record's own time unset and Save
 * answered "Please Add From Time!". Each box is therefore set through its own control, read back, and the
 * two are reported separately so From cannot silently take To's value.</p>
 */
public class Shift extends BasePage {

    public Shift(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastTimes = "",
            lastColour = "", lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

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
     * Application Configuration &rarr; General &rarr; Shift.
     *
     * <p>The route is read off the menu link rather than guessed — labels in this module do not predict
     * URLs (the sibling "Complaint Type" is routed {@code #/ComplaintListType}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*shift\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__shMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__shMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Shift.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__shMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        // Fall back to the route the MENU gave, never to a guessed one: this screen is served from
        // #/AccountingShift, so a hand-written "#/ShiftMaster" simply drops the run on the dashboard.
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

    /**
     * On the Shift screen.
     *
     * <p>The General menu routes this screen to {@code #/AccountingShift}, so the check cannot demand
     * that "shift" begin the route segment — that rejected the very page the menu had just opened. It is
     * still confined to the hash route and excludes the dashboard, so a failed navigation cannot pass.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        return route.contains("shift");
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
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,40)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== Shift CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?shift|AddShift/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__shAdd'; }");
        try {
            page.locator("#__shAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("Shift.clickAdd: " + e.getMessage().split("\n")[0]);
            try { page.evaluate("() => { window.location.hash = '#/add-Shift'; }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const e=document.getElementById('__shAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("Shift.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/^code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /** Enter <b>Code</b> and <b>Remark</b>, told apart by the ng-model's last segment. */
    public String enterDetails(String code, String remark) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, rm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio'"
                + "            && e.type!=='color')"
                + "   .filter(e=>!/search|colFilter/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^code$/i) || boxes.find(e=>/^\\s*code\\s*$/i.test(norm(e.placeholder||'')));"
                + " const remEl=boxes.filter(e=>e!==codeEl)"
                + "   .find(e=>/^(remark|remarks|description|name)$/i.test(tail(e)))"
                + "   || boxes.filter(e=>e!==codeEl)"
                + "        .find(e=>/remark|description/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-code-field)';"
                + " const rr = remEl? setEl(remEl,rm)+' ['+(ngOf(remEl)||'?')+']' : '(no-remark-field)';"
                + " return 'Code='+rc+' | Remark='+rr; }",
                java.util.List.of(code, remark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Shift: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code, String remark) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code) && lastEntry.contains("Remark=" + remark);
    }

    /**
     * Dump the {@code AccountingShift} scope object — which keys hold the times, and what is in them.
     *
     * <p>Both time boxes share {@code ng-model="inputTime"}, so writing that model says nothing about
     * where the value lands. The record's own fields have to be read off the scope.</p>
     */
    public String dumpShiftModel() {
        Object r = page.evaluate("() => {" + JS
                + " const el=[...document.querySelectorAll(\"[ng-model^='AccountingShift.']\")].find(vis);"
                + " if(!el) return '(no AccountingShift element)';"
                + " try{ const s=angular.element(el).scope();"
                + "   const o=s&&s.AccountingShift; if(!o) return '(no AccountingShift on scope)';"
                + "   return Object.keys(o).map(k=>k+'='+JSON.stringify(o[k])).join(' | ').slice(0,1200);"
                + " }catch(e){ return '(scope read failed: '+e.message+')'; } }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== Shift AccountingShift model ===\n" + s);
        return s;
    }

    /**
     * Enter <b>From Time</b> and <b>To Time</b> through their own pickers.
     *
     * <p>Typing into the boxes is not enough, and not even distinguishable: both carry
     * {@code ng-model="inputTime"} — the same collision as the OT sheet's eight time boxes — so setting
     * "the time field" writes one shared model and leaves the record's own From/To untouched. Each box
     * has a {@code toggle()} that opens a popup carrying {@code hours}, {@code minutes} and a meridian
     * button; that popup is what feeds the real model. The result is read back off the
     * {@code AccountingShift} scope, not off the DOM.</p>
     *
     * @param from "hh:mm AM|PM", e.g. "08:00 AM"
     * @param to   "hh:mm AM|PM", e.g. "04:00 PM"
     */
    public String enterTimes(String from, String to) {
        String[] f = splitTime(from), t = splitTime(to);
        Object r = page.evaluate("(a) => new Promise(async resolve => {" + JS
                + " const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const [fh,fm,fap,th,tm,tap]=a;"
                + " const boxes=[...document.querySelectorAll(\"input[ng-model='inputTime']\")].filter(vis);"
                + " const openPicker=async(box)=>{ let host=box,tog=null;"
                + "   for(let i=0;i<5&&host&&!tog;i++){ host=host.parentElement;"
                + "     if(host) tog=[...host.querySelectorAll('button,a,i,span')]"
                + "       .find(x=>/toggle\\(\\)/.test(x.getAttribute('ng-click')||'')); }"
                + "   if(!tog) return null; tog.click(); await sleep(800); return tog; };"
                + " const fill=async(box,hh,mm,ap)=>{"
                + "   const tog=await openPicker(box); if(!tog) return '(no toggle)';"
                + "   const hrs=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(vis)[0];"
                + "   const min=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(vis)[0];"
                + "   if(!hrs||!min) return '(no hours/minutes in the picker)';"
                + "   setEl(hrs,hh); await sleep(300); setEl(min,mm); await sleep(400);"
                // The box reads ": : PM", so the popup is a 12-hour picker: without matching the meridian
                // an "08:00" From silently becomes 8 PM and the screen rejects the pair.
                + "   const mer=[...document.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/toggleMeridian\\(\\)/.test(x.getAttribute('ng-click')||'')"
                + "          || /^(am|pm)$/i.test(norm(x.textContent)));"
                + "   let merTxt=mer? norm(mer.textContent) : '';"
                + "   if(mer && merTxt && merTxt.toUpperCase()!==ap){ mer.click(); await sleep(400);"
                + "     merTxt=norm(mer.textContent); }"
                + "   tog.click(); await sleep(600);"
                + "   return (box.value||'(box empty)')+(merTxt? ' '+merTxt : ''); };"
                + " const rf = boxes[0]? await fill(boxes[0],fh,fm,fap) : '(no From box)';"
                + " const rt = boxes[1]? await fill(boxes[1],th,tm,tap) : '(no To box)';"
                + " let model='(scope unread)';"
                + " try{ const el=[...document.querySelectorAll(\"[ng-model^='AccountingShift.']\")].find(vis);"
                + "      const o=el&&angular.element(el).scope().AccountingShift;"
                + "      if(o) model=Object.keys(o).filter(k=>/time/i.test(k))"
                + "        .map(k=>k+'='+JSON.stringify(o[k])).join(' ')||'(no *time* key on the model)';"
                + " }catch(e){ model='(scope read failed: '+e.message+')'; }"
                + " resolve('FromBox='+rf+' | ToBox='+rt+' | timeBoxes='+boxes.length+' | model: '+model); })",
                java.util.List.of(f[0], f[1], f[2], t[0], t[1], t[2]));
        lastTimes = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("Shift: times -> " + lastTimes);
        return lastTimes;
    }

    /** "08:00 AM" -> {"08","00","AM"}; a bare "08:00" is read as AM. */
    private static String[] splitTime(String v) {
        String s = v == null ? "" : v.trim().toUpperCase();
        String ap = s.endsWith("PM") ? "PM" : "AM";
        String hhmm = s.replaceAll("(?i)\\s*[AP]M$", "").trim();
        String[] p = hhmm.split(":");
        String hh = p.length > 0 ? p[0].trim() : "12";
        String mm = p.length > 1 ? p[1].trim() : "00";
        return new String[] { hh, mm, ap };
    }

    /**
     * Both times reached the record's OWN model.
     *
     * <p>Judged on the {@code AccountingShift} scope rather than the boxes: the shared {@code inputTime}
     * box can show a time the record never received.</p>
     */
    public boolean timesEntered() {
        if (lastTimes == null || lastTimes.contains("(no From box)") || lastTimes.contains("(no toggle)")
                || lastTimes.contains("(no hours/minutes")) return false;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("model: (.*)$").matcher(lastTimes);
        if (!m.find()) return false;
        String model = m.group(1);
        // Two distinct, non-null time values on the model.
        java.util.regex.Matcher v = java.util.regex.Pattern
                .compile("=\"([^\"]+)\"").matcher(model);
        java.util.List<String> vals = new java.util.ArrayList<>();
        while (v.find()) vals.add(v.group(1));
        return vals.size() >= 2 && !vals.get(0).equals(vals.get(1));
    }

    /**
     * Select the <b>Cell Colour</b>.
     *
     * <p>Handled for either shape this module uses: a dropdown of named colours, or an
     * {@code <input type="color">} like the DMS Folder screen's. A colour input never fires its own
     * change event when the value is assigned, so the events are dispatched explicitly and the value is
     * read back — otherwise the model keeps its default and the save stores the wrong colour.</p>
     */
    public String selectCellColour(String hex) {
        Object r = page.evaluate("(v) => {" + JS
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(e=>!/pagination/i.test(ngOf(e)));"
                + " const s=sels.find(e=>/colou?r/i.test(tail(e)))"
                + "   || sels.find(e=>/colou?r/i.test(ngOf(e)+' '+labelOf(e)));"
                + " if(s){ const reals=[...s.options].map((o,i)=>({o,i}))"
                + "     .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + "   if(!reals.length) return '(no-option) ['+(ngOf(s)||'?')+']';"
                + "   s.selectedIndex=reals[0].i;"
                + "   try{ const c=angular.element(s).controller('ngModel');"
                + "        if(c){ c.$setViewValue(s.value); c.$render(); } }catch(e){}"
                + "   s.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   return norm((s.options[s.selectedIndex]||{}).text||'')+' ['+(ngOf(s)||'?')+', dropdown]'; }"
                + " const ci=[...document.querySelectorAll('input')].filter(vis)"
                + "   .find(e=>e.type==='color' || /colou?r/i.test(tail(e)+' '+(e.placeholder||'')+' '+labelOf(e)));"
                + " if(!ci) return '(no-colour-control)';"
                + " ci.focus(); ci.value=v;"
                + " try{ const c=angular.element(ci).controller('ngModel');"
                + "      if(c){ c.$setViewValue(v); c.$render(); } }catch(e){}"
                + " ci.dispatchEvent(new Event('input',{bubbles:true}));"
                + " ci.dispatchEvent(new Event('change',{bubbles:true}));"
                + " ci.dispatchEvent(new Event('blur',{bubbles:true}));"
                // The grid shows Cell Colour as a swatch with no text, so the list cannot evidence it.
                // Read the value back off the record's own model instead of trusting the input.
                + " let onModel='';"
                + " try{ const o=angular.element(ci).scope().AccountingShift;"
                + "      if(o) onModel=' model.cellcolor='+JSON.stringify(o.cellcolor); }catch(e){}"
                + " return ci.value+' ['+(ngOf(ci)||'?')+', '+ci.type+' input]'+onModel; }", hex);
        lastColour = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("Shift: Cell Colour = " + lastColour);
        return lastColour;
    }

    public boolean colourSelected() {
        return lastColour != null && !lastColour.isEmpty() && !lastColour.startsWith("(")
                && !lastColour.matches("^-*\\s*[Ss]elect.*");
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
                    + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis)"
                    + "   .filter(x=>!/cke/i.test(x.className||''))"
                    + "   .find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /submit|fnIUD/i.test(x.getAttribute('ng-click')||''));"
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
        System.out.println("Shift: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * remark, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String remark) {
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
            // Record WHERE the search happened. "0 rows" on its own cannot tell a genuinely absent record
            // from a filter typed into the wrong column or a form that never returned to the list.
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
                    + " return 'rowsBeforeFilter='+rows+', filterBoxes='+boxes.length"
                    + "   +', filtered='+(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.rm? withCode.find(t=>t.includes(a.rm)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "rm", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("Shift: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
