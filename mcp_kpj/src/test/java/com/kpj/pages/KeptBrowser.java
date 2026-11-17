package com.kpj.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A browser that OUTLIVES the test run, so a long form can be scripted one tab at a time without ever reloading it, saving it or logging out.
 *
 * <p>The first {@link #open(int)} starts Chromium as an independent process with a remote-debugging port and connects to it over CDP; when the run ends only the
 * connection is dropped ({@link Session#close()}), the window stays open on the page the run left it on. The next run's {@link #open(int)} finds that port, connects to
 * the same browser and gets the same page - with the form, the patient and everything typed still in it - and carries on from there.</p>
 *
 * <p>The window is a normal visible Chromium (maximized, pop-up blocking off like the framework's own launch). Close it by hand, or end its process, to start afresh.</p>
 */
public final class KeptBrowser {

    private KeptBrowser() { }

    /** A connection to the kept browser. {@link #fresh} is true when this call had to start the browser. */
    public static final class Session {
        public final Playwright pw;
        public final Browser browser;
        public final BrowserContext ctx;
        public final Page page;
        public final boolean fresh;

        Session(Playwright pw, Browser browser, BrowserContext ctx, Page page, boolean fresh) {
            this.pw = pw; this.browser = browser; this.ctx = ctx; this.page = page; this.fresh = fresh;
        }

        /** Drop the connection ONLY - the browser and its pages stay open. */
        public void close() {
            try { pw.close(); } catch (Throwable ignore) { }
        }
    }

    private static Browser tryConnect(Playwright pw, int port, double slowmo, double timeoutMs) {
        try {
            return pw.chromium().connectOverCDP("http://127.0.0.1:" + port,
                    new BrowserType.ConnectOverCDPOptions().setSlowMo(slowmo).setTimeout(timeoutMs));
        } catch (Exception e) {
            return null;
        }
    }

    /** Connect to the kept browser on {@code port}, starting it first when nothing listens there. */
    public static Session open(int port) {
        double slowmo = Double.parseDouble(System.getProperty("slowmo", "120"));
        Playwright pw = Playwright.create();
        boolean fresh = false;
        Browser b = tryConnect(pw, port, slowmo, 4000);
        if (b == null) {
            fresh = true;
            launchDetached(pw, port);
            long end = System.currentTimeMillis() + 45_000;
            while (b == null && System.currentTimeMillis() < end) {
                try { Thread.sleep(1000); } catch (InterruptedException ignore) { }
                b = tryConnect(pw, port, slowmo, 3000);
            }
            if (b == null) {
                pw.close();
                throw new IllegalStateException("The kept browser did not come up on port " + port);
            }
        }
        BrowserContext ctx = b.contexts().isEmpty() ? b.newContext() : b.contexts().get(0);
        return new Session(pw, b, ctx, pickPage(ctx), fresh);
    }

    /** The page to work in: the Cath Lab form if one is open, else any DevHIS page, else the first page, else a new one. */
    private static Page pickPage(BrowserContext ctx) {
        Page devhis = null;
        for (Page p : ctx.pages()) {
            String u = p.url().toLowerCase();
            if (u.contains("add-cathlabdetails")) return p;
            if (devhis == null && u.startsWith("http")) devhis = p;
        }
        if (devhis != null) return devhis;
        return ctx.pages().isEmpty() ? ctx.newPage() : ctx.pages().get(0);
    }

    private static void launchDetached(Playwright pw, int port) {
        String exe = pw.chromium().executablePath().toString();
        Path profile = Paths.get(System.getProperty("java.io.tmpdir"), "mcp-kpj-kept-browser-" + port);
        List<String> cmd = new ArrayList<>(List.of(exe,
                "--remote-debugging-port=" + port,
                "--user-data-dir=" + profile,
                "--no-first-run", "--no-default-browser-check",
                "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
                "--start-maximized", "--kiosk-printing",
                // report tabs opened by window.open() after an AJAX answer must not be blocked (same reason as the framework's own launch)
                "--disable-popup-blocking",
                "about:blank"));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            System.out.println("KeptBrowser: started " + exe + " on remote-debugging port " + port);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the kept browser (" + exe + "): " + e.getMessage(), e);
        }
    }
}
