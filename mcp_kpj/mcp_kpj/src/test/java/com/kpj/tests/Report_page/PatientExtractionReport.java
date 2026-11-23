package com.kpj.tests.Report_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDate;
import java.util.List;

// The page object is also named PatientExtractionReport — referenced by its fully-qualified name.

/**
 * Report &gt; <b>Patient Extraction Report</b> ({@code #/PatientExtractionReport}).
 *
 * <ol>
 *   <li>Enter <b>From</b> and <b>To</b>: To is the date the screen opens with (today), From is 1 month and 15 days before it (inside "1 to 2 months before"); click <b>Search</b>.</li>
 *   <li>Verify the <b>List Of Item Enquiry</b> table is populated (and every row lies within From..To), then select one row by clicking on it.</li>
 *   <li><b>Attachment</b> (a column of the grid): click the row's attachment symbol, click <b>Choose File</b> and attach a file, click <b>Add</b>, click <b>OK</b> and verify the success toast.</li>
 *   <li><b>Print</b> (a column of the grid): click the row's print symbol and verify the PDF report opens in another tab and carries content.</li>
 * </ol>
 *
 * <p>"Attachment" and "Print" are COLUMNS of the grid on this screen, not tabs. This test SAVES A REAL ATTACHMENT against the extraction record of the clicked row; it stays.</p>
 */
public class PatientExtractionReport extends DevHisBase {

    public PatientExtractionReport() { super("Report_PatientExtractionReport"); }

    public static void main(String[] args) {
        PatientExtractionReport t = new PatientExtractionReport();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Report - Patient Extraction Report", "Report > Patient Extraction Report",
                "Enter From (1.5 months before) and To dates; Search; verify the List Of Item Enquiry table; click a row; Attachment symbol -> Choose File -> Add -> OK -> success toast; Print symbol -> PDF in another tab. SAVES A REAL ATTACHMENT.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Report_page.PatientExtractionReport pe = new com.kpj.pages.Report_page.PatientExtractionReport(page);

        boolean on = pe.navigateViaMenu();
        step("Open Patient Extraction Report", "Click Report -> Patient Extraction Report", "The Patient Extraction Report screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Patient Extraction Report not reached"); return; }

        // 1) From and To: To = the date the screen opens with (today); From = 1 month and 15 days before it
        LocalDate to = pe.defaultToDate();
        LocalDate from = to.minusMonths(1).minusDays(15);
        boolean dated = pe.enterDates(from, to);
        step("Enter From and To dates", "Enter From " + from.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (1 month 15 days before To) and To " + to.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "From is 1 to 2 months before To", dated ? "From \"" + pe.lastFrom + "\" and To \"" + pe.lastTo + "\" entered through the calendars (From is 1 month and 15 days before To, inside the 1 to 2 months asked); the boxes and the screen's model hold them"
                        : "Problem — " + pe.problems, dated ? "PASS" : "FAIL");
        if (!dated) { addSummary("Result", "FAILED — dates: " + pe.problems); return; }

