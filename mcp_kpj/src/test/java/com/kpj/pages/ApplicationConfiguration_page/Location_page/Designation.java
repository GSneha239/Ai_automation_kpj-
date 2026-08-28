package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Designation</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Designation</b> → enter <b>Code</b> +
 * <b>Remark</b> → <b>Submit</b> → success toast. {@link #clickAddIfPresent()} covers both shapes: an Add button
 * that opens a form, and an inline-add screen whose boxes are already on the list.</p>
 */
public class Designation extends BasePage {

    public Designation(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastCode = "", lastRemark = "";
    public String lastToasts = "[]", lastSaveApi = "";
    /** The screen while the message is up. */
    public byte[] toastPng;

    /**
     * Designations a hospital actually has. The Remark carries one of these rather than a generated
     * "Auto designation DG123" — these rows are read by people in the grid.
     */
    private static final String[] DESIGNATIONS = {
            "Staff Nurse", "Medical Officer", "Senior Consultant", "Pharmacist", "Radiographer",
            "Physiotherapist", "Ward Clerk", "Medical Assistant", "Clinical Dietitian", "Phlebotomist",
            "Occupational Therapist", "Nursing Sister", "Laboratory Technologist", "Admissions Officer"
    };

    /**
     * Locate the <b>Code</b> and <b>Remark</b> boxes. Each match is CLAIMED so the two roles can never resolve to
     * the same box, and the Code is claimed FIRST — a "Designation Code" label contains "Designation", so a
     * name-first search would take the code box.
     */
    private static final String FIND_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const lbl=e=>{ let t='';"
            + "   if(e.id){ try{ const l=document.querySelector('label[for=\"'+CSS.escape(e.id)+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
            + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
            + "   return t.replace(/\\*/g,'').trim(); };"
            + " const skip=m=>/colFilter|pagination|row\\.entity|textAngular|^q$/i.test(m||'');"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
            + "   && !/hidden|checkbox|radio|button|submit/i.test(e.type||'') && !skip(e.getAttribute('ng-model')));"
            + " const used=new Set(); const mdl=e=>e.getAttribute('ng-model')||'';"
            + " const pick=(...tests)=>{ for(const t of tests){ const e=boxes.find(x=>!used.has(x) && t(x)); if(e){ used.add(e); return e; } } return null; };"
            + " const codeEl = pick(e=>/^code$/i.test(lbl(e)), e=>/code/i.test(lbl(e)), e=>/\\.code$/i.test(mdl(e)), e=>/code/i.test(mdl(e)));"
            + " const remEl  = pick(e=>/^(remark|remarks|description|designation)$/i.test(lbl(e)),"
            + "                     e=>/remark|description|designation|name/i.test(lbl(e)),"
            + "                     e=>/\\.(description|remark|name)$/i.test(mdl(e)), e=>/description|remark|name/i.test(mdl(e)));";

    // ---- probes ----------------------------------------------------------

    public String findDesignationLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/designation/i.test(norm(a.textContent)) || /designation/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,30).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,30); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|pagination|row\\.entity/i.test(e.getAttribute('ng-model')||'')).slice(0,20)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,10)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

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
            System.out.println("Designation.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*designation\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/designation/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__dgMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__dgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("Designation.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__dgMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__dgMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("Designation.nav: menu link not found. designation links => " + findDesignationLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("Designation.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /designation/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("Designation." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("Designation." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click <b>Add</b> if this screen has one; a no-op when the boxes are already on screen (inline add). */
    public String clickAddIfPresent() {
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }")))
            return "inline-add (the form is already on screen)";
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button],[ng-click]')].find(x=>x.offsetParent!==null"
                + "   && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__dgAdd'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "no Add button and no inline form";
        robustClick("__dgAdd", "clickAdd");
        page.evaluate("() => { const e=document.getElementById('__dgAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return "Add clicked";
    }

    /**
     * Make sure the <b>Form Name</b> drop-down really says {@code wanted}.
     *
     * <p>This is the generic CommonMaster screen (models {@code commonmaster.Code} / {@code .Description}); the
     * Form Name is what decides WHICH master the row is written into. Trusting the route's default is how a row
     * ends up in a different master, so it is asserted — and set — explicitly.</p>
     */
    public String ensureFormName(String wanted) {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let p=e.parentElement,h=0,t=''; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
                + " const s=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && /form\\s*name/i.test(lbl(e)));"
                + " if(!s) return ''; s.id='__dgForm';"
                + " return (s.selectedIndex>=0 ? (s.options[s.selectedIndex].textContent||'').trim() : '') + '\\u0001' + s.options.length; }");
        String info = found == null ? "" : found.toString();
        if (info.isEmpty()) return "(no Form Name drop-down on this screen)";
        String current = info.split("\u0001")[0];
        String count = info.contains("\u0001") ? info.split("\u0001")[1] : "?";
        if (current.equalsIgnoreCase(wanted)) {
            page.evaluate("() => { const e=document.getElementById('__dgForm'); if(e) e.removeAttribute('id'); }");
            return "Form Name = " + current + " (of " + count + " masters)";
        }
        Object idx = page.evaluate("(w) => { const e=document.getElementById('__dgForm'); if(!e) return -1;"
                + " const n=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " let i=[...e.options].findIndex(o=>n(o.textContent)===n(w));"
                + " if(i<0) i=[...e.options].findIndex(o=>o.value && n(o.textContent).includes(n(w))); return i; }", wanted);
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__dgForm'); if(e) e.removeAttribute('id'); }");
            return "Form Name is \"" + current + "\" and \"" + wanted + "\" is not in the list of " + count; }
        try {
            page.locator("#__dgForm").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            page.evaluate("(k) => { const el=document.getElementById('__dgForm'); if(!el) return; const A=window.angular;"
                    + " el.selectedIndex=k; el.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(el).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(1500);
        Object now = page.evaluate("() => { const e=document.getElementById('__dgForm');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return "Form Name = " + now + " (was \"" + current + "\", of " + count + " masters)";
    }

    /**
     * Continue the CODE FORMAT the master already uses ({@code DS00000080} → {@code DS00000532}) instead of
     * inventing one: the grid is paged and sorted, so the highest code lives on the LAST page.
     */
    public String nextCodeFromTable(int attempt) {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/pageLastPageClick/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(2000);
        // The last page renders asynchronously — reading once lands on an empty grid and quietly falls back to an
        // invented code, which is exactly what "continue the table's format" is meant to avoid.
        String json = "[]", previous = "";
        for (int i = 0; i < 14; i++) {
            // Take every cell of the grid, ui-grid or plain table, and keep the ones SHAPED like a code — the
            // column-index route is brittle here (a leading select column shifts the headers off the cells).
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cells=[...document.querySelectorAll('.ui-grid-row .ui-grid-cell-contents, table tbody tr td')]"
                    + "   .map(c=>norm(c.textContent)).filter(x=>/^[A-Za-z]{1,4}\\d{3,}$/.test(x));"
                    + " return JSON.stringify(cells); }");
            json = r == null ? "[]" : r.toString();
            // Two identical reads in a row: the first non-empty read can catch the grid mid-render, mixing rows
            // from the page being left with the page being entered.
            if (json.length() > 2 && json.equals(previous)) break;
            previous = json;
            page.waitForTimeout(700);
        }
        System.out.println("nextCodeFromTable: last-page codes => " + json.substring(0, Math.min(200, json.length())));
        String prefix = "DS";
        int width = 8;
        long max = -1;
        if (json.length() > 2) {
            for (String c : json.substring(1, json.length() - 1).split("\",\"")) {
                String code = c.replaceAll("^\"|\"$", "").trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z]+)(\\d+)$").matcher(code);
                if (!m.matches()) continue;
                prefix = m.group(1);
                width = Math.max(width, m.group(2).length());
                try { max = Math.max(max, Long.parseLong(m.group(2))); } catch (Exception ignore) { }
            }
        }
        // back to the first page so the screenshot shows the normal view
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/pageFirstPageClick/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1200);
        if (max < 0) return "DS" + String.format("%08d", Math.abs((System.nanoTime() / 13 + attempt * 7919L) % 100000000));
        return prefix + String.format("%0" + width + "d", max + 1 + attempt);
    }

    /** Are both boxes on screen? */
    public boolean formReady() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"));
    }

    /**
     * Enter a unique <b>Code</b> and a <b>Remark</b> that names a real designation ({@code attempt} moves both
     * along so a retry after "already exists" submits different details).
     */
    public String fillCodeAndRemark(int attempt) {
        // Continue the master's own code format (DS00000531 -> DS00000532) rather than inventing a shape.
        lastCode = nextCodeFromTable(attempt);
        lastRemark = DESIGNATIONS[(int) (Math.abs(System.nanoTime() / 7 + attempt) % DESIGNATIONS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')"
                + "   +' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("designation")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__dgToasts=[]; if(window.__dgObs) window.__dgObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dgToasts.includes(t)) window.__dgToasts.push(t); }); };"
                + " window.__dgObs=new MutationObserver(grab); window.__dgObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit],a.btn')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__dgSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__dgSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dgToasts||[]).includes(t)) (window.__dgToasts=window.__dgToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__dgToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__dgToasts||[]; return a.find(x=>/saved|success|succes|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
