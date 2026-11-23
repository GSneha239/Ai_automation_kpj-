package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>SlidingScaleProtocol</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>SlidingScaleProtocol</b> → enter
 * <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast. INLINE-ADD generic CommonMaster screen
 * (Form Name pre-selected, no Add button — confirmed live via screenshot).</p>
 *
 * <p>Do NOT confuse with {@code Billing_page.SlidingScaleProtocol} (Receivable Master, route {@code #/receivableMaster},
 * SlidingScaleProtocol Type / phone / e-mail form) — an earlier version of this class incorrectly subclassed that screen
 * because a menu-link search with no visibility filter matched a DOM-present-but-hidden leftover link from
 * the Billing submenu instead of Patient's own "SlidingScaleProtocol" link. The two are unrelated screens.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded — same reusable shape as
 * {@code Investigation_page.Agency} / {@code Nursing_page.CampType} (this screen's exact route and
 * ng-model names were not yet confirmed live; sibling masters can live at routes that do not literally match
 * their menu label, so {@link #onScreen()} verifies by the rendered HEADER text via a substring match).
 * Grid filter boxes ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 */
public class SlidingScaleProtocol extends BasePage {

    public SlidingScaleProtocol(Page page) { super(page); }

    /** Best-effort direct-route fallback if the menu link cannot be found at all. */
    public static final String ROUTE = "#/SlidingScaleProtocolList";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Remarks that read like genuine configuration rather than machine noise. */
    private static final String[] REMARKS = {
            "ICD-10 Classification", "ICD-10-CM Edition", "ICD-11 Classification", "ICD-9-CM Legacy", "ICD-O Oncology Edition", "ICD-10 WHO Edition"
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
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1200);
                // The submenu can take a moment to actually render its child links after the toggle click —
                // poll for ANY visible link before trusting the "not found" result (learned from BlackListReason /
                // BloodGroup, where a query issued too early only saw 2 leftover top-level links).
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a[href]')].filter(a=>a.offsetParent!==null).length > 3",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                } catch (Exception ignore) { System.out.println("SlidingScaleProtocol.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // EXACT text match on a VISIBLE link ONLY — a href-pattern search with no offsetParent!==null
                // filter previously matched a DOM-present-but-hidden leftover link from the Billing submenu's
                // OWN "SlidingScaleProtocol" (Receivable Master) instead of this Nursing submenu's own link.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*sliding\\s*scale\\s*protocol\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__ssMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("SlidingScaleProtocol.nav DEBUG: visible links => " + dbg);
                    System.out.println("SlidingScaleProtocol.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__ssMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("SlidingScaleProtocol.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__ssMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__ssMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("SlidingScaleProtocol.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("SlidingScaleProtocol.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the SlidingScaleProtocol screen — verified by a Code/Remark box actually present (this app's CommonMaster-family
     * header text is unreliable to check by itself — confirmed live it can show "COMPANY", the pre-selected
     * Form Name value, rather than "SlidingScaleProtocol" — so field presence plus the URL are the real signals).
     */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const urlOk=/slidingscaleprotocol/i.test(location.hash||'');" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
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
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ssAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__ssAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("SlidingScaleProtocol.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ssAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "SS" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length)]
                + " " + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + attempt;   // unique: never repeats
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
        page.evaluate("() => { window.__ssToasts=[]; if(window.__pyObs) window.__pyObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ssToasts.includes(t)) window.__ssToasts.push(t); }); };"
                + " window.__pyObs=new MutationObserver(grab); window.__pyObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("payor") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__pySubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("SlidingScaleProtocol submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__pySubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SlidingScaleProtocol submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pySubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__ssToasts||[]).some(a=>/payor|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("SlidingScaleProtocol save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__ssToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    public String lastParameter = "", lastUnit = "", lastDoseUnit = "", lastProtoCode = "", lastProtoName = "", lastStep = "";

    /** Set a plain text/number input by its ng-model (Angular-aware). */
    private String setInput(String model, String value) {
        Object r = page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='\"+a.m+\"']\")].find(x=>x.offsetParent!==null);"
                + " if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return e.value; }",
                java.util.Map.of("m", model, "v", value));
        return r == null ? "" : r.toString();
    }

    /**
     * Select the first real option of a (possibly select2-hidden) dropdown by ng-model: the options are waited for
     * (they fill asynchronously), the choice fires a real change event, and the selection is read back off the control.
     */
    private String selectFirst(String model) {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            Object ready = page.evaluate("(m) => { const e=document.querySelector(\"select[ng-model='\"+m+\"']\");"
                    + " return !!e && [...e.options].filter(o=>o.value && !/^-*\s*select/i.test((o.text||'').trim())).length>0; }", model);
            if (Boolean.TRUE.equals(ready)) break;
            page.waitForTimeout(400);
        }
        Object r = page.evaluate("(m) => { const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); if(!e) return '(no dropdown)';"
                + " const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\s*select/i.test((x.o.text||'').trim()));"
                + " if(!reals.length) return '(no options)';"
                + " e.selectedIndex=reals[0].i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return ((e.options[e.selectedIndex]||{}).text||'').trim()+' ['+reals.length+' options]'; }", model);
        waitForAngular(900);
        return r == null ? "" : r.toString();
    }

    /** Enter the unique <b>Code*</b> and <b>Protocol Name*</b>. */
    public String enterCodeAndName(int attempt) {
        lastProtoCode = "SS" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastProtoName = "Insulin Sliding Scale " + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + attempt;
        String c = setInput("Protocol.ProtocolCode", lastProtoCode);
        String n = setInput("Protocol.ProtocolName", lastProtoName);
        waitForAngular(400);
        return "Code=" + c + " | Protocol Name=" + n;
    }

    /** Select the <b>Parameter*</b> then the <b>Unit*</b> (Unit can depend on the Parameter, so it is read after). */
    public String selectParameterAndUnit() {
        lastParameter = selectFirst("Protocol.PatientVitalID");
        lastUnit = selectFirst("Protocol.UnitID");
        return "Parameter=" + lastParameter + " | Unit=" + lastUnit;
    }

    /** Enter <b>Range From</b>, <b>Range To</b> and <b>Dose</b> ({@code step.FromValue / ToValue / DosageQuantity}). */
    public String enterRangeAndDose() {
        String f = setInput("step.FromValue", "1");
        String t = setInput("step.ToValue", "10");
        String d = setInput("step.DosageQuantity", "2");
        waitForAngular(400);
        lastStep = "Range From=" + f + " | Range To=" + t + " | Dose=" + d;
        return lastStep;
    }

    /** Select the <b>Dose Unit</b> ({@code step.DosageUnitID}). */
    public String selectDoseUnit() {
        lastDoseUnit = selectFirst("step.DosageUnitID");
        return "Dose Unit=" + lastDoseUnit;
    }

    /** Click <b>Add Step</b> (adds the Range/Dose row to the protocol's grid). Returns true when the click landed. */
    public boolean clickAddStep() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\s*add\s*step\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ssAddStep'; return true; }"));
        if (!tagged) return false;
        try { page.locator("#__ssAddStep").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        page.evaluate("() => { const e=document.getElementById('__ssAddStep'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        return true;
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
