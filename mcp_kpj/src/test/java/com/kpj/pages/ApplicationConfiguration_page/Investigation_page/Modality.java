package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Investigation &gt; <b>Modality</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Investigation</b> (submenu) → <b>Modality</b> → (Add, if the
 * screen has one) → enter <b>Code*</b>, <b>Remark*</b>, <b>Ae Title</b>, <b>Time Slot</b>, <b>No. of Patient</b> →
 * select <b>Department</b> and <b>Equipment</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Department/Equipment are selected with a REAL {@code selectOption} so any {@code ng-change} fires, and
 * Equipment is polled after Department in case it is a cascade — a synthetic assignment leaves the second list
 * empty and the save is rejected for a field that looks filled on screen.</p>
 *
 * <p>Fields are DISCOVERED at runtime by LABEL first: sibling screens here name the same two boxes
 * {@code commonmaster.Code}/{@code .Description}, {@code LabOrganism.code}/{@code .description} and
 * {@code LabTestSampleType.SampleTypeCode}/{@code .Remark}, so a fixed model list does not travel.</p>
 */
public class Modality extends BasePage {

    public Modality(Page page) { super(page); }

    public static final String ROUTE = "#/Modality";
    public String lastCode = "", lastRemark = "", lastAeTitle = "", lastTimeSlot = "", lastNoOfPatient = "";
    public String lastDepartment = "", lastEquipment = "";
    public String lastCodeModel = "", lastRemarkModel = "";
    /** Whether the screen's existing rows use all-numeric codes (set by {@link #sampleCodeStyle()}). */
    private boolean numericCodes = true;
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Imaging modalities a radiology department would actually register (Remark). */
    private static final String[] REMARKS = {
            "CT Scan", "MRI Scan", "Digital X-Ray", "Ultrasound", "Mammography", "Fluoroscopy"
    };

    /** DICOM Application Entity titles — these are the real shape: short, upper case, no spaces. */
    private static final String[] AE_TITLES = {
            "CT_SCANNER_01", "MRI_SUITE_A", "XRAY_ROOM_02", "USG_ROOM_01", "MAMMO_UNIT_01", "FLUORO_ROOM_01"
    };

