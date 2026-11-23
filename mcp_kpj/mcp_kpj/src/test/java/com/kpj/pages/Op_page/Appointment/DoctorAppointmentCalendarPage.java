package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * DoctorAppointmentCalendarPage — Page Object for
 * OP &gt; Appointment &gt; Doctorwise Appointment Calendar (#/dr-appointment-calendar).
 *
 * <p>Flow (checked by hand in the browser, 18-11-2026, build v2026.11.17.488):</p>
 * <ul>
 *   <li>Toolbar: Day / Week / Month / Today / Refresh, Previous / Next arrows and the date-range label.</li>
 *   <li>Filters: Appointment Type (Doctor / Modality) unlocks Doctor (or Modality); Cluster, NRIC and
 *       Appointment Status narrow the calendar; "Total Booked" shows the count for the current filter.</li>
 *   <li>Book Appointment stays DISABLED until a free, future slot is clicked
 *       (<code>ng-disabled="!SelectedSlot || isPastDate || isSlotFull() || isSlotUnavailable()"</code>).</li>
 *   <li>With a slot selected, Book Appointment shows the info alert "For the selected time slot, a maximum of N
 *       patients can be booked. M slots are remaining.", stores the slot in sessionStorage
 *       (<code>SelectedAppointment</code>, <code>FromCalendar=true</code>, <code>CalendarDoctorID</code>) and opens
 *       <code>/OPD/#/New</code> (Book Appointment) in a NEW TAB.</li>
 * </ul>
 *
 * <p>The Book Appointment tab itself is NOT scripted here — the test hands that tab to the team's
 * {@link BookAppointmentPage} ({@code attachToOpenForm()}, {@code fillMandatoryDetails()}, {@code clickAdd()},
 * {@code saveAndCaptureReport()}), so there is no duplicate Book Appointment script.</p>
 */
public class DoctorAppointmentCalendarPage extends BasePage {

    private static final String HREF = "#/dr-appointment-calendar";
    private static final String BOOK_BTN = "#btnBookAppointment";
    private static final String TYPE_SELECT = "select[ng-model='Filter.AppointmentTypeID']";
    private static final String DOCTOR_SELECT = "select[ng-model='Filter.DoctorID']";

    public DoctorAppointmentCalendarPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /**
     * OP &gt; Appointment &gt; Doctorwise Appointment Calendar through the sidebar (same way as the other Appointment
     * pages: each level opened only if shut, the leaf looked up by its exact href inside the OP group).
     * Returns true only when the screen really is on view ({@link #onScreen()}).
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*op\\s*$");
            expandMenuEntry("^\\s*op\\s*$", "^\\s*appointment\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*op\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__calLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__calLink").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DoctorAppointmentCalendar.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__calLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(BOOK_BTN, new Page.WaitForSelectorOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("DoctorAppointmentCalendar.nav: the Book Appointment button is not up"); }
        return onScreen();
    }

    /** On this screen — the route AND its Book Appointment button, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(HREF)) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", BOOK_BTN));
    }

    /** After the screen is open: wait for the Appointment Type list and the calendar grid, then install the helpers. */
    public void prepareScreen() {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length > 1; }",
                    TYPE_SELECT, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { System.out.println("DoctorAppointmentCalendar.prepare: Appointment Type options did not load"); }
        try {
            page.waitForFunction("() => document.querySelectorAll('.gcal-grid-line').length > 0", null,
                    new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { System.out.println("DoctorAppointmentCalendar.prepare: the calendar grid did not render"); }
        waitForAngular(1500);
        installHelpers();
    }

    private void installHelpers() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                // the controller scope (holds Filter, viewMode, SelectedSlot, TotalBookedAppt)
                + "window.__calScope = function(){ let s=angular.element(document.querySelector('#btnBookAppointment')).scope();"
                + "  for(let i=0;i<12&&s;i++){ if(s.Filter && s.viewMode!==undefined) return s; s=s.$parent; } return null; };"
                + "window.__setSel = function(ng, text){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "  if(!e) return ng+':no-select'; if(e.disabled) return ng+':disabled'; const want=(text||'').trim().toLowerCase();"
                + "  const i=[...e.options].findIndex(o=>o.text.trim().toLowerCase()===want);"
                + "  if(i<0) return ng+':no-opt';"
                + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + "  return ng+':'+e.options[e.selectedIndex].text.trim(); };"
                + "}");
    }

    // ---- toolbar ----------------------------------------------------------

    /** The date-range label next to the arrows, e.g. "November 15–21, 2026". */
    public String dateRangeLabel() {
        Object r = page.evaluate("() => { const e=document.getElementById('date-range'); return e ? e.textContent.replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The view the calendar is in: "day", "week" or "month". */
    public String viewMode() {
        Object r = page.evaluate("() => { const s=window.__calScope && window.__calScope(); return s ? String(s.viewMode) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the Day / Week / Month / Today button (real click) and return the date-range label afterwards. */
    public String clickToolbar(String label) {
        page.locator(".view-toggle button:visible").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(label)).first()
                .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
        waitForAngular(2000);
        return dateRangeLabel();
    }

    /** Click the Next (down) or Previous (up) arrow and return the date-range label afterwards. */
    public String clickArrow(boolean next) {
        // matched by ng-click, not title: the Bootstrap tooltip moves the arrows' title into data-original-title
        page.locator("button[ng-click='" + (next ? "goNext()" : "goPrev()") + "']:visible").first()
                .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
        waitForAngular(2000);
        return dateRangeLabel();
    }

    // ---- filters ----------------------------------------------------------

    /** Select the Appointment Type (Doctor / Modality); the Doctor (or Modality) list unlocks after it. */
    public String selectAppointmentType(String type) {
        Object r = page.evaluate("(t) => window.__setSel('Filter.AppointmentTypeID', t)", type);
        waitForAngular(1500);
        return String.valueOf(r);
    }

    /** Select a Doctor once the Doctor list is enabled; the calendar reloads for that doctor. */
    public String selectDoctor(String doctor) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && !e.disabled && e.options.length > 1; }",
                    DOCTOR_SELECT, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("DoctorAppointmentCalendar.selectDoctor: the Doctor list did not unlock"); }
        Object r = page.evaluate("(d) => window.__setSel('Filter.DoctorID', d)", doctor);
        waitForAngular(3000);
        return String.valueOf(r);
    }

    /** Select an Appointment Status ("--All--", "Arrived", "Completed", "No Show", "Cancelled"). */
    public String selectAppointmentStatus(String status) {
        Object r = page.evaluate("(t) => window.__setSel('Filter.AppointmentStatus', t)", status);
        waitForAngular(2500);
        return String.valueOf(r);
    }

    /** The "Total Booked" count shown for the current filter. */
    public String totalBooked() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='TotalBookedAppt']\"); return e ? e.value.trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Number of appointment blocks drawn on the calendar for the current filter/view. */
    public int appointmentBlocks() {
        Object r = page.evaluate("() => document.querySelectorAll('.gcal-appt-block').length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    // ---- slot + book ------------------------------------------------------

    /** True when the Book Appointment button can be clicked. */
    public boolean bookButtonEnabled() {
        return Boolean.TRUE.equals(page.evaluate("(s) => { const b=document.querySelector(s); return !!b && !b.disabled; }", BOOK_BTN));
    }

    /**
     * Click (real click) a free slot in the Week view on a day AFTER today between 09:00 AM and 05:00 PM, until the
     * Book Appointment button unlocks (a full / unavailable slot keeps it disabled). Moves to the next week if the
     * current one has none. Returns "dd/MM/yyyy hh:mm AM" of the selected slot, or null when none could be selected.
     */
    public String selectFreeFutureSlot() {
        for (int week = 0; week < 2; week++) {
            Object cands = page.evaluate("() => {"
                    + " const today=new Date(); today.setHours(23,59,59,999);"
                    + " const toMin=t=>{ const m=/(\\d{1,2}):(\\d{2})\\s*(AM|PM)/i.exec(t||''); if(!m) return -1; let h=+m[1]%12; if(/pm/i.test(m[3])) h+=12; return h*60+(+m[2]); };"
                    + " const out=[]; [...document.querySelectorAll('.gcal-day-inner')].forEach((day,di)=>{"
                    + "  [...day.querySelectorAll('.gcal-grid-line')].forEach((ln,ti)=>{"
                    + "   if(ln.classList.contains('slot-past')||ln.classList.contains('slot-unavailable')) return;"
                    + "   const sc=angular.element(ln).scope(); if(!sc||!sc.d) return;"
                    + "   const d=new Date(sc.d.fullDate); if(!(d>today)) return;"
                    + "   const m=toMin(sc.t); if(m<9*60||m>17*60) return;"
                    + "   out.push(di+':'+ti); }); });"
                    + " return out.slice(0,40); }");
            @SuppressWarnings("unchecked")
            java.util.List<String> list = cands instanceof java.util.List ? (java.util.List<String>) cands : new java.util.ArrayList<>();
            for (String c : list) {
                String[] p = c.split(":");
                Object tagged = page.evaluate("([di,ti]) => { const day=document.querySelectorAll('.gcal-day-inner')[di]; if(!day) return false;"
                        + " const ln=day.querySelectorAll('.gcal-grid-line')[ti]; if(!ln) return false; ln.id='__calSlot'; ln.scrollIntoView({block:'center'}); return true; }",
                        java.util.Arrays.asList(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
                if (!Boolean.TRUE.equals(tagged)) continue;
                try {
                    // click at the left edge of the line so an appointment block drawn over the middle is not hit
                    page.locator("#__calSlot").click(new com.microsoft.playwright.Locator.ClickOptions()
                            .setPosition(6, 15).setTimeout(5000));
                } catch (Exception e) { System.out.println("selectFreeFutureSlot: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__calSlot'); if(e) e.removeAttribute('id'); }");
                waitForAngular(700);
                if (bookButtonEnabled()) {
                    Object sel = page.evaluate("() => { const s=window.__calScope(); if(!s||!s.SelectedSlot) return null;"
                            + " const d=new Date(s.SelectedSlot.date); const dd=String(d.getDate()).padStart(2,'0'), mm=String(d.getMonth()+1).padStart(2,'0');"
                            + " return dd+'/'+mm+'/'+d.getFullYear()+' '+s.SelectedSlot.time; }");
                    if (sel != null) return sel.toString();
                }
            }
            clickArrow(true); // nothing free this week — try the next one
        }
        return null;
    }

    /**
     * Click Book Appointment (real click) and catch the Book Appointment tab it opens. Returns that tab, or null when
     * no tab opened (the alert shown instead is in {@link #capturedMessages()}).
     */
    public Page clickBookAppointmentAndCatchTab() {
        page.evaluate("() => { window.__alerts=[]; }");
        try {
            Page tab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(30000),
                    () -> page.locator(BOOK_BTN).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)));
            try { tab.waitForLoadState(); } catch (Exception ignore) { }
            return tab;
        } catch (Exception e) {
            System.out.println("clickBookAppointmentAndCatchTab: no new tab - " + String.valueOf(e.getMessage()).split("\n")[0]);
            return null;
        }
    }

    /** What the calendar handed to Book Appointment (sessionStorage SelectedAppointment), as raw JSON. */
    public String handOff() {
        Object r = page.evaluate("() => sessionStorage.getItem('SelectedAppointment') || ''");
        return r == null ? "" : r.toString();
    }

    /** Messages (JAlert / alert) the calendar showed, without bare type labels ("info", "warning"…). */
    @SuppressWarnings("unchecked")
    public java.util.List<String> capturedMessages() {
        Object r = page.evaluate("() => window.__alerts || []");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) r) {
                String t = o == null ? "" : o.toString().trim();
                if (!t.isEmpty() && !t.matches("(?i)error|success|warning|info|alert|notice")) out.add(t);
            }
        }
        return out;
    }

    /**
     * Read what the Book Appointment tab shows for the slot the calendar handed over: Appointment Type, Doctor,
     * Appointment Date and From/To time (selected option TEXT for the drop-downs). Returns
     * {type, doctor, date, from, to}; blanks when a field is empty.
     */
    public static String[] carriedToBookAppointment(Page tab) {
        Object r = tab.evaluate("() => { const txt=ng=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "   return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.trim().replace(/^-+\\s*Select\\s*-+$/i,'') : ''; };"
                + " let s=angular.element(document.querySelector('#txtFirstName')||document.body).scope(), pd=null;"
                + " for(let i=0;i<12&&s;i++){ if(s.PatientData){ pd=s.PatientData; break; } s=s.$parent; }"
                + " pd=pd||{};"
                + " return [txt('PatientData.AppointmentTypeID'), txt('PatientData.DoctorModalityID'),"
                + "   String(pd.AppointmentDate||''), String(pd.FromApptTime||''), String(pd.ToApptTime||'')]; }");
        String[] out = {"", "", "", "", ""};
        if (r instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) r;
            for (int i = 0; i < out.length && i < l.size(); i++) out[i] = l.get(i) == null ? "" : l.get(i).toString().trim();
        }
        return out;
    }
}
