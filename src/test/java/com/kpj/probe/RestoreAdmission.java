package com.kpj.probe;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

/** TEMPORARY (delete before building the jar): re-opens (Revokes) an admission that an automated run closed and could not revoke. */
public class RestoreAdmission extends DevHisBase {
    public RestoreAdmission() { super("Probe_RestoreAdmission"); }

    public static void main(String[] args) {
        RestoreAdmission t = new RestoreAdmission();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected void body() {
        String mrn = System.getProperty("restore.mrn");
        new LoginPage(page).login(BASE, USER, PASS);
        com.kpj.pages.Emergency_page.EmergencyListView lv = new com.kpj.pages.Emergency_page.EmergencyListView(page);
        System.out.println("RESTORE nav=" + lv.navigateViaMenu());
        int rows = lv.useClosedAdmissionFilter(mrn);
        System.out.println("RESTORE closed rows=" + rows);
        String d = lv.reselectPatientByMrn(mrn);
        System.out.println("RESTORE selected=" + d);
        if (d == null) return;
        boolean open = lv.clickRevokeAdmission();
        System.out.println("RESTORE revoke popup opened=" + open);
        if (!open) return;
        lv.enterRevokeReason("Revoked - re-opening an admission closed by an automated test run");
        String toast = lv.saveRevokeAdmissionAndGetToast();
        System.out.println("RESTORE revoke toast=" + toast);
        lv.closeRevokeAdmissionPopup();
        step(page, "Revoke Admission (restore)", "Revoke the admission closed by an automated run", "Admission Revoked Successfully", String.valueOf(toast),
                toast != null && toast.toLowerCase().matches(".*(revoked|success).*") ? "PASS" : "FAIL");
    }
}
