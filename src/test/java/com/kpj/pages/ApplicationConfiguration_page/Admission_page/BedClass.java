package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Class</b> — configuration screen Page Object.
 *
 * <p>Flow: expand <b>Application Configuration</b> → (Admission) → <b>Bed Class</b> (a grid of Bed Class Code /
 * Room Type / Status / Edit with an <b>Add</b> button) → click <b>Add</b> → fill <b>Bed Class Code</b> +
 * <b>Room Type</b> → tick any one <b>Select</b> checkbox in the <b>Pricing Policy</b> table → <b>Submit</b> →
 * success toast.</p>
 *
 * <p>Per the M6 Bed Planning FSD, Bed Class is configured before adding a bed; once added it appears as the Room
 * Type dropdown in the Bed screen.</p>
 */
public class BedClass extends BasePage {

    public BedClass(Page page) { super(page); }

    public static final String ROUTE = "#/BedClassMaster";
    public String lastCode = "";

    // ---- discovery (prints the live DOM so the exact route/ng-models/handlers can be wired) ---------------

    /** Dump the current screen: URL, grid headers/rows, and buttons (text + ng-click). */
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

    /** Dump the Add form: inputs/selects (ng-model + label), the pricing table checkbox model, and Submit handler. */
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

    /**
     * Expand the <b>Application Configuration</b> menu and open the <b>Bed Class</b> screen (via the Admission submenu
     * if present). Uses real clicks so AngularJS menu handlers fire.
     */
    public boolean navigateViaMenu() {
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedClass.navigateViaMenu: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        } else {
            System.out.println("BedClass.navigateViaMenu: 'Application Configuration' menu not found");
        }
        // expand an "Admission" submenu group if present (a non-route anchor)
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // click the "Bed Class" link (exact — not "Bed").
        Object bc = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*class\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__bedClassMenu'; return true; }");
        if (Boolean.TRUE.equals(bc)) {
            try { page.locator("#__bedClassMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedClass.navigateViaMenu: Bed Class click failed - " + e.getMessage()); }
            waitForAngular(1500);
        } else {
            System.out.println("BedClass.navigateViaMenu: 'Bed Class' menu link not found");
        }
        // Fallback: the menu link is flaky, so route directly if we didn't land on the Bed Class screen.
        if (!onBedClassScreen()) {
            System.out.println("BedClass.navigateViaMenu: menu nav didn't land on " + ROUTE + " — using direct route");
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        return true;
    }

