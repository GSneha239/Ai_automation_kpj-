package com.kpj.tests.Emergency_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
// Not imported directly: the page object shares its simple name
// (com.kpj.pages.Emergency_page.Emergency_Registration_Unconscious) with this test class.
import com.microsoft.playwright.Page;

/**
 * TC11 - Emergency &gt; <b>Emergency Registration (Unconscious)</b>.
 *
 * <ol>
 *   <li>Click Emergency → Emergency Registration (Unconscious) ({@code #/EmergencyRegistration}).</li>
 *   <li>Select <b>Reg.type</b> (New / Registered).</li>
 *   <li>Fill the mandatory details if empty (Gender is required; Name/Prefix/Doctor filled too).</li>
 *   <li>Save ({@code IUDRegistration()}) → "Registration Saved Successfully &amp; MRN is …"; the
 *       registration report opens in another tab (rasterized screenshot).</li>
 *   <li>The Consent Details modal auto-opens (fixed PDPA consent) → Save opens the signable form tab.</li>
 *   <li>Create Document → Submit (no signature) → the consent report generates → screenshot.</li>
 * </ol>
 */
public class Emergency_Registration_Unconscious extends DevHisBase {

    public Emergency_Registration_Unconscious() { super("TC11_EmergencyRegistrationUnconscious"); }

