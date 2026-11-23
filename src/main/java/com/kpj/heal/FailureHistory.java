package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * V2 capability 4 — reads the small append-only history log {@code DevHisBase} now writes for every step
 * ({@code <outDir>/failures/history-<testId>.tsv}: one "status\tstep" line per run) to tell an INTERMITTENT
 * step (sometimes fails, sometimes passes) apart from one that fails every time. Read-only; this class
 * never writes and is purely additional evidence surfaced to the human approving a proposal — it does not
 * gate or alter what {@link PatchProposer} proposes.
 */
public class FailureHistory {

    public static class StepHistory {
        public final int passCount;
        public final int failCount;

        StepHistory(int passCount, int failCount) {
            this.passCount = passCount;
            this.failCount = failCount;
        }

        public int total() { return passCount + failCount; }

        /** True when the step has BOTH passed and failed across recorded runs — the signature of a flake. */
        public boolean isIntermittent() { return passCount > 0 && failCount > 0; }
    }

    private final Path outDir;

    public FailureHistory(String outDir) {
        this.outDir = Paths.get(outDir);
    }

    /** History for one (testId, step) pair. Empty (0/0) if no history file exists yet or the step never appears. */
    public StepHistory forStep(String testId, String step) {
        int pass = 0, fail = 0;
        Path f = outDir.resolve("failures").resolve("history-" + safe(testId) + ".tsv");
        if (Files.isRegularFile(f)) {
            try {
                for (String line : Files.readAllLines(f)) {
                    String[] c = line.split("\t", -1);
                    if (c.length < 2 || !c[1].equals(step)) continue;
                    if ("FAIL".equalsIgnoreCase(c[0])) fail++;
                    else if ("PASS".equalsIgnoreCase(c[0])) pass++;
                }
            } catch (IOException ignore) { /* history is best-effort context, not a source of truth */ }
        }
        return new StepHistory(pass, fail);
    }

    private static String safe(String testId) { return testId.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
