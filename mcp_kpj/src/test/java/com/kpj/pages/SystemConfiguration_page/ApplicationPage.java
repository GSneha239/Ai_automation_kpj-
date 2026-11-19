package com.kpj.pages.SystemConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * System Access Configuration &gt; <b>Application</b> ({@code #/ApplicationConfiguration}) - Page Object.
 *
 * <p>Built from a manual walk-through of devhis on 2026-11-19 (build v2026.11.18.491):</p>
 * <ul>
 *   <li>One AngularJS form, controller {@code ApplicationConfigurationController}. View
 *       {@code GET /Billing/Load/ApplicationConfiguration}; data loaded by four POSTs to
 *       {@code /api/ApplicationConfiguration/Fetch...} and {@code /GetPhoto}.</li>
 *   <li>Top of form: <b>Location*</b> (select2, ng-model {@code conffapp1.locationid1}, pre-selected to the
 *       login hospital) and <b>Hospital Logo</b> (file input {@code #PhotoData}).</li>
 *   <li>Bootstrap accordion {@code #accordion1}: General, Nursing Station, Investigation, Ancillary, HL7/FTP.
 *       Only one is open at a time (opening Nursing Station closes General). Ancillary holds a nested
 *       accordion: Blood Bank, Equipment, CSSD, Linen, DMS, MRD, Ambulance, Vital.</li>
 *   <li>Buttons: <b>Save</b> ({@code fnSaveconfigapp()}) and <b>Clear</b> ({@code ClearForm()}).</li>
 *   <li>Save runs client-side checks first and shows a toastr warning (JAlert appends "!"). Only when all of
 *       them pass does it POST {@code /api/ApplicationConfiguration/IUD}.</li>
 * </ul>
 *
 * <p><b>Safety:</b> this screen holds the hospital-wide configuration. {@link #blockSaveRequests()} aborts every
 * call to the save endpoint, so the validation checks cannot change live data even if a validation is
 * missing. {@link #saveAttempts()} reports whether the screen tried to save.</p>
 */
public class ApplicationPage extends BasePage {

    public ApplicationPage(Page page) { super(page); }

    public static final String ROUTE = "#/ApplicationConfiguration";
    public static final String ROOT = "[ng-controller='ApplicationConfigurationController']";
    public static final String VIEW_URL_PART = "/Billing/Load/ApplicationConfiguration";
    public static final String SAVE_API_PATH = "/api/ApplicationConfiguration/IUD";
    /** Every call under this prefix is aborted unless it is one of the screen's read calls (Fetch... / GetPhoto). */
    private static final java.util.regex.Pattern CONFIG_API = java.util.regex.Pattern.compile("(?i)/api/ApplicationConfiguration/");
    private static final java.util.regex.Pattern CONFIG_READ = java.util.regex.Pattern.compile("(?i)/api/ApplicationConfiguration/(Fetch[A-Za-z0-9]*|GetPhoto)(\\?|$)");

    public static final String LOCATION = "[ng-model='conffapp1.locationid1']";
    public static final String LOGO_FILE = "#PhotoData";
    public static final String SAVE_BTN = ROOT + " button[ng-click='fnSaveconfigapp();']";
    public static final String CLEAR_BTN = ROOT + " button[ng-click='ClearForm()']";

    // ng-models of the fields that Save validates (all inside General / #collapseOne1)
    public static final String REFILLING_DAYS = "conffapp.refillingdays";
    public static final String ALT_DRUG_ACK_HRS = "conffapp.alternatedrugacknowledgmenthrs";
    public static final String PREGNANCY_WEEKS = "conffapp.pregnancyreviewgestationalweeks";
    public static final String PROGRESS_NOTES_HRS = "conffapp.progressnotesapprovaltimelimithrs";
    public static final String OP_ORDER_HRS = "conffapp.opordertimelimithrs";
    public static final String IP_ORDER_HRS = "conffapp.ipordertimelimithrs";
    public static final String EOL_DAYS = "conffapp.eolvalidityperioddays";
    public static final String DNR_DIL_DAYS = "conffapp.dnrdilvalidityperioddays";

    /** Top-level accordion: title -> collapse id, plus the first label seen live in each section. */
    public static final Map<String, String[]> SECTIONS = new LinkedHashMap<>();
    /** Nested accordion inside Ancillary. */
    public static final Map<String, String[]> ANCILLARY = new LinkedHashMap<>();
    static {
        SECTIONS.put("General",         new String[]{"collapseOne1",   "Default Sponsor Type"});
        SECTIONS.put("Nursing Station", new String[]{"collapseFour4",  "Drug Admin Time Before"});
        SECTIONS.put("Investigation",   new String[]{"collapseTwo2",   "Default Normal Test Type"});
        SECTIONS.put("Ancillary",       new String[]{"collapseThree3", "Blood Bank Store"});
        SECTIONS.put("HL7/FTP",         new String[]{"collapseThree4", "FileServerType"});

        ANCILLARY.put("Blood Bank", new String[]{"BloodBank", "Blood Bank Store"});
        ANCILLARY.put("Equipment",  new String[]{"Equipment", "Equip Group"});
        ANCILLARY.put("CSSD",       new String[]{"CSSD",      "CSSD Group"});
        ANCILLARY.put("Linen",      new String[]{"Linen",     "Linen Group"});
        ANCILLARY.put("DMS",        new String[]{"DMS",       "Default Images Path for search"});
        ANCILLARY.put("MRD",        new String[]{"MRD",       "Mrd Department"});
        ANCILLARY.put("Ambulance",  new String[]{"Ambulance", "Vehicle Type"});
        ANCILLARY.put("Vital",      new String[]{"Vital",     "Height"});
    }

    private final AtomicInteger saveAttempts = new AtomicInteger();
    private final AtomicInteger iudResponses = new AtomicInteger();
    private final AtomicInteger apiWrites = new AtomicInteger();
    private volatile int viewStatus = -1;
    /** Screenshot taken while the last toast was still on screen (toastr auto-dismisses). */
    public byte[] toastPng;

    private static String model(String ngModel) { return ROOT + " [ng-model='" + ngModel + "']"; }

    // ---- safety + diagnostics -------------------------------------------

    /**
     * Abort every write call to the configuration API for the rest of this page's life and count the attempts.
     * Allow-list: only {@code Fetch...} and {@code GetPhoto} (the screen's read calls) pass; {@code IUD} and anything
     * else under {@code /api/ApplicationConfiguration/} is aborted in the browser, before it reaches the server.
     */
    public void blockSaveRequests() {
        page.route(CONFIG_API, route -> {
            String url = route.request().url();
            if (CONFIG_READ.matcher(url).find()) { route.resume(); return; }
            saveAttempts.incrementAndGet();
            System.out.println("ApplicationPage: BLOCKED " + route.request().method() + " " + url);
            route.abort();
        });
        page.onResponse((Response r) -> {
            if (r.url().contains(VIEW_URL_PART)) viewStatus = r.status();
            if (r.url().toLowerCase().contains(SAVE_API_PATH.toLowerCase())) iudResponses.incrementAndGet();
        });
        page.onRequest(r -> {
            String u = r.url().toLowerCase();
            if (u.contains("/api/") && !"GET".equalsIgnoreCase(r.method())) apiWrites.incrementAndGet();
        });
    }

    /** Responses received from the save endpoint. Must stay 0: a response means a save reached the server. */
    public int iudResponses() { return iudResponses.get(); }
    /** Non-GET /api/ requests the page started (aborted ones included). Used to prove Clear sends nothing. */
    public int apiWrites() { return apiWrites.get(); }

    public int saveAttempts() { return saveAttempts.get(); }
    /** HTTP status of the screen's view request (-1 if not seen). 500 = server-side failure. */
    public int viewStatus() { return viewStatus; }

    // ---- navigation ------------------------------------------------------

    /** Left menu: System Configuration -> Application. Waits for the form and its data to load. */
    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/^\\s*system\\s*configuration\\s*$/i.test(a.textContent||'') && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>/^\\s*system\\s*configuration\\s*$/i.test(x.textContent||'') && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            Locator link = page.locator("a[href='" + ROUTE + "']").first();
            try { link.click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.querySelector(\"a[href='" + ROUTE + "']\"); if(a) a.click(); }"); }
            waitForLoaded(30000);
        }
        return onScreen();
    }

    /** Form rendered AND its data bound (Location chosen, Refilling Days filled). */
    public boolean waitForLoaded(int timeoutMs) {
        try {
            page.waitForFunction("() => { const r=document.querySelector(\"" + ROOT + "\"); if(!r) return false;"
                    + " const loc=r.querySelector(\"[ng-model='conffapp1.locationid1']\");"
                    + " const rd=r.querySelector(\"[ng-model='conffapp.refillingdays']\");"
                    + " return !!loc && loc.selectedIndex>0 && !!rd && rd.value!==''; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            waitForAngular(800);
            return true;
        } catch (Exception e) {
            System.out.println("ApplicationPage.waitForLoaded: " + e.getMessage());
            return false;
        }
    }

    public boolean onScreen() {
        return page.url().contains("ApplicationConfiguration") && page.locator(ROOT).count() > 0;
    }

    public String currentScreen() {
        Object t = page.evaluate("() => document.title");
        return (t == null ? "(no title)" : t.toString()) + " @ " + page.url();
    }

    // ---- read-only checks ------------------------------------------------

    /** Location* label, selected value and option count. */
    public String locationInfo() {
        Object r = page.evaluate("() => { const s=document.querySelector(\"" + LOCATION + "\"); if(!s) return '(no Location field)';"
                + " const g=s.closest('.form-group,.row,div'); const lab=[...document.querySelectorAll(\"" + ROOT + " label\")].map(l=>l.textContent.trim()).find(t=>/^location/i.test(t))||'';"
                + " return 'label='+lab+' | selected='+(s.options[s.selectedIndex]||{}).text+' | options='+s.options.length; }");
        return r == null ? "" : r.toString();
    }

    public boolean locationSelected() {
        return Boolean.TRUE.equals(page.evaluate("() => { const s=document.querySelector(\"" + LOCATION + "\"); return !!s && s.selectedIndex>0; }"));
    }

    public boolean logoUploadPresent() {
        return page.locator(LOGO_FILE).count() > 0;
    }

    /** Titles of every accordion heading that is rendered (top level + Ancillary sub-sections). */
    public List<String> sectionTitles() {
        @SuppressWarnings("unchecked")
        List<String> l = (List<String>) page.evaluate("() => [...document.querySelectorAll(\"" + ROOT + " .panel-heading h4\")].map(h=>h.textContent.replace(/\\s+/g,' ').trim())");
        return l == null ? new ArrayList<>() : l;
    }

    public boolean isExpanded(String collapseId) {
        return Boolean.TRUE.equals(page.evaluate("(id) => { const b=document.getElementById(id); return !!b && b.classList.contains('in') && b.offsetHeight>0; }", collapseId));
    }

    /** Click a section heading (real click) and wait for the Bootstrap collapse to open. */
    public boolean expand(String collapseId) {
        if (isExpanded(collapseId)) return true;
        Locator head = page.locator(ROOT + " a[data-target='#" + collapseId + "']").first();
        try {
            head.scrollIntoViewIfNeeded();
            head.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("ApplicationPage.expand(" + collapseId + "): " + e.getMessage());
            page.evaluate("(id) => { const a=document.querySelector(\"a[data-target='#\"+id+\"']\"); if(a) a.click(); }", collapseId);
        }
        try {
            page.waitForFunction("(id) => { const b=document.getElementById(id); return !!b && b.classList.contains('in') && !b.classList.contains('collapsing') && b.offsetHeight>0; }",
                    collapseId, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        waitForAngular(400);
        return isExpanded(collapseId);
    }

    /** First non-empty visible labels inside a section body. */
    public List<String> visibleLabels(String collapseId, int max) {
        @SuppressWarnings("unchecked")
        List<String> l = (List<String>) page.evaluate("(a) => { const b=document.getElementById(a.id); if(!b) return [];"
                + " return [...b.querySelectorAll('label')].filter(x=>x.offsetParent!==null).map(x=>x.textContent.replace(/\\s+/g,' ').trim()).filter(Boolean).slice(0,a.max); }",
                Map.of("id", collapseId, "max", max));
        return l == null ? new ArrayList<>() : l;
    }

    /** min / max / type of a numeric field, e.g. "type=number min=1 max=720". */
    public String numberRange(String ngModel) {
        Object r = page.evaluate("(m) => { const e=document.querySelector(\"" + ROOT + " [ng-model='\"+m+\"']\"); if(!e) return '(field not found)';"
                + " return 'type='+e.type+' min='+e.getAttribute('min')+' max='+e.getAttribute('max'); }", ngModel);
        return r == null ? "" : r.toString();
    }

    public String value(String ngModel) {
        Locator l = page.locator(model(ngModel)).first();
        return l.count() == 0 ? null : l.inputValue();
    }

    public boolean saveVisibleAndEnabled() {
        Locator s = page.locator(SAVE_BTN).first();
        return s.count() > 0 && s.isVisible() && s.isEnabled();
    }

    public boolean clearVisible() {
        Locator c = page.locator(CLEAR_BTN).first();
        return c.count() > 0 && c.isVisible();
    }

    // ---- validation (Save is blocked) -------------------------------------

    /**
     * Put {@code value} in one field, click Save, return the toast, then put the original value back.
     * The original is restored in the form only; nothing is sent because {@link #blockSaveRequests()} is active.
     */
    public String saveWithValue(String ngModel, String value) {
        expand(SECTIONS.get("General")[0]);
        Locator f = page.locator(model(ngModel)).first();
        if (f.count() == 0) return "(field " + ngModel + " not found)";
        String original = f.inputValue();
        f.scrollIntoViewIfNeeded();
        f.fill(value);
        f.blur();
        waitForAngular(300);
        String toast = clickSaveAndGetToast();
        f.fill(original == null ? "" : original);
        f.blur();
        waitForAngular(300);
        return toast;
    }

    /** Click Save and capture the toastr message text (title excluded). */
    public String clickSaveAndGetToast() {
        if (page.locator(SAVE_BTN).count() == 0) return "(Save button not found)";
        armToastCapture();
        Locator save = page.locator(SAVE_BTN).first();
        try {
            save.scrollIntoViewIfNeeded();
            save.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("ApplicationPage.clickSave: " + e.getMessage());
            return "(Save click failed: " + e.getMessage() + ")";
        }
        return readToast();
    }

    /**
     * Click Clear. Inspected before use (controller {@code ClearForm()}): it only sets the dropdown OPTION lists
     * ({@code drp...}) to null in the browser - no HTTP call, no change to the field values or Location.
     * Returns what the form shows afterwards. The caller must reload the screen and must not click Save.
     */
    public String clickClear() {
        Locator c = page.locator(CLEAR_BTN).first();
        c.scrollIntoViewIfNeeded();
        c.click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(1000);
        return locationInfo() + " | Refilling Days=" + value(REFILLING_DAYS);
    }

    /** Every stored field value of the form (Angular scope conffapp + Location), as JSON - to prove nothing changed. */
    public String configSnapshot() {
        Object r = page.evaluate("() => { const r=document.querySelector(\"" + ROOT + "\"); const sc=window.angular && r ? angular.element(r).scope() : null;"
                + " if(!sc) return ''; const pick=o=>{ const out={}; Object.keys(o||{}).sort().forEach(k=>{ if(k.indexOf('$$')===0) return;"
                + "   const v=o[k]; if(v===null||['string','number','boolean'].includes(typeof v)) out[k]=v; else if(v instanceof Date) out[k]=v.toISOString(); });"
                + "   return out; };"
                + " return JSON.stringify({loc: pick(sc.conffapp1), cfg: pick(sc.conffapp)}); }");
        return r == null ? "" : r.toString();
    }

    /** Re-open the screen so later checks start from the stored configuration. */
    public boolean reload() {
        page.navigate(page.url().replaceAll("#.*$", "") + ROUTE);
        return waitForLoaded(30000);
    }

    private void armToastCapture() {
        clearToasts();
        page.evaluate("() => { window.__acToasts=[]; if(window.__acObs) window.__acObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('#toast-container .toast').forEach(el=>{"
                + "   const m=el.querySelector('.toast-message'); const t=((m||el).textContent||'').replace(/\\s+/g,' ').trim();"
                + "   const kind=(el.className.match(/toast-(success|error|warning|info)/)||[])[1]||'';"
                + "   const v=kind+'|'+t; if(t && !window.__acToasts.includes(v)) window.__acToasts.push(v); });"
                + " window.__acObs=new MutationObserver(grab); window.__acObs.observe(document.body,{childList:true,subtree:true}); }");
    }

    /** "warning|Refilling Days is Mandatory!" style string, or "" if no toast appeared. */
    private String readToast() {
        try {
            page.waitForFunction("() => (window.__acToasts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); } catch (Exception ignore) { toastPng = null; }
        Object r = page.evaluate("() => (window.__acToasts||[])[0]||''");
        clearToasts();
        return r == null ? "" : r.toString();
    }

    /**
     * Dismiss toasts through toastr itself and wait until its container is gone. toastr may suppress a message
     * identical to the previous one (preventDuplicates) until its container is removed, so the next validation
     * (e.g. 721 after 0, same text) only gets a fresh toast once this has finished.
     */
    private void clearToasts() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} }");
            page.waitForFunction("() => !document.querySelector('#toast-container .toast')",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) { }
        waitForAngular(300);
    }
}
