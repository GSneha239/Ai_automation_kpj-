package com.kpj.pages.Ip.BedManagement_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * IP &gt; Bed Management &gt; <b>Bed Release</b> — Page Object ({@code #/BedRelease}).
 *
 * <p>Checked by hand and against the screen's own controller (BedReleaseController.js, build v2026.11.17.488):</p>
 * <ul>
 *   <li>Three tabs: Discharge Bed List ({@code DischargeBedList}), Transfer Bed List ({@code transferBedList}),
 *       Additional Bed List ({@code AdditionalBedList}); each has a search box, export buttons and paging.</li>
 *   <li>A <b>pink</b> row ({@code interorfinal == 0}) = final bill not generated: Notify and Released are disabled.</li>
 *   <li>Per row, in order: <b>Select</b> ☑ → <b>Notify</b> 🔔 (sends the housekeeping message for SELECTED rows;
 *       unlocks Start) → <b>Start</b> ▶ (saved at once) → <b>End</b> ⏻ (saved at once; needs Start) →
 *       <b>Cleaning Complete</b> ☑ (saved at once, "Cleaning Completed Successfully"; needs End) →
 *       <b>Released</b> ☑ (needs Cleaning Complete) → <b>Save</b> → bed checklist popup → Save →
 *       "Bed Released Successfully" and the row leaves the list.</li>
 *   <li>Save refuses when End date-time is not after Start date-time; both are kept to the MINUTE.</li>
 * </ul>
 */
public class BedRelease extends BasePage {

    private static final String HREF = "#/bedrelease";
    private static final String GRID = "#ipdDischargeReleaseGrid";
    private static final String ROW = "tr[ng-repeat='Discharge in DischargeBedList']";

    public BedRelease(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /** IP &gt; Bed Management &gt; Bed Release through the sidebar. True only when the screen is really on view. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*ip\\s*$");
            expandMenuEntry("^\\s*ip\\s*$", "^\\s*bed management\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*ip\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__brLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__brLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("BedRelease.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__brLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(GRID, new Page.WaitForSelectorOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("BedRelease.nav: the Discharge Bed grid is not up"); }
        return onScreen();
    }

    /** On this screen — the route AND the Discharge Bed grid. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(HREF)) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", GRID));
    }

    /** Wait for the Discharge Bed List to load and install the helpers (alert hook, scope/row finders). */
    public void prepare() {
        installHelpers();
        try {
            page.waitForFunction("() => { const s=window.__brScope(); return s && Array.isArray(s.DischargeBedList); }", null,
                    new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { System.out.println("BedRelease.prepare: Discharge Bed List did not load"); }
        waitForAngular(1500);
    }

    private void installHelpers() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                + "window.__brScope = function(){ const f=document.getElementById('BedRelease'); if(!f||!window.angular) return null; let s=angular.element(f).scope();"
                + "  for(let i=0;i<10&&s;i++){ if(s.DischargeBedList!==undefined) return s; s=s.$parent; } return null; };"
                + "window.__brRow = function(mrn){ return [...document.querySelectorAll(\"" + ROW + "\")].find(tr=>{ const s=angular.element(tr).scope();"
                + "  return s && s.Discharge && String(s.Discharge.MrNo).trim()===String(mrn).trim() && tr.offsetParent!==null; }) || null; };"
                + "}");
    }

    // ---- lists / tabs / search --------------------------------------------

    /** Size of a list the controller holds: "Discharge", "Transfer" or "Additional" (-1 when not loaded). */
    public int listSize(String tab) {
        String model = "Transfer".equalsIgnoreCase(tab) ? "transferBedList" : "Additional".equalsIgnoreCase(tab) ? "AdditionalBedList" : "DischargeBedList";
        Object r = page.evaluate("(m) => { const s=window.__brScope(); return s && Array.isArray(s[m]) ? s[m].length : -1; }", model);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Pink rows (final bill not generated) in the Discharge Bed List. */
    public int pinkRowCount() {
        Object r = page.evaluate("() => { const s=window.__brScope(); return s && s.DischargeBedList ? s.DischargeBedList.filter(d=>d.interorfinal==0).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Click a tab ("Discharge", "Transfer", "Additional") with a real click and return its list size after it loads. */
    public int openTab(String tab) {
        String target = "Transfer".equalsIgnoreCase(tab) ? "#transferBed" : "Additional".equalsIgnoreCase(tab) ? "#AdditionalBed" : "#DischargeBed";
        page.locator("a[data-target='" + target + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(3000);
        return listSize(tab);
    }

    /** Type into the Discharge search box (real typing) and return the number of rows left on view. */
    public int searchDischarge(String text) {
        Locator box = page.locator("input[ng-model='ipDischargeBedSearch']").first();
        box.fill("");
        box.fill(text);
        waitForAngular(1500);
        return visibleRows();
    }

    /** Rows currently drawn in the Discharge grid. */
    public int visibleRows() {
        Object r = page.evaluate("(s) => [...document.querySelectorAll(s)].filter(tr=>tr.offsetParent!==null).length", ROW);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** True when every drawn Discharge row shows the given MRN. */
    public boolean allVisibleRowsAre(String mrn) {
        return Boolean.TRUE.equals(page.evaluate("([s,m]) => { const rows=[...document.querySelectorAll(s)].filter(tr=>tr.offsetParent!==null);"
                + " return rows.length>0 && rows.every(tr=>tr.textContent.includes(m)); }", java.util.Arrays.asList(ROW, mrn)));
    }

    // ---- choosing rows ----------------------------------------------------

    /** MRN of the first pink row (final bill not generated), or null. */
    public String firstPinkRowMrn() {
        Object r = page.evaluate("() => { const s=window.__brScope(); const d=s && s.DischargeBedList ? s.DischargeBedList.find(x=>x.interorfinal==0) : null; return d ? String(d.MrNo) : null; }");
        return r == null ? null : r.toString();
    }

    /**
     * MRN of a row that can be released (final bill generated, not yet released). Prefers the row at the EARLIEST
     * stage (not notified, then not started …) so the run walks every step. Null when there is none.
     */
    public String pickReleasableRowMrn() {
        Object r = page.evaluate("() => { const s=window.__brScope(); if(!s||!s.DischargeBedList) return null;"
                + " const ok=s.DischargeBedList.filter(d=>d.interorfinal!=0 && !d.isReleased);"
                + " const stage=d=> !d.isnotify?0 : !d.StartTime?1 : !d.EndTime?2 : !d.iscleaningcomplete?3 : 4;"
                + " ok.sort((a,b)=>stage(a)-stage(b)); return ok.length ? String(ok[0].MrNo) : null; }");
        return r == null ? null : r.toString();
    }

    /** The row's state from the controller: notified / start / end / cleaning / released / final-bill flag. */
    public String rowState(String mrn) {
        Object r = page.evaluate("(m) => { const s=window.__brScope(); const d=s && s.DischargeBedList ? s.DischargeBedList.find(x=>String(x.MrNo).trim()===m) : null;"
                + " if(!d) return 'not in list';"
                + " return d.PatientName+' | bed '+d.Bed+' | notified='+(!!d.isnotify)+' | start='+(d.StartTime||'-')+' | end='+(d.EndTime||'-')"
                + "  +' | cleaning='+(!!d.iscleaningcomplete)+' | released='+(!!d.isReleased)+' | finalBill='+(d.interorfinal!=0); }", mrn);
        return r == null ? "" : r.toString();
    }

    /** One flag of the row from the controller: "isnotify", "StartTime", "EndTime", "iscleaningcomplete", "isReleased". */
    public boolean rowHas(String mrn, String field) {
        return Boolean.TRUE.equals(page.evaluate("([m,f]) => { const s=window.__brScope(); const d=s && s.DischargeBedList ? s.DischargeBedList.find(x=>String(x.MrNo).trim()===m) : null;"
                + " return !!(d && d[f]); }", java.util.Arrays.asList(mrn, field)));
    }

    /** True when the row is still in the Discharge Bed List. */
    public boolean inList(String mrn) {
        return Boolean.TRUE.equals(page.evaluate("(m) => { const s=window.__brScope(); return !!(s && s.DischargeBedList && s.DischargeBedList.some(x=>String(x.MrNo).trim()===m)); }", mrn));
    }

    // ---- row controls -----------------------------------------------------

    /** CSS (inside the row) of each control. */
    private static String control(String which) {
        switch (which) {
            case "Select":   return "input[ng-model='Discharge.Isdefault']";
            case "Notify":   return "button[ng-click^='sendmsg(']";
            case "Start":    return "button[ng-click^='displayStartTime(']";
            case "End":      return "button[ng-click^='displayEndTime(']";
            case "Cleaning": return "input[ng-model='Discharge.iscleaningcomplete']";
            case "Released": return "input[ng-model='Discharge.isReleased']";
            default: throw new IllegalArgumentException(which);
        }
    }

    /** True when the row's control is disabled (or the row / control is not on view). */
    public boolean controlDisabled(String mrn, String which) {
        return !Boolean.FALSE.equals(page.evaluate("([m,c]) => { const tr=window.__brRow(m); if(!tr) return true; const e=tr.querySelector(c); return !e || e.disabled; }",
                java.util.Arrays.asList(mrn, control(which))));
    }

    /** True when the row is drawn pink (final bill not generated). */
    public boolean rowIsPink(String mrn) {
        return Boolean.TRUE.equals(page.evaluate("(m) => { const tr=window.__brRow(m); return !!tr && tr.classList.contains('my-style-11'); }", mrn));
    }

    /**
     * Real click on one of the row's controls. Returns "clicked", "disabled" or "missing". Clears the alert log
     * first so {@link #messages()} shows only what this click produced.
     */
    public String clickControl(String mrn, String which) {
        Object tagged = page.evaluate("([m,c]) => { window.__alerts=[]; const tr=window.__brRow(m); if(!tr) return 'missing'; const e=tr.querySelector(c);"
                + " if(!e) return 'missing'; if(e.disabled) return 'disabled'; e.id='__brCtl'; e.scrollIntoView({block:'center'}); return 'ok'; }",
                java.util.Arrays.asList(mrn, control(which)));
        if (!"ok".equals(String.valueOf(tagged))) return String.valueOf(tagged);
        try { page.locator("#__brCtl").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BedRelease.click " + which + ": " + String.valueOf(e.getMessage()).split("\n")[0]); return "click failed"; }
        page.evaluate("() => { const e=document.getElementById('__brCtl'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);
        return "clicked";
    }

    /** Tick the row's Select box if it is not ticked (Notify and Save act only on selected rows). */
    public boolean ensureSelected(String mrn) {
        boolean on = Boolean.TRUE.equals(page.evaluate("(m) => { const tr=window.__brRow(m); const e=tr && tr.querySelector(\"input[ng-model='Discharge.Isdefault']\"); return !!(e && e.checked); }", mrn));
        if (!on) clickControl(mrn, "Select");
        return Boolean.TRUE.equals(page.evaluate("(m) => { const tr=window.__brRow(m); const e=tr && tr.querySelector(\"input[ng-model='Discharge.Isdefault']\"); return !!(e && e.checked); }", mrn));
    }

    /** Wait until the controller has re-fetched the list and the row shows the given flag (or time runs out). */
    public boolean waitForRow(String mrn, String field, int ms) {
        try {
            page.waitForFunction("([m,f]) => { const s=window.__brScope(); const d=s && s.DischargeBedList ? s.DischargeBedList.find(x=>String(x.MrNo).trim()===m) : null; return !!(d && d[f]); }",
                    java.util.Arrays.asList(mrn, field), new Page.WaitForFunctionOptions().setTimeout(ms));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Wait for the clock to pass into the next minute — Start and End are stored to the minute and Save needs End &gt; Start. */
    public void waitForNextMinute() {
        int sec = java.time.LocalTime.now().getSecond();
        page.waitForTimeout((61 - sec) * 1000L);
    }

    // ---- save + checklist -------------------------------------------------

    /** Click Save under the Discharge list. Returns true when the bed checklist popup opened. */
    public boolean clickSave() {
        page.evaluate("() => { window.__alerts=[]; }");
        page.locator("#DischargeBed button[ng-click='OpenDischargeBed();']").first().click(new Locator.ClickOptions().setTimeout(8000));
        try {
            page.waitForFunction("() => { const m=document.getElementById('CklistModal'); return !!m && m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none'; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            waitForAngular(1500);
            return true;
        } catch (Exception e) {
            waitForAngular(2000);
            return false;
        }
    }

    /** In the checklist popup: tick Select-All (real click), then Save. Returns the number of checklist items. */
    public int completeChecklistAndSave() {
        Object n = page.evaluate("() => { const s=window.__brScope(); return s && s.BEDCkList ? s.BEDCkList.length : 0; }");
        Locator all = page.locator("#CklistModal input[ng-model='checkbox.ChkSelectAllChecklist']").first();
        try { if (all.isVisible() && !all.isChecked()) all.click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BedRelease.checklist: select-all - " + String.valueOf(e.getMessage()).split("\n")[0]); }
        waitForAngular(600);
        page.evaluate("() => { window.__alerts=[]; }");
        page.locator("#IUDGross").click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(4000);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Messages (JAlert / alert) since the last action, without bare type labels. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> messages() {
        Object r = page.evaluate("() => window.__alerts || []");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) r) {
                String t = o == null ? "" : o.toString().trim();
                if (!t.isEmpty() && !t.matches("(?i)error|success|warning|info|alert|notice")) out.add(t);
            }
        }
        return out;
    }
}
