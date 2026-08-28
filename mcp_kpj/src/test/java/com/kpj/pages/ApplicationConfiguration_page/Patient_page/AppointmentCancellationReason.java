package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Appointment Cancellation Reason</b> — configuration screen
 * Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Appointment Cancellation Reason</b>
 * (route {@code #/AppointmentCancellationReason}, best-guess fallback — navigation is primarily by the live
 * menu link) → (Add, if the screen has one) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success
 * toast.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded — same reusable shape as
 * {@code Investigation_page.Agency} / {@code Patient_page.Religion} / {@code Patient_page.Triage} (this
 * screen's exact ng-model names were not yet confirmed live; sibling masters can live at routes that do not
 * literally match their menu label, so {@link #onScreen()} verifies primarily by field presence + URL, not
 * header text alone — the CommonMaster-family header can show the pre-selected Form Name rather than the menu
 * label, per the {@code Patient_page.Payor} / {@code Patient_page.CampType} lesson). Grid filter boxes
 * ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 */
public class AppointmentCancellationReason extends BasePage {

    public AppointmentCancellationReason(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/AppointmentCancellationReason";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    private String menuHref = "";
    public byte[] toastPng;

    /**
     * Remarks are real appointment-cancellation reasons, not generic filler text.
     */
    private static final String[] REMARKS = {
            "Patient Request", "Doctor Unavailable", "Rescheduled", "Duplicate Booking",
            "Patient No-Show", "Emergency", "Weather / Travel Issue", "Insurance Issue",
            "Patient Admitted", "Wrong Department Selected"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** JS helper: the visible Code / Remark inputs, whatever they are called on this screen. */
    private static final String FIND_FIELDS =
            " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && !skip(e.getAttribute('ng-model')));"
            + " const byModel=re=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
            + " const byPh=re=>boxes.find(e=>re.test(e.placeholder||''));"
            + " const codeEl = byModel(/\\.code(id)?$/i) || byModel(/code/i) || byPh(/^\\s*code\\s*$/i);"
            + " const remEl  = byModel(/\\.(description|remark)$/i) || byModel(/description|remark/i) || byPh(/^\\s*(remark|description)\\s*$/i);";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Patient" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1200);
                // The submenu can take a moment to actually render its child links after the toggle click —
                // poll for ANY visible link before trusting the "not found" result.
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a[href]')].filter(a=>a.offsetParent!==null).length > 3",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                } catch (Exception ignore) { System.out.println("AppointmentCancellationReason.nav: Patient submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // EXACT text match on a VISIBLE link ONLY (learned from the Payor mix-up: an unfiltered/loose
                // match can pick up a DOM-present-but-hidden leftover link from a different submenu). Accepts
                // either the full "Appointment Cancellation Reason" label or the shorter "Cancellation Reason".
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*appointment\\s*cancellation\\s*reason\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                        + "        || links.find(x=>/^\\s*cancellation\\s*reason\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__acrMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("AppointmentCancellationReason.nav DEBUG: visible links => " + dbg);
                    System.out.println("AppointmentCancellationReason.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__acrMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("AppointmentCancellationReason.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__acrMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__acrMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("AppointmentCancellationReason.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("AppointmentCancellationReason.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Appointment Cancellation Reason screen — verified by a Code/Remark box actually present plus the
     * URL (the app's CommonMaster-family header text can show the pre-selected Form Name rather than the menu
     * label, per the {@code Patient_page.Payor} / {@code Patient_page.CampType} lesson — so field presence +
     * URL are the primary signal, with the header as a secondary hint only).
     */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/cancellationreason/i.test((location.hash||'').replace(/\\s+/g,''));" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " if(/cancellation\\s*reason/i.test(hdr)) return true;"
                + " return urlOk && [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        // Already showing both boxes -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__acrAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__acrAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AppointmentCancellationReason.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__acrAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "AC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res;
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Code/Remark first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit handler
     * from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__acrToasts=[]; if(window.__acrObs) window.__acrObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__acrToasts.includes(t)) window.__acrToasts.push(t); }); };"
                + " window.__acrObs=new MutationObserver(grab); window.__acrObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("cancellationreason") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(remEl, a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__acrSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("AppointmentCancellationReason submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__acrSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AppointmentCancellationReason submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__acrSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__acrToasts||[]).some(a=>/cancellation|reason|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("AppointmentCancellationReason save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__acrToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels.
     * Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
