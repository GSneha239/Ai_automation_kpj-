package com.kpj.core;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans the per-run {@code result.tsv} files that {@link DevHisBase} writes and produces a single
 * <b>failed-steps report</b> ({@code test-output/FAILED_STEPS.html}) that lists every FAILED step with
 * its <b>page name</b>. Also writes the run's exit code (0 = all steps passed, 1 = one or more failed)
 * to {@code test-output/aggregate-exit.txt} so the CI batch file can gate the build on it.
 *
 * <p>Usage: {@code java com.kpj.core.ReportAggregator [markerFile]} — if {@code markerFile} is given,
 * only report folders modified at/after that file's timestamp are counted (so only the CURRENT run's
 * reports are aggregated). Omit it to scan every report under {@code test-output/reports}.</p>
 */
public class ReportAggregator {

    public static void main(String[] args) {
        // Output folder is configurable (-Ddevhis.outdir) so each environment aggregates its own reports.
        Path root = Paths.get(System.getProperty("devhis.outdir", "test-output"));
        // Per-step data lives in the hidden aggregate-data folder (the reports folder is HTML-only). Fall back to
        // reports/ for older runs whose .tsv were still written there.
        Path dataDir = root.resolve("aggregate-data");
        Path reportsDir = Files.isDirectory(dataDir) ? dataDir : root.resolve("reports");

        long since = 0L, startMs = 0L;
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            try {
                Path m = Paths.get(args[0]);
                if (Files.exists(m)) { startMs = Files.getLastModifiedTime(m).toMillis(); since = startMs - 2000L; }
            } catch (Exception ignore) { }
        }
        long endMs = System.currentTimeMillis();
        // Run start = the .runmarker's timestamp; end = now (the aggregator runs after all flows finish).
        java.time.format.DateTimeFormatter DTF = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        java.util.function.LongFunction<String> fmt = ms -> ms <= 0 ? "-"
                : java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault()).format(DTF);
        String startStr = fmt.apply(startMs), endStr = fmt.apply(endMs);
        String durStr;
        if (startMs > 0) { long sec = Math.max(0, (endMs - startMs) / 1000); durStr = (sec / 60) + "m " + (sec % 60) + "s"; }
        else durStr = "-";

        List<String[]> fails = new ArrayList<>();   // {page, testId, step, expected, actual}
        List<String[]> runs = new ArrayList<>();    // {page, testId, pass, fail, manual}
        int totalSteps = 0, totalFail = 0, totalManual = 0;
        Set<String> failedPages = new LinkedHashSet<>();

        try {
            if (Files.isDirectory(reportsDir)) {
                // Reports are written flat: test-output/reports/<Page>.html + <Page>.tsv (one per test).
                List<Path> tsvs = Files.list(reportsDir)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".tsv"))
                        .sorted().collect(Collectors.toList());
                for (Path tsv : tsvs) {
                    if (since > 0) {
                        try { if (Files.getLastModifiedTime(tsv).toMillis() < since) continue; } catch (Exception ignore) { }
                    }
                    int pass = 0, fail = 0, manual = 0;
                    String page = "", testId = "";
                    for (String line : Files.readAllLines(tsv)) {
                        String[] c = line.split("\t", -1);
                        if (c.length < 6) continue;
                        String status = c[0].trim();
                        page = c[1]; testId = c[2];
                        totalSteps++;
                        if (status.equalsIgnoreCase("FAIL")) {
                            fail++; totalFail++; failedPages.add(page);
                            fails.add(new String[]{page, testId, c[3], c[4], c[5]});
                        } else if (status.equalsIgnoreCase("MANUAL")) {
                            manual++; totalManual++;
                        } else {
                            pass++;
                        }
                    }
                    if (!page.isEmpty()) runs.add(new String[]{page, testId, String.valueOf(pass), String.valueOf(fail), String.valueOf(manual)});
                }
            }
        } catch (Exception e) {
            System.out.println("ReportAggregator: scan error - " + e.getMessage());
        }

        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("FAILED_STEPS.html"), buildHtml(runs, fails, totalSteps, totalFail, totalManual, startStr, endStr, durStr));
        } catch (Exception e) {
            System.out.println("ReportAggregator: could not write FAILED_STEPS.html - " + e.getMessage());
        }
        try { Files.writeString(root.resolve("aggregate-exit.txt"), totalFail > 0 ? "1" : "0"); } catch (Exception ignore) { }

        System.out.println("============================================================");
        System.out.println(" Aggregate : " + totalSteps + " steps | " + totalFail + " FAILED | "
                + totalManual + " MANUAL | pages with failures: " + failedPages.size());
        for (String[] f : fails) System.out.println("   FAIL [" + f[0] + "] " + f[2] + "  ->  " + f[4]);
        System.out.println(" Report    : " + root.resolve("FAILED_STEPS.html").toAbsolutePath());
        System.out.println(" Exit code : " + (totalFail > 0 ? 1 : 0));
        System.out.println("============================================================");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String buildHtml(List<String[]> runs, List<String[]> fails, int steps, int fail, int manual,
                                    String startStr, String endStr, String durStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'><title>Failed Steps Report</title><style>")
          .append("body{font-family:Segoe UI,Arial,sans-serif;margin:0;background:#f5f5f7;color:#222}")
          .append("header{background:#5b2c8f;color:#fff;padding:18px 24px}header h1{margin:0;font-size:20px}header .sub{opacity:.9;font-size:13px;margin-top:4px}")
          .append(".wrap{padding:20px 24px}h3{color:#5b2c8f}")
          .append("table{border-collapse:collapse;width:100%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.1);margin-bottom:26px}")
          .append("th,td{border:1px solid #e3e3e8;padding:8px 10px;text-align:left;font-size:13px;vertical-align:top}")
          .append("th{background:#efe9f6;color:#5b2c8f}tr:nth-child(even) td{background:#faf9fc}")
          .append(".fail{color:#c0392b;font-weight:600}.ok{color:#1e8449;font-weight:600}")
          .append(".pill{display:inline-block;padding:2px 9px;border-radius:10px;font-size:12px}.pfail{background:#fdecea;color:#c0392b}.ppass{background:#e9f7ef;color:#1e8449}")
          .append("</style></head><body>");
        sb.append("<header><h1>Failed Steps Report</h1><div class='sub'>")
          .append(steps).append(" steps &middot; <b>").append(fail).append("</b> failed &middot; ").append(manual).append(" manual</div>")
          .append("<div class='sub'>Start: <b>").append(esc(startStr)).append("</b> &middot; End: <b>").append(esc(endStr))
          .append("</b> &middot; Duration: <b>").append(esc(durStr)).append("</b></div></header><div class='wrap'>");

        sb.append("<h3>Runs</h3><table><tr><th>Page</th><th>Test</th><th>Passed</th><th>Failed</th><th>Manual</th><th>Result</th></tr>");
        for (String[] r : runs) {
            boolean ok = "0".equals(r[3]);
            sb.append("<tr><td>").append(esc(r[0])).append("</td><td>").append(esc(r[1])).append("</td><td>").append(r[2])
              .append("</td><td class='").append(ok ? "ok" : "fail").append("'>").append(r[3]).append("</td><td>").append(r[4])
              .append("</td><td><span class='pill ").append(ok ? "ppass" : "pfail").append("'>").append(ok ? "PASS" : "FAIL").append("</span></td></tr>");
        }
        sb.append("</table>");

        sb.append("<h3>Failed steps</h3>");
        if (fails.isEmpty()) {
            sb.append("<p class='ok'>No failed steps &#127881;</p>");
        } else {
            sb.append("<table><tr><th>Page</th><th>Test</th><th>Step</th><th>Expected</th><th>Actual / Reason</th></tr>");
            for (String[] f : fails) {
                sb.append("<tr><td>").append(esc(f[0])).append("</td><td>").append(esc(f[1])).append("</td><td>").append(esc(f[2]))
                  .append("</td><td>").append(esc(f[3])).append("</td><td class='fail'>").append(esc(f[4])).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
