package com.kpj.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks an entry from a select2 dropdown the way a user does — open the box, click the entry in its list — for a select identified by its {@code ng-model}.
 *
 * <p>DevHIS styles most {@code <select>}s with select2: the native select stays in the page (hidden) and the visible box is the {@code .select2-container} that
 * PRECEDES it. The list is a shared {@code .select2-drop-active} popup holding {@code li.select2-result-selectable} entries. Used by screens whose stores are chosen
 * from such a list ({@code AutoIndent.FromStoreID} / {@code ToStoreID}).</p>
 */
public final class Select2Pick {

    private Select2Pick() { }

    private static final String NORM = "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();";

    /** Every entry the select offers, in list order ("-Select-" left out). */
    public static List<String> names(Page page, String ngModel) {
        Object r = page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)); }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** What the select's visible box shows now ("" when nothing is chosen). */
    public static String chosen(Page page, String ngModel) {
        Object r = page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " const c=e.previousElementSibling; const ch=c && c.querySelector('.select2-chosen'); return ch? norm(ch.textContent) : ''; }", ngModel);
        String s = r == null ? "" : r.toString();
        return s.matches("(?i)-*\\s*select\\s*-*") ? "" : s;
    }

    /** The value the screen's model holds for the select ("" when empty). */
    public static String model(Page page, String ngModel) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ngModel);
        return v == null ? "" : v.toString().trim();
    }

    /**
     * Open the select's box and click {@code name} in its list.
     *
     * @return "" when the box then shows {@code name} AND the screen's model holds it, otherwise a sentence saying what went wrong
     */
    public static String pick(Page page, String ngModel, String name) {
        Object tagged = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return false;"
                + " const c=e.previousElementSibling; const b=c && (c.querySelector('.select2-choice')||c); if(!b) return false; b.id='__s2Box'; return true; }", ngModel);
        if (!Boolean.TRUE.equals(tagged)) return "the box for " + ngModel + " is not on screen";
        try {
            page.locator("#__s2Box").click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForFunction("() => [...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].length>1", null,
                    new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { return "the list of " + ngModel + " did not open"; }
        finally { page.evaluate("() => { const e=document.getElementById('__s2Box'); if(e) e.removeAttribute('id'); }"); }
        Object found = page.evaluate("(w) => {" + NORM + " const li=[...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].find(x=>norm(x.textContent)===w);"
                + " if(!li) return false; li.id='__s2Opt'; return true; }", name);
        if (!Boolean.TRUE.equals(found)) {
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            return "\"" + name + "\" is not in the dropdown list";
        }
        try { page.locator("#__s2Opt").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return "\"" + name + "\" could not be clicked in the list"; }
        finally { page.evaluate("() => { const e=document.getElementById('__s2Opt'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 20 && !name.equals(chosen(page, ngModel)); i++) page.waitForTimeout(150);
        String shown = chosen(page, ngModel);
        if (!name.equals(shown)) return "the box shows \"" + shown + "\" instead of \"" + name + "\"";
        String held = model(page, ngModel);
        if (held.isEmpty() || held.equals("null")) return "\"" + name + "\" is shown but the screen's model does not hold it";
        return "";
    }
}
