package com.kpj.pages.SystemConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * System Configuration &gt; <b>User</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>System Configuration</b> → <b>User</b> ({@code #/User}) → <b>Add</b>
 * ({@code #/Add-UserDetailsModified}) → select <b>Payable Type*</b> ({@code UserDetailsModified.PayableTypeID})
 * and <b>Payable*</b> ({@code UserDetailsModified.EDPID}) → enter <b>Login ID*</b>
 * ({@code UserDetailsModified.LoginName}), <b>Password*</b> ({@code UserDetailsModified.Password}) and
 * <b>Confirm Password*</b> ({@code UserDetailsModified.ConfirmPassword}) → tick a <b>Location Details</b> row
 * ({@code Loc.IsSelected}) → select <b>OPD Waiting Area</b> ({@code UserDetailsModified.WaitingAreaID}) and
 * <b>Deposit/Receipt Type</b> → tick a right in the {@code #UserDetailsTab} block → <b>Submit</b>
 * ({@code FnIUDUserDetails()}) → success toast.</p>
 *
 * <p><b>Payable</b> is a CASCADE off Payable Type — its list is empty until a type is chosen, so it is polled
 * rather than read once. The <b>Location Details</b> rows and the rights checkboxes are real-clicked so their
 * ng-change handlers fire.</p>
 *
 * <p>This screen creates a LOGIN ACCOUNT. The password is a throwaway test value used only on this QA
 * environment; it is not a credential for anything else.</p>
 */
public class UserPage extends BasePage {

    public UserPage(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/User";
    /** Throwaway password for the QA account this flow creates (meets the usual upper/lower/digit/symbol rules). */
    public static final String TEST_PASSWORD = "Qa@Test12345";

    public String lastLoginId = "", lastPayableType = "", lastPayable = "",
            lastWaitingArea = "", lastDepositType = "", lastLocation = "", lastRight = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    /** <b>System Configuration</b> → <b>User</b> ({@code #/User}). */
    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/system\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*system\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1500);
            // EXACT "User" — "User Role" (#/UserRole) sits right next to it in the same submenu.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__usrMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*user\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/User'); if(!a) return ''; a.id='__usrMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("UserPage.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__usrMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__usrMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__usrMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => /#\\/user/i.test(location.hash) && [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("UserPage.nav: User list not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the User list (route {@code #/User}) with its Add button, or already on the Add form. */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        if (!(u.contains("#/user") || u.contains("userdetailsmodified"))) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null)"))
                || onAddForm();
    }

    /** Real-click <b>Add</b> → {@code #/Add-UserDetailsModified}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " document.querySelectorAll('#__usrAdd').forEach(e=>e.removeAttribute('id'));"
                    + " const b=[...document.querySelectorAll('[ng-click],button,a')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__usrAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("UserPage.clickAdd: Add button not found"); return false; }
        try { page.locator("#__usrAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("UserPage.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__usrAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='UserDetailsModified.LoginName')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("UserPage.clickAdd: add form did not render"); }
        waitForAngular(1200);
        return onAddForm();
    }

    /**
     * True once the Add form is up. Checks the URL FIRST — the Login ID field alone is only visible on the
     * User Details tab, so relying on it gave a false "not on the form" negative whenever a different tab
     * (Inventory Details1 / Report Field Settings / Integration Access Control) was the currently active one.
     */
    public boolean onAddForm() {
        if (page.url().toLowerCase().contains("add-userdetailsmodified")) return true;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='UserDetailsModified.LoginName')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Select <b>Payable Type*</b> then <b>Payable*</b> (a cascade — the Payable list is empty until the type is
     * chosen, so it is polled), and enter <b>Login ID*</b> / <b>Password*</b> / <b>Confirm Password*</b>.
     */
    public String fillUserDetails() {
        // Login ID must be UNIQUE. A 5-digit value from nanoTime%100000 is only a 100k space AND nanoTime's low
        // digits have poor entropy, so repeat runs collided. Use the epoch millis (unique per run) plus a random
        // tail, which cannot repeat within a run either.
        lastLoginId = newLoginId();
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const el=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const setTxt=(ng,v)=>{ const e=el(ng); if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ let e=null;"
                + "   for(let k=0;k<20;k++){ e=el(ng);"
                + "     if(e && e.tagName==='SELECT' && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))) break;"
                + "     await sleep(400); }"
                + "   if(!e || e.tagName!=='SELECT') return '(no select)';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no options)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   await sleep(500); return norm(e.options[i].textContent); };"
                // Payable Type FIRST — it drives the Payable list.
                + " const pt=await pick('UserDetailsModified.PayableTypeID');"
                + " await sleep(1200);"
                + " const pay=await pick('UserDetailsModified.EDPID');"
                + " const lid=setTxt('UserDetailsModified.LoginName', a.login);"
                + " const pw=setTxt('UserDetailsModified.Password', a.pwd);"
                + " const cpw=setTxt('UserDetailsModified.ConfirmPassword', a.pwd);"
                + " resolve('PayableType='+pt+' | Payable='+pay+' | LoginID='+lid+' | Password='+(pw==='(no field)'?'(no field)':'set')+' | ConfirmPassword='+(cpw==='(no field)'?'(no field)':'set')); })",
                java.util.Map.of("login", lastLoginId, "pwd", TEST_PASSWORD));
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("PayableType=");
        if (i >= 0) lastPayableType = s.substring(i + 12, s.indexOf(" | ", i)).trim();
        int j = s.indexOf("Payable=");
        if (j >= 0) lastPayable = s.substring(j + 8, s.indexOf(" | ", j)).trim();
        return s;
    }

    /**
     * Tick a <b>Location Details</b> row ({@code Loc.IsSelected} — real click so its ng-change fires) and select
     * <b>OPD Waiting Area</b> ({@code UserDetailsModified.WaitingAreaID}) and <b>Deposit/Receipt Type</b>.
     *
     * <p>Deposit/Receipt Type is located by its LABEL first (the form's labels are laid out so a positional guess
     * is unreliable), falling back to {@code UserDetailsModified.SubGroupID}.</p>
     */
    public String fillLocationAndTypes() {
        // Location row — real click.
        Object locTagged = page.evaluate("() => { document.querySelectorAll('#__usrLoc').forEach(e=>e.removeAttribute('id'));"
                + " const c=[...document.querySelectorAll(\"input[type=checkbox][ng-model='Loc.IsSelected']\")].find(x=>x.offsetParent!==null);"
                + " if(!c) return ''; c.id='__usrLoc';"
                + " const row=c.closest('tr'); const txt=row?[...row.querySelectorAll('td')].map(t=>(t.textContent||'').replace(/\\s+/g,' ').trim()).filter(Boolean)[1]||'' : '';"
                + " return txt || 'row'; }");
        lastLocation = locTagged == null ? "" : locTagged.toString();
        if (!lastLocation.isEmpty()) {
            try { page.locator("#__usrLoc").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000)); }
            catch (Exception e) { System.out.println("fillLocationAndTypes: location click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__usrLoc'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(800);

        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const choose=async(sel)=>{ if(!sel) return '(not found)';"
                + "   for(let k=0;k<15;k++){ if([...sel.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))) break; await sleep(400); }"
                + "   const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no options)';"
                + "   sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(x){} if($){try{$(sel).trigger('change');}catch(x){}}"
                + "   await sleep(400); return norm(sel.options[i].textContent); };"
                + " const byNg=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + " const wa=await choose(byNg('UserDetailsModified.WaitingAreaID'));"
                // Deposit/Receipt Type — by label, else the SubGroupID select.
                + " let dep=[...document.querySelectorAll('select')].find(x=>x.offsetParent!==null && /deposit|receipt/i.test(labelOf(x)));"
                + " let via='label'; if(!dep){ dep=byNg('UserDetailsModified.SubGroupID'); via='SubGroupID'; }"
                + " const dv=await choose(dep);"
                + " resolve('OPDWaitingArea='+wa+' | DepositReceiptType='+dv+' [via '+via+(dep?(' ng='+(dep.getAttribute('ng-model')||'?')):'')+']'); })");
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("OPDWaitingArea=");
        if (i >= 0) lastWaitingArea = s.substring(i + 15, s.indexOf(" | ", i)).trim();
        int j = s.indexOf("DepositReceiptType=");
        if (j >= 0) lastDepositType = s.substring(j + 19).replaceAll("\\s*\\[via.*$", "").trim();
        return "Location=" + (lastLocation.isEmpty() ? "(none)" : lastLocation) + " | " + s;
    }

    /**
     * Make sure <b>Deposit/Receipt Type</b> ends up with a value, changing <b>Payable Type</b> if it does not.
     *
     * <p>Deposit/Receipt Type is a STARRED field fed by the Payable Type → Payable cascade. The fill always took
     * the FIRST Payable Type ("Agent"), which yields <i>no</i> deposit/receipt options, so the field stayed empty
     * and Submit was rejected with the unhelpful <i>"Please fill * mark fields!"</i> — while the fill step still
     * reported PASS, because it only checked that it ran. Walk the Payable Type options until one produces a
     * Deposit/Receipt Type, then select it. Same shape as the Registration department walk.</p>
     *
     * @return what happened, for the report
     */
    public String ensureDepositReceiptType() {
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(x=>setTimeout(x,ms));"
                + " const byNg=ng=>[...document.querySelectorAll('select')].find(e=>e.getAttribute('ng-model')===ng && e.offsetParent!==null);"
                + " const real=e=>e?[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent))):[];"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                + " const pickFirst=e=>{ const rs=real(e); if(!rs.length) return ''; e.selectedIndex=rs[0].i; fire(e); return norm(rs[0].o.textContent); };"
                + " const dep=()=>byNg('UserDetailsModified.SubGroupID');"
                // Already has a value? nothing to do.
                + " let d=dep(); if(d){ const cur=norm((d.options[d.selectedIndex]||{}).textContent||'');"
                + "   if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return resolve('already set: '+cur); }"
                + " if(d && real(d).length){ const v=pickFirst(d); return resolve('selected without changing Payable Type: '+v); }"
                + " const pt=byNg('UserDetailsModified.PayableTypeID'); if(!pt) return resolve('(no Payable Type field)');"
                // STOP unless explicitly asked to walk. Cycling Payable Type looking for a Deposit/Receipt Type
                // DESTROYS the Payable selection: the list for the original type never comes back, the fallback
                // type loses its list too once its cascade settles, and Submit is then rejected with
                // "Please fill * mark fields!" naming an EDPID this probe emptied. On DSH and KLG the walk can
                // never succeed anyway — Deposit/Receipt Type has ZERO options for EVERY Payable Type, which is
                // the app defect this step should report. A diagnostic must not break the form it is inspecting.
                + " if(!a.walk) return resolve('Deposit/Receipt Type has no options for Payable Type \"'"
                + "   +norm((pt.options[pt.selectedIndex]||{}).textContent||'')+'\" — NOT walking the other types"
                + " (that empties Payable and blocks Submit); pass -Ddeposit.walk=true to force it');"
                // POLL for each dependent list instead of a fixed wait. A fixed sleep picked nothing when the list
                // was still loading, which left PAYABLE ITSELF EMPTY — and Deposit/Receipt Type hangs off Payable,
                // not off Payable Type, so the walk could never succeed and it left the form worse than it found it.
                + " const waitOpts=async(ng,ms)=>{ const end=Date.now()+ms; while(Date.now()<end){ const e=byNg(ng); if(e && real(e).length) return e; await sleep(300); } return null; };"
                + " const types=real(pt); const startIdx=pt.selectedIndex; let startType=norm((pt.options[startIdx]||{}).textContent||'');"
                + " let noPayable=0; let goodIdx=-1;"
                + " for(const t of types){ if(t.i===startIdx) continue;"
                + "   pt.selectedIndex=t.i; fire(pt);"
                + "   const pay=await waitOpts('UserDetailsModified.EDPID', 8000);"
                + "   if(!pay){ noPayable++; continue; }"                     // this type has no Payable at all
                + "   if(goodIdx<0) goodIdx=t.i;"                             // remember a type that DOES have one
                + "   const payV=pickFirst(pay);"
                + "   const dsel=await waitOpts('UserDetailsModified.SubGroupID', 8000);"
                + "   if(dsel){ const v=pickFirst(dsel);"
                + "     return resolve('PayableType=\"'+norm(t.o.textContent)+'\" Payable=\"'+payV+'\" -> DepositReceiptType='+v); } }"
                // Nothing worked — RESTORE a usable combination so Payable is not left blank by this walk.
                // Selecting ONCE is not enough: restoring Payable Type re-fires its cascade, and the response that
                // lands AFTER the pick REPLACES the option list, dropping the selection again. Submit was then
                // rejected with "Please fill * mark fields!" naming EDPID — a field this walk itself had emptied.
                // So re-assert until BOTH the DOM selection and the bound model agree, the same read-back the
                // Registration visit cascade uses.
                + " pt.selectedIndex=startIdx; fire(pt);"
                + " const chosen=e=>{ const o=e&&e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                // Wait for the list with the SAME 8s budget the walk uses — a shorter poll gives up while the
                // cascade is still loading and leaves Payable blank, which is the very thing this restore exists
                // to prevent. Then re-assert until the selection survives the reload that lands after the pick.
                + " let payBackV='(none)', usedType=startType;"
                + " for(let k=0;k<3;k++){ const e=await waitOpts('UserDetailsModified.EDPID', 8000);"
                + "   if(!e) continue;"
                + "   if(!chosen(e)) pickFirst(e);"
                + "   await sleep(600);"
                + "   const e2=byNg('UserDetailsModified.EDPID'); const v=e2?chosen(e2):'';"
                + "   if(v){ payBackV=v; break; } }"
                // The original type's Payable list does not always come back after the walk — on DSH it stayed
                // empty however long we polled, so Submit failed on an EDPID this walk had emptied. The record does
                // not need THIS Payable Type; it needs A VALID PAIR. Fall back to a type the walk proved has one.
                + " if(payBackV==='(none)' && goodIdx>=0){"
                + "   pt.selectedIndex=goodIdx; fire(pt);"
                + "   const e3=await waitOpts('UserDetailsModified.EDPID', 10000);"
                + "   if(e3){ const v3=pickFirst(e3); await sleep(500);"
                + "     const e4=byNg('UserDetailsModified.EDPID'); const vv=e4?chosen(e4):v3;"
                + "     if(vv){ payBackV=vv; usedType=norm((pt.options[goodIdx]||{}).textContent||'')+'(fallback)'; } } }"
                + " startType=usedType;"
                + " resolve('no Payable Type yielded a Deposit/Receipt Type (tried '+types.length+', '+noPayable"
                + "   +' had no Payable) - restored PayableType=\"'+startType+'\" Payable=\"'+payBackV+'\"'); })",
                java.util.Map.of("walk", Boolean.getBoolean("deposit.walk")));
        String s = r == null ? "" : r.toString();
        // Keep the reported values in step with what is now on screen.
        Object cur = page.evaluate("() => { const norm=x=>(x||'').replace(/\\s+/g,' ').trim();"
                + " const g=ng=>{ const e=[...document.querySelectorAll('select')].find(y=>y.getAttribute('ng-model')===ng && y.offsetParent!==null);"
                + "   return e?norm((e.options[e.selectedIndex]||{}).textContent||''):''; };"
                + " return g('UserDetailsModified.PayableTypeID')+'|'+g('UserDetailsModified.EDPID')+'|'+g('UserDetailsModified.SubGroupID'); }");
        if (cur != null) {
            String[] p = cur.toString().split("\\|", -1);
            if (p.length == 3) {
                if (!p[0].isBlank()) lastPayableType = p[0];
                if (!p[1].isBlank()) lastPayable = p[1];
                if (!p[2].isBlank()) lastDepositType = p[2];
            }
        }
        System.out.println("ensureDepositReceiptType: " + s);
        waitForAngular(400);
        return s;
    }

    /**
     * List the STARRED (mandatory) fields that are still empty. The app rejects with a nameless
     * <i>"Please fill * mark fields!"</i>, so this turns that into the actual list instead of a guessing game.
     */
    public String emptyStarredFields() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p&&h++<4&&!t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t; };"
                + " const out=[];"
                + " document.querySelectorAll('input,select,textarea').forEach(e=>{ if(e.offsetParent===null) return;"
                + "   const lb=labelOf(e); if(!/\\*/.test(lb)) return;"                    // starred only
                + "   let empty;"
                + "   if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; empty=!o||!o.value||/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)); }"
                + "   else if(e.type==='checkbox'||e.type==='radio'){ return; }"
                + "   else { empty=!(e.value||'').trim(); }"
                + "   if(empty) out.push(lb.replace(/\\s*\\*+\\s*$/,'')+'['+(e.getAttribute('ng-model')||e.id||'?')+']'); });"
                + " return out.length? out.slice(0,15).join(', ') : '(none)'; }");
        String s = r == null ? "" : r.toString();
        System.out.println("emptyStarredFields: " + s);
        return s;
    }

    /**
     * Tick one right from the user-rights block — {@code //*[@id="UserDetailsTab"]/div[2]/div[2]/div[3]} (88
     * checkboxes). Real-clicked so its ng-change fires; the label of whatever was ticked is returned.
     */
    public String tickUserRight() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__usrRight').forEach(e=>e.removeAttribute('id'));"
                + " const node=document.evaluate('//*[@id=\"UserDetailsTab\"]/div[2]/div[2]/div[3]', document, null, 9, null).singleNodeValue"
                + "   || document.querySelector('#UserDetailsTab');"
                + " if(!node) return ''; const c=[...node.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked);"
                + " if(!c) return ''; c.id='__usrRight';"
                + " return norm((c.closest('label')||c.parentElement||{}).textContent||'') || (c.getAttribute('ng-model')||'right'); }");
        String label = tagged == null ? "" : tagged.toString();
        if (label.isEmpty()) { System.out.println("tickUserRight: no un-ticked checkbox found in the rights block"); return "(none found)"; }
        try { page.locator("#__usrRight").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000)); }
        catch (Exception e) { System.out.println("tickUserRight: click failed - " + e.getMessage()); }
        Object ok = page.evaluate("() => { const c=document.getElementById('__usrRight'); const v=c?c.checked:false; if(c) c.removeAttribute('id'); return v; }");
        lastRight = label;
        waitForAngular(500);
        return label + (Boolean.TRUE.equals(ok) ? " (ticked)" : " (NOT ticked)");
    }

    /**
     * Click the <b>Inventory Details</b> tab on the Add-User form (confirmed live: a plain {@code <a>} with
     * exactly that text — no trailing "1", despite the flow wording).
     */
    public String clickInventoryDetails1Tab() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__invTab').forEach(e=>e.removeAttribute('id'));"
                + " const t=[...document.querySelectorAll('a,li,button,[ng-click],[role=tab],span')].find(x=>x.offsetParent!==null && /^\\s*inventory\\s*details\\s*1?\\s*$/i.test(norm(x.textContent)));"
                + " if(!t) return false; t.id='__invTab'; return true; }"));
        if (!tagged) return "(Inventory Details tab not found) " + describeTabs();
        try { page.locator("#__invTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("clickInventoryDetails1Tab: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const t=document.getElementById('__invTab'); if(t) t.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const t=document.getElementById('__invTab'); if(t) t.removeAttribute('id'); }");
        waitForAngular(1500);
        return "Inventory Details1 tab clicked (" + currentScreen() + ")";
    }

    /** Every tab-like label visible on the Add-User form, PLUS any element anywhere containing "inventory" text
     *  (case-insensitive, whatever tag it is) — for diagnosing a miss. */
    public String describeTabs() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tabs=[...document.querySelectorAll('a,li,button,[role=tab]')].filter(x=>x.offsetParent!==null)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t && t.length<40).slice(0,40).join(' | ');"
                + " const inv=[...document.querySelectorAll('*')].filter(x=>/inventory/i.test((x.textContent||''))"
                + "   && ![...x.children].some(c=>/inventory/i.test(c.textContent||'')))"    // leaf-most match only
                + "   .map(x=>x.tagName+(x.className?('.'+String(x.className).split(' ')[0]):'')+' vis='+(x.offsetParent!==null)+' \"'+norm(x.textContent).slice(0,50)+'\"')"
                + "   .slice(0,20).join(' | ');"
                + " return 'TABS: '+tabs+'  ||  INVENTORY MATCHES: '+(inv||'(none found anywhere on the page)'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Add</b> again inside the Inventory Details tab (this tab's own inner Add — same
     * list-then-Add-reveals-the-form shape as several other DevHIS screens in this codebase).
     */
    public String clickAddInInventoryTab() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__invAdd').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__invAdd'; return true; }"));
        if (!tagged) return "(no Add button in Inventory Details tab)";
        try { page.locator("#__invAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("clickAddInInventoryTab: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const b=document.getElementById('__invAdd'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const b=document.getElementById('__invAdd'); if(b) b.removeAttribute('id'); }");
        waitForAngular(1500);
        return "Add clicked (" + currentScreen() + ")";
    }

    /**
     * Tick one un-ticked checkbox in EACH visible table on the currently active tab (real-clicked so any
     * ng-change fires). Returns a per-table summary.
     */
    public String tickCheckboxInEachTable() {
        Object countObj = page.evaluate("() => [...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null).length");
        int tableCount = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
        if (tableCount == 0) return "(no visible tables)";
        StringBuilder out = new StringBuilder();
        int ticked = 0;
        for (int i = 0; i < tableCount; i++) {
            // ROW checkboxes only (tbody) — a thead "select all" checkbox can toggle a different flag than the
            // per-row selection model the app's own "Please Select At least One Record!" validation actually reads.
            boolean tagged = Boolean.TRUE.equals(page.evaluate("(i) => { document.querySelectorAll('#__invCb').forEach(e=>e.removeAttribute('id'));"
                    + " const tables=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                    + " const t=tables[i]; if(!t) return false;"
                    + " const cb=[...t.querySelectorAll('tbody input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked && !x.disabled);"
                    + " if(!cb) return false; cb.id='__invCb'; return true; }", i));
            if (!tagged) { out.append("table").append(i).append(":(no checkbox) "); continue; }
            Object ngModel = page.evaluate("() => { const e=document.getElementById('__invCb'); return e?(e.getAttribute('ng-model')||'(no ng-model)'):'?'; }");
            try {
                page.locator("#__invCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000));
                Object after = page.evaluate("() => { const e=document.getElementById('__invCb'); if(!e) return 'gone';"
                        + " let mv='?'; try{ const c=angular.element(e).controller('ngModel'); mv=(c&&c.$modelValue!=null)?String(c.$modelValue):'(null)'; }catch(x){}"
                        + " return 'checked='+e.checked+' model='+mv; }");
                ticked++;
                out.append("table").append(i).append(":ticked[ng=").append(ngModel).append(" ").append(after).append("] ");
            } catch (Exception e) {
                out.append("table").append(i).append(":click-failed[ng=").append(ngModel).append("] ");
            }
            page.evaluate("() => { const e=document.getElementById('__invCb'); if(e) e.removeAttribute('id'); }");
            waitForAngular(300);
        }
        return "tables=" + tableCount + " ticked=" + ticked + " [" + out.toString().trim() + "]";
    }

    /**
     * Click the <b>Report Field Settings</b> tab on the Add-User form (discovered by its visible text — the
     * exact DOM shape was not yet confirmed live).
     */
    public String clickReportFieldSettingsTab() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__rfsTab').forEach(e=>e.removeAttribute('id'));"
                + " const t=[...document.querySelectorAll('a,li,button,[ng-click],[role=tab],span')].find(x=>x.offsetParent!==null && /^\\s*report\\s*field\\s*settings\\s*$/i.test(norm(x.textContent)));"
                + " if(!t) return false; t.id='__rfsTab'; return true; }"));
        if (!tagged) return "(Report Field Settings tab not found) " + describeTabs();
        try { page.locator("#__rfsTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("clickReportFieldSettingsTab: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const t=document.getElementById('__rfsTab'); if(t) t.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const t=document.getElementById('__rfsTab'); if(t) t.removeAttribute('id'); }");
        waitForAngular(1500);
        return "Report Field Settings tab clicked (" + currentScreen() + ")";
    }

    /**
     * Click the <b>Integration Access Control</b> tab on the Add-User form (discovered by its visible text —
     * the exact DOM shape was not yet confirmed live).
     */
    public String clickIntegrationAccessControlTab() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__iacTab').forEach(e=>e.removeAttribute('id'));"
                + " const t=[...document.querySelectorAll('a,li,button,[ng-click],[role=tab],span')].find(x=>x.offsetParent!==null && /^\\s*integration\\s*access\\s*control\\s*$/i.test(norm(x.textContent)));"
                + " if(!t) return false; t.id='__iacTab'; return true; }"));
        if (!tagged) return "(Integration Access Control tab not found) " + describeTabs();
        try { page.locator("#__iacTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("clickIntegrationAccessControlTab: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const t=document.getElementById('__iacTab'); if(t) t.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const t=document.getElementById('__iacTab'); if(t) t.removeAttribute('id'); }");
        waitForAngular(1500);
        return "Integration Access Control tab clicked (" + currentScreen() + ")";
    }

    /**
     * For EACH given table name (matched against a nearby caption/header/panel-title), tick one un-ticked
     * checkbox in its table. A table with NO data rows is reported as such (the caller should FAIL on that,
     * per the flow's own "fail the step if no data in any table" requirement) rather than silently skipped.
     *
     * @return one line per name: {@code "<name>: ticked"} / {@code "<name>: (no data)"} / {@code "<name>: (table not found)"}
     */
    public String tickCheckboxInNamedTables(String[] names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            Object res = page.evaluate("(name) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " document.querySelectorAll('#__rfsCb').forEach(e=>e.removeAttribute('id'));"
                    + " const target=name.toLowerCase().trim();"
                    + " const heads=[...document.querySelectorAll('*')].filter(h=>h.offsetParent!==null"
                    + "   && norm(h.textContent).toLowerCase()===target"
                    + "   && !(h.children.length && [...h.children].some(c=>norm(c.textContent).toLowerCase()===target)));"
                    + " for (const h of heads) {"
                    + "   let node = h, table = null;"
                    + "   for (let hop=0; hop<10 && node && !table; hop++) {"
                    + "     if (node.tagName==='TABLE' && node.offsetParent!==null) { table = node; break; }"
                    + "     if (node.querySelector) { const t=node.querySelector('table'); if (t && t.offsetParent!==null) { table=t; break; } }"
                    + "     node = node.nextElementSibling || (node.parentElement ? node.parentElement.nextElementSibling : null) || node.parentElement;"
                    + "   }"
                    + "   if (!table) continue;"
                    + "   const rows=[...table.querySelectorAll('tbody tr')].filter(r=>{ const tx=norm(r.textContent); return tx && !/no (records|data)/i.test(tx); });"
                    + "   if (!rows.length) return {found:true, rows:0};"
                    + "   const cb=[...table.querySelectorAll('tbody input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked && !x.disabled);"
                    + "   if (!cb) return {found:true, rows:rows.length, noCheckbox:true};"
                    + "   cb.id='__rfsCb'; return {found:true, rows:rows.length, tagged:true};"
                    + " }"
                    + " return {found:false}; }", name);
            java.util.Map<?, ?> m = (res instanceof java.util.Map) ? (java.util.Map<?, ?>) res : java.util.Collections.emptyMap();
            if (!Boolean.TRUE.equals(m.get("found"))) { out.append(name).append(": (table not found)\n"); continue; }
            Object rowsObj = m.get("rows");
            int rows = rowsObj instanceof Number ? ((Number) rowsObj).intValue() : -1;
            if (rows == 0) { out.append(name).append(": (no data)\n"); continue; }
            if (!Boolean.TRUE.equals(m.get("tagged"))) { out.append(name).append(": ").append(rows).append(" row(s) but no checkbox found\n"); continue; }
            try {
                page.locator("#__rfsCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000));
                out.append(name).append(": ticked (").append(rows).append(" row(s))\n");
            } catch (Exception e) {
                out.append(name).append(": click-failed (").append(rows).append(" row(s))\n");
            }
            page.evaluate("() => { const e=document.getElementById('__rfsCb'); if(e) e.removeAttribute('id'); }");
            waitForAngular(300);
        }
        return out.toString().trim();
    }

    /** Click <b>Submit</b> ({@code FnIUDUserDetails}) and return the toast. */
    /**
     * Re-assert <b>Payable</b> ({@code UserDetailsModified.EDPID}) immediately before Save.
     *
     * <p>Selecting Payable Type kicks off an ASYNC reload of the Payable list that lands AFTER the pick and wipes
     * the selection — so Payable was filled in step 4, looked right, and was EMPTY by the time Submit ran. The app
     * then rejected with the nameless "Please fill * mark fields!". Same class of race as the Registration
     * Queue No / Visit Type re-assert.</p>
     */
    /**
     * Select Payable with a REAL Playwright selection, the way a person would.
     *
     * <p>Diagnostics proved the JS route cannot work here: there is exactly ONE element bound to
     * {@code UserDetailsModified.EDPID} — a plain, visible, enabled {@code <select>} carrying 259 real options —
     * yet its model stayed {@code null} through {@code selectedIndex}, {@code $setViewValue}, {@code change} and
     * {@code triggerHandler}, and the control kept the {@code ng-untouched} class, i.e. Angular never saw an
     * interaction at all. This screen behaves like the others that need a real click for {@code ng-change} to fire.
     * {@code selectOption} issues a trusted browser event, so Angular's own select directive does the binding.</p>
     *
     * @return what was selected, or "" when a real selection was not possible
     */
    private String selectPayableForReal() {
        Object opt = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__edpidSel').forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.EDPID' && x.offsetParent!==null);"
                + " if(!e) return ''; const o=[...e.options].find(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(!o) return ''; e.id='__edpidSel'; return o.value; }");
        String value = opt == null ? "" : opt.toString();
        if (value.isEmpty()) return "";
        try {
            page.locator("#__edpidSel").selectOption(new com.microsoft.playwright.options.SelectOption().setValue(value),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("selectPayableForReal: selectOption failed - " + e.getMessage());
        }
        waitForAngular(900);
        Object back = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=document.getElementById('__edpidSel'); if(!e) return '';"
                + " let mv=''; try{ const c=angular.element(e).controller('ngModel'); mv=(c&&c.$modelValue!=null)?String(c.$modelValue):''; }catch(x){}"
                + " const t=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " e.removeAttribute('id'); return mv ? (t+' (model='+mv+')') : ''; }");
        return back == null ? "" : back.toString();
    }

    /** The Payable currently COMMITTED (text + model), or "" when the model is empty however it renders. */
    private String payableCurrent() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.EDPID' && x.offsetParent!==null);"
                + " if(!e) return ''; let mv=''; try{ const c=angular.element(e).controller('ngModel');"
                + "   mv=(c && c.$modelValue!=null)?String(c.$modelValue):''; }catch(x){}"
                + " const t=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " if(!mv || /^-*\\s*select\\s*-*$/i.test(t)) return ''; return t; }");
        return r == null ? "" : r.toString();
    }

    /** How many REAL options the Payable list currently offers (-1 when the field is absent). */
    private int payableOptionCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.EDPID' && x.offsetParent!==null);"
                + " if(!e) return -1; return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))).length; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /**
     * Change <b>Payable Type</b> until its <b>Payable</b> list yields a selection that commits.
     *
     * <p>Payable is a cascade off Payable Type, so when Payable cannot be set the type is what to change — either
     * because the list is genuinely empty for the current type, or because the list on screen has gone stale and
     * will not bind. Each candidate type is chosen with a REAL selection, its cascade waited for, and a Payable
     * then attempted; the first type that produces a committed Payable wins.</p>
     */
    private String changePayableTypeUntilPayableSelects() {
        Object list = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.PayableTypeID' && x.offsetParent!==null);"
                + " if(!e) return []; const cur=e.value;"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && o.value!==cur)"
                + "   .map(o=>o.value+'\\u0001'+norm(o.textContent)); }");
        java.util.List<?> types = (list instanceof java.util.List) ? (java.util.List<?>) list : java.util.Collections.emptyList();
        if (types.isEmpty()) return "";

        for (Object t : types) {
            String[] parts = String.valueOf(t).split("", 2);
            String value = parts[0], text = parts.length > 1 ? parts[1] : parts[0];
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__ptSel').forEach(e=>e.removeAttribute('id'));"
                    + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.PayableTypeID' && x.offsetParent!==null);"
                    + " if(!e) return false; e.id='__ptSel'; return true; }");
            if (!Boolean.TRUE.equals(tagged)) break;
            try {
                page.locator("#__ptSel").selectOption(new com.microsoft.playwright.options.SelectOption().setValue(value),
                        new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(8000));
            } catch (Exception e) { System.out.println("changePayableType: selectOption failed for \"" + text + "\" - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ptSel'); if(e) e.removeAttribute('id'); }");

            // Wait for the dependent Payable list to load before judging it.
            int opts = -1;
            for (int k = 0; k < 16; k++) { waitForAngular(500); opts = payableOptionCount(); if (opts > 0) break; }
            if (opts <= 0) { System.out.println("changePayableType: \"" + text + "\" offers no Payable — next"); continue; }

            String picked = selectPayableForReal();
            if (!picked.isEmpty()) {
                lastPayableType = text;
                lastPayable = picked.replaceAll("\\s*\\(model=.*$", "").trim();
                return "changed Payable Type to \"" + text + "\" -> Payable=" + picked;
            }
            System.out.println("changePayableType: \"" + text + "\" has " + opts + " Payables but none committed — next");
        }
        return "";
    }

    public String ensurePayableSelected() {
        // 1. Already bound? Judge on the MODEL, not the visible text — this control has been observed displaying a
        //    value while its model was null, and it is the model the save reads.
        String already = payableCurrent();
        if (!already.isEmpty()) { System.out.println("ensurePayableSelected: already set -> " + already); return "already set: " + already; }

        // 2. Select from the list as it stands, with a REAL selection (synthetic events do not bind here).
        String real = selectPayableForReal();
        if (!real.isEmpty()) {
            System.out.println("ensurePayableSelected: real selectOption -> " + real);
            return "re-selected (real): " + real;
        }

        // 3. Nothing selectable — the list is empty, or it holds options that refuse to bind. Either way the Payable
        //    Type is the thing to change: it OWNS this list, and a freshly loaded list binds where a stale one does
        //    not. Walk the types until one produces a Payable that actually commits.
        String walked = changePayableTypeUntilPayableSelects();
        if (!walked.isEmpty()) { System.out.println("ensurePayableSelected: " + walked); return walked; }
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(x=>setTimeout(x,ms));"
                + " const e0=()=>[...document.querySelectorAll('select')].find(e=>e.getAttribute('ng-model')==='UserDetailsModified.EDPID' && e.offsetParent!==null);"
                + " let e=e0(); if(!e) return resolve('(no Payable field)');"
                + " const cur=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return resolve('already set: '+cur);"
                // Payable hangs off Payable Type. This screen has already shown that a control can DISPLAY a value
                // while its model is null — if PayableTypeID is in that state, the app's cascade keeps resetting
                // EDPID to null no matter how we write it, which is why both selectedIndex and $setViewValue were
                // reverted. So repair the PARENT first, then let its cascade repopulate Payable.
                + " const ptEl=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.PayableTypeID' && x.offsetParent!==null);"
                + " if(ptEl){ let ptModel=''; try{ const pc=A.element(ptEl).controller('ngModel');"
                + "     ptModel=(pc && pc.$modelValue!=null)?String(pc.$modelValue):''; }catch(x){}"
                + "   if(!ptModel){ const prs=[...ptEl.options].map((o,i)=>({o,i}))"
                + "       .filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)));"
                + "     if(prs.length){ const keep=ptEl.selectedIndex>0?ptEl.selectedIndex:prs[0].i;"
                + "       ptEl.selectedIndex=keep; ptEl.value=ptEl.options[keep].value;"
                + "       try{ const pc2=A.element(ptEl).controller('ngModel'); if(pc2){ pc2.$setViewValue(ptEl.value); pc2.$render(); } }catch(x){}"
                + "       ptEl.dispatchEvent(new Event('change',{bubbles:true}));"
                + "       try{A.element(ptEl).triggerHandler('change');}catch(x){} if($){try{$(ptEl).trigger('change');}catch(x){}}"
                + "       await sleep(1500); } } }"
                // Set through the ngModel CONTROLLER, not just selectedIndex. The list is not empty here — the
                // diagnostics show 259 real options with a NULL model — and assigning selectedIndex alone let
                // Angular re-render from that null model and snap the control straight back to "--Select--",
                // which is why 20 attempts all appeared to fail. $setViewValue writes the model itself.
                + " for(let k=0;k<20;k++){ e=e0(); if(e){ const rs=[...e.options].map((o,i)=>({o,i}))"
                + "     .filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)));"
                + "   if(rs.length){ const pick=rs[0];"
                + "     e.value=pick.o.value; e.selectedIndex=pick.i;"
                + "     try{ const c=A.element(e).controller('ngModel'); if(c){ c.$setViewValue(e.value); c.$render(); } }catch(x){}"
                + "     e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "     await sleep(500); const e2=e0();"
                + "     const v=e2?norm((e2.options[e2.selectedIndex]||{}).textContent||''):'';"
                + "     let mv=''; if(e2){ try{ const c2=A.element(e2).controller('ngModel'); mv=(c2&&c2.$modelValue!=null)?String(c2.$modelValue):''; }catch(x){} }"
                + "     if(v && !/^-*\\s*select\\s*-*$/i.test(v) && mv) return resolve('re-selected: '+v+' (model='+mv+')'); } }"
                + "   await sleep(400); }"
                // Say WHY it could not: is the Payable Type still set, and does the Payable list have any options?
                // "could not select a Payable" alone sent me chasing the Deposit/Receipt walk for three rounds when
                // the selection is lost even with that walk disabled.
                + " const pt=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='UserDetailsModified.PayableTypeID');"
                + " const ptTxt=pt?norm((pt.options[pt.selectedIndex]||{}).textContent||''):'(no field)';"
                + " let ptMv='?'; if(pt){ try{ const pc=A.element(pt).controller('ngModel');"
                + "   ptMv=(pc && pc.$modelValue!=null)?String(pc.$modelValue):'(null)'; }catch(x){} }"
                + " const eF=e0(); const optN=eF?eF.options.length:-1;"
                + " const realN=eF?[...eF.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))).length:-1;"
                + " let mv='?'; if(eF){ try{ const c=A.element(eF).controller('ngModel'); mv=(c&&c.$modelValue!=null)?String(c.$modelValue):'(null)'; }catch(x){} }"
                // Inventory EVERY element bound to EDPID. A select that holds 259 options yet will not bind is the
                // signature of writing to the wrong one — this app uses select2 elsewhere, where the real <select>
                // is hidden offscreen and a rendered span stands in for it, and a hidden duplicate would explain
                // every failed write so far.
                + " const all=[...document.querySelectorAll(\"[ng-model='UserDetailsModified.EDPID']\")].map((x,i)=>"
                + "   i+':'+x.tagName+' vis='+(x.offsetParent!==null)+' cls='+((x.className||'').toString().slice(0,40))"
                + "   +' opts='+(x.options?x.options.length:-1)+' disabled='+!!x.disabled).join(' ;; ');"
                + " resolve('could not select a Payable [PayableType='+ptTxt+' ptModel='+ptMv+' || EDPID elements: '+all+' || chosen: present='+(!!eF)"
                + "   +' options='+optN+' real='+realN+' model='+mv+']'); })");
        String s = r == null ? "" : r.toString();
        System.out.println("ensurePayableSelected: " + s);
        waitForAngular(400);
        return s;
    }

    /** A fresh, unique Login ID. Epoch millis (unique per run) plus a random tail (unique within a run). */
    private static String newLoginId() {
        return "QAUSER" + Long.toString(System.currentTimeMillis() % 100000000L)
                + String.format("%03d", (int) (Math.random() * 1000));
    }

    /**
     * The app says this user already exists — either the login name is taken, or an account already exists for the
     * selected Payable ("User account is already created for Agent Test !"). Both mean: try again under a new name.
     */
    private static boolean userNameTaken(String toast) {
        String t = toast == null ? "" : toast.toLowerCase();
        return (t.contains("exist") && (t.contains("user") || t.contains("login")))
                || t.contains("already created");
    }

    /** Write a new Login ID into the form; true when it landed. */
    private boolean setLoginId(String value) {
        Object r = page.evaluate("([ng,v]) => { const e=[...document.querySelectorAll('input')]"
                + " .find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return false;"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return (e.value||'')===v; }",
                java.util.Arrays.asList("UserDetailsModified.LoginName", value));
        return Boolean.TRUE.equals(r);
    }

    /**
     * Submit, and RETRY WITH A NEW LOGIN ID when the app answers that the user name already exists.
     *
     * <p>The generated id can collide with an account left behind by an earlier run, and the save is then rejected
     * for a reason that has nothing to do with what the flow is testing. Rather than fail the run on a name clash,
     * mint a fresh id and submit again (up to 3 attempts); any other message is returned unchanged.</p>
     */
    public String submitAndGetToast() {
        // Payable can be cleared by its own async reload after step 4 — re-assert it before the save.
        ensurePayableSelected();
        String toast = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            toast = clickSubmitAndReadToast();
            // Judge on ALL the toasts: the app pairs "saved successfully" with "already created for <Payable>",
            // and the reported toast is the success one, which would hide the rejection from this check.
            if (!userNameTaken(lastToasts.isEmpty() ? toast : lastToasts)) return toast;
            String taken = lastLoginId;
            lastLoginId = newLoginId();
            boolean set = setLoginId(lastLoginId);
            System.out.println("submitAndGetToast: \"" + toast + "\" — Login ID \"" + taken
                    + "\" is taken; retrying as \"" + lastLoginId + "\" (set=" + set + ")");
            if (!set) return toast;                    // cannot change it — report the app's message as-is
            waitForAngular(800);
        }
        return toast;
    }

    /** One Submit click: fire it, clear the confirm, and return the toast the app raised. */
    private String clickSubmitAndReadToast() {
        page.evaluate("() => { window.__usrToasts=[]; if(window.__usrObs) window.__usrObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__usrToasts.includes(t)) window.__usrToasts.push(t); }); };"
                + " window.__usrObs=new MutationObserver(grab); window.__usrObs.observe(document.body,{childList:true,subtree:true}); }");
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__usrSubmit').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/FnIUDUserDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return ''; b.id='__usrSubmit'; return b.getAttribute('ng-click')||'submit'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        System.out.println("UserPage submit => " + how);
        try { page.locator("#__usrSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__usrSubmit'); if(e) e.removeAttribute('id'); }");
        // Some saves raise a confirm first.
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].some(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].find(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''));"
                    + " const b=[...(box||document).querySelectorAll('button,a')].find(x=>/^(yes|ok|save|submit)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(800);
        }
        try {
            page.waitForFunction("() => (window.__usrToasts||[]).some(a=>/saved|success|added|updated|please|enter|select|required|exist|error|already/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        // Keep EVERY toast, not just the one reported. The app can raise a success AND a rejection together
        // ("User Details saved successfully." + "User account is already created for Agent Test !"), and this
        // returns the success one — so a retry that inspected only the return value would never see the problem.
        Object all = page.evaluate("() => (window.__usrToasts||[]).join(' | ')");
        lastToasts = all == null ? "" : all.toString();
        Object r = page.evaluate("() => { const a=window.__usrToasts||[]; return a.find(x=>/saved|added|success|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Every toast raised by the last Submit, joined — a save can raise a success and a rejection together. */
    public String lastToasts = "";
}
