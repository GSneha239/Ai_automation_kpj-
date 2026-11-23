package com.kpj.heal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * One analysis per completed run. The lock is a file created atomically ({@code CREATE_NEW}); whoever creates it owns the
 * run, and every later trigger for the same run (a nested runner, a second listener, a retried hook) finds it and stands down.
 * Lives under {@code <outDir>/self-healing/post-run/}, so it is additive and never touches existing report data.
 */
public final class RunLedger {

    private final Path dir;

    public RunLedger(Path outDir) {
        this.dir = outDir.resolve("self-healing").resolve("post-run");
    }

    public Path dir() { return dir; }

    /** @return true if THIS caller now owns the run; false if another trigger already claimed it. */
    public boolean claim(String runKey, String claimedBy) throws IOException {
        Files.createDirectories(dir);
        Path lock = dir.resolve(safe(runKey) + ".lock");
        try {
            Files.writeString(lock, "claimedBy=" + claimedBy + "\nat=" + System.currentTimeMillis() + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        }
    }

    public Path lockFile(String runKey) { return dir.resolve(safe(runKey) + ".lock"); }

    public Path resultFile(String runKey) { return dir.resolve(safe(runKey) + ".result.txt"); }

    private static String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
