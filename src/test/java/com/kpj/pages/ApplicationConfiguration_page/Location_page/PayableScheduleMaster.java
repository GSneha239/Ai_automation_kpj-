package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Payable Schedule Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Payable Schedule Master</b> → <b>Add</b> →
 * <b>Location</b>, <b>Department</b>, <b>Payable</b>, <b>Start Time</b>, <b>End Time</b>,
 * <b>Consultation Room</b>, <b>Days</b> → inner <b>Add</b> → <b>Submit</b> → success toast.</p>
 */
public class PayableScheduleMaster extends BasePage {

    public PayableScheduleMaster(Page page) { super(page); }

    /** Route — re-mined from the menu at runtime. */
    public static String ROUTE = "";

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "payable"/"schedule", with the submenu it sits under. */
    public String findPayableLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/payable|schedule/i.test(norm(a.textContent)) || /payable|schedule/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** Every visible field / select / checkbox / button with its ng-model or ng-click — the wiring, in one look. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t=''; if(e.id){ try{ const l=document.querySelector('label[for=\"'+CSS.escape(e.id)+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } } return t.slice(0,34); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|paginationCurrentPage/i.test(e.getAttribute('ng-model')||'')).slice(0,40)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"'+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,20)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,30);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- navigation ------------------------------------------------------

    /** <b>Application Configuration</b> → <b>Location</b> → <b>Payable Schedule Master</b>. */
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
            System.out.println("PayableScheduleMaster.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/payable\\s*schedule/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/payableschedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__psmMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__psmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("PayableScheduleMaster.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__psmMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__psmMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("PayableScheduleMaster.nav: menu link not found. payable/schedule links => " + findPayableLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("PayableScheduleMaster.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /payable\\s*schedule/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("PayableScheduleMaster." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("PayableScheduleMaster." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b>. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/add.*payable|addschedule|addpayable/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__psmAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PayableScheduleMaster.clickAdd: Add button not found"); return false; }
        robustClick("__psmAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its four selects) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                + " .some(s=>/payScheduleInfo\\.Locationid/i.test(s.getAttribute('ng-model')||''))"));
    }

    public String lastLocation = "", lastDepartment = "", lastPayable = "", lastRoom = "";
    public String lastStart = "", lastEnd = "", lastDays = "";
    public String lastAddToasts = "[]", lastToasts = "[]", lastSaveApi = "";
    /** Which department option is in play — the retry moves past it when the schedule already exists. */
    public int lastDepartmentIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    /**
     * Choose the {@code index}-th real option of the select bound to {@code ngModel} (0 = first non-placeholder),
     * with a REAL selectOption and a JS + Angular-change fallback. Returns the chosen text, or "".
     */
    private String selectNth(String ngModel, int index) {
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__psmSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(!real.length) return -1; return real[Math.min(n, real.length-1)].i; }",
                java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__psmSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__psmSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__psmSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__psmSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** The first Payable option this department offers that has no schedule yet, or "". */
    private String firstFreePayable() {
        Object r = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>/payScheduleInfo\\.Payableid/i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return '[]';"
                + " return JSON.stringify([...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()))"
                + "   .map(o=>(o.textContent||'').trim())); }");
        String json = r == null ? "[]" : r.toString();
        if (json.length() < 3) return "";
        StringBuilder skipped = new StringBuilder();
        for (String opt : json.substring(1, json.length() - 1).split("\",\"")) {
            String name = opt.replaceAll("^\"|\"$", "").trim();
            if (name.isEmpty()) continue;
            if (alreadyScheduled(name)) { skipped.append(skipped.length() == 0 ? "" : ", ").append(name); continue; }
            if (skipped.length() > 0) System.out.println("  firstFreePayable: skipped (already scheduled) " + skipped);
            return name;
        }
        System.out.println("  firstFreePayable: all options already scheduled (" + skipped + ")");
        return "";
    }

