package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Side Amenities</b> — configuration screen Page Object.
 *
 * <p>Flow: expand <b>Application Configuration</b> → (Admission) → <b>Bed Side Amenities</b> → enter <b>Code</b> +
 * <b>Remark</b> → tick any one record in the table → <b>Submit</b> → success toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime by {@link #dumpScreen()} /
 * {@link #dumpAddForm()} and then wired.</p>
 */
public class BedSideAmenities extends BasePage {

    public BedSideAmenities(Page page) { super(page); }

    /** Route (discovered): the Bed Side Amenities master screen. */
    public static String ROUTE = "#/BEDAMENITIES";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic bed-side amenity names — the Remark must read like real data, not "Auto amenity <CODE>". */
    private static final String[] REMARKS = {
            "Bedside Locker", "Overbed Table", "Reading Lamp", "Nurse Call Button",
            "Oxygen Outlet", "Bedside Chair", "Privacy Curtain", "Patient Television"};

    /** Diagnostic: list every checkbox/radio (ng-model) and any repeated/grid rows anywhere on the page. */
    public String dumpSelectables() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox],input[type=radio]')];"
                + " out.push('all-checkboxes='+cbs.length+' visible='+cbs.filter(c=>c.offsetParent!==null).length);"
                + " out.push('checkbox-ngmodels='+JSON.stringify(cbs.map(c=>c.getAttribute('ng-model')||'?').filter((v,i,a)=>a.indexOf(v)===i).slice(0,15)));"
                + " out.push('ng-repeats='+JSON.stringify([...document.querySelectorAll('[ng-repeat],[data-ng-repeat]')].map(e=>e.tagName+':'+(e.getAttribute('ng-repeat')||e.getAttribute('data-ng-repeat'))).filter((v,i,a)=>a.indexOf(v)===i).slice(0,15)));"
                + " out.push('ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length+' ui-grid-rows='+document.querySelectorAll('.ui-grid-row').length);"
                + " out.push('tables='+document.querySelectorAll('table').length+' tbody-rows='+document.querySelectorAll('table tbody tr').length);"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const grids=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                + " out.push('tables='+grids.length);"
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
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null).map(c=>c.getAttribute('ng-model')||'?').filter((v,i,a)=>a.indexOf(v)===i);"
                + " out.push('checkbox-ngmodels='+JSON.stringify(cbs.slice(0,10)));"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    /** Candidate routes tried (in order) when the menu link can't be found — following the master-screen naming. */
    private static final String[] ROUTE_CANDIDATES = {
            "#/BedSideAmenitiesMaster", "#/BedSideAmenities", "#/AmenitiesMaster", "#/BedSideAmenity", "#/BedsideAmenitiesMaster"};

    /** Expand <b>Application Configuration</b> → (Admission) → <b>Bed Side Amenities</b> using real clicks. */
    public boolean navigateViaMenu() {
        // wait for the app shell (Application Configuration menu) to render after login
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedSideAmenities.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        } else {
            System.out.println("BedSideAmenities.nav: 'Application Configuration' menu not found");
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        Object bsa = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*side\\s*amenit/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__bsaMenu'; return a.getAttribute('href')||'link'; }");
        if (bsa != null && !bsa.toString().isEmpty()) {
            try { page.locator("#__bsaMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedSideAmenities.nav: Bed Side Amenities click failed - " + e.getMessage()); }
            waitForAngular(1500);
            String href = bsa.toString();
            if (href.contains("#/")) ROUTE = href.substring(href.indexOf("#/"));
        } else {
            System.out.println("BedSideAmenities.nav: 'Bed Side Amenities' menu link not found");
        }
        // If the visible menu didn't work, mine the DOM for ANY anchor (even hidden in a collapsed sidebar) whose
        // text/href mentions amenities, and capture its real route.
        if (ROUTE.isEmpty()) {
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/amenit/i.test(norm(x.textContent)) || /amenit/i.test(x.getAttribute('href')||''));"
                    + " return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("BedSideAmenities.nav: mined amenities href => " + href);
            if (href != null && href.toString().contains("#/")) {
                ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            }
        }
        // fallback: route directly. Use the captured route, else try each naming candidate until one lands on a
        // screen with a Code field + a table.
        if (!onScreen()) {
            java.util.List<String> tries = new java.util.ArrayList<>();
            if (!ROUTE.isEmpty()) tries.add(ROUTE);
            for (String c : ROUTE_CANDIDATES) if (!tries.contains(c)) tries.add(c);
            for (String h : tries) {
                System.out.println("BedSideAmenities.nav: direct route " + h);
                try { page.evaluate("(x) => { window.location.hash = x; }", h); } catch (Exception ignore) { }
                waitForAngular(1600);
                boolean looksRight = Boolean.TRUE.equals(page.evaluate("() => { const t=(document.body.innerText||'').toLowerCase(); const hasTbl=document.querySelector('table tbody tr, .ui-grid-row'); return (/amenit/.test(t) || /remark/.test(t)) && !!hasTbl; }"));
                if (looksRight || page.url().toLowerCase().contains(h.replace("#/", "").toLowerCase())) { ROUTE = h; break; }
            }
        }
        return true;
    }

    /** True when a Bed Side Amenities screen is shown (route captured, or the header text is present). */
    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /bed\\s*side\\s*amenit/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /**
     * Wait until the REAL screen has finished loading — header "Bed Side Attachments", the <b>Form Name*</b> select
     * resolved to an actual master (not {@code --Select--}), and the grid populated.
     *
     * <p>Without this the flow fills Code/Remark while the app is still showing the previous screen ("TRANSFER")
     * with a blank commonmaster form. The values go in, the screen then finishes loading, and that re-render
     * <b>wipes them</b> — which is why the step screenshot showed an empty form and why the first save came back
     * "Please Enter Code!". Waiting here also makes the grid rows reliably present for the record tick.</p>
     */
    public boolean waitForScreenReady() {
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const sel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && (s.getAttribute('ng-model')||'').indexOf('grid.')<0"
                    + "   && [...s.options].some(o=>/bed\\s*side/i.test(o.textContent||'')));"
                    + " if(!sel) return false;"
                    + " const cur=norm(sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].textContent : '');"
                    + " if(!cur || /^-*\\s*select\\s*-*$/i.test(cur)) return false;"
                    + " const rows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row, table tbody tr')]"
                    + "   .filter(r=>r.offsetParent!==null && norm(r.textContent) && !/no records|no data/i.test(r.textContent));"
                    + " return rows.length > 0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
            waitForAngular(800);
            return true;
        } catch (Exception e) {
            System.out.println("waitForScreenReady: screen did not finish loading (Form Name unresolved / grid empty)");
            return false;
        }
    }

    /** What the app actually rendered — header + the Form Name currently selected. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1')||{}).textContent||'').slice(0,40);"
                + " const sel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && [...s.options].some(o=>/bed\\s*side/i.test(o.textContent||'')));"
                + " const fn=sel && sel.options[sel.selectedIndex] ? norm(sel.options[sel.selectedIndex].textContent) : '(none)';"
                + " return (hdr||'(no header)')+' | Form Name: '+fn; }");
        return r == null ? "(unknown)" : r.toString();
    }

    /**
     * Read Code + Remark back OUT of the DOM — the truth of what is on screen, as opposed to what a set call
     * claimed. Used to assert the step rather than trusting the write.
     */
    public String readBackCodeAndRemark() {
        Object r = page.evaluate("() => { const g=ng=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); return e ? (e.value||'') : '(no field)'; };"
                + " return 'Code=\"'+g('commonmaster.Code')+'\" Remark=\"'+g('commonmaster.Description')+'\"'; }");
        return r == null ? "" : r.toString();
    }

    /** True when the Code + Remark inputs really hold the values this run intends to save. */
    public boolean valuesPresent() {
        Object r = page.evaluate("(a) => { const g=ng=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); return e ? (e.value||'').trim() : ''; };"
                + " return g('commonmaster.Code')===a.code && g('commonmaster.Description')===a.remark; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        return Boolean.TRUE.equals(r);
    }

    /** Enter <b>Code</b> (unique) + <b>Remark</b>. Located by label so it works before the exact ng-models are known.
     *  Returns a summary incl. the discovered ng-models. */
    public String fillCodeAndRemark() {
        String code = "AM" + String.format("%06d", Math.abs(System.nanoTime() % 1000000));
        lastCode = code;
        // Remark gets a REALISTIC amenity name from a per-screen pool — only the Code carries the generated value.
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        // The inline form renders AFTER the grid on this screen — a single read can land before it exists and
        // silently return "(no)" for both fields. Poll for the Code input first.
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...document.querySelectorAll('label,.control-label,span,th,td,b')].some(e=>e.offsetParent!==null && /^code\\*?$/i.test(norm(e.textContent).replace(/\\*/g,'').trim()))"
                    + "   && [...document.querySelectorAll('input:not([type=hidden]),textarea')].some(x=>x.offsetParent!==null && (x.getAttribute('ng-model')||'').indexOf('commonmaster')>=0); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillCodeAndRemark: inline form did not render in time"); }
        waitForAngular(600);
        Object r = page.evaluate("(a) => { const code=a.code; const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; const p=l.parentElement?l.parentElement.querySelector('input:not([type=hidden]),textarea'):null; if(p&&p.offsetParent!==null) return p; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const codeF=labelField(['Code','Amenity Code','Bed Side Amenities Code']);"
                + " const remF=labelField(['Remark','Remarks','Description']);"
                + " const cd=setInp(codeF, code); const rm=setInp(remF, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg='+(codeF?codeF.getAttribute('ng-model'):'-')+' | remNg='+(remF?remF.getAttribute('ng-model'):'-'); }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        String summary = r == null ? "" : r.toString();

        // The screen can still re-render right after the write and BLANK both fields. Re-apply until the values
        // are actually readable in the DOM, so the step (and its screenshot) reflects what is really on screen.
        for (int i = 0; i < 6 && !valuesPresent(); i++) {
            System.out.println("fillCodeAndRemark: fields cleared by a re-render — re-applying (attempt " + (i + 1) + "/6)");
            page.evaluate("(a) => { const A=window.angular;"
                    + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return;"
                    + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " set('commonmaster.Code', a.code); set('commonmaster.Description', a.remark); }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            waitForAngular(1200);
        }
        return summary + " | on-screen: " + readBackCodeAndRemark();
    }

    /**
     * Tick/select any one record in the amenities grid. The grid is a ui-grid (virtualized — rows only render once
     * the grid is scrolled into view), so poll for rows, then select via the row checkbox if present, else a real
     * row click (ui-grid row selection). Returns the row label, or "" if none.
     */
    public String selectFirstRecord() {
        // poll for ui-grid / table rows to render, scrolling the grid into view each time to force virtualization
        String rowText = null;
        for (int i = 0; i < 40 && rowText == null; i++) {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const g=document.querySelector('.ui-grid-viewport,.ui-grid,[ui-grid]'); if(g && g.scrollIntoView) g.scrollIntoView({block:'center'});"
                    + " const rows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row, table tbody tr')].filter(r=>r.offsetParent!==null && norm(r.textContent) && !/no records|no data/i.test(r.textContent));"
                    + " if(!rows.length) return null;"
                    + " const row=rows[0];"
                    + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(cb){ cb.id='__bsaRowCb'; } else { let cell=row.querySelector('.ui-grid-cell-contents, td'); if(cell) cell.id='__bsaRowCell'; }"
                    + " return norm(([...row.querySelectorAll('.ui-grid-cell-contents,td')].map(c=>norm(c.textContent)).find(x=>x && !/^\\d+$/.test(x))) || norm(row.textContent)).slice(0,40); }");
            if (r != null) rowText = r.toString();
            else page.waitForTimeout(600);
        }
        if (rowText == null) { System.out.println("selectFirstRecord: no grid rows rendered"); return ""; }
        // click the checkbox if there is one, else real-click the row cell (ui-grid selects on row click)
        boolean cb = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector('#__bsaRowCb')"));
        String sel = cb ? "#__bsaRowCb" : "#__bsaRowCell";
        try { page.locator(sel).click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectFirstRecord: click failed - " + e.getMessage()); }
        waitForAngular(300);
        return rowText;
    }

    /**
     * Click <b>Submit</b> and return the toast (success or a server error).
     *
     * <p>Code/Remark are RE-APPLIED to every scope's {@code commonmaster} immediately before the click. Ticking a
     * row fires a Status update that re-renders the inline form and <b>blanks the Code</b>, so without this the
     * save comes back "Please Enter Code!" even though the field was filled a moment earlier.</p>
     */
    /**
     * Submit, and on <b>"Description already exists!"</b> retry with the NEXT realistic name from the pool —
     * keep retrying until the success toast, not just until the small pool is exhausted once.
     *
     * <p>The Remark must read like a real amenity, but the pool is small and this environment already holds most
     * of those names, so a single attempt frequently collides. Walking the pool keeps the data realistic; once
     * every pool entry has been tried once, a short numeric suffix (" 2", " 3", …) is appended so later attempts
     * are still realistic-looking but never repeat an already-rejected exact value — stopping at
     * {@code REMARKS.length} would give up right when the pool (not the retry) runs out.</p>
     */
    public String submitWithRetries() {
        final int maxAttempts = 40;
        String toast = "";
        for (int i = 0; i < maxAttempts; i++) {
            toast = submitAndGetToast();
            if (toast == null) toast = "";
            if (!toast.toLowerCase().contains("already exist")) return toast;
            int cycle = (i + 1) / REMARKS.length;
            String next = REMARKS[(int) ((Math.abs(System.nanoTime()) + i + 1) % REMARKS.length)]
                    + (cycle > 0 ? " " + (cycle + 1) : "");
            if (next.equals(lastRemark)) next = REMARKS[(i + 1) % REMARKS.length] + (cycle > 0 ? " " + (cycle + 1) : "");
            System.out.println("submitWithRetries: \"" + lastRemark + "\" exists — retrying as \"" + next + "\"");
            lastRemark = next;
            page.evaluate("(rm) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"[ng-model='commonmaster.Description']\")].find(x=>x.offsetParent!==null);"
                    + " if(e){ const c=A.element(e).controller('ngModel'); e.value=rm; if(c){c.$setViewValue(rm);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }", lastRemark);
            waitForAngular(600);
        }
        return toast;
    }

    public String submitAndGetToast() {
        Object tagged = page.evaluate("(a) => { window.__bsaToasts=[]; if(window.__bsaObs) window.__bsaObs.disconnect();"
                // REMOVE any toast still on screen before observing. Ticking a row raises "Status updated
                // successfully." and that toast lingers — grabbing it here made Submit look successful even when
                // the save never happened.
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bsaToasts.includes(t)) window.__bsaToasts.push(t); }); };"
                + " window.__bsaObs=new MutationObserver(grab); window.__bsaObs.observe(document.body,{childList:true,subtree:true});"
                + " const A=window.angular;"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " setInp('commonmaster.Code', a.code); setInp('commonmaster.Description', a.remark);"
                + " const seen=new Set();"
                + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                + "   if(s.commonmaster && typeof s.commonmaster==='object'){ if(a.code) s.commonmaster.Code=a.code; if(a.remark) s.commonmaster.Description=a.remark; } }catch(e){} });"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__bsaSubmit'; return true; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__bsaSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__bsaToasts||[]).some(a=>/amenit|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bsaToasts||[]).includes(t)) (window.__bsaToasts=window.__bsaToasts||[]).push(t); }); }");
        }
        // Take the Submit's OWN toast. "Status updated successfully." comes from the row-tick (a Status toggle),
        // NOT from saving an amenity — it must never be reported as a successful save, so it is excluded here and
        // any remaining message is returned verbatim for the step to judge.
        Object r = page.evaluate("() => { const a=(window.__bsaToasts||[]).filter(x=>!/status updated/i.test(x));"
                + " return a.find(x=>/master saved|amenit.*(saved|added)|saved successfully|added successfully/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
