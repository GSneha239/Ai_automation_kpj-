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

    /**
     * JS predicate: is the "Patient Search" popup showing?
     *
     * <p>Corrected 26/10/2026 (verified live on HIS Demo): the popup is a {@code .modal} with
     * {@code position:fixed}, and a fixed element's {@code offsetParent} is ALWAYS null — so the old
     * {@code m.offsetParent!==null} test never saw the popup open and every pick waited out its full 8s timeout
     * before carrying on. Visibility is now decided by the element's rendered box.</p>
     */
    private static final String MODAL_SHOWN_JS =
            "() => [...document.querySelectorAll('.modal')].some(m=>{ const r=m.getBoundingClientRect(); return r.width>0 && r.height>0; })";

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
        try { page.waitForFunction(MODAL_SHOWN_JS, null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
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
        try { page.waitForFunction(MODAL_SHOWN_JS, null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
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

    /**
     * Add one service charge via the Service Name autocomplete. Returns the picked service name (or a marker).
     *
     * <p>Corrected 26/10/2026 (verified live on HIS Demo): the Service Name box ({@code #txtServiceName},
     * {@code ServiceData.ServiceName}) is now an AngularJS auto-complete — results render as
     * {@code li.auto-complete-item} inside a body-level {@code div.auto-complete-container}, NOT as jQuery-UI
     * {@code ul.ui-autocomplete} items, so the old wait never saw a single result. The jQuery-UI selector is kept
     * only as a fallback for older builds. A NON-package service is preferred here so this (saving) flow keeps
     * posting a plain service line, as it did before; packages are handled by {@link #addFirstPackage}.</p>
     */
    public String addServiceCharge() {
        String[] queries = { "con", "gen", "ward", "nur", "char", "room", "bed", "med", "a", "e" };
        for (String q : queries) {
            java.util.List<java.util.Map<String, Object>> items = searchServices(q);
            if (items.isEmpty()) { System.out.println("addServiceCharge: q='" + q + "' no autocomplete items"); continue; }
            int pick = -1;
            for (int i = 0; i < items.size(); i++) if (!Boolean.TRUE.equals(items.get(i).get("ispackage"))) { pick = i; break; }
            if (pick < 0) pick = 0;
            if (!clickAutocompleteItem(pick)) continue;
            setNg("ServiceData.quantity", "1");
            waitForAngular(400);
            String name = String.valueOf(page.evaluate("() => { const e=document.querySelector('#txtServiceName'); return e ? e.value : ''; }"));
            if (!name.isEmpty()) {
                lastService = name;
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
        // ":visible" — the page carries a second, hidden ServiceData row; .first() alone could press Enter on it.
        try { page.locator("input[ng-model='ServiceData.quantity']:visible").first().press("Enter"); } catch (Exception ignore) { }
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
        if (!showTab(panelSelector, tabLinkText)) return "(tab link '" + tabLinkText + "' not found)";
        Object r = page.evaluate("(sel) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const panel=document.querySelector(sel); if(!panel) return 'no panel';"
                + " const t=panel.querySelector('table'); if(!t) return 'no table in panel';"
                + " const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent)).filter(Boolean);"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent));"
                + " return JSON.stringify({ heads, rowCount: rows.length, sample: rows.slice(0,3).map(r=>norm(r.textContent).slice(0,120)) }); }", panelSelector);
        return r == null ? "(no data)" : r.toString();
    }

    /**
     * Show a Bootstrap tab of the charges area.
     *
     * <p>Corrected 26/10/2026 (verified live on HIS Demo): the tab links have no {@code href}; each carries
     * {@code data-target="#billDtls" | "#pkg" | "#pkgItem" | "#CancelledCharges"}, and the package tab is labelled
     * <b>Package Service</b> (it used to be "Package", so the old exact-text match found nothing). The link is now
     * found by its {@code data-target} first, with the visible text only as a fallback. Shown via jQuery's
     * {@code $(el).tab('show')} — a synthetic click is intercepted by the active pane underneath.</p>
     */
    public boolean showTab(String panelSelector, String fallbackTextRegex) {
        Object tagged = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " let el=document.querySelector('a[data-toggle=tab][data-target=\"'+a.sel+'\"],a[data-toggle=tab][href=\"'+a.sel+'\"]');"
                + " if(!el && a.txt) el=[...document.querySelectorAll('a')].find(x=>x.offsetParent!==null && new RegExp('^\\\\s*(?:'+a.txt+')\\\\s*$','i').test(norm(x.textContent)));"
                + " if(!el) return false; el.id='__ipcTabLink'; return true; }",
                java.util.Map.of("sel", panelSelector, "txt", fallbackTextRegex == null ? "" : fallbackTextRegex));
        if (!Boolean.TRUE.equals(tagged)) return false;
        page.evaluate("() => { const $=window.jQuery; const a=document.querySelector('#__ipcTabLink'); if($ && a) $(a).tab('show'); }");
        page.evaluate("() => { const e=document.getElementById('__ipcTabLink'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        return true;
    }

    /** Read the <b>Package Service</b> tab (the break-up of a package — Cost Centre, Service, Qty/Day's, amounts,
     *  Doctor Share/Hospital Share, Doctor, Remark). Tab label was "Package" on older builds; both are accepted. */
    public String readPackageTab() { return readTabbedTable("Package Service|Package", "#pkg"); }

    /** Read the <b>Cancelled Charges</b> tab (an audit trail — Authorize/Authorized By/Cancelled By/Cancellation
     *  Reason alongside the original charge line). */
    public String readCancelledChargesTab() { return readTabbedTable("Cancelled Charges", "#CancelledCharges"); }

    /** Switch back to <b>Bill Details</b> — call after reading the Package/Cancelled Charges tabs so the rest of
     *  the flow (Cost Centre selects, Save) operates on the tab it expects. */
    public void switchToBillDetailsTab() { showTab("#billDtls", "Bill Details"); }

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

    // =====================================================================================================
    //  SECTION HELPERS — used by tests.Billing_page.IpdChargesSections (added 26/10/2026, mapped live on
    //  HIS Demo). Everything below is READ-ONLY with respect to the server: nothing here clicks Save
    //  (IUDBill), cancels a posted charge, or touches a charge row that existed before the test. The only
    //  rows ever removed are rows this helper itself added AND tagged (window flag __ipcAddedByTest) AND that
    //  are still unsaved (chargeid == 0) — see removeRowsAddedByTest().
    // =====================================================================================================

    /** JS: the Bill Details charge rows (the controller's ServiceList.data; ui-grid data as a fallback). */
    private static final String ROWS_JS =
            "const __rows=()=>{ let d=null;"
          + " try{ const s=angular.element(document.querySelector('#billDtls')).scope(); if(s&&s.ServiceList&&Array.isArray(s.ServiceList.data)) d=s.ServiceList.data; }catch(e){}"
          + " if(!d){ document.querySelectorAll('#billDtls *').forEach(el=>{ try{ const s=angular.element(el).scope(); const x=s&&s.grid&&s.grid.options&&s.grid.options.data;"
          + "   if(Array.isArray(x)&&(!d||x.length>d.length)) d=x; }catch(e){} }); }"
          + " return d||[]; };"
          + " const __code=r=>String(r.ServiceCode||r.servicecode||'');"
          + " const __name=r=>String(r.ServiceName||r.servicename||'');";

    /** JS: the controller scope that owns PatientDetails. */
    private static final String PD_SCOPE_JS =
            "const __pd=()=>{ try{ return angular.element(document.querySelector(\"[ng-model='PatientDetails.MRNo']\")).scope().PatientDetails||{}; }catch(e){ return {}; } };";

    /** JS: a visible ng-confirm / jconfirm box. */
    private static final String CONFIRM_JS =
            "const __confirm=()=>[...document.querySelectorAll('.ng-confirm-box,.jconfirm-box')].find(b=>{ const r=b.getBoundingClientRect(); return r.width>0&&r.height>0; });";

    // ---- shared: alert + network capture --------------------------------

    /**
     * Capture {@code JAlert} messages (the app's toastr wrapper — toasts fade in ~1-2s, so reading the DOM later
     * misses them) and every XHR's URL / ExecFlag / result size (to prove which calls a step made — e.g. that
     * adding a package only FETCHED, and that GetPackageServices returned no lines). Idempotent; call after the
     * screen has loaded (and again after any page reload).
     */
    public void installCaptureHooks() {
        page.evaluate("() => {"
                + " if(!window.__ipcJaHooked && typeof window.JAlert==='function'){ const o=window.JAlert;"
                + "   window.JAlert=function(m,t){ try{ (window.__ipcAlerts=window.__ipcAlerts||[]).push(String(m==null?'':m).replace(/<[^>]+>/g,'').replace(/\\s+/g,' ').trim()); }catch(e){} return o.apply(this,arguments); };"
                + "   window.__ipcJaHooked=true; }"
                + " if(!window.__ipcXhrHooked){ window.__ipcXhr=[]; const op=XMLHttpRequest.prototype.open, sd=XMLHttpRequest.prototype.send;"
                + "   XMLHttpRequest.prototype.open=function(m,u){ this.__ipcU=u; return op.apply(this,arguments); };"
                + "   XMLHttpRequest.prototype.send=function(b){ const x=this; const body=String(b||'');"
                + "     x.addEventListener('load',()=>{ try{ if(/getInternalmsg/i.test(String(x.__ipcU||''))) return;"
                + "       const f=(body.match(/\"ExecFlag\"\\s*:\\s*\"(\\w+)\"/)||[])[1]||''; let n=-1; try{ const j=JSON.parse(x.responseText); if(Array.isArray(j)) n=j.length; }catch(e){}"
                + "       window.__ipcXhr.push({u:String(x.__ipcU||''), flag:f, n:n}); if(window.__ipcXhr.length>400) window.__ipcXhr.shift(); }catch(e){} });"
                + "     return sd.apply(this,arguments); };"
                + "   window.__ipcXhrHooked=true; } }");
    }

    public void clearAlerts() { page.evaluate("() => { window.__ipcAlerts=[]; }"); }

    @SuppressWarnings("unchecked")
    public java.util.List<String> alerts() {
        Object r = page.evaluate("() => (window.__ipcAlerts||[]).slice()");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Wait (up to {@code ms}) for at least one JAlert after the last {@link #clearAlerts()}; returns them joined. */
    public String waitForAlert(int ms) {
        try { page.waitForFunction("() => (window.__ipcAlerts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(ms)); }
        catch (Exception ignore) { }
        return String.join(" | ", alerts());
    }

    /** Index into the XHR log, to ask "what did the next action call?". */
    public int xhrMark() {
        Object r = page.evaluate("() => (window.__ipcXhr||[]).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Calls made since {@code mark}, one "url [ExecFlag] -> n rows" line each. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> xhrSince(int mark) {
        Object r = page.evaluate("(m) => (window.__ipcXhr||[]).slice(m).map(x=>x.u.replace(location.origin,'')+(x.flag?' ['+x.flag+']':'')+' -> '+(x.n<0?'?':x.n+' rows'))", mark);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- network write audit ---------------------------------------------
    //
    // Fixed 26/10/2026 after a false FAIL on HIS Demo. The old check matched the substring "cancel" anywhere in the
    // in-page XHR log and flagged  POST /api/Bill/FetchIPDBill [ExecFlag GetCancelledCharges]  — a READ (the app's
    // fnFetchCancelledCharges() -> BillRefundFactory.FetchIPDBillDetails, fired on every patient load to fill the
    // Cancelled Charges tab). That log also only saw XHRs that completed normally, so a write that errored, aborted
    // or went through fetch() would never have been seen.
    //
    // Now: every request the page sends is recorded by Playwright itself (page.onRequest — method, URL, ExecFlag,
    // at send time, whether or not it succeeds), and each one is classified on its ENDPOINT (last path segment) and
    // ExecFlag. This app POSTs every API call, reads included, so the HTTP method alone cannot tell them apart.
    //   READ          GET/HEAD; or an endpoint named Fetch*/Get*/Search*/Check*/Is*/Load*/... ; or a known read endpoint
    //   WRITE         endpoint or ExecFlag STARTS with a mutation verb (IUD, Save, Insert, Update, Delete, Cancel, ...)
    //                 or contains "IUD" — checked FIRST, so Fetch... [CancelCharge] is still a write
    //   UNCLASSIFIED  anything else — fails too (fail closed), labelled so a person can review it

    private static final java.util.regex.Pattern MUTATION = java.util.regex.Pattern.compile(
            "(?i)^(iud|save|insert|update|delete|remove|cancel|post|submit|approve|create|add|upsert|modify|generate|upload|import|settle|refund|discharge|close|reject|reverse|void|finali[sz]e|confirm)|iud");
    private static final java.util.regex.Pattern READ_ENDPOINT = java.util.regex.Pattern.compile(
            "(?i)^(fetch|get|search|check|is|load|validate|find|list|count|verify|read|show|view)");
    /** Endpoints confirmed live (HIS Demo, 26/10/2026) to be reads although their names carry no read verb. */
    private static final java.util.Set<String> KNOWN_READ_ENDPOINTS = java.util.Set.of("bill/holidayordaymaster");
    private static final java.util.regex.Pattern EXEC_FLAG = java.util.regex.Pattern.compile("(?i)\"?ExecFlag\"?\\s*[:=]\\s*\"?([A-Za-z0-9_]+)");

    private final java.util.List<String[]> netLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>()); // {method, path, flag}
    private java.util.function.Consumer<com.microsoft.playwright.Request> netListener;

    /** Start recording every API/XHR/fetch request (and any non-GET request) this page sends. Idempotent. */
    public void startNetworkAudit() {
        if (netListener != null) return;
        netListener = req -> {
            try {
                String method = req.method() == null ? "" : req.method().toUpperCase();
                String rt = req.resourceType() == null ? "" : req.resourceType();
                String url = req.url() == null ? "" : req.url();
                if (url.startsWith("data:") || url.startsWith("blob:")) return;
                if (!("xhr".equals(rt) || "fetch".equals(rt) || url.contains("/api/") || !"GET".equals(method))) return;
                if (url.matches("(?i).*/api/Auth/getInternalmsgCount(Only)?\\b.*")) return;   // unread-message poll, every few s
                String path = url.replaceFirst("^[a-z]+://[^/]+", "");
                String flag = "";
                String body = "";
                try { body = req.postData() == null ? "" : req.postData(); } catch (Exception ignore) { }
                java.util.regex.Matcher m = EXEC_FLAG.matcher(body.isEmpty() ? path : body);
                if (m.find()) flag = m.group(1);
                netLog.add(new String[]{method, path, flag});
            } catch (Exception ignore) { }
        };
        page.onRequest(netListener);
    }

    /** Stop recording (detaches the listener — matters when a shared browser runs several tests). */
    public void stopNetworkAudit() {
        if (netListener == null) return;
        try { page.offRequest(netListener); } catch (Exception ignore) { }
        netListener = null;
    }

    /** Index into the request log, to ask "what did everything after this point send?". */
    public int networkMark() { return netLog.size(); }

    /** READ / WRITE / UNCLASSIFIED for one request (see the table above). */
    public static String classifyRequest(String method, String path, String flag) {
        String p = path == null ? "" : path.replaceFirst("[?#].*$", "").replaceAll("/+$", "");
        String endpoint = p.substring(p.lastIndexOf('/') + 1);
        String parent = p.lastIndexOf('/') > 0 ? p.substring(0, p.lastIndexOf('/')) : "";
        String ctrlEndpoint = (parent.substring(parent.lastIndexOf('/') + 1) + "/" + endpoint).toLowerCase();
        String f = flag == null ? "" : flag;
        if (MUTATION.matcher(endpoint).find() || (!f.isEmpty() && MUTATION.matcher(f).find())) return "WRITE";
        String m = method == null ? "" : method.toUpperCase();
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) return "READ";
        if (READ_ENDPOINT.matcher(endpoint).find() || KNOWN_READ_ENDPOINTS.contains(ctrlEndpoint)) return "READ";
        return "UNCLASSIFIED";
    }

    /** Every request since {@code mark}: "METHOD /path [ExecFlag] -> CLASS". */
    public java.util.List<String> networkSince(int mark) {
        java.util.List<String> out = new java.util.ArrayList<>();
        synchronized (netLog) {
            for (int i = Math.max(0, mark); i < netLog.size(); i++) {
                String[] r = netLog.get(i);
                out.add(r[0] + " " + r[1] + (r[2].isEmpty() ? "" : " [" + r[2] + "]") + " -> " + classifyRequest(r[0], r[1], r[2]));
            }
        }
        return out;
    }

    /** Requests since {@code mark} that are NOT confirmed reads (WRITE or UNCLASSIFIED). Must stay empty. */
    public java.util.List<String> writeCallsSince(int mark) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : networkSince(mark)) if (!s.endsWith("-> READ")) out.add(s);
        return out;
    }

    private void clickTagged(String jsFinder, String tagId, int timeoutMs) {
        Object ok = page.evaluate("(id) => { const el=(" + jsFinder + ")(); if(!el) return false; el.id=id; return true; }", tagId);
        if (!Boolean.TRUE.equals(ok)) throw new IllegalStateException("element not found for #" + tagId);
        try { page.locator("#" + tagId).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(timeoutMs)); }
        finally { page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tagId); }
    }

    // ---- PATIENT DETAILS -------------------------------------------------

    /** The Patient Details panel's controls as found on screen: MRN box + Search + picker buttons, and whether
     *  each admission field is present and read-only. */
    public java.util.Map<String, Object> patientDetailsControls() {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) page.evaluate("() => {"
                + " const vis=e=>!!e && e.getBoundingClientRect().width>0;"
                + " const q=s=>document.querySelector(s);"
                + " const ro={}; ['IPDNo','visitadmissiondate','ward','bedclass','bed','department','doctorname','creditlimit','AdvanceBalance']"
                + "   .forEach(k=>{ const e=q(\"input[ng-model='PatientDetails.\"+k+\"']\"); ro[k]= !e ? 'missing' : ((e.readOnly||e.disabled) ? 'read-only' : 'EDITABLE'); });"
                + " return { mrnInput: vis(q(\"input[ng-model='PatientDetails.MRNo']\")),"
                + "   mrnSearchButton: vis([...document.querySelectorAll('button')].find(b=>/SearchPatientByMRNo/.test(b.getAttribute('ng-click')||''))),"
                + "   pickerButton: vis([...document.querySelectorAll('[ng-click]')].find(b=>/OpenPopupScreen/.test(b.getAttribute('ng-click')||''))),"
                + "   billNo: vis(q(\"input[ng-model='PatientDetails.BillNo']\")),"
                + "   getBillButton: vis([...document.querySelectorAll('button')].find(b=>/fnGetZTCareBillDetails/.test(b.getAttribute('ng-click')||''))),"
                + "   readOnly: ro }; }");
        return m;
    }

    /** The 4 charge-area tabs (by their data-target), true when the link is present and visible. */
    public java.util.Map<String, Object> chargeTabs() {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) page.evaluate("() => { const o={};"
                + " ['#billDtls','#pkg','#pkgItem','#CancelledCharges'].forEach(t=>{ const a=document.querySelector('a[data-toggle=tab][data-target=\"'+t+'\"]');"
                + "   o[t]= a ? (a.textContent||'').replace(/\\s+/g,' ').trim() + (a.getBoundingClientRect().width>0?'':' (hidden)') : '(missing)'; });"
                + " return o; }");
        return m;
    }

    /**
     * Type an MRN into Patient Details and click its Search ({@code SearchPatientByMRNo()}). Returns the JAlert
     * text raised (e.g. "Please Enter MRN No." / "Patient Not Found"), or "" when the patient loaded quietly.
     * NOTE: a search for an unknown MRN clears the currently loaded patient (the app calls fnClearControls()).
     */
    public String searchByMrn(String mrn) {
        clearAlerts();
        setNg("PatientDetails.MRNo", mrn == null ? "" : mrn);
        waitForAngular(300);
        clickTagged("() => [...document.querySelectorAll('button')].find(b=>/SearchPatientByMRNo/.test(b.getAttribute('ng-click')||'') && b.getBoundingClientRect().width>0)", "__ipcMrnSearch", 5000);
        try {
            page.waitForFunction("(mrn) => { " + PD_SCOPE_JS + " const p=__pd(); return (window.__ipcAlerts||[]).length>0 || (mrn && p.IPDNo && String(p.MRNo)===mrn); }",
                    mrn == null ? "" : mrn, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        String a = String.join(" | ", alerts());
        java.util.Map<String, String> h = readPatientHeader();
        if (!h.getOrDefault("IPDNo", "").isEmpty()) { lastIpd = h.get("IPDNo"); lastPatient = h.getOrDefault("MRNo", ""); }
        return a;
    }

    /** The loaded admission as the screen holds it (PatientDetails model). */
    public java.util.Map<String, String> readPatientHeader() {
        Object r = page.evaluate("() => { " + PD_SCOPE_JS + " const p=__pd(); const o={};"
                + " ['MRNo','IPDNo','visitadmissiondate','ward','bedclass','bed','department','doctorname','creditlimit','AdvanceBalance','ChargeDate'].forEach(k=>{ o[k]= p[k]==null ? '' : String(p[k]).trim(); });"
                + " return o; }");
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (r instanceof java.util.Map) ((java.util.Map<?, ?>) r).forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    // ---- BILL DETAILS ----------------------------------------------------

    /** Bill Details grid column titles, from the ui-grid column definitions (includes columns scrolled off-screen). */
    @SuppressWarnings("unchecked")
    public java.util.List<String> billGridColumns() {
        showTab("#billDtls", "Bill Details");
        Object r = page.evaluate("() => { let defs=null; document.querySelectorAll('#billDtls *').forEach(el=>{ try{ const s=angular.element(el).scope();"
                + "   const c=s&&s.grid&&s.grid.options&&s.grid.options.columnDefs; if(Array.isArray(c)&&(!defs||c.length>defs.length)) defs=c; }catch(e){} });"
                + " if(defs) return defs.map(c=>String(c.displayName||c.name||c.field||'').replace(/\\s+/g,' ').trim()).filter(Boolean);"
                + " return [...document.querySelectorAll('#billDtls .ui-grid-header-cell .ui-grid-cell-contents')].map(h=>(h.textContent||'').replace(/\\s+/g,' ').replace(/\\s+\\d+$/,'').trim()).filter(Boolean); }");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /** ng-models of the visible service-entry row (Service Code / Name / CPT / Quantity / Unit Price). */
    @SuppressWarnings("unchecked")
    public java.util.List<String> serviceEntryFields() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"input[ng-model^='ServiceData.']\")].filter(e=>e.getBoundingClientRect().width>0)"
                + ".map(e=>e.getAttribute('ng-model').replace('ServiceData.','')+(e.placeholder?' (\"'+e.placeholder.trim()+'\")':''))");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click Get Bill with the Bill No. box empty; returns the JAlert raised. */
    public String getBillWithoutNumber() {
        clearAlerts();
        setNg("PatientDetails.BillNo", "");
        waitForAngular(300);
        clickTagged("() => [...document.querySelectorAll('button')].find(b=>/fnGetZTCareBillDetails/.test(b.getAttribute('ng-click')||'') && b.getBoundingClientRect().width>0)", "__ipcGetBill", 5000);
        return waitForAlert(6000);
    }

    /** Press Enter in Quantity with the Service Name empty; returns the JAlert raised. Adds nothing. */
    public String addServiceWithNoName() {
        markBeforeAdd();
        clearAlerts();
        setNg("ServiceData.ServiceName", "");
        setNg("ServiceData.ServiceCode", "");
        waitForAngular(300);
        commitServiceEntry();
        String a = waitForAlert(6000);
        markRowsAddedByTest("");   // nothing should be added; an unexpected row is left untagged and reported by cleanup
        return a;
    }

    /** Charge rows: code | name | chargeid | totalamount | ispackage | addedByTest. */
    @SuppressWarnings("unchecked")
    public java.util.List<java.util.Map<String, Object>> gridRows() {
        Object r = page.evaluate("() => { " + ROWS_JS + " return __rows().map(x=>({ code:__code(x), name:__name(x), chargeid: x.chargeid==null?0:Number(x.chargeid)||0,"
                + " total: parseFloat(x.totalamount)||0, ispackage: !!x.ispackage, addedByTest: !!x.__ipcAddedByTest })); }");
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add((java.util.Map<String, Object>) o);
        return out;
    }

    /** "CODE#chargeid" for each charge row, in grid order — the before/after fingerprint for cleanup checks. */
    public java.util.List<String> gridFingerprint() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> r : gridRows()) out.add(r.get("code") + "#" + r.get("chargeid"));
        return out;
    }

    /** Footer totals as the screen shows them, plus the arithmetic sum of the grid rows' Total Amount. */
    public java.util.Map<String, String> totals() {
        Object r = page.evaluate("() => { " + PD_SCOPE_JS + ROWS_JS + " const p=__pd();"
                + " const sum=__rows().reduce((a,x)=>a+(parseFloat(x.totalamount)||0),0);"
                + " return { TotalAmount:String(p.TotalAmount==null?'':p.TotalAmount), TotalDiscount:String(p.TotalConcessionAmount==null?'':p.TotalConcessionAmount),"
                + "   SelfAmount:String(p.SelfPayAmount==null?'':p.SelfPayAmount), CompanyAmount:String(p.ReceivalbePayAmount==null?'':p.ReceivalbePayAmount),"
                + "   NetAmount:String(p.TotalNetAmount==null?'':p.TotalNetAmount), RowSum:sum.toFixed(2), Rows:String(__rows().length) }; }");
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (r instanceof java.util.Map) ((java.util.Map<?, ?>) r).forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    // ---- service entry / AngularJS autocomplete (shared by Bill Details + Package Services) --------------

    /**
     * Type {@code q} into the visible Service Name box and return the auto-complete results:
     * {@code {label, code, name, ispackage, rate}} per item (AngularJS {@code li.auto-complete-item}; jQuery-UI
     * {@code ul.ui-autocomplete li} as a fallback, label only).
     */
    @SuppressWarnings("unchecked")
    public java.util.List<java.util.Map<String, Object>> searchServices(String q) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        try {
            com.microsoft.playwright.Locator svc = page.locator("#txtServiceName:visible, input[ng-model='ServiceData.ServiceName']:visible").first();
            svc.scrollIntoViewIfNeeded();
            svc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            svc.fill("");
            svc.pressSequentially(q, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(120));
        } catch (Exception e) { System.out.println("searchServices: typing '" + q + "' failed - " + e.getClass().getSimpleName()); return out; }
        try {
            page.waitForFunction("(q) => [...document.querySelectorAll('li.auto-complete-item, ul.ui-autocomplete li')]"
                    + ".some(li=>li.getBoundingClientRect().width>0 && (li.textContent||'').toLowerCase().includes(q.toLowerCase()))",
                    q, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { return out; }
        waitForAngular(400);
        Object r = page.evaluate("() => [...document.querySelectorAll('li.auto-complete-item, ul.ui-autocomplete li')].filter(li=>li.getBoundingClientRect().width>0).map(li=>{"
                + " let it=null; try{ const s=angular.element(li).scope(); it=s&&s.item ? (s.item.data||s.item) : null; }catch(e){}"
                + " return { label:(li.textContent||'').replace(/\\s+/g,' ').trim(), code: it?String(it.ServiceCode||''):'', name: it?String(it.ServiceName||''):'',"
                + "   ispackage: !!(it&&it.ispackage), rate: it&&it.rate!=null ? String(it.rate) : '' }; })");
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add((java.util.Map<String, Object>) o);
        return out;
    }

    /** Click the visible auto-complete item at {@code idx} (a real click). True when it filled the Service Name. */
    public boolean clickAutocompleteItem(int idx) {
        Object ok = page.evaluate("(i) => { const li=[...document.querySelectorAll('li.auto-complete-item, ul.ui-autocomplete li')].filter(e=>e.getBoundingClientRect().width>0)[i];"
                + " if(!li) return false; const a=li.querySelector('a')||li; a.id='__ipcAcItem'; return true; }", idx);
        if (!Boolean.TRUE.equals(ok)) return false;
        try { page.locator("#__ipcAcItem").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("clickAutocompleteItem: " + e.getClass().getSimpleName()); }
        page.evaluate("() => { const e=document.getElementById('__ipcAcItem'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        Object name = page.evaluate("() => { try{ const sd=angular.element(document.querySelector('#txtServiceName')).scope().ServiceData; return sd&&sd.ServiceName ? String(sd.ServiceName) : ''; }catch(e){ return ''; } }");
        return name != null && !name.toString().isEmpty();
    }

    /** Enter in the visible Quantity box — the screen's own "add this line" ({@code QuantityblurEvent}). Nothing
     *  else: unlike addServiceToGrid() this never clicks any other add control. */
    public void commitServiceEntry() {
        try { page.locator("input[ng-model='ServiceData.quantity']:visible").first().press("Enter"); }
        catch (Exception e) { System.out.println("commitServiceEntry: " + e.getClass().getSimpleName()); }
        waitForAngular(1200);
    }

    /** Snapshot the current row objects (by identity) so the next add can tell which rows it created. */
    public void markBeforeAdd() {
        page.evaluate("() => { " + ROWS_JS + " window.__ipcPre=__rows().slice(); }");
    }

    /**
     * Tag rows that appeared since {@link #markBeforeAdd()} AND carry {@code expectedCode} (the service/package this
     * test itself just added) as test-owned; returns the tagged codes. Matching the code as well as "new since the
     * snapshot" means a row the app re-creates or loads on its own during an add is never tagged, so cleanup can
     * never remove it. {@code expectedCode} empty = tag nothing (an unexpected new row then stays and the cleanup
     * step's before/after comparison reports it).
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> markRowsAddedByTest(String expectedCode) {
        Object r = page.evaluate("(code) => { " + ROWS_JS + " const pre=window.__ipcPre||null; if(!pre || !code) return [];"
                + " const fresh=__rows().filter(x=>!pre.includes(x) && __code(x).toLowerCase()===code.toLowerCase());"
                + " fresh.forEach(x=>{ x.__ipcAddedByTest=true; }); return fresh.map(__code); }", expectedCode == null ? "" : expectedCode);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- PACKAGE SERVICES ------------------------------------------------

    /** The visible confirm dialog's text, or "" when none is showing. */
    public String confirmText() {
        Object r = page.evaluate("() => { " + CONFIRM_JS + " const b=__confirm(); return b ? (b.textContent||'').replace(/\\s+/g,' ').replace(/^\u00D7\\s*/,'').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click a button of the visible confirm dialog by its exact (case-insensitive) label, e.g. "No". */
    public boolean answerConfirm(String label) {
        Object ok = page.evaluate("(lbl) => { " + CONFIRM_JS + " const b=__confirm(); if(!b) return false;"
                + " const btn=[...b.querySelectorAll('button')].find(x=>x.getBoundingClientRect().width>0 && (x.textContent||'').trim().toLowerCase()===lbl.toLowerCase());"
                + " if(!btn) return false; btn.id='__ipcConfirmBtn'; return true; }", label);
        if (!Boolean.TRUE.equals(ok)) return false;
        try { page.locator("#__ipcConfirmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("answerConfirm: " + e.getClass().getSimpleName()); }
        page.evaluate("() => { const e=document.getElementById('__ipcConfirmBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        return true;
    }

    /** Result of trying to add a package. {@code code} is "" when no package could be found/added. */
    public static final class PackageAdd {
        public String code = "", name = "", rate = "", query = "", dialog = "";
        public java.util.List<String> added = new java.util.ArrayList<>();
        public java.util.List<String> searched = new java.util.ArrayList<>();
    }

    /**
     * Search the Service Name box with each query in turn, pick the first result the server flags
     * {@code ispackage=true}, and press Enter to add it to the Bill Details grid (client-side only — Save is never
     * clicked). The new row(s) are tagged test-owned. Any confirm the add raises is answered <b>No</b> and its text
     * returned in {@code dialog}.
     */
    public PackageAdd addFirstPackage(String... queries) {
        PackageAdd res = new PackageAdd();
        for (String q : queries) {
            java.util.List<java.util.Map<String, Object>> items = searchServices(q);
            res.searched.add(q + " -> " + items.size() + " result(s)");
            int pick = -1;
            for (int i = 0; i < items.size(); i++) if (Boolean.TRUE.equals(items.get(i).get("ispackage"))) { pick = i; break; }
            if (pick < 0) continue;
            java.util.Map<String, Object> it = items.get(pick);
            if (!clickAutocompleteItem(pick)) continue;
            res.code = String.valueOf(it.get("code")); res.name = String.valueOf(it.get("name"));
            res.rate = String.valueOf(it.get("rate")); res.query = q;
            int before = gridRows().size();
            markBeforeAdd();
            commitServiceEntry();
            try {
                page.waitForFunction("(n) => { " + ROWS_JS + CONFIRM_JS + " return __rows().length>n || !!__confirm(); }",
                        before, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { }
            waitForAngular(800);
            String c = confirmText();
            if (!c.isEmpty()) { res.dialog = c; answerConfirm("No"); }
            res.added = markRowsAddedByTest(res.code);
            return res;
        }
        return res;
    }

    /**
     * Wait (up to {@code ms}) for the package break-up to arrive, then describe it: package blocks in the
     * controller ({@code PackageList}), visible rows in the Package Service and Package Item tables, and what the
     * break-up lookups ({@code GetPackageServices} / {@code GetPackageGroupServices}) returned since {@code mark}.
     */
    public java.util.Map<String, Object> packageBreakdown(int mark, int ms) {
        try {
            page.waitForFunction("() => { try{ const s=angular.element(document.querySelector('#billDtls')).scope(); return (s.PackageList||[]).some(p=>(p.PackageServices||[]).length>0); }catch(e){ return false; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(ms));
        } catch (Exception ignore) { }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) page.evaluate("(mark) => { const o={};"
                + " try{ const s=angular.element(document.querySelector('#billDtls')).scope(); const pl=s.PackageList||[];"
                + "   o.packages=pl.map(p=>String(p.PackageName)+' ('+(p.PackageServices||[]).length+' service line(s), '+(p.PackageItems||[]).length+' item(s))');"
                + "   o.serviceLines=pl.reduce((a,p)=>a+(p.PackageServices||[]).length,0); o.itemLines=pl.reduce((a,p)=>a+(p.PackageItems||[]).length,0); }catch(e){ o.packages=[]; o.serviceLines=0; o.itemLines=0; }"
                + " o.lookups=(window.__ipcXhr||[]).slice(mark).filter(x=>/GetPackage/i.test(x.flag)).map(x=>x.flag+' -> '+(x.n<0?'?':x.n+' row(s)'));"
                + " return o; }", mark);
        return m;
    }

    /**
     * Add {@code code} a second time and answer the duplicate confirm with <b>No</b>.
     * Returns {dialog, answeredNo, rowsBefore, rowsAfter}.
     */
    public java.util.Map<String, Object> addDuplicateAndAnswerNo(String code) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        int before = gridRows().size();
        out.put("rowsBefore", before);
        java.util.List<java.util.Map<String, Object>> items = searchServices(code);
        int pick = -1;
        for (int i = 0; i < items.size(); i++) if (code.equalsIgnoreCase(String.valueOf(items.get(i).get("code")))) { pick = i; break; }
        if (pick < 0 || !clickAutocompleteItem(pick)) { out.put("dialog", ""); out.put("answeredNo", false); out.put("rowsAfter", gridRows().size()); out.put("note", "package " + code + " not found again in the search"); return out; }
        markBeforeAdd();
        commitServiceEntry();
        try { page.waitForFunction("() => { " + CONFIRM_JS + " return !!__confirm(); }", null, new Page.WaitForFunctionOptions().setTimeout(8000)); }
        catch (Exception ignore) { }
        String dialog = confirmText();
        out.put("dialog", dialog);
        out.put("answeredNo", !dialog.isEmpty() && answerConfirm("No"));
        waitForAngular(800);
        markRowsAddedByTest(code);   // should be none; a duplicate that did get in is test-owned and cleanup-eligible
        out.put("rowsAfter", gridRows().size());
        return out;
    }

    /** The Package Service ({@code #pkg}) table: column titles, data rows. */
    public java.util.Map<String, Object> packageServiceTable() { return readPlainTable("#pkg", "Package Service|Package"); }

    // ---- PACKAGE ITEM ----------------------------------------------------

    /** The Package Item ({@code #pkgItem}) table: column titles, data rows, and the empty-state text if shown. */
    public java.util.Map<String, Object> packageItemTable() { return readPlainTable("#pkgItem", "Package Item"); }

    private java.util.Map<String, Object> readPlainTable(String pane, String tabText) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        boolean shown = showTab(pane, tabText);
        out.put("tabShown", shown);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) page.evaluate("(sel) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const p=document.querySelector(sel); if(!p) return {heads:[], rows:0, sample:[], empty:'', active:false};"
                + " const t=p.querySelector('table'); const heads=t?[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent)).filter(Boolean):[];"
                + " const empty=/No package items for this visit/i.test(p.innerText||'') ? 'No package items for this visit' : '';"
                + " const rows=t?[...t.querySelectorAll('tbody tr')].filter(r=>r.cells.length>2 && norm(r.textContent) && !/No package items/i.test(r.textContent)):[];"
                + " return {heads, rows:rows.length, sample:rows.slice(0,3).map(r=>norm(r.textContent).slice(0,140)), empty, active:/\\bactive\\b/.test(p.className)}; }", pane);
        if (m != null) out.putAll(m);
        return out;
    }

    // ---- CLEANUP ---------------------------------------------------------

    /**
     * Remove ONLY the rows this test added: tagged {@code __ipcAddedByTest} AND still unsaved ({@code chargeid==0}).
     * A posted row (chargeid != 0) is never touched — deleting one asks for a cancellation reason, i.e. a real
     * cancellation. Uses the row's own Delete button (a real click); for an unsaved row the app removes it
     * client-side with no confirm and no server call (verified live). Returns {removed, refusedPosted, remaining}.
     */
    public java.util.Map<String, Object> removeRowsAddedByTest() {
        java.util.List<String> removed = new java.util.ArrayList<>();
        java.util.List<String> refused = new java.util.ArrayList<>();
        try { switchToBillDetailsTab(); } catch (Exception ignore) { }
        try { answerConfirm("No"); } catch (Exception ignore) { }   // never leave a confirm hanging over the grid
        for (int guard = 0; guard < 15; guard++) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> t = (java.util.Map<String, Object>) page.evaluate("() => { " + ROWS_JS
                    + " const mine=__rows().filter(x=>x.__ipcAddedByTest);"
                    + " const posted=mine.filter(x=>Number(x.chargeid||0)!==0).map(__code);"
                    + " const x=mine.find(r=>Number(r.chargeid||0)===0); if(!x) return {code:'', posted};"
                    + " const row=[...document.querySelectorAll('#billDtls .ui-grid-render-container-body .ui-grid-row')].find(r=>{ try{ return angular.element(r).scope().row.entity===x; }catch(e){ return false; } });"
                    + " const btn=row ? row.querySelector('[ng-click*=fnDeleteService]') : null;"
                    + " if(btn){ btn.id='__ipcDelRow'; return {code:__code(x), how:'button', posted}; }"
                    + " return {code:__code(x), how:'scope', posted}; }");
            if (t == null) break;
            Object posted = t.get("posted");
            if (posted instanceof java.util.List) for (Object o : (java.util.List<?>) posted) if (!refused.contains(String.valueOf(o))) refused.add(String.valueOf(o));
            String code = String.valueOf(t.getOrDefault("code", ""));
            if (code.isEmpty()) break;
            if ("button".equals(t.get("how"))) {
                try { page.locator("#__ipcDelRow").scrollIntoViewIfNeeded(); page.locator("#__ipcDelRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
                catch (Exception e) { System.out.println("removeRowsAddedByTest: click failed - " + e.getClass().getSimpleName()); }
                page.evaluate("() => { const e=document.getElementById('__ipcDelRow'); if(e) e.removeAttribute('id'); }");
            } else {
                // Row not rendered (ui-grid virtualisation): call the same handler its Delete button calls — for an
                // unsaved row (chargeid==0, re-checked here) that is a client-side splice.
                page.evaluate("() => { " + ROWS_JS + " const x=__rows().find(r=>r.__ipcAddedByTest && Number(r.chargeid||0)===0); if(!x) return;"
                        + " const s=angular.element(document.querySelector('#billDtls')).scope(); if(s && typeof s.fnDeleteService==='function'){ s.fnDeleteService(x); try{ s.$applyAsync(); }catch(e){} } }");
            }
            waitForAngular(900);
            String c = confirmText();
            if (!c.isEmpty()) { System.out.println("removeRowsAddedByTest: unexpected confirm '" + c + "' - answered No"); answerConfirm("No"); break; }
            removed.add(code);
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("removed", removed);
        out.put("refusedPosted", refused);
        out.put("remaining", gridFingerprint());
        return out;
    }
}
