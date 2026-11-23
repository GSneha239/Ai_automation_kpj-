package com.kpj.tests.OT_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The page object is also named OTGraphicalView - referenced by its fully-qualified name.

/**
 * OT &gt; <b>OT Graphical View</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: OT &gt; OT Graphical View.</li>
 *   <li>Choose each <b>Operation Theatre</b> in turn; its OT tables appear as tiles (green = vacant, red = scheduled) and the legend counts Vacant / Scheduled / Total Tables.</li>
 *   <li>Click a table ({@code GetScheduleDetails}) - the <b>Current Schedule</b> lists its time slots (free slots are green).</li>
 *   <li><b>Vacant</b> slot: tick it -&gt; the warning "Patient Not Found!" appears and no patient details are shown.</li>
 *   <li><b>Booked</b> slot (red): tick it -&gt; the patient details (MRN, name, age, gender, doctor, surgery) are shown and can be edited in the boxes.</li>
 * </ol>
 *
 * <p>Nothing is saved on this screen (it has no Save; edited boxes are client side only). The booked-slot part needs an OT booking for today - none exists unless one was made on OT &gt; OT Schedule; the step reports
 * MANUAL when there is none. {@code -Dotg.book=true} first tries to book one through the OT Schedule page ({@code OTSchedule.scheduleForToday}, SAVES a real OT schedule). {@code -Dotg.scan=all} walks every theatre
 * instead of the recorded ones.</p>
 */
public class OTGraphicalView extends DevHisBase {

    private static final String[] RECORDED_THEATRES = {"General OT", "test", "test11", "Auto OT theatre 10400", "Auto OT theatre 97500", "Auto OT theatre 99600", "Auto OT theatre 29200", "Auto OT theatre 70200"};
    private static final boolean SCAN_ALL = "all".equalsIgnoreCase(System.getProperty("otg.scan", ""));
    private static final boolean BOOK = Boolean.getBoolean("otg.book");
    private static final String VACANT_THEATRE = System.getProperty("otg.theatre", "Auto OT theatre 10400").trim();
    private static final String VACANT_TABLE = System.getProperty("otg.table", "Auto OT table 66600").trim();

    public OTGraphicalView() { super("OT_OTGraphicalView"); }