    public static void main(String[] args) {
        Emergency_Registration_Unconscious t = new Emergency_Registration_Unconscious();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Emergency - Emergency Registration (Unconscious)", "Emergency > Emergency Registration (Unconscious)",
                "Register an unconscious emergency patient, then complete the auto-opened PDPA consent form.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // Reg.type — alternate/randomize New vs Registered each run.
        //   New        → creates a brand-new unconscious patient.
        //   Registered → searches an EXISTING patient by MRN for a new ED visit.
        // Pinnable: -Ddevhis.regtype=New (or Registered) forces the path, so a specific one can be re-run on
        // demand instead of waiting for the coin flip to land on it. Unset = the original random behaviour.
        String wanted = System.getProperty("devhis.regtype", "").trim();
        boolean registered = wanted.isEmpty()
                ? new java.util.Random().nextBoolean()
                : wanted.equalsIgnoreCase("Registered");
        if (!wanted.isEmpty()) System.out.println("Reg.type pinned by -Ddevhis.regtype=" + wanted);

        // If Registered, line up a pool of candidate MRNs to try BEFORE opening the registration form itself
        // (a side-trip to Emergency List View after that would leave the form's state stranded). NOT a
        // hardcoded MRN — this shared QA environment's patient data drifts, so any fixed value eventually
        // goes stale (same lesson already learned for TransferBedList's own hardcoded MRN pool). Instead,
        // pull real, currently-valid MRNs live from Emergency List View's own grid.
        java.util.List<String> mrnCandidates = new java.util.ArrayList<>();
        if (registered) {
            com.kpj.pages.Emergency_page.EmergencyListView elv = new com.kpj.pages.Emergency_page.EmergencyListView(page);
            if (elv.navigateViaMenu()) {
                java.time.format.DateTimeFormatter ldf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                java.time.LocalDate ltd = java.time.LocalDate.now();
                String search = elv.searchByDateAndMrn(ltd.minusDays(365).format(ldf), ltd.plusDays(1).format(ldf), null);
                java.util.List<String> live = elv.listMrns(10);
                System.out.println("Emergency_Registration_Unconscious: Emergency List View search -> " + search + " | live MRNs: " + live);
                for (String m : live) if (!mrnCandidates.contains(m)) mrnCandidates.add(m);
            } else {
                System.out.println("Emergency_Registration_Unconscious: could not open Emergency List View for a live MRN pool");
            }
        }

        com.kpj.pages.Emergency_page.Emergency_Registration_Unconscious er =
                new com.kpj.pages.Emergency_page.Emergency_Registration_Unconscious(page);
        er.navigateTo(BASE);
        step("Open Emergency Registration (Unconscious)", "Emergency → Emergency Registration (Unconscious) (#/EmergencyRegistration)",
                "The Emergency Registration form is shown", "Form opened", "PASS");

        String regType = er.selectRegType(registered ? "Registered" : "New");
        String regDetail;
        boolean regStepOk = true;
        if (registered) {
            String loadedName = "";
            String usedMrn = null;
            for (String candidate : mrnCandidates) {
                loadedName = er.searchExistingByMRN(candidate);
                if (!loadedName.isEmpty()) { usedMrn = candidate; break; }
                System.out.println("Emergency_Registration_Unconscious: MRN " + candidate + " not found — trying the next candidate");
            }
            boolean found = usedMrn != null;
            regStepOk = found;
            regDetail = found
                    ? "Reg.type: Registered — searched MRN " + usedMrn + " → loaded: " + loadedName
                            + (mrnCandidates.size() > 1 ? " (tried " + mrnCandidates.indexOf(usedMrn) + " other candidate(s) first)" : "")
                    : "Reg.type: Registered — none of " + mrnCandidates.size() + " candidate MRN(s) resolved: " + mrnCandidates;
        } else {
            regDetail = "Reg.type: New";
        }
        step("Select Reg.type",
                "Select Reg.type = New / Registered (" + (wanted.isEmpty() ? "randomized" : "pinned to " + wanted) + ")",
                "Reg.type selected", regDetail, regStepOk ? "PASS" : "FAIL");

        String filled = er.fillMandatoryIfEmpty();
        step("Fill mandatory details (if empty)", "Fill Gender (required) + Name/Prefix/Doctor if empty",
                "Mandatory fields populated", filled, "PASS");

        int tabsBeforeSave = page.context().pages().size();
        String toast = er.saveRegistrationAndGetToast();
        boolean saved = toast != null && (toast.toLowerCase().contains("saved successfully") || toast.toLowerCase().contains("mrn is"));
        // If Department genuinely has no options to pick (not just a load-timing issue — that is worked
        // around by polling in fillMandatoryIfEmpty()), say so plainly instead of just echoing the
        // downstream "Please Select Department First Then Doctor!" toast, which by itself does not explain
        // that the dropdown was empty.
        boolean deptEmpty = filled != null && filled.contains("Dept=(no-opt)");
        String saveActual = toast == null || toast.isEmpty() ? "No success toast appeared" : toast;
        if (!saved && deptEmpty) {
            saveActual += "  ||  REASON: the Department dropdown (Visit.DepartmentID) had no selectable "
                    + "options at fill time, so Department (and Doctor, which cascades from it) could not "
                    + "be set — that is why Save rejects the registration.";
        }
        step(page, "Save registration", "Click Save (IUDRegistration); wait for the toast",
                "'Registration Saved Successfully & MRN is …' toast",
                saveActual, saved ? "PASS" : "FAIL");
        if (!saved) return;
        // Extract the MRN for the summary.
        String mrn = toast.replaceAll(".*MRN is\\s*([A-Za-z0-9]+).*", "$1");
        addSummary("Emergency MRN", mrn);

        // Save opens the registration report(s) in new tab(s) — capture ALL of them (Patient Sticker + Patient
        // Label; both are RegistrationReport.aspx PDFs). Wait for them to appear, then rasterize each.
        // Wait for the tabs to stop ARRIVING, not for a fixed 6s. The report tabs open at different delays, and a
        // flat wait silently truncated the set: the New path reported 2 tabs (Sticker, Wrist Band) while the
        // Registered path reported 3 — the Patient Label had simply not opened yet when the snapshot was taken.
        int stable = 0, seen = page.context().pages().size();
        for (int i = 0; i < 20 && stable < 3; i++) {   // up to ~30s, settled once the count holds for ~4.5s
            page.waitForTimeout(1500);
            int now = page.context().pages().size();
            stable = (now == seen) ? stable + 1 : 0;
            seen = now;
        }
        System.out.println("report tabs: settled at " + (seen - tabsBeforeSave) + " new tab(s)");
        java.util.List<Page> reportTabs = new java.util.ArrayList<>();
        java.util.List<Page> allTabs = page.context().pages();
        for (int i = tabsBeforeSave; i < allTabs.size(); i++) reportTabs.add(allTabs.get(i));
        addSummary("Registration report tabs", String.valueOf(reportTabs.size()));
        int rn = 0;
        for (Page rpt : reportTabs) {
            rn++;
            String url = rpt.url();
            String label = url.contains("IsPatientSticker=true") ? "Patient Sticker"
                    : url.contains("IsLabel=true") ? "Patient Label"
                    : url.contains("WristBand") ? "Patient Wrist Band" : "Report " + rn;
            byte[] png = er.captureReportPng(rpt);
            // A tab opening (or even a screenshot capturing) is NOT proof the report rendered — the report
            // server can return a Crystal Reports/ASP.NET "Server Error ... Logon failed" error PAGE instead
            // of the PDF, and captureReportPng's screenshot fallback happily captures that error page as a
            // "successful" PNG (confirmed live on this exact screen — Patient Wrist Band). Same helper already
            // used by IP Admission's report-tab check (DevHisBase.reportPageError).
            String err = reportPageError(rpt);
            if (!err.isEmpty()) {
                step(png, "Registration report " + rn + " · " + label, "Save opens the registration report tab #" + rn,
                        "The registration report is shown", "Report FAILED to render — " + err + "  (" + url + ")", "FAIL");
            } else if (png != null && png.length > 0) {
                step(png, "Registration report " + rn + " · " + label, "Save opens the registration report tab #" + rn,
                        "The registration report is shown", label + " captured (" + url + ")", "PASS");
            } else {
                step("Registration report " + rn + " · " + label, "Save opens the registration report tab #" + rn,
                        "The registration report is shown", label + " tab opened: " + url, "PASS");
            }
        }
        if (reportTabs.isEmpty()) {
            step("Registration report (new tab)", "Save opens the registration report in another tab",
                    "The registration report is shown", "No report tab detected", "MANUAL");
        }
        page.bringToFront();

        // ===== Consent (auto-opens after save, EXCEPT on this exact screen) =====
        // Confirmed live in EmergencypatientRegistrationController.js: "BW-807 | Paraskumar | 17/08/2026"
        // added `if ($state.current.name !== 'EmergencyRegistration') { ...modal('show')... }` around the
        // auto-open call — i.e. the PDPA Consent Details modal is DELIBERATELY suppressed on THIS screen
        // (Emergency Registration Unconscious) for every patient, New or Registered. It still presumably
        // auto-opens on other registration screens not gated by that condition. This is intentional app
        // behavior as of that change, not a defect — SKIPPED either way, not FAIL for New / SKIPPED only for
        // Registered as this used to assume (that split predates BW-807 and no longer matches reality).
        boolean consentOpen = er.waitForConsentModal();
        String consentForm = er.getConsentName();
        if (!consentOpen) {
            step("Consent Details auto-opens (PDPA)", "After save the PDPA Consent Details modal opens (new/un-consented patients)",
                    "Consent modal opens, or is skipped where the app suppresses it",
                    "No consent modal — suppressed on this screen by design (BW-807, 17/08/2026: the app only "
                            + "auto-opens it when the current route is NOT EmergencyRegistration)",
                    "SKIPPED");
            addSummary("Reg.type", regType);
            addSummary("Consent", "Suppressed on this screen by design (BW-807)");
            addSummary("Application URL", BASE + "/#/EmergencyRegistration");
            return;
        }
        step("Consent Details auto-opens (PDPA)", "After save the Consent Details modal opens automatically with the PDPA consent pre-selected",
                "Consent Details modal is shown", "Consent modal opened: " + consentForm, "PASS");

        int tabCountBefore = page.context().pages().size();
        // Synthetic click — the consent modal backdrop (#myModal) intercepts a real Playwright click.
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /consent/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDconsentdetail()' && x.offsetParent!==null); if(b) b.click(); }");
        Page docTab = null;
        for (int i = 0; i < 60; i++) {
            if (page.context().pages().size() > tabCountBefore) { docTab = page.context().pages().get(page.context().pages().size() - 1); break; }
            page.waitForTimeout(1000);
        }
        if (docTab == null) {
            step(page, "Consent Save opens the form tab", "Save (IUDconsentdetail) opens the consent form tab",
                    "New tab opens", "No new tab appeared", "FAIL");
            return;
        }
        docTab.waitForLoadState();
        docTab.waitForTimeout(2000);
        step(docTab, "Consent Save opens the form tab", "Save (IUDconsentdetail) opens the consent form tab",
                "New tab opens", "New tab: " + consentForm + " (" + docTab.url() + ")", "PASS");

        docTab.locator("a:has-text('Create Document')").click(
                new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true));
        docTab.waitForSelector("#Submit", new Page.WaitForSelectorOptions().setTimeout(45000));
        docTab.waitForTimeout(2000);
        step(docTab, "Create Document (auto-filled)", "Click Create Document; the form auto-fills",
                "Navigates to Create; details prefilled", "Create form auto-filled", "PASS");

