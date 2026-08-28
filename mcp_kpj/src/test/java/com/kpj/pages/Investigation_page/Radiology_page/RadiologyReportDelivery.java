package com.kpj.pages.Investigation_page.Radiology_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Radiology &gt; <b>Radiology Report Delivery</b> — Page Object.
 *
 * <p>Flow: enter the <b>MRN</b> and click the search symbol beside it → click <b>Search</b> → enter the
 * <b>From Date</b> and <b>To Date</b> → click <b>Search</b> again → tick a record in <b>Test</b> → click
 * <b>Delivered</b>.</p>
 *
 * <p>Pinned from a live run: MRN is {@code ReportDelivery.MRNO}, its search icon is
 * {@code SearchPatientByMRNo()}, the dates are {@code ReportDelivery.FromDate}/{@code ToDate}, Search is
 * {@code fnGetAllReportDeliveryTestDetails()}, the Sub Group is {@code ReportDelivery.subgroupid}, and
 * Delivered is {@code opendeliveredpopup();fnSetDelivered();}. Each is matched on the pinned model/click
 * FIRST, with the original fuzzy match (ng-model tail, placeholder or label containing the obvious word)
 * kept only as a fallback in case a build renames something. {@link #describeControls()} still dumps
 * every visible control into the report for that reason.</p>
 *
 * <p>The Sub Group list reads as a single "--Select--" placeholder immediately after the screen renders
 * — it fills in asynchronously, the same as the sibling {@code AcceptRadiologyOrder} screen, which also
 * refuses to return anything without one chosen. {@link #selectSubGroupWithRecords()} waits for it, then
 * tries each real option in turn until one returns rows.</p>
 */
public class RadiologyReportDelivery extends BasePage {

    public RadiologyReportDelivery(Page page) { super(page); }

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastMrnSearch = "", lastSearch = "",
            lastDates = "", lastRows = "", lastTick = "", lastDelivered = "", lastSubGroup = "";

    /** Shared JS helpers: visibility, text normalising, ng-model tail, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
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

    /** Application menu &rarr; Investigation &rarr; Radiology &rarr; Radiology Report Delivery. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*investigation\\s*$");
            clickMenu("^\\s*radiology\\s*$");
            lastMenu = describeMenu();
            // Anchored to the whole label: this submenu also carries Result Entry / Result Entry
            // Authentication / Result Entry Admin Auth, whose names share words with this one.
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*radiology\\s*report\\s*delivery\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__rrdMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__rrdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("RadiologyReportDelivery.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__rrdMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3500);
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
        waitForAngular(1400);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On this screen — matched on the route the MENU discovered, since the real route is not pinned. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("reportdelivery") || route.contains("radiologyreport")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    // ---- diagnostics -------------------------------------------------------

    /** Dump every visible control with its ng-model — real selectors are pinned from this once run. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== RadiologyReportDelivery CONTROLS ===\n" + s);
        return s;
    }

    // ---- filters -------------------------------------------------------------

    public String lastOpdIpd = "", lastReportStatus = "";

    /**
     * Choose a radio in the <b>OPD/IPD/All</b> group ({@code ReportDelivery.OPDIPD}) or the
     * <b>Not Delivered/Delivered</b> group ({@code ReportDelivery.ReportUploaded}), by its visible label.
     *
     * <p>Not one of the requested steps — added because the Sub Group list stays empty (never populates)
     * with both groups left unset, on every MRN tried including a blank one. These two look like the
     * cascading filter that unlocks it.</p>
     */
    public String selectRadio(String ngModel, String wantLabelRegex) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [ng, re] = a;"
                + " const rx=new RegExp(re,'i');"
                + " const radios=[...document.querySelectorAll(\"input[type=radio][ng-model='\"+ng+\"']\")]"
                + "   .filter(vis);"
                + " const e=radios.find(x=>rx.test(labelOf(x)));"
                + " if(!e) return '(no matching radio for '+ng+' / '+re+' — options: '"
                + "   +radios.map(x=>labelOf(x)).join(', ')+')';"
                + " e.click();"
                + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(e.value); c.$render(); } }catch(err){}"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return 'selected \"'+labelOf(e)+'\" ['+ng+']'; }",
                java.util.List.of(ngModel, wantLabelRegex));
        String result = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("RadiologyReportDelivery: radio -> " + result);
        return result;
    }

    public String selectOpdIpd(String wantLabelRegex) {
        lastOpdIpd = selectRadio("ReportDelivery.OPDIPD", wantLabelRegex);
        return lastOpdIpd;
    }

    public String selectReportStatus(String wantLabelRegex) {
        lastReportStatus = selectRadio("ReportDelivery.ReportUploaded", wantLabelRegex);
        return lastReportStatus;
    }

    // ---- search ------------------------------------------------------------

    /**
     * Enter the <b>MRN</b> and click the search symbol beside it.
     *
     * <p>The box is found by fuzzy match — ng-model tail, placeholder or label containing "mrn" — and the
     * symbol is whichever visible icon/button sits right after it with a search-flavoured ng-click or
     * glyphicon.</p>
     */
    public String enterMrnAndClickSearchSymbol(String mrn) {
        Object r = page.evaluate("(m) => {" + JS
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>ngOf(x)==='ReportDelivery.MRNO')"
                + "   || boxes.find(x=>/mrn/i.test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '(no MRN box)';"
                + " const written=setEl(e,m);"
                + " const btn=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a,span,i')].filter(vis)"
                + "     .find(x=>{ const r1=e.getBoundingClientRect(), r2=x.getBoundingClientRect();"
                + "       const close=Math.abs(r1.top-r2.top)<40 && r2.left>=r1.left && r2.left<r1.right+120;"
                + "       const searchy=/search/i.test(x.getAttribute('ng-click')||'')"
                + "         || /glyphicon-search|fa-search/i.test(x.className||'');"
                + "       return close && searchy; });"
                + " if(btn) btn.click();"
                + " return 'MRN box holds \"'+written+'\"; the search symbol '"
                + "   +(btn? 'was clicked [ng-click='+(btn.getAttribute('ng-click')||'icon')+']' : 'was NOT found'); }",
                mrn);
        lastMrnSearch = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("RadiologyReportDelivery: mrn -> " + lastMrnSearch);
        return lastMrnSearch;
    }

    public boolean mrnEntered(String mrn) {
        return lastMrnSearch != null && lastMrnSearch.contains("\"" + mrn + "\"");
    }

    /** Click the main <b>Search</b> button and report how many rows came back. */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/fnGetAllReportDeliveryTestDetails/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a')].filter(vis)"
                + "     .find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        lastSearch = "clicked Search -> " + rowCount() + " row(s) in the test grid";
        System.out.println("RadiologyReportDelivery: " + lastSearch);
        return lastSearch;
    }

    /** How many real (non-placeholder) Sub Group options are currently loaded. */
    public int subGroupCount() {
        Object n = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='ReportDelivery.subgroupid']\");"
                + " if(!e) return 0;"
                + " return [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Select the Sub Group at the given (real-option) index. */
    public String selectSubGroup(int index) {
        Object r = page.evaluate("(i) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='ReportDelivery.subgroupid']\");"
                + " if(!e) return '(no Sub Group list)';"
                + " const reals=[...e.options].map((o,k)=>({o,k}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(the Sub Group list is empty)';"
                + " if(i>=reals.length) return '(only '+reals.length+' sub groups)';"
                + " e.selectedIndex=reals[i].k;"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                + " return norm((e.options[e.selectedIndex]||{}).text)+' ['+(i+1)+' of '+reals.length+']'; }",
                index);
        waitForAngular(1200);
        lastSubGroup = r == null ? "" : r.toString();
        System.out.println("RadiologyReportDelivery: sub group -> " + lastSubGroup);
        return lastSubGroup;
    }

    public boolean subGroupSelected() {
        return lastSubGroup != null && !lastSubGroup.startsWith("(");
    }

    /**
     * Select a Sub Group and search, trying each in turn until one returns records.
     *
     * <p>The list is waited for first — it renders as a single "--Select--" placeholder immediately
     * after the screen loads, and reading it once would report "empty" for a list still populating.</p>
     */
    public String selectSubGroupWithRecords() {
        long deadline = System.currentTimeMillis() + 12000;
        int count = subGroupCount();
        while (count == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(500);
            count = subGroupCount();
        }
        StringBuilder tried = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String name = selectSubGroup(i);
            clickSearch();
            int rows = rowCount();
            tried.append(tried.length() == 0 ? "" : ", ")
                 .append(name.split(" \\[")[0]).append(" -> ").append(rows);
            if (rows > 0) {
                lastSubGroup = name + " — it returns " + rows + " record(s). Tried: " + tried;
                System.out.println("RadiologyReportDelivery: sub group with records -> " + lastSubGroup);
                return lastSubGroup;
            }
        }
        lastSubGroup = count == 0 ? "(the Sub Group list never populated within 12s)"
                                  : "(none of the " + count + " sub groups returned a record. Tried: " + tried + ")";
        System.out.println("RadiologyReportDelivery: sub group -> " + lastSubGroup);
        return lastSubGroup;
    }

    public int rowCount() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /** Enter the <b>From</b> and <b>To</b> dates — both cleared before typing (they arrive pre-filled). */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(true, from);
        String r2 = typeDate(false, to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("RadiologyReportDelivery: " + lastDates);
        return lastDates;
    }

    private String typeDate(boolean from, String value) {
        String pinnedModel = from ? "ReportDelivery.FromDate" : "ReportDelivery.ToDate";
        String re = from ? "fromdate" : "todate";
        Object found = page.evaluate("(a) => {" + JS
                + " const [pinned, r] = a;"
                + " const boxes=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio');"
                + " const e=boxes.find(x=>ngOf(x)===pinned)"
                + "   || boxes.find(x=>new RegExp(r,'i').test(tail(x)+' '+(x.placeholder||'')+' '+labelOf(x)));"
                + " if(!e) return '';"
                + " if(!e.id) e.id='__rrd'+(r==='fromdate'?'From':'To');"
                + " return e.id; }", java.util.List.of(pinnedModel, re));
        String id = found == null ? "" : found.toString();
        if (id.isEmpty()) return "(no-field)";
        try {
            com.microsoft.playwright.Locator box = page.locator("#" + id).first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            box.press("Escape");
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(500);
        Object back = page.evaluate("(i) => { const e=document.getElementById(i);"
                + " return e? (e.value||'') : '(gone)'; }", id);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from) && lastDates.contains("ToDate=" + to);
    }

    // ---- select and deliver -------------------------------------------------

    /**
     * Tick the first record — a real checkbox if the grid has one (the usual Radiology pattern), else
     * ui-grid's own selection button (the Lab pattern).
     */
    public String tickFirstRecord() {
        Object r = page.evaluate("() => {" + JS
                + " const cb=[...document.querySelectorAll('.ui-grid-row input[type=checkbox]')].filter(vis)[0];"
                + " if(cb){ if(!cb.checked) cb.click();"
                + "   const row=cb.closest('.ui-grid-row');"
                + "   return 'ticked the checkbox ['+(cb.getAttribute('ng-model')||'?')+'] -> checked='"
                + "     +cb.checked+'; the row reads: '+(row? norm(row.textContent).slice(0,90) : '?'); }"
                + " const sb=[...document.querySelectorAll("
                + "   '.ui-grid-row .ui-grid-selection-row-header-buttons')].filter(vis)[0];"
                + " if(sb){ const row=sb.closest('.ui-grid-row');"
                + "   const text=row? norm(row.textContent).slice(0,90) : '?';"
                + "   sb.click();"
                + "   return 'clicked the tick [selectButtonClick]; the row reads: '+text; }"
                + " return '(no tick control on any record - the grid has no records)'; }");
        lastTick = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("RadiologyReportDelivery: tick -> " + lastTick);
        return lastTick;
    }

    public boolean recordTicked() {
        return lastTick != null && (lastTick.contains("checked=true") || lastTick.startsWith("clicked the tick"));
    }

    /**
     * Click <b>Delivered</b> and report whatever the screen said afterward (a toast, if any).
     *
     * <p>No success message is asserted — not requested — so this reports what happened for the record,
     * the way {@code Save}/{@code Submit} is reported on screens with no fixed confirmation text.</p>
     */
    public String clickDelivered() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(300);

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/fnSetDelivered/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "     .find(x=>/^\\s*delivered\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no Delivered button found)';"
                + " b.click(); return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        String clickedText = clicked == null ? "" : clicked.toString();
        waitForAngular(2000);
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 10000;
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
            if (toast.isEmpty()) page.waitForTimeout(400);
        }
        lastDelivered = clickedText + (toast.isEmpty() ? "; no message within 10s" : "; screen said: \"" + toast + "\"");
        System.out.println("RadiologyReportDelivery: delivered -> " + lastDelivered);
        return lastDelivered;
    }

    public boolean deliveredClicked() {
        return lastDelivered != null && lastDelivered.startsWith("clicked \"");
    }
}
