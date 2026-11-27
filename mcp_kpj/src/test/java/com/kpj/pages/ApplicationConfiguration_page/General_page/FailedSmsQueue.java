package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Application Configuration &gt; General &gt; <b>FailedSmsQueue</b> — Page Object (route {@code #/FailedSmsQueue}, controller
 * {@code App/GeneralConfig/controller/FailedSmsQueueController.js}, factory {@code SmsLog.Factory.js}).
 *
 * <p>Confirmed live on devhis (v2026.11.27.558.1):</p>
 * <ul>
 *   <li>Header: "SMS Service: ON|OFF" with the master switch ({@code fnSetSmsService(false|true)}, asks YES / NO in an ngConfirm dialog, then
 *       {@code POST /api/SmsLog/SetSmsService}), the iSMS gateway status line, Refresh ({@code fnRefresh()}).</li>
 *   <li>Queue ({@code POST /api/SmsLog/Fetch {ExecFlag:QUEUE,pageno,pagesize:50}}): Resend Selected / Discard Selected
 *       ({@code fnResend|fnDiscard(fnSelected())}), Select All ({@code AllChecked}), a table Timestamp | Notification Type | Recipient | Message
 *       Body (link {@code fnView(row)} → modal {@code #FailedSmsMessageModal}) | Template ID | Error Code | Error Description | Retry Count | Status |
 *       Operator Action (Resend {@code fnResend([row])} — sends at once, no confirmation; Discard {@code fnDiscard([row])} — asks YES / NO), paging
 *       « Prev / Next » ({@code fnLoad(PageNo ∓ 1)}) and "page x of y".</li>
 *   <li>Settings: Subscription End Date ({@code Settings.subscriptionenddate}, date input), Reminder Days ({@code Settings.reminderdays}), Ops Alert
 *       E-mails ({@code Settings.opsalertemails}), Save ({@code fnSaveSettings()}: checks each e-mail and the "30,14,7,1" format, then
 *       {@code POST /api/SmsLog/SaveSettings}).</li>
 * </ul>
 *
 * <p>Every write call ({@code /api/SmsLog/IUD|SaveSettings|SetSmsService}) is recorded in {@link #writes} so a test can prove a cancelled
 * action sent nothing.</p>
 */
public class FailedSmsQueue extends BasePage {

    public static final String ROUTE = "#/FailedSmsQueue";

    /** Write calls seen: "METHOD path". */
    public final List<String> writes = new ArrayList<>();
    private final Consumer<Request> listener = q -> {
        String u = q.url().toLowerCase();
        if (u.contains("/api/smslog/iud") || u.contains("/api/smslog/savesettings") || u.contains("/api/smslog/setsmsservice"))
            synchronized (writes) { writes.add(q.method() + " " + q.url().replaceAll("^https?://[^/]+", "")); }
    };

    public FailedSmsQueue(Page page) {
        super(page);
        page.onRequest(listener);
    }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const sc=()=>{ const e=document.querySelector(\"[ng-model='Settings.reminderdays']\"); return e?angular.element(e).scope():null; };"
          + "const main=()=>document.querySelector('.content-wrapper')||document.body;";

    public final List<String> navNotes = new ArrayList<>();
    public long lastLoadMs = -1;

    // ---- navigation ------------------------------------------------------------------------------------------------

    public boolean navigateViaMenu() {
        navNotes.clear();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').trim()==='Application Configuration' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(45000));
        } catch (Exception e) { navNotes.add("sidebar 'Application Configuration' not shown within 45 s"); return false; }
        long t0 = System.currentTimeMillis();
        for (int attempt = 0; attempt < 3 && !linkVisible(); attempt++) {
            expandMenuEntry(null, "^Application Configuration$");
            page.waitForTimeout(1500);
            expandMenuEntry("^Application Configuration$", "^General$");
            page.waitForTimeout(1500);
        }
        try {
            Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
            link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            link.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            navNotes.add("'FailedSmsQueue' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
            if (!onScreen()) return false;
        }
        boolean ok = waitLoaded();
        lastLoadMs = System.currentTimeMillis() - t0;
        return ok;
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    public boolean waitLoaded() {
        try {
            page.waitForFunction("() => /#\\/FailedSmsQueue/i.test(location.href) && [...document.querySelectorAll('button')].some(b=>b.getAttribute('ng-click')==='fnRefresh()' && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("screen not ready within 30 s (" + page.url() + ")"); return false; }
        return waitForHttpIdle(60000);
    }

    public boolean onScreen() { return page.url().matches("(?i).*#/FailedSmsQueue/?$"); }

    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { return angular.element(document.body).injector().get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    /** Header, gateway, paging and settings as the screen holds them. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> state() {
        Object r = page.evaluate("() => {" + JS + " const s=sc()||{}; const t=norm(main().innerText); const g=s.Gateway||{}; const st=s.Settings||{};"
                + " const sm=t.match(/SMS Service:\\s*(ON|OFF)/i); const gw=t.match(/iSMS gateway:[^.]*\\./i); const pg=t.match(/page\\s*(\\d+)\\s*of\\s*(\\d+)/i);"
                + " const prev=[...document.querySelectorAll('button')].find(b=>(b.getAttribute('ng-click')||'')==='fnLoad(PageNo - 1)');"
                + " const next=[...document.querySelectorAll('button')].find(b=>(b.getAttribute('ng-click')||'')==='fnLoad(PageNo + 1)');"
                + " return { service: sm?sm[1].toUpperCase():'', gateway: gw?gw[0]:'', smsenabled: !!g.smsenabled, paused: !!g.ispaused, subscriptionEnd: g.subscriptionenddate||'',"
                + " pageNo: s.PageNo||0, totalPages: s.TotalPages||0, totalCount: s.TotalCount||0, rows:(s.Rows||[]).length, pageText: pg?pg[0]:'',"
                + " prevDisabled: prev?prev.disabled:null, nextDisabled: next?next.disabled:null,"
                + " reminderDays: st.reminderdays||'', emails: st.opsalertemails||'', endDate: String(st.subscriptionenddate||'') }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const t=[...main().querySelectorAll('table')].find(vis); return t?[...t.querySelectorAll('th')].map(h=>norm(h.innerText)).filter(Boolean):[]; }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Fields of the queue rows on the current page. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rows() {
        Object r = page.evaluate("() => {" + JS + " const s=sc()||{}; const t=[...main().querySelectorAll('table')].find(vis);"
                + " const hd=t?[...t.querySelectorAll('th')].map(h=>norm(h.innerText)):[]; const trs=t?[...t.querySelectorAll('tbody tr')]:[];"
                + " const cell=(i,name)=>{ const k=hd.indexOf(name); const tr=trs[i]; return tr&&k>=0&&tr.children[k]?norm(tr.children[k].innerText):''; };"
                + " return (s.Rows||[]).map((x,i)=>({ id:x.id, type:x.notificationtype, recipient:x.recipients, template:x.templateid, errorcode:x.responsecode, error:x.errordescription||'',"
                + " attempts:x.attemptcount, shownRetry:cell(i,'Retry Count'), status:x.status||'', message:(x.message||'').slice(0,200), checked:!!x.checked })); }");
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> buttons() {
        Object r = page.evaluate("() => {" + JS + " const o={}; [...main().querySelectorAll('button')].filter(vis).filter(b=>!b.closest('table') && !b.closest('.modal') && norm(b.textContent))"
                + " .forEach(b=>o[norm(b.textContent)]=b.getAttribute('ng-click')||''); return o; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    // ---- actions -----------------------------------------------------------------------------------------------------

    public boolean clickNg(String ngClick) {
        Locator b = page.locator(".content-wrapper button[ng-click='" + ngClick + "']:visible").first();
        try { b.scrollIntoViewIfNeeded(); b.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(600);
        waitForHttpIdle(60000);
        waitForAngular(300);
        return true;
    }

    /** Click the n-th row's control with the given ng-click (e.g. "fnDiscard([row])", "fnView(row)"). */
    public boolean clickRowControl(String ngClick, int n) {
        Locator c = page.locator(".content-wrapper [ng-click='" + ngClick + "']").nth(n);
        try { c.scrollIntoViewIfNeeded(); c.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(800);
        return true;
    }

    public void toggleAll(boolean on) {
        Locator c = page.locator(".content-wrapper input[ng-model='AllChecked']").first();
        if (c.isChecked() != on) c.click();
        waitForAngular(500);
    }

    // ---- ngConfirm dialog / message modal ----------------------------------------------------------------------------

    /** Text of the open ngConfirm dialog ("" when none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.ng-confirm')].filter(vis).pop(); return d?norm(d.innerText):''; }");
        return r == null ? "" : r.toString();
    }

    public boolean waitConfirm(int ms) {
        try { page.waitForFunction("() => {" + JS + " return [...document.querySelectorAll('.ng-confirm')].some(vis); }", null, new Page.WaitForFunctionOptions().setTimeout(ms)); return true; }
        catch (Exception e) { return false; }
    }

    /** Answer the open ngConfirm dialog: "YES" or "NO". */
    public boolean answerConfirm(String button) {
        Locator b = page.locator(".ng-confirm button").filter(new Locator.FilterOptions().setHasText(button)).last();
        try { b.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(800);
        waitForHttpIdle(30000);
        return true;
    }

    public boolean messageModalOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " const m=document.getElementById('FailedSmsMessageModal'); return !!m && vis(m) && getComputedStyle(m).display!=='none'; }"));
    }

    public String messageModalText() {
        Object r = page.evaluate("() => {" + JS + " const m=document.getElementById('FailedSmsMessageModal'); return m?norm(m.innerText):''; }");
        return r == null ? "" : r.toString();
    }

    public void closeMessageModal() {
        Locator b = page.locator("#FailedSmsMessageModal button[ng-click='fnCloseView()']").filter(new Locator.FilterOptions().setHasText("Close")).first();
        try { b.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { page.evaluate("() => { try{ $('#FailedSmsMessageModal').modal('hide'); }catch(x){} }"); }
        waitForAngular(800);
    }

    // ---- settings ------------------------------------------------------------------------------------------------------

    public void setSetting(String field, String value) {
        Locator box = page.locator(".content-wrapper [ng-model='Settings." + field + "']").first();
        box.fill(value);
        box.dispatchEvent("input");
        box.dispatchEvent("change");
        waitForAngular(300);
    }

    // ---- alerts -------------------------------------------------------------------------------------------------------

    public void spyAlerts() {
        page.evaluate("() => { if(!window.__fsqAlerts) window.__fsqAlerts=[]; const o=window.__fsqOrigJAlert||window.JAlert; window.__fsqOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__fsqAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__fsqAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public int writeCount() { synchronized (writes) { return writes.size(); } }
}
