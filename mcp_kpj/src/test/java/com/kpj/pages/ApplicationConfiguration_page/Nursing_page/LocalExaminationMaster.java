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
 * Application Configuration &gt; Nursing &gt; <b>Local Examination Master</b> — Page Object ({@code #/LocalExamination}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.382). One screen with no Add button and no list grid: the inline form (model {@code LocalExamination.*}) — <b>Group*</b> (select2, {@code .groupid}, 88 options;
 * its change runs {@code fnGetLocalExamination()}, which asks for that group's entries with {@code POST /api/LocalExamination/Fetch}), <b>Patient Complaints*</b> ({@code #txtcode}, {@code .Code} — the
 * complaint text is stored in the field called Code) and <b>Parent*</b> (select2, {@code .parentID}, filled with the group's existing entries) — the buttons <b>Save</b> ({@code #btnSave},
 * {@code fnIUDLocalExamination()}), <b>Modify</b> ({@code #btnModify}) and <b>Cancel</b> ({@code resetForm()}), and a <b>Local Examination Details</b> panel below it.</p>
 *
 * <p>The group's entries are a small tree of {@code {LocalExaminationID, groupid, groupname, Code, parentID}}: for the group Bed they were {@code Test} (the root: its parent is itself), {@code Test1},
 * {@code Test2}, {@code Test_SL} (under Test) and {@code TEST}, {@code Test_SL} (under Test2). Only groups that already have entries offer any Parent (Bed 6, Catherisation Laboratory 3; the others tried offered
 * none), and Parent is required, so the group is chosen among those. This page object reads the group's entries from the network (the screen asks again after a save) to pick a complaint that is not among
 * them and to verify the new entry arrives under its parent; the "Local Examination Details" panel is checked separately in the page itself.</p>
 *
 * <p>Saving is REAL: the entry is added to the master and stays (it can only be made inactive with its Status, which this page never touches).</p>
 *
 * <p>The Nursing group is looked up INSIDE the Application Configuration entry only (Nursing is also a top-level module of the sidebar).</p>
 */
public class LocalExaminationMaster extends BasePage {

    /** One entry of a group as the screen's own data holds it. */
    public static final class Entry {
        public final long id, groupId, parentId; public final String group, code;
        Entry(long id, long groupId, String group, String code, long parentId) { this.id = id; this.groupId = groupId; this.group = group; this.code = code; this.parentId = parentId; }
    }

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    /** The latest NON-EMPTY answer per group (lower-case group name): the screen asks twice when a group is chosen and one of the answers is an empty list, which must not wipe out the real one. */
    private final java.util.Map<String, List<Entry>> byGroup = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int fetchCount = 0;

    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean fetch = low.contains("/api/localexamination/fetch");
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!fetch && !write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            if (fetch && r.status() < 400) {
                List<Entry> parsed = parseEntries(body);
                if (!parsed.isEmpty()) byGroup.put(parsed.get(0).group.toLowerCase(), parsed);
                fetchCount++;
            }
            if (!write && !bad) return;
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    private static List<Entry> parseEntries(String body) {
        List<Entry> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) return out;
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("LocalExaminationID") || o.get("LocalExaminationID").isJsonNull()) continue;
                out.add(new Entry(o.get("LocalExaminationID").getAsLong(), num(o, "groupid"), str(o, "groupname"), str(o, "Code"), num(o, "parentID")));
            }
        } catch (Exception ignore) { }
        return out;
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static long num(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0; }

    public LocalExaminationMaster(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastGroup = "", lastComplaint = "", lastParent = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String GROUP_NG = "LocalExamination.groupid", COMPLAINT_NG = "LocalExamination.Code", PARENT_NG = "LocalExamination.parentID";
    private static final String HREF = "#/localexamination";
    private static final String PARENT = "Application Configuration", GROUP = "Nursing";

    /** Real local-examination findings a patient complains of, by group; the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[] BED = {"Pressure sore over the sacrum", "Swelling of the left ankle", "Redness around the surgical wound", "Tenderness over the calf muscle", "Pain on turning in bed"};
    private static final String[] CATH_LAB = {"Haematoma at the femoral puncture site", "Bleeding at the radial access site", "Numbness distal to the puncture site"};
    private static final String[] OTHER = {"Localised swelling with tenderness", "Skin redness and warmth", "Pain on movement of the joint", "Limited range of movement"};

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
     * Application Configuration &gt; Nursing &gt; Local Examination Master. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the
     * group again, and a click on Nursing straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__leLink'; }", HREF);
                try { page.locator("#__leLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("LocalExaminationMaster.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__leLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 80 && groupOptions().size() < 2; i++) page.waitForTimeout(250);        // the Group dropdown fills when the screen has loaded
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/localexamination/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", COMPLAINT_NG));
    }

    // ---- dropdowns ---------------------------------------------------------------------------------------------------

    private List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    public List<String[]> groupOptions() { return options(GROUP_NG); }

    /** The Parent options as {text, value} for the group now selected. */
    public List<String[]> parentOptions() { return options(PARENT_NG); }

    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(500);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        return got;
    }

    /**
     * Select this group and wait for the screen's answer about it (its entries are fetched on change and fill the Parent dropdown). Returns the group selected, "" if it could not be; the group's entries are then in
     * {@link #entries()} and its parents in {@link #parentOptions()}.
     */
    public String selectGroup(String name) {
        problems.clear();
        int before = fetchCount;
        String got = choose(GROUP_NG, name, "Group");
        if (got.isEmpty()) return "";
        for (int i = 0; i < 40 && fetchCount == before; i++) page.waitForTimeout(250);          // up to 10 s for the first answer
        for (int i = 0; i < 12 && !byGroup.containsKey(got.toLowerCase()); i++) page.waitForTimeout(250);   // the real answer can follow an empty one; a group without entries just waits these 3 s
        page.waitForTimeout(1000);                                                                 // then the dropdown refills
        lastGroup = got;
        return got;
    }

    /**
     * Select the first of the {@code preferred} groups that offers at least one Parent (Parent is required, and only a group that already has entries offers any); if none does, scan the other groups in the
     * dropdown's order, at most {@code maxScan} of them. Returns the group selected, "" when no group with a Parent was found. {@code tried} receives what was looked at.
     */
    public String selectGroupWithParents(List<String> preferred, int maxScan, StringBuilder tried) {
        List<String> names = new ArrayList<>();
        for (String[] o : groupOptions()) names.add(o[0]);
        List<String> order = new ArrayList<>();
        for (String p : preferred) if (names.contains(p)) order.add(p);
        int extra = 0;
        for (String n : names) if (!order.contains(n) && extra++ < maxScan) order.add(n);
        for (String g : order) {
            String got = selectGroup(g);
            int parents = got.isEmpty() ? 0 : parentOptions().size();
            tried.append(tried.length() == 0 ? "" : "; ").append(g).append(": ").append(got.isEmpty() ? "could not be selected" : parents + " parent(s)");
            if (parents > 0) return got;
        }
        problems.add("no group offering a Parent was found (" + tried + ")");
        return "";
    }

    /** The entries of the group on screen (the screen's latest non-empty answer for it; empty for a group that has none). */
    public List<Entry> entries() {
        List<Entry> l = byGroup.get(lastGroup.toLowerCase());
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public String nameOf(long id) {
        for (Entry e : entries()) if (e.id == id) return e.code;
        return "";
    }

    private boolean complaintTaken(String c) {
        for (Entry e : entries()) if (e.code.trim().equalsIgnoreCase(c.trim())) return true;
        return false;
    }

    /**
     * Choose a complaint for the group on screen that is not among its entries, ignoring case: the first unused one of the group's pool (skipping {@code skip} of them for a retry; a numbered one once the pool is
     * used up). Sets {@link #lastComplaint}.
     */
    public String pickComplaint(int skip) {
        String[] pool = "Bed".equals(lastGroup) ? BED : lastGroup.toLowerCase().startsWith("cath") ? CATH_LAB : OTHER;
        String out = "";
        int seen = 0;
        for (int round = 0; round < 50 && out.isEmpty(); round++) {
            for (String base : pool) {
                String cand = round == 0 ? base : base + " " + (round + 1);
                if (complaintTaken(cand)) continue;
                if (seen++ < skip) continue;
                out = cand;
                break;
            }
        }
        lastComplaint = out;
        return out;
    }

    /** The entry with this complaint under this parent in the screen's latest data for the group (waits up to {@code ms} for the refetch after a save); null if it is not there. */
    public Entry awaitEntry(String complaint, String parentName, int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            for (Entry e : entries()) {
                if (e.code.trim().equalsIgnoreCase(complaint) && (parentName.isEmpty() || nameOf(e.parentId).equalsIgnoreCase(parentName))) return e;
            }
            page.waitForTimeout(500);
        } while (System.currentTimeMillis() < end);
        return null;
    }

    // ---- the form ----------------------------------------------------------------------------------------------------

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public boolean enterComplaint(String text) {
        problems.clear();
        Locator in = page.locator("input[ng-model='" + COMPLAINT_NG + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(25));
        } catch (Exception e) { problems.add("the Patient Complaints could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", COMPLAINT_NG)).trim();
        if (!text.equals(box)) problems.add("the Patient Complaints box shows \"" + box + "\" instead of \"" + text + "\"");
        if (!text.equals(model(COMPLAINT_NG))) problems.add("the Patient Complaints are not held by the model");
        return ok();
    }

    /** Select the Parent with this name; if it is not offered, the first one. Returns the Parent selected, "" if none could be. */
    public String selectParent(String preferred) {
        problems.clear();
        List<String[]> opts = parentOptions();
        if (opts.isEmpty()) { problems.add("the Parent dropdown offers nothing for the group " + lastGroup); return ""; }
        String want = opts.get(0)[0];
        for (String[] o : opts) if (o[0].equalsIgnoreCase(preferred)) { want = o[0]; break; }
        String got = choose(PARENT_NG, want, "Parent");
        if (got.isEmpty()) return "";
        if (model(PARENT_NG).isEmpty()) { problems.add("the Parent is not held by the model after selecting \"" + got + "\""); return ""; }
        lastParent = got;
        return got;
    }

    // ---- the Local Examination Details panel ---------------------------------------------------------------------------------

    /**
     * True when the body of the <b>Local Examination Details</b> panel ({@code div.panel-primary > div.panel-body}) shows this text in a visible element. A panel that is blank, or does not show the new entry,
     * gives false — which is a FAIL of the verification, whatever the server answered.
     */
    public boolean shownInDetailsPanel(String text) {
        Object r = page.evaluate("(t) => {" + JS + " const h=[...document.querySelectorAll('.panel-heading')].find(x=>/^\\s*Local Examination Details\\s*$/.test(x.textContent||'')); if(!h) return false;"
                + " const body=h.closest('.panel')? h.closest('.panel').querySelector('.panel-body') : null; if(!body) return false;"
                + " return [...body.querySelectorAll('*')].some(e=>e.children.length===0 && vis(e) && norm(e.textContent).includes(t)); }", text);
        return Boolean.TRUE.equals(r);
    }

    /** Wait up to {@code ms} for the text to appear in the details panel; returns whether it did. */
    public boolean awaitInDetailsPanel(String text, int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            if (shownInDetailsPanel(text)) return true;
            page.waitForTimeout(500);
        } while (System.currentTimeMillis() < end);
        return false;
    }

    /** What the Local Examination Details panel shows now (its text), "" when it is empty. */
    public String detailsPanelText() {
        Object r = page.evaluate("() => {" + JS + " const h=[...document.querySelectorAll('.panel-heading')].find(x=>/^\\s*Local Examination Details\\s*$/.test(x.textContent||'')); if(!h) return '(no panel)';"
                + " const body=h.closest('.panel')? h.closest('.panel').querySelector('.panel-body') : null; return body? norm(body.textContent) : '(no panel body)'; }");
        return r == null ? "" : r.toString();
    }

    // ---- save --------------------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__leToasts=[]; if(window.__leObs) window.__leObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__leToasts.includes(m)) window.__leToasts.push(m); });"
                + " window.__leObs=new MutationObserver(add); window.__leObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__leToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && b.id!=='btnSave' && !/^fnIUD/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__leDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__leDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("LocalExaminationMaster.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__leDlg'); if(e) e.removeAttribute('id'); }");
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

    /** The screen refused because the complaint already exists. */
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
