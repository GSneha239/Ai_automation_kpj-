package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Billing &gt; <b>Credit Note</b> - Page Object ({@code #/CreditNote}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar Billing &gt; Credit Note; <b>From Date</b> ({@code PatientData.FromDate}, a 720kb date picker;
 * the person went one month back and picked the 5th), <b>Search</b> ({@code fnFillBillDetails();}); click a bill row ({@code SelectBillDetails(bill,$index)}); tick a service ({@code charge.isSelectedService},
 * {@code GetSelectedServices(charge)}); <b>Credit Note To*</b> ({@code PatientData.RefundTo}: Patient), <b>Credit Note Reason*</b> ({@code PatientData.CreditNoteReasonId}), <b>Credit Note Code Paymode*</b>
 * ({@code PatientData.CNCodePaymodeId}); the service's credit-note amount ({@code charge.refundamount}); <b>Save</b> ({@code fnSaveRefundDetails();}) - toast "Credit Note Saved Successfully."; then
 * <b>View Credit Note</b> ({@code fnViewLastCreditNote()}) opens the credit note report ({@code SQLReport/BillingReport/BillRefundCreditNoteReport.aspx?fintransheaderid=...}) in another tab.</p>
 *
 * <p>Seen live: ticking a service or typing the amount BEFORE the three drop-downs only raises "Please select Credit Note To!", and choosing the drop-downs clears the amount (Save then says "Amount should
 * be greater than Zero!") - so the amount is typed after the drop-downs.</p>
 */
public class CreditNote extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/creditnote";
    private static final String TOP = "Billing";

    public CreditNote(Page page) { super(page); }

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

    /** Billing &gt; Credit Note. Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Billing')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (topState(TOP) != 1) clickTop(TOP);
                waitLink(15000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__cnLink'; }", HREF);
                try { page.locator("#__cnLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CreditNote.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__cnLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fnFillBillDetails/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CreditNote.nav: the screen is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/creditnote/?$"); }

    // ---- the flow (steps as recorded) -------------------------------------------------------------------------------

    /** What the last action could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    private String val(String ngModel) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"[ng-model='\"+n+\"']\"); return e ? e.value : ''; }", ngModel);
        return v == null ? "" : v.toString();
    }

    public String fromDate() { return val("PatientData.FromDate"); }
    public String toDate() { return val("PatientData.ToDate"); }

    /** From Date: open the date picker, go back {@code monthsBack} month(s) with the previous-month arrow and click day {@code day} - as the recording did. Returns the From Date box's value. */
    public String pickFromDate(int monthsBack, int day) {
        try {
            page.locator("[ng-model='PatientData.FromDate']").first().click(new Locator.ClickOptions().setTimeout(5000));
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

    /** Click <b>Search</b> ({@code fnFillBillDetails();}) and wait for the bill list; returns the number of bills listed. */
    public int clickSearch() {
        try { page.locator("button[ng-click='fnFillBillDetails();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Search: " + firstLine(e)); return 0; }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"tr[ng-click*='SelectBillDetails']\").length>0", null, new Page.WaitForFunctionOptions().setTimeout(15000).setPollingInterval(500));
        } catch (Exception ignore) { /* none listed */ }
        waitForAngular(500);
        return billRows().size();
    }

    /** The bills listed as {bill date, bill no., MRN, visit no., patient name, company, total bill, discount, tax, net, self pay, company pay}. */
    public List<String[]> billRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"tr[ng-click*='SelectBillDetails']\")].map(tr=>[...tr.querySelectorAll('td')].slice(0,12).map(t=>(t.textContent||'').replace(/\\s+/g,' ').trim()))");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[12]; for (int i = 0; i < 12; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    /** Click the bill row holding {@code billNo} (its Bill No. cell, as recorded) and wait for its services; returns the number of services listed. */
    public int selectBill(String billNo) {
        try {
            page.locator("tr[ng-click*='SelectBillDetails']").filter(new Locator.FilterOptions().setHasText(billNo)).first().locator("td").nth(1).click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Bill row " + billNo + ": " + firstLine(e)); return 0; }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='charge.isSelectedService']\").length>0", null, new Page.WaitForFunctionOptions().setTimeout(15000).setPollingInterval(500));
        } catch (Exception ignore) { }
        waitForAngular(800);
        return serviceRows().size();
    }

    /** The bill's services as {code, name, doctor, qty, ..., amount available for CN} text per row. */
    public List<String> serviceRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"input[ng-model='charge.isSelectedService']\")].map(c=>{ const tr=c.closest('tr'); const t=[...tr.querySelectorAll('td')].map(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()).filter(x=>x && x.length<40); return t.slice(0,14).join(' | '); })");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Toasts on screen now (joined with " / "). */
    public String toastsNow() {
        Object r = page.evaluate("() => [...document.querySelectorAll('.toast-message')].map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).filter((v,i,a)=>a.indexOf(v)===i).join(' / ')");
        return r == null ? "" : r.toString();
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    /** Tick the {@code idx}-th service ({@code GetSelectedServices(charge)}); returns the toast it raised ("" if none). */
    public String tickService(int idx) {
        clearToasts();
        try {
            Locator cb = page.locator("input[ng-model='charge.isSelectedService']").nth(idx);
            cb.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            cb.click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Service tick: " + firstLine(e)); return ""; }
        waitForAngular(900);
        return toastsNow();
    }

    public boolean serviceTicked(int idx) {
        Object r = page.evaluate("(i) => { const c=document.querySelectorAll(\"input[ng-model='charge.isSelectedService']\")[i]; return !!c && c.checked; }", idx);
        return Boolean.TRUE.equals(r);
    }

    /** Choose {@code label} in the plain select bound to {@code ngModel} and read the chosen text back ("" when it could not be chosen). */
    public String choose(String ngModel, String label) {
        try {
            page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
        } catch (Exception e) { problems.add(ngModel + " = " + label + ": " + firstLine(e)); return ""; }
        waitForAngular(1000);
        Object r = page.evaluate("(n) => { const s=document.querySelector(\"select[ng-model='\"+n+\"']\"); const o=s && s.options[s.selectedIndex]; return o ? (o.textContent||'').replace(/\\s+/g,' ').trim() : ''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    /** The options of the select bound to {@code ngModel} ("Select" left out). */
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(n) => { const s=document.querySelector(\"select[ng-model='\"+n+\"']\"); return s ? [...s.options].map(o=>(o.textContent||'').replace(/\\s+/g,' ').trim()).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)) : []; }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Type {@code amount} into the {@code idx}-th service's credit note amount box and read it back. */
    public String typeAmount(int idx, String amount) {
        try {
            Locator box = page.locator("input[ng-model='charge.refundamount']").nth(idx);
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(amount, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
        } catch (Exception e) { problems.add("Credit note amount: " + firstLine(e)); return ""; }
        waitForAngular(800);
        Object r = page.evaluate("(i) => { const e=document.querySelectorAll(\"input[ng-model='charge.refundamount']\")[i]; return e ? e.value : ''; }", idx);
        return r == null ? "" : r.toString();
    }

    /** The "Credit Note Amount" total box above the services (disabled; filled by the screen). */
    public String totalCreditNoteAmount() { return val("BillDetails.RefundAmount"); }

    private void armToasts() {
        page.evaluate("() => { window.__cnT=[]; if(window.__cnObs) window.__cnObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cnT.includes(t)) window.__cnT.push(t); });"
                + " window.__cnObs=new MutationObserver(grab); window.__cnObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Save</b> ({@code fnSaveRefundDetails();}), accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSave() {
        clearToasts();
        armToasts();
        try { page.locator("button[ng-click='fnSaveRefundDetails();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Save: " + firstLine(e)); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__cnT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
    }

    /** The report tab opened by the last {@link #clickViewCreditNote()} (null if none opened) and its URL / screenshot. */
    public Page reportTab;
    public String reportUrl = "";
    public byte[] reportPng;

    /**
     * Click <b>View Credit Note</b> ({@code fnViewLastCreditNote()}): the credit note report opens in ANOTHER tab. The tab is read (URL, screenshot) and closed again. Returns the report URL ("" when no tab opened
     * and no URL was seen). {@code window.open} is wrapped as well, so a report that is handed to a download instead of a tab still gives its URL.
     */
    public String clickViewCreditNote() {
        reportTab = null; reportUrl = ""; reportPng = null;
        page.evaluate("() => { window.__cnUrl=''; if(!window.__cnWin){ window.__cnWin=true; const ow=window.open; window.open=function(u){ try{ window.__cnUrl=''+u; }catch(e){} return ow && ow.apply(this, arguments); }; } }");
        Page tab = null;
        try {
            tab = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(25000), () -> {
                try { page.locator("button[ng-click='fnViewLastCreditNote()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { problems.add("View Credit Note: " + firstLine(e)); }
            });
        } catch (Exception e) { System.out.println("CreditNote.view: no report tab opened (" + firstLine(e) + ")"); }
        Object u = page.evaluate("() => window.__cnUrl || ''");
        reportUrl = u == null ? "" : u.toString();
        if (tab != null) {
            reportTab = tab;
            try { tab.waitForLoadState(); } catch (Exception ignore) { }
            tab.waitForTimeout(3000);
            try { tab.bringToFront(); tab.waitForTimeout(1200); reportPng = tab.screenshot(new Page.ScreenshotOptions().setTimeout(20000)); }
            catch (Exception e) { System.out.println("CreditNote.view: report screenshot failed - " + firstLine(e)); }
            if (reportUrl.isEmpty() || reportUrl.equals("null")) reportUrl = tab.url();
            else if (tab.url() != null && tab.url().toLowerCase().contains("report")) reportUrl = tab.url();
            try { if (!tab.isClosed()) tab.close(); } catch (Exception ignore) { }
            try { page.bringToFront(); } catch (Exception ignore) { }
        }
        if (reportUrl.startsWith("/")) reportUrl = "https://devhis.sancyberhad.com" + reportUrl;
        return reportUrl;
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
