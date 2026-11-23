package com.kpj.heal;

import java.nio.file.Path;
import java.util.List;

/**
 * Requirement 9 - shared policy for files that must NEVER be automatically modified, regardless of proposal
 * source. Previously this list of forbidden keywords lived only inside {@link LlmProposalValidator} (V3),
 * meaning it only ever protected the LLM path. Extracting it here lets {@code SelfHealingAgent} apply the
 * SAME policy to every deterministic candidate too — a genuine strengthening, not a duplication:
 * {@code LlmProposalValidator} now delegates to this class instead of keeping its own copy.
 */
public final class ProtectedResourcePolicy {
    private ProtectedResourcePolicy() {}

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "credential", "password", "secret", "config.properties", ".git", ".db", ".sqlite", ".env", "keystore");

    /** Specific files known to implement authentication/login logic in this codebase — protected by exact
     *  filename rather than a broad "auth" substring, which would risk blocking legitimate Page Objects
     *  whose business-domain name happens to contain that word. */
    private static final List<String> PROTECTED_FILENAMES = List.of("LoginPage.java", "DevHisBase.java");

    /** True if {@code file}'s path suggests it's a protected resource that must never be auto-modified. */
    public static boolean isProtected(Path file) {
        String lower = file.toString().toLowerCase().replace('\\', '/');
        for (String bad : FORBIDDEN_KEYWORDS) {
            if (lower.contains(bad)) return true;
        }
        String name = file.getFileName().toString();
        for (String protectedName : PROTECTED_FILENAMES) {
            if (protectedName.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static List<String> forbiddenKeywords() { return FORBIDDEN_KEYWORDS; }
}
