package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Mews Score</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu, EXACT text — "Nursing Station" is a
 * different module and a prefix match hits it first) → <b>Mews Score</b> (route {@code #/MewsScore}, best-guess
 * fallback — navigation is primarily by the live menu link) → (Add, if the screen has one) → enter
 * <b>Code*</b> + <b>Description*</b> → select <b>Vital</b>, enter <b>Score</b>, pick a <b>Type</b>, enter
 * <b>Min</b>/<b>Max</b>/<b>Value</b>, click the inner <b>Add</b> (appends a vital-score row) → enter one shared
 * <b>Interpretation</b> plus the <b>From Score</b>/<b>To Score</b> thresholds for BOTH "Future Observation"
 * and "Immediate Attention" → <b>Submit</b> → success toast.</p>
 *
 * <p>Confirmed live (2026-08-20) — exact ng-models, NOT the fuzzy label-matched guesses this page object
 * started with:</p>
 * <ul>
 *   <li>{@code MewsScore.Code} (Code*) and {@code MewsScore.Description} (Description*) — Code is MANDATORY
 *       and easy to miss since the user-facing flow only calls out "Description".</li>
 *   <li>{@code MewsScore.applicabletoid} (select, "ApplicableFor") — filled defensively in case it too is
 *       required; the app's sequential validation only reveals one missing field at a time.</li>
 *   <li>{@code MewsScore.Vital} (select), {@code MewsScore.Score} (plain number input — NOT a "Score Type"
 *       select as the request phrasing suggested), {@code MewsScore.Type} (a RADIO group, both options
 *       labelled "Numeric" in the DOM dump — picked by the first unchecked one), {@code MewsScore.Min} /
 *       {@code .Max} / {@code .Value}.</li>
 *   <li>{@code MewsScore.Interpretation} is a SINGLE shared textarea — there are NOT two independent
 *       Interpretation boxes per category. The two categories only have their own score thresholds:
 *       {@code MewsScore.FutureObservationFromScore} / {@code .FutureObservationToScore} and
 *       {@code MewsScore.ImmediateAttentionFromScore} / {@code .ImmediateAttentionToScore}.</li>
 * </ul>
 */
public class MewsScore extends BasePage {

    public MewsScore(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/MewsScore";
    public String lastCode = "", lastDescription = "";
    public String lastVital = "", lastScore = "", lastType = "", lastMin = "", lastMax = "", lastValue = "";
    public String lastInterpretation = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic Mews Score descriptions, not "Auto ... <code>" filler. */
    private static final String[] DESCRIPTIONS = {
            "Modified Early Warning Score", "Adult MEWS Chart", "General Ward MEWS", "Post-Op MEWS Chart",
            "Obstetric MEWS", "Paediatric Early Warning Score", "Standard MEWS Assessment", "ICU Step-Down MEWS"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** JS helper: the Code / Description inputs, by their confirmed exact ng-model suffix. */
    private static final String FIND_HEADER =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='file');"
            + " const codeEl = boxes.find(e=>/\\.code$/i.test(e.getAttribute('ng-model')||''));"
            + " const descEl = boxes.find(e=>/\\.description$/i.test(e.getAttribute('ng-model')||''));";

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
                } catch (Exception ignore) { System.out.println("MewsScore.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // "Mews Score" — case varies in the app (MEWS / Mews); match loosely on the two words.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*mews\\s*score\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__mwMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("MewsScore.nav DEBUG: visible links => " + dbg);
                    System.out.println("MewsScore.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__mwMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("MewsScore.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__mwMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__mwMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("MewsScore.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("MewsScore.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Mews Score screen — verified by Code/Description boxes present plus the URL. */
    public boolean onScreen() {
        Object r = page.evaluate("() => {" + FIND_HEADER
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/mewsscore/i.test((location.hash||'').replace(/\\s+/g,''));"
                + " if(codeEl && descEl) return true;"
                + " if(/mews\\s*score/i.test(hdr)) return true;"
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
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_HEADER + " return !!(codeEl && descEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__mwAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__mwAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MewsScore.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mwAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- header form -------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Description*</b>, and select the first real <b>ApplicableFor</b> option
     * defensively (the app validates one missing field at a time, so a hidden second requirement would
     * otherwise surface only after Code is already fixed). {@code attempt} shifts Code/Description so a retry
     * after "already exists" is genuinely different.
     */
    public String fillHeaderDetails(int attempt) {
        String code = "MW" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastDescription = DESCRIPTIONS[attempt % DESCRIPTIONS.length] + (attempt >= DESCRIPTIONS.length ? " " + (attempt / DESCRIPTIONS.length + 1) : "");
        Object r = page.evaluate("(a) => new Promise(async resolve => { const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + FIND_HEADER
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl,a.code); const de=set(descEl,a.description);"
                + " const appSel=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && /applicableto/i.test(e.getAttribute('ng-model')||''));"
                + " let applicable='(no ApplicableFor select)';"
                + " if(appSel){ const i=[...appSel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ appSel.selectedIndex=i; appSel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(appSel).triggerHandler('change');}catch(x){} if($){try{$(appSel).trigger('change');}catch(x){}} applicable=norm(appSel.options[i].textContent); } }"
                + " resolve('Code='+cd+' | Description='+de+' | ApplicableFor='+applicable); })",
                java.util.Map.of("code", code, "description", lastDescription));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    // ---- vital / score row --------------------------------------------------

    /**
     * Select the first real <b>Vital</b> option, enter <b>Score</b>, pick the first <b>Type</b> radio option,
     * and enter <b>Min</b>/<b>Max</b>/<b>Value</b> — all by their confirmed exact ng-model suffix. Returns a
     * summary.
     */
    public String fillVitalScoreRow() {
        Object r = page.evaluate("() => new Promise(async resolve => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const byModel=re=>[...document.querySelectorAll('input,select')].find(e=>e.offsetParent!==null && re.test(e.getAttribute('ng-model')||''));"
                + " let vitalSel=byModel(/\\.vital$/i);"
                + " for(let k=0;k<15 && vitalSel && [...vitalSel.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))).length===0;k++){ await sleep(400); vitalSel=byModel(/\\.vital$/i); }"
                + " let vital='(no vital select)';"
                + " if(vitalSel){ const i=[...vitalSel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ vitalSel.selectedIndex=i; vitalSel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(vitalSel).triggerHandler('change');}catch(x){} if($){try{$(vitalSel).trigger('change');}catch(x){}} vital=norm(vitalSel.options[i].textContent); } }"
                + " const setTxt=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const scoreEl=byModel(/\\.score$/i); const score=setTxt(scoreEl,'1');"
                + " const typeRadios=[...document.querySelectorAll('input[type=radio]')].filter(e=>e.offsetParent!==null && /\\.type$/i.test(e.getAttribute('ng-model')||''));"
                + " let type='(no Type radio)';"
                + " const typeRadio=typeRadios.find(e=>!e.checked) || typeRadios[0];"
                + " if(typeRadio){ typeRadio.checked=true; typeRadio.dispatchEvent(new Event('click',{bubbles:true})); typeRadio.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(typeRadio).triggerHandler('click');}catch(x){}"
                + "   const lbl=typeRadio.closest('label'); type=lbl?norm(lbl.textContent):(typeRadio.value||'checked'); }"
                + " const minEl=byModel(/\\.min$/i); const maxEl=byModel(/\\.max$/i); const valEl=byModel(/\\.value$/i);"
                + " const min=setTxt(minEl,'0'); const max=setTxt(maxEl,'10'); const val=setTxt(valEl,'1');"
                + " resolve('Vital='+vital+' | Score='+score+' | Type='+type+' | Min='+min+' | Max='+max+' | Value='+val); })");
        String s = r == null ? "" : r.toString();
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("Vital=([^|]+)\\|").matcher(s);
        if (m1.find()) lastVital = m1.group(1).trim();
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("Type=([^|]+)\\|").matcher(s);
        if (m2.find()) lastType = m2.group(1).trim();
        lastScore = "1"; lastMin = "0"; lastMax = "10"; lastValue = "1";
        waitForAngular(400);
        return s;
    }

    /**
     * Click the INNER <b>Add</b> that appends the filled Vital/Score/Type/Min/Max/Value row to the detail
     * grid (distinct from the outer Add that opened this form).
     */
    public String clickAddVitalRow() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__mwVitalAdd'; return true; }"));
        if (!tagged) return "no inner Add button found";
        try { page.locator("#__mwVitalAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAddVitalRow: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mwVitalAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1000);
        return "inner Add clicked (vital/score row)";
    }

    // ---- interpretation + thresholds --------------------------------------------------

    /**
     * Fill the ONE shared <b>Interpretation</b> textarea, then the <b>From Score</b>/<b>To Score</b>
     * thresholds for BOTH "Future Observation" and "Immediate Attention" — by their confirmed exact ng-model
     * suffixes. Returns a summary.
     */
    public String fillInterpretationAndThresholds() {
        lastInterpretation = "Continue routine monitoring; escalate to medical officer if MEWS threshold is reached";
        Object r = page.evaluate("(v) => { const A=window.angular;"
                + " const byModel=re=>[...document.querySelectorAll('input,textarea')].find(e=>e.offsetParent!==null && e.type!=='file' && re.test(e.getAttribute('ng-model')||''));"
                + " const setTxt=(e,val)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return val; };"
                + " const interp=setTxt(byModel(/\\.interpretation$/i), v);"
                + " const foFrom=setTxt(byModel(/\\.futureobservationfromscore$/i), '0');"
                + " const foTo=setTxt(byModel(/\\.futureobservationtoscore$/i), '4');"
                + " const iaFrom=setTxt(byModel(/\\.immediateattentionfromscore$/i), '5');"
                + " const iaTo=setTxt(byModel(/\\.immediateattentiontoscore$/i), '10');"
                + " return 'Interpretation='+interp"
                + "   +' | FutureObservation[From='+foFrom+' To='+foTo+']'"
                + "   +' | ImmediateAttention[From='+iaFrom+' To='+iaTo+']'; }", lastInterpretation);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    // ---- save ---------------------------------------------------------------

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Code/Description first — these
     * forms re-render and can blank a field between fill and click.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__mwToasts=[]; if(window.__mwObs) window.__mwObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mwToasts.includes(t)) window.__mwToasts.push(t); }); };"
                + " window.__mwObs=new MutationObserver(grab); window.__mwObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("mews") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_HEADER
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(descEl, a.description);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__mwSubmit'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "description", lastDescription));
        System.out.println("MewsScore submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__mwSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("MewsScore submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__mwSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__mwToasts||[]).some(a=>/mews|score|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("MewsScore save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__mwToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    public String lastSaveHttp = "";

    /** PROBE — the visible field ng-models plus the visible button labels, for a diagnosable FAIL. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,60).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,25);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
