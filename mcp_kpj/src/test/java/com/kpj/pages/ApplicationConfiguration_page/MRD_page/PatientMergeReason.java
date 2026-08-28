package com.kpj.pages.ApplicationConfiguration_page.MRD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; MRD &gt; <b>Patient Merge Reason</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>MRD</b> (submenu) → <b>PatientMergeReason</b>
 * ({@code #/PatientMergeReason}) → enter <b>Code*</b> ({@code commonmaster.Code}) + <b>Remark*</b>
 * ({@code commonmaster.Description}) → <b>Submit</b> ({@code fnIUDCommonMaster()}) → success toast. INLINE-ADD —
 * no Add button; the form sits on the screen.</p>
 *
 * <p>This is the shared generic <b>commonmaster</b> form ({@code <form name="myCommonMasterForm"
 * ng-submit="fnIUDCommonMaster()">}) — the same screen shape as MRD's {@link FileType} and
 * {@link MRDDeficiencyCheckList}. It also carries a mandatory <b>Form Name*</b> select (which arrives
 * pre-selected — {@link #ensureFormName()} only intervenes if it is empty) plus optional MIMS GUID / Description /
 * Type, none of which the save needs.</p>
 *
 * <p>NB the <b>Form Name*</b> select carries <b>no ng-model</b>, so it is addressed by its label rather than by
 * model, and the Submit button has no {@code ng-click} — the save is the form's {@code ng-submit}.</p>
 */
public class PatientMergeReason extends BasePage {

    public PatientMergeReason(Page page) { super(page); }

    public static final String ROUTE = "#/PatientMergeReason";
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
            "Duplicate Registration", "Same Patient Different MRN", "Registration Error",
            "Name Spelling Variation", "NRIC Entered Incorrectly", "Merged On Patient Request"};

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
            // The menu label is run together as "PatientMergeReason" — accept that and the spaced spelling.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__pmMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*patient\\s*merge\\s*reason\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/PatientMergeReason'); if(!a) return ''; a.id='__pmMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("PatientMergeReason.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__pmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__pmMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__pmMenu'); if(e) e.removeAttribute('id'); }");
            try {
                // Generous: these MRD screens can sit under the previous screen for >10s before swapping in.
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Description')",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("PatientMergeReason.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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
     * On the REAL Patient Merge Reason screen — verified by this screen's own <b>Remark</b> field
     * ({@code commonmaster.Description}), NOT just the URL: the SPA can hold the right hash while leaving a
     * previous screen mounted, and that leftover markup carries a Code + Store commonmaster form which a Code-only
     * check would happily accept (the known wrong-page signature on this app).
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("patientmergereason")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Description')"));
    }

    /**
     * The mandatory <b>Form Name*</b> select arrives pre-selected; if it is somehow empty, choose its first real
     * option. It carries NO ng-model, so it is found by its label and driven through the DOM + a change event.
     */
    public String ensureFormName() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const s=[...document.querySelectorAll('select')].find(x=>x.offsetParent!==null && (x.getAttribute('ng-model')||'').indexOf('grid.')<0 && /form\\s*name/i.test(labelOf(x)));"
                + " if(!s) return '(no Form Name select)';"
                + " const cur=norm(s.options[s.selectedIndex] ? s.options[s.selectedIndex].textContent : '');"
                + " if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return 'already \"'+cur+'\"';"
                + " const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no option)';"
                + " s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(s).triggerHandler('change');}catch(x){} if($){try{$(s).trigger('change');}catch(x){}}"
                + " return 'set \"'+norm(s.options[i].textContent)+'\"'; }");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> ({@code commonmaster.Code}) and <b>Remark*</b> ({@code commonmaster.Description}). */
    public String fillDetails() {
        String code = "PM" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('commonmaster.Code', a.code); const rm=set('commonmaster.Description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Submit the inline commonmaster form ({@code fnIUDCommonMaster()}) and return the toast. The Submit button has
     * no {@code ng-click} — the save is the form's {@code ng-submit} — so the handler is read off the form and
     * invoked from its own scope, with Code/Remark re-applied to EVERY scope's {@code commonmaster} first (the
     * inline form re-renders on save and can blank the Code), and retried: the async save occasionally yields no
     * toast at all.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__pmToasts=[]; if(window.__pmObs) window.__pmObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pmToasts.includes(t)) window.__pmToasts.push(t); }); };"
                + " window.__pmObs=new MutationObserver(grab); window.__pmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" toast hides whether it 500'd
        // server-side. See devhis-commonmaster-iud-500 project memory: several sibling commonmaster screens
        // (Opthal Advice, Severity, Tint) hit the SHARED CommonMaster/IUD endpoint's 500 MappingException.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("commonmaster") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

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
                    + " const b=[...document.querySelectorAll('button[type=submit],input[type=submit],button')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__pmSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            System.out.println("PatientMergeReason submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__pmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__pmSubmit'); if(e) e.removeAttribute('id'); }");
            }
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__pmToasts||[]).some(a=>/merge|reason|master|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
            System.out.println("PatientMergeReason save HTTP => " + lastSaveHttp);
            Object t = page.evaluate("() => { const a=window.__pmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) { try { page.offResponse(onResp); } catch (Exception ignore) { } return last; }
            // "… already exists!" is NOT retryable with the SAME values — the old loop re-submitted the
            // identical Code/Remark and simply got the same rejection every attempt. Regenerate the
            // details (fillDetails() picks a fresh Code and a different description) and try again.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The realistic-name pool is SMALL and this environment already holds EVERY entry, so
                // picking another name collides again — that is why the retries kept failing. Append a
                // short number so the retry is genuinely unique, while still reading like configuration
                // ("Peanut 4821", not machine noise). The FIRST attempt keeps the clean name.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
            }
            page.evaluate("() => { window.__pmToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        return last;
    }

    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";
}
