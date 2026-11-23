package com.kpj.heal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that the synthetic Self-Healing suite itself can never reach a browser, DevHIS or a real project test. Two independent kinds of
 * evidence: a STATIC scan of every synthetic test source for anything that could start one, and RUNTIME facts about this very test
 * run (no real test report, no project output file and no browser/Maven child process has appeared since the JVM started).
 */
class SafetyGuardTest {

    private static final Path HEAL_TESTS = Paths.get("src/test/java/com/kpj/heal");

    /** Anything that could launch a real browser, talk to DevHIS, or run a project test. */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("com\\.microsoft\\.playwright"),
            Pattern.compile("Playwright\\s*\\.\\s*create"),
            Pattern.compile("\\.chromium\\s*\\("),
            Pattern.compile("\\.launch\\s*\\(\\s*new\\s+BrowserType"),
            Pattern.compile("sancyberhad", Pattern.CASE_INSENSITIVE),
            // (the XML-namespace URLs in a generated pom are identifiers, not addresses that are ever contacted)
            Pattern.compile("https?://(?!synthetic\\.invalid|maven\\.apache\\.org|www\\.w3\\.org)[a-z0-9.-]+\\.(?:com|my|net|org)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("-Dtest=AppointmentList"),
            Pattern.compile("AppointmentListTest\\s*\\.\\s*class"),
            Pattern.compile("new\\s+AppointmentListTest"),
            Pattern.compile("Appointment\\.AppointmentListTest\""),
            Pattern.compile("Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec"),
            Pattern.compile("new\\s+ProcessBuilder"),
            // a fully-qualified project test class: synthetic tests only ever name com.kpj.tests.Syn.*
            Pattern.compile("com\\.kpj\\.tests\\.(?!Syn[A-Za-z]*\\b)[A-Za-z_]")
    );

