package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Location Waiver</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Location Waiver</b> ({@code #/LocationWaiverList})
 * → <b>Add</b> ({@code addLocationwaiver()}, opens {@code #/add-Locationwaiver}) → <b>Location</b>,
 * <b>Pricing Policy</b>, <b>Service</b>, <b>Waiver Days</b>, <b>Service Rate</b> → <b>Submit</b>
 * ({@code fnIUDWaiver()}) → success toast, save API {@code POST /api/Waiver/IUD}.</p>
 *
 * <p>Sibling of {@link DepartmentWaiver} minus the Department field — the ng-model prefix here is
 * {@code Locationwaiver.*} (capital L; same "Locationa" typo on the Location field as
 * {@code deptwaiver.LocationaID}). <b>Pricing Policy</b> ({@code Locationwaiver.TariffID}) is DEPENDENT on
 * Location; <b>Service</b> ({@code Locationwaiver.ServiceID}) is NOT dependent on either — it lists its own fixed
 * options regardless of Location/Pricing Policy. The success toast reads <i>"Payable Waiver Saved
 * Successfully."</i> — the app reuses the Payable Waiver message text on this screen, so any check must key on
 * "saved"/"success" rather than the screen's own name.</p>
 */
public class LocationWaiver extends BasePage {

    public LocationWaiver(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastPricingPolicy = "", lastService = "";
    public String lastWaiverDays = "", lastServiceRate = "";
    public String lastToasts = "[]", lastSaveApi = "";
    /** The screen while the message is up — anything after Submit outlives the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "location waiver", with the submenu it sits under. */
    public String findWaiverLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/waiver/i.test(norm(a.textContent)) || /waiver/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** Every visible field / select / button with its ng-model or ng-click. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,34); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|paginationCurrentPage|row\\.entity/i.test(e.getAttribute('ng-model')||'')).slice(0,30)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,20)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** PROBE — the form's own model object, so a filled box with an unbound model is visible. */
    public String modelDump() {
        Object r = page.evaluate("() => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select,input')].find(x=>/^Locationwaiver\\./i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return '(no Locationwaiver field)'; try { const sc=A.element(s).scope();"
                + "   return JSON.stringify(sc && sc.Locationwaiver ? sc.Locationwaiver : '(no Locationwaiver on scope)'); } catch(e){ return 'err '+e; } }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("LocationWaiver.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*location\\s*waiver\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/locationwaiverlist/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__lwMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__lwMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("LocationWaiver.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__lwMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__lwMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("LocationWaiver.nav: menu link not found. waiver links => " + findWaiverLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("LocationWaiver.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /location\\s*waiver/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("LocationWaiver." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("LocationWaiver." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Leave the form via <b>Back</b> so a retry starts from a clean form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__lwBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__lwBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** Click the list screen's <b>Add</b> ({@code addLocationwaiver()}). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/addlocationwaiver/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__lwAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("LocationWaiver.clickAdd: Add button not found"); return false; }
        robustClick("__lwAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form is up — a Submit button plus at least two selects. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sub=[...document.querySelectorAll('button,input[type=submit]')].some(b=>b.offsetParent!==null && /^\\s*submit\\s*$/i.test(norm(b.textContent||b.value)));"
                + " const sels=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null && !/pagination/i.test(s.getAttribute('ng-model')||'')).length;"
                + " return sub && sels>=2; }"));
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** Choose the {@code index}-th real option of the select bound to {@code ngModel}; "" when there is none. */
    private String selectNth(String ngModel, int index) {
        for (int w = 0; w < 12 && optionCount(ngModel) == 0; w++) page.waitForTimeout(600);
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__lwSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return -1; return real[n].i; }", java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__lwSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__lwSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__lwSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__lwSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /**
     * Type {@code value} into the box bound to {@code ngModel}. Waiver Days and Service Rate carry a plain
     * ngModel (no {@code updateOn:'blur'} here, unlike {@link DepartmentWaiver}'s Service Rate box), but the
     * commit is still driven through Angular's controller rather than a bare DOM value set.
     */
    public String fillField(String ngModel, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(m) => {"
                + " const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return false; e.id='__lwBox'; e.scrollIntoView({block:'center'}); return true; }", ngModel));
        if (!tagged) { System.out.println("fillField: no input bound to " + ngModel); return ""; }
        page.evaluate("(v) => { const A=window.angular; const e=document.getElementById('__lwBox'); if(!e) return;"
                + " const c=A.element(e).controller('ngModel'); e.value=v;"
                + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " e.dispatchEvent(new Event('blur',{bubbles:true})); try{ A.element(e).triggerHandler('blur'); }catch(x){} }", value);
        waitForAngular(500);
        Object v = page.evaluate("() => { const e=document.getElementById('__lwBox'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
    }

    // ---- add form ------------------------------------------------------------

    /** Select the form's <b>Location</b>. Pricing Policy stays empty until this commits. */
    public String selectLocation() { lastLocation = selectNth("Locationwaiver.LocationaID", 0); return lastLocation; }

    /** Select <b>Pricing Policy</b> — DEPENDENT on Location, refilled by its ng-change. */
    public String selectPricingPolicy(int index) { lastPricingPolicy = selectNth("Locationwaiver.TariffID", index); return lastPricingPolicy; }

    /** Select <b>Service</b> — NOT dependent on Location or Pricing Policy; its own fixed list. */
    public String selectService(int index) { lastService = selectNth("Locationwaiver.ServiceID", index); return lastService; }

    /** Select <b>Location</b>, <b>Pricing Policy</b> and <b>Service</b>, in cascade order. */
    public String selectAll(int pricingPolicyIndex, int serviceIndex) {
        String loc = selectLocation();
        String pp = selectPricingPolicy(pricingPolicyIndex);
        String svc = selectService(serviceIndex);
        return "Location=" + or(loc) + " | Pricing Policy=" + or(pp) + " | Service=" + or(svc);
    }

    /** Enter <b>Waiver Days</b> and <b>Service Rate</b>. */
    public String fillRates(String waiverDays, String serviceRate) {
        lastWaiverDays = fillField("Locationwaiver.WaiverDays", waiverDays);
        lastServiceRate = fillField("Locationwaiver.ServiceRate", serviceRate);
        return "Waiver Days=" + or(lastWaiverDays) + " | Service Rate=" + or(lastServiceRate) + " | model: " + modelDump();
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int pricingPolicyIndex, int serviceIndex, String waiverDays, String serviceRate) {
        String sel = selectAll(pricingPolicyIndex, serviceIndex);
        String rates = fillRates(waiverDays, serviceRate);
        return sel + " | " + rates;
    }

    private static String or(String s) { return s == null || s.isEmpty() ? "(not set)" : s; }

    /**
     * Click <b>Submit</b> ({@code fnIUDWaiver()}) and return the toast; screenshots the message the moment it
     * appears. A successful Submit navigates straight back to the list ({@code #/LocationWaiverList}).
     */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("waiver")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__lwToasts=[]; if(window.__lwObs) window.__lwObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__lwToasts.includes(t)) window.__lwToasts.push(t); }); };"
                + " window.__lwObs=new MutationObserver(grab); window.__lwObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__lwSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__lwSubmit", "submit");

        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("submit: toast screenshot failed - " + e.getMessage()); break; }
            } else page.waitForTimeout(200);
        }
        if (toastPng == null) System.out.println("submit: no message was ever on screen to screenshot");
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__lwToasts||[]).includes(t)) (window.__lwToasts=window.__lwToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__lwToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        System.out.println("submit: toasts => " + lastToasts);
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                java.util.regex.Matcher rs = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (rs.find() ? ", ResultStatus=" + rs.group(1) : "")
                        + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__lwToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
