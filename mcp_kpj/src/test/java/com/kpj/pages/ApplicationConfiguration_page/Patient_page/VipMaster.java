package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>VIP Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>VIP Master</b>
 * ({@code #/VIPMaster}) → <b>Add</b> ({@code AddVIPMaster()}) → fill the details → <b>Save</b>
 * ({@code fnSaveVIPMaster()}) → success toast.</p>
 *
 * <p><b>The Add button does NOT change the route</b> — it swaps the {@code VIPForm} in on the same
 * {@code #/VIPMaster} URL, so arrival at the form must be detected by a form field
 * ({@code commonmaster.code}), never by the URL.</p>
 *
 * <p>Fields (all on {@code commonmaster.*}): Code*, NRIC*, Passport*, Gender*, Marital Status, Religion,
 * Mobile No*, Name*, Designation, Expiry Date ({@code <input type="date">} → {@code yyyy-MM-dd}) and VIP Category.
 * The form's own <b>Add</b> ({@code addDependent()}) appends a dependent row and is not needed for the save.</p>
 *
 * <p>Menu note: the sibling <b>VIP Category</b> ({@code #/VIPCategory}) is a different screen — match the link
 * text exactly or the exact href.</p>
 */
public class VipMaster extends BasePage {

    public VipMaster(Page page) { super(page); }

    public static final String ROUTE = "#/VIPMaster";
    public String lastCode = "", lastName = "", lastNric = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;
    /** Screenshot of the VIP Family / Dependent fields WHILE THEY ARE STILL FILLED — clicking Add moves the values
     *  into the grid and blanks the entry form, so a later screenshot shows them empty. */
    public byte[] dependentPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*vip\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/VIPMaster'); if(!a) return ''; a.id='__vipMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("VipMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__vipMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("VipMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__vipMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVIPMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("VipMaster.nav: VIP Master screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL VIP Master screen — its own Add ({@code AddVIPMaster}) button or the VIP form is present. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("vipmaster")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVIPMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"
                + " || [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.code')"));
    }

    /** Real-click <b>Add</b> ({@code AddVIPMaster}). NB: the route does not change — confirm by the form field. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddVIPMaster\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__vipAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("VipMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__vipAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VipMaster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__vipAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("VipMaster.clickAdd: VIP form did not render"); return false; }
        waitForAngular(1000);
        return true;
    }

    // ---- form ------------------------------------------------------------

    /**
     * Fill EVERY field on the VIP form: Code*, Name*, NRIC*, Passport*, Gender*, Marital Status, Religion,
     * Mobile No*, Designation, Expiry Date, VIP Category, Organization, <b>State*</b>, <b>Post Code*</b>,
     * <b>Remark*</b>, <b>City*</b>, <b>Date Of Birth*</b> and <b>Address*</b>.
     *
     * <p>City ({@code commonmaster.City}, ~443 options) is populated off <b>State</b>, so State is set first and
     * the City list is polled afterwards.</p>
     */
    public String fillDetails() {
        String code = "VIP" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastName = new String[]{"Ahmad Faizal","Lim Wei Sheng","Siti Nurhaliza","Rajesh Kumar","Nurul Aina","Tan Chee Keong"}[(int)(Math.abs(System.nanoTime())%6)];
        // 12-digit NRIC, unique per run (the app rejects duplicates).
        lastNric = "8" + String.format("%011d", Math.abs(System.nanoTime() % 100000000000L));
        String expiry = java.time.LocalDate.now().plusYears(1).toString();          // input[type=date] → yyyy-MM-dd
        String mobile = "60" + String.format("%09d", Math.abs(System.nanoTime() % 1000000000L));
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<12;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const out=[];"
                + " out.push('Code='+set('commonmaster.code', a.code));"
                + " out.push('Name='+set('commonmaster.Name', a.name));"
                + " out.push('NRIC='+set('commonmaster.NRIC', a.nric));"
                + " out.push('Passport='+set('commonmaster.Passport', a.passport));"
                + " out.push('Gender='+(await pick('commonmaster.Gender')));"
                + " out.push('Marital='+(await pick('commonmaster.MaritalStatus')));"
                + " out.push('Religion='+(await pick('commonmaster.Religion')));"
                + " out.push('Mobile='+set('commonmaster.Mobile', a.mobile));"
                + " out.push('Designation='+set('commonmaster.Designation','Director'));"
                + " out.push('Expiry='+set('commonmaster.ExpiryDate', a.expiry));"
                + " out.push('VIPCategory='+(await pick('commonmaster.VIPCategoryId')));"
                + " out.push('Organization='+set('commonmaster.Organization','KPJ Healthcare Berhad'));"
                + " out.push('DOB='+set('commonmaster.DOB', a.dob));"
                + " out.push('Address='+set('commonmaster.Address','No 12, Jalan Test'));"
                + " out.push('PostCode='+set('commonmaster.PostCode','81000'));"
                + " out.push('Remark='+set('commonmaster.Remarks','Board member - priority admission'));"
                // State FIRST — the City list (~443 options) is RELOADED off it. Picking a city too early takes one
                // from the unfiltered list (e.g. Ajil, which is in Terengganu, not Johor) and the reload then drops
                // it back to --Select--. So: pick, wait, VERIFY it stuck, and retry if it reverted.
                + " out.push('State='+(await pick('commonmaster.State'))); await sleep(2000);"
                + " let city='(no)';"
                + " for(let attempt=0; attempt<6; attempt++){"
                + "   const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='commonmaster.City' && x.offsetParent!==null);"
                + "   if(!e){ await sleep(600); continue; }"
                + "   const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   if(idx<0){ await sleep(600); continue; }"
                + "   e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   await sleep(1500);"
                + "   const cur=e.options[e.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(norm(cur.textContent))){ city=norm(cur.textContent); break; } }"
                + " out.push('City='+city);"
                + " resolve(out.join(' | ')); })",
                java.util.Map.of("code", code, "name", lastName, "nric", lastNric,
                        "passport", "P" + code, "mobile", mobile, "expiry", expiry,
                        "dob", java.time.LocalDate.now().minusYears(40).toString()));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Rows currently in the VIP Family / Dependent grid. Counted from the SCOPE array first (the grid may render
     * as a ui-grid of divs rather than a &lt;table&gt;, so a table-only count reports 0 even after a successful Add),
     * falling back to ui-grid rows and then a Relationship-headed table.
     */
    public int dependentRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                // 1) scope array whose name looks like a dependent list
                + " let n=-1; document.querySelectorAll('*').forEach(el=>{ if(n>=0) return; try{ const s=window.angular.element(el).scope(); let x=s;"
                + "   for(let i=0;i<20&&x;i++){ const k=Object.keys(x).find(k=>/depend/i.test(k) && Array.isArray(x[k])); if(k){ n=x[k].length; return; } x=x.$parent; } }catch(e){} });"
                + " if(n>=0) return n;"
                // 2) ui-grid rows
                + " const g=[...document.querySelectorAll('[ui-grid],.ui-grid')].find(e=>e.offsetParent!==null && /relationship/i.test(e.innerText||''));"
                + " if(g) return [...g.querySelectorAll('.ui-grid-row')].filter(r=>norm(r.innerText)).length;"
                // 3) plain table with a Relationship header
                + " const t=[...document.querySelectorAll('table')].find(x=>/relationship/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.innerText) && !/no record/i.test(r.innerText)).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Fill the <b>VIP Family / Dependent Information</b> section ({@code dependent.*}: Name*, Relationship*,
     * Date Of Birth*, NRIC*, Passport*, Remark*) and click its <b>Add</b> ({@code addDependent()}) so the row
     * lands in the dependent grid. Returns the entered values + the resulting row count.
     */
    public String fillDependentAndAdd() {
        String dnric = "9" + String.format("%011d", Math.abs(System.nanoTime() % 100000000000L));
        int before = dependentRowCount();
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<12;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const out=[];"
                + " out.push('Name='+set('dependent.Name', a.name));"
                + " out.push('Relationship='+(await pick('dependent.Relationship')));"
                + " out.push('DOB='+set('dependent.DOB', a.dob));"
                + " out.push('NRIC='+set('dependent.NRIC', a.nric));"
                + " out.push('Passport='+set('dependent.Passport', a.passport));"
                // Neutral wording — Relationship takes the first real option, so don't name a specific relation here.
                + " out.push('Remark='+set('dependent.Remarks','Immediate family - VIP privileges apply'));"
                + " resolve(out.join(' | ')); })",
                java.util.Map.of("name", "Aishah binti Ahmad", "dob", java.time.LocalDate.now().minusYears(10).toString(),
                        "nric", dnric, "passport", "D" + lastCode));
        waitForAngular(600);
        // Capture the fields WHILE STILL FILLED — Add clears them, so evidence must be taken first.
        try { dependentPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("dependent screenshot: " + e.getMessage()); }
        // Commit the dependent row with its own Add (addDependent).
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].find(x=>/addDependent/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__vipAddDep'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("fillDependentAndAdd: addDependent button not found"); return (r == null ? "" : r.toString()) + " | (no Add button)"; }
        try { page.locator("#__vipAddDep").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("fillDependentAndAdd: Add click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vipAddDep'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return (r == null ? "" : r.toString()) + " | dependent rows " + before + " -> " + dependentRowCount();
    }

    /**
     * Click <b>Save</b> ({@code fnSaveVIPMaster}) and return the toast; screenshots it while still on screen. On
     * "… already exists!" (e.g. a duplicate NRIC or Code) all the main fields are regenerated via
     * {@link #fillDetails()} and Save is tried again (up to 3 attempts) — the dependent row already added is
     * left alone, since re-adding it is not needed to retry the save.
     */
    public String saveAndGetToast() { return saveAndGetToast(0); }

    private String saveAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__vipToasts=[]; if(window.__vipObs) window.__vipObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__vipToasts.includes(t)) window.__vipToasts.push(t); }); };"
                + " window.__vipObs=new MutationObserver(grab); window.__vipObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnSaveVIPMaster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__vipSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__vipSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vipSave'); if(e) e.removeAttribute('id'); }");
        // answer any "do you want to save" confirm
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|save|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__vipToasts||[]).some(a=>/vip|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__vipToasts||[]; return a.find(x=>/(vip|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("VipMaster.saveAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            // fillDetails() regenerates Code, NRIC, Mobile etc. off a fresh System.nanoTime() (so a Code/NRIC
            // collision self-resolves) AND re-writes every field into the DOM, including a freshly-picked Name.
            fillDetails();
            // The Name pool is small — append a short number so a repeat pick is still genuinely unique, while
            // still reading like a real name rather than machine noise.
            lastName = lastName + " " + (Math.abs(System.nanoTime()) % 10000);
            page.evaluate("(v) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"input[ng-model='commonmaster.Name'],textarea[ng-model='commonmaster.Name']\")].find(x=>x.offsetParent!==null);"
                    + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", lastName);
            waitForAngular(300);
            return saveAndGetToast(attempt + 1);
        }
        return toast;
    }
}
