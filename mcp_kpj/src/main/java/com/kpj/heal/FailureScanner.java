package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Step 1 (detect) — reads the FAILED rows the framework already writes for a given test, from
 * {@code <outDir>/aggregate-data/*.tsv} (written by {@code DevHisBase.writeReport()}), and enriches each
 * with the URL/DOM-summary sidecar {@code DevHisBase} writes for FAIL steps
 * ({@code <outDir>/failures/*.txt}), when one exists.
 *
 * <p>This never runs a test itself — it only reads what the last run already produced. TSV files are
 * scanned by content (the {@code testId} column), not by filename, since the report filename is derived
 * from the page title, not the testId.</p>
 */
public class FailureScanner {

    /**
     * Set by {@link PostRunCoordinator} for an automatic post-run analysis: failure-detail sidecars last written before this instant
     * (epoch millis) belong to an EARLIER run and are ignored, so another run's page-state evidence is never attached to this run's
     * failures. Null (the default, and always for the manual agents) means no filtering - behaviour is exactly as before.
     */
    static volatile Long sidecarNotBeforeMillis = null;

    private final Path outDir;

    public FailureScanner(String outDir) {
        this.outDir = Paths.get(outDir);
    }

    /** All FAIL rows for the given testId across every aggregate-data TSV, enriched with sidecar detail. */
    public List<FailureRecord> scan(String testId) throws IOException {
        List<FailureRecord> out = new ArrayList<>();
        Path dataDir = outDir.resolve("aggregate-data");
        if (!Files.isDirectory(dataDir)) return out;

        Map<String, String> sidecars = loadSidecars(testId);

        List<Path> tsvs;
        try (var stream = Files.list(dataDir)) {
            tsvs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv"))
                    .collect(Collectors.toList());
        }

        for (Path tsv : tsvs) {
            for (String line : Files.readAllLines(tsv)) {
                String[] c = line.split("\t", -1);
                if (c.length < 6) continue;
                String status = c[0].trim(), page = c[1], rowTestId = c[2], step = c[3], expected = c[4], actual = c[5];
                if (!rowTestId.equals(testId) || !"FAIL".equalsIgnoreCase(status)) continue;
                FailureRecord rec = new FailureRecord(rowTestId, page, step, expected, actual);
                applySidecar(rec, sidecars.get(step));
                out.add(rec);
            }
        }
        return out;
    }

    /**
     * Positive evidence from ONE re-run of a test: which steps it recorded as PASS, read only from the
     * {@code aggregate-data} TSVs that were (re)written at or after {@code since}. A missing, unreadable or stale
     * (older than {@code since}) result therefore yields no evidence at all - the absence of a FAIL row is never
     * treated as a pass.
     */
    public static final class RunEvidence {
        /** At least one TSV with rows for the testId was written since the re-run started. */
        public final boolean freshOutputFound;
        /** Steps with a PASS row in those fresh TSVs. */
        public final Set<String> passedSteps;

        RunEvidence(boolean freshOutputFound, Set<String> passedSteps) {
            this.freshOutputFound = freshOutputFound;
            this.passedSteps = passedSteps;
        }
    }

    /** Tolerates coarse or slightly skewed file-system timestamps; far smaller than the minutes a real browser re-run takes. */
    private static final long FRESHNESS_TOLERANCE_MS = 2000;

    /** The PASS evidence for {@code testId} that a run started at {@code since} actually wrote (see {@link RunEvidence}). */
    public RunEvidence evidenceSince(String testId, java.time.Instant since) throws IOException {
        Set<String> passed = new LinkedHashSet<>();
        boolean fresh = false;
        Path dataDir = outDir.resolve("aggregate-data");
        if (!Files.isDirectory(dataDir)) return new RunEvidence(false, passed);
        List<Path> tsvs;
        try (var stream = Files.list(dataDir)) {
            tsvs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv"))
                    .collect(Collectors.toList());
        }
        long earliest = since.toEpochMilli() - FRESHNESS_TOLERANCE_MS;
        for (Path tsv : tsvs) {
            if (Files.getLastModifiedTime(tsv).toMillis() < earliest) continue; // written before this re-run: not evidence of it
            for (String line : Files.readAllLines(tsv)) {
                String[] c = line.split("\t", -1);
                if (c.length < 6 || !c[2].equals(testId)) continue;
                fresh = true;
                if ("PASS".equalsIgnoreCase(c[0].trim())) passed.add(c[3].trim());
            }
        }
        return new RunEvidence(fresh, passed);
    }

    /**
     * Full-project mode (requirement 1) — every distinct testId with at least one FAIL row across every
     * aggregate-data TSV. This is the discovery step for {@code --all}: it doesn't enrich records with
     * sidecar detail (call {@link #scan(String)} per id for that) — it only answers "which tests currently
     * have a failure at all", reusing the exact same file-listing this class already does for {@link #scan}.
     */
    public Set<String> listFailingTestIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        Path dataDir = outDir.resolve("aggregate-data");
        if (!Files.isDirectory(dataDir)) return ids;

        List<Path> tsvs;
        try (var stream = Files.list(dataDir)) {
            tsvs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv"))
                    .collect(Collectors.toList());
        }
        for (Path tsv : tsvs) {
            for (String line : Files.readAllLines(tsv)) {
                String[] c = line.split("\t", -1);
                if (c.length < 6) continue;
                if ("FAIL".equalsIgnoreCase(c[0].trim())) ids.add(c[2]);
            }
        }
        return ids;
    }

    /** step-name -> raw sidecar file content, for this testId's failure-detail files. */
    private Map<String, String> loadSidecars(String testId) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        Path failuresDir = outDir.resolve("failures");
        if (!Files.isDirectory(failuresDir)) return map;
        String prefix = testId.replaceAll("[^A-Za-z0-9._-]", "_") + "_";
        List<Path> files;
        try (var stream = Files.list(failuresDir)) {
            files = stream.filter(p -> p.getFileName().toString().startsWith(prefix)).collect(Collectors.toList());
        }
        Long notBefore = sidecarNotBeforeMillis;
        for (Path f : files) {
            if (notBefore != null && Files.getLastModifiedTime(f).toMillis() < notBefore) continue; // earlier run's evidence
            String content = Files.readString(f);
            String step = field(content, "step");
            if (!step.isEmpty()) map.put(step, content);
        }
        return map;
    }

    private void applySidecar(FailureRecord rec, String content) {
        if (content == null) return;
        rec.url = field(content, "url");
        rec.buttons = field(content, "buttons");
        rec.selects = field(content, "selects");
        rec.headings = field(content, "headings");
        rec.settledButtons = field(content, "settled_buttons");
        rec.settledSelects = field(content, "settled_selects");
        rec.settledHeadings = field(content, "settled_headings");
        rec.ngClicks = field(content, "ngclicks");
        rec.settledNgClicks = field(content, "settled_ngclicks");
        rec.selectOptions = field(content, "selectopts");
        rec.settledSelectOptions = field(content, "settled_selectopts");
    }

    private static String field(String content, String key) {
        for (String line : content.split("\n")) {
            if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
        }
        return "";
    }
}
