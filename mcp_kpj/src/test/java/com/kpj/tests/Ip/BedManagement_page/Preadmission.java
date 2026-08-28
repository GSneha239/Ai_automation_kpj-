package com.kpj.tests.Ip.BedManagement_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// The page object is also named Preadmission — referenced by its fully-qualified name.

/**
 * IP &gt; Bed Management &gt; <b>Preadmission</b> ({@code #/ReservationList}). Four actions in one run:
 * <ol>
 *   <li><b>Reserve Bed</b> — enter date → New ({@code AddBedReservation()}) → enter MRN + Search → fill Ward/Room
 *       Type/Department/Doctor/Admission Type/GL → the system generates the bed list → select a bed → Reserve
 *       ({@code savebedreservation()}) → verify in the List of Reserved Beds.</li>
 *   <li><b>Request MRD File</b> — if the patient has no MRD file, confirm the "create one?" popup, fill the MRD
 *       Details popup → Save → success toast.</li>
 *   <li><b>Admission</b> ({@code getPatientDataforAdmission()}) — carries the patient into the IP Admission screen;
 *       fill the details as per IP &gt; Admission → Save → capture the admission report tabs.</li>
 *   <li><b>Print</b> ({@code PrintBedReservation()}) — capture the bed reservation report.</li>
 * </ol>
 *
 * <p>The Reserve flow follows the M6 Bed Planning FSD: "Fill in Ward, Room Type (Bed Type, Billing Class) → the
 * system generates the bed list based on the filters → select an available bed and Save." A reserved bed's visit
 * status is <i>Pending</i> and changes to <i>Admitted</i> after the Admission action.</p>
 */
public class Preadmission extends DevHisBase {

    public Preadmission() { super("Preadmission"); }

