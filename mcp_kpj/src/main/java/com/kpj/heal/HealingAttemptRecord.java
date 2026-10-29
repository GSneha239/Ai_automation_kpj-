package com.kpj.heal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * V4 - one full record of a single healing ATTEMPT (one candidate shown to a human for one failed step),
 * shared by {@link HealingAuditTrail} (persists it), {@link HealingDashboard} (summarizes/lists it), and
 * {@link HtmlDiffRenderer} (renders its before/after). Kept as ONE plain data holder rather than three
 * separate ad-hoc shapes, so nothing about a single attempt is computed or formatted more than once.
 *
 * <p>Every field is set as its corresponding step of the pipeline actually happens - most start at a "not
 * yet known" default (empty string / null / false) and are filled in as the attempt progresses, so a record
 * for an attempt that stopped early (e.g. declined, or no candidate at all) still prints/serializes cleanly.</p>
 */
public class HealingAttemptRecord {

    public final LocalDateTime timestamp = LocalDateTime.now();
    public String testId = "";
    public String testClass = "";
    public String step = "";
    public String failureExpected = "";
    public String failureActual = "";

    public String affectedFile = "";
    public String affectedMethod = "";
    /** {@link FailureClassifier.Category} name — set for every record, even one that never reaches a
     *  candidate (e.g. INFRASTRUCTURE_OR_AUTH_FAILURE), so the report can always show why. */
    public String classification = "";
    public String source = "";          // DETERMINISTIC / LLM / N-A
    public String proposalKind = "";    // LOCATOR / TIMING / WORKFLOW / LLM / N-A
    public int candidateIndex = 0;      // 1-based rank shown to the human, 0 if none
    public int candidateTotal = 0;
    public double confidence = -1;      // -1 = not applicable
    public Double adaptiveScore = null;
    public String adaptiveLevel = "";
    public String risk = "";
    public String rootCause = "";
    public List<String> evidence = new ArrayList<>();
    public String oldSnippet = "";
    public String newSnippet = "";
    /** {@link SemanticValidator} outcome for the candidate actually shown — "PASSED" / "REJECTED" / "N/A"
     *  (never evaluated, e.g. a triage-only record with no candidate at all). */
    public String semanticValidation = "N/A";
    public String semanticJustification = "";

    public String humanDecision = "N/A";     // APPROVED / DECLINED / SKIPPED_NO_CANDIDATE / SKIPPED_DUPLICATE / N/A
    public boolean backupCreated = false;
    public String backupPath = "";
    public boolean patchApplied = false;
    public String compileResult = "N/A";     // PASS / FAIL / N/A
    public String targetStepResult = "N/A";  // PASS / FAIL / N/A
    public String downstreamRegression = "N/A"; // NONE / <list> / N/A
    public String finalStatus = "N/A";       // KEPT / REVERTED / NOT_APPLIED / NEEDS_TRIAGE / DRY_RUN
    public boolean reverted = false;
    public String reason = "";

    /** This run's shared {@code HealingSession} attempt counter at the moment this candidate was chosen for
     *  review (0 if this record never consumed the attempt budget, e.g. triage-only or no-candidate). */
    public int attemptNumber = 0;
    /** The session's configured maximum attempts (for display as "attempt N of M"). */
    public int attemptsMax = 0;

    /** Base64 PNG of the application at the moment the ORIGINAL failure happened — extracted (best-effort)
     *  from the standard test report before the patch is applied/re-run. Empty if none could be found. */
    public String failureScreenshotBase64 = "";
    /** Base64 PNG captured from the RE-RUN after a patch was applied — the step shown depends on outcome:
     *  the healed step itself when it now passes (KEPT), or whichever step is still/newly failing when
     *  verification failed (REVERTED) — see {@code retestScreenshotStep} for which one this is. Empty if
     *  no patch was ever applied (e.g. declined, no candidate) or none could be found. */
    public String retestScreenshotBase64 = "";
    /** Which step's screenshot {@link #retestScreenshotBase64} actually is — the healed step's own name on
     *  KEPT, or the name of whichever step was still/newly failing on REVERTED. Empty if not applicable. */
    public String retestScreenshotStep = "";

    /** Whether {@link PatchApplier#revert()}'s restored file was verified to match the backup byte-for-byte —
     *  {@code null} when no rollback ever happened for this attempt (not applicable), otherwise the exact
     *  value that restore already computed. Reporting-only: this never influences KEEP/ROLLBACK/any other
     *  decision, it only lets a report state plainly whether a rollback's restoration was verified. */
    public Boolean restorationVerified = null;
    /** SHA-256 of the exact pre-patch source bytes (what a verified restoration must reproduce) - empty if no patch was applied. */
    public String originalSha256 = "";
    /** SHA-256 of the exact bytes the patch wrote. */
    public String patchedSha256 = "";
    /** How a verified restoration was achieved: BACKUP, MEMORY or ALREADY_RESTORED. Empty if no rollback was verified. */
    public String restorationMethod = "";
}
