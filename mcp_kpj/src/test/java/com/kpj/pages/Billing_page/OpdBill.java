package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Billing &gt; <b>OPD Bill</b> (route {@code #/OPDBill}) — Page Object (bill generation, distinct from
 * <b>OPD Charges</b> / {@link OpdCharges}, which only posts individual charge lines).
 *
 * <p>Flow (discovered live): <b>Billing</b> → <b>OPD Bill</b> → patient picker popup ({@code OpenPopupScreen()},
 * button confusingly titled "Search Patient" — same mislabel as {@link OpdCharges}) → pick a row
 * ({@code SetSearchPatient(PR)}) which loads the visit and auto-loads any existing unbilled charge into the grid
 * → add a procedure ({@code openProcedurePopup()} → pick a Procedure Name → the popup auto-expands into one
 * service row per procedure component, each needing a Doctor → OK) → set the Cost Centre on every charge line
 * → Save ({@code IUDBill()}).</p>
 *
 * <p><b>Save on this screen is a chain of up to four confirm dialogs</b> (checked live), not one like
 * {@link OpdCharges}: "Are you sure you want to proceed saving the interim/final bill?" (Yes) → "Have you
 * verified that Cost Centers are defined for all service?" (Validated) → occasionally "Do you want to close a
 * visit?" (declined here — <b>No</b> — closing the visit is a real side effect the described test steps never
 * asked for, and it is not required for the bill save itself) → "Patient has IC Number which is not
 * validated. Do you want to proceed without validation?" (Yes). {@link #saveAndValidate()} drives all of these
 * generically by dialog text rather than a fixed sequence, since which ones appear can vary run to run.</p>
 *
 * <p><b>Reproduced live 2026-08-25: the save can fail silently.</b> {@code POST /api/Bill/IUDBillSaveOPD} answers
 * HTTP 200 with an empty JSON array {@code []}. The controller's own success check
 * ({@code response.data[0].ResultStatus == 1}) then reads {@code data[0]} as {@code undefined}, falls into its
 * failure branch, and raises a generic <b>"KPJ Portal / Error!"</b> toastr with no real message — no Bill No. is
 * ever populated. This is a real app defect, not a script defect; see {@link #saveAndValidate()}.</p>
 *
 * <p><b>Also reproduced live 2026-08-25: the Procedure popup's own "Ok" can be blocked by a separate
 * "Confirm! Visit is closed. Please contact billing team to add charges." dialog.</b> This is <i>not</i> tied to
 * any one visit — it fired identically for two different, previously untouched OP visits in the same run of this
 * QA environment (devhis.sancyberhad.com), so it reads as an environment-wide state (e.g. an end-of-day billing
 * close already run for today) rather than something this script or a prior test caused. {@link #addProcedure}
 * detects it via {@link #readStrayConfirm()} and fails fast with the message in {@link #blockingMessage} instead
 * of retrying the intercepted "Ok" click for several seconds.</p>
 *
 * <p>The Service Name / Procedure Name fields share the same broken custom {@code auto-complete} directive as
 * {@link OpdCharges} — see that class's Javadoc for the workaround this page object also uses.</p>
 */
public class OpdBill extends BasePage {

    public OpdBill(Page page) { super(page); }

    public String route = "", lastPatient = "", lastVisit = "", lastProcedure = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*opd\\s*bill\\s*$/i.test(norm(x.textContent)) || /#\\/OPDBill\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__opbMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__opbMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("OpdBill.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("opdbill")) {
            try { page.evaluate("() => { window.location.hash = '#/OPDBill'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => document.querySelector('#textinput') && document.querySelector('#textinput').offsetParent!==null",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("OpdBill.nav: MRN input not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("opdbill"); }

    // ---- patient selection -------------------------------------------------

    /** Enter an MRN and click Search to load its OP visit (same two-mislabeled-buttons gotcha as
     *  {@link OpdCharges#searchByMrn} — drives by {@code ng-click}, never by the title text). */
    public boolean searchByMrn(String mrn) {
        try { page.locator("#textinput").fill(mrn); } catch (Exception e) { System.out.println("searchByMrn: fill failed - " + e.getMessage()); return false; }
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__mrnSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("searchByMrn: Search (SearchPatientByMRNo) button not found"); return false; }
        try { page.locator("#__mrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__mrnSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        Object st = page.evaluate("() => { const sel=document.querySelector(\"select[ng-model='PatientDetails.OPDNo']\"); return sel && sel.selectedOptions[0] ? sel.selectedOptions[0].textContent.trim() : ''; }");
        String opt = st == null ? "" : st.toString();
        if (!opt.isEmpty()) { lastVisit = opt; lastPatient = mrn; }
        return !opt.isEmpty();
    }

    /** Open the patient picker popup (ng-click {@code OpenPopupScreen()}, button titled "Search Patient") purely to
     *  DISCOVER a candidate MRN with an open OP visit — defaults to Search By = OPD, Visit Date = today. Returns
     *  distinct MRNs from the results grid via the row's own "Select" button ({@code SetSearchPatient(PR)}), which
     *  also closes the popup — matching the real "search -> select -> popup closes" steps. */
    public java.util.List<String> candidateOpdMrnsFromPopup(int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__popSearch'; }");
        try { page.locator("#__popSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')).length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("candidateOpdMrnsFromPopup: no rows for today's visits"); }
        waitForAngular(600);
        Object mrns = page.evaluate("() => [...document.querySelectorAll('table tbody tr, .ui-grid-row')].filter(r=>r.offsetParent!==null && /\\d{6,}/.test(r.textContent||'')).map(r=>(r.textContent||'').replace(/\\s+/g,' ').trim().match(/^\\S+/)[0])");
        if (mrns instanceof java.util.List) {
            for (Object o : (java.util.List<?>) mrns) { String s = String.valueOf(o); if (!out.contains(s)) out.add(s); if (out.size() >= max) break; }
        }
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/CloseModel/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(500);
        return out;
    }

    /** Discover candidate MRNs from the popup, then load the first one whose OP visit actually resolves via
     *  {@link #searchByMrn}. Returns "MRN / OPVisitNo" (empty if none of the candidates loaded a visit). */
    public String selectOpdPatient() {
        for (String mrn : candidateOpdMrnsFromPopup(8)) {
            if (searchByMrn(mrn)) {
                System.out.println("selectOpdPatient: loaded " + lastPatient + " / " + lastVisit);
                return lastPatient + " / " + lastVisit;
            }
        }
        return "";
    }

    /** Same picker, but selects a specific row via its own "Select" button ({@code ng-click="SetSearchPatient(PR)"})
     *  instead of going back through {@link #searchByMrn} — matches "search any patient -> select the patient ->
     *  (popup closes)" exactly, including auto-loading any existing unbilled charge for that visit. Returns
     *  "MRN / OPVisitNo" (empty if no row could be selected). */
    public String searchAndSelectAnyPatient() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__popSearch'; }");
        try { page.locator("#__popSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr')].some(r=>r.offsetParent!==null && r.querySelector('button[ng-click*=\"SetSearchPatient\"]'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("searchAndSelectAnyPatient: no selectable rows"); }
        waitForAngular(400);
        Object picked = page.evaluate("() => { const row=[...document.querySelectorAll('table tbody tr')].find(r=>r.offsetParent!==null && r.querySelector('button[ng-click*=\"SetSearchPatient\"]'));"
                + " if(!row) return ''; const mrn=(row.textContent||'').replace(/\\s+/g,' ').trim().match(/^\\S+/)[0];"
                + " row.querySelector('button[ng-click*=\"SetSearchPatient\"]').click(); return mrn; }");
        String mrn = picked == null ? "" : picked.toString();
        if (mrn.isEmpty()) return "";
        try { page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(800);
        Object st = page.evaluate("() => { const sel=document.querySelector(\"select[ng-model='PatientDetails.OPDNo']\"); return sel && sel.selectedOptions[0] ? sel.selectedOptions[0].textContent.trim() : ''; }");
        String opt = st == null ? "" : st.toString();
        if (!opt.isEmpty()) { lastVisit = opt; lastPatient = mrn; return mrn + " / " + opt; }
        return "";
    }

    // ---- broken custom autocomplete workaround (same defect as OpdCharges) ----

    private String pickViaAutoComplete(String inputSelector, String query) {
        Object r = page.evaluate("(a) => { const n=document.querySelector(a.sel); if(!n) return null; const A=window.angular; const s=A.element(n).scope();"
                + " const o=s.AutoCompleteOptionsForServiceName; if(!o) return null;"
                + " return o.data(a.q).then(items => { const item=(items||[])[0]; if(!item) return null; o.itemSelected({item}); try{s.$apply&&s.$apply();}catch(e){}"
                + "   return { code:item.ServiceCode||'', name:item.ServiceName||'' }; }); }",
                java.util.Map.of("sel", inputSelector, "q", query));
        @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) r;
        if (m == null) return "";
        return (m.get("code") == null ? "" : m.get("code")) + "|" + (m.get("name") == null ? "" : m.get("name"));
    }

    // ---- cost centre (grid, per row) --------------------------------------

    /** Set every charge line's Cost Centre select that is still "--Select--" to its first real option. Returns how
     *  many rows were fixed. */
    public int ensureAllCostCentresSet() {
        Object idxs = page.evaluate("() => { const sels=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null"
                + "   && [...s.options].some(o=>/General$/.test((o.textContent||'').trim()))"
                + "   && s.selectedOptions[0] && (s.selectedOptions[0].textContent||'').trim()==='--Select--');"
                + " sels.forEach((s,i)=>s.id='__ccFix'+i); return sels.length; }");
        int n = idxs instanceof Number ? ((Number) idxs).intValue() : 0;
        for (int i = 0; i < n; i++) {
            try {
                com.microsoft.playwright.Locator sel = page.locator("#__ccFix" + i);
                int optIdx = (int) sel.evaluate("(e) => [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && (o.textContent||'').trim()!=='--Select--')");
                if (optIdx >= 0) sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(optIdx));
            } catch (Exception ignore) { }
        }
        page.evaluate("() => { for(let i=0;;i++){ const e=document.getElementById('__ccFix'+i); if(!e) break; e.removeAttribute('id'); } }");
        waitForAngular(400);
        return n;
    }

    // ---- procedure popup ---------------------------------------------------

    /** Open the Procedure popup, pick the first procedure matching {@code query}, select the first real Doctor for
     *  every service line it expands into, and OK. Returns the picked procedure "code|name", or "" if the popup
     *  couldn't be driven. */
    public String addProcedure(String query) {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/openProcedurePopup/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null && /Procedure Details/i.test(m.textContent||''))", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(500);
        String picked = pickViaAutoComplete(".modal #txtServiceName", query);
        if (picked.equals("|")) { System.out.println("addProcedure: no procedure matched '" + query + "'"); return ""; }
        lastProcedure = picked;
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal table select')].filter(s=>s.offsetParent!==null).length > 0", null, new Page.WaitForFunctionOptions().setTimeout(6000)); } catch (Exception ignore) { }
        waitForAngular(500);
        Object n = page.evaluate("() => { const sels=[...document.querySelectorAll('.modal table select')].filter(s=>s.offsetParent!==null); sels.forEach((s,i)=>s.id='__docSel'+i); return sels.length; }");
        int rows = n instanceof Number ? ((Number) n).intValue() : 0;
        for (int i = 0; i < rows; i++) {
            try {
                com.microsoft.playwright.Locator sel = page.locator("#__docSel" + i);
                int optIdx = (int) sel.evaluate("(e) => [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && (o.textContent||'').trim()!=='--Select--')");
                if (optIdx >= 0) sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(optIdx));
            } catch (Exception ignore) { }
        }
        page.evaluate("() => { for(let i=0;;i++){ const e=document.getElementById('__docSel'+i); if(!e) break; e.removeAttribute('id'); } }");
        waitForAngular(300);
        // A stray $ngConfirm (e.g. "Visit is closed. Please contact billing team to add charges.") can pop up on
        // top of the Procedure popup right here and intercept every click on its "Ok" button — dismiss it (click
        // its own OK) and report that message instead of retrying the intercepted click for ~13 attempts / 5s+
        // before timing out.
        String stray = dismissStrayConfirmIfPresent();
        if (!stray.isEmpty()) {
            blockingMessage = stray;
            System.out.println("addProcedure: blocked by a confirm dialog (dismissed with OK) -> " + stray);
            try { page.locator(".modal button:has-text('Cancel')").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignore) { }
            return "";
        }
        boolean clicked = false;
        try {
            page.locator(".modal button:has-text('Ok')").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            clicked = true;
        } catch (Exception e) { System.out.println("addProcedure: OK click failed - " + e.getMessage()); }
        waitForAngular(1000);
        return clicked ? picked + " (" + rows + " service line(s))" : "";
    }

    /** Detects an {@code $ngConfirm} box sitting on top of whatever screen/popup is currently open (distinct from
     *  the Bootstrap {@code .modal} popups this page object drives itself) — e.g. "Confirm! Visit is closed.
     *  Please contact billing team to add charges." — clicks its <b>OK</b> button to dismiss it (per the user's
     *  explicit instruction: "click ok for this popup whenever it appears"), and returns its message, or "" if
     *  none is open. */
    private String dismissStrayConfirmIfPresent() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const box=[...document.querySelectorAll('.ng-confirm-box,[class*=\"ng-confirm \"],[class^=\"ng-confirm\"]')].find(m=>m.getBoundingClientRect().width>0 && norm(m.textContent));"
                + " if(!box) return '';"
                + " const msg=norm(box.textContent).slice(0,200);"
                + " const b=[...box.querySelectorAll('button,a')].find(x=>/^ok$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                + " if(b) b.click(); return msg; }");
        return r == null ? "" : r.toString().trim();
    }

    // ---- charge grid introspection ------------------------------------------

    public java.util.List<String> readChargeGridServiceNames() {
        Object r = page.evaluate("() => { const A=window.angular; let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data) && (!data||s.grid.options.data.length>data.length)) data=s.grid.options.data; }catch(e){} }); return data ? data.map(row=>row.ServiceName||row.servicename||row.ServiceCode||'') : []; }");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public static java.util.List<String> diff(java.util.List<String> before, java.util.List<String> after) {
        java.util.List<String> remaining = new java.util.ArrayList<>(before);
        java.util.List<String> added = new java.util.ArrayList<>();
        for (String a : after) { if (!remaining.remove(a)) added.add(a); }
        return added;
    }

    /** Reload and re-select {@code mrn} via the popup, then report which of {@code expectedNames} are still
     *  missing from the charge grid — the only reliable success signal on this screen (see class Javadoc: Save
     *  can answer 200 with an empty body and the app can still silently drop the new lines). */
    public java.util.List<String> missingAfterReload(String mrn, java.util.List<String> expectedNames) {
        if (expectedNames.isEmpty()) return java.util.Collections.emptyList();
        page.reload();
        waitForAngular(1500);
        try { page.waitForFunction("() => document.querySelector('#textinput') && document.querySelector('#textinput').offsetParent!==null", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        searchByMrn(mrn);
        waitForAngular(1000);
        java.util.List<String> after = readChargeGridServiceNames();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String exp : expectedNames) {
            boolean found = after.stream().anyMatch(a -> a != null && a.contains(exp));
            if (!found) missing.add(exp);
        }
        return missing;
    }

    // ---- save --------------------------------------------------------------

    public String blockingMessage = "";
    public String lastToast = "";
    /** Screenshot taken the moment {@link #saveAndValidate()} reads the toast text — the toast fades within ~1s
     *  (see {@code DevHisBase} class Javadoc), and by the time a caller's own {@code step(page, ...)} screenshot
     *  fires (typically after {@link #missingAfterReload}, i.e. after a page reload) it is long gone from the DOM.
     *  Pass this to {@code step(byte[], ...)} instead when the toast itself needs to be visible as evidence. */
    public byte[] lastScreenshot = null;

    /**
     * Click Save ({@code IUDBill}) and drive whichever confirm dialogs actually appear (see class Javadoc) until
     * none are left, then wait for the {@code IUDBillSaveOPD} response.
     *
     * <p><b>Do not trust this method's return value alone</b> for a screen that already had charge lines — a live
     * repro on 2026-08-25 showed {@code IUDBillSaveOPD} answering HTTP 200 with an empty {@code []} body (no
     * "ResultStatus" to read), which the app surfaces as a generic "KPJ Portal / Error!" toastr rather than any
     * specific validation message. Callers that add new charge lines should also call {@link #missingAfterReload}
     * to confirm persistence, exactly like {@link OpdCharges#saveAndValidate()}.</p>
     */
    public boolean saveAndValidate() {
        page.evaluate("() => { window.__opbToasts=[]; if(window.__opbObs) window.__opbObs.disconnect();"
                + " const grab=()=>{ [...document.querySelectorAll('[class*=toast]')].forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__opbToasts.includes(t)) window.__opbToasts.push(t); }); };"
                + " window.__opbObs=new MutationObserver(grab); window.__opbObs.observe(document.body,{childList:true,subtree:true}); }");
        com.microsoft.playwright.Response[] resp = new com.microsoft.playwright.Response[1];
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/IUDBill\\(\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__opbSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndValidate: Save (IUDBill) button not found"); return false; }
        try {
            resp[0] = page.waitForResponse(r -> r.url().contains("IUDBillSaveOPD"), () -> {
                try { page.locator("#__opbSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                answerSaveConfirms();
            });
        } catch (Exception e) {
            System.out.println("saveAndValidate: no IUDBillSaveOPD response - " + e.getMessage());
            blockingMessage = readBlockingDialog();
            return false;
        }
        page.evaluate("() => { const e=document.getElementById('__opbSave'); if(e) e.removeAttribute('id'); }");
        // The "KPJ Portal" error toastr (when it fires) renders a moment after the last confirm settles and then
        // fades within ~1s — poll for it and screenshot at the FIRST sighting instead of a fixed wait, or the
        // toast is gone from the DOM by the time a fixed-delay screenshot fires.
        for (int i = 0; i < 10; i++) {
            Object visible = page.evaluate("() => { const el=[...document.querySelectorAll('[class*=toast]')].find(e=>e.offsetParent!==null); return el ? (el.textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
            if (visible != null && !visible.toString().trim().isEmpty()) {
                try { lastScreenshot = page.screenshot(new Page.ScreenshotOptions().setTimeout(5000)); } catch (Exception e) { lastScreenshot = null; }
                break;
            }
            page.waitForTimeout(250);
        }
        if (lastScreenshot == null) {
            try { lastScreenshot = page.screenshot(new Page.ScreenshotOptions().setTimeout(5000)); } catch (Exception e) { lastScreenshot = null; }
        }
        page.waitForTimeout(600);   // let the toast-log MutationObserver catch anything the poll above just missed
        Object t = page.evaluate("() => (window.__opbToasts||[]).join(' | ')");
        lastToast = t == null ? "" : t.toString().trim();
        boolean httpOk = resp[0] != null && resp[0].status() == 200;
        boolean errorToast = lastToast.toLowerCase().contains("error");
        if (errorToast) System.out.println("saveAndValidate: error toast seen -> " + lastToast);
        return httpOk && !errorToast;
    }

    /**
     * Drive whichever confirm dialogs appear after clicking Save, by matching each dialog's own text rather than a
     * fixed sequence (checked live: which dialogs appear, and in what order, varied run to run):
     * <ul>
     *   <li>"proceed saving the interim/final bill?" → <b>Yes</b></li>
     *   <li>"Have you verified that Cost Centers are defined…" → <b>Validated</b></li>
     *   <li>"Do you want to close a visit?" → <b>No</b> — a real side effect the described steps never asked for,
     *       and not required for the bill save itself.</li>
     *   <li>"Patient has IC Number which is not validated…" → <b>Yes</b> (proceed without validation)</li>
     * </ul>
     * Polls briefly since each dialog renders a moment after the previous one is answered.
     */
    private void answerSaveConfirms() {
        for (int i = 0; i < 20; i++) {
            Object handled = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')].filter(m=>m.getBoundingClientRect().width>0);"
                    + " for (const box of boxes) {"
                    + "   const txt = norm(box.textContent).toLowerCase();"
                    + "   let want = null;"
                    + "   if (/proceed saving the (interim|final) bill/.test(txt)) want = 'yes';"
                    + "   else if (/cost centers are defined/.test(txt)) want = 'validated';"
                    + "   else if (/do you want to close a visit/.test(txt)) want = 'no';"
                    + "   else if (/ic number which is not validated/.test(txt)) want = 'yes';"
                    + "   else continue;"
                    + "   const b=[...box.querySelectorAll('button,a')].find(x=>new RegExp('^'+want+'$','i').test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + "   if (b) { b.click(); return txt.slice(0,90); }"
                    + " }"
                    + " return null; }");
            if (handled != null) { System.out.println("answerSaveConfirms: answered -> " + handled); waitForAngular(500); continue; }
            page.waitForTimeout(400);
            Object anyOpen = page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')].some(m=>m.getBoundingClientRect().width>0)");
            if (!Boolean.TRUE.equals(anyOpen)) return;
        }
    }

    private String readBlockingDialog() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')]"
                + "   .find(m=>m.getBoundingClientRect().width>0 && norm(m.textContent));"
                + " if(!box) return ''; const msg=norm(box.textContent).slice(0,180);"
                + " const b=[...box.querySelectorAll('button,a')].find(x=>/^(ok|close|no|cancel)$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                + " if(b) b.click(); return msg; }");
        return r == null ? "" : r.toString().trim();
    }
}
