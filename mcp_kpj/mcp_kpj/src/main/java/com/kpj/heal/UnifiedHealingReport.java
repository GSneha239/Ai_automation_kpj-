package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unified Self-Healing Report - ONE HTML page per test that joins what the existing agents already wrote:
 * the failure capture ({@code <outDir>/failures/<testId>_N.txt}), the append-only audit trail
 * ({@link HealingAuditTrail}'s {@code healing-history.tsv}), an unresolved proposal ({@link PendingHeal}), and links to the
 * reports {@link FailureAnalysisReport} and {@link HealingDashboard} already produced.
 *
 * <p><b>Read-only over every existing artifact.</b> It never logs in, never launches a browser, never runs a test
 * and never modifies an existing file: its only write is ONE new timestamped file under
 * {@code <outDir>/unified-report/} (created with CREATE_NEW, so an existing report is never overwritten).</p>
 *
 * <p><b>Never invents a result.</b> Every field is either a value read from an artifact or is labelled
 * {@code NOT AVAILABLE}, {@code UNVERIFIED} or {@code PENDING}. The banner says "HEALED AND VERIFIED" only when the
 * audit trail records KEPT with compile PASS and re-run PASS for an applied, not-reverted patch.</p>
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.UnifiedHealingReport -Dexec.args="&lt;testId|ClassName|FQCN&gt; [outDir]"
 * </pre>
 */
public final class UnifiedHealingReport {

    private UnifiedHealingReport() {}

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BACKUP_TS = Pattern.compile("\\.(\\d{8}-\\d{6}-\\d{3})\\.bak$");
    private static final DateTimeFormatter BACKUP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String NA = "NOT AVAILABLE";
    private static final List<String> DECISION_STATUSES =
            List.of("KEPT", "REVERTED", "RESTORE_FAILED", "REJECTED_VERIFICATION_FAILED", "NOT_APPLIED");

    // ------------------------------------------------------------------ entry points

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: UnifiedHealingReport <testId|TestClassName|fully.qualified.ClassName> [outDir]");
            System.out.println("       Reads existing artifacts under <outDir> (default: test-output) and writes ONE new file to");
            System.out.println("       <outDir>/unified-report/. No login, no browser, no tests, nothing existing is modified.");
            return;
        }
        String outDir = args.length > 1 ? args[1] : "test-output";
        try {
            Path p = generate(outDir, args[0]);
            if (p != null) System.out.println("Unified report: " + p.toAbsolutePath());
        } catch (Throwable t) {
            System.out.println("UnifiedHealingReport: could not generate the report - " + t);
        }
    }

    /** Hook-safe wrapper: never throws, never affects the calling workflow; prints one line either way. */
    public static void generateQuietly(String outDir, String testRef) {
        try {
            Path p = generate(outDir, testRef);
            if (p != null) System.out.println("Unified report: " + p.toAbsolutePath());
        } catch (Throwable t) {
            System.out.println("UnifiedHealingReport: skipped (" + t + ")");
        }
    }

    /**
     * Builds and writes the unified report for one test.
     *
     * @return the new report file, or {@code null} if no artifact under {@code outDir} mentions this test.
     */
    public static Path generate(String outDir, String testRef) throws IOException {
        Path out = Paths.get(outDir);
        List<Map<String, String>> audit = readAudit(out);
        List<PendingHeal> pendings = readPending(out);

        Ident id = identify(out, testRef, audit, pendings);
        if (id == null) {
            System.out.println("UnifiedHealingReport: no healing or failure evidence found for '" + testRef
                    + "' under " + outDir + " - no report written.");
            return null;
        }

        List<Map<String, String>> mine = new ArrayList<>();
        for (Map<String, String> r : audit) if (id.testId.equals(r.getOrDefault("testId", ""))) mine.add(r);
        Map<String, String> decision = null;     // latest real approve/decline/keep/revert row
        Map<String, String> analysisOnly = null; // latest DRY_RUN / NEEDS_TRIAGE row
        for (Map<String, String> r : mine) {
            if (DECISION_STATUSES.contains(r.getOrDefault("finalStatus", ""))) decision = r;
            else analysisOnly = r;
        }
        PendingHeal pending = null;
        for (PendingHeal p : pendings) {
            if (id.testId.equals(p.testId) && (pending == null || p.createdAt.compareTo(pending.createdAt) > 0)) pending = p;
        }

        // The pending proposal is "current" unless a real decision was recorded AFTER it was created.
        boolean pendingIsCurrent = pending != null;
        if (pending != null && decision != null) {
            LocalDateTime dt = parseTs(decision.get("timestamp")), pt = parseTs(pending.createdAt);
            if (dt != null && pt != null && dt.isAfter(pt)) pendingIsCurrent = false;
        }

        State state;
        if (pendingIsCurrent) state = State.PROPOSED;
        else if (decision != null) state = State.DECIDED;
        else if (analysisOnly != null) state = State.ANALYSIS_ONLY;
        else state = State.NO_RESULT;

        Path reportDir = out.resolve("unified-report");
        Files.createDirectories(reportDir);
        String html = render(out, reportDir, id, state, decision, analysisOnly, pendingIsCurrent ? pending : null, mine);

        String base = safe(id.testId) + "_" + FILE_TS.format(LocalDateTime.now());
        Path file = reportDir.resolve(base + ".html");
        for (int n = 2; ; n++) {
            try {
                Files.writeString(file, html, StandardOpenOption.CREATE_NEW);
                break;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                file = reportDir.resolve(base + "_" + n + ".html"); // never overwrite an existing report
            }
        }
        return file;
    }

    // ------------------------------------------------------------------ model

    private enum State { PROPOSED, DECIDED, ANALYSIS_ONLY, NO_RESULT }

    private static final class Ident {
        String testId = "", testClass = "", simpleName = "";
    }

    private static boolean refMatches(String ref, String testId, String testClass) {
        if (ref == null || ref.isBlank()) return false;
        String r = ref.trim();
        String simple = testClass == null ? "" : testClass.substring(testClass.lastIndexOf('.') + 1);
        return r.equalsIgnoreCase(testId) || r.equalsIgnoreCase(testClass) || r.equalsIgnoreCase(simple);
    }

    private static Ident identify(Path out, String ref, List<Map<String, String>> audit, List<PendingHeal> pendings) throws IOException {
        Ident id = new Ident();
        for (PendingHeal p : pendings) {
            if (refMatches(ref, p.testId, p.testClass)) { id.testId = p.testId; id.testClass = p.testClass; break; }
        }
        if (id.testId.isEmpty()) {
            for (Map<String, String> r : audit) {
                if (refMatches(ref, r.getOrDefault("testId", ""), r.getOrDefault("testClass", ""))) {
                    id.testId = r.get("testId"); id.testClass = r.getOrDefault("testClass", ""); // last match wins
                }
            }
        }
        if (id.testId.isEmpty() && !failureFiles(out, ref).isEmpty()) id.testId = ref.trim();
        if (id.testId.isEmpty()) return null;
        if (id.testClass.isEmpty()) {
            for (Map<String, String> r : audit) if (id.testId.equals(r.get("testId")) && !r.getOrDefault("testClass", "").isEmpty()) id.testClass = r.get("testClass");
        }
        if (id.testClass.isEmpty()) {
            // Only a failure capture exists (e.g. the FAILURE_ANALYSIS_ONLY path): map testId -> class with the existing
            // TestClassResolver (read-only scan of src/test/java), so the report names the class and can find the
            // Failure Analysis report folder, which is named after the class's simple name.
            try {
                String fq = new TestClassResolver().resolveAll().get(id.testId);
                if (fq != null) id.testClass = fq;
            } catch (Throwable ignore) { /* the class simply stays "not recorded" */ }
        }
        id.simpleName = id.testClass.isEmpty() ? id.testId : id.testClass.substring(id.testClass.lastIndexOf('.') + 1);
        return id;
    }

    // ------------------------------------------------------------------ readers (read-only)

    private static List<Map<String, String>> readAudit(Path out) {
        List<Map<String, String>> rows = new ArrayList<>();
        Path f = out.resolve("failures").resolve("healing-history.tsv");
        if (!Files.isRegularFile(f)) return rows;
        try {
            List<String> lines = Files.readAllLines(f);
            if (lines.isEmpty()) return rows;
            String[] header = lines.get(0).split("\t", -1);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) continue;
                String[] c = lines.get(i).split("\t", -1);
                Map<String, String> m = new HashMap<>();
                for (int k = 0; k < header.length; k++) m.put(header[k], k < c.length ? c[k] : "");
                rows.add(m);
            }
        } catch (IOException ignore) { /* an unreadable audit trail simply means "no audit evidence" */ }
        return rows;
    }

    private static List<PendingHeal> readPending(Path out) {
        List<PendingHeal> list = new ArrayList<>();
        Path dir = out.resolve("self-healing").resolve("pending");
        if (!Files.isDirectory(dir)) return list;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(x -> x.getFileName().toString().endsWith(".properties")).collect(Collectors.toList())) {
                try { list.add(PendingHeal.load(p)); } catch (Exception ignore) { /* skip unreadable file */ }
            }
        } catch (IOException ignore) { }
        return list;
    }

    private static List<Path> failureFiles(Path out, String testId) {
        Path dir = out.resolve("failures");
        if (testId == null || testId.isBlank() || !Files.isDirectory(dir)) return List.of();
        String prefix = safe(testId.trim()) + "_";
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> { String n = p.getFileName().toString(); return n.startsWith(prefix) && n.endsWith(".txt") && n.substring(prefix.length()).matches("\\d+\\.txt"); })
                    .collect(Collectors.toList());
        } catch (IOException e) { return List.of(); }
    }

    private static Map<String, String> readFailureCapture(Path out, String testId, String step) {
        List<Path> files = failureFiles(out, testId);
        Map<String, String> best = null;
        long bestTime = Long.MIN_VALUE;
        for (Path p : files) {
            Map<String, String> kv = new HashMap<>();
            try {
                for (String line : Files.readAllLines(p)) {
                    int eq = line.indexOf('=');
                    if (eq > 0) kv.putIfAbsent(line.substring(0, eq), line.substring(eq + 1));
                }
                kv.put("__file", p.toString());
                long t = Files.getLastModifiedTime(p).toMillis();
                boolean stepMatch = step != null && !step.isBlank() && step.equals(kv.get("step"));
                if (stepMatch) t += Long.MAX_VALUE / 4; // a capture of the exact step beats a newer capture of another step
                if (best == null || t > bestTime) { best = kv; bestTime = t; }
            } catch (IOException ignore) { }
        }
        return best;
    }

    private static LocalDateTime parseTs(String s) {
        try { return s == null || s.isBlank() ? null : LocalDateTime.parse(s.trim()); } catch (Exception e) { return null; }
    }

    /** Newest {@code <dir>/<Simple>_<timestamp>/<fileName>} that exists, as a path relative to {@code reportDir}. */
    private static String linkTo(Path reportDir, Path dir, String simple, String fileName) {
        Path best = findReport(dir, simple, fileName);
        if (best == null) return null;
        return reportDir.toAbsolutePath().normalize().relativize(best.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** Newest {@code <dir>/<Simple>_<timestamp>/<fileName>} that exists, or null. */
    private static Path findReport(Path dir, String simple, String fileName) {
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith(simple + "_"))
                    .map(d -> d.resolve(fileName.replace("<Simple>", simple)))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; } }))
                    .orElse(null);
        } catch (IOException e) { return null; }
    }

    // ---- Failure Analysis details, read ONLY from rows the existing report states explicitly -----------------------
    private static final List<String> FA_LABELS = List.of("Failure Category", "Recommended Owner", "Severity", "Root Cause",
            "Automatic Fixing Possible", "Current Status");
    private static final Pattern FA_ROW = Pattern.compile("<tr><th>(.*?)</th><td>(.*?)</td></tr>", Pattern.DOTALL);
    /** Wording the Failure Analysis report itself uses when it has NOT determined a cause. */
    private static final Pattern FA_UNDETERMINED = Pattern.compile("(?i)could not (confidently )?determine");

    /**
     * The label/value rows of ONE failed step in an existing Failure Analysis report. The report repeats a "Failed Step"
     * row at the start of each section, so rows are grouped under the step most recently seen and only the FIRST value per
     * label is kept. Returns null if the file is unreadable, no step is known, or the report has no entry for the step -
     * never rows of a different step, and nothing is inferred.
     */
    private static Map<String, String> readFailureAnalysis(Path file, String step) {
        if (file == null || step == null || step.isBlank()) return null;
        try {
            String html = Files.readString(file);
            Map<String, Map<String, String>> byStep = new HashMap<>();
            String current = null;
            Matcher m = FA_ROW.matcher(html);
            while (m.find()) {
                String label = plainText(m.group(1)), value = plainText(m.group(2));
                if (label.equals("Failed Step")) { current = value; byStep.computeIfAbsent(current, k -> new HashMap<>()); continue; }
                if (current != null && FA_LABELS.contains(label)) byStep.get(current).putIfAbsent(label, value);
            }
            return byStep.get(step.trim());
        } catch (IOException e) { return null; }
    }

    private static String plainText(String html) {
        return html.replaceAll("<[^>]+>", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    private static String faCell(Map<String, String> fa, String label, List<String> missing) {
        String v = fa.getOrDefault(label, "");
        if (v.isBlank()) { missing.add("Failure Analysis: " + label.toLowerCase()); return unavailable("not recorded in the Failure Analysis report"); }
        return esc(v);
    }

    /** The backup file {@code PatchApplier} took for this attempt. The audit trail stores only "backup created: yes/no",
     *  so the file is matched by name and nearest time - and the report says so. */
    private static String inferBackup(Path out, String affectedFile, String attemptTs) {
        if (affectedFile == null || affectedFile.isBlank()) return null;
        Path dir = out.resolve("self-healing").resolve("backups");
        if (!Files.isDirectory(dir)) return null;
        String name = Paths.get(affectedFile.replace('\\', '/')).getFileName().toString();
        LocalDateTime at = parseTs(attemptTs);
        if (at == null) return null;
        Path best = null;
        long bestDiff = Long.MAX_VALUE;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.collect(Collectors.toList())) {
                String fn = p.getFileName().toString();
                if (!fn.startsWith(name + ".")) continue;
                Matcher m = BACKUP_TS.matcher(fn);
                if (!m.find()) continue;
                try {
                    long diff = Math.abs(Duration.between(at, LocalDateTime.parse(m.group(1), BACKUP_FMT)).getSeconds());
                    if (diff <= 600 && diff < bestDiff) { bestDiff = diff; best = p; }
                } catch (Exception ignore) { }
            }
        } catch (IOException e) { return null; }
        return best == null ? null : best.toString();
    }

    // ------------------------------------------------------------------ rendering

    private static String render(Path out, Path reportDir, Ident id, State state, Map<String, String> decision,
                                 Map<String, String> analysisOnly, PendingHeal pending, List<Map<String, String>> allRows) {
        Map<String, String> row = decision != null ? decision : (state == State.ANALYSIS_ONLY ? analysisOnly : null);
        String step = pending != null ? pending.step : (row != null ? row.getOrDefault("step", "") : "");
        Map<String, String> cap = readFailureCapture(out, id.testId, step);
        // No audit row or proposal names the step: the failure capture is then the only record of it.
        if (step.isBlank() && cap != null) step = cap.getOrDefault("step", "");
        List<String> missing = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if (cap != null) sources.add("Failure capture: " + cap.get("__file"));
        if (!allRows.isEmpty()) sources.add("Audit trail: " + out.resolve("failures").resolve("healing-history.tsv"));
        if (pending != null) sources.add("Pending proposal: " + out.resolve("self-healing").resolve("pending") + " (testId " + pending.testId + ")");

        // ---- final label
        String bannerClass, bannerText, bannerDetail;
        String fs = row == null ? "" : row.getOrDefault("finalStatus", "");
        String compile = row == null ? "" : row.getOrDefault("compileResult", "");
        String rerun = row == null ? "" : row.getOrDefault("rerunResult", "");
        boolean applied = row != null && "true".equalsIgnoreCase(row.getOrDefault("patchApplied", ""));
        boolean reverted = row != null && "true".equalsIgnoreCase(row.getOrDefault("reverted", ""));
        String restoration = row == null ? "" : row.getOrDefault("restorationVerified", "").trim();
        // A rollback is only a ROLLBACK when the restoration was verified. An audit row that says REVERTED but records
        // restorationVerified=false is treated as a failed restore, never as a clean rollback.
        boolean restoreFailed = "RESTORE_FAILED".equals(fs) || ("REVERTED".equals(fs) && "false".equalsIgnoreCase(restoration));
        switch (state) {
            case PROPOSED:
                bannerClass = "warn"; bannerText = "PROPOSED, awaiting approval";
                bannerDetail = "A fix has been proposed but NOTHING has been applied, re-tested or verified yet. This is not a healing result.";
                break;
            case DECIDED:
                if ("KEPT".equals(fs) && "PASS".equals(compile) && "PASS".equals(rerun) && applied && !reverted) {
                    bannerClass = "ok"; bannerText = "HEALED AND VERIFIED - KEEP";
                    bannerDetail = "The audit trail records an approved, applied patch with compile PASS and re-run PASS, and the change was kept.";
                } else if ("KEPT".equals(fs)) {
                    bannerClass = "warn"; bannerText = "KEPT - UNVERIFIED";
                    bannerDetail = "The audit trail says KEPT, but compile / re-run PASS is not recorded, so this report does not claim a verified heal.";
                } else if (restoreFailed) {
                    bannerClass = "bad"; bannerText = "RESTORE_FAILED - MANUAL RESTORE REQUIRED";
                    bannerDetail = "The patch was applied and verification failed, but the original file could NOT be restored and verified. "
                            + "The source file MAY STILL CONTAIN THE PATCH - restore it manually from the backup. The test is NOT healed."
                            + (row.getOrDefault("reason", "").isBlank() ? "" : " Recorded reason: " + esc(row.get("reason")));
                } else if ("REVERTED".equals(fs)) {
                    bannerClass = "bad"; bannerText = "ROLLBACK - NOT HEALED";
                    bannerDetail = "The patch was applied but verification failed, so it was reverted"
                            + ("true".equalsIgnoreCase(restoration) ? " and the restoration was verified"
                               : " (this audit row does not record whether the restoration was verified)")
                            + ". The test is NOT healed.";
                } else if ("REJECTED_VERIFICATION_FAILED".equals(fs)) {
                    bannerClass = "bad"; bannerText = "REJECTED - NOT HEALED";
                    bannerDetail = "This candidate was rejected or failed verification. The test is NOT healed.";
                } else {
                    bannerClass = "bad"; bannerText = "NOT APPLIED - NOT HEALED";
                    bannerDetail = "The proposal was declined or not applied. Nothing was changed.";
                }
                break;
            case ANALYSIS_ONLY:
                bannerClass = "warn"; bannerText = "NO HEALING RESULT - analysis only";
                bannerDetail = "Only analysis was recorded (" + esc(fs.isEmpty() ? "no final status" : fs) + "). No fix was approved, applied or re-tested.";
                break;
            default:
                bannerClass = "warn"; bannerText = "NO HEALING RESULT";
                bannerDetail = "A failure was captured, but no proposal, approval or healing attempt is recorded.";
        }

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><title>Unified Self-Healing Report - ")
          .append(esc(id.simpleName)).append("</title><style>").append(CSS).append("</style></head><body><div class='wrap'>");
        sb.append("<h1>Unified Self-Healing Report</h1>");
        sb.append("<div class='sub'>").append(esc(id.simpleName)).append(" &middot; ").append(esc(id.testId))
          .append(" &middot; generated ").append(esc(DISPLAY_TS.format(LocalDateTime.now()))).append("</div>");
        sb.append("<div class='banner ").append(bannerClass).append("'><div class='bt'>").append(esc(bannerText))
          .append("</div><div>").append(bannerDetail).append("</div></div>");

        sb.append("<h2>1. Test</h2><table>");
        row(sb, "Test name", esc(id.simpleName));
        row(sb, "Test ID", esc(id.testId));
        row(sb, "Test class", id.testClass.isEmpty() ? unavailable("not recorded") : esc(id.testClass));
        row(sb, "Failed step", step.isBlank() ? unavailable("no step recorded") : esc(step));
        sb.append("</table>");

        // ---- original failure
        sb.append("<h2>2. Original failure</h2><table>");
        String expected = cap != null ? cap.getOrDefault("expected", "") : "", actual = cap != null ? cap.getOrDefault("actual", "") : "";
        String failureSource = cap != null ? "failure capture" : "";
        if ((expected.isBlank() && actual.isBlank()) && row != null && !row.getOrDefault("failure", "").isBlank()) {
            String f = row.get("failure");
            int bar = f.indexOf(" | actual=");
            expected = f.startsWith("expected=") && bar > 0 ? f.substring(9, bar) : f;
            actual = bar > 0 ? f.substring(bar + 10) : "";
            failureSource = "audit trail";
        }
        row(sb, "Expected", val(expected, missing, "original failure - expected"));
        row(sb, "Actual", val(actual, missing, "original failure - actual"));
        if (cap != null) row(sb, "Page URL", val(cap.getOrDefault("url", ""), missing, "failure page URL"));
        row(sb, "Source", failureSource.isEmpty() ? unavailable("no failure capture or audit row") : esc(failureSource));
        sb.append("</table>");

        // ---- root cause
        String rootCause = pending != null ? pending.rootCause : (row != null ? row.getOrDefault("rootCause", "") : "");
        Path faFile = findReport(out.resolve("failure-analysis").resolve("reports"), id.simpleName, "<Simple>_FailureAnalysisReport.html");
        Map<String, String> faRows = readFailureAnalysis(faFile, step);
        if (faRows != null) sources.add("Failure Analysis report: " + faFile);
        String faRoot = faRows == null ? "" : faRows.getOrDefault("Root Cause", "");
        boolean faRootUndetermined = !faRoot.isBlank() && FA_UNDETERMINED.matcher(faRoot).find();
        sb.append("<h2>3. Root cause and Failure Analysis</h2><table>");
        if (!rootCause.isBlank() && !"N/A".equalsIgnoreCase(rootCause.trim())) {
            row(sb, "Root cause", val(rootCause, missing, "root cause") + " <span class='note'>(source: healing analysis)</span>");
        } else if (!faRoot.isBlank() && !faRootUndetermined) {
            row(sb, "Root cause", esc(faRoot) + " <span class='note'>(source: Failure Analysis report)</span>");
        } else {
            missing.add("root cause");
            row(sb, "Root cause", faRootUndetermined
                    ? unavailable("Failure Analysis states it has not determined one: \"" + faRoot + "\"")
                    : unavailable("not recorded"));
        }
        if (faRows == null) {
            row(sb, "Failure Analysis", unavailable(faFile == null ? "no Failure Analysis report found for this test"
                    : "the Failure Analysis report has no entry for this step"));
            missing.add("Failure Analysis category and owner");
        } else {
            row(sb, "Failure category", faCell(faRows, "Failure Category", missing));
            row(sb, "Recommended owner", faCell(faRows, "Recommended Owner", missing));
            row(sb, "Severity", faCell(faRows, "Severity", missing));
            row(sb, "Automatic fixing possible", faCell(faRows, "Automatic Fixing Possible", missing));
            row(sb, "Failure Analysis status", faCell(faRows, "Current Status", missing));
        }
        sb.append("</table>");

        // ---- proposed fix
        sb.append("<h2>4. Proposed fix</h2><table>");
        String kind = pending != null ? pending.proposalKind : (row != null ? row.getOrDefault("proposalKind", "") : "");
        String source = pending != null ? pending.proposalSource : (row != null ? row.getOrDefault("source", "") : "");
        String conf = pending != null ? (pending.confidence >= 0 ? Math.round(pending.confidence * 100) + "%" : "")
                : (row != null ? row.getOrDefault("confidence", "") : "");
        String risk = pending != null ? pending.risk : (row != null ? row.getOrDefault("risk", "") : "");
        String sem = pending != null ? pending.semanticValidation : (row != null ? row.getOrDefault("semanticValidation", "") : "");
        String file = pending != null ? pending.affectedFile : (row != null ? row.getOrDefault("affectedFile", "") : "");
        String method = pending != null ? pending.affectedMethod : (row != null ? row.getOrDefault("affectedMethod", "") : "");
        row(sb, "Fix type / source", val(kind.isBlank() && source.isBlank() ? "" : kind + " / " + source, missing, "proposal type"));
        row(sb, "Target file", val(file, missing, "target file"));
        row(sb, "Target method", val(method, missing, "target method"));
        row(sb, "Confidence", val(conf, missing, "confidence"));
        row(sb, "Risk", val(risk, missing, "risk"));
        row(sb, "Semantic validation", val(sem, missing, "semantic validation"));
        sb.append("</table>");

        // ---- approval
        sb.append("<h2>5. Approval</h2><table>");
        String approval;
        if (state == State.PROPOSED) approval = "<span class='tag pend'>PENDING</span> awaiting human approval - nothing applied";
        else if (row != null && !row.getOrDefault("humanDecision", "").isBlank() && !"N/A".equals(row.get("humanDecision"))) approval = esc(row.get("humanDecision"));
        else approval = unavailable("no approval decision recorded");
        row(sb, "Approval status", approval);
        sb.append("</table>");

        // ---- backup
        sb.append("<h2>6. Backup</h2><table>");
        String backupText;
        if (state == State.PROPOSED) backupText = "<span class='tag pend'>PENDING</span> not taken yet - a backup is created only when an approved patch is applied";
        else if (row == null) backupText = unavailable("no attempt recorded");
        else if (!"true".equalsIgnoreCase(row.getOrDefault("backupCreated", ""))) backupText = "No backup recorded (" + (applied ? "audit says none" : "no patch was applied") + ")";
        else if (!row.getOrDefault("backupPath", "").isBlank()) {
            // recorded exactly by the audit trail - no inference needed
            backupText = "Created: " + esc(row.get("backupPath"));
        } else {
            String inferred = inferBackup(out, file, row.get("timestamp"));
            backupText = inferred != null
                    ? "Created. <span class='tag unv'>UNVERIFIED (inferred)</span> " + esc(inferred) + "<div class='note'>The audit trail stores only \"backup created: yes\"; the file was matched by name and nearest time.</div>"
                    : "Created (per audit trail). <span class='tag unv'>PATH NOT AVAILABLE</span> the file could not be matched under " + esc(out.resolve("self-healing").resolve("backups").toString());
            if (inferred == null) missing.add("backup file path");
        }
        row(sb, "Backup", backupText);
        sb.append("</table>");

        // ---- diff
        sb.append("<h2>7. Before / after</h2>");
        String oldS = pending != null ? pending.oldSnippet : (row != null ? row.getOrDefault("oldSnippet", "") : "");
        String newS = pending != null ? pending.newSnippet : (row != null ? row.getOrDefault("newSnippet", "") : "");
        if (!oldS.isBlank() && !newS.isBlank()) {
            sb.append(HtmlDiffRenderer.renderDiff(oldS, newS));
            if (state == State.PROPOSED) sb.append("<div class='note'>Proposed change only - not applied.</div>");
        } else {
            sb.append("<div class='na'>").append(NA).append(" - no before/after snippet is recorded.</div>");
            missing.add("before/after diff");
        }

        // ---- compile, re-test, final
        sb.append("<h2>8. Compile, re-test and final status</h2><table>");
        if (state == State.PROPOSED) {
            row(sb, "Compile result", "<span class='tag pend'>NOT RUN YET</span>");
            row(sb, "Re-test result", "<span class='tag pend'>NOT RUN YET</span>");
            row(sb, "Regression check", "<span class='tag pend'>NOT RUN YET</span>");
            row(sb, "Final status", "<span class='tag pend'>PROPOSED, awaiting approval</span>");
        } else if (row == null) {
            row(sb, "Compile result", unavailable("no attempt recorded"));
            row(sb, "Re-test result", unavailable("no attempt recorded"));
            row(sb, "Regression check", unavailable("no attempt recorded"));
            row(sb, "Final status", unavailable("no attempt recorded"));
        } else {
            String notRun = applied ? "" : " (no patch was applied)";
            row(sb, "Compile result", resultCell(compile, notRun, missing, "compile result"));
            row(sb, "Re-test result", resultCell(rerun, notRun, missing, "re-test result"));
            row(sb, "Regression check", resultCell(row.getOrDefault("regressionResult", ""), notRun, missing, "regression result"));
            String finalCell = fs.isBlank() || "N/A".equals(fs) ? unavailable("no final status")
                    : ("KEPT".equals(fs) ? "KEEP" : restoreFailed ? "ROLLBACK FAILED - patch may still be in the source"
                       : "REVERTED".equals(fs) ? "ROLLBACK" : esc(fs)) + " <span class='note'>(audit status: " + esc(fs) + ")</span>";
            row(sb, "Final status", finalCell);
            if (applied && !"KEPT".equals(fs)) {
                String orig = row.getOrDefault("originalSha256", "");
                String restoreVia = row.getOrDefault("restorationMethod", "");
                String exact = orig.length() >= 12 && !restoreVia.isBlank()
                        ? " (byte-for-byte against the original, SHA-256 " + orig.substring(0, 12) + "..., via " + restoreVia + ")" : "";
                row(sb, "Restoration verified", "true".equalsIgnoreCase(restoration) ? "YES - the restored file was verified against the backup" + esc(exact)
                        : "false".equalsIgnoreCase(restoration) || restoreFailed ? "<span class='tag unv'>NO</span> the file could not be verified as restored - it may still contain the patch"
                        : unavailable("not recorded in this audit row"));
            }
            if (!row.getOrDefault("reason", "").isBlank()) row(sb, "Reason recorded", esc(row.get("reason")));
        }
        sb.append("</table>");

        // ---- other attempts
        sb.append("<h2>9. All recorded attempts for this test</h2>");
        if (allRows.isEmpty()) sb.append("<div class='na'>").append(NA).append(" - the audit trail has no rows for this test.</div>");
        else {
            sb.append("<table><tr><th>Time</th><th>Step</th><th>Decision</th><th>Final status</th><th>Re-test</th></tr>");
            int from = Math.max(0, allRows.size() - 15);
            for (int i = from; i < allRows.size(); i++) {
                Map<String, String> r = allRows.get(i);
                sb.append("<tr><td>").append(esc(r.getOrDefault("timestamp", ""))).append("</td><td>").append(esc(r.getOrDefault("step", "")))
                  .append("</td><td>").append(esc(r.getOrDefault("humanDecision", ""))).append("</td><td>").append(esc(r.getOrDefault("finalStatus", "")))
                  .append("</td><td>").append(esc(r.getOrDefault("rerunResult", ""))).append("</td></tr>");
            }
            sb.append("</table>");
            if (from > 0) sb.append("<div class='note'>Showing the latest 15 of ").append(allRows.size()).append(" rows.</div>");
        }

        // ---- links to existing reports
        sb.append("<h2>10. Existing detailed reports</h2><ul>");
        String fa = linkTo(reportDir, out.resolve("failure-analysis").resolve("reports"), id.simpleName, "<Simple>_FailureAnalysisReport.html");
        String sh = linkTo(reportDir, out.resolve("self-healing").resolve("reports"), id.simpleName, "<Simple>_SelfHealingReport.html");
        String hr = linkTo(reportDir, out.resolve("self-healing").resolve("healing"), id.simpleName, "healing-report.html");
        sb.append("<li>Failure Analysis report: ").append(fa == null ? unavailable("none found") : "<a href='" + esc(fa) + "'>" + esc(fa) + "</a>").append("</li>");
        sb.append("<li>Self-Healing report: ").append(sh == null ? unavailable("none found") : "<a href='" + esc(sh) + "'>" + esc(sh) + "</a>").append("</li>");
        sb.append("<li>Healing report (screenshots, step-by-step): ").append(hr == null ? unavailable("none found - exists only after a real approval decision") : "<a href='" + esc(hr) + "'>" + esc(hr) + "</a>").append("</li>");
        sb.append("</ul>");

        // ---- missing / unverified
        sb.append("<h2>11. Missing or unverified information</h2>");
        if (missing.isEmpty()) sb.append("<div class='note'>Every field above was read from a recorded artifact. Backup path matching is inferred only where marked.</div>");
        else {
            sb.append("<ul>");
            for (String m : new java.util.LinkedHashSet<>(missing)) sb.append("<li>").append(esc(m)).append("</li>");
            sb.append("</ul>");
        }
        sb.append("<h3>Sources read</h3><ul>");
        if (sources.isEmpty()) sb.append("<li>").append(NA).append("</li>");
        for (String s : sources) sb.append("<li>").append(esc(s)).append("</li>");
        sb.append("</ul><div class='note'>Generated by com.kpj.heal.UnifiedHealingReport from existing artifacts only. No login, browser or test was used.</div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String resultCell(String v, String notRunNote, List<String> missing, String what) {
        if (v == null || v.isBlank() || "N/A".equalsIgnoreCase(v.trim())) {
            if (!notRunNote.isEmpty()) return "<span class='tag unv'>N/A</span>" + esc(notRunNote);
            missing.add(what);
            return unavailable("not recorded");
        }
        return esc(v);
    }

    private static String val(String v, List<String> missing, String what) {
        if (v == null || v.isBlank() || "N/A".equalsIgnoreCase(v.trim())) { missing.add(what); return unavailable("not recorded"); }
        return esc(HealingAuditTrail.sanitize(v));
    }

    private static String unavailable(String why) { return "<span class='tag na'>" + NA + "</span> " + esc(why); }

    private static void row(StringBuilder sb, String label, String valueHtml) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(valueHtml).append("</td></tr>");
    }

    private static String esc(String s) { return HtmlDiffRenderer.esc(s); }

    private static String safe(String s) { return s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static final String CSS =
            "body{font-family:Segoe UI,Arial,sans-serif;background:#f4f5f7;color:#1f2933;margin:0}"
            + ".wrap{max-width:1000px;margin:0 auto;padding:24px}"
            + "h1{margin:0 0 4px}h2{margin:26px 0 8px;font-size:18px;border-bottom:1px solid #d5dae1;padding-bottom:4px}h3{font-size:14px}"
            + ".sub{color:#52606d;margin-bottom:14px}"
            + ".banner{border-radius:8px;padding:14px 18px;margin:10px 0;border:1px solid}"
            + ".banner .bt{font-size:20px;font-weight:700;margin-bottom:4px}"
            + ".banner.ok{background:#e3f6e8;border-color:#2e9e57;color:#14532d}"
            + ".banner.warn{background:#fff6dd;border-color:#d9a400;color:#6b4e00}"
            + ".banner.bad{background:#fde8e8;border-color:#d64545;color:#7a1c1c}"
            + "table{border-collapse:collapse;width:100%;background:#fff;border:1px solid #d5dae1}"
            + "th,td{text-align:left;padding:8px 10px;border-bottom:1px solid #e6e9ee;vertical-align:top;word-break:break-word}"
            + "table th{width:210px;background:#f0f2f5;font-weight:600}"
            + ".tag{display:inline-block;font-size:11px;font-weight:700;padding:1px 7px;border-radius:10px;margin-right:6px}"
            + ".tag.na{background:#e5e7eb;color:#374151}.tag.unv{background:#ffe9b0;color:#6b4e00}.tag.pend{background:#dbeafe;color:#1e40af}"
            + ".note{color:#52606d;font-size:12px;margin-top:4px}.na{color:#52606d;padding:8px 0}"
            + ".diff{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px}"
            + ".diff-col pre{background:#1e1e2e;color:#e6e6e6;padding:10px;border-radius:6px;overflow-x:auto;font-size:12px;white-space:pre-wrap;word-break:break-word}"
            + ".diff-label{font-weight:700;font-size:12px;margin-bottom:4px}.diff-label.before{color:#c0392b}.diff-label.after{color:#1e8449}"
            + "a{color:#1d4ed8}";
}
