package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Sample</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>Lab Sample</b> → (Add, if the
 * screen has one) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>Sibling trap:</b> this screen sits next to <b>Lab Sample Suitability</b>, whose menu text and route both
 * START with the same words. The menu match is therefore EXACT ({@code ^lab sample$}) and every URL test also
 * requires the absence of "suitab" — a prefix match silently lands on the neighbour, which is a real, working
 * screen that would pass a naive Code/Remark gate.</p>
 *
 * <p>The Code / Remark fields are DISCOVERED at runtime rather than hard-coded: sibling masters use several
 * naming schemes for the same two boxes ({@code commonmaster.Code}/{@code .Description},
 * {@code LabOrganism.code}/{@code .description}, {@code DiagnosisSet.CodeId}/{@code .description}). Grid filter
 * boxes ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 */
public class LabSample extends BasePage {

    public LabSample(Page page) { super(page); }

    /** The menu says "Lab Sample" but the route is {@code #/LabTestSampleType} — the name and the hash disagree. */
    public static final String ROUTE = "#/LabTestSampleType";
    public String lastCode = "", lastName = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    /** Whether the screen's existing rows use all-numeric codes (set by {@link #sampleCodeStyle()}). */
    private boolean numericCodes = true;
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Specimen types a lab would actually register on this screen (Sample Type Name). */
    private static final String[] NAMES = {
            "Venous Blood", "Midstream Urine", "Sputum", "Wound Swab", "Cerebrospinal Fluid", "Stool"
    };

    /** The handling note beside the specimen type (Remark). */
    private static final String[] REMARKS = {
            "Collect in a plain tube", "Collect in an EDTA tube", "Sterile container, early morning",
            "Transport to the lab within one hour", "Keep refrigerated until processed", "Label at the bedside"
    };

    /**
     * Retry qualifiers. The server enforces uniqueness on the <b>Sample Type Name</b>, not the Code, so rotating
     * the pool alone can keep colliding with rows that already exist — a qualifier makes each retry a genuinely
     * new name that still reads like real configuration ("Wound Swab - Paediatric").
     */
    private static final String[] QUALIFIERS = {
            "Paediatric", "Neonatal", "Sterile Site", "Post Transfusion", "Repeat Collection"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: the visible <b>Code</b> and <b>Remark</b> inputs, whatever they are called here. Each match is
     * CLAIMED ({@code used}) so the two roles can never resolve to the same box. select2 chrome
     * ({@code s2id_autogen*}) is skipped, but a real {@code <select>} is NOT — select2 marks the field it wraps
     * with class {@code select2-offscreen}, and a blanket class test would discard it.
     */
    private static final String FIND_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const lbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const skip=m=>/colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m||'');"
            + " const s2=e=>/^s2id_autogen/i.test(e.id||'') || (e.tagName!=='SELECT' && /select2/i.test(e.className||''));"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !s2(e) && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            + " const codeEl = pick(e=>/code/i.test(lbl(e)), e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)), e=>/^\\s*code\\s*$/i.test(plc(e)));"
            // Sample Type Name* is mandatory here even though it is not in the screen's own field list — leaving it
            // empty makes Submit fail with the misleading "Please Enter Description!".
            + " const nameEl = pick(e=>/name/i.test(lbl(e)), e=>/\\.name$/i.test(mdl(e)), e=>/name/i.test(mdl(e)), e=>/^\\s*name\\s*$/i.test(plc(e)));"
            + " const remEl  = pick(e=>/^(remark|description)/i.test(lbl(e)), e=>/\\.(description|remark)$/i.test(mdl(e)), e=>/description|remark/i.test(mdl(e)), e=>/^\\s*(remark|description)\\s*$/i.test(plc(e)));";

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
                // EXACT text, and an href fallback that EXCLUDES the "Lab Sample Suitability" neighbour.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*lab\\s*samples?\\s*$/i.test(norm(x.textContent)))"
                        + "   || [...document.querySelectorAll('a[href]')].find(x=>{ const h=x.getAttribute('href')||''; return /labsample/i.test(h) && !/suitab/i.test(h); });"
                        + " if(!a) return ''; a.id='__lsmMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("LabSample.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__lsmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("LabSample.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__lsmMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__lsmMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("LabSample.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("LabSample.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Lab Sample screen — URL plus something real on the page (a Code/Remark pair or an Add button).
     * The "suitab" exclusion keeps the neighbouring Lab Sample Suitability screen from passing this gate, and the
     * field test demands BOTH boxes because the leftover "Transfer" screen renders a Code and a Submit but no Remark.
     */
    public boolean onScreen() {
        // Route is #/LabTestSampleType, so test for "sample" rather than the screen name; "suitab" still excludes
        // the Lab Sample Suitability neighbour (#/LabSampleSuitability), which would otherwise pass this gate.
        String u = page.url().toLowerCase();
        if (!u.contains("sample") || u.contains("suitab")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS
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
     * <p>Learned on the sibling Interpretation Template: its save proc casts existing codes to {@code int}, so a
     * single {@code IT#####} row inserted by a test broke every later save on that screen for everyone. Matching
     * the format the screen already uses avoids planting that kind of row.</p>
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
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        // Already showing both boxes -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__lsmAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__lsmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("LabSample.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__lsmAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        long n = Math.abs((System.nanoTime() + attempt * 7919L) % 100000);
        // The screen's own rows read SAM00001, SAM00002 … — match that rather than inventing a new prefix.
        lastCode = numericCodes ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                                : "SAM" + String.format("%05d", n);
        lastName = NAMES[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % NAMES.length)]
                + (attempt == 0 ? "" : " - " + QUALIFIERS[(attempt - 1) % QUALIFIERS.length]);
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const nm=set(nameEl, a.name); const rm=set(remEl, a.remark);"
                + " return 'Code='+cd+' | Sample Type Name='+nm+' | Remark='+rm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", lastCode, "name", lastName, "remark", lastRemark));
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
        page.evaluate("() => { window.__lsmToasts=[]; if(window.__lsmObs) window.__lsmObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__lsmToasts.includes(t)) window.__lsmToasts.push(t); }); };"
                + " window.__lsmObs=new MutationObserver(grab); window.__lsmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("sample") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(nameEl, a.name); set(remEl, a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__lsmSubmit'; return 'click:'+(b.getAttribute('ng-click')||norm(b.textContent||b.value)); }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "name", lastName, "remark", lastRemark));
        System.out.println("LabSample submit => " + how);
        if (String.valueOf(how).startsWith("click")) {
            try { page.locator("#__lsmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("LabSample submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__lsmSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__lsmToasts||[]).some(a=>/sample|master|saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("LabSample save HTTP => " + lastSaveHttp);
        // "succes" (one s) is deliberate — the sibling Lab Organism screen returns "Record added succesfully".
        Object t = page.evaluate("() => { const a=window.__lsmToasts||[]; return a.find(x=>/saved|added|succes/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels
     * with their ng-click handlers. Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type?(':'+e.type):'')+(e.tagName==='SELECT'?(' opts='+e.options.length):'')"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
