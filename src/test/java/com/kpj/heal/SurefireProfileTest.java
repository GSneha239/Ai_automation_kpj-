package com.kpj.heal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves, with REAL Maven/Surefire on a throw-away project, what a bare {@code mvn test} selects and what the production pom's
 * {@code heal-synthetic} profile selects - without ever running a project test. The temp project has two harmless dummy classes laid out
 * like the real ones: {@code com.kpj.tests.DummyRealTest} (stands for AppointmentListTest and the other browser tests) and
 * {@code com.kpj.heal.DummySyntheticTest}. Each only writes a marker file. The profile text is taken verbatim from the production pom.
 */
class SurefireProfileTest {

    @TempDir
    static Path shared;

    private static Path project;
    private static boolean mavenAvailable;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @BeforeAll
    static void build() throws Exception {
        String prod = Files.readString(Paths.get("pom.xml"));
        Matcher profile = Pattern.compile("(?s)<profile>\\s*<id>heal-synthetic</id>.*?</profile>").matcher(prod);
        assertTrue(profile.find(), "the production pom declares the heal-synthetic profile");
        Matcher plugin = Pattern.compile("(?s)<plugin>\\s*<groupId>org.apache.maven.plugins</groupId>\\s*<artifactId>maven-surefire-plugin</artifactId>\\s*<version>3.2.5</version>.*?</plugin>").matcher(prod);
        assertTrue(plugin.find(), "the production pom pins the Surefire plugin");

        project = shared.resolve("profile-probe");
        Files.createDirectories(project.resolve("src/test/java/com/kpj/tests"));
        Files.createDirectories(project.resolve("src/test/java/com/kpj/heal"));
        Files.writeString(project.resolve("pom.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>probe</groupId><artifactId>profile-probe</artifactId><version>1.0</version>\n"
                + "  <properties><maven.compiler.source>21</maven.compiler.source><maven.compiler.target>21</maven.compiler.target>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>\n"
                + "  <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>\n"
                + "    <version>5.10.2</version><scope>test</scope></dependency></dependencies>\n"
                + "  <build><plugins>" + plugin.group() + "</plugins></build>\n"
                + "  <profiles>" + profile.group() + "</profiles>\n</project>\n");
        dummy("com/kpj/tests/DummyRealTest.java", "com.kpj.tests", "DummyRealTest", "real");
        dummy("com/kpj/heal/DummySyntheticTest.java", "com.kpj.heal", "DummySyntheticTest", "synthetic");
        mavenAvailable = run("-v").exit == 0;
    }

    private static void dummy(String path, String pkg, String name, String marker) throws IOException {
        Files.writeString(project.resolve("src/test/java").resolve(path), "package " + pkg + ";\n"
                + "import org.junit.jupiter.api.Test;\nimport java.nio.file.*;\n"
                + "public class " + name + " {\n    @Test void runs() throws Exception {\n"
                + "        Files.createDirectories(Paths.get(\"target\"));\n"
                + "        Files.writeString(Paths.get(\"target/ran-" + marker + ".txt\"), \"ran\");\n    }\n}\n");
    }

    private static final class R { int exit; String log; }

    private static R run(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.add("mvn");
        cmd.add("-o");
        cmd.add("-q");
        if (!(args.length == 1 && args[0].equals("-v"))) { cmd.add("-f"); cmd.add(project.resolve("pom.xml").toString()); }
        for (String a : args) cmd.add(a);
        Path log = shared.resolve("mvn-" + System.nanoTime() + ".log");
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile());
        if (project != null) pb.directory(project.toFile());
        Process p = pb.start();
        if (!p.waitFor(240, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("Maven timed out"); }
        R r = new R();
        r.exit = p.exitValue();
        r.log = Files.readString(log);
        return r;
    }

    private static void clean() throws IOException {
        Files.deleteIfExists(project.resolve("target/ran-real.txt"));
        Files.deleteIfExists(project.resolve("target/ran-synthetic.txt"));
    }

    @Test
    void aBareMvnTest_selectsTheRealStyleTestsToo_whichIsTheDangerForTheRealProject() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clean();
        R r = run("test");
        assertEquals(0, r.exit, r.log);
        assertTrue(Files.exists(project.resolve("target/ran-real.txt")), "a bare mvn test ran the com.kpj.tests.*Test class (this is what would reach AppointmentListTest)");
        assertTrue(Files.exists(project.resolve("target/ran-synthetic.txt")));
    }

    @Test
    void theHealSyntheticProfile_selectsOnlyTheSyntheticTests() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clean();
        R r = run("-Pheal-synthetic", "test");
        assertEquals(0, r.exit, r.log);
        assertTrue(Files.exists(project.resolve("target/ran-synthetic.txt")), "the synthetic class ran");
        assertFalse(Files.exists(project.resolve("target/ran-real.txt")), "the com.kpj.tests.* class did NOT run");
    }

    @Test
    void anExplicitDashDTest_stillSelectsExactlyWhatWasNamed_profileOrNot() throws Exception {
        assumeTrue(mavenAvailable, "mvn is not available");
        clean();
        R r = run("-Pheal-synthetic", "-Dtest=DummySyntheticTest", "test");
        assertEquals(0, r.exit, r.log);
        assertTrue(Files.exists(project.resolve("target/ran-synthetic.txt")));
        assertFalse(Files.exists(project.resolve("target/ran-real.txt")));
    }
}
