package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Requirement (chat-driven approval) — everything the {@code apply-pending} phase needs to finish a healing
 * attempt that the {@code propose} phase already fully analyzed, chose a candidate for, and showed to the
 * human (in Claude Code chat, not a terminal). Serialized to a small {@link Properties} file so the two
 * phases can be two entirely separate, non-interactive JVM invocations with no live process/stdin connecting
 * them — Claude Code itself is what carries the human's decision from chat into the second invocation's
 * command-line argument.
 *
 * <p>This is a data holder only - it never decides anything and never touches a source file. Multi-line
 * values (old/newSnippet, evidence) are stored as ordinary {@link Properties} values, which already correctly
 * escapes/restores embedded newlines.</p>
 */
public class PendingHeal {

    public String outDir = "test-output";
    public String testId = "";
    public String testClass = "";
    public String simpleName = "";
    public String step = "";

    public String affectedFile = "";
    public String affectedMethod = "";
    public String proposalSource = "";
    public String proposalKind = "";
    public String oldSnippet = "";
    public String newSnippet = "";
    public String reason = "";
    public double confidence = -1;
    public String risk = "";
    public String rootCause = "";
    public List<String> evidence = new ArrayList<>();
    public String semanticValidation = "";
    public String semanticJustification = "";
    public Double adaptiveScore = null;
    public String adaptiveLevel = "";
    public String impactSummary = "";
    public String groupKey = "";
    public List<String> originalFailingSteps = new ArrayList<>();
    public int candidateIndex = 1;
    public int candidateTotal = 1;
    public int attemptNumber = 0;
    public int attemptsMax = 0;
    public String sessionFile = "";
    public String createdAt = LocalDateTime.now().toString();

    /** SHA-256 of the target source file's exact bytes when the proposal was created. A patch is never applied to a file
     *  that no longer matches it. */
    public String sourceSha256 = "";
    /** SHA-256 of the failing step's recorded expected/actual text when the proposal was created - the evidence it was based on. */
    public String failureEvidenceHash = "";
    /** SHA-256 over EVERY other field of this proposal (see {@link #computeProposalHash()}). The human's approval is bound to
     *  this value, so an approval for one proposal can never authorize another, and an edited file is detectable. */
    public String proposalHash = "";

    // ------------------------------------------------------------------ integrity / approval binding

    private static final java.util.regex.Pattern SHA256_HEX = java.util.regex.Pattern.compile("[0-9a-f]{64}");

    /** Lower-case SHA-256 hex of {@code bytes}. */
    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** SHA-256 of a source file's exact bytes (line endings and encoding included). */
    public static String hashFile(Path file) throws IOException { return sha256Hex(Files.readAllBytes(file)); }