        boolean searched = pe.clickSearch();
        step("Click Search", "Click Search", "The list is searched for the dates",
                searched ? "Search clicked; the server answered with " + pe.lastSearchRows + " record(s) for " + pe.lastSearchRequest : "Problem — " + pe.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — search: " + pe.problems); return; }

        // 2) the list
        List<String[]> rows = pe.rows();
        List<String> outside = pe.outsideRange(from, to);
        boolean populated = !rows.isEmpty() && outside.isEmpty();
        step("Verify the List Of Item Enquiry table", "Check that the table is populated with data", "The table lists the extractions of the dates",
                populated ? "PASSES because the table lists " + rows.size() + " row(s) (pager \"" + pe.pagerText() + "\"; columns " + pe.headings() + "), all dated within " + pe.lastFrom + " - " + pe.lastTo + ": "
                        + rows.stream().limit(3).map(r -> r[0] + " / " + r[1] + " / " + r[2]).toList() + (rows.size() > 3 ? " ..." : "")
                    : rows.isEmpty() ? "FAILS because the table is BLANK although the server answered " + pe.lastSearchRows + " record(s)"
                    : "FAILS because " + outside.size() + " row(s) lie outside " + pe.lastFrom + " - " + pe.lastTo + ": " + outside,
                populated ? "PASS" : "FAIL");
        if (rows.isEmpty()) { addSummary("Result", "FAILED — the table is blank"); return; }

        // 3) click a row
        boolean rowClicked = pe.clickRow(0);
        step("Select a row by clicking on it", "Click one of the rows of the table", "The row is selected",
                rowClicked ? "Clicked row 1: \"" + pe.lastRow + "\"; the screen's own state for it: " + pe.lastSelection + " (this grid shows no highlight for a clicked row; every row carries a tick in its Sel column)"
                        : "Problem — " + pe.problems, rowClicked ? "PASS" : "FAIL");
        if (!rowClicked) { addSummary("Result", "FAILED — row: " + pe.problems); return; }
        addSummary("Row used", pe.lastRow);

        // 4) Attachment: the grid column's symbol -> popup -> Choose File -> Add -> OK -> toast
        String toast = "";
        boolean saved = false, savedByServer = false;
        String persisted = "(not checked)";
        attachment:
        {
            boolean opened = pe.clickAttachment(0);
            List<String> docsBefore = opened ? pe.documentNames() : List.of();
            step("Click the attachment symbol", "Click the Attachment symbol of the selected row (there is no separate Attachment tab; it is a column of the table)", "The Extraction Details popup opens",
                    opened ? "The Extraction Details popup is open; its Document List holds " + docsBefore.size() + " file(s): " + docsBefore : "Problem — " + pe.problems, opened ? "PASS" : "FAIL");
            if (!opened) { addSummary("Attachment", "the popup did not open: " + pe.problems); break attachment; }

            boolean chosen = pe.chooseFile(ATTACH);
            step("Click Choose File and attach a file", "Click Choose File and pick " + ATTACH.getFileName(), "The file is chosen and its name shows",
                    chosen ? "File \"" + pe.lastFileName + "\" chosen " + pe.lastSelection + "; the name box shows \"" + pe.fileNameBox() + "\"" : "Problem — " + pe.problems, chosen ? "PASS" : "FAIL");
            if (!chosen) { addSummary("Attachment", "no file could be chosen: " + pe.problems); break attachment; }

            boolean added = pe.clickAdd();
            step("Click Add", "Click Add in the popup", "The file joins the Document List",
                    added ? "Add clicked; the Document List now holds " + pe.documentNames() + " (new: \"" + pe.lastAddedFile + "\")" : "Problem — " + pe.problems, added ? "PASS" : "FAIL");
            if (!added) { addSummary("Attachment", "Add failed: " + pe.problems); break attachment; }

            toast = pe.clickOkAndGetToast();
            saved = pe.problems.isEmpty() && pe.saveSucceeded();
            savedByServer = pe.savedByServer();
            step("Click OK", "Click OK in the popup", "The attachment is saved",
                    pe.problems.isEmpty() ? "OK clicked" + (pe.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + pe.lastDialog + "\"") : "Problem — " + pe.problems,
                    pe.problems.isEmpty() ? "PASS" : "FAIL");
            step("Verify success toast message", "Read the toast shown after OK", "A success toast appears",
                    saved ? "PASSES because the toast is a success message: \"" + toast + "\"" + (pe.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + pe.lastSaveMessage.trim() + "\"")
                          : toast.isEmpty() ? "FAILS because no toast appeared — " + pe.saveDiagnosis()
                                            : "FAILS because the toast is not a success message: \"" + toast + "\" — " + pe.saveDiagnosis(),
                    saved ? "PASS" : "FAIL");

            // not a step: read the saved file back by opening the popup again
            if (saved || savedByServer) {
                try {
                    pe.closePopup();
                    if (pe.clickAttachment(0)) {
                        List<String> docs = pe.documentNames();
                        persisted = docs.contains(pe.lastFileName) ? "yes - the Document List of that row now holds \"" + pe.lastFileName + "\" (" + docs + ")" : "NOT FOUND in the reopened Document List " + docs;
                    } else persisted = "(the popup could not be reopened: " + pe.problems + ")";
                } catch (Exception e) { persisted = "(not checked: " + e.getMessage().split("\n")[0] + ")"; }
            }
        }
        if (!pe.closePopup()) addSummary("Popup", "the Extraction Details popup stayed open and may cover the Print symbol");

        // 5) Print: the grid column's symbol -> a PDF in another tab
        int tabsBefore = pe.clickPrint(0);
        boolean printClicked = tabsBefore >= 0;
        step("Click the print symbol", "Click the Print symbol of the selected row (there is no separate Print tab; it is a column of the table)", "The print action fires",
                printClicked ? "Print symbol clicked (" + tabsBefore + " tab(s) were open)" : "Problem — " + pe.problems, printClicked ? "PASS" : "FAIL");
        PdfReport.Result pdf = null;
        if (printClicked) {
            pdf = PdfReport.capture(page, tabsBefore, 20000);
            if (pdf.png != null && pdf.png.length > 0) {
                step(pdf.png, "Verify the print PDF report in another tab", "Wait for the report to open in another tab", "A non-blank PDF report is generated",
                        pdf.diagnostics + (pdf.blank ? "" : " The screenshot is the PDF's first page rendered from the file itself."), pdf.blank ? "FAIL" : "PASS");
            } else {
                step(page, "Verify the print PDF report in another tab", "Wait for the report to open in another tab", "A non-blank PDF report is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");
            }
        }

        addSummary("Dates", pe.lastFrom + " - " + pe.lastTo + " (From is 1 month 15 days before To)");
        addSummary("Rows listed", rows.size() + " (pager \"" + pe.pagerText() + "\")");
        addSummary("Attached file", pe.lastFileName.isEmpty() ? "(none)" : pe.lastFileName);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + pe.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("Attachment kept afterwards", persisted);
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Print report", pdf == null ? "(print not clicked)" : pdf.diagnostics);
        addSummary("Result", saved && pdf != null && !pdf.blank ? "PASS - attachment saved (\"" + toast + "\") and the PDF report was generated"
                : "see the failed step(s): toast \"" + (toast.isEmpty() ? "(none)" : toast) + "\"; print " + (pdf == null ? "not clicked" : pdf.blank ? "blank/not generated" : "generated"));
    }
}
