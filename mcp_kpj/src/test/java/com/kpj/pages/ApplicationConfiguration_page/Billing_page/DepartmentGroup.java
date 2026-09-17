package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Department Group</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Department Group</b> (route
 * {@code #/DepartmentGroup}) → <b>Add</b> ({@code #/add-DepartmentGroup}) → select <b>Department</b> +
 * <b>Group</b> → <b>Submit</b> ({@code fnIUDDepartmentGroup}) → toast → verify in the list table.</p>
 *
 * <p>Fields (discovered): only two — <b>Department</b> {@code departmentgrp.departmentid} (174 opts) and
 * <b>Group</b> {@code departmentgrp.groupid} (43 opts); both independent masters (no cascade). There is NO Code
 * input on the add form (the only text field is a textAngular editor helper). Department+Group is a unique combo,
 * so on "already exists" pick a different Group and Submit again ({@link #selectGroupOrdinal(int)} = 1-based Group).</p>
 */
public class DepartmentGroup extends BasePage {

    public DepartmentGroup(Page page) { super(page); }

    public static String LIST_ROUTE = "#/DepartmentGroup";
    public static String ADD_ROUTE = "#/add-DepartmentGroup";
    public String lastDepartment = "", lastGroup = "";
    /** Screenshot taken the moment the toast is detected in {@link #submitAndGetToast()} — the app
     *  auto-navigates to the list screen shortly after saving, so a screenshot taken later (e.g. by the
     *  test's own step() call) shows the list, not the toast. */
    public byte[] toastPng;

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/department\\s*group/i.test(norm(x.textContent)) || /departmentgroup/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__dgMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__dgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DepartmentGroup.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("DepartmentGroup.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("departmentgroup");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-DepartmentGroup}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dgAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DepartmentGroup.clickAdd: Add button not found"); return false; }
        try { page.locator("#__dgAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("DepartmentGroup.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='departmentgrp.departmentid')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("DepartmentGroup.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Select the first real <b>Department</b> option (real selectOption). Returns the chosen text. */
    public String selectDepartmentFirst() {
        return selectDepartmentOrdinal(1);
    }

    /** Select the {@code ordinal}-th (1-based) real <b>Department</b> option. Returns the chosen text. */
    public String selectDepartmentOrdinal(int ordinal) {
        lastDepartment = realSelectOrdinal("departmentgrp.departmentid", ordinal);
        return lastDepartment;
    }

    /** Number of real (non "-Select-") Department options. */
    public int departmentCount() {
        Object c = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='departmentgrp.departmentid']\"); if(!e) return 0; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Number of real (non "-Select-") Group options. */
    public int groupCount() {
        Object c = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='departmentgrp.groupid']\"); if(!e) return 0; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Select the {@code ordinal}-th (1-based) real <b>Group</b> option. Returns the chosen text. */
    public String selectGroupOrdinal(int ordinal) {
        lastGroup = realSelectOrdinal("departmentgrp.groupid", ordinal);
        return lastGroup;
    }

    private String realSelectOrdinal(String selNg, int ordinal) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            page.locator(sel).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectOrdinal: selectOption failed for " + selNg + " - " + e.getMessage()); return "(err)"; }
        waitForAngular(400);
        return chosen;
    }

    /** Click <b>Submit</b> ({@code fnIUDDepartmentGroup}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__dgToasts=[]; if(window.__dgObs) window.__dgObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dgToasts.includes(t)) window.__dgToasts.push(t); }); };"
                + " window.__dgObs=new MutationObserver(grab); window.__dgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDDepartmentGroup/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__dgSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__dgSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__dgToasts||[]).some(a=>/department|group|saved|success|added|updated|please|select|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dgToasts||[]).includes(t)) (window.__dgToasts=window.__dgToasts||[]).push(t); }); }");
        }
        // Grab the screenshot HERE, while the toast is still on screen — the app auto-navigates to the
        // list screen shortly after a successful save, so a screenshot taken later (e.g. the test's own
        // step() call) shows the list, not the toast that confirmed the save.
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: toast screenshot failed - " + e.getMessage()); }

        Object r = page.evaluate("() => { const a=window.__dgToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After a successful Save, open the list ({@code #/DepartmentGroup}) and confirm a grid row matches the saved
     *  Department + Group. Returns the matched row text, or "". */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Object r = page.evaluate("(v) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const dep=up(v.dep), grp=up(v.grp);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); const has=x=>x && cells.some(c=>c===x || c.includes(x));"
                + "   if(has(dep) && has(grp)) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " return ''; }", java.util.Map.of("dep", lastDepartment, "grp", lastGroup));
        return r == null ? "" : r.toString().trim();
    }
}
