package com.kpj.pages.ApplicationConfiguration_page.Equipment_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Equipment &gt; <b>Compliance</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Code</b> and <b>Remark</b> → select the <b>Compliance</b>
 * → <b>Submit</b> → success toast.</p>
 */
public class Compliance extends BasePage {

    public Compliance(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastCompliance = "",
            lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

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

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Equipment &rarr; Compliance.
     *
     * <p>The route is read off the menu link rather than guessed — this module spells things
     * inconsistently (its Blood Bank section has {@code RedCellSeriologyGroup} beside
     * {@code RedCellSerologyMaster}), so a hand-written route is a coin flip.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*equipment(\\s*/?\\s*asset)?\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*compliance\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__cmMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__cmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Compliance.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__cmMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen()) {
            // Fallback only — the menu href above is authoritative.
            try { page.evaluate("() => { window.location.hash = '#/ComplianceList'; }"); } catch (Exception ignore) { }
            waitForAngular(3000);
        }
        if (!onScreen()) {
            try { page.navigate(baseUrl + "#/ComplianceList"); } catch (Exception ignore) { }
            waitForAngular(3500);
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

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the Compliance screen.
     *
     * <p>The route is taken from the menu link, because labels in this module do not predict URLs — the
     * sibling "Complaint Type" is routed {@code #/ComplaintListType}. The check also rejects the Complaint
     * screens explicitly: "compliance" and "complaint" are one letter apart, and matching loosely landed a
     * whole run on Complaint Type, which reported PASS against the wrong screen's fields.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        // Reject the Complaint* screens explicitly. "compliance" and "complaint" differ by one letter, and
        // a loose match ran a whole flow against Complaint Type — reporting PASS on the wrong screen's
        // fields (ComplaintType.Code, .Period) while claiming to test Compliance.
        if (u.contains("complaint")) return false;
        return u.contains("compliance");
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
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,40)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== Compliance CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?compliance|AddCompliance/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__cmAdd'; }");
        try {
            page.locator("#__cmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("Compliance.clickAdd: " + e.getMessage());
            try { page.evaluate("() => { window.location.hash = '#/add-Compliance'; }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const e=document.getElementById('__cmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("Compliance.clickAdd: Code field never appeared"); }
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
     * Enter <b>Code</b> and <b>Remark</b>.
     *
     * <p>Fields are matched on their ng-model first and only then on placeholder/label, with search and
     * grid-filter boxes excluded, so a column filter cannot be mistaken for the Code field.</p>
     */
    public String enterDetails(String code, String remark) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, rm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')));"
                + " const pick=(re)=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''))"
                + "   || boxes.find(e=>re.test(e.placeholder||''))"
                + "   || boxes.find(e=>re.test(labelOf(e)));"
                + " const codeEl=pick(/code/i);"
                + " const remEl=pick(/remark|description/i);"
                + " const resEl=pick(/expected\\s*resolution|resolution|expected/i);"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(codeEl.getAttribute('ng-model')||'?')+']' : '(no-code-field)';"
                + " const rr = remEl? setEl(remEl,rm)+' ['+(remEl.getAttribute('ng-model')||'?')+']' : '(no-remark-field)';"
                + " return 'Code='+rc+' | Remark='+rr; }",
                java.util.List.of(code, remark));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Compliance: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code) {
        return lastEntry != null && !lastEntry.contains("(no-") && lastEntry.contains("Code=" + code);
    }

    /**
     * Select the <b>Compliance</b>.
     *
     * <p>Chosen natively so any ng-change fires, and the list is waited for — these dropdowns are filled
     * asynchronously, and a single read can land on the empty list and report "(no-option)" for a screen
     * that is merely still loading.</p>
     */
    public String selectCompliance() {
        String sel = "select";
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')]"
                    + " .filter(e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " .some(e=>[...e.options].some(o=>o.value"
                    + "   && !/^-*\\s*select/i.test((o.text||'').trim())))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("Compliance.selectCompliance: no populated dropdown within 15s");
        }

        Object info = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__cmCompliance').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(e=>!/pagination/i.test(e.getAttribute('ng-model')||''));"
                + " const e=sels.find(s=>/complian/i.test((s.getAttribute('ng-model')||'')+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__cmCompliance';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return reals.length? {index:reals[0].i, ng:(e.getAttribute('ng-model')||'?'),"
                + "   count:reals.length} : null; }");
        if (info == null) { lastCompliance = "(no-option)"; return lastCompliance; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        try {
            page.locator("#__cmCompliance").selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setIndex(((Number) m.get("index")).intValue()));
        } catch (Exception e) { lastCompliance = "(select failed: " + e.getMessage() + ")"; return lastCompliance; }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__cmCompliance');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastCompliance = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
        System.out.println("Compliance: Compliance = " + lastCompliance);
        return lastCompliance;
    }

    public boolean complianceSelected() {
        return lastCompliance != null && !lastCompliance.startsWith("(");
    }

    /**
     * A success message, judged strictly.
     *
     * <p>These configuration screens phrase refusals with the word "Added" — e.g. "… Is Already Added!" —
     * so a plain {@code contains("added")} reads a rejection as a save.</p>
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /**
     * Click <b>Submit</b> and return the toast.
     *
     * <p>Toasts are CLEARED first rather than snapshot-and-excluded, so a repeated message still reads, and
     * every call is guarded — Submit on these screens can be a real form post that reloads the page.</p>
     */
    public String submitAndGetToast() {
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
                    + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||'')"
                    + "        || /submit/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
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
            // Some screens in this module answer in a "KPJ Portal" banner that carries no toast class —
            // looking only for toastr elements reports silence for a screen that spoke clearly.
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
        System.out.println("Compliance: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the code up on the screen's own grid.
     *
     * <p>Screens in this module can answer <b>"Message Not Found."</b> — the result code has no text in the
     * message master — so the toast alone cannot say whether the record was written.</p>
     */
    public boolean codeInList(String code) {
        waitForAngular(1500);
        try {
            page.evaluate("(c) => {" + JS
                    + " const f=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")].filter(vis)[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); } }", code);
            waitForAngular(2500);
            Object hit = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const m=rows.find(t=>c && t.includes(c));"
                    + " return m? 'FOUND: '+m.slice(0,120) : 'not in the list ('+rows.length+' rows shown)'; }",
                    code);
            lastListCheck = "looked for \"" + code + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("Compliance: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
