package com.kpj.pages.Op_page.Appointment;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

/**
 * MultipleAppointmentBookingPage — Page Object Model for
 * OP &gt; Appointment &gt; Multiple Appointment Booking.
 */
public class MultipleAppointmentBookingPage extends BasePage {

    private static final String MODULE_URL = "/#/MultipleAppointmentBooking";

    // ---- constructor ------------------------------------------------------

    public MultipleAppointmentBookingPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    /**
     * Navigate to the module by clicking through the menu:
     * Login Page → OP menu → Appointment submenu → Multiple Appointment Booking link.
     * Note: User must be logged in before calling this method.
     */
    public void navigateTo(String baseUrl) {
        // Use in-app hash navigation instead of page.goto() to avoid AngularJS SPA reloading issues
        page.evaluate("() => { window.location.hash = '#/MultipleAppointmentBooking'; }");
        waitForAngular(3000);

        // Fallback: if not rendered, expand OP menu and click
        boolean ready = Boolean.TRUE.equals(page.evaluate(
                "() => !!document.querySelector(\"select[ng-model*='BookingType']\")"));
        if (!ready) {
            page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP'); if(op) op.click(); }");
            waitForAngular(1000);
            page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/MultipleAppointmentBooking'); if(a) a.click(); }");
            waitForAngular(3000);
        }
    }

