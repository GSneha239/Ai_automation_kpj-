package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * AppointmentListPage — Page Object for OP &gt; Appointment &gt; Appointment List (#/AppointmentList).
 *
 * <p>Flow (verified live): search appointments, <b>select an appointment row</b>
 * (<code>ng-click="fnSelectAppointment(app,$index)"</code> — this populates
 * <code>SelectedAppointment</code>), then click <b>Reschedule Appointment</b>
 * (<code>fnReschduleAppointment()</code>), which auto-navigates to Book Appointment (#/New)
 * pre-filled from the existing appointment. Complete the mandatory fields the reschedule doesn't
 * carry over (Prefix, Nationality, Payor Type, Booking Type), Save, confirm → the Registration
 * Report opens in a new tab.</p>
 *
 * <p>Unlike a fresh Book Appointment (which posts an empty AppointmentListJsonString and generates
 * no report), the reschedule sends a populated payload, so the save works and the report is
 * generated.</p>
 */
public class AppointmentListPage extends BasePage {

    public AppointmentListPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /**
     * Expand a collapsed VisitScreen accordion section ("Patient Information" / "Payor Information" /
     * "Visit Information") so its fields are actually fillable.
     *
     * <p>Reaching the VisitScreen from the Appointment List renders EVERY section COLLAPSED — only the footer bar
     * is visible. A collapsed section's inputs still exist in the DOM and a locator resolves to them, but they are
     * not visible, so {@code fill()} waits the full timeout and throws, and every field then reports as empty.
     * That is what produced "these mandatory fields were still empty: Name, Identification Type, Nationality …"
     * and "Please Select Visit Type!" — nothing was ever filled because nothing was open.</p>
     *
     * <p>Only clicks when the section is actually closed: clicking an OPEN accordion header COLLAPSES it.</p>
     *
     * @return what happened, for the log
     */
    public String expandSection(String titleRegex) {
        try {
            Object r = page.evaluate("(t) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const re=new RegExp('^\\\\s*'+t+'\\\\s*$','i');"
                    + " const heads=[...document.querySelectorAll('a,button,div,h3,h4,span,.panel-heading,.panel-title,.box-header')]"
                    + "   .filter(x=>re.test(norm(x.textContent)) && x.offsetParent!==null);"
                    + " if(!heads.length) return 'no header';"
                    + " const h=heads[0];"
                    // Body = the collapsible panel this header controls; open when it holds a VISIBLE field.
                    + " const panel=h.closest('.panel,.box,.card') || h.parentElement;"
                    + " const open=panel && [...panel.querySelectorAll('input,select,textarea')].some(e=>e.offsetParent!==null);"
                    + " if(open) return 'already open';"
                    + " h.click(); return 'clicked'; }", titleRegex);
            String how = r == null ? "" : r.toString();
            if ("clicked".equals(how)) waitForAngular(1200);
            System.out.println("expandSection('" + titleRegex + "') => " + how);
            return how;
        } catch (Exception e) {
            System.out.println("expandSection('" + titleRegex + "'): " + e.getMessage());
            return "error";
        }
    }

    /**
     * True when the Appointment List is ALREADY on screen with its grid rendered — i.e. the previous section's
     * action returned here by itself and a full re-navigation would be wasted work.
     */
    public boolean alreadyOnList() {
        try {
            if (!page.url().toLowerCase().contains("appointmentlist")) return false;
            return Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ui-grid-row, table tbody tr')].some(r=>r.offsetParent!==null)"));
        } catch (Exception e) { return false; }
    }

    /** How many rows the grid is currently showing (0 = the search came back empty). */
    public int listRowCount() {
        try {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')];"
                    + " if(rows.length) return rows.filter(x=>x.offsetParent!==null).length;"
                    + " return [...document.querySelectorAll('table tbody tr')].filter(x=>x.offsetParent!==null && norm(x.textContent) && !/no\\s*record/i.test(norm(x.textContent))).length; }");
            return r instanceof Number ? ((Number) r).intValue() : 0;
        } catch (Exception e) { return 0; }
    }

    public void navigateTo(String baseUrl) {
        // Login already authenticated the SPA and landed on PatientDashboard. Re-navigating with
        // page.navigate() re-loads the heavy dashboard whose "load" event can hang (websockets/widgets)
        // and time out. Instead do IN-APP hash routing (no page reload). Bounce via PatientDashboard so
        // the AppointmentList controller RE-ENTERS FRESH each call — otherwise a no-op hash set (when
        // already on #/AppointmentList) leaves a previous section's filter/state, and the search returns
        // no rows.
        page.evaluate("() => { window.location.hash = '#/PatientDashboard'; }");
        waitForAngular(800);
        page.evaluate("() => { window.location.hash = '#/AppointmentList'; }");
        waitForAngular(1500);

        // Fallback: if the list didn't render, expand the OP menu and click the Appointment List link.
        boolean ready = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"button[ng-click='fnSearchAppointments();']\")"));
        if (!ready) {
            page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP'); if(op) op.click(); }");
            waitForAngular(1000);
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/AppointmentList'); if(a) a.click(); }");
            waitForAngular(1000);
        }

        page.waitForSelector("button[ng-click='fnSearchAppointments();']",
                new Page.WaitForSelectorOptions().setTimeout(20000));
        waitForAngular(1000);
        installHelpers(); // alert hook (captures "File Requested/Allocated successfully")
    }

    /**
     * After a report opens in a new tab and has been captured, close the extra tab(s), bring the
     * primary page back to the front, and navigate to the Appointment List — otherwise focus is left
     * on the report tab. Call this once the report step has finished using the report tab.
     */
    public void returnToAppointmentList(String baseUrl) {
        try {
            for (Page p : page.context().pages()) {
                if (p != page) { try { p.close(); } catch (Exception ignore) {} }
            }
        } catch (Exception ignore) {}
        try { page.bringToFront(); } catch (Exception ignore) {}
        page.navigate(baseUrl + "/#/AppointmentList");
        try {
            page.waitForSelector("button[ng-click='fnSearchAppointments();']",
                    new Page.WaitForSelectorOptions().setTimeout(20000));
        } catch (Exception ignore) {}
        waitForAngular(1000);
    }

    /** Click Search with the default (today) date filters to load the appointment list. */
    public void search() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnSearchAppointments();'); if(b) b.click(); }");
        waitForAngular(2500);
    }

    // ---- search & select --------------------------------------------------

    /** Set the From/To date filters (dd/MM/yyyy) and click Search. */
    public void searchAppointments(String fromDate, String toDate) {
        page.evaluate("(a) => { const [f,t]=a;"
                + "const set=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + "  if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + "set('PatientData.FromApptTime', f); set('PatientData.ToApptTime', t);"
                + "const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnSearchAppointments();'); if(b) b.click(); }",
                java.util.List.of(fromDate, toDate));
        // Wait for the results grid to actually populate (the search API + render can take several
        // seconds); a fixed short pause races the render and can read 0 rows.
        try {
            page.waitForFunction(
                    "() => document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\").length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) {
            System.out.println("searchAppointments: no rows rendered within timeout (empty result?)");
        }
        waitForAngular(800);
    }

    /**
     * Select the first appointment row (invokes fnSelectAppointment, which populates
     * SelectedAppointment — required by Reschedule). Returns "PatientName / AppointmentNo", or null.
     */
    public String selectFirstAppointment() { return selectFirstAppointment(0); }

    /**
     * Select the <b>skip</b>-th rescheduleable appointment from the ranked candidate list (0 = best). This lets
     * the caller RETRY with a DIFFERENT patient when the chosen one can't be rescheduled ("visit already mark!",
     * or Reschedule doesn't navigate). Returns "PatientName / MRN / AppointmentNo / Dr / time", or null
     * (ERR / no candidate at that index).
     */
    public String selectFirstAppointment(int skip) {
        Object r = page.evaluate("(skip) => {"
                + "const tables=[...document.querySelectorAll('table')].filter(t=>/patient name|appointment no/i.test(t.innerText||''));"
                + "const t=tables.sort((a,b)=>b.querySelectorAll('tbody tr').length-a.querySelectorAll('tbody tr').length)[0]; if(!t) return 'ERR:no-table';"
                + "const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null); if(!rows.length) return 'ERR:no-rows';"
                + "const timesOf=app=>{ const out=[]; for(const k in app){ const v=app[k]; if(typeof v==='string' && /^\\s*\\d{1,2}:\\d{2}\\s*(AM|PM)\\s*$/i.test(v)) out.push(v.replace(/\\s+/g,' ').trim()); } return out; };"
                + "const scoped=rows.map(row=>angular.element(row).scope()).filter(s=>s && s.app && typeof s.fnSelectAppointment==='function');"
                + "if(!scoped.length) return 'ERR:no-app-scope('+rows.length+' rows)';"
                // Prefer a REAL, rescheduleable appointment: a valid MRN + patientid>0 + a doctor/modality
                // + a scheduled time. AutoBook rows (patientid=0, no MRN, no doctor) can't be rescheduled.
                + "const hasMRN=a=>['mrno','MRNO','MRN','mrnno'].some(k=>((a[k]||'')+'').trim().length>0);"
                + "const docOf=a=>{ for(const k in a){ if(/doctor|modality/i.test(k) && typeof a[k]==='string' && a[k].trim() && !/select|^-+$/i.test(a[k].trim())) return a[k].trim(); } return ''; };"
                // visitstatus===2 means the visit is already marked (patient arrived) → "visit already mark!" → can't reschedule.
                + "const marked=a=>(+(a.visitstatus||0))===2;"
                + "const isReal=s=>{ const a=s.app; return (+(a.patientid||0)>0) && hasMRN(a) && timesOf(a).length>0 && !marked(a); };"
                // Build a RANKED candidate list (best first, de-duplicated) so 'skip' can walk to the next patient:
                // real+doctor > real > not-marked+timed > any timed > anything.
                + "const uniq=arr=>{const seen=new Set();return arr.filter(s=>{if(seen.has(s.$id))return false;seen.add(s.$id);return true;});};"
                + "const ranked=uniq([].concat(scoped.filter(s=>isReal(s)&&docOf(s.app)), scoped.filter(s=>isReal(s)), scoped.filter(s=>!marked(s.app)&&timesOf(s.app).length>0), scoped.filter(s=>timesOf(s.app).length>0), scoped));"
                + "if(skip>=ranked.length) return 'ERR:no-more-candidates(skip='+skip+',have='+ranked.length+')';"
                + "const chosen=ranked[skip];"
                + "const chosenTimes=timesOf(chosen.app);"
                + "chosen.$apply(function(){ chosen.fnSelectAppointment(chosen.app, chosen.$index); });"
                + "window.__reschedFrom=chosenTimes[0]||''; window.__reschedTo=chosenTimes[1]||chosenTimes[0]||''; window.__reschedDoctor=docOf(chosen.app)||'';"
                + "const mrn=['mrno','MRNO','MRN','mrnno'].map(k=>chosen.app[k]).find(v=>((v||'')+'').trim())||'';"
                + "return ((chosen.app.PatientName||chosen.app.patientname||'')+'').trim() + ' / MRN ' + mrn + ' / ' + (chosen.app.AppointmentNo||chosen.app.appointmentno||'') + ' / Dr ' + (window.__reschedDoctor||'-') + ' @ ' + (window.__reschedFrom||'no-time'); }", skip);
        waitForAngular(500);
        String s = r == null ? null : r.toString();
        if (s != null && s.startsWith("ERR:")) { System.out.println("selectFirstAppointment(skip=" + skip + "): " + s); return null; }
        return s;
    }

    // ---- reschedule -> Book Appointment -----------------------------------

    /**
     * Click Reschedule Appointment; it navigates to Book Appointment (#/New). Installs the in-page
     * helpers on the booking page. Returns true if the navigation happened.
     */
    public boolean clickRescheduleAndOpenBooking() {
        page.evaluate("() => { window.__alerts=[]; const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnReschduleAppointment();'); if(b) b.click(); }");
        waitForAngular(800);
        // A real appointment may show a confirm ("Do you want to reschedule?") before navigating, or an
        // alert if it can't be rescheduled — click through any confirm.
        for (int i = 0; i < 3; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.getBoundingClientRect().width>0 && /reschedul|do you want|are you sure/i.test(m.textContent||''))"));
            if (!confirm) break;
            clickNgConfirmButton("Yes");
            clickNgConfirmButton("OK");
            clickNgConfirmButton("Save");
            waitForAngular(600);
        }
        try {
            page.waitForURL("**/#/New", new Page.WaitForURLOptions().setTimeout(20000));
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(20000));
        } catch (Exception e) {
            System.out.println("clickRescheduleAndOpenBooking: did not navigate to Book Appointment (" + e.getMessage() + ")");
            return false;
        }
        // The existing appointment's From time populates asynchronously after navigation — wait for it.
        try {
            page.waitForFunction(
                    "() => { const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='PatientData.FromApptTime' && x.offsetParent!==null); return e && (e.value||'').trim().length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("clickRescheduleAndOpenBooking: From time did not populate within timeout");
        }
        waitForAngular(1000);
        installHelpers();
        return true;
    }

    private void installHelpers() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                + "window.__setSel = function(ng, text){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "  if(!e) return; let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===text.toLowerCase());"
                + "  if(i<0) i=[...e.options].findIndex(o=>(o.text||'').toLowerCase().includes(text.toLowerCase())); if(i<0) return;"
                + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + "window.__setInp = function(ng, val){ const e=[...document.querySelectorAll('input,textarea')].filter(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null)[0];"
                + "  if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();}"
                + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; }");
    }

    /**
     * On the Book Appointment page, the reschedule PRE-FILLS the existing patient's details. Do NOT
     * overwrite them (overwriting e.g. Prefix='Mr.' on a Female patient corrupts the record and the
     * save produces no report). Only fill a mandatory field that is genuinely EMPTY, matching the
     * existing patient (Prefix derived from the pre-filled Gender; Doctor from the picked appointment).
     * The new slot's date/From/To are set separately by {@link #selectFreeRescheduleSlot}.
     */
    public void completeMandatoryFields(String newDate) {
        page.evaluate("(d) => {"
                + "const getSel=ng=>{const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e?((e.options[e.selectedIndex]||{}).text||'').trim():''; };"
                + "const emptySel=ng=>{const v=getSel(ng); return !v || /^-*\\s*select\\s*-*$/i.test(v); };"
                // Fill each mandatory field ONLY when it is empty — never touch pre-filled patient details.
                + "if(emptySel('PatientData.receivabletypeid')) window.__setSel('PatientData.receivabletypeid','Self');"
                + "if(emptySel('PatientData.BookingTypeID')) window.__setSel('PatientData.BookingTypeID','KPJ');"
                + "if(emptySel('PatientData.NationalityID')) window.__setSel('PatientData.NationalityID','Malaysian');"
                + "if(emptySel('PatientData.PrefixID')){ const g=getSel('PatientData.GenderID'); window.__setSel('PatientData.PrefixID', /female/i.test(g)?'Ms.':'Mr.'); }"
                + "if(emptySel('PatientData.DoctorModalityID') && window.__reschedDoctor) window.__setSel('PatientData.DoctorModalityID', window.__reschedDoctor);"
                + "if(d) window.__setInp('PatientData.AppointmentDate', d);"
                + " }", newDate);
        waitForAngular(700);
    }

    /**
     * On the reschedule Book Appointment page, pick an AVAILABLE (green) slot from the Next Schedule
     * grid — the original appointment's time may now be booked (red), which makes the reschedule save
     * silently fail with no report. Sets AppointmentDate + From/To to the chosen free slot. Returns the
     * slot's From time, or null if none is available.
     *
     * <p><b>Verified live 2026-07-08:</b> the slot checkbox MUST be ticked with a REAL Playwright click
     * so AngularJS's ng-click fires and pushes the slot into <code>AppointmentListJsonString</code>. A
     * synthetic in-page <code>cb.click()</code> leaves that list EMPTY, so the reschedule save posts
     * <code>{AppointmentListJsonString:[], ExecFlag:"RescheduleAppointment"}</code>, the server returns
     * <code>[]</code>, and no Appointment Slip is generated. So we only TAG the chosen checkbox here and
     * click it for real below (mirrors the working MAB {@code selectAppointmentSlots}).</p>
     */
    public String selectFreeRescheduleSlot() {
        try {
            page.waitForFunction(
                    "() => { const t=[...document.querySelectorAll('table')].find(x=>/Next Schedule/.test(x.caption&&x.caption.textContent||''));"
                            + "return t && [...t.querySelectorAll('tbody tr input[type=checkbox]')].some(c=>!c.disabled); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("selectFreeRescheduleSlot: Next Schedule slots did not load");
        }
        // Find the first free slot, set the date, and TAG its checkbox (no synthetic tick — see Javadoc).
        Object r = page.evaluate("() => {"
                + "const nextT=[...document.querySelectorAll('table')].find(t=>/Next Schedule/.test(t.caption&&t.caption.textContent||''));"
                + "if(!nextT) return null;"
                + "const cap=(nextT.caption.textContent||'');"
                + "const m=cap.match(/(\\d{2}\\/\\d{2}\\/\\d{4})/); if(m && window.__setInp) window.__setInp('PatientData.AppointmentDate', m[1]);"
                + "const rows=[...nextT.querySelectorAll('tbody tr')];"
                + "const isRed=cell=>{ if(!cell) return false; const bg=getComputedStyle(cell).backgroundColor; return /rgb\\(2\\d\\d,\\s*\\d?\\d,/.test(bg); };"
                + "const tag=row=>{ const cb=row.querySelector('input[type=checkbox]'); const tds=row.querySelectorAll('td');"
                + "  const from=((tds[1]||{}).textContent||'').trim(); const to=((tds[2]||{}).textContent||'').trim();"
                + "  cb.id='__reschedSlotCb'; window.__reschedSlotFrom=from.replace(/\\s+/g,' '); window.__reschedSlotTo=to.replace(/\\s+/g,' '); return from; };"
                + "let free=rows.find(row=>{ const cb=row.querySelector('input[type=checkbox]'); return cb && !cb.disabled && !cb.checked && !isRed(row.querySelectorAll('td')[1]); });"
                + "if(free) return tag(free);"
                + "let any=rows.find(row=>{ const cb=row.querySelector('input[type=checkbox]'); return cb && !cb.disabled && !cb.checked; });"
                + "if(any) return tag(any);"
                + "return null; }");
        if (r == null) return null;
        // Real Playwright click ticks the slot — its ng-click fnSelectFromNextSchedule(val,$index) sets
        // val.IsSelected (STAGES the slot; it does NOT yet enter the appointment list).
        try {
            page.locator("#__reschedSlotCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) {
            System.out.println("selectFreeRescheduleSlot: real checkbox click failed (" + e.getMessage() + ")");
        }
        // Mirror the slot's From/To into the form fields for display/validation.
        page.evaluate("() => { if(window.__setInp){ window.__setInp('PatientData.FromApptTime', window.__reschedSlotFrom||''); window.__setInp('PatientData.ToApptTime', window.__reschedSlotTo||''); } }");
        waitForAngular(400);
        // THE FIX (verified live 2026-07-08): click "Add" (fnAddToMultipleApptList) to move the staged
        // slot into the "Multiple Appointment Booking" grid. That grid is what the reschedule save posts
        // as AppointmentListJsonString — WITHOUT this the list is [], the server returns [], and NO
        // Appointment Slip is generated (the silent failure).
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnAddToMultipleApptList();' && x.offsetParent!==null); if(b) b.id='__addSlotBtn'; }");
        try {
            page.locator("#__addSlotBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) {
            System.out.println("selectFreeRescheduleSlot: Add (fnAddToMultipleApptList) click failed (" + e.getMessage() + ")");
        }
        // Confirm the slot actually landed in the appointment list grid before we try to save.
        try {
            page.waitForFunction(
                    "() => { const t=[...document.querySelectorAll('table')].find(x=>/Multiple Appointment Booking/i.test((x.caption&&x.caption.textContent)||'')); return t && t.querySelectorAll('tbody tr').length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("selectFreeRescheduleSlot: slot did not appear in the Multiple Appointment Booking grid");
        }
        waitForAngular(500);
        return r.toString();
    }

    // ---- save & report ----------------------------------------------------

    /** Save → confirm "Do You Want To Save" → capture the Registration Report tab (or null). */
    public Page saveAndCaptureReport() {
        page.evaluate("() => { const b=document.querySelector('#btnSave'); if(!b) return;"
                + " try { const sc=angular.element(b).scope(); sc.$apply(function(){ sc.IsClicked=true; }); } catch(e){}"
                + " window.__alerts=[]; document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); b.click(); }");
        waitForAngular(600);
        try {
            page.waitForSelector(".ng-confirm-box button", new Page.WaitForSelectorOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no confirm dialog. Alerts: " + capturedAlerts());
            return null;
        }
        Page reportTab = null;
        try {
            reportTab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(25000),
                    () -> {
                        clickNgConfirmButton("Save");
                        page.waitForTimeout(1500);
                        clickNgConfirmButton("Yes");   // optional "slot already booked" prompt
                        page.waitForTimeout(1500);
                    });
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no report tab opened (" + e.getMessage() + ")");
        }
        if (reportTab != null) {
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(1500);
        }
        return reportTab;
    }

    /**
     * Reschedule save: on the Book Appointment page there is NO Save button — click the footer
     * <b>"Reschedule Appointment"</b> button, confirm the "Do you want to Save" alert, and capture the
     * report tab that opens. Returns the report tab, or null.
     */
    public Page saveRescheduleAndCaptureReport() {
        page.evaluate("() => { window.__alerts=[]; document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove());"
                + " const b=[...document.querySelectorAll('button')].find(x=>/reschedule appointment/i.test((x.textContent||'').trim()) && x.getBoundingClientRect().width>0);"
                + " if(b){ try{ const sc=angular.element(b).scope(); sc.$apply(function(){ sc.IsClicked=true; }); }catch(e){} b.click(); } }");
        waitForAngular(600);
        try {
            page.waitForSelector(".ng-confirm-box button, .jconfirm button, button.btn-danger:has-text('SAVE')",
                    new Page.WaitForSelectorOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("saveRescheduleAndCaptureReport: no confirm dialog. Alerts: " + capturedAlerts());
            return null;
        }
        Page reportTab = null;
        try {
            reportTab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(25000),
                    () -> {
                        clickNgConfirmButton("Save");
                        page.waitForTimeout(1200);
                        clickNgConfirmButton("Yes"); // optional secondary prompt
                        page.waitForTimeout(1200);
                        clickNgConfirmButton("OK");
                        page.waitForTimeout(1200);
                    });
        } catch (Exception e) {
            System.out.println("saveRescheduleAndCaptureReport: no report tab opened (" + e.getMessage() + ")");
        }
        if (reportTab != null) {
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(1500);
        }
        return reportTab;
    }

    private void clickNgConfirmButton(String text) {
        Object tagged = page.evaluate("(t) => { const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].filter(m=>m.getBoundingClientRect().width>0);"
                + "let b=null; for(const box of boxes){ b=[...box.querySelectorAll('button')].find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase() && x.getBoundingClientRect().width>0); if(b) break; }"
                + "if(!b){ b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.modal button')].find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase() && x.getBoundingClientRect().width>0); }"
                + "if(!b) return false; b.id='__ngConfirmBtn'; return true; }", text);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ngConfirmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception ignore) {}
        }
    }

    // ---- view app history -------------------------------------------------

    /**
     * Click "View App History" (fnViewAppHistory) for the selected appointment and wait for the
     * history popup (a modal titled "View App History" listing the patient's past appointments).
     * Returns true if the popup opened.
     */
    public boolean openViewAppHistory() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fnViewAppHistory/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /view app history/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("openViewAppHistory: popup did not open");
            return false;
        }
        // Wait for the history DATA to load inside the popup — the modal renders before the async fetch fills the
        // table, so wait for at least one data row (or an explicit "no records" message) before returning, otherwise
        // the screenshot catches an empty popup.
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /view app history/i.test(x.textContent||'')); if(!m) return false;"
                    + " const rows=[...m.querySelectorAll('table tbody tr, .ui-grid-row, [ng-repeat]')].filter(r=>r.offsetParent!==null && (r.textContent||'').replace(/\\s+/g,' ').trim().length>2);"
                    + " const noRec=/no\\s*record|no\\s*data|not\\s*found/i.test(m.textContent||'');"
                    + " return rows.length>0 || noRec; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("openViewAppHistory: history data slow to load"); }
        waitForAngular(900);
        return true;
    }

    /** Close the View App History popup (its red "Close" button). */
    public void closeViewAppHistory() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /view app history/i.test(x.textContent||''));"
                + "if(!m) return; const c=[...m.querySelectorAll('button')].find(b=>/^close$/i.test((b.textContent||'').trim()) && b.getBoundingClientRect().width>0); if(c) c.click(); }");
        waitForAngular(500);
    }

    // ---- change executor --------------------------------------------------

    /** Click "Change Executor" (fnChangeExecutor) for the selected appointment; wait for the popup. */
    public boolean openChangeExecutor() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fnChangeExecutor/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /change executor/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("openChangeExecutor: popup did not open");
            return false;
        }
        waitForAngular(800);
        return true;
    }

    /** Fill the Change Executor popup — Execution Department then Executor (doctor). Returns a summary. */
    public String fillExecutor(String department, String doctor) {
        Object dep = page.evaluate("(dept) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.ExecutionDepartmetId'); if(!e)return '(missing)';"
                + "let i=[...e.options].findIndex(o=>o.text.trim()===dept); if(i<0)i=[...e.options].findIndex(o=>o.text.trim().toLowerCase().includes(dept.toLowerCase())); if(i<0)return '(no-opt)';"
                + "e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} return e.options[i].text.trim(); }", department);
        waitForAngular(1200); // executor list may reload after the department is set
        Object doc = page.evaluate("(doc) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.ExecutionDoctorId'); if(!e)return '(missing)';"
                + "let i=[...e.options].findIndex(o=>o.text.trim()===doc); if(i<0)i=[...e.options].findIndex(o=>o.text.trim().toLowerCase().includes(doc.toLowerCase())); if(i<0)return '(no-opt)';"
                + "e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} return e.options[i].text.trim(); }", doctor);
        waitForAngular(500);
        return "Execution Department: " + dep + " | Executor: " + doc;
    }

    /** Click Save (SaveExecutorDetails) and return the success toast ("Executor changed successfully"). */
    public String saveExecutorAndGetToast() {
        page.evaluate("() => { window.__exToast=''; if(window.__exObs) window.__exObs.disconnect();"
                + "window.__exObs=new MutationObserver(()=>{ const el=document.querySelector('.toast-message'); const m=el?(el.textContent||'').trim():''; if(m && !window.__exToast) window.__exToast=m; });"
                + "window.__exObs.observe(document.body,{childList:true,subtree:true});"
                + "const sv=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='SaveExecutorDetails()' && x.offsetParent!==null); if(sv) sv.click(); }");
        try {
            page.waitForFunction("() => window.__exToast && window.__exToast.length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("saveExecutorAndGetToast: no success toast observed");
        }
        Object r = page.evaluate("() => window.__exToast || ''");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Change Executor popup (its "Close" button). */
    public void closeChangeExecutor() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change executor/i.test(x.textContent||''));"
                + "if(!m) return; const c=[...m.querySelectorAll('button')].find(b=>/^close$/i.test((b.textContent||'').trim()) && b.getBoundingClientRect().width>0); if(c) c.click(); }");
        waitForAngular(500);
    }

    // ---- cancel appointment ----------------------------------------------

    /**
     * Tick the Cancellation checkbox (app.isenabled — what fnCancelAppointment reads) of the first
     * cancellable appointment row. Returns "PatientName / AppointmentNo", or null if none is
     * cancellable (all checkboxes disabled).
     */
    public String selectFirstCancellableAppointment() {
        Object r = page.evaluate("() => {"
                + "window.__alerts=[];"
                + "const tables=[...document.querySelectorAll('table')].filter(t=>/patient name|appointment no/i.test(t.innerText||''));"
                + "const t=tables.sort((a,b)=>b.querySelectorAll('tbody tr').length-a.querySelectorAll('tbody tr').length)[0]; if(!t) return null;"
                + "const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null);"
                + "rows.forEach(r=>{const cb=[...r.querySelectorAll('input[type=checkbox]')].find(c=>c.getAttribute('ng-model')==='app.isenabled'); if(cb&&cb.checked) cb.click();});"
                + "for(const r of rows){ const sc=angular.element(r).scope(); if(!sc||!sc.app) continue;"
                + "  const cb=[...r.querySelectorAll('input[type=checkbox]')].find(c=>c.getAttribute('ng-model')==='app.isenabled');"
                + "  if(cb && !cb.disabled){ cb.click(); return ((sc.app.PatientName||sc.app.patientname||'')+'').trim()+' / '+(sc.app.AppointmentNo||sc.app.appointmentno||''); } }"
                + "return null; }");
        waitForAngular(400);
        return r == null ? null : r.toString();
    }

    /**
     * Click Cancel Appointment, confirm the "Are you sure. Do you want to Cancel?" dialog, and wait
     * for the Cancellation Reason form (Remark) to open. Returns true if the form opened.
     */
    public boolean clickCancelAppointment() {
        // This section runs LAST, so a stale dialog/backdrop left open by an earlier section (Reschedule, the
        // Registration confirms) can swallow the click — the flow itself is fine in isolation. Clear leftovers,
        // and retry the button once if the confirm doesn't appear.
        page.evaluate("() => { document.querySelectorAll('.modal-backdrop').forEach(b=>b.remove());"
                + " [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].filter(m=>m.getBoundingClientRect().width>0 && !/do you want to cancel/i.test(m.textContent||''))"
                + "   .forEach(m=>{ const b=[...m.querySelectorAll('button')].find(x=>/^(no|ok|close|cancel)$/i.test((x.textContent||'').trim())); if(b) b.click(); }); }");
        waitForAngular(600);
        boolean confirmed = false;
        for (int attempt = 0; attempt < 2 && !confirmed; attempt++) {
            page.evaluate("() => { window.__alerts=[]; const b=[...document.querySelectorAll('button')].find(x=>/fnCancelAppointment/.test(x.getAttribute('ng-click')||'')); if(b) b.click(); }");
            try {
                page.waitForSelector(".ng-confirm-box button", new Page.WaitForSelectorOptions().setTimeout(8000));
                confirmed = true;
            } catch (Exception e) {
                System.out.println("clickCancelAppointment: no confirm dialog (attempt " + (attempt + 1) + "). Alerts: " + capturedAlerts());
                waitForAngular(1200);
            }
        }
        if (!confirmed) return false;
        clickNgConfirmButton("Save");
        try {
            page.waitForSelector("textarea[ng-model='PatientData.appcancelreason']",
                    new Page.WaitForSelectorOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickCancelAppointment: cancellation form did not open");
            return false;
        }
        waitForAngular(500);
        return true;
    }

    /** Enter the cancellation Remark and select the Reason (by visible text, e.g. "Personal issues"). */
    public void enterCancellationDetails(String remark, String reason) {
        page.evaluate("(a) => { const rm=a[0], rs=a[1];"
                + "const ta=[...document.querySelectorAll('textarea')].find(x=>x.getAttribute('ng-model')==='PatientData.appcancelreason' && x.offsetParent!==null);"
                + "if(ta){ const c=angular.element(ta).controller('ngModel'); ta.value=rm; if(c){c.$setViewValue(rm);c.$render();} ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }"
                + "const sel=[...document.querySelectorAll('select')].filter(s=>s.getAttribute('ng-model')==='PatientData.appointmentreasonid' && s.offsetParent!==null)[0];"
                + "if(sel){ let i=[...sel.options].findIndex(o=>(o.text||'').trim().toLowerCase()===rs.toLowerCase()); if(i<0) i=[...sel.options].findIndex(o=>(o.text||'').toLowerCase().includes(rs.toLowerCase())); if(i<1) i=1;"
                + "  sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}} } }",
                java.util.List.of(remark, reason));
        waitForAngular(500);
    }

    /**
     * Click Save on the Cancellation form and return the success toast text. Resets the isSaving
     * flag (which can stay stuck after a failed save) and pins the toast into a persistent element
     * (#__pinnedToastBox) so it survives long enough to be screenshotted.
     */
    public String saveCancellationAndGetToast() {
        page.evaluate("() => {"
                + "const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnSaveAppointmentReason();' && x.offsetParent!==null);"
                + "if(b){ try{ const sc=angular.element(b).scope(); sc.$apply(function(){ let x=sc; for(let i=0;i<6&&x;i++){ if('isSaving' in x) x.isSaving=false; x=x.$parent; } }); }catch(e){} b.id='__cancelSaveBtn'; }"
                + "window.__cancelToast='';"
                + "if(window.__ctObs) window.__ctObs.disconnect();"
                + "window.__ctObs=new MutationObserver(()=>{ const el=document.querySelector('.toast-message') || document.querySelector('#toast-container .toast');"
                + "  if(el && el.textContent.trim() && !window.__cancelToast){ const msg=el.textContent.trim(); window.__cancelToast=msg;"
                + "    const box=document.createElement('div'); box.id='__pinnedToastBox';"
                + "    box.style.cssText='position:fixed;top:16px;right:16px;z-index:99999;background:#51a351;color:#fff;padding:15px 20px;border-radius:4px;font-family:sans-serif;font-size:15px;box-shadow:0 0 12px rgba(0,0,0,.3);min-width:260px;';"
                + "    box.innerHTML='<div style=\"font-weight:700;margin-bottom:4px;\">KPJ Portal</div><div>'+msg+'</div>'; document.body.appendChild(box); } });"
                + "window.__ctObs.observe(document.body,{childList:true,subtree:true}); }");
        try { page.locator("#__cancelSaveBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
        try {
            page.waitForFunction("() => window.__cancelToast && window.__cancelToast.length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("saveCancellationAndGetToast: no success toast observed. Alerts: " + capturedAlerts());
        }
        Object r = page.evaluate("() => window.__cancelToast || ''");
        return r == null ? "" : r.toString().trim();
    }

    // ---- Request MRD File -------------------------------------------------

    /** Row index (in the visible list) of the appointment chosen for the MRD request. */
    private int mrdRowIndex = -1;

    /** Number of visible appointment rows. */
    public int mrdRowCount() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Outcome of selecting a row and clicking "Request MRD File". */
    public static class MRDResult {
        /** "PatientName / AppointmentNo" of the selected appointment. */
        public String label;
        /** True if the file already existed and the report opened directly (see {@link #reportTab}). */
        public boolean fileExists;
        /** True if the "File is not generated ... create one?" confirm appeared (create flow needed). */
        public boolean needsCreate;
        /** The report tab (when the file already existed, or after re-requesting). */
        public Page reportTab;
        /** PNG bytes of the report tab, captured while it was foregrounded (may be null). */
        public byte[] reportPng;
    }

    /**
     * Screenshot the (PDF) report tab as PNG bytes: bring it to the front (a backgrounded PDF viewer
     * won't render for a screenshot), capture with a bounded timeout, then restore the primary page
     * to the front so subsequent primary-page steps still screenshot cleanly. Returns null on failure.
     */
    private byte[] captureReportPng(Page reportTab) {
        // The MRD report is a server-generated PDF; a Chromium PDF-viewer screenshot is a blank gray
        // page. Fetch the PDF bytes IN THE BROWSER (fetch() shares the session cookies — verified to
        // return application/pdf; APIRequestContext.get() throws a PlaywrightException for this
        // endpoint) as base64, then rasterize page 1 to PNG so the report shows the actual label.
        try {
            String url = reportTab.url();
            if (url != null && url.startsWith("http")) {
                Object b64 = reportTab.evaluate(
                        "async (u) => { const r = await fetch(u, {credentials:'include'});"
                                + " const buf = await r.arrayBuffer(); const bytes = new Uint8Array(buf);"
                                + " let bin=''; const chunk=0x8000;"
                                + " for (let i=0;i<bytes.length;i+=chunk) bin += String.fromCharCode.apply(null, bytes.subarray(i,i+chunk));"
                                + " return btoa(bin); }", url);
                if (b64 != null && !b64.toString().isEmpty()) {
                    byte[] pdf = java.util.Base64.getDecoder().decode(b64.toString());
                    boolean isPdf = pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F';
                    if (isPdf) {
                        byte[] pngFromPdf = com.kpj.core.PdfUtil.firstPageToPng(pdf);
                        if (pngFromPdf != null && pngFromPdf.length > 0) return pngFromPdf;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("captureReportPng: PDF fetch/rasterize failed (" + e.getMessage() + ")");
        }
        // Fallback: screenshot the tab directly (works for HTML reports, e.g. the Appointment Slip).
        byte[] png = null;
        try {
            reportTab.bringToFront();
            try { reportTab.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) {}
            reportTab.waitForTimeout(2500); // let the report render
            png = reportTab.screenshot(new Page.ScreenshotOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("captureReportPng: " + e.getMessage());
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) {}
        }
        return png;
    }

    /**
     * Select the appointment at {@code rowIndex} (fnSelectAppointment) and click "Request MRD File",
     * then detect which branch the app takes:
     * <ul>
     *   <li><b>File already exists</b> → the MRD report opens directly in a new tab
     *       ({@code fileExists=true}, {@code reportTab} set).</li>
     *   <li><b>No file yet</b> → the "create one?" confirm appears ({@code needsCreate=true}; the
     *       confirm is left open for {@link #confirmCreateMRD}).</li>
     * </ul>
     */
    public MRDResult selectRowAndRequestMRD(int rowIndex) {
        MRDResult res = new MRDResult();
        mrdRowIndex = rowIndex;
        int baseTabs = page.context().pages().size();
        Object label = page.evaluate("(i) => { const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null);"
                + "const row=rows[i]; if(!row) return null; row.click();"
                + "const s=angular.element(row).scope(); const a=(s&&s.app)?s.app:{};"
                + "return ((a.PatientName||a.patientname||'')+'').trim()+' / '+(a.AppointmentNo||a.appointmentno||''); }", rowIndex);
        res.label = label == null ? null : label.toString();
        waitForAngular(500);
        page.evaluate("() => { window.__alerts=[]; const b=[...document.querySelectorAll(\"button[ng-click='fnRequestMRDFile()']\")].find(x=>x.offsetParent!==null); if(b && !b.disabled) b.click(); }");
        // Poll for whichever comes first: the "create one?" confirm, or a report tab (file exists).
        for (int t = 0; t < 24; t++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => !![...document.querySelectorAll('.ng-confirm-box, .jconfirm')].find(m=>m.getBoundingClientRect().width>0 && /not generated|create one/i.test(m.textContent||''))"));
            if (confirm) { res.needsCreate = true; return res; }
            if (page.context().pages().size() > baseTabs) {
                res.fileExists = true;
                res.reportTab = page.context().pages().get(page.context().pages().size() - 1);
                res.reportPng = captureReportPng(res.reportTab);
                return res;
            }
            page.waitForTimeout(250);
        }
        return res; // neither branch fired
    }

    /**
     * Index of the first appointment whose patient has NO MRD file yet — detected by an empty
     * {@code app.mrdfileno} on the row scope (the file number is assigned per patient, so this is the
     * same condition that makes "Request MRD File" show the "create one?" confirm). Returns -1 if
     * every listed appointment already has a file. Reads the scope only — no clicks, no side effects.
     */
    public int findRowNeedingMRDFile() {
        Object r = page.evaluate("() => {"
                + "const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null);"
                + "for(let i=0;i<rows.length;i++){ const s=angular.element(rows[i]).scope(); const a=(s&&s.app)?s.app:{};"
                // skip patientid=0 (AutoBook) rows — Request MRD File does nothing for them
                + "  if((+(a.patientid||0))>0 && !((a.mrdfileno||'').toString().trim())) return i; }"
                + "return -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Click "Yes" on the "create one?" confirm and wait for the MRD Details modal to open. */
    public boolean confirmCreateMRD() {
        page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box, .jconfirm')].find(m=>m.getBoundingClientRect().width>0);"
                + "if(!box) return; const y=[...box.querySelectorAll('button')].find(b=>/^yes$/i.test(b.textContent.trim())); if(y) y.click(); }");
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /MRD Details/.test(x.textContent||'')); return !!m; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("confirmCreateMRD: MRD Details modal did not open");
            return false;
        }
        waitForAngular(600);
        return true;
    }

    /**
     * Fill the mandatory MRD Details fields — File Type, Patient Type, Allocation Location (Patient
     * Name arrives pre-filled from the appointment). Returns a summary of the values applied.
     */
    public String fillMRDDetails(String fileType, String patientType, String allocationLocation) {
        Object r = page.evaluate("(a) => { const ft=a[0], pt=a[1], al=a[2];"
                + "const setSel=(ng,text)=>{const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + "  if(!e) return '(missing)'; let i=[...e.options].findIndex(o=>o.text.trim().toLowerCase()===text.toLowerCase());"
                + "  if(i<0) i=[...e.options].findIndex(o=>o.text.trim().toLowerCase().includes(text.toLowerCase())); if(i<0) return '(no-opt)';"
                + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} return e.options[i].text.trim(); };"
                + "const a1=setSel('MRDAllocation.mrdfiletypeid',ft), a2=setSel('MRDAllocation.mrdpatienttypeid',pt), a3=setSel('MRDAllocation.allocationlocationid',al);"
                + "const nm=(([...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='MRDAllocation.name' && x.offsetParent!==null)||{}).value)||'';"
                + "return 'Patient Name='+nm+' | File Type='+a1+' | Patient Type='+a2+' | Allocation Location='+a3; }",
                java.util.List.of(fileType, patientType, allocationLocation));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Click Save (fnIUDAllocation) on the MRD Details modal and return the success toast text
     * ("File Allocated Successfully"). Uses a MutationObserver so the toast is captured even if it
     * fades quickly.
     */
    public String saveMRDDetailsAndGetToast() {
        page.evaluate("() => { window.__alerts=[]; window.__mrdToasts=[];"
                + "if(window.__mtObs) window.__mtObs.disconnect();"
                + "window.__mtObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const m=(el.textContent||'').trim();"
                + "  if(m && !window.__mrdToasts.includes(m)) window.__mrdToasts.push(m); }); });"
                + "window.__mtObs.observe(document.body,{childList:true,subtree:true});"
                + "const b=[...document.querySelectorAll('.modal button')].find(x=>x.offsetParent!==null && /^save$/i.test(x.textContent.trim())); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__mrdToasts||[]).some(a=>/allocated/i.test(a)) || (window.__alerts||[]).some(a=>/allocated/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("saveMRDDetailsAndGetToast: no allocation toast. Alerts: " + capturedAlerts());
        }
        Object r = page.evaluate("() => ((window.__mrdToasts||[]).find(a=>/allocated/i.test(a))) || ((window.__alerts||[]).find(a=>/allocated/i.test(a))) || (window.__mrdToasts||[]).join(' | ') || ''");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Re-select the same appointment row and click "Request MRD File" again. Now that the file
     * exists, the MRD report (frmMRD.aspx label) opens in a new tab. Returns an {@link MRDResult}
     * with {@code reportTab} and {@code reportPng} populated (both null/absent if none opened).
     */
    public MRDResult requestMRDFileAgainAndCaptureReport() {
        final int idx = mrdRowIndex;
        MRDResult res = new MRDResult();
        Page reportTab = null;
        try {
            reportTab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(20000),
                    () -> {
                        page.evaluate("(i) => { const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null); if(rows[i]) rows[i].click(); }", idx);
                        page.waitForTimeout(700);
                        page.evaluate("() => { window.__alerts=[]; const b=[...document.querySelectorAll(\"button[ng-click='fnRequestMRDFile()']\")].find(x=>x.offsetParent!==null); if(b && !b.disabled) b.click(); }");
                        page.waitForTimeout(1500);
                    });
        } catch (Exception e) {
            System.out.println("requestMRDFileAgainAndCaptureReport: no report tab opened (" + e.getMessage() + ")");
        }
        res.reportTab = reportTab;
        if (reportTab != null) res.reportPng = captureReportPng(reportTab);
        return res;
    }

    // ---- Return MRD File --------------------------------------------------

    /**
     * Index of the first appointment whose patient already HAS an MRD file — detected by a non-empty
     * {@code app.mrdfileno} on the row scope. That is the row eligible for "Return MRD File" (you can
     * only return a file that was issued). Returns -1 if no listed appointment has a file yet. Reads
     * the scope only — no clicks, no side effects. (Opposite of {@link #findRowNeedingMRDFile}.)
     */
    public int findRowWithMRDFile() {
        Object r = page.evaluate("() => {"
                + "const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null);"
                + "for(let i=0;i<rows.length;i++){ const s=angular.element(rows[i]).scope(); const a=(s&&s.app)?s.app:{};"
                + "  if((+(a.patientid||0))>0 && ((a.mrdfileno||'').toString().trim())) return i; }"
                + "return -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /**
     * Select the appointment at {@code rowIndex} (fnSelectAppointment) and click the footer
     * <b>"Return MRD File"</b> button, then wait for the Return-MRD popup to open. Returns the
     * "PatientName / AppointmentNo" label of the selected row, or null if the popup did not open.
     *
     * <p>The button is matched by its {@code ng-click} (fnReturnMRDFile) with a visible-text fallback
     * ("Return MRD File"); the popup is any newly-visible modal mentioning "Return" + MRD/file.</p>
     */
    public String selectRowAndReturnMRD(int rowIndex) {
        Object label = page.evaluate("(i) => { const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null);"
                + "const row=rows[i]; if(!row) return null; row.click();"
                + "const s=angular.element(row).scope(); const a=(s&&s.app)?s.app:{};"
                + "return ((a.PatientName||a.patientname||'')+'').trim()+' / '+(a.AppointmentNo||a.appointmentno||''); }", rowIndex);
        waitForAngular(500);
        page.evaluate("() => { window.__alerts=[];"
                + "let b=[...document.querySelectorAll('button')].find(x=>/fnReturnMRDFile/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + "if(!b) b=[...document.querySelectorAll('button')].find(x=>/return\\s*mrd\\s*file/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + "if(b && !b.disabled) b.click(); }");
        try {
            // The "MRDReturn" modal (From/To Department, User, Remark; OK=fnIUDReturn()) — verified live.
            page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.getBoundingClientRect().width>0 && (/MRDReturn/i.test(m.textContent||'') || m.querySelector(\"button[ng-click='fnIUDReturn()']\")))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("selectRowAndReturnMRD: Return MRD popup did not open. Alerts: " + capturedAlerts());
            return null;
        }
        waitForAngular(600);
        return label == null ? null : label.toString();
    }

    /**
     * Fill the Return-MRD ("MRDReturn") popup — selectors verified live. Sets From Department
     * ({@code MRDReturn.fromdepartmentid}) and To Department ({@code MRDReturn.todepartmentid}) to their
     * first real option (each defaults to "-Select-"), and the Remark ({@code MRDReturn.remark})
     * textarea. The User ({@code MRDReturn.userid}) arrives pre-filled with the logged-in user, so it is
     * left untouched. Returns a short summary of the values applied.
     */
    public String fillReturnMRDDetails(String remark) {
        Object r = page.evaluate("(rm) => {"
                + "const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /MRDReturn/i.test(x.textContent||'')); if(!m) return '(no-popup)';"
                + "const setSel=ng=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(missing)';"
                + "  if(e.selectedIndex>0) return (e.options[e.selectedIndex].text||'').trim();"
                + "  const idx=[...e.options].findIndex((o,i)=>i>0 && !/^-*\\s*select\\s*-*$/i.test(o.text||'')); if(idx<0) return '(no-opt)';"
                + "  e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} return (e.options[idx].text||'').trim(); };"
                + "const from=setSel('MRDReturn.fromdepartmentid'); const to=setSel('MRDReturn.todepartmentid');"
                + "const uEl=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='MRDReturn.userid');"
                + "const user=uEl?((uEl.options[uEl.selectedIndex]||{}).text||'').trim():'';"
                + "const ta=[...m.querySelectorAll('textarea')].find(x=>x.getAttribute('ng-model')==='MRDReturn.remark');"
                + "if(ta){ const c=angular.element(ta).controller('ngModel'); ta.value=rm; if(c){c.$setViewValue(rm);c.$render();} ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }"
                + "return 'From Dept='+from+' | To Dept='+to+' | User='+user+' | Remark='+rm; }", remark);
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>OK</b> ({@code fnIUDReturn()}) on the MRDReturn popup and return the success toast text
     * (verified live: <b>"File Return successfully."</b>). Captures the toast via a MutationObserver so
     * a fast-fading toast is still recorded, and falls back to any intercepted JAlert.
     */
    public String confirmReturnMRDAndGetToast() {
        page.evaluate("() => { window.__alerts=[]; window.__retToasts=[];"
                + "if(window.__rtObs) window.__rtObs.disconnect();"
                + "window.__rtObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const m=(el.textContent||'').trim(); if(m && !window.__retToasts.includes(m)) window.__retToasts.push(m); }); });"
                + "window.__rtObs.observe(document.body,{childList:true,subtree:true});"
                + "const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /MRDReturn/i.test(x.textContent||''));"
                + "const scope=m||document;"
                + "const b=[...scope.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDReturn()' && x.offsetParent!==null)"
                + "  || [...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^ok$/i.test((x.textContent||'').trim()));"
                + "if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__retToasts||[]).some(a=>/return/i.test(a)) || (window.__alerts||[]).some(a=>/return/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("confirmReturnMRDAndGetToast: no toast observed. Alerts: " + capturedAlerts());
        }
        Object r = page.evaluate("() => ((window.__retToasts||[]).find(a=>/return/i.test(a))) || ((window.__alerts||[]).find(a=>/return/i.test(a))) || (window.__retToasts||[])[0] || ''");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Registration (routes to VisitScreen) -----------------------------

    /**
     * Click the footer <b>"Registration"</b> button for the selected appointment; it routes to the
     * Registration / VisitScreen. The full registration flow is covered by TC02 (RegistrationTest);
     * here we only verify the tab opens the registration screen. Returns true if it routed there.
     */
    /**
     * Select the first REAL appointment (patientid&gt;0 with an MRN) whose patient name is not already in
     * {@code tried}, and return that patient name (the tried-key). Returns null if every registrable
     * patient has been tried. Used to iterate registration onto the next patient when one is gated
     * (e.g. an incomplete next-of-kin blocks the save).
     */
    public String selectRegistrableAppointmentExcluding(java.util.Collection<String> tried) {
        // Wait for the appointment rows to actually render — the grid loads a beat after Search, and
        // selecting too early sees zero rows (which produced the empty candidate list).
        try {
            page.waitForFunction("() => document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\").length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("selectRegistrableAppointmentExcluding: no appointment rows rendered"); }
        waitForAngular(600);
        // Pick ANY not-yet-tried candidate: prefer a real patient (patientid>0 + MRN), then any
        // patientid>0, then any row at all. Attempt it and let the caller skip on the real
        // "visit already exists / Error" outcome.
        Object r = page.evaluate("(triedArr) => {"
                + "const tried=new Set((triedArr||[]).map(s=>String(s).toLowerCase()));"
                + "const rows=[...document.querySelectorAll(\"tr[ng-click*='fnSelectAppointment']\")].filter(x=>x.offsetParent!==null);"
                + "const hasMRN=a=>['mrno','MRNO','MRN','mrnno'].some(k=>((a[k]||'')+'').trim().length>0);"
                + "const scoped=rows.map(row=>({s:angular.element(row).scope()})).filter(o=>o.s && o.s.app);"
                + "const nameOf=a=>((a.PatientName||a.patientname||'')+'').trim();"
                + "const notTried=o=>{ const nm=nameOf(o.s.app); return nm && !tried.has(nm.toLowerCase()); };"
                + "const log=[]; scoped.forEach(o=>{ if(notTried(o)){ const a=o.s.app; log.push(nameOf(a)+' pid='+(a.patientid||0)+' vs='+(a.visitstatus||0)); } });"
                + "window.__regCandLog=log;"
                + "let chosen = scoped.find(o=>notTried(o) && (+(o.s.app.patientid||0))>0 && hasMRN(o.s.app))"
                + "          || scoped.find(o=>notTried(o) && (+(o.s.app.patientid||0))>0)"
                + "          || scoped.find(o=>notTried(o));"
                + "if(!chosen) return null;"
                + "const a=chosen.s.app; chosen.s.$apply(function(){ chosen.s.fnSelectAppointment(a, chosen.s.$index); });"
                + "return nameOf(a); }", new java.util.ArrayList<>(tried));
        Object log = page.evaluate("() => (window.__regCandLog||[]).join(' | ')");
        System.out.println("registrable candidates: " + log);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    public boolean clickRegistrationTab() {
        // The footer "Registration" action is a BUTTON with ng-click="fnPatientRegistration()" — it
        // carries the SELECTED patient into the VisitScreen (pre-filled). NB: there is also a top-menu
        // link <a href="#/VisitScreen">Registration</a> that opens a BLANK VisitScreen — do NOT match by
        // text (it hits the menu link first); match the footer button's ng-click.
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnPatientRegistration();' && x.offsetParent!==null); if(b) b.click(); }");
        try {
            // Wait on location.hash, NOT a URL glob: the app inserts a cache-buster BEFORE the hash
            // (…/?_cb=1.2.16#/VisitScreen), which no "**/#/VisitScreen" glob ever matches — the route
            // succeeds but waitForURL times out and the whole section is skipped.
            page.waitForFunction("() => /#\\/VisitScreen/i.test(location.hash || location.href)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("clickRegistrationTab: did not route to the VisitScreen (" + e.getMessage() + ")");
            return false;
        }
        waitForAngular(2000); // let the existing patient's details pre-fill
        installHelpers();
        return true;
    }

    /** Outcome of saving a registration launched from the Appointment List. */
    public static class RegSaveResult {
        /** The Registration Report tab (PDF slip / patient sticker), if the save succeeded. */
        public Page reportTab;
        /** PNG bytes of the Registration Report (rasterized from the PDF), or null. */
        public byte[] reportPng;
        /** The Consent form tab (Patient Registration Form on nhisformstest), if it opened. */
        public Page consentTab;
        /** PNG bytes of the Consent form (full-page HTML screenshot), or null. */
        public byte[] consentPng;
        /** Full-page screenshot after the Patient + Correspondence sections are filled. */
        public byte[] patientPng;
        /** Full-page screenshot after the Payor Information section is filled. */
        public byte[] payorPng;
        /** Full-page screenshot after the Next of Kin section is filled. */
        public byte[] kinPng;
        /** True if the in-app "Consent Details" (PERSONAL DATA NOTICE &amp; CONSENT) modal appeared after Save. */
        public boolean consentModalShown;
        /** True if the Patient Registration Form consent tab was found AND submitted successfully. */
        public boolean consentSubmitted;
        /** Success toast, or the blocking message (e.g. an incomplete-NOK warning) if the save was gated. */
        public String message = "";
        /** True if the registration saved and a report/consent artifact appeared. */
        public boolean saved;
        /**
         * Why the save failed, in plain words — set when the cause is known:
         * <ul>
         *   <li>"no doctors in the drop down" — no department/sub-department offered a single doctor;</li>
         *   <li>"even after adding the details it still says: …" — every mandatory field was filled and the app
         *       still asked for one.</li>
         * </ul>
         */
        public String failReason = "";
    }

    /**
     * Save the (pre-filled) registration opened from the Appointment List and capture the Registration
     * Report. Sets the Queue No, clicks Save ({@code IUDRegistration()}), then clicks OK through the
     * informational warnings this flow raises for an existing patient — <i>"Patient has previous
     * unclosed episode"</i> and any NOK reminder — and, when the <i>"Do you want to Save"</i> confirm
     * appears, confirms it and grabs the Registration Report tab that opens. If the save is instead
     * gated (e.g. <i>"Please update the mandatory details in NOK for &lt;name&gt;"</i> blocks it), the
     * blocking message is returned in {@link RegSaveResult#message} with {@code saved=false}.
     */
    /**
     * Ensure the Visit cascade is actually committed before Save: <b>Department → Sub Department → Doctor</b>,
     * then Visit Type.
     *
     * <p>Each of these reloads ASYNCHRONOUSLY and clears the ones below it, so a one-shot read picks nothing and
     * the model stays empty — which is why Save answered "Please Select SubDepartment !" and then
     * "Please Select Doctor!". Every step here polls for a real option before selecting, and reads the bound model
     * back, since assigning a value that is not in the list is silently discarded by Angular.</p>
     *
     * <p>A field that already holds a value is left alone — the appointment pre-fills most of them.</p>
     */
    public String ensureVisitCascade() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery||window.$; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const sel=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + " const has=e=>e && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " const cur=e=>{ const o=e&&e.options[e.selectedIndex]; const t=o?norm(o.textContent):''; return (t && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const ensure=async(ng)=>{ let e=sel(ng);"
                + "   if(e && cur(e)) return 'kept:'+cur(e);"                       // already set by the pre-fill
                + "   for(let k=0;k<20;k++){ e=sel(ng); if(has(e)) break; await sleep(400); }"
                + "   if(!e) return '(no field)'; if(!has(e)) return '(no options)';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(x){}}"
                // Read the MODEL back — a changed <select> whose model never took is the failure mode here.
                + "   for(let k=0;k<10;k++){ await sleep(300); const c=A.element(e).controller('ngModel');"
                + "     if(c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='') return norm(e.options[i].textContent); }"
                + "   return norm(e.options[i].textContent)+'(model?)'; };"
                + " const out={};"
                + " out.Dept=await ensure('Visit.DepartmentID'); await sleep(1200);"      // Department refills Sub Dept
                + " out.SubDept=await ensure('Visit.SubDepartmentID'); await sleep(1200);" // Sub Dept refills Doctor
                + " out.Doctor=await ensure('Visit.DoctorID'); await sleep(600);"
                + " out.VisitType=await ensure('Visit.VisitTypeID');"
                + " resolve(JSON.stringify(out)); })");
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Mandatory fields left empty on the VisitScreen at the last save attempt (empty when all were filled). */
    public String lastEmptyMandatory = "";
    /** Plain-words reason the last save failed, when it is known. See {@link RegSaveResult#failReason}. */
    public String lastFailReason = "";
    /** Nationality on the VisitScreen at the last save attempt — drives whether a passport is expected. */
    public String lastNationality = "";

    /** The Nationality currently selected on the registration form ("" when unset). */
    public String currentNationality() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.NationalityID');"
                + " if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + " return (t && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Every mandatory (*) control on the VisitScreen that is still EMPTY, with its label and ng-model.
     *
     * <p>Without this the flow only learns about one missing field per run — the app validates in order and the
     * toast names just the first ("Please Select SubDepartment !", then "Please Select Doctor!"), so each fix
     * costs a full run to reveal the next. This lists them all in one pass.</p>
     */
    public String describeEmptyMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const out=[];"
                + " [...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button' && e.type!=='checkbox' && e.type!=='radio')"
                + "  .filter(e=>(e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0)"
                + "  .forEach(e=>{ const lab=labelOf(e);"
                + "    const mandatory=/\\*/.test(lab) || e.hasAttribute('required') || e.hasAttribute('ng-required')"
                + "      || !!(e.closest('.form-group,div')||{}).querySelector?.('.arstrik');"
                + "    if(!mandatory) return;"
                + "    let v=(e.value||'').trim();"
                + "    if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; v=o?norm(o.textContent):''; if(/^-*\\s*select\\s*-*$/i.test(v)) v=''; }"
                + "    if(!v) out.push(lab.replace(/\\*/g,'').trim()+'['+(e.getAttribute('ng-model')||'?')+']'); });"
                + " return out.join(', '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Re-check every mandatory field right before Save and fill whatever is STILL empty — the same detection
     * {@link #describeEmptyMandatory()} uses, but acting on it instead of only reporting it. Filling everything
     * up front (Patient/Kin/Payor/Visit) still leaves gaps for an existing patient whose own record is missing
     * something the app requires (e.g. Email, Gender) — those never get backfilled because nothing upstream owns
     * them, and Save then rejects on "Please fill in all the mandatory fields!" for a field never touched.
     *
     * <p>Only backfills what can be safely guessed:</p>
     * <ul>
     *   <li>SELECT left on "-Select-" → first real option (same fallback used throughout this suite).</li>
     *   <li>Email-shaped input → a generated address.</li>
     *   <li>Mobile/Phone-shaped input → a valid 10-digit number.</li>
     * </ul>
     * <p>Anything else (dates, free text whose correct value can't be guessed — e.g. NRIC/Passport, which is
     * already handled by nationality above) is left alone and reported, rather than risk writing a wrong value
     * into a field the app might not treat as freely as a plain string.</p>
     *
     * @return "" if nothing was left empty, else what was filled and — if anything couldn't be — what's still empty.
     */
    public String backfillStillEmptyMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const fixed=[], stillEmpty=[];"
                + " [...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button' && e.type!=='checkbox' && e.type!=='radio')"
                + "  .filter(e=>(e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0)"
                + "  .forEach(e=>{ const lab=labelOf(e); const ng=e.getAttribute('ng-model')||'?';"
                + "    const mandatory=/\\*/.test(lab) || e.hasAttribute('required') || e.hasAttribute('ng-required')"
                + "      || !!(e.closest('.form-group,div')||{}).querySelector?.('.arstrik');"
                + "    if(!mandatory) return;"
                + "    let v=(e.value||'').trim();"
                + "    if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; v=o?norm(o.textContent):''; if(/^-*\\s*select\\s*-*$/i.test(v)) v=''; }"
                + "    if(v) return;"
                + "    const label=lab.replace(/\\*/g,'').trim()+'['+ng+']';"
                + "    if(e.tagName==='SELECT'){"
                + "      const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && (o.text||'').trim() && !/^-*\\s*select\\s*-*$/i.test((o.text||'').trim()));"
                + "      if(i<0){ stillEmpty.push(label+' (no real option to fall back to)'); return; }"
                + "      e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}}"
                + "      fixed.push(label+' -> '+e.options[i].text.trim()); return; }"
                + "    const isEmail=/email/i.test(ng)||/email/i.test(lab); const isMobile=/mobile|phone/i.test(ng)||/mobile|phone/i.test(lab);"
                + "    let val=null;"
                + "    if(isEmail) val='autofill'+Math.floor(Math.random()*90000+10000)+'@example.com';"
                + "    else if(isMobile) val='1'+String(Math.floor(Math.random()*900000000+100000000));"
                + "    if(val==null){ stillEmpty.push(label+' (no safe default — left empty)'); return; }"
                + "    const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();}"
                + "    e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "    fixed.push(label+' -> '+val); });"
                + " return {fixed, stillEmpty}; }");
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        waitForAngular(500);
        java.util.List<?> fixed = (java.util.List<?>) m.get("fixed");
        java.util.List<?> stillEmpty = (java.util.List<?>) m.get("stillEmpty");
        if ((fixed == null || fixed.isEmpty()) && (stillEmpty == null || stillEmpty.isEmpty())) return "";
        StringBuilder sb = new StringBuilder();
        if (fixed != null && !fixed.isEmpty()) sb.append("Filled: ").append(fixed);
        if (stillEmpty != null && !stillEmpty.isEmpty()) sb.append(sb.length() > 0 ? " | " : "").append("STILL EMPTY: ").append(stillEmpty);
        return sb.toString();
    }

    public RegSaveResult saveRegistrationAndCaptureReport() {
        RegSaveResult res = new RegSaveResult();
        int baseTabs = page.context().pages().size();
        // Held at method scope so the SAVE below can reuse the very same RegistrationPage/profile the fill used —
        // the save is delegated to RegistrationPage instead of being re-implemented here.
        com.kpj.pages.Op_page.RegistrationPage reg = null;
        com.kpj.pages.Op_page.RegistrationPage.PatientProfile profile = null;

        // The existing patient's details pre-fill ASYNCHRONOUSLY; saving too early is rejected with
        // "Please Select Patient Source!" / "Please enter Address!". Wait for the pre-fill to settle
        // (Patient Source populated), then backfill ONLY the mandatory fields that are still empty so a
        // sparsely-recorded patient can still save. Never overwrite a value the pre-fill already set.
        try {
            page.waitForFunction(
                    "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Visit.PatientSourceID'); return e && e.selectedIndex>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { /* backfill below */ }
        waitForAngular(600);
        nudgePage(); // clear any post-pre-fill render glitch before reading/filling field state
        // The "Patient has previous unclosed episode" (and similar) confirm pops up EARLY — as soon as
        // the Patient Information pre-fills — and while it is up it BLOCKS the payor row-click and kin
        // edits (which is why the payor form stays unfilled). Dismiss it now, before those steps.
        dismissInfoAlerts();
        page.evaluate("() => {"
                + "const setSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                + "  const cur=((e.options[e.selectedIndex]||{}).text||'').trim(); if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return;"
                + "  let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>(o.text||'').toLowerCase().includes(txt.toLowerCase())); if(i<1) return;"
                + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} };"
                + "const setInp=(ng,val)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e || (e.value||'').trim()) return;"
                + "  const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + "setSel('Visit.PatientSourceID','External'); setSel('Visit.EncounterTypeID','Outpatient');"
                // Patient Information mandatory dropdowns — backfill only if the pre-fill left them empty
                // (e.g. Race was flagged as not selected). setSel is select2-aware and 'only if empty'.
                // IDENTIFICATION — this block previously set only the KIN's id, never the PATIENT's, so
                // Identification Type and NRIC stayed empty. Filled the same way OP Registration does it:
                // Identification Type FIRST (the NRIC input stays disabled until it is set), then the NRIC —
                // choosing the maxlength=12 input, since the screen renders more than one NationalId field.
                // NATIONALITY DECIDES IDENTIFICATION — read (and default) it FIRST, then branch: Malaysian (or
                // unset, which setSel below then defaults to Malaysian) fills NRIC and leaves Passport alone;
                // any OTHER nationality fills Passport + Passport Expiry and leaves NRIC alone. The previous
                // version filled NRIC unconditionally for every nationality and only ADDED Passport for Indian,
                // so an Indian patient ended up with both, and every other foreign nationality got NRIC instead
                // of Passport.
                + "setSel('Registration.NationalityID','Malaysian');"
                + "const natSel=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.NationalityID');"
                + "const nat=natSel ? ((natSel.options[natSel.selectedIndex]||{}).text||'').trim() : '';"
                + "const isMalaysian=!nat || /malaysia/i.test(nat);"
                + "if(isMalaysian){"
                + "  setSel('Registration.ICCardTypeID','New IC');"
                + "  const idt=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID');"
                + "  if(idt){ const ic=angular.element(idt).controller('ngModel');"
                + "    if(!ic || ic.$modelValue==null || ic.$modelValue===''){ const i=[...idt.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.text||'').trim()));"
                + "      if(i>0){ idt.selectedIndex=i; idt.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(idt).trigger('change');}catch(err){}} } } }"
                + "  const nric=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12')"
                + "    || [...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.offsetParent!==null);"
                + "  if(nric && !(nric.value||'').trim()){ const nc=angular.element(nric).controller('ngModel'); nric.value='900101145523'; if(nc){nc.$setViewValue('900101145523');nc.$render();}"
                + "    nric.dispatchEvent(new Event('input',{bubbles:true})); nric.dispatchEvent(new Event('change',{bubbles:true})); }"
                + "  console.log('identification: Malaysian — NRIC filled, passport left empty');"
                + "} else {"
                // Passport No. is really Registration.FamilyName on this screen once nationality flips the
                // Passport box on (see RegistrationPage.PatientProfile.randomForeign's Javadoc) — same field
                // for every non-Malaysian nationality, not just Indian. Expiry is required alongside it
                // ("Please Enter Passport Expiry Date.!"). NRIC is deliberately left untouched.
                + "  setSel('Registration.ICCardTypeID','Passport');"
                + "  const setIf=(ng,val)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + "    if(!e || (e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();}"
                + "    e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + "  setIf('Registration.FamilyName','A1234567');"                       // Passport No
                + "  const d=new Date(); const p2=n=>('0'+n).slice(-2);"
                + "  setIf('Registration.PassportExpirydate', p2(d.getDate())+'/'+p2(d.getMonth()+1)+'/'+(d.getFullYear()+5));"
                + "  console.log('identification: nationality \"'+nat+'\" — passport + expiry filled, NRIC left empty');"
                + "}"
                + "setSel('Registration.RaceID','Malay'); setSel('Registration.ReligionID','Islam'); setSel('Registration.MaritalStatusID','Single');"
                + "setSel('Registration.BloodGroupID','O'); setSel('Registration.IncomeCategoryID','No Income');"
                // Gender was the one field NOTHING upstream ever touched — the app's own Save rejection always
                // named it specifically ("Gender*=\"\""), never any of the others in this block, confirming it is
                // the real blocker for a patient record with no Gender on file (e.g. an ER placeholder patient).
                + "setSel('Registration.GenderID','Male');"
                + "setInp('Registration.ResAddress','15 Jalan Damai'); setInp('Registration.ResHouseNo','15'); setInp('Registration.ResStreet','Jalan Damai'); setInp('Registration.ResPinCode','50000');"
                // Mobile must be REPLACED whenever it is not EXACTLY 10 digits — too short ("Mobile number must be
                // at least 10 digits!") AND too long ("Mobile number cannot exceed 10 digits!"). The previous
                // check only caught "too short" (length<10), so an existing patient with an 11+ digit number on
                // record sailed through untouched and failed Save on the "cannot exceed" message instead.
                + "const mob=document.querySelector('#txtMobileNo'); if(mob){ const cur=(mob.value||'').replace(/\\D/g,''); if(cur.length!==10){ const mc=angular.element(mob).controller('ngModel'); mob.value='1234567890'; if(mc){mc.$setViewValue('1234567890');mc.$render();} mob.dispatchEvent(new Event('input',{bubbles:true})); mob.dispatchEvent(new Event('change',{bubbles:true})); } }"
                // Phone No (Registration.ResiNo) is a SEPARATE field from Mobile No — "Phone number must be at
                // least 9 digits!" fires when it is empty or short, same class of gap as Mobile.
                + "const phn=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='Registration.ResiNo' && x.offsetParent!==null);"
                + "if(phn){ const cur=(phn.value||'').replace(/\\D/g,''); if(cur.length<9){ const pc=angular.element(phn).controller('ngModel'); phn.value='123456789'; if(pc){pc.$setViewValue('123456789');pc.$render();} phn.dispatchEvent(new Event('input',{bubbles:true})); phn.dispatchEvent(new Event('change',{bubbles:true})); } }"
                // Email — same "only if empty" rule as everything else here, filled directly rather than relying
                // on the generic mandatory-field scan later (that scan's visibility filter is unreliable for a
                // patient whose Correspondence Details fields are collapsed/hidden at the moment it runs).
                + "const eml=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')==='Registration.Email' && x.offsetParent!==null);"
                + "if(eml && !(eml.value||'').trim()){ const ec=angular.element(eml).controller('ngModel'); const v='autofill'+Math.floor(Math.random()*90000+10000)+'@example.com'; eml.value=v; if(ec){ec.$setViewValue(v);ec.$render();} eml.dispatchEvent(new Event('input',{bubbles:true})); eml.dispatchEvent(new Event('change',{bubbles:true})); }"
                + "const ins=[...document.querySelectorAll('input[ng-model=\"Insurer\"]')].find(x=>x.value==='1'); if(ins && !ins.checked) ins.click();"
                + " }");
        waitForAngular(600);
        res.patientPng = sectionShot(); // Patient Information + Correspondence Details filled

        // Payor Information: the "Payor Information" accordion header (ng-click="FillSponserDropDown()")
        // loads the payor defaults for the selected MRN — a grid row "Self / SELFPAY CASH CUSTOMER /
        // Priority 1". Click it, wait for that default row to load, then ensure Payor = Self (Insurer=1)
        // and any still-empty mandatory payor select is set.
        dismissInfoAlerts(); // clear any lingering "unclosed episode" confirm before touching the payor
        page.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>x.getAttribute('ng-click')==='FillSponserDropDown();' && x.offsetParent!==null); if(a) a.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('table')].some(t=>/PAYOR MODE|PRICING POLICY/i.test((t.querySelector('thead')||{}).innerText||'') && /self|selfpay|cash/i.test((t.querySelector('tbody')||{}).innerText||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { System.out.println("payor default row did not load within timeout"); }
        waitForAngular(500);
        // Invoke the payor grid row's handler — that is what auto-fills all the payor form details for
        // the MRN (Payor Mode=Self, Payor/Pricing Policy=SELFPAY CASH CUSTOMER, Priority, GL amounts).
        // Clicking only the accordion header does NOT populate them (the form stays "Insurance Group").
        // Call EditSponser($index) directly on the row's scope (verified live to set Payor Mode=Self);
        // fall back to a DOM click if the scope call isn't available.
        Object payorFilled = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/PAYOR MODE|PRICING POLICY/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return 'no-table'; const row=t.querySelector('tbody tr'); if(!row) return 'no-row';"
                + " const cell=[...row.querySelectorAll('td')].find(td=>/self|selfpay|cash/i.test(td.textContent||'')) || row.querySelector('td') || row;"
                + " const sc=angular.element(cell).scope(); if(sc && typeof sc.EditSponser==='function'){ sc.$apply(function(){ sc.EditSponser(sc.$index); }); return 'EditSponser'; } cell.click(); return 'domclick'; }");
        System.out.println("payor row fill via: " + payorFilled);
        waitForAngular(700);
        Object payorMode = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypeid'); return e?((e.options[e.selectedIndex]||{}).text||'').trim():'(no-el)'; }");
        System.out.println("Payor Mode after row fill: " + payorMode);
        res.payorPng = sectionShot(); // Payor Information filled
        page.evaluate("() => {"
                + "const ins=[...document.querySelectorAll('input[ng-model=\"Insurer\"]')].find(x=>x.value==='1'); if(ins && !ins.checked) ins.click();"
                + "const setSelIfEmpty=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return; const cur=((e.options[e.selectedIndex]||{}).text||'').trim(); if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return; let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(i<1) i=[...e.options].findIndex((o,ix)=>ix>0 && !/select/i.test(o.text||'')); if(i<1) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} };"
                + "setSelIfEmpty('Registration.receivabletypeid','Self'); setSelIfEmpty('Registration.PayerTypeId','Self'); setSelIfEmpty('Registration.KinReceivableID','Self');"
                + " }");
        waitForAngular(500);

        // Clear the "Please update the mandatory details in NOK for <name>" gate before saving.
        dismissInfoAlerts(); // ensure no alert blocks the kin edit form
        ensureNextOfKin();
        res.kinPng = sectionShot(); // Next of Kin filled

        // Correspondence City is mandatory and existing appointment patients often have it blank → the save is
        // gated with "Please Select City!". The City list loads ASYNC off the State, so iterate States until one
        // actually yields a City option.
        System.out.println("ensureCity: " + ensureCity());

        // Visit Information: reuse OP REGISTRATION's own routine rather than a parallel implementation. This is
        // the SAME VisitScreen, and RegistrationPage.fillVisitInformation() already solves the cascade that was
        // rejecting the save one field at a time ("Please Select SubDepartment !", then "Please Select Doctor!"):
        // it sets Department first, waits for the doctor list to reload, picks a Sub Department that still leaves
        // a selectable Doctor, and re-asserts Visit Type LAST because the cascade clears it.
        try {
            reg = new com.kpj.pages.Op_page.RegistrationPage(page);
            profile = com.kpj.pages.Op_page.RegistrationPage.PatientProfile.random();
            com.kpj.pages.Op_page.RegistrationPage.PatientProfile p = profile;
            // OPEN EACH SECTION BEFORE FILLING IT. Arriving from the Appointment List, the VisitScreen renders
            // every accordion COLLAPSED (Patient Information / Payor Information / Visit Information — only the
            // footer bar shows). A collapsed section's inputs are in the DOM but not visible, so fill() waits the
            // full timeout and throws, and every field then reads back empty. Earlier this was mis-diagnosed as
            // "the patient section is read-only for an existing patient" — it is not read-only, it is CLOSED.
            expandSection("Patient Information");
            // DO NOT re-fill the patient here. Registering an EXISTING patient auto-populates Patient and
            // Correspondence from their record; the job on this screen is to REVIEW those and complete what is
            // missing, not to replace them. Running OP's fillPatientInformation/fillCorrespondence/fillOtherDetails
            // overwrote the real patient with a generated profile AND threw part-way (a 30s fill timeout), which
            // killed Payor and Visit and left the save asking for "Case Category" / "Doctor".
            // Only the genuinely-empty mandatory fields are backfilled — Gender now included above alongside
            // Race/Religion/Marital/Blood/Income (it was the one field nothing upstream ever touched, and the
            // app's own Save rejection always named it specifically) — plus Kin, Payor and Visit below.
            System.out.println("saveRegistration: existing patient — keeping the auto-filled Patient/Correspondence "
                    + "details and only completing what is empty");
            // NEXT OF KIN IS MANDATORY FOR AN EXISTING PATIENT TOO — the FSD lists it as a required section for
            // both the new and the registered flows, and the save rejects with "Please Fill Kin Details!" without
            // it. So it must NOT be skipped along with the read-only patient section: an existing patient often
            // has no kin on record, and the kin form stays editable even when the patient fields do not.
            try {
                // The kin grid lives inside the Patient Information panel, already opened above — do NOT call
                // expandSection("Patient Information") again here. Its "is it open" check is unreliable (it can
                // false-negative on an already-open panel) and a redundant click toggles the accordion closed and
                // back open — live-confirmed via a checkpoint diagnostic that Gender's Angular model holds its
                // value correctly through every fill step UNTIL this exact toggle, right after which the app's own
                // Save validation rejects it as empty. AngularJS accordion bodies commonly use ng-if, which
                // destroys and recreates the panel's child scope on each collapse/expand — re-toggling an
                // already-open panel is exactly what orphans a value like Gender from the scope Save reads.
                boolean kin = reg.addNextOfKin(p);
                System.out.println("saveRegistration: kin ensured = " + kin);
            } catch (Exception e) {
                System.out.println("saveRegistration: kin fill failed - " + e.getMessage());
            }
            // Payor and Visit are what this screen actually has to supply — run them INDEPENDENTLY so a failure in
            // one cannot silently swallow the other (that is what produced the "everything is empty" report).
            expandSection("Payor Information");
            try { System.out.println("saveRegistration: payor self = " + reg.selectPayorSelf()); }
            catch (Exception e) { System.out.println("saveRegistration: payor fill failed - " + e.getMessage()); }
            expandSection("Visit Information");
            reg.fillVisitInformationRequiringSubDept();
            // Walking Department/Sub-Department to find one with a doctor (ensureDoctorByChangingDepartment) can
            // clear OTHER Visit fields that depend on Department — confirmed live: Encounter Type, Cash Counter,
            // Location, Registration Department and Admission Source all come back empty after that walk runs,
            // even though Encounter Type was explicitly set earlier. The generic mandatory-field scan below
            // can't see or fix these itself: it matches by label text across input/select/textarea, and for a
            // select2 widget that sweep can land on the invisible "select2-focusser" decoy input next to the
            // real <select> (no ng-model, always empty) instead of the real field — the exact confusion that
            // made Gender look unfixable earlier. Go straight at the real <select> elements by ng-model prefix
            // instead: every Visit.* select still on "-Select-" gets its first real option.
            String visitBackfilled = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const fixed=[];"
                    + " [...document.querySelectorAll('select')].filter(e=>(e.getAttribute('ng-model')||'').indexOf('Visit.')===0).forEach(e=>{"
                    + "   const ng=e.getAttribute('ng-model'); const cur=norm((e.options[e.selectedIndex]||{}).text);"
                    + "   if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return;"
                    + "   const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && (o.text||'').trim() && !/^-*\\s*select\\s*-*$/i.test((o.text||'').trim()));"
                    + "   if(i<0) return;"
                    + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}}"
                    + "   fixed.push(ng+' -> '+e.options[i].text.trim()); });"
                    + " return fixed.join(', '); }").toString();
            if (!visitBackfilled.isEmpty()) System.out.println("saveRegistration: Visit.* backfill after department walk => " + visitBackfilled);
            waitForAngular(500);
            lastNationality = currentNationality();
            // Distinguish "the environment has no doctor to pick" from "we failed to fill something".
            if (reg.noDoctorAvailable) {
                lastFailReason = "no doctors in the drop down";
                System.out.println("saveRegistration: " + lastFailReason);
            }
            System.out.println("saveRegistration: visit info filled via RegistrationPage.fillVisitInformation()");
            // The Department/Doctor/Visit-Type cascades above are exactly the kind of operation that leaves this
            // AngularJS + select2 UI visually stuck (a field correctly set but not yet repainted) — nudge before
            // reading field state, rather than let a render glitch read back as "empty".
            nudgePage();
            // Report EVERY mandatory field still empty, rather than discovering them one per run from the toast.
            String stillEmpty = describeEmptyMandatory();
            System.out.println("saveRegistration: mandatory still empty => " + (stillEmpty.isEmpty() ? "(none)" : stillEmpty));
            lastEmptyMandatory = stillEmpty;
            // Now actually fill whatever is still empty (Email, Gender, or any other mandatory field nothing
            // upstream owns) BEFORE clicking Save, instead of only reporting the gap and letting Save reject it.
            String backfilled = backfillStillEmptyMandatory();
            if (!backfilled.isEmpty()) System.out.println("saveRegistration: backfillStillEmptyMandatory => " + backfilled);
            lastEmptyMandatory = describeEmptyMandatory();
        } catch (Exception e) {
            System.out.println("saveRegistration: fillVisitInformation failed (" + e.getMessage() + ") — falling back");
            System.out.println("ensureVisitCascade => " + ensureVisitCascade());
        }





        // ---- SAVE: delegate to OP RegistrationPage -------------------------------------------------------
        // This screen used to re-implement the whole save (arm a toast observer, poke Queue No / SaveBtnEnable,
        // invoke IUDRegistration() on the scope, then a 30-iteration poll clicking through the confirm dialog and
        // the "previous unclosed episode" popup). That parallel copy is why every Registration fix had to be made
        // TWICE and kept regressing here. It is the SAME VisitScreen and the SAME IUDRegistration() save, so the
        // save now runs through RegistrationPage: clickSaveAwaitConfirm() (Queue No, mandatory re-assert, blocking
        // popups, retry on a recoverable validation) then confirmSaveAndAwaitSuccess() (confirm dialog + success).
        // Only the report/consent TAB CAPTURE stays here, because SaveOutcome does not carry the tabs.
        if (reg == null) reg = new com.kpj.pages.Op_page.RegistrationPage(page);
        if (profile == null) profile = com.kpj.pages.Op_page.RegistrationPage.PatientProfile.random();
        reg.ensureVisitTypeSelected();
        String preSave = "";
        com.kpj.pages.Op_page.RegistrationPage.SaveOutcome outcome = null;
        try {
            preSave = reg.clickSaveAwaitConfirm(profile);
            System.out.println("saveRegistration: clickSaveAwaitConfirm => " + preSave);
            outcome = reg.confirmSaveAndAwaitSuccess();
            res.saved = outcome != null && outcome.saved;
            if (outcome != null && outcome.toast != null && !outcome.toast.isBlank()) res.message = outcome.toast;
            System.out.println("saveRegistration: shared save => saved=" + res.saved + " toast=" + res.message);
        } catch (Exception e) {
            System.out.println("saveRegistration: shared save threw - " + e.getMessage());
        }
        // Pick up the Registration Report / consent tabs the save opens (they can lag the toast).
        for (int i = 0; i < 16 && res.reportTab == null; i++) {
            if (page.context().pages().size() > baseTabs) {
                res.reportTab = page.context().pages().stream()
                        .filter(p2 -> p2.url().contains("RegistrationReport") || p2.url().contains("SQLReport"))
                        .reduce((a, b) -> b).orElse(null);
                if (res.reportTab == null && res.saved) {
                    res.reportTab = page.context().pages().stream()
                            .filter(p2 -> p2.url().contains("nhisformstest"))
                            .reduce((a, b) -> b).orElse(null);
                }
            }
            if (res.reportTab != null) break;
            waitForAngular(500);
        }
        if (res.reportTab != null) res.saved = true;
        String lastWarning = preSave == null ? "" : preSave;
        // Saved (toast) but the report tab hadn't appeared yet — final scan for it.
        if (res.saved && res.reportTab == null) {
            res.reportTab = page.context().pages().stream()
                    .filter(p -> p.url().contains("RegistrationReport") || p.url().contains("SQLReport"))
                    .reduce((a, b) -> b).orElse(null);
        }
        if (res.reportTab != null) {
            try { res.reportTab.waitForLoadState(); } catch (Exception ignore) {}
            res.reportTab.waitForTimeout(1200);
            res.reportPng = captureReportPng(res.reportTab);
            res.message = "Registration saved — report opened: " + res.reportTab.url();
        } else if (!res.saved) {
            Object toast = page.evaluate("() => window.__regToast || ''");
            String t = toast == null ? "" : toast.toString();
            res.message = !t.isEmpty() ? t : (!lastWarning.isEmpty() ? "Save gated: " + lastWarning : "No report tab / confirm appeared");
            // Say WHY in plain words, so the report does not just echo the app's message.
            if (!lastFailReason.isEmpty()) {
                res.failReason = lastFailReason;                       // e.g. no doctors in the drop down
            } else if (java.util.regex.Pattern.compile("passport\\s*expiry", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(t).find() && !lastNationality.toLowerCase().contains("indian")) {
                // The passport rule: a passport (and its expiry) is only supplied for an INDIAN nationality.
                // If the app demands the expiry for any other nationality, that is the app contradicting itself —
                // report exactly that rather than quietly filling a passport for a local patient.
                res.failReason = "nationality is " + (lastNationality.isEmpty() ? "(not set)" : "\"" + lastNationality + "\"")
                        + " (not Indian) but the app asks for Passport Expiry Date — app said: \"" + t + "\"";
            } else if (lastEmptyMandatory.isEmpty() && !t.isEmpty()) {
                // Everything mandatory WAS filled and the app still asked for something.
                res.failReason = "even after adding the details it still says: \"" + t + "\"";
            } else if (!lastEmptyMandatory.isEmpty()) {
                res.failReason = "these mandatory fields were still empty: " + lastEmptyMandatory;
            }
        }
        // Consent handling — SAME process as OP > Registration (RegistrationTest), reusing RegistrationPage's own
        // methods instead of a parallel hand-rolled version: (1) the in-app "Consent Details" (PDPA) modal, which
        // this screen previously never even checked for; (2) the Patient Registration Form consent tab — found
        // generically (its template id differs by environment) and submitted via submitPatientForm() (Submit ->
        // Yes -> success), the exact same call RegistrationTest makes.
        if (res.saved) {
            res.consentModalShown = reg.waitConsentDetailsModal();
            if (res.consentModalShown) reg.closeConsentDetailsModal();

            Page consent = reg.findTab("TH_EF_293");
            if (consent == null) consent = reg.findConsentFormTab();
            if (consent != null) {
                res.consentTab = consent;
                res.consentSubmitted = reg.submitPatientForm(consent);
                res.consentPng = reg.captureFullForm(consent);
            }
        }
        return res;
    }

    /**
     * Clear the "Please update the mandatory details in NOK for &lt;name&gt;" gate. For each existing
     * next-of-kin row that is missing a mandatory field, load it ({@code EditKinDetails}), fill the
     * empty mandatory fields (Relationship, Occupation, Family Name, Nationality) and tick <b>"Same As
     * Patient Address"</b> ({@code chkSameasPatAddr} → {@code setpatientaddress()} auto-fills the address
     * from the patient), then <b>Modify</b> ({@code ModifyKinDetails}). If no kin exists at all, add one
     * ({@code AddKinDetails}). Verified live: fixing the kin lets the registration Save proceed.
     */
    /**
     * Dismiss any INFORMATIONAL confirm/warning currently blocking the VisitScreen — e.g. "Patient has
     * previous unclosed episode" (which pops as soon as the patient details pre-fill) or a NOK reminder
     * — by clicking its OK/Yes. Never touches the "Do you want to Save" confirm. Loops a few times in
     * case several are stacked.
     */
    /** Full-page screenshot of the current VisitScreen (evidence of a filled section). Null on failure. */
    private byte[] sectionShot() {
        try { return page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(15000)); }
        catch (Exception e) { System.out.println("section screenshot: " + e.getMessage()); return null; }
    }

    /**
     * Read every informational warning/confirm popup and click through it (OK / Yes / Confirm /
     * Continue / Proceed) — e.g. "Patient has previous unclosed episode", "Please update the mandatory
     * details in NOK ...". Covers ng-confirm / jconfirm / bootstrap-modal / sweetalert dialogs. The
     * "Do you want to Save" confirm is left alone (handled by the save loop). Logs each message read.
     */
    private void dismissInfoAlerts() {
        for (int k = 0; k < 6; k++) {
            Object res = page.evaluate("() => { const out=[];"
                    + " const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.swal2-container,.swal2-popup,.bootbox,[role=dialog]')]"
                    + "   .filter(m=>m.offsetParent!==null && !/do you want to save/i.test(m.textContent||''));"
                    + " boxes.forEach(m=>{ const msg=(m.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "   const b=[...m.querySelectorAll('button,a')].find(x=>/^(ok|yes|confirm|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                    + "   if(b){ out.push(msg.slice(0,120)); b.click(); } });"
                    + " return out; }");
            @SuppressWarnings("unchecked")
            java.util.List<Object> msgs = res instanceof java.util.List ? (java.util.List<Object>) res : new java.util.ArrayList<>();
            if (msgs.isEmpty()) break;
            for (Object m : msgs) System.out.println("dismissInfoAlerts: read & OK'd -> " + m);
            waitForAngular(400);
        }
    }

    /**
     * Ensure the patient's Correspondence <b>City</b> is set (mandatory — an empty one gates the save with
     * "Please Select City!"). The City options are loaded ASYNC from the selected State, and not every State has
     * cities configured, so: make sure Country is set, then walk the State options until one populates a real City
     * option and take it. Leaves existing non-empty values alone. Returns what ended up selected.
     */
    private String ensureCity() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery||window.$; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const sel=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + " const realOpts=e=>[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)));"
                + " const chosen=e=>{ if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):''; return (t && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const pick=(e,idx)=>{ e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(x){}} };"
                // Address / house / street / postcode are mandatory too ("Please enter Address!") — fill any blanks.
                + " const setInpIfEmpty=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e || norm(e.value)) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " setInpIfEmpty('Registration.ResHouseNo','12'); setInpIfEmpty('Registration.ResStreet','Jalan Test');"
                + " setInpIfEmpty('Registration.ResAddress','No 12, Jalan Test'); setInpIfEmpty('Registration.ResPinCode','81000');"
                + " const cityEl=sel('Registration.ResCityID'); if(!cityEl) return '(no City field)';"
                + " if(chosen(cityEl)) return 'Address filled | City already set: '+chosen(cityEl);"
                // Country first — the State list hangs off it.
                + " const ctryEl=sel('Registration.ResCountryID');"
                + " if(ctryEl && !chosen(ctryEl)){ const rs=realOpts(ctryEl); let t=rs.find(x=>/malaysia/i.test(norm(x.o.textContent)))||rs[0]; if(t){ pick(ctryEl,t.i); await sleep(900); } }"
                + " const stEl=sel('Registration.ResStateID'); if(!stEl) return '(no State field)';"
                // If a State is already chosen, give its City list a chance first.
                + " if(chosen(stEl)){ for(let k=0;k<8;k++){ const c=sel('Registration.ResCityID'); if(c && realOpts(c).length){ pick(c, realOpts(c)[0].i); await sleep(300); return 'State='+chosen(stEl)+' | City='+chosen(c); } await sleep(400); } }"
                + " const states=realOpts(stEl);"
                + " for(let s=0; s<Math.min(states.length,25); s++){ pick(stEl, states[s].i); "
                + "   for(let k=0;k<10;k++){ const c=sel('Registration.ResCityID'); if(c && realOpts(c).length){ pick(c, realOpts(c)[0].i); await sleep(300); return 'State='+norm(states[s].o.textContent)+' | City='+chosen(c); } await sleep(350); } }"
                + " return '(no State yielded a City)'; }");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** The next-of-kin grid rows. They are {@code <tr ng-repeat="Kin in KinDetailsList">} with NO ng-click on the
     *  row (Edit is a control in the row's first column), so the old {@code tr[ng-click*='EditKinDetails']} selector
     *  never matched — every patient looked kin-less and got a duplicate blank kin added instead of being fixed. */
    private static final String KIN_ROWS_JS =
            "[...document.querySelectorAll(\"tr[ng-repeat*='KinDetailsList']\")].filter(r=>r.offsetParent!==null && [...r.querySelectorAll('td')].length>3)";

    private int kinRowCount() {
        Object r = page.evaluate("() => " + KIN_ROWS_JS + ".length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    private void ensureNextOfKin() {
        // The patient's existing next-of-kin sit in a table whose rows carry ng-click="EditKinDetails($index)".
        // The NOK grid lives INSIDE the "Patient Information" accordion (header #headingTwo), which collapses
        // after the payor step. Mirror the proven TC02 RegistrationPage.openNokAndClickGrid: if the kin rows
        // aren't visible, re-open the Patient Information header via //*[@id='headingTwo']/a (fallback: click the
        // "Patient Information" text), then open any collapsed Next of Kin sub-panel — otherwise the rows stay
        // hidden and EditKinDetails/ModifyKinDetails act on an off-screen form (save stays gated with
        // "Please update the mandatory details in NOK").
        // NB: check the kin FORM's visibility too, not just the kin ROWS. A patient with no next-of-kin has
        // zero EditKinDetails rows even when the section is wide open — treating that as "collapsed" makes the
        // header click TOGGLE THE OPEN SECTION SHUT, after which the Add path fills hidden inputs, adds nothing,
        // and the save is gated with "Please Fill Kin Details!".
        boolean kinVisible = Boolean.TRUE.equals(page.evaluate("() => " + KIN_ROWS_JS + ".length > 0"
                + " || [...document.querySelectorAll(\"input[ng-model='Registration.KinName']\")].some(e=>e.offsetParent!==null)"));
        if (!kinVisible) {
            try {
                page.locator("//*[@id='headingTwo']/a").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            } catch (Exception e) {
                page.evaluate("() => { const h=[...document.querySelectorAll('a,button,div,h3,h4,span,.panel-heading,.panel-title,.box-header')].find(x=>/^\\s*patient information\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(h) h.click(); }");
            }
            waitForAngular(700);
            page.evaluate("() => { [...document.querySelectorAll('a[data-toggle=collapse],.panel-title a,.panel-heading,a,h4,legend')].forEach(h=>{ const t=(h.textContent||'').replace(/\\s+/g,' ').trim(); if(/next of kin|kin details/i.test(t) && t.length<40 && h.getAttribute('aria-expanded')!=='true'){ try{ h.click(); }catch(e){} } }); }");
            waitForAngular(500);
        }
        // The patient's kin list arrives ASYNC — on arrival the grid is empty and KinDetailsList is len=0, and the
        // existing kin only appear a moment later. Deciding Add-vs-Edit immediately therefore appends a DUPLICATE
        // incomplete kin while leaving the real (incomplete) one untouched, and the save stays gated with
        // "Please update the mandatory details in NOK for <name>". Wait for the list to settle first.
        // Poll a settle WINDOW (an empty list is indistinguishable from a not-yet-loaded one, so we cannot
        // early-out on "length === 0" — that returns instantly and re-creates the race).
        int rows = 0;
        for (int w = 0; w < 16; w++) {
            rows = kinRowCount();
            if (rows > 0) break;
            page.waitForTimeout(500);
        }
        System.out.println("ensureNextOfKin: kin rows after settle = " + rows);
        if (rows > 0) {
            for (int i = 0; i < rows; i++) {
                // Load kin row i into the edit form with a REAL click on the row (its
                // ng-click="EditKinDetails($index)") — tag a data cell and click via Playwright so
                // Angular's handler fires reliably — then WAIT for the form to populate (KinName filled)
                // before filling; the kin edit form renders asynchronously after the click.
                // The Edit control lives in the row's first ("Edit") column — the <tr> itself has NO ng-click.
                page.evaluate("(i) => { const rows=" + KIN_ROWS_JS + "; const row=rows[i]; if(!row) return;"
                        + " const ctl=[...row.querySelectorAll('[ng-click]')].find(e=>/EditKinDetails/i.test(e.getAttribute('ng-click')||''))"
                        + "   || row.querySelector('td a,td button,td i,td img,td span'); if(ctl) ctl.id='__kinRowCell'; }", i);
                try { page.locator("#__kinRowCell").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) {
                    System.out.println("ensureNextOfKin: kin edit click failed, falling back to scope call (" + e.getMessage() + ")");
                    final int idx = i;
                    page.evaluate("(i) => { const rows=" + KIN_ROWS_JS + "; const row=rows[i]; if(!row) return;"
                            + " const sc=angular.element(row).scope(); let x=sc; for(let k=0;k<10&&x;k++){ if(typeof x.EditKinDetails==='function'){ const idx=(sc.$index!=null?sc.$index:i); x.$apply(function(){ x.EditKinDetails(idx); }); return; } x=x.$parent; } }", idx);
                }
                try {
                    page.waitForFunction("() => { const e=[...document.querySelectorAll(\"input[ng-model='Registration.KinName']\")][0]; return e && (e.value||'').trim().length>0; }",
                            null, new Page.WaitForFunctionOptions().setTimeout(6000));
                } catch (Exception ignore) { System.out.println("ensureNextOfKin: kin edit form did not populate after click"); }
                waitForAngular(600);
                // Fill ALL mandatory kin fields FRESHLY (the mandatory dropdowns — esp. Relationship —
                // must be set every time; don't rely on the row's partial data). Tick Same-As-Patient-
                // Address. NB: do NOT click Modify in this same evaluate — Angular hasn't $digested the
                // <select> change yet, so Modify would read a stale model and drop the Relationship
                // (the exact bug that kept a patient gated). Fill here, digest, then Modify separately.
                page.evaluate("() => {"
                        + "const setSel=(ng,txt)=>{const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                        + "  let k=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(k<1) k=[...e.options].findIndex((o,ix)=>ix>0 && !/select/i.test(o.text||'')); if(k<1) return; e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} };"
                        + "const setInpIfEmpty=(ng,val)=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e || (e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                        + "setSel('Registration.KinRelationID','Father'); setSel('Registration.KinOccupationID','Education'); setSel('Registration.NOKnationalid','Malaysian');"
                        + "setSel('Registration.KinMobileCountryCode','60'); setSel('Registration.KinCountryID','Malaysia');"
                        + "const nm=(([...document.querySelectorAll(\"input[ng-model='Registration.KinName']\")][0]||{}).value||'Kin').trim(); setInpIfEmpty('Registration.KinFamilyName', (nm.split(' ').pop()||'Kin'));"
                        // Same 10-digit rule for the kin mobile — must be EXACTLY 10 (too short AND too long both fail Save).
                        + "const kmob=[...document.querySelectorAll(\"input[ng-model='Registration.KinMobileNo']\")].find(x=>x.offsetParent!==null); if(kmob){ const kc=(kmob.value||'').replace(/\\D/g,''); if(kc.length!==10){ const kk=angular.element(kmob).controller('ngModel'); kmob.value='1234567890'; if(kk){kk.$setViewValue('1234567890');kk.$render();} kmob.dispatchEvent(new Event('input',{bubbles:true})); kmob.dispatchEvent(new Event('change',{bubbles:true})); } }"
                        + "const chk=[...document.querySelectorAll('input[type=checkbox]')].find(c=>c.getAttribute('ng-model')==='chkSameasPatAddr'); if(chk && !chk.checked) chk.click(); }");
                waitForAngular(700); // let Angular $digest the Relationship (and other) changes
                // Now click Modify — the model is up to date, so the Relationship persists.
                page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='ModifyKinDetails();' && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(800);
            }
        } else {
            // No kin — add one (Same As Patient Address fills the address block).
            page.evaluate("() => {"
                    + "const setSel=(ng,txt)=>{const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; let k=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(k<1) k=[...e.options].findIndex((o,ix)=>ix>0 && !/select/i.test(o.text||'')); if(k<1) return; e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} };"
                    + "const setInp=(ng,val)=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + "setSel('Registration.KinTitleID','Mr.'); setInp('Registration.KinName','Test Kin'); setInp('Registration.KinFamilyName','Kin'); setSel('Registration.NOKnationalid','Malaysian'); setInp('Registration.KinNationalId','900101145523'); setSel('Registration.KinRelationID','Father'); setInp('Registration.KinMobileNo','1234567890'); setSel('Registration.KinMobileCountryCode','60'); setSel('Registration.KinCountryID','Malaysia'); setSel('Registration.KinOccupationID','Education');"
                    + "const chk=[...document.querySelectorAll('input[type=checkbox]')].find(c=>c.getAttribute('ng-model')==='chkSameasPatAddr'); if(chk && !chk.checked) chk.click(); }");
            waitForAngular(700); // digest before Add (same stale-model concern as Modify)
            page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(800);
            // Add is the LAST step for the kin — do NOT click its grid row afterwards. That click fires
            // EditKinDetails($index), which takes the kin back out of KinDetailsList into the form, and Save is
            // then rejected with "Please click Add to include the NOK/Guarantor details!". Proven on OP
            // Registration: with the row left alone the save goes through (MRN issued).
            waitForAngular(600);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> capturedAlerts() {
        Object r = page.evaluate("() => window.__alerts || []");
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    @Override
    public void screenshot(String path) {
        super.screenshot(path);
    }
}
