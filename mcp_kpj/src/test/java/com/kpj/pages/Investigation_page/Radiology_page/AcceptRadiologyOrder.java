package com.kpj.pages.Investigation_page.Radiology_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Radiology &gt; <b>Accept Radiology Order</b> — Page Object.
 *
 * <p>Accepting walks a chain: tick a record → <b>Accept</b> → a confirmation → the <b>Mark Visit</b>
 * dialog → <b>Mark Visit</b> → the <b>Acceptance Remark</b> dialog → <b>OK</b>.</p>
 *
 * <p>Unlike the Lab screens, this grid has a real <b>Select</b> column with a checkbox, not ui-grid's
 * selection button.</p>
 */
public class AcceptRadiologyOrder extends BasePage {

    public AcceptRadiologyOrder(Page page) { super(page); }

    private static final String M = "RadiologyAcceptanceRejection.";

    public String lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "", lastRows = "",
            lastTick = "", lastAccept = "", lastConfirm = "", lastMarkVisit = "", lastMarkVisitSave = "",
            lastRemarkDialog = "", lastSave = "", lastDialogs = "";
    public boolean toastFromObserver = false;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const topDialog=()=>[...document.querySelectorAll("
          + "  '.modal,.modal-content,[role=dialog],.sweet-alert')].filter(vis).pop();"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    /** Record and accept native dialogs, so a window.confirm cannot stall the run unseen. */
    public void captureDialogs() {
        page.onDialog(d -> {
            lastDialogs += (lastDialogs.isEmpty() ? "" : " ;; ") + d.type() + ": \"" + d.message() + "\"";
            try { d.accept(); } catch (Exception ignore) { }
        });
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*investigation\\s*$");
            // The entry sits under Radiology in some builds and under Lab in others; open both.
            clickMenu("^\\s*radiology\\s*$");
            clickMenu("^\\s*lab\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*accept\\s*radiology\\s*order\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__arMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__arMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AcceptRadiologyOrder.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__arMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3500);
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '/RadiologyAcceptLabOrder'; }"); }
            catch (Exception ignore) { }
            waitForAngular(4500);
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='" + M + "FromDate']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        return onScreen();
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

    public boolean onScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        return h >= 0 && u.substring(h).contains("radiologyacceptlaborder");
    }

    // ---- search ----------------------------------------------------------

    public String enterDateRange(String from, String to) {
        String r1 = typeDate(M + "FromDate", from);
        String r2 = typeDate(M + "ToDate", to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("AcceptRadiologyOrder: " + lastDates);
        return lastDates;
    }

    private String typeDate(String model, String value) {
        boolean inDialog = Boolean.TRUE.equals(page.evaluate(
                "(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return !!(e && e.closest('.modal,[role=dialog]')); }", model));
        try {
            com.microsoft.playwright.Locator box = page.locator("input[ng-model='" + model + "']").first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            // Escape inside a dialog closes the DIALOG, not just the picker.
            if (inDialog) {
                page.evaluate("() => {" + JS
                        + " const d=topDialog(); if(!d) return;"
                        + " (d.querySelector('.modal-title,.modal-header,h3,h4') || d).click(); }");
            } else {
                box.press("Escape");
            }
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(600);
        Object back = page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return e? (e.value||'') : '(gone)'; }", model);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from)
                && lastDates.contains("ToDate=" + to);
    }

    public String lastSubGroup = "";

    /**
     * Select the <b>Sub Group</b>.
     *
     * <p>This screen REFUSES to search without one: it answers "Please select sub group!" and leaves the
     * grid untouched. A search run without it proves nothing about the data - an empty grid then says
     * only that the screen declined to look.</p>
     */
    public String selectSubGroup(int index) {
        Object r = page.evaluate("(i) => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + M + "subgroupid']\");"
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
        System.out.println("AcceptRadiologyOrder: sub group -> " + lastSubGroup);
        return lastSubGroup;
    }

    public int subGroupCount() {
        Object n = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + M + "subgroupid']\");"
                + " if(!e) return 0;"
                + " return [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean subGroupSelected() {
        return lastSubGroup != null && !lastSubGroup.startsWith("(");
    }

    /**
     * Select a sub group and search, trying each in turn until one returns records.
     *
     * <p>Radiology orders sit under one sub group at a time, so the first is usually empty; stopping
     * there would report "no records" for a screen that simply had not been asked the right question.
     * The report lists what each one returned.</p>
     */
    public String selectSubGroupWithRecords() {
        int count = subGroupCount();
        StringBuilder tried = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String name = selectSubGroup(i);
            clickSearch();
            int rows = rowCount();
            tried.append(tried.length() == 0 ? "" : ", ")
                 .append(name.split(" \\[")[0]).append(" -> ").append(rows);
            if (rows > 0) {
                lastSubGroup = name + " — it returns " + rows + " record(s). Tried: " + tried;
                System.out.println("AcceptRadiologyOrder: sub group with records -> " + lastSubGroup);
                return lastSubGroup;
            }
        }
        lastSubGroup = "(none of the " + count + " sub groups returned a record. Tried: " + tried + ")";
        System.out.println("AcceptRadiologyOrder: sub group -> " + lastSubGroup);
        return lastSubGroup;
    }

    /** Click <b>Search</b> ({@code fnSearchorderbooking}). */
    public String clickSearch() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/fnSearchorderbooking/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(5500);
        lastSearch = "clicked Search [fnSearchorderbooking] -> " + rowCount() + " record(s)";
        System.out.println("AcceptRadiologyOrder: " + lastSearch);
        return lastSearch;
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
                + " return rows.length+' record(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /** Row counts under each Sample Status radio — used to explain an empty grid. */
    public String describeStatusCounts() {
        StringBuilder sb = new StringBuilder();
        String[] names = { "Not Accepted", "Accepted", "Rejected", "All" };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Object ok = page.evaluate("(i) => {" + JS
                    + " const rs=[...document.querySelectorAll(\"input[ng-model='" + M + "SampleStatus']\")]"
                    + "   .filter(vis);"
                    + " if(!rs[i]) return false; rs[i].click(); return true; }", idx);
            if (!Boolean.TRUE.equals(ok)) continue;
            waitForAngular(700);
            clickSearch();
            sb.append(sb.length() == 0 ? "" : ", ").append(names[i]).append(" = ").append(rowCount());
        }
        return sb.toString();
    }

    // ---- select and accept ------------------------------------------------

    /**
     * Tick the checkbox in the row's <b>Select</b> column.
     *
     * <p>A real checkbox here, unlike the Lab screens' selection button. The state is read back off the
     * control and out of its model.</p>
     */
    public String tickFirstRecord() {
        Object r = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll(\".ui-grid-row input[type='checkbox']\")]"
                + "   .filter(vis)[0];"
                + " if(!b) return '(no checkbox on any record)';"
                + " if(!b.checked) b.click();"
                + " let model='(unreadable)';"
                + " try{ const c=angular.element(b).controller('ngModel');"
                + "      if(c) model=String(c.$modelValue); }catch(err){}"
                + " const row=b.closest('.ui-grid-row');"
                + " return 'ticked ['+(b.getAttribute('ng-model')||'?')+'] -> checked='+b.checked"
                + "   +', model='+model+'; the row reads: '+(row? norm(row.textContent).slice(0,90) : '?'); }");
        lastTick = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("AcceptRadiologyOrder: tick -> " + lastTick);
        return lastTick;
    }

    public boolean recordTicked() {
        return lastTick != null && lastTick.contains("checked=true") && !lastTick.contains("model=false");
    }

    /** Click <b>Accept</b> ({@code openAcceptRemark}). */
    public String clickAccept() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/openAcceptRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog();"
                + " return d? ('a dialog appeared: \"'+norm(d.textContent).slice(0,90)+'\"')"
                + "         : 'no dialog appeared after Accept'; }");
        lastAccept = r == null ? "" : r.toString();
        System.out.println("AcceptRadiologyOrder: accept -> " + lastAccept);
        return lastAccept;
    }

    /** Answer the confirmation with <b>Yes</b>. */
    public String confirmYes() {
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 'there was nothing to confirm';"
                + " const y=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^(yes|ok|confirm)$/i.test(norm(x.textContent)));"
                + " if(!y) return 'no Yes button in: \"'+norm(d.textContent).slice(0,80)+'\"';"
                + " const msg=norm(d.textContent).slice(0,110);"
                + " y.click();"
                + " return 'clicked \"'+norm(y.textContent)+'\" on: \"'+msg+'\"'; }");
        lastConfirm = r == null ? "" : r.toString();
        waitForAngular(3500);
        System.out.println("AcceptRadiologyOrder: confirm -> " + lastConfirm);
        return lastConfirm;
    }

    public boolean confirmed() { return lastConfirm != null && lastConfirm.startsWith("clicked"); }

    // ---- Mark Visit -------------------------------------------------------

    /** Select the <b>Department</b>, <b>Sub Department</b> and <b>Patient Type</b> in Mark Visit. */
    public String fillMarkVisit() {
        String dept = pick(M + "DepartmentID", "Department");
        String sub = pick(M + "SubDepartmentID", "Sub Department");
        // "Patient type" is the patient source on this dialog; the visit-type list is the other
        // candidate, so whichever one actually offers options is used and named in the report.
        String type = pick(M + "PatientSourceID", "Patient Type");
        if (type.startsWith("(")) type = pick(M + "VisitTypeID", "Patient Type (visit type)");
        lastMarkVisit = "Department=" + dept + " | SubDepartment=" + sub + " | PatientType=" + type;
        waitForAngular(700);
        System.out.println("AcceptRadiologyOrder: mark visit -> " + lastMarkVisit);
        return lastMarkVisit;
    }

    private String pick(String model, String what) {
        Object r = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " if(!e) return '(no dropdown '+m+')';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(the list has no real option)';"
                + " e.selectedIndex=reals[0].i;"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                + " return norm((e.options[e.selectedIndex]||{}).text)+' ['+reals.length+' options]'; }",
                model);
        waitForAngular(800);
        return r == null ? "" : r.toString();
    }

    public boolean markVisitFilled() {
        return lastMarkVisit != null && !lastMarkVisit.contains("(no dropdown")
                && !lastMarkVisit.contains("(the list has no real option)");
    }

    /** Click <b>Mark Visit</b> ({@code fnMarkVisit}). */
    public String clickMarkVisit() {
        page.evaluate("() => {" + JS
                + " const d=topDialog();"
                + " const b=[...(d? d.querySelectorAll('button,a') : [])].filter(vis)"
                + "   .find(x=>/fnMarkVisit/i.test(x.getAttribute('ng-click')||'')"
                + "        || /^\\s*mark\\s*visit\\s*$/i.test(norm(x.textContent)));"
                + " if(b) b.click(); }");
        waitForAngular(4000);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog();"
                + " return d? ('the next dialog reads: \"'+norm(d.textContent).slice(0,90)+'\"')"
                + "         : 'no dialog is on screen after Mark Visit'; }");
        lastMarkVisitSave = r == null ? "" : r.toString();
        System.out.println("AcceptRadiologyOrder: mark visit clicked -> " + lastMarkVisitSave);
        return lastMarkVisitSave;
    }

    // ---- Acceptance Remark ------------------------------------------------

    /**
     * Fill the acceptance dialog: the <b>Remark</b> and the <b>No of Films</b>.
     *
     * <p>Matched inside the dialog by model or label, because this environment holds no radiology order
     * to walk the chain with, so the exact models could not be dumped from a live dialog. Whatever is
     * found is named in the report.</p>
     */
    public String fillAcceptanceRemark(String remark, String films) {
        Object r = page.evaluate("(a) => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div');"
                + "   return g? norm(g.textContent).slice(0,60):''; };"
                + " const boxes=[...d.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='checkbox' && e.type!=='radio' && e.type!=='hidden');"
                + " const rem=boxes.find(e=>/remark/i.test((e.getAttribute('ng-model')||'')+' '+labelOf(e)));"
                + " const film=boxes.find(e=>/film/i.test((e.getAttribute('ng-model')||'')+' '+labelOf(e)));"
                + " const rr = rem? setEl(rem,a[0])+' ['+(rem.getAttribute('ng-model')||'?')+']'"
                + "                : '(no Remark box; the dialog offers: '"
                + "                  +boxes.map(e=>e.getAttribute('ng-model')||'?').join(', ')+')';"
                + " const rf = film? setEl(film,a[1])+' ['+(film.getAttribute('ng-model')||'?')+']'"
                + "                 : '(no No-of-Films box)';"
                + " return 'Remark='+rr+' | NoOfFilms='+rf; }",
                java.util.List.of(remark, films));
        lastRemarkDialog = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("AcceptRadiologyOrder: acceptance remark -> " + lastRemarkDialog);
        return lastRemarkDialog;
    }

    public boolean remarkFilled(String remark, String films) {
        return lastRemarkDialog != null && !lastRemarkDialog.contains("(no ")
                && lastRemarkDialog.contains("Remark=" + remark)
                && lastRemarkDialog.contains("NoOfFilms=" + films);
    }

    /** Click <b>OK</b> in the acceptance dialog and return the toast. */
    public String clickOkAndGetToast() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove());"
                    + " window.__arSeen = [];"
                    + " if(window.__arObs) window.__arObs.disconnect();"
                    + " window.__arObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /toast|alert|jAlert|sweet/i.test(cls+' '+n.id))"
                    + "       window.__arSeen.push(t.slice(0,140)); } } });"
                    + " window.__arObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog to confirm)';"
                + " const b=[...d.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/^(ok|save|submit)$/i.test(norm(x.textContent)));"
                + " if(!b) return '(no OK button in the dialog)';"
                + " b.click(); return 'clicked \"'+norm(b.textContent)+'\"'; }");
        waitForAngular(3500);

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
            Object s = page.evaluate("() => { const a=window.__arSeen||[];"
                    + " if(window.__arObs) window.__arObs.disconnect();"
                    + " return a.length? a.slice(0,4).join(' ;; ') : ''; }");
            seen = s == null ? "" : s.toString();
        } catch (Exception ignore) { }
        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[^A-Za-z0-9]*(?:KPJ Portal)?\\s*([A-Za-z][^;]{3,120})").matcher(seen);
            if (m.find()) { toast = m.group(1).trim(); toastFromObserver = true; }
        }
        toast = toast.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastSave = (clicked == null ? "" : clicked.toString()) + "; "
                + (seen.isEmpty() ? "nothing was recorded appearing after OK" : "the page added: " + seen);
        System.out.println("AcceptRadiologyOrder: ok -> " + lastSave + " toast=\"" + toast + "\"");
        return toast;
    }

    // ---- reject ----------------------------------------------------------

    public String lastReject = "", lastRejectDialog = "";

    /**
     * Click <b>Reject</b> ({@code openRejectRemark}) and report what came up.
     *
     * <p>Rejecting does not go through Mark Visit - it raises the Rejection Reason dialog directly. A
     * confirmation is still handled if this environment raises one, so the chain says what happened
     * rather than stalling.</p>
     */
    public String clickReject() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/openRejectRemark/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3500);
        Object r = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return 'nothing came up after Reject';"
                + " const text=norm(d.textContent).slice(0,100);"
                + " const hasReason=!!d.querySelector('select');"
                + " const yes=[...d.querySelector? d.querySelectorAll('button,a') : []]"
                + "   .filter(vis).find(x=>/^\\s*yes\\s*$/i.test(norm(x.textContent)));"
                + " if(yes && !hasReason){ yes.click();"
                + "   return 'a confirmation came up and was answered Yes: \"'+text+'\"'; }"
                + " return 'the Rejection Reason dialog opened: \"'+text+'\"'; }");
        lastReject = r == null ? "" : r.toString();
        waitForAngular(2500);
        if (lastReject.startsWith("a confirmation")) {
            Object after = page.evaluate("() => {" + JS
                    + " const d=topDialog(); if(!d) return ' — and nothing followed it';"
                    + " return ' — and then: \"'+norm(d.textContent).slice(0,90)+'\"'; }");
            lastReject += after == null ? "" : after.toString();
        }
        System.out.println("AcceptRadiologyOrder: reject -> " + lastReject);
        return lastReject;
    }

    public boolean rejectDialogOpen() {
        return lastReject != null && (lastReject.contains("Rejection Reason dialog opened")
                || lastReject.contains("Rejection"));
    }

    /**
     * In the rejection dialog: choose the <b>Rejection Reason</b> and enter the <b>Remark</b>.
     *
     * <p>The reason list is the dialog's own select; the remark box is whatever text control it carries,
     * matched on its model or its label so a renamed field is still found.</p>
     */
    public String fillRejectionReason(String remark) {
        Object reason = page.evaluate("() => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const e=[...d.querySelectorAll('select')].filter(vis)[0];"
                + " if(!e) return '(no Rejection Reason list)';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " if(!reals.length) return '(the Rejection Reason list holds only Select)';"
                + " e.selectedIndex=reals[0].i;"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                + " return norm((e.options[e.selectedIndex]||{}).text)+' ['"
                + "   +(e.getAttribute('ng-model')||'?')+', '+reals.length+' reasons]'; }");
        waitForAngular(700);
        Object rem = page.evaluate("(v) => {" + JS
                + " const d=topDialog(); if(!d) return '(no dialog on screen)';"
                + " const boxes=[...d.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='checkbox' && e.type!=='radio' && e.type!=='hidden');"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div');"
                + "   return g? norm(g.textContent) : ''; };"
                + " const e=boxes.find(x=>/remark/i.test((x.getAttribute('ng-model')||'')+' '+labelOf(x)))"
                + "   || boxes[0];"
                + " return e? setEl(e,v)+' ['+(e.getAttribute('ng-model')||'?')+']' : '(no Remark box)'; }",
                remark);
        lastRejectDialog = "RejectionReason=" + reason + " | Remark=" + rem;
        waitForAngular(600);
        System.out.println("AcceptRadiologyOrder: rejection dialog -> " + lastRejectDialog);
        return lastRejectDialog;
    }

    public boolean rejectionFilled(String remark) {
        return lastRejectDialog != null && !lastRejectDialog.contains("(no ")
                && !lastRejectDialog.contains("only Select")
                && lastRejectDialog.contains("Remark=" + remark);
    }

    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("please") || t.contains("error")
                || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("accepted")
                || t.contains("updated");
    }
}
