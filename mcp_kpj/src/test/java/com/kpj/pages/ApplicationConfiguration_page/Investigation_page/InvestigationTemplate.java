package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>InvestigationTemplate</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>InvestigationTemplate</b> → (Add, if the
 * screen has one) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded: sibling masters use several
 * naming schemes for the same two boxes ({@code commonmaster.Code}/{@code .Description},
 * {@code DrugInstruction.code}/{@code .description}, {@code DiagnosisSet.CodeId}/{@code .description}), and the
 * labels on these screens are mis-associated so they cannot be trusted either. Grid filter boxes
 * ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 *
 * <p>Menu note: the submenu parent is matched on EXACT text — a prefix match can hit a different module.</p>
 */
public class InvestigationTemplate extends BasePage {

    public InvestigationTemplate(Page page) { super(page); }

    public static final String ROUTE = "#/InvestigationTemplate";
    public String lastCode = "", lastName = "", lastRemark = "", lastCodeModel = "", lastNameModel = "", lastRemarkModel = "";
    public String lastGender = "", lastDoctor = "";
    /** Whether the screen's existing rows use all-numeric codes (set by sampleCodeStyle). */
    private boolean numericCodes = true;
    private String menuHref = "";
    public byte[] toastPng;

    /** Remarks — a short note a person would actually type on this screen. */
    private static final String[] REMARKS = {
            "Standard investigation reporting", "For routine outpatient screening",
            "Used for pre-operative workup", "General laboratory reporting",
            "For radiology reporting", "Follow-up investigation notes"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: the visible <b>Code</b> / <b>Name</b> / <b>Remark</b> inputs, whatever they are called here.
     * Each match is CLAIMED ({@code used}) so two roles can never resolve to the same box — without that, a
     * loose fallback would hand the same input back twice and one field would silently go unfilled.
     */
    private static final String FIND_FIELDS =
            " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            + " const codeEl = pick(e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)), e=>/^\\s*code\\s*$/i.test(plc(e)));"
            + " const nameEl = pick(e=>/\\.name$/i.test(mdl(e)), e=>/name/i.test(mdl(e)), e=>/^\\s*name\\s*$/i.test(plc(e)));"
            + " const remEl  = pick(e=>/\\.(description|remark)$/i.test(mdl(e)), e=>/description|remark/i.test(mdl(e)), e=>/^\\s*(remark|description)\\s*$/i.test(plc(e)));";

    /**
     * JS helper: set the <b>Remark / Description</b> when it is NOT a plain visible box. On this screen the
     * server rejects with "Please Enter Description!" while no visible input matches — a rich-text editor keeps
     * its real {@code <textarea>} HIDDEN (so the visible-only scan skips it) and mirrors the text elsewhere.
     * Tries, in order: any hidden/visible field with a description-ish ng-model, every CKEditor instance, then a
     * contenteditable. Returns how it was set, for the report.
     */
    private static final String SET_DESC =
            " const commit=(e,v)=>{ const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
            + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
            + " const setDescAnywhere=(v)=>{ let how='';"
            + "   const cand=[...document.querySelectorAll('input,textarea')].find(e=>/description|remark/i.test(e.getAttribute('ng-model')||''));"
            + "   if(cand){ commit(cand,v); how='hidden-model:'+(cand.getAttribute('ng-model')||'?'); }"
            + "   try{ if(window.CKEDITOR && CKEDITOR.instances){ for(const k in CKEDITOR.instances){ const i=CKEDITOR.instances[k];"
            + "     i.setData(v); try{ i.updateElement(); }catch(e){} const el=i.element && i.element.$; if(el) commit(el,v);"
            + "     how=(how?how+'+':'')+'ckeditor:'+k; } } }catch(e){}"
            + "   if(!how){ const ce=[...document.querySelectorAll('[contenteditable=true]')].find(e=>e.offsetParent!==null);"
            + "     if(ce){ ce.innerHTML=v; ce.dispatchEvent(new Event('input',{bubbles:true})); ce.dispatchEvent(new Event('blur',{bubbles:true})); how='contenteditable'; } }"
            + "   return how||'(no description field)'; };";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            // The WHOLE menu-entry block is guarded: clicking a menu link routes the SPA, which destroys the JS
            // execution context mid-evaluate ("Execution context was destroyed"). That is a SUCCESSFUL navigation,
            // not a failure — swallow it and let onScreen()/the direct-route fallback decide where we landed.
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Investigation" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*investigations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1500);
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*investigation\\s*template\\s*$/i.test(norm(x.textContent)) || (/investigation/i.test(x.getAttribute('href')||'') && /template/i.test(x.getAttribute('href')||''))); if(!a) return ''; a.id='__ivMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("InvestigationTemplate.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__ivMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("InvestigationTemplate.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__ivMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__ivMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("InvestigationTemplate.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("InvestigationTemplate.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the InvestigationTemplate screen — URL plus something real on the page (a Code/Remark box or an Add button). */
    public boolean onScreen() {
        // BOTH words: a bare "investigation" test also accepts #/HistoDashboard (the Histopath Dashboard), which is a
        // real, working screen sitting earlier in the menu — that is what a loose match landed on first.
        String u = page.url().toLowerCase();
        if (!(u.contains("investigation") && u.contains("template"))) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }"));
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Read the LIST grid's existing Code values and decide which format to generate. Call this BEFORE Add.
     *
     * <p>Learned the hard way on the sibling Interpretation Template: its save proc casts existing codes to
     * {@code int}, so a single {@code IT#####} row inserted by a test broke every later save on that screen for
     * everyone. Matching the format the screen already uses avoids planting that kind of row.</p>
     */
    public String sampleCodeStyle() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " let cells=[...document.querySelectorAll('.ui-grid-row .ui-grid-cell-contents')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " if(!cells.length) cells=[...document.querySelectorAll('table tbody tr td')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " const codes=cells.filter(t=>t.length<=12 && /^[A-Za-z0-9._-]+$/.test(t)).slice(0,12);"
                + " return JSON.stringify(codes); }");
        String raw = r == null ? "[]" : r.toString();
        java.util.List<String> codes = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(raw);
        while (m.find()) codes.add(m.group(1));
        long digits = codes.stream().filter(c -> c.matches("\\d+")).count();
        // Only call it alphanumeric when EVERY sample is; with nothing to learn from, keep the safer numeric form.
        if (codes.isEmpty()) { numericCodes = true; return "no existing rows sampled - defaulting to a NUMERIC code"; }
        numericCodes = digits == codes.size();
        return "sampled " + codes.size() + " grid value(s) " + codes.subList(0, Math.min(6, codes.size()))
                + " -> generating a " + (numericCodes ? "NUMERIC" : "prefixed alphanumeric") + " code";
    }

    /**
     * Select the two mandatory drop-downs — <b>Gender</b> and <b>Pathologist/Radiologist</b> — with a REAL
     * {@code selectOption} so any {@code ng-change} fires (a synthetic assignment does not). Picks the first
     * real (non "-Select-") option of each.
     */
    public String selectDropdowns() {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p&&h++<3&&!t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } } return t; };"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null);"
                + " const hit=(e,re)=>re.test(e.getAttribute('ng-model')||'') || re.test(lbl(e)) || [...e.options].some(o=>re.test(o.textContent||''));"
                + " const g=sels.find(e=>hit(e,/gender|sex/i));"
                + " const p=sels.find(e=>e!==g && hit(e,/patholog|radiolog|doctor|consultant/i));"
                + " if(g) g.id='__ivGender'; if(p) p.id='__ivDoctor';"
                + " return ' [selects: '+sels.length+', gender-model='+(g?(g.getAttribute('ng-model')||'?'):'none')"
                + "   +', doctor-model='+(p?(p.getAttribute('ng-model')||'?'):'none')+']'; }");
        String info = found == null ? "" : found.toString();
        lastGender = pickFirstRealOption("#__ivGender");
        lastDoctor = pickFirstRealOption("#__ivDoctor");
        page.evaluate("() => { ['__ivGender','__ivDoctor'].forEach(id=>{ const e=document.getElementById(id); if(e) e.removeAttribute('id'); }); }");
        waitForAngular(800);
        return "Gender=" + (lastGender.isEmpty() ? "(not selected)" : lastGender)
                + " | Pathologist/Radiologist=" + (lastDoctor.isEmpty() ? "(not selected)" : lastDoctor) + info;
    }

    /** Real selectOption on the first non "-Select-" option; returns the chosen label, or "" if unavailable. */
    private String pickFirstRealOption(String selector) {
        try {
            if (page.locator(selector).count() == 0) return "";
            Object idx = page.evaluate("(s) => { const norm=t=>(t||'').replace(/\\s+/g,' ').trim();"
                    + " const e=document.querySelector(s); if(!e) return -1;"
                    + " for(let i=0;i<e.options.length;i++){ const o=e.options[i]; if(o.value && !/^-*\\s*select|^\\s*$/i.test(norm(o.textContent))) return i; } return -1; }", selector);
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i < 0) return "";
            page.locator(selector).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(600);
            Object t = page.evaluate("(s) => { const e=document.querySelector(s); return e ? ((e.options[e.selectedIndex]||{}).text||'') : ''; }", selector);
            return t == null ? "" : t.toString().trim();
        } catch (Exception e) {
            System.out.println("pickFirstRealOption(" + selector + "): " + e.getMessage());
            return "";
        }
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
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ivAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__ivAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("InvestigationTemplate.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ivAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b>, <b>Name*</b> and <b>Remark*</b>. {@code attempt} shifts ALL THREE values so a retry
     * after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        // Code format MATCHES THE SCREEN'S EXISTING ROWS (see sampleCodeStyle). On the sibling Interpretation
        // Template an "IT#####" code was accepted by the insert and then broke every later save, because the proc
        // casts existing codes to int — so never assume a prefixed code is safe here.
        String code = numericCodes
                ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                : "IV" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        // Name used to cycle through a fixed 6-entry NAMES pool — on this shared, long-lived QA
        // environment all 6 are already used up from prior runs, so every "already exists" retry just
        // re-hit the same already-taken names instead of finding a fresh one. Generate a genuinely
        // unique name per attempt instead, the same way Code already does.
        lastName = "Auto Template " + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        // Same reasoning as Name above: a fixed 6-entry REMARKS pool runs dry on a shared, long-lived QA
        // environment. Keep a readable base phrase but make it unique per attempt.
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length)]
                + " " + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS + SET_DESC
                + " const set=(e,v)=>{ if(!e) return '(no field)'; commit(e,v); return v; };"
                + " const cd=set(codeEl, a.code); const nm=set(nameEl, a.name);"
                + " const rm = remEl ? set(remEl, a.remark) : (a.remark+' [via '+setDescAnywhere(a.remark)+']');"
                + " const mn=e=>e?(e.getAttribute('ng-model')||'?'):'none';"
                + " return 'Code='+cd+' | Name='+nm+' | Remark='+rm"
                + "   +' | [models: '+mn(codeEl)+' / '+mn(nameEl)+' / '+mn(remEl)+']'; }",
                java.util.Map.of("code", code, "name", lastName, "remark", lastRemark));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastNameModel = m.group(2).trim(); lastRemarkModel = m.group(3).trim(); }
        return res;
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Code/Remark first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit handler
     * from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ivToasts=[]; if(window.__ivObs) window.__ivObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ivToasts.includes(t)) window.__ivToasts.push(t); }); };"
                + " window.__ivObs=new MutationObserver(grab); window.__ivObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("investigation") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_FIELDS + SET_DESC
                + " const set=(e,v)=>{ if(!e) return; commit(e,v); };"
                + " set(codeEl, a.code); set(nameEl, a.name);"
                + " if(remEl) set(remEl, a.remark); else setDescAnywhere(a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__ivSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "name", lastName, "remark", lastRemark));
        System.out.println("InvestigationTemplate submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__ivSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("InvestigationTemplate submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ivSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__ivToasts||[]).some(a=>/investigation|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("InvestigationTemplate save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__ivToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels.
     * Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeEditors() {
        Object r = page.evaluate("() => {"
                + " const hidden=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent===null)"
                + "   .map(e=>'HIDDEN '+e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.id||e.name||'?')+']').slice(0,20);"
                + " let ck=[]; try{ if(window.CKEDITOR && CKEDITOR.instances) ck=Object.keys(CKEDITOR.instances); }catch(e){}"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].map(e=>e.className||'(contenteditable)').slice(0,5);"
                + " return 'HIDDEN FIELDS: '+(hidden.join(' ; ')||'(none)')"
                + "   +'  || CKEDITOR: '+(ck.join(', ')||'(none)')"
                + "   +'  || CONTENTEDITABLE: '+(ce.join(', ')||'(none)'); }");
        return r == null ? "" : r.toString();
    }

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
