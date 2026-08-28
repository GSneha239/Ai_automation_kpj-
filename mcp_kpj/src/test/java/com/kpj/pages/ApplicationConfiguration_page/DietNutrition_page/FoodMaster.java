package com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Food Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Diet and Nutrition</b> (submenu) → <b>Food Master</b> →
 * <b>Add</b> → <b>Food &amp; Beverage Details</b> (Code, Food Name, Service Name) → <b>Nutritional Details</b>
 * (Calories, Protein, Carbohydrates, Fats) → <b>Other Details</b> (Veg / Non-Veg) → <b>Diet Linking</b> →
 * <b>Submit</b> → success toast.</p>
 *
 * <p>Four sections on one form, each with its own fields. This screen's exact ng-model names were not yet
 * confirmed live, so every field is matched generically: first by its section heading (DOM-order range between
 * that heading and the next known section heading — never confuses one section's controls with another's, same
 * technique used to fix {@code BloodBank_page.RedCellSerologyMaster}'s two grouping sections), then by its own
 * LABEL text within that range. {@link #describeForm()} dumps every visible field/button so a naming mismatch
 * is diagnosable from the report without a second live session.</p>
 */
public class FoodMaster extends BasePage {

    public FoodMaster(Page page) { super(page); }

    /** Best-effort direct-route fallback if the menu link cannot be found at all. */
    public static final String ROUTE = "#/FoodMaster";
    public String lastCode = "", lastFoodName = "", lastServiceName = "";
    public String lastCalories = "", lastProtein = "", lastCarbs = "", lastFats = "";
    public String lastVegNonVeg = "", lastDietLinking = "";
    private String menuHref = "";
    public byte[] toastPng;
    public String lastSaveHttp = "";

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** Realistic food/dish names, not "Steamed Rice &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] FOOD_NAMES = {
            "Steamed Rice", "Grilled Chicken Breast", "Vegetable Soup", "Fresh Fruit Salad", "Boiled Egg",
            "Mashed Potato", "Chicken Porridge", "Grilled Fish Fillet", "Mixed Vegetables", "Plain Oatmeal"
    };

    /** The four section headings this form is known to have — used to bound each section's own field range. */
    private static final String SEC_FOODBEV = "food\\s*(&|and)?\\s*beverage";
    private static final String SEC_NUTRITION = "nutritional\\s*detail";
    private static final String SEC_OTHER = "other\\s*detail";
    private static final String SEC_DIETLINK = "diet\\s*link";

    /**
     * Shared JS: {@code rangeFor(sectionRe)} returns a predicate matching elements in DOM order strictly AFTER
     * that section's heading and BEFORE the next DIFFERENT known section heading (or end of document) — this
     * can never cross into a sibling section's identically-shaped controls. Also provides {@code labelOf(e)}.
     */
    private static final String RANGE_JS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const HEADRX=/food\\s*(&|and)?\\s*beverage|nutritional\\s*detail|other\\s*detail|diet\\s*link/i;"
            + " const rangeFor=(re)=>{ const rx=new RegExp(re,'i'); const all=[...document.querySelectorAll('*')];"
            + "   const hit=all.find(e=>e.children.length===0 && rx.test(norm(e.textContent||'')));"
            + "   if(!hit) return null; let boundary=null;"
            + "   for(const e of all){ if(e===hit||e.children.length!==0) continue; const t=norm(e.textContent||'');"
            + "     if(!HEADRX.test(t)||rx.test(t)) continue;"
            + "     if(!(hit.compareDocumentPosition(e)&Node.DOCUMENT_POSITION_FOLLOWING)) continue;"
            + "     if(!boundary||(e.compareDocumentPosition(boundary)&Node.DOCUMENT_POSITION_FOLLOWING)) boundary=e; }"
            + "   return el=>{ if(!(hit.compareDocumentPosition(el)&Node.DOCUMENT_POSITION_FOLLOWING)) return false;"
            + "     if(!boundary) return true; return !!(el.compareDocumentPosition(boundary)&Node.DOCUMENT_POSITION_FOLLOWING); }; };"
            + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
            + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };";

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
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/diet\\s*(and|&)?\\s*nutrition/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1000);
                // EXACT text match ONLY — a bare href-contains fallback previously picked a DIFFERENT, similarly
                // spelled menu item on other submenus (see CancellationReason.nav's "Receipt Cancellation" trap).
                Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const links=[...document.querySelectorAll('a[href]')];"
                        + " const a = links.find(x=>/^\\s*food\\s*master\\s*$/i.test(norm(x.textContent)));"
                        + " if(!a) return ''; a.id='__fmMenu'; return a.getAttribute('href')||'link'; }");
                if (href == null || href.toString().isEmpty()) { System.out.println("FoodMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
                if (href.toString().startsWith("#")) menuHref = href.toString();
                try { page.locator("#__fmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("FoodMaster.nav: real click failed, dispatching in-page click - " + e.getMessage());
                    try { page.evaluate("() => { const a=document.getElementById('__fmMenu'); if(a) a.click(); }"); }
                    catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__fmMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } catch (Exception e) {
                System.out.println("FoodMaster.nav: menu entry raced the SPA route change (attempt " + (attempt + 1) + ") - " + e.getMessage());
                waitForAngular(2000);
            }
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("FoodMaster.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        return onScreen();
    }

    public boolean onScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " const urlOk=/foodmaster/i.test(location.hash||'');"
                + " if(!/food\\s*master/i.test(hdr) && !urlOk) return false;"
                + " return true; }");
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

    /** Click <b>Add</b> to open the Food Master form. Real-click (polls up to ~7.5s). */
    public String clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__fmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) return "no Add button and no inline form";
        try { page.locator("#__fmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("FoodMaster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__fmAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2000);
        return "Add clicked (" + page.url() + ")";
    }

    /** Fill a text/number box within {@code sectionRe} whose LABEL matches {@code fieldRe}. */
    private String fillTextInSection(String sectionRe, String fieldRe, String value) {
        Object r = page.evaluate("(a) => {" + RANGE_JS
                + " const inRange=rangeFor(a.section); if(!inRange) return '(section not found: '+a.section+')';"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && inRange(e));"
                + " const el=boxes.find(e=>new RegExp(a.field,'i').test(labelOf(e)));"
                + " if(!el) return '(not found: '+a.field+')';"
                + " const c=window.angular.element(el).controller('ngModel'); el.value=a.value; if(c){c.$setViewValue(a.value);c.$render();}"
                + " el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return a.value; }",
                java.util.Map.of("section", sectionRe, "field", fieldRe, "value", value));
        waitForAngular(250);
        return r == null ? "" : r.toString();
    }

    /** Select the {@code index}-th real option of the select within {@code sectionRe} whose LABEL matches {@code fieldRe}. */
    private String selectInSection(String sectionRe, String fieldRe, int index) {
        Object idx = page.evaluate("(a) => {" + RANGE_JS
                + " const inRange=rangeFor(a.section); if(!inRange) return -3;"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && inRange(e));"
                + " const s=sels.find(e=>new RegExp(a.field,'i').test(labelOf(e))); if(!s) return -2; s.id='__fmSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(!real.length) return -1; return real[Math.min(a.index, real.length-1)].i; }",
                java.util.Map.of("section", sectionRe, "field", fieldRe, "index", index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -3; }
        if (i == -3) return "(section not found: " + sectionRe + ")";
        if (i == -2) return "(not found: " + fieldRe + ")";
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__fmSel'); if(e) e.removeAttribute('id'); }"); return "(no-options)"; }
        return commitSelectById("__fmSel", i);
    }

    private String commitSelectById(String id, int i) {
        try {
            page.locator("#" + id).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("commitSelectById(" + id + "): real select failed - " + e.getMessage());
            page.evaluate("(a) => { const e=document.getElementById(a.id); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=a.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", java.util.Map.of("id", id, "i", i));
        }
        waitForAngular(500);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }", id);
        return t == null ? "" : t.toString();
    }

    /**
     * <b>Food &amp; Beverage Details</b> — Code, Food Name, Service Name. {@code attempt} shifts Code and Food
     * Name so a retry after an "already exists" toast submits genuinely different details.
     * @return "Code=... | FoodName=... | ServiceName=..." summary
     */
    public String fillFoodBeverageDetails(int attempt) {
        String code = "FD" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastFoodName = FOOD_NAMES[attempt % FOOD_NAMES.length] + (attempt >= FOOD_NAMES.length ? " " + (attempt / FOOD_NAMES.length + 1) : "");
        String codeR = fillTextInSection(SEC_FOODBEV, "^code\\*?$", code);
        String nameR = fillTextInSection(SEC_FOODBEV, "food\\s*name", lastFoodName);
        String svc = selectInSection(SEC_FOODBEV, "service\\s*name", 0);
        lastServiceName = svc;
        return "Code=" + codeR + " | FoodName=" + nameR + " | ServiceName=" + svc;
    }

    /**
     * <b>Nutritional Details</b> — Calories, Protein, Carbohydrates, Fats.
     * @return summary
     */
    public String fillNutritionalDetails() {
        lastCalories = "250"; lastProtein = "5"; lastCarbs = "45"; lastFats = "3";
        String cal = fillTextInSection(SEC_NUTRITION, "calor", lastCalories);
        String pro = fillTextInSection(SEC_NUTRITION, "protein", lastProtein);
        String carb = fillTextInSection(SEC_NUTRITION, "carbohydrate", lastCarbs);
        String fat = fillTextInSection(SEC_NUTRITION, "fat", lastFats);
        return "Calories=" + cal + " | Protein=" + pro + " | Carbohydrates=" + carb + " | Fats=" + fat;
    }

    /** <b>Other Details</b> — select Veg (or Non-Veg via {@code veg=false}). Tries a select first, then radio/checkbox. */
    public String selectVegNonVeg(boolean veg) {
        String wantLabel = veg ? "veg" : "non.?veg";
        String sel = selectInSection(SEC_OTHER, wantLabel, 0);
        if (!sel.startsWith("(")) { lastVegNonVeg = sel; return sel; }
        // Fallback: radio/checkbox matched by its OWN nearby text (not the field label search above).
        Object r = page.evaluate("(a) => {" + RANGE_JS
                + " const inRange=rangeFor(a.section); if(!inRange) return '(section not found)';"
                + " const rx=new RegExp(a.want,'i');"
                + " const inputs=[...document.querySelectorAll('input[type=radio],input[type=checkbox]')].filter(e=>e.offsetParent!==null && inRange(e));"
                + " const el=inputs.find(e=>rx.test(norm((e.closest('label')||e.parentElement||{}).textContent||'')));"
                + " if(!el) return '(not found)'; el.id='__fmVeg'; return 'tag'; }",
                java.util.Map.of("section", SEC_OTHER, "want", veg ? "\\bveg\\b" : "non[- ]?veg"));
        String how = r == null ? "" : r.toString();
        if (!"tag".equals(how)) { lastVegNonVeg = how; return how; }
        try { page.locator("#__fmVeg").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("selectVegNonVeg: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__fmVeg'); if(e) e.removeAttribute('id'); }");
        waitForAngular(300);
        lastVegNonVeg = veg ? "Veg (radio/checkbox)" : "Non-Veg (radio/checkbox)";
        return lastVegNonVeg;
    }

    /** <b>Diet Linking</b> — select the {@code index}-th option of the first select in that section, else tick the first checkbox. */
    public String selectDietLinking(int index) {
        Object idx = page.evaluate("(a) => {" + RANGE_JS
                + " const inRange=rangeFor(a.section); if(!inRange) return -3;"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && inRange(e));"
                + " if(!sels.length) return -4; const s=sels[0]; s.id='__fmDietSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(!real.length) return -1; return real[Math.min(a.index, real.length-1)].i; }",
                java.util.Map.of("section", SEC_DIETLINK, "index", index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -3; }
        if (i == -3) { lastDietLinking = "(section not found: DietLinking)"; return lastDietLinking; }
        if (i == -1) { lastDietLinking = "(no-options)"; return lastDietLinking; }
        if (i != -4) { lastDietLinking = commitSelectById("__fmDietSel", i); return lastDietLinking; }
        // No select in the section — fall back to ticking the first checkbox there.
        Object tagged = page.evaluate("(a) => {" + RANGE_JS
                + " const inRange=rangeFor(a.section); if(!inRange) return '(section not found)';"
                + " const cb=[...document.querySelectorAll('input[type=checkbox]')].find(e=>e.offsetParent!==null && inRange(e) && !/row\\.entity/i.test(e.getAttribute('ng-model')||''));"
                + " if(!cb) return '(no checkbox)'; if(cb.checked) return 'already'; cb.id='__fmDietCb'; return 'tag'; }",
                java.util.Map.of("section", SEC_DIETLINK));
        String how = tagged == null ? "" : tagged.toString();
        if ("tag".equals(how)) {
            try { page.locator("#__fmDietCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("selectDietLinking: checkbox click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__fmDietCb'); if(e) e.removeAttribute('id'); }");
            waitForAngular(300);
            how = "checked";
        }
        lastDietLinking = how;
        return lastDietLinking;
    }

    /**
     * Click <b>Submit</b> (accepts "Save" too) and return the toast. Falls back to invoking the form's
     * ng-submit handler from the scope when there is no clickable button.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__fmToasts=[]; if(window.__fmObs) window.__fmObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__fmToasts.includes(t)) window.__fmToasts.push(t); }); };"
                + " window.__fmObs=new MutationObserver(grab); window.__fmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");

        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("food") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        Object how = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null);"
                + " if(b){ b.id='__fmSubmit'; return 'click'; }"
                + " const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                + " const m=f?((f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/)):null; const hn=m?m[1]:null;"
                + " if(hn){ const seen=new Set(); let hs=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id); if(typeof s[hn]==='function' && !hs) hs=s; }catch(e){} });"
                + "   if(hs){ try{ hs.$apply(function(){ hs[hn](); }); }catch(e){ try{ hs[hn](); }catch(e2){} } return 'invoked:'+hn; } }"
                + " return 'none'; }");
        System.out.println("FoodMaster submit => " + how);
        if ("click".equals(String.valueOf(how))) {
            try { page.locator("#__fmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("FoodMaster submit: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__fmSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__fmToasts||[]).some(a=>/food|master|saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("FoodMaster save HTTP => " + lastSaveHttp);
        Object t = page.evaluate("() => { const a=window.__fmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels
     * and section-like headings. Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,80).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +' type=\"'+(e.type||'')+'\"'+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " const heads=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,.panel-title,.box-title')].map(h=>norm(h.textContent)).filter(t=>t).slice(0,20);"
                + " return 'HEADINGS: '+heads.join(' | ')+'\\nFIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