    public static void main(String[] args) {
        OTGraphicalView t = new OTGraphicalView();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static int[] rgb(String s) {
        Matcher m = Pattern.compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").matcher(s == null ? "" : s);
        int[] last = null;
        while (m.find()) last = new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
        return last;
    }

    /** The slot's colour cell (second part of the "row | cell" background text) - green = free. */
    private static boolean isGreen(String bg) { int[] c = rgb(bg.contains("|") ? bg.substring(bg.indexOf('|') + 1) : bg); return c != null && c[1] > 150 && c[0] < 120 && c[2] < 120; }
    private static boolean isRed(String bg) { int[] c = rgb(bg.contains("|") ? bg.substring(bg.indexOf('|') + 1) : bg); return c != null && c[0] > 150 && c[1] < 110 && c[2] < 110; }

    private static boolean scheduled(String[] tile) { return "true".equalsIgnoreCase(tile[1]) || "1".equals(tile[1]) || "red".equals(tile[2]); }

    @Override
    protected void body() {
        meta("OT - OT Graphical View", "OT > OT Graphical View",
                "Choose operation theatres and see their OT tables (vacant / scheduled); open a table's Current Schedule; tick a vacant slot ('Patient Not Found!') and, when one is booked, a red slot (patient details shown and editable). Nothing is saved.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.OT_page.OTGraphicalView g = new com.kpj.pages.OT_page.OTGraphicalView(page);

        boolean on = g.navigateViaMenu();
        step(page, "Open OT Graphical View", "Click OT -> OT Graphical View", "The OT Graphical View screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - OT Graphical View not reached"); return; }

        List<String> all = g.theatreNames();
        step(page, "Open the Operation Theatre list", "Open the Operation Theatre dropdown", "The operation theatres are offered",
                all.isEmpty() ? "No theatre offered" : all.size() + " theatres offered, first: " + all.get(0), all.isEmpty() ? "FAIL" : "PASS");
        if (all.isEmpty()) { addSummary("Result", "FAILED - no theatres"); return; }

        // ---- choose each theatre in turn (as recorded) and read its tables + legend -------------------------------------------------------------------
        List<String> theatres = new ArrayList<>();
        if (SCAN_ALL) theatres.addAll(all);
        else for (String n : RECORDED_THEATRES) { if (all.contains(n)) theatres.add(n); }
        if (theatres.isEmpty()) theatres.add(all.get(0));

        Map<String, List<String[]>> tilesByTheatre = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        List<String> pickProblems = new ArrayList<>();
        List<String> legendMismatch = new ArrayList<>();
        List<String> staleLegend = new ArrayList<>();
        for (String th : theatres) {
            String r = g.pickTheatre(th);
            if (!r.isEmpty()) { pickProblems.add(th + ": " + r); continue; }
            List<String[]> tiles = g.tables();
            int[] lg = g.legend();
            tilesByTheatre.put(th, tiles);
            int sched = 0; for (String[] t : tiles) if (scheduled(t)) sched++;
            int vac = tiles.size() - sched;
            lines.add(th + ": " + tiles.size() + " table(s) (" + vac + " vacant, " + sched + " scheduled), legend " + lg[0] + "/" + lg[1] + "/" + lg[2]);
            if (tiles.isEmpty()) { if (lg[2] > 0) staleLegend.add(th + " (legend still " + lg[0] + "/" + lg[1] + "/" + lg[2] + ")"); }
            else if (lg[0] != vac || lg[1] != sched || lg[2] != tiles.size()) legendMismatch.add(th + " tiles " + vac + "/" + sched + "/" + tiles.size() + " legend " + lg[0] + "/" + lg[1] + "/" + lg[2]);
        }
        boolean cyc = pickProblems.isEmpty() && !tilesByTheatre.isEmpty();
        step(page, "Choose each Operation Theatre", "Choose " + theatres.size() + " theatre(s) in turn: " + theatres, "Each theatre loads its OT tables (green = vacant, red = scheduled) and the legend counts",
                cyc ? String.join("; ", lines) : "Problem - " + pickProblems, cyc ? "PASS" : "FAIL");
        addSummary("Theatres", theatres.size() + " visited (" + all.size() + " offered)");
        if (!legendMismatch.isEmpty()) addSummary("Legend vs tiles (differs)", String.join("; ", legendMismatch));
        if (!staleLegend.isEmpty()) addSummary("Legend not reset (theatre without tables)", String.join("; ", staleLegend));

        // ---- vacant table: Current Schedule + "Patient Not Found!" -------------------------------------------------------------------------------------
        String theatre = tilesByTheatre.containsKey(VACANT_THEATRE) && !tilesByTheatre.get(VACANT_THEATRE).isEmpty() ? VACANT_THEATRE : null;
        if (theatre == null) { for (Map.Entry<String, List<String[]>> e : tilesByTheatre.entrySet()) if (!e.getValue().isEmpty()) { theatre = e.getKey(); break; } }
        if (theatre == null) { step(page, "Open a vacant table", "Choose a theatre that has tables", "A table tile is shown", "No theatre with tables was found", "FAIL"); addSummary("Result", "FAILED - no tables"); return; }

        g.problems.clear();
        String pr = g.pickTheatre(theatre);
        List<String[]> tiles = g.tables();
        int ti = 0;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i)[0].equals(VACANT_TABLE)) ti = i;
        String tableName = tiles.isEmpty() ? "" : tiles.get(ti)[0];
        boolean clicked = !tiles.isEmpty() && g.clickTable(ti);
        List<String[]> slots = g.slotInfo();
        int green = 0, red = 0; for (String[] s : slots) { if (isGreen(s[2])) green++; else if (isRed(s[2])) red++; }
        step(page, "Open a table's schedule", "Choose " + theatre + "; click the table " + tableName + " (GetScheduleDetails)", "The Current Schedule lists the table's time slots",
                clicked && !slots.isEmpty() ? theatre + " / " + tableName + ": " + slots.size() + " slot(s) from " + slots.get(0)[0] + " to " + slots.get(slots.size() - 1)[0] + " (" + green + " green/free, " + red + " red/booked)"
                        : "Problem - no slots listed " + g.problems, clicked && !slots.isEmpty() ? "PASS" : "FAIL");
        if (slots.isEmpty()) { addSummary("Result", "FAILED - no slots"); return; }

