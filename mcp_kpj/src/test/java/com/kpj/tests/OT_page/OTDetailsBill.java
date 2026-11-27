package com.kpj.tests.OT_page;

import com.kpj.core.DevHisBase;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

// The page object has the same simple name — it is referenced by its fully-qualified name.

/**
 * Operation Theatre &gt; <b>OT Details - Bill</b> ({@code #/OTDetailsListopdbill}) — full functional test (OB-01 … OB-24).
 *
 * <p>List: page load and defaults, Search by date range, the data the grid shows, MRN search and filter, OT Theatre / OT Table filter, the
 * Status checkbox, Edit, Bill (OPD and IPD records), Print, Investigation View, File Linking and Back. Form (New): Title list, Reg. Type default,
 * validations (patient, MRN, From Time, OT Theatre), the "use current time" checkboxes, Total Minutes, Save, the screen shown after Save, and Bill on
 * the record just saved. Console / API errors at the end. Report: {@code test-output/reports/OT_OTDetailsBill.html}.</p>
 *
 * <h3>Data</h3>
 * <ul>
 *   <li><b>Each run creates one OT Details record</b> (Save) for the admitted patient {@code -Dob.mrn} (default 1100339440), today 10:00–11:00 AM,
 *       General OT, Successful, Completed, Minor, the first Anesthesia Type. Use {@code -Dob.save=false} to skip the Save cases.</li>
 *   <li>The Status checkbox is clicked on one record and, if the change was saved, clicked again so the record ends Active.</li>
 *   <li>Bill only opens the OPD / IPD Bill screen and checks the patient was handed over — no bill is generated or saved.</li>
 *   <li>The list search range is {@code -Dob.from} (default: 25 days ago) to today.</li>
 * </ul>
 *
 * <pre>
 *   mvn -o test -Dtest=com.kpj.tests.OT_page.OTDetailsBill -Ddevhis.postrun=off
 * </pre>
 */
public class OTDetailsBill extends DevHisBase {

    public OTDetailsBill() { super("OT_OTDetailsBill"); }

    public static void main(String[] args) {
        OTDetailsBill t = new OTDetailsBill();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern API = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);

    private com.kpj.pages.OT_page.OTDetailsBill ob;
    private final LocalDate today = LocalDate.now();
    private final String from = System.getProperty("ob.from", today.minusDays(25).format(DMY));
    private final String mrnNew = System.getProperty("ob.mrn", "1100339440");
    private final boolean doSave = !"false".equalsIgnoreCase(System.getProperty("ob.save", "true"));

    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> apiFailures = new CopyOnWriteArrayList<>();
    private final List<String> defects = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();
    private Map<String, Object> firstRow = new LinkedHashMap<>();
    private Object savedId = null;
    private int listRows = 0;

    private static final class R {
        String desc, expected, actual = "", status = "PASS";
        R(String desc, String expected) { this.desc = desc; this.expected = expected; }
        R set(String actual, boolean ok) { this.actual = actual; this.status = ok ? "PASS" : "FAIL"; return this; }
        R set(String actual, String status) { this.actual = actual; this.status = status; return this; }
    }
    private interface Body { R run(); }

    // =================================================================================================================

