package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * LandingPageImgPage — Page Object for OP &gt; <b>Landingpageimg</b> (#/LandingPage).
 *
 * <p>A view-only screen (checked by hand and against LandingpageimgController.js / OPD/Load/Landingpageimg, build
 * v2026.11.17.488): the KPJ healthcare background image ({@code Contents/images/bg-main.png} on {@code #content1}) over an
 * EMPTY form — no fields, buttons or grid; the controller and factory are empty, so it makes no server calls.
 * Only a smoke check applies: the screen opens, the image loads, and nothing on it can be edited.</p>
 */
public class LandingPageImgPage extends BasePage {

    private static final String HREF = "#/landingpage";
    private static final String VIEW = "[ng-controller='LandingpageimgController']";

    public LandingPageImgPage(Page page) {
        super(page);
    }

    /** OP &gt; Landingpageimg through the sidebar. True only when the screen is really on view. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*op\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*op\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__lpLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__lpLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("LandingPageImg.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__lpLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(VIEW, new Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED).setTimeout(20000)); }
        catch (Exception ignore) { System.out.println("LandingPageImg.nav: the screen's view is not up"); }
        return onScreen();
    }

    /** On this screen — the route AND the screen's own view. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*" + HREF + "/?$")) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", VIEW));
    }

    /**
     * The background image: returns "url | width x height" once the image file the page uses has really loaded,
     * or a reason ("no background image", "image failed to load: …").
     */
    public String backgroundImage() {
        Object r = page.evaluate("async () => { const el=document.getElementById('content1') ||"
                + " [...document.querySelectorAll('body *')].find(e=>/bg-main/i.test(getComputedStyle(e).backgroundImage));"
                + " const bg=el ? getComputedStyle(el).backgroundImage : 'none';"
                + " const m=/url\\([\"']?([^\"')]+)[\"']?\\)/.exec(bg||''); if(!m) return 'no background image';"
                + " const url=m[1]; const ok=await new Promise(res=>{ const i=new Image(); i.onload=()=>res(i.naturalWidth+' x '+i.naturalHeight); i.onerror=()=>res(null); i.src=url; setTimeout(()=>res(null),10000); });"
                + " return ok ? url+' | '+ok : 'image failed to load: '+url; }");
        return r == null ? "" : r.toString();
    }

    /** Editable controls inside the screen's own view (inputs, selects, text areas, buttons). A view-only page has 0. */
    public int editableControls() {
        Object r = page.evaluate("(s) => { const v=document.querySelector(s); return v ? v.querySelectorAll('input,select,textarea,button').length : -1; }", VIEW);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** The page header text (top-left title of the app shell). */
    public String headerText() {
        Object r = page.evaluate("() => { const c=[...document.querySelectorAll('.navbar *, header *, .main-header *')].filter(e=>e.children.length===0 && e.offsetParent!==null"
                + " && /[A-Za-z]/.test(e.textContent||'') && e.getBoundingClientRect().top<60 && e.getBoundingClientRect().left<400);"
                + " const t=c.map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).find(t=>t.length>2 && !/welcome|login/i.test(t)); return t||''; }");
        return r == null ? "" : r.toString();
    }
}
