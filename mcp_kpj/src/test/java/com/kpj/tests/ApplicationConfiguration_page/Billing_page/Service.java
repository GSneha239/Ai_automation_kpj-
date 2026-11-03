package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named Service — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Service</b> (the Service Master form, {@code #/add-serviceItem/serviceMaster}).
 *
 * <ol>
 *   <li>Click <b>Add</b>; enter a <b>Service Name</b> the server does not hold yet (a suitable one, chosen from a pool and checked against the server's own name search).</li>
 *   <li>Select a <b>Group</b>, a <b>Sub Group</b> and a <b>Department</b>.</li>
 *   <li><b>Pricing Policy Details</b>: tick a pricing policy. <b>Code Type</b>: select a code type, enter a code, click Add and verify the Code Type table shows the row.</li>
 *   <li><b>Add Set Details</b>: type "Panadeine" (or "Novofine Needles") in Item Name, select the option, and verify it appears in <b>List of Set Information</b>
 *       (picking the option only fills Item Name; the set's own Add puts it in the list, so the test clicks it and says so).</li>
 *   <li>Click <b>Submit</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES A REAL SERVICE into the master on every run; it stays (a service can only be made inactive, which this test does not do).</p>
 */
public class Service extends DevHisBase {

    /** Suitable service names (each is a billable service that uses medication / injection items), tried in order until one is not on the server yet. */
    private static final String[] NAMES = {
            "Pain Management Service Set", "Insulin Injection Administration Service", "Minor Procedure Pain Relief Pack", "Diabetic Care Injection Service Set",
            "Wound Care Analgesia Service Set", "Post Operative Pain Relief Service", "Home Nursing Injection Service Set"
    };

    public Service() { super("ApplicationConfig_Billing_Service"); }

    public static void main(String[] args) {
        Service t = new Service();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Service", "Application Configuration > Billing > Service",
                "Add a service: name, group, sub group, department, a pricing policy, a code type + code (Add, verify the Code Type table), a set item by smart search (verify List of Set Information); Submit; verify the success toast. SAVES A REAL SERVICE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.Service sv = new com.kpj.pages.ApplicationConfiguration_page.Billing_page.Service(page);

        boolean on = sv.navigateViaMenu();
        step("Open Service", "Click Application Configuration -> Billing -> Service", "The Service list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Service not reached"); return; }

        boolean added = sv.clickAdd();
        step("Click Add", "Click Add", "The Service form opens", added ? "The form is open (" + page.url() + ") with " + sv.options("serviceMst.GroupId").size() + " group(s) and " + sv.policyNames().size() + " pricing policies listed"
                : "Problem — " + sv.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — Add: " + sv.problems); return; }

        // Service Name: the first suitable name the server does not hold yet
        String name = "", checked = "";
        for (int i = 0; i < NAMES.length + 3; i++) {
            String candidate = i < NAMES.length ? NAMES[i] : NAMES[0] + " " + (i - NAMES.length + 2);
            Boolean taken = sv.nameTaken(candidate);
            checked += (checked.isEmpty() ? "" : "; ") + candidate + (taken == null ? " (could not be checked)" : taken ? " (already exists)" : " (free)");
            if (!Boolean.TRUE.equals(taken)) { name = candidate; break; }
        }
        boolean named = !name.isEmpty() && sv.enterServiceName(name);
        step("Enter Service Name", "Enter a Service Name different from the services already in the table (" + name + ")", "The Service Name is accepted",
                named ? "Service Name \"" + name + "\" entered; the server's own name search was asked first: " + checked : "Problem — " + (name.isEmpty() ? "every candidate name exists (" + checked + ")" : sv.problems), named ? "PASS" : "FAIL");
        if (!named) { addSummary("Result", "FAILED — Service Name"); return; }

        StringBuilder tried = new StringBuilder();
        boolean grouped = sv.selectGroupAndSubGroup(List.of("Nursing", "Medical Consumable", "Doctor Procedure"), 12, tried);
        step("Select Group", "Select a Group", "A group is selected and its sub groups are listed",
                grouped ? "Group \"" + sv.lastGroup + "\" selected (looked at: " + tried + ")" : "Problem — " + sv.problems, grouped ? "PASS" : "FAIL");
        if (!grouped) { addSummary("Result", "FAILED — group: " + sv.problems); return; }
        step("Select Sub Group", "Select a Sub Group", "A sub group is selected",
                "Sub Group \"" + sv.lastSubGroup + "\" selected (of the " + sv.optionNames("serviceMst.SubGroupId").size() + " that group \"" + sv.lastGroup + "\" offers)", "PASS");

        String dept = sv.selectDepartment(List.of("NURSING DEPARTMENT", "PHARMACY", "NURSING"));
        step("Select Department", "Select a Department", "A department is selected",
                !dept.isEmpty() ? "Department \"" + dept + "\" selected (of " + sv.optionNames("serviceMst.DepartmentId").size() + ")" : "Problem — " + sv.problems, !dept.isEmpty() ? "PASS" : "FAIL");
        if (dept.isEmpty()) { addSummary("Result", "FAILED — department: " + sv.problems); return; }

        String policy = sv.tickPolicy(List.of("Standard Tariff Revision", "Standard", "Self Pay", "Cash"));
        step("Tick a Pricing Policy", "In Pricing Policy Details, tick the checkbox of a pricing policy", "The pricing policy is ticked",
                !policy.isEmpty() ? "Ticked \"" + policy + "\" (of " + sv.policyNames().size() + " pricing policies; ticked now: " + sv.tickedPolicies() + ")" : "Problem — " + sv.problems, !policy.isEmpty() ? "PASS" : "FAIL");
        if (policy.isEmpty()) { addSummary("Result", "FAILED — pricing policy: " + sv.problems); return; }

        String codeType = sv.selectCodeType(List.of("Service Code", "13th Schedule"));
        step("Select Code Type", "Select a Code Type", "A code type is selected",
                !codeType.isEmpty() ? "Code Type \"" + codeType + "\" selected (of " + sv.codeTypeNames().size() + ")" : "Problem — " + sv.problems, !codeType.isEmpty() ? "PASS" : "FAIL");
        if (codeType.isEmpty()) { addSummary("Result", "FAILED — code type: " + sv.problems); return; }

        String code = "SV" + String.format("%06d", System.currentTimeMillis() % 1000000);
        boolean coded = sv.enterCode(code);
        step("Enter Code", "Enter a Code (" + code + ")", "The code is accepted", coded ? "Code \"" + code + "\" entered" : "Problem — " + sv.problems, coded ? "PASS" : "FAIL");
        if (!coded) { addSummary("Result", "FAILED — code: " + sv.problems); return; }

        boolean codeAdded = sv.clickAddCode();
        step("Click Add (Code Type)", "Click Add under Code Type", "The code is added to the Code Type table",
                codeAdded ? "Add clicked; the Code Type table gained a row" : "Problem — " + sv.problems, codeAdded ? "PASS" : "FAIL");

        boolean inCodeTable = codeAdded && sv.codeRows().stream().anyMatch(r -> r.toLowerCase().contains(code.toLowerCase()) && r.toLowerCase().contains(codeType.toLowerCase()));
        step("Verify the Code Type table", "In Code Type, look for the data just added", "The code type and code are listed in the table",
                inCodeTable ? "PASSES because the Code Type table lists \"" + sv.lastCodeRow + "\"" : "FAILS because the Code Type table does not list " + codeType + " / " + code + " (rows: " + sv.codeRows() + ")" + (sv.lastToast.isEmpty() ? "" : "; toast: \"" + sv.lastToast + "\""),
                inCodeTable ? "PASS" : "FAIL");

        // Add Set Details: the smart search
        String term = "", option = "";
        List<String> sug = List.of();
        String searched = "";
        for (String t : new String[]{"Panadeine", "Novofine Needles"}) {
            sug = sv.typeItem(t);
            searched += (searched.isEmpty() ? "" : "; ") + "\"" + t + "\" -> " + sug.size() + " suggestion(s)" + (sug.isEmpty() ? "" : " " + sug);
            if (sug.isEmpty()) continue;
            term = t;
            if (sv.pickSuggestion(0)) { option = sv.lastItem; break; }
        }
        step("Enter Item Name", "In Add Set Details, type \"Panadeine\" (or \"Novofine Needles\") in Item Name (smart search)", "Matching options are suggested",
                !term.isEmpty() ? "Typed \"" + term + "\"; suggested: " + sug + " (" + searched + ")" : "Problem — nothing was suggested (" + searched + ")", !term.isEmpty() ? "PASS" : "FAIL");
        if (term.isEmpty()) { addSummary("Result", "FAILED — item name: no suggestion"); return; }

        step("Select the option", "Click the first suggested option", "The option is selected into Item Name",
                !option.isEmpty() ? "Selected \"" + option + "\"; Item Name now shows it" + (sv.setListFilledBeforeAdd ? " and the List of Set Information already lists it" : "; the List of Set Information is still empty at this point")
                        : "Problem — " + sv.problems, !option.isEmpty() ? "PASS" : "FAIL");
        if (option.isEmpty()) { addSummary("Result", "FAILED — option: " + sv.problems); return; }

        boolean listed = sv.setListFilledBeforeAdd;
        boolean addClicked = false;
        if (!listed) { addClicked = sv.clickAddItem(); listed = addClicked && sv.listHas(option); }
        step("Verify List of Set Information", "Look for the selected option in List of Set Information", "The selected option is listed",
                listed ? "PASSES because List of Set Information lists \"" + (sv.lastAddedRow.isEmpty() ? option : sv.lastAddedRow) + "\""
                        + (addClicked ? " (selecting the option only filled Item Name; the set's Add button was clicked to put it in the list)" : "")
                        : "FAILS because \"" + option + "\" is not listed (rows: " + sv.setRows() + ")" + (sv.lastToast.isEmpty() ? "" : "; toast: \"" + sv.lastToast + "\""),
                listed ? "PASS" : "FAIL");

        // Submit
        String toast = sv.submitAndGetToast();
        boolean ok = sv.ok() && sv.saveSucceeded();
        step("Click Submit", "Click Submit", "The service is saved",
                sv.problems.isEmpty() ? "Submit clicked" + (sv.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + sv.lastDialog + "\"") : "Problem — " + sv.problems,
                sv.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = sv.savedByServer();
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (sv.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + sv.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the service (ResultStatus 1, \"" + sv.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + sv.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + sv.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        Boolean now = (ok || savedByServer) ? sv.nameTaken(name) : null;
        addSummary("Service Name", name);
        addSummary("Group / Sub Group / Department", sv.lastGroup + " / " + sv.lastSubGroup + " / " + sv.lastDepartment);
        addSummary("Pricing policy ticked", sv.lastPolicy);
        addSummary("Code Type / Code", sv.lastCodeType + " / " + sv.lastCode);
        addSummary("Set item", option + (sv.lastAddedRow.isEmpty() ? "" : " (list row: " + sv.lastAddedRow + ")"));
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + sv.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("On the server's list afterwards", now == null ? "(not checked)" : now ? "yes - \"" + name + "\" is now listed" : "NOT found");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
