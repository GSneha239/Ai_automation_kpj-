package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Vaccination Schedule</b> (VaccineScheduleMaster) — configuration
 * screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Vaccination Schedule</b>
 * ({@code #/VaccineScheduleMasterList}) → <b>Add</b> ({@code AddVaccineScheduleMaster()} →
 * {@code #/add-VaccineScheduleMaster}) → enter <b>Schedule Name*</b> ({@code VaccineSchedule.schedulename}),
 * <b>Age (In Days)*</b> ({@code VaccineSchedule.ageindays}) and <b>Vaccination Name*</b>
 * ({@code VaccineSchedule.vaccinename}) → <b>Submit</b> ({@code fnIUDVaccineSchedule()}) → success toast.</p>
 *
 * <p><b>Two different Add buttons.</b> The list's is {@code AddVaccineScheduleMaster()} (opens the form); the form
 * carries a second one, {@code AddVaccineSchedule(VacSchList)}, which commits the entered line into the detail list
 * {@code VacSchList}. {@link #clickInnerAdd()} drives that second button and is exposed separately — on the sibling
 * Pain Screening Master an uncommitted detail row made Submit answer "Error!", so if Submit misbehaves here the
 * uncommitted row is the first thing to check.</p>
 */
public class VaccineScheduleMaster extends BasePage {

    public VaccineScheduleMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/VaccineScheduleMasterList";
    public String lastScheduleName = "", lastAgeInDays = "", lastVaccineName = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] SCHEDULES = {
            "Infant Immunisation Schedule", "Childhood Booster Schedule", "Adolescent Vaccination Schedule",
            "Maternal Immunisation Schedule", "Travel Vaccination Schedule"};
    private static final String[] VACCINES = {
            "BCG", "Hepatitis B", "Polio", "Measles Mumps Rubella", "Diphtheria Tetanus Pertussis", "Influenza"};

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            // Menu label is "Vaccination Schedule"; the route/header say "VaccineScheduleMaster" — accept either.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__vsMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*(vaccination\\s*schedule|vaccineschedulemaster)\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/VaccineScheduleMasterList'); if(!a) return ''; a.id='__vsMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("VaccineScheduleMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__vsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__vsMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__vsMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVaccineScheduleMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("VaccineScheduleMaster.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * On the REAL Vaccination Schedule list — verified by its own Add button ({@code AddVaccineScheduleMaster}),
     * NOT just the URL: the SPA can hold the right hash while leaving a previous screen mounted.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("vaccineschedule")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVaccineScheduleMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /**
     * Real-click the LIST's <b>Add</b> ({@code AddVaccineScheduleMaster}) → {@code #/add-VaccineScheduleMaster}.
     * Polls for the button (it renders after the grid) and retries — a single early click on these Nursing lists
     * has been seen doing nothing at all.
     */
    public boolean clickAdd() {
        for (int round = 0; round < 3 && !onAddForm(); round++) {
            boolean tagged = false;
            for (int i = 0; i < 25 && !tagged; i++) {
                tagged = Boolean.TRUE.equals(page.evaluate("() => { document.querySelectorAll('#__vsAdd').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddVaccineScheduleMaster\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__vsAdd'; return true; }"));
                if (!tagged) page.waitForTimeout(500);
            }
            if (!tagged) { System.out.println("VaccineScheduleMaster.clickAdd: Add button not found (round " + (round + 1) + ")"); return false; }
            try { page.locator("#__vsAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("VaccineScheduleMaster.clickAdd: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__vsAdd'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='VaccineSchedule.schedulename')",
                        null, new Page.WaitForFunctionOptions().setTimeout(12000));
            } catch (Exception ignore) { System.out.println("VaccineScheduleMaster.clickAdd: entry form did not render (round " + (round + 1) + ")"); }
            waitForAngular(1200);
        }
        return onAddForm();
    }

    /** True once the Add form is up (its Schedule Name field is present). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='VaccineSchedule.schedulename')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Schedule Name*</b>, <b>Age (In Days)*</b> and <b>Vaccination Name*</b> by their exact ng-models.
     * Age is a plain text input on this screen (not a number field), so a numeric string is written into it.
     * {@code attempt} shifts Schedule Name and Vaccination Name so a retry after an "already exists" toast
     * submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        lastScheduleName = SCHEDULES[attempt % SCHEDULES.length] + (attempt >= SCHEDULES.length ? " " + (attempt / SCHEDULES.length + 1) : "");
        lastVaccineName = VACCINES[attempt % VACCINES.length] + (attempt >= VACCINES.length ? " " + (attempt / VACCINES.length + 1) : "");
        lastAgeInDays = String.valueOf(1 + Math.abs(System.nanoTime() % 365));
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const sn=set('VaccineSchedule.schedulename', a.schedule);"
                + " const ag=set('VaccineSchedule.ageindays', a.age);"
                + " const vn=set('VaccineSchedule.vaccinename', a.vaccine);"
                + " return 'ScheduleName='+sn+' | AgeInDays='+ag+' | VaccinationName='+vn; }",
                java.util.Map.of("schedule", lastScheduleName, "age", lastAgeInDays, "vaccine", lastVaccineName));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Click the form's inner <b>Add</b> ({@code AddVaccineSchedule(VacSchList)}), which commits the entered line
     * into the detail list. Returns the handler fired plus the resulting {@code VacSchList} length.
     */
    public String clickInnerAdd() {
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__vsInnerAdd').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>x.offsetParent!==null && /AddVaccineSchedule\\s*\\(/.test(x.getAttribute('ng-click')||'') && !/AddVaccineScheduleMaster/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return ''; b.id='__vsInnerAdd'; return b.getAttribute('ng-click'); }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) return "(no inner Add button found)";
        try { page.locator("#__vsInnerAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickInnerAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vsInnerAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        // Count from the scope list the Add feeds — a DOM row count also picks up the blank template row.
        Object rows = page.evaluate("() => { let n=-1; document.querySelectorAll('*').forEach(el=>{ if(n>=0) return; try{ const s=angular.element(el).scope();"
                + " if(s && Array.isArray(s.VacSchList)) n=s.VacSchList.length; }catch(e){} }); return n; }");
        int n = rows instanceof Number ? ((Number) rows).intValue() : -1;
        return (n > 0 ? "Add clicked {" + how + "} -> VacSchList rows=" + n
                      : "Add clicked {" + how + "} but NO row was committed (VacSchList=" + n + ")");
    }

    /** Click <b>Submit</b> ({@code fnIUDVaccineSchedule}) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__vsToasts=[]; if(window.__vsObs) window.__vsObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__vsToasts.includes(t)) window.__vsToasts.push(t); }); };"
                + " window.__vsObs=new MutationObserver(grab); window.__vsObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__vsSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__vsSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("VaccineScheduleMaster submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__vsSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__vsSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__vsToasts||[]).some(a=>/vaccin|schedule|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__vsToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            // "saved" ends the flow successfully; "already exists" is NOT retryable with the SAME details —
            // return it immediately rather than blindly resubmitting stale data, so the caller can regenerate
            // fresh details (fillDetails) and try again.
            if (last.toLowerCase().matches(".*(saved|added|success|exist|already).*")) return last;
            page.evaluate("() => { window.__vsToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