    /** Choose the option whose visible text equals {@code text}. */
    private String selectByText(String ngModel, String text) {
        Object idx = page.evaluate("([m,t]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__psmSel';"
                + " return [...s.options].findIndex(o=>(o.textContent||'').trim()===t); }",
                java.util.Arrays.asList(ngModel, text));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__psmSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__psmSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            page.evaluate("(k) => { const e=document.getElementById('__psmSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__psmSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return -1; }
    }

    /** Is the select's Angular MODEL actually set? A rendered value with a null model is the classic Payable trap. */
    private boolean modelSet(String ngModel) {
        return Boolean.TRUE.equals(page.evaluate("(m) => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return false; try { const c=A.element(s).controller('ngModel');"
                + "   return !!(c && c.$modelValue !== null && c.$modelValue !== undefined && c.$modelValue !== ''); } catch(e){ return false; } }", ngModel));
    }

    /** Select the <b>Location</b>. */
    public String selectLocation() { lastLocation = selectNth("payScheduleInfo.Locationid", 0); return lastLocation; }

    /** Payables that already have a schedule — Submit against one of these UPDATES live configuration. */
    private final java.util.Set<String> scheduledPayables = new java.util.HashSet<>();

    /**
     * The schedule id the form is holding. On a FRESH Add form this is 0; once the app has loaded an existing
     * schedule (or a save has happened) it stays set, and Submit then UPDATES that schedule instead of adding one.
     * That is why a retry must re-open the form rather than just re-picking a payable.
     */
    public int payScheduleId() {
        Object r = page.evaluate("() => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>/payScheduleInfo\\.Payableid/i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return -1; try { const sc=A.element(s).scope();"
                + "   return (sc && sc.payScheduleInfo && sc.payScheduleInfo.PayScheduleid) ? sc.payScheduleInfo.PayScheduleid : 0; } catch(e){ return -1; } }");
        try { return (int) Double.parseDouble(String.valueOf(r)); } catch (Exception e) { return -1; }
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}) and land back on the list. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__psmBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__psmBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /**
     * Fill the whole form on a FRESH Add: Location, a Department/Payable pair starting at {@code deptFrom}, the
     * times, the room, the days, then the inner Add. Used for retries, where every field has to be re-entered.
     */
    public String fillAll(int deptFrom, String start, String end, String... days) {
        String loc = selectLocation();
        String dp = selectDepartmentAndPayable(deptFrom);
        String times = fillTimes(start, end);
        String room = selectConsultationRoom();
        String d = tickDays(days);
        String added = clickInnerAdd();
        return "Location=" + loc + " | " + dp + " | " + times + " | Room=" + room + " | Days=" + d + " | " + added;
    }

