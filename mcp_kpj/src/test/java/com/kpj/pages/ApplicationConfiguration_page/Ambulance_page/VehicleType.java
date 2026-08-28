package com.kpj.pages.ApplicationConfiguration_page.Ambulance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Ambulance &gt; <b>Vehicle Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Ambulance</b> (submenu) → <b>Vehicle Type</b> (route
 * {@code #/Vehicletype}) → enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → toast.</p>
 *
 * <p><b>INLINE-ADD screen</b> — the fields live on the list page and there is no Add button, so the sibling
 * {@link Vehicle} screen's Add step does not apply here.</p>
 *
 * <p>What the screen actually offers, as probed (2026-08-18):</p>
 * <ul>
 *   <li><b>devhis</b> — {@code commonmaster.Code} (Code*) and a {@code StoreId} (Store*) select that has NO real
 *       options. No Remark control of any kind is rendered, and the Submit button carries no {@code ng-click}.</li>
 *   <li><b>KS and DSH</b> — the route renders NOTHING at all: no fields, no buttons, no grid.</li>
 * </ul>
 *
 * <p>So the Remark is looked up defensively across the ng-models this family uses
 * ({@code commonmaster.Description} / {@code .Remark} / {@code .remark}) and its absence is REPORTED rather than
 * silently skipped — a screen that cannot take the value the flow asks for should fail loudly, not pass quietly.</p>
 */
public class VehicleType extends BasePage {

    public VehicleType(Page page) { super(page); }

    public static String LIST_ROUTE = "#/Vehicletype";

    public String lastCode = "";
    public String lastRemark = "";
    /** Which Remark ng-model was found (or "" when the screen renders none). */
    public String remarkModel = "";

    /** Realistic vehicle-type names — a Remark reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] REMARKS = {
            "Emergency Ambulance", "Patient Transport Van", "Mobile Intensive Care Unit",
            "Neonatal Transport Ambulance", "Rapid Response Car", "Bariatric Ambulance",
            "Air Ambulance Transfer", "Non-Emergency Transfer Van"
    };

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Ambulance → Vehicle Type, falling back to the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ambulance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        // The child link's text is "Vehicle Type" — anchored so it cannot match the sibling "Vehicle".
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*vehicle\\s*type\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!a) return ''; a.id='__vtMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__vtMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("VehicleType.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__vtMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        } else {
            System.out.println("VehicleType.nav: no 'Vehicle Type' menu link found");
        }
        if (!onScreen()) {
            System.out.println("VehicleType.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(3000);
        }
        return onScreen();
    }

    /**
     * On the screen means the FORM is really there — judged by its own Code field, not by the URL.
     * The hash reads {@code #/Vehicletype} on KS and DSH while the page renders nothing at all, so a URL check
     * would report success on a blank screen.
     */
    public boolean onScreen() {
        for (int i = 0; i < 12; i++) {
            boolean ready = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')]"
                    + ".some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Code')"));
            if (ready) return true;
            page.waitForTimeout(500);
        }
        return false;
    }

    /** Does the screen render a Remark control at all, under any of the ng-models this family uses? */
    public boolean hasRemarkField() {
        return Boolean.TRUE.equals(page.evaluate("() => { const vis=e=>e && e.offsetParent!==null;"
                + " const ngs=['commonmaster.Description','commonmaster.Remark','commonmaster.remark','commonmaster.description'];"
                + " if(ngs.some(ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].some(vis))) return true;"
                // Last resort: a visible text box whose own label says Remark / Description.
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('input,textarea')].filter(vis).some(e=>{"
                + "   let n=e.parentElement; for(let i=0;i<5&&n;i++){ const l=n.querySelector('label');"
                + "     if(l) return /remark|description/i.test(norm(l.textContent)); n=n.parentElement; } return false; }); }"));
    }

    /** What the route rendered, for a step that has to explain an empty screen. */
    public String describeScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const f=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>(e.getAttribute('ng-model')||e.id||e.tagName));"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t);"
                + " return 'url=' + location.hash + ' | fields=[' + f.join(', ') + '] | buttons=[' + b.join(', ') + ']'; }");
        return r == null ? "" : r.toString();
    }

    // ---- actions ---------------------------------------------------------

    /**
     * Enter <b>Code</b> (unique) and <b>Remark</b>, plus the mandatory <b>Store</b> when it offers a value.
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different
     * details.
     *
     * @return what was entered, naming anything the screen does not provide
     */
    public String fillDetails(int attempt) {
        String code = "VT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        String remark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        lastRemark = remark;
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
                + " const setInp=(e,v)=>{ if(!e) return false; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); return true; };"
                + " const codeEl=byNg('commonmaster.Code');"
                + " const codeOk=setInp(codeEl, a.code);"
                // The Remark's ng-model varies across this family — take whichever one the screen renders.
                + " const remarkNgs=['commonmaster.Description','commonmaster.Remark','commonmaster.remark','commonmaster.description'];"
                + " let remarkNg='', remarkOk=false;"
                + " for(const ng of remarkNgs){ const e=byNg(ng); if(e){ remarkNg=ng; remarkOk=setInp(e, a.remark); break; } }"
                // Store is starred on this screen; only selectable when its list actually has an option.
                + " let store='(no Store field)';"
                + " const st=byNg('StoreId');"
                + " if(st){ const i=[...st.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + "   if(i<0){ store='(Store list is EMPTY - ' + st.options.length + ' option(s))'; }"
                + "   else { st.selectedIndex=i; st.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     try{A.element(st).triggerHandler('change');}catch(x){} if($){try{$(st).trigger('change');}catch(x){}}"
                + "     store=norm(st.options[i].textContent); } }"
                + " return JSON.stringify({codeOk:codeOk, remarkOk:remarkOk, remarkNg:remarkNg, store:store}); }",
                java.util.Map.of("code", code, "remark", remark));
        waitForAngular(500);
        String s = String.valueOf(r);
        remarkModel = s.contains("\"remarkNg\":\"") ? s.split("\"remarkNg\":\"")[1].split("\"")[0] : "";
        boolean codeOk = s.contains("\"codeOk\":true");
        boolean remarkOk = s.contains("\"remarkOk\":true");
        String store = s.contains("\"store\":\"") ? s.split("\"store\":\"")[1].split("\"")[0] : "";
        return "Code=" + (codeOk ? code : "(NOT set — no Code field)")
                + " | Remark=" + (remarkOk ? remark + " [" + remarkModel + "]" : "(NOT set — the screen renders NO Remark field)")
                + " | Store=" + store;
    }

    /** Click <b>Submit</b> and return whatever message the screen raised ("" when it stayed silent). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__vtToasts=[]; if(window.__vtObs) window.__vtObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__vtToasts.includes(t)) window.__vtToasts.push(t); }); };"
                + " window.__vtObs=new MutationObserver(grab); window.__vtObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')]"
                + "   .find(x=>x.offsetParent!==null && /^\\s*submit\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__vtSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("VehicleType.submit: Submit button not found"); return ""; }
        // REAL click — these ng-click handlers do not fire reliably from a synthetic one.
        try { page.locator("#__vtSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("VehicleType.submit: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vtSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__vtToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|required|exist|error|not found/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__vtToasts=window.__vtToasts||[]).includes(t)) window.__vtToasts.push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__vtToasts||[];"
                + " return a.find(x=>/saved successfully|added successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
