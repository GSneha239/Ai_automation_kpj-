package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; Billing &gt; <b>Service</b> — Page Object (list {@code #/serviceItemMaster}, form {@code #/add-serviceItem/serviceMaster}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.385). The menu has two entries called "Service"; the sidebar shows the one routed to {@code #/serviceItemMaster}. The list is a server-paged ui-grid
 * ({@code POST /api/Service/Fetch}, {@code ExecFlag: GRID}; the Service Name column filter sends {@code SearchName}). Its <b>Add</b> ({@code AddServiceItem()}) opens the form (model {@code serviceMst.*}):</p>
 * <ul>
 *   <li><b>Service Master Details</b>: Service Code (filled by the screen), Short Name, <b>Service Name*</b> ({@code .ServiceItemName}), <b>Department</b> ({@code .DepartmentId}), <b>Group*</b> ({@code .GroupId},
 *       its change loads the sub groups) and <b>Sub Group*</b> ({@code .SubGroupId}); all three are select2 dropdowns over a plain select.</li>
 *   <li><b>Pricing Policy Details*</b>: a table of pricing policies, one checkbox per row ({@code tt.Isdefault}); at least one must be ticked.</li>
 *   <li><b>Code Type</b>: a table (Code Type | Code | Version | Service Name | Status) fed by the entry fields below it: <b>Code Type</b> ({@code .CodeType}), <b>Code</b> ({@code .ClinicalCode}), and <b>Add</b> ({@code AddCode()}).</li>
 *   <li><b>Add Set Details</b>: <b>Item Name*</b> ({@code SetItemEntry.ItemName}, a smart search: typing "Panadeine" lists {@code li.ac-menu-item a}); picking an option only fills Item Name — the set's own <b>Add</b>
 *       ({@code fnAddSetItem()}) puts it in <b>List of Set Information</b> (Item Code | Item Name | Batch Code | Quantity per Set | Total Quantity | Edit | Delete ...).</li>
 *   <li><b>Submit</b> ({@code fnIUDServiceMaster()}) saves; <b>Back</b> ({@code closeForm()}) returns to the list.</li>
 * </ul>
 *
 * <p>Saving is REAL: a service is created. It stays (a service can only be made inactive, which this page does not do).</p>
 */
public class Service extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster") && !low.contains("get");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public Service(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastName = "", lastGroup = "", lastSubGroup = "", lastDepartment = "", lastPolicy = "", lastCodeType = "", lastCode = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public String lastItem = "", lastAddedRow = "", lastCodeRow = "";
    public boolean setListFilledBeforeAdd = false;
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "serviceMst.";
    private static final String SET = "SetItemEntry.";
    private static final String HREF = "#/serviceitemmaster";
    private static final String PARENT = "Application Configuration", GROUP = "Billing";

    // ---- navigation ------------------------------------------------------------------------------------------

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

    /**
     * Application Configuration &gt; Billing &gt; Service. "Billing" is also a top-level module, so it is looked for only inside the Application Configuration entry; each group is opened ONCE and awaited (the menu
     * builds each level lazily; a second click while it is still building closes the group again). The link is found by its exact route.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__svLink'; }", HREF);
                try { page.locator("#__svLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("Service.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__svLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddLink();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddLink() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a,button')].some(x=>/^AddServiceItem/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("Service.nav: the Add button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/serviceitemmaster/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-serviceitem/servicemaster/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"input[ng-model='" + M + "ServiceItemName']\")"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__svToasts=[]; if(window.__svObs) window.__svObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__svToasts.includes(m)) window.__svToasts.push(m); });"
                + " window.__svObs=new MutationObserver(add); window.__svObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    /** The toasts recorded since {@link #armToasts()}. */
    public String toasts() {
        Object r = page.evaluate("() => (window.__svToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the form -----------------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form with its Group dropdown and the pricing policies loaded. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^AddServiceItem/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__svAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__svAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__svAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Service form did not open (" + page.url() + ")"); return false; }
        for (int i = 0; i < 80 && (options(M + "GroupId").size() < 2 || policyNames().isEmpty()); i++) page.waitForTimeout(250);
        if (options(M + "GroupId").size() < 2) problems.add("the Group dropdown has no options");
        if (policyNames().isEmpty()) problems.add("the Pricing Policy Details table is empty");
        waitForAngular(1000);
        return ok();
    }

    /** The options of the dropdown with this ng-model as {text, value} (placeholder excluded). */
    public List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    private String readValue(String selector) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", selector);
        return v == null ? "" : v.toString().trim();
    }

    /** Select the option with this exact text (ignoring case) and make angular/select2 see the change; returns the selected text, "" if it could not be selected. */
    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(600);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        if (model == null || model.toString().trim().isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        return got;
    }

    // ---- Service Name -------------------------------------------------------------------------------------------------

    /** The names the server already holds that contain this text (the list's own Service Name search); null when the answer could not be read. */
    public List<String> existingNames(String text) {
        Object r = page.evaluate("(n) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/Service/Fetch', {CreatedLocationId:1,IsServiceSearch:true,ExecFlag:'GRID',PageIndex:0,PageSize:50,SearchName:n})"
                + ".then(x=>res(JSON.stringify((x.data||[]).map(v=>v.ServiceItemName))), e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })", text);
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
        while (m.find()) out.add(m.group(1).replace("\\\"", "\"").trim());
        return out;
    }

    /** True when a service with exactly this name (ignoring case) is already held; null when that could not be read. */
    public Boolean nameTaken(String name) {
        List<String> names = existingNames(name);
        if (names == null) return null;
        for (String n : names) if (n.equalsIgnoreCase(name.trim())) return true;
        return false;
    }

    public boolean enterServiceName(String name) {
        problems.clear();
        lastName = name;
        Locator in = page.locator("input[ng-model='" + M + "ServiceItemName']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(name, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Service Name could not be typed"); return false; }
        waitForAngular(400);
        Object model = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='" + M + "ServiceItemName']\"); try{ return angular.element(e).scope().$eval('" + M + "ServiceItemName'); }catch(x){ return ''; } }");
        if (model == null || !name.equals(model.toString())) problems.add("the Service Name model holds \"" + model + "\" instead of \"" + name + "\"");
        return ok();
    }

    // ---- Group / Sub Group / Department ------------------------------------------------------------------------------------

    public List<String> optionNames(String ng) {
        List<String> out = new ArrayList<>();
        for (String[] o : options(ng)) out.add(o[0]);
        return out;
    }

    /**
     * Select a Group and a Sub Group. Not every group has sub groups, so the preferred groups are tried first, then the others, until one offers a sub group (then the first one is selected). The group's
     * change loads the sub groups, which is awaited. The tried groups are left in {@code tried}.
     */
    public boolean selectGroupAndSubGroup(List<String> preferred, int maxScan, StringBuilder tried) {
        problems.clear();
        List<String> all = optionNames(M + "GroupId");
        List<String> order = new ArrayList<>();
        for (String p : preferred) for (String g : all) if (g.equalsIgnoreCase(p) && !order.contains(g)) order.add(g);
        for (String g : all) if (!order.contains(g) && !g.toLowerCase().startsWith("corporate panel") && !g.toLowerCase().startsWith("qa ")) order.add(g);
        int n = 0;
        for (String g : order) {
            if (n++ >= maxScan) break;
            problems.clear();
            String got = choose(M + "GroupId", g, "Group");
            if (got.isEmpty()) { if (tried.length() > 0) tried.append("; "); tried.append(g).append(" (not selectable)"); continue; }
            for (int i = 0; i < 28 && options(M + "SubGroupId").isEmpty(); i++) page.waitForTimeout(250);
            List<String> subs = optionNames(M + "SubGroupId");
            if (tried.length() > 0) tried.append("; ");
            tried.append(g).append(" (").append(subs.size()).append(" sub group(s))");
            if (subs.isEmpty()) continue;
            lastGroup = got;
            lastSubGroup = choose(M + "SubGroupId", subs.get(0), "Sub Group");
            if (ok()) return true;
        }
        problems.clear();
        problems.add("no group offered a sub group (looked at: " + tried + ")");
        return false;
    }

    /** Select the Department: the first of the preferred names that exists, else the first real one. */
    public String selectDepartment(List<String> preferred) {
        problems.clear();
        List<String> all = optionNames(M + "DepartmentId");
        if (all.isEmpty()) { problems.add("the Department dropdown has no options"); return ""; }
        String pick = all.get(0);
        for (String p : preferred) { boolean hit = false; for (String d : all) if (d.equalsIgnoreCase(p)) { pick = d; hit = true; break; } if (hit) break; }
        lastDepartment = choose(M + "DepartmentId", pick, "Department");
        return lastDepartment;
    }

    // ---- Pricing Policy Details ------------------------------------------------------------------------------------------

    /** The pricing policies listed (one per checkbox row). */
    public List<String> policyNames() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"input[type=checkbox][ng-model='tt.Isdefault']\")].map(c=>{ const tr=c.closest('tr'); return norm(tr? tr.textContent : ''); }); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The policies whose checkbox is ticked. */
    public List<String> tickedPolicies() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"input[type=checkbox][ng-model='tt.Isdefault']\")].filter(c=>c.checked).map(c=>{ const tr=c.closest('tr'); return norm(tr? tr.textContent : ''); }); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Tick the checkbox of a pricing policy: the first of the preferred names that exists, else the first one listed. Returns the policy ticked, "" when none could be. */
    public String tickPolicy(List<String> preferred) {
        problems.clear();
        List<String> all = policyNames();
        if (all.isEmpty()) { problems.add("no pricing policy is listed"); return ""; }
        int idx = 0;
        outer:
        for (String p : preferred) for (int i = 0; i < all.size(); i++) if (all.get(i).equalsIgnoreCase(p)) { idx = i; break outer; }
        Object tagged = page.evaluate("(i) => { const c=[...document.querySelectorAll(\"input[type=checkbox][ng-model='tt.Isdefault']\")][i]; if(!c) return false; c.id='__svPolicy'; c.scrollIntoView({block:'center'}); return true; }", idx);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the checkbox of the pricing policy could not be found"); return ""; }
        try { page.locator("#__svPolicy").check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the pricing policy checkbox could not be ticked: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__svPolicy'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(500);
        lastPolicy = all.get(idx);
        List<String> ticked = tickedPolicies();
        if (!ticked.contains(lastPolicy)) problems.add("the checkbox of \"" + lastPolicy + "\" is not ticked afterwards");
        return ok() ? lastPolicy : "";
    }

    // ---- Code Type -----------------------------------------------------------------------------------------------------------

    public List<String> codeTypeNames() { return optionNames(M + "CodeType"); }

    /** Select the Code Type: the first of the preferred names that exists, else the first real one. */
    public String selectCodeType(List<String> preferred) {
        problems.clear();
        List<String> all = codeTypeNames();
        if (all.isEmpty()) { problems.add("the Code Type dropdown has no options"); return ""; }
        String pick = all.get(0);
        for (String p : preferred) { boolean hit = false; for (String c : all) if (c.equalsIgnoreCase(p)) { pick = c; hit = true; break; } if (hit) break; }
        lastCodeType = choose(M + "CodeType", pick, "Code Type");
        return lastCodeType;
    }

    public boolean enterCode(String code) {
        problems.clear();
        lastCode = code;
        Locator in = page.locator("input[ng-model='" + M + "ClinicalCode']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(code, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Code could not be typed"); return false; }
        waitForAngular(300);
        if (!code.equals(readValue("input[ng-model='" + M + "ClinicalCode']"))) problems.add("the Code field does not hold \"" + code + "\"");
        return ok();
    }

    /** The rows of the Code Type table (Code Type | Code | Version | Service Name | Status), each as one text. */
    public List<String> codeRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Code Type/.test(x.textContent) && /Version/.test(x.textContent) && /Status/.test(x.textContent) && !/Pricing Policy|Quantity per Set/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click the Code Type's Add: the code should join the Code Type table. Returns false (toast in {@link #lastToast}) when it did not. */
    public boolean clickAddCode() {
        problems.clear();
        lastCodeRow = "";
        List<String> before = codeRows();
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^AddCode/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__svAddCode'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Code Type's Add button is not on screen"); return false; }
        try { page.locator("#__svAddCode").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Code Type's Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__svAddCode'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1000);
        List<String> after = before;
        for (int i = 0; i < 24; i++) { after = codeRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        lastToast = toasts();
        if (after.size() <= before.size()) { problems.add("Add put no row into the Code Type table" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        for (String r : after) if (!before.contains(r)) lastCodeRow = r;
        if (lastCodeRow.isEmpty()) lastCodeRow = after.get(after.size() - 1);
        return true;
    }

    // ---- Add Set Details: the smart search and List of Set Information ----------------------------------------------------------

    /** The rows of the "List of Set Information" table, each as one text (Item Code | Item Name | ...). */
    public List<String> setRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Quantity per Set/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).slice(0,5).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Type the text into Item Name (a smart search) and return the suggestions listed. */
    public List<String> typeItem(String text) {
        problems.clear();
        Locator in = page.locator("input[ng-model='" + SET + "ItemName']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(80));
        } catch (Exception e) { problems.add("the Item Name could not be typed"); return List.of(); }
        List<String> sug = suggestions();
        for (int i = 0; i < 40 && sug.isEmpty(); i++) { page.waitForTimeout(250); sug = suggestions(); }
        if (sug.isEmpty()) problems.add("no item is suggested for \"" + text + "\"");
        return sug;
    }

    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li.ac-menu-item a')].filter(vis).map(a=>norm(a.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click the {@code index}-th (0-based) suggestion; Item Name should then hold the option's name. */
    public boolean pickSuggestion(int index) {
        problems.clear();
        armToasts();
        Object tagged = page.evaluate("(i) => {" + JS + " const a=[...document.querySelectorAll('li.ac-menu-item a')].filter(vis)[i]; if(!a) return ''; a.id='__svSug'; return norm(a.textContent); }", index);
        String name = tagged == null ? "" : tagged.toString();
        if (name.isEmpty()) { problems.add("suggestion " + (index + 1) + " is not on screen"); return false; }
        lastItem = name;
        try { page.locator("#__svSug").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion \"" + name + "\" could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__svSug'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        String shown = readValue("input[ng-model='" + SET + "ItemName']");
        lastToast = toasts();
        if (!name.equalsIgnoreCase(shown)) { problems.add("Item Name shows \"" + shown + "\" instead of the option \"" + name + "\"" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        setListFilledBeforeAdd = listHas(name);
        return true;
    }

    /** True when the List of Set Information has a row for this item name. */
    public boolean listHas(String itemName) {
        for (String r : setRows()) if (r.toLowerCase().contains(itemName.toLowerCase())) return true;
        return false;
    }

    /** Click the set's Add: the option should join the List of Set Information. Returns false (toast in {@link #lastToast}) when it did not. */
    public boolean clickAddItem() {
        problems.clear();
        lastAddedRow = "";
        List<String> before = setRows();
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^fnAddSetItem/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__svAddItem'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add Set Details' Add button is not on screen"); return false; }
        try { page.locator("#__svAddItem").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add Set Details' Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__svAddItem'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        List<String> after = before;
        for (int i = 0; i < 24; i++) { after = setRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        lastToast = toasts();
        if (after.size() <= before.size()) { problems.add("Add put no row into the List of Set Information" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        for (String r : after) if (!before.contains(r)) lastAddedRow = r;
        if (lastAddedRow.isEmpty()) lastAddedRow = after.get(after.size() - 1);
        return true;
    }

    // ---- Submit ----------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|submit|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnIUDServiceMaster|AddCode|fnAddSetItem)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__svDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__svDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("Service.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__svDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Submit, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis).find(x=>/^fnIUDServiceMaster/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__svSubmit'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__svSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__svSubmit'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(220, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several of its messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
