package com.kpj.pages.ApplicationConfiguration_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Machine Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>CSSD Configuration</b> (submenu) → <b>Machine Master</b> →
 * <b>Add</b> → enter <b>Code</b>, <b>Machine Name</b>, select <b>Sterilization Type</b>, enter
 * <b>IdealNumberOfTrays</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Unlike the sibling {@link SterilizationType} (inline-add, no Add button), this screen has a dedicated
 * <b>Add</b> button per the flow. Fields are DISCOVERED at runtime — exact ng-model names were not yet
 * confirmed live — via LABEL text, with {@link #describeForm()} dumping every visible field/button so a
 * naming mismatch is diagnosable from the report without a second live session.</p>
 */
public class MachineMaster extends BasePage {

    public MachineMaster(Page page) { super(page); }

    /** Best-effort direct-route fallback if the menu link cannot be found at all. */
    public static final String ROUTE = "#/MachineMaster";
    public String lastCode = "", lastMachineName = "", lastSterilizationType = "", lastTrays = "";
    private String menuHref = "";
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** Realistic CSSD machine names, not "Autoclave &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] MACHINE_NAMES = {
            "Autoclave Machine", "ETO Sterilizer", "Plasma Sterilizer", "Washer Disinfector", "Ultrasonic Cleaner",
            "Steam Sterilizer Unit", "Dry Heat Sterilizer", "Low Temperature Sterilizer", "Formaldehyde Sterilizer", "Flash Sterilizer"
    };

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
                // EXACT "CSSD Configuration" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/cssd/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1000);
                // EXACT text match ONLY — a bare href-contains fallback previously picked a DIFFERENT, similarly
                // spelled menu item on other submenus (see CancellationReason.nav's "Receipt Cancellation" trap).
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*machine\\s*master\\s*$/i.test(norm(x.textContent)));"
                        + " if(!a) return ''; a.id='__mmMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("MachineMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__mmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("MachineMaster.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__mmMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__mmMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("MachineMaster.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("MachineMaster.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Machine Master screen — verified by the rendered HEADER text or URL, plus an Add button present. */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/machinemaster/i.test(location.hash||'');"
                + " if(!/machine\\s*master/i.test(hdr) && !urlOk) return false;"
                + " return true; }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- actions ---------------------------------------------------------

    /** Click <b>Add</b> to open the Machine Master form. Real-click (polls up to ~7.5s). */
    public String clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__mmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__mmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MachineMaster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mmAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    /**
     * Enter <b>Code</b>, <b>Machine Name</b>, select <b>Sterilization Type</b>, enter
     * <b>IdealNumberOfTrays</b>. Fields are matched by LABEL text (ng-model names were not yet confirmed live).
     * {@code attempt} shifts both Code and Machine Name so a retry after an "already exists" toast submits
     * genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "MC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        String name = MACHINE_NAMES[attempt % MACHINE_NAMES.length] + (attempt >= MACHINE_NAMES.length ? " " + (attempt / MACHINE_NAMES.length + 1) : "");
        String trays = "10";
        Object r = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
                + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
                + "   && !/colFilter|paginationCurrentPage|textAngular|^q$/i.test(e.getAttribute('ng-model')||''));"
                + " const codeEl=boxes.find(e=>/^code\\*?$/i.test(labelOf(e)) || /code/i.test(e.getAttribute('ng-model')||''));"
                + " const nameEl=boxes.find(e=>e!==codeEl && /machine\\s*name/i.test(labelOf(e)));"
                + " const traysEl=boxes.find(e=>e!==codeEl && e!==nameEl && /ideal.*trays|trays/i.test(labelOf(e).replace(/\\s+/g,'')) || /idealnumberoftrays/i.test((e.getAttribute('ng-model')||'')));"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null);"
                + " const stSel=sels.find(e=>/sterilization\\s*type/i.test(labelOf(e)) || /sterilizationtype/i.test(e.getAttribute('ng-model')||''));"
                + " const setText=(e,v)=>{ if(!e) return '(not found)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setText(codeEl, a.code); const nm=setText(nameEl, a.name); const tr=setText(traysEl, a.trays);"
                + " let st='(not found)';"
                + " if(stSel){ stSel.id='__mmSterilSel'; const cur=stSel.options[stSel.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select/i.test(norm(cur.textContent))) st=norm(cur.textContent)+' (kept)';"
                + "   else { const i=[...stSel.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + "     if(i>=0){ st='__SELIDX__'+i; } else st='(no-options)'; } }"
                + " return 'Code='+cd+' | MachineName='+nm+' | Trays='+tr+' | Sterilization='+st"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(nameEl?(nameEl.getAttribute('ng-model')||'?'):'none')+' / '+(traysEl?(traysEl.getAttribute('ng-model')||'?'):'none')+' / '+(stSel?(stSel.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "name", name, "trays", trays));
        waitForAngular(400);
        String res = r == null ? "" : r.toString();
        // If a Sterilization Type option index was found, select it via a REAL Playwright selectOption.
        java.util.regex.Matcher idxM = java.util.regex.Pattern.compile("__SELIDX__(\\d+)").matcher(res);
        if (idxM.find()) {
            int idx = Integer.parseInt(idxM.group(1));
            try {
                page.locator("#__mmSterilSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(idx),
                        new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
            } catch (Exception e) { System.out.println("fillDetails: Sterilization Type select failed - " + e.getMessage()); }
            waitForAngular(400);
            Object t = page.evaluate("() => { const e=document.getElementById('__mmSterilSel'); const v=(e&&e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():''; return v; }");
            res = res.replace("__SELIDX__" + idx, t == null ? "(selected)" : t.toString());
        }
        page.evaluate("() => { const e=document.getElementById('__mmSterilSel'); if(e) e.removeAttribute('id'); }");
        lastMachineName = name; lastTrays = trays;
        java.util.regex.Matcher stM = java.util.regex.Pattern.compile("Sterilization=([^|]+)").matcher(res);
        if (stM.find()) lastSterilizationType = stM.group(1).trim();
        return res;
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Falls back to invoking the form's
     * ng-submit handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__mmToasts=[]; if(window.__mmObs) window.__mmObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mmToasts.includes(t)) window.__mmToasts.push(t); }); };"
                + " window.__mmObs=new MutationObserver(grab); window.__mmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("machine") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__mmSubmit'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }");
        System.out.println("MachineMaster submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__mmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("MachineMaster submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__mmSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__mmToasts||[]).some(a=>/machine|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("MachineMaster save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__mmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
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
                + "   +' type=\"'+(e.type||'')+'\"'+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
