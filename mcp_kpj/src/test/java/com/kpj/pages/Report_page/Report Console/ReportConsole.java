package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Report &gt; <b>Report Console</b> ({@code #/reportconsole}) — Page Object, shared by every report category (MIS Report,
 * Miscellaneous Reports, …).
 *
 * <p>Read from the screen's own view and controller (Report/Load/ReportConsole + ReportConsoleController.js, build
 * v2026.11.23.518):</p>
 * <ul>
 *   <li>Category buttons on the right (Financial Report, MIS Report, Miscellaneous Reports, …) fill the <b>Reports</b> dropdown
 *       ({@code RptColsole.Report}, select2).</li>
 *   <li>A report fills the <b>Filter</b> dropdown ({@code RptColsole.Parameter}, select2). <b>Add</b> ({@code AddFilter()}) puts
 *       the filter in the table; its <b>AddValue</b> button ({@code AddValue(tt)}) opens that filter's popup — one of ~190
 *       ({@code ModalDate}, {@code ModalDepartmentList}, {@code ModalDoctorList}, …): a date range, a tick list, a dropdown or a
 *       text box — confirmed with the popup's {@code fnAdd(…)} button.</li>
 *   <li><b>Show Report</b> ({@code ShowReport($event)}) opens the report in a new tab (SAP Crystal Reports viewer,
 *       {@code SQLReport/ReportConsole/…/frm…Report.aspx}). <b>Clear</b> ({@code ClearParameters()}) empties the table.</li>
 * </ul>
 */
public class ReportConsole extends BasePage {

    private static final String REPORT = "select[ng-model='RptColsole.Report']";
    private static final String FILTER = "select[ng-model='RptColsole.Parameter']";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Random rnd = new Random();

    /** Pages (tabs) open before the last Show Report — to spot the report tab. */
    public int tabsBefore = 0;
    /** File name when the last Show Report downloaded a file instead of opening a tab, else "". */
    public String downloaded = "";

    public ReportConsole(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------

    /** Report → Report Console through the sidebar (same way as the Sample Accept/Reject script). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e&&!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(a=>/^\\s*report\\s*$/i.test(norm(a.textContent)) && vis(a) && !(a.getAttribute('href')||'').includes('reportconsole'));"
                    + " const li=top&&top.closest('li'); if(top && !(li && li.classList.contains('menu-open'))) top.click(); }");
            waitForAngular(1500);
            Object tagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/reportconsole' && x.offsetParent);"
                    + " if(!a) return false; a.id='__rcLink'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__rcLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ReportConsole.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__rcLink'); if(e) e.removeAttribute('id'); }");
            }
            try { page.waitForSelector(REPORT, new Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED).setTimeout(20000)); }
            catch (Exception ignore) { System.out.println("ReportConsole.nav: not on the screen yet (attempt " + attempt + ")"); }
            waitForAngular(2500);
        }
        prepare();
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("#/reportconsole") && page.locator(REPORT).count() > 0;
    }

    /** Message hook (JAlert). Safe to call again. */
    public void prepare() {
        page.evaluate("() => { window.__alerts = window.__alerts || [];"
                + " ['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m).replace(/<br\\s*\\/?>/gi,' '));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } }); }");
    }

    public void resetMessages() { page.evaluate("() => { window.__alerts=[]; }"); }

    public String messages() {
        Object r = page.evaluate("() => (window.__alerts||[]).join(' | ')");
        return r == null ? "" : r.toString().trim();
    }

    // ---- category / report / filter -----------------------------------------

    /** Click a category button on the right (exact text, e.g. "MIS Report"). True when its reports are listed. */
    public boolean openCategory(String name) {
        String find = "(n) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].filter(e=>e.offsetParent).find(e=>norm(e.textContent).toLowerCase()===n.toLowerCase());"
                + " if(!b) return false; b.id='__rcCat'; return true; }";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try { page.waitForFunction(find, name, new Page.WaitForFunctionOptions().setTimeout(20000)); }   // the buttons load after the screen
            catch (Exception e) { System.out.println("ReportConsole.category: '" + name + "' button not shown (attempt " + attempt + ")"); continue; }
            page.locator("#__rcCat").click(new Locator.ClickOptions().setTimeout(6000));
            page.evaluate("() => { const e=document.getElementById('__rcCat'); if(e) e.removeAttribute('id'); }");
            try { page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length > 1; }", REPORT, new Page.WaitForFunctionOptions().setTimeout(20000)); }
            catch (Exception ignore) { }
            waitForAngular(1200);
            if (!reportNames().isEmpty()) return true;
        }
        return false;
    }

