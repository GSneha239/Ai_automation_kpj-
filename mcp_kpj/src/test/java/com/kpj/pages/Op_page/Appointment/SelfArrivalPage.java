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

    /** Click Mark Arrival and return the toast it shows ("" when none appeared). */
    public String clickMarkArrival() {
        toastPng = null;
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
