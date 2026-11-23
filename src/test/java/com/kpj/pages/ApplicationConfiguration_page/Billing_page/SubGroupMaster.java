package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Sub Group Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Sub Group</b> (route {@code #/SubGroup}) →
 * <b>Add</b> ({@code #/add-SubGroup}) → enter <b>Location</b>, <b>Group</b>, <b>Sub Group Code</b>,
 * <b>Sub Group</b> → enter <b>Depreciation Field</b> + <b>Value</b> → click <b>Add</b>
 * ({@code AddDepreciationDetails}) → <b>Submit</b> ({@code fnIUDSubGroup}) → toast → verify in the table.</p>
 *
 * <p>Fields: {@code subgroup.LocationID} (Location select), {@code subgroup.GroupID} (Group select),
 * {@code subgroup.SubGroupCode}, {@code subgroup.SubGroupName}; depreciation detail
 * {@code subgroup1.DepreciationFieldID} (select) + {@code subgroup1.Value}.</p>
 */
public class SubGroupMaster extends BasePage {

    public SubGroupMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/SubGroup";
    public static String ADD_ROUTE = "#/add-SubGroup";
    public String lastCode = "", lastGroup = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // "Billing" is ALSO a top-level module in the sidebar, listed before Application Configuration: an unscoped lookup clicks
        // that one, which collapses the Application Configuration menu again. Look only inside the Application Configuration entry.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); const scope=(top&&top.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*sub\\s*group\\s*$/i.test(norm(x.textContent)) || /#\\/subgroup\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__sgMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__sgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("SubGroupMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("SubGroupMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("subgroup");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-SubGroup}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__sgAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("SubGroupMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__sgAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("SubGroupMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            // wait for the form (Location select) AND the Group select's real options (loads async). Sub Group Code is no longer a
            // text box - it is a select2 dropdown that only fills in once Group is chosen.
            page.waitForFunction("() => { const c=!!document.querySelector(\"select[ng-model='subgroup.LocationID']\");"
                    + " const g=document.querySelector(\"select[ng-model='subgroup.GroupID']\"); const gok=g && [...g.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); return c && gok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("SubGroupMaster.clickAdd: add form/Group options not ready"); }
        waitForAngular(1000);
        return true;
    }

    /** Diagnostics: every visible select / input on the form with its ng-model (and a select's first options). Never fails the flow. */
    public String describeControls() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>e && e.offsetParent!==null;"
                + " const sel=[...document.querySelectorAll('select')].map(e=>'SELECT'+(vis(e)?'':'(hidden)')+' [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length"
                + "   +' first=['+[...e.options].slice(0,4).map(o=>norm(o.textContent).slice(0,24)).join(' | ')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis).filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT [ng='+(e.getAttribute('ng-model')||'?')+']'+(e.value?'='+e.value:''));"
                + " const any=[...document.querySelectorAll('[ng-model*=\"subgroup\" i],ui-select,.ui-select-container,.select2-container,.chosen-container')]"
                + "   .map(e=>'ANY <'+e.tagName.toLowerCase()+'> ng-model='+(e.getAttribute('ng-model')||'-')+' class='+norm(e.className).slice(0,50)+(vis(e)?'':' (hidden)')+' text=\"'+norm(e.textContent).slice(0,40)+'\"');"
                + " return sel.concat(inp).concat(any).join('\\n'); }");
        String out = r == null ? "" : r.toString();
        System.out.println("SubGroupMaster controls:\n" + out);
        return out;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
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

    /**
     * Choose the {@code ordinal}-th real row (1-based, wrapping round the list) of a select2 dropdown whose hidden input has the
     * given ng-model. The Sub Group Code and Sub Group boxes are select2 widgets - there is no text box to type in and no native
     * select to set, so typing a value into them (as this page used to) silently did nothing and Submit then answered
     * "Please Enter Sub Group Code!".
     *
     * <p>If {@code typed} is given it is entered in the dropdown's search box first: that box offers whatever is typed as a
     * choice of its own, which is how a NEW value (e.g. a new Sub Group Code) is created from the dropdown. The row whose
     * text equals {@code typed} is then chosen.</p>
     *
     * @return the chosen row's text, or {@code (no-select2)} / {@code (no-choices)}
     */
    private String selectSelect2(String hiddenNg, int ordinal, String typed) {
        Object tagged = page.evaluate("(ng) => { const inp=document.querySelector(\"input[ng-model='\"+ng+\"']\"); if(!inp) return false;"
                + " let c=inp.previousElementSibling; while(c && !/select2-container/.test(c.className||'')) c=c.previousElementSibling;"
                + " if(!c && inp.parentElement) c=inp.parentElement.querySelector('.select2-container'); if(!c) return false;"
                + " c.setAttribute('data-s2','pick'); return true; }", hiddenNg);
        if (!Boolean.TRUE.equals(tagged)) return "(no-select2)";
        try {
            // A select2 left open by the previous pick puts its "select2-drop-mask" over the page, and every click then fails with
            // "select2-drop-mask intercepts pointer events". Close any open one first.
            if (Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector('.select2-drop-mask, .select2-drop-active')"))) {
                page.keyboard().press("Escape");
                page.waitForTimeout(300);
            }
            page.locator("[data-s2='pick'] .select2-choice").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            // When a value is to be TYPED the list may hold no existing rows at all (the Sub Group list is empty for a new code),
            // so wait for the search box rather than for a row.
            // A select2 that was filled earlier KEEPS its "select2-drop-active" class after it closes (hidden), so once the Sub Group Code has been
            // chosen the Sub Group box opens a SECOND active drop and ".select2-drop-active .select2-input" resolves to the stale, invisible
            // first one — fill() then waited its full 30 s for an element that never becomes visible. Only a drop that is actually showing counts.
            page.waitForFunction(typed != null
                            ? "() => [...document.querySelectorAll('.select2-drop-active')].some(d=>(d.offsetWidth||d.offsetHeight) && d.querySelector('.select2-input'))"
                            : "() => [...document.querySelectorAll('.select2-drop-active')].filter(d=>d.offsetWidth||d.offsetHeight)"
                                    + ".some(d=>d.querySelectorAll('.select2-results li.select2-result-selectable').length > 0)",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
            if (typed != null) {
                page.locator(".select2-drop-active .select2-input:visible").first().fill(typed);
                page.waitForTimeout(1500);
            }
            Object chosen = page.evaluate("([n, t]) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const drop=[...document.querySelectorAll('.select2-drop-active')].find(d=>d.offsetWidth||d.offsetHeight);"
                    + " const rows=(drop? [...drop.querySelectorAll('.select2-results li.select2-result-selectable')] : [])"
                    + ".filter(r=>!/^\\s*-*\\s*select/i.test(r.textContent||''));"
                    + " if(!rows.length) return '';"
                    + " const r=(t && rows.find(x=>norm(x.textContent).toLowerCase()===t.toLowerCase())) || rows[(n-1)%rows.length];"
                    + " r.setAttribute('data-s2row','1'); return norm(r.textContent); }", java.util.Arrays.asList(ordinal, typed));
            String text = chosen == null ? "" : chosen.toString();
            if (text.isEmpty() && typed != null) {
                // The list offers no row for the typed text: accept it as a new value with Enter, then read back what the box holds.
                page.locator(".select2-drop-active .select2-input:visible").first().press("Enter");
                page.waitForTimeout(700);
                Object shown = page.evaluate("(ng) => { const inp=document.querySelector(\"input[ng-model='\"+ng+\"']\"); if(!inp) return '';"
                        + " let c=inp.previousElementSibling; while(c && !/select2-container/.test(c.className||'')) c=c.previousElementSibling;"
                        + " const t=c? (c.querySelector('.select2-chosen')||{}).textContent : ''; return (t||'').replace(/\\s+/g,' ').trim(); }", hiddenNg);
                String s = shown == null ? "" : shown.toString();
                if (!s.isEmpty() && !s.toLowerCase().startsWith("select")) return s + " (typed, entered with Enter)";
            }
            if (text.isEmpty()) return "(no-choices)";
            page.locator("[data-s2row='1']").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            return text;
        } catch (Exception e) {
            System.out.println("SubGroupMaster.selectSelect2 " + hiddenNg + " failed: " + e.getMessage());
            return "(no-choices)";
        } finally {
            page.evaluate("() => { document.querySelectorAll('[data-s2]').forEach(e=>e.removeAttribute('data-s2'));"
                    + " document.querySelectorAll('[data-s2row]').forEach(e=>e.removeAttribute('data-s2row')); }");
            waitForAngular(400);
        }
    }

    /**
     * Select Location, Group, Sub Group Code and Sub Group - the last two through their dropdowns. {@code attempt} gives a
     * different (new) Sub Group Code each time, so a retry after an "already exists" toast submits another code.
     */
    public String fillHeader(int attempt) {
        String loc = realSelectFirst("subgroup.LocationID");
        String grp = realSelectFirst("subgroup.GroupID");
        for (int a = 0; a < 3 && grp.startsWith("("); a++) { waitForAngular(700); grp = realSelectFirst("subgroup.GroupID"); }
        lastGroup = grp;
        waitForAngular(1500);                   // the Sub Group Code / Sub Group dropdowns fill in once Group is chosen
        // Sub Group Code and Sub Group are both chosen from their dropdowns (click the box, a list opens, pick a row). The attempt
        // number picks the row, so an "already exists" answer is followed by a DIFFERENT value from the same dropdown. The two lists
        // run in step (row n of Sub Group Code is row n of Sub Group), so the pair stays consistent.
        // Every row in these lists already exists (86 in a row were refused with "Subgroupcode already exist!"), so type a NEW
        // unique code and name: the dropdown's search box offers whatever is typed as a choice of its own.
        // Stamped from the wall clock: System.nanoTime() ticks in 100 ns steps on this machine, so its remainders end in "00" and a code
        // taken from them came from only ~1,000 values — too few for a master list that keeps every record ever saved.
        String stamp = String.format("%05d", (System.currentTimeMillis() + attempt * 7919L) % 100000);
        String code = selectSelect2("subGroupCodeSelection", attempt + 1, "SG" + stamp);
        lastCode = code;
        String name = selectSelect2("subGroupNameSelection", attempt + 1, "Auto Sub Group " + stamp);
        return "Location=" + loc + " | Group=" + grp + " | SubGroupCode=" + code + " | SubGroup=" + name;
    }

    /** Enter Depreciation Field + Value, then click <b>Add</b> ({@code AddDepreciationDetails}) to add the line. */
    public String addDepreciationDetail() {
        String field = realSelectFirst("subgroup1.DepreciationFieldID");
        setNg("subgroup1.Value", "100");
        waitForAngular(300);
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__sgToasts=[]; if(window.__sgObs) window.__sgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sgToasts.includes(t)) window.__sgToasts.push(t); }); };"
                + " window.__sgObs=new MutationObserver(grab); window.__sgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddDepreciationDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object toast = page.evaluate("() => { const a=window.__sgToasts||[]; return a.find(x=>/deprecia|detail|added|success|please|select|enter|value|field/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        String rowMsg = (after > before) ? "(detail row added)" : (t.isEmpty() ? "(no row/toast)" : t);
        return "DepreciationField=" + field + " | Value=100 | " + rowMsg;
    }

    /** Click <b>Submit</b> ({@code fnIUDSubGroup}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__sgToasts=[]; if(window.__sgObs) window.__sgObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sgToasts.includes(t)) window.__sgToasts.push(t); }); };"
                + " window.__sgObs=new MutationObserver(grab); window.__sgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDSubGroup/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__sgSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__sgSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__sgToasts||[]).some(a=>/sub\\s*group|subgroup|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sgToasts||[]).includes(t)) (window.__sgToasts=window.__sgToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__sgToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/SubGroup}) and confirm a grid row matches the saved Sub Group Code. */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        page.evaluate("(code) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=code; if(c){c.$setViewValue(code);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastCode);
        try {
            page.waitForFunction("(code) => { const up=s=>(s||'').toUpperCase(); const cc=up(code); return [...document.querySelectorAll('.ui-grid-row, table tbody tr')].some(r=>up(r.textContent||'').includes(cc)); }",
                    lastCode, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(700);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tbody tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>up(JSON.stringify(x)).includes(cc)); if(m) found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } }catch(e){} }); return found; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
