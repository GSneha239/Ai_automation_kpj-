package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Department</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Department</b> → <b>Add</b> →
 * <b>Code</b> + <b>Department</b> + <b>Store</b> → tick <b>Department Location Details</b> →
 * <b>Case Template</b> → <b>Diagnosis Code</b> → <b>Patient Count</b> + <b>Remark</b> → inner <b>Add</b> →
 * <b>Submit</b> → success toast.</p>
 */
public class Department extends BasePage {

    public Department(Page page) { super(page); }

    /** Route — re-mined from the menu at runtime; this is the known default. */
    public static String ROUTE = "#/DepartmentMaster";
    public String lastCode = "", lastDepartment = "", lastStore = "";
    public String lastCaseTemplate = "", lastDiagnosisCode = "", lastDiagnosisDescription = "", lastPatientCount = "";
    /** What the save call actually returned (status + body) — a bare toast says nothing about the real result. */
    public String lastSaveApi = "";
    /** The screen at toast time — captured before the row check navigates back to the list. */
    public byte[] toastPng;
    /** Every toast Submit produced, as JSON — the decisive one is not always the first. */
    public String lastToasts = "[]";
    /** Any message the inner Add raised — an error there must not be swallowed by a later success. */
    public String lastAddToasts = "[]";

    /** Departments a hospital would actually have — a realistic name beats "Auto department DEP123". */
    private static final String[] DEPARTMENTS = {
            "Interventional Cardiology", "Paediatric Cardiology", "Hand and Microsurgery", "Spine Surgery",
            "Vascular Surgery", "Hepatobiliary Surgery", "Breast and Endocrine Surgery", "Sports Medicine",
            "Geriatric Medicine", "Palliative Care", "Pain Management", "Infectious Diseases",
            "Clinical Haematology", "Medical Oncology", "Neonatology", "Maternal Fetal Medicine"
    };

    /** Department descriptions already on file — the master rejects a duplicate with "Description already exist". */
    private final java.util.Set<String> existingNames = new java.util.HashSet<>();

    /** Case paper / case template names the screen would plausibly carry. */
    private static final String[] CASE_TEMPLATES = {
            "General Consultation Case Sheet", "Specialist Clinic Case Sheet", "Day Care Case Sheet",
            "Follow Up Case Sheet", "Outpatient Assessment Form"
    };

