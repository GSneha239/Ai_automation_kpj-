package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Camp Details</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Camp Details</b>
 * ({@code #/CampDetails}) → <b>Add</b> ({@code AddCamp()} → {@code #/add-camp}) → fill the <b>Camp Details</b> tab
 * (Camp Type*, Code*, Remark*, From/To Date, Valid Days, Pricing Policy*, Reason, City, Area) → the
 * <b>Camp Service</b> tab ({@code #CampServiceTab}) → pick a Service via the Service Name autocomplete so a row
 * lands in the "Service Code · Service Name · Remove" grid → <b>Save</b> ({@code fnIUDCampDetails()}) → toast.</p>
 */
public class CampDetails extends BasePage {

    public CampDetails(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/CampDetails";
    public String lastCode = "", lastService = "", lastRemark = "";

    /** Camp remarks are real camp/outreach names, not generic filler text. */
    private static final String[] REMARKS = {
            "Community Blood Donation Camp", "Free Health Screening Camp", "Diabetes Awareness Camp",
            "Corporate Wellness Camp", "Rural Outreach Medical Camp", "Senior Citizens Health Camp",
            "Eye Screening Camp", "Vaccination Drive Camp"
    };

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Patient (submenu) → Camp Details. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/camp\\s*details/i.test(norm(x.textContent)) || /campdetails/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__campMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__campMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("CampDetails.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("CampDetails.nav: menu link not found"); }
        if (!onScreen()) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("campdetails"); }

    /** Real-click <b>Add</b> ({@code AddCamp}) → {@code #/add-camp}. The button renders after the ui-grid, so POLL
     *  for it — clicking too early silently leaves you on the list. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddCamp\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__campAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("CampDetails.clickAdd: Add button not found"); return false; }
        try { page.locator("#__campAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CampDetails.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__campAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CampDetails.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CampDetails.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-camp");
    }

    // ---- camp details tab ------------------------------------------------

    /**
     * Fill the Camp Details tab: Camp Type*, Code*, Remark*, From/To Date, Valid Days, Pricing Policy*, Reason,
     * City, Area. {@code attempt} shifts BOTH Code and Remark so a retry after an "already exists" toast submits
     * genuinely different details. Returns a summary.
     */
    public String fillCampDetails(int attempt) {
        String code = "CMP" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        String from = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String to = java.time.LocalDate.now().plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const type=await pick('CampDetails.CampTypeID');"
                + " const cd=setInp('CampDetails.Code', a.code); const rm=setInp('CampDetails.Description', a.remark);"
                + " const fd=setInp('CampDetails.FromDate', a.from); const td=setInp('CampDetails.ToDate', a.to); const vd=setInp('CampDetails.ValidDays','30');"
                + " const tar=await pick('CampDetails.TariffID');"
                + " const rs=setInp('CampDetails.Reason','Automated camp'); const ct=setInp('CampDetails.City','Petaling Jaya'); const ar=setInp('CampDetails.Area','Damansara');"
                + " resolve('CampType='+type+' | Code='+cd+' | Remark='+rm+' | From='+fd+' | To='+td+' | ValidDays='+vd+' | PricingPolicy='+tar+' | Reason='+rs+' | City='+ct+' | Area='+ar); })",
                java.util.Map.of("code", code, "remark", lastRemark, "from", from, "to", to));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Switch back to the <b>Camp Details</b> tab — needed before a retry, since {@link #fillCampDetails(int)} only
     * finds inputs that are actually visible ({@code offsetParent!==null}), which requires this tab to be active.
     */
    public boolean openCampDetailsTab() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('data-target')||'')==='#CampDetailsTab')"
                + "   || [...document.querySelectorAll('a')].find(x=>/^\\s*camp\\s*details\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return false; a.id='__campDetTab'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("openCampDetailsTab: tab not found"); return false; }
        try { page.locator("#__campDetTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("openCampDetailsTab: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll(\"input[ng-model='CampDetails.Code']\")].some(e=>e.offsetParent!==null)"));
    }

    // ---- camp service tab ------------------------------------------------

    /** Open the <b>Camp Service</b> tab ({@code data-target="#CampServiceTab"}) — the Service Name input is hidden
     *  until it is selected. */
    public boolean openCampServiceTab() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('data-target')||'')==='#CampServiceTab')"
                + "   || [...document.querySelectorAll('a')].find(x=>/^\\s*camp\\s*service\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return false; a.id='__campSvcTab'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("openCampServiceTab: tab not found"); return false; }
        try { page.locator("#__campSvcTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("openCampServiceTab: click failed - " + e.getMessage()); }
        waitForAngular(1500);
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll(\"input[ng-model='ServiceData.ServiceName']\")].some(e=>e.offsetParent!==null)"));
    }

    /** Rows currently in the Camp Service grid ("Service Code · Service Name · Remove"). */
    public int serviceRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/service code/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.innerText) && !/no record/i.test(r.innerText)).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Camp Service — type a value into <b>Service Name</b> and press <b>Enter</b>. Returns what was typed, what the
     * field holds afterwards, and the resulting grid row count.
     */
    public String enterServiceNameAndPressEnter(String value) {
        Object tag = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='ServiceData.ServiceName']\")].find(x=>x.offsetParent!==null); if(!e) return false; e.id='__campSvc'; return true; }");
        if (!Boolean.TRUE.equals(tag)) { System.out.println("enterServiceName: no visible Service Name input"); return "(no service input)"; }
        int before = serviceRowCount();
        try {
            com.microsoft.playwright.Locator svc = page.locator("#__campSvc");
            svc.scrollIntoViewIfNeeded();
            svc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            svc.fill("");
            svc.pressSequentially(value, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(200));
            waitForAngular(1200);
            svc.press("Enter");
        } catch (Exception e) { System.out.println("enterServiceName: typing failed - " + e.getMessage()); return "(typing failed)"; }
        waitForAngular(2000);
        lastService = value;
        int after = serviceRowCount();
        Object typed = page.evaluate("() => { const e=document.getElementById('__campSvc'); return e?e.value:''; }");
        return "Typed \"" + value + "\" + Enter | field now=\"" + typed + "\" | grid rows " + before + " -> " + after;
    }

    // ---- save ------------------------------------------------------------

    /** Click <b>Save</b> ({@code fnIUDCampDetails}) and return the toast (toastr cleared first). */
    public String saveAndGetToast() {
        Object tagged = page.evaluate("() => { window.__cmpToasts=[]; if(window.__cmpObs) window.__cmpObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cmpToasts.includes(t)) window.__cmpToasts.push(t); }); };"
                + " window.__cmpObs=new MutationObserver(grab); window.__cmpObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCampDetails/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__campSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__campSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveAndGetToast: Save click failed - " + e.getMessage()); }
        // answer any "do you want to save" confirm
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|save|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__cmpToasts||[]).some(a=>/camp|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__cmpToasts||[]).includes(t)) (window.__cmpToasts=window.__cmpToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cmpToasts||[]; return a.find(x=>/(camp|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
