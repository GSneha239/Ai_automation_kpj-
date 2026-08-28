package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.nio.file.Path;
import java.util.Map;

/**
 * Application Configuration &gt; Location &gt; <b>Payable</b> ({@code #/paybleMaster}, add form {@code #/add-Payble}) —
 * Page Object.
 *
 * <p>Full staff/doctor registration master with a header block (Reg. Type, Payable Type, Code, User Role, Name,
 * Gender, Date of Birth, Doctor Type, ...) plus a growing set of tabs. <b>Selecting Payable Type = "Doctor" is what
 * unlocks the Doctor Type select and six extra tabs</b> (Location-VisitType, Location-Classification, Dependent
 * List, Education, Experience, Einvoice) — with the default "--Select--" only <b>Personal Information</b>,
 * <b>Location-Department</b>, <b>Address Information</b>, and <b>File Linking</b> exist. Save is
 * {@code fnIUDpayable()}.</p>
 */
public class Payable extends BasePage {

    public Payable(Page page) { super(page); }

    public static String ROUTE = "";

    public String lastCode = "", lastName = "";
    public String lastToasts = "[]", lastSaveApi = "";
    public byte[] toastPng;

    /** Realistic doctor names — never "Auto Payable <code>" (see feedback-realistic-test-data). */
    private static final String[] FIRST_NAMES = { "Ahmad Faiz", "Nur Aisyah", "Kumaravel", "Siti Zulaikha", "Wei Jian", "Preethi", "Farid", "Michelle" };
    private static final String[] LAST_NAMES = { "bin Ismail", "binti Rahman", "a/l Muthu", "binti Hassan", "Tan", "a/p Raj", "bin Yusof", "Wong" };
    private static final String[] DEPENDENT_NAMES = { "Nur Aina", "Danish Iman", "Sofea Batrisyia", "Aiman Haziq", "Qistina Zara" };

    // ---- shared field helpers ----------------------------------------------

