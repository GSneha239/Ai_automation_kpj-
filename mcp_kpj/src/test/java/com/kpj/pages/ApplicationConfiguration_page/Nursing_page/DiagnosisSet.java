package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Diagnosis Set</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Diagnosis Set</b> (list) →
 * <b>Add</b> → type 2 digits in <b>Diagnosis Code</b> and pick a suggestion (which auto-fills
 * <b>Diagnosis Description</b>) → enter <b>Diagnosis Set Code</b> + <b>Diagnosis Set Description</b> →
 * inner <b>Add</b> (adds the detail row) → <b>Submit</b> → success toast.</p>
 *
 * <p>Menu note: the submenu parent is <b>Nursing</b> — matched on EXACT text, because "Nursing Station" is a
 * separate top-level module that a prefix match hits first.</p>
 */
public class DiagnosisSet extends BasePage {

    public DiagnosisSet(Page page) { super(page); }

    public static final String ROUTE = "#/DiagnosisSet";
    public String lastSetCode = "", lastSetDescription = "", lastDiagnosisCode = "", lastDiagnosisDescription = "";
    /** href discovered on the menu link — used as the direct-route fallback if the click does not land. */
    private String menuHref = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Set descriptions that read like genuine configuration rather than machine noise. */
    private static final String[] SET_DESCRIPTIONS = {
            "Respiratory Assessment Set", "Cardiac Review Set", "Diabetic Care Set",
            "Post Operative Review Set", "General Screening Set", "Maternity Care Set"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*diagnosis\\s*set\\s*$/i.test(norm(x.textContent)) || /diagnosisset/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__dsMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("DiagnosisSet.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            if (href.toString().startsWith("#")) menuHref = href.toString();
            try { page.locator("#__dsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DiagnosisSet.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dsMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("DiagnosisSet.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Diagnosis Set list/route. */
    public boolean onScreen() {
        return page.url().toLowerCase().contains("diagnosisset");
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** Real-click the <b>Add</b> button on the list screen (polls). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dsAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DiagnosisSet.clickAdd: Add button not found"); return false; }
        try { page.locator("#__dsAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("DiagnosisSet.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dsAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return true;
    }

    // ---- form ------------------------------------------------------------
    // Field ng-models established by the probe (labels on this form are mis-associated, placeholders are generic):
    //   DiagnosisSet.DiagnosisCodeId        Diagnosis Code        (type 2 digits -> suggestion list)
    //   DiagnosisSet.DiagnosisDescriptionId Diagnosis Description (AUTO-FILLED by picking a suggestion)
    //   DiagnosisSet.CodeId                 Diagnosis Set Code
    //   DiagnosisSet.description            Diagnosis Set Description
    // Buttons on the add form: Add (adds the detail row), Submit, Back.

    private static final String DIAG_CODE = "input[ng-model='DiagnosisSet.DiagnosisCodeId']";
    private static final String DIAG_DESC = "input[ng-model='DiagnosisSet.DiagnosisDescriptionId']";

    /** Current value of a field, by ng-model. */
    private String valueOf(String ngModel) {
        Object r = page.evaluate("(ng) => { const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); return e? (e.value||'') : ''; }", ngModel);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Type 2 digits into <b>Diagnosis Code</b> and pick a value from the suggestion drop-down, which AUTO-FILLS
     * <b>Diagnosis Description</b>. Typing is REAL keyboard input — the suggestion list is driven by the field's
     * own key handler and a synthetic value assignment never opens it. Tries several 2-digit prefixes until one
     * returns suggestions, and confirms success by the Description actually being populated.
     */
    public String pickDiagnosisCode() {
        String[] prefixes = {"10", "11", "12", "20", "21", "30", "40", "50"};
        for (String prefix : prefixes) {
            try {
                page.locator(DIAG_CODE).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
                page.locator(DIAG_CODE).fill("");
                page.locator(DIAG_CODE).type(prefix, new com.microsoft.playwright.Locator.TypeOptions().setDelay(180));
            } catch (Exception e) { System.out.println("pickDiagnosisCode: typing failed - " + e.getMessage()); continue; }
            waitForAngular(400);

            // Poll for a visible suggestion list (the app could use any of the usual typeahead widgets).
            String picked = "";
            for (int i = 0; i < 16 && picked.isEmpty(); i++) {
                Object tag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const sels=['.dropdown-menu li','li.uib-typeahead-match','.ui-select-choices-row','.select2-results li',"
                        + "  '.tt-suggestion','.autocomplete-suggestion','ul.typeahead li','.angucomplete-row','.ui-menu-item'];"
                        + " for(const s of sels){ const el=[...document.querySelectorAll(s)].find(x=>x.offsetParent!==null && norm(x.textContent)); "
                        + "   if(el){ el.id='__dsSug'; return norm(el.textContent).slice(0,80); } }"
                        + " return ''; }");
                if (tag != null && !tag.toString().isEmpty()) {
                    try { page.locator("#__dsSug").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); picked = tag.toString(); }
                    catch (Exception e) { System.out.println("pickDiagnosisCode: suggestion click failed - " + e.getMessage()); }
                    page.evaluate("() => { const e=document.getElementById('__dsSug'); if(e) e.removeAttribute('id'); }");
                }
                if (picked.isEmpty()) page.waitForTimeout(400);
            }
            // Keyboard fallback — many typeaheads commit on ArrowDown + Enter without exposing a clickable row.
            if (picked.isEmpty()) {
                try {
                    page.locator(DIAG_CODE).press("ArrowDown");
                    page.waitForTimeout(400);
                    page.locator(DIAG_CODE).press("Enter");
                    picked = "(via keyboard)";
                } catch (Exception ignore) { }
            }
            waitForAngular(1200);

            lastDiagnosisCode = valueOf("DiagnosisSet.DiagnosisCodeId");
            lastDiagnosisDescription = valueOf("DiagnosisSet.DiagnosisDescriptionId");
            if (!lastDiagnosisDescription.isEmpty()) {
                return "typed=" + prefix + " | picked=" + picked
                        + " | DiagnosisCode=" + lastDiagnosisCode + " | DiagnosisDescription=" + lastDiagnosisDescription;
            }
            System.out.println("pickDiagnosisCode: '" + prefix + "' gave no usable suggestion — trying the next prefix");
        }
        return "DiagnosisCode=" + lastDiagnosisCode + " | DiagnosisDescription=(not auto-filled)";
    }

    /**
     * Enter <b>Diagnosis Set Code</b> ({@code DiagnosisSet.CodeId}) and <b>Diagnosis Set Description</b>
     * ({@code DiagnosisSet.description}). {@code attempt} shifts BOTH so a retry after an "already exists" toast
     * submits genuinely different details.
     */
    public String fillSetDetails(int attempt) {
        String code = "DS" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastSetCode = code;
        lastSetDescription = SET_DESCRIPTIONS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % SET_DESCRIPTIONS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('DiagnosisSet.CodeId', a.code); const de=set('DiagnosisSet.description', a.desc);"
                + " return 'DiagnosisSetCode='+cd+' | DiagnosisSetDescription='+de; }",
                java.util.Map.of("code", code, "desc", lastSetDescription));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** How many real detail rows the add form's grid currently holds. */
    public int detailRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')];"
                + " if(rows.length) return rows.filter(r=>norm(r.textContent)).length;"
                + " return [...document.querySelectorAll('table tbody tr')].filter(r=>norm(r.textContent) && !/no\\s*record/i.test(norm(r.textContent))).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Real-click the add form's inner <b>Add</b> (commits the diagnosis as a detail row). */
    public boolean clickInnerAdd() {
        int before = detailRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dsInnerAdd'; return true; }"));
        if (!tagged) { System.out.println("clickInnerAdd: Add button not found on the add form"); return false; }
        try { page.locator("#__dsInnerAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickInnerAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dsInnerAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return detailRowCount() > before;
    }

    /**
     * Click <b>Submit</b> and return the toast. Re-asserts the Set Code / Description first — the inner Add
     * re-renders the form and can blank them.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__dsToasts=[]; if(window.__dsObs) window.__dsObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dsToasts.includes(t)) window.__dsToasts.push(t); }); };"
                + " window.__dsObs=new MutationObserver(grab); window.__dsObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('DiagnosisSet.CodeId', a.code); set('DiagnosisSet.description', a.desc); }",
                java.util.Map.of("code", lastSetCode, "desc", lastSetDescription));
        waitForAngular(400);

        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__dsSubmit'; return true; }"));
        if (!tagged) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__dsSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dsSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__dsToasts||[]).some(a=>/diagnosis|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        Object t = page.evaluate("() => { const a=window.__dsToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — everything the add form actually exposes: visible field ng-models (with tag, type, placeholder and
     * any nearby label text) and the visible button labels. Labels render empty on most of these screens, so this
     * is how the real field names get established instead of guessed.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type&&e.tagName.toLowerCase()==='input'?':'+e.type:'')"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')"
                + "   +(lbl(e)?' lbl=\"'+lbl(e)+'\"':'')"
                + "   +(e.tagName.toLowerCase()==='select'?' opts='+e.options.length:''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
