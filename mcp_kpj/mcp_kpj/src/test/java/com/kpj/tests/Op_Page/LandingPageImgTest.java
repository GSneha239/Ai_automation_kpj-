package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.LandingPageImgPage;

/**
 * OP &gt; <b>Landingpageimg</b> (#/LandingPage) — SMOKE test of a view-only screen.
 *
 * Login; open the screen from the OP menu; the KPJ healthcare background image loads; the screen has no editable
 * controls (empty form, empty controller — nothing to save); the header names the screen.
 */
public class LandingPageImgTest extends DevHisBase {

    public LandingPageImgTest() { super("LandingPageImg"); }

    public static void main(String[] args) {
        LandingPageImgTest t = new LandingPageImgTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OP - Landingpageimg", "OP > Landingpageimg",
                "Smoke test of a view-only screen: it opens, its background image loads, and it has no editable controls.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        LandingPageImgPage lp = new LandingPageImgPage(page);
        boolean opened = lp.navigateViaMenu();
        step(page, "Open Landingpageimg", "OP > Landingpageimg", "The Landingpageimg screen opens (#/LandingPage)",
                opened ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED - screen not reached"); return; }

        String img = lp.backgroundImage();
        boolean imgOk = img.contains("|");
        step(page, "Background image", "Look at the screen's background", "The KPJ healthcare background image loads",
                imgOk ? "Loaded: " + img : img, imgOk ? "PASS" : "FAIL");

        int controls = lp.editableControls();
        step(page, "View only", "Look for fields / buttons on the screen", "No editable fields or buttons (view-only screen)",
                controls < 0 ? "Screen view not found" : controls + " editable control(s) on the screen", controls == 0 ? "PASS" : "FAIL");

        String header = lp.headerText();
        boolean headerOk = header.toLowerCase().replaceAll("\\s+", "").contains("landing");
        step(page, "Header", "Read the page header", "The header names this screen (Landingpageimg / Landing Page)",
                "Header shows \"" + (header.isEmpty() ? "(none)" : header) + "\"" + (headerOk ? "" : " - the previous screen's title is left in place"),
                headerOk ? "PASS" : "FAIL");

        addSummary("Application URL", BASE + "/#/LandingPage");
        addSummary("Background image", img);
    }
}
