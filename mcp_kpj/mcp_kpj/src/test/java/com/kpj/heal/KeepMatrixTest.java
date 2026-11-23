package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "never KEEP on weak evidence" matrix. Each scenario is a way a re-run could LOOK fine without being proof that the fix works - a
 * clean exit with nothing behind it, results written to the wrong folder, an old PASS lying around, a leftover failure in another result
 * file, a duplicated step, a new failure elsewhere. All must roll the patch back, with the restoration verified; the control scenario
 * (a clean exit AND a fresh PASS and nothing failing) is the only one that keeps it. Temp files and a fake launcher only.
 */
class KeepMatrixTest {

    @TempDir
    Path tmp;

    private static final String TEST_ID = "SYN_TEST";
    private static final String STEP = "Probe Location selector";
    private static final String OLD = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.LocationId'); return !!e; }\");";
    private static final String NEW = "                + \".find(x=>x.getAttribute('ng-model')==='staffScheduleInfo.Locationid'); return !!e; }\");";
    private static final String ORIGINAL = "class SynPage {\r\n    public boolean probe() {\r\n" + OLD + "\r\n    }\r\n}\r\n";
    private static final String ACTUAL = "FAILED: no live <select> has ng-model='staffScheduleInfo.LocationId'";

    private interface Rerun { int run(Path out, Path elsewhere) throws IOException; }

    private SelfHealingAgent.MavenLauncher realLauncher;
    private Path source, out, elsewhere, pendingFile;
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        source = tmp.resolve("src").resolve("SynPage.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ORIGINAL.getBytes(StandardCharsets.UTF_8));
        out = tmp.resolve("out");
        elsewhere = tmp.resolve("elsewhere");
        Files.createDirectories(tmp.resolve("no-dependents"));
        ImpactAnalyzer.testRootOverride = tmp.resolve("no-dependents");
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(tsv(out, "SYN.tsv"), row("PASS", "Login", "ok") + row("FAIL", STEP, ACTUAL));   // the original failing run
        ApplyTestSupport.installLocatorFixture(tmp, TEST_ID, STEP);
        ApplyTestSupport.writeFailureSidecar(out, TEST_ID, STEP, "element found", ACTUAL);
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        ImpactAnalyzer.testRootOverride = null;
        ApplyTestSupport.uninstallLocatorFixture();
    }

    private static Path tsv(Path dir, String name) { return dir.resolve("aggregate-data").resolve(name); }

    private static String row(String status, String step, String actual) {
        return String.join("\t", status, "Synthetic page", TEST_ID, step, "element found", actual, "") + "\n";
    }

    private Map<String, String> apply(Rerun rerun) throws Exception {
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
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) return rerun.run(out, elsewhere);
            return 0;   // compile succeeds
        };
        ApplyTestSupport.apply(pendingFile, "approved");
        List<String> lines = Files.readAllLines(out.resolve("failures").resolve("healing-history.tsv"));
        String[] header = lines.get(0).split("\t", -1), c = lines.get(lines.size() - 1).split("\t", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length; i++) m.put(header[i], i < c.length ? c[i] : "");
        return m;
    }

    private void assertRolledBack(Map<String, String> r, String reasonFragment) throws IOException {
        assertEquals("REVERTED", r.get("finalStatus"), r.get("reason"));
        assertTrue(r.get("reason").contains(reasonFragment), "reason should mention \"" + reasonFragment + "\": " + r.get("reason"));
        assertEquals("true", r.get("restorationVerified"));
        assertEquals(ORIGINAL, Files.readString(source), "the source is exactly the original again");
        assertFalse(Files.exists(out.resolve("failure-analysis").resolve("resolution-tracking.tsv")), "nothing is recorded as resolved");
    }

    @Test
    void control_aCleanExitAndAFreshPassAndNothingFailing_isTheOnlyWayToKeep() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found"));
            return 0;
        });
        assertEquals("KEPT", r.get("finalStatus"));
        assertTrue(Files.readString(source).contains(NEW));
    }

    @Test
    void theRerunWroteToAnotherFolder_andOnlyAnOldPassIsLeftHere_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.createDirectories(e.resolve("aggregate-data"));
            Files.writeString(tsv(e, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found"));   // the real pass went elsewhere
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found"));   // an old pass is "here"...
            Files.setLastModifiedTime(tsv(o, "SYN.tsv"), FileTime.from(Instant.now().minusSeconds(3600)));          // ...but it is not from this run
            return 0;
        });
        assertRolledBack(r, "no new test result");
    }

    @Test
    void aLeftoverFailureInAnotherResultFile_isNotKept_evenWithAFreshPassBesideIt() throws Exception {
        Files.writeString(tsv(out, "OTHER.tsv"), row("FAIL", STEP, ACTUAL));   // another result file still records this step as failing
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found"));
            return 0;
        });
        assertRolledBack(r, "target step still fails");
    }

    @Test
    void aStepRecordedBothPassAndFail_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found") + row("FAIL", STEP, ACTUAL));
            return 0;
        });
        assertRolledBack(r, "target step still fails");
    }

    @Test
    void aNewFailureElsewhereInTheSameRun_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found") + row("FAIL", "A later step", "broke"));
            return 0;
        });
        assertRolledBack(r, "downstream regression");
    }

    @Test
    void aPassRowButAMavenFailureExit_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", STEP, "element found"));
            return 1;
        });
        assertRolledBack(r, "exit code was 1");
    }

    @Test
    void aCleanExitWithNoResultAtAll_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> { Files.delete(tsv(o, "SYN.tsv")); return 0; });
        assertRolledBack(r, "no new test result");
    }

    @Test
    void aCleanExitThatRanADifferentStepOnly_isNotKept() throws Exception {
        Map<String, String> r = apply((o, e) -> {
            Files.writeString(tsv(o, "SYN.tsv"), row("PASS", "Login", "ok") + row("PASS", "Some other step", "element found"));
            return 0;
        });
        assertRolledBack(r, "no PASS result for step");
    }
}
