package com.kpj.heal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Everything the post-run coordinator needs to know about ONE completed test run: which entry point ran it, where its
 * results are, when it started and finished, how its test JVM(s) exited, and (optionally) the exact test classes it
 * was scoped to. It is plain data, written as a small {@code .properties} file so a batch runner can hand it to the
 * coordinator across a process boundary.
 *
 * <p>It never carries credentials: the runner's {@code DEVHIS_OPTS} (URL, user, password) is deliberately not part of it.</p>
 */
public final class RunContext {

    /** Which supported entry point produced the run. */
    public String entryPoint = "unknown";
    /** Absolute path of the run's output folder (the one holding {@code aggregate-data}). */
    public String outDir = "";
    /** Run start (the marker file's timestamp for batch runs; plan start for Maven). Epoch millis; 0 = unknown. */
    public long startedAtMillis = 0;
    /** Run end (when the post-run step was reached). Epoch millis; 0 = unknown. */
    public long endedAtMillis = 0;
    /** Exit code of every test JVM the run launched, in order. Empty = the entry point could not capture any. */
    public List<Integer> exitCodes = new ArrayList<>();
    /** Exact fully qualified test classes the run executed, when the entry point knows them (Maven, RunAll). Empty = unknown. */
    public List<String> scopeClasses = new ArrayList<>();
    /** The jar that ran the tests (path, size, modified) - facts only, used to describe the failure evidence honestly. */
    public String jarPath = "";
    public long jarSize = -1;
    public long jarModifiedMillis = -1;

    public RunContext() { }

    /** Stable key identifying "this run" for the one-analysis-per-run lock. */
    public String key() {
        return entryPoint.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + startedAtMillis;
    }

    public boolean hasNonZeroExit() {
        for (int c : exitCodes) if (c != 0) return true;
        return false;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("entryPoint", entryPoint);
        p.setProperty("outDir", outDir);
        p.setProperty("startedAtMillis", Long.toString(startedAtMillis));
        p.setProperty("endedAtMillis", Long.toString(endedAtMillis));
        StringJoiner codes = new StringJoiner(",");
        for (int c : exitCodes) codes.add(Integer.toString(c));
        p.setProperty("exitCodes", codes.toString());
        p.setProperty("scopeClasses", String.join(",", scopeClasses));
        p.setProperty("jarPath", jarPath);
        p.setProperty("jarSize", Long.toString(jarSize));
        p.setProperty("jarModifiedMillis", Long.toString(jarModifiedMillis));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "post-run context (no credentials)");
        }
    }

    public static RunContext load(Path file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        RunContext c = new RunContext();
        c.entryPoint = p.getProperty("entryPoint", "unknown");
        c.outDir = p.getProperty("outDir", "");
        c.startedAtMillis = parseLong(p.getProperty("startedAtMillis"), 0);
        c.endedAtMillis = parseLong(p.getProperty("endedAtMillis"), 0);
        for (String s : split(p.getProperty("exitCodes", ""))) {
            try { c.exitCodes.add(Integer.parseInt(s)); } catch (NumberFormatException e) { c.exitCodes.add(Integer.MIN_VALUE); }
        }
        c.scopeClasses.addAll(split(p.getProperty("scopeClasses", "")));
        c.jarPath = p.getProperty("jarPath", "");
        c.jarSize = parseLong(p.getProperty("jarSize"), -1);
        c.jarModifiedMillis = parseLong(p.getProperty("jarModifiedMillis"), -1);
        return c;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split(",")) if (!part.isBlank()) out.add(part.trim());
        return out;
    }

    private static long parseLong(String s, long def) {
        try { return s == null ? def : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return def; }
    }
}
