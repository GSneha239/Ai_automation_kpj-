package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Patient Requisition</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>Patient Requisition</b> (list, route {@code #/PatientRequisitionList}:
 * Required From / To date, Search, <b>New</b> and a grid Patient Req. No. | Requisition Date | Required Date | MRN | Patient Name |
 * Reg. Type | Component | Qty | Emergency | Crossmatched | Issued | Deny | Status | Edit) → <b>New</b> ({@code FnAddPatientRequisition();},
 * route {@code #/add-PatientRequisition}).</p>
 *
 * <p><b>The form is two-stage, like Blood Bag Opening Stock.</b> The patient is chosen first — here through the <b>patient search</b>
 * popup ({@code OpenPatientSearchPopupScreen()}): type a few digits of an MRN, Search, click a row's select icon
 * ({@code SetSearchPatient(PR)}); the MRN, name, gender, doctor … then fill in (read-only). The requisition line — <b>Component</b>,
 * <b>Required Date</b>, <b>Qty</b> (and Emergency / Remark) — is staged with the form's own <b>Add</b>, which moves it into the table
 * "List Of Blood Component Requisition" above the entry boxes (Component Description | Required Date | Qty | Emergency | Remark).
 * Only <b>Save</b> ({@code fnIUDPatientRequisition()}) sends it to the server.</p>
 *
 * <p><b>The list is filtered by REQUIRED date</b> (default today to today), so a requisition for tomorrow does not show until the
 * To date is moved. To tell the new row from older ones, the row count and the highest requisition number are recorded BEFORE the
 * form is opened ({@link #baseline}) and the new row must carry a higher number than that.</p>
 */
public class PatientRequisition extends BasePage {

    public PatientRequisition(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PatientRequisitionList";

    private static final String NG = "patientrequisition.";
    private static final String NEW = "button[ng-click='FnAddPatientRequisition();']";
    private static final String SAVE = "button[ng-click='fnIUDPatientRequisition()']";
    private static final String SEARCH = "[ng-click='fnGetRequisitionGridWithFilter()']";

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "", lastRoute = "", lastControls = "", lastNew = "", lastBaseline = "", lastPatient = "", lastLookup = "",
            lastEntry = "", lastStaged = "", lastToast = "", lastSaveDiagnostics = "", lastListCheck = "";
    public String lastMrn = "", lastPatientName = "", lastComponent = "", lastRequiredDate = "", lastQty = "", lastRemark = "", lastNo = "";
    /** Highest requisition number and row count seen BEFORE the new one was saved (same date range as the check). */
    public int baselineMaxNo = -1, baselineRows = -1;

    /** Shared JS helpers: visibility and text normalising. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * Ancillary Services &rarr; Blood Bank &rarr; Patient Requisition. Each level is opened only if it is closed (a second click on an
     * open entry CLOSES it) and the next one is looked up INSIDE its parent's {@code li}; the app bounces a typed hash back to the
     * dashboard, so the menu link is the only way in.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*ancillary\\s*services?\\s*$");
        expandMenuEntry("^\\s*ancillary\\s*services?\\s*$", "^\\s*blood\\s*bank\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*ancillary\\s*services?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bb=[...topLi.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(bb&&bb.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*patient\\s*requisition\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__prMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PatientRequisition.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__prMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onListScreen();
    }

    /** Every visible menu entry — used to pin the real label when the menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On the list screen: the route AND the screen's own New button must be there (a bounce to the dashboard has neither). */
    public boolean onListScreen() {
        if (!page.url().toLowerCase().endsWith("#/patientrequisitionlist")) return false;
        try {
            page.waitForFunction("() => { const b=document.querySelector(\"" + NEW + "\"); return !!b && !!(b.offsetWidth||b.offsetHeight||b.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return true;
    }

    /** On the form: its own MRN box is showing. */
    public boolean onForm() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                            + " && (e.getAttribute('ng-model')||'')==='" + NG + "mrno')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(!vis(e) && !(e.tagName==='SELECT' && ng)) continue;"
                + "   if(/colFilter|row\\.entity|pagination|grid\\.options|datepicker/i.test(ng+' '+click)) continue;"
                + "   if(/page(First|Previous|Next|Last)PageClick|openMic|EditRequisition|setDatepickerDay|prevMonth|nextMonth|showYearsPagination|FnEditComponentRequisition/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT [ng='+(ng||'?')+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' [ng='+(ng||'?')+'] type='+(e.type||'')+(e.disabled?' (read-only: filled from the patient)':'')); }"
                + " const hdr=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t);"
                + " if(hdr.length) out.push('GRID COLUMNS: '+hdr.join(' | '));"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== PatientRequisition CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    /**
     * Close the date calendars left open after a date is typed (this screen uses the {@code _720kb-datepicker}). The shared
     * {@link #dismissDatePicker()} walks up from the month header and stops at the first element whose class says "datepicker" — here
     * the header wrapper — so it hides only the header and leaves the day grid over the table (and in the screenshots). A click on the
     * page body is tried first, as a user would; whatever calendar is still showing is then hidden by its own container.
     */
    private void closeCalendars() {
        try {
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); if(document.body) document.body.click(); }");
            page.waitForTimeout(300);
            Object hid = page.evaluate("() => {" + JS
                    + " let n=0; for(const c of document.querySelectorAll(\"[class*='_720kb-datepicker-calendar']\")){"
                    + "   const box=c.closest('._720kb-datepicker-calendar')||c;"
                    + "   if(vis(box)){ box.style.setProperty('display','none','important'); n++; } }"
                    + " return n; }");
            if (hid instanceof Number && ((Number) hid).intValue() > 0) page.waitForTimeout(200);
        } catch (Exception e) { System.out.println("PatientRequisition.closeCalendars: " + String.valueOf(e.getMessage()).split("\n")[0]); }
    }

    // ---- list: baseline and range ---------------------------------------

    /**
     * Put the list's <b>Required To Date</b> on {@code requiredDate} and press <b>Search</b> (the list is filtered by REQUIRED date,
     * default today to today). The date is typed and the box left with Tab so the model takes it.
     */
    private void searchRange(String requiredDate) {
        try {
            Locator to = page.locator("#toDate");
            to.fill(requiredDate, new Locator.FillOptions().setTimeout(6000));
            to.press("Tab");
            waitForAngular(500);
            page.locator(SEARCH).first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("PatientRequisition.searchRange: " + e.getMessage().split("\n")[0]); }
        waitForAngular(3000);
    }

    /** One JS pass over the grid's raw data: row count, highest requisition number, and (optionally) the rows newer than a number. */
    private static final String LIST_READ = "(a) => {" + JS
            + " const g=document.querySelector('[ui-grid]'); const sc=g?window.angular.element(g).scope():null;"
            + " const data=(sc&&sc.gridOptions&&sc.gridOptions.data)||[];"
            + " const num=d=>{ const m=/(\\d+)\\s*$/.exec(d.patientrequisitionno||''); return m?parseInt(m[1],10):0; };"
            + " const max=data.reduce((n,d)=>Math.max(n,num(d)),0);"
            + " if(!a) return JSON.stringify({rows:data.length,max:max});"
            + " const same=(x,y)=>norm(x).toLowerCase()===norm(y).toLowerCase();"
            + " const fresh=data.filter(d=>num(d)>a.max);"
            + " const mine=fresh.filter(d=>String(d.mrno)===a.mrn && same(d.component,a.comp) && String(d.noofunit)===a.qty && same(d.requireddate1,a.date));"
            + " const row=mine[0];"
            + " return JSON.stringify({rows:data.length,max:max,fresh:fresh.length,mine:mine.length,"
            + "   no:row?row.patientrequisitionno:'', name:row?norm(row.patientname):'', type:row?row.patienttype:'', emergency:row?String(row.isemergency):'',"
            + "   firstFresh:fresh[0]?[fresh[0].patientrequisitionno,fresh[0].mrno,fresh[0].component,fresh[0].noofunit,fresh[0].requireddate1].join(' / '):''}); }";

    /**
     * Record how the list looks BEFORE anything is saved: Search with the To date moved to the date the requisition will be required,
     * then note the row count and the highest requisition number. Call it on the list screen, before {@link #clickNew}.
     */
    public String baseline(String requiredDate) {
        searchRange(requiredDate);
        Object r = page.evaluate(LIST_READ, (Object) null);
        String s = r == null ? "" : r.toString();
        baselineRows = intOf(s, "rows");
        baselineMaxNo = intOf(s, "max");
        lastBaseline = "list for Required date today to " + requiredDate + " holds " + baselineRows + " requisition(s); highest number "
                + baselineMaxNo;
        System.out.println("PatientRequisition: baseline -> " + lastBaseline);
        return lastBaseline;
    }

    private static int intOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":(-?\\d+)").matcher(json == null ? "" : json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String strOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : "";
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the list's <b>New</b> and wait for the form's MRN box. Lands on {@code #/add-PatientRequisition}. */
    public boolean clickNew() {
        try { page.locator(NEW).filter(new Locator.FilterOptions().setHasText("New")).first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { lastNew = "the New button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        boolean open = onForm();
        waitForAngular(1000);
        lastNew = open ? "New clicked; the form opened at " + page.url() : "New clicked but the form did not open (" + page.url() + ")";
        return open;
    }

    /** The form's <b>Back</b> ({@code CloseForm()}) — used to reach the list again without typing a hash (that raises "Reload page"). */
    public boolean clickBack() {
        try { page.locator("[ng-click='CloseForm()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        waitForAngular(1500);
        return onListScreen();
    }

    /**
     * Choose the patient by the screen's own <b>patient search</b> (the popup behind the second button beside the MRN box):
     * type a few digits of an MRN, click Search, click the select icon of the first row. The popup's search is a plain search — an
     * EMPTY one returns nothing — so each fragment is tried in turn until a patient attaches to the form (its MRN and first name fill
     * in). Returns a summary of the patient that attached, or why none could be picked.
     */
    public String selectPatientFromSearch(java.util.List<String> fragments) {
        StringBuilder tried = new StringBuilder();
        for (String frag : fragments) {
            try {
                boolean open = Boolean.TRUE.equals(page.evaluate("() => {" + JS
                        + " return [...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].some(vis); }"));
                if (!open) {
                    page.locator("[ng-click='OpenPatientSearchPopupScreen()']").first().click(new Locator.ClickOptions().setTimeout(6000));
                    waitForAngular(2500);
                }
                Object box = page.evaluate("() => {" + JS
                        + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0]||document;"
                        + " const i=[...dlg.querySelectorAll('input')].filter(vis).find(x=>/mrno?$/i.test(x.getAttribute('ng-model')||''));"
                        + " if(!i) return false; i.id='__prLkMrn'; return true; }");
                if (!Boolean.TRUE.equals(box)) { tried.append(frag).append(" -> no MRN box in the search popup; "); continue; }
                page.locator("#__prLkMrn").fill(frag, new Locator.FillOptions().setTimeout(5000));
                page.evaluate("() => { const e=document.getElementById('__prLkMrn'); if(e) e.removeAttribute('id'); }");
                Object sb = page.evaluate("() => {" + JS
                        + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0]||document;"
                        + " const b=[...dlg.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                        + "   .find(x=>/searchpatient\\s*\\(/i.test(x.getAttribute('ng-click')||'') || /^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||''));"
                        + " if(!b) return false; b.id='__prLkSearch'; return true; }");
                if (!Boolean.TRUE.equals(sb)) { tried.append(frag).append(" -> no Search button in the popup; "); continue; }
                page.locator("#__prLkSearch").click(new Locator.ClickOptions().setTimeout(5000));
                page.evaluate("() => { const e=document.getElementById('__prLkSearch'); if(e) e.removeAttribute('id'); }");
                try {
                    page.waitForFunction("() => [...document.querySelectorAll(\"[ng-click*='SetSearchPatient']\")].some(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)",
                            null, new Page.WaitForFunctionOptions().setTimeout(20000));
                } catch (Exception noRows) { tried.append(frag).append(" -> no rows; "); continue; }
                Object pick = page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll(\"[ng-click*='SetSearchPatient']\")].find(vis); if(!b) return null;"
                        + " b.id='__prLkPick'; const row=b.closest('tr,.ui-grid-row'); return row? norm(row.textContent).slice(0,110) : ''; }");
                if (pick == null) { tried.append(frag).append(" -> no row to pick; "); continue; }
                String rowText = String.valueOf(pick);
                page.locator("#__prLkPick").click(new Locator.ClickOptions().setTimeout(5000));
                page.evaluate("() => { const e=document.getElementById('__prLkPick'); if(e) e.removeAttribute('id'); }");
                waitForAngular(3500);

                String summary = readPatient();
                boolean ok = !lastMrn.isEmpty() && !lastPatientName.isEmpty();
                tried.append(frag).append(ok ? " -> ATTACHED (row: " + rowText + ")" : " -> picked \"" + rowText + "\" but no patient bound").append("; ");
                if (ok) {
                    lastLookup = "typed \"" + frag + "\" in the patient search, clicked Search, picked the first row (" + rowText + ")";
                    lastPatient = summary;
                    System.out.println("PatientRequisition: patient -> " + lastLookup + " => " + summary);
                    return summary;
                }
            } catch (Exception e) {
                tried.append(frag).append(" -> ").append(String.valueOf(e.getMessage()).split("\n")[0]).append("; ");
            }
        }
        lastLookup = "no patient could be picked from the patient search: " + tried;
        lastPatient = "";
        System.out.println("PatientRequisition: " + lastLookup);
        return "";
    }

    /** What the form holds for the patient now (also sets {@link #lastMrn} / {@link #lastPatientName}). */
    public String readPatient() {
        Object r = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"[ng-model='" + NG + "\"+n+\"']\"); if(!e) return '';"
                + "   if(e.tagName==='SELECT') return e.selectedIndex>0?norm(e.options[e.selectedIndex].textContent):''; return norm(e.value); };"
                + " const reg=[...document.querySelectorAll(\"input[ng-model='" + NG + "OPDIPD']\")].find(x=>x.checked);"
                + " const regLabel=reg?norm((reg.closest('label')||{}).textContent||reg.id):'';"
                + " return JSON.stringify({mrn:g('mrno'),name:[g('prefixid'),g('firstname'),g('middlename'),g('lastname')].filter(x=>x).join(' '),"
                + "   gender:g('genderid'),age:g('age'),doctor:g('doctorname'),dept:g('department'),reg:regLabel}); }");
        String s = r == null ? "" : r.toString();
        lastMrn = strOf(s, "mrn");
        lastPatientName = strOf(s, "name");
        return "MRN " + lastMrn + ", " + lastPatientName + ", gender " + strOf(s, "gender") + ", reg type "
                + (strOf(s, "reg").isEmpty() ? "(none ticked)" : strOf(s, "reg"))
                + (strOf(s, "doctor").isEmpty() ? "" : ", doctor " + strOf(s, "doctor"))
                + (strOf(s, "dept").isEmpty() ? "" : ", department " + strOf(s, "dept"));
    }

    /**
     * Enter the requisition line: <b>Component</b> (a clean entry whose name is unique in the list — the list carries leftovers such
     * as "Packed Red Blood Cells 011388" and two entries named "Packed Red Blood Cells"), <b>Required Date</b> (typed, then Tab so the
     * model takes it) and <b>Qty</b>, plus a stamped Remark.
     *
     * @param requiredDate the date to type, {@code dd/MM/yyyy}
     */
    public String fillRequisition(int attempt, String requiredDate) {
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        int seed = Integer.parseInt(stamp.substring(stamp.length() - 6)) / 7 + attempt;
        lastRequiredDate = requiredDate;
        lastQty = String.valueOf(1 + (seed % 4));
        lastRemark = "Auto test patient requisition " + stamp + (attempt > 0 ? String.valueOf(attempt) : "");

        lastComponent = pickComponent(seed);
        Locator date = page.locator("input[ng-model='" + NG + "requireddate']");
        date.fill(requiredDate);
        date.press("Tab");
        page.locator("input[ng-model='" + NG + "noofunit']").fill(lastQty);
        page.locator("input[ng-model='" + NG + "remark']").fill(lastRemark);
        waitForAngular(500);
        closeCalendars();                               // the typed date leaves its calendar open over the table
        lastEntry = "Component \"" + lastComponent + "\", Required Date \"" + lastRequiredDate + "\", Qty \"" + lastQty + "\", Remark \"" + lastRemark + "\"";
        System.out.println("PatientRequisition: entered -> " + lastEntry);
        return lastEntry;
    }

    private String pickComponent(int index) {
        Object r = page.evaluate("async ([ng, idx]) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '';"
                + " const real=o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent));"
                + " const clean=t=>!/\\d{4,}/.test(t) && !/\\b(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]{7,}\\b/.test(t) && !/^(new|test|test1|tested|testvasanth)$/i.test(t);"
                + " for(let k=0;k<20 && ![...e.options].some(real);k++) await new Promise(r=>setTimeout(r,400));"
                + " const nameCount=o=>[...e.options].filter(x=>norm(x.textContent)===norm(o.textContent)).length;"
                + " let opts=[...e.options].filter(o=>real(o) && clean(norm(o.textContent)) && nameCount(o)===1);"
                + " if(!opts.length) opts=[...e.options].filter(o=>real(o) && clean(norm(o.textContent)));"
                + " if(!opts.length) opts=[...e.options].filter(real);"
                + " if(!opts.length) return '';"
                + " const o=opts[((idx%opts.length)+opts.length)%opts.length];"
                + " e.selectedIndex=[...e.options].indexOf(o); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ window.angular.element(e).triggerHandler('change'); }catch(x){}"
                + " try{ window.jQuery(e).trigger('change'); }catch(x){}"
                + " await new Promise(r=>setTimeout(r,800));"
                + " return norm(o.textContent); }",
                java.util.Arrays.asList(NG + "componentid", index));
        return r == null ? "" : r.toString();
    }

    /** Read the entry boxes back: Component, Required Date and Qty must each hold what was entered. */
    public boolean detailsEntered() {
        Object r = page.evaluate("() => {" + JS
                + " const g=n=>{ const e=document.querySelector(\"[ng-model='" + NG + "\"+n+\"']\"); if(!e) return '(missing)';"
                + "   return e.tagName==='SELECT' ? (e.selectedIndex>0 ? norm(e.options[e.selectedIndex].textContent) : '') : norm(e.value); };"
                + " return JSON.stringify({comp:g('componentid'),date:g('requireddate'),qty:g('noofunit'),rem:g('remark')}); }");
        String s = r == null ? "" : r.toString();
        boolean ok = s.contains("\"comp\":\"" + lastComponent + "\"") && s.contains("\"date\":\"" + lastRequiredDate + "\"")
                && s.contains("\"qty\":\"" + lastQty + "\"") && s.contains("\"rem\":\"" + lastRemark + "\"") && !lastComponent.isEmpty();
        if (!ok) lastEntry += "  ||  READ BACK: " + s;
        return ok;
    }

    /** Installs a fresh toast collector. Must run BEFORE the click — toasts fade in about a second. */
    private void armToasts() {
        page.evaluate("() => {" + JS
                + " window.__prToasts=[]; if(window.__prObs) window.__prObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=norm(el.textContent); if(t && !window.__prToasts.includes(t)) window.__prToasts.push(t); }); };"
                + " window.__prObs=new MutationObserver(grab); window.__prObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /**
     * Click the form's <b>Add</b> and check the staged line. The line leaves the entry boxes and appears in the table "List Of Blood
     * Component Requisition" (Component Description | Required Date | Qty | Emergency | Remark) — every value is compared with what was
     * entered. A toast (if the screen raises one instead, e.g. a missing mandatory field) is reported.
     */
    public boolean addToList() {
        armToasts();
        try {
            page.locator(NEW).filter(new Locator.FilterOptions().setHasText("Add")).first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { lastStaged = "the form's Add button could not be clicked: " + e.getMessage().split("\n")[0]; return false; }
        waitForAngular(1500);
        Object r = page.evaluate("(a) => {" + JS
                + " const tabs=[...document.querySelectorAll('table')].filter(t=>/component\\s*description/i.test(norm(t.textContent)));"
                + " const rows=tabs.length?[...tabs[0].querySelectorAll('tbody tr')].map(r=>[...r.querySelectorAll('td')].map(d=>norm(d.textContent))).filter(c=>c.some(x=>x)):[];"
                + " const mine=rows.find(c=>c[0]===a.comp && c[1]===a.date && c[2]===a.qty && c[4]===a.rem);"
                + " const toast=(window.__prToasts||[]).join(' || ');"
                + " const comp=document.querySelector(\"[ng-model='" + NG + "componentid']\"); const qty=document.querySelector(\"[ng-model='" + NG + "noofunit']\");"
                + " const cleared=(comp&&comp.selectedIndex<=0)&&(qty&&!qty.value);"
                + " if(!mine) return 'NOT STAGED: the table holds '+rows.length+' line(s)'+(rows.length?' ['+rows.map(c=>c.slice(0,4).join(\"/\")).join('; ')+']':'')+(toast?' - message: '+toast:'');"
                + " return 'STAGED: Component=\"'+mine[0]+'\" | Required Date=\"'+mine[1]+'\" | Qty=\"'+mine[2]+'\" | Emergency=\"'+mine[3]+'\" | Remark=\"'+mine[4]+'\" | entry boxes cleared='+cleared; }",
                java.util.Map.of("comp", lastComponent, "date", lastRequiredDate, "qty", lastQty, "rem", lastRemark));
        lastStaged = r == null ? "" : r.toString();
        closeCalendars();                               // the form's Add leaves the Required Date calendar open over the staged table
        System.out.println("PatientRequisition: add -> " + lastStaged);
        return lastStaged.startsWith("STAGED:");
    }

    /**
     * A success message, judged strictly: these screens phrase refusals with words like "Already Added!" — so a plain
     * {@code contains("added")} would read a rejection as a save.
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ") || t.contains("fail") || t.contains("error")
                || t.contains("please") || t.contains("select") || t.contains("enter")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /**
     * Click <b>Save</b> ({@code fnIUDPatientRequisition()}) and return the toast. The server call is watched too, so a refusal without a
     * toast is still explained. Answers the "Do You Want To Save" dialog if one is raised.
     */
    public String saveAndGetToast() {
        final StringBuilder net = new StringBuilder();
        // Every API answer during the save is noted (the endpoint is not guessed): background polling is left out.
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            String u = r.url().toLowerCase();
            if (r.request().method().equals("POST") && u.contains("/api/") && !u.contains("getinternalmsgcount")
                    && !u.contains("admindashboard") && !u.contains("commondropdown")) {
                String body = "";
                try { body = r.text(); } catch (Exception ignore) { }
                if (net.length() > 0) net.append(" ; ");
                net.append(r.url().substring(u.indexOf("/api/"))).append(" answered HTTP ").append(r.status())
                        .append(body.isBlank() ? "" : " " + body.substring(0, Math.min(150, body.length())));
            }
        };
        page.onResponse(onResp);
        try {
            // The Required Date's calendar is left open by the form's Add; shut it so nothing sits over the form when Save is pressed.
            closeCalendars();
            armToasts();
            try { page.locator(SAVE).first().click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                lastSaveDiagnostics = "the Save button could not be clicked: " + e.getMessage().split("\n")[0];
                lastToast = "";
                return "";
            }
            long deadline = System.currentTimeMillis() + 20000;
            String toast = "";
            while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
                try { acceptSaveDialog(); } catch (Exception ignore) { }
                try {
                    Object now = page.evaluate("() => (window.__prToasts||[]).find(a=>/[a-z]/i.test(a)) || ''");
                    toast = now == null ? "" : now.toString();
                } catch (Exception ignore) { }
                if (toast.isEmpty()) page.waitForTimeout(500);
            }
            // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
            toast = toast.replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").replaceFirst("^\\s*\\W*KPJ Portal\\s*", "").trim();
            String why = "";
            if (toast.isEmpty()) {
                // No message at all: say what the screen was showing — a disabled Save, a validation prompt, a popup, an invalid field.
                try {
                    Object d = page.evaluate("() => {" + JS
                            + " const save=document.querySelector(\"" + SAVE + "\");"
                            + " const prompts=[...document.querySelectorAll('.formError,.formErrorContent,.alert,.jAlert,.modal.in,.sweet-alert,.bootbox')].filter(vis).map(x=>norm(x.textContent).slice(0,120));"
                            + " const bad=[...document.querySelectorAll('.ng-invalid')].filter(x=>vis(x)||x.tagName==='SELECT').map(x=>(x.getAttribute('ng-model')||x.id||x.tagName)).slice(0,8);"
                            + " return 'Save disabled='+(save?save.disabled:'(no button)')+'; prompts=['+prompts.join(' | ')+']; invalid fields=['+bad.join(', ')+']; url='+location.hash; }");
                    why = " | screen state: " + d;
                } catch (Exception ignore) { }
            }
            lastSaveDiagnostics = (toast.isEmpty() ? "Save clicked; no message within 20s" : "Save clicked")
                    + (net.length() > 0 ? " (" + net + ")" : " (no API call was seen after Save)") + why;
            lastToast = toast;
            System.out.println("PatientRequisition: save -> toast=\"" + toast + "\" " + lastSaveDiagnostics);
            waitForAngular(1500);
            return toast;
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
    }

    /**
     * Look the saved requisition up in the list. After a save the screen returns to the list by itself; otherwise the form's Back is
     * used. The list is filtered by REQUIRED date, so the To date is moved to the required date and <b>Search</b> pressed (as in
     * {@link #baseline}); the grid's data is then read: a row counts only when its requisition number is HIGHER than the highest seen
     * before the save AND its MRN, Component, Qty and Required Date equal what was entered. The grid is also filtered by the MRN
     * so the screenshot shows the row.
     */
    public boolean verifyInTable() {
        waitForAngular(1200);
        try {
            if (page.url().toLowerCase().contains("add-")) clickBack();
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')].some(r=>/\\S/.test(r.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        try {
            searchRange(lastRequiredDate);
            Object hit = page.evaluate(LIST_READ, java.util.Map.of("max", baselineMaxNo, "mrn", lastMrn, "comp", lastComponent, "qty", lastQty, "date", lastRequiredDate));
            String h = hit == null ? "" : hit.toString();
            int rows = intOf(h, "rows"), fresh = intOf(h, "fresh"), mine = intOf(h, "mine");
            lastNo = strOf(h, "no");
            // leave the grid showing the row: filter the MRN column
            page.evaluate("(c) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis);"
                    + " const f=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell'); return h && /^\\s*mrn\\b/i.test(norm(h.textContent)); });"
                    + " if(f){ f.focus(); f.value=c; try{ const ct=angular.element(f).controller('ngModel'); if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastMrn);
            waitForAngular(1500);
            closeCalendars();                           // the To date's calendar would sit over the grid in the screenshot
            String counts = "[list now holds " + rows + " requisition(s) for Required date today to " + lastRequiredDate + " (before the save: "
                    + baselineRows + "), " + fresh + " with a number above " + baselineMaxNo + "]";
            if (mine == 1) {
                lastListCheck = "FOUND: Patient Req. No. " + lastNo + " | MRN " + lastMrn + " | " + strOf(h, "name") + " | " + strOf(h, "type")
                        + " | Component " + lastComponent + " | Qty " + lastQty + " | Required Date " + lastRequiredDate + " | Emergency " + strOf(h, "emergency")
                        + "  " + counts;
            } else if (mine > 1) {
                lastListCheck = "LISTED " + mine + " TIMES: " + mine + " new rows match the entry (first: Patient Req. No. " + lastNo + ", MRN " + lastMrn
                        + ", " + lastComponent + ", Qty " + lastQty + ", " + lastRequiredDate + ") — one save must give one row  " + counts;
            } else if (fresh > 0) {
                lastListCheck = "a new requisition appeared (" + strOf(h, "firstFresh") + ") but it does not match the entry (MRN " + lastMrn + ", "
                        + lastComponent + ", Qty " + lastQty + ", " + lastRequiredDate + ")  " + counts;
            } else {
                lastListCheck = "no new requisition in the list  " + counts;
            }
        } catch (Exception e) {
            lastListCheck = "could not read the grid: " + e.getMessage().split("\n")[0];
        }
        System.out.println("PatientRequisition: list check -> " + lastListCheck);
        return lastListCheck.startsWith("FOUND:");
    }
}