    /** Debug: take a screenshot of current page state. */
    public void debugScreenshot(String path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(path)));
    }

    /** Debug: current page URL. */
    public String currentUrl() {
        return page.url();
    }

    /** Debug: count of FirstName inputs present in the DOM. */
    public int countFirstNameInputsInDOM() {
        return page.locator(inputByNgModel("FirstName")).count();
    }

    // ---- locators ---------------------------------------------------------

    // General / shared
    private String firstSelect()                         { return "select"; }
    private String anySelect(String ngModel)            { return "select[ng-model*='" + ngModel + "']"; }
    private String inputByNgModel(String partial)       { return "input[ng-model*='" + partial + "']"; }

    // Patient details
    private String titleSelect()                        { return firstSelect(); }
    private String firstNameInput()                     { return inputByNgModel("FirstName"); }
    private String nationalIdInput()                    { return inputByNgModel("NationalId"); }
    private String dateOfBirthInput()                   { return inputByNgModel("DateOfBirth"); }
    private String emailInput()                         { return inputByNgModel("Email"); }
    private String mobileNoInput()                      { return inputByNgModel("MobileNo"); }

    // Booking configuration
    private String bookingTypeSelect()                  { return anySelect("BookingType"); }
    private String payorTypeSelect()                    { return anySelect("PayorType"); }
    private String appointmentTypeSelect()              { return anySelect("AppointmentType"); }

    // Table
    private String appointmentTable()                    { return "table"; }
    private String tableCheckbox()                      { return "input[type=checkbox]"; }

    // Save button
    private String saveButton()                          { return "button:has-text('Save')"; }

    // Dialog / toast
    private String toastMessage()                        { return ".toast-message"; }
    private String toastTitle()                          { return ".toast-title"; }
    private String toastAny()                            { return "[class*=toast]"; }
    private String alertBox()                            { return ".alert"; }

    // ---- patient details --------------------------------------------------

    /**
     * Fill all patient detail fields with randomly generated valid data:
     * Title, Full Name, NRIC, Date of Birth, Email, Mobile Number.
     */
    public void fillPatientDetails() {
        waitForAngular(2000);
        selectTitleMr();
        page.locator(firstNameInput()).first().fill(randomName());
        waitForAngular(200);
        page.locator(nationalIdInput()).first().fill(randomNRIC());
        waitForAngular(200);
        page.locator(dateOfBirthInput()).first().fill(randomDOB());
        waitForAngular(200);
        closeDatePicker();
        page.locator(emailInput()).first().fill(randomEmail());
        waitForAngular(200);
        page.locator(mobileNoInput()).first().fill(randomMobile());
    }

    /**
     * Dismiss the _720kb date-picker popup that opens when a date field is filled.
     *
     * <p>While it is open its calendar header overlays the fields BELOW the date input, and Playwright
     * refuses to act on a covered element — every hover/click retries until the 30s timeout with
     * "…_720kb-datepicker-calendar-header-middle… intercepts pointer events". The previous approach
     * (hover the Email field to dismiss it) could not work: Email is precisely the field being covered.
     * Escape first, then a click on a neutral area; if the popup still hangs around, hide it directly.</p>
     */
    public void closeDatePicker() {
        try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
        waitForAngular(150);
        // Click a harmless spot well away from the calendar (top-left gutter) to blur the date input.
        try { page.mouse().click(5, 5); } catch (Exception ignore) { }
        waitForAngular(150);
        // Last resort: the popup is a sibling div that ignores blur on some builds — take it out of the
        // layout so it cannot intercept pointer events. Angular re-creates it next time a date is focused.
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(d => {"
                + " if(d.getBoundingClientRect().width>0) d.style.display='none'; }); }");
        waitForAngular(150);
    }

    /** Select Title = Mr (index 1 in the first dropdown). */
    public void selectTitleMr() {
        try {
            page.locator(titleSelect()).first().selectOption(new SelectOption().setIndex(1));
        } catch (Exception e) {
            // Fallback: direct DOM manipulation
            selectOptionByIndex(titleSelect(), 1);
        }
        waitForAngular(300);
    }

    /** Select Gender (Male). */
    public void selectGenderMale() {
        waitForAngular(500);
        page.evaluate("" +
                "(function() { " +
                "var selects = document.querySelectorAll('select[ng-model]'); " +
                "for(var i=0; i<selects.length; i++) { " +
                "  var s = selects[i]; " +
                "  if(s.getAttribute('ng-model') && s.getAttribute('ng-model').indexOf('GenderID') >= 0) { " +
                "    for(var j=0; j<s.options.length; j++) { " +
                "      if(s.options[j].text && (s.options[j].text.indexOf('Male') >= 0 || s.options[j].text.indexOf('Female') >= 0)) { " +
                "        s.value = s.options[j].value; s.dispatchEvent(new Event('change', {bubbles: true})); return; } } } } } )()");
        waitForAngular(300);
    }

    /** Fill Age in years. */
    public void fillAge(String years) {
        waitForAngular(500);
        page.evaluate("" +
                "(function(y) { " +
                "var inputs = document.querySelectorAll('input[ng-model]'); " +
                "for(var i=0; i<inputs.length; i++) { " +
                "  var input = inputs[i]; " +
                "  var ngModel = input.getAttribute('ng-model'); " +
                "  if(ngModel === 'PatientData.AgeYear') { input.value = y; input.dispatchEvent(new Event('input', {bubbles: true})); } " +
                "  if(ngModel === 'PatientData.AgeMonth') { input.value = '0'; input.dispatchEvent(new Event('input', {bubbles: true})); } " +
                "  if(ngModel === 'PatientData.AgeDays') { input.value = '0'; input.dispatchEvent(new Event('input', {bubbles: true})); } " +
                "} } )('" + years + "')");
        waitForAngular(300);
    }

    /** Select receivableid (Self pay). */
    public void selectReceivableId() {
        waitForAngular(500);
        page.evaluate("" +
                "(function() { " +
                "var selects = document.querySelectorAll('select[ng-model]'); " +
                "for(var i=0; i<selects.length; i++) { " +
                "  var s = selects[i]; " +
                "  if(s.getAttribute('ng-model') && s.getAttribute('ng-model').indexOf('receivableid') >= 0) { " +
                "    for(var j=1; j<s.options.length && j<20; j++) { " +
                "      if(s.options[j].text && (s.options[j].text.indexOf('Self') >= 0 || s.options[j].text.indexOf('Cash') >= 0)) { " +
                "        s.value = s.options[j].value; s.dispatchEvent(new Event('change', {bubbles: true})); return; } } } } } )()");
        waitForAngular(300);
    }

    // ---- booking configuration --------------------------------------------

    /**
     * Set Booking Type to "KPJ". DevHIS renamed this option to "KPJ Portal" (the old marker-based
     * lookup — a select containing both "KPJ" and "Email Campaign" as exact option texts — stopped
     * matching anything once that rename shipped). Selected via ng-model + substring match instead,
     * so a future rename of the visible text still resolves as long as it still contains "KPJ".
     */
    public void selectBookingTypeKPJ() {
        waitForAngular(500);
        setSelectByNgModel("BookingType", "KPJ", "Booking Type");
    }

    /**
     * Set Payor Type. DevHIS replaced the old Self/Banks/THKD list with a full financial-class list
     * (COMPANY, INSURANCE, PATIENT, GOVERNMENT AGENCY, …) — "Self" no longer exists as an option.
     * "ALL FINANCIAL CLASS" (the first real option) is a meta/filter entry, not a real self-pay
     * class — selecting it rejected Save with "Please Enter Passport No.!". "PATIENT" is the actual
     * self-pay financial class in this list, so target that specifically.
     */
    public void selectPayorTypeSelf() {
        waitForAngular(1500);
        setSelectByNgModel("receivabletypeid", "PATIENT", "Payor Type");
    }

    /**
     * Robustly select a DevHIS select2 dropdown by its visible option text.
     *
     * <p>Finds the &lt;select&gt; that contains ALL the given marker options, selects the target
     * option by setting the native value and firing a native {@code change} event — this lets
     * AngularJS decode its {@code ng-options} value and lets select2 repaint its visible box (the
     * same minimal pattern the Department/Gender selectors use; it deliberately avoids
     * {@code $setViewValue}, which pushes the raw encoded option value into the model). It reads
     * the selection back and retries to tolerate options that load a moment late, and throws a
     * clear exception if the value never sticks — so the step can never silently no-op.</p>
     *
     * @param markerOptions option texts that uniquely identify the target &lt;select&gt;
     * @param optionText    the option to select (matched case-insensitively, trimmed)
     * @param label         human-readable field name, used in the failure message
     */
    private void setSelect2ByOptionText(java.util.List<String> markerOptions, String optionText, String label) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            Object res = page.evaluate(
                    "(args) => {" +
                    "  const markers = args[0], text = args[1];" +
                    "  let el = null;" +
                    "  for (const s of document.querySelectorAll('select')) {" +
                    "    const opts = [...s.options].map(o => (o.text || '').trim());" +
                    "    if (markers.every(t => opts.includes(t))) { el = s; break; }" +
                    "  }" +
                    "  if (!el) return { ok:false, status:'select-not-found' };" +
                    "  const opt = [...el.options].find(o => (o.text || '').trim().toLowerCase() === text.toLowerCase());" +
                    "  if (!opt) return { ok:false, status:'option-not-found' };" +
                    "  el.value = opt.value;" +
                    "  el.selectedIndex = opt.index;" +
                    "  el.dispatchEvent(new Event('change', { bubbles: true }));" +   // Angular decodes + select2 repaints
                    "  const $ = window.jQuery || window.$;" +
                    "  if ($) { try { $(el).trigger('change'); } catch (e) {} try { $(el).select2('val', el.value); } catch (e) {} }" +
                    "  const scope = window.angular && angular.element(el).scope();" +
                    "  if (scope) scope.$applyAsync();" +
                    "  const ctrl = window.angular && angular.element(el).controller('ngModel');" +
                    "  let display = '';" +
                    "  try { display = ($ && $(el).select2('container').find('.select2-chosen').text() || '').trim(); } catch (e) {}" +
                    "  return { ok:true, selectedText: (el.options[el.selectedIndex].text || '').trim()," +
                    "           model: ctrl ? String(ctrl.$modelValue) : null, display: display };" +
                    "}",
                    java.util.List.of(markerOptions, optionText));

            java.util.Map<String, Object> m = asMap(res);
            boolean selected = Boolean.TRUE.equals(m.get("ok"))
                    && optionText.equalsIgnoreCase(String.valueOf(m.get("selectedText")));
            if (selected) {
                waitForAngular(400);
                return;
            }
            last = new RuntimeException("Could not select " + label + " = '" + optionText
                    + "' (attempt " + attempt + "/5): " + m);
            waitForAngular(800);
        }
        throw last;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> asMap(Object o) {
        return (o instanceof java.util.Map) ? (java.util.Map<String, Object>) o : java.util.Map.of();
    }

    /**
     * Select a dropdown identified by an ng-model substring, matching the option text exactly first
     * and falling back to a case-insensitive substring match. The substring fallback tolerates DevHIS
     * renaming an option's visible text (e.g. "KPJ" → "KPJ Portal") without silently no-op'ing — the
     * same pattern {@code window.__setSel} uses elsewhere in this suite. Retries, verifies, throws on
     * failure.
     */
    private void setSelectByNgModel(String ngModelPart, String optionText, String label) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            Object res = page.evaluate(
                    "(args) => {" +
                    "  const ngPart = args[0], text = args[1];" +
                    "  const el = [...document.querySelectorAll('select')].find(s => (s.getAttribute('ng-model')||'').indexOf(ngPart) >= 0);" +
                    "  if (!el) return { ok:false, status:'select-not-found' };" +
                    "  let opt = [...el.options].find(o => (o.text||'').trim().toLowerCase() === text.toLowerCase());" +
                    "  if (!opt) opt = [...el.options].find(o => (o.text||'').toLowerCase().includes(text.toLowerCase()));" +
                    "  if (!opt) return { ok:false, status:'option-not-found' };" +
                    "  el.value = opt.value; el.selectedIndex = opt.index;" +
                    "  el.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "  const $ = window.jQuery || window.$;" +
                    "  if ($) { try { $(el).trigger('change'); } catch (e) {} try { $(el).select2('val', el.value); } catch (e) {} }" +
                    "  const scope = window.angular && angular.element(el).scope();" +
                    "  if (scope) scope.$applyAsync();" +
                    "  return { ok:true, selectedText: (el.options[el.selectedIndex].text || '').trim() };" +
                    "}",
                    java.util.List.of(ngModelPart, optionText));

            java.util.Map<String, Object> m = asMap(res);
            if (Boolean.TRUE.equals(m.get("ok"))) {
                waitForAngular(400);
                return;
            }
            last = new RuntimeException("Could not select " + label + " (ng-model*='" + ngModelPart
                    + "' containing '" + optionText + "') (attempt " + attempt + "/5): " + m);
            waitForAngular(800);
        }
        throw last;
    }

    /** Set Appointment Type to "Department" (select2 — verified). */
    public void selectAppointmentTypeDepartment() {
        waitForAngular(2000);
        setSelect2ByOptionText(java.util.List.of("Department", "Doctor", "Modality"), "Department", "Appointment Type");
    }

    /** Select a doctor from the first available doctor option in any select. */
    public void selectDoctor() {
        waitForAngular(2000);
        selectOptionInAnySelectByText("Doctor");
        waitForAngular(1000);
    }

    /** Select Department for Appointment 1. */
    public void selectDepartment1() {
        waitForAngular(500);
        Object result = page.evaluate("" +
                "(function() { " +
                "var selects = document.querySelectorAll('select[ng-model]'); " +
                "for(var i=0; i<selects.length; i++) { " +
                "  var s = selects[i]; " +
                "  if(s.getAttribute('ng-model') === 'PatientData.DepartmentID' && s.options.length > 1) { " +
                "    var opt = s.options[1]; var prev = s.value; " +
                "    s.value = opt.value; s.dispatchEvent(new Event('change', {bubbles: true})); " +
                "    return 'changed:' + prev + '->' + opt.text + '(opt[1] of ' + s.options.length + ')'; } } " +
                "return 'not-found'; } )()");
        System.out.println("Department1 result: " + result);
        waitForAngular(500);
    }

    /** Select Department for Appointment 2. */
    public void selectDepartment2() {
        waitForAngular(500);
        Object result = page.evaluate("" +
                "(function() { " +
                "var selects = document.querySelectorAll('select[ng-model]'); " +
                "for(var i=0; i<selects.length; i++) { " +
                "  var s = selects[i]; " +
                "  if(s.getAttribute('ng-model') === 'PatientData.DepartmentID1' && s.options.length > 1) { " +
                "    var opt = s.options[2]; var prev = s.value; " +
                "    s.value = opt.value; s.dispatchEvent(new Event('change', {bubbles: true})); " +
                "    return 'changed:' + prev + '->' + opt.text + '(opt[2] of ' + s.options.length + ')'; } } " +
                "return 'not-found'; } )()");
        System.out.println("Department2 result: " + result);
        waitForAngular(2000);
    }

    /** Select Department for Appointment 3. */
    public void selectDepartment3() {
        waitForAngular(500);
        Object result = page.evaluate("" +
                "(function() { " +
                "var selects = document.querySelectorAll('select[ng-model]'); " +
                "for(var i=0; i<selects.length; i++) { " +
                "  var s = selects[i]; " +
                "  if(s.getAttribute('ng-model') === 'PatientData.DepartmentID2' && s.options.length > 1) { " +
                "    var opt = s.options[3]; var prev = s.value; " +
                "    s.value = opt.value; s.dispatchEvent(new Event('change', {bubbles: true})); " +
                "    return 'changed:' + prev + '->' + opt.text + '(opt[3] of ' + s.options.length + ')'; } } " +
                "return 'not-found'; } )()");
        System.out.println("Department3 result: " + result);
        waitForAngular(2000);
    }

    /** Set the appointment date (for all 3 appointments). */
    public void setAppointmentDate(String date) {
        page.evaluate("(function(d) { " +
                "var inputs = document.querySelectorAll('input[ng-model]'); " +
                "for (var i = 0; i < inputs.length; i++) { " +
                "  var el = inputs[i]; " +
                "  var ng = (el.getAttribute('ng-model') || '').toLowerCase(); " +
                "  if (ng.indexOf('appointmentdate') >= 0 || ng.indexOf('slotdate') >= 0) { " +
                "    var c = angular.element(el).controller('ngModel'); " +
                "    el.value = d; " +
                "    if (c) { c.$setViewValue(d); c.$render(); } " +
                "    el.dispatchEvent(new Event('input', {bubbles: true})); " +
                "    el.dispatchEvent(new Event('change', {bubbles: true})); " +
                "  } } " +
                "})('" + date + "')");
        System.out.println("Appointment date set to: " + date);
        waitForAngular(1000);
    }

    // ---- appointment slot selection ----------------------------------------

    /**
     * Select exactly 1 slot checkbox for EACH of the 3 appointments.
     *
     * <p>DevHIS renders two slot tables per appointment — {@code ng-repeat="val in CurrentSchedule"} /
     * {@code NextSchedule} for appointment 1, with a "1"/"2" suffix (e.g. {@code CurrentSchedule1}) for
     * appointments 2 and 3 — six {@code <table>} elements in total, not three. The previous
     * implementation treated every {@code <table>} on the page as one flat pool and stopped after 3
     * clicks total; since appointment 1 alone contributes 2 such tables, it could exhaust all 3 clicks
     * on appointments 1–2 and leave appointment 3 with zero slots selected — Save then silently no-ops
     * (no toast, no confirm dialog) because the app requires a slot for every appointment. Selecting by
     * appointment index instead (preferring that appointment's Current-schedule table, falling back to
     * Next) guarantees one real click lands in each of the 3 appointments.</p>
     */
    public void selectAppointmentSlots() {
        // Wait for appointment tables to render after date change
        waitForAngular(3000);

        int clicked = 0;
        for (int apptIndex = 0; apptIndex < 3; apptIndex++) {
            // appointment 1 has no numeric suffix; appointments 2/3 use "1"/"2"
            String suffix = apptIndex == 0 ? "" : String.valueOf(apptIndex);
            Object tagged = page.evaluate(
                    "(suffix) => {" +
                    "  const tables = [...document.querySelectorAll('table')];" +
                    "  const findByRepeat = (name) => {" +
                    "    const t = tables.find(tb => { const row = tb.querySelector('tr[ng-repeat]');" +
                    "      return row && row.getAttribute('ng-repeat') === ('val in ' + name); });" +
                    "    return t ? t.querySelector('input[type=checkbox]:not(:disabled):not(:checked)') : null;" +
                    "  };" +
                    "  const cb = findByRepeat('CurrentSchedule' + suffix) || findByRepeat('NextSchedule' + suffix);" +
                    "  if (!cb) return false;" +
                    "  cb.id = '__mabSlotCur'; return true;" +
                    "}", suffix);
            if (!Boolean.TRUE.equals(tagged)) {
                System.out.println("selectAppointmentSlots: no available slot found for appointment " + (apptIndex + 1));
                continue;
            }
            // Real Playwright click — a synthetic in-page cb.click() does NOT fire Angular's ng-click,
            // so the slot is never actually selected (the exact bug that broke the reschedule slot).
            try {
                page.locator("#__mabSlotCur").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                clicked++;
            } catch (Exception e) {
                System.out.println("selectAppointmentSlots: real click failed for appointment " + (apptIndex + 1)
                        + " (" + e.getMessage() + ")");
            }
            page.evaluate("() => { const e=document.getElementById('__mabSlotCur'); if(e) e.removeAttribute('id'); }");
            waitForAngular(700);
        }
        System.out.println("Appointment slots clicked via Playwright: " + clicked + " (1 per appointment, 3 appointments)");
        waitForAngular(1500);
    }

    /**
     * @deprecated Use {@link #selectAppointmentSlots()} — count parameter was ignored.
     */
    @Deprecated
    public void selectAppointmentSlots(int count) {
        selectAppointmentSlots();
    }

    // ---- save & toast ------------------------------------------------------

    /**
     * Click the Save button, accept the confirmation dialog,
     * wait for the toast/alert to appear, and return its message text.
     *
     * @return the toast message text, or empty string if none found
     */
    public String clickSaveAndAccept() {
        waitForAngular(1000);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
        waitForAngular(3000);

        acceptSaveDialog();         // click "SAVE" in the "Do You Want To Save" dialog
        waitForAngular(5000);

        return extractToastMessage();
    }

    /**
     * Whether this screen has a Nationality field at all. The single Book Appointment screen sets
     * {@code PatientData.NationalityID} (see {@code BookAppointmentPage.fillMandatoryDetails}), but
     * Multiple Appointment Booking has no such select/input — there is nothing this automation (or a
     * manual user) can fill to resolve Nationality here. When Save is rejected with a Passport-No.
     * requirement, that absence is the likely cause: the backend can't resolve Nationality and falls
     * back to demanding a passport.
     */
    public boolean hasNationalityField() {
        Object res = page.evaluate("() => [...document.querySelectorAll('select,input')]"
                + ".some(e => (e.getAttribute('ng-model')||'').toLowerCase().indexOf('nationality') >= 0)");
        return Boolean.TRUE.equals(res);
    }

    /**
     * Extract visible toast/alert message text from the page.
     */
    public String extractToastMessage() {
        Object result = page.evaluate("(function() { " +
                "var selectors = ['.toast-message', '.toast-title', '[class*=toast]', '.alert'];" +
                "var found = '';" +
                "for (var i = 0; i < selectors.length; i++) { " +
                "  var el = document.querySelector(selectors[i]);" +
                "  if (el && el.offsetParent !== null && el.innerText.trim()) { found = el.innerText.trim(); break; } }" +
                "return found; })()");
        return result != null ? result.toString() : "";
    }

    // ---- screenshot --------------------------------------------------------

    @Override
    public void screenshot(String path) {
        super.screenshot(path);
    }
}
