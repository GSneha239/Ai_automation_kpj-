package com.kpj.pages.Billing_page.DoctorShare_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Billing &gt; Doctor Share &gt; <b>Doctor Share</b> - Page Object ({@code #/Doctorshare}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar Billing &gt; Doctor Share &gt; Doctor Share; <b>From Date</b> ({@code DoctorShare.FromDate},
 * a 720kb date picker: previous-month arrow twice, day 6); <b>Search</b> ({@code GetDoctorShareList(DoctorShare)}); tick a row of the ui-grid ({@code row.entity.IsChecked}, {@code grid.appScope.Getcheck(row)}) and
 * select it; then the edit boxes under the grid: <b>Service Rate</b> ({@code DoctorShare.ServiceRateCal}, id {@code drServiceRate}) + <b>Update</b> ({@code UpdateServiceRate()}); <b>Doctor Share Amount</b>
 * ({@code DoctorShare.DoctorshrAmtCal}, id {@code drShareAmt}) + <b>Update</b> ({@code UpdateDoctorShareamt()}); <b>Department</b> ({@code DoctorShare.DepartmentIDCal}, select2) and <b>Doctor Name</b>
 * ({@code DoctorShare.DoctorIDCal}, select2) + <b>Update</b> ({@code UpdateDoctor()}); then <b>Save</b> ({@code IUDDoctorShare()}) - toast "Record Added Successfully.".</p>
 */
public class DoctorShare extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/doctorshare";
    private static final String PARENT = "Billing", GROUP = "Doctor Share";

    public DoctorShare(Page page) { super(page); }

    /** What the last action could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getMessage()).replace((char) 10, (char) 32).replace((char) 13, (char) 32);
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    // ---- navigation (the sidebar builds each level lazily: open each group ONCE and await it) -------------------

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

    private int innerState(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /** Billing &gt; Doctor Share &gt; Doctor Share. Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Billing')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(2000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__dsLink'; }", HREF);
                try { page.locator("#__dsLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DoctorShare.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__dsLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/GetDoctorShareList/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DoctorShare.nav: the screen is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/doctorshare/?$"); }

    // ---- the flow (steps as recorded) -------------------------------------------------------------------------------

    private String val(String ngModel) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"[ng-model='\"+n+\"']\"); return e ? e.value : ''; }", ngModel);
        return v == null ? "" : v.toString();
    }

    public String fromDate() { return val("DoctorShare.FromDate"); }
    public String toDate() { return val("DoctorShare.ToDate"); }

    /** From Date: open the date picker, go back {@code monthsBack} month(s) with the previous-month arrow and click day {@code day} - as the recording did. Returns the From Date box's value. */
    public String pickFromDate(int monthsBack, int day) {
        try {
            page.locator("[ng-model='DoctorShare.FromDate']").first().click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForTimeout(500);
            for (int i = 0; i < monthsBack; i++) {
                page.locator("a[ng-click='prevMonth()']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(300);
            }
            page.locator("a[ng-click='setDatepickerDay(item)']:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^" + day + "$")))
                    .first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("From Date: " + firstLine(e)); }
        waitForAngular(500);
        return fromDate();
    }

    /** Click <b>Search</b> ({@code GetDoctorShareList(DoctorShare)}) and wait for the list; returns the number of rows the grid holds. */
    public int clickSearch() {
        try { page.locator("button[ng-click='GetDoctorShareList(DoctorShare)']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Search: " + firstLine(e)); return 0; }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='row.entity.IsChecked']\").length>0", null, new Page.WaitForFunctionOptions().setTimeout(20000).setPollingInterval(500));
        } catch (Exception ignore) { /* none listed */ }
        waitForAngular(800);
        return gridRows().size();
    }

    private static final String GRID_SCOPE_JS =
            "let sc=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!sc && s && s.gridOptions && s.gridOptions.data && s.gridOptions.data.length) sc=s; }catch(e){} });";

    /** Every row the grid holds as {bill no., service, doctor name, service rate, hospital share, doctor share, checked, doctor id}. (A processed row keeps the doctor in DoctorID; its DoctorName column is blank.) */
    public List<String[]> gridRows() {
        Object r = page.evaluate("() => { " + GRID_SCOPE_JS + " if(!sc) return []; const s=v=>v==null?'':String(v).trim();"
                + " return sc.gridOptions.data.map(x=>[s(x.BillNo), s(x.ServiceName), s(x.DoctorName), s(x.ServiceRate), s(x.HospitalShare), s(x.DoctrShare), s(x.IsChecked), s(x.DoctorID)]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[8]; for (int i = 0; i < 8; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    /** How many rows the grid shows on its current page. */
    public int renderedRows() {
        Object r = page.evaluate("() => document.querySelectorAll(\"input[ng-model='row.entity.IsChecked']\").length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the {@code idx}-th row on the page and select it (the row header button), as the recording did; waits until the edit boxes show the row. Returns true when the row is ticked. */
    public boolean tickRow(int idx) {
        try {
            Locator cb = page.locator("input[ng-model='row.entity.IsChecked']").nth(idx);
            cb.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            cb.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(800);
            page.locator(".ui-grid-selection-row-header-buttons").nth(idx).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Row tick: " + firstLine(e)); return false; }
        waitForAngular(1500);
        Object r = page.evaluate("(i) => { const c=document.querySelectorAll(\"input[ng-model='row.entity.IsChecked']\")[i]; return !!c && c.checked; }", idx);
        return Boolean.TRUE.equals(r);
    }

    /** The totals of the ticked row ("the total available"): {Total Service Rate, Total Hospital Share, Total Doctor Share}. */
    public String[] totals() {
        return new String[]{val("DoctorShare.TotalSerRate"), val("DoctorShare.TotalHospShr"), val("DoctorShare.TotalDoctorShr")};
    }

    public String serviceRateBox() { return val("DoctorShare.ServiceRateCal"); }
    public String shareAmountBox() { return val("DoctorShare.DoctorshrAmtCal"); }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    /** Toasts on screen now (joined with " / "). */
    public String toastsNow() {
        Object r = page.evaluate("() => [...document.querySelectorAll('.toast-message')].map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).filter((v,i,a)=>a.indexOf(v)===i).join(' / ')");
        return r == null ? "" : r.toString();
    }

    private boolean typeInto(String id, String value) {
        try {
            Locator box = page.locator("#" + id);
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(value, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
            waitForAngular(400);
            return true;
        } catch (Exception e) { problems.add("Typing " + value + " into " + id + ": " + firstLine(e)); return false; }
    }

    private void clickButton(String ngClickPrefix) {
        clearToasts();
        try { page.locator("button[ng-click^='" + ngClickPrefix + "']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add(ngClickPrefix + ": " + firstLine(e)); }
        waitForAngular(1500);
    }

    /** Type {@code rate} in <b>Service Rate</b> and click its <b>Update</b> ({@code UpdateServiceRate()}). */
    public void updateServiceRate(String rate) { if (typeInto("drServiceRate", rate)) clickButton("UpdateServiceRate"); }

    /** Type {@code amount} in <b>Doctor Share Amount</b> and click its <b>Update</b> ({@code UpdateDoctorShareamt()}). */
    public void updateDoctorShareAmount(String amount) { if (typeInto("drShareAmt", amount)) clickButton("UpdateDoctorShareamt"); }

    /** Department names the select2 offers ("-Select-" left out). */
    public List<String> departmentNames() { return Select2Pick.names(page, "DoctorShare.DepartmentIDCal"); }

    /** Doctor names the select2 offers now (they load after a Department is chosen). */
    public List<String> doctorNames() { return Select2Pick.names(page, "DoctorShare.DoctorIDCal"); }

    /** Choose a Department; returns "" when it is shown and held by the model, else the reason. */
    public String pickDepartment(String name) { return Select2Pick.pick(page, "DoctorShare.DepartmentIDCal", name); }

    /** The id the screen's model holds for the chosen Doctor Name ("" when none). */
    public String doctorId() { return Select2Pick.model(page, "DoctorShare.DoctorIDCal"); }

    /** Choose a Doctor Name; returns "" when it is shown and held by the model, else the reason. */
    public String pickDoctor(String name) { return Select2Pick.pick(page, "DoctorShare.DoctorIDCal", name); }

    /** Click the Doctor Name's <b>Update</b> ({@code UpdateDoctor()}). */
    public void updateDoctor() { clickButton("UpdateDoctor"); }

    private void armToasts() {
        page.evaluate("() => { window.__dsT=[]; if(window.__dsObs) window.__dsObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dsT.includes(t)) window.__dsT.push(t); });"
                + " window.__dsObs=new MutationObserver(grab); window.__dsObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Save</b> ({@code IUDDoctorShare()}), accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSave() {
        clearToasts();
        armToasts();
        try { page.locator("button[ng-click='IUDDoctorShare()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Save: " + firstLine(e)); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__dsT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
    }

    /** Choose the Processed / UnProcessed / ALL radio ({@code DoctorShare.ISPROCESSED}). */
    public void chooseProcessed(String idOfRadio) {
        try { page.locator("#" + idOfRadio).click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("Radio " + idOfRadio + ": " + firstLine(e)); }
        waitForAngular(500);
    }

    /** Diagnostic: every visible control with its model / click handler / label / options. */
    public String dumpControls() {
        Object r = page.evaluate("() => {" + JS + " const lab=e=>{ try{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); } const g=e.closest('.form-group,td,li'); const l=g&&g.querySelector('label'); return l?norm(l.textContent).slice(0,40):''; }catch(x){return '';} };"
                + " const f=[...document.querySelectorAll('input,select,textarea,button,a[ng-click]')].filter(e=>vis(e) && !e.closest('.sidebar,.main-sidebar,.sidebar-menu') && !/setDatepickerDay|setNewYear|paginateYears/.test(e.getAttribute('ng-click')||''))"
                + ".map(e=>e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+' ngm='+(e.getAttribute('ng-model')||'')+' ngc='+(e.getAttribute('ng-click')||'')+' id='+(e.id||'')+' label='+lab(e)+(e.tagName==='SELECT'?' opts='+e.options.length:'')+(e.tagName==='BUTTON'||e.tagName==='A'?' text='+norm(e.textContent).slice(0,30):'')+(e.disabled?' [disabled]':''));"
                + " return f.join('\\n'); }");
        return r == null ? "" : r.toString();
    }
}