    /** All reports in the Reports dropdown (read at run time, without "--Select--"). */
    public List<String> reportNames() { return options(REPORT); }

    /** All filters in the Filter dropdown for the selected report. */
    public List<String> filterNames() { return options(FILTER); }

    @SuppressWarnings("unchecked")
    private List<String> options(String sel) {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); return e ? [...e.options].map(o=>o.text.replace(/\\s+/g,' ').trim()).filter(t=>t && !/^--/.test(t)) : []; }", sel);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Choose a report (real select2 clicks). Returns "" when the dropdown shows it, else why not. */
    public String selectReport(String name) {
        String why = chooseSelect2(REPORT, name);
        try { page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length > 1; }", FILTER, new Page.WaitForFunctionOptions().setTimeout(15000)); }
        catch (Exception ignore) { }
        waitForAngular(1200);
        return why;
    }

    /** Choose a filter and click Add (real clicks). Returns the message shown, "" when the filter row was added. */
    public String addFilter(String name) {
        String why = selectFilter(name);
        return why.isEmpty() ? clickAdd() : why;
    }

    /** Choose a filter in the Filter dropdown (real select2 clicks). "" when the dropdown shows it, else why not. */
    public String selectFilter(String name) { return chooseSelect2(FILTER, name); }

    /** The filter the Filter dropdown shows. */
    public String filterShown() {
        Object shown = page.evaluate("(s) => { const e=document.querySelector(s); return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.replace(/\\s+/g,' ').trim() : ''; }", FILTER);
        return shown == null ? "" : shown.toString();
    }

    /** Click Add (real click). "" when a new filter row appeared, else the screen's message. */
    public String clickAdd() {
        resetMessages();
        int before = filterRowCount();
        page.locator("button[ng-click='AddFilter()']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForFunction("(n) => document.querySelectorAll(\"button[ng-click*='AddValue(tt']\").length > n || (window.__alerts||[]).length > 0", before,
                new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(600);
        return filterRowCount() > before ? "" : (messages().isEmpty() ? "the filter row was not added" : messages());
    }

    /** The Filter Name column of the filter table. */
    @SuppressWarnings("unchecked")
    public List<String> filtersInTable() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"button[ng-click*='AddValue(tt']\")].map(b=>{ const tr=b.closest('tr'); return tr && tr.children[1] ? tr.children[1].textContent.replace(/\\s+/g,' ').trim() : ''; })");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    public int filterRowCount() {
        Object r = page.evaluate("() => document.querySelectorAll(\"button[ng-click*='AddValue(tt']\").length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The value shown in the filter table for this filter ("" when none). */
    public String filterValue(String filterName) {
        Object r = page.evaluate("(f) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll(\"button[ng-click*='AddValue(tt']\")].find(x=>{"
                + " const tr=x.closest('tr'); return tr && norm(tr.children[1] && tr.children[1].textContent)===f; }); if(!b) return null; return norm(b.closest('tr').children[2].textContent); }", filterName);
        return r == null ? "" : r.toString();
    }

    /** Pick an option of a select2 dropdown by its text — opens the box, types, clicks the matching result. */
    private String chooseSelect2(String sel, String text) {
        Locator box = page.locator(sel).first().locator("xpath=preceding-sibling::div[contains(@class,'select2-container')][1]");
        if (box.count() == 0) {   // not a select2 — plain select
            try { page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(text)); } catch (Exception e) { return String.valueOf(e.getMessage()).split("\n")[0]; }
        } else {
            box.locator("a.select2-choice").click(new Locator.ClickOptions().setTimeout(6000));
            Locator drop = page.locator(".select2-drop-active:visible").last();   // the screen keeps an old, hidden drop around
            Locator input = drop.locator("input.select2-input");
            if (input.count() > 0) input.first().fill(text.length() > 25 ? text.substring(0, 25) : text);
            waitForAngular(500);
            Locator hits = drop.locator(".select2-results li.select2-result-selectable");
            Locator exact = hits.filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*" + java.util.regex.Pattern.quote(text) + "\\s*$")));
            if (exact.count() > 0) exact.first().click(new Locator.ClickOptions().setTimeout(5000));
            else if (hits.count() > 0) hits.first().click(new Locator.ClickOptions().setTimeout(5000));
            else { page.keyboard().press("Escape"); return "\"" + text + "\" is not in the list"; }
        }
        waitForAngular(800);
        Object shown = page.evaluate("(s) => { const e=document.querySelector(s); return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.replace(/\\s+/g,' ').trim() : ''; }", sel);
        return text.equals(String.valueOf(shown)) ? "" : "the dropdown shows \"" + shown + "\"";
    }

    // ---- Add Value popups -------------------------------------------------

    /** Result of filling one filter's value. */
    public static class ValueResult {
        public String popup = "", chosen = "", value = "", message = "", problem = "";
        public boolean ok() { return problem.isEmpty() && !value.isEmpty(); }
    }

    /**
     * Click the filter's AddValue (real click), fill its popup and confirm it:
     * date range → From = two months before today, To = today; month range → the same months; tick list / dropdown / radio → a
     * RANDOM entry. Then the popup's Add button. The value the table now shows is returned in {@code value}.
     */
    public ValueResult addValue(String filterName) {
        ValueResult res = openAddValue(filterName);
        if (!res.problem.isEmpty()) return res;
        chooseValue(res);
        if (!res.problem.isEmpty()) return res;
        return clickOk(res, filterName);
    }

    /** Click the filter row's AddValue (real click). {@code popup} = the popup that opened, or {@code problem} says why none did. */
    public ValueResult openAddValue(String filterName) {
        ValueResult res = new ValueResult();
        Object tagged = page.evaluate("(f) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__rcAv').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll(\"button[ng-click*='AddValue(tt']\")].find(x=>{ const tr=x.closest('tr'); return tr && norm(tr.children[1] && tr.children[1].textContent)===f; });"
                + " if(!b) return false; b.id='__rcAv'; return true; }", filterName);
        if (!Boolean.TRUE.equals(tagged)) { res.problem = "the filter row / AddValue button was not found"; return res; }
        resetMessages();
        page.locator("#__rcAv").click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.classList.contains('in') && getComputedStyle(m).display!=='none') || (window.__alerts||[]).length>0", null,
                new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        waitForAngular(1500);
        Object id = page.evaluate("() => { const m=[...document.querySelectorAll('.modal')].filter(m=>m.classList.contains('in') && getComputedStyle(m).display!=='none').pop(); if(!m) return '';"
                + " document.querySelectorAll('[data-rcmodal]').forEach(e=>e.removeAttribute('data-rcmodal')); m.setAttribute('data-rcmodal','1'); return m.id || '(no id)'; }");
        res.popup = id == null ? "" : id.toString();
        if (res.popup.isEmpty()) res.problem = "no popup opened" + (messages().isEmpty() ? "" : " (" + messages() + ")");
        return res;
    }

    /** In the open popup: choose the value(s) — see {@link #addValue}. {@code chosen} = what was picked, or {@code problem}. */
    public ValueResult chooseValue(ValueResult res) {
        Locator m = page.locator("[data-rcmodal='1']");
        try { waitForPopupData(m); fill(m, res); }
        catch (Exception e) {
            String em = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("message='([^']*)").matcher(em);
            res.problem = "could not fill the popup: " + (mm.find() ? mm.group(1) : em).trim();
            if (res.problem.length() > 300) res.problem = res.problem.substring(0, 300);
        }
        if (!res.problem.isEmpty()) closePopup(m);
        return res;
    }

    /** Click the popup's own Add / OK (real click). {@code value} = what the filter table now shows for the filter. */
    public ValueResult clickOk(ValueResult res, String filterName) {
        Locator m = page.locator("[data-rcmodal='1']");
        Locator add = m.locator("button[ng-click^='fnAdd']:visible");
        if (add.count() == 0) add = m.locator(".modal-footer button:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*(add|ok|save|select)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)));
        if (add.count() == 0) { res.problem = "the popup has no Add / OK button"; closePopup(m); return res; }
        add.first().click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(1500);
        res.message = messages();
        if (m.isVisible()) { res.problem = "the popup stayed open" + (res.message.isEmpty() ? "" : " (" + res.message + ")"); closePopup(m); }
        res.value = filterValue(filterName);
        if (res.problem.isEmpty() && res.value.isEmpty()) res.problem = "the table shows no value for the filter" + (res.message.isEmpty() ? "" : " (" + res.message + ")");
        if (res.problem.isEmpty() && res.value.toLowerCase().contains("undefined"))   // e.g. City/District saves "undefined,Chaah"
            res.problem = "the popup saved a broken value (\"" + res.value + "\") for the chosen \"" + res.chosen + "\"";
        return res;
    }

    /** Lists in a popup load after it opens: wait for a tick list first (up to 8 s), then for any input. */
    private void waitForPopupData(Locator m) {
        String boxesJs = "() => { const m=document.querySelector(\"[data-rcmodal='1']\"); if(!m) return true;"
                + " return [...m.querySelectorAll('input[type=checkbox],input[type=radio]')].filter(c=>c.offsetParent && !/fnSelectAll/.test(c.getAttribute('ng-click')||'')).length > 0"
                + " || [...m.querySelectorAll('input.txtDate,input[type=month]')].some(i=>i.offsetParent); }";
        try { page.waitForFunction(boxesJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception e) {
            try {
                page.waitForFunction("() => { const m=document.querySelector(\"[data-rcmodal='1']\"); if(!m) return true;"
                        + " return [...m.querySelectorAll('select')].some(s=>s.offsetParent && s.options.length>1) || [...m.querySelectorAll('input')].some(i=>i.offsetParent && i.type!=='hidden'); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(4000));
            } catch (Exception ignore) { }
        }
        waitForAngular(800);
    }

    /** How many values to tick in a tick-list popup: several (2 or 3) at random, or all when the list is shorter. */
    public int valuesPerList = -1;

    /** Tick SEVERAL random entries of the popup's tick list (real clicks; "select all" is never used). False when it has no list. */
    private boolean tickRandom(Locator m, ValueResult res) {
        Locator boxes = m.locator("input[type=checkbox]:visible:not([ng-click*='fnSelectAll'])");
        int n = boxes.count();
        if (n == 0) return false;
        int want = Math.min(n, valuesPerList > 0 ? valuesPerList : 2 + rnd.nextInt(2));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        java.util.Collections.shuffle(order, rnd);
        List<String> picked = new ArrayList<>();
        for (int i : order.subList(0, want)) {
            Locator b = boxes.nth(i);
            try {
                b.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
                if (!b.isChecked()) b.click(new Locator.ClickOptions().setTimeout(5000));
                picked.add(labelOf(b));
            } catch (Exception e) { System.out.println("ReportConsole.tick: could not tick entry " + i + " - " + String.valueOf(e.getMessage()).split("\n")[0]); }
        }
        if (picked.isEmpty()) return false;
        res.chosen = (res.chosen.isEmpty() ? "" : res.chosen + " -> ") + String.join(", ", picked);
        return true;
    }

    /** Start of the date ranges typed in the popups; null = two months before today (a wider retry sets e.g. 01/01/2020). */
    public LocalDate fromDate = null;

    private void fill(Locator m, ValueResult res) {
        LocalDate to = LocalDate.now(), from = fromDate != null ? fromDate : to.minusMonths(2);
        // "Date of …" ranges (Date of Birth: yyyy-MM-dd from 1940; Date of Discharge: the console's format) — ng-model From/ToDateof…
        Locator fromOf = m.locator("input:visible[ng-model*='.FromDateof']"), toOf = m.locator("input:visible[ng-model*='.ToDateof']");
        if (fromOf.count() > 0 || toOf.count() > 0) {
            boolean dob = String.valueOf((fromOf.count() > 0 ? fromOf : toOf).first().getAttribute("ng-model")).contains("Birth");
            Object f = (fromOf.count() > 0 ? fromOf : toOf).first().evaluate("i => { const d=i.closest('.datepicker'); return d ? d.getAttribute('date-format')||'' : ''; }");
            DateTimeFormatter fmt = String.valueOf(f).startsWith("yyyy") ? DateTimeFormatter.ISO_LOCAL_DATE : DMY;
            LocalDate start = dob ? LocalDate.of(1940, 1, 1) : from;
            if (fromOf.count() > 0) typeDate(m, fromOf.first(), start.format(fmt));
            if (toOf.count() > 0) typeDate(m, toOf.first(), to.format(fmt));
            res.chosen = start.format(fmt) + " - " + to.format(fmt);
            return;
        }
        Locator fromTxt = m.locator("input[type=text][ng-model$='FromDate']:visible, input:not([type])[ng-model$='FromDate']:visible");
        Locator toTxt = m.locator("input[type=text][ng-model$='ToDate']:visible, input:not([type])[ng-model$='ToDate']:visible");
        Locator fromMon = m.locator("input[type=month][ng-model$='FromDate']:visible");
        Locator toMon = m.locator("input[type=month][ng-model$='ToDate']:visible");
        Locator anyDate = m.locator("input.txtDate:visible");
        int nf = fromTxt.count(), nt = toTxt.count();
        if (nf > 0 || nt > 0) {
            if (nf > 0) typeDate(m, fromTxt.first(), from.format(DMY));
            if (nt > 0) typeDate(m, toTxt.first(), to.format(DMY));
            res.chosen = (nf > 0 ? from.format(DMY) : "") + " - " + (nt > 0 ? to.format(DMY) : "");
            return;
        }
        if (fromMon.count() > 0 || toMon.count() > 0) {
            DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
            if (fromMon.count() > 0) fromMon.first().fill(from.format(ym));
            if (toMon.count() > 0) toMon.first().fill(to.format(ym));
            res.chosen = from.format(ym) + " - " + to.format(ym);
            return;
        }
        if (anyDate.count() > 0) {   // a single-date popup
            int nd = anyDate.count();
            for (int i = 0; i < nd; i++) typeDate(m, anyDate.nth(i), to.format(DMY));
            res.chosen = to.format(DMY);
            return;
        }
        // tick list (skip "select all")
        if (tickRandom(m, res)) return;
        Locator radios = m.locator("input[type=radio]:visible");
        if (radios.count() > 0) {
            Locator r = radios.nth(rnd.nextInt(radios.count()));
            r.click(new Locator.ClickOptions().setTimeout(5000));
            res.chosen = labelOf(r);
            return;
        }
        Locator sels = m.locator("select:visible");
        for (int s = 0; s < sels.count(); s++) {
            Locator sel = sels.nth(s);
            int n = ((Number) sel.evaluate("e => e.options.length")).intValue();
            List<Integer> real = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                Object t = sel.evaluate("(e,k) => e.options[k].text.trim()", k);
                if (t != null && !t.toString().isEmpty() && !t.toString().startsWith("--")) real.add(k);
            }
            if (real.isEmpty()) continue;
            int k = real.get(rnd.nextInt(real.size()));
            // Payable Type (Doctor popup): only "Doctor" loads the doctor list — prefer it when the dropdown offers it
            for (int r : real) if ("Doctor".equalsIgnoreCase(String.valueOf(sel.evaluate("(e,k) => e.options[k].text.trim()", r)))) { k = r; break; }
            pick(sel, k);
            Object t = sel.evaluate("(e,k) => e.options[k].text.trim()", k);
            res.chosen = (res.chosen.isEmpty() ? "" : res.chosen + ", ") + t;
            waitForAngular(800);
        }
        if (!res.chosen.isEmpty() && m.locator("input[ng-click*='fnSelectAll'], [ng-repeat]").count() == 0) return;   // a dropdown-only popup (e.g. Month): the choice is the value
        if (!res.chosen.isEmpty()) {   // the dropdowns may load a list (e.g. Location + Payable Type -> doctors): tick one of it
            waitForPopupData(m);
            if (tickRandom(m, res)) return;
            System.out.println("ReportConsole.fill: after dropdowns (" + res.chosen + ") - ticks: "
                    + m.locator("input[type=checkbox]").count() + " in popup, " + m.locator("input[type=checkbox]:visible").count() + " visible");
            int tries = 0;
            for (int s = sels.count() - 1; s >= 0 && tries < 25; s--) {   // the last dropdown first (e.g. Cash Counter), then the earlier ones (Location)
                Locator sel = sels.nth(s);
                int n = ((Number) sel.evaluate("e => e.options.length")).intValue();
                List<Integer> order = new ArrayList<>();
                for (int k = 0; k < n; k++) order.add(k);
                java.util.Collections.shuffle(order, rnd);
                for (int k : order) {
                    if (tries++ >= 25) break;
                    Object t = sel.evaluate("(e,k) => e.options[k].text.trim()", k);
                    if (t == null || t.toString().isEmpty() || t.toString().startsWith("--")) continue;
                    pick(sel, k);
                    waitForPopupData(m);
                    if (tickRandom(m, res)) { res.chosen = t + " -> " + res.chosen.replaceAll("^.* -> ", ""); return; }
                }
            }
            res.problem = "the list in the popup stayed empty for every choice of its dropdowns";
            return;
        }
        Locator fromAge = m.locator("input[ng-model$='FromAge']:visible"), toAge = m.locator("input[ng-model$='ToAge']:visible");
        if (fromAge.count() > 0 || toAge.count() > 0) {   // Age Range: a typed range — 0 to 100 covers every age
            if (fromAge.count() > 0) fromAge.first().fill("0");
            if (toAge.count() > 0) toAge.first().fill("100");
            res.chosen = "0 - 100";
            return;
        }
        // Patient: type an MRN and Find (the patient must exist) — the MRN comes from -Dreport.mrn
        Locator mrn = m.locator("input[ng-model='Patient.MRNo']:visible");
        if (mrn.count() > 0) {
            String no = System.getProperty("report.mrn", DEFAULT_MRN);
            mrn.first().fill(no);
            resetMessages();
            m.locator("button[ng-click*='SearchPatientByMRNo']").first().click(new Locator.ClickOptions().setTimeout(5000));
            try { page.waitForFunction("() => { const s=angular.element(document.querySelector(\"[data-rcmodal='1'] input[ng-model='Patient.MRNo']\")).scope();"
                    + " return (s && s.Patient && s.Patient.patientid) || (window.__alerts||[]).length>0; }", null, new Page.WaitForFunctionOptions().setTimeout(15000)); }
            catch (Exception ignore) { }
            waitForAngular(800);
            Object name = page.evaluate("() => { const s=angular.element(document.querySelector(\"[data-rcmodal='1'] input[ng-model='Patient.MRNo']\")).scope();"
                    + " return s && s.Patient && s.Patient.patientid ? ((s.Patient.FirstName||'')+' '+(s.Patient.LastName||'')).trim() : ''; }");
            if (name == null || name.toString().isEmpty()) { res.problem = "MRN " + no + " was not found" + (messages().isEmpty() ? "" : " (" + messages() + ")"); return; }
            res.chosen = "MRN " + no + " (" + name + ")";
            return;
        }
        // a search box with a Find button that loads the tick list (e.g. Diagnosis): search, then tick several
        Locator find = m.locator("button:visible[title*='Find'], button:visible[ng-click*='fnGet']");
        Locator box = m.locator("input[type=text]:visible:not([ng-model='Search.text'])");
        if (find.count() > 0 && box.count() > 0) {
            for (String term : SEARCH_TERMS) {
                box.first().fill(term);
                find.first().click(new Locator.ClickOptions().setTimeout(5000));
                waitForPopupData(m);
                if (tickRandom(m, res)) { res.chosen = "search \"" + term + "\" -> " + res.chosen; return; }
            }
            res.problem = "searching " + String.join(" / ", SEARCH_TERMS) + " found nothing to pick";
            return;
        }
        // time pickers (From Time / To Time) come pre-filled — their own times are the value
        // time pickers (From Time / To Time): open each clock, type the hour and minute — From 08:00, To 11:59
        Locator pops = m.locator("timepicker-pop");
        if (pops.count() > 0) {
            String[][] hm = {{"08", "00"}, {"11", "59"}};
            for (int i = 0; i < pops.count(); i++) {
                Locator pop = pops.nth(i);
                String[] v = hm[Math.min(i, hm.length - 1)];
                pop.locator("button[ng-click='toggle()']").first().click(new Locator.ClickOptions().setTimeout(5000));
                waitForAngular(500);
                pop.locator("input[ng-model='hours']").first().fill(v[0]);
                pop.locator("input[ng-model='minutes']").first().fill(v[1]);
                page.keyboard().press("Tab");
                try { m.locator(".modal-title").first().click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignore) { }   // close the clock
                waitForAngular(500);
            }
            Object times = page.evaluate("() => [...document.querySelectorAll(\"[data-rcmodal='1'] input[ng-model='inputTime']\")].map(i=>i.value).join(' - ')");
            res.chosen = times == null ? "" : times.toString();
            if (res.chosen.replace("-", "").trim().isEmpty()) res.problem = "the time pickers did not take a time";
            return;
        }
        // a number box (Discharge, Occupancy Rate, Vacant / Occupied / Available Beds): type a small count
        Locator num = m.locator("input[type=number]:visible");
        if (num.count() > 0) {
            for (int i = 0; i < num.count(); i++) num.nth(i).fill(NUMBER_VALUE);
            res.chosen = NUMBER_VALUE;
            return;
        }
        // Record Status: a free-text box
        Locator status = m.locator("input:visible[ng-model$='RecordStatus']");
        if (status.count() > 0) {
            status.first().fill(RECORD_STATUS);
            res.chosen = RECORD_STATUS;
            return;
        }
        Locator text = m.locator("input[type=text]:visible, input[type=number]:visible, input:not([type]):visible");
        res.problem =text.count() > 0 ? "the popup needs a typed value (" + text.first().getAttribute("placeholder") + ") - no list to pick from"
                                       : "the popup has no values to pick";
    }

    /** A patient that exists on devhis (seen in the Date Wise Death Report); override with -Dreport.mrn. */
    private static final String DEFAULT_MRN = "1100339288";
    /** Words tried in search-first popups (e.g. Diagnosis) until the list shows entries. */
    private static final String[] SEARCH_TERMS = {"fever", "pain", "diabetes", "a"};
    /** Typed in number popups (Discharge, Occupancy Rate, beds); override with -Dreport.number. */
    private static final String NUMBER_VALUE = System.getProperty("report.number", "1");
    /** Typed in the Record Status popup; override with -Dreport.recordStatus. */
    private static final String RECORD_STATUS = System.getProperty("report.recordStatus", "Completed");

    /**
     * Choose option {@code k} so that the screen reacts. Some popups share their dropdown with another popup (e.g. Location in
     * Attending Doctor and Submitted By): when it already shows that option, picking it again changes nothing and the list is
     * never loaded — so move to another option first.
     */
    private void pick(Locator sel, int k) {
        Object cur = sel.evaluate("e => e.selectedIndex");
        int n = ((Number) sel.evaluate("e => e.options.length")).intValue();
        if (cur instanceof Number && ((Number) cur).intValue() == k && n > 1) {
            sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(k == 0 ? 1 : 0));
            waitForAngular(1200);
        }
        sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(k));
        waitForAngular(1500);
    }

    /** Type a date and close the date picker by clicking the popup's title (Escape would close the whole popup). */
    private void typeDate(Locator m, Locator input, String dmy) {
        input.fill(dmy, new Locator.FillOptions().setTimeout(5000));   // no click: an open calendar can cover the next box
        page.keyboard().press("Tab");
        try { m.locator(".modal-title").first().click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignore) { }
        waitForAngular(400);
    }

    private String labelOf(Locator box) {
        Object t = box.evaluate("c => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const l=c.closest('label'); if(l && norm(l.textContent)) return norm(l.textContent);"
                + " const tr=c.closest('tr,li,div'); return tr ? norm(tr.textContent).slice(0,80) : ''; }");
        return t == null ? "" : t.toString();
    }

    private void closePopup(Locator m) {
        try {
            Locator c = m.locator("button[data-dismiss='modal']:visible");
            if (c.count() > 0) c.last().click(new Locator.ClickOptions().setTimeout(4000));
            else page.keyboard().press("Escape");
        } catch (Exception ignore) { }
        waitForAngular(800);
    }

    // ---- Show Report / Clear ------------------------------------------------

    /**
     * Click Show Report (real click) and wait (up to {@code ms}) for the report tab. Returns the tab, or null when none opened —
     * then {@link #messages()} / {@link #downloaded} say why.
     */
    public Page showReport(int ms) {
        resetMessages();
        downloaded = "";
        tabsBefore = page.context().pages().size();
        final String[] dl = {""};
        java.util.function.Consumer<com.microsoft.playwright.Download> onDl = d -> { dl[0] = d.suggestedFilename(); try { d.cancel(); } catch (Exception ignore) { } };
        page.onDownload(onDl);
        try {
            page.locator("button[ng-click^='ShowReport']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
            long end = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < end) {
                List<Page> pages = page.context().pages();
                if (pages.size() > tabsBefore) {
                    Page t = pages.get(pages.size() - 1);
                    try { t.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(60000)); } catch (Exception ignore) { }
                    return t;
                }
                if (!dl[0].isEmpty()) { downloaded = dl[0]; return null; }
                if (!messages().isEmpty() && System.currentTimeMillis() > end - ms + 4000) return null;
                page.waitForTimeout(500);
            }
            return null;
        } finally { page.offDownload(onDl); downloaded = dl[0].isEmpty() ? downloaded : dl[0]; }
    }

    // ---- the report tab (SAP Crystal Reports viewer) --------------------------

    private static final String PAGE_BOX = "#text_CrystalReportViewer1_toptoolbar_selectPg";
    private static final String NEXT_PAGE = "#CrystalReportViewer1_toptoolbar_nextPg";

    /** Wait (up to {@code ms}) until the viewer has drawn the report (its page box shows "1 of …"). */
    public boolean waitForViewer(Page tab, int ms) {
        try { tab.setViewportSize(1920, 1075); } catch (Exception ignore) { }   // a new tab opens narrow (800 px) and cuts the report off
        try {
            tab.waitForFunction("(s) => { const e=document.querySelector(s); return e && /\\d+\\s*of/i.test(e.value||''); }", PAGE_BOX, new Page.WaitForFunctionOptions().setTimeout(ms));
            tab.waitForTimeout(1500);
            return true;
        } catch (Exception e) { return false; }
    }

    /** When the report tab shows the server's error page ("Server Error in '/' Application"), its message; else "". */
    public String serverError(Page tab) {
        try {
            Object r = tab.evaluate("() => { const t=document.body ? document.body.innerText : ''; if(/504 Gateway Time-?out|Gateway Time-?out/i.test(t)) return '504 Gateway Time-out';"
                    + " if(!/Server Error in/i.test(t)) return '';"
                    + " const i=document.querySelector('h2 i, h2'); return (i ? i.innerText : t).replace(/\\s+/g,' ').trim().slice(0,300); }");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** The viewer's page box, e.g. "1 of 3" ("" when the tab is not the viewer). */
    public String pageBox(Page tab) {
        try { Object v = tab.evaluate("(s) => (document.querySelector(s)||{}).value || ''", PAGE_BOX); return v == null ? "" : v.toString().trim(); }
        catch (Exception e) { return ""; }
    }

    /** All the text the report shows (the viewer draws the report inside a frame). */
    public String reportText(Page tab) {
        StringBuilder sb = new StringBuilder();
        for (com.microsoft.playwright.Frame f : tab.frames()) {
            try {
                Object t = f.evaluate("() => document.body ? document.body.innerText : ''");
                if (t != null) sb.append(' ').append(t.toString());
            } catch (Exception ignore) { }
        }
        return sb.toString().replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * Screenshot EVERY page of the report: page 1, then "Go to Next Page" until the page box shows the last page (at most
     * {@code maxPages}). Returns one screenshot per page.
     */
    public List<byte[]> capturePages(Page tab, int maxPages) {
        List<byte[]> shots = new ArrayList<>();
        String box = pageBox(tab);
        shots.add(tab.screenshot());
        for (int p = 2; p <= maxPages; p++) {
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(\\d+)\\s*of\\s*(\\d+)(\\+?)").matcher(box);
            if (!mm.find()) break;
            int cur = Integer.parseInt(mm.group(1)), total = Integer.parseInt(mm.group(2));
            if (cur >= total && mm.group(3).isEmpty()) break;
            Locator next = tab.locator(NEXT_PAGE);
            if (next.count() == 0) break;
            next.click(new Locator.ClickOptions().setTimeout(6000));
            final String before = box;
            try { tab.waitForFunction("([s,b]) => { const e=document.querySelector(s); return e && e.value !== b; }", java.util.Arrays.asList(PAGE_BOX, before),
                    new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception e) { break; }   // the page did not change: last page
            tab.waitForTimeout(1500);
            box = pageBox(tab);
            shots.add(tab.screenshot());
        }
        return shots;
    }

    /** Join page screenshots one under the other into a single image (so one report = one test step with one screenshot). */
    public static byte[] stitch(List<byte[]> shots) {
        if (shots == null || shots.isEmpty()) return null;
        if (shots.size() == 1) return shots.get(0);
        try {
            List<java.awt.image.BufferedImage> imgs = new ArrayList<>();
            int w = 0, h = 0;
            for (byte[] s : shots) {
                java.awt.image.BufferedImage i = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(s));
                imgs.add(i); w = Math.max(w, i.getWidth()); h += i.getHeight() + 6;
            }
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setColor(java.awt.Color.DARK_GRAY); g.fillRect(0, 0, w, h);
            int y = 0;
            for (java.awt.image.BufferedImage i : imgs) { g.drawImage(i, 0, y, null); y += i.getHeight() + 6; }
            g.dispose();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) { return shots.get(0); }
    }

    /** Close the report tab and come back to the console. */
    public void closeTab(Page tab) {
        if (tab != null && tab != page) { try { tab.close(); } catch (Exception ignore) { } }
        page.bringToFront();
        waitForAngular(800);
    }

    /** Click Clear (real click) — empties the filter table. */
    public void clear() {
        Locator c = page.locator("button[ng-click='ClearParameters()']:visible");
        if (c.count() > 0) c.first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(1000);
    }
}
