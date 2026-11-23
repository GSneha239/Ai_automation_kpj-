package com.kpj.tests.Billing_page.DoctorShare_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;
import java.util.Random;

// The page object is also named DoctorShare - referenced by its fully-qualified name.

/**
 * Billing &gt; Doctor Share &gt; <b>Doctor Share</b> ({@code #/Doctorshare}). The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: Billing &gt; Doctor Share &gt; Doctor Share. From Date: two months back, day 6. Search.</li>
 *   <li>Tick a row (its totals are "the total available").</li>
 *   <li><b>Service Rate</b> + Update - the rate has to be within the total available; only then can the <b>Doctor Share Amount</b> be calculated and updated (it has to be within the new rate).</li>
 *   <li><b>Department</b> and <b>Doctor Name</b> (anything the lists offer) + Update. <b>Save</b>; verify the toast "Record Added Successfully.".</li>
 * </ol>
 *
 * <p>Nothing is copied from the recording except the order: the rate, the share amount, the department and the doctor are chosen again on every run (department / doctor from what the lists offer). Pin
 * them with {@code -Dds.rate=}, {@code -Dds.share=}.</p>
 *
 * <p>&#9888; SAVES REAL DOCTOR SHARE DATA on every run (the ticked charge is processed, so each run uses up one row).</p>
 */
public class DoctorShare extends DevHisBase {

    private static final String PIN_RATE = System.getProperty("ds.rate", "").trim();
    private static final String PIN_SHARE = System.getProperty("ds.share", "").trim();

    public DoctorShare() { super("Billing_DoctorShare_DoctorShare"); }

