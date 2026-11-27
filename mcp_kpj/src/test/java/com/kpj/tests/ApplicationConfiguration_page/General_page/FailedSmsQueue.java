package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.microsoft.playwright.Request;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

// The page object has the same simple name — it is referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>FailedSmsQueue</b> ({@code #/FailedSmsQueue}) — functional test (FS-01 … FS-15).
 *
 * <p>Page load and header (SMS Service ON/OFF, gateway line), queue data and columns, paging, Refresh, the message pop-up, Select All, Resend /
 * Discard Selected with nothing selected, the Discard and Switch OFF confirmations answered NO, the Settings validations (e-mail, Reminder Days),
 * the gateway health (error codes, subscription end date), console / API errors. Report:
 * {@code test-output/reports/ApplicationConfiguration_General_FailedSmsQueue.html}.</p>
 *
 * <h3>Data</h3>
 * <p><b>Read-only by design.</b> Nothing is resent (Resend sends real SMS at once), discarded, switched or saved: the confirmations are answered
 * NO and the settings validations stop before saving; the test proves no write call ({@code /api/SmsLog/IUD|SaveSettings|SetSmsService}) was
 * sent. {@code -Dfsq.saveSettings=true} additionally saves the settings unchanged (same values back).</p>
 *
 * <pre>
 *   mvn -o test -Dtest=com.kpj.tests.ApplicationConfiguration_page.General_page.FailedSmsQueue -Ddevhis.postrun=off
 * </pre>
 */
public class FailedSmsQueue extends DevHisBase {

    public FailedSmsQueue() { super("ApplicationConfiguration_General_FailedSmsQueue"); }

    public static void main(String[] args) {
        FailedSmsQueue t = new FailedSmsQueue();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final Pattern API = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);

    private com.kpj.pages.ApplicationConfiguration_page.General_page.FailedSmsQueue q;
    private final LocalDate today = LocalDate.now();
    private final boolean saveSettings = "true".equalsIgnoreCase(System.getProperty("fsq.saveSettings", "false"));

    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> apiFailures = new CopyOnWriteArrayList<>();
    private final List<String> defects = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();
    private Map<String, Object> st0 = new LinkedHashMap<>();

    private static final class R {
        String desc, expected, actual = "", status = "PASS";
        R(String desc, String expected) { this.desc = desc; this.expected = expected; }
        R set(String actual, boolean ok) { this.actual = actual; this.status = ok ? "PASS" : "FAIL"; return this; }
        R set(String actual, String status) { this.actual = actual; this.status = status; return this; }
    }
    private interface Body { R run(); }

    // =================================================================================================================

    @Override
    protected void body() {
        meta("Application Configuration - General - FailedSmsQueue", "Application Configuration > General > FailedSmsQueue",
                "Read-only functional test of the Failed SMS Queue: header and gateway status, queue, paging, message pop-up, selections, the Discard / "
                        + "Switch OFF confirmations (answered NO), settings validations and gateway health. No SMS is resent, discarded or switched.");
        attachMonitors();
        login();
        step("FS-00 Login", "Log in with the configured test account (values not shown)", "Authenticated; application shell loaded", "Logged in to " + BASE, "PASS");
        q = new com.kpj.pages.ApplicationConfiguration_page.General_page.FailedSmsQueue(page);
        try {
            tc("FS-01 Page Loading", this::pageLoading);
            if (!q.onScreen()) { addSummary("Result", "FAILED — FailedSmsQueue could not be opened; remaining cases not run"); return; }
            q.spyAlerts();
            tc("FS-02 Queue Data", this::queueData);
            tc("FS-03 Retry Count Column", this::retryCount);
            tc("FS-04 Paging", this::paging);
            tc("FS-05 Refresh", this::refresh);
            tc("FS-06 Message Pop-up", this::messagePopup);
            tc("FS-07 Resend / Discard Selected With Nothing Selected", this::nothingSelected);
            tc("FS-08 Select All", this::selectAll);
            tc("FS-09 Discard Confirmation - NO", this::discardNo);
            tc("FS-10 Switch OFF Confirmation - NO", this::switchNo);
            tc("FS-11 Settings - Invalid E-mail", this::invalidEmail);
            tc("FS-12 Settings - Invalid Reminder Days", this::invalidDays);
            if (saveSettings) tc("FS-13 Settings - Save Unchanged", this::saveUnchanged);
            tc("FS-14 Gateway Health", this::gatewayHealth);
            tc("FS-15 Console / Network Errors", this::consoleCheck);
        } finally { writeSummary(); }
    }

