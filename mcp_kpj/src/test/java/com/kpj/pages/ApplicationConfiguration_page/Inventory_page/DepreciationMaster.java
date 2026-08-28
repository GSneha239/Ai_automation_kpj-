package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>Depreciation Master</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Depreciation Code</b> and <b>Depreciation Description</b> →
 * select the <b>Depreciation Name</b> → type a <b>numeric value</b> and click <b>add numeric value</b> →
 * click the <b>mathematical symbols</b> to build the formula → <b>Submit</b> → success toast.</p>
 *
 * <p>The formula builder is the interesting part: it is a calculator-style pad, so what matters is not
 * that the buttons were clicked but that the formula box grew each time. Every click is therefore read
 * back against the formula's own text, and the sequence is reported, so a pad that swallows a click shows
 * up instead of passing.</p>
 */
public class DepreciationMaster extends BasePage {

    public DepreciationMaster(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastName = "",
            lastNumeric = "", lastFormula = "", lastFormulaSteps = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model tail, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
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
     * Application Configuration &rarr; Inventory &rarr; Depreciation Master.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*inventory\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*depreciation\\s*master\\s*$/i.test(norm(x.textContent)))"
                + "   || [...document.querySelectorAll('a[href]')]"
                + "        .find(x=>/^\\s*depreciation\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__dmMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__dmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DepreciationMaster.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__dmMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3000);
            if (!onScreen()) {
                try { page.navigate(baseUrl + hash); } catch (Exception ignore) { }
                waitForAngular(3500);
            }
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

