    package com.kpj.core;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.*;
import java.util.*;

/**
 * DevHisBase - reusable core for DevHIS (KPJ nHIS) Playwright Java tests.
 *
 * Encapsulates the DevHIS-specific gotchas that the prompt package documents:
 *   - two-step login (credentials -> pick OPD counter -> Login again)
 *   - select2 dropdowns (offscreen <select> set via Angular ngModel + select2('val'))
 *   - ui-grid row selection via grid.api.selection.selectRow(dataRow)
 *   - confirm "Do You Want To Save" dialog before the save API fires
 *   - JAlert toast interception (toasts fade in ~1s)
 *   - signature drawing with real mouse events
 *   - per-step screenshots + standard-format HTML report
 *
 * A test extends this, calls start(), runs steps via step(...), then report(...).
 */
public abstract class DevHisBase {

    /** The actual test steps. Set metadata with meta(...) and add summary rows with addSummary(...).
     *  The framework calls start() before and always writes this test's report after (even on failure). */
    protected abstract void body();

    /** Runs this test: start browser (unless attached) -> body() -> ALWAYS write its own report. */
    public final void run() {
        start();
        try {
            body();
        } catch (Throwable e) {
            System.err.println("[" + testId + "] FAILED: " + e);
            failStep(e);
        } finally {
            writeReport();
        }
    }

    // ---- environment config (URL + credentials) --------------------------
    // Resolved at startup so ONE jar can run against any environment. Priority (first non-blank wins):
    //   1) JVM system property   -Ddevhis.url=... -Ddevhis.user=... -Ddevhis.pass=...
    //   2) environment variable  DEVHIS_URL / DEVHIS_USER / DEVHIS_PASS
    //   3) config.properties     (path in -Ddevhis.config=..., else ./config.properties, else next to the jar)
    //   4) the built-in default  (devhis.sancyberhad.com / tieba - farisha is rejected on devhis since 2026-09-03;
    //      tieba's password was reset to Tieba@123 on 2026-09-10)
    private static final java.util.Properties FILE_PROPS = loadProps();

    public static final String BASE = cfg("devhis.url", "https://devhis.sancyberhad.com").replaceAll("/+$", "");
    public static final String DASHBOARD = BASE + "/#/PatientDashboard";
    public static final String USER = cfg("devhis.user", "tieba");
    public static final String PASS = cfg("devhis.pass", "Tieba@123");
    public static final String COUNTER = null;
    // Some environments (e.g. KS QA) serve a "Local Login" form with a mandatory Organization/Department
    // select that must be chosen BEFORE the Login button will authenticate — confirmed live: without it,
    // login() reports success (never stuck on the login form) but the app session is broken, so every
    // subsequent screen fails to load. Blank/unset = skip (no-op on environments without this field).
    public static final String ORG = cfg("devhis.org", "");

    private static java.util.Properties loadProps() {
        java.util.Properties p = new java.util.Properties();
        for (String path : new String[]{ System.getProperty("devhis.config", ""), "config.properties", jarDir() + "/config.properties" }) {
            if (path == null || path.isBlank()) continue;
            java.io.File f = new java.io.File(path);
            if (f.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { p.load(in);
                    System.out.println("DevHisBase: loaded config from " + f.getAbsolutePath()); break; }
                catch (Exception ignore) { }
            }
        }
        return p;
    }

