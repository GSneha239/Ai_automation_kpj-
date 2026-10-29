package com.kpj.heal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * REAL Maven, REAL Surefire - but ONLY against a throw-away project built in a temp folder. The project's single harmless test writes a
 * uniquely identifiable result file (and a report of the environment/system properties its forked JVM saw) and nothing else: no
 * DevHIS, no browser, no {@code com.kpj.tests.*} class, no {@code AppointmentListTest}, and nothing from the production project is
 * compiled or run (every Maven launch names the temp project with {@code -f}, and a test below proves the probe class does not exist
 * in the production tree). Maven runs offline against the local repository.
 *
 * <p>Verifies how {@code -Ddevhis.outdir}, {@code DEVHIS_OUTDIR} and {@code JAVA_TOOL_OPTIONS} interact in the forked test JVM, where
 * the result lands, that it is fresh, what Maven's exit code is for a pass, a deliberate test failure, missing output and an
 * output-directory mismatch, and that {@link HealingWorkflowOrchestrator} refuses stale results and never reads an unsuccessful run
 * as a pass.</p>
 *
 * <p>This is real-Maven verification of the output-folder plumbing on a temporary project. It is NOT verification against DevHIS.</p>
 */
class RealMavenOutDirTest {

    @TempDir
    static Path shared;

    private static Path project;
    private static Path pom;
    private static boolean mavenAvailable;
    private static String productionOutputsBefore;

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String PROBE_ID = "PROBE_ID";
    private static final String PROBE_FQCN = "probe.OutDirProbeTest";
    private static final String PROBE_SIMPLE = "OutDirProbeTest";

    // ------------------------------------------------------------------ the temporary Maven project

    @BeforeAll
    static void createProjectAndCheckMaven() throws Exception {
        productionOutputsBefore = productionOutputFingerprint();
        project = shared.resolve("probe-project");
        pom = project.resolve("pom.xml");
        Path tests = project.resolve("src/test/java/probe");
        Files.createDirectories(tests);
        // same Surefire settings as the production project: failures are ignored (exit 0), one forked JVM
        Files.writeString(pom, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>probe</groupId><artifactId>outdir-probe</artifactId><version>1.0</version>\n"
                + "  <properties><maven.compiler.source>21</maven.compiler.source><maven.compiler.target>21</maven.compiler.target>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>\n"
                + "  <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>\n"
                + "    <version>5.10.2</version><scope>test</scope></dependency></dependencies>\n"
                + "  <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>\n"
                + "    <version>3.2.5</version><configuration><testFailureIgnore>true</testFailureIgnore><forkCount>1</forkCount>\n"
                + "    <reuseForks>true</reuseForks></configuration></plugin></plugins></build>\n</project>\n");
        Files.writeString(tests.resolve("ProbeBase.java"), "package probe;\n"
                + "import java.nio.file.*;\n"
                + "public abstract class ProbeBase {\n"
                + "    protected final String id;\n"
                + "    protected ProbeBase(String id) { this.id = id; }\n"
                + "    /** The same resolution order DevHisBase.cfg uses: -Dkey, then KEY_LIKE_THIS, then the default. */\n"
                + "    static String cfg(String key, String def) {\n"
                + "        String v = System.getProperty(key); if (v != null && !v.isBlank()) return v.trim();\n"
                + "        v = System.getenv(key.replace('.', '_').toUpperCase()); if (v != null && !v.isBlank()) return v.trim();\n"
                + "        return def;\n"
                + "    }\n"
                + "}\n");
        Files.writeString(tests.resolve("OutDirProbeTest.java"), "package probe;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import java.nio.file.*;\n"
                + "public class OutDirProbeTest extends ProbeBase {\n"
                + "    public OutDirProbeTest() { super(\"" + PROBE_ID + "\"); }\n"
                + "    @Test void probe() throws Exception {\n"
                + "        String mode = System.getProperty(\"probe.mode\", \"pass\");      // pass | fail | nothing\n"
                + "        String token = System.getProperty(\"probe.token\", \"none\");\n"
                + "        Path out = Paths.get(cfg(\"devhis.outdir\", \"test-output\"));\n"
                + "        Files.createDirectories(Paths.get(\"target\"));\n"
                + "        Files.writeString(Paths.get(\"target/probe-env.txt\"),\n"
                + "            \"env.DEVHIS_OUTDIR=\" + System.getenv(\"DEVHIS_OUTDIR\") + \"\\n\"\n"
                + "          + \"env.JAVA_TOOL_OPTIONS=\" + System.getenv(\"JAVA_TOOL_OPTIONS\") + \"\\n\"\n"
                + "          + \"prop.devhis.outdir=\" + System.getProperty(\"devhis.outdir\") + \"\\n\"\n"
                + "          + \"prop.probe.mode=\" + mode + \"\\n\"\n"
                + "          + \"prop.probe.token=\" + token + \"\\n\"\n"
                + "          + \"resolved=\" + out.toAbsolutePath() + \"\\n\"\n"
                + "          + \"user.dir=\" + System.getProperty(\"user.dir\") + \"\\n\");\n"
                + "        if (mode.equals(\"nothing\")) return;                              // a run that writes no result at all\n"
                + "        Files.createDirectories(out.resolve(\"aggregate-data\"));\n"
                + "        boolean fail = mode.equals(\"fail\");\n"
                + "        Files.writeString(out.resolve(\"aggregate-data\").resolve(\"PROBE.tsv\"),\n"
                + "            \"PASS\\tProbe page\\t\" + id + \"\\tLogin\\tok\\ttoken=\" + token + \"\\t\\n\"\n"
                + "          + (fail ? \"FAIL\" : \"PASS\") + \"\\tProbe page\\t\" + id + \"\\tProbe step\\tok\\ttoken=\" + token + \"\\t\\n\");\n"
                + "        if (fail) org.junit.jupiter.api.Assertions.fail(\"deliberate failure\");\n"
                + "    }\n"
                + "}\n");
        mavenAvailable = mavenWorks();
    }

