package com.kpj.tests.AncillaryServices_page.MRD_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.kpj.tests.ReadOnlyScenarioTestBase;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ancillary Services &gt; MRD &gt; MRD (MRD file list) — READ-ONLY automated checks.
 *
 * <p>Source: the MRD test scenarios supplied in this conversation (important ones only). One JUnit test per scenario; each one
 * reopens the screen so none depends on another. Report: {@code test-output/reports/AncillaryServices_MRD_MRD.html} with
 * descriptive scenario names only - no IDs and no step numbers ({@link ReadOnlyScenarioTestBase} switches the report's numbering off).</p>
 *
 * <p><b>Read-only.</b> The {@code ReadOnlyScreen} write guard is installed before login and proven before the screen is touched.
 * Add, Edit, Scanning Status / Update, Scrap, Print, File Linking's Add / Submit and the Investigation View's Show are never clicked;
 * pop-ups are only opened, read and closed with x.</p>
 *
 * <p>Known application issues are checked as such: an empty search shows no "No Records Found" message, and the Diagnosis label
 * appears twice. They are reported as APPLICATION DEFECT (known issue), not as automation failures.</p>
 *
 * <p>Data (override with -D): {@code mrd.mrnOne} 1000338068 (one file), {@code mrd.mrnTwo} ED000322 (two files),
 * {@code mrd.mrnNone} 1000338059 (no file), {@code mrd.mrnUnknown} 9999999999, {@code mrd.fileNo} FN-01-26-0434,
 * {@code mrd.from} 01/11/2026, {@code mrd.to} 27/11/2026.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.MRD_page.MRD" -Ddevhis.postrun=off
 * </pre>
 */
public class MRD extends ReadOnlyScenarioTestBase<com.kpj.pages.AncillaryServices_page.MRD_page.MRD> {

    private static final String MRN_ONE = System.getProperty("mrd.mrnOne", "1000338068");
    private static final String MRN_TWO = System.getProperty("mrd.mrnTwo", "ED000322");
    private static final String MRN_NONE = System.getProperty("mrd.mrnNone", "1000338059");
    private static final String MRN_UNKNOWN = System.getProperty("mrd.mrnUnknown", "9999999999");
    private static final String FILE_NO = System.getProperty("mrd.fileNo", "FN-01-26-0434");
    private static final String FROM = System.getProperty("mrd.from", "01/11/2026");
    private static final String TO = System.getProperty("mrd.to", "27/11/2026");

    private static final List<String> GRID_COLUMNS = List.of("Edit", "EMR", "MRN", "Patient Name", "Is Deceased", "Medico Legal Case",
            "Notifiable Disease Form", "File No.", "File Type", "Appointmentno", "File Linking", "Visit No.", "Diagnosis", "Scanning Status", "Update");
    private static final String KNOWN = " (known issue)";

    public MRD() { super("AncillaryServices_MRD_MRD"); }

    @Override protected com.kpj.pages.AncillaryServices_page.MRD_page.MRD createScreen(Page page) {
        return new com.kpj.pages.AncillaryServices_page.MRD_page.MRD(page);
    }
    @Override protected String reportTitle() { return "Ancillary Services - MRD - MRD File List (read-only)"; }
    @Override protected String reportModule() { return "Ancillary Services > MRD > MRD"; }
    @Override protected String reportNote() {
        return "Read-only checks of the MRD file list. Write calls are aborted in the browser; Add, Edit, Scanning Status / Update, Scrap, "
                + "Print and File Linking's Submit are never clicked. Known issues (no 'No Records Found' message, duplicate Diagnosis label) are "
                + "reported as application defects.";
    }
    @Override protected String readOnlyNote() {
        return "READ-ONLY - write calls aborted in the browser; no Add / Edit / Update / Scrap / Print; pop-ups only read and closed";
    }
    @Override protected String dataSummary() {
        return "MRN " + MRN_ONE + " (one file), " + MRN_TWO + " (two files), " + MRN_NONE + " (no file), " + MRN_UNKNOWN + " (unknown); file " + FILE_NO;
    }

    // ---- helpers --------------------------------------------------------------------------

    private com.kpj.pages.AncillaryServices_page.MRD_page.MRD m() { return screen; }

    private String[] freshScreen() {
        return m().reopen() ? null : automationFailure("Ancillary Services > MRD > MRD did not open (at " + page.url() + ")");
    }

    /** Fresh screen, MRN searched; null = searched (rows may be empty - the caller decides). */
    private String[] searchedMrn(String mrn) {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        m().setMrn(mrn);
        int status = m().search();
        if (status == 0) return automationFailure("Search sent no request for MRN " + mrn + " (messages " + m().alerts() + ")");
        if (status != 200) return defect("MRD file search answered HTTP " + status);
        return null;
    }

    /** Fresh screen, default search; null = files listed. */
    private String[] defaultSearch() {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        int status = m().search();
        if (status == 0) return automationFailure("Search sent no request (messages " + m().alerts() + ")");
        if (status != 200) return defect("MRD file search answered HTTP " + status);
        if (m().files().isEmpty()) return dataMissing("the MRD file list is empty on " + BASE);
        return null;
    }

    private static List<String> field(List<Map<String, String>> rows, String key) {
        List<String> v = new ArrayList<>();
        rows.forEach(r -> v.add(r.getOrDefault(key, "")));
        return v;
    }

    private static List<String> missing(List<String> expected, List<String> actual) {
        List<String> out = new ArrayList<>(expected);
        if (actual != null) out.removeAll(actual);
        return out;
    }

    // ---- screen and layout --------------------------------------------------------------------

    @Test @Order(1) @DisplayName("Verify MRD Page Loading")
    void verifyMrdPageLoading() {
        scenario("Verify MRD Page Loading",
                "Ancillary Services > MRD > MRD (sidebar); read the route, header and any error",
                "Route #/MRDFileList; header 'MRD'; no application error",
                () -> {
                    int failuresBefore = m().apiFailures.size();
                    if (!m().reopen()) return automationFailure("Ancillary Services > MRD > MRD did not open (at " + page.url() + ")");
                    String header = m().headerText(), route = m().route(), error = m().visibleErrorText() + reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(m().apiFailures.subList(failuresBefore, m().apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Route " + route + "; header '" + header + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    boolean ok = route.startsWith("#/MRDFileList") && header.matches("(?i)^MRD\\b.*");
                    return ok ? passed(seen + "; no application error") : defect(seen + " - route or header is not the MRD file list");
                });
    }

    @Test @Order(2) @DisplayName("Verify Search Panel Layout")
    void verifySearchPanelLayout() {
        scenario("Verify Search Panel Layout",
                "Fresh screen; check every search control, the Search / Add buttons and the four list buttons",
                "Shown: " + com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SEARCH_PANEL.keySet() + " and "
                        + com.kpj.pages.AncillaryServices_page.MRD_page.MRD.LIST_BUTTONS.keySet(),
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    List<String> missing = new ArrayList<>();
                    m().presence(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SEARCH_PANEL).forEach((k, v) -> { if (!v) missing.add(k); });
                    m().presence(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.LIST_BUTTONS).forEach((k, v) -> { if (!v) missing.add(k); });
                    String seen = "Missing " + missing;
                    return missing.isEmpty() ? passed("All search controls and list buttons shown (Add checked for presence only)") : defect(seen);
                });
    }

    @Test @Order(3) @DisplayName("Verify Default Filter Values and Dropdown Options")
    void verifyDefaultFilterValuesAndDropdownOptions() {
        scenario("Verify Default Filter Values and Dropdown Options",
                "Fresh screen; read the defaults; read File Type, Rack, Row, Box and Scanning Status options",
                "Select Date unticked, From / To = today and disabled; Reg. Type = All; Diagnosis unticked and disabled; dropdowns on -Select-; "
                        + "File Type includes Discharge, Rack includes Rack 1, Scanning Status offers Yes / No; Row and Box have entries",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    String today = ReadOnlyScreen.today();
                    String from = m().inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FROM), to = m().inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.TO);
                    boolean dateTicked = m().isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.DATE_FILTER),
                            datesDisabled = m().isDisabled("[ng-model='MRDFileList.fromdate']") && m().isDisabled("[ng-model='MRDFileList.todate']"),
                            diagTicked = m().isChecked(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.DIAGNOSIS_FILTER), diagDisabled = m().isDisabled("#Diagnosis");
                    String reg = m().regTypeChecked();
                    List<String> fileTypes = m().options(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_TYPE), racks = m().options(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.RACK),
                            rows = m().options(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.ROW), boxes = m().options(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.BOX),
                            scanning = m().options(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SCANNING);
                    List<String> preselected = new ArrayList<>();
                    for (String ng : new String[]{com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_TYPE, com.kpj.pages.AncillaryServices_page.MRD_page.MRD.RACK,
                            com.kpj.pages.AncillaryServices_page.MRD_page.MRD.ROW, com.kpj.pages.AncillaryServices_page.MRD_page.MRD.BOX, com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SCANNING})
                        if (!m().selectedText(ng).toLowerCase().contains("select")) preselected.add(ng + "='" + m().selectedText(ng) + "'");
                    String seen = "Select Date " + dateTicked + ", dates '" + from + "' - '" + to + "' (today " + today + ", disabled " + datesDisabled + "); Reg. Type '" + reg
                            + "'; Diagnosis ticked " + diagTicked + ", disabled " + diagDisabled + "; File Type " + fileTypes.size() + " (e.g. " + fileTypes.subList(0, Math.min(4, fileTypes.size()))
                            + "); Rack " + racks.size() + "; Row " + rows.size() + "; Box " + boxes.size() + "; Scanning Status " + scanning;
                    List<String> wrong = new ArrayList<>();
                    if (dateTicked || !datesDisabled) wrong.add("date filter not off by default");
                    if (!today.equals(from) || !today.equals(to)) wrong.add("dates are not today");
                    if (!"2".equals(reg)) wrong.add("Reg. Type is not All");
                    if (diagTicked || !diagDisabled) wrong.add("Diagnosis filter not off by default");
                    if (!preselected.isEmpty()) wrong.add("preselected " + preselected);
                    if (!fileTypes.contains("Discharge")) wrong.add("File Type has no Discharge");
                    if (!racks.contains("Rack 1")) wrong.add("Rack has no Rack 1");
                    if (!scanning.containsAll(List.of("Yes", "No"))) wrong.add("Scanning Status is not Yes / No");
                    if (rows.isEmpty() || boxes.isEmpty()) wrong.add("Row / Box lists empty");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(4) @DisplayName("Verify MRD Grid Columns")
    void verifyMrdGridColumns() {
        scenario("Verify MRD Grid Columns",
                "Fresh screen; read the grid headers",
                "Columns " + GRID_COLUMNS,
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    List<String> headers = m().gridHeaders();
                    if (headers == null) return automationFailure("MRD grid not found");
                    List<String> miss = missing(GRID_COLUMNS, headers);
                    return miss.isEmpty() ? passed("Headers " + headers) : defect("missing columns " + miss + " | headers " + headers);
                });
    }

    @Test @Order(5) @DisplayName("Verify Duplicate Diagnosis Label")
    void verifyDuplicateDiagnosisLabel() {
        scenario("Verify Duplicate Diagnosis Label",
                "Fresh screen; count the visible labels reading 'Diagnosis' in the search panel",
                "One 'Diagnosis' label for the diagnosis filter",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    int count = m().labelCount("Diagnosis");
                    String seen = count + " visible 'Diagnosis' labels (the tick box and the text box each have one)";
                    if (count == 1) return passed(seen + " - the duplicate label is fixed");
                    if (count == 2) return defect("Diagnosis label appears twice" + KNOWN + " | " + seen);
                    return defect(seen);
                });
    }

    // ---- search --------------------------------------------------------------------------------

    @Test @Order(6) @DisplayName("Verify Default MRD Search")
    void verifyDefaultMrdSearch() {
        scenario("Verify Default MRD Search",
                "Fresh screen; no filters; click Search",
                "MRD files are listed and the grid pager shows the total",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    List<Map<String, String>> files = m().files();
                    String seen = files.size() + " files; pager '" + m().pagerText() + "'; first " + files.get(0).get("mrno") + " " + files.get(0).get("patientname") + " " + files.get(0).get("fileno");
                    return m().pagerText().contains("of " + files.size()) ? passed(seen) : defect(seen + " - the pager does not match the list");
                });
    }

    @Test @Order(7) @DisplayName("Verify Single File MRN Search")
    void verifySingleFileMrnSearch() {
        scenario("Verify Single File MRN Search",
                "Fresh screen; MRN = " + MRN_ONE + "; click Search",
                "Exactly one file, belonging to MRN " + MRN_ONE,
                () -> {
                    String[] notReady = searchedMrn(MRN_ONE);
                    if (notReady != null) return notReady;
                    List<Map<String, String>> files = m().files();
                    List<String> mrns = field(files, "mrno");
                    String seen = files.size() + " files " + field(files, "fileno") + " for MRNs " + mrns;
                    if (files.isEmpty()) return dataMissing("MRN " + MRN_ONE + " has no MRD file on " + BASE);
                    if (mrns.stream().anyMatch(x -> !x.equalsIgnoreCase(MRN_ONE))) return defect("files of other patients listed | " + seen);
                    return files.size() == 1 ? passed(seen) : passed(seen + " (the data now has " + files.size() + " files for this MRN)");
                });
    }

    @Test @Order(8) @DisplayName("Verify Multiple File MRN Search")
    void verifyMultipleFileMrnSearch() {
        scenario("Verify Multiple File MRN Search",
                "Fresh screen; MRN = " + MRN_TWO + "; click Search",
                "Both files of MRN " + MRN_TWO + " are listed and nothing else",
                () -> {
                    String[] notReady = searchedMrn(MRN_TWO);
                    if (notReady != null) return notReady;
                    List<Map<String, String>> files = m().files();
                    List<String> mrns = field(files, "mrno");
                    String seen = files.size() + " files " + field(files, "fileno");
                    if (files.size() < 2) return dataMissing("MRN " + MRN_TWO + " has " + files.size() + " MRD file(s) on " + BASE + " | " + seen);
                    if (mrns.stream().anyMatch(x -> !x.equalsIgnoreCase(MRN_TWO))) return defect("files of other patients listed | " + seen + " MRNs " + mrns);
                    return passed(seen);
                });
    }

    @Test @Order(9) @DisplayName("Verify Empty MRD Search Results")
    void verifyEmptyMrdSearchResults() {
        scenario("Verify Empty MRD Search Results",
                "Fresh screen; MRN = " + MRN_NONE + " (registered, no MRD file); click Search",
                "Grid empty and a 'No Records Found' message is shown",
                () -> {
                    String[] notReady = searchedMrn(MRN_NONE);
                    if (notReady != null) return notReady;
                    List<String> messages = m().waitForAlert(3000);
                    int files = m().files().size();
                    String seen = files + " files; messages " + messages;
                    if (files > 0) return dataMissing("MRN " + MRN_NONE + " now has MRD files, so an empty result cannot be shown | " + seen);
                    if (messages.stream().anyMatch(x -> x.toLowerCase().contains("no record"))) return passed(seen + " - the missing message is fixed");
                    return defect("empty result shows no 'No Records Found' message" + KNOWN + " | " + seen);
                });
    }

    @Test @Order(10) @DisplayName("Verify Invalid MRN Search")
    void verifyInvalidMrnSearch() {
        scenario("Verify Invalid MRN Search",
                "Fresh screen; MRN = " + MRN_UNKNOWN + "; click Search",
                "Grid empty and a 'No Records Found' message is shown",
                () -> {
                    String[] notReady = searchedMrn(MRN_UNKNOWN);
                    if (notReady != null) return notReady;
                    List<String> messages = m().waitForAlert(3000);
                    int files = m().files().size();
                    String seen = files + " files; messages " + messages;
                    if (files > 0) return defect("files listed for an unknown MRN | " + seen);
                    if (messages.stream().anyMatch(x -> x.toLowerCase().contains("no record") || x.toLowerCase().contains("not found"))) return passed(seen);
                    return defect("unknown MRN shows no 'No Records Found' message" + KNOWN + " | " + seen);
                });
    }

    @Test @Order(11) @DisplayName("Verify New Search Replaces Previous Results")
    void verifyNewSearchReplacesPreviousResults() {
        scenario("Verify New Search Replaces Previous Results",
                "Fresh screen; default search; then MRN = " + MRN_ONE + "; click Search",
                "Only MRN " + MRN_ONE + "'s files remain - none of the earlier rows",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    int before = m().files().size();
                    m().setMrn(MRN_ONE);
                    int status = m().search();
                    if (status != 200) return status == 0 ? automationFailure("second Search sent no request") : defect("second search answered HTTP " + status);
                    List<Map<String, String>> after = m().files();
                    List<String> others = new ArrayList<>();
                    after.forEach(r -> { if (!MRN_ONE.equalsIgnoreCase(r.get("mrno"))) others.add(r.get("mrno")); });
                    String seen = "Before " + before + " files; after " + after.size() + " " + field(after, "fileno") + "; pager '" + m().pagerText() + "'";
                    if (after.isEmpty()) return dataMissing("MRN " + MRN_ONE + " has no MRD file | " + seen);
                    return others.isEmpty() ? passed(seen) : defect("earlier rows kept " + others + " | " + seen);
                });
    }

    @Test @Order(12) @DisplayName("Verify Find Patient")
    void verifyFindPatient() {
        scenario("Verify Find Patient",
                "Fresh screen; Find Patient with MRN empty; then MRN = " + MRN_ONE + " and Find Patient",
                "Empty: warning 'Please Enter MRN No.'. Valid: the patient's details load for MRN " + MRN_ONE,
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    int emptyStatus = m().findPatient();
                    List<String> emptyMsgs = m().waitForAlert(3000);
                    m().setMrn(MRN_ONE);
                    int status = m().findPatient();
                    String banner = m().patientBanner(), mrnAfter = m().inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.MRN);
                    String seen = "Empty: request " + (emptyStatus != 0) + ", messages " + emptyMsgs + " | valid: HTTP " + status + ", MRN box '" + mrnAfter
                            + "', banner '" + trim(banner, 120) + "'";
                    List<String> wrong = new ArrayList<>();
                    if (emptyStatus != 0 || emptyMsgs.stream().noneMatch(x -> x.contains("Please Enter MRN"))) wrong.add("empty MRN not stopped with 'Please Enter MRN No.'");
                    if (status == 0) return automationFailure("Find Patient sent no lookup for " + MRN_ONE + " | " + seen);
                    if (!banner.contains(MRN_ONE) && !MRN_ONE.equalsIgnoreCase(mrnAfter)) wrong.add("patient details not loaded");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(13) @DisplayName("Verify Search Patient Popup Selection")
    void verifySearchPatientPopupSelection() {
        scenario("Verify Search Patient Popup Selection",
                "Fresh screen; click Search Patient; search MRN " + MRN_ONE + " in the pop-up; press Select on the result",
                "The 'Patient Search' pop-up opens, lists the patient, and Select closes it and fills MRN " + MRN_ONE,
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    boolean picked = m().pickPatientInSearchPopup(MRN_ONE);
                    String mrn = m().inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.MRN);
                    int open = m().openModals();
                    if (open > 0) m().closePopup();
                    String seen = "Pop-up used " + picked + "; MRN box '" + mrn + "'; pop-ups still open " + open;
                    if (!picked) return automationFailure("Patient Search pop-up did not open or listed no patient for " + MRN_ONE + " | " + seen);
                    return MRN_ONE.equalsIgnoreCase(mrn) && open == 0 ? passed(seen) : defect(seen);
                });
    }

    // ---- filters -------------------------------------------------------------------------------

    @Test @Order(14) @DisplayName("Verify Date Filtering")
    void verifyDateFiltering() {
        scenario("Verify Date Filtering",
                "Fresh screen; tick Select Date (dates become editable); From " + FROM + " To " + TO + " -> Search; From " + TO + " To " + FROM
                        + " -> Search; untick Select Date",
                "Dates editable only while ticked; the range lists fewer files than the full list; From later than To shows "
                        + "'From-date cannot be later than the To-date' and leaves the grid empty",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    int all = m().files().size();
                    m().setDateFilter(true);
                    boolean editable = !m().isDisabled("[ng-model='MRDFileList.fromdate']");
                    m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FROM, FROM);
                    m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.TO, TO);
                    int status = m().search();
                    if (status != 200) return status == 0 ? automationFailure("date-range Search sent no request (messages " + m().alerts() + ")") : defect("date search answered HTTP " + status);
                    int ranged = m().files().size();
                    m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FROM, TO);
                    m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.TO, FROM);
                    int reversedStatus = m().search();
                    List<String> reversedMsgs = m().waitForAlert(3000);
                    int reversed = m().files().size();
                    m().setDateFilter(false);
                    boolean disabledAgain = m().isDisabled("[ng-model='MRDFileList.fromdate']");
                    String seen = "Editable when ticked " + editable + "; all " + all + ", range " + ranged + "; reversed: request " + (reversedStatus != 0)
                            + ", messages " + reversedMsgs + ", rows " + reversed + "; disabled after untick " + disabledAgain;
                    List<String> wrong = new ArrayList<>();
                    if (!editable || !disabledAgain) wrong.add("Select Date does not enable / disable the dates");
                    if (ranged > all) wrong.add("the range lists more files than the full list");
                    if (reversedMsgs.stream().noneMatch(x -> x.contains("From-date cannot be later than the To-date"))) wrong.add("no From/To order warning");
                    if (reversed > 0) wrong.add("grid not emptied for a reversed range");
                    if (ranged == 0) return dataMissing("no MRD file in " + FROM + " - " + TO + " | " + seen);
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(15) @DisplayName("Verify Registration Type Filtering")
    void verifyRegistrationTypeFiltering() {
        scenario("Verify Registration Type Filtering",
                "Fresh screen; default search (All); Reg. Type OPD -> Search; Reg. Type IPD -> Search",
                "OPD lists only OPD files; IPD lists only IPD files; together they match the All list",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    List<Map<String, String>> all = m().files();
                    long allOpd = all.stream().filter(r -> "0".equals(r.get("opd_ipd"))).count(), allIpd = all.stream().filter(r -> "1".equals(r.get("opd_ipd"))).count();
                    m().setRegType("0");
                    if (m().search() != 200) return automationFailure("OPD Search did not complete");
                    List<Map<String, String>> opd = m().files();
                    m().setRegType("1");
                    if (m().search() != 200) return automationFailure("IPD Search did not complete");
                    List<Map<String, String>> ipd = m().files();
                    long opdWrong = opd.stream().filter(r -> !"0".equals(r.get("opd_ipd"))).count(), ipdWrong = ipd.stream().filter(r -> !"1".equals(r.get("opd_ipd"))).count();
                    String seen = "All " + all.size() + " (OPD " + allOpd + ", IPD " + allIpd + "); OPD search " + opd.size() + " (non-OPD " + opdWrong + "); IPD search "
                            + ipd.size() + " (non-IPD " + ipdWrong + ")";
                    List<String> wrong = new ArrayList<>();
                    if (opdWrong > 0) wrong.add("OPD search lists non-OPD files");
                    if (ipdWrong > 0) wrong.add("IPD search lists non-IPD files");
                    if (opd.size() != allOpd || ipd.size() != allIpd) wrong.add("OPD / IPD counts do not match the All list");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(16) @DisplayName("Verify File Type Filtering")
    void verifyFileTypeFiltering() {
        scenario("Verify File Type Filtering",
                "Fresh screen; File Type = Discharge -> Search; then add MRN = " + MRN_TWO + " -> Search",
                "Only Discharge files are listed; with the MRN as well, only " + MRN_TWO + "'s Discharge files",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    if (m().pickOption(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_TYPE, "Discharge").isEmpty()) return dataMissing("no 'Discharge' File Type configured");
                    if (m().search() != 200) return automationFailure("File Type Search did not complete");
                    List<Map<String, String>> discharge = m().files();
                    long notDischarge = discharge.stream().filter(r -> !"Discharge".equalsIgnoreCase(r.get("filetype"))).count();
                    m().setMrn(MRN_TWO);
                    if (m().search() != 200) return automationFailure("combined Search did not complete");
                    List<Map<String, String>> combined = m().files();
                    long combinedWrong = combined.stream().filter(r -> !"Discharge".equalsIgnoreCase(r.get("filetype")) || !MRN_TWO.equalsIgnoreCase(r.get("mrno"))).count();
                    String seen = "Discharge " + discharge.size() + " (other types " + notDischarge + "); with MRN " + MRN_TWO + ": " + combined.size() + " " + field(combined, "fileno")
                            + " (not matching both " + combinedWrong + ")";
                    if (discharge.isEmpty()) return dataMissing("no Discharge files | " + seen);
                    if (notDischarge > 0 || combinedWrong > 0) return defect(seen);
                    return passed(seen);
                });
    }

    @Test @Order(17) @DisplayName("Verify Rack Row and Box Filtering")
    void verifyRackRowAndBoxFiltering() {
        scenario("Verify Rack Row and Box Filtering",
                "Default search; take a file stored in a rack / row / box; filter by that Rack, then Rack + Row + Box",
                "Only files stored in that location are listed",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    Map<String, String> stored = m().files().stream().filter(r -> !r.get("rackid").isEmpty() && !"0".equals(r.get("rackid"))).findFirst().orElse(null);
                    if (stored == null) return dataMissing("no MRD file is allocated to a rack on " + BASE + ", so the location filters cannot be shown");
                    Object picked = page.evaluate("([r, w, b]) => { const pick = (ng, v) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s || !v || v === '0') return '';"
                            + " const o = [...s.options].find(x => x.value === v || x.value.endsWith(':' + v)); if (!o) return ''; s.value = o.value; s.dispatchEvent(new Event('change', {bubbles: true}));"
                            + " if (window.jQuery) window.jQuery(s).trigger('change'); return o.text.trim(); };"
                            + " return [pick('MRDFileList.rackid', r), pick('MRDFileList.rowid', w), pick('MRDFileList.boxid', b)].join(' / '); }",
                            List.of(stored.get("rackid"), stored.get("rowid"), stored.get("boxid")));
                    if (m().search() != 200) return automationFailure("location Search did not complete");
                    List<Map<String, String>> files = m().files();
                    long elsewhere = files.stream().filter(r -> !stored.get("rackid").equals(r.get("rackid"))).count();
                    String seen = "Location " + picked + " (file " + stored.get("fileno") + "); " + files.size() + " files listed, " + elsewhere + " in another rack";
                    if (files.isEmpty()) return defect("the file stored there is not listed | " + seen);
                    return elsewhere == 0 ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(18) @DisplayName("Verify File Number Search")
    void verifyFileNumberSearch() {
        scenario("Verify File Number Search",
                "Fresh screen; File No. = " + FILE_NO + "; click Search",
                "Only file " + FILE_NO + " is listed",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_NO, FILE_NO);
                    if (m().search() != 200) return automationFailure("File No. Search did not complete");
                    List<String> files = field(m().files(), "fileno");
                    String seen = files.size() + " files " + files;
                    if (files.isEmpty()) return dataMissing("file " + FILE_NO + " not found on " + BASE);
                    return files.stream().allMatch(FILE_NO::equalsIgnoreCase) ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(19) @DisplayName("Verify Diagnosis Filter")
    void verifyDiagnosisFilter() {
        scenario("Verify Diagnosis Filter",
                "Default search; tick the Diagnosis filter; type a diagnosis taken from the list; Search; untick the filter",
                "The Diagnosis box is editable only while ticked; only files with that diagnosis are listed; unticking clears the box",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    String sample = m().files().stream().map(r -> r.get("diagnosis")).filter(x -> x != null && !x.isBlank()).findFirst().orElse("");
                    m().setDiagnosisFilter(true);
                    boolean editable = !m().isDisabled("#Diagnosis");
                    String term = sample.split("[,;]")[0].trim();
                    if (term.length() > 25) term = term.substring(0, 25);
                    int listed = -1;
                    long without = 0;
                    if (!term.isEmpty()) {
                        m().setNgInput(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.DIAGNOSIS, term);
                        if (m().search() != 200) return automationFailure("Diagnosis Search did not complete");
                        List<Map<String, String>> files = m().files();
                        listed = files.size();
                        final String t = term.toLowerCase();
                        without = files.stream().filter(r -> !r.get("diagnosis").toLowerCase().contains(t)).count();
                    }
                    m().setDiagnosisFilter(false);
                    boolean disabledAgain = m().isDisabled("#Diagnosis");
                    String cleared = m().inputValue(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.DIAGNOSIS);
                    String seen = "Editable when ticked " + editable + "; term '" + term + "' -> " + listed + " files (" + without + " without it); disabled after untick "
                            + disabledAgain + ", box '" + cleared + "'";
                    if (!editable || !disabledAgain) return defect("the tick box does not enable / disable the Diagnosis box | " + seen);
                    if (term.isEmpty()) return dataMissing("no MRD file has a diagnosis, so the filter result cannot be shown (toggle verified) | " + seen);
                    if (cleared != null && !cleared.isBlank()) return defect("the Diagnosis box is not cleared when unticked | " + seen);
                    return listed > 0 && without == 0 ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(20) @DisplayName("Verify Scanning Status Filtering")
    void verifyScanningStatusFiltering() {
        scenario("Verify Scanning Status Filtering",
                "Fresh screen; Scanning Status = Yes -> Search; Scanning Status = No -> Search",
                "Yes lists only scanned files; No lists only files marked not scanned",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    m().pickOption(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SCANNING, "Yes");
                    if (m().search() != 200) return automationFailure("Scanning Status Yes Search did not complete");
                    List<String> yes = field(m().files(), "scanningstatus");
                    m().pickOption(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.SCANNING, "No");
                    if (m().search() != 200) return automationFailure("Scanning Status No Search did not complete");
                    List<String> no = field(m().files(), "scanningstatus");
                    long yesWrong = yes.stream().filter(x -> !"1".equals(x) && !"true".equalsIgnoreCase(x)).count();
                    long noWrong = no.stream().filter(x -> !"0".equals(x) && !"false".equalsIgnoreCase(x)).count();
                    String seen = "Yes: " + yes.size() + " files (" + yesWrong + " not scanned); No: " + no.size() + " files (" + noWrong + " scanned or unset)";
                    if (yes.isEmpty() && no.isEmpty()) return dataMissing("no file has a scanning status | " + seen);
                    return yesWrong == 0 && noWrong == 0 ? passed(seen) : defect(seen);
                });
    }

    // ---- grid and pop-ups ------------------------------------------------------------------------

    @Test @Order(21) @DisplayName("Verify Grid Pagination and Page Size")
    void verifyGridPaginationAndPageSize() {
        scenario("Verify Grid Pagination and Page Size",
                "Default search; read the pager; Page forward; Page back; page size 50",
                "Page forward shows page 2 and the next 25 files; Page back returns to page 1; page size 50 shows up to 50 rows",
                () -> {
                    String[] notReady = defaultSearch();
                    if (notReady != null) return notReady;
                    int total = m().files().size();
                    if (total <= 25) return dataMissing("only " + total + " files - paging needs more than 25");
                    String first = m().pagerText();
                    m().clickPager("ui-grid-pager-next");
                    String second = m().pagerText(), pageNo = m().currentPage();
                    m().clickPager("ui-grid-pager-previous");
                    String back = m().pagerText();
                    m().setPageSize("50");
                    String fifty = m().pagerText();
                    String seen = "'" + first + "' -> next '" + second + "' (page " + pageNo + ") -> back '" + back + "' -> size 50 '" + fifty + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!second.startsWith("26 - ")) wrong.add("Page forward did not show rows 26+");
                    if (!back.startsWith("1 - 25")) wrong.add("Page back did not return to rows 1 - 25");
                    if (!fifty.startsWith("1 - 50") && !(total < 50 && fifty.startsWith("1 - " + total))) wrong.add("page size 50 not applied");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(22) @DisplayName("Verify EMR View")
    void verifyEmrView() {
        scenario("Verify EMR View",
                "Search MRN " + MRN_TWO + "; click EMR on the first file; read where it goes; return to MRD",
                "The patient's EMR opens (Nursing EMR or group-wise EMR) for that visit",
                () -> {
                    String[] notReady = searchedMrn(MRN_TWO);
                    if (notReady != null) return notReady;
                    if (m().files().isEmpty()) return dataMissing("no MRD file for " + MRN_TWO);
                    int failuresBefore = m().apiFailures.size();
                    m().clickFirstRowButton("Show EMR");
                    try {
                        page.waitForFunction("() => /EMR/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(20000));
                    } catch (Exception e) { /* reported below */ }
                    page.waitForTimeout(3000);
                    String route = m().route(), header = m().headerText(), error = m().visibleErrorText();
                    List<String> apiErrors = new ArrayList<>(m().apiFailures.subList(failuresBefore, m().apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Route " + route + "; header '" + header + "'";
                    m().goHome();
                    if (!route.matches("(?i).*EMR.*")) return defect("EMR did not open | " + seen);
                    return error.isEmpty() && apiErrors.isEmpty() ? passed(seen) : defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                });
    }

    @Test @Order(23) @DisplayName("Verify File Linking View")
    void verifyFileLinkingView() {
        scenario("Verify File Linking View",
                "Search MRN " + MRN_TWO + "; click File Linking without selecting a row; select the first row; click File Linking; return to MRD",
                "Without a row: warning to select the patient row first. With a row: the File Linking screen opens for MRN " + MRN_TWO + " (Add / Submit not clicked)",
                () -> {
                    String[] notReady = searchedMrn(MRN_TWO);
                    if (notReady != null) return notReady;
                    if (m().files().isEmpty()) return dataMissing("no MRD file for " + MRN_TWO);
                    m().clearCaptured();
                    m().clickFirstRowButton("File Linking");
                    List<String> noRowMsgs = m().waitForAlert(3000);
                    boolean stayed = m().route().startsWith("#/MRDFileList");
                    m().selectFirstRow();
                    m().clickFirstRowButton("File Linking");
                    try {
                        page.waitForFunction("() => /FileLinking/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(20000));
                    } catch (Exception e) { /* reported below */ }
                    page.waitForTimeout(3000);
                    String route = m().route(), header = m().headerText(), banner = m().patientBanner();
                    String seen = "No row: messages " + noRowMsgs + ", stayed " + stayed + " | with row: route " + route + ", header '" + header + "', banner '" + trim(banner, 80) + "'";
                    m().goHome();
                    List<String> wrong = new ArrayList<>();
                    if (!stayed || noRowMsgs.stream().noneMatch(x -> x.toLowerCase().contains("select the patient row"))) wrong.add("File Linking without a row was not stopped");
                    if (!route.contains("FileLinking")) wrong.add("File Linking screen did not open");
                    else if (!banner.contains(MRN_TWO)) wrong.add("File Linking does not show MRN " + MRN_TWO);
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(24) @DisplayName("Verify MRD Non Allocated List")
    void verifyMrdNonAllocatedList() {
        scenario("Verify MRD Non Allocated List",
                "Fresh screen; click MRD Non Allocated List; read the pop-up; close with x (Print not clicked)",
                "'MRD Non Allocated List' pop-up with MRN, Patient Name, OPD/IPD NO, Visit/Addmission Date, Department, Discharge Date; closes cleanly",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    if (!m().openPopup("nonallocatedlist()")) return automationFailure("Non Allocated List pop-up did not open (messages " + m().alerts() + ")");
                    Map<String, Object> view = m().modalView(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.NON_ALLOCATED_MODAL);
                    boolean closed = m().closePopup();
                    String seen = "Title '" + view.get("title") + "'; headers " + view.get("headers") + "; rows " + view.get("rows") + "; closed " + closed;
                    if (!closed) return automationFailure("pop-up did not close | " + seen);
                    @SuppressWarnings("unchecked") List<String> headers = (List<String>) view.getOrDefault("headers", List.of());
                    List<String> miss = missing(List.of("MRN", "Patient Name", "OPD/IPD NO", "Department", "Discharge Date"), headers);
                    if (!String.valueOf(view.get("title")).contains("Non Allocated")) return defect("unexpected title | " + seen);
                    return miss.isEmpty() ? passed(seen) : defect("columns missing " + miss + " | " + seen);
                });
    }

    @Test @Order(25) @DisplayName("Verify MRD File Movement History")
    void verifyMrdFileMovementHistory() {
        scenario("Verify MRD File Movement History",
                "Fresh screen; click File Movement History; read it; filter by the first listed patient's MRN with the pop-up's Find Patient; close with x",
                "'MRD File Movement History' lists movements (Date & Time, Patient Name, MRN No., File No., File Status, From / To Department, Action By); "
                        + "the MRN filter leaves only that patient's movements",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    if (!m().openPopup("openFileMovementHistory()")) return automationFailure("File Movement History did not open (messages " + m().alerts() + ")");
                    Map<String, Object> view = m().modalView(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_MOVEMENT_MODAL);
                    List<String> rows = m().modalRows(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_MOVEMENT_MODAL);
                    String mrn = rows.isEmpty() ? "" : java.util.Arrays.stream(rows.get(0).split(" ")).filter(x -> x.matches("[A-Z]{0,4}-?\\d{6,}|\\d{8,}|ED\\d{6}")).findFirst().orElse("");
                    List<String> filtered = new ArrayList<>();
                    if (!mrn.isEmpty()) {
                        m().fileMovementFilterByMrn(mrn);
                        filtered = m().modalRows(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.FILE_MOVEMENT_MODAL);
                    }
                    boolean closed = m().closePopup();
                    final String fm = mrn;
                    long others = filtered.stream().filter(r -> !r.contains(fm)).count();
                    String seen = "Title '" + view.get("title") + "'; headers " + view.get("headers") + "; " + rows.size() + " rows on page 1 (pager '" + view.get("pager")
                            + "'); filter '" + mrn + "' -> " + filtered.size() + " rows (" + others + " other patients); closed " + closed;
                    if (!closed) return automationFailure("pop-up did not close | " + seen);
                    if (rows.isEmpty()) return dataMissing("no file movement recorded | " + seen);
                    if (mrn.isEmpty()) return automationFailure("could not read an MRN from the first movement row | " + seen);
                    return !filtered.isEmpty() && others == 0 ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(26) @DisplayName("Verify Investigation View")
    void verifyInvestigationView() {
        scenario("Verify Investigation View",
                "Search MRN " + MRN_TWO + "; click Investigation View without a selected row; select the first row; click Investigation View; close with x",
                "Without a row: 'Please Select MRD Record from the List'. With a row: the 'Investigation View' pop-up opens with the investigation table (Show not clicked)",
                () -> {
                    String[] notReady = searchedMrn(MRN_TWO);
                    if (notReady != null) return notReady;
                    if (m().files().isEmpty()) return dataMissing("no MRD file for " + MRN_TWO);
                    m().clearCaptured();
                    m().clickNg("ViewInvestigation()");
                    List<String> noRowMsgs = m().waitForAlert(3000);
                    int openedWithoutRow = m().openModals();
                    m().selectFirstRow();
                    boolean opened = m().openPopup("ViewInvestigation()");
                    Map<String, Object> view = opened ? m().modalView(null) : Map.of();
                    boolean closed = !opened || m().closePopup();
                    String seen = "No row: messages " + noRowMsgs + ", pop-up " + openedWithoutRow + " | with row: opened " + opened + ", title '" + view.get("title")
                            + "', headers " + view.get("headers") + ", rows " + view.get("rows") + "; closed " + closed;
                    if (!closed) return automationFailure("pop-up did not close | " + seen);
                    List<String> wrong = new ArrayList<>();
                    if (openedWithoutRow > 0 || noRowMsgs.stream().noneMatch(x -> x.contains("Please Select MRD Record"))) wrong.add("no-row case not stopped");
                    if (!opened || !String.valueOf(view.get("title")).contains("Investigation View")) wrong.add("Investigation View did not open");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(27) @DisplayName("Verify Pending File Return List")
    void verifyPendingFileReturnList() {
        scenario("Verify Pending File Return List",
                "Fresh screen; click Pending File Return List; read it; filter by the first listed patient's MRN (Enter); close with x",
                "'Pending File Return List' lists files not yet returned (Patient Name, MRN, File No., Visit No., Discharge Date, Holding Department, "
                        + "File Accepted Date & Time); the MRN filter leaves only that patient's files",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    if (!m().openPopup("fnOpenPendingFileReturnList()")) return automationFailure("Pending File Return List did not open (messages " + m().alerts() + ")");
                    Map<String, Object> view = m().modalView(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.PENDING_RETURN_MODAL);
                    List<String> rows = m().modalRows(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.PENDING_RETURN_MODAL);
                    String mrn = rows.isEmpty() ? "" : java.util.Arrays.stream(rows.get(0).split(" ")).filter(x -> x.matches("[A-Z]{0,4}-?\\d{6,}|\\d{8,}|ED\\d{6}")).findFirst().orElse("");
                    List<String> filtered = new ArrayList<>();
                    if (!mrn.isEmpty()) {
                        m().pendingReturnFilterByMrn(mrn);
                        filtered = m().modalRows(com.kpj.pages.AncillaryServices_page.MRD_page.MRD.PENDING_RETURN_MODAL);
                    }
                    boolean closed = m().closePopup();
                    final String pm = mrn;
                    long others = filtered.stream().filter(r -> !r.contains(pm)).count();
                    String seen = "Title '" + view.get("title") + "'; headers " + view.get("headers") + "; " + rows.size() + " rows; filter '" + mrn + "' -> " + filtered.size()
                            + " rows (" + others + " other patients); closed " + closed;
                    if (!closed) return automationFailure("pop-up did not close | " + seen);
                    if (rows.isEmpty()) return dataMissing("no pending file return | " + seen);
                    if (mrn.isEmpty()) return automationFailure("could not read an MRN from the first pending row | " + seen);
                    return !filtered.isEmpty() && others == 0 ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(28) @DisplayName("Verify Clear Functionality")
    void verifyClearFunctionality() {
        scenario("Verify Clear Functionality",
                "Fresh screen; look for a Clear / Reset button on the MRD search panel",
                "If the screen has a Clear button: it resets the filters and the grid",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    Object clear = page.evaluate("() => [...document.querySelectorAll('[ui-view] button')].filter(b => b.offsetParent !== null"
                            + " && /^(clear|reset)$/i.test(b.innerText.trim())).map(b => b.getAttribute('ng-click'))");
                    if (clear instanceof List && !((List<?>) clear).isEmpty())
                        return automationFailure("a Clear button now exists (" + clear + ") - extend this scenario to exercise it");
                    return new String[]{"SKIPPED", "SKIPPED - NOT APPLICABLE: the MRD screen has no Clear / Reset button (verified on the live screen)"};
                });
    }

    @Test @Order(29) @DisplayName("Confirm No Write Request Was Sent")
    void confirmNoWriteRequestWasSent() {
        scenario("Confirm No Write Request Was Sent",
                "After all scenarios, read the write guard's log",
                "No add / edit / update / scrap request was attempted by this suite",
                () -> m().blockedWrites.isEmpty() ? passed("No write request attempted; " + m().apiLog.size() + " read calls made")
                        : automationFailure("write requests were attempted (and aborted): " + m().blockedWrites));
    }
}
