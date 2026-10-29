package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Section helpers shared by the Billing <b>charges</b> screens (OPD Charges / IPD Charges): Patient Details,
 * Bill Details, Package Service, Package Item — plus the safety tooling a read-only test needs (JAlert capture,
 * a request-level write audit, and a cleanup that only ever removes rows the test itself added).
 *
 * <p>The two screens share one AngularJS controller design (ServiceData entry row, ServiceList ui-grid, the same
 * auto-complete, JAlert messages and duplicate confirm) but differ in pane ids, in the model behind the Package
 * Service tab, and in the Delete handler — so each screen supplies a {@link Config}. Verified live on HIS Demo,
 * 26/10/2026: {@link Config#OPD}.</p>
 *
 * <p><b>Read-only with respect to the server.</b> Nothing here clicks Save ({@code IUDBill}), answers a confirm
 * with anything but <b>No</b>, or removes a row unless it was tagged by this helper as test-added AND is unsaved
 * ({@code chargeid == 0}) AND is unpaired ({@code configsamplecollectibleserviceid == 0}). The pairing guard
 * matters on OPD: {@code RemoveService} also removes the row's paired partner, and when that partner is posted
 * ({@code chargeid > 0}) the app cancels it on the server ({@code OPDBillFactory.IUDBill}, ExecFlag
 * {@code CancelService}).</p>
 *
 * <p>(IPD Charges currently keeps its own copy of these helpers in {@link IpdCharges}; it can move onto this class
 * later without behaviour change. The request classifier is shared already: {@link IpdCharges#classifyRequest}.)</p>
 */
public class ChargesScreenSections extends BasePage {

    /** Per-screen DOM / model names. */
    public static final class Config {
        public final String screen, billPane, packageServicePane, packageItemPane, cancelledPane;
        /** Controller array behind the Package Service tab, its per-entry service-line list and name field. */
        public final String breakdownModel, breakdownLines, breakdownName;
        /** Grid row Delete handler name (as in the cell template's ng-click). */
        public final String deleteFn;
        /** Item flag the Package Service tab is fed by (OPD: health plans). */
        public final String breakdownItemFlag;

        public Config(String screen, String billPane, String packageServicePane, String packageItemPane, String cancelledPane,
                      String breakdownModel, String breakdownLines, String breakdownName, String deleteFn, String breakdownItemFlag) {
            this.screen = screen; this.billPane = billPane; this.packageServicePane = packageServicePane;
            this.packageItemPane = packageItemPane; this.cancelledPane = cancelledPane; this.breakdownModel = breakdownModel;
            this.breakdownLines = breakdownLines; this.breakdownName = breakdownName; this.deleteFn = deleteFn;
            this.breakdownItemFlag = breakdownItemFlag;
        }

        /** OPD Charges ({@code #/OPDCharges}) — mapped live on HIS Demo, 26/10/2026. */
        public static final Config OPD = new Config("OPD Charges", "#PatientServiceDetails", "#HealthPlanDetails", "#pkgItem",
                "#CancelledCharges", "HealthPlans", "HealthPlanServices", "PlanName", "RemoveService", "ishealthplan");
    }

    public final Config cfg;
    private final String rowsJs, pdJs;
    private static final String CONFIRM_JS =
            "const __confirm=()=>[...document.querySelectorAll('.ng-confirm-box,.jconfirm-box')].find(b=>{ const r=b.getBoundingClientRect(); return r.width>0&&r.height>0; });";

    public ChargesScreenSections(Page page, Config cfg) {
        super(page);
        this.cfg = cfg;
        // The controller scope: anchored on the MRN box (same scope as the charge grid on both screens).
        this.pdJs = "const __sc=()=>{ try{ return angular.element(document.querySelector(\"[ng-model='PatientDetails.MRNo']\")).scope()||{}; }catch(e){ return {}; } };"
                + " const __pd=()=>(__sc().PatientDetails||{});";
        this.rowsJs = pdJs
                + " const __rows=()=>{ const s=__sc(); if(s.ServiceList&&Array.isArray(s.ServiceList.data)) return s.ServiceList.data; let d=null;"
                + "   document.querySelectorAll('" + cfg.billPane + " *').forEach(el=>{ try{ const g=angular.element(el).scope(); const x=g&&g.grid&&g.grid.options&&g.grid.options.data;"
                + "     if(Array.isArray(x)&&(!d||x.length>d.length)) d=x; }catch(e){} }); return d||[]; };"
                + " const __code=r=>String(r.ServiceCode||r.servicecode||'');"
                + " const __name=r=>String(r.ServiceName||r.servicename||'');";
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.getBoundingClientRect().width>0); if(!e) return;"
                + " const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } else { e.value=a.v; }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                Map.of("ng", ng, "v", v));
    }

    private void clickTagged(String jsFinder, String tagId, int timeoutMs) {
        Object ok = page.evaluate("(id) => { const el=(" + jsFinder + ")(); if(!el) return false; el.id=id; return true; }", tagId);
        if (!Boolean.TRUE.equals(ok)) throw new IllegalStateException("element not found for #" + tagId);
        try { page.locator("#" + tagId).click(new Locator.ClickOptions().setTimeout(timeoutMs)); }
        finally { page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tagId); }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object r) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (r instanceof Map) out.putAll((Map<String, Object>) r);
        return out;
    }

    // ---- alert + response capture ---------------------------------------------------------------------------

    /** Capture JAlert messages (toasts fade in ~1-2s) and each XHR's ExecFlag + result size. Idempotent. */
    public void installCaptureHooks() {
        page.evaluate("() => {"
                + " if(!window.__csJaHooked && typeof window.JAlert==='function'){ const o=window.JAlert;"
                + "   window.JAlert=function(m,t){ try{ (window.__csAlerts=window.__csAlerts||[]).push(String(m==null?'':m).replace(/<[^>]+>/g,'').replace(/\\s+/g,' ').trim()); }catch(e){} return o.apply(this,arguments); };"
                + "   window.__csJaHooked=true; }"
                + " if(!window.__csXhrHooked){ window.__csXhr=[]; const op=XMLHttpRequest.prototype.open, sd=XMLHttpRequest.prototype.send;"
                + "   XMLHttpRequest.prototype.open=function(m,u){ this.__csU=u; return op.apply(this,arguments); };"
                + "   XMLHttpRequest.prototype.send=function(b){ const x=this; const body=String(b||'');"
                + "     x.addEventListener('loadend',()=>{ try{ if(/getInternalmsg/i.test(String(x.__csU||''))) return;"
                + "       const f=(body.match(/\"ExecFlag\"\\s*:\\s*\"(\\w+)\"/)||[])[1]||''; let n=-1; try{ const j=JSON.parse(x.responseText); if(Array.isArray(j)) n=j.length; }catch(e){}"
                + "       window.__csXhr.push({u:String(x.__csU||''), flag:f, n:n}); if(window.__csXhr.length>400) window.__csXhr.shift(); }catch(e){} });"
                + "     return sd.apply(this,arguments); };"
                + "   window.__csXhrHooked=true; } }");
    }

    public void clearAlerts() { page.evaluate("() => { window.__csAlerts=[]; }"); }

    public List<String> alerts() { return strings(page.evaluate("() => (window.__csAlerts||[]).slice()")); }

    /** Wait up to {@code ms} for a JAlert after the last {@link #clearAlerts()}; returns them joined ("" = none). */
    public String waitForAlert(int ms) {
        try { page.waitForFunction("() => (window.__csAlerts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(ms)); }
        catch (Exception ignore) { }
        return String.join(" | ", alerts());
    }

    public int xhrMark() {
        Object r = page.evaluate("() => (window.__csXhr||[]).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    // ---- request-level write audit (same rules as IpdCharges; classifier shared) ------------------------------

    private final List<String[]> netLog = java.util.Collections.synchronizedList(new ArrayList<>());
    private java.util.function.Consumer<com.microsoft.playwright.Request> netListener;
    private static final java.util.regex.Pattern EXEC_FLAG = java.util.regex.Pattern.compile("(?i)\"?ExecFlag\"?\\s*[:=]\\s*\"?([A-Za-z0-9_]+)");

    /** Record every API/XHR/fetch (and any non-GET) request the page sends, at send time. Idempotent. */
    public void startNetworkAudit() {
        if (netListener != null) return;
        netListener = req -> {
            try {
                String method = req.method() == null ? "" : req.method().toUpperCase();
                String rt = req.resourceType() == null ? "" : req.resourceType();
                String url = req.url() == null ? "" : req.url();
                if (url.startsWith("data:") || url.startsWith("blob:")) return;
                if (!("xhr".equals(rt) || "fetch".equals(rt) || url.contains("/api/") || !"GET".equals(method))) return;
                if (url.matches("(?i).*/api/Auth/getInternalmsgCount(Only)?\\b.*")) return;   // unread-message poll
                String path = url.replaceFirst("^[a-z]+://[^/]+", "");
                String body = "";
                try { body = req.postData() == null ? "" : req.postData(); } catch (Exception ignore) { }
                java.util.regex.Matcher m = EXEC_FLAG.matcher(body.isEmpty() ? path : body);
                netLog.add(new String[]{method, path, m.find() ? m.group(1) : ""});
            } catch (Exception ignore) { }
        };
        page.onRequest(netListener);
    }

    public void stopNetworkAudit() {
        if (netListener == null) return;
        try { page.offRequest(netListener); } catch (Exception ignore) { }
        netListener = null;
    }

    public int networkMark() { return netLog.size(); }

    /** Every request since {@code mark}: "METHOD /path [ExecFlag] -> READ|WRITE|UNCLASSIFIED". */
    public List<String> networkSince(int mark) {
        List<String> out = new ArrayList<>();
        synchronized (netLog) {
            for (int i = Math.max(0, mark); i < netLog.size(); i++) {
                String[] r = netLog.get(i);
                out.add(r[0] + " " + r[1] + (r[2].isEmpty() ? "" : " [" + r[2] + "]") + " -> " + IpdCharges.classifyRequest(r[0], r[1], r[2]));
            }
        }
        return out;
    }

    /** Requests since {@code mark} not confirmed as reads (WRITE or UNCLASSIFIED). Must stay empty. */
    public List<String> writeCallsSince(int mark) {
        List<String> out = new ArrayList<>();
        for (String s : networkSince(mark)) if (!s.endsWith("-> READ")) out.add(s);
        return out;
    }

    // ---- tabs / tables ------------------------------------------------------------------------------------------

    /** Show a charges-area tab by its {@code data-target} (jQuery {@code tab('show')}: a synthetic click is
     *  intercepted by the active pane underneath). */
    public boolean showTab(String pane) {
        Object ok = page.evaluate("(sel) => { const a=document.querySelector('a[data-toggle=tab][data-target=\"'+sel+'\"],a[data-toggle=tab][href=\"'+sel+'\"]');"
                + " if(!a || !window.jQuery) return false; jQuery(a).tab('show'); return true; }", pane);
        waitForAngular(800);
        return Boolean.TRUE.equals(ok);
    }

    /** The 4 charge-area tabs: data-target -> label (with "(hidden)" / "(missing)" markers). */
    public Map<String, Object> chargeTabs() {
        return map(page.evaluate("(panes) => { const o={}; panes.forEach(t=>{ const a=document.querySelector('a[data-toggle=tab][data-target=\"'+t+'\"]');"
                + " o[t]= a ? (a.textContent||'').replace(/\\s+/g,' ').trim() + (a.getBoundingClientRect().width>0?'':' (hidden)') : '(missing)'; }); return o; }",
                List.of(cfg.billPane, cfg.packageServicePane, cfg.packageItemPane, cfg.cancelledPane)));
    }

    /** A plain-table tab: {tabShown, heads, rows, sample, empty}. Cells holding a select/input report their value. */
    public Map<String, Object> readPlainTable(String pane) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tabShown", showTab(pane));
        out.putAll(map(page.evaluate("(sel) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const p=document.querySelector(sel); if(!p) return {heads:[], rows:0, sample:[], empty:''};"
                + " const t=p.querySelector('table'); const heads=t?[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent)).filter(Boolean):[];"
                + " const empty=/No package items for this visit/i.test(p.innerText||'') ? 'No package items for this visit' : '';"
                + " const cell=c=>{ const s=c.querySelector('select'); if(s) return '['+norm((s.selectedOptions[0]||{}).textContent)+']'; const i=c.querySelector('input'); if(i) return i.value; return norm(c.textContent); };"
                + " const rows=t?[...t.querySelectorAll('tbody tr')].filter(r=>r.cells.length>2 && !/No package items/i.test(r.textContent)):[];"
                + " return {heads, rows:rows.length, sample:rows.slice(0,5).map(r=>[...r.cells].map(cell).join(' | ').slice(0,220)), empty}; }", pane)));
        return out;
    }

    // ---- PATIENT DETAILS ----------------------------------------------------------------------------------------

    /** MRN search ({@code SearchPatientByMRNo}). Returns the JAlert raised ("" = loaded quietly). An unknown MRN
     *  clears the loaded patient (the app calls fnClearControls()). */
    public String searchByMrn(String mrn) {
        clearAlerts();
        setNg("PatientDetails.MRNo", mrn == null ? "" : mrn);
        waitForAngular(300);
        clickTagged("() => [...document.querySelectorAll('button')].find(b=>/SearchPatientByMRNo/.test(b.getAttribute('ng-click')||'') && b.getBoundingClientRect().width>0)", "__csMrnSearch", 5000);
        try {
            page.waitForFunction("(mrn) => { " + pdJs + " const p=__pd(); return (window.__csAlerts||[]).length>0 || (mrn && String(p.MRNo)===mrn && Number(p.visitadmissionid||0)>0); }",
                    mrn == null ? "" : mrn, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(2000);
        return String.join(" | ", alerts());
    }

    /** The loaded patient as the screen holds it, plus the banner text above the panel (Name / MRN / Mob / Gen / Age). */
    public Map<String, String> readPatientHeader() {
        Map<String, Object> m = map(page.evaluate("() => { " + pdJs + " const p=__pd(); const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const o={};"
                + " ['MRNo','OPDNo','IPDNo','visitadmissionid','ChargeDate','creditlimit','companyamount','BillNo'].forEach(k=>{ o[k]= p[k]==null ? '' : String(p[k]).trim(); });"
                + " const sel=m=>{ const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e&&e.selectedOptions[0] ? norm(e.selectedOptions[0].textContent) : ''; };"
                + " o.visit=sel('PatientDetails.OPDNo'); o.payor=sel('PatientDetails.ReceivableID');"
                + " const h=[...document.querySelectorAll('.panel-heading')].find(x=>/Patient Details/i.test(x.textContent||'') && x.getBoundingClientRect().width>0);"
                + " o.banner=h?norm(h.textContent):''; return o; }"));
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    // ---- BILL DETAILS -------------------------------------------------------------------------------------------

    /** Charge grid column titles from the ui-grid column definitions (includes columns scrolled off-screen). */
    public List<String> billGridColumns() {
        showTab(cfg.billPane);
        return strings(page.evaluate("(pane) => { let defs=null; document.querySelectorAll(pane+' *').forEach(el=>{ try{ const s=angular.element(el).scope();"
                + "   const c=s&&s.grid&&s.grid.options&&s.grid.options.columnDefs; if(Array.isArray(c)&&(!defs||c.length>defs.length)) defs=c; }catch(e){} });"
                + " return defs ? defs.map(c=>String(c.displayName||c.name||'').replace(/\\s+/g,' ').trim()).filter(Boolean) : []; }", cfg.billPane));
    }

    /** ng-models (and placeholders) of the visible service-entry row. */
    public List<String> serviceEntryFields() {
        return strings(page.evaluate("() => [...document.querySelectorAll(\"input[ng-model^='ServiceData.']\")].filter(e=>e.getBoundingClientRect().width>0)"
                + ".map(e=>e.getAttribute('ng-model').replace('ServiceData.','')+(e.placeholder?' (\"'+e.placeholder.trim()+'\")':''))"));
    }

    /** Bill-level controls: each visible PatientDetails/CashCounter select with its selected text and option count,
     *  and the Charges Date box (value + read-only). */
    public Map<String, Object> billControls() {
        return map(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const o={};"
                + " [...document.querySelectorAll(\"select[ng-model^='PatientDetails.'],select[ng-model='CashCounterID'],select[ng-model='PatientDetails.CashCounterId']\")]"
                + "   .filter(e=>!e.closest('.modal')).forEach(e=>{ const real=[...e.options].filter(x=>x.value && !/^-*\\s*select/i.test(norm(x.textContent))).length;"
                + "     o[e.getAttribute('ng-model')]=(norm((e.selectedOptions[0]||{}).textContent)||'(empty)')+' ['+real+' option(s)]'; });"
                + " const cd=document.querySelector(\"input[ng-model='PatientDetails.ChargeDate']\");"
                + " o.ChargeDate = cd ? (cd.value + ((cd.readOnly||cd.disabled)?' (read-only)':' (editable)')) : '(missing)'; return o; }"));
    }

    /** Get Bill with Bill No. empty; returns the JAlert. */
    public String getBillWithoutNumber() {
        clearAlerts();
        setNg("PatientDetails.BillNo", "");
        waitForAngular(300);
        clickTagged("() => [...document.querySelectorAll('button')].find(b=>/fnGetZTCareBillDetails/.test(b.getAttribute('ng-click')||'') && b.getBoundingClientRect().width>0)", "__csGetBill", 5000);
        return waitForAlert(6000);
    }

    /** Enter in Quantity with Service Name empty; returns the JAlert. Adds nothing (anything that appears is left
     *  untagged so cleanup's before/after comparison reports it). */
    public String addServiceWithNoName() {
        clearAlerts();
        setNg("ServiceData.ServiceName", "");
        setNg("ServiceData.ServiceCode", "");
        waitForAngular(300);
        commitServiceEntry();
        return waitForAlert(6000);
    }

    /** Charge rows: code, name, chargeid, total, ispackage, ishealthplan, paired, addedByTest. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridRows() {
        Object r = page.evaluate("() => { " + rowsJs + " return __rows().map(x=>({ code:__code(x), name:__name(x), chargeid:Number(x.chargeid||0),"
                + " total:parseFloat(x.totalamount)||0, ispackage:!!x.ispackage, ishealthplan:!!x.ishealthplan,"
                + " paired:Number(x.configsamplecollectibleserviceid||0), addedByTest:!!x.__csAddedByTest })); }");
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** "CODE#chargeid" per charge row, in grid order — the before/after fingerprint. */
    public List<String> gridFingerprint() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : gridRows()) out.add(r.get("code") + "#" + ((Number) r.get("chargeid")).longValue());
        return out;
    }

    /** Footer totals as the screen holds them, plus the arithmetic sum of the rows' Total Amount. */
    public Map<String, String> totals() {
        Map<String, Object> m = map(page.evaluate("() => { " + rowsJs + " const p=__pd(); const f=v=>v==null?'':String(v);"
                + " const sum=__rows().reduce((a,x)=>a+(parseFloat(x.totalamount)||0),0);"
                + " return { TotalAmount:f(p.TotalAmount), TotalDiscount:f(p.TotalConcessionAmount), SelfAmount:f(p.SelfPayAmount),"
                + "   CompanyAmount:f(p.ReceivalbePayAmount), NetAmount:f(p.TotalNetAmount), RowSum:sum.toFixed(2), Rows:String(__rows().length) }; }"));
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    // ---- service entry (AngularJS auto-complete li.auto-complete-item) -------------------------------------------

    /** Type {@code q} into Service Name (real keystrokes); return the results: {label, code, name, ispackage,
     *  ishealthplan, rate}. Empty when nothing matched within 8s. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchServices(String q) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Locator svc = page.locator("#txtServiceName:visible").first();
            svc.scrollIntoViewIfNeeded();
            svc.click(new Locator.ClickOptions().setTimeout(4000));
            svc.fill("");
            svc.pressSequentially(q, new Locator.PressSequentiallyOptions().setDelay(120));
        } catch (Exception e) { System.out.println("searchServices: typing '" + q + "' failed - " + e.getClass().getSimpleName()); return out; }
        try {
            page.waitForFunction("(q) => [...document.querySelectorAll('li.auto-complete-item')].some(li=>li.getBoundingClientRect().width>0 && (li.textContent||'').toLowerCase().includes(q.toLowerCase()))",
                    q, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { return out; }
        waitForAngular(400);
        Object r = page.evaluate("() => [...document.querySelectorAll('li.auto-complete-item')].filter(li=>li.getBoundingClientRect().width>0).map(li=>{"
                + " let it=null; try{ const s=angular.element(li).scope(); it=s&&s.item ? (s.item.data||s.item) : null; }catch(e){}"
                + " return { label:(li.textContent||'').replace(/\\s+/g,' ').trim(), code: it?String(it.ServiceCode||''):'', name: it?String(it.ServiceName||''):'',"
                + "   ispackage: !!(it&&it.ispackage), ishealthplan: !!(it&&it.ishealthplan), rate: it&&it.rate!=null ? String(it.rate) : '' }; })");
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Real click on the visible auto-complete item {@code idx}; true when it filled ServiceData.ServiceName. */
    public boolean clickAutocompleteItem(int idx) {
        Object ok = page.evaluate("(i) => { const li=[...document.querySelectorAll('li.auto-complete-item')].filter(e=>e.getBoundingClientRect().width>0)[i];"
                + " if(!li) return false; li.id='__csAcItem'; return true; }", idx);
        if (!Boolean.TRUE.equals(ok)) return false;
        try { page.locator("#__csAcItem").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("clickAutocompleteItem: " + e.getClass().getSimpleName()); }
        page.evaluate("() => { const e=document.getElementById('__csAcItem'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        Object name = page.evaluate("() => { try{ const sd=angular.element(document.querySelector('#txtServiceName')).scope().ServiceData; return sd&&sd.ServiceName ? String(sd.ServiceName) : ''; }catch(e){ return ''; } }");
        return name != null && !name.toString().isEmpty();
    }

    /** Enter in the visible Quantity box — the screen's own "add this line" ({@code QuantityblurEvent}). */
    public void commitServiceEntry() {
        try { page.locator("input[ng-model='ServiceData.quantity']:visible").first().press("Enter"); }
        catch (Exception e) { System.out.println("commitServiceEntry: " + e.getClass().getSimpleName()); }
        waitForAngular(1200);
    }

    private void markBeforeAdd() { page.evaluate("() => { " + rowsJs + " window.__csPre=__rows().slice(); }"); }

    /** Tag rows that are new since {@link #markBeforeAdd()} AND carry {@code code} as test-owned; returns them. */
    private List<String> markRowsAddedByTest(String code) {
        return strings(page.evaluate("(code) => { " + rowsJs + " const pre=window.__csPre||null; if(!pre || !code) return [];"
                + " const fresh=__rows().filter(x=>!pre.includes(x) && __code(x).toLowerCase()===code.toLowerCase());"
                + " fresh.forEach(x=>{ x.__csAddedByTest=true; }); return fresh.map(__code); }", code == null ? "" : code));
    }

    // ---- confirm dialogs (only ever answered "No") --------------------------------------------------------------

    public String confirmText() {
        Object r = page.evaluate("() => { " + CONFIRM_JS + " const b=__confirm(); return b ? (b.textContent||'').replace(/\\s+/g,' ').replace(/^\\u00D7\\s*/,'').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click "No" on the visible confirm. (Deliberately no way to click anything else.) */
    public boolean answerConfirmNo() {
        Object ok = page.evaluate("() => { " + CONFIRM_JS + " const b=__confirm(); if(!b) return false;"
                + " const btn=[...b.querySelectorAll('button')].find(x=>x.getBoundingClientRect().width>0 && (x.textContent||'').trim().toLowerCase()==='no');"
                + " if(!btn) return false; btn.id='__csConfirmNo'; return true; }");
        if (!Boolean.TRUE.equals(ok)) return false;
        try { page.locator("#__csConfirmNo").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("answerConfirmNo: " + e.getClass().getSimpleName()); }
        page.evaluate("() => { const e=document.getElementById('__csConfirmNo'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        return true;
    }

    // ---- PACKAGE SERVICE ----------------------------------------------------------------------------------------

    /** Outcome of adding a service picked by flag. {@code code} "" = no search result carried the flag. */
    public static final class AddResult {
        public String code = "", name = "", rate = "", query = "", alert = "", dialog = "";
        public List<String> added = new ArrayList<>();
        public List<String> searched = new ArrayList<>();
        public int rowsBefore, rowsAfter;
    }

    /**
     * For each query, search Service Name and pick the first result whose {@code flag} ("ispackage" /
     * "ishealthplan") is true (and, when {@code needRate}, whose rate is above 0); press Enter to add it (no Save).
     * Records any JAlert, answers any confirm with No, and tags the new row(s) as test-owned.
     */
    public AddResult addFirstFlagged(String flag, boolean needRate, String... queries) {
        AddResult res = new AddResult();
        for (String q : queries) {
            List<Map<String, Object>> items = searchServices(q);
            res.searched.add(q + " -> " + items.size() + " result(s)");
            int pick = -1;
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> it = items.get(i);
                double rate = 0;
                try { rate = Double.parseDouble(String.valueOf(it.get("rate"))); } catch (Exception ignore) { }
                if (Boolean.TRUE.equals(it.get(flag)) && (!needRate || rate > 0)) { pick = i; break; }
            }
            if (pick < 0) continue;
            Map<String, Object> it = items.get(pick);
            if (!clickAutocompleteItem(pick)) continue;
            res.code = String.valueOf(it.get("code")); res.name = String.valueOf(it.get("name"));
            res.rate = String.valueOf(it.get("rate")); res.query = q;
            res.rowsBefore = gridRows().size();
            markBeforeAdd();
            clearAlerts();
            commitServiceEntry();
            try {
                page.waitForFunction("(n) => { " + rowsJs + CONFIRM_JS + " return __rows().length>n || !!__confirm() || (window.__csAlerts||[]).length>0; }",
                        res.rowsBefore, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { }
            waitForAngular(1500);
            res.alert = String.join(" | ", alerts());
            String c = confirmText();
            if (!c.isEmpty()) { res.dialog = c; answerConfirmNo(); }
            res.added = markRowsAddedByTest(res.code);
            res.rowsAfter = gridRows().size();
            return res;
        }
        return res;
    }

    /**
     * After an add, wait up to {@code ms} for the Package Service break-up and describe it: entries in the
     * breakdown model with their service lines (code, name, qty, rate, total, net), visible table rows, and what
     * the break-up lookups returned since {@code xhrMark}.
     */
    public Map<String, Object> packageBreakdown(int xhrMark, int ms) {
        try {
            page.waitForFunction("(a) => { " + pdJs + " return (__sc()[a.m]||[]).some(e=>(e[a.l]||[]).length>0); }",
                    Map.of("m", cfg.breakdownModel, "l", cfg.breakdownLines), new Page.WaitForFunctionOptions().setTimeout(ms));
        } catch (Exception ignore) { }
        Map<String, Object> m = map(page.evaluate("(a) => { " + pdJs + " const s=__sc(); const o={}; const list=s[a.m]||[];"
                + " o.entries=list.map(e=>String(e[a.n]||e.PackageName||'')+' ('+(e[a.l]||[]).length+' line(s))');"
                + " o.lines=[]; list.forEach(e=>(e[a.l]||[]).forEach(l=>o.lines.push({ code:String(l.ServiceCode||l.servicecode||''), name:String(l.ServiceName||l.servicename||''),"
                + "   qty:parseFloat(l.quantity!=null?l.quantity:l.Quantity)||0, rate:parseFloat(l.rate)||0, total:parseFloat(l.totalamount)||0, net:parseFloat(l.netamount)||0,"
                + "   discount:parseFloat(l.concessionamount)||0, tax:parseFloat(l.taxamount)||0 })));"
                + " o.itemLines=(s.PackageList||[]).reduce((n,p)=>n+(p.PackageItems||[]).length,0);"
                + " o.lookups=(window.__csXhr||[]).slice(a.x).filter(x=>/GetPackage|HealthPlan/i.test(x.flag)).map(x=>x.flag+' -> '+(x.n<0?'?':x.n+' row(s)'));"
                + " return o; }", Map.of("m", cfg.breakdownModel, "l", cfg.breakdownLines, "n", cfg.breakdownName, "x", xhrMark)));
        Map<String, Object> t = readPlainTable(cfg.packageServicePane);
        m.put("tableRows", t.get("rows"));
        m.put("tableSample", t.get("sample"));
        showTab(cfg.billPane);
        return m;
    }

    /** Add {@code code} again and answer the duplicate confirm with No. {dialog, answeredNo, rowsBefore, rowsAfter}. */
    public Map<String, Object> addDuplicateAndAnswerNo(String code) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rowsBefore", gridRows().size());
        List<Map<String, Object>> items = searchServices(code);
        int pick = -1;
        for (int i = 0; i < items.size(); i++) if (code.equalsIgnoreCase(String.valueOf(items.get(i).get("code")))) { pick = i; break; }
        if (pick < 0 || !clickAutocompleteItem(pick)) {
            out.put("dialog", ""); out.put("answeredNo", false); out.put("rowsAfter", gridRows().size());
            out.put("note", code + " not found again in the search");
            return out;
        }
        markBeforeAdd();
        commitServiceEntry();
        try { page.waitForFunction("() => { " + CONFIRM_JS + " return !!__confirm(); }", null, new Page.WaitForFunctionOptions().setTimeout(8000)); }
        catch (Exception ignore) { }
        String dialog = confirmText();
        out.put("dialog", dialog);
        out.put("answeredNo", !dialog.isEmpty() && answerConfirmNo());
        waitForAngular(800);
        markRowsAddedByTest(code);   // a duplicate that got in anyway is test-owned and removed by cleanup
        out.put("rowsAfter", gridRows().size());
        return out;
    }

    /** Number of entries in the breakdown model (e.g. HealthPlans) — for before/after cleanup checks. */
    public int breakdownEntries() {
        Object r = page.evaluate("(m) => { " + pdJs + " return (__sc()[m]||[]).length; }", cfg.breakdownModel);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    // ---- CLEANUP ------------------------------------------------------------------------------------------------

    /**
     * Remove ONLY rows this helper tagged as test-added that are still unsaved ({@code chargeid==0}) and unpaired
     * ({@code configsamplecollectibleserviceid==0}), with the row's own Delete button (a real click). Anything else
     * is refused and listed. The conditions are re-checked in the page immediately before each click.
     * Returns {removed, refused, remaining}.
     */
    public Map<String, Object> removeRowsAddedByTest() {
        List<String> removed = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        try { showTab(cfg.billPane); } catch (Exception ignore) { }
        try { answerConfirmNo(); } catch (Exception ignore) { }
        for (int guard = 0; guard < 15; guard++) {
            Map<String, Object> t = map(page.evaluate("(fn) => { " + rowsJs
                    + " const mine=__rows().filter(x=>x.__csAddedByTest);"
                    + " const refused=mine.filter(x=>Number(x.chargeid||0)!==0 || Number(x.configsamplecollectibleserviceid||0)>0)"
                    + "   .map(x=>__code(x)+(Number(x.chargeid||0)!==0?' (posted)':' (paired with another row)'));"
                    + " const x=mine.find(r=>Number(r.chargeid||0)===0 && Number(r.configsamplecollectibleserviceid||0)===0); if(!x) return {code:'', refused};"
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(r=>{ try{ return angular.element(r).scope().row.entity===x; }catch(e){ return false; } });"
                    + " const btn=rows.map(r=>r.querySelector('[ng-click*=\"'+fn+'\"]')).find(b=>b && b.getBoundingClientRect().width>0);"
                    + " if(!btn) return {code:__code(x), how:'none', refused};"
                    + " window.__csTarget=x; btn.id='__csDelRow'; return {code:__code(x), how:'button', refused}; }", cfg.deleteFn));
            Object rf = t.get("refused");
            if (rf instanceof List) for (Object o : (List<?>) rf) if (!refused.contains(String.valueOf(o))) refused.add(String.valueOf(o));
            String code = String.valueOf(t.getOrDefault("code", ""));
            if (code.isEmpty()) break;
            if (!"button".equals(t.get("how"))) {
                refused.add(code + " (Delete button not found/visible - left in place; it is unsaved and disappears when the screen is left)");
                page.evaluate("() => { " + rowsJs + " __rows().forEach(x=>{ if(x.__csAddedByTest && Number(x.chargeid||0)===0) x.__csAddedByTest=false; }); }");
                continue;
            }
            try {
                page.locator("#__csDelRow").scrollIntoViewIfNeeded();
                page.locator("#__csDelRow").click(new Locator.ClickOptions().setTimeout(4000));
            } catch (Exception e) { System.out.println("removeRowsAddedByTest: click failed - " + e.getClass().getSimpleName()); }
            page.evaluate("() => { const e=document.getElementById('__csDelRow'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1000);
            String c = confirmText();
            if (!c.isEmpty()) { System.out.println("removeRowsAddedByTest: unexpected confirm '" + c + "' - answered No"); answerConfirmNo(); refused.add(code + " (confirm appeared: " + c + ")"); break; }
            // Did the row actually go? If not, stop targeting it (untag) and say so, rather than retrying blindly.
            Object gone = page.evaluate("() => { " + rowsJs + " const x=window.__csTarget; const still=__rows().includes(x);"
                    + " if(still && x) x.__csAddedByTest=false; window.__csTarget=null; return !still; }");
            if (Boolean.TRUE.equals(gone)) removed.add(code);
            else refused.add(code + " (Delete clicked but the row is still there)");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", removed);
        out.put("refused", refused);
        out.put("remaining", gridFingerprint());
        return out;
    }
}
