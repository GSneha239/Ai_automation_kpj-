package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>SMS E-mail Configuration</b> — Page Object.
 *
 * <p>Flow: in <b>SMS E-mail Configuration Search</b> select the <b>Location</b> → in <b>Add SMS/E-mail
 * Configuration</b> select the <b>Event</b>, <b>SMS</b>, <b>E-mail</b> and <b>SMS E-mail To</b> →
 * <b>Save</b> → success toast.</p>
 *
 * <p>This screen carries TWO panels — a search panel and an entry panel — so every control is scoped to
 * its own panel before it is touched. An unscoped lookup takes whichever matching control comes first in
 * the DOM, which on OPD Waiting Area ticked the same list twice and reported both steps as passed.</p>
 */
public class SmsEmailConfiguration extends BasePage {

    public SmsEmailConfiguration(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastLocation = "", lastEvent = "",
            lastSms = "", lastEmail = "", lastSendTo = "", lastToast = "", lastSaveDiagnostics = "",
            lastListCheck = "", lastRoute = "", lastPanels = "";

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

    /**
     * Find the panel whose heading matches, and return its controls.
     *
     * <p>Walks up from the heading to the ancestor that actually contains form controls, so "the Add
     * panel" means the block under that heading rather than the whole page.</p>
     */
    private static final String PANEL =
            "const panelOf=(rx)=>{ const re=new RegExp(rx,'i');"
          + "  const head=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,.panel-heading,.box-header,"
          + "    .panel-title,label,div,span')].filter(vis)"
          + "    .filter(e=>re.test(norm(e.textContent)))"
          + "    .sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length)[0];"
          + "  if(!head) return null;"
          + "  let p=head;"
          + "  for(let i=0;i<8 && p;i++){"
          + "    const c=[...p.querySelectorAll('select,input,textarea')].filter(vis)"
          + "      .filter(e=>!/pagination|colFilter/i.test(ngOf(e)));"
          + "    if(c.length) return p;"
          + "    p=p.parentElement; }"
          + "  return null; };"
          + "const ctrlsIn=(p,sel)=>p? [...p.querySelectorAll(sel)].filter(vis)"
          + "    .filter(e=>!/pagination|colFilter/i.test(ngOf(e))) : [];";

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; General &rarr; SMS E-mail Configuration.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*general\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*sms\\s*e-?\\s*mail\\s*configuration\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__seMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__seMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SmsEmailConfiguration.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__seMenu'); if(e) e.removeAttribute('id'); }");
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

    /** On the SMS/E-mail Configuration screen — matched on the hash route, never on page text. */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        return (route.contains("sms") && (route.contains("mail") || route.contains("config")))
                || route.contains("smsemail") || route.contains("emailconfig");
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
                + "   else out.push(e.tagName+' \"'+(e.placeholder||labelOf(e)).slice(0,40)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,40)+'\"'); }"
                + " return out.join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== SmsEmailConfiguration CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    /** Which controls each panel owns — printed before anything is touched, so the scoping is evidenced. */
    public String describePanels() {
        Object r = page.evaluate("() => {" + JS + PANEL
                + " const show=(name,rx)=>{ const p=panelOf(rx);"
                + "   if(!p) return name+': (panel not found)';"
                + "   const c=ctrlsIn(p,'select,input,textarea')"
                + "     .map(e=>e.tagName.toLowerCase()+'['+(ngOf(e)||'?')+']');"
                + "   return name+': '+c.length+' controls -> '+c.join(', '); };"
                + " return [show('SEARCH panel','sms\\\\s*e-?mail\\\\s*configuration\\\\s*search'),"
                + "         show('ADD panel','add\\\\s*sms\\\\s*/?\\\\s*e-?mail\\\\s*configuration')].join('\\n'); }");
        lastPanels = r == null ? "" : r.toString();
        System.out.println("=== SmsEmailConfiguration PANELS ===\n" + lastPanels);
        return lastPanels;
    }

    // ---- flow ------------------------------------------------------------

    /**
     * Pick the first real option of a dropdown inside a named panel.
     *
     * <p>The list is waited for first: these dropdowns are filled asynchronously and a single read lands
     * on the empty list, reporting "(no-option)" for a screen that is merely still loading — the false
     * negative that made Postal's City look permanently empty.</p>
     *
     * @param panelRx  regex identifying the panel heading
     * @param fieldRx  regex matched against the ng-model's LAST segment, then its label
     */
    private String pickInPanel(String panelRx, String fieldRx, String id) {
        try {
            page.waitForFunction("(a) => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const re=new RegExp(a.f,'i');"
                    + " return [...document.querySelectorAll('select')].filter(vis)"
                    + "   .filter(e=>re.test(((e.getAttribute('ng-model')||'').split('.').pop()||'')))"
                    + "   .some(e=>[...e.options].some(o=>o.value"
                    + "     && !/^-*\\s*select/i.test((o.text||'').trim()))); }",
                    java.util.Map.of("f", fieldRx),
                    new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }

        Object info = page.evaluate("(a) => {" + JS + PANEL
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const p=panelOf(a.p);"
                + " const pool=p? ctrlsIn(p,'select') : [];"
                + " const re=new RegExp(a.f,'i');"
                + " const e=pool.find(s=>re.test(tail(s)))"
                + "   || pool.find(s=>re.test(ngOf(s)+' '+labelOf(s)))"
                + "   || [...document.querySelectorAll('select')].filter(vis)"
                + "        .filter(s=>!/pagination|colFilter/i.test(ngOf(s)))"
                + "        .find(s=>re.test(tail(s)));"
                + " if(!e) return null; e.id=a.id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + " return {index: reals.length? reals[0].i : -1, ng:(ngOf(e)||'?'), count:reals.length,"
                + "         scoped: !!(p && ctrlsIn(p,'select').includes(e))}; }",
                java.util.Map.of("p", panelRx, "f", fieldRx, "id", id));
        if (info == null) return "(no dropdown matching /" + fieldRx + "/ in the " + panelRx + " panel)";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) return "(no-option) [" + m.get("ng") + "]";
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        // Read the choice back off the control, so a click that did not bind cannot pass as a selection.
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return (t == null ? "" : t.toString()) + " [" + m.get("ng") + ", " + m.get("count") + " options"
                + (Boolean.TRUE.equals(m.get("scoped")) ? "" : ", NOT scoped to the panel") + "]";
    }

    /** Search panel: select the <b>Location</b> ({@code SmsEmailConfig.locationid}). */
    public String selectLocation() {
        lastLocation = pickInPanel("sms\\s*e-?mail\\s*configuration\\s*search", "^locationid$", "__seLoc");
        System.out.println("SmsEmailConfiguration: Location = " + lastLocation);
        waitForAngular(1500);
        return lastLocation;
    }

    /**
     * Add panel: select an <b>Event</b> that is not configured yet ({@code SmsEmailConfig.eventid}).
     *
     * <p>One configuration per event: saving a combination that already exists is answered "Record
     * already exist!". Always taking the first option therefore succeeds once and fails on every later
     * run — so the events already shown in the grid are skipped, and {@code nth} steps past them again
     * when a save is still refused.</p>
     *
     * @param nth 0 for the first unconfigured event, 1 for the next, and so on
     */
    public String selectEvent(int nth) {
        Object info = page.evaluate("(a) => {" + JS + PANEL
                + " document.querySelectorAll('#__seEvent').forEach(e=>e.removeAttribute('id'));"
                + " const p=panelOf(a.p);"
                + " const pool=p? ctrlsIn(p,'select') : [];"
                + " const e=pool.find(s=>/^eventid$/i.test(tail(s)));"
                + " if(!e) return null; e.id='__seEvent';"
                + " const rows=[...document.querySelectorAll('.ui-grid-row,tr')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                + " const reals=[...e.options].map((o,i)=>({t:norm(o.text),i}))"
                + "   .filter(x=>x.t && !/^-*\\s*select/i.test(x.t));"
                + " const taken=x=>rows.some(r=>r.startsWith(x.t));"
                + " const free=reals.filter(x=>!taken(x));"
                + " const pool2 = free.length? free : reals;"
                + " const pick = pool2[Math.min(a.n, pool2.length-1)];"
                + " return pick? {index:pick.i, text:pick.t, free:free.length, total:reals.length,"
                + "   skipped:reals.filter(taken).map(x=>x.t).slice(0,6).join(', ')} : null; }",
                java.util.Map.of("p", ADD_PANEL, "n", Math.max(0, nth)));
        if (info == null) { lastEvent = "(no Event dropdown in the Add panel)"; return lastEvent; }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        try {
            page.locator("#__seEvent").selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setIndex(((Number) m.get("index")).intValue()));
        } catch (Exception e) {
            lastEvent = "(select failed: " + e.getMessage().split("\n")[0] + ")";
            return lastEvent;
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__seEvent');"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }");
        lastEvent = (t == null ? "" : t.toString()) + " [SmsEmailConfig.eventid, "
                + m.get("free") + " of " + m.get("total") + " events not configured yet"
                + (String.valueOf(m.get("skipped")).isEmpty() ? "" : "; already configured: " + m.get("skipped"))
                + "]";
        System.out.println("SmsEmailConfiguration: Event = " + lastEvent);
        return lastEvent;
    }

    /**
     * Add panel: select <b>SMS</b> and <b>E-mail</b>.
     *
     * <p>Both are template dropdowns — {@code SmsEmailConfig.smstemplateid} and
     * {@code SmsEmailConfig.emailtemplateid} — not the Yes/No flags the labels suggest. Each is pinned to
     * its exact model tail: a bare "mail" matches both of them, and picking "the first match" would set
     * the SMS template twice and leave the e-mail one empty while reporting the step as passed.</p>
     */
    public String setSmsAndEmail() {
        lastSms = pickInPanel(ADD_PANEL, "^smstemplateid$", "__seSms");
        lastEmail = pickInPanel(ADD_PANEL, "^emailtemplateid$", "__seEmail");
        System.out.println("SmsEmailConfiguration: SMS = " + lastSms + " | E-mail = " + lastEmail);
        return "SMS=" + lastSms + " | E-mail=" + lastEmail;
    }

    /** Add panel: select <b>SMS E-mail To</b> ({@code SmsEmailConfig.toid}). */
    public String selectSendTo() {
        lastSendTo = pickInPanel(ADD_PANEL, "^toid$", "__seTo");
        System.out.println("SmsEmailConfiguration: SMS E-mail To = " + lastSendTo);
        return lastSendTo;
    }

    private static final String ADD_PANEL = "add\\s*sms\\s*/?\\s*e-?mail\\s*configuration";

    /** A value that is really set — not a placeholder, not a lookup failure. */
    public static boolean chosen(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(")
                && !v.matches("^-*\\s*[Ss]elect.*") && !v.startsWith("NOT ticked");
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
     * Click <b>Save</b> and return the toast.
     *
     * <p>CKEditor's own toolbar carries a Save button with {@code ng-click="-"}; clicking it does nothing
     * and looks like a screen that will not save, so editor chrome is excluded and an {@code fnIUD}
     * handler is preferred.</p>
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
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(save|submit)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/save|submit/i.test(x.getAttribute('ng-click')||''));"
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
        System.out.println("SmsEmailConfiguration: save -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the configured event up in the screen's own grid.
     *
     * <p>A grid here can carry one {@code colFilter.term} box per column, all sharing that ng-model, so
     * the search is reported with the columns it ran against — "0 rows" on its own cannot tell an absent
     * record from a term typed into the wrong column.</p>
     */
    public boolean rowInList(String... values) {
        waitForAngular(2000);
        // Match the WHOLE row, not just the event. This screen already holds rows for common events, so an
        // event-only match can find a configuration that was there before the run and call it proof of the
        // save — exactly the false pass a 5-digit postal code produced on the Postal screen.
        java.util.List<String> needles = new java.util.ArrayList<>();
        for (String v : values) {
            if (v == null) continue;
            String n = v.replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
            if (!n.isEmpty() && !n.startsWith("(")) needles.add(n);
        }
        try {
            Object hit = page.evaluate("(ns) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const norml=s=>norm(s).replace(/\\u00a0/g,' ');"
                    + " const m=ns.length? rows.find(t=>ns.every(n=>norml(t).includes(norml(n)))) : null;"
                    + " const partial=ns.length? rows.filter(t=>norml(t).includes(norml(ns[0]))) : [];"
                    + " return (m? 'FOUND: '+m.slice(0,160)"
                    + "          : (partial.length"
                    + "             ? 'rows exist for \"'+ns[0]+'\" but none carries every value chosen ('"
                    + "               +partial.map(t=>t.slice(0,60)).join(' / ')+')'"
                    + "             : 'not in the list ('+rows.length+' rows shown)'))"
                    + "        +'  [columns: '+(heads||'none')+']'; }", needles);
            lastListCheck = "looked for a row carrying " + needles + " -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck = "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("SmsEmailConfiguration: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
