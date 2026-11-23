package com.kpj.tests.Inventory_page.StockTakingActivity_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.util.List;

// The page object is also named StockTakeApproval — referenced by its fully-qualified name.

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>Stock Take Approval</b>.
 *
 * <ol>
 *   <li>Select the <b>Store</b> (Pharmacy Main Store) and click <b>Search</b>; verify rows are populated in the table (the dates default to today; if nothing was done today the From Date is moved back
 *       one month, then three, and the report says so).</li>
 *   <li>Click <b>Print</b>. On this screen Print needs a stock take to be selected first, so with nothing selected it answers "Please Select Stock Take!" — recorded as the screen's answer.</li>
 *   <li>Select any row (click on its Stock No — never on the Approval checkbox or the Forcefully Close button) and click <b>Print</b> again.</li>
 *   <li>Verify the print document was generated: it opens in a new tab ("Stock Take Approval Report", an HTML page written by the app, not a PDF), and it must carry the report title, the Stock No
 *       that was selected, the Store Name and at least one item row.</li>
 * </ol>
 *
 * <p>This test is read-only: it approves, closes and changes nothing. Pin the store with {@code -Dstocktakeapproval.store=}.</p>
 */
public class StockTakeApproval extends DevHisBase {

    static final String STORE = System.getProperty("stocktakeapproval.store", "Pharmacy Main Store");

    public StockTakeApproval() { super("Inventory_StockTaking_StockTakeApproval"); }

    public static void main(String[] args) {
        StockTakeApproval t = new StockTakeApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Stock Taking Activity - Stock Take Approval", "Inventory > Stock Taking Activity > Stock Take Approval",
                "Store " + STORE + "; Search; verify rows are populated in the table; Print (nothing selected yet); select a row; Print; verify the print document is generated. READ-ONLY.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTakeApproval sa = new com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTakeApproval(page);

        boolean on = sa.navigateViaMenu();
        step("Open Stock Take Approval", "Click Inventory -> Stock Taking Activity -> Stock Take Approval", "The Stock Take Approval screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Stock Take Approval not reached"); return; }

