package com.kpj.tests;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * LoginPage - Reusable login component for DevHIS.
 * Can be used across ALL tests regardless of module.
 */
public class LoginPage {

    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    /**
     * Perform two-step login for DevHIS
     */
    public void login(String baseUrl, String username, String password, String counter) {
        page.navigate(baseUrl + "/#/PatientDashboard");
        page.waitForTimeout(1000);
        if (!page.url().contains("/Account/Login")) {
            return; // already logged in
        }

        page.locator("input[placeholder='Login Name']").fill(username);
        page.locator("input[type='password']").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
        page.waitForTimeout(1500);

        page.evaluate("(c)=>{const s=[...document.querySelectorAll('select')].find(x=>[...x.options].some(o=>o.text.includes(c)));" +
                "if(s){const o=[...s.options].find(x=>x.text.includes(c));s.value=o.value;s.dispatchEvent(new Event('change',{bubbles:true}));}}", counter);
        page.waitForTimeout(500);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
        page.waitForURL("**/PatientDashboard", new Page.WaitForURLOptions().setTimeout(15000));
        page.waitForTimeout(1000);
    }

    /**
     * Manual login - pauses for user to enter credentials manually
     */
    public void loginManual(String url) {
        page.navigate(url);
        System.out.println("\n>>> Log in manually in the browser, then press ENTER here to continue...");
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            scanner.nextLine();
        }
        page.waitForLoadState();
    }

    public Page getPage() {
        return page;
    }
}
