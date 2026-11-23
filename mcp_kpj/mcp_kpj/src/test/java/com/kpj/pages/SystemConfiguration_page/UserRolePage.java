package com.kpj.pages.SystemConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * System Configuration &gt; <b>User Role</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>System Configuration</b> → <b>User Role</b> ({@code #/UserRole}) → <b>Add</b> → enter <b>Role*</b>
 * and <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The field ng-models are not assumed — this screen sits in a different module from the Application
 * Configuration masters and does not share their {@code commonmaster.*} prefix. Role and Remark are located
 * <b>by their labels</b> and the discovered ng-model of each is echoed back in the step text, and the Submit
 * handler is read off the button rather than hard-coded.</p>
 */
public class UserRolePage extends BasePage {

    public UserRolePage(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/UserRole";
    public String lastRole = "", lastRemark = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Realistic role names — never "Auto role <CODE>"; only the numeric suffix keeps the run unique. */
    private static final String[] ROLES = {
            "Ward Nurse", "Billing Executive", "Front Desk Officer", "Pharmacy Assistant",
            "Radiology Technician", "Medical Records Officer", "Admissions Clerk"};
    private static final String[] REMARKS = {
            "Handles day to day ward duties", "Manages patient billing and receipts",
            "Registers and directs walk-in patients", "Dispenses and records medication",
            "Performs imaging procedures", "Maintains patient case files", "Processes inpatient admissions"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const flds=()=>[...document.querySelectorAll('input[type=text],input:not([type]),textarea')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0);"
          + " const byLabel=re=>flds().find(e=>re.test(labelOf(e)));";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/system\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*system\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1500);
            // EXACT "User Role" — plain "User" (#/User) is the sibling entry directly above it.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__urMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*user\\s*role\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/UserRole'); if(!a) return ''; a.id='__urMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("UserRolePage.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__urMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__urMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__urMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => /userrole/i.test(location.hash) && [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("UserRolePage.nav: User Role list not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the User Role list (its Add button is present) or already on the Add form. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("userrole")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null)"))
                || onAddForm();
    }

    /** Real-click <b>Add</b> and wait for the entry form (a Role field). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " document.querySelectorAll('#__urAdd').forEach(e=>e.removeAttribute('id'));"
                    + " const b=[...document.querySelectorAll('[ng-click],button,a')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__urAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("UserRolePage.clickAdd: Add button not found"); return false; }
        try { page.locator("#__urAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("UserRolePage.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__urAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/^role/i) || !!byLabel(/remark/i); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("UserRolePage.clickAdd: entry form did not render"); }
        waitForAngular(1200);
        return onAddForm();
    }

    /** True once an entry form with a Role (or Remark) field is up. */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/^role/i) && !!byLabel(/remark/i); }"));
    }

    /** Every visible form field with its label + ng-model — so a fill failure shows what the screen offered. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const btns=[...document.querySelectorAll('button,a[ng-click]')].filter(e=>e.offsetParent!==null).map(b=>norm(b.textContent)+'{'+(b.getAttribute('ng-click')||'-')+'}').filter(s=>s.length<60);"
                + " return 'fields: '+f.join(' ; ')+' || buttons: '+btns.join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Role*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed). */
    public String fillDetails() {
        int i = (int) (Math.abs(System.nanoTime()) % ROLES.length);
        // Role must be LETTERS ONLY — a digit in the name makes the screen reject the save silently (no request,
        // no message). Uniqueness therefore comes from a letters-only suffix, not a number.
        lastRole = ROLES[i] + " " + randomLetters(4);
        lastRemark = REMARKS[i];
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const rl=fill(byLabel(/^role/i), a.role); const rm=fill(byLabel(/remark/i), a.remark);"
                + " return 'Role='+rl+' | Remark='+rm; }",
                java.util.Map.of("role", lastRole, "remark", lastRemark));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Letters-only suffix — the Role field rejects digits. */
    private static String randomLetters(int n) {
        StringBuilder sb = new StringBuilder();
        long seed = Math.abs(System.nanoTime());
        for (int i = 0; i < n; i++) { sb.append((char) ('A' + (seed % 26))); seed /= 26; }
        return sb.toString();
    }

    /**
     * Tick ONE right checkbox in the menu tree (a single {@code Menu.IsSelected} / {@code WebPage.View} node) —
     * not "Select All".
     *
     * <p>Two traps here, both verified live:</p>
     * <ul>
     *   <li>{@code UserRole.SelectAll} only toggles ITSELF (ticking it left exactly 1 checkbox checked, not the
     *       ~1423 rights), so it grants the role nothing.</li>
     *   <li>A PARENT node cascades — ticking {@code Menu.IsSelected} on "Inventory" checked <b>270</b> boxes.
     *       So only a LEAF right ({@code WebPage.View} / {@code WebPage.Add}) is used, which selects one right
     *       rather than a whole branch.</li>
     * </ul>
     */
    public String tickOneRight() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__urRight').forEach(e=>e.removeAttribute('id'));"
                // LEAF rights only — a parent (Menu.IsSelected / SubMenuX.IsSelected) cascades to its whole branch.
                + " const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked"
                + "   && /^WebPage\\.(View|Add)$/.test(x.getAttribute('ng-model')||''));"
                + " if(!c) return ''; c.id='__urRight';"
                + " const row=c.closest('tr,li,div');"
                + " return (c.getAttribute('ng-model')||'right')+' :: '+norm((row?row.textContent:'')).slice(0,40); }");
        String what = tagged == null ? "" : tagged.toString();
        if (what.isEmpty()) { System.out.println("tickOneRight: no un-ticked right checkbox found"); return "(no right checkbox found)"; }
        try { page.locator("#__urRight").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000)); }
        catch (Exception e) { System.out.println("tickOneRight: click failed - " + e.getMessage()); }
        Object ok = page.evaluate("() => { const c=document.getElementById('__urRight'); const v=c?c.checked:false; if(c) c.removeAttribute('id'); return v; }");
        waitForAngular(1200);
        Object n = page.evaluate("() => [...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.checked).length");
        return what + (Boolean.TRUE.equals(ok) ? " (ticked" : " (NOT ticked") + ", total checked=" + (n == null ? "?" : n) + ")";
    }

    /** Click <b>Submit</b> (handler read off the button) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__urToasts=[]; if(window.__urObs) window.__urObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__urToasts.includes(t)) window.__urToasts.push(t); }); };"
                + " window.__urObs=new MutationObserver(grab); window.__urObs.observe(document.body,{childList:true,subtree:true}); }");
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__urSubmit').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,input[type=submit],a')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return ''; b.id='__urSubmit'; return b.getAttribute('ng-click')||'submit'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        System.out.println("UserRolePage submit => " + how);
        try { page.locator("#__urSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__urSubmit'); if(e) e.removeAttribute('id'); }");
        // Some saves raise a confirm first.
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].some(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].find(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''));"
                    + " const b=[...(box||document).querySelectorAll('button,a')].find(x=>/^(yes|ok|save|submit)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(800);
        }
        try {
            page.waitForFunction("() => (window.__urToasts||[]).some(a=>/saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__urToasts||[]; return a.find(x=>/saved|added|success|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
