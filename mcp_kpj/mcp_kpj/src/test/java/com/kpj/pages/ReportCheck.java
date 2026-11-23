package com.kpj.pages;

import com.microsoft.playwright.Page;

/**
 * Finds the report a screen generates on Save, and judges whether it is <b>blank</b>.
 *
 * <p>Shared by the certificate screens, which open their report as a new tab (an {@code .aspx} PDF) or
 * as an in-page {@code iframe}/{@code embed}/{@code object}.</p>
 *
 * <h2>Why blankness is measured in pixels, on the paper only</h2>
 *
 * <p>A text check cannot work: Chrome's PDF viewer exposes no innerText, so every PDF would read as
 * blank. Measuring the whole screenshot does not work either — the viewer surrounds the page with a dark
 * toolbar, a thumbnail rail and a grey backdrop, and those alone made an <b>empty</b> Birth Certificate
 * score 51% "covered". Nor can the qualifying columns simply be spanned min-to-max: that merges the page
 * with the viewer's near-white scrollbar, and the dark gap between them counts as ink (21% on the same
 * empty page). So the paper is located as the longest CONTIGUOUS run of mostly-white columns and rows,
 * and only what sits inside it counts.</p>
 */
public class ReportCheck {

    /** Where the report was found — empty when Save produced none. */
    public String url = "";
    /** Full evidence line for the report: location, bytes, ink, and the verdict. */
    public String diagnostics = "";
    /** Where the screenshot was saved, if it was. */
    public String file = "";
    /** True when a report WAS produced but carries no content. */
    public boolean blank = false;
    public byte[] png = null;

    /** A report was produced at all — a new tab or an embedded frame. */
    public boolean produced() { return url != null && !url.isEmpty(); }

    /**
     * Look for the report Save was supposed to generate.
     *
     * @param page       the screen's page
     * @param tabsBefore how many tabs existed before Save was clicked
     * @param waitMs     how long to wait for a report tab to appear
     * @param saveAs     file name (under {@code test-output/reports}) for the screenshot, or null
     */
    public static ReportCheck after(Page page, int tabsBefore, int waitMs, String saveAs) {
        ReportCheck rc = new ReportCheck();

        Page report = null;
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            for (Page p : page.context().pages()) {
                if (p != page && !p.isClosed()) { report = p; break; }
            }
            if (report != null) break;
            page.waitForTimeout(500);
        }

        String inPage = "";
        if (report == null) {
            // No new tab — the report may be embedded in the screen itself.
            Object f = page.evaluate("() => { const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const e=[...document.querySelectorAll('iframe,embed,object')].filter(vis)"
                    + "   .find(x=>/\\.(pdf|aspx)|report/i.test(x.src||x.data||''));"
                    + " return e? (e.src||e.data||'') : ''; }");
            inPage = f == null ? "" : f.toString();
        }

        if (report == null && inPage.isEmpty()) {
            rc.diagnostics = "NO REPORT WAS PRODUCED: Save opened no new tab (" + tabsBefore
                    + " before, " + page.context().pages().size() + " after) and the screen shows no "
                    + "embedded report frame.";
            System.out.println("ReportCheck -> " + rc.diagnostics);
            return rc;
        }

        String url = report != null ? report.url() : inPage;
        if (url.startsWith("/")) url = "https://devhis.sancyberhad.com" + url;
        rc.url = url;

        // Measure the bytes too, where the server allows it: an empty response is decisive on its own.
        String bytesInfo;
        int bytes = -1;
        try {
            com.microsoft.playwright.APIResponse r = page.context().request().get(url);
            byte[] body = r.body();
            bytes = body == null ? 0 : body.length;
            String ct = r.headers().get("content-type");
            bytesInfo = r.status() + " " + (ct == null ? "?" : ct) + ", " + bytes + " bytes";
        } catch (Exception e) {
            bytesInfo = "(bytes not fetched: " + String.valueOf(e.getMessage()).replaceAll("\\s+", " ")
                    .replaceAll("^(.{0,60}).*", "$1") + ")";
        }

