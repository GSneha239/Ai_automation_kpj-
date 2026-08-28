package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Dose Frequency Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu, EXACT text — "Nursing Station" is a
 * different module and a prefix match hits it first) → <b>Dose Frequency Master</b> (route
 * {@code #/DoseFrequencyMaster}, best-guess fallback — navigation is primarily by the live menu link) →
 * (Add, if the screen has one) → enter <b>Code*</b> + <b>Remark*</b> + <b>Abbreviation*</b> → select a
 * <b>Time</b> and click the inner <b>Add</b> (adds a time row to the detail grid) → <b>Save</b> → success
 * toast.</p>
 *
 * <p>The header fields are DISCOVERED at runtime rather than hard-coded — same reusable shape as
 * {@code Investigation_page.Agency} / {@code Nursing_page.Diagnosis} (this screen's exact ng-model names
 * were not yet confirmed live). Grid filter boxes ({@code colFilter.term}) and the textAngular artefact are
 * excluded.</p>
 */
public class DoseFrequencyMaster extends BasePage {

    public DoseFrequencyMaster(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/DoseFrequencyMaster";
    public String lastCode = "", lastRemark = "", lastAbbr = "", lastTime = "";
    public String lastCodeModel = "", lastRemarkModel = "", lastAbbrModel = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic dose-frequency names + matching abbreviations, not "Auto ... <code>" filler. */
    private static final String[][] REMARK_ABBR = {
            {"Once Daily", "OD"}, {"Twice Daily", "BD"}, {"Three Times Daily", "TDS"},
            {"Four Times Daily", "QID"}, {"Every Morning", "OM"}, {"Every Night", "ON"},
            {"Every 4 Hours", "Q4H"}, {"Every 6 Hours", "Q6H"}, {"Every 8 Hours", "Q8H"},
            {"As Needed", "PRN"}, {"Before Meals", "AC"}, {"After Meals", "PC"}
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** JS helper: the visible Code / Remark / Abbreviation inputs, whatever they are called on this screen. */
    private static final String FIND_FIELDS =
            " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && !skip(e.getAttribute('ng-model')));"
            + " const byModel=re=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
            + " const byPh=re=>boxes.find(e=>re.test(e.placeholder||''));"
            + " const codeEl = byModel(/\\.code(id)?$/i) || byModel(/code/i) || byPh(/^\\s*code\\s*$/i);"
            + " const remEl  = byModel(/\\.(description|remark)$/i) || byModel(/description|remark/i) || byPh(/^\\s*(remark|description)\\s*$/i);"
            + " const abbrEl = byModel(/abbr/i) || byPh(/abbr/i);";

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
                } catch (Exception ignore) { System.out.println("DoseFrequencyMaster.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // EXACT-ish text match on a VISIBLE link ONLY — accepts "Dose Frequency Master" or the shorter
                // "Dose Frequency" (menu labels sometimes drop the trailing "Master").
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*dose\\s*frequency\\s*master\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                        + "        || links.find(x=>/^\\s*dose\\s*frequency\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__dfMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("DoseFrequencyMaster.nav DEBUG: visible links => " + dbg);
                    System.out.println("DoseFrequencyMaster.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__dfMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("DoseFrequencyMaster.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__dfMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__dfMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("DoseFrequencyMaster.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("DoseFrequencyMaster.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Dose Frequency Master screen — verified by Code/Remark boxes present plus the URL. */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/dosefrequency/i.test((location.hash||'').replace(/\\s+/g,''));" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " if(/dose\\s*frequency/i.test(hdr)) return true;"
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
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dfAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__dfAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DoseFrequencyMaster.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dfAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- header form -------------------------------------------------------

    /**
     * Enter <b>Code*</b>, <b>Remark*</b> and <b>Abbreviation*</b>. {@code attempt} shifts all three so a retry
     * after an "already exists" toast submits genuinely different details.
     */
    public String fillHeaderDetails(int attempt) {
        String code = "DF" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        String[] pick = REMARK_ABBR[attempt % REMARK_ABBR.length];
        lastRemark = pick[0] + (attempt >= REMARK_ABBR.length ? " " + (attempt / REMARK_ABBR.length + 1) : "");
        lastAbbr = pick[1] + (attempt >= REMARK_ABBR.length ? (attempt / REMARK_ABBR.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark); const ab=set(abbrEl, a.abbr);"
                + " return 'Code='+cd+' | Remark='+rm+' | Abbreviation='+ab"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+' / '+(abbrEl?(abbrEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "remark", lastRemark, "abbr", lastAbbr));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); lastAbbrModel = m.group(3).trim(); }
        return res;
    }

    // ---- time detail row ---------------------------------------------------

    /**
     * Select a <b>Time</b> (first real option if it's a select, or type a plausible HH:MM if it's a text/time
     * input) and click the INNER <b>Add</b> that appends it to the time-detail grid (distinct from the outer
     * Add that opened this form). Returns a summary; reports "(no time control found)" if the screen has none.
     */
    public String selectTimeAndAddRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const labelNear=e=>{ let t='', p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " const timeSel=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && (/time/i.test(e.getAttribute('ng-model')||'') || /time/i.test(labelNear(e))));"
                + " const timeInp=[...document.querySelectorAll('input')].find(e=>e.offsetParent!==null && (e.type==='time' || /time/i.test(e.getAttribute('ng-model')||'') || /time/i.test(e.placeholder||'') || /time/i.test(labelNear(e))));"
                + " let how='none', val='';"
                + " if(timeSel){ const i=[...timeSel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ timeSel.selectedIndex=i; timeSel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(timeSel).triggerHandler('change');}catch(x){} const $=window.jQuery; if($){try{$(timeSel).trigger('change');}catch(x){}} val=norm(timeSel.options[i].textContent); how='select'; } }"
                + " else if(timeInp){ const c=A.element(timeInp).controller('ngModel'); val='08:00'; timeInp.value=val; if(c){c.$setViewValue(val);c.$render();} timeInp.dispatchEvent(new Event('input',{bubbles:true})); timeInp.dispatchEvent(new Event('change',{bubbles:true})); how='input'; }"
                + " return how+'|'+val; }");
        String how = r == null ? "none|" : r.toString();
        String[] parts = how.split("\\|", 2);
        lastTime = parts.length > 1 ? parts[1] : "";
        if ("none".equals(parts[0])) {
            Object dbg = page.evaluate("() => [...document.querySelectorAll('input,select')].filter(e=>e.offsetParent!==null).slice(0,30).map(e=>e.tagName+'['+(e.getAttribute('ng-model')||e.id||'?')+']').join(' ; ')");
            return "(no time control found) visible=[" + dbg + "]";
        }
        waitForAngular(400);
        // Inner Add — a button/link labelled "Add" that is NOT the outer one (already consumed), found fresh here.
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dfTimeAdd'; return true; }"));
        if (!tagged) return "Time=" + lastTime + " (" + parts[0] + ") | Add row: no Add button found";
        try { page.locator("#__dfTimeAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("selectTimeAndAddRow: Add click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dfTimeAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        return "Time=" + lastTime + " (" + parts[0] + ") | Add row clicked";
    }

    // ---- save ---------------------------------------------------------------

    /**
     * Click <b>Save</b> and return the toast. Re-asserts Code/Remark/Abbreviation first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit
     * handler from the scope when there is no clickable button.
     */
    public String saveAndGetToast() {
        page.evaluate("() => { window.__dfToasts=[]; if(window.__dfObs) window.__dfObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dfToasts.includes(t)) window.__dfToasts.push(t); }); };"
                + " window.__dfObs=new MutationObserver(grab); window.__dfObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("frequency") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(remEl, a.remark); set(abbrEl, a.abbr);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__dfSave'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark, "abbr", lastAbbr));
        System.out.println("DoseFrequencyMaster save => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__dfSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DoseFrequencyMaster save: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dfSave'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__dfToasts||[]).some(a=>/dose|frequency|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("DoseFrequencyMaster save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__dfToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    public String lastSaveHttp = "";

    /** PROBE — the visible field ng-models plus the visible button labels, for a diagnosable FAIL. */
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
