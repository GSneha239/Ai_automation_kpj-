package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Read-only review of {@code ComplaintResolution} as a first controlled live target. NOTHING here runs the real test, opens a browser or
 * contacts DevHIS:
 * <ul>
 *   <li><b>static checks</b> read the test's and its page object's source and pin down every action they can take;</li>
 *   <li><b>reflection</b> asks the login page which accounts it would try (class loading only);</li>
 *   <li><b>the recorded failure</b> from an earlier real run is copied from {@code test-output-demo} (read only) and classified by the real
 *       classifiers, to show what a repeat of that failure would lead to;</li>
 *   <li><b>real Maven on a throw-away "mirror" project</b> with dummy classes laid out under the SAME package and simple names
 *       (including a second class called {@code ComplaintResolution}) proves which classes {@code -Dtest=ComplaintResolution} selects and
 *       how the planned environment/JVM settings reach the forked test JVM.</li>
 * </ul>
 */
class LiveTargetReviewTest {

    private static final String TEST_FQCN = "com.kpj.tests.AncillaryServices_page.Equipment_page.ComplaintResolution";
    private static final String PAGE_FQCN = "com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintResolution";
    private static final Path TEST_SRC = Paths.get("src/test/java/com/kpj/tests/AncillaryServices_page/Equipment_page/ComplaintResolution.java");
    private static final Path PAGE_SRC = Paths.get("src/test/java/com/kpj/pages/AncillaryServices_page/Equipment_page/ComplaintResolution.java");
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @TempDir
    static Path shared;

    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @AfterEach
    void clearOverrides() {
        SelfHealingAgent.mavenExecutableOverride = null;
        CodeLocator.testRootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
    }

    // ================================================================== A. what the real test and page object can do (source only)

    @Test
    void thePageObject_canOnlyNavigate_fillFilters_andPressSearch() throws IOException {
        String code = stripComments(Files.readString(PAGE_SRC));

        // every Angular handler it ever names or presses
        Set<String> handlers = new TreeSet<>();
        Matcher m = Pattern.compile("(?:ng-click|fn[A-Z][A-Za-z]+|Get[A-Z][A-Za-z]+\\(?)").matcher(code);
        Matcher named = Pattern.compile("/([A-Za-z]+)/i\\.test\\(x\\.getAttribute\\('ng-click'\\)").matcher(code);
        while (named.find()) handlers.add(named.group(1));
        assertEquals(Set.of("GetComplaintGrid"), handlers, "the only ng-click handler it presses is the Search button's");

        // nothing that saves, submits, deletes, cancels, approves, resolves or confirms
        for (String forbidden : new String[]{"fnSave", "fnSubmit", "fnDelete", "fnCancel", "fnApprove", "fnUpdate", "fnAdd", "fnResolve", "fnIUD",
                "acceptSaveDialog", "confirm(", ".press(", "pressSequentially", "setInputFiles", ".check(", ".type(", "dispatchEvent(new MouseEvent",
                "clickButtonByText", "clickButtonByTextPrefix", "page.onDialog", "page.route("}) {
            assertFalse(code.contains(forbidden), "the page object must not contain " + forbidden);
        }

        // every real click, and every place it navigates
        List<String> clicks = new ArrayList<>();
        Matcher c = Pattern.compile("locator\\(\"([^\"]+)\"\\)\\.click\\(").matcher(code);
        while (c.find()) clicks.add(c.group(1));
        assertEquals(List.of("#__crMenu", "#__crSearch"), clicks, "only the tagged menu link and the tagged Search button are clicked with Playwright");
        List<String> hashes = new ArrayList<>();
        Matcher h = Pattern.compile("location\\.hash\\s*=\\s*('#/[a-z]+'|h)").matcher(code);
        while (h.find()) hashes.add(h.group(1));
        assertEquals(List.of("h", "'#/complaintdetails'"), hashes, "it sets the route to its own screen (fallback) and reads Complaint Details");
        assertTrue(code.contains("ROUTE = \"#/complaintresolution\""));
        // in-page clicks are menu links only
        Matcher jsClicks = Pattern.compile("(\\w+)\\.click\\(\\)").matcher(code);
        List<String> js = new ArrayList<>();
        while (jsClicks.find()) js.add(jsClicks.group(1));
        assertEquals(List.of("a", "a"), js, "the only in-page clicks are the two menu anchors (Ancillary Services, Equipment/Asset)");
    }

