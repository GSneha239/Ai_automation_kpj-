package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Concession Template</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Concession Template</b> (route
 * {@code #/concessiontemplateMaster}) → <b>Add</b> ({@code #/add-concessiontemplate}) → enter <b>Code</b>,
 * <b>Remark</b>, <b>Group</b>, <b>Percentage</b> → click <b>Add</b> ({@code AddConcessionDetails}) to add the
 * detail line → <b>Save</b> ({@code fnIUDConcessiontemplate}) → toast → verify in the list table.</p>
 *
 * <p>Fields (discovered): {@code ConcessionTemplate.Code}, {@code .Description} (Remark),
 * {@code .groupid} (Group select, 43 opts), {@code .percentage}. Add-line button {@code AddConcessionDetails(CTDetailsList)},
 * Save button {@code fnIUDConcessiontemplate()}.</p>
 */
public class ConcessionTemplate extends BasePage {

    public ConcessionTemplate(Page page) { super(page); }

    public static String LIST_ROUTE = "#/concessiontemplateMaster";
    public static String ADD_ROUTE = "#/add-concessiontemplate";
    public String lastCode = "", lastGroup = "", lastRemark = "";

    /** Realistic concession template names — a Remark reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] REMARKS = {
            "Staff Concession", "Senior Citizen Concession", "Corporate Discount Scheme",
            "Loyalty Member Concession", "Charity Patient Concession", "Government Panel Concession",
            "Referral Doctor Concession", "Long Stay Patient Concession"
    };

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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/concession\\s*template/i.test(norm(x.textContent)) || /concessiontemplate/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ctMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__ctMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("ConcessionTemplate.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("ConcessionTemplate.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("concessiontemplate");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-concessiontemplate}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ctAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ConcessionTemplate.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ctAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("ConcessionTemplate.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='ConcessionTemplate.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("ConcessionTemplate.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Enter Code, Remark (Description), Group (real selectOption first real), Percentage. {@code attempt} shifts
     *  Code and Remark so a retry after an "already exists" toast submits genuinely different details.
     *  Returns a summary. */
    public String fillDetails(int attempt) {
        String code = "CT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('ConcessionTemplate.Code',a.code); set('ConcessionTemplate.Description',a.remark); set('ConcessionTemplate.percentage','10'); }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(300);
        lastGroup = realSelectFirst("ConcessionTemplate.groupid");
        // re-assert percentage after the group change (guards any digest reset)
        page.evaluate("() => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='ConcessionTemplate.percentage']\")].find(x=>x.offsetParent!==null); if(e && !(''+e.value).trim()){ const c=A.element(e).controller('ngModel'); e.value='10'; if(c){c.$setViewValue('10');c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(300);
        return "Code=" + code + " | Remark=" + lastRemark + " | Group=" + lastGroup + " | Percentage=10";
    }

    private String realSelectFirst(String selNg) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { System.out.println("realSelectFirst: no option for " + selNg); return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst: selectOption failed for " + selNg + " - " + e.getMessage()); }
        waitForAngular(500);
        return chosen;
    }

    /** Click <b>Add</b> ({@code AddConcessionDetails}) to add the Group+Percentage detail line. Returns the toast, or
     *  "(detail row added)" if a grid row appeared, else "". */
    public String addDetailLine() {
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__ctToasts=[]; if(window.__ctObs) window.__ctObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ctToasts.includes(t)) window.__ctToasts.push(t); }); };"
                + " window.__ctObs=new MutationObserver(grab); window.__ctObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddConcessionDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1200);
        Object toast = page.evaluate("() => { const a=window.__ctToasts||[]; return a.find(x=>/detail|added|success|please|select|enter|required|percentage|group/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        if ((t.isEmpty() || t.toLowerCase().contains("added")) && after > before) t = t.isEmpty() ? "(detail row added)" : t;
        return t;
    }

    /** Click <b>Save</b> ({@code fnIUDConcessiontemplate}) and return the success toast (toastr cleared first). */
    public String saveAndGetToast() {
        Object tagged = page.evaluate("() => { window.__ctToasts=[]; if(window.__ctObs) window.__ctObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ctToasts.includes(t)) window.__ctToasts.push(t); }); };"
                + " window.__ctObs=new MutationObserver(grab); window.__ctObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDConcessiontemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ctSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__ctSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveAndGetToast: Save click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__ctToasts||[]).some(a=>/concession|template|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ctToasts||[]).includes(t)) (window.__ctToasts=window.__ctToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ctToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Save, open the list ({@code #/concessiontemplateMaster}) and confirm a grid row matches the saved Code.
     *  Returns the matched row text, or "". */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " return ''; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
