package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Application Configuration &gt; Location &gt; <b>Hospital Location</b> ({@code #/location}) — Page Object.
 *
 * <p>Read-only verification screen: a single-row grid (Code / Location / Status / Edit) listing the hospital
 * location(s) this login is scoped to. The "Location" column renders the grid row's {@code Name} field.</p>
 *
 * <p><b>The menu link only becomes clickable after the "Location" submenu is expanded</b> — clicking it while
 * collapsed (its sub-items not yet in the DOM/visible) silently leaves the SPA on whatever screen it was already
 * on even though the URL and document title update to {@code #/location}/"Hospital Location" — checked live: the
 * page kept rendering Patient Dashboard content with the header/H1 stuck on "Patient Dashboard" for a beat.
 * {@code navigateViaMenu()} explicitly expands Location first and waits for the Hospital Location link to actually
 * render before clicking it.</p>
 */
public class HospitalLocation extends BasePage {

    public HospitalLocation(Page page) { super(page); }

    public static String ROUTE = "#/location";

    // ---- navigation ---------------------------------------------------------

    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        // Expand "Location" (its href is bare "#") and WAIT for the sub-menu to actually render before clicking
        // Hospital Location — clicking too early leaves the SPA on the previous screen despite the URL changing.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*location\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a[href]')].some(a=>a.getAttribute('href')==='#/location' && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { System.out.println("HospitalLocation.nav: submenu did not expand in time"); }
        Object tagged = page.evaluate("() => { const a=[...document.querySelectorAll('a[href]')].find(x=>x.getAttribute('href')==='#/location' && x.offsetParent!==null); if(!a) return false; a.id='__hlMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__hlMenu").click(new Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("HospitalLocation.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__hlMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2000);
        } else {
            System.out.println("HospitalLocation.nav: menu link 'Hospital Location' not found under Location");
        }
        for (int i = 0; i < 10 && !onScreen(); i++) page.waitForTimeout(700);
        return onScreen();
    }

    public boolean onScreen() {
        return Boolean.TRUE.equals(page.evaluate("() => /^\\s*hospital location\\s*$/i.test((document.querySelector('h1')||{}).textContent||'')"))
                && page.url().toLowerCase().contains("location");
    }

    // ---- grid -----------------------------------------------------------------

    /** Read the grid rows (Code / Name / Status) from the grid's own scope data. */
    public List<Map<String, Object>> readRows() {
        List<Map<String, Object>> out = new ArrayList<>();
        Object r = page.evaluate("() => { const A=window.angular; let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope();"
                + " if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data) && (!data||s.grid.options.data.length>data.length)) data=s.grid.options.data; }catch(e){} });"
                + " if(!data) return []; return data.map(row => ({ Code: row.Code, Name: row.Name, Status: row.Status })); }");
        if (r instanceof List) {
            for (Object o : (List<?>) r) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                out.add(m);
            }
        }
        return out;
    }

    /** True if any grid row's Location (Name) matches {@code expected} exactly (trimmed). */
    public boolean hasLocation(String expected) {
        for (Map<String, Object> row : readRows()) {
            Object name = row.get("Name");
            if (name != null && name.toString().trim().equals(expected)) return true;
        }
        return false;
    }
}
