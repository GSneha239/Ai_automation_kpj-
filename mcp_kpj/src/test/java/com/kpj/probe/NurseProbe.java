package com.kpj.probe;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** TEMPORARY exploration probe (delete before building the jar): logs in with the framework, then evaluates JS files dropped in a folder. */
public class NurseProbe extends DevHisBase {
    public NurseProbe() { super("Probe_Nurse"); }

    public static void main(String[] args) {
        NurseProbe t = new NurseProbe();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected void body() {
        Path dir = Paths.get(System.getProperty("probe.dir"));
        try {
            new LoginPage(page).login(BASE, USER, PASS);
            Files.writeString(dir.resolve("ready.txt"), "logged in: " + page.url());
            int seen = 0;
            while (!Files.exists(dir.resolve("quit"))) {
                Path cmd = dir.resolve("cmd-" + (seen + 1) + ".js");
                if (Files.exists(cmd)) {
                    seen++;
                    String js = Files.readString(cmd, StandardCharsets.UTF_8);
                    String out;
                    try {
                        if (js.startsWith("//TYPE ")) {
                            String[] parts = js.substring(7).split("[|][|]", 2);
                            com.microsoft.playwright.Locator l = page.locator(parts[0].trim()).first();
                            l.click();
                            l.fill("");
                            l.pressSequentially(parts[1].trim(), new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(70));
                            out = "typed";
                        } else if (js.startsWith("//PRESS ")) {
                            String[] parts = js.substring(8).split("[|][|]", 2);
                            page.locator(parts[0].trim()).first().press(parts[1].trim());
                            out = "pressed";
                        } else if (js.startsWith("//CLICK ")) {
                            page.locator(js.substring(8).trim()).first().click();
                            out = "clicked";
                        } else if (js.startsWith("//GOTO ")) {
                            page.navigate(js.substring(7).trim(), new com.microsoft.playwright.Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                            out = "navigated " + page.url();
                        } else if (js.startsWith("//TABEVAL ")) {
                            java.util.List<com.microsoft.playwright.Page> ps = page.context().pages();
                            com.microsoft.playwright.Page last = ps.get(ps.size() - 1);
                            Object r = last.evaluate("(async()=>{ try { const __r = await (async()=>{" + js.substring(10) + "})(); return JSON.stringify(__r === undefined ? null : __r); } catch(e) { return 'ERR ' + e.message; } })()");
                            out = ps.size() + " page(s); last=" + last.url() + " :: " + r;
                        } else if (js.startsWith("//CLICKPOPUP ")) {
                            com.microsoft.playwright.Page popup = null;
                            try { popup = page.context().waitForPage(new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(15000), () -> page.locator(js.substring(13).trim()).first().click()); }
                            catch (Exception e) { out = "no popup: " + String.valueOf(e.getMessage()).split("[\\r\\n]")[0]; Files.writeString(dir.resolve("out-" + seen + ".txt"), out, StandardCharsets.UTF_8); continue; }
                            try { popup.waitForLoadState(); } catch (Exception ignore) { }
                            Thread.sleep(6000);
                            String sel = "?";
                            try { sel = String.valueOf(popup.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='consent.consentid'); return JSON.stringify({href:location.href, select:!!s, options:s?s.options.length:-1, title:document.title, body:(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,200)}); }")); } catch (Exception e) { sel = "eval failed " + e.getMessage(); }
                            out = "popup url=" + popup.url() + " :: " + sel;
                        } else if (js.startsWith("//PAGES")) {
                            StringBuilder sb = new StringBuilder();
                            for (com.microsoft.playwright.Page pg : page.context().pages()) sb.append("[").append(pg.url()).append("] ");
                            out = sb.toString();
                        } else if (js.startsWith("//SHOT")) {
                            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(dir.resolve("shot-" + seen + ".png")));
                            out = "screenshot saved";
                        } else {
                            Object r = page.evaluate("(async()=>{ try { const __r = await (async()=>{" + js + "})(); return JSON.stringify(__r === undefined ? null : __r); } catch(e) { return 'ERR ' + e.message; } })()");
                            out = String.valueOf(r);
                        }
                    } catch (Throwable e) { out = "JAVAERR " + e.getMessage(); }
                    Files.writeString(dir.resolve("out-" + seen + ".txt"), out, StandardCharsets.UTF_8);
                } else {
                    Thread.sleep(300);
                }
            }
        } catch (Exception e) {
            System.out.println("probe failed: " + e);
        }
    }
}
