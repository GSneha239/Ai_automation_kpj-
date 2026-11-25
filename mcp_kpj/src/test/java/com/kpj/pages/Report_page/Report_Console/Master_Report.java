package com.kpj.pages.Report_page.Report_Console;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
            r.diagnostics = "downloaded \"" + r.filename + "\" (" + r.bytes + " bytes, " + (r.looksLikeZip ? "a zip container, not a CSV" : r.lines.size() + " non-blank line(s)") + ") from " + r.url
                    + (r.failure.isEmpty() ? "" : " - the download FAILED: " + r.failure);
        } catch (Exception e) {
            r.diagnostics = "a file \"" + r.filename + "\" was downloaded but could not be saved / read: " + String.valueOf(e.getMessage()).split("\n")[0];
            r.failure = r.failure.isEmpty() ? "could not be saved" : r.failure;
        }
        return r;
    }
}
