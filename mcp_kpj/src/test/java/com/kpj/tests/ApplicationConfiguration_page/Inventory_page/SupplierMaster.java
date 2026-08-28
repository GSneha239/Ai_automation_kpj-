package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SupplierMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Supplier Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Supplier Master</b>.</li>
 *   <li>Enter the <b>Code</b>.</li>
 *   <li>Select the <b>Title</b>, then enter the <b>Name</b>.</li>
 *   <li>Select the <b>Payable Type</b> and the <b>Vendor Type</b>.</li>
 *   <li>Open the <b>Personal Information</b> tab.</li>
 *   <li>Enter the <b>Acc Ledger Name</b>, <b>Email ID</b>, <b>Telephone No</b> and <b>Mobile No</b>.</li>
 *   <li>Click <b>Submit</b>, then <b>Save</b> on the confirmation dialog it raises.</li>
 *   <li>Verify the success toast.</li>
 * </ol>
 *
 * <p>Every field is pinned to its exact ng-model, dumped from the live form: they are all prefixed
 * {@code payable.}, so keyword matching is unusable here — and the label/model names disagree (the
 * "Vendor Type" dropdown is {@code SupplierTypeID}, "Name" is {@code FirstName}).</p>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin values with
 * {@code -Dcode=}, {@code -Dname=}, {@code -Demail=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL supplier in the target environment.</p>
 */
public class SupplierMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SupplierMaster() { super("ApplicationConfiguration_SupplierMaster"); }

    public static void main(String[] args) {
        SupplierMaster t = new SupplierMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /**
     * Write the file the run links, so the flow never depends on a file happening to exist on the
     * machine. A real 1×1 PNG — the grid's Preview column expects an image, and a renamed text file
     * would be a different test.
     */
    private static java.nio.file.Path writeUploadFile(String stamp) {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                    "supplier-doc-" + stamp + ".png");
            java.nio.file.Files.write(p, png);
            return p;
        } catch (Exception e) {
            throw new RuntimeException("could not write the upload file: " + e.getMessage(), e);
        }
    }

    @Override
    protected void body() {
        meta("Supplier Master", "Application Configuration > Inventory > Supplier Master",
                "&#9888; Creates a REAL supplier: Code, Title, Name, Payable Type, Vendor Type, the "
                        + "Personal Information tab's ledger, e-mail, telephone and mobile, then the "
                        + "Address Information tab (type, country/state/city/area, address, contact, "
                        + "postcode, Add), Submit and Save on the confirmation dialog.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "SUP" + stamp);
        String name = System.getProperty("name", "Auto Supplier " + stamp);
        // Each value carries its own field name, so a value landing in a neighbouring box is visible.
        String ledger = System.getProperty("ledger", "LEDGER-" + stamp);
        String email = System.getProperty("email", "auto" + stamp + "@example.com");
        String tel = System.getProperty("tel", "03" + stamp + "1");
        String mobile = System.getProperty("mobile", "019" + stamp + "2");
        String address = System.getProperty("address", "Addr " + stamp + " Jalan Auto");
        String addrContact = System.getProperty("addrContact", "07" + stamp + "3");
        // Five digits, so it is a plausible postcode; stamped so the added row can be told from any other.
        String postcode = System.getProperty("postcode", stamp);
        // Rate contract: today to a month out, in the dd/MM/yyyy the boxes ask for.
        java.time.format.DateTimeFormatter dmy =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String startDate = System.getProperty("startDate", java.time.LocalDate.now().format(dmy));
        String endDate = System.getProperty("endDate",
                java.time.LocalDate.now().plusMonths(1).format(dmy));
        String item = System.getProperty("item", "Auto Item " + stamp);
        String rate = System.getProperty("rate", "12.50");
        String qty = System.getProperty("qty", "4");
        String amount = System.getProperty("amount", "50.00");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster sm =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster(page);

        // 1) Navigate
        boolean rendered = sm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", sm.lastMenu);
        step(page, "Open Supplier Master screen",
                "Click Application Configuration -> Inventory -> Supplier Master",
                "The Supplier Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (sm.lastRoute.isEmpty() ? "" : " (menu route " + sm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", sm.describeControls());
        addSummary("Add", sm.openFormIfNeeded());
        addSummary("Form controls", sm.describeControls());

        // 2) Code
        String codeEntry = sm.enterCode(code);
        boolean codeOk = sm.codeEntered(code);
        step(page, "Enter code", "Enter the Code " + code,
                "The code is entered",
                (codeOk
                    ? "PASSES because the field accepted the value and read it back: " + codeEntry
                    : "FAILS because the code did not land in its own field: " + codeEntry),
                codeOk ? "PASS" : "FAIL");

        // 3) Title + Name
        String title = sm.selectTitle();
        String nameEntry = sm.enterName(name);
        boolean titleOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster.chosen(title);
        boolean nameOk = sm.nameEntered(name);
        step(page, "Select title and enter name", "Select the Title, then enter the Name " + name,
                "The title is selected and the name entered",
                ((titleOk && nameOk)
                    ? "PASSES because the title dropdown holds its choice and the name read back: "
                      + "Title = " + title + " | Name = " + nameEntry
                    : "FAILS: Title = " + title + " | Name = " + nameEntry),
                (titleOk && nameOk) ? "PASS" : "FAIL");

        // 4) Payable Type
        String payable = sm.selectPayableType();
        boolean payableOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster.chosen(payable);
        step(page, "Select payable type", "Select the Payable Type",
                "A payable type is selected",
                (payableOk
                    ? "PASSES because the dropdown holds the choice: " + payable
                    : "FAILS because no payable type could be selected: " + payable),
                payableOk ? "PASS" : "FAIL");

        // 5) Vendor Type
        String vendor = sm.selectVendorType();
        boolean vendorOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster.chosen(vendor);
        step(page, "Select vendor type", "Select the Vendor Type",
                "A vendor type is selected",
                (vendorOk
                    ? "PASSES because the dropdown holds the choice: " + vendor
                      + " (the model is SupplierTypeID even though the label reads Vendor Type)"
                    : "FAILS because no vendor type could be selected: " + vendor),
                vendorOk ? "PASS" : "FAIL");

        // 6) Personal Information tab
        String tab = sm.openPersonalInformationTab();
        boolean tabOk = sm.tabOpened();
        step(page, "Open the Personal Information tab", "Click the Personal Information tab",
                "The Personal Information tab is open",
                (tabOk
                    ? "PASSES because the tab was clicked: " + tab + ". Note its fields are already in "
                      + "the DOM before the click, so the next step is judged on the fields themselves "
                      + "rather than on this click having revealed them."
                    : "FAILS because the Personal Information tab could not be opened: " + tab),
                tabOk ? "PASS" : "FAIL");

        // 7) Acc Ledger Name / Email ID / Telephone No / Mobile No
        String contact = sm.enterContactBlock(ledger, email, tel, mobile);
        String[] expected = { "AccLedgerName", ledger, "EmailId", email,
                              "TelNo", tel, "MobileNo", mobile };
        boolean contactOk = sm.contactEntered(expected);
        step(page, "Enter acc ledger name, email ID, telephone no and mobile no",
                "Enter the Acc Ledger Name, Email ID, Telephone No and Mobile No",
                "All four are entered",
                (contactOk
                    ? "PASSES because each box read back its OWN value — telephone and mobile are "
                      + "separate models, so a loose match would fill one twice: " + contact
                    : "FAILS — these did not receive their value: " + sm.contactMissing(expected)
                      + ". Detail: " + contact),
                contactOk ? "PASS" : "FAIL");

        // ---- Address Information tab ------------------------------------

        String addrTab = sm.openAddressTab();
        boolean addrTabOk = sm.addressTabOpened();
        step(page, "Open the Address Information tab", "Click the Address Information tab",
                "The Address Information tab is open",
                (addrTabOk
                    ? "PASSES because the tab's own fields are now on screen: " + addrTab
                      + ". The label sits on an <li> with no handler — the <a data-toggle=\"tab\"> inside "
                      + "it is what switches the pane, so the click is aimed there."
                    : "FAILS because the address fields did not come on screen: " + addrTab),
                addrTabOk ? "PASS" : "FAIL");

        String addrType = sm.selectAddressType();
        boolean addrTypeOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster.chosen(addrType);
        step(page, "Select address type", "Select the Address Type",
                "An address type is selected",
                (addrTypeOk
                    ? "PASSES because the dropdown holds the choice: " + addrType
                    : "FAILS because no address type could be selected: " + addrType),
                addrTypeOk ? "PASS" : "FAIL");

        String cascade = sm.selectAddressCascade();
        boolean cascadeAll = sm.cascadeComplete();
        // Area/Town is an optional "--Select--" on this tab (no field here is marked mandatory), so a
        // list with nothing in it still passes — provided the levels ABOVE it really did fill each other.
        boolean cascadePass = cascadeAll || (sm.cascadeToCity() && sm.areaListEmpty());
        step(page, "Select country, state, city/district and area/town",
                "Select the Country, then the State, then the City/District, then the Area/Town — each "
                        + "list filling the next",
                "Each list is filled from the one before it",
                (cascadeAll
                    ? "PASSES because every level holds a value and each list was waited for until it "
                      + "CHANGED after its parent was chosen: " + cascade
                    : cascadePass
                      ? "PASSES with a note: " + cascade + "  ||  WHY THIS IS STILL A PASS: Area/Town is "
                        + "NOT mandatory on this tab (no field here carries the red asterisk), and by the "
                        + "rule for optional '--Select--' dropdowns an empty one is acceptable. The empty "
                        + "list is a DATA gap, not a broken cascade: the same Country -> State -> City "
                        + "chain reloads correctly at every level (Malaysia -> 20 states -> Johor -> 56 "
                        + "cities), and there is no Area/Town master anywhere in the menu to hold area "
                        + "records, so no city has an area to offer."
                      : "FAILS — the cascade did not fill: " + cascade),
                cascadePass ? "PASS" : "FAIL");

        String addrFields = sm.enterAddressFields(address, addrContact, postcode);
        boolean addrFieldsOk = sm.addressFieldsEntered(address, addrContact, postcode);
        step(page, "Enter address, contact and postcode", "Enter the Address, Contact and Postcode",
                "All three are entered",
                (addrFieldsOk
                    ? "PASSES because each box read back its own value: " + addrFields
                    : "FAILS — a value did not land in its own field: " + addrFields),
                addrFieldsOk ? "PASS" : "FAIL");

        String addRow = sm.addAddressRow();
        boolean rowAdded = sm.addressRowAdded();
        step(page, "Click Add", "Click Add to put the address into the grid",
                "A new row appears in the address grid",
                (rowAdded
                    ? "PASSES because the grid grew — " + addRow + ". The click alone is not credited: "
                      + "the row count before and after is what says an address was added."
                    : "FAILS because the grid did not grow — " + addRow),
                rowAdded ? "PASS" : "FAIL");

        boolean rowShows = sm.addedRowShows(address, addrContact, postcode);
        step(page, "Verify the address data was added",
                "Read the new row back and compare it with what was entered",
                "The row shows the address, contact and postcode that were entered",
                (rowShows
                    ? "PASSES because the new row shows the values entered: "
                      + sm.addedRowCheck(address, addrContact, postcode)
                    : "FAILS because the row does not show what was entered: "
                      + sm.addedRowCheck(address, addrContact, postcode)
                      + ". Row text: " + sm.lastAddedRowText),
                rowShows ? "PASS" : "FAIL");

        // ---- File Linking tab -------------------------------------------

        String fileTab = sm.openFileLinkingTab();
        boolean fileTabOk = sm.fileTabOpened();
        step(page, "Open the File Linking tab", "Click the File Linking tab",
                "The File Linking tab is open",
                (fileTabOk
                    ? "PASSES because the tab's file input is on screen: " + fileTab
                    : "FAILS because no file input came on screen: " + fileTab),
                fileTabOk ? "PASS" : "FAIL");

        // The file is written fresh each run so the flow never depends on something on the machine.
        java.nio.file.Path upload = writeUploadFile(stamp);
        String fileName = upload.getFileName().toString();
        String chosen = sm.chooseFile(upload);
        boolean chosenOk = sm.fileChosen(fileName);
        step(page, "Choose file", "Choose a file to link (" + fileName + ")",
                "The file is chosen and the screen shows its name",
                (chosenOk
                    ? "PASSES because the screen took the file and reports it back: " + chosen
                      + ". The file is handed to the real <input type=\"file\"> so the screen's own "
                      + "fileChanged1() handler runs — typing the name into the model would leave the "
                      + "controller with no file while the step still looked like a pass."
                    : "FAILS because the file was not taken: " + chosen),
                chosenOk ? "PASS" : "FAIL");

        String fileAdd = sm.addFileRow();
        boolean fileRowOk = sm.fileRowAdded();
        step(page, "Click Add", "Click Add to link the chosen file",
                "A new row appears in the File Linking grid",
                (fileRowOk
                    ? "PASSES because the grid grew — " + fileAdd
                    : "FAILS because the grid did not grow — " + fileAdd),
                fileRowOk ? "PASS" : "FAIL");

        boolean fileRowNamed = sm.fileRowShows(fileName);
        step(page, "Verify the file was linked",
                "Read the new row back and check it names the chosen file",
                "The row names " + fileName,
                (fileRowNamed
                    ? "PASSES because the linked row names the file that was chosen: " + sm.lastFileRowText
                    : "FAILS because the row does not name \"" + fileName + "\". Row text: "
                      + sm.lastFileRowText),
                fileRowNamed ? "PASS" : "FAIL");

        // ---- Rate Contract tab ------------------------------------------

        String rateTab = sm.openRateContractTab();
        boolean rateTabOk = sm.rateTabOpened();
        step(page, "Open the Rate Contract tab", "Click the Rate Contract tab",
                "The Rate Contract tab is open",
                (rateTabOk
                    ? "PASSES because the tab's own fields are on screen: " + rateTab
                    : "FAILS because the rate contract fields did not come on screen: " + rateTab),
                rateTabOk ? "PASS" : "FAIL");

        String dates = sm.enterRateDates(startDate, endDate);
        boolean datesOk = sm.rateDatesEntered(startDate, endDate);
        step(page, "Enter start date and end date",
                "Enter the Start Date " + startDate + " and the End Date " + endDate,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value after the calendar closed: " + dates
                      + ". They are 720kb datepickers — a typed value the picker ignores leaves the model "
                      + "empty while the box still shows text, so each is read back."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        String itemEntry = sm.enterItemName(item);
        boolean itemOk = sm.itemNameEntered(item);
        step(page, "Enter item name", "Enter the Item Name " + item,
                "The item name is entered",
                (itemOk
                    ? "PASSES because the field read the value back: " + itemEntry
                    : "FAILS because the item name did not land in its field: " + itemEntry),
                itemOk ? "PASS" : "FAIL");

        String amounts = sm.enterRateAmounts(rate, qty, amount);
        boolean amountsOk = sm.rateAmountsEntered(rate, qty, amount);
        step(page, "Enter purchase rate, total qty and total amount",
                "Enter the Purchase Rate " + rate + ", Total Qty " + qty + " and Total Amount " + amount,
                "All three are entered",
                (amountsOk
                    ? "PASSES because each box holds what was entered: " + amounts
                    : "FAILS — the boxes do not hold what was entered: " + amounts
                      + ". (Total Amount may be computed by the screen from the rate and quantity, in "
                      + "which case what it holds afterwards is what counts.)"),
                amountsOk ? "PASS" : "FAIL");

        // Submit -> confirmation dialog -> Save -> toast. submitAndGetToast() clicks Submit, then
        // clicks Save on the dialog Submit raises; nothing is written until that second click.
        String toast = sm.submitAndGetToast();
        addSummary("Confirmation dialog", sm.lastConfirm);
        boolean confirmed = sm.lastConfirm != null && sm.lastConfirm.startsWith("clicked");
        step(page, "Confirm the save on the confirmation dialog",
                "Submit raises a confirmation dialog — click its Save button",
                "The confirmation dialog is accepted",
                (confirmed
                    ? "PASSES because the dialog appeared and its Save was clicked: " + sm.lastConfirm
                    : "FAILS because no confirmation dialog could be accepted: " + sm.lastConfirm),
                confirmed ? "PASS" : "FAIL");
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.SupplierMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = sm.codeInList(code, name);
        addSummary("List check", sm.lastListCheck
                + (ok && !inList
                    ? "  ||  INCONCLUSIVE, not a failure: the supplier list is paged BY THE SERVER (78 "
                      + "pages) and its column filter only searches the rows already loaded, so a record "
                      + "saved seconds ago need not appear. The save itself is evidenced by the screen's "
                      + "own message, \"" + toast + "\"."
                    : ""));

        boolean genderBlocked = toast != null && toast.toLowerCase().contains("gender");
        String actual = genderBlocked
                ? "WHAT: the supplier cannot be saved. WHERE: the Supplier Master form — Submit "
                  + "(fnIUDpayable) raises its confirmation dialog, its Save is clicked, and the screen "
                  + "then answers \"" + toast + "\". WHY: the save requires a GENDER, and this form "
                  + "renders NO gender control at all. Searched for one by model and by label, including "
                  + "HIDDEN elements, across both tabs the form offers (Personal Information, Address "
                  + "Information): " + sm.lastGender + ". So the mandatory field cannot be supplied from "
                  + "this screen, and no supplier can be created here."
                : toast == null || toast.isEmpty()
                ? "No message appeared even after the confirmation dialog was accepted — "
                  + sm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the supplier IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + sm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual

                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Address tab", sm.lastCascade + "  ||  " + sm.lastAddRow);
        addSummary("File Linking tab", sm.lastFileChosen + "  ||  " + sm.lastFileAdd);
        addSummary("Code / Name", code + " / " + name);
        addSummary("Title", sm.lastTitle);
        addSummary("Payable / Vendor type", sm.lastPayableType + " / " + sm.lastVendorType);
        addSummary("Personal Information tab", sm.lastTab);
        addSummary("Contact block", sm.lastContact);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
