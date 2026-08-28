package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Sub Department</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Sub Department</b> ({@code #/subdepartmentMaster})
 * → <b>Add</b> ({@code AddSubDepartment()}, opens {@code #/add-subdepartment}) → <b>Code</b>, <b>Sub Department</b>,
 * <b>Department</b>, <b>Default HOD</b>, <b>Time Slot(Min)</b> → <b>Submit</b> → success toast <i>"Sub Department
 * Saved Successfully."</i>.</p>
 *
 * <p>Simpler than [[devhis-modality-schedule]]: <b>Department</b> ({@code subdepartment.Departmentfkid}, 174
 * options) and <b>Default HOD</b> ({@code subdepartment.defaulthodid}, 265 options) are BOTH already fully
 * populated when the form opens — neither is dependent on the other, so both take a real Playwright
 * {@code selectOption} straight away with no polling needed. Like [[devhis-payable-type]] / [[devhis-sub-tax-master]]
 * -style masters, the existing Sub Department codes are NOT one consistent shape (`CARD01`, `001`, `Code3`, `34`,
 * ...) so there is no table format to continue — a fresh unique code is generated instead.</p>
 */
public class SubDepartment extends BasePage {

    public SubDepartment(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastCode = "", lastSubDepartment = "", lastDepartment = "", lastHod = "", lastTimeSlot = "";
    public String lastToasts = "[]", lastSaveApi = "";
    /** The screen while the message is up. */
    public byte[] toastPng;

    /** Sub-department names a hospital actually uses — no "Auto Sub Department X" placeholders. */
    private static final String[] NAMES = {
            "Sub-Nephrology", "Sub-Cardiology", "Sub-Neurology", "Sub-Oncology", "Sub-Endocrinology",
            "Sub-Gastroenterology", "Sub-Rheumatology", "Sub-Urology", "Sub-Dermatology", "Sub-Psychiatry"
    };

    // ---- probes ----------------------------------------------------------

    public String findSubDepartmentLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/sub\\s*department/i.test(norm(a.textContent)) || /subdepartment/i.test(a.getAttribute('href')||''))"
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
            System.out.println("SubDepartment.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*sub\\s*department\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/subdepartmentmaster/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__sdMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__sdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("SubDepartment.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__sdMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__sdMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("SubDepartment.nav: menu link not found. sub department links => " + findSubDepartmentLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("SubDepartment.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /sub\\s*department/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("SubDepartment." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("SubDepartment." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> ({@code AddSubDepartment()}). Polls generously — the async render can
     *  lag well past the route change (same class of trap as [[devhis-payable-type]]'s Add button). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 40 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/addsubdepartment/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__sdAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("SubDepartment.clickAdd: Add button not found"); return false; }
        robustClick("__sdAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Code input) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')]"
                + " .some(s=>/subdepartment\\.SubDepartmentcode/i.test(s.getAttribute('ng-model')||''))"));
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}) so a retry starts from a fresh form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__sdBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__sdBack", "clickBack");
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

    /** Choose the {@code index}-th real option of the select bound to {@code ngModel}. Both selects on this form
     *  are pre-populated (no dependency chain), so a short poll before the real {@code selectOption} is enough. */
    private String selectNth(String ngModel, int index) {
        for (int w = 0; w < 8 && optionCount(ngModel) == 0; w++) page.waitForTimeout(400);
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__sdSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return -1; return real[n].i; }", java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__sdSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__sdSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__sdSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__sdSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** Select the {@code index}-th <b>Department</b>. */
    public String selectDepartment(int index) { lastDepartment = selectNth("subdepartment.Departmentfkid", index); return lastDepartment; }

    /** Select the {@code index}-th <b>Default HOD</b>. */
    public String selectDefaultHod(int index) { lastHod = selectNth("subdepartment.defaulthodid", index); return lastHod; }

    /** Type {@code value} into the box bound to {@code ngModel}. */
    private String fillField(String ngModel, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(m) => { const e=[...document.querySelectorAll('input,textarea')]"
                + " .find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return false; e.id='__sdBox'; e.scrollIntoView({block:'center'}); return true; }", ngModel));
        if (!tagged) { System.out.println("fillField: no input bound to " + ngModel); return ""; }
        page.evaluate("(v) => { const A=window.angular; const e=document.getElementById('__sdBox'); if(!e) return;"
                + " const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " e.dispatchEvent(new Event('blur',{bubbles:true})); }", value);
        waitForAngular(400);
        Object v = page.evaluate("() => { const e=document.getElementById('__sdBox'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** Enter <b>Code</b>, <b>Sub Department</b> and <b>Time Slot(Min)</b>. {@code attempt} moves both text fields
     *  along so a retry after "already exists" submits different details. */
    public String fillCodeAndName(int attempt) {
        lastCode = "SD" + String.format("%05d", Math.abs((System.nanoTime() / 977 + attempt * 7919L) % 100000));
        lastSubDepartment = NAMES[(int) (Math.abs(System.nanoTime() / 7 + attempt) % NAMES.length)];
        String c = fillField("subdepartment.SubDepartmentcode", lastCode);
        String n = fillField("subdepartment.Subdepartment", lastSubDepartment);
        lastTimeSlot = fillField("subdepartment.timeslot", "15");
        return "Code=" + c + " | Sub Department=" + n + " | Time Slot=" + lastTimeSlot;
    }

    /** Select <b>Department</b> and <b>Default HOD</b>, fill <b>Code</b>/<b>Sub Department</b>/<b>Time Slot</b>. */
    public String fillAll(int attempt) {
        String dept = selectDepartment(0);
        String hod = selectDefaultHod(0);
        String rest = fillCodeAndName(attempt);
        return "Department=" + dept + " | Default HOD=" + hod + " | " + rest;
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. A successful
     *  Submit navigates straight back to the list ({@code #/subdepartmentMaster}). */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("subdepartment")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__sdToasts=[]; if(window.__sdObs) window.__sdObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sdToasts.includes(t)) window.__sdToasts.push(t); }); };"
                + " window.__sdObs=new MutationObserver(grab); window.__sdObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__sdSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__sdSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sdToasts||[]).includes(t)) (window.__sdToasts=window.__sdToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__sdToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__sdToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
