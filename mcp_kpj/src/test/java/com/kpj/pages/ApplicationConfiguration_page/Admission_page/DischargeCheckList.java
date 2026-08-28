package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Discharge CheckList</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Discharge CheckList</b> → <b>Add</b> →
 * enter <b>Code</b> + <b>Remark</b> → tick the <b>Mandatory</b> checkbox → <b>Submit</b> → success toast.</p>
 *
 * <p>Same family (and the same discovery-driven wiring) as
 * {@link BedReleasedChecklist} — the route and the field ng-models are mined at runtime because the
 * Application Configuration menu links are frequently hidden in a collapsed sidebar.</p>
 */
public class DischargeCheckList extends BasePage {

    public DischargeCheckList(Page page) { super(page); }

    /** Route — the menu href is re-mined at runtime; this is the known default (config menu links are often hidden). */
    public static String ROUTE = "#/DischargeCheckListMasterList";
    public String lastCode = "";
    public String lastRemark = "";

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const grids=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                + " out.push('tables='+grids.length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " grids.slice(0,4).forEach((t,i)=>{ const h=norm((t.querySelector('thead')||{}).innerText||'').slice(0,120); const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; out.push(' grid'+i+' rows='+rows+' headers=['+h+']'); });"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'); });"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null).map(c=>(c.getAttribute('ng-model')||'?')+' lbl=\"'+labelOf(c)+'\"').filter((v,i,a)=>a.indexOf(v)===i);"
                + " out.push('checkboxes=\\n  '+cbs.slice(0,10).join('\\n  '));"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    /** Expand <b>Application Configuration</b> → (Admission) → <b>Discharge CheckList</b>; recovers the route by
     *  mining the menu href when the link is hidden (collapsed sidebar). */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DischargeCheckList.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Discharge CheckList" link if present (also capture its route)
        Object dcHref = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/discharge\\s*check\\s*list/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__dclMenu'; return a.getAttribute('href')||'link'; }");
        if (dcHref != null && !dcHref.toString().isEmpty()) {
            try { page.locator("#__dclMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DischargeCheckList.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (dcHref.toString().contains("#/")) ROUTE = dcHref.toString().substring(dcHref.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text/href matches discharge + checklist
        if (ROUTE.isEmpty()) {
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/discharge\\s*check\\s*list/i.test(norm(x.textContent)) || /discharge.*check|dischargecheck/i.test(x.getAttribute('href')||''));"
                    + " return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("DischargeCheckList.nav: mined href => " + href);
            if (href != null && href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // direct-route fallback
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("DischargeCheckList.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /discharge\\s*check\\s*list/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Click the element tagged with {@code id}: a REAL Playwright click first (so ng-click/ng-change fire like a
     *  user), falling back to a DOM click when the sticky header overlays the control and swallows the real click
     *  — which it does on this screen. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("DischargeCheckList." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("DischargeCheckList." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). The header overlays the page on this
     *  screen and swallows real clicks, so an intercepted click falls back to a direct DOM click. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const body=x=>!x.closest('header,.main-header,nav,.navbar,.sidebar,.skin-blue .main-sidebar');"
                    + " const cands=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null && body(x));"
                    + " const b=cands.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                    + " || cands.find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent))); if(!b) return false;"
                    + " b.id='__dclAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DischargeCheckList.clickAdd: Add button not found"); return false; }
        robustClick("__dclAdd", "clickAdd");
        waitForAngular(1200);
        return true;
    }

    /** True once the Add form (a Code field + a Submit button) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=[...document.querySelectorAll('label,.control-label')].some(l=>l.offsetParent!==null && /^code\\*?$/i.test(norm(l.textContent).replace(/\\*/g,'').trim()));"
                + " const sub=[...document.querySelectorAll('button,input[type=submit]')].some(b=>b.offsetParent!==null && /^\\s*submit\\s*$/i.test(norm(b.textContent||b.value)));"
                + " return lbl && sub; }"));
    }

    /** Enter <b>Code</b> (unique) + <b>Remark</b> (a realistic discharge-checklist item, by label).
     *  Returns a summary incl. the discovered ng-models. */
    public String fillCodeAndRemark() {
        String code = "DC" + String.format("%06d", Math.abs(System.nanoTime() % 1000000));
        // Realistic remark — a genuine discharge checklist item, not a generated placeholder.
        String[] pool = { "Discharge Summary Signed", "Medication Reconciliation Done", "Final Bill Settled",
                          "Follow Up Appointment Given", "Patient Belongings Returned", "Cannula Removed",
                          "Discharge Medication Handed Over", "Home Care Instructions Explained" };
        String remark = pool[(int) (Math.abs(System.nanoTime()) % pool.length)];
        lastCode = code;
        lastRemark = remark;
        Object r = page.evaluate("([code,remark]) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const byNg=(re)=>[...document.querySelectorAll('input:not([type=hidden]),textarea')].find(e=>e.offsetParent!==null && re.test(e.getAttribute('ng-model')||''));"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; const p=l.parentElement?l.parentElement.querySelector('input:not([type=hidden]),textarea'):null; if(p&&p.offsetParent!==null) return p; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const codeF=byNg(/DischargeCheckListMaster\\.code$/i) || labelField(['Code','Checklist Code','Discharge CheckList Code']);"
                + " const remF=byNg(/DischargeCheckListMaster\\.description$/i) || labelField(['Remark','Remarks','Description']);"
                + " const cd=setInp(codeF, code); const rm=setInp(remF, remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg='+(codeF?codeF.getAttribute('ng-model'):'-')+' | remNg='+(remF?remF.getAttribute('ng-model'):'-'); }",
                java.util.Arrays.asList(code, remark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Tick the <b>Mandatory</b> checkbox on the Add form (else the first visible form checkbox, excluding the
     *  grid filter checkboxes). Real-click so the Angular binding fires. Returns the label / ng-model, or "". */
    public String selectMandatoryCheckbox() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div,label'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } return norm(t||(e.parentElement?e.parentElement.textContent:'')).slice(0,30); };"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/logbookfilters|colfilter/i.test(c.getAttribute('ng-model')||''));"
                + " if(!cbs.length) return 'none';"
                // the app's own ng-model spells it "ismandantory"
                + " let cb=cbs.find(c=>/ismandantory|mandat|require/i.test(labelOf(c)+' '+(c.getAttribute('ng-model')||''))) || cbs.find(c=>!c.checked) || cbs[0];"
                + " cb.id='__dclMandCb'; return (labelOf(cb)||(cb.getAttribute('ng-model')||'checkbox')); }");
        String label = String.valueOf(r);
        if ("none".equals(label)) { System.out.println("selectMandatoryCheckbox: no form checkbox found"); return ""; }
        robustClick("__dclMandCb", "selectMandatoryCheckbox");
        waitForAngular(300);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__dclMandCb'); return !!(c && c.checked); }"));
        return checked ? label : "";
    }

    /** Did the row really save? The list grid is paged (a new row lands on the LAST page), so ask the master API
     *  itself — {@code /api/DischargeCheckListMaster/fetch} — for the code, and report its mandatory flag too. */
    public String fetchSavedRow(String code) {
        for (int i = 0; i < 4; i++) {
            // cache-bust + no-store: the plain URL is served from the browser cache and answers with the
            // PRE-save list, which makes a row that really saved look missing.
            Object r = page.evaluate("async (code) => { try {"
                    + " const res = await fetch('/api/DischargeCheckListMaster/fetch?_cb=' + Date.now(),"
                    + "   {method:'POST', cache:'no-store', headers:{'Content-Type':'application/json','Accept':'application/json'},"
                    + "    body: JSON.stringify({ExecFlag:'GRID'})});"
                    + " const d = await res.json(); const row=(d||[]).find(x=>(''+x.code).trim()===code);"
                    + " return row ? ('id='+row.id+' code='+row.code+' description='+row.description+' mandatory='+row.ismandantory) : ''; }"
                    + " catch(e) { return 'fetch failed: '+e; } }", code);
            String s = r == null ? "" : r.toString();
            if (!s.isEmpty()) return s;
            page.waitForTimeout(800);
        }
        return "";
    }

    /** The save API's status + body, captured while Submit runs (a bare "Message Not Found." toast says nothing). */
    public String lastSaveApi = "";
    /** The screen while the message is still up — the later checks outlive the dialog. */
    public byte[] toastPng;

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> {
            // the save itself: POST /api/DischargeCheckListMaster/IUD (its body carries the message the app toasts)
            if (resp.url().toLowerCase().contains("dischargechecklistmaster/iud")) hold[0] = resp;
        });
        Object tagged = page.evaluate("() => { window.__dclToasts=[]; if(window.__dclObs) window.__dclObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dclToasts.includes(t)) window.__dclToasts.push(t); }); };"
                + " window.__dclObs=new MutationObserver(grab); window.__dclObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const bs=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null);"
                + " const b=bs.find(x=>/fnIUDDischargeCheckList/i.test(x.getAttribute('ng-click')||'')) || bs.find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim())); if(!b) return false; b.id='__dclSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__dclSubmit", "submit");
        // Shoot the MOMENT the message is up: it is gone long before the save verification finishes, and a report
        // whose toast step shows a bare grid proves nothing.
        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("submit: toast screenshot failed - " + e.getMessage()); break; }
            } else page.waitForTimeout(200);
        }
        if (toastPng == null) System.out.println("submit: no message was ever on screen to screenshot");
        try {
            // "message not found" is in the list because that is what THIS screen actually pops — without it the
            // wait burns its full timeout on every run.
            page.waitForFunction("() => (window.__dclToasts||[]).some(a=>/checklist|check list|master|saved|success|added|updated|please|select|enter|required|exist|error|not found/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dclToasts||[]).includes(t)) (window.__dclToasts=window.__dclToasts||[]).push(t); }); }");
        }
        // Give a later success toast a chance to render behind the dialog this screen pops.
        page.waitForTimeout(2000);
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dclToasts||[]).includes(t)) (window.__dclToasts=window.__dclToasts||[]).push(t); }); }");
        System.out.println("submit: toasts => " + page.evaluate("() => JSON.stringify(window.__dclToasts||[])"));
        // prefer the Submit's own save toast ("... details added successfully.") over any incidental status toast.
        Object r = page.evaluate("() => { const a=window.__dclToasts||[]; return a.find(x=>/details added|added successfully|master saved|saved successfully/i.test(x)) || a.find(x=>/status updated|success|added|updated/i.test(x)) || a.find(x=>x) || ''; }");
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
