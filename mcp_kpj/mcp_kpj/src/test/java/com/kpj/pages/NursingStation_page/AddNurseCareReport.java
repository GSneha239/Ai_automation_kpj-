package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nursing Station &gt; <b>Nursing Console</b> &gt; <b>Add Nurse Care Report</b> — Page Object.
 *
 * <p>Two routes, confirmed live (devhis build v2026.10.20.257):</p>
 * <ul>
 *   <li>{@code #/NursingConsole} — the ward's patient list. <b>Add Nurse Care Report</b>
 *       ({@code fnAddNurseCareReport()}) works on the TICKED patient ({@code Patient.IsSelected}) and answers
 *       "Please Select Patient!" when none is. The menu also carries <b>OPD Nursing Console</b>
 *       ({@code #/NursingConsoleOPD}); this page uses the plain <b>Nursing Console</b> only.</li>
 *   <li>{@code #/AddNurseCareReport} — the report form: Evaluation Date and Time (pre-filled), <b>Status Tag</b>
 *       ({@code Report.statustagid}), Subjective / Objective Data, five tag sections — <b>Diagnosis</b>,
 *       <b>Outcomes</b>, <b>Interventions</b>, <b>Rationales</b>, <b>Evaluation</b> (all mandatory) —
 *       <b>Recommendation</b>, then <b>Save</b> ({@code fnSave()}).</li>
 * </ul>
 *
 * <p>The five sections share one widget: a search box ("Search &lt;x&gt; or type a new entry") with an
 * <b>Add</b> button ({@code fnAddTyped}) and a suggestion list ({@code fnPickSearch}). Typing calls
 * {@code /api/NurseCareReport/FetchItems} ({@code itemtype} 1..5). Picking a suggestion, or pressing Add with typed
 * text, adds an entry. The entry shows in one of TWO styles, set per section: <b>tags</b> (Diagnosis, Outcomes,
 * Evaluation — a removable chip in a box that reads "None" until then) or <b>checks</b> (Interventions, Rationales
 * — a ticked checkbox row with a trash icon). Reading only chips would miss the checks sections, so both are read.
 * Diagnosis has a master (ICD codes), so it suggests; the other four returned no suggestions on devhis (still none
 * on a repeat run with the same text), so there the typed name is added as a new entry. Both paths are handled for
 * every section.</p>
 */
public class AddNurseCareReport extends BasePage {

    public AddNurseCareReport(Page page) { super(page); }

    public static final String[] SECTIONS = {"Diagnosis", "Outcomes", "Interventions", "Rationales", "Evaluation"};

    public String lastMenu = "", lastRoute = "", lastStation = "", lastPatientRow = "", lastPatient = "",
            lastStatus = "", lastSave = "", lastSaveToast = "", lastEvaluationDate = "";
    /** True when the patient tick read back as selected. */
    public boolean patientTicked = false;
    /** Per section: how it was filled and what the chip box ended up with. */
    public final Map<String, String> sectionResult = new LinkedHashMap<>();
    public final Map<String, Boolean> sectionOk = new LinkedHashMap<>();

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Nursing Station &gt; Nursing Console. Only opens the Nursing Station group when its entries are hidden. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        for (int i = 0; i < 4 && !onConsole(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis)"
                    + "   .find(x=>/^#\\/nursingconsole$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__ncLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const ns=[...document.querySelectorAll('a')].filter(vis)"
                    + "   .find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)));"
                    + " if(ns){ ns.click(); return 'opened'; }"
                    + " return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__ncLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("AddNurseCareReport.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__ncLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                lastMenu = s.isEmpty() ? "Nursing Station menu not visible" : "Nursing Station group opened";
                waitForAngular(1300);
            }
        }
        if (!onConsole()) {
            try { page.evaluate("() => { window.location.hash = '#/NursingConsole'; }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"[ng-model='NursingConsole.NursingStation']\").length>0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("AddNurseCareReport.nav: Nursing Console not ready"); }
        waitForAngular(1200);
        return onConsole();
    }

    /** On {@code #/NursingConsole} exactly — not {@code #/NursingConsoleOPD}. */
    public boolean onConsole() { return page.url().toLowerCase().matches(".*#/nursingconsole/?$"); }

    public boolean onAddScreen() { return page.url().toLowerCase().contains("addnursecarereport"); }

    // ---- Nursing Console: pick a patient and open the form -----------------------------------------------------

    public int patientCount() {
        Object n = page.evaluate("() => document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\").length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Make sure the list shows patients: the pre-selected nursing station is used when it has some, otherwise each
     * station is tried in turn. Returns "station (N patients)".
     */
    public String ensurePatients() {
        String station = currentStation();
        if (patientCount() == 0) {
            int opts = intOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='NursingConsole.NursingStation']\");"
                    + " return e? [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length : 0; }"));
            for (int i = 0; i < opts && patientCount() == 0; i++) {
                try {
                    page.locator("select[ng-model='NursingConsole.NursingStation']").first().selectOption(new SelectOption().setIndex(i + 1));
                } catch (Exception e) { System.out.println("AddNurseCareReport: station select failed - " + e.getMessage()); }
                try {
                    page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\").length>0",
                            null, new Page.WaitForFunctionOptions().setTimeout(6000));
                } catch (Exception ignore) { }
                waitForAngular(600);
                station = currentStation();
            }
        }
        lastStation = station;
        return station + " (" + patientCount() + " patients)";
    }

    private String currentStation() {
        Object t = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='NursingConsole.NursingStation']\");"
                + " return e? ((e.options[e.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return t == null ? "" : t.toString();
    }

    /** Tick the first patient's checkbox (a real click) and read the tick back. */
    public String tickFirstPatient() {
        Object row = page.evaluate("() => {" + JS
                + " const c=document.querySelector(\"input[ng-model='Patient.IsSelected']\"); if(!c) return '';"
                + " c.id='__ncPatient'; const r=c.closest('tr')||c.parentElement.parentElement;"
                + " return norm(r.textContent).slice(0,120); }");
        lastPatientRow = row == null ? "" : row.toString();
        if (lastPatientRow.isEmpty()) { patientTicked = false; return "no patient row to tick"; }
        try {
            page.locator("#__ncPatient").scrollIntoViewIfNeeded();
            page.locator("#__ncPatient").click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) {
            System.out.println("AddNurseCareReport: patient click failed - " + e.getMessage());
            page.evaluate("() => { const e=document.getElementById('__ncPatient'); if(e && !e.checked) e.click(); }");
        }
        waitForAngular(600);
        patientTicked = Boolean.TRUE.equals(page.evaluate("() => { const e=document.getElementById('__ncPatient'); return !!e && e.checked; }"));
        page.evaluate("() => { const e=document.getElementById('__ncPatient'); if(e) e.removeAttribute('id'); }");
        return "ticked: " + lastPatientRow + " -> " + (patientTicked ? "tick reads back as selected" : "tick did NOT stick");
    }

    /**
     * Click <b>Add Nurse Care Report</b> ({@code fnAddNurseCareReport()}) and wait for the form. Returns "" when the
     * form opened, otherwise the toast the console answered with (e.g. "Please Select Patient!").
     */
    public String clickAddNurseCareReport() {
        page.evaluate("() => { window.__ncToasts=[]; if(window.__ncObs) window.__ncObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__ncToasts.includes(t)) window.__ncToasts.push(t); }); };"
                + " window.__ncObs=new MutationObserver(grab); window.__ncObs.observe(document.body,{childList:true,subtree:true}); }");
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnAddNurseCareReport/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ncAdd'; }");
        try { page.locator("#__ncAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AddNurseCareReport: Add click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ncAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => /addnursecarereport/i.test(location.hash) && document.querySelectorAll('.ncr-field').length>=5",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1200);
        if (onAddScreen()) return "";
        Object t = page.evaluate("() => (window.__ncToasts||[])[0] || ''");
        return t == null || t.toString().isEmpty() ? "form did not open and no toast appeared" : t.toString();
    }

    // ---- the form: header, status tag, free-text fields --------------------------------------------------------

    /** MRN / name / ward-room-bed read from the patient banner at the top of the form. */
    public String patientBanner() {
        Object r = page.evaluate("() => { const t=(document.body.innerText||'');"
                + " const g=re=>{ const m=t.match(re); return m? m[1].replace(/\\s+/g,' ').trim() : ''; };"
                + " return 'MRN '+g(/MRN[\\s:]+(\\d+)/)+' | '+g(/Patient Name[\\s:]+([^\\n\\t]+)/)+' | Ward/Room/Bed '+g(/Ward \\/ Room \\/ Bed[\\s:]+([^\\n\\t]+)/); }");
        lastPatient = r == null ? "" : r.toString();
        Object d = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='Report.evaluationdt']\"); return e? e.value : ''; }");
        lastEvaluationDate = d == null ? "" : d.toString();
        return lastPatient;
    }

    /**
     * Select a <b>Status Tag</b> with a real select (so Angular's change handling runs). Prefers an "In Progress" /
     * "Under Observation" tag — devhis also carries throw-away tags ("TEST", "submit") that are skipped — else the
     * first real one.
     */
    public String selectStatusTag() {
        String sel = "select[ng-model='Report.statustagid']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { lastStatus = ""; return "(no status tags loaded)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); const o=[...e.options];"
                + " const real=o.map((x,i)=>({i,t:(x.textContent||'').trim(),v:x.value})).filter(x=>x.v && !/^-*\\s*select/i.test(x.t));"
                + " const pref=real.find(x=>/in progress|under observation/i.test(x.t)) || real.find(x=>!/^(test|submit)$/i.test(x.t)) || real[0];"
                + " return pref? pref.i : -1; }", sel);
        int i = intOf(idx);
        if (i < 0) { lastStatus = ""; return "(no selectable status tag)"; }
        try { page.locator(sel).first().scrollIntoViewIfNeeded(); page.locator(sel).first().selectOption(new SelectOption().setIndex(i)); }
        catch (Exception e) { System.out.println("AddNurseCareReport: status select failed - " + e.getMessage()); }
        waitForAngular(500);
        Object t = page.evaluate("(s) => { const e=document.querySelector(s); return e? ((e.options[e.selectedIndex]||{}).textContent||'').trim() : ''; }", sel);
        lastStatus = t == null ? "" : t.toString();
        return lastStatus;
    }

    /** The status tag is bound in the model (not just shown). */
    public boolean statusTagBound() {
        return Boolean.TRUE.equals(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Report.statustagid']\");"
                + " if(!e) return false; try{ const s=angular.element(e).scope(); const v=s.Report && s.Report.statustagid; return !!v; }catch(x){ return false; } }"));
    }

    public boolean fillSubjective(String text) { return fillTextarea("Report.subjectivedata", text); }
    public boolean fillRecommendation(String text) { return fillTextarea("Report.recommendation", text); }

    private boolean fillTextarea(String ng, String text) {
        String sel = "textarea[ng-model='" + ng + "']";
        try {
            Locator l = page.locator(sel).first();
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(text);
        } catch (Exception e) { System.out.println("AddNurseCareReport: " + ng + " fill failed - " + e.getMessage()); return false; }
        waitForAngular(400);
        // read it back from the MODEL, not just the box
        Object m = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return ''; try{ const sc=angular.element(e).scope();"
                + " const v=sc.Report && sc.Report[a.k]; return v==null?'':String(v); }catch(x){ return e.value; } }",
                Map.of("s", sel, "k", ng.substring(ng.indexOf('.') + 1)));
        return m != null && m.toString().trim().equals(text.trim());
    }

    // ---- the five tag sections ---------------------------------------------------------------------------------

    private Locator field(String section) {
        return page.locator(".ncr-field:has(input[placeholder^='Search " + section + "'])").first();
    }

    /**
     * What a section currently holds. The widget renders in two styles — {@code tags} (Diagnosis, Outcomes,
     * Evaluation: removable chips) and {@code checks} (Interventions, Rationales: a ticked checkbox row per entry) —
     * so both are read. A checkbox row that is not ticked is reported with an "[unticked]" suffix, because an
     * unticked entry is not part of the report.
     */
    @SuppressWarnings("unchecked")
    public List<String> chips(String section) {
        Object r = page.evaluate("(s) => {" + JS
                + " const inp=[...document.querySelectorAll('input')].find(e=>(e.placeholder||'').startsWith('Search '+s));"
                + " const f=inp && inp.closest('.ncr-field'); if(!f) return [];"
                + " const out=[];"
                + " f.querySelectorAll('.ncr-chip').forEach(c=>out.push(norm(c.textContent).replace(/×$/,'').trim()));"
                + " f.querySelectorAll('.ncr-check').forEach(c=>{ const cb=c.querySelector('input[type=checkbox]'); const l=c.querySelector('label');"
                + "   out.push(norm(l?l.textContent:'') + (cb && !cb.checked ? ' [unticked]' : '')); });"
                + " return out; }", section);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Fill one tag section the way a nurse would: type {@code term} in its search box; if suggestions appear, pick
     * one (the exact match if there is one, else the first that matches {@code preferRe}, else the first); if none
     * appear, the typed name stays in the box and <b>Add</b> adds it as a new entry. Returns a description and
     * records the outcome in {@link #sectionResult} / {@link #sectionOk}.
     */
    public String fillSection(String section, String term, String preferRe) {
        Locator f = field(section);
        Locator input = f.locator("input[placeholder^='Search " + section + "']").first();
        int before = chips(section).size();
        try { f.scrollIntoViewIfNeeded(); } catch (Exception ignore) { }
        try {
            input.click(new Locator.ClickOptions().setTimeout(5000));
            // fill() fires one input event -> one FetchItems call; wait for its answer before reading the list
            page.waitForResponse(r -> r.url().contains("NurseCareReport/FetchItems"),
                    new Page.WaitForResponseOptions().setTimeout(8000), () -> input.fill(term));
        } catch (Exception e) {
            System.out.println("AddNurseCareReport: search '" + section + "' - " + e.getMessage());
            try { input.fill(term); } catch (Exception ignore) { }
        }
        waitForAngular(700);

        int suggested = f.locator("li[ng-click^='fnPickSearch']").count();
        String how;
        String picked = "";
        if (suggested > 0) {
            Object idx = page.evaluate("(a) => {" + JS
                    + " const inp=[...document.querySelectorAll('input')].find(e=>(e.placeholder||'').startsWith('Search '+a.s));"
                    + " const f=inp.closest('.ncr-field');"
                    + " const lis=[...f.querySelectorAll('li')].filter(vis).filter(l=>/fnPickSearch/.test(l.getAttribute('ng-click')||''));"
                    + " let i=lis.findIndex(l=>norm(l.textContent).toLowerCase()===a.t.toLowerCase());"
                    + " if(i<0 && a.re) i=lis.findIndex(l=>new RegExp(a.re,'i').test(norm(l.textContent)));"
                    + " if(i<0) i=0;"
                    + " lis[i].id='__ncPick'; return norm(lis[i].textContent); }",
                    Map.of("s", section, "t", term, "re", preferRe == null ? "" : preferRe));
            picked = idx == null ? "" : idx.toString();
            try { page.locator("#__ncPick").click(new Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { page.evaluate("() => { const e=document.getElementById('__ncPick'); if(e) e.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__ncPick'); if(e) e.removeAttribute('id'); }");
            how = suggested + " suggestion(s) appeared for \"" + term + "\"; picked \"" + picked + "\"";
        } else {
            try { f.locator(".ncr-addbtn").first().click(new Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AddNurseCareReport: Add click failed (" + section + ") - " + e.getMessage()); }
            how = "no suggestion appeared for \"" + term + "\"; typed it as a new entry and clicked Add";
        }
        waitForAngular(800);

        List<String> after = chips(section);
        String expect = suggested > 0 ? picked : term;
        boolean added = after.size() > before && after.stream().anyMatch(c -> c.equalsIgnoreCase(expect.trim()));
        String result = how + " -> " + section + " now holds: " + (after.isEmpty() ? "(none)" : after);
        sectionResult.put(section, result);
        sectionOk.put(section, added);
        System.out.println("AddNurseCareReport." + section + ": " + result);
        try { f.scrollIntoViewIfNeeded(); } catch (Exception ignore) { }
        return result;
    }

    // ---- Save --------------------------------------------------------------------------------------------------

    /**
     * Click <b>Save</b> ({@code fnSave()}), answer a confirmation dialog if one appears, and return the toast.
     * Toasts are harvested with a MutationObserver (they fade), after clearing stale ones so an old message is never
     * read as this save's answer.
     */
    public String clickSaveAndGetToast() {
        page.evaluate("() => { window.__ncToasts=[]; if(window.__ncObs) window.__ncObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ncToasts.includes(t)) window.__ncToasts.push(t); }); };"
                + " window.__ncObs=new MutationObserver(grab); window.__ncObs.observe(document.body,{childList:true,subtree:true}); }");
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnSave\\(/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__ncSave'; }");
        try {
            page.locator("#__ncSave").scrollIntoViewIfNeeded();
            page.locator("#__ncSave").click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("AddNurseCareReport: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ncSave'); if(e) e.removeAttribute('id'); }");

        String confirm = "";
        for (int i = 0; i < 30; i++) {
            Object ans = page.evaluate("() => {" + JS
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.bootbox,.sweet-alert')].filter(vis)"
                    + "   .find(m=>/save|confirm|sure|proceed/i.test(norm(m.textContent)));"
                    + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                    + "     .find(b=>/^\\s*(yes|ok|save|confirm)\\s*$/i.test(norm(b.textContent)));"
                    + "   if(y){ const t=norm(box.textContent).slice(0,90); y.click(); return t; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); window.__ncToasts=window.__ncToasts||[];"
                    + "   if(t && !window.__ncToasts.includes(t)) window.__ncToasts.push(t); });"
                    + " return ''; }");
            if (ans != null && !ans.toString().isEmpty()) confirm = ans.toString();
            boolean got = Boolean.TRUE.equals(page.evaluate(
                    "() => (window.__ncToasts||[]).some(a=>/succe|saved|added|created|updated|please|error|fail|invalid|required|mandatory/i.test(a))"));
            if (got) break;
            page.waitForTimeout(500);
        }
        lastSave = "clicked Save [fnSave]" + (confirm.isEmpty() ? " (no confirmation dialog appeared)"
                : ", then confirmed the dialog (\"" + confirm + "\")");
        waitForAngular(800);
        Object r = page.evaluate("() => { const a=window.__ncToasts||[];"
                + " return a.find(x=>/succe|saved|added|created|updated/i.test(x))"
                + "   || a.find(x=>/please|error|fail|invalid|required|mandatory/i.test(x)) || a[0] || ''; }");
        lastSaveToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        System.out.println("AddNurseCareReport: save -> " + lastSave + "; toast: \"" + lastSaveToast + "\"");
        return lastSaveToast;
    }

    /** The save answered with a success message (and not a validation/error one). */
    public boolean saveSucceeded() {
        String t = lastSaveToast == null ? "" : lastSaveToast.toLowerCase();
        return t.matches(".*(succe|saved|added|created|updated).*")
                && !t.matches(".*(please|error|fail|invalid|not\\s*saved|mandatory|required).*");
    }

    private static int intOf(Object o) { return o instanceof Number ? ((Number) o).intValue() : -1; }
}