    @Override
    protected void body() {
        meta("Operation Theatre - OT Details - Bill", "Operation Theatre > OT Details - Bill",
                "Full functional test of the list (search, filters, Status, Edit, Bill, Print, Investigation View, File Linking, Back) and the New form "
                        + "(validations, time entry, Save, Bill on the saved record). Each run saves one OT Details record for MRN " + mrnNew + ".");
        attachMonitors();
        login();
        step("OB-00 Login", "Log in with the configured test account (values not shown)", "Authenticated; application shell loaded", "Logged in to " + BASE, "PASS");
        ob = new com.kpj.pages.OT_page.OTDetailsBill(page);
        try {
            tc("OB-01 Page Loading", this::pageLoading);
            if (!ob.onList()) { addSummary("Result", "FAILED — OT Details - Bill could not be opened; remaining cases not run"); return; }
            ob.spyAlerts();
            tc("OB-02 Default Filters", this::defaults);
            tc("OB-03 Search by Date Range", this::searchRange);
            tc("OB-04 Grid Data Completeness", this::gridData);
            tc("OB-05 MRN Search Button (listed patient)", this::mrnFind);
            tc("OB-06 MRN Filter on Search", this::mrnFilter);
            tc("OB-07 OT Theatre / OT Table Filter", this::theatreFilter);
            tc("OB-08 Status Checkbox", this::statusToggle);
            tc("OB-09 Edit (listed record)", this::editListed);
            tc("OB-10 Bill (listed record)", this::billListed);
            tc("OB-11 Print", this::print);
            tc("OB-12 Investigation View", this::investigation);
            tc("OB-13 File Linking", this::fileLinking);
            tc("OB-15 New - Form Opens", this::newForm);
            tc("OB-16 New - Empty Save / Empty MRN", this::newEmpty);
            tc("OB-17 New - Patient Search (IPD)", this::newPatient);
            tc("OB-18 New - Mandatory Field Messages", this::newMandatory);
            tc("OB-19 New - Use Current Time", this::useCurrentTime);
            tc("OB-20 New - Times and Total Minutes", this::times);
            if (doSave) {
                tc("OB-21 New - Save", this::save);
                tc("OB-22 Screen After Save", this::afterSave);
                tc("OB-23 Bill (record just saved)", this::billSaved);
            }
            tc("OB-24 Console / Network Errors", this::consoleCheck);
            // Back runs last: after a visit to #/NursingEMR, page screenshots time out for the rest of the run.
            tc("OB-14 Back", this::back);
        } finally { writeSummary(); }
    }

    // =================================================================================================================
    // A. List
    // =================================================================================================================

    private R pageLoading() {
        R r = new R("Open Operation Theatre > OT Details - Bill from the sidebar", "The list opens with the date filters, OT Theatre / OT Table, MRN, Reg. Type, Search, the grid and its buttons");
        boolean ok = ob.navigateViaMenu();
        if (!ok) return r.set("Did NOT open — " + ob.navNotes + " (" + page.url() + ")", false);
        Map<String, String> b = ob.listButtons();
        List<String> need = List.of("Search", "Investigation View", "Print", "File Linking", "New", "Back"), missing = new ArrayList<>();
        for (String n : need) if (!b.containsKey(n)) missing.add(n);
        List<String> h = ob.headers();
        return r.set("Loaded in " + secs(ob.lastLoadMs) + "; buttons " + b.keySet() + "; columns " + h + (missing.isEmpty() ? "" : "; MISSING " + missing), missing.isEmpty() && h.contains("Bill"));
    }

    private R defaults() {
        R r = new R("Read the default filters", "From / To Date = today; Reg. Type IPD selected");
        Map<String, Object> m = ob.listModel();
        String t = today.format(DMY);
        boolean ok = t.equals(m.get("from")) && t.equals(m.get("to")) && String.valueOf(m.get("regTypes")).contains("(checked)");
        return r.set("From " + m.get("from") + ", To " + m.get("to") + ", Reg. Type " + m.get("regTypes"), ok);
    }

