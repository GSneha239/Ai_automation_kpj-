package com.kpj.pages.AncillaryServices_page.HQM_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ancillary Services &gt; HQM &gt; <b>Incident Reporting</b> - Page Object ({@code #/IncidentReporting} list, {@code #/add-IncidentReporting} form).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar HQM &gt; Incident Reporting; list <b>Add</b> ({@code fnSaveIncident()});
 * on the form <b>Name Of The Victim*</b> ({@code IncidentDetails.victimname}, letters only), <b>Employee Department</b> ({@code IncidentDetails.Department}), <b>Remark</b>
 * ({@code IncidentDetails.remark}), <b>Event Details*</b> ({@code IncidentDetails.eventDetail}), <b>Place of Occurence</b> ({@code IncidentDetails.Placeofoccurance}), <b>Incident Time</b>
 * ({@code inputTime}, a time picker whose hours arrow is {@code incrementHours()}), a tick in <b>Other Responsible Department</b> ({@code cc.IsSelected}, {@code funSetChkBxCCModels()}), the
 * <b>Template Details</b> tab with its <b>Add</b> ({@code AddTemplate();}), then <b>Save</b> ({@code fnSaveIncidentReport()}) - toast "Incident Saved Successfully." and the app returns to the list.
 * The list is a ui-grid: Form Number, Victim Name, Reporting Name, Incident Date, Incident Harm To, Template, Incident Status, Status, Edit, History.</p>
 */
public class IncidentReporting extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/incidentreporting";
    private static final String PARENT = "Ancillary Services", GROUP = "HQM";

    public IncidentReporting(Page page) { super(page); }

    // ---- navigation (the sidebar builds each level lazily: open each group ONCE and await it) -------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    private int innerState(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /** Ancillary Services &gt; HQM &gt; Incident Reporting (the list). Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__irLink'; }", HREF);
                try { page.locator("#__irLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("IncidentReporting.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__irLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fnIncidentReporting/.test(b.getAttribute('ng-click')||''))", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("IncidentReporting.nav: the list is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/incidentreporting/?$"); }

    public boolean onForm() { return page.url().toLowerCase().contains("add-incidentreporting"); }

    /** List: click <b>Add</b> ({@code fnSaveIncident()}) and wait for the form. */
    public boolean clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'')==='fnSaveIncident()' && x.offsetParent!==null); if(b) b.id='__irAdd'; }");
        try { page.locator("#__irAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("IncidentReporting.clickAdd: " + String.valueOf(e.getMessage()).split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__irAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => /add-IncidentReporting/i.test(location.hash) && !!document.querySelector(\"[ng-model='IncidentDetails.victimname']\")", null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("IncidentReporting.clickAdd: the form did not open"); }
        waitForAngular(1500);
        return onForm();
    }

    /** Diagnostic: every visible control of the open form with its model / click handler / label / options. */
    public String dumpForm() {
        Object r = page.evaluate("() => {" + JS + " const lab=e=>{ try{ if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) return norm(l.textContent); } const g=e.closest('.form-group,td,li'); const l=g&&g.querySelector('label'); return l?norm(l.textContent).slice(0,40):''; }catch(x){return '';} };"
                + " const f=[...document.querySelectorAll('input,select,textarea,button,a[ng-click],ul.nav a')].filter(e=>!e.closest('.sidebar,.main-sidebar,.sidebar-menu')).map(e=>(vis(e)?'':'(hidden) ')+e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+' ngm='+(e.getAttribute('ng-model')||'')+' ngc='+(e.getAttribute('ng-click')||'')+' id='+(e.id||'')+' label='+lab(e)+(e.tagName==='SELECT'?' opts='+e.options.length+' sel='+norm((e.options[e.selectedIndex]||{}).textContent):'')+(e.tagName==='A'||e.tagName==='BUTTON'?' text='+norm(e.textContent).slice(0,30):''));"
                + " return f.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- the form (steps as recorded) -----------------------------------------------------------------------------

    /** What the last fill / click could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    /**
     * Type {@code value} into the visible input / textarea bound to {@code ngModel} (real typing, so the AngularJS model and the form's own validators see it) and read the model back.
     * Returns the value the box holds afterwards ("" when the box was not found).
     */
    public String fillBox(String ngModel, String value) {
        String sel = "[ng-model='" + ngModel + "']";
        try {
            Locator box = page.locator(sel + ":visible").first();
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(value, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
        } catch (Exception e) {
            problems.add(ngModel + ": " + String.valueOf(e.getMessage()).split("\n")[0]);
            return "";
        }
        waitForAngular(300);
        Object v = page.evaluate("(s) => { const e=[...document.querySelectorAll(s)].find(x=>x.offsetParent!==null) || document.querySelector(s); return e ? e.value : ''; }", sel);
        String got = v == null ? "" : v.toString();
        if (!got.equals(value)) problems.add(ngModel + ": typed \"" + value + "\" but the box holds \"" + got + "\"");
        return got;
    }

    /** The Incident Time box's text (empty until a time is set). */
    public String incidentTime() {
        Object v = page.evaluate("() => { const e=document.querySelector(\"[ng-model='inputTime']\"); return e ? e.value : ''; }");
        return v == null ? "" : v.toString();
    }

    /**
     * Incident Time: click the box (opens the time picker), click the hours <b>up</b> arrow ({@code incrementHours()}) once - as the recording did - then click the
     * "Other Responsible Department" label to close the picker. Returns "before -> after".
     */
    public String bumpIncidentHour() {
        String before = incidentTime();
        try {
            page.locator("[ng-model='inputTime']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(500);
            page.locator("a[ng-click='incrementHours()']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(400);
        } catch (Exception e) { problems.add("Incident Time: " + firstLine(e)); }
        closePickers();
        return (before.isEmpty() ? "(blank)" : before) + " -> " + (incidentTime().isEmpty() ? "(blank)" : incidentTime());
    }

    /** Click a neutral label (never Escape) so an open picker closes. */
    public void closePickers() {
        try {
            page.locator("label").filter(new Locator.FilterOptions().setHasText("Other Responsible Department")).first().click(new Locator.ClickOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        waitForAngular(300);
    }

    /** Tick the first department under <b>Other Responsible Department</b> ({@code cc.IsSelected} id=1, {@code funSetChkBxCCModels()}); returns its text, "" if it could not be ticked. */
    public String tickFirstResponsibleDepartment() {
        try {
            Locator cb = page.locator("input[ng-model='cc.IsSelected']").first();
            cb.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            cb.click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) {
            problems.add("Other Responsible Department tick: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return "";
        }
        waitForAngular(400);
        Object r = page.evaluate("() => {" + JS + " const c=document.querySelector(\"input[ng-model='cc.IsSelected']\"); if(!c || !c.checked) return ''; const p=c.closest('label,li,div,td'); return norm(p ? p.textContent : '') || 'ticked'; }");
        return r == null ? "" : r.toString();
    }

    /** Open the <b>Template Details</b> tab. */
    public boolean openTemplateTab() {
        try {
            page.locator("a").filter(new Locator.FilterOptions().setHasText("Template Details")).first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Template Details tab: " + String.valueOf(e.getMessage()).split("\n")[0]); return false; }
        waitForAngular(800);
        Object r = page.evaluate("() => !!document.querySelector(\"button[ng-click='AddTemplate();']\") && document.querySelector(\"button[ng-click='AddTemplate();']\").offsetParent!==null");
        return Boolean.TRUE.equals(r);
    }

    private static String firstLine(Exception e) { String m = String.valueOf(e.getMessage()).replace((char) 10, (char) 32).replace((char) 13, (char) 32); return m.length() > 220 ? m.substring(0, 220) : m; }

    /** Pick {@code name} in the select2 box bound to {@code ngModel}; returns "" when it is shown and held by the model, else the reason. */
    public String pickSelect2(String ngModel, String name) {
        return com.kpj.pages.Select2Pick.pick(page, ngModel, name);
    }

    /** Remove any toast still on screen (they linger a few seconds) so the next read only sees new ones. */
    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    /** The Template select's chosen text before Add ("--Select--" when none) - read so the report shows what Add acted on. */
    public String templateSelected() {
        Object r = page.evaluate("() => { const s=document.querySelector(\"select[ng-model='IncidentDetails.templateid']\"); if(!s) return ''; const o=s.options[s.selectedIndex]; return o ? (o.textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the Template tab's <b>Add</b> ({@code AddTemplate();}); returns "template | description | toast" as the form shows them afterwards ("" if the click failed). */
    public String clickAddTemplate() {
        clearToasts();
        armToasts();
        try {
            page.locator("button[ng-click='AddTemplate();']").first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("Template Add: " + firstLine(e)); return ""; }
        waitForAngular(1500);
        Object r = page.evaluate("() => { const t=document.querySelector(\"[ng-model='IncidentDetails.TemplateDescription']\"); const s=document.querySelector(\"select[ng-model='IncidentDetails.templateid']\");"
                + " const o=s? s.options[s.selectedIndex] : null; return ((o? (o.textContent||'').trim() : '') + ' | description: ' + ((t && t.value) ? t.value.replace(/\\s+/g,' ').trim().slice(0,80) : '(none)') + ' | toast: ' + ((window.__irT||[]).join(' / ') || '(none)')); }");
        return r == null ? "" : r.toString();
    }

    private void armToasts() {
        page.evaluate("() => { window.__irT=[]; if(window.__irObs) window.__irObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__irT.includes(t)) window.__irT.push(t); });"
                + " window.__irObs=new MutationObserver(grab); window.__irObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Click <b>Save</b> ({@code fnSaveIncidentReport()}), accept a confirm if one shows, and return the toast text ("" when none appeared). */
    public String clickSave() {
        clearToasts();
        armToasts();
        try {
            page.locator("button[ng-click='fnSaveIncidentReport()']").first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Save: " + String.valueOf(e.getMessage()).split("\n")[0]); return ""; }
        for (int i = 0; i < 40; i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__irT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            // a confirm ("Do you want to save") may sit in front of the save
            page.evaluate("() => { const b=[...document.querySelectorAll('.ng-confirm-box button,.jconfirm button,.swal2-confirm')].find(x=>x.offsetParent!==null && /^\\s*(ok|yes|save|confirm)\\s*$/i.test(x.textContent||'')); if(b) b.click(); }");
        }
        return "";
    }

    /** Wait for the list to be back (Save returns to #/IncidentReporting). */
    public boolean awaitList(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (onList()) { waitForAngular(1500); return true; } page.waitForTimeout(300); }
        return onList();
    }

    /** The list's rows as {id, form number or id, victim, reporting name, date, template, status}, read from the grid data array. */
    public List<String[]> listRows() {
        Object r = page.evaluate("() => { try{ let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!data && s&&s.gridOptions&&s.gridOptions.data&&s.gridOptions.data.length) data=s.gridOptions.data; }catch(e){} });"
                + " if(!data) return []; const s=v=>v==null?'':String(v); return data.map(x=>[s(x.id), s(x.victimname), s(x.reportingname), s(x.date), s(x.TemplateDescription), s(x.IncidentStatus)]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[c.size()]; for (int i = 0; i < a.length; i++) a[i] = String.valueOf(c.get(i)); out.add(a); }
        return out;
    }

    /** The rows the list SHOWS on screen (text per row), for the report. */
    public List<String> shownRows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(vis).map(x=>norm(x.textContent).slice(0,160)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Every form number the list's grid holds (all pages), newest data as read from the grid's data array. */
    public List<String[]> gridRows() {
        Object r = page.evaluate("() => { try{ let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!data && s&&s.gridOptions&&s.gridOptions.data&&s.gridOptions.data.length) data=s.gridOptions.data; }catch(e){} });"
                + " if(!data) return []; const s=v=>v==null?'':String(v); return data.map(x=>Object.keys(x).slice(0,14).map(k=>k+'='+s(x[k]))); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[c.size()]; for (int i = 0; i < a.length; i++) a[i] = String.valueOf(c.get(i)); out.add(a); }
        return out;
    }
}
