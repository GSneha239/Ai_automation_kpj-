package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Billing &gt; Miscellaneous &gt; <b>Miscellaneous Income</b> - Page Object ({@code #/MiscellaneousIncome}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar Billing &gt; Miscellaneous &gt; Miscellaneous Income; the list filter <b>From Date</b>
 * ({@code MiscellaneousIncome.fromdate}, a 720kb picker: previous-month arrow twice, day 6) + <b>Search</b> ({@code fngetMisincomeGrid();}); then the entry form under the list: <b>Name</b>
 * ({@code MiscellaneousIncome.name}), <b>Account Head</b> ({@code accountheadid}, select2), <b>Department</b> ({@code departmentid}, select2), <b>Cash Counter</b> ({@code cashcounterid}, select2),
 * <b>Amount</b> ({@code amount}), <b>Remark</b> ({@code remark}); <b>Save</b> ({@code saveadd();}) - toast "Data Saved Successfully.".</p>
 */
public class MiscellaneousIncome extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/miscellaneousincome";
    private static final String PARENT = "Billing", GROUP = "Miscellaneous";
    private static final String M = "MiscellaneousIncome.";

    public MiscellaneousIncome(Page page) { super(page); }

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

    /** Billing &gt; Miscellaneous &gt; Miscellaneous Income. Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__miLink'; }", HREF);
                try { page.locator("#__miLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("MiscellaneousIncome.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__miLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fngetMisincomeGrid/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("MiscellaneousIncome.nav: the screen is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/miscellaneousincome/?$"); }

    // ---- the list filter + list (steps as recorded) ---------------------------------------------------------------

    private String val(String ngModel) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"[ng-model='\"+n+\"']\"); return e ? e.value : ''; }", ngModel);
        return v == null ? "" : v.toString();
    }

    public String fromDate() { return val(M + "fromdate"); }
    public String toDate() { return val(M + "todate"); }

    /** From Date: open the date picker, go back {@code monthsBack} month(s) with the previous-month arrow and click day {@code day} - as the recording did. Returns the From Date box's value. */
    public String pickFromDate(int monthsBack, int day) {
        try {
            page.locator("[ng-model='" + M + "fromdate']").first().click(new Locator.ClickOptions().setTimeout(5000));
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

    /** Click the filter's <b>Search</b> ({@code fngetMisincomeGrid();}) and return the list's rows as {Bill No., Name, Amount}. */
    public List<String[]> clickSearch() {
        try { page.locator("button[ng-click='fngetMisincomeGrid();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Search: " + firstLine(e)); }
        page.waitForTimeout(3500);
        waitForAngular(800);
        return listRows();
    }

    /** The "List of Miscellaneous Income" rows as {Bill No., Name, Amount} (the table whose header starts with Bill No.). */
    public List<String[]> listRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>vis(x) && /^bill no/i.test(norm(x.rows[0]&&x.rows[0].textContent)) && /delete/i.test(norm(x.rows[0].textContent)));"
                + " if(!t) return []; return [...t.rows].slice(1).map(r=>[...r.cells].map(c=>norm(c.textContent))); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[3]; for (int i = 0; i < 3; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    // ---- the entry form (steps as recorded) -------------------------------------------------------------------------

    /** The form's Bill No. box ({@code #Billno}). */
    public String billNo() { return val("MiscellaneousIncomes.billno"); }
    public String name() { return val(M + "name"); }
    public String amount() { return val(M + "amount"); }
    public String remark() { return val(M + "remark"); }

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

    /** Account Head (select2): returns "" when shown and held by the model, else the reason. */
    public String pickAccountHead(String name) { return Select2Pick.pick(page, M + "accountheadid", name); }

    /** Department (select2). */
    public String pickDepartment(String name) { return Select2Pick.pick(page, M + "departmentid", name); }

    /** Cash Counter (select2). */
    public String pickCashCounter(String name) { return Select2Pick.pick(page, M + "cashcounterid", name); }

    /** The real Cash Counter names offered (no blank / "--Select--"). */
    public List<String> cashCounterNames() {
        List<String> out = new ArrayList<>();
        for (String n : Select2Pick.names(page, M + "cashcounterid")) { if (n != null && !n.trim().isEmpty() && !n.contains("--Select--")) out.add(n.trim()); }
        return out;
    }

    public List<String> accountHeadNames() {
        List<String> out = new ArrayList<>();
        for (String n : Select2Pick.names(page, M + "accountheadid")) { if (n != null && !n.trim().isEmpty() && !n.contains("--Select--")) out.add(n.trim()); }
        return out;
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    private void armToasts() {
        page.evaluate("() => { window.__miT=[]; if(window.__miObs) window.__miObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__miT.includes(t)) window.__miT.push(t); });"
                + " window.__miObs=new MutationObserver(grab); window.__miObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Save</b> ({@code saveadd();}) ONCE, accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSave() {
        clearToasts();
        armToasts();
        try { page.locator("button[ng-click='saveadd();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Save: " + firstLine(e)); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__miT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
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