    // =================================================================================================================

    private R pageLoading() {
        R r = new R("Open Application Configuration > General > FailedSmsQueue from the sidebar",
                "The screen shows SMS Service ON/OFF with its switch, the gateway status, Refresh, Resend Selected, Discard Selected, the queue table and the settings");
        if (!q.navigateViaMenu()) return r.set("Did NOT open — " + q.navNotes + " (" + page.url() + ")", false);
        st0 = q.state();
        Map<String, String> b = q.buttons();
        List<String> need = List.of("Refresh", "Resend Selected", "Discard Selected", "Save"), missing = new ArrayList<>();
        for (String n : need) if (!b.containsKey(n)) missing.add(n);
        boolean sw = b.containsKey("Switch OFF") || b.containsKey("Switch ON");
        List<String> h = q.headers();
        return r.set("Loaded in " + secs(q.lastLoadMs) + "; SMS Service " + st0.get("service") + "; " + st0.get("gateway") + "; buttons " + b.keySet() + "; columns " + h
                + (missing.isEmpty() ? "" : "; MISSING " + missing), missing.isEmpty() && sw && h.contains("Error Code") && !s(st0.get("service")).isEmpty());
    }

    private R queueData() {
        R r = new R("Read the queue", "Page 1 holds min(50, total) messages, each with timestamp, type, recipient, error and status; \"page 1 of N\" matches the total");
        Map<String, Object> s = q.state();
        int total = n(s.get("totalCount")), rows = n(s.get("rows")), pages = n(s.get("totalPages"));
        List<Map<String, Object>> d = q.rows();
        List<String> incomplete = new ArrayList<>();
        for (Map<String, Object> x : d) if (s(x.get("type")).isEmpty() || s(x.get("recipient")).isEmpty() || s(x.get("status")).isEmpty()) incomplete.add(s(x.get("id")));
        Map<String, Integer> byStatus = count(d, "status"), byError = count(d, "error");
        boolean ok = rows == Math.min(50, total) && pages == Math.max(1, (int) Math.ceil(total / 50.0)) && incomplete.isEmpty()
                && s(s.get("pageText")).toLowerCase().contains("page 1 of " + pages);
        if (total == 0) return r.set("The queue is empty", "MANUAL");
        return r.set(total + " message(s), " + rows + " on page 1, " + s.get("pageText") + "; status " + byStatus + "; errors " + byError
                + (incomplete.isEmpty() ? "" : "; INCOMPLETE rows " + head(incomplete, 5)), ok);
    }

    private R retryCount() {
        R r = new R("Compare the Retry Count column with each message's attempt count", "Retry Count shows how many times the message was tried");
        List<String> bad = new ArrayList<>();
        List<Map<String, Object>> d = q.rows();
        for (Map<String, Object> x : d) {
            int att = n(x.get("attempts")), shown = n(x.get("shownRetry"));
            if (att > 0 && shown != att && shown != att - 1) bad.add(x.get("id") + ": shown " + x.get("shownRetry") + ", attempts " + att);
        }
        if (d.isEmpty()) return r.set("No messages", "MANUAL");
        if (!bad.isEmpty()) defect("FS-03", "Medium", "Retry Count shows " + d.get(0).get("shownRetry") + " although the messages were tried " + d.get(0).get("attempts")
                + " time(s) (" + bad.size() + " of " + d.size() + " rows)");
        return r.set(bad.isEmpty() ? "Retry Count matches the attempts for " + d.size() + " row(s)" : bad.size() + " row(s) wrong: " + head(bad, 4), bad.isEmpty());
    }