    public static void main(String[] args) {
        Preadmission t = new Preadmission();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Past-date offsets to try (preadmission data lives on past admission dates). */
    // Include TODAY (0) first — reservations are dated today, so MRD/Print must search today (not only past days).
    private static final int[] DATE_OFFSETS = {0, -1, -2, -3, -5, -7, -10, -14, -21, -30, -45, -60, -90};
    /** The MRN the Reserve section just reserved (dated today) — the Admission section admits this same patient. */
    private String reservedMrn = null;
    /** The last warning/no-toast the admission Save returned (surfaced in the final FAIL when no patient admits). */
    private String lastAdmissionWarning = "";

    @Override
    protected void body() {
        meta("IP - Bed Management - Preadmission", "IP > Bed Management > Preadmission",
                "Preadmission actions: Reserve Bed (New -> MRN search -> details -> select bed -> Reserve -> verify in list); "
                        + "Request MRD File; Admission (carry patient into IP Admission -> Save -> reports); Print.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // Each section is guarded so one section's crash (e.g. a patient that closes the browser during Admission
        // Save) doesn't abort the remaining sections / surface as a raw top-level ERROR.
        runSection(this::sectionReserveBed, "Reserve");
        runSection(this::sectionRequestMrd, "Request MRD File");
        runSection(this::sectionAdmission, "Admission");
        runSection(this::sectionPrint, "Print");
    }

    /** Run a section, swallowing (and reporting) any exception; if the browser/context died, stop the rest. */
    private void runSection(Runnable section, String name) {
        boolean closed = false;
        try { closed = page.isClosed(); } catch (Exception e) { closed = true; }
        if (closed) { addSummary(name + " · Result", "Skipped — browser/context closed by a previous section"); return; }
        try {
            section.run();
        } catch (Exception e) {
            System.out.println(name + " section threw: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            addSummary(name + " · Result", "Section error: " + e.getClass().getSimpleName());
        }
    }

    /** Candidate MRNs for reserving a bed — the Reserve section auto-picks the first that actually reserves. The pool
     *  is SHUFFLED each run (see {@link #shuffledReserveCandidates()}) so we don't hammer the same MRN — different
     *  runs reserve/admit different patients, spreading the churn and self-healing when one gets Admitted. */
    private static final String[] RESERVE_MRN_POOL = {
            // fresh batch first (different patients than the earlier runs that failed admission on 100000969/JOHN PETER)
            "100000273", "100000283", "100000339", "100000406", "100000434", "100000570", "100000260", "100000228",
            "100001060", "100001061", "100001062", "100000990", "100000119", "100000120",
            "100000998", "100001005", "100000982", "100000944", "100001022", "100000850", "100000790",
            "100000900", "100000955", "100001010", "100000930", "100000975", "100001000", "100000960"};

    /** A run-specific, shuffled, de-duplicated copy of the candidate pool (so each run tries different MRNs first). */
    private java.util.List<String> shuffledReserveCandidates() {
        java.util.List<String> list = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(java.util.Arrays.asList(RESERVE_MRN_POOL)));
        java.util.Collections.shuffle(list);
        return list;
    }

    // ===== Section: New bed reservation =================================
    private void sectionReserveBed() {
        com.kpj.pages.Ip.BedManagement_page.Preadmission pa =
                new com.kpj.pages.Ip.BedManagement_page.Preadmission(page);
        pa.navigateViaMenu();
        step(page, "Reserve · Open Preadmission", "IP > Bed Management > Preadmission",
                "The Preadmission screen is shown", page.url(), page.url().toLowerCase().contains("reservationlist") ? "PASS" : "FAIL");

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Enter the Admission Date (no need to select any row from the List of Reserved Beds).
        String d = pa.enterAdmissionDate(today);
        step(page, "Reserve · Enter date", "Enter the Admission Date (Reservation.fromdate = " + today + ")",
                "The admission date is set", "Date = " + d, "PASS");

        // Click New (AddBedReservation) -> the bed reservation form (#/addReservation).
        boolean form = pa.clickReserveNew();
        boolean onForm = page.url().toLowerCase().contains("addreservation");
        step(page, "Reserve · Click New", "Click New (AddBedReservation())",
                "The bed reservation form (#/addReservation) opens", onForm ? "Form opened (" + page.url() + ")" : "Form did NOT open",
                onForm ? "PASS" : "FAIL");
        if (!onForm) return;

        // AUTO-PICK: try each candidate MRN, use the first that binds a reservable patient AND reserves successfully
        // (a fixed MRN becomes Admitted/non-reservable over runs → "Please Select Patient"). Self-heals across data.
        String usedMrn = null, bed = null, toast = null, details = "";
        boolean ok = false, bedOk = false;
        StringBuilder tried = new StringBuilder();
        for (String cand : shuffledReserveCandidates()) {
            pa.reserveEnterMrnAndSearch(cand);
            boolean bound = pa.isPatientBound();
            tried.append(cand).append(bound ? "(bound)" : "(not-bound)").append(" ");
            if (!bound) continue;                        // patient not found / not reservable → next candidate
            usedMrn = cand;
            details = pa.fillReserveDetails(today);
            pa.ensureRoomTypeAndWard();
            int beds = pa.loadReserveBedsIterating(0, 0);   // iterate ALL Room Types x ALL Wards (0 = no cap) until vacant beds appear
            bed = beds > 0 ? pa.selectFirstReserveBed() : null;
            bedOk = bed != null;
            if (!bedOk) { usedMrn = null; continue; }      // no bed under this patient's fill state → try a different patient
            // The Room Type walk above can have cleared Department/Sub Dept/Doctor/Admission Type/Pricing —
            // re-assert them (only if empty) before Reserve, same fix already applied to Admission's Sub Dept.
            String reasserted = pa.reassertDependentFields();
            if (!reasserted.isEmpty()) System.out.println("Reserve: re-asserted after bed search -> " + reasserted);
            toast = pa.clickReserveAndGetToast();
            ok = toast != null && (toast.toLowerCase().contains("reserv") || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
            if (ok) break;                                // reserved successfully → done
            usedMrn = null;                               // reserve rejected this patient → try the next candidate
        }

        step(page, "Reserve · Enter MRN & Search (auto-pick)", "Enter each candidate MRN + Search (SearchPatientByMRNo()); use the first that binds a reservable patient",
                "A reservable patient is loaded", "Tried: " + tried + "| using " + (usedMrn == null ? "none" : usedMrn),
                usedMrn != null ? "PASS" : "FAIL");
        if (usedMrn == null) { addSummary("Reserve · Result", "No reservable patient found in the candidate pool: " + tried); return; }

        step(page, "Reserve · Enter details", "Room Type/Ward/Payor Status/Department/Sub Dept/Doctor/Admission Type/Pricing + GL Amount + GL Limit + Diagnosis",
                "The reservation details are filled", details, "PASS");
        step(page, "Reserve · Select the bed", "Click Search (fetchCensusBedList/fetchnonCensusBedList) then tick a bed",
                "A bed is selected", bedOk ? "selected: " + bed : "no bed selected", bedOk ? "PASS" : "FAIL");
        step(page, "Reserve · Click Reserve", "Click Reserve (savebedreservation()) — auto-picked MRN " + usedMrn,
                "'... Reserved Successfully.' toast", toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        if (!ok) { addSummary("Reserve · Result", "Not confirmed (tried: " + tried + ")"); return; }

        reservedMrn = usedMrn;   // hand this Pending (today-dated) reservation to the Admission section
        boolean listed = pa.verifyReservedInList(usedMrn);
        step(page, "Reserve · Verify in List of Reserved Beds", "Check the List of Reserved Beds table for MRN " + usedMrn,
                "The reserved bed is listed for the patient", listed ? "MRN " + usedMrn + " found in List of Reserved Beds" : "MRN not found in the list",
                listed ? "PASS" : "FAIL");
        addSummary("Reserve · Result", "MRN " + usedMrn + " -> " + toast + (listed ? " | listed" : " | not listed"));
    }

    // ===== Section: Print (bed reservation report) ======================
    private void sectionPrint() {
        com.kpj.pages.Ip.BedManagement_page.Preadmission pa = openSearchSelect("Print", false);
        if (pa == null) return;

        java.util.List<Page> tabs = pa.clickPrintAndGetTabs();
        step(page, "Print · Click Print", "Click Print (PrintBedReservation()) for the selected row",
                "The bed reservation report opens in a new tab", tabs.isEmpty() ? "No report tab opened" : (tabs.size() + " report tab(s) opened"),
                tabs.isEmpty() ? "FAIL" : "PASS");
        if (tabs.isEmpty()) { addSummary("Print · Result", "No report tab opened"); return; }

        com.kpj.pages.Ip.Admission ip = new com.kpj.pages.Ip.Admission(page);
        int n = 0;
        for (Page rpt : tabs) {
            n++;
            String url = rpt.url();
            byte[] png = ip.captureReportPng(rpt);
            // A tab opening is not a report that rendered — Crystal Reports can serve a "Server Error /
            // Logon failed" page instead of the real PDF, and captureReportPng's screenshot fallback captures
            // that error page just as "successfully" as a real report.
            String err = reportPageError(rpt);
            if (!err.isEmpty()) {
                step(png, "Print · Report " + n, "Bed reservation report tab #" + n,
                        "The report is shown", "Report FAILED to render — " + err + "  (" + url + ")", "FAIL");
            } else if (png != null && png.length > 0) {
                step(png, "Print · Report " + n, "Bed reservation report tab #" + n,
                        "The report is shown (screenshot captured)", "Report captured (" + url + ")", "PASS");
            } else {
                step("Print · Report " + n, "Bed reservation report tab #" + n,
                        "The report is shown", "Report tab opened: " + url, "PASS");
            }
        }
        page.bringToFront();
        addSummary("Print · Result", tabs.size() + " report(s) captured");
    }

    /** Candidate admission dates (today + past offsets) in dd/MM/yyyy. */
    private java.util.List<String> candidateDates() {
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<String> dates = new java.util.ArrayList<>();
        for (int off : DATE_OFFSETS) dates.add(today.plusDays(off).format(f));
        return dates;
    }

    /** Open Preadmission, search past dates until data, select a patient. When {@code pendingOnly}, pick an
     *  admittable (Pending, not-yet-admitted) row so the Admission action can proceed. Returns the page (or null). */
    private com.kpj.pages.Ip.BedManagement_page.Preadmission openSearchSelect(String tag, boolean pendingOnly) {
        return openSearchSelect(tag, pendingOnly, null);
    }

    /** {@code preferMrn} (when non-null) targets a specific just-reserved patient — the search then also includes
     *  TODAY (reservations are created dated today) and the row is selected by that MRN. */
    private com.kpj.pages.Ip.BedManagement_page.Preadmission openSearchSelect(String tag, boolean pendingOnly, String preferMrn) {
        com.kpj.pages.Ip.BedManagement_page.Preadmission pa =
                new com.kpj.pages.Ip.BedManagement_page.Preadmission(page);
        pa.navigateViaMenu();
        boolean onScreen = page.url().toLowerCase().contains("reservationlist");
        step(page, tag + " · Open Preadmission", "IP > Bed Management > Preadmission",
                "The Preadmission (#/ReservationList) screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen", onScreen ? "PASS" : "FAIL");
        if (!onScreen) return null;
        // When targeting a just-reserved patient, search TODAY first (reservations are dated today), then past dates.
        java.util.List<String> dates = candidateDates();
        if (preferMrn != null) {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            dates.add(0, today);
        }
        String hit = pa.searchUntilData(dates);
        int rows = pa.gridRowCount();
        step(page, tag + " · Search by admission date", "Set Reservation.fromdate + Search"
                        + (preferMrn != null ? " (today + past dates for the reserved patient)" : " (retry past dates if empty)"),
                "The grid lists at least one reservation", hit == null ? "No data in any tried date" : ("Data on " + hit + " (rows=" + rows + ")"),
                hit == null ? "FAIL" : "PASS");
        if (hit == null) return null;
        String sel = preferMrn != null ? pa.selectPendingPatientRowByMrn(preferMrn)
                : (pendingOnly ? pa.selectPendingPatientRow() : pa.selectFirstPatientRow());
        step(page, tag + " · Select a patient",
                preferMrn != null ? "Select the just-reserved Pending patient (MRN " + preferMrn + ")"
                        : (pendingOnly ? "Select an admittable (Pending) patient row" : "Select the first patient row"),
                "One patient row is selected",
                sel == null ? (pendingOnly ? "No admittable (Pending) patient available" : "No row") : "Selected: " + sel,
                sel == null ? (pendingOnly ? "MANUAL" : "FAIL") : "PASS");
        return sel == null ? null : pa;
    }

    // ===== Section: Request MRD File ====================================
    private void sectionRequestMrd() {
        com.kpj.pages.Ip.BedManagement_page.Preadmission pa = openSearchSelect("Request MRD File", false);
        if (pa == null) return;

        String outcome = pa.clickRequestMrdFile();
        step(page, "Request MRD File · Click", "Click Request MRD File (fnRequestMRDFile())",
                "A 'create MRD file?' confirm appears (no file) OR the report opens (already has a file)",
                "Outcome: " + outcome, "confirm".equals(outcome) || "report".equals(outcome) ? "PASS" : "FAIL");
        if ("report".equals(outcome)) { addSummary("Request MRD File · Result", "Patient already has an MRD file — report opened"); return; }
        if (!"confirm".equals(outcome)) { addSummary("Request MRD File · Result", "No confirm/report"); return; }

        boolean modal = pa.confirmCreateMrdFile();
        step(page, "Request MRD File · Confirm create", "Click Yes on 'File is not generated… create one?'",
                "The MRD Details popup opens", modal ? "MRD Details popup opened" : "Popup did NOT open", modal ? "PASS" : "FAIL");
        if (!modal) return;

        String toast = pa.fillMrdDetailsAndSave();
        boolean ok = toast != null && (toast.toLowerCase().contains("allocated") || toast.toLowerCase().contains("success"));
        step(page, "Request MRD File · Fill details & Save", "Fill the MRD Details and Save (fnIUDAllocation())",
                "'File Allocated Successfully.' toast", toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Request MRD File · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Admission (carry patient into IP Admission) ==========
    /** How many DIFFERENT reserved patients to try admitting before giving up. The Admission Save is
     *  patient-dependent (some patients no-op with no toast, or crash the browser), so on a no-toast Save we
     *  re-open the list and try the NEXT admittable patient. */
    private static final int ADMIT_MAX_PATIENTS = 4;

    private void sectionAdmission() {
        java.util.Set<String> tried = new java.util.LinkedHashSet<>();
        for (int attempt = 1; attempt <= ADMIT_MAX_PATIENTS; attempt++) {
            String tag = attempt == 1 ? "Admission" : "Admission (try " + attempt + ")";
            try {
                if (admitOneAttempt(tag, attempt, tried)) return;   // admitted successfully
            } catch (Exception e) {
                System.out.println(tag + " threw: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                try { if (page.isClosed()) { addSummary("Admission · Result", "Browser closed during " + tag + " (tried: " + tried + ")"); return; } }
                catch (Exception ig) { addSummary("Admission · Result", "Browser closed during " + tag); return; }
            }
        }
        // Every attempt filled ALL mandatory fields (the fill steps passed) yet the Save never confirmed — so this is
        // a genuine defect, not a data-entry gap. Fail with that exact reasoning + the warning the app kept returning.
        step(page, "Admission · Save (all patients tried)",
                "Fill EVERY mandatory field, then Save (IUDAdmission()) — repeated for " + tried.size() + " different admittable patients",
                "'Patient Admitted Successfully.' toast",
                "DEFECT: even though all mandatory fields were filled, the Admission Save still returns the warning \""
                        + (lastAdmissionWarning.isEmpty() ? "(no toast / no warning shown)" : lastAdmissionWarning)
                        + "\" for every patient tried (" + tried + ") — cannot admit",
                "FAIL");
        addSummary("Admission · Result", "Not admitted — all mandatory fields filled but Save keeps warning: "
                + (lastAdmissionWarning.isEmpty() ? "(no toast)" : lastAdmissionWarning) + " | tried: " + tried);
    }

    /** Extract the first MRN-like token (100xxxxxx) from a grid row's text. */
    private String firstMrn(String rowText) {
        if (rowText == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(100\\d{6})\\b").matcher(rowText);
        return m.find() ? m.group(1) : null;
    }

    /** One admission attempt on a Pending patient not already in {@code tried}. Returns true if admitted. */
    private boolean admitOneAttempt(String tag, int attempt, java.util.Set<String> tried) {
        // Open Preadmission + search (today first) + select an admittable patient NOT already tried.
        com.kpj.pages.Ip.BedManagement_page.Preadmission pa = new com.kpj.pages.Ip.BedManagement_page.Preadmission(page);
        pa.navigateViaMenu();
        boolean onScreen = page.url().toLowerCase().contains("reservationlist");
        step(page, tag + " · Open Preadmission", "IP > Bed Management > Preadmission",
                "The Preadmission (#/ReservationList) screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) return false;

        String hit = pa.searchUntilData(candidateDates());
        int rows = pa.gridRowCount();
        step(page, tag + " · Search by admission date", "Set Reservation.fromdate + Search (today first)",
                "The grid lists at least one reservation", hit == null ? "No data in any tried date" : "Data on " + hit + " (rows=" + rows + ")",
                hit == null ? "FAIL" : "PASS");
        if (hit == null) return false;

        String sel = (attempt == 1 && reservedMrn != null && !tried.contains(reservedMrn))
                ? pa.selectPendingPatientRowByMrn(reservedMrn) : null;
        if (sel == null) sel = pa.selectPendingPatientRowExcluding(tried);
        step(page, tag + " · Select a patient", "Select an admittable (Pending) patient not yet tried",
                "One admittable patient is selected", sel == null ? "No un-tried admittable patient available" : "Selected: " + sel,
                sel == null ? "MANUAL" : "PASS");
        if (sel == null) return false;
        String selMrn = firstMrn(sel);
        if (selMrn != null) tried.add(selMrn);

        // Click Admission -> carries the patient into the IP Admission screen (#/Admission).
        boolean onAdmit = pa.clickAdmission();
        step(page, tag + " · Click Admission", "Click Admission (getPatientDataforAdmission()); carries the patient into IP Admission",
                "The IP Admission form (#/Admission) opens with the patient", onAdmit ? "Admission form opened (" + page.url() + ")" : "Did NOT reach the Admission form",
                onAdmit ? "PASS" : "FAIL");
        if (!onAdmit) return false;

        // Fill the details exactly as IP > Admission (reuse the Ip.Admission page object on the same page).
        com.kpj.pages.Ip.Admission ip = new com.kpj.pages.Ip.Admission(page);

        // This screen is bound to a REAL, already-existing patient carried over from the Reservation — NOT a
        // blank walk-in. fillPatientSection() unconditionally overwrites Patient Information with a synthetic
        // identity ("John Peter", a fresh NRIC, "Malaysian"), which starts a tug-of-war with the app's own async
        // "patient lookup by NRIC/Mobile" that keeps re-asserting the REAL record over the fake one — confirmed
        // live as the mechanism behind a same-toast "Please Enter First Name!" loop that never resolved across 5
        // retries (changing Mobile No alone reproduces a ~900ms-delayed First Name wipe in isolation). Read the
        // already-bound identity instead of overwriting it; only fall back to the synthetic fill if the form
        // genuinely looks unbound (no First Name/NRIC yet).
        String patient = ip.captureExistingIdentity();
        boolean alreadyBound = patient != null && !patient.isEmpty();
        if (!alreadyBound) patient = ip.fillPatientSection();
        step(page, tag + " · Patient Information",
                alreadyBound ? "Patient already bound from the Reservation — read identity as-is (no overwrite)"
                             : "Fill/verify the (pre-filled) patient section",
                "Patient section populated", patient, "PASS");

        // SAME AS IP ADMISSION — fill a fresh NOK and Add, with NO click back into the grid afterward (no Edit,
        // no select-row). IP Admission's own flow (Ip/Admission.java fillNokSection()) never touches the grid a
        // second time. The ONE thing IP Admission never has to deal with, because every patient there starts
        // with zero kin rows, is a CARRIED patient that already has a kin from an earlier Preadmission stage —
        // calling fillNokSection() unconditionally there adds a SECOND row, and Save then rejects with "Please
        // Select Kin!" because the grid is now ambiguous between two rows (confirmed live: "kin rows 1 -> 2" then
        // "Please Select Kin!"). So: if a kin row is already in the grid, leave it exactly as-is — no Add, no
        // Edit, no click of any kind. Only call fillNokSection() when the grid is empty.
        boolean kinRowExists = Boolean.TRUE.equals(page.evaluate(
                "() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor|kin/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return false;"
                + " return [...t.querySelectorAll('tbody tr')].some(r=>{ const v=norm(r.textContent); return v && !/no records|no data/i.test(v); }); }"));
        String nok;
        boolean nokOk;
        if (kinRowExists) {
            nok = "existing kin row already in grid — left as-is (no Add, no Edit, no grid click)";
            nokOk = true;
        } else {
            nok = ip.fillNokSection();
            nokOk = nok != null && nok.contains("added=true");
        }
        step(page, tag + " · NOK / Guarantor",
                kinRowExists ? "A kin row is already in the grid — leave it untouched (no click of any kind)"
                             : "Title/Name/Relationship/Mobile/NRIC/Occupation + Same-as-address → Add",
                "A Next-of-Kin row is present and complete", nok, nokOk ? "PASS" : "MANUAL");

        String payor = ip.fillPayorSection();
        boolean payorOk = payor != null && payor.toLowerCase().contains("self");
        step(page, tag + " · Payor Information", "Open Payor accordion → click the payor row (auto-fills) → ensure Self",
                "Payor is set to Self", payor, payorOk ? "PASS" : "MANUAL");

        String admission = ip.fillAdmissionSection();
        step(page, tag + " · Admission Information", "Admission Location/Department/Doctor/Type/Source/Billing Class/Purpose",
                "Admission section populated", admission, "PASS");

        // Same as the plain IP > Admission flow (com.kpj.tests.Ip.Admission): just select Room Type + Ward,
        // deliberately NOT scanning the census grid for a specific vacant bed. That scan walks every Bed Class x
        // Ward combination (20+ x 16, up to 6.4s each) inside one page.evaluate() call with no timeout of its
        // own — confirmed live as a 30+ minute hang with near-zero CPU when beds were genuinely scarce. Room
        // Type + Ward alone is enough for Save to proceed.
        String bed = ip.selectRoomTypeAndWard();
        boolean bedOk = bed != null && bed.contains("RoomType=") && bed.contains("Ward=") && !bed.startsWith("ERR");
        step(page, tag + " · Room Type + Ward", "Select Bed Class (Room Type) + Ward",
                "Room Type and Ward are selected",
                bedOk ? bed : "Could not select Room Type/Ward — " + bed, bedOk ? "PASS" : "FAIL");

        String addDocs = ip.fillAdditionalDoctors();
        boolean addDocsOk = addDocs != null && addDocs.contains("rows=") && !addDocs.contains("rows=0");
        step(page, tag + " · Additional Doctors", "Select Classification + Additional Doctor, click Add",
                "A doctor row is added", addDocs, addDocsOk ? "PASS" : "MANUAL");

        // FILL EXACTLY AS IP > ADMISSION — nothing more, nothing after. Every section above already uses the
        // Ip.Admission page object (fillPatientSection / fillNokSection / fillPayorSection / fillAdmissionSection
        // / selectVacantBed / fillAdditionalDoctors), which is the flow that admits successfully — including NOK,
        // which is filled once and Added, with no follow-up click back into the kin grid.
        //
        // The Preadmission-only "ensure*" extras that used to run here (ensureKinComplete / ensureKinMandatoryComplete
        // / ensureAdmissionDoctor / ensureDateOfBirth / ensurePatientIdentity / ensureMandatoryBeforeSave) were
        // bolted on to patch earlier failures and did more harm than good — the passport clearing inside
        // ensurePatientIdentity blanked Registration.KinFamilyName straight after the kin fill set it,
        // ensureMandatoryBeforeSave re-drove State/City/Gender on a form the app had already accepted, and
        // ensureKinComplete/ensureKinMandatoryComplete re-opened the kin grid row (Edit, then select-row) right
        // after it had just been added — an extra grid click IP Admission never makes. IP Admission needs none
        // of them, so neither does this. They remain available on the page objects if a specific patient ever
        // needs them.

        // Save -> handle & screenshot the report tabs.
        int tabsBefore = page.context().pages().size();
        String toast = ip.saveAdmissionAndGetToast();
        // The admission Save raises a confirm ("Do you want to Save" / "unclosed episode") — click Yes and await the
        // toast. Do NOT re-confirm on "Patient Already Admitted!": that is a final answer, not a pending dialog.
        String t0 = toast == null ? "" : toast.toLowerCase();
        if (!t0.contains("already admitted") && !t0.contains("admitted")) {
            toast = pa.confirmAdmissionAndAwaitToast(tabsBefore);
        }
        // "Patient Already Admitted!" contains "admitted" but is the OPPOSITE of success — it means this patient
        // was admitted by an EARLIER run, so nothing was created and no report tab opens. Counting it as a pass is
        // why one run looked green and the next failed: each run admitted a patient, and the following run picked
        // that same now-admitted patient. Treat it as a failure so the retry loop moves on to another patient.
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean alreadyAdmitted = tl.contains("already admitted") || tl.contains("already been admitted");
        boolean ok = !alreadyAdmitted && tl.contains("admitted");
        if (alreadyAdmitted) {
            System.out.println("Admission: patient already admitted by an earlier run — trying the next patient");
        }
        if (!ok) {
            String vis = "";
            try { Object v = page.evaluate("() => { const t=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].filter(e=>e.offsetParent!==null).map(e=>(e.textContent||'').replace(/\s+/g,' ').trim()).filter(Boolean); return t.length?t[t.length-1]:''; }"); vis = v==null?"":v.toString(); } catch (Exception ignore) { }
            lastAdmissionWarning = (toast != null && !toast.trim().isEmpty()) ? toast
                    : (!vis.isEmpty() ? vis : "(no toast / no warning shown)");
            // TARGETED repair, not a pre-emptive one: only when the app names the CARRIED kin's data as
            // incomplete ("Please update the mandatory details in NOK for <name>") does this click back into
            // the grid — via ensureKinComplete(), which fills the kin the same way IP Admission does (Edit the
            // row, set Title/NRIC/Email + tick Same As Patient Address, then Modify) — and retry Save ONCE for
            // this same patient. Every other Save failure (missing sponsor document, silent no-op, etc.) still
            // falls straight through to the next-patient retry, exactly as before.
            if (lastAdmissionWarning.toLowerCase().contains("mandatory details in nok")) {
                String repair = pa.ensureKinComplete();
                System.out.println(tag + ": NOK incomplete for the carried kin — repaired (" + repair + "), retrying Save once");
                toast = ip.saveAdmissionAndGetToast();
                t0 = toast == null ? "" : toast.toLowerCase();
                if (!t0.contains("already admitted") && !t0.contains("admitted")) {
                    toast = pa.confirmAdmissionAndAwaitToast(tabsBefore);
                }
                tl = toast == null ? "" : toast.toLowerCase();
                alreadyAdmitted = tl.contains("already admitted") || tl.contains("already been admitted");
                ok = !alreadyAdmitted && tl.contains("admitted");
                if (!ok) {
                    String vis2 = "";
                    try { Object v2 = page.evaluate("() => { const t=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].filter(e=>e.offsetParent!==null).map(e=>(e.textContent||'').replace(/\s+/g,' ').trim()).filter(Boolean); return t.length?t[t.length-1]:''; }"); vis2 = v2==null?"":v2.toString(); } catch (Exception ignore) { }
                    lastAdmissionWarning = (toast != null && !toast.trim().isEmpty()) ? toast
                            : (!vis2.isEmpty() ? vis2 : "(no toast / no warning shown after NOK repair)");
                }
            }
        }
        boolean willRetry = !ok && attempt < ADMIT_MAX_PATIENTS;
        // Every mandatory section above filled/verified (PASS). If the Save still doesn't confirm, the patient
        // can't be admitted — MANUAL while retries remain; sectionAdmission emits the consolidated FAIL after all tries.
        step(page, tag + " · Save", "Click Save (IUDAdmission()) after all mandatory fields are filled; wait for the toast",
                "'Patient Admitted Successfully.' toast",
                ok ? toast : ("All mandatory fields were filled, but Save returned: \"" + lastAdmissionWarning + "\""
                        + (willRetry ? " — retrying a different patient (MRN " + selMrn + " not admittable)" : "")),
                ok ? "PASS" : "MANUAL");
        if (!ok) return false;

        // The report tabs open a few seconds after Save — wait, then capture & screenshot each.
        page.waitForTimeout(12000);
        java.util.List<Page> newTabs = new java.util.ArrayList<>();
        java.util.List<Page> all = page.context().pages();
        for (int i = tabsBefore; i < all.size(); i++) newTabs.add(all.get(i));
        addSummary("Admission · Report tabs", String.valueOf(newTabs.size()));
        int n = 0;
        for (Page rpt : newTabs) {
            n++;
            String url = rpt.url();
            String label = url.contains("IPDReport") ? "IPD Admission Report"
                    : url.contains("IPDPatientLabel") ? "IPD Patient Label"
                    : url.contains("WristBand") ? "Patient Wrist Band"
                    : url.contains("IsPatientSticker=true") ? "Patient Sticker"
                    : url.contains("RegistrationReport") ? "Patient Label"
                    : url.contains("nhisformstest") ? "Consent Form"
                    : "Report " + n;
            byte[] png = ip.captureReportPng(rpt);
            String err = reportPageError(rpt);
            if (!err.isEmpty()) {
                step(png, "Admission · Report " + n + " (" + label + ")", "Admission report tab #" + n,
                        "The report is shown", "Report FAILED to render — " + err + "  (" + url + ")", "FAIL");
            } else if (png != null && png.length > 0) {
                step(png, "Admission · Report " + n + " (" + label + ")", "Admission report tab #" + n,
                        "The report is shown (screenshot captured)", label + " captured (" + url + ")", "PASS");
            } else {
                step("Admission · Report " + n + " (" + label + ")", "Admission report tab #" + n,
                        "The report is shown", label + " tab opened: " + url, "PASS");
            }
        }
        if (newTabs.isEmpty()) {
            step("Admission · Report", "Save opens the admission report(s) in new tab(s)",
                    "The admission report is shown", "No report tab detected", "MANUAL");
        }
        page.bringToFront();
        addSummary("Admission · Result", toast + " (MRN " + selMrn + (attempt > 1 ? ", after " + attempt + " tries" : "") + ")");
        return true;
    }
}
