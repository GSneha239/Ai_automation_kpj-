package com.kpj.pages.Ip;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * GraphicalView — Page Object for <b>IP &gt; Inpatients &gt; Graphical View</b> (#/GraphicalView).
 *
 * <p>Confirmed live (Chrome DevTools on the real app): the bed board is a plain {@code ng-repeat} list —
 * {@code ul.Bed-Status > li[ng-repeat="BedList in BedStatusList | bedSort:getSortValue"]}, each tagged
 * {@code ng-click="TogglePatient(BedList)"} and a {@code title} of {@code "Bed: <name>\nRoom Type:
 * <room>\nWard: <ward>"}. Each {@code <li>} renders ONE of three status images (all present in the DOM,
 * only one un-hidden via {@code ng-show}):</p>
 * <ul>
 *   <li>{@code BedList.BedStatus == 0} → vacant (green, "patient bed.png")</li>
 *   <li>{@code BedList.BedStatus == 1} → occupied (red, "admitpatient.png")</li>
 *   <li>{@code BedList.BedStatus == 2} → discharged (yellow, "discharge.png")</li>
 * </ul>
 * <p>plus overlay icons on an occupied bed: {@code genderid == 2} → Male, {@code genderid == 3} → Female,
 * a housekeeping-in-progress icon, "Infectious", "Bill Prepared" and "Pre Admission" markers — matching the
 * counts/legend at {@code #BedStatusCriteria} ("Vacant/Occupied/Discharged/Under Maintenance/... Male and
 * Female/Total Beds").</p>
 *
 * <p>Selecting a bed (clicking its {@code <li>}) enables a toolbar including
 * {@code button[ng-click='GetBedDetailsView()']} (title "View Details"), which opens a <b>Bed Details</b>
 * modal ({@code .BedDetailsPopup}) with three Bootstrap tabs: <b>Bed Information</b>
 * ({@code #BedInformation} — Bed Name/Ward/Class Name/Room Name/Amenities), <b>Admission Details</b>
 * ({@code #AdmissionDetails} — Admission Date/Time, IPD No, Doctor, Patient Name, MRN, Mobile, Referring
 * Doctor) and <b>Rate Details</b> ({@code #RoomTariff} — a Pricing Policy select
 * ({@code select[ng-model='BedDetailsList.TariffId']}) that loads a Service Name/Unit/Purchase Price rate
 * table). <b>OK</b> ({@code ng-click="clearform()"}) closes the modal.</p>
 */
public class GraphicalView extends BasePage {

    public GraphicalView(Page page) {
        super(page);
    }

    // ---- navigation ---------------------------------------------------------

    /** Open Graphical View directly via hash (fast path), falling back to the IP → Inpatients menu if the
     *  bed board hasn't rendered. Returns true once the bed list (or its "no beds" state) is ready. */
    public boolean navigateTo(String baseUrl) {
        page.evaluate("() => { window.location.hash = '#/GraphicalView'; }");
        waitForAngular(1500);

        boolean ready = Boolean.TRUE.equals(page.evaluate(
                "() => !!document.querySelector('#BedStatusCriteria') || !!document.querySelector('ul.Bed-Status')"));
        if (!ready) {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const ipLi=[...document.querySelectorAll('li')].find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test(norm(a.textContent)); });"
                    + " const ipA=ipLi && ipLi.querySelector(':scope > a'); if(ipA) ipA.click(); }");
            waitForAngular(900);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const inpA=[...document.querySelectorAll('a')].find(a=>norm(a.textContent)==='Inpatients'); if(inpA) inpA.click(); }");
            waitForAngular(900);
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/GraphicalView' && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1500);
        }

        try {
            page.waitForFunction("() => !!document.querySelector('#BedStatusCriteria') || !!document.querySelector('ul.Bed-Status')",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("GraphicalView.navigateTo: bed board did not render in time"); }
        waitForAngular(1000);
        // The bed list itself loads asynchronously after the shell renders — give it a real chance before
        // callers start looking for beds.
        try {
            page.waitForFunction("() => document.querySelectorAll('ul.Bed-Status li').length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("GraphicalView.navigateTo: bed list stayed empty"); }
        return page.url().toLowerCase().contains("graphicalview");
    }

    // ---- legend / counts ------------------------------------------------------

    /** The Vacant/Occupied/Discharged/... counts strip at {@code #BedStatusCriteria} (the legend the user
     *  points at — green/red/yellow beds plus the M/F, housekeeping-bucket and other overlay icons). */
    public String readLegendCounts() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const el=document.evaluate('//*[@id=\"BedStatusCriteria\"]/div[2]/div[3]', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"
                + " return el ? norm(el.innerText) : null; }");
        return r == null ? "" : r.toString();
    }

    // ---- bed selection ----------------------------------------------------

    /** Click the Nth (0-based, DOM order) bed whose status matches {@code bedStatus} (0=vacant, 1=occupied,
     *  2=discharged). Returns the bed's {@code title} text ("Bed: ...\nRoom Type: ...\nWard: ..."), or null
     *  if there is no such bed (fewer than {@code index + 1} beds of that status). */
    private String selectBedByStatus(int bedStatus, int index) {
        // Remove any stale '__bedPick' id from a PREVIOUSLY tagged candidate first — setAttribute on a new
        // element does not strip the id off the old one, and HTML tolerates duplicate ids, so a retry loop
        // (selectVacantBedAndClickAdmission trying index 0, 1, 2...) could otherwise leave 2+ elements
        // sharing the id and have the click below silently re-select the FIRST one ever tagged instead of
        // the new candidate at `index`.
        page.evaluate("() => { document.querySelectorAll('#__bedPick').forEach(e=>e.removeAttribute('id')); }");
        Object title = page.evaluate("(a) => { const imgs=[...document.querySelectorAll('img')].filter(i => {"
                + "   const show=i.getAttribute('ng-show')||''; return show.indexOf('BedList.BedStatus=='+a.status)>=0 && i.offsetParent!==null && !i.classList.contains('ng-hide'); });"
                + " const img=imgs[a.index]; if(!img) return null; const li=img.closest('li'); if(!li) return null;"
                + " li.setAttribute('id','__bedPick'); return li.getAttribute('title'); }", java.util.Map.of("status", bedStatus, "index", index));
        if (title == null) return null;
        page.evaluate("() => { const li=document.querySelector('#__bedPick'); if(li) li.click(); }");
        waitForAngular(700);
        return title.toString();
    }

    /** Select the first OCCUPIED (red) bed. Returns its title text, or null if the board has none. */
    public String selectOccupiedBed() { return selectBedByStatus(1, 0); }

    /** Select the first VACANT (green) bed. Returns its title text, or null if the board has none. */
    public String selectVacantBed() { return selectBedByStatus(0, 0); }

    /** Select the first DISCHARGED (yellow) bed. Returns its title text, or null if the board has none. */
    public String selectDischargedBed() { return selectBedByStatus(2, 0); }

    /** Extract the bed name from a board title ({@code "Bed: DC-26\nRoom Type: DAY CARE\nWard: DAY CARE"} →
     *  {@code "DC-26"}). Returns null if the title doesn't match the expected format. */
    public static String bedNameFromTitle(String title) {
        if (title == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Bed:\\s*([^\\n]+)").matcher(title);
        return m.find() ? m.group(1).trim() : null;
    }

    /** How many distinct vacant beds to try in {@link #selectVacantBedAndClickAdmission()} before giving up.
     *  Kept small (not exhaustive): live investigation (Chrome DevTools, same bed, same click sequence)
     *  showed this is NOT a per-bed data difference — the Admission button's {@code ng-disabled} gate
     *  ({@code StatusOfBed==0?false:true}) stayed false for every vacant bed tried in one automated run, and
     *  true for the exact same bed reproduced manually. The most likely cause is session-level, not
     *  bed-level (see {@link #clickAdmission()}), so trying more beds within one broken session is unlikely
     *  to help — this stays only as a cheap safety net for genuinely bed-specific cases. */
    private static final int MAX_VACANT_BED_ATTEMPTS = 3;

    /** Select vacant beds one at a time, trying up to {@link #MAX_VACANT_BED_ATTEMPTS} of them, until one
     *  actually allows Admission (its "Admission" button becomes enabled and clicking it opens the form).
     *  Returns the bed's title text on success, or null if none of the candidates tried worked. */
    public String selectVacantBedAndClickAdmission() {
        for (int i = 0; i < MAX_VACANT_BED_ATTEMPTS; i++) {
            String bed = selectBedByStatus(0, i);
            if (bed == null) { System.out.println("selectVacantBedAndClickAdmission: no more vacant beds to try (tried " + i + ")"); return null; }
            if (clickAdmission()) return bed;
            System.out.println("selectVacantBedAndClickAdmission: bed " + i + " (" + bed.replace("\n", " | ")
                    + ") does not allow Admission — trying the next vacant bed");
        }
        return null;
    }

    /**
     * Click the (now-enabled) <b>Admission</b> button ({@code fnAdmission()}) — confirmed live only enabled
     * for a VACANT bed's toolbar (an occupied bed's toolbar has no such button). It carries the selected
     * bed's Ward straight into the IP Admission screen ({@code #/Admission}) — the SAME form/route
     * {@code com.kpj.tests.Ip.Admission} drives directly via the IP menu, so callers reuse
     * {@code com.kpj.pages.Ip.Admission}'s fill/save methods afterward. Returns true once the Admission form
     * (Save button {@code IUDAdmission();}) is ready.
     *
     * <p><b>Known environment gap (investigated live, Chrome DevTools):</b> the button's gate is
     * {@code ng-disabled="StatusOfBed==0?false:true"}. On this suite's automated login it can stay disabled
     * for every vacant bed in a run — confirmed NOT a per-bed data issue (the exact same bed that fails here
     * every time works when the identical click sequence is reproduced manually). The one consistent
     * difference found: automated login never establishes a Location/CashCounter — every run logs "Location
     * (getLocations) did not populate in time — proceeding" — while a manual session that explicitly picks
     * an Organization + Cash Counter at login has never reproduced the disabled state. Per direction, login
     * stays username/password only, so this is reported as a diagnosed environment limitation via
     * {@link #lastAdmissionDisabledReason} rather than "fixed" by guessing a counter to select.</p>
     */
    public String lastAdmissionDisabledReason = "";

    /** Screenshot taken at the moment {@link #clickAdmission()} finds the button non-clickable — for direct
     *  visual evidence of what the automated session actually looked like at that point (every MANUAL
     *  reproduction of the same click sequence has succeeded, so this exists to see the one environment
     *  where it doesn't). Null if the last call succeeded or hasn't run yet. */
    public byte[] lastFailureScreenshot;

    public boolean clickAdmission() {
        lastFailureScreenshot = null;
        // Confirmed live: selecting a SECOND bed right after a prior selection's toolbar/modal cycle can leave
        // the Admission button's ng-disabled binding a beat behind the new selection — poll briefly rather
        // than judging "not enabled yet" as "never available".
        Object clicked = page.evaluate("async () => { const find=()=>[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='fnAdmission();' && x.offsetParent!==null && !x.disabled);"
                + " let b=null; for(let i=0;i<6;i++){ b=find(); if(b) break; await new Promise(r=>setTimeout(r,300)); }"
                + " if(!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='fnAdmission();');"
                    + " if(!b) return 'button not found in DOM';"
                    + " const sc=window.angular?angular.element(b).scope():null;"
                    + " const liTagged=document.querySelector('#__bedPick');"
                    + " return 'found: visible=' + (b.offsetParent!==null) + ' disabled=' + b.disabled"
                    + "   + ' | StatusOfBed=' + (sc?JSON.stringify(sc.StatusOfBed):'(no scope)')"
                    + "   + ' | selectedBed=' + (sc&&sc.selectedBed?JSON.stringify({BedName:sc.selectedBed.BedName,BedStatus:sc.selectedBed.BedStatus}):'(none)')"
                    + "   + ' | taggedLiTitle=' + (liTagged?norm(liTagged.getAttribute('title')):'(not found)')"
                    + "   + ' | url=' + location.href; }");
            lastAdmissionDisabledReason = "Admission button " + diag + " — likely cause: the automated login never establishes a"
                    + " Location/CashCounter context (see class Javadoc); NOT a per-bed data issue (confirmed live).";
            System.out.println("GraphicalView.clickAdmission: Admission button not clickable - " + diag);
            try { lastFailureScreenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true)); }
            catch (Exception e) { System.out.println("GraphicalView.clickAdmission: screenshot failed - " + e.getMessage()); }
            return false;
        }
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDAdmission();']\") && /#\\/Admission\\b/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("GraphicalView.clickAdmission: Admission form did not open - " + e.getMessage()); return false; }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("admission");
    }

    /** How many distinct occupied beds to try in {@link #selectOccupiedBedAndClickTransferBed()} before
     *  giving up — mirrors {@link #MAX_VACANT_BED_ATTEMPTS}'s reasoning: kept small since a stuck toolbar
     *  gate, if it happens, is a session-level issue, not something more candidates fixes. */
    private static final int MAX_OCCUPIED_BED_ATTEMPTS = 3;

    /** Select occupied beds one at a time, trying up to {@link #MAX_OCCUPIED_BED_ATTEMPTS} of them, until one
     *  lets {@link #clickTransferBed()} succeed. Returns the bed's title text on success, or null. */
    public String selectOccupiedBedAndClickTransferBed() {
        for (int i = 0; i < MAX_OCCUPIED_BED_ATTEMPTS; i++) {
            String bed = selectBedByStatus(1, i);
            if (bed == null) { System.out.println("selectOccupiedBedAndClickTransferBed: no more occupied beds to try (tried " + i + ")"); return null; }
            if (clickTransferBed()) return bed;
            System.out.println("selectOccupiedBedAndClickTransferBed: bed " + i + " (" + bed.replace("\n", " | ")
                    + ") did not open Transfer Bed — trying the next occupied bed");
        }
        return null;
    }

    /**
     * Click the (now-enabled) <b>Transfer Bed</b> button ({@code TransferBed()}) on a selected OCCUPIED
     * bed's toolbar. It navigates to the SAME {@code #/addTransferBed} form
     * {@code com.kpj.tests.Ip.BedManagement_page.TransferBedList}'s "New" flow drives via the Transfer Bed
     * List screen — so callers reuse {@code com.kpj.pages.Ip.BedManagement_page.TransferBedList}'s
     * {@code autoTransferAdmittedPatient(...)} afterward, same as that standalone test. Returns true once
     * the transfer form (Save button {@code savebedtransfer()}) is ready.
     *
     * <p>Confirmed live: the selected bed's own MRN is NOT carried into the form — {@code BedTransfer.MRNO}
     * stays empty even after the navigation settles, unlike Admission's Ward carry-over. Callers still need
     * to search/pick a patient on the form itself (which {@code autoTransferAdmittedPatient} already does),
     * exactly as if they had opened this form from Transfer Bed List's own "New" button.</p>
     */
    public boolean clickTransferBed() {
        Object clicked = page.evaluate("async () => { const find=()=>[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='TransferBed()' && x.offsetParent!==null && !x.disabled);"
                + " let b=null; for(let i=0;i<6;i++){ b=find(); if(b) break; await new Promise(r=>setTimeout(r,300)); }"
                + " if(!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) {
            Object diag = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='TransferBed()'); if(!b) return 'button not found in DOM'; return 'found: visible=' + (b.offsetParent!==null) + ' disabled=' + b.disabled; }");
            System.out.println("GraphicalView.clickTransferBed: Transfer Bed button not clickable - " + diag);
            return false;
        }
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click*='savebedtransfer']\") && /#\\/addTransferBed\\b/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("GraphicalView.clickTransferBed: Transfer Bed form did not open - " + e.getMessage()); return false; }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("addtransferbed");
    }

    /** How many distinct vacant beds to try in {@link #selectVacantBedAndClickMaintenanceBed()} before
     *  giving up — same reasoning as {@link #MAX_VACANT_BED_ATTEMPTS}. */
    private static final int MAX_MAINTENANCE_BED_ATTEMPTS = 3;

    /** Select vacant beds one at a time, trying up to {@link #MAX_MAINTENANCE_BED_ATTEMPTS} of them, until
     *  one lets {@link #clickMaintenanceBed()} succeed. Returns the bed's title text on success, or null. */
    public String selectVacantBedAndClickMaintenanceBed() {
        for (int i = 0; i < MAX_MAINTENANCE_BED_ATTEMPTS; i++) {
            String bed = selectBedByStatus(0, i);
            if (bed == null) { System.out.println("selectVacantBedAndClickMaintenanceBed: no more vacant beds to try (tried " + i + ")"); return null; }
            if (clickMaintenanceBed()) return bed;
            System.out.println("selectVacantBedAndClickMaintenanceBed: bed " + i + " (" + bed.replace("\n", " | ")
                    + ") did not open Under Maintenance — trying the next vacant bed");
        }
        return null;
    }

    /**
     * Click the (now-enabled) <b>Under Maintenance</b> button ({@code MaintenanceBed()}) on a selected
     * VACANT bed's toolbar. It navigates to the SAME {@code #/add-undermaintenance} form
     * {@code com.kpj.tests.Ip.BedManagement_page.UnderMaintenance}'s "Add" flow drives via the Under
     * Maintenance list screen — so callers reuse
     * {@code com.kpj.pages.Ip.BedManagement_page.UnderMaintenance}'s
     * {@code markBedUnderMaintenance(...)} afterward, same as that standalone test. Returns true once the
     * add-maintenance form (Save button {@code IUDBedUnderMaitenance()}) is ready.
     */
    public boolean clickMaintenanceBed() {
        Object clicked = page.evaluate("async () => { const find=()=>[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='MaintenanceBed()' && x.offsetParent!==null && !x.disabled);"
                + " let b=null; for(let i=0;i<6;i++){ b=find(); if(b) break; await new Promise(r=>setTimeout(r,300)); }"
                + " if(!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) {
            Object diag = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='MaintenanceBed()'); if(!b) return 'button not found in DOM'; return 'found: visible=' + (b.offsetParent!==null) + ' disabled=' + b.disabled; }");
            System.out.println("GraphicalView.clickMaintenanceBed: Under Maintenance button not clickable - " + diag);
            return false;
        }
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click*='IUDBedUnderMaitenance']\") && /#\\/add-undermaintenance\\b/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("GraphicalView.clickMaintenanceBed: Under Maintenance form did not open - " + e.getMessage()); return false; }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("add-undermaintenance");
    }

    // ---- Bed Details modal --------------------------------------------------

    /** Click the (now-enabled) <b>View Details</b> button ({@code GetBedDetailsView()}) and wait for the
     *  Bed Details modal to open. Returns true once it is visible. */
    public boolean openViewDetails() {
        Object clicked = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='GetBedDetailsView()' && x.offsetParent!==null && !x.disabled);"
                + " if(!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) return false;
        try {
            page.waitForFunction("() => { const m=document.querySelector('.BedDetailsPopup'); return !!m && m.classList.contains('in'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("GraphicalView.openViewDetails: Bed Details modal did not open - " + e.getMessage()); return false; }
        waitForAngular(500);
        return true;
    }

    /** Read the <b>Bed Information</b> tab (shown by default when the modal opens). */
    public String readBedInformation() {
        Object r = page.evaluate("() => { const g=(ng)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'], textarea[ng-model='\"+ng+\"']\"); return e?e.value:''; };"
                + " return { bedName:g('BedDetailsList.BedName'), ward:g('BedDetailsList.WardName'), className:g('BedDetailsList.ClassName'),"
                + "   roomName:g('BedDetailsList.RoomName'), amenities:g('BedDetailsList.Amenities') }; }");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = r instanceof java.util.Map ? (java.util.Map<String, Object>) r : java.util.Collections.emptyMap();
        return "Bed " + m.getOrDefault("bedName", "") + " | Ward " + m.getOrDefault("ward", "")
                + " | Class " + m.getOrDefault("className", "") + " | Room " + m.getOrDefault("roomName", "")
                + " | Amenities " + m.getOrDefault("amenities", "");
    }

    /** Click the <b>Admission Details</b> tab and wait for it to become active. */
    public boolean openAdmissionDetailsTab() {
        Object clicked = page.evaluate("() => { const t=document.querySelector('a[data-target=\"#AdmissionDetails\"]'); if(!t || t.offsetParent===null) return false; t.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) return false;
        try {
            page.waitForFunction("() => { const p=document.querySelector('#AdmissionDetails'); return !!p && p.classList.contains('active'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("GraphicalView.openAdmissionDetailsTab: tab did not activate - " + e.getMessage()); return false; }
        waitForAngular(500);
        return true;
    }

    /** Read the <b>Admission Details</b> tab (Admission Date/Time, IPD No, Doctor, Patient Name, MRN,
     *  Mobile, Referring Doctor). */
    public String readAdmissionDetails() {
        Object r = page.evaluate("() => { const g=(ng)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'], textarea[ng-model='\"+ng+\"']\"); return e?e.value.trim():''; };"
                + " return { date:g('BedDetailsList.AdmissionDate'), time:g('BedDetailsList.AdmissionTime'), ipd:g('BedDetailsList.IPDNo'),"
                + "   doctor:g('BedDetailsList.DocName'), patient:g('BedDetailsList.PatientName'), mrn:g('BedDetailsList.MRNo'),"
                + "   mobile:g('BedDetailsList.MobileNo'), referring:g('BedDetailsList.RefferingName') }; }");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = r instanceof java.util.Map ? (java.util.Map<String, Object>) r : java.util.Collections.emptyMap();
        return "Admitted " + m.getOrDefault("date", "") + " " + m.getOrDefault("time", "") + " | IPD " + m.getOrDefault("ipd", "")
                + " | Doctor " + m.getOrDefault("doctor", "") + " | Patient " + m.getOrDefault("patient", "")
                + " | MRN " + m.getOrDefault("mrn", "") + " | Mobile " + m.getOrDefault("mobile", "")
                + " | Referring " + m.getOrDefault("referring", "");
    }

    /** Click the <b>Rate Details</b> tab and wait for it to become active. */
    public boolean openRateDetailsTab() {
        Object clicked = page.evaluate("() => { const t=document.querySelector('a[data-target=\"#RoomTariff\"]'); if(!t || t.offsetParent===null) return false; t.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) return false;
        try {
            page.waitForFunction("() => { const p=document.querySelector('#RoomTariff'); return !!p && p.classList.contains('active'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("GraphicalView.openRateDetailsTab: tab did not activate - " + e.getMessage()); return false; }
        waitForAngular(500);
        return true;
    }

    /** On the (now-active) Rate Details tab, select the first real <b>Pricing Policy</b> option
     *  ({@code select[ng-model='BedDetailsList.TariffId']}). Returns the option text picked, or "" if the
     *  select has no real option. */
    public String selectPricingPolicy() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sel=document.querySelector(\"select[ng-model='BedDetailsList.TariffId']\"); if(!sel) return '';"
                + " const i=[...sel.options].findIndex(o=>o.value && norm(o.textContent) && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent); }");
        waitForAngular(1500);
        return r == null ? "" : r.toString();
    }

    /** Read the Rate Details table (Service Name / Unit / Purchase Price) that loads under the selected
     *  Pricing Policy. Returns a summary — row count and the header text, since the actual rates are
     *  data-dependent on the policy picked. */
    public String readRateDetails() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const panel=document.querySelector('#RoomTariff'); const table=panel? panel.querySelector('table') : null; if(!table) return 'no rate table';"
                + " const rows=[...table.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(norm(r.textContent)));"
                + " return 'rows=' + rows.length + (rows.length ? ' | first: ' + norm(rows[0].textContent).slice(0,120) : ''); }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>OK</b> ({@code clearform()}) to close the Bed Details modal. Returns true once it is closed. */
    public boolean clickOk() {
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const modal=document.querySelector('.BedDetailsPopup'); if(!modal) return;"
                + " const b=[...modal.querySelectorAll('button')].find(x=>/^ok$/i.test(norm(x.textContent))); if(b) b.click(); }");
        try {
            page.waitForFunction("() => { const m=document.querySelector('.BedDetailsPopup'); return !m || !m.classList.contains('in'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("GraphicalView.clickOk: modal did not close - " + e.getMessage()); return false; }
        waitForAngular(500);
        return true;
    }
}
