package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * TokenDisplayPage — Page Object for OP &gt; <b>Token Display</b> (#/TokenDisplay), the waiting-area display board.
 *
 * <p>Checked by hand and against tokenDisplayController.js (build v2026.11.17.488):</p>
 * <ul>
 *   <li>The only control is the <b>Waiting Area</b> drop-down ({@code token.waitingareaid}); picking one loads that
 *       area's tokens ({@code /API/TokanDisplay/fetch}, ExecFlag GRID) into {@code TokenDisplayList}.</li>
 *   <li>Normal areas draw up to 5 rows: doctor photo / name / designation, <b>Token Number</b> ({@code tokenno}) and
 *       <b>Consultation Room</b> ({@code description}). A called token ({@code iscalled}) <b>flashes</b>.</li>
 *   <li>Waiting Area id 6 (<b>Emergency</b>) draws an emergency card board instead.</li>
 *   <li>The board re-fetches every 5 seconds; a newly called token rings a bell, is read aloud, and is marked
 *       "audio played" on the server (sound cannot be checked by automation).</li>
 * </ul>
 * <p>Tokens get onto the board from OP &gt; Outpatient Queue Management &gt; <b>Call Patient</b>
 * ({@link OutPatientQueueManagementPage#callPatientAndGetToast()}).</p>
 */
public class TokenDisplayPage extends BasePage {

    private static final String HREF = "#/tokendisplay";
    private static final String AREA_SELECT = "select[ng-model='token.waitingareaid']";

    public TokenDisplayPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /** OP &gt; Token Display through the sidebar. True only when the screen is really on view. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*op\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*op\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__tdLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__tdLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("TokenDisplay.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__tdLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(AREA_SELECT, new Page.WaitForSelectorOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("TokenDisplay.nav: the Waiting Area drop-down is not up"); }
        if (onScreen()) installHelpers();
        return onScreen();
    }

    /** On this screen — the route AND the Waiting Area drop-down. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(HREF)) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", AREA_SELECT));
    }

    private void installHelpers() {
        page.evaluate("() => { window.__tdScope = function(){ const e=document.querySelector(\"" + AREA_SELECT + "\"); if(!e||!window.angular) return null;"
                + " let s=angular.element(e).scope(); for(let i=0;i<10&&s;i++){ if(s.token!==undefined) return s; s=s.$parent; } return null; }; }");
    }

    // ---- waiting areas ----------------------------------------------------

    /** Waiting Area names in the drop-down (without "--Select--"); waits for the list to load. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> waitingAreas() {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length > 1; }", AREA_SELECT,
                    new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("TokenDisplay.waitingAreas: list did not load"); }
        Object r = page.evaluate("(s) => [...document.querySelector(s).options].map(o=>o.text.trim()).filter(t=>t && !/^-+\\s*select\\s*-+$/i.test(t))", AREA_SELECT);
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    /**
     * Pick a Waiting Area with a real select, then wait for the board's fetch to answer. Returns the number of tokens
     * the area holds ({@code TokenDisplayList} length; -1 when the fetch did not answer).
     */
    public int selectWaitingArea(String name) {
        page.evaluate("() => { const s=window.__tdScope(); if(s){ s.TokenDisplayList=undefined; } }");
        // The board also re-fetches every 5 s on its own, so wait for the fetch that carries THIS area's id
        // (option value "number:NN"), not an in-flight refresh of the previous area.
        Object v = page.evaluate("([s,n]) => { const o=[...document.querySelector(s).options].find(x=>x.text.trim()===n); return o ? String(o.value).replace(/^number:/,'') : ''; }",
                java.util.Arrays.asList(AREA_SELECT, name));
        final String areaId = v == null ? "" : v.toString();
        try {
            com.microsoft.playwright.Response resp = page.waitForResponse(r -> r.url().contains("/API/TokanDisplay/fetch")
                            && (areaId.isEmpty() || String.valueOf(r.request().postData()).replace(" ", "").matches(".*\"waitingareaid\":\"?" + areaId + "\"?[,}].*")),
                    new Page.WaitForResponseOptions().setTimeout(15000),
                    () -> page.locator(AREA_SELECT).selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(name)));
            if (resp == null) return -1;
        } catch (Exception e) {
            System.out.println("TokenDisplay.selectWaitingArea '" + name + "': " + String.valueOf(e.getMessage()).split("\n")[0]);
            return -1;
        }
        waitForAngular(800);
        Object n = page.evaluate("() => { const s=window.__tdScope(); return s && Array.isArray(s.TokenDisplayList) ? s.TokenDisplayList.length : 0; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Option text of the Waiting Area with this id (the board draws its Emergency layout for id 6, "Accident &amp; Emergency"). */
    public String areaNameById(int id) {
        Object r = page.evaluate("([s,id]) => { const o=[...document.querySelector(s).options].find(x=>String(x.value).replace(/^number:/,'')===String(id)); return o ? o.text.trim() : null; }",
                java.util.Arrays.asList(AREA_SELECT, id));
        return r == null ? null : r.toString();
    }

    /** The Waiting Area currently chosen (option text). */
    public String selectedArea() {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.trim() : ''; }", AREA_SELECT);
        return r == null ? "" : r.toString();
    }

    // ---- board ------------------------------------------------------------

    /** True when the normal board's "Token Number" and "Consultation Room" headings are on view. */
    public boolean normalHeadingsShown() {
        return Boolean.TRUE.equals(page.evaluate("() => { const t=document.body.innerText; return /Token Number/.test(t) && /Consultation Room/.test(t); }"));
    }

    /** True when the Emergency card board is on view (and the normal headings are not). */
    public boolean emergencyBoardShown() {
        return Boolean.TRUE.equals(page.evaluate("() => { const b=document.querySelector('.emergency-board'); return !!b && b.offsetParent!==null; }"));
    }

    /** Rows drawn on the normal board (it draws at most 5). */
    public int drawnRows() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"li[ng-repeat^='tokens in TokenDisplayList']\")].filter(e=>e.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Cards drawn on the Emergency board (it draws at most 5). */
    public int drawnEmergencyCards() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"[ng-repeat^='item in TokenDisplayList']\")].filter(e=>e.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Find a token in the loaded area's list: returns "tokenno | room | doctor | called=… | drawn=… | flashing=…",
     * or null when the area does not hold it. "drawn" = the row is on the board (the board shows only 5 rows).
     */
    public String findToken(String tokenNo) {
        Object r = page.evaluate("(tn) => { const s=window.__tdScope(); if(!s||!Array.isArray(s.TokenDisplayList)) return null;"
                + " const t=s.TokenDisplayList.find(x=>String(x.tokenno).trim()===String(tn).trim()); if(!t) return null;"
                + " const li=[...document.querySelectorAll(\"li[ng-repeat^='tokens in TokenDisplayList']\")].find(e=>{ const sc=angular.element(e).scope(); return sc && sc.tokens && sc.tokens.id===t.id && e.offsetParent!==null; });"
                + " const flash=!!(li && li.querySelector('.flash'));"
                + " return String(t.tokenno)+' | room '+(t.description||'-')+' | doctor '+(t.doctorname||'-')+' | called='+(!!t.iscalled)+' | drawn='+(!!li)+' | flashing='+flash; }", tokenNo);
        return r == null ? null : r.toString();
    }

    /** Short summary of the board: the first few "token → room" pairs of the loaded area. */
    public String boardSummary() {
        Object r = page.evaluate("() => { const s=window.__tdScope(); const l=s && Array.isArray(s.TokenDisplayList) ? s.TokenDisplayList : [];"
                + " return l.length+' token(s)'+(l.length? ': '+l.slice(0,5).map(t=>(t.tokenno||'?')+' -> '+(t.description||'-')+(t.iscalled?' (called)':'')).join(', ') : ''); }");
        return r == null ? "" : r.toString();
    }

    /** Wait (up to {@code ms}) for the board's 5-second refresh to show the token as called. */
    public boolean waitForCalled(String tokenNo, int ms) {
        try {
            page.waitForFunction("(tn) => { const s=window.__tdScope(); return !!(s && Array.isArray(s.TokenDisplayList) && s.TokenDisplayList.some(x=>String(x.tokenno).trim()===String(tn).trim() && x.iscalled)); }",
                    tokenNo, new Page.WaitForFunctionOptions().setTimeout(ms));
            return true;
        } catch (Exception e) { return false; }
    }
}