        // Directly Submit (no signature).
        docTab.locator("#Submit").click();
        page.waitForTimeout(800);
        docTab.locator("#btnConfirmYes").click(new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true));
        docTab.waitForTimeout(2500);
        try {
            docTab.waitForURL("**/Edit/**",
                    new Page.WaitForURLOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(45000));
        } catch (Exception ignore) { }
        docTab.waitForTimeout(1500);
        String docId = docTab.url().replaceAll(".*/Edit/(\\d+).*", "$1");
        boolean ok = docTab.url().contains("/Edit/") && docId.matches("\\d+");
        // Dismiss the "Form Submitted Successfully!" dialog FIRST so the report shows cleanly, then let the
        // generated consent report render before taking its screenshot.
        try {
            docTab.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("OK")).click();
        } catch (Exception ignore) { }
        docTab.bringToFront();
        try { docTab.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignore) { }
        docTab.waitForTimeout(3000); // let the submitted consent report fully render
        // Full-page ("long") screenshot so the entire consent report is captured, not just the viewport.
        byte[] reportPng = null;
        try { reportPng = docTab.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("consent report full-page screenshot failed: " + e.getMessage()); }
        String reportActual = ok ? "Consent report captured (doc ID " + docId + ")" : "NOT confirmed (url=" + docTab.url() + ")";
        if (reportPng != null && reportPng.length > 0) {
            step(reportPng, "Consent report (full-page screenshot after submit)",
                    "After Submit, capture a full-page screenshot of the generated consent report",
                    "The full submitted consent report (/Edit/<id>) is shown", reportActual, ok ? "PASS" : "FAIL");
        } else {
            step(docTab, "Consent report (screenshot after submit)", "After Submit, screenshot the generated consent report",
                    "The submitted consent report (/Edit/<id>) is shown", reportActual, ok ? "PASS" : "FAIL");
        }
        page.bringToFront();

        addSummary("Reg.type", regType);
        addSummary("Consent form", consentForm);
        addSummary("Consent document", ok ? "Submitted (ID " + docId + ")" : "Not confirmed");
        addSummary("Application URL", BASE + "/#/EmergencyRegistration");
    }
}
