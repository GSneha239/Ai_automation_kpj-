package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nursing Station &gt; <b>DeathRegister</b> — Page Object ({@code #/DeathRegister}).
 *
 * <p>One page, six panels, confirmed live (devhis build v2026.10.20.257): <b>Deceased Form</b> (MRN + Search, Episode,
 * Death Type), <b>Information of Doctor's who Issuing Burial Permits JPN.LM02 / Death Verification</b>,
 * <b>Primary Doctor's Information</b>, <b>The Deceased's Information</b>, <b>Information of The Informant</b> and
 * <b>Body Claimed By</b>. Every field is bound as {@code DeathRegister.<name>}, so fields are found by model, not by
 * position.</p>
 *
 * <p>How the screen behaves:</p>
 * <ul>
 *   <li><b>Search</b> ({@code SearchPatientByMRNo()}) calls {@code GetPatientIPD}, then {@code GetPatientOPD}, then
 *       {@code GETLIST}. The episode is filled from the visit. <b>If the MRN already has a death register record, the
 *       whole record is loaded into the form</b> ({@code deathregisterid > 0}) and Submit would update it — so a
 *       search is reported as "new" or "existing".</li>
 *   <li>The doctor boxes are plain selects; picking a doctor <b>auto-fills</b> the IC No. / Passport and Specialty
 *       ({@code fnSetDepartmentByDoctor}). Not every doctor has an IC — Submit demands one.</li>
 *   <li>Address selects CASCADE: <b>Country</b> ({@code fnSetInformantState}) loads the States, <b>State</b>
 *       ({@code fnSetInformantCity}) loads the Cities. State and City are empty until a Country is chosen, so the
 *       Country is picked first — and the Body Claimed By Country is mandatory too.</li>
 *   <li><b>Submit</b> ({@code IUDSaveDeathRegister()}) validates in the browser and answers with ONE toast that lists
 *       every missing field ("Primary Doctor is required…"). The five date/time fields (Burial Permit Date, Death
 *       Date/Time, Informant Date, Mortuary Registration and Released Date) are mandatory and default to today on a
 *       freshly opened screen. <b>Close</b> ({@code fnClearDeathRegister()}) clears the form — dates included — and
 *       stays on the screen.</li>
 * </ul>
 */
public class DeathRegister extends BasePage {

    public DeathRegister(Page page) { super(page); }

    /** One person block (Informant or Body Claimed By). */
    public static class Person {
        public String title, name, idType, idNo, street, postcode, country, state, city, phone, occupationRe, relationship;
    }

    public String lastRoute = "", lastEpisode = "", lastPatient = "", lastSubmit = "", lastToast = "";
    /** The searched MRN already had a saved death register record (it is loaded into the form). */
    public boolean existingRecord = false;
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Nursing Station &gt; DeathRegister. Only opens the Nursing Station group when its entries are hidden. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        for (int i = 0; i < 4 && !onScreen(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis)"
                    + "   .find(x=>/^#\\/deathregister$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__drLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const ns=[...document.querySelectorAll('a')].filter(vis)"
                    + "   .find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)));"
                    + " if(ns){ ns.click(); return 'opened'; }"
                    + " return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__drLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DeathRegister.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__drLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                waitForAngular(1300);
            }
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '#/DeathRegister'; }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        try {
            page.waitForFunction("() => document.querySelectorAll(\"[ng-model='DeathRegister.MRNo']\").length>0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DeathRegister.nav: form not ready"); }
        waitForAngular(1500);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/deathregister/?$"); }

    /** Scroll a panel (by its heading text) to the top of the viewport, so a screenshot shows it. */
    public void scrollTo(String headingRe) {
        page.evaluate("(re) => {" + JS
                + " const h=[...document.querySelectorAll('.panel-heading')].find(x=>new RegExp(re,'i').test(norm(x.textContent)));"
                + " if(h){ h.scrollIntoView({block:'start'}); window.scrollBy(0,-70); } }", headingRe);
        page.waitForTimeout(250);
    }

    // ---- low-level field helpers (all by ng-model, verified from the model) -------------------------------------

    private String sel(String name) { return "[ng-model='DeathRegister." + name + "']"; }

    /**
     * The model value of a field, read from the scope (what Submit would send), as a string. Not every model property
     * is bound to a box ({@code deathregisterid} and {@code PatientFullDtl} are not), so the value is read from the
     * shared {@code DeathRegister} object through the MRN box's scope when the field has no box of its own.
     */
    public String model(String name) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='DeathRegister.\"+n+\"']\")"
                + " || document.querySelector(\"[ng-model='DeathRegister.MRNo']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval('DeathRegister.'+n); return v==null?'':String(v); }catch(x){ return ''; } }", name);
        return v == null ? "" : v.toString().trim();
    }

    /** Type into a text box / textarea (a real fill) and read the model back. */
    private boolean fillText(String name, String value) {
        try {
            Locator l = page.locator(sel(name)).first();
            l.scrollIntoViewIfNeeded();
            l.click(new Locator.ClickOptions().setTimeout(5000));
            l.fill(value);
        } catch (Exception e) {
            System.out.println("DeathRegister: fill " + name + " failed - " + e.getMessage());
            problems.add(name + " could not be typed");
            return false;
        }
        waitForAngular(250);
        boolean held = model(name).equals(value.trim());
        if (!held) problems.add(name + " reads \"" + model(name) + "\" instead of \"" + value + "\"");
        return held;
    }

    /**
     * Choose an option in a select with a real selection (so Angular's ng-change runs). {@code want} matches the
     * option text exactly (case-insensitive); else {@code fallbackRe} matches; else, when {@code anyIfMissing}, the
     * first real option. Waits for the options to load. Returns the option text chosen, "" if none.
     */
    private String choose(String name, String want, String fallbackRe, boolean anyIfMissing) {
        String s = sel(name);
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }",
                    s, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add(name + " has no options to choose from"); return ""; }
        Object idx = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map((x,i)=>({i,t:norm(x.textContent),v:x.value}))"
                + "   .filter(x=>x.v && !/^-*\\s*(select|title)\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.re) p=o.find(x=>new RegExp(a.re,'i').test(x.t));"
                + " if(!p && a.any) p=o[0];"
                + " return p? p.i : -1; }",
                Map.of("s", s, "w", want == null ? "" : want, "re", fallbackRe == null ? "" : fallbackRe, "any", anyIfMissing));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) { problems.add(name + " has no option \"" + want + "\""); return ""; }
        try {
            page.locator(s).first().scrollIntoViewIfNeeded();
            page.locator(s).first().selectOption(new SelectOption().setIndex(i));
        } catch (Exception e) { System.out.println("DeathRegister: select " + name + " failed - " + e.getMessage()); }
        waitForAngular(700);
        Object t = page.evaluate("(s) => { const e=document.querySelector(s); return e? ((e.options[e.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : ''; }", s);
        String chosen = t == null ? "" : t.toString();
        if (model(name).isEmpty()) problems.add(name + " is not held by the model");
        return chosen;
    }

    private void waitOptions(String name, int timeoutMs) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }",
                    sel(name), new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
    }

    // ---- Deceased Form -------------------------------------------------------------------------------------------

    /**
     * What one MRN search tells us, taken from the SERVER'S OWN ANSWERS rather than from the form (a form that still
     * holds an earlier patient's record would give a stale reading — that mistake once made the test submit over an
     * existing record).
     */
    public static class Screen {
        public final String mrn;
        /** GetPatientIPD / GetPatientOPD returned a visit (an episode). */
        public boolean visit;
        /** The existing-record lookup (GETLIST) answered at all. */
        public boolean answered;
        /** GETLIST returned a death register record for the patient. */
        public boolean hasRecord;
        public String reason = "";
        Screen(String mrn) { this.mrn = mrn; }
        /** Usable for a NEW death register: it has a visit, the record lookup answered, and there is no record. */
        public boolean eligible() { return visit && answered && !hasRecord; }
    }

    public Screen lastScreen;

    private static boolean hasRows(String body) { return body != null && body.trim().length() > 2; }

    private static String bodyOf(com.microsoft.playwright.Response r) {
        try { return r == null ? "" : r.text(); } catch (Exception e) { return ""; }
    }

    /**
     * Enter an MRN, click <b>Search</b> and judge the answer. The lookup is a chain — {@code GetPatientIPD} →
     * {@code GetPatientOPD} → {@code GETLIST} (an existing death register record) → patient info — so the click
     * is watched until GETLIST has answered, or until both visit lookups came back empty (GETLIST is then never
     * asked). An MRN is eligible only when it has a visit and GETLIST answered with no record.
     */
    public Screen screenMrn(String mrn) {
        Screen s = new Screen(mrn);
        final java.util.Map<String, com.microsoft.playwright.Response> got = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> h = r -> {
            try {
                if (!r.url().contains("DeathRegister/Fetch")) return;
                String post = String.valueOf(r.request().postData());
                for (String f : new String[]{"GetPatientIPD", "GetPatientOPD", "GETLIST"})
                    if (post.contains("\"" + f + "\"")) got.put(f, r);
            } catch (Exception ignore) { }
        };
        page.onResponse(h);
        try {
            try {
                Locator in = page.locator(sel("MRNo")).first();
                in.scrollIntoViewIfNeeded();
                in.click(new Locator.ClickOptions().setTimeout(5000));
                in.fill(mrn);
            } catch (Exception e) { s.reason = "the MRN box could not be typed in"; return s; }
            page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.id='__drSearch'; }");
            try { page.locator("#__drSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { s.reason = "Search could not be clicked"; return s; }
            finally { page.evaluate("() => { const e=document.getElementById('__drSearch'); if(e) e.removeAttribute('id'); }"); }

            long end = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < end) {
                if (got.containsKey("GETLIST")) break;
                if (got.containsKey("GetPatientIPD") && got.containsKey("GetPatientOPD")
                        && !hasRows(bodyOf(got.get("GetPatientIPD"))) && !hasRows(bodyOf(got.get("GetPatientOPD")))) {
                    page.waitForTimeout(500);       // no visit at all: GETLIST is never asked
                    break;
                }
                page.waitForTimeout(200);
            }
        } finally {
            page.offResponse(h);
        }
        s.visit = hasRows(bodyOf(got.get("GetPatientIPD"))) || hasRows(bodyOf(got.get("GetPatientOPD")));
        s.answered = got.containsKey("GETLIST");
        s.hasRecord = java.util.regex.Pattern.compile("\"deathregisterid\"\\s*:\\s*([1-9][0-9]*)")
                .matcher(bodyOf(got.get("GETLIST"))).find();
        s.reason = !s.visit ? "no visit / episode"
                : !s.answered ? "the existing-record lookup did not answer"
                : s.hasRecord ? "already has a death register record"
                : "eligible (visit found, no death register record)";
        return s;
    }

    /**
     * Enter the MRN, click <b>Search</b> and let the form fill. Returns a description; {@link #lastEpisode},
     * {@link #lastPatient}, {@link #existingRecord} and {@link #lastScreen} are set. Use it on a BLANK screen.
     */
    public String searchMrn(String mrn) {
        problems.clear();
        existingRecord = false; lastEpisode = ""; lastPatient = "";
        Screen s = screenMrn(mrn);
        lastScreen = s;
        if (s.visit) {
            try {
                page.waitForFunction("() => { const e=document.querySelector(\"[ng-model='DeathRegister.episodeno']\"); return !!e && !!(e.value||'').trim(); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { }
            try {   // the patient banner is the last thing the lookup fills
                page.waitForFunction("() => { const e=document.querySelector(\"[ng-model='DeathRegister.episodeno']\"); if(!e) return false;"
                        + " try{ const v=angular.element(e).scope().DeathRegister.PatientFullDtl; return !!v; }catch(x){ return false; } }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
            } catch (Exception ignore) { }
            waitForAngular(1200);   // let a loaded record settle into the form
        }
        lastEpisode = model("episodeno");
        lastPatient = model("PatientFullDtl").replaceAll("\\s+", " ");
        existingRecord = s.hasRecord || intOf(model("deathregisterid")) > 0;
        if (!s.visit) return "MRN " + mrn + ": " + s.reason;
        return "Episode " + lastEpisode + "; " + lastPatient + " — "
                + (existingRecord ? "this MRN ALREADY HAS a death register record, loaded into the form"
                                  : s.answered ? "no death register record yet, the form is blank"
                                               : "the existing-record lookup did not answer");
    }

    // ---- finding patients through Patient Search --------------------------------------------------------------------

    private List<String> popupMrns() {
        Object r = page.evaluate("() => {" + JS
                + " const ths=[...document.querySelectorAll('.modal table thead th')];"
                + " const idx=ths.findIndex(h=>/^\\s*mrn\\b/i.test(norm(h.textContent)));"
                + " const ok=t=>/^[A-Za-z0-9]{4,}$/.test(t);"
                + " return [...document.querySelectorAll('.modal table tbody tr')].filter(vis).map(r=>{"
                + "   const td=r.querySelectorAll('td'); let t=(idx>=0&&td[idx])? norm(td[idx].textContent) : '';"
                + "   if(!ok(t)) t=norm(r.textContent).split(' ')[0]; return t; }).filter(ok); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private boolean clickPopupButton(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__drPop'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#__drPop").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("DeathRegister: popup click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__drPop'); if(e) e.removeAttribute('id'); }"); }
    }

    /**
     * Find patients to try: open the <b>Patient Search</b> popup beside the MRN box, search with its default filters,
     * and collect the MRNs it lists (page by page with {@code >>}), without repeats. The popup is closed again. No MRN is
     * assumed anywhere — whether a patient is usable is decided later, by {@link #screenMrn}.
     */
    public List<String> findCandidateMrns(int target, int maxPages) {
        List<String> out = new ArrayList<>();
        if (!clickPopupButton("openPopupScreen", null)) { System.out.println("DeathRegister: Patient Search button not found"); return out; }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/SearchPatient\\(0\\)/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("DeathRegister: Patient Search popup did not open"); return out; }
        try {
            page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> clickPopupButton("SearchPatient\\(0\\)", "^\\s*search\\s*$"));
        } catch (Exception e) { System.out.println("DeathRegister: popup search gave no answer - " + e.getMessage()); }
        waitForAngular(1200);
        for (int p = 0; p < maxPages; p++) {
            int before = out.size();
            for (String m : popupMrns()) if (!out.contains(m)) out.add(m);
            if (out.size() >= target || (p > 0 && out.size() == before)) break;
            if (p == maxPages - 1) break;
            try {
                page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(15000),
                        () -> clickPopupButton("NextBtn", null));
            } catch (Exception e) { break; }
            waitForAngular(900);
        }
        clickPopupButton("CloseModel", "^\\s*close\\s*$");
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) { }
        waitForAngular(500);
        return out;
    }

    /**
     * Start over with a fresh screen: open another visible menu entry, then DeathRegister again. This is how the
     * screen is blanked between patients rather than with the <b>Close</b> button ({@code fnClearDeathRegister()}),
     * because Close also wipes the DEFAULT dates (Burial Permit Date, Death Date/Time, Informant Date, Mortuary
     * Registration / Released Date) — all mandatory — whereas a freshly opened screen fills them with today. A search
     * LOADS an existing record into the form, so the next patient must not be searched on a screen that still holds it.
     * Returns true only when the screen came back blank (no MRN, no episode, no loaded record).
     */
    public boolean reopenScreen() {
        Object other = page.evaluate("() => {" + JS
                + " const links=[...document.querySelectorAll('a[href^=\"#/\"]')].filter(vis)"
                + "   .filter(x=>!/deathregister/i.test(x.getAttribute('href')));"
                + " const pick=links.find(x=>/NursingStationDefinationList/i.test(x.getAttribute('href'))) || links[0];"
                + " if(!pick) return '';"
                + " pick.id='__drOther'; return pick.getAttribute('href'); }");
        if (other == null || other.toString().isEmpty()) {
            System.out.println("DeathRegister.reopen: no other menu entry is visible");
            return false;
        }
        try { page.locator("#__drOther").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DeathRegister.reopen: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__drOther'); if(e) e.removeAttribute('id'); }");
        try {   // really leave this screen before coming back
            page.waitForFunction("() => !/#\\/deathregister\\/?$/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        waitForAngular(2000);
        boolean back = navigateViaMenu(null);
        boolean blank = model("MRNo").isEmpty() && model("episodeno").isEmpty() && intOf(model("deathregisterid")) <= 0;
        if (back && !blank) System.out.println("DeathRegister.reopen: the screen came back WITH the previous form data");
        return back && blank;
    }

    // ---- doctors -------------------------------------------------------------------------------------------------

    /**
     * Burial-permit doctor: select {@code want}; the IC No. / Passport and Specialty auto-fill. A doctor with no IC
     * is not accepted — the next doctors are tried until one fills it. Returns "Name — IC … — Specialty …".
     */
    public String selectBurialDoctor(String want) {
        problems.clear();
        String chosen = choose("burialdoctorid", want, null, false);
        waitForAngular(1200);
        if (!chosen.isEmpty() && model("burialdoctoricpassport").isEmpty()) {
            String first = chosen;
            int n = optionCount("burialdoctorid");
            for (int i = 1; i <= n && model("burialdoctoricpassport").isEmpty() && i <= 60; i++) {
                selectIndex("burialdoctorid", i);
                waitForAngular(900);
            }
            chosen = selectedText("burialdoctorid");
            problems.removeIf(p -> p.contains("burialdoctorid"));
            if (!model("burialdoctoricpassport").isEmpty()) problems.add("\"" + first + "\" has no IC No. / Passport, so \"" + chosen + "\" was used instead");
        }
        if (model("burialdoctoricpassport").isEmpty()) problems.add("no doctor with an IC No. / Passport was found");
        return chosen + " — IC No. / Passport " + model("burialdoctoricpassport") + " (auto-filled) — Specialty " + model("doctorspeciality");
    }

    /** Primary doctor: select {@code want} (else the first doctor that auto-fills a specialty). */
    public String selectPrimaryDoctor(String want) {
        problems.clear();
        String chosen = choose("primarydoctorid", want, null, true);
        waitForAngular(1200);
        if (model("primarydoctorspeciality").isEmpty()) {
            int n = optionCount("primarydoctorid");
            for (int i = 1; i <= n && model("primarydoctorspeciality").isEmpty() && i <= 60; i++) {
                selectIndex("primarydoctorid", i);
                waitForAngular(900);
            }
            chosen = selectedText("primarydoctorid");
        }
        if (model("primarydoctorspeciality").isEmpty()) problems.add("the primary doctor has no specialty");
        return chosen + " — Specialty " + model("primarydoctorspeciality") + " (auto-filled)";
    }

    private int optionCount(String name) {
        Object n = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.options.length-1 : 0; }", sel(name));
        return intOf(n);
    }

    private void selectIndex(String name, int i) {
        try { page.locator(sel(name)).first().selectOption(new SelectOption().setIndex(i)); }
        catch (Exception e) { System.out.println("DeathRegister: select " + name + " #" + i + " failed - " + e.getMessage()); }
    }

    private String selectedText(String name) {
        Object t = page.evaluate("(s) => { const e=document.querySelector(s); return e? ((e.options[e.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : ''; }", sel(name));
        return t == null ? "" : t.toString();
    }

    // ---- The Deceased's Information -------------------------------------------------------------------------------

    public String fillCauseOfDeath(String text) {
        problems.clear();
        fillText("causeofdeath", text);
        return "Cause of Death \"" + model("causeofdeath") + "\"";
    }

    public String fillSerialNumber(String text) {
        problems.clear();
        fillText("serialnumberjpnlm02", text);
        return "Serial Number JPN.LM02 \"" + model("serialnumberjpnlm02") + "\"";
    }

    // ---- Informant / Body Claimed By (same fields, different model names) -----------------------------------------

    private static String key(String p, String field) {
        if ("claim".equals(p) && "name".equals(field)) return "claimedbyname";
        return p + field;
    }

    /** Title and Name. */
    public String fillTitleName(String p, Person d) {
        problems.clear();
        String title = choose(key(p, "prefixid"), d.title, null, true);
        fillText(key(p, "name"), d.name);
        return "Title \"" + title + "\", Name \"" + model(key(p, "name")) + "\"";
    }

    /** IC type and IC No. */
    public String fillIdentity(String p, Person d) {
        problems.clear();
        String type = choose(key(p, "idtypeid"), d.idType, "ic", true);
        fillText(key(p, "idno"), d.idNo);
        return "ID type \"" + type + "\", ID No. \"" + model(key(p, "idno")) + "\"";
    }

    /**
     * Address Line 1 and Postcode, then Country → State → City. The Country goes FIRST because the State list is empty
     * until a Country is chosen and the City list until a State is.
     */
    public String fillAddress(String p, Person d) {
        problems.clear();
        fillText(key(p, "street1"), d.street);
        fillText(key(p, "postalcode"), d.postcode);
        String country = choose(key(p, "countryid"), d.country, null, true);
        waitOptions(key(p, "stateid"), 10000);
        String state = choose(key(p, "stateid"), d.state, null, true);
        waitOptions(key(p, "cityid"), 10000);
        String city = choose(key(p, "cityid"), d.city, null, true);
        return "Address \"" + model(key(p, "street1")) + "\", Postcode \"" + model(key(p, "postalcode")) + "\", Country \"" + country
                + "\", State \"" + state + "\", City/District \"" + city + "\"";
    }

    /** Phone number (the country code is left at its default), Occupation and Relationship with Deceased. */
    public String fillContact(String p, Person d) {
        problems.clear();
        String code = selectedText(key(p, "phonecode"));
        fillText(key(p, "phoneno"), d.phone);
        String occ = choose(key(p, "occupationid"), null, d.occupationRe, true);
        String rel = choose(key(p, "relationshipid"), d.relationship, null, true);
        return "Phone " + code + " " + model(key(p, "phoneno")) + ", Occupation \"" + occ + "\", Relationship with Deceased \"" + rel + "\"";
    }

    // ---- Mortuary remark, Submit ------------------------------------------------------------------------------------

    public String fillMortuaryRemark(String text) {
        problems.clear();
        fillText("mortuaryremark", text);
        return "Mortuary Remark \"" + model("mortuaryremark") + "\"";
    }

    /**
     * Click <b>Submit</b> ({@code IUDSaveDeathRegister()}), answer a confirmation dialog if one appears, and return the
     * toast. Toasts are harvested with a MutationObserver (they fade), after clearing stale ones.
     */
    public String clickSubmitAndGetToast() {
        page.evaluate("() => { window.__drToasts=[]; if(window.__drObs) window.__drObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__drToasts.includes(t)) window.__drToasts.push(t); }); };"
                + " window.__drObs=new MutationObserver(grab); window.__drObs.observe(document.body,{childList:true,subtree:true}); }");
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/IUDSaveDeathRegister/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__drSubmit'; }");
        try {
            page.locator("#__drSubmit").scrollIntoViewIfNeeded();
            page.locator("#__drSubmit").click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("DeathRegister: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__drSubmit'); if(e) e.removeAttribute('id'); }");

        String confirm = "";
        for (int i = 0; i < 30; i++) {
            Object ans = page.evaluate("() => {" + JS
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.bootbox,.sweet-alert')].filter(vis)"
                    + "   .find(m=>/save|submit|confirm|sure|proceed/i.test(norm(m.textContent)));"
                    + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                    + "     .find(b=>/^\\s*(yes|ok|save|submit|confirm)\\s*$/i.test(norm(b.textContent)));"
                    + "   if(y){ const t=norm(box.textContent).slice(0,90); y.click(); return t; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); window.__drToasts=window.__drToasts||[];"
                    + "   if(t && !window.__drToasts.includes(t)) window.__drToasts.push(t); });"
                    + " return ''; }");
            if (ans != null && !ans.toString().isEmpty()) confirm = ans.toString();
            boolean got = Boolean.TRUE.equals(page.evaluate(
                    "() => (window.__drToasts||[]).some(a=>/succe|saved|submitted|registered|created|updated|required|please|error|fail|invalid|already/i.test(a))"));
            if (got) break;
            page.waitForTimeout(500);
        }
        lastSubmit = "clicked Submit [IUDSaveDeathRegister]" + (confirm.isEmpty() ? " (no confirmation dialog appeared)"
                : ", then confirmed the dialog (\"" + confirm + "\")");
        waitForAngular(800);
        Object r = page.evaluate("() => { const a=window.__drToasts||[];"
                + " return a.find(x=>/succe|saved|submitted|registered|created|updated/i.test(x))"
                + "   || a.find(x=>/required|please|error|fail|invalid|already/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        System.out.println("DeathRegister: submit -> " + lastSubmit + "; toast: \"" + lastToast + "\"");
        return lastToast;
    }

    /** The submit answered with a success message (and not a validation/error one). */
    public boolean submitSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|saved|submitted|registered|created|updated).*")
                && !t.matches(".*(required|please|error|fail|invalid|not\\s*saved|already).*");
    }

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return -1; }
    }
}
