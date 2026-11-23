package com.kpj.pages.AncillaryServices_page.KitchenMenu_page;

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
 * Ancillary Services &gt; Kitchen &amp; Menu &gt; <b>Day Specific Menu</b> — Page Object (list {@code #/DaySpecificMenuList}, form {@code #/add-DaySpecificMenu}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.392). The list ({@code ui-grid #grid1}: Day | Meal Type | Menu Details (eye) | Valid Till | Added By | Added Date &amp; Time | Modify (pencil); the Meal Type and Day
 * filters sit above it) loads from {@code POST /api/DaySpecificMenu/fetch} and has <b>Add Menu</b> ({@code AddDaySpecificMenu()}). The form "Add New Menu" (model {@code DaySpecificMenu.*}):</p>
 * <ul>
 *   <li><b>Meal Type*</b> ({@code .MealTypeId}) and <b>Day*</b> ({@code .DayOfWeek}, Monday-Sunday): select2 over a plain select. The Meal Type list carries many same-named entries (19 "Breakfast", 2 "Dinner", 3 "Hi-Tea"), so a
 *       uniquely named one is preferred.</li>
 *   <li><b>Valid From*</b> ({@code .ValidFrom}) and <b>Valid To*</b> ({@code .ValidTo}): NATIVE {@code <input type=date>} (filled with yyyy-MM-dd; the model holds a Date).</li>
 *   <li>Special Instructions (optional), <b>Diet Type</b> and <b>Therapeutic Diet</b> (both default to "All").</li>
 *   <li><b>Food Item*</b> ({@code .FoodItemId}, select2; its {@code ng-change fnAddMenuItem()} puts the chosen item into the <b>Menu Item List</b> table: Food Item | Diet Type | Therapeutic Diet | Delete; empty = "No items added").</li>
 *   <li><b>Save</b> ({@code IUDDaySpecificMenu()}) and Clear ({@code clearForm()}).</li>
 * </ul>
 *
 * <p>The server does not forbid overlapping menus for the same day and meal type (it already holds several), but a free combination is preferred so the new menu stands out.</p>
 *
 * <p>Saving is REAL: a day-specific menu is created and applies from its Valid From to its Valid To.</p>
 */
public class DaySpecificMenu extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public DaySpecificMenu(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastMealType = "", lastDay = "", lastFrom = "", lastTo = "", lastFood = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastItemRow = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "DaySpecificMenu.";
    private static final String HREF = "#/dayspecificmenulist";
    private static final String PARENT = "Ancillary Services", GROUP = "Kitchen & Menu";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

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
     * Ancillary Services &gt; Kitchen &amp; Menu &gt; Day Specific Menu. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again,
     * and a click on Kitchen &amp; Menu straight after Ancillary Services is lost). The link is found by its exact route.
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__dsLink'; }", HREF);
                try { page.locator("#__dsLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DaySpecificMenu.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__dsLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddButton();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddButton() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a,button')].some(x=>/^AddDaySpecificMenu/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DaySpecificMenu.nav: the Add Menu button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/dayspecificmenulist/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-dayspecificmenu/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"select[ng-model='" + M + "MealTypeId']\")"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__dsToasts=[]; if(window.__dsObs) window.__dsObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__dsToasts.includes(m)) window.__dsToasts.push(m); });"
                + " window.__dsObs=new MutationObserver(add); window.__dsObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__dsToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the existing menus --------------------------------------------------------------------------------------------------

    /** The menus the server holds as {id, meal type, day, valid from (dd/MM/yyyy), valid to (dd/MM/yyyy)}; null when they could not be read. Asked with the call the list makes. */
    public List<String[]> existingMenus() {
        Object r = page.evaluate("() => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/DaySpecificMenu/fetch', {ExecFlag:'GRID'}).then(x=>res(JSON.stringify((x.data||[]).map(d=>[d.ID,d.MealTypeName,d.DayOfWeek,d.ValidFrom,d.ValidTo]))), e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })");
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String[]> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement row : a) {
                JsonArray c = row.getAsJsonArray();
                String[] v = new String[5];
                for (int i = 0; i < 5; i++) v[i] = c.get(i).isJsonNull() ? "" : c.get(i).getAsString().trim();
                v[4] = v[4].replaceAll("\\s+\\d{2}:\\d{2}.*$", "");           // "10/12/2026 00:00" -> "10/12/2026"
                out.add(v);
            }
        } catch (Exception e) { return null; }
        return out;
    }

    private static LocalDate parse(String ddMMyyyy) {
        try { return LocalDate.parse(ddMMyyyy, DMY); } catch (Exception e) { return null; }
    }

    /** True when a menu of this meal type (by name) for this day already covers any date of from..to. */
    public static boolean overlaps(List<String[]> menus, String meal, String day, LocalDate from, LocalDate to) {
        if (menus == null) return false;
        for (String[] m : menus) {
            if (!m[1].equalsIgnoreCase(meal.trim()) || !m[2].equalsIgnoreCase(day)) continue;
            LocalDate f = parse(m[3]), t = parse(m[4]);
            if (f == null || t == null) return true;
            if (!(t.isBefore(from) || f.isAfter(to))) return true;
        }
        return false;
    }

    /** The first day (Monday first) with no menu of this meal type covering from..to; "" when every day has one. */
    public String freeDay(String meal, LocalDate from, LocalDate to) {
        List<String[]> menus = existingMenus();
        for (String d : DAYS) if (!overlaps(menus, meal, d, from, to)) return d;
        return "";
    }

    /** True when the server holds a menu of this meal type and day with exactly these dates (read afresh); the matching id is left in {@code lastMenuId}. */
    public String lastMenuId = "";
    public boolean serverHolds(String meal, String day, LocalDate from, LocalDate to) {
        List<String[]> menus = existingMenus();
        lastMenuId = "";
        if (menus == null) return false;
        for (String[] m : menus) {
            if (m[1].equalsIgnoreCase(meal.trim()) && m[2].equalsIgnoreCase(day) && from.format(DMY).equals(m[3]) && to.format(DMY).equals(m[4])) { lastMenuId = m[0]; return true; }
        }
        return false;
    }

    // ---- the form -----------------------------------------------------------------------------------------------------

    /** Click Add Menu on the list and wait for the form with its Meal Type and Food Item lists filled. */
    public boolean clickAddMenu() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^AddDaySpecificMenu/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dsAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add Menu button is not on screen"); return false; }
        try { page.locator("#__dsAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add Menu button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__dsAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Add New Menu form did not open (" + page.url() + ")"); return false; }
        for (int i = 0; i < 80 && (options(M + "MealTypeId").size() < 2 || options(M + "FoodItemId").isEmpty()); i++) page.waitForTimeout(250);
        if (options(M + "MealTypeId").isEmpty()) problems.add("the Meal Type dropdown has no options");
        if (options(M + "FoodItemId").isEmpty()) problems.add("the Food Item dropdown has no options");
        waitForAngular(800);
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

    public List<String> optionNames(String ng) {
        List<String> out = new ArrayList<>();
        for (String[] o : options(ng)) out.add(o[0]);
        return out;
    }

    /** Select the FIRST option with this exact text (ignoring case) and make angular/select2 see the change; returns the selected text, "" if it could not be selected. */
    private String choose(String ng, String text, String what) { return choose(ng, text, what, true); }

    /**
     * Select the FIRST option with this exact text (ignoring case) and make angular/select2 see the change; returns the text of the option picked, "" if it could not be selected. {@code expectModel} is false for
     * the Food Item: its change handler adds the item to the list and then CLEARS the model (so another item can be added), which would read as "not held".
     */
    private String choose(String ng, String text, String what, boolean expectModel) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " const picked=norm(e.options[i].textContent);"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return picked; }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(700);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        if (expectModel) {
            Object model = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
            if (model == null || model.toString().trim().isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        }
        return got;
    }

    /** Select the Meal Type: the first of the preferred names that is offered exactly ONCE (the list repeats several names), else the first option. */
    public String selectMealType(List<String> preferred) {
        problems.clear();
        List<String> all = optionNames(M + "MealTypeId");
        if (all.isEmpty()) { problems.add("the Meal Type dropdown has no options"); return ""; }
        String pick = all.get(0);
        outer:
        for (String p : preferred) {
            int n = 0;
            for (String a : all) if (a.equalsIgnoreCase(p)) n++;
            if (n == 1) { pick = p; break outer; }
        }
        lastMealType = choose(M + "MealTypeId", pick, "Meal Type");
        return lastMealType;
    }

    public String selectDay(String day) {
        problems.clear();
        lastDay = choose(M + "DayOfWeek", day, "Day");
        return lastDay;
    }

    // ---- Valid From / Valid To (native date boxes) ----------------------------------------------------------------------------------------

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n);"
                + " if(m instanceof Date) return m.getFullYear()+'-'+String(m.getMonth()+1).padStart(2,'0')+'-'+String(m.getDate()).padStart(2,'0'); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** Fill a native date box with yyyy-MM-dd and check the box and the model (a Date) hold it. */
    private boolean setDate(String ng, LocalDate date, String what) {
        String iso = date.toString();
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.fill(iso, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the " + what + " could not be filled: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(500);
        Object box = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng);
        if (box == null || !iso.equals(box.toString())) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + iso + "\"");
        else if (!iso.equals(model(ng))) problems.add("the " + what + " is not held by the model (\"" + model(ng) + "\")");
        return ok();
    }

    public boolean enterValidFrom(LocalDate from) { problems.clear(); boolean ok = setDate(M + "ValidFrom", from, "Valid From"); if (ok) lastFrom = from.format(DMY); return ok; }

    public boolean enterValidTo(LocalDate to) { problems.clear(); boolean ok = setDate(M + "ValidTo", to, "Valid To"); if (ok) lastTo = to.format(DMY); return ok; }

    // ---- Food Item and the Menu Item List ------------------------------------------------------------------------------------------------

    /** The rows of the Menu Item List (Food Item | Diet Type | Therapeutic Diet), each as one text; the "No items added" placeholder is not a row. */
    public List<String> itemRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Food Item/.test(x.textContent) && /Therapeutic Diet/.test(x.textContent) && /Delete/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).join(' | ')).filter(x=>x && !/No items added/i.test(x)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public List<String> foodNames() { return optionNames(M + "FoodItemId"); }

    /** Select the Food Item: the first of the preferred names that is offered exactly once, else the first option. Its change adds it to the Menu Item List, which is awaited. */
    public String selectFoodItem(List<String> preferred) {
        problems.clear();
        lastItemRow = "";
        List<String> all = foodNames();
        if (all.isEmpty()) { problems.add("the Food Item dropdown has no options"); return ""; }
        String pick = all.get(0);
        outer:
        for (String p : preferred) {
            int n = 0;
            for (String a : all) if (a.equalsIgnoreCase(p)) n++;
            if (n == 1) { pick = p; break outer; }
        }
        List<String> before = itemRows();
        armToasts();
        lastFood = choose(M + "FoodItemId", pick, "Food Item", false);
        if (!ok()) return "";
        List<String> after = before;
        for (int i = 0; i < 24; i++) { after = itemRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        if (after.size() <= before.size()) { problems.add("selecting the Food Item put no row into the Menu Item List" + (toasts().isEmpty() ? "" : " (toast: \"" + toasts() + "\")")); return ""; }
        for (String r : after) if (!before.contains(r)) lastItemRow = r;
        if (lastItemRow.isEmpty()) lastItemRow = after.get(after.size() - 1);
        return lastFood;
    }

    // ---- Save ----------------------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(IUDDaySpecificMenu|clearForm)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__dsDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__dsDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("DaySpecificMenu.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__dsDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^IUDDaySpecificMenu/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dsSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__dsSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__dsSave'); if(e) e.removeAttribute('id'); }"); }
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

    /** After a successful save the screen should return to the list (waits for it). */
    public boolean awaitList(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (onList()) return true; page.waitForTimeout(250); }
        return onList();
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
}
