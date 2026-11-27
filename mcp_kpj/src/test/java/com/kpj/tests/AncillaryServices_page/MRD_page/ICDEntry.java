package com.kpj.tests.AncillaryServices_page.MRD_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.kpj.tests.ReadOnlyScenarioTestBase;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ancillary Services &gt; MRD &gt; ICD Entry — READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_MRD_ICDEntry_v1.xlsx (the important scenarios only). One JUnit test per scenario; each one
 * reopens the screen so none depends on another. Report: {@code test-output/reports/AncillaryServices_MRD_ICDEntry.html},
 * descriptive scenario names only (no IDs, no step numbers).</p>
 *
 * <p><b>Read-only.</b> The {@code ReadOnlyScreen} write guard is installed before login and proven before the screen is touched.
 * The ICD picker and the Procedure type-ahead are opened and searched but no result is ever picked; Save is clicked once, only
 * after every row is unticked, to prove the "Select at least one row" check - the screen stops before any request. Saving ICD
 * data is not part of this suite.</p>
 *
 * <p>Data (override with -D): {@code icd.mrn} 1100338591 + {@code icd.visit} OP-00000593 (living OPD patient);
 * {@code icd.mrn2} 1000338059 (several OPD visits, some discharged); {@code icd.invalidMrn} 9999999999;
 * {@code icd.from} 01/01/2026, {@code icd.to} 27/11/2026 (discharge range).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.MRD_page.ICDEntry" -Ddevhis.postrun=off
 * </pre>
 */
public class ICDEntry extends ReadOnlyScenarioTestBase<com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry> {

    private static final String MRN = System.getProperty("icd.mrn", "1100338591");
    private static final String VISIT = System.getProperty("icd.visit", "OP-00000593");
    private static final String MRN2 = System.getProperty("icd.mrn2", "1000338059");
    private static final String INVALID_MRN = System.getProperty("icd.invalidMrn", "9999999999");
    private static final String FROM = System.getProperty("icd.from", "01/01/2026");
    private static final String TO = System.getProperty("icd.to", "27/11/2026");

    private static final List<String> GRID_COLUMNS = List.of("Select checkbox", "Reg. Type", "Visit / Adm. No", "Visit Date", "Discharge Date",
            "Final Diagnosis", "ICD Diagnosis", "Primary ICD", "Secondary ICD", "Procedure", "Cause of Death", "Underlying Cause of Death", "External ICD");
    private static final List<String> AUDIT_COLUMNS = List.of("Transaction Type", "Field / Column", "Previous Value", "New Value",
            "Added By", "Added Date & Time", "Modified By", "Modified Date & Time");

    public ICDEntry() { super("AncillaryServices_MRD_ICDEntry"); }

    @Override protected com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry createScreen(Page page) {
        return new com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry(page);
    }
    @Override protected String reportTitle() { return "Ancillary Services - MRD - ICD Entry (read-only)"; }
    @Override protected String reportModule() { return "Ancillary Services > MRD > ICD Entry"; }
    @Override protected String reportNote() {
        return "Read-only checks from TestCases_MRD_ICDEntry_v1.xlsx. Write calls are aborted in the browser; no ICD, procedure or "
                + "cause of death is picked or saved. Save is clicked only with every row unticked, to prove its validation.";
    }
    @Override protected String readOnlyNote() {
        return "READ-ONLY - write calls aborted in the browser; no ICD / procedure picked; Save clicked only with no row selected";
    }
    @Override protected String dataSummary() { return "MRN " + MRN + " (" + VISIT + "), MRN " + MRN2 + ", discharge range " + FROM + " - " + TO; }

    // ---- helpers --------------------------------------------------------------------------

    private String[] freshScreen() {
        return screen.reopen() ? null : automationFailure("Ancillary Services > MRD > ICD Entry did not open (at " + page.url() + ")");
    }

    /** Fresh screen, {@code mrn} searched (Reg. Type All, no date filter); null = rows listed. */
    private String[] searched(String mrn) {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        screen.setMrn(mrn);
        int status = screen.search();
        if (status == 0) return automationFailure("Search sent no request for MRN " + mrn);
        if (status != 200) return defect("ICD Entry search answered HTTP " + status);
        if (screen.rows().isEmpty()) return dataMissing("no visits for MRN " + mrn + " on " + BASE + " (messages " + screen.alerts() + ")");
        return null;
    }

