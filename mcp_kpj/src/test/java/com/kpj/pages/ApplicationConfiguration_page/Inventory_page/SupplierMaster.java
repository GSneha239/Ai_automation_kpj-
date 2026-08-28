package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Inventory &gt; <b>Supplier Master</b> — Page Object.
 *
 * <p>Flow: enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class SupplierMaster extends BasePage {

    public SupplierMaster(Page page) { super(page); }

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
     * Application Configuration &rarr; Inventory &rarr; SupplierMaster.
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

        // Anchored to the WHOLE label, and hidden anchors are included on purpose: these menu links sit in
        // the DOM before the submenu shows them. A loose match would take the first entry that merely
        // contains the words — the scaffolding trap that once ran an entire Compliance flow against
        // Complaint Type and reported PASS against the wrong screen's fields.
        Object href = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[href]')]"
                + "   .find(x=>/^\\s*supplier\\s*master\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__smMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__smMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SupplierMaster.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__smMenu'); if(e) e.removeAttribute('id'); }");
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
            System.out.println("SupplierMaster.waitForScreenControls: no Code box or grid within "
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
     * On the SupplierMaster screen — matched on the hash route, never on page text.
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
        return route.contains("supplier");
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
        System.out.println("=== SupplierMaster CONTROLS ===\n" + lastControls);
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
    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?supplier|AddSupplier/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__smAdd'; }");
        try {
            page.locator("#__smAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("SupplierMaster.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__smAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        // The entry form is rendered asynchronously: on a slow response the Code box does not exist yet,
        // and typing into a form that is not there reads back "(no field)". Wait for the Code box itself.
        long deadline = System.currentTimeMillis() + 20000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("() => {" + JS
                    + " return [...document.querySelectorAll(\"[ng-model='payable.PayableCode']\")]"
                    + "   .filter(vis).length; }");
            if (n instanceof Number && ((Number) n).intValue() > 0) { ready = true; break; }
            page.waitForTimeout(300);
        }
        lastAdd = formOpen()
                ? "The form needed Add; it is now open"
                  + (ready ? " and its Code box has rendered." : " but its Code box never rendered (20s).")
                : "Add did not open a form.";
        return lastAdd;
    }

    public String lastTitle = "", lastPayableType = "", lastVendorType = "";
    public String lastCode = "", lastName = "", lastTab = "", lastContact = "";

    /** Every model on this form is prefixed {@code payable.} — pinned exactly, never by keyword. */
    private static final String M = "payable.";

    /** Wait for a dropdown to hold a real option — these lists load asynchronously. */
    private void waitForOptions(String model, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object n = page.evaluate("(m) => {" + JS
                    + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                    + " return e? [...e.options].filter(o=>o.value"
                    + "   && !/^-*\s*select/i.test(norm(o.text))).length : 0; }", model);
            if (n instanceof Number && ((Number) n).intValue() > 0) return;
            page.waitForTimeout(300);
        }
    }

    /** Select a dropdown by its EXACT ng-model, reading the choice back off the control. */
    private String pickExact(String model, String id) { return pickExact(model, id, ""); }

    /**
     * As {@link #pickExact(String, String)}, but preferring the first option whose text matches
     * {@code preferRx}, falling back to the first real option when nothing matches.
     *
     * <p>Needed where the first option in the list is not a neutral one: this screen's Title list opens
     * on "Baby of", and DevHIS derives the supplier's gender from the title prefix.</p>
     */
    private String pickExact(String model, String id, String preferRx) {
        waitForOptions(model, 12000);
        Object info = page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+a.m+\"']\")].filter(vis)[0];"
                + " if(!e) return null; e.id=a.id;"
                + " const reals=[...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\s*select/i.test(norm(x.o.text)));"
                + " let pick = reals.length? reals[0] : null;"
                + " if(a.rx){ const rx=new RegExp(a.rx,'i');"
                + "   const p=reals.find(x=>rx.test(norm(x.o.text))); if(p) pick=p; }"
                + " return {index: pick? pick.i : -1, count:reals.length}; }",
                java.util.Map.of("m", model, "id", id, "rx", preferRx));
        if (info == null) return "(no dropdown " + model + ")";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) info;
        int index = ((Number) m.get("index")).intValue();
        if (index < 0) return "(no-option) [" + model + "]";
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(800);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return (t == null ? "" : t.toString()) + " [" + model + ", " + m.get("count") + " options]";
    }

    /** A value that is really set — not a placeholder, not a lookup failure. */
    public static boolean chosen(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(")
                && !v.matches("^-*\s*[Ss]elect.*");
    }

    /** Write a value to an EXACT model and read it back. */
    private String put(String model, String value) {
        Object r = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.m+\"']\")].filter(vis)"
                + "   .filter(x=>x.tagName==='INPUT'||x.tagName==='TEXTAREA')[0];"
                + " return e? setEl(e,a.v)+' ['+a.m+']' : '(no field '+a.m+')'; }",
                java.util.Map.of("m", model, "v", value));
        return r == null ? "" : r.toString();
    }

    /** Enter the supplier <b>Code</b> ({@code payable.PayableCode}). */
    public String enterCode(String code) {
        lastCode = put(M + "PayableCode", code);
        waitForAngular(400);
        System.out.println("SupplierMaster: Code = " + lastCode);
        return lastCode;
    }

    public boolean codeEntered(String code) {
        return lastCode != null && !lastCode.startsWith("(no field") && lastCode.startsWith(code);
    }

    /**
     * Select the <b>Title</b> ({@code payable.PrefixId}) — the prefix beside the name.
     *
     * <p>A GENDERED title is chosen (Mr/Ms/Mrs/Miss/Encik/Puan) rather than simply the first option in
     * the list. The list opens on "Baby of", which carries no gender, and the save then rejects the
     * record with "Please Select Gender!" — the screen has no gender control of its own, so the gender
     * can only arrive through this prefix.</p>
     */
    public String selectTitle() {
        lastTitle = pickExact(M + "PrefixId", "__smTitle",
                "^(mr|ms|mrs|miss|madam|encik|puan|cik|tuan)\\.?$");
        System.out.println("SupplierMaster: Title = " + lastTitle);
        return lastTitle;
    }

    /** Enter the <b>Name</b> ({@code payable.FirstName}). */
    public String enterName(String name) {
        lastName = put(M + "FirstName", name);
        waitForAngular(400);
        System.out.println("SupplierMaster: Name = " + lastName);
        return lastName;
    }

    public boolean nameEntered(String name) {
        return lastName != null && !lastName.startsWith("(no field") && lastName.startsWith(name);
    }

    /** Select the <b>Payable Type</b> ({@code payable.PayableTypeID}). */
    public String selectPayableType() {
        lastPayableType = pickExact(M + "PayableTypeID", "__smPayable");
        System.out.println("SupplierMaster: Payable Type = " + lastPayableType);
        return lastPayableType;
    }

    /**
     * Select the <b>Vendor Type</b> ({@code payable.SupplierTypeID}).
     *
     * <p>Note the model says Supplier while the label says Vendor — pinned to the model, since a match on
     * "vendor" finds nothing and a match on "supplier" would also hit the screen's own prefix.</p>
     */
    public String selectVendorType() {
        lastVendorType = pickExact(M + "SupplierTypeID", "__smVendor");
        System.out.println("SupplierMaster: Vendor Type = " + lastVendorType);
        return lastVendorType;
    }

    /**
     * Open the <b>Personal Information</b> tab.
     *
     * <p>The tab is a plain {@code <li>}/{@code <a>} with no ng-click, and its fields are ALREADY in the
     * DOM before it is opened — so the click is reported for fidelity to the steps, and the fields are
     * then verified on their own models rather than on the tab having been clicked.</p>
     */
    public String openPersonalInformationTab() {
        Object r = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('a,li,button,span')].filter(vis)"
                + "   .find(e=>/^\s*personal\s*information\s*$/i.test(norm(e.textContent)));"
                + " if(!t) return '(no Personal Information tab)';"
                + " t.click();"
                + " const cls=(t.className||'')+' '+((t.parentElement||{}).className||'');"
                + " return 'clicked the '+t.tagName+' tab'+(/active/i.test(cls)? ' (now active)' : ''); }");
        lastTab = r == null ? "" : r.toString();
        waitForAngular(1200);
        System.out.println("SupplierMaster: Personal Information tab -> " + lastTab);
        return lastTab;
    }

    public boolean tabOpened() {
        return lastTab != null && lastTab.startsWith("clicked");
    }

    /**
     * Enter the contact block: <b>Acc Ledger Name</b>, <b>Email ID</b>, <b>Telephone No</b> and
     * <b>Mobile No</b>, each written to its own model and read back.
     *
     * <p>Telephone and Mobile are separate models ({@code TelNo} / {@code MobileNo}) — a keyword match on
     * "no" or "phone" would fill one twice and leave the other empty while both looked entered.</p>
     */
    public String enterContactBlock(String ledger, String email, String tel, String mobile) {
        String r1 = put(M + "AccLedgerName", ledger);
        String r2 = put(M + "EmailId", email);
        String r3 = put(M + "TelNo", tel);
        String r4 = put(M + "MobileNo", mobile);
        lastContact = "AccLedgerName=" + r1 + " | EmailId=" + r2 + " | TelNo=" + r3 + " | MobileNo=" + r4;
        waitForAngular(500);
        System.out.println("SupplierMaster: " + lastContact);
        return lastContact;
    }

    /** Every named field read its own value back. */
    public boolean contactEntered(String... namesAndValues) {
        if (lastContact == null || lastContact.contains("(no field")) return false;
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (!lastContact.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) return false;
        }
        return true;
    }

    /** Which named fields did NOT take their value. */
    public String contactMissing(String... namesAndValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (lastContact == null
                    || !lastContact.contains(namesAndValues[i] + "=" + namesAndValues[i + 1])) {
                sb.append(sb.length() == 0 ? "" : ", ").append(namesAndValues[i]);
            }
        }
        return sb.toString();
    }

    // ---- Address Information tab ----------------------------------------

    /** Every model on the address tab is prefixed {@code ADDpayable.} — again pinned exactly. */
    private static final String A = "ADDpayable.";

    public String lastAddressTab = "", lastAddressType = "", lastCascade = "", lastAddressFields = "",
            lastAddRow = "", lastAddedRowText = "";
    private int addressRowsBefore = -1, addressRowsAfter = -1;

    /**
     * Open the <b>Address Information</b> tab.
     *
     * <p>The tab label sits on an {@code <li>} with no handler — the {@code <a data-toggle="tab">} INSIDE
     * it is what switches the pane. Clicking the {@code <li>} leaves the Personal Information pane
     * showing while the run happily types into fields that are not on screen.</p>
     */
    public String openAddressTab() {
        Object r = page.evaluate("() => {" + JS
                + " const li=[...document.querySelectorAll('li,a')].filter(vis)"
                + "   .find(e=>/^\\s*address\\s*information\\s*$/i.test(norm(e.textContent)));"
                + " if(!li) return '(no Address Information tab)';"
                + " const a=li.tagName==='A'? li : (li.querySelector('a')||li);"
                + " a.click(); return 'clicked the '+a.tagName+' inside the tab'; }");
        lastAddressTab = r == null ? "" : r.toString();
        waitForAngular(1500);
        // Judged on the pane, not on the click: the address fields must actually be on screen.
        Object shown = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"[ng-model^='ADDpayable.']\")].filter(vis).length; }");
        int n = shown instanceof Number ? ((Number) shown).intValue() : 0;
        lastAddressTab += " -> " + n + " ADDpayable fields are now visible";
        System.out.println("SupplierMaster: Address tab -> " + lastAddressTab);
        return lastAddressTab;
    }

    public boolean addressTabOpened() { return lastAddressTab != null && lastAddressTab.contains("-> 0") == false
            && lastAddressTab.startsWith("clicked"); }

    /** How many REAL options a list currently holds. */
    private int optionCount(String model) {
        Object n = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " return e? [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }", model);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Wait for a dependent list to CHANGE, not merely to be non-empty.
     *
     * <p>State and City ship pre-populated on this form — 21 and 48 options before any country is
     * chosen. A "wait until not empty" is satisfied instantly by that stale list, and the run then picks
     * a state belonging to some other country.</p>
     */
    private boolean waitForListChange(String model, int before, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int now = optionCount(model);
            if (now != before && now > 0) return true;
            page.waitForTimeout(300);
        }
        return false;
    }

    /** Select the option at {@code index} of a list, returning its text. */
    private String selectIndex(String model, String id, int index) {
        page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+a.m+\"']\")].filter(vis)[0];"
                + " if(e) e.id=a.id; }", java.util.Map.of("m", model, "id", id));
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(600);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return t == null ? "" : t.toString();
    }

    /** The DOM indexes of the real options of a list, in order. */
    private java.util.List<Integer> realIndexes(String model) {
        Object r = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " if(!e) return [];"
                + " return [...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)))"
                + "   .map(x=>x.i); }", model);
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) {
            for (Object o : (java.util.List<?>) r) out.add(((Number) o).intValue());
        }
        return out;
    }

    /** Select the <b>Address Type</b> ({@code ADDpayable.AddressType}). */
    public String selectAddressType() {
        lastAddressType = pickExact(A + "AddressType", "__smAddrType");
        System.out.println("SupplierMaster: Address Type = " + lastAddressType);
        return lastAddressType;
    }

    /**
     * Walk the <b>Country → State → City/District → Area/Town</b> cascade, each selection waiting for the
     * NEXT list to change before reading it.
     *
     * <p>Countries are tried in turn (Malaysia first, since the data lives there) and, within a country,
     * states and cities are tried until one yields a real <b>Area/Town</b> — most combinations have no
     * area at all, and stopping at the first state would report an empty Area as a defect when it is
     * simply an unused branch of the reference data.</p>
     */
    public String selectAddressCascade() {
        StringBuilder log = new StringBuilder();
        java.util.List<Integer> countries = realIndexes(A + "Country");
        // Malaysia first — the reference data behind these screens is Malaysian.
        Object my = page.evaluate("() => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='ADDpayable.Country']\")]"
                + "   .filter(vis)[0]; if(!e) return -1;"
                + " const i=[...e.options].findIndex(o=>/^malaysia$/i.test(norm(o.text)));"
                + " return i; }");
        int malaysia = my instanceof Number ? ((Number) my).intValue() : -1;
        if (malaysia >= 0) { countries.remove(Integer.valueOf(malaysia)); countries.add(0, malaysia); }

        // The best country/state/city found while hunting for an area, so a run that finds no area
        // anywhere still LEAVES a valid country/state/city on the form instead of whatever combination
        // the search happened to end on — that is what put "No Information" into the added row.
        int bestCountry = -1, bestState = -1, bestCity = -1;
        String bestNames = "";
        int citiesTried = 0;

        int triedCountries = 0;
        for (Integer ci : countries) {
            if (triedCountries++ >= 3) break;                       // three countries is enough evidence
            int statesBefore = optionCount(A + "State");
            String country = selectIndex(A + "Country", "__smCountry", ci);
            boolean stateChanged = waitForListChange(A + "State", statesBefore, 9000);
            java.util.List<Integer> states = realIndexes(A + "State");
            log.setLength(0);
            log.append("Country = ").append(country)
               .append(stateChanged ? " (the State list reloaded: " : " (the State list did NOT reload, still ")
               .append(statesBefore).append(" -> ").append(states.size()).append(" options)");
            if (states.isEmpty()) continue;

            int triedStates = 0;
            for (Integer si : states) {
                if (triedStates++ >= 4) break;
                int citiesBefore = optionCount(A + "City");
                String state = selectIndex(A + "State", "__smState", si);
                waitForListChange(A + "City", citiesBefore, 9000);
                java.util.List<Integer> cities = realIndexes(A + "City");
                // "No Information" is this application's placeholder row, not a real state or city.
                if (cities.isEmpty() || state.matches("(?i)\\s*no information\\s*")) continue;

                int triedCities = 0;
                for (Integer cyi : cities) {
                    if (triedCities++ >= 6) break;
                    int areasBefore = optionCount(A + "Area");
                    String city = selectIndex(A + "City", "__smCity", cyi);
                    citiesTried++;
                    if (bestCity < 0) {
                        bestCountry = ci; bestState = si; bestCity = cyi;
                        bestNames = "Country = " + country + " | State = " + state
                                + " | City/District = " + city;
                    }
                    waitForListChange(A + "Area", areasBefore, 9000);
                    java.util.List<Integer> areas = realIndexes(A + "Area");
                    if (areas.isEmpty()) continue;

                    String area = selectIndex(A + "Area", "__smArea", areas.get(0));
                    lastCascade = log + " | State = " + state + " | City/District = " + city
                            + " | Area/Town = " + area
                            + " (each list was waited for until it CHANGED, so the state belongs to the "
                            + "country and the area to the city — State and City arrive pre-populated on "
                            + "this form, so 'not empty' proves nothing)";
                    System.out.println("SupplierMaster: cascade -> " + lastCascade);
                    return lastCascade;
                }
            }
        }

        // No area anywhere. Restore the best country/state/city so the address is still a real one, and
        // report what was searched — an empty optional list is not the same as a broken cascade.
        if (bestCity >= 0) {
            selectIndex(A + "Country", "__smCountry", bestCountry);
            waitForListChange(A + "State", 0, 6000);
            selectIndex(A + "State", "__smState", bestState);
            waitForListChange(A + "City", 0, 6000);
            selectIndex(A + "City", "__smCity", bestCity);
            waitForAngular(800);
            lastCascade = bestNames + " | Area/Town = (the list is EMPTY for every city tried — "
                    + citiesTried + " cities across the states of up to 3 countries, Malaysia first). "
                    + "Country, State and City/District each reloaded from their parent and hold a real "
                    + "value; only Area/Town has nothing to offer.";
        } else {
            lastCascade = log + " — no country tried produced a usable State/City at all";
        }
        System.out.println("SupplierMaster: cascade -> " + lastCascade);
        return lastCascade;
    }

    /** Country, State and City/District all hold a real value (Area/Town may legitimately be empty). */
    public boolean cascadeToCity() {
        return lastCascade != null && lastCascade.contains("City/District = ")
                && !lastCascade.contains("City/District = No Information")
                && !lastCascade.contains("no country tried");
    }

    /** The Area/Town list was empty everywhere it was looked for. */
    public boolean areaListEmpty() {
        return lastCascade != null && lastCascade.contains("Area/Town = (the list is EMPTY");
    }

    /** Every level of the cascade holds a real value. */
    public boolean cascadeComplete() {
        return lastCascade != null && lastCascade.contains("Area/Town = ")
                && !lastCascade.contains("FAILED");
    }

    /** Enter the <b>Address</b>, <b>Contact</b> and <b>Postcode</b>, each pinned and read back. */
    public String enterAddressFields(String address, String contact, String postcode) {
        String r1 = put(A + "Address", address);
        String r2 = put(A + "Contact", contact);
        String r3 = put(A + "Postcode", postcode);
        lastAddressFields = "Address=" + r1 + " | Contact=" + r2 + " | Postcode=" + r3;
        waitForAngular(500);
        System.out.println("SupplierMaster: " + lastAddressFields);
        return lastAddressFields;
    }

    public boolean addressFieldsEntered(String address, String contact, String postcode) {
        return lastAddressFields != null && !lastAddressFields.contains("(no field")
                && lastAddressFields.contains("Address=" + address)
                && lastAddressFields.contains("Contact=" + contact)
                && lastAddressFields.contains("Postcode=" + postcode);
    }

    /**
     * Click <b>Add</b> ({@code AddAddress()}) and count the grid rows either side of it.
     *
     * <p>The click is never credited on its own: the row count before and after is what says an address
     * was added, and the added row's own text is read back so the values can be checked against what was
     * typed rather than against the form still holding them.</p>
     */
    public String addAddressRow() {
        addressRowsBefore = addressGridRows();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/AddAddress/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(2000);
        addressRowsAfter = addressGridRows();
        Object rows = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .map(r=>norm(r.textContent)).filter(t=>t).slice(-3).join(' ;; '); }");
        lastAddedRowText = rows == null ? "" : rows.toString();
        lastAddRow = "grid rows " + addressRowsBefore + " -> " + addressRowsAfter
                + (addressRowsAfter > addressRowsBefore
                    ? "; the new row reads: " + lastAddedRowText
                    : "; NO row was added (the rows shown: " + lastAddedRowText + ")");
        System.out.println("SupplierMaster: Add address -> " + lastAddRow);
        return lastAddRow;
    }

    private int addressGridRows() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** A row really appeared. */
    public boolean addressRowAdded() { return addressRowsAfter > addressRowsBefore; }

    /** Which of the typed values the new row actually shows. */
    public String addedRowCheck(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            sb.append(sb.length() == 0 ? "" : ", ")
              .append(v).append(lastAddedRowText != null && lastAddedRowText.contains(v)
                      ? " (shown)" : " (NOT shown)");
        }
        return sb.toString();
    }

    public boolean addedRowShows(String... values) {
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            if (lastAddedRowText == null || !lastAddedRowText.contains(v)) return false;
        }
        return true;
    }

    // ---- File Linking tab ------------------------------------------------

    public String lastFileTab = "", lastFileChosen = "", lastFileAdd = "", lastFileRowText = "";
    private int fileRowsBefore = -1, fileRowsAfter = -1;

    /** Open the <b>File Linking</b> tab — again by the {@code <a>} inside the tab's {@code <li>}. */
    public String openFileLinkingTab() {
        Object r = page.evaluate("() => {" + JS
                + " const li=[...document.querySelectorAll('li,a')].filter(vis)"
                + "   .find(e=>/^\\s*file\\s*linking\\s*$/i.test(norm(e.textContent)));"
                + " if(!li) return '(no File Linking tab)';"
                + " const a=li.tagName==='A'? li : (li.querySelector('a')||li);"
                + " a.click(); return 'clicked the '+a.tagName+' inside the tab'; }");
        lastFileTab = r == null ? "" : r.toString();
        waitForAngular(1500);
        Object shown = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"input[type='file']\")].filter(vis).length; }");
        int n = shown instanceof Number ? ((Number) shown).intValue() : 0;
        lastFileTab += " -> " + n + " file input(s) on screen";
        System.out.println("SupplierMaster: File Linking tab -> " + lastFileTab);
        return lastFileTab;
    }

    public boolean fileTabOpened() {
        return lastFileTab != null && lastFileTab.startsWith("clicked") && !lastFileTab.endsWith("-> 0 file input(s) on screen");
    }

    /**
     * Choose a file ({@code payable.fileimage}).
     *
     * <p>The file is handed to the real {@code <input type="file">} so the screen's own
     * {@code fileChanged1(this.files)} handler runs — setting the model text by hand would leave the
     * controller with no file and the run would still look like it had chosen one.</p>
     */
    public String chooseFile(java.nio.file.Path file) {
        try {
            page.locator("input[type='file'][ng-model='payable.fileimage']").first()
                    .setInputFiles(file, new com.microsoft.playwright.Locator.SetInputFilesOptions().setTimeout(15000));
        } catch (Exception e) {
            lastFileChosen = "(setInputFiles failed: " + e.getMessage().split("\n")[0] + ")";
            System.out.println("SupplierMaster: choose file -> " + lastFileChosen);
            return lastFileChosen;
        }
        waitForAngular(2000);
        // Read back from the input itself AND from the screen's own filename box.
        Object r = page.evaluate("() => {" + JS
                + " const f=[...document.querySelectorAll(\"input[type='file']\")][0];"
                + " const picked=(f && f.files && f.files[0])? f.files[0].name : '(the input holds no file)';"
                + " const box=[...document.querySelectorAll(\"[ng-model='payable.filename']\")][0];"
                + " return 'the input holds \"'+picked+'\"; the File Name box reads \"'"
                + "   +(box? (box.value||'') : '(no File Name box)')+'\"'; }");
        lastFileChosen = r == null ? "" : r.toString();
        System.out.println("SupplierMaster: choose file -> " + lastFileChosen);
        return lastFileChosen;
    }

    public boolean fileChosen(String fileName) {
        return lastFileChosen != null && lastFileChosen.contains("\"" + fileName + "\"")
                && !lastFileChosen.startsWith("(");
    }

    /** Click <b>Add</b> ({@code AddFileDetails}) and count the File Linking grid rows either side. */
    public String addFileRow() {
        fileRowsBefore = fileGridRows();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/AddFileDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        fileRowsAfter = fileGridRows();
        Object rows = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .map(r=>norm(r.textContent)).filter(t=>t).slice(-3).join(' ;; '); }");
        lastFileRowText = rows == null ? "" : rows.toString();
        lastFileAdd = "grid rows " + fileRowsBefore + " -> " + fileRowsAfter
                + (fileRowsAfter > fileRowsBefore
                    ? "; the new row reads: " + lastFileRowText
                    : "; NO row was added (rows shown: " + lastFileRowText + ")");
        System.out.println("SupplierMaster: Add file -> " + lastFileAdd);
        return lastFileAdd;
    }

    private int fileGridRows() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean fileRowAdded() { return fileRowsAfter > fileRowsBefore; }

    /** The added row names the file that was chosen. */
    public boolean fileRowShows(String fileName) {
        return lastFileRowText != null && lastFileRowText.contains(fileName);
    }

    // ---- Rate Contract tab -----------------------------------------------

    public String lastRateTab = "", lastRateDates = "", lastItemName = "", lastRateAmounts = "",
            lastRateAdd = "", lastRateRowText = "";
    private int rateRowsBefore = -1, rateRowsAfter = -1;

    /** Open the <b>Rate Contract</b> tab. */
    public String openRateContractTab() {
        Object r = page.evaluate("() => {" + JS
                + " const li=[...document.querySelectorAll('li,a')].filter(vis)"
                + "   .find(e=>/^\\s*rate\\s*contract\\s*$/i.test(norm(e.textContent)));"
                + " if(!li) return '(no Rate Contract tab)';"
                + " const a=li.tagName==='A'? li : (li.querySelector('a')||li);"
                + " a.click(); return 'clicked the '+a.tagName+' inside the tab'; }");
        lastRateTab = r == null ? "" : r.toString();
        waitForAngular(1500);
        Object shown = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"[ng-model='payable.ItemName'],"
                + "[ng-model='payable.FromDate']\")].filter(vis).length; }");
        int n = shown instanceof Number ? ((Number) shown).intValue() : 0;
        lastRateTab += " -> " + n + " rate-contract field(s) on screen";
        System.out.println("SupplierMaster: Rate Contract tab -> " + lastRateTab);
        return lastRateTab;
    }

    public boolean rateTabOpened() {
        return lastRateTab != null && lastRateTab.startsWith("clicked")
                && !lastRateTab.endsWith("-> 0 rate-contract field(s) on screen");
    }

    /**
     * Enter the <b>Start Date</b> and <b>End Date</b> ({@code payable.FromDate} / {@code payable.ToDate}).
     *
     * <p>Both are 720kb datepicker boxes. The value is written through the model first and read back; if
     * the box refuses it, the calendar is opened and the day clicked — a datepicker that ignores a typed
     * value leaves the model empty while the box still shows text.</p>
     */
    public String enterRateDates(String start, String end) {
        String r1 = putDate(M + "FromDate", start);
        String r2 = putDate(M + "ToDate", end);
        lastRateDates = "StartDate=" + r1 + " | EndDate=" + r2;
        waitForAngular(600);
        System.out.println("SupplierMaster: " + lastRateDates);
        return lastRateDates;
    }

    /** Write a date, then close any calendar it opened so the next field is not covered. */
    private String putDate(String model, String value) {
        String written = put(model, value);
        waitForAngular(400);
        // Dismiss the calendar: it overlays the fields below it.
        page.evaluate("() => { document.body.click(); }");
        waitForAngular(300);
        Object back = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " return e? (e.value||'') : '(gone)'; }", model);
        String now = back == null ? "" : back.toString();
        return now.isEmpty() ? "(the box did not keep the value; write returned " + written + ")"
                             : now + " [" + model + "]";
    }

    public boolean rateDatesEntered(String start, String end) {
        return lastRateDates != null && lastRateDates.contains("StartDate=" + start)
                && lastRateDates.contains("EndDate=" + end);
    }

    /** Enter the <b>Item Name</b> ({@code payable.ItemName}). */
    public String enterItemName(String item) {
        lastItemName = put(M + "ItemName", item);
        waitForAngular(800);
        System.out.println("SupplierMaster: Item Name = " + lastItemName);
        return lastItemName;
    }

    public boolean itemNameEntered(String item) {
        return lastItemName != null && !lastItemName.startsWith("(no field")
                && lastItemName.startsWith(item);
    }

    /**
     * Enter the <b>Purchase Rate</b>, <b>Total Qty</b> and <b>Total Amount</b>.
     *
     * <p>Each is read back off its own box: Total Amount may be computed by the screen from the other
     * two, in which case what it holds afterwards is what matters, not what was typed into it.</p>
     */
    public String enterRateAmounts(String rate, String qty, String amount) {
        String r1 = put(M + "PurchaseRate", rate);
        String r2 = put(M + "TotalQty", qty);
        String r3 = put(M + "TotalAmount", amount);
        waitForAngular(700);
        Object back = page.evaluate("() => {" + JS
                + " const g=m=>{ const e=[...document.querySelectorAll(\"[ng-model='payable.\"+m+\"']\")]"
                + "   .filter(vis)[0]; return e? (e.value||'') : '(gone)'; };"
                + " return 'PurchaseRate='+g('PurchaseRate')+' | TotalQty='+g('TotalQty')"
                + "   +' | TotalAmount='+g('TotalAmount'); }");
        lastRateAmounts = (back == null ? "" : back.toString())
                + "  (as written: " + r1 + " / " + r2 + " / " + r3 + ")";
        System.out.println("SupplierMaster: " + lastRateAmounts);
        return lastRateAmounts;
    }

    public boolean rateAmountsEntered(String rate, String qty, String amount) {
        return lastRateAmounts != null && !lastRateAmounts.contains("(no field")
                && lastRateAmounts.contains("PurchaseRate=" + rate)
                && lastRateAmounts.contains("TotalQty=" + qty)
                && lastRateAmounts.contains("TotalAmount=" + amount);
    }

    /** What the three amount boxes hold now — used when one of them is computed rather than typed. */
    public String rateAmountsActual() { return lastRateAmounts; }

    /**
     * Click the Rate Contract tab's own <b>Add</b> ({@code AddRateContractDetails}) and count its grid
     * rows either side. Reported for evidence; the requested steps do not include this click.
     */
    public String addRateContractRow() {
        rateRowsBefore = fileGridRows();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/AddRateContractDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        rateRowsAfter = fileGridRows();
        Object rows = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .map(r=>norm(r.textContent)).filter(t=>t).slice(-3).join(' ;; '); }");
        lastRateRowText = rows == null ? "" : rows.toString();
        lastRateAdd = "grid rows " + rateRowsBefore + " -> " + rateRowsAfter
                + (rateRowsAfter > rateRowsBefore
                    ? "; the new row reads: " + lastRateRowText
                    : "; NO row was added (rows shown: " + lastRateRowText + ")");
        System.out.println("SupplierMaster: Add rate contract -> " + lastRateAdd);
        return lastRateAdd;
    }

    public boolean rateRowAdded() { return rateRowsAfter > rateRowsBefore; }

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

        // Submit raises a CONFIRMATION dialog carrying its own Save button — nothing is written until
        // that is clicked. Without it the screen simply sits there and no message ever appears, which is
        // exactly how this flow first read as "Submit says nothing".
        lastConfirm = confirmSave();

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
        System.out.println("SupplierMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    public String lastGender = "";

    /**
     * Select the <b>Gender</b>.
     *
     * <p>Not in the requested steps, but the screen refuses to save without it ("Please Select Gender!").
     * Handled as a dropdown OR a radio, since the control differs between these forms, and reported so
     * the report shows a mandatory field the steps did not name rather than hiding an extra click.</p>
     */
    public String selectGender() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .find(e=>/gender|sex/i.test(tail(e)+' '+labelOf(e)));"
                + " if(sel){ const reals=[...sel.options].map((o,i)=>({o,i}))"
                + "     .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                + "   if(!reals.length) return '(no gender option)';"
                + "   sel.selectedIndex=reals[0].i;"
                + "   try{ const c=angular.element(sel).controller('ngModel');"
                + "        if(c){ c.$setViewValue(sel.value); c.$render(); } }catch(e){}"
                + "   sel.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   return norm((sel.options[sel.selectedIndex]||{}).text||'')+' ['+(ngOf(sel)||'?')+']'; }"
                + " const radio=[...document.querySelectorAll('input[type=radio]')].filter(vis)"
                + "   .find(e=>/gender|sex/i.test(ngOf(e)+' '+labelOf(e)));"
                + " if(radio){ radio.click();"
                + "   return (radio.checked? 'selected' : 'NOT selected')+' radio ['+(ngOf(radio)||'?')"
                + "     +'] '+labelOf(radio).slice(0,30); }"
                // Nothing visible. Say whether one exists HIDDEN, and what tabs the form offers — the
                // difference between "the control is on a tab that did not open" and "the screen demands
                // a field it never renders".
                + " const hidden=[...document.querySelectorAll('select,input')]"
                + "   .filter(e=>/gender|sex/i.test((e.getAttribute('ng-model')||'')))"
                + "   .map(e=>(e.getAttribute('ng-model')||'?')+(vis(e)? ' (visible)' : ' (HIDDEN)'));"
                + " const tabs=[...document.querySelectorAll('a,li')].filter(vis)"
                + "   .map(e=>norm(e.textContent)).filter(t=>t && t.length<30"
                + "     && /information|detail|bank|address|personal|general/i.test(t));"
                + " return '(no VISIBLE gender control) hidden matches: '"
                + "   +(hidden.length? hidden.join(', ') : 'none in the DOM at all')"
                + "   +'; tabs on the form: '+([...new Set(tabs)].join(' | ')||'none'); }");
        lastGender = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("SupplierMaster: Gender = " + lastGender);
        return lastGender;
    }

    /** What the confirmation dialog did, for the report. */
    public String lastConfirm = "";

    /**
     * Click <b>Save</b> on the confirmation dialog that Submit raises.
     *
     * <p>Waited for rather than assumed: the dialog is rendered after the Submit handler runs, so a
     * single immediate look misses it. The button is searched for INSIDE a visible dialog, and only
     * exact "Save"/"Yes"/"OK" text is accepted — a loose match would find the form's own Submit again
     * and re-open the dialog.</p>
     */
    public String confirmSave() {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            Object r = page.evaluate("() => {" + JS
                    + " const dlgs=[...document.querySelectorAll("
                    + "   '.modal,.modal-dialog,.modal-content,[role=dialog],.sweet-alert,.jAlert,.ui-dialog')]"
                    + "   .filter(vis);"
                    + " for(const d of dlgs){"
                    + "   const b=[...d.querySelectorAll('button,a,input[type=button],input[type=submit]')]"
                    + "     .filter(vis)"
                    + "     .find(x=>/^save|yes|ok|confirm$/i.test(norm(x.textContent)||x.value||''));"
                    + "   if(b){ const msg=norm(d.textContent).slice(0,120);"
                    + "     b.click();"
                    + "     return 'clicked \"'+(norm(b.textContent)||b.value)+'\" on the confirmation"
                    + "       dialog: \"'+msg+'\"'; } }"
                    + " return ''; }");
            String s2 = r == null ? "" : r.toString();
            if (!s2.isEmpty()) {
                waitForAngular(1200);
                System.out.println("SupplierMaster: confirm -> " + s2);
                return s2;
            }
            page.waitForTimeout(400);
        }
        System.out.println("SupplierMaster: confirm -> no confirmation dialog appeared within 10s");
        return "(no confirmation dialog appeared within 10s)";
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
        // Submit on this screen REDIRECTS to #/add-RateContract — the next step of the supplier
        // workflow. Reading the list from there finds no grid at all, so go back to the supplier list
        // (the route the menu gave) before looking.
        try {
            String u = page.url().toLowerCase();
            if (!u.contains("payable") && !u.contains("supplier") && !lastRoute.isEmpty()) {
                String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
                page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1));
                waitForAngular(3500);
            }
        } catch (Exception ignore) { }
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
        // The supplier grid does NOT refresh itself: it still holds the rows fetched by the last Search,
        // so a record saved a moment ago is genuinely absent from it. Run the screen's own Search
        // (fngetPayableGrid, on the location it defaults to) before reading the grid, or the check
        // reports "never saved" for a record the server did write.
        try {
            page.evaluate("() => {" + JS
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/fngetPayableGrid/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\s*search\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(b) b.click(); }");
            waitForAngular(3500);
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

            // Not on the page shown? This grid pages server-side, so a column filter only searches the
            // rows already loaded — a new record can sit on the last page and read as "never saved".
            if (hit != null && !hit.toString().contains("FOUND:")) {
                page.evaluate("() => {" + JS
                        // Clear the column filter first — leaving it set means the widened, last page is
                        // still filtered to nothing, and the deep search reads 0 rows every time.
                        + " [...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                        + "   .filter(vis).forEach(f=>{ f.value='';"
                        + "     try{ const c=angular.element(f).controller('ngModel');"
                        + "          if(c){ c.$setViewValue(''); c.$render(); } }catch(e){}"
                        + "     f.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "     f.dispatchEvent(new Event('change',{bubbles:true})); });"
                        + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                        + "   .find(s=>/paginationPageSize/i.test(ngOf(s)));"
                        + " if(sel){ sel.selectedIndex=sel.options.length-1;"
                        + "   sel.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "   try{ angular.element(sel).triggerHandler('change'); }catch(e){} }"
                        + " const last=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(b=>/pageLastPageClick/i.test(b.getAttribute('ng-click')||''));"
                        + " if(last) last.click(); }");
                waitForAngular(3000);
                Object deep = page.evaluate("(a) => {" + JS
                        + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                        + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                        + " const mine=rows.find(t=>t.includes(a.c) && (!a.n || t.includes(a.n)));"
                        + " return mine? 'FOUND: on the last page -> '+mine.slice(0,140)"
                        + "   : 'still not found after widening the page size and jumping to the last "
                        + "page ('+rows.length+' rows read there)'; }",
                        java.util.Map.of("c", code, "n", remark == null ? "" : remark));
                lastListCheck += "  ||  " + (deep == null ? "" : deep);
            }
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("SupplierMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
