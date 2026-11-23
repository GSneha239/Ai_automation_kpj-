package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * Application Configuration &gt; Nursing &gt; <b>History Master</b> — Page Object ({@code #/HistoryMaster}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.376). One screen with no Add button: a <b>History Details</b> tree on top (each node reads "code description", for example "37 Chief Complaint"; 117 items in
 * 13 top-level entries (12 history types such as Past Medical History, Surgical History, Medication History, Allergy History, Family History, Social History, Obstetric &amp; Gynaecological History, Immunisation History,
 * Blood Transfusion History and Developmental History, plus a stray childless "Ischaemic Heart Disease") and, below it, the inline form (model {@code HistoryMaster.*}): <b>Code*</b> ({@code #txtcode}, {@code .Code}), <b>Remark*</b> ({@code #txtDescription},
 * {@code .Description}) and <b>Parent</b> (a select2, {@code .parentID}, 117 items + the placeholder; the new item is filed under it), with <b>Save</b> ({@code #btnSave}, {@code fnIUDLocalExamination()} — the
 * handler is named after another screen), <b>Modify</b> ({@code #btnModify}, disabled for a new item) and Clear ({@code resetForm()}). The tree's data is the answer of
 * {@code POST /api/HistoryMaster/Fetch}: a flat list of {@code {HistoryID, Code, Description, parentID, Status}} that this page object reads from the network, because the screen asks for it again after a save.</p>
 *
 * <p>Codes are plain numbers that follow the item's id (1 ... 115), with three manual rows that do not ({@code 099}, {@code 123}, {@code C012}); the new code is one above the largest all-digit code in use
 * ({@code 124}). The remark is a real history item that belongs under its parent (for example "Sleep Pattern" under "Social History"), different from every existing description ignoring case. Saving is
 * REAL: the item is added to the master and stays (it can only be made inactive with its Status, which this page never touches).</p>
 *
 * <p>The Nursing group is looked up INSIDE the Application Configuration entry only (Nursing is also a top-level module of the sidebar).</p>
 */
public class HistoryMaster extends BasePage {

    /** One history item as the screen's own data holds it. */
    public static final class Item {
        public final long id; public final String code, description; public final long parentId;
        Item(long id, String code, String description, long parentId) { this.id = id; this.code = code; this.description = description; this.parentId = parentId; }
    }

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private volatile List<Item> items = new ArrayList<>();

    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean fetch = low.contains("/api/historymaster/fetch");
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!fetch && !write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            if (fetch && r.status() < 400) {
                List<Item> parsed = parseItems(body);
                if (parsed.size() >= 50) items = parsed;                         // the whole tree (the screen also asks for each node's children, small answers)
            }
            if (!write && !bad) return;
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    private static List<Item> parseItems(String body) {
        List<Item> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) return out;
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("HistoryID") || o.get("HistoryID").isJsonNull()) continue;
                out.add(new Item(o.get("HistoryID").getAsLong(), str(o, "Code"), str(o, "Description"), o.has("parentID") && !o.get("parentID").isJsonNull() ? o.get("parentID").getAsLong() : 0));
            }
        } catch (Exception ignore) { }
        return out;
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    public HistoryMaster(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastRemark = "", lastParent = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "HistoryMaster.Code", REMARK_NG = "HistoryMaster.Description", PARENT_NG = "HistoryMaster.parentID";
    private static final String HREF = "#/historymaster";
    private static final String PARENT = "Application Configuration", GROUP = "Nursing";

    /** Real history items as {remark, the parent it belongs under}; the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[][] POOL = {
            {"Sleep Pattern", "Social History"},
            {"Implant or Prosthesis Used", "Surgical History"},
            {"Speech Development", "Developmental History"},
            {"Blood Group", "Blood Transfusion History"},
            {"Pregnancy Outcome", "Obstetric & Gynaecological History"},
            {"Date of Last Tetanus Booster", "Immunisation History"},
            {"Allergy Skin Test Result", "Allergy History"},
            {"Hereditary Condition", "Family History"}
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

    /** Open/shut state of the group {@code label} looked up INSIDE the entry {@code parent}: 1 open, 0 shut, -1 not on screen. */
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
     * Application Configuration &gt; Nursing &gt; History Master. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again,
     * and a click on Nursing straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked. Then the screen is given time to load its tree and the
     * Parent dropdown (the page asks for every node's children).
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__hmLink'; }", HREF);
                try { page.locator("#__hmLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("HistoryMaster.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__hmLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 80 && options().size() < 2; i++) page.waitForTimeout(250);        // the Parent dropdown fills once the tree has loaded
        for (int i = 0; i < 80 && items.size() < 50; i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/historymaster/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CODE_NG));
    }

    // ---- the tree's data ---------------------------------------------------------------------------------------------

    /** Every history item the screen holds (the answer of its last full fetch). Empty until the tree has loaded. */
    public List<Item> items() { return new ArrayList<>(items); }

    public String describe(Item it) { return it.code + " = " + it.description; }

    /** The name of the item with this id ("" if unknown). */
    public String nameOf(long id) {
        for (Item it : items) if (it.id == id) return it.description;
        return "";
    }

    /** The top-level history types (parent 0) as {id, description, number of children}. */
    public List<String[]> topLevel() {
        List<String[]> out = new ArrayList<>();
        for (Item it : items) {
            if (it.parentId != 0) continue;
            long kids = items.stream().filter(x -> x.parentId == it.id).count();
            out.add(new String[]{String.valueOf(it.id), it.description, String.valueOf(kids)});
        }
        return out;
    }

    private boolean descriptionTaken(String remark) {
        for (Item it : items) if (it.description.trim().equalsIgnoreCase(remark.trim())) return true;
        return false;
    }

    private boolean codeTaken(String code) {
        for (Item it : items) if (it.code.trim().equalsIgnoreCase(code.trim())) return true;
        return false;
    }

    /**
     * Choose a {code, remark, parent} that collides with no existing item, ignoring case: the first unused history item of {@code POOL} whose parent is offered (skipping {@code skip} of them for a retry; a
     * numbered item is made once the pool is used up), and the code one above the largest all-digit code in use, stepped past any clash. Sets the {@code last*} fields.
     */
    public String[] pickNew(int skip) {
        String remark = "", parent = "";
        int seen = 0;
        List<String> offered = new ArrayList<>();
        for (String[] o : options()) offered.add(o[0]);
        for (int round = 0; round < 50 && remark.isEmpty(); round++) {
            for (String[] p : POOL) {
                String cand = round == 0 ? p[0] : p[0] + " " + (round + 1);
                if (descriptionTaken(cand) || !offered.contains(p[1])) continue;
                if (seen++ < skip) continue;
                remark = cand;
                parent = p[1];
                break;
            }
        }
        long max = 0;
        for (Item it : items) if (it.code.trim().matches("\\d+")) max = Math.max(max, Long.parseLong(it.code.trim()));
        String code = "";
        int seenCode = 0;
        for (long n = max + 1; n < max + 10000 && code.isEmpty(); n++) {
            String cand = String.valueOf(n);
            if (codeTaken(cand)) continue;
            if (seenCode++ < skip) continue;
            code = cand;
        }
        lastCode = code;
        lastRemark = remark;
        lastParent = parent;
        return new String[]{code, remark, parent};
    }

    /** True once an item with this code and remark, filed under this parent, appears in the screen's data (it asks for the tree again after a save); waits up to {@code ms}. */
    public Item awaitItem(String code, String remark, String parentName, int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            for (Item it : items) {
                if (it.code.trim().equalsIgnoreCase(code) && it.description.trim().equalsIgnoreCase(remark) && (parentName.isEmpty() || nameOf(it.parentId).equalsIgnoreCase(parentName))) return it;
            }
            page.waitForTimeout(500);
        } while (System.currentTimeMillis() < end);
        return null;
    }

    // ---- the form ---------------------------------------------------------------------------------------------------

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(30));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterRemark(String remark) { problems.clear(); return type(REMARK_NG, remark, "Remark"); }

    /** The options of the Parent dropdown as {text, value} (placeholder excluded). */
    public List<String[]> options() {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", PARENT_NG);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Select the Parent with this exact name (the first one of that name) and make angular/select2 see it; returns the selected text, "" when it could not be. */
    public String selectParent(String name) {
        problems.clear();
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(PARENT_NG, name));
        String got = r == null ? "" : r.toString();
        waitForAngular(600);
        if (got.isEmpty()) { problems.add("the Parent \"" + name + "\" could not be selected"); return ""; }
        String m = model(PARENT_NG);
        if (m.isEmpty()) problems.add("the Parent is not held by the model after selecting \"" + got + "\"");
        lastParent = got;
        return got;
    }

    // ---- save ------------------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__hmToasts=[]; if(window.__hmObs) window.__hmObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__hmToasts.includes(m)) window.__hmToasts.push(m); });"
                + " window.__hmObs=new MutationObserver(add); window.__hmObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__hmToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && b.id!=='btnSave' && !/^fnIUD/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__hmDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__hmDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("HistoryMaster.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__hmDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=document.getElementById('btnSave'); if(!b || !vis(b) || b.disabled) return false; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen or is disabled"); return ""; }
        try { page.locator("#btnSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
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
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(200, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The screen refused because the code or remark already exists. */
    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /** The server answered that it saved (ResultStatus 1), whatever toast the screen shows. */
    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
