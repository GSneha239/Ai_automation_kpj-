package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Shift Allocation</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Shift Allocation</b> ({@code #/EmployeeShiftAllocation})
 * → select the list-level <b>Location</b> → <b>Search</b> → <b>Add</b> (opens {@code #/add-ShiftAllocation}) →
 * <b>Location</b>, <b>Shift</b>, <b>Opening Balance</b>, one <b>Day</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Unlike sibling {@link DepartmentSchedule} / {@link PayableScheduleMaster}, the add form has no inner "Add" —
 * it is a single row and Submit both saves it AND navigates straight back to the list. <b>Shift</b> is a DEPENDENT
 * select (ng-model {@code addShiftAllocation.ShiftId}) — empty until <b>Location</b> (ng-model
 * {@code addShiftAllocation.LocationId}) is chosen, so it must be selected first and the Shift options polled.
 * <b>Opening Balance</b> is a plain text input (ng-model {@code addShiftAllocation.openingbalance}) whose label
 * walk mis-resolves to "Location*" — cosmetic, the ng-model is authoritative. The seven <b>Days</b> checkboxes all
 * share ng-model {@code todo.done}, same as the sibling schedule screens, so each is matched on its own nearby
 * text.</p>
 */
public class ShiftAllocation extends BasePage {

    public ShiftAllocation(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastFormLocation = "", lastShift = "", lastOpeningBalance = "", lastDay = "";
    public String lastToasts = "[]", lastSaveApi = "";
    public int lastShiftIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "shift", with the submenu it sits under. */
    public String findShiftLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/shift/i.test(norm(a.textContent)) || /shift/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** Every visible field / select / checkbox / button with its ng-model or ng-click. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,34); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|paginationCurrentPage|row\\.entity/i.test(e.getAttribute('ng-model')||'')).slice(0,30)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,20)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("ShiftAllocation.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*shift\\s*allocation\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/employeeshiftallocation/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__saMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__saMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("ShiftAllocation.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__saMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__saMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("ShiftAllocation.nav: menu link not found. shift links => " + findShiftLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("ShiftAllocation.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /shift\\s*allocation/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("ShiftAllocation." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("ShiftAllocation." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Choose the {@code index}-th real option of the select bound to {@code ngModel} (0 = first non-placeholder). */
    private String selectNth(String ngModel, int index) {
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__saSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(!real.length) return -1; return real[Math.min(n, real.length-1)].i; }",
                java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__saSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__saSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__saSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(800);
        Object t = page.evaluate("() => { const e=document.getElementById('__saSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return -1; }
    }

    // ---- list screen -------------------------------------------------------

    /** Select the list screen's <b>Location</b> filter — polls for the select, which renders after the route change. */
    public String selectListLocation() {
        for (int i = 0; i < 15 && optionCount("EmployeeShiftAllocation.Locationid") <= 0; i++) page.waitForTimeout(500);
        lastLocation = selectNth("EmployeeShiftAllocation.Locationid", 0);
        return lastLocation;
    }

    /** Click the list screen's <b>Search</b> ({@code getShiftAllocation()}). */
    public boolean clickSearch() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                    + "   && (/getshiftallocation/i.test(x.getAttribute('ng-click')||'') || /^\\s*search\\s*$/i.test(norm(x.textContent||x.value))));"
                    + " if(!b) return false; b.id='__saSearch'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ShiftAllocation.clickSearch: Search button not found"); return false; }
        robustClick("__saSearch", "clickSearch");
        waitForAngular(1500);
        return true;
    }

    /** Click the list screen's <b>Add</b> ({@code AddShiftAllocation()}). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/addshiftallocation/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__saAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ShiftAllocation.clickAdd: Add button not found"); return false; }
        robustClick("__saAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Location select) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                + " .some(s=>/addShiftAllocation\\.LocationId/i.test(s.getAttribute('ng-model')||''))"));
    }

    // ---- add form ------------------------------------------------------------

    /** Select the form's <b>Location</b>. Shift stays empty until this commits. */
    public String selectLocation() { lastFormLocation = selectNth("addShiftAllocation.LocationId", 0); return lastFormLocation; }

    /**
     * Select the {@code index}-th <b>Shift</b>. DEPENDENT on Location — the select is refilled by Location's
     * ng-change, so its options are polled rather than read immediately.
     */
    public String selectShift(int index) {
        int shifts = -1;
        for (int w = 0; w < 12; w++) {
            shifts = optionCount("addShiftAllocation.ShiftId");
            if (shifts > 0) break;
            page.waitForTimeout(500);
        }
        if (shifts <= 0) { System.out.println("selectShift: Shift list never populated (Location not committed?)"); return ""; }
        lastShift = selectNth("addShiftAllocation.ShiftId", index);
        if (!lastShift.isEmpty()) lastShiftIndex = index;
        return lastShift;
    }

    /**
     * Enter <b>Opening Balance</b>. The sticky header overlays this field (same class of trap as the buttons), so
     * a real click/type is tried first and a JS value-set + Angular ngModel commit is the fallback — plain DOM
     * value assignment alone leaves the model unset and Submit saves 0 regardless of what was "typed".
     */
    public String fillOpeningBalance(String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const e=[...document.querySelectorAll('input')]"
                + " .find(x=>/addShiftAllocation\\.openingbalance/i.test(x.getAttribute('ng-model')||''));"
                + " if(!e) return false; e.id='__saOB'; e.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) { System.out.println("fillOpeningBalance: Opening Balance input not found"); return ""; }
        boolean typed = true;
        try {
            page.locator("#__saOB").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            page.locator("#__saOB").fill("");
            page.locator("#__saOB").type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(60));
            page.locator("#__saOB").press("Tab");
        } catch (Exception e) { typed = false; System.out.println("fillOpeningBalance: real click/type intercepted — JS fallback"); }
        waitForAngular(400);
        Object shown = page.evaluate("() => { const e=document.getElementById('__saOB'); return e?(e.value||''):''; }");
        if (!typed || shown == null || !value.equals(String.valueOf(shown).trim())) {
            page.evaluate("([v]) => { const e=document.getElementById('__saOB'); if(!e) return; const A=window.angular;"
                    + " const c=A.element(e).controller('ngModel'); e.value=v;"
                    + " if(c){ c.$setViewValue(v); c.$render(); }"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }",
                    java.util.Arrays.asList(value));
            waitForAngular(400);
        }
        Object v = page.evaluate("() => { const e=document.getElementById('__saOB'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        lastOpeningBalance = v == null ? "" : v.toString().trim();
        return lastOpeningBalance;
    }

    /**
     * Tick ONE <b>Day</b>. All seven boxes share ng-model {@code todo.done}, so the wanted one is matched on its
     * own nearby text — same wiring as {@link DepartmentSchedule#tickDays}.
     */
    public String tickDay(String day) {
        Object r = page.evaluate("(d) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''));"
                + " const cb=cbs.find(c=>new RegExp(d,'i').test(norm((c.closest('label')||c.parentElement||{}).textContent||'')));"
                + " if(!cb) return ''; if(cb.checked) return 'already'; cb.id='__saDay'; return 'tag'; }", day);
        String how = String.valueOf(r);
        if (how.isEmpty()) { System.out.println("tickDay: no checkbox for " + day); lastDay = ""; return ""; }
        if ("tag".equals(how)) robustClick("__saDay", "tickDay");
        boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__saDay');"
                + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
        waitForAngular(200);
        lastDay = (on || "already".equals(how)) ? day : "";
        return lastDay;
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}) so a retry starts from a fresh form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__saBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__saBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int shiftIndex, String openingBalance, String day) {
        String loc = selectLocation();
        String shift = selectShift(shiftIndex);
        String ob = fillOpeningBalance(openingBalance);
        String d = tickDay(day);
        return "Location=" + loc + " | Shift=" + shift + " | Opening Balance=" + ob + " | Day=" + d;
    }

    /**
     * Click <b>Submit</b> ({@code fnSubmit()}) and return the toast; screenshots the message the moment it
     * appears. Unlike the sibling schedule screens, a successful Submit here also navigates straight back to the
     * list ({@code #/EmployeeShiftAllocation}) — there is no inner "Add" / Back step to run first.
     */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("shiftallocation")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__saToasts=[]; if(window.__saObs) window.__saObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__saToasts.includes(t)) window.__saToasts.push(t); }); };"
                + " window.__saObs=new MutationObserver(grab); window.__saObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__saSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__saSubmit", "submit");

        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("submit: toast screenshot failed - " + e.getMessage()); break; }
            } else page.waitForTimeout(200);
        }
        if (toastPng == null) System.out.println("submit: no message was ever on screen to screenshot");
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__saToasts||[]).includes(t)) (window.__saToasts=window.__saToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__saToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        System.out.println("submit: toasts => " + lastToasts);
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                java.util.regex.Matcher rs = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (rs.find() ? ", ResultStatus=" + rs.group(1) : "")
                        + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__saToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(600);
        return r == null ? "" : r.toString().trim();
    }
}
