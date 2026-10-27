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

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "";
    public String lastRoute = "";

    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        // The sidebar is built lazily and "Billing" is ALSO a top-level module listed before Application Configuration. Open each
        // level only if it is closed (a second click on an open entry CLOSES it) and look the next one up INSIDE its parent's li.
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*billing\\s*$");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bill=[...topLi.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const scope=(bill&&bill.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*department\\s*group\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__dgMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__dgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DepartmentGroup.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__dgMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        // Direct hash navigation is bounced to #/PatientDashboard by the app, so it is NOT a way in — only the menu link is.
        if (!onScreen()) {
            lastMenu = String.valueOf(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...new Set([...document.querySelectorAll('a')].filter(a=>a.offsetParent!==null)"
                    + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }"));
            return false;
        }
        return true;
    }

    /** On the Department Group list/add screen — the route AND the screen's own control must be there (a bounce to the dashboard fails). */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("departmentgroup")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a,input[type=button]')].some(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(((x.textContent||x.value)||'').replace(/\\s+/g,' ').trim()))"
                    + " || [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='departmentgrp.departmentid')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
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

    /** Text of every real (non "-Select-") Department option, in the order {@link #selectDepartmentOrdinal(int)} counts them. */
    public java.util.List<String> departmentNames() { return optionNames("departmentgrp.departmentid"); }

    /** Text of every real (non "-Select-") Group option, in the order {@link #selectGroupOrdinal(int)} counts them. */
    public java.util.List<String> groupNames() { return optionNames("departmentgrp.groupid"); }

    private java.util.List<String> optionNames(String selNg) {
        Object r = page.evaluate("(ng) => { const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).map(o=>(o.textContent||'').replace(/\\s+/g,' ').trim()); }", selNg);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Every Department + Group row on the LIST screen, read by scrolling the virtualized grid to the bottom. Each entry is
     * {@code "DEPARTMENT\tGROUP"} (upper-case, single-spaced). Must be called while the list is on screen.
     */
    public java.util.List<String> readListPairs() {
        try {
            page.waitForFunction("() => document.querySelectorAll('.ui-grid-row').length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        // A row's cells are read by their own text, keeping the first two NON-EMPTY ones (Department, Group): the leading "Sel" cell
        // holds only a tick icon, and a pinned column container repeats the rows with just that cell.
        Object r = page.evaluate("async () => { const sleep=ms=>new Promise(res=>setTimeout(res,ms)); const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase();"
                + " const vps=[...document.querySelectorAll('.ui-grid-viewport')]; const vp=vps.sort((a,b)=>b.scrollHeight-a.scrollHeight)[0]; const seen=new Set(); let sample='';"
                + " const grab=()=>document.querySelectorAll('.ui-grid-row').forEach(row=>{ const c=[...row.querySelectorAll('.ui-grid-cell')].map(x=>up(x.textContent)).filter(t=>t);"
                + "   if(!sample) sample=c.join(' | '); if(c.length>=2) seen.add(c[0]+'\\t'+c[1]); });"
                + " let steps=0;"
                + " if(vp){ vp.scrollTop=0; await sleep(300); let stable=0;"
                + "   for(;steps<600 && stable<3;steps++){ grab(); const before=vp.scrollTop; vp.scrollTop=before+Math.max(120,vp.clientHeight*0.7); await sleep(150); grab();"
                + "     if(vp.scrollTop===before){ stable++; } else { stable=0; } }"
                + "   vp.scrollTop=0; } else { grab(); }"
                + " return ['#probe viewport='+(vp?('found h='+vp.scrollHeight):'none')+' steps='+steps+' firstRow=['+sample+']'].concat([...seen]); }");
        java.util.List<String> out = new java.util.ArrayList<>();
        lastListProbe = "";
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) {
            String s = String.valueOf(o);
            if (s.startsWith("#probe")) lastListProbe = s; else out.add(s);
        }
        return out;
    }

    /** What {@link #readListPairs()} saw (grid viewport, scroll steps, first row's cells) — for the report when it finds nothing. */
    public String lastListProbe = "";

    /**
     * Type {@code text} into the grid's column filter number {@code col} (0 = Department, 1 = Group Name) with real keystrokes, so
     * the virtualized list narrows to the matching rows. Returns false when that filter box is not there.
     */
    public boolean filterList(int col, String text) {
        try {
            com.microsoft.playwright.Locator box = page.locator("input.ui-grid-filter-input").nth(col);
            if (box.count() == 0) return false;
            box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            box.fill("");
            box.pressSequentially(text, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(15));
            waitForAngular(1200);
            return true;
        } catch (Exception e) {
            System.out.println("DepartmentGroup.filterList: " + e.getMessage().split("\n")[0]);
            return false;
        }
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
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        return r == null ? "" : r.toString().replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
    }

    /** After a successful Save, open the list ({@code #/DepartmentGroup}) and confirm a grid row matches the saved
     *  Department + Group. Returns the matched row text, or "". */
    public String findAddedInList() {
        // The app returns to the list by itself shortly after a save; give it a moment before deciding it did not.
        for (int i = 0; i < 8 && page.url().toLowerCase().contains("add-"); i++) page.waitForTimeout(500);
        if (page.url().toLowerCase().contains("add-")) {
            // Setting location.hash from the form raises the app's "Reload page" popup, which the framework answers No — so leave
            // with the form's own Back button instead.
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')]"
                    + ".find(x=>/^\\s*back\\s*$/i.test((x.textContent||x.value||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__dgBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__dgBack").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("DepartmentGroup.findAddedInList: Back click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__dgBack'); if(e) e.removeAttribute('id'); }");
                waitForAngular(1500);
            }
        } else if (!page.url().toLowerCase().contains("departmentgroup")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        String row = scanForSavedRow();
        if (row.isEmpty() && filterList(0, lastDepartment)) {
            // The grid is virtualized and the new row is usually far below the first screenful: narrow it with the Department filter.
            row = scanForSavedRow();
            if (!row.isEmpty()) row += "  [found with the Department filter]";
        }
        return row;
    }

    /** The grid row (as currently rendered) holding the saved Department AND Group, or "". */
    private String scanForSavedRow() {
        Object r = page.evaluate("(v) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const dep=up(v.dep), grp=up(v.grp);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); const has=x=>x && cells.some(c=>c===x || c.includes(x));"
                + "   if(has(dep) && has(grp)) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " return ''; }", java.util.Map.of("dep", lastDepartment, "grp", lastGroup));
        return r == null ? "" : r.toString().trim();
    }
}