    /** Set a text/date input's Angular model directly (ng-model may not be wired to plain DOM events on some of
     *  this form's fields), then dispatch input/change so any {@code ng-change} watcher fires. */
    private void setNg(String ngModel, String value) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return false;"
                + " const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){ c.$setViewValue(a.v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; }",
                Map.of("ng", ngModel, "v", value));
    }

    /** Poll until the select has more than one option (i.e. real data has loaded, not just "--Select--") — the add
     *  form's dropdowns were seen live to still be a bare placeholder for a beat after {@code addFormOpen()}
     *  already reports true, which silently no-ops every {@code selectFirstReal}/{@code selectPreferring} call
     *  that races it. */
    private boolean waitForOptions(String ngModel, int minOptions) {
        String sel = "select[ng-model='" + ngModel + "']";
        for (int i = 0; i < 20; i++) {
            Object n = page.evaluate("(s) => { const e=document.querySelector(s); return e ? e.options.length : -1; }", sel);
            int count = n instanceof Number ? ((Number) n).intValue() : -1;
            if (count >= minOptions) return true;
            page.waitForTimeout(400);
        }
        System.out.println("waitForOptions(" + ngModel + "): still not populated after 8s");
        return false;
    }

    /** Real Playwright {@code selectOption} on the first real (non "--Select--") option of a select by ng-model.
     *  Returns the option text picked, or "" if the select/any real option wasn't found. */
    private String selectFirstReal(String ngModel) {
        String sel = "select[ng-model='" + ngModel + "']";
        try {
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return -1;"
                    + " return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i < 0) return "";
            page.locator(sel).first().selectOption(new SelectOption().setIndex(i));
            waitForAngular(300);
            return String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectFirstReal(" + ngModel + "): " + e.getMessage()); return ""; }
    }

    /** Same as {@link #selectFirstReal} but prefers an option whose text matches {@code preferRegex}, falling back
     *  to the first real option. */
    private String selectPreferring(String ngModel, String preferRegex) {
        String sel = "select[ng-model='" + ngModel + "']";
        try {
            Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const re=new RegExp(a.re,'i');"
                    + " const opts=[...e.options]; let i=opts.findIndex(o=>o.value && re.test((o.textContent||'').trim()));"
                    + " if(i<0) i=opts.findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); return i; }",
                    Map.of("s", sel, "re", preferRegex));
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i < 0) return "";
            page.locator(sel).first().selectOption(new SelectOption().setIndex(i));
            waitForAngular(300);
            return String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectPreferring(" + ngModel + "): " + e.getMessage()); return ""; }
    }

    private void switchTab(String tabText) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(t) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const re=new RegExp('^\\\\s*'+t+'\\\\s*$','i');"
                + " const el=[...document.querySelectorAll('[role=tab], .nav-tabs a, ul.nav a')].find(x=>x.offsetParent!==null && re.test(norm(x.textContent)));"
                + " if(!el) return false; el.id='__pyTab'; return true; }", tabText));
        if (tagged) { try { page.locator("#__pyTab").click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { } page.evaluate("() => { const e=document.getElementById('__pyTab'); if(e) e.removeAttribute('id'); }"); }
        else System.out.println("switchTab: tab not found - " + tabText);
        waitForAngular(500);
    }

    private String activePanelSelector() { return ".tab-pane.active"; }

    /** Tick the first not-yet-checked pair of checkboxes matching {@code rowNgModel} + {@code defaultNgModel}
     *  that live in the SAME table row (real Playwright clicks, so any ng-click validator on the row fires). */
    private String tickFirstRowPair(String rowNgModel, String defaultNgModel) {
        Object tagged = page.evaluate("(a) => { const panel=document.querySelector(a.panel); if(!panel) return null;"
                + " const rows=[...panel.querySelectorAll('tr')].filter(r=>r.offsetParent!==null && r.querySelector(\"input[ng-model='\"+a.row+\"']\") && r.querySelector(\"input[ng-model='\"+a.def+\"']\"));"
                + " const r=rows.find(rr=>!rr.querySelector(\"input[ng-model='\"+a.row+\"']\").checked);"
                + " if(!r) return null; const cb=r.querySelector(\"input[ng-model='\"+a.row+\"']\"); const db=r.querySelector(\"input[ng-model='\"+a.def+\"']\");"
                + " cb.id='__pyRow'; db.id='__pyDef'; return (r.textContent||'').replace(/\\s+/g,' ').trim().slice(0,40); }",
                Map.of("panel", activePanelSelector(), "row", rowNgModel, "def", defaultNgModel));
        if (tagged == null) return "";
        try { page.locator("#__pyRow").click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        waitForAngular(300);
        try { page.locator("#__pyDef").click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        page.evaluate("() => { ['__pyRow','__pyDef'].forEach(i=>{ const e=document.getElementById(i); if(e) e.removeAttribute('id'); }); }");
        waitForAngular(400);
        return tagged.toString();
    }

    private String tickFirstRow(String ngModel) {
        Object tagged = page.evaluate("(a) => { const panel=document.querySelector(a.panel); if(!panel) return null;"
                + " const cbs=[...panel.querySelectorAll(\"input[ng-model='\"+a.ng+\"']\")].filter(e=>e.offsetParent!==null && !e.checked);"
                + " if(!cbs.length) return null; const cb=cbs[0]; cb.id='__pySingle';"
                + " const row=cb.closest('tr'); return row ? (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,40) : 'row 0'; }",
                Map.of("panel", activePanelSelector(), "ng", ngModel));
        if (tagged == null) return "";
        try { page.locator("#__pySingle").click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__pySingle'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);
        return tagged.toString();
    }

    // ---- navigation ---------------------------------------------------------

    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(1500);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*payable\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null) || [...document.querySelectorAll('a[href]')].find(x=>/paybleMaster/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__pyMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__pyMenu").click(new Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("Payable.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pyMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        } else { System.out.println("Payable.nav: menu link 'Payable' not found under Location"); }
        if (!onScreen() && !ROUTE.isEmpty()) { try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { } waitForAngular(2000); }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /^payable$/im.test((document.querySelector('h1')||{}).textContent||'')"));
    }

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(4000)); return; } catch (Exception e) { System.out.println("Payable." + what + ": real click intercepted - DOM fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); } catch (Exception e) { System.out.println("Payable." + what + ": DOM click failed - " + e.getMessage()); }
    }

    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 30 && !tagged; i++) {
            Object state = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent)));"
                    + " if(b){ b.id='__pyAdd'; return true; } return false; }");
            tagged = Boolean.TRUE.equals(state);
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("Payable.clickAdd: Add button not found"); return false; }
        robustClick("__pyAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-model]')].some(e=>e.getAttribute('ng-model')==='payable.PayableCode')"));
    }

    // ---- header (Reg. Type, Payable Type, Code, User Role, Name, Gender, DOB, Doctor Type) ----

    /** Fill the always-visible header block. Selecting Payable Type = "Doctor" is what unlocks Doctor Type and the
     *  six extra tabs, so this always picks it rather than a random Payable Type. */
    public String fillHeader() {
        waitForOptions("payable.PatientTypeID", 2);
        String regType = selectFirstReal("payable.PatientTypeID");
        waitForOptions("payable.PayableTypeID", 2);
        String payableType = selectPreferring("payable.PayableTypeID", "doctor");
        waitForAngular(600);   // Doctor Type + extra tabs render after this selection settles
        lastCode = "PAY" + String.format("%07d", Math.abs(System.nanoTime() / 977 % 10000000));
        setNg("payable.PayableCode", lastCode);
        waitForOptions("payable.UserRoleId", 2);
        String userRole = selectFirstReal("payable.UserRoleId");
        waitForOptions("payable.PrefixId", 2);
        String title = selectFirstReal("payable.PrefixId");
        lastName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        setNg("payable.FirstName", lastName);
        waitForOptions("payable.GenderID", 2);
        String gender = selectPreferring("payable.GenderID", "^male$");
        setNg("payable.BirthDate", randomDOB());
        waitForOptions("payable.doctortypeid", 2);
        String doctorType = selectFirstReal("payable.doctortypeid");
        return "RegType=" + regType + " | PayableType=" + payableType + " | Code=" + lastCode + " | UserRole=" + userRole
                + " | Title=" + title + " | Name=" + lastName + " | Gender=" + gender + " | DoctorType=" + doctorType;
    }

    // ---- Personal Information tab -------------------------------------------

    public String fillPersonalInformation() {
        switchTab("Personal Information");
        String marital = selectFirstReal("payable.MaritalStatusId");
        String nationality = selectPreferring("payable.NationalityId", "malaysia");
        String designation = selectFirstReal("payable.DesignationID");
        setNg("payable.RegNo", "MMC" + uniqueId());
        setNg("payable.EmailId", randomEmail());
        return "MaritalStatus=" + marital + " | Nationality=" + nationality + " | Designation=" + designation;
    }

    // ---- Location-Department tab --------------------------------------------

    public String fillLocationDepartment() {
        switchTab("Location-Department");
        String row = tickFirstRowPair("Pay.Visibility", "Pay.IsDefaultDoctor");
        return row.isEmpty() ? "no unticked department row found" : "Ticked department + default doctor: " + row;
    }

    // ---- Address Information tab --------------------------------------------

    public String fillAddressInformation() {
        switchTab("Address Information");
        String type = selectFirstReal("ADDpayable.AddressType");
        String country = selectPreferring("ADDpayable.Country", "malaysia");
        waitForAngular(800);   // State list loads async off Country
        String state = selectFirstReal("ADDpayable.State");
        waitForAngular(800);
        String city = selectFirstReal("ADDpayable.City");
        waitForAngular(800);
        String area = selectFirstReal("ADDpayable.Area");
        boolean added = clickInPanel("AddAddress()");
        return "Type=" + type + " | Country=" + country + " | State=" + state + " | City=" + city + " | Area=" + area + " | Add=" + added;
    }

    // ---- File Linking tab ----------------------------------------------------

    public String fillFileLinking(Path filePath) {
        switchTab("File Linking");
        setNg("payable.filename", "Medical Registration Certificate");
        try { page.locator("input[ng-model='payable.fileimage']").setInputFiles(filePath); }
        catch (Exception e) { System.out.println("fillFileLinking: setInputFiles failed - " + e.getMessage()); }
        waitForAngular(500);
        setNg("filelinkings.documentname", "Scanned copy of medical registration certificate");
        boolean added = clickInPanel("AddFileDetails(linklist)");
        return "File=" + filePath.getFileName() + " | Add=" + added;
    }

    // ---- Location-VisitType tab -----------------------------------------------

    public String fillLocationVisitType() {
        switchTab("Location-VisitType");
        String row = tickFirstRowPair("Pay.Status", "Pay.IsDefaultvisittypeid");
        return row.isEmpty() ? "no unticked visit-type row found" : "Ticked location + default: " + row;
    }

    // ---- Location-Classification tab -------------------------------------------

    public String fillLocationClassification() {
        switchTab("Location-Classification");
        String row = tickFirstRow("tt.IsSelected");
        return row.isEmpty() ? "no unticked classification row found" : "Ticked: " + row;
    }

    // ---- Dependent List tab -----------------------------------------------------

    public String fillDependentList() {
        switchTab("Dependent List");
        String name = DEPENDENT_NAMES[random.nextInt(DEPENDENT_NAMES.length)] + " " + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        setNg("dependentEntry.Name", name);
        setNg("dependentEntry.DateOfBirth", "2015-06-15");
        setNg("payable.MobileNo", randomMobile());
        String relation = selectFirstReal("dependentEntry.fnSetRelation");
        boolean added = clickInPanel("Adddependentlist()");
        return "Name=" + name + " | Relation=" + relation + " | Add=" + added;
    }

    // ---- Education tab -----------------------------------------------------------

    public String fillEducation() {
        switchTab("Education");
        setNg("Qualification.qualification", "MBBS");
        setNg("Qualification.university", "University of Malaya");
        setNg("Qualification.yearofpassing", "2010");
        setNg("Qualification.class1", "First Class");
        boolean added = clickInPanel("AddEducation()");
        return "Qualification=MBBS | Add=" + added;
    }

    // ---- Experience tab -------------------------------------------------------

    public String fillExperience() {
        switchTab("Experience");
        setNg("Experience.fromdate", "01/01/2015");
        setNg("Experience.todate", "31/12/2019");
        setNg("Experience.expyear", "5");
        setNg("Experience.expmonth", "0");
        setNg("Experience.postheld", "Medical Officer");
        setNg("Experience.lastdrawnsalary", "8000");
        setNg("Experience.company", "Pantai Hospital");   // labelled "Payor" on this tab
        setNg("Experience.reasonforleaving", "Career Advancement");
        boolean added = clickInPanel("AddExperience()");
        return "PostHeld=Medical Officer | Add=" + added;
    }

    // ---- Einvoice tab -----------------------------------------------------------

    public String fillEinvoice() {
        switchTab("Einvoice");
        setNg("Einvoice.tinnumber", "TIN" + uniqueId());
        setNg("Einvoice.identificationno", randomNRIC());
        setNg("Einvoice.tourismtaxregistrationno", "TTX" + uniqueId());
        setNg("Einvoice.sstregistrationno", "SST" + uniqueId());
        setNg("Einvoice.misccode", "MISC001");
        setNg("Einvoice.businessactivitydescription", "Medical and Surgical Services");
        setNg("Einvoice.bankaccountno", "16420" + uniqueId());
        boolean added = clickInPanel("AddEinvoice()");
        return "Add=" + added;
    }

    private boolean clickInPanel(String ngClickContains) {
        Object tagged = page.evaluate("(a) => { const panel=document.querySelector(a.panel); if(!panel) return false;"
                + " const b=[...panel.querySelectorAll('[ng-click]')].find(x=>x.offsetParent!==null && x.getAttribute('ng-click')===a.nc);"
                + " if(!b) return false; b.id='__pyPanelBtn'; return true; }",
                Map.of("panel", activePanelSelector(), "nc", ngClickContains));
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickInPanel: '" + ngClickContains + "' not found"); return false; }
        try { page.locator("#__pyPanelBtn").click(new Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickInPanel: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__pyPanelBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(500);
        return true;
    }

    // ---- submit ---------------------------------------------------------------

    /**
     * Click <b>Save</b> on the confirm popup that Submit raises on a form that actually passes validation (a blank
     * Submit was checked live and is silently blocked with no dialog at all — so this only fires on a real
     * attempt). Polls briefly since the dialog renders a moment after the Submit click, same as the "Do You Want
     * to Save" pattern elsewhere in this app ({@code BasePage.acceptSaveDialog}, {@code IpdCharges.answerSaveConfirm}).
     */
    private void answerSaveConfirm() {
        for (int i = 0; i < 10; i++) {
            Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const box=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0);"
                    + " if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button')].find(x=>/^(save|yes|ok|confirm)$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                    + " if(!b) return false; b.click(); return true; }");
            if (Boolean.TRUE.equals(clicked)) { System.out.println("answerSaveConfirm: clicked Save on the confirm popup"); waitForAngular(600); return; }
            page.waitForTimeout(400);
        }
    }

    /** Click <b>Submit</b> ({@code fnIUDpayable()}) and return the toast, screenshotting it the moment it appears
     *  (same pattern as {@code PayableType.submitAndGetToast} — toasts on these screens fade in ~1s). */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        // "save" alone is too broad — it also matches the unrelated FTPFileTransfer/SaveServerFiles call the File
        // Linking tab fires (its URL happens to contain both "payable" and "save" too), which returned HTTP 500
        // live (2026-08-24) without affecting the actual Payable save. Require "iud" (this app's own IUD-endpoint
        // naming, e.g. IUDBillSaveOPD, IUDPaybleType) and exclude that FTP call explicitly.
        page.onResponse(resp -> { String u = resp.url().toLowerCase(); if (u.contains("payable") && u.contains("iud") && !u.contains("ftpfiletransfer")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__pyToasts=[]; if(window.__pyObs) window.__pyObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pyToasts.includes(t)) window.__pyToasts.push(t); }); };"
                + " window.__pyObs=new MutationObserver(grab); window.__pyObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^\\s*submit\\s*$/i.test((x.textContent||'').trim()));"
                + " if(!b) return false; b.id='__pySubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__pySubmit", "submit");
        answerSaveConfirm();

        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) { try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); } catch (Exception e) { break; } }
            else page.waitForTimeout(200);
        }
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__pyToasts||[]).includes(t)) (window.__pyToasts=window.__pyToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__pyToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                java.util.regex.Matcher rs = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + " " + hold[0].url() + (rs.find() ? ", ResultStatus=" + rs.group(1) : "") + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
                System.out.println("submit: save API => " + lastSaveApi);
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__pyToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x)) || a.find(x=>/exist|error|please|select|enter|required|already|fill/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
