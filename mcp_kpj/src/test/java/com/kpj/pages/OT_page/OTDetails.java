package com.kpj.pages.OT_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OT &gt; <b>OT Details</b> — Page Object ({@code #/OTDetailsList} → New → {@code #/add-OTDetails}).
 *
 * <p>Confirmed live (devhis build v2026.10.21.266). <b>New</b> ({@code OTDetails()}) opens a form headed by the patient
 * (<b>MRN</b>, searched with {@code SearchPatientByMRNo2()}), followed by the <b>OT Sheet Details</b>: Date*, From Time*,
 * To Time*, Total Minutes, <b>OT Theatre*</b>, <b>OT Result*</b>, <b>OT Status</b>, <b>Procedure Type*</b>, <b>Anesthesia
 * Type*</b> and the optional timings and notes. <b>Save</b> is {@code fnIUDOTSchedules()} (the same handler OT Schedule uses)
 * and answers "OT Detail Saved Successfully.", returning to the list.</p>
 *
 * <p><b>The search pre-fills the OT sheet from the patient's OT schedule for TODAY</b> — the Date, From/To Time, Total
 * Minutes and OT Theatre ({@code GetSchedulefromScheduleList}). For a patient with no schedule today those stay empty, and
 * the times cannot be filled reliably by hand (they are a custom time-picker), so the patient must be one with an OT
 * schedule today. The schedule is NOT consumed: the same patient can be searched and saved again (a second save was
 * accepted).</p>
 *
 * <p>The four dropdowns are select2-enhanced, so a choice is made on the underlying select with a change event and the
 * select2 display is synced; each choice is read back from the Angular model.</p>
 */
public class OTDetails extends BasePage {

    public OTDetails(Page page) { super(page); }

    public String lastRoute = "", lastPatient = "", lastTheatre = "", lastDate = "", lastFrom = "", lastTo = "", lastMinutes = "",
            lastResult = "", lastStatus = "", lastProcedureType = "", lastAnesthesia = "", lastSave = "", lastToast = "";
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** OT &gt; OT Details. The nav menu is built seconds after login, so its OT entry is awaited first. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>x.textContent.trim()==='OT')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 4 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/otdetailslist$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__odLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const ot=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='OT');"
                    + " if(ot){ ot.click(); return 'opened'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__odLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("OTDetails.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__odLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/OTDetails\\(\\)/.test(b.getAttribute('ng-click')||'') && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("OTDetails.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/otdetailslist/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-otdetails"); }

    /** Click <b>New</b> ({@code OTDetails()}) and wait for the form (MRN box). */
    public boolean clickNew() {
        if (!clickByHandler("OTDetails\\(\\)", null)) return false;
        try {
            page.waitForFunction("() => /add-otdetails/i.test(location.hash) && document.querySelector(\"input[ng-model='otdetails.MRNo']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm();
    }

    // ---- low-level helpers -----------------------------------------------------------------------------------

    private boolean clickByHandler(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__odBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("OTDetails: no visible button for " + handlerRe); return false; }
        try { page.locator("#__odBtn").scrollIntoViewIfNeeded(); page.locator("#__odBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("OTDetails: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__odBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    /** An Angular expression evaluated in the scope of the element matching {@code css}. */
    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }",
                Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String sheetSel(String field) { return "select[ng-model='otsheetdetails." + field + "']"; }

    /** An OT-sheet model value (e.g. {@code fromtime}). */
    private String sheet(String field) { return model(sheetSel("otresultid"), "otsheetdetails." + field); }

    /**
     * Choose an option of a (select2-enhanced) OT-sheet select: set the underlying select, fire its change event and sync the
     * select2 display. {@code want} matches the option text exactly (case-insensitive); else {@code fallbackRe}; else the first
     * real option. Returns the option text; the model is checked afterwards.
     */
    private String choose(String field, String want, String fallbackRe) {
        String s = sheetSel(field);
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", s, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add(field + " has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.re) p=o.find(x=>new RegExp(a.re,'i').test(x.t));"
                + " if(!p) p=o[0]; if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }", Map.of("s", s, "w", want == null ? "" : want, "re", fallbackRe == null ? "" : fallbackRe));
        String chosen = t == null ? "" : t.toString();
        if (chosen.isEmpty()) { problems.add(field + " has no selectable option"); return ""; }
        waitForAngular(700);
        if (sheet(field).isEmpty() || "0".equals(sheet(field))) problems.add(field + " is not held by the model");
        return chosen;
    }

    private void scrollToCss(String css) {
        page.evaluate("(c) => { const e=document.querySelector(c); if(e) e.scrollIntoView({block:'center'}); }", css);
        page.waitForTimeout(250);
    }

    // ---- patient -------------------------------------------------------------------------------------------------

    /**
     * Enter an MRN and click search. Returns true when the screen loaded THAT patient (a stale previous patient must not be
     * taken for it); {@link #sheetPrefilled()} then says whether the OT sheet came with the patient's schedule.
     */
    public boolean searchPatient(String mrn) {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        try {
            Locator in = page.locator("input[ng-model='otdetails.MRNo']").first();
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(mrn);
        } catch (Exception e) { System.out.println("OTDetails: the MRN could not be typed - " + e.getMessage()); return false; }
        if (!clickByHandler("SearchPatientByMRNo2", null)) return false;
        try {
            page.waitForFunction("(m) => { const e=document.querySelector(\"input[ng-model='otdetails.MRNo']\"); if(!e) return false;"
                    + " try{ const o=angular.element(e).scope().otdetails; return !!o && String(o.MRNo).trim()===m && Number(o.visitadmissionid)>0 && String(o.PatientFullDtl||'').indexOf(m)>=0; }catch(x){ return false; } }",
                    mrn, new Page.WaitForFunctionOptions().setTimeout(9000));
        } catch (Exception e) { return false; }
        waitForAngular(800);
        lastPatient = model("input[ng-model='otdetails.MRNo']", "otdetails.PatientFullDtl").replaceAll("\\s+", " ");
        return true;
    }

    /**
     * True when the search filled the OT sheet from an OT schedule: an OT Theatre, From Time and To Time are set. The
     * schedule arrives with a second answer after the patient, so it is waited for.
     */
    public boolean sheetPrefilled() {
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='otsheetdetails.otresultid']\"); if(!e) return false;"
                    + " try{ const s=angular.element(e).scope().otsheetdetails; return !!s && Number(s.ottheatreid)>0 && !!s.fromtime && !!s.totime; }catch(x){ return false; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { return false; }
        lastTheatre = selectedText("ottheatreid");
        lastDate = sheet("date"); lastFrom = sheet("fromtime"); lastTo = sheet("totime"); lastMinutes = sheet("totalhours");
        return true;
    }

    private String selectedText(String field) {
        Object t = page.evaluate("(s) => { const e=document.querySelector(s); return e? ((e.options[e.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : ''; }", sheetSel(field));
        return t == null ? "" : t.toString();
    }

    // ---- OT sheet details -------------------------------------------------------------------------------------------

    public String selectOtResult(String want) { problems.clear(); scrollToCss(sheetSel("otresultid")); lastResult = choose("otresultid", want, "^suc+es+ful+$"); return lastResult; }
    public String selectOtStatus(String want) { problems.clear(); scrollToCss(sheetSel("otstatusid")); lastStatus = choose("otstatusid", want, "^completed$|^successful$"); return lastStatus; }
    public String selectProcedureType(String want) { problems.clear(); scrollToCss(sheetSel("proceduretypeid")); lastProcedureType = choose("proceduretypeid", want, null); return lastProcedureType; }
    public String selectAnesthesiaType(String want) { problems.clear(); scrollToCss(sheetSel("anesthesiatypeid")); lastAnesthesia = choose("anesthesiatypeid", want, "anesthesia$"); return lastAnesthesia; }

    // ---- Save ---------------------------------------------------------------------------------------------------

    /**
     * Click <b>Save</b> ({@code fnIUDOTSchedules()}), answer a confirmation dialog if one appears, and return the toast.
     * Toasts are harvested with a MutationObserver (they fade), after clearing stale ones.
     */
    public String clickSaveAndGetToast() {
        page.evaluate("() => { window.__odToasts=[]; if(window.__odObs) window.__odObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__odToasts.includes(t)) window.__odToasts.push(t); }); };"
                + " window.__odObs=new MutationObserver(grab); window.__odObs.observe(document.body,{childList:true,subtree:true}); }");
        if (!clickByHandler("fnIUDOTSchedules", "^\\s*save\\s*$")) System.out.println("OTDetails: the Save button was not found");
        String confirm = "";
        for (int i = 0; i < 30; i++) {
            Object ans = page.evaluate("() => {" + JS
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.bootbox,.sweet-alert')].filter(vis)"
                    + "   .find(m=>/save|submit|confirm|sure|proceed/i.test(norm(m.textContent)));"
                    + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                    + "     .find(b=>/^\\s*(yes|ok|save|submit|confirm)\\s*$/i.test(norm(b.textContent)));"
                    + "   if(y){ const t=norm(box.textContent).slice(0,90); y.click(); return t; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); window.__odToasts=window.__odToasts||[];"
                    + "   if(t && !window.__odToasts.includes(t)) window.__odToasts.push(t); });"
                    + " return ''; }");
            if (ans != null && !ans.toString().isEmpty()) confirm = ans.toString();
            boolean got = Boolean.TRUE.equals(page.evaluate(
                    "() => (window.__odToasts||[]).some(a=>/succe|saved|created|added|updated|please|error|fail|invalid|required|mandatory|already|select|enter/i.test(a))"));
            if (got) break;
            page.waitForTimeout(500);
        }
        lastSave = "clicked Save [fnIUDOTSchedules]" + (confirm.isEmpty() ? " (no confirmation dialog appeared)"
                : ", then confirmed the dialog (\"" + confirm + "\")");
        waitForAngular(800);
        Object r = page.evaluate("() => { const a=window.__odToasts||[];"
                + " return a.find(x=>/succe|saved|created|added|updated/i.test(x))"
                + "   || a.find(x=>/please|error|fail|invalid|required|mandatory|already|select|enter/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        System.out.println("OTDetails: save -> " + lastSave + "; toast: \"" + lastToast + "\"");
        return lastToast;
    }

    /** The save answered with a success message (and not a validation/error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|saved|created|added|updated).*")
                && !t.matches(".*(please|error|fail|invalid|not\\s*saved|mandatory|required|already).*");
    }
}
