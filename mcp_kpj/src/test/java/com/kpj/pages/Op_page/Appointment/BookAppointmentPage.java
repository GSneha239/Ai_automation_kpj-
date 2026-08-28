package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * BookAppointmentPage — Page Object Model for OP &gt; Appointment &gt; Book Appointment (#/New).
 *
 * <p>Flow (verified live): navigate via the OP menu, fill the mandatory patient + appointment
 * details, pick a free schedule slot, Save → confirm the "Do You Want To Save" dialog → accept
 * the optional "slot already booked" prompt → the appointment report opens in a new tab.</p>
 *
 * <p>DevHIS specifics handled here:
 * <ul>
 *   <li>select2/ng-options dropdowns are set by changing selectedIndex + firing a native change
 *       so AngularJS decodes the value;</li>
 *   <li>the Save button is <code>ng-disabled="!IsClicked"</code> — re-enabled before clicking;</li>
 *   <li>the confirm is a jQuery-confirm (<code>.ng-confirm-box</code>) whose buttons have no inline
 *       handler, so they are clicked with a real Playwright click;</li>
 *   <li>same-day slots are disabled and already-booked slots are red — a free slot from the
 *       Next Schedule is used, and From/To time is set to match.</li>
 * </ul>
 */
public class BookAppointmentPage extends BasePage {

    public BookAppointmentPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /** Open OP &gt; Appointment &gt; Book Appointment and wait for the form + dropdown master-data. */
    public void navigateTo(String baseUrl) {
        // Use COMMIT, not the default LOAD — under server load the AngularJS SPA takes >30s to even reach
        // domcontentloaded, so page.navigate() timed out. COMMIT resolves as soon as the server responds; the
        // waits below (form input + Payor Type options, 40s each) are the real readiness signal.
        page.navigate(baseUrl + "/#/PatientDashboard",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT)
                        .setTimeout(60000));
        waitForAngular(2000);

        // Open the Book Appointment form. On a slow SPA boot the menu links aren't clickable yet, so the
        // #/New click can miss and the form never loads — retry (expand OP → click #/New; direct #/New route
        // as a fallback) until #txtFirstName appears.
        boolean formUp = false;
        for (int attempt = 0; attempt < 3 && !formUp; attempt++) {
            // Wait for the OP menu anchor to exist before clicking it.
            try {
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x => x.textContent.trim() === 'OP')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const op = [...document.querySelectorAll('a')].find(x => x.textContent.trim() === 'OP'); if (op) op.click(); }");
            waitForAngular(800);
            page.evaluate("() => { const a = [...document.querySelectorAll('a')].find(x => x.getAttribute('href') === '#/New'); if (a) a.click(); }");
            waitForAngular(1000);
            try {
                page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(20000));
                formUp = true;
            } catch (Exception e) {
                System.out.println("BookAppointment.navigateTo: form not up via menu (attempt " + (attempt + 1) + ") — trying the direct #/New route");
                try {
                    page.navigate(baseUrl + "/#/New",
                            new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                } catch (Exception ex) { System.out.println("BookAppointment.navigateTo: direct route failed - " + ex.getMessage()); }
                waitForAngular(2000);
            }
        }
        page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(40000));
        // dropdown options load asynchronously — wait for Payor Type to populate
        page.waitForFunction(
                "() => { const e = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'PatientData.receivabletypeid'); return e && e.options.length > 1; }",
                null, new Page.WaitForFunctionOptions().setTimeout(40000));
        waitForAngular(1500);
        installHelpers();
    }

    /** Install in-page JS helpers (select setter that decodes ng-options; ng-model input setter). */
    protected void installHelpers() {
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
                + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return ng+':'+e.value; };"
                + "}");
    }

    // ---- fill -------------------------------------------------------------

    /** Fill all mandatory patient + appointment configuration fields. */
    public void fillMandatoryDetails() {
        String suffix = uniqueId();
        // Dropdowns
        page.evaluate("() => {"
                + "window.__setSel('PatientData.PrefixID','Mr.');"
                + "window.__setSel('PatientData.GenderID','Male');"
                + "window.__setSel('PatientData.NationalityID','Malaysia');"
                + "window.__setSel('PatientData.receivabletypeid','Patient');"
                + "window.__setSel('PatientData.BookingTypeID','KPJ');"
                + "window.__setSel('PatientData.AppointmentTypeID','Department');"
                + "window.__setSel('PatientData.DepartmentID','Cardiac Surgery');"
                + "}");
        waitForAngular(500);
        // Name — .fill() alone is unreliable here (AngularJS re-render), so fill, then force the
        // ng-model value + input event, then verify; retry with per-key typing if still empty.
        setPatientName("AutoBook " + suffix);
        // text/number fields
        page.evaluate("(s) => {"
                + "window.__setInp('PatientData.NationalId','9002020' + (s.slice(0,5)+'0').slice(0,5));"
                + "window.__setInp('PatientData.DateOfBirth','02/02/1990');"
                + "window.__setInp('PatientData.Email','autobook' + s + '@example.com');"
                + "window.__setInp('PatientData.MobileNo','19' + (s+'0000000').slice(0,7));"
                + "window.__setInp('PatientData.AgeYear','36');"
                + "window.__setInp('PatientData.AgeMonth','0');"
                + "window.__setInp('PatientData.AgeDays','0');"
                + "}", suffix);
        waitForAngular(500);
        // Doctor (loads after department)
        page.evaluate("() => window.__setSel('PatientData.DoctorModalityID','Demo Doctor')");
        waitForAngular(800);
    }

    /**
     * Set the patient Name (#txtFirstName / ng-model PatientData.FirstName). The field has an
     * auto-complete widget that clears the input when an <code>input</code> event fires and no
     * patient matches, so we set the AngularJS MODEL directly (PatientData.FirstName) inside
     * $apply and $render the input — no input event. Verifies the model took; throws otherwise.
     */
    private void setPatientName(String name) {
        Object val = page.evaluate("(v) => { const e=document.querySelector('#txtFirstName'); if(!e) return 'no-el';"
                + " let s=angular.element(e).scope(), pd=null; for(let i=0;i<8&&s;i++){ if(s.PatientData){pd=s.PatientData;break;} s=s.$parent; }"
                + " if(!pd) return 'no-model';"
                + " s.$apply(function(){ pd.FirstName = v; });"
                + " const c=angular.element(e).controller('ngModel'); if(c) c.$render();"
                + " return pd.FirstName; }", name);
        if (!name.equalsIgnoreCase(String.valueOf(val).trim())) {
            throw new RuntimeException("Patient Name not set (got: '" + val + "')");
        }
        waitForAngular(300);
    }

    /**
     * Set Appointment Date to TOMORROW. Confirmed live: the Next/Current Schedule grid only
     * (re)loads once {@code PatientData.AppointmentDate} actually changes — left at its default,
     * the Next Schedule table can still be mid-load (or stale) when {@link #selectFreeSlot()}
     * checks it, causing an intermittent "Next Schedule slots did not load" failure. Setting the
     * date explicitly first (as {@code ExistingPatientAppointmentPage} already does) triggers that
     * reload and gives the grid time to populate before slots are picked.
     */
    public String setAppointmentDateTomorrow() {
        Object v = page.evaluate("() => { const d=new Date(Date.now()+86400000); const dd=String(d.getDate()).padStart(2,'0'), mm=String(d.getMonth()+1).padStart(2,'0'), yyyy=d.getFullYear();"
                + " const val=dd+'/'+mm+'/'+yyyy; window.__setInp('PatientData.AppointmentDate', val); return val; }");
        waitForAngular(1000);
        return v == null ? "" : v.toString();
    }

    /**
     * Set the appointment date to the Next-Schedule day and select a free (enabled, not-booked)
     * slot, filling From/To time to match. Returns the selected From time, or null if none free.
     */
    public String selectFreeSlot() {
        // The schedule grid loads asynchronously after department + doctor are set — wait for the
        // Next Schedule table to have enabled slot checkboxes before selecting.
        try {
            page.waitForFunction(
                    "() => { const t=[...document.querySelectorAll('table')].find(x=>/Next Schedule/.test(x.caption&&x.caption.textContent||''));"
                            + "return t && [...t.querySelectorAll('tbody tr input[type=checkbox]')].some(c=>!c.disabled); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            System.out.println("selectFreeSlot: Next Schedule slots did not load");
        }
        Object r = page.evaluate("() => {"
                + "const nextT=[...document.querySelectorAll('table')].find(t=>/Next Schedule/.test(t.caption&&t.caption.textContent||''));"
                + "if(!nextT) return null;"
                + "const cap=(nextT.caption.textContent||'');"
                + "const m=cap.match(/(\\d{2}\\/\\d{2}\\/\\d{4})/); if(m) window.__setInp('PatientData.AppointmentDate', m[1]);"
                + "const rows=[...nextT.querySelectorAll('tbody tr')];"
                + "rows.forEach(row=>{ const cb=row.querySelector('input[type=checkbox]'); if(cb && cb.checked) cb.click(); });"  // clear pre-checked
                + "const isRed=cell=>{ if(!cell) return false; const bg=getComputedStyle(cell).backgroundColor; return /rgb\\(2\\d\\d,\\s*\\d?\\d,/.test(bg); };"
                + "const pick=row=>{ const cb=row.querySelector('input[type=checkbox]'); const tds=row.querySelectorAll('td');"
                + "  const from=((tds[1]||{}).textContent||'').trim(); const to=((tds[2]||{}).textContent||'').trim();"
                + "  cb.click(); window.__setInp('PatientData.FromApptTime', from.replace(/\\s+/g,' '));"
                + "  window.__setInp('PatientData.ToApptTime', to.replace(/\\s+/g,' ')); return from; };"
                + "let free=rows.find(row=>{ const cb=row.querySelector('input[type=checkbox]'); return cb && !cb.disabled && !isRed(row.querySelectorAll('td')[1]); });"
                + "if(free) return pick(free);"
                + "let any=rows.find(row=>{ const cb=row.querySelector('input[type=checkbox]'); return cb && !cb.disabled; });"  // fallback: first enabled (may prompt already-booked)
                + "if(any) return pick(any);"
                + "return null; }");
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /**
     * Click the "Add" button (fnAddToMultipleApptList) which appends the current appointment to
     * the internal list (multipleApptBookingList). This is REQUIRED before Save — otherwise the
     * save posts an empty AppointmentListJsonString and no report is generated. Returns the list
     * size after adding (0 means the Add did not register).
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

    // ---- save & report ----------------------------------------------------

    /**
     * Click Save, confirm the "Do You Want To Save" dialog, accept the optional "slot already
     * booked" prompt, and capture the appointment report that opens in a new tab.
     *
     * @return the report tab Page, or null if no new tab opened.
     */
    public Page saveAndCaptureReport() {
        enableAndClickSaveButton();
        // Wait for the jQuery-confirm "Do You Want To Save" dialog. If it doesn't appear a
        // validation JAlert likely fired instead — capturedAlerts() will show it; return null.
        try {
            page.waitForSelector(".ng-confirm-box button", new Page.WaitForSelectorOptions().setTimeout(20000));
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no confirm dialog (validation?). Alerts: " + capturedAlerts());
            return null;
        }
        Page reportTab = null;
        try {
            reportTab = page.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(60000),
                    () -> {
                        clickNgConfirmButton("Save");           // confirm "Do You Want To Save"
                        page.waitForTimeout(3000);
                        clickNgConfirmButton("Yes");            // optional "slot already booked?" -> Yes
                        page.waitForTimeout(3000);
                    });
        } catch (Exception e) {
            System.out.println("saveAndCaptureReport: no report tab opened (" + e.getMessage() + ")");
        }
        if (reportTab != null) {
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(3000);
        }
        return reportTab;
    }

    /** Ensure the Save button is enabled (ng-disabled="!IsClicked") then click it. */
    private void enableAndClickSaveButton() {
        page.evaluate("() => { const b=document.querySelector('#btnSave'); if(!b) return;"
                + "try { const sc=angular.element(b).scope(); sc.$apply(function(){ sc.IsClicked=true; }); } catch(e){}"
                + "window.__alerts=[]; document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); b.click(); }");
        waitForAngular(600);
    }

    /**
     * Click a button (by trimmed text) inside the visible jQuery-confirm box with a REAL Playwright
     * click. No-op if that button isn't currently shown. Tags it with an id first for a stable click.
     */
    private void clickNgConfirmButton(String text) {
        Object tagged = page.evaluate("(t) => { const box=[...document.querySelectorAll('.ng-confirm-box')].find(m=>m.getBoundingClientRect().width>0);"
                + "if(!box) return false; const b=[...box.querySelectorAll('button')].find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase() && x.getBoundingClientRect().width>0);"
                + "if(!b) return false; b.id='__ngConfirmBtn'; return true; }", text);
        if (Boolean.TRUE.equals(tagged)) {
            try {
                page.locator("#__ngConfirmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            } catch (Exception ignore) { /* dialog may have closed already */ }
        }
    }

    /** Return the captured JAlert/toast messages recorded during the flow. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> capturedAlerts() {
        Object r = page.evaluate("() => window.__alerts || []");
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    // ---- screenshot -------------------------------------------------------

    @Override
    public void screenshot(String path) {
        super.screenshot(path);
    }
}
