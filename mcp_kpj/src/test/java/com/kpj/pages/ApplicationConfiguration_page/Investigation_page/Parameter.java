package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>Parameter</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>Parameter</b> → <b>Add</b> →
 * <b>Parameter Details</b> (Parameter Code, Parameter Name, Print Name, Parameter Unit, Parameter SSI Unit,
 * LOINC Code, Conver. Factor) → <b>Age Wise Range</b> (Gender + Lower/Alert Low/Critical Low and
 * Upper/Alert High/Critical High) → <b>Add</b> (append the range row) → <b>Submit</b> → success toast.</p>
 *
 * <p>Master-detail: the range only reaches the server once the inner <b>Add</b> has appended it, so
 * {@link #clickAddDetail()} reports the row-count delta — the sibling Lab Machine screen rejects a Submit with no
 * appended row, and this one is built the same way.</p>
 *
 * <p>Fields are resolved by LABEL with a CLAIM set, MOST SPECIFIC FIRST: Print Name is claimed before Parameter
 * Name, and the six range boxes before any loose numeric fallback, so no two roles can land on the same input.
 * <b>Parameter Unit and Parameter SSI Unit are drop-downs, not text boxes</b> — they are resolved among the
 * selects, where SSI is likewise claimed before the plain Unit.</p>
 */
public class Parameter extends BasePage {

    public Parameter(Page page) { super(page); }

    public static final String ROUTE = "#/Parameter";
    public String lastCode = "", lastName = "", lastPrintName = "", lastUnit = "", lastSsiUnit = "";
    public String lastLoinc = "", lastConvFactor = "", lastAgeRange = "", lastGender = "";
    public String lastRange = "";
    private String menuHref = "";
    /** Whether the screen's existing rows use all-numeric codes (set by {@link #sampleCodeStyle()}). */
    private boolean numericCodes = true;
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Real laboratory parameters, with the print name / units / LOINC that genuinely go with each. */
    private static final String[][] PARAMS = {
            // name,                     print, unit,      SSI unit,  LOINC
            { "Haemoglobin",             "HGB",  "g/dL",   "g/L",     "718-7"  },
            { "White Blood Cell Count",  "WBC",  "10^3/uL", "10^9/L", "6690-2" },
            { "Platelet Count",          "PLT",  "10^3/uL", "10^9/L", "777-3"  },
            { "Serum Creatinine",        "CREA", "mg/dL",  "umol/L",  "2160-0" },
            { "Random Blood Sugar",      "RBS",  "mg/dL",  "mmol/L",  "2345-7" },
            { "Total Bilirubin",         "TBIL", "mg/dL",  "umol/L",  "1975-2" }
    };

    /** Age bands a lab would actually configure a reference range for. */
    private static final String[][] AGE_BANDS = { { "18", "60" }, { "13", "17" }, { "1", "12" }, { "61", "99" }, { "0", "1" } };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: every box on this form, resolved by LABEL with a CLAIM set. Order matters twice over —
     * "Parameter SSI Unit" is claimed before "Parameter Unit" (the looser pattern would otherwise take it), and
     * the six range boxes are claimed before anything generic. select2 chrome ({@code s2id_autogen*}) is skipped.
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
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const flat=e=>mdl(e).replace(/[^a-z]/gi,''); const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            // --- Parameter Details. Print Name before Parameter Name — both labels contain "Name". ---
            + " const printEl = pick(e=>/print\\s*name/i.test(lbl(e)), e=>/printname/i.test(flat(e)));"
            + " const codeEl  = pick(e=>/parameter\\s*code/i.test(lbl(e)), e=>/^code\\b/i.test(lbl(e)), e=>/parametercode|\\.code$/i.test(flat(e)+mdl(e)));"
            + " const nameEl  = pick(e=>/parameter\\s*name/i.test(lbl(e)), e=>/^name\\b/i.test(lbl(e)), e=>/parametername/i.test(flat(e)));"
            // NB: Parameter Unit and Parameter SSI Unit are SELECTS on this screen, not text boxes — they are
            // resolved in FIND_SELECTS. A text-input scan finds nothing for them and reports "(no field)".
            + " const loincEl = pick(e=>/loinc/i.test(lbl(e)), e=>/loinc/i.test(flat(e)));"
            + " const convEl  = pick(e=>/conver|factor/i.test(lbl(e)), e=>/conver|factor/i.test(flat(e)));"
            // --- Age Wise Range: the two age boxes, then the six limits (specific words, no overlap). ---
            + " const ageFromEl = pick(e=>/from\\s*age|age\\s*from/i.test(lbl(e)), e=>/fromage|agefrom/i.test(flat(e)), e=>/^age/i.test(lbl(e)));"
            + " const ageToEl   = pick(e=>/to\\s*age|age\\s*to/i.test(lbl(e)), e=>/toage|ageto/i.test(flat(e)));"
            + " const critLowEl = pick(e=>/critical\\s*low/i.test(lbl(e)), e=>/criticallow/i.test(flat(e)));"
            + " const alertLowEl= pick(e=>/alert\\s*low/i.test(lbl(e)), e=>/alertlow/i.test(flat(e)));"
            + " const lowerEl   = pick(e=>/lower\\s*value|lower/i.test(lbl(e)), e=>/lowervalue|lower/i.test(flat(e)));"
            + " const critHighEl= pick(e=>/critical\\s*high/i.test(lbl(e)), e=>/criticalhigh/i.test(flat(e)));"
            + " const alertHighEl=pick(e=>/alert\\s*high/i.test(lbl(e)), e=>/alerthigh/i.test(flat(e)));"
            + " const upperEl   = pick(e=>/upper\\s*value|upper/i.test(lbl(e)), e=>/uppervalue|upper/i.test(flat(e)));";

    /**
     * JS helper: the three real drop-downs — <b>Parameter SSI Unit</b>, <b>Parameter Unit</b> and <b>Gender</b>.
     * Deliberately NOT filtered on visibility: select2 hides the real {@code <select>} behind class
     * {@code select2-offscreen}, and that is still the element to drive.
     *
     * <p>SSI is claimed FIRST — "Parameter SSI Unit" also answers a plain "unit" test, so letting Parameter Unit
     * pick first would take the SSI box and leave the other unset. The four {@code …sign} selects (single-option
     * comparison operators beside each limit) are excluded: they carry the limits' labels and would otherwise
     * masquerade as the unit drop-downs.</p>
     */
    private static final String FIND_SELECTS =
            " const slbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const sels=[...document.querySelectorAll('select')].filter(e=>{ const m=e.getAttribute('ng-model')||'';"
            + "   return !/pagination/i.test(m) && !/sign$/i.test(m); });"
            + " const smdl=e=>e.getAttribute('ng-model')||'';"
            + " const ssiSel = sels.find(e=>/ssi/i.test(smdl(e)) || /ssi/i.test(slbl(e)));"
            + " const unitSel = sels.find(e=>e!==ssiSel && (/unit/i.test(smdl(e)) || /unit/i.test(slbl(e))));"
            + " const genderEl = sels.find(e=>/gender|sex/i.test(smdl(e)) || /gender|sex/i.test(slbl(e)));";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Close any open select2 before a real click — its mask sits over the whole page and swallows the click. */
    private void closeSelect2() {
        try {
            page.evaluate("() => { try{ if(window.jQuery){ jQuery('select').each(function(){ try{ jQuery(this).select2('close'); }catch(e){} }); } }catch(e){}"
                    + " document.querySelectorAll('#select2-drop-mask,.select2-drop-mask').forEach(m=>{ m.style.display='none'; });"
                    + " document.querySelectorAll('.select2-drop-active,.select2-drop').forEach(d=>{ d.classList.remove('select2-drop-active'); d.style.display='none'; });"
                    + " try{ document.activeElement && document.activeElement.blur(); }catch(e){} }");
        } catch (Exception ignore) { }
        waitForAngular(300);
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
                // EXACT "Investigation" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*investigations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1500);
                // EXACT text only — "Parameter" is a common word and a prefix/contains match would hit several
                // other links (and the sibling Lab Machine screen has a "Parameter Name" field of its own).
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*parameters?\\s*$/i.test(norm(x.textContent)));"
                        + " if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("Parameter.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__prMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("Parameter.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__prMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__prMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("Parameter.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("Parameter.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Parameter screen. The URL is checked against the route MINED FROM THE MENU where possible, because a
     * screen's hash need not contain its name ("Lab Sample" routes to {@code #/LabTestSampleType}). The field gate
     * wants a Parameter Code together with a Name or a Unit, so the leftover "Transfer" screen — a Code and a
     * Submit and nothing else — cannot pass.
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        String want = (menuHref.isEmpty() ? ROUTE : menuHref).replaceAll("^#/?", "").toLowerCase();
        boolean routeOk = (!want.isEmpty() && u.contains(want)) || u.contains("parameter");
        if (!routeOk) return false;
        return Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS
                + " if(codeEl && (nameEl || loincEl)) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }"));
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Read the LIST grid's existing Code values and decide which format to generate. Call this BEFORE Add.
     *
     * <p>Learned on the sibling Interpretation Template: its save proc casts existing codes to {@code int}, so a
     * single {@code IT#####} row inserted by a test broke every later save on that screen for everyone.</p>
     */
    public String sampleCodeStyle() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " let cells=[...document.querySelectorAll('.ui-grid-row .ui-grid-cell-contents')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " if(!cells.length) cells=[...document.querySelectorAll('table tbody tr td')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " const codes=cells.filter(t=>t.length<=12 && /^[A-Za-z0-9._-]+$/.test(t)).slice(0,12);"
                + " return JSON.stringify(codes); }");
        String raw = r == null ? "[]" : r.toString();
        java.util.List<String> codes = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(raw);
        while (m.find()) codes.add(m.group(1));
        long digits = codes.stream().filter(c -> c.matches("\\d+")).count();
        if (codes.isEmpty()) { numericCodes = true; return "no existing rows sampled - defaulting to a NUMERIC code"; }
        numericCodes = digits == codes.size();
        return "sampled " + codes.size() + " grid value(s) " + codes.subList(0, Math.min(6, codes.size()))
                + " -> generating a " + (numericCodes ? "NUMERIC" : "prefixed alphanumeric") + " code";
    }

    /**
     * Click the list <b>Add</b> button; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && (nameEl||loincEl)); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__prAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__prAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Parameter.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__prAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Fill <b>Parameter Details</b> — Parameter Code, Parameter Name, Print Name, Parameter Unit,
     * Parameter SSI Unit, LOINC Code and Conver. Factor. The name/print/unit/LOINC values travel together so the
     * row reads like a real parameter ("Haemoglobin / HGB / g/dL / g/L / 718-7") rather than five unrelated strings.
     */
    public String fillParameterDetails(int attempt) {
        int i = (int) (Math.abs(System.nanoTime() / 1000 + attempt) % PARAMS.length);
        String[] p = PARAMS[i];
        long n = Math.abs((System.nanoTime() + attempt * 7919L) % 100000);
        lastCode = numericCodes ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                                : "PR" + String.format("%05d", n);
        // Uniqueness on sibling screens here is enforced on the NAME, not the Code — qualify it on every retry.
        lastName = p[0] + (attempt == 0 ? "" : " - Method " + (attempt + 1));
        lastPrintName = p[1];
        lastUnit = p[2];
        lastSsiUnit = p[3];
        lastLoinc = p[4];
        lastConvFactor = "1";
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl,a.code), nm=set(nameEl,a.name), pn=set(printEl,a.print),"
                + "   lo=set(loincEl,a.loinc), cf=set(convEl,a.conv);"
                + " return 'Parameter Code='+cd+' | Parameter Name='+nm+' | Print Name='+pn"
                + "   +' | LOINC Code='+lo+' | Conver. Factor='+cf; }",
                java.util.Map.of("code", lastCode, "name", lastName, "print", lastPrintName,
                        "loinc", lastLoinc, "conv", lastConvFactor));
        waitForAngular(500);

        // Parameter Unit / Parameter SSI Unit are drop-downs. Aim for the unit that genuinely goes with this
        // parameter ("g/dL" for Haemoglobin); if this environment's 19-option list does not carry it, take the
        // first real option and REPORT what was actually chosen rather than the unit that was wanted.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_SELECTS
                + " if(ssiSel) ssiSel.id='__prSsi'; if(unitSel) unitSel.id='__prUnit'; }");
        String unitPicked = pickOption("#__prUnit", reQuote(lastUnit), 10000);
        String ssiPicked = pickOption("#__prSsi", reQuote(lastSsiUnit), 10000);
        page.evaluate("() => { ['__prUnit','__prSsi'].forEach(id=>{ const e=document.getElementById(id); if(e) e.removeAttribute('id'); }); }");
        closeSelect2();
        String unitNote = (unitPicked.isEmpty() ? "(not selected)" : unitPicked)
                + (unitPicked.isEmpty() || unitPicked.equalsIgnoreCase(lastUnit) ? "" : " [wanted " + lastUnit + "]");
        String ssiNote = (ssiPicked.isEmpty() ? "(not selected)" : ssiPicked)
                + (ssiPicked.isEmpty() || ssiPicked.equalsIgnoreCase(lastSsiUnit) ? "" : " [wanted " + lastSsiUnit + "]");
        if (!unitPicked.isEmpty()) lastUnit = unitPicked;
        if (!ssiPicked.isEmpty()) lastSsiUnit = ssiPicked;

        return (r == null ? "" : r.toString()) + " | Parameter Unit=" + unitNote + " | Parameter SSI Unit=" + ssiNote;
    }

    /** Escape a literal so it can be embedded in a JS regular expression. */
    private static String reQuote(String s) { return s.replaceAll("([.*+?^${}()|\\[\\]\\\\/])", "\\\\$1"); }

    /**
     * Fill the <b>Age Wise Range</b> line — the age band, the Gender select and the six limits. The limits are
     * kept COHERENT (Critical Low &lt; Alert Low &lt; Lower &lt; Upper &lt; Alert High &lt; Critical High);
     * a screen that validates the ordering rejects random numbers, and a report full of nonsense ranges is no use
     * to a reviewer either.
     */
    public String fillAgeWiseRange(int attempt) {
        String[] band = AGE_BANDS[attempt % AGE_BANDS.length];
        lastAgeRange = band[0] + " - " + band[1];
        // 8 < 11 < 12 .. 16 < 17 < 20
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("from", band[0]); args.put("to", band[1]);
        args.put("critLow", "8"); args.put("alertLow", "11"); args.put("lower", "12");
        args.put("upper", "16"); args.put("alertHigh", "17"); args.put("critHigh", "20");
        lastRange = "Lower=12 | Alert Low=11 | Critical Low=8 | Upper=16 | Alert High=17 | Critical High=20";

        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const af=set(ageFromEl,a.from), at=set(ageToEl,a.to);"
                + " const lv=set(lowerEl,a.lower), al=set(alertLowEl,a.alertLow), cl=set(critLowEl,a.critLow);"
                + " const uv=set(upperEl,a.upper), ah=set(alertHighEl,a.alertHigh), ch=set(critHighEl,a.critHigh);"
                + " return 'Age='+af+'-'+at+' | Lower Value='+lv+' | Alert Low='+al+' | Critical Low='+cl"
                + "   +' | Upper Value='+uv+' | Alert High='+ah+' | Critical High='+ch; }", args);
        waitForAngular(400);
        String res = r == null ? "" : r.toString();

        // Gender is a dropdown — a REAL selectOption so any ng-change fires.
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_SELECTS
                + " if(genderEl) genderEl.id='__prGender';"
                + " return ' [selects: '+sels.length+', gender-model='+(genderEl?(genderEl.getAttribute('ng-model')||'?'):'none')+']'; }");
        // Prefer an actual sex: this list's first real option is "Ambiguous", which is a legitimate value but a
        // poor reference-range row for a reviewer to read.
        lastGender = pickOption("#__prGender", "male|female", 10000);
        page.evaluate("() => { const e=document.getElementById('__prGender'); if(e) e.removeAttribute('id'); }");
        closeSelect2();
        return "Gender=" + (lastGender.isEmpty() ? "(not selected)" : lastGender) + " | " + res
                + (found == null ? "" : found.toString());
    }

    /**
     * Choose the first real (non "-Select-") option of a select, polling until one exists — these lists load
     * async. Uses a REAL {@code selectOption} and falls back to driving the element from JS (plus the select2
     * widget) when the select is hidden behind select2 and Playwright refuses to interact with it.
     */
    private String pickFirstRealOption(String selector, int timeoutMs) { return pickOption(selector, null, timeoutMs); }

    /**
     * Choose an option of a select, polling until a real one exists (these lists load async). When
     * {@code preferred} is given — a JS REGEX, so callers pass {@link #reQuote} for a literal — the first option
     * matching it wins; otherwise, and when nothing matches, the first real (non "-Select-") option is taken, so
     * a missing preference degrades instead of failing.
     */
    private String pickOption(String selector, String preferred, int timeoutMs) {
        if (page.locator(selector).count() == 0) return "";
        try {
            page.waitForFunction("(sel) => { const e=document.querySelector(sel); if(!e) return false;"
                    + " return [...e.options].some(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    selector, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { System.out.println("pickOption: no real option in " + selector + " within " + timeoutMs + "ms"); }

        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.sel); if(!e) return -1;"
                + " const real=o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim());"
                + " const opts=[...e.options];"
                + " if(a.pref){ const re=new RegExp(a.pref,'i'); const i=opts.findIndex(o=>real(o) && re.test((o.textContent||'').trim())); if(i>=0) return i; }"
                + " return opts.findIndex(real); }",
                java.util.Map.of("sel", selector, "pref", preferred == null ? "" : preferred));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) return "";

        try {
            page.locator(selector).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            // select2 hides the real <select> off-screen, so Playwright will not drive it — do it from JS and
            // tell the widget to re-render, otherwise the model changes but the screen still reads "-Select-".
            System.out.println("pickFirstRealOption: real selectOption failed on " + selector + " - " + e.getMessage());
            page.evaluate("(a) => { const e=document.querySelector(a.sel); if(!e) return; const A=window.angular, $=window.jQuery;"
                    + " e.selectedIndex=a.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).select2('close'); }catch(x){} } }",
                    java.util.Map.of("sel", selector, "i", i));
        }
        waitForAngular(600);
        Object txt = page.evaluate("(sel) => { const e=document.querySelector(sel); if(!e||e.selectedIndex<0) return '';"
                + " return (e.options[e.selectedIndex].textContent||'').replace(/\\s+/g,' ').trim(); }", selector);
        return txt == null ? "" : txt.toString();
    }

    /** How many range rows the detail list currently holds — the before/after proof that Add appended one. */
    public int detailRowCount() {
        // Count the ng-repeat rows, NOT "every tbody tr": the master list grid is still in the DOM behind the
        // form and would swamp the delta (that is exactly what hid the appended row on the Lab Machine screen).
        Object r = page.evaluate("() => { const q=s=>document.querySelectorAll(s).length;"
                + " let n=q('tr[ng-repeat*=\"Range\"],tr[ng-repeat*=\"Details\"],tr[data-ng-repeat*=\"Range\"],tr[data-ng-repeat*=\"Details\"]');"
                + " if(!n) n=q('tbody tr[ng-repeat],tbody tr[data-ng-repeat]');"
                + " if(!n) n=q('.ui-grid-row');"
                + " return n; }");
        try { return (int) Double.parseDouble(String.valueOf(r)); } catch (Exception e) { return -1; }
    }

    /**
     * Click the DETAIL <b>Add</b> (appends the age-wise range row) and report the row-count delta. Prefers a
     * button whose ng-click looks like an add-to-list handler; otherwise the LAST visible Add, which on these
     * master-detail forms is the one under the detail fields rather than the list Add.
     */
    public String clickAddDetail() {
        closeSelect2();
        lastRowsBefore = detailRowCount();
        int before = lastRowsBefore;
        // Watch for a validation toast raised BY the Add itself — without this a refused append reads only as
        // "NO new row" and the reason (which field the app objected to) is lost.
        page.evaluate("() => { window.__prAddToasts=[]; if(window.__prAddObs) window.__prAddObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__prAddToasts.includes(t)) window.__prAddToasts.push(t); }); };"
                + " window.__prAddObs=new MutationObserver(grab); window.__prAddObs.observe(document.body,{childList:true,subtree:true}); }");
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const adds=[...document.querySelectorAll('button,a,input[type=button]')].filter(b=>b.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(b.textContent||b.value)));"
                + " const b=adds.find(x=>/add.*(range|detail|para|row|item|age)/i.test(x.getAttribute('ng-click')||'')) || adds[adds.length-1];"
                + " if(!b) return 'no-add'; b.id='__prAddDetail'; return (b.getAttribute('ng-click')||'add'); }");
        String how = String.valueOf(r);
        if ("no-add".equals(how)) { System.out.println("Parameter.clickAddDetail: no detail Add button"); return "no detail Add button found"; }
        // REAL click — these handlers hang off ng-click and a synthetic dispatch can miss the digest.
        try { page.locator("#__prAddDetail").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("Parameter.clickAddDetail: real click failed - " + e.getMessage());
            try { page.evaluate("() => { const b=document.getElementById('__prAddDetail'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const e=document.getElementById('__prAddDetail'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        lastRowsAfter = detailRowCount();
        int after = lastRowsAfter;
        Object addToast = page.evaluate("() => { if(window.__prAddObs) window.__prAddObs.disconnect();"
                + " return (window.__prAddToasts||[]).join(' | '); }");
        String why = addToast == null || addToast.toString().isEmpty() ? "" : "  [Add said: " + addToast + "]";
        return "Add clicked {" + how + "} - range rows " + before + " -> " + after
                + (after > before ? " (row appended)" : " (NO new row)") + why;
    }

    /** Range-row count either side of the last {@link #clickAddDetail()} — Submit needs at least one row. */
    public int lastRowsBefore = -1, lastRowsAfter = -1;

    /**
     * Did the list end up with a range row for Submit to send? A retry starts with the previous attempt's row
     * still in the list, and the app declines to append an identical-looking second one — that is not a failure
     * of this step, so the check is "there IS a row", with a strict increase required only from an empty list.
     */
    public boolean hasRangeRow() { return lastRowsAfter >= 1 && (lastRowsAfter > lastRowsBefore || lastRowsBefore >= 1); }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts the Parameter Details first —
     * these forms re-render and can blank a field between fill and click. Falls back to invoking the form's
     * ng-submit handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        closeSelect2();
        page.evaluate("() => { window.__prToasts=[]; if(window.__prObs) window.__prObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__prToasts.includes(t)) window.__prToasts.push(t); }); };"
                + " window.__prObs=new MutationObserver(grab); window.__prObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url().toLowerCase();
                if (!(u.contains("parameter") || u.contains("/iud") || resp.status() >= 400)) return;
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
                // Text boxes only — the two Unit drop-downs keep their selection across the re-render and
                // re-asserting a <select> from here would need the select2 dance for no benefit.
                + " set(codeEl,a.code); set(nameEl,a.name); set(printEl,a.print); set(loincEl,a.loinc); set(convEl,a.conv);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__prSubmit'; return 'click:'+(b.getAttribute('ng-click')||norm(b.textContent||b.value)); }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "name", lastName, "print", lastPrintName,
                        "loinc", lastLoinc, "conv", lastConvFactor));
        System.out.println("Parameter submit => " + how);
        if (String.valueOf(how).startsWith("click")) {
            try { page.locator("#__prSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Parameter submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__prSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__prToasts||[]).some(a=>/parameter|master|saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("Parameter save HTTP => " + lastSaveHttp);
        // "succes" (one s) is deliberate — the sibling Lab Organism screen returns "Record added succesfully".
        Object t = page.evaluate("() => { const a=window.__prToasts||[]; return a.find(x=>/saved|added|succes/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label), the selects (including the ones
     * select2 hides) and the visible button labels with their ng-click handlers. Included in the FAIL text so a
     * miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null).slice(0,45).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type?(':'+e.type):'')+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const selects=[...document.querySelectorAll('select')].slice(0,12).map(e=>"
                + "   'select['+(e.getAttribute('ng-model')||e.id||'?')+'] opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  SELECTS: '+selects.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
