package com.kpj.pages.AncillaryServices_page.Equipment_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Equipment/Asset &gt; <b>Complaint Details</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>Complaint Details</b>
 * ({@code #/ComplaintDetails}, a search/list grid) → <b>New</b> ({@code AddComplaint()}) → the complaint
 * form ({@code #/addComplaintDetails}) → Location / Store → Equipment → Complaint By / Reported By /
 * Status → complaint text + Entry Date → Reported To / Verified By → Problem &amp; Reported dates +
 * Category → Problem &amp; Reported times → Down From date + time → <b>Add</b>
 * ({@code AddDetails(linklist)}) → <b>Save</b> ({@code IUDOperations()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06, all under {@code ComplaintDetails.}):</b> selects
 * {@code locationid}, {@code storeid}, {@code equipmentid}, {@code complainttype}, {@code employeeid},
 * {@code reportedby}, {@code reportedto}, {@code complaintstatus}, {@code verifiedby},
 * {@code complaintcategoryid}; text/date inputs {@code complaintno}, {@code entrydate},
 * {@code problemdate}, {@code reporteddate}, {@code expectedresolutiondate}, {@code downdate} (all
 * {@code type=text} with a {@code yyyy-MM-dd} placeholder); checkboxes {@code isemergency} and
 * {@code isdownfrom}; radios {@code Isinhouserbt}.</p>
 *
 * <h2>The trap on this screen: four time fields share one ng-model</h2>
 * <p>Problem time, Reported time, Expected-resolution time and Down time are ALL
 * {@code ng-model="inputTime"} — there is no way to tell them apart by model. They are distinguished by
 * DOM position: each follows its own date input. So {@link #setTimeAfter} finds the date field and walks
 * forward to the next {@code inputTime}, rather than matching on the model name (which would always hit
 * the first one and silently set the wrong field).</p>
 *
 * <p>The <b>Down</b> checkbox ({@code isdownfrom}) gates the down date/time, so it is ticked first.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class ComplaintDetails extends BasePage {

    public ComplaintDetails(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/ComplaintDetails";
    public static final String ADD_ROUTE = "#/addComplaintDetails";
    private static final String M = "ComplaintDetails.";

    public String lastControls = "";
    public String lastLocation = "", lastStore = "", lastEquipment = "";
    public String lastComplaintBy = "", lastReportedBy = "", lastStatus = "";
    public String lastComplaintText = "", lastEntryDate = "";
    public String lastReportedTo = "", lastVerifiedBy = "";
    public String lastProblemDate = "", lastReportedDate = "", lastCategory = "";
    public String lastProblemTime = "", lastReportedTime = "";
    public String lastDownDate = "", lastDownTime = "";

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
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            // tries defaults to 15 (6s). The store scan passes a smaller budget so 40 stores stay quick,
            // but it must still WAIT: these lists arrive asynchronously after the store changes.
            + "const pick=async(ng,tries)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<(tries||15);k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    private static final String ARM_TOASTS = ""
            + " window.__cdToasts=[]; if(window.__cdObs) window.__cdObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__cdToasts.includes(t)) window.__cdToasts.push(t); }); };"
            + " window.__cdObs=new MutationObserver(grab); window.__cdObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>Complaint Details</b>, else direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("ComplaintDetails.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/equipment\\s*\\/?\\s*asset/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^complaint\\s*details?$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/ComplaintDetails/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__cdMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__cdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ComplaintDetails.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__cdMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("ComplaintDetails.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddComplaint/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ComplaintDetails.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("complaintdetails") && !url.contains("add");
    }

    public boolean onComplaintForm() {
        if (page.url().toLowerCase().contains("addcomplaintdetails")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='" + M + "locationid']\")].find(e=>e.offsetParent!==null)"));
    }

    /** Click <b>New</b> ({@code AddComplaint()}); polled, as it renders after the grid. */
    public boolean clickNew() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/AddComplaint/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*new\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__cdNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ComplaintDetails.clickNew: New button not found"); return false; }
        try { page.locator("#__cdNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ComplaintDetails.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cdNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "locationid']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ComplaintDetails.clickNew: complaint form did not render"); }
        waitForAngular(1200);
        return onComplaintForm();
    }

    /** Diagnostics: selects, inputs AND textareas (the complaint text is a textarea, not an input). */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const ta=[...document.querySelectorAll('textarea')].filter(vis)"
                + "   .map(e=>'TEXTAREA \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " return sel.concat(ta).concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("ComplaintDetails controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: location + store ------------------------------------------

    /** Select <b>Location</b> then <b>Store</b> — Store and Equipment cascade from Location. */
    public String selectLocationAndStore() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const l=await pick('" + M + "locationid'); await sleep(1200);"
                + " const s=await pick('" + M + "storeid');"
                + " resolve('Location='+l+' | Store='+s); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("Location=")) lastLocation = t.substring(9).trim();
            else if (t.startsWith("Store=")) lastStore = t.substring(6).trim();
        }
        waitForAngular(1500);   // Store cascades into Equipment
        System.out.println("ComplaintDetails: " + out);
        return out;
    }

    public boolean locationAndStoreSelected() { return isReal(lastLocation) && isReal(lastStore); }

    // ---- step 2: equipment -------------------------------------------------

    /**
     * Select <b>Equipment</b> ({@code equipmentid}).
     *
     * <p>The equipment list cascades from Location/Store, and <b>most stores hold no equipment</b> — the
     * first store alphabetically ("Administration") yields an empty list, which then blocks Add. So when
     * the list is empty this walks the Store options until one populates Equipment, and updates
     * {@link #lastStore} to whichever store actually worked.</p>
     *
     * @param maxStoresToTry how many stores to try before giving up (there are ~97)
     */
    public String selectEquipment(int maxStoresToTry) {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "equipmentid')); })");
        lastEquipment = r == null ? "" : r.toString();
        if (isReal(lastEquipment)) {
            waitForAngular(800);
            System.out.println("ComplaintDetails: Equipment = " + lastEquipment);
            return lastEquipment;
        }

        System.out.println("ComplaintDetails: no equipment under store \"" + lastStore
                + "\" — scanning other stores");
        Object countObj = page.evaluate("() => {" + JS
                + " const e=byNg('" + M + "storeid'); return e? e.options.length : 0; }");
        int storeCount = countObj == null ? 0 : Integer.parseInt(countObj.toString());

        for (int i = 1; i < storeCount && i <= maxStoresToTry; i++) {
            Object storeName = page.evaluate("(i) => {" + JS
                    + " const e=byNg('" + M + "storeid'); if(!e||i>=e.options.length) return '';"
                    + " if(!real(e.options[i])) return '(skip)';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).trigger('change'); }catch(x){} }"
                    + " return norm(e.options[i].textContent); }", i);
            if (storeName == null || storeName.toString().isEmpty() || storeName.toString().equals("(skip)")) continue;
            waitForAngular(900);

            // Ask pick() to WAIT rather than testing the list once. The previous version resolved ''
            // the moment the list looked empty — about 900ms after the store changed — so a store whose
            // equipment simply had not arrived yet was recorded as having none. Forty stores checked
            // that way reported "no equipment anywhere", which is how a working screen looks broken.
            Object eq = page.evaluate("() => new Promise(async resolve => {" + JS
                    + " resolve(await pick('" + M + "equipmentid', 6)); })");
            if (eq != null && isReal(eq.toString())) {
                lastStore = storeName.toString();
                lastEquipment = eq.toString();
                waitForAngular(600);
                System.out.println("ComplaintDetails: store \"" + lastStore + "\" has equipment -> " + lastEquipment);
                return lastEquipment;
            }
        }
        System.out.println("ComplaintDetails: no store among the first " + maxStoresToTry + " has any equipment");
        lastEquipment = "(no-option after scanning " + maxStoresToTry + " stores)";
        return lastEquipment;
    }

    /** Select Equipment, scanning up to 40 stores for one that has any. */
    public String selectEquipment() { return selectEquipment(40); }

    /**
     * Diagnostic: report how many Equipment options each <b>Location</b> × <b>Complaint Type</b>
     * combination yields, to find what actually gates the equipment list. Read-only apart from the
     * selections it makes on the form.
     */
    public String diagnoseEquipmentAvailability() {
        StringBuilder sb = new StringBuilder();
        Object locCount = page.evaluate("() => {" + JS + " const e=byNg('" + M + "locationid'); return e? e.options.length : 0; }");
        Object typeCount = page.evaluate("() => {" + JS + " const e=byNg('" + M + "complainttype'); return e? e.options.length : 0; }");
        int locs = locCount == null ? 0 : Integer.parseInt(locCount.toString());
        int types = typeCount == null ? 0 : Integer.parseInt(typeCount.toString());

        for (int li = 1; li < locs; li++) {
            Object loc = page.evaluate("(i) => {" + JS
                    + " const e=byNg('" + M + "locationid'); if(!e||i>=e.options.length||!real(e.options[i])) return '';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " return norm(e.options[i].textContent); }", li);
            if (loc == null || loc.toString().isEmpty()) continue;
            waitForAngular(900);

            for (int ti = 1; ti < types; ti++) {
                Object typ = page.evaluate("(i) => {" + JS
                        + " const e=byNg('" + M + "complainttype'); if(!e||i>=e.options.length||!real(e.options[i])) return '';"
                        + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                        + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                        + " return norm(e.options[i].textContent); }", ti);
                if (typ == null || typ.toString().isEmpty()) continue;
                waitForAngular(900);

                Object counts = page.evaluate("() => {" + JS
                        + " const eq=byNg('" + M + "equipmentid'); const st=byNg('" + M + "storeid');"
                        + " const eqReal=eq? [...eq.options].filter(real).length : -1;"
                        + " const stReal=st? [...st.options].filter(real).length : -1;"
                        + " return 'equip='+eqReal+' stores='+stReal; }");
                sb.append("  location=").append(loc).append(" type=").append(typ)
                  .append(" -> ").append(counts).append('\n');
            }
        }
        String out = sb.toString();
        System.out.println("ComplaintDetails equipment availability:\n" + out);
        return out;
    }

    public boolean equipmentSelected() { return isReal(lastEquipment); }

    // ---- step 3: complaint by / reported by / status ------------------------

    /** Select <b>Complaint By</b> ({@code employeeid}), <b>Reported By</b> and <b>Status</b>. */
    public String selectComplaintByReportedByStatus() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const c=await pick('" + M + "employeeid'); await sleep(300);"
                + " const rb=await pick('" + M + "reportedby'); await sleep(300);"
                + " const st=await pick('" + M + "complaintstatus');"
                + " resolve('ComplaintBy='+c+' | ReportedBy='+rb+' | Status='+st); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("ComplaintBy=")) lastComplaintBy = t.substring(12).trim();
            else if (t.startsWith("ReportedBy=")) lastReportedBy = t.substring(11).trim();
            else if (t.startsWith("Status=")) lastStatus = t.substring(7).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintDetails: " + out);
        return out;
    }

    public boolean complaintByReportedByStatusSelected() {
        return isReal(lastComplaintBy) && isReal(lastReportedBy) && isReal(lastStatus);
    }

    // ---- step 4: complaint text + entry date -------------------------------

    /**
     * Enter the <b>complaint details</b> text and the <b>Entry Date</b>.
     *
     * <p>The complaint text is a {@code <textarea>}; it is located by ng-model/label naming complaint or
     * remark, falling back to the only textarea on the form.</p>
     *
     * @param text     complaint description
     * @param entryDate {@code yyyy-MM-dd}
     */
    public String enterComplaintDetailsAndEntryDate(String text, String entryDate) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [text, entryDate] = args;"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const tas=[...document.querySelectorAll('textarea')].filter(vis);"
                + " let ta=tas.find(x=>/complaint|detail|remark|description/i.test((x.getAttribute('ng-model')||'')+' '+labelOf(x)));"
                + " if(!ta) ta=tas[0];"
                + " const t=ta? setEl(ta, text) : '(no-textarea)';"
                + " const d=setInp('" + M + "entrydate', entryDate);"
                + " return 'ComplaintText='+t+' | EntryDate='+d; }",
                java.util.List.of(text, entryDate));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("ComplaintText=")) lastComplaintText = t.substring(14).trim();
            else if (t.startsWith("EntryDate=")) lastEntryDate = t.substring(10).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintDetails: " + out);
        return out;
    }

    public boolean complaintTextAndEntryDateSet() {
        return isReal(lastComplaintText) && isReal(lastEntryDate);
    }

    // ---- step 5: reported to + verified by ---------------------------------

    /** Select <b>Reported To</b> and <b>Verified By</b>. */
    public String selectReportedToAndVerifiedBy() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const rt=await pick('" + M + "reportedto'); await sleep(300);"
                + " const vb=await pick('" + M + "verifiedby');"
                + " resolve('ReportedTo='+rt+' | VerifiedBy='+vb); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("ReportedTo=")) lastReportedTo = t.substring(11).trim();
            else if (t.startsWith("VerifiedBy=")) lastVerifiedBy = t.substring(11).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintDetails: " + out);
        return out;
    }

    public boolean reportedToAndVerifiedBySelected() {
        return isReal(lastReportedTo) && isReal(lastVerifiedBy);
    }

    // ---- step 6: problem / reported dates + category ------------------------

    /** Set <b>Problem Date</b>, <b>Reported Date</b> ({@code yyyy-MM-dd}) and pick <b>Complaint Category</b>. */
    public String selectDatesAndCategory(String problemDate, String reportedDate) {
        Object r = page.evaluate("(args) => new Promise(async resolve => {" + JS
                + " const [pd, rd] = args;"
                + " const p=setInp('" + M + "problemdate', pd);"
                + " const q=setInp('" + M + "reporteddate', rd);"
                + " await sleep(300);"
                + " const c=await pick('" + M + "complaintcategoryid');"
                + " resolve('ProblemDate='+p+' | ReportedDate='+q+' | Category='+c); })",
                java.util.List.of(problemDate, reportedDate));
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("ProblemDate=")) lastProblemDate = t.substring(12).trim();
            else if (t.startsWith("ReportedDate=")) lastReportedDate = t.substring(13).trim();
            else if (t.startsWith("Category=")) lastCategory = t.substring(9).trim();
        }
        waitForAngular(500);
        System.out.println("ComplaintDetails: " + out);
        return out;
    }

    public boolean datesAndCategorySet() {
        return isReal(lastProblemDate) && isReal(lastReportedDate) && isReal(lastCategory);
    }

    // ---- step 7: problem + reported times ----------------------------------

    /**
     * Set the time field that FOLLOWS the given date field in DOM order.
     *
     * <p>All four time inputs on this form share {@code ng-model="inputTime"}, so they cannot be told
     * apart by model — targeting by model would always hit the first and quietly set the wrong field.
     * This walks the document-ordered input list from the date field to the next {@code inputTime}.</p>
     *
     * @param dateNgModel e.g. {@code ComplaintDetails.problemdate}
     * @param time        e.g. {@code 10:30}
     */
    public String setTimeAfter(String dateNgModel, String time) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [dateNg, time] = args;"
                + " const all=[...document.querySelectorAll('input')].filter(vis);"
                + " const di=all.findIndex(x=>(x.getAttribute('ng-model')||'')===dateNg);"
                + " if(di<0) return '(no-date-field)';"
                + " const t=all.slice(di+1).find(x=>(x.getAttribute('ng-model')||'')==='inputTime');"
                + " if(!t) return '(no-time-field)';"
                + " return setEl(t, time); }", java.util.List.of(dateNgModel, time));
        String out = r == null ? "" : r.toString();
        System.out.println("ComplaintDetails: time after " + dateNgModel + " = " + out);
        return out;
    }

    /** Set <b>Problem Time</b> and <b>Reported Time</b>. */
    public String selectProblemAndReportedTimes(String problemTime, String reportedTime) {
        lastProblemTime = setTimeAfter(M + "problemdate", problemTime);
        waitForAngular(300);
        lastReportedTime = setTimeAfter(M + "reporteddate", reportedTime);
        waitForAngular(300);
        return "ProblemTime=" + lastProblemTime + " | ReportedTime=" + lastReportedTime;
    }

    public boolean timesSet() { return isReal(lastProblemTime) && isReal(lastReportedTime); }

    // ---- step 8: down from -------------------------------------------------

    /**
     * Tick the <b>Down</b> checkbox ({@code isdownfrom}) — it gates the down fields — and set the
     * <b>Down Date</b>.
     */
    public String enterDownDate(String downDate) {
        Object r = page.evaluate("(d) => {" + JS
                + " const cb=byNg('" + M + "isdownfrom');"
                + " if(cb && !cb.checked){ cb.click(); }"
                + " return setInp('" + M + "downdate', d); }", downDate);
        lastDownDate = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("ComplaintDetails: DownDate = " + lastDownDate);
        return lastDownDate;
    }

    /** Set <b>Down Time</b> — the time field following the down date. */
    public String selectDownTime(String downTime) {
        lastDownTime = setTimeAfter(M + "downdate", downTime);
        waitForAngular(300);
        return lastDownTime;
    }

    public boolean downSet() { return isReal(lastDownDate) && isReal(lastDownTime); }

    // ---- step 9: add row ---------------------------------------------------

    /** Click <b>Add</b> ({@code AddDetails(linklist)}) and report the grid row delta. */
    public String clickAdd() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/AddDetails/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__cdAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("ComplaintDetails.clickAdd: Add button not found"); return "(no Add button)"; }
        try { page.locator("#__cdAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ComplaintDetails.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cdAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        String out = r == null ? "" : r.toString();
        System.out.println("ComplaintDetails: Add -> " + out);
        return out;
    }

    // ---- step 10: save -----------------------------------------------------

    /** Click <b>Save</b> ({@code IUDOperations()}) and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDOperations/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__cdSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("ComplaintDetails.save: Save button not found"); return ""; }

        try { page.locator("#__cdSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ComplaintDetails.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cdSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__cdToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__cdToasts||[]).includes(t)) (window.__cdToasts=window.__cdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cdToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("ComplaintDetails: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