    private static String jarDir() {
        try { return new java.io.File(DevHisBase.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent(); }
        catch (Exception e) { return "."; }
    }

    private static String cfg(String key, String def) {
        String v = System.getProperty(key);                                  // 1) -Dkey=...
        if (v != null && !v.isBlank()) return v.trim();
        v = System.getenv(key.replace('.', '_').toUpperCase());              // 2) KEY_LIKE_THIS
        if (v != null && !v.isBlank()) return v.trim();
        v = FILE_PROPS.getProperty(key);                                     // 3) config.properties
        if (v != null && !v.isBlank()) return v.trim();
        return def;                                                          // 4) built-in default
    }

    // ---- attachment image (Photo / Thumb / IC Card / passport copy) -------
    // Every registration-style flow attaches an image. The path used to be hardcoded to one developer's
    // machine, so the attach step FAILED with "Cannot get absolute file path" on every other machine.
    // Resolved the same way as the URL/credentials: -Ddevhis.attach.file for one exact file, else the
    // NEWEST image in -Ddevhis.attach.dir (default: this machine's Screenshots folder).
    public static final Path ATTACH_DIR =
            Paths.get(cfg("devhis.attach.dir", System.getProperty("user.home") + "\\Pictures\\Screenshots"));
    /** Newest image in ATTACH_DIR (or -Ddevhis.attach.file). */
    public static final Path ATTACH = attachImage(0);
    /** Second-newest image — for the few screens that upload TWO different images (e.g. Image Management). */
    public static final Path ATTACH2 = attachImage(1);

    private static Path attachImage(int index) {
        String file = cfg("devhis.attach.file", "");
        if (index == 0 && !file.isBlank()) return Paths.get(file);
        try (java.util.stream.Stream<Path> files = Files.list(ATTACH_DIR)) {
            java.util.List<Path> images = files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase().matches(".*\\.(png|jpg|jpeg|bmp|gif)$"))
                    .sorted(java.util.Comparator.comparingLong((Path f) -> f.toFile().lastModified()).reversed())
                    .collect(java.util.stream.Collectors.toList());
            if (!images.isEmpty()) {
                Path pick = images.get(Math.min(index, images.size() - 1));   // one image => both slots use it
                System.out.println("DevHisBase: attachment image [" + index + "] = " + pick);
                return pick;
            }
        } catch (Exception e) {
            System.out.println("DevHisBase: cannot read " + ATTACH_DIR + " (" + e + ")");
        }
        // Still no image (the Screenshots-folder default is empty, or missing, on this machine): fall back
        // to a real image bundled with the repo itself, so upload steps do not depend on any particular
        // machine's Pictures\Screenshots folder happening to have a file in it.
        Path bundled = extractBundledPlaceholder();
        if (bundled != null) {
            System.out.println("DevHisBase: no image in " + ATTACH_DIR + " - using the bundled placeholder " + bundled);
            return bundled;
        }
        System.out.println("DevHisBase: no image in " + ATTACH_DIR + " and the bundled placeholder could not be extracted - attachment steps will FAIL");
        return ATTACH_DIR.resolve("missing-attachment.png");
    }

