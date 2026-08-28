package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Room</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Room</b> → <b>Add</b> → enter <b>Code</b> +
 * <b>Remark</b> → select <b>Room Type</b> → tick any one <b>Amenity</b> checkbox in the table → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class Room extends BasePage {

    public Room(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic room-name/description values — a retry after "already exists" must still read like real data,
     *  never "Auto room &lt;CODE&gt;". */
    private static final String[] REMARKS = {
            "Deluxe Room", "Standard Room", "Suite Room", "Family Room",
            "Executive Room", "Twin Sharing Room", "Single Occupancy Room", "Premium Room"
    };

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " out.push('tables='+document.querySelectorAll('table').length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); const l=g?g.querySelector('label,.control-label'):null; return norm(l?l.textContent:'').slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null).map(c=>(c.getAttribute('ng-model')||'?')+' lbl=\"'+labelOf(c)+'\"').filter((v,i,a)=>a.indexOf(v)===i);"
                + " out.push('checkboxes=\\n  '+cbs.slice(0,12).join('\\n  '));"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Room.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Room" link if present (capture its route)
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*room\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__roomMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__roomMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Room.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text is exactly "Room" or href mentions room master
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*room\\s*$/i.test(norm(x.textContent)) || /roommaster|#\\/room/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("Room.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("Room.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return false;
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__roomAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Room.clickAdd: Add button not found"); return false; }
        try { page.locator("#__roomAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Room.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Enter <b>Code</b> (unique) + <b>Remark</b> (by label). Returns a summary incl. the discovered ng-models.
     *  {@code attempt} shifts both values so a retry after an "already exists" toast submits genuinely different
     *  details. */
    public String fillCodeAndRemark(int attempt) {
        String code = "RM" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; const p=l.parentElement?l.parentElement.querySelector('input:not([type=hidden]),textarea'):null; if(p&&p.offsetParent!==null) return p; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const codeF=labelField(['Code','Room Code']);"
                + " const remF=labelField(['Remark','Remarks','Description']);"
                + " const cd=setInp(codeF, a.code); const rm=setInp(remF, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg='+(codeF?codeF.getAttribute('ng-model'):'-')+' | remNg='+(remF?remF.getAttribute('ng-model'):'-'); }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Select <b>Room Type</b> (dropdown located by label; first real option). Returns the chosen text / ng-model. */
    public String selectRoomType() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const byLabel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && (()=>{ const g=s.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div'); const l=g?g.querySelector('label,.control-label'):null; return l && /room\\s*type/i.test(l.textContent||''); })());"
                + " const sel=byLabel || [...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && s.options.length>1 && !/filter|page|entries/i.test(s.getAttribute('ng-model')||''));"
                + " if(!sel) return '(no room-type select)';"
                + " const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '(no-opt)';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(e){} if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent)+' [ng='+(sel.getAttribute('ng-model')||'')+']'; }");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Tick any one amenity checkbox in the amenities table (real/force click). Returns the amenity label, or "". */
    public String selectAmenity() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                // amenities are checkboxes inside a table/grid — exclude grid-filter checkboxes
                + " const cbs=[...document.querySelectorAll('table input[type=checkbox], .ui-grid-row input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/logbookfilters|colfilter|selectall/i.test(c.getAttribute('ng-model')||''));"
                + " if(!cbs.length) return 'none';"
                + " const cb=cbs.find(c=>!c.checked)||cbs[0]; cb.id='__roomAmenityCb';"
                + " const row=cb.closest('tr,.ui-grid-row,li,div'); const name=row?(([...row.querySelectorAll('td,.ui-grid-cell-contents')].map(td=>norm(td.textContent)).find(x=>x && !/^\\d+$/.test(x))) || norm(row.textContent).slice(0,40)):'amenity';"
                + " return name+' [ng='+(cb.getAttribute('ng-model')||'')+']'; }");
        String name = String.valueOf(r);
        if ("none".equals(name)) { System.out.println("selectAmenity: no amenity checkbox found"); return ""; }
        try { page.locator("#__roomAmenityCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectAmenity: click failed - " + e.getMessage()); }
        waitForAngular(300);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__roomAmenityCb'); return !!(c && c.checked); }"));
        return checked ? name : "";
    }

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__rmToasts=[]; if(window.__rmObs) window.__rmObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rmToasts.includes(t)) window.__rmToasts.push(t); }); };"
                + " window.__rmObs=new MutationObserver(grab); window.__rmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__roomSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__roomSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__rmToasts||[]).some(a=>/room|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__rmToasts||[]).includes(t)) (window.__rmToasts=window.__rmToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__rmToasts||[]; return a.find(x=>/room.*(saved|added|updated)|saved successfully|master saved|added successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