    private R paging() {
        R r = new R("Check « Prev on page 1; click Next; then Prev", "« Prev is disabled on page 1; Next shows page 2 with other messages; Prev returns to page 1");
        Map<String, Object> s1 = q.state();
        if (n(s1.get("totalPages")) < 2) return r.set("Only one page (" + s1.get("totalCount") + " messages)", Boolean.TRUE.equals(s1.get("prevDisabled")) && Boolean.TRUE.equals(s1.get("nextDisabled")) ? "PASS" : "FAIL");
        Set<String> ids1 = ids();
        q.clickNg("fnLoad(PageNo + 1)");
        Map<String, Object> s2 = q.state();
        Set<String> ids2 = ids();
        boolean differ = Collections.disjoint(ids1, ids2) && !ids2.isEmpty();
        q.clickNg("fnLoad(PageNo - 1)");
        Map<String, Object> s3 = q.state();
        boolean ok = Boolean.TRUE.equals(s1.get("prevDisabled")) && n(s2.get("pageNo")) == 2 && differ && n(s3.get("pageNo")) == 1 && ids().equals(ids1);
        return r.set("Page 1 Prev disabled: " + s1.get("prevDisabled") + "; Next → " + s2.get("pageText") + ", " + s2.get("rows") + " row(s), other messages: " + differ
                + "; Prev → " + s3.get("pageText"), ok);
    }

    private Set<String> ids() { Set<String> s = new TreeSet<>(); for (Map<String, Object> x : q.rows()) s.add(s(x.get("id"))); return s; }

    private R refresh() {
        R r = new R("Click Refresh", "The queue and the gateway status are loaded again (same total)");
        int before = n(q.state().get("totalCount"));
        long t0 = System.currentTimeMillis();
        q.clickNg("fnRefresh()");
        Map<String, Object> s = q.state();
        return r.set(s.get("totalCount") + " message(s) (was " + before + ") in " + secs(System.currentTimeMillis() - t0) + "; " + s.get("gateway"), n(s.get("totalCount")) >= 0 && !s(s.get("service")).isEmpty());
    }

    private R messagePopup() {
        R r = new R("Click the first Message Body link; then Close", "A pop-up shows the full message and recipient; Close hides it");
        List<Map<String, Object>> d = q.rows();
        if (d.isEmpty()) return r.set("No messages", "MANUAL");
        if (!q.clickRowControl("fnView(row)", 0)) return r.set("Could not click the message link", false);
        page.waitForTimeout(800);
        boolean open = q.messageModalOpen();
        String txt = q.messageModalText();
        String start = s(d.get(0).get("message"));
        start = start.length() > 30 ? start.substring(0, 30) : start;
        boolean hasMsg = txt.contains(start);
        q.closeMessageModal();
        boolean closed = !q.messageModalOpen();
        return r.set("Opened: " + open + "; shows the message: " + hasMsg + " — " + trim(txt, 160) + "; closed: " + closed, open && hasMsg && closed);
    }

    private R nothingSelected() {
        R r = new R("With no message ticked click Resend Selected, then Discard Selected", "Both say \"Select at least one message.\" and nothing is sent");
        q.toggleAll(false);
        int w0 = q.writeCount();
        int a0 = q.alerts().size();
        q.clickNg("fnResend(fnSelected())");
        List<String> a1 = since(a0);
        a0 = q.alerts().size();
        q.clickNg("fnDiscard(fnSelected())");
        List<String> a2 = since(a0);
        boolean confirm = q.waitConfirm(800);
        if (confirm) q.answerConfirm("NO");
        int w = q.writeCount() - w0;
        boolean ok = a1.stream().anyMatch(x -> x.contains("Select at least one")) && a2.stream().anyMatch(x -> x.contains("Select at least one")) && w == 0 && !confirm;
        return r.set("Resend Selected → " + (a1.isEmpty() ? "no message" : a1) + "; Discard Selected → " + (a2.isEmpty() ? "no message" : a2) + "; write calls " + w, ok);
    }

    private R selectAll() {
        R r = new R("Tick Select All; then untick it", "Every message on the page is ticked, then none");
        q.toggleAll(true);
        long on = q.rows().stream().filter(x -> Boolean.TRUE.equals(x.get("checked"))).count();
        int rows = q.rows().size();
        q.toggleAll(false);
        long off = q.rows().stream().filter(x -> Boolean.TRUE.equals(x.get("checked"))).count();
        return r.set("Ticked " + on + "/" + rows + "; after untick " + off, rows > 0 ? (on == rows && off == 0 ? "PASS" : "FAIL") : "MANUAL");
    }

