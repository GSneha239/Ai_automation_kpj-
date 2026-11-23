package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

// The page object is also named MiscellaneousIncome - referenced by its fully-qualified name.

/**
 * Billing &gt; Miscellaneous &gt; <b>Miscellaneous Income</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: Billing &gt; Miscellaneous &gt; Miscellaneous Income; list filter From Date (two months back, day 6) + Search.</li>
 *   <li>Entry form: Name, Account Head, Department, <b>Cash Counter</b> (any available - picked at random), Amount, Remark.</li>
 *   <li><b>Save</b> (once); verify the toast "Data Saved Successfully." and the new row in the list.</li>
 * </ol>
 *
 * <p>The Name and Remark are generated text (different on every run), not the recorded ones. Pin the cash counter with {@code -Dmi.counter=}, the department with {@code -Dmi.dept=}, the amount with {@code -Dmi.amount=}.</p>
 *
 * <p>&#9888; SAVES A REAL MISCELLANEOUS INCOME record on every run (the list has a Delete button; this test never uses it).</p>
 */
public class MiscellaneousIncome extends DevHisBase {

    private static final String DEPT = System.getProperty("mi.dept", "3RD FLOOR STORE").trim();
    private static final String AMOUNT = System.getProperty("mi.amount", "200").trim();
    private static final String COUNTER = System.getProperty("mi.counter", "").trim();

    public MiscellaneousIncome() { super("Billing_MiscellaneousIncome"); }

