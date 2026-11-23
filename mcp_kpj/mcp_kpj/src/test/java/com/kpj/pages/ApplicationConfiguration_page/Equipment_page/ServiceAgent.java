package com.kpj.pages.ApplicationConfiguration_page.Equipment_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Equipment &gt; <b>Service Agent</b> — Page Object.
 *
 * <p>Flow: list → <b>Add</b> → enter <b>Agent Code</b> and <b>Agent Name</b> → in the <b>Contact Details</b>
 * block enter Name, Telephone Number, Designation and Cell → inner <b>Add</b> (puts the contact in the
 * grid) → <b>Submit</b> → success toast.</p>
 */
public class ServiceAgent extends BasePage {

    public ServiceAgent(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastAgent = "", lastContact = "",
            lastAddRow = "", lastToast = "", lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "";

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
     * Application Configuration &rarr; Equipment &rarr; Service Agent.
     *
     * <p>The route is taken from the menu link, never guessed: labels in this module do not predict URLs —
     * "Complaint Type" is routed {@code #/ComplaintListType} — so a hand-written route lands on the
     * dashboard.</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        clickMenu("^\\s*application\\s*configuration\\s*$");
        clickMenu("^\\s*equipment(\\s*/?\\s*asset)?\\s*$");
        lastMenu = describeMenu();

        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*service\\s*agent\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__saMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__saMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ServiceAgent.nav: " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__saMenu'); if(e) e.removeAttribute('id'); }");
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

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the Service Agent screen.
     *
     * <p>Checked against the route the MENU gave, rather than a guessed keyword. A loose check on a sibling
     * screen once let a whole run pass against the wrong page, so anything mentioning the Complaint screens
     * is rejected outright.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "");
        if (u.contains("complaint") || u.contains("compliance")) return false;
        if (!lastRoute.isEmpty()) {
            String r = lastRoute.toLowerCase().replace("#/", "").replace(" ", "");
            if (!r.isEmpty() && u.contains(r)) return true;
        }
        return u.contains("serviceagent");
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
        System.out.println("=== ServiceAgent CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** Click <b>Add</b> and wait for the entry form to render. */
    public boolean clickAdd() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?serviceagent|AddServiceAgent/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__saAdd'; }");
        try {
            page.locator("#__saAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("ServiceAgent.clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__saAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,textarea')].some(e=>"
                    + " (e.offsetWidth||e.offsetHeight||e.getClientRects().length)"
                    + " && /code/i.test((e.getAttribute('ng-model')||'')+' '+(e.placeholder||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("ServiceAgent.clickAdd: Code field never appeared"); }
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
     * Enter the <b>Agent Code</b> and <b>Agent Name</b>.
     *
     * <p>Pinned to {@code ServiceAgent.Code} / {@code ServiceAgent.Name} for the same reason the contact
     * fields are: the form carries an agent Name and a contact Name, and a keyword match cannot tell them
     * apart.</p>
     */
    public String enterAgentDetails(String code, String name) {
        Object r = page.evaluate("(a) => {" + JS
                + " const [c, n] = a;"
                + " const byModel=(ng)=>[...document.querySelectorAll("
                + "     \"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(vis);"
                + " const codeEl=byModel('ServiceAgent.Code'), nameEl=byModel('ServiceAgent.Name');"
                + " const rc = codeEl? setEl(codeEl,c)+' ['+(codeEl.getAttribute('ng-model')||'?')+']' : '(no-agent-code-field)';"
                + " const rn = nameEl? setEl(nameEl,n)+' ['+(nameEl.getAttribute('ng-model')||'?')+']' : '(no-agent-name-field)';"
                + " return 'AgentCode='+rc+' | AgentName='+rn; }", java.util.List.of(code, name));
        lastAgent = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ServiceAgent: " + lastAgent);
        return lastAgent;
    }

    public boolean agentEntered(String code) {
        return lastAgent != null && !lastAgent.contains("(no-") && lastAgent.contains("AgentCode=" + code);
    }

    /**
     * Fill the <b>Contact Details</b> block: Name, Telephone Number, Designation and Cell.
     *
     * <p>Each field is addressed by its own ng-model where one exists. The agent block above has its own
     * Name, so a bare "name" match would fill that instead and leave the contact empty — the contact's
     * fields are therefore matched on contact-specific models first.</p>
     */
    public String enterContactDetails(String name, String telephone, String designation, String cell) {
        // Pinned to the Contact.* models. The agent block above carries ServiceAgent.Name and
        // ServiceAgent.TelephoneNo, and a keyword match on "name" or "telephone" finds THOSE first — which
        // filled the agent's phone, left the contact's empty, and made Add a silent no-op followed by
        // "Please add the Contact Details!" at Submit.
        Object r = page.evaluate("(a) => {" + JS
                + " const [n, tel, des, cel] = a;"
                + " const byModel=(ng)=>[...document.querySelectorAll("
                + "     \"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"'],select[ng-model='\"+ng+\"']\")]"
                + "   .find(vis);"
                + " const put=(el,v)=>{ if(!el) return '(no-field)';"
                + "   if(el.tagName==='SELECT'){"
                + "     const i=[...el.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.text)));"
                + "     if(i<0) return '(no-option)';"
                + "     el.selectedIndex=i;"
                + "     try{ const c=angular.element(el).controller('ngModel');"
                + "          if(c){ c.$setViewValue(el.value); c.$render(); } }catch(e){}"
                + "     el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     return norm(el.options[i].text); }"
                + "   return setEl(el, v); };"
                + " const nameEl=byModel('Contact.Name'), telEl=byModel('Contact.TelephoneNo'),"
                + "       desEl=byModel('Contact.Designation'), celEl=byModel('Contact.Cell');"
                + " const rn=put(nameEl,n), rt=put(telEl,tel), rd=put(desEl,des), rc=put(celEl,cel);"
                + " const ng=e=>e? ' ['+(e.getAttribute('ng-model')||'?')+']' : '';"
                + " return 'Name='+rn+ng(nameEl)+' | Telephone='+rt+ng(telEl)"
                + "   +' | Designation='+rd+ng(desEl)+' | Cell='+rc+ng(celEl); }",
                java.util.List.of(name, telephone, designation, cell));
        lastContact = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("ServiceAgent: contact -> " + lastContact);
        return lastContact;
    }

    public boolean contactEntered() {
        return lastContact != null && !lastContact.contains("(no-field)") && !lastContact.contains("(no-option)");
    }

    /** Rows currently in the contact grid. */
    public int contactRows() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('table tbody tr,.ui-grid-row')].filter(vis)"
                + "   .filter(t=>norm(t.textContent) && t.querySelectorAll('td,.ui-grid-cell').length>0).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Click the inner <b>Add</b> that puts the contact in the grid, and confirm a row appeared.
     *
     * <p>Verified by the row count and by finding the entered name in it — a click that "worked" proves
     * nothing on these screens, where Add is a silent no-op when a field is missing.</p>
     */
    public String clickAddContact(String expectName) {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                + "   .forEach(t=>t.remove()); }");
        int before = contactRows();
        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        && !/submit/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no inner Add button)';"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " return 'clicked [ng-click='+(b.getAttribute('ng-click')||'-')+']'; }");
        waitForAngular(2000);
        int after = contactRows();
        Object shown = page.evaluate("(n) => {" + JS
                + " const rows=[...document.querySelectorAll('table tbody tr,.ui-grid-row')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                + " const hit=rows.find(t=>n && t.includes(n));"
                + " return hit? hit.slice(0,110) : ''; }", expectName);
        String hit = shown == null ? "" : shown.toString();
        Object msg = page.evaluate("() => {" + JS + TOAST_ELS
                + " return toastEls().map(x=>norm(x.textContent)).join(' | '); }");
        String toast = msg == null ? "" : msg.toString();
        lastAddRow = (clicked == null ? "" : clicked.toString()) + " | rows " + before + " -> " + after
                + (hit.isEmpty() ? " | the contact is NOT in the grid" : " | added row: \"" + hit + "\"")
                + (toast.isEmpty() ? "" : " | msg=" + toast);
        System.out.println("ServiceAgent: add contact -> " + lastAddRow);
        return lastAddRow;
    }

    public boolean contactRowAdded() {
        return lastAddRow != null && lastAddRow.contains("added row:");
    }

    /** A success message, judged strictly — these screens phrase refusals with the word "Added". */
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
            // Screens in this module can answer in a banner that carries no toast class.
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
        System.out.println("ServiceAgent: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the saved agent up on the screen's own grid — the toast alone is not proof of a write.
     *
     * <p>Two things this gets right that the previous version did not. The grid carries one
     * {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking the first one filters
     * whichever column happens to come first and empties the grid — that is what "0 rows shown" meant on
     * Postal. And the row must carry the code AND the agent name: a code alone can match a record that
     * already existed, which is how a generated 5-digit value once "proved" a save on Postal.</p>
     *
     * @param code the agent code just submitted
     * @param name the agent name just submitted, matched in the same row
     */
    public boolean codeInList(String code, String name) {
        waitForAngular(1500);
        // A refused Submit leaves the entry form on screen, where there is no grid to read at all.
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
                    + " return 'rowsBeforeFilter='+rows+', filterBoxes='+boxes.length+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.n? withCode.find(t=>t.includes(a.n)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "n", name == null ? "" : name));
            lastListCheck += "looked for \"" + code + "\" + \"" + name + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("ServiceAgent: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
