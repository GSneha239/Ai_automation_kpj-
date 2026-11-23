package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Billing &gt; <b>Bill Estimation-Service</b> - Page Object (list {@code #/BillEstimationList}, form {@code #/add-BillEstimation}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar Billing &gt; Bill Estimation-Service; list <b>Add</b> ({@code AddEstimationMaster()}, a link);
 * on the form the <b>Patient Search</b> popup beside MRN ({@code openPopupScreen();}): tick <b>Visit Date</b> ({@code PatientData.ChkVisitDate}), From date ({@code PatientData.VisitFromDate}, a 720kb picker:
 * previous-month arrow twice, day 6), <b>Search</b> ({@code SearchPatient(0);}), pick a patient ({@code SetSearchPatient(PR);}); <b>Last Name*</b> ({@code BillEstimation.lastName}); <b>Bill Template</b>
 * ({@code BillEstimation.billtemplateid}, select2) + <b>Add Bill Template</b> ({@code GetBillTemplateServices()}); <b>Submit</b> ({@code IUDBillEstimation()}) - toast "Bill Estimation Saved Successfully.".</p>
 */
public class BillEstimationService extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/billestimationlist";
    private static final String TOP = "Billing";

    public BillEstimationService(Page page) { super(page); }

    /** What the last action could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getMessage()).replace((char) 10, (char) 32).replace((char) 13, (char) 32);
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    // ---- navigation (the sidebar builds each level lazily: open the group ONCE and await it) ----------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /** Billing &gt; Bill Estimation-Service (the list). Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Billing')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (topState(TOP) != 1) clickTop(TOP);
                waitLink(15000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__beLink'; }", HREF);
                try { page.locator("#__beLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("BillEstimationService.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__beLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a,button')].some(b=>/AddEstimationMaster/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("BillEstimationService.nav: the list is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/billestimationlist/?$"); }

    public boolean onForm() { return page.url().toLowerCase().contains("add-billestimation"); }

    /** List: click <b>Add</b> ({@code AddEstimationMaster()}) and wait for the form. */
    public boolean clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('a,button')].find(x=>/AddEstimationMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__beAdd'; }");
        try { page.locator("#__beAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BillEstimationService.clickAdd: " + firstLine(e)); }
        page.evaluate("() => { const e=document.getElementById('__beAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => /add-BillEstimation/i.test(location.hash) && !!document.querySelector(\"[ng-model='BillEstimation.lastName']\")", null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("BillEstimationService.clickAdd: the form did not open"); }
        waitForAngular(1500);
        return onForm();
    }

    // ---- the form (steps as recorded) -----------------------------------------------------------------------------

    private String val(String ngModel) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"[ng-model='\"+n+\"']\"); return e ? e.value : ''; }", ngModel);
        return v == null ? "" : v.toString();
    }

    /** The MRN box of the form. */
    public String mrn() { return val("BillEstimation.MRNo"); }
    public String firstName() { return val("BillEstimation.firstName"); }
    public String lastName() { return val("BillEstimation.lastName"); }
    public String totalBillAmount() { return val("BillEstimation.totalbillamount"); }

    /** Open the <b>Patient Search</b> popup beside MRN ({@code openPopupScreen();}). */
    public boolean openPatientSearch() {
        try { page.locator("button[ng-click='openPopupScreen();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Patient Search button: " + firstLine(e)); return false; }
        try {
            page.waitForFunction("() => !!document.querySelector(\".modal.in button[ng-click='SearchPatient(0);']\")", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the Patient Search popup did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    /** The popup's <b>Visit Date</b> filter ({@code PatientData.ChkVisitDate}): make sure it is ticked (the recording unticked and re-ticked it). Returns true when it is ticked. */
    public boolean ensureVisitDateTicked() {
        Object on = page.evaluate("() => { const c=document.querySelector(\".modal.in input[ng-model='PatientData.ChkVisitDate']\"); return !!c && c.checked; }");
        if (!Boolean.TRUE.equals(on)) {
            try { page.locator(".modal.in input[ng-model='PatientData.ChkVisitDate']").first().click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("Visit Date tick: " + firstLine(e)); }
            waitForAngular(400);
        }
        Object r = page.evaluate("() => { const c=document.querySelector(\".modal.in input[ng-model='PatientData.ChkVisitDate']\"); return !!c && c.checked; }");
        return Boolean.TRUE.equals(r);
    }

    /** The popup's Visit Date <b>From</b>: previous-month arrow {@code monthsBack} times, then day {@code day}. Returns the From box's value. */
    public String pickVisitFrom(int monthsBack, int day) {
        try {
            page.locator(".modal.in input[ng-model='PatientData.VisitFromDate']").first().click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForTimeout(500);
            for (int i = 0; i < monthsBack; i++) {
                page.locator("a[ng-click='prevMonth()']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(300);
            }
            page.locator("a[ng-click='setDatepickerDay(item)']:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^" + day + "$")))
                    .first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Visit From date: " + firstLine(e)); }
        waitForAngular(500);
        Object r = page.evaluate("() => { const e=document.querySelector(\".modal.in input[ng-model='PatientData.VisitFromDate']\"); return e ? e.value : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the popup's <b>Search</b> ({@code SearchPatient(0);}) and wait for the patient rows; returns the number of rows. */
    public int popupSearch() {
        try {
            page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator(".modal.in button[ng-click='SearchPatient(0);']").first().click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Patient search: " + firstLine(e)); }
        waitForAngular(1200);
        return popupRows().size();
    }

    /** The popup's patient rows (text), in list order. */
    public List<String> popupRows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\".modal.in button[ng-click='SetSearchPatient(PR);']\")].map(b=>norm((b.closest('tr')||{}).textContent).slice(0,160)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Pick the {@code idx}-th patient of the popup ({@code SetSearchPatient(PR);}) and wait until the form shows its MRN; returns the MRN ("" when it did not fill). */
    public String pickPatient(int idx) {
        try { page.locator(".modal.in button[ng-click='SetSearchPatient(PR);']").nth(idx).click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Patient pick: " + firstLine(e)); return ""; }
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"[ng-model='BillEstimation.MRNo']\"); return !!e && e.value.trim().length>0 && !document.querySelector('.modal.in'); }", null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return mrn();
    }

    /** Type {@code text} in a form box ({@code ngModel}) and read it back. */
    public String fillBox(String ngModel, String text) {
        String sel = "[ng-model='" + ngModel + "']";
        try {
            Locator box = page.locator(sel + ":visible").first();
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(text, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
        } catch (Exception e) { problems.add(ngModel + ": " + firstLine(e)); return ""; }
        waitForAngular(400);
        return val(ngModel);
    }

    /** Bill Template (select2): returns "" when shown and held by the model, else the reason. */
    public String pickTemplate(String name) { return Select2Pick.pick(page, "BillEstimation.billtemplateid", name); }

    /** The distinct Bill Template names offered. */
    public List<String> templateNames() { return Select2Pick.names(page, "BillEstimation.billtemplateid"); }

    /** Click <b>Add Bill Template</b> ({@code GetBillTemplateServices()}) and wait for the services to be added (Total Bill Amount changes); returns the Total Bill Amount afterwards. */
    public String clickAddTemplate() {
        String before = totalBillAmount();
        try { page.locator("button[ng-click='GetBillTemplateServices()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Add Bill Template: " + firstLine(e)); return totalBillAmount(); }
        for (int i = 0; i < 30; i++) { page.waitForTimeout(500); if (!totalBillAmount().equals(before)) break; }
        waitForAngular(800);
        return totalBillAmount();
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    private void armToasts() {
        page.evaluate("() => { window.__beT=[]; if(window.__beObs) window.__beObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__beT.includes(t)) window.__beT.push(t); });"
                + " window.__beObs=new MutationObserver(grab); window.__beObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Submit</b> ({@code IUDBillEstimation()}), accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSubmit() {
        clearToasts();
        armToasts();
        try { page.locator("button[ng-click='IUDBillEstimation()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Submit: " + firstLine(e)); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__beT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
    }

    /** Form: click <b>Back</b> ({@code fnBack()}) to return to the list (nothing is typed in the address bar). */
    public boolean clickBack() {
        try { page.locator("button[ng-click='fnBack()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Back: " + firstLine(e)); }
        for (int i = 0; i < 30 && !onList(); i++) page.waitForTimeout(500);
        waitForAngular(1500);
        return onList();
    }

    /** List: click <b>Search</b> ({@code fngetBillEstimationMasterGrid()}) with the default dates (today) and return the grid's rows as {MRN, patient, bill date, OPD/IPD, deposit, total bill amount}. */
    public List<String[]> searchList() {
        try { page.locator("button[ng-click='fngetBillEstimationMasterGrid()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("List Search: " + firstLine(e)); }
        page.waitForTimeout(3500);
        waitForAngular(800);
        return listRows();
    }

    /** The list grid's rows (data array). */
    public List<String[]> listRows() {
        Object r = page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!sc && s && s.gridOptions && s.gridOptions.data) sc=s; }catch(e){} });"
                + " if(!sc) return []; const s=v=>v==null?'':String(v).trim(); return sc.gridOptions.data.map(x=>[s(x.mrno), s(x.patientname), s(x.billdate), s(x.opdipd), s(x.TotaldepositAmount), s(x.totalbillamount)]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[6]; for (int i = 0; i < 6; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    /** Diagnostic: every visible control with its model / click handler / label / options. */
    public String dumpControls() {
        Object r = page.evaluate("() => {" + JS + " const lab=e=>{ try{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); } const g=e.closest('.form-group,td,li'); const l=g&&g.querySelector('label'); return l?norm(l.textContent).slice(0,40):''; }catch(x){return '';} };"
                + " const f=[...document.querySelectorAll('input,select,textarea,button,a[ng-click]')].filter(e=>vis(e) && !e.closest('.sidebar,.main-sidebar,.sidebar-menu') && !/setDatepickerDay|setNewYear|paginateYears|prevMonth|nextMonth|showYearsPagination/.test(e.getAttribute('ng-click')||''))"
                + ".map(e=>e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+' ngm='+(e.getAttribute('ng-model')||'')+' ngc='+(e.getAttribute('ng-click')||'')+' id='+(e.id||'')+' label='+lab(e)+(e.tagName==='SELECT'?' opts='+e.options.length:'')+(e.tagName==='BUTTON'||e.tagName==='A'?' text='+norm(e.textContent).slice(0,30):'')+(e.disabled?' [disabled]':'')+(e.value&&e.tagName==='INPUT'&&e.type==='text'?' val='+e.value.slice(0,25):''));"
                + " return f.join('\\n'); }");
        return r == null ? "" : r.toString();
    }
}
