package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Cancel Admission Reason</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Cancel Admission Reason</b> → enter
 * Code + Remark → <b>Submit</b> → success toast. (Inline-add commonmaster — the master ng-model prefix is
 * auto-detected from the visible {@code *.Code} input, and the Submit handler by ng-click / text.)</p>
 */
public class CancelAdmissionReason extends BasePage {

    public CancelAdmissionReason(Page page) { super(page); }

    public String route = "", lastCode = "", lastRemark = "", prefix = "";

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/cancel\\s*admission\\s*reason/i.test(norm(x.textContent)) || /canceladmission/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__carMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__carMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("CancelAdmissionReason.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("CancelAdmissionReason.nav: menu link not found"); }
        waitForAngular(1200);
        return page.url().length() > 0;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("canceladmission"); }

    /** Detect the master ng-model prefix (the object with a {@code .Code} field). */
    public String detectPrefix() {
        Object p = page.evaluate("() => { const e=[...document.querySelectorAll('input')].find(x=>x.offsetParent!==null && /\\.Code$/i.test(x.getAttribute('ng-model')||'')); return e?(e.getAttribute('ng-model')||'').replace(/\\.Code$/i,''):''; }");
        prefix = p == null ? "" : p.toString();
        return prefix;
    }

    /** If the screen has an Add button (not inline-add), click it. Harmless if inline-add. */
    public void clickAddIfPresent() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddCancel|AddReason|AddAdmission|Add\\(/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1500);
    }

    /** Wait for the form, then enter Code + Remark (auto-detected prefix). */
    public String fillDetails() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && /\\.Code$/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillDetails: Code input not ready"); }
        detectPrefix();
        if (prefix.isEmpty()) return "(no Code field)";
        String code = "CAR" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = "Auto cancel admission reason " + code;
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(a.p+'.Code',a.code); let rm='(n/a)'; ['.Remark','.Description','.remark','.description'].forEach(f=>{ const e=document.querySelector(\"[ng-model='\"+a.p+f+\"']\"); if(e && rm==='(n/a)') rm=set(a.p+f,a.remark); }); return 'prefix='+a.p+' | Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("p", prefix, "code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Submit the generic commonmaster form ({@code ng-submit="fnIUDCommonMaster()"}). Commits Code/Remark onto EVERY
     *  scope's {@code commonmaster} object (the handler may read a parent scope's copy), invokes the handler, returns
     *  the toast. */
    public String submitAndGetToast() {
        // Persistent toast observer.
        page.evaluate("() => { window.__carToasts=[]; if(window.__carObs) window.__carObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__carToasts.includes(t)) window.__carToasts.push(t); }); };"
                + " window.__carObs=new MutationObserver(grab); window.__carObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String remark = lastRemark.isEmpty() ? "Auto cancel admission reason" : lastRemark;
        String last = "";
        // The async save is flaky (sometimes no toast) and the inline form re-render blanks Code — retry re-fill+invoke.
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object res = page.evaluate("(a) => { const A=window.angular; const code=a.code, desc=a.remark;"
                    + " const setInp=ng=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); const v=ng.endsWith('.Code')?code:desc; e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; setInp('commonmaster.Code'); setInp('commonmaster.Description');"
                    + " const seen=new Set(); let handlerScope=null, handlerName=null;"
                    + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit')); if(f){ const ng=f.getAttribute('ng-submit')||''; const m=ng.match(/([A-Za-z_$][\\w$]*)\\s*\\(/); handlerName=m?m[1]:null; }"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(s.commonmaster && typeof s.commonmaster==='object'){ if(code) s.commonmaster.Code=code; if(desc) s.commonmaster.Description=desc; } if(handlerName && typeof s[handlerName]==='function' && !handlerScope) handlerScope=s; }catch(e){} });"
                    + " if(handlerScope && handlerName){ try{ handlerScope.$apply(function(){ handlerScope[handlerName](); }); }catch(e){ try{ handlerScope[handlerName](); }catch(e2){} } return 'invoked:'+handlerName+'(Code='+code+')'; }"
                    + " const b=[...document.querySelectorAll('button[type=submit],input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__carSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", remark));
            System.out.println("CancelAdmissionReason submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__carSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            }
            // Wait up to ~9s for ANY toast from this attempt.
            try {
                page.waitForFunction("() => (window.__carToasts||[]).some(a=>/cancel|reason|admission|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            Object t = page.evaluate("() => { const a=window.__carToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            System.out.println("CancelAdmissionReason toasts[" + attempt + "] => " + page.evaluate("() => JSON.stringify(window.__carToasts||[])"));
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;   // success → done
            // clear toasts before retrying so a stale "Please Enter" doesn't linger
            page.evaluate("() => { window.__carToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
