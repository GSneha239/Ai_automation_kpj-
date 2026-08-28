package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Consent</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Consent</b> ({@code #/Consent}) →
 * <b>Add</b> ({@code AddConsent()} → {@code #/addConsent}) → enter <b>Code*</b> ({@code consent.code}),
 * <b>Template Name*</b> ({@code consent.description}) and <b>Consent Type*</b> ({@code consent.consenttypeid})
 * → <b>Submit</b> ({@code fnIUDConsent()}) → toast.</p>
 *
 * <p>The menu has three similar entries — <b>Consent</b> ({@code #/Consent}), <b>ConsentTXT</b>
 * ({@code #/ConsentTXT}) and <b>Consent Type</b> ({@code #/CONSNTTYP}) — so the link is matched on its EXACT
 * text, not a substring.</p>
 */
public class Consent extends BasePage {

    public Consent(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/Consent";
    public String lastCode = "", lastTemplateName = "", lastConsentType = "";

    /** Template names are real consent-form names, not generic filler text. */
    private static final String[] TEMPLATE_NAMES = {
            "Surgery Consent Form", "Anaesthesia Consent Form", "Blood Transfusion Consent",
            "Day Care Procedure Consent", "Admission Consent Form", "General Treatment Consent",
            "Radiology Procedure Consent", "Discharge Against Medical Advice Consent"
    };

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Patient (submenu) → Consent. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        // EXACT "Consent" — "ConsentTXT" and "Consent Type" are different screens.
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*consent\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/Consent'); if(!a) return ''; a.id='__cnMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__cnMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Consent.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("Consent.nav: menu link not found"); }
        if (!onScreen()) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(1000);
        return onScreen();
    }

    /** On the Consent list/add screen (and NOT on ConsentTXT / CONSNTTYP). */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        return (u.contains("#/consent") || u.contains("addconsent")) && !u.contains("consenttxt") && !u.contains("consnttyp");
    }

    /** Real-click <b>Add</b> ({@code AddConsent}) → {@code #/addConsent}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddConsent\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__cnAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Consent.clickAdd: Add button not found"); return false; }
        try { page.locator("#__cnAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Consent.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__cnAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='consent.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("Consent.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("addconsent");
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> ({@code consent.code}), <b>Template Name*</b> ({@code consent.description}) and
     * <b>Consent Type*</b> ({@code consent.consenttypeid} — first real option of --Select--/Admission/Operation/
     * Forms). The consent body (CKEditor {@code editor1} bound to {@code consent.template}) is filled too, since a
     * template with no text is rejected. {@code attempt} shifts both Code and Template Name so a retry after an
     * "already exists" toast submits genuinely different details. Returns a summary.
     */
    public String fillDetails(int attempt) {
        String code = "CN" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastTemplateName = TEMPLATE_NAMES[attempt % TEMPLATE_NAMES.length] + (attempt >= TEMPLATE_NAMES.length ? " " + (attempt / TEMPLATE_NAMES.length + 1) : "");
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const cd=set('consent.code', a.code); const tn=set('consent.description', a.name);"
                + " const ct=await pick('consent.consenttypeid');"
                + " let body='(no)'; const txt='<p>Automated consent template '+a.code+'.</p>';"
                + " if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances||{}); if(insts[0]){ try{ insts[0].setData(txt); body='CKEDITOR:'+insts[0].name; }catch(x){} } }"
                + " if(body==='(no)'){ const ta=document.querySelector(\"textarea[ng-model='consent.template']\"); if(ta){ const c=A.element(ta).controller('ngModel'); ta.value='Automated consent template '+a.code+'.'; if(c){c.$setViewValue(ta.value);c.$render();} ta.dispatchEvent(new Event('input',{bubbles:true})); body='textarea'; } }"
                + " resolve('Code='+cd+' | TemplateName='+tn+' | ConsentType='+ct+' | Body='+body); })",
                java.util.Map.of("code", code, "name", lastTemplateName));
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("ConsentType=");
        if (i >= 0) { int j = s.indexOf(" |", i); lastConsentType = s.substring(i + 12, j < 0 ? s.length() : j).trim(); }
        return s;
    }

    /** Click <b>Submit</b> ({@code fnIUDConsent}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__cnToasts=[]; if(window.__cnObs) window.__cnObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cnToasts.includes(t)) window.__cnToasts.push(t); }); };"
                + " window.__cnObs=new MutationObserver(grab); window.__cnObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDConsent/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__cnSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__cnSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|save|submit|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__cnToasts||[]).some(a=>/consent|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__cnToasts||[]).includes(t)) (window.__cnToasts=window.__cnToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cnToasts||[]; return a.find(x=>/(consent|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
