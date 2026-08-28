package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AssociateSponsor — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Associate Sponsor</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Associate Sponsor</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Pricing Policy</b>, <b>Payor</b>, <b>Associate Sponsor</b>.</li>
 *   <li>Click <b>Submit</b> → success toast. If the toast says the combination already exists, pick a
 *       DIFFERENT Pricing Policy and Submit again (looped over the available Pricing Policy options).</li>
 * </ol>
 */
public class AssociateSponsor extends DevHisBase {

    public AssociateSponsor() { super("ApplicationConfig_Billing_AssociateSponsor"); }

    public static void main(String[] args) {
        AssociateSponsor t = new AssociateSponsor();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Associate Sponsor", "Application Configuration > Billing > Associate Sponsor",
                "Add an Associate Sponsor: Add, select Location + Pricing Policy + Payor + Associate Sponsor, Submit. "
                        + "On 'already exists', retry with a different Pricing Policy.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.AssociateSponsor as =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.AssociateSponsor(page);

        // 1) Navigate
        as.navigateViaMenu();
        boolean onScreen = as.onScreen();
        step(page, "Open Associate Sponsor screen", "Click Application Configuration -> Billing -> Associate Sponsor",
                "The Associate Sponsor screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = as.clickAdd();
        step(page, "Click Add", "Click Add", "The Associate Sponsor add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // How many Pricing Policy options are available (bounds the retry loop).
        int tariffCount = as.tariffOptionCount();
        if (tariffCount <= 0) tariffCount = 1;

        // 3) + 4) Fill cascade and Submit; on "already exists" try the next combination. Uniqueness is on the WHOLE
        // Location/Pricing Policy/Payor/Associate Sponsor combination and this environment already holds most of the
        // first-Payor rows, so walk the Payor and Associate Sponsor axes too — cycling Pricing Policy alone only
        // explores one row of the space and runs out.
        // Loop in CASCADE order (Pricing Policy -> Payor -> Associate Sponsor): the child lists are filtered by their
        // parent, so a Payor ordinal only means something within a given Pricing Policy.
        // Pricing Policy is walked from the MIDDLE of the list (user requirement): the top-of-list policies are
        // already mapped in this environment ("already exists") and the bottom of the list is the PUBLIC BANK
        // block whose Payor list never loads, so the middle is where a free, workable combination actually is.
        // From the midpoint walk towards the top, then fall back to the second half.
        String fill = "", toast = "", submittedFill = "";
        boolean ok = false, exists = false;
        int maxTariffs = Math.min(Math.max(tariffCount, 1), 16), maxPayors = 4, maxAssoc = 4, maxAttempts = 40;
        int mid = Math.max(1, (Math.max(tariffCount, 1) + 1) / 2);
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int t = mid; t >= 1; t--) order.add(t);                          // middle -> top
        for (int t = mid + 1; t <= Math.max(tariffCount, 1); t++) order.add(t); // then middle -> bottom
        if (order.size() > maxTariffs) order = order.subList(0, maxTariffs);
        int used = 0, skippedPolicies = 0;
        outer:
        for (int t : order) {
            payors:
            for (int p = 1; p <= maxPayors; p++) {
                for (int a = 1; a <= maxAssoc; a++) {
                    fill = as.fillCascade(t, p, a);
                    if (fill.contains("PricingPolicy=(exhausted)")) continue outer;  // ordinal past this list — next policy up
                    if (fill.contains("Payor=(exhausted)")) break payors;          // no more payors under this policy
                    if (fill.contains("AssociateSponsor=(exhausted)")) break;      // no more associates under this payor
                    // A Pricing Policy whose Payor list never loads is a dead end — Submit would only produce a
                    // validation message. Move UP to the next Pricing Policy instead of aborting the whole walk.
                    if (fill.contains("Payor=(no-opt)") || fill.contains("PricingPolicy=(no-opt)")) {
                        skippedPolicies++;
                        System.out.println("AssociateSponsor: Pricing Policy \"" + as.lastPricingPolicy
                                + "\" has no Payor options — skipping to the next Pricing Policy");
                        continue outer;
                    }
                    if (fill.contains("AssociateSponsor=(no-opt)")) break;         // no associates under this payor
                    used++;
                    submittedFill = fill;
                    toast = as.submitAndGetToast();
                    String tl = toast == null ? "" : toast.toLowerCase();
                    ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
                    exists = tl.contains("exist") || tl.contains("already");
                    if (ok) break outer;
                    if (!exists && toast != null && !toast.isEmpty()) break outer; // a different (non-retryable) message
                    System.out.println("AssociateSponsor: attempt " + used + " (" + fill + ") "
                            + (exists ? "exists" : "no toast") + " — trying the next combination");
                    if (used >= maxAttempts) break outer;
                }
            }
        }

        // Report what was actually selected on the attempt that got submitted (or the last one tried).
        String shown = submittedFill.isEmpty() ? fill : submittedFill;
        boolean fillOk = shown.contains("Location=") && !shown.contains("=(no") && !shown.contains("=(exhausted)");
        step(page, "Select Location, Pricing Policy, Payor, Associate Sponsor",
                "Select Location, then the cascading Pricing Policy, Payor and Associate Sponsor "
                        + "(Pricing Policy taken from the MIDDLE of the list, walking towards the top on 'already exists')",
                "All four dropdowns are selected",
                shown + (skippedPolicies > 0 ? "  [skipped " + skippedPolicies + " Pricing Policy option(s) with no Payor list]" : ""),
                fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after trying " + used + " Pricing Policy x Payor x Associate combination(s): \"" + toast + "\""
                                        : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast (retry other combinations on 'exists')",
                "Click Submit; if it says the combination already exists, try the next Pricing Policy / Payor / Associate Sponsor combination and Submit again",
                "'... saved successfully.' toast",
                (used > 1 && ok ? "(after " + used + " combination attempts, saved as " + submittedFill + ") " : "") + actual,
                ok ? "PASS" : "FAIL");

        // 5) After a successful Save, confirm the new record appears in the list table (screenshot the grid).
        if (ok) {
            String row = as.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Verify added record in the list table",
                    "Open the Associate Sponsor list; find the row just added (Location / Sponsor / Associate Sponsor / Pricing Policy)",
                    "The newly added Associate Sponsor row is shown in the table",
                    found ? "Found in table: " + row
                          : "Saved (toast confirmed) but the new row was not located in the table — Location=" + as.lastLocation
                                    + ", Sponsor=" + as.lastPayor + ", Associate=" + as.lastAssociate + ", Pricing Policy=" + as.lastPricingPolicy,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Pricing Policy attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
