package com.kpj.heal;

import com.kpj.core.DevHisBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Safe, SYNTHETIC tests of the Maven/Surefire trigger. The listener is driven by a real JUnit {@code TestPlan} that is only DISCOVERED
 * (never executed): the fixture flow is never instantiated, so no browser, Maven run or DevHIS is involved. The coordinator is replaced
 * by a recorder, so these tests also prove "exactly once" and "not at all" without analysing anything.
 */
class PostRunListenerTest {

    /** A flow-shaped class so the discovered plan contains a DevHisBase test. Discovered, never run (guarded + never instantiated). */
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "syn.postrun.fixture.execute", matches = "yes")
    static class SynFlowFixture extends DevHisBase {
        SynFlowFixture() { super("SYN_FIXTURE"); }
        @Override protected void body() { }
        @org.junit.jupiter.api.Test void execute() { }
    }

    private final List<PostRunListener.RunRequest> requests = new ArrayList<>();
    private java.util.function.Function<PostRunListener.RunRequest, PostRunCoordinator.Result> realSink;

    @BeforeEach
    void install() {
        realSink = PostRunListener.sink;
        PostRunListener.sink = r -> { requests.add(r); return new PostRunCoordinator.Result(); };
        System.setProperty("devhis.postrun.trigger", "maven");   // what the pom's Surefire configuration sets for a direct `mvn test`
    }

    @AfterEach
    void restore() {
        PostRunListener.sink = realSink;
        System.clearProperty("devhis.postrun");
        // leave devhis.postrun.trigger as this JVM had it: it is set for the whole Surefire JVM by the pom
    }

    private static TestPlan discover(Class<?>... classes) {
        Launcher launcher = LauncherFactory.create();
        LauncherDiscoveryRequestBuilder b = LauncherDiscoveryRequestBuilder.request();
        for (Class<?> c : classes) b.selectors(selectClass(c));
        return launcher.discover(b.build());
    }

    private static void finish(PostRunListener l, TestPlan plan) {
        l.testPlanExecutionStarted(plan);
        l.testPlanExecutionFinished(plan);
    }

    @Test
    void aPlanWithFlows_triggersExactlyOneAnalysis_scopedToTheExactClasses() {
        TestPlan plan = discover(SynFlowFixture.class);
        PostRunListener l = new PostRunListener();
        finish(l, plan);
        assertEquals(1, requests.size(), "exactly one trigger for the finished plan");
        PostRunListener.RunRequest r = requests.get(0);
        assertEquals(List.of(SynFlowFixture.class.getName()), r.scopeClasses, "the scope is the exact fully qualified class, not a simple name");
        assertTrue(r.startedAtMillis > 0 && r.startedAtMillis <= System.currentTimeMillis(), "the plan's start time is recorded");
        assertEquals(List.of(0), r.exitCodes, "no failure was observed");
    }

    @Test
    void theListenerIsNotFiredTwice_forOnePlan() {
        TestPlan plan = discover(SynFlowFixture.class);
        PostRunListener l = new PostRunListener();
        finish(l, plan);
        // a second listener instance (for example the same service registered twice) must not make the coordinator run twice for one
        // run: the coordinator's own run lock is the guard - proven in PostRunCoordinatorTest - and the listener acts once per plan end.
        assertEquals(1, requests.size());
    }

    @Test
    void aFailedOrAbortedContainerIsRecordedAsANonZeroExit() {
        TestPlan plan = discover(SynFlowFixture.class);
        PostRunListener l = new PostRunListener();
        l.testPlanExecutionStarted(plan);
        l.executionFinished(plan.getRoots().iterator().next(), TestExecutionResult.failed(new RuntimeException("synthetic")));
        l.testPlanExecutionFinished(plan);
        assertEquals(List.of(1), requests.get(0).exitCodes, "a failed container is never reported as a clean exit");
    }

    @Test
    void aSyntheticSuite_neverStartsAnAnalysis() {
        // this very class is what `mvn -Pheal-synthetic test` runs: it contains no DevHisBase flow, so nothing may fire
        finish(new PostRunListener(), discover(PostRunListenerTest.class, PostRunCoordinatorTest.class));
        assertEquals(0, requests.size());
    }

    @Test
    void withoutTheMavenTriggerProperty_theListenerIsInert() {
        System.clearProperty("devhis.postrun.trigger");   // what a ConsoleLauncher / shaded-jar JVM looks like: same service file, no property
        try {
            finish(new PostRunListener(), discover(SynFlowFixture.class));
            assertEquals(0, requests.size(), "the batch runners have their own single post-run step; the listener must not add a second");
        } finally {
            System.setProperty("devhis.postrun.trigger", "maven");
        }
    }

    @Test
    void theKillSwitch_silencesTheListener() {
        System.setProperty("devhis.postrun", "off");
        finish(new PostRunListener(), discover(SynFlowFixture.class));
        assertEquals(0, requests.size());
    }

    @Test
    void theListenerNeverThrows_evenIfTheSinkFails() {
        PostRunListener.sink = r -> { throw new IllegalStateException("synthetic sink failure"); };
        finish(new PostRunListener(), discover(SynFlowFixture.class));   // must simply return - the test run's own outcome is untouched
        assertEquals(0, requests.size());
    }

    @Test
    void theListenerIsRegisteredForSurefire_andTheTriggerIsArmedInThePom() throws IOException {
        List<String> found = new ArrayList<>();
        for (TestExecutionListener l : ServiceLoader.load(TestExecutionListener.class)) found.add(l.getClass().getName());
        assertEquals(1, found.stream().filter(PostRunListener.class.getName()::equals).count(), "registered exactly once via META-INF/services: " + found);
        String service = Files.readString(Paths.get("src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener"));
        assertTrue(service.contains("com.kpj.heal.PostRunListener"));
        String pom = Files.readString(Paths.get("pom.xml"));
        assertTrue(pom.contains("<devhis.postrun.trigger>maven</devhis.postrun.trigger>"), "Surefire arms the trigger for a direct mvn test");
        assertNotNull(PostRunListener.class);
    }

    @Test
    void runAllDoesNotGoThroughJUnit_soTheListenerCannotAlsoFireForIt() throws IOException {
        String runAll = Files.readString(Paths.get("src/test/java/com/kpj/RunAll.java"));
        assertTrue(runAll.contains("PostRunCoordinator.triggerFor(\"runall\""), "RunAll triggers the coordinator itself, once, at its end");
        assertFalse(runAll.contains("@Test") || runAll.contains("junit"), "RunAll runs flows directly: no JUnit plan, hence no listener");
        assertEquals(1, runAll.split("PostRunCoordinator\\.triggerFor", -1).length - 1, "one call site");
    }
}
