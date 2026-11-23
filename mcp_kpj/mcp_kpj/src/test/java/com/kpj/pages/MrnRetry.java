package com.kpj.pages;

import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared MRN retry for the DevHIS screens that attach a patient by MRN.
 *
 * <p>Several screens refuse an MRN that resolves perfectly well elsewhere — 100000956 works on Vitals
 * Details and Intake Output Chart but is rejected by Birth Certificate ("Patient Not Found!"), Adverse Drug
 * Reaction ("Please Select Patient!") and others. A single hard-coded MRN therefore makes a working screen
 * look broken, and that mistake has been made more than once in this suite: two flows were documented as
 * defective when the only problem was the patient.</p>
 *
 * <p>The strategy here is the one that fixed those: try the supplied candidates in turn, and if none
 * attach, ask the screen's OWN patient lookup ({@code openPopupScreen()} &rarr; its Search) for MRNs this
 * environment actually has, then retry with those. Whether a patient attached is decided by the caller's
 * predicate — normally a check that the Angular model carries real patient detail, never that it echoes
 * back the MRN that was typed.</p>
 */
public final class MrnRetry {

    private MrnRetry() { }

    /** MRNs confirmed to attach somewhere in this environment; a reasonable starting pool. */
    public static final String[] KNOWN_GOOD = {
            "100000684", "100000687", "100001148", "100001061", "100001060", "100000888"
    };

    /**
     * Build the candidate list: {@code -Dmrn=} first (comma-separated is allowed), then {@code -Dmrns=}
     * or the supplied fallbacks, de-duplicated and order-preserving.
     */
    public static List<String> candidates(String defaultMrn, String... fallbacks) {
        List<String> out = new ArrayList<>();
        for (String s : System.getProperty("mrn", defaultMrn).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        String[] rest = (fallbacks == null || fallbacks.length == 0) ? KNOWN_GOOD : fallbacks;
        for (String s : System.getProperty("mrns", String.join(",", rest)).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** What a retry produced: which MRN worked, the last search output, and everything tried. */
    public static final class Result {
        public final String mrn;          // "" when nothing attached
        public final String lastSearch;
        public final String attempts;

        Result(String mrn, String lastSearch, String attempts) {
            this.mrn = mrn; this.lastSearch = lastSearch; this.attempts = attempts;
        }

        public boolean attached() { return !mrn.isEmpty(); }
    }

    /**
     * Try each candidate until {@code attached} reports success.
     *
     * @param label  the screen name, for logging
     * @param page   used to clear stale messages between attempts, so one screen's refusal is not read as
     *               the next attempt's result
     * @param search runs the screen's MRN search and returns its description
     */
    public static Result trySearch(String label, Page page, List<String> candidates,
                                   Function<String, String> search, Supplier<Boolean> attached) {
        StringBuilder tried = new StringBuilder();
        String last = "";
        for (String mrn : candidates) {
            if (mrn == null || mrn.isBlank()) continue;
            clearToasts(page);
            last = search.apply(mrn.trim());
            boolean ok = Boolean.TRUE.equals(attached.get());
            tried.append(tried.length() == 0 ? "" : "; ").append(mrn.trim())
                 .append(ok ? " -> ATTACHED" : " -> not attached");
            if (ok) {
                System.out.println(label + ": MRN " + mrn.trim() + " attached (tried: " + tried + ")");
                return new Result(mrn.trim(), last, tried.toString());
            }
        }
        System.out.println(label + ": no MRN attached (tried: " + tried + ")");
        return new Result("", last, tried.toString());
    }

    private static void clearToasts(Page page) {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        try { page.waitForTimeout(300); } catch (Exception ignore) { }
    }

    /**
     * Ask the screen's patient lookup for MRNs it actually has.
     *
     * <p>The popup lists nothing until its own Search is run, so that is clicked before the rows are read.
     * Only {@code 100xxxxxx} is accepted: those rows also carry NRIC and phone numbers, and a looser
     * pattern hands back values that are not MRNs at all.</p>
     */
    public static List<String> discover(String label, Page page, int max) {
        List<String> found = new ArrayList<>();
        try {
            page.evaluate("() => { const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis)"
                    + "   .find(x=>/openPopupScreen|OpenPopupScreen/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            page.waitForTimeout(2500);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const b=[...root.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                    + "   .find(x=>/SearchPatient\\(/i.test(x.getAttribute('ng-click')||'')"
                    + "        || /^\\s*search\\s*$/i.test(norm(x.textContent)||x.value||''));"
                    + " if(b) b.click(); }");
            page.waitForTimeout(3000);
            Object r = page.evaluate("(max) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " const root=dlg||document;"
                    + " const text=[...root.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).join(' ');"
                    + " return [...new Set((text.match(/\\b100\\d{6}\\b/g)||[]))].slice(0,max); }", max);
            if (r instanceof List) for (Object o : (List<?>) r) found.add(o.toString());
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const dlg=[...document.querySelectorAll('.modal,.modal-content,[role=dialog]')].filter(vis)[0];"
                    + " if(!dlg) return;"
                    + " const b=[...dlg.querySelectorAll('button,a')].filter(vis)"
                    + "   .find(x=>/^\\s*(close|cancel|x)\\s*$/i.test(norm(x.textContent))"
                    + "        || /resetForm|closePopup|cancel/i.test(x.getAttribute('ng-click')||''));"
                    + " if(b) b.click(); }");
            page.waitForTimeout(1500);
        } catch (Exception e) {
            System.out.println(label + ".discover: " + e.getMessage().split("\n")[0]);
        }
        System.out.println(label + ".discover -> " + found);
        return found;
    }

    /**
     * The whole strategy: candidates, then the screen's own lookup if none of them attach.
     */
    public static Result resolve(String label, Page page, List<String> candidates,
                                 Function<String, String> search, Supplier<Boolean> attached, int discoverMax) {
        Result r = trySearch(label, page, candidates, search, attached);
        if (r.attached()) return r;

        List<String> offered = discover(label, page, discoverMax);
        if (offered.isEmpty()) return r;

        Result r2 = trySearch(label, page, offered, search, attached);
        String combined = r.attempts + " | from the patient lookup: " + r2.attempts;
        return new Result(r2.mrn, r2.lastSearch, combined);
    }
}
