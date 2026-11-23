package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared fixtures for the synthetic {@code apply-pending} tests. Everything lives in the caller's temp folder: a tiny test
 * class and Page Object the real {@link CodeLocator} can resolve, the failure-detail sidecar the real framework writes, and a
 * correctly sealed {@link PendingHeal}. Nothing here touches Maven, a browser, DevHIS or a real project file.
 *
 * <p>The synthetic Page Object is {@code <tmp>/src/SynPage.java}, referenced from the synthetic test as {@code src.SynPage};
 * the locator is pointed at {@code <tmp>} through {@link CodeLocator#testRootOverride}, so the "healed" file is the very file
 * the locator resolves for the failing step, exactly as in a real run.</p>
 */
final class ApplyTestSupport {

    private ApplyTestSupport() {}

    static final String TEST_FQCN = "com.kpj.tests.Syn.SynTest";

    /** Writes the synthetic test class under {@code root} and points the {@link CodeLocator} at it. */
    static void installLocatorFixture(Path root, String testId, String step) throws IOException {
        Path test = root.resolve("com/kpj/tests/Syn/SynTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test,
                "package com.kpj.tests.Syn;\n"
                        + "import src.SynPage;\n"
                        + "public class SynTest {\n"
                        + "    public SynTest() { super(\"" + testId + "\"); }\n"
                        + "    protected void body() {\n"
                        + "        SynPage p = new SynPage(page);\n"
                        + "        p.probe();\n"
                        + "        step(\"" + step + "\", \"desc\", \"expected\", \"actual\", \"FAIL\");\n"
                        + "    }\n}\n");
        CodeLocator.testRootOverride = root;
    }

    static void uninstallLocatorFixture() { CodeLocator.testRootOverride = null; }

    /** The failure-detail sidecar {@code DevHisBase} writes for a FAIL step (URL + DOM summary), so the failure has DOM evidence. */
    static void writeFailureSidecar(Path out, String testId, String step, String expected, String actual) throws IOException {
        Path dir = out.resolve("failures");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(testId + "_1.txt"),
                "testId=" + testId + "\npage=Synthetic page\nstep=" + step + "\ndesc=d\nexpected=" + expected + "\nactual=" + actual
                        + "\nurl=https://synthetic.invalid/page\nbuttons=Save|Cancel\nselects=staffScheduleInfo.Locationid:Main\nheadings=Staff Schedule\n");
    }

    /** Records the source file's hash and the failure evidence currently on disk for the step, then seals the proposal. */
    static void seal(PendingHeal p, Path out, Path source) throws IOException {
        FailureRecord failure = new FailureScanner(out.toString()).scan(p.testId).stream()
                .filter(f -> f.step.equals(p.step)).findFirst()
                .orElseThrow(() -> new IllegalStateException("the synthetic failing run has no FAIL row for " + p.step));
        p.seal(source, PendingHeal.hashFailure(failure.step, failure.expected, failure.actual));
    }

    /** Re-seals after a test deliberately changed a field, so the proposal is internally consistent again. */
    static void reseal(PendingHeal p, Path source, Path out) throws IOException { seal(p, out, source); }

    /** The hash a correct approval must quote for the pending proposal currently saved at {@code pendingFile}. */
    static String storedHash(Path pendingFile) throws IOException { return PendingHeal.load(pendingFile).proposalHash; }

    /** Runs {@code apply-pending}; an {@code approved} decision quotes the saved proposal's own hash, as a correct approval does. */
    static void apply(Path pendingFile, String decision) throws Exception {
        if ("approved".equalsIgnoreCase(decision)) {
            SelfHealingAgent.main(new String[]{"apply-pending", pendingFile.toString(), decision, storedHash(pendingFile)});
        } else {
            SelfHealingAgent.main(new String[]{"apply-pending", pendingFile.toString(), decision});
        }
    }

    /** Runs {@code apply-pending ... approved <hash>} with an explicit (possibly wrong) hash argument. */
    static void applyQuoting(Path pendingFile, String hashArgument) throws Exception {
        SelfHealingAgent.main(hashArgument == null
                ? new String[]{"apply-pending", pendingFile.toString(), "approved"}
                : new String[]{"apply-pending", pendingFile.toString(), "approved", hashArgument});
    }
}
