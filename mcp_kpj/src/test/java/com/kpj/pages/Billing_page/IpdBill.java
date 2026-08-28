package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Billing &gt; <b>IPD Bill</b> (route {@code #/IPDBill}) — Page Object (inpatient bill generation, the IPD sibling
 * of {@link OpdBill}). Distinct from <b>IPD Charges</b>, which only posts individual charge lines.
 *
 * <p>Flow (discovered live 2026-08-25): <b>Billing</b> → <b>IPD Bill</b> → patient picker popup
 * ({@code OpenPopupScreen()}, same mislabelled "Search Patient" button as {@link OpdBill}) → select <b>IPD</b>
 * (no Search-By radio is pre-selected on this screen, unlike OPD Bill which defaults to OPD) → pick a row
 * ({@code SetSearchPatient(PR)}), which loads the admission and auto-loads any existing unbilled charge into the
 * grid → for a newborn ("Baby Of …") patient, <b>Merge With Mother</b> ({@code mergeChargesWithMother()}) → confirm
 * "Are you sure you want to merge charges with mother." with its own <b>Save</b> button → set the Cost Centre on
 * every charge line ({@code row.entity.CostcenterId}, same mechanism as {@link OpdBill}) → Save ({@code IUDBill()}).
 * </p>
 *
 * <p><b>Reproduced live 2026-08-25: both known {@link OpdBill} defects reproduce here too.</b></p>
 * <ul>
 *   <li>An environment-wide <b>"Admission is closed. Please contact billing team to add charges."</b> confirm can
 *       appear the moment a charge-affecting action (e.g. Merge With Mother) is attempted — the IPD wording of the
 *       same state {@link OpdBill} sees as "Visit is closed…". Confirmed live not tied to one admission (see that
 *       class's Javadoc for the OPD-side evidence). {@link #mergeWithMother()} dismisses it with <b>OK</b> per the
 *       user's standing instruction ("click ok for this popup whenever it appears") and continues.</li>
 *   <li>{@code POST /api/Bill/IUDBillSaveOPD} (yes — the same endpoint name is reused for the IPD bill save) can
 *       answer HTTP 200 with an empty body while the app raises the same generic <b>"KPJ Portal / Error!"</b>
 *       toastr. See {@link #saveAndValidate()}.</li>
 * </ul>
 */
public class IpdBill extends BasePage {

    public IpdBill(Page page) { super(page); }

    public String route = "", lastPatient = "", lastVisit = "";
    public String blockingMessage = "";
    public String lastToast = "";
    public String mergeBlockingMessage = "";
    /** Screenshot taken the moment {@link #saveAndValidate()} first sees the toast — see {@link OpdBill#lastScreenshot}
     *  for why a caller's own later {@code step(page, ...)} screenshot would miss it. */
    public byte[] lastScreenshot = null;

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*ipd\\s*bill\\s*$/i.test(norm(x.textContent)) || /#\\/IPDBill\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ipbMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__ipbMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("IpdBill.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("ipdbill")) {
            try { page.evaluate("() => { window.location.hash = '#/IPDBill'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => document.querySelector('input[ng-model=\"PatientDetails.MRNo\"]') && document.querySelector('input[ng-model=\"PatientDetails.MRNo\"]').offsetParent!==null",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("IpdBill.nav: MRN input not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("ipdbill"); }

    // ---- patient selection -------------------------------------------------

    /** Enter an MRN and click Search to load its admission (same mislabelled-buttons gotcha as
     *  {@link OpdBill#searchByMrn} — drives by {@code ng-click}, never by the title text). Success is read from the
     *  "Name : …" patient-details label (this screen has no OP-Visit-No-style select to read back from). */
    public boolean searchByMrn(String mrn) {
        try { page.locator("input[ng-model='PatientDetails.MRNo']").first().fill(mrn); } catch (Exception e) { System.out.println("searchByMrn: fill failed - " + e.getMessage()); return false; }
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__mrnSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("searchByMrn: Search (SearchPatientByMRNo) button not found"); return false; }
        try { page.locator("#__mrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__mrnSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        Object nm = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const l=[...document.querySelectorAll('label')].find(e=>/^Name\\s*:/.test(norm(e.textContent))); return l ? norm(l.textContent) : ''; }");
        String name = nm == null ? "" : nm.toString().trim();
        if (name.length() > "Name :".length()) { lastPatient = mrn; lastVisit = name; return true; }
        return false;
    }

    /** Open the patient picker popup and select <b>IPD</b> explicitly (unlike {@link OpdBill}, no Search-By radio
     *  is pre-selected on this screen), search, and return distinct MRNs from the results grid, closing the popup
     *  afterwards. */
    public java.util.List<String> candidateIpdMrnsFromPopup(int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.app-modal-window')].length > 0", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const dlgs=[...document.querySelectorAll('.app-modal-window')]; const d=dlgs[dlgs.length-1]; if(!d) return;"
                + " const ipd=[...d.querySelectorAll('input[type=radio]')].find(r=>/^ipd$/i.test(norm((r.closest('label')||{}).textContent||'')));"
                + " if(ipd) ipd.click(); }");
        waitForAngular(300);
        page.evaluate("() => { const dlgs=[...document.querySelectorAll('.app-modal-window')]; const d=dlgs[dlgs.length-1]; if(!d) return;"
                + " const b=[...d.querySelectorAll('[ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__popSearch'; }");
        try { page.locator("#__popSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr')].filter(r=>/\\d{6,}/.test(r.textContent||'') && r.querySelector('button[ng-click*=\"SetSearchPatient\"]')).length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("candidateIpdMrnsFromPopup: no rows for IPD admissions"); }
        waitForAngular(600);
        Object mrns = page.evaluate("() => [...document.querySelectorAll('table tbody tr')].filter(r=>/\\d{6,}/.test(r.textContent||'') && r.querySelector('button[ng-click*=\"SetSearchPatient\"]')).map(r=>(r.textContent||'').replace(/\\s+/g,' ').trim().match(/^\\S+/)[0])");
        if (mrns instanceof java.util.List) {
            for (Object o : (java.util.List<?>) mrns) { String s = String.valueOf(o); if (!out.contains(s)) out.add(s); if (out.size() >= max) break; }
        }
        page.evaluate("() => { const b=[...document.querySelectorAll('.app-modal-window [ng-click]')].find(x=>/CloseModel/.test(x.getAttribute('ng-click')||'') && x.getBoundingClientRect().width>0); if(b) b.click(); }");
        waitForAngular(500);
        return out;
    }

    /**
     * Select the Nth newborn ("Baby Of …") row from the patient picker popup by clicking <b>its own</b> "Select"
     * button ({@code SetSearchPatient(PR)}) — the only mechanism proven live to actually load a newborn's
     * admission on this screen. (Typing the MRN into the MRN box and clicking the direct-search button, the
     * pattern that works on {@link OpdBill}, does <b>not</b>: {@code POST /api/patientregistration/FetchPatientData}
     * answers {@code []} for these newborns when searched that way — reproduced live 2026-08-25.) Reopens the
     * popup fresh each call so a different index can be picked on retry. Returns "MRN / Name" (empty if fewer than
     * {@code index+1} "Baby Of" rows exist, or the row didn't load).
     */
    public String selectBabyPatientByIndex(int index) {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.app-modal-window')].length > 0", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const dlgs=[...document.querySelectorAll('.app-modal-window')]; const d=dlgs[dlgs.length-1]; if(!d) return;"
                + " const ipd=[...d.querySelectorAll('input[type=radio]')].find(r=>/^ipd$/i.test(norm((r.closest('label')||{}).textContent||'')));"
                + " if(ipd) ipd.click(); }");
        waitForAngular(300);
        page.evaluate("() => { const dlgs=[...document.querySelectorAll('.app-modal-window')]; const d=dlgs[dlgs.length-1]; if(!d) return;"
                + " const b=[...d.querySelectorAll('[ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__popSearch2'; }");
        try { page.locator("#__popSearch2").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__popSearch2'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table tbody tr')].some(r=>/baby of/i.test(r.textContent||'') && r.querySelector('button[ng-click*=\"SetSearchPatient\"]'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("selectBabyPatientByIndex: no 'Baby Of' rows found"); }
        waitForAngular(600);
        Object picked = page.evaluate("(idx) => { const rows=[...document.querySelectorAll('table tbody tr')].filter(r=>/baby of/i.test(r.textContent||'') && r.querySelector('button[ng-click*=\"SetSearchPatient\"]'));"
                + " const row=rows[idx]; if(!row) return ''; const mrn=(row.textContent||'').replace(/\\s+/g,' ').trim().match(/^\\S+/)[0];"
                + " row.querySelector('button[ng-click*=\"SetSearchPatient\"]').click(); return mrn; }", index);
        String mrn = picked == null ? "" : picked.toString();
        if (mrn.isEmpty()) { System.out.println("selectBabyPatientByIndex: no 'Baby Of' row at index " + index); return ""; }
        try { page.waitForFunction("() => ![...document.querySelectorAll('.app-modal-window')].some(m=>m.getBoundingClientRect().width>0)", null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(800);
        Object nm = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const l=[...document.querySelectorAll('label')].find(e=>/^Name\\s*:/.test(norm(e.textContent))); return l ? norm(l.textContent) : ''; }");
        String name = nm == null ? "" : nm.toString().trim();
        if (name.length() > "Name :".length()) { lastPatient = mrn; lastVisit = name; return mrn + " / " + name; }
        return "";
    }

    // ---- merge with mother ---------------------------------------------------

    /**
     * Click <b>Merge With Mother</b> ({@code mergeChargesWithMother()}). An environment-wide "Admission is closed"
     * confirm can appear at the same moment as the real "Are you sure you want to merge charges with mother."
     * confirm (both stacked, checked live) — dismiss the closed-state one with <b>OK</b> first (per the user's
     * standing instruction), then answer the merge confirm's own <b>Save</b> button. Returns true if the merge
     * confirm was seen and answered (regardless of whether the environment-closed state also fired — that is
     * recorded separately in {@link #mergeBlockingMessage}).
     */
    public boolean mergeWithMother() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/merge with mother/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__mergeBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("mergeWithMother: Merge With Mother button not found (not a newborn / no mother link?)"); return false; }
        try { page.locator("#__mergeBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("mergeWithMother: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__mergeBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        // Dismiss the stray "Admission is closed" confirm (click OK) if it showed up alongside the merge confirm.
        for (int i = 0; i < 6; i++) {
            Object dismissed = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const box=[...document.querySelectorAll('.ng-confirm-box')].find(m=>m.getBoundingClientRect().width>0 && /admission is closed/i.test(m.textContent||''));"
                    + " if(!box) return null; const msg=norm(box.textContent).slice(0,200);"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>/^ok$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + " if(b) b.click(); return msg; }");
            if (dismissed != null) { mergeBlockingMessage = dismissed.toString(); System.out.println("mergeWithMother: dismissed stray confirm -> " + dismissed); waitForAngular(400); }
            else break;
        }
        // Answer the actual merge confirm.
        for (int i = 0; i < 10; i++) {
            Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const box=[...document.querySelectorAll('.ng-confirm-box')].find(m=>m.getBoundingClientRect().width>0 && /merge charges with mother/i.test(m.textContent||''));"
                    + " if(!box) return false; const b=[...box.querySelectorAll('button,a')].find(x=>/^save$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + " if(!b) return false; b.click(); return true; }");
            if (Boolean.TRUE.equals(clicked)) { waitForAngular(800); return true; }
            page.waitForTimeout(400);
        }
        System.out.println("mergeWithMother: 'merge charges with mother' confirm never appeared");
        return false;
    }

    // ---- cost centre (grid, per row) --------------------------------------

    /** Set every charge line's Cost Centre select that is still "--Select--" to its first real option. Returns how
     *  many rows were fixed. Same mechanism as {@link OpdBill#ensureAllCostCentresSet()}. */
    public int ensureAllCostCentresSet() {
        Object idxs = page.evaluate("() => { const sels=[...document.querySelectorAll(\"select[ng-model='row.entity.CostcenterId']\")].filter(s=>s.offsetParent!==null"
                + "   && s.selectedOptions[0] && (s.selectedOptions[0].textContent||'').trim()==='--Select--');"
                + " sels.forEach((s,i)=>s.id='__ccFix'+i); return sels.length; }");
        int n = idxs instanceof Number ? ((Number) idxs).intValue() : 0;
        for (int i = 0; i < n; i++) {
            try {
                com.microsoft.playwright.Locator sel = page.locator("#__ccFix" + i);
                int optIdx = (int) sel.evaluate("(e) => [...e.options].findIndex(o=>o.value && (o.textContent||'').trim()==='General')");
                if (optIdx >= 0) sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(optIdx));
            } catch (Exception ignore) { }
        }
        page.evaluate("() => { for(let i=0;;i++){ const e=document.getElementById('__ccFix'+i); if(!e) break; e.removeAttribute('id'); } }");
        waitForAngular(400);
        return n;
    }

    // ---- save --------------------------------------------------------------

    /**
     * Click Save ({@code IUDBill()}) and drive whichever confirm dialogs actually appear (same generic,
     * text-matching approach as {@link OpdBill#saveAndValidate()} — checked live, only the "proceed saving the
     * interim/final bill?" confirm fired for a clean newborn+merge run, but the Cost-Centers-verified and
     * IC-not-validated confirms are handled too in case they appear for other patients), then waits for the
     * {@code IUDBillSaveOPD} response.
     *
     * <p><b>Do not trust the return value alone</b> — reproduced live 2026-08-25: HTTP 200 with an empty body and
     * a generic "KPJ Portal / Error!" toastr, exactly like {@link OpdBill#saveAndValidate()}.</p>
     */
    public boolean saveAndValidate() {
        page.evaluate("() => { window.__ipbToasts=[]; if(window.__ipbObs) window.__ipbObs.disconnect();"
                + " const grab=()=>{ [...document.querySelectorAll('[class*=toast]')].forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ipbToasts.includes(t)) window.__ipbToasts.push(t); }); };"
                + " window.__ipbObs=new MutationObserver(grab); window.__ipbObs.observe(document.body,{childList:true,subtree:true}); }");
        com.microsoft.playwright.Response[] resp = new com.microsoft.playwright.Response[1];
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/IUDBill\\(\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__ipbSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndValidate: Save (IUDBill) button not found"); return false; }
        try {
            resp[0] = page.waitForResponse(r -> r.url().contains("IUDBillSaveOPD") || r.url().contains("IUDBillSaveIPD"), () -> {
                try { page.locator("#__ipbSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                answerSaveConfirms();
            });
        } catch (Exception e) {
            System.out.println("saveAndValidate: no save response - " + e.getMessage());
            blockingMessage = readBlockingDialog();
            return false;
        }
        page.evaluate("() => { const e=document.getElementById('__ipbSave'); if(e) e.removeAttribute('id'); }");
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
        page.waitForTimeout(600);
        Object t = page.evaluate("() => (window.__ipbToasts||[]).join(' | ')");
        lastToast = t == null ? "" : t.toString().trim();
        boolean httpOk = resp[0] != null && resp[0].status() == 200;
        boolean errorToast = lastToast.toLowerCase().contains("error");
        if (errorToast) System.out.println("saveAndValidate: error toast seen -> " + lastToast);
        return httpOk && !errorToast;
    }

    /** Drive whichever confirm dialogs appear after clicking Save, matching each by its own text (see
     *  {@link #saveAndValidate()}). "Do you want to close the admission?" (reproduced live 2026-08-25 — the IPD
     *  wording of {@link OpdBill}'s "Do you want to close a visit?") is answered <b>No</b>, for the same reason:
     *  a real side effect the described test steps never asked for. */
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
                    + "   else if (/do you want to close the admission/.test(txt)) want = 'no';"
                    + "   else if (/ic number which is not validated/.test(txt)) want = 'yes';"
                    + "   else if (/admission is closed/.test(txt)) want = 'ok';"
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
