package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Receive Return Item</b> — Page Object (list {@code #/ReceiveReturnItemListCSSD}, form {@code #/add-ReceiveReturnItem}).
 *
 * <p>Confirmed live (devhis build v2026.11.15.469). Same shape as {@link ReceiveIssueItem}. The list screen (From / To Store, dates, Status, Search, grids) has <b>New</b>
 * ({@code AddReceiveReturnItem()}), which opens the form:</p>
 * <ul>
 *   <li><b>Received Date</b> ({@code ReceiveReturnItem.ReceivedDate}, a 720kb date picker, today at first - the day has to be clicked in the calendar). It is NOT sent to the search.</li>
 *   <li><b>Received From</b> ({@code ReceiveReturnItem.FromStore}) and <b>Received To</b> ({@code ReceiveReturnItem.ToStore}): selecting Received To runs the search BY ITSELF ({@code ng-change ReturndFrom()});
 *       the Search button ({@code ReturndFrom()}) runs the same call: {@code POST /api/ReturnItem/Fetch {fromstoreid, tostoreid, ExecFlag GetReturndItemSearch}} -&gt; the returns still waiting to be received
 *       (ui-grid {@code #grid1}: Return Number | Return Date | From Store Name | To Store Name | Total Amount | Total Selling Price | Received By | Remarks).</li>
 *   <li>Clicking a row ({@code getReturnItemdetails(row)}) fetches its items ({@code ExecFlag ReceiveReturnItemList, returnid}) into the table below (Sr. No. | Item Code | Item Name | Batch Code | Expiry Date |
 *       Return Quantity | Transit Quantity | <b>Received Quantity</b> (an input, <b>0 at first</b>) | Purchase Rate | Total Amount | Selling Price | Total Selling Price).</li>
 *   <li><b>Remarks</b> ({@code ReceiveReturnItem.Remark}), <b>Save</b> ({@code IUDSave()}): every Received Quantity above 0; {@code ExecFlag ReceiveReturnItem} -&gt; ResultStatus 1 -&gt; the report opens at once in
 *       another tab, toast "Receive Return Item added successfully", and the screen goes to the Inventory list {@code #/ReceiveReturnItemList}.</li>
 * </ul>
 *
 * <p>Saving is REAL: the return is received into the store and stops being listed as pending.</p>
 */
public class ReceiveReturnItem extends BasePage {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            String post = r.request().postData() == null ? "" : r.request().postData();
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && post.contains("\"ExecFlag\":\"ReceiveReturnItem\"");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 1200 ? body.substring(0, 1200) : body, post};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public ReceiveReturnItem(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not do (empty = it held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastToast = "", lastSaveMessage = "", lastResponseId = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const bodyRows=()=>[...document.querySelectorAll('#grid1 .ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')];"
          + "const itemTbl=()=>[...document.querySelectorAll('table')].find(x=>vis(x) && /Transit\\s+Quantity/.test(x.textContent));"
          + "const scp=()=>{ const e=document.querySelector(\"select[ng-model='ReceiveReturnItem.FromStore']\"); return e? angular.element(e).scope() : null; };";

    private static final String PARENT = "Ancillary Services", GROUP = "CSSD";

    // ---- navigation ------------------------------------------------------------------------------------------

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

    /** Tag the "Receive Return Item" link INSIDE the CSSD group (exact text; Inventory has its own entry with a similar route). */
    private boolean tagLink() {
        Object r = page.evaluate("() => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='Ancillary Services'); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const g=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)==='CSSD'); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>norm(x.textContent)==='Receive Return Item'); if(!l) return false; l.id='__rrLink'; return true; }");
        return Boolean.TRUE.equals(r);
    }

    private boolean tagLinkProbe() {
        boolean t = tagLink();
        page.evaluate("() => { const e=document.getElementById('__rrLink'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagLinkProbe()) return true; page.waitForTimeout(250); }
        return tagLinkProbe();
    }

    /** Ancillary Services &gt; CSSD &gt; Receive Return Item. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagLinkProbe()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagLinkProbe()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (tagLink()) {
                try { page.locator("#__rrLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CSSD ReceiveReturnItem.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__rrLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !newButtonShown(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    private boolean newButtonShown() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && (b.getAttribute('ng-click')||'').indexOf('AddReceiveReturnItem')===0); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/receivereturnitemlistcssd/?$") && newButtonShown(); }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"input[ng-model='ReceiveReturnItem.ReceivedDate']\"); return !!e && vis(e); }");
        return Boolean.TRUE.equals(r) && page.url().toLowerCase().contains("#/add-receivereturnitem");
    }

    /** Click New and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        try { page.locator("button[ng-click^='AddReceiveReturnItem']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("New could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !formOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!formOpen()) problems.add("the Receive Return Item form did not open (" + page.url() + ")");
        return ok();
    }

    // ---- Received Date (720kb date picker) -----------------------------------------------------------------------------

    private String model(String sel, String expr) {
        Object r = page.evaluate("(a) => { try { const e=document.querySelector(a[0]); const sc=angular.element(e).scope(); const v=sc.$eval(a[1]); return v==null? '' : String(v); } catch(x) { return ''; } }", Arrays.asList(sel, expr));
        return r == null ? "" : r.toString();
    }

    /** What the Received Date box shows. */
    public String receivedDateBox() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='ReceiveReturnItem.ReceivedDate']\"); return e? e.value : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The date the Received Date box holds at first (today on the application's clock); today on the browser's clock when it cannot be read. */
    public LocalDate defaultDate() {
        try { return LocalDate.parse(receivedDateBox(), DMY); } catch (Exception e) { return LocalDate.now(); }
    }

    private boolean calendarOpen(String sel) {
        Object r = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const c=w && w.querySelector('._720kb-datepicker-calendar'); return !!c && vis(c) && [...c.querySelectorAll('._720kb-datepicker-calendar-day')].some(vis); }", sel);
        return Boolean.TRUE.equals(r);
    }

    private int[] shownMonth(String sel) {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const h=w && w.querySelector('._720kb-datepicker-calendar-header-middle'); return h? h.textContent : ''; }", sel);
        String t = r == null ? "" : r.toString();
        Matcher y = Pattern.compile("(\\d{4})").matcher(t);
        if (!y.find()) return null;
        for (int i = 0; i < 12; i++) if (t.contains(MONTHS[i])) return new int[]{Integer.parseInt(y.group(1)), i + 1};
        return null;
    }

    /** Enter the Received Date through its calendar (Prev / Next links to the month, then the day) and check the box and the model hold it. */
    public boolean enterReceivedDate(LocalDate target) {
        problems.clear();
        String ng = "ReceiveReturnItem.ReceivedDate";
        String sel = "input[ng-model='" + ng + "']";
        try { page.locator(sel).first().click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the Received Date box could not be clicked"); return false; }
        for (int i = 0; i < 20 && !calendarOpen(sel); i++) page.waitForTimeout(250);
        if (!calendarOpen(sel)) { problems.add("the Received Date calendar did not open"); return false; }
        for (int step = 0; step < 36; step++) {
            int[] my = shownMonth(sel);
            if (my == null) { problems.add("the month of the calendar could not be read"); return false; }
            int diff = (target.getYear() - my[0]) * 12 + (target.getMonthValue() - my[1]);
            if (diff == 0) break;
            String link = diff < 0 ? "prevMonth()" : "nextMonth()";
            Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); const l=w && w.querySelector('a[ng-click=\"'+a[1]+'\"]'); if(!l) return false; l.id='__rrNav'; return true; }", Arrays.asList(sel, link));
            if (!Boolean.TRUE.equals(tagged)) { problems.add("the calendar has no " + link + " link"); return false; }
            try { page.locator("#__rrNav").click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("the calendar's " + link + " link could not be clicked"); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__rrNav'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(500);
        }
        int[] now = shownMonth(sel);
        if (now == null || now[0] != target.getYear() || now[1] != target.getMonthValue()) { problems.add("the calendar did not reach " + MONTHS[target.getMonthValue() - 1] + " " + target.getYear()); return false; }
        // the days of the neighbouring months that fill the grid share their numbers with the real ones: a day up to the 15th is the FIRST cell with that number, a later day the LAST
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; const x=(parseInt(a[1],10)<=15)? d[0] : d[d.length-1]; x.id='__rrDay'; return true; }", Arrays.asList(sel, String.valueOf(target.getDayOfMonth())));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + target.getDayOfMonth() + " is not offered by the calendar"); return false; }
        try { page.locator("#__rrDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + target.getDayOfMonth() + " could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__rrDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String want = target.format(DMY), box = receivedDateBox(), mod = model(sel, ng);
        if (!want.equals(box)) problems.add("the Received Date box shows \"" + box + "\" instead of \"" + want + "\"");
        else if (!want.equals(mod)) problems.add("the Received Date is not held by the model (\"" + mod + "\")");
        if (calendarOpen(sel)) dismissDatePicker();
        return ok();
    }

    // ---- stores (Received To runs the search) ------------------------------------------------------------------------------------

    /** The real options (placeholder excluded) of a select. */
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private String selectStore(String ngModel, String what, String want, boolean expectSearch) {
        problems.clear();
        List<String> all = options(ngModel);
        String pick = null;
        for (String a : all) if (a.equalsIgnoreCase(want)) { pick = a; break; }
        if (pick == null) { problems.add("\"" + want + "\" is not offered by " + what + " (" + all.size() + " options)"); return ""; }
        final String label = pick;
        Runnable select = () -> page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label), new Locator.SelectOptionOptions().setTimeout(8000));
        try {
            if (expectSearch) {
                try {
                    page.waitForResponse(x -> x.url().contains("/ReturnItem/Fetch") && String.valueOf(x.request().postData()).contains("GetReturndItemSearch"), new Page.WaitForResponseOptions().setTimeout(30000), select::run);
                } catch (Exception e) { problems.add("selecting " + what + " \"" + label + "\" ran no search: " + e.getMessage().split("\n")[0]); return ""; }
            } else select.run();
        } catch (Exception e) { problems.add("\"" + label + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        waitForAngular(900);
        Object shown = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; }", ngModel);
        String mv = model("select[ng-model='" + ngModel + "']", ngModel);
        if (shown == null || !label.equals(shown.toString())) { problems.add(what + " shows \"" + shown + "\" instead of \"" + label + "\""); return ""; }
        if (mv.isEmpty() || mv.equals("0")) { problems.add("the screen's model " + ngModel + " is empty after selecting \"" + label + "\""); return ""; }
        return label;
    }

    /** The store id the screen's model holds for Received From ("from") or Received To ("to"), -1 when it holds none. */
    public int storeId(String which) {
        String ng = "from".equals(which) ? "ReceiveReturnItem.FromStore" : "ReceiveReturnItem.ToStore";
        try { return Integer.parseInt(model("select[ng-model='" + ng + "']", ng).replaceAll("[^0-9]", "")); } catch (Exception e) { return -1; }
    }

    /** Select Received From (a real selection; the model is checked). Returns the store, "" on failure. */
    public String selectReceivedFrom(String store) { return selectStore("ReceiveReturnItem.FromStore", "Received From", store, false); }

    /** Select Received To (a real selection; the model is checked) - the screen searches by itself on this change. Returns the store, "" on failure. */
    public String selectReceivedTo(String store) {
        String s = selectStore("ReceiveReturnItem.ToStore", "Received To", store, true);
        for (int i = 0; i < 40 && s.length() > 0 && transfers().isEmpty(); i++) page.waitForTimeout(250);
        return s;
    }

    // ---- the returns grid ---------------------------------------------------------------------------------------------------------

    /** One return of the grid. */
    public static final class Return {
        public final String number, date, from, to, amount, remarks;
        Return(String number, String date, String from, String to, String amount, String remarks) { this.number = number; this.date = date; this.from = from; this.to = to; this.amount = amount; this.remarks = remarks; }
        @Override public String toString() { return number + " (" + date + ", " + from + " -> " + to + ", " + amount + (remarks.isEmpty() ? "" : ", \"" + remarks + "\"") + ")"; }
    }

    /** The returns the grid holds (from the screen's own data, in the grid's order). */
    public List<Return> transfers() {
        Object r = page.evaluate("() => {" + JS + " const sc=scp(); const d=(sc&&sc.gridlst)||[]; return JSON.stringify(d.map(r=>[String(r.returnnumber||''), String(r.returndate||''), String(r.FromStoreName||''), String(r.ToStoreName||''), String(r.totalamount==null?'':r.totalamount), String(r.remarks||r.remark||'')])); }");
        List<Return> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); out.add(new Return(c.get(0).getAsString(), c.get(1).getAsString(), c.get(2).getAsString(), c.get(3).getAsString(), c.get(4).getAsString(), c.get(5).getAsString())); } }
        catch (Exception ignore) { }
        return out;
    }

    /** One row of the item table: Item Code, Item Name, Batch Code, Expiry Date, Return Quantity, Transit Quantity, Received Quantity (the box), Purchase Rate, Selling Price. */
    public List<String[]> items() {
        Object r = page.evaluate("() => {" + JS + " const t=itemTbl(); if(!t) return '[]'; return JSON.stringify([...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('td') && r.querySelector('input[ng-model=\"Itm.receivedquantity\"]')).map(r=>{ const c=[...r.querySelectorAll('td')];"
                + " const q=r.querySelector('input[ng-model=\"Itm.receivedquantity\"]'); return [norm(c[1].textContent), norm(c[2].textContent), norm(c[3].textContent), norm(c[4].textContent), norm(c[5].textContent), norm(c[6].textContent), q.value, norm(c[8].textContent), norm(c[10].textContent)]; })); }");
        List<String[]> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); String[] v = new String[c.size()]; for (int i = 0; i < v.length; i++) v[i] = c.get(i).getAsString(); out.add(v); } }
        catch (Exception ignore) { }
        return out;
    }

    /** Click the Return Number cell of the grid row with this index (a real click) and wait for the return's items. Returns the items shown (empty when none came). */
    public List<String[]> clickRow(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=bodyRows()[i]; if(!r) return false; const c=[...r.querySelectorAll('.ui-grid-cell')].find(x=>/^RI\\d/.test(norm(x.textContent))) || r.querySelector('.ui-grid-cell'); if(!c) return false; c.id='__rrRow'; r.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " is not on screen"); return List.of(); }
        try {
            page.waitForResponse(x -> x.url().contains("/ReturnItem/Fetch") && String.valueOf(x.request().postData()).contains("\"ReceiveReturnItemList\""), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("#__rrRow").click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("clicking row " + (index + 1) + " fetched no items: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__rrRow'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        for (int i = 0; i < 20 && items().isEmpty(); i++) page.waitForTimeout(250);
        return items();
    }

    /** Type the Received Quantity of the item row (a real fill + Tab) and check the box and the row's data hold it. */
    public boolean enterReceivedQuantity(int index, String qty) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const t=itemTbl(); const rs=t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[ng-model=\"Itm.receivedquantity\"]')) : []; const r=rs[i]; if(!r) return false; const b=r.querySelector('input[ng-model=\"Itm.receivedquantity\"]'); if(b.disabled) return 'disabled'; b.id='__rrQty'; r.scrollIntoView({block:'center'}); return true; }", index);
        if ("disabled".equals(String.valueOf(tagged))) { problems.add("the Received Quantity of row " + (index + 1) + " is disabled"); return false; }
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Received Quantity box"); return false; }
        try {
            Locator q = page.locator("#__rrQty");
            q.click(new Locator.ClickOptions().setTimeout(5000));
            q.fill(qty, new Locator.FillOptions().setTimeout(5000));
            q.press("Tab", new Locator.PressOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Received Quantity could not be typed: " + e.getMessage().split("\n")[0]); }
        waitForAngular(900);
        Object mv = page.evaluate("() => { const e=document.getElementById('__rrQty'); let m=''; try{ m=String(angular.element(e).scope().Itm.receivedquantity); }catch(x){} const r=(e? e.value : '')+'|'+m; if(e) e.removeAttribute('id'); return r; }");
        String[] bm = String.valueOf(mv).split("\\|", -1);
        if (bm.length < 2 || !qty.equals(bm[0])) problems.add("the Received Quantity box shows \"" + (bm.length > 0 ? bm[0] : "") + "\" instead of " + qty);
        else if (!qty.equals(bm[1])) problems.add("the row's data holds \"" + bm[1] + "\" instead of " + qty);
        return ok();
    }

    // ---- remarks, Save -----------------------------------------------------------------------------------------------------------------

    public boolean enterRemarks(String text) {
        problems.clear();
        Locator m = page.locator("textarea[ng-model='ReceiveReturnItem.Remark']").first();
        try {
            m.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            m.click(new Locator.ClickOptions().setTimeout(5000));
            m.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Remarks could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(500);
        String box = m.inputValue();
        String mv = model("textarea[ng-model='ReceiveReturnItem.Remark']", "ReceiveReturnItem.Remark");
        if (!text.equals(box)) problems.add("the Remarks box shows \"" + box + "\" instead of the text typed");
        else if (!text.equals(mv)) problems.add("the Remarks are not held by the screen's model (\"" + mv + "\")");
        return ok();
    }

    /** Clear the toasts still on screen and start recording new ones. */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__rrToasts=[]; if(window.__rrObs) window.__rrObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__rrToasts.includes(m)) window.__rrToasts.push(m); });"
                + " window.__rrObs=new MutationObserver(add); window.__rrObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__rrToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Save (a real click) and return how many tabs were open just before (the save itself opens the report). The toasts are recorded from this moment. */
    public int clickSave() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        lastSaveStatus = -1; lastSaveMessage = ""; lastResponseId = "";
        int tabsBefore = page.context().pages().size();
        armToasts();
        Locator b = page.locator("button[ng-click^='IUDSave']").first();
        try {
            b.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            b.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Save could not be clicked: " + e.getMessage().split("\n")[0]); }
        return tabsBefore;
    }

    /** Wait for a toast after Save (up to {@code ms}); also reads the save call's answer. Returns the toasts shown ("" if none). */
    public String awaitToast(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(300);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        String[] c = null;
        synchronized (saveCalls) { if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1); else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1); }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        Matcher ri = Pattern.compile("\"ResponseId\"\\s*:\\s*\"?(\\d+)").matcher(c[3]);
        if (ri.find()) lastResponseId = ri.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(260, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message and the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("unexpected");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    // ---- what the server holds -----------------------------------------------------------------------------------------------------------

    /** Whether this return is still listed as waiting to be received (the same search call, read-only): 1 = still listed, 0 = no longer listed, -1 = could not be read. */
    public int stillPending(String returnNumber, int fromStoreId, int toStoreId) {
        Object r = page.evaluate("(a) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/ReturnItem/Fetch', {fromstoreid:a[1], tostoreid:a[2], ExecFlag:'GetReturndItemSearch'}).then(x=>{ const d=x.data||[]; res(String(d.filter(v=>String(v.returnnumber)===a[0]).length)); }, e=>res('ERR')); } catch(x) { res('ERR'); } })",
                Arrays.asList(returnNumber, fromStoreId, toStoreId));
        String s = r == null ? "" : r.toString();
        try { return Integer.parseInt(s) > 0 ? 1 : 0; } catch (Exception e) { return -1; }
    }
}
