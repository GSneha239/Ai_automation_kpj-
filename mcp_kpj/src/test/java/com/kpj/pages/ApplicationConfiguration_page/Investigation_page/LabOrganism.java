package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Organism</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>Lab Organism</b> → <b>Add</b> →
 * enter <b>Code*</b> + <b>Remark*</b> → tick a check box in the grid inside
 * {@code #LabOrganismForm &gt; div:nth-of-type(2)} → <b>Save</b> → success toast.</p>
 *
 * <p>The grid tick is a REAL Playwright click, not a synthetic {@code checked=true}: these rows hang their
 * selection off an {@code ng-click}/{@code ng-change}, and assigning the property skips the digest so the row is
 * never actually claimed and the server rejects the save.</p>
 *
 * <p>Sibling note: the neighbouring Lab Machine screen wraps a drop-down in select2 whose mask swallows later
 * clicks — {@link #closeSelect2()} is kept here for the same reason.</p>
 */
public class LabOrganism extends BasePage {

    public LabOrganism(Page page) { super(page); }

    public static final String ROUTE = "#/LabOrganism";
    /** The grid container the user pointed at: the 2nd div of the form. */
    public static final String GRID = "#LabOrganismForm > div:nth-of-type(2)";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "", lastTicked = "";
    /** Whether the screen's existing rows use all-numeric codes (set by {@link #sampleCodeStyle()}). */
    private boolean numericCodes = true;
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Organisms a microbiology lab would actually register on this screen (Remark). */
    private static final String[] REMARKS = {
            "Escherichia Coli", "Staphylococcus Aureus", "Klebsiella Pneumoniae",
            "Pseudomonas Aeruginosa", "Streptococcus Pneumoniae", "Candida Albicans"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: the visible <b>Code</b> and <b>Remark</b> boxes, whatever they are called here. Each match is
     * CLAIMED ({@code used}) so the two roles can never resolve to the same input. select2 chrome
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
            + " const boxes=[...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !s2(e) && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            + " const codeEl = pick(e=>/^LabOrganism[A-Za-z]*\\.Code$/i.test(mdl(e)), e=>/^code\\b/i.test(lbl(e)), e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)), e=>/^\\s*code\\s*$/i.test(plc(e)));"
            + " const remEl  = pick(e=>/^LabOrganism[A-Za-z]*\\.(Description|Remark)$/i.test(mdl(e)), e=>/^(remark|description)/i.test(lbl(e)), e=>/\\.(description|remark)$/i.test(mdl(e)), e=>/description|remark/i.test(mdl(e)));";

    /** JS helper: commit a value to an input/textarea, or pick the first real option of a select. */
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

    /** JS helper: shut any select2 drop-down + its full-page mask, so a real click can reach what is underneath. */
    private static final String CLOSE_SELECT2 =
            "() => { try{ if(window.jQuery){ jQuery('select').each(function(){ try{ jQuery(this).select2('close'); }catch(e){} }); } }catch(e){}"
            + " document.querySelectorAll('#select2-drop-mask,.select2-drop-mask').forEach(m=>{ m.style.display='none'; });"
            + " document.querySelectorAll('.select2-drop-active,.select2-drop').forEach(d=>{ d.classList.remove('select2-drop-active'); d.style.display='none'; });"
            + " document.querySelectorAll('.select2-dropdown-open').forEach(d=>d.classList.remove('select2-dropdown-open'));"
            + " try{ document.activeElement && document.activeElement.blur(); }catch(e){} }";

    /** JS helper: the check boxes of the grid the user pointed at, with a widening fallback. */
    private static final String FIND_BOXES =
            " const inGrid=()=>{ const g=document.querySelector('#LabOrganismForm') ;"
            + "   let scope=null;"
            + "   if(g){ const divs=[...g.children].filter(c=>c.tagName==='DIV'); scope=divs[1]||divs[0]||g; }"
            + "   const from=n=>n?[...n.querySelectorAll('input[type=checkbox]')].filter(e=>e.offsetParent!==null):[];"
            + "   let cbs=from(scope); if(!cbs.length) cbs=from(g);"
            // No form id (or an empty one) -> any check box that sits inside a grid/table, minus the select-all header.
            + "   if(!cbs.length) cbs=[...document.querySelectorAll('table input[type=checkbox], .ui-grid input[type=checkbox], [ui-grid] input[type=checkbox]')].filter(e=>e.offsetParent!==null);"
            + "   if(!cbs.length) cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(e=>e.offsetParent!==null);"
            + "   return cbs.filter(e=>!/selectall|checkall|allrows/i.test((e.getAttribute('ng-model')||'')+(e.id||'')));"
            + " };";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Close any open select2 before a real click — its mask sits over the whole page and swallows the click. */
    private void closeSelect2() {
        try { page.evaluate(CLOSE_SELECT2); } catch (Exception ignore) { }
        waitForAngular(300);
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
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*lab\\s*organisms?\\s*$/i.test(norm(x.textContent))"
                        + "   || /organism/i.test(x.getAttribute('href')||''));"
                        + " if(!a) return ''; a.id='__loMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("LabOrganism.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__loMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("LabOrganism.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__loMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__loMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("LabOrganism.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("LabOrganism.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Lab Organism screen — URL plus something real on the page (the Code/Remark pair, or a list with an
     * Add button). The leftover "Transfer" screen this build sometimes serves renders a Code box and a Submit but
     * NO Remark, so the field gate deliberately demands BOTH.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("organism")) return false;
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
        // Already showing both boxes -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__loAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__loAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("LabOrganism.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__loAdd'); if(e) e.removeAttribute('id'); }");
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
        lastCode = numericCodes ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                                : "LO" + String.format("%05d", n);
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
     * Tick one check box in the grid inside {@code #LabOrganismForm > div:nth-of-type(2)} with a REAL click, and
     * verify it actually went checked.
     *
     * <p>The grid loads ASYNC after Add, so this polls for a box before clicking. A synthetic {@code checked=true}
     * is deliberately NOT used: the row's selection hangs off an {@code ng-click}/{@code ng-change} that a
     * property assignment never fires, which is how a "ticked" row still saves as unselected.</p>
     */
    public String tickGridCheckbox() {
        closeSelect2();
        // The grid is populated by its own call — wait for a real check box rather than failing on an empty grid.
        try {
            page.waitForFunction("() => {" + FIND_BOXES + " return inGrid().length > 0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("LabOrganism.tickGridCheckbox: no check box appeared within 15s"); }

        Object info = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_BOXES
                + " const cbs=inGrid(); if(!cbs.length) return JSON.stringify({found:0});"
                + " const cb=cbs.find(e=>!e.checked && !e.disabled) || cbs[0]; cb.id='__loCb';"
                // Row text so the report names WHICH organism/test was ticked, not just "a box".
                + " const row=cb.closest('tr,.ui-grid-row,.row,li,div'); const txt=norm(row?row.textContent:'').slice(0,60);"
                + " return JSON.stringify({found:cbs.length, model:(cb.getAttribute('ng-model')||''), click:(cb.getAttribute('ng-click')||cb.getAttribute('ng-change')||''), row:txt, was:cb.checked}); }");
        String meta = String.valueOf(info);
        if (meta.contains("\"found\":0")) {
            return "NO check box found in " + GRID + " (or anywhere on the form)";
        }
        // REAL click — the row's ng-click/ng-change must fire; setting .checked skips the digest.
        try { page.locator("#__loCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("LabOrganism.tickGridCheckbox: real click failed - " + e.getMessage());
            try { page.locator("#__loCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000)); }
            catch (Exception e2) { System.out.println("LabOrganism.tickGridCheckbox: forced click failed too - " + e2.getMessage()); }
        }
        waitForAngular(900);
        Object after = page.evaluate("() => { const e=document.getElementById('__loCb'); const c=!!(e && e.checked); if(e) e.removeAttribute('id'); return c; }");
        boolean checked = Boolean.TRUE.equals(after);
        lastTicked = extract(meta, "row");
        return (checked ? "Ticked" : "NOT ticked (click did not check the box)")
                + " [" + meta.replaceAll("[{}\"]", "") + "]";
    }

    /** Was the last {@link #tickGridCheckbox()} actually successful? */
    public boolean lastTickOk(String result) { return result.startsWith("Ticked"); }

    private static String extract(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Click <b>Save</b> (accepts "Submit" too) and return the toast. Re-asserts Code/Remark first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit handler
     * from the scope when there is no clickable button.
     */
    public String saveAndGetToast() {
        closeSelect2();
        page.evaluate("() => { window.__loToasts=[]; if(window.__loObs) window.__loObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__loToasts.includes(t)) window.__loToasts.push(t); }); };"
                + " window.__loObs=new MutationObserver(grab); window.__loObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("organism") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS + SET_EL
                + " setEl(codeEl, a.code); setEl(remEl, a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__loSave'; return 'click:'+(b.getAttribute('ng-click')||norm(b.textContent||b.value)); }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("LabOrganism save => " + how);
        if (String.valueOf(how).startsWith("click")) {
            try { page.locator("#__loSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("LabOrganism save: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__loSave'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__loToasts||[]).some(a=>/organism|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("LabOrganism save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__loToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label), the grid check boxes and the
     * visible button labels with their ng-click handlers. Included in the FAIL text so a miss is diagnosable
     * without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_BOXES
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
                + " const form=document.querySelector('#LabOrganismForm');"
                + " const kids=form?[...form.children].map(c=>c.tagName.toLowerCase()+'.'+(c.className||'').split(' ')[0]).slice(0,8).join(' , '):'(no #LabOrganismForm)';"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  GRID CHECKBOXES: '+inGrid().length+'  ||  FORM CHILDREN: '+kids+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
