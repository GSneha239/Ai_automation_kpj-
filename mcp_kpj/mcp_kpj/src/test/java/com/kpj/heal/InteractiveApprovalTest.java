package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of the INTERACTIVE approval path ({@code SelfHealingAgent <class> [outDir]}, {@code --all}, {@code --dry-run}):
 * the human answers on stdin, and ONLY an exact {@code y} / {@code Y} (after trimming) applies a patch. Stdin is a canned byte stream,
 * the "test run" is a fake launcher, and the source being "healed" is a temp file: no terminal, Maven, browser or DevHIS is involved.
 */
class InteractiveApprovalTest {

    @TempDir
    Path tmp;

    private static final String PROMPT = "Apply this change? [y/N]:";

    private SelfHealingAgent.MavenLauncher realLauncher;
    private InputStream realIn;
    private SynSuite suite;
    private SynSuite.SynTest eligible, noEvidence;
    private Path out;
    private final List<String> mavenCalls = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        realLauncher = SelfHealingAgent.mavenLauncher;
        realIn = System.in;
        out = tmp.resolve("out");
        suite = new SynSuite(tmp.resolve("proj"), out);
        eligible = suite.add("SynEligibleTest", "SYN_ELIGIBLE", "Pick eligible location", true);
        suite.install();
        suite.writeRun(eligible, false);
        SelfHealingAgent.mavenLauncher = args -> {
            mavenCalls.add(String.join(" ", args));
            if (Arrays.stream(args).anyMatch(a -> a.startsWith("-Dtest="))) {   // after a patch the re-run passes
                for (SynSuite.SynTest t : suite.tests) if (("-Dtest=" + t.fqcn).equals(args[args.length - 2])) suite.writeRun(t, true);
            }
            return 0;
        };
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = realLauncher;
        System.setIn(realIn);
        SynSuite.uninstall();
    }

    // ------------------------------------------------------------------ helpers

    /** Behaves like a terminal: each read hands over at most ONE line and nothing is read ahead, so every prompt gets its own answer. */
    private static final class TerminalLikeInput extends InputStream {
        private final byte[] data;
        private int pos = 0;
        TerminalLikeInput(String s) { data = s.getBytes(StandardCharsets.UTF_8); }
        @Override public int read() { return pos < data.length ? data[pos++] & 0xFF : -1; }
        @Override public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = 0;
            while (n < len && pos < data.length) {
                byte c = data[pos++];
                b[off + n++] = c;
                if (c == '\n') break;
            }
            return n;
        }
        @Override public int available() { return 0; }
    }

    private String run(String stdin, String... args) throws Exception { return run(new TerminalLikeInput(stdin), args); }

    /** Several answers arriving at once, as when stdin is piped from a file. */
    private String runPiped(String stdin, String... args) throws Exception {
        return run(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), args);
    }

    private String run(InputStream in, String... args) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        System.setIn(in);
        try { SelfHealingAgent.main(args); } finally { System.setOut(old); System.setIn(realIn); }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    private String[] single(String... extra) {
        List<String> a = new ArrayList<>(List.of(eligible.fqcn, out.toString()));
        a.addAll(Arrays.asList(extra));
        return a.toArray(new String[0]);
    }

    private List<Map<String, String>> auditRows() throws IOException {
        Path f = out.resolve("failures").resolve("healing-history.tsv");
        List<Map<String, String>> rows = new ArrayList<>();
        if (!Files.isRegularFile(f)) return rows;
        List<String> lines = Files.readAllLines(f);
        String[] header = lines.get(0).split("\t", -1);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split("\t", -1);
            Map<String, String> m = new HashMap<>();
            for (int k = 0; k < header.length; k++) m.put(header[k], k < c.length ? c[k] : "");
            rows.add(m);
        }
        return rows;
    }

    private static int count(String text, String needle) {
        int n = 0, i = 0;
        while ((i = text.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    private boolean patched(SynSuite.SynTest t) throws IOException { return Files.readString(t.page).contains("Locationid"); }

    // ================================================================== only an exact y / Y applies

    @ParameterizedTest
    @ValueSource(strings = {"n", "N", "no", "yes", "YES", "ok", "maybe", "", "1", "yes please", "y es", "yy"})
    void anythingOtherThanAnExactY_leavesTheSourceUntouched(String answer) throws Exception {
        String log = run(answer + "\n", single());
        assertTrue(log.contains(PROMPT), "the human was asked: " + log);
        assertFalse(patched(eligible), "answer \"" + answer + "\" must not apply a patch");
        assertEquals(List.of(), mavenCalls, "no compile, no re-run");
        assertEquals("NOT_APPLIED", auditRows().get(auditRows().size() - 1).get("finalStatus"));
        assertFalse(Files.exists(out.resolve("self-healing").resolve("backups"))
                && Files.walk(out.resolve("self-healing").resolve("backups")).anyMatch(Files::isRegularFile), "no backup either");
    }

    @ParameterizedTest
    @ValueSource(strings = {"y", "Y", " y ", "\ty"})
    void anExactY_appliesThePatch_andThenTheNormalVerificationRuns(String answer) throws Exception {
        String log = run(answer + "\n", single());
        assertTrue(log.contains(PROMPT));
        assertTrue(patched(eligible), "the patch was applied");
        assertEquals(List.of("-q test-compile", "-q -Dtest=com.kpj.tests.Syn.SynEligibleTest test"), mavenCalls, "compile then re-run, nothing else");
        Map<String, String> r = auditRows().get(auditRows().size() - 1);
        assertEquals("KEPT", r.get("finalStatus"));
        assertEquals("PASS", r.get("rerunResult"));
        assertTrue(r.get("backupPath").endsWith(".bak"), "a backup was taken first");
    }

    @Test
    void noAnswerAtAll_endOfInput_neverAppliesAnything() throws Exception {
        assertThrows(NoSuchElementException.class, () -> run("", single()));
        assertFalse(patched(eligible));
        assertEquals(List.of(), mavenCalls);
    }

    @Test
    void aFailedVerification_afterY_isRolledBack_withTheRestorationVerified() throws Exception {
        SelfHealingAgent.mavenLauncher = args -> {   // the re-run still fails
            mavenCalls.add(String.join(" ", args));
            return 0;
        };
        run("y\n", single());
        List<Map<String, String>> rows = auditRows();
        Map<String, String> r = rows.stream().filter(x -> "REVERTED".equals(x.get("finalStatus"))).findFirst().orElseThrow(
                () -> new AssertionError("no REVERTED row: " + rows));
        assertEquals("true", r.get("restorationVerified"));
        assertEquals("NEEDS_TRIAGE", rows.get(rows.size() - 1).get("finalStatus"),
                "the same patch is not offered a second time (loop protection) - the step goes to a person");
        assertFalse(patched(eligible), "the source is back to the original");
        assertEquals(1, mavenCalls.stream().filter(c -> c.contains("test-compile")).count(), "and no second attempt was compiled");
    }

    // ================================================================== dry run

    @Test
    void dryRun_neverAsks_andNeverWrites() throws Exception {
        String log = run("y\ny\ny\n", single("--dry-run"));   // even a stream full of y's is never read
        assertFalse(log.contains(PROMPT), log);
        assertTrue(log.contains("[DRY-RUN]"), log);
        assertFalse(patched(eligible));
        assertEquals(List.of(), mavenCalls);
        assertEquals("DRY_RUN", auditRows().get(auditRows().size() - 1).get("finalStatus"));
    }

    // ================================================================== --all (interactive)

    @Test
    void all_asksOncePerProposal_skipsTheStepsFailureAnalysisRejects_andAppliesNothingOnN() throws Exception {
        noEvidence = suite.add("SynNoEvidenceTest", "SYN_NOEVIDENCE", "Pick without evidence", false);
        suite.writeRun(noEvidence, false);
        String log = run("n\n", "--all", out.toString());
        assertEquals(1, count(log, PROMPT), "only the eligible test's fix is put to the human: " + log);
        assertFalse(patched(eligible) || patched(noEvidence));
        assertEquals(List.of(), mavenCalls);
        Map<String, Map<String, String>> byStep = new HashMap<>();
        for (Map<String, String> r : auditRows()) byStep.put(r.get("step"), r);
        assertEquals("NOT_APPLIED", byStep.get(eligible.step).get("finalStatus"));
        assertEquals("NEEDS_TRIAGE", byStep.get(noEvidence.step).get("finalStatus"));
    }

    @Test
    void all_appliesOnlyTheProposalTheHumanAnsweredY_to() throws Exception {
        SynSuite.SynTest second = suite.add("SynSecondTest", "SYN_SECOND", "Pick second location", true);
        suite.writeRun(second, false);
        String log = run("y\nn\n", "--all", out.toString());   // first proposal yes, second no
        assertEquals(2, count(log, PROMPT), log);
        int patchedCount = (patched(eligible) ? 1 : 0) + (patched(second) ? 1 : 0);
        assertEquals(1, patchedCount, "exactly the one approved fix was applied");
        // and it is the one that was asked first
        assertTrue(patched(eligible) ^ patched(second));
    }

    @Test
    void piped_severalAnswersAtOnce_runOutOfInputAtTheSecondPrompt_andNothingUnapprovedIsEverApplied() throws Exception {
        // Each prompt opens its own Scanner, so when several answers arrive in one read the first prompt swallows them all and the
        // second finds the input exhausted. That is a limitation for piped input (a terminal is unaffected) and it fails SAFE.
        SynSuite.SynTest second = suite.add("SynSecondTest", "SYN_SECOND", "Pick second location", true);
        suite.writeRun(second, false);
        assertThrows(NoSuchElementException.class, () -> runPiped("n\nn\n", "--all", out.toString()));
        assertFalse(patched(eligible) || patched(second), "nothing was applied");
        assertEquals(List.of(), mavenCalls);
    }

    @Test
    void all_withNothingFailing_doesNothing() throws Exception {
        suite.writeRun(eligible, true);
        String log = run("", "--all", out.toString());
        assertTrue(log.contains("no FAILED steps found anywhere"), log);
        assertFalse(patched(eligible));
    }
}
