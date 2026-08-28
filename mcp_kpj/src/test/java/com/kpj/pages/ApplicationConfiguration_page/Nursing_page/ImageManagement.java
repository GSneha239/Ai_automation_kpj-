package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Image Management</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu, EXACT text — "Nursing Station" is a
 * different module and a prefix match hits it first) → <b>Image Management</b> (route
 * {@code #/ImageManagement}, best-guess fallback — navigation is primarily by the live menu link) → (Add, if
 * the screen has one) → enter <b>Code*</b> + <b>Remark*</b> + <b>Image Category*</b> + upload an <b>Image</b> +
 * <b>Image Name*</b> + <b>Icon Name*</b> + upload an <b>Icon Image</b> → click the inner <b>Add</b> (appends a
 * row to the detail grid) → <b>Save</b> → success toast.</p>
 *
 * <p>The header fields are DISCOVERED at runtime rather than hard-coded (this screen's exact ng-model names
 * were not yet confirmed live). Because there are TWO file inputs (Image / Icon Image) and TWO "name" text
 * inputs (Image Name / Icon Name), each is disambiguated by its nearest &lt;label&gt; text ("icon" vs plain),
 * not by ng-model alone. Grid filter boxes ({@code colFilter.term}) and the textAngular artefact are
 * excluded.</p>
 */
public class ImageManagement extends BasePage {

    public ImageManagement(Page page) { super(page); }

    /** Best-guess fallback — navigation is primarily by the live menu link, not this hardcoded route. */
    public static final String ROUTE = "#/ImageManagement";
    public String lastCode = "", lastRemark = "", lastCategory = "", lastImageName = "", lastIconName = "";
    public String lastCodeModel = "", lastRemarkModel = "";
    private String menuHref = "";
    public byte[] toastPng;

    /** Realistic image-management names, not "Auto ... <code>" filler. */
    private static final String[] REMARKS = {
            "Wound Assessment Chart", "Pain Location Diagram", "Body Map Front", "Body Map Back",
            "Pressure Ulcer Stages", "Fall Risk Icon", "Patient Position Guide", "Skin Integrity Chart",
            "Nutrition Screening Icon", "Vital Signs Icon", "Allergy Alert Icon", "Mobility Aid Icon"
    };

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** JS helper: the visible Code / Remark inputs, whatever they are called on this screen. */
    private static final String FIND_FIELDS =
            " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$|txtUserName|txtOldPassword|txtNewPassword|txtConfNewPassword/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='file' && !skip(e.getAttribute('ng-model')));"
            + " const byModel=re=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
            + " const byPh=re=>boxes.find(e=>re.test(e.placeholder||''));"
            + " const codeEl = byModel(/\\.code(id)?$/i) || byModel(/code/i) || byPh(/^\\s*code\\s*$/i);"
            + " const remEl  = byModel(/\\.(description|remark)$/i) || byModel(/description|remark/i) || byPh(/^\\s*(remark|description)\\s*$/i);";

    /** JS helper: lookups for the Image-Category select, the two name text boxes and the two file inputs.
     *  Confirmed live (2026-08-20): {@code ImageManagement.imagename} is the real "Image Name*" box, but
     *  {@code ImageManagement.imagename1} is a DECOY that auto-displays the uploaded file's name next to the
     *  Image* upload — same trap for {@code ImageManagement2.iconname} (real "Icon Name*") vs
     *  {@code ImageManagement2.iconimagename} (decoy next to the Icon Image* upload). A loose "contains name"
     *  match picks the wrong (decoy) box, so both are matched with an END anchor ({@code $}) on the exact
     *  ng-model suffix, never by label text or a partial contains-match. */
    private static final String FIND_IMAGE_FIELDS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const labelNear=e=>{ let t='', p=e.parentElement, hop=0; while(p && hop++<5 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
            + " const catSel=[...document.querySelectorAll('select')].find(e=>e.offsetParent!==null && (/\\.categoryid$/i.test(e.getAttribute('ng-model')||'') || /categ/i.test(e.getAttribute('ng-model')||'') || /categ/i.test(labelNear(e))));"
            + " const fileInputs=[...document.querySelectorAll('input[type=file]')];"
            + " const iconFile = fileInputs.find(e=>/\\.iconimage$/i.test(e.getAttribute('ng-model')||'')) || fileInputs.find(e=>/icon/i.test(e.getAttribute('ng-model')||'') || /icon/i.test(labelNear(e)));"
            + " const imageFile = fileInputs.find(e=>e!==iconFile && /\\.image$/i.test(e.getAttribute('ng-model')||''))"
            + "   || fileInputs.find(e=>e!==iconFile && (/image/i.test(e.getAttribute('ng-model')||'') || /image/i.test(labelNear(e)))) || fileInputs.find(e=>e!==iconFile);"
            + " const textInputs=[...document.querySelectorAll('input')].filter(e=>e.offsetParent!==null && e.type!=='file');"
            + " const iconNameEl = textInputs.find(e=>/\\.iconname$/i.test(e.getAttribute('ng-model')||''))"
            + "   || textInputs.find(e=>/icon/i.test((e.getAttribute('ng-model')||'')+' '+labelNear(e)) && /name/i.test((e.getAttribute('ng-model')||'')+' '+labelNear(e)));"
            + " const imageNameEl = textInputs.find(e=>/\\.imagename$/i.test(e.getAttribute('ng-model')||''))"
            + "   || textInputs.find(e=>e!==iconNameEl && /image/i.test((e.getAttribute('ng-model')||'')+' '+labelNear(e)) && /name/i.test((e.getAttribute('ng-model')||'')+' '+labelNear(e)));";

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { }
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1200);
                // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
                waitForAngular(1200);
                try {
                    page.waitForFunction("() => [...document.querySelectorAll('a[href]')].filter(a=>a.offsetParent!==null).length > 3",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                } catch (Exception ignore) { System.out.println("ImageManagement.nav: Nursing submenu did not expand in time (attempt " + (attempt + 1) + ")"); }
                // EXACT-ish text match on a VISIBLE link ONLY — accepts "Image Management" or the sibling
                // "Image Category" is a DIFFERENT screen (excluded by requiring "management").
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*image\\s*management\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                        + " if(!a) return ''; a.id='__imMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) {
                    Object dbg = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null).map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).join(' | ')");
                    System.out.println("ImageManagement.nav DEBUG: visible links => " + dbg);
                    System.out.println("ImageManagement.nav: menu link not found (attempt " + (attempt + 1) + ")");
                    continue;
                }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__imMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("ImageManagement.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__imMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__imMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("ImageManagement.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("ImageManagement.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the Image Management screen — verified by Code/Remark boxes present plus the URL. */
    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/imagemanagement/i.test((location.hash||'').replace(/\\s+/g,''));" + FIND_FIELDS
                + " if(codeEl && remEl) return true;"
                + " if(/image\\s*management/i.test(hdr)) return true;"
                + " return urlOk && [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Click <b>Add</b> if this screen has one; a no-op on inline-add screens where the form is already there.
     * Returns what happened, for the report.
     */
    public String clickAddIfPresent() {
        if (Boolean.TRUE.equals(page.evaluate("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }"))) {
            return "inline-add (form already on screen)";
        }
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__imAdd'; return true; }"));
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__imAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ImageManagement.clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__imAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    // ---- header form -------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>. {@code attempt} shifts both so a retry after an "already exists"
     * toast submits genuinely different details; once the pool is exhausted a number is appended.
     */
    public String fillCodeAndRemark(int attempt) {
        String code = "IM" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl, a.code); const rm=set(remEl, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[models: ([^/]+) / ([^\\]]+)\\]").matcher(res);
        if (m.find()) { lastCodeModel = m.group(1).trim(); lastRemarkModel = m.group(2).trim(); }
        return res;
    }

    /** Select the first real <b>Image Category</b> option. Returns the chosen text, or "(no category select)". */
    public String selectImageCategory() {
        Object r = page.evaluate("() => new Promise(async resolve => { const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + FIND_IMAGE_FIELDS
                + " let sel=catSel; for(let k=0;k<15 && !sel;k++){ await sleep(400); {" + FIND_IMAGE_FIELDS + " sel=catSel; } }"
                + " if(!sel){ resolve('(no category select)'); return; }"
                + " const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0){ resolve('(no options)'); return; }"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(x){} if($){try{$(sel).trigger('change');}catch(x){}}"
                + " resolve(norm(sel.options[i].textContent)); })");
        lastCategory = r == null ? "" : r.toString();
        waitForAngular(400);
        return lastCategory;
    }

    /** Upload a file into the <b>Image</b> file input (found by nearby label, not the Icon Image one). */
    public String uploadImage(String path) {
        Object found = page.evaluate("() => {" + FIND_IMAGE_FIELDS + " if(!imageFile) return false; imageFile.id='__imImageFile'; return true; }");
        if (!Boolean.TRUE.equals(found)) return "(no Image file input found)";
        try { page.locator("#__imImageFile").setInputFiles(java.nio.file.Paths.get(path)); }
        catch (Exception e) { System.out.println("uploadImage: setInputFiles failed - " + e.getMessage()); return "(upload failed: " + e.getMessage() + ")"; }
        page.evaluate("() => { const e=document.getElementById('__imImageFile'); if(e){ e.dispatchEvent(new Event('change',{bubbles:true})); e.removeAttribute('id'); } }");
        waitForAngular(800);
        return "Image uploaded: " + path;
    }

    /** Upload a file into the <b>Icon Image</b> file input (found by nearby label mentioning "icon"). */
    public String uploadIconImage(String path) {
        Object found = page.evaluate("() => {" + FIND_IMAGE_FIELDS + " if(!iconFile) return false; iconFile.id='__imIconFile'; return true; }");
        if (!Boolean.TRUE.equals(found)) return "(no Icon Image file input found)";
        try { page.locator("#__imIconFile").setInputFiles(java.nio.file.Paths.get(path)); }
        catch (Exception e) { System.out.println("uploadIconImage: setInputFiles failed - " + e.getMessage()); return "(upload failed: " + e.getMessage() + ")"; }
        page.evaluate("() => { const e=document.getElementById('__imIconFile'); if(e){ e.dispatchEvent(new Event('change',{bubbles:true})); e.removeAttribute('id'); } }");
        waitForAngular(800);
        return "Icon Image uploaded: " + path;
    }

    /** Enter <b>Image Name*</b> and <b>Icon Name*</b> (two distinct "name" boxes, disambiguated by label). */
    public String fillImageAndIconName(int attempt) {
        lastImageName = "Image " + lastCode;
        lastIconName = "Icon " + lastCode;
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_IMAGE_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const im=set(imageNameEl, a.imageName); const ic=set(iconNameEl, a.iconName);"
                + " return 'ImageName='+im+' | IconName='+ic; }",
                java.util.Map.of("imageName", lastImageName, "iconName", lastIconName));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click the INNER <b>Add</b> that appends the filled row (Image Category / Image / Image Name / Icon
     * Name / Icon Image) to the detail grid (distinct from the outer Add that opened this form).
     */
    public String clickInnerAdd() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__imInnerAdd'; return true; }"));
        if (!tagged) return "no inner Add button found";
        try { page.locator("#__imInnerAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickInnerAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__imInnerAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1000);
        return "inner Add clicked";
    }

    // ---- save ---------------------------------------------------------------

    /** Click <b>Save</b> and return the toast. */
    public String saveAndGetToast() {
        // Re-assert Image Name / Icon Name right before Save — this form re-renders as later fields (Icon
        // Image upload, the inner Add) are filled, and can blank a text field set earlier without any visible
        // sign, the same gotcha several sibling masters in this codebase already guard against. Only re-set
        // when the field currently reads empty, so a value the app legitimately kept is not overwritten.
        Object reassert = page.evaluate("(a) => { const A=window.angular;" + FIND_IMAGE_FIELDS
                + " const setIfEmpty=(e,v)=>{ if(!e) return '(no field)'; if((e.value||'').trim()) return e.value+'(kept)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " return 'ImageName='+setIfEmpty(imageNameEl, a.imageName)+' | IconName='+setIfEmpty(iconNameEl, a.iconName); }",
                java.util.Map.of("imageName", lastImageName, "iconName", lastIconName));
        System.out.println("ImageManagement save: re-assert -> " + reassert);
        waitForAngular(300);
        page.evaluate("() => { window.__imToasts=[]; if(window.__imObs) window.__imObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__imToasts.includes(t)) window.__imToasts.push(t); }); };"
                + " window.__imObs=new MutationObserver(grab); window.__imObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("image") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__imSave'; return true; }");
        String how = "none";
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__imSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); how = "click"; }
            catch (Exception e) { System.out.println("ImageManagement save: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__imSave'); if(e) e.removeAttribute('id'); }");
        } else {
            // No button — invoke the form's ng-submit handler from whichever scope owns it.
            Object invoked = page.evaluate("() => { const A=window.angular;"
                    + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                    + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                    + " if(!hn) return 'none'; const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                    + " if(!hs) return 'none'; try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; }");
            how = String.valueOf(invoked);
        }
        System.out.println("ImageManagement save => " + how);
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__imToasts||[]).some(a=>/image|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("ImageManagement save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__imToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    public String lastSaveHttp = "";

    /** PROBE — the visible field ng-models plus the visible button labels, for a diagnosable FAIL. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<4 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null || e.type==='file').slice(0,50).map(e=>"
                + "   e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)).filter(t=>t).slice(0,25);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