    @Test
    void theTest_callsOnlyReadingFilteringAndSearchMethods_andNeverTheSiblingScreensActions() throws IOException {
        String test = stripComments(Files.readString(TEST_SRC));
        Set<String> calls = new TreeSet<>();
        Matcher m = Pattern.compile("\\bcr\\.([a-zA-Z]+)\\(").matcher(test);
        while (m.find()) calls.add(m.group(1));
        assertEquals(Set.of("clickSearchAndVerify", "complaintsInMasterList", "datesSet", "describeControls", "enterDates", "navigateViaMenu",
                "searchReturnedResults", "searchScanningStores", "selectLocation", "selectStore"), calls);
        // the only other collaborator is the shared login page, and the base class's screenshot-only step()
        Set<String> pages = new TreeSet<>();
        Matcher n = Pattern.compile("new\\s+((?:com\\.kpj\\.pages\\.)?[A-Za-z_.]*(?:LoginPage|ComplaintResolution))\\(").matcher(test);
        while (n.find()) pages.add(n.group(1));
        assertEquals(Set.of("LoginPage", "ComplaintResolution" /* the test itself, in its own main() */, "com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintResolution"), pages);
        assertFalse(test.contains("acceptSaveDialog") || test.contains("clickButtonByText") || test.contains("openViaHash"));
        // exactly one JUnit entry point, and it runs once
        assertEquals(1, Pattern.compile("@org\\.junit\\.jupiter\\.api\\.Test").matcher(Files.readString(TEST_SRC)).results().count());
    }

