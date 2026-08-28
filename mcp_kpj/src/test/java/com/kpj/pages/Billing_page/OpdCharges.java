package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Billing &gt; <b>OPD Charges</b> (route {@code #/OPDCharges}) — Page Object (transactional bill posting).
 *
 * <p>Flow (discovered live): <b>Billing</b> → <b>OPD Charges</b> → patient picker popup ({@code OpenPopupScreen()},
 * defaults to Search By OPD + today's Visit Date) → pick a row ({@code SetSearchPatient(PR)}) which loads the visit
 * (OP Visit No / Payor auto-select) and auto-loads any existing unbilled charge into the grid → set the Cost Centre
 * on every charge line → add a procedure ({@code openProcedurePopup()} → pick a Procedure Name → the popup
 * auto-expands into one service row per procedure component, each needing a Doctor → OK) → Save
 * ({@code IUDBill()} → confirm dialog "Have you verified that Cost Centers are defined…" → <b>Validated</b>).</p>
 *
 * <p><b>The Service Name / Procedure Name fields are effectively broken for scripted typing.</b> Both are bound
 * through a bespoke {@code auto-complete="AutoCompleteOptionsForServiceName"} directive (not jQuery-UI, not
 * ui-select) whose {@code ngModel} never leaves {@code ng-pristine} even after real Playwright keystrokes — no
 * dropdown ever renders and no XHR fires. Calling the directive's own config functions directly bypasses this:
 * {@code AutoCompleteOptionsForServiceName.data(query)} returns the same promise the UI would have used, and
 * {@code .itemSelected({item})} (note the wrapper — the callback reads {@code item.item}) is exactly what a real
 * click on a suggestion would invoke. This is a workaround for a real app defect, not a script shortcut.</p>
 */
public class OpdCharges extends BasePage {

    public OpdCharges(Page page) { super(page); }

    public String route = "", lastPatient = "", lastVisit = "", lastProcedure = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*opd\\s*charges\\s*$/i.test(norm(x.textContent)) || /#\\/OPDCharges/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__opcMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__opcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("OpdCharges.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("opdcharges")) {
            try { page.evaluate("() => { window.location.hash = '#/OPDCharges'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => document.querySelector('#textinput') && document.querySelector('#textinput').offsetParent!==null",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("OpdCharges.nav: MRN input not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("opdcharges"); }

    // ---- patient selection -------------------------------------------------

    /**
     * Enter an MRN and click <b>Search</b> to load its OP visit — the real steps ("enter mrn and click search").
     *
     * <p><b>The two buttons beside the MRN box are mislabeled</b> (checked live via their {@code title} + real
     * {@code ng-click}): the one titled <i>"Find Patient"</i> is actually {@code SearchPatientByMRNo()} (the direct
     * MRN search), and the one titled <i>"Search Patient"</i> is actually {@code OpenPopupScreen()} (the picker
     * popup) — backwards from what the titles suggest. This method drives by {@code ng-click}, never by the title
     * text, and always clicks the direct-search button.</p>
     */
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

    /**
     * Open the <b>patient picker popup</b> (ng-click {@code OpenPopupScreen()}; the button confusingly titled
     * "Search Patient" — see {@link #searchByMrn}) purely to DISCOVER a candidate MRN with an open OP visit today
     * — it defaults to Search By = OPD, Visit Date = today. Returns distinct MRNs from the results grid, closing
     * the popup afterwards. The actual visit load still goes through {@link #searchByMrn}, matching the real steps.
     */
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
     *  {@link #searchByMrn} (the real "enter mrn and click search" steps). Returns "MRN / OPVisitNo" (empty if
     *  none of the candidates loaded a visit). */
    public String selectOpdPatient() {
        for (String mrn : candidateOpdMrnsFromPopup(8)) {
            if (searchByMrn(mrn)) {
                System.out.println("selectOpdPatient: loaded " + lastPatient + " / " + lastVisit);
                return lastPatient + " / " + lastVisit;
            }
        }
        return "";
    }

    // ---- broken custom autocomplete workaround ---------------------------

    /** Search the given text-input's {@code auto-complete} directive config directly and pick the first hit, exactly
     *  what a real click on a suggestion would do. Returns "code|name" of the picked item, or "" if none found. */
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
     *  many rows were fixed. Save is blocked ("Have you verified that Cost Centers are defined for all service?")
     *  when any line is missing one. */
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
     *  every service line it expands into (e.g. Doctor Fee + Anaesthesia Fee), and OK. Returns the picked procedure
     *  "code|name", or "" if the popup couldn't be driven. */
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
        boolean clicked = false;
        try {
            page.locator(".modal button:has-text('Ok')").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            clicked = true;
        } catch (Exception e) { System.out.println("addProcedure: OK click failed - " + e.getMessage()); }
        waitForAngular(1000);
        return clicked ? picked + " (" + rows + " service line(s))" : "";
    }

    // ---- mandatory selects (fill only if not already selected) -----------

    private String ensureSelectHasRealValue(String ngModel) {
        String sel = "select[ng-model='" + ngModel + "']";
        Object cur = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return null; const t=e.selectedOptions[0]?e.selectedOptions[0].textContent.trim():''; return {val:e.value, text:t}; }", sel);
        @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) cur;
        String text = m != null && m.get("text") != null ? m.get("text").toString() : "";
        if (!text.isEmpty() && !text.matches("(?i)-*\\s*select\\s*-*")) return text;   // already selected
        try {
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return -1; return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i >= 0) {
                page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
                return String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
            }
        } catch (Exception ignore) { }
        return "(n/a)";
    }

    /** Fill Cash Counter / Validated By only if the app didn't already default them (it usually does). */
    public String fillMandatory() {
        String cc = ensureSelectHasRealValue("CashCounterID");
        String vb = ensureSelectHasRealValue("PatientDetails.validatedby");
        return "CashCounter=" + cc + " | ValidatedBy=" + vb;
    }

    // ---- charge grid introspection (for real persistence verification) --

    /** Read the currently loaded charge lines' service names/codes from the grid's own scope data. */
    public java.util.List<String> readChargeGridServiceNames() {
        Object r = page.evaluate("() => { const A=window.angular; let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data) && (!data||s.grid.options.data.length>data.length)) data=s.grid.options.data; }catch(e){} }); return data ? data.map(row=>row.ServiceName||row.servicename||row.ServiceCode||'') : []; }");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Names present in {@code after} but not in {@code before} (one-for-one, not just set difference — so two
     *  identically-named lines added still count as two). Used to isolate what a save attempt is supposed to add. */
    public static java.util.List<String> diff(java.util.List<String> before, java.util.List<String> after) {
        java.util.List<String> remaining = new java.util.ArrayList<>(before);
        java.util.List<String> added = new java.util.ArrayList<>();
        for (String a : after) { if (!remaining.remove(a)) added.add(a); }
        return added;
    }

    /**
     * Reload the screen and re-search {@code mrn}, then report which of {@code expectedNames} are still missing
     * from the charge grid. <b>This is the only reliable success signal on this screen</b> — Save can return HTTP
     * 200 with an empty body, answer "Validated" cleanly, and the app can still silently drop the newly added
     * charge lines (reproduced live 2026-08-24: a "KPJ Portal / Error!" toastr fired ~1-2s after the Validated
     * click on one save and the two procedure-added lines were gone on reload, while an earlier save with the
     * identical steps had no toast and the lines persisted — same 200 response either way).
     */
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

    /**
     * Click Save ({@code IUDBill}); answer the "Have you verified that Cost Centers are defined…" confirm with the
     * <b>Validated</b> button (its "click validated" affirmative — there is no separate Validate control); wait for
     * the {@code IUDBillSaveOPD} response.
     *
     * <p><b>Do not trust this method's return value alone.</b> It reflects the HTTP response (200) and the absence
     * of an error-looking toast, but a live repro showed both of those can look clean while the save silently did
     * nothing server-side. Callers that add new charge lines should also call {@link #missingAfterReload} to
     * confirm persistence.</p>
     */
    public boolean saveAndValidate() {
        page.evaluate("() => { window.__opcToasts=[]; if(window.__opcObs) window.__opcObs.disconnect();"
                + " const grab=()=>{ [...document.querySelectorAll('[class*=toast]')].forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__opcToasts.includes(t)) window.__opcToasts.push(t); }); };"
                + " window.__opcObs=new MutationObserver(grab); window.__opcObs.observe(document.body,{childList:true,subtree:true}); }");
        com.microsoft.playwright.Response[] resp = new com.microsoft.playwright.Response[1];
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/IUDBill\\(\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__opcSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndValidate: Save (IUDBill) button not found"); return false; }
        try {
            resp[0] = page.waitForResponse(r -> r.url().contains("IUDBillSaveOPD"), () -> {
                try { page.locator("#__opcSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                answerValidatedConfirm();
            });
        } catch (Exception e) {
            System.out.println("saveAndValidate: no IUDBillSaveOPD response - " + e.getMessage());
            blockingMessage = readBlockingDialog();
            return false;
        }
        page.evaluate("() => { const e=document.getElementById('__opcSave'); if(e) e.removeAttribute('id'); }");
        // The "KPJ Portal" error toastr (when it fires) renders a moment after the Validated confirm settles.
        page.waitForTimeout(2000);
        Object t = page.evaluate("() => (window.__opcToasts||[]).join(' | ')");
        lastToast = t == null ? "" : t.toString().trim();
        boolean httpOk = resp[0] != null && resp[0].status() == 200;
        boolean errorToast = lastToast.toLowerCase().contains("error");
        if (errorToast) System.out.println("saveAndValidate: error toast seen -> " + lastToast);
        return httpOk && !errorToast;
    }

    /** Click the <b>Validated</b> button on the "Confirm! Before Saving Have you verified that Cost Centers are
     *  defined for all service?" dialog (its buttons are Validated / No — Validated is the affirmative). Polls
     *  briefly because it renders a moment after the Save click. */
    private void answerValidatedConfirm() {
        for (int i = 0; i < 12; i++) {
            Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,[class*=confirm]')]"
                    + "   .find(m=>m.getBoundingClientRect().width>0 && /cost centers are defined/i.test(m.textContent||''));"
                    + " if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>/^validated$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + " if(!b) return false; b.click(); return true; }");
            if (Boolean.TRUE.equals(clicked)) { System.out.println("answerValidatedConfirm: clicked Validated"); waitForAngular(600); return; }
            page.waitForTimeout(500);
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
