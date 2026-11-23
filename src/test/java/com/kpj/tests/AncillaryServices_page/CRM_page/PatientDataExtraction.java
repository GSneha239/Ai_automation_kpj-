package com.kpj.tests.AncillaryServices_page.CRM_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page objects are also named PatientDataExtraction / PatientExtractionReport — referenced by their fully-qualified names.

/**
 * Ancillary Services &gt; CRM &gt; <b>Patient Data Extraction</b> ({@code #/PatientAnalysisReport}), then Report &gt; Patient Extraction Report.
 *
 * <ol>
 *   <li>Tick <b>Visit Date</b> and enter From 14 days before To (the screen's today), click <b>Search</b>, verify patients are listed.</li>
 *   <li>Click the ✔ of a row, click <b>Save Data</b>.</li>
 *   <li>In <b>Extraction Details</b>: enter a motive, tick <b>Consent given</b>, click <b>OK</b> (the screen refuses: consent needs a document), choose a text file, click <b>Add</b>, click <b>OK</b>, verify the success toast.</li>
 *   <li>The screen opens <b>Patient Extraction Report</b> in another tab: click Search there and verify the new extraction is listed in <b>List Of Item Enquiry</b>.</li>
 * </ol>
 *
 * <p>The attached file is NOT a fixed path: a small text file is created in the operating system's temp folder at run time (any laptop has one); {@code -Dpatientdataextraction.file=<path>} uses a text file of your choice
 * instead (its name must contain exactly one dot and be a txt/pdf/png/jpg/jpeg/xlsx/docx/doc under 1 MB - the screen's own rule).</p>
 *
 * <p>This test SAVES A REAL EXTRACTION RECORD with its document on every run; it stays.</p>
 */
public class PatientDataExtraction extends DevHisBase {

    public PatientDataExtraction() { super("AncillaryServices_CRM_PatientDataExtraction"); }