        // first free (green, not booked) slot - tick it
        int freeIdx = 0; for (int i = 0; i < slots.size(); i++) if (isGreen(slots.get(i)[2]) && slots.get(i)[4].isEmpty() && !slots.get(i)[1].equals("disabled")) { freeIdx = i; break; }
        g.problems.clear();
        String toast1 = g.tickSlot(freeIdx);
        String[] d1 = g.patientDetails();
        boolean nf1 = toast1.contains("Patient Not Found") && !g.patientDetailsVisible() && d1[0].isEmpty();
        step(page, "Tick a vacant slot", "Tick the checkbox of the slot " + slots.get(freeIdx)[0] + " (GetPatientDetails(list))", "The warning 'Patient Not Found!' appears and no patient details are shown",
                (toast1.isEmpty() ? "No toast appeared" : "Toast: " + toast1.replaceAll("^\\W*KPJ Portal", "")) + "; patient details " + (g.patientDetailsVisible() ? "SHOWN " + Arrays.toString(d1) : "not shown"), nf1 ? "PASS" : "FAIL");

        // another vacant slot (random)
        int idx2 = freeIdx;
        if (slots.size() > 1) { Random rnd = new Random(); for (int tries = 0; tries < 20 && idx2 == freeIdx; tries++) idx2 = rnd.nextInt(slots.size()); }
        g.problems.clear();
        String toast2 = g.tickSlot(idx2);
        String[] d2 = g.patientDetails();
        boolean nf2 = toast2.contains("Patient Not Found") && !g.patientDetailsVisible() && d2[0].isEmpty();
        step(page, "Tick another vacant slot", "Tick the checkbox of the slot " + slots.get(idx2)[0], "The warning 'Patient Not Found!' appears again",
                (toast2.isEmpty() ? "No toast appeared" : "Toast: " + toast2.replaceAll("^\\W*KPJ Portal", "")) + "; patient details " + (g.patientDetailsVisible() ? "SHOWN " + Arrays.toString(d2) : "not shown"), nf2 ? "PASS" : "FAIL");

