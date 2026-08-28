package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Lab &gt; <b>Sample Accept/Reject</b> — Page Object.
 *
 * <p>A transactional screen, not a master: it searches orders over a date range and an MRN, lists them
 * in <b>Test Details</b>, and accepts a selected sample through a dialog.</p>
 *
 * <p>Every field is pinned to its exact ng-model, dumped from the live screen. Two of them are spelled
 * as the application spells them, not as the labels read: the suitability remark is
 * {@code samplesutabilityremark} (missing "i") and "accepted by" is {@code sampleaccptedbyid} (missing
 * "e"). A match on the label would find neither.</p>
 */
public class SampleAcceptReject extends BasePage {

    public SampleAcceptReject(Page page) { super(page); }

    /** Every model on this screen carries this prefix. */
    private static final String M = "SampleAcceptanceRejection.";

    public String lastBodyText = "", lastMenu = "", lastRoute = "", lastDates = "", lastMrnSearch = "",
            lastSearch = "", lastRows = "", lastTick = "", lastAccept = "", lastDialog = "",
            lastSave = "", lastPopupsSeen = "", lastMrnUsed = "";
    public boolean toastFromObserver = false;

    /** Shared JS helpers: visibility, text normalising, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    // ---- navigation ------------------------------------------------------

    /** Investigation &rarr; Lab &rarr; Sample Accept/Reject, taking the route from the menu link. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*investigation\\s*$");
            clickMenu("^\\s*lab\\s*$");
            lastMenu = describeMenu();
            // Anchored to the whole label; the Lab submenu also carries Sample Collection and several
            // other screens whose names share words with this one.
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*sample\\s*accept\\s*\\/?\\s*reject\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__sarMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            System.out.println("SampleAcceptReject.nav: the link was not in the menu on attempt " + attempt);
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__sarMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SampleAcceptReject.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__sarMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '/SampleAcceptanceRejection'; }"); }
            catch (Exception ignore) { }
            waitForAngular(4000);
        }
        waitForScreenControls(15000);
        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen();
    }

    /** Wait for this screen's OWN controls — its MRN box — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='"
                    + M + "MRNO']\")", null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("SampleAcceptReject.waitForScreenControls: no MRN box within " + timeoutMs + "ms");
            return false;
        }
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1500);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On this screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        return route.contains("sampleacceptancerejection");
    }

    // ---- search ----------------------------------------------------------

    /**
     * Enter the <b>From</b> and <b>To</b> dates.
     *
     * <p>Both are 720kb datepickers that arrive pre-filled, so each box is CLEARED before typing — these
     * pickers append to what is already there, and an unparseable result is silently discarded, leaving
     * the search running on a range nobody chose.</p>
     */
    public String enterDateRange(String from, String to) {
        String r1 = typeDate(M + "FromDate", from);
        String r2 = typeDate(M + "ToDate", to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("SampleAcceptReject: " + lastDates);
        return lastDates;
    }

    private String typeDate(String model, String value) {
        try {
            com.microsoft.playwright.Locator box = page.locator("input[ng-model='" + model + "']").first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            box.press("Escape");
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(500);
        Object back = page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return e? (e.value||'') : '(gone)'; }", model);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from)
                && lastDates.contains("ToDate=" + to);
    }

    /** Enter the <b>MRN</b> and click the search symbol beside it ({@code SearchPatientByMRNo}). */
    public String enterMrnAndClickSearchSymbol(String mrn) {
        Object r = page.evaluate("(m) => {" + JS
                + " const e=document.querySelector(\"input[ng-model='" + M + "MRNO']\");"
                + " if(!e) return '(no MRN box)';"
                + " const written=setEl(e,m);"
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click();"
                + " return 'MRN box holds \"'+written+'\"; the search symbol '"
                + "   +(b? 'was clicked [SearchPatientByMRNo]' : 'was NOT found'); }", mrn);
        lastMrnSearch = r == null ? "" : r.toString();
        lastMrnUsed = mrn;
        waitForAngular(2500);
        System.out.println("SampleAcceptReject: mrn -> " + lastMrnSearch);
        return lastMrnSearch;
    }

    public boolean mrnEntered(String mrn) {
        return lastMrnSearch != null && lastMrnSearch.contains("\"" + mrn + "\"")
                && lastMrnSearch.contains("was clicked");
    }

    /** Click <b>Search</b> ({@code fnSearchorderbooking}) and report how many rows came back. */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/fnSearchorderbooking/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(4500);
        lastSearch = "clicked Search [fnSearchorderbooking] -> " + rowCount() + " row(s) in Test Details";
        System.out.println("SampleAcceptReject: " + lastSearch);
        return lastSearch;
    }

