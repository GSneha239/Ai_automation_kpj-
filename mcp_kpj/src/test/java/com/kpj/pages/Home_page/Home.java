package com.kpj.pages.Home_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * <b>Home</b> — the house icon at the top of the sidebar ({@code <a href="#/home" title="Home">}). It opens the
 * <b>Patient Dashboard</b> ({@code #/PatientDashboard}, top bar "Patient Dashboard (v...)").
 *
 * <p>Layout (discovered live, v2026.11.24.524): a Date Range panel ({@code FromDate1} / {@code ToDate1}, dd/MM/yyyy, both today by default),
 * four KPI tiles (Total No. of Appointments, Pending Appointments, Confirmed Appointments, Total No. of Registered Patients), the
 * Patient Journey TAT tile ("Patient Journey : TAT (30 Min)", "Within TAT :(n)", "Exceeding TAT :(n)") and four widgets: Patient Journey
 * (pie), Diagnosis Trend, Pharmacy Dispense Completed / Pending ("No Data Available" when empty) and Billing.</p>
 */
public class Home extends BasePage {

    public Home(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    /** The KPI tiles, in the order the screen shows them. */
    public static final String[] TILES = {"Total No. of Appointments", "Pending Appointments", "Confirmed Appointments", "Total No. of Registered Patients"};

    /** The dashboard widgets, in the order the screen shows them. */
    public static final String[] WIDGETS = {"Patient Journey", "Diagnosis Trend", "Pharmacy Dispense Completed / Pending", "Billing"};

    public String lastClick = "";

    /** Click the <b>Home</b> icon of the sidebar (real click first, in-page click when the real one cannot land). */
    public boolean clickHome() {
        lastClick = "";
        Locator home = page.locator("a[href='#/home']:visible").first();
        try {
            if (home.count() == 0) { lastClick = "no visible Home link (a[href='#/home']) in the sidebar"; return false; }
            home.click(new Locator.ClickOptions().setTimeout(6000));
            lastClick = "clicked the Home icon";
        } catch (Exception e) {
            Object r = page.evaluate("() => { const a=[...document.querySelectorAll(\"a[href='#/home']\")][0]; if(!a) return false; a.click(); return true; }");
            lastClick = Boolean.TRUE.equals(r) ? "Home icon clicked in-page (the real click was refused: " + e.getMessage().split("\n")[0] + ")"
                                               : "Home click failed - " + e.getMessage().split("\n")[0];
            if (!Boolean.TRUE.equals(r)) return false;
        }
        waitForAngular(1500);
        return true;
    }

    /** On the Patient Dashboard: the route AND the top bar names it (the sidebar lists every menu name, so page text proves nothing). */
    public boolean onDashboard() {
        try {
            page.waitForFunction("() => { " + JS + " if(!/patientdashboard/i.test(location.hash)) return false;"
                            + " const h=document.querySelector('header,.navbar'); return /patient\\s*dashboard/i.test(norm(h?h.textContent:'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Top bar text, for the report. */
    public String header() {
        Object r = page.evaluate("() => { " + JS + " const h=document.querySelector('header,.navbar'); return norm(h?h.textContent:'').slice(0,60); }");
        return r == null ? "" : r.toString();
    }

    /** Wait (up to 20 s) until the KPI tiles have been drawn. */
    public void waitForTiles() {
        try {
            page.waitForFunction("() => /total\\s*no\\.?\\s*of\\s*appointments/i.test(document.body.innerText||'')",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        waitForAngular(800);
    }

    /** {@code From=<value>|To=<value>} of the Date Range panel. */
    public String[] dateRange() {
        Object r = page.evaluate("() => { " + JS
                + " const f=[...document.querySelectorAll('input')].filter(vis).find(e=>/^FromDate1$/i.test(e.getAttribute('ng-model')||e.id||e.name||''));"
                + " const t=[...document.querySelectorAll('input')].filter(vis).find(e=>/^ToDate1$/i.test(e.getAttribute('ng-model')||e.id||e.name||''));"
                + " return (f?f.value:'(no From box)')+'\\u0001'+(t?t.value:'(no To box)'); }");
        String[] p = (r == null ? "" : r.toString()).split("\u0001", -1);
        return new String[]{p.length > 0 ? p[0] : "", p.length > 1 ? p[1] : ""};
    }

    /** The number shown in a KPI tile, or {@code ""} when the tile (or a numeric value) is not there. */
    public String tileValue(String label) {
        Object r = page.evaluate("(l) => { " + JS
                + " const want=norm(l).toLowerCase();"
                + " const els=[...document.querySelectorAll('div,span,p,h1,h2,h3,h4,h5,small,label')].filter(vis)"
                + "   .filter(e=>e.getBoundingClientRect().left>60 && norm(e.textContent).toLowerCase()===want);"
                + " if(!els.length) return '';"
                + " const e=els[els.length-1]; const p=e.parentElement; if(!p) return '';"
                + " const rest=norm(p.textContent).toLowerCase().replace(want,'').trim();"
                + " return /^\\d+$/.test(rest) ? rest : ('?'+rest.slice(0,20)); }", label);
        return r == null ? "" : r.toString();
    }

    /** {@code TAT limit|within|exceeding} of the Patient Journey TAT tile, or {@code ""}. */
    public String[] tat() {
        Object r = page.evaluate("() => { const t=document.body.innerText||'';"
                + " const a=t.match(/patient\\s*journey\\s*:\\s*tat\\s*\\((\\d+)\\s*min\\)/i), b=t.match(/within\\s*tat\\s*:\\s*\\((\\d+)\\)/i), c=t.match(/exceeding\\s*tat\\s*:\\s*\\((\\d+)\\)/i);"
                + " return (a?a[1]:'')+'\\u0001'+(b?b[1]:'')+'\\u0001'+(c?c[1]:''); }");
        String[] p = (r == null ? "" : r.toString()).split("\u0001", -1);
        return p.length == 3 ? p : new String[]{"", "", ""};
    }

    /**
     * State of a dashboard widget: {@code missing} (no such title), {@code chart} (a canvas / svg is drawn in it),
     * {@code no-data} ("No Data Available" shown), {@code blank} (a chart area is there but nothing is drawn in it - Diagnosis Trend and
     * Billing look like this on 2026-11-24) or {@code empty} (title only).
     */
    public String widgetState(String title) {
        Object r = page.evaluate("(l) => { " + JS
                + " const want=norm(l).toLowerCase();"
                + " const t=[...document.querySelectorAll('div,span,h1,h2,h3,h4,h5,p,label,b,strong')].filter(vis)"
                + "   .filter(e=>e.getBoundingClientRect().left>60 && norm(e.textContent).toLowerCase()===want).pop();"
                + " if(!t) return 'missing';"
                + " let c=t.parentElement; for(let i=0;i<6&&c;i++){ const b=c.getBoundingClientRect(); if(b.width>=300&&b.height>=150) break; c=c.parentElement; }"
                + " if(!c) return 'empty';"
                + " const big=x=>{const b=x.getBoundingClientRect(); return b.width>40&&b.height>40;};"
                // an svg counts as drawn only when it carries labels or several shapes (an empty chart shell has 3 bare shapes and no text);
                // a canvas only when some pixel is painted
                + " const drawnSvg=[...c.querySelectorAll('svg')].filter(vis).filter(big).some(x=>norm(x.textContent).length>0 || x.querySelectorAll('path,rect,circle,polygon,line').length>=5);"
                + " const drawnCanvas=[...c.querySelectorAll('canvas')].filter(vis).filter(big).some(x=>{ try{ const d=x.getContext('2d').getImageData(0,0,x.width,x.height).data; for(let i=3;i<d.length;i+=200){ if(d[i]>0) return true; } return false; }catch(e){ return true; } });"
                + " if(drawnSvg||drawnCanvas) return 'chart';"
                + " if(/no\\s*data/i.test(c.innerText||'')) return 'no-data';"
                + " if([...c.querySelectorAll('canvas,svg')].filter(vis).some(big)) return 'blank';"
                + " return 'empty'; }", title);
        return r == null ? "missing" : r.toString();
    }
}
