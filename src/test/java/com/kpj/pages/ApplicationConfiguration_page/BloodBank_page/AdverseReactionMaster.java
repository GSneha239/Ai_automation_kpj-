package com.kpj.pages.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Adverse Reaction Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Blood Bank</b> (submenu) → <b>Adverse Reaction Master</b>
 * (route {@code #/AdverseReactionMaster}) → <b>Add</b> ({@code #/add-AdverseReactionMaster}) → enter
 * <b>Code</b>, <b>Remark</b>, <b>Comment</b> → <b>Submit</b> ({@code fnIUDAdverseReaction}) → toast.</p>
 *
 * <p>Fields (all text): {@code AdverseReaction.Code}, {@code AdverseReaction.Description} (Remark),
 * {@code AdverseReaction.Comment}.</p>
 */
public class AdverseReactionMaster extends BasePage {

    public AdverseReactionMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/AdverseReactionMaster";
    public static String ADD_ROUTE = "#/add-AdverseReactionMaster";
    public String lastCode = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // Blood Bank submenu parent → expand
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/adverse\\s*reaction/i.test(norm(x.textContent)) || /adversereaction/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__arMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__arMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AdverseReactionMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("AdverseReactionMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("adversereaction");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-AdverseReactionMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__arAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("AdverseReactionMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__arAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("AdverseReactionMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='AdverseReaction.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("AdverseReactionMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Realistic adverse reaction names/comments — not generic filler text; only the Code stays a generated
     * unique value. Comment is a realistic clinical remark paired by the same index.
     */
    private static final String[] REACTION_NAMES = {
            "Febrile Non-Hemolytic Reaction", "Allergic Urticaria", "Anaphylaxis", "Hemolytic Reaction",
            "Transfusion-Related Acute Lung Injury", "Hypotensive Reaction", "Delayed Hemolytic Reaction",
            "Transfusion-Associated Circulatory Overload", "Septic Reaction", "Anxiety Reaction",
            "Dyspnea", "Rigors and Chills"
    };
    private static final String[] REACTION_COMMENTS = {
            "Stop transfusion immediately and notify physician", "Administer antihistamine and monitor",
            "Observe vital signs every 15 minutes", "Document and report to blood bank",
            "Provide supportive care and oxygen", "Isolate blood unit for investigation",
            "Repeat crossmatch before resuming", "Refer to hematology for review",
            "Monitor for delayed symptoms over 24 hours", "Reassure patient and monitor",
            "Elevate head of bed and monitor breathing", "Provide warm blankets and monitor"
    };

    /**
     * Enter Code, Remark, Comment. {@code attempt} shifts the code, remark and comment so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "AR" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        String remark = REACTION_NAMES[attempt % REACTION_NAMES.length] + (attempt >= REACTION_NAMES.length ? " " + (attempt / REACTION_NAMES.length + 1) : "");
        String comment = REACTION_COMMENTS[attempt % REACTION_COMMENTS.length] + (attempt >= REACTION_COMMENTS.length ? " " + (attempt / REACTION_COMMENTS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('AdverseReaction.Code',a.code); const rm=set('AdverseReaction.Description',a.remark); const cm=set('AdverseReaction.Comment',a.comment);"
                + " return 'Code='+cd+' | Remark='+rm+' | Comment='+cm; }",
                java.util.Map.of("code", code, "remark", remark, "comment", comment));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code fnIUDAdverseReaction}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__arToasts=[]; if(window.__arObs) window.__arObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__arToasts.includes(t)) window.__arToasts.push(t); }); };"
                + " window.__arObs=new MutationObserver(grab); window.__arObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDAdverseReaction/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__arSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__arSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__arToasts||[]).some(a=>/adverse|reaction|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__arToasts||[]).includes(t)) (window.__arToasts=window.__arToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__arToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
