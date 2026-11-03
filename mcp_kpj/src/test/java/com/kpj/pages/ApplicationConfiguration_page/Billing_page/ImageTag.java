package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
 * Application Configuration &gt; Billing &gt; <b>ImageTag</b> — Page Object (route {@code #/ImageTag}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.387). It is the GENERIC common-master screen (the same shape as Quality Indicator): no Add button, an always-open form —
 * <b>Form Name*</b> (a native select, {@code data-ng-model="selectedform.value"}, options {@code drpFormNamesVals} = 179 masters, {@code ng-change="GetGridData(commonmaster.MasterCodeFor)"}; it chooses WHICH
 * common master the record is saved into; some values appear twice and are never picked), <b>Code*</b> ({@code commonmaster.Code}), <b>Description*</b> ({@code commonmaster.Description}), a plain
 * <b>Submit</b> (no ng-click: found by its text) and <b>Clear</b>; below, the grid {@code #grid1} (Code | Description | Status | Edit).</p>
 *
 * <p>Saving is REAL: {@code POST /api/CommonMaster/IUD} writes the record into the chosen master, where it stays.</p>
 *
 * <p>KNOWN APP DEFECT: the grid stays EMPTY after choosing a form name (also for masters that hold rows, e.g. Bank): {@code GetGridData} first asks the rights service for
 * {@code selectedform.CommonMasterPkID}, which this generic screen never sets, so {@code Access} becomes all-false and the rows are never fetched. The records can still be read from the server with the
 * same call the grid would make ({@code commonMasterFactory.fetchCommonMaster}, {@code ExecFlag GRID}); {@link #serverRows()} does that to pick unique values and to say whether a saved record IS on the server.</p>
 */
public class ImageTag extends BasePage {

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

    public ImageTag(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFormName = "", lastFormValue = "", lastCode = "", lastDescription = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE = "commonmaster.Code", DESC = "commonmaster.Description";
    private static final String HREF = "#/imagetag";
    private static final String PARENT = "Application Configuration", GROUP = "Billing";
    private static final String FORM_SELECT = "[...document.querySelectorAll('select')].filter(vis).find(x=>/drpFormNamesVals/.test(x.getAttribute('ng-options')||''))";

    /** Image tags a clinician would use; the first one the chosen master does not hold yet is entered. */
    private static final String[] DESCRIPTIONS = {
            "Surgical Site Photo", "Burn Injury Photo", "Rash Progress Photo", "Fracture Cast Photo", "Dressing Change Photo", "Stoma Site Photo", "Scar Healing Photo", "Cast Window Photo"
    };

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
     * Application Configuration &gt; Billing &gt; ImageTag. "Billing" is also a top-level module, so it is looked for only inside the Application Configuration entry; each group is opened ONCE and awaited
     * (the menu builds each level lazily; a second click while it is still building closes the group again). The link is found by its exact route.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__itLink'; }", HREF);
                try { page.locator("#__itLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ImageTag.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__itLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own Code box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/imagetag/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='" + CODE + "'); }"));
    }

    // ---- Form Name ---------------------------------------------------------------------------------------------------

    /** The Form Names offered as {text, value}; entries whose value is shared with another entry are left out (they cannot be told apart by the model). */
    public List<String[]> formOptions() {
        Object r = page.evaluate("() => {" + JS + " const s=" + FORM_SELECT + "; if(!s) return [];"
                + " const opts=[...s.options].filter(o=>o.value); return opts.filter(o=>opts.filter(x=>x.value===o.value).length===1).map(o=>[norm(o.textContent), o.value]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    public int formCount() {
        Object r = page.evaluate("() => {" + JS + " const s=" + FORM_SELECT + "; return s? [...s.options].filter(o=>o.value).length : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** What the Form Name select shows now, and what the screen's model holds; sets {@link #lastFormName} / {@link #lastFormValue}. Returns the model's value ("" when none). */
    public String readFormName() {
        Object r = page.evaluate("() => {" + JS + " const s=" + FORM_SELECT + "; if(!s) return '';"
                + " const o=s.options[s.selectedIndex]; let m=''; try{ const sc=angular.element(s).scope(); m=(sc&&sc.selectedform&&sc.selectedform.value)||''; }catch(e){}"
                + " return (o?norm(o.textContent):'')+'\\u0001'+(o?o.value:'')+'\\u0001'+m; }");
        String[] p = (r == null ? "" : r.toString()).split("\u0001", -1);
        lastFormName = p.length > 0 ? p[0] : "";
        lastFormValue = p.length > 1 ? p[1] : "";
        return p.length > 2 ? p[2] : "";
    }

    public boolean formNameSelected() {
        String model = readFormName();
        return !lastFormName.isBlank() && !lastFormName.startsWith("--") && !lastFormValue.isBlank() && !model.isBlank() && lastFormValue.replaceFirst("^string:", "").equals(model.replaceFirst("^string:", ""));
    }

    /** Select the Form Name whose text or value matches the regex (first match among the unique options), else the first unique option. Returns the text selected, "" when it could not be. */
    public String selectFormName(String regex) {
        problems.clear();
        List<String[]> opts = formOptions();
        if (opts.isEmpty()) { problems.add("the Form Name dropdown has no options"); return ""; }
        Pattern rx = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        String[] pick = opts.get(0);
        for (String[] o : opts) if (rx.matcher(o[0]).find() || rx.matcher(o[1].replaceFirst("^string:", "")).find()) { pick = o; break; }
        page.evaluate("() => {" + JS + " const s=" + FORM_SELECT + "; if(s) s.id='__itForm'; }");
        try {
            page.locator("#__itForm").selectOption(new com.microsoft.playwright.options.SelectOption().setValue(pick[1]), new Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the Form Name \"" + pick[0] + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__itForm'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        if (!formNameSelected()) { problems.add("the Form Name is not held by the model after selecting \"" + pick[0] + "\" (" + lastFormName + " / " + lastFormValue + ")"); return ""; }
        return lastFormName;
    }

    // ---- what the server holds -----------------------------------------------------------------------------------------------

    /** The records the server holds for the chosen Form Name, as {id, code, description, status}, read with the same call the grid makes ({@code ExecFlag GRID}); null when it could not be read. */
    public List<String[]> serverRows() {
        String form = lastFormValue.replaceFirst("^string:", "");
        if (form.isEmpty()) return null;
        Object r = page.evaluate("(v) => new Promise(res => { try { const f=angular.element(document.body).injector().get('commonMasterFactory');"
                + " f.fetchCommonMaster({ExecFlag:'GRID', MasterCodeFor:v}).then(x=>res(JSON.stringify((x.data||[]).map(d=>[d.CommonMasterPkID,d.Code,d.Description,d.Status]))), e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })", form);
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String[]> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement row : a) {
                JsonArray c = row.getAsJsonArray();
                String[] v = new String[4];
                for (int i = 0; i < 4; i++) v[i] = c.get(i).isJsonNull() ? "" : c.get(i).getAsString();
                out.add(v);
            }
        } catch (Exception e) { return null; }
        return out;
    }

    /** True when the server holds a record with this code and description (read afresh). */
    public boolean serverHolds(String code, String description) {
        List<String[]> rows = serverRows();
        if (rows == null) return false;
        for (String[] r : rows) if (r[1].trim().equalsIgnoreCase(code.trim()) && r[2].trim().equalsIgnoreCase(description.trim())) return true;
        return false;
    }

    /**
     * A code and a description the chosen master does not hold. The description is the first of the pool not in use (the pool is walked from {@code skip}, so a retry differs); the code is "IT" and five digits
     * from the clock, moved on until it is free.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        java.util.Set<String> codes = new java.util.HashSet<>(), descs = new java.util.HashSet<>();
        for (String[] r : rows) { codes.add(r[1].trim().toUpperCase()); descs.add(r[2].trim().toLowerCase()); }
        String description = "";
        for (int i = 0; i < DESCRIPTIONS.length && description.isEmpty(); i++) {
            String d = DESCRIPTIONS[(skip + i) % DESCRIPTIONS.length];
            if (!descs.contains(d.toLowerCase())) description = d;
        }
        if (description.isEmpty()) description = DESCRIPTIONS[skip % DESCRIPTIONS.length] + " " + (System.currentTimeMillis() % 100000);
        long n = (System.currentTimeMillis() / 1000 + skip * 7L) % 100000;
        String code = "IT" + String.format("%05d", n);
        while (codes.contains(code)) { n = (n + 1) % 100000; code = "IT" + String.format("%05d", n); }
        return new String[]{code, description};
    }

    // ---- Code and Description ------------------------------------------------------------------------------------------------

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(value, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(300);
        Object shown = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng);
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); try{ return angular.element(e).scope().$eval(n); }catch(x){ return ''; } }", ng);
        if (shown == null || !value.equals(shown.toString())) problems.add("the " + what + " box shows \"" + shown + "\" instead of \"" + value + "\"");
        else if (model == null || !value.equals(model.toString())) problems.add("the " + what + " model holds \"" + model + "\" instead of \"" + value + "\"");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); lastCode = code; return type(CODE, code, "Code"); }

    public boolean enterDescription(String description) { problems.clear(); lastDescription = description; return type(DESC, description, "Description"); }

    // ---- toasts --------------------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__itToasts=[]; if(window.__itObs) window.__itObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__itToasts.includes(m)) window.__itToasts.push(m); });"
                + " window.__itObs=new MutationObserver(add); window.__itObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__itToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- Submit ----------------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value)) && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__itDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__itDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("ImageTag.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__itDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Submit (found by its text; it has no ng-click), answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value))); if(!b) return false; b.id='__itSubmit'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__itSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__itSubmit'); if(e) e.removeAttribute('id'); }"); }
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

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    // ---- the table -------------------------------------------------------------------------------------------------------------------

    /** The rows the grid shows, each as "Code | Description | Status" (blank rows left out). */
    public List<String> tableRows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-row, .ui-grid-row')].filter(vis)"
                + ".map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent)).filter(Boolean).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** True when a row of the grid shows this code and description. */
    public boolean shownInTable(String code, String description) {
        for (String r : tableRows()) if (r.toLowerCase().contains(code.toLowerCase()) && r.toLowerCase().contains(description.toLowerCase())) return true;
        return false;
    }

    /** Waits up to {@code ms} for the grid to show the record. */
    public boolean awaitInTable(String code, String description, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (shownInTable(code, description)) return true; page.waitForTimeout(500); }
        return shownInTable(code, description);
    }

    /** Why the grid shows nothing, from the screen's own scope: the rows its data holds and the rights ({@code Access}) the screen believes it has. */
    public String explainEmptyGrid() {
        Object r = page.evaluate("() => {" + JS + " try{ const s=" + FORM_SELECT + "; const sc=angular.element(s).scope();"
                + " const d=(sc.gridOptions&&sc.gridOptions.data)?sc.gridOptions.data.length:'n/a';"
                + " return 'the grid data holds '+d+' row(s); scope.Access='+JSON.stringify(sc.Access||null)+'; selectedform.CommonMasterPkID='+((sc.selectedform||{}).CommonMasterPkID); }catch(e){ return '(could not inspect the screen: '+e.message+')'; } }");
        return r == null ? "" : r.toString();
    }
}
