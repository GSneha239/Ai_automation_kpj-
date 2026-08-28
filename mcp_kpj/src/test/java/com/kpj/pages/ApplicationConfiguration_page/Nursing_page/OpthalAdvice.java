package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Opthal Advice</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu, EXACT text — "Nursing Station" is a
 * different module and a prefix match hits it first) → <b>Opthal Advice</b> (route {@code #/Opthaladvice} —
 * confirmed live per an earlier menu-search note: a loose {@code /advice/i} search finds only this screen,
 * not "Advise Master" which is spelled "Advise" in the app) → (Add, if the screen has one) → enter
 * <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded — same reusable shape as
 * {@code Investigation_page.Agency} / {@code Nursing_page.Diagnosis} (this screen's exact ng-model names were
 * not yet confirmed live; sibling masters can live at routes that do not literally match their menu label, so
 * {@link #onScreen()} verifies primarily by field presence + URL, not header text alone). Grid filter boxes
 * ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 */
public class OpthalAdvice extends BasePage {

    public OpthalAdvice(Page page) { super(page); }

    /** Confirmed live per an earlier menu-search note (see class javadoc). */
    public static final String ROUTE = "#/Opthaladvice";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic ophthalmic-advice names, not "Auto ... <code>" filler. */
    private static final String[] REMARKS = {
            "Avoid Rubbing Eyes", "Use Prescribed Eye Drops as Directed", "Wear Protective Eyewear",
            "Avoid Contact Lens Use", "Keep Eye Area Clean", "Apply Cold Compress if Swollen",
            "Avoid Bright Light Exposure", "Complete Full Course of Antibiotic Drops",
            "Report Increased Redness or Pain Immediately", "Avoid Swimming Until Healed",
            "Rest Eyes Periodically During Screen Use", "Follow Up in One Week"
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
                // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1200);
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a[href]')].filter(a=>a.offsetParent!==null).length > 3",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                } catch (Exception ignore) { System.out.println("OpthalAdvice.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // Accept "Opthal Advice" / "Ophthal Advice" / the href #/Opthaladvice directly — spelling varies.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*op(h)?thal\\s*advi(c|s)e\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                        + "        || links.find(x=>/opthaladvice/i.test(x.getAttribute('href')||'') && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__oaMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("OpthalAdvice.nav DEBUG: visible links => " + dbg);
                    System.out.println("OpthalAdvice.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__oaMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("OpthalAdvice.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__oaMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__oaMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("OpthalAdvice.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("OpthalAdvice.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Opthal Advice screen — verified by a Code/Remark box actually present plus the URL (the app's
     * CommonMaster-family header text can show the pre-selected Form Name rather than the menu label, per the
     * {@code Patient_page.Payor} / {@code Patient_page.CampType} lesson — so field presence + URL are the
     * primary signal, with the header as a secondary hint only).
     */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/opthaladvice/i.test((location.hash||'').replace(/\\s+/g,''));" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " if(/op(h)?thal\\s*advi(c|s)e/i.test(hdr)) return true;"
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
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__oaAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__oaAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("OpthalAdvice.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__oaAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details; once the realistic-name pool is exhausted a
     * short number is appended so the retry is still guaranteed unique.
     */
    public String fillDetails(int attempt) {
        String code = "OA" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
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
        page.evaluate("() => { window.__oaToasts=[]; if(window.__oaObs) window.__oaObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__oaToasts.includes(t)) window.__oaToasts.push(t); }); };"
                + " window.__oaObs=new MutationObserver(grab); window.__oaObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("opthal") || resp.status() >= 400)) return;
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
                + " if(b){ b.id='__oaSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("OpthalAdvice submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__oaSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("OpthalAdvice submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__oaSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__oaToasts||[]).some(a=>/opthal|advice|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("OpthalAdvice save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__oaToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
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
