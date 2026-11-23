package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named AutoIndent — referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Auto Indent</b> (the menu entry, {@code #/AutoIndent}).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Inventory</b> → <b>Transfer</b> → <b>Auto Indent</b>.</li>
 *   <li>Select the <b>Requesting Store</b> and the <b>Issuing Store</b> from their lists (Pharmacy Main Store, then Pharmacy (Central), are tried first).</li>
 *   <li>Click <b>Search</b> — when the Requesting Store has no item to indent, the next store of the list is selected and searched, through the whole list, until one lists items.</li>
 *   <li>Verify the records are displayed in <b>Auto Indent Details</b>; select a record (tick it); enter the <b>Remarks</b>; click <b>Save Indent</b>; verify the success toast and the report the
 *       screen opens.</li>
 * </ol>
 *
 * <p>The screen's Search sends only the Requesting Store, so the walk is over that list; the Issuing Store is chosen so that it differs from it. This is the menu entry's own test — the Store Indent
 * test also ends with an Auto Indent section, reached from that screen's button with a fixed Pharmacy pair.</p>
 *
 * <p>&#9888; A passing run SAVES one REAL auto indent. Pin the first Requesting / Issuing Store with {@code -Dai.requesting=} / {@code -Dai.issuing=}.</p>
 */
public class AutoIndent extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final String[] ISSUING_PREFERENCE = {"Pharmacy (Central)", "Pharmacy Main Store"};
    private static final String[] REQUESTING_PREFERENCE = {"Pharmacy Main Store", "Pharmacy (Central)"};

    public AutoIndent() { super("Inventory_Transfer_AutoIndent"); }

    public static void main(String[] args) {
        AutoIndent t = new AutoIndent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** The Issuing Store for a Requesting Store: the first preferred store that differs from it, else the first store of the list that does. */
    private static String issuingFor(List<String> issuingList, String requesting) {
        for (String want : ISSUING_PREFERENCE) for (String s : issuingList) if (s.equalsIgnoreCase(want) && !s.equals(requesting)) return s;
        for (String s : issuingList) if (!s.equals(requesting)) return s;
        return "";
    }

    @Override
    protected void body() {
        meta("Inventory - Transfer - Auto Indent", "Inventory > Transfer > Auto Indent",
                "&#9888; SAVES one REAL auto indent: select Requesting and Issuing Store (next store of the list until items are listed), Search, select a record, "
                        + "enter remarks, click Save Indent, verify the success toast and report.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        String remarks = System.getProperty("ai.remarks", "Automated auto indent remarks " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.AutoIndent ai = new com.kpj.pages.Inventory_page.Transfer_page.AutoIndent(page);

        // 1) Navigate
        boolean on = ai.navigateViaMenu();
        step(page, "Open Auto Indent screen", "Click Inventory -> Transfer -> Auto Indent", "The Auto Indent screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Auto Indent not reached"); return; }

        List<String> requestingList = ai.requestingStores(), issuingList = ai.issuingStores();
        if (requestingList.isEmpty() || issuingList.isEmpty()) {
            step(page, "Select requesting store and issuing store", "Open the Requesting Store / Issuing Store lists", "Both lists offer stores",
                    "FAILED — the Requesting Store list has " + requestingList.size() + " store(s), the Issuing Store list " + issuingList.size(), "FAIL");
            addSummary("Result", "FAILED — a store list is empty");
            return;
        }
        // the order the Requesting Stores are tried in: the pinned one, Pharmacy Main Store, Pharmacy (Central), then the list order
        List<String> order = new ArrayList<>();
        String pinned = System.getProperty("ai.requesting", "");
        if (!pinned.isEmpty() && requestingList.contains(pinned)) order.add(pinned);
        for (String want : REQUESTING_PREFERENCE) for (String s : requestingList) if (s.equalsIgnoreCase(want) && !order.contains(s)) order.add(s);
        for (String s : requestingList) if (!order.contains(s)) order.add(s);

        // 2) Requesting + Issuing store
        String first = order.get(0);
        String pinnedIssuing = System.getProperty("ai.issuing", "");
        boolean reqOk = ai.selectRequesting(first);
        String reqProblems = String.valueOf(ai.problems);
        String issuing = !pinnedIssuing.isEmpty() && issuingList.contains(pinnedIssuing) && !pinnedIssuing.equals(first) ? pinnedIssuing : issuingFor(issuingList, first);
        boolean issOk = reqOk && ai.selectIssuing(issuing);
        step(page, "Select requesting store and issuing store", "Open each list and click the Requesting Store \"" + first + "\" and the Issuing Store \"" + issuing + "\"",
                "Both stores are selected from their lists",
                reqOk && issOk ? "Requesting Store \"" + ai.lastRequesting + "\", Issuing Store \"" + ai.lastIssuing + "\" selected from the lists (" + requestingList.size() + " / " + issuingList.size() + " stores offered)"
                               : "FAILED — " + (reqOk ? ai.problems : reqProblems),
                reqOk && issOk ? "PASS" : "FAIL");
        if (!(reqOk && issOk)) { addSummary("Result", "FAILED — stores: " + (reqOk ? ai.problems : reqProblems)); return; }

        // 3) Search; next Requesting Store until the screen lists items
        List<String> tried = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        tried.add(first);
        int n = ai.clickSearch();
        String foundStore = n > 0 ? ai.lastRequesting : "";
        System.out.println("AutoIndent: [1/" + order.size() + "] " + first + " -> " + n + " item(s)");
        for (int i = 1; i < order.size() && n == 0; i++) {
            String next = order.get(i);
            if (!ai.selectRequesting(next)) { skipped.add(next + " (" + ai.problems + ")"); continue; }
            if (ai.chosenIssuing().equals(next)) {                     // the app rejects a matching pair: move the Issuing Store off it
                String other = issuingFor(issuingList, next);
                if (other.isEmpty() || !ai.selectIssuing(other)) { skipped.add(next + " (no different Issuing Store)"); continue; }
            }
            tried.add(next);
            n = ai.clickSearch();
            if (!(ai.lastSearchStatus > 0 && ai.lastSearchStatus < 400)) skipped.add(next + " (search: " + ai.problems + ")");
            System.out.println("AutoIndent: [" + (i + 1) + "/" + order.size() + "] " + next + " -> " + n + " item(s)");
            if (n > 0) foundStore = ai.lastRequesting;
        }
        boolean found = n > 0;
        String triedText = tried.size() == 1 ? "\"" + tried.get(0) + "\"" : tried.size() + " store(s), from \"" + tried.get(0) + "\" to \"" + tried.get(tried.size() - 1) + "\"";
        step(page, "Click search (try the next requesting store until records come back)", "Click Search; if the store lists no item select the next store of the list and search again",
                "Records are displayed in Auto Indent Details",
                found ? n + " item(s) listed for Requesting Store \"" + foundStore + "\" after trying " + triedText + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped)
                      : "FAILED — none of the " + tried.size() + " store(s) tried (" + triedText + ") listed an item" + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped)
                              + (ai.ok() ? "" : "; last Search problem: " + ai.problems),
                found ? "PASS" : "FAIL");
        addSummary("Stores tried", tried.size() + " of " + order.size() + (found ? " — items listed for \"" + foundStore + "\"" : " — no store lists an item"));
        if (!found) { addSummary("Result", "FAILED — no Requesting Store lists an item to indent"); return; }

        // keep the pair valid for Save: the Issuing Store must differ from the Requesting Store that returned the items
        if (ai.chosenIssuing().isEmpty() || ai.chosenIssuing().equals(foundStore)) {
            String other = issuingFor(issuingList, foundStore);
            if (other.isEmpty() || !ai.selectIssuing(other)) { addSummary("Result", "FAILED — no Issuing Store other than " + foundStore); return; }
        }

        // 4) Records are displayed in Auto Indent Details
        int rows = ai.itemRowCount();
        boolean shown = rows > 0 && ai.headers().stream().anyMatch(h -> h.equalsIgnoreCase("Item Name"));
        step(page, "Verify records are displayed in Auto Indent Details", "Read Auto Indent Details after Search", "The items are listed with their code, name and quantities",
                shown ? rows + " item(s) listed (columns " + ai.headers() + "); first: " + ai.sample(2) : "FAILED — Auto Indent Details shows " + rows + " item(s)",
                shown ? "PASS" : "FAIL");
        if (!shown) { addSummary("Result", "FAILED — Auto Indent Details not populated"); return; }

        // 5) Select the record
        String item = ai.tickFirstItem();
        boolean ticked = !item.isEmpty() && ai.ok() && ai.tickedCount() >= 1;
        step(page, "Select the record", "Tick the first item in Auto Indent Details", "The record is selected",
                ticked ? "Ticked: " + item : "FAILED — " + ai.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no record could be selected"); return; }

        // 6) Remarks
        String typed = ai.enterRemarks(remarks);
        boolean remarksOk = typed.equals(remarks) && ai.ok();
        step(page, "Enter remarks", "Enter the Remarks " + remarks, "The remarks are entered", remarksOk ? typed : "FAILED — " + (ai.ok() ? "the box holds \"" + typed + "\"" : ai.problems),
                remarksOk ? "PASS" : "FAIL");

        // 7) Save Indent
        boolean clicked = ai.clickSaveIndent();
        step(page, "Click save indent", "Click Save Indent", "The Save Indent action fires", clicked ? "Clicked Save Indent" : "FAILED — " + ai.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save Indent was not clicked"); return; }

        // 8) the success toast — checked before the report, since the toast fades while the report tab is judged
        String toast = ai.waitForSaveToast();
        boolean saved = com.kpj.pages.Inventory_page.Transfer_page.AutoIndent.isSuccess(toast);
        step(page, "Verify success toast message", "Wait for the success toast", "'... successfully' toast",
                toast.isEmpty() ? "FAILED — no message appeared after Save Indent" : saved ? toast : "FAILED — the screen answered \"" + toast + "\"", saved ? "PASS" : "FAIL");

        // 9) the report the save opens
        PdfReport.Result pdf = ai.captureReport();
        boolean tab = !pdf.url.isEmpty();
        boolean reportOk = tab && !pdf.blank;
        String evidence = pdf.diagnostics + (pdf.png != null && pdf.png.length > 0 ? " The screenshot is the PDF's first page rendered from the file itself." : "");
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generation", "Wait for the report to open", "A non-blank PDF is generated", reportOk ? evidence : "FAILED — " + evidence, reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generation", "Wait for the report to open", "A non-blank PDF is generated", reportOk ? evidence : "FAILED — " + evidence, reportOk ? "PASS" : "FAIL");
        }

        addSummary("[Auto Indent] Indent saved", "Requesting " + foundStore + " | Issuing " + ai.chosenIssuing() + " | item " + ai.lastItem + " | remarks " + ai.lastRemarks + " | "
                + (saved ? toast : "not confirmed (\"" + toast + "\")") + " | report " + (tab ? pdf.url : "(none)"));
        addSummary("Indents created", (saved ? 1 : 0) + " / 1");
    }
}
