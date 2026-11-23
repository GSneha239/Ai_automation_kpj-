package com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Meal Type Master</b> — configuration screen Page
 * Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Diet and Nutrition</b> (submenu) → <b>Meal Type Master</b> →
 * enter <b>Code</b>, <b>Meal Type</b>, <b>Stipulated Time</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Sibling of {@link TherapeuticDietMaster} / {@link ConsistencyModifiedDietMaster} (same submenu). This
 * screen's exact ng-model names were not yet confirmed live, so fields are matched generically by LABEL text.
 * {@link #describeForm()} dumps every visible field/button so a naming mismatch is diagnosable from the report
 * without a second live session.</p>
 */
public class MealTypeMaster extends BasePage {

    public MealTypeMaster(Page page) { super(page); }

    public String route = "", lastCode = "", lastMealType = "", lastStipulatedTime = "";

    /** Realistic meal-type names, not "Breakfast &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] MEAL_TYPES = {
            "Breakfast", "Lunch", "Dinner", "Mid-Morning Snack", "Evening Snack", "Bedtime Snack",
            "Brunch", "Supper", "High Tea", "Early Morning Tea"
    };

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // expand the "Diet and Nutrition" submenu parent
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/diet\\s*(and|&)?\\s*nutrition/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // EXACT text match ONLY, ANCHORED — "Meal Type Master" must not match any sibling diet-related link.
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*meal\\s*type\\s*master\\s*$/i.test(norm(x.textContent))); if(!a) return ''; a.id='__mtMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__mtMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("MealTypeMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("MealTypeMaster.nav: menu link not found"); }
        waitForAngular(1200);
        return page.url().length() > 0;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("mealtype"); }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * Enter <b>Code</b>, <b>Meal Type</b>, <b>Stipulated Time</b> — matched by LABEL text since the exact
     * ng-models are unconfirmed. Stipulated Time is filled as plain text first; if that field turns out to be
     * a timepicker (hours/minutes spinners like the schedule screens), the spinners are set too as a fallback.
     */
    public String fillDetails(int attempt) {
        String code = "MT" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastMealType = MEAL_TYPES[attempt % MEAL_TYPES.length] + (attempt >= MEAL_TYPES.length ? " " + (attempt / MEAL_TYPES.length + 1) : "");
        // Confirmed live: this is a native <input type="time"> (ng-model formData.MealOrderStipulatedTimeObj).
        // Such inputs only accept the strict HTML5 24-hour "HH:MM" format for .value — anything else (e.g. a
        // 12-hour "08:00 AM" string) is silently rejected by the browser, leaving the underlying model null and
        // Submit failing with "Please fill required fields!" even though the box visibly looked filled.
        lastStipulatedTime = "08:00";
        Object r = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
                + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null"
                + "   && !/colFilter|paginationCurrentPage|textAngular|^q$/i.test(e.getAttribute('ng-model')||''));"
                + " const codeEl=boxes.find(e=>/^code\\*?$/i.test(labelOf(e)) || /\\.code(id)?$/i.test(e.getAttribute('ng-model')||''));"
                + " const mealEl=boxes.find(e=>e!==codeEl && /meal\\s*type/i.test(labelOf(e)));"
                + " const timeEl=boxes.find(e=>e!==codeEl && e!==mealEl && (/stipulated\\s*time/i.test(labelOf(e)) || e.type==='time'));"
                + " const setText=(e,v)=>{ if(!e) return '(not found)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setText(codeEl, a.code); const mt=setText(mealEl, a.meal); const tm=setText(timeEl, a.time);"
                + " return 'Code='+cd+' | MealType='+mt+' | StipulatedTime='+tm"
                + "   +' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(mealEl?(mealEl.getAttribute('ng-model')||'?'):'none')+' / '+(timeEl?(timeEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "meal", lastMealType, "time", lastStipulatedTime));
        waitForAngular(400);
        String res = r == null ? "" : r.toString();
        // Fallback: if Stipulated Time was not a plain text field, try the schedule-family timepicker spinners.
        if (res.contains("StipulatedTime=(not found)")) {
            String widget = fillTimeWidgetFallback(lastStipulatedTime);
            if (!widget.isEmpty()) res = res.replace("StipulatedTime=(not found)", "StipulatedTime=" + widget);
        }
        return res;
    }

    /** Fallback for a Stipulated Time TIMEPICKER (hours/minutes spinners), same widget family as the schedule screens. */
    private String fillTimeWidgetFallback(String hhmm) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").matcher(hhmm);
        if (!m.find()) return "";
        Object r = page.evaluate("([hh,mm,mer]) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null);"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null);"
                + " if(!hours.length) return '';"
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); };"
                + " set(hours[0], hh); set(mins[0], mm);"
                + " if(mer && hours[0]){ let p=hours[0].parentElement, hop=0, btn=null;"
                + "   while(p && hop++<6 && !btn){ btn=[...p.querySelectorAll('button,a')].find(b=>/^(am|pm)$/i.test((b.textContent||'').trim())); p=p.parentElement; }"
                + "   if(btn && (btn.textContent||'').trim().toUpperCase()!==mer) btn.click(); }"
                + " return hh+':'+mm+' '+(mer||''); }",
                java.util.Arrays.asList(m.group(1), m.group(2), m.group(3) == null ? "" : m.group(3).toUpperCase()));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Submit</b> and return the toast (toastr cleared first). Re-asserts Code/Meal Type right before
     * clicking — the Angular route-settle race can blank a field set too early (same lesson as
     * {@code CSSD_page.DialysisType}).
     */
    public String submitAndGetToast() {
        if (!lastCode.isEmpty()) {
            page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const labelOf=e=>{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); }"
                    + "   let p=e.closest('.form-group,.row,td,tr,div'); for(let hop=0; hop<4 && p; hop++){ const l=p.querySelector('label,.control-label'); if(l) return norm(l.textContent); p=p.parentElement; } return ''; };"
                    + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null);"
                    + " const codeEl=boxes.find(e=>/^code\\*?$/i.test(labelOf(e)) || /\\.code(id)?$/i.test(e.getAttribute('ng-model')||''));"
                    + " const mealEl=boxes.find(e=>e!==codeEl && /meal\\s*type/i.test(labelOf(e)));"
                    + " const timeEl=boxes.find(e=>e!==codeEl && e!==mealEl && (/stipulated\\s*time/i.test(labelOf(e)) || e.type==='time'));"
                    + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " set(codeEl, a.code); set(mealEl, a.meal); set(timeEl, a.time); }",
                    java.util.Map.of("code", lastCode, "meal", lastMealType, "time", lastStipulatedTime));
            waitForAngular(300);
        }
        Object tagged = page.evaluate("() => { window.__mtToasts=[]; if(window.__mtObs) window.__mtObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mtToasts.includes(t)) window.__mtToasts.push(t); }); };"
                + " window.__mtObs=new MutationObserver(grab); window.__mtObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const cand=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " let b=cand.find(x=>/mealtype/i.test(x.getAttribute('ng-click')||''))"
                + " || cand.find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__mtSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__mtSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__mtToasts||[]).some(a=>/meal|type|master|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__mtToasts||[]).includes(t)) (window.__mtToasts=window.__mtToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__mtToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * PROBE — the visible field ng-models (tag, type, placeholder, nearby label) plus the visible button labels.
     * Included in the FAIL text so a miss is diagnosable without another run.
     */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement, hop=0; while(p && hop++<3 && !t){ const l=p.querySelector('label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t.slice(0,40); };"
                + " const fields=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>"
                + "   e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('id')||e.name||'?')+']'"
                + "   +' type=\"'+(e.type||'')+'\"'+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+(lbl(e)?' lbl=\"'+lbl(e)+'\"':''));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " return 'FIELDS: '+fields.join(' ; ')+'  ||  BUTTONS: '+btns.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}
