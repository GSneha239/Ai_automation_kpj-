package com.kpj.heal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * V3 — a minimal, vendor-agnostic HTTP LLM provider built ONLY on the JDK's built-in {@code java.net.http}
 * (no new project dependency). POSTs a small JSON envelope ({@code model}/{@code system}/{@code prompt}) to
 * {@link LlmConfig#endpoint} with an {@code Authorization: Bearer <LLM_API_KEY>} header, and expects the
 * response BODY to directly contain the structured JSON object described in {@link LlmFallbackProposer} —
 * point {@code LLM_ENDPOINT} at a thin adapter/proxy if a specific provider's native response shape differs;
 * this class deliberately does not hardcode any one vendor's request/response schema.
 *
 * <p>Bounded timeout; any failure (network, non-2xx, interrupted) returns {@code null} rather than throwing
 * into the healing pipeline — a failed LLM call is treated exactly like "no proposal found", never a fatal
 * error for the run.</p>
 */
public class HttpLlmProvider implements LlmProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final LlmConfig config;
    private final HttpClient client;

    public HttpLlmProvider(LlmConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public boolean isEnabled() { return config.enabled; }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws IOException {
        if (!isEnabled()) return null;

        String body = "{"
                + "\"model\":\"" + jsonEscape(config.model) + "\","
                + "\"system\":\"" + jsonEscape(systemPrompt) + "\","
                + "\"prompt\":\"" + jsonEscape(userPrompt) + "\""
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                System.out.println("HttpLlmProvider: LLM endpoint returned HTTP " + response.statusCode()
                        + " — treating as no proposal.");
                return null;
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.out.println("HttpLlmProvider: call failed (" + e.getMessage() + ") — treating as no proposal.");
            return null;
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }
}
