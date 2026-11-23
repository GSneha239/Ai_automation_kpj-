package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Shared, run-scoped state for ONE {@code SelfHealingAgent} invocation - whether that invocation heals a
 * single test class or every failing test in the project ({@code --all}). Centralising this (rather than
 * threading loose local variables through every call) is what lets {@code --all} share {@code maxAttempts}
 * and loop-protection across every test class in the run, exactly the way the single-test path already
 * shared them across one test's own multiple failures.
 */
public class HealingSession {

    public final int maxAttempts;
    public final boolean dryRun;
    public int attempts = 0;

    /** (testId, step) pairs already decided non-actionable / declined / exhausted this run. Keyed by BOTH
     *  testId and step - not step alone - so two different tests that happen to share a step name (e.g.
     *  "Login") never cross-contaminate each other's loop protection. */
    private final Set<String> exhaustedSteps = new LinkedHashSet<>();
    /** Exact (testId, step, oldSnippet, newSnippet) signatures already tried and failed verification. */
    private final Set<String> triedFailedSignatures = new LinkedHashSet<>();
    /** Root-cause group keys already successfully healed this run - later members of the same group are
     *  not re-proposed; the next re-scan naturally shows whether they're fixed too (requirement 3). */
    private final Set<String> healedGroupKeys = new LinkedHashSet<>();

    public HealingSession(int maxAttempts, boolean dryRun) {
        this.maxAttempts = maxAttempts;
        this.dryRun = dryRun;
    }

    /** Dry-run never writes anything, so it is never subject to the write-attempt cap - it still terminates
     *  because every analysed failure is marked exhausted regardless of outcome (see SelfHealingAgent). */
    public boolean underAttemptBudget() { return dryRun || attempts < maxAttempts; }

    /** Length-prefixed encoding so no combination of ordinary testId/step values can collide. */
    private static String stepKey(String testId, String step) {
        return testId.length() + ":" + testId + step;
    }

    /** Steps skipped for THIS invocation only (never saved): the skip lifts itself on the next run once its cause is gone. */
    private final Set<String> skippedThisRun = new LinkedHashSet<>();

    public boolean isStepExhausted(String testId, String step) {
        String key = stepKey(testId, step);
        return exhaustedSteps.contains(key) || skippedThisRun.contains(key);
    }
    public void markStepExhausted(String testId, String step) { exhaustedSteps.add(stepKey(testId, step)); }
    /** Like {@link #markStepExhausted} for the loop that is running now, but not persisted - for a block that a person can clear. */
    public void skipStepForThisRun(String testId, String step) { skippedThisRun.add(stepKey(testId, step)); }

    public boolean isSignatureTriedAndFailed(String signature) { return triedFailedSignatures.contains(signature); }
    public void markSignatureFailed(String signature) { triedFailedSignatures.add(signature); }

    public boolean isGroupHealed(String groupKey) { return healedGroupKeys.contains(groupKey); }
    public void markGroupHealed(String groupKey) { healedGroupKeys.add(groupKey); }

    /** Requirement (chat-driven approval) — persists this session's mutable state so the attempt budget and
     *  loop-protection genuinely survive across the two-phase {@code propose}/{@code apply-pending} split
     *  (each its own JVM invocation) and across repeated "heal" requests for the same scope, exactly the way
     *  they already survived across steps within one continuous in-process run. */
    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Properties p = new Properties();
            p.setProperty("attempts", String.valueOf(attempts));
            p.setProperty("exhaustedSteps", joinSet(exhaustedSteps));
            p.setProperty("triedFailedSignatures", joinSet(triedFailedSignatures));
            p.setProperty("healedGroupKeys", joinSet(healedGroupKeys));
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "HealingSession persisted state - internal, do not edit by hand");
            }
        } catch (IOException e) {
            System.out.println("HealingSession: could not save state to " + file + " - " + e.getMessage());
        }
    }

    /** Loads a previously-saved session, or a fresh one (0 attempts, no exhausted/failed/healed state) if the
     *  file doesn't exist yet — the natural "first request for this scope" case. {@code maxAttempts}/{@code
     *  dryRun} always come from the CURRENT invocation's own configuration, never from the saved file, so a
     *  changed {@code -Dheal.maxAttempts} takes effect immediately. */
    public static HealingSession load(Path file, int maxAttempts, boolean dryRun) {
        HealingSession s = new HealingSession(maxAttempts, dryRun);
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                Properties p = new Properties();
                p.load(in);
                s.attempts = Integer.parseInt(p.getProperty("attempts", "0"));
                loadSet(s.exhaustedSteps, p.getProperty("exhaustedSteps", ""));
                loadSet(s.triedFailedSignatures, p.getProperty("triedFailedSignatures", ""));
                loadSet(s.healedGroupKeys, p.getProperty("healedGroupKeys", ""));
            } catch (IOException | NumberFormatException e) {
                System.out.println("HealingSession: could not load state from " + file + " - " + e.getMessage()
                        + " - starting a fresh session.");
            }
        }
        return s;
    }

    /** Length-prefixed join/split (same convention as {@link HealingCandidate#signature}) — safe regardless
     *  of what characters (including a real newline, from a multi-line snippet) an individual element holds. */
    private static String joinSet(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(s.length()).append(':').append(s);
        return sb.toString();
    }

    private static void loadSet(Set<String> target, String joined) {
        int i = 0;
        while (i < joined.length()) {
            int colon = joined.indexOf(':', i);
            if (colon < 0) break;
            int len;
            try { len = Integer.parseInt(joined.substring(i, colon)); } catch (NumberFormatException e) { break; }
            int start = colon + 1;
            if (start + len > joined.length()) break;
            target.add(joined.substring(start, start + len));
            i = start + len;
        }
    }
}