    private R discardNo() {
        R r = new R("Click Discard on the first message and answer NO", "A confirmation \"Discard 1 message(s)?\" appears; NO closes it, the message stays and nothing is sent");
        List<Map<String, Object>> d = q.rows();
        if (d.isEmpty()) return r.set("No messages", "MANUAL");
        String id = s(d.get(0).get("id"));
        int w0 = q.writeCount();
        q.clickRowControl("fnDiscard([row])", 0);
        boolean shown = q.waitConfirm(5000);
        String txt = q.confirmText();
        if (shown) q.answerConfirm("NO");
        boolean closed = !q.waitConfirm(500);
        boolean still = ids().contains(id);
        int w = q.writeCount() - w0;
        return r.set("Confirmation: " + (shown ? "\"" + trim(txt, 120) + "\"" : "NOT shown") + "; after NO: closed " + closed + ", message " + id + " still listed " + still + ", write calls " + w,
                shown && txt.toLowerCase().contains("discard") && closed && still && w == 0);
    }

    private R switchNo() {
        R r = new R("Click the SMS Service switch and answer NO", "A confirmation explains the effect; NO closes it, the service keeps its state and nothing is sent");
        String before = s(q.state().get("service"));
        String ng = "ON".equals(before) ? "fnSetSmsService(false)" : "fnSetSmsService(true)";
        int w0 = q.writeCount();
        if (!q.clickNg(ng)) return r.set("Switch button not found (" + ng + ")", false);
        boolean shown = q.waitConfirm(5000);
        String txt = q.confirmText();
        if (shown) q.answerConfirm("NO");
        String after = s(q.state().get("service"));
        int w = q.writeCount() - w0;
        return r.set("Service " + before + "; confirmation: " + (shown ? "\"" + trim(txt, 140) + "\"" : "NOT shown") + "; after NO: service " + after + ", write calls " + w,
                shown && before.equals(after) && w == 0);
    }

    private R invalidEmail() {
        R r = new R("Type \"not-an-email\" in Ops Alert E-mails and click Save", "\"Invalid e-mail address: not-an-email\"; nothing is saved");
        String old = s(q.state().get("emails"));
        int w0 = q.writeCount(), a0 = q.alerts().size();
        q.setSetting("opsalertemails", "not-an-email");
        q.clickNg("fnSaveSettings()");
        List<String> a = since(a0);
        int w = q.writeCount() - w0;
        q.setSetting("opsalertemails", old);
        return r.set("Save → " + (a.isEmpty() ? "no message" : a) + "; write calls " + w + "; field restored to \"" + old + "\"",
                a.stream().anyMatch(x -> x.contains("Invalid e-mail")) && w == 0);
    }

    private R invalidDays() {
        R r = new R("Type \"30,abc\" in Reminder Days and click Save", "\"Reminder days must be numbers separated by commas\"; nothing is saved");
        String old = s(q.state().get("reminderDays"));
        int w0 = q.writeCount(), a0 = q.alerts().size();
        q.setSetting("reminderdays", "30,abc");
        q.clickNg("fnSaveSettings()");
        List<String> a = since(a0);
        int w = q.writeCount() - w0;
        q.setSetting("reminderdays", old);
        return r.set("Save → " + (a.isEmpty() ? "no message" : a) + "; write calls " + w + "; field restored to \"" + old + "\"",
                a.stream().anyMatch(x -> x.toLowerCase().contains("reminder days")) && w == 0);
    }

    private R saveUnchanged() {
        R r = new R("Click Save with the settings unchanged", "A success message; the settings keep their values");
        Map<String, Object> s0 = q.state();
        int a0 = q.alerts().size();
        q.clickNg("fnSaveSettings()");
        List<String> a = since(a0);
        Map<String, Object> s1 = q.state();
        boolean same = s(s0.get("emails")).equals(s(s1.get("emails"))) && s(s0.get("reminderDays")).equals(s(s1.get("reminderDays")));
        return r.set("Save → " + (a.isEmpty() ? "no message" : a) + "; values kept: " + same, !a.isEmpty() && same);
    }

