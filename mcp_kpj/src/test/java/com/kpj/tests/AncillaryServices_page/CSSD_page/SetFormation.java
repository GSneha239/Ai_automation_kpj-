package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The page object is also named SetFormation — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Set Formation</b>.
 *
 * <ol>
 *   <li>Open the screen and read the sets already formed; click <b>Add</b>.</li>
 *   <li>Select the <b>Store</b> ("Pharmacy Main Store") and a <b>Group Name</b>.</li>
 *   <li>In Add Set Details type "pana" in <b>Item Name</b> (a smart search), click the option "Panadeine Tab (Paracetamol + Codeine)", click one row of the <b>Batch List</b> popup (a batch with stock),
 *       click <b>Add</b> (the item joins the List of Set Information).</li>
 *   <li>Click <b>Save</b>, verify the toast and that the set is listed.</li>
 * </ol>
 *
 * <p>Choosing a group loads that group's items from its last formation, so a group that was formed before refuses an item it already holds ("Selected Item is already added!"); and on this environment
 * only a group that was formed successfully before can form a new set (a never-formed group is refused at Save with "Batch could not be created for the selected set.", a group whose loaded items have
 * no stock with "Stock unavailable for selected item!"). As the written steps allow ("try other item name or select another group name"), the test tries the groups already in the list of formed sets
 * first; if the app refuses an item it tries the next suggestion, then other search starts (pana, para, ibu, amox, vita) because every formation is reloaded into the next one; and if Save is refused
 * (nothing is written) it restarts the form with the next group. Every refusal is listed in the report.</p>
 *
 * <p>This test FORMS A REAL SET for the store (the items' stock moves into the set). Override the search text, the preferred item, a batch and the store with {@code -Dsetformation.search=},
 * {@code -Dsetformation.item=}, {@code -Dsetformation.batch=} and {@code -Dsetformation.store=}.</p>
 */
public class SetFormation extends DevHisBase {

    public SetFormation() { super("AncillaryServices_CSSD_SetFormation"); }

    public static void main(String[] args) {
        SetFormation t = new SetFormation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** One group as seen when selected: how many items the app loaded from its last formation. */
    private static final class GroupInfo {
        final String name; final int loaded; final String toast;
        GroupInfo(String name, int loaded, String toast) { this.name = name; this.loaded = loaded; this.toast = toast; }
    }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Set Formation", "Ancillary Services > CSSD > Set Formation",
                "Add; select the store and a group; search an item (pana...) and click the option; click a batch row in the popup; Add; Save; verify the toast. FORMS A REAL SET.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.SetFormation sf = new com.kpj.pages.AncillaryServices_page.CSSD_page.SetFormation(page);

        boolean on = sf.navigateViaMenu();
        step("Open Set Formation", "Click Ancillary Services -> CSSD -> Set Formation", "The Set Formation screen is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Set Formation not reached"); return; }

        List<String[]> before = sf.searchList();
        step("Read the sets already formed", "Click Search and read the list", "The existing formations are known",
                before.isEmpty() ? "No formed set is listed" : before.size() + " set(s) listed (pager: \"" + sf.pagerText() + "\"): " + before.stream().map(r -> r[1] + " (code " + r[2] + ", qty " + r[3] + ")").toList(), "PASS");

        boolean opened = sf.clickAdd();
        step("Click Add", "Click Add", "The Set Formation form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + sf.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + sf.problems); return; }

        String storeWanted = System.getProperty("setformation.store", "Pharmacy Main Store");
        String store = sf.selectStore(storeWanted);
        List<String> groupNames = sf.ok() ? sf.groupNames() : List.of();
        step("Select store name", "Select the Store", "The store is selected and its groups are listed",
                sf.ok() ? "Store \"" + store + "\" selected; " + groupNames.size() + " group(s) offered: " + groupNames : "Problem — " + sf.problems, sf.ok() ? "PASS" : "FAIL");
        if (!sf.ok()) { addSummary("Result", "FAILED — store: " + sf.problems); return; }

        // scan every group once: selecting one loads the items of its last formation (and a toast saying how many)
        List<GroupInfo> scan = new ArrayList<>();
        for (String g : groupNames) {
            String sel = sf.selectGroup(g);
            if (sel.isEmpty()) continue;
            scan.add(new GroupInfo(sel, sf.setRows().size(), sf.lastToast));
        }
        // Which group works: the evidence on this environment is that only a group that was formed successfully before (it is in the list of formed sets, and its items are reloaded) can form a new set;
        // a never-formed group is refused with "Batch could not be created for the selected set." and a group whose loaded items have no stock with "Stock unavailable for selected item!".
        // So the groups already in the list go first, then the others; within each, the group that loads the fewest items first (stable, in the listed order).
        List<String> formedBefore = before.stream().map(r -> r[1].toLowerCase()).toList();
        List<GroupInfo> order = new ArrayList<>(scan);
        order.sort(Comparator.<GroupInfo>comparingInt(x -> formedBefore.contains(x.name.toLowerCase()) ? 0 : 1).thenComparingInt(x -> x.loaded));
        StringBuilder scanText = new StringBuilder();
        for (GroupInfo gi : scan) scanText.append(scanText.length() == 0 ? "" : "; ").append(gi.name).append(gi.loaded == 0 ? ": nothing loaded" : ": " + gi.loaded + " item(s) loaded from its last formation");
        if (order.isEmpty()) { step("Select group name", "Select a Group Name", "A group is selected", "No group could be selected — " + sf.problems, "FAIL"); addSummary("Result", "FAILED — no group"); return; }

        String search = System.getProperty("setformation.search", "pana");
        String preferredItem = System.getProperty("setformation.item", "Panadeine");
        List<String> terms = new ArrayList<>(List.of(search));
        for (String t : new String[]{"para", "ibu", "amox", "vita"}) if (!terms.contains(t)) terms.add(t);
        StringBuilder notes = new StringBuilder(), refusals = new StringBuilder();
        List<String> suggestionsSeen = new ArrayList<>();
        boolean everAdded = false, ok = false;
        String toast = "", firstGroup = order.get(0).name;
        int groupsTried = 0;
        for (GroupInfo gi : order) {
            if (groupsTried++ > 0 && !sf.restartForm(storeWanted)) { notes.append(notes.length() == 0 ? "" : "; ").append("could not restart the form: ").append(sf.problems); break; }
            String group = sf.selectGroup(gi.name);
            if (group.isEmpty()) { notes.append(notes.length() == 0 ? "" : "; ").append("group ").append(gi.name).append(" could not be selected"); continue; }
            boolean added = false;
            // every formation is reloaded into the next one, so the items found by "pana" are used up after a few runs: the written search first, then other common starts (as the steps allow:
            // "can try other item name")
            for (String term : terms) {
                for (int attempt = 0; attempt < 6 && !added; attempt++) {
                    List<String> sug = sf.typeItem(term);
                    if (sug.isEmpty()) { notes.append(notes.length() == 0 ? "" : "; ").append(group).append(": no suggestion for \"").append(term).append("\""); break; }
                    if (suggestionsSeen.isEmpty() && term.equals(search)) suggestionsSeen.addAll(sug);
                    List<Integer> idx = new ArrayList<>();                          // the preferred item first, then the others in the order listed
                    for (int i = 0; i < sug.size(); i++) if (term.equals(search) && sug.get(i).contains(preferredItem)) idx.add(i);
                    for (int i = 0; i < sug.size(); i++) if (!idx.contains(i)) idx.add(i);
                    if (attempt >= idx.size()) break;
                    if (!sf.pickSuggestion(idx.get(attempt))) { notes.append(notes.length() == 0 ? "" : "; ").append(group).append(" / ").append(sf.lastItem).append(": ").append(sf.problems); continue; }
                    if (!sf.selectBatchRow()) { notes.append(notes.length() == 0 ? "" : "; ").append(group).append(" / ").append(sf.lastItem).append(": ").append(sf.problems); continue; }
                    if (sf.clickAddItem()) added = true;
                    else notes.append(notes.length() == 0 ? "" : "; ").append(group).append(" / ").append(sf.lastItem).append(": ").append(sf.problems);
                }
                if (added) break;
            }
            if (!added) continue;
            everAdded = true;
            toast = sf.saveAndGetToast();
            ok = sf.ok() && sf.saveSucceeded();
            if (ok) break;
            refusals.append(refusals.length() == 0 ? "" : "; ").append("group \"").append(group).append("\" with \"").append(sf.lastItem).append("\" (batch ").append(sf.lastBatch).append(") was refused at Save: \"")
                    .append(toast.isEmpty() ? sf.saveDiagnosis() : toast).append("\" (nothing was written)");
        }

        step("Select group name", "Select a Group Name", "A group is selected",
                "Group \"" + sf.lastGroup + "\" used (scan of the groups: " + scanText + "; the ones that load the fewest items are tried first)"
                        + (sf.lastGroup.equals(firstGroup) ? "" : "; the first choice \"" + firstGroup + "\" was refused"), "PASS");
        step("Enter item name", "In Add Set Details type \"" + search + "\" in Item Name", "The matching items are suggested",
                suggestionsSeen.isEmpty() ? "No suggestion appeared for \"" + search + "\"" : suggestionsSeen.size() + " suggestion(s): " + suggestionsSeen, suggestionsSeen.isEmpty() ? "FAIL" : "PASS");
        if (suggestionsSeen.isEmpty()) { addSummary("Result", "FAILED — no item suggestion for \"" + search + "\""); return; }
        if (!everAdded) {
            step("Click on the option for item name", "Click an option, pick a batch row, click Add", "The item joins the set", "No item could be added — " + notes, "FAIL");
            addSummary("Result", "FAILED — nothing could be added: " + notes);
            return;
        }
        step("Click on the option for item name", "Click the option for the item name", "The Batch List popup opens",
                "Clicked \"" + sf.lastItem + "\"; the Batch List popup opened" + (notes.length() == 0 ? "" : " (refused or skipped on the way: " + notes + ")"), "PASS");
        step("Select a batch row in the Batch List popup", "In the popup click one row of data for the batch", "The row is selected and the item details are filled",
                "Clicked the batch " + sf.lastBatch + " (expiry " + sf.lastBatchExpiry + ", available " + sf.lastBatchAvailable + ", purchase cost " + sf.lastBatchCost + "); details now: " + sf.lastDetails, "PASS");
        step("Click Add", "Click Add", "The item is added to the List of Set Information", "Add put this row into the List of Set Information: " + sf.lastAddedRow, "PASS");
        step("Click Save", "Click Save", "The set is saved",
                sf.problems.isEmpty() ? "Save clicked" + (sf.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + sf.lastDialog + "\"") + (refusals.length() == 0 ? "" : "; earlier: " + refusals) : "Problem — " + sf.problems,
                sf.problems.isEmpty() ? "PASS" : "FAIL");

        String listed = "";
        boolean back = false;
        if (ok) {
            back = sf.returnToList();
            List<String[]> after = back ? sf.searchList() : List.of();
            List<String> beforeIds = before.stream().map(r -> r[0]).toList();
            for (String[] r : after) if (r[1].equalsIgnoreCase(sf.lastGroup) && !beforeIds.contains(r[0])) listed = r[1] + " (id " + r[0] + ", code " + r[2] + ", qty " + r[3] + ")";
            if (listed.isEmpty()) for (String[] r : after) if (r[1].equalsIgnoreCase(sf.lastGroup)) listed = r[1] + " (id " + r[0] + ", code " + r[2] + ", qty " + r[3] + ") - already listed before, so it may be an earlier formation";
        }
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (sf.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + sf.lastSaveMessage + "\"")
                        + (back ? "; back on the list" : "; the list was not reached") + (listed.isEmpty() ? "; no set of the group \"" + sf.lastGroup + "\" was found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + sf.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + sf.saveDiagnosis() + (refusals.length() == 0 ? "" : " — every group tried was refused: " + refusals),
                ok ? "PASS" : "FAIL");

        addSummary("Store / Group", sf.lastStore + " / " + sf.lastGroup + " (code " + sf.lastGroupCode + ")");
        addSummary("Item / Batch", sf.lastItem + " / " + sf.lastBatch + " (expiry " + sf.lastBatchExpiry + ", available " + sf.lastBatchAvailable + ")");
        addSummary("Group scan", scanText.toString());
        if (refusals.length() > 0) addSummary("Save refusals (nothing written)", refusals.toString());
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
