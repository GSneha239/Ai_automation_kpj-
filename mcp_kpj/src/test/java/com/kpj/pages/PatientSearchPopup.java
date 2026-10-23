package com.kpj.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The shared <b>Patient Search</b> popup that sits beside an MRN box on many screens (OT Schedule, Death Register, Lab
 * Work Order, …). It lists registered patients, ten to a page ({@code SearchPatient(0)}, paged with {@code NextBtn()},
 * closed with {@code CloseModel()}), and is used here to FIND patients to try instead of hardcoding an MRN.
 *
 * <p>Confirmed live (devhis build v2026.10.21.266): opened by the {@code openPopupScreen()} button next to the MRN box;
 * a default search (no filters ticked) lists the most recently active patients first; the MRN is the first word of each
 * result row.</p>
 */
public class PatientSearchPopup extends BasePage {

    public PatientSearchPopup(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private List<String> visibleMrns() {
        Object r = page.evaluate("() => {" + JS
                + " const ths=[...document.querySelectorAll('.modal table thead th')];"
                + " const idx=ths.findIndex(h=>/^\\s*mrn\\b/i.test(norm(h.textContent)));"
                + " const ok=t=>/^[A-Za-z0-9]{4,}$/.test(t);"
                + " return [...document.querySelectorAll('.modal table tbody tr')].filter(vis).map(r=>{"
                + "   const td=r.querySelectorAll('td'); let t=(idx>=0&&td[idx])? norm(td[idx].textContent) : '';"
                + "   if(!ok(t)) t=norm(r.textContent).split(' ')[0]; return t; }).filter(ok); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    private boolean clickButton(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__psPop'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#__psPop").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("PatientSearchPopup: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__psPop'); if(e) e.removeAttribute('id'); }"); }
    }

    /**
     * Open the popup, search with its default filters and collect up to {@code target} distinct MRNs, paging through
     * at most {@code maxPages} pages, then close it again. Returns an empty list when the popup could not be used.
     */
    public List<String> collectMrns(int target, int maxPages) {
        return collectMrns("openPopupScreen", target, maxPages);
    }

    /**
     * As {@link #collectMrns(int, int)}, for a screen whose popup button has another handler (e.g. Pharmacy Sale/Dispense uses
     * {@code OpenPatientSearchPopupScreen()}); {@code openHandlerRe} is matched against the button's {@code ng-click}.
     */
    public List<String> collectMrns(String openHandlerRe, int target, int maxPages) {
        return collectMrns(openHandlerRe, target, maxPages, false);
    }

    /**
     * As above; with {@code anyVisitDate} the popup's <b>Visit Date</b> filter (ticked for today by default on some screens, which limits the list to
     * patients who visited TODAY) is unticked first, so patients last seen on earlier days are listed too.
     */
    public List<String> collectMrns(String openHandlerRe, int target, int maxPages, boolean anyVisitDate) {
        List<String> out = new ArrayList<>();
        if (!clickButton(openHandlerRe, null)) { System.out.println("PatientSearchPopup: the popup button was not found"); return out; }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/SearchPatient\\(0\\)/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("PatientSearchPopup: the popup did not open"); return out; }
        if (anyVisitDate) {
            Object tagged = page.evaluate("() => { const c=document.querySelector(\".modal.in input[ng-model='PatientData.ChkVisitDate']\");"
                    + " if(!c || !c.checked) return ''; c.id='__psVisitChk'; return 'ok'; }");
            if (tagged != null && !tagged.toString().isEmpty()) {
                try { page.locator("#__psVisitChk").click(new Locator.ClickOptions().setTimeout(4000)); }
                catch (Exception e) { System.out.println("PatientSearchPopup: could not untick Visit Date - " + e.getMessage().split("\n")[0]); }
                finally { page.evaluate("() => { const e=document.getElementById('__psVisitChk'); if(e) e.removeAttribute('id'); }"); }
            }
        }
        try {
            page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> clickButton("SearchPatient\\(0\\)", "^\\s*search\\s*$"));
        } catch (Exception e) { System.out.println("PatientSearchPopup: the search gave no answer - " + e.getMessage()); }
        waitForAngular(1200);
        for (int p = 0; p < maxPages; p++) {
            int before = out.size();
            List<String> pageMrns = visibleMrns();
            for (String m : pageMrns) if (!out.contains(m)) out.add(m);
            // a page holds ten patients: a shorter one is the last, and ">>" on it only raises a warning toast
            if (out.size() >= target || (p > 0 && out.size() == before) || p == maxPages - 1 || pageMrns.size() < 10) break;
            try {
                page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(15000),
                        () -> clickButton("NextBtn", null));
            } catch (Exception e) { break; }
            waitForAngular(900);
        }
        clearToasts();   // a toast across the top of the page would sit on top of the popup's buttons
        clickButton("CloseModel", "^\\s*close\\s*$");
        try {
            // (the popup is position:fixed, so offsetParent would say "hidden" even while it is open — use its size)
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m=>(m.offsetWidth||m.offsetHeight||m.getClientRects().length) && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) { }
        waitForAngular(500);
        return out;
    }
}