        String store = sa.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && sa.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + sa.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + sa.problems); return; }

        // Search: today first; if nothing was done today, move the From Date back (one month, then three)
        String dateNote = "dates left at today (" + sa.fromDate() + " - " + sa.toDate() + ")";
        int n = sa.clickSearch();
        boolean searchOk = sa.ok();
        LocalDate to = com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTakeApproval.parse(sa.toDate());
        for (int months : new int[]{1, 3}) {
            if (!searchOk || n > 0 || to == null) break;
            String picked = sa.pickFromDate(to.minusMonths(months));
            if (picked.isEmpty()) { searchOk = false; break; }
            dateNote = "no stock take today, so the From Date was moved back " + months + " month(s) to " + picked + " (To Date " + sa.toDate() + ")";
            n = sa.clickSearch();
            searchOk = sa.ok();
        }
        step("Click Search", "Click Search", "The search answers with the stock takes of the store",
                searchOk ? "Search answered: " + n + " stock take(s) listed; " + dateNote + (sa.lastToast.isEmpty() ? "" : "; the screen said \"" + sa.lastToast + "\"") : "Problem — " + sa.problems, searchOk ? "PASS" : "FAIL");
        if (!searchOk) { addSummary("Result", "FAILED — Search: " + sa.problems); return; }

        // verify rows populated in the table
        List<List<String>> rows = sa.stockTakes();
        int bad = 0;
        for (List<String> r : rows) if (r.size() < 4 || r.get(0).isEmpty() || r.get(1).isEmpty() || r.get(2).isEmpty() || r.get(3).isEmpty()) bad++;
        boolean populated = !rows.isEmpty() && bad == 0;
        String sample = rows.stream().limit(3).map(r -> String.join(" | ", r.subList(0, Math.min(r.size(), 4)))).toList().toString();
        step("Verify rows are populated in the table", "Read the stock take table after Search", "Rows are listed, each with a Stock No, Store Name, User and Date Time",
                populated ? "PASSES because the table (columns " + sa.headers() + ") lists " + rows.size() + " stock take(s), each with a Stock No, Store Name, User and Date Time; " + dateNote + "; first rows: " + sample
                          : rows.isEmpty() ? "FAILS because the table is empty after Search for " + STORE + " (" + dateNote + ")"
                                           : "FAILS because " + bad + " of " + rows.size() + " row(s) lack a Stock No, Store Name, User or Date Time; first rows: " + sample,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Result", "FAILED — table not populated"); return; }

        // Print with nothing selected: the screen's own answer
        String answer = sa.clickPrint();
        boolean tabOpened = sa.tabCount() > 1;
        step("Click Print", "Click Print (no stock take selected yet)", "Print needs a stock take: the screen asks for one to be selected",
                tabOpened ? "A tab opened straight away (no selection was needed)"
                          : answer.isEmpty() ? "No message appeared" : "The screen answered \"" + answer + "\" — Print needs a row to be selected first",
                !tabOpened && answer.toLowerCase().contains("select") ? "PASS" : "FAIL");
        if (tabOpened) { addSummary("Result", "FAILED — Print opened a tab before any row was selected (the screen changed?)"); return; }

        // select any row
        String no = sa.selectFirstRow();
        boolean rowOk = !no.isEmpty() && sa.ok();
        step("Select any row of data (click on it)", "Click the Stock No of the first row", "The stock take is selected and its items load in the grid below",
                rowOk ? "Selected " + no + " (" + sa.lastRowText + ") — its " + sa.lastDetailRows + " item row(s) loaded in the grid below" : "Problem — " + sa.problems, rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row: " + sa.problems); return; }

        // Print again and judge the document
        com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTakeApproval.Doc doc = sa.clickPrintAndCapture();
        String text = doc.text;
        boolean titleOk = doc.title.toLowerCase().contains("stock take approval report") || text.toLowerCase().contains("stock take approval report");
        boolean noOk = text.contains(no);
        boolean storeInDoc = text.toLowerCase().contains(STORE.toLowerCase());
        boolean itemRows = doc.rows >= 2;
        boolean generated = doc.opened && titleOk && noOk && storeInDoc && itemRows;
        String evidence = "tab \"" + doc.title + "\", " + doc.textLen + " characters of text, " + doc.tables + " table(s) with " + doc.rows + " row(s), " + doc.images + " image(s); it names Stock No "
                + (noOk ? no : "(NOT " + no + ")") + " and Store " + (storeInDoc ? STORE : "(NOT " + STORE + ")") + "; text: \"" + text.substring(0, Math.min(330, text.length())) + "\"";
        String verdict = generated ? "PASSES because the print document was generated: " + evidence
                                   : "FAILS because " + (!doc.opened ? doc.diagnostics : "the document is not what was asked for (" + (titleOk ? "" : "no report title; ") + (noOk ? "" : "Stock No missing; ")
                                           + (storeInDoc ? "" : "Store missing; ") + (itemRows ? "" : "no item row; ") + ") — " + evidence);
        if (doc.png != null && doc.png.length > 0) step(doc.png, "Verify the print document is generated", "Click Print with the row selected and look at the document that opens",
                "A report tab opens with the stock take's details and items", verdict, generated ? "PASS" : "FAIL");
        else step("Verify the print document is generated", "Click Print with the row selected and look at the document that opens", "A report tab opens with the stock take's details and items",
                verdict, generated ? "PASS" : "FAIL");

        addSummary("Store / dates", sa.lastStore + " / " + dateNote);
        addSummary("Stock takes listed", String.valueOf(rows.size()));
        addSummary("Stock take printed", no + " — " + sa.lastRowText);
        addSummary("Print with nothing selected", answer);
        addSummary("Print document", generated ? doc.title + " (" + doc.rows + " table rows)" : "NOT generated: " + doc.diagnostics);
        addSummary("Result", generated ? "Print document generated for " + no : "Print document not as expected");
    }
}
