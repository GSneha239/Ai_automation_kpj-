package com.kpj.heal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.kpj.heal.PostRunTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SYNTHETIC end-to-end tests of the real batch entry points. The ACTUAL edited scripts ({@code module-runner.bat}, {@code core-flows-runner.bat},
 * {@code run.bat}, {@code postrun-hook.bat} and the thin wrappers) are copied into a throw-away "project" and executed with:
 * <ul>
 *   <li>a stand-in "shaded jar" (built here from a few lines of stub code) that writes synthetic result files (or none) and exits with a
 *       scripted code, so no browser, no DevHIS and no real test ever starts. Its manifest also exposes the project's compiled classes,
 *       so the runners' own {@code ReportAggregator} step is the REAL aggregator;</li>
 *   <li>a stub {@code mvn.cmd} that records the launch and then starts the REAL {@link PostRunCoordinator} in a child JVM, with the working
 *       directory the hook chose (the temp project root) - so the batch-hook -> coordinator -> agents chain is exercised for real.</li>
 * </ul>
 * Windows only (the runners are .bat); on another OS these tests are SKIPPED, never counted as passed.
 */
class PostRunBatchEntryTest {

    @TempDir
    Path tmp;

    private static String outputsBefore;
    private static Path realShare;
    private static Path stubDir;
    private static Path stubJar;

    private Path proj, share, bin, stubLog, mvnLog, javaCounter, srcRoot;

    /** What the stand-in jar's {@code main} does - driven by environment variables so each test scripts its own "run". */
    private static final String STUB_MAIN = String.join("\n",
            "import java.nio.file.*;",
            "public class StubJarMain {",
            "  public static void main(String[] a) throws Exception {",
            "    String out = System.getProperty(\"devhis.outdir\");",
            "    Files.writeString(Paths.get(System.getenv(\"STUB_COUNT\")), \"jar\\n\", StandardOpenOption.CREATE, StandardOpenOption.APPEND);",
            "    String mode = System.getenv(\"STUB_MODE\");",
            "    if (out != null && !\"none\".equalsIgnoreCase(mode)) {",
            "      Files.createDirectories(Paths.get(out, \"aggregate-data\"));",
            "      boolean fail = \"fail\".equalsIgnoreCase(mode);",
            "      Files.copy(Paths.get(System.getenv(fail ? \"STUB_TSV_FAIL\" : \"STUB_TSV_PASS\")), Paths.get(out, \"aggregate-data\", \"Syn.tsv\"), StandardCopyOption.REPLACE_EXISTING);",
            "      String sc = System.getenv(\"STUB_SIDECAR\");",
            "      if (fail && sc != null && !sc.isBlank()) {",
            "        Files.createDirectories(Paths.get(out, \"failures\"));",
            "        Files.copy(Paths.get(sc), Paths.get(out, \"failures\", \"SYN_FLOW_1.txt\"), StandardCopyOption.REPLACE_EXISTING);",
            "      }",
            "    }",
            "    String ex = System.getenv(\"STUB_JVM_EXIT\");",
            "    System.exit(ex == null || ex.isBlank() ? 0 : Integer.parseInt(ex));",
            "  }",
            "}");

