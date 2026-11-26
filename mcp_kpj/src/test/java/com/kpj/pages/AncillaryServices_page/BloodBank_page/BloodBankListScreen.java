package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared page object for the Ancillary Services &gt; Blood Bank screens that follow the same layout: a <b>list</b> (From Date, To Date and
 * Blood Bag No. filters, <b>Search</b>, <b>Add</b>, a ui-grid) and an <b>Add form</b> that is opened for one blood bag found by its number.
 * Used by {@link RedCellSerology} and {@link BloodBagComponentExtraction}.
 *
 * <p>Both screens load slowly (the view is fetched after the route changes), so every step waits for the screen's OWN controls, never
 * for the URL alone; a typed hash bounces to the dashboard, so the sidebar link is the only way in.</p>
 */
public abstract class BloodBankListScreen extends BasePage {

    protected BloodBankListScreen(Page page) { super(page); }

    /** Sidebar entry under Ancillary Services &gt; Blood Bank (compared with spaces removed, case-insensitive). */
    protected abstract String leafText();
    /** Lower-case hash of the list, e.g. {@code #/redcellserologylist}. */
    protected abstract String listHash();
    /** Lower-case hash of the add form. */
    protected abstract String addHash();
    /** ng-model prefix of the list filters ({@code <prefix>.fromdate / todate / bloodbagno}). */
    protected abstract String filterModel();
    /** ng-click of the list's Search button. */
    protected abstract String searchCall();
    /** ng-model of the bag number box on the add form. */
    protected abstract String addFormBagModel();
    /** ng-click of the add form's bag Search button. */
    protected abstract String addFormSearchCall();

    public String lastMenu = "", lastRoute = "", lastSet = "", lastSearch = "", lastWrites = "", lastBagSearch = "";
    public int rowCount = -1;

