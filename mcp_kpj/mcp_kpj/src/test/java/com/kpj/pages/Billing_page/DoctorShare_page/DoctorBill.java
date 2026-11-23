package com.kpj.pages.Billing_page.DoctorShare_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Doctor Share &gt; <b>Doctor Bill</b> — Page Object ({@code #/Doctorbill}, header "Doctor Bill").
 *
 * <p>Confirmed live in a Playwright probe (devhis build v2026.11.03.387). One screen ({@code DoctorBill.*}):</p>
 * <ul>
 *   <li>Filters: <b>From Date</b> / <b>To Date</b> ({@code #txtFromDate}, {@code #txtToDate}, picker boxes, today by default), Department, Payable Type, Payable, Service (select2 lists) and a
 *       <b>OPD / IPD / ALL</b> radio ({@code DoctorBill.OPD_IPD}, OPD by default);</li>
 *   <li><b>Search</b> ({@code GetChargesDetails()}, POST {@code /api/DoctorBill/Fetch}, {@code ExecFlag GetChargesDetails}, body {@code fromdate1 / todate1 / opd_ipd1}) lists the doctors' charges in
 *       <b>Charges Details</b> — a plain table (Select | Bill No. | Bill Date | Patient Name | Service Name | Service Rate | Hospital Share | Doctor Share | Doctor Name | Net Bill Amount | Paid Amount |
 *       Is Settled), a tick box ({@code Charge.IsSelected}) on every row, rows in the scope's {@code ChargesList} (each with {@code issettled});</li>
 *   <li><b>Bill Details</b> (Doctor Name | Share Amount | Addition Amount | Deduction Amount | Total Amount) stays empty until a charge is ticked; ticking one also fills the read-only
 *       {@code TotalServiceRate} and {@code PaybleShare};</li>
 *   <li>Below: <b>Select All</b>, <b>Cash Counter</b> ({@code DoctorBill.CashCounterID}, select2), <b>Remark</b> ({@code DoctorBill.Remark}), <b>Save</b> ({@code IUDDoctorBill()}) and <b>Clear</b>.</li>
 * </ul>
 */
public class DoctorBill extends BasePage {

    public DoctorBill(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastCharge = "", lastCounter = "", lastRemark = "", lastToast = "", lastSaveAnswer = "", lastBillDetails = "";
    public int lastRowCount = 0, lastUnsettled = 0, lastSearchStatus = 0, pagesBeforeSave = 0;

    private static final String FROM_NG = "DoctorBill.FromDate", TO_NG = "DoctorBill.ToDate", COUNTER_NG = "DoctorBill.CashCounterID", REMARK_NG = "DoctorBill.Remark";
    private static final String HREF = "#/doctorbill";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const tableWith=(re)=>[...document.querySelectorAll('table')].find(t=>re.test(norm([...t.querySelectorAll('th')].map(h=>h.textContent).join(' '))));"
          + "const scope=()=>angular.element(document.querySelector(\"[ng-model='" + COUNTER_NG + "']\")).scope();";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Billing &gt; Doctor Share &gt; Doctor Bill — each level opened only if shut, the leaf looked up by its exact route inside the Doctor Share group (a plain Doctor Share screen sits beside it). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*billing\\s*$");
            expandMenuEntry("^\\s*billing\\s*$", "^\\s*doctor\\s*share\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const grp=top && [...top.closest('li').querySelectorAll('a')].find(x=>/^\\s*doctor\\s*share\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=grp ? grp.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__dbLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__dbLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DoctorBill.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__dbLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("() => !!document.getElementById('txtFromDate') && !!document.getElementById('txtToDate')", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DoctorBill.nav: the date boxes are not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    /** On this screen — the route AND the Search button the screen carries, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/doctorbill/?$")) return false;
        Object b = page.evaluate("() => [...document.querySelectorAll('button')].some(x=>/GetChargesDetails/.test(x.getAttribute('ng-click')||''))");
        return Boolean.TRUE.equals(b);
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    protected String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** The toasts showing right now, each message once, joined with " | " (the portal title stripped). */
    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean))].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- dates -----------------------------------------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> (dd/MM/yyyy) — typed first, set through the box's own model when the keystrokes are discarded. */
    public String enterDateRange(String from, String to) {
        problems.clear();
        String a = typeDate("txtFromDate", FROM_NG, from);
        String b = typeDate("txtToDate", TO_NG, to);
        lastFrom = model(FROM_NG);
        lastTo = model(TO_NG);
        if (!from.equals(lastFrom)) problems.add("From Date holds \"" + lastFrom + "\" instead of " + from);
        if (!to.equals(lastTo)) problems.add("To Date holds \"" + lastTo + "\" instead of " + to);
        String r = "From Date " + a + " | To Date " + b;
        System.out.println("DoctorBill: " + r);
        return r;
    }

    private String typeDate(String id, String ng, String value) {
        try {
            Locator box = page.locator("#" + id);
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.press("Control+a");
            box.press("Delete");
            box.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(90));
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        } catch (Exception e) { return "(typing failed: " + String.valueOf(e.getMessage()).split("\n")[0] + ")"; }
        waitForAngular(400);
        String via = "typed";
        if (!value.equals(model(ng))) {
            // The box is a picker box (class txtDate): when its typed keystrokes are discarded, set the value through the box's own model.
            page.evaluate("(a) => { const e=document.getElementById(a.id); if(!e) return; e.value=a.v;"
                    + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }",
                    Map.of("id", id, "v", value));
            waitForAngular(400);
            via = "set through the model";
        }
        dismissDatePicker();
        return value + " (" + via + ")";
    }

    // ---- search + Charges Details -----------------------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer ({@code /api/DoctorBill/Fetch}) and count the charges listed in Charges Details (also {@link #lastRowCount}). */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/GetChargesDetails/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__dbSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().contains("/api/DoctorBill/Fetch") && String.valueOf(x.request().postData()).contains("GetChargesDetails"),
                    new Page.WaitForResponseOptions().setTimeout(45000), () -> page.locator("#__dbSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search did not get an answer from /api/DoctorBill/Fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__dbSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(700);
        lastRowCount = chargeRowCount();
        for (int i = 0; answered && i < 10 && lastRowCount == 0; i++) {            // the answer is in; let the table render it before calling it empty
            page.waitForTimeout(300);
            lastRowCount = chargeRowCount();
        }
        lastUnsettled = unsettledCount();
        lastToast = toastsNow();
        return lastRowCount;
    }

    /** Charges listed in Charges Details — one tick box per row. */
    public int chargeRowCount() {
        Object n = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input[ng-model=\"Charge.IsSelected\"]')].filter(vis).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** How many of the listed charges are not settled yet (read from the screen's own list). */
    public int unsettledCount() {
        Object n = page.evaluate("() => {" + JS + " try{ const L=scope().ChargesList||[]; return L.filter(x=>x.issettled===false).length; }catch(e){ return -1; } }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** The column headers of Charges Details. */
    public List<String> chargeHeaders() {
        Object r = page.evaluate("() => {" + JS + " const t=tableWith(/Bill No\\./); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** "Bill No. | Bill Date | Patient | Service" of the first {@code n} charges. */
    public String sample(int n) {
        Object r = page.evaluate("(n) => {" + JS + " const t=tableWith(/Bill No\\./); if(!t) return ''; return [...t.querySelectorAll('tbody tr')].slice(0,n)"
                + "   .map(r=>[...r.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).slice(0,4).join(' | ')).join(' ;; '); }", n);
        return r == null ? "" : r.toString();
    }

    // ---- tick a charge ---------------------------------------------------------------------------------------

    /**
     * Tick the first charge that is not settled yet (the first one when every charge is settled) with a genuine click and read back which bill it was. Ticking is what fills
     * Bill Details and the Total Service Rate / Payble Share boxes.
     */
    public String tickFirstUnsettledCharge() {
        problems.clear();
        Object idx = page.evaluate("() => { try{ const L=angular.element(document.querySelector(\"[ng-model='" + COUNTER_NG + "']\")).scope().ChargesList||[];"
                + " const i=L.findIndex(x=>x.issettled===false); return i<0 ? 0 : i; }catch(e){ return 0; } }");
        int i = idx instanceof Number ? ((Number) idx).intValue() : 0;
        boolean wasUnsettled = unsettledCount() > 0;
        try {
            Locator cb = page.locator("input[ng-model='Charge.IsSelected']").nth(i);
            cb.scrollIntoViewIfNeeded();
            cb.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) {
            problems.add("the charge's tick box could not be ticked: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return "";
        }
        waitForAngular(900);
        Object r = page.evaluate("(i) => {" + JS + " const cb=[...document.querySelectorAll('input[ng-model=\"Charge.IsSelected\"]')][i]; if(!cb || !cb.checked) return '';"
                + " const tds=[...cb.closest('tr').querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean); return tds.slice(0,4).join(' | '); }", i);
        lastCharge = r == null ? "" : r.toString();
        if (lastCharge.isEmpty()) problems.add("the charge's tick box did not stay ticked");
        else if (!wasUnsettled) lastCharge += " (every listed charge was already settled)";
        lastBillDetails = billDetails();
        return lastCharge;
    }

    /** Bill Details as the screen shows it after a tick: the rows' text and the Total Service Rate / Payble Share boxes. */
    public String billDetails() {
        Object r = page.evaluate("() => {" + JS + " const t=tableWith(/Doctor Name.*Share Amount/); const rows=t? [...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(Boolean) : [];"
                + " const s=angular.element(document.querySelector(\"[ng-model='" + COUNTER_NG + "']\")).scope().DoctorBill||{};"
                + " return rows.length+' row(s) in Bill Details'+(rows.length? ' ('+rows.join(' ;; ').slice(0,160)+')' : '')+'; Total Service Rate '+(s.TotalServiceRate==null?'':s.TotalServiceRate)+', Payble Share '+(s.PaybleShare==null?'':s.PaybleShare); }");
        return r == null ? "" : r.toString();
    }

    /** True when ticking filled Bill Details (at least one row) and the Total Service Rate box. */
    public boolean billDetailsFilled() {
        Object r = page.evaluate("() => {" + JS + " const t=tableWith(/Doctor Name.*Share Amount/); const rows=t? [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent)).length : 0;"
                + " const s=angular.element(document.querySelector(\"[ng-model='" + COUNTER_NG + "']\")).scope().DoctorBill||{}; return rows>0 && parseFloat(s.TotalServiceRate)>0; }");
        return Boolean.TRUE.equals(r);
    }

    // ---- cash counter, remark, save --------------------------------------------------------------------------

    public List<String> cashCounters() { return Select2Pick.names(page, COUNTER_NG); }

    /** Choose the Cash Counter from its list; true when the box and the screen's model both hold it. */
    public boolean selectCashCounter(String name) {
        problems.clear();
        String p = Select2Pick.pick(page, COUNTER_NG, name);
        if (!p.isEmpty()) problems.add("Cash Counter: " + p);
        else lastCounter = name;
        return ok();
    }

    public String enterRemark(String value) {
        problems.clear();
        try {
            Locator box = page.locator("textarea[ng-model=\"" + REMARK_NG + "\"]");
            box.scrollIntoViewIfNeeded();
            box.fill(value, new Locator.FillOptions().setTimeout(6000));
            lastRemark = box.inputValue();
        } catch (Exception e) {
            lastRemark = "";
            problems.add("Remark could not be filled: " + String.valueOf(e.getMessage()).split("\n")[0]);
        }
        waitForAngular(300);
        if (lastRemark.isEmpty() && problems.isEmpty()) problems.add("Remark stayed empty");
        return lastRemark;
    }

    /** Click <b>Save</b> (a genuine click) and accept a confirm dialog if one appears; the save call's own answer is recorded in {@link #lastSaveAnswer}. */
    public boolean clickSave() {
        problems.clear();
        lastSaveAnswer = "";
        pagesBeforeSave = page.context().pages().size();
        java.util.function.Consumer<Response> listener = r -> {
            try {
                String u = r.url();
                if (u.contains("/api/DoctorBill/") && !String.valueOf(r.request().postData()).contains("GetChargesDetails") && "POST".equalsIgnoreCase(r.request().method())) {
                    String b = ""; try { b = r.text(); } catch (Exception ignore) { }
                    lastSaveAnswer = "HTTP " + r.status() + " " + u.replaceFirst("^https?://[^/]+", "") + " -> " + b.replaceAll("\\s+", " ").substring(0, Math.min(260, b.replaceAll("\\s+", " ").length()));
                }
            } catch (Exception ignore) { }
        };
        page.onResponse(listener);
        try {
            Locator save = page.locator("button[ng-click^='IUDDoctorBill']").first();   // the attribute reads "IUDDoctorBill();" (with the semicolon)
            save.scrollIntoViewIfNeeded();
            save.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
            problems.add("the Save button could not be clicked: " + m.substring(0, Math.min(400, m.length())));
            return false;
        }
        waitForAngular(300);
        try { acceptSaveDialog(); } catch (Exception ignore) { }
        return true;
    }

    /** Wait for the answer to Save: a success or failure toast ("" when none appeared). */
    public String waitForSaveToast() {
        String seen = "";
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            String t = toastsNow();
            if (!t.isEmpty()) {
                seen = t;
                String low = t.toLowerCase();
                if (low.contains("success") || low.contains("saved") || low.contains("fail") || low.contains("error") || low.contains("please") || low.contains("already")) break;
            }
            page.waitForTimeout(250);
        }
        lastToast = seen;
        return seen;
    }

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("please") || t.contains("error") || t.contains("fail") || t.contains("already") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added") || t.contains("inserted");
    }

    /** Close any tab the save opened (a report / voucher) and return how many there were. */
    public int closeExtraTabs() {
        int closed = 0;
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) { pg.close(); closed++; }
            page.bringToFront();
        } catch (Exception ignore) { }
        return closed;
    }
}
