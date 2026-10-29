package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of {@code HealingWorkflowOrchestrator --all}: it discovers the failing testIds already recorded in the output folder,
 * resolves each to a class, runs the SAME single-test workflow for each (fresh test run -> Failure Analysis -> proposal), and summarises.
 * Every "test run" is a fake launcher writing synthetic results; nothing is applied, and no Maven, browser or DevHIS is involved.
 */
class OrchestratorAllModeTest {

    @TempDir
    Path tmp;

    private SelfHealingAgent.MavenLauncher realLauncher;
    private SynSuite suite;
    private SynSuite.SynTest fixable, unfixable, healthy;
    private Path out;
    private final List<String> mavenCalls = new ArrayList<>();
    private final Map<String, Integer> exitCodes = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        out = tmp.resolve("out");
        suite = new SynSuite(tmp.resolve("proj"), out);
        fixable = suite.add("SynFixableTest", "SYN_FIXABLE", "Pick fixable location", true);       // has a deterministic fix
        unfixable = suite.add("SynUnfixableTest", "SYN_UNFIXABLE", "Pick unfixable location", false); // no evidence -> nothing proposable
        healthy = suite.add("SynHealthyTest", "SYN_HEALTHY", "Pick healthy location", true);       // fails in the OLD results, passes now
        suite.install();
        // the OLD run (an hour old) is what --all discovers; the fake "test run" below is the FRESH one
        for (SynSuite.SynTest t : suite.tests) suite.writeRun(t, false);
        SynSuite.makeOld(out);

        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            for (SynSuite.SynTest t : suite.tests) {
                if (("-Dtest=" + t.fqcn).equals(args[args.length - 2])) {
                    int exit = exitCodes.getOrDefault(t.simple, 0);
                    if (exit == 0) suite.writeRun(t, t == healthy);
                    return exit;
                }
            }
            return 0;
        };
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        SynSuite.uninstall();
    }

    private String all(String... extra) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        List<String> args = new ArrayList<>(List.of("--all", out.toString()));
        args.addAll(Arrays.asList(extra));
        try { HealingWorkflowOrchestrator.main(args.toArray(new String[0])); } finally { System.setOut(old); }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    private List<Path> pending() throws IOException {
        Path dir = out.resolve("self-healing").resolve("pending");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".properties")).collect(Collectors.toList()); }
    }

    private boolean patched(SynSuite.SynTest t) throws IOException { return Files.readString(t.page).contains("Locationid"); }

    @Test
    void all_runsEachFailingTestFresh_andSummarisesEachOutcome_withoutApplyingAnything() throws Exception {
        String log = all();
        // one fresh run per discovered test, each of exactly that class
        List<String> expected = List.of("-q -Dtest=com.kpj.tests.Syn.SynFixableTest test", "-q -Dtest=com.kpj.tests.Syn.SynHealthyTest test", "-q -Dtest=com.kpj.tests.Syn.SynUnfixableTest test");
        assertEquals(expected, mavenCalls.stream().sorted().collect(Collectors.toList()), "three test runs, nothing else (no compile, no re-run)");

        assertTrue(log.contains(fixable.fqcn + " -> PENDING_PROPOSAL"), log);
        assertTrue(log.contains(unfixable.fqcn + " -> FAILURE_ANALYSIS_ONLY"), log);
        assertTrue(log.contains(healthy.fqcn + " -> NO_FAILURES"), log);
        assertTrue(log.contains("3 test(s) processed, 1 pending proposal(s) awaiting human approval"), log);
        assertTrue(log.trim().endsWith("WORKFLOW_COMPLETE: ALL_DONE"), "the last line is the marker: " + log);

        // the human-approval checkpoint: exactly the fixable test has a proposal, and nothing was written to any source
        List<Path> pending = pending();
        assertEquals(1, pending.size());
        assertEquals("SYN_FIXABLE", PendingHeal.load(pending.get(0)).testId);
        assertFalse(patched(fixable) || patched(unfixable) || patched(healthy));
        assertFalse(log.contains("Patch applied"));
    }

    @Test
    void all_aTestWhoseRunFails_isReported_andTheOthersStillProceed() throws Exception {
        exitCodes.put("SynFixableTest", 1);   // its Maven run breaks
        String log = all();
        assertTrue(log.contains(fixable.fqcn + " -> TEST_RUN_FAILED"), log);
        assertTrue(log.contains(unfixable.fqcn + " -> FAILURE_ANALYSIS_ONLY") && log.contains(healthy.fqcn + " -> NO_FAILURES"), log);
        assertEquals(List.of(), pending(), "the broken run's old failure was NOT analyzed into a proposal");
    }

    @Test
    void all_aFailingTestIdThatMapsToNoClass_isSkipped_notGuessed() throws Exception {
        SynSuite.SynTest ghost = new SynSuite.SynTest("SynGhostTest", "SYN_GHOST", "Pick ghost location", true, fixable.page);
        suite.writeRun(ghost, false);                 // a recorded failure for a testId no test source declares
        SynSuite.makeOld(out);
        String log = all();
        assertTrue(log.contains("could not resolve testId \"SYN_GHOST\" to a test class — skipping."), log);
        assertFalse(mavenCalls.stream().anyMatch(c -> c.contains("Ghost")), "nothing was launched for it");
        assertTrue(log.contains("3 test(s) processed"), log);
    }

    @Test
    void all_withNoRecordedFailures_stopsImmediately() throws Exception {
        for (SynSuite.SynTest t : suite.tests) suite.writeRun(t, true);
        String log = all();
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FAILURES"), log);
        assertEquals(List.of(), mavenCalls);
    }
}