    /** On the Depreciation Master screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        return route.contains("depreciation");
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,24)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,26)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,40)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== DepreciationMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        if (formOpen()) return true;
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?depreciation|AddDepreciation/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__dmAdd'; }");
        try {
            page.locator("#__dmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("DepreciationMaster.clickAdd: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__dmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && !/colFilter|row\\.entity/i.test(e.getAttribute('ng-model')||'')"
                    + " && /code/i.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')"
                    + "                  +' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("DepreciationMaster.clickAdd: Code field never appeared");
        }
        waitForAngular(900);
        return formOpen();
    }

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*(depreciation\\s*)?code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Enter the <b>Depreciation Code</b> and <b>Depreciation Description</b>.
     *
     * <p>Told apart by the ng-model's last segment: every model here is prefixed with the screen's own
     * name, so a match on "depreciation" finds the Code box first and writes the description over it.</p>
     */
    public String enterDetails(String code, String description) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, d] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^code$/i) || byTail(/code$/i)"
                + "   || boxes.find(e=>/^\\s*(depreciation\\s*)?code\\s*$/i.test(norm(e.placeholder||'')));"
                + " const rest=boxes.filter(e=>e!==codeEl);"
                + " const descEl=rest.find(e=>/^(description|name|remark)$/i.test(tail(e)))"
                + "   || rest.find(e=>/(description|remark)$/i.test(tail(e)))"
                + "   || rest.find(e=>/description|remark/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-code-field)';"
                + " const rd = descEl? setEl(descEl,d)+' ['+(ngOf(descEl)||'?')+']' : '(no-description-field)';"
                + " return 'Code='+rc+' | Description='+rd; }",
                java.util.List.of(code, description));
        lastEntry = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("DepreciationMaster: " + lastEntry);
        return lastEntry;
    }

    public boolean detailsEntered(String code, String description) {
        return lastEntry != null && !lastEntry.contains("(no-")
                && lastEntry.contains("Code=" + code) && lastEntry.contains("Description=" + description);
    }

    /**
     * Select the <b>Depreciation Name</b>.
     *
     * <p>The list is waited for rather than sampled once — these dropdowns fill asynchronously, and one
     * early read reports "(no-option)" for a screen that is merely still loading.</p>
     */
    public String selectDepreciationName() { return selectDepreciationName(0); }

    /**
     * Select the <b>Depreciation Name</b> by position.
     *
     * @param nth 0 for the first real option — varying it is what proves whether the formula is taking
     *            the number that was typed or this dropdown's underlying value
     */
    public String selectDepreciationName(int nth) {
        long end = System.currentTimeMillis() + 12000;
        int n = 0;
        while (n == 0 && System.currentTimeMillis() < end) {
            Object c = page.evaluate("() => {" + JS
                    + " const e=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .filter(s=>!/pagination/i.test(ngOf(s)))[0];"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }");
            n = c instanceof Number ? ((Number) c).intValue() : 0;
            if (n == 0) page.waitForTimeout(400);
        }

        Object info = page.evaluate("(n) => {" + JS
                + " document.querySelectorAll('#__dmName').forEach(e=>e.removeAttribute('id'));"
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination/i.test(ngOf(s)));"
                + " const e=sels.find(s=>/name$/i.test(tail(s)))"
                + "   || sels.find(s=>/depreciation|name/i.test(ngOf(s)+' '+labelOf(s)))"
                + "   || sels[0];"
                + " if(!e) return null; e.id='__dmName';"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " const pick=reals[Math.min(Math.max(n,0), reals.length-1)];"
                + " return pick? {index:pick.i, ng:(ngOf(e)||'?'), count:reals.length,"
                + "   optionValue:String(pick.o.value)} : null; }", Math.max(0, nth));
        if (info == null) { lastName = "(no depreciation name dropdown)"; return lastName; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) { lastName = "(no-option) [" + m.get("ng") + "]"; return lastName; }
        try {
            page.locator("#__dmName").selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            lastName = "(select failed: " + e.getMessage().split("\n")[0] + ")";
            return lastName;
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__dmName');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastName = (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count")
                + " options, option value=" + m.get("optionValue") + "]";
        System.out.println("DepreciationMaster: Depreciation Name = " + lastName);
        return lastName;
    }

    public boolean nameSelected() {
        return lastName != null && !lastName.isEmpty() && !lastName.startsWith("(")
                && !lastName.matches("^-*\\s*[Ss]elect.*");
    }

    /**
     * The formula as the screen currently shows it.
     *
     * <p>Read from the formula box ALONE — {@code DepreciationMst.ClearFormula}. Matching "formula"
     * loosely also catches {@code FormulaNumericValue} and {@code FormulaDepreciationName}, and joining
     * those together produced a "formula" that mixed the typed number in with the real one.</p>
     */
    public String formulaText() {
        Object r = page.evaluate("() => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/numericvalue|depreciationname|colFilter|search/i.test(ngOf(e)));"
                + " const el=boxes.find(e=>/^(clearformula|formula|depreciationformula)$/i.test(tail(e)))"
                + "   || boxes.find(e=>e.tagName==='TEXTAREA');"
                + " if(el) return el.value;"
                + " const d=[...document.querySelectorAll('div,span,p,label')].filter(vis)"
                + "   .find(e=>/formula/i.test(norm(e.textContent)) && norm(e.textContent).length<80);"
                + " return d? norm(d.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** What {@link #clickAddDepreciation()} last did, for the report. */
    public String lastAddDepreciation = "";

    /**
     * Click <b>Add Depreciation</b> ({@code funSetDepreciationName()}) to put the selected depreciation
     * name into the formula.
     *
     * <p>The formula is read before and after: the step is judged on the formula having grown, not on the
     * click having happened. Pinned to the handler rather than the button text, because "Add Depreciation"
     * and "Add Numeric" sit side by side and a loose "Add" match takes whichever comes first.</p>
     */
    public String clickAddDepreciation() {
        String before = formulaText();
        Object r = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/funSetDepreciationName/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "      .find(x=>/^\\s*add\\s+depreciation\\s*$/i.test(norm(x.textContent)||x.value||''));"
                + " if(!b) return '(no \"Add Depreciation\" button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        waitForAngular(800);
        String after = formulaText();
        lastAddDepreciation = (r == null ? "" : r.toString())
                + " | formula \"" + before + "\" -> \"" + after + "\"";
        System.out.println("DepreciationMaster: add depreciation -> " + lastAddDepreciation);
        return lastAddDepreciation;
    }

    /** The depreciation name reached the formula — judged on the formula, not on the click. */
    public boolean depreciationAdded() {
        if (lastAddDepreciation == null || lastAddDepreciation.contains("(no ")) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("formula \"([^\"]*)\" -> \"([^\"]*)\"").matcher(lastAddDepreciation);
        return m.find() && !m.group(1).equals(m.group(2));
    }

    /**
     * Type the numeric value and click <b>add numeric value</b>.
     *
     * <p>The formula is read before and after, so the step is judged on the formula having grown rather
     * than on the click having happened.</p>
     */
    public String addNumericValue(String value) {
        String before = formulaText();

        // TYPE the number rather than assigning it. A JS assignment plus $setViewValue put only the
        // first digit into the formula ("10" arrived as "1"), so the box is filled through the browser's
        // own input the way a person fills it.
        Object tagged = page.evaluate("() => {" + JS
                + " document.querySelectorAll('#__dmNum').forEach(e=>e.removeAttribute('id'));"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const el=boxes.find(e=>/numericvalue$/i.test(tail(e)))"
                + "   || boxes.find(e=>/numeric|value|number|digit/i.test(tail(e)+' '+(e.placeholder||'')+' '+labelOf(e)))"
                + "   || boxes.find(e=>e.type==='number');"
                + " if(!el) return ''; el.id='__dmNum'; return ngOf(el)||'?'; }");
        String numModel = tagged == null ? "" : tagged.toString();
        String typed;
        if (numModel.isEmpty()) {
            typed = "(no numeric field)";
        } else {
            try {
                page.locator("#__dmNum").fill(value,
                        new com.microsoft.playwright.Locator.FillOptions().setTimeout(6000));
            } catch (Exception e) { System.out.println("DepreciationMaster.numeric: " + e.getMessage().split("\n")[0]); }
            // Read the box AND the Angular model, so "the app dropped a digit" can be told apart from
            // "the typing never bound".
            Object read = page.evaluate("() => { const e=document.getElementById('__dmNum');"
                    + " if(!e) return '';"
                    + " let model='(unread)';"
                    + " try{ const ng=e.getAttribute('ng-model')||'', root=ng.split('.')[0], key=ng.split('.').pop();"
                    + "      const o=angular.element(e).scope()[root]; if(o) model=JSON.stringify(o[key]); }catch(err){}"
                    + " return 'box=\"'+e.value+'\" model='+model; }");
            typed = (read == null ? "" : read.toString()) + " [" + numModel + "]";
        }

        Object r = page.evaluate("(t) => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/funSetNumeric/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "      .find(x=>/add\\s*numeric|addnumeric|add\\s*value/i.test("
                + "          norm(x.textContent)+' '+(x.value||'')));"
                + " if(!b) return t+' | (no \"add numeric value\" button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return t+' | clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                + "   +(b.getAttribute('ng-click')||'-')+']'; }", typed);
        waitForAngular(800);
        String after = formulaText();
        lastNumeric = (r == null ? "" : r.toString())
                + " | formula \"" + before + "\" -> \"" + after + "\"";
        System.out.println("DepreciationMaster: numeric -> " + lastNumeric);
        return lastNumeric;
    }

    /** The numeric value reached the formula — judged on the formula, not on the click. */
    public boolean numericAdded(String value) {
        String after = formulaText();
        return lastNumeric != null && !lastNumeric.contains("(no ")
                && after != null && after.contains(value);
    }

    /**
     * Click the mathematical symbols in turn, checking after each one that the formula grew.
     *
     * <p>Reported symbol by symbol: a calculator pad that silently ignores one operator is exactly the
     * kind of thing a single end-of-flow check would miss.</p>
     */
    public String clickSymbols(String... symbols) {
        StringBuilder sb = new StringBuilder();
        for (String s : symbols) {
            String before = formulaText();
            // Only real buttons, and the handler is named in the report. Matching any element whose text
            // is ")" also matches spans and layout divs that merely contain the character, which "clicks"
            // something that was never the pad's key.
            Object r = page.evaluate("(sym) => {" + JS
                    + " const HANDLERS={'(':'funSetRightparenthesis',')':'funSetLeftparenthesis',"
                    + "   '+':'funSetPlusSign','-':'funSetminusSign','*':'funSetMulSign',"
                    + "   '/':'funSetDivSign','^':'funSetSeqSign'};"
                    + " const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis);"
                    + " const want=HANDLERS[sym];"
                    + " const b=(want? btns.find(x=>(x.getAttribute('ng-click')||'').includes(want)) : null)"
                    + "   || btns.find(x=>norm(x.textContent)===sym || (x.value||'').trim()===sym);"
                    + " if(!b) return '(no button for \"'+sym+'\")';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked ['+(b.getAttribute('ng-click')||'-')+']'; }", s);
            waitForAngular(600);
            String after = formulaText();
            boolean grew = after != null && !after.equals(before);
            sb.append(sb.length() == 0 ? "" : "; ").append("\"").append(s).append("\" ")
              .append(r == null ? "" : r.toString())
              .append(grew ? " -> formula now \"" + after + "\"" : " -> FORMULA UNCHANGED (\"" + after + "\")");
        }
        lastFormulaSteps = sb.toString();
        lastFormula = formulaText();
        System.out.println("DepreciationMaster: symbols -> " + lastFormulaSteps);
        return lastFormulaSteps;
    }

    /** Every symbol clicked actually changed the formula. */
    public boolean formulaBuilt() {
        return lastFormulaSteps != null && !lastFormulaSteps.isEmpty()
                && !lastFormulaSteps.contains("(no button")
                && !lastFormulaSteps.contains("FORMULA UNCHANGED");
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
     * <p>The formula pad's own buttons are excluded: several of them are plain {@code <button>} elements
     * and one careless "any button" match would press an operator instead of Submit.</p>
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
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent)||x.value||''));"
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
                        + "|added successfully|updated successfully|message not found|please (enter|select|add))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "");
        lastToast = toast;
        System.out.println("DepreciationMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * description, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String description) {
        waitForAngular(1500);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.d? withCode.find(t=>t.includes(a.d)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "d", description == null ? "" : description));
            lastListCheck += "looked for \"" + code + "\" + \"" + description + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("DepreciationMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
