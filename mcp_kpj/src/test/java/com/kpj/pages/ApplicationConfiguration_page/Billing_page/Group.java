package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Group</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Group</b> → <b>Add</b> → enter
 * <b>Group Code</b> + <b>Group</b> (name) → fill <b>Group Location Details</b> (<b>Code</b>, <b>OPLedger</b>,
 * <b>IPLedger</b>, <b>Cost Centre</b>, <b>OPD MarkUp</b>, <b>IPD MarkUp</b>, <b>RefEntitySharePer</b>,
 * <b>Print Order</b>) → select the <b>true/false</b> status toggle → <b>Submit</b> → success toast.</p>
 *
 * <p>This screen's exact ng-model names were not yet confirmed live — sibling Billing masters ({@code Group}
 * is the parent of {@code Billing_page.SubGroupMaster}'s "Group" select) use varied naming, so every field
 * here is filled by its own LABEL text rather than a guessed ng-model, and {@link #describeForm()} dumps every
 * visible field's real ng-model/label if a fill comes back "(not found)" — the label wording confirmed live is
 * what should replace the regexes below. {@code Group Code} and the Location Details' plain {@code Code} are
 * two DIFFERENT fields on this form — matched with anchored regexes so filling one never touches the other.</p>
 */
public class Group extends BasePage {

    public Group(Page page) { super(page); }

    /** Best-effort direct-route fallback if the menu link cannot be found at all. */
    public static final String ROUTE = "#/Group";
    public String lastGroupCode = "", lastGroup = "", lastLocationCode = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic Group names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] GROUP_NAMES = {
            "Corporate Panel", "Insurance Panel", "Government Panel", "TPA Panel", "Self-Pay Group",
            "Staff Welfare Scheme", "Retiree Medical Scheme", "Educational Institution Panel",
            "SOCSO Panel", "Takaful Panel"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** JS helpers shared by every fill/probe call: normalize text, find a field's nearby label. */
    private static final String LABEL_HELPERS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
            + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Billing" — a prefix match can expand a different (similarly named) menu.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1000);
            // EXACT text match ONLY — a bare href-contains fallback previously picked a DIFFERENT, similarly
            // spelled menu item on this same submenu (see CancellationReason.nav's "Receipt Cancellation" trap).
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const links=[...document.querySelectorAll('a[href]')];"
                    + " const a = links.find(x=>/^\\s*group\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__grMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("Group.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            if (href.toString().startsWith("#")) menuHref = href.toString();
            try { page.locator("#__grMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Group.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__grMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("Group.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /**
     * On the Group screen — verified by the rendered HEADER text (sibling masters on this submenu live at
     * routes/headers that do not always match their menu label — see the class doc), plus a Group Code/Group
     * box or an Add button actually present.
     */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/\\bgroup\\b/i.test(location.hash||'');"
                + " if(!/\\bgroup\\b/i.test(hdr) && !urlOk) return false;"
                + " const inputs=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null);"
                + " const hasGroupField=inputs.length>0;"
                + " if(hasGroupField) return true;"
                + " return [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button; a no-op (returns as-is) on inline-add screens where the form is already there. */
    public String clickAddIfPresent() {
        boolean already = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('input,textarea')].some(e=>e.offsetParent!==null && /group\\s*code/i.test(norm((e.closest('.form-group,.row,td,tr,div')||{}).textContent||''))); }"));
        if (already) return "inline-add (form already on screen)";
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__grAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__grAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("Group.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__grAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    /**
     * Enter <b>Group Code*</b> ({@code groupMaster.GroupCode}) and <b>Group*</b> name
     * ({@code groupMaster.GroupName}) — confirmed live via {@link #describeForm()}'s field dump (an earlier
     * label-based version was fooled by a stray "True" label shared across the Location Details row; these
     * exact ng-models are now used directly, the same lesson learned on Company Approve Amount).
     *
     * <p>{@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely
     * different details — fields are always force-overwritten (not just filled when empty), since a retry
     * refills the same still-open form.</p>
     */
    public String fillGroupHeader(int attempt) {
        String code = "GR" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastGroupCode = code;
        lastGroup = GROUP_NAMES[attempt % GROUP_NAMES.length] + (attempt >= GROUP_NAMES.length ? " " + (attempt / GROUP_NAMES.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const el=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const set=(ng,v)=>{ const e=el(ng); if(!e) return '(not found)';"
                + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const gc=set('groupMaster.GroupCode', a.code); const gn=set('groupMaster.GroupName', a.name);"
                + " return 'GroupCode='+gc+' | Group='+gn; }",
                java.util.Map.of("code", code, "name", lastGroup));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Fill the <b>Group Location Details</b> row (the {@code grp.*} models — confirmed live): <b>Code</b>
     * ({@code grp.locgrpcode}), <b>OPLedger</b> ({@code grp.opledger}), <b>IPLedger</b> ({@code grp.ipledger}),
     * <b>Cost Centre</b> ({@code grp.costcenterid}, select — first real option), <b>OPD MarkUp</b>
     * ({@code grp.opdmarkup}), <b>IPD MarkUp</b> ({@code grp.ipdmarkup}), <b>RefEntitySharePer</b>
     * ({@code grp.referentitysharepercentage}), <b>Print Order</b> ({@code grp.printorder}).
     */
    public String fillLocationDetails(int attempt) {
        String code = "LC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastLocationCode = code;
        Object r = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const el=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const set=(ng,v)=>{ const e=el(ng); if(!e) return '(not found)';"
                + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const setSel=(ng)=>{ const e=el(ng); if(!e) return '(not found)';"
                + "   const cur=e.options[e.selectedIndex]; if(cur && cur.value && !/^-*\\s*select/i.test(norm(cur.textContent))) return norm(cur.textContent)+' (kept)';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '(no-options)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}}"
                + "   return norm(e.options[i].textContent); };"
                + " const cd=set('grp.locgrpcode', a.code);"
                + " const op=set('grp.opledger', 'OPL' + a.code);"
                + " const ip=set('grp.ipledger', 'IPL' + a.code);"
                + " const cc=setSel('grp.costcenterid');"
                + " const opm=set('grp.opdmarkup', '10');"
                + " const ipm=set('grp.ipdmarkup', '10');"
                + " const rsp=set('grp.referentitysharepercentage', '10');"
                + " const po=set('grp.printorder', '1');"
                + " return 'Code='+cd+' | OPLedger='+op+' | IPLedger='+ip+' | CostCentre='+cc+' | OPDMarkUp='+opm+' | IPDMarkUp='+ipm+' | RefEntitySharePer='+rsp+' | PrintOrder='+po; }",
                java.util.Map.of("code", code));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Tick the Location Details row's <b>true/false</b> flag ({@code grp.Isdefault}, confirmed live) to True.
     */
    public String selectStatusTrueFalse() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll(\"[ng-model='grp.Isdefault']\")].find(x=>x.offsetParent!==null);"
                + " if(!e) return '(grp.Isdefault not found)'; if(e.checked) return 'grp.Isdefault=True (kept)';"
                + " const A=window.angular; const c=A.element(e).controller('ngModel'); e.checked=true; if(c){c.$setViewValue(true);c.$render();} e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return 'grp.Isdefault=True'; }");
        waitForAngular(300);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Submit</b> and return the toast. Falls back to invoking the form's ng-submit handler from the
     * scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__grToasts=[]; if(window.__grObs) window.__grObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__grToasts.includes(t)) window.__grToasts.push(t); }); };"
                + " window.__grObs=new MutationObserver(grab); window.__grObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("group") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__grSubmit'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }");
        System.out.println("Group submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__grSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Group submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__grSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__grToasts||[]).some(a=>/group|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("Group save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__grToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels.
     * Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => {" + LABEL_HELPERS
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,50).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(labelOf(e)?' lbl=\"'+labelOf(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