    /** The evidence hash for one failing step: its recorded expected and actual text. */
    public static String hashFailure(String step, String expected, String actual) {
        StringBuilder sb = new StringBuilder("failure/v1\n");
        field(sb, "step", step);
        field(sb, "expected", expected);
        field(sb, "actual", actual);
        return sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void field(StringBuilder sb, String name, String value) {
        String v = value == null ? "" : value;
        sb.append(name).append('=').append(v.length()).append(':').append(v).append('\n'); // length-prefixed: no field can bleed into the next
    }

    /** Hash over every field except {@code proposalHash} itself, in a fixed order with length-prefixed values. */
    public String computeProposalHash() {
        StringBuilder sb = new StringBuilder("proposal/v1\n");
        field(sb, "outDir", outDir);
        field(sb, "testId", testId);
        field(sb, "testClass", testClass);
        field(sb, "simpleName", simpleName);
        field(sb, "step", step);
        field(sb, "affectedFile", affectedFile);
        field(sb, "affectedMethod", affectedMethod);
        field(sb, "proposalSource", proposalSource);
        field(sb, "proposalKind", proposalKind);
        field(sb, "oldSnippet", oldSnippet);
        field(sb, "newSnippet", newSnippet);
        field(sb, "reason", reason);
        field(sb, "confidence", String.valueOf(confidence));
        field(sb, "risk", risk);
        field(sb, "rootCause", rootCause);
        field(sb, "evidence.count", String.valueOf(evidence.size()));
        for (int i = 0; i < evidence.size(); i++) field(sb, "evidence." + i, evidence.get(i));
        field(sb, "semanticValidation", semanticValidation);
        field(sb, "semanticJustification", semanticJustification);
        field(sb, "adaptiveScore", adaptiveScore == null ? "" : String.valueOf(adaptiveScore));
        field(sb, "adaptiveLevel", adaptiveLevel);
        field(sb, "impactSummary", impactSummary);
        field(sb, "groupKey", groupKey);
        field(sb, "originalFailingSteps.count", String.valueOf(originalFailingSteps.size()));
        for (int i = 0; i < originalFailingSteps.size(); i++) field(sb, "originalFailingSteps." + i, originalFailingSteps.get(i));
        field(sb, "candidateIndex", String.valueOf(candidateIndex));
        field(sb, "candidateTotal", String.valueOf(candidateTotal));
        field(sb, "attemptNumber", String.valueOf(attemptNumber));
        field(sb, "attemptsMax", String.valueOf(attemptsMax));
        field(sb, "sessionFile", sessionFile);
        field(sb, "createdAt", createdAt);
        field(sb, "sourceSha256", sourceSha256);
        field(sb, "failureEvidenceHash", failureEvidenceHash);
        return sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Records the source file's and the failure evidence's hashes and then seals the proposal. Call once, right before {@link #save}. */
    public void seal(Path targetSourceFile, String failureEvidence) throws IOException {
        this.sourceSha256 = hashFile(targetSourceFile);
        this.failureEvidenceHash = failureEvidence;
        this.proposalHash = computeProposalHash();
    }

    /** The lower-case hash if {@code candidate} (as typed in the approval command, optionally {@code sha256:}-prefixed) is a
     *  well-formed SHA-256, otherwise an empty string. */
    public static String normalizeHash(String candidate) {
        if (candidate == null) return "";
        String h = candidate.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.startsWith("sha256:")) h = h.substring(7);
        return SHA256_HEX.matcher(h).matches() ? h : "";
    }

    /** Everything that makes this proposal unusable as written: empty, partial or inconsistent fields. Empty list = well-formed. */
    public List<String> structuralProblems() {
        List<String> p = new ArrayList<>();
        requireText(p, "outDir", outDir);
        requireText(p, "testId", testId);
        requireText(p, "testClass", testClass);
        requireText(p, "simpleName", simpleName);
        requireText(p, "step", step);
        requireText(p, "affectedFile", affectedFile);
        requireText(p, "affectedMethod", affectedMethod);
        requireText(p, "oldSnippet", oldSnippet);
        requireText(p, "newSnippet", newSnippet);
        requireText(p, "reason", reason);
        requireText(p, "groupKey", groupKey);
        if (oldSnippet.equals(newSnippet)) p.add("oldSnippet and newSnippet are identical (a no-op change)");
        if (!enumName(PatchProposer.Proposal.Kind.class, proposalKind)) p.add("proposalKind is not a known kind: \"" + proposalKind + "\"");
        if (!enumName(PatchProposer.Proposal.Source.class, proposalSource)) p.add("proposalSource is not a known source: \"" + proposalSource + "\"");
        if (!(confidence >= 0 && confidence <= 1)) p.add("confidence is outside 0..1: " + confidence);
        if (attemptNumber < 1) p.add("attemptNumber is missing (" + attemptNumber + ")");
        if (!SHA256_HEX.matcher(sourceSha256).matches()) p.add("sourceSha256 is missing or not a SHA-256");
        if (!SHA256_HEX.matcher(failureEvidenceHash).matches()) p.add("failureEvidenceHash is missing or not a SHA-256");
        if (!SHA256_HEX.matcher(proposalHash).matches()) p.add("proposalHash is missing or not a SHA-256");
        return p;
    }

    private static void requireText(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) problems.add(name + " is missing");
    }

    private static <E extends Enum<E>> boolean enumName(Class<E> type, String name) {
        try { Enum.valueOf(type, name); return true; } catch (RuntimeException e) { return false; }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty("outDir", outDir);
        p.setProperty("testId", testId);
        p.setProperty("testClass", testClass);
        p.setProperty("simpleName", simpleName);
        p.setProperty("step", step);
        p.setProperty("affectedFile", affectedFile);
        p.setProperty("affectedMethod", affectedMethod);
        p.setProperty("proposalSource", proposalSource);
        p.setProperty("proposalKind", proposalKind);
        p.setProperty("oldSnippet", oldSnippet);
        p.setProperty("newSnippet", newSnippet);
        p.setProperty("reason", reason);
        p.setProperty("confidence", String.valueOf(confidence));
        p.setProperty("risk", risk);
        p.setProperty("rootCause", rootCause);
        p.setProperty("evidence", String.join("␟", evidence));
        p.setProperty("semanticValidation", semanticValidation);
        p.setProperty("semanticJustification", semanticJustification);
        p.setProperty("adaptiveScore", adaptiveScore == null ? "" : String.valueOf(adaptiveScore));
        p.setProperty("adaptiveLevel", adaptiveLevel);
        p.setProperty("impactSummary", impactSummary);
        p.setProperty("groupKey", groupKey);
        p.setProperty("originalFailingSteps", String.join("␟", originalFailingSteps));
        p.setProperty("candidateIndex", String.valueOf(candidateIndex));
        p.setProperty("candidateTotal", String.valueOf(candidateTotal));
        p.setProperty("attemptNumber", String.valueOf(attemptNumber));
        p.setProperty("attemptsMax", String.valueOf(attemptsMax));
        p.setProperty("sessionFile", sessionFile);
        p.setProperty("createdAt", createdAt);
        p.setProperty("sourceSha256", sourceSha256);
        p.setProperty("failureEvidenceHash", failureEvidenceHash);
        p.setProperty("proposalHash", proposalHash);
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "PendingHeal - a proposal shown to a human in Claude Code chat, awaiting their approve/decline decision");
        }
    }

    public static PendingHeal load(Path file) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        }
        PendingHeal h = new PendingHeal();
        h.outDir = p.getProperty("outDir", "test-output");
        h.testId = p.getProperty("testId", "");
        h.testClass = p.getProperty("testClass", "");
        h.simpleName = p.getProperty("simpleName", "");
        h.step = p.getProperty("step", "");
        h.affectedFile = p.getProperty("affectedFile", "");
        h.affectedMethod = p.getProperty("affectedMethod", "");
        h.proposalSource = p.getProperty("proposalSource", "");
        h.proposalKind = p.getProperty("proposalKind", "");
        h.oldSnippet = p.getProperty("oldSnippet", "");
        h.newSnippet = p.getProperty("newSnippet", "");
        h.reason = p.getProperty("reason", "");
        h.confidence = parseDouble(p.getProperty("confidence", "-1"), -1);
        h.risk = p.getProperty("risk", "");
        h.rootCause = p.getProperty("rootCause", "");
        h.evidence = splitNonEmpty(p.getProperty("evidence", ""));
        h.semanticValidation = p.getProperty("semanticValidation", "");
        h.semanticJustification = p.getProperty("semanticJustification", "");
        String adaptive = p.getProperty("adaptiveScore", "");
        h.adaptiveScore = adaptive.isBlank() ? null : parseDouble(adaptive, 0);
        h.adaptiveLevel = p.getProperty("adaptiveLevel", "");
        h.impactSummary = p.getProperty("impactSummary", "");
        h.groupKey = p.getProperty("groupKey", "");
        h.originalFailingSteps = splitNonEmpty(p.getProperty("originalFailingSteps", ""));
        h.candidateIndex = parseInt(p.getProperty("candidateIndex", "1"), 1);
        h.candidateTotal = parseInt(p.getProperty("candidateTotal", "1"), 1);
        h.attemptNumber = parseInt(p.getProperty("attemptNumber", "0"), 0);
        h.attemptsMax = parseInt(p.getProperty("attemptsMax", "0"), 0);
        h.sessionFile = p.getProperty("sessionFile", "");
        h.createdAt = p.getProperty("createdAt", "");
        h.sourceSha256 = p.getProperty("sourceSha256", "");
        h.failureEvidenceHash = p.getProperty("failureEvidenceHash", "");
        h.proposalHash = p.getProperty("proposalHash", "");
        return h;
    }

    private static List<String> splitNonEmpty(String joined) {
        List<String> out = new ArrayList<>();
        if (joined.isBlank()) return out;
        for (String s : joined.split("␟")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
