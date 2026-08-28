package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Machine</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>Lab Machine</b> → <b>Add</b> →
 * enter <b>Code*</b> + <b>Remark*</b> → enter <b>Machine Para. Code</b>, <b>Machine Para. Name</b>,
 * <b>Parameter Name</b> → <b>Add</b> (appends the parameter row) → <b>Submit</b> → success toast.</p>
 *
 * <p>Master-detail screen: the three parameter boxes are a DETAIL line that only reaches the server once the inner
 * <b>Add</b> has appended it to the list, so {@link #clickAddDetail()} reports the row-count delta — a Submit with
 * no appended row is the usual cause of a "Please add parameter" style rejection.</p>
 *
 * <p>Fields are DISCOVERED at runtime rather than hard-coded (sibling masters name the same two boxes
 * {@code commonmaster.Code}/{@code .Description}, {@code DrugInstruction.code}/{@code .description},
 * {@code DiagnosisSet.CodeId}/{@code .description}). Here the LABEL is the primary discriminator, because
 * <b>Machine Para. Code</b> also satisfies a loose {@code /code/} ng-model test — the detail fields are therefore
 * claimed BEFORE the master Code so they cannot be stolen from each other.</p>
 *
 * <p>Menu note: the submenu parent is matched on EXACT text — a prefix match can hit a different module.</p>
 */
public class LabMachine extends BasePage {

    public LabMachine(Page page) { super(page); }

    public static final String ROUTE = "#/LabMachine";
    public String lastCode = "", lastRemark = "";
    public String lastParaCode = "", lastParaName = "", lastParameterName = "";
    public String lastCodeModel = "", lastRemarkModel = "";
    /** Whether the screen's existing rows use all-numeric codes (set by {@link #sampleCodeStyle()}). */
    private boolean numericCodes = true;
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Lab analysers a person would actually register on this screen (Remark). */
    private static final String[] REMARKS = {
            "Sysmex XN-1000 Haematology Analyser", "Cobas c311 Chemistry Analyser",
            "Architect i2000 Immunoassay Analyser", "ABL800 Blood Gas Analyser",
            "Urisys 2400 Urine Analyser", "BacT ALERT 3D Culture System"
    };

    /** The parameter as the machine reports it (Machine Para. Name). */
    private static final String[] PARA_NAMES = {
            "HGB Channel", "WBC Channel", "PLT Channel",
            "Creatinine Channel", "Glucose Channel", "Bilirubin Channel"
    };

    /** The lab-side parameter the machine channel maps to (Parameter Name). */
    private static final String[] PARAMETER_NAMES = {
            "Haemoglobin", "White Blood Cell Count", "Platelet Count",
            "Serum Creatinine", "Random Blood Sugar", "Total Bilirubin"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: the five visible boxes — master <b>Code</b> / <b>Remark</b> and the detail
     * <b>Machine Para. Code</b> / <b>Machine Para. Name</b> / <b>Parameter Name</b> — whatever they are called here.
     *
     * <p>Each match is CLAIMED ({@code used}) so two roles can never resolve to the same box, and the DETAIL
     * fields are resolved FIRST: "Machine Para. Code" answers a loose {@code /code/} test, so letting the master
     * Code pick first would fill the parameter code and leave the real Code empty.</p>
     */
    private static final String FIND_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const lbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const skip=m=>/colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m||'');"
            // select2 renders its OWN search box (id s2id_autogen*) right beside the real <select>, and it carries the
            // same "Parameter Name*" label. Typing into it opens the drop-down AND its full-page #select2-drop-mask,
            // which then intercepts every later click (that is what blocked Add and Submit on the first run).
            // Only the select2 CHROME is excluded — the real <select> it wraps carries class "select2-offscreen",
            // so a blanket /select2/ class test throws away the very field we need (Parameter Name).
            + " const s2=e=>/^s2id_autogen/i.test(e.id||'') || (e.tagName!=='SELECT' && /select2/i.test(e.className||''));"
            + " const boxes=[...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !s2(e) && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const flat=e=>mdl(e).replace(/[^a-z]/gi,''); const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            // Exact ng-models first (confirmed on this screen), then label, then a loose model/placeholder fallback.
            // --- detail line before the master: "Machine Para. Code" also answers a loose /code/ test ---
            + " const mpCodeEl = pick(e=>/^LabMachineMst\\.MachineParameterCode$/.test(mdl(e)), e=>/machine\\s*para\\.?\\s*code/i.test(lbl(e)), e=>/paracode$/i.test(flat(e)));"
            + " const mpNameEl = pick(e=>/^LabMachineMst\\.MachineParameterName$/.test(mdl(e)), e=>/machine\\s*para\\.?\\s*name/i.test(lbl(e)), e=>/paraname$/i.test(flat(e)));"
            + " const pNameEl  = pick(e=>/^LabMachineMst\\.TestParameterId$/.test(mdl(e)), e=>e.tagName==='SELECT' && /parameter\\s*name/i.test(lbl(e)), e=>/parametername|testparameterid$/i.test(flat(e)));"
            // --- master ---
            + " const codeEl = pick(e=>/^LabMachineMst\\.Code$/.test(mdl(e)), e=>/^code\\b/i.test(lbl(e)) && !/para/i.test(lbl(e)), e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)));"
            + " const remEl  = pick(e=>/^LabMachineMst\\.Description$/.test(mdl(e)), e=>/^(remark|description)/i.test(lbl(e)), e=>/\\.(description|remark)$/i.test(mdl(e)), e=>/description|remark/i.test(mdl(e)));";

    /**
     * JS helper: commit a value to an input/textarea, or pick the first real option of a select.
     * Selects here are select2-wrapped, so after Angular has taken the new value the select2 widget is told to
     * re-render and CLOSE — leaving it open leaves the drop-mask over the page.
     */
    private static final String SET_EL =
            " const $=window.jQuery;"
            + " const setEl=(e,v)=>{ if(!e) return '(no field)';"
            + "   if(e.tagName==='SELECT'){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
            + "     if(i<0) return '(no options)'; e.selectedIndex=i;"
            + "     e.dispatchEvent(new Event('change',{bubbles:true})); try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "     if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).select2('close'); }catch(x){} }"
            + "     return norm(e.options[i].textContent); }"
            + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
            + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };";

    /** JS helper: shut any select2 drop-down + its full-page mask, so a real click can reach the button underneath. */
    private static final String CLOSE_SELECT2 =
            "() => { try{ if(window.jQuery){ jQuery('select').each(function(){ try{ jQuery(this).select2('close'); }catch(e){} }); } }catch(e){}"
            + " document.querySelectorAll('#select2-drop-mask,.select2-drop-mask').forEach(m=>{ m.style.display='none'; });"
            + " document.querySelectorAll('.select2-drop-active,.select2-drop').forEach(d=>{ d.classList.remove('select2-drop-active'); d.style.display='none'; });"
            + " document.querySelectorAll('.select2-dropdown-open').forEach(d=>d.classList.remove('select2-dropdown-open'));"
            + " try{ document.activeElement && document.activeElement.blur(); }catch(e){} }";

    /** Close any open select2 before a real click — its mask sits over the whole page and swallows the click. */
    private void closeSelect2() {
        try { page.evaluate(CLOSE_SELECT2); } catch (Exception ignore) { }
        waitForAngular(300);
    }

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
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*lab\\s*machines?\\s*$/i.test(norm(x.textContent))"
                        + "   || (/machine/i.test(x.getAttribute('href')||'') && /lab/i.test(x.getAttribute('href')||'')));"
                        + " if(!a) return ''; a.id='__lmMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("LabMachine.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__lmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("LabMachine.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__lmMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__lmMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("LabMachine.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("LabMachine.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Lab Machine screen — URL plus something real on the page (the Code/Remark pair, or a list with an
     * Add button). The leftover "Transfer" screen this build sometimes serves renders a Code box and a Submit but
     * NO Remark, so the field gate deliberately demands BOTH.
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        if (!u.contains("machine")) return false;
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
     * Click the list <b>Add</b> button; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        // Already showing both master boxes -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__lmAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__lmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("LabMachine.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__lmAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter the master <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillMaster(int attempt) {
        lastCode = nextCode("LM", attempt);
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS + SET_EL
                + " const cd=setEl(codeEl, a.code); const rm=setEl(remEl, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res;
    }

    /**
     * Enter the detail line — <b>Machine Para. Code</b>, <b>Machine Para. Name</b>, <b>Parameter Name</b>.
     * Parameter Name is handled as a drop-down too (first real option), since sibling screens render that column
     * as a lookup rather than free text.
     */
    public String fillParameter(int attempt) {
        lastParaCode = nextCode("MP", attempt + 3);
        lastParaName = PARA_NAMES[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % PARA_NAMES.length)];
        lastParameterName = PARAMETER_NAMES[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % PARAMETER_NAMES.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS + SET_EL
                + " const pc=setEl(mpCodeEl, a.paraCode); const pn=setEl(mpNameEl, a.paraName); const nm=setEl(pNameEl, a.parameterName);"
                + " return 'Machine Para. Code='+pc+' | Machine Para. Name='+pn+' | Parameter Name='+nm"
                + "   +' | [models: '+(mpCodeEl?(mpCodeEl.getAttribute('ng-model')||'?'):'none')+' / '+(mpNameEl?(mpNameEl.getAttribute('ng-model')||'?'):'none')"
                + "   +' / '+(pNameEl?(pNameEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("paraCode", lastParaCode, "paraName", lastParaName, "parameterName", lastParameterName));
        waitForAngular(500);
        // A lookup may have resolved to a different label than the text typed — report what actually landed.
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Parameter Name=([^|]+)\\|").matcher(res + "|");
        if (m.find()) { String v = m.group(1).trim(); if (!v.startsWith("(")) lastParameterName = v; }
        return res;
    }

    /** How many detail rows the parameter list currently holds — the before/after proof that Add appended one. */
    public int detailRowCount() {
        // The detail list is an ng-repeat over LMDetailsList — count THAT, not "every tbody tr on the page"
        // (the master list grid is still in the DOM behind the form and swamps the delta).
        Object r = page.evaluate("() => { const q=s=>document.querySelectorAll(s).length;"
                + " let n=q('tr[ng-repeat*=\"Details\"],tr[data-ng-repeat*=\"Details\"],tr[ng-repeat*=\"LMDetails\"]');"
                + " if(!n) n=q('tbody tr[ng-repeat],tbody tr[data-ng-repeat]');"
                + " if(!n) n=q('.ui-grid-row');"
                + " if(!n) n=[...document.querySelectorAll('table')].reduce((s,t)=>s+t.querySelectorAll('tbody tr').length,0);"
                + " return n; }");
        try { return (int) Double.parseDouble(String.valueOf(r)); } catch (Exception e) { return -1; }
    }

    /**
     * Click the DETAIL <b>Add</b> (appends the Machine Para. / Parameter Name row to the list) and report the
     * row-count delta. Prefers a button whose ng-click looks like an add-to-list handler; otherwise the LAST
     * visible Add, which on these master-detail forms is the one under the detail fields rather than the list Add.
     */
    public String clickAddDetail() {
        closeSelect2();
        int before = detailRowCount();
        // Handler on this screen is AddLabMachineDetails(LMDetailsList) — /add.*detail/ matches it; the
        // last-visible-Add fallback stays for siblings that name theirs differently.
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const adds=[...document.querySelectorAll('button,a,input[type=button]')].filter(b=>b.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(b.textContent||b.value)));"
                + " const b=adds.find(x=>/add.*(detail|para|row|item|field)/i.test(x.getAttribute('ng-click')||'')) || adds[adds.length-1];"
                + " if(!b) return 'no-add'; b.id='__lmAddDetail'; return (b.getAttribute('ng-click')||'add'); }");
        String how = String.valueOf(r);
        if ("no-add".equals(how)) { System.out.println("LabMachine.clickAddDetail: no detail Add button"); return "no detail Add button found"; }
        // REAL click — these handlers hang off ng-click and a synthetic dispatch can miss the digest.
        try { page.locator("#__lmAddDetail").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("LabMachine.clickAddDetail: real click failed - " + e.getMessage());
            try { page.evaluate("() => { const b=document.getElementById('__lmAddDetail'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const e=document.getElementById('__lmAddDetail'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        int after = detailRowCount();
        return "Add clicked {" + how + "} - parameter rows " + before + " -> " + after
                + (after > before ? " (row appended)" : " (NO new row)");
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts the master Code/Remark first —
     * these forms re-render and can blank a field between fill and click. Falls back to invoking the form's
     * ng-submit handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        closeSelect2();
        page.evaluate("() => { window.__lmToasts=[]; if(window.__lmObs) window.__lmObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__lmToasts.includes(t)) window.__lmToasts.push(t); }); };"
                + " window.__lmObs=new MutationObserver(grab); window.__lmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
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

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS + SET_EL
                + " setEl(codeEl, a.code); setEl(remEl, a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__lmSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("LabMachine submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__lmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("LabMachine submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__lmSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__lmToasts||[]).some(a=>/machine|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("LabMachine save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__lmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
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
                + "   +(e.tagName==='SELECT'?(' opts='+e.options.length):'')"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** A code in the format this screen's own rows use — numeric unless the grid showed alphanumeric codes. */
    private String nextCode(String prefix, int attempt) {
        long n = Math.abs((System.nanoTime() + attempt * 7919L) % 100000);
        return numericCodes ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                            : prefix + String.format("%05d", n);
    }
}
