package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Drug Instruction</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Drug Instruction</b> (list) →
 * <b>Add</b> → type 2 digits in the code look-up and pick a suggestion (which auto-fills its description) →
 * enter the <b>Code</b> + <b>Description</b> → inner <b>Add</b> (adds the detail row) → <b>Submit</b> → toast.</p>
 *
 * <p>Field ng-models are discovered by {@link #describeForm()} rather than assumed — labels on these screens are
 * mis-associated (several fields report the same label text), so guessing from labels crosses the wires.</p>
 *
 * <p>Menu note: the submenu parent is <b>Nursing</b> — matched on EXACT text, because "Nursing Station" is a
 * separate top-level module that a prefix match hits first.</p>
 */
public class DrugInstruction extends BasePage {

    public DrugInstruction(Page page) { super(page); }

    public static final String ROUTE = "#/DrugInstruction";
    public String lastCode = "", lastDescription = "", lastLookupCode = "", lastLookupDescription = "";
    private String menuHref = "";
    public byte[] toastPng;
    /** What the save call actually returned over HTTP (status + URL + body) — for diagnosing a bare "Error!". */
    public String lastSaveHttp = "";

    /** Descriptions that read like genuine configuration rather than machine noise. */
    private static final String[] DESCRIPTIONS = {
            "Take After Meals", "Take Before Meals", "Take With Water",
            "Apply To Affected Area", "Take At Bedtime", "Do Not Crush Or Chew"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*drug\\s*instruction\\s*$/i.test(norm(x.textContent)) || /druginstruction/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__diMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("DrugInstruction.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            if (href.toString().startsWith("#")) menuHref = href.toString();
            try { page.locator("#__diMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DrugInstruction.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__diMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("DrugInstruction.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Drug Instruction list/route. */
    public boolean onScreen() {
        return page.url().toLowerCase().contains("druginstruction");
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** Real-click the <b>Add</b> button on the list screen (polls). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__diAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DrugInstruction.clickAdd: Add button not found"); return false; }
        try { page.locator("#__diAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("DrugInstruction.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__diAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return true;
    }

    // ---- form ------------------------------------------------------------
    // Established by the probe — the add form is just TWO fields and a Save button:
    //   DrugInstruction.code        label "Code*"
    //   DrugInstruction.description label "Drug Instruction*"
    // There is NO diagnosis-code look-up, NO inner Add and NO Submit button on this screen.

    /**
     * Enter <b>Code*</b> ({@code DrugInstruction.code}) and <b>Drug Instruction*</b>
     * ({@code DrugInstruction.description}). {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "DI" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastDescription = DESCRIPTIONS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % DESCRIPTIONS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('DrugInstruction.code', a.code); const de=set('DrugInstruction.description', a.desc);"
                + " return 'Code='+cd+' | DrugInstruction='+de; }",
                java.util.Map.of("code", code, "desc", lastDescription));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Save</b> (this screen's commit button — there is no Submit) and return the toast. Re-asserts the
     * Code / Description first, since the form can re-render between fill and click.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__diToasts=[]; if(window.__diObs) window.__diObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__diToasts.includes(t)) window.__diToasts.push(t); }); };"
                + " window.__diObs=new MutationObserver(grab); window.__diObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('DrugInstruction.code', a.code); set('DrugInstruction.description', a.desc); }",
                java.util.Map.of("code", lastCode, "desc", lastDescription));
        waitForAngular(400);

        // The commit button is "Save" here; accept "Submit" too so the same code survives a relabel.
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__diSave'; return true; }"));
        if (!tagged) { System.out.println("submitAndGetToast: Save button not found"); return ""; }

        // Record what the save call ACTUALLY returned — a bare "Error!" toast hides whether the request 500'd or
        // came back 200 with a failure payload, and those need very different fixes.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url();
                boolean interesting = u.toLowerCase().contains("druginstruction") || resp.status() >= 400;
                if (!interesting) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + u + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        try { page.locator("#__diSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__diSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__diToasts||[]).some(a=>/drug|instruction|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("DrugInstruction save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__diToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — what the app expects at Save time: the {@code DrugInstruction} scope object as the app sees it, the
     * Save button's handler, the form's ng-submit, and EVERY field including hidden/required ones. A bare "Error!"
     * toast on these screens usually means a required input the visible form never showed.
     */
    public String describeState() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " let model=null; const seen=new Set();"
                + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                + "   if(s.DrugInstruction && typeof s.DrugInstruction==='object' && !model) model=s.DrugInstruction; }catch(e){} });"
                + " let modelStr='(no DrugInstruction on any scope)';"
                + " if(model){ try{ modelStr=JSON.stringify(model); }catch(e){ modelStr='(unserialisable)'; } }"
                + " const saveBtn=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const all=[...document.querySelectorAll('input,select,textarea')].map(e=>"
                + "   (e.offsetParent===null?'HIDDEN ':'')+e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.id||e.name||'?')+']'"
                + "   +(e.hasAttribute('required')?' REQUIRED':'')+(e.value?' val=\"'+String(e.value).slice(0,20)+'\"':'')"
                + "   +(e.tagName.toLowerCase()==='select'?' opts='+e.options.length:'')).slice(0,40);"
                + " return 'MODEL: '+modelStr"
                + "   +'  || SAVE ng-click: '+(saveBtn?(saveBtn.getAttribute('ng-click')||'(none)'):'(no save button)')"
                + "   +'  || FORM ng-submit: '+(f?(f.getAttribute('ng-submit')||'(none)'):'(no form)')"
                + "   +'  || ALL FIELDS: '+all.join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * PROBE — the visible field ng-models (with tag, type, placeholder and nearby label) plus the visible button
     * labels. This is how the real field names get established instead of guessed.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.type&&e.tagName.toLowerCase()==='input'?':'+e.type:'')"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')"
                + "   +(lbl(e)?' lbl=\"'+lbl(e)+'\"':'')"
                + "   +(e.tagName.toLowerCase()==='select'?' opts='+e.options.length:''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
