package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Postal</b> — Page Object.
 *
 * <p>Flow: select <b>Country</b>, <b>State</b> and <b>City/District</b> → enter the <b>Postal</b> code
 * → <b>Submit</b> → success toast.</p>
 */
public class Postal extends BasePage {

    public Postal(Page page) { super(page); }

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
     * Application Configuration &rarr; General &rarr; Postal.
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
                    + "   .find(x=>/^\\s*postal(\\s*code)?\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__poMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__poMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Postal.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__poMenu'); if(e) e.removeAttribute('id'); }");
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
     * On the Postal screen.
     *
     * <p>Matched against the route the MENU gave rather than a guessed keyword. A loose check on a sibling
     * screen once let a whole run pass against the wrong page, so the City/District screen — the obvious
     * neighbour here — is rejected outright.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        // Reject the neighbours outright. Trusting the menu route alone is not enough: if the menu lookup
        // ever matches the wrong link, lastRoute carries that screen's URL and the check then confirms it —
        // which is how a Postal run once executed entirely against City/District, filling city.Code and
        // calling fnIUDCity(), and still reported its first four steps as passed.
        if (u.contains("area") || u.contains("town")) return false;
        if (u.contains("citydistrict") || u.contains("district")) return false;
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("postal") || u.contains("pincode") || u.contains("zip");
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
        System.out.println("=== Postal CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?postal|AddPostal/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__poAdd'; }");
        try {
            page.locator("#__poAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("Postal.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__poAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("Postal.clickAdd: Code field never appeared"); }
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
     * Select <b>Country</b>, then <b>State</b>, then <b>City/District</b>.
     *
     * <p>They CASCADE: State is filled by the Country choice and District by the State choice, so each is
     * waited for after the one above rather than read at a fixed moment — a single read lands on the empty
     * list and reports "(no-option)" for a screen that is only still loading.</p>
     *
     * <p>Each is addressed by its own ng-model. On this module's forms every model shares one prefix
     * (here {@code city.}), so a keyword match for "city" or "district" finds the wrong control — that is
     * how the sibling Area/Town screen ended up writing the town name into the code box.</p>
     */
    public String selectCountryStateCity() {
        // Vary the STATE as well as the country. A country whose first state has no cities is not proof
        // that the country has none — Malaysia's Johor comes back empty while another state may not — so
        // this walks states within each country instead of judging a country on its first one.
        //
        // A state literally named "No Information" is skipped where a real one exists: it is a filler row,
        // and selecting it guarantees an empty city list.
        String[] preferred = System.getProperty("countries", "Malaysia,Singapore,India,Indonesia").split(",");
        int maxCountries = Integer.getInteger("maxCountries", 25);
        int maxStates = Integer.getInteger("maxStates", 15);
        int budget = Integer.getInteger("maxTries", 90);

        StringBuilder tried = new StringBuilder();
        int tries = 0;

        // The Country list is fetched after the form renders. Starting the search before it lands means
        // every country lookup misses instantly and the whole search finishes in milliseconds having
        // "tried" nothing — which reads in the report as though no country offers a city.
        int countries = waitForOptions("country", 15000);
        if (countries == 0) {
            lastCity = "(no country dropdown was populated within 15s)";
            System.out.println("Postal: " + lastCity);
            return lastCity;
        }

        java.util.List<String> order = new java.util.ArrayList<>();
        for (String s : preferred) if (!s.trim().isEmpty()) order.add(s.trim());

        // Preferred countries by name, then the rest of the list by position.
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
                int citiesBefore = realOptionCount("city");
                String state = pickNthPreferringReal("state", si);
                if (state.startsWith("(")) continue;
                waitForAngular(900);
                tries++;

                // WAIT for GetCity2() to refill the list rather than counting it straight away: it is
                // fetched asynchronously after the state changes.
                int cities = waitForCityOptions(citiesBefore, 8000);
                if (cities > 0) {
                    String city = pickWhenReady("city");
                    if (!city.startsWith("(")) {
                        lastCity = "Country=" + country.split(" \\[")[0] + " | State=" + state.split(" \\[")[0]
                                + " | City=" + city;
                        System.out.println("Postal: " + lastCity + "  (after " + tries + " tries)");
                        return lastCity;
                    }
                }
                tried.append(tries == 1 ? "" : "; ").append(country.split(" \\[")[0])
                     .append("/").append(state.split(" \\[")[0]).append(" -> no city");
            }
        }

        lastCity = "(no country offered a city) tried " + tries + " country/state pairs: "
                + (tried.length() > 600 ? tried.substring(0, 600) + "..." : tried);
        System.out.println("Postal: " + lastCity);
        return lastCity;
    }

    /**
     * Wait for the City list to be populated after a state change, and return how many real options it has.
     *
     * <p>The list arrives asynchronously. Counting it immediately after selecting the state reports zero
     * for a state that does have cities, so the search moves on and every state looks empty.</p>
     *
     * @return the number of real (non-placeholder) city options, 0 if none arrived within the timeout
     */
    /** Poll until a dropdown has at least one real option, and return how many it has. */
    private int waitForOptions(String rx, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int n = realOptionCount(rx);
        while (n == 0 && System.currentTimeMillis() < end) {
            page.waitForTimeout(300);
            n = realOptionCount(rx);
        }
        return n;
    }

    private int waitForCityOptions(int before, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int n = realOptionCount("city");
        // GetCity2() re-filters the list, so the count MOVES when the state's cities arrive. Waiting for
        // "not empty" would be no wait at all: Postal.CityId ships with every city in the database (448
        // on this environment) before a country is even chosen, and picking from that unfiltered list
        // would store a city belonging to some other state.
        while (n == before && System.currentTimeMillis() < end) {
            page.waitForTimeout(300);
            n = realOptionCount("city");
        }
        return n;
    }

    /**
     * Pick the n-th real option, skipping a filler row literally named "No Information" when a genuine
     * option exists — selecting the filler guarantees an empty list below it.
     */
    private String pickNthPreferringReal(String rx, int nth) {
        Object idx = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#__poSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(s.getAttribute('ng-model')||''))"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__poSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*select/i.test(norm(x.o.text)));"
                + " const real=reals.filter(x=>!/^no\s*information$/i.test(norm(x.o.text)));"
                + " const pool = real.length? real : reals;"
                + " return pool[a.n]? pool[a.n].i : -1; }",
                java.util.Map.of("rx", rx, "n", nth));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-option)";
        try {
            page.locator("#__poSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__poSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        return t == null ? "" : t.toString();
    }


    /** Select a country by name, so a deployment's real data can be targeted directly. */
    private String pickCountryByName(String name) {
        Object idx = page.evaluate("(n) => {" + JS
                + " document.querySelectorAll('#__poSel').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>/country/i.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__poSel';"
                + " return [...e.options].findIndex(o=>o.value"
                + "   && norm(o.text).toLowerCase()===String(n).toLowerCase()); }", name);
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(country \"" + name + "\" not in the list)";
        try {
            page.locator("#__poSel").selectOption(
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
                + " document.querySelectorAll('#__poSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__poSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return reals[a.n]? reals[a.n].i : -1; }",
                java.util.Map.of("rx", rx, "n", nth));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-option)";
        try {
            page.locator("#__poSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__poSel');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        return (t == null ? "" : t.toString());
    }

    /** Wait for a cascading dropdown to gain a real option, then take the first one natively. */
    private String pickWhenReady(String which) {
        // The field is LABELLED "City/District" but its model is Postal.CityId — there is no ng-model
        // containing "district" on this form. Mapping anything-but-country/state to "district" (carried
        // over from the City/District screen this was scaffolded from) meant the city was searched for
        // under a name the screen does not use, so it was never found however long the wait.
        String rx = which;
        try {
            page.waitForFunction("(rx) => { const re=new RegExp(rx,'i');"
                    + " return [...document.querySelectorAll('select')]"
                    + "  .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + "  .filter(e=>re.test(e.getAttribute('ng-model')||''))"
                    + "  .some(e=>[...e.options].some(o=>o.value"
                    + "    && !/^-*\s*select/i.test((o.text||'').trim()))); }", rx,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("Postal.pick(" + which + "): no option within 15s");
        }
        Object info = page.evaluate("(rx) => {" + JS
                + " document.querySelectorAll('#__poSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(s.getAttribute('ng-model')||''))"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return null; e.id='__poSel';"
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
            page.locator("#__poSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__poSel');"
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
    public boolean noCityAnywhere() {
        return lastCity != null && lastCity.startsWith("(no country offered a city)");
    }

    /**
     * Enter the <b>Postal</b> code.
     *
     * <p>Addressed by ng-model, and the cascade's own selects are excluded so the value cannot land in a
     * neighbouring box: on Area/Town every model began "area.", and a keyword match for the screen's own
     * name found the Code field and overwrote it.</p>
     */
    public String enterPostal(String postal) {
        Object r = page.evaluate("(v) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const byNg=(re)=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
                + " const el = byNg(/postal|pincode|zip/i)"
                + "   || boxes.find(e=>/postal|pin\\s*code|zip/i.test((e.placeholder||'')+' '+labelOf(e)))"
                + "   || byNg(/\\.(code|description|name)$/i);"
                + " if(!el) return '(no-postal-field)';"
                + " return setEl(el,v)+' ['+(el.getAttribute('ng-model')||'?')+']'; }", postal);
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Postal: " + lastEntry);
        return lastEntry;
    }

    public boolean postalEntered(String postal) {
        return lastEntry != null && !lastEntry.startsWith("(no-") && lastEntry.startsWith(postal);
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
        System.out.println("Postal: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /** Look the code up on the screen's own grid — the toast alone is not proof of a write. */
    /** The city text chosen by the cascade, e.g. "Auto City 28300" — "" if the cascade did not complete. */
    public String selectedCity() {
        if (lastCity == null) return "";
        int i = lastCity.indexOf("City=");
        if (i < 0) return "";
        String c = lastCity.substring(i + 5);
        int b = c.indexOf(" [");
        return (b < 0 ? c : c.substring(0, b)).trim();
    }

    public boolean codeInList(String code) {
        waitForAngular(1500);
        // A refused Submit leaves the entry form on screen, where there is no grid at all — reading it
        // reports "0 rows" and looks like an empty screen. The list is a separate route (#/PostalMaster,
        // 111 pages of records here), so go back to it before filtering.
        try {
            if (page.url().toLowerCase().contains("add-postal")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(1500);
                if (page.url().toLowerCase().contains("add-postal")) {
                    page.navigate(page.url().replaceAll("(?i)#/add-Postal.*", "#/PostalMaster"));
                    waitForAngular(2500);
                }
            }
        } catch (Exception ignore) { }
        try {
            // The grid carries ONE colFilter.term box PER COLUMN — Country, State, City/District and
            // Postal Code all share that ng-model. Taking the first one filters the COUNTRY column, so a
            // postal code matches nothing and the grid empties: that is what "0 rows shown" was. Pick the
            // box that sits inside the "Postal Code" header cell.
            page.evaluate("(c) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const inPostalCol=boxes.find(b=>{"
                    + "   const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /postal/i.test(norm(h.textContent)); });"
                    + " const f=inPostalCol||boxes[boxes.length-1];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
            waitForAngular(2500);
            // Match the WHOLE record, not just the code. Postal codes here are 5 digits, so a generated
            // one collides with a real Malaysian code sooner or later: a run that saved Johor / Auto City
            // 28300 / 33400 found "Malaysia Perak Lenggong 33400" — an unrelated record that already
            // existed — and would have called that proof its own save worked.
            String city = selectedCity();
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.city? withCode.find(t=>t.includes(a.city)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+') — not the one just saved (city \"'"
                    + "   +a.city+'\")';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "city", city));
            lastListCheck = "looked for \"" + code + "\"" + (city.isEmpty() ? "" : " + city \"" + city + "\"")
                    + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("Postal: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