    @BeforeAll
    static void before() throws Exception {
        outputsBefore = outputsContentFingerprint();
        realShare = Paths.get("devhis-tests-share").toAbsolutePath();
        if (!WINDOWS) return;
        // Build the stand-in jar once: a stub Main-Class and a
        // Class-Path to the project's compiled classes so the runners' ReportAggregator step is the real one.
        stubDir = Files.createTempDirectory("postrun-stubjar");
        Path src = stubDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("StubJarMain.java"), STUB_MAIN);
        Path classes = stubDir.resolve("classes");
        Files.createDirectories(classes);
        javax.tools.JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();
        assumeTrue(jc != null, "no system Java compiler");
        int rc = jc.run(null, null, null, "-d", classes.toString(), src.resolve("StubJarMain.java").toString());
        assertEquals(0, rc, "the stub jar sources compiled");
        java.util.jar.Manifest mf = new java.util.jar.Manifest();
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "StubJarMain");
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.CLASS_PATH, Paths.get("target/classes").toAbsolutePath().toUri().toString());
        stubJar = stubDir.resolve("stub-shaded.jar");
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(Files.newOutputStream(stubJar), mf)) {
            for (String cls : new String[]{"StubJarMain.class"}) {
                jos.putNextEntry(new java.util.jar.JarEntry(cls));
                jos.write(Files.readAllBytes(classes.resolve(cls)));
                jos.closeEntry();
            }
        }
    }

    @AfterAll
    static void realOutputsUntouched() throws IOException {
        assertEquals(outputsBefore, outputsContentFingerprint(), "every real test-output* folder is byte-identical to before");
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(WINDOWS, "the runners are .bat files - skipped on this OS");
        proj = tmp.resolve("proj");
        share = proj.resolve("devhis-tests-share");
        bin = tmp.resolve("bin");
        stubLog = tmp.resolve("java-stub.log");
        mvnLog = tmp.resolve("mvn-stub.log");
        javaCounter = tmp.resolve("java-jar-count.txt");
        srcRoot = proj.resolve("src/test/java");
        Files.createDirectories(share);
        Files.createDirectories(bin);
        Files.writeString(proj.resolve("pom.xml"), "<project/>");
        writeAllFlows(srcRoot);
        for (String f : new String[]{"module-runner.bat", "core-flows-runner.bat", "run.bat", "postrun-hook.bat", "run-ip-devhis.bat", "run-klg.bat",
                "run-core-flows-devhis.bat", "run-emergency-dsh.bat"}) {
            Files.copy(realShare.resolve(f), share.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(stubJar, share.resolve("mcp-kpj-1.0.0-shaded.jar"));
        writeStubs();
    }

    // ------------------------------------------------------------------ stubs

    private void writeStubs() throws IOException {
        // "mvn": record the call, then run the REAL coordinator exactly as `exec:java -Dexec.args=--batch` would (same env, same working directory).
        Files.writeString(bin.resolve("mvn.cmd"), String.join("\r\n",
                "@echo off",
                ">> \"%STUB_MVN_LOG%\" echo mvn-call ENTRY=%DEVHIS_POSTRUN_ENTRY% CWD=%CD% ARGS=%*",
                "\"%REAL_JAVA%\" -cp \"%COORD_CP%\" com.kpj.heal.PostRunCoordinator --batch",
                "exit /b %ERRORLEVEL%",
                ""), StandardCharsets.US_ASCII);
        // synthetic result files the stub "jar" writes
        Files.writeString(tmp.resolve("fail.tsv"), row("PASS", "SYN_FLOW", "Login", "Authenticated", "Logged in")
                + row("FAIL", "SYN_FLOW", STEP, "element found", ELIGIBLE_ACTUAL));
        Files.writeString(tmp.resolve("pass.tsv"), row("PASS", "SYN_FLOW", "Login", "Authenticated", "Logged in")
                + row("PASS", "SYN_FLOW", STEP, "element found", "element found"));
        Files.writeString(tmp.resolve("sidecar.txt"), "testId=SYN_FLOW\npage=Synthetic page\nstep=" + STEP + "\ndesc=d\nexpected=element found\nactual=" + ELIGIBLE_ACTUAL
                + "\nurl=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff\n");
    }

    // ------------------------------------------------------------------ running a real script

    private static final class Outcome {
        int exit;
        String output;
    }

    private Outcome runScript(String script, Map<String, String> env, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("cmd", "/c", share.resolve(script).toString()));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(share.toFile()).redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("NUL")));   // the runners end with `pause`
        Map<String, String> e = pb.environment();
        for (String k : new ArrayList<>(e.keySet())) {
            String u = k.toUpperCase();
            if (u.startsWith("DEVHIS_") || u.startsWith("LLM_") || u.startsWith("STUB_")) e.remove(k);
        }
        String java = Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString();
        e.put("PATH", Paths.get(System.getProperty("java.home"), "bin") + ";" + bin + ";" + System.getenv("SystemRoot") + "\\System32");   // real java (runs the stub jar); NO real Maven
        e.put("STUB_COUNT", javaCounter.toString());
        e.put("STUB_MVN_LOG", mvnLog.toString());
        e.put("REAL_JAVA", java);
        e.put("COORD_CP", Paths.get("target/classes").toAbsolutePath().toString());
        e.put("STUB_MODE", "fail");
        e.put("STUB_JVM_EXIT", "0");
        e.put("STUB_TSV_FAIL", tmp.resolve("fail.tsv").toString());
        e.put("STUB_TSV_PASS", tmp.resolve("pass.tsv").toString());
        e.put("STUB_SIDECAR", tmp.resolve("sidecar.txt").toString());
        e.putAll(env);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "the script finished: " + output);
        Outcome o = new Outcome();
        o.exit = p.exitValue();
        o.output = output;
        System.out.print(output);
        return o;
    }

    private int mvnCalls() throws IOException {
        return Files.isRegularFile(mvnLog) ? (int) Files.readAllLines(mvnLog).stream().filter(l -> l.startsWith("mvn-call")).count() : 0;
    }

    private int jarCalls() throws IOException {
        return Files.isRegularFile(javaCounter) ? Files.readAllLines(javaCounter).size() : 0;
    }

    private Path out(String name) { return share.resolve(name); }

    private List<Path> pending(String outName) throws IOException {
        return files(out(outName).resolve("self-healing").resolve("pending")).stream().filter(p -> p.toString().endsWith(".properties")).collect(Collectors.toList());
    }

    /** The exit code of the same runner with the hook line removed from a temp copy - what the runner returned before this integration. */
    private int baselineExit(String script, String... args) throws Exception {
        String name = "nohook-" + script;
        List<String> kept = Files.readAllLines(share.resolve(script)).stream().filter(l -> !l.contains("postrun-hook.bat")).collect(Collectors.toList());
        Files.write(share.resolve(name), kept);
        return runScript(name, Map.of(), args).exit;
    }

    /**
     * No credential appears in anything the POST-RUN step prints or writes. (The runners themselves - run.bat's "Target" line - echo
     * DEVHIS_OPTS; that is pre-existing behaviour outside this integration, so only the output from the hook's banner on is inspected.)
     */
    private static final java.util.regex.Pattern PASSWORD_SHAPE = java.util.regex.Pattern.compile("\\b[A-Za-z]+@\\d{3,}\\b");

    private void assertNoCredentialsAnywhere(String consoleOutput, String... outNames) throws IOException {
        int at = consoleOutput.indexOf("Automatic post-run analysis");
        String output = at >= 0 ? consoleOutput.substring(at) : "";
        // a password-shaped token (word@digits) - matched by SHAPE so this test never has to contain a real password itself
        assertFalse(PASSWORD_SHAPE.matcher(output).find(), "no password-shaped text in the post-run output");
        for (String n : outNames) {
            for (Path f : files(out(n).resolve("self-healing"))) {
                assertFalse(PASSWORD_SHAPE.matcher(Files.readString(f, StandardCharsets.ISO_8859_1)).find(), "no password-shaped text in " + f);
            }
        }
        if (Files.isRegularFile(mvnLog)) assertFalse(PASSWORD_SHAPE.matcher(Files.readString(mvnLog)).find(), "the hook passes on no credentials");
    }

    // ================================================================== module-runner (and its 53 thin wrappers)

    @Test
    void moduleRunner_failingRun_triggersExactlyOneAnalysis_reachesAProposal_andStopsForApproval() throws Exception {
        Outcome o = runScript("module-runner.bat", Map.of(), "devhis", "ip");
        assertEquals(1, jarCalls(), "the (stub) jar ran once");
        assertEquals(1, mvnCalls(), "the post-run analysis was started exactly once");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), o.output);
        assertTrue(o.output.contains("STOP - HUMAN APPROVAL REQUIRED") && o.output.contains("PROPOSAL_HASH    : "), o.output);
        assertEquals(1, pending("test-output-ip-devhis").size(), "one proposal, in this run's output folder");
        assertEquals(baselineExit("module-runner.bat", "devhis", "ip"), o.exit, "the runner's exit code is exactly what the same runner returns without the hook line");
        assertEquals(List.of("0"), Files.readAllLines(out("test-output-ip-devhis").resolve(".run-exit")), "the test JVM's exit was captured");
        String log = Files.readString(mvnLog);
        assertTrue(log.contains("ENTRY=batch-module-ip-devhis") && log.contains("proj"), "started from the project root: " + log);
        assertTrue(log.contains("-Dexec.args=--batch") && log.contains("com.kpj.heal.PostRunCoordinator"), log);
        assertNoCredentialsAnywhere(o.output, "test-output-ip-devhis");
        assertTrue(o.output.contains("LLM fallback disabled for the automatic post-run"), o.output);
    }

    @Test
    void theExitCodeOfTheRunner_isIdenticalWithAndWithoutTheAnalysis() throws Exception {
        Outcome with = runScript("module-runner.bat", Map.of(), "devhis", "ip");
        Outcome without = runScript("module-runner.bat", Map.of("DEVHIS_POSTRUN", "off"), "devhis", "ip");
        assertEquals(without.exit, with.exit);
        Outcome pass = runScript("module-runner.bat", Map.of("STUB_MODE", "pass"), "devhis", "ip");
        assertEquals(0, pass.exit, "an all-pass run still exits 0");
    }

    @Test
    void wrapperScripts_nestedRunners_triggerExactlyOnce() throws Exception {
        Outcome o = runScript("run-ip-devhis.bat", Map.of());   // wrapper -> module-runner.bat
        assertEquals(1, jarCalls());
        assertEquals(1, mvnCalls(), "a wrapper calling a runner does not double-trigger");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), o.output);
    }

    @Test
    void anotherWrapperFamily_emergencyOnDsh_triggersExactlyOnce() throws Exception {
        runScript("run-emergency-dsh.bat", Map.of());
        assertEquals(1, mvnCalls());
        assertTrue(Files.isDirectory(out("test-output-emergency-dsh").resolve("self-healing").resolve("post-run")));
    }

    // ================================================================== run.bat

    @Test
    void runBat_fullRun_isTwelveJvmsButOneAnalysis() throws Exception {
        Outcome o = runScript("run.bat", Map.of());
        assertEquals(12, jarCalls(), "a full run launches one JVM per module package");
        assertEquals(1, mvnCalls(), "...but ONE post-run analysis");
        assertEquals(12, Files.readAllLines(out("test-output").resolve(".run-exit")).size(), "every JVM's exit was recorded");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: PENDING_PROPOSAL"), o.output);
        assertEquals(1, files(out("test-output").resolve("self-healing").resolve("post-run")).stream().filter(p -> p.toString().endsWith(".lock")).count());
    }

    @Test
    void runBat_singleFlow_triggersOnce() throws Exception {
        runScript("run.bat", Map.of(), "Ip.Admission");
        assertEquals(1, jarCalls());
        assertEquals(1, mvnCalls());
    }

    @Test
    void runBat_viaTheKlgWrapper_triggersOnce() throws Exception {
        Outcome o = runScript("run-klg.bat", Map.of(), "Ip.Admission");
        assertEquals(1, mvnCalls());
        assertTrue(Files.isDirectory(out("test-output-klg").resolve("self-healing")), o.output);
        assertNoCredentialsAnywhere(o.output, "test-output-klg");
    }

    @Test
    void runBat_aFlowNameThatMatchesNothing_runsNoAnalysis() throws Exception {
        runScript("run.bat", Map.of("STUB_JVM_EXIT", "-1", "STUB_MODE", "none"), "Ip.Typo");
        assertEquals(0, mvnCalls(), "nothing ran, so nothing is analysed");
    }

    @Test
    void runBat_honoursTheKillSwitchGivenInDevhisOpts_throughTheCoordinator() throws Exception {
        Outcome o = runScript("run.bat", Map.of("DEVHIS_OPTS", "-Ddevhis.user=x -Ddevhis.postrun=off"), "Ip.Admission");
        assertEquals(1, mvnCalls(), "the hook cannot read DEVHIS_OPTS safely in a .bat, so the coordinator is started and stands down");
        assertTrue(o.output.contains("POSTRUN_SKIPPED: DISABLED"), o.output);
        assertEquals(0, pending("test-output").size());
    }

    // ================================================================== core-flows-runner

    @Test
    void coreFlows_eachPassHasItsOwnFolderAndItsOwnSingleAnalysis() throws Exception {
        Outcome o = runScript("run-core-flows-devhis.bat", Map.of());
        assertEquals(2, jarCalls(), "Malaysian pass + foreign pass");
        assertEquals(2, mvnCalls(), "two separate runs, two separate analyses - one each");
        assertEquals(1, pending("test-output-core-devhis").size());
        assertEquals(1, pending("test-output-core-devhis-Australian").size());
        assertTrue(o.output.contains("batch-core") || Files.readString(mvnLog).contains("ENTRY=batch-core-test-output-core-devhis"), o.output);
    }

    // ================================================================== things that must stop it

    @Test
    void theKillSwitchEnvVar_meansNoAnalysisAtAll() throws Exception {
        Outcome o = runScript("module-runner.bat", Map.of("DEVHIS_POSTRUN", "off"), "devhis", "ip");
        assertEquals(0, mvnCalls());
        assertTrue(o.output.contains("POSTRUN_SKIPPED: DISABLED"), o.output);
        assertFalse(Files.exists(out("test-output-ip-devhis").resolve("self-healing")));
    }

    @Test
    void withoutASourceTree_theHookSaysSo_andDoesNothing() throws Exception {
        Files.delete(proj.resolve("pom.xml"));
        Outcome o = runScript("module-runner.bat", Map.of(), "devhis", "ip");
        assertEquals(0, mvnCalls());
        assertTrue(o.output.contains("POSTRUN_SKIPPED: NO_SOURCE_TREE"), o.output);
        assertEquals(baselineExit("module-runner.bat", "devhis", "ip"), o.exit, "the runner's own result is unaffected");
    }

    @Test
    void withoutMaven_theHookSaysSo_andDoesNothing() throws Exception {
        Files.delete(bin.resolve("mvn.cmd"));
        Outcome o = runScript("module-runner.bat", Map.of(), "devhis", "ip");
        assertTrue(o.output.contains("POSTRUN_SKIPPED: NO_MAVEN"), o.output);
        assertEquals(baselineExit("module-runner.bat", "devhis", "ip"), o.exit);
    }

    @Test
    void aTestJvmThatExitsNonZeroAndWritesNothing_isTestRunFailed_neverAPass() throws Exception {
        // an OLD failing result already sits in the folder: it must not be analysed in place of a run that produced nothing
        Path old = writeTsv(out("test-output-ip-devhis"), "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.now().minusSeconds(3600));
        writeSidecar(out("test-output-ip-devhis"), "SYN_FLOW", ELIGIBLE_ACTUAL, Instant.now().minusSeconds(3600));
        Outcome o = runScript("module-runner.bat", Map.of("STUB_MODE", "none", "STUB_JVM_EXIT", "3"), "devhis", "ip");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: TEST_RUN_FAILED"), o.output);
        assertEquals(List.of("1"), Files.readAllLines(out("test-output-ip-devhis").resolve(".run-exit")), "the non-zero exit was captured");
        assertEquals(0, pending("test-output-ip-devhis").size());
        assertFalse(Files.exists(out("test-output-ip-devhis").resolve("failure-analysis")), "the old failing result was not analysed");
        assertTrue(Files.isRegularFile(old), "and it was left alone");
    }

    @Test
    void aRunThatWritesNothingAndExitsCleanly_isNoFreshResults() throws Exception {
        Path old = writeTsv(out("test-output-ip-devhis"), "Syn.tsv", "SYN_FLOW", true, ELIGIBLE_ACTUAL, Instant.now().minusSeconds(3600));
        Outcome o = runScript("module-runner.bat", Map.of("STUB_MODE", "none"), "devhis", "ip");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: NO_FRESH_RESULTS"), o.output);
        assertEquals(0, pending("test-output-ip-devhis").size());
        assertTrue(Files.isRegularFile(old));
    }

    @Test
    void aPassingRun_createsNoProposal() throws Exception {
        Outcome o = runScript("module-runner.bat", Map.of("STUB_MODE", "pass"), "devhis", "ip");
        assertTrue(o.output.contains("POSTRUN_COMPLETE: NO_FAILURES"), o.output);
        assertEquals(0, pending("test-output-ip-devhis").size());
        assertEquals(0, o.exit);
    }

    @Test
    void aFreshFailureWithoutPageStateEvidence_isStatedHonestly() throws Exception {
        Outcome o = runScript("module-runner.bat", Map.of("STUB_SIDECAR", ""), "devhis", "ip");   // an older jar that writes no failures/ sidecar
        assertTrue(o.output.contains("for 0 of 1") && o.output.contains("NOT captured by the run that produced these results"), o.output);
    }

    @Test
    void theShippedWrapperScriptsAreUnchangedExceptTheSharedRunners() throws Exception {
        // the 62 thin wrappers must still just delegate; only the three shared runners and the new hook carry the integration
        for (String w : new String[]{"run-ip-devhis.bat", "run-klg.bat", "run-core-flows-devhis.bat", "run-emergency-dsh.bat"}) {
            String t = Files.readString(realShare.resolve(w));
            assertFalse(t.contains("postrun") || t.contains("PostRun"), w + " does not mention the hook - it delegates to a runner");
        }
        for (String legacy : new String[]{"run-ip.bat", "run-op.bat", "run-appconfig.bat", "run-emergency.bat", "run-op-ip-emergency.bat"}) {
            String t = Files.readString(realShare.resolve(legacy));
            assertFalse(t.contains("postrun-hook") || t.contains(".run-exit"), legacy + " (legacy standalone runner) is deliberately left unchanged and unsupported for now");
        }
        for (String runner : new String[]{"module-runner.bat", "core-flows-runner.bat", "run.bat"}) {
            String t = Files.readString(realShare.resolve(runner));
            assertEquals(1, t.split("postrun-hook\\.bat", -1).length - 1, runner + " calls the hook exactly once");
        }
    }
}