    public static void main(String[] args) {
        DoctorShare t = new DoctorShare();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    @Override
    protected void body() {
        meta("Billing - Doctor Share - Doctor Share", "Billing > Doctor Share > Doctor Share",
                "&#9888; Saves REAL doctor share data: From Date, Search, tick a row, Service Rate (within the total available) + Update, Doctor Share Amount (within the rate) + Update, Department + Doctor Name (any) + Update, Save, verify the toast.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.DoctorShare_page.DoctorShare ds = new com.kpj.pages.Billing_page.DoctorShare_page.DoctorShare(page);
        Random rnd = new Random();

        boolean on = ds.navigateViaMenu();
        step(page, "Open Doctor Share", "Click Billing -> Doctor Share -> Doctor Share", "The Doctor Share screen is shown",
                on ? "Opened " + page.url() + " (From " + ds.fromDate() + ", To " + ds.toDate() + ")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Doctor Share not reached"); return; }

        ds.problems.clear();
        String from = ds.pickFromDate(2, 6);
        step(page, "Set From Date", "Click From Date; go back two months; click day 6", "From Date is the 6th, two months back",
                ds.problems.isEmpty() ? "From Date = " + from + " (To Date " + ds.toDate() + ")" : "Problem - " + ds.problems, ds.problems.isEmpty() && from.startsWith("06/") ? "PASS" : "FAIL");

        ds.problems.clear();
        int rows = ds.clickSearch();
        step(page, "Click Search", "Click Search (GetDoctorShareList(DoctorShare))", "The charges in the date range are listed",
                rows > 0 ? rows + " charge(s) listed" : "No charge listed" + (ds.problems.isEmpty() ? "" : " - " + ds.problems), rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED - nothing listed"); return; }

        // the first row on the page that has a rate to work with
        List<String[]> grid = ds.gridRows();
        int shown = Math.max(1, ds.renderedRows());
        int idx = 0;
        for (int i = 0; i < Math.min(shown, grid.size()); i++) { if (num(grid.get(i)[3]) >= 2) { idx = i; break; } }
        String[] row = grid.get(idx);
        String rowInfo = row[0] + " | " + row[1] + " | doctor " + (row[2].isEmpty() || row[2].equals("null") ? "(none)" : row[2]) + " | rate " + row[3] + " | hospital " + row[4] + " | doctor share " + row[5];

        ds.problems.clear();
        boolean ticked = ds.tickRow(idx);
        String[] tot = ds.totals();
        step(page, "Tick a row", "Tick the charge (row " + (idx + 1) + ") and select it (grid.appScope.Getcheck(row))", "The row is ticked; its totals (the total available) show",
                ticked ? "Ticked " + rowInfo + "; total available: Service Rate " + tot[0] + ", Hospital Share " + tot[1] + ", Doctor Share " + tot[2]
                       : "Problem - " + ds.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED - row not ticked"); return; }
        double available = num(tot[0]);
        if (Double.isNaN(available) || available < 2) { step(page, "Check the total available", "Read Total Service Rate", "At least 2 available", "Total Service Rate = " + tot[0], "FAIL"); addSummary("Result", "FAILED - nothing available"); return; }

        // Service Rate: within the total available (the recording used 15)
        int maxRate = (int) Math.min(Math.floor(available), 50);
        int rate = !PIN_RATE.isEmpty() ? (int) num(PIN_RATE) : 2 + rnd.nextInt(Math.max(1, maxRate - 1));
        ds.problems.clear();
        ds.updateServiceRate(String.valueOf(rate));
        String[] t1 = ds.totals();
        boolean rateOk = ds.problems.isEmpty() && Math.abs(num(t1[0]) - rate) < 0.005;
        step(page, "Update the Service Rate", "Type " + rate + " in Service Rate (within the total available " + tot[0] + ") and click Update (UpdateServiceRate())", "The row's service rate becomes " + rate,
                rateOk ? "Service Rate " + ds.serviceRateBox() + " -> totals now: Service Rate " + t1[0] + ", Hospital Share " + t1[1] + ", Doctor Share " + t1[2] : "Problem - " + ds.problems + "; totals " + String.join(" / ", t1) + "; toasts: " + ds.toastsNow(),
                rateOk ? "PASS" : "FAIL");
        if (!rateOk) { addSummary("Result", "FAILED - service rate not updated"); return; }

        // Doctor Share Amount: only after the rate, and within it (the recording used 1 then 2)
        int share = !PIN_SHARE.isEmpty() ? (int) num(PIN_SHARE) : 1 + rnd.nextInt(Math.min(rate, 5));
        ds.problems.clear();
        ds.updateDoctorShareAmount(String.valueOf(share));
        String[] t2 = ds.totals();
        boolean shareOk = ds.problems.isEmpty() && Math.abs(num(t2[2]) - share) < 0.005 && Math.abs(num(t2[1]) - (rate - share)) < 0.005;
        step(page, "Update the Doctor Share Amount", "Type " + share + " in Doctor Share Amount (within the rate " + rate + ") and click Update (UpdateDoctorShareamt())", "The doctor share becomes " + share + " and the hospital share " + (rate - share),
                shareOk ? "Totals now: Service Rate " + t2[0] + ", Hospital Share " + t2[1] + ", Doctor Share " + t2[2] : "Problem - " + ds.problems + "; totals " + String.join(" / ", t2) + "; toasts: " + ds.toastsNow(),
                shareOk ? "PASS" : "FAIL");
        if (!shareOk) { addSummary("Result", "FAILED - doctor share not updated"); return; }

        // Department and Doctor Name: anything available (a department with no doctor is skipped)
        List<String> depts = ds.departmentNames();
        String dept = "", doctor = "";
        String deptMsg = "no department offered";
        List<String> docs = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 10 && !depts.isEmpty(); attempt++) {
            dept = depts.get(rnd.nextInt(depts.size()));
            deptMsg = ds.pickDepartment(dept);
            if (!deptMsg.isEmpty()) continue;
            page.waitForTimeout(1500);
            docs = ds.doctorNames();
            if (!docs.isEmpty()) break;
            dept = "";
        }
        boolean deptOk = !dept.isEmpty() && deptMsg.isEmpty() && !docs.isEmpty();
        step(page, "Select a Department", "Choose any Department the list offers", "A department is chosen and its doctors load",
                deptOk ? "Department = " + dept + " (one of " + depts.size() + "; " + docs.size() + " doctor(s) offered)" : "Problem - " + deptMsg + " (" + depts.size() + " departments offered, none gave a doctor)", deptOk ? "PASS" : "FAIL");
        if (!deptOk) { addSummary("Result", "FAILED - no department with doctors"); return; }

        doctor = docs.get(rnd.nextInt(docs.size()));
        String docMsg = ds.pickDoctor(doctor);
        final String doctorId = ds.doctorId();
        step(page, "Select a Doctor Name", "Choose any Doctor Name the list offers", "A doctor is chosen",
                docMsg.isEmpty() ? "Doctor Name = " + doctor + " (one of " + docs.size() + ")" : "Problem - " + docMsg, docMsg.isEmpty() ? "PASS" : "FAIL");
        if (!docMsg.isEmpty()) { addSummary("Result", "FAILED - doctor not chosen"); return; }

        ds.problems.clear();
        ds.updateDoctor();
        String gridDoctor = ds.gridRows().get(idx)[2];
        boolean docOk = ds.problems.isEmpty() && gridDoctor.toLowerCase().contains(doctor.toLowerCase().trim());
        step(page, "Update the Doctor", "Click the Doctor Name's Update (UpdateDoctor())", "The row's doctor becomes " + doctor,
                docOk ? "The row's Doctor Name is now " + gridDoctor : "Problem - the row shows \"" + gridDoctor + "\" " + ds.problems + " toasts: " + ds.toastsNow(), docOk ? "PASS" : "FAIL");

        ds.problems.clear();
        String toast = ds.clickSave();
        boolean saved = toast.toLowerCase().contains("added successfully") || toast.toLowerCase().contains("saved successfully") || toast.toLowerCase().contains("updated successfully");
        step(page, "Click Save", "Click Save (IUDDoctorShare())", "'Record Added Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (ds.problems.isEmpty() ? "" : " - " + ds.problems) : toast, saved ? "PASS" : "FAIL");
        addSummary("Charge", rowInfo);
        addSummary("Saved values", "Service Rate " + rate + ", Doctor Share " + share + ", Hospital Share " + (rate - share) + ", " + dept + " / " + doctor);
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        if (!saved) { addSummary("Result", "FAILED - not saved"); return; }

        // look the charge up again: processed charges normally leave the UnProcessed list
        page.waitForTimeout(2500);
        ds.problems.clear();
        ds.clickSearch();
        String where = "the list (default filter)";
        String[] found = findRow(ds.gridRows(), row);
        if (found == null) {
            ds.chooseProcessed("Processed");
            ds.clickSearch();
            where = "the Processed list";
            found = findRow(ds.gridRows(), row);
            if (found == null) { ds.chooseProcessed("ALL"); ds.clickSearch(); where = "the ALL list"; found = findRow(ds.gridRows(), row); }
        }
        boolean idOk = found != null && (doctorId.isEmpty() || found[7].equals(doctorId));
        boolean match = found != null && Math.abs(num(found[3]) - rate) < 0.005 && Math.abs(num(found[5]) - share) < 0.005 && idOk;
        step(page, "Verify the saved doctor share", "Search again and look up the charge " + row[0] + " | " + row[1],
                "The charge shows service rate " + rate + ", doctor share " + share + " and the chosen doctor (id " + doctorId + ")",
                found == null ? "The charge was not found again (default, Processed and ALL lists)" + (ds.problems.isEmpty() ? "" : " - " + ds.problems)
                              : "Found in " + where + ": doctor id " + found[7] + (idOk ? " (the chosen " + doctor + ")" : " (expected " + doctorId + ")") + " | rate " + found[3] + " | hospital " + found[4] + " | doctor share " + found[5],
                match ? "PASS" : "FAIL");
        addSummary("Result", match ? "Saved and found again with the new values" : (found == null ? "Saved; charge not found again" : "Saved; values differ"));
    }

    private static String[] findRow(List<String[]> rows, String[] key) {
        for (String[] r : rows) if (r[0].equals(key[0]) && r[1].equals(key[1])) return r;
        return null;
    }
}
