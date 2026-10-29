package com.kpj.heal;

import java.util.regex.Pattern;

/**
 * Requirement 8 - an extra validation layer applied to EVERY candidate (deterministic or LLM) right before
 * it's shown for approval, on top of {@link PatchApplier}'s exact-occurrence check and (for LLM proposals)
 * {@link LlmProposalValidator}. Rejects proposals that look oversized or suspicious, regardless of source.
 * A rejection here never modifies or applies anything — it just filters that one candidate out of the list
 * shown to the human, exactly like loop-protection filtering.
 *
 * <p>Deliberately does NOT attempt a brace-balance / "malformed Java" check: several of this framework's own
 * existing, already-verified V2 rules (e.g. the TIMING fix) intentionally produce a snippet spanning a
 * PARTIAL statement — that is normal and safe for a unique-text-replacement patch, but would look "unbalanced"
 * to a naive brace counter. Rejecting on that basis would have silently broken working V1/V2 healing, so this
 * guard sticks to checks that cannot false-positive against any proposal already in production use.</p>
 */
public class PatchQualityGuard {

    private static final int MAX_NEW_SNIPPET_CHARS = 4000;
    private static final int MAX_LINE_DELTA = 40;

    private static final Pattern DEBUG_STATEMENT = Pattern.compile("(?i)System\\.out\\.println\\(\\s*\"DEBUG");
    private static final Pattern TODO_MARKER = Pattern.compile("(?i)//\\s*(TODO|FIXME|XXX)\\b");
    private static final Pattern IMPORT_STATEMENT = Pattern.compile("(?m)^\\s*import\\s+[\\w.]+\\s*;");
    private static final Pattern SUSPICIOUS_SECRET = Pattern.compile(
            "(?i)password\\s*=\\s*['\"]|api[_-]?key\\s*=\\s*['\"]|secret\\s*=\\s*['\"]|bearer\\s+[a-z0-9._-]{10,}");

    public static class Result {
        public final boolean ok;
        public final String reason;

        private Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        static Result pass() { return new Result(true, ""); }
        static Result reject(String reason) { return new Result(false, reason); }
    }

    public Result check(String oldSnippet, String newSnippet) {
        if (newSnippet == null || newSnippet.isBlank()) return Result.reject("empty replacement");
        if (newSnippet.length() > MAX_NEW_SNIPPET_CHARS) {
            return Result.reject("proposed replacement is too large (" + newSnippet.length()
                    + " chars, max " + MAX_NEW_SNIPPET_CHARS + ")");
        }
        int oldLines = oldSnippet == null ? 0 : oldSnippet.split("\n", -1).length;
        int newLines = newSnippet.split("\n", -1).length;
        if (Math.abs(newLines - oldLines) > MAX_LINE_DELTA) {
            return Result.reject("line-count delta too large (" + oldLines + " -> " + newLines
                    + " lines, max delta " + MAX_LINE_DELTA + ")");
        }
        if (IMPORT_STATEMENT.matcher(newSnippet).find()) {
            return Result.reject("proposed change introduces an import statement — out of scope for a method-body patch");
        }
        if (DEBUG_STATEMENT.matcher(newSnippet).find()) return Result.reject("proposed change contains a debug print statement");
        if (TODO_MARKER.matcher(newSnippet).find()) return Result.reject("proposed change contains a TODO/FIXME marker");
        if (SUSPICIOUS_SECRET.matcher(newSnippet).find()) {
            return Result.reject("proposed change contains a suspicious hardcoded credential-like value");
        }
        return Result.pass();
    }
}
