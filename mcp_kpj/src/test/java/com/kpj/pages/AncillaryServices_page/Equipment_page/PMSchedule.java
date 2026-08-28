package com.kpj.pages.AncillaryServices_page.Equipment_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Equipment/Asset &gt; <b>PM Schedule</b> (Preventive Maintenance) — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>PM Schedule</b>
 * ({@code #/PreventiveMaintenanceSchedule}, a list grid) → <b>Add</b> → the schedule form
 * ({@code #/add-PreventiveMaintenanceSchedule}) → select <b>Store</b> → enter <b>Year</b> →
 * <b>Submit</b> ({@code fnIUDPreventiveMaintenanceSchedule()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06):</b> {@code Store.StoreID} (select, 97 options) and
 * {@code Store.Year} (text). Note the model prefix is {@code Store.}, not the screen's own name.
 * Buttons: {@code fnIUDPreventiveMaintenanceSchedule()} (Submit), {@code closeForm()} (Back).</p>
 *
 * <p><b>Add is not a {@code <button>}</b> on the list screen — it is an anchor/icon, so the lookup spans
 * {@code a}/{@code i}/{@code span} too, and is polled because it renders after the grid.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class PMSchedule extends BasePage {

    public PMSchedule(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PreventiveMaintenanceSchedule";
    public static final String ADD_ROUTE = "#/add-PreventiveMaintenanceSchedule";

    public String lastControls = "";
    public String lastStore = "", lastYear = "";
    /** Stores tried before one was accepted (a schedule already exists for some store/year pairs). */
    public int storesTried = 0;

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pick=async(ng)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    private static final String ARM_TOASTS = ""
            + " window.__pmToasts=[]; if(window.__pmObs) window.__pmObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__pmToasts.includes(t)) window.__pmToasts.push(t); }); };"
            + " window.__pmObs=new MutationObserver(grab); window.__pmObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>PM Schedule</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("PMSchedule.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/equipment\\s*\\/?\\s*asset/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^pm\\s*schedule$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/PreventiveMaintenanceSchedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__pmMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__pmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PMSchedule.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pmMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("PMSchedule.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(1200);
        return onListScreen();
    }

    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("preventivemaintenanceschedule") && !url.contains("add-");
    }

    public boolean onScheduleForm() {
        if (page.url().toLowerCase().contains("add-preventivemaintenanceschedule")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='Store.Year']\")].find(e=>e.offsetParent!==null)"));
    }

    /**
     * Click <b>Add</b> and wait for the form.
     *
     * <p>Add is an anchor/icon here rather than a {@code <button>}, so the search spans {@code a}, {@code i}
     * and {@code span} as well, and is polled because it renders after the grid finishes loading.</p>
     */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                    + "   .filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/add/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/fa-plus|glyphicon-plus/i.test(x.className||''));"
                    + " if(!b) return false; b.id='__pmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PMSchedule.clickAdd: Add control not found"); return false; }
        try { page.locator("#__pmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PMSchedule.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='Store.Year']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("PMSchedule.clickAdd: schedule form did not render"); }
        waitForAngular(1000);
        return onScheduleForm();
    }

    /** Diagnostics: visible selects (with option counts) and inputs. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " return sel.concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("PMSchedule controls: " + lastControls);
        return lastControls;
    }

    // ---- store + year ------------------------------------------------------

    /** Select <b>Store</b> ({@code Store.StoreID}) — the first real option. */
    public String selectStore() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('Store.StoreID')); })");
        lastStore = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("PMSchedule: Store = " + lastStore);
        return lastStore;
    }

    /** Select the store at the given option index; returns its text ("" when the index is unusable). */
    private String selectStoreByIndex(int index) {
        Object r = page.evaluate("(i) => {" + JS
                + " const e=byNg('Store.StoreID'); if(!e||i>=e.options.length||!real(e.options[i])) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                + " if($){ try{ $(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[i].textContent); }", index);
        return r == null ? "" : r.toString();
    }

    /**
     * Enter the <b>Year</b> ({@code Store.Year}).
     *
     * <p>The field is backed by a datepicker, which typing alone leaves open and uncommitted. So after
     * writing the value this commits it — pressing Enter, then blurring, then dismissing any datepicker
     * still on screen — and waits for the schedule rows the year is supposed to generate.</p>
     */
    public String enterYear(String year) {
        Object r = page.evaluate("(y) => {" + JS + " return setInp('Store.Year', y); }", year);
        lastYear = r == null ? "" : r.toString();
        waitForAngular(400);

        // Commit the datepicker by blurring and dismissing it.
        //
        // Deliberately NOT pressing Enter: on this field Enter submits/navigates the form, and repeating
        // that once per store during a scan reliably killed the page ("TargetClosedError: Target page,
        // context or browser has been closed") on two consecutive runs. The value is already in the model
        // via $setViewValue, so the keypress bought nothing and cost the whole run.
        try {
            page.locator("[ng-model='Store.Year']").first().blur();
        } catch (Exception e) { System.out.println("PMSchedule.enterYear: blur failed - " + e.getMessage()); }
        page.evaluate("() => { const dp=[...document.querySelectorAll('[ng-click*=setDatepickerDay]')]"
                + ".find(e=>e.offsetParent!==null); if(dp){ document.body.click(); } }");
        waitForAngular(600);

        // The schedule rows (if any) are generated off store+year — give them a moment to arrive.
        // Kept short: this runs once per store during a store scan, so a long timeout here dominates
        // the whole run (an 8s wait × 15 stores is two minutes of pure waiting).
        try {
            page.waitForFunction("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(2000));
        } catch (Exception ignore) { }

        Object confirmed = page.evaluate("() => {" + JS
                + " const e=byNg('Store.Year'); return e? norm(e.value) : ''; }");
        String shown = confirmed == null ? "" : confirmed.toString();
        if (!shown.isEmpty()) lastYear = shown;
        System.out.println("PMSchedule: Year = " + lastYear);
        return lastYear;
    }

    public boolean storeAndYearSet() { return isReal(lastStore) && isReal(lastYear); }

    /**
     * Diagnostic: what the form offers to "select" as a schedule — grid rows, checkboxes and any
     * clickable carrying an ng-click. Submit rejects with "Please Select Schedule!" when nothing here
     * can be picked.
     */
    public String describeScheduleSelection() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const gridRows=document.querySelectorAll('.ui-grid-row').length;"
                + " const tableRows=document.querySelectorAll('table tbody tr').length;"
                + " const boxes=[...document.querySelectorAll('input[type=checkbox]')].filter(vis).length;"
                + " const radios=[...document.querySelectorAll('input[type=radio]')].filter(vis).length;"
                + " const clicks=[...document.querySelectorAll('[ng-click]')].filter(vis)"
                + "   .map(e=>e.tagName+':'+norm(e.textContent).slice(0,18)+'['+e.getAttribute('ng-click')+']').slice(0,12);"
                + " const bodyHasNoRecords=/no\\s*record|no\\s*data/i.test(document.body.innerText||'');"
                + " return 'gridRows='+gridRows+' tableRows='+tableRows+' checkboxes='+boxes+' radios='+radios"
                + "   +' noRecordsText='+bodyHasNoRecords+' | clickables: '+clicks.join(' , '); }");
        String out = r == null ? "" : r.toString();
        System.out.println("PMSchedule schedule-selection: " + out);
        return out;
    }

    /**
     * Click the form's own <b>Add</b> ({@code AddPreventiveMaintenanceSchedule()}), which generates the
     * schedule line for the chosen Store + Year.
     *
     * <p>This is a SECOND Add, distinct from the list screen's: it only appears once the Year has
     * committed, and without it Submit rejects with "Please Select Schedule!" because there is no
     * schedule row to save.</p>
     *
     * @return a summary of the row delta, e.g. {@code rowsAdded=1 total=1}
     */
    public String clickAddSchedule() {
        Object before = page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/AddPreventiveMaintenanceSchedule/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__pmAddSch'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PMSchedule.clickAddSchedule: form Add not found"); return "(no Add on the form)"; }
        try { page.locator("#__pmAddSch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PMSchedule.clickAddSchedule: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmAddSch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('.ui-grid-row, table tbody tr').length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        String out = r == null ? "" : r.toString();
        System.out.println("PMSchedule: form Add -> " + out);
        return out;
    }

    /**
     * Walk the Store list until one whose <b>Add</b> actually generates a schedule row.
     *
     * <p>A store with no equipment produces no schedule line — Add succeeds silently with zero rows, and
     * Submit then rejects with "Please Select Schedule!". Rather than fail on whichever store happens to
     * be first alphabetically, this finds a store that has something to schedule and leaves it selected.</p>
     *
     * @return the row summary for the store that worked, or a statement that none did
     */
    public String addScheduleScanningStores(String year, int maxStores) {
        storesTried = 0;

        // Re-read the option count each iteration: reading it once under-scans if the list is re-rendered
        // mid-scan (observed stopping after 5 of 30 because the count was captured from a stale list).
        for (int i = 1; storesTried < maxStores; i++) {
            Object countObj = page.evaluate("() => {" + JS
                    + " const e=byNg('Store.StoreID'); return e? e.options.length : 0; }");
            int storeCount = countObj == null ? 0 : Integer.parseInt(countObj.toString());
            if (storeCount == 0) {
                // The Store select is gone — the form navigated away mid-scan, so the remaining stores
                // were never tried. Say so rather than reporting a clean "exhausted".
                System.out.println("PMSchedule: the form was LOST after " + storesTried
                        + " store(s) — the Store select no longer exists, so the scan stopped early");
                break;
            }
            if (i >= storeCount) {
                System.out.println("PMSchedule: store list exhausted at index " + i + " of " + storeCount);
                break;
            }
            String store = selectStoreByIndex(i);
            if (store.isEmpty()) continue;
            storesTried++;
            lastStore = store;
            waitForAngular(500);
            enterYear(year);

            String res = clickAddSchedule();
            if (res != null && res.startsWith("rowsAdded=") && !res.startsWith("rowsAdded=0")) {
                System.out.println("PMSchedule: store \"" + store + "\" produced a schedule -> " + res);
                return res;
            }
        }
        String out = "no schedule row could be generated in any of the " + storesTried
                + " stores tried (year " + year + ")";
        System.out.println("PMSchedule: " + out);
        return out;
    }

    // ---- submit ------------------------------------------------------------

    /** Click <b>Submit</b> ({@code fnIUDPreventiveMaintenanceSchedule()}) and return the toast. */
    public String submitAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnIUDPreventiveMaintenanceSchedule/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__pmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PMSchedule.submit: Submit button not found"); return ""; }

        try { page.locator("#__pmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PMSchedule.submit: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pmSubmit'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__pmToasts||[]).some(a=>/saved|success|added|updated|generated|please|fill|enter|select|required|exist|already|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__pmToasts||[]).includes(t)) (window.__pmToasts=window.__pmToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__pmToasts||[];"
                + " return a.find(x=>/(saved|added|updated|generated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Submit, and if the store/year pair is rejected as already scheduled, move to the next store and try
     * again.
     *
     * <p>A PM schedule is one per store per year, so a fixed store makes this flow pass once and fail on
     * every re-run. Only "already exists"-style rejections trigger the retry; any other error is returned
     * as-is so genuine failures are not masked by churning through stores.</p>
     *
     * @param year        the year being scheduled
     * @param maxStores   how many stores to try
     * @return the toast from the first accepted submit, or the last rejection
     */
    public String submitWithStoreFallback(String year, int maxStores) {
        String toast = submitAndGetToast();
        storesTried = 1;
        if (isSuccess(toast) || !isAlreadyScheduled(toast)) return toast;

        System.out.println("PMSchedule: store \"" + lastStore + "\" already has a " + year
                + " schedule — trying other stores");
        for (int i = 2; i <= maxStores; i++) {
            if (!onScheduleForm()) break;
            String store = selectStoreByIndex(i);
            if (store.isEmpty()) continue;
            storesTried++;
            lastStore = store;
            waitForAngular(500);
            enterYear(year);
            toast = submitAndGetToast();
            if (isSuccess(toast)) {
                System.out.println("PMSchedule: accepted for store \"" + store + "\"");
                return toast;
            }
            if (!isAlreadyScheduled(toast)) return toast;   // a different error — report it, don't churn
        }
        return toast;
    }

    private static boolean isSuccess(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return t.contains("success") || t.contains("saved") || t.contains("generated") || t.contains("added");
    }

    private static boolean isAlreadyScheduled(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return t.contains("already") || t.contains("exist");
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("PMSchedule: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
