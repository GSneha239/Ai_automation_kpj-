package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Cluster</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Cluster</b>
 * ({@code #/ClusterMasterList}) → <b>Add</b> ({@code AddCluster()} → {@code #/add-ClusterMaster}) → enter
 * <b>Code*</b> ({@code Cluster.code}) + <b>Remark*</b> ({@code Cluster.description}) → type a <b>Doctor</b>
 * ({@code Cluster.ClusterDoctorName} / {@code #txtDoctorName}, an {@code auto-complete} box) and select it →
 * <b>Add</b> ({@code AddDoctor()}) so a row lands in the "Select · Doctor Name · Delete" grid → <b>Submit</b>
 * ({@code fnIUDCluster()}) → toast.</p>
 */
public class Cluster extends BasePage {

    public Cluster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/ClusterMasterList";
    public String lastCode = "", lastDoctor = "", lastRemark = "";

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Patient (submenu) → Cluster. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*cluster\\s*$/i.test(norm(x.textContent)) || /clustermasterlist/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__clMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__clMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Cluster.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("Cluster.nav: menu link not found"); }
        if (!onScreen()) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("clustermaster"); }

    /** Real-click <b>Add</b> ({@code AddCluster}) → {@code #/add-ClusterMaster}. The button renders after the
     *  ui-grid, so POLL for it. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddCluster\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__clAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Cluster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__clAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Cluster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__clAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='Cluster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("Cluster.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-cluster");
    }

    // ---- form ------------------------------------------------------------

    /** Enter a unique <b>Code*</b> ({@code Cluster.code}) and <b>Remark*</b> ({@code Cluster.description}). */
    /** Cluster names that read like genuine configuration rather than machine noise. */
    private static final String[] REMARKS = {
            "Cardiology Specialist Cluster", "Orthopaedic Specialist Cluster", "Paediatric Specialist Cluster",
            "Oncology Specialist Cluster", "General Surgery Cluster", "Womens Health Cluster"
    };

    /** Bumped on every "already exists" retry so the regenerated description is genuinely unique. */
    private int dupRetry = 0;

    public String fillCodeAndRemark() {
        String code = "CL" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        // The description used to be the HARD-CODED "Cardiology Specialist Cluster", so every run submitted the
        // identical value and the save always came back "Description already exist!". Pick from a pool, and on a
        // retry append a short number so the value cannot collide again.
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000) % REMARKS.length)]
                + (dupRetry > 0 ? " " + (Math.abs(System.nanoTime()) % 10000) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('Cluster.code', a.code); const rm=set('Cluster.description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Rows in the "Select · Doctor Name · Delete" grid. */
    public int doctorRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/doctor name/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.innerText) && !/no record/i.test(r.innerText)).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Type a doctor into the <b>Doctor</b> box ({@code #txtDoctorName}). If the autocomplete offers a suggestion it
     * is taken (that also sets {@code Cluster.ClusterDoctorId}); otherwise the typed text is left in place — the
     * <b>Add</b> button pushes whatever is in the box into the grid either way.
     *
     * <p>NB: do NOT retag this input's {@code id}. The widget's own {@code data()} callback does
     * {@code document.getElementById("txtDoctorName")}, so renaming the element breaks the autocomplete outright
     * (it throws on {@code getBoundingClientRect} and no suggestion ever appears). {@code minimumChars} is 3.</p>
     */
    public String enterDoctorName(String value) {
        boolean present = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector('#txtDoctorName')"));
        if (!present) { System.out.println("enterDoctorName: no #txtDoctorName"); return "(no doctor input)"; }
        try {
            com.microsoft.playwright.Locator doc = page.locator("#txtDoctorName");
            doc.scrollIntoViewIfNeeded();
            doc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            doc.fill("");
            doc.pressSequentially(value, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(220));
        } catch (Exception e) { System.out.println("enterDoctorName: typing failed - " + e.getMessage()); return "(typing failed)"; }
        waitForAngular(2500);   // give the suggestion list (minimumChars = 3) a chance
        // Take a suggestion if the widget rendered one — its dropdown carries the class "txtDoctorName".
        Object picked = page.evaluate("() => { const norm=s=>(s||'').replace(/\s+/g,' ').trim();"
                + " const conts=[...document.querySelectorAll('.txtDoctorName,[class*=auto-complete],[class*=autocomplete],ul.ui-autocomplete,.dropdown-menu,[role=listbox]')].filter(c=>c.offsetParent!==null && c.tagName!=='INPUT');"
                + " for(const c of conts){ const items=[...c.querySelectorAll('li,a,div,span,tr')].filter(e=>e.offsetParent!==null && norm(e.textContent) && !e.querySelector('li,a,tr'));"
                + "   if(items.length){ items[0].id='__clDocItem'; return norm(items[0].textContent).slice(0,60); } }"
                + " return ''; }");
        String suggestion = picked == null ? "" : picked.toString();
        if (!suggestion.isEmpty()) {
            try { page.locator("#__clDocItem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("enterDoctorName: suggestion click failed - " + e.getMessage()); }
            waitForAngular(1200);
        }
        Object val = page.evaluate("() => { const e=document.querySelector('#txtDoctorName'); return e?e.value:''; }");
        String field = val == null ? "" : val.toString().trim();
        lastDoctor = field.isEmpty() ? value : field;
        Object id = page.evaluate("() => { let c=null; document.querySelectorAll('*').forEach(el=>{ if(c) return; try{ const s=window.angular.element(el).scope(); let x=s; for(let i=0;i<25&&x;i++){ if(x.Cluster){ c=x.Cluster; return; } x=x.$parent; } }catch(e){} }); return c && c.ClusterDoctorId!=null ? String(c.ClusterDoctorId) : ''; }");
        return "Typed \"" + value + "\" | field=\"" + field + "\""
                + (suggestion.isEmpty() ? " | no suggestion offered" : " | picked suggestion \"" + suggestion + "\"")
                + " | ClusterDoctorId=" + (id == null || id.toString().isEmpty() ? "(unset)" : id);
    }


    /** Click <b>Add</b> ({@code AddDoctor()}) to push the selected doctor into the grid. Returns the row count. */
    public String clickAddDoctor() {
        int before = doctorRowCount();
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddDoctor/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__clAddDoc'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddDoctor: AddDoctor button not found"); return "(no Add button)"; }
        try { page.locator("#__clAddDoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAddDoctor: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__clAddDoc'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return "grid rows " + before + " -> " + doctorRowCount();
    }

    /**
     * Tick the <b>Select</b> checkbox of the doctor row in the "Select · Doctor Name · Delete" grid — Submit's
     * "Please Select Doctor." refers to THIS tick, not merely having a row. Real-click so the Angular binding fires,
     * and skip the header's select-all ({@code selectall.isselected}). Returns which row was ticked.
     */
    public String selectDoctorRowCheckbox() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/doctor name/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return ''; const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.innerText) && !/no record/i.test(r.innerText));"
                + " for(const r of rows){ const cb=[...r.querySelectorAll('input[type=checkbox]')].find(c=>c.offsetParent!==null && (c.getAttribute('ng-model')||'').indexOf('selectall')<0);"
                + "   if(cb){ cb.id='__clRowCb'; return norm(r.innerText).slice(0,50); } }"
                + " return ''; }");
        String row = tagged == null ? "" : tagged.toString();
        if (row.isEmpty()) { System.out.println("selectDoctorRowCheckbox: no doctor row checkbox"); return "(no row checkbox)"; }
        for (int i = 0; i < 3; i++) {
            if (Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__clRowCb'); return !!(c && c.checked); }"))) break;
            try { page.locator("#__clRowCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
            catch (Exception e) { System.out.println("selectDoctorRowCheckbox: click failed - " + e.getMessage()); }
            waitForAngular(600);
        }
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__clRowCb'); return !!(c && c.checked); }"));
        return row + " (checked=" + checked + ")";
    }

    /** Click <b>Submit</b> ({@code fnIUDCluster}) and return the toast (toastr cleared first). */
    /** Submit; on "Description already exist!" regenerate the Code/Description and submit again (3 attempts). */
    public String submitAndGetToast() { return submitAndGetToast(0); }

    private String submitAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__clToasts=[]; if(window.__clObs) window.__clObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__clToasts.includes(t)) window.__clToasts.push(t); }); };"
                + " window.__clObs=new MutationObserver(grab); window.__clObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCluster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__clSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__clSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|save|submit|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__clToasts||[]).some(a=>/cluster|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__clToasts||[]).includes(t)) (window.__clToasts=window.__clToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__clToasts||[]; return a.find(x=>/(cluster|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        // "Description already exist!" is not retryable with the same values — regenerate the Code/Description
        // (dupRetry makes the description unique) and submit again. The doctor row already sits in the grid.
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("submitAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            dupRetry++;
            fillCodeAndRemark();
            return submitAndGetToast(attempt + 1);
        }
        return toast;
    }
}
