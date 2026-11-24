package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.ApplicationConfiguration_page.General_page.QualityIndicator;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Allergen Type</b> — configuration screen Page Object.
 *
 * <p>Route {@code #/AllergenType}. It is the SAME generic common-master screen as {@link QualityIndicator} (Form Name select,
 * Code, Remark, Submit, Code | Remark | Status | Edit grid), reached through the <b>Nursing</b> sub-menu — only the menu group, the
 * entry that is clicked and the stamps written into the form differ, so everything else is inherited.</p>
 *
 * <p>The sub-menu is matched on EXACT text ("Nursing"): "Nursing Station" is a separate top-level module that a prefix match hits first.</p>
 */
public class AllergenType extends QualityIndicator {

    public AllergenType(Page page) {
        super(page, "nursing", "allergen\\s*type", "#/allergentype", "AT", "Auto allergen type ", "AllergenType");
    }
}
