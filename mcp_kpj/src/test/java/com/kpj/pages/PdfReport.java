package com.kpj.pages;

import com.microsoft.playwright.Page;

/**
 * Judges a report that DevHIS opens in a new tab.
 *
 * <p>These reports come back as <b>PDFs</b>, which Chrome shows in its own viewer: the HTML around the
 * document is empty by design. Screenshotting that page therefore produces a blank image however good
 * the report is — which is exactly how a perfectly good rejection report first read as "BLANK". So the
 * PDF itself is judged: its size, its {@code %PDF} header, its page objects and its content streams.</p>
 *
 * <p>The bytes are fetched from INSIDE the report tab. Fetching from Java trips over this environment's
 * certificate, while inside the tab the browser has already accepted it and the request carries the
 * session automatically.</p>
 */
public final class PdfReport {

    private PdfReport() { }

    public static final class Result {
        /** Where the report opened; empty when no tab ever opened. */
        public String url = "";
        /** True when nothing was produced, or what was produced carries no content. */
        public boolean blank = true;
        /** A sentence for the report, naming the evidence either way. */
        public String diagnostics = "";
        public int bytes, pdfPages, streams;
        /** The PDF's first page rendered to PNG (null when it could not be rendered) — the screenshot a PDF viewer tab cannot give. */
        public byte[] png;
    }

    /**
     * Wait for the tab a save opened, then judge what it holds.
     *
     * @param tabsBefore how many tabs were open before the action
     * @param waitMs     how long to wait for the tab to carry a real URL
     */
    public static Result capture(Page page, int tabsBefore, int waitMs) {
        Result r = new Result();

        Page tab = null;
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            java.util.List<Page> pages = page.context().pages();
            if (pages.size() > tabsBefore) {
                Page t = pages.get(pages.size() - 1);
                try {
                    String u = t.url() == null ? "" : t.url();
                    if (!u.isEmpty() && !u.startsWith("about:")) {
                        tab = t;
                        r.url = u;
                        try { t.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD,
                                new Page.WaitForLoadStateOptions().setTimeout(8000)); }
                        catch (Exception ignore) { }
                        break;
                    }
                } catch (Exception ignore) { }
            }
            page.waitForTimeout(500);
        }

        if (tab == null) {
            r.diagnostics = "no report tab opened at all";
            return r;
        }

        try {
            Object o = tab.evaluate("async () => {"
                    + " const res = await fetch(location.href, {credentials:'include'});"
                    + " const buf = await res.arrayBuffer();"
                    + " const u8 = new Uint8Array(buf);"
                    + " let head=''; for(let i=0;i<Math.min(8,u8.length);i++) head += String.fromCharCode(u8[i]);"
                    + " const text = new TextDecoder('latin1').decode(u8);"
                    + " const pages = (text.match(/\\/Type\\s*\\/Page[^s]/g)||[]).length;"
                    + " const streams = (text.match(/stream/g)||[]).length;"
                    + " let bin=''; const chunk=0x8000; for(let i=0;i<u8.length;i+=chunk) bin += String.fromCharCode.apply(null, u8.subarray(i,i+chunk));"
                    // When the answer is an error page rather than a PDF, quote what the server says (ASP.NET error pages carry the title,
                    // the message and the exception details), so a 500 explains itself.
                    + " const pick=re=>{ const x=re.exec(text); return x? x[1].replace(/<[^>]*>/g,' ').replace(/\\s+/g,' ').trim() : ''; };"
                    + " const errText = head.startsWith('%PDF') ? '' : [pick(/<title>([\\s\\S]*?)<\\/title>/i), pick(/<h2>([\\s\\S]*?)<\\/h2>/i),"
                    + "   pick(/Description:\\s*<\\/b>([\\s\\S]*?)<br/i), pick(/Exception Details:\\s*<\\/b>([\\s\\S]*?)<br/i)].filter(x=>x).join(' | ').slice(0,500);"
                    + " return {len:u8.length, head:head, pages:pages, streams:streams, status:res.status, b64:btoa(bin), err:errText}; }");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
            r.bytes = ((Number) m.get("len")).intValue();
            r.pdfPages = ((Number) m.get("pages")).intValue();
            r.streams = ((Number) m.get("streams")).intValue();
            String head = String.valueOf(m.get("head"));
            boolean isPdf = head.startsWith("%PDF");
            if (isPdf) {
                try {
                    byte[] pdf = java.util.Base64.getDecoder().decode(String.valueOf(m.get("b64")));
                    r.png = com.kpj.core.PdfUtil.firstPageToPng(pdf);
                } catch (Exception e) {
                    System.out.println("PdfReport: could not render the PDF to an image — " + e.getMessage());
                }
            }
            // The tab did NOT hold a PDF (typically the server's HTML error page): that page screenshots normally, unlike Chrome's PDF
            // viewer, so the report gets a picture of what the tab actually showed instead of the app tab behind it.
            boolean tabScreenshot = false;
            if (!isPdf) {
                try {
                    r.png = tab.screenshot(new Page.ScreenshotOptions().setTimeout(8000));
                    tabScreenshot = r.png != null && r.png.length > 0;
                } catch (Exception e) {
                    System.out.println("PdfReport: could not screenshot the report tab — " + e.getMessage().split("\n")[0]);
                }
            }
            // A document with nothing on it is a few hundred bytes of scaffolding and no content
            // stream; a real one carries fonts and streams.
            boolean hasContent = isPdf && r.bytes > 3000 && r.streams > 0;
            r.blank = !hasContent;
            r.diagnostics = "a report opened at " + r.url + " — HTTP " + m.get("status") + ", "
                    + r.bytes + " bytes, starts with " + (isPdf ? "%PDF" : "\"" + head.trim() + "\"")
                    + ", " + r.pdfPages + " page object(s), " + r.streams + " content stream(s). "
                    + (hasContent ? "It carries content, so it really was generated."
                                  : "It carries no content, so nothing is printed on it.")
                    + (isPdf || m.get("err") == null || String.valueOf(m.get("err")).isEmpty() ? "" : " The server's error page says: \"" + m.get("err") + "\".")
                    + (tabScreenshot
                        ? " The screenshot is of the report tab itself (it is an HTML page, not a PDF)."
                        : " Judged on the file itself: a screenshot cannot see inside Chrome's PDF viewer, "
                          + "so the page around it is blank however good the report is.");
        } catch (Exception e) {
            r.blank = true;
            r.diagnostics = "a tab opened at " + r.url + " but its content could not be read: "
                    + e.getMessage().split("\n")[0];
        }
        return r;
    }
}
