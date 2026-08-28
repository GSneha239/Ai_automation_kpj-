package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Pain Screening Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>PainScreeingMaster</b>
 * ({@code #/PainScreeingList}) → <b>Add</b> ({@code addPainScreeingMaster()}) → enter <b>Code</b>,
 * <b>Category</b> and <b>Description</b>, then the detail line <b>Category Description</b> + <b>Scale</b> →
 * inner <b>Add</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>The menu label and route carry the app's own misspelling — "PainScreeingMaster" / {@code #/PainScreeingList}
 * ("Screeing", not "Screening")</b>, so the link is matched on that spelling as well as the correct one.</p>
 *
 * <p>The entry form's ng-models are not known up front (the Nursing masters share no prefix), so every field is
 * located <b>by its label</b> and the discovered ng-model is echoed back in the step text. The two Add buttons are
 * distinguished by handler: the list's is {@code addPainScreeingMaster()}, while the inner one that commits the
 * detail row is whatever other {@code ng-click} Add is present once the form is open.</p>
 */
public class PainScreeningMaster extends BasePage {

    public PainScreeningMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PainScreeingList";
    public String lastCode = "", lastCategory = "", lastDescription = "", lastCatDescription = "", lastScale = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] CATEGORIES = {"Mild Pain", "Moderate Pain", "Severe Pain", "No Pain", "Chronic Pain"};
    private static final String[] DESCRIPTIONS = {
            "Numeric Rating Scale screening", "Wong Baker Faces screening", "Behavioural pain screening",
            "Post operative pain review", "Admission pain assessment"};
    private static final String[] CAT_DESCRIPTIONS = {
            "Patient reports no discomfort", "Patient reports aching discomfort", "Patient reports sharp pain",
            "Patient reports throbbing pain", "Patient reports burning pain"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const flds=()=>[...document.querySelectorAll('input[type=text],input[type=number],input:not([type]),textarea')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0);"
          + " const byLabel=re=>flds().find(e=>re.test(labelOf(e)));";

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
            // The app spells it "PainScreeingMaster" — accept that and the correct spelling, or the exact href.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__psMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*pain\\s*scree? n?ing\\s*master\\s*$/i.test(norm(x.textContent)) || /^\\s*painscree?n?ingmaster\\s*$/i.test(norm(x.textContent).replace(/\\s+/g,'')) || (x.getAttribute('href')||'')==='#/PainScreeingList'); if(!a) return ''; a.id='__psMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("PainScreeningMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__psMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__psMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__psMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/addPainScreeingMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("PainScreeningMaster.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL Pain Screening list — verified by its own Add button ({@code addPainScreeingMaster}), not the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("painscree")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/addPainScreeingMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /**
     * Real-click the list's <b>Add</b> ({@code addPainScreeingMaster}) and wait for the entry form (route or modal).
     * Polls for the button first — it renders after the grid — and retries the click, since a single early click on
     * this screen has been seen doing nothing at all.
     */
    public boolean clickAdd() {
        for (int round = 0; round < 3 && !onAddForm(); round++) {
            boolean tagged = false;
            for (int i = 0; i < 25 && !tagged; i++) {
                tagged = Boolean.TRUE.equals(page.evaluate("() => { document.querySelectorAll('#__psAdd').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('[ng-click]')].find(x=>/addPainScreeingMaster\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__psAdd'; return true; }"));
                if (!tagged) page.waitForTimeout(500);
            }
            if (!tagged) { System.out.println("PainScreeningMaster.clickAdd: Add button not found (round " + (round + 1) + ")"); return false; }
            try { page.locator("#__psAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("PainScreeningMaster.clickAdd: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__psAdd'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(12000));
            } catch (Exception ignore) { System.out.println("PainScreeningMaster.clickAdd: entry form did not render (round " + (round + 1) + ")"); }
            waitForAngular(1200);
        }
        return onAddForm();
    }

    /** True once an entry form with a Code field is up (route or modal). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }"));
    }

    /** Every visible form field with its label + ng-model — so a failure shows what the screen actually offered. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('grid.')<0).map(e=>'SELECT '+norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const btns=[...document.querySelectorAll('button,a[ng-click]')].filter(e=>e.offsetParent!==null).map(b=>norm(b.textContent)+'{'+(b.getAttribute('ng-click')||'-')+'}').filter(s=>s.length<60);"
                + " return 'fields: '+f.concat(sels).join(' ; ')+' || buttons: '+btns.join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter the header <b>Code</b> ({@code PainScreeing.Code}) + <b>Description</b>
     * ({@code PainScreeing.Description}) and the detail line <b>Category</b> ({@code PainScreeing.Category}),
     * <b>Description</b> ({@code PainScreeing.Descriptiondetails}) and <b>Scale</b> ({@code PainScreeing.Scale}).
     *
     * <p>The fields are addressed by their exact ng-models rather than by label, because <b>two different fields
     * are both labelled "Description*"</b> — the header one and the detail line's
     * {@code Descriptiondetails}. A label match cannot tell them apart, and leaving the detail Description empty
     * makes the inner Add silently commit nothing (0 rows) and Submit answer "Error!".</p>
     *
     * <p>{@code attempt} shifts Code, Category, Description and Category Description so a retry after an
     * "already exists" toast submits genuinely different details.</p>
     */
    public String fillDetails(int attempt) {
        String code = "PS" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastCategory = CATEGORIES[attempt % CATEGORIES.length] + (attempt >= CATEGORIES.length ? " " + (attempt / CATEGORIES.length + 1) : "");
        lastDescription = DESCRIPTIONS[attempt % DESCRIPTIONS.length] + (attempt >= DESCRIPTIONS.length ? " " + (attempt / DESCRIPTIONS.length + 1) : "");
        lastCatDescription = CAT_DESCRIPTIONS[attempt % CAT_DESCRIPTIONS.length] + (attempt >= CAT_DESCRIPTIONS.length ? " " + (attempt / CAT_DESCRIPTIONS.length + 1) : "");
        lastScale = String.valueOf(1 + Math.abs(System.nanoTime() % 10));
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const put=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + "   if(e){ const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }"
                // The same model may be rendered as a <select> — then match the value, else take the first real option.
                + "   const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!s) return '(no field)';"
                + "   let i=[...s.options].findIndex(o=>norm(o.textContent).toLowerCase()===String(v).toLowerCase());"
                + "   if(i<0) i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no option)';"
                + "   s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(s).triggerHandler('change');}catch(x){} if($){try{$(s).trigger('change');}catch(x){}}"
                + "   return norm(s.options[i].textContent)+' [select]'; };"
                + " const cd=put('PainScreeing.Code', a.code);"
                + " const dsc=put('PainScreeing.Description', a.description);"
                + " const cat=put('PainScreeing.Category', a.category);"
                + " const cds=put('PainScreeing.Descriptiondetails', a.catdesc);"
                + " const scl=put('PainScreeing.Scale', a.scale);"
                + " return 'Code='+cd+' | Description='+dsc+' | Category='+cat+' | CategoryDescription='+cds+' | Scale='+scl; }",
                java.util.Map.of("code", code, "category", lastCategory, "description", lastDescription,
                        "catdesc", lastCatDescription, "scale", lastScale));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Click the <b>inner Add</b> that commits the Category Description / Scale detail row — i.e. an Add button that
     * is NOT the list's {@code addPainScreeingMaster()}. Returns the handler it fired plus the resulting row count.
     */
    public String clickInnerAdd() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__psInnerAdd').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,a[ng-click],input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && !/addPainScreeingMaster/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return ''; b.id='__psInnerAdd'; return b.getAttribute('ng-click')||'(text Add)'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) return "(no inner Add button found)";
        try { page.locator("#__psInnerAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickInnerAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__psInnerAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        // Count the committed rows from the scope list the Add feeds (PainDetailsList) — a DOM row count also picks
        // up the header/blank rows, so it cannot tell "committed" from "rendered".
        Object rows = page.evaluate("() => { let n=-1; document.querySelectorAll('*').forEach(el=>{ if(n>=0) return; try{ const s=angular.element(el).scope();"
                + " if(s && Array.isArray(s.PainDetailsList)) n=s.PainDetailsList.length; }catch(e){} });"
                + " if(n>=0) return n;"
                + " return [...document.querySelectorAll('table tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim() && !/no records/i.test(r.innerText||'')).length; }");
        int n = rows instanceof Number ? ((Number) rows).intValue() : -1;
        return (n > 0 ? "Add clicked {" + how + "} -> detail rows=" + n
                      : "Add clicked {" + how + "} but NO detail row was committed (rows=" + n + ")");
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__psToasts=[]; if(window.__psObs) window.__psObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__psToasts.includes(t)) window.__psToasts.push(t); }); };"
                + " window.__psObs=new MutationObserver(grab); window.__psObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__psSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__psSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("PainScreeningMaster submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__psSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__psSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__psToasts||[]).some(a=>/pain|screen|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__psToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            // "saved" ends the flow successfully; "already exists" is NOT retryable with the SAME details —
            // return it immediately rather than blindly resubmitting stale data, so the caller can regenerate
            // fresh details (fillDetails) and try again.
            if (last.toLowerCase().matches(".*(saved|added|success|exist|already).*")) return last;
            page.evaluate("() => { window.__psToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
