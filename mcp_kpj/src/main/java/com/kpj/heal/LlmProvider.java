package com.kpj.heal;

import java.io.IOException;

/**
 * V3 — abstraction over "ask an LLM for a structured root-cause analysis". Implementations must never be
 * invoked unless {@link #isEnabled()} is {@code true}. The default ({@link NoOpLlmProvider}) is always
 * disabled, so V1/V2 deterministic healing keeps working with zero LLM configuration and zero network calls.
 *
 * <p>An {@code LlmProvider} only ever ANALYSES and PROPOSES — it has no access to the filesystem and cannot
 * apply anything. See {@link LlmFallbackProposer} for how its response becomes a validated {@link
 * PatchProposer.Proposal}, and {@link PatchApplier} for the only component allowed to write a file.</p>
 */
public interface LlmProvider {

    boolean isEnabled();

    /**
     * Sends the prompt, returns the raw response text (expected to contain the structured JSON object
     * described in {@link LlmFallbackProposer}) or {@code null} if the call didn't produce one. Implementations
     * should catch their own transport errors and return {@code null} rather than propagate a routine failure —
     * callers already treat {@code null}/blank exactly like "no proposal found".
     */
    String complete(String systemPrompt, String userPrompt) throws IOException;
}
