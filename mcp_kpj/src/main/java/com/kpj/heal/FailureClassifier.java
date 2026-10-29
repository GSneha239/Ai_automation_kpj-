package com.kpj.heal;

import java.util.regex.Pattern;

/**
 * Classification step — runs BEFORE {@link PatchProposer}, and only for the outcomes that must never reach
 * an automatic patch:
 * <ul>
 *   <li>{@code ENVIRONMENT_FILE_PATH} — a hardcoded/missing local file path. Always report only (never
 *       auto-fixed — a machine-specific path is an environment problem, not an automation bug).</li>
 *   <li>{@code INFRASTRUCTURE_OR_AUTH_FAILURE} — a login/authentication/credential failure, an SSL/certificate
 *       error, a timeout, a network/DNS/connectivity failure, an environment/configuration failure, a
 *       database/infrastructure error, or a missing external resource. None of these are code-level UI
 *       defects a locator/timing/workflow patch could ever fix — always routed to human triage, never
 *       guessed at, regardless of what {@link CodeLocator} or the DOM capture found.</li>
 *   <li>{@code INSUFFICIENT_EVIDENCE} — no located method and/or no DOM capture. Not enough to reason about
 *       safely — always "needs human triage".</li>
 *   <li>{@code VALIDATION_STATE_CHANGE} — the step judges an element's enabled/visible/required STATE
 *       against an expectation. A mismatch here is equally consistent with a real application defect as
 *       with a stale automation assumption, and this system has no way to tell them apart — always routed
 *       to human triage, never guessed at.</li>
 * </ul>
 * Everything else is {@code CANDIDATE_FOR_AUTO_HEAL} and is handed to {@link PatchProposer}, whose
 * LOCATOR / TIMING / WORKFLOW rules make the actual, final call on whether (and how) to fix it. This class
 * deliberately does not duplicate that reasoning — it only decides whether to attempt it at all.
 */
public class FailureClassifier {

    public enum Category {
        ENVIRONMENT_FILE_PATH, INFRASTRUCTURE_OR_AUTH_FAILURE, VALIDATION_STATE_CHANGE,
        INSUFFICIENT_EVIDENCE, CANDIDATE_FOR_AUTO_HEAL
    }

    public static class Classification {
        public final Category category;
        public final String reason;

        Classification(Category category, String reason) {
            this.category = category;
            this.reason = reason;
        }
    }

    private static final Pattern HARD_PATH = Pattern.compile("[A-Za-z]:\\\\[^\"]+|/(?:Users|home)/[^\"]+");
    private static final Pattern FILE_MISSING_MSG =
            Pattern.compile("(?i)no such file|file not found|cannot find the (?:file|path)|enoent|no file attached");
    private static final Pattern VALIDATION_MSG = Pattern.compile("(?i)\\benabled\\b|\\bdisabled\\b|\\bvisible\\b|\\brequired\\b|\\bdefect\\b");
    private static final Pattern VALIDATION_PROBE =
            Pattern.compile("\\benabled\\b|\\bdisabled\\b|\\bvisible\\b|\\brequired\\b|getAttribute\\(['\"]disabled['\"]\\)");

    /** Login/credential, SSL/certificate, timeout, network/DNS, environment/config, database/infrastructure,
     *  and missing-external-resource failures — matched against the failure MESSAGE (not source code, unlike
     *  {@link #HARD_PATH}), since these are runtime/environment conditions that show up in what the app or
     *  the browser reported, not in a code pattern. Deliberately broad: a false positive here just routes a
     *  possibly-fixable issue to human triage, which is the safe direction to err in. */
    private static final Pattern INFRASTRUCTURE_OR_AUTH_MSG = Pattern.compile("(?i)"
            + "login failed|authentication failed|invalid credentials|unauthorized|access is denied|"
            + "\\bcertificate\\b|\\bssl\\b|err_cert|cert_authority_invalid|self.signed certificate|"
            + "\\btimeout\\b|timed out|"
            + "dns_probe|err_connection|err_name_not_resolved|err_internet_disconnected|net::err|connection refused|econnrefused|"
            + "environment variable|not configured|misconfigured|"
            + "\\bsql\\b|database connection|connection pool|deadlock|"
            + "err_file_not_found|resource not found|http 404|status code 404");

    /** {@code loc} may be {@code null} (CodeLocator couldn't identify the responsible method). */
    public Classification classify(FailureRecord failure, CodeLocator.CodeLocation loc) {
        String methodSource = loc != null ? loc.methodSource : "";
        // Bounded to the vicinity of THIS failure's own step(...) call (see CodeLocator) — never the whole
        // test file, so a hardcoded/local path used by a different, unrelated step elsewhere in the same
        // (possibly large, multi-section) test file never explains a failure it has nothing to do with.
        String nearbyTestSource = loc != null ? loc.nearbyTestSource : "";

        if (HARD_PATH.matcher(methodSource).find() || HARD_PATH.matcher(nearbyTestSource).find()
                || FILE_MISSING_MSG.matcher(failure.actual == null ? "" : failure.actual).find()) {
            return new Classification(Category.ENVIRONMENT_FILE_PATH,
                    "The failure message or the responsible code references a hardcoded/local file path — a "
                    + "machine-specific environment issue, not something safe to auto-patch.");
        }

        String rawMsg = (failure.expected == null ? "" : failure.expected) + " " + (failure.actual == null ? "" : failure.actual);
        if (INFRASTRUCTURE_OR_AUTH_MSG.matcher(rawMsg).find()) {
            return new Classification(Category.INFRASTRUCTURE_OR_AUTH_FAILURE,
                    "This looks like a login/authentication, certificate, timeout, network, environment, or "
                    + "infrastructure failure, not a UI/code defect — never auto-fixed, always routed to human triage.");
        }

        if (loc == null && !failure.hasDomCapture()) {
            return new Classification(Category.INSUFFICIENT_EVIDENCE,
                    "No DOM/URL capture and no located Page Object method — not enough evidence to reason about this failure.");
        }
        if (loc == null) {
            return new Classification(Category.INSUFFICIENT_EVIDENCE,
                    "Could not identify the responsible Page Object method for this step.");
        }
        if (!failure.hasDomCapture()) {
            return new Classification(Category.INSUFFICIENT_EVIDENCE,
                    "No DOM/URL capture for this step (older run, or it failed before a snapshot could be taken).");
        }

        String msg = (failure.expected == null ? "" : failure.expected) + " " + (failure.actual == null ? "" : failure.actual);
        if (VALIDATION_MSG.matcher(msg).find() && VALIDATION_PROBE.matcher(methodSource).find()) {
            return new Classification(Category.VALIDATION_STATE_CHANGE,
                    "This step judges an element's enabled/visible/required STATE against an expected value. That "
                    + "mismatch is equally consistent with a real application defect as with a stale automation "
                    + "assumption — never auto-fixed, always routed to human triage.");
        }

        return new Classification(Category.CANDIDATE_FOR_AUTO_HEAL, "No disqualifying signal found — handing to PatchProposer.");
    }
}