    public static void main(String[] args) {
        PatientDataExtraction t = new PatientDataExtraction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** The text file to attach and how it was got (for the report). */
    private String fileNote = "";

    /** A text file to attach: the one named by {@code -Dpatientdataextraction.file=} when it is usable, else a small one created in the temp folder (no fixed path, so it works on any machine). */
    private Path attachFile() throws IOException {
        String prop = System.getProperty("patientdataextraction.file", "").trim();
        if (!prop.isEmpty()) {
            Path p = Paths.get(prop);
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            boolean oneDot = name.indexOf('.') >= 0 && name.indexOf('.') == name.lastIndexOf('.');
            String ext = oneDot ? name.substring(name.indexOf('.') + 1).toLowerCase() : "";
            boolean typeOk = List.of("txt", "pdf", "png", "jpg", "jpeg", "xlsx", "docx", "doc").contains(ext);
            if (Files.isRegularFile(p) && oneDot && typeOk && Files.size(p) < 1_000_000) { fileNote = "the file named by -Dpatientdataextraction.file (" + p.toAbsolutePath() + ")"; return p; }
            fileNote = "the file named by -Dpatientdataextraction.file was not usable (it must exist, have exactly one dot in its name, be a txt/pdf/png/jpg/jpeg/xlsx/docx/doc and be under 1 MB), so a temp file was created instead";
        }
        Path f = Files.createTempFile("extraction-consent-", ".txt");
        Files.writeString(f, "Patient data extraction - consent note\nCreated by the automated QA run on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n");
        f.toFile().deleteOnExit();
        if (fileNote.isEmpty()) fileNote = "a small text file created in the operating system's temp folder at run time (" + System.getProperty("java.io.tmpdir") + ")";
        return f;
    }

    @Override
    protected void body() {
        meta("Ancillary Services - CRM - Patient Data Extraction", "Ancillary Services > CRM > Patient Data Extraction",
                "Visit date range (From 2 weeks before To), Search, tick a row, Save Data; Extraction Details: motive, consent, OK, choose a text file, Add, OK, success toast; the Patient Extraction Report opens in another tab: Search and verify the new extraction is listed. SAVES A REAL EXTRACTION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CRM_page.PatientDataExtraction pd = new com.kpj.pages.AncillaryServices_page.CRM_page.PatientDataExtraction(page);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        boolean on = pd.navigateViaMenu();
        step("Open Patient Data Extraction", "Click Ancillary Services -> CRM -> Patient Data Extraction", "The Patient Data Extraction screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Patient Data Extraction not reached"); return; }

        // 1) Visit date: tick the box, From at least a week (here 14 days) before To
        LocalDate to = pd.defaultToDate();
        LocalDate from = to.minusDays(14);
        boolean ticked = pd.tickVisitDate();
        boolean fromOk = ticked && pd.enterVisitFrom(from);
        step("Enter visit date", "Tick Visit Date and enter From " + from.format(dmy) + " (14 days, i.e. more than a week, before To " + to.format(dmy) + ")", "The visit dates are accepted, From at least 1 week before To",
                fromOk ? "Visit Date ticked; From \"" + pd.lastFrom + "\" entered through the calendar and To is \"" + pd.lastTo + "\" (From is 14 days before To, at least the 1 week asked); the boxes and the screen's model hold them"
                        : "Problem — " + pd.problems, fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary("Result", "FAILED — visit date: " + pd.problems); return; }

        boolean searched = pd.clickSearch();
        List<String[]> rows = pd.rows();
        step("Click Search", "Click Search", "The Clinical Filter table lists patients",
                searched ? "Search clicked; the table shows " + rows.size() + " row(s) on this page, pager \"" + pd.lastPager + "\"" : "Problem — " + pd.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — search: " + pd.problems); return; }

        step("Verify records appear in the table", "Look at the Clinical Filter table", "Patient records are listed",
                !rows.isEmpty() ? "PASSES because the table lists " + rows.size() + " record(s) (pager \"" + pd.lastPager + "\"; columns " + pd.headings() + "): "
                        + rows.stream().limit(3).map(r -> r[0] + " / " + r[1] + " / " + r[2]).toList() + (rows.size() > 3 ? " ..." : "")
                        : "FAILS because the table is BLANK for " + pd.lastFrom + " - " + pd.lastTo, !rows.isEmpty() ? "PASS" : "FAIL");
        if (rows.isEmpty()) { addSummary("Result", "FAILED — the table is blank"); return; }

        // 2) the tick symbol and Save Data
        int index = pd.preferredRow();
        boolean tickedRow = pd.tickRow(index);
        step("Click the tick symbol to select a row", "Click the tick symbol (✔) in the Sel column of row " + (index + 1) + " (" + rows.get(index)[0] + " " + rows.get(index)[1] + ")", "The row is selected and Save Data is enabled",
                tickedRow ? "Row " + (index + 1) + " selected: the screen holds the patient \"" + pd.lastPatient + "\" and Save Data is enabled" : "Problem — " + pd.problems, tickedRow ? "PASS" : "FAIL");
        if (!tickedRow) { addSummary("Result", "FAILED — row: " + pd.problems); return; }

        boolean popup = pd.clickSaveData();
        step("Click Save Data", "Click Save Data", "The Extraction Details popup opens",
                popup ? "The Extraction Details popup is open (Motive of Extraction*, Consent given*, Document List with " + pd.documentNames().size() + " file(s))" : "Problem — " + pd.problems, popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED — Save Data: " + pd.problems); return; }

        // 3) the popup
        String motive = "Clinical audit of recent outpatient visit records, QA " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean motiveOk = pd.enterMotive(motive);
        step("Enter motive of extraction", "Enter the Motive of Extraction (" + motive + ")", "The motive is accepted",
                motiveOk ? "Motive \"" + motive + "\" entered (a suitable reason, stamped with the time so it can be found in the Patient Extraction Report)" : "Problem — " + pd.problems, motiveOk ? "PASS" : "FAIL");
        if (!motiveOk) { addSummary("Result", "FAILED — motive: " + pd.problems); return; }

        boolean consent = pd.tickConsent();
        step("Tick consent given", "Tick the Consent given checkbox", "Consent given is ticked",
                consent ? "Consent given is ticked; the screen's model holds true" : "Problem — " + pd.problems, consent ? "PASS" : "FAIL");
        if (!consent) { addSummary("Result", "FAILED — consent: " + pd.problems); return; }

        String firstToast = pd.clickOkAndGetToast();
        boolean firstClicked = pd.problems.isEmpty();
        boolean stillOpen = pd.popupOpen();
        step("Click OK (before any document)", "Click OK in the popup", "OK is clicked",
                firstClicked ? "OK clicked; the screen answered \"" + (firstToast.isEmpty() ? "(no message)" : firstToast) + "\" and the popup " + (stillOpen ? "stays open" : "closed")
                        + " - with Consent given ticked the screen refuses to save until a document is in the Document List" : "Problem — " + pd.problems, firstClicked ? "PASS" : "FAIL");
        if (!firstClicked) { addSummary("Result", "FAILED — OK: " + pd.problems); return; }
        if (!stillOpen) { addSummary("Result", "FAILED — the popup closed on the first OK (" + firstToast + "); the attachment steps cannot follow"); return; }

        Path file;
        try { file = attachFile(); } catch (IOException e) { step("Attach file", "Prepare a text file to attach", "A text file is ready", "Problem — " + e.getMessage(), "FAIL"); addSummary("Result", "FAILED — no text file could be prepared"); return; }
        boolean chosen = pd.chooseFile(file);
        step("Attach file - Choose File", "In the Document List, click Choose File and choose a text document (" + file.getFileName() + ")", "The file is chosen and its name shows",
                chosen ? "File \"" + pd.lastFileName + "\" chosen " + pd.lastFileVia + "; the name box shows \"" + pd.fileNameBox() + "\"; the file is " + fileNote : "Problem — " + pd.problems, chosen ? "PASS" : "FAIL");
        if (!chosen) { addSummary("Result", "FAILED — file: " + pd.problems); return; }

        boolean added = pd.clickAdd();
        step("Click Add", "Click Add in the popup", "The file joins the Document List",
                added ? "Add clicked; the Document List now holds " + pd.documentNames() + " (new: \"" + pd.lastAddedFile + "\")" : "Problem — " + pd.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add: " + pd.problems); return; }

        int tabsBefore = page.context().pages().size();
        String toast = pd.clickOkAndGetToast();
        boolean ok = pd.problems.isEmpty() && pd.saveSucceeded();
        boolean savedByServer = pd.savedByServer();
        step("Click OK", "Click OK in the popup", "The extraction is saved",
                pd.problems.isEmpty() ? "OK clicked" + (pd.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + pd.lastDialog + "\"") : "Problem — " + pd.problems, pd.problems.isEmpty() ? "PASS" : "FAIL");

        step("Verify success toast message", "Read the toast shown after OK", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (pd.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + pd.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the extraction (ResultStatus 1, \"" + pd.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + pd.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + pd.saveDiagnosis(),
                ok ? "PASS" : "FAIL");
        if (!ok && !savedByServer) { addSummary("Result", "FAILED — the extraction was not saved: " + (toast.isEmpty() ? pd.saveDiagnosis() : toast)); return; }

        // 4) the Patient Extraction Report: the screen opens it in another tab
        Page tab = pd.awaitReportTab(tabsBefore, 25000);
        boolean closed = pd.popupClosed();
        Page reportPage = tab != null ? tab : page;
        com.kpj.pages.Report_page.PatientExtractionReport rep = new com.kpj.pages.Report_page.PatientExtractionReport(reportPage);
        boolean onReport = false;
        if (tab != null) {
            try { tab.bringToFront(); } catch (Exception ignore) { }
            for (int i = 0; i < 80 && !(onReport = rep.onScreen()); i++) tab.waitForTimeout(500);
        } else {
            onReport = rep.navigateViaMenu();                                          // no tab came: open the screen from the Report menu, as the steps allow
        }
        step(reportPage, "Check the Patient Extraction Report screen", "Check whether the screen was redirected to Patient Extraction Report, or open that screen in another tab", "The Patient Extraction Report screen is shown",
                onReport ? (tab != null ? "PASSES: the screen opened Patient Extraction Report in ANOTHER TAB (" + tab.url() + "); the Extraction Details popup " + (closed ? "closed" : "is still open") + " and this tab stays on Patient Data Extraction"
                                        : "No new tab came, so the screen was opened from the Report menu (" + reportPage.url() + ")")
                         : "FAILS: " + (tab != null ? "a tab opened at " + tab.url() + " but the Patient Extraction Report screen did not render" : "no tab opened and the screen could not be opened from the menu"),
                onReport ? "PASS" : "FAIL");
        if (!onReport) { addSummary("Result", "FAILED — the Patient Extraction Report screen could not be shown"); return; }

        boolean repSearched = rep.clickSearch();
        step(reportPage, "Click search", "Click Search on the Patient Extraction Report (From and To are both today, which holds the new extraction)", "The list is searched",
                repSearched ? "Search clicked; the server answered with " + rep.lastSearchRows + " record(s) for " + rep.lastSearchRequest : "Problem — " + rep.problems, repSearched ? "PASS" : "FAIL");

        List<String[]> listed = rep.rows();
        String[] mine = null;
        for (String[] r : listed) if (r[2].equals(motive)) mine = r;
        step(reportPage, "Verify the data in the List Of Item Enquiry table", "Look for the new extraction (motive \"" + motive + "\") in the table", "The new extraction is listed",
                mine != null ? "PASSES because the table lists " + listed.size() + " row(s), among them the new one: " + mine[0] + " / " + mine[1] + " / " + mine[2] + " / consent " + mine[3]
                        : listed.isEmpty() ? "FAILS because the table is BLANK although the server answered " + rep.lastSearchRows + " record(s)"
                        : "FAILS because the table lists " + listed.size() + " row(s) but none has the motive \"" + motive + "\" (rows: " + listed.stream().limit(3).map(r -> r[2]).toList() + ")",
                mine != null ? "PASS" : "FAIL");

        addSummary("Visit date From / To", pd.lastFrom + " / " + pd.lastTo + " (From is 14 days before To)");
        addSummary("Patient", pd.lastPatient);
        addSummary("Motive / Consent", motive + " / given");
        addSummary("First OK (no document yet)", firstToast.isEmpty() ? "(no message)" : firstToast);
        addSummary("Attached file", pd.lastFileName + " - " + fileNote);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1" + (pd.lastSaveMessage.isEmpty() ? "" : ", \"" + pd.lastSaveMessage.trim() + "\"") + ")" : "(no save confirmed)");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Patient Extraction Report", tab != null ? "opened in another tab: " + tab.url() : "no new tab; opened from the menu");
        addSummary("Result", ok && mine != null ? toast + " and the new extraction is listed in the Patient Extraction Report" : "see the failed step(s)");
    }
}
