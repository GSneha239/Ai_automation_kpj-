package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Bill Template Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Bill Template Master</b> (route
 * {@code #/BillTemplateList}) → <b>Add</b> ({@code #/add-BillTemplateMaster}) → enter <b>Code</b>,
 * <b>Template Name</b>, <b>Print Name</b>, <b>Group</b>, <b>Sub Group</b>, <b>Services</b>, <b>Quantity</b>,
 * <b>Unit Purchase Price</b> → click <b>Add</b> ({@code fnAddListOfServices}) to add the service line →
 * <b>Submit</b> ({@code fnIUDBillTemplate}) → success toast.</p>
 *
 * <p>Fields (discovered): {@code BillTemplate.code} / {@code .description} (Template Name) / {@code .printname};
 * cascade {@code .groupid} → {@code .subgroupid} → {@code .serviceitemid} (each loads after its parent);
 * {@code .quantity}; {@code .rate} (Unit Purchase Price); {@code .total} / {@code .totaltemplateamount} auto-compute.</p>
 */
public class BillTemplateMaster extends BasePage {

    public BillTemplateMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/BillTemplateList";
    public static String ADD_ROUTE = "#/add-BillTemplateMaster";
    public String lastCode = "";
    public String lastTemplateName = "";
    public String lastPrintName = "";

    /** Realistic bill template names — a Template Name reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] TEMPLATE_NAMES = {
            "Standard Consultation Package", "Basic Health Screening Package", "Minor Surgery Package",
            "Maternity Delivery Package", "Executive Health Checkup", "Diagnostic Imaging Package",
            "Physiotherapy Session Package", "Dental Care Package"
    };

    /** Print names shown on the printed bill — kept realistic too, paired index-for-index with the template name. */
    private static final String[] PRINT_NAMES = {
            "Consultation Package", "Health Screening", "Minor Surgery", "Delivery Package",
            "Executive Checkup", "Imaging Package", "Physiotherapy Package", "Dental Package"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/bill\\s*template\\s*master/i.test(norm(x.textContent)) || /billtemplate/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__btMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__btMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BillTemplateMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("BillTemplateMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("billtemplate");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-BillTemplateMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__btAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BillTemplateMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__btAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BillTemplateMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='BillTemplate.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("BillTemplateMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Enter Code, Template Name, Print Name; cascade Group → Sub Group → Services (each via a REAL Playwright
     *  selectOption so the ng-change loads the dependent — synthetic change does NOT); Quantity + Unit Purchase
     *  Price (rate). {@code attempt} shifts Code, Template Name and Print Name so a retry after an "already
     *  exists" toast submits genuinely different details. Returns a summary of the values set. */
    public String fillDetails(int attempt) {
        String code = "BT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastTemplateName = TEMPLATE_NAMES[attempt % TEMPLATE_NAMES.length] + (attempt >= TEMPLATE_NAMES.length ? " " + (attempt / TEMPLATE_NAMES.length + 1) : "");
        lastPrintName = PRINT_NAMES[attempt % PRINT_NAMES.length] + (attempt >= PRINT_NAMES.length ? " " + (attempt / PRINT_NAMES.length + 1) : "");
        // Text fields (JS binding is fine).
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('BillTemplate.code',a.code); set('BillTemplate.description',a.templateName); set('BillTemplate.printname',a.printName); }",
                java.util.Map.of("code", code, "templateName", lastTemplateName, "printName", lastPrintName));
        waitForAngular(300);
        // Not every Group has Sub Groups, and not every Sub Group has Services — search the Group→SubGroup→Service
        // cascade (real selectOption so ng-change fires) until a full chain is found.
        String group = "(no-opt)", subgroup = "(no-opt)", service = "(no-opt)";
        int groupCount = realCount("BillTemplate.groupid");
        boolean done = false;
        for (int gi = 1; gi <= Math.min(groupCount, 15) && !done; gi++) {
            String g = selectRealOrdinal("BillTemplate.groupid", gi);
            if (g.startsWith("(")) continue;
            waitForAngular(800);
            pollHasReal("BillTemplate.subgroupid", 8000);
            int subCount = realCount("BillTemplate.subgroupid");
            if (subCount == 0) continue; // this Group has no Sub Groups — try the next Group
            for (int sj = 1; sj <= Math.min(subCount, 15) && !done; sj++) {
                String s = selectRealOrdinal("BillTemplate.subgroupid", sj);
                if (s.startsWith("(")) continue;
                waitForAngular(800);
                pollHasReal("BillTemplate.serviceitemid", 12000);
                if (realCount("BillTemplate.serviceitemid") > 0) {
                    String sv = selectRealOrdinal("BillTemplate.serviceitemid", 1);
                    if (!sv.startsWith("(")) { group = g; subgroup = s; service = sv; done = true; }
                }
            }
            System.out.println("BillTemplate: group '" + g + "' subCount=" + subCount + (done ? " -> DONE" : " -> no service, next group"));
        }
        // Quantity + Unit Purchase Price (rate). NOTE: fnAddListOfServices validates quantity/rate but shows the
        // MISLABELED toast "Please Enter Discount (%)" when either is empty — so always set BOTH to real values.
        setQuantityAndRate();
        waitForAngular(400);
        Object rate = page.evaluate("() => { const r=[...document.querySelectorAll(\"[ng-model='BillTemplate.rate']\")].find(x=>x.offsetParent!==null); return r?r.value:''; }");
        return "Code=" + code + " | TemplateName=" + lastTemplateName + " | PrintName=" + lastPrintName
                + " | Group=" + group + " | SubGroup=" + subgroup + " | Services=" + service
                + " | Quantity=1 | UnitPurchasePrice=" + (rate == null ? "" : rate);
    }

    /** Set Quantity (1) and Unit Purchase Price / rate (100) — both non-empty (guards the mislabeled
     *  "Please Enter Discount (%)" validation which really means quantity/rate is empty). */
    private void setQuantityAndRate() {
        page.evaluate("() => { const A=window.angular; const $=window.jQuery;"
                + " const setNg=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); if($){try{$(e).trigger('input').trigger('change');}catch(x){}} };"
                + " setNg('BillTemplate.quantity','1'); setNg('BillTemplate.rate','100'); }");
        waitForAngular(300);
    }

    /** Count real (non "-Select-") options currently in the {@code selNg} dropdown. */
    private int realCount(String selNg) {
        Object c = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return 0; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }",
                "select[ng-model='" + selNg + "']");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Poll (up to {@code timeoutMs}) for the {@code selNg} dropdown to have at least one real option. */
    private void pollHasReal(String selNg, int timeoutMs) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    "select[ng-model='" + selNg + "']", new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
    }

    /** REAL Playwright selectOption of the {@code ordinal}-th (1-based) real option of {@code selNg} (fires the
     *  ng-change that loads dependents). Returns the chosen option text, or an {@code (…)} marker. */
    private String selectRealOrdinal(String selNg, int ordinal) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            page.locator(sel).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectRealOrdinal: selectOption failed for " + selNg + " - " + e.getMessage()); return "(err)"; }
        waitForAngular(600);
        return chosen;
    }

    /** Click the <b>Add</b> button ({@code fnAddListOfServices}) that adds the service line to the grid. Returns the
     *  toast (if any) OR "(row added)" if a detail row appeared, else "". */
    public String addServiceLine() {
        setQuantityAndRate(); // re-assert — selecting the service can async-reset rate to empty (mislabeled "Discount (%)")
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__btToasts=[]; if(window.__btObs) window.__btObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__btToasts.includes(t)) window.__btToasts.push(t); }); };"
                + " window.__btObs=new MutationObserver(grab); window.__btObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/fnAddListOfServices/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1200);
        Object toast = page.evaluate("() => { const a=window.__btToasts||[]; return a.find(x=>/service|added|success|please|select|enter|required/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        if (t.isEmpty() && after > before) t = "(service row added)";
        return t;
    }

    /** Click <b>Submit</b> ({@code fnIUDBillTemplate}) and return the success toast. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__btToasts=[]; if(window.__btObs) window.__btObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__btToasts.includes(t)) window.__btToasts.push(t); }); };"
                + " window.__btObs=new MutationObserver(grab); window.__btObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDBillTemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__btSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__btSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__btToasts||[]).some(a=>/template|bill|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__btToasts||[]).includes(t)) (window.__btToasts=window.__btToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__btToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