        // ---- booked slot (red): patient details shown + editable ---------------------------------------------------------------------------------------
        String bookedTheatre = null, bookedTable = null;
        for (Map.Entry<String, List<String[]>> e : tilesByTheatre.entrySet())
            for (String[] t : e.getValue()) if (scheduled(t) && bookedTheatre == null) { bookedTheatre = e.getKey(); bookedTable = t[0]; }
        String bookNote = "";
        if (bookedTheatre == null && BOOK) {
            com.kpj.pages.OT_page.OTSchedule os = new com.kpj.pages.OT_page.OTSchedule(page);
            boolean list = os.navigateViaMenu();
            String mrn = list ? os.scheduleForToday("General OT", "test") : "";
            bookNote = mrn.isEmpty() ? "OT Schedule booking failed: " + (list ? os.scheduleProblem : "list not reached") : "OT Schedule booked for MRN " + mrn + " (" + os.lastFrom + "-" + os.lastTo + ")";
            g.navigateViaMenu();
            g.pickTheatre("General OT");
            for (String[] t : g.tables()) if (scheduled(t) && bookedTheatre == null) { bookedTheatre = "General OT"; bookedTable = t[0]; }
        }
        if (bookedTheatre == null) {
            step(page, "Tick a booked (red) slot", "Open a table that has a booked slot; tick the red slot", "The patient details of the booked slot are shown and can be edited",
                    "NOT EXERCISED - no table with a booked slot exists today in the " + tilesByTheatre.size() + " theatre(s) visited (every table is vacant, legend 0 scheduled)" + (bookNote.isEmpty() ? "; book one on OT > OT Schedule (or run with -Dotg.book=true) and re-run" : "; " + bookNote), "MANUAL");
            addSummary("Booked slot", "none today" + (bookNote.isEmpty() ? "" : " - " + bookNote));
        } else {
            g.pickTheatre(bookedTheatre);
            List<String[]> bt = g.tables();
            int bi = 0; for (int i = 0; i < bt.size(); i++) if (bt.get(i)[0].equals(bookedTable)) bi = i;
            g.clickTable(bi);
            List<String[]> bs = g.slotInfo();
            int ri = -1; for (int i = 0; i < bs.size(); i++) if (isRed(bs.get(i)[2]) || !bs.get(i)[4].isEmpty()) { ri = i; break; }
            if (ri < 0) {
                step(page, "Tick a booked (red) slot", "Open " + bookedTheatre + " / " + bookedTable + "; tick the red slot", "A red slot is listed", "The table is scheduled but no red slot was found among " + bs.size() + " slot(s)", "FAIL");
            } else {
                String toast = g.tickSlot(ri);
                page.waitForTimeout(800);
                String[] d = g.patientDetails();
                boolean shown = g.patientDetailsVisible() && !d[0].isEmpty() && !d[1].isEmpty();
                step(page, "Tick a booked (red) slot", "Open " + bookedTheatre + " / " + bookedTable + "; tick the red slot " + bs.get(ri)[0], "The patient details (MRN, name, age, gender, doctor, surgery) are shown",
                        shown ? "MRN " + d[0] + ", " + d[1] + ", age " + d[2] + ", " + d[3] + ", doctor " + d[4] + ", surgery " + d[5] : "Details not shown (toast: " + toast + ")", shown ? "PASS" : "FAIL");
                if (shown) {
                    String before = d[1];
                    boolean editable = g.detailEditable("PatientName");
                    String after = g.setDetail("PatientName", before + " edit");
                    boolean edited = editable && after.equals(before + " edit");
                    g.setDetail("PatientName", before);   // put it back (client side only)
                    step(page, "Edit a patient detail", "Type in the Patient Name box of the shown details", "The detail boxes are editable",
                            edited ? "Patient Name box accepted \"" + after + "\" (restored to \"" + before + "\"; nothing is saved by this screen)" : "Box not editable / value not held (" + after + ")", edited ? "PASS" : "FAIL");
                }
                addSummary("Booked slot", bookedTheatre + " / " + bookedTable + " " + bs.get(ri)[0] + " - " + (shown ? "MRN " + d[0] : "details not shown"));
            }
        }

        // ---- last recorded action: another theatre, click its first table -------------------------------------------------------------------------------
        String last = tilesByTheatre.containsKey("Auto OT theatre 97500") && !tilesByTheatre.get("Auto OT theatre 97500").isEmpty() ? "Auto OT theatre 97500" : theatre;
        g.problems.clear();
        g.pickTheatre(last);
        List<String[]> lt = g.tables();
        boolean lc = !lt.isEmpty() && g.clickTable(0);
        List<String[]> ls = g.slotInfo();
        // a table without time slots configured simply shows an empty Current Schedule (the recording ended on such a table: 'Auto OT table 84199')
        step(page, "Open another theatre's table", "Choose " + last + "; click its first table", "The table is selected and its Current Schedule is shown (empty when the table has no time slots)",
                lc ? last + " / " + lt.get(0)[0] + ": " + (ls.isEmpty() ? "Current Schedule is empty (the table has no time slots set up)" : ls.size() + " slot(s)") : "Problem - " + (lt.isEmpty() ? "no tables" : "table not clicked") + " " + g.problems, lc ? "PASS" : "FAIL");
        addSummary("Vacant slot check", "Patient Not Found! " + (nf1 && nf2 ? "shown for both ticked slots" : "NOT as expected"));
        addSummary("Result", nf1 && nf2 ? "Screen checked (nothing saved)" : "See the failed step(s)");
    }
}
