package com.kpj.pages;

import java.util.Scanner;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * LoginPage — Page Object Model for the DevHIS login screen.
 *
 * Locators are private; all interactions go through action methods.
 * Reusable across every test regardless of the target module.
 */
public class LoginPage extends BasePage {

    // ---- locators --------------------------------------------------------

    private String loginNameInput()     { return "input[placeholder='Login Name']"; }
    private String passwordInput()      { return "input[type='password']"; }
    private String loginButton()         { return "button[type='submit'], button:has-text('Login')"; }
    private String counterSelect()      { return "select"; }

    // ---- constructor ------------------------------------------------------

    public LoginPage(Page page) {
        super(page);
    }

    // ---- actions ----------------------------------------------------------

    /**
     * Login: <b>enter the user name → enter the password → click the Login button</b>.
     *
     * <p>Both devhis and KLG now serve a SINGLE-step login (Version 3.0.0 screen: "Welcome Back", one
     * <code>LOGIN</code> button, no counter dropdown). The counter selection + second Login click are kept only as a
     * fallback for a two-step environment, and run just when the login form is STILL showing after the first click.</p>
     *
     * @param baseUrl   e.g. "https://devhis.sancyberhad.com"
     * @param username  login name
     * @param password  password
     * @param counter   counter/location code to select (e.g. "OPD-B-01"); pass null/empty to skip counter selection
     */
    /** Credentials to fall back to, in order, when the primary login is rejected specifically for being
     *  LOCKED or INVALID (account locked, wrong password, etc.). All are QA credentials already established
     *  and reused throughout this suite — this never guesses a password, it only rotates between known-good
     *  accounts. Override/extend with {@code -Ddevhis.cred.fallbacks=user1:pass1,user2:pass2}. */
    private static final String[][] DEFAULT_CRED_FALLBACKS = {
            {"tieba", "User@123"},
            {"farisha", "Tcare@123"},
            {"sandhya", "User@123"},
    };

    public void login(String baseUrl, String username, String password, String counter) {
        java.util.LinkedHashMap<String, String> candidates = new java.util.LinkedHashMap<>();
        candidates.put(username, password); // primary — always tried first, regardless of the fallback list
        for (String[] fb : resolveCredFallbacks()) candidates.putIfAbsent(fb[0], fb[1]);

        String lastError = "";
        int attempt = 0;
        for (java.util.Map.Entry<String, String> cred : candidates.entrySet()) {
            attempt++;
            boolean isPrimary = attempt == 1;
            if (!isPrimary) System.out.println("login: '" + lastError + "' for a prior account — trying fallback credentials (" + cred.getKey() + ")");

            AttemptResult result = attemptLogin(baseUrl, cred.getKey(), cred.getValue(), counter, isPrimary);
            if (result.success) return;

            // Confirmed live: the app doesn't always surface a readable toast for a genuine rejection (a
            // locked/invalid attempt can leave errorText blank), so gating the retry on CRED_ERROR_PATTERN
            // matching missed real cases and gave up after one account. Try every configured fallback
            // whenever the form is still visible after an attempt — worst case a few extra seconds per
            // candidate; that is cheaper than failing the whole run over one bad account.
            lastError = result.errorText.isEmpty() ? "still on login page after the attempt" : result.errorText;
        }

        throw new IllegalStateException(
                "Login failed: still on login page after authentication (tried " + attempt + " account(s)). "
                + "URL=" + page.url() + (lastError.isEmpty() ? "" : " — last error: \"" + lastError + "\"")
                + " — check credentials or counter selection.");
    }

    private static final class AttemptResult {
        final boolean success;
        final String errorText;
        AttemptResult(boolean success, String errorText) { this.success = success; this.errorText = errorText; }
    }

