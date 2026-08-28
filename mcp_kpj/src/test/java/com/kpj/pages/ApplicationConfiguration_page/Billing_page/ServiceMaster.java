package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Service Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Service Master</b> (route
 * {@code #/serviceItemMaster}) → <b>Add</b> ({@code #/add-serviceItem/serviceMaster}) → enter <b>Service Name</b>
 * ({@code serviceMst.ServiceItemName}), <b>Group</b> ({@code serviceMst.GroupId}), <b>SubGroup</b>
 * ({@code serviceMst.SubGroupId}) → fill <b>Pricing Policy Details*</b> (Unit Purchase Price {@code tt.rate}) →
 * enter <b>HSN Code</b> ({@code serviceMst.ClinicalCode}) + <b>Code Type</b> ({@code serviceMst.CodeType}) →
 * click <b>Add</b> ({@code AddCode()}) → select <b>Tax Details</b> (tick {@code tt.Isdefault}) → <b>Submit</b>
 * ({@code fnIUDServiceMaster}) → toast.</p>
 */
public class ServiceMaster extends BasePage {

    public ServiceMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/serviceItemMaster";
    public static String ADD_ROUTE = "#/add-serviceItem/serviceMaster";
    public String lastName = "", lastGroup = "", lastCode = "";

    /** Realistic Service names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] SERVICE_NAMES = {
            "Complete Blood Count", "X-Ray Chest", "Ultrasound Abdomen", "ECG Recording", "MRI Brain Scan",
            "CT Scan Thorax", "Blood Glucose Test", "Urine Routine Examination", "Physiotherapy Session",
            "Wound Dressing"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*service\\s*master\\s*$/i.test(norm(x.textContent)) || /serviceitemmaster/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__smMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__smMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("ServiceMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("ServiceMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("serviceitem");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-serviceItem/serviceMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null && /AddServiceItem/.test(x.getAttribute('ng-click')||''))"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__smAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ServiceMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__smAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("ServiceMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            // heavy form — wait for the Service Name input AND the Group dropdown's real options to load, AND a
            // Pricing Policy (tt.rate) row to render, else fillHeader/fillPricingPolicy race an empty form.
            page.waitForFunction("() => { const nm=[...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='serviceMst.ServiceItemName');"
                    + " const gp=document.querySelector(\"select[ng-model='serviceMst.GroupId']\"); const gok=gp && [...gp.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim()));"
                    + " const pr=[...document.querySelectorAll(\"input[ng-model='tt.rate']\")].some(e=>e.offsetParent!==null); return nm && gok && pr; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("ServiceMaster.clickAdd: add form not fully ready"); }
        waitForAngular(1200);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private int selectableCount(String selNg) {
        Object c = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return 0; return [...e.options].filter(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }",
                "select[ng-model='" + selNg + "']");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    private String selectOrdinal(String selNg, int ordinal) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && (x.o.textContent||'').trim() && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectOrdinal " + selNg + " failed: " + e.getMessage()); return "(err)"; }
        waitForAngular(500);
        return chosen;
    }

    private String realSelectFirst(String selNg) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(500);
        return chosen;
    }

    /** Fill Service Name (+ Code/ShortName defensively), Group, SubGroup. {@code attempt} shifts BOTH the Code
     *  and the Service Name so a retry after an "already exists" toast submits genuinely different details. */
    public String fillHeader(int attempt) {
        String code = "SM" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastName = SERVICE_NAMES[attempt % SERVICE_NAMES.length] + (attempt >= SERVICE_NAMES.length ? " " + (attempt / SERVICE_NAMES.length + 1) : "");
        lastCode = code;
        setNg("serviceMst.ServiceItemCode", code);
        setNg("serviceMst.ServiceItemShortName", code);
        setNg("serviceMst.ServiceItemName", lastName);
        waitForAngular(300);
        // Group + SubGroup are BOTH required — not every Group has SubGroups, so search for one that does.
        String group = "(no-opt)", sub = "(no-opt)";
        int groupCount = selectableCount("serviceMst.GroupId");
        for (int gi = 1; gi <= Math.min(groupCount, 15); gi++) {
            String g = selectOrdinal("serviceMst.GroupId", gi);
            if (g.startsWith("(")) continue;
            waitForAngular(700);
            // SubGroup loads async after the Group ng-change — poll before checking.
            try {
                page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='serviceMst.SubGroupId']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(6000));
            } catch (Exception ignore) { }
            if (selectableCount("serviceMst.SubGroupId") > 0) {
                String s = selectOrdinal("serviceMst.SubGroupId", 1);
                if (!s.startsWith("(")) { group = g; sub = s; break; }
            }
        }
        lastGroup = group;
        return "ServiceName=" + lastName + " | Code=" + code + " | Group=" + group + " | SubGroup=" + sub;
    }

    /** Fill Pricing Policy Details* — set every visible Unit Purchase Price ({@code tt.rate}) input to a value. */
    public String fillPricingPolicy() {
        Object n = page.evaluate("() => { const A=window.angular; let n=0; [...document.querySelectorAll(\"input[ng-model='tt.rate']\")].filter(e=>e.offsetParent!==null).forEach(e=>{ const c=A.element(e).controller('ngModel'); e.value='100'; if(c){c.$setViewValue('100');c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); n++; }); return n; }");
        waitForAngular(400);
        return "Unit Purchase Price set on " + n + " pricing row(s)";
    }

    /** Enter HSN Code + Code Type, click <b>Add</b> ({@code AddCode}) to add the code row. */
    public String addCode() {
        // Code Type — prefer an option mentioning HSN, else first real.
        String codeType = "(no)";
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='serviceMst.CodeType']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
            Object idx = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='serviceMst.CodeType']\"); let i=[...e.options].findIndex(o=>o.value && /hsn/i.test(o.textContent||'')); if(i<0) i=[...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); return i; }");
            int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
            page.locator("select[ng-model='serviceMst.CodeType']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            codeType = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='serviceMst.CodeType']\"); return (e.options[e.selectedIndex]||{}).textContent||''; }")).trim();
        } catch (Exception e) { System.out.println("addCode: CodeType select failed - " + e.getMessage()); }
        setNg("serviceMst.ClinicalCode", "9993");      // HSN Code value
        setNg("serviceMst.ServiceName", lastName);
        setNg("serviceMst.TaxCode", "9993");
        waitForAngular(300);
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddCode/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(900);
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        return "CodeType=" + codeType + " | HSNCode=9993 | codeRowAdded=" + (after > before);
    }

    /** Select Tax Details — tick the first Tax default checkbox ({@code tt.Isdefault}) / selectall. */
    public String selectTax() {
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='tt.Isdefault']\")].find(x=>x.offsetParent!==null && !x.checked); if(!cb) return false; cb.id='__smTax'; return true; }");
        String r = "(none)";
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__smTax").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); r = "ticked a tax default"; }
            catch (Exception e) { try { page.locator("#__smTax").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); r = "clicked a tax default"; } catch (Exception e2) { System.out.println("selectTax: failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__smTax'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return r;
    }

    /** Click <b>Submit</b> ({@code fnIUDServiceMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__smToasts=[]; if(window.__smObs) window.__smObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__smToasts.includes(t)) window.__smToasts.push(t); }); };"
                + " window.__smObs=new MutationObserver(grab); window.__smObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDServiceMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__smSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__smSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__smToasts||[]).some(a=>/service|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__smToasts||[]).includes(t)) (window.__smToasts=window.__smToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__smToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list and confirm the added Service is there. The list grid is server-loaded via
     *  {@code fngetServiceItemGrid()} — set the Group search ({@code servicesearch.groupsearchid}) to the saved
     *  Group, click Search, then match the saved Service Name. */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        waitForAngular(1800);
        String token = lastCode.trim();   // the Code is the first grid column — most reliably matchable
        // Select the saved Group in the list's Group search dropdown via a REAL selectOption (its ng-change must
        // fire to apply the criterion; a synthetic change loads 0 rows). Also select Location = first real (KPJ).
        try {
            Object gi = page.evaluate("(g) => { const e=document.querySelector(\"select[ng-model='servicesearch.groupsearchid']\"); if(!e) return -1; return [...e.options].findIndex(o=>((o.textContent||'').replace(/\\s+/g,' ').trim().toUpperCase())===g.toUpperCase()); }", lastGroup.toUpperCase());
            int g = gi instanceof Number ? ((Number) gi).intValue() : -1;
            if (g >= 0) page.locator("select[ng-model='servicesearch.groupsearchid']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(g));
            waitForAngular(700);
        } catch (Exception e) { System.out.println("findAddedInList: group search select failed - " + e.getMessage()); }
        try {
            Object li = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='servicesearch.locationid']\"); if(!e) return -1; return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
            int l = li instanceof Number ? ((Number) li).intValue() : -1;
            if (l >= 0) page.locator("select[ng-model='servicesearch.locationid']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(l));
            waitForAngular(500);
        } catch (Exception ignore) { }
        // REAL click on Search (fngetServiceItemGrid ng-click won't fire on a synthetic click).
        Object stag = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/fngetServiceItemGrid/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__smSearch'; return true; }");
        if (Boolean.TRUE.equals(stag)) {
            try { page.locator("#__smSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("findAddedInList: Search click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__smSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(2500);
        // type the name into the first column filter to narrow the loaded rows
        page.evaluate("(t) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=t; if(c){c.$setViewValue(t);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", token);
        try {
            page.waitForFunction("(t) => { const up=s=>(s||'').toUpperCase(); const tt=up(t);"
                    + " if([...document.querySelectorAll('.ui-grid-row, table tbody tr')].some(r=>up(r.textContent||'').includes(tt))) return true;"
                    + " let hit=false; document.querySelectorAll('*').forEach(el=>{ if(hit) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d&&d.some(x=>up(JSON.stringify(x)).includes(tt))) hit=true; }catch(e){} }); return hit; }",
                    token, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(800);
        Object r = page.evaluate("(t) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const tt=up(t);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tbody tr')];"
                + " for(const row of rows){ if(up(row.textContent||'').includes(tt)) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>up(JSON.stringify(x)).includes(tt)); if(m) found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } }catch(e){} }); return found; }", token);
        return r == null ? "" : r.toString().trim();
    }
}
