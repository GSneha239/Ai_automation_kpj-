package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DeathMarkingMortuary — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Death Marking / Mortuary</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Death Marking/Mortuary</b>; click <b>Add</b>.</li>
 *   <li>Enter MRN + search; fill the Death Marking + Mortuary details (Time, Mortuary Cabin, Handover, Template).</li>
 *   <li>Click <b>Save</b> ({@code fnSaveDeathMarking}); wait for the success toast.</li>
 * </ol>
 */
public class DeathMarkingMortuary extends DevHisBase {

    public DeathMarkingMortuary() { super("NursingStation_DeathMarkingMortuary"); }

    public static void main(String[] args) {
        DeathMarkingMortuary t = new DeathMarkingMortuary();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Death Marking/Mortuary", "Nursing Station > Death Marking/Mortuary",
                "Add a Death Marking/Mortuary record: enter MRN + search, fill Death Marking + Mortuary details, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.DeathMarkingMortuary dm =
                new com.kpj.pages.NursingStation_page.DeathMarkingMortuary(page);

        boolean onScreen = dm.navigateTo(BASE);
        step(page, "Open Death Marking/Mortuary screen", "Nursing Station -> Death Marking/Mortuary",
                "The Death Marking list is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = dm.clickTopAdd();
        step(page, "Click Add", "Click Add (AddDeathMarkingMaster) -> #/add-DeathMarking",
                "The add form is shown", added ? "Add form opened (" + page.url() + ")" : "Add form did not open",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add form not opened"); return; }

        String[] candidates = { "100000273", "100000025", "100000034", "100000990", "100001062" };
        String mrn = "";
        for (String c : candidates) { if (dm.enterMrnAndSearch(c)) { mrn = c; break; } }
        step(page, "Enter MRN & Search", "Type the MRN and click the search magnifier (span.glyphicon-search)",
                "The patient is loaded by MRN",
                mrn.isEmpty() ? "No MRN loaded a patient" : "Loaded patient MRN " + mrn,
                mrn.isEmpty() ? "FAIL" : "PASS");
        if (mrn.isEmpty()) { addSummary("Result", "No patient loaded by MRN"); return; }

        String fill = dm.fillDetails();
        boolean fillOk = !fill.contains("Relationship=(no") && !fill.contains("Template=(no");
        step(page, "Fill Death Marking + Mortuary details",
                "Time, Mortuary Cabin No., Handover To (Relationship) + Handover To, Template",
                "The mandatory details are filled", fill, fillOk ? "PASS" : "FAIL");

        String toast = dm.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (fnSaveDeathMarking); wait for the success toast",
                "A success toast appears", actual, ok ? "PASS" : "FAIL");

        addSummary("Patient MRN", dm.lastMrn);
        addSummary("Relationship / Template", dm.lastRelation + " / " + dm.lastTemplate);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
