package com.kpj.heal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only helper that surfaces a screenshot the standard test report ({@code com.kpj.core.HtmlReport}, via
 * {@code DevHisBase.step()}) already captured and embedded as base64 — for the Failure Analysis and
 * Self-Healing reports to show at the exact failure point, WITHOUT DevHisBase ever needing to change or take
 * a second screenshot. {@code DevHisBase} already captures one screenshot per step (PASS/MANUAL/FAIL) and
 * embeds it directly in {@code test-output/reports/&lt;Test&gt;.html} — this class only reads that existing
 * file back out.
 */
public final class ScreenshotExtractor {
    private ScreenshotExtractor() {}

    /**
     * Locates the standard per-test report file for {@code testId}. The report is named after the page
     * title, not the testId, so this never guesses a filename — it finds the {@code aggregate-data} TSV
     * whose testId column matches (that TSV's filename is always identical to its report's, just with a
     * {@code .tsv} extension instead of {@code .html} — see {@code DevHisBase.writeReport()}), then resolves
     * the sibling report file.
     */
    public static Path findReportFile(String outDir, String testId) {
        if (testId == null || testId.isBlank()) return null;
        Path dataDir = Paths.get(outDir, "aggregate-data");
        if (!Files.isDirectory(dataDir)) return null;
        List<Path> tsvs;
        try (var stream = Files.list(dataDir)) {
            tsvs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return null;
        }
        for (Path tsv : tsvs) {
            try {
                for (String line : Files.readAllLines(tsv)) {
                    String[] c = line.split("\t", -1);
                    if (c.length >= 3 && testId.equals(c[2])) {
                        String base = tsv.getFileName().toString().replaceFirst("\\.tsv$", ".html");
                        Path html = Paths.get(outDir, "reports", base);
                        return Files.isRegularFile(html) ? html : null;
                    }
                }
            } catch (IOException ignore) { /* try the next tsv */ }
        }
        return null;
    }

    /**
     * Extracts the base64 PNG (no {@code data:image/png;base64,} prefix) embedded for the step whose name
     * matches {@code stepName} in the given standard test report, or {@code null} if the report, the step,
     * or its screenshot can't be found (best-effort — a report a future run may not have this exact step
     * name in is not an error). Matches the LAST occurrence, so a re-run's freshest screenshot for a
     * repeated step name is the one returned.
     */
    public static String extractStepScreenshot(Path reportHtml, String stepName) {
        if (reportHtml == null || stepName == null || stepName.isBlank() || !Files.isRegularFile(reportHtml)) return null;
        try {
            String html = Files.readString(reportHtml);
            String needle = ": " + escapeLikeReport(stepName) + " <a href='#top'";
            int at = html.lastIndexOf(needle);
            if (at < 0) return null;
            int imgAt = html.indexOf("data:image/png;base64,", at);
            if (imgAt < 0) return null;
            int start = imgAt + "data:image/png;base64,".length();
            int end = html.indexOf('\'', start);
            if (end < 0) return null;
            return html.substring(start, end);
        } catch (IOException e) {
            return null;
        }
    }

    /** Convenience: {@link #findReportFile} + {@link #extractStepScreenshot} in one call. */
    public static String extractStepScreenshot(String outDir, String testId, String stepName) {
        return extractStepScreenshot(findReportFile(outDir, testId), stepName);
    }

    /** Same escaping {@code com.kpj.core.HtmlReport} applies to step names before writing them into the
     *  report (&amp;, &lt;, &gt; only) — so the search text matches what's actually on disk. */
    private static String escapeLikeReport(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