        String pixels = "(not rendered)";
        boolean flat = false;
        if (report != null) {
            try { report.waitForLoadState(); } catch (Exception ignore) { }
            report.waitForTimeout(3000);
            try {
                report.bringToFront();
                report.waitForTimeout(1000);
                rc.png = report.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000));
                double ink = inkRatio(rc.png);
                // 0.2% of the paper: ordinary printed text covers several percent.
                flat = ink >= 0 && ink < 0.002;
                pixels = ink < 0 ? "(could not locate the printed page in the image)"
                        : String.format("%.4f%% of the printed page carries ink", ink * 100);
                if (saveAs != null && !saveAs.isBlank()) {
                    // Keep the image: "blank" is a claim, and the page itself is the evidence.
                    try {
                        java.nio.file.Path out = java.nio.file.Paths.get("test-output", "reports", saveAs);
                        java.nio.file.Files.createDirectories(out.getParent());
                        java.nio.file.Files.write(out, rc.png);
                        rc.file = out.toAbsolutePath().toString();
                        pixels += "; image saved to " + rc.file;
                    } catch (Exception ignore) { }
                }
            } catch (Exception e) {
                pixels = "(screenshot failed: " + e.getMessage().split("\n")[0] + ")";
            }
            try { if (!report.isClosed()) report.close(); } catch (Exception ignore) { }
            page.bringToFront();
        }

        boolean tinyBytes = bytes >= 0 && bytes < 1024;
        rc.blank = tinyBytes || flat;
        rc.diagnostics = "report at " + rc.url
                + (report != null ? " (new tab)" : " (embedded frame)")
                + " — " + bytesInfo + "; " + pixels
                + (rc.blank
                    ? "  ==> BLANK: "
                      + (tinyBytes ? "the response carries no real content" : "")
                      + (tinyBytes && flat ? " and " : "")
                      + (flat ? "the printed page has nothing on it" : "")
                    : "  ==> the report carries content");
        System.out.println("ReportCheck -> " + rc.diagnostics);
        return rc;
    }

    /**
     * Fraction of the PRINTED PAGE that carries ink.
     *
     * @return the ink ratio inside the page, or -1 if the image or the page could not be read
     */
    public static double inkRatio(byte[] png) {
        if (png == null || png.length == 0) return -1;
        try {
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) return -1;
            int w = img.getWidth(), h = img.getHeight();
            java.util.function.BiPredicate<Integer, Integer> white = (x, y) -> {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                return r > 235 && g > 235 && b > 235;
            };

            boolean[] colWhite = new boolean[w];
            for (int x = 0; x < w; x++) {
                int c = 0;
                for (int y = 0; y < h; y += 2) if (white.test(x, y)) c++;
                colWhite[x] = c > (h / 2) * 0.5;
            }
            int[] xr = longestRun(colWhite);
            int x0 = xr[0], x1 = xr[1];
            if (x0 < 0 || x1 - x0 < 50) return -1;

            boolean[] rowWhite = new boolean[h];
            for (int y = 0; y < h; y++) {
                int c = 0;
                for (int x = x0; x <= x1; x += 2) if (white.test(x, y)) c++;
                rowWhite[y] = c > ((x1 - x0) / 2) * 0.5;
            }
            int[] yr = longestRun(rowWhite);
            int y0 = yr[0], y1 = yr[1];
            if (y0 < 0 || y1 - y0 < 50) return -1;

            // Ignore a margin so the page border and its drop shadow are not mistaken for content.
            int m = 6, total = 0, ink = 0;
            for (int y = y0 + m; y <= y1 - m; y += 2) {
                for (int x = x0 + m; x <= x1 - m; x += 2) {
                    total++;
                    if (!white.test(x, y)) ink++;
                }
            }
            return total == 0 ? -1 : ink / (double) total;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Start and end index of the longest run of {@code true}, or {@code {-1,-1}}. */
    private static int[] longestRun(boolean[] flags) {
        int bestStart = -1, bestLen = 0, start = -1;
        for (int i = 0; i <= flags.length; i++) {
            boolean on = i < flags.length && flags[i];
            if (on && start < 0) start = i;
            if (!on && start >= 0) {
                if (i - start > bestLen) { bestLen = i - start; bestStart = start; }
                start = -1;
            }
        }
        return bestLen == 0 ? new int[] { -1, -1 } : new int[] { bestStart, bestStart + bestLen - 1 };
    }
}