    private R gatewayHealth() {
        R r = new R("Check the gateway: error codes of the queued messages and the subscription end date", "Messages fail for varied, temporary reasons; the subscription is current or the gateway shows it as expired");
        Map<String, Object> s = q.state();
        List<Map<String, Object>> d = q.rows();
        Map<String, Integer> errs = count(d, "error");
        String end = s(s.get("subscriptionEnd"));
        LocalDate endDate = null;
        try { endDate = LocalDate.parse(end.length() >= 10 ? end.substring(0, 10) : end); } catch (Exception ignore) { }
        boolean expired = endDate != null && endDate.isBefore(today);
        boolean saysNormal = s(s.get("gateway")).toLowerCase().contains("sending normally");
        List<String> issues = new ArrayList<>();
        if (!d.isEmpty() && errs.size() == 1 && errs.keySet().iterator().next().toUpperCase().contains("AUTHENTICATION")) {
            issues.add("every queued message failed with AUTHENTICATION FAILED");
            defect("FS-14", "High", "All " + s.get("totalCount") + " failed messages show \"AUTHENTICATION FAILED\" (iSMS rejects the account), so no SMS is delivered");
        }
        if (expired && saysNormal) {
            issues.add("subscription ended " + endDate + " but the gateway line says \"sending normally\"");
            defect("FS-14", "Medium", "Subscription End Date " + endDate + " has passed, yet the screen shows \"iSMS gateway: sending normally.\" and sending is not paused");
        }
        return r.set("Errors " + errs + "; subscription end " + end + "; " + s.get("gateway") + (issues.isEmpty() ? "" : "; ISSUES: " + String.join("; ", issues)), issues.isEmpty());
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private R consoleCheck() {
        R r = new R("Console errors, page errors and failed API calls collected during the run; write calls", "None");
        return r.set("Console: " + (consoleErrors.isEmpty() ? "none" : consoleErrors.size() + " — " + head(consoleErrors, 4))
                + "; API failures: " + (apiFailures.isEmpty() ? "none" : apiFailures.size() + " — " + head(apiFailures, 4))
                + "; write calls: " + (q.writes.isEmpty() ? "none" : q.writes), consoleErrors.isEmpty() && apiFailures.isEmpty());
    }

    private Map<String, Integer> count(List<Map<String, Object>> d, String key) {
        Map<String, Integer> m = new TreeMap<>();
        for (Map<String, Object> x : d) m.merge(s(x.get(key)).isEmpty() ? "(blank)" : s(x.get(key)), 1, Integer::sum);
        return m;
    }

    private void tc(String name, Body b) {
        System.out.println("[FailedSmsQueue] --- " + name + " ---");
        long t0 = System.currentTimeMillis();
        R r;
        try { r = b.run(); }
        catch (Throwable e) { r = new R("Run " + name, "Case completes").set("ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        step(page, name, r.desc, r.expected, r.actual + " [execution time " + secs(System.currentTimeMillis() - t0) + "]", r.status);
    }

    private List<String> since(int n0) {
        page.waitForTimeout(500);
        List<String> a = q.alerts();
        List<String> out = new ArrayList<>();
        for (int i = Math.min(n0, a.size()); i < a.size(); i++) out.add(a.get(i).replaceFirst("^\\w*:\\s*", ""));
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    private void attachMonitors() {
        page.onConsoleMessage(c -> { if ("error".equals(c.type())) consoleErrors.add(route() + " " + trim(c.text(), 200)); });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 200)));
        page.onResponse(resp -> {
            if (!API.matcher(resp.url()).find()) return;
            Request rq = resp.request();
            if (resp.status() >= 400) apiFailures.add(route() + " " + rq.method() + " " + resp.url().replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", "") + " → HTTP " + resp.status());
        });
    }

    private void defect(String id, String severity, String text) { String d = "[" + severity + "] " + id + " " + text; if (!defects.contains(d)) defects.add(d); }
    @SuppressWarnings("unused")
    private void observation(String id, String text) { String d = id + " " + text; if (!observations.contains(d)) observations.add(d); }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.ApplicationConfiguration_page.General_page.FailedSmsQueue.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("SMS Service / gateway", s(st0.get("service")) + " / " + s(st0.get("gateway")));
        addSummary("Messages in the queue", s(st0.get("totalCount")));
        addSummary("Write calls sent", q == null || q.writes.isEmpty() ? "None (read-only run)" : String.valueOf(q.writes));
        addSummary("Application defects found", defects.isEmpty() ? "None" : String.valueOf(defects.size()));
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        i = 1;
        for (String o : observations) addSummary("Observation " + (i++), o);
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 4));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 4));
    }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : u.replaceAll("^https?://[^/]+", "")) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static int n(Object o) { try { return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return -1; } }
    private static String secs(long ms) { return ms < 0 ? "—" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0); }
    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String head(Collection<?> l, int n) {
        if (l == null) return "";
        List<String> x = new ArrayList<>(); for (Object o : l) x.add(String.valueOf(o));
        return x.size() <= n ? String.join("; ", x) : String.join("; ", x.subList(0, n)) + "; … (+" + (x.size() - n) + " more)";
    }
}
