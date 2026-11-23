package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of how the agent launches its test runs, using the REAL process-launch code with a harmless child program
 * ({@link FakeMavenChild}) in place of {@code mvn}. No Maven, browser, DevHIS or project test is started.
 *
 * <p>Checked: the exact command line, the exit code that comes back, that nothing is dropped from the environment the child sees
 * ({@code DEVHIS_OUTDIR}, {@code JAVA_TOOL_OPTIONS}, {@code LLM_*}, {@code HEAL_MAX_ATTEMPTS}, {@code PATH}), and that a launch
 * failure surfaces as an exception rather than being mistaken for a result.</p>
 */
class ProcessLaunchTest {

    @TempDir
    Path tmp;

    private SelfHealingAgent.MavenLauncher saved;

    @BeforeEach
    void setUp() {
        saved = SelfHealingAgent.mavenLauncher;
        SelfHealingAgent.mavenLauncher = SelfHealingAgent::launchMaven;   // the REAL launcher
    }

    @AfterEach
    void restore() {
        SelfHealingAgent.mavenLauncher = saved;
        SelfHealingAgent.mavenExecutableOverride = null;
    }

    private Path report() { return tmp.resolve("child-report.txt"); }

    /** Points the real launcher at the harmless child program, which will exit with {@code exitCode}. */
    private void useChild(int exitCode) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classes = Paths.get(FakeMavenChild.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        SelfHealingAgent.mavenExecutableOverride = List.of(java, "-cp", classes, FakeMavenChild.class.getName(), report().toString(), String.valueOf(exitCode));
    }

    private List<String> reportLines() throws IOException { return Files.readAllLines(report()); }

    // ------------------------------------------------------------------ command line

    @Test
    void theCommandLine_isMvnWithTheGoalsExactlyAsPassed() {
        SelfHealingAgent.mavenExecutableOverride = null;
        List<String> cmd = SelfHealingAgent.processCommand("-q", "-Dtest=SynTest", "test");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        assertEquals(windows ? List.of("cmd", "/c", "mvn", "-q", "-Dtest=SynTest", "test") : List.of("mvn", "-q", "-Dtest=SynTest", "test"), cmd);
        assertEquals(windows ? List.of("cmd", "/c", "mvn", "-q", "test-compile") : List.of("mvn", "-q", "test-compile"),
                SelfHealingAgent.processCommand("-q", "test-compile"), "the compile step uses the same launcher");
    }

    @Test
    void theGoalsReachTheChildExactly_andInOrder() throws Exception {
        useChild(0);
        assertEquals(0, SelfHealingAgent.runMaven("-q", "-Dtest=SynTest", "test"));
        List<String> args = reportLines().stream().filter(l -> l.startsWith("ARG=")).collect(Collectors.toList());
        assertEquals(List.of("ARG=-q", "ARG=-Dtest=SynTest", "ARG=test"), args);
    }

    @Test
    void theTestRunIsNeverAimedAtTheAppointmentListTest() throws Exception {
        // the agent only ever runs the class it is asked to heal (and its declared read-only dependents) - nothing else is added
        useChild(0);
        SelfHealingAgent.runMaven("-q", "-Dtest=SynTest", "test");
        assertFalse(String.join(" ", reportLines()).contains("AppointmentList"));
    }

    // ------------------------------------------------------------------ exit code

    @Test
    void theExitCode_comesBackUnchanged() throws Exception {
        for (int code : new int[]{0, 1, 2, 137}) {
            useChild(code);
            assertEquals(code, SelfHealingAgent.runMaven("-q", "test"), "exit code " + code);
        }
    }

    // ------------------------------------------------------------------ environment

    @Test
    void theChildSeesTheSameEnvironmentAndWorkingDirectoryAsTheAgent() throws Exception {
        useChild(0);
        SelfHealingAgent.runMaven("-q", "test");
        List<String> lines = reportLines();
        for (String key : FakeMavenChild.WATCHED_ENV) {
            String parent = System.getenv(key);
            assertTrue(lines.contains("ENV:" + key + "=" + (parent == null ? "<unset>" : parent)),
                    key + " must reach the child exactly as the agent has it (" + (parent == null ? "unset" : "set") + ")");
        }
        assertTrue(lines.contains("CWD=" + System.getProperty("user.dir")), "the child runs in the agent's working directory (where test-output/ is resolved)");
    }

    // ------------------------------------------------------------------ launch failure

    @Test
    void aMissingExecutable_isAnException_notAResult() {
        SelfHealingAgent.mavenExecutableOverride = List.of(tmp.resolve("no-such-mvn").toString());
        assertThrows(IOException.class, () -> SelfHealingAgent.runMaven("-q", "test"));
    }
}
