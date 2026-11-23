package com.kpj.tests.Telemedicine_page;

import com.kpj.pages.Op_page.Appointment.AppointmentListPage;
import com.kpj.pages.Telemedicine_page.TelemedicineAppointmentListPage;
import com.kpj.tests.Op_Page.Appointment.AppointmentListTest;

/**
 * Telemedicine &gt; <b>Appointment List</b> — the SAME screen/flow as OP &gt; Appointment &gt; Appointment List
 * ({@code #/AppointmentList}), reached via the Telemedicine menu. Reuses ALL 7 footer-tab sections from
 * {@link AppointmentListTest} (Request MRD File, Return MRD File, View App History, Change Executor, Registration,
 * Reschedule Appointment, Cancel Appointment) — only the entry point (page factory) and report meta differ.
 */
public class TelemedicineAppointmentList extends AppointmentListTest {

    public TelemedicineAppointmentList() { super(); }

    /** Report under the Telemedicine name while reusing the OP flow wholesale (see DevHisBase.reportId()). */
    @Override
    protected String reportId() { return "Telemedicine_AppointmentList"; }

    public static void main(String[] args) {
        TelemedicineAppointmentList t = new TelemedicineAppointmentList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Enter the SAME #/AppointmentList screen via the Telemedicine menu. */
    @Override
    protected AppointmentListPage newListPage() { return new TelemedicineAppointmentListPage(page); }

    @Override
    protected String[] metaInfo() {
        return new String[]{
                "Telemedicine - Appointment List",
                "Telemedicine > Appointment List",
                "Telemedicine Appointment List (same #/AppointmentList screen as OP, entered via the Telemedicine menu): exercises all footer tabs — Request MRD File, Return MRD File, View App History, Change Executor, Registration, Reschedule Appointment, and Cancel Appointment."
        };
    }
}
