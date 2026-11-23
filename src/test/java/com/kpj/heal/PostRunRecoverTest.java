package com.kpj.heal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kpj.heal.PostRunTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Safe, SYNTHETIC tests of the explicit interrupted-run handling ({@link PostRunCoordinator#recover}). Everything happens in temp
 * folders; nothing is launched. The mode is READ-ONLY: every case also proves that not one byte in the inspected folder changed, and
 * that no Failure Analysis or Self-Healing artefact was produced.
 */
class PostRunRecoverTest {

    @TempDir
    Path tmp;

    private final long started = System.currentTimeMillis() - 600_000;

    private Path out() throws IOException {
        Path o = tmp.resolve("out");
        Files.createDirectories(o);
        return o;
    }

    private void marker(Path out) throws IOException {
        Path m = out.resolve(".runmarker");
        Files.writeString(m, "");
        Files.setLastModifiedTime(m, FileTime.from(Instant.ofEpochMilli(started)));
    }

    private static String fingerprint(Path dir) throws IOException {
        MessageDigest md;
        try { md = MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IllegalStateException(e); }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                md.update(dir.relativize(p).toString().getBytes(StandardCharsets.UTF_8));
                md.update(Files.readAllBytes(p));
                md.update((byte) Files.getLastModifiedTime(p).toMillis());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private PostRunCoordinator.Recovery inspect(Path out) throws Exception {
        String before = fingerprint(out);
        boolean healingFolderPreExisted = Files.exists(out.resolve("self-healing"));   // a test fixture may have claimed the run itself
        PostRunCoordinator.Recovery[] r = new PostRunCoordinator.Recovery[1];
        String log = capture(() -> r[0] = PostRunCoordinator.recover(out));
        assertEquals(before, fingerprint(out), "the inspected folder is byte-for-byte unchanged (modified times included)");
        assertFalse(Files.exists(out.resolve("failure-analysis")) || (!healingFolderPreExisted && Files.exists(out.resolve("self-healing"))) || Files.exists(out.resolve("unified-report")),
                "no Failure Analysis / Self-Healing artefact was produced");
        assertTrue(log.contains("RECOVER_COMPLETE: " + r[0].status) && log.contains("nothing was written, analysed or applied"), log);
        return r[0];
    }

    @Test
    void aFolderWithNoRunMarker_hasNoRunRecord() throws Exception {
        assertEquals("NO_RUN_RECORD", inspect(out()).status);
    }

    @Test
    void aMissingFolder_isReported_andNotCreated() throws Exception {
        PostRunCoordinator.Recovery r = PostRunCoordinator.recover(tmp.resolve("nope"));
        assertEquals("NO_OUTPUT_FOLDER", r.status);
        assertFalse(Files.exists(tmp.resolve("nope")));
    }

    @Test
    void aMarkerWithNoResultsAndNoReport_isInterruptedWithNothingToAnalyse() throws Exception {
        Path o = out();
        marker(o);
        PostRunCoordinator.Recovery r = inspect(o);
        assertEquals("INTERRUPTED_NO_RESULTS", r.status);
        assertTrue(r.detail.contains("no PASS/FAIL result exists"), r.detail);
    }

    @Test
    void inFlightFailureSidecarsAlone_doNotMakeAResult() throws Exception {
        Path o = out();
        marker(o);
        writeSidecar(o, "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.ofEpochMilli(started + 60_000));   // like the interrupted OccupancyList run
        Files.writeString(o.resolve("failures").resolve("history-SYN_FLOW.tsv"), "FAIL\tstep\n");
        PostRunCoordinator.Recovery r = inspect(o);
        assertEquals("INTERRUPTED_NO_RESULTS", r.status);
        assertTrue(r.detail.contains("in-flight page state only"), r.detail);
    }

    @Test
    void partialResultsWithoutAReportStep_areIncomplete_andNeverAnalysed() throws Exception {
        Path o = out();
        marker(o);
        writeTsv(o, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(started + 60_000));
        PostRunCoordinator.Recovery r = inspect(o);
        assertEquals("INTERRUPTED_PARTIAL_RESULTS", r.status);
        assertTrue(r.detail.contains("NOT analysed automatically") && r.detail.contains("1 result file"), r.detail);
    }

    @Test
    void aStaleReportExitFromAnEarlierRun_isNotTakenAsThisRunsReport() throws Exception {
        Path o = out();
        marker(o);
        writeTsv(o, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(started + 60_000));
        Path exit = o.resolve("aggregate-exit.txt");
        Files.writeString(exit, "1");
        setMtime(exit, Instant.ofEpochMilli(started - 3_600_000));   // left over from a previous run
        assertEquals("INTERRUPTED_PARTIAL_RESULTS", inspect(o).status);
    }

    @Test
    void aRunThatReachedItsReportStepButWasNeverAnalysed_isNamed_withTheExactCommandToAnalyseIt() throws Exception {
        Path o = out();
        marker(o);
        writeTsv(o, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(started + 60_000));
        Path exit = o.resolve("aggregate-exit.txt");
        Files.writeString(exit, "1");
        setMtime(exit, Instant.ofEpochMilli(started + 120_000));
        PostRunCoordinator.Recovery r = inspect(o);
        assertEquals("UNANALYSED_COMPLETED_RUN", r.status);
        assertTrue(r.detail.contains("DEVHIS_POSTRUN_OUT=" + o.toAbsolutePath()) && r.detail.contains("com.kpj.heal.PostRunCoordinator") && r.detail.contains("--batch"), r.detail);
        assertTrue(r.detail.contains("analysis and proposal only"), "and it says that analysing never applies anything: " + r.detail);
    }

    @Test
    void aRunThatAnalysisAlreadyClaimed_isReportedAsAnalysed() throws Exception {
        Path o = out();
        marker(o);
        writeTsv(o, "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.ofEpochMilli(started + 60_000));
        assertTrue(new RunLedger(o).claim("batch-module-ip-devhis-" + started, "test"));
        PostRunCoordinator.Recovery r = inspect(o);
        assertEquals("ALREADY_ANALYSED", r.status);
    }

    @Test
    void theRealInterruptedOccupancyListFolder_isRecognisedAsInterrupted_whenCopied() throws Exception {
        Path real = Paths.get("test-output-e2e-occupancy");
        assumeTrue(Files.isDirectory(real.resolve("failures")), "the interrupted-run evidence folder is not present");
        Path copy = tmp.resolve("copy");
        try (Stream<Path> s = Files.walk(real)) {
            for (Path p : s.collect(Collectors.toList())) {
                Path t = copy.resolve(real.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
            }
        }
        marker(copy);   // the runner's marker, which that folder never had because the run went through the orchestrator
        List<String> before = files(real).stream().map(Path::toString).collect(Collectors.toList());
        PostRunCoordinator.Recovery r = inspect(copy);
        assertEquals("INTERRUPTED_NO_RESULTS", r.status, "the real interrupted run is correctly named");
        assertEquals(before, files(real).stream().map(Path::toString).collect(Collectors.toList()), "the original folder was only read");
    }

    @Test
    void theCoordinatorSourceStillNeverWrites_inRecoverMode() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/com/kpj/heal/PostRunCoordinator.java"));
        String recover = src.substring(src.indexOf("public static Recovery recover"), src.indexOf("private static Recovery finishRecovery"));
        assertFalse(recover.contains("Files.write") || recover.contains("Files.copy") || recover.contains("Files.delete") || recover.contains("createDirectories")
                || recover.contains("claim(") || recover.contains("FailureAnalysisAgent") || recover.contains("SelfHealingAgent")
                || recover.contains("analyzeCompletedRun"), "recover() only reads");
    }
}
