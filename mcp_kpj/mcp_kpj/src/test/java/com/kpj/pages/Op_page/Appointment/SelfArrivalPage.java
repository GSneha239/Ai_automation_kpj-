package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.kpj.pages.PatientSearchPopup;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * OP &gt; <b>Self Arrival</b> (#/SelfArrival): enter the PR No (= MRN), click <b>Search</b> ({@code fnSearchAppointments()}), the patient's
 * appointments are listed in the table, then <b>Mark Arrival</b> ({@code fnUpdateMarkArrival()}).
 *
 * <p>Seen live (v2026.11.12.446): the sidebar's OP submenu loads lazily (click OP, then its sub-headers); the screen lists a patient's
 * appointments only when the patient HAS one that is due (a patient without one gives an empty table and no message).</p>
 */
public class SelfArrivalPage extends BasePage {

    public String lastMrn = "";
    /** Screenshot taken while the Mark Arrival toast is on screen (null when none appeared). */
    public byte[] toastPng;

    public SelfArrivalPage(Page page) { super(page); }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("selfarrival")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"[ng-model='PatientData.MRNO']\")"));
    }

    /** OP &gt; Self Arrival through the sidebar (a typed deep link is bounced to the dashboard). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            // the sidebar is built some seconds after login
            try { page.waitForFunction("() => [...document.querySelectorAll('a')].some(x=>(x.textContent||'').trim()==='OP')",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
            page.waitForTimeout(4000);
            page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>(x.textContent||'').trim()==='OP'); if(op) op.click(); }");
            page.waitForTimeout(1500);
            page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>(x.textContent||'').trim()==='OP'); if(!op) return;"
                    + " [...op.closest('li').querySelectorAll('a')].filter(x=>x.getAttribute('href')==='#').forEach(x=>{ try{x.click();}catch(e){} }); }");
            page.waitForTimeout(1500);
            Object tagged = page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>(x.textContent||'').trim()==='OP'); if(!op) return false;"
                    + " const a=[...op.closest('li').querySelectorAll('a[href]')].find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/selfarrival');"
                    + " if(!a) return false; a.id='__saLink'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__saLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // the link can sit in a submenu that is not painted yet: click it through the page instead
                    System.out.println("SelfArrival.nav: pointer click failed, clicking through the page - " + String.valueOf(e.getMessage()).split("\n")[0]);
                    page.evaluate("() => { const e=document.getElementById('__saLink'); if(e) e.click(); }");
                }
                page.evaluate("() => { const e=document.getElementById('__saLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try { page.waitForSelector("[ng-model='PatientData.MRNO']", new Page.WaitForSelectorOptions().setTimeout(15000)); } catch (Exception ignore) { }
        return onScreen();
    }

    /** Open the Patient Search popup beside the PR No box and list up to {@code target} MRNs (patients seen on any day). */
    public List<String> candidateMrns(int target) {
        return new PatientSearchPopup(page).collectMrns("OpenPopupScreen", target, Math.max(1, (target + 9) / 10), true);
    }

    public void enterMrn(String mrn) {
        lastMrn = mrn;
        page.evaluate("(m) => { const e=document.querySelector(\"[ng-model='PatientData.MRNO']\"); const c=window.angular.element(e).controller('ngModel');"
                + " e.value=m; if(c){ c.$setViewValue(m); c.$render(); } e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", mrn);
        waitForAngular(300);
    }

    public String mrnBoxValue() {
        Object v = page.evaluate("() => { const e=document.querySelector(\"[ng-model='PatientData.MRNO']\"); return e ? e.value : ''; }");
        return v == null ? "" : v.toString();
    }

    /** Click Search and wait for the answer. */
    public void clickSearch() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnSearchAppointments();' && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(2500);
    }

    /** Rows the appointment table shows now. */
    public int rowCount() {
        Object n = page.evaluate("() => [...document.querySelectorAll('table tbody tr')].filter(x=>x.offsetParent!==null && (x.textContent||'').trim()).length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String rowsText() {
        Object r = page.evaluate("() => [...document.querySelectorAll('table tbody tr')].filter(x=>x.offsetParent!==null && (x.textContent||'').trim())"
                + ".slice(0,3).map(x=>x.textContent.replace(/\\s+/g,' ').trim().slice(0,220)).join(' || ')");
        return r == null ? "" : r.toString();
    }

    // ---- the appointments due today ------------------------------------------

    /** Rows of the appointment list proper ({@code ng-repeat="app in AppointmentList"}). */
    private static final String DUE_ROWS_JS = "[...document.querySelectorAll(\"tr[ng-repeat*='AppointmentList']\")].filter(x=>x.offsetParent!==null && (x.textContent||'').trim())";

    /** Empty the PR No / Name / Mobile No boxes so the next Search lists every appointment due today. */
    public void clearFilters() {
        page.evaluate("() => { for (const ng of ['PatientData.MRNO','PatientData.PatientName','PatientData.MobileNo']) { const e=document.querySelector(\"[ng-model='\"+ng+\"']\"); if(!e) continue;"
                + " const c=window.angular.element(e).controller('ngModel'); e.value=''; if(c){ c.$setViewValue(''); c.$render(); } e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(300);
    }

    /**
     * Confirmed live 2026-11-19 (build .488): Search with PR No / Name / Mobile all EMPTY lists every appointment due today (a PR No filters to that patient's rows; the Mobile No box does not filter).
     * Returns the number of rows listed (0 = nothing due today). Polls gently - an empty answer is a genuine "none due".
     */
    public int searchAllDue() {
        clearFilters();
        clickSearch();
        try {
            page.waitForFunction("() => " + DUE_ROWS_JS + ".length>0", null, new Page.WaitForFunctionOptions().setTimeout(8000).setPollingInterval(500));
        } catch (Exception ignore) { /* nothing due */ }
        return dueRows().size();
    }

    /** The listed appointments as {appointment id, MRN (blank for a walk-in), patient name, appointment no., date, from time, to time}. */
    public List<String[]> dueRows() {
        Object r = page.evaluate("() => " + DUE_ROWS_JS + ".map(tr=>[...tr.querySelectorAll('td')].slice(0,7).map(t=>(t.textContent||'').replace(/\\s+/g,' ').trim()))");
        List<String[]> out = new java.util.ArrayList<>();
        if (r instanceof List) {
            for (Object row : (List<?>) r) {
                if (!(row instanceof List)) continue;
                List<?> cells = (List<?>) row;
                String[] a = new String[7];
                for (int i = 0; i < 7; i++) a[i] = i < cells.size() && cells.get(i) != null ? cells.get(i).toString() : "";
                out.add(a);
            }
        }
        return out;
    }

    /** Distinct non-blank MRNs of the listed appointments, in list order. */
    public List<String> dueMrns() {
        List<String> m = new java.util.ArrayList<>();
        for (String[] row : dueRows()) if (!row[1].isEmpty() && !m.contains(row[1])) m.add(row[1]);
        return m;
    }

    /**
     * Click the {@code idx}-th listed appointment so the screen selects it. Mark Arrival acts on the SELECTED appointment ({@code fnUpdateMarkArrival}: {@code SelectedAppointment.id}) -
     * every rendered row selects itself ({@code ng-init="fnSelectAppointment(app,$index)"}), so with several rows listed the LAST one is selected unless another is clicked, and after
     * a search that lists nothing the selection is a stale one from an earlier search. Returns the selected row's text ("" if the row was not found).
     */
    public String selectRow(int idx) {
        Object r = page.evaluate("(i) => { const rows=" + DUE_ROWS_JS + "; const tr=rows[i]; if(!tr) return ''; tr.click(); return (tr.textContent||'').replace(/\\s+/g,' ').trim().slice(0,200); }", idx);
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Text of the row the screen has selected (the {@code activeRow} class), "" when none. */
    public String selectedRowText() {
        Object r = page.evaluate("() => { const tr=" + DUE_ROWS_JS + ".find(x=>x.classList.contains('activeRow')); return tr ? (tr.textContent||'').replace(/\\s+/g,' ').trim().slice(0,200) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The row Mark Arrival was clicked on (read just before the click). */
    public String lastMarkedRow = "";

    /** Click Mark Arrival and return the toast it shows ("" when none appeared). */
    public String clickMarkArrival() {
        toastPng = null;
        lastMarkedRow = selectedRowText();
        page.evaluate("() => { window.__saToasts=[]; if(window.__saObs) window.__saObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove());"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__saToasts.includes(t)) window.__saToasts.push(t); });"
                + " window.__saObs=new MutationObserver(grab); window.__saObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        Object tag = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnUpdateMarkArrival();' && x.offsetParent!==null); if(!b) return false; b.id='__saMark'; return true; }");
        if (!Boolean.TRUE.equals(tag)) return "";
        try { page.locator("#__saMark").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("SelfArrival.markArrival: " + String.valueOf(e.getMessage()).split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__saMark'); if(e) e.removeAttribute('id'); }");
        // a confirm dialog may sit in front of the save: accept it
        for (int i = 0; i < 6; i++) {
            page.waitForTimeout(700);
            Object t = page.evaluate("() => (window.__saToasts||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); } catch (Exception e) { toastPng = null; }
                return t.toString();
            }
            page.evaluate("() => { const b=[...document.querySelectorAll('.swal2-confirm,.sweet-alert .confirm,.modal.in .btn-primary,.modal.in button')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        Object t = page.evaluate("() => (window.__saToasts||[]).join(' | ')");
        return t == null ? "" : t.toString();
    }
}