    /**
     * Retry qualifiers for the Remark. The server enforces uniqueness on the DESCRIPTION
     * ({@code "Description already exist"}), not the Code, so rotating a six-item pool can keep landing on rows
     * that already exist — a location qualifier makes each retry genuinely new and still reads like real
     * configuration ("CT Scan - Room 2").
     */
    private static final String[] QUALIFIERS = { "Room 2", "Suite B", "Unit 3", "Mobile Unit", "Annexe" };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /**
     * JS helper: the five text boxes, whatever they are called here. Each match is CLAIMED ({@code used}) so two
     * roles can never resolve to the same input, and the SPECIFIC labels are resolved first — "Ae Title" would
     * otherwise be free to answer a loose fallback meant for another field. select2 chrome
     * ({@code s2id_autogen*}) is skipped; a real {@code <select>} is handled separately.
     */
    private static final String FIND_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const lbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const skip=m=>/colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m||'');"
            + " const s2=e=>/^s2id_autogen/i.test(e.id||'') || (e.tagName!=='SELECT' && /select2/i.test(e.className||''));"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !s2(e) && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||''; const plc=e=>e.placeholder||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            // --- the three specific boxes first ---
            + " const aeEl   = pick(e=>/ae\\s*title/i.test(lbl(e)), e=>/aetitle/i.test(mdl(e).replace(/[^a-z]/gi,'')), e=>/ae\\s*title/i.test(plc(e)));"
            + " const slotEl = pick(e=>/time\\s*slot/i.test(lbl(e)), e=>/timeslot/i.test(mdl(e).replace(/[^a-z]/gi,'')), e=>/time\\s*slot/i.test(plc(e)));"
            + " const patEl  = pick(e=>/no\\.?\\s*of\\s*patient|patient\\s*count|patients?\\s*per/i.test(lbl(e)), e=>/(noof)?patients?$/i.test(mdl(e).replace(/[^a-z]/gi,'')), e=>/patient/i.test(lbl(e)));"
            // --- master ---
            + " const codeEl = pick(e=>/code/i.test(lbl(e)), e=>/\\.code(id)?$/i.test(mdl(e)), e=>/code/i.test(mdl(e)), e=>/^\\s*code\\s*$/i.test(plc(e)));"
            + " const remEl  = pick(e=>/^(remark|description)/i.test(lbl(e)), e=>/\\.(description|remark)$/i.test(mdl(e)), e=>/description|remark/i.test(mdl(e)), e=>/^\\s*(remark|description)\\s*$/i.test(plc(e)));";

    /**
     * JS helper: the Department / Equipment selects. Deliberately NOT filtered on visibility — select2 hides the
     * real {@code <select>} behind class {@code select2-offscreen}, and that is still the element to drive.
     */
    private static final String FIND_SELECTS =
            " const slbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t; };"
            + " const sels=[...document.querySelectorAll('select')].filter(e=>!/pagination/i.test(e.getAttribute('ng-model')||''));"
            + " const hit=(e,re)=>re.test(e.getAttribute('ng-model')||'') || re.test(slbl(e));"
            + " const deptEl = sels.find(e=>hit(e,/department|dept/i));"
            + " const equipEl = sels.find(e=>e!==deptEl && hit(e,/equipment|equip|machine/i));";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Close any open select2 before a real click — its mask sits over the whole page and swallows the click. */
    private void closeSelect2() {
        try {
            page.evaluate("() => { try{ if(window.jQuery){ jQuery('select').each(function(){ try{ jQuery(this).select2('close'); }catch(e){} }); } }catch(e){}"
                    + " document.querySelectorAll('#select2-drop-mask,.select2-drop-mask').forEach(m=>{ m.style.display='none'; });"
                    + " document.querySelectorAll('.select2-drop-active,.select2-drop').forEach(d=>{ d.classList.remove('select2-drop-active'); d.style.display='none'; });"
                    + " try{ document.activeElement && document.activeElement.blur(); }catch(e){} }");
        } catch (Exception ignore) { }
        waitForAngular(300);
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            // The WHOLE menu-entry block is guarded: clicking a menu link routes the SPA, which destroys the JS
            // execution context mid-evaluate ("Execution context was destroyed"). That is a SUCCESSFUL navigation,
            // not a failure — swallow it and let onScreen()/the direct-route fallback decide where we landed.
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Investigation" — a prefix match can expand a different (similarly named) menu.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*investigations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1500);
                // EXACT text first. The sibling "Lab Sample" screen taught that the href need not contain the
                // screen name at all (it routes to #/LabTestSampleType), so the text match is what to trust.
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*modality\\s*$/i.test(norm(x.textContent)))"
                        + "   || [...document.querySelectorAll('a[href]')].find(x=>/modality/i.test(x.getAttribute('href')||''));"
                        + " if(!a) return ''; a.id='__mdMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("Modality.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__mdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // The menu anchor is unstable under the SPA re-render, so the real click keeps failing.
                    // Dispatch the click in-page instead: that still fires any ng-click the anchor carries, which
                    // setting location.hash alone would skip.
                    System.out.println("Modality.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__mdMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__mdMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("Modality.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("Modality.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Modality screen. The URL is checked against the route MINED FROM THE MENU where possible, because a
     * screen's hash need not contain its name ("Lab Sample" routes to {@code #/LabTestSampleType}). The field gate
     * demands Code AND Remark together with one of the Modality-only boxes, so the leftover "Transfer" screen —
     * which renders a Code and a Submit but nothing else — cannot pass.
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        String want = (menuHref.isEmpty() ? ROUTE : menuHref).replaceAll("^#/?", "").toLowerCase();
        if (!want.isEmpty() && !u.contains(want) && !u.contains("modality")) return false;
        if (want.isEmpty() && !u.contains("modality")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS
                + " if(codeEl && remEl && (aeEl || slotEl || patEl)) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }"));
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Read the LIST grid's existing Code values and decide which format to generate. Call this BEFORE Add.
     *
     * <p>Learned on the sibling Interpretation Template: its save proc casts existing codes to {@code int}, so a
     * single {@code IT#####} row inserted by a test broke every later save on that screen for everyone. Matching
     * the format the screen already uses avoids planting that kind of row.</p>
     */
    public String sampleCodeStyle() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " let cells=[...document.querySelectorAll('.ui-grid-row .ui-grid-cell-contents')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " if(!cells.length) cells=[...document.querySelectorAll('table tbody tr td')].map(c=>norm(c.textContent)).filter(t=>t);"
                + " const codes=cells.filter(t=>t.length<=12 && /^[A-Za-z0-9._-]+$/.test(t)).slice(0,12);"
                + " return JSON.stringify(codes); }");
        String raw = r == null ? "[]" : r.toString();
        java.util.List<String> codes = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(raw);
        while (m.find()) codes.add(m.group(1));
        long digits = codes.stream().filter(c -> c.matches("\\d+")).count();
        // Only call it alphanumeric when EVERY sample is; with nothing to learn from, keep the safer numeric form.
        if (codes.isEmpty()) { numericCodes = true; return "no existing rows sampled - defaulting to a NUMERIC code"; }
        numericCodes = digits == codes.size();
        return "sampled " + codes.size() + " grid value(s) " + codes.subList(0, Math.min(6, codes.size()))
                + " -> generating a " + (numericCodes ? "NUMERIC" : "prefixed alphanumeric") + " code";
    }

    /**
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        // Already showing the form -> inline-add, nothing to click.
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl && (aeEl||slotEl||patEl)); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__mdAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__mdAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Modality.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mdAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b>, <b>Remark*</b>, <b>Ae Title</b>, <b>Time Slot</b> and <b>No. of Patient</b>.
     * {@code attempt} shifts the values so a retry after an "already exists" toast submits different details.
     */
    public String fillDetails(int attempt) {
        long n = Math.abs((System.nanoTime() + attempt * 7919L) % 100000);
        lastCode = numericCodes ? String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000))
                                : "MD" + String.format("%05d", n);
        int i = (int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length);
        lastRemark = REMARKS[i] + (attempt == 0 ? "" : " - " + QUALIFIERS[(attempt - 1) % QUALIFIERS.length]);
        // AE titles are unique per device — suffix it so a retry is a genuinely different entity, not a duplicate.
        lastAeTitle = AE_TITLES[i] + (attempt == 0 ? "" : "_" + (attempt + 1));
        lastTimeSlot = String.valueOf(10 + (attempt % 4) * 5);      // 10/15/20/25 minutes per slot
        lastNoOfPatient = String.valueOf(2 + (attempt % 4));        // 2..5 patients per slot
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark); const ae=set(aeEl, a.ae);"
                + " const ts=set(slotEl, a.slot); const np=set(patEl, a.pat);"
                + " return 'Code='+cd+' | Remark='+rm+' | Ae Title='+ae+' | Time Slot='+ts+' | No. of Patient='+np"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark, "ae", lastAeTitle,
                        "slot", lastTimeSlot, "pat", lastNoOfPatient));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res;
    }

    /**
     * Select <b>Department</b> then <b>Equipment</b> with a REAL {@code selectOption} so any {@code ng-change}
     * fires. Equipment is POLLED after Department because it may be a cascade whose list only loads once a
     * Department is chosen — picking it too early silently selects nothing.
     */
    public String selectDepartmentAndEquipment() {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + FIND_SELECTS
                + " if(deptEl) deptEl.id='__mdDept'; if(equipEl) equipEl.id='__mdEquip';"
                + " return ' [selects: '+sels.length+', dept-model='+(deptEl?(deptEl.getAttribute('ng-model')||'?'):'none')"
                + "   +', equip-model='+(equipEl?(equipEl.getAttribute('ng-model')||'?'):'none')+']'; }");
        String info = found == null ? "" : found.toString();

        lastDepartment = pickFirstRealOption("#__mdDept", 10000);
        // The Equipment list can be filled by the Department's ng-change — give it time to arrive.
        waitForAngular(1200);
        lastEquipment = pickFirstRealOption("#__mdEquip", 12000);

        page.evaluate("() => { ['__mdDept','__mdEquip'].forEach(id=>{ const e=document.getElementById(id); if(e) e.removeAttribute('id'); }); }");
        closeSelect2();
        return "Department=" + (lastDepartment.isEmpty() ? "(not selected)" : lastDepartment)
                + " | Equipment=" + (lastEquipment.isEmpty() ? "(not selected)" : lastEquipment) + info;
    }

    /**
     * Choose the first real (non "-Select-") option of a select, polling until one exists — these lists load
     * async. Uses a REAL {@code selectOption} and falls back to driving the element from JS (plus the select2
     * widget) when the select is hidden behind select2 and Playwright refuses to interact with it.
     */
    private String pickFirstRealOption(String selector, int timeoutMs) {
        if (page.locator(selector).count() == 0) return "";
        try {
            page.waitForFunction("(sel) => { const e=document.querySelector(sel); if(!e) return false;"
                    + " return [...e.options].some(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    selector, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { System.out.println("pickFirstRealOption: no real option in " + selector + " within " + timeoutMs + "ms"); }

        Object idx = page.evaluate("(sel) => { const e=document.querySelector(sel); if(!e) return -1;"
                + " return [...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", selector);
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) return "";

        try {
            page.locator(selector).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            // select2 hides the real <select> off-screen, so Playwright will not drive it — do it from JS and
            // tell the widget to re-render, otherwise the model changes but the screen still reads "-Select-".
            System.out.println("pickFirstRealOption: real selectOption failed on " + selector + " - " + e.getMessage());
            page.evaluate("(a) => { const e=document.querySelector(a.sel); if(!e) return; const A=window.angular, $=window.jQuery;"
                    + " e.selectedIndex=a.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).select2('close'); }catch(x){} } }",
                    java.util.Map.of("sel", selector, "i", i));
        }
        waitForAngular(600);
        Object txt = page.evaluate("(sel) => { const e=document.querySelector(sel); if(!e||e.selectedIndex<0) return '';"
                + " return (e.options[e.selectedIndex].textContent||'').replace(/\\s+/g,' ').trim(); }", selector);
        return txt == null ? "" : txt.toString();
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Re-asserts the text fields first — these
     * forms re-render and can blank a field between fill and click. Falls back to invoking the form's ng-submit
     * handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        closeSelect2();
        page.evaluate("() => { window.__mdToasts=[]; if(window.__mdObs) window.__mdObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mdToasts.includes(t)) window.__mdToasts.push(t); }); };"
                + " window.__mdObs=new MutationObserver(grab); window.__mdObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        // Capture what the save call actually returns — a bare "Error!" hides whether it 500'd server-side.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("modality") || resp.url().toLowerCase().contains("/iud") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set(codeEl, a.code); set(remEl, a.remark); set(aeEl, a.ae); set(slotEl, a.slot); set(patEl, a.pat);"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__mdSubmit'; return 'click:'+(b.getAttribute('ng-click')||norm(b.textContent||b.value)); }"
                // No button — invoke the form's ng-submit handler from whichever scope owns it.
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark, "ae", lastAeTitle,
                        "slot", lastTimeSlot, "pat", lastNoOfPatient));
        System.out.println("Modality submit => " + how);
        if (String.valueOf(how).startsWith("click")) {
            try { page.locator("#__mdSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Modality submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__mdSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__mdToasts||[]).some(a=>/modality|master|saved|success|succes|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("Modality save HTTP => " + lastSaveHttp);
        // "succes" (one s) is deliberate — the sibling Lab Organism screen returns "Record added succesfully".
        Object t = page.evaluate("() => { const a=window.__mdToasts||[]; return a.find(x=>/saved|added|succes/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels
     * with their ng-click handlers. Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                // Selects are listed even when hidden — select2 parks the real one off-screen.
                + " const fields=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null).slice(0,30).map(e=>"
                + "   'input['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type?(':'+e.type):'')+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const selects=[...document.querySelectorAll('select')].slice(0,15).map(e=>"
                + "   'select['+(e.getAttribute('ng-model')||e.id||'?')+'] opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  SELECTS: '+selects.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
