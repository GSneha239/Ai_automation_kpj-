package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>District</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → select <b>Country</b> and <b>State</b> → enter the <b>Code</b> and
 * <b>District</b> → <b>Submit</b> → success toast.</p>
 */
public class District extends BasePage {

    public District(Page page) { super(page); }

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
     * Application Configuration &rarr; General &rarr; District.
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
                    // Anchored: "District" alone, never "City/District" — the two sit side by side in
                    // this menu and a loose match takes whichever comes first.
                    + "   .find(x=>/^\\s*district\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__dsMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__dsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("District.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dsMenu'); if(e) e.removeAttribute('id'); }");
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
     * On the District screen.
     *
     * <p>Matched against the route the MENU gave rather than a guessed keyword, and the neighbours are
     * rejected outright: "District" is a substring of "City/District", so a loose check would accept the
     * wrong screen — which is exactly how a Compliance run once passed against Complaint Type.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (u.contains("area") || u.contains("town")) return false;      // sibling Area/Town
        if (u.contains("citydistrict") || u.contains("city")) return false;  // sibling City/District
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("district");
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
        System.out.println("=== District CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?district|AddDistrict/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__dsAdd'; }");
        try {
            page.locator("#__dsAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("District.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dsAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("District.clickAdd: Code field never appeared"); }
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
     * <p>They CASCADE: State is filled by the Country choice, so State is waited for after Country rather
     * than read at a fixed moment — a single read lands on the empty list and reports "(no-option)" for a
     * screen that is only still loading.</p>
     *
     * <p>Each is addressed by its own ng-model. On this module's forms every model shares one prefix, so a
     * keyword match for the screen's own name finds the wrong control — that is how the sibling Area/Town
     * screen ended up writing the town name into its Code box.</p>
     */
    public String selectCountryState() {
        // Countries are tried until one yields a State: most carry none (the first alphabetically,
        // Afghanistan, gives only "No Information"), and this deployment's real data is tried first.
        String[] preferred = System.getProperty("countries", "Malaysia,Singapore,India,Indonesia")
                .split(",");
        StringBuilder tried = new StringBuilder();

        for (String want : preferred) {
            String c = pickCountryByName(want.trim());
            if (c.startsWith("(")) continue;
            waitForAngular(1200);
            String s = pickWhenReady("state");
            tried.append(tried.length() == 0 ? "" : "; ").append(want.trim())
                 .append(s.startsWith("(") ? " -> no state" : " -> ok");
            if (!s.startsWith("(")) {
                lastCity = "Country=" + c + " | State=" + s;
                System.out.println("District: " + lastCity);
                return lastCity;
            }
        }

        int countries = realOptionCount("country");
        for (int i = 0; i < Math.min(countries, Integer.getInteger("maxCountries", 12)); i++) {
            String c = pickNth("country", i);
            if (c.startsWith("(")) continue;
            waitForAngular(1200);
            String s = pickWhenReady("state");
            tried.append("; ").append(c.split(" \\[")[0]).append(s.startsWith("(") ? " -> no state" : " -> ok");
            if (!s.startsWith("(")) {
                lastCity = "Country=" + c + " | State=" + s;
                System.out.println("District: " + lastCity);
                return lastCity;
            }
        }

        lastCity = "(no country offered a state) tried: " + tried;
        System.out.println("District: " + lastCity);
        return lastCity;
    }


    /** Select a country by name, so a deployment's real data can be targeted directly. */
    private String pickCountryByName(String name) {
        Object idx = page.evaluate("(n) => {" + JS
                + " document.querySelectorAll('#__dsSel').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>/country/i.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__dsSel';"
                + " return [...e.options].findIndex(o=>o.value"
                + "   && norm(o.text).toLowerCase()===String(n).toLowerCase()); }", name);
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(country \"" + name + "\" not in the list)";
        try {
            page.locator("#__dsSel").selectOption(
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
                + " document.querySelectorAll('#__dsSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return -1; e.id='__dsSel';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return reals[a.n]? reals[a.n].i : -1; }",
                java.util.Map.of("rx", rx, "n", nth));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-option)";
        try {
            page.locator("#__dsSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i));
        } catch (Exception e) { return "(select failed)"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dsSel');"
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
            System.out.println("District.pick(" + which + "): no option within 15s");
        }
        Object info = page.evaluate("(rx) => {" + JS
                + " document.querySelectorAll('#__dsSel').forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(s.getAttribute('ng-model')||''))"
                + "   .find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " if(!e) return null; e.id='__dsSel';"
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
            page.locator("#__dsSel").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dsSel');"
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

    /** True when the run searched and found no country with any state — a master-data gap. */
    public boolean noStateAnywhere() {
        return lastCity != null && lastCity.startsWith("(no country offered a state)");
    }

    /**
     * Enter the <b>Code</b> and the <b>District</b> name.
     *
     * <p>Both are addressed by exact ng-model. The models are discovered from the form dump rather than
     * guessed: on Area/Town every model began "area.", so a keyword match for the screen's own name found
     * the Code box and overwrote it.</p>
     */
    public String enterCodeAndDistrict(String code, String district) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, ci] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const byNg=(re)=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
                + " const codeEl=byNg(/\\.code$/i);"
                + " const cityEl=byNg(/\\.(description|name)$/i);"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(codeEl.getAttribute('ng-model')||'?')+']' : '(no-code-field)';"
                + " const rn = cityEl? setEl(cityEl,ci)+' ['+(cityEl.getAttribute('ng-model')||'?')+']' : '(no-district-field)';"
                + " return 'Code='+rc+' | District='+rn; }", java.util.List.of(code, district));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("District: " + lastEntry);
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
        System.out.println("District: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /** Look the code up on the screen's own grid — the toast alone is not proof of a write. */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        // Make sure we are ON the list before filtering. Submit does not always leave the form, and a
        // check run against the entry form finds "0 rows" and reports a saved record as missing.
        try {
            page.evaluate("() => {" + JS
                    + " const onForm=[...document.querySelectorAll('input')].filter(vis)"
                    + "   .some(e=>/\\.code$/i.test(e.getAttribute('ng-model')||''));"
                    + " if(!onForm) return;"
                    + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                    + " if(b) b.click(); }");
            waitForAngular(2500);
            page.waitForFunction("() => [...document.querySelectorAll('tr,.ui-grid-row')]"
                    + " .filter(t=>t.offsetWidth||t.offsetHeight||t.getClientRects().length).length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("District.codeInList: list rows never appeared");
        }
        waitForAngular(600);
        try {
            // Show every row and DO NOT use the column filter: that filter is bound to one column only,
            // and typing the Code into it emptied the grid — which reported a record that had just saved
            // successfully as missing. Widening and scanning is what actually answers the question.
            page.evaluate("() => {" + JS
                    + " const size=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                    + " if(size){ size.selectedIndex=size.options.length-1;"
                    + "   size.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " const last=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/pageLastPageClick/i.test(x.getAttribute('ng-click')||''));"
                    + " if(last) last.click(); }");
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
        System.out.println("District: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