    private static List<Path> syntheticTestSources() throws IOException {
        try (Stream<Path> s = Files.list(HEAL_TESTS)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("SafetyGuardTest.java"))   // this file names the patterns it forbids
                    .sorted().collect(Collectors.toList());
        }
    }

    @Test
    void noSyntheticTestSource_canStartABrowser_reachDevHis_orRunAProjectTest() throws IOException {
        List<String> offences = new ArrayList<>();
        List<Path> sources = syntheticTestSources();
        assertTrue(sources.size() >= 10, "the scan found the synthetic test sources: " + sources.size());
        for (Path p : sources) {
            String src = Files.readString(p);
            for (Pattern f : FORBIDDEN) {
                // The deliberate exceptions: these two start the real Maven, only on their own temp project - see the dedicated checks below.
                String fileName = p.getFileName().toString();
                if ((fileName.equals("RealMavenOutDirTest.java") || fileName.equals("SurefireProfileTest.java") || fileName.equals("LiveTargetReviewTest.java"))
                        && f.pattern().equals("new\\s+ProcessBuilder")) continue;
                // PostRunBatchEntryTest runs the shipped .bat runners (copied into its own temp project) against a stand-in jar and a stub
                // mvn - see its dedicated, pinning check below. Only the process launch is excepted; every other pattern still applies.
                if (fileName.equals("PostRunBatchEntryTest.java") && f.pattern().equals("new\\s+ProcessBuilder")) continue;
                // LiveTargetReviewTest names ONE real class (ComplaintResolution) to read its source and to lay out a dummy mirror - see its dedicated check
                if (fileName.equals("LiveTargetReviewTest.java") && f.pattern().startsWith("com\\.kpj\\.tests\\.")) continue;
                // ... and quotes the host once, only to assert what DevHisBase's built-in default is (no connection is made - see the dedicated check)
                if (fileName.equals("LiveTargetReviewTest.java") && (f.pattern().equals("sancyberhad") || f.pattern().startsWith("https?://"))) continue;
                // SurefireProfileTest writes DUMMY classes laid out like the real ones (package com.kpj.tests) into its temp project
                if (fileName.equals("SurefireProfileTest.java") && f.pattern().startsWith("com\\.kpj\\.tests\\.")) continue;
                if (f.matcher(src).find()) offences.add(p.getFileName() + " matches /" + f.pattern() + "/");
            }
        }
        assertEquals(List.of(), offences, "forbidden references in synthetic tests");
    }

    @Test
    void theLiveTargetReview_readsOneRealClassAsText_andStartsMavenOnlyOnItsDummyMirrorProject() throws IOException {
        String src = Files.readString(HEAL_TESTS.resolve("LiveTargetReviewTest.java"));
        // the only project test class it names is ComplaintResolution, and only as a string for source reading / a dummy's package
        Matcher fq = Pattern.compile("com\\.kpj\\.tests\\.[A-Za-z_.]+").matcher(src);
        java.util.Set<String> names = new java.util.TreeSet<>();
        while (fq.find()) names.add(fq.group().replaceAll("\\.+$", ""));
        assertEquals(java.util.Set.of("com.kpj.tests.AncillaryServices_page.Equipment_page.ComplaintResolution",
                "com.kpj.tests.AncillaryServices_page.Equipment_page"), names);
        assertFalse(src.contains("Class.forName(TEST_FQCN") || src.contains("Class.forName(\"com.kpj.tests"), "the real test class is never loaded");
        assertFalse(src.contains("-Dtest=AppointmentList") || src.contains("AppointmentListTest.class"));
        // the DevHIS host appears exactly once, inside the assertion about DevHisBase's default - no URL is ever opened
        assertEquals(1, Pattern.compile("sancyberhad").matcher(src).results().count());
        assertTrue(src.contains("assertTrue(base.contains(\"cfg(\\\"devhis.url\\\", \\\"https://devhis.sancyberhad.com\\\")\"));"));
        assertFalse(src.contains("new URL(") || src.contains("openConnection") || src.contains("HttpClient"));
        // every Maven it starts is aimed at the dummy mirror's pom
        assertEquals(2, Pattern.compile("new\\s+ProcessBuilder\\(").matcher(src).results().count(), "mvn -v, and the mirror run");
        assertTrue(src.contains("mavenBase(\"-o\", \"-q\", \"-f\", mirror.resolve(\"pom.xml\").toString())"));
        assertTrue(src.contains("mavenBase(\"-o\", \"-f\", mirror.resolve(\"pom.xml\").toString(),"));
    }

    @Test
    void theSurefireProfileProof_alsoStartsMavenOnlyAgainstItsOwnTemporaryProject() throws IOException {
        String src = Files.readString(HEAL_TESTS.resolve("SurefireProfileTest.java"));
        assertEquals(1, Pattern.compile("new\\s+ProcessBuilder\\(").matcher(src).results().count(), "a single launch point");
        assertTrue(src.contains("cmd.add(\"-f\"); cmd.add(project.resolve(\"pom.xml\").toString());"), "every run (except mvn -v) names the temp pom");
        assertTrue(src.contains("project = shared.resolve(\"profile-probe\")"), "the project lives in the temp folder");
        assertFalse(src.contains("-Dtest=AppointmentList") || src.contains("src/main/java/com/kpj/pages"));
        assertTrue(src.contains("dummy(\"com/kpj/tests/DummyRealTest.java\"") && !src.contains("Playwright"), "the 'real-style' class is a dummy that only writes a marker file");
    }

    @Test
    void theOnlyTestThatStartsARealProcess_startsMavenAgainstItsOwnTemporaryProjectAndNothingElse() throws IOException {
        String src = Files.readString(HEAL_TESTS.resolve("RealMavenOutDirTest.java"));
        Matcher starts = Pattern.compile("new\\s+ProcessBuilder\\(([^)]*)\\)").matcher(src);
        List<String> args = new ArrayList<>();
        while (starts.find()) args.add(starts.group(1).trim());
        // exactly two launches: "mvn -v" (is Maven installed?) and the temp-project run, whose command always carries "-f <temp pom>"
        assertEquals(List.of("mavenBase(\"-v\")", "cmd"), args.stream().map(a -> a.startsWith("mavenBase(\"-v\"") ? "mavenBase(\"-v\")" : "cmd").collect(Collectors.toList()));
        assertTrue(src.contains("mavenBase(\"-o\", \"-q\", \"-f\", pom.toString())"), "every direct Maven run names the temp project's pom");
        assertTrue(src.contains("mavenBase(\"-o\", \"-f\", pom.toString(),"), "every orchestrated Maven run names the temp project's pom");
        assertFalse(src.contains("src/main/java/com/kpj/pages") || src.contains("-Dtest=AppointmentList"));
        // and it can only ever match its own probe class
        assertTrue(src.contains("PROBE_SIMPLE = \"OutDirProbeTest\""));
    }

    @Test
    void thePostRunBatchEntryTest_startsOnlyTheShippedRunnerScriptsInItsOwnTempProject_withNoRealJarAndNoRealMaven() throws IOException {
        String src = Files.readString(HEAL_TESTS.resolve("PostRunBatchEntryTest.java"));
        assertEquals(1, Pattern.compile("new\\s+ProcessBuilder\\(").matcher(src).results().count(), "a single launch point");
        assertTrue(src.contains("new ProcessBuilder(cmd).directory(share.toFile())"), "every launch runs inside the temp project's scripts folder");
        assertTrue(src.contains("List.of(\"cmd\", \"/c\", share.resolve(script).toString())"), "the only thing launched is a script copied into that temp folder");
        assertTrue(src.contains("Files.copy(stubJar, share.resolve(\"mcp-kpj-1.0.0-shaded.jar\"))"), "the 'jar' is the generated stand-in, never the real shaded jar");
        assertTrue(src.contains("e.put(\"PATH\", ") && src.contains("NO real Maven"), "the child's PATH holds the JDK, a stub mvn and the system folder - no real Maven");
        assertFalse(src.contains("realShare.resolve(\"mcp-kpj") || src.contains("target/mcp-kpj"), "the real shaded jars are never referenced");
        assertFalse(src.contains("-Dtest=AppointmentList") || src.contains("AppointmentListTest.class"), "AppointmentListTest is never named");
        assertFalse(src.contains("exec:java") && src.contains("mvn -q compile exec:java"), "no real Maven goal is ever run");
    }

    @Test
    void theOnlyProductionCodeThatStartsAProcessIsTheMavenLauncher_whichEverySyntheticTestReplaces() throws IOException {
        // Production process start-up lives in exactly one place; the synthetic tests replace it with a fake launcher, or point it
        // at the harmless FakeMavenChild (ProcessLaunchTest).
        List<String> starters = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Paths.get("src/main/java/com/kpj/heal"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String src = Files.readString(p);
                if (src.contains("new ProcessBuilder(") && !p.getFileName().toString().equals("GitStatus.java")) starters.add(p.getFileName().toString());
            }
        }
        assertEquals(List.of("SelfHealingAgent.java"), starters, "only the Maven launcher starts a process (GitStatus runs read-only git queries)");
    }

    @Test
    void noRealTestReport_noProjectOutputFile_and_noBrowserOrMavenChild_appeared_sinceThisJvmStarted() throws IOException {
        long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();

        // surefire reports of project tests (anything not in com.kpj.heal) would appear here if a real test had run in this JVM
        Path reports = Paths.get("target/surefire-reports");
        if (Files.isDirectory(reports)) {
            try (Stream<Path> s = Files.list(reports)) {
                List<String> recentProject = s.filter(p -> p.getFileName().toString().startsWith("com.kpj.tests.")
                                || p.getFileName().toString().toLowerCase(Locale.ROOT).contains("appointmentlist"))
                        .filter(p -> p.toFile().lastModified() >= jvmStart)
                        .map(p -> p.getFileName().toString()).collect(Collectors.toList());
                assertEquals(List.of(), recentProject, "a real project test report was written during this run");
            }
        }

        // the project's own output folders: nothing may have been written since this JVM started
        for (String dir : new String[]{"test-output", "test-output-demo"}) {
            Path d = Paths.get(dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d)) {
                List<String> recent = s.filter(Files::isRegularFile).filter(p -> p.toFile().lastModified() >= jvmStart)
                        .map(Path::toString).collect(Collectors.toList());
                assertEquals(List.of(), recent, dir + " was written to during this run");
            }
        }

        // no browser/Maven/Node child process is running under this JVM
        List<String> children = ProcessHandle.current().descendants()
                .map(h -> h.info().command().orElse("") + " " + String.join(" ", h.info().arguments().orElse(new String[0])))
                .filter(c -> { String l = c.toLowerCase(Locale.ROOT); return l.contains("chrom") || l.contains("playwright") || l.contains("msedge") || l.contains("mvn") || l.contains("node"); })
                .collect(Collectors.toList());
        assertEquals(List.of(), children, "no real browser or Maven process is running");
    }
}
