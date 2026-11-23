package com.kpj.heal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the documentation to the promises it must keep: every unsupported entry point is named, and coverage is never claimed as universal. */
class PostRunCoverageDocTest {

    private static String doc() throws IOException {
        return Files.readString(Paths.get("devhis-tests-share/POST-RUN-HEALING.txt"));
    }

    @Test
    void everyUnsupportedEntryPoint_isDocumented() throws IOException {
        String d = doc();
        for (String unsupported : List.of("run-ip.bat", "run-op.bat", "run-appconfig.bat", "run-emergency.bat", "run-op-ip-emergency.bat",
                "Executables", "java -jar", "main()", "playwright-ts", "killed or interrupted", "shaded jar was NOT rebuilt")) {
            assertTrue(d.contains(unsupported), "the documentation names the unsupported entry point: " + unsupported);
        }
        assertTrue(d.contains("NOT universal coverage") || d.contains("is NOT universal"), "it does not claim universal coverage");
    }

    @Test
    void theSwitchesAndSafetyPromises_areDocumented() throws IOException {
        String d = doc();
        for (String s : List.of("DEVHIS_POSTRUN=off", "-Ddevhis.postrun=off", "DEVHIS_POSTRUN_LLM=on", "never applies a patch", "NOT captured by the run that produced these results",
                "MISMATCHED_OUTPUT", "fully qualified class name", "approval hash")) {
            assertTrue(d.contains(s) || d.toLowerCase().contains(s.toLowerCase()), "documented: " + s);
        }
        assertFalse(java.util.regex.Pattern.compile("\\b[A-Za-z]+@\\d{3,}\\b").matcher(d).find(), "no password-shaped text in the documentation");
    }

    @Test
    void theInterruptedRunMode_andTheQualifiedNameSelection_areDocumented_andReallyExist() throws IOException {
        String d = doc();
        assertTrue(d.contains("--recover <output folder>") && d.contains("INTERRUPTED_PARTIAL_RESULTS") && d.contains("never analysed"), "interrupted-run mode documented");
        assertTrue(d.contains("-Dtest=<fully qualified class name>") && d.contains("Transfer"), "qualified-name selection documented");
        assertFalse(d.contains("KNOWN LIMITATION"), "the obsolete simple-name limitation text is gone");
        String agent = Files.readString(Paths.get("src/main/java/com/kpj/heal/SelfHealingAgent.java"));
        String orch = Files.readString(Paths.get("src/main/java/com/kpj/heal/HealingWorkflowOrchestrator.java"));
        assertFalse(agent.contains("\"-Dtest=\" + simpleName") || agent.contains("\"-Dtest=\" + depSimple") || orch.contains("\"-Dtest=\" + simpleName"),
                "no agent launches a test by simple name any more");
        assertTrue(agent.contains("testSelector(className)") && agent.contains("testSelector(depClassName)") && orch.contains("testSelector(fqcn)"));
    }

    @Test
    void theDocumentedSupportedRunners_actuallyCallTheHook() throws IOException {
        Path share = Paths.get("devhis-tests-share");
        for (String runner : List.of("module-runner.bat", "core-flows-runner.bat", "run.bat")) {
            assertTrue(Files.readString(share.resolve(runner)).contains("postrun-hook.bat"), runner + " calls the hook");
        }
        assertTrue(Files.isRegularFile(share.resolve("postrun-hook.bat")));
    }
}
