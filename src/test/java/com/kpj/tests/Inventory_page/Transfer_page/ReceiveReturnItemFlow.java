package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ReceiveReturnItemFlow — referenced by its fully-qualified name.

/**
 * Inventory &gt; <b>Receive Return Item</b> — the WRITING flow (the read-only suite {@code Inventory_page.Transfer_page.ReceiveReturnItem} is separate and unchanged).
 *
 * <ol>
 *   <li>Open Inventory &rarr; Receive Return Item and click <b>New</b> (the script does not mention it, but the form lives behind it).</li>
 *   <li>Select <b>Received From</b> (Pharmacy Main Store) and <b>Received To</b> (Pharmacy (Central)); click <b>Search</b>.</li>
 *   <li>Select one of the returns by clicking its <b>Return Number</b> cell - its items appear below.</li>
 *   <li>Enter the <b>Received Quantity</b> (1) in the item row below.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The return taken is the first pending one that has a single item row with a Transit Quantity of at least 1 (the script speaks of "the row"); if none has, the first one whose rows all have stock in transit
 * (every Received Quantity must be above 0 for Save). The Received Date, Remarks and the report tab are not part of the script and are left as they are.</p>
 *
 * <p>Options: {@code -Drri.from=}, {@code -Drri.to=}, {@code -Drri.qty=}.</p>
 *
 * <p>&#9888; A successful run RECEIVES one pending return into the store (a real, irreversible stock movement); it stops being listed as pending.</p>
 */
public class ReceiveReturnItemFlow extends DevHisBase {

    public ReceiveReturnItemFlow() { super("Inventory_ReceiveReturnItemFlow"); }

