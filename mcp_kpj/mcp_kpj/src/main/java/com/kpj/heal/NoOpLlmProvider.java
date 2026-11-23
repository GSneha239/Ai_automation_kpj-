package com.kpj.heal;

/**
 * V3 default provider — always disabled, always returns {@code null}. Used whenever {@link LlmConfig}
 * reports the LLM as not (fully) configured, so {@link SelfHealingAgent} never attempts a network call it
 * wasn't explicitly, completely configured for. V1/V2 deterministic healing is entirely unaffected by this
 * class's presence.
 */
public class NoOpLlmProvider implements LlmProvider {
    @Override public boolean isEnabled() { return false; }
    @Override public String complete(String systemPrompt, String userPrompt) { return null; }
}
