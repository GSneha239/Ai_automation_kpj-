package com.kpj.pages.Billing_page.Cancellation_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Billing &gt; Cancellation &gt; <b>Receipt</b> - Page Object ({@code #/ReceiptCancellation}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar Billing &gt; Cancellation &gt; Receipt; <b>From Date</b> ({@code PatientData.FromDate}, a 720kb
 * date picker: previous-month arrow twice, day 6); <b>Receipt Type</b> ({@code PatientData.ReceiptTypeId}, select2: Advance Receipt, Advance Refund Receipt, Bill Receipt, ...); <b>Search</b> ({@code fnSearchBill();});
 * tick a receipt ({@code bill.IsSelected}, {@code SelectBillDetails(bill,$index)}); in its payment row choose the <b>cancellation reason</b> ({@code PaymentData.cancellationreasonid[$index]}, a plain select) and type a
 * <b>cancellation remark</b> ({@code PaymentData.cancellationreason[$index]}); <b>Save</b> ({@code fnCancelReceipts();}) - toast "Receipt Cancelled Successfully.".</p>
 */
public class Receipt extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/receiptcancellation";
    private static final String PARENT = "Billing", GROUP = "Cancellation";

    public Receipt(Page page) { super(page); }

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

    /** Billing &gt; Cancellation &gt; Receipt. Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__rcLink'; }", HREF);
                try { page.locator("#__rcLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("Receipt.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__rcLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fnSearchBill/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("Receipt.nav: the screen is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/receiptcancellation/?$"); }

    // ---- the flow (steps as recorded) -------------------------------------------------------------------------------

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

    /** Receipt Type (select2): returns "" when it is shown and held by the model, else the reason. */
    public String pickReceiptType(String name) {
        return Select2Pick.pick(page, "PatientData.ReceiptTypeId", name);
    }

    /** Click <b>Search</b> ({@code fnSearchBill();}) and wait for the receipt list; returns the number of receipts listed. */
    public int clickSearch() {
        try { page.locator("button[ng-click='fnSearchBill();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Search: " + firstLine(e)); return 0; }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='bill.IsSelected']\").length>0", null, new Page.WaitForFunctionOptions().setTimeout(15000).setPollingInterval(500));
        } catch (Exception ignore) { /* none listed */ }
        waitForAngular(800);
        return receiptRows().size();
    }

    /** The receipts listed as {allocation no., date, bill no., receipt type, reg. type, patient, amount}. */
    public List<String[]> receiptRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"input[ng-model='bill.IsSelected']\")].map(c=>{ const tr=c.closest('tr'); const t=[...tr.querySelectorAll('td')].map(td=>[...td.childNodes].filter(n=>n.nodeType===3).map(n=>n.textContent).join(' ').replace(/\\s+/g,' ').trim()).filter(x=>x); return t.slice(0,7); })");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[7]; for (int i = 0; i < 7; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    /** Re-apply the filters (From Date, Receipt Type) and Search again - after Save the screen empties its list; returns the number of receipts listed. */
    public int searchAgain(int monthsBack, int day, String receiptType) {
        pickFromDate(monthsBack, day);
        pickReceiptType(receiptType);
        return clickSearch();
    }

    /** Tick the {@code idx}-th receipt ({@code SelectBillDetails(bill,$index)}) and wait for its payment rows; returns the number of payment rows. */
    public int tickReceipt(int idx) {
        try {
            Locator cb = page.locator("input[ng-model='bill.IsSelected']").nth(idx);
            cb.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            cb.click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Receipt tick: " + firstLine(e)); return 0; }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"select[ng-model='PaymentData.cancellationreasonid[$index]']\").length>0", null, new Page.WaitForFunctionOptions().setTimeout(15000).setPollingInterval(500));
        } catch (Exception ignore) { }
        waitForAngular(800);
        return paymentRows().size();
    }

    public boolean receiptTicked(int idx) {
        Object r = page.evaluate("(i) => { const c=document.querySelectorAll(\"input[ng-model='bill.IsSelected']\")[i]; return !!c && c.checked; }", idx);
        return Boolean.TRUE.equals(r);
    }

    /** The ticked receipt's payment rows (text of each row without the drop-down's options). */
    public List<String> paymentRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"select[ng-model='PaymentData.cancellationreasonid[$index]']\")].map(s=>{ const tr=s.closest('tr'); return [...tr.querySelectorAll('td')].map(td=>[...td.childNodes].filter(n=>n.nodeType===3).map(n=>n.textContent).join(' ').replace(/\\s+/g,' ').trim()).filter(x=>x).join(' | '); })");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The cancellation reasons the payment row offers ("Select" left out). */
    public List<String> reasonOptions() {
        Object r = page.evaluate("() => { const s=document.querySelector(\"select[ng-model='PaymentData.cancellationreasonid[$index]']\"); return s ? [...s.options].map(o=>(o.textContent||'').replace(/\\s+/g,' ').trim()).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Choose {@code label} in the first payment row's cancellation reason and read the chosen text back ("" when it could not be chosen). */
    public String chooseReason(String label) {
        try {
            page.locator("select[ng-model='PaymentData.cancellationreasonid[$index]']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
        } catch (Exception e) { problems.add("Cancellation reason = " + label + ": " + firstLine(e)); return ""; }
        waitForAngular(800);
        Object r = page.evaluate("() => { const s=document.querySelector(\"select[ng-model='PaymentData.cancellationreasonid[$index]']\"); const o=s && s.options[s.selectedIndex]; return o ? (o.textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Type {@code text} into the first payment row's cancellation remark and read it back. */
    public String typeRemark(String text) {
        try {
            Locator box = page.locator("input[ng-model='PaymentData.cancellationreason[$index]']").first();
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(text, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
        } catch (Exception e) { problems.add("Cancellation remark: " + firstLine(e)); return ""; }
        waitForAngular(500);
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='PaymentData.cancellationreason[$index]']\"); return e ? e.value : ''; }");
        return r == null ? "" : r.toString();
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    private void armToasts() {
        page.evaluate("() => { window.__rcT=[]; if(window.__rcObs) window.__rcObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rcT.includes(t)) window.__rcT.push(t); });"
                + " window.__rcObs=new MutationObserver(grab); window.__rcObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Save</b> ({@code fnCancelReceipts();}), accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSave() {
        clearToasts();
        armToasts();
        try { page.locator("button[ng-click='fnCancelReceipts();']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Save: " + firstLine(e)); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__rcT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
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
