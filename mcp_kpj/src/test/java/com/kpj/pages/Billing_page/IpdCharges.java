package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Billing &gt; <b>IPD Charges</b> (route {@code #/IPDCharges}) — Page Object (transactional bill posting).
 *
 * <p>Flow (discovered live): <b>Billing</b> → <b>IPD Charges</b> → pick an admitted IPD patient via the patient
 * picker popup ({@code OpenPopupScreen()} → filter IPD → {@code SearchPatient(0)} → click a row's
 * {@code SetSearchPatient(PR)}) which loads the admission ({@code PatientDetails.IPDNo}/ward/bed) → add a service
 * charge via the jQuery-UI autocomplete on <b>Service Name</b> ({@code #txtServiceName}) + quantity → fill the
 * mandatory bill fields (Charge Date, Cash Counter) → <b>Save</b> ({@code IUDBill()}).</p>
 */
public class IpdCharges extends BasePage {

    public IpdCharges(Page page) { super(page); }

    public String route = "", lastPatient = "", lastIpd = "", lastService = "", lastToast = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/ipd\\s*charge/i.test(norm(x.textContent)) || /#\\/IPDCharges/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ipcMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__ipcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("IpdCharges.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("ipdcharges")) {
            try { page.evaluate("() => { window.location.hash = '#/IPDCharges'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        // wait for the MRN input / patient-picker button to render
        try {
            page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/OpenPopupScreen/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("IpdCharges.nav: picker button not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("ipdcharges"); }

    /** Open the picker, filter IPD, search, and click the patient row at {@code rowIndex}. Returns the loaded
     *  IPDNo (empty if that row didn't load an admission). Re-openable to try different patients. */
    public String pickPatientByRow(int rowIndex) {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        page.evaluate("() => { const rs=[...document.querySelectorAll(\"input[type=radio][ng-model='PatientData.OPD_IPD']\")]; const ipd=rs.find(r=>/ipd/i.test((r.closest('label')||r.parentElement||{}).textContent||'')); if(ipd){ ipd.click(); ipd.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__popSearch'; }");
        try { page.locator("#__popSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch'); if(e) e.removeAttribute('id'); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, .modal tbody tr')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')).length > 0", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        waitForAngular(500);
        Object picked = page.evaluate("(idx) => { const rows=[...document.querySelectorAll('.ui-grid-row, .modal tbody tr')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')); const r=rows[idx]; if(!r) return null; const sel=r.querySelector('[ng-click*=SetSearchPatient]')||r.querySelector('[ng-click]')||r; sel.id='__pRow'; return r.textContent.replace(/\\s+/g,' ').trim().slice(0,45); }", rowIndex);
        if (picked == null) return "";
        try { page.locator("#__pRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__pRow'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);
        Object st = page.evaluate("() => { let ipd='', mrn=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.PatientDetails){ if(s.PatientDetails.IPDNo) ipd=s.PatientDetails.IPDNo; if(s.PatientDetails.MRNo) mrn=s.PatientDetails.MRNo; } }catch(e){} }); return {ipd, mrn}; }");
        @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) st;
        String ipd = m != null && m.get("ipd") != null ? m.get("ipd").toString() : "";
        if (!ipd.isEmpty()) { lastIpd = ipd; lastPatient = m.get("mrn") != null ? m.get("mrn").toString() : ""; }
        // close popup if still open
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/CloseModel/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(600);
        return ipd;
    }

    /** Click <b>Get Bill</b> ({@code fnGetZTCareBillDetails}) and return the resulting charge-grid row count. */
    public int getBill() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/fnGetZTCareBillDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__getbill'; }");
        try { page.locator("#__getbill").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__getbill'); if(e) e.removeAttribute('id'); }");
        waitForAngular(4000);
        return chargeGridRows();
    }

    // ---- patient selection (popup) --------------------------------------

    /** Open the patient picker, filter to IPD, search, and click patient rows until one loads an IPD admission.
     *  Returns the loaded IPDNo (empty if none). */
    public String selectIpdPatient() {
        // open popup
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(800);
        // filter IPD + search
        page.evaluate("() => { const rs=[...document.querySelectorAll(\"input[type=radio][ng-model='PatientData.OPD_IPD']\")]; const ipd=rs.find(r=>/ipd/i.test((r.closest('label')||r.parentElement||{}).textContent||'')); if(ipd){ ipd.click(); ipd.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'')); if(b){ b.id='__popSearch'; } }");
        try { page.locator("#__popSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch'); if(e) e.removeAttribute('id'); }");
        // wait for result rows (rows containing a 6+ digit MRN)
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, .modal tbody tr')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')).length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("selectIpdPatient: no popup rows"); }
        waitForAngular(600);
        // iterate rows, click the SetSearchPatient button, check admission loads
        for (int i = 0; i < 8; i++) {
            Object picked = page.evaluate("(idx) => { const rows=[...document.querySelectorAll('.ui-grid-row, .modal tbody tr')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')); const r=rows[idx]; if(!r) return null; const sel=r.querySelector('[ng-click*=SetSearchPatient]')||r.querySelector('[ng-click]')||r; sel.id='__pRow'; return r.textContent.replace(/\\s+/g,' ').trim().slice(0,45); }", i);
            if (picked == null) break;
            try { page.locator("#__pRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__pRow'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
            Object st = page.evaluate("() => { let ipd='', mrn=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.PatientDetails){ if(s.PatientDetails.IPDNo) ipd=s.PatientDetails.IPDNo; if(s.PatientDetails.MRNo) mrn=s.PatientDetails.MRNo; } }catch(e){} }); return {ipd, mrn}; }");
            @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) st;
            String ipd = m != null && m.get("ipd") != null ? m.get("ipd").toString() : "";
            if (!ipd.isEmpty()) {
                lastIpd = ipd; lastPatient = m.get("mrn") != null ? m.get("mrn").toString() : "";
                System.out.println("selectIpdPatient: loaded " + lastPatient + " / " + ipd + " (row " + i + " = " + picked + ")");
                // ensure the popup is closed
                page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/CloseModel/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(800);
                return ipd;
            }
        }
        return "";
    }

    // ---- service charge (jQuery-UI autocomplete) ------------------------

    /** Add one service charge via the Service Name autocomplete. Returns the picked service name (or a marker). */
    public String addServiceCharge() {
        // Diagnose the service-name input once.
        Object diag = page.evaluate("() => { const els=[...document.querySelectorAll('#txtServiceName, input[ng-model=\\'ServiceData.ServiceName\\']')]; return { count:els.length, visible:els.filter(e=>e.offsetParent!==null).length, disabled:els.map(e=>e.disabled) }; }");
        System.out.println("addServiceCharge: ServiceName input diag => " + diag);
        // Tag the VISIBLE ServiceName input (there are 2; only one is shown).
        Object tag = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='ServiceData.ServiceName']\")].find(x=>x.offsetParent!==null); if(!e) return false; e.id='__svcName'; return true; }");
        if (!Boolean.TRUE.equals(tag)) { System.out.println("addServiceCharge: no visible ServiceName input"); return "(no service input)"; }
        String[] queries = { "con", "gen", "ward", "nur", "char", "room", "bed", "med", "a", "e" };
        for (String q : queries) {
            try {
                com.microsoft.playwright.Locator svc = page.locator("#__svcName");
                svc.scrollIntoViewIfNeeded();
                svc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
                svc.fill("");
                svc.pressSequentially(q, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(180));
            } catch (Exception e) { System.out.println("addServiceCharge: type '" + q + "' failed - " + e.getClass().getSimpleName()); continue; }
            // wait for autocomplete items
            boolean have = false;
            try {
                page.waitForFunction("() => [...document.querySelectorAll('ul.ui-autocomplete li, .ui-autocomplete li.ui-menu-item')].some(e=>e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(4000));
                have = true;
            } catch (Exception ignore) { }
            if (!have) {
                Object probe = page.evaluate("() => { const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const n=document.querySelector('#__svcName');"
                        + " const menus=[...document.querySelectorAll('ul.ui-autocomplete, .ui-menu, [role=listbox], .autocomplete-suggestions, .dropdown-menu')].map(e=>({cls:e.className.slice(0,40), vis:e.offsetParent!==null, items:e.querySelectorAll('li,a,div').length}));"
                        + " const jq=!!(window.jQuery && window.jQuery('#__svcName').data && window.jQuery('#__svcName').data('uiAutocomplete'));"
                        + " return { typedVal:n?n.value:'(gone)', jqAutocompleteBound:jq, menus:menus.slice(0,6) }; }");
                System.out.println("addServiceCharge: q='" + q + "' no items; probe => " + probe);
                continue;
            }
            page.evaluate("() => { const li=[...document.querySelectorAll('ul.ui-autocomplete li, .ui-autocomplete li.ui-menu-item')].find(e=>e.offsetParent!==null); if(li){ const a=li.querySelector('a')||li; a.id='__acitem'; } }");
            try { page.locator("#__acitem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__acitem'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
            // set quantity = 1
            setNg("ServiceData.quantity", "1");
            waitForAngular(400);
            // read what got picked
            Object picked = page.evaluate("() => { const n=document.querySelector('#__svcName'); const c=[...document.querySelectorAll(\"input[ng-model='ServiceData.ServiceCode']\")].find(x=>x.offsetParent!==null); return { name:n?n.value:'', code:c?c.value:'' }; }");
            @SuppressWarnings("unchecked") java.util.Map<String,Object> pk = (java.util.Map<String,Object>) picked;
            String name = pk != null && pk.get("name") != null ? pk.get("name").toString() : "";
            if (!name.isEmpty()) {
                lastService = name;
                // trigger add to the charge grid (Enter on quantity, or an add control)
                addServiceToGrid();
                System.out.println("addServiceCharge: picked '" + name + "' (query '" + q + "')");
                return name;
            }
        }
        return "(no service picked)";
    }

    /** Push the currently-entered ServiceData row into the charge grid (Enter on the qty box or an add handler). */
    private void addServiceToGrid() {
        // Try Enter on the quantity field (common commit for these rows).
        try { page.locator("input[ng-model='ServiceData.quantity']").first().press("Enter"); } catch (Exception ignore) { }
        waitForAngular(700);
        // Or a dedicated add control near the service row.
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,i,span')].find(x=>/AddService|AddItem|fnAddService|AddCharge|AddBillDetail|addToGrid/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(800);
    }

    public int chargeGridRows() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) n=Math.max(n,s.grid.options.data.length); }catch(e){} }); return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    // ---- Package / Cancelled Charges tabs (read-only, plain <table>s — NOT the ui-grid Bill Details uses) -----

    /**
     * Switch to a sibling tab by its visible text and read its table (header row + up to {@code maxRows} data
     * rows). These two tabs are plain Bootstrap tabs (real {@code data-toggle="tab"} `<a>`s, no {@code ng-click})
     * — a synthetic/JS `.click()` gets intercepted by the still-active Bill Details panel sitting underneath
     * (confirmed live: Playwright's real click retried for 5s and never got through), so drive them via jQuery's
     * own {@code $(el).tab('show')}, exactly what Bootstrap's click handler does internally.
     *
     * <p>Both tabs are legitimately EMPTY for a patient with no package admission / no cancelled charges — 0 rows
     * is a normal data state here, not a defect (same category as the "no vacant bed" cases elsewhere in this
     * project), so callers should report the row count rather than fail on zero.</p>
     */
    private String readTabbedTable(String tabLinkText, String panelSelector) {
        Object tagged = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const el=[...document.querySelectorAll('a')].find(x=>x.offsetParent!==null && new RegExp('^\\\\s*'+a+'\\\\s*$','i').test(norm(x.textContent)));"
                + " if(!el) return false; el.id='__ipcTabLink'; return true; }", tabLinkText);
        if (!Boolean.TRUE.equals(tagged)) return "(tab link '" + tabLinkText + "' not found)";
        page.evaluate("() => { const $=window.jQuery; const a=document.querySelector('#__ipcTabLink'); if($ && a) $(a).tab('show'); }");
        page.evaluate("() => { const e=document.getElementById('__ipcTabLink'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        Object r = page.evaluate("(sel) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const panel=document.querySelector(sel); if(!panel) return 'no panel';"
                + " const t=panel.querySelector('table'); if(!t) return 'no table in panel';"
                + " const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent)).filter(Boolean);"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent));"
                + " return JSON.stringify({ heads, rowCount: rows.length, sample: rows.slice(0,3).map(r=>norm(r.textContent).slice(0,120)) }); }", panelSelector);
        return r == null ? "(no data)" : r.toString();
    }

    /** Read the <b>Package</b> tab (charges billed under a package admission — Cost Centre, Service, Qty/Day's,
     *  amounts, Doctor Share/Hospital Share, Doctor, Remark). */
    public String readPackageTab() { return readTabbedTable("Package", "#pkg"); }

    /** Read the <b>Cancelled Charges</b> tab (an audit trail — Authorize/Authorized By/Cancelled By/Cancellation
     *  Reason alongside the original charge line). */
    public String readCancelledChargesTab() { return readTabbedTable("Cancelled Charges", "#CancelledCharges"); }

    /** Switch back to <b>Bill Details</b> — call after reading the Package/Cancelled Charges tabs so the rest of
     *  the flow (Cost Centre selects, Save) operates on the tab it expects. */
    public void switchToBillDetailsTab() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const el=[...document.querySelectorAll('a')].find(x=>x.offsetParent!==null && /^\\s*bill details\\s*$/i.test(norm(x.textContent)));"
                + " if(!el) return false; el.id='__ipcTabLink'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return;
        page.evaluate("() => { const $=window.jQuery; const a=document.querySelector('#__ipcTabLink'); if($ && a) $(a).tab('show'); }");
        page.evaluate("() => { const e=document.getElementById('__ipcTabLink'); if(e) e.removeAttribute('id'); }");
        waitForAngular(600);
    }

    /**
     * Which loaded charge lines have NO Cost Centre — the thing that makes this screen refuse to save.
     *
     * <p>The app's warning ("Cost Center is missing for one or more services") names no service, so a run that
     * hits it says nothing actionable. This reads the grid's own row objects, finds whatever key holds the cost
     * centre (builds differ: {@code CostCenterId}, {@code costcentreid}, {@code CostCenterName}) and reports the
     * services where it is empty or zero, so the report names the master data that needs fixing.</p>
     */
    public String servicesMissingCostCentre() {
        Object r = page.evaluate("() => { let data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope();"
                + "   if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data) && s.grid.options.data.length"
                + "      && (!data || s.grid.options.data.length>data.length)) data=s.grid.options.data; }catch(e){} });"
                + " if(!data) return '(no charge grid data)';"
                + " const keys=Object.keys(data[0]||{});"
                + " const ccKeys=keys.filter(k=>/costcent(er|re)/i.test(k));"
                + " if(!ccKeys.length) return '(no cost-centre column on the charge rows; keys: '+keys.slice(0,14).join(',')+')';"
                // Name the row from whichever field actually carries text — the obvious key can be empty on the
                // very row that is at fault, which is how a real service came out as "(unnamed)".
                + " const NAMEY=/servicename|serviceitemname|itemname|chargename|servicedesc|description|particular|name$/i;"
                + " const label=row=>{"
                + "   for(const k of keys) if(NAMEY.test(k) && typeof row[k]==='string' && row[k].trim()) return row[k].trim();"
                + "   const code=keys.find(k=>/code/i.test(k) && typeof row[k]==='string' && row[k].trim());"
                + "   if(code) return row[code].trim();"
                + "   for(const k of keys){ const v=row[k]; if(typeof v==='string' && v.trim() && !/^\\d+(\\.\\d+)?$/.test(v.trim())) return k+'='+v.trim(); }"
                + "   const id=keys.find(k=>/id$/i.test(k) && row[k]); return id ? (id+'='+row[id]) : '(unnamed row)'; };"
                + " const bad=data.filter(row=>ccKeys.every(k=>{ const v=row[k]; return v===null||v===undefined||v===''||v===0||v==='0'; }))"
                + "   .map(label).filter((v,i,a)=>a.indexOf(v)===i);"
                + " return bad.length ? (bad.length+' of '+data.length+' charge line(s) have no Cost Centre: '+bad.slice(0,8).join(', '))"
                + "                   : 'every charge line has a Cost Centre ('+ccKeys.join('/')+')'; }");
        return r == null ? "" : r.toString();
    }

    /** Was the save stopped by the missing-Cost-Centre warning (a master-data gap, not a script problem)? */
    public boolean blockedByCostCentre() {
        return blockingMessage != null && blockingMessage.toLowerCase().matches(".*cost\\s*cent(er|re).*");
    }

    /**
     * Set every charge line's <b>Cost Center</b> select (a column in the Bill Details grid, one per row) that is
     * still "--Select--" to its first real option. Same fix as {@code OpdCharges.ensureAllCostCentresSet()} —
     * Cost Centre isn't something Save can infer, it is a per-row dropdown sitting right there in the grid, so the
     * "Cost Center is missing" block is fixable in the flow rather than only reportable. Returns how many rows
     * were fixed.
     */
    public int ensureAllCostCentresSet() {
        // Same approach as OpdCharges.ensureAllCostCentresSet(): the grid is a ui-grid (div-based, not a plain
        // <table>), so locating the "Cost Center" column by header/cell position doesn't match anything. Instead
        // identify the select directly — it's the one whose option list includes "General" (a Cost Centre name)
        // and is still on "--Select--". Verified live: finds exactly the 3 Cost Center selects on a 3-row bill.
        Object idxs = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sels=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null"
                + "   && [...s.options].some(o=>/General$/.test(norm(o.textContent)))"
                + "   && s.selectedOptions[0] && norm(s.selectedOptions[0].textContent)==='--Select--');"
                + " sels.forEach((s,i)=>s.id='__ipcCcFix'+i); return sels.length; }");
        int n = idxs instanceof Number ? ((Number) idxs).intValue() : 0;
        for (int i = 0; i < n; i++) {
            try {
                com.microsoft.playwright.Locator sel = page.locator("#__ipcCcFix" + i);
                int optIdx = (int) sel.evaluate("(e) => [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim()))");
                if (optIdx >= 0) sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(optIdx));
            } catch (Exception ignore) { }
        }
        page.evaluate("() => { for(let i=0;;i++){ const e=document.getElementById('__ipcCcFix'+i); if(!e) break; e.removeAttribute('id'); } }");
        waitForAngular(400);
        return n;
    }

    // ---- mandatory fields + save ----------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Fill the mandatory bill fields: Charge Date (today) + Cash Counter (first real option). */
    public String fillMandatory() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        setNg("PatientDetails.ChargeDate", today);
        String counter = "(n/a)";
        try {
            String sel = "select[ng-model='PatientDetails.CashCounterId']";
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return -1; return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i >= 0) { page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
                counter = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim(); }
        } catch (Exception ignore) { }
        waitForAngular(500);
        return "ChargeDate=" + today + " | CashCounter=" + counter + " | chargeRows=" + chargeGridRows();
    }

    /** The dialog that blocked the save, if any (e.g. the missing Cost Center warning). "" when the save proceeded. */
    public String blockingMessage = "";

    /**
     * Read (and dismiss) any dialog left on screen AFTER the save confirm was answered.
     *
     * <p>On this screen the save can be stopped by a plain confirm box rather than a toast — most commonly
     * <i>"Cost Center is missing for one or more services…"</i>, which is a MASTER-DATA gap, not a UI failure: no
     * {@code IUDBill} request is sent at all. Surfacing its text turns a mystifying "no toast, no report tab" into
     * a report that names the actual reason.</p>
     */
    private String readBlockingDialog() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')]"
                + "   .find(m=>m.getBoundingClientRect().width>0 && norm(m.textContent) && !/do you want to save/i.test(m.textContent||''));"
                + " if(!box) return ''; const msg=norm(box.textContent).slice(0,160);"
                + " const b=[...box.querySelectorAll('button,a')].find(x=>/^(ok|close|no|cancel)$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                + " if(b) b.click(); return msg; }");
        String msg = r == null ? "" : r.toString().trim();
        if (!msg.isEmpty()) System.out.println("readBlockingDialog: save blocked by -> " + msg);
        waitForAngular(500);
        return msg;
    }

    /**
     * Answer the <b>"Confirm ! Do you want to Save"</b> dialog that {@code IUDBill} raises.
     *
     * <p>Its affirmative button is labelled <b>SAVE</b> (not Yes/OK), and the bill is not posted until it is
     * clicked — so a flow that only waits for a toast or a report tab hangs and then reports "no confirmation".
     * Polls briefly because the dialog renders a moment after the click.</p>
     */
    /** Returns true once it actually clicked the confirm dialog — false if there was never one to click (so a
     *  caller can skip a pointless second poll instead of always spending the full 12×500ms wait). */
    private boolean answerSaveConfirm() {
        for (int i = 0; i < 12; i++) {
            Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')]"
                    + "   .find(m=>m.getBoundingClientRect().width>0 && /do you want to save|are you sure/i.test(m.textContent||''));"
                    + " if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>/^(save|yes|ok|confirm)$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + " if(!b) return false; b.click(); return true; }");
            if (Boolean.TRUE.equals(clicked)) { System.out.println("answerSaveConfirm: confirmed the save dialog"); waitForAngular(800); return true; }
            page.waitForTimeout(500);
        }
        return false;
    }

    /** The screen at the moment a toast was showing — toasts fade in ~1-2s, so a screenshot taken later (e.g. at
     *  the test's own step() call) misses it entirely, same fix as {@code Payable.submitAndGetToast}. */
    public byte[] toastPng;

    /** Click <b>Save</b> ({@code IUDBill}); capture a receipt report tab if one opens + any toast. */
    public Page saveAndCapture() {
        page.evaluate("() => { window.__ipcToasts=[]; if(window.__ipcObs) window.__ipcObs.disconnect(); try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast],[class*=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ipcToasts.includes(t)) window.__ipcToasts.push(t); }); };"
                + " window.__ipcObs=new MutationObserver(grab); window.__ipcObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        int before = page.context().pages().size();
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/IUDBill/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__ipcSave'; return true; }");
        Page report = null;
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ipcSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
            // Save raises a confirm — "Confirm ! Do you want to Save" with SAVE / NO buttons — and the post does
            // not happen until it is answered. Without this the flow just sat there: no toast, no report tab, no
            // error.
            answerSaveConfirm();
            blockingMessage = readBlockingDialog();   // e.g. "Cost Center is missing for one or more services…"
            // Poll for EITHER a new report tab OR a visible toast, whichever comes first — deliberately NOT
            // page.context().waitForPage(): that call does not return early just because its action finished; on
            // the failure path (no tab ever opens) it blocks for its FULL timeout regardless of how fast the
            // click+confirm actually happened. The "KPJ Portal Error!" toastr is only visible ~5.4s (measured
            // live) starting a moment after the confirm click, so even an 8s waitForPage timeout swallowed the
            // toast's entire visible window before this method ever got to look for it. Checking both conditions
            // in one tight loop, starting immediately after the confirm, is what actually catches it.
            toastPng = null;
            for (int i = 0; i < 30 && report == null && toastPng == null; i++) {
                if (page.context().pages().size() > before) { report = page.context().pages().get(page.context().pages().size() - 1); break; }
                boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[class*=toast]')].some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
                if (showing) { try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); } catch (Exception e) { break; } }
                else page.waitForTimeout(200);
            }
        } else { System.out.println("saveAndCapture: Save (IUDBill) button not found"); }
        Object t = page.evaluate("() => { const a=window.__ipcToasts||[]; return a.find(x=>/saved|success|bill|charge|please|enter|select|required|amount/i.test(x)) || a[0] || ''; }");
        lastToast = t == null ? "" : t.toString().trim();
        if (report == null && page.context().pages().size() > before) report = page.context().pages().get(page.context().pages().size() - 1);
        if (report != null) { try { report.waitForLoadState(); report.waitForTimeout(2000); } catch (Exception ignore) { } }
        return report;
    }
}
