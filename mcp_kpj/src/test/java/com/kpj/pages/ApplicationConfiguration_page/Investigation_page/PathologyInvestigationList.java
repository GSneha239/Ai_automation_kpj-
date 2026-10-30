package com.kpj.pages.ApplicationConfiguration_page.Investigation_page;

import com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.Procedure;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.Map;

/**
 * Application Configuration &gt; Investigation &gt; <b>Pathology Investigation List</b> — Page Object.
 *
 * <p>Route {@code #/PathRadMaster}. The list screen has no Add of its own — only a <b>Service Item</b> button, which opens the Service Master
 * list ({@code #/serviceItemMaster}); its <b>Add</b> opens the "Service Master Details" form ({@code #/add-serviceItem/serviceMaster}), the
 * same form the OT Configuration &gt; Procedure screen uses. So everything the two screens share (Service Item / Add, service code and name,
 * pricing policy, Submit, the list check) is inherited from {@link Procedure}; this class adds the menu walk and the three sections of the form
 * that screen does not exercise:</p>
 * <ul>
 *   <li><b>Code Type</b> — pick the type, type the Code ({@code serviceMst.ClinicalCode}, max 10) and Service Name
 *       ({@code serviceMst.ServiceName}), click Add ({@code AddCode()}); the row is listed in the Code Type table.</li>
 *   <li><b>Tax Details</b> — tick a tax row ({@code IsTaxSelected}), pick Applicable On / Applicable For and a Percentage (the save refuses a
 *       ticked tax with no percentage). The first {@code tt.Status} checkbox on the page belongs to the Code Type row, not to a tax.</li>
 *   <li><b>Add Set Details</b> — the Item Name is a mass-autocomplete box: it is typed into and the item is PICKED from the suggestion list, then
 *       Add ({@code fnAddSetItem()}) lists it in "List of Set Information".</li>
 * </ul>
 *
 * <p>There is no HSN box on this form (see {@link Procedure#enterHsn}).</p>
 */
public class PathologyInvestigationList extends Procedure {

    public PathologyInvestigationList(Page page) { super(page); }

    public String lastCodeType = "", lastCodeRow = "", lastCodeAdd = "", lastTax = "", lastSetItem = "", lastSetAdd = "";

