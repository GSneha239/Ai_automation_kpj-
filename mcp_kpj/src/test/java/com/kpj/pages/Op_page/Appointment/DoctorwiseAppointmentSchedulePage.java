package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * DoctorwiseAppointmentSchedulePage — Page Object for
 * OP &gt; Appointment &gt; Doctorwise Appointment Schedule (#/DoctorwiseAppointmentSchedule).
 *
 * <p>Flow (verified live): select Month (current), Department and Doctor → the doctor's monthly
 * slot grid loads (green = available); <b>double-clicking</b> an available slot
 * (<code>ng-dblclick="Opendetails(GS1,CS1)"</code>) auto-navigates to Book Appointment (#/New)
 * pre-filled with that slot; fill the mandatory patient details, Save, confirm.</p>
 *
 * <p><b>Known defect (reported):</b> On save, the Book Appointment page posts
 * <code>/api/appointment/IUD</code> with an <b>empty</b> <code>AppointmentListJsonString</code>,
 * so nothing is inserted, no success message is shown, and no report is generated. This page
 * therefore treats the report tab as best-effort and surfaces whether it opened.</p>
 */
public class DoctorwiseAppointmentSchedulePage extends BasePage {

    public DoctorwiseAppointmentSchedulePage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /** Open the Doctorwise Appointment Schedule page and wait for the Department dropdown to load. */
    public void navigateTo(String baseUrl) {
        // Use in-app hash navigation instead of page.goto() to avoid AngularJS SPA reloading issues
        page.evaluate("() => { window.location.hash = '#/DoctorwiseAppointmentSchedule'; }");
        waitForAngular(2000);

        // Fallback: if not rendered, expand OP menu and click
        boolean ready = Boolean.TRUE.equals(page.evaluate(
                "() => !!document.querySelector(\"select[ng-model='DoctorwiseAppointmentSchedule.DepartmentID']\")"));
        if (!ready) {
            page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP'); if(op) op.click(); }");
            waitForAngular(1000);
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/DoctorwiseAppointmentSchedule'); if(a) a.click(); }");
            waitForAngular(1000);
        }

        page.waitForSelector("select[ng-model='DoctorwiseAppointmentSchedule.DepartmentID']",
                new Page.WaitForSelectorOptions().setTimeout(20000));
        page.waitForFunction(
                "() => { const e=document.querySelector(\"select[ng-model='DoctorwiseAppointmentSchedule.DepartmentID']\"); return e && e.options.length > 1; }",
                null, new Page.WaitForFunctionOptions().setTimeout(20000));
        waitForAngular(800);
        installHelpers();
        setMonthToCurrent(); // the slot grid stays EMPTY for every doctor until the Month is set
    }

    /**
     * Set the schedule Month filter to the current month (format yyyy-MM). This is REQUIRED: with the
     * Month blank the slot grid renders empty for every doctor, so {@link #findDoctorWithSlots} finds
     * nothing. Returns the value the field ended up with.
     */
    public String setMonthToCurrent() {
        return setMonth(java.time.YearMonth.now().toString()); // e.g. "2026-07"
    }

    /** Set the Month filter (yyyy-MM) and let the grid reload. */
    public String setMonth(String yyyyMM) {
        Object r = page.evaluate("(v) => {"
                + "const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='DoctorwiseAppointmentSchedule.SelectedMonth');"
                + "if(!e) return 'no-month-input';"
                + "const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + "return e.value; }", yyyyMM);
        waitForAngular(1500);
        return String.valueOf(r);
    }

    private void installHelpers() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                + "window.__setSel = function(ng, text){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "  if(!e) return ng+':no-select'; let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===text.toLowerCase());"
                + "  if(i<0) i=[...e.options].findIndex(o=>(o.text||'').toLowerCase().includes(text.toLowerCase())); if(i<0) return ng+':no-opt';"
                + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + "  return ng+':'+e.options[e.selectedIndex].text.trim(); };"
                + "window.__setInp = function(ng, val){ const e=[...document.querySelectorAll('input,textarea')].filter(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null)[0];"
                + "  if(!e) return ng+':no-input'; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();}"
                + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return ng+':ok'; };"
                + "}");
    }

    // ---- schedule selection ----------------------------------------------

    /** Select the schedule Department (e.g. "Emergency Department", "Cardiology"). */
    public String selectDepartment(String department) {
        Object r = page.evaluate("(d) => window.__setSel('DoctorwiseAppointmentSchedule.DepartmentID', d)", department);
        waitForAngular(1500);
        return String.valueOf(r);
    }

    /**
     * Find a doctor in the currently-selected department that has available slots. Tries the
     * preferred doctor first, then iterates the list. Returns the doctor name, or null if none
     * in this department has a schedule (e.g. Emergency Department has no configured slots).
     */
    /** How long to wait for a doctor's calendar to load before deciding they have no slots. */
    private static final int SLOT_WAIT_MS = Integer.getInteger("devhis.slotwait", 8000);
    /** Overall cap for the doctor scan, so a department of 20 empty doctors cannot stall the run. */
    private static final int SCAN_BUDGET_MS = Integer.getInteger("devhis.slotscan", 90000);

    public String findDoctorWithSlots(String preferredDoctor) {
        String ym = java.time.YearMonth.now().toString();
        Object r = page.evaluate("async (args) => {"
                + "const preferred=args.preferred, ym=args.ym;"
                + "const mo=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='DoctorwiseAppointmentSchedule.SelectedMonth');"
                + "if(mo && mo.value!==ym){ const c=angular.element(mo).controller('ngModel'); mo.value=ym; if(c){c.$setViewValue(ym);c.$render();} mo.dispatchEvent(new Event('change',{bubbles:true})); await new Promise(r=>setTimeout(r,1200)); }"
                + "const sel=document.querySelector(\"select[ng-model='DoctorwiseAppointmentSchedule.DoctorModalityID']\");"
                + "if(!sel) return null;"
                + "const opts=[...sel.options].filter(o=>o.text.trim()!=='-Select-');"
                + "const order=[]; const pi=opts.findIndex(o=>o.text.trim()===preferred); if(pi>=0) order.push(opts[pi]);"
                + "opts.forEach(o=>{ if(o.text.trim()!==preferred) order.push(o); });"
                + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + "const hasSlots=()=>[...document.querySelectorAll('td.available-slot')].some(c=>c.getBoundingClientRect().width>0);"
                // The calendar reloads over the network after the doctor changes. A single check behind a fixed
                // 1.6s sleep read a still-loading grid as "this doctor has no slots" and moved on, so the flow
                // reported "No doctor with slots found" on a department that does have them. Poll instead: give
                // each doctor up to SLOT_WAIT ms, checking every 200ms, and only move on once it really is empty.
                + "const SLOT_WAIT=" + SLOT_WAIT_MS + ", SCAN_BUDGET=" + SCAN_BUDGET_MS + ", scanStart=Date.now();"
                + "const waitForSlots=async()=>{ const t0=Date.now();"
                + "  while(Date.now()-t0<SLOT_WAIT){ if(hasSlots()) return true; await sleep(200); } return false; };"
                + "for(let k=0;k<Math.min(order.length,20);k++){"
                + "  if(Date.now()-scanStart>SCAN_BUDGET) break;"
                + "  sel.selectedIndex=[...sel.options].indexOf(order[k]); sel.dispatchEvent(new Event('change',{bubbles:true}));"
                + "  const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}}"
                + "  if(await waitForSlots()) return order[k].text.trim();"
                + "}"
                + "return null; }", java.util.Map.of("preferred", preferredDoctor == null ? "" : preferredDoctor, "ym", ym));
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** How many distinct available-slot cells to try before giving up (a single cell's dblclick can
     *  fail to navigate — confirmed live it is intermittent, not tied to a specific cell — so retrying
     *  a DIFFERENT cell rather than the same one gives the flow a real chance to recover). */
    private static final int MAX_SLOT_ATTEMPTS = 5;

    /**
     * Double-click an available slot in the loaded grid, which auto-navigates to Book Appointment
     * (#/New). Returns the slot time (e.g. "08:00 AM-08:15 AM"), or null if none navigated.
     *
     * <p>Confirmed live (Chrome DevTools on the real app): double-clicking a genuinely available
     * (green) slot DOES navigate to #/New — the flow itself works. A first attempt at this method used
     * {@code Locator.dblclick()} — a real dblclick on one cell worked once when driven manually, but
     * under Playwright it failed on EVERY cell tried (5/5), the first as a clean TimeoutError and the
     * rest as PlaywrightExceptions — the same "locator-based click, separate round trip" shape that
     * caused the Reserve-bed checkbox hang elsewhere in this suite (id injected / element resolved,
     * then a SEPARATE dispatcher call tries to click it, with a window for a sticky header, an overlay,
     * or Playwright's own re-scroll to fight the click). Fix the same way: dispatch the double-click
     * as real mouse events on the element from INSIDE one synchronous evaluate — no separate
     * locator round trip, so there is no window for anything to move the target out from under it.</p>
     */
    public String pickAvailableSlotAndOpenBooking() {
        for (int attempt = 0; attempt < MAX_SLOT_ATTEMPTS; attempt++) {
            Object slot = page.evaluate("(skip) => { const cells=[...document.querySelectorAll('td.available-slot')].filter(e=>e.getBoundingClientRect().width>0 && e.getAttribute('data-time'));"
                    + " const c=cells[skip]; if(!c) return null;"
                    + " c.scrollIntoView({block:'center', inline:'center'});"
                    + " const r=c.getBoundingClientRect(); const x=r.x+r.width/2, y=r.y+r.height/2;"
                    + " const opts={bubbles:true, cancelable:true, view:window, clientX:x, clientY:y};"
                    + " c.dispatchEvent(new MouseEvent('mousedown',{...opts, detail:1})); c.dispatchEvent(new MouseEvent('mouseup',{...opts, detail:1})); c.dispatchEvent(new MouseEvent('click',{...opts, detail:1}));"
                    + " c.dispatchEvent(new MouseEvent('mousedown',{...opts, detail:2})); c.dispatchEvent(new MouseEvent('mouseup',{...opts, detail:2})); c.dispatchEvent(new MouseEvent('click',{...opts, detail:2}));"
                    + " c.dispatchEvent(new MouseEvent('dblclick',{...opts, detail:2}));"
                    + " try{ if(window.angular) angular.element(c).triggerHandler('dblclick'); }catch(e){}"
                    + " return c.getAttribute('data-time'); }",
                    attempt);
            if (slot == null) return null; // no more candidate cells left to try
            try {
                // The app inserts a ?_cb=… cache-buster BEFORE the hash (…?_cb=1.2.16#/New), so waitForURL("**/#/New")
                // never matches even though navigation happened — wait on the hash instead, then on the field.
                page.waitForFunction("() => /#\\/New\\b/i.test(location.hash || location.href)",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(15000));
                waitForAngular(1500);
                installHelpers(); // re-install on the Book Appointment page
                return slot.toString();
            } catch (Exception e) {
                System.out.println("pickAvailableSlotAndOpenBooking: slot " + slot + " (attempt " + (attempt + 1)
                        + "/" + MAX_SLOT_ATTEMPTS + ") did not navigate - " + e.getClass().getSimpleName()
                        + " — trying a different slot");
                page.evaluate("() => { [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')]"
                        + ".filter(m=>m.getBoundingClientRect().width>0).forEach(box=>{ const btn=[...box.querySelectorAll('button,a')]"
                        + ".find(x=>/^(ok|yes|close|cancel)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(btn) btn.click(); }); }");
            }
        }
        return null;
    }

    // ---- Book Appointment (after slot navigation) -------------------------

    /**
     * On the Book Appointment page: fill the mandatory patient details (appointment context —
     * department/doctor/slot — arrives pre-filled from the schedule). Also ensures the Doctor is
     * set and the appointment date is a valid future day matching the picked slot.
     */
    public void fillPatientDetails(String doctor, String appointmentDate) {
        // Name via the AngularJS model directly (the field's auto-complete clears it on input)
        page.evaluate("(v) => { const e=document.querySelector('#txtFirstName'); if(!e) return;"
                + " let s=angular.element(e).scope(), pd=null; for(let i=0;i<8&&s;i++){ if(s.PatientData){pd=s.PatientData;break;} s=s.$parent; }"
                + " if(pd){ s.$apply(function(){ pd.FirstName=v; }); const c=angular.element(e).controller('ngModel'); if(c) c.$render(); } }",
                "AutoSched " + uniqueId());
        String suffix = uniqueId();
        page.evaluate("(s) => {"
                + "window.__setSel('PatientData.PrefixID','Mr.');"
                + "window.__setSel('PatientData.GenderID','Male');"
                + "window.__setSel('PatientData.NationalityID','Malaysian');"
                + "window.__setSel('PatientData.receivabletypeid','Self');"
                + "window.__setSel('PatientData.BookingTypeID','KPJ');"
                + "window.__setInp('PatientData.NationalId','9004040' + (s+'00000').slice(0,5));"
                + "window.__setInp('PatientData.DateOfBirth','04/04/1990');"
                + "window.__setInp('PatientData.Email','autosched' + s + '@example.com');"
                + "window.__setInp('PatientData.MobileNo','19' + (s+'0000000').slice(0,7));"
                + "window.__setInp('PatientData.AgeYear','36');"
                + "window.__setInp('PatientData.AgeMonth','0');"
                + "window.__setInp('PatientData.AgeDays','0');"
                + "}", suffix);
        // Ensure Doctor + a valid future date are set (the schedule can leave Doctor unset / date=today)
        if (doctor != null) page.evaluate("(d) => window.__setSel('PatientData.DoctorModalityID', d)", doctor);
        // Keep the slot's OWN pre-filled appointment date whenever it is today-or-future (that date came from
        // the picked available slot, so it is valid). Only fall back to the supplied date if the field is blank
        // or the slot pre-filled a PAST day — a past appointment date is rejected server-side, so the save is
        // silently dropped and no report is generated.
        if (appointmentDate != null) {
            page.evaluate("(fb) => { const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')==='PatientData.AppointmentDate' && x.offsetParent!==null);"
                    + " const cur=e?((e.value||'').trim()):'';"
                    + " const parse=s=>{ const m=(s||'').match(/(\\d{2})\\/(\\d{2})\\/(\\d{4})/); return m?new Date(+m[3],+m[2]-1,+m[1]):null; };"
                    + " const today=new Date(); today.setHours(0,0,0,0); const d=parse(cur);"
                    + " if(!(d && d>=today) && fb) window.__setInp('PatientData.AppointmentDate', fb); }", appointmentDate);
        }
        // Also ensure AppointmentType and Department are set (required for save)
        page.evaluate("() => {"
                + "window.__setSel('PatientData.AppointmentTypeID','Department');"
                + "window.__setSel('PatientData.DepartmentID','Cardiology');"
                + "}");
        waitForAngular(800);
    }

    /**
     * Click the "Add" button (fnAddToMultipleApptList) to append the appointment to the internal
     * list before Save — REQUIRED, or Save posts an empty AppointmentListJsonString and no report
     * is generated. Returns the list size after adding (0 = did not register).
     */
    public int clickAdd() {
        Object r = page.evaluate("() => {"
                + "const add=[...document.querySelectorAll('[ng-click]')].find(e=>/fnAddToMultipleApptList/.test(e.getAttribute('ng-click')||'') && e.getBoundingClientRect().width>0);"
                + "if(add) add.click();"
                + "let s=angular.element(document.querySelector('#btnSave')).scope(), len=0;"
                + "for(let i=0;i<12&&s;i++){ if(Array.isArray(s.multipleApptBookingList)){ len=s.multipleApptBookingList.length; break; } s=s.$parent; }"
                + "return len; }");
        waitForAngular(500);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Save → confirm "Do You Want To Save" → capture the appointment report tab. Returns the report
     * tab Page, or null.
     */
    public Page saveAndCaptureReport() {
        page.evaluate("() => { const b=document.querySelector('#btnSave'); if(!b) return;"
                + " try { const sc=angular.element(b).scope(); sc.$apply(function(){ sc.IsClicked=true; }); } catch(e){}"
                + " window.__alerts=[]; document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); b.click(); }");
        waitForAngular(600);
        try {
            page.waitForSelector(".ng-confirm-box button", new Page.WaitForSelectorOptions().setTimeout(20000));
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no confirm dialog. Alerts: " + capturedAlerts());
            return null;
        }
        Page reportTab = null;
        try {
            reportTab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(60000),
                    () -> {
                        clickNgConfirmButton("Save");
                        page.waitForTimeout(3000);
                        clickNgConfirmButton("Yes");   // optional "slot already booked" prompt
                        page.waitForTimeout(3000);
                    });
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no report tab (known defect — empty AppointmentListJsonString).");
        }
        if (reportTab != null) {
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(3000);
        }
        return reportTab;
    }

    private void clickNgConfirmButton(String text) {
        Object tagged = page.evaluate("(t) => { const box=[...document.querySelectorAll('.ng-confirm-box')].find(m=>m.getBoundingClientRect().width>0);"
                + "if(!box) return false; const b=[...box.querySelectorAll('button')].find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase() && x.getBoundingClientRect().width>0);"
                + "if(!b) return false; b.id='__ngConfirmBtn'; return true; }", text);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ngConfirmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception ignore) {}
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
