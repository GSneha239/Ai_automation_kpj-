package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Payable Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Payable Type</b> ({@code #/paybletype}) →
 * <b>Add</b> ({@code AddPaybleType()}, opens {@code #/add-PaybleType}) → <b>Code</b>, <b>Remark</b>, tick
 * <b>Refentity</b> → <b>Submit</b> ({@code IUDPaybleType()}) → success toast <i>"Payable Type Saved
 * Successfully."</i>, save API {@code POST /api/PaybleType/IUD}.</p>
 *
 * <p>The app spells its own models/routes {@code PaybleType} (no second "a") throughout — kept verbatim in every
 * selector rather than "corrected", or nothing matches. <b>Refentity is a CHECKBOX</b>
 * ({@code paybletype.IsRefentity}), not a select — there is no dropdown anywhere on this form; the flow's "select
 * Refentity" means tick it. Unlike the CommonMaster screens ([[devhis-designation]] etc.), existing Codes in this
 * master are NOT one consistent format (`PY00000001`, `PY5`, `0001`, `2207`, `32`, ...) so there is no table
 * format to continue — a fresh unique code is generated instead.</p>
 */
public class PayableType extends BasePage {

    public PayableType(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastCode = "", lastRemark = "";
    public boolean lastRefentity = false;
    public String lastToasts = "[]", lastSaveApi = "";
    /** The screen while the message is up. */
    public byte[] toastPng;

    /** Payable-type categories a hospital actually uses — no "Auto Payable Type X" placeholders. */
    private static final String[] CATEGORIES = {
            "Contractor", "Insurance Agent", "Referral Doctor", "Locum", "Supplier",
            "Government Panel", "Corporate Client", "Sponsor", "Third Party Administrator", "Broker"
    };

    // ---- probes ----------------------------------------------------------

    public String findPayableTypeLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/payable\\s*type/i.test(norm(a.textContent)) || /paybletype/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
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
            System.out.println("PayableType.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*payable\\s*type\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/paybletype/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__ptMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__ptMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("PayableType.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__ptMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__ptMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("PayableType.nav: menu link not found. payable type links => " + findPayableTypeLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("PayableType.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /payable\\s*type/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("PayableType." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("PayableType." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> ({@code AddPaybleType()}). Polls generously and looks past visibility
     *  too — on this screen the button has been seen not to render for 15-20s after the grid itself is up. */
    public boolean clickAdd() {
        boolean tagged = false;
        String lastState = "";
        for (int i = 0; i < 40 && !tagged; i++) {
            Object state = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const all=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')];"
                    + " const vis=all.filter(x=>x.offsetParent!==null);"
                    + " let b=vis.find(x=>/addpabletype/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(b){ b.id='__ptAdd'; b.scrollIntoView({block:'center'}); return 'tagged'; }"
                    + " const hidden=all.find(x=>/addpabletype/i.test(x.getAttribute('ng-click')||''));"
                    + " return hidden ? 'hidden' : 'absent'; }");
            lastState = String.valueOf(state);
            tagged = "tagged".equals(lastState);
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("PayableType.clickAdd: Add button not found (last state: " + lastState + ")"); return false; }
        robustClick("__ptAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Code/Remark boxes) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')]"
                + " .some(s=>/paybletype\\.Code/i.test(s.getAttribute('ng-model')||''))"));
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}) so a retry starts from a fresh form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__ptBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__ptBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** Enter <b>Code</b> and <b>Remark</b>, and tick <b>Refentity</b>. {@code attempt} moves both along so a retry
     *  after "already exists" submits different details. */
    public String fillAll(int attempt) {
        lastCode = "PY" + String.format("%08d", Math.abs((System.nanoTime() / 977 + attempt * 7919L) % 100000000));
        lastRemark = CATEGORIES[(int) (Math.abs(System.nanoTime() / 7 + attempt) % CATEGORIES.length)];
        page.evaluate("(a) => { const A=window.angular;"
                + " const code=[...document.querySelectorAll('input')].find(x=>/paybletype\\.Code/i.test(x.getAttribute('ng-model')||''));"
                + " const rem=[...document.querySelectorAll('input')].find(x=>/paybletype\\.Description/i.test(x.getAttribute('ng-model')||''));"
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); };"
                + " set(code, a.code); set(rem, a.remark); }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        waitForAngular(400);
        lastRefentity = tickRefentity();
        return "Code=" + lastCode + " | Remark=" + lastRemark + " | Refentity=" + lastRefentity;
    }

    /**
     * Tick the <b>Refentity</b> checkbox — it is a checkbox on this screen, not a select. Polled: it can render a
     * beat after the rest of the form (same async-render lag as the list's Add button).
     */
    public boolean tickRefentity() {
        String how = "";
        for (int i = 0; i < 10 && how.isEmpty(); i++) {
            Object r = page.evaluate("() => { const e=[...document.querySelectorAll('input[type=checkbox]')]"
                    + " .find(x=>/paybletype\\.IsRefentity/i.test(x.getAttribute('ng-model')||'') && x.offsetParent!==null);"
                    + " if(!e) return ''; if(e.checked) return 'already'; e.id='__ptRef'; return 'tag'; }");
            how = r == null ? "" : r.toString();
            if (how.isEmpty()) page.waitForTimeout(500);
        }
        if ("already".equals(how)) return true;
        if (!"tag".equals(how)) { System.out.println("tickRefentity: checkbox not found"); return false; }
        robustClick("__ptRef", "tickRefentity");
        boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__ptRef');"
                + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
        waitForAngular(300);
        return on;
    }

    /** Click <b>Submit</b> ({@code IUDPaybleType()}) and return the toast; screenshots the message the moment it
     *  appears. A successful Submit navigates straight back to the list ({@code #/paybletype}). */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("paybletype")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__ptToasts=[]; if(window.__ptObs) window.__ptObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ptToasts.includes(t)) window.__ptToasts.push(t); }); };"
                + " window.__ptObs=new MutationObserver(grab); window.__ptObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__ptSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__ptSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ptToasts||[]).includes(t)) (window.__ptToasts=window.__ptToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__ptToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__ptToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
