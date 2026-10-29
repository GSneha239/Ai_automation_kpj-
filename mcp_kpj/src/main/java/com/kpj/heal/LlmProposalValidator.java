package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * V3 Step 4 — the safety gate an LLM's structured response must pass BEFORE it is ever allowed to become a
 * {@link PatchProposer.Proposal}. Deliberately conservative: any ambiguity, missing evidence, or out-of-scope
 * target rejects the proposal outright rather than guessing or narrowing it. Nothing here ever modifies a
 * file — a reject just means {@link LlmFallbackProposer} returns {@code null}, identical to a deterministic
 * rule finding nothing.
 */
public class LlmProposalValidator {

    public static class Result {
        public final boolean valid;
        public final String reason;
        private Result(boolean valid, String reason) { this.valid = valid; this.reason = reason; }
        static Result ok() { return new Result(true, ""); }
        static Result reject(String reason) { return new Result(false, reason); }
    }

    /**
     * @param resp         the parsed LLM response, or {@code null} if parsing itself already failed.
     * @param expectedFile the ONE file {@code CodeLocator} already identified as responsible for this
     *                     failure. The LLM may only propose a change to exactly THIS file — never a
     *                     different or additional one, however plausible-looking. This is the strictest
     *                     reading of "only the exact file represented by the final Proposal can be passed to
     *                     PatchApplier": the LLM cannot redirect the patch anywhere the deterministic
     *                     pipeline didn't already point.
     * @param projectRoot  the project's root directory, for path-traversal / outside-project checks.
     */
    public Result validate(LlmResponseParser.ParsedLlmResponse resp, Path expectedFile, Path projectRoot) {
        if (resp == null) return Result.reject("malformed response (not valid/parseable JSON)");
        if (resp.oldSnippet == null || resp.oldSnippet.isBlank()) return Result.reject("missing oldSnippet");
        if (resp.newSnippet == null || resp.newSnippet.isBlank()) return Result.reject("missing newSnippet");
        if (resp.oldSnippet.equals(resp.newSnippet)) return Result.reject("empty patch — oldSnippet and newSnippet are identical");
        if (resp.confidence < 0.0 || resp.confidence > 1.0) return Result.reject("missing or out-of-range confidence");
        if (resp.affectedFiles == null || resp.affectedFiles.size() != 1) {
            return Result.reject("must name exactly one affected file (got "
                    + (resp.affectedFiles == null ? 0 : resp.affectedFiles.size()) + ")");
        }

        String rawPath = resp.affectedFiles.get(0);
        if (rawPath == null || rawPath.isBlank()) return Result.reject("empty affected file path");
        if (rawPath.contains("..")) return Result.reject("path traversal (\"..\") in affected file path");
        if (!rawPath.toLowerCase().endsWith(".java")) return Result.reject("affected file is not a .java source file");
        if (ProtectedResourcePolicy.isProtected(Paths.get(rawPath))) {
            return Result.reject("affected file path references a protected/forbidden location");
        }

        Path resolved;
        try {
            Path candidate = Paths.get(rawPath);
            Path base = candidate.isAbsolute() ? candidate : projectRoot.resolve(candidate);
            resolved = base.toAbsolutePath().normalize();
        } catch (Exception e) {
            return Result.reject("invalid file path: " + e.getMessage());
        }
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            return Result.reject("affected file resolves outside the project root");
        }

        Path expectedNormalized = expectedFile.toAbsolutePath().normalize();
        if (!resolved.equals(expectedNormalized)) {
            return Result.reject("affected file (" + resolved + ") is not the file CodeLocator identified as "
                    + "responsible (" + expectedNormalized + ") — refusing to let the LLM redirect the patch elsewhere");
        }

        // oldSnippet must exist, and exist EXACTLY ONCE, in the actual current file — the same discipline
        // PatchApplier itself re-checks before writing anything. Checked here too so an invalid LLM proposal
        // never even reaches the diff/approval screen.
        String content;
        try { content = Files.readString(resolved); }
        catch (IOException e) { return Result.reject("cannot read affected file: " + e.getMessage()); }
        int count = countOccurrences(content, resp.oldSnippet);
        if (count == 0) return Result.reject("oldSnippet not found in the affected file — proposal doesn't match the real source");
        if (count > 1) return Result.reject("oldSnippet occurs " + count + " times in the affected file — ambiguous, refusing to guess which one");

        return Result.ok();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }
}