    public static void main(String[] args) {
        MiscellaneousIncome t = new MiscellaneousIncome();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    @Override
    protected void body() {
        meta("Billing - Miscellaneous Income", "Billing > Miscellaneous > Miscellaneous Income",
                "&#9888; Saves a REAL miscellaneous income: filter + Search; Name, Account Head, Department, Cash Counter (any), Amount, Remark; Save; verify the toast and the new list row.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.MiscellaneousIncome mi = new com.kpj.pages.Billing_page.MiscellaneousIncome(page);

        boolean on = mi.navigateViaMenu();
        step(page, "Open Miscellaneous Income", "Click Billing -> Miscellaneous -> Miscellaneous Income", "The Miscellaneous Income screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Miscellaneous Income not reached"); return; }

        mi.problems.clear();
        String from = mi.pickFromDate(2, 6);
        step(page, "Set the From Date", "Open the From Date picker; previous month twice; click day 6", "From Date is the 6th, two months back",
                from.startsWith("06/") && mi.problems.isEmpty() ? "From " + from + ", To " + mi.toDate() : "Problem - From=" + from + " " + mi.problems, from.startsWith("06/") ? "PASS" : "FAIL");

        mi.problems.clear();
        List<String[]> before = mi.clickSearch();
        step(page, "Search the list", "Click Search (fngetMisincomeGrid();)", "The list of Miscellaneous Income is shown",
                mi.problems.isEmpty() ? before.size() + " record(s) listed" + (before.isEmpty() ? "" : ", last: " + before.get(before.size() - 1)[0] + " " + before.get(before.size() - 1)[1] + " " + before.get(before.size() - 1)[2]) : "Problem - " + mi.problems,
                mi.problems.isEmpty() ? "PASS" : "FAIL");

        String stamp = new SimpleDateFormat("HHmmss").format(new Date());
        String name = "MiscIncome_" + stamp;
        String remark = "Misc income automated test " + stamp;

        mi.problems.clear();
        String billBefore = mi.billNo();
        String typedName = mi.fillBox("MiscellaneousIncome.name", name);
        step(page, "Enter the Name", "Type \"" + name + "\" in Name (generated; the recording typed another)", "Name = " + name,
                typedName.equals(name) ? "Name = " + typedName + " (Bill No. box shows \"" + billBefore + "\")" : "Problem - Name=" + typedName + " " + mi.problems, typedName.equals(name) ? "PASS" : "FAIL");

        mi.problems.clear();
        List<String> heads = mi.accountHeadNames();
        String head = heads.isEmpty() ? "Biling head" : (heads.contains("Biling head") ? "Biling head" : heads.get(0));
        String hm = mi.pickAccountHead(head);
        step(page, "Select the Account Head", "Choose \"" + head + "\" in Account Head (offered: " + heads + ")", "Account Head = " + head,
                hm.isEmpty() ? "Account Head = " + head : "Problem - " + hm, hm.isEmpty() ? "PASS" : "FAIL");

        mi.problems.clear();
        String dm = mi.pickDepartment(DEPT);
        step(page, "Select the Department", "Choose \"" + DEPT + "\" in Department", "Department = " + DEPT,
                dm.isEmpty() ? "Department = " + DEPT : "Problem - " + dm, dm.isEmpty() ? "PASS" : "FAIL");

        // the Cash Counter can be anything available -> pick one at random
        mi.problems.clear();
        List<String> counters = mi.cashCounterNames();
        String counter = !COUNTER.isEmpty() ? COUNTER : counters.isEmpty() ? "ADMISSION 1" : counters.get(new Random().nextInt(counters.size()));
        String cm = mi.pickCashCounter(counter);
        step(page, "Select the Cash Counter", "Choose \"" + counter + "\" in Cash Counter (any available; " + counters.size() + " offered)", "Cash Counter = " + counter,
                cm.isEmpty() ? "Cash Counter = " + counter + " (picked at random of " + counters.size() + ")" : "Problem - " + cm, cm.isEmpty() ? "PASS" : "FAIL");

        mi.problems.clear();
        String typedAmt = mi.fillBox("MiscellaneousIncome.amount", AMOUNT);
        step(page, "Enter the Amount", "Type \"" + AMOUNT + "\" in Amount", "Amount = " + AMOUNT,
                typedAmt.equals(AMOUNT) ? "Amount = " + typedAmt : "Problem - Amount=" + typedAmt + " " + mi.problems, typedAmt.equals(AMOUNT) ? "PASS" : "FAIL");

        mi.problems.clear();
        String typedRem = mi.fillBox("MiscellaneousIncome.remark", remark);
        step(page, "Enter the Remark", "Type \"" + remark + "\" in Remark (generated; the recording typed another)", "Remark = " + remark,
                typedRem.equals(remark) ? "Remark = " + typedRem : "Problem - Remark=" + typedRem + " " + mi.problems, typedRem.equals(remark) ? "PASS" : "FAIL");

        mi.problems.clear();
        String toast = mi.clickSave();
        boolean saved = toast.toLowerCase().contains("saved successfully");
        String billAfter = mi.billNo();
        step(page, "Click Save", "Click Save (saveadd();) once", "'Data Saved Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (mi.problems.isEmpty() ? "" : " - " + mi.problems) : toast + " (Bill No. box now shows \"" + billAfter + "\")", saved ? "PASS" : "FAIL");
        addSummary("Record", name + " / " + head + " / " + DEPT + " / " + counter + " / " + AMOUNT + " / " + remark);
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        addSummary("Bill No.", billAfter);
        if (!saved) { addSummary("Result", "FAILED - not saved"); return; }

        // search again (same range) and look for the new row
        page.waitForTimeout(1500);
        List<String[]> after = mi.clickSearch();
        List<String[]> mine = new ArrayList<>();
        for (String[] r : after) { if (r[1].equals(name)) mine.add(r); }
        boolean listed = mine.size() == 1 && num(mine.get(0)[2]) == num(AMOUNT);
        step(page, "Verify the new record in the list", "Search again (same dates); look for Name \"" + name + "\"", "Exactly one new row with the Name and Amount " + AMOUNT,
                mine.isEmpty() ? "No row named " + name + " (the list held " + before.size() + ", now " + after.size() + ")"
                        : mine.size() > 1 ? mine.size() + " rows named " + name + ": " + describe(mine) + " (the list held " + before.size() + ", now " + after.size() + ")"
                        : "Listed: " + mine.get(0)[0] + ", " + mine.get(0)[1] + ", " + mine.get(0)[2] + " (the list held " + before.size() + ", now " + after.size() + ")",
                listed && after.size() == before.size() + 1 ? "PASS" : "FAIL");
        addSummary("List", before.size() + " -> " + after.size());
        addSummary("Result", listed ? "Saved and listed as " + mine.get(0)[0] : mine.isEmpty() ? "Saved but not found in the list" : "Saved - see the list check");
    }

    private static String describe(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) { if (sb.length() > 0) sb.append("; "); sb.append(r[0]).append(' ').append(r[1]).append(' ').append(r[2]); }
        return sb.toString();
    }
}
