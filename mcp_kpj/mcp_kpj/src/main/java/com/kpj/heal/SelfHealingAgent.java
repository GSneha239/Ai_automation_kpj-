package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Self-Healing UI Test Agent — orchestrator. This is the FINAL, consolidated implementation: single-test
 * healing (V1-V4, unchanged in behaviour) plus full-project scanning, dry-run analysis, failure grouping,
 * dependency/impact analysis, Git-aware safety, a reusable healing-memory knowledge base, a patch quality
 * guard, and (this pass) a chat-driven two-phase approval mode — all layered onto the same pipeline, sharing
 * the same collaborators, never duplicating logic.
 *
 * <p>Pipeline, per FAILED step: (1) detect from the last run's own reports (always RE-SCANNED fresh), (2)
 * failure detail (URL/DOM, plus a SETTLED DOM snapshot) was already captured live by {@code DevHisBase}, (3)
 * locate the responsible Page Object method, (4) skip if its root-cause group was already healed this
 * session, skip if the file is a protected resource, skip if the file already has unrelated uncommitted Git
 * changes, (5) classify the failure ({@link FailureClassifier}) — a hardcoded file path, an
 * infrastructure/auth/certificate/timeout/network/environment/database failure, insufficient evidence, or an
 * ambiguous enabled/visible/required STATE check is reported only and never reaches a patch, (6) analyse
 * dependency/impact ({@link ImpactAnalyzer}) for display and as adaptive-confidence input, (7) propose —
 * deterministic rules run first; the LLM is only ever consulted if none match and it's enabled, (8) filter
 * candidates through loop-protection and {@link PatchQualityGuard}, (9) show an explicit {@link
 * RootCauseAnalysis} and {@link AdaptiveConfidenceScorer} result plus the diff, (10) in dry-run mode, stop
 * here — never write, never prompt, never apply; otherwise require an explicit human approval, (11) on
 * approval, {@link PatchApplier} takes a verified on-disk backup — if that fails, nothing is written — then
 * applies, (12) recompile, (13) re-run just that test, then any read-only impacted regression tests {@link
 * ImpactAnalyzer} found, (14) verify the target step AND that no new (downstream or impacted-test) FAIL
 * appeared — revert (restoring the backup) and report if any check fails, (15) record the attempt to the
 * append-only audit trail and re-scan for the next remaining failure, bounded by a configurable maximum
 * number of healing attempts shared across the WHOLE run, and loop-protected against re-trying an exact edit
 * that already failed verification this session.</p>
 *
 * <p><b>Two ways to get a human's approval, one identical pipeline behind both:</b></p>
 * <ul>
 *   <li><b>Interactive/terminal</b> ({@code SelfHealingAgent <FQCN>} or {@code --all}, optionally {@code
 *       --dry-run}) — unchanged since V1: one continuous JVM run, a single {@code Apply this change? [y/N]:}
 *       prompt read from stdin per candidate, via {@link PatchApplier#confirmAndApply(Path, String, String,
 *       String, double)}.</li>
 *   <li><b>Chat-driven, two-phase</b> ({@code propose} then {@code apply-pending}) — for a Claude Code chat
 *       session, which has no live stdin channel into a running process. {@code propose} runs the ENTIRE
 *       analyze/classify/locate/propose/score pipeline for the next actionable step and STOPS right before
 *       applying, serializing everything needed as a {@link PendingHeal} file instead of prompting. Claude
 *       Code relays the shown proposal to the human IN CHAT, and once (and only once) the human gives an
 *       explicit, current, affirmative answer, invokes {@code apply-pending <file> approved} - a second,
 *       separate, still non-interactive JVM run that performs backup/apply/compile/rerun/verify/keep-or-revert
 *       via {@link PatchApplier#confirmAndApply(Path, String, String, String, double, boolean)} - the SAME
 *       underlying diff-check/backup/write code, just told the decision directly instead of reading stdin.
 *       {@code apply-pending <file> declined} performs no write at all, exactly like typing anything other
 *       than {@code y}/{@code Y} at the interactive prompt.</li>
 * </ul>
 * Neither mode ever weakens a safety gate: both paths run through the exact same {@link FailureClassifier},
 * {@link ProtectedResourcePolicy}, {@link PatchQualityGuard}, {@link HealingSession} loop-protection/attempt
 * budget (persisted to disk so it is genuinely shared across the two separate {@code propose}/{@code
 * apply-pending} processes), and {@link PatchApplier} (still the ONLY class that writes a source file, still
 * requiring an explicit, current, human-given approval — never inferred, never defaulted, never bypassed).
 *
 * <p>Usage:</p>
 * <pre>
 *   REM single test — unchanged since V1 (interactive, terminal-driven)
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent ^
 *       -Dexec.args="com.kpj.tests.Op_Page.RegistrationTest"
 *
 *   REM full project — every test with a recorded FAILED step (interactive, terminal-driven)
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="--all"
 *
 *   REM analysis-only — never writes, never prompts, never applies (either mode)
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="--all --dry-run"
 *
 *   REM discovery — lists every resolvable test class + testId (read-only; no healing, no reports)
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="--list"
 *
 *   REM chat-driven phase 1 — analyze, propose, STOP (writes a PendingHeal file, never prompts)
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent ^
 *       -Dexec.args="propose com.kpj.tests.Op_Page.RegistrationTest"
 *
 *   REM chat-driven phase 2 — apply (or decline) the SAME proposal, given the human's chat decision
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent ^
 *       -Dexec.args="apply-pending test-output/self-healing/pending/XYZ.properties approved"
 * </pre>
 * Run the target test(s) at least once first (FAILED steps come from {@code test-output/aggregate-data}).
 * Maximum healing attempts per run, shared across every test in that run: {@code -Dheal.maxAttempts=N} or
 * {@code HEAL_MAX_ATTEMPTS=N} (default 3) — ignored (never limits analysis) in {@code --dry-run}.
 */
public class SelfHealingAgent {

    /** Every collaborator shared across every test processed in one run — built once in {@code main}, never
     *  duplicated per-test. A plain data holder, not a new architectural layer: {@code healOneTest} would
     *  otherwise need a dozen individual parameters. */
    private static final class SharedContext {
        final String outDir;
        final FailureScanner scanner;
        final CodeLocator locator;
        final FailureClassifier classifier;
        final PatchProposer proposer;
        final FailureHistory history;
        final HealingMemory memory;
        final AdaptiveConfidenceScorer scorer;
        final HealingAuditTrail audit;
        final FailureGrouper grouper;
        final ImpactAnalyzer impactAnalyzer;
        final PatchQualityGuard qualityGuard;
        final GitStatus git;
        final LlmProvider llmProvider;
        final LlmFallbackProposer llmFallback;
        final int maxAttempts;
        final FailureCategoryClassifier categoryClassifier;
        final ResolutionTracker resolutionTracker;
        final SemanticValidator semanticValidator;

        SharedContext(String outDir, GitStatus git, LlmProvider llmProvider, LlmFallbackProposer llmFallback, int maxAttempts) {
            this.outDir = outDir;
            this.git = git;
            this.llmProvider = llmProvider;
            this.llmFallback = llmFallback;
            this.maxAttempts = maxAttempts;
            this.scanner = new FailureScanner(outDir);
            this.locator = new CodeLocator();
            this.classifier = new FailureClassifier();
            this.proposer = new PatchProposer();
            this.history = new FailureHistory(outDir);
            this.memory = new HealingMemory(outDir);
            this.scorer = new AdaptiveConfidenceScorer();
            this.audit = new HealingAuditTrail(outDir);
            this.grouper = new FailureGrouper();
            this.impactAnalyzer = new ImpactAnalyzer();
            this.categoryClassifier = newCategoryClassifier();
            this.resolutionTracker = new ResolutionTracker(outDir);
            this.qualityGuard = new PatchQualityGuard();
            this.semanticValidator = new SemanticValidator();
        }
    }

    /** Test seam: when non-null, used instead of a fresh {@link FailureCategoryClassifier} everywhere eligibility is decided
     *  (the orchestrator's gate, propose, apply-time validation). Always null in production. */
    static volatile FailureCategoryClassifier categoryClassifierOverride = null;

    static FailureCategoryClassifier newCategoryClassifier() {
        FailureCategoryClassifier o = categoryClassifierOverride;
        return o != null ? o : new FailureCategoryClassifier();
    }

    /**
     * Read-only recovery check after a RESTORE_FAILED: tells a person whether a source file is now byte-for-byte the original
     * again (so they know it is safe to continue) or still needs restoring, and from which backup. Changes nothing.
     * Usage: {@code verify-restore <sourceFile> [outDir]}.
     */
    private static void verifyRestoreMode(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: SelfHealingAgent verify-restore <sourceFile> [outDir]");
            return;
        }
        Path target = Paths.get(args[0]);
        String outDir = args.length > 1 ? args[1] : "test-output";
        HealingAuditTrail trail = new HealingAuditTrail(outDir);
        HealingAuditTrail.RestoreRecord rec = trail.latestRestoreFailure(target);
        if (rec == null) {
            System.out.println("verify-restore: no unverified rollback is recorded for " + target + " under " + outDir + ".");
            System.out.println("RESTORE_STATUS: NO_RECORD");
            return;
        }
        String unresolved = trail.unresolvedRestoreFailure(target);
        if (!rec.backupPath.isEmpty()) System.out.println("Backup recorded for that attempt: " + rec.backupPath);
        if (!rec.originalSha256.isEmpty()) System.out.println("A correct restoration has SHA-256: " + rec.originalSha256);
        if (unresolved == null) {
            System.out.println("RESTORE_STATUS: RECOVERED - " + target + " no longer holds the unrestored change; healing may continue for it.");
        } else {
            System.out.println("RESTORE_STATUS: UNRESOLVED - " + unresolved + ".");
            System.out.println("After restoring the file from the backup, run this command again to confirm.");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "verify-restore".equalsIgnoreCase(args[0])) {
            verifyRestoreMode(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "propose".equalsIgnoreCase(args[0])) {
            proposeMode(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "apply-pending".equalsIgnoreCase(args[0])) {
            applyPendingMode(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        boolean allMode = false, dryRun = false, listMode = false;
        List<String> positional = new ArrayList<>();
        for (String a : args) {
            if ("--all".equalsIgnoreCase(a)) allMode = true;
            else if ("--dry-run".equalsIgnoreCase(a)) dryRun = true;
            else if ("--list".equalsIgnoreCase(a) || "--agents".equalsIgnoreCase(a)) listMode = true;
            else positional.add(a);
        }

        // Discovery mode — read-only, no healing collaborators needed. Reuses TestClassResolver's
        // testId->class map (the exact same reverse lookup --all already depends on) and
        // FailureScanner.listFailingTestIds() so this list is never a second, hand-maintained catalog.
        if (listMode) {
            printAgentCatalog(positional.isEmpty() ? "test-output" : positional.get(0));
            return;
        }

        String className = null;
        String outDir = "test-output";
        if (allMode) {
            if (!positional.isEmpty()) outDir = positional.get(0);
        } else {
            if (positional.isEmpty()) {
                System.out.println("Usage: SelfHealingAgent <fully.qualified.TestClassName> [outDir] [--dry-run]");
                System.out.println("       SelfHealingAgent --all [outDir] [--dry-run]");
                System.out.println("       SelfHealingAgent --list [outDir]");
                System.out.println("       SelfHealingAgent propose <fully.qualified.TestClassName> [outDir]");
                System.out.println("       SelfHealingAgent apply-pending <pendingFile> approved|declined");
                return;
            }
            className = positional.get(0);
            if (positional.size() > 1) outDir = positional.get(1);
        }

        int maxAttempts = cfgInt("heal.maxAttempts", "HEAL_MAX_ATTEMPTS", 3);
        System.out.println("SelfHealingAgent: maximum healing attempts this run: " + maxAttempts
                + (dryRun ? " (not enforced — dry-run mode never applies anything)" : ""));
        if (dryRun) {
            System.out.println("SelfHealingAgent: DRY-RUN mode — analysis only. No file will be modified, no "
                    + "approval will ever be requested, nothing will be committed or pushed.");
        }

        GitStatus git = GitStatus.capture();
        System.out.println("SelfHealingAgent: Git " + (git.isRepo
                ? ("branch=" + git.branch + ", commit=" + shortCommit(git.commit) + ", "
                    + git.modifiedOrUntrackedFiles.size() + " modified/untracked file(s)")
                : "not a Git repository — Git-aware safety checks are skipped."));

        LlmConfig llmConfig = LlmConfig.fromEnvironment();
        LlmProvider llmProvider = llmConfig.enabled ? new HttpLlmProvider(llmConfig) : new NoOpLlmProvider();
        LlmFallbackProposer llmFallback = new LlmFallbackProposer(llmProvider);
        System.out.println("SelfHealingAgent: LLM fallback " + (llmProvider.isEnabled() ? "ENABLED" : "disabled")
                + " (set LLM_ENABLED=true, LLM_ENDPOINT, LLM_API_KEY to enable).");

        SharedContext ctx = new SharedContext(outDir, git, llmProvider, llmFallback, maxAttempts);
        HealingSession session = new HealingSession(maxAttempts, dryRun);
        List<HealingAttemptRecord> allRecords = new ArrayList<>();
        List<FailureGrouper.Group> allGroups = new ArrayList<>();
        String runLabel;
        String sessionLabel; // short, clean title for report headings — never the verbose console runLabel

        if (allMode) {
            Set<String> failingIds = ctx.scanner.listFailingTestIds();
            if (failingIds.isEmpty()) {
                System.out.println("SelfHealingAgent: --all — no FAILED steps found anywhere under "
                        + outDir + "/aggregate-data.");
                return;
            }
            Map<String, String> testIdToClass = new TestClassResolver().resolveAll();
            List<String> resolvedIds = new ArrayList<>();
            for (String id : failingIds) {
                if (testIdToClass.containsKey(id)) resolvedIds.add(id);
                else System.out.println("SelfHealingAgent: could not resolve testId \"" + id + "\" to a test class "
                        + "— skipping (no test source declares super(\"" + id + "\")).");
            }
            System.out.println("SelfHealingAgent: --all mode — " + resolvedIds.size() + " of " + failingIds.size()
                    + " failing testId(s) resolved to a class.");
            runLabel = "Full-project scan (--all) - " + resolvedIds.size() + " failing test(s)";
            sessionLabel = "Full Project";

            for (String testId : resolvedIds) {
                if (!session.underAttemptBudget()) {
                    System.out.println("SelfHealingAgent: maximum healing attempts (" + maxAttempts
                            + ") reached for this run — stopping before " + testId + ".");
                    break;
                }
                allRecords.addAll(healOneTest(testIdToClass.get(testId), testId, ctx, session, allGroups));
            }
        } else {
            Path testFile = CodeLocator.sourceRoot().resolve(className.replace('.', '/') + ".java");
            if (!Files.isRegularFile(testFile)) {
                System.out.println("SelfHealingAgent: test source not found: " + testFile);
                return;
            }
            String testSrc = Files.readString(testFile);
            Matcher idm = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(testSrc);
            if (!idm.find()) {
                System.out.println("SelfHealingAgent: could not find the testId (super(\"...\")) in " + testFile);
                return;
            }
            String testId = idm.group(1);
            System.out.println("SelfHealingAgent: target " + className + " (testId=" + testId + ")");
            runLabel = className;
            sessionLabel = className.substring(className.lastIndexOf('.') + 1);
            allRecords.addAll(healOneTest(className, testId, ctx, session, allGroups));
        }

        System.out.println();
        System.out.println("SelfHealingAgent: run complete — " + session.attempts + " healing attempt(s) made "
                + "(max " + maxAttempts + ")" + (dryRun ? " [dry-run: budget not enforced, nothing was ever applied]" : "") + ".");

        if (!allRecords.isEmpty()) {
            HealingDashboard.Result reportResult = HealingDashboard.write(outDir, sessionLabel, git, allGroups, allRecords, dryRun, maxAttempts);
            if (reportResult != null) {
                System.out.println();
                System.out.println("Healing completed.");
                System.out.println();
                for (Path p : reportResult.individualReportPaths.values()) {
                    System.out.println("Individual report:");
                    System.out.println(p);
                }
                if (!reportResult.healingReportPaths.isEmpty()) {
                    System.out.println();
                    for (Path p : reportResult.healingReportPaths.values()) {
                        System.out.println("Healing report:");
                        System.out.println(p);
                    }
                }
                System.out.println();
                System.out.println("Overall summary:");
                System.out.println(reportResult.summaryReportPath);
                System.out.println();
                System.out.println("Report index:");
                System.out.println(reportResult.indexPath);
            }
        } else {
            System.out.println("SelfHealingAgent: no healing attempts were made — reports not generated.");
        }
    }

    // =======================================================================
    // Shared analysis result — one FailureRecord fully analyzed, either fully resolved as NEEDS_TRIAGE
    // (triageRecord != null, already recorded+audited — nothing else to do) or ready for a human decision.
    // =======================================================================

    private static final class AnalyzeOutcome {
        final HealingAttemptRecord triageRecord;
        final FailureRecord failure;
        final CodeLocator.CodeLocation loc;
        final HealingCandidate chosen;
        final int chosenIndex;
        final int filteredSize;
        final ImpactAnalyzer.Result impact;
        final String impactSummary;
        final FailureHistory.StepHistory history;
        final HealingMemory.Summary prior;
        final RootCauseAnalysis rca;
        final AdaptiveConfidenceScorer.Result adaptive;
        final String groupKey;
        final int attemptNumber;
        final SemanticValidator.Result semantic;

        private AnalyzeOutcome(HealingAttemptRecord triageRecord) {
            this.triageRecord = triageRecord;
            this.failure = null; this.loc = null; this.chosen = null; this.chosenIndex = 0; this.filteredSize = 0;
            this.impact = null; this.impactSummary = null; this.history = null; this.prior = null;
            this.rca = null; this.adaptive = null; this.groupKey = null; this.attemptNumber = 0; this.semantic = null;
        }

        private AnalyzeOutcome(FailureRecord failure, CodeLocator.CodeLocation loc, HealingCandidate chosen,
                                int chosenIndex, int filteredSize, ImpactAnalyzer.Result impact, String impactSummary,
                                FailureHistory.StepHistory history, HealingMemory.Summary prior, RootCauseAnalysis rca,
                                AdaptiveConfidenceScorer.Result adaptive, String groupKey, int attemptNumber,
                                SemanticValidator.Result semantic) {
            this.triageRecord = null;
            this.failure = failure; this.loc = loc; this.chosen = chosen; this.chosenIndex = chosenIndex;
            this.filteredSize = filteredSize; this.impact = impact; this.impactSummary = impactSummary;
            this.history = history; this.prior = prior; this.rca = rca; this.adaptive = adaptive;
            this.groupKey = groupKey; this.attemptNumber = attemptNumber; this.semantic = semantic;
        }
    }

    /** Analyzes the NEXT not-yet-exhausted failing step for one test — locate, root-cause-group check,
     *  protected-resource check, Git-uncommitted check, classify, dependency/impact, propose, loop-protection,
     *  quality guard, root-cause analysis, adaptive confidence. This is the ONE place that whole sequence
     *  lives; both the interactive loop ({@code healOneTest}) and the chat-driven {@code propose} mode call
     *  this same method, so neither can ever apply a different (weaker or stronger) set of gates than the
     *  other. Returns {@code null} if nothing is left to analyze (every step exhausted, or none failing).
     *
     *  @param interactiveCandidateChoice when {@code true} (the classic terminal path) and more than one
     *         candidate survives filtering, prompts on stdin exactly as before. When {@code false} (the
     *         chat-driven path, which has no stdin), auto-selects the highest-confidence candidate instead -
     *         every real failure observed so far in this codebase has produced exactly one candidate anyway. */
    private static AnalyzeOutcome analyzeNextActionableStep(String className, String testId, SharedContext ctx,
                                                             HealingSession session, boolean interactiveCandidateChoice) throws Exception {
        List<FailureRecord> failures = ctx.scanner.scan(testId); // always the LATEST result
        FailureRecord failure = failures.stream().filter(f -> !session.isStepExhausted(testId, f.step)).findFirst().orElse(null);
        if (failure == null) return null;

        System.out.println();
        System.out.println("---- Analysing: [" + testId + "] " + failure.step + " ----");

        HealingAttemptRecord record = new HealingAttemptRecord();
        record.testId = testId;
        record.testClass = className;
        record.step = failure.step;
        record.failureExpected = failure.expected;
        record.failureActual = failure.actual;

        CodeLocator.CodeLocation loc = ctx.locator.locate(className, failure.step);
        if (loc != null) {
            System.out.println("Located: " + loc.pageObjectFile + " :: " + loc.methodName + "()");
            record.affectedFile = loc.pageObjectFile.toString();
            record.affectedMethod = loc.methodName + "()";
        }

        String groupKey = FailureGrouper.signature(failure, loc);
        if (session.isGroupHealed(groupKey)) {
            System.out.println("SelfHealingAgent: this failure's root-cause group was already healed this "
                    + "session — waiting for the next re-scan to confirm, not re-proposing.");
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = "root-cause group already healed this session; awaiting re-scan confirmation";
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        // Requirement 9 — protected resource check, applied to the deterministic path too (the LLM path is
        // already covered separately via LlmProposalValidator).
        if (loc != null && ProtectedResourcePolicy.isProtected(loc.pageObjectFile)) {
            System.out.println("SelfHealingAgent: " + loc.pageObjectFile + " is a protected resource — "
                    + "NEVER automatically modified. Needs human triage.");
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = "target file is a protected resource";
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        // A rollback of this file that could not be verified (RESTORE_FAILED) must not be silently ignored: while the file
        // still appears to hold that change, nothing further is proposed for it.
        if (loc != null) {
            String unresolved = ctx.audit.unresolvedRestoreFailure(loc.pageObjectFile);
            if (unresolved != null) {
                System.out.println("SelfHealingAgent: " + unresolved + ". Needs human triage.");
                // skipped for this run only (not saved): once the file has been restored, the next proposal is allowed again
                session.skipStepForThisRun(testId, failure.step);
                record.finalStatus = "NEEDS_TRIAGE";
                record.reason = unresolved;
                ctx.audit.append(record);
                return new AnalyzeOutcome(record);
            }
        }

        // Requirement 5 — Git-aware safety: never propose a patch for a file that already carries unrelated
        // uncommitted changes, so a developer's in-progress work can never be silently discarded.
        if (loc != null && ctx.git.hasUncommittedChanges(loc.pageObjectFile)) {
            System.out.println("SelfHealingAgent: " + loc.pageObjectFile + " already has uncommitted Git "
                    + "changes — refusing to propose a patch until those are resolved. Needs human triage.");
            String existingDiff = GitStatus.diffFor(loc.pageObjectFile);
            if (!existingDiff.isBlank()) {
                System.out.println("---- existing (pre-healing) diff for this file ----");
                System.out.println(existingDiff);
                System.out.println("----------------------------------------------------");
            }
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = "target file has unrelated uncommitted Git changes";
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        FailureClassifier.Classification cls = ctx.classifier.classify(failure, loc);
        System.out.println("Classification: " + cls.category + " - " + cls.reason);
        record.classification = cls.category.name();

        // Requirement (Failure Analysis Agent integration) — the SAME categorization the Failure Analysis Agent's
        // reports show, so the two never disagree. It is also a per-step GATE (see below): a step Failure Analysis
        // marks not eligible is never proposed, whatever another step of the same test run looks like. Every other
        // safety check below still runs unconditionally.
        FailureCategoryClassifier.Result faResult = ctx.categoryClassifier.classify(failure, loc);
        System.out.println("Failure Analysis Category: " + faResult.category.displayName()
                + " (Recommended Owner: " + faResult.recommendedOwner + ")");
        System.out.println("Self-Healing Eligible: " + (faResult.selfHealingEligible ? "YES" : "NO")
                + " — " + faResult.eligibilityReason);

        if (cls.category == FailureClassifier.Category.ENVIRONMENT_FILE_PATH
                || cls.category == FailureClassifier.Category.INFRASTRUCTURE_OR_AUTH_FAILURE
                || cls.category == FailureClassifier.Category.VALIDATION_STATE_CHANGE
                || cls.category == FailureClassifier.Category.INSUFFICIENT_EVIDENCE) {
            System.out.println("SelfHealingAgent: " + cls.category + " — reporting only, NEVER auto-fixed. "
                    + "Needs human triage.");
            RootCauseAnalysis.forTriage(cls.reason,
                    List.of("Expected: " + failure.expected, "Actual: " + failure.actual, cls.reason),
                    loc != null ? (loc.pageObjectFile.getFileName() + " :: " + loc.methodName + "()") : "(unresolved)"
            ).print();
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = cls.category + ": " + cls.reason;
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        // Per-step eligibility gate. The orchestrator's "is anything eligible" check is coarse (any step of the test), and
        // this method picks the first unresolved failing step - so without this gate a step Failure Analysis marked NOT
        // eligible could be proposed just because another step is. The verdict is taken here, for exactly this step, from
        // the same classifier that wrote its Failure Analysis report. (An LLM-only proposal is therefore also impossible for
        // such a step: Failure Analysis is only "eligible" when a deterministic fix exists.)
        if (!faResult.selfHealingEligible) {
            System.out.println("SelfHealingAgent: Failure Analysis marks THIS step not eligible for automatic healing - never "
                    + "proposed, even if another step of this test is eligible. Needs human triage.");
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = "Failure Analysis marks this step not eligible for automatic healing (category: "
                    + faResult.category.displayName() + ")";
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        // Requirement 4 — dependency/impact analysis: display + confidence input only, never expands scope.
        ImpactAnalyzer.Result impact = loc != null ? ctx.impactAnalyzer.analyze(loc.pageObjectFile) : null;
        String impactSummary = impact == null ? "not analyzed"
                : impact.dependentCount() == 0 ? "No other test class depends on this Page Object (isolated change)."
                : impact.dependentCount() + " test class(es) depend on this Page Object: " + shortNames(impact.dependentTestFiles)
                  + (impact.readOnlyDependents.isEmpty() ? "" : " (" + impact.readOnlyDependents.size() + " marked read-only)");

        List<PatchProposer.Proposal> candidates = new ArrayList<>(ctx.proposer.proposeCandidates(loc.methodSource, failure));
        if (!candidates.isEmpty()) {
            System.out.println("SelfHealingAgent: deterministic rule(s) found " + candidates.size()
                    + " candidate(s) — LLM not consulted.");
        } else if (ctx.llmProvider.isEnabled()) {
            System.out.println("SelfHealingAgent: no deterministic proposal — trying V3 LLM fallback...");
            List<HealingAuditTrail.PriorAttempt> priorAttempts = ctx.audit.priorAttempts(testId, failure.step);
            if (!priorAttempts.isEmpty()) {
                System.out.println("SelfHealingAgent: " + priorAttempts.size() + " prior attempt(s) for this step "
                        + "supplied to the LLM as context (so it doesn't repeat a rejected/reverted candidate).");
            }
            PatchProposer.Proposal llmP = ctx.llmFallback.propose(className, loc, failure, faResult, priorAttempts);
            if (llmP != null) candidates.add(llmP);
        }

        // Loop protection (requirement 13) + patch quality guard (requirement 8) + semantic validation gate
        // (requirement 7/8 — applied to EVERY candidate regardless of source: deterministic and LLM alike)
        // before any candidate is shown for approval.
        List<HealingCandidate> filtered = new ArrayList<>();
        boolean anySemanticRejection = false;
        for (PatchProposer.Proposal p : candidates) {
            HealingCandidate c = new HealingCandidate(p, loc);
            if (session.isSignatureTriedAndFailed(c.signature(testId, failure.step))) {
                System.out.println("SelfHealingAgent: skipping a candidate already attempted and reverted "
                        + "this session (loop protection): " + c.kind() + " — " + p.reason);
                continue;
            }
            PatchQualityGuard.Result q = ctx.qualityGuard.check(p.oldSnippet, p.newSnippet);
            if (!q.ok) {
                System.out.println("SelfHealingAgent: candidate rejected by quality guard (" + q.reason + "): " + c.kind());
                continue;
            }
            SemanticValidator.Result sv = ctx.semanticValidator.validate(p, failure);
            if (!sv.valid) {
                System.out.println("SelfHealingAgent: candidate REJECTED by semantic validation (" + c.kind() + "): " + sv.reason);
                anySemanticRejection = true;
                continue;
            }
            filtered.add(c);
        }
        if (filtered.size() > 3) filtered = filtered.subList(0, 3);

        if (filtered.isEmpty()) {
            if (anySemanticRejection) {
                System.out.println("SelfHealingAgent: No sufficiently supported automatic fix found. Manual "
                        + "investigation required. (every candidate for this step failed semantic validation — "
                        + "see rejection reason(s) above; no fix is invented in place of a real one.)");
            } else {
                System.out.println("SelfHealingAgent: no confident automatic fix for this step"
                        + (ctx.llmProvider.isEnabled() ? " (deterministic and LLM both found nothing, or every candidate was filtered)" : "")
                        + " — Needs human triage.");
            }
            // Requirement (interaction/data-loading diagnostic evidence) - when no safe patch applies, surface
            // WHAT is actually known about the referenced locator(s) (present live? current value? option
            // count? settled delta?) as evidence only - never a patch, never applied automatically. Reuses the
            // exact same evidence Failure Analysis already attaches for this step, so the two reports agree.
            if (loc != null) {
                boolean dropdownShaped = !failure.selects.isBlank() || !failure.selectOptions.isBlank();
                if (dropdownShaped) {
                    PatchProposer.Proposal locatorHit = candidates.stream()
                            .filter(p -> p.kind == PatchProposer.Proposal.Kind.LOCATOR).findFirst().orElse(null);
                    String rejectedReason = locatorHit == null ? null : ctx.semanticValidator.validate(locatorHit, failure).reason;
                    DropdownDiagnostics.Finding dd = DropdownDiagnostics.diagnose(loc.methodSource, failure, candidates, rejectedReason);
                    if (dd != null) {
                        record.evidence = dd.lines();
                        System.out.println("Diagnostic evidence (analysis only - no patch is ever proposed from this):");
                        for (String o : dd.lines()) System.out.println("  - " + o);
                    }
                } else {
                    LocatorDiagnostics.Finding diag = LocatorDiagnostics.diagnose(loc.methodSource, failure);
                    if (diag != null && !diag.observations.isEmpty()) {
                        record.evidence = diag.observations;
                        if (diag.anyPresent) {
                            System.out.println("Diagnostic evidence (analysis only - no patch is ever proposed from this):");
                            for (String o : diag.observations) System.out.println("  - " + o);
                        }
                    }
                }
            }
            session.markStepExhausted(testId, failure.step);
            record.finalStatus = "NEEDS_TRIAGE";
            record.reason = anySemanticRejection
                    ? "No sufficiently supported automatic fix found. Manual investigation required."
                    : record.reason;
            record.semanticValidation = anySemanticRejection ? "REJECTED" : "N/A";
            ctx.audit.append(record);
            return new AnalyzeOutcome(record);
        }

        HealingCandidate chosen;
        int chosenIndex;
        if (filtered.size() == 1) {
            chosen = filtered.get(0);
            chosenIndex = 1;
        } else if (interactiveCandidateChoice) {
            System.out.println();
            System.out.println("==== " + filtered.size() + " candidate fix(es) found — choose one ====");
            for (int i = 0; i < filtered.size(); i++) {
                HealingCandidate c = filtered.get(i);
                String risk = c.proposal.source == PatchProposer.Proposal.Source.LLM
                        ? RootCauseAnalysis.forLlm(c.proposal, impactSummary).risk
                        : RootCauseAnalysis.forDeterministic(c.proposal, failure, loc, impactSummary).risk;
                System.out.printf("[%d] %-8s | confidence %3.0f%% | risk %-6s | %s%n",
                        i + 1, c.kind(), c.confidence() * 100, risk, c.source());
                System.out.println("    " + c.proposal.reason);
            }
            System.out.print("Select a candidate to review [1-" + filtered.size() + "], or 0 to skip this step: ");
            String sel = new Scanner(System.in).nextLine().trim();
            int idx;
            try { idx = Integer.parseInt(sel); } catch (Exception e) { idx = 0; }
            if (idx < 1 || idx > filtered.size()) {
                System.out.println("SelfHealingAgent: no candidate selected — skipping this step.");
                session.markStepExhausted(testId, failure.step);
                record.finalStatus = "NEEDS_TRIAGE";
                record.candidateTotal = filtered.size();
                ctx.audit.append(record);
                return new AnalyzeOutcome(record);
            }
            chosen = filtered.get(idx - 1);
            chosenIndex = idx;
        } else {
            // Chat-driven path: no stdin available. Every real candidate list observed in this codebase has
            // had exactly one entry; on the rare chance of more, take the highest-confidence one (proposer
            // already sorts descending) and say so plainly rather than silently guessing.
            chosen = filtered.get(0);
            chosenIndex = 1;
            if (filtered.size() > 1) {
                System.out.println("SelfHealingAgent: " + filtered.size() + " candidates found — auto-selected "
                        + "candidate 1 (highest confidence) for the chat approval flow.");
            }
        }

        session.attempts++; // a candidate is about to be shown for a real approval decision — this IS an attempt
        PatchProposer.Proposal proposal = chosen.proposal;

        System.out.println("Proposal source: " + (proposal.source == PatchProposer.Proposal.Source.LLM ? "V3-LLM" : "V1/V2-DETERMINISTIC"));
        System.out.println("Proposal kind: " + proposal.kind);

        FailureHistory.StepHistory sh = ctx.history.forStep(testId, failure.step);
        if (proposal.kind == PatchProposer.Proposal.Kind.TIMING) {
            if (sh.isIntermittent()) {
                System.out.println("History: this step has PASSED " + sh.passCount + " and FAILED " + sh.failCount
                        + " time(s) across prior runs — intermittent, corroborates a TIMING diagnosis.");
            } else if (sh.total() > 0) {
                System.out.println("History: " + sh.total() + " prior recorded run(s), " + sh.failCount
                        + " failure(s) so far — no intermittency observed yet.");
            } else {
                System.out.println("History: no prior recorded runs for this step yet.");
            }
        }

        RootCauseAnalysis rca = proposal.source == PatchProposer.Proposal.Source.LLM
                ? RootCauseAnalysis.forLlm(proposal, impactSummary)
                : RootCauseAnalysis.forDeterministic(proposal, failure, loc, impactSummary);
        rca.print();

        // Re-evaluated once more for the CHOSEN candidate specifically (cheap and deterministic — it already
        // passed this same check during filtering above) so the exact justification shown here matches the
        // exact candidate the human is about to see, without needing to carry the Result through the loop.
        SemanticValidator.Result semantic = ctx.semanticValidator.validate(proposal, failure);
        System.out.println();
        System.out.println("Semantic Validation: " + (semantic.valid ? "PASSED" : "REJECTED")
                + " — " + (semantic.valid ? semantic.justification : semantic.reason));

        HealingMemory.Summary prior = loc != null ? ctx.memory.forLocation(loc.pageObjectFile.toString(), loc.methodName) : null;
        AdaptiveConfidenceScorer.Result adaptive = ctx.scorer.score(proposal, failure, sh, null, impact, prior, semantic);
        System.out.println();
        System.out.println("Adaptive Confidence: " + Math.round(adaptive.score * 100) + "%");
        System.out.println("Level: " + adaptive.level);
        System.out.println("Evidence:");
        for (String e : adaptive.explanation) System.out.println("- " + e);

        return new AnalyzeOutcome(failure, loc, chosen, chosenIndex, filtered.size(), impact, impactSummary,
                sh, prior, rca, adaptive, groupKey, session.attempts, semantic);
    }

    /** Heals every failing step of ONE test, sharing {@code session}'s attempt budget and loop-protection
     *  state with every other test in this run — the classic, interactive/terminal path, unchanged in
     *  behaviour. Returns every attempt record produced (possibly empty). */
    private static List<HealingAttemptRecord> healOneTest(String className, String testId, SharedContext ctx,
                                                            HealingSession session,
                                                            List<FailureGrouper.Group> allGroupsOut) throws Exception {
        List<HealingAttemptRecord> records = new ArrayList<>();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);

        List<FailureRecord> initialFailures = ctx.scanner.scan(testId);
        if (initialFailures.isEmpty()) {
            System.out.println("SelfHealingAgent: no FAILED steps found for testId=" + testId + " under "
                    + ctx.outDir + "/aggregate-data.");
            return records;
        }
        System.out.println("SelfHealingAgent: [" + testId + "] found " + initialFailures.size() + " failed step(s):");
        for (FailureRecord f : initialFailures) System.out.println("  - " + f);

        Set<String> originalFailingSteps = initialFailures.stream().map(f -> f.step).collect(Collectors.toSet());

        // Requirement 3 — group this test's failures by likely root cause before healing anything, purely for
        // display and to avoid re-proposing the same fix for the same underlying cause.
        List<FailureGrouper.Entry> entries = new ArrayList<>();
        for (FailureRecord f : initialFailures) entries.add(new FailureGrouper.Entry(f, ctx.locator.locate(className, f.step)));
        List<FailureGrouper.Group> groups = ctx.grouper.group(entries);
        allGroupsOut.addAll(groups);
        if (groups.size() < initialFailures.size()) {
            System.out.println("SelfHealingAgent: [" + testId + "] grouped " + initialFailures.size()
                    + " failure(s) into " + groups.size() + " likely root cause(s).");
        }

        while (session.underAttemptBudget()) {
            AnalyzeOutcome outcome = analyzeNextActionableStep(className, testId, ctx, session, true);
            if (outcome == null) {
                System.out.println("\nSelfHealingAgent: [" + testId + "] no further actionable failures this session.");
                break;
            }
            if (outcome.triageRecord != null) {
                records.add(outcome.triageRecord);
                continue;
            }

            FailureRecord failure = outcome.failure;
            CodeLocator.CodeLocation loc = outcome.loc;
            HealingCandidate chosen = outcome.chosen;
            PatchProposer.Proposal proposal = chosen.proposal;

            HealingAttemptRecord record = new HealingAttemptRecord();
            record.testId = testId;
            record.testClass = className;
            record.step = failure.step;
            record.failureExpected = failure.expected;
            record.failureActual = failure.actual;
            record.affectedFile = loc.pageObjectFile.toString();
            record.affectedMethod = loc.methodName + "()";
            record.classification = ctx.classifier.classify(failure, loc).category.name();
            record.attemptNumber = outcome.attemptNumber;
            record.attemptsMax = ctx.maxAttempts;
            record.source = proposal.source == PatchProposer.Proposal.Source.LLM ? "LLM" : "DETERMINISTIC";
            record.proposalKind = proposal.kind.name();
            record.candidateIndex = outcome.chosenIndex;
            record.candidateTotal = outcome.filteredSize;
            record.confidence = proposal.confidence;
            record.oldSnippet = proposal.oldSnippet;
            record.newSnippet = proposal.newSnippet;
            record.rootCause = outcome.rca.rootCause;
            record.risk = outcome.rca.risk;
            record.evidence = outcome.rca.evidence;
            record.semanticValidation = outcome.semantic.valid ? "PASSED" : "REJECTED";
            record.semanticJustification = outcome.semantic.valid ? outcome.semantic.justification : outcome.semantic.reason;

            // Requirement 17 — dry-run stops HERE: never shows the approval prompt, never calls PatchApplier.
            if (session.dryRun) {
                System.out.println();
                System.out.println("[DRY-RUN] Would show the diff and request approval here. Nothing applied, nothing written.");
                System.out.println("---- proposed diff (not applied) ----");
                for (String line : proposal.oldSnippet.split("\n", -1)) System.out.println("- " + line);
                for (String line : proposal.newSnippet.split("\n", -1)) System.out.println("+ " + line);
                System.out.println("--------------------------------------");
                session.markStepExhausted(testId, failure.step);
                record.finalStatus = "DRY_RUN";
                record.humanDecision = "N/A (dry-run)";
                records.add(record);
                ctx.audit.append(record);
                continue;
            }

            // Failure Analysis of the ORIGINAL failure, taken before any patch is written (used only if the fix is KEPT).
            List<FailureAnalysisRecord> faSnapshot = snapshotFailureAnalysis(ctx.outDir, testId, className);
            PatchApplier applier = new PatchApplier(ctx.outDir);
            boolean applied = applier.confirmAndApply(loc.pageObjectFile, proposal.oldSnippet, proposal.newSnippet,
                    proposal.reason, proposal.confidence);
            record.humanDecision = applied ? "APPROVED" : "DECLINED";
            record.backupCreated = applier.lastBackupCreated;
            record.backupPath = applier.lastBackupPath == null ? "" : applier.lastBackupPath.toString();
            record.patchApplied = applied;

            if (!applied) {
                record.finalStatus = "NOT_APPLIED";
                records.add(record);
                ctx.audit.append(record);
                session.markStepExhausted(testId, failure.step);
                continue;
            }

            finishApplyAndVerify(ctx, session, className, testId, simpleName, failure, loc, chosen, proposal,
                    outcome.impact, outcome.history, outcome.prior, outcome.groupKey, originalFailingSteps,
                    applier, record, outcome.semantic, faSnapshot);
            records.add(record);
            ctx.audit.append(record);
        }

        return records;
    }

    /** Compile → re-run the target test → check regression/impacted tests → keep or revert → adaptive score →
     *  resolution tracking on KEPT. Shared by the interactive loop (right after a stdin approval) and {@code
     *  apply-pending} (right after a chat-relayed approval) — identical verification either way. */
    private static void finishApplyAndVerify(SharedContext ctx, HealingSession session, String className, String testId,
                                              String simpleName, FailureRecord failure, CodeLocator.CodeLocation loc,
                                              HealingCandidate chosen, PatchProposer.Proposal proposal,
                                              ImpactAnalyzer.Result impact, FailureHistory.StepHistory sh,
                                              HealingMemory.Summary prior, String groupKey,
                                              Set<String> originalFailingSteps, PatchApplier applier,
                                              HealingAttemptRecord record, SemanticValidator.Result semantic,
                                              List<FailureAnalysisRecord> faSnapshot) throws IOException, InterruptedException {
        // The patch is already written to source at this point, but the test has not been RE-RUN yet, so the
        // standard test report on disk still reflects the ORIGINAL failing run — the correct moment to grab
        // the "before" screenshot for the report (best-effort; never blocks the real verification below).
        String beforeShot = ScreenshotExtractor.extractStepScreenshot(ctx.outDir, testId, failure.step);
        record.failureScreenshotBase64 = beforeShot == null ? "" : beforeShot;
        // Fingerprints of the exact pre-patch and patched bytes: what any later restoration is verified against.
        record.originalSha256 = applier.lastOriginalSha256;
        record.patchedSha256 = applier.lastPatchedSha256;

        System.out.println("SelfHealingAgent: recompiling...");
        int compileExit = runMaven("-q", "test-compile");
        record.compileResult = compileExit == 0 ? "PASS" : "FAIL";
        if (compileExit != 0) {
            System.out.println("SelfHealingAgent: compile FAILED after the patch — reverting.");
            rollBack(applier, record, "compile failed after patch");
            session.markSignatureFailed(chosen.signature(testId, failure.step));
            System.out.println("SelfHealingAgent: STOPPED for this step. Manual investigation needed for: " + failure);
            return;
        }

        System.out.println("SelfHealingAgent: re-running " + simpleName + " (this drives the real app again)...");
        Instant rerunStart = Instant.now();
        int rerunExit = runMaven("-q", "-Dtest=" + testSelector(className), "test");

        List<FailureRecord> after = ctx.scanner.scan(testId);
        boolean targetStillFailing = after.stream().anyMatch(r -> r.step.equals(failure.step));
        List<String> newRegressions = after.stream().map(r -> r.step)
                .filter(step -> !step.equals(failure.step) && !originalFailingSteps.contains(step))
                .distinct().collect(Collectors.toList());

        // Positive evidence: a KEEP needs the re-run to have exited cleanly AND to have written, since it started, a PASS
        // row for the target step. No FAIL row is not enough - a missing step, missing output or crashed run proves nothing.
        String evidenceProblem = rerunExit != 0 ? "the re-run's Maven exit code was " + rerunExit : null;
        if (evidenceProblem == null) {
            FailureScanner.RunEvidence ev = ctx.scanner.evidenceSince(testId, rerunStart);
            if (!ev.freshOutputFound) evidenceProblem = "the re-run wrote no new test result for this test";
            else if (!ev.passedSteps.contains(failure.step)) evidenceProblem = "the re-run recorded no PASS result for step \"" + failure.step + "\"";
        }

        record.targetStepResult = targetStillFailing ? "FAIL" : evidenceProblem == null ? "PASS" : "UNVERIFIED";
        boolean regressionOk = !targetStillFailing && newRegressions.isEmpty() && evidenceProblem == null;

        // "After" screenshot for the report — the re-run above already overwrote the standard test report, so
        // this now reflects the fresh state. Prefer whichever step is actually still/newly wrong (that's the
        // "failure after healing" a human needs to see); fall back to the healed step's own (now passing)
        // screenshot when everything is clean. Best-effort — never blocks the real verification.
        String retestStepForShot = targetStillFailing ? failure.step : !newRegressions.isEmpty() ? newRegressions.get(0) : failure.step;
        String afterShot = ScreenshotExtractor.extractStepScreenshot(ctx.outDir, testId, retestStepForShot);
        record.retestScreenshotStep = retestStepForShot;
        record.retestScreenshotBase64 = afterShot == null ? "" : afterShot;

        // Requirement 6 — where safely determinable (a dependent test file this project's own convention
        // marks "read-only"), also run the impacted regression test(s) before declaring success.
        List<String> impactedFailed = List.of();
        if (regressionOk && impact != null && !impact.readOnlyDependents.isEmpty()) {
            impactedFailed = runImpactedRegressionTests(impact, className, ctx.scanner);
            if (!impactedFailed.isEmpty()) {
                regressionOk = false;
                System.out.println("SelfHealingAgent: impacted read-only regression test(s) FAILED after the "
                        + "patch: " + impactedFailed + " — reverting.");
            }
        }

        record.downstreamRegression = (newRegressions.isEmpty() && impactedFailed.isEmpty()) ? "NONE"
                : joinAll(newRegressions, impactedFailed);

        if (!regressionOk) {
            if (targetStillFailing) System.out.println("SelfHealingAgent: step \"" + failure.step + "\" still FAILS after the patch — reverting.");
            if (!newRegressions.isEmpty()) System.out.println("SelfHealingAgent: the patch introduced NEW failing step(s) downstream: " + newRegressions + " — reverting.");
            if (!targetStillFailing && evidenceProblem != null) System.out.println("SelfHealingAgent: no positive PASS evidence (" + evidenceProblem + ") — reverting.");
            System.out.println("Verification failed.");
            String why = targetStillFailing ? "target step still fails"
                    : !newRegressions.isEmpty() ? ("downstream regression: " + newRegressions)
                    : evidenceProblem != null ? ("insufficient PASS evidence - " + evidenceProblem)
                    : ("impacted regression test(s) failed: " + impactedFailed);
            if (rerunExit != 0 && evidenceProblem != null && !why.contains("exit code")) why += " (the re-run's Maven exit code was " + rerunExit + ")";
            rollBack(applier, record, why);
            session.markSignatureFailed(chosen.signature(testId, failure.step));
            System.out.println("SelfHealingAgent: STOPPED for this step. Manual investigation needed for: " + failure);
        } else {
            System.out.println("Verification passed.");
            System.out.println("Patch kept: " + loc.pageObjectFile);
            record.finalStatus = "KEPT";
            session.markGroupHealed(groupKey);

            ctx.resolutionTracker.recordFix(
                    ResolutionTracker.signatureFor(testId, failure.step),
                    "Self-Healing Agent (automated, human-approved)",
                    "Automation Tester",
                    proposal.reason,
                    "PASS",
                    "RESOLVED");

            // Verification is COMPLETE and the tracker row is written: only now refresh the Failure Analysis report /
            // summary / index so they show the resolution. Never reached for a declined, refused or reverted patch.
            refreshFailureAnalysisReport(ctx, className, faSnapshot, record);
        }

        AdaptiveConfidenceScorer.Result finalAdaptive = ctx.scorer.score(proposal, failure, sh, regressionOk, impact, prior, semantic);
        record.adaptiveScore = finalAdaptive.score;
        record.adaptiveLevel = finalAdaptive.level.name();
    }

    /**
     * Rolls an applied patch back and records the outcome truthfully. {@code REVERTED} (and {@code reverted=true}) only when
     * {@link PatchApplier#revert()} restored the file AND verified it; otherwise {@code RESTORE_FAILED}, {@code reverted=false},
     * and the reason says plainly that the source file MAY STILL CONTAIN THE PATCH. Never claims a restoration it did not verify.
     */
    private static void rollBack(PatchApplier applier, HealingAttemptRecord record, String why) {
        PatchApplier.RestoreResult result;
        try {
            result = applier.revertVerified();
        } catch (Throwable t) {
            System.out.println("SelfHealingAgent: restoration threw " + t);
            result = null;
        }
        // verified = the file's bytes were confirmed (SHA-256) to equal the exact pre-patch bytes; nothing weaker counts
        boolean restored = result != null && result.verified;
        record.restorationVerified = restored;
        record.reverted = restored;
        record.restorationMethod = restored ? result.method : "";
        if (restored) {
            record.finalStatus = "REVERTED";
            record.reason = why;
            return;
        }
        record.finalStatus = "RESTORE_FAILED";
        record.reason = why + " | RESTORE_FAILED: the original file could not be restored and verified"
                + (result == null || result.detail.isBlank() ? "" : " (" + result.detail + ")") + ", so "
                + record.affectedFile + " MAY STILL CONTAIN THE PATCH. Restore it manually from the backup"
                + (record.backupPath.isBlank() ? "." : " " + record.backupPath + ".");
        System.out.println("SelfHealingAgent: RESTORE_FAILED - " + record.affectedFile + " MAY STILL CONTAIN THE PATCH. "
                + "Restore it manually from the backup" + (record.backupPath.isBlank() ? "." : ": " + record.backupPath));
    }

    /** Takes the pre-patch Failure Analysis snapshot; never lets a snapshot problem block the healing flow itself. */
    private static List<FailureAnalysisRecord> snapshotFailureAnalysis(String outDir, String testId, String fqcn) {
        try {
            return FailureAnalysisAgent.snapshotBeforeFix(outDir, testId, fqcn);
        } catch (Throwable t) {
            System.out.println("SelfHealingAgent: could not snapshot the Failure Analysis before the patch (" + t
                    + ") - the Failure Analysis report will not be refreshed if the fix is kept.");
            return null;
        }
    }

    /**
     * After a verified KEEP: re-generates the Failure Analysis report / summary / index from the pre-patch snapshot (via
     * the existing {@link FailureAnalysisAgent#refreshAfterFix}). A problem here NEVER changes the code-fix outcome: the
     * attempt stays KEPT and the problem is recorded in {@code record.reason} (audit trail, Technical Details, unified report).
     */
    private static void refreshFailureAnalysisReport(SharedContext ctx, String fqcn, List<FailureAnalysisRecord> snapshot,
                                                     HealingAttemptRecord record) {
        try {
            FailureAnalysisReport.Result r = FailureAnalysisAgent.refreshAfterFix(ctx.outDir, fqcn, snapshot);
            if (r == null) {
                record.reason = "Failure Analysis report NOT refreshed (no pre-fix analysis was available or the report files "
                        + "could not be written); the code fix itself was verified and kept.";
                System.out.println("SelfHealingAgent: " + record.reason);
            } else {
                record.reason = "Failure Analysis report refreshed after the verified fix: " + r.summaryPath;
                System.out.println("SelfHealingAgent: Failure Analysis report refreshed - " + r.summaryPath);
            }
        } catch (Throwable t) {
            record.reason = "Failure Analysis report refresh FAILED (" + t + "); the code fix itself was verified and kept.";
            System.out.println("SelfHealingAgent: " + record.reason);
        }
    }

    // =======================================================================
    // Chat-driven two-phase mode
    // =======================================================================

    private static Path sessionFileFor(String outDir, String testId) {
        return Paths.get(outDir, "self-healing", "session", safeFileName(testId) + ".session.properties");
    }

    private static String safeFileName(String s) {
        String cleaned = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "test" : cleaned;
    }

    /** Phase 1 of the chat-driven flow — analyzes the NEXT actionable failing step for one test (auto-skipping
     *  past NEEDS_TRIAGE steps, exactly like the interactive loop does across iterations) and STOPS the moment
     *  a real candidate is ready, instead of prompting. Never touches {@code PatchApplier}. Writes a {@link
     *  PendingHeal} file and prints "PENDING_HEAL_FILE: &lt;path&gt;" so the calling Claude Code session can
     *  find it, relay the proposal to the human in chat, and later invoke {@code apply-pending}. */
    private static void proposeMode(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        boolean dryRun = false;
        for (String a : args) {
            if ("--dry-run".equalsIgnoreCase(a)) dryRun = true;
            else positional.add(a);
        }
        if (positional.isEmpty()) {
            System.out.println("Usage: SelfHealingAgent propose <fully.qualified.TestClassName> [outDir]");
            return;
        }
        String className = positional.get(0);
        String outDir = positional.size() > 1 ? positional.get(1) : "test-output";

        Path testFile = CodeLocator.sourceRoot().resolve(className.replace('.', '/') + ".java");
        if (!Files.isRegularFile(testFile)) {
            System.out.println("SelfHealingAgent: test source not found: " + testFile);
            return;
        }
        String testSrc = Files.readString(testFile);
        Matcher idm = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(testSrc);
        if (!idm.find()) {
            System.out.println("SelfHealingAgent: could not find the testId (super(\"...\")) in " + testFile);
            return;
        }
        String testId = idm.group(1);
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        System.out.println("SelfHealingAgent (propose): target " + className + " (testId=" + testId + ")");

        int maxAttempts = cfgInt("heal.maxAttempts", "HEAL_MAX_ATTEMPTS", 3);
        GitStatus git = GitStatus.capture();
        LlmConfig llmConfig = LlmConfig.fromEnvironment();
        LlmProvider llmProvider = llmConfig.enabled ? new HttpLlmProvider(llmConfig) : new NoOpLlmProvider();
        LlmFallbackProposer llmFallback = new LlmFallbackProposer(llmProvider);
        SharedContext ctx = new SharedContext(outDir, git, llmProvider, llmFallback, maxAttempts);

        Path sessionFile = sessionFileFor(outDir, testId);
        HealingSession session = HealingSession.load(sessionFile, maxAttempts, dryRun);
        System.out.println("SelfHealingAgent (propose): attempt budget " + session.attempts + "/" + maxAttempts
                + (dryRun ? " (dry-run: not enforced)" : "") + " so far for this test.");

        List<FailureRecord> initialFailures = ctx.scanner.scan(testId);
        if (initialFailures.isEmpty()) {
            System.out.println("SelfHealingAgent (propose): no FAILED steps found for testId=" + testId
                    + " under " + outDir + "/aggregate-data. Nothing to propose.");
            return;
        }
        Set<String> originalFailingSteps = initialFailures.stream().map(f -> f.step).collect(Collectors.toSet());

        AnalyzeOutcome outcome = null;
        while (session.underAttemptBudget()) {
            AnalyzeOutcome o = analyzeNextActionableStep(className, testId, ctx, session, false);
            if (o == null) break;
            if (o.triageRecord != null) continue; // auto-skip past triage-only steps, matching interactive behaviour
            outcome = o;
            break;
        }
        session.save(sessionFile);

        if (outcome == null) {
            System.out.println();
            System.out.println("SelfHealingAgent (propose): no actionable candidate right now for " + simpleName
                    + " — either every remaining failure needs human triage, or the attempt budget is exhausted.");
            System.out.println("NO_PENDING_PROPOSAL");
            return;
        }

        PatchProposer.Proposal proposal = outcome.chosen.proposal;
        PendingHeal pending = new PendingHeal();
        pending.outDir = outDir;
        pending.testId = testId;
        pending.testClass = className;
        pending.simpleName = simpleName;
        pending.step = outcome.failure.step;
        pending.affectedFile = outcome.loc.pageObjectFile.toString();
        pending.affectedMethod = outcome.loc.methodName + "()";
        pending.proposalSource = proposal.source.name();
        pending.proposalKind = proposal.kind.name();
        pending.oldSnippet = proposal.oldSnippet;
        pending.newSnippet = proposal.newSnippet;
        pending.reason = proposal.reason;
        pending.confidence = proposal.confidence;
        pending.risk = outcome.rca.risk;
        pending.rootCause = outcome.rca.rootCause;
        pending.evidence = outcome.rca.evidence;
        pending.semanticValidation = outcome.semantic.valid ? "PASSED" : "REJECTED";
        pending.semanticJustification = outcome.semantic.valid ? outcome.semantic.justification : outcome.semantic.reason;
        pending.adaptiveScore = outcome.adaptive.score;
        pending.adaptiveLevel = outcome.adaptive.level.name();
        pending.impactSummary = outcome.impactSummary;
        pending.groupKey = outcome.groupKey;
        pending.originalFailingSteps = new ArrayList<>(originalFailingSteps);
        pending.candidateIndex = outcome.chosenIndex;
        pending.candidateTotal = outcome.filteredSize;
        pending.attemptNumber = outcome.attemptNumber;
        pending.attemptsMax = maxAttempts;
        pending.sessionFile = sessionFile.toString();
        // Bind the proposal to the exact source file and failure evidence it was made from, and seal it: apply-pending will
        // refuse a modified proposal, a changed source file, changed evidence, or an approval quoting a different hash.
        pending.seal(outcome.loc.pageObjectFile,
                PendingHeal.hashFailure(outcome.failure.step, outcome.failure.expected, outcome.failure.actual));

        Path pendingDir = Paths.get(outDir, "self-healing", "pending");
        Path pendingFile = pendingDir.resolve(safeFileName(testId) + "__" + safeFileName(outcome.failure.step) + ".properties");

        // P2 fix (pending-proposal audit safety) — the filename above is derived only from testId+step, so a
        // second "propose" for the SAME test+step would otherwise silently overwrite an existing, still-
        // unresolved pending proposal before a human ever got to decide on it (this actually happened once,
        // mid-session, before this check existed). Never overwrite: if an unresolved proposal is already
        // sitting there, refuse and point at it — exactly the same "safe direction to err" every other gate
        // in this class already takes (loop protection, quality guard, protected-resource policy). Once that
        // proposal is resolved (apply-pending, approved OR declined), apply-pending itself deletes the file
        // (see applyPendingMode), so this check only ever blocks a GENUINELY still-pending decision.
        if (Files.isRegularFile(pendingFile)) {
            System.out.println();
            System.out.println("SelfHealingAgent (propose): an UNRESOLVED pending proposal already exists for "
                    + "this exact test + step — refusing to overwrite it.");
            System.out.println("Existing pending proposal: " + pendingFile);
            System.out.println("Resolve it first — apply-pending <path> approved|declined — before requesting "
                    + "a new proposal for this step.");
            System.out.println("PENDING_PROPOSAL_ALREADY_EXISTS: " + pendingFile);
            return;
        }

        Files.createDirectories(pendingDir);
        pending.save(pendingFile);

        System.out.println();
        System.out.println("SelfHealingAgent (propose): a candidate is ready for human approval.");
        System.out.println("Semantic Validation: " + pending.semanticValidation + " — " + pending.semanticJustification);
        System.out.println("This is analysis only so far — nothing has been written, no backup has been taken.");
        System.out.println("PENDING_HEAL_FILE: " + pendingFile);
        System.out.println("PROPOSAL_HASH: " + pending.proposalHash);
        System.out.println("To approve THIS exact proposal: apply-pending \"" + pendingFile + "\" approved " + pending.proposalHash);
    }

    /** Phase 2 of the chat-driven flow — reads a {@link PendingHeal} written by {@code propose}, and applies
     *  it ONLY if {@code decision} is exactly {@code approved} (case-insensitive) — anything else (typically
     *  {@code declined}) leaves the source file untouched, exactly like any non-"y"/"Y" answer at the
     *  interactive prompt. Runs through the IDENTICAL backup/apply/compile/rerun/verify/keep-or-revert pipeline
     *  as the interactive path, via the shared {@link #finishApplyAndVerify} helper, then regenerates this
     *  test's reports and persists the (possibly updated) session state. */
    private static void applyPendingMode(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: SelfHealingAgent apply-pending <pendingFile> approved <proposalHash> | declined | rejected [reason...]");
            return;
        }
        Path pendingFile = Paths.get(args[0]);
        String decision = args[1];
        boolean approved = "approved".equalsIgnoreCase(decision);
        boolean rejected = "rejected".equalsIgnoreCase(decision);
        boolean declined = "declined".equalsIgnoreCase(decision);
        if (!approved && !rejected && !declined) {
            System.out.println("SelfHealingAgent (apply-pending): decision must be exactly 'approved', 'declined', "
                    + "or 'rejected' (got \"" + decision + "\") — treating as DECLINED for safety.");
        }
        // "rejected" carries an optional free-text reason (e.g. why this exact candidate must never be
        // re-offered) - everything after the decision word, joined back into one string.
        String rejectionReason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        if (!Files.isRegularFile(pendingFile)) {
            System.out.println("SelfHealingAgent (apply-pending): pending file not found: " + pendingFile);
            return;
        }
        PendingHeal pending;
        try {
            pending = PendingHeal.load(pendingFile);
        } catch (IOException | RuntimeException e) {
            System.out.println("SelfHealingAgent (apply-pending): REFUSED - the pending file cannot be read (" + e + "). Nothing was changed.");
            return;
        }

        // The output folder every artifact is written to. For an approval it is taken from where the pending file actually
        // lives (<outDir>/self-healing/pending/), never from a value inside the file; a file that disagrees is refused below.
        String outDir = pending.outDir;
        boolean outDirTampered = false;
        if (approved) {
            Path trusted = trustedOutDirOf(pendingFile);
            if (trusted == null) {
                System.out.println("SelfHealingAgent (apply-pending): REFUSED - " + pendingFile + " is not under "
                        + "<outDir>/self-healing/pending/, so it is not a proposal this agent created. Nothing was changed.");
                return;
            }
            if (!sameDir(trusted, pending.outDir)) outDirTampered = true;
            outDir = trusted.toString();
        }
        String approvalHash = approved && args.length > 2 ? PendingHeal.normalizeHash(args[2]) : "";

        int maxAttempts = pending.attemptsMax > 0 ? pending.attemptsMax : cfgInt("heal.maxAttempts", "HEAL_MAX_ATTEMPTS", 3);
        GitStatus git = GitStatus.capture();
        LlmConfig llmConfig = LlmConfig.fromEnvironment();
        LlmProvider llmProvider = llmConfig.enabled ? new HttpLlmProvider(llmConfig) : new NoOpLlmProvider();
        LlmFallbackProposer llmFallback = new LlmFallbackProposer(llmProvider);
        SharedContext ctx = new SharedContext(outDir, git, llmProvider, llmFallback, maxAttempts);

        // Always the session file propose itself derives from (outDir, testId) - never a path taken from inside the pending file.
        Path sessionFile = sessionFileFor(outDir, pending.testId);
        HealingSession session = HealingSession.load(sessionFile, maxAttempts, false);

        HealingAttemptRecord record = new HealingAttemptRecord();
        record.testId = pending.testId;
        record.testClass = pending.testClass;
        record.step = pending.step;
        // Report-only: the ORIGINAL failure's expected/actual text, for display in the healing report's
        // "Problem detected" row, plus the same FailureClassifier category the interactive path already shows
        // in Technical Details — both looked up fresh from the still-on-disk failure data (the source patch
        // hasn't been applied yet at this point, so this reflects the exact same failure "propose" analyzed)
        // rather than persisted through PendingHeal, and used ONLY for this record's own display fields —
        // never fed into the separate FailureRecord below that semantic validation/verification actually use,
        // so their behavior is unchanged. The synthetic CodeLocation from fallbackLocation() has a null
        // testFile, so FailureClassifier never reads a source file for this — purely a label derived from the
        // failure text itself, unaffected by the patch about to be applied. Best-effort: a lookup miss just
        // leaves these blank, as before.
        try {
            for (FailureRecord f : ctx.scanner.scan(pending.testId)) {
                if (f.step.equals(pending.step)) {
                    record.failureExpected = f.expected;
                    record.failureActual = f.actual;
                    CodeLocator.CodeLocation displayLoc = fallbackLocation(Paths.get(pending.affectedFile), pending.affectedMethod);
                    record.classification = ctx.classifier.classify(f, displayLoc).category.name();
                    break;
                }
            }
        } catch (IOException ignore) { /* display-only lookup; never blocks the real apply/verify below */ }
        record.affectedFile = pending.affectedFile;
        record.affectedMethod = pending.affectedMethod;
        record.source = pending.proposalSource;
        record.proposalKind = pending.proposalKind;
        record.candidateIndex = pending.candidateIndex;
        record.candidateTotal = pending.candidateTotal;
        record.confidence = pending.confidence;
        record.oldSnippet = pending.oldSnippet;
        record.newSnippet = pending.newSnippet;
        record.rootCause = pending.rootCause;
        record.risk = pending.risk;
        record.evidence = pending.evidence;
        record.semanticValidation = pending.semanticValidation.isBlank() ? "N/A" : pending.semanticValidation;
        record.semanticJustification = pending.semanticJustification;
        record.attemptNumber = pending.attemptNumber;
        record.attemptsMax = pending.attemptsMax;

        // Apply-time validation: an approval is honoured only for the exact, unmodified, still-current, still-eligible proposal
        // it quotes. Any failed check means NOTHING is written (no backup, no patch) and the reason is recorded.
        ApplyGate gate = approved
                ? validateBeforeApply(ctx, session, pending, approvalHash, maxAttempts, outDirTampered)
                : ApplyGate.OK;
        String refusal = gate.refusal;

        List<FailureAnalysisRecord> faSnapshot = null;
        PatchApplier applier = new PatchApplier(outDir);
        boolean applied = false;
        if (refusal == null) {
            // Failure Analysis of the ORIGINAL failure, taken before any patch is written (used only if the fix is KEPT).
            faSnapshot = snapshotFailureAnalysis(outDir, pending.testId, pending.testClass);
            applied = applier.confirmAndApply(Paths.get(pending.affectedFile), pending.oldSnippet, pending.newSnippet,
                    pending.reason, pending.confidence, approved);
        } else {
            System.out.println("SelfHealingAgent (apply-pending): REFUSED - " + refusal);
            System.out.println("SelfHealingAgent (apply-pending): nothing was changed and no backup was taken.");
            if (gate.keepPending) {
                System.out.println("SelfHealingAgent (apply-pending): the proposal is still pending. To approve THIS exact proposal: apply-pending \""
                        + pendingFile + "\" approved " + pending.proposalHash);
            }
        }
        // humanDecision mechanically reflects whether PatchApplier applied it (unchanged vocabulary, so
        // HealingDashboard's existing "a real healing attempt was made" check still recognizes this case);
        // "rejected" is a stronger, more specific outcome carried in finalStatus/reason below instead.
        record.humanDecision = applied ? "APPROVED" : "DECLINED";
        record.backupCreated = applier.lastBackupCreated;
        record.backupPath = applier.lastBackupPath == null ? "" : applier.lastBackupPath.toString();
        record.patchApplied = applied;

        List<HealingAttemptRecord> records = new ArrayList<>();
        if (!applied) {
            if (refusal != null) {
                // The approval was refused by the apply-time checks. The step is NOT marked exhausted, so a fresh, valid
                // proposal can still be made for it once the cause (changed file, stale evidence, ...) is dealt with.
                record.finalStatus = "NOT_APPLIED";
                record.reason = "APPLY REFUSED - " + refusal;
            } else if (rejected) {
                // The human has explicitly told us this exact candidate must never be re-offered (e.g. it
                // already failed verification before, in this session or a prior one) - loop-protect the
                // SPECIFIC signature (old/new snippet pair), the same mechanism a real post-apply verification
                // failure already uses, WITHOUT exhausting the whole step - so a genuinely NEW candidate,
                // backed by new evidence, can still surface for this same step in a future propose call.
                record.finalStatus = "REJECTED_VERIFICATION_FAILED";
                record.reason = rejectionReason.isBlank()
                        ? "Rejected by human reviewer - this exact candidate must not be re-offered."
                        : rejectionReason;
                String sig = new HealingCandidate(reconstructProposal(pending), null).signature(pending.testId, pending.step);
                session.markSignatureFailed(sig);
            } else {
                record.finalStatus = "NOT_APPLIED";
                session.markStepExhausted(pending.testId, pending.step);
            }
            records.add(record);
            ctx.audit.append(record);
        } else {
            // The file has ALREADY been patched by confirmAndApply above by this point, so re-running
            // CodeLocator against the new source text would be unreliable — build the location directly from
            // what "propose" already captured (the whole point of persisting it) instead of re-deriving it.
            PatchProposer.Proposal proposal = reconstructProposal(pending);
            Path affectedFile = Paths.get(pending.affectedFile);
            CodeLocator.CodeLocation loc = fallbackLocation(affectedFile, pending.affectedMethod);
            HealingCandidate chosen = new HealingCandidate(proposal, loc);
            FailureRecord failure = new FailureRecord(pending.testId, "", pending.step, "", "");
            HealingMemory.Summary prior = ctx.memory.forLocation(pending.affectedFile, stripParens(pending.affectedMethod));
            // Recomputed rather than persisted-and-reconstructed - semantic validation is a pure function of
            // (proposal, failure), so this is guaranteed to reproduce exactly what "propose" already showed.
            SemanticValidator.Result semantic = ctx.semanticValidator.validate(proposal, failure);

            // The SAME dependency/impact analysis the interactive path runs (analyzeNextActionableStep), so an approved
            // chat-driven patch also gets the dependent read-only regression tests after the target test passes. It
            // reads only the Page Object's file NAME and scans the test tree, so it is unaffected by the patch above.
            ImpactAnalyzer.Result impact = ctx.impactAnalyzer.analyze(loc.pageObjectFile);

            finishApplyAndVerify(ctx, session, pending.testClass, pending.testId, pending.simpleName, failure, loc,
                    chosen, proposal, impact, ctx.history.forStep(pending.testId, pending.step), prior,
                    pending.groupKey, new LinkedHashSet<>(pending.originalFailingSteps), applier, record, semantic, faSnapshot);
            records.add(record);
            ctx.audit.append(record);
        }
        if (refusal == null) session.save(sessionFile); // a refused approval changes no session state (and never writes to a file-supplied path)

        System.out.println();
        System.out.println("SelfHealingAgent (apply-pending): final result = " + record.finalStatus);

        HealingDashboard.Result reportResult = HealingDashboard.write(outDir, pending.simpleName, git,
                List.of(), records, false, maxAttempts);
        if (reportResult != null) {
            System.out.println();
            for (Path p : reportResult.individualReportPaths.values()) {
                System.out.println("Individual report:");
                System.out.println(p);
            }
            for (Path p : reportResult.healingReportPaths.values()) {
                System.out.println("Healing report:");
                System.out.println(p);
            }
            System.out.println("Overall summary:");
            System.out.println(reportResult.summaryReportPath);
        }

        // Housekeeping — the pending proposal has now been resolved (either applied-and-verified, or
        // declined); remove it so a stale file can never be re-applied by accident.
        // A refused proposal is moved aside (kept for inspection, never deleted) so a fresh proposal can be made; an approval
        // that merely quoted the wrong/missing hash leaves the intact proposal waiting for the correct approval.
        if (refusal != null) {
            if (!gate.keepPending) moveRefusedProposalAside(pendingFile);
        } else {
            try { Files.deleteIfExists(pendingFile); } catch (IOException ignore) { }
        }

        // Unified report - read-only over the existing artifacts, never throws, never affects the result above.
        // Runs AFTER the pending file is removed so it reports the final, resolved state.
        UnifiedHealingReport.generateQuietly(outDir, pending.testId);
    }

    // =======================================================================
    // Apply-time proposal validation
    // =======================================================================

    /** Outcome of {@link #validateBeforeApply}: {@code refusal == null} means every check passed. */
    private static final class ApplyGate {
        static final ApplyGate OK = new ApplyGate(null, false);
        final String refusal;
        /** True when the proposal itself is intact and only the approval was wrong, so it stays pending. */
        final boolean keepPending;

        private ApplyGate(String refusal, boolean keepPending) { this.refusal = refusal; this.keepPending = keepPending; }
        static ApplyGate refuse(String why) { return new ApplyGate(why, false); }
        static ApplyGate refuseKeepPending(String why) { return new ApplyGate(why, true); }
    }

    /**
     * Everything that must still hold at the moment a human's approval is about to be acted on, checked BEFORE a backup is taken
     * or a byte is written. Reuses the existing components (ProtectedResourcePolicy, PatchQualityGuard, SemanticValidator,
     * FailureClassifier, CodeLocator, HealingSession loop protection / attempt budget) rather than adding new rules:
     * <ol>
     *   <li>the proposal is well-formed and unmodified since it was created (recorded SHA-256);</li>
     *   <li>the approval quotes THIS proposal's hash, so it cannot authorize any other proposal;</li>
     *   <li>the target is not a protected resource, and is exactly the file the locator resolves for the step;</li>
     *   <li>the target source file is byte-identical to what the proposal was made from;</li>
     *   <li>the failure evidence is unchanged and the step is still failing;</li>
     *   <li>the patch still passes the quality guard, semantic validation and the auto-heal classification;</li>
     *   <li>loop protection and the attempt budget still permit it.</li>
     * </ol>
     */
    private static ApplyGate validateBeforeApply(SharedContext ctx, HealingSession session, PendingHeal pending,
                                                 String approvalHash, int maxAttempts, boolean outDirTampered) {
        // 1. integrity of the proposal itself
        List<String> problems = pending.structuralProblems();
        if (!problems.isEmpty()) return ApplyGate.refuse("the pending proposal is malformed: " + String.join("; ", problems) + ".");
        if (outDirTampered) return ApplyGate.refuse("the pending proposal names an output folder other than the one it is stored in.");
        if (!pending.computeProposalHash().equals(pending.proposalHash)) {
            return ApplyGate.refuse("the pending proposal was modified after it was created (its content no longer matches its recorded hash).");
        }

        // 2. the approval must quote this exact proposal
        if (approvalHash.isEmpty()) {
            // (only a short prefix goes into the recorded reason: the audit trail redacts long hex strings as possible secrets)
            return ApplyGate.refuseKeepPending("the approval did not quote a valid proposal hash (this proposal's hash starts "
                    + pending.proposalHash.substring(0, 12) + "). The approval must name the proposal it covers.");
        }
        if (!approvalHash.equals(pending.proposalHash)) {
            return ApplyGate.refuseKeepPending("the approval quotes a different proposal's hash (" + approvalHash.substring(0, 12)
                    + "...), not this proposal's (" + pending.proposalHash.substring(0, 12) + "...). An approval covers only the proposal it names.");
        }

        Path file = Paths.get(pending.affectedFile);

        // 3. protected resources are never modified
        if (ProtectedResourcePolicy.isProtected(file)) {
            return ApplyGate.refuse(file.getFileName() + " is a protected resource - never automatically modified.");
        }

        // 3b. an earlier rollback of this file that could not be verified must be dealt with first
        String unresolvedRestore = ctx.audit.unresolvedRestoreFailure(file);
        if (unresolvedRestore != null) return ApplyGate.refuse(unresolvedRestore + ".");

        // 4. the target file must be exactly what the proposal was made from
        if (!Files.isRegularFile(file)) return ApplyGate.refuse("the target source file no longer exists: " + file);
        try {
            if (!PendingHeal.hashFile(file).equals(pending.sourceSha256)) {
                return ApplyGate.refuse("the source file " + file.getFileName() + " has changed since the proposal was created. "
                        + "Re-run the test and request a new proposal.");
            }
        } catch (IOException e) {
            return ApplyGate.refuse("the target source file cannot be read (" + e.getMessage() + ").");
        }

        // 5. patch quality (size / shape / secrets)
        PatchQualityGuard.Result q = ctx.qualityGuard.check(pending.oldSnippet, pending.newSnippet);
        if (!q.ok) {
            System.out.println("SelfHealingAgent (apply-pending): quality guard detail: " + q.reason);
            return ApplyGate.refuse("the patch no longer passes the quality guard (size, shape or content check).");
        }

        // 6. loop protection and attempt budget (this proposal's own attempt is already counted in the session)
        HealingCandidate asCandidate = new HealingCandidate(reconstructProposal(pending), null);
        if (session.isSignatureTriedAndFailed(asCandidate.signature(pending.testId, pending.step))) {
            return ApplyGate.refuse("this exact patch was already tried and failed or was rejected (loop protection).");
        }
        if (session.isStepExhausted(pending.testId, pending.step)) {
            return ApplyGate.refuse("this step was already marked as needing manual triage (declined or exhausted).");
        }
        if (session.isGroupHealed(pending.groupKey)) {
            return ApplyGate.refuse("this failure's root-cause group was already healed - awaiting a re-scan, not another patch.");
        }
        if (pending.attemptNumber > maxAttempts || session.attempts > maxAttempts) {
            return ApplyGate.refuse("the healing attempt budget is exhausted (attempt " + Math.max(pending.attemptNumber, session.attempts)
                    + " of at most " + maxAttempts + ").");
        }

        // 7. the failure evidence must be the evidence the proposal was based on
        FailureRecord current = null;
        try {
            for (FailureRecord f : ctx.scanner.scan(pending.testId)) {
                if (f.step.equals(pending.step)) { current = f; break; }
            }
        } catch (IOException e) {
            return ApplyGate.refuse("the recorded test results cannot be read (" + e.getMessage() + ").");
        }
        if (current == null) {
            return ApplyGate.refuse("the recorded test results no longer show step \"" + pending.step + "\" as failing (stale evidence). "
                    + "Re-run the test and request a new proposal.");
        }
        if (!PendingHeal.hashFailure(current.step, current.expected, current.actual).equals(pending.failureEvidenceHash)) {
            return ApplyGate.refuse("the failure evidence for step \"" + pending.step + "\" has changed since the proposal was created (stale evidence). "
                    + "Request a new proposal.");
        }

        // 8. eligibility, re-derived exactly as propose derived it: located target, auto-heal classification, semantic validation
        CodeLocator.CodeLocation loc = ctx.locator.locate(pending.testClass, pending.step);
        if (loc == null) return ApplyGate.refuse("the responsible Page Object method can no longer be located for this step.");
        if (!sameFile(loc.pageObjectFile, file)) {
            return ApplyGate.refuse("the step resolves to " + loc.pageObjectFile.getFileName() + ", not the proposal's target " + file.getFileName() + ".");
        }
        if (!(loc.methodName + "()").equals(pending.affectedMethod)) {
            return ApplyGate.refuse("the step resolves to method " + loc.methodName + "(), not the proposal's " + pending.affectedMethod + ".");
        }
        FailureClassifier.Classification cls = ctx.classifier.classify(current, loc);
        if (cls.category != FailureClassifier.Category.CANDIDATE_FOR_AUTO_HEAL) {
            // plain wording, not the enum name: the audit trail redacts text containing words like "auth" as possible secrets
            return ApplyGate.refuse("the failure is now classified as " + plainCategory(cls.category) + " - never auto-fixed.");
        }
        // Failure Analysis must still mark THIS step eligible (the same per-step gate propose applies).
        if (!ctx.categoryClassifier.classify(current, loc).selfHealingEligible) {
            return ApplyGate.refuse("Failure Analysis no longer marks this step eligible for automatic healing.");
        }
        SemanticValidator.Result sv = ctx.semanticValidator.validate(reconstructProposal(pending), current);
        if (!sv.valid) {
            System.out.println("SelfHealingAgent (apply-pending): semantic validation detail: " + sv.reason);
            return ApplyGate.refuse("the patch no longer passes semantic validation (the replacement does not fit the field being fixed).");
        }

        return ApplyGate.OK;
    }

    private static String plainCategory(FailureClassifier.Category c) {
        switch (c) {
            case ENVIRONMENT_FILE_PATH: return "a local file-path / environment problem";
            case INFRASTRUCTURE_OR_AUTH_FAILURE: return "an infrastructure, sign-in or network problem";
            case VALIDATION_STATE_CHANGE: return "an element-state (enabled/visible/required) check";
            case INSUFFICIENT_EVIDENCE: return "a failure without enough evidence to fix safely";
            default: return c.name();
        }
    }

    /** {@code <outDir>} when the pending file sits at {@code <outDir>/self-healing/pending/<name>}, otherwise {@code null}. */
    private static Path trustedOutDirOf(Path pendingFile) {
        Path pending = pendingFile.toAbsolutePath().normalize().getParent();
        if (pending == null || !"pending".equals(String.valueOf(pending.getFileName()))) return null;
        Path selfHealing = pending.getParent();
        if (selfHealing == null || !"self-healing".equals(String.valueOf(selfHealing.getFileName()))) return null;
        return selfHealing.getParent();
    }

    private static boolean sameDir(Path trusted, String claimed) {
        if (claimed == null || claimed.isBlank()) return false;
        return trusted.toAbsolutePath().normalize().equals(Paths.get(claimed).toAbsolutePath().normalize());
    }

    private static boolean sameFile(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    /** Keeps a refused proposal for inspection (never deletes it) while freeing its slot so a new proposal can be made. */
    private static void moveRefusedProposalAside(Path pendingFile) {
        try {
            Path dir = pendingFile.toAbsolutePath().getParent().resolveSibling("refused-proposals");
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String name = pendingFile.getFileName().toString().replaceFirst("\\.properties$", "");
            Path target = dir.resolve(name + "." + stamp + ".properties");
            Files.move(pendingFile, target);
            System.out.println("SelfHealingAgent (apply-pending): the refused proposal was kept for inspection at " + target);
        } catch (IOException | RuntimeException e) {
            System.out.println("SelfHealingAgent (apply-pending): could not move the refused proposal aside (" + e.getMessage()
                    + "); delete or move " + pendingFile + " before requesting a new proposal.");
        }
    }

    /** Rebuilds the {@link PatchProposer.Proposal} {@code propose} already fully computed and persisted -
     *  {@code apply-pending} never re-derives a proposal from scratch, only replays the one a human already
     *  saw and approved. {@code PatchProposer.Proposal}'s full constructor is package-private, which
     *  {@code SelfHealingAgent} (same package, {@code com.kpj.heal}) can call directly - no new access class
     *  needed. */
    private static PatchProposer.Proposal reconstructProposal(PendingHeal p) {
        PatchProposer.Proposal.Kind kind;
        try { kind = PatchProposer.Proposal.Kind.valueOf(p.proposalKind); } catch (Exception e) { kind = PatchProposer.Proposal.Kind.LOCATOR; }
        PatchProposer.Proposal.Source source;
        try { source = PatchProposer.Proposal.Source.valueOf(p.proposalSource); } catch (Exception e) { source = PatchProposer.Proposal.Source.DETERMINISTIC; }
        PatchProposer.Proposal.Risk risk;
        try { risk = PatchProposer.Proposal.Risk.valueOf(p.risk); } catch (Exception e) { risk = null; }
        return new PatchProposer.Proposal(kind, p.confidence, p.oldSnippet, p.newSnippet, p.reason,
                source, p.rootCause, p.evidence, p.affectedFile, p.reason, risk);
    }

    private static String stripParens(String method) {
        return method.endsWith("()") ? method.substring(0, method.length() - 2) : method;
    }

    /** Builds a {@code CodeLocation} directly from what {@code propose} already persisted, rather than asking
     *  {@code CodeLocator} to re-resolve the method against source text that {@code confirmAndApply} has, by
     *  this point, already patched (which would be unreliable). {@code CodeLocation} is a package-private-
     *  constructed static nested class - {@code SelfHealingAgent} (same package) can build one directly. */
    private static CodeLocator.CodeLocation fallbackLocation(Path affectedFile, String affectedMethod) {
        return new CodeLocator.CodeLocation(null, affectedFile, "", stripParens(affectedMethod), "", "");
    }

    /** Requirement 6 (impacted regression tests) — runs each read-only dependent test (excluding the one just
     *  healed) and reports which, if any, now fail. Defensive by design: any single dependent that can't be
     *  resolved or run is skipped rather than aborting the whole check, since this only ever runs after a real
     *  patch has already been approved, applied, compiled, and passed its own target-step rerun. */
    private static List<String> runImpactedRegressionTests(ImpactAnalyzer.Result impact, String justHealedClassName,
                                                             FailureScanner scanner) {
        List<String> failed = new ArrayList<>();
        for (String depFile : impact.readOnlyDependents) {
            try {
                Path depPath = Paths.get(depFile);
                String depSrc = Files.readString(depPath);
                String depClassName = TestClassResolver.classNameFor(depPath);
                if (depClassName.equals(justHealedClassName)) continue;
                Matcher dm = Pattern.compile("super\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(depSrc);
                if (!dm.find()) continue;
                String depTestId = dm.group(1);
                System.out.println("SelfHealingAgent: running impacted read-only regression test: " + depClassName);
                Instant depStart = Instant.now();
                int depExit;
                try {
                    depExit = runMaven("-q", "-Dtest=" + testSelector(depClassName), "test");
                } catch (IOException | InterruptedException launch) {
                    if (launch instanceof InterruptedException) Thread.currentThread().interrupt();
                    System.out.println("SelfHealingAgent: impacted test " + depClassName + " could not be launched (" + launch
                            + ") - no evidence it still passes, so it counts as FAILED.");
                    failed.add(depClassName);
                    continue;
                }
                // No evidence is not a pass: a dependent counts as healthy only if its run exited cleanly AND wrote a fresh result.
                if (depExit != 0) {
                    System.out.println("SelfHealingAgent: impacted test " + depClassName + " exited with code " + depExit + " - counts as FAILED.");
                    failed.add(depClassName);
                    continue;
                }
                if (!scanner.evidenceSince(depTestId, depStart).freshOutputFound) {
                    System.out.println("SelfHealingAgent: impacted test " + depClassName + " wrote no new result - no evidence it passes, counts as FAILED.");
                    failed.add(depClassName);
                    continue;
                }
                List<FailureRecord> depAfter = scanner.scan(depTestId);
                if (!depAfter.isEmpty()) failed.add(depClassName);
            } catch (Exception e) {
                System.out.println("SelfHealingAgent: could not run impacted regression test for " + depFile
                        + " (" + e.getMessage() + ") — skipping it, not treated as a failure.");
            }
        }
        return failed;
    }

    /** Requirement (Claude Code UX layer) — dynamic {@code @agent} discovery: every test class this project
     *  actually has, resolved the same way {@code --all} resolves them, so this listing can never drift out
     *  of sync with what a real run would find. Read-only, no healing collaborators constructed. */
    private static void printAgentCatalog(String outDir) throws IOException {
        Map<String, String> testIdToClass = new TestClassResolver().resolveAll();
        Set<String> failingIds;
        try { failingIds = new FailureScanner(outDir).listFailingTestIds(); }
        catch (IOException e) { failingIds = Set.of(); }

        List<String> testIds = new ArrayList<>(testIdToClass.keySet());
        testIds.sort(Comparator.comparing(id -> simpleNameOf(testIdToClass.get(id))));

        System.out.println("Available Self-Healing Agents:");
        System.out.println();
        int i = 1;
        for (String testId : testIds) {
            String fqcn = testIdToClass.get(testId);
            System.out.println(i + ". " + simpleNameOf(fqcn));
            System.out.println("   Test ID: " + testId);
            System.out.println("   Class: " + fqcn);
            System.out.println("   Status: " + (failingIds.contains(testId) ? "has recorded failure(s)" : "no recorded failures"));
            System.out.println();
            i++;
        }
        System.out.println("[Run Full Self-Healing]  -> --all");
        System.out.println("[Dry Run / Preview]      -> --all --dry-run");
        System.out.println();
        System.out.println(testIds.size() + " test class(es) discovered under src/test/java/com/kpj/tests"
                + " (" + failingIds.size() + " with a recorded failure).");
    }

    private static String simpleNameOf(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static String joinAll(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return String.join(", ", all);
    }

    private static String shortNames(List<String> filePaths) {
        List<String> names = new ArrayList<>();
        for (String p : filePaths) names.add(Paths.get(p).getFileName().toString());
        return String.join(", ", names);
    }

    private static String shortCommit(String commit) {
        return commit != null && commit.length() > 10 ? commit.substring(0, 10) : commit;
    }

    private static int cfgInt(String propKey, String envKey, int def) {
        String v = System.getProperty(propKey);
        if (v == null || v.isBlank()) v = System.getenv(envKey);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    /**
     * The value for {@code -Dtest=} that selects EXACTLY one test class: its fully qualified name. A simple name matches every class
     * with that name - this project has duplicated simple names ({@code Transfer}, {@code Payor}, {@code ItemFactorizationTemplate}) and
     * every test class shares its simple name with a page class - so a simple name could re-run (or report on) the wrong class.
     * Surefire accepts the dotted fully qualified name. A class in the default package has no qualifier, so its name is used as is.
     */
    static String testSelector(String fqcn) {
        return fqcn == null ? "" : fqcn.trim();
    }

    /* package-private (was private) - reused by HealingWorkflowOrchestrator for the test-execution step, so
     * the automatic workflow launches Maven exactly the same way this class always has - no second copy. */
    static int runMaven(String... goalsAndArgs) throws IOException, InterruptedException {
        return mavenLauncher.run(goalsAndArgs);
    }

    /** How Maven is launched. Production ALWAYS uses {@link #launchMaven}; the seam exists only so tests can substitute a
     *  fake and exercise apply -> compile -> re-run -> verify -> KEEP/ROLLBACK without Maven, a browser or DevHIS. */
    interface MavenLauncher { int run(String... goalsAndArgs) throws IOException, InterruptedException; }

    static MavenLauncher mavenLauncher = SelfHealingAgent::launchMaven;

    /** Test seam (null in production): replaces the {@code mvn} executable prefix, so a synthetic test can exercise the real
     *  process launch with a harmless child program instead of Maven, a browser or DevHIS. */
    static volatile List<String> mavenExecutableOverride = null;

    /** The exact command line used to run Maven with {@code goalsAndArgs}. */
    static List<String> processCommand(String... goalsAndArgs) {
        List<String> cmd = new ArrayList<>();
        List<String> override = mavenExecutableOverride;
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (override != null) cmd.addAll(override);
        else if (windows) { cmd.add("cmd"); cmd.add("/c"); cmd.add("mvn"); }
        else cmd.add("mvn");
        cmd.addAll(Arrays.asList(goalsAndArgs));
        return cmd;
    }

    /** Runs Maven as a child process that INHERITS this JVM's environment and console (nothing is filtered or added, so
     *  {@code DEVHIS_OUTDIR}, {@code JAVA_TOOL_OPTIONS}, {@code LLM_*} and the rest reach the tests exactly as set), and returns
     *  its exit code. */
    static int launchMaven(String... goalsAndArgs) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(processCommand(goalsAndArgs)).inheritIO();
        // The agents' own Maven runs (test re-runs, compile, regression) are part of an already-controlled healing flow that reports
        // for itself; they must never start a second automatic post-run analysis. Everything else in the environment is untouched.
        pb.environment().put("DEVHIS_POSTRUN", "off");
        Process proc = pb.start();
        return proc.waitFor();
    }
}
