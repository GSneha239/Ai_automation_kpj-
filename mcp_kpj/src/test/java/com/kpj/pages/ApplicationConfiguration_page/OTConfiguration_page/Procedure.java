package com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; OT Configuration &gt; <b>Item Master</b> — Page Object.
 *
 * <p>Flow: enter the <b>Service Code</b> and <b>Service Name</b> → select the <b>Group</b> and
 * <b>Sub Group</b> → tick a <b>Pricing Policy</b> in Pricing Policy Details → enter the <b>HSN Code</b>
 * → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class Procedure extends BasePage {

    public Procedure(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "", lastAdd = "";

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
     * Application Configuration &rarr; OT Configuration &rarr; Procedure.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*ot\\s*configuration\\s*$");
        lastMenu = describeMenu();

        // Anchored to the WHOLE label, and hidden anchors are included on purpose: these menu links sit in
        // the DOM before the submenu shows them. A loose match would take the first entry that merely
        // contains the words — the scaffolding trap that once ran an entire Compliance flow against
        // Complaint Type and reported PASS against the wrong screen's fields.
        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                // Item MASTER, not Item Company / Item Class — all three sit in this submenu, so the
                // match is anchored to the whole label and the siblings are rejected in onScreen().
                + "   .find(x=>/^\\s*procedure\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__prMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("Procedure.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__prMenu'); if(e) e.removeAttribute('id'); }");
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
        // The route resolving is not the screen rendering. GENMST loads its view after the hash changes,
        // and until it does the DASHBOARD's controls are still in the DOM — a run that starts typing
        // straight away puts its values into dashboard widgets (LocationName took the remark once).
        waitForScreenControls(15000);

        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    /** Wait for this screen's OWN controls — a Code box, or its grid — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const tail=e=>((e.getAttribute('ng-model')||'').split('.').pop()||'');"
                    + " const code=[...document.querySelectorAll('input,textarea')].filter(vis)"
                    + "   .some(e=>/code$/i.test(tail(e)));"
                    + " const grid=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .some(vis);"
                    + " return code || grid; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("Procedure.waitForScreenControls: no Code box or grid within "
                    + timeoutMs + "ms — the screen may not have rendered");
            return false;
        }
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
     * On the Procedure screen — matched on the hash route, never on page text.
     *
     * <p>Routes in this section bear no resemblance to their labels — the sibling "Generic" is served
     * from {@code #/GENMST} — so the route the MENU gives is authoritative and a label-derived route is
     * never assumed. Demanding that the route contain the label is what rejected the page the menu had
     * just opened on that screen.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        if (!menuRoute.isEmpty() && route.contains(menuRoute)) return true;
        // Reject the siblings outright: Item Company and Item Class live in the same submenu.
        if (route.contains("itemcompany") || route.contains("itmclsmst") || route.contains("itemclass"))
            return false;
        return route.contains("itemmaster") || route.contains("itemmst") || route.contains("item");
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
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== Procedure CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>!/colFilter|search|row\\.entity/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .some(e=>/code$/i.test(tail(e))"
                + "        || /^\\s*code\\s*$/i.test(norm(e.placeholder||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> if the entry form is not already on screen.
     *
     * <p>The requested steps do not mention Add, so this reports which of the two the screen did rather
     * than assuming either.</p>
     */
    public String lastServiceItem = "";

    /**
     * Click <b>Service Item</b> — the entry this screen offers before its Add.
     *
     * <p>The label sits on a BUTTON inside a plain wrapper div; clicking the wrapper does nothing, which
     * is how this first read as "no Add control appeared". The click NAVIGATES (to
     * {@code #/serviceItemMaster}), which destroys the page context mid-call, so the click is made
     * through a locator and judged on where the browser ended up rather than on the call returning.</p>
     */
    public String clickServiceItem() {
        String before = page.url();
        try {
            page.locator("button:has-text('Service Item'), a:has-text('Service Item')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            lastServiceItem = "(could not click Service Item: " + e.getMessage().split("\n")[0] + ")";
            System.out.println("Procedure: service item -> " + lastServiceItem);
            return lastServiceItem;
        }
        waitForAngular(3000);
        Object add = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(x=>/^\s*add\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /AddServiceItem/i.test(x.getAttribute('ng-click')||'')).length; }");
        int addControls = add instanceof Number ? ((Number) add).intValue() : 0;
        lastServiceItem = "clicked the Service Item button: " + before + " -> " + page.url()
                + " (" + addControls + " Add control(s) on screen)";
        System.out.println("Procedure: service item -> " + lastServiceItem);
        return lastServiceItem;
    }

    /** It really reached the service item screen — an Add control is there to open the form. */
    public boolean serviceItemOpened() {
        return lastServiceItem != null && lastServiceItem.startsWith("clicked")
                && !lastServiceItem.contains("(0 Add control(s)");
    }

    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?item|AddProcedure|AddItem/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__prAdd'; }");
        try {
            page.locator("#__prAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("Procedure.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__prAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastAdd = formOpen() ? "The form needed Add; it is now open." : "Add did not open a form.";
        return lastAdd;
    }

    public String lastService = "", lastGroup = "", lastSubGroup = "", lastPolicy = "", lastHsn = "";

    /**
     * Enter the <b>Service Code</b> and <b>Service Name</b>.
     *
     * <p>Told apart by the ng-model's last segment, and the Name lookup excludes the box already claimed
     * by the Code: both models are prefixed with the screen's own name, so a match on "service" finds the
     * Code box first and writes the name over it — the Area/Town defect. The HSN box is excluded here so
     * it cannot be mistaken for either.</p>
     */
    public String enterService(String code, String name) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, nm] = a;"
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')))"
                + "   .filter(e=>!/hsn/i.test(tail(e)+' '+(e.placeholder||'')));"
                + " const byTail=(re)=>boxes.find(e=>re.test(tail(e)));"
                + " const codeEl=byTail(/^(servicecode|code|itemcode)$/i) || byTail(/code$/i)"
                + "   || boxes.find(e=>/service\\s*code/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " const rest=boxes.filter(e=>e!==codeEl);"
                // The record's OWN name is ServiceItemName. This form carries a SECOND box whose model is
                // literally ServiceName — it belongs to the clinical-code row beside AddCode — and filling
                // that one leaves the record nameless, so Submit answers "Please Enter Service Name!".
                + " const notCodeRow=e=>!/^servicename$/i.test(tail(e));"
                + " const nameEl=rest.find(e=>/^serviceitemname$/i.test(tail(e)))"
                + "   || rest.filter(notCodeRow).find(e=>/^(itemname|name|description)$/i.test(tail(e)))"
                + "   || rest.filter(notCodeRow).find(e=>/(itemname|description)$/i.test(tail(e)));"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(ngOf(codeEl)||'?')+']' : '(no-service-code-field)';"
                + " const rn = nameEl? setEl(nameEl,nm)+' ['+(ngOf(nameEl)||'?')+']' : '(no-service-name-field)';"
                + " return 'ServiceCode='+rc+' | ServiceName='+rn; }",
                java.util.List.of(code, name));
        lastService = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Procedure: " + lastService);
        return lastService;
    }

    public boolean serviceEntered(String code, String name) {
        return lastService != null && !lastService.contains("(no-")
                && lastService.contains("ServiceCode=" + code)
                && lastService.contains("ServiceName=" + name);
    }

    /** Poll until a dropdown has a real option; returns how many it has. */
    private int waitForOptions(String tailRx, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int n = countOptions(tailRx);
        while (n == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(300);
            n = countOptions(tailRx);
        }
        return n;
    }

    private int countOptions(String tailRx) {
        Object n = page.evaluate("(rx) => {" + JS
                + " const re=new RegExp(rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination|colFilter/i.test(ngOf(s)))"
                + "   .find(s=>re.test(tail(s)));"
                + " return e? [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }", tailRx);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Select a dropdown by its ng-model tail, reading the choice back off the control. */
    private String pick(String tailRx, String id) {
        Object info = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const re=new RegExp(a.rx,'i');"
                + " const e=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(s=>!/pagination|colFilter/i.test(ngOf(s)))"
                + "   .find(s=>re.test(tail(s)));"
                + " if(!e) return null; e.id=a.id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length}; }",
                java.util.Map.of("rx", tailRx, "id", id));
        if (info == null) return "(no dropdown matching /" + tailRx + "/)";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) return "(no-option) [" + m.get("ng") + "]";
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options]";
    }

    /** Select the <b>Group</b>. */
    public String selectGroup() {
        waitForOptions("group", 12000);
        lastGroup = pick("^(groupid|group)$", "__prGroup");
        if (lastGroup.startsWith("(")) lastGroup = pick("^group", "__prGroup");
        System.out.println("Procedure: Group = " + lastGroup);
        return lastGroup;
    }

    /**
     * Select the <b>Sub Group</b>.
     *
     * <p>Cascades from the Group, so the list is waited for after that choice rather than read at a fixed
     * moment — reading too early is what made Postal's City look permanently empty.</p>
     */
    public String selectSubGroup() {
        waitForOptions("subgroup", 12000);
        lastSubGroup = pick("^(subgroupid|subgroup)$", "__prSub");
        if (lastSubGroup.startsWith("(")) lastSubGroup = pick("subgroup", "__prSub");
        System.out.println("Procedure: Sub Group = " + lastSubGroup);
        return lastSubGroup;
    }

    /** A value that is really set — not a placeholder, not a lookup failure. */
    public static boolean chosen(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(")
                && !v.matches("^-*\\s*[Ss]elect.*");
    }

    /**
     * Tick a checkbox in <b>Pricing Policy Details</b>.
     *
     * <p>Scoped to that section and read back afterwards. An unscoped checkbox lookup ticks whatever
     * comes first — on OPD Waiting Area that meant ticking one list twice while reporting both steps as
     * passed — and a click on a disabled box would otherwise look like a tick. If no Pricing Policy
     * section can be found the report says so rather than quietly ticking something else.</p>
     */
    public String tickPricingPolicy() {
        Object r = page.evaluate("() => {" + JS
                + " const head=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,label,div,span,th')]"
                + "   .filter(vis).filter(e=>/pricing\\s*policy/i.test(norm(e.textContent)))"
                + "   .sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length)[0];"
                + " let scope=null;"
                + " if(head){ let p=head;"
                + "   for(let i=0;i<8 && p && !scope;i++){ p=p.parentElement;"
                + "     if(p && [...p.querySelectorAll('input[type=checkbox]')].filter(vis).length) scope=p; } }"
                + " const boxes=[...(scope||document).querySelectorAll('input[type=checkbox]')].filter(vis)"
                + "   .filter(e=>!/^(all|selectall)$/i.test(tail(e)));"
                + " if(!boxes.length) return '(no checkbox in Pricing Policy Details)';"
                + " const b=boxes[0]; const before=b.checked;"
                + " if(!b.checked) b.click();"
                + " const row=b.closest('tr,.ui-grid-row,div');"
                + " const rowText=row? norm(row.textContent).slice(0,70) : '';"
                + " return (b.checked? 'ticked' : 'NOT ticked')+' ['+(ngOf(b)||'?')+'] '"
                + "   +before+' -> '+b.checked"
                + "   +(scope? '' : ' (WARNING: no Pricing Policy section found, so this was the first"
                + "     checkbox on the page)')+' | row: '+rowText; }");
        lastPolicy = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("Procedure: pricing policy -> " + lastPolicy);
        return lastPolicy;
    }

    public boolean policyTicked() {
        // A tick is only a pass when it landed inside the Pricing Policy section. Without this the
        // helper falls back to the first checkbox on the page — on the LIST screen that is a grid row's
        // Status box, which reported a pass while no pricing policy had been touched at all.
        return lastPolicy != null && lastPolicy.startsWith("ticked")
                && !lastPolicy.contains("no Pricing Policy section found");
    }

    /** Enter the <b>HSN Code</b>, pinned so it cannot take the Service Code's box. */
    public String enterHsn(String hsn) {
        Object r = page.evaluate("(v) => {" + JS
                + " const boxes=[...document.querySelectorAll('input,textarea')].filter(vis)"
                + "   .filter(e=>e.type!=='hidden' && e.type!=='checkbox' && e.type!=='radio')"
                + "   .filter(e=>!/search|colFilter|row\\.entity|pagination/i.test(ngOf(e)+' '+(e.placeholder||'')));"
                + " const el=boxes.find(e=>/hsn/i.test(tail(e)))"
                + "   || boxes.find(e=>/hsn/i.test((e.placeholder||'')+' '+labelOf(e)));"
                + " if(el) return setEl(el,v)+' ['+(ngOf(el)||'?')+']';"
                // The model does not mention HSN at all: the box carrying "HSN Code *" is bound to
                // serviceMst.TaxCode. So fall back to the LABEL and take the input nearest to it, and say
                // in the result which model was written — a name-based guess would have missed it, and
                // Submit rejects the save without it ("Please Enter HSN Code!").
                + " const hit=[...document.querySelectorAll('label,span,div,th,td')].filter(vis)"
                + "   .filter(e=>/hsn/i.test(norm(e.textContent)) && norm(e.textContent).length<60)"
                + "   .sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length)[0];"
                + " if(hit){ let host=hit;"
                + "   for(let i=0;i<5 && host;i++){ host=host.parentElement;"
                + "     const ins=host? [...host.querySelectorAll('input')].filter(vis)"
                + "         .filter(x=>x.type!=='checkbox' && x.type!=='radio') : [];"
                + "     if(ins.length) return setEl(ins[0],v)+' ['+(ngOf(ins[0])||'?')"
                + "       +', matched by the \"'+norm(hit.textContent).slice(0,20)+'\" label, not by model]'; } }"
                // Say WHERE it was looked for, and whether the screen mentions HSN at all — "field not
                // found" on its own cannot tell a mis-named selector from a field the screen lacks.
                // Where does the screen actually mention HSN? Report the nearest inputs to that text, so
                // "no HSN field" can be told from "the box is there under another name".
                + " const onPage=/hsn/i.test(document.body.innerText||'');"
                + " let near='';"
                + " const hitDiag=[...document.querySelectorAll('label,span,div,th,td')].filter(vis)"
                + "   .filter(e=>/hsn/i.test(norm(e.textContent)) && norm(e.textContent).length<60)"
                + "   .sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length)[0];"
                + " if(hitDiag){ let host=hitDiag;"
                + "   for(let i=0;i<5 && host;i++){ host=host.parentElement;"
                + "     const ins=host? [...host.querySelectorAll('input,select')].filter(vis) : [];"
                + "     if(ins.length){ near=' Nearest to the \"'+norm(hitDiag.textContent).slice(0,40)"
                + "       +'\" text: '+ins.map(e=>(ngOf(e)||e.tagName)).slice(0,5).join(', '); break; } } }"
                + " const codeish=boxes.filter(e=>/code|tax/i.test(tail(e)))"
                + "   .map(e=>ngOf(e)).slice(0,8).join(', ');"
                + " return '(no-HSN-field) — the text \"HSN\" is '+(onPage? 'PRESENT' : 'ABSENT')"
                + "   +' in the page text.'+near+' Code/tax boxes on this form: '+codeish; }", hsn);
        lastHsn = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("Procedure: HSN = " + lastHsn);
        return lastHsn;
    }

    public boolean hsnEntered(String hsn) {
        return lastHsn != null && !lastHsn.startsWith("(no-") && lastHsn.startsWith(hsn);
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

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
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
                    + "   || all.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/submit|save/i.test(x.getAttribute('ng-click')||''));"
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
                        // A real toast is a sentence. When the whole page header comes back it means the
                        // toast container matched page chrome — reporting that as "the message" made the
                        // screen look like it answered with its own title.
                        if (!s.isEmpty() && s.length() <= 200) { toast = s; break; }
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
        System.out.println("Procedure: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * remark, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String remark) {
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
        // This list opens EMPTY: it only fills once its own Search is run. Reading it straight away
        // reports "0 rows" whatever was saved.
        try {
            page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/fngetServiceItemGrid/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\s*search\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(b) b.click(); }");
            waitForAngular(4000);
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
                    + " const mine=a.v? withCode.find(t=>t.includes(a.v)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "v", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("Procedure: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
