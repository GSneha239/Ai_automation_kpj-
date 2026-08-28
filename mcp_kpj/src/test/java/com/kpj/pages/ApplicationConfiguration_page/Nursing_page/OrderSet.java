package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Order Set</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu, EXACT text — "Nursing Station" is a
 * different module and a prefix match hits it first) → <b>Order Set</b> (route {@code #/OrderSet}, best-guess
 * fallback — navigation is primarily by the live menu link) → (Add, if the screen has one) → enter
 * <b>Code*</b> + <b>Remark*</b> + a <b>Diagnosis</b> (typeahead — same commit mechanism as the sibling
 * {@code Nursing_page.DiagnosisSet}: type digits, then click a suggestion or ArrowDown+Enter) →
 * <b>Submit</b> → success toast.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded (this screen's exact ng-model
 * names were not yet confirmed live). Grid filter boxes ({@code colFilter.term}) and the textAngular artefact
 * are excluded.</p>
 */
public class OrderSet extends BasePage {

    public OrderSet(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/OrderSet";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    public String lastDiagnosis = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic order-set names, not "Auto ... <code>" filler. */
    private static final String[] REMARKS = {
            "Post-Operative Care Order Set", "Sepsis Management Order Set", "Diabetic Ketoacidosis Order Set",
            "Community Acquired Pneumonia Order Set", "Acute Stroke Order Set", "Chest Pain Order Set",
            "Pre-Operative Order Set", "Asthma Exacerbation Order Set", "Acute Kidney Injury Order Set",
            "Congestive Heart Failure Order Set", "Labour and Delivery Order Set", "Paediatric Fever Order Set"
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

    /** JS helper: label-based lookup — walks up to 5 ancestor levels looking for a &lt;label&gt;. */
    private static final String LABEL_NEAR_JS =
            " const labelNear=e=>{ let t='', p=e.parentElement, hop=0; while(p && hop++<5 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };";

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
                } catch (Exception ignore) { System.out.println("OrderSet.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*order\\s*set\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                        + "        || links.find(x=>/ordersets?/i.test(x.getAttribute('href')||'') && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__osMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("OrderSet.nav DEBUG: visible links => " + dbg);
                    System.out.println("OrderSet.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__osMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("OrderSet.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__osMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__osMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("OrderSet.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("OrderSet.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Order Set screen — verified by a Code/Remark box actually present plus the URL. */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/orderset/i.test((location.hash||'').replace(/\\s+/g,''));" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " if(/order\\s*set/i.test(hdr)) return true;"
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
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__osAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__osAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("OrderSet.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__osAdd'); if(e) e.removeAttribute('id'); }");
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
        String code = "OS" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
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
     * Type digits into a <b>Diagnosis</b> typeahead (found by ng-model/label containing "diagnosis") and pick a
     * suggestion — same commit mechanism as the sibling {@code DiagnosisSet.pickDiagnosisCode()}: click a
     * suggestion row if one renders, else ArrowDown+Enter. Tries several digit prefixes. Reports if no
     * Diagnosis control exists on this screen at all (not every screen needs one).
     */
    public String pickDiagnosis() {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + LABEL_NEAR_JS
                + " const e=[...document.querySelectorAll('input')].find(x=>x.offsetParent!==null && x.type!=='file'"
                + "   && (/diagnos/i.test(x.getAttribute('ng-model')||'') || /diagnos/i.test(labelNear(x)) || /diagnos/i.test(x.placeholder||'')));"
                + " if(!e) return false; e.id='__osDiagField'; return true; }");
        if (!Boolean.TRUE.equals(found)) return "(no Diagnosis control found)";

        String[] prefixes = {"10", "11", "12", "20", "21", "30", "40", "50"};
        for (String prefix : prefixes) {
            try {
                page.locator("#__osDiagField").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
                page.locator("#__osDiagField").fill("");
                page.locator("#__osDiagField").type(prefix, new com.microsoft.playwright.Locator.TypeOptions().setDelay(180));
            } catch (Exception e) { System.out.println("pickDiagnosis: typing failed - " + e.getMessage()); continue; }
            waitForAngular(400);

            String picked = "";
            for (int i = 0; i < 16 && picked.isEmpty(); i++) {
                Object tag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const sels=['.dropdown-menu li','li.uib-typeahead-match','.ui-select-choices-row','.select2-results li',"
                        + "  '.tt-suggestion','.autocomplete-suggestion','ul.typeahead li','.angucomplete-row','.ui-menu-item'];"
                        + " for(const s of sels){ const el=[...document.querySelectorAll(s)].find(x=>x.offsetParent!==null && norm(x.textContent)); "
                        + "   if(el){ el.id='__osSug'; return norm(el.textContent).slice(0,80); } }"
                        + " return ''; }");
                if (tag != null && !tag.toString().isEmpty()) {
                    try { page.locator("#__osSug").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); picked = tag.toString(); }
                    catch (Exception e) { System.out.println("pickDiagnosis: suggestion click failed - " + e.getMessage()); }
                    page.evaluate("() => { const e=document.getElementById('__osSug'); if(e) e.removeAttribute('id'); }");
                }
                if (picked.isEmpty()) page.waitForTimeout(400);
            }
            if (picked.isEmpty()) {
                try {
                    page.locator("#__osDiagField").press("ArrowDown");
                    page.waitForTimeout(400);
                    page.locator("#__osDiagField").press("Enter");
                    picked = "(via keyboard)";
                } catch (Exception ignore) { }
            }
            waitForAngular(1200);

            Object val = page.evaluate("() => { const e=document.getElementById('__osDiagField'); return e?(e.value||''):''; }");
            String v = val == null ? "" : val.toString().trim();
            if (!v.isEmpty()) {
                lastDiagnosis = v;
                page.evaluate("() => { const e=document.getElementById('__osDiagField'); if(e) e.removeAttribute('id'); }");
                return "typed=" + prefix + " | picked=" + picked + " | Diagnosis=" + v;
            }
            System.out.println("pickDiagnosis: '" + prefix + "' gave no usable suggestion — trying the next prefix");
        }
        page.evaluate("() => { const e=document.getElementById('__osDiagField'); if(e) e.removeAttribute('id'); }");
        return "Diagnosis=(not selected after trying all prefixes)";
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Code/Remark first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit handler
     * from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__osToasts=[]; if(window.__osObs) window.__osObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__osToasts.includes(t)) window.__osToasts.push(t); }); };"
                + " window.__osObs=new MutationObserver(grab); window.__osObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("orderset") || resp.status() >= 400)) return;
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
                + " if(b){ b.id='__osSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("OrderSet submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            // A leftover typeahead/dialog overlay (role="dialog") can still be covering the Submit button at
            // this point — close it first (Escape, or its own close/cancel control) so the real click isn't
            // intercepted.
            page.evaluate("() => { document.querySelectorAll('[role=dialog]').forEach(d=>{ if(d.offsetParent===null) return;"
                    + " const close=d.querySelector('.close,[ng-click*=\"cancel\" i],[ng-click*=\"close\" i],button[aria-label=\"Close\"]');"
                    + " if(close) close.click(); }); }");
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(400);
            try { page.locator("#__osSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                System.out.println("OrderSet submit: real click failed (" + e.getMessage().split("\n")[0] + ") — dispatching in-page click");
                try { page.evaluate("() => { const e=document.getElementById('__osSubmit'); if(e) e.click(); }"); }
                catch (Exception e2) { System.out.println("OrderSet submit: in-page click also failed - " + e2.getMessage()); }
            }
            page.evaluate("() => { const e=document.getElementById('__osSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__osToasts||[]).some(a=>/order|set|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("OrderSet save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__osToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
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
