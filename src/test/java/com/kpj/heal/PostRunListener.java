package com.kpj.heal;

import com.kpj.core.DevHisBase;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Post-run trigger for DIRECT Maven/Surefire executions ({@code mvn test [-Dtest=...]}). Registered through
 * {@code META-INF/services}; it does nothing unless the pom's Surefire configuration sets
 * {@code devhis.postrun.trigger=maven}, so the shaded-jar / ConsoleLauncher JVMs (which load the same service file) stay inert -
 * the batch runners have their own, single, post-run step.
 *
 * <p>It acts once, when the whole test plan has finished, and only if that plan contains at least one {@link DevHisBase} flow:
 * a synthetic suite (for example {@code mvn -Pheal-synthetic test}) therefore never starts an analysis. The scope handed to the
 * coordinator is the exact set of flow classes the plan executed, which is what {@code -Dtest=} selected.</p>
 */
public class PostRunListener implements TestExecutionListener {

    /** Test seam: where the finished run goes. Production: the shared coordinator. */
    static volatile Function<RunRequest, PostRunCoordinator.Result> sink =
            r -> PostRunCoordinator.triggerFor("maven", r.startedAtMillis, r.exitCodes, r.scopeClasses);

    /** What the listener hands to the sink. */
    static final class RunRequest {
        final long startedAtMillis;
        final List<Integer> exitCodes;
        final List<String> scopeClasses;
        RunRequest(long s, List<Integer> e, List<String> c) { startedAtMillis = s; exitCodes = e; scopeClasses = c; }
    }

    private volatile long startedAt = 0;
    private volatile boolean anyFailure = false;

    static boolean triggerEnabled() {
        return "maven".equalsIgnoreCase(System.getProperty("devhis.postrun.trigger", "")) && !PostRunCoordinator.disabled();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        startedAt = System.currentTimeMillis();
        anyFailure = false;
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (result.getStatus() == TestExecutionResult.Status.FAILED || result.getStatus() == TestExecutionResult.Status.ABORTED) anyFailure = true;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        try {
            if (!triggerEnabled() || PostRunCoordinator.isActive()) return;
            List<String> flows = flowClasses(plan);
            if (flows.isEmpty()) return;
            List<Integer> exit = new ArrayList<>();
            exit.add(anyFailure ? 1 : 0);
            sink.apply(new RunRequest(startedAt, exit, flows));
        } catch (Throwable t) {
            System.out.println("POSTRUN_COMPLETE: INTERNAL_ERROR - " + t + " (the test run's own results and exit status are unaffected)");
        }
    }

    /** The distinct {@link DevHisBase} flow classes in the plan, by fully qualified name. */
    static List<String> flowClasses(TestPlan plan) {
        TreeSet<String> out = new TreeSet<>();
        for (TestIdentifier root : plan.getRoots()) {
            for (TestIdentifier d : plan.getDescendants(root)) {
                d.getSource().ifPresent(src -> {
                    if (src instanceof ClassSource) {
                        Class<?> c = ((ClassSource) src).getJavaClass();
                        if (DevHisBase.class.isAssignableFrom(c)) out.add(c.getName());
                    }
                });
            }
        }
        return new ArrayList<>(out);
    }
}