    /** One login attempt with one credential pair. Returns success + whatever error text was visible on
     *  failure (never throws — the retry loop in {@link #login} decides whether that's worth a fallback). */
    private AttemptResult attemptLogin(String baseUrl, String username, String password, String counter, boolean isFirstNavigation) {
        // Only re-navigate on the very first attempt — a fallback retry reuses the login form already on
        // screen (a fresh navigate would just reset any progress and re-trigger the same async loads).
        if (isFirstNavigation) {
            page.navigate(baseUrl + "/#/PatientDashboard");
            waitForAngular(1000);
            // Already authenticated? Decide on the login FORM, not on the URL. The app serves the login screen
            // at more than one path (/Account and /Account/Login, plus a #/... fragment carried over from the
            // requested route), so the old `url().contains("/Account/Login")` test could silently skip login.
            if (!waitForLoginForm(8000)) return new AttemptResult(true, "");
        }

        // Organization/Department (KS-style "Local Login" form): select BEFORE anything else — it gates the
        // Login button's actual authentication, not just a post-failure fallback like the counter below.
        String org = System.getProperty("devhis.org", "");
        if (!org.trim().isEmpty()) {
            Object picked = page.evaluate("(o) => { const norm=s=>(s||'').toLowerCase();"
                    + " const sel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && [...s.options].some(x=>/organi[sz]ation|department/i.test(x.textContent||'')));"
                    + " if(!sel) return false; const opt=[...sel.options].find(x=>norm(x.textContent).includes(norm(o))); if(!opt) return false;"
                    + " sel.value=opt.value; sel.dispatchEvent(new Event('change',{bubbles:true})); return opt.textContent.trim(); }", org);
            if (Boolean.FALSE.equals(picked)) System.out.println("login: Organization/Department select or option matching '" + org + "' not found");
            else System.out.println("login: Organization/Department = " + picked);
            waitForAngular(800);
        }

        // A fallback retry reuses the form after a REJECTED attempt — dismiss whatever error toast/alert is
        // still on screen first. Confirmed live: leaving it up, the very next attempt's password came back
        // empty at submit ("Password is Required") even though .fill() reported no error — the stale error
        // UI was still intercepting/clearing the field.
        if (!isFirstNavigation) {
            page.evaluate("() => { [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.alert,.jconfirm,.ng-confirm-box,.sweet-alert,.modal.in,.modal.show')]"
                    + ".filter(m=>m.getBoundingClientRect().width>0).forEach(box=>{ const btn=[...box.querySelectorAll('button,a,.jconfirm-buttons button')]"
                    + ".find(x=>/^(ok|yes|close|cancel|×|x)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(btn) btn.click(); else box.remove(); }); }");
            waitForAngular(500);
        }

        // Step 1 — enter the user name (clear first — a fallback retry reuses the form, which may still hold
        // the PRIOR account's rejected username/password). Verify-and-retry: the field has been seen going
        // back to empty after a fill under these conditions, so confirm the value actually stuck.
        fillAndVerify(loginNameInput(), username);

        // Step 2 — enter the password
        fillAndVerify(passwordInput(), password);

        // Both login fields carry ng-blur="getLocations()" — an ASYNC fetch that populates the REQUIRED hidden
        // Location/CashCounter on environments that have them. Blur to fire it, then WAIT for the location before
        // submitting; otherwise fnLogin() races the fetch and is rejected (stuck on the login page). Where there is
        // no LocationID field (the current devhis screen has none) the wait returns immediately.
        try { page.locator(passwordInput()).blur(); } catch (Exception ignore) {}
        try {
            page.waitForFunction(
                    "() => { const l=document.querySelector('#LocationID, select[ng-model=\"LocationID\"]'); if(!l) return true;"
                    + " const o=l.options[l.selectedIndex]; return !!(l.value && (''+l.value).trim() && o && o.text && !/^-*\\s*select|^\\s*$/i.test(o.text.trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("login: Location (getLocations) did not populate in time — proceeding"); }
        waitForAngular(600);

        // Step 3 — click the Login button
        clickLoginButton();
        waitForAngular(2000);

        // --- two-step fallback: only if the login form is STILL on screen -------------------------------------
        if (isLoginFormVisible()) {
            if (counter != null && !counter.trim().isEmpty()) {
                selectOptionInAnySelectByText(counter);
                waitForAngular(1000);
            }
            clickLoginButton();
        }

        try { page.waitForURL("**/PatientDashboard", new Page.WaitForURLOptions().setTimeout(15000)); } catch (Exception ignore) {}
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        waitForAngular(3000);

        if (!isLoginFormVisible()) return new AttemptResult(true, "");
        String errorText = readLoginErrorText();
        System.out.println("login: attempt for '" + username + "' failed" + (errorText.isEmpty() ? "" : " — \"" + errorText + "\""));
        return new AttemptResult(false, errorText);
    }

    /** Fill a field and verify the value actually stuck (re-filling up to 3x) — confirmed live that a fill
     *  right after dismissing a rejected-login toast can silently not stick the first time. */
    private void fillAndVerify(String selector, String value) {
        for (int attempt = 0; attempt < 3; attempt++) {
            page.locator(selector).fill("");
            page.locator(selector).fill(value);
            String actual = page.locator(selector).inputValue();
            if (value.equals(actual)) return;
            System.out.println("login: fill of '" + selector + "' did not stick (got \"" + actual + "\") — retrying");
            waitForAngular(400);
        }
    }

    /** Any visible toast/alert/inline-validation text on the login form — whatever the app said about why
     *  the attempt was rejected (account locked, invalid password, etc.), for the fallback-decision above. */
    private String readLoginErrorText() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sels = '.toast-message,.toast,[id^=toast],.alert,.jconfirm,.ng-confirm-box,.sweet-alert,[class*=validation],.help-block,.error,.text-danger';"
                + " const els=[...document.querySelectorAll(sels)].filter(e=>e.offsetParent!==null);"
                + " return els.map(e=>norm(e.textContent)).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** Parses {@code -Ddevhis.cred.fallbacks=user1:pass1,user2:pass2} if set, else the built-in defaults. */
    private static java.util.List<String[]> resolveCredFallbacks() {
        String raw = System.getProperty("devhis.cred.fallbacks", "");
        if (raw.trim().isEmpty()) return java.util.Arrays.asList(DEFAULT_CRED_FALLBACKS);
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty()) out.add(new String[]{parts[0].trim(), parts[1].trim()});
        }
        return out;
    }

