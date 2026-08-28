package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>OPD Waiting Area</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b> and <b>Remark</b> → tick a cabin in
 * <b>Consultation Room</b> → tick a modality in <b>Modality</b> → <b>Save</b> → success toast.</p>
 *
 * <p>The two tick lists are the risk on this screen. They carry the same kind of checkbox, so a lookup
 * that is not scoped to the owning section ticks the first list twice and leaves the second empty — while
 * reporting both steps as passed. Sections are therefore resolved by walking the DOM in order and tracking
 * which heading is currently in force, the same approach the Nursing Station Definition screen needed for
 * its Department / Room / Bed lists.</p>
 */
public class OpdWaitingArea extends BasePage {

    public OpdWaitingArea(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastCabin = "",
            lastModality = "", lastSections = "", lastToast = "", lastSaveDiagnostics = "",
            lastListCheck = "", lastRoute = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    /**
     * Walk the document in order, remembering which section heading is in force, and act on the
     * checkboxes that fall under the wanted one.
     *
     * <p>{@code SECTION_WALK} defines {@code inSection(headingRe, sectionRe)} returning the checkboxes
     * belonging to that section. Container-based scoping is unreliable here because both lists live in the
     * same block with only headings between them.</p>
     */
    private static final String SECTION_WALK =
            "const SEC=/consultation\\s*room|modality|cabin/i;"
          + "const boxesIn=(re)=>{ const rx=new RegExp(re,'i');"
          + "  const all=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,th,strong,b,label,div,span,input')];"
          + "  let on=false; const out=[];"
          + "  for(const el of all){"
          + "    if(el.tagName!=='INPUT'){"
          + "      const t=norm(el.textContent);"
          + "      if(t && t.length<45 && SEC.test(t)){ on=rx.test(t); }"
          + "      continue; }"
          + "    if(on && el.type==='checkbox' && vis(el) && !el.disabled"
          + "       && !/checkall|selectall/i.test(el.getAttribute('ng-model')||'')) out.push(el); }"
          + "  return out; };";

    // ---- navigation ------------------------------------------------------

    /** Application Configuration &rarr; General &rarr; OPD Waiting Area. */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) page.waitForTimeout(1500);
            clickMenu("^\\s*application\\s*configuration\\s*$");
            clickMenu("^\\s*general\\s*$");
            lastMenu = describeMenu();
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*opd\\s*waiting\\s*area\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__owMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__owMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("OpdWaitingArea.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__owMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            final String r = lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", r); } catch (Exception ignore) { }
            waitForAngular(3000);
        }
        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1200);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("waitingarea") || u.contains("opdwaiting");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=e.getAttribute('ng-model')||'', click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,35)+'\" [ng='+ng+'] opts='+e.options.length);"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,35)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,35)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].slice(0,40).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== OpdWaitingArea CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    /** How many rows each list owns — proves the targeting before anything is ticked. */
    public String describeSections() {
        Object r = page.evaluate("() => {" + JS
                + " const n=ng=>[...document.querySelectorAll("
                + "     \"input[type=checkbox][ng-model='\"+ng+\"']\")].filter(vis).length;"
                + " return 'Consultation Room rows (Cab.IsSelected): '+n('Cab.IsSelected')"
                + "   +' | Modality rows (Mod.IsSelected): '+n('Mod.IsSelected'); }");
        lastSections = r == null ? "" : r.toString();
        System.out.println("OpdWaitingArea: sections -> " + lastSections);
        return lastSections;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?waiting|AddWaiting|AddOPD/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__owAdd'; }");
        try {
            page.locator("#__owAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("OpdWaitingArea.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__owAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("OpdWaitingArea.clickAdd: Code field never appeared"); }
        waitForAngular(900);
        return formOpen();
    }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .some(e=>/code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')+' '+labelOf(e))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Enter the <b>Code</b> and <b>Remark</b>.
     *
     * <p>A control is claimed only once, so the two values cannot land in the same box — on Area/Town a
     * keyword match handed back the Code field twice and the second value overwrote the first.</p>
     */
    public String enterCodeAndRemark(String code, String remark) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, rm] = a;"
                + " const used=new Set();"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const find=(re)=>boxes.find(e=>!used.has(e)"
                + "   && re.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')+' '+labelOf(e)));"
                + " const put=(e,v)=>{ if(!e) return '(no-field)'; used.add(e); return setEl(e,v); };"
                + " const codeEl=find(/code/i);"
                + " const remEl=find(/remark|description/i);"
                + " const ng=e=>e? ' ['+(e.getAttribute('ng-model')||'?')+']' : '';"
                + " const rc=put(codeEl,c), rr=put(remEl,rm);"
                + " return 'Code='+rc+ng(codeEl)+' | Remark='+rr+ng(remEl); }",
                java.util.List.of(code, remark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("OpdWaitingArea: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code) {
        return lastEntry != null && !lastEntry.contains("(no-field)") && lastEntry.contains("Code=" + code);
    }

    /**
     * Tick the first tickable checkbox in one section, and read the tick back.
     *
     * @param sectionRe which section owns it, e.g. {@code consultation\\s*room|cabin}
     */
    /**
     * Tick the first row of one list, addressed by the row's own ng-model.
     *
     * <p>The lists are {@code Cab.IsSelected} (Consultation Room) and {@code Mod.IsSelected} (Modality) —
     * one per repeated row. Walking the DOM by heading is not enough here: the option checkboxes
     * "Unique No. For Cabin" and "Unique No. For Modality" sit right beside those headings, so a
     * section walk ticks THOSE and Save answers "Please Select At least One Cabin OR Modality!" —
     * which is exactly what happened before this was pinned.</p>
     *
     * @param model the row model, e.g. {@code Cab.IsSelected}
     */
    private String tickRow(String model, String label) {
        Object r = page.evaluate("(ng) => {" + JS
                + " const boxes=[...document.querySelectorAll("
                + "     \"input[type=checkbox][ng-model='\"+ng+\"']\")].filter(vis).filter(e=>!e.disabled);"
                + " if(!boxes.length) return '(no row with ng-model '+ng+')';"
                + " const cb=boxes[0];"
                + " if(!cb.checked){ cb.click();"
                + "   try{ const s=angular.element(cb).scope(); if(s && !s.$$phase) s.$apply(); }catch(e){} }"
                + " const row=cb.closest('tr')||cb.closest('label')||cb.parentElement;"
                + " const t=norm(row? row.textContent : '').slice(0,60);"
                + " return (cb.checked? 'ticked: ' : 'NOT ticked: ')+(t||'(unnamed)')"
                + "   +' ['+ng+'] | '+boxes.length+' rows available'; }", model);
        String s = r == null ? "" : r.toString();
        waitForAngular(700);
        System.out.println("OpdWaitingArea: " + label + " -> " + s);
        return s;
    }

    /** Consultation Room &rarr; tick a cabin ({@code Cab.IsSelected}). */
    public String tickCabin() {
        lastCabin = tickRow("Cab.IsSelected", "cabin");
        return lastCabin;
    }

    /** Modality &rarr; tick a modality ({@code Mod.IsSelected}). */
    public String tickModality() {
        lastModality = tickRow("Mod.IsSelected", "modality");
        return lastModality;
    }

    public static boolean ticked(String result) {
        return result != null && result.startsWith("ticked:");
    }

    /** A success message, judged strictly — these screens phrase refusals with the word "Added". */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /**
     * Click <b>Save</b> and return the toast.
     *
     * <p>Prefers the button carrying the form's own handler and ignores any inside a CKEditor toolbar —
     * on the E-mail Template screen the editor's own "Save" comes first in the DOM and clicking it does
     * nothing at all, silently.</p>
     */
    public String saveAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const inEditor=e=>!!e.closest('.cke, .cke_toolbox, .cke_top, .cke_inner');"
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(e=>!inEditor(e));"
                    + " const b=all.find(x=>/fnIUD|save|submit/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(!b) return '(no Save button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Save click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try {
                Object now = page.evaluate("() => {" + JS + TOAST_ELS
                        + " return toastEls().map(x=>norm(x.textContent)); }");
                if (now instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) now) {
                        String s = o.toString();
                        if (!s.isEmpty()) { toast = s; break; }
                    }
                }
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }
        if (toast.isEmpty()) {
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }
        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("OpdWaitingArea: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the code up in the screen's own grid.
     *
     * <p>Widened and scanned rather than filtered: the District screen's column filter is bound to one
     * column only, and typing a code into it emptied the grid — reporting a record that had just saved as
     * missing.</p>
     */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        try {
            page.evaluate("() => {" + JS
                    + " const onForm=[...document.querySelectorAll('input')].filter(vis)"
                    + "   .some(e=>/code/i.test(e.getAttribute('ng-model')||''));"
                    + " if(onForm){ const b=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "     .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                    + "          || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                    + "   if(b) b.click(); }"
                    + " const size=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .find(e=>/paginationPageSize/i.test(e.getAttribute('ng-model')||''));"
                    + " if(size){ size.selectedIndex=size.options.length-1;"
                    + "   size.dispatchEvent(new Event('change',{bubbles:true})); } }");
            waitForAngular(2500);
            // A new record lands at the END of this grid, so page 1 never shows it however wide the page
            // size is — jump to the last page before scanning.
            page.evaluate("() => { const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const last=[...document.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/pageLastPageClick/i.test(x.getAttribute('ng-click')||''));"
                    + " if(last) last.click(); }");
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,120) : 'not in the list ('+rows.length+' rows shown)'; }",
                    code);
            lastListCheck = "looked for \"" + code + "\" -> " + (hit == null ? "" : hit);

            // Scanning misses it when the grid pages server-side, so fall back to the column filter.
            // (Tried in this order because on the District screen the filter EMPTIES the grid, and a
            // filter-first check reported a record that had just saved as missing.)
            if (!lastListCheck.contains("FOUND:")) {
                page.evaluate("(c) => {" + JS
                        + " const f=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis)[0];"
                        + " if(f){ f.focus(); f.value=c;"
                        + "   try{ const ct=angular.element(f).controller('ngModel');"
                        + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                        + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
                waitForAngular(2500);
                Object hit2 = page.evaluate("(c) => {" + JS
                        + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                        + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                        + " const m=rows.find(t=>c && t.includes(c));"
                        + " return m? 'FOUND: '+m.slice(0,120) : 'not found after filtering either ('"
                        + "   +rows.length+' rows)'; }", code);
                lastListCheck += " | filtered: " + (hit2 == null ? "" : hit2);
            }
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("OpdWaitingArea: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
