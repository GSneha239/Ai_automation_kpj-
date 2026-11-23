package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>City/District</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → select <b>Country</b>, <b>State</b> and <b>District</b> → enter the
 * <b>Code</b> and <b>City/District</b> → <b>Submit</b> → success toast.</p>
 */
public class CityDistrict extends BasePage {

    public CityDistrict(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastCity = "", lastEntry = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; General &rarr; City/District.
     *
     * <p>The route is taken from the menu link, never guessed: labels in this module do not predict URLs —
     * "Complaint Type" is routed {@code #/ComplaintListType} — so a hand-written route lands on the
     * dashboard.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        // Retried: the Application Configuration / General menus are built after login and a click that
        // lands too early expands nothing, leaving the run on the dashboard. One attempt is a coin flip.
        Object href = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) page.waitForTimeout(1500);
            clickMenu("^\\s*application\\s*configuration\\s*$");
            clickMenu("^\\s*general\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*city\\s*\\/?\\s*district\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__cdMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__cdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("CityDistrict.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__cdMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            final String r = lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", r); } catch (Exception ignore) { }
            waitForAngular(3000);
        }
        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1200);
    }

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the City/District screen.
     *
     * <p>Matched against the route the MENU gave rather than a guessed keyword. A loose check on a sibling
     * screen once let a whole run pass against the wrong page, so the City/District screen — the obvious
     * neighbour here — is rejected outright.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (u.contains("area") || u.contains("town")) return false;   // reject the sibling Area/Town
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("city") || u.contains("district");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,40)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== CityDistrict CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?city|AddCity/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__cdAdd'; }");
        try {
            page.locator("#__cdAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("CityDistrict.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cdAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("CityDistrict.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .some(e=>/code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')+' '+labelOf(e))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Select <b>Country</b>, then <b>State</b>, then <b>District</b>.
     *
     * <p>They CASCADE: State is filled by the Country choice and District by the State choice, so each is
     * waited for after the one above rather than read at a fixed moment — a single read lands on the empty
     * list and reports "(no-option)" for a screen that is only still loading.</p>
     *
     * <p>Each is addressed by its own ng-model. On this module's forms every model shares one prefix
     * (here {@code city.}), so a keyword match for "city" or "district" finds the wrong control — that is
     * how the sibling Area/Town screen ended up writing the town name into the code box.</p>
     */
    public String selectCountryStateDistrict() {
        // Vary the STATE as well as the country. A country whose FIRST state has no districts is not proof
        // that the country has none, and the previous version moved straight on to the next country — so a
        // deployment whose districts hang off a later state looked empty everywhere.
        String[] preferred = System.getProperty("countries", "Malaysia,Singapore,India,Indonesia")
                .split(",");
        int maxCountries = Integer.getInteger("maxCountries", 25);
        int maxStates = Integer.getInteger("maxStates", 20);
        int budget = Integer.getInteger("maxTries", 120);

        StringBuilder tried = new StringBuilder();
        int tries = 0;

        // The Country list is fetched after the form renders; starting before it lands means every lookup
        // misses instantly and the search "tries" nothing.
        if (waitForOptions("country", 15000) == 0) {
            lastCity = "(no country dropdown was populated within 15s)";
            System.out.println("CityDistrict: " + lastCity);
            return lastCity;
        }

        java.util.List<String> order = new java.util.ArrayList<>();
        for (String s2 : preferred) if (!s2.trim().isEmpty()) order.add(s2.trim());

        for (int ci = 0; ci < order.size() + maxCountries && tries < budget; ci++) {
            String country;
            if (ci < order.size()) {
                country = pickCountryByName(order.get(ci));
                if (country.startsWith("(")) continue;
            } else {
                country = pickNth("country", ci - order.size());
                if (country.startsWith("(")) continue;
            }
            waitForAngular(1000);

            int states = waitForOptions("state", 6000);
            if (states == 0) {
                tried.append(tries == 0 ? "" : "; ").append(country.split(" \\[")[0]).append(" -> no state");
                tries++;
                continue;
            }

            for (int si = 0; si < Math.min(states, maxStates) && tries < budget; si++) {
                int districtsBefore = realOptionCount("district");
                String state = pickNthPreferringReal("state", si);
                if (state.startsWith("(")) continue;
                waitForAngular(900);
                tries++;

                // WAIT for the District list to be refilled by this state before judging it empty.
                int districts = waitForDistrictOptions(districtsBefore, 8000);
                if (districts > 0) {
                    String d = pickWhenReady("district");
                    if (!d.startsWith("(")) {
                        lastCity = "Country=" + country.split(" \\[")[0]
                                + " | State=" + state.split(" \\[")[0] + " | District=" + d;
                        System.out.println("CityDistrict: " + lastCity + "  (after " + tries + " tries)");
                        return lastCity;
                    }
                }
                tried.append(tries == 1 ? "" : "; ").append(country.split(" \\[")[0])
                     .append("/").append(state.split(" \\[")[0]).append(" -> no district");
            }
        }

        lastCity = "(no country offered a district) tried " + tries + " country/state pairs: "
                + (tried.length() > 600 ? tried.substring(0, 600) + "..." : tried);
        System.out.println("CityDistrict: " + lastCity);
        return lastCity;
    }

    /** Poll until a dropdown has at least one real option; returns how many it has. */
    private int waitForOptions(String rx, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int n = realOptionCount(rx);
        while (n == 0 && System.currentTimeMillis() < end) {
            page.waitForTimeout(300);
            n = realOptionCount(rx);
        }
        return n;
    }

    /**
     * Wait for the District list to be refilled after a state change.
     *
     * <p>Waits for the count to MOVE, not merely to be non-empty: on the sibling Postal screen the
     * equivalent list ships pre-loaded with every city in the database, so "not empty" is already true
     * before a state is chosen and picking from it stores a district belonging to another state.</p>
     */
    private int waitForDistrictOptions(int before, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int n = realOptionCount("district");
        while (n == before && System.currentTimeMillis() < end) {
            page.waitForTimeout(300);
            n = realOptionCount("district");
        }
        return n;
    }

    /**
     * Pick the n-th real option, skipping a filler row literally named "No Information" where a genuine
     * one exists — selecting the filler guarantees an empty list below it.
     */
    private String pickNthPreferringReal(String rx, int nth) {
        Object idx = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#__cdSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(s.getAttribute('ng-model')||''))"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__cdSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*select/i.test(norm(x.o.text)));"
                + " const real=reals.filter(x=>!/^no\s*information$/i.test(norm(x.o.text)));"
                + " const pool = real.length? real : reals;"
                + " return pool[a.n]? pool[a.n].i : -1; }",
                java.util.Map.of("rx", rx, "n", nth));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-option)";
        try {
            page.locator("#__cdSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__cdSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        return t == null ? "" : t.toString();
    }

    /** Select a country by name, so a deployment's real data can be targeted directly. */
    private String pickCountryByName(String name) {
        Object idx = page.evaluate("(n) => {" + JS
                + " document.querySelectorAll('#__cdSel').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>/country/i.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__cdSel';"
                + " return [...e.options].findIndex(o=>o.value"
                + "   && norm(o.text).toLowerCase()===String(n).toLowerCase()); }", name);
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(country \"" + name + "\" not in the list)";
        try {
            page.locator("#__cdSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        return name;
    }

    private int realOptionCount(String rx) {
        Object n = page.evaluate("(rx) => {" + JS
                + " const re=new RegExp(rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " return e? [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }", rx);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    private String pickNth(String rx, int nth) {
        Object idx = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#__cdSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__cdSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return reals[a.n]? reals[a.n].i : -1; }",
                java.util.Map.of("rx", rx, "n", nth));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-option)";
        try {
            page.locator("#__cdSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__cdSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        return (t == null ? "" : t.toString());
    }

    /** Wait for a cascading dropdown to gain a real option, then take the first one natively. */
    private String pickWhenReady(String which) {
        String rx = which.equals("country") ? "country"
                  : which.equals("state") ? "state"
                  : "district";
        try {
            page.waitForFunction("(rx) => { const re=new RegExp(rx,'i');"
                    + " return [...document.querySelectorAll('select')]"
                    + "  .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + "  .filter(e=>re.test(e.getAttribute('ng-model')||''))"
                    + "  .some(e=>[...e.options].some(o=>o.value"
                    + "    && !/^-*\s*select/i.test((o.text||'').trim()))); }", rx,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("CityDistrict.pick(" + which + "): no option within 15s");
        }
        Object info = page.evaluate("(rx) => {" + JS
                + " document.querySelectorAll('#__cdSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(s.getAttribute('ng-model')||''))"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return null; e.id='__cdSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*select/i.test(norm(x.o.text)));"
                + " return reals.length? {index:reals[0].i, ng:(e.getAttribute('ng-model')||'?'),"
                + "   count:reals.length} : {index:-1, ng:(e.getAttribute('ng-model')||'?'), count:0}; }", rx);
        if (info == null) return "(no " + which + " dropdown)";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) return "(no-option) [" + m.get("ng") + "]";
        try {
            page.locator("#__cdSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__cdSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        return (t == null ? "" : t.toString()) + " [" + m.get("ng") + "]";
    }

    /**
     * All three of Country, State and District hold a real value.
     *
     * <p>Checks for any parenthesised failure, not just {@code (no-}: the exhausted-search message reads
     * "(no country offered a district)", which slipped past a {@code (no-} test and reported a step as
     * passed while District was empty.</p>
     */
    public boolean cascadeSelected() {
        if (lastCity == null || lastCity.isEmpty()) return false;
        return lastCity.startsWith("Country=")
                && !lastCity.contains("(no") && !lastCity.contains("(select failed")
                && !lastCity.contains("(country ");
    }

    /** True when the run searched and found no country with any district — a master-data gap. */
    public boolean noDistrictAnywhere() {
        return lastCity != null && lastCity.startsWith("(no country offered a district)");
    }

    /**
     * Enter the <b>Code</b> and the <b>City/District</b> name.
     *
     * <p>Both are addressed by exact ng-model. The models are discovered from the form dump rather than
     * guessed: on Area/Town every model began "area.", so a keyword match for the screen's own name found
     * the Code box and overwrote it.</p>
     */
    public String enterCodeAndCity(String code, String city) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, ci] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const byNg=(re)=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
                + " const codeEl=byNg(/\\.code$/i);"
                + " const cityEl=byNg(/\\.(description|name)$/i);"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(codeEl.getAttribute('ng-model')||'?')+']' : '(no-code-field)';"
                + " const rn = cityEl? setEl(cityEl,ci)+' ['+(cityEl.getAttribute('ng-model')||'?')+']' : '(no-city-field)';"
                + " return 'Code='+rc+' | CityDistrict='+rn; }", java.util.List.of(code, city));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("CityDistrict: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code) {
        return lastEntry != null && !lastEntry.contains("(no-") && lastEntry.contains("Code=" + code);
    }

    /** A success message, judged strictly — these screens phrase refusals with the word "Added". */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String submitAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /submit/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try {
                Object now = page.evaluate("() => {" + JS + TOAST_ELS
                        + " return toastEls().map(x=>norm(x.textContent)); }");
                if (now instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) now) {
                        String s = o.toString();
                        if (!s.isEmpty()) { toast = s; break; }
                    }
                }
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }
        if (toast.isEmpty()) {
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }
        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("CityDistrict: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /** Look the code up on the screen's own grid — the toast alone is not proof of a write. */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        try {
            page.evaluate("(c) => {" + JS
                    + " const f=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis)[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,120) : 'not in the list ('+rows.length+' rows shown)'; }",
                    code);
            lastListCheck = "looked for \"" + code + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("CityDistrict: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