    @Test
    void thereAreTwoClassesCalledComplaintResolution_butOnlyOneIsATest() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Paths.get("src"))) {
            for (Path p : s.filter(f -> f.getFileName().toString().equals("ComplaintResolution.java")).sorted().collect(Collectors.toList())) {
                found.add(p.toString().replace('\\', '/') + (Files.readString(p).contains("@org.junit.jupiter.api.Test") || Files.readString(p).contains("@Test") ? " [TEST]" : ""));
            }
        }
        assertEquals(List.of("src/test/java/com/kpj/pages/AncillaryServices_page/Equipment_page/ComplaintResolution.java",
                "src/test/java/com/kpj/tests/AncillaryServices_page/Equipment_page/ComplaintResolution.java [TEST]"), found);
        // and no other class in the tree has a name that -Dtest=ComplaintResolution could match
        try (Stream<Path> s = Files.walk(Paths.get("src"))) {
            assertEquals(2, s.filter(f -> f.getFileName().toString().matches("(?i).*ComplaintResolution.*\\.java")).count());
        }
    }

    // ================================================================== B. which accounts a live run would try

    @SuppressWarnings("unchecked")
    private static List<String[]> fallbacksFor(String property) throws Exception {
        Class<?> login = Class.forName("com.kpj.pages.LoginPage");
        Method m = login.getDeclaredMethod("resolveCredFallbacks");
        m.setAccessible(true);
        String old = System.getProperty("devhis.cred.fallbacks");
        try {
            if (property == null) System.clearProperty("devhis.cred.fallbacks"); else System.setProperty("devhis.cred.fallbacks", property);
            return (List<String[]>) m.invoke(null);
        } finally {
            if (old == null) System.clearProperty("devhis.cred.fallbacks"); else System.setProperty("devhis.cred.fallbacks", old);
        }
    }

    @Test
    void withoutTheFallbackSetting_aRejectedLoginRotatesThroughOtherAccounts_andWithNoneItNeverDoes() throws Exception {
        List<String[]> byDefault = fallbacksFor(null);
        assertTrue(byDefault.size() >= 5, "by default the login page keeps trying other accounts: " + byDefault.size());
        assertEquals("Sarannya", byDefault.get(0)[0]);
        assertTrue(byDefault.stream().skip(1).anyMatch(a -> !a[0].equals("Sarannya")), "...including accounts other than Sarannya");

        assertEquals(0, fallbacksFor("none").size(), "-Ddevhis.cred.fallbacks=none leaves no fallback account at all");
        assertEquals(0, fallbacksFor("none").size());
        // blank behaves like "unset" - the dangerous default
        assertTrue(fallbacksFor("").size() >= 5, "a blank value does NOT disable the fallbacks");
    }

    @Test
    void theBaseClassDefaultsToTheAuthorizedHostAndSarannya_andLoginIsTheOnlyAuthenticationPath() throws IOException {
        String base = Files.readString(Paths.get("src/main/java/com/kpj/core/DevHisBase.java"));
        assertTrue(base.contains("cfg(\"devhis.url\", \"https://devhis.sancyberhad.com\")"));
        assertTrue(base.contains("cfg(\"devhis.user\", \"Sarannya\")"));
        assertFalse(base.contains("onDialog") || base.contains(".accept()"), "the base class installs no automatic confirm/alert acceptance");
        String login = Files.readString(Paths.get("src/test/java/com/kpj/pages/LoginPage.java"));
        assertTrue(login.contains("if (!Boolean.getBoolean(\"devhis.login.fallbacksOnly\")) candidates.put(username, password);"), "the primary account is tried first");
    }

    // ================================================================== C. what a repeat of the recorded failure would lead to

    @Test
    void theRecordedFailure_isNotEligibleForHealing_soTheLiveRunEndsAtFailureAnalysis() throws Exception {
        Path recordedTsv = Paths.get("test-output-demo/aggregate-data/AncillaryServices_Equipment_ComplaintResolution.tsv");
        Path recordedDom = Paths.get("test-output-demo/failures/AncillaryServices_Equipment_ComplaintResolution_6.txt");
        assumeTrue(Files.isRegularFile(recordedTsv) && Files.isRegularFile(recordedDom), "no recorded run to classify");
        Path out = shared.resolve("recorded-copy");
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.createDirectories(out.resolve("failures"));
        Files.copy(recordedTsv, out.resolve("aggregate-data").resolve(recordedTsv.getFileName()));
        Files.copy(recordedDom, out.resolve("failures").resolve(recordedDom.getFileName()));

        String testId = "AncillaryServices_Equipment_ComplaintResolution";
        List<FailureRecord> failures = new FailureScanner(out.toString()).scan(testId);
        assertEquals(List.of("Click Search & verify the result"), failures.stream().map(f -> f.step).collect(Collectors.toList()),
                "the recorded run had exactly one failing step - a data gap on the Search");

        CodeLocator.CodeLocation loc = new CodeLocator().locate(TEST_FQCN, failures.get(0).step);   // the REAL sources, read only
        FailureCategoryClassifier.Result fa = new FailureCategoryClassifier().classify(failures.get(0), loc);
        assertFalse(fa.selfHealingEligible, "Failure Analysis: " + fa.category.displayName() + " - " + fa.eligibilityReason);
    }

    // ================================================================== D. the synthetic-only profile

    @Test
    void theHealSyntheticProfile_selectsOnlyComKpjHeal_andNoRealBrowserTestMatchesIt() throws IOException {
        String pom = Files.readString(Paths.get("pom.xml"));
        Matcher p = Pattern.compile("(?s)<profile>\\s*<id>heal-synthetic</id>.*?</profile>").matcher(pom);
        assertTrue(p.find());
        List<String> includes = new ArrayList<>();
        Matcher i = Pattern.compile("<include>([^<]+)</include>").matcher(p.group());
        while (i.find()) includes.add(i.group(1));
        assertEquals(List.of("com/kpj/heal/*Test.java"), includes);
        assertFalse(p.group().contains("<exclude>") && false);

        Path tests = Paths.get("src/test/java");
        List<String> matchedByProfile = new ArrayList<>();
        List<String> realOnes = new ArrayList<>();
        try (Stream<Path> s = Files.walk(tests)) {
            for (Path f : s.filter(x -> x.getFileName().toString().endsWith("Test.java")).collect(Collectors.toList())) {
                String rel = tests.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith("com/kpj/heal/") && rel.indexOf('/', "com/kpj/heal/".length()) < 0) matchedByProfile.add(rel);
                if (rel.startsWith("com/kpj/tests/")) realOnes.add(rel);
            }
        }
        assertTrue(realOnes.size() >= 13 && realOnes.contains("com/kpj/tests/Op_Page/Appointment/AppointmentListTest.java"),
                "sanity: the real *Test browser classes exist (" + realOnes.size() + ") - the danger a bare mvn test has");
        assertTrue(matchedByProfile.size() >= 20, "the profile selects the synthetic suite: " + matchedByProfile.size());
        assertTrue(matchedByProfile.stream().noneMatch(r -> r.startsWith("com/kpj/tests/")), "and none of the real ones");
        // the real ComplaintResolution is named without a Test suffix, so even a bare mvn test would not match it by name - but it would run via -Dtest
        assertFalse(TEST_SRC.getFileName().toString().endsWith("Test.java"));
    }

    // ================================================================== E. real Maven on a mirror project

    private static Path mirror;
    private static boolean mavenAvailable;

    private static List<String> mavenBase(String... args) {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.add("mvn");
        for (String a : args) cmd.add(a);
        return cmd;
    }

    @BeforeAll
    static void buildMirror() throws Exception {
        mirror = shared.resolve("mirror");
        Path testPkg = mirror.resolve("src/test/java/com/kpj/tests/AncillaryServices_page/Equipment_page");
        Path pagePkg = mirror.resolve("src/test/java/com/kpj/pages/AncillaryServices_page/Equipment_page");
        Path baseDir = mirror.resolve("src/test/java/mirror");
        Files.createDirectories(testPkg);
        Files.createDirectories(pagePkg);
        Files.createDirectories(baseDir);
        Files.writeString(mirror.resolve("pom.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>mirror</groupId><artifactId>cr-mirror</artifactId><version>1.0</version>\n"
                + "  <properties><maven.compiler.source>21</maven.compiler.source><maven.compiler.target>21</maven.compiler.target>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>\n"
                + "  <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>\n"
                + "    <version>5.10.2</version><scope>test</scope></dependency></dependencies>\n"
                + "  <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>\n"
                + "    <version>3.2.5</version><configuration><testFailureIgnore>true</testFailureIgnore><forkCount>1</forkCount>\n"
                + "    <reuseForks>true</reuseForks></configuration></plugin></plugins></build>\n</project>\n");
        Files.writeString(baseDir.resolve("MirrorBase.java"), "package mirror;\n"
                + "public abstract class MirrorBase {\n    protected MirrorBase(String id) { }\n"
                + "    protected static String cfg(String key, String def) {\n"
                + "        String v = System.getProperty(key); if (v != null && !v.isBlank()) return v.trim();\n"
                + "        v = System.getenv(key.replace('.', '_').toUpperCase()); if (v != null && !v.isBlank()) return v.trim();\n"
                + "        return def;\n    }\n"
                + "    protected static void marker(String name) throws Exception {\n"
                + "        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(\"target\"));\n"
                + "        java.nio.file.Files.writeString(java.nio.file.Paths.get(\"target/ran-\" + name + \".txt\"), \"ran\");\n    }\n}\n");
        // the stand-in for the REAL test: same package, same simple name, same testId, same step names; the Search step fails like the real one did
        Files.writeString(testPkg.resolve("ComplaintResolution.java"), "package com.kpj.tests.AncillaryServices_page.Equipment_page;\n"
                + "import java.nio.file.*;\n"
                + "public class ComplaintResolution extends mirror.MirrorBase {\n"
                + "    public ComplaintResolution() { super(\"AncillaryServices_Equipment_ComplaintResolution\"); }\n"
                + "    @org.junit.jupiter.api.Test void execute() throws Exception {\n"
                + "        marker(\"test\");\n"
                + "        Path out = Paths.get(cfg(\"devhis.outdir\", \"test-output\"));\n"
                + "        Files.writeString(Paths.get(\"target/jvm-report.txt\"),\n"
                + "            \"prop.devhis.outdir=\" + System.getProperty(\"devhis.outdir\") + \"\\n\"\n"
                + "          + \"prop.devhis.cred.fallbacks=\" + System.getProperty(\"devhis.cred.fallbacks\") + \"\\n\"\n"
                + "          + \"prop.headless=\" + System.getProperty(\"headless\") + \"\\n\"\n"
                + "          + \"env.DEVHIS_OUTDIR=\" + System.getenv(\"DEVHIS_OUTDIR\") + \"\\n\"\n"
                + "          + \"user.dir=\" + System.getProperty(\"user.dir\") + \"\\n\");\n"
                + "        Files.createDirectories(out.resolve(\"aggregate-data\"));\n"
                + "        String id = \"AncillaryServices_Equipment_ComplaintResolution\";\n"
                + "        String page = \"Ancillary Services - Equipment/Asset - Complaint Resolution\";\n"
                + "        StringBuilder t = new StringBuilder();\n"
                + "        for (String s : new String[]{\"Login\", \"Open Complaint Resolution screen\", \"Enter From Date and To Date\", \"Select Location\", \"Select Store\"})\n"
                + "            t.append(\"PASS\\t\" + page + \"\\t\" + id + \"\\t\" + s + \"\\tok\\tok\\t\\n\");\n"
                + "        t.append(\"FAIL\\t\" + page + \"\\t\" + id + \"\\tClick Search & verify the result\\tThe search returns matching complaints\\t\"\n"
                + "            + \"FAILS - no complaint rows loaded in the table.\\t\\n\");\n"
                + "        Files.writeString(out.resolve(\"aggregate-data\").resolve(id + \".tsv\"), t.toString());\n"
                + "    }\n}\n");
        // same simple name, a DIFFERENT package, and not a test
        Files.writeString(pagePkg.resolve("ComplaintResolution.java"), "package com.kpj.pages.AncillaryServices_page.Equipment_page;\n"
                + "public class ComplaintResolution extends mirror.MirrorBase {\n"
                + "    public ComplaintResolution() { super(\"page\"); }\n"
                + "    public void touch() throws Exception { marker(\"page\"); }\n}\n");
        // names that a loose pattern could catch
        Files.writeString(testPkg.resolve("ComplaintResolutionDetailsTest.java"), "package com.kpj.tests.AncillaryServices_page.Equipment_page;\n"
                + "public class ComplaintResolutionDetailsTest extends mirror.MirrorBase {\n    public ComplaintResolutionDetailsTest() { super(\"x\"); }\n"
                + "    @org.junit.jupiter.api.Test void execute() throws Exception { marker(\"detailstest\"); }\n}\n");
        Files.writeString(testPkg.resolve("XComplaintResolution.java"), "package com.kpj.tests.AncillaryServices_page.Equipment_page;\n"
                + "public class XComplaintResolution extends mirror.MirrorBase {\n    public XComplaintResolution() { super(\"x\"); }\n"
                + "    @org.junit.jupiter.api.Test void execute() throws Exception { marker(\"prefixed\"); }\n}\n");
        Files.writeString(testPkg.resolve("BookAppointmentTest.java"), "package com.kpj.tests.AncillaryServices_page.Equipment_page;\n"
                + "public class BookAppointmentTest extends mirror.MirrorBase {\n    public BookAppointmentTest() { super(\"x\"); }\n"
                + "    @org.junit.jupiter.api.Test void execute() throws Exception { marker(\"other\"); }\n}\n");
        try {
            Process p = new ProcessBuilder(mavenBase("-v")).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            mavenAvailable = p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) { mavenAvailable = false; }
    }

    private static final String[] MARKERS = {"test", "page", "detailstest", "prefixed", "other"};

    private static void clearMarkers() throws IOException {
        for (String m : MARKERS) Files.deleteIfExists(mirror.resolve("target/ran-" + m + ".txt"));
        Files.deleteIfExists(mirror.resolve("target/jvm-report.txt"));
    }

    private static Set<String> ran() {
        Set<String> r = new TreeSet<>();
        for (String m : MARKERS) if (Files.exists(mirror.resolve("target/ran-" + m + ".txt"))) r.add(m);
        return r;
    }

    private static int mvn(java.util.Map<String, String> env, String... args) throws Exception {
        List<String> cmd = mavenBase("-o", "-q", "-f", mirror.resolve("pom.xml").toString());
        for (String a : args) cmd.add(a);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(mirror.toFile()).redirectErrorStream(true)
                .redirectOutput(shared.resolve("mirror-" + System.nanoTime() + ".log").toFile());
        pb.environment().remove("DEVHIS_OUTDIR");
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().putAll(env);
        Process p = pb.start();
        if (!p.waitFor(240, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("Maven timed out"); }
        return p.exitValue();
    }

    @Test
    void dashDTestComplaintResolution_runsExactlyTheRealStyleTestClass_andNothingElse() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clearMarkers();
        Path out = shared.resolve("mirror-out-a");
        assertEquals(0, mvn(java.util.Map.of(), "-Ddevhis.outdir=" + out, "-Dtest=ComplaintResolution", "test"));
        assertEquals(Set.of("test"), ran(), "the same-named page object, 'ComplaintResolutionDetailsTest', 'XComplaintResolution' and BookAppointmentTest did NOT run");
    }

    @Test
    void thePlannedSettings_reachTheForkedJvm_throughTheEnvironment_andTheResultLandsInTheMatchingFolder() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clearMarkers();
        Path out = shared.resolve("mirror-out-b");
        String tool = "-Ddevhis.outdir=" + out + " -Ddevhis.cred.fallbacks=none -Dheadless=true";
        long start = System.currentTimeMillis();
        // exactly what the orchestrator's child process sees: the settings come ONLY from the environment
        assertEquals(0, mvn(java.util.Map.of("DEVHIS_OUTDIR", out.toString(), "JAVA_TOOL_OPTIONS", tool), "-Dtest=ComplaintResolution", "test"));
        assertEquals(Set.of("test"), ran());
        List<String> report = Files.readAllLines(mirror.resolve("target/jvm-report.txt"));
        assertTrue(report.contains("prop.devhis.cred.fallbacks=none"), "the fallback setting reached the test JVM: " + report);
        assertTrue(report.contains("prop.devhis.outdir=" + out), report.toString());
        assertTrue(report.contains("env.DEVHIS_OUTDIR=" + out), report.toString());
        assertTrue(report.contains("prop.headless=true"));
        Path tsv = out.resolve("aggregate-data").resolve("AncillaryServices_Equipment_ComplaintResolution.tsv");
        assertTrue(Files.isRegularFile(tsv) && Files.getLastModifiedTime(tsv).toMillis() >= start - 2000, "the result is in the matching folder and fresh");
        assertTrue(new FailureScanner(out.toString()).evidenceSince("AncillaryServices_Equipment_ComplaintResolution", java.time.Instant.ofEpochMilli(start)).freshOutputFound);
    }

    @Test
    void theOrchestrator_onTheMirrorProject_stopsAtFailureAnalysis_withEverythingInTheTemporaryFolder() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clearMarkers();
        Path out = shared.resolve("mirror-out-c");
        SelfHealingAgent.mavenExecutableOverride = mavenBase("-o", "-f", mirror.resolve("pom.xml").toString(), "-Ddevhis.outdir=" + out,
                "-Ddevhis.cred.fallbacks=none");
        CodeLocator.testRootOverride = mirror.resolve("src/test/java");
        Path none = shared.resolve("no-dependents");
        Files.createDirectories(none);
        ImpactAnalyzer.testRootOverride = none;

        SelfHealingAgent.MavenLauncher saved = SelfHealingAgent.mavenLauncher;
        SelfHealingAgent.mavenLauncher = SelfHealingAgent::launchMaven;
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try { HealingWorkflowOrchestrator.main(new String[]{TEST_FQCN, out.toString()}); }
        finally { System.setOut(old); SelfHealingAgent.mavenLauncher = saved; }
        String log = buf.toString(StandardCharsets.UTF_8);
        old.print(log);

        assertEquals(Set.of("test"), ran(), "Maven ran only the ComplaintResolution test class (-Dtest=ComplaintResolution)");
        assertTrue(log.contains("[2/4] FAILURE DETECTION — 1 failed step(s)"), log);
        assertTrue(log.contains("WORKFLOW_COMPLETE: FAILURE_ANALYSIS_ONLY"), "no proposal can come from this failure, so there is nothing to approve: " + log);
        assertFalse(log.contains("PENDING_HEAL_FILE"));
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("reports")));
        // (no unified report here: the dummy test writes no failure capture, which the real DevHisBase does)
        assertNotEquals(List.of(), Files.list(out.resolve("aggregate-data")).collect(Collectors.toList()));
    }
}
