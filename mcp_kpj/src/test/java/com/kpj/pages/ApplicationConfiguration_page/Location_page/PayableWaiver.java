package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Payable Waiver</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Payable Waiver</b> ({@code #/waiver}) →
 * <b>Add</b> ({@code AddWaiver()}, opens {@code #/add-waiver}) → <b>Location</b>, <b>Department</b>,
 * <b>Doctor</b>, <b>Visit Type</b>, <b>Pricing Policy</b>, <b>Service</b>, <b>Waiver Days</b>,
 * <b>Service Rate</b> → <b>Submit</b> ({@code fnIUDWaiver()}) → success toast.</p>
 *
 * <p>Third distinct Waiver screen alongside [[devhis-department-waiver]] and [[devhis-location-waiver]] — this
 * one's ng-model prefix is the shortest, {@code waiver.*} (the other two are {@code deptwaiver.*} and
 * {@code Locationwaiver.*}), suggesting this is the original module the other two were copied from.
 * {@code waiver.LocationaID} keeps the family's "Locationa" typo.</p>
 *
 * <p><b>Cascade is FOUR deep</b>, confirmed live by selecting one level at a time and reading the next: Location
 * → Department (populates) → Doctor (ng-model {@code waiver.PayableID} — labelled "Doctor" but bound to the same
 * Payable field the sibling screens use; the flow's "Doctor" IS this screen's Payable) → Visit Type (ng-model
 * {@code waiver.VisitTypeID}, populates only once Doctor commits: Follow Up / New / Other). <b>Pricing Policy</b>
 * ({@code waiver.TariffID}) and <b>Service</b> ({@code waiver.ServiceID}, 3 fixed options) are independent of the
 * whole chain — their option lists never changed across any Location/Department/Doctor/VisitType combination
 * tried.</p>
 *
 * <p>Selection throughout is JS {@code selectedIndex} + {@code dispatchEvent('change')} + Angular
 * {@code triggerHandler('change')}, not a real Playwright {@code selectOption} — the recipe
 * [[devhis-sub-department-schedule]] proved necessary for this cascade family (a real select can "succeed" while
 * silently not propagating to the next dependent list).</p>
 */
public class PayableWaiver extends BasePage {

    public PayableWaiver(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastDepartment = "", lastDoctor = "", lastVisitType = "";
    public String lastPricingPolicy = "", lastService = "", lastWaiverDays = "", lastServiceRate = "";
    public String lastToasts = "[]", lastSaveApi = "";
    public int lastDepartmentIndex = -1, lastDoctorIndex = -1;
    /** The screen while the message is up — anything after Submit outlives the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /**
     * Best-effort read of the Department values already sitting in the LIST screen's grid — call this
     * BEFORE clicking Add. Every one of those departments already has at least one waiver on this
     * environment, and some (like "(NAMA DR) MR C/N") have waivers against MANY doctors — cycling only
     * the Doctor within that same Department can exhaust the whole retry budget before ever reaching a
     * combination that saves. Starting from a Department NOT in this set instead gives Submit its best
     * shot at succeeding quickly. Never fails the flow; returns an empty set if the grid holds nothing or
     * no Department-shaped column is found.
     */
    public java.util.Set<String> existingDepartmentsInList() {
        Object r = page.evaluate("() => { let best=-1, arr=null;"
                + " document.querySelectorAll('*').forEach(el=>{ try{"
                + "   const s=window.angular.element(el).scope(); if(!s) return;"
                + "   const scan=a=>{ if(Array.isArray(a) && a.length && typeof a[0]==='object'"
                + "        && Object.keys(a[0]).some(k=>/depart/i.test(k)) && a.length>best){ best=a.length; arr=a; } };"
                + "   if(s.grid && s.grid.options && s.grid.options.data) scan(s.grid.options.data);"
                + "   for(const k of Object.keys(s)){ try{ scan(s[k]); }catch(e){} } }catch(e){} });"
                + " const out=[], seen=new Set();"
                + " if(arr){ for(const row of arr){"
                + "   const key=Object.keys(row).find(k=>/depart.*name/i.test(k)) || Object.keys(row).find(k=>/depart/i.test(k) && !/id$/i.test(k));"
                + "   const v=key && row[key] && String(row[key]).trim();"
                + "   if(v && !seen.has(v)){ seen.add(v); out.push(v); } } }"
                + " return out; }");
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) set.add(o.toString());
        System.out.println("PayableWaiver: " + set.size() + " department(s) already in the list -> " + set);
        return set;
    }

    /**
     * The ordinal (0-based, among REAL options) of the first Department option whose text is NOT in
     * {@code exclude}; 0 if every option is already taken or the select is empty.
     */
    public int firstDepartmentIndexAvoiding(java.util.Set<String> exclude) {
        int count = optionCount("waiver.DepartmentID");
        for (int i = 0; i < count; i++) {
            String candidate = peekNth("waiver.DepartmentID", i);
            if (!candidate.isEmpty() && !exclude.contains(candidate)) return i;
        }
        return 0;
    }

    /** The text of the {@code index}-th real option, without selecting it. */
    private String peekNth(String ngModel, int index) {
        Object t = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return '';"
                + " const real=[...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " return n<real.length ? (real[n].textContent||'').trim() : ''; }", java.util.Arrays.asList(ngModel, index));
        return t == null ? "" : t.toString();
    }

    public String findPayableWaiverLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/payable\\s*waiver/i.test(norm(a.textContent)))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,30); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|pagination|row\\.entity/i.test(e.getAttribute('ng-model')||'')).slice(0,20)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,10)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

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
            System.out.println("PayableWaiver.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*payable\\s*waiver\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>x.getAttribute('href')==='#/waiver');"
                + " if(!a) return ''; a.id='__pwMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__pwMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("PayableWaiver.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__pwMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__pwMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("PayableWaiver.nav: menu link not found. links => " + findPayableWaiverLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("PayableWaiver.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /payable\\s*waiver/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("PayableWaiver." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("PayableWaiver." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> ({@code AddWaiver()}). Polls generously — the async render can lag
     *  well past the route change. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 30 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/^addwaiver/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__pwAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("PayableWaiver.clickAdd: Add button not found"); return false; }
        robustClick("__pwAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (Submit + at least four selects) is on screen. Polled — the SPA route change can
     *  leave stale list DOM behind for a beat. */
    public boolean addFormOpen() {
        for (int i = 0; i < 15; i++) {
            boolean ready = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const sub=[...document.querySelectorAll('button,input[type=submit]')].some(b=>b.offsetParent!==null && /^\\s*submit\\s*$/i.test(norm(b.textContent||b.value)));"
                    + " const sels=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null && !/pagination/i.test(s.getAttribute('ng-model')||'')).length;"
                    + " return sub && sels>=4; }"));
            if (ready) return true;
            page.waitForTimeout(500);
        }
        return false;
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}). */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__pwBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__pwBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /**
     * Choose the {@code index}-th real option of the select bound to {@code ngModel} via JS
     * {@code selectedIndex} + {@code dispatchEvent('change')} + Angular {@code triggerHandler('change')} — NOT a
     * real Playwright {@code selectOption}, the recipe proven for this cascade family in
     * [[devhis-sub-department-schedule]].
     */
    private String selectNth(String ngModel, int index) {
        for (int w = 0; w < 12 && optionCount(ngModel) == 0; w++) page.waitForTimeout(600);
        Object r = page.evaluate("([m,n]) => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return '';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return '';"
                + " const i=real[n].i; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ A.element(s).triggerHandler('change'); }catch(x){}"
                + " return s.options[s.selectedIndex].textContent.trim(); }", java.util.Arrays.asList(ngModel, index));
        waitForAngular(900);
        return r == null ? "" : r.toString();
    }

    /** Select the form's <b>Location</b>. Department stays empty until this commits. */
    public String selectLocation() { lastLocation = selectNth("waiver.LocationaID", 0); return lastLocation; }

    /** Select the {@code index}-th <b>Department</b> — DEPENDENT on Location. */
    public String selectDepartment(int index) {
        lastDepartment = selectNth("waiver.DepartmentID", index);
        if (!lastDepartment.isEmpty()) lastDepartmentIndex = index;
        return lastDepartment;
    }

    /** Select the {@code index}-th <b>Doctor</b> (ng-model {@code waiver.PayableID}) — DEPENDENT on Department. */
    public String selectDoctor(int index) {
        lastDoctor = selectNth("waiver.PayableID", index);
        if (!lastDoctor.isEmpty()) lastDoctorIndex = index;
        return lastDoctor;
    }

    /** Select the {@code index}-th <b>Visit Type</b> — DEPENDENT on Doctor. */
    public String selectVisitType(int index) { lastVisitType = selectNth("waiver.VisitTypeID", index); return lastVisitType; }

    /** Select <b>Pricing Policy</b> — independent of the Location/Department/Doctor/Visit Type chain. */
    public String selectPricingPolicy(int index) { lastPricingPolicy = selectNth("waiver.TariffID", index); return lastPricingPolicy; }

    /** Select <b>Service</b> — independent, a fixed 3-option list. */
    public String selectService(int index) { lastService = selectNth("waiver.ServiceID", index); return lastService; }

    /** Select Location → Department → Doctor → Visit Type → Pricing Policy → Service, in cascade order. */
    public String selectAll(int deptIndex, int doctorIndex) {
        String loc = selectLocation();
        String dept = selectDepartment(deptIndex);
        String doc = selectDoctor(doctorIndex);
        String vt = selectVisitType(0);
        String pp = selectPricingPolicy(0);
        String svc = selectService(0);
        return "Location=" + or(loc) + " | Department=" + or(dept) + " | Doctor=" + or(doc)
                + " | Visit Type=" + or(vt) + " | Pricing Policy=" + or(pp) + " | Service=" + or(svc);
    }

    /** Type {@code value} into the box bound to {@code ngModel}. */
    private String fillField(String ngModel, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(m) => { const e=[...document.querySelectorAll('input,textarea')]"
                + " .find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return false; e.id='__pwBox'; e.scrollIntoView({block:'center'}); return true; }", ngModel));
        if (!tagged) { System.out.println("fillField: no input bound to " + ngModel); return ""; }
        page.evaluate("(v) => { const A=window.angular; const e=document.getElementById('__pwBox'); if(!e) return;"
                + " const c=A.element(e).controller('ngModel'); e.value=v;"
                + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " e.dispatchEvent(new Event('blur',{bubbles:true})); }", value);
        waitForAngular(500);
        Object v = page.evaluate("() => { const e=document.getElementById('__pwBox'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** Enter <b>Waiver Days</b> and <b>Service Rate</b>. */
    public String fillRates(String waiverDays, String serviceRate) {
        lastWaiverDays = fillField("waiver.WaiverDays", waiverDays);
        lastServiceRate = fillField("waiver.ServiceRate", serviceRate);
        return "Waiver Days=" + or(lastWaiverDays) + " | Service Rate=" + or(lastServiceRate);
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int deptIndex, int doctorIndex, String waiverDays, String serviceRate) {
        String sel = selectAll(deptIndex, doctorIndex);
        String rates = fillRates(waiverDays, serviceRate);
        return sel + " | " + rates;
    }

    private static String or(String s) { return s == null || s.isEmpty() ? "(not set)" : s; }

    /** Click <b>Submit</b> ({@code fnIUDWaiver()}) and return the toast; screenshots the message the moment it
     *  appears. A successful Submit navigates straight back to the list ({@code #/waiver}). */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("waiver")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__pwToasts=[]; if(window.__pwObs) window.__pwObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pwToasts.includes(t)) window.__pwToasts.push(t); }); };"
                + " window.__pwObs=new MutationObserver(grab); window.__pwObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__pwSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__pwSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__pwToasts||[]).includes(t)) (window.__pwToasts=window.__pwToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__pwToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__pwToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