    protected static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const gridOpts=()=>{ const el=document.querySelector('.ui-grid'); if(!el) return null; let s=angular.element(el).scope();"
          + "  for(let i=0;i<10&&s;i++){ const o=s.gridOptions; if(o && Array.isArray(o.data)) return o; s=s.$parent; } return null; };";

    // ---- navigation ------------------------------------------------------

    /** Ancillary Services &rarr; Blood Bank &rarr; the screen (each level opened only if closed, each lookup scoped to its parent's li). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*ancillary\\s*services?\\s*$");
        expandMenuEntry("^\\s*ancillary\\s*services?\\s*$", "^\\s*blood\\s*bank\\s*$");
        waitForAngular(800);
        Object menu = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis).map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }");
        lastMenu = menu == null ? "" : menu.toString();

        Object href = page.evaluate("(leaf) => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*ancillary\\s*services?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bb=[...topLi.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(bb&&bb.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>norm(x.textContent).split(' ').join('').toLowerCase()===leaf.toLowerCase());"
                + " if(!a) return ''; a.id='__bbMenu'; return a.getAttribute('href')||'link'; }", leafText().replace(" ", ""));
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__bbMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println(getClass().getSimpleName() + ".nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__bbMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onList();
    }

    /** On the list: the route AND the screen's own Search button (the view is drawn after the route changes; a bounce to the dashboard has neither). */
    public boolean onList() {
        if (!page.url().toLowerCase().endsWith(listHash())) return false;
        try {
            page.waitForFunction("(c) => { const b=document.querySelector('button[ng-click=\"'+c+'\"]'); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    searchCall(), new Page.WaitForFunctionOptions().setTimeout(40000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- list ------------------------------------------------------------

    /** The column headings the grid shows (the trailing sort number the grid appends is removed). */
    public String headings() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(t=>t).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** True when the list shows its own filter boxes, Search and Add. */
    public String missingListControls() {
        Object r = page.evaluate("(a) => {" + JS
                + " const miss=[]; const f=a[0];"
                + " ['fromdate','todate','bloodbagno'].forEach(n=>{ const e=document.querySelector(\"input[ng-model='\"+f+\".\"+n+\"']\"); if(!vis(e)) miss.push(n); });"
                + " if(!vis(document.querySelector('button[ng-click=\"'+a[1]+'\"]'))) miss.push('Search');"
                + " if(![...document.querySelectorAll('button,a')].some(b=>vis(b)&&/^\\s*add\\s*$/i.test(norm(b.textContent)))) miss.push('Add');"
                + " return miss.join(', '); }", List.of(filterModel(), searchCall()));
        return r == null ? "" : r.toString();
    }

    /** Set the From Date filter through its model (the box is calendar-only). @return "box value | screen value" */
    public String setFromDate(String dmy) {
        Object r = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(\"input[ng-model='\"+a[0]+\".fromdate']\"); if(!e) return 'no From Date box';"
                + " e.focus(); e.value=a[1];"
                + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a[1]); c.$render(); } }catch(x){}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true}));"
                + " let m=''; try{ m=String(angular.element(e).scope().$eval(a[0]+'.fromdate')); }catch(x){}"
                + " return e.value+' | '+m; }", List.of(filterModel(), dmy));
        lastSet = r == null ? "" : r.toString();
        return lastSet;
    }

    /** Type a blood bag number into the list's Blood Bag No. filter. */
    public void fillListBag(String no) {
        page.locator("input[ng-model='" + filterModel() + ".bloodbagno']").first().fill(no);
        waitForAngular(300);
    }

    /** Click the list's Search and wait for its server call; @return how many rows the grid holds afterwards (-1 when no grid). */
    public int clickSearch() {
        String sel = "button[ng-click=\"" + searchCall() + "\"]";
        boolean called = true;
        try {
            page.waitForResponse(r -> r.url().toLowerCase().contains("/api/") && "POST".equals(r.request().method())
                            && !r.url().toLowerCase().contains("getinternalmsgcount"),
                    new Page.WaitForResponseOptions().setTimeout(15000), () -> page.locator(sel).first().click());
        } catch (Exception e) { called = false; }
        waitForAngular(1200);
        Object n = page.evaluate("() => {" + JS + " const o=gridOpts(); return o ? o.data.length : -1; }");
        rowCount = n instanceof Number ? ((Number) n).intValue() : -1;
        lastSearch = (called ? "Search sent its request" : "Search sent no request within 15 s") + "; the grid holds " + rowCount + " row(s)";
        return rowCount;
    }

    /** The first column's values (Blood Bag No.) of up to {@code max} rows, from the grid's own data. */
    public String firstRows(int max) {
        Object r = page.evaluate("(m) => {" + JS + " const o=gridOpts(); if(!o) return ''; return o.data.slice(0,m).map(x=>norm(x.bloodbagno)||'(no bag no)').join(', '); }", max);
        return r == null ? "" : r.toString();
    }

    // ---- add form --------------------------------------------------------

    /** Click Add on the list and wait for the add form's own bag number box. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].find(x=>vis(x)&&/^\\s*add\\s*$/i.test(norm(x.textContent))); if(b) b.click(); }");
        waitForAngular(1500);
        return onAddForm();
    }

    public boolean onAddForm() {
        if (!page.url().toLowerCase().endsWith(addHash())) return false;
        try {
            page.waitForFunction("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\"); return !!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length); }",
                    addFormBagModel(), new Page.WaitForFunctionOptions().setTimeout(30000));
            return true;
        } catch (Exception e) { return false; }
    }

    /** The ng-model names (last segment) of every visible entry control on the add form (the calendar and grid controls left out). */
    public String formModels() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[]; for(const e of document.querySelectorAll('select,input,textarea')){ if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||''; if(!ng || /colFilter|row\\.entity|pagination|grid\\.options/.test(ng)) continue; out.push(ng.split('.').pop()); }"
                + " return [...new Set(out)].join(','); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Type a bag number into the add form and click its Search. @return what the screen's request answered ("[]" = no such bag), and the
     * donor name box afterwards is read with {@link #donorNameValue()}.
     */
    public String searchBagOnForm(String no) {
        page.locator("input[ng-model='" + addFormBagModel() + "']").first().fill(no);
        waitForAngular(300);
        String sel = "button[ng-click=\"" + addFormSearchCall() + "\"]";
        final String[] body = {""};
        try {
            page.waitForResponse(r -> r.url().toLowerCase().contains("/api/") && "POST".equals(r.request().method())
                            && !r.url().toLowerCase().contains("getinternalmsgcount"),
                    new Page.WaitForResponseOptions().setTimeout(15000), () -> page.locator(sel).first().click());
        } catch (Exception e) { lastBagSearch = "the Search sent no request within 15 s"; waitForAngular(800); return lastBagSearch; }
        waitForAngular(1200);
        lastBagSearch = "Search for bag \"" + no + "\" answered";
        return lastBagSearch;
    }

    /** Text of the first donor / name box on the form (empty when nothing was found). */
    public String donorNameValue() {
        Object r = page.evaluate("() => {" + JS
                + " const e=[...document.querySelectorAll('input')].find(x=>vis(x) && /donorname|firstname/i.test((x.getAttribute('ng-model')||'').split('.').pop()));"
                + " return e ? e.value : '(no donor box)'; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click a button on the form and read the message the screen shows. Every POST the click sends is recorded in {@link #lastWrites}
     * (empty = the screen refused it itself and called nothing).
     */
    public String clickAndRead(String buttonText) {
        // A toast from the previous click can still be showing: wait it out so the message read below belongs to THIS click.
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        final List<String> posts = new ArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Request> onReq = r -> {
            try {
                String u = r.url().toLowerCase();
                if ("POST".equals(r.method()) && u.contains("/api/") && !u.contains("getinternalmsgcount")) posts.add(r.url().replaceFirst("^https?://[^/]+", ""));
            } catch (Exception ignore) { }
        };
        page.onRequest(onReq);
        try {
            page.evaluate("(t) => {" + JS
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>vis(x)&&norm(x.textContent||x.value).toLowerCase()===t.toLowerCase()); if(b) b.click(); }", buttonText);
            waitForAngular(1800);
        } finally { page.offRequest(onReq); }
        lastWrites = String.join(", ", posts);
        Object m = page.evaluate("() => {" + JS
                + " const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].filter(vis).map(e=>norm(e.textContent));"
                + " const inner=all.filter(t=>t); const uniq=[...new Set(inner.map(t=>t.replace(/^.{0,2}KPJ Portal/,'')))];"
                + " return uniq.join(' || '); }");
        return m == null ? "" : m.toString().trim();
    }

    /** Click Back on the add form and wait for the list. */
    public boolean clickBack() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].find(x=>vis(x)&&/^\\s*back\\s*$/i.test(norm(x.textContent))); if(b) b.click(); }");
        waitForAngular(1500);
        return onList();
    }

    /** Every visible menu entry — used to pin the real label when the menu click misses. */
    public String describeMenu() { return lastMenu; }
}
