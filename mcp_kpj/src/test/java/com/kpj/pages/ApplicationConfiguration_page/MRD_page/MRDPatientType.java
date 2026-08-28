package com.kpj.pages.ApplicationConfiguration_page.MRD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; MRD &gt; <b>MRD Patient Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>MRD</b> (submenu) → <b>MRD Patient Type</b>
 * ({@code #/MRDPatientType}) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast. INLINE-ADD —
 * no Add button; the form sits on the screen.</p>
 *
 * <p>These MRD screens can render <b>well after the route settles</b> (Box Master sat under the previous screen's
 * "Transfer" header for seconds before appearing), so navigation waits on this screen's own <b>Remark</b> field —
 * generously, and across three attempts — before concluding anything.</p>
 *
 * <p><b>Wrong-page guard:</b> if what finally renders is the leftover generic {@code myCommonMasterForm} carrying
 * <b>Code* + Store*</b> ({@code commonmaster.Code} + {@code StoreId}) with no Remark, that is the app's known
 * wrong-page signature — {@link #onScreen()} rejects it rather than trusting the URL. NB the sibling MRD screens
 * {@link FileType} and {@link MRDDeficiencyCheckList} render the SAME commonmaster form correctly (Code + Remark
 * + Form Name), so a failure here is this route not mounting its view, not commonmaster being broken.</p>
 */
public class MRDPatientType extends BasePage {

    public MRDPatientType(Page page) { super(page); }

    public static final String ROUTE = "#/MRDPatientType";
    public String lastCode = "", lastRemark = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] REMARKS = {
            "Inpatient Record", "Outpatient Record", "Day Care Record",
            "Emergency Record", "Maternity Record", "Referral Record"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const flds=()=>[...document.querySelectorAll('input[type=text],input:not([type]),textarea')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0);"
          + " const byLabel=re=>flds().find(e=>re.test(labelOf(e)));";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "MRD" — matched on the whole label so it can't hit another submenu that merely contains it.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*mrd\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__ptMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*mrd\\s*patient\\s*type\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/MRDPatientType'); if(!a) return ''; a.id='__ptMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("MRDPatientType.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__ptMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__ptMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__ptMenu'); if(e) e.removeAttribute('id'); }");
            try {
                // Generous: this family has been seen taking >10s to swap the previous screen out.
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("MRDPatientType.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * On the REAL MRD Patient Type screen — verified by its own <b>Remark</b> field, NOT the URL (see the class
     * note on the leftover Code + Store commonmaster form).
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("mrdpatienttype")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }"));
    }

    /** The wrong-page signature: a generic commonmaster <b>Code* + Store*</b> form and no Remark. */
    public boolean showsCodeStoreLeftover() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL
                + " const hasStore=[...document.querySelectorAll('select')].some(s=>s.offsetParent!==null && (s.getAttribute('ng-model')||'')==='StoreId');"
                + " return hasStore && !byLabel(/remark/i); }"));
    }

    /** Every visible form field with its label + ng-model — evidence of what the app actually served. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('grid.')<0).map(e=>'SELECT '+norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const forms=[...document.querySelectorAll('form[name]')].map(x=>x.getAttribute('name')+' ng-submit='+(x.getAttribute('ng-submit')||'-'));"
                + " return 'forms: '+forms.join(' ; ')+' || fields: '+f.concat(sels).join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed). */
    public String fillDetails() {
        String code = "PT" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code); const rm=fill(byLabel(/remark/i), a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__mptToasts=[]; if(window.__mptObs) window.__mptObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mptToasts.includes(t)) window.__mptToasts.push(t); }); };"
                + " window.__mptObs=new MutationObserver(grab); window.__mptObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
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
                    + " const b=[...document.querySelectorAll('button[type=submit],input[type=submit],button')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__mptSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            System.out.println("MRDPatientType submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__mptSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__mptSubmit'); if(e) e.removeAttribute('id'); }");
            }
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__mptToasts||[]).some(a=>/patient|type|master|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__mptToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "… already exists!" is NOT retryable with the SAME values — a blind retry would just resubmit the
            // identical Code/Remark and get the same rejection every attempt. Regenerate the details (a fresh
            // Code plus a different Remark) and push them into the DOM before retrying.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The realistic-name pool is SMALL and can already be exhausted in this environment, so picking
                // another name can still collide — append a short number so the retry is genuinely unique, while
                // still reading like configuration ("Peanut 4821", not machine noise). The FIRST attempt keeps
                // the clean name; fillDetails() already wrote the un-suffixed value, so re-push the suffixed one.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
                page.evaluate("(v) => { const A=window.angular; " + BY_LABEL
                        + " const e=byLabel(/remark/i); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                        lastRemark);
            }
            page.evaluate("() => { window.__mptToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
