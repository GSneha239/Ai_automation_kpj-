package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Notification</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> (submenu, EXACT text — a prefix match can expand
 * a different module) → <b>Notification</b> (route {@code #/Notification}, best-guess fallback — navigation
 * is primarily by the live menu link) → (Add, if the screen has one) → enter <b>Notification Date*</b> +
 * <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both fields are DISCOVERED at runtime rather than hard-coded (this screen's exact ng-model names were not
 * yet confirmed live). Grid filter boxes ({@code colFilter.term}) and the textAngular artefact are excluded.</p>
 */
public class Notification extends BasePage {

    public Notification(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/Notification";
    public String lastDate = "", lastRemark = "";
    public String lastDateModel = "", lastRemarkModel = "";
    private java.time.LocalDate lastTargetDate;
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic notification remarks, not "Auto ... <code>" filler. */
    private static final String[] REMARKS = {
            "System Maintenance Notice", "Public Holiday Closure", "New Policy Announcement",
            "Scheduled Downtime Alert", "Emergency Contact Update", "Fire Drill Notice",
            "Staff Meeting Reminder", "Billing Cycle Change Notice", "New Service Launch", "Audit Notice"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** JS helper: label-based lookup — walks up to 5 ancestor levels looking for a &lt;label&gt;. */
    private static final String LABEL_NEAR_JS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const labelNear=e=>{ let t='', p=e.parentElement, hop=0; while(p && hop++<5 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };";

    /** JS helper: the visible Notification Date / Remark inputs, whatever they are called on this screen. */
    private static final String FIND_FIELDS =
            LABEL_NEAR_JS
            + " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='file' && !skip(e.getAttribute('ng-model')));"
            + " const dateEl = boxes.find(e=>e.type==='date' || /date/i.test(e.getAttribute('ng-model')||'') || /date/i.test(labelNear(e)) || /date/i.test(e.placeholder||''));"
            + " const remEl  = boxes.find(e=>e!==dateEl && (/\\.(description|remark)$/i.test(e.getAttribute('ng-model')||'') || /description|remark/i.test((e.getAttribute('ng-model')||'')+' '+labelNear(e)) || /^\\s*(remark|description)\\s*$/i.test(e.placeholder||'')));";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Location" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1200);
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a[href]')].filter(a=>a.offsetParent!==null).length > 3",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                } catch (Exception ignore) { System.out.println("Notification.nav: Location submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*notifications?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                        + "        || links.find(x=>/notification/i.test(x.getAttribute('href')||'') && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__ntMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("Notification.nav DEBUG: visible links => " + dbg);
                    System.out.println("Notification.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__ntMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("Notification.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__ntMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__ntMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("Notification.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("Notification.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Notification screen — verified by a Date/Remark box actually present plus the URL. */
    public boolean onScreen() {
        Object r = page.evaluate("() => {" + FIND_FIELDS
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/notification/i.test((location.hash||'').replace(/\\s+/g,''));"
                + " if(dateEl && remEl) return true;"
                + " if(/^\\s*notification\\s*$/i.test(hdr)) return true;"
                + " return urlOk && [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(dateEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ntAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__ntAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Notification.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ntAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Selects the Notification Date via the app's own <b>calendar picker</b> (a {@code _720kb-datepicker}
     * component, {@code date-format="dd/MM/yyyy"}, always present in the DOM next to the masked text input —
     * no separate trigger icon; clicking the input itself opens/keeps it visible). Clicks the day cell matching
     * {@code day}/{@code month}/{@code year}, walking {@code nextMonth()}/{@code prevMonth()} as needed.
     */
    public boolean selectDateFromPicker(java.time.LocalDate target) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " if(dateEl){ dateEl.id='__ntDate'; return true; } return false; }"));
        if (!tagged) return false;
        try { page.locator("#__ntDate").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Notification.selectDateFromPicker: open failed - " + e.getMessage()); }
        waitForAngular(600);

        String wantMonthYear = target.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH));
        for (int guard = 0; guard < 36; guard++) {
            Object header = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const h=document.querySelector('._720kb-datepicker-calendar-header-middle');"
                    + " return h?norm(h.textContent):''; }");
            String h = header == null ? "" : header.toString();
            if (h.replace(" ", " ").trim().startsWith(wantMonthYear)) break;
            boolean forward = java.time.YearMonth.from(target).isAfter(currentPickerYearMonth(h));
            String fn = forward ? "nextMonth" : "prevMonth";
            page.evaluate("(f) => { const s=[...document.querySelectorAll('a[ng-click]')].find(a=>a.getAttribute('ng-click')===f+'()' && a.offsetParent!==null); if(s) s.click(); }", fn);
            waitForAngular(400);
        }

        int day = target.getDayOfMonth();
        boolean tagged2 = Boolean.TRUE.equals(page.evaluate("(d) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cells=[...document.querySelectorAll('._720kb-datepicker-calendar-body span,._720kb-datepicker-calendar-body td,._720kb-datepicker-calendar-body a')].filter(e=>e.offsetParent!==null);"
                + " const cell=cells.find(e=>norm(e.textContent)===String(d) && !/disabled|_720kb-datepicker-calendar-day-not-in-month/i.test(e.className));"
                + " if(!cell) return false; cell.id='__ntDay'; return true; }", day));
        if (!tagged2) {
            System.out.println("Notification.selectDateFromPicker: day cell " + day + " not found");
            return false;
        }
        try { page.locator("#__ntDay").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Notification.selectDateFromPicker: day click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const d=document.getElementById('__ntDay'); if(d) d.removeAttribute('id'); const e=document.getElementById('__ntDate'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);
        return true;
    }

    private java.time.YearMonth currentPickerYearMonth(String headerText) {
        try {
            String t = headerText.replace(" ", " ").trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z]+)\\s+(\\d{4})").matcher(t);
            if (m.find()) return java.time.YearMonth.of(Integer.parseInt(m.group(2)),
                    java.time.Month.valueOf(m.group(1).toUpperCase(java.util.Locale.ENGLISH)));
        } catch (Exception ignore) { }
        return java.time.YearMonth.now();
    }

    /**
     * Enter <b>Notification Date*</b> (today, or a few days out on retry) and <b>Remark*</b>. {@code attempt}
     * shifts both so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        java.time.LocalDate d = java.time.LocalDate.now().plusDays(attempt);
        lastTargetDate = d;
        // The server rejects on "Description already exist" — on this shared, long-lived QA environment the
        // fixed REMARKS pool is already used up, so even attempt 0 needs a unique suffix, not just retries
        // past REMARKS.length.
        lastRemark = REMARKS[attempt % REMARKS.length] + " "
                + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));

        selectDateFromPicker(d);
        Object dv = page.evaluate("() => {" + FIND_FIELDS + " return dateEl?dateEl.value:''; }");
        lastDate = dv == null ? "" : dv.toString();

        boolean remTagged = Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " if(remEl){ remEl.id='__ntRem'; return true; } return false; }"));
        if (remTagged) {
            try { page.locator("#__ntRem").fill(lastRemark); }
            catch (Exception e) { System.out.println("Notification.fillDetails: remark fill failed - " + e.getMessage()); }
            page.evaluate("() => { const r=document.getElementById('__ntRem'); if(r) r.removeAttribute('id'); }");
        }
        waitForAngular(500);

        Object r = page.evaluate("() => {" + FIND_FIELDS
                + " return 'NotificationDate='+(dateEl?dateEl.value:'(no field)')+' | Remark='+(remEl?remEl.value:'(no field)')"
                + "   +' | [models: '+(dateEl?(dateEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }");
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastDateModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res;
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts Date/Remark first — these
     * forms re-render and can blank a field between fill and click. Falls back to invoking the form's
     * ng-submit handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ntToasts=[]; if(window.__ntObs) window.__ntObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ntToasts.includes(t)) window.__ntToasts.push(t); }); };"
                + " window.__ntObs=new MutationObserver(grab); window.__ntObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        // (See devhis-commonmaster-iud-500 project memory: several sibling commonmaster screens hit the SHARED
        // CommonMaster/IUD endpoint's 500 MappingException — check for that first on any bare "Error!" here.)
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("notification") || resp.url().toLowerCase().contains("commonmaster") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        // Re-assert Remark (real fill) and Date (real calendar-picker click, per selectDateFromPicker's javadoc)
        // — these forms can re-render and blank a field between fillDetails() and Submit. NEVER bulk-set the
        // date via JS .value/$setViewValue here — that mangles the masked input (confirmed live).
        if (lastTargetDate != null) selectDateFromPicker(lastTargetDate);
        boolean remTagged = Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " if(remEl){ remEl.id='__ntRem2'; return true; } return false; }"));
        if (remTagged) {
            try { page.locator("#__ntRem2").fill(lastRemark); }
            catch (Exception e) { System.out.println("Notification.submitAndGetToast: remark re-fill failed - " + e.getMessage()); }
            page.evaluate("() => { const r=document.getElementById('__ntRem2'); if(r) r.removeAttribute('id'); }");
        }

        Object how = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__ntSubmit'; return 'click'; }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }");
        System.out.println("Notification submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__ntSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Notification submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ntSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__ntToasts||[]).some(a=>/notification|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("Notification save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__ntToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels.
     * Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
