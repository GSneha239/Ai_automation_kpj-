package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Occupation</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Occupation</b> ({@code #/OCCUPTN}) →
 * enter <b>Code*</b> + <b>Remark</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>INLINE-ADD</b> screen — there is no Add button; the generic commonmaster form
 * ({@code <form name="myCommonMasterForm" ng-submit="fnIUDCommonMaster()">}) is on the screen itself, bound to
 * {@code commonmaster.Code} / {@code commonmaster.Description}. Same shape as
 * {@link CancelAdmissionReason}, including its re-render quirk: the inline form can blank the Code between the
 * fill and the submit, so the values are re-applied to EVERY scope's {@code commonmaster} and the handler invoked
 * from the scope, with retries.</p>
 */
public class Occupation extends BasePage {

    public Occupation(Page page) { super(page); }

    public static final String ROUTE = "#/OCCUPTN";
    public String lastCode = "", lastRemark = "";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration → Patient → <b>Occupation</b>, clicking the MENU LINK (retried).
     *
     * <p>Setting {@code window.location.hash = '#/OCCUPTN'} is NOT enough: the hash changes but the SPA does not
     * route, leaving the previous screen's markup on the page (header still reads "PATIENT DASHBOARD" while the
     * URL says {@code #/OCCUPTN}) — and that leftover markup happens to contain a Code + Store commonmaster form,
     * so a naive "is commonmaster.Code present?" check passes on the WRONG screen. Verify the real Occupation
     * screen instead: its header reads OCCUPATION and it has a <b>Remark</b> field.</p>
     */
    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1000);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1000);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*occupation\\s*$/i.test(norm(x.textContent)) || /occuptn/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ocMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("Occupation.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__ocMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Occupation.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ocMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                        + " if(/occupation/i.test(hdr)) return true;"
                        + " const labelled=[...document.querySelectorAll('input,textarea')].some(e=>{ if(e.offsetParent===null) return false;"
                        + "   let n=e.closest('.form-group,.row,.col-md-3,.col-md-4,.col-md-6,td,div'); for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && /remark/i.test(l.textContent||'')) return true; n=n.parentElement; } return false; });"
                        + " return labelled; }", null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("Occupation.nav: Occupation screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — the screen header plus the URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL Occupation screen: right route AND the screen's own Remark field is present. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("occuptn")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " if(/occupation/i.test(hdr)) return true;"
                + " return [...document.querySelectorAll('input,textarea')].some(e=>{ if(e.offsetParent===null) return false;"
                + "   let n=e.closest('.form-group,.row,.col-md-3,.col-md-4,.col-md-6,td,div'); for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && /remark/i.test(l.textContent||'')) return true; n=n.parentElement; } return false; }); }"));
    }

    // ---- form ------------------------------------------------------------

    /** Realistic occupation names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] OCCUPATIONS = {
            "Software Engineer", "Registered Nurse", "School Teacher", "Civil Engineer", "Accountant",
            "Police Officer", "Retail Manager", "Electrician", "Marketing Executive", "Bus Driver",
            "Chef", "Pharmacist"
    };

    /** Enter <b>Code*</b> ({@code commonmaster.Code}) and <b>Remark</b> ({@code commonmaster.Description}). */
    public String fillDetails() {
        String code = "OC" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = OCCUPATIONS[(int) (Math.abs(System.nanoTime()) % OCCUPATIONS.length)];
        // Locate Code and Remark by their LABELS — the ng-model names are not guessable from the sibling screens
        // and a wrong guess silently writes nothing.
        Object r = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-3,.col-md-4,.col-md-6,td,div'); for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const fill=(e,v)=>{ const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " const inputs=[...document.querySelectorAll('input[type=text],textarea')].filter(e=>e.offsetParent!==null);"
                + " const byLabel=re=>inputs.find(e=>re.test(labelOf(e)));"
                + " const codeEl=byLabel(/^code/i); const remEl=byLabel(/remark/i);"
                + " let cd='(no)', rm='(no)';"
                + " if(codeEl){ fill(codeEl, a.code); cd=a.code+' [ng='+(codeEl.getAttribute('ng-model')||'?')+']'; }"
                + " if(remEl){ fill(remEl, a.remark); rm=a.remark+' [ng='+(remEl.getAttribute('ng-model')||'?')+']'; }"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Submit the inline commonmaster form ({@code ng-submit="fnIUDCommonMaster()"}) and return the toast.
     * Re-applies Code/Remark to EVERY scope's {@code commonmaster} before invoking the handler (the inline form
     * re-renders and can blank the Code), and retries — the async save occasionally yields no toast at all.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ocToasts=[]; if(window.__ocObs) window.__ocObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ocToasts.includes(t)) window.__ocToasts.push(t); }); };"
                + " window.__ocObs=new MutationObserver(grab); window.__ocObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object res = page.evaluate("(a) => { const A=window.angular; const code=a.code, desc=a.remark;"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " setInp('commonmaster.Code', code); setInp('commonmaster.Description', desc);"
                    + " let handlerName=null; const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                    + " if(f){ const m=(f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/); handlerName=m?m[1]:null; }"
                    + " const seen=new Set(); let handlerScope=null;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                    + "   if(s.commonmaster && typeof s.commonmaster==='object'){ if(code) s.commonmaster.Code=code; if(desc) s.commonmaster.Description=desc; }"
                    + "   if(handlerName && typeof s[handlerName]==='function' && !handlerScope) handlerScope=s; }catch(e){} });"
                    + " if(handlerScope && handlerName){ try{ handlerScope.$apply(function(){ handlerScope[handlerName](); }); }catch(e){ try{ handlerScope[handlerName](); }catch(e2){} } return 'invoked:'+handlerName; }"
                    + " const b=[...document.querySelectorAll('button[type=submit],input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__ocSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            System.out.println("Occupation submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__ocSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__ocSubmit'); if(e) e.removeAttribute('id'); }");
            }
            try {
                page.waitForFunction("() => (window.__ocToasts||[]).some(a=>/occupation|master|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            Object t = page.evaluate("() => { const a=window.__ocToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "… already exists!" is NOT retryable with the SAME values — regenerate a fresh Code and a
            // different, still-realistic occupation name (fillDetails() also re-applies them below on the
            // next loop iteration via lastCode/lastRemark) and try again.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("Occupation.submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The occupation pool is small, so an already fully-populated environment can still collide —
                // append a short number so the retry is genuinely unique, while still reading like real data.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
            }
            page.evaluate("() => { window.__ocToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
