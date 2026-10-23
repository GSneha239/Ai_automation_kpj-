package com.kpj.tests.Flow_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

/**
 * TEMPORARY probe (delete after use): goes straight to the Radiology Work Order List and to OPD Bill for patients created by
 * earlier flow runs, to read the real tick controls and the Payment Window options without a full 10-minute flow.
 *   -Dprobe.rad=<MRN>   -Dprobe.bill=<MRN>
 */
public class BillingOPProbe extends DevHisBase {
    public BillingOPProbe() { super("Flow_BillingOPProbe"); }

    public static void main(String[] args) {
        BillingOPProbe t = new BillingOPProbe();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected void body() {
        meta("Billing OP probe", "Probe", "temporary");
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER, "ok", "ok", "PASS");
        com.kpj.pages.Flow_page.BillingOP bp = new com.kpj.pages.Flow_page.BillingOP(page);

        String rad = System.getProperty("probe.rad", "");
        if (rad.equalsIgnoreCase("ANY")) {
            // pick the first PENDING order on the Radiology list (no MRN filter), then run the real accept on that MRN
            bp.openScreen("Investigation", "#/DoctorWorkOrderR", "DoctorWorkOrderR");
            page.waitForTimeout(2000);
            page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/fnSearchorderbookingdetails\\(1\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            page.waitForTimeout(4000);
            Object first = page.evaluate("() => { let g=null; document.querySelectorAll('*').forEach(el=>{ if(g) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length) g=s; }catch(e){} });"
                    + " if(!g) return ''; const m=JSON.stringify(g.grid.options.data[0]).match(/11\\d{8}/); return m? m[0] : ''; }");
            rad = first == null ? "" : first.toString();
            System.out.println("probe: first pending radiology MRN = " + rad);
        }
        if (!rad.isBlank()) {
            int n = bp.acceptWorkOrders("#/DoctorWorkOrderR", rad, "radiology");
            step(page, "Probe Radiology accept " + rad, "tick + accept", "accepted", "rows=" + n + " toast=" + bp.lastToast
                    + (bp.lastDump.isEmpty() ? "" : " | DUMP: " + bp.lastDump), n > 0 ? "PASS" : "FAIL");
        }

        String emr = System.getProperty("probe.emr", "");
        if (!emr.isBlank()) {
            com.kpj.pages.Op_page.OutPatientQueueManagementPage q = new com.kpj.pages.Op_page.OutPatientQueueManagementPage(page);
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String why = bp.openEmrFromQueue(q, BASE, emr, today);
            step(page, "Probe EMR open " + emr, "queue > Action > EMR", "EMR opens", why == null ? "opened; " + bp.lastFallRiskNote : why, why == null ? "PASS" : "FAIL");
        }

        String bill = System.getProperty("probe.bill", "");
        if (!bill.isBlank()) {
            com.kpj.pages.Billing_page.OpdBill ob = new com.kpj.pages.Billing_page.OpdBill(page);
            boolean nav = ob.navigateTo(BASE);
            boolean found = false;
            for (int i = 0; i < 4 && nav && !found; i++) { found = ob.searchByMrn(bill); if (!found) page.waitForTimeout(2500); }
            for (int i = 0; i < 15 && found && ob.readChargeGridServiceNames().isEmpty(); i++) page.waitForTimeout(1000);
            boolean fin = found && bp.selectFinalBill();
            boolean saved = fin && bp.saveFinalBill();
            step(page, "Probe OPD Bill final " + bill, "final save", "saved",
                    "nav=" + nav + " found=" + found + " final=" + fin + " saved=" + saved + " | payNote=" + bp.lastPaymentNote
                            + " | modes=[" + bp.payModes + "] | toast=" + bp.lastToast
                            + (bp.lastDump.isEmpty() ? "" : " | DUMP: " + bp.lastDump), saved ? "PASS" : "FAIL");
        }
    }
}