    private String[] patientVisitListed() {
        String[] notReady = searched(MRN);
        if (notReady != null) return notReady;
        if (screen.row(VISIT).isEmpty()) return dataMissing("visit " + VISIT + " not listed for MRN " + MRN);
        return null;
    }

    private static List<String> visits(List<Map<String, String>> rows) {
        List<String> v = new ArrayList<>();
        rows.forEach(r -> v.add(r.getOrDefault("Visit / Adm. No", "")));
        return v;
    }

    private static LocalDate parseDate(String s) {
        for (String f : new String[]{"dd-MM-yyyy", "dd/MM/yyyy", "dd-MMM-yy", "dd-MMM-yyyy"}) {
            try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(f, java.util.Locale.ENGLISH)); } catch (Exception ignore) { }
        }
        return null;
    }

    // ---- scenarios -------------------------------------------------------------------------

    @Test @Order(1) @DisplayName("Verify ICD Entry Page Loading")
    void verifyIcdEntryPageLoading() {
        scenario("Verify ICD Entry Page Loading",
                "Ancillary Services > MRD > ICD Entry (sidebar); read the header and any error",
                "ICD Entry opens with no error; header 'ICD Entry'",
                () -> {
                    int failuresBefore = screen.apiFailures.size();
                    if (!screen.reopen()) return automationFailure("Ancillary Services > MRD > ICD Entry did not open (at " + page.url() + ")");
                    String header = screen.headerText(), error = screen.visibleErrorText() + reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(screen.apiFailures.subList(failuresBefore, screen.apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Opened " + page.url() + "; header '" + header + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    return header.toLowerCase().startsWith("icd entry") ? passed(seen + "; no application error") : defect(seen + " - header is not 'ICD Entry'");
                });
    }

    @Test @Order(2) @DisplayName("Verify Search Filters, Buttons and Grid")
    void verifySearchFiltersButtonsAndGrid() {
        scenario("Verify Search Filters, Buttons and Grid",
                "Fresh screen; check every search control, button and the grid headers; read the default values",
                "Select Date, Discharge From/To Date, Reg. Type, MRN No + search icon, Ward, Search, Save, Clear and the grid "
                        + GRID_COLUMNS + " are shown; defaults: Select Date unticked, dates = today and disabled, Reg. Type = All, MRN empty",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = screen.presence(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.SEARCH_PANEL);
                    List<String> missing = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missing.add(k); });
                    List<String> headers = screen.gridHeaders();
                    if (headers == null) return automationFailure("ICD Entry grid not found");
                    List<String> missingCols = new ArrayList<>(GRID_COLUMNS);
                    missingCols.removeAll(headers);
                    String today = ReadOnlyScreen.today();
                    String from = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FROM),
                            to = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.TO),
                            mrn = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.MRN), reg = screen.regTypeChecked();
                    boolean filter = screen.isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.DATE_FILTER),
                            datesDisabled = screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FROM)
                                    && screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.TO);
                    String seen = "Missing controls " + missing + "; headers " + headers + "; Select Date " + filter + "; dates '" + from + "' - '" + to
                            + "' (today " + today + ", disabled " + datesDisabled + "); Reg. Type '" + reg + "'; MRN '" + (mrn == null ? "" : mrn) + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!missing.isEmpty()) wrong.add("controls missing " + missing);
                    if (!missingCols.isEmpty()) wrong.add("grid columns missing " + missingCols);
                    if (filter) wrong.add("Select Date ticked by default");
                    if (!today.equals(from) || !today.equals(to)) wrong.add("dates are not today");
                    if (!datesDisabled) wrong.add("dates editable before Select Date");
                    if (!"-1".equals(reg)) wrong.add("Reg. Type is not All");
                    if (mrn != null && !mrn.isBlank()) wrong.add("MRN not empty");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(3) @DisplayName("Validate Empty MRN Search")
    void validateEmptyMrnSearch() {
        scenario("Validate Empty MRN Search",
                "Fresh screen; MRN empty; click Search",
                "Warning 'Please Enter MRN'; no search request; grid empty",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    int logBefore = screen.apiLog.size();
                    screen.clickSearchOnly();
                    List<String> messages = screen.waitForAlert(4000);
                    boolean requested = screen.apiLog.subList(logBefore, screen.apiLog.size()).stream()
                            .anyMatch(x -> x.contains(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FETCH_API));
                    String seen = "Messages " + messages + "; search request sent " + requested + "; rows " + screen.rows().size();
                    boolean warned = messages.stream().anyMatch(m -> m.toLowerCase().contains("please enter mrn"));
                    return warned && !requested ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(4) @DisplayName("Validate Invalid MRN Search")
    void validateInvalidMrnSearch() {
        scenario("Validate Invalid MRN Search",
                "Fresh screen; MRN = " + INVALID_MRN + "; click Search",
                "Warning 'No Records Found'; grid empty; no patient banner",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    screen.setMrn(INVALID_MRN);
                    int status = screen.search();
                    if (status == 0) return automationFailure("Search sent no request");
                    List<String> messages = screen.waitForAlert(3000);
                    int rows = screen.rows().size();
                    String banner = screen.patientBanner();
                    String seen = "HTTP " + status + "; messages " + messages + "; rows " + rows + "; banner '" + trim(banner, 60) + "'";
                    if (rows > 0 || !banner.isEmpty()) return defect(seen + " - data shown for an unknown MRN");
                    return messages.stream().anyMatch(m -> m.toLowerCase().contains("no record")) ? passed(seen) : defect(seen + " - no 'No Records Found' message");
                });
    }

    @Test @Order(5) @DisplayName("Verify Patient Visit Records")
    void verifyPatientVisitRecords() {
        scenario("Verify Patient Visit Records",
                "Fresh screen; Reg. Type All; MRN = " + MRN + "; click Search; read the banner and the visit row",
                "Patient banner shows Name, DOB (Age), Gender, NRIC and MRN " + MRN + "; grid lists " + VISIT + ", Reg. Type OPD, Visit Date 27-04-2026",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    String banner = screen.patientBanner();
                    Map<String, String> row = screen.row(VISIT);
                    String seen = "Banner '" + trim(banner, 160) + "'; row " + VISIT + ": Reg. Type '" + row.get("Reg. Type") + "', Visit Date '" + row.get("Visit Date") + "'";
                    List<String> wrong = new ArrayList<>();
                    for (String part : new String[]{"Name :", "Gender :", "NRIC :", "MRN : " + MRN}) if (!banner.contains(part)) wrong.add("banner has no '" + part + "'");
                    if (!"OPD".equalsIgnoreCase(row.getOrDefault("Reg. Type", ""))) wrong.add("Reg. Type is not OPD");
                    if (!"27-04-2026".equals(row.getOrDefault("Visit Date", ""))) wrong.add("Visit Date is not 27-04-2026");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(6) @DisplayName("Verify All Visits Listed Without Date Filter")
    void verifyAllVisitsListedWithoutDateFilter() {
        scenario("Verify All Visits Listed Without Date Filter",
                "Fresh screen; Select Date unticked; MRN = " + MRN2 + "; click Search",
                "Every visit of the patient is listed, including visits not yet discharged (8 on devhis, e.g. OP-00000248, OP-00000247)",
                () -> {
                    String[] notReady = searched(MRN2);
                    if (notReady != null) return notReady;
                    List<Map<String, String>> rows = screen.rows();
                    long undischarged = rows.stream().filter(r -> r.getOrDefault("Discharge Date", "").isEmpty()).count();
                    String seen = rows.size() + " visits " + visits(rows) + "; without a discharge date " + undischarged
                            + (rows.size() == 8 ? "" : " (the workbook recorded 8 on devhis - the data has changed)");
                    if (!visits(rows).contains("OP-00000248")) return dataMissing("OP-00000248 not listed for " + MRN2 + " | " + seen);
                    return undischarged > 0 ? passed(seen) : defect(seen + " - undischarged visits are not listed without the date filter");
                });
    }

    @Test @Order(7) @DisplayName("Verify Select Date Enables Discharge Dates")
    void verifySelectDateEnablesDischargeDates() {
        scenario("Verify Select Date Enables Discharge Dates",
                "Fresh screen; tick Select Date; read Discharge From / To Date; untick it",
                "Ticked: both dates editable. Unticked: both dates disabled again",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    screen.setDateFilter(true);
                    boolean editable = !screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FROM)
                            && !screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.TO);
                    screen.setDateFilter(false);
                    boolean disabledAgain = screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FROM)
                            && screen.isDisabled(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.TO);
                    String seen = "Ticked: dates editable " + editable + "; unticked: dates disabled " + disabledAgain;
                    return editable && disabledAgain ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(8) @DisplayName("Verify Discharge Date Filtering")
    void verifyDischargeDateFiltering() {
        scenario("Verify Discharge Date Filtering",
                "Fresh screen; MRN = " + MRN2 + "; search without the filter; tick Select Date; From " + FROM + ", To " + TO + "; search again",
                "Only visits discharged within " + FROM + " - " + TO + " are listed (4 on devhis); visits not yet discharged drop out",
                () -> {
                    String[] notReady = searched(MRN2);
                    if (notReady != null) return notReady;
                    int all = screen.rows().size();
                    screen.setDateFilter(true);
                    screen.setDates(FROM, TO);
                    int status = screen.search();
                    if (status != 200) return status == 0 ? automationFailure("filtered Search sent no request") : defect("filtered search answered HTTP " + status);
                    List<Map<String, String>> rows = screen.rows();
                    LocalDate from = parseDate(FROM), to = parseDate(TO);
                    List<String> outside = new ArrayList<>();
                    for (Map<String, String> r : rows) {
                        LocalDate d = parseDate(r.getOrDefault("Discharge Date", ""));
                        if (d == null || d.isBefore(from) || d.isAfter(to)) outside.add(r.get("Visit / Adm. No") + " '" + r.get("Discharge Date") + "'");
                    }
                    String seen = "Without filter " + all + " visits; with filter " + rows.size() + " " + visits(rows)
                            + (rows.size() == 4 ? "" : " (the workbook recorded 4 on devhis)");
                    if (rows.isEmpty()) return dataMissing("no visit of " + MRN2 + " discharged in " + FROM + " - " + TO + " | " + seen);
                    if (!outside.isEmpty()) return defect("visits outside the range or without a discharge date listed " + outside + " | " + seen);
                    return rows.size() < all ? passed(seen) : defect(seen + " - the filter did not drop the undischarged visits");
                });
    }

    @Test @Order(9) @DisplayName("Verify Registration Type Filtering")
    void verifyRegistrationTypeFiltering() {
        scenario("Verify Registration Type Filtering",
                "Fresh screen; MRN = " + MRN2 + "; Reg. Type OPD -> Search; Reg. Type IPD -> Search",
                "OPD: only OPD visits listed. IPD: none for this patient -> 'No Records Found' and an empty grid",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    screen.setMrn(MRN2);
                    screen.setRegType("0");
                    int opdStatus = screen.search();
                    if (opdStatus != 200) return opdStatus == 0 ? automationFailure("OPD Search sent no request") : defect("OPD search answered HTTP " + opdStatus);
                    List<Map<String, String>> opd = screen.rows();
                    List<String> notOpd = new ArrayList<>();
                    opd.forEach(r -> { if (!"OPD".equalsIgnoreCase(r.getOrDefault("Reg. Type", ""))) notOpd.add(r.get("Visit / Adm. No") + " " + r.get("Reg. Type")); });
                    screen.setRegType("1");
                    int ipdStatus = screen.search();
                    if (ipdStatus != 200) return ipdStatus == 0 ? automationFailure("IPD Search sent no request") : defect("IPD search answered HTTP " + ipdStatus);
                    List<String> messages = screen.waitForAlert(3000);
                    List<Map<String, String>> ipd = screen.rows();
                    List<String> notIpd = new ArrayList<>();
                    ipd.forEach(r -> { if (!"IPD".equalsIgnoreCase(r.getOrDefault("Reg. Type", ""))) notIpd.add(r.get("Visit / Adm. No") + " " + r.get("Reg. Type")); });
                    String seen = "OPD: " + opd.size() + " visits" + (notOpd.isEmpty() ? "" : ", non-OPD " + notOpd) + " | IPD: " + ipd.size()
                            + " rows, messages " + messages;
                    if (opd.isEmpty()) return dataMissing("no OPD visit for " + MRN2 + " | " + seen);
                    if (!notOpd.isEmpty() || !notIpd.isEmpty()) return defect("Reg. Type filter returned other visit types | " + seen);
                    if (ipd.isEmpty() && messages.stream().noneMatch(m -> m.toLowerCase().contains("no record")))
                        return defect("IPD search returned nothing without a 'No Records Found' message | " + seen);
                    return passed(seen + (ipd.isEmpty() ? "" : " (this patient now has IPD admissions - all listed rows are IPD)"));
                });
    }

    @Test @Order(10) @DisplayName("Verify Audit Trail")
    void verifyAuditTrail() {
        scenario("Verify Audit Trail",
                "Search " + MRN + "; click the Audit Trail icon of " + VISIT + "; read the pop-up; close it with x",
                "Pop-up opens titled with " + VISIT + " / MRN " + MRN + " and columns " + AUDIT_COLUMNS + "; after closing, the grid still lists " + VISIT,
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    if (!screen.openAuditTrail(VISIT)) return automationFailure("Audit Trail pop-up did not open");
                    Map<String, Object> view = screen.auditView();
                    boolean closed = screen.closeAuditTrail();
                    boolean gridKept = !screen.row(VISIT).isEmpty();
                    @SuppressWarnings("unchecked") List<String> headers = view.get("headers") instanceof List ? (List<String>) view.get("headers") : new ArrayList<>();
                    String title = String.valueOf(view.getOrDefault("title", ""));
                    List<String> missingCols = new ArrayList<>(AUDIT_COLUMNS);
                    missingCols.removeAll(headers);
                    String seen = "Title '" + title + "'; headers " + headers + "; audit rows " + view.get("rows") + "; closed " + closed + "; grid still lists " + VISIT + " " + gridKept;
                    if (!closed) return automationFailure("the Audit Trail pop-up did not close | " + seen);
                    List<String> wrong = new ArrayList<>();
                    if (!title.contains(VISIT)) wrong.add("title does not name " + VISIT);
                    if (!missingCols.isEmpty()) wrong.add("columns missing " + missingCols);
                    if (!gridKept) wrong.add("the grid lost " + VISIT + " after closing");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(11) @DisplayName("Verify Primary ICD Search")
    void verifyPrimaryIcdSearch() {
        scenario("Verify Primary ICD Search",
                "Search " + MRN + "; click the Primary ICD box of " + VISIT + "; read the ICD-10 / ICD-11 options; type 'A09'; close the picker without picking",
                "Picker opens with ICD-10 selected and ICD-11 available; results include 'A09 > Infectious gastroenteritis and colitis, unspecified'; nothing is added to the visit",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    String primaryBefore = screen.row(VISIT).getOrDefault("Primary ICD", "");
                    if (!screen.openIcdPicker(VISIT, "primary")) return automationFailure("Primary ICD picker did not open");
                    Map<String, String> versions = screen.pickerVersions();
                    List<String> results = screen.searchInPicker("A09");
                    boolean closed = screen.closeIcdPicker();
                    String primaryAfter = screen.row(VISIT).getOrDefault("Primary ICD", "");
                    String seen = "Versions " + versions + "; results " + results + "; picker closed " + closed + "; Primary ICD before '" + primaryBefore + "' after '" + primaryAfter + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!versions.getOrDefault("ICD-10", "").startsWith("active")) wrong.add("ICD-10 is not the selected version");
                    if (!versions.containsKey("ICD-11")) wrong.add("no ICD-11 option");
                    if (results.stream().noneMatch(r -> r.contains("A09 > Infectious gastroenteritis and colitis, unspecified"))) wrong.add("A09 diagnosis not in the results");
                    if (!closed) return automationFailure("the picker did not close | " + seen);
                    if (!primaryBefore.equals(primaryAfter)) return automationFailure("the Primary ICD cell changed although nothing was picked | " + seen);
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(12) @DisplayName("Verify Secondary ICD Picker Behaviour")
    void verifySecondaryIcdPickerBehaviour() {
        scenario("Verify Secondary ICD Picker Behaviour",
                "Search " + MRN + "; read the user's 'Primary before Secondary' setting from the screen; click the Secondary ICD box of " + VISIT + " (no Primary ICD yet)",
                "Setting OFF: the Secondary ICD picker opens. Setting ON: warning 'Please record Primary ICD before Secondary ICD.' and no picker",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    Object flag = page.evaluate("() => { const e = document.querySelector(\"[ng-model='ICDEntry.mrno']\"); let s = e && angular.element(e).scope();"
                            + " while (s && !s.fnRequirePrimaryBeforeSecondary) s = s.$parent; return s ? s.fnRequirePrimaryBeforeSecondary() : null; }");
                    if (flag == null) return automationFailure("could not read the 'Primary before Secondary' setting");
                    boolean required = Boolean.TRUE.equals(flag);
                    String primary = screen.row(VISIT).getOrDefault("Primary ICD", "");
                    if (!primary.isEmpty()) return dataMissing(VISIT + " already has a Primary ICD ('" + primary + "'), so the rule cannot be shown");
                    boolean opened = screen.openIcdPicker(VISIT, "secondary");
                    List<String> messages = screen.waitForAlert(2000);
                    if (opened) screen.closeIcdPicker();
                    boolean warned = messages.stream().anyMatch(m -> m.contains("Please record Primary ICD before Secondary ICD"));
                    String seen = "Setting 'Primary before Secondary' = " + (required ? "ON" : "OFF") + "; picker opened " + opened + "; messages " + messages;
                    if (required) return !opened && warned ? passed(seen) : defect(seen + " - the setting is ON but Secondary ICD was not blocked");
                    return opened ? passed(seen) : defect(seen + " - the setting is OFF but the Secondary ICD picker did not open");
                });
    }

    @Test @Order(13) @DisplayName("Verify Procedure Search")
    void verifyProcedureSearch() {
        scenario("Verify Procedure Search",
                "Search " + MRN + "; type 'appendic' in the Procedure box of " + VISIT + "; read the suggestions; dismiss without picking",
                "Suggestions include 'PC00000046 - Appendicectomy' and 'Laparoscopic Appendicectomy'; no procedure is added to the visit",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    int before = screen.procedureCount(VISIT);
                    List<String> suggestions = screen.searchProcedure(VISIT, "appendic");
                    int after = screen.procedureCount(VISIT);
                    String seen = "Suggestions " + suggestions + "; procedures on the visit before " + before + ", after " + after;
                    if (before != after) return automationFailure("a procedure was added although nothing was picked | " + seen);
                    List<String> wrong = new ArrayList<>();
                    if (suggestions.stream().noneMatch(x -> x.contains("PC00000046 - Appendicectomy"))) wrong.add("Appendicectomy not suggested");
                    if (suggestions.stream().noneMatch(x -> x.contains("Laparoscopic Appendicectomy"))) wrong.add("Laparoscopic Appendicectomy not suggested");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(14) @DisplayName("Verify Cause of Death Not Offered for a Living Patient")
    void verifyCauseOfDeathNotOfferedForLivingPatient() {
        scenario("Verify Cause of Death Not Offered for a Living Patient",
                "Search " + MRN + " (living patient); check which ICD boxes the row of " + VISIT + " offers",
                "Cause of Death and Underlying Cause of Death offer no ICD box; Primary, Secondary and External ICD do",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    Object deceased = page.evaluate("(v) => { const tr = [...document.querySelectorAll('table.icd-entry-table tbody tr')].find(r => r.innerText.includes(v));"
                            + " const s = tr && angular.element(tr).scope(); return s && s.item ? s.item.isdeceased : null; }", VISIT);
                    if (Boolean.TRUE.equals(deceased) || "1".equals(String.valueOf(deceased)))
                        return dataMissing("patient " + MRN + " is recorded as deceased, so the living-patient rule cannot be shown");
                    List<String> offered = List.of(screen.row(VISIT).getOrDefault("__icdInputs", "").split("\\|"));
                    String seen = "ICD boxes offered " + offered + "; deceased flag " + deceased;
                    List<String> wrong = new ArrayList<>();
                    if (offered.contains("death")) wrong.add("Cause of Death box offered");
                    if (offered.contains("underlyingdeath")) wrong.add("Underlying Cause of Death box offered");
                    if (!offered.contains("primary") || !offered.contains("external")) wrong.add("Primary / External ICD box missing");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(15) @DisplayName("Verify Row Selection")
    void verifyRowSelection() {
        scenario("Verify Row Selection",
                "Search " + MRN2 + "; tick select-all; untick it; tick every row one by one; untick one row; untick all",
                "Select-all ticks / unticks every row; ticking every row ticks select-all; unticking one row unticks select-all; no request is sent",
                () -> {
                    String[] notReady = searched(MRN2);
                    if (notReady != null) return notReady;
                    int writesBefore = screen.blockedWrites.size();
                    List<String> visits = visits(screen.rows());
                    if (visits.size() < 2) return dataMissing("fewer than 2 visits for " + MRN2);
                    boolean allTicked, allCleared, autoTicked, autoUnticked;
                    try {
                        screen.setSelectAll(true);
                        allTicked = screen.rows().stream().allMatch(r -> "true".equals(r.get("__selected")));
                        screen.setSelectAll(false);
                        allCleared = screen.rows().stream().noneMatch(r -> "true".equals(r.get("__selected")));
                        for (String v : visits) screen.setRowSelected(v, true);
                        autoTicked = screen.isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.SELECT_ALL);
                        screen.setRowSelected(visits.get(0), false);
                        autoUnticked = !screen.isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.SELECT_ALL);
                    } finally {
                        screen.untickEveryRow();
                    }
                    boolean wrote = screen.blockedWrites.size() > writesBefore;
                    String seen = visits.size() + " visits; select-all ticks all " + allTicked + ", clears all " + allCleared
                            + "; every row ticked -> select-all " + autoTicked + "; one row unticked -> select-all cleared " + autoUnticked + "; write attempted " + wrote;
                    if (wrote) return defect(seen + " - selecting rows tried to send a write request (aborted)");
                    return allTicked && allCleared && autoTicked && autoUnticked ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(16) @DisplayName("Verify Save Validation")
    void verifySaveValidation() {
        scenario("Verify Save Validation",
                "Search " + MRN + "; make sure no row is ticked; click Save",
                "Warning 'Select at least one row'; no save request is sent and nothing is changed",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    int writesBefore = screen.blockedWrites.size(), logBefore = screen.apiLog.size();
                    if (!screen.clickSaveWithNoRowSelected()) return automationFailure("could not untick every row, so Save was NOT clicked");
                    List<String> messages = screen.waitForAlert(3000);
                    boolean writeAttempted = screen.blockedWrites.size() > writesBefore;
                    List<String> callsAfterSave = new ArrayList<>(screen.apiLog.subList(logBefore, screen.apiLog.size()));
                    callsAfterSave.removeIf(x -> !x.contains("/api/ICDEntry"));
                    String seen = "Messages " + messages + "; write request attempted " + writeAttempted + "; ICD Entry calls after Save " + callsAfterSave;
                    if (writeAttempted) return defect(seen + " - Save tried to send a request with no row selected (aborted by the guard)");
                    return messages.stream().anyMatch(m -> m.contains("Select at least one row")) ? passed(seen) : defect(seen + " - no 'Select at least one row' warning");
                });
    }

    @Test @Order(17) @DisplayName("Verify Clear Functionality")
    void verifyClearFunctionality() {
        scenario("Verify Clear Functionality",
                "Search " + MRN + "; tick Select Date; set Reg. Type OPD; click Clear; read the form",
                "MRN empty, grid empty, patient banner gone, Reg. Type back to All, Select Date unticked, dates back to today",
                () -> {
                    String[] notReady = patientVisitListed();
                    if (notReady != null) return notReady;
                    screen.setDateFilter(true);
                    screen.setRegType("0");
                    screen.clickClear();
                    String mrn = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.MRN), reg = screen.regTypeChecked(),
                            banner = screen.patientBanner(), today = ReadOnlyScreen.today(),
                            from = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.FROM),
                            to = screen.inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.TO);
                    int rows = screen.rows().size();
                    boolean filter = screen.isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.ICDEntry.DATE_FILTER);
                    String seen = "MRN '" + (mrn == null ? "" : mrn) + "'; rows " + rows + "; banner '" + trim(banner, 40) + "'; Reg. Type '" + reg
                            + "'; Select Date " + filter + "; dates '" + from + "' - '" + to + "' (today " + today + ")";
                    List<String> wrong = new ArrayList<>();
                    if (mrn != null && !mrn.isBlank()) wrong.add("MRN not cleared");
                    if (rows > 0) wrong.add("grid not cleared");
                    if (!banner.isEmpty()) wrong.add("banner still shown");
                    if (!"-1".equals(reg)) wrong.add("Reg. Type not back to All");
                    if (filter) wrong.add("Select Date still ticked");
                    if (!today.equals(from) || !today.equals(to)) wrong.add("dates not back to today");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(18) @DisplayName("Confirm No Write Request Was Sent")
    void confirmNoWriteRequestWasSent() {
        scenario("Confirm No Write Request Was Sent",
                "After all scenarios, read the write guard's log",
                "No save / insert / update / delete request was attempted by this suite",
                () -> screen.blockedWrites.isEmpty() ? passed("No write request attempted; " + screen.apiLog.size() + " read calls made")
                        : automationFailure("write requests were attempted (and aborted): " + screen.blockedWrites));
    }
}
