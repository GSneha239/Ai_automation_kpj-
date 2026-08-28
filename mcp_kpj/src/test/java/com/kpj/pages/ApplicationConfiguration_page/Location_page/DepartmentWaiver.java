package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Department Waiver</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Department Waiver</b> → <b>Add</b> →
 * <b>Location</b>, <b>Department</b>, <b>Pricing Policy</b>, <b>Service</b> → <b>Waiver Days</b>,
 * <b>Service Rate</b>, <b>Emergency Rate</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The fields are addressed by their on-screen LABEL rather than by ng-model, so the page object stands up on a
 * screen whose models have not been catalogued yet; {@link #describeForm()} prints the real wiring when a label
 * misses.</p>
 */
public class DepartmentWaiver extends BasePage {

    public DepartmentWaiver(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastDepartment = "", lastPricingPolicy = "", lastService = "";
    public String lastWaiverDays = "", lastServiceRate = "", lastEmergencyRate = "";
    public String lastToasts = "[]", lastSaveApi = "";
    /** The screen while the message is up — anything after Submit outlives the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "waiver", with the submenu it sits under. */
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

    /** PROBE — the raw markup of the rate boxes: attributes, directives, readonly/disabled state. */
    public String rateBoxes() {
        Object r = page.evaluate("() => [...document.querySelectorAll('input')]"
                + " .filter(e=>/waiverdays|servicerate|rate/i.test((e.getAttribute('ng-model')||'')+' '+(e.name||'')+' '+(e.id||'')))"
                + " .map(e=>e.outerHTML.slice(0,300)+'  || disabled='+e.disabled+' readonly='+e.readOnly+' value=\"'+(e.value||'')+'\"').join('\\n  ')");
        return r == null ? "" : r.toString();
    }

    /** PROBE — the form's own model object, so a filled box with an unbound model is visible. */
    public String modelDump() {
        Object r = page.evaluate("() => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select,input')].find(x=>/^deptwaiver\\./i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return '(no deptwaiver field)'; try { const sc=A.element(s).scope();"
                + "   return JSON.stringify(sc && sc.deptwaiver ? sc.deptwaiver : '(no deptwaiver on scope)'); } catch(e){ return 'err '+e; } }");
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
            System.out.println("DepartmentWaiver.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*department\\s*waiver\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/waiver/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__dwMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__dwMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("DepartmentWaiver.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__dwMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__dwMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("DepartmentWaiver.nav: menu link not found. waiver links => " + findWaiverLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("DepartmentWaiver.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /waiver/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("DepartmentWaiver." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("DepartmentWaiver." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Leave the form via <b>Back</b> so a retry starts from a clean form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__dwBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__dwBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** Click the list screen's <b>Add</b>. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/add.*waiver|addwaiver/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__dwAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DepartmentWaiver.clickAdd: Add button not found"); return false; }
        robustClick("__dwAdd", "clickAdd");
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

    /**
     * Choose the first real option of the select whose LABEL matches {@code labelRegex}. Dependent lists (Service
     * under Pricing Policy) are filled asynchronously, so the options are waited for. Returns the chosen text.
     */
    public String selectByLabel(String labelRegex) {
        String tagged = "";
        for (int w = 0; w < 16 && tagged.isEmpty(); w++) {
            Object r = page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const want=new RegExp(re,'i');"
                    + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                    + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent).replace(/\\*/g,'').trim(); p=p.parentElement; } return t; };"
                    + " const sels=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null && !/pagination/i.test(s.getAttribute('ng-model')||''));"
                    + " const s=sels.find(x=>want.test(lbl(x)) || want.test(x.getAttribute('ng-model')||''));"
                    + " if(!s) return '';"
                    + " const real=[...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                    + " if(!real.length) return 'empty';"
                    + " s.id='__dwSel'; return 'ok'; }", labelRegex);
            String state = String.valueOf(r);
            if ("ok".equals(state)) tagged = state;
            else if (state.isEmpty()) { System.out.println("selectByLabel: no select for /" + labelRegex + "/"); return ""; }
            else page.waitForTimeout(600);                      // options still loading
        }
        if (tagged.isEmpty()) { System.out.println("selectByLabel: /" + labelRegex + "/ never offered an option"); return ""; }

        Object idx = page.evaluate("() => { const s=document.getElementById('__dwSel'); if(!s) return -1;"
                + " return [...s.options].findIndex(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())); }");
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__dwSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__dwSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectByLabel(" + labelRegex + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__dwSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dwSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /**
     * Type {@code value} into the box bound to {@code ngModel}, falling back to the label.
     *
     * <p>ng-model first is not a nicety here: label-walking put the Service Rate value into the <b>Emergency
     * Rate</b> box (that box's nearest label resolves to "Service Rate"), so ServiceRate stayed unset and Submit
     * answered "Please Select Service Rate!" over a form that looked filled.</p>
     */
    public String fillField(String ngModel, String labelRegex, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(m) => {"
                + " const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return false; e.id='__dwBox'; e.scrollIntoView({block:'center'}); return true; }", ngModel));
        if (tagged) {
            // Service Rate carries ng-model-options="{updateOn: 'blur'}": input/change alone leave the control
            // PRISTINE and the model unset, however the box looks. Commit the pending value and blur for real.
            page.evaluate("(v) => { const A=window.angular; const e=document.getElementById('__dwBox'); if(!e) return;"
                    + " const c=A.element(e).controller('ngModel'); e.value=v;"
                    + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " e.dispatchEvent(new Event('blur',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('blur'); }catch(x){}"
                    + " try{ const sc=A.element(e).scope(); if(sc && !sc.$$phase) sc.$apply(); }catch(x){} }", value);
            waitForAngular(500);
            Object v = page.evaluate("() => { const e=document.getElementById('__dwBox'); const val=e?(e.value||''):'';"
                    + " if(e) e.removeAttribute('id'); return val; }");
            return v == null ? "" : v.toString().trim();
        }
        System.out.println("fillField: no input bound to " + ngModel + " — falling back to the label");
        return fillByLabel(labelRegex, value);
    }

    /** Choose the first real option of the select bound to {@code ngModel}, falling back to the label. */
    public String selectField(String ngModel, String labelRegex) {
        boolean present = Boolean.TRUE.equals(page.evaluate("(m) => [...document.querySelectorAll('select')]"
                + " .some(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null)", ngModel));
        if (!present) return selectByLabel(labelRegex);
        // wait for a dependent list to fill before picking
        for (int w = 0; w < 16; w++) {
            Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                    + " if(!s) return 0;"
                    + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
            int count;
            try { count = (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { count = 0; }
            if (count > 0) break;
            page.waitForTimeout(600);
        }
        Object idx = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__dwSel';"
                + " return [...s.options].findIndex(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())); }", ngModel);
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__dwSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__dwSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectField(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__dwSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dwSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** Type {@code value} into the input whose LABEL matches {@code labelRegex}; returns what the box kept. */
    public String fillByLabel(String labelRegex, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(re) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=new RegExp(re,'i');"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent).replace(/\\*/g,'').trim(); p=p.parentElement; } return t; };"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
                + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'')"
                + "   && !/colFilter|pagination|row\\.entity/i.test(e.getAttribute('ng-model')||''));"
                + " const f=boxes.find(e=>want.test(lbl(e))) || boxes.find(e=>want.test(e.getAttribute('ng-model')||''));"
                + " if(!f) return false; f.id='__dwBox'; f.scrollIntoView({block:'center'}); return true; }", labelRegex));
        if (!tagged) { System.out.println("fillByLabel: no input for /" + labelRegex + "/"); return ""; }
        page.evaluate("(v) => { const A=window.angular; const e=document.getElementById('__dwBox'); if(!e) return;"
                + " const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", value);
        waitForAngular(400);
        Object v = page.evaluate("() => { const e=document.getElementById('__dwBox'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
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
                + " if(!s) return -1; s.id='__dwSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return -1; return real[n].i; }", java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__dwSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__dwSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            page.evaluate("(k) => { const e=document.getElementById('__dwSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dwSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** Which Department / Service option this run is on — the retry walks these when the waiver already exists. */
    public int departmentIndex = 0, serviceIndex = 0;

    /**
     * Move to the next Department/Service combination: the next Service, or the next Department when this
     * department's services are exhausted. Call on a FRESH form. Returns the new selection, or "" when spent.
     */
    public String nextCombination() {
        lastLocation = selectField("deptwaiver.LocationaID", "^location");
        int services;
        for (int guard = 0; guard < 40; guard++) {
            String dept = selectNth("deptwaiver.DepartmentID", departmentIndex);
            if (dept.isEmpty()) return "";
            lastDepartment = dept;
            lastPricingPolicy = selectField("deptwaiver.TariffID", "pricing\\s*policy|tariff");
            services = optionCount("deptwaiver.ServiceID");
            serviceIndex++;
            if (serviceIndex < services) {
                String svc = selectNth("deptwaiver.ServiceID", serviceIndex);
                if (!svc.isEmpty()) {
                    lastService = svc;
                    return "Department=" + dept + " | Service=" + svc + " (service " + (serviceIndex + 1) + "/" + services + ")";
                }
            }
            departmentIndex++;
            serviceIndex = -1;                                  // ++ on the next pass starts this department at 0
        }
        return "";
    }

    /** Select <b>Location</b>, <b>Department</b>, <b>Pricing Policy</b> and <b>Service</b>, in cascade order. */
    public String selectAll() {
        // "LocationaID" is the app's own spelling.
        lastLocation = selectField("deptwaiver.LocationaID", "^location");
        lastDepartment = selectField("deptwaiver.DepartmentID", "^department");
        lastPricingPolicy = selectField("deptwaiver.TariffID", "pricing\\s*policy|tariff");
        lastService = selectField("deptwaiver.ServiceID", "^service(?!\\s*rate)");
        return "Location=" + or(lastLocation) + " | Department=" + or(lastDepartment)
                + " | Pricing Policy=" + or(lastPricingPolicy) + " | Service=" + or(lastService);
    }

    /** What this run means to enter — kept so the values can be re-asserted after the form wipes one. */
    private String wantWaiverDays = "", wantServiceRate = "", wantEmergencyRate = "";

    /**
     * Enter <b>Waiver Days</b>, <b>Service Rate</b> and <b>Emergency Rate</b>.
     *
     * <p>The Service selection fires an async handler that lands AFTER the fill and blanks
     * {@code deptwaiver.ServiceRate} (only that field — Waiver Days and Emergency Rate survive), which produced a
     * "Please Select Service Rate!" over a form that looked complete. So the rates settle first and
     * {@link #reassertRates()} puts them back immediately before Submit.</p>
     */
    public String fillRates(String waiverDays, String serviceRate, String emergencyRate) {
        wantWaiverDays = waiverDays; wantServiceRate = serviceRate; wantEmergencyRate = emergencyRate;
        waitForAngular(1500);                                   // let the Service change handler finish first
        lastWaiverDays = fillField("deptwaiver.WaiverDays", "waiver\\s*day", waiverDays);
        lastEmergencyRate = fillField("deptwaiver.EmergencyServiceRate", "emergency\\s*rate", emergencyRate);
        lastServiceRate = fillField("deptwaiver.ServiceRate", "^service\\s*rate", serviceRate);
        return "Waiver Days=" + or(lastWaiverDays) + " | Service Rate=" + or(lastServiceRate)
                + " | Emergency Rate=" + or(lastEmergencyRate) + " | model: " + modelDump();
    }

    /** Re-apply the three values and report the model — called right before Submit. */
    public String reassertRates() {
        if (wantWaiverDays.isEmpty()) return modelDump();
        lastWaiverDays = fillField("deptwaiver.WaiverDays", "waiver\\s*day", wantWaiverDays);
        lastEmergencyRate = fillField("deptwaiver.EmergencyServiceRate", "emergency\\s*rate", wantEmergencyRate);
        lastServiceRate = fillField("deptwaiver.ServiceRate", "^service\\s*rate", wantServiceRate);
        return modelDump();
    }

    private static String or(String s) { return s == null || s.isEmpty() ? "(not set)" : s; }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("waiver")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__dwToasts=[]; if(window.__dwObs) window.__dwObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dwToasts.includes(t)) window.__dwToasts.push(t); }); };"
                + " window.__dwObs=new MutationObserver(grab); window.__dwObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__dwSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        System.out.println("submit: model just before clicking => " + reassertRates());
        robustClick("__dwSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dwToasts||[]).includes(t)) (window.__dwToasts=window.__dwToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__dwToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__dwToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