    /** Read the current value of the input bound to an ng-model. */
    private String valueOf(String ngModel) {
        Object r = page.evaluate("(m) => { const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " return e ? (e.value||'') : ''; }", ngModel);
        return r == null ? "" : r.toString().trim();
    }

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "department", with the submenu it sits under — answers "which link, which route?". */
    public String findDepartmentLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/department/i.test(norm(a.textContent)) || /department/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,32)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** Every visible field / select / checkbox / button with its ng-model or ng-click — the wiring, in one look. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t=''; if(e.id){ try{ const l=document.querySelector('label[for=\"'+CSS.escape(e.id)+'\"]'); if(l) t=norm(l.textContent); }catch(x){} }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } } return t.slice(0,34); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').slice(0,40)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"'+(e.placeholder?' ph=\"'+e.placeholder+'\"':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,20)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,30);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " const secs=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,.panel-title,.box-title,.nav-tabs a')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent)).filter(t=>t).slice(0,20);"
                + " return 'SECTIONS: '+secs.join(' | ')+'\\nFIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- navigation ------------------------------------------------------

    /** <b>Application Configuration</b> → <b>Location</b> → <b>Department</b>, mining the route from the menu. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Location" — a prefix match expands another module's menu.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("Department.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        // EXACT "Department" — "Department Group" (Billing) and "Department Wise ..." must not match.
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*departments?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/^\\s*departments?\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__deptMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__deptMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("Department.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__deptMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__deptMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("Department.nav: menu link not found. department links => " + findDepartmentLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("Department.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /department/i.test(document.body.innerText||'')"
                + " && [...document.querySelectorAll('button,a,input[type=button]')].some(x=>/^\\s*add\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null)"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback — the sticky header swallows real clicks on these config screens. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("Department." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("Department." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> (the one that opens the new-department form). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            // By ng-click first: this screen's Add sits INSIDE the page header toolbar, so filtering the header out
            // (as the other config screens do) loses it.
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/adddepartment/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__deptAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Department.clickAdd: Add button not found"); return false; }
        robustClick("__deptAdd", "clickAdd");
        waitForAngular(1500);
        return true;
    }

    /** True once the add form (Code + Department + Submit) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => {"
                + " const has=m=>[...document.querySelectorAll('input')].some(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " return has('department.Code') && has('department.Description'); }"));
    }

    /** Set an input bound to {@code ngModel} through Angular so the binding really takes. */
    private String setNg(String ngModel, String value) {
        Object r = page.evaluate("([m,v]) => { const A=window.angular;"
                + " const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }",
                java.util.Arrays.asList(ngModel, value));
        return r == null ? "" : r.toString();
    }

    /** Enter <b>Code*</b>, <b>Department*</b> and pick a <b>Store</b>. {@code attempt} shifts the values on a retry. */
    public String fillCodeDepartmentStore(int attempt) {
        lastCode = "DP" + String.format("%05d", Math.abs((System.nanoTime() / 13 + attempt * 7919L) % 100000));
        lastDepartment = pickUnusedDepartment(attempt);
        String cd = setNg("department.Code", lastCode);
        String dp = setNg("department.Description", lastDepartment);
        lastStore = selectByLabel("department.storeid");
        waitForAngular(500);
        return "Code=" + cd + " | Department=" + dp + " | Store=" + lastStore;
    }

    /**
     * A department name the master does not already hold. The pool is walked from a per-attempt offset; if every
     * pool name is taken the name gains a plausible unit number ("Sports Medicine Unit 3") rather than a random
     * suffix, so the master keeps reading like real configuration.
     */
    private String pickUnusedDepartment(int attempt) {
        int start = (int) (Math.abs(System.nanoTime() / 7 + attempt) % DEPARTMENTS.length);
        for (int i = 0; i < DEPARTMENTS.length; i++) {
            String cand = DEPARTMENTS[(start + i) % DEPARTMENTS.length];
            if (!existingNames.contains(cand.toLowerCase())) return cand;
        }
        for (int unit = 2; unit < 40; unit++) {
            String cand = DEPARTMENTS[start] + " Unit " + unit;
            if (!existingNames.contains(cand.toLowerCase())) return cand;
        }
        return DEPARTMENTS[start] + " Unit " + (attempt + 2);
    }

    /** Pick the first real option of a &lt;select&gt; (skipping the "--Select--" placeholder); returns its text. */
    private String selectByLabel(String ngModel) {
        Object idx = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__deptSel';"
                + " return [...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", ngModel);
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) return "(no option)";
        try {
            page.locator("#__deptSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectByLabel(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__deptSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(600);
        Object t = page.evaluate("() => { const e=document.getElementById('__deptSel');"
                + " const v=(e && e.selectedIndex>=0) ? (e.options[e.selectedIndex].textContent||'').trim() : '';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /**
     * Tick the <b>Select</b> checkbox of the <b>Department Location Details</b> row (ng-model {@code dept.Isdefault}
     * inside that table's ng-repeat). Returns the location the ticked row belongs to, or "".
     */
    public String tickDepartmentLocationDetails() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null"
                + "   && /isdefault|isselect|selected/i.test(c.getAttribute('ng-model')||''));"
                + " if(!cbs.length) return ''; const cb=cbs.find(c=>!c.checked)||cbs[0]; cb.id='__deptLocCb';"
                + " const row=cb.closest('tr'); return row ? norm(row.textContent).slice(0,60) : 'row'; }");
        String row = r == null ? "" : r.toString();
        if (row.isEmpty()) { System.out.println("tickDepartmentLocationDetails: no Select checkbox in the table"); return ""; }
        robustClick("__deptLocCb", "tickDepartmentLocationDetails");
        waitForAngular(400);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__deptLocCb'); return !!(c && c.checked); }"));
        page.evaluate("() => { const c=document.querySelector('#__deptLocCb'); if(c) c.removeAttribute('id'); }");
        return checked ? row : "";
    }

    /**
     * Fill <b>Case Template</b> — a CKEditor rich-text box, NOT the "Case Paper Format" input at the top of the
     * form. Writing to {@code department.reportname} leaves the editor empty, so the value goes in through the
     * editor's own API and is READ BACK to prove it landed.
     */
    public String fillCaseTemplate() {
        lastCaseTemplate = CASE_TEMPLATES[(int) (Math.abs(System.nanoTime() / 11) % CASE_TEMPLATES.length)];
        Object r = page.evaluate("(text) => {"
                + " if(window.CKEDITOR && CKEDITOR.instances){ const names=Object.keys(CKEDITOR.instances);"
                + "   if(names.length){ const ed=CKEDITOR.instances[names[0]]; ed.setData(text);"
                + "     try{ ed.fire('change'); }catch(e){}"
                + "     try{ ed.updateElement(); const el=ed.element && ed.element.$; if(el){ const A=window.angular; const c=A.element(el).controller('ngModel');"
                + "       if(c){ c.$setViewValue(text); c.$render(); } el.dispatchEvent(new Event('change',{bubbles:true})); } }catch(e){}"
                + "     return 'ckeditor:'+names[0]; } }"
                + " const body=[...document.querySelectorAll('iframe')].map(f=>{ try{ return f.contentDocument && f.contentDocument.body; }catch(e){ return null; } })"
                + "   .find(b=>b && b.isContentEditable);"
                + " if(body){ body.innerHTML='<p>'+text+'</p>'; body.dispatchEvent(new Event('input',{bubbles:true})); return 'iframe-body'; }"
                + " const ce=[...document.querySelectorAll('[contenteditable=true]')].find(e=>e.offsetParent!==null);"
                + " if(ce){ ce.innerHTML='<p>'+text+'</p>'; ce.dispatchEvent(new Event('input',{bubbles:true})); return 'contenteditable'; }"
                + " return ''; }", lastCaseTemplate);
        String how = r == null ? "" : r.toString();
        if (how.isEmpty()) { System.out.println("fillCaseTemplate: no Case Template editor found"); return ""; }
        waitForAngular(600);
        // Read it back out of the editor — a setData that silently no-ops must not report as filled.
        Object back = page.evaluate("() => { if(window.CKEDITOR && CKEDITOR.instances){ const n=Object.keys(CKEDITOR.instances);"
                + "   if(n.length) return (CKEDITOR.instances[n[0]].getData()||'').replace(/<[^>]*>/g,'').trim(); }"
                + " const b=[...document.querySelectorAll('iframe')].map(f=>{ try{ return f.contentDocument && f.contentDocument.body; }catch(e){ return null; } }).find(x=>x && x.isContentEditable);"
                + " return b ? (b.textContent||'').trim() : ''; }");
        String shown = back == null ? "" : back.toString();
        return shown.contains(lastCaseTemplate) ? lastCaseTemplate + " [" + how + "]"
                : "";
    }

    /**
     * Pick a <b>Diagnosis Code</b> from the typeahead. It only opens for REAL keystrokes and, as on Diagnosis Set,
     * commits on <b>ArrowDown + Enter</b> rather than by clicking a suggestion — success is confirmed by the
     * Diagnosis Description auto-filling, not by the click.
     */
    public String pickDiagnosisCode() {
        final String box = "input[ng-model='Diagnosis.DiagnosisCode']";
        String[] prefixes = { "10", "11", "12", "20", "21", "30", "40", "50" };
        for (String prefix : prefixes) {
            try {
                page.locator(box).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
                page.locator(box).fill("");
                page.locator(box).type(prefix, new com.microsoft.playwright.Locator.TypeOptions().setDelay(180));
            } catch (Exception e) { System.out.println("pickDiagnosisCode: typing failed - " + e.getMessage()); continue; }
            waitForAngular(500);

            String picked = "";
            for (int i = 0; i < 14 && picked.isEmpty(); i++) {
                Object tag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const sels=['.dropdown-menu li','li.uib-typeahead-match','.ui-select-choices-row','.select2-results li',"
                        + "  '.tt-suggestion','.autocomplete-suggestion','ul.typeahead li','.angucomplete-row','.ui-menu-item'];"
                        + " for(const s of sels){ const el=[...document.querySelectorAll(s)].find(x=>x.offsetParent!==null && norm(x.textContent));"
                        + "   if(el){ el.id='__deptSug'; return norm(el.textContent).slice(0,80); } }"
                        + " return ''; }");
                if (tag != null && !tag.toString().isEmpty()) {
                    try { page.locator("#__deptSug").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); picked = tag.toString(); }
                    catch (Exception e) { System.out.println("pickDiagnosisCode: suggestion click failed - " + e.getMessage()); }
                    page.evaluate("() => { const e=document.getElementById('__deptSug'); if(e) e.removeAttribute('id'); }");
                }
                if (picked.isEmpty()) page.waitForTimeout(400);
            }
            if (picked.isEmpty()) {
                try {
                    page.locator(box).press("ArrowDown");
                    page.waitForTimeout(400);
                    page.locator(box).press("Enter");
                    picked = "(via keyboard)";
                } catch (Exception ignore) { }
            }
            waitForAngular(1200);

            lastDiagnosisCode = valueOf("Diagnosis.DiagnosisCode");
            lastDiagnosisDescription = valueOf("Diagnosis.DiagnosisDescription");
            if (!lastDiagnosisDescription.isEmpty()) {
                return "typed=" + prefix + " | picked=" + picked + " | Diagnosis Code=" + lastDiagnosisCode
                        + " | Description=" + lastDiagnosisDescription;
            }
            System.out.println("pickDiagnosisCode: '" + prefix + "' gave no usable suggestion — trying the next prefix");
        }
        return "Diagnosis Code=" + lastDiagnosisCode + " | Description=(not auto-filled)";
    }

    /**
     * Enter <b>Patient Count</b> and <b>Remark</b>. Remark is the typeahead's own Diagnosis Description box, which
     * the code pick auto-fills — it is only written when the pick left it empty, so a real diagnosis description is
     * never overwritten with test text.
     */
    public String fillPatientCountAndRemark() {
        lastPatientCount = String.valueOf(5 + (int) (Math.abs(System.nanoTime() / 17) % 20));
        String pc = setNg("Diagnosis.PatientCount", lastPatientCount);
        String rm = valueOf("Diagnosis.DiagnosisDescription");
        if (rm.isEmpty()) rm = setNg("Diagnosis.DiagnosisDescription", "Routine follow up review");
        lastDiagnosisDescription = rm;
        waitForAngular(300);
        return "Patient Count=" + pc + " | Remark=" + rm;
    }

    /** How many rows the diagnosis detail grid holds. */
    public int diagnosisRowCount() {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/diagnosis\\s*code/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return 0;"
                + " return [...tbl.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)"
                + "   && !r.querySelector('input[ng-model=\"Diagnosis.DiagnosisCode\"]')).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** PROBE — the diagnosis detail grid's rows, so "row added" can be checked against what it actually holds. */
    public String diagnosisRows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/diagnosis\\s*code/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return '(no diagnosis table)';"
                + " return [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent).slice(0,80)).filter(t=>t).slice(0,6).join(' ;; '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click the inner <b>Add</b> ({@code AddDiagnosis()}) that commits the diagnosis line.
     *
     * <p>Clicked <b>exactly once</b>: the blind real-click → DOM-click fallback fires it twice, and the second
     * shot runs against the inputs the first one just cleared, so the screen ends up showing
     * "Please Select Diagnosis Description!" over a row that did add. The fallback is only used when the row
     * count says the real click did nothing. Any error message raised by the click is reported.</p>
     */
    public String clickInnerAdd() {
        int before = diagnosisRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__deptAddToasts=[]; if(window.__deptAddObs) window.__deptAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__deptAddToasts.includes(t)) window.__deptAddToasts.push(t); }); };"
                + " window.__deptAddObs=new MutationObserver(grab); window.__deptAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && /adddiagnosis/i.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__deptAddDiag'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";

        boolean realClicked = true;
        try { page.locator("#__deptAddDiag").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1000);
        int after = diagnosisRowCount();
        if (after == before && !realClicked) {                 // only then did nothing happen — click it in the DOM
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__deptAddDiag'); if(e) e.click(); }");
            waitForAngular(1000);
            after = diagnosisRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__deptAddDiag'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__deptAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "diagnosis rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /** Click <b>Submit</b> ({@code fnIUDDepartment()}) and return the toast; also captures the save API's answer. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("department/iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__deptToasts=[]; if(window.__deptObs) window.__deptObs.disconnect();"
                // CLEAR what is already on screen first: the inner Add's own toast is still up, and capturing it
                // would report the previous action's message as Submit's answer.
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                // Stop the message auto-dismissing, so the screenshot below catches the REAL one still on screen.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__deptToasts.includes(t)) window.__deptToasts.push(t); }); };"
                + " window.__deptObs=new MutationObserver(grab); window.__deptObs.observe(document.body,{childList:true,subtree:true});"
                + " const bs=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null);"
                + " const b=bs.find(x=>/fniuddepartment/i.test(x.getAttribute('ng-click')||''))"
                + "   || bs.find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__deptSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__deptSubmit", "submit");
        // Shoot the MOMENT the message is on screen. Waiting for the observer to settle (or for the row check,
        // which navigates back to the list) means the dialog has already closed and the report shows a bare grid.
        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("submit: toast screenshot failed - " + e.getMessage()); break; }
            } else page.waitForTimeout(200);
        }
        if (toastPng == null) System.out.println("submit: no message was ever on screen to screenshot");
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__deptToasts||[]).includes(t)) (window.__deptToasts=window.__deptToasts||[]).push(t); }); }");
        System.out.println("submit: toasts => " + page.evaluate("() => JSON.stringify(window.__deptToasts||[])"));
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object all = page.evaluate("() => JSON.stringify(window.__deptToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        Object r = page.evaluate("() => { const a=window.__deptToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Remember a name the server has just rejected as a duplicate, so the retry picks a different one. */
    public void markNameTaken(String name) { if (name != null) existingNames.add(name.trim().toLowerCase()); }

    /**
     * Did the department really save? Go back to the list and read the grid's own data array for the code — the
     * grid is paged, so the rendered rows are not the whole master.
     */
    public String fetchSavedRow(String code) {
        for (int i = 0; i < 4; i++) {
            // The screen's own list call. GET would be answered from cache with the PRE-save list.
            Object r = page.evaluate("async (code) => { try {"
                    + " const res = await fetch('/api/Department/fetch?_cb=' + Date.now(), {method:'POST', cache:'no-store',"
                    + "   headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify({ExecFlag:'GRID'})});"
                    + " const d = await res.json();"
                    + " const row=(d||[]).find(x=>(''+(x.Code||x.code||'')).trim()===code);"
                    + " return row ? ('id='+(row.DepartmentID||row.id||'?')+' code='+(row.Code||row.code)+' department='+(row.Description||row.description)) : ''; }"
                    + " catch(e) { return 'fetch failed: '+e; } }", code);
            String s = r == null ? "" : r.toString();
            if (!s.isEmpty() && !s.startsWith("fetch failed")) return s;
            page.waitForTimeout(800);
        }
        return "";
    }

    /**
     * The department names already on file, straight from the master's own list call — the grid's scope is not
     * reachable from outside its digest, so reading the API is what actually yields the names.
     */
    public int harvestExistingNamesFromApi() {
        Object r = page.evaluate("async () => { try {"
                + " const res = await fetch('/api/Department/fetch?_cb=' + Date.now(), {method:'POST', cache:'no-store',"
                + "   headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify({ExecFlag:'GRID'})});"
                + " const d = await res.json();"
                + " return (d||[]).map(x=>(''+(x.Description||x.description||'')).trim()).filter(x=>x).join('\\u0001'); }"
                + " catch(e) { return ''; } }");
        existingNames.clear();
        String s = r == null ? "" : r.toString();
        if (!s.isEmpty()) for (String n : s.split("\u0001")) existingNames.add(n.trim().toLowerCase());
        return existingNames.size();
    }
}
