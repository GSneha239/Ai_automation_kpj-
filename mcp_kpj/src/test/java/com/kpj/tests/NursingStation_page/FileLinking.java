package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FileLinking — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>File Linking</b> (M7UC1 — M7 Scanning FSD).
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>File Linking</b>.</li>
 *   <li>Pick a patient; fill the mandatory details (File Received Date/Time, File Name, File Category).</li>
 *   <li>Attach a file; click <b>Add</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class FileLinking extends DevHisBase {

    /**
     * The file to attach: {@code -DuploadFile=} if given, otherwise a freshly generated PNG.
     *
     * <p>This used to be a hard-coded path into one developer's Pictures folder
     * ({@code C:\Users\Siva Sankar\OneDrive\...}), so the attach step failed on every other machine —
     * reported as "(attach failed)", which reads like a broken screen rather than a missing fixture.
     * Generated instead so the flow never depends on a file that happens to be on one PC, and a real
     * image rather than a stub .txt because document uploads commonly validate the type.</p>
     */
    private java.nio.file.Path attachFile() throws Exception {
        String given = System.getProperty("uploadFile");
        if (given != null && !given.isBlank()) {
            java.nio.file.Path p = java.nio.file.Paths.get(given);
            if (java.nio.file.Files.exists(p)) return p;
            System.out.println("FileLinking: -DuploadFile not found (" + given + ") — generating one");
        }
        java.nio.file.Path out = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "devhis-filelinking-upload.png");
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.GREEN);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    public FileLinking() { super("NursingStation_FileLinking"); }

    public static void main(String[] args) {
        FileLinking t = new FileLinking();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - File Linking", "Nursing Station > File Linking",
                "Open File Linking, pick a patient, fill the mandatory details, attach a file, Add, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.FileLinking fl =
                new com.kpj.pages.NursingStation_page.FileLinking(page);

        boolean onScreen = fl.navigateTo(BASE);
        step(page, "Open File Linking screen", "Nursing Station -> File Linking",
                "The File Linking screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter MRN directly (SearchPatientByMRNo) — try known-good candidates until one loads.
        String[] candidates = { "100000273", "100000025", "100000034", "100000990", "100001062" };
        String mrn = "";
        for (String c : candidates) { if (fl.enterMrn(c)) { mrn = c; break; } }
        step(page, "Enter MRN", "Type the MRN and click search (SearchPatientByMRNo)",
                "The patient is loaded by MRN",
                mrn.isEmpty() ? "No MRN loaded a patient" : "Loaded patient MRN " + mrn,
                mrn.isEmpty() ? "FAIL" : "PASS");
        if (mrn.isEmpty()) { addSummary("Result", "No patient loaded by MRN"); return; }

        String fill = fl.fillMandatory();
        step(page, "Fill mandatory details", "File Received Date/Time, File Name, File Category",
                "The mandatory File Linking fields are filled", fill, fill.contains("Category=(n/a") ? "FAIL" : "PASS");

        java.nio.file.Path toAttach;
        try { toAttach = attachFile(); }
        catch (Exception e) {
            addSummary("Result", "FAILED — could not prepare a file to attach: " + e.getMessage());
            step(page, "Attach file", "Attach a generated PNG",
                    "The file is attached (fileChanged fires)",
                    "Could not create the upload file: " + e.getMessage(), "FAIL");
            return;
        }
        String attach = fl.attachFile(toAttach);
        boolean attached = attach.startsWith("Attached:") && !attach.equals("Attached: ");
        step(page, "Attach file", "Attach " + toAttach,
                "The file is attached (fileChanged fires)", attach, attached ? "PASS" : "FAIL");

        String added = fl.clickAdd();
        boolean addOk = fl.listRows() > 0;
        step(page, "Click Add", "Click Add (AddFileDetails) to add the file entry to the list",
                "The file entry is added to the list", added, addOk ? "PASS" : "FAIL");

        String toast = fl.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("succes") || tl.contains("linked")
                || tl.contains("uploaded") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit (fnSaveFilesonServer); wait for the success toast",
                "A success toast appears", actual, ok ? "PASS" : "FAIL");

        addSummary("Patient", fl.lastPatient);
        addSummary("File Name", fl.lastFileName);
        addSummary("File Category", fl.lastCategory);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