    @AfterAll
    static void productionOutputsAreUntouched() throws IOException {
        assertEquals(productionOutputsBefore, productionOutputFingerprint(), "the production project's test-output folders were not touched");
    }

    private static boolean mavenWorks() {
        try {
            Process p = new ProcessBuilder(mavenBase("-v")).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private static List<String> mavenBase(String... args) {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.add("mvn");
        for (String a : args) cmd.add(a);
        return cmd;
    }

    private static String productionOutputFingerprint() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String root : new String[]{"test-output", "test-output-demo"}) {
            Path r = Paths.get(root);
            if (!Files.isDirectory(r)) continue;
            try (Stream<Path> s = Files.walk(r)) {
                for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    sb.append(p).append('@').append(Files.getLastModifiedTime(p).toMillis()).append('#').append(Files.size(p)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    @BeforeEach
    void requireMaven() throws IOException {
        assumeTrue(mavenAvailable, "mvn is not available on this machine");
        Files.deleteIfExists(project.resolve("target/probe-env.txt"));
    }

    @AfterEach
    void clearOverrides() {
        SelfHealingAgent.mavenExecutableOverride = null;
        CodeLocator.testRootOverride = null;
        ImpactAnalyzer.testRootOverride = null;
    }

    // ------------------------------------------------------------------ real Maven, started directly (full control of the environment)

    private static final class Outcome {
        final int exit; final Instant started; final String log;
        Outcome(int exit, Instant started, String log) { this.exit = exit; this.started = started; this.log = log; }
    }

    /** Runs the real {@code mvn -o -q -f <temp pom> ...} with the project as working directory and EXACTLY the given environment extras. */
    private Outcome mvn(Map<String, String> env, String... args) throws Exception {
        List<String> cmd = mavenBase("-o", "-q", "-f", pom.toString());
        for (String a : args) cmd.add(a);
        Path log = shared.resolve("mvn-" + UUID.randomUUID() + ".log");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(project.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        pb.environment().remove("DEVHIS_OUTDIR");
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().putAll(env);
        Instant started = Instant.now();
        Process p = pb.start();
        if (!p.waitFor(240, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("Maven did not finish within 240 s"); }
        return new Outcome(p.exitValue(), started, Files.readString(log));
    }

    private Map<String, String> probeReport() throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(project.resolve("target/probe-env.txt"))) {
            int i = line.indexOf('=');
            m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    private static String token() { return "T-" + UUID.randomUUID(); }

    private static Path result(Path outDir) { return outDir.resolve("aggregate-data").resolve("PROBE.tsv"); }

    private static void assertSamePath(Path expected, String actual) {
        assertEquals(expected.toAbsolutePath().normalize(), Paths.get(actual).toAbsolutePath().normalize());
    }

    private void assertFreshResultWithToken(Path outDir, Outcome o, String token) throws IOException {
        assertTrue(Files.isRegularFile(result(outDir)), "result file exists at " + result(outDir));
        assertTrue(Files.readString(result(outDir)).contains("token=" + token), "it is THIS run's result (unique token)");
        assertTrue(Files.getLastModifiedTime(result(outDir)).toMillis() >= o.started.toEpochMilli() - 2000, "and it was written after the run started");
    }

    @Test
    void theProbeProjectIsTemporary_andTheProductionProjectHasNoSuchTest() throws IOException {
        assertTrue(pom.toAbsolutePath().startsWith(shared.toAbsolutePath()));
        try (Stream<Path> s = Files.walk(Paths.get("src"))) {
            assertEquals(List.of(), s.filter(p -> p.getFileName().toString().equals("OutDirProbeTest.java") || p.getFileName().toString().equals("ProbeBase.java"))
                    .collect(Collectors.toList()), "nothing in the production tree could be matched by -Dtest=" + PROBE_SIMPLE);
        }
    }

    @Test
    void theProbesResolutionOrder_isTheOneDevHisBaseUses() throws IOException {
        // The probe cannot run the production base class (that would pull in a browser), so pin its replica to the real source text:
        // -Dkey, then KEY_LIKE_THIS from the environment, then config.properties, then the built-in default "test-output".
        String src = Files.readString(Paths.get("src/main/java/com/kpj/core/DevHisBase.java"));
        int prop = src.indexOf("System.getProperty(key)");
        int env = src.indexOf("System.getenv(key.replace('.', '_').toUpperCase())");
        int file = src.indexOf("FILE_PROPS.getProperty(key)");
        int def = src.indexOf("return def;");
        assertTrue(prop > 0 && prop < env && env < file && file < def, "resolution order in DevHisBase.cfg: " + prop + "," + env + "," + file + "," + def);
        assertTrue(src.contains("cfg(\"devhis.outdir\", \"test-output\")"), "the output folder key and default");
    }

    // ================================================================== how the output folder is resolved

    @Test
    void systemPropertyOnTheMavenCommandLine_reachesTheForkedJvm_andDecidesTheOutputFolder() throws Exception {
        Path outA = shared.resolve("out-prop");
        String token = token();
        Outcome o = mvn(Map.of(), "-Ddevhis.outdir=" + outA, "-Dprobe.token=" + token, "test");
        assertEquals(0, o.exit, o.log);
        assertFreshResultWithToken(outA, o, token);
        Map<String, String> r = probeReport();
        assertSamePath(outA, r.get("prop.devhis.outdir"));         // the -D reached the FORKED JVM as a system property
        assertEquals(token, r.get("prop.probe.token"));             // ... as does any other -D
        assertEquals("null", r.get("env.DEVHIS_OUTDIR"));
        assertSamePath(outA, r.get("resolved"));
        assertSamePath(project, r.get("user.dir"));                 // the fork runs in the temp project, not where Maven was started
    }

    @Test
    void theEnvironmentVariableAlone_decidesTheOutputFolder() throws Exception {
        Path outB = shared.resolve("out-env");
        String token = token();
        Outcome o = mvn(Map.of("DEVHIS_OUTDIR", outB.toString()), "-Dprobe.token=" + token, "test");
        assertEquals(0, o.exit, o.log);
        assertFreshResultWithToken(outB, o, token);
        Map<String, String> r = probeReport();
        assertSamePath(outB, r.get("env.DEVHIS_OUTDIR"));           // the environment reached the forked JVM
        assertEquals("null", r.get("prop.devhis.outdir"));
        assertSamePath(outB, r.get("resolved"));
    }

    @Test
    void theCommandLineProperty_beatsTheEnvironmentVariable() throws Exception {
        Path viaProp = shared.resolve("out-wins-prop"), viaEnv = shared.resolve("out-loses-env");
        String token = token();
        Outcome o = mvn(Map.of("DEVHIS_OUTDIR", viaEnv.toString()), "-Ddevhis.outdir=" + viaProp, "-Dprobe.token=" + token, "test");
        assertEquals(0, o.exit, o.log);
        assertFreshResultWithToken(viaProp, o, token);
        assertFalse(Files.exists(viaEnv), "the environment variable was overridden, nothing went there");
        assertSamePath(viaEnv, probeReport().get("env.DEVHIS_OUTDIR"));   // it was visible - just not used
    }

    @Test
    void javaToolOptions_isAJvmPropertyForEveryJvm_soItBeatsTheEnvironmentVariable_butNotTheMavenCommandLine() throws Exception {
        Path viaTool = shared.resolve("out-tool"), viaEnv = shared.resolve("out-env2"), viaCli = shared.resolve("out-cli");
        String t1 = token();
        Outcome o1 = mvn(Map.of("JAVA_TOOL_OPTIONS", "-Ddevhis.outdir=" + viaTool, "DEVHIS_OUTDIR", viaEnv.toString()), "-Dprobe.token=" + t1, "test");
        assertEquals(0, o1.exit, o1.log);
        assertFreshResultWithToken(viaTool, o1, t1);
        assertFalse(Files.exists(viaEnv), "JAVA_TOOL_OPTIONS' -Ddevhis.outdir outranks DEVHIS_OUTDIR");

        String t2 = token();
        Outcome o2 = mvn(Map.of("JAVA_TOOL_OPTIONS", "-Ddevhis.outdir=" + viaTool), "-Ddevhis.outdir=" + viaCli, "-Dprobe.token=" + t2, "test");
        assertEquals(0, o2.exit, o2.log);
        assertFreshResultWithToken(viaCli, o2, t2);
        assertEquals(0, Files.readString(result(viaTool)).split("token=" + t2, -1).length - 1, "the earlier run's file was not overwritten");
    }

    @Test
    void withNoSetting_theOutputFolderIsRelativeToTheForkedJvmsDirectory_theTempProject_notTheProductionProject() throws Exception {
        Path defaultDir = project.resolve("test-output");
        String token = token();
        Outcome o = mvn(Map.of(), "-Dprobe.token=" + token, "test");
        assertEquals(0, o.exit, o.log);
        assertFreshResultWithToken(defaultDir, o, token);
        assertSamePath(defaultDir, probeReport().get("resolved"));
    }

    // ================================================================== outcomes: pass, deliberate failure, missing output, no such test

    @Test
    void aPassingRun_exitsZero_andWritesAFreshResult() throws Exception {
        Path out = shared.resolve("out-pass");
        String token = token();
        Outcome o = mvn(Map.of(), "-Ddevhis.outdir=" + out, "-Dprobe.token=" + token, "-Dprobe.mode=pass", "test");
        assertEquals(0, o.exit, o.log);
        assertFreshResultWithToken(out, o, token);
        assertFalse(Files.readString(result(out)).contains("FAIL\t"), "no failing step");
    }

    @Test
    void aDeliberateTestFailure_stillExitsZero_becauseSurefireIgnoresFailures_soTheResultFileIsTheOnlyTruth() throws Exception {
        Path out = shared.resolve("out-fail");
        String token = token();
        Outcome o = mvn(Map.of(), "-Ddevhis.outdir=" + out, "-Dprobe.token=" + token, "-Dprobe.mode=fail", "test");
        assertEquals(0, o.exit, "testFailureIgnore=true: a failing test does not fail the build\n" + o.log);
        assertFreshResultWithToken(out, o, token);
        assertTrue(Files.readString(result(out)).contains("FAIL\tProbe page\t" + PROBE_ID + "\tProbe step"), "the failing step is recorded");
        String report = Files.readString(project.resolve("target/surefire-reports/probe.OutDirProbeTest.txt"));
        assertTrue(report.contains("FAILURE") && report.contains("deliberate failure"), "Surefire saw the failure too: " + report);
        // the agents' own reader sees exactly this failure
        List<FailureRecord> failures = new FailureScanner(out.toString()).scan(PROBE_ID);
        assertEquals(1, failures.size());
        assertEquals("Probe step", failures.get(0).step);
    }

    @Test
    void aRunThatWritesNoResult_exitsZero_butLeavesNoFreshEvidence() throws Exception {
        Path out = shared.resolve("out-nothing");
        Files.createDirectories(out.resolve("aggregate-data"));
        Files.writeString(result(out), "FAIL\tProbe page\t" + PROBE_ID + "\tProbe step\tok\ttoken=OLD\t\n");   // an old run's file
        Files.setLastModifiedTime(result(out), FileTime.from(Instant.now().minusSeconds(3600)));
        Outcome o = mvn(Map.of(), "-Ddevhis.outdir=" + out, "-Dprobe.mode=nothing", "test");
        assertEquals(0, o.exit, "Maven cannot tell that nothing was written\n" + o.log);
        assertFalse(new FailureScanner(out.toString()).evidenceSince(PROBE_ID, o.started).freshOutputFound, "the agents' freshness check can");
        assertTrue(Files.readString(result(out)).contains("token=OLD"), "the old file is exactly as it was");
    }

    @Test
    void aTestNameThatMatchesNothing_isANonZeroExit() throws Exception {
        Outcome o = mvn(Map.of(), "-Dtest=NoSuchProbeTest", "test");
        assertNotEquals(0, o.exit, "Maven must report that no test ran\n" + o.log);
    }

    // ================================================================== selecting a test by fully qualified class name (real Surefire)

    private void writeDuplicateNamedClass(String pkg, String marker) throws IOException {
        Path dir = project.resolve("src/test/java/probe/" + pkg);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Transfer.java"), "package probe." + pkg + ";\n"
                + "import org.junit.jupiter.api.Test;\nimport java.nio.file.*;\n"
                + "public class Transfer {\n    @Test void ran() throws Exception {\n"
                + "        Files.createDirectories(Paths.get(\"target\"));\n"
                + "        Files.writeString(Paths.get(\"target/" + marker + "\"), \"ran\");\n    }\n}\n");
    }

    @Test
    void aFullyQualifiedClassName_selectsExactlyOneOfTwoClassesThatShareASimpleName() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        writeDuplicateNamedClass("dupa", "dup-a.txt");
        writeDuplicateNamedClass("dupb", "dup-b.txt");
        try {
            Files.deleteIfExists(project.resolve("target/dup-a.txt"));
            Files.deleteIfExists(project.resolve("target/dup-b.txt"));
            // what the agents now send: the fully qualified name selects exactly that class
            Outcome exact = mvn(Map.of(), "-Dtest=" + SelfHealingAgent.testSelector("probe.dupb.Transfer"), "test");
            assertEquals(0, exact.exit, exact.log);
            assertTrue(Files.exists(project.resolve("target/dup-b.txt")), "the named class ran\n" + exact.log);
            assertFalse(Files.exists(project.resolve("target/dup-a.txt")), "the same-named class in another package did NOT run\n" + exact.log);
            // control: what the simple name used to do - it selects BOTH, which is exactly the ambiguity this change removes
            Files.deleteIfExists(project.resolve("target/dup-b.txt"));
            Outcome simple = mvn(Map.of(), "-Dtest=Transfer", "test");
            assertEquals(0, simple.exit, simple.log);
            assertTrue(Files.exists(project.resolve("target/dup-a.txt")) && Files.exists(project.resolve("target/dup-b.txt")),
                    "the simple name matches both classes\n" + simple.log);
        } finally {
            Files.deleteIfExists(project.resolve("src/test/java/probe/dupa/Transfer.java"));
            Files.deleteIfExists(project.resolve("src/test/java/probe/dupb/Transfer.java"));
        }
    }

    // ================================================================== the orchestrator driving the REAL Maven on the temp project

    /** Points the agent's real launcher at the real Maven + the temp project; {@code -D}s ride on the command line like a user would. */
    private void aimRealMavenAtTheProbeProject(Path outDirForTests, String token, String mode) {
        List<String> prefix = mavenBase("-o", "-f", pom.toString(), "-Ddevhis.outdir=" + outDirForTests, "-Dprobe.token=" + token, "-Dprobe.mode=" + mode);
        SelfHealingAgent.mavenExecutableOverride = prefix;
        CodeLocator.testRootOverride = project.resolve("src/test/java");
        Path none = shared.resolve("no-dependents");
        try { Files.createDirectories(none); } catch (IOException e) { throw new RuntimeException(e); }
        ImpactAnalyzer.testRootOverride = none;
    }

    private String orchestrate(Path orchestratorOut, String fqcn) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        SelfHealingAgent.MavenLauncher saved = SelfHealingAgent.mavenLauncher;
        SelfHealingAgent.mavenLauncher = SelfHealingAgent::launchMaven;   // the REAL launcher
        try { HealingWorkflowOrchestrator.main(new String[]{fqcn, orchestratorOut.toString()}); }
        finally { System.setOut(old); SelfHealingAgent.mavenLauncher = saved; }
        String text = buf.toString(StandardCharsets.UTF_8);
        old.print(text);
        return text;
    }

    private void seedStale(Path outDir, String status) throws IOException {
        Files.createDirectories(outDir.resolve("aggregate-data"));
        Files.writeString(result(outDir), status + "\tProbe page\t" + PROBE_ID + "\tProbe step\tok\ttoken=STALE\t\n");
        Files.setLastModifiedTime(result(outDir), FileTime.from(Instant.now().minusSeconds(3600)));
    }

    @Test
    void orchestrator_realMaven_passingRun_isNoFailures_andTheResultIsThisRunsNotTheStaleOne() throws Exception {
        Path out = shared.resolve("orch-pass");
        seedStale(out, "FAIL");   // an old failing result is sitting there
        String token = token();
        aimRealMavenAtTheProbeProject(out, token, "pass");
        String log = orchestrate(out, PROBE_FQCN);
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FAILURES"), log);
        assertTrue(Files.readString(result(out)).contains("token=" + token) && !Files.readString(result(out)).contains("STALE"));
        assertSamePath(project, probeReport().get("user.dir"));
    }

    @Test
    void orchestrator_realMaven_deliberateFailure_isDetectedFromTheResultFile_andFlowsIntoFailureAnalysis() throws Exception {
        Path out = shared.resolve("orch-fail");
        String token = token();
        aimRealMavenAtTheProbeProject(out, token, "fail");
        String log = orchestrate(out, PROBE_FQCN);
        assertTrue(log.contains("[2/4] FAILURE DETECTION — 1 failed step(s)"), log);
        assertTrue(log.contains("[3/4] FAILURE ANALYSIS"), log);
        assertTrue(Files.isDirectory(out.resolve("failure-analysis").resolve("reports")), "the Failure Analysis report was written");
        assertFalse(log.contains("NO_FAILURES") || log.contains("TEST_RUN_FAILED"), log);
        assertTrue(log.contains("FAILURE_ANALYSIS_ONLY"), "no locator exists for the probe step, so nothing is proposed: " + log);
    }

    @Test
    void orchestrator_realMaven_aRunThatWritesNothing_isRefused_andTheStaleFailureIsNotAnalyzed() throws Exception {
        Path out = shared.resolve("orch-nothing");
        seedStale(out, "FAIL");
        aimRealMavenAtTheProbeProject(out, token(), "nothing");
        String log = orchestrate(out, PROBE_FQCN);
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FRESH_RESULTS"), log);
        assertFalse(Files.exists(out.resolve("failure-analysis")), "the stale failing result was not analyzed");
    }

    @Test
    void orchestrator_realMaven_anOutputFolderMismatch_isRefused_andNeitherFolderIsMisread() throws Exception {
        Path orchestratorOut = shared.resolve("orch-mismatch-agent"), testsWriteHere = shared.resolve("orch-mismatch-tests");
        seedStale(orchestratorOut, "PASS");   // an old PASSING result in the folder the agent reads
        String token = token();
        aimRealMavenAtTheProbeProject(testsWriteHere, token, "pass");   // ... while the tests write somewhere else
        String log = orchestrate(orchestratorOut, PROBE_FQCN);
        assertTrue(log.contains("WORKFLOW_COMPLETE: NO_FRESH_RESULTS"), log);
        assertFalse(log.contains("NO_FAILURES"), "the old PASS must not be taken for this run's result: " + log);
        assertTrue(Files.readString(result(testsWriteHere)).contains("token=" + token), "the run did write - just not where the agent looks");
        assertTrue(Files.readString(result(orchestratorOut)).contains("STALE"), "the agent's folder is exactly as it was");
    }

    @Test
    void orchestrator_realMaven_anUnsuccessfulRun_isNeverReadAsAPass() throws Exception {
        // The test class named does not exist in the Maven project: Maven exits non-zero. An old PASSING result sits in the output folder.
        Path ghostRoot = shared.resolve("ghost-root");
        Path ghost = ghostRoot.resolve("probe/GhostProbeTest.java");
        Files.createDirectories(ghost.getParent());
        Files.writeString(ghost, "package probe;\npublic class GhostProbeTest extends ProbeBase { public GhostProbeTest() { super(\"" + PROBE_ID + "\"); } }\n");
        Path out = shared.resolve("orch-ghost");
        seedStale(out, "PASS");
        aimRealMavenAtTheProbeProject(out, token(), "pass");
        CodeLocator.testRootOverride = ghostRoot;
        String log = orchestrate(out, "probe.GhostProbeTest");
        assertTrue(log.contains("WORKFLOW_COMPLETE: TEST_RUN_FAILED") && log.contains("Maven exited with code"), log);
        assertFalse(log.contains("NO_FAILURES"), "a failed run must never look like a pass: " + log);
        assertFalse(Files.exists(out.resolve("failure-analysis")));
    }

    @Test
    void orchestrator_realMaven_aBuildThatCannotStart_isNeverReadAsAPass() throws Exception {
        Path out = shared.resolve("orch-badpom");
        seedStale(out, "PASS");
        aimRealMavenAtTheProbeProject(out, token(), "pass");
        SelfHealingAgent.mavenExecutableOverride = mavenBase("-o", "-f", shared.resolve("no-such-project/pom.xml").toString());
        String log = orchestrate(out, PROBE_FQCN);
        assertTrue(log.contains("WORKFLOW_COMPLETE: TEST_RUN_FAILED"), log);
        assertFalse(log.contains("NO_FAILURES"), log);
    }

}
