package com.kpj.heal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Requirement 5 - read-only Git awareness. Only ever runs read-only plumbing ({@code rev-parse},
 * {@code branch --show-current}, {@code status --porcelain}, {@code diff}) - never {@code add}/{@code commit}/
 * {@code push}/{@code reset}/{@code checkout}. Used to (a) show the repository state before healing, and (b)
 * block healing a file that already has unrelated uncommitted changes, so a patch can never silently discard
 * a developer's in-progress work. Degrades gracefully to {@code isRepo=false} when the project isn't a Git
 * repository at all (this project currently isn't) - every other check becomes a no-op rather than an error.
 */
public class GitStatus {

    public final boolean isRepo;
    public final String branch;
    public final String commit;
    public final List<String> modifiedOrUntrackedFiles;

    private GitStatus(boolean isRepo, String branch, String commit, List<String> modifiedOrUntrackedFiles) {
        this.isRepo = isRepo;
        this.branch = branch;
        this.commit = commit;
        this.modifiedOrUntrackedFiles = modifiedOrUntrackedFiles;
    }

    public static GitStatus capture() {
        String isInside = run("git", "rev-parse", "--is-inside-work-tree");
        if (!"true".equals(isInside)) {
            return new GitStatus(false, "", "", List.of());
        }
        String branch = run("git", "branch", "--show-current");
        String commit = run("git", "rev-parse", "HEAD");
        List<String> changed = new ArrayList<>();
        String status = runMultiline("git", "status", "--porcelain");
        for (String line : status.split("\n")) {
            if (line.isBlank()) continue;
            changed.add(line.length() > 3 ? line.substring(3).trim() : line.trim());
        }
        return new GitStatus(true, branch == null ? "" : branch, commit == null ? "" : commit, changed);
    }

    /** True if {@code file} shows up as modified or untracked in the working tree right now. */
    public boolean hasUncommittedChanges(Path file) {
        if (!isRepo) return false;
        String norm = file.toString().replace('\\', '/');
        for (String f : modifiedOrUntrackedFiles) {
            String fn = f.replace('\\', '/');
            if (norm.endsWith(fn) || fn.endsWith(norm)) return true;
        }
        return false;
    }

    /** The EXISTING (pre-healing) diff for one file - shown separately from the proposed healing diff, so a
     *  human can tell the two apart. Empty string if there is none, or the project isn't a Git repo. */
    public static String diffFor(Path file) {
        return runMultiline("git", "diff", "--", file.toString());
    }

    private static String run(String... cmd) {
        String out = runMultiline(cmd);
        return out == null ? null : out.trim();
    }

    private static String runMultiline(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out;
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
