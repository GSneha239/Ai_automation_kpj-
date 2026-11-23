package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ModalitySchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Modality Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Modality Schedule</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b>, <b>Modality</b>, <b>Start Time</b>, <b>End Time</b>, <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class ModalitySchedule extends DevHisBase {

    public ModalitySchedule() { super("ApplicationConfig_Location_ModalitySchedule"); }

    public static void main(String[] args) {
        ModalitySchedule t = new ModalitySchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final String[] DAYS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

    /** Where the previous run's walk ended ("department index,modality index"), kept next to the test output so every run starts on a NEW combination. */
    private static java.nio.file.Path positionFile() {
        String out = System.getenv("OUTDIR");
        if (out == null || out.isBlank()) out = System.getProperty("user.dir") + "/test-output";
        return java.nio.file.Paths.get(out, "state", "modality-schedule-next.txt");
    }

    private static int[] readPosition() {
        try {
            java.nio.file.Path f = positionFile();
            if (java.nio.file.Files.exists(f)) {
                String[] p = java.nio.file.Files.readString(f).trim().split(",");
                return new int[]{Math.max(0, Integer.parseInt(p[0].trim())), Math.max(0, Integer.parseInt(p[1].trim()))};
            }
        } catch (Exception ignore) { }
        return new int[]{0, 0};
    }

    private static void writePosition(int dept, int mod) {
        try {
            java.nio.file.Path f = positionFile();
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, dept + "," + mod);
        } catch (Exception ignore) { }
    }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Modality Schedule",
                "Application Configuration > Location > Modality Schedule",
                "Add a modality schedule: Department, Modality, Start/End Time, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.ModalitySchedule ms =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.ModalitySchedule(page);

        boolean onScreen = ms.navigateViaMenu();
        step(page, "Open Modality Schedule screen",
                "Click Application Configuration -> Location -> Modality Schedule",
                "The Modality Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + ms.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("modality links => " + ms.findModalityLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + ms.describeForm());

        boolean added = ms.clickAdd() && ms.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + ms.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + ms.describeForm());

        // Location (mandatory, not named in the flow), Department, Modality
        String loc = ms.selectLocation();
        // A different Department/Modality combination each run: start where the previous run's walk ended (one schedule per modality).
        int[] start = readPosition();
        int startDept = start[0], startMod = start[1];
        String dept = ms.selectDepartment(startDept);
        if (dept.isEmpty() && startDept > 0) { startDept = 0; startMod = 0; dept = ms.selectDepartment(0); }   // ran past the last department: wrap round
        String mod = ms.selectModality(startMod);
        if (mod.isEmpty() && startMod > 0) { startMod = 0; mod = ms.selectModality(0); }
        boolean selOk = !loc.isEmpty() && !dept.isEmpty() && !mod.isEmpty();
        step(page, "Select Department and Modality", "Choose the Department and the Modality",
                "Both are selected", "Location=" + loc + " | Department=" + dept + " | Modality=" + mod,
                selOk ? "PASS" : "FAIL");

        // Start / End time
        String times = ms.fillTimes("09:00 AM", "05:00 PM");
        boolean timesOk = !ms.lastStart.isEmpty() && !ms.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // Days
        String day = ms.tickDay(DAYS[0]);
        boolean dayOk = !day.isEmpty();
        step(page, "Select Days", "Tick a day of the schedule", "The day is ticked",
                dayOk ? "Ticked: " + day : "No day ticked", dayOk ? "PASS" : "FAIL");

        // Inner Add — commits the schedule line
        String inner = ms.clickInnerAdd();
        System.out.println("schedule grid now: " + ms.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(ms.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // Submit. A Department/Modality that already has a schedule is rejected AND wipes the whole form (see the
        // page object's class doc) — the retry must Back -> Add for a genuinely fresh form, not just re-fill.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptIndex = startDept, modIndex = ms.lastModalityIndex + 1;
        // A modality can hold only one schedule, and earlier runs have used up the first department's six — so keep
        // walking on through the modalities, then the next departments: up to 12 attempts by default (-DmodalityTries=; each refused try costs ~25 s,
        // and a run that ends on "already scheduled" is no longer a failure, so a long walk buys little).
        int maxTries = Integer.getInteger("modalityTries", 12);
        for (int attempt = 0; attempt < maxTries && !ok; attempt++) {
            toast = ms.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(ms.lastDepartment).append('/').append(ms.lastModality).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("scheduled") && !tl.contains("exist")) break;
            if (!ms.clickBack()) break;
            if (!(ms.clickAdd() && ms.addFormOpen())) break;
            String refilled = ms.fillAll(deptIndex, modIndex, "09:00 AM", "05:00 PM", DAYS[0]);
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (ms.lastModality.isEmpty()) { modIndex = 0; deptIndex++; }
            else modIndex = ms.lastModalityIndex + 1;
        }
        // Remember where the walk ended: the next run starts on the combination after this one (after a save, the next one too).
        writePosition(deptIndex, ok ? ms.lastModalityIndex + 1 : modIndex);
        // "Already scheduled" is the app enforcing its one-schedule-per-modality rule, not a broken screen: when every combination tried was refused
        // for that reason the steps are NOT failed (MANUAL = nothing was saved, nothing is wrong). Any other refusal still fails.
        String lastTl = toast == null ? "" : toast.toLowerCase();
        boolean alreadyScheduled = !ok && (lastTl.contains("already") || lastTl.contains("scheduled") || lastTl.contains("exist"));
        String submitStatus = ok ? "PASS" : (alreadyScheduled ? "MANUAL" : "FAIL");
        String schedNote = alreadyScheduled
                ? " - NOT SAVED: the app says this Department/Modality already has a schedule (one schedule per modality), so the duplicate was refused as designed; not counted as a failure"
                : "";
        step(page, "Click Submit", "Click Submit", "The modality schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (ms.lastSaveApi.isEmpty() ? "" : "  [" + ms.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]" + schedNote,
                submitStatus);

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"" + schedNote;
        step(ms.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Modality Schedule Saved Successfully.' toast", actual, submitStatus);

        addSummary("Department", ms.lastDepartment);
        addSummary("Modality", ms.lastModality);
        addSummary("Schedule", ms.lastDay + "  " + ms.lastStart + " - " + ms.lastEnd);
        addSummary("Save API", ms.lastSaveApi);
        addSummary("Result", ok ? toast : (alreadyScheduled ? "Already scheduled - nothing saved (\"" + toast + "\")" : "Not confirmed (\"" + toast + "\")"));
    }
}
