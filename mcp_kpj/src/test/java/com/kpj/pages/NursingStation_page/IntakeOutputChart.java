package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Intake Output Chart</b> — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Intake Output Chart</b> ({@code #/IntakeOutputChart}) → enter
 * <b>MRN</b> + search → fill the intake/output rows (Oral/NG, IV/SC, Intake Others, Headache, Oral Fluid
 * Intake, Total Intake, Urine, Vomit, Bowel, Tube) → <b>Save</b> ({@code IUDIntakeoutputDetails()}) →
 * toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-10):</b> {@code IntakeOutputChart.MRNo} with a
 * {@code SearchPatientByMRNo()} icon; the chart's value cells are {@code INCl.intakeoutputvalues}
 * (number) with {@code INCl.intakecomment} / {@code INC.intakecomment} / {@code INRw.intakecomment} /
 * {@code INC.outputcomment} / {@code INCl.outputcomment} note fields, plus computed totals
 * {@code INC.intaketotalvalues}, {@code INCl.intaketotalvalues}, {@code INC.outputtotalvalues} and
 * {@code INCl.outputtotalvalues}. Buttons: {@code IUDIntakeoutputDetails()} (Save),
 * {@code resetForm()} (Clear), {@code BackToNursingStation()} (Back).</p>
 *
 * <h2>Every value cell shares one ng-model</h2>
 * <p>The chart is an ng-repeat: each row's value box is {@code INCl.intakeoutputvalues}, so the model name
 * identifies nothing on its own. Cells are therefore addressed by their <b>row label</b> — the item name
 * in the row — via {@link #enterValue}. {@link #describeChart()} prints the label-to-cell map, which is
 * the only reliable way to see what this screen actually offers.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class IntakeOutputChart extends BasePage {

    public IntakeOutputChart(Page page) { super(page); }

    public static final String ROUTE = "#/IntakeOutputChart";
    private static final String M = "IntakeOutputChart.";

    public String lastControls = "", lastChartMap = "";
    public String lastMrn = "", lastSearchResult = "";
    /** item name -> what was written (or why not), in the order filled. */
    public final java.util.LinkedHashMap<String, String> filled = new java.util.LinkedHashMap<>();

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  if(e.readOnly || e.disabled) return '(read-only)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const setInp=(ng,v)=>setEl(byNg(ng), v);"
            + "const valueCells=()=>[...document.querySelectorAll(\"input[ng-model='INCl.intakeoutputvalues']\")].filter(vis);"
            // The chart is COLUMN-oriented: the item names (Oral/Ng, IV/SC, Urine, ...) are table HEADERS,
            // not row labels, and they are spread over several tables (intake and output are separate).
            // So a cell is addressed by matching a header, taking its column index, and reading that
            // column in the body rows.
            + "const chartTables=()=>[...document.querySelectorAll('table')]"
            + "  .filter(t=>t.querySelector(\"input[ng-model='INCl.intakeoutputvalues']\"));"
            + "const headerCells=t=>{ const hr=t.querySelector('thead tr') || t.querySelector('tr');"
            + "  return hr? [...hr.children] : []; };"
            + "const cellByHeader=name=>{"
            + "  const rx=new RegExp('^\\\\s*'+String(name).replace(/[.*+?^${}()|[\\]\\\\]/g,'\\\\$&')"
            + "        .replace(/[\\/\\s]+/g,'[\\\\s/]*')+'\\\\s*$','i');"
            + "  for(const t of chartTables()){"
            + "    const heads=headerCells(t);"
            + "    const ci=heads.findIndex(h=>rx.test(norm(h.textContent)));"
            + "    if(ci<0) continue;"
            + "    const hr=t.querySelector('thead tr') || t.querySelector('tr');"
            + "    const rows=[...t.querySelectorAll('tr')].filter(r=>r!==hr"
            + "        && r.querySelector(\"input[ng-model='INCl.intakeoutputvalues']\"));"
            + "    for(const r of rows){ const cell=r.children[ci];"
            + "      const inp=cell? cell.querySelector(\"input[ng-model='INCl.intakeoutputvalues']\") : null;"
            + "      if(inp && vis(inp)) return inp; } }"
            + "  return null; };"
            // Totals are NOT columns in the entry grid — they are their own fields, and DevHIS computes
            // them, so they may well be read-only. Resolve them by model when no header matches.
            + "const totalCell=name=>{ const n=String(name).toLowerCase();"
            + "  let models=[];"
            + "  if(/total\\s*intake/.test(n)) models=['INCl.intaketotalvalues','INC.intaketotalvalues'];"
            + "  else if(/total\\s*output/.test(n)) models=['INCl.outputtotalvalues','INC.outputtotalvalues'];"
            + "  for(const m of models){ const e=byNg(m); if(e) return e; }"
            + "  return null; };";

    /** Collect toasts from the NARROWEST elements, so two messages never merge into one blob. */
    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    private static final String ARM_TOASTS = TOAST_ELS
            + " window.__ioToasts=[]; if(window.__ioObs) window.__ioObs.disconnect();"
            + " const grab=()=>{ toastEls().forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__ioToasts.includes(t)) window.__ioToasts.push(t); }); };"
            + " window.__ioObs=new MutationObserver(grab); window.__ioObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Nursing Station</b> → <b>Intake Output Chart</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("IntakeOutputChart.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^intake\\s*\\/?\\s*output\\s*chart$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/IntakeOutputChart/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__ioMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ioMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("IntakeOutputChart.nav: menu click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("IntakeOutputChart.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("IntakeOutputChart.nav: screen did not finish rendering"); }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("intakeoutputchart");
    }

    // ---- MRN + search ------------------------------------------------------

    /**
     * Enter the <b>MRN</b> and click the search icon ({@code SearchPatientByMRNo()}).
     *
     * <p>Success is judged on the chart coming to life — value cells appearing — since this screen has no
     * patient-name field to assert against.</p>
     */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;
        page.evaluate("(m) => {" + JS
                + " setInp('" + M + "MRNo', m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ioMrnSearch'; }", mrn);
        try { page.locator("#__ioMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("IntakeOutputChart.searchByMrn: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ioMrnSearch'); if(e) e.removeAttribute('id'); }");

        try {
            page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='INCl.intakeoutputvalues']\").length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);

        // Report the MRN box AND the model behind it, not just that cells rendered. Save answers
        // "Please Enter MRN No.!" when the model is empty, and the chart paints regardless — so a cell
        // count alone says nothing about whether the patient is bound where Save actually looks.
        Object r = page.evaluate("() => {" + JS + TOAST_ELS
                + " const cells=valueCells().length;"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " const box=[...document.querySelectorAll(\"input[ng-model='IntakeOutputChart.MRNo']\")]"
                + "   .find(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " let model='(unread)';"
                + " try{ if(box){ const s=angular.element(box).scope();"
                + "        const o=s && s.IntakeOutputChart;"
                + "        model = o? JSON.stringify(o.MRNo) : '(no model object)'; } }catch(e){}"
                + " return 'valueCells='+cells+' | mrnBox=\"'+(box?box.value:'(no box)')+'\"'"
                + "   +' | model.MRNo='+model+(msg?' | msg='+msg:''); }");
        lastSearchResult = r == null ? "" : r.toString();
        System.out.println("IntakeOutputChart: search MRN " + mrn + " -> " + lastSearchResult);
        return lastSearchResult;
    }

    /** True when the chart rendered value cells for the patient. */
    public boolean chartLoaded() {
        if (lastSearchResult == null || lastSearchResult.isEmpty()) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("valueCells=(\\d+)").matcher(lastSearchResult);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
    }

    // ---- chart map ---------------------------------------------------------

    /**
     * Diagnostic: every value cell with its row label and whether it is editable.
     *
     * <p>Labels are the only way to address these cells (they all share one ng-model), and computed totals
     * are read-only — both facts are invisible without this.</p>
     */
    public String describeChart() {
        Object r = page.evaluate("() => {" + JS
                + " const cells=valueCells(); if(!cells.length) return '(no value cells)';"
                + " const tbl=cells[0].closest('table');"
                + " if(!tbl) return '(cells are not in a table) count='+cells.length;"
                // Which way round is the grid? Dump BOTH the header row and the first column, plus the
                // cell's own column index, so the addressing scheme is visible rather than assumed.
                + " const per=chartTables().map((t,i)=>'  table#'+i+' headers: '"
                + "   +headerCells(t).map(h=>norm(h.textContent)).filter(x=>x).slice(0,20).join(' | '));"
                + " return 'cells='+cells.length+' chartTables='+chartTables().length+'\\n'+per.join('\\n'); }");
        lastChartMap = r == null ? "" : r.toString();
        System.out.println("IntakeOutputChart chart structure:\n" + lastChartMap);
        return lastChartMap;
    }

    /** Diagnostic: non-repeating controls on the screen. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/INCl\\.|INC\\.|INRw\\./.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+(e.getAttribute('placeholder')||'')+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " return inp.join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("IntakeOutputChart controls: " + lastControls);
        return lastControls;
    }

    // ---- filling -----------------------------------------------------------

    /**
     * Enter a value into the chart row whose label matches {@code itemName} (case-insensitive, spaces and
     * slashes flexible).
     *
     * @return the value written, or {@code (not-found)} / {@code (read-only)}
     */
    public String enterValue(String itemName, String value) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [name, v] = args;"
                + " const e=cellByHeader(name) || totalCell(name);"
                + " if(!e) return '(not-found)';"
                + " return setEl(e, v); }", java.util.List.of(itemName, value));
        String out = r == null ? "" : r.toString();
        filled.put(itemName, out);
        System.out.println("IntakeOutputChart: " + itemName + " = " + out);
        return out;
    }

    /** Enter the same value into each named row, in order. Returns a one-line summary. */
    public String enterValues(java.util.List<String> itemNames, String value) {
        StringBuilder sb = new StringBuilder();
        for (String name : itemNames) {
            String res = enterValue(name, value);
            if (sb.length() > 0) sb.append(" | ");
            sb.append(name).append('=').append(res);
            waitForAngular(150);
        }
        return sb.toString();
    }

    /** How many of the requested rows actually took a value. */
    public int filledCount() {
        int n = 0;
        for (String v : filled.values()) if (v != null && !v.startsWith("(")) n++;
        return n;
    }

    /** Requested rows the app COMPUTES and will not accept typing into (read-only). Not a defect. */
    public String computedRows() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : filled.entrySet()) {
            if ("(read-only)".equals(e.getValue())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey());
            }
        }
        return sb.toString();
    }

    /** Requested rows that do not exist on the chart at all — a genuine mismatch worth failing on. */
    public String missingRows() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : filled.entrySet()) {
            if ("(not-found)".equals(e.getValue())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey());
            }
        }
        return sb.toString();
    }

    /** Names that could not be filled, with the reason. */
    public String unfilled() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : filled.entrySet()) {
            if (e.getValue() != null && e.getValue().startsWith("(")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append(' ').append(e.getValue());
            }
        }
        return sb.toString();
    }

    // ---- save --------------------------------------------------------------

    /** Click <b>Save</b> ({@code IUDIntakeoutputDetails()}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " window.__ioPrior=(window.__ioToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDIntakeoutputDetails/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__ioSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("IntakeOutputChart.save: Save button not found"); return ""; }

        try { page.locator("#__ioSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("IntakeOutputChart.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ioSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__ioToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => {" + TOAST_ELS
                    + " toastEls().forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "   if(t && !(window.__ioToasts||[]).includes(t)) (window.__ioToasts=window.__ioToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const prior=window.__ioPrior||[];"
                + " const a=(window.__ioToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => { const n=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".filter(e=>e.offsetParent!==null && (e.textContent||'').trim()); return n.length===0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("IntakeOutputChart: a previous toast is still on screen — excluding it from this capture");
        }
    }
}
