package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Investigation &gt; Lab &gt; <b>Report Delivery</b> — Page Object ({@code #/ReportDelivery}, page title "Report Delivery").
 *
 * <p>Not the same screen as Investigation &gt; Radiology &gt; Radiology Report Delivery ({@code #/RadiologyReportDelivery}) or
 * Lab &gt; Histo Pathology &gt; Histo Report Delivery ({@code #/HistoReportDelivery}); the menu link is matched on its exact route.</p>
 *
 * <h3>Confirmed live (devhis, 2026-11-19, build v2026.11.18.491)</h3>
 * <ul>
 *   <li>Filters: <b>Date Range</b> ({@code #IsDateRange}, ticked), <b>From Date</b> / <b>To Date</b> ({@code ReportDelivery.FromDate/.ToDate},
 *       dd/MM/yyyy, today, no element id), <b>MRN.</b> ({@code #textinput}) with the find button ({@code SearchPatientByMRNo()}) and the patient
 *       popup ({@code OpenPopupScreen()}), <b>Reg. Type</b> OPD / IPD / All ({@code ReportDelivery.OPDIPD}, OPD by default), <b>Delivered</b> /
 *       <b>Not Delivered.</b> ({@code ReportDelivery.ReportUploaded}, Not Delivered by default), <b>Sub Group</b> (select2 {@code #subgroupid},
 *       CommonDropdown LABSUBGROUP), <b>Test Name</b> ({@code #txtServiceName}), <b>Sample No</b> / <b>Order No</b> (max 15), <b>Location</b>
 *       ({@code #locationid}).</li>
 *   <li>Buttons: <b>Search</b> ({@code fnGetAllReportDeliveryTestDetails()}), <b>Clear</b> ({@code fnClear()}), <b>Bulk print</b>
 *       ({@code Bulkprintorder()}), <b>Combine print</b> ({@code printorder()}), <b>Separate print</b> ({@code printseprateReport()}),
 *       <b>Delivered</b> ({@code opendeliveredpopup();fnSetDelivered();}).</li>
 *   <li>Grids: <b>List of Orders</b> ({@code gridOptions}) and <b>Test Details</b> ({@code gridOptions1}: Print | Report Delivered |
 *       Order Date &amp; Time | Order No | sampleno | Test Name | MRN No. | Patient Name | Sub Group Name). Legend: Report Delivered,
 *       Report Pending / Pending Reports, Stat, VIP, Infectious.</li>
 *   <li>Search = {@code POST /API/ReportDelivery/fetch}, ExecFlag {@code GetAllReportDeliveryTestDetails}; refused without a Sub Group
 *       (JAlert "Please select sub group").</li>
 *   <li>Delivered dialog {@code #CashModal}: Delivered By ({@code #deliveredby}) with save ({@code IUDReportDelivery()} &rarr;
 *       {@code /API/ReportDelivery/IUD}, ExecFlag REPORTDELIVERED, then SendSMsandEmail) and cancel ({@code resetForm()}).</li>
 * </ul>
 *
 * <p><b>Read-only:</b> the dialog's save is never pressed and no print is produced.</p>
 */
public class ReportDelivery extends BasePage {

    public ReportDelivery(Page page) { super(page); }

    public static final String ROUTE = "#/ReportDelivery";
    public static final String API_FETCH = "/api/reportdelivery/fetch";
    public static final String EXEC_SEARCH = "GetAllReportDeliveryTestDetails";
    public static final String ORDERS = "gridOptions", TESTS = "gridOptions1";
    public static final String NG = "ReportDelivery.";

    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public int lastSearchStatus = 0;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const grid=n=>document.querySelector(\"[ui-grid='\"+n+\"']\");"
          + "const data=n=>{ try{ return angular.element(grid(n)).scope().$eval(n+'.data')||[]; }catch(e){ return []; } };"
          + "const btn=re=>[...document.querySelectorAll('button')].filter(vis).find(x=>re.test(x.getAttribute('ng-click')||''));";

    // ---- menu / navigation ---------------------------------------------------------------------------------

    /**
     * Open Investigation &gt; Lab in the side menu. The sidebar ({@code #divSideMeunGeneration}) is built LAZILY: right after login a
     * module's sub-links are not in the page at all, so the menu has to be opened before it can be read (confirmed live: reading it first
     * found no Report Delivery link although the screen then opened through that same menu).
     */
    public void expandLabMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*investigation\\s*$");
        expandMenuEntry("^\\s*investigation\\s*$", "^\\s*lab\\s*$");
    }

    /** Every link under Investigation &gt; Lab whose text is exactly "Report Delivery", as "text => href". Opens the menu first. */
    public List<String> menuEntries() {
        expandLabMenu();
        return strings(page.evaluate("() => {" + JS
                + " const inv=[...document.querySelectorAll('a')].find(x=>/^investigation$/i.test(norm(x.textContent)));"
                + " const li=inv ? inv.closest('li') : null; if(!li) return [];"
                + " const lab=[...li.querySelectorAll('a')].find(x=>/^lab$/i.test(norm(x.textContent)));"
                + " const labLi=lab ? lab.closest('li') : null; if(!labLi) return [];"
                + " return [...labLi.querySelectorAll('a[href]')].filter(a=>/^report\\s*delivery$/i.test(norm(a.textContent)))"
                + "   .map(a=>norm(a.textContent)+' => '+a.getAttribute('href')); }"));
    }

    /** Investigation &gt; Lab &gt; Report Delivery through the side menu (a typed URL makes the app offer a log-out "Reload page" prompt). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*investigation\\s*$");
            expandMenuEntry("^\\s*investigation\\s*$", "^\\s*lab\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS
                    + " const inv=[...document.querySelectorAll('a')].find(x=>/^investigation$/i.test(norm(x.textContent)));"
                    + " const root=inv ? (inv.closest('li')||document) : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__rdLink'; return true; }", ROUTE.toLowerCase());
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__rdLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__rdLink'); if(a) a.click(); }"); }
                page.evaluate("() => { const e=document.getElementById('__rdLink'); if(e) e.removeAttribute('id'); }");
            }
            try {
                page.waitForFunction("() => /#\\/reportdelivery$/i.test(location.hash) && [...document.querySelectorAll('button')]"
                        + ".some(b=>/fnGetAllReportDeliveryTestDetails\\(\\)/.test(b.getAttribute('ng-click')||''))", null,
                        new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        return onScreen();
    }

    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/reportdelivery/?$")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('button')].some(b=>/fnGetAllReportDeliveryTestDetails\\(\\)/.test(b.getAttribute('ng-click')||''))"));
    }

    public String pageTitle() { Object t = page.evaluate("() => document.title"); return t == null ? "" : t.toString(); }

    // ---- what is on the screen -----------------------------------------------------------------------------

    public List<String> labels() {
        return strings(page.evaluate("() => {" + JS + " const m=document.querySelector('main')||document.body;"
                + " return [...m.querySelectorAll('label')].filter(l=>!l.closest('.modal')).map(l=>norm(l.innerText)).filter(Boolean); }"));
    }

    /** Visible buttons of the screen (not date pickers' / pager's), as "text | ng-click". */
    public List<String> buttons() {
        return strings(page.evaluate("() => {" + JS + " const m=document.querySelector('main')||document.body;"
                + " return [...m.querySelectorAll('button')].filter(vis).filter(b=>!b.closest('.modal'))"
                + "  .map(b=>norm(b.innerText)+' | '+(b.getAttribute('ng-click')||'')).filter(s=>!/page\\w*Click|toggle\\(|Month\\(|Year|Datepicker|increment|decrement/i.test(s)); }"));
    }

    public String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public String shownValue(String ng) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")][0]; return e ? String(e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString();
    }

    public String chosen(String selectId) {
        Object v = page.evaluate("(i) => { const e=document.getElementById(i); if(!e || e.selectedIndex<0) return '';"
                + " return (e.options[e.selectedIndex].textContent||'').replace(/\\s+/g,' ').trim(); }", selectId);
        return v == null ? "" : v.toString();
    }

    public boolean dateRangeTicked() {
        return Boolean.TRUE.equals(page.evaluate("() => { const e=document.getElementById('IsDateRange'); return !!e && e.checked; }"));
    }

    public String checkedRadio(String ng) {
        Object v = page.evaluate("(n) => {" + JS + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='\"+n+\"']\")].find(x=>x.checked);"
                + " if(!r) return ''; const l=r.closest('label'); return l ? norm(l.innerText) : (r.id||r.value); }", ng);
        return v == null ? "" : v.toString();
    }

    public boolean clickRadio(String ng, String labelRegex) {
        Object v = page.evaluate("(a) => {" + JS + " const rx=new RegExp(a.re,'i');"
                + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='\"+a.ng+\"']\")].find(x=>{ const l=x.closest('label'); return rx.test(norm(l?l.innerText:'')); });"
                + " if(!r) return false; r.click(); return true; }", Map.of("ng", ng, "re", labelRegex));
        waitForAngular(300);
        return Boolean.TRUE.equals(v);
    }

    // ---- filters -------------------------------------------------------------------------------------------

    public List<String> subGroupOptions() {
        return strings(page.evaluate("() => { const s=document.getElementById('subgroupid'); if(!s) return [];"
                + " return [...s.options].filter(o=>o.value!=='' && o.value!=='?' && !/^\\s*-*\\s*select\\s*-*\\s*$/i.test(o.textContent)).map(o=>o.textContent.trim()); }"));
    }

    public String selectSubGroup(String text) {
        Object v = page.evaluate("(t) => { const s=document.getElementById('subgroupid'); if(!s) return '';"
                + " const opts=[...s.options].filter(o=>o.value!=='' && o.value!=='?' && !/^\\s*-*\\s*select\\s*-*\\s*$/i.test(o.textContent));"
                + " const o=t ? opts.find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase()) : opts[0]; if(!o) return '';"
                + " s.value=o.value; try{ const c=angular.element(s).controller('ngModel'); if(c){ c.$setViewValue(o.value); c.$render(); } }catch(e){}"
                + " try{ if(window.jQuery) jQuery(s).select2('val', o.value); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return o.textContent.trim(); }", text == null ? "" : text);
        waitForAngular(400);
        return v == null ? "" : v.toString();
    }

    public void clearSubGroup() {
        setModel(NG + "subgroupid", null);
        page.evaluate("() => { const s=document.getElementById('subgroupid'); if(s && window.jQuery){ try{ jQuery(s).select2('val',''); }catch(e){} } }");
        waitForAngular(300);
    }

    public void setModel(String ng, String value) {
        page.evaluate("(a) => { const e=document.querySelector(\"[ng-model='\"+a.ng+\"']\"); if(!e) return;"
                + " const s=angular.element(e).scope(); s.$apply(()=>{ const p=a.ng.split('.'); let o=s; for(let i=0;i<p.length-1;i++){ o=o[p[i]]=o[p[i]]||{}; }"
                + "   o[p[p.length-1]] = a.v===undefined ? null : a.v; }); }", mapOf("ng", ng, "v", value));
        waitForAngular(250);
    }

    public void typeInto(String ng, String value) {
        page.evaluate("(a) => { const e=[...document.querySelectorAll(\"input[ng-model='\"+a.ng+\"'],textarea[ng-model='\"+a.ng+\"']\")][0]; if(!e) return;"
                + " e.value=a.v; try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", Map.of("ng", ng, "v", value));
        waitForAngular(300);
    }

    public void enterDates(String from, String to) {
        typeInto(NG + "FromDate", from);
        typeInto(NG + "ToDate", to);
        dismissDatePicker();
    }

    public void setDateRange(boolean ticked) {
        page.evaluate("(t) => { const e=document.getElementById('IsDateRange'); if(e && e.checked!==t) e.click(); }", ticked);
        waitForAngular(300);
    }

    // ---- actions -------------------------------------------------------------------------------------------

    /** Click <b>Search</b> and wait for {@code /API/ReportDelivery/fetch} (GetAllReportDeliveryTestDetails). HTTP status, or 0 when nothing answered. */
    public int clickSearch(int timeoutMs) {
        problems.clear();
        lastSearchStatus = 0;
        if (!tagButton("fnGetAllReportDeliveryTestDetails\\(\\)", "__rdSearch")) { problems.add("the Search button is not on screen"); return 0; }
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains(API_FETCH)
                            && String.valueOf(x.request().postData()).contains(EXEC_SEARCH),
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> page.locator("#__rdSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("no " + EXEC_SEARCH + " request answered within " + timeoutMs + " ms");
        } finally {
            untag("__rdSearch");
        }
        waitForAngular(1200);
        return lastSearchStatus;
    }

    /** Click Search and say whether a search request was SENT within the time. */
    public boolean clickSearchSendsRequest(int timeoutMs) {
        if (!tagButton("fnGetAllReportDeliveryTestDetails\\(\\)", "__rdSearch")) { problems.add("the Search button is not on screen"); return false; }
        try {
            Request q = page.waitForRequest(x -> x.url().toLowerCase().contains(API_FETCH) && String.valueOf(x.postData()).contains(EXEC_SEARCH),
                    new Page.WaitForRequestOptions().setTimeout(timeoutMs),
                    () -> page.locator("#__rdSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            return q != null;
        } catch (Exception e) {
            return false;
        } finally {
            untag("__rdSearch");
            waitForAngular(800);
        }
    }

    public void clickClear() { clickByNgClick("fnClear\\(\\)", "Clear"); waitForAngular(800); }

    public void findPatient(String mrn) {
        typeInto(NG + "MRNO", mrn);
        clickByNgClick("SearchPatientByMRNo\\(\\)", "MRN find");
        waitForAngular(2500);
    }

    /**
     * Press an action button (Bulk print / Combine print / Separate print / Delivered) by its ng-click. With no record ticked the app answers
     * with a message only. Returns false when the button is not on screen.
     */
    public boolean pressAction(String ngClickRegex, String name) {
        boolean ok = clickByNgClick(ngClickRegex, name);
        waitForAngular(1200);
        return ok;
    }

    /** Ids of the dialogs (.modal) showing right now. */
    public List<String> openDialogs() {
        return strings(page.evaluate("() => [...document.querySelectorAll('.modal')].filter(m=>(m.classList.contains('in')||m.classList.contains('show')) && getComputedStyle(m).display!=='none')"
                + ".map(m=>m.id||'(no id)')"));
    }

    /** Close every open dialog through its own cancel/close button (resetForm / resetFormPopup / hidepreview), never its save. */
    public void closeDialogs() {
        page.evaluate("() => { [...document.querySelectorAll('.modal')].filter(m=>(m.classList.contains('in')||m.classList.contains('show')) && getComputedStyle(m).display!=='none')"
                + ".forEach(m=>{ const b=[...m.querySelectorAll('button')].find(x=>/resetForm|resetFormPopup|hidepreview|close/i.test((x.getAttribute('ng-click')||'')+' '+x.className) && !/IUD|fnAdd|PRint/i.test(x.getAttribute('ng-click')||''));"
                + "   if(b) b.click(); else if(window.jQuery) jQuery(m).modal('hide'); }); }");
        waitForAngular(800);
    }

    /** The Delivered dialog's Delivered By options and its buttons (as "text | ng-click"). */
    public String deliveredDialog() {
        Object v = page.evaluate("() => {" + JS + " const m=document.getElementById('CashModal'); if(!m) return 'no #CashModal';"
                + " const s=document.getElementById('deliveredby'); const opts=s?[...s.options].map(o=>norm(o.textContent)).filter(Boolean):[];"
                + " const b=[...m.querySelectorAll('button')].map(x=>norm(x.innerText)+' | '+(x.getAttribute('ng-click')||'')).filter(x=>!/^\\s*\\|/.test(x)||/IUD|reset/.test(x));"
                + " return 'Delivered By '+JSON.stringify(opts)+'; buttons '+JSON.stringify(b); }");
        return v == null ? "" : v.toString();
    }

    // ---- grids ---------------------------------------------------------------------------------------------

    public int rowCount(String grid) {
        Object n = page.evaluate("(g) => {" + JS + " return data(g).length; }", grid);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public List<String> headers(String grid) {
        return strings(page.evaluate("(g) => {" + JS + " const e=grid(g); return e ? [...e.querySelectorAll('.ui-grid-header-cell')]"
                + ".map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean) : []; }", grid));
    }

    /** The delivered flag of every Test Details record ("true" / "false" / "" when the record has no such field). */
    public List<String> deliveredFlags() {
        return strings(page.evaluate("() => {" + JS + " return data('" + TESTS + "').map(x=>{ const v=(x.isreportdelivered!==undefined?x.isreportdelivered:x.reportdelivered);"
                + " return v===undefined||v===null ? '' : String(v); }); }"));
    }

    public List<String> testRecords(int n) {
        return strings(page.evaluate("(k) => {" + JS + " return data('" + TESTS + "').slice(0,k).map(x=>["
                + "'Order No='+(x.orderno||''),'Sample No='+(x.sampleno||''),'Test='+(x.servicename||x.tariffservicename||x.serviceitemname||''),"
                + "'MRN='+(x.mrno||''),'Delivered='+(x.isreportdelivered!==undefined?x.isreportdelivered:'')].join(' | ')); }", n));
    }

    public boolean selectFirstTest() {
        Object v = page.evaluate("() => {" + JS + " const g=grid('" + TESTS + "'); if(!g) return false; const d=data('" + TESTS + "'); if(!d.length) return false;"
                + " try{ const ctl=angular.element(g).controller('uiGrid'); ctl.grid.api.selection.selectRow(d[0]); return true; }catch(e){ return false; } }");
        waitForAngular(800);
        return Boolean.TRUE.equals(v);
    }

    public List<String> legend() {
        List<String> out = new ArrayList<>();
        for (String l : labels()) if (l.matches("(?i)stat|vip|infected|infectious|report delivered|report pending.*")) out.add(l);
        return out;
    }

    public String boxFit(String ng) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")][0]; return e ? e.clientWidth+'/'+e.scrollWidth : ''; }", ng);
        return v == null ? "" : v.toString();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private boolean clickByNgClick(String regex, String name) {
        String id = "__rdBtn";
        if (!tagButton(regex, id)) { problems.add("the " + name + " button is not on screen"); return false; }
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add(name + " could not be clicked: " + String.valueOf(e.getMessage()).split("\n")[0]); return false; }
        finally { untag(id); }
        return true;
    }

    private boolean tagButton(String clickRegex, String id) {
        Object v = page.evaluate("(a) => {" + JS + " const b=btn(new RegExp(a.re)); if(!b) return false; b.scrollIntoView({block:'center'}); b.id=a.id; return true; }",
                Map.of("re", clickRegex, "id", id));
        return Boolean.TRUE.equals(v);
    }

    private void untag(String id) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id);
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }
}
