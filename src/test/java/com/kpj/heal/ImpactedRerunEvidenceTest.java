package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of the dependent-test re-run after an approved patch, using a fake launcher (no Maven, browser or DevHIS). Only
 * dependents that DECLARE themselves read-only are launched, and a dependent counts as healthy only on POSITIVE evidence: a clean
 * exit AND a fresh result with no failing step. A non-zero exit, a launch failure or a run that wrote nothing counts as FAILED, which
 * rolls the patch back.
 */
class ImpactedRerunEvidenceTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private enum Dep { PASSES, FAILS, EXIT_NONZERO, WRITES_NOTHING, CANNOT_LAUNCH }

    private SelfHealingAgent.MavenLauncher realLauncher;
    private final List<String> calls = new ArrayList<>();
    private Path source, out, pendingFile;
    private Dep depBehaviour = Dep.PASSES;
    private boolean targetPasses = true;

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");

        Path deps = tmp.resolve("tests/com/kpj/tests/Syn");
        Files.createDirectories(deps);
        dependent(deps, "DepDeclaredReadOnlyTest", "DEP_RO", "/**\n * Read-only: lists and views, saves nothing.\n */\n", "");
        dependent(deps, "DepWritesDataTest", "DEP_WR", "/** Registers a patient. */\n", "");
        dependent(deps, "DepMentionsOnlyTest", "DEP_MENTION", "/** Saves a record. */\n", "  // afterwards the form is read-only\n");
        dependent(deps, "DepMakesAChangeTest", "DEP_CHANGE", "/**\n * Saves it. The run makes a real change; one extra, read-only check follows.\n */\n", "");
        ImpactAnalyzer.testRootOverride = tmp.resolve("tests");

        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(out.resolve("aggregate-data").resolve("SYN.tsv"), row(TEST_ID, "FAIL", STEP, ACTUAL));
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", ACTUAL);
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        ApplyTestSupport.uninstallLocatorFixture();
    }

    private static void dependent(Path dir, String name, String id, String doc, String extra) throws IOException {
        Files.writeString(dir.resolve(name + ".java"), "package com.kpj.tests.Syn;\n" + doc + "public class " + name + " {\n"
                + "    public " + name + "() { super(\"" + id + "\"); }\n    void body() { SynPage p = new SynPage(page); }\n" + extra + "}\n");
    }

    private static String row(String testId, String status, String step, String actual) {
        return String.join("\t", status, "Synthetic page", testId, step, "element found", actual, "") + "\n";
    }

    private void apply() throws Exception {
        PendingHeal p = new PendingHeal();
        p.outDir = out.toString();
        p.testId = TEST_ID;
        p.testClass = ApplyTestSupport.TEST_FQCN;
        p.simpleName = "SynTest";
        p.step = STEP;
        p.affectedFile = source.toString();
        p.affectedMethod = "probe()";
        p.proposalSource = "DETERMINISTIC";
        p.proposalKind = "LOCATOR";
        p.oldSnippet = OLD;
        p.newSnippet = NEW;
        p.reason = "closest live ng-model is staffScheduleInfo.Locationid";
        p.confidence = 0.75;
        p.risk = "LOW";
        p.rootCause = "Stale locator literal";
        p.semanticValidation = "PASSED";
        p.groupKey = "g";
        p.originalFailingSteps = new ArrayList<>(List.of(STEP));
        p.attemptNumber = 1;
        p.attemptsMax = 3;
        pendingFile = out.resolve("self-healing").resolve("pending").resolve("SYN_TEST__Probe.properties");
        ApplyTestSupport.seal(p, out, source);
        p.save(pendingFile);

        SelfHealingAgent.mavenLauncher = args -> {
            calls.add(String.join(" ", args));
            for (String a : args) {
                if (a.equals("-Dtest=com.kpj.tests.Syn.SynTest")) {   // the healed test itself passes after the patch
                    Files.writeString(out.resolve("aggregate-data").resolve("SYN.tsv"),
                            targetPasses ? row(TEST_ID, "PASS", STEP, "element found") : row(TEST_ID, "FAIL", STEP, ACTUAL));
                }
                if (a.equals("-Dtest=com.kpj.tests.Syn.DepDeclaredReadOnlyTest")) {
                    switch (depBehaviour) {
                        case CANNOT_LAUNCH: throw new IOException("synthetic: mvn could not be started");
                        case WRITES_NOTHING: return 0;
                        case EXIT_NONZERO:
                            Files.writeString(out.resolve("aggregate-data").resolve("DEP_RO.tsv"), row("DEP_RO", "PASS", "Dependent step", "ok"));
                            return 1;
                        case FAILS:
                            Files.writeString(out.resolve("aggregate-data").resolve("DEP_RO.tsv"), row("DEP_RO", "FAIL", "Dependent step", "broke"));
                            return 0;
                        default:
                            Files.writeString(out.resolve("aggregate-data").resolve("DEP_RO.tsv"), row("DEP_RO", "PASS", "Dependent step", "ok"));
                    }
                }
            }
            return 0;
        };
        ApplyTestSupport.apply(pendingFile, "approved");
    }

    private Map<String, String> lastAuditRow() throws IOException {
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1);
        String[] c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private void assertRolledBack(String reasonFragment) throws Exception {
        Map<String, String> r = lastAuditRow();
        assertEquals("REVERTED", r.get("finalStatus"));
        assertTrue(r.get("reason").contains(reasonFragment), r.get("reason"));
        assertEquals(ORIGINAL, Files.readString(source), "the patch was rolled back exactly");
        assertEquals("true", r.get("restorationVerified"));
    }

    // ================================================================== which dependents are launched

    @Test
    void onlyTheDeclaredReadOnlyDependent_isLaunched() throws Exception {
        apply();
        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test", "-q -Dtest=com.kpj.tests.Syn.DepDeclaredReadOnlyTest test"), calls,
                "compile, re-run of the healed test, then ONLY the declared read-only dependent");
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
        for (String c : calls) assertFalse(c.contains("AppointmentList") || c.contains("DepWritesData") || c.contains("DepMentionsOnly") || c.contains("DepMakesAChange"), c);
    }

    @Test
    void noDependentIsLaunched_whenTheHealedTestItselfStillFails() throws Exception {
        targetPasses = false;   // the healed step still fails: nothing else may be run against the application
        apply();
        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynTest test"), calls);
        assertRolledBack("target step still fails");
    }

    // ================================================================== positive evidence for a dependent

    @Test
    void aDependentThatPassesWithAFreshResult_keepsThePatch() throws Exception {
        depBehaviour = Dep.PASSES;
        apply();
        assertEquals("KEPT", lastAuditRow().get("finalStatus"));
    }

    @Test
    void aDependentThatFails_rollsThePatchBack() throws Exception {
        depBehaviour = Dep.FAILS;
        apply();
        assertRolledBack("impacted regression test(s) failed");
    }

    @Test
    void aDependentWithANonZeroExit_countsAsFailed() throws Exception {
        depBehaviour = Dep.EXIT_NONZERO;   // even though its result file says PASS
        apply();
        assertRolledBack("impacted regression test(s) failed");
    }

    @Test
    void aDependentThatWritesNoResult_hasNoEvidence_andCountsAsFailed() throws Exception {
        depBehaviour = Dep.WRITES_NOTHING;
        apply();
        assertRolledBack("impacted regression test(s) failed");
        assertTrue(lastAuditRow().get("regressionResult").contains("DepDeclaredReadOnlyTest"));
    }

    @Test
    void aDependentThatCannotBeLaunched_countsAsFailed_notAsSkipped() throws Exception {
        depBehaviour = Dep.CANNOT_LAUNCH;
        apply();
        assertRolledBack("impacted regression test(s) failed");
    }
}
