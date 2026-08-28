package com.kpj.pages.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Red Cell Serology Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Red Cell Serology Master</b> → <b>Add</b>
 * → select <b>Blood Group</b> → select <b>Red Cell Serology Group</b> + <b>Result</b> for <b>Cell Grouping</b>
 * → click <b>Add</b> (that section) → select <b>Red Cell Serology Group</b> + <b>Result</b> for
 * <b>Serum Grouping</b> → click <b>Add</b> (that section) → <b>Submit</b> → success toast.</p>
 *
 * <p>This screen's exact ng-model names and section headings were not yet confirmed live. Master-detail with
 * TWO independent line-item sections (Cell Grouping / Serum Grouping), each its own Group+Result select pair
 * plus its own Add button — {@link #fillGroupingSection(String)} scopes every query to the section's own
 * container (found by its heading text) so the two sections' controls are never confused with each other.
 * {@link #describeForm()} dumps every visible field/button (with a best-effort nearby-heading label) so a
 * naming mismatch is diagnosable from the report without a second live session.</p>
 */
public class RedCellSerologyMaster extends BasePage {

    public RedCellSerologyMaster(Page page) { super(page); }

    /** Best-effort direct-route fallback if the menu link cannot be found at all. */
    public static final String ROUTE = "#/RedCellSerologyMaster";
    public String lastBloodGroup = "", lastCellGroup = "", lastCellResult = "", lastSerumGroup = "", lastSerumResult = "";
    private String menuHref = "";
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Blood Bank" — a prefix match can expand a different (similarly named) menu.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1000);
            // EXACT text match ONLY, anchored so "Red Cell Serology Master" never matches the sibling
            // "Red Cell Serology Group" link (both live on the same Blood Bank submenu).
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const links=[...document.querySelectorAll('a[href]')];"
                    + " const a = links.find(x=>/^\\s*red\\s*cell\\s*serology\\s*master\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__rcmMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("RedCellSerologyMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            if (href.toString().startsWith("#")) menuHref = href.toString();
            try { page.locator("#__rcmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("RedCellSerologyMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__rcmMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("RedCellSerologyMaster.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Red Cell Serology Master screen — verified by the rendered HEADER text (anchored to "Master" so
     * it never matches the sibling "Red Cell Serology Group" screen), plus an Add button or a Blood Group
     * control actually present.
     */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/redcellserologymaster/i.test(location.hash||'');"
                + " if(!/red\\s*cell\\s*serology\\s*master/i.test(hdr) && !urlOk) return false;"
                + " if([...document.querySelectorAll('select,input')].some(e=>e.offsetParent!==null)) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }");
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

    /** Click <b>Add</b> to open the Red Cell Serology Master form. Real-click (polls up to ~7.5s). */
    public String clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__rcmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__rcmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("RedCellSerologyMaster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__rcmAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    /**
     * Select <b>Blood Group</b> — matched by its own LABEL text. {@code attempt} cycles through the real
     * (non-placeholder) options instead of always taking the first: this screen has no Code/Name text field,
     * so Blood Group is the only field that can distinguish one submission from another — a retry after
     * "already exists" (this Blood Group already has a Red Cell Serology Master row) must pick a DIFFERENT
     * option, not resubmit the same one.
     */
    public String selectBloodGroup(int attempt) {
        Object r = page.evaluate("(idx) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
                + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null);"
                + " const e=sels.find(x=>/blood\\s*group/i.test(labelOf(x)));"
                + " if(!e) return '(not found)';"
                + " const real=[...e.options].map((o,i)=>({o,i})).filter(p=>p.o.value && !/^-*\\s*select/i.test(norm(p.o.textContent)));"
                + " if(!real.length) return '(no-options)';"
                + " const pick=real[idx % real.length];"
                + " e.selectedIndex=pick.i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + " return norm(pick.o.textContent); }", attempt);
        waitForAngular(400);
        lastBloodGroup = r == null ? "" : r.toString();
        return lastBloodGroup;
    }

    /**
     * Fill ONE grouping section — <b>Red Cell Serology Group</b> + <b>Result</b> (both selects, first real
     * option), then click that section's own <b>Add</b> button. Confirmed live via {@link #describeForm()}:
     * there is no "Cell Grouping" / "Serum Grouping" text anywhere in the DOM (the two rows share the same
     * "Red Cell Serology Group*" / "Result*" labels) — the rows are only distinguishable by their exact
     * ng-model/ng-click, so this targets those directly instead of any heading-text heuristic:
     * <ul>
     *   <li>Cell row: {@code ng-model="RedCellSerologyGroup1.id"} / {@code "Result1.id"},
     *       Add button {@code ng-click="fnIAddCellGroupingforCell(CellGrouping)"}</li>
     *   <li>Serum row: {@code ng-model="RedCellSerologyGroup2.id"} / {@code "Result2.id"},
     *       Add button {@code ng-click="fnIAddCellGroupingforSerum(SerumGrouping)"}</li>
     * </ul>
     *
     * @param sectionRe text containing "cell" (case-insensitive) selects the Cell row, anything else the Serum row
     * @return "Group=... | Result=... | Add=..." summary
     */
    public String fillGroupingSection(String sectionRe) {
        boolean isCell = sectionRe != null && sectionRe.toLowerCase().contains("cell");
        Object r = page.evaluate("(cell) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const suffix=cell?'1':'2';"
                + " const grpModel='RedCellSerologyGroup'+suffix+'.id', resModel='Result'+suffix+'.id';"
                + " const addFrag=cell?'fnIAddCellGroupingforCell':'fnIAddCellGroupingforSerum';"
                + " const grpSel=[...document.querySelectorAll('select[ng-model=\"'+grpModel+'\"]')].find(e=>e.offsetParent!==null);"
                + " const resSel=[...document.querySelectorAll('select[ng-model=\"'+resModel+'\"]')].find(e=>e.offsetParent!==null);"
                + " const pick=(e)=>{ if(!e) return '(not found)'; const cur=e.options[e.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select/i.test(norm(cur.textContent))) return norm(cur.textContent)+' (kept)';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '(no-options)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + "   return norm(e.options[i].textContent); };"
                + " const gp=pick(grpSel); const rs=pick(resSel);"
                // Tag the Add button rather than clicking it here — a raw JS .click() does not reliably fire
                // this app's ng-click handlers (see DevHisBase real-click convention); the caller performs a
                // REAL Playwright click on this id instead.
                + " document.querySelectorAll('#__rcmSectionAdd').forEach(x=>x.removeAttribute('id'));"
                + " const addBtn=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>(x.getAttribute('ng-click')||'').includes(addFrag) && x.offsetParent!==null);"
                + " let addTag='no';"
                + " if(addBtn){ addBtn.id='__rcmSectionAdd'; addTag='yes'; }"
                + " return 'Group='+gp+' | Result='+rs+' | AddTag='+addTag; }", isCell);
        waitForAngular(200);
        String summary = r == null ? "" : r.toString();
        String addResult;
        if (summary.contains("AddTag=yes")) {
            try {
                page.locator("#__rcmSectionAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
                addResult = "clicked";
            } catch (Exception e) {
                addResult = "click failed - " + e.getMessage();
            }
            page.evaluate("() => { const e=document.getElementById('__rcmSectionAdd'); if(e) e.removeAttribute('id'); }");
        } else {
            addResult = "(no Add button in section)";
        }
        waitForAngular(600);
        return summary.replaceFirst("AddTag=\\w+", "Add=" + addResult);
    }

    /**
     * Click <b>Submit</b> and return the toast. Falls back to invoking the form's ng-submit handler from the
     * scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__rcmToasts=[]; if(window.__rcmObs) window.__rcmObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rcmToasts.includes(t)) window.__rcmToasts.push(t); }); };"
                + " window.__rcmObs=new MutationObserver(grab); window.__rcmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("redcellserology") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__rcmSubmit'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }");
        System.out.println("RedCellSerologyMaster submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__rcmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("RedCellSerologyMaster submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__rcmSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__rcmToasts||[]).some(a=>/serology|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("RedCellSerologyMaster save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__rcmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
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
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,60).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+'['+(e.getAttribute('ng-click')||'')+']').filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