    private static final String H =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const setNg=(e,v)=>{ e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; Investigation &rarr; Pathology Investigation List. Each level is opened only if it is closed and the next
     * is looked up INSIDE its parent's {@code li} ("Investigation" is also a top-level module). The address can change before the view does, so the
     * screen's own title in the top bar is waited for (up to 30 s).
     */
    @Override
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*investigations?\\s*$");
        waitForAngular(800);
        lastMenu = describeMenu();
        Object href = page.evaluate("() => {" + H
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const inv=[...topLi.querySelectorAll('a')].find(x=>/^\\s*investigations?\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(inv&&inv.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*pathology\\s*investigation\\s*list\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__pilMenu'; return a.getAttribute('href')||''; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__pilMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PathologyInvestigationList.nav: " + e.getMessage().split("\n")[0]); }
            try { page.evaluate("() => { const e=document.getElementById('__pilMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 30 && !titleShown(); i++) page.waitForTimeout(1000);
        return titleShown() && onScreen();
    }

    /** The top bar names this screen — the address alone is not proof (the dashboard can stay on screen under a changed hash). */
    private boolean titleShown() {
        try {
            Object t = page.evaluate("() => { const h=document.querySelector('header,.navbar'); return h? (h.textContent||'').replace(/\\s+/g,' ').trim().slice(0,90) : ''; }");
            return t != null && t.toString().toLowerCase().contains("pathology investigation");
        } catch (Exception e) { return false; }
    }

    // ---- Group / Sub Group by name ---------------------------------------

    /** Select the Group whose name matches {@code labelRx}; the first real option when none does (said in the result). */
    public String selectGroupNamed(String labelRx) {
        lastGroup = selectByLabel("serviceMst.GroupId", labelRx, "__pilGroup", 12000);
        System.out.println("PathologyInvestigationList: Group = " + lastGroup);
        return lastGroup;
    }

    /** Select the Sub Group (its list cascades from the Group, so it is waited for) whose name matches {@code labelRx}. */
    public String selectSubGroupNamed(String labelRx) {
        lastSubGroup = selectByLabel("serviceMst.SubGroupId", labelRx, "__pilSub", 15000);
        System.out.println("PathologyInvestigationList: Sub Group = " + lastSubGroup);
        return lastSubGroup;
    }

    /** Select a dropdown by its exact ng-model and the option text, reading the choice back off the control. */
    @SuppressWarnings("unchecked")
    private String selectByLabel(String ngModel, String labelRx, String id, int waitMs) {
        long end = System.currentTimeMillis() + waitMs;
        Map<String, Object> info = null;
        while (System.currentTimeMillis() < end) {
            Object o = page.evaluate("(a) => {" + H
                    + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                    + " const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===a.ng);"
                    + " if(!s) return null;"
                    + " const reals=[...s.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                    + " if(!reals.length) return {index:-2};"
                    + " const rx=new RegExp(a.rx,'i'); const m=reals.find(x=>rx.test(norm(x.o.text)));"
                    + " s.id=a.id; return {index:(m||reals[0]).i, matched:!!m, count:reals.length}; }",
                    Map.of("ng", ngModel, "rx", labelRx, "id", id));
            if (o instanceof Map) {
                info = (Map<String, Object>) o;
                if (((Number) info.get("index")).intValue() >= 0) break;
            }
            page.waitForTimeout(400);
        }
        if (info == null) return "(no dropdown [" + ngModel + "] on the form)";
        int index = ((Number) info.get("index")).intValue();
        if (index < 0) return "(no-option) [" + ngModel + "] still has no real option after " + (waitMs / 1000) + "s";
        try {
            page.locator("#" + id).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(index),
                    new Locator.SelectOptionOptions().setTimeout(8000));
        } catch (Exception e) { return "(select failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(900);
        Object t = page.evaluate("(i) => { const e=document.getElementById(i); return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        boolean matched = Boolean.TRUE.equals(info.get("matched"));
        return (t == null ? "" : t.toString()) + " [" + ngModel + ", " + info.get("count") + " options"
                + (matched ? "" : "; nothing matched /" + labelRx + "/ so the first option was taken") + "]";
    }

    // ---- Code Type -------------------------------------------------------

    /** Select the <b>Code Type</b> whose name matches {@code labelRx}. */
    public String selectCodeType(String labelRx) {
        lastCodeType = selectByLabel("serviceMst.CodeType", labelRx, "__pilCt", 10000);
        System.out.println("PathologyInvestigationList: Code Type = " + lastCodeType);
        return lastCodeType;
    }

    /** Type the Code (max 10 characters — a longer value shows in the box but never reaches the model) and the Service Name of the code row. */
    public String enterCodeRow(String code, String serviceName) {
        Object r = page.evaluate("(a) => {" + H
                + " const g=ng=>[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(vis);"
                + " const c=g('serviceMst.ClinicalCode'), n=g('serviceMst.ServiceName');"
                + " if(!c||!n) return '(no-field) Code box '+(c?'found':'MISSING')+', Service Name box '+(n?'found':'MISSING');"
                + " setNg(c,a.code); setNg(n,a.name);"
                + " const sc=angular.element(c).scope(); try{ sc.$apply(); }catch(e){}"
                + " return 'Code='+c.value+' [model '+sc.serviceMst.ClinicalCode+'] | Service Name='+n.value+' [model '+sc.serviceMst.ServiceName+']'; }",
                Map.of("code", code, "name", serviceName));
        lastCodeRow = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("PathologyInvestigationList: code row -> " + lastCodeRow);
        return lastCodeRow;
    }

    /** Both boxes hold their own value AND the screen's model has it (the model stays empty for an over-long Code). */
    public boolean codeRowEntered(String code, String serviceName) {
        return lastCodeRow != null && !lastCodeRow.startsWith("(no-")
                && lastCodeRow.contains("Code=" + code + " [model " + code + "]")
                && lastCodeRow.contains("Service Name=" + serviceName + " [model " + serviceName + "]");
    }

    /** Click <b>Add</b> of the Code Type section ({@code AddCode()}) and read the Code Type table: the row must be listed. */
    public String clickAddCode(String code) {
        Object r = page.evaluate("(a) => new Promise(res => {" + H
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/Code\\s*Type/i.test(t.textContent)&&/Version/i.test(t.textContent)&&/Service\\s*Name/i.test(t.textContent));"
                + " const rows=()=>tbl?[...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(x=>x):[];"
                + " const before=rows().length;"
                + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/AddCode/.test(x.getAttribute('ng-click')||''));"
                + " if(!b){ res('(no-add) the Add button of the Code Type section was not found'); return; }"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " setTimeout(()=>{ const after=rows(); const mine=after.find(t=>t.includes(a.code));"
                + "   res((mine?'ADDED':'NOT added')+': Code Type table rows '+before+' -> '+after.length+(mine?' | row: '+mine:'')"
                + "     +' | toast: '+[...document.querySelectorAll('.toast-message')].map(t=>norm(t.textContent)).join(';')); },1500); })",
                Map.of("code", code));
        lastCodeAdd = r == null ? "" : r.toString();
        System.out.println("PathologyInvestigationList: add code -> " + lastCodeAdd);
        return lastCodeAdd;
    }

    public boolean codeAdded() { return lastCodeAdd != null && lastCodeAdd.startsWith("ADDED"); }

    // ---- Tax Details -----------------------------------------------------

    /**
     * Tick the first row of <b>Tax Details</b>, pick its Applicable On and Applicable For (first real options) and type the Percentage. The table
     * is found by its "Tax Name" heading — the first {@code tt.Status} box on the page is the Code Type row's own status toggle.
     */
    public String selectTaxDetails(String percentage) {
        Object r = page.evaluate("(pct) => new Promise(res => {" + H
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/Tax\\s*Name/i.test(t.textContent)&&/Applicable\\s*On/i.test(t.textContent));"
                + " if(!tbl){ res('(no-tax-table) no Tax Details table on the form'); return; }"
                + " const tr=tbl.querySelector('tbody tr'); if(!tr){ res('(no-tax-row)'); return; }"
                + " tr.scrollIntoView({block:'center'});"
                + " const chk=tr.querySelector('input[type=checkbox]'); if(!chk.checked) chk.click();"
                + " const on=tr.querySelector(\"select[ng-model='tt.ApplicableOnID']\"), fo=tr.querySelector(\"select[ng-model='tt.ApplicableForID']\"), pc=tr.querySelector(\"input[ng-model='tt.Percentage']\");"
                + " const first=s=>{ const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.text))); s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); return i; };"
                + " first(on); first(fo); setNg(pc,pct);"
                + " setTimeout(()=>{ let tt=null; try{ tt=angular.element(tr).scope().tt; }catch(e){}"
                + "   res((tt&&tt.Status===true?'ticked':'NOT ticked')+' '+norm(tr.children[1]?tr.children[1].textContent:'')+' | Applicable On='+norm(on.options[on.selectedIndex].text)"
                + "     +' [id '+(tt?tt.ApplicableOnID:'?')+'] | Percentage='+(tt?tt.Percentage:'?')+' | Applicable For='+norm(fo.options[fo.selectedIndex].text)+' [id '+(tt?tt.ApplicableForID:'?')+']'); },800); })",
                percentage);
        lastTax = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("PathologyInvestigationList: tax -> " + lastTax);
        return lastTax;
    }

    public boolean taxSelected(String percentage) {
        return lastTax != null && lastTax.startsWith("ticked") && lastTax.contains("Percentage=" + percentage);
    }

    // ---- Add Set Details -------------------------------------------------

    /**
     * Type into the <b>Item Name</b> box of Add Set Details and PICK the first entry of the suggestion list (mass-autocomplete): typing alone
     * never sets the item. Tries each search term until the list offers something.
     */
    public String pickSetItem(String... terms) {
        Locator box = page.locator("input[ng-model='SetItemEntry.ItemName']");
        try { box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(6000)); } catch (Exception ignore) { }
        for (String term : terms) {
            try {
                box.click(new Locator.ClickOptions().setTimeout(5000));
                box.fill("");
                box.pressSequentially(term, new Locator.PressSequentiallyOptions().setDelay(120));
                Locator items = page.locator("ul.ac-menu li.ac-menu-item");
                items.first().waitFor(new Locator.WaitForOptions().setTimeout(7000));
                int n = items.count();
                String text = items.first().textContent().replaceAll("\\s+", " ").trim();
                items.first().click(new Locator.ClickOptions().setTimeout(5000));
                waitForAngular(900);
                String shown = box.inputValue();
                lastSetItem = text.equals(shown.trim())
                        ? text
                        : "(not-set) clicked \"" + text + "\" but the box reads \"" + shown + "\"";
                lastSetItem += lastSetItem.startsWith("(") ? "" : "  [typed \"" + term + "\", " + n + " suggestion(s), picked the first]";
                System.out.println("PathologyInvestigationList: set item -> " + lastSetItem);
                return lastSetItem;
            } catch (Exception e) {
                System.out.println("PathologyInvestigationList: no suggestion for \"" + term + "\" - " + e.getMessage().split("\n")[0]);
            }
        }
        lastSetItem = "(no-suggestion) none of " + String.join(", ", terms) + " produced a suggestion list";
        return lastSetItem;
    }

    public boolean setItemPicked() { return lastSetItem != null && !lastSetItem.startsWith("("); }

    /** The picked item's own name, without the trailing note. */
    private String pickedName() { int i = lastSetItem.indexOf("  ["); return (i < 0 ? lastSetItem : lastSetItem.substring(0, i)).trim(); }

    /** Click <b>Add</b> of Add Set Details ({@code fnAddSetItem()}) and read "List of Set Information": the picked item must be listed. */
    public String clickAddSetItem() {
        Object r = page.evaluate("(a) => new Promise(res => {" + H
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/Item\\s*Code/i.test(t.textContent)&&/Batch/i.test(t.textContent));"
                + " const rows=()=>tbl?[...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(x=>x):[];"
                + " const before=rows().length;"
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnAddSetItem/.test(x.getAttribute('ng-click')||''));"
                + " if(!b){ res('(no-add) the Add button of Add Set Details was not found'); return; }"
                + " b.scrollIntoView({block:'center'}); b.click();"
                + " setTimeout(()=>{ const after=rows(); const mine=after.find(t=>t.includes(a.item));"
                + "   res((mine?'ADDED':'NOT added')+': List of Set Information rows '+before+' -> '+after.length+(mine?' | row: '+mine:'')"
                + "     +' | toast: '+[...document.querySelectorAll('.toast-message')].map(t=>norm(t.textContent)).join(';')); },1500); })",
                Map.of("item", pickedName()));
        lastSetAdd = r == null ? "" : r.toString();
        System.out.println("PathologyInvestigationList: add set item -> " + lastSetAdd);
        return lastSetAdd;
    }

    public boolean setItemAdded() { return lastSetAdd != null && lastSetAdd.startsWith("ADDED"); }
}