    public static void main(String[] args) {
        ReceiveReturnItemFlow t = new ReceiveReturnItemFlow();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private final String from = System.getProperty("rri.from", "Pharmacy Main Store");
    private final String to = System.getProperty("rri.to", "Pharmacy (Central)");
    private final String qty = System.getProperty("rri.qty", "1");

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    /** A step whose screenshot is taken here with a 40 s limit (the framework's 10 s one times out now and then on the grid screens). */
    private void stepL(String name, String desc, String expected, String actual, String status) {
        byte[] png = null;
        try { png = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { png = null; }
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(name, desc, expected, actual, status);
    }

    @Override
    protected void body() {
        meta("Inventory - Receive Return Item", "Inventory > Receive Return Item (#/ReceiveReturnItemList -> #/add-ReceiveReturnItem)",
                "&#9888; RECEIVES a real pending return. Select Received From (" + from + ") and Received To (" + to + "); click Search; select one of the returns by clicking its Return Number; "
                        + "enter the Received Quantity (" + qty + ") in the item row below; click Save; verify the success toast.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.ReceiveReturnItemFlow rr = new com.kpj.pages.Inventory_page.Transfer_page.ReceiveReturnItemFlow(page);

        boolean list = rr.navigateViaMenu();
        stepL("Open Inventory - Receive Return Item", "Click Inventory -> Receive Return Item", "The Receive Return Item list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ") — " + rr.navigationProblem, list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — Receive Return Item not reached: " + rr.navigationProblem); return; }

        boolean form = rr.clickNew();
        stepL("Click New", "Click New (not in the script, but the form is behind it)", "The Receive Return Item form is shown",
                form ? "The form opened (" + page.url() + ")" : "Problem — " + rr.problems, form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the form did not open: " + rr.problems); return; }

        // 1) the two stores
        String f = rr.selectReceivedFrom(from);
        stepL("Select Received From", "Select Received From \"" + from + "\"", "The store is selected",
                !f.isEmpty() ? "Received From \"" + f + "\" selected (of " + rr.options("ReceiveReturnItem.FromStore").size() + " stores); the screen's model holds it (store id " + rr.storeId("from") + ")" : "Problem — " + rr.problems,
                !f.isEmpty() ? "PASS" : "FAIL");
        if (f.isEmpty()) { addSummary("Result", "FAILED — Received From: " + rr.problems); return; }
        String t = rr.selectReceivedTo(to);
        stepL("Select Received To", "Select Received To \"" + to + "\"", "The store is selected",
                !t.isEmpty() ? "Received To \"" + t + "\" selected (of " + rr.options("ReceiveReturnItem.ToStore").size() + " stores); the screen's model holds it (store id " + rr.storeId("to") + ") and it searched by itself" : "Problem — " + rr.problems,
                !t.isEmpty() ? "PASS" : "FAIL");
        if (t.isEmpty()) { addSummary("Result", "FAILED — Received To: " + rr.problems); return; }

        // 2) Search
        int found = rr.clickSearch();
        List<com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem.Return> returns = rr.transfers();
        stepL("Click Search", "Click Search", "The pending returns of that route are listed",
                found > 0 ? "Search clicked - " + found + " pending return(s) listed: " + returns : "Problem — no pending return is listed for " + from + " -> " + to + (rr.problems.isEmpty() ? "" : " (" + rr.problems + ")"),
                found > 0 ? "PASS" : "FAIL");
        if (found == 0) { addSummary("Result", "FAILED — no pending returns for " + from + " -> " + to); return; }

        // 3) one return: the first with a single item row that has stock in transit (else the first with stock in transit on every row)
        int pickIdx = -1;
        List<String[]> pickItems = List.of();
        int fallbackIdx = -1;
        StringBuilder tried = new StringBuilder();
        for (int i = 0; i < Math.min(found, 8) && pickIdx < 0; i++) {
            List<String[]> items = rr.clickRow(i);
            boolean allTransit = !items.isEmpty() && items.stream().allMatch(r -> num(r[5]) >= 1);
            tried.append(tried.length() == 0 ? "" : "; ").append(returns.get(i).number).append(items.isEmpty() ? " (no items)" : " (" + items.size() + " item row(s)" + (allTransit ? "" : ", no stock in transit") + ")");
            if (allTransit && items.size() == 1) { pickIdx = i; pickItems = items; }
            else if (allTransit && fallbackIdx < 0) fallbackIdx = i;
        }
        if (pickIdx < 0 && fallbackIdx >= 0) { pickIdx = fallbackIdx; pickItems = rr.clickRow(pickIdx); }
        else if (pickIdx >= 0) pickItems = rr.clickRow(pickIdx);   // re-select it: later rows were clicked after it
        boolean selected = pickIdx >= 0 && !pickItems.isEmpty();
        String number = selected ? returns.get(pickIdx).number : "";
        stepL("Select a return by clicking its Return Number", "Click the Return Number cell of one of the returns", "The return's items appear below",
                selected ? "Clicked the Return Number of row " + (pickIdx + 1) + " of " + found + ": " + returns.get(pickIdx) + " - " + pickItems.size() + " item row(s) appear below: "
                        + pickItems.stream().map(r -> r[1] + " (batch " + (r[2].isEmpty() ? "-" : r[2]) + ", return qty " + r[4] + ", transit qty " + r[5] + ", received qty " + r[6] + ")").toList()
                        + ". Tried: " + tried
                           : "Problem — no return with items in transit among the first " + Math.min(found, 8) + " (" + tried + ")",
                selected ? "PASS" : "FAIL");
        if (!selected) { addSummary("Result", "FAILED — no return could be selected"); return; }

        // 4) the Received Quantity of every item row (Save needs each above 0; the script speaks of one row)
        boolean qtyOk = true;
        for (int i = 0; i < pickItems.size(); i++) qtyOk &= rr.enterReceivedQuantity(i, qty);
        List<String[]> after = rr.items();
        stepL("Enter received quantity", "Enter Received Quantity " + qty + " in the row below", "The quantity is accepted",
                qtyOk ? "Received Quantity " + qty + " typed in " + (pickItems.size() == 1 ? "the item row" : "each of the " + pickItems.size() + " item rows") + " (" + after.stream().map(r -> r[1] + " -> " + r[6]).toList() + "); the boxes and the rows' data hold it"
                      : "Problem — " + rr.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — received quantity: " + rr.problems); return; }

        // 5) Save + toast
        int fromId = rr.storeId("from"), toId = rr.storeId("to");
        int tabsBefore = rr.clickSave();
        boolean clicked = rr.problems.isEmpty();
        String toast = clicked ? rr.awaitToast(25000) : "";
        stepL("Click Save", "Click Save", "The return is received",
                clicked ? "Save clicked" + (rr.lastSaveMessage.isEmpty() ? "" : "; the server answered \"" + rr.lastSaveMessage.trim() + "\" (ResultStatus " + rr.lastSaveStatus + ")") : "Problem — " + rr.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save: " + rr.problems); return; }

        boolean success = rr.saveSucceeded();
        int pending = rr.stillPending(number, fromId, toId);
        stepL("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\" (server ResultStatus " + rr.lastSaveStatus + (rr.lastResponseId.isEmpty() ? "" : ", id " + rr.lastResponseId) + "); the return "
                        + number + (pending == 0 ? " is no longer listed as pending" : pending == 1 ? " is STILL listed as pending" : " could not be re-checked") + "; the screen is now " + page.url()
                        : toast.isEmpty() ? "FAILS because no toast appeared — " + rr.saveDiagnosis() : "FAILS because the toast is not a success message: \"" + toast + "\" — " + rr.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        addSummary("Received From / To", fromId + " / " + toId + " (" + from + " -> " + to + ")");
        addSummary("Pending returns listed", found + ": " + returns);
        addSummary("Return received", number + (pickItems.isEmpty() ? "" : " - " + pickItems.stream().map(r -> r[0] + " " + r[1] + " (batch " + (r[2].isEmpty() ? "-" : r[2]) + ")").toList()));
        addSummary("Received Quantity", qty);
        addSummary("Server answer", rr.savedByServer() ? "saved (ResultStatus 1" + (rr.lastResponseId.isEmpty() ? "" : ", id " + rr.lastResponseId) + ", \"" + rr.lastSaveMessage.trim() + "\")" : "(no save confirmed" + (rr.lastSaveMessage.isEmpty() ? "" : ": " + rr.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
