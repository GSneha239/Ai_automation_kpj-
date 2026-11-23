package com.kpj.tests.AncillaryServices_page.HQM_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named IncidentReporting - referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; HQM &gt; <b>Incident Reporting</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: HQM &gt; Incident Reporting; on the list click <b>Add</b>.</li>
 *   <li>Name Of The Victim = Norasmi; Employee Department = Emergency; Remark = Test_SL; Event Details = Incident; Place of Occurence = ward.</li>
 *   <li>Incident Time: open the picker and raise the hour once; tick a department under Other Responsible Department.</li>
 *   <li>Template Details tab &gt; <b>Add</b>; <b>Save</b>; verify the toast "Incident Saved Successfully." and the new row in the list.</li>
 * </ol>
 *
 * <p>&#9888; SAVES A REAL INCIDENT REPORT on every run (it appears in the list with status Open; the list has an Edit / History but no delete).</p>
 */
public class IncidentReporting extends DevHisBase {

    public IncidentReporting() { super("AncillaryServices_HQM_IncidentReporting"); }

    public static void main(String[] args) {
        IncidentReporting t = new IncidentReporting();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - HQM - Incident Reporting", "Ancillary Services > HQM > Incident Reporting",
                "&#9888; Saves a REAL incident report: Add; victim name, department, remark, event details, place, incident time, responsible department, template; Save; verify the toast and the new list row.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.HQM_page.IncidentReporting ir = new com.kpj.pages.AncillaryServices_page.HQM_page.IncidentReporting(page);

        boolean on = ir.navigateViaMenu();
        step(page, "Open Incident Reporting", "Click HQM -> Incident Reporting", "The Incident Reporting list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the list (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Incident Reporting not reached"); return; }

        List<String[]> before = ir.listRows();
        int maxBefore = 0;
        for (String[] r : before) { try { maxBefore = Math.max(maxBefore, Integer.parseInt(r[0].trim())); } catch (Exception ignore) { } }

