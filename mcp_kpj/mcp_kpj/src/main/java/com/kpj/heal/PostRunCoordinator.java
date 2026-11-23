package com.kpj.heal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The ONE shared post-run hook: given the context of a test run that has just finished, validate that the results belong to
 * exactly that run, then drive the existing agents - Failure Analysis -&gt; eligibility gate -&gt; Self-Healing {@code propose} -
 * through {@link HealingWorkflowOrchestrator#analyzeCompletedRun}, and STOP.
 *
 * <p>What it never does: run a test, open a browser, connect to DevHIS, call {@code apply-pending}, approve anything, change a
 * source file, or change the run's own exit code. It contains no failure-analysis or healing logic of its own.</p>
 *
 * <p>Safety it adds around the agents: a kill switch ({@code DEVHIS_POSTRUN=off} / {@code -Ddevhis.postrun=off}); one analysis
 * per run ({@link RunLedger}); freshness (only result files written during THIS run are analysed; stale, missing, mismatched or
 * out-of-scope output is rejected, never silently analysed); and the LLM fallback stays off unless explicitly opted in
 * ({@link LlmConfig#suppressedForAutomaticPostRun}).</p>
 *
 * <p>Must run with the project root as the working directory (the agents read {@code src/test/java} relative to it); the batch
 * hook and the Maven listener both guarantee that, and it reports {@code POSTRUN_SKIPPED: NO_SOURCE_TREE} otherwise.</p>
 */
public final class PostRunCoordinator {

    private PostRunCoordinator() { }

    private static final long FRESHNESS_TOLERANCE_MS = 2000;

    /** True while a coordinator run is in progress in this JVM: a nested trigger must stand down. */
    private static volatile boolean active = false;

    /** What a coordinator run concluded - the markers are printed verbatim as {@code POSTRUN_COMPLETE: <marker>}. */
    public static final class Result {
        public String marker = "";
        public final List<String> notes = new ArrayList<>();
        public final List<String> pendingFiles = new ArrayList<>();
        public final List<String> pendingHashes = new ArrayList<>();
        public final Map<String, String> perTest = new LinkedHashMap<>();
        public Path resultFile = null;

        @Override public String toString() { return marker; }
    }

    // ---------------------------------------------------------------- entry points

    /** {@code PostRunCoordinator --context <file>} - the batch hook's entry. Never throws; never alters an exit status. */
    public static void main(String[] args) {
        try {
            Path ctxFile = null;
            boolean batch = false;
            for (int i = 0; i < args.length; i++) {
                if ("--context".equalsIgnoreCase(args[i]) && i + 1 < args.length) ctxFile = Paths.get(args[++i]);
                else if ("--batch".equalsIgnoreCase(args[i])) batch = true;
                else if ("--recover".equalsIgnoreCase(args[i])) {
                    if (i + 1 >= args.length) { System.out.println("Usage: PostRunCoordinator --recover <output folder>"); return; }
                    recover(Paths.get(args[i + 1]));
                    return;
                }
            }
            if (batch) {
                // The batch hook (postrun-hook.bat) hands over paths through the environment, not the command line: paths with
                // spaces and the runner's own settings never have to survive Maven's argument parsing.
                String out = System.getenv("DEVHIS_POSTRUN_OUT");
                if (out == null || out.isBlank()) {
                    System.out.println("POSTRUN_SKIPPED: INVALID_CONTEXT - DEVHIS_POSTRUN_OUT is not set.");
                    return;
                }
                run(fromBatch(Paths.get(out), System.getenv("DEVHIS_POSTRUN_ENTRY"), System.getenv("DEVHIS_POSTRUN_JAR")));
                return;
            }
            if (ctxFile == null) {
                System.out.println("Usage: PostRunCoordinator --batch   (paths via DEVHIS_POSTRUN_OUT / _ENTRY / _JAR)");
                System.out.println("       PostRunCoordinator --context <run-context.properties>");
                return;
            }
            run(RunContext.load(ctxFile));
        } catch (Throwable t) {
            System.out.println("POSTRUN_COMPLETE: INTERNAL_ERROR - " + t + " (the test run's own results and exit status are unaffected)");
        }
    }

    /** What {@link #recover} concluded about an output folder - the markers are printed verbatim as {@code RECOVER_COMPLETE: <status>}. */
    public static final class Recovery {
        public String status = "";
        public String detail = "";
        @Override public String toString() { return status; }
    }

    /**
     * Explicit handling of a run that was killed or interrupted: READ-ONLY inspection of an output folder that a runner left behind, so a
     * half-finished run is named as such instead of being silently ignored or - worse - analysed as if it had completed.
     *
     * <p>It writes nothing, runs no test and never starts Failure Analysis or Self-Healing. It only says which of these the folder is:
     * {@code NO_RUN_RECORD} (no run marker), {@code ALREADY_ANALYSED} (a post-run analysis claimed that run), {@code INTERRUPTED_NO_RESULTS}
     * or {@code INTERRUPTED_PARTIAL_RESULTS} (the runner never reached its report step - results, if any, are incomplete and are NOT
     * analysed), or {@code UNANALYSED_COMPLETED_RUN} (the run did reach its report step but no analysis was ever claimed for it - for
     * example the kill switch was on, Maven was missing, or the window was closed during the analysis).</p>
     */
    public static Recovery recover(Path out) {
        Recovery r = new Recovery();
        try {
            out = out.toAbsolutePath();
            if (!Files.isDirectory(out)) return finishRecovery(r, "NO_OUTPUT_FOLDER", "the folder " + out + " does not exist");
            Path marker = out.resolve(".runmarker");
            if (!Files.isRegularFile(marker)) {
                return finishRecovery(r, "NO_RUN_RECORD", "no .runmarker in " + out + " - nothing identifies a batch run to inspect "
                        + "(a direct mvn test or RunAll leaves no marker, and an analysis for those is triggered in-process)");
            }
            long started = Files.getLastModifiedTime(marker).toMillis();
            Path postRun = out.resolve("self-healing").resolve("post-run");
            if (Files.isDirectory(postRun)) {
                try (var s = Files.list(postRun)) {
                    List<Path> locks = s.filter(p -> p.getFileName().toString().endsWith("-" + started + ".lock")).collect(Collectors.toList());
                    if (!locks.isEmpty()) {
                        return finishRecovery(r, "ALREADY_ANALYSED", "a post-run analysis claimed this run (" + locks.get(0).getFileName()
                                + "); its summary, if written, is the matching .result.txt in " + postRun);
                    }
                }
            }
            long since = started - FRESHNESS_TOLERANCE_MS;
            int fresh = 0;
            Path dataDir = out.resolve("aggregate-data");
            if (Files.isDirectory(dataDir)) {
                try (var s = Files.list(dataDir)) {
                    for (Path p : (Iterable<Path>) s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".tsv"))::iterator) {
                        if (Files.getLastModifiedTime(p).toMillis() >= since) fresh++;
                    }
                }
            }
            Path aggExit = out.resolve("aggregate-exit.txt");
            boolean reachedReport = Files.isRegularFile(aggExit) && Files.getLastModifiedTime(aggExit).toMillis() >= since;
            String when = "the run started " + Instant.ofEpochMilli(started);
            if (!reachedReport && fresh == 0) {
                return finishRecovery(r, "INTERRUPTED_NO_RESULTS", when + " and wrote no result and no report: it was interrupted before any test finished. "
                        + "There is nothing to analyse and no PASS/FAIL result exists. Any failure sidecars (failures\\) are in-flight page state only.");
            }
            if (!reachedReport) {
                return finishRecovery(r, "INTERRUPTED_PARTIAL_RESULTS", when + "; " + fresh + " result file(s) were written but the runner never reached its report step "
                        + "(no fresh aggregate-exit.txt). The run is incomplete, so these results are NOT analysed automatically. Re-run the missing flow(s).");
            }
            return finishRecovery(r, "UNANALYSED_COMPLETED_RUN", when + " and reached its report step, but no post-run analysis was ever claimed for it "
                    + "(kill switch on, Maven missing, or the window was closed during the analysis). To analyse it now (analysis and proposal only): "
                    + "set DEVHIS_POSTRUN_OUT=" + out + " & set DEVHIS_POSTRUN_ENTRY=recovered-run & mvn -q compile exec:java "
                    + "-Dexec.mainClass=com.kpj.heal.PostRunCoordinator -Dexec.args=--batch   (run from the project root)");
        } catch (Throwable t) {
            return finishRecovery(r, "RECOVER_ERROR", String.valueOf(t));
        }
    }

    private static Recovery finishRecovery(Recovery r, String status, String detail) {
        r.status = status;
        r.detail = detail;
        System.out.println("RECOVER: " + detail);
        System.out.println("RECOVER_COMPLETE: " + status + " (read-only - nothing was written, analysed or applied)");
        return r;
    }

    /**
     * Builds the context of a finished batch run from what the runner leaves in its output folder: {@code .runmarker} (created when the
     * run started - its timestamp IS the run start) and {@code .run-exit} (one 0/1 line per test JVM, written by the runner right after
     * each launch). A {@code .run-exit} older than the marker belongs to an earlier run and is ignored (exit status "not captured").
     */
    public static RunContext fromBatch(Path out, String entry, String jar) throws IOException {
        RunContext ctx = new RunContext();
        ctx.entryPoint = entry == null || entry.isBlank() ? "batch" : entry;
        ctx.outDir = out.toAbsolutePath().toString();
        ctx.endedAtMillis = System.currentTimeMillis();
        Path marker = out.resolve(".runmarker");
        if (Files.isRegularFile(marker)) ctx.startedAtMillis = Files.getLastModifiedTime(marker).toMillis();
        Path exit = out.resolve(".run-exit");
        if (Files.isRegularFile(exit) && ctx.startedAtMillis > 0
                && Files.getLastModifiedTime(exit).toMillis() >= ctx.startedAtMillis - FRESHNESS_TOLERANCE_MS) {
            for (String line : Files.readAllLines(exit)) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                try { ctx.exitCodes.add(Integer.parseInt(s)); } catch (NumberFormatException e) { ctx.exitCodes.add(Integer.MIN_VALUE); }
            }
        }
        if (jar != null && !jar.isBlank()) {
            Path j = Paths.get(jar);
            ctx.jarPath = j.toString();
            if (Files.isRegularFile(j)) {
                ctx.jarSize = Files.size(j);
                ctx.jarModifiedMillis = Files.getLastModifiedTime(j).toMillis();
            }
        }
        return ctx;
    }

    /** Disabled by {@code -Ddevhis.postrun=off} or {@code DEVHIS_POSTRUN=off} (case-insensitive). */
    public static boolean disabled() {
        String v = System.getProperty("devhis.postrun");
        if (v == null || v.isBlank()) v = System.getenv("DEVHIS_POSTRUN");
        if (v != null && "off".equalsIgnoreCase(v.trim())) return true;
        // The batch runners keep their JVM settings in DEVHIS_OPTS; honour the switch there too. Only the exact token is looked for -
        // the rest of the value (which can hold credentials) is never stored, printed or passed on.
        String opts = System.getenv("DEVHIS_OPTS");
        if (opts != null) {
            for (String tok : opts.trim().split("\\s+")) if ("-Ddevhis.postrun=off".equalsIgnoreCase(tok)) return true;
        }
        return false;
    }

    /** True while this JVM is inside a coordinator run (used by triggers to avoid nesting). */
    public static boolean isActive() { return active; }

    /**
     * The output folder the tests of THIS JVM write to: {@code -Ddevhis.outdir}, then {@code DEVHIS_OUTDIR}, then {@code test-output}
     * (the framework's own order; a value that exists only in {@code config.properties} is not seen here - the freshness check then
     * finds no new result and says so, rather than analysing the wrong folder).
     */
    public static String resolveOutDir() {
        String v = System.getProperty("devhis.outdir");
        if (v == null || v.isBlank()) v = System.getenv("DEVHIS_OUTDIR");
        if (v == null || v.isBlank()) v = "test-output";
        return Paths.get(v.trim()).toAbsolutePath().toString();
    }

    /**
     * Trigger for in-process entry points (the Maven listener, {@code RunAll}): builds the run context from what the entry point
     * knows - its start time, the exit status it observed and the exact test classes it executed - and runs the coordinator.
     * Never throws, and stands down when disabled or nested.
     */
    public static Result triggerFor(String entryPoint, long startedAtMillis, List<Integer> exitCodes, List<String> scopeClasses) {
        try {
            RunContext ctx = new RunContext();
            ctx.entryPoint = entryPoint;
            ctx.outDir = resolveOutDir();
            ctx.startedAtMillis = startedAtMillis;
            ctx.endedAtMillis = System.currentTimeMillis();
            ctx.exitCodes = new ArrayList<>(exitCodes);
            ctx.scopeClasses = new ArrayList<>(scopeClasses);
            return run(ctx);
        } catch (Throwable t) {
            Result r = new Result();
            r.marker = "INTERNAL_ERROR - " + t;
            System.out.println("POSTRUN_COMPLETE: " + r.marker + " (the test run's own results and exit status are unaffected)");
            return r;
        }
    }

    public static Result run(RunContext ctx) {
        Result res = new Result();
        if (disabled()) return finish(res, null, "POSTRUN_SKIPPED: DISABLED - automatic post-run analysis is switched off (DEVHIS_POSTRUN=off / -Ddevhis.postrun=off).");
        if (active) return finish(res, null, "POSTRUN_SKIPPED: NESTED - a post-run analysis is already running in this process.");
        active = true;
        boolean prevSuppress = LlmConfig.suppressedForAutomaticPostRun;
        Long prevFloor = FailureScanner.sidecarNotBeforeMillis;
        try {
            return runGuarded(ctx, res);
        } catch (Throwable t) {
            return finish(res, null, "INTERNAL_ERROR - " + t + " (the test run's own results and exit status are unaffected)");
        } finally {
            LlmConfig.suppressedForAutomaticPostRun = prevSuppress;
            FailureScanner.sidecarNotBeforeMillis = prevFloor;
            active = false;
        }
    }

    // ---------------------------------------------------------------- the workflow

    private static Result runGuarded(RunContext ctx, Result res) throws Exception {
        if (ctx.outDir == null || ctx.outDir.isBlank()) {
            return finish(res, null, "POSTRUN_SKIPPED: INVALID_CONTEXT - the run context names no output folder.");
        }
        Path out = Paths.get(ctx.outDir).toAbsolutePath();
        Path sourceRoot = CodeLocator.sourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            return finish(res, null, "POSTRUN_SKIPPED: NO_SOURCE_TREE - " + sourceRoot.toAbsolutePath() + " not found; the agents need the project's "
                    + "source tree (run from the project root). Nothing was analysed.");
        }
        if (!Files.isDirectory(out)) {
            return finish(res, null, "NO_FRESH_RESULTS - the output folder " + out + " does not exist; nothing to analyse.");
        }

        RunLedger ledger = new RunLedger(out);
        String key = ctx.key();
        if (!ledger.claim(key, ctx.entryPoint)) {
            return finish(res, null, "POSTRUN_ALREADY_DONE - this run (" + key + ") has already been analysed by another trigger; not analysing it twice.");
        }

        say(res, "========================================================================");
        say(res, "POSTRUN: automatic post-run analysis - entry point '" + ctx.entryPoint + "', output folder " + out);
        say(res, "POSTRUN: this analysis only READS results. It runs no test, applies no patch and approves nothing.");
        say(res, "POSTRUN: LLM fallback " + (LlmConfig.postRunLlmOptedIn() ? "allowed (explicit opt-in DEVHIS_POSTRUN_LLM=on)" : "disabled for the automatic post-run"));
        describeJar(res, ctx);
        say(res, "========================================================================");

        LlmConfig.suppressedForAutomaticPostRun = true;

        // ---- exit status ------------------------------------------------------------------------------------
        boolean nonZero = ctx.hasNonZeroExit();
        if (ctx.exitCodes.isEmpty()) {
            say(res, "POSTRUN: exit status of the test JVM(s) was not captured by this entry point - judging by the fresh result files only.");
        } else if (nonZero) {
            say(res, "POSTRUN: a test JVM exited non-zero " + ctx.exitCodes + ".");
        }

        // ---- freshness: only files written during THIS run --------------------------------------------------
        if (ctx.startedAtMillis <= 0) {
            return finish(res, ledger.resultFile(key), "NO_FRESH_RESULTS - the run's start time is unknown, so freshness cannot be proven; nothing was analysed.");
        }
        long since = ctx.startedAtMillis - FRESHNESS_TOLERANCE_MS;
        long until = ctx.endedAtMillis > 0 ? ctx.endedAtMillis + FRESHNESS_TOLERANCE_MS : Long.MAX_VALUE;

        Path dataDir = out.resolve("aggregate-data");
        List<Path> tsvs = new ArrayList<>();
        if (Files.isDirectory(dataDir)) {
            try (var s = Files.list(dataDir)) {
                tsvs = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv")).sorted().collect(Collectors.toList());
            }
        }
        List<Path> fresh = new ArrayList<>();
        List<Path> stale = new ArrayList<>();
        List<Path> future = new ArrayList<>();
        for (Path t : tsvs) {
            long m = Files.getLastModifiedTime(t).toMillis();
            if (m < since) stale.add(t);
            else if (m > until) future.add(t);
            else fresh.add(t);
        }
        if (!future.isEmpty()) {
            return finish(res, ledger.resultFile(key), "MISMATCHED_OUTPUT - " + future.size() + " result file(s) in " + dataDir + " were written AFTER this run ended "
                    + "(" + names(future) + "): another run is writing to the same output folder. Nothing was analysed.");
        }
        if (fresh.isEmpty()) {
            if (nonZero) {
                return finish(res, ledger.resultFile(key), "TEST_RUN_FAILED - a test JVM exited non-zero " + ctx.exitCodes + " and no result was written during this run ("
                        + stale.size() + " older result file(s) were ignored). Nothing was analysed.");
            }
            return finish(res, ledger.resultFile(key), "NO_FRESH_RESULTS - no result file under " + dataDir + " was written during this run ("
                    + stale.size() + " older file(s) ignored). Nothing was analysed.");
        }

        // ---- read the fresh rows: testId -> rows ------------------------------------------------------------
        Map<String, Integer> failRows = new LinkedHashMap<>();
        Set<String> freshIds = new LinkedHashSet<>();
        for (Path t : fresh) {
            for (String line : Files.readAllLines(t)) {
                String[] c = line.split("\t", -1);
                if (c.length < 6) continue;
                freshIds.add(c[2]);
                if ("FAIL".equalsIgnoreCase(c[0].trim())) failRows.merge(c[2], 1, Integer::sum);
            }
        }
        if (freshIds.isEmpty()) {
            return finish(res, ledger.resultFile(key), "NO_FRESH_RESULTS - the result files written during this run contain no step rows. Nothing was analysed.");
        }

        // ---- scope: the run's exact test classes, when the entry point knows them ----------------------------
        Map<String, String> idToClass = new TestClassResolver().resolveAll();
        if (!ctx.scopeClasses.isEmpty()) {
            Set<String> expectedIds = new LinkedHashSet<>();
            for (String fq : ctx.scopeClasses) {
                String id = idOf(fq, sourceRoot);
                if (id != null) expectedIds.add(id);
                else say(res, "POSTRUN: scoped class " + fq + " has no readable super(\"testId\") - it cannot be matched to results.");
            }
            Set<String> outside = new LinkedHashSet<>(freshIds);
            outside.removeAll(expectedIds);
            if (!outside.isEmpty()) {
                return finish(res, ledger.resultFile(key), "MISMATCHED_OUTPUT - fresh results exist for test(s) outside this run's scope " + outside
                        + " (scope: " + expectedIds + "). They belong to some other run. Nothing was analysed.");
            }
            Set<String> silent = new LinkedHashSet<>(expectedIds);
            silent.removeAll(freshIds);
            if (silent.size() == expectedIds.size()) {
                return finish(res, ledger.resultFile(key), "NO_FRESH_RESULTS - none of the run's " + expectedIds.size() + " scoped test(s) wrote a result during this run. Nothing was analysed.");
            }
            for (String s : silent) say(res, "POSTRUN: scoped test " + s + " wrote no result during this run - a missing result is NOT treated as a pass.");
        }

        // ---- a testId whose rows also sit in an OLDER result file would mix runs: refuse that test -----------
        Set<String> staleIds = new LinkedHashSet<>();
        for (Path t : stale) {
            for (String line : Files.readAllLines(t)) {
                String[] c = line.split("\t", -1);
                if (c.length >= 6) staleIds.add(c[2]);
            }
        }

        // The agents' freshness for failure-detail sidecars: nothing older than this run's start is attached to its failures.
        FailureScanner.sidecarNotBeforeMillis = since;

        FailureScanner scanner = new FailureScanner(out.toString());
        List<String> failingIds = new ArrayList<>(failRows.keySet());
        if (failingIds.isEmpty()) {
            say(res, "POSTRUN: " + fresh.size() + " fresh result file(s), " + freshIds.size() + " test(s), no failed step recorded.");
            return finish(res, ledger.resultFile(key), "NO_FAILURES - the fresh results of this run record no failed step. No Failure Analysis, no proposal.");
        }

        say(res, "POSTRUN: " + fresh.size() + " fresh result file(s); " + failingIds.size() + " test(s) with failed steps: " + failRows);
        List<String> proceed = new ArrayList<>();
        for (String id : failingIds) {
            String fqcn = idToClass.get(id);
            if (staleIds.contains(id)) {
                res.perTest.put(id, "MISMATCHED_OUTPUT (rows for this test also sit in an older result file)");
                say(res, "POSTRUN: " + id + " - REJECTED: its rows also exist in an older result file, so this run's results cannot be told apart; not analysed.");
                continue;
            }
            if (fqcn == null) {
                res.perTest.put(id, "RESOLUTION_FAILED (no test class declares super(\"" + id + "\"))");
                say(res, "POSTRUN: " + id + " - no test class declares this testId; cannot analyse it.");
                continue;
            }
            List<FailureRecord> recs = scanner.scan(id);
            int withEvidence = 0;
            for (FailureRecord r : recs) if (r.hasDomCapture() || !r.url.isEmpty()) withEvidence++;
            say(res, "POSTRUN: " + id + " (" + fqcn + ") - " + recs.size() + " failed step(s); detailed page-state evidence (failures/ sidecar written during this run) for "
                    + withEvidence + " of " + recs.size() + (withEvidence == 0
                    ? " - NOT captured by the run that produced these results; classification relies on the recorded step text only."
                    : withEvidence < recs.size() ? " - the remaining step(s) have no captured page state." : "."));
            proceed.add(id);
        }

        // ---- Failure Analysis -> eligibility -> propose, one test at a time; STOP before any apply -----------
        Path pendingDir = out.resolve("self-healing").resolve("pending");
        for (String id : proceed) {
            String fqcn = idToClass.get(id);
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            Set<String> before = listNames(pendingDir);
            HealingWorkflowOrchestrator.RunResult rr;
            try {
                rr = HealingWorkflowOrchestrator.analyzeCompletedRun(fqcn, out.toString(), id, simple);
            } catch (Throwable t) {
                res.perTest.put(id, "ANALYSIS_ERROR (" + t + ")");
                say(res, "POSTRUN: " + id + " - analysis raised " + t + "; no proposal.");
                continue;
            }
            res.perTest.put(id, rr.terminal.name());
            Set<String> created = listNames(pendingDir);
            created.removeAll(before);
            for (String name : new TreeSet<>(created)) {
                Path p = pendingDir.resolve(name);
                String hash = "";
                try { hash = PendingHeal.load(p).proposalHash; } catch (Exception e) { hash = "(unreadable: " + e + ")"; }
                res.pendingFiles.add(p.toString());
                res.pendingHashes.add(hash);
            }
        }

        // ---- verdict ----------------------------------------------------------------------------------------
        say(res, "");
        say(res, "POSTRUN: summary");
        for (Map.Entry<String, String> e : res.perTest.entrySet()) say(res, "  " + e.getKey() + " -> " + e.getValue());
        String marker;
        if (!res.pendingFiles.isEmpty()) {
            marker = "PENDING_PROPOSAL";
            say(res, "");
            say(res, "STOP - HUMAN APPROVAL REQUIRED. Nothing has been applied, no backup taken, no file changed.");
            for (int i = 0; i < res.pendingFiles.size(); i++) {
                say(res, "  PENDING_HEAL_FILE: " + res.pendingFiles.get(i));
                say(res, "  PROPOSAL_HASH    : " + res.pendingHashes.get(i));
            }
            say(res, "  Review each proposal, then approve or decline it explicitly (apply-pending <file> approved <full hash> | declined).");
        } else if (res.perTest.containsValue(HealingWorkflowOrchestrator.Terminal.NO_PENDING_PROPOSAL.name())) {
            marker = "NO_PENDING_PROPOSAL";
        } else if (res.perTest.containsValue(HealingWorkflowOrchestrator.Terminal.FAILURE_ANALYSIS_ONLY.name())) {
            marker = "FAILURE_ANALYSIS_ONLY";
        } else if (proceed.isEmpty()) {
            marker = "NO_ANALYSIS - every failing test was rejected or could not be resolved (see above)";
        } else {
            marker = "NO_PENDING_PROPOSAL";
        }
        return finish(res, ledger.resultFile(key), marker);
    }

    // ---------------------------------------------------------------- helpers

    private static Result finish(Result res, Path resultFile, String marker) {
        res.marker = marker;
        boolean skipped = marker.startsWith("POSTRUN_SKIPPED") || marker.startsWith("POSTRUN_ALREADY_DONE");
        System.out.println(skipped ? marker : "POSTRUN_COMPLETE: " + marker);
        if (resultFile != null) {
            try {
                StringBuilder sb = new StringBuilder();
                for (String n : res.notes) sb.append(n).append('\n');
                sb.append(skipped ? marker : "POSTRUN_COMPLETE: " + marker).append('\n');
                Files.writeString(resultFile, sb.toString(), StandardCharsets.UTF_8);
                res.resultFile = resultFile;
            } catch (IOException ignore) { /* the summary file is a convenience; the printed output stands */ }
        }
        return res;
    }

    private static void say(Result res, String line) {
        res.notes.add(line);
        System.out.println(line);
    }

    private static void describeJar(Result res, RunContext ctx) {
        if (ctx.jarPath == null || ctx.jarPath.isBlank()) {
            say(res, "POSTRUN: the test runner (jar) that produced these results is not recorded.");
        } else {
            say(res, "POSTRUN: results produced by " + ctx.jarPath + (ctx.jarModifiedMillis > 0 ? " (built " + Instant.ofEpochMilli(ctx.jarModifiedMillis) + ")" : "")
                    + " - whether it captures detailed failure evidence is judged below from the files it actually wrote.");
        }
    }

    private static String names(List<Path> ps) {
        return ps.stream().map(p -> p.getFileName().toString()).limit(5).collect(Collectors.joining(", ")) + (ps.size() > 5 ? ", ..." : "");
    }

    private static Set<String> listNames(Path dir) {
        Set<String> out = new HashSet<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException ignore) { /* treated as empty */ }
        return out;
    }

    /** The testId a test class declares via super("..."), read from its source - the same lookup every agent uses. */
    private static String idOf(String fqcn, Path sourceRoot) {
        try {
            Path f = sourceRoot.resolve(fqcn.replace('.', '/') + ".java");
            if (!Files.isRegularFile(f)) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(Files.readString(f));
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
