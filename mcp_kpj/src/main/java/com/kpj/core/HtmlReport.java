package com.kpj.core;

import java.nio.file.*;
import java.util.*;

/**
 * Builds the standard-format, self-contained HTML test report:
 *  - purple gradient header (#667eea -> #764ba2)
 *  - Test Summary table
 *  - Execution Steps table: # / Step Name / Description / Expected / Actual / Status / Screenshot
 *  - screenshots embedded as base64; Screenshot column links to in-page #shot_N anchors
 */
public final class HtmlReport {
    private HtmlReport() {}

    private static final String CSS =
      "*{box-sizing:border-box}body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f5f7;color:#2d2d2d;line-height:1.5}" +
      ".header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;padding:36px 40px;box-shadow:0 4px 12px rgba(0,0,0,.15)}" +
      ".header h1{margin:0 0 6px;font-size:28px;font-weight:600}.header p{margin:2px 0;opacity:.92;font-size:14px}" +
      ".container{max-width:1180px;margin:28px auto;padding:0 20px 60px}" +
      "h2{font-size:20px;color:#4a3b78;border-left:4px solid #764ba2;padding-left:12px;margin:34px 0 14px}" +
      "table{width:100%;border-collapse:collapse;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.08);border-radius:6px;overflow:hidden}" +
      "th,td{padding:11px 14px;text-align:left;border-bottom:1px solid #ececf1;font-size:14px;vertical-align:top}" +
      "thead th{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;font-weight:600;letter-spacing:.3px}" +
      "tbody tr:nth-child(even){background:#faf9fc}tbody tr:hover{background:#f0ecfa}" +
      ".summary-table td:first-child{font-weight:600;width:280px;color:#4a3b78}" +
      ".status{display:inline-block;padding:3px 10px;border-radius:12px;font-size:12px;font-weight:700;letter-spacing:.4px}" +
      ".pass{background:#e3f7e8;color:#1b8a3d;border:1px solid #b6e6c4}.manual{background:#fff3d6;color:#9a6b00;border:1px solid #f3dca0}" +
      ".fail{background:#fde2e2;color:#b3261e;border:1px solid #f3b0b0}" +
      ".step-num{text-align:center;font-weight:700;color:#764ba2;width:42px}" +
      "td a{color:#667eea;text-decoration:none;font-weight:600}td a:hover{text-decoration:underline;color:#764ba2}" +
      ".note{background:#fff8e6;border:1px solid #f0d99b;border-radius:6px;padding:12px 16px;margin:18px 0;font-size:13.5px;color:#7a5b10}" +
      ".footer{text-align:center;color:#9a9aa5;font-size:12px;margin-top:40px}" +
      ".gallery figure{margin:0 0 26px;background:#fff;border:1px solid #ececf1;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden}" +
      ".gallery figcaption{background:#f0ecfa;color:#4a3b78;font-weight:600;font-size:13px;padding:8px 14px;display:flex;justify-content:space-between;align-items:center}" +
      ".gallery figcaption .back{font-weight:500;font-size:12px}.gallery img{display:block;max-width:100%;height:auto}";

    /** steps rows: {n, name, desc, expected, actual, status, screenshotBase64}. Screenshots are embedded
     *  directly from the base64 in each step (index 6) — no image files are read from or written to disk. */
    public static void write(Path dir, String fileName, String title, String app, String module, String note,
                             List<String[]> summaryRows, List<String[]> steps) {
        long pass = steps.stream().filter(s -> s[5].equalsIgnoreCase("PASS")).count();
        long manual = steps.stream().filter(s -> s[5].equalsIgnoreCase("MANUAL")).count();
        long fail = steps.stream().filter(s -> s[5].equalsIgnoreCase("FAIL")).count();

        StringBuilder sum = new StringBuilder();
        for (String[] kv : summaryRows) sum.append("<tr><td>").append(esc(kv[0])).append("</td><td>").append(esc(kv[1])).append("</td></tr>");
        sum.append("<tr><td>Total / Passed / Manual / Failed</td><td>").append(steps.size()).append(" / ").append(pass)
           .append(" / ").append(manual).append(" / ").append(fail).append("</td></tr>");
        sum.append("<tr><td>Overall Result</td><td><span class='status ").append(fail==0?"pass'>PASS":"fail'>FAIL").append("</span></td></tr>");

        StringBuilder rows = new StringBuilder(), gal = new StringBuilder();
        for (String[] s : steps) {
            String cls = s[5].equalsIgnoreCase("PASS") ? "pass" : s[5].equalsIgnoreCase("MANUAL") ? "manual" : "fail";
            boolean hasShot = s[6] != null && !s[6].isEmpty();
            rows.append("<tr><td class='step-num'>").append(s[0]).append("</td><td>").append(esc(s[1])).append("</td><td>")
                .append(esc(s[2])).append("</td><td>").append(esc(s[3])).append("</td><td>").append(esc(s[4]))
                .append("</td><td><span class='status ").append(cls).append("'>").append(s[5])
                .append("</span></td><td>").append(hasShot ? "<a href='#shot_" + s[0] + "'>View</a>" : "&ndash;").append("</td></tr>");
        }
        for (String[] s : steps) {
            if (s[6] == null || s[6].isEmpty()) continue;   // no screenshot for this step
            gal.append("<figure id='shot_").append(s[0]).append("'><figcaption>Step ").append(s[0]).append(": ").append(esc(s[1]))
               .append(" <a href='#top' class='back'>back to top</a></figcaption>")
               .append("<img alt='Step ").append(s[0]).append("' src='data:image/png;base64,").append(s[6]).append("'></figure>");
        }

        String noteHtml = (note == null || note.isBlank()) ? "" : "<div class='note'>" + note + "</div>";
        String html = "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'><title>" + esc(title) + " Report</title>" +
            "<style>" + CSS + "</style></head><body>" +
            "<div class='header'><h1>" + esc(title) + " &ndash; Test Execution Report</h1>" +
            "<p><strong>Application:</strong> " + esc(app) + "</p>" +
            "<p><strong>Module:</strong> " + esc(module) + "</p></div>" +
            "<div class='container' id='top'>" + noteHtml +
            "<h2>Test Summary</h2><table class='summary-table'><tbody>" + sum + "</tbody></table>" +
            "<h2>Execution Steps</h2><table><thead><tr><th>#</th><th>Step Name</th><th>Description</th><th>Expected</th>" +
            "<th>Actual</th><th>Status</th><th>Screenshot</th></tr></thead><tbody>" + rows + "</tbody></table>" +
            "<h2>Screenshots</h2><div class='gallery'>" + gal + "</div>" +
            "<div class='footer'>Generated by mcp_kpj (Playwright Java) &middot; DevHIS</div></div></body></html>";

        try { Files.write(dir.resolve(fileName), html.getBytes("UTF-8")); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