        boolean form = ir.clickAdd();
        step(page, "Click Add", "Click Add (fnSaveIncident())", "The incident form (#/add-IncidentReporting) opens",
                form ? "Form opened (" + page.url().replaceAll(".*#", "#") + "); the list held " + before.size() + " incident(s)" : "The form did NOT open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED - form not opened"); return; }

        String victim = "Norasmi";
        ir.problems.clear();
        String got = ir.fillBox("IncidentDetails.victimname", victim);
        step(page, "Enter Name Of The Victim", "Type \"" + victim + "\" in Name Of The Victim*", "The name is accepted", got.isEmpty() ? "Problem - " + ir.problems : "Name Of The Victim = " + got, got.equals(victim) ? "PASS" : "FAIL");

        ir.problems.clear();
        got = ir.fillBox("IncidentDetails.Department", "Emergency");
        step(page, "Enter Employee Department", "Type \"Emergency\" in Employee Department", "The department is accepted", got.isEmpty() ? "Problem - " + ir.problems : "Employee Department = " + got, got.equals("Emergency") ? "PASS" : "FAIL");

        ir.problems.clear();
        got = ir.fillBox("IncidentDetails.remark", "Test_SL");
        step(page, "Enter Remark", "Type \"Test_SL\" in Remark", "The remark is accepted", got.isEmpty() ? "Problem - " + ir.problems : "Remark = " + got, got.equals("Test_SL") ? "PASS" : "FAIL");

        ir.problems.clear();
        got = ir.fillBox("IncidentDetails.eventDetail", "Incident");
        step(page, "Enter Event Details", "Type \"Incident\" in Event Details*", "The event details are accepted", got.isEmpty() ? "Problem - " + ir.problems : "Event Details = " + got, got.equals("Incident") ? "PASS" : "FAIL");

        ir.problems.clear();
        got = ir.fillBox("IncidentDetails.Placeofoccurance", "ward");
        step(page, "Enter Place of Occurence", "Type \"ward\" in Place of Occurence", "The place is accepted", got.isEmpty() ? "Problem - " + ir.problems : "Place of Occurence = " + got, got.equals("ward") ? "PASS" : "FAIL");

        ir.problems.clear();
        String time = ir.bumpIncidentHour();
        boolean timeOk = ir.problems.isEmpty() && !ir.incidentTime().isEmpty();
        step(page, "Set Incident Time", "Click Incident Time; click the hours up arrow (incrementHours()) once", "A time is set in Incident Time",
                timeOk ? "Incident Time " + time : "Problem - " + ir.problems + " (" + time + ")", timeOk ? "PASS" : "FAIL");

        ir.problems.clear();
        String dept = ir.tickFirstResponsibleDepartment();
        step(page, "Tick Other Responsible Department", "Tick the first department under Other Responsible Department (funSetChkBxCCModels())", "The department is ticked",
                dept.isEmpty() ? "Problem - " + ir.problems : "Ticked: " + dept, dept.isEmpty() ? "FAIL" : "PASS");

        // The recording could not see select2 picks (the list closes on mouse-up), but the incident the person saved shows Incident Status "Open" and Template "Nursing Infection Control" - the same picks are made here.
        String stMsg = ir.pickSelect2("IncidentDetails.incidentstatusid", "Open");
        step(page, "Select Incident Status", "Pick \"Open\" in the Incident Status list (the saved incident of the recorded run shows status Open)", "Incident Status = Open",
                stMsg.isEmpty() ? "Incident Status = Open" : "Problem - " + stMsg, stMsg.isEmpty() ? "PASS" : "FAIL");

        ir.problems.clear();
        boolean tab = ir.openTemplateTab();
        step(page, "Open Template Details", "Click the Template Details tab", "The Template tab with its Add button is shown",
                tab ? "Template Details tab opened; Template select shows \"" + ir.templateSelected() + "\"" : "Problem - " + ir.problems, tab ? "PASS" : "FAIL");

        String tplMsg = tab ? ir.pickSelect2("IncidentDetails.templateid", "Nursing Infection Control") : "the Template tab is not open";
        step(page, "Select Template", "Pick \"Nursing Infection Control\" in the Template list (the only template; the saved incident of the recorded run shows it)", "Template = Nursing Infection Control",
                tplMsg.isEmpty() ? "Template = Nursing Infection Control" : "Problem - " + tplMsg, tplMsg.isEmpty() ? "PASS" : "FAIL");

        String added = tab ? ir.clickAddTemplate() : "";
        boolean addOk = !added.isEmpty() && !added.toLowerCase().contains("please select");
        step(page, "Click Add (Template)", "Click Add (AddTemplate();)", "The template is added to the report (no 'Please Select Template!' warning)",
                added.isEmpty() ? "Problem - " + ir.problems : "Template after Add: " + added, addOk ? "PASS" : "FAIL");

        String toast = ir.clickSave();
        boolean saved = toast.toLowerCase().contains("saved successfully") || toast.toLowerCase().contains("incident saved");
        step(page, "Click Save", "Click Save (fnSaveIncidentReport())", "'Incident Saved Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (ir.problems.isEmpty() ? "" : " - " + ir.problems) : toast, saved ? "PASS" : "FAIL");
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        if (!saved) { addSummary("Result", "FAILED - not saved"); return; }

        boolean back = ir.awaitList(20000);
        List<String[]> after = ir.listRows();
        String[] mine = null;
        for (String[] r : after) {
            int id = -1; try { id = Integer.parseInt(r[0].trim()); } catch (Exception ignore) { }
            if (id > maxBefore && r[1].equalsIgnoreCase(victim)) { if (mine == null || id > Integer.parseInt(mine[0].trim())) mine = r; }
        }
        step(page, "Verify the new incident in the list", "After Save the app returns to the list; look for the new incident (victim " + victim + ")",
                "The new incident is listed (status Open)",
                !back ? "The list did not come back (" + page.url() + ")"
                      : mine == null ? "No new incident for " + victim + " (the list held " + before.size() + ", now " + after.size() + ")"
                      : "Listed: id " + mine[0] + ", victim " + mine[1] + ", reporting " + mine[2] + ", date " + mine[3] + ", template " + mine[4] + ", status " + (mine[5].isEmpty() ? "(blank)" : mine[5])
                        + " (the list held " + before.size() + ", now " + after.size() + ")",
                mine != null && mine[4].contains("Nursing Infection Control") && mine[5].equalsIgnoreCase("Open") ? "PASS" : "FAIL");
        addSummary("Incident list", before.size() + " -> " + after.size());
        addSummary("New incident", mine == null ? "(not found)" : "id " + mine[0] + ", " + mine[1] + ", " + mine[4] + ", " + mine[5]);
        addSummary("Result", mine != null ? "Saved and listed" : "Saved but not found in the list");
    }
}
