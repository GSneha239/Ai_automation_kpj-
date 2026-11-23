package com.kpj.heal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A harmless stand-in for {@code mvn}, started as a real child process by {@link ProcessLaunchTest}. It does nothing but record what
 * it was given and what environment it sees, then exit with a chosen code. It has no connection to Maven, a browser or DevHIS.
 *
 * <p>Arguments: {@code <reportFile> <exitCode> <goals and arguments exactly as the agent passed them...>}.</p>
 */
public final class FakeMavenChild {

    /** The environment variables the Self-Healing workflow relies on reaching the test run. */
    static final String[] WATCHED_ENV = {"DEVHIS_OUTDIR", "JAVA_TOOL_OPTIONS", "HEAL_MAX_ATTEMPTS", "LLM_ENABLED", "LLM_ENDPOINT", "PATH"};

    private FakeMavenChild() {}

    public static void main(String[] args) throws Exception {
        Path report = Paths.get(args[0]);
        int exit = Integer.parseInt(args[1]);
        List<String> lines = new ArrayList<>();
        for (int i = 2; i < args.length; i++) lines.add("ARG=" + args[i]);
        for (String key : WATCHED_ENV) {
            String v = System.getenv(key);
            lines.add("ENV:" + key + "=" + (v == null ? "<unset>" : v));
        }
        lines.add("CWD=" + System.getProperty("user.dir"));
        Files.write(report, lines);
        System.exit(exit);
    }
}
