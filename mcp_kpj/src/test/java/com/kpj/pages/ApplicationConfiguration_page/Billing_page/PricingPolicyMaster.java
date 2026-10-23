package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Pricing Policy Master</b> (route
 * {@code #/tariff}) → <b>Add</b> ({@code #/add-tariff}) → enter <b>Code</b>, <b>Remark</b>, tick a
 * <b>Location Details</b> checkbox → select <b>Class</b>, <b>Group</b>, <b>Subgroup</b>, enter <b>Discount</b> →
 * click <b>Add</b> ({@code AddTariffDetails}) → <b>Submit</b> ({@code fnIUDtariffDetails('Discount')}) → toast.</p>
 *
 * <p>Same {@code #/add-tariff} form as [[devhis-price-revision-applicability]] (that one is reached via
 * "Price Revision/ Applicability" → {@code #/ServiceApplicableList}); this is reached via "Pricing Policy Master"
 * → {@code #/tariff}. Here the full detail line (Class/Group/Subgroup/Discount + Add) is exercised.</p>
 */
public class PricingPolicyMaster extends BasePage {

    public PricingPolicyMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/tariff";
    public static String ADD_ROUTE = "#/add-tariff";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic Remark values — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] REMARKS = {
            "Standard Pricing Policy", "Corporate Discount Policy", "Insurance Panel Pricing", "Government Scheme Pricing",
            "Staff Concession Policy", "Senior Citizen Discount Policy", "Promotional Pricing Policy",
            "Package Rate Policy", "Special Panel Pricing", "Annual Tariff Policy"
    };

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // "Billing" is ALSO a top-level module in the sidebar, listed before Application Configuration: an unscoped lookup clicks
        // that one, which collapses the Application Configuration menu again. Look only inside the Application Configuration entry.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); const scope=(top&&top.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*pricing\\s*policy\\s*master\\s*$/i.test(norm(x.textContent)) || /#\\/tariff\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ppMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__ppMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PricingPolicyMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("PricingPolicyMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase();
        return (u.contains("#/tariff") || u.contains("add-tariff")) && !u.contains("serviceapplicable");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-tariff}. */
    public boolean clickAdd() {
        boolean tagged = false;
        // The list has 500+ rows and its Add button only appears once the screen has loaded — poll up to 30 s.
        for (int i = 0; i < 60 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ppAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PricingPolicyMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ppAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("PricingPolicyMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            // wait for the Code input, a Location checkbox, AND the Base Pricing Policy / Service Group options (all load async)
            page.waitForFunction("() => { const code=[...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='tariffDetails.Code');"
                    + " const loc=[...document.querySelectorAll(\"input[type=checkbox][ng-model='loc.IsSelected']\")].some(e=>e.offsetParent!==null);"
                    + " const hasOpt=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); };"
                    + " return code && loc && hasOpt('tariffDetails.base_tariffid') && hasOpt('tariffDetails.groupid'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(18000));
        } catch (Exception ignore) { System.out.println("PricingPolicyMaster.clickAdd: add form/options not fully ready"); }
        waitForAngular(1000);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private int selectableCount(String selNg) {
        Object c = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return 0; return [...e.options].filter(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }",
                "select[ng-model='" + selNg + "']");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    private String selectOrdinal(String selNg, int ordinal) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && (x.o.textContent||'').trim() && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectOrdinal " + selNg + " failed: " + e.getMessage()); return "(err)"; }
        waitForAngular(500);
        return chosen;
    }

    /** Select the first option whose text matches {@code rx} (case-insensitive); "(no match)" when none does. */
    private String selectMatching(String selNg, String rx) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const re=new RegExp(a.rx,'i');"
                + " return [...e.options].findIndex(o=>o.value && re.test((o.textContent||'').replace(/\\s+/g,' ').trim())); }",
                java.util.Map.of("s", sel, "rx", rx));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no match)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(500);
            return String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { return "(err " + e.getMessage().split("\n")[0] + ")"; }
    }

    /** Enter Code + Remark and tick a Location Details checkbox. {@code attempt} shifts BOTH Code and Remark
     *  so a retry after an "already exists" toast submits genuinely different details. */
    public String fillHeader(int attempt) {
        // Stamped from the wall clock: nanoTime remainders end in "00" on this machine (only ~10,000 distinct codes).
        String code = "PP" + String.format("%06d", (System.currentTimeMillis() + attempt * 7919L) % 1000000);
        lastCode = code;
        // Unique per run (a fixed pool is exhausted for good on the shared QA env), kept within 30 characters.
        String suffix = " " + code.substring(2);
        String base = REMARKS[attempt % REMARKS.length];
        lastRemark = (base.length() + suffix.length() > 30 ? base.substring(0, 30 - suffix.length()).trim() : base) + suffix;
        setNg("tariffDetails.Code", code);
        setNg("tariffDetails.Description", lastRemark);
        waitForAngular(200);
        // Base Pricing Policy (a Select2 dropdown over tariffDetails.base_tariffid, ~210 payors) — the first real option.
        // The base policy supplies the services (and their rates) that the Service search offers, so prefer the main Self-Pay /
        // Cash tariff, which carries the full rate list; the first option on the list is an insurer tariff with no rates.
        lastBasePolicy = selectMatching("tariffDetails.base_tariffid", "^\\s*self\\s*-?\\s*pay\\b.*|.*\\bcash\\s+customer\\b.*");
        if (lastBasePolicy.startsWith("(")) lastBasePolicy = selectOrdinal("tariffDetails.base_tariffid", 1);
        String loc = "(none)";
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='loc.IsSelected']\")].find(x=>x.offsetParent!==null && !x.checked); if(!cb) return false; cb.id='__ppLoc'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ppLoc").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); loc = "ticked 1 location"; }
            catch (Exception e) { try { page.locator("#__ppLoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); loc = "clicked 1 location"; } catch (Exception e2) { System.out.println("fillHeader: location tick failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__ppLoc'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return "Code=" + code + " | Description=" + lastRemark + " | BasePricingPolicy=" + lastBasePolicy + " | Location=" + loc;
    }

    public String lastBasePolicy = "", lastService = "", lastAmount = "", lastType = "", lastSuggestions = "";
    /** Screenshot of the filled detail section taken before Add (Add moves the values into the grid and blanks them). */
    public byte[] detailPng;
    /** Screenshot taken while the Submit toast was on screen. */
    public byte[] toastPng;

    /** Visible items of whatever suggestion list the Service box opened (typeahead / autocomplete / dropdown). */
    private static final String SUGGEST_JS =
            "const vis=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const sugg=()=>[...document.querySelectorAll('ul.dropdown-menu li, .typeahead li, .ui-autocomplete li, [role=listbox] li, [role=option],"
          + " .angucomplete-row, .angucomplete-dropdown .angucomplete-row, .autocomplete-suggestions div, .tt-suggestion, .uib-typeahead-match, li[uib-typeahead-match]')]"
          + ".filter(vis).filter(e=>norm(e.textContent).length>1);";

    /**
     * Type into the <b>Service*</b> box ({@code tariffDetails.servicename}) and pick the first suggestion. The box is a search
     * (typeahead) field, so a character is typed for real and the list that opens is clicked. Several first letters are tried
     * until some service matches. Returns the chosen service text, or a reason.
     */
    public String pickService() {
        final String sel = "input[ng-model='tariffDetails.servicename']";
        // The box is a custom autocomplete (auto-complete="ServiceAutoCompleteOptions") that answers nothing for one letter:
        // search with 3 characters, starting with the selected Service Group's own name (e.g. "Bed").
        String groupName = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='tariffDetails.groupid']\");"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }"));
        String[] terms = {groupName.length() >= 3 ? groupName.substring(0, 3).toLowerCase() : "bed", "con", "cha", "ser", "pro", "lab"};
        if (!lastServiceHint.isEmpty()) {
            // A service that really is listed: search for it by its own first letters (3 is the minimum the box accepts).
            String alnum = lastServiceHint.replaceAll("[^A-Za-z0-9 ]", "").trim();
            terms = new String[]{alnum.substring(0, Math.min(3, alnum.length())), alnum.substring(0, Math.min(5, alnum.length()))};
        }
        StringBuilder diag = new StringBuilder();
        // The Service search only answers for a policy that has been SAVED: its data function returns an empty list while
        // $scope.tariffDetailsPkID is unset, and that id only exists after the first Submit. On a new policy one 3-letter
        // search is made to show that, then the box is left empty.
        long pkId = policyId();
        if (pkId <= 0) terms = new String[]{terms[0]};
        // What the search calls return (URL, request body, status, row count) — shown in the step so an empty list explains itself.
        final java.util.List<String> searchHttp = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url().toLowerCase();
                String body = resp.request().postData() == null ? "" : resp.request().postData();
                if (!(u.contains("tariff") || body.contains("GETSERVICESWITHRATE"))) return;
                String rb = ""; try { rb = resp.text(); } catch (Exception ignore) { rb = "(body unavailable)"; }
                int rows = rb.startsWith("[") ? rb.split("\\},\\s*\\{", -1).length : -1;
                searchHttp.add(resp.request().method() + " " + resp.url().replaceFirst("^https?://[^/]+", "") + " " + resp.status()
                        + " request=" + body.replaceAll("\\s+", " ").substring(0, Math.min(220, body.replaceAll("\\s+", " ").length()))
                        + " -> " + (rb.equals("[]") ? "0 rows" : (rows >= 0 ? "~" + rows + " row(s)" : rb.substring(0, Math.min(120, rb.length())))));
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        {
        for (String t : terms) {
            try {
                page.locator(sel).first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
                page.locator(sel).first().fill("");
                page.locator(sel).first().pressSequentially(t, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(120));
            } catch (Exception e) { diag.append("[type '").append(t).append("' failed: ").append(e.getMessage().split("\n")[0]).append("] "); continue; }
            // wait up to 4 s for a suggestion list
            Object n = 0;
            for (int i = 0; i < 16; i++) {
                n = page.evaluate("() => {" + SUGGEST_JS + " return sugg().length; }");
                if (n instanceof Number && ((Number) n).intValue() > 0) break;
                page.waitForTimeout(250);
            }
            int count = n instanceof Number ? ((Number) n).intValue() : 0;
            diag.append("'").append(t).append("' -> ").append(count).append(" suggestion(s); ");
            if (count == 0 && pkId > 0) {
                Object dd = page.evaluate("() => { const els=[...document.getElementsByClassName('txtPolicyService')].map(e=>e.tagName+'.'+e.className+' vis='+!!(e.offsetWidth||e.offsetHeight)+' html='+e.outerHTML.replace(/\\s+/g,' ').slice(0,500));"
                        + " const ac=[...document.querySelectorAll('[class*=utocomplete],[class*=ac-],[class*=dropdown]')].filter(e=>e.offsetWidth||e.offsetHeight).slice(0,4).map(e=>e.tagName+'.'+e.className+' :: '+e.outerHTML.replace(/\\s+/g,' ').slice(0,300));"
                        + " return 'byClass: '+JSON.stringify(els)+' | visible ac-like: '+JSON.stringify(ac); }");
                System.out.println("PricingPolicyMaster dropdown probe ('" + t + "') => " + dd);
            }
            if (count > 0) {
                Object first = page.evaluate("() => {" + SUGGEST_JS + " const e=sugg()[0]; if(!e) return ''; e.id='__ppSugg'; return norm(e.textContent); }");
                lastSuggestions = String.valueOf(first);
                try { page.locator("#__ppSugg").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
                catch (Exception e) { diag.append("[click failed: ").append(e.getMessage().split("\n")[0]).append("] "); }
                page.evaluate("() => { const e=document.getElementById('__ppSugg'); if(e) e.removeAttribute('id'); }");
                waitForAngular(700);
                Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", sel);
                lastService = v == null ? "" : v.toString().trim();
                if (!lastService.isEmpty()) return lastService + "  [typed '" + t + "', picked: " + lastSuggestions + "]";
            }
        }
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastService = "";
        lastServiceHttp = searchHttp.isEmpty() ? "(no search call was made)" : String.join(" ;; ", searchHttp);
        return (pkId <= 0 ? "(the Service search returns nothing for a policy that has not been saved yet: the screen has no policy id until Submit) " : "(no service found) ")
                + diag + "[search calls: " + lastServiceHttp + "]";
        }
    }

    /** What the Service search calls returned (see {@link #pickService()}). */
    public String lastServiceHttp = "";
    public String lastGroup = "", lastSub = "", lastServiceHint = "", lastScan = "";

    /**
     * Walk the Service Group / Service Sub - Group dropdowns until a pair that lists at least one service is found. Selecting a
     * Sub - Group makes the screen call {@code TariffMaster/Fetch} (GETSERVICESWITHRATE, no search text); a non-empty answer carries
     * the services, and the first one's name is kept in {@link #lastServiceHint} to search for. The pair stays selected.
     */
    private String scanGroupsForServices(int maxGroups) {
        final java.util.List<String> bodies = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String req = resp.request().postData() == null ? "" : resp.request().postData();
                if (resp.url().toLowerCase().contains("tariffmaster/fetch") && req.contains("GETSERVICESWITHRATE") && !req.contains("serviceName"))
                    bodies.add(resp.text());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        int pairs = 0;
        try {
            int groups = Math.min(selectableCount("tariffDetails.groupid"), maxGroups);
            for (int g = 1; g <= groups; g++) {
                lastGroup = selectOrdinal("tariffDetails.groupid", g);
                waitForAngular(300);
                try {
                    page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='tariffDetails.subgroupid']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                            null, new Page.WaitForFunctionOptions().setTimeout(2500));
                } catch (Exception noSubs) { continue; }
                int subs = Math.min(selectableCount("tariffDetails.subgroupid"), 3);
                for (int s = 1; s <= subs; s++) {
                    bodies.clear();
                    lastSub = selectOrdinal("tariffDetails.subgroupid", s);
                    pairs++;
                    // The services of the pair are fetched when the Service box is entered (not when the Sub - Group changes).
                    try { page.locator("input[ng-model='tariffDetails.servicename']").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000)); }
                    catch (Exception ignore) { }
                    for (int i = 0; i < 10 && bodies.isEmpty(); i++) page.waitForTimeout(250);
                    String b = bodies.isEmpty() ? "" : bodies.get(0);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Description\"\\s*:\\s*\"([^\"]+)\"").matcher(b);
                    if (m.find()) {
                        lastServiceHint = m.group(1).trim();
                        lastScan = "Service Group '" + lastGroup + "' / Sub - Group '" + lastSub + "' lists services (first: " + lastServiceHint + "); " + pairs + " pair(s) scanned";
                        return lastScan;
                    }
                }
            }
            lastScan = "no Service Group / Sub - Group pair returned a service for this policy (" + pairs + " pair(s) scanned)";
            return lastScan;
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
    }

    /** Type {@code value} into the input with the given ng-model like a user (real keystrokes), then Tab out so the screen recalculates. */
    private String typeReal(String ng, String value) {
        String sel = "input[ng-model='" + ng + "']";
        try {
            page.locator(sel).first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            page.locator(sel).first().fill("");
            page.locator(sel).first().pressSequentially(value, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(60));
            page.keyboard().press("Tab");
        } catch (Exception e) { return "(type failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(400);
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", sel);
        return v == null ? "" : v.toString();
    }

    /**
     * Fill the detail section — <b>Service Group*</b>, <b>Service Sub - Group*</b>, <b>Service*</b>, <b>Type*</b>,
     * <b>Discount Percentage*</b> and <b>Hospital Rate*</b> ({@code Tariff/Payor} is filled by the screen from the
     * Description and {@code Amount} is calculated, both read-only) — then click <b>Add</b> ({@code AddPolicyDetails}) to put
     * the line in the grid.
     */
    public String addDetailLine() {
        lastServiceHint = "";
        String group, sub;
        if (policyId() > 0) {
            // Saved policy: the Service search only lists services that have a rate under it, and most Service Group / Sub - Group
            // pairs have none (Bed / Bed Charges has none), so look for a pair that does and search for one of its services.
            String scan = scanGroupsForServices(70);
            group = lastGroup;
            sub = lastSub;
            if (lastServiceHint.isEmpty()) System.out.println("PricingPolicyMaster: " + scan);
        } else {
            group = selectOrdinal("tariffDetails.groupid", 1);
            waitForAngular(700);
            try {
                page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='tariffDetails.subgroupid']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(6000));
            } catch (Exception ignore) { }
            sub = selectableCount("tariffDetails.subgroupid") > 0 ? selectOrdinal("tariffDetails.subgroupid", 1) : "(no sub-group options)";
        }
        String service = pickService();
        lastType = selectOrdinal("tariffDetails.policytype", 1);              // Discount | Mark Up | Flat Amount -> Discount
        String disc = typeReal("tariffDetails.discount", "5");
        String rate = typeReal("tariffDetails.hospitalrate", "100");
        Object amt = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='tariffDetails.amount']\"); return e? e.value : ''; }");
        lastAmount = amt == null ? "" : amt.toString();
        String tariff = String.valueOf(page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='tariffDetails.Description']\")].find(x=>x.disabled); return e? e.value : ''; }"));
        try { detailPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("addDetailLine: screenshot failed - " + e.getMessage()); }

        int before = gridRowCount();
        page.evaluate("() => { window.__ppToasts=[]; if(window.__ppObs) window.__ppObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ppToasts.includes(t)) window.__ppToasts.push(t); }); };"
                + " window.__ppObs=new MutationObserver(grab); window.__ppObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddPolicyDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__ppAddLine'; }");
        try { page.locator("#__ppAddLine").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("addDetailLine: Add click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ppAddLine'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        Object toast = page.evaluate("() => { const a=window.__ppToasts||[]; return a.find(x=>/tariff|detail|policy|added|success|please|select|enter|discount|group|service|rate/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = gridRowCount();
        String rowMsg = (after > before) ? "(detail row added: grid rows " + before + " -> " + after + ")" : (t.isEmpty() ? "(no row/toast: grid rows " + before + " -> " + after + ")" : "(no row added; screen said: " + t + ")");
        return "Group=" + group + " | SubGroup=" + sub + " | Service=" + (lastService.isEmpty() ? service : lastService) + " | Type=" + lastType
                + " | Discount%=" + disc + " | HospitalRate=" + rate + " | Amount(calculated)=" + lastAmount + " | Tariff/Payor=" + tariff + " | " + rowMsg
                + (policyId() > 0 && !lastScan.isEmpty() ? " | scan: " + lastScan : "");
    }

    private int gridRowCount() {
        Object c = page.evaluate("() => document.querySelectorAll('.ui-grid-row').length");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /**
     * The id of the policy on the form ({@code $scope.tariffDetailsPkID}) — unset (0 here) for a new, unsaved policy and set
     * once it has been saved and reopened. The Service search is switched off while it is unset.
     */
    public long policyId() {
        Object r = page.evaluate("() => { try { const e=document.querySelector(\"input[ng-model='tariffDetails.Code']\"); if(!e) return 0;"
                + " const v=window.angular.element(e).scope().tariffDetailsPkID; return (v==null||v==='')? 0 : Number(v)||0; } catch(x){ return 0; } }");
        return r instanceof Number ? ((Number) r).longValue() : 0;
    }

    /**
     * Open the saved policy for editing: filter the list by its Code, click the row's edit icon, and wait until the form shows
     * that Code with a policy id (which is what turns the Service search on).
     */
    public String openSavedPolicy(String code) {
        // After Submit the screen returns to the list. Wait for the Code filter box, then filter.
        try {
            page.waitForFunction("() => [...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].some(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { return "(the Pricing Policy list did not show: " + page.url() + ")"; }
        waitForAngular(800);
        try {
            page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].find(x=>x.offsetParent!==null); if(e) e.id='__ppFilter'; }");
            page.locator("#__ppFilter").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            page.locator("#__ppFilter").first().fill(code);
            page.keyboard().press("Enter");
            page.evaluate("() => { const e=document.getElementById('__ppFilter'); if(e) e.removeAttribute('id'); }");
        } catch (Exception e) { return "(could not filter the list by Code: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(1500);
        Object tagged = page.evaluate("(code) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const row=[...document.querySelectorAll('.ui-grid-row')].filter(vis).find(r=>norm(r.textContent).includes(code));"
                + " if(!row) return 'no row for '+code+' ('+document.querySelectorAll('.ui-grid-row').length+' rows shown)';"
                // The Edit column is the 5th cell (check, Code, Description, Status, Edit, Link, Link Item): never fall back to the
                // first icon in the row, that is the row's selection tick.
                + " const cells=row.querySelectorAll('.ui-grid-cell');"
                + " const ed=row.querySelector('[ng-click*=\"dit\"], .fa-edit, .glyphicon-edit, [class*=\"edit\"]')"
                + "   || (cells[4] && (cells[4].querySelector('a,button,i,img,span') || cells[4]));"
                + " if(!ed) return 'row found but no edit control: '+norm(row.textContent).slice(0,120);"
                + " ed.id='__ppEdit'; return 'ok'; }", code);
        if (!"ok".equals(String.valueOf(tagged))) return "(" + tagged + ")";
        try { page.locator("#__ppEdit").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { return "(edit click failed: " + e.getMessage().split("\n")[0] + ")"; }
        page.evaluate("() => { const e=document.getElementById('__ppEdit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("(code) => { const e=document.querySelector(\"input[ng-model='tariffDetails.Code']\"); return !!e && e.value===code; }",
                    code, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { return "(the edit form did not open for " + code + ": " + page.url() + ")"; }
        waitForAngular(1500);
        long id = policyId();
        return id > 0 ? "opened " + code + " for editing (policy id " + id + ")" : "(opened " + code + " but the screen has no policy id)";
    }

    /**
     * PROBE — every visible form control: tag/type, ng-model, readonly/disabled, nearby label, and for a dropdown its
     * option count + first options; plus the visible buttons with their ng-click. Put in the report so a screen change
     * is diagnosable from one run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                + " const lbl=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } } return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|paginationCurrentPage|paginationPageSize/i.test(e.getAttribute('ng-model')||'')).slice(0,60).map(e=>{"
                + "   const o=e.tagName==='SELECT'? ' opts='+e.options.length+' ['+[...e.options].slice(0,4).map(x=>norm(x.text)).join(' | ')+']' : '';"
                + "   return e.tagName.toLowerCase()+(e.type&&e.tagName!=='SELECT'?'['+e.type+']':'')+'['+(e.getAttribute('ng-model')||e.id||e.name||'?')+']'"
                + "     +(e.readOnly?' READONLY':'')+(e.disabled?' DISABLED':'')+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':'')+o; });"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(vis)"
                + "   .map(e=>norm(e.textContent||e.value)+'['+(e.getAttribute('ng-click')||'')+']').filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code fnIUDtariffDetails}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__ppToasts=[]; if(window.__ppObs) window.__ppObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ppToasts.includes(t)) window.__ppToasts.push(t); }); };"
                + " window.__ppObs=new MutationObserver(grab); window.__ppObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnSavePricingPolicy|fnIUDtariffDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ppSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__ppSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__ppToasts||[]).some(a=>/tariff|pricing|policy|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ppToasts||[]).includes(t)) (window.__ppToasts=window.__ppToasts||[]).push(t); }); }");
        }
        // Screenshot the toast while it is on screen and fully faded in — a shot taken after the step misses it.
        toastPng = null;
        for (int w = 0; w < 15; w++) {
            boolean solid = Boolean.TRUE.equals(page.evaluate("() => { const t=[...document.querySelectorAll('.toast,.toast-message,[id^=toast]')]"
                    + ".filter(el=>el.offsetParent!==null && (el.textContent||'').trim()); if(!t.length) return true;"
                    + " return t.every(el=>{ let o=1; for(let n=el; n && n!==document.body; n=n.parentElement) o*=parseFloat(getComputedStyle(n).opacity||'1'); return o>=0.98; }); }"));
            if (solid) break;
            page.waitForTimeout(100);
        }
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: toast screenshot failed - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__ppToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
