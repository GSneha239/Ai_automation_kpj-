package com.kpj.tests.Billing_page;

import com.kpj.tests.ReadOnlyScenarioTestBase;

/**
 * Discount Manager/CR suites ({@link DiscountManagerList}, {@link DiscountManagerAdd}): the shared read-only plumbing now lives
 * in {@link ReadOnlyScenarioTestBase}; this class only fixes the module name and the read-only summary line.
 */
public abstract class DiscountManagerTestBase<P extends com.kpj.pages.Billing_page.DiscountManagerList> extends ReadOnlyScenarioTestBase<P> {

    protected DiscountManagerTestBase(String testId) { super(testId); }

    @Override protected String reportModule() { return "Billing > Discount Manager/CR"; }

    @Override protected String readOnlyNote() { return "READ-ONLY - write calls aborted in the browser; Save / Approve / Print never clicked"; }
}
