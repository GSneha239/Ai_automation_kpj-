package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PatientSearchPopup;
import com.kpj.pages.PdfReport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; POS &gt; <b>Pharmacy Sale/Dispense</b> — Page Object ({@code #/pharmacysale}).
 *
 * <p>Confirmed live (devhis build v2026.10.22.279). One screen, no New button:</p>
 * <ul>
 *   <li><b>MRN No.</b> ({@code pharmacysale.mrno}) + <b>Search</b> ({@code SearchPatientByMRNo()}), or the Patient Search popup beside it
 *       ({@code OpenPatientSearchPopupScreen()}, the shared popup of {@link PatientSearchPopup}). For an OPD patient with NO visit today the search
 *       raises an ng-confirm dialog <i>"Today's Visit of this Patient is Not Mark Do you want to Mark Today's Visit with Previous Visit Data?"</i>
 *       (Yes / No). Typing the MRN and clicking Search runs the search TWICE (the MRN box's blur and the click), so the dialog is often stacked
 *       TWICE — the second one sits on top of the first and the first one's Yes cannot be clicked until the top one is gone. <b>Yes</b> opens the
 *       <b>Mark Visit</b> modal ({@code #VisitMarkModal}: Department ({@code pharmacysale.DepartmentID}), Sub Dept, Doctor, … — only Department is
 *       needed; <b>Mark Visit</b> {@code fnMarkVisit()} posts {@code pharmacysale/IUD} "MarkVisit" and re-runs the search); a stacked second
 *       confirmation stays over the modal until its Yes is clicked too. A patient who already has a visit today loads without any dialog.</li>
 *   <li>Header after the patient loads: Payor ({@code .receivableid}; auto-filled only on the mark-visit path — one option, SELFPAY / CASH CUSTOMER),
 *       <b>Store*</b> ({@code .storeid}; EMPTY by default, and BOTH the Patient Search popup (it silently does not open without a store) and the item
 *       search need it), Patient Type, OP Visit No.</li>
 *   <li>Item strip: <b>Name</b> ({@code #txtServiceName}) is a SMART SEARCH ({@code li.auto-complete-item}); clicking one fills the code.
 *       <b>Quantity</b> ({@code #PSqty}): Enter ({@code AddDetails}) adds the line to the <b>Counter Sale Information</b> grid
 *       ({@code gridOptionsadd}); without a loaded patient / payor it warns "Please select payor!".</li>
 *   <li><b>Save</b> ({@code IUDSave()}) validates, then shows a confirmation ("PharmacySale:Details Do you want to Save Charges?", YES / NO); YES posts
 *       {@code /api/pharmacysale/IUD}; on {@code ResultStatus 1} ("Item sale done successfully", {@code TransNo} SALE…, {@code ResponseId} = bill header id) the app
 *       opens the sale/drug-label report in a NEW WINDOW ({@code SQLReport/InventoryReport/frmSaleDrugLabelReport.aspx?billheaderid=..}, a PDF).</li>
 * </ul>
 */
public class PharmacySale extends BasePage {

    public PharmacySale(Page page) { super(page); }

    /** What a patient search ended with. */
    public enum Outcome { MARK_VISIT_CONFIRM, OTHER_CONFIRM, LOADED, NOT_FOUND, NO_ANSWER }

    public String lastMrn = "", lastPatient = "", lastVisit = "", lastDialog = "", lastDepartment = "", lastStore = "", lastPayor = "", lastItemCode = "",
            lastItemName = "", lastSuggestion = "", lastQty = "", lastRow = "", lastSave = "", lastToast = "", lastSaleNo = "", lastSaveAnswer = "", lastReport = "",
            lastSaveDialog = "";
    /** How many confirmation dialogs were showing right after the search. */
    public int dialogsShown;
    public int lastResultStatus = -1;
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public boolean reportBlank = true;
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String MRN_BOX = "input[ng-model='pharmacysale.mrno']";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; POS &gt; Pharmacy Sale/Dispense. Only opens the groups that are shut; the menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 6 && !onScreen(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/pharmacysale$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__psLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const find=t=>[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t);"
                    + " const pos=find('POS'); if(pos){ pos.click(); return 'pos'; }"
                    + " const inv=find('Inventory'); if(inv){ inv.click(); return 'inventory'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                try { page.locator("#__psLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PharmacySale.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__psLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='pharmacysale.mrno']\") && !!document.getElementById('PSqty')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PharmacySale.nav: the MRN box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/pharmacysale/?$"); }

    // ---- low-level helpers -----------------------------------------------------------------------------------

    private boolean clickButton(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__psBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("PharmacySale: no visible button for " + handlerRe); return false; }
        try { page.locator("#__psBtn").scrollIntoViewIfNeeded(); page.locator("#__psBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("PharmacySale: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__psBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    /** An Angular expression evaluated in the scope of the element matching {@code css}. */
    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }",
                Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String sel(String ng) { return "select[ng-model='" + ng + "']"; }

    /** Choose an option of a (select2-enhanced) select: set it, fire change and sync select2. Exact text first, then {@code fallbackRe}, then (if allowed) the first one. */
    private String choose(String scopeCss, String ng, String want, String fallbackRe, boolean anyIfMissing) {
        String css = scopeCss + sel(ng);
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", css, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add(ng + " has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.f) p=o.find(x=>new RegExp(a.f,'i').test(x.t));"
                + " if(!p && a.any) p=o[0]; if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }", Map.of("s", css, "w", want == null ? "" : want, "f", fallbackRe == null ? "" : fallbackRe, "any", anyIfMissing));
        String chosen = t == null ? "" : t.toString();
        if (chosen.isEmpty()) { problems.add(ng + " has no option" + (want == null ? "" : " \"" + want + "\"")); return ""; }
        waitForAngular(1000);
        if (model(css, ng).isEmpty() || "0".equals(model(css, ng))) problems.add(ng + " is not held by the model");
        return chosen;
    }

    public void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    private String toastsNow() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('#toast-container .toast')].map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- confirmation dialogs (ng-confirm) -----------------------------------------------------------------------------

    /** The text of every confirmation dialog on screen, bottom one first. */
    public List<String> confirms() {
        List<String> out = new ArrayList<>();
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ng-confirm')].filter(vis).map(d=>norm((d.querySelector('.ng-confirm-content-pane')||d).textContent)); }");
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click a button ({@code labelRe}) of the TOPMOST confirmation dialog (the one in front; a stacked one underneath cannot be clicked). Returns its text, "" when none. */
    private String clickConfirmButton(String labelRe) {
        Object t = page.evaluate("(re) => {" + JS
                + " const boxes=[...document.querySelectorAll('.ng-confirm')].filter(vis);"
                + " const hit=boxes.map(d=>({d:d,b:[...d.querySelectorAll('button')].find(b=>new RegExp(re,'i').test(norm(b.textContent)))})).filter(x=>x.b);"
                + " if(!hit.length) return ''; const x=hit[hit.length-1]; x.b.id='__psConfirm';"
                + " return norm((x.d.querySelector('.ng-confirm-content-pane')||x.d).textContent); }", labelRe);
        String text = t == null ? "" : t.toString();
        if (text.isEmpty()) return "";
        try { page.locator("#__psConfirm").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("PharmacySale: the confirmation button could not be clicked - " + e.getMessage().split("\n")[0]); text = ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__psConfirm'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(900);
        return text;
    }

    /** Click <b>Yes</b> on the topmost confirmation dialog. Returns the dialog's text ("" when none was showing). */
    public String clickYes() { return clickConfirmButton("^\\s*yes\\s*$"); }

    /** Dismiss every confirmation on screen with its No / Ok / Close button (used for a candidate patient that is not wanted). */
    public void dismissConfirms() {
        for (int i = 0; i < 5 && !confirms().isEmpty(); i++) {
            if (clickConfirmButton("^\\s*(no|ok|close|cancel)\\s*$").isEmpty()) break;
        }
    }

    // ---- patient search ------------------------------------------------------------------------------------------------

    /** MRNs of registered patients from the Patient Search popup beside the MRN box (nothing is hardcoded). */
    public List<String> candidateMrns(int target, int maxPages) {
        List<String> mrns = new ArrayList<>();
        // the first click right after the screen loads can be swallowed before its controller is ready: try again once
        for (int attempt = 0; attempt < 2 && mrns.isEmpty(); attempt++) {
            if (attempt > 0) waitForAngular(2500);
            // the popup lists only patients who visited TODAY by default, and those have no "visit not marked" dialog — untick Visit Date
            mrns = new PatientSearchPopup(page).collectMrns("OpenPatientSearchPopupScreen", target, maxPages, true);
        }
        return mrns;
    }

    /**
     * Type the MRN (a real fill) and click <b>Search</b>; wait for the patient lookup to answer, let the screen settle, then say what happened.
     * Sets {@link #dialogsShown} and {@link #lastDialog} when a confirmation appeared.
     */
    public Outcome searchMrn(String mrn) {
        problems.clear();
        lastMrn = mrn; lastDialog = ""; dialogsShown = 0;
        clearToasts();
        try {
            Locator box = page.locator(MRN_BOX).first();
            box.scrollIntoViewIfNeeded();
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(mrn);
        } catch (Exception e) { problems.add("the MRN could not be typed"); return Outcome.NO_ANSWER; }
        try {
            page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(15000),
                    () -> clickButton("SearchPatientByMRNo\\(\\)", null));
        } catch (Exception e) { problems.add("the search gave no answer"); return Outcome.NO_ANSWER; }
        waitForAngular(1500);
        List<String> c = confirms();
        dialogsShown = c.size();
        if (!c.isEmpty()) {
            lastDialog = c.get(c.size() - 1);
            return lastDialog.toLowerCase().contains("mark") && lastDialog.toLowerCase().contains("visit") ? Outcome.MARK_VISIT_CONFIRM : Outcome.OTHER_CONFIRM;
        }
        String toast = toastsNow();
        if (toast.toLowerCase().contains("not found")) { lastDialog = toast; return Outcome.NOT_FOUND; }
        lastPatient = patientName();
        return !lastPatient.isEmpty() && mrn.equals(model(MRN_BOX, "pharmacysale.mrno")) ? Outcome.LOADED : Outcome.NO_ANSWER;
    }

    public String patientName() {
        String f = model(MRN_BOX, "pharmacysale.firstname"), l = model(MRN_BOX, "pharmacysale.lastname");
        return (f + " " + l).trim();
    }

    // ---- the Mark Visit modal ------------------------------------------------------------------------------------------

    public boolean markVisitOpen() {
        Object r = page.evaluate("() => { const m=document.getElementById('VisitMarkModal'); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }");
        return Boolean.TRUE.equals(r);
    }

    /** Wait for the Mark Visit modal to open. */
    public boolean awaitMarkVisit(int ms) {
        try {
            page.waitForFunction("() => { const m=document.getElementById('VisitMarkModal'); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(ms));
            waitForAngular(700);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Select the Department in the Mark Visit modal (the exact name, else the first department). */
    public String selectDepartment(String want) {
        problems.clear();
        lastDepartment = choose("#VisitMarkModal ", "pharmacysale.DepartmentID", want, null, true);
        return lastDepartment;
    }

    /** Click <b>Mark Visit</b>, wait for the modal to close and the patient's visit to load. Returns "" when it did, else what went wrong. */
    public String clickMarkVisit() {
        problems.clear();
        if (!clickButton("fnMarkVisit", "mark visit")) return "the Mark Visit button was not found";
        try {
            page.waitForFunction("() => { const m=document.getElementById('VisitMarkModal'); return !m || !m.classList.contains('in'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return "the Mark Visit dialog did not close (" + toastsNow() + ")"; }
        waitForAngular(2500);
        List<String> c = confirms();
        if (!c.isEmpty()) return "a confirmation is on screen after Mark Visit: " + c.get(c.size() - 1);
        lastPatient = patientName();
        lastVisit = model(MRN_BOX, "pharmacysale.visitid");
        String visitText = String.valueOf(page.evaluate("() => { const s=document.querySelector(\"select[ng-model='pharmacysale.visitid']\"); const o=s&&s.options[s.selectedIndex]; return o? (o.textContent||'').replace(/\\s+/g,' ').trim() : ''; }"));
        if (lastPatient.isEmpty()) return "the patient did not load after Mark Visit";
        if (lastVisit.isEmpty() || "0".equals(lastVisit)) return "no visit is selected after Mark Visit";
        lastVisit = visitText.isEmpty() ? lastVisit : visitText.replaceAll("\\s*-\\s*$", "");
        return "";
    }

    // ---- store / payor ----------------------------------------------------------------------------------------------

    /** Select the Store: the exact name, else the first with "pharmacy" in it. Store is mandatory and the item search needs it. */
    public String selectStore(String want) {
        problems.clear();
        lastStore = choose("", "pharmacysale.storeid", want, "pharmacy", false);
        waitForAngular(800);
        return lastStore;
    }

    /** If the Payor is still blank (it is only auto-filled on the mark-visit path) pick its first option. Returns the payor shown. */
    public String ensurePayor() {
        problems.clear();
        boolean blank = model(MRN_BOX, "pharmacysale.receivableid").isEmpty() || "0".equals(model(MRN_BOX, "pharmacysale.receivableid"));
        if (blank) lastPayor = choose("", "pharmacysale.receivableid", null, null, true);
        else lastPayor = String.valueOf(page.evaluate("() => { const s=document.querySelector(\"select[ng-model='pharmacysale.receivableid']\"); const o=s&&s.options[s.selectedIndex]; return o? (o.textContent||'').replace(/\\s+/g,' ').trim() : ''; }"));
        payorWasBlank = blank;
        return lastPayor;
    }
    public boolean payorWasBlank;

    // ---- item search + quantity ------------------------------------------------------------------------------------------

    /**
     * Type {@code term} in the item <b>Name</b> (real key presses, so the smart search fires), wait for its suggestions and click the first one that
     * contains the term. Returns the suggestion clicked ("" when none appeared / matched).
     */
    public String searchAndPickItem(String term) {
        problems.clear();
        Locator in = page.locator("#txtServiceName");
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("");
            in.pressSequentially(term, new Locator.PressSequentiallyOptions().setDelay(60));
        } catch (Exception e) { problems.add("the item name could not be typed"); return ""; }
        try {
            page.waitForFunction("() => document.querySelectorAll('li.auto-complete-item').length>0", null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { problems.add("no suggestion appeared for \"" + term + "\" (is a Store selected?)"); return ""; }
        waitForAngular(600);
        Object s = page.evaluate("(t) => {" + JS
                + " const lis=[...document.querySelectorAll('li.auto-complete-item')].filter(vis);"
                + " const li=lis.find(l=>norm(l.textContent).toLowerCase().indexOf(t.toLowerCase())>=0); if(!li) return '';"
                + " li.id='__psSugg'; return norm(li.textContent); }", term);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + term + "\""); return ""; }
        try { page.locator("#__psSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__psSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastItemCode = String.valueOf(page.evaluate("() => { const e=document.getElementById('txtItemCode'); return e? e.value : ''; }"));
        lastItemName = String.valueOf(page.evaluate("() => { const e=document.getElementById('txtServiceName'); return e? e.value : ''; }"));
        if (lastItemCode.isEmpty()) problems.add("choosing the suggestion did not fill the item code");
        return lastSuggestion;
    }

    private int gridRows() {
        Object n = page.evaluate("() => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); return (s.gridOptionsadd.data||[]).length; }catch(e){ return -1; } }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** Type the quantity and press Enter — that adds the line to the grid. Returns the grid's row count. */
    public int enterQuantityAndEnter(String qty) {
        problems.clear();
        int before = Math.max(0, gridRows());
        clearToasts();
        try {
            Locator l = page.locator("#PSqty");
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(qty);
            lastQty = model("#PSqty", "pharmacysale.quantity");
            l.press("Enter");
        } catch (Exception e) { problems.add("the quantity could not be entered"); return before; }
        try {
            page.waitForFunction("(n) => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); return (s.gridOptionsadd.data||[]).length>n; }catch(e){ return false; } }",
                    before, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("Enter in Quantity did not add a line to the grid" + (toastsNow().isEmpty() ? "" : " (the screen said: " + toastsNow() + ")")); }
        waitForAngular(800);
        lastRow = rowSummary();
        return gridRows();
    }

    /** The first grid line as the grid data holds it. */
    public String rowSummary() {
        Object r = page.evaluate("() => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); const d=s.gridOptionsadd.data[0]; if(!d) return '';"
                + " return d.itemcode+' '+d.itemname+' | qty '+d.quantity+' | batch '+(d.batchcode||'-')+' exp '+(d.expirydate||'-')+' | rate '+d.rate+' | total '+d.totalamount+' (net '+d.netamount+')'; }catch(e){ return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** True when the first grid line carries this quantity and a code and name, and the strip was cleared. */
    public boolean rowHolds(String qty) {
        Object r = page.evaluate("(q) => { try{ const s=angular.element(document.querySelector('#PSqty')).scope(); const d=s.gridOptionsadd.data[0]; if(!d) return false;"
                + " return !!d.itemcode && !!d.itemname && String(d.quantity)===q && document.getElementById('txtServiceName').value===''; }catch(e){ return false; } }", qty);
        return Boolean.TRUE.equals(r);
    }

    // ---- Save -> confirmation -> report + toast --------------------------------------------------------------------------

    /** Click <b>Save</b> ({@code IUDSave()}); returns the confirmation dialog's text ("" when none appeared — e.g. a validation toast instead). */
    public String clickSave() {
        clearToasts();
        lastSaveDialog = "";
        if (!clickButton("IUDSave", "^\\s*save\\s*$")) return "";
        for (int i = 0; i < 16; i++) {
            List<String> c = confirms();
            if (!c.isEmpty()) { lastSaveDialog = c.get(c.size() - 1); return lastSaveDialog; }
            if (!toastsNow().isEmpty()) break;
            page.waitForTimeout(500);
        }
        return "";
    }

    public String toastAfterSaveClick() { return toastsNow(); }

    /** Records every toast to sessionStorage (it survives a reload): the screen can navigate right after a save, destroying any in-page record. */
    private static final String TOAST_RECORDER =
            "(function(){ try{ const KEY='__psToasts';"
          + " const get=()=>{ try{ return JSON.parse(sessionStorage.getItem(KEY)||'[]'); }catch(e){ return []; } };"
          + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
          + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(!t) return;"
          + "   const a=get(); if(!a.includes(t)){ a.push(t); sessionStorage.setItem(KEY, JSON.stringify(a)); } }); };"
          + " const start=()=>{ if(window.__psObs) return; window.__psObs=new MutationObserver(grab); window.__psObs.observe(document.documentElement,{childList:true,subtree:true}); grab(); };"
          + " if(document.documentElement) start(); else document.addEventListener('DOMContentLoaded', start); }catch(e){} })();";

    private List<String> toastsSeen() {
        try {
            Object o = page.evaluate("() => { try{ return JSON.parse(sessionStorage.getItem('__psToasts')||'[]'); }catch(e){ return []; } }");
            List<String> l = new ArrayList<>();
            if (o instanceof List) for (Object x : (List<?>) o) l.add(String.valueOf(x));
            return l;
        } catch (Exception e) { return null; }
    }

    private static boolean isAnswer(String t) {
        return t.toLowerCase().matches(".*(succe|done|added|saved|error|fail|please|invalid|required|already|exist).*");
    }

    /**
     * Click <b>Yes</b> in the save confirmation. Captures the toast, the {@code pharmacysale/IUD} answer (a failure reports the server's own reason) and the
     * report window the app opens, judged like any DevHIS report ({@link PdfReport}). Returns the toast.
     */
    public String confirmSaveAndCapture() {
        lastToast = ""; lastSaleNo = ""; lastSaveAnswer = ""; lastReport = ""; reportBlank = true; reportPng = null; lastResultStatus = -1;
        final java.util.List<com.microsoft.playwright.Response> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> watcher = r -> {
            try { if (r.url().toLowerCase().contains("pharmacysale/iud")) answers.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(watcher);
        page.evaluate("() => { try{ sessionStorage.removeItem('__psToasts'); }catch(e){} try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        page.addInitScript(TOAST_RECORDER);
        page.evaluate(TOAST_RECORDER);
        int tabsBefore = page.context().pages().size();
        List<String> seen = new ArrayList<>();
        try {
            String yes = clickYes();
            if (yes.isEmpty()) System.out.println("PharmacySale: no confirmation Yes was found");
            for (int i = 0; i < 40; i++) {
                List<String> t = toastsSeen();
                if (t != null) { seen = t; if (seen.stream().anyMatch(PharmacySale::isAnswer)) break; }
                page.waitForTimeout(500);
            }
            lastSave = "clicked Yes in the confirmation";
            // the report opens in a new window: wait until it carries the report's own address, then judge it
            long end = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < end) {
                boolean found = false;
                for (Page pg : new ArrayList<>(page.context().pages())) {
                    if (pg == page) continue;
                    try { if (pg.url().toLowerCase().contains("report")) found = true; } catch (Exception ignore) { }
                }
                if (found) break;
                page.waitForTimeout(500);
            }
            PdfReport.Result rep = PdfReport.capture(page, tabsBefore, 20000);
            reportBlank = rep.blank; lastReport = rep.diagnostics; reportPng = rep.png;
            try { waitForAngular(800); } catch (Exception ignore) { }
        } finally {
            page.offResponse(watcher);
        }
        for (int i = 0; i < 6; i++) { List<String> t = toastsSeen(); if (t != null) { seen = t; break; } page.waitForTimeout(500); }
        String pick = seen.stream().filter(x -> x.toLowerCase().matches(".*(succe|done|added|saved).*")).findFirst()
                .orElse(seen.stream().filter(PharmacySale::isAnswer).findFirst().orElse(seen.isEmpty() ? "" : seen.get(0)));
        lastToast = pick.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        for (com.microsoft.playwright.Response a : answers) {
            String body = "";
            try { body = a.text(); } catch (Exception ignore) { }
            String shortBody = body.replaceAll("\\s+", " ");
            shortBody = shortBody.substring(0, Math.min(170, shortBody.length()));
            if (a.status() == 200) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) lastSaleNo = m.group(1);
                java.util.regex.Matcher st = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                if (st.find()) lastResultStatus = Integer.parseInt(st.group(1));
                lastSaveAnswer = "HTTP 200 " + shortBody;
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                lastSaveAnswer = "HTTP " + a.status() + " — " + (m.find() ? m.group(1) : shortBody);
            }
        }
        if (answers.isEmpty()) lastSaveAnswer = "no /api/pharmacysale/IUD call was made";
        // close the report window so the main page is the one in front again
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
            page.bringToFront();
        } catch (Exception ignore) { }
        System.out.println("PharmacySale: save -> toast \"" + lastToast + "\"; answer: " + lastSaveAnswer + "; report: " + lastReport);
        return lastToast;
    }

    /** The save answered with a success message (and not an error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|done|added|saved).*") && !t.matches(".*(error|fail|please|invalid|not\\s*saved|required|already).*");
    }

    /** The server confirmed the sale ({@code ResultStatus 1}). */
    public boolean savedOnServer() { return lastResultStatus == 1; }
}
