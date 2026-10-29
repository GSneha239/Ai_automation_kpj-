package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.kpj.heal.PostRunTestSupport.capture;
import static com.kpj.heal.PostRunTestSupport.files;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of the script-vs-application cross-check. The fixtures are COPIES of the real thing that triggered it: the exact
 * Page Object method that failed ("Print Barcode") and the exact buttons the live page showed when it failed (the footer offers "Print", not
 * "Print Barcode"). No browser, no DevHIS: everything is text in, verdict out.
 */
class ScriptVsDomDiagnosticsTest {

    @TempDir
    Path tmp;

    /** The real clickPrintBarcode() text, as it appears in the Page Object source (regex escapes doubled inside the Java string). */
    private static final String PRINT_BARCODE_METHOD =
            "public boolean clickPrintBarcode() {\n"
            + "    Object tagged = page.evaluate(\"() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('printpatient')>=0 && x.offsetParent!==null)\"\n"
            + "            + \" || [...document.querySelectorAll('button,a')].find(x=>/^\\\\s*print barcode\\\\s*$/i.test((x.textContent||'').replace(/\\\\s+/g,' ').trim()) && x.offsetParent!==null);\"\n"
            + "            + \" if(!b) return false; b.id='__printBarcodeBtn'; return true; }\");\n"
            + "    if (!Boolean.TRUE.equals(tagged)) { System.out.println(\"clickPrintBarcode: 'Print Barcode' button not found\"); return false; }\n"
            + "    return true;\n}\n";

    /** The footer buttons the live page really showed when the step failed (copied from the captured failure file). */
    private static final String LIVE_BUTTONS = "Search|Change Admission Type|Expected Discharge Date|Cancel Admission|Close Admission|Revoke Admission|Referred Patients"
            + "|Ward Acceptance|Plan Discharge|Discharge|Company Approved Amount|Change Refer Entity|View Details|Patient Record History|Consent/Forms"
            + "|View Consent/Form|Assign Triage|Assign Bed|Fall Risk Assessment|Attach Signature|Print|Convert Opd Charges|Convert Opd Prescription"
            + "|Patient Task|Bed View|UnReservation|Export To Excel|Admission Checklist|Patient History|Request MRD File|Return MRD File|Patient Status"
            + "|Send Deposit Request SMS|Print Deposit Request";

    private static FailureRecord failure(String buttons, String ngClicks, String actual) {
        FailureRecord f = new FailureRecord("TC23_X", "page", "Print Barcode · Open popup", "The Barcode popup opens", actual);
        f.url = "https://synthetic.invalid/#/AdmissionList";
        f.buttons = buttons;
        f.ngClicks = ngClicks;
        f.headings = "Home|OP|IP";
        return f;
    }

    // ================================================================== the real case

