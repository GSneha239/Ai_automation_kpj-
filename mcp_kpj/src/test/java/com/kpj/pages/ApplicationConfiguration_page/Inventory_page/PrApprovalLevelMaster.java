package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>PR Approval Level Master</b> — Page Object.
 *
 * <p>Flow: select the <b>PR Approval Level Master</b>, enter the <b>Min</b> and <b>Max</b>, then
 * <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class PrApprovalLevelMaster extends BasePage {

    public PrApprovalLevelMaster(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "", lastAdd = "";

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
     * Application Configuration &rarr; Inventory &rarr; PrApprovalLevelMaster.
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

        // Anchored to the WHOLE label, and hidden anchors are included on purpose: these menu links sit in
        // the DOM before the submenu shows them. A loose match would take the first entry that merely
        // contains the words — the scaffolding trap that once ran an entire Compliance flow against
        // Complaint Type and reported PASS against the wrong screen's fields.
        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*pr\\s*approval\\s*level\\s*master\\s*$/i.test(norm(x.textContent))"
                + "        || /^\\s*pr\\s*approval\\s*level\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__prMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PrApprovalLevelMaster.nav: " + e.getMessage().split("\\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__prMenu'); if(e) e.removeAttribute('id'); }");
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
        // The route resolving is not the screen rendering. GENMST loads its view after the hash changes,
        // and until it does the DASHBOARD's controls are still in the DOM — a run that starts typing
        // straight away puts its values into dashboard widgets (LocationName took the remark once).
        waitForScreenControls(15000);

        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    /** Wait for this screen's OWN controls — a Code box, or its grid — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const tail=e=>((e.getAttribute('ng-model')||'').split('.').pop()||'');"
                    + " const fields=[...document.querySelectorAll('input,textarea')].filter(vis)"
                    + "   .some(e=>/^(min|max)$/i.test(tail(e)));"
                    + " const level=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .some(e=>/level/i.test(tail(e)));"
                    + " const grid=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .some(vis);"
                    + " return fields || level || grid; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("PrApprovalLevelMaster.waitForScreenControls: no Code box or grid within "
                    + timeoutMs + "ms — the screen may not have rendered");
            return false;
        }
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
     * On the PrApprovalLevelMaster screen — matched on the hash route, never on page text.
     *
     * <p>Routes in this section bear no resemblance to their labels — the sibling "Generic" is served
     * from {@code #/GENMST} — so the route the MENU gives is authoritative and a label-derived route is
     * never assumed. Demanding that the route contain the label is what rejected the page the menu had
     * just opened on that screen.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        if (!menuRoute.isEmpty() && route.contains(menuRoute)) return true;
        return route.contains("approval") || route.contains("prlevel") || route.contains("prapproval");
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
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== PrApprovalLevelMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        // This screen has NO Code box — its entry fields are the level dropdown and Min/Max, and they sit
        // on the list route itself. Judging "is the form open" by a Code box, as the sibling screens do,
        // reported this one as never opening.
        Object r = page.evaluate("() => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const minmax=boxes.some(e=>/^(min|max)$/i.test(tail(e)));"
                + " const level=[...document.querySelectorAll('select')].filter(vis)"
                + "   .some(e=>/level/i.test(tail(e)));"
                + " return minmax || level; }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> if the entry form is not already on screen.
     *
     * <p>The requested steps do not mention Add, so this reports which of the two the screen did rather
     * than assuming either.</p>
     */
    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?pr|AddPRApproval|AddPrApproval/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__prAdd'; }");
        try {
            page.locator("#__prAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("PrApprovalLevelMaster.openFormIfNeeded: " + e.getMessage().split("\\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__prAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastAdd = formOpen() ? "The form needed Add; it is now open." : "Add did not open a form.";
        return lastAdd;
    }

    public String lastLevel = "", lastMinMax = "";

    /**
     * Select the <b>PR Approval Level Master</b> dropdown.
     *
     * <p>The list is waited for rather than sampled once — these dropdowns fill asynchronously, and one
     * early read reports "(no-option)" for a screen that is merely still loading.</p>
     */
    public String selectLevel() {
        long end = System.currentTimeMillis() + 12000;
        int n = 0;
        while (n == 0 && System.currentTimeMillis() < end) {
            Object c = page.evaluate("() => {" + JS
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .filter(s=>!/pagination|colFilter/i.test(ngOf(s)))[0];"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }");
            n = c instanceof Number ? ((Number) c).intValue() : 0;
            if (n == 0) page.waitForTimeout(400);
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__prSel').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination|colFilter/i.test(ngOf(s)));"
                + " const e=sels.find(s=>/approval|level|prtype|type$/i.test(tail(s)))"
                + "   || sels.find(s=>/approval|level/i.test(ngOf(s)+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__prSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length}; }");
        if (info == null) { lastLevel = "(no dropdown on this screen)"; return lastLevel; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) { lastLevel = "(no-option) [" + m.get("ng") + "]"; return lastLevel; }
        try {
            page.locator("#__prSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            lastLevel = "(select failed: " + e.getMessage().split("\\n")[0] + ")";
            return lastLevel;
        }
        waitForAngular(900);
        // Read the choice back off the control, so a click that did not bind cannot pass as a selection.
        Object t = page.evaluate("() => { const e=document.getElementById('__prSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastLevel = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("PrApprovalLevelMaster: level = " + lastLevel);
        return lastLevel;
    }

    public String lastLocation = "";

    /**
     * Select the <b>Location</b> ({@code ApprovalDrp.locationid}).
     *
     * <p>Not in the requested steps, and the screen defaults it to "--All--". It is set only as a retry
     * when Submit refuses, so the report can say whether the refusal was caused by the missing location
     * or is a fault of the save itself.</p>
     */
    public String selectLocation() {
        Object r = page.evaluate("() => {" + JS
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>/locationid$/i.test(tail(s)));"
                + " if(!e) return '(no location dropdown)';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*(all|select)/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(no real location option)';"
                + " e.selectedIndex=reals[0].i;"
                + " try{ const c=angular.element(e).controller('ngModel');"
                + "      if(c){ c.$setViewValue(e.value); c.$render(); } }catch(err){}"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return norm((e.options[e.selectedIndex]||{}).text||'')+' ['+(ngOf(e)||'?')+']'; }");
        lastLocation = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("PrApprovalLevelMaster: location = " + lastLocation);
        return lastLocation;
    }

    public boolean levelSelected() {
        return lastLevel != null && !lastLevel.isEmpty() && !lastLevel.startsWith("(")
                && !lastLevel.matches("^-*\\s*[Ss]elect.*");
    }

    /**
     * Enter <b>Min</b> and <b>Max</b>.
     *
     * <p>Told apart by the ng-model's last segment and by each other: "min" and "max" are one letter
     * apart, and both sit inside models prefixed with the screen's own name, so a loose match can fill
     * the same box twice and leave the other empty while both look entered.</p>
     */
    public String enterMinMax(String min, String max) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [mn, mx] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const minEl=boxes.find(e=>/^(min|minamount|minvalue|fromamount|minamt)$/i.test(tail(e)))"
                + "   || boxes.find(e=>/^min/i.test(tail(e)))"
                + "   || boxes.find(e=>/\\bmin\\b/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const maxEl=boxes.filter(e=>e!==minEl)"
                + "     .find(e=>/^(max|maxamount|maxvalue|toamount|maxamt)$/i.test(tail(e)))"
                + "   || boxes.filter(e=>e!==minEl).find(e=>/^max/i.test(tail(e)))"
                + "   || boxes.filter(e=>e!==minEl).find(e=>/\\bmax\\b/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rmn = minEl? setEl(minEl,mn)+' ['+(ngOf(minEl)||'?')+']' : '(no-min-field)';"
                + " const rmx = maxEl? setEl(maxEl,mx)+' ['+(ngOf(maxEl)||'?')+']' : '(no-max-field)';"
                + " return 'Min='+rmn+' | Max='+rmx; }",
                java.util.List.of(min, max));
        lastMinMax = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("PrApprovalLevelMaster: " + lastMinMax);
        return lastMinMax;
    }

    /** Both boxes hold their OWN value — the check that catches min and max landing in one box. */
    public boolean minMaxEntered(String min, String max) {
        return lastMinMax != null && !lastMinMax.contains("(no-")
                && lastMinMax.contains("Min=" + min) && lastMinMax.contains("Max=" + max);
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
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\\n")[0];
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
        System.out.println("PrApprovalLevelMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the saved row up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The row must carry BOTH the min and the max, since either alone can belong
     * to a band that already existed.</p>
     */
    public boolean rowInList(String min, String max) {
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
            Object where = page.evaluate("() => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " return 'rows='+rows+', columns=['+heads+']'; }");
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const mine=rows.find(t=>t.includes(a.mn) && t.includes(a.mx));"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " const partial=rows.filter(t=>t.includes(a.mn)||t.includes(a.mx));"
                    + " return partial.length? 'no row carries BOTH values; rows with one of them: '"
                    + "     +partial.map(t=>t.slice(0,60)).join(' / ')"
                    + "   : 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("mn", min, "mx", max));
            lastListCheck += "looked for a row with min " + min + " and max " + max + " -> "
                    + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("PrApprovalLevelMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