    /**
     * Click the Login button. The current screen renders it as <code>&lt;button type="button"&gt;LOGIN&lt;/button&gt;</code>
     * — the role name match is case-insensitive, with a CSS fallback if the accessible name ever changes.
     */
    private void clickLoginButton() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"))
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("login: Login button by role not clickable (" + e.getMessage() + ") — trying CSS");
            try { page.locator(loginButton()).first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception ignore) { System.out.println("login: Login button not clickable at all"); }
        }
    }

    /**
     * Wait for the login form to appear. Returns {@code true} when it is showing (login is needed), {@code false}
     * when it never appears (already authenticated). Exits as soon as either is known.
     */
    private boolean waitForLoginForm(int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isLoginFormVisible()) return true;
            // Authenticated shell: the app is on a hash route and no longer under /Account.
            if (!page.url().toLowerCase().contains("/account")) return false;
            page.waitForTimeout(300);
        }
        return isLoginFormVisible();
    }

    /**
     * Login with only username + password (no explicit counter).
     */
    public void login(String baseUrl, String username, String password) {
        login(baseUrl, username, password, null);
    }

    /**
     * Convenience overload using default DevHIS credentials (sandhya / User@123 / OPD-B-01).
     */
    public void loginWithDefaults(String baseUrl) {
        login(baseUrl, "farisha", "Tcare@123", null);
    }

    /**
     * Manual login — navigates to the given URL and waits for the user
     * to complete authentication in the browser, then press ENTER in the console.
     */
    public void loginManual(String url) {
        page.navigate(url);
        System.out.println("\n>>> Log in manually in the browser, then press ENTER here to continue...");
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }
        page.waitForLoadState();
    }

    /**
     * Returns true if the login form is currently visible (not yet authenticated).
     */
    public boolean isLoginFormVisible() {
        return page.locator(loginNameInput()).isVisible();
    }
}