    @Test
    void theRealPrintBarcodeFailure_isAScriptThatIsOutOfDate_notAnApplicationFailure() {
        ScriptVsDomDiagnostics.Finding f = ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD,
                failure(LIVE_BUTTONS, "openMic()|fnToggleBedOccupancyPanel();", "Popup did NOT open"));
        assertNotNull(f);
        assertEquals(ScriptVsDomDiagnostics.Verdict.SCRIPT_STALE_LIKELY, f.verdict);
        assertTrue(f.headline.contains("\"print barcode\"") && f.headline.contains("\"Print\""), f.headline);
        String all = String.join("\n", f.lines);
        assertTrue(all.contains("looks for: button/link label \"print barcode\"") && all.contains("click handler \"printpatient\""), "what the script looks for:\n" + all);
        assertTrue(all.contains("showed 34 button/link control(s)"), "what the page showed:\n" + all);
        assertTrue(all.contains("CLOSEST live control to \"print barcode\": \"Print\""), "the right near-match was chosen, not \"Print Deposit Request\":\n" + all);
        assertTrue(all.contains("NOT SEEN: the click handler \"printpatient\""), all);
        assertTrue(all.contains("not proof that it is the same function"), "the caveat is stated:\n" + all);
        assertTrue(f.suggestedAction.contains("a person must confirm"), f.suggestedAction);
    }

    // ================================================================== the other verdicts

    @Test
    void whenTheLabelTheScriptWantsIsOnThePage_theLocatorIsFine_andTheFailureCameAfter() {
        ScriptVsDomDiagnostics.Finding f = ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD,
                failure("Search|Print Barcode|Export To Excel", "", "Popup did NOT open"));
        assertEquals(ScriptVsDomDiagnostics.Verdict.SCRIPT_LOCATOR_OK, f.verdict);
        assertTrue(f.headline.contains("the failure came after"), f.headline);
        assertTrue(String.join("\n", f.lines).contains("MATCH: the label \"print barcode\" IS on the page."));
    }

    @Test
    void whenTheClickHandlerTheScriptWantsIsOnThePage_theLocatorIsFine_evenIfTheLabelChanged() {
        ScriptVsDomDiagnostics.Finding f = ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD,
                failure("Search|Print", "openMic()|printpatient()", "Popup did NOT open"));
        assertEquals(ScriptVsDomDiagnostics.Verdict.SCRIPT_LOCATOR_OK, f.verdict);
        assertTrue(String.join("\n", f.lines).contains("MATCH: the click handler \"printpatient\" IS on the page."));
    }

    @Test
    void aControlThatAppearsOnlyInTheSettledSnapshot_isATimingMatter_notAMissingControl() {
        FailureRecord f = failure("Search|Export To Excel", "", "Popup did NOT open");
        f.settledButtons = "Search|Print Barcode|Export To Excel";
        assertEquals(ScriptVsDomDiagnostics.Verdict.SCRIPT_LOCATOR_OK, ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD, f).verdict);
    }

    @Test
    void whenNothingSimilarIsOnThePage_itIsTheApplicationOrTheState_notTheScript() {
        ScriptVsDomDiagnostics.Finding f = ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD,
                failure("Search|Export To Excel|Bed View|Patient Status", "openMic()", "Popup did NOT open"));
        assertEquals(ScriptVsDomDiagnostics.Verdict.APPLICATION_OR_STATE_LIKELY, f.verdict);
        assertTrue(f.headline.contains("removed by the application") && f.headline.contains("permission"), f.headline);
        assertTrue(f.suggestedAction.contains("report it to development"), f.suggestedAction);
    }

    @Test
    void aFullClickCapture_isNeverTakenAsProofThatAHandlerIsAbsent() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < ScriptVsDomDiagnostics.CLICK_CAPTURE_LIMIT; i++) many.append(i == 0 ? "" : "|").append("setDay(").append(i).append(")");
        ScriptVsDomDiagnostics.Finding f = ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD, failure(LIVE_BUTTONS, many.toString(), "x"));
        assertTrue(String.join("\n", f.lines).contains("the capture is full (limit 60), so this alone does not prove it is absent"));
    }

    // ================================================================== when it must say nothing

    @Test
    void withNoCapturedControls_or_NoTargetsInTheScript_itSaysNothing() {
        assertNull(ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD, failure("", "", "x")), "no controls captured: nothing to compare");
        assertNull(ScriptVsDomDiagnostics.diagnose("public void wait() { page.waitForTimeout(500); }", failure(LIVE_BUTTONS, "", "x")), "no label or handler in the script");
        assertNull(ScriptVsDomDiagnostics.diagnose(null, failure(LIVE_BUTTONS, "", "x")));
        assertNull(ScriptVsDomDiagnostics.diagnose(PRINT_BARCODE_METHOD, null));
    }

    @Test
    void theFindingCarriesNoPatch_soNothingInItCouldEverBeApplied() {
        List<String> fields = new ArrayList<>();
        for (var fld : ScriptVsDomDiagnostics.Finding.class.getDeclaredFields()) fields.add(fld.getName());
        assertEquals(List.of("verdict", "headline", "lines", "suggestedAction"), fields, "no old/new snippet, no file, no edit");
    }

    // ================================================================== inside the classifier (precedence preserved)

    private FailureCategoryClassifier.Result classify(String actual, String buttons) {
        CodeLocator.CodeLocation loc = new CodeLocator.CodeLocation(null, Paths.get("SynPage.java"), "occ", "clickPrintBarcode", PRINT_BARCODE_METHOD, "");
        return new FailureCategoryClassifier().classify(failure(buttons, "openMic()", actual), loc);
    }

    @Test
    void theClassifierReportsTheVerdict_asAScriptIssue_neverEligible_andNeverProposesAnything() {
        FailureCategoryClassifier.Result r = classify("The popup did not open - click had no effect", LIVE_BUTTONS);
        assertEquals(FailureCategory.AUTOMATION_SCRIPT_ISSUE, r.category);
        assertEquals("Automation Tester", r.recommendedOwner);
        assertTrue(r.rootCause.startsWith("VERDICT: SCRIPT LIKELY OUT OF DATE"), r.rootCause);
        assertFalse(r.selfHealingEligible, "a stale-label verdict is never auto-healed: a person must confirm the replacement control");
        assertTrue(r.eligibilityReason.contains("a person must confirm"), r.eligibilityReason);
        assertTrue(r.evidenceAvailable.contains("CLOSEST live control"), "the evidence lines travel with the result: " + r.evidenceAvailable);
        assertTrue(r.candidates.isEmpty(), "no candidate patch exists for this failure");
    }

    @Test
    void aMissingControl_isReportedAsAnApplicationOrStateProblem() {
        FailureCategoryClassifier.Result r = classify("The popup did not open - click had no effect", "Search|Export To Excel|Bed View");
        assertEquals(FailureCategory.APPLICATION_BUG, r.category);
        assertTrue(r.rootCause.startsWith("VERDICT: APPLICATION OR STATE LIKELY"), r.rootCause);
        assertFalse(r.selfHealingEligible);
    }

    @Test
    void aPresentControl_isReportedAsScriptLocatorOk_notAsAScriptProblem() {
        FailureCategoryClassifier.Result r = classify("The popup did not open - click had no effect", "Search|Print Barcode");
        assertEquals(FailureCategory.BUTTON_UI_FAILURE, r.category);
        assertTrue(r.rootCause.startsWith("VERDICT: SCRIPT LOCATOR OK"), r.rootCause);
        assertFalse(r.selfHealingEligible);
    }

    @Test
    void everyMoreSpecificRuleStillWinsOverTheNewCheck() {
        // "no ... found" is missing data, "save failed" an application error: they were decided BEFORE this check existed and still are
        assertEquals(FailureCategory.MISSING_TEST_DATA, classify("No patient found for this search", LIVE_BUTTONS).category);
        assertEquals(FailureCategory.APPLICATION_BUG, classify("Save failed - server returned an error", LIVE_BUTTONS).category);
    }

    // ================================================================== end to end through the real agent (synthetic tree, no browser)

    private Path testRoot, out;

    @BeforeEach
    void wire() throws IOException {
        testRoot = tmp.resolve("tests");
        out = tmp.resolve("out");
        Files.createDirectories(out);
        CodeLocator.testRootOverride = testRoot;
        TestClassResolver.rootOverride = testRoot;
        ImpactAnalyzer.testRootOverride = testRoot.resolve("no-dependents");
        Files.createDirectories(testRoot.resolve("no-dependents"));
    }

    @AfterEach
    void unwire() {
        CodeLocator.testRootOverride = null;
        TestClassResolver.rootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
        SelfHealingAgent.categoryClassifierOverride = null;
    }

    private void writeButtonFlow(String liveButtons) throws IOException {
        Path page = testRoot.resolve("src").resolve("SynBtnPage.java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package src;\npublic class SynBtnPage {\n    public SynBtnPage(Object page) { }\n"
                + "    public boolean pick() {\n"
                + "        Object t = page.evaluate(\"() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\\\s*print barcode\\\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); return !!b; }\");\n"
                + "        return Boolean.TRUE.equals(t);\n    }\n}\n");
        Path test = testRoot.resolve("com/kpj/tests/Syn/SynBtnTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.kpj.tests.Syn;\nimport src.SynBtnPage;\npublic class SynBtnTest {\n"
                + "    public SynBtnTest() { super(\"SYN_BTN\"); }\n    protected void body() {\n"
                + "        SynBtnPage p = new SynBtnPage(page);\n        p.pick();\n"
                + "        step(\"Open Barcode popup\", \"desc\", \"The Barcode popup opens\", \"Popup did NOT open\", \"FAIL\");\n    }\n}\n");
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(out.resolve("aggregate-data").resolve("Btn.tsv"),
                "PASS\tSynthetic page\tSYN_BTN\tLogin\tok\tok\t\nFAIL\tSynthetic page\tSYN_BTN\tOpen Barcode popup\tThe Barcode popup opens\tPopup did NOT open\t\n");
        Files.createDirectories(out.resolve("failures"));
        Files.writeString(out.resolve("failures").resolve("SYN_BTN_2.txt"), "testId=SYN_BTN\npage=Synthetic page\nstep=Open Barcode popup\ndesc=d\n"
                + "expected=The Barcode popup opens\nactual=Popup did NOT open\nurl=https://synthetic.invalid/#/AdmissionList\nbuttons=" + liveButtons
                + "\nselects=\nheadings=Home|OP|IP\nngclicks=openMic()\n");
        Instant now = Instant.now();
        for (Path p : files(out)) Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(now));
    }

    @Test
    void throughTheRealAgent_aStaleLabelIsNamedAsAScriptProblem_andTheWorkflowStopsWithNoProposal() throws Exception {
        writeButtonFlow("Search|Print|Export To Excel");
        String log = capture(() -> FailureAnalysisAgent.main(new String[]{"com.kpj.tests.Syn.SynBtnTest", out.toString()}));
        assertTrue(log.contains("Category: Automation Script Issue") && log.contains("Self-Healing Eligible: NO"), log);
        assertTrue(log.contains("VERDICT: SCRIPT LIKELY OUT OF DATE"), "the verdict is printed in the console, not only in the report: " + log);
        List<Path> reports = files(out.resolve("failure-analysis")).stream().filter(p -> p.toString().endsWith(".html")).collect(Collectors.toList());
        assertFalse(reports.isEmpty(), "the report was written");
        String html = reports.stream().map(p -> { try { return Files.readString(p); } catch (IOException e) { return ""; } }).collect(Collectors.joining());
        assertTrue(html.contains("SCRIPT LIKELY OUT OF DATE") && html.contains("CLOSEST live control"), "the verdict and its evidence are in the report");
    }

    @Test
    void throughTheWholeWorkflow_noProposalIsCreated_forAnApplicationOrStateVerdict() throws Exception {
        writeButtonFlow("Search|Export To Excel|Bed View");
        String log = capture(() -> HealingWorkflowOrchestrator.main(new String[]{"com.kpj.tests.Syn.SynBtnTest", out.toString(), "--skip-run"}));
        assertTrue(log.contains("VERDICT: APPLICATION OR STATE LIKELY"), log);
        assertTrue(log.contains("WORKFLOW_COMPLETE: FAILURE_ANALYSIS_ONLY"), log);
        assertFalse(Files.exists(out.resolve("self-healing").resolve("pending")) && !files(out.resolve("self-healing").resolve("pending")).isEmpty(), "no proposal");
    }
}