    private R searchRange() {
        R r = new R("Set From Date = " + from + ", To Date = today, click Search", "OT records of the period are listed, each dated inside the range");
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_FROM, from);
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_TO, today.format(DMY));
        long ms = ob.search();
        listRows = ob.rows();
        List<Map<String, Object>> d = ob.data("id", "mrno", "patientname", "date", "opd_ipd", "Status", "procedurename");
        if (listRows <= 0) return r.set("No OT records between " + from + " and today (" + secs(ms) + ")", "MANUAL");
        firstRow = d.get(0);
        List<String> outside = new ArrayList<>();
        for (Map<String, Object> x : d) {
            LocalDate dt = parse(s(x.get("date")));
            if (dt == null || dt.isBefore(parse(from)) || dt.isAfter(today)) outside.add(x.get("id") + " " + x.get("date"));
        }
        Set<String> ids = new TreeSet<>(); for (Map<String, Object> x : d) ids.add(s(x.get("id")));
        Set<String> types = new TreeSet<>(); for (Map<String, Object> x : d) types.add(s(x.get("opd_ipd")).matches("true|1") ? "IPD" : "OPD");
        if (types.contains("OPD")) observation("OB-03", "The IPD-only screen (Reg. Type IPD) lists OPD records (opd_ipd = 0)");
        return r.set(listRows + " row(s), " + ids.size() + " OT record(s), types " + types + ", in " + secs(ms) + "; first: MRN " + firstRow.get("mrno") + " " + s(firstRow.get("patientname"))
                + (outside.isEmpty() ? "" : "; OUTSIDE range: " + head(outside, 5)), outside.isEmpty());
    }

    private R gridData() {
        R r = new R("Check the grid columns Doctor Name, Ward, Bed Name, Start Time, End Time, OT Room and OT Table", "Each listed record shows these details");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("Doctor Name", "doctorname"); cols.put("Ward", "ward"); cols.put("Bed Name", "bedname"); cols.put("Start Time", "fromtime");
        cols.put("End Time", "totime"); cols.put("OT Room", "ottheatrename"); cols.put("OT Table", "ottable");
        List<Map<String, Object>> d = ob.data(cols.values().toArray(new String[0]));
        List<String> blank = new ArrayList<>();
        for (Map.Entry<String, String> c : cols.entrySet()) {
            long empty = d.stream().filter(x -> s(x.get(c.getValue())).isEmpty()).count();
            if (empty > 0) blank.add(c.getKey() + " blank in " + empty + "/" + d.size());
        }
        if (!blank.isEmpty()) defect("OB-04", "High", "Grid columns are empty: " + head(blank, 7));
        return r.set(blank.isEmpty() ? "All " + cols.size() + " columns filled for " + d.size() + " row(s)" : "Blank: " + blank, blank.isEmpty());
    }

    private R mrnFind() {
        R r = new R("Type the MRN of the first listed record and click the search button beside MRN", "The patient is found (patient id set, no \"Patient Not Found!\")");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        String mrn = s(firstRow.get("mrno"));
        int a0 = ob.alerts().size();
        ob.typeMrn(mrn);
        ob.clickListMrnSearch();
        List<String> a = since(a0);
        Object pid = ob.listModel().get("patientid");
        boolean ok = pid instanceof Number && ((Number) pid).intValue() > 0;
        if (!ok) defect("OB-05", "Medium", "The MRN search returns \"Patient Not Found!\" for MRN " + mrn + ", a patient listed on this screen (the search looks for IPD only)");
        return r.set("MRN " + mrn + ": patient id " + pid + "; alerts " + (a.isEmpty() ? "none" : a), ok);
    }

    private R mrnFilter() {
        R r = new R("With the MRN still typed, click Search", "Only that patient's OT records are listed");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        String mrn = s(firstRow.get("mrno"));
        ob.search();
        Set<String> mrns = new TreeSet<>(); for (Map<String, Object> x : ob.data("mrno")) mrns.add(s(x.get("mrno")));
        boolean ok = mrns.equals(Set.of(mrn));
        if (!ok) defect("OB-06", "Medium", "Search ignores the typed MRN " + mrn + " and lists " + mrns.size() + " patients");
        ob.typeMrn("");
        return r.set(ob.rows() + " row(s) for MRN(s) " + head(mrns, 6), ok);
    }

    private R theatreFilter() {
        R r = new R("Pick the first OT Theatre, check the OT Table list, click Search", "OT Table lists that theatre's tables; only records of that theatre are listed");
        List<String> th = ob.options(com.kpj.pages.OT_page.OTDetailsBill.NG_THEATRE);
        if (th.isEmpty()) return r.set("OT Theatre list is empty", false);
        String t = th.contains("General OT") ? "General OT" : th.get(0);
        String p = ob.pick(com.kpj.pages.OT_page.OTDetailsBill.NG_THEATRE, t);
        if (!p.isEmpty()) return r.set("Could not pick \"" + t + "\": " + p, false);
        List<String> tables = ob.options(com.kpj.pages.OT_page.OTDetailsBill.NG_TABLE);
        ob.search();
        Set<String> rooms = new TreeSet<>(); for (Map<String, Object> x : ob.data("ottheatrename")) rooms.add(s(x.get("ottheatrename")));
        boolean ok = !tables.isEmpty() && rooms.stream().allMatch(x -> x.equals(t));
        String res = "Theatre \"" + t + "\": tables " + tables + "; " + ob.rows() + " row(s), OT Room " + rooms;
        ob.reopen();
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_FROM, from);
        ob.search();
        ob.spyAlerts();
        return r.set(res, ok);
    }

    private R statusToggle() {
        R r = new R("Untick the Status checkbox of the first record, reopen the list, check the record's Status; put it back",
                "\"Status updated\" message; the record is saved Inactive; it can be set Active again");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        Object id = firstRow.get("id");
        int a0 = ob.alerts().size();
        List<String> errs0 = new ArrayList<>(consoleErrors);
        if (!ob.clickRow("grid.appScope.changeStatus($event,row)", id)) return r.set("Status checkbox of record " + id + " not clickable", false);
        page.waitForTimeout(1500);
        List<String> a = since(a0);
        List<String> newErr = new ArrayList<>(consoleErrors); newErr.removeAll(errs0);
        List<Object> shown = ob.statusOf(id);
        ob.reopen(); ob.spyAlerts();
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_FROM, from);
        ob.search();
        List<Object> saved = ob.statusOf(id);
        boolean changed = saved.stream().anyMatch(x -> Boolean.FALSE.equals(x));
        if (changed) { ob.clickRow("grid.appScope.changeStatus($event,row)", id); page.waitForTimeout(1500); }
        boolean ok = changed && a.stream().anyMatch(x -> x.toLowerCase().contains("success"));
        if (!ok) defect("OB-08", "High", "The Status checkbox does not save (record " + id + " still " + saved + " after reopening); "
                + (newErr.isEmpty() ? "no console error" : "console: " + trim(newErr.get(0), 120)) + "; message: " + (a.isEmpty() ? "none" : a));
        return r.set("Record " + id + ": grid showed " + shown + " right after the click; alerts " + (a.isEmpty() ? "none" : a) + "; after reopening " + saved
                + (newErr.isEmpty() ? "" : "; console: " + trim(newErr.get(0), 140)) + (changed ? "; put back to Active" : ""), ok);
    }

    private R editListed() {
        R r = new R("Click Edit on the first listed record, then Back", "The OT Details form opens with the patient and the OT sheet; Back returns to the list");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        Object id = firstRow.get("id");
        int a0 = ob.alerts().size();
        if (!ob.clickRow("grid.appScope.editOTSchedule(row)", id)) return r.set("Edit of record " + id + " not clickable (disabled?)", false);
        boolean form = ob.waitForm();
        Map<String, Object> f = ob.formState();
        List<String> a = since(a0);
        step(page, "OB-09a Edit form", "Screenshot of the form opened by Edit", "Patient and OT sheet loaded", "Route " + ob.hash() + "; " + f + "; alerts " + a, form && !s(f.get("name")).isEmpty() ? "PASS" : "FAIL");
        ob.clickFormBack();
        boolean back = ob.onList() || ob.waitList();
        boolean ok = form && !s(f.get("name")).isEmpty() && !s(f.get("theatre")).matches("|-Select-") && back;
        if (form && s(f.get("name")).isEmpty()) defect("OB-09", "High", "Edit of record " + id + " (MRN " + firstRow.get("mrno") + ") shows \"Patient Not Found!\" — the patient section stays empty");
        if (!ob.onList()) { ob.reopen(); ob.spyAlerts(); }
        return r.set("Form " + (form ? "opened" : "did NOT open") + ": patient \"" + f.get("name") + "\", theatre \"" + f.get("theatre") + "\", Save visible " + f.get("saveVisible")
                + "; alerts " + (a.isEmpty() ? "none" : a) + "; Back → " + (back ? "list" : ob.hash()), ok);
    }

    private R billListed() {
        R r = new R("Click Bill on the first listed record", "OPD Bill (OPD record) or IPD Bill (IPD record) opens with the patient loaded");
        if (listRows <= 0) return r.set("No rows (OB-03)", "MANUAL");
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_FROM, from);
        ob.search();
        return billFor(r, firstRow.get("id"), s(firstRow.get("mrno")), "OB-10");
    }

    private R billFor(R r, Object id, String mrn, String tag) {
        int a0 = ob.alerts().size();
        if (!ob.clickRow("grid.appScope.GetBill(row)", id)) return r.set("Bill of record " + id + " not clickable", false);
        page.waitForTimeout(5000);
        ob.waitForHttpIdle(30000);
        String route = ob.hash(), billMrn = ob.billScreenMrn();
        List<String> a = since(a0);
        boolean ok = route.matches("(?i)#/(OPD|IPD)Bill") && mrn.equals(billMrn);
        if (!ok) defect(tag, "High", "Bill for record " + id + " (MRN " + mrn + ") opens " + route + " without the patient (MRN box \"" + billMrn + "\"); message: " + (a.isEmpty() ? "none" : a));
        String res = "Opened " + route + "; MRN box \"" + billMrn + "\"; alerts " + (a.isEmpty() ? "none" : a);
        step(page, tag + "a Bill screen", "Screenshot of the bill screen", "Patient " + mrn + " loaded", res, ok ? "PASS" : "FAIL");
        ob.reopen(); ob.spyAlerts();
        return r.set(res, ok);
    }

    private R print() {
        R r = new R("Click Print", "The OT Details report opens in a new tab for the current filters");
        ob.typeDate(com.kpj.pages.OT_page.OTDetailsBill.SEL_FROM, from);
        Page rep;
        try {
            rep = page.context().waitForPage(new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(20000), () -> ob.clickNg("printOtReport()", 10000));
        } catch (Exception e) { return r.set("No report tab opened: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        String url = rep.url();
        try { rep.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); } catch (Exception ignore) { }
        boolean ok = url.contains("OtDetailsReport") && url.contains("FromDate=" + from);
        step(rep, "OB-11a Print report tab", "Screenshot of the report tab", "OT Details report", "URL " + trim(url.replaceAll("^https?://[^/]+", ""), 260), ok ? "PASS" : "FAIL");
        try { rep.close(); } catch (Exception ignore) { }
        return r.set("Report " + trim(url.replaceAll("^https?://[^/]+", ""), 260), ok);
    }

    private R investigation() {
        R r = new R("Click Investigation View", "The Investigation View popup opens");
        if (!ob.clickInvestigationView()) return r.set("Button not clickable", false);
        boolean shown = ob.modalShown("EMRinvestignView");
        String res = "Popup " + (shown ? "opened" : "NOT opened");
        step(page, "OB-12a Investigation View popup", "Screenshot of the popup", "Popup with Pathology / Radiology / Cardiology", res, shown ? "PASS" : "FAIL");
        ob.closeModal("EMRinvestignView");
        return r.set(res + (shown ? " (no patient is asked for; the lists are empty without one)" : ""), shown);
    }

    private R fileLinking() {
        R r = new R("Click File Linking (no patient loaded), then load the first listed patient with the MRN search and click again",
                "Without a patient: \"Please Select Patient!\"; with one: the File Linking screen opens");
        int a0 = ob.alerts().size();
        ob.clickNg("ViewFileLinking()", 10000);
        List<String> a = since(a0);
        boolean warned = a.stream().anyMatch(x -> x.toLowerCase().contains("select patient"));
        String mrn = s(firstRow.get("mrno"));
        if (!mrn.isEmpty()) { ob.typeMrn(mrn); ob.clickListMrnSearch(); }
        boolean loaded = ob.listModel().get("patientid") instanceof Number && ((Number) ob.listModel().get("patientid")).intValue() > 0;
        String route = "";
        if (loaded) { ob.clickNg("ViewFileLinking()", 20000); page.waitForTimeout(2000); route = ob.hash(); }
        boolean ok = warned && loaded && route.toLowerCase().contains("filelinking");
        if (!loaded) defect("OB-13", "Medium", "File Linking cannot be used for the listed patients: it needs a patient, and the MRN search cannot find them (OB-05)");
        String res = "No patient: alerts " + (a.isEmpty() ? "none" : a) + "; MRN " + mrn + " loaded=" + loaded + (loaded ? "; File Linking → " + route : "");
        if (!ob.onList()) { ob.reopen(); ob.spyAlerts(); } else ob.typeMrn("");
        return r.set(res, ok);
    }

    private R back() {
        R r = new R("Click Back on the list", "The EMR screen opens");
        ob.clickNg("BackForm()", 20000);
        page.waitForTimeout(3000);
        String route = ob.hash();
        boolean emr = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"select[ng-model='EMRTagID']\") || !document.querySelector(\"button[ng-click='fngetOTScheduleGrid()']\")"));
        boolean ok = route.contains("NursingEMR") && emr;
        if (!ok) defect("OB-14", "Medium", "Back changes the address to " + route + " but the screen does not change (the OT list stays)");
        String res = "Address " + route + "; " + (emr ? "EMR screen shown" : "OT list still shown");
        step(page, "OB-14a After Back", "Screenshot after Back", "EMR screen", res, ok ? "PASS" : "FAIL");
        ob.reopen(); ob.spyAlerts();
        return r.set(res, ok);
    }

    // =================================================================================================================
    // B. New form
    // =================================================================================================================

    private R newForm() {
        R r = new R("Click New", "The OT Details form opens with the Title list filled and Reg. Type IPD selected (the only type on this screen)");
        int f0 = apiFailures.size();
        ob.clickNg("OTDetails()", 20000);
        if (!ob.onForm()) ob.clickNg("OTDetails()", 20000);   // the first click is sometimes swallowed while the list settles
        boolean form = ob.waitForm();
        if (!form) return r.set("Form did not open (" + ob.hash() + ")", false);
        Map<String, Object> f = ob.formState();
        List<String> failed = new ArrayList<>(apiFailures.subList(f0, apiFailures.size()));
        int titles = ((Number) f.getOrDefault("titleOptions", -1)).intValue();
        boolean ok = titles > 1 && Boolean.TRUE.equals(f.get("ipdChecked")) && failed.isEmpty();
        if (titles <= 1) defect("OB-15", "Medium", "New form: the Title list is empty" + (failed.isEmpty() ? "" : " — " + head(failed, 2)));
        if (!Boolean.TRUE.equals(f.get("ipdChecked"))) defect("OB-15", "Low", "New form: Reg. Type IPD is not pre-selected, so the MRN search loads nothing until it is ticked");
        return r.set("Route " + ob.hash() + "; Title options " + titles + "; IPD pre-selected " + f.get("ipdChecked") + "; API failures " + (failed.isEmpty() ? "none" : failed), ok);
    }

    private R newEmpty() {
        R r = new R("Click Save with nothing filled; click the MRN search with the MRN empty", "\"Please Select Patient!\" and \"Please Enter MRN No!\"");
        int a0 = ob.alerts().size();
        ob.clickSave();
        List<String> a = since(a0);
        int a1 = ob.alerts().size();
        ob.clickFormMrnSearch();
        List<String> b = since(a1);
        boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("select patient")) && b.stream().anyMatch(x -> x.toLowerCase().contains("mrn"));
        return r.set("Save: " + a + "; MRN search: " + b, ok);
    }

    private R newPatient() {
        R r = new R("Type MRN " + mrnNew + " (admitted patient) and search, first as the form opens, then with IPD ticked",
                "The patient, ward, bed and doctor load");
        int a0 = ob.alerts().size();
        ob.formMrn(mrnNew);
        ob.clickFormMrnSearch();
        String before = s(ob.formState().get("name"));
        List<String> a = since(a0);
        ob.tickIpd();
        ob.formMrn(mrnNew);
        ob.clickFormMrnSearch();
        Map<String, Object> f = ob.formState();
        boolean ok = !s(f.get("name")).isEmpty();
        if (before.isEmpty() && ok) observation("OB-17", "With Reg. Type unticked (as the form opens) the MRN search loads nothing and shows no message; it works once IPD is ticked");
        return r.set("As opened: \"" + before + "\" (alerts " + (a.isEmpty() ? "none" : a) + "); with IPD: " + f.get("name") + " | " + f.get("ward") + " | " + f.get("bed") + " | " + f.get("doctor"), ok);
    }

    private R newMandatory() {
        R r = new R("With only the patient filled click Save; type the times and click Save again", "\"Please Add From Time!\", then \"Please Select OT Theatre!\"");
        if (s(ob.formState().get("name")).isEmpty()) return r.set("No patient loaded (OB-17)", "MANUAL");
        int a0 = ob.alerts().size();
        ob.clickSave();
        List<String> a = since(a0);
        ob.typeTime(0, "10:00 AM");
        ob.typeTime(1, "11:00 AM");
        int a1 = ob.alerts().size();
        ob.clickSave();
        List<String> b = since(a1);
        boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("from time")) && b.stream().anyMatch(x -> x.toLowerCase().contains("theatre"));
        return r.set("First Save: " + a + "; after the times: " + b, ok);
    }

    private R useCurrentTime() {
        R r = new R("Clear the times, tick \"use current time\" beside From Time and To Time", "From Time and To Time are filled with the current time");
        if (s(ob.formState().get("name")).isEmpty()) return r.set("No patient loaded (OB-17)", "MANUAL");
        ob.useCurrentTime(0, true);
        ob.useCurrentTime(1, true);
        Map<String, Object> f = ob.formState();
        boolean ok = !s(f.get("fromModel")).isEmpty() && !s(f.get("toModel")).isEmpty();
        if (!ok) defect("OB-19", "Medium", "Ticking \"use current time\" leaves From / To Time empty (model " + f.get("fromModel") + " / " + f.get("toModel") + ")");
        ob.useCurrentTime(0, false);
        ob.useCurrentTime(1, false);
        return r.set("After ticking: From \"" + f.get("from") + "\" (model " + f.get("fromModel") + "), To \"" + f.get("to") + "\" (model " + f.get("toModel") + ")", ok);
    }

    private R times() {
        R r = new R("Type From Time 10:00 AM and To Time 11:00 AM", "Both times are held and Total Minutes shows 60");
        if (s(ob.formState().get("name")).isEmpty()) return r.set("No patient loaded (OB-17)", "MANUAL");
        ob.typeTime(0, "10:00 AM");
        ob.typeTime(1, "11:00 AM");
        Map<String, Object> f = ob.formState();
        boolean held = !s(f.get("fromModel")).isEmpty() && !s(f.get("toModel")).isEmpty();
        boolean minutes = s(f.get("minutes")).equals("60");
        if (held && !minutes) defect("OB-20", "Low", "Total Minutes is not calculated from From / To Time (shows \"" + f.get("minutes") + "\")");
        return r.set("From \"" + f.get("from") + "\", To \"" + f.get("to") + "\", Total Minutes \"" + f.get("minutes") + "\"", held && minutes);
    }

    private R save() {
        R r = new R("Pick OT Theatre General OT, OT Result Successful, OT Status Completed, Procedure Type Minor and the first Anesthesia Type; click Save",
                "\"OT Detail Saved Successfully.\"");
        if (s(ob.formState().get("name")).isEmpty()) return r.set("No patient loaded (OB-17)", "MANUAL");
        if (s(ob.formState().get("fromModel")).isEmpty()) { ob.typeTime(0, "10:00 AM"); ob.typeTime(1, "11:00 AM"); }
        List<String> bad = new ArrayList<>();
        String[][] picks = {{"otsheetdetails.ottheatreid", "General OT"}, {"otsheetdetails.otresultid", "Successful"}, {"otsheetdetails.otstatusid", "Completed"},
                {"otsheetdetails.proceduretypeid", "Minor"}};
        for (String[] p : picks) { String e = ob.pick(p[0], p[1]); if (!e.isEmpty()) bad.add(p[1] + ": " + e); }
        List<String> an = ob.options("otsheetdetails.anesthesiatypeid");
        if (an.isEmpty()) bad.add("Anesthesia Type list empty"); else { String e = ob.pick("otsheetdetails.anesthesiatypeid", an.get(0)); if (!e.isEmpty()) bad.add(an.get(0) + ": " + e); }
        Map<String, Object> f = ob.formState();
        step(page, "OB-21a Filled form", "Screenshot before Save", "All mandatory fields filled", f.toString(), bad.isEmpty() ? "PASS" : "FAIL");
        int a0 = ob.alerts().size();
        ob.clickSave();
        page.waitForTimeout(2000);
        List<String> a = since(a0);
        boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("saved successfully"));
        return r.set((bad.isEmpty() ? "" : "Pick problems: " + bad + "; ") + "Save → " + (a.isEmpty() ? "no message" : a) + "; now on " + ob.hash(), ok);
    }

    private R afterSave() {
        R r = new R("Check the screen shown after Save and find the saved record in OT Details - Bill (today)", "The app returns to OT Details - Bill, which lists the new record");
        String route = ob.hash();
        boolean backHere = route.matches("(?i)#/OTDetailsListopdbill");
        if (!backHere) defect("OB-22", "Medium", "After Save the app opens " + route + " (plain OT Details list) instead of returning to OT Details - Bill");
        ob.reopen(); ob.spyAlerts();
        ob.search();
        List<Map<String, Object>> d = ob.data("id", "mrno", "date", "fromtime", "totime", "ottheatrename", "opd_ipd");
        Map<String, Object> mine = null;
        for (Map<String, Object> x : d) if (mrnNew.equals(s(x.get("mrno"))) && (mine == null || ((Number) x.get("id")).intValue() > ((Number) mine.get("id")).intValue())) mine = x;
        if (mine != null) savedId = mine.get("id");
        addSummary("Record created", mine == null ? "not found in the list" : "OT Details ID " + savedId + " — MRN " + mrnNew + ", " + mine.get("date") + " " + mine.get("fromtime") + "–" + mine.get("totime") + ", " + mine.get("ottheatrename"));
        return r.set("After Save: " + route + "; today's list: " + d.size() + " row(s); saved record " + (mine == null ? "NOT found" : mine), backHere && mine != null);
    }

    private R billSaved() {
        R r = new R("Click Bill on the record just saved (IPD)", "IPD Bill opens with the patient loaded");
        if (savedId == null) return r.set("Saved record not found (OB-22)", "MANUAL");
        return billFor(r, savedId, mrnNew, "OB-23");
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private R consoleCheck() {
        R r = new R("Console errors, page errors and failed API calls collected during the run", "None");
        return r.set("Console: " + (consoleErrors.isEmpty() ? "none" : consoleErrors.size() + " — " + head(consoleErrors, 4))
                + "; API failures: " + (apiFailures.isEmpty() ? "none" : apiFailures.size() + " — " + head(apiFailures, 4)), consoleErrors.isEmpty() && apiFailures.isEmpty());
    }

    private void tc(String name, Body b) {
        System.out.println("[OTDetailsBill] --- " + name + " ---");
        long t0 = System.currentTimeMillis();
        R r;
        try { r = b.run(); }
        catch (Throwable e) { r = new R("Run " + name, "Case completes").set("ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        step(page, name, r.desc, r.expected, r.actual + " [execution time " + secs(System.currentTimeMillis() - t0) + "]", r.status);
    }

    private List<String> since(int n0) {
        List<String> a = ob.alerts();
        List<String> out = new ArrayList<>();
        for (int i = Math.min(n0, a.size()); i < a.size(); i++) out.add(a.get(i).replaceFirst("^\\w*:\\s*", ""));
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    private void attachMonitors() {
        page.onConsoleMessage(m -> { if ("error".equals(m.type())) consoleErrors.add(route() + " " + trim(m.text(), 200)); });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 200)));
        page.onResponse(resp -> {
            if (!API.matcher(resp.url()).find()) return;
            Request q = resp.request();
            if (resp.status() >= 400) apiFailures.add(route() + " " + q.method() + " " + resp.url().replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", "") + " → HTTP " + resp.status());
        });
    }

    private void defect(String id, String severity, String text) { String d = "[" + severity + "] " + id + " " + text; if (!defects.contains(d)) defects.add(d); }
    private void observation(String id, String text) { String d = id + " " + text; if (!observations.contains(d)) observations.add(d); }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.OT_page.OTDetailsBill.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("List range", from + " – " + today.format(DMY) + ": " + listRows + " row(s)");
        addSummary("Patient used for New / Save", mrnNew + (doSave ? "" : " (Save skipped: -Dob.save=false)"));
        addSummary("Application defects found", defects.isEmpty() ? "None" : String.valueOf(defects.size()));
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        i = 1;
        for (String o : observations) addSummary("Observation " + (i++), o);
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 4));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 4));
    }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : u.replaceAll("^https?://[^/]+", "")) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static LocalDate parse(String s) { try { return LocalDate.parse(s.trim(), DMY); } catch (Exception e) { return null; } }
    private static String secs(long ms) { return ms < 0 ? "—" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0); }
    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String head(Collection<?> l, int n) {
        List<String> x = new ArrayList<>(); for (Object o : l) x.add(String.valueOf(o));
        return x.size() <= n ? String.join("; ", x) : String.join("; ", x.subList(0, n)) + "; … (+" + (x.size() - n) + " more)";
    }
}
