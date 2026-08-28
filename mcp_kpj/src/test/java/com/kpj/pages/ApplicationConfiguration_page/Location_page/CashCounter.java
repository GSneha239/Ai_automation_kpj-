package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>CashCounter</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> (submenu) → <b>CashCounter</b> → <b>Add</b> →
 * enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>The values are taken FROM THE TABLE.</b> {@link #sampleTablePattern()} reads the list grid's headers,
 * finds the Code and Remark columns by name, and continues the sequence the screen already uses — {@code R001},
 * {@code R002} → {@code R003}; "Room 101", "Room 102" → "Room 103". This is not cosmetic: a sibling master
 * (Interpretation Template) casts existing codes to {@code int} in its save proc, so one invented
 * {@code XX#####} row bricked that screen for everyone. Continuing the table's own format avoids planting that.</p>
 */
public class CashCounter extends BasePage {

    public CashCounter(Page page) { super(page); }

    /**
     * Cash Counter has its OWN route, unlike its Location neighbours Room No and Bank which share the generic
     * CommonMaster screen at {@code #/CABIN}. There is therefore no Form Name drop-down to set here — the
     * {@link #selectFormName} step is skipped when the screen does not offer one.
     */
    public static final String ROUTE = "#/cashcounter";
    public String lastCode = "", lastRemark = "", lastCodeModel = "", lastRemarkModel = "";
    /** What the table showed and what was derived from it — reported so the match is auditable. */
    public String patternNote = "";
    private final java.util.List<String> tableCodes = new java.util.ArrayList<>();
    private final java.util.List<String> tableRemarks = new java.util.ArrayList<>();
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /**
     * Cash counters a hospital would actually have, as {code, name} — used only when the table carries no
     * numbered sequence to continue. Whatever this run writes becomes the pattern the NEXT run copies, so these
     * are deliberately plausible rather than machine noise.
     */
    private static final String[][] COUNTERS = {
            { "CC01", "Main Lobby Counter" },
            { "CC02", "Outpatient Counter 1" },
            { "CC03", "Outpatient Counter 2" },
            { "CC04", "Pharmacy Counter" },
            { "CC05", "Emergency Counter" },
            { "CC06", "Inpatient Billing Counter" },
            { "CC07", "Radiology Counter" },
            { "CC08", "Day Care Counter" }
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * Continue the table by its COMMON SHAPE rather than a trailing number.
     *
     * <p>This screen's codes are {@code ADM5-C-01}, {@code ADM6-C-01}, {@code ADM7-C-01}, {@code ADMG-C-01}: the
     * part that varies sits in the MIDDLE, so a "trailing number" rule finds nothing and falls back to inventing
     * a code. Peeling the longest common prefix ({@code ADM}) and suffix ({@code -C-01}) exposes the variable
     * part ({@code 5,6,7,G}); the numeric ones give the next value, and the affixes are put back —
     * {@code ADM8-C-01}. Returns null when the samples share no shape.</p>
     */
    /**
     * The largest group of codes sharing a SHAPE, digits masked ({@code ADM5-C-01} and {@code ADM6-C-01} are both
     * {@code ADM#-C-##}). A 25-row page holds several unrelated shapes, so demanding a common affix across ALL of
     * them finds nothing; the dominant shape is what "the table's format" actually means.
     */
    static java.util.List<String> dominantShape(java.util.List<String> samples) {
        java.util.Map<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        for (String s : samples) {
            if (s == null || s.isEmpty()) continue;
            groups.computeIfAbsent(s.replaceAll("\\d", "#"), k -> new java.util.ArrayList<>()).add(s);
        }
        java.util.List<String> best = null;
        for (java.util.List<String> v : groups.values()) if (best == null || v.size() > best.size()) best = v;
        return best == null ? java.util.Collections.emptyList() : best;
    }

    static String nextByCommonAffix(java.util.List<String> samples, int attempt) {
        java.util.List<String> s = new java.util.ArrayList<>();
        for (String x : samples) if (x != null && !x.isEmpty()) s.add(x);
        if (s.size() < 2) return null;

        String first = s.get(0);
        int pre = first.length(), suf = first.length();
        for (String x : s) {
            int i = 0;
            while (i < pre && i < x.length() && x.charAt(i) == first.charAt(i)) i++;
            pre = i;
            int j = 0;
            while (j < suf && j < x.length() && x.charAt(x.length() - 1 - j) == first.charAt(first.length() - 1 - j)) j++;
            suf = j;
        }
        // Keep the affixes from overlapping on the shortest sample.
        int shortest = s.stream().mapToInt(String::length).min().orElse(0);
        if (pre + suf > shortest) suf = Math.max(0, shortest - pre);
        if (pre == 0 && suf == 0) return null;

        String prefix = first.substring(0, pre);
        String suffix = suf == 0 ? "" : first.substring(first.length() - suf);

        long max = -1;
        int width = 0;
        for (String x : s) {
            if (x.length() < pre + suf) continue;
            String mid = x.substring(pre, x.length() - suf);
            if (!mid.matches("\\d+")) continue;          // ADMG's "G" is not part of the sequence
            width = Math.max(width, mid.length());
            try { max = Math.max(max, Long.parseLong(mid)); } catch (Exception ignore) { }
        }
        if (max < 0) return null;
        String next = String.valueOf(max + 1 + attempt);
        StringBuilder sb = new StringBuilder(next);
        while (sb.length() < width) sb.insert(0, '0');
        return prefix + sb + suffix;
    }

    /**
     * Build the <b>Cash Counter</b> name the way the table does. Every row here reads
     * {@code <code> (<place>)} — "ADM5-C-01 (Ward Level 5)" — so the name is derived from the NEW code, and when
     * the sample's parenthetical ends with that sample's own varying part, the new value is substituted so
     * "Ward Level 5" becomes "Ward Level 8" instead of a mismatched label.
     */
    static String nameLikeTable(String newCode, java.util.List<String> codes, java.util.List<String> names) {
        if (codes.isEmpty() || names.isEmpty()) return null;
        String sampleCode = codes.get(0), sampleName = null;
        for (String n : names) if (n.startsWith(sampleCode)) { sampleName = n; break; }
        if (sampleName == null) return null;                      // names are not "<code> (...)" here

        String tail = sampleName.substring(sampleCode.length()).trim();   // "(Ward Level 5)"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\((.*)\\)$").matcher(tail);
        if (!m.matches()) return newCode + (tail.isEmpty() ? "" : " " + tail);
        String inner = m.group(1);                                        // "Ward Level 5"

        // Substitute the varying part when the description is numbered in step with the code.
        java.util.regex.Matcher oldNum = java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(inner);
        java.util.regex.Matcher newNum = java.util.regex.Pattern.compile("(\\d+)").matcher(newCode);
        if (oldNum.find() && newNum.find()) {
            inner = inner.substring(0, oldNum.start(1)) + newNum.group(1);
        }
        return newCode + " (" + inner + ")";
    }

    /** Splits "Room 101" / "R001" into its text part and its trailing number. */
    private static final java.util.regex.Pattern SPLIT_NUM = java.util.regex.Pattern.compile("^(.*?)(\\d+)\\s*$");
    /** Cell / row / grid separators — control characters no screen value will contain. */
    private static final String CELL = String.valueOf((char) 2), ROW = String.valueOf((char) 1),
            GRID = String.valueOf((char) 3);

    /**
     * JS helper: the visible <b>Code</b> and <b>Remark</b> inputs, whatever they are called here. Each match is
     * CLAIMED ({@code used}) so the two roles can never resolve to the same box. select2 chrome
     * ({@code s2id_autogen*}) is skipped, but a real {@code <select>} would not be — select2 marks the field it
     * wraps with class {@code select2-offscreen}.
     */
    private static final String FIND_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const lbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const skip=m=>/colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m||'');"
            + " const s2=e=>/^s2id_autogen/i.test(e.id||'') || (e.tagName!=='SELECT' && /select2/i.test(e.className||''));"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !s2(e) && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            // "Cash Counter Code" CONTAINS "Cash Counter", so the code box must be claimed first — otherwise the
            // name picker takes it and the code is written into the wrong field (or both roles collapse onto one).
            + " const codeEl = pick(e=>/code/i.test(lbl(e)), e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)), e=>/^\\s*code\\s*$/i.test(plc(e)));"
            + " const remEl  = pick(e=>/cash\\s*counter|counter\\s*name/i.test(lbl(e)), e=>/^(remark|description|name)/i.test(lbl(e)),"
            + "                     e=>/\\.(description|remark|name)$/i.test(mdl(e)), e=>/description|remark|countername/i.test(mdl(e)));";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            // The WHOLE menu-entry block is guarded: clicking a menu link routes the SPA, which destroys the JS
            // execution context mid-evaluate ("Execution context was destroyed"). That is a SUCCESSFUL navigation,
            // not a failure — swallow it and let onScreen()/the direct-route fallback decide where we landed.
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Location" — a prefix match expands e.g. "Location Master" or another module's menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1500);
                // EXACT text — a bare "Room" match would hit the Admission submenu's own Room screen
                // Go in via "Consultation Room" (#/CABIN) — the "CashCounter Name" link's own #/BANK route is dead, so
                // clicking it just leaves the dashboard up. Both open the SAME CommonMaster screen; Form Name is
                // what selects the CashCounter master afterwards.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*cash\\s*counters?\\s*$/i.test(norm(x.textContent)))"
                        + "   || [...document.querySelectorAll('a[href]')].find(x=>/cashcounter/i.test(x.getAttribute('href')||''));"
                        + " if(!a) return ''; a.id='__ccMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    System.out.println("CashCounter.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    // Dump the menu once so a wrong guess at the label is diagnosable from THIS run rather than
                    // the next one — the submenu parent's wording is what usually differs.
                    if (attempt == 0) {
                        System.out.println("CashCounter.nav: menu now shows => " + dumpMenu());
                        System.out.println("CashCounter.nav: room-like links anywhere in the menu => " + findRoomLinks());
                    }
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__ccMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("CashCounter.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__ccMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__ccMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("CashCounter.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("CashCounter.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        // The Code/Remark pair can arrive a beat after the route does — poll rather than judging on one look,
        // otherwise a slow render is indistinguishable from the leftover "Transfer" screen this build serves.
        for (int i = 0; i < 10 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    /**
     * On the CashCounter screen. The URL is checked against the route MINED FROM THE MENU where possible, because a
     * screen's hash need not contain its name ("Lab Sample" routes to {@code #/LabTestSampleType}). The field gate
     * demands a Code AND a Remark: the leftover "Transfer" screen renders a Code and a Submit but no Remark.
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        String want = (menuHref.isEmpty() ? ROUTE : menuHref).replaceAll("^#/?", "").toLowerCase();
        boolean routeOk = (!want.isEmpty() && u.contains(want)) || u.contains("cashcounter");
        if (!routeOk) return false;
        return Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }"));
    }

    /**
     * PROBE — expand EVERY submenu and report each link whose text or href mentions "room", with the submenu it
     * sits under. Answers "is this screen here at all, and under which parent?" in ONE run instead of one
     * build-and-run per guess.
     */
    public String findRoomLinks() {
        // NO clicking: a collapsed submenu still holds its children in the DOM (they are only hidden by CSS), so
        // scanning every anchor — visible or not — finds the screen without routing the SPA. Clicking the parents
        // instead navigates and kills the JS context mid-scan.
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')]"
                + "   .filter(a=>{ const t=norm(a.textContent), h=a.getAttribute('href')||''; return /room|bank/i.test(t) || /room|bank/i.test(h); })"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,32)+' -> '+(a.getAttribute('href')||'')"
                + "     +(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** PROBE — every visible menu anchor (text + href), for when a label guess misses. */
    public String dumpMenu() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('a')].filter(a=>a.offsetParent!==null)"
                + "   .map(a=>norm(a.textContent).slice(0,32)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,120).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- form name + grid load -------------------------------------------

    /**
     * Set the <b>Form Name</b> drop-down, which is what decides WHICH master this screen edits.
     *
     * <p>{@code #/BANK} is the same generic CommonMaster screen as {@code #/CABIN}: one component, and a
     * ~177-option Form Name select choosing the master behind it. The route sets a default, but relying on that
     * default means a changed default silently writes rows into a DIFFERENT master — so it is selected
     * explicitly. {@code wanted} may list alternative spellings ("CashCounter", "CashCounter Name"); each is tried as an exact
     * match in turn, then as a substring.</p>
     */
    public String selectFormName(String... wanted) {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let p=e.parentElement,h=0,t=''; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " const s=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && /form\\s*name/i.test(lbl(e)));"
                + " if(!s) return 0; s.id='__ccForm'; return s.options.length; }");
        int count;
        try { count = (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { count = 0; }
        if (count == 0) return "(no Form Name drop-down on this screen)";

        // Exact match on each spelling first, then a substring match — "CashCounter" must not be allowed to grab
        // "CashCounter Branch" or similar while an exact "CashCounter Name" option is sitting there.
        Object idx = page.evaluate("(ws) => { const e=document.getElementById('__ccForm'); if(!e) return -1;"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " const opts=[...e.options];"
                + " for(const w of ws){ const i=opts.findIndex(o=>norm(o.textContent)===norm(w)); if(i>=0) return i; }"
                + " for(const w of ws){ const i=opts.findIndex(o=>o.value && norm(o.textContent).includes(norm(w))); if(i>=0) return i; }"
                + " return -1; }", java.util.Arrays.asList(wanted));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) {
            Object opts = page.evaluate("() => { const e=document.getElementById('__ccForm'); if(!e) return '';"
                    + " return [...e.options].slice(0,12).map(o=>(o.textContent||'').trim()).join(', '); }");
            page.evaluate("() => { const e=document.getElementById('__ccForm'); if(e) e.removeAttribute('id'); }");
            return "Form Name " + java.util.Arrays.toString(wanted) + " is NOT in the list ("
                    + count + " options: " + opts + " ...)";
        }
        // REAL selectOption so the screen's ng-change reloads the grid for this master.
        try {
            page.locator("#__ccForm").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("selectFormName: selectOption failed - " + e.getMessage()); }
        waitForAngular(1200);
        String selected = readFormName();

        // VERIFY, do not assume. A silent no-op here leaves the previous master selected and the row is written
        // into THAT master — this screen saved a CashCounter row into Room No exactly that way. If the real click did not
        // take, drive the element from JS and fire Angular's change so the controller reloads.
        if (!matchesAny(selected, wanted)) {
            System.out.println("selectFormName: real select left it on \"" + selected + "\" - retrying from JS");
            page.evaluate("(idx) => { const e=document.getElementById('__ccForm'); if(!e) return; const A=window.angular, $=window.jQuery;"
                    + " e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).trigger('change'); }catch(x){} } }", i);
            waitForAngular(1500);
            selected = readFormName();
        }
        page.evaluate("() => { const e=document.getElementById('__ccForm'); if(e) e.removeAttribute('id'); }");
        waitForAngular(500);
        return "Form Name = " + (selected.isEmpty() ? "(not set)" : selected) + " (of " + count + " masters)";
    }

    /**

    /**
     * Wait for the list grid to actually have rows. The grid is filled by its own call AFTER the screen renders,
     * so sampling immediately reads an empty grid and the "copy the table's format" step silently falls back to
     * an invented code. Returns whether any row arrived.
     */
    public boolean waitForGridRows(int timeoutMs) {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')]"
                    + " .some(r=>[...r.querySelectorAll('.ui-grid-cell-contents')].some(c=>(c.textContent||'').trim()))"
                    + " || [...document.querySelectorAll('table tbody tr td')].some(c=>(c.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("waitForGridRows: no populated row within " + timeoutMs + "ms");
            return false;
        }
    }

    // ---- read the table --------------------------------------------------

    // ---- cleanup of rows a test planted --------------------------------
    // These live here rather than in their own page object because this class already owns navigation to
    // #/CABIN, the Form Name switch and the grid reading — the cleanup flow needs exactly those three.

    /**
     * Filter the grid's <b>Code</b> column to one value, so a row can be acted on without paging to find it.
     * Returns how many rows are showing afterwards.
     */
    public int filterByCode(String code) {
        page.evaluate("(c) => { const A=window.angular;"
                + " const f=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(e=>e.offsetParent!==null)[0];"
                + " if(!f) return; const ctrl=A.element(f).controller('ngModel'); f.value=c;"
                + " if(ctrl){ ctrl.$setViewValue(c); ctrl.$render(); }"
                + " f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); }", code);
        waitForAngular(1500);
        Object n = page.evaluate("() => document.querySelectorAll('.ui-grid-row').length");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return -1; }
    }

    /** PROBE — what a row offers: its cells, its per-row controls and their handlers, and the Status state. */
    public String describeRowControls(String code) {
        Object r = page.evaluate("(c) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const row=[...document.querySelectorAll('.ui-grid-row')].find(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].some(t=>norm(t.textContent)===c));"
                + " if(!row) return '(row not found)';"
                + " const cells=[...row.querySelectorAll('.ui-grid-cell-contents')].map(t=>norm(t.textContent)).join(' | ');"
                + " const ctrls=[...row.querySelectorAll('button,a,i,span[ng-click],input')].map(e=>"
                + "   (e.tagName.toLowerCase())+(e.type?(':'+e.type):'')+(e.type==='checkbox'?(' checked='+e.checked):'')"
                + "   +(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')"
                + "   +(e.className?(' .'+String(e.className).split(' ').slice(0,2).join('.')):'')).slice(0,12).join(' ; ');"
                + " return 'cells=['+cells+'] controls: '+ctrls; }", code);
        return r == null ? "" : r.toString();
    }
    /**
     * Remove one row. Prefers a real delete control; if the screen offers none, DEACTIVATES the row by unticking
     * its Status box — this grid only exposes Edit and Status, so a soft-delete is the strongest thing the UI
     * allows, and the result says which of the two happened rather than claiming "deleted".
     */
    public String removeRow(String code) {
        int shown = filterByCode(code);
        if (shown <= 0) return code + ": NOT FOUND in the grid (nothing removed)";

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("commonmaster") || resp.status() >= 400)) return;
                String b = "";
                try { b = resp.text(); } catch (Exception ignore) { b = "(body unavailable)"; }
                if (b.length() > 160) b = b.substring(0, 160) + "…";
                http.add(resp.status() + " " + b.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object what = page.evaluate("(c) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const row=[...document.querySelectorAll('.ui-grid-row')].find(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].some(t=>norm(t.textContent)===c));"
                + " if(!row) return 'no-row';"
                + " const del=[...row.querySelectorAll('[ng-click]')].find(e=>/delete|remove/i.test(e.getAttribute('ng-click')||''));"
                + " if(del){ del.id='__ccKill'; return 'delete:'+del.getAttribute('ng-click'); }"
                + " const st=[...row.querySelectorAll('input[type=checkbox]')].find(e=>/status/i.test(e.getAttribute('ng-model')||''));"
                + " if(st){ if(!st.checked) return 'already-inactive'; st.id='__ccKill'; return 'status'; }"
                + " return 'no-control'; }", code);
        String how = String.valueOf(what);

        if ("no-row".equals(how) || "no-control".equals(how) || "already-inactive".equals(how)) {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
            filterByCode("");
            return code + ": " + ("already-inactive".equals(how) ? "already inactive - left as is"
                    : "no delete or status control on the row (" + how + ")");
        }

        try { page.locator("#__ccKill").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("removeRow: real click failed - " + e.getMessage());
            try { page.evaluate("() => { const e=document.getElementById('__ccKill'); if(e) e.click(); }"); } catch (Exception ignore) { }
        }
        waitForAngular(2000);
        page.evaluate("() => { const e=document.getElementById('__ccKill'); if(e) e.removeAttribute('id'); }");
        try { page.offResponse(onResp); } catch (Exception ignore) { }

        Object after = page.evaluate("(c) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const row=[...document.querySelectorAll('.ui-grid-row')].find(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].some(t=>norm(t.textContent)===c));"
                + " if(!row) return 'row gone from the grid';"
                + " const st=[...row.querySelectorAll('input[type=checkbox]')].find(e=>/status/i.test(e.getAttribute('ng-model')||''));"
                + " return st ? ('row still listed, Status now '+(st.checked?'ACTIVE':'inactive')) : 'row still listed'; }", code);
        filterByCode("");
        return code + ": " + (how.startsWith("delete") ? "deleted via " + how : "Status unticked (deactivated)")
                + " -> " + after + (http.isEmpty() ? "" : "  [HTTP " + String.join(" ;; ", http) + "]");
    }

    /** The Form Name currently showing, whether or not this flow set it. */
    private String readFormName() {
        Object t = page.evaluate("() => { const e=document.getElementById('__ccForm');"
                + " return (e && e.selectedIndex>=0) ? (e.options[e.selectedIndex].textContent||'').trim() : ''; }");
        return t == null ? "" : t.toString();
    }

    private static boolean matchesAny(String actual, String[] wanted) {
        if (actual == null || actual.isEmpty()) return false;
        String a = actual.replaceAll("\\s+", " ").trim().toLowerCase();
        for (String w : wanted) {
            String x = w.replaceAll("\\s+", " ").trim().toLowerCase();
            if (a.equals(x) || a.contains(x)) return true;
        }
        return false;
    }

    /** Every Form Name option matching a word — for finding the exact label when a guess misses. */
    public String dumpFormNameOptions(String word) {
        Object r = page.evaluate("(w) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let p=e.parentElement,h=0,t=''; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " const s=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && /form\\s*name/i.test(lbl(e)));"
                + " if(!s) return '(no Form Name select)';"
                + " const re=new RegExp(w,'i');"
                + " const hits=[...s.options].map(o=>norm(o.textContent)).filter(t=>re.test(t));"
                + " return hits.length? hits.slice(0,20).join(' | ') : '(no option matching /'+w+'/ among '+s.options.length+')'; }", word);
        return r == null ? "" : r.toString();
    }

    /** Did the table actually yield values to copy? */
    public boolean hasTableData() { return !tableCodes.isEmpty(); }

    /** True when the grid genuinely holds no rows — nothing to copy, as opposed to a failed read. */
    public boolean tableEmpty = false;

    /**
     * Put the grid on its SMALLEST "items per page".
     *
     * <p>ui-grid remembers this setting between visits, and at a large page size this grid simply never
     * repopulates — one run that switched it to 125 left the table reading as empty on every later run until it
     * was set back. Normalising it here makes the read independent of what a previous run left behind.</p>
     */
    public String resetGridPageSize() {
        Object r = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null"
                + "   && /paginationPageSize/i.test(e.getAttribute('ng-model')||'')); if(!s) return '';"
                + " let best=Number.MAX_SAFE_INTEGER, bi=-1, cur='';"
                + " if(s.selectedIndex>=0) cur=(s.options[s.selectedIndex].textContent||'').trim();"
                + " [...s.options].forEach((o,i)=>{ const n=parseInt((o.value||o.textContent||'').replace(/\\D/g,''),10); if(!isNaN(n) && n<best){ best=n; bi=i; } });"
                + " if(bi<0) return ''; s.id='__ccPageSize'; return bi+':'+best+':'+cur; }");
        String info = String.valueOf(r);
        if (info.isEmpty() || info.split(":").length < 2) return "(no page-size selector)";
        String[] p = info.split(":");
        try {
            page.locator("#__ccPageSize").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(Integer.parseInt(p[0])),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("resetGridPageSize: selectOption failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ccPageSize'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return "items per page " + (p.length > 2 ? p[2] : "?") + " -> " + p[1];
    }

    /**
     * Read the table, retrying until it yields data. Widening the page size REFETCHES the grid, so a single
     * read can land on the blank moment in between and silently fall back to an invented code — and the whole
     * point of this screen's flow is that the new row copies the existing ones.
     */
    public String sampleTablePatternPolling(int tries) {
        String note = "";
        for (int i = 0; i < tries; i++) {
            waitForGridRows(8000);
            note = sampleTablePattern();
            if (hasTableData()) return (i > 0 ? "(read on attempt " + (i + 1) + ") " : "") + note;
            page.waitForTimeout(1500);
        }
        return "(no table data after " + tries + " reads) " + note;
    }

    /**
     * Read the LIST grid and learn the format it already uses for BOTH columns. Call this BEFORE Add.
     *
     * <p>Columns are located by HEADER NAME rather than position — these grids put a serial number, a status
     * toggle or an action column first, so "the first cell" is not the Code.</p>
     */
    public String sampleTablePattern() {
        // Read each grid SEPARATELY. This build leaves other screens' grids in the DOM (a CSSD sterilisation grid
        // sits behind this one), and scanning ".ui-grid-header-cell" globally splices both grids' headers into one
        // array while the leftover's empty rows come first — which is how a populated Remark column reads as blank.
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                // 500, not 25: the page size is widened first so "the highest code" is the table's, not the
                // first page's.
                + " const block=(heads,rows)=>[heads.join('\\u0002')].concat(rows.slice(0,500).map(x=>x.join('\\u0002'))).join('\\u0001');"
                + " [...document.querySelectorAll('.ui-grid,[ui-grid]')].forEach(g=>{"
                + "   const heads=[...g.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + "   const rows=[...g.querySelectorAll('.ui-grid-row')].map(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].map(c=>norm(c.textContent)));"
                + "   if(heads.length) out.push(block(heads,rows)); });"
                + " [...document.querySelectorAll('table')].forEach(t=>{"
                + "   const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent));"
                + "   const rows=[...t.querySelectorAll('tbody tr')].map(x=>[...x.querySelectorAll('td')].map(c=>norm(c.textContent)));"
                + "   if(heads.length) out.push(block(heads,rows)); });"
                // Last block: the WHOLE document. If a grid's rows are not DOM descendants of the element that
                // carries its headers, the per-grid blocks above come back empty and this one still sees them.
                + " { const heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + "   const rows=[...document.querySelectorAll('.ui-grid-row')].map(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].map(c=>norm(c.textContent)));"
                + "   if(heads.length) out.push(block(heads,rows)); }"
                + " return out.join('\\u0003'); }");
        tableCodes.clear();
        tableRemarks.clear();
        String raw = r == null ? "" : r.toString();
        // An EMPTY master is a legitimate outcome, not a read failure: this screen's CashCounter master has no rows at
        // all, so there is no format to copy. Say which of the two happened instead of blaming the read.
        tableEmpty = Boolean.TRUE.equals(page.evaluate("() => document.querySelectorAll('.ui-grid-row').length===0"));
        if (raw.isEmpty() || tableEmpty) {
            patternNote = tableEmpty
                    ? "the table has NO rows - nothing to copy, using this screen's own code shape and a stock remark"
                    : "no grid on screen - falling back to a generated code and a stock remark";
            return patternNote;
        }

        // Pick the grid that actually holds Code data — the leftover grids have headers but no rows.
        String[] bestHeads = null, bestRows = null;
        int bestCi = -1, bestRi = -1, bestCount = -1;
        for (String block : raw.split(GRID, -1)) {
            String[] lines = block.split(ROW, -1);
            if (lines.length == 0) continue;
            // ALIGNMENT: the row-selector column contributes a header but no .ui-grid-cell-contents, so raw
            // header indices sit one to the right of the cells. Dropping the empty headers restores the mapping —
            // without it "Code" reads the Remark column (rows are [CB00000064 | A-L1-01], headers
            // ['', Code, Remark, Status, Edit]) and the room name gets written into the Code box.
            String[] heads = java.util.Arrays.stream(lines[0].split(CELL, -1))
                    .filter(h -> !h.trim().isEmpty()).toArray(String[]::new);
            int ci = indexOf(heads, "(?i)code");
            // The second column is not called "Remark" here — it is "Cash Counter". Take the first header that is
            // neither the code column nor one of the grid's own control columns, so this works whatever the
            // screen names its description field.
            int ri = -1;
            for (int h = 0; h < heads.length && ri < 0; h++) {
                if (h == ci || heads[h].trim().isEmpty()) continue;
                if (heads[h].matches("(?i).*\\b(status|edit|action|delete|select)\\b.*")) continue;
                ri = h;
            }
            if (ci < 0) continue;
            int count = 0;
            for (int i = 1; i < lines.length; i++) {
                String[] cells = lines[i].split(CELL, -1);
                if (ci < cells.length && !cells[ci].isEmpty()) count++;
            }
            if (count > bestCount) { bestCount = count; bestCi = ci; bestRi = ri; bestHeads = heads; bestRows = lines; }
        }
        if (bestHeads == null || bestCount <= 0) {
            patternNote = "no grid with a populated Code column (" + raw.split(GRID, -1).length + " grid(s) on screen)"
                    + " - falling back to a generated code and a stock remark";
            return patternNote;
        }

        StringBuilder raws = new StringBuilder();
        int shown = 0;
        for (int i = 1; i < bestRows.length; i++) {
            String[] cells = bestRows[i].split(CELL, -1);
            if (bestCi < cells.length && !cells[bestCi].isEmpty()) tableCodes.add(cells[bestCi]);
            if (bestRi >= 0 && bestRi < cells.length && !cells[bestRi].isEmpty()) tableRemarks.add(cells[bestRi]);
            // Show POPULATED rows: ui-grid keeps blank render-buffer rows at the top, so "the first three rows"
            // would prove nothing about whether a column really is empty.
            if (shown < 3 && bestCi < cells.length && !cells[bestCi].isEmpty()) {
                raws.append(shown == 0 ? "" : " / ").append('[').append(String.join(" | ", cells)).append(']');
                shown++;
            }
        }
        patternNote = "grid headers " + java.util.Arrays.toString(bestHeads)
                + " (Code col " + bestCi + ", Remark col " + bestRi + ")"
                + " -> Code values " + head(tableCodes) + ", Remark values " + head(tableRemarks)
                + "  || sample rows: " + (raws.length() == 0 ? "(none)" : raws);
        return patternNote;
    }

    /** Index of the first header matching the pattern, or -1. */
    private static int indexOf(String[] headers, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null && p.matcher(headers[i]).find()) return i;
        }
        return -1;
    }

    private static String head(java.util.List<String> l) {
        return l.isEmpty() ? "(none)" : l.subList(0, Math.min(4, l.size())).toString();
    }

    /**
     * Continue the sequence the samples use: shared text part + the highest trailing number, incremented and
     * zero-padded to the same width ({@code R001},{@code R002} → {@code R003}). Returns null when the samples
     * carry no trailing number to continue.
     */
    private static String nextLike(java.util.List<String> samples, int attempt) {
        // Continue the DOMINANT numbered pattern, not merely the first one seen. Locking onto the first match let
        // a single stray row hijack the format: one leftover RM23501 in a table of SBI/CIMB/MBB made the next row
        // RM23502, propagating a bad code instead of matching the table. A pattern must cover at least two rows,
        // and the most common one wins.
        java.util.Map<String, Integer> hits = new java.util.HashMap<>();
        for (String s : samples) {
            java.util.regex.Matcher m = SPLIT_NUM.matcher(s);
            if (m.matches()) hits.merge(m.group(1), 1, Integer::sum);
        }
        // A MAJORITY of the sampled rows, not just the commonest of a few: two leftover RM##### rows among a
        // dozen SBI/CIMB/MBB abbreviations are not "the table's format", and treating them as one propagates them.
        String prefix = null;
        int best = Math.max(1, samples.size() / 2);
        for (java.util.Map.Entry<String, Integer> e : hits.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); prefix = e.getKey(); }
        }
        if (prefix == null) return null;

        int width = 0;
        long max = -1;
        for (String s : samples) {
            java.util.regex.Matcher m = SPLIT_NUM.matcher(s);
            if (!m.matches() || !m.group(1).equals(prefix)) continue;
            String d = m.group(2);
            width = Math.max(width, d.length());
            try { max = Math.max(max, Long.parseLong(d)); } catch (Exception ignore) { }
        }
        if (max < 0) return null;
        String num = String.valueOf(max + 1 + attempt);
        StringBuilder sb = new StringBuilder(num);
        while (sb.length() < width) sb.insert(0, '0');
        return prefix + sb;
    }

    /**
     * Move the code onto a different letter series, keeping the digits and the width — {@code CB00000065} →
     * {@code CC00000065} → {@code CD00000065}. Used when the server says the code already exists.
     */
    private static String withShiftedPrefix(String code, int shift) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z]+)(.*)$").matcher(code);
        if (!m.matches()) return code;
        char[] letters = m.group(1).toCharArray();
        int last = letters.length - 1;
        boolean lower = Character.isLowerCase(letters[last]);
        int base = lower ? 'a' : 'A';
        letters[last] = (char) (base + ((Character.toUpperCase(letters[last]) - 'A' + shift) % 26));
        return new String(letters) + m.group(2);
    }

    /**
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        // Already showing both boxes -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ccAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__ccAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CashCounter.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ccAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>, both continuing the format read from the table. {@code attempt}
     * pushes the sequence further along so a retry after an "already exists" toast submits different details.
     */
    public String fillDetails(int attempt) {
        // Attempt 0 continues the sequence. On a retry the LETTERS move instead of the number (CB… -> CC…):
        // pushing the number further just walks into the next code that already exists, and the screen's own
        // codes are "two letters + eight digits", so a different letter pair is still the table's format.
        // Shape-matching first: this screen's variation sits in the MIDDLE of the code, which the trailing-number
        // rule cannot see.
        java.util.List<String> shape = dominantShape(tableCodes);
        String code = nextByCommonAffix(shape, attempt);
        String remark = code == null ? null : nameLikeTable(code, shape, tableRemarks);
        String how;
        if (code != null && remark != null) {
            lastCode = code;
            lastRemark = remark;
            how = "matching the table's dominant shape " + head(shape) + " -> " + code + " / " + remark;
            return fillBoxes(how);
        }

        code = nextLike(tableCodes, 0);
        if (code != null && attempt > 0) code = withShiftedPrefix(code, attempt);
        remark = nextLike(tableRemarks, attempt);
        if (code != null && remark != null) {
            how = "continuing the table (" + head(tableCodes) + " -> " + code + ", " + head(tableRemarks) + " -> " + remark + ")";
        } else {
            // The table carries no numbered sequence to continue — add another plausible counter in the same
            // style instead, skipping any code already on file. Whatever this run writes becomes the pattern the
            // NEXT run copies, so an invented shape would propagate.
            String[] counter = null;
            for (int k = 0; k < COUNTERS.length && counter == null; k++) {
                String[] cand = COUNTERS[(k + attempt) % COUNTERS.length];
                boolean taken = tableCodes.stream().anyMatch(c -> c.equalsIgnoreCase(cand[0]));
                if (!taken) counter = cand;
            }
            if (counter != null) {
                code = counter[0];
                remark = counter[1];
                how = "table has no numbered sequence (" + head(tableCodes) + ") - adding another counter in the same style: "
                        + code + " / " + remark;
            } else {
                if (code == null) code = "CC" + String.format("%04d", Math.abs((System.nanoTime() + attempt * 7919L) % 10000));
                if (remark == null) remark = COUNTERS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % COUNTERS.length)][1];
                how = "every counter in the pool already exists - using " + code + " / " + remark;
            }
        }
        lastCode = code;
        lastRemark = remark;
        return fillBoxes(how);
    }

    /** Type {@link #lastCode} / {@link #lastRemark} into the two boxes and report what landed where. */
    private String fillBoxes(String how) {
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark);"
                + " return 'Code='+cd+' | Cash Counter='+rm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res + "  [" + how + "]";
    }

    /**
     * Choose the mandatory <b>Location*</b> (and any other required drop-down) with a REAL selectOption, falling
     * back to JS + Angular's change when the widget refuses — the Form Name select on the sibling CommonMaster
     * screen ignores Playwright's select entirely, and this screen's are built the same way.
     */
    public String selectLocation() {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let p=e.parentElement,h=0,t=''; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " const s=[...document.querySelectorAll('select')].find(e=>/location/i.test(e.getAttribute('ng-model')||'') || /^location/i.test(lbl(e)));"
                + " if(!s) return ''; s.id='__ccLoc'; return s.getAttribute('ng-model')||'?'; }");
        String model = String.valueOf(found);
        if (model.isEmpty()) return "(no Location drop-down)";

        Object idx = page.evaluate("() => { const e=document.getElementById('__ccLoc'); if(!e) return -1;"
                + " return [...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__ccLoc'); if(e) e.removeAttribute('id'); }"); return "Location has no real option"; }

        try {
            page.locator("#__ccLoc").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectLocation: real selectOption failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__ccLoc'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(800);
        Object txt = page.evaluate("() => { const e=document.getElementById('__ccLoc');"
                + " const t=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return t; }");
        String sel = txt == null ? "" : txt.toString();
        return "Location = " + (sel.isEmpty() ? "(not set)" : sel) + " [" + model + "]";
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Code/Remark first — these forms
     * re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit handler
     * from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ccToasts=[]; if(window.__ccObs) window.__ccObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ccToasts.includes(t)) window.__ccToasts.push(t); }); };"
                + " window.__ccObs=new MutationObserver(grab); window.__ccObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url().toLowerCase();
                if (!(u.contains("cashcounter") || u.contains("counter") || u.contains("/iud") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(remEl, a.remark);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__ccSubmit'; return 'click:'+(b.getAttribute('ng-click')||norm(b.textContent||b.value)); }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        System.out.println("CashCounter submit => " + how);
        if (String.valueOf(how).startsWith("click")) {
            try { page.locator("#__ccSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("CashCounter submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ccSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__ccToasts||[]).some(a=>/counter|master|saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("CashCounter save HTTP => " + lastSaveHttp);
        // "succes" (one s) is deliberate — the sibling Lab Organism screen returns "Record added succesfully".
        Object t = page.evaluate("() => { const a=window.__ccToasts||[]; return a.find(x=>/saved|added|succes/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label), the grid headers and the
     * visible button labels with their ng-click handlers. Included in the FAIL text so a miss is diagnosable
     * without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null).slice(0,30).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type?(':'+e.type):'')+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const selects=[...document.querySelectorAll('select')].slice(0,10).map(e=>"
                + "   'select['+(e.getAttribute('ng-model')||e.id||'?')+'] opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  SELECTS: '+selects.join(' ; ')+'  ||  GRID HEADERS: '+heads.join(' | ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