    /** How many rows the Test Details grid is showing. */
    public int rowCount() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The rows themselves, so the report shows what was found rather than only a count. */
    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,3)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /**
     * Find an MRN that actually has samples in this window, by searching with the MRN box EMPTY and
     * reading one off the first row.
     *
     * <p>Used only when the requested MRN returns nothing: the flow then says which MRN it fell back to,
     * rather than reporting the screen as broken when the environment simply has no sample for that
     * patient.</p>
     */
    public String findMrnWithSamples(String from, String to) {
        // Emptying the MRN box is NOT enough: the search symbol beside it sets a PATIENT CONTEXT that
        // survives the box being cleared, so every later search stays scoped to that patient and comes
        // back empty. The screen's own Clear resets it - and takes the dates with it, so they go back in.
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/ClearsearchFilter/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        enterDateRange(from, to);
        clickSearch();

        Object mrn = page.evaluate("() => {" + JS
                + " const r=[...document.querySelectorAll('.ui-grid-row')].filter(vis)[0];"
                + " if(!r) return '';"
                + " const m=norm(r.textContent).match(/\\b1\\d{8}\\b/);"
                + " return m? m[0] : ''; }");
        String found = mrn == null ? "" : mrn.toString();
        System.out.println("SampleAcceptReject: MRN discovered from an unfiltered search -> "
                + (found.isEmpty() ? "(none)" : found));
        return found;
    }

    /** Which Sample Status filter the screen is searching under. */
    public String describeStatusCounts() {
        Object r = page.evaluate("() => {" + JS
                + " const rs=[...document.querySelectorAll("
                + "   \"input[ng-model='SampleAcceptanceRejection.SampleStatus']\")].filter(vis);"
                + " const i=rs.findIndex(x=>x.checked);"
                + " const v=(rs[i]||{}).value||'?';"
                + " return rs.length+' status options; in force: #'+i+' (value '+v+')'; }");
        String s2 = r == null ? "" : r.toString();
        // Value 1 is the screen's default, Not Accepted - which is why an empty grid means every
        // sample has already been accepted rather than that the search failed.
        return s2 + (s2.contains("(value 1)") ? " = Not Accepted, the screen's default" : "");
    }

    // ---- select and accept ------------------------------------------------

    /**
     * Click the tick symbol on the first row.
     *
     * <p>It is ui-grid's own selection button ({@code selectButtonClick}), not a checkbox — a search for
     * {@code input[type=checkbox]} finds nothing here. Selection is read back from the row's scope, so a
     * click that lands but does not select is not reported as success.</p>
     */
    public String tickFirstRow() {
        Object r = page.evaluate("() => {" + JS
                // Scoped to DATA rows: the same class is used by the header's select-all, so an empty
                // grid would otherwise report a tick that was never on a record.
                + " const t=[...document.querySelectorAll("
                + "   '.ui-grid-row .ui-grid-selection-row-header-buttons')].filter(vis)[0];"
                + " if(!t) return '(no tick symbol on any row - the grid has no records)';"
                + " const row=t.closest('.ui-grid-row');"
                + " const before=document.querySelectorAll('.ui-grid-row-selected').length;"
                + " t.click();"
                + " return JSON.stringify({before:before,"
                + "   text:(row? norm(row.textContent).slice(0,90) : '?')}); }");
        waitForAngular(1500);
        // Read the selection AFTER Angular has applied it: ui-grid marks the selected row with the
        // class ui-grid-row-selected. The row's scope is not reachable from the button in this build,
        // which is why the first version of this check could only report "(unreadable)".
        Object after = page.evaluate("() => {" + JS
                // Count ROWS, not marker elements: ui-grid renders its selection marker in more than
                // one container, so counting the markers reports six for a single selected row.
                + " const marked=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>/ui-grid-row-selected/.test(r.className)"
                + "     || r.querySelector('.ui-grid-row-selected')).length;"
                + " let scoped='(unreadable)';"
                + " try{ const g=angular.element(document.querySelector('[ui-grid]')).scope();"
                + "      const api=g && (g.gridApi || (g.grid && g.grid.api));"
                + "      if(api && api.selection) scoped=String(api.selection.getSelectedRows().length);"
                + " }catch(err){}"
                + " return marked+'|'+scoped; }");
        String[] parts = String.valueOf(after).split("\\|");
        int marked = 0;
        try { marked = Integer.parseInt(parts[0].trim()); } catch (Exception ignore) { }
        String selectedRows = parts.length > 1 ? parts[1] : "(unreadable)";
        String base = r == null ? "" : r.toString();
        String rowText = base.contains("\"text\"")
                ? base.replaceAll("(?s).*\"text\":\"(.*?)\".*", "$1") : base;
        lastTick = "clicked the tick on row 1 [selectButtonClick] -> " + marked
                + " row(s) now selected"
                + (selectedRows.equals("(unreadable)") ? ""
                   : "; the grid's selection API reports " + selectedRows + " selected")
                + "; the row reads: " + rowText;
        System.out.println("SampleAcceptReject: tick -> " + lastTick);
        return lastTick;
    }

    /**
     * A row really is selected.
     *
     * <p>Judged on the grid's own selection API where it can be read: ui-grid renders each logical row
     * in more than one container, so counting row elements reports two for a single selected record.
     * The element count is only the fallback.</p>
     */
    public boolean rowSelected() {
        if (lastTick == null) return false;
        java.util.regex.Matcher api = java.util.regex.Pattern
                .compile("selection API reports (\\d+) selected").matcher(lastTick);
        if (api.find()) return Integer.parseInt(api.group(1)) > 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("-> (\\d+) row\\(s\\) now selected").matcher(lastTick);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
    }

    /** Click <b>Accept</b> ({@code openAcceptRemark}) and confirm its dialog came up. */
    public String clickAccept() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/openAcceptRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        Object r = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')]"
                + "   .filter(vis).pop();"
                + " if(!d) return 'the Accept dialog did NOT open';"
                + " const save=[...d.querySelectorAll('button')]"
                + "   .some(b=>/addsampleacceptRemark/i.test(b.getAttribute('ng-click')||''));"
                + " return 'the Accept dialog opened: \"'+norm(d.textContent).slice(0,70)+'\"'"
                + "   +(save? ' (its Save is addsampleacceptRemark)' : ' (no Save button in it)'); }");
        lastAccept = r == null ? "" : r.toString();
        System.out.println("SampleAcceptReject: accept -> " + lastAccept);
        return lastAccept;
    }

    public boolean acceptDialogOpen() {
        return lastAccept != null && lastAccept.startsWith("the Accept dialog opened");
    }

    // ---- the dialog -------------------------------------------------------

    /**
     * Fill the dialog: <b>Acceptance Remark</b>, <b>Sample Suitability</b>, <b>Sample Suitability
     * Remark</b> and <b>Sample Accepted Date Time</b>.
     *
     * <p>The two remarks are separate models and are pinned individually — they are both textareas with
     * no label of their own, so a positional guess would swap them and both steps would still look
     * right. Note the application's own spelling: {@code samplesutabilityremark}.</p>
     */
    public String fillDialog(String remark, String suitabilityRemark, String dateTime) {
        Object r1 = page.evaluate("(v) => {" + JS
                + " const e=document.querySelector(\"[ng-model='" + M + "sampleacceptremark']\");"
                + " return e? setEl(e,v)+' [" + M + "sampleacceptremark]' : '(no Acceptance Remark box)'; }",
                remark);

        // Suitability: first real option, read back off the control.
        Object r2 = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + M + "samplesuitabilityid']\");"
                + " if(!e) return '(no Sample Suitability dropdown)';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(the Sample Suitability list has no real option)';"
                + " e.selectedIndex=reals[0].i;"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                + " return norm((e.options[e.selectedIndex]||{}).text)+' [" + M + "samplesuitabilityid, '"
                + "   +reals.length+' options]'; }");
        waitForAngular(600);

        Object r3 = page.evaluate("(v) => {" + JS
                + " const e=document.querySelector(\"[ng-model='" + M + "samplesutabilityremark']\");"
                + " return e? setEl(e,v)+' [" + M + "samplesutabilityremark]'"
                + "   : '(no Sample Suitability Remark box)'; }", suitabilityRemark);

        String r4 = typeDate(M + "sampleaccepteddatetime", dateTime);

        lastDialog = "AcceptanceRemark=" + r1 + " | Suitability=" + r2
                + " | SuitabilityRemark=" + r3 + " | AcceptedDateTime=" + r4;
        waitForAngular(600);
        System.out.println("SampleAcceptReject: dialog -> " + lastDialog);
        return lastDialog;
    }

    public boolean dialogFilled(String remark, String suitabilityRemark, String dateTime) {
        return lastDialog != null && !lastDialog.contains("(no ")
                && lastDialog.contains("AcceptanceRemark=" + remark)
                && lastDialog.contains("SuitabilityRemark=" + suitabilityRemark)
                && lastDialog.contains("AcceptedDateTime=" + dateTime);
    }

    /**
     * Click <b>Save</b> ({@code addsampleacceptRemark}) and return the toast.
     *
     * <p>Toasts on this application live about five seconds, so a mutation observer records them as they
     * appear rather than polling for them afterwards.</p>
     */
    public String saveAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove());"
                    + " window.__sarSeen = [];"
                    + " if(window.__sarObs) window.__sarObs.disconnect();"
                    + " window.__sarObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /toast|alert|jAlert|sweet|modal/i.test(cls+' '+n.id))"
                    + "       window.__sarSeen.push(t.slice(0,140)); } } });"
                    + " window.__sarObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/addsampleacceptRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Save button in the dialog)';"
                + " b.click(); return 'clicked Save [addsampleacceptRemark]'; }");
        waitForAngular(3000);

        String toast = "";
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            Object now = page.evaluate("() => {" + JS
                    + " const t=[...document.querySelectorAll('.toast-message,.toast')].filter(vis)"
                    + "   .map(x=>norm(x.textContent)).filter(x=>x);"
                    + " return t.length? t[0] : ''; }");
            toast = now == null ? "" : now.toString();
            if (toast.isEmpty()) page.waitForTimeout(400);
        }

        String seen = "";
        try {
            Object s = page.evaluate("() => { const a=window.__sarSeen||[];"
                    + " if(window.__sarObs) window.__sarObs.disconnect();"
                    + " return a.length? a.slice(0,4).join(' ;; ') : ''; }");
            seen = s == null ? "" : s.toString();
        } catch (Exception ignore) { }

        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[^A-Za-z0-9]*(?:KPJ Portal)?\\s*([A-Za-z][^;]{3,120})").matcher(seen);
            if (m.find()) { toast = m.group(1).trim(); toastFromObserver = true; }
        }
        // toastr prefixes its container title ("KPJ Portal") onto the text, and the title itself starts
        // with a non-printing character. Report the message the user actually reads.
        toast = toast.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastPopupsSeen = seen.isEmpty() ? "nothing was recorded appearing after Save" : "the page added: " + seen;
        lastSave = (clicked == null ? "" : clicked.toString()) + "; " + lastPopupsSeen;
        System.out.println("SampleAcceptReject: save -> " + lastSave + " toast=\"" + toast + "\"");
        return toast;
    }

    // ---- reject ----------------------------------------------------------

    public String lastReject = "", lastRejectDialog = "", lastRejectSave = "",
            lastReportUrl = "", lastReportDiagnostics = "", lastReportFile = "";
    public boolean reportBlank = false;
    public byte[] lastReportPng;

    /** How many statuses the screen offers and which one is in force — the search runs under it. */
    public String selectStatus(String want) {
        Object r = page.evaluate("(want) => {" + JS
                + " const rs=[...document.querySelectorAll("
                + "   \"input[ng-model='" + M + "SampleStatus']\")].filter(vis);"
                + " const labels=['Not Accepted','Accepted','Rejected','All'];"
                + " const i=labels.findIndex(l=>l.toLowerCase()===String(want).toLowerCase());"
                + " if(i<0 || !rs[i]) return '(no status option for '+want+')';"
                + " rs[i].click();"
                + " return 'Sample Status = '+labels[i]+' (radio #'+i+', value '+(rs[i].value||'?')+')'; }",
                want);
        waitForAngular(900);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Reject</b> ({@code openRejectRemark}) and confirm its dialog came up. */
    public String clickReject() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/openRejectRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        Object r = page.evaluate("() => {" + JS
                + " const d=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')]"
                + "   .filter(vis).pop();"
                + " if(!d) return 'the Reject dialog did NOT open';"
                + " const save=[...d.querySelectorAll('button')]"
                + "   .some(b=>/addsamplerejectRemark/i.test(b.getAttribute('ng-click')||''));"
                + " return 'the Reject dialog opened: \"'+norm(d.textContent).slice(0,70)+'\"'"
                + "   +(save? ' (its Save is addsamplerejectRemark)' : ' (no Save button in it)'); }");
        lastReject = r == null ? "" : r.toString();
        System.out.println("SampleAcceptReject: reject -> " + lastReject);
        return lastReject;
    }

    public boolean rejectDialogOpen() {
        return lastReject != null && lastReject.startsWith("the Reject dialog opened");
    }

    /**
     * Fill the rejection dialog: the <b>Rejection Reason</b> and the <b>Remark</b>.
     *
     * <p>Both are pinned by ng-model. The remark box is capped at 500 characters by the screen itself,
     * so what it reads back is what was stored, not what was typed.</p>
     */
    public String fillRejectDialog(String remark) {
        Object reason = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + M + "samplerejectionid']\");"
                + " if(!e) return '(no Rejection Reason dropdown)';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(the Rejection Reason list has no real option)';"
                + " e.selectedIndex=reals[0].i;"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                + " return norm((e.options[e.selectedIndex]||{}).text)+' [" + M + "samplerejectionid, '"
                + "   +reals.length+' reasons]'; }");
        waitForAngular(600);
        Object rem = page.evaluate("(v) => {" + JS
                + " const e=document.querySelector(\"[ng-model='" + M + "samplerejectremark']\");"
                + " return e? setEl(e,v)+' [" + M + "samplerejectremark]' : '(no Remark box)'; }", remark);
        lastRejectDialog = "RejectionReason=" + reason + " | Remark=" + rem;
        waitForAngular(500);
        System.out.println("SampleAcceptReject: reject dialog -> " + lastRejectDialog);
        return lastRejectDialog;
    }

    public boolean rejectDialogFilled(String remark) {
        return lastRejectDialog != null && !lastRejectDialog.contains("(no ")
                && !lastRejectDialog.contains("has no real option")
                && lastRejectDialog.contains("Remark=" + remark);
    }

    /** Click <b>Save</b> in the rejection dialog ({@code addsamplerejectRemark}) and return the toast. */
    public String saveRejectAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove());"
                    + " window.__sarSeen = [];"
                    + " if(window.__sarObs) window.__sarObs.disconnect();"
                    + " window.__sarObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /toast|alert|jAlert|sweet|modal/i.test(cls+' '+n.id))"
                    + "       window.__sarSeen.push(t.slice(0,140)); } } });"
                    + " window.__sarObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/addsamplerejectRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Save button in the rejection dialog)';"
                + " b.click(); return 'clicked Save [addsamplerejectRemark]'; }");
        waitForAngular(3000);

        String toast = "";
        long deadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            Object now = page.evaluate("() => {" + JS
                    + " const t=[...document.querySelectorAll('.toast-message,.toast')].filter(vis)"
                    + "   .map(x=>norm(x.textContent)).filter(x=>x);"
                    + " return t.length? t[0] : ''; }");
            toast = now == null ? "" : now.toString();
            if (toast.isEmpty()) page.waitForTimeout(400);
        }
        String seen = "";
        try {
            Object s2 = page.evaluate("() => { const a=window.__sarSeen||[];"
                    + " if(window.__sarObs) window.__sarObs.disconnect();"
                    + " return a.length? a.slice(0,4).join(' ;; ') : ''; }");
            seen = s2 == null ? "" : s2.toString();
        } catch (Exception ignore) { }
        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[^A-Za-z0-9]*(?:KPJ Portal)?\\s*([A-Za-z][^;]{3,120})").matcher(seen);
            if (m.find()) { toast = m.group(1).trim(); toastFromObserver = true; }
        }
        toast = toast.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastRejectSave = (clicked == null ? "" : clicked.toString())
                + "; " + (seen.isEmpty() ? "nothing was recorded appearing after Save"
                                         : "the page added: " + seen);
        System.out.println("SampleAcceptReject: reject save -> " + lastRejectSave + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Capture the sample rejection report the save is expected to generate.
     *
     * <p>Judged the same way as the certificates: the printed page is located inside whatever opened,
     * and the ink on it measured. A report that opens but comes out BLANK is a failure, not a pass —
     * a document with nothing on it is not a generated report.</p>
     */
    public String captureRejectionReport(int tabsBefore, int waitMs) {
        // Wait for whatever opened to actually LOAD. Captured too early, a report tab is still
        // about:blank and would be reported as an empty document when it simply had not arrived.
        String tabUrl = "";
        String contentType = "";
        com.microsoft.playwright.Page tab = null;
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            java.util.List<com.microsoft.playwright.Page> pages = page.context().pages();
            if (pages.size() > tabsBefore) {
                com.microsoft.playwright.Page t = pages.get(pages.size() - 1);
                try {
                    String u = t.url() == null ? "" : t.url();
                    if (!u.isEmpty() && !u.startsWith("about:")) {
                        tab = t;
                        tabUrl = u;
                        try { t.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD,
                                new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(8000)); }
                        catch (Exception ignore) { }
                        Object ct = t.evaluate("() => document.contentType || ''");
                        contentType = ct == null ? "" : ct.toString();
                        break;
                    }
                } catch (Exception ignore) { }
            }
            page.waitForTimeout(500);
        }

        if (tabUrl.isEmpty()) {
            reportBlank = true;
            lastReportUrl = "";
            lastReportDiagnostics = "no report tab ever opened after Save";
            System.out.println("SampleAcceptReject: rejection report -> " + lastReportDiagnostics);
            return lastReportDiagnostics;
        }
        lastReportUrl = tabUrl;

        // The report comes back as a PDF, which Chrome shows in its own viewer: the HTML around it is
        // empty by design, so a SCREENSHOT of that page is blank however good the report is. Judge the
        // PDF itself instead, fetched through the browser's own session so it carries the login.
        if (contentType.toLowerCase().contains("pdf") || tabUrl.toLowerCase().contains(".aspx")) {
            // Read the PDF from INSIDE the report tab. Fetching it from Java trips over the
            // environment's certificate; inside the tab the browser has already accepted it, and the
            // request carries the session automatically.
            String stats = "";
            try {
                Object r = tab.evaluate("async () => {"
                        + " const res = await fetch(location.href, {credentials:'include'});"
                        + " const buf = await res.arrayBuffer();"
                        + " const u8 = new Uint8Array(buf);"
                        + " let head=''; for(let i=0;i<Math.min(8,u8.length);i++)"
                        + "   head += String.fromCharCode(u8[i]);"
                        + " const text = new TextDecoder('latin1').decode(u8);"
                        + " const pages = (text.match(/\\/Type\\s*\\/Page[^s]/g)||[]).length;"
                        + " const streams = (text.match(/stream/g)||[]).length;"
                        + " return {len:u8.length, head:head, pages:pages, streams:streams,"
                        + "         status:res.status}; }");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
                int len = ((Number) m.get("len")).intValue();
                int pdfPages = ((Number) m.get("pages")).intValue();
                int streams = ((Number) m.get("streams")).intValue();
                String head = String.valueOf(m.get("head"));
                boolean isPdf = head.startsWith("%PDF");
                // A report with nothing on it is a few hundred bytes of scaffolding with no content
                // stream; a real one carries fonts and streams.
                boolean hasContent = isPdf && len > 3000 && streams > 0;
                reportBlank = !hasContent;
                stats = "the report is a PDF at " + tabUrl + " — HTTP " + m.get("status") + ", "
                        + len + " bytes, starts with " + (isPdf ? "%PDF" : "\"" + head.trim() + "\"")
                        + ", " + pdfPages + " page object(s), " + streams + " content stream(s). "
                        + (hasContent
                            ? "It carries content, so the report was generated with something on it."
                            : "It carries no content, so the report is empty.")
                        + " Judged on the PDF itself: a screenshot cannot see inside Chrome's PDF "
                        + "viewer, so the page around it is blank however good the report is.";
                lastReportDiagnostics = stats;
                System.out.println("SampleAcceptReject: rejection report -> " + lastReportDiagnostics);
                return lastReportDiagnostics;
            } catch (Exception e) {
                stats = "could not read the PDF from inside the tab: " + e.getMessage().split("\n")[0];
            }
            reportBlank = true;
            lastReportDiagnostics = "the report opened at " + tabUrl + " but its PDF could not be read ("
                    + stats + ")";
            System.out.println("SampleAcceptReject: rejection report -> " + lastReportDiagnostics);
            return lastReportDiagnostics;
        }

        // Not a PDF: judge the rendered page the way the certificates are judged.
        com.kpj.pages.ReportCheck rc = com.kpj.pages.ReportCheck.after(page, tabsBefore, 4000,
                "Investigation - Lab - Sample Rejection report.png");
        lastReportFile = rc.file;
        lastReportPng = rc.png;
        reportBlank = rc.blank;
        lastReportDiagnostics = rc.diagnostics;
        System.out.println("SampleAcceptReject: rejection report -> " + lastReportDiagnostics);
        return lastReportDiagnostics;
    }

    /** A success message, judged strictly — these screens phrase refusals with "Added" too. */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("accepted")
                || t.contains("updated");
    }

    /** Every visible control — dumped into the report so a changed screen is visible at a glance. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination|setNewYear"
                + "|paginateYears|crement/.test(click)) continue;"
                + "   if(/colFilter|pagination/.test(ng)) continue;"
                + "   if(e.tagName==='BUTTON'){ const t=norm(e.textContent)||e.value||'';"
                + "     if(click) out.push('BTN \"'+t.slice(0,30)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' ['+(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].join('\\n'); }");
        String s = r == null ? "" : r.toString();
        System.out.println("=== SampleAcceptReject CONTROLS ===\n" + s);
        return s;
    }
}
