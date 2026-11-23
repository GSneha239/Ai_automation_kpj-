package com.kpj.heal;

import java.time.LocalDateTime;

/**
 * One fully-categorized failed step, produced by the Failure Analysis Agent and rendered by {@link
 * FailureAnalysisReport}. Every business-facing field required by the report is here in one place, so nothing
 * is computed or formatted more than once. Resolution-tracking fields ({@code fixedBy}, {@code fixingRole},
 * {@code fixedDate}, {@code resolution}, {@code retestResult}, {@code finalStatus}) are read from {@link
 * ResolutionTracker} - defaulting to "Pending" - and are NEVER invented here; only {@code SelfHealingAgent}
 * ever writes a real resolution, after an actual human-approved, verified fix.
 */
public class FailureAnalysisRecord {

    public final LocalDateTime timestamp = LocalDateTime.now();
    public String testId = "";
    public String testClass = "";
    public String step = "";
    public String failureExpected = "";
    public String failureActual = "";

    public String category = "";
    public String failureDescription = "";
    public String evidenceAvailable = "";
    public String rootCause = "";
    public String severity = "";
    public String recommendedOwner = "";
    public String suggestedAction = "";
    public String currentStatus = "OPEN";

    public boolean selfHealingEligible = false;
    public String eligibilityReason = "";

    /** Base64 PNG of the application at the exact moment this step failed — extracted (best-effort, never
     *  invented) from the standard test report's own already-captured step screenshot via
     *  {@link ScreenshotExtractor}. Empty if none could be found. */
    public String screenshotBase64 = "";

    /** The Page Object file / method {@link CodeLocator} identified as responsible for this step — already
     *  computed by {@code FailureAnalysisAgent} for classification, now also surfaced here (display-only,
     *  Technical Details) instead of being discarded. Empty when {@code CodeLocator} could not resolve one. */
    public String affectedFile = "";
    public String affectedMethod = "";

    // Resolution tracking - populated from ResolutionTracker.forSignature(...); never invented here.
    public String fixedBy = "Pending";
    public String fixingRole = "Pending";
    public String fixedDate = "";
    public String resolution = "";
    public String retestResult = "";
    public String finalStatus = "OPEN";
}