    /** Copies {@code attachments/placeholder.png} (src/test/resources) out to a real temp file — Playwright's
     *  file-upload input needs an actual filesystem path, not a classpath resource stream. */
    private static Path extractBundledPlaceholder() {
        try (java.io.InputStream in = DevHisBase.class.getClassLoader().getResourceAsStream("attachments/placeholder.png")) {
            if (in == null) return null;
            Path tmp = Files.createTempFile("mcp-kpj-placeholder-", ".png");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (Exception e) {
            System.out.println("DevHisBase: could not extract the bundled placeholder image - " + e.getMessage());
            return null;
        }
    }

    public final String testId;
    public final Path reportDir;
    protected Playwright pw;
    protected Browser browser;
    protected BrowserContext ctx;
    protected Page page;

    private int shotCount = 0;
    private boolean attached = false; // true when running inside a shared browser (RunAll)
    private boolean reported = false;
    private String rTitle = "", rModule = "", rNote = "";
    private final List<String[]> steps = new ArrayList<>();       // {n, name, desc, expected, actual, status, screenshot}
    private final List<String[]> summaryRows = new ArrayList<>(); // {key, value}

    public DevHisBase(String testId) {
        this.testId = testId;
        // The report is a single self-contained HTML file written straight into test-output/reports/ (named
        // after the page, e.g. Registration.html) — no per-run subfolder. Screenshots are embedded as base64,
        // so nothing else is written. A re-run overwrites the page's HTML.
        // Output folder is configurable (-Ddevhis.outdir / DEVHIS_OUTDIR / config.properties) so different
        // environments can write to different folders and never clobber each other. Defaults to "test-output".
        this.reportDir = Paths.get(cfg("devhis.outdir", "test-output"), "reports");
        try { Files.createDirectories(reportDir); } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- lifecycle --------------------------------------------------------
    /** Attach to a shared browser so start()/stop() reuse it instead of launching/closing.
     *  Used by RunAll to run every test in ONE browser window. */
    public void attach(Playwright pw, Browser browser, BrowserContext ctx, Page page) {
        this.pw = pw; this.browser = browser; this.ctx = ctx; this.page = page; this.attached = true;
    }

    public void start() {
        Reasons.clear(); // a prior test in the same JVM must not leak its pending reasons into this one
        if (attached) return; // shared browser already provided
        pw = Playwright.create();
        browser = pw.chromium().launch(launchOptions());
        ctx = browser.newContext(contextOptions());
        page = ctx.newPage();
        ensureLargeWindow(page);
    }

    /** The size screenshots fall back to when the window cannot be maximized (headless / locked desktop). */
    public static final int SHOT_W = 1920, SHOT_H = 1075;

    /**
     * Maximize the window so every screenshot in a report shares one scale.
     *
     * <p>{@code --start-maximized} + {@code setViewportSize(null)} gives the real maximized window when there is a
     * desktop to maximize into. When there is not — headless, or a locked/disconnected session — Chromium falls
     * back to 800x600, and a report then mixes 800x600 with 1920x1075 images, the small ones reading as zoomed in.
     * So: ask the window to maximize, and pin {@link #SHOT_W}x{@link #SHOT_H} only if it stayed small.</p>
     */
    public static void ensureLargeWindow(Page p) {
        int width = 0;
        try {
            p.evaluate("() => { try { window.moveTo(0,0); window.resizeTo(screen.availWidth, screen.availHeight); } catch(e){} }");
            p.waitForTimeout(300);
            Object w = p.evaluate("() => window.innerWidth");
            width = w instanceof Number ? ((Number) w).intValue() : 0;
        } catch (Exception e) {
            // A PDF viewer / error page can refuse the script — that is not a reason to leave the shot small.
            System.out.println("ensureLargeWindow: could not measure the window (" + e.getMessage() + ")");
        }
        // Pin unless a large window is CONFIRMED. Skipping the pin whenever the probe failed is what left the
        // report-tab captures at 704x195 next to the 1920x1075 ones.
        if (width >= 1200) {
            System.out.println("ensureLargeWindow: maximized window " + width + "px wide");
            return;
        }
        try {
            p.setViewportSize(SHOT_W, SHOT_H);
            System.out.println("ensureLargeWindow: window was " + width + "px — pinned " + SHOT_W + "x" + SHOT_H
                    + " so the report's screenshots stay one size");
        } catch (Exception e) {
            System.out.println("ensureLargeWindow: could not pin the size - " + e.getMessage());
        }
    }

    /** Launch options honouring runtime flags: -Dheadless=true|false, -Dslowmo=<ms>. */
    public static BrowserType.LaunchOptions launchOptions() {
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        double slowmo = Double.parseDouble(System.getProperty("slowmo", "120"));
        String exePath = System.getProperty("playwright.chromiumExecutable", "");
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(slowmo)
                .setArgs(java.util.Arrays.asList(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--start-maximized",
                        "--kiosk-printing"));
        if (!exePath.isEmpty()) opts.setExecutablePath(Paths.get(exePath));
        return opts;
    }

    public static Browser.NewContextOptions contextOptions() {
        // MAXIMIZED window (user-mandated 2026-08-12): --start-maximized + setViewportSize(null), so the page
        // fills the real OS window (~1920x1075) and the report's screenshots match the App-Config ones instead of
        // being small shots blown up to the report width.
        //
        // The old fixed 800x600 was there because headless Chromium has no window to maximize and falls back to
        // Playwright's 800x600, which made headed and headless disagree. That is handled by ensureLargeWindow()
        // at start() rather than by shrinking everything: it maximizes, and only pins a size if the window is
        // still small (headless, or a locked/disconnected desktop).
        return new Browser.NewContextOptions().setViewportSize(null);
    }

    /** Closes the browser only when this test owns it (standalone run). No-op in shared mode. */
    public void stop() {
        if (attached) return; // RunAll owns and closes the shared browser
        if (browser != null) browser.close();
        if (pw != null) pw.close();
    }

    public Page page() { return page; }
    public BrowserContext context() { return ctx; }

    // ---- login ------------------------------------------------------------
    /**
     * Login: <b>enter the user name → enter the password → click the Login button</b>. No-op if already
     * authenticated. Kept in step with {@code com.kpj.pages.LoginPage} (this class lives in src/main and cannot
     * reference the src/test page object, so the logic is mirrored rather than delegated).
     */
    public void login() {
        page.navigate(DASHBOARD);
        page.waitForTimeout(1000);

        // Already authenticated? Decide on the login FORM, not on the URL — the app serves the login screen at
        // /Account (and /Account#/PatientDashboard when a hash route was requested), so the old
        // `url().contains("/Account/Login")` test matched NOTHING and silently skipped the login entirely.
        if (!waitForLoginForm(8000)) return;

        // Organization/Department (KS-style "Local Login" form): select BEFORE anything else — it gates the
        // Login button's actual authentication, not just a post-failure fallback like the Cash Counter below.
        if (ORG != null && !ORG.trim().isEmpty()) {
            Object picked = page.evaluate("(org) => { const norm=s=>(s||'').toLowerCase();"
                    + " const sel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && [...s.options].some(o=>/organi[sz]ation|department/i.test(o.textContent||'')));"
                    + " if(!sel) return false; const o=[...sel.options].find(x=>norm(x.textContent).includes(norm(org))); if(!o) return false;"
                    + " sel.value=o.value; sel.dispatchEvent(new Event('change',{bubbles:true})); return o.textContent.trim(); }", ORG);
            if (Boolean.FALSE.equals(picked)) System.out.println("login: Organization/Department select or option matching '" + ORG + "' not found");
            else System.out.println("login: Organization/Department = " + picked);
            page.waitForTimeout(800);
        }

        // Step 1 + 2 — enter the user name and the password
        page.locator("input[placeholder='Login Name']").fill(USER);
        page.locator("input[type='password']").fill(PASS);

        // Both fields carry ng-blur="getLocations()" — an ASYNC fetch that populates the REQUIRED hidden
        // Location/CashCounter where those exist. Blur, then wait for it before submitting (no-op when absent).
        try { page.locator("input[type='password']").blur(); } catch (Exception ignore) { }
        try {
            page.waitForFunction(
                    "() => { const l=document.querySelector('#LocationID, select[ng-model=\"LocationID\"]'); if(!l) return true;"
                    + " const o=l.options[l.selectedIndex]; return !!(l.value && (''+l.value).trim() && o && o.text && !/^-*\\s*select|^\\s*$/i.test(o.text.trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("login: Location (getLocations) did not populate in time — proceeding"); }
        page.waitForTimeout(600);

        // Step 3 — click the Login button
        clickLoginButton();
        page.waitForTimeout(2000);

        // Two-step fallback — only when the login form is STILL showing after the first click.
        if (isLoginFormVisible()) {
            if (COUNTER != null && !COUNTER.trim().isEmpty()) {
                page.evaluate("(c)=>{const s=[...document.querySelectorAll('select')].find(x=>[...x.options].some(o=>o.text.includes(c)));" +
                        "if(s){const o=[...s.options].find(x=>x.text.includes(c));s.value=o.value;s.dispatchEvent(new Event('change',{bubbles:true}));}}", COUNTER);
                page.waitForTimeout(800);
            }
            clickLoginButton();
        }
        try { page.waitForURL("**/PatientDashboard", new Page.WaitForURLOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.waitForTimeout(600);

        if (isLoginFormVisible()) {
            throw new IllegalStateException("Login failed: still on the login page. URL=" + page.url()
                    + " — check credentials (devhis.user / devhis.pass).");
        }
    }

    /** True while the login form is on screen (i.e. not yet authenticated). */
    private boolean isLoginFormVisible() {
        try { return page.locator("input[placeholder='Login Name']").isVisible(); }
        catch (Exception e) { return false; }
    }

    /** Click the Login button — the screen renders it as {@code <button type="button">LOGIN</button>}. */
    private void clickLoginButton() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"))
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            try { page.locator("button[type='submit'], button:has-text('Login')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception ignore) { System.out.println("login: Login button not clickable"); }
        }
    }

    /** Wait for the login form; {@code true} = login needed, {@code false} = already authenticated. */
    private boolean waitForLoginForm(int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isLoginFormVisible()) return true;
            if (!page.url().toLowerCase().contains("/account")) return false; // authenticated shell
            page.waitForTimeout(300);
        }
        return isLoginFormVisible();
    }

    /** Manual login: pauses until the tester logs in and presses ENTER in the console. */
    public void loginManual(String url) {
        page.navigate(url);
        System.out.println("\n>>> Log in manually in the browser, then press ENTER here to continue...");
        new Scanner(System.in).nextLine();
        page.waitForLoadState();
    }

    // ---- navigation -------------------------------------------------------
    public void openViaHash(String hashRoute) { page.navigate(BASE + "/#/" + hashRoute); page.waitForTimeout(1000); }

    public void clickOpMenu() { page.evaluate("()=>{const a=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP');if(a)a.click();}"); page.waitForTimeout(300); }

    /** Click an OP submenu link by its hash href (e.g. '#/VisitScreen', '#/queueManagement'). */
    public void clickMenuHref(String href) {
        clickOpMenu();
        page.evaluate("(h)=>{const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')===h);if(a)a.click();}", href);
        page.waitForTimeout(1200);
    }

    // ---- DevHIS widget helpers -------------------------------------------
    /** Set a select2/native <select> (bound to Angular ngModel) by visible option text. */
    public void setSelect2(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);" +
                "if(!e)return;const o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);if(!o)return;e.value=o.value;" +
                "const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}" +
                "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));}",
                Arrays.asList(ngModel, optionText));
    }

    /** Fill a text input/textarea bound to Angular ngModel (first visible match). */
    public void fillNg(String ngModel, String value) {
        page.evaluate("([ng,v])=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng&&(x.offsetParent!==null||!x.getAttribute('maxlength')));" +
                "if(!e)return;const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}" +
                "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}",
                Arrays.asList(ngModel, value));
    }

    /** Set an Angular scope model directly (for hidden fields like Visit.TokenNo). */
    public void setScopeModel(String anchorSelector, String path, Object value) {
        page.evaluate("([sel,p,v])=>{const el=document.querySelector(sel);const s=angular.element(el).scope();" +
                "s.$apply(()=>{const parts=p.split('.');let o=s;for(let i=0;i<parts.length-1;i++){o=o[parts[i]]=o[parts[i]]||{};}o[parts[parts.length-1]]=v;});}",
                Arrays.asList(anchorSelector, path, value));
    }

    /** Select a ui-grid row whose PatientName matches (regex); returns the matched name. */
    public String selectQueueRow(String nameRegex) {
        Object r = page.evaluate("(re)=>{let gs=null;document.querySelectorAll('*').forEach(el=>{if(gs)return;try{const s=angular.element(el).scope();" +
                "if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data)gs=s;}catch(e){}});if(!gs)return null;" +
                "const d=gs.grid.options.data||[];const rx=new RegExp(re,'i');const p=d.find(x=>rx.test(x.PatientName||x.patientname||''))||d[0];" +
                "gs.grid.api.selection.clearSelectedRows();gs.grid.api.selection.selectRow(p);return (p.PatientName||p.patientname||'').trim();}", nameRegex);
        return r == null ? null : r.toString();
    }

    /** Search the queue with a widened From date (loads records so rows exist). */
    public void searchQueue(String fromDate) {
        page.evaluate("(fd)=>{const f=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='queue.fromdate');" +
                "if(f){const c=angular.element(f).controller('ngModel');f.value=fd;if(c){c.$setViewValue(fd);c.$render();}f.dispatchEvent(new Event('change',{bubbles:true}));}" +
                "const b=[...document.querySelectorAll('button')].find(x=>/^Search$/i.test(x.innerText.trim()));if(b)b.click();}", fromDate);
        page.waitForTimeout(2200);
    }

    /** Click a visible button by exact (trimmed) text. */
    public void clickButton(String text) {
        page.evaluate("(t)=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim().toLowerCase()===t.toLowerCase()&&x.offsetParent!==null);if(b)b.click();}", text);
    }

    /** Confirm the 'Do You Want To Save' dialog by clicking its red SAVE (btn-danger). */
    public void confirmSaveDialog() {
        page.evaluate("()=>{const b=[...document.querySelectorAll('button')].find(x=>/^SAVE$/i.test(x.innerText.trim())&&/danger/.test(x.className)&&x.offsetParent!==null);if(b)b.click();}");
    }

    /** Install a JAlert interceptor that records toast/alert text into window.__alerts. */
    public void interceptAlerts() {
        page.evaluate("()=>{window.__alerts=[];const o=window.JAlert;window.JAlert=function(m,t){window.__alerts.push(m);if(o)try{return o.apply(this,arguments);}catch(e){}};}");
    }

    /** Return the captured JAlert/toast messages. */
    @SuppressWarnings("unchecked")
    public List<String> capturedAlerts() {
        Object r = page.evaluate("()=>window.__alerts||[]");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Draw a signature on the given canvas with real mouse events (on the given page/tab). */
    public boolean drawSignature(Page p, String canvasSelector) {
        Object rc = p.evaluate("(sel)=>{const c=sel?document.querySelector(sel):[...document.querySelectorAll('canvas')].filter(x=>x.offsetParent!==null).find(x=>!x.id);" +
                "if(!c)return null;c.scrollIntoView({block:'center'});const r=c.getBoundingClientRect();return {x:r.left,y:r.top,w:r.width,h:r.height};}", canvasSelector);
        // Not every form HAS a signature canvas. This used to dereference the null straight away and take the
        // whole section down with "Cannot invoke Map.get because m is null" — which read as a harness crash and
        // said nothing about the form. Report it and let the caller decide.
        if (!(rc instanceof Map)) {
            System.out.println("drawSignature: no signature canvas on this page — skipped");
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> m = (Map<String,Object>) rc;
        double x=((Number)m.get("x")).doubleValue(), y=((Number)m.get("y")).doubleValue();
        double w=((Number)m.get("w")).doubleValue(), h=((Number)m.get("h")).doubleValue();
        double cx=x+w/2, cy=y+h/2;
        p.mouse().move(cx-70, cy); p.mouse().down();
        double[][] path={{-40,-20},{-10,20},{20,-18},{50,18},{70,-10},{90,10}};
        for (double[] d: path) p.mouse().move(cx+d[0], cy+d[1], new Mouse.MoveOptions().setSteps(6));
        p.mouse().up();
        return true;
    }

    // ---- steps & report ---------------------------------------------------
    /** Record a step and take a screenshot of the given page (status: PASS/MANUAL/FAIL). */
    public void step(Page p, String name, String desc, String expected, String actual, String status) {
        shotCount++;
        // Capture the screenshot to MEMORY (base64) and embed it directly in the HTML — no step_N.png files are
        // ever written to the report folder. Bound it and never let it fail the test: some report tabs are PDFs
        // whose viewer can stall Playwright's screenshot; try the target page first, then the primary page.
        String b64 = "";
        try {
            byte[] png = p.screenshot(new Page.ScreenshotOptions().setTimeout(10000));
            if (png != null && png.length > 0) b64 = java.util.Base64.getEncoder().encodeToString(png);
        } catch (Exception e) {
            System.out.println("step: screenshot of target page failed (" + name + "): " + e.getMessage());
            try {
                if (page != null && page != p) {
                    byte[] png = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000));
                    if (png != null && png.length > 0) b64 = java.util.Base64.getEncoder().encodeToString(png);
                }
            } catch (Exception ignore) {}
        }
        steps.add(new String[]{String.valueOf(shotCount), name, desc, expected, Reasons.appendTo(actual), status, b64});
        System.out.printf("  %2d [%-6s] %s%n", shotCount, status, name);
    }
    /** Convenience: step on the primary page. */
    public void step(String name, String desc, String expected, String actual, String status) {
        step(page, name, desc, expected, actual, status);
    }

    /**
     * Record a step whose screenshot was captured elsewhere (e.g. a PDF report tab that Playwright
     * cannot screenshot reliably in the background). {@code png} may be null → the step is recorded
     * imageless.
     */
    /**
     * Bring a form SECTION into view so the step's screenshot shows it filled.
     *
     * <p>Screenshots are viewport-sized, and these forms are several screens long — a step for NOK / Payor /
     * Visit captured the top of the page instead of the section it had just filled. Matches the panel header by
     * text (e.g. "Patient Information", "Payor"), centres it, and lets the scroll settle.</p>
     */
    /**
     * Open a COLLAPSED section (accordion panel) and scroll to it, so the step's screenshot shows its contents.
     *
     * <p>Only clicks when the panel's body is actually hidden — clicking an already-open header would collapse
     * it and the capture would show even less.</p>
     */
    public void openSection(String titleRegex) {
        try {
            Object what = page.evaluate("(re) => { const rx=new RegExp(re,'i'); const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const heads=[...document.querySelectorAll('.panel-title,.panel-heading,.box-title,a,h3,h4,h5,div,span')]"
                    + "   .filter(e=>e.offsetParent!==null).map(e=>({e,t:norm(e.textContent)}))"
                    + "   .filter(x=>x.t && x.t.length<45 && rx.test(x.t));"
                    + " if(!heads.length) return 'no header';"
                    + " const h=heads[0].e;"
                    // the panel this header controls: aria-controls / href="#collapseX" / the nearest collapse body
                    + " const id=(h.getAttribute('aria-controls')||'') || ((h.getAttribute('href')||'').replace(/^#/,''));"
                    + " let body = id ? document.getElementById(id) : null;"
                    + " if(!body){ const p=h.closest('.panel,.box'); if(p) body=p.querySelector('.panel-collapse,.panel-body,.box-body'); }"
                    + " const open = !!body && body.offsetParent!==null && body.getBoundingClientRect().height>40;"
                    + " if(!open){ (h.querySelector('a')||h).click(); return 'opened \"'+heads[0].t+'\"'; }"
                    + " return 'already open \"'+heads[0].t+'\"'; }", titleRegex);
            page.waitForTimeout(900);
            System.out.println("openSection(" + titleRegex + "): " + what);
        } catch (Exception e) {
            System.out.println("openSection(" + titleRegex + "): " + e.getMessage());
        }
        scrollToSection(titleRegex);
    }

    public void scrollToSection(String titleRegex) {
        try {
            Object found = page.evaluate("(re) => { const rx=new RegExp(re,'i'); const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cand=[...document.querySelectorAll('.panel-title,.panel-heading,.box-title,legend,h3,h4,h5,a,div,span')]"
                    + "   .filter(e=>e.offsetParent!==null).map(e=>({e,t:norm(e.textContent)}))"
                    + "   .filter(x=>x.t && x.t.length<45 && rx.test(x.t));"
                    + " if(!cand.length) return ''; cand[0].e.scrollIntoView({block:'center'}); return cand[0].t; }", titleRegex);
            page.waitForTimeout(600);
            if (found == null || found.toString().isEmpty())
                System.out.println("scrollToSection: no section matching /" + titleRegex + "/ — screenshot may not show it");
        } catch (Exception e) {
            System.out.println("scrollToSection(" + titleRegex + "): " + e.getMessage());
        }
    }

    /**
     * The error a report tab is showing, or "" when it rendered.
     *
     * <p>A report tab that opens is not a report that WORKED: the Crystal pages answer with an ASP.NET
     * "Server Error in '/' Application" (typically {@code Logon failed ... incorrect log on parameters}) and a
     * stack trace, which screenshots perfectly happily. Judging those steps on "a tab opened" or "a screenshot
     * was captured" marked a broken report PASS. Returns the first meaningful lines so the report says what
     * went wrong.</p>
     */
    public static String reportPageError(Page p) {
        // A tab existing at the right URL is not proof it loaded — the host can come back as a browser-level
        // DNS/connection failure (confirmed live: dsh-nhisforms.kpjhealth.com.my DNS_PROBE_FINISHED_NXDOMAIN),
        // which the app-error text scan below never sees since Chrome's own error page has no such text.
        try { if (p.url().startsWith("chrome-error://")) return "chrome-error page (" + p.url() + ")"; }
        catch (Exception ignore) { }
        try {
            Object r = p.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const body=document.body ? (document.body.innerText||'') : '';"
                    + " const title=document.title||'';"
                    + " const dnsBad=(title+' '+body).match(/this site can.?t be reached|dns_probe[a-z_]*|err_connection[a-z_]*|err_name_not_resolved|err_internet_disconnected/i);"
                    + " if(dnsBad) return dnsBad[0];"
                    + " const bad=/server error in|logon failed|unhandled exception|runtime error|comexception"
                    + "|could not be found|access is denied|invalid viewstate|the resource cannot be found/i;"
                    + " if(!bad.test(body)) return '';"
                    + " const lines=body.split('\\n').map(norm).filter(x=>x);"
                    + " const start=lines.findIndex(x=>bad.test(x));"
                    + " return lines.slice(Math.max(0,start), Math.max(0,start)+3).join(' | ').slice(0,300); }");
            return r == null ? "" : r.toString().trim();
        } catch (Exception e) {
            return "";                                   // a PDF/blank viewer has no readable body — not an error
        }
    }

    public void step(byte[] png, String name, String desc, String expected, String actual, String status) {
        shotCount++;
        String b64 = (png != null && png.length > 0) ? java.util.Base64.getEncoder().encodeToString(png) : "";
        steps.add(new String[]{String.valueOf(shotCount), name, desc, expected, Reasons.appendTo(actual), status, b64});
        System.out.printf("  %2d [%-6s] %s%n", shotCount, status, name);
    }

    /** Set report metadata (call early in body() so a report can be written even on failure). */
    public void meta(String title, String module, String note) { rTitle = title; rModule = module; rNote = note; }

    /** Append a Test-Summary key/value row (call anytime during body()). */
    public void addSummary(String key, String value) { summaryRows.add(new String[]{key, value}); }

    /** Records the failure as a FAIL step (with a screenshot of the current page if possible). */
    private void failStep(Throwable e) {
        shotCount++;
        String b64 = "";
        try {
            if (page != null) {
                byte[] png = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000));
                if (png != null && png.length > 0) b64 = java.util.Base64.getEncoder().encodeToString(png);
            }
        } catch (Exception ignore) {}
        steps.add(new String[]{String.valueOf(shotCount), "ERROR", "Unexpected error during the test",
                "Test completes", Reasons.appendTo("FAILED: " + e.getMessage()), "FAIL", b64});
    }

    /**
     * Report id for the report file / title / TSV — {@link #testId} by default.
     *
     * <p>OVERRIDE this (instead of adding a second constructor) when a test class reuses another test's whole flow
     * but must report under its own name. {@code testId} is {@code public final}, so the only other way to change
     * it is a parameterised constructor — and a test class with TWO constructors is rejected outright by JUnit
     * ("must declare a single constructor"), which silently made the parent class unrunnable.</p>
     */
    protected String reportId() { return testId; }

    /** Writes this test's own standard-format report (idempotent; always called by run()). */
    private void writeReport() {
        if (reported) return;
        reported = true;
        final String rid = reportId() == null || reportId().isBlank() ? testId : reportId();
        if (rTitle.isBlank()) rTitle = rid;
        // Name the report file after the PAGE (strip the "TCxx_" prefix from the testId) — e.g. Registration.html,
        // IP_Admission.html — instead of a generic TestReport.html. Screenshots are embedded (base64) in the HTML,
        // so nothing else is written to the folder.
        String reportFile = rid.replaceFirst("^TC\\d+_", "");
        if (reportFile.isBlank()) reportFile = rid;
        reportFile = reportFile.replaceAll("[^A-Za-z0-9._-]", "_") + ".html";
        HtmlReport.write(reportDir, reportFile, rTitle, BASE, rModule, rNote, summaryRows, steps);
        System.out.println("Report: " + reportDir.resolve(reportFile).toAbsolutePath());

        // Machine-readable per-step result for the CI aggregator (ReportAggregator):
        //   status \t page \t testId \t step \t expected \t actual   (one line per step)
        // The "page" is the report title (e.g. "Emergency List View (Change Admission Type)").
        try {
            String page = rTitle.isBlank() ? rid : rTitle;
            StringBuilder tsv = new StringBuilder();
            for (String[] s : steps) {
                String status = s.length > 5 ? s[5] : "";
                String name = s.length > 1 ? s[1] : "";
                String expected = s.length > 3 ? s[3] : "";
                String actual = s.length > 4 ? s[4] : "";
                tsv.append(clean(status)).append('\t').append(clean(page)).append('\t').append(clean(rid)).append('\t')
                        .append(clean(name)).append('\t').append(clean(expected)).append('\t').append(clean(actual)).append('\n');
            }
            // Write the per-step data to a hidden sibling folder (aggregate-data) — NOT into reports/ — so the
            // reports folder stays HTML-only. The aggregator (ReportAggregator) reads the tsv from there.
            String tsvFile = reportFile.replaceFirst("\\.html$", ".tsv");
            Path dataDir = reportDir.getParent() != null ? reportDir.getParent().resolve("aggregate-data")
                                                          : Paths.get("aggregate-data");
            Files.createDirectories(dataDir);
            Files.writeString(dataDir.resolve(tsvFile), tsv.toString());
        } catch (Exception ignore) { /* report file is the source of truth; tsv is best-effort */ }
    }

    /** Flatten a cell for TSV output (no tabs/newlines). */
    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace("\r", " ").replace("\n", " ").trim();
    }
}
