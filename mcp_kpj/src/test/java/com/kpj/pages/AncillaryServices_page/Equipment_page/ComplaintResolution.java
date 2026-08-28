package com.kpj.pages.AncillaryServices_page.Equipment_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Equipment/Asset &gt; <b>Complaint Resolution</b> — Page Object.
 *
 * <p>A search/filter screen ({@code #/complaintresolution}): set From/To dates, Location, Store,
 * Equipment Type, Equipment Code + Name, Complaint Category and Status, then <b>Search</b>
 * ({@code GetComplaintGrid()}) and read the result grid. Read-only — it saves nothing.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06, all under {@code ComplaintResolution.}):</b> selects
 * {@code locationid}, {@code storeid}, {@code equipmentid} (the Equipment Type list, 243 options),
 * {@code complaintcategoryid}, {@code statusid}, {@code employeeid}; text inputs {@code fromdate},
 * {@code todate}, {@code equipmentcode}, {@code equipmentname}; radios {@code Isinhouserbt} and an
 * "Over Due" checkbox. Buttons: {@code GetComplaintGrid()} (Search), plus {@code getAMCDetails()},
 * {@code getWarrantyDetails()}, {@code getInsuranceDetails()} and {@code getSALDetails()}.</p>
 *
 * <p><b>Note for the sibling Complaint Details screen.</b> Equipment master data plainly EXISTS — this
 * screen's Equipment list carries 243 options — yet Complaint Details renders an empty equipment list
 * under all 96 stores. That points at a broken cascade there rather than missing data.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class ComplaintResolution extends BasePage {

    public ComplaintResolution(Page page) { super(page); }

    public static final String ROUTE = "#/complaintresolution";
    private static final String M = "ComplaintResolution.";

    public String lastControls = "";
    public String lastFromDate = "", lastToDate = "";
    public String lastLocation = "", lastStore = "", lastEquipmentType = "";
    public String lastEquipmentCode = "", lastEquipmentName = "";
    public String lastCategory = "", lastStatus = "";
    public int lastRowCount = -1;
    public String lastSearchMessage = "";

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

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>Complaint Resolution</b>, else direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("ComplaintResolution.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/equipment\\s*\\/?\\s*asset/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^complaint\\s*resolution$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/complaintresolution/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__crMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__crMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ComplaintResolution.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__crMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("ComplaintResolution.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/GetComplaintGrid/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ComplaintResolution.nav: screen did not finish rendering"); }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("complaintresolution");
    }

    /** Diagnostics: every visible select (with option counts) and input. */
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
        System.out.println("ComplaintResolution controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: dates -----------------------------------------------------

    /** Enter <b>From Date</b> and <b>To Date</b> (the screen's own text format, e.g. {@code dd/MM/yyyy}). */
    public String enterDates(String fromDate, String toDate) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [from, to] = args;"
                + " return 'FromDate='+setInp('" + M + "fromdate', from)+' | ToDate='+setInp('" + M + "todate', to); }",
                java.util.List.of(fromDate, toDate));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("FromDate=")) lastFromDate = t.substring(9).trim();
            else if (t.startsWith("ToDate=")) lastToDate = t.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintResolution: " + out);
        return out;
    }

    public boolean datesSet() { return isReal(lastFromDate) && isReal(lastToDate); }

    // ---- step 2/3: location + store ---------------------------------------

    /** Select <b>Location</b>. */
    public String selectLocation() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "locationid')); })");
        lastLocation = r == null ? "" : r.toString();
        waitForAngular(1200);   // Store cascades from Location
        System.out.println("ComplaintResolution: Location = " + lastLocation);
        return lastLocation;
    }

    /** Select <b>Store</b> — polled, its list loads from the Location. */
    public String selectStore() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "storeid')); })");
        lastStore = r == null ? "" : r.toString();
        waitForAngular(900);
        System.out.println("ComplaintResolution: Store = " + lastStore);
        return lastStore;
    }

    // ---- step 4: equipment type -------------------------------------------

    /** Select <b>Equipment Type</b> ({@code equipmentid}) and return the chosen option text. */
    public String selectEquipmentType() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "equipmentid')); })");
        lastEquipmentType = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("ComplaintResolution: Equipment Type = " + lastEquipmentType);
        return lastEquipmentType;
    }

    // ---- step 5: equipment code + name -------------------------------------

    /**
     * Enter <b>Equipment Code</b> and <b>Equipment Name</b>.
     *
     * <p>Passing null for either derives it from the Equipment Type option already chosen — DevHIS renders
     * those as {@code CODE - NAME}, so the filters stay consistent with the selection instead of
     * over-constraining the search with an unrelated value.</p>
     */
    public String enterEquipmentCodeAndName(String code, String name) {
        String derivedCode = code, derivedName = name;
        if ((code == null || name == null) && lastEquipmentType != null && !lastEquipmentType.isEmpty()) {
            String[] parts = lastEquipmentType.split("\\s*-\\s*", 2);
            if (derivedCode == null) derivedCode = parts.length > 0 ? parts[0].trim() : "";
            if (derivedName == null) derivedName = parts.length > 1 ? parts[1].trim() : "";
        }
        if (derivedCode == null) derivedCode = "";
        if (derivedName == null) derivedName = "";

        Object r = page.evaluate("(args) => {" + JS
                + " const [code, name] = args;"
                + " return 'Code='+setInp('" + M + "equipmentcode', code)"
                + "   +' | Name='+setInp('" + M + "equipmentname', name); }",
                java.util.List.of(derivedCode, derivedName));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("Code=")) lastEquipmentCode = t.substring(5).trim();
            else if (t.startsWith("Name=")) lastEquipmentName = t.substring(5).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintResolution: " + out);
        return out;
    }

    // ---- step 6/7: category + status ---------------------------------------

    /** Select <b>Complaint Category</b>. */
    public String selectComplaintCategory() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "complaintcategoryid')); })");
        lastCategory = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("ComplaintResolution: Complaint Category = " + lastCategory);
        return lastCategory;
    }

    /** Select <b>Status</b>. */
    public String selectStatus() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "statusid')); })");
        lastStatus = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("ComplaintResolution: Status = " + lastStatus);
        return lastStatus;
    }

    // ---- step 8: search + verify -------------------------------------------

    /**
     * Click <b>Search</b> ({@code GetComplaintGrid()}) and report what came back.
     *
     * <p>Counts the result grid's backing rows off the Angular scope where possible (ui-grid virtualises
     * its DOM, so counting rendered rows under-reports), falling back to a DOM row count. Any toast is
     * captured too — "no records" is a legitimate response and is reported rather than hidden.</p>
     *
     * @return a summary such as {@code rows=12} or {@code rows=0 | msg=No Records Found}
     */
    public String clickSearchAndVerify() {
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__crToasts=[]; if(window.__crObs) window.__crObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__crToasts.includes(t)) window.__crToasts.push(t); }); };"
                + " window.__crObs=new MutationObserver(grab); window.__crObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/GetComplaintGrid/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__crSearch'; }");
        try { page.locator("#__crSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ComplaintResolution.search: Search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__crSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);

        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                // ui-grid virtualises rows, so prefer the scope's backing array over the rendered DOM.
                + " let best=-1;"
                + " document.querySelectorAll('*').forEach(el=>{ try{"
                + "   const s=window.angular.element(el).scope(); if(!s) return;"
                + "   const scan=a=>{ if(Array.isArray(a) && a.length>best && a.length && typeof a[0]==='object') best=a.length; };"
                + "   if(s.grid && s.grid.options && s.grid.options.data) scan(s.grid.options.data);"
                + " }catch(e){} });"
                + " const dom=document.querySelectorAll('.ui-grid-row, table tbody tr').length;"
                + " const rows = best>=0 ? best : dom;"
                + " const msg=(window.__crToasts||[]).join(' ');"
                + " return 'rows='+rows+' domRows='+dom+(msg?' | msg='+msg:''); }");
        String out = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("rows=(\\d+)").matcher(out);
        lastRowCount = m.find() ? Integer.parseInt(m.group(1)) : -1;
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("msg=(.*)$").matcher(out);
        lastSearchMessage = mm.find() ? mm.group(1).trim() : "";
        System.out.println("ComplaintResolution: Search -> " + out);
        return out;
    }

    /** True when the search returned at least one result row. */
    public boolean searchReturnedResults() { return lastRowCount > 0; }

    /**
     * Diagnostic: broaden the search and walk the Store list looking for ANY store that returns rows.
     *
     * <p>Runs when the specified filter combination finds nothing, to tell "my filters were too narrow"
     * apart from "this screen holds no complaints at all". Equipment code/name, equipment type and
     * category are cleared first, since those are the narrowing filters; <b>Store is kept</b> because the
     * screen rejects a search without one ("Please Select Store!"), which is why clearing everything is
     * not a valid comparison.</p>
     *
     * @param maxStores how many stores to try
     * @return a summary naming the first store that returned rows, or stating that none did
     */
    public String searchScanningStores(int maxStores) {
        // Clear the narrowing filters, keep dates + location + store.
        page.evaluate("() => {" + JS
                + " ['" + M + "equipmentcode','" + M + "equipmentname'].forEach(ng=>{ const e=byNg(ng);"
                + "   if(e){ const c=A.element(e).controller('ngModel'); e.value=''; if(c){ c.$setViewValue(''); c.$render(); }"
                + "     e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } });"
                + " ['" + M + "equipmentid','" + M + "complaintcategoryid'].forEach(ng=>{ const e=byNg(ng);"
                + "   if(e){ e.selectedIndex=0; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     try{ A.element(e).triggerHandler('change'); }catch(x){} } }); }");
        waitForAngular(900);

        Object countObj = page.evaluate("() => {" + JS
                + " const e=byNg('" + M + "storeid'); return e? e.options.length : 0; }");
        int storeCount = countObj == null ? 0 : Integer.parseInt(countObj.toString());
        int tried = 0;

        for (int i = 1; i < storeCount && tried < maxStores; i++) {
            Object storeName = page.evaluate("(i) => {" + JS
                    + " const e=byNg('" + M + "storeid'); if(!e||i>=e.options.length||!real(e.options[i])) return '';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " return norm(e.options[i].textContent); }", i);
            if (storeName == null || storeName.toString().isEmpty()) continue;
            tried++;
            waitForAngular(600);

            String res = clickSearchAndVerify();
            if (lastRowCount > 0) {
                String hit = "store \"" + storeName + "\" returned " + lastRowCount + " row(s)";
                System.out.println("ComplaintResolution: " + hit);
                return hit;
            }
            if (res.toLowerCase().contains("please select")) {
                return "search rejected: " + lastSearchMessage;
            }
        }
        String out = "no complaints found in any of the " + tried + " stores tried (dates "
                + lastFromDate + " - " + lastToDate + ")";
        System.out.println("ComplaintResolution: " + out);
        return out;
    }

    // ---- is a field actually required? -------------------------------------

    /** Set by {@link #isMandatory} — the label it judged on, so the report shows the evidence. */
    public String lastMandatoryCheck = "";

    /**
     * Is the field marked <b>mandatory</b> on screen?
     *
     * <p>This screen marks required fields with a red asterisk in the label (Location*, Store*). A
     * dropdown left on "-Select-" is only a failure when the screen demands a value — judging every empty
     * dropdown as a failure would report an optional filter, like Status, as a defect.</p>
     */
    public boolean isMandatory(String ngModel) {
        Object r = page.evaluate("(ng) => {" + JS
                + " const e=byNg(ng); if(!e) return '(no-field)';"
                + " let host=e, lbl=null;"
                + " for(let i=0;i<4 && host && !lbl;i++){ host=host.parentElement;"
                + "   if(host) lbl=host.querySelector('label'); }"
                + " if(!lbl && e.id) lbl=document.querySelector(\"label[for='\"+e.id+\"']\");"
                + " if(!lbl) return '(no label found)';"
                + " const txt=norm(lbl.textContent);"
                + " const star=/\\*/.test(txt)"
                + "   || !!lbl.querySelector('.text-danger,.required,span[style*=red],span[style*=Red]');"
                + " return (star? 'MANDATORY' : 'optional')+': label \"'+txt+'\"'; }", ngModel);
        lastMandatoryCheck = r == null ? "" : r.toString();
        System.out.println("ComplaintResolution: " + ngModel + " -> " + lastMandatoryCheck);
        return lastMandatoryCheck.startsWith("MANDATORY");
    }

    // ---- does any complaint data exist at all? -----------------------------

    /** Set by {@link #complaintsInMasterList()} — what the master list showed. */
    public String lastMasterListCheck = "";

    /**
     * How many complaints the sibling <b>Complaint Details</b> list holds.
     *
     * <p>This is what tells an empty search apart from a broken one. Complaint Details is where
     * complaints are created and listed: if it holds records this screen's Search cannot return, the
     * search is at fault; if it holds none, there is simply nothing stored to find yet.</p>
     *
     * @return the row count, or -1 if the list could not be read
     */
    public int complaintsInMasterList() {
        int rows = -1;
        try {
            page.evaluate("() => { window.location.hash = '#/complaintdetails'; }");
            waitForAngular(5000);
            Object r = page.evaluate("() => {"
                    + " let best=-1;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{"
                    + "   const s=window.angular.element(el).scope(); if(!s) return;"
                    + "   const a=s.grid && s.grid.options && s.grid.options.data;"
                    + "   if(Array.isArray(a) && a.length>best) best=a.length;"
                    + " }catch(e){} });"
                    + " const dom=[...document.querySelectorAll('.ui-grid-row')]"
                    + "   .filter(e=>e.offsetParent!==null).length;"
                    + " return (best>=0? best : dom)+'|'+dom+'|'+location.hash; }");
            String s = r == null ? "" : r.toString();
            String[] parts = s.split("\\|");
            if (parts.length > 0) try { rows = Integer.parseInt(parts[0].trim()); } catch (Exception ignore) { }
            lastMasterListCheck = "the Complaint Details list (" + (parts.length > 2 ? parts[2] : "?")
                    + ") holds " + rows + " complaint row(s)"
                    + (parts.length > 1 ? ", " + parts[1] + " rendered" : "");
        } catch (Exception e) {
            lastMasterListCheck = "could not read the Complaint Details list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("ComplaintResolution: " + lastMasterListCheck);
        return rows;
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