    /** True when the Bed Class list screen ({@code #/BedClassMaster}) is shown. */
    public boolean onBedClassScreen() {
        return page.url().toLowerCase().contains("bedclassmaster");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button on the Bed Class list (polls — the control renders after the ui-grid). Confirms
     *  the add form opened by the presence of the {@code BedClassMaster.BedClassCode} field. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/addbedclass|bedclassadd/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__bcAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BedClass.clickAdd: Add button not found"); return false; }
        try { page.locator("#__bcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BedClass.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1000);
        // confirm the add form is up
        for (int i = 0; i < 10; i++) {
            if (Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"input[ng-model='BedClassMaster.BedClassCode']\")"))) return true;
            page.waitForTimeout(400);
        }
        return Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"input[ng-model='BedClassMaster.BedClassCode']\")"));
    }

    /** Realistic room-type names — a retry after "already exists" must still read like real data, never
     *  "AutoRoom&lt;CODE&gt;". */
    private static final String[] ROOM_TYPES = {
            "Deluxe Suite", "General Ward", "ICU Room", "Semi-Private Room", "VIP Suite",
            "Executive Room", "Isolation Room", "Family Room"
    };
    public String lastRoomType = "";

    /**
     * Fill the Add form: a unique <b>Bed Class Code</b> and a <b>Room Type</b> (dropdown → first real option;
     * otherwise a text value). Located by the field's label so it works without the exact ng-model. Returns a
     * summary. {@code attempt} shifts both values so a retry after an "already exists" toast submits genuinely
     * different details.
     */
    public String fillBedClassDetails(int attempt) {
        String code = "BC" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRoomType = ROOM_TYPES[attempt % ROOM_TYPES.length] + (attempt >= ROOM_TYPES.length ? " " + (attempt / ROOM_TYPES.length + 1) : "");
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"'],select[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                // Bed Class Code = unique code; Room Type (ng-model BedClassMaster.BedClass) is a free-text name.
                + " const cd=setNg('BedClassMaster.BedClassCode', a.code);"
                + " const rt=setNg('BedClassMaster.BedClass', a.roomType);"
                + " return 'BedClassCode='+cd+' | RoomType='+rt; }",
                java.util.Map.of("code", code, "roomType", lastRoomType));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Tick the <b>Location</b> row's Select checkbox ({@code parameterName.isselected}) in the
     * Select / Location / Order Number table. That table's rows arrive ASYNC (empty for several seconds after Add),
     * so poll for one. Returns the ticked location, or "" if none.
     */
    public String selectLocation() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " let cb=null;"
                + " for(let w=0;w<30;w++){ cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='parameterName.isselected']\")].find(c=>c.offsetParent!==null && c.closest('tr')); if(cb) break; await sleep(500); }"
                + " if(!cb) return 'none'; cb.id='__bcLocCb';"
                + " const row=cb.closest('tr'); return row?(([...row.querySelectorAll('td')].map(td=>norm(td.textContent)).find(x=>x && !/^\\d*$/.test(x))) || norm(row.textContent).slice(0,40)):'location'; }");
        String name = String.valueOf(r);
        if ("none".equals(name)) { System.out.println("selectLocation: no location checkbox (parameterName.isselected) found"); return ""; }
        try { page.locator("#__bcLocCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectLocation: click failed - " + e.getMessage()); }
        waitForAngular(400);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__bcLocCb'); return !!(c && c.checked); }"));
        return checked ? name : "";
    }

    /**
     * Tick any one <b>Select</b> checkbox in the <b>Pricing Policy</b> table (the table headed by
     * Select / Pricing Policy / Standard Deposit). Real-click the first row's checkbox so its Angular binding fires.
     * The 561 policy rows render ASYNC — they are not visible for a few seconds after Add, so POLL for a visible
     * checkbox instead of reading once (reading once is why this used to report "No pricing policy selected").
     * Also fills that row's Standard Deposit, which the save expects alongside the tick.
     * Returns the ticked pricing-policy name, or "" if none.
     */
    public String selectPricingPolicy() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                // The Pricing Policy table's Select checkbox is ng-model='cont.isselected'.
                + " let cbs=[];"
                + " for(let w=0;w<30;w++){ cbs=[...document.querySelectorAll(\"input[type=checkbox][ng-model='cont.isselected']\")].filter(c=>c.offsetParent!==null); if(cbs.length) break; await sleep(500); }"
                + " if(!cbs.length) return 'none';"
                + " const cb=cbs.find(c=>!c.checked)||cbs[0]; cb.id='__bcPricingCb';"
                + " const row=cb.closest('tr,li,div'); const name=row?(([...row.querySelectorAll('td')].map(td=>norm(td.textContent)).find(x=>x && !/^\\d+$/.test(x))) || norm(row.textContent).slice(0,40)):'policy';"
                + " return name; }");
        String name = String.valueOf(r);
        if ("none".equals(name)) { System.out.println("selectPricingPolicy: no pricing checkbox (cont.isselected) found"); return ""; }
        try { page.locator("#__bcPricingCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectPricingPolicy: click failed - " + e.getMessage()); }
        waitForAngular(300);
        // That row's Standard Deposit goes with the tick.
        page.evaluate("() => { const cb=document.querySelector('#__bcPricingCb'); if(!cb) return; const row=cb.closest('tr'); if(!row) return;"
                + " const d=row.querySelector(\"input[ng-model='cont.standarddeposit']\"); if(!d) return; const A=window.angular; const c=A.element(d).controller('ngModel');"
                + " d.value='100'; if(c){c.$setViewValue('100');c.$render();} d.dispatchEvent(new Event('input',{bubbles:true})); d.dispatchEvent(new Event('change',{bubbles:true})); }");
        waitForAngular(300);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__bcPricingCb'); return !!(c && c.checked); }"));
        return checked ? name : "";
    }

    /** Click <b>Submit</b> and return the success toast (MutationObserver slow-toast pattern). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__bcToasts=[]; if(window.__bcObs) window.__bcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bcToasts.includes(t)) window.__bcToasts.push(t); }); };"
                + " window.__bcObs=new MutationObserver(grab); window.__bcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__bcSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__bcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__bcToasts||[]).some(a=>/bed class|saved|success|added|updated|please|select|enter|required|exist/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bcToasts||[]).includes(t)) (window.__bcToasts=window.__bcToasts||[]).push(t); }); }");
        }
        // Return the success toast if present; otherwise surface whatever toast appeared (e.g. a server "Error!").
        Object r = page.evaluate("() => { const a=window.__bcToasts||[]; return a.find(x=>/bed class.*(saved|added|updated)|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
