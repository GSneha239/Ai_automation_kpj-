package com.kpj.pages.Report_page.Report_Console;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Report &gt; <b>Report Console</b> &gt; <b>Master Report</b> - Page Object (route {@code #/reportconsole}).
 *
 * <p>The console is the same as for the Investigation Reports (category buttons on the left, the Reports and Filter dropdowns, the Filter Name | Filter Value table with an Add Value popup per filter), so every generic
 * step - choosing the category and the report, adding a filter, ticking a row of its popup, OK - is inherited from {@link Investigation_Reports}; only the category button ({@code #btnli0MasterReport}) is different.</p>
 *
 * <p><b>Show Report does not always open a report tab:</b> a Master report is produced as a <b>file</b> - the console builds it in the browser and clicks a {@code blob:} link, so the browser <i>downloads</i> it (a CSV).
 * {@link #showReportAndCaptureDownload} clicks Show Report, waits for that download (or for the console's "No Data Available!" toast), saves the file and reads it, so a test can check that it was generated, which report it is
 * and that it lists records.</p>
 */
public class Master_Report extends Investigation_Reports {

    public static final String CATEGORY = "Master Report";

    public Master_Report(Page page) { super(page); }

    /** What Show Report produced when the report is downloaded as a file. */
    public static final class DownloadResult {
        /** A file was downloaded. */
        public boolean downloaded;
        public String filename = "";
        public String url = "";
        /** Where the file was saved. */
        public String savedTo = "";
        public long bytes;
        /** Playwright's failure text for the download ("" = completed). */
        public String failure = "";
        /** The file read as UTF-8 text (BOM removed); "" when nothing was downloaded. */
        public String text = "";
        /** The file's non-blank lines. */
        public List<String> lines = new ArrayList<>();
        /** The file is a web page or an error text, not data (e.g. an HTML error page saved as the report). */
        public boolean looksLikeHtml;
        /** The file is a zip container (an .xlsx / .zip), not text. */
        public boolean looksLikeZip;
        public String diagnostics = "";
        /** When nothing was downloaded: what the screen said instead (e.g. the "No Data Available!" toast). */
        public String errorText = "";
    }

    /** The data rows shown at the top / bottom of the CSV evidence picture; a longer file says how many rows are left out in between. */
    private static final int EVIDENCE_FIRST_ROWS = 40, EVIDENCE_LAST_ROWS = 5;

    /** CSV text as rows of fields (a quoted field may hold commas, doubled quotes and line breaks); rows whose fields are all blank are left out. */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        final String s = text == null ? "" : text;
        for (int i = 0; i <= s.length(); i++) {
            final boolean end = i == s.length();
            final char c = end ? '\n' : s.charAt(i);
            if (quoted && !end) {
                if (c != '"') field.append(c);
                else if (i + 1 < s.length() && s.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = false;
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString()); field.setLength(0);
                if (row.stream().anyMatch(f -> !f.trim().isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        return rows;
    }

    private static String html(String s) {
        return String.valueOf(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * The <b>evidence picture</b> of a downloaded CSV. A CSV has no screen of its own in the application, so the downloaded file is shown as a table in a temporary tab (file name, size, row and column count above it) and that
     * tab is photographed; the tab is closed again and the console is brought back to the front. A long file shows its first {@value #EVIDENCE_FIRST_ROWS} and last {@value #EVIDENCE_LAST_ROWS} data rows and says how many are left out.
     * The picture is also written to {@code pngFile}. Returns the PNG, or {@code null} when it could not be made (the reason is added to {@link #problems}).
     */
    public byte[] screenshotCsv(DownloadResult r, String reportName, Path pngFile) {
        final List<List<String>> rows = parseCsv(r.text);
        final boolean empty = rows.isEmpty();   // a completely empty download is evidence too: the picture says so
        final List<String> header = empty ? new ArrayList<>() : rows.get(0);
        final int data = Math.max(0, rows.size() - 1);
        int columns = header.size();
        for (List<String> row : rows) columns = Math.max(columns, row.size());
        final boolean cut = data > EVIDENCE_FIRST_ROWS + EVIDENCE_LAST_ROWS + 3;

        StringBuilder t = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>CSV evidence</title><style>"
                + "body{font-family:'Segoe UI',Arial,sans-serif;margin:16px;color:#1f2328;background:#fff}h2{margin:0 0 4px;font-size:18px}.meta{font-size:12.5px;color:#444c56;margin-bottom:10px;word-break:break-all}"
                + "table{border-collapse:collapse;font-size:12px;width:calc(100% - 24px)}th,td{border:1px solid #c9ced6;padding:3px 8px;text-align:left;vertical-align:top}th{background:#8f7564;color:#fff}tr:nth-child(even) td{background:#f6f8fa}"
                + "td.n,th.n{width:40px;text-align:right}td.n{color:#6e7781;background:#eef0f2}tr.gap td{text-align:center;color:#6e5a00;background:#fff8e1;font-style:italic}</style></head><body>");
        t.append("<h2>").append(html(reportName)).append(" - downloaded CSV</h2><div class=\"meta\">File: <b>").append(html(r.filename.replace('_', ' '))).append("</b> &middot; ").append(r.bytes).append(" bytes &middot; <b>")
         .append(data).append(" data row(s)</b>, ").append(columns).append(" column(s) &middot; pictured ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
         .append(cut ? " &middot; shown: the first " + EVIDENCE_FIRST_ROWS + " and the last " + EVIDENCE_LAST_ROWS + " rows" : " &middot; every row shown").append("<br>Saved as: ").append(html(r.savedTo)).append("</div><table><tr><th class=\"n\">#</th>");
        for (int c = 0; c < columns; c++) t.append("<th>").append(html(c < header.size() ? header.get(c) : "")).append("</th>");
        t.append("</tr>");
        for (int i = 1; i <= data; i++) {
            if (cut && i == EVIDENCE_FIRST_ROWS + 1) {
                t.append("<tr class=\"gap\"><td colspan=\"").append(columns + 1).append("\">... ").append(data - EVIDENCE_FIRST_ROWS - EVIDENCE_LAST_ROWS).append(" row(s) not shown (they are in the CSV file) ...</td></tr>");
                i = data - EVIDENCE_LAST_ROWS;
                continue;
            }
            List<String> row = rows.get(i);
            t.append("<tr><td class=\"n\">").append(i).append("</td>");
            for (int c = 0; c < columns; c++) t.append("<td>").append(html(c < row.size() ? row.get(c) : "")).append("</td>");
            t.append("</tr>");
        }
        if (data == 0) t.append("<tr class=\"gap\"><td colspan=\"").append(columns + 1).append("\">").append(empty ? "THE DOWNLOADED FILE IS EMPTY - no header line and no data rows (" + r.bytes + " bytes in all)" : "no data rows - only the header").append("</td></tr>");
        t.append("</table></body></html>");

        Page viewer = null;
        try {
            viewer = page.context().newPage();
            try { viewer.setViewportSize(1500, 900); } catch (Exception ignore) { }   // a fixed width, so the picture does not depend on the window (and the table is never cut at the right edge)
            viewer.setContent(t.toString());
            viewer.waitForTimeout(300);
            byte[] png = viewer.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(30000));
            if (pngFile != null) Files.write(pngFile, png);
            return png;
        } catch (Exception e) {
            problems.add("CSV picture: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return null;
        } finally {
            if (viewer != null) { try { viewer.close(); } catch (Exception ignore) { } }
            try { page.bringToFront(); } catch (Exception ignore) { }
        }
    }

    /**
     * Click <b>Show Report</b> and wait (up to {@code waitMs}) for the file the console downloads; the file is saved into {@code dir} (created) as {@code <time>_<its name>} and read.
     * When the console answers with a toast ("No Data Available!") and no download follows, it stops waiting a few seconds after the toast and returns {@code downloaded=false} with the toast in {@code errorText}.
     */
    public DownloadResult showReportAndCaptureDownload(Path dir, int waitMs) {
        DownloadResult r = new DownloadResult();
        final AtomicReference<Download> got = new AtomicReference<>();
        final Consumer<Download> handler = got::set;
        page.onDownload(handler);
        boolean clicked;
        final long asked = System.currentTimeMillis();
        long secondsToDownload = 0;
        try {
            clicked = clickShowReport();
            long deadline = System.currentTimeMillis() + waitMs;
            while (clicked && got.get() == null && System.currentTimeMillis() < deadline) {
                // the console refuses a report with no data by a warning toast and downloads nothing: give a download 3 s after that, then stop waiting
                if (seenToasts().toLowerCase().contains("no data")) {
                    page.waitForTimeout(3000);
                    if (got.get() == null) break;
                }
                page.waitForTimeout(400);
            }
            secondsToDownload = Math.round((System.currentTimeMillis() - asked) / 1000.0);
        } finally {
            page.offDownload(handler);
        }
        Download d = got.get();
        if (d == null) {
            String seen = seenToasts();
            r.errorText = seen;
            r.diagnostics = !clicked ? "Show Report could not be clicked: " + problems
                    : "no file was downloaded" + (seen.isEmpty() ? " within " + (waitMs / 1000) + " s" : " - the screen said: \"" + seen + "\"");
            return r;
        }
        r.downloaded = true;
        r.url = String.valueOf(d.url());
        r.filename = d.suggestedFilename() == null ? "" : d.suggestedFilename();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + (r.filename.isEmpty() ? "report" : r.filename).replaceAll("[^A-Za-z0-9._-]", "_"));
            d.saveAs(target);
            String failure = d.failure();
            r.failure = failure == null ? "" : failure;
            r.savedTo = target.toAbsolutePath().toString();
            r.bytes = Files.size(target);
            byte[] data = Files.readAllBytes(target);
            r.looksLikeZip = data.length > 3 && data[0] == 'P' && data[1] == 'K';
            if (!r.looksLikeZip) {
                String t = new String(data, StandardCharsets.UTF_8);
                if (t.startsWith("﻿")) t = t.substring(1);
                r.text = t;
                for (String line : t.split("\\r?\\n")) if (!line.trim().isEmpty()) r.lines.add(line);
                String head = t.trim().toLowerCase();
                r.looksLikeHtml = head.startsWith("<!doctype") || head.startsWith("<html") || head.contains("server error in");
            }
            r.diagnostics = "downloaded \"" + r.filename + "\" (" + r.bytes + " bytes, " + (r.looksLikeZip ? "a zip container, not a CSV" : r.lines.size() + " non-blank line(s)") + ") about " + secondsToDownload + " s after Show Report, from " + r.url
                    + (r.failure.isEmpty() ? "" : " - the download FAILED: " + r.failure);
        } catch (Exception e) {
            r.diagnostics = "a file \"" + r.filename + "\" was downloaded but could not be saved / read: " + String.valueOf(e.getMessage()).split("\n")[0];
            r.failure = r.failure.isEmpty() ? "could not be saved" : r.failure;
        }
        return r;
    }

    // ---- private data -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** What a hidden cell shows in the saved copy, the report and the evidence picture. */
    public static final String HIDDEN = "[hidden]";

    /** A column name (lower case, letters and digits only) that shows a file holds private data: passwords, bank or tax numbers, identity numbers, birth dates, phone numbers, e-mail addresses. */
    private static final Pattern PRIVATE_COLUMN = Pattern.compile(
            "password|passwd|^pwd$|bankaccount|bankacc(ount)?no|^accountno$|identification(number|no|nmber)|^ic(no|number)$|nric|passport|birthdate|dateofbirth|^dob$|mobile|telephone|^(home|office|res|company|primary|contact)?tel(no|number)?\\d*$|^phone$|phone(no|number)|^contactno$|email|^tin(no|number)$|taxidentification");

    /** Further personal columns, hidden only in a file that also has one of the above (the Nationality master list is not private data; a doctor's nationality is). */
    private static final Pattern PERSONAL_COLUMN = Pattern.compile("loginname|username|maritalstatus|nationality|^gender$|^sex$|address|postcode|postalcode|zipcode|contactperson|fax|tourismtax|sstregist|^nmc$|^nsr$");

    /**
     * Hides private data BEFORE anything else is written from a downloaded report. When the header has a private column, every filled cell of the private and personal columns becomes {@value #HIDDEN} - in {@code r.text} /
     * {@code r.lines} (what the HTML report and the evidence picture are made from) and in the saved copy of the file. The number of rows and columns, and which cells are blank, stay as downloaded. A row whose column
     * count differs from the header's keeps only its first cell (a private value could sit in any of the others). If the saved copy cannot be rewritten it is removed, so that no unhidden copy stays behind.
     *
     * @return the names of the hidden columns; empty = nothing private, and the file stays exactly as downloaded
     */
    public List<String> hidePrivateColumns(DownloadResult r) {
        List<String> hidden = new ArrayList<>();
        if (r == null || !r.downloaded || r.looksLikeZip) return hidden;
        List<List<String>> rows = parseCsv(r.text);
        if (rows.isEmpty()) return hidden;
        List<String> header = rows.get(0);
        if (header.stream().noneMatch(h -> PRIVATE_COLUMN.matcher(columnKey(h)).find())) return hidden;
        List<Integer> columns = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            String key = columnKey(header.get(c));
            if (PRIVATE_COLUMN.matcher(key).find() || PERSONAL_COLUMN.matcher(key).find()) { columns.add(c); hidden.add(header.get(c).trim()); }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = new ArrayList<>(rows.get(i));
            if (i > 0 && row.size() != header.size()) for (int c = 1; c < row.size(); c++) { if (!row.get(c).trim().isEmpty()) row.set(c, HIDDEN); }
            else if (i > 0) for (int c : columns) { if (!row.get(c).trim().isEmpty()) row.set(c, HIDDEN); }
            for (int c = 0; c < row.size(); c++) {
                String v = row.get(c);
                if (c > 0) out.append(',');
                out.append(v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r") ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
            }
            out.append("\r\n");
        }
        r.text = out.toString();
        r.lines = new ArrayList<>();
        for (String line : r.text.split("\\r?\\n")) if (!line.trim().isEmpty()) r.lines.add(line);
        if (!r.savedTo.isEmpty()) {
            Path saved = Paths.get(r.savedTo);
            try {
                Files.write(saved, ("﻿" + r.text).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                try { Files.deleteIfExists(saved); } catch (Exception ignore) { }
                problems.add("the copy with the private data hidden could not be written, so no copy was kept: " + String.valueOf(e.getMessage()).split("\n")[0]);
                r.savedTo = "";
            }
        }
        return hidden;
    }

    private static String columnKey(String header) {
        return header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
