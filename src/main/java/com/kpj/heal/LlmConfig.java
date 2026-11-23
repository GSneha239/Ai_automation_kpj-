package com.kpj.heal;

/**
 * V3 — reads LLM configuration from JVM system properties / environment variables ONLY. No credential, key,
 * or endpoint is ever hardcoded in this codebase. Fails SAFE: unless the LLM is BOTH explicitly enabled
 * ({@code LLM_ENABLED=true}) AND fully configured (endpoint + API key present), {@link #enabled} is
 * {@code false} — a partial configuration never silently activates, and there is no default remote service.
 *
 * <p>Resolution order per key (matches {@code DevHisBase}'s own convention elsewhere in this project):
 * JVM system property, then environment variable, then a blank default.</p>
 */
public class LlmConfig {

    public final boolean enabled;
    public final String endpoint;
    public final String apiKey;
    public final String model;

    private LlmConfig(boolean enabled, String endpoint, String apiKey, String model) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Set by {@link PostRunCoordinator} for the duration of an AUTOMATIC post-run analysis. While set, the LLM fallback stays
     * off even if {@code LLM_ENABLED}/{@code LLM_ENDPOINT}/{@code LLM_API_KEY} are present in the environment, so failure details
     * are never sent to an external provider just because a test run finished. Opt in explicitly with
     * {@code -Ddevhis.postrun.llm=on} or {@code DEVHIS_POSTRUN_LLM=on}. The manual {@code @agent} path never sets this flag.
     */
    static volatile boolean suppressedForAutomaticPostRun = false;

    /** True only when the automatic post-run path has been explicitly opted in to the LLM fallback. */
    static boolean postRunLlmOptedIn() {
        String v = System.getProperty("devhis.postrun.llm");
        if (v == null || v.isBlank()) v = System.getenv("DEVHIS_POSTRUN_LLM");
        return v != null && "on".equalsIgnoreCase(v.trim());
    }

    public static LlmConfig fromEnvironment() {
        boolean wantEnabled = "true".equalsIgnoreCase(cfg("LLM_ENABLED", "false"));
        if (suppressedForAutomaticPostRun && !postRunLlmOptedIn()) wantEnabled = false;
        String endpoint = cfg("LLM_ENDPOINT", "");
        String apiKey = cfg("LLM_API_KEY", "");
        String model = cfg("LLM_MODEL", "");
        boolean fullyConfigured = !endpoint.isBlank() && !apiKey.isBlank();
        return new LlmConfig(wantEnabled && fullyConfigured, endpoint, apiKey, model);
    }

    private static String cfg(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) return v.trim();
        v = System.getenv(key);
        if (v != null && !v.isBlank()) return v.trim();
        return def;
    }
}