    /**
     * Read the schedules already on file from the list API ({@code POST /api/PayableSchedule/Fetch}, ExecFlag
     * GRID) so the form is never pointed at a payable whose schedule Submit would overwrite.
     *
     * <p>The form's own {@code PayScheduleid} is NOT usable for this: after one save it keeps that id for every
     * subsequent selection, so it reads "taken" for everything.</p>
     */
    /**
     * Read the scheduled payables off the LIST GRID, widening the page size and walking every page.
     *
     * <p>Used in preference to the API: {@code POST /api/PayableSchedule/Fetch} answers with a SINGLE row however
     * it is called (all of ExecFlag GRID with locationid 1 / absent / 0 return one), and which row it returns
     * varies between calls — so it cannot say which payables are taken.</p>
     */
    public int harvestScheduledFromGrid() {
        scheduledPayables.clear();
        // widen the page size so fewer pages have to be walked
        page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                + " if(!s) return; let best=-1, bi=-1;"
                + " [...s.options].forEach((o,i)=>{ const n=parseInt((o.value||o.textContent||'').replace(/\\D/g,''),10); if(!isNaN(n) && n>best){ best=n; bi=i; } });"
                + " if(bi>=0){ const A=window.angular; s.selectedIndex=bi; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ A.element(s).triggerHandler('change'); }catch(x){} } }");
        waitForAngular(2000);
        for (int pageNo = 0; pageNo < 20; pageNo++) {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/\\s*1$/,''));"
                    + " const col=heads.findIndex(h=>/payable\\s*name/i.test(h));"
                    + " const rows=[...document.querySelectorAll('.ui-grid-row')].map(x=>[...x.querySelectorAll('.ui-grid-cell-contents')].map(c=>norm(c.textContent)));"
                    + " const names=rows.map(cells=>{ if(col>=0 && col<cells.length) return cells[col];"
                    + "   return cells.find(c=>/^(dr|ms|mr|mrs)\\.?\\s/i.test(c)) || ''; }).filter(x=>x);"
                    + " return JSON.stringify(names); }");
            String json = r == null ? "[]" : r.toString();
            if (json.length() > 2) {
                for (String n : json.substring(1, json.length() - 1).split("\",\""))
                    scheduledPayables.add(n.replaceAll("^\"|\"$", "").trim().toLowerCase());
            }
            Object more = page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/pageNextPageClick/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                    + " if(!b) return false; const dis=b.hasAttribute('disabled') || /disabled/i.test(b.className||'') || b.getAttribute('aria-disabled')==='true';"
                    + " if(dis) return false; b.click(); return true; }");
            if (!Boolean.TRUE.equals(more)) break;
            waitForAngular(1200);
        }
        System.out.println("  scheduled payables (" + scheduledPayables.size() + ") => "
                + scheduledPayables.stream().limit(8).toList());
        return scheduledPayables.size();
    }

    public int harvestScheduledPayables() {
        // The endpoint answers differently depending on how it is called — the app's own call returns the whole
        // master while a cache-busted one came back with a single row. Try the variants and keep the fullest.
        Object r = page.evaluate("async () => {"
                + " const bodies=[{ExecFlag:'GRID', locationid:1}, {ExecFlag:'GRID'}, {ExecFlag:'GRID', locationid:0}];"
                + " const urls=['/api/PayableSchedule/Fetch', '/api/PayableSchedule/Fetch?_cb=' + Date.now()];"
                + " let best=[], tried=[];"
                + " for(const u of urls){ for(const b of bodies){ try {"
                + "   const res = await fetch(u, {method:'POST', cache:'no-store',"
                + "     headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify(b)});"
                + "   const d = await res.json(); const arr=Array.isArray(d)?d:[];"
                + "   tried.push(u.split('?')[0]+JSON.stringify(b)+'='+arr.length);"
                + "   if(arr.length>best.length) best=arr; } catch(e){ tried.push('err'); } } }"
                + " const names=best.map(x=>(''+(x.PayableName||x.payablename||'')).trim()).filter(x=>x);"
                + " return JSON.stringify({n:best.length, tried:tried, names:names}); }");
        String json = r == null ? "{}" : r.toString();
        System.out.println("harvestScheduledPayables: " + json.substring(0, Math.min(400, json.length())));
        scheduledPayables.clear();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"names\":\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
        if (m.find() && !m.group(1).isBlank()) {
            for (String n : m.group(1).split("\",\"")) scheduledPayables.add(n.replaceAll("^\"|\"$", "").trim().toLowerCase());
        }
        System.out.println("  scheduled payables => " + scheduledPayables
                + "  (normalized: " + scheduledPayables.stream().map(PayableScheduleMaster::normalizeName).toList() + ")");
        return scheduledPayables.size();
    }

    /** Re-read the master and report whether {@code payable} now has a schedule — the proof that Submit inserted. */
    public boolean isNowScheduled(String payable) {
        for (int i = 0; i < 3; i++) {
            if (!onScreen() && !ROUTE.isEmpty()) {
                try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
                waitForAngular(2000);
            }
            harvestScheduledFromGrid();
            if (alreadyScheduled(payable)) return true;
            page.waitForTimeout(1000);
        }
        return false;
    }

    /**
     * Is this payable already scheduled? The two sides spell the same person differently — the master stores
     * "Dr. Doctor Aisya" where the form offers "Doctor Aisya".
     *
     * <p>The honorific must be peeled as a WHOLE WORD: a bare {@code ^dr\.?} also eats the "Dr" inside "Doctor"
     * and turns "Doctor Aisya" into "octor aisya", which matches nothing and reports a saved schedule as
     * missing.</p>
     */
    private static String normalizeName(String s) {
        return s == null ? "" : s.toLowerCase()
                .replaceAll("^\\s*dr\\.\\s*", "")     // "Dr. Doctor Aisya" -> "doctor aisya"
                .replaceAll("^\\s*dr\\s+", "")        // "Dr Doctor Aisya"  -> "doctor aisya"
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private boolean alreadyScheduled(String payable) {
        String p = normalizeName(payable);
        if (p.isEmpty()) return false;
        for (String s : scheduledPayables) if (normalizeName(s).equals(p)) return true;
        return false;
    }

    /**
     * Select a <b>Department</b> and then a <b>Payable</b> that has NO schedule yet.
     *
     * <p>Payable is a DEPENDENT list — empty until a Department is chosen, and some departments offer none. Each
     * candidate payable is checked two ways: its model must actually commit (the classic Payable trap, where a
     * full option list binds nothing), and {@link #payScheduleId()} must still be 0 — otherwise Submit would
     * overwrite that payable's existing schedule instead of adding one.</p>
     */
    public String selectDepartmentAndPayable() { return selectDepartmentAndPayable(0); }

    /** As above, but starting at the {@code from}-th department — used to move off a combination the master says
     *  is already scheduled. */
    public String selectDepartmentAndPayable(int from) {
        int departments = optionCount("payScheduleInfo.Departmentid");
        for (int d = from; d < Math.max(1, Math.min(departments, 40)); d++) {
            String dept = selectNth("payScheduleInfo.Departmentid", d);
            if (dept.isEmpty()) break;
            // The Payable list is refilled by the department's ng-change — wait for it rather than reading at once.
            int payables = -1;
            for (int w = 0; w < 12; w++) {
                payables = optionCount("payScheduleInfo.Payableid");
                if (payables > 0) break;
                page.waitForTimeout(500);
            }
            if (payables <= 0) { System.out.println("selectDepartmentAndPayable: '" + dept + "' offers no Payable — next department"); continue; }
            // Which of this department's payables are free? Decided from the harvested list, so the form is only
            // ever pointed at one of them — selecting a scheduled payable loads its rows and Submit overwrites it.
            String free = firstFreePayable();
            if (free.isEmpty()) { System.out.println("selectDepartmentAndPayable: every payable under '" + dept + "' is already scheduled — next department"); continue; }
            String pay = selectByText("payScheduleInfo.Payableid", free);
            if (pay.isEmpty() || !modelSet("payScheduleInfo.Payableid")) {
                System.out.println("selectDepartmentAndPayable: Payable did not commit under '" + dept + "' — next department");
                continue;
            }
            waitForAngular(1000);
            lastDepartment = dept;
            lastPayable = pay;
            lastDepartmentIndex = d;
            return "Department=" + dept + " | Payable=" + pay + " (of " + payables + ", none of it scheduled yet)";
        }
        lastDepartment = lastDepartment.isEmpty() ? "(none)" : lastDepartment;
        return "Department=" + lastDepartment + " | Payable=(none committed)";
    }

    /** Select the <b>Consultation Room</b>. */
    public String selectConsultationRoom() { lastRoom = selectNth("payScheduleInfo.cabinid", 0); return lastRoom; }

    /**
     * Enter <b>Start Time</b> and <b>End Time</b>.
     *
     * <p>Each time field is a timepicker: a text box (ng-model {@code inputTime}) PLUS <b>hours</b>/<b>minutes</b>
     * spinners and an AM/PM button. Typing into the text box alone is not enough — it displays the typed text
     * while the spinners keep their own value, and it is the SPINNERS the schedule row is built from (an
     * End Time typed as 05:00 PM landed in the grid as 08:00 AM). So both are set, spinners first.</p>
     */
    public String fillTimes(String start, String end) {
        setTimeWidget(0, start);
        setTimeWidget(1, end);
        lastStart = typeByLabel("Start Time", start);
        lastEnd = typeByLabel("End Time", end);
        // re-assert the spinners: the text box's own parse can push them back
        setTimeWidget(0, start);
        setTimeWidget(1, end);
        String shown = readTimeWidgets();
        return "Start Time=" + (lastStart.isEmpty() ? "(not set)" : lastStart)
                + " | End Time=" + (lastEnd.isEmpty() ? "(not set)" : lastEnd) + " | widgets: " + shown;
    }

    /** Set the {@code idx}-th timepicker (0 = Start, 1 = End) from a "hh:mm AM/PM" string. */
    private void setTimeWidget(int idx, String hhmm) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").matcher(hhmm);
        if (!m.find()) { System.out.println("setTimeWidget: cannot parse '" + hhmm + "'"); return; }
        String hh = m.group(1), mm = m.group(2);
        String mer = m.group(3) == null ? "" : m.group(3).toUpperCase();
        page.evaluate("([i,hh,mm,mer]) => { const A=window.angular;"
                + " const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null);"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null);"
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); };"
                + " set(hours[i], hh); set(mins[i], mm);"
                + " if(mer && hours[i]){ let p=hours[i].parentElement, hop=0, btn=null;"
                + "   while(p && hop++<6 && !btn){ btn=[...p.querySelectorAll('button,a')].find(b=>/^(am|pm)$/i.test((b.textContent||'').trim())); p=p.parentElement; }"
                + "   if(btn && (btn.textContent||'').trim().toUpperCase()!==mer) btn.click(); } }",
                java.util.Arrays.asList(String.valueOf(idx), hh, mm, mer));
        waitForAngular(400);
    }

    /** What the two timepickers currently hold — hours:minutes AM/PM, straight off the widgets. */
    private String readTimeWidgets() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null);"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null);"
                + " return hours.map((h,i)=>{ let p=h.parentElement, hop=0, btn=null;"
                + "   while(p && hop++<6 && !btn){ btn=[...p.querySelectorAll('button,a')].find(b=>/^(am|pm)$/i.test((b.textContent||'').trim())); p=p.parentElement; }"
                + "   return (h.value||'?')+':'+((mins[i]||{}).value||'?')+' '+(btn?norm(btn.textContent):''); }).join(' / '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Type into the time box whose label starts with {@code label}. Found from the INPUT side (walking up to its
     * label) rather than from the label side — the label is not an ancestor-container sibling of the input here,
     * so a label-first search finds nothing. Both boxes share ng-model {@code inputTime}, so the DOM order
     * (Start, then End) is the fallback.
     */
    private String typeByLabel(String label, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("([lbl,ord]) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<5 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; }"
                + "   return t.replace(/\\*/g,'').trim(); };"
                + " const boxes=[...document.querySelectorAll('input')].filter(e=>e.offsetParent!==null"
                + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'')"
                + "   && (/inputtime/i.test(e.getAttribute('ng-model')||'') || /time/i.test(labelOf(e))));"
                + " const want=new RegExp('^'+lbl.replace(/[.*+?^${}()|[\\]\\\\]/g,'\\\\$&'),'i');"
                + " let f=boxes.find(e=>want.test(labelOf(e)));"
                + " if(!f) f=boxes[ord];"                       // DOM order: 0 = Start Time, 1 = End Time
                + " if(!f) return false; f.id='__psmTime'; f.scrollIntoView({block:'center'}); return true; }",
                java.util.Arrays.asList(label, label.toLowerCase().startsWith("start") ? 0 : 1)));
        if (!tagged) { System.out.println("typeByLabel: no input for '" + label + "'"); return ""; }
        try {
            page.locator("#__psmTime").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            page.locator("#__psmTime").fill("");
            page.locator("#__psmTime").type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            page.locator("#__psmTime").press("Tab");
        } catch (Exception e) { System.out.println("typeByLabel(" + label + "): typing failed - " + e.getMessage()); }
        waitForAngular(500);
        Object v = page.evaluate("() => { const e=document.getElementById('__psmTime'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** PROBE — every time-ish input: its ng-model, the label above it, its value and its model value. */
    public String timeInputs() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const labelOf=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<5 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " return [...document.querySelectorAll('input')].filter(e=>e.offsetParent!==null && !/checkbox|radio|button|submit|hidden/i.test(e.type||''))"
                + "   .map((e,i)=>{ let mv=''; try{ const c=A.element(e).controller('ngModel'); mv=c?JSON.stringify(c.$modelValue):'-'; }catch(x){}"
                + "     return i+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+labelOf(e)+'\" value=\"'+(e.value||'')+'\" model='+mv"
                + "       +' cls=\"'+String(e.className||'').slice(0,40)+'\"'; }).join('\\n  '); }");
        return r == null ? "" : r.toString();
    }

    /** PROBE — the day checkboxes with the text actually next to each one. */
    public String dayLabels() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''))"
                + "   .map((c,i)=>i+':'+(norm((c.closest('label')||c.parentElement||{}).textContent||'').slice(0,14)||'?')).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Tick the <b>Days</b>. The seven boxes come from one ng-repeat (all bound to {@code todo.done}), so they are
     * taken in DOM order and each one's own adjacent text is the day name.
     */
    public String tickDays(String... wanted) {
        StringBuilder ticked = new StringBuilder();
        for (String day : wanted) {
            Object r = page.evaluate("(day) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''));"
                    + " const cb=cbs.find(c=>new RegExp(day,'i').test(norm((c.closest('label')||c.parentElement||{}).textContent||'')));"
                    + " if(!cb) return ''; if(cb.checked) return 'already'; cb.id='__psmDay'; return 'tag'; }", day);
            String how = String.valueOf(r);
            if (how.isEmpty()) { System.out.println("tickDays: no checkbox for " + day); continue; }
            if ("tag".equals(how)) robustClick("__psmDay", "tickDays");
            boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__psmDay');"
                    + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
            if (on || "already".equals(how)) ticked.append(ticked.length() == 0 ? "" : ", ").append(day);
            waitForAngular(200);
        }
        lastDays = ticked.toString();
        return lastDays;
    }

    /** How many rows the Days detail grid holds (its own header row and the entry row excluded). */
    public int scheduleRowCount() {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return 0;"
                + " return [...tbl.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** PROBE — what the Days grid holds. */
    public String scheduleRows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return '(no schedule table)';"
                + " return [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent).slice(0,60)).filter(t=>t).slice(0,8).join(' ;; '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click the form's <b>Add</b> ({@code fnAddPaySchedule()}) that commits the day/time line. Clicked exactly
     * ONCE — a blind DOM-click fallback re-fires it against the inputs the first click cleared.
     */
    public String clickInnerAdd() {
        int before = scheduleRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__psmAddToasts=[]; if(window.__psmAddObs) window.__psmAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__psmAddToasts.includes(t)) window.__psmAddToasts.push(t); }); };"
                + " window.__psmAddObs=new MutationObserver(grab); window.__psmAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__psmAddRow'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";
        boolean realClicked = true;
        try { page.locator("#__psmAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1200);
        int after = scheduleRowCount();
        if (after == before && !realClicked) {
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__psmAddRow'); if(e) e.click(); }");
            waitForAngular(1200);
            after = scheduleRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__psmAddRow'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__psmAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "schedule rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().matches(".*(payschedule|payableschedule).*/iud.*")
                || resp.url().toLowerCase().contains("payschedule/iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__psmToasts=[]; if(window.__psmObs) window.__psmObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__psmToasts.includes(t)) window.__psmToasts.push(t); }); };"
                + " window.__psmObs=new MutationObserver(grab); window.__psmObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__psmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__psmSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__psmToasts||[]).includes(t)) (window.__psmToasts=window.__psmToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__psmToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        System.out.println("submit: toasts => " + lastToasts);
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__psmToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
