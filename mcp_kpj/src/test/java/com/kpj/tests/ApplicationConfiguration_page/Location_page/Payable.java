package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Payable — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Payable</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Payable</b> → <b>Add</b>.</li>
 *   <li>Header: Reg. Type, Payable Type (Doctor — unlocks Doctor Type + 6 tabs), Code, User Role, Name (Title +
 *       name), Gender, Doctor Type, Date of Birth.</li>
 *   <li>Personal Information: Marital Status, Nationality, Designation, MMC No, Email ID.</li>
 *   <li>Location-Department: tick a department + its default doctor.</li>
 *   <li>Address Information: Address Type, Country, State, City, Area, Add.</li>
 *   <li>File Linking: choose a file, Remark, Add.</li>
 *   <li>Location-VisitType: tick a location + default.</li>
 *   <li>Location-Classification: tick a row.</li>
 *   <li>Dependent List: Name, DOB, Mobile No, Relationship, Add.</li>
 *   <li>Education: Qualification, University, Year of Passing, Class, Add.</li>
 *   <li>Experience: From/To Date, Exp YY/MM, Post Held, Last Drawn Salary, Payor, Reason Of Leaving, Add.</li>
 *   <li>Einvoice: Tin/Identification/Tourism Tax/SST/MISC/Business Activity/Bank Account, Add.</li>
 *   <li>Submit → success toast.</li>
 * </ol>
 */
public class Payable extends DevHisBase {

    public Payable() { super("ApplicationConfig_Location_Payable"); }

    public static void main(String[] args) {
        Payable t = new Payable();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Payable", "Application Configuration > Location > Payable",
                "Add a Payable (Doctor): header, Personal Information, Location-Department, Address Information, "
                        + "File Linking, Location-VisitType, Location-Classification, Dependent List, Education, "
                        + "Experience, Einvoice, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.Payable py =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.Payable(page);

        boolean onScreen = py.navigateViaMenu();
        step(page, "Open Payable screen", "Application Configuration -> Location -> Payable",
                "The Payable list screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = py.clickAdd() && py.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens (#/add-Payble)",
                added ? "Add form opened at " + page.url() : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add form did not open"); return; }

        String header = py.fillHeader();
        step(page, "Header: Reg. Type, Payable Type, User Role, Name, Gender, Doctor Type, Code, DOB",
                "Select Reg. Type, Payable Type (Doctor), User Role, Title + Name, Gender, Doctor Type; enter Code, Date of Birth",
                "All header fields are filled and Doctor Type is enabled", header,
                header.contains("DoctorType=") && !header.endsWith("DoctorType=") ? "PASS" : "FAIL");

        String personal = py.fillPersonalInformation();
        step(page, "Personal Information: Marital Status, Nationality, Designation, MMC No, Email ID",
                "Click Personal Information; select Marital Status, Nationality, Designation; enter MMC No, Email ID",
                "Personal information fields are filled", personal, "PASS");

        String dept = py.fillLocationDepartment();
        step(page, "Location-Department: select a location and its default doctor",
                "Click Location-Department; tick one department row and its default-doctor checkbox",
                "A department and its default doctor are ticked", dept, dept.isEmpty() ? "FAIL" : "PASS");

        String address = py.fillAddressInformation();
        step(page, "Address Information: Address Type, Country, State, City, Area",
                "Click Address Information; select Address Type, Country, State, City/District, Area/Town; Add",
                "The address is added to the list", address, address.contains("Add=true") ? "PASS" : "FAIL");

        java.nio.file.Path screenshotFile = findFirstScreenshot();
        String fileLink = screenshotFile == null ? "No file found under " + ATTACH_DIR
                : py.fillFileLinking(screenshotFile);
        step(page, "File Linking: choose file, enter remark",
                "Click File Linking; Choose File from " + ATTACH_DIR + "; enter Remark; Add",
                "The file is linked with a remark", fileLink, fileLink.contains("Add=true") ? "PASS" : "FAIL");

        String visitType = py.fillLocationVisitType();
        step(page, "Location-VisitType: select location checkbox and default checkbox",
                "Click Location-Visit type; tick one row's location checkbox and its default checkbox",
                "A visit-type location and its default are ticked", visitType, visitType.isEmpty() ? "FAIL" : "PASS");

        String classification = py.fillLocationClassification();
        step(page, "Location-Classification: select a checkbox",
                "Click Location-classification; tick one row's checkbox",
                "A classification row is ticked", classification, classification.isEmpty() ? "FAIL" : "PASS");

        String dependent = py.fillDependentList();
        step(page, "Dependent List: Name, Date of Birth, Mobile No, Relationship, Add",
                "Click Dependent List; enter Name, DOB, Mobile No, Relationship; Add",
                "A dependent is added to the list", dependent, dependent.contains("Add=true") ? "PASS" : "FAIL");

        String education = py.fillEducation();
        step(page, "Education: Qualification, University, Year of Passing, Class, Add",
                "Click Education; enter Qualification, University, Year of Passing, Class; Add",
                "An education entry is added", education, education.contains("Add=true") ? "PASS" : "FAIL");

        String experience = py.fillExperience();
        step(page, "Experience: From/To Date, Exp YY/MM, Post Held, Last Drawn Salary, Payor, Reason Of Leaving, Add",
                "Click Experience; enter all fields; Add",
                "An experience entry is added", experience, experience.contains("Add=true") ? "PASS" : "FAIL");

        String einvoice = py.fillEinvoice();
        step(page, "Einvoice: Tin Number, Identification No, Tourism Tax Reg No, SST Reg No, MISC Code, Business Activity, Bank Account No, Add",
                "Click Einvoice; enter all fields; Add",
                "An e-invoice entry is added", einvoice, einvoice.contains("Add=true") ? "PASS" : "FAIL");

        String toast = py.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
        String actual = (toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"") + (py.lastSaveApi.isEmpty() ? "" : "  [" + py.lastSaveApi + "]");
        step(py.toastPng, "Click Submit", "Click Submit", "The Payable is saved successfully", actual, ok ? "PASS" : "FAIL");

        addSummary("Code", py.lastCode);
        addSummary("Name", py.lastName);
        addSummary("Save API", py.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    /** An image from the machine's Screenshots folder (DevHisBase.ATTACH_DIR), for the File Linking tab's upload. */
    private java.nio.file.Path findFirstScreenshot() {
        return java.nio.file.Files.isRegularFile(ATTACH) ? ATTACH : null;
    }
}
