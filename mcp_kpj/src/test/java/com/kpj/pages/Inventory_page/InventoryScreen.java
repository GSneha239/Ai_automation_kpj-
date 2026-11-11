package com.kpj.pages.Inventory_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared, NON page-specific helpers for the READ-ONLY Inventory list/add screens (AngularJS + select2 + ui-grid):
 * sidebar navigation under <b>Inventory</b>, select2 / date / checkbox handling, ui-grid reading, on-screen messages,
 * search calls awaited on their API response, and Print capture.
 *
 * <p>Locators and workflows of a particular screen do NOT live here — each screen has its own page object that extends
 * this class ({@code Transfer_page.ReceiveReturnItem}, {@code Transfer_page.ReceiveIssueItem}, {@code Others_page.GatePassInList}).</p>
 *
 * <p>READ-ONLY: nothing in this class clicks Save / Receive / Approve / Delete. A page object may click Save only through
 * {@link #clickSaveForValidation(String)}, which a test calls only after it has verified the form is incomplete; the test's
 * write guard additionally aborts any write API before it reaches the server.</p>
 */
public abstract class InventoryScreen extends BasePage {

    protected static final String NORM = "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();";
    protected static final String VIS = "const vis=e=>{ if(!e) return false; const r=e.getBoundingClientRect(); return e.offsetParent!==null && r.width>0 && r.height>0"
            + " && getComputedStyle(e).visibility!=='hidden'; };";
    /** The screen's own view: the ui-view holding the most controls (sidebar / header are outside it). */
    protected static final String ROOT = "const views=[...document.querySelectorAll('[ui-view]')];"
            + " const score=v=>v.querySelectorAll('button,input,select,.ui-grid').length;"
            + " const root=views.length ? views.sort((a,b)=>score(b)-score(a))[0] : document.body;";

    /** Collects every toast / confirm / alert text the moment it appears (toasts fade in ~1 s). Installed once per context. */
    public static final String MESSAGE_WATCH_JS = "(() => { if (window.__msgWatch) return; window.__msgWatch = true; window.__msgs = [];"
            + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " setInterval(() => { try { document.querySelectorAll('.toast-message,.jconfirm-content,.ng-confirm-message,.bootbox-body,.swal2-html-container,.sweet-alert p')"
            + "  .forEach(e => { const t = norm(e.innerText); if (t && t.length < 400 && !window.__msgs.includes(t)) window.__msgs.push(t); }); } catch (x) {} }, 100); })();";

    protected InventoryScreen(Page page) { super(page); }

    private static Page.WaitForFunctionOptions poll(int timeoutMs) { return new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250); }

    // ================================================================ navigation

    /**
     * Inventory &gt; (group) &gt; link, through the sidebar like a user. The link is looked up INSIDE the Inventory entry, so a
     * same-named link under another module (e.g. CSSD &gt; Receive Issue Item) is never taken.
     *
     * @return "" when the link was clicked and {@code expectedState} became the current ui-router state, otherwise why not
     */
    public String navigateInventory(String href, String expectedState) {
        if (!expandMenuEntry(null, "^Inventory$")) return "the sidebar entry \"Inventory\" was not found";
        Object chain = page.evaluate("(h) => {" + NORM + " const inv=[...document.querySelectorAll('a')].find(x=>/^Inventory$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const scope=(inv && inv.closest('li')) || document; const a=[...scope.querySelectorAll('a')].find(x=>x.getAttribute('href')===h); if(!a) return null;"
                + " const out=[]; let li=a.closest('li'); li=li && li.parentElement && li.parentElement.closest('li');"
                + " const invLi=inv && inv.closest('li'); while(li && li!==invLi){ const x=li.querySelector(':scope > a'); if(x) out.unshift(norm(x.textContent)); li=li.parentElement && li.parentElement.closest('li'); }"
                + " return out; }", href);
        if (!(chain instanceof List)) return "no sidebar link " + href + " exists under Inventory for this user";
        String parent = "^Inventory$";
        for (Object o : (List<?>) chain) {
            String re = "^" + Pattern.quote(String.valueOf(o)) + "$";
            if (!expandMenuEntry(parent, re)) return "the menu group \"" + o + "\" did not expand";
            parent = re;
        }
        Object tagged = page.evaluate("(h) => {" + NORM + " const inv=[...document.querySelectorAll('a')].find(x=>/^Inventory$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const a=[...((inv && inv.closest('li')) || document).querySelectorAll('a')].find(x=>x.getAttribute('href')===h); if(!a) return false; a.id='__invLink'; return true; }", href);
        if (!Boolean.TRUE.equals(tagged)) return "the link " + href + " disappeared from the menu";
        try {
            page.locator("#__invLink").click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            return "the menu link " + href + " could not be clicked (" + firstLine(e) + ")";
        } finally {
            page.evaluate("() => { const e=document.getElementById('__invLink'); if(e) e.removeAttribute('id'); }");
        }
        return waitForState(expectedState, 45000) ? "" : "after the click the screen is \"" + currentState() + "\" (" + page.url() + "), not " + expectedState;
    }

    /** Current ui-router state name ("" when unknown). */
    public String currentState() {
        try { Object s = page.evaluate("() => { try { return angular.element(document.body).injector().get('$state').current.name || ''; } catch(e) { return ''; } }");
              return s == null ? "" : s.toString(); }
        catch (Exception e) { return ""; }
    }

    /** Waits until the given ui-router state is active and its view shows a button, then lets dropdown options arrive. */
    public boolean waitForState(String state, int timeoutMs) {
        try {
            page.waitForFunction("(st) => { try { if (angular.element(document.body).injector().get('$state').current.name !== st) return false; } catch(e) { return false; }"
                    + ROOT + VIS + " return [...root.querySelectorAll('button')].some(vis); }", state, poll(timeoutMs));
        } catch (Exception e) { return false; }
        try {
            page.waitForFunction("() => {" + ROOT + " const s=[...root.querySelectorAll('select[ng-model]')].filter(x=>!/pagination/.test(x.getAttribute('ng-model')) && !x.disabled);"
                    + " return s.length===0 || s.every(x=>x.options.length>1); }", null, poll(10000));
        } catch (Exception slow) { /* a dropdown that never loads is reported by the case that uses it */ }
        page.waitForLoadState();
        return true;
    }

    /** Recovery ONLY (never used to make a navigation case pass): go to a ui-router state directly. */
    public boolean goState(String state) {
        page.evaluate("(st) => { angular.element(document.body).injector().get('$state').go(st); }", state);
        return waitForState(state, 30000);
    }

    public String route() { int i = page.url().indexOf('#'); return i >= 0 ? page.url().substring(i).replaceFirst("\\?.*$", "") : page.url(); }

    // ================================================================ buttons

    /** Visible buttons of the screen: text -> ng-click. */
    public Map<String, String> buttons() {
        Object r = page.evaluate("() => {" + NORM + VIS + ROOT + " return [...root.querySelectorAll('button,input[type=button]')].filter(vis)"
                + ".map(b=>[norm(b.textContent||b.value), b.getAttribute('ng-click')||'']).filter(x=>x[0] && !/grid menu/i.test(x[0])); }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof List) for (Object o : (List<?>) r) { List<?> p = (List<?>) o; out.putIfAbsent(String.valueOf(p.get(0)), String.valueOf(p.get(1))); }
        return out;
    }

    /** Locator of the visible button whose trimmed text is exactly {@code text}. */
    public Locator button(String text) {
        return page.locator("button:visible, input[type=button]:visible")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + Pattern.quote(text) + "\\s*$"))).first();
    }

    /** Clicks a NON-writing button (Search, New, Back, Print...). Refuses Save / Receive / Approve / Delete / Submit. */
    public void clickSafe(String text) {
        if (text.matches("(?i).*\\b(save|receive|approve|reject|delete|remove|submit|confirm|post|update)\\b.*"))
            throw new IllegalArgumentException("read-only: refusing to click \"" + text + "\" (use clickSaveForValidation for an incomplete form)");
        button(text).click(new Locator.ClickOptions().setTimeout(10000));
    }

    /**
     * Clicks Save on a form the CALLING TEST has verified to be incomplete, to observe the validation that must block it.
     * {@code preconditionProof} is the test's description of why the form is incomplete; it must not be blank.
     */
    public void clickSaveForValidation(String preconditionProof) {
        if (preconditionProof == null || preconditionProof.isBlank())
            throw new IllegalStateException("read-only: Save may only be clicked on a form verified to be incomplete");
        button("Save").click(new Locator.ClickOptions().setTimeout(10000));
    }

    // ================================================================ messages

    /** Clears the message log (call right before the action whose messages you want). */
    public void clearMessages() {
        page.evaluate("() => { window.__msgs = []; document.querySelectorAll('#toast-container .toast').forEach(t => t.remove()); }");
    }

    /** Messages that appeared since {@link #clearMessages()}; waits up to {@code settleMs} for the first one. */
    public List<String> messages(int settleMs) {
        try { page.waitForFunction("() => (window.__msgs||[]).length > 0", null, poll(Math.max(settleMs, 300))); } catch (Exception none) { }
        return strings(page.evaluate("() => window.__msgs || []"));
    }

    /** Closes confirm / info boxes with Cancel / Close / No (or a lone OK) — never a button that acts on data. */
    public int dismissBoxes() {
        int closed = 0;
        for (int i = 0; i < 3; i++) {
            Object r = page.evaluate("() => {" + NORM + " const shown=e=>{const r=e.getBoundingClientRect(); return r.width>0 && r.height>0 && getComputedStyle(e).visibility!=='hidden';};"
                    + " const box=[...document.querySelectorAll('.jconfirm-box,.ng-confirm-box,[class*=\"confirm-box\"],.bootbox.modal.in,.sweet-alert.visible,.swal2-popup')].filter(shown).pop(); if(!box) return false;"
                    + " const btns=[...box.querySelectorAll('button')]; let b=btns.find(x=>/^(cancel|close|no)$/i.test(norm(x.textContent)))"
                    + "   || box.querySelector('.jconfirm-closeIcon,.ng-confirm-closeIcon,.close'); if(!b && btns.length===1 && /^ok$/i.test(norm(btns[0].textContent))) b=btns[0];"
                    + " if(!b) return false; b.click(); return true; }");
            if (!Boolean.TRUE.equals(r)) break;
            closed++;
            page.waitForTimeout(300);
        }
        return closed;
    }

    /** Clears anything sitting over the screen (boxes, open select2 list, calendar). @return what was closed */
    public String closeOverlays() {
        StringBuilder sb = new StringBuilder();
        int c = dismissBoxes();
        if (c > 0) sb.append(c).append(" message box(es) closed; ");
        Object open = page.evaluate("() => document.querySelectorAll('.select2-drop-active').length");
        if (intOf(open) > 0) { try { page.keyboard().press("Escape"); } catch (Exception ignore) { } sb.append("dropdown list closed; "); }
        dismissDatePicker();
        return sb.toString().trim();
    }

    // ================================================================ dropdowns

    public List<String> options(String ngModel) { return Select2Pick.names(page, ngModel); }

    /** Text the dropdown shows ("" = nothing chosen). Works for select2 and native selects. */
    public String chosen(String ngModel) {
        String s = Select2Pick.chosen(page, ngModel);
        if (!s.isEmpty()) return s;
        Object r = page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e || e.selectedIndex<0) return '';"
                + " const t=norm(e.options[e.selectedIndex].textContent); return /^-*\\s*select\\s*-*$/i.test(t) ? '' : t; }", ngModel);
        return r == null ? "" : r.toString();
    }

    /** Value the screen's model holds ("" = empty). */
    public String model(String ngModel) { return Select2Pick.model(page, ngModel); }

    public boolean disabled(String ngModel) {
        return Boolean.TRUE.equals(page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!(e && e.disabled); }", ngModel));
    }

    /** Picks {@code name} the way a user does (open box, click entry); falls back to the native select. @return "" on success */
    public String choose(String ngModel, String name) {
        String err = Select2Pick.pick(page, ngModel, name);
        if (err.isEmpty() || !err.contains("not on screen")) return err;
        Object ok = page.evaluate("([n, t]) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return false;"
                + " const o=[...e.options].find(x=>norm(x.textContent)===t); if(!o) return false; e.value=o.value;"
                + " e.dispatchEvent(new Event('change',{bubbles:true})); if(window.jQuery) jQuery(e).trigger('change'); return true; }", Arrays.asList(ngModel, name));
        return Boolean.TRUE.equals(ok) ? "" : "\"" + name + "\" is not an option of " + ngModel;
    }

    /** Puts a dropdown back to its "--Select--" entry. @return "" on success */
    public String reset(String ngModel) {
        Object ok = page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return false;"
                + " const o=[...e.options].find(x=>/^-*\\s*select\\s*-*$/i.test(norm(x.textContent))) || e.options[0]; e.value=o.value;"
                + " if(window.jQuery){ try{ jQuery(e).select2('val', o.value); }catch(x){} jQuery(e).trigger('change'); } else e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return true; }", ngModel);
        page.waitForTimeout(300);
        return Boolean.TRUE.equals(ok) ? "" : ngModel + " not found";
    }

    // ================================================================ dates / text / checkboxes

    /** Types a date into the box bound to {@code ngModel}, closes the calendar. @return the value the screen's model now holds */
    public String setDate(String ngModel, String value) {
        Locator box = page.locator("[ng-model='" + ngModel + "']").first();
        box.fill(value);
        box.dispatchEvent("change");
        try { box.blur(); } catch (Exception ignore) { }
        dismissDatePicker();
        String held = model(ngModel);
        if (!value.equals(held) && !value.isEmpty()) {   // datepicker directives sometimes ignore fill(); set the view value like a typed entry
            page.evaluate("([n, v]) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); const c=angular.element(e).controller('ngModel');"
                    + " if(c){ c.$setViewValue(v); c.$render(); } angular.element(e).scope().$applyAsync(); }", Arrays.asList(ngModel, value));
            page.waitForTimeout(200);
            held = model(ngModel);
        }
        return held;
    }

    public String fieldValue(String selector) { try { return page.locator(selector).first().inputValue(); } catch (Exception e) { return ""; } }

    public boolean isChecked(String ngModel) {
        return Boolean.TRUE.equals(page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!(e && e.checked); }", ngModel));
    }

    /** Ticks / unticks a checkbox by clicking it (only when its state differs). */
    public void setChecked(String ngModel, boolean on) {
        if (isChecked(ngModel) != on) page.locator("[ng-model='" + ngModel + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
        page.waitForTimeout(250);
    }

    public boolean inputDisabled(String selector) {
        return Boolean.TRUE.equals(page.evaluate("(s) => { const e=document.querySelector(s); return !e || e.disabled || e.readOnly; }", selector));
    }

    /** Label texts of the screen that occur more than once (e.g. a field label repeated by mistake). */
    public List<String> duplicateLabels() {
        return strings(page.evaluate("() => {" + NORM + VIS + ROOT + " const seen={}, d=[];"
                + " [...root.querySelectorAll('label')].filter(vis).map(l=>norm(l.textContent).replace(/[*:]+$/,'').trim()).filter(t=>t && !/^(from|to)$/i.test(t))"
                + "  .forEach(t=>{ seen[t]=(seen[t]||0)+1; if(seen[t]===2) d.push(t); }); return d; }"));
    }

    /** Visible label texts of the screen. */
    public List<String> labels() {
        return strings(page.evaluate("() => {" + NORM + VIS + ROOT + " return [...root.querySelectorAll('label')].filter(vis).map(l=>norm(l.textContent)).filter(t=>t); }"));
    }

    // ================================================================ API-awaited actions

    /** Outcome of an action that should send one data request. */
    public static class Call {
        public int status = -1;          // -1 = no matching request was sent
        public String url = "", request = "", response = "", error = "";
        public int rows = -1;            // rows in the JSON response (-1 unknown)
        public boolean fired() { return status >= 0; }
        @Override public String toString() {
            return fired() ? "HTTP " + status + " " + url + " rows=" + rows : "no request sent" + (error.isEmpty() ? "" : " (" + error + ")");
        }
    }

    /**
     * Runs {@code action} and waits (up to {@code timeoutMs}) for the first POST/GET to an /api/ URL containing {@code urlPart}.
     * No fixed sleep: it returns as soon as the response arrives; a timeout means no such request was sent.
     */
    public Call awaitApi(String urlPart, int timeoutMs, Runnable action) {
        Call c = new Call();
        try {
            Response r = page.waitForResponse(x -> x.url().contains("/api/") && x.url().toLowerCase().contains(urlPart.toLowerCase()),
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs), action);
            c.status = r.status();
            c.url = r.url().replaceFirst("^https?://[^/]+", "");
            c.request = r.request().postData() == null ? "" : r.request().postData();
            try { c.response = r.text(); } catch (Exception e) { c.response = ""; }
            c.rows = countRows(c.response);
        } catch (Exception e) {
            c.error = firstLine(e);
        }
        page.waitForLoadState();
        return c;
    }

    /** Number of rows in the first array of a JSON text (JSON wrapped in a JSON string is unwrapped). */
    public int countRows(String json) {
        if (json == null || json.isBlank()) return -1;
        return intOf(page.evaluate("(t) => { try { let j=JSON.parse(t); if(typeof j==='string') j=JSON.parse(j);"
                + " const f=o=>{ if(Array.isArray(o)) return o.length; if(o&&typeof o==='object'){ for(const k of Object.keys(o)){ const r=f(o[k]); if(r>=0) return r; } } return -1; };"
                + " return f(j); } catch(e) { return -2; } }", json));
    }

    // ================================================================ grids

    /** CSS of the ui-grid bound to {@code optionsName} (e.g. "gridOptions"). */
    public static String grid(String optionsName) { return ".ui-grid[ui-grid='" + optionsName + "']"; }

    public int gridRows(String grid) {
        return intOf(page.evaluate("(g) => { const el=document.querySelector(g); if(!el) return -1; const s=angular.element(el).isolateScope();"
                + " return s && s.grid && s.grid.options.data ? s.grid.options.data.length : -1; }", grid));
    }

    /** Waits until the grid's data length differs from {@code before} (or {@code timeoutMs} passes). */
    public int waitGridChange(String grid, int before, int timeoutMs) {
        try {
            page.waitForFunction("([g, b]) => { const el=document.querySelector(g); if(!el) return false; const s=angular.element(el).isolateScope();"
                    + " return s && s.grid && s.grid.options.data && s.grid.options.data.length !== b; }", Arrays.asList(grid, before), poll(timeoutMs));
        } catch (Exception same) { }
        return gridRows(grid);
    }

    /** Rendered header labels of the grid. */
    public List<String> headers(String grid) {
        return strings(page.evaluate("(g) => {" + NORM + " return [...document.querySelectorAll(g+' .ui-grid-render-container-body .ui-grid-header-cell .ui-grid-header-cell-label')]"
                + ".map(e=>norm(e.textContent)).filter(t=>t); }", grid));
    }

    /** Every data row of the grid (ALL rows, not only the rendered page): column display name -> raw value. */
    public List<Map<String, String>> gridData(String grid) {
        Object r = page.evaluate("(g) => {" + NORM + " const el=document.querySelector(g); if(!el) return [];"
                + " const s=angular.element(el).isolateScope(); if(!s || !s.grid) return []; const G=s.grid;"
                + " const cols=G.columns.filter(c=>c.field && c.displayName && !/RowHeaderCol/.test(c.name));"
                + " const get=(e,f)=>{ try { return f.split('.').reduce((o,k)=>o==null?o:o[k], e); } catch(x) { return null; } };"
                + " return (G.options.data||[]).map(e=>{ const o={}; cols.forEach(c=>{ const v=get(e,c.field); o[norm(c.displayName)] = v==null ? '' : String(v); }); return o; }); }", grid);
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) {
            Map<String, String> m = new LinkedHashMap<>();
            if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) m.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            out.add(m);
        }
        return out;
    }

    /**
     * Clicks the grid row whose cells contain {@code rowText} (scrolling it into view first), the way a user selects a record.
     * @return "" on success, else why the row could not be clicked
     */
    public String clickRow(String grid, String keyColumn, String rowText) {
        page.evaluate("([g, col, t]) => { const el=document.querySelector(g); const s=el && angular.element(el).isolateScope(); if(!s || !s.grid) return;"
                + " const G=s.grid; const c=G.columns.find(x=>(x.displayName||'').trim()===col); const e=(G.options.data||[]).find(d=>c && String(d[c.field]).trim()===t);"
                + " if(e && G.api.core.scrollTo) G.api.core.scrollTo(e, c); }", Arrays.asList(grid, keyColumn, rowText));
        // the cell whose whole text is the record number: clicking it selects that row (row text has no separators between cells)
        Locator cell = page.locator(grid + " .ui-grid-render-container-body .ui-grid-row .ui-grid-cell-contents")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + Pattern.quote(rowText) + "\\s*$"))).first();
        try {
            cell.waitFor(new Locator.WaitForOptions().setTimeout(8000));
            cell.click(new Locator.ClickOptions().setTimeout(8000));
            return "";
        } catch (Exception e) {
            return "row \"" + rowText + "\" could not be clicked (" + firstLine(e) + ")";
        }
    }

    /** Whether a grid row shows as selected (ui-grid selection or a highlight class). */
    public int highlightedRows(String grid) {
        return intOf(page.evaluate("(g) => { const el=document.querySelector(g); const s=el && angular.element(el).isolateScope();"
                + " const sel = s && s.grid && s.grid.api && s.grid.api.selection ? s.grid.api.selection.getSelectedRows().length : 0;"
                + " const css = document.querySelectorAll(g+' .ui-grid-row-selected, '+g+' .ui-grid-row .selected, '+g+' .ui-grid-row.active').length;"
                + " return Math.max(sel, css); }", grid));
    }

    // ================================================================ print capture (read-only)

    /** Outcome of a Print click: report URLs the screen tried to open (no tab is opened), and what each returns. */
    public static class PrintResult {
        public final List<String> urls = new ArrayList<>();
        public final List<String> reports = new ArrayList<>();   // "HTTP 500 <title>" per url
        public final List<String> messages = new ArrayList<>();
        public boolean opened() { return !urls.isEmpty(); }
        public boolean allReportsOk() { return opened() && reports.stream().allMatch(r -> r.startsWith("HTTP 200") && !r.contains("ERROR")); }
        @Override public String toString() {
            return (urls.isEmpty() ? "no report opened" : "opened " + urls + " -> " + reports) + (messages.isEmpty() ? "" : "; messages " + messages);
        }
    }

    /**
     * Clicks {@code buttonText} with window.open captured (the URL is recorded, no tab opens), then fetches each captured report
     * URL with a GET in the same session to see whether the report actually renders. GET requests only — nothing is changed.
     */
    public PrintResult print(String buttonText, String base) {
        PrintResult p = new PrintResult();
        page.evaluate("() => { window.__opened=[]; if(!window.__origOpen) window.__origOpen=window.open;"
                + " window.open=function(u){ window.__opened.push(String(u)); return { focus(){}, close(){}, print(){}, document:{ write(){}, close(){} }, location:{} }; }; }");
        clearMessages();
        try {
            clickSafe(buttonText);
            try { page.waitForFunction("() => (window.__opened||[]).length>0 || (window.__msgs||[]).length>0", null, poll(6000)); } catch (Exception none) { }
            p.urls.addAll(strings(page.evaluate("() => window.__opened || []")));
            p.messages.addAll(messages(300));
        } finally {
            page.evaluate("() => { if(window.__origOpen) window.open=window.__origOpen; }");
        }
        for (String u : p.urls) {
            String abs = u.startsWith("http") ? u : base + (u.startsWith("/") ? "" : "/") + u;
            try {
                APIResponse r = page.request().get(abs);
                String body = r.text();
                java.util.regex.Matcher m = Pattern.compile("(?is)<title>(.*?)</title>").matcher(body);
                String title = m.find() ? m.group(1).replaceAll("\\s+", " ").trim() : "";
                boolean err = r.status() >= 400 || body.matches("(?is).*(Server Error in|Exception from HRESULT|Stack Trace:).*");
                p.reports.add("HTTP " + r.status() + (err ? " ERROR" : "") + (title.isEmpty() ? "" : " \"" + title + "\""));
            } catch (Exception e) {
                p.reports.add("request failed: " + firstLine(e));
            }
        }
        return p;
    }

    /** Runs {@code action} with window.open captured (no tab opens). @return the URLs the screen tried to open */
    public List<String> withOpenCaptured(Runnable action) {
        page.evaluate("() => { window.__opened=[]; if(!window.__origOpen) window.__origOpen=window.open;"
                + " window.open=function(u){ window.__opened.push(String(u)); return { focus(){}, close(){}, print(){}, document:{ write(){}, close(){} }, location:{} }; }; }");
        try {
            action.run();
            page.waitForTimeout(1500);   // an app that opens a print tab after a save does so right after the save response
            return strings(page.evaluate("() => window.__opened || []"));
        } finally {
            page.evaluate("() => { if(window.__origOpen) window.open=window.__origOpen; }");
        }
    }

    // ================================================================ small helpers

    public static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return -1; }
    }

    public static String firstLine(Throwable e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }
}
