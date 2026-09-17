package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named EducationManagement — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Education Management</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Education Management</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Enter the <b>file date</b> and <b>file time</b>.</li>
 *   <li>Select the <b>document category</b>.</li>
 *   <li><b>Upload a file</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>The upload file is generated at run time</b> — a small valid PNG written to the temp directory —
 * so the flow is self-contained and does not depend on a file existing on the machine. Point it at your
 * own file with {@code -DuploadFile=C:\path\to\file.pdf} when you need to exercise a specific type.</p>
 *
 * <p>&#9888; A successful run UPLOADS a REAL document in the target environment.</p>
 *
 * <h2>BLOCKED — the server's FTP upload fails</h2>
 * <p>As of 2026-08-10 every client-side step passes and Submit is rejected by the server with
 * <b>"Error while uploading file on FTP Server!"</b>. The file is genuinely attached first — the app
 * echoes it into {@code EducationManagement.documentname}, sets {@code model.report}, and the form's Add
 * appends it to the attachment list ({@code rowsAdded=1}) — so the failure is in DevHIS storing the file,
 * not in selecting it. This is an environment/infrastructure issue for the DevHIS team.</p>
 *
 * <h2>Two things about uploads here</h2>
 * <ul>
 *   <li><b>The file input must be set directly.</b> Clicking the visible control opens the OS file
 *       chooser, which Playwright cannot drive; {@code setInputFiles} on the input works even though the
 *       control is styled.</li>
 *   <li><b>The input is cleared by the app as soon as the file is chosen.</b> Reading
 *       {@code input.files} after a wait therefore reports "no file attached" for an upload that
 *       succeeded — a false negative this flow hit before the check was moved to run immediately, with
 *       the app's own {@code documentname} / {@code model.report} as corroboration.</li>
 * </ul>
 */
public class EducationManagement extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public EducationManagement() { super("NursingStation_EducationManagement"); }

    public static void main(String[] args) {
        EducationManagement t = new EducationManagement();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /**
     * The file to upload: {@code -DuploadFile=} if given, otherwise a freshly generated 1x1 PNG.
     *
     * <p>Generated rather than committed so the flow never fails for a missing fixture, and a real image
     * rather than a stub .txt because document uploads commonly validate the type.</p>
     */
    private java.nio.file.Path uploadFile() throws Exception {
        String given = System.getProperty("uploadFile");
        if (given != null && !given.isBlank()) {
            java.nio.file.Path p = java.nio.file.Paths.get(given);
            if (java.nio.file.Files.exists(p)) return p;
            System.out.println("EducationManagement: -DuploadFile not found (" + given + ") — generating one");
        }
        java.nio.file.Path out = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "devhis-education-upload.png");
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    @Override
    protected void body() {
        meta("Nursing Station - Education Management", "Nursing Station > Education Management",
                "&#9888; Uploads a REAL document: New, enter the file date and time, select the document "
                        + "category, upload a file, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String fileDate = System.getProperty("fileDate",
                now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String fileTime = System.getProperty("fileTime",
                now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));

        java.nio.file.Path file;
        try { file = uploadFile(); }
        catch (Exception e) {
            step("Prepare upload file", "Generate a small PNG to upload",
                    "A file exists to upload", "FAILED to create the file: " + e.getMessage(), "FAIL");
            addSummary("Result", "FAILED — could not create the upload file");
            return;
        }

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.EducationManagement edm =
                new com.kpj.pages.NursingStation_page.EducationManagement(page);

        // 1) Navigate
        boolean rendered = edm.navigateViaMenu(BASE);
        step(page, "Open Education Management screen",
                "Click Nursing Station -> Education Management (retrying via the route and a full page load)",
                "The Education Management screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + edm.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = edm.clickNew();
        edm.describeControls();   // diagnostics: the real ng-models and any file inputs
        step(page, "Click New", "Click New to open the entry form",
                "The Education Management form opens",
                formOpen ? "New clicked (" + page.url() + ")" : "New did NOT open a form",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — form not reached"); return; }

        // 3) File date + time
        String dt = edm.enterFileDateAndTime(fileDate, fileTime);
        step(page, "Enter file date and file time",
                "Enter file date " + fileDate + " and file time " + fileTime,
                "Both are entered", dt, edm.dateTimeSet() ? "PASS" : "FAIL");

        // 4) Document category
        String cat = edm.selectDocumentCategory();
        boolean catOk = !cat.startsWith("(");
        step(page, "Select document category", "Select the document category",
                "A document category is selected",
                catOk ? "Category = " + cat : "Category NOT selected " + cat,
                catOk ? "PASS" : "FAIL");

        // 5) Upload
        String up = edm.uploadFile(file);
        step(page, "Upload file",
                "Attach " + file.getFileName() + " to the file input (set directly — the visible control "
                        + "opens the OS chooser, which cannot be driven)",
                "The file is attached",
                edm.fileUploaded() ? "Uploaded " + up : "Upload FAILED " + up,
                edm.fileUploaded() ? "PASS" : "FAIL");

        // 5b) The form's own Add moves the chosen file into the attachment list that Submit saves.
        String addedFile = edm.clickAddFile();
        boolean addOk = addedFile != null && addedFile.startsWith("rowsAdded=") && !addedFile.startsWith("rowsAdded=0");
        step(page, "Add the file to the list",
                "Click the form's Add (AddFileDetails) so the file joins the list that Submit saves",
                "The file is added to the attachment list", addedFile, addOk ? "PASS" : "FAIL");

        // 6) Submit -> toast
        String toast = edm.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added")
                || tl.contains("updated") || tl.contains("uploaded");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + edm.lastSaveDiagnostics
                : (ok ? toast : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("File date / time", fileDate + " " + fileTime);
        addSummary("Document category", edm.lastCategory);
        addSummary("Uploaded file", file.toString() + "  ->  " + edm.lastUpload);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
