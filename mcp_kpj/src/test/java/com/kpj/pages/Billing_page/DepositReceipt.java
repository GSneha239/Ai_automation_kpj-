package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Billing &gt; <b>Deposit / Receipt</b> (advance) — Page Object.
 *
 * <p>Flow: <b>Billing</b> → <b>Deposit / Receipt</b> (route {@code #/advance}) → in the <b>Add Deposit/Receipt</b>
 * section fill the mandatory fields (<b>Visit</b>, <b>Deposit/Receipt Type</b>, <b>Deposit/Receipt Against</b>,
 * <b>Cash Counter</b>, <b>Amount</b>) → tick the <b>Cash</b> checkbox → click <b>Save</b>
 * ({@code fnSaveadvance()}) → the official receipt report opens in a new tab.</p>
 *
 * <p>Add-form fields: Visit {@code advance.OPDNo}, Deposit/Receipt Type {@code advance.AdvanceTypeid}, Payor
 * {@code Registration.receivablename} (auto), Deposit/Receipt Against {@code advance.againsttype}, Cash Counter
 * {@code advance.CashCounterid}, Amount {@code advance.Balance}, Remark {@code advance.Remark}, Cash checkbox
 * {@code TrueValChk}. NOTE: the screen loads slowly (title momentarily reads "Transfer" mid-load).</p>
 */
public class DepositReceipt extends BasePage {

    public DepositReceipt(Page page) { super(page); }

    public static String ROUTE = "#/advance";
    public String lastVisit = "", lastType = "", lastAgainst = "", lastCounter = "", lastAmount = "";

    // ---- navigation ------------------------------------------------------

    /** Navigate via the Billing menu → Deposit / Receipt, then wait for the Add form (advance.OPDNo select). */
    public boolean navigateTo(String baseUrl) {
        try {
            page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/deposit\\s*\\/?\\s*receipt/i.test(norm(x.textContent)) || /#\\/advance\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__drMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__drMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DepositReceipt.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!page.url().toLowerCase().contains("advance")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1500);
        }
        // slow screen — wait for the Add-form Visit select to render
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='advance.OPDNo')",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("DepositReceipt.nav: Add form not ready"); }
        waitForAngular(1500);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("advance");
    }

    // ---- actions ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Real selectOption of the first real option of {@code selNg} (polls). Returns the chosen text or a marker. */
    private String realSelectFirst(String selNg, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    /** Search a patient by MRN to populate the Visit dropdown: set Reg Type = ALL, enter the MRN, click the
     *  search-by-MRN icon ({@code SearchPatientByMRNo()}), and wait for the Visit list ({@code drpVisit}) to load.
     *  Returns the resulting Visit option count. */
    public int searchPatient(String mrn) {
        // Reg Type stays at its default (IPD) — deposit-record patients are inpatients; changing it clears the search.
        // Set MRN via ngModel and fire SearchPatientByMRNo() (real click so the ng-click handler runs).
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='advance.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const b=[...document.querySelectorAll('button,a,i,span,img')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__mrnSearch'; }", mrn);
        try { page.locator("#__mrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__mrnSearch'); if(e) e.removeAttribute('id'); }");
        // wait for the Visit list (drpVisit) or the advance.OPDNo select options to populate
        try {
            page.waitForFunction("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&Array.isArray(s.drpVisit)) n=Math.max(n,s.drpVisit.length); }catch(e){} }); if(n>0) return true; const e=document.querySelector(\"select[ng-model='advance.OPDNo']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(700);
        return visitCount();
    }

    /** Search the deposit-records grid over the last {@code months} months and return distinct patient MRNs
     *  (candidates to deposit against). Stays entirely on the Deposit/Receipt screen. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> candidateMrnsFromGrid(int months) {
        String from = java.time.LocalDate.now().minusMonths(months).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String to = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        page.evaluate("(d) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; set('advance1.fromdate', d.from); set('advance1.todate', d.to);"
                + " const b=[...document.querySelectorAll('button,a,i,span')].find(x=>/GetAdvance|fnSearch|Search/i.test(x.getAttribute('ng-click')||'') && !/SearchPatientByMRNo|openMic/.test(x.getAttribute('ng-click')||'')) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim())); if(b){ b.id='__grdSrch'; } }",
                java.util.Map.of("from", from, "to", to));
        try { page.locator("#__grdSrch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__grdSrch'); if(e) e.removeAttribute('id'); }");
        page.waitForTimeout(4000);
        Object res = page.evaluate("() => { let best=-1, arr=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!s) return; const scan=(a)=>{ if(Array.isArray(a)&&a.length&&typeof a[0]==='object'&&Object.keys(a[0]).includes('MRNo')&&a.length>best){ best=a.length; arr=a; } }; if(s.grid&&s.grid.options&&s.grid.options.data) scan(s.grid.options.data); for(const k of Object.keys(s)){ try{ scan(s[k]); }catch(e){} } }catch(e){} });"
                + " const out=[]; const seen=new Set(); if(arr){ for(const r of arr){ const m=r.MRNo && (''+r.MRNo).trim(); if(m && !seen.has(m)){ seen.add(m); out.push(m); } } } return out; }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (res instanceof java.util.List) for (Object o : (java.util.List<Object>) res) if (o != null) list.add(o.toString().trim());
        System.out.println("candidateMrnsFromGrid: " + list.size() + " distinct MRNs => " + list);
        return list;
    }

    /** How many visits the patient has (max of the drpVisit scope list and the Visit select's real options). */
    public int visitCount() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&Array.isArray(s.drpVisit)) n=Math.max(n,s.drpVisit.length); }catch(e){} }); const e=document.querySelector(\"select[ng-model='advance.OPDNo']\"); if(e){ const o=[...e.options].filter(x=>x.value && (x.textContent||'').trim() && !/^-*\\s*select/i.test((x.textContent||'').trim())).length; n=Math.max(n,o); } return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Select an option of {@code selNg} whose text matches {@code preferRe}; if none, the first real option that
     *  does NOT match {@code avoidRe}. Returns the chosen text (or a marker). */
    private String realSelectPreferring(String selNg, String preferRe, String avoidRe, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.sel); const opts=[...e.options]; const real=o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim()); const pref=new RegExp(a.pref,'i'); const avoid=new RegExp(a.avoid,'i');"
                + " let i=opts.findIndex(o=>real(o) && pref.test((o.textContent||'').trim())); if(i<0) i=opts.findIndex(o=>real(o) && !avoid.test((o.textContent||'').trim())); if(i<0) i=opts.findIndex(real); return i; }",
                java.util.Map.of("sel", sel, "pref", preferRe, "avoid", avoidRe));
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectPreferring " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    /** Fill ONLY the mandatory (starred) Add Deposit/Receipt fields: Deposit/Receipt Type*, Deposit/Receipt
     *  Against*, Cash Counter*, Payor*, Amount*. The Visit is selected first only because that is what populates
     *  the mandatory Payor; the non-mandatory fields (Date, Remark) are left untouched. */
    public String fillMandatory() {
        lastVisit = realSelectFirst("advance.OPDNo", 12000);   // not starred, but selecting it populates Payor*
        waitForAngular(900);
        // Deposit/Receipt Type* — prefer a CASH type; never "Company" (that needs a payor company).
        lastType = realSelectPreferring("advance.AdvanceTypeid", "cash|advance|deposit|patient", "company|corporate|credit|insurance", 8000);
        waitForAngular(500);
        // Deposit/Receipt Against* (server-validated: "Please select Deposit Against dropdown!")
        lastAgainst = realSelectFirst("advance.againsttype", 8000);
        waitForAngular(500);
        // Cash Counter*
        lastCounter = realSelectFirst("advance.CashCounterid", 8000);
        waitForAngular(400);
        // Amount*
        setNg("advance.Balance", "100");
        lastAmount = "100";
        waitForAngular(300);
        // Payor* — auto-populated from the Visit; capture for the report.
        String payor = String.valueOf(page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='Registration.receivablename']\")].find(x=>x.offsetParent!==null); return e?e.value:''; }")).trim();
        return "Type=" + lastType + " | Against=" + lastAgainst + " | CashCounter=" + lastCounter
                + " | Payor=" + (payor.isEmpty() ? "(auto)" : payor) + " | Amount=100";
    }

    /** Tick the <b>Cash</b> checkbox ({@code TrueValChk}). */
    public String selectCash() {
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='TrueValChk']\")].find(x=>x.offsetParent!==null); if(!cb) return false; cb.id='__cashChk'; return true; }");
        String r = "(none)";
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__cashChk").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); r = "Cash ticked"; }
            catch (Exception e) { try { page.locator("#__cashChk").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); r = "Cash clicked"; } catch (Exception e2) { System.out.println("selectCash: failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__cashChk'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return r;
    }

    /** Click <b>Save</b> ({@code fnSaveadvance}); capture the receipt report that opens in a new tab. Returns the
     *  new Page (report tab), or null if no tab opened; also collects any toast into {@link #lastToast}. */
    public String lastToast = "";
    public Page saveAndOpenReport() {
        page.evaluate("() => { window.__drToasts=[]; if(window.__drObs) window.__drObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__drToasts.includes(t)) window.__drToasts.push(t); }); };"
                + " window.__drObs=new MutationObserver(grab); window.__drObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        int tabsBefore = page.context().pages().size();
        // real click the Save button (fnSaveadvance)
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/fnSaveadvance/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__drSave'; return true; }");
        Page report = null;
        if (Boolean.TRUE.equals(tagged)) {
            try {
                report = page.context().waitForPage(() -> {
                    try { page.locator("#__drSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                });
            } catch (Exception e) {
                System.out.println("saveAndOpenReport: no new tab within timeout - " + e.getClass().getSimpleName());
            }
        } else {
            System.out.println("saveAndOpenReport: Save button not found");
        }
        // capture toast (fallback)
        try {
            page.waitForFunction("() => (window.__drToasts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        Object t = page.evaluate("() => { const a=window.__drToasts||[]; return a.find(x=>/saved|success|receipt|deposit|please|enter|select|amount|required/i.test(x)) || a[0] || ''; }");
        lastToast = t == null ? "" : t.toString().trim();
        if (report == null && page.context().pages().size() > tabsBefore) {
            report = page.context().pages().get(page.context().pages().size() - 1);
        }
        if (report != null) { try { report.waitForLoadState(); report.waitForTimeout(2500); } catch (Exception ignore) { } }
        return report;
    }
}
