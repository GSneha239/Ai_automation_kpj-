package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>Franchise Master</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → select the <b>Location</b> → enter <b>Code</b>, <b>Remark</b>,
 * <b>Address</b>, <b>Contact No</b> and <b>E-mail</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Five boxes on one form, so each is claimed <b>most specific first</b> against the ng-model's last
 * segment, and every box that has been claimed is removed from the pool before the next field is looked
 * up. Two fields can then never land in the same box — the defect that put the contact's phone into the
 * agent's field on Service Agent and the area name into the Code field on Area/Town.</p>
 */
public class FranchiseMaster extends BasePage {

    public FranchiseMaster(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastLocation = "",
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
     * Application Configuration &rarr; Inventory &rarr; Franchise Master.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*inventory\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*franchise\\s*master\\s*$/i.test(norm(x.textContent)))"
                + "   || [...document.querySelectorAll('a[href]')]"
                + "        .find(x=>/^\\s*franchise\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__fmMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__fmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("FranchiseMaster.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__fmMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On the Franchise Master screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        return route.contains("franchise");
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
                + "     if(t||click) out.push('BTN \"'+t.slice(0,24)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,26)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== FranchiseMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        if (formOpen()) return true;
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?franchise|AddFranchise/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__fmAdd'; }");
        try {
            page.locator("#__fmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("FranchiseMaster.clickAdd: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__fmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && !/colFilter|row\\.entity/i.test(e.getAttribute('ng-model')||'')"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("FranchiseMaster.clickAdd: Code field never appeared");
        }
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

    /**
     * Select the <b>Location</b>.
     *
     * <p>The list is waited for rather than sampled once — these dropdowns fill asynchronously, and a
     * single early read reports "(no-option)" for a screen that is merely still loading.</p>
     */
    public String selectLocation() {
        long end = System.currentTimeMillis() + 12000;
        int n = 0;
        while (n == 0 && System.currentTimeMillis() < end) {
            Object c = page.evaluate("() => {" + JS
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .filter(s=>!/pagination/i.test(ngOf(s)))"
                    + "   .find(s=>/location|centre|center|branch/i.test(tail(s)+' '+labelOf(s)))"
                    + "   || [...document.querySelectorAll('select')].filter(vis)"
                    + "      .filter(s=>!/pagination/i.test(ngOf(s)))[0];"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }");
            n = c instanceof Number ? ((Number) c).intValue() : 0;
            if (n == 0) page.waitForTimeout(400);
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__fmLoc').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(ngOf(s)));"
                + " const e=sels.find(s=>/location|centre|center|branch/i.test(tail(s)))"
                + "   || sels.find(s=>/location|centre|center|branch/i.test(ngOf(s)+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__fmLoc';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length}; }");
        if (info == null) { lastLocation = "(no location dropdown)"; return lastLocation; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) { lastLocation = "(no-option) [" + m.get("ng") + "]"; return lastLocation; }
        try {
            page.locator("#__fmLoc").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            lastLocation = "(select failed: " + e.getMessage().split("\n")[0] + ")";
            return lastLocation;
        }
        waitForAngular(900);
        // Read the choice back off the control, so a click that did not bind cannot pass as a selection.
        Object t = page.evaluate("() => { const e=document.getElementById('__fmLoc');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastLocation = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("FranchiseMaster: Location = " + lastLocation);
        return lastLocation;
    }

    public boolean locationSelected() {
        return lastLocation != null && !lastLocation.isEmpty() && !lastLocation.startsWith("(")
                && !lastLocation.matches("^-*\\s*[Ss]elect.*");
    }

    /**
     * Enter <b>Code</b>, <b>Remark</b>, <b>Address</b>, <b>Contact No</b> and <b>E-mail</b>.
     *
     * <p>Claimed most specific first, each box removed from the pool once taken. Order matters: "contact
     * no" and "e-mail" both sit under a Contact heading on screens like Service Agent, and a loose match
     * on the screen's own name would take the Code box before any of them.</p>
     */
    public String enterDetails(String code, String remark, String address, String contact, String email) {
        Object r = page.evaluate("(a) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const used=new Set();"
                + " const free=()=>boxes.filter(e=>!used.has(e));"
                + " const claim=(exact, loose, labelRx, value, name)=>{"
                + "   let el=free().find(e=>exact.test(tail(e)));"
                + "   if(!el) el=free().find(e=>loose.test(tail(e)));"
                + "   if(!el) el=free().find(e=>labelRx.test(norm(labelOf(e))));"
                + "   if(!el) el=free().find(e=>labelRx.test(norm(e.placeholder||'')));"
                + "   if(!el) return name+'=(no field)';"
                + "   used.add(el);"
                + "   return name+'='+setEl(el,value)+' ['+(ngOf(el)||'?')+']'; };"
                + " const out=[];"
                + " out.push(claim(/^(email|emailid|mailid|emailaddress)$/i, /e-?mail/i, /e-?mail/i, a.em, 'Email'));"
                + " out.push(claim(/^(contactno|contactnumber|phoneno|telephoneno|mobileno)$/i,"
                + "   /contact|phone|telephone|mobile|cell/i, /contact\\s*no|phone|telephone|mobile/i,"
                + "   a.ct, 'ContactNo'));"
                + " out.push(claim(/^address$/i, /address/i, /address/i, a.ad, 'Address'));"
                + " out.push(claim(/^(remark|remarks|description)$/i, /remark|description/i,"
                + "   /^remarks?$|description/i, a.rm, 'Remark'));"
                + " out.push(claim(/^code$/i, /code$/i, /^code$/i, a.c, 'Code'));"
                + " return out.join(' | '); }",
                java.util.Map.of("c", code, "rm", remark, "ad", address, "ct", contact, "em", email));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("FranchiseMaster: " + lastEntry);
        return lastEntry;
    }

    /** Every field carries its OWN value — the check that catches one value written over another. */
    public boolean allEntered(String... namesAndValues) {
        if (lastEntry == null || lastEntry.contains("(no field)")) return false;
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (!lastEntry.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) return false;
        }
        return true;
    }

    /** Which fields did NOT receive their value — named, so the report says what actually went wrong. */
    public String missing(String... namesAndValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (lastEntry == null || !lastEntry.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) {
                sb.append(sb.length() == 0 ? "" : ", ").append(namesAndValues[i]);
            }
        }
        return sb.toString();
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
                    + "   || all.find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)||x.value||''));"
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
        System.out.println("FranchiseMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
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
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.r? withCode.find(t=>t.includes(a.r)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,160);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "r", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("FranchiseMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
