package com.kpj.pages.Flow_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Ip.InPatients.OccupancyList;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Billing IP flow</b> (workflow document "Billing Complete Workflow OP + IP v3.1", Part D stages D1-D5, with the
 * Part B masters that drive the IP charges) - screen-level actions for the screens the flow touches that have NO page
 * object of their own yet. Everything else is delegated to existing page objects; the test class
 * ({@code tests.Flow_page.BillingIP}) owns the sequence, assertions and reporting.
 *
 * <pre>
 *  D1 IP &gt; Admission                         -> pages.Ip.Admission (reused section by section) + {@link #admitNewSelfPayPatient}
 *  D2 IP &gt; Inpatients &gt; Occupancy List &gt; EMR  -> pages.Ip.InPatients.OccupancyList (reused: menu, search, row) + {@link #openEmrFromOccupancy}
 *     EMR orders, work-order accept, dispense -> pages.Flow_page.BillingOP (reused unchanged: same EMR / work-order screens as OP C2-C3)
 *  D3 Billing &gt; IPD Charges                  -> pages.Billing_page.IpdCharges (reused) + {@link #searchIpdByMrn}, {@link #readChargeLines}
 *  D4 Billing &gt; IPD Bill (Interim, Final)    -> pages.Billing_page.IpdBill (reused) + BillingOP.saveFinalBill (payment window, reused)
 *  D5 Bill Estimation, Time Trail, Additional Deposit Request -> NEW here
 * </pre>
 *
 * <p><b>Locator status.</b> Admission, IPD Charges, IPD Bill and the Occupancy List grid use locators already proven by
 * their own page objects. The screens that have no page object yet - the Occupancy List's <i>EMR</i>, <i>Time Trail</i>
 * and <i>Additional Deposit Request</i> actions and the <i>Bill Estimation-Service</i> screen - are located by visible
 * text / {@code ng-click} name (the document gives names, not element ids). Each reports a control dump
 * ({@link #lastDump}) when it cannot find its control, so the first run on a new build is a one-pass fix.</p>
 */
public class BillingIP extends BasePage {

    public BillingIP(Page page) { super(page); }

    /** Control/dialog dump taken on a failure - appended to the step's Actual text by the test class. */
    public String lastDump = "";
    public String lastToast = "";
    /** Plain-language notes about what the admission actually used (bed, ward, payor ...). */
    public String admissionNotes = "";
    public String patientLine = "";
    public String nric = "";
    /** Optional: an MRN that is ALREADY admitted ({@code -Dbillingip.mrn}) - used instead of admitting a new patient (e.g. when no bed is vacant). */
    public String existingMrn = "";
    public String lastFallRiskNote = "";
    public String lastIpdNo = "";
    public String lastHeader = "";
    public String lastBedCharge = "";
    public String lastTimeTrail = "";
    public String lastEstimate = "";

    /** The services ordered in EMR (name as picked by the screen) - checked later against IPD Charges. */
    public final List<String> orderedServices = new ArrayList<>();

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && (e.offsetParent!==null || (e.getClientRects && e.getClientRects().length>0));"
          + "const ngEl=(m)=>[...document.querySelectorAll('[ng-model]')].filter(e=>e.getAttribute('ng-model')===m);"
          + "const setEl=(e,v)=>{ if(!e) return false; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; };"
          + "const gridData=()=>{ let d=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope();"
          + "  if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)&&(!d||s.grid.options.data.length>d.length)) d=s.grid.options.data; }catch(e){} }); return d; };";

    /** Start recording toasts (the app's toastr fades in ~1s, so poll-and-read is too late). */
    public void watchToasts() {
        page.evaluate("() => { window.__bipToasts=[]; if(window.__bipObs) window.__bipObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('[class*=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__bipToasts.includes(t)) window.__bipToasts.push(t); }); };"
                + " window.__bipObs=new MutationObserver(grab); window.__bipObs.observe(document.body,{childList:true,subtree:true,characterData:true}); }");
    }

    public String toasts() {
        Object t = page.evaluate("() => (window.__bipToasts||[]).join(' | ')");
        lastToast = t == null ? "" : t.toString().trim();
        return lastToast;
    }

    // ============================================================================================================
    //  D1  IP > Admission  (pages.Ip.Admission is reused section by section, in the order of its own test TC14)
    // ============================================================================================================

    /**
     * Admit a NEW inpatient on <b>Self</b> payor into a vacant bed, exactly as {@code tests.Ip.Admission} does, and Save.
     * Document D1: patient + payor information (as OP stage 1), admission information, choose the ward, click an empty bed.
     *
     * @return null on success, else the reason the admission did not complete
     */
    public String admitNewSelfPayPatient(com.kpj.pages.Ip.Admission ip) {
        StringBuilder notes = new StringBuilder();
        if (!ip.navigateViaMenu()) return "IP > Admission did not open (" + page.url() + ")";
        String patient = ip.fillPatientSection();
        patientLine = patient;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("NRIC (\\d{12})").matcher(patient);
        nric = m.find() ? m.group(1) : "";
        if (patient.contains("City=(empty)") || patient.contains("State=(empty)"))
            return "Patient section incomplete - the postcode did not fill City/District + State | " + patient;
        if (com.kpj.pages.Ip.Admission.isForeign()) {
            String visa = new com.kpj.pages.Op_page.RegistrationPage(page).fillVisaDetails(com.kpj.core.DevHisBase.ATTACH);
            if (!visa.matches("(?s).*VisaDetails=[1-9].*")) return "Visa Details (mandatory for a passport holder) were not added | " + visa;
            notes.append("visa added; ");
        }
        String nok = ip.fillNokSection();
        boolean nokAdded = nok != null && nok.contains("added=true");
        notes.append(fillTinNumbers(ip, nokAdded)).append("; ");
        nokAdded = nokAdded || lastNokAdded;
        notes.append("NOK: ").append(nokAdded ? "added" : "not confirmed").append("; ");
        String payor = ip.fillPayorSection();
        if (payor == null || !payor.toLowerCase().contains("self")) return "Payor was not set to Self | " + payor;
        notes.append("Payor: ").append(payor).append("; ").append(ensureSelfFinancialClass()).append("; ");
        notes.append("Admission: ").append(ip.fillAdmissionSection()).append("; ");
        notes.append(ensureInpatientEncounterType()).append("; ");
        String bed = ip.selectRoomTypeAndWard();
        if (bed == null || !bed.contains("RoomType=") || !bed.contains("Ward=") || bed.startsWith("ERR"))
            return "Room Type / Ward could not be selected | " + bed;
        String bedRow = ip.tickVacantBedRow();
        if (bedRow == null || !bedRow.startsWith("Bed="))
            return "No vacant bed could be ticked in the Census Bed List (" + bed + ") | " + bedRow;
        notes.append(bed).append(" | ").append(bedRow).append("; ");
        String docs = ip.fillAdditionalDoctors();
        notes.append("Additional doctors: ").append(docs == null ? "-" : docs);
        admissionNotes = notes.toString();
        notes.append("; ").append(fillGuaranteeFieldsIfRequired());
        admissionNotes = notes.toString();
        String toast = ip.saveAdmissionAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("admitted");
        if (!ok) {
            String flagged = ip.appFlaggedMandatory();
            String silent = ip.diagnoseSilentSave();
            return "Save was not confirmed: " + (toast == null || toast.isEmpty() ? "no success toast" : toast)
                    + (flagged.isEmpty() ? "" : " | the screen flagged: " + flagged) + (silent.isEmpty() ? "" : " | CAUSE: " + silent)
                    + " | still empty (the app's own scan): " + ip.describeEmptyMandatory()
                    + (ip.clearedFieldsSummary().isEmpty() ? "" : " | cleared after entry: " + ip.clearedFieldsSummary());
        }
        closeExtraTabs();   // admission report / wrist band / consent tabs open after Save - the flow drives the main tab
        return null;
    }

    public boolean lastNokAdded = false;

    /**
     * This build marks GL Reference No., GL Approved Amt. and Estimated Amount mandatory on the admission form (devhis, 10/2026), even for a
     * Self payor. When any of them is empty, fill it with a test value. The approved amount is kept at 1.00 so every charge is above the
     * guarantee limit and falls to the self-payer invoice (document C5 / D4), which is what this Self-pay flow bills and settles.
     */
    public String fillGuaranteeFieldsIfRequired() {
        Object r = page.evaluate("(a) => { " + JS + " const out=[]; const set=(m,v)=>{ const e=ngEl(m).find(vis)||ngEl(m)[0]; if(!e) return; if(!(e.value||'').trim()){ setEl(e,v); out.push(m.replace('Registration.','')+'='+v); } };"
                + " set('Registration.GLNumber',a.gl); set('Registration.GLAmount','1.00'); set('Registration.EstimatedAmount','1000.00'); return out.join(', '); }",
                java.util.Map.of("gl", "GL" + System.currentTimeMillis() % 100000000L));
        String s = r == null ? "" : r.toString();
        waitForAngular(500);
        return s.isEmpty() ? "GL / estimate fields: not required or already filled" : "GL / estimate fields filled (required by the form): " + s;
    }


    /**
     * A Billing flow admits a SELF-paying patient. The admission page picks Payor Mode "ASSOCIATE COMPANY"; where that behaves as a company
     * payor the save demands GL Reference No. / GL Approved Amt. / Estimated Amount (devhis, 10/2026). When it did, switch Payor Mode to
     * "ALL FINANCIAL CLASS" (the self row the admission test reported before) and set Payor Status = Self again.
     */
    public String ensureSelfFinancialClass() {
        Object r = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypeid'); if(!s) return ''; const norm=t=>(t||'').replace(/\s+/g,' ').trim();"
                + " const cur=norm((s.options[s.selectedIndex]||{}).text); const all=[...s.options].find(o=>/all financial class/i.test(o.text));"
                + " return JSON.stringify({cur, all: all? norm(all.text):'', opts:[...s.options].map(o=>norm(o.text)).filter(Boolean)}); }");
        String j = String.valueOf(r);
        if (!j.startsWith("{")) return "Payor Mode: dropdown not found";
        String cur = j.replaceAll("(?s).*\"cur\":\"([^\"]*)\".*", "$1");
        String all = j.replaceAll("(?s).*\"all\":\"([^\"]*)\".*", "$1");
        if (!cur.toUpperCase().contains("ASSOCIATE") || all.isEmpty() || !j.contains("\"all\":\"")) return "Payor Mode kept: " + cur;
        try {
            page.locator("select[ng-model='Registration.receivabletypeid']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(all),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
            waitForAngular(700);
            page.evaluate("() => { const s=document.querySelector(\"select[ng-model='Registration.PayerTypeId']\"); if(!s) return; const o=[...s.options].find(x=>/^\s*self\s*$/i.test(x.text)); if(o){ s.value=o.value; try{ const c=angular.element(s).controller('ngModel'); c.$setViewValue(o.value); c.$render(); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true})); } }");
            waitForAngular(600);
            try { page.locator("#txtSponsorName").fill("self"); } catch (Exception ignore) { }
            waitForAngular(400);
        } catch (Exception e) { return "Payor Mode: could not switch " + cur + " -> " + all + " (" + String.valueOf(e.getMessage()).split("\n")[0] + ")"; }
        return "Payor Mode switched " + cur + " -> " + all + " (self-pay; ASSOCIATE COMPANY makes the GL fields mandatory)";
    }

    /**
     * TIN Number on the admission form. The patient TIN ({@code Registration.tinno}) is filled by the admission page; this checks it took and
     * fills it when empty. The Next-of-Kin TIN ({@code Registration.KinTinNo}) is NOT filled by the admission page and the app then refuses the
     * NOK Add ("TIN for Next of Kin is mandatory!"): when the NOK was not added, fill it and click Add again (same fix as OP registration).
     */
    public String fillTinNumbers(com.kpj.pages.Ip.Admission ip, boolean nokAdded) {
        lastNokAdded = nokAdded;
        String tin = "TIN" + String.format("%09d", Math.abs(System.nanoTime() % 1_000_000_000L));
        Object r = page.evaluate("(t) => { " + JS + " const p=ngEl('Registration.tinno').find(vis) || ngEl('Registration.tinno')[0]; let pv=p? (p.value||'') : '(no field)';"
                + " if(p && !pv.trim()){ setEl(p,t); pv=t+' (filled by the flow)'; } return pv; }", tin);
        String patientTin = String.valueOf(r);
        String kinTin = "-";
        if (!nokAdded) {
            String kt = "TIN" + String.format("%09d", Math.abs((System.nanoTime() / 7) % 1_000_000_000L));
            Object k = page.evaluate("(t) => { " + JS + " const e=ngEl('Registration.KinTinNo')[0]; if(!e) return ''; if(!(e.value||'').trim()) setEl(e,t); return e.value; }", kt);
            kinTin = k == null ? "" : k.toString();
            if (!kinTin.isEmpty()) {
                waitForAngular(500);
                page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(1500);
                lastNokAdded = ip.kinGridRowCount() > 0;
            } else kinTin = "(no NOK TIN field)";
        }
        return "TIN: patient=" + patientTin + ", NOK=" + (nokAdded ? "(NOK added without it)" : kinTin) + (nokAdded || lastNokAdded == false ? "" : " -> NOK added");
    }

    /**
     * The admission page assigns Encounter Type by a hard-coded id; on an environment where that id is another type (devhis: it falls back to
     * the first option, DayCare) the save then demands GL Reference No. / GL Approved Amt. / Estimated Amount. An inpatient admission needs an
     * inpatient Encounter Type: pick the option whose text says inpatient / IPD from the live list and report the list.
     */
    public String ensureInpatientEncounterType() {
        Object r = page.evaluate("() => { const norm0=t=>(t||'').replace(/\s+/g,' ').trim(); const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,div'); for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm0(l.textContent)) return norm0(l.textContent); n=n.parentElement; } return ''; }; const s=[...document.querySelectorAll('select')].find(x=>/EncounterType/i.test(x.getAttribute('ng-model')||'') || /encounter\s*type/i.test(labelOf(x))); if(!s) return 'Encounter Type: no dropdown found'; s.id='__bipEnc';"
                + " const opts=[...s.options].filter(o=>o.value && !/^-*\s*select/i.test(o.text.trim())).map(o=>o.text.trim());"
                + " const cur=(s.options[s.selectedIndex]||{}).text||''; const want=[...s.options].find(o=>/in.?patient|ipd/i.test(o.text));"
                + " return JSON.stringify({cur:cur.trim(), opts, want: want? want.text.trim():''}); }");
        String j = String.valueOf(r);
        if (!j.startsWith("{")) return j;
        String cur = j.replaceAll("(?s).*\"cur\":\"([^\"]*)\".*", "$1");
        String want = j.replaceAll("(?s).*\"want\":\"([^\"]*)\".*", "$1");
        String opts = j.replaceAll("(?s).*\"opts\":\\[([^\\]]*)\\].*", "$1");
        if (want.isEmpty()) return "Encounter Type: no inpatient option among [" + opts + "]; left as " + cur;
        if (want.equalsIgnoreCase(cur)) return "Encounter Type: " + cur;
        Object done = page.evaluate("(w) => { const s=document.getElementById('__bipEnc'); if(!s) return 'select gone'; const o=[...s.options].find(x=>x.text.trim()===w); if(!o) return 'option gone';"
                + " s.selectedIndex=[...s.options].indexOf(o); o.selected=true;"
                + " s.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(s).triggerHandler('change'); }catch(e){} try{ if(window.jQuery) jQuery(s).trigger('change'); }catch(e){}"
                + " return (s.options[s.selectedIndex]||{}).text.trim(); }", want);
        page.evaluate("() => { const e=document.getElementById('__bipEnc'); if(e) e.removeAttribute('id'); }");
        if (!want.equalsIgnoreCase(String.valueOf(done))) return "Encounter Type: could not select '" + want + "' (" + done + "); left as " + cur;
        waitForAngular(800);
        return "Encounter Type: " + cur + " -> " + want + " (options: " + opts + ")";
    }

    /** Close every browser tab except the flow's own, after a screen opened report/EMR tabs. */
    public void closeExtraTabs() {
        waitForAngular(2500);
        try {
            for (Page pg : new ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
        } catch (Exception ignore) { }
        page.bringToFront();
    }

    // ============================================================================================================
    //  D2  Occupancy List
    // ============================================================================================================

    /**
     * Open IP &gt; Inpatients &gt; Occupancy List, search a one-month range ending today and find the new admission.
     * The admission screen does not hand the MRN back, so the grid row is matched on the NRIC captured in D1 (the
     * patient name is the same fixed value every run). Retries because the list can lag the save.
     *
     * @return {MRN, IPD No, name} or null when the admission is not listed
     */
    public String[] lookupAdmission(OccupancyList occ) {
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!occ.navigateViaMenu()) { waitForAngular(2500); continue; }
            occ.searchOneMonthToToday();
            Object r = page.evaluate("(a) => { " + JS + " const d=gridData(); if(!d) return null;"
                    + " const key=(o,re)=>{ for(const k in o){ if(re.test(k) && o[k]!=null && String(o[k]).trim()) return String(o[k]).trim(); } return ''; };"
                    + " let rows = a.mrn ? d.filter(x=>key(x,/^(mrno|mrn|mrnno)$/i)===a.mrn) : (a.nric ? d.filter(x=>JSON.stringify(x).indexOf(a.nric)>=0) : []);"
                    + " if(!rows.length){ const rx=new RegExp(a.name,'i'); rows=d.filter(x=>rx.test(key(x,/patientname|patientfullname/i)));"
                    + "   rows.sort((p,q)=>Number(key(q,/^(mrno|mrn|mrnno)$/i))-Number(key(p,/^(mrno|mrn|mrnno)$/i))); rows=rows.slice(0,1); }"
                    + " if(!rows.length) return null; const x=rows[0];"
                    + " return [key(x,/^(mrno|mrn|mrnno)$/i), key(x,/ipd.?no|admissionno|admno/i), key(x,/patientname|patientfullname/i)]; }",
                    Map.of("nric", nric == null ? "" : nric, "name", "John Peter", "mrn", existingMrn == null ? "" : existingMrn));
            if (r instanceof List && !((List<?>) r).isEmpty() && !String.valueOf(((List<?>) r).get(0)).isBlank()) {
                List<?> l = (List<?>) r;
                lastIpdNo = String.valueOf(l.get(1));
                return new String[]{ String.valueOf(l.get(0)), String.valueOf(l.get(1)), String.valueOf(l.get(2)) };
            }
            waitForAngular(3000);
        }
        return null;
    }

    /** Select the grid row of {@code mrn} with a real click on its selection cell (a synthetic API select does not enable the footer actions). */
    public boolean selectOccupancyRow(String mrn) {
        Object tag = page.evaluate("(mrn) => { const match=ent=>{ for(const k in ent){ if(/^(mrno|mrn|mrnno)$/i.test(k) && (''+ent[k]).trim()===mrn) return true; } return false; };"
                + " const bodyRows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " let idx=-1; for(let i=0;i<bodyRows.length;i++){ let ent=null; try{ent=angular.element(bodyRows[i]).scope().row.entity;}catch(e){} if(ent&&match(ent)){ idx=i; break; } }"
                + " if(idx<0) return 'notfound';"
                + " const leftRows=[...document.querySelectorAll('.ui-grid-render-container-left .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " const cb=(leftRows[idx]&&leftRows[idx].querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]')) || bodyRows[idx].querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]');"
                + " if(cb){ cb.id='__bipSelCb'; return 'cb'; }"
                + " let api=null; document.querySelectorAll('*').forEach(el=>{ if(api)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api) api=s.grid.api; }catch(e){} });"
                + " if(api){ const ent=angular.element(bodyRows[idx]).scope().row.entity; try{api.selection.clearSelectedRows();}catch(e){} api.selection.selectRow(ent); return 'api'; } return 'notfound'; }", mrn);
        String how = String.valueOf(tag);
        if ("notfound".equals(how)) return false;
        if ("cb".equals(how)) {
            try { page.locator("#__bipSelCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("selectOccupancyRow: real click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__bipSelCb'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(800);
        return true;
    }

    /** Click the first visible control whose text equals {@code textRe} or whose ng-click / title / class matches {@code attrRe}. */
    private boolean clickControl(String textRe, String attrRe) {
        Object r = page.evaluate("(a) => { " + JS + " const tr=new RegExp(a.t,'i'), ar=new RegExp(a.a,'i');"
                + " const c=[...document.querySelectorAll('button,a,li,span,i,img,[ng-click],[title]')].filter(vis).find(x=>{"
                + "   const own=norm(x.children.length? (x.innerText||'') : x.textContent); return (own.length<40 && tr.test(own)) || ar.test(x.getAttribute('ng-click')||'') || ar.test(x.getAttribute('title')||'') || ar.test(x.getAttribute('data-original-title')||''); });"
                + " if(!c) return false; if(c.disabled || c.classList.contains('disabled') || c.classList.contains('is-disabled')) return 'disabled'; c.scrollIntoView({block:'center'}); c.click(); return true; }",
                Map.of("t", textRe, "a", attrRe));
        return Boolean.TRUE.equals(r);
    }

    private boolean fallRiskModalOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(m.textContent||''))"));
    }

    /**
     * Document D2.1: on the Occupancy List select the patient to go to <b>EMR</b>. The EMR action is found by name (the document
     * gives no element id); EMR may open in a new tab (adopted into the main tab by {@code BillingOP.adoptEmrTab}) and a
     * Fall Risk Assessment may be demanded first (completed through the existing {@code OccupancyList} helper).
     *
     * @return null once the EMR screen is open, else the reason it did not
     */
    public String openEmrFromOccupancy(OccupancyList occ, BillingOP op, String mrn) {
        if (!occ.navigateViaMenu()) return "Occupancy List did not open (" + page.url() + ")";
        occ.searchOneMonthToToday();
        if (!selectOccupancyRow(mrn)) return "MRN " + mrn + " is not on the Occupancy List grid";
        watchToasts();
        boolean clicked = clickControl("^\\s*EMR\\s*$", "(^|[^a-z])emr($|[^a-z])");
        if (!clicked) {
            // some builds keep the EMR entry inside an Action menu
            clickControl("^\\s*action\\s*$", "op-act-toggle|actionmenu");
            waitForAngular(700);
            clicked = clickControl("^\\s*EMR\\s*$", "(^|[^a-z])emr($|[^a-z])");
        }
        if (!clicked) { lastDump = op.describeVisibleControls(); return "No EMR action found for the selected admission on the Occupancy List"; }
        boolean fallDone = false;
        long lastClick = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            if (op.onEmr() || op.adoptEmrTab()) break;
            if (fallRiskModalOpen() && !fallDone) {
                String toast = occ.fillFallRiskAndApply();
                lastFallRiskNote = "Fall Risk Assessment demanded before EMR (not in the workflow document) - " + toast;
                fallDone = true;
                waitForAngular(2500);
                lastClick = System.currentTimeMillis();
            } else if (fallDone && !fallRiskModalOpen() && System.currentTimeMillis() - lastClick > 5000) {
                clickControl("^\\s*EMR\\s*$", "(^|[^a-z])emr($|[^a-z])");   // assessment saved: the second click passes the gate
                lastClick = System.currentTimeMillis();
            }
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='EMRTagID')",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            lastDump = op.describeVisibleControls();
            return "EMR screen did not open (url " + page.url() + ")" + (lastFallRiskNote.isEmpty() ? "" : " | " + lastFallRiskNote) + " | toasts: " + toasts();
        }
        waitForAngular(1500);
        return null;
    }

    // ============================================================================================================
    //  D3  Billing > IPD Charges
    // ============================================================================================================

    /**
     * Document D3.1 "Search by MRN": type the MRN into the screen's MRN box and click its search button
     * ({@code SearchPatientByMRNo}, same wiring as OPD Charges / IPD Bill). Success = the admission loaded (IPD No. present).
     * Falls back to the patient-picker popup of {@code IpdCharges} when the MRN box is not on this screen.
     */
    public boolean searchIpdByMrn(com.kpj.pages.Billing_page.IpdCharges charges, String mrn) {
        Object tagged = page.evaluate("(m) => { " + JS + " const e=[...document.querySelectorAll('input')].filter(vis).find(x=>x.id==='textinput' || /(^|\\.)MRNo?$/i.test(x.getAttribute('ng-model')||''));"
                + " if(!e) return false; setEl(e,m); return true; }", mrn);
        if (!Boolean.TRUE.equals(tagged)) { lastDump = describeVisibleControls(); return false; }
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(2500);
        lastHeader = readIpdHeader();
        boolean loaded = !lastIpdOnScreen().isEmpty();
        if (!loaded) lastDump = describeVisibleControls();
        return loaded;
    }

    private String lastIpdOnScreen() {
        Object r = page.evaluate("() => { let ipd=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.PatientDetails&&s.PatientDetails.IPDNo) ipd=''+s.PatientDetails.IPDNo; }catch(e){} }); return ipd; }");
        return r == null ? "" : r.toString().trim();
    }

    /** Document Table 50 header: IPD No., Admission Date, Ward, Room Type, Bed, Department, Doctor, Payor, Credit Limit, Approved Amount, Advance Balance. */
    public String readIpdHeader() {
        Object r = page.evaluate("() => { let out=''; document.querySelectorAll('*').forEach(el=>{ if(out) return; try{ const s=angular.element(el).scope(); const p=s&&s.PatientDetails;"
                + " if(p&&p.IPDNo){ const want=/ipdno|admission.?date|ward|room|bed|department|doctor|payor|credit|approved|advance/i; const parts=[];"
                + "   for(const k in p){ const v=p[k]; if(want.test(k) && v!==null && v!==undefined && typeof v!=='object' && String(v).trim()) parts.push(k+'='+String(v).trim().slice(0,40)); }"
                + "   out=parts.slice(0,14).join(' | '); } }catch(e){} }); return out; }");
        return r == null ? "" : r.toString();
    }

    /** One entry per charge line of the loaded grid: {@code name|amount} (amount = the first Net/Total/Amount/Rate column found, '' if none). */
    @SuppressWarnings("unchecked")
    public List<String> readChargeLines() {
        Object r = page.evaluate("() => { " + JS + " const d=gridData(); if(!d) return [];"
                + " return d.map(row=>{ const nm=row.ServiceName||row.servicename||row.serviceitemname||row.ServiceCode||'';"
                + "   let amt=''; for(const re of [/^net.?amount$/i,/^total.?amount$/i,/^amount$/i,/^(rate|price|unitrate)$/i]){ const k=Object.keys(row).find(x=>re.test(x)); if(k && row[k]!==null && row[k]!==''){ amt=String(row[k]); break; } }"
                + "   return nm+'|'+amt; }); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public static String lineName(String line) { int i = line.lastIndexOf('|'); return i < 0 ? line : line.substring(0, i); }
    public static String lineAmount(String line) { int i = line.lastIndexOf('|'); return i < 0 ? "" : line.substring(i + 1); }

    /**
     * Part B masters, observed through their effect: the Bed master's bed-charge / nursing-charge service must have dropped as an
     * auto charge at admission (document D1 "adds the bed charge"), and every line must carry a price from the Service / Pricing
     * Policy masters. Returns "" when both hold, else what is missing.
     */
    public String mastersProblems(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        String bed = null;
        for (String l : lines) if (lineName(l).toLowerCase().matches(".*(bed|room|ward|nursing).*")) { bed = l; break; }
        lastBedCharge = bed == null ? "" : bed;
        if (bed == null) sb.append("no bed / room / ward / nursing auto-charge line (Bed master bed-charge service not linked or not dropped at admission); ");
        List<String> unpriced = new ArrayList<>();
        for (String l : lines) {
            String a = lineAmount(l).replaceAll("[^0-9.\\-]", "");
            if (!a.isEmpty() && a.matches("-?\\d+(\\.\\d+)?") && Double.parseDouble(a) == 0.0) unpriced.add(lineName(l));
        }
        if (!unpriced.isEmpty()) sb.append("zero-priced line(s) (Service / Pricing Policy master has no price for Inpatient): ").append(unpriced);
        return sb.toString().trim();
    }

    // ============================================================================================================
    //  D4  Billing > IPD Bill
    // ============================================================================================================

    /** Document D4.3: choose the <b>Interim</b> radio ({@code PatientDetails.isdraftbill} not 1) and Credit off. */
    public boolean selectInterimBill() {
        Object r = page.evaluate("() => { " + JS + " const i=ngEl('PatientDetails.isdraftbill').find(e=>e.value!=='1'); if(!i) return false; if(!i.checked) i.click();"
                + " const cr=ngEl('IsCredite')[0]; if(cr && cr.checked) cr.click(); return i.checked; }");
        waitForAngular(400);
        return Boolean.TRUE.equals(r);
    }

    /** Close a payment window / dialog left open by an Interim save (no payment is taken for an interim bill). */
    public String closeOpenDialogs() {
        Object r = page.evaluate("() => { " + JS + " const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].filter(m=>m.getBoundingClientRect().width>0 && norm(m.textContent));"
                + " const out=[]; boxes.forEach(box=>{ const b=[...box.querySelectorAll('button,a')].find(x=>vis(x)&&/^(close|cancel|ok|no)$/i.test(norm(x.textContent))); if(b){ out.push(norm(box.textContent).slice(0,60)+' -> '+norm(b.textContent)); b.click(); } }); return out.join(' ; '); }");
        waitForAngular(800);
        return r == null ? "" : r.toString();
    }

    // ============================================================================================================
    //  D5  IP-only support screens
    // ============================================================================================================

    /** Click a top menu then the submenu link whose text matches {@code linkRe}; true when the URL then matches {@code routeRe}. */
    private boolean openMenuByText(String topMenu, String linkRe, String routeRe) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        for (int attempt = 0; attempt < 3; attempt++) {
            page.evaluate("(m) => { const a=[...document.querySelectorAll('a')].find(x=>x.textContent.replace(/\\s+/g,' ').trim()===m && x.getAttribute('href')==='#'); if(a) a.click(); }", topMenu);
            waitForAngular(900);
            Object href = page.evaluate("(re) => { const a=[...document.querySelectorAll('a[href]')].find(x=>new RegExp(re,'i').test(x.textContent.replace(/\\s+/g,' ').trim()) && x.getAttribute('href')!=='#'); if(!a) return ''; a.click(); return a.getAttribute('href'); }", linkRe);
            waitForAngular(2500);
            if (page.url().toLowerCase().matches(".*(" + routeRe.toLowerCase() + ").*")) return true;
        }
        return false;
    }

    /**
     * Document D5 "Bill Estimation": Billing &gt; Bill Estimation-Service. Opens the screen, loads the MRN when the screen has an MRN
     * box and reports what is shown. The document does not list the screen's steps (open point: Table 75), so nothing is printed or saved.
     *
     * @return null when the screen opened, else the reason it did not
     */
    public String openBillEstimation(String mrn) {
        if (!openMenuByText("Billing", "bill\\s*estimation", "estimat")) { lastDump = describeVisibleControls(); return "Billing > Bill Estimation-Service did not open (" + page.url() + ")"; }
        waitForAngular(1500);
        Object loaded = page.evaluate("(m) => { " + JS + " const e=[...document.querySelectorAll('input')].filter(vis).find(x=>x.id==='textinput' || /(^|\\.)MRNo?$/i.test(x.getAttribute('ng-model')||''));"
                + " if(!e) return false; setEl(e,m); const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo|FindPatient|Search/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); return true; }", mrn);
        waitForAngular(2500);
        Object txt = page.evaluate("() => { " + JS + " return norm((document.querySelector('.content-wrapper,.page-content,#page-wrapper,main')||document.body).innerText).slice(0,300); }");
        lastEstimate = (Boolean.TRUE.equals(loaded) ? "MRN loaded; " : "no MRN box on the screen; ") + "screen: " + txt;
        return null;
    }

    /**
     * Document D5 "Time Trail": on the Occupancy List select the patient and click the Time Trail icon; read the Patient Journey Time Trail
     * and report which of the document's time stamps it holds ({@code Pre-Admission, Registration / Bed Allocation, Planned Discharge, Discharge,
     * Cancelled Discharge, Housekeeping Start / End, Bed Release, Final Bill Generation}).
     *
     * @return null when the Time Trail opened (stamps in {@link #lastTimeTrail}), else the reason it did not
     */
    public String openTimeTrail(OccupancyList occ, String mrn) {
        if (!occ.navigateViaMenu()) return "Occupancy List did not open (" + page.url() + ")";
        occ.searchOneMonthToToday();
        if (!selectOccupancyRow(mrn)) return "MRN " + mrn + " is not on the Occupancy List grid";
        boolean clicked = clickControl("^\\s*time\\s*trail\\s*$", "time.?trail|journey");
        if (!clicked) { lastDump = describeVisibleControls(); return "No Time Trail icon / action found for the selected admission"; }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog],.content-wrapper')].some(m=>m.getBoundingClientRect().width>0 && /journey|time\\s*trail/i.test(m.textContent||'') && /registration|bed allocation|admission/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { lastDump = describeVisibleControls(); return "Patient Journey Time Trail did not open"; }
        waitForAngular(1500);
        Object txt = page.evaluate("() => { " + JS + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && /journey|time\\s*trail/i.test(x.textContent||'')).pop() || document.body; return norm(m.innerText); }");
        String t = txt == null ? "" : txt.toString();
        String low = t.toLowerCase();
        String[][] stamps = {{"Pre-Admission", "pre.?admission"}, {"Registration / Bed Allocation", "registration|bed allocation"}, {"Planned Discharge", "planned discharge"},
                {"Discharge", "discharge"}, {"Cancelled Discharge", "cancel+ed discharge"}, {"Housekeeping", "housekeeping"}, {"Bed Release", "bed release"}, {"Final Bill Generation", "final bill"}};
        StringBuilder sb = new StringBuilder();
        for (String[] s : stamps) sb.append(s[0]).append(low.matches("(?s).*(" + s[1] + ").*") ? ": shown" : ": absent").append("; ");
        lastTimeTrail = sb.toString().trim();
        return null;
    }

    public boolean timeTrailHasFinalBill() { return lastTimeTrail.contains("Final Bill Generation: shown"); }

    /** Close the Time Trail / deposit-request modal without changing anything. */
    public void closeModal() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0); if(m){"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel|back|×|x)\\s*$/i.test((x.textContent||'').trim()) || /close|cancel/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); } try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(600);
    }

    /**
     * Document D5 / E3 "Additional Deposit Request": on the Occupancy List open the request dialog for the patient and report its fields.
     * The dialog is only OPENED and closed again: submitting it sends a WhatsApp / SMS deposit request to the patient, which a test must not do.
     *
     * @return null when the dialog opened, else the reason it did not
     */
    public String openAdditionalDepositRequest(OccupancyList occ, String mrn, String[] fieldsOut) {
        if (!occ.navigateViaMenu()) return "Occupancy List did not open (" + page.url() + ")";
        occ.searchOneMonthToToday();
        if (!selectOccupancyRow(mrn)) return "MRN " + mrn + " is not on the Occupancy List grid";
        boolean clicked = clickControl("additional\\s*deposit", "additional.?deposit|depositrequest");
        if (!clicked) { lastDump = describeVisibleControls(); return "No Additional Deposit Request action found for the selected admission"; }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /deposit/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { lastDump = describeVisibleControls(); return "The Additional Deposit Request dialog did not open"; }
        waitForAngular(800);
        Object txt = page.evaluate("() => { " + JS + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && /deposit/i.test(x.textContent||'')).pop();"
                + " if(!m) return ''; const f=[...m.querySelectorAll('input,select,textarea,button')].filter(vis).map(e=>(e.getAttribute('ng-model')||e.id||norm(e.innerText)||e.tagName.toLowerCase()).slice(0,28));"
                + " return norm(m.innerText).slice(0,160)+' || controls: '+f.join(', '); }");
        fieldsOut[0] = txt == null ? "" : txt.toString();
        return null;
    }

    // ============================================================================================================
    //  shared helper
    // ============================================================================================================

    /** Compact listing of the visible controls + any open dialog text - attached to a FAIL so locators can be fixed in one pass. */
    public String describeVisibleControls() {
        Object r = page.evaluate("() => { " + JS
                + " const dlg=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,.sweet-alert')].filter(m=>m.getBoundingClientRect().width>0).map(m=>'DIALOG['+norm(m.textContent).slice(0,160)+']');"
                + " const ctl=[...document.querySelectorAll('input,select,textarea,button,[ng-click]')].filter(vis).filter(e=>!/setDatepickerDay|prevMonth|nextMonth/.test(e.getAttribute('ng-click')||''))"
                + "   .map(e=>e.tagName.toLowerCase()+'#'+e.id+'{m='+(e.getAttribute('ng-model')||'')+',c='+(e.getAttribute('ng-click')||'').slice(0,34)+',t='+norm(e.innerText||e.placeholder||e.title||'').slice(0,18)+'}');"
                + " return dlg.concat(ctl).slice(0,90).join(' ; '); }");
        return r == null ? "" : r.toString();
    }
}
