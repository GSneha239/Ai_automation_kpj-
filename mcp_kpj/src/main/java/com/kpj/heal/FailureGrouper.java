package com.kpj.heal;

import java.util.*;

/**
 * Requirement 3 - groups failures that appear to share the same root cause: same affected Page Object file,
 * same method, and a normalized form of the failure message. Purely a grouping/display aid for the console
 * and dashboard - it never changes what gets proposed or applied. {@link PatchProposer}/{@link PatchApplier}
 * still ever operate on one failure and one file at a time; grouping only decides which failures are shown
 * together and whether a later group member is skipped because an earlier one in the SAME group was already
 * healed this session (see {@link HealingSession#isGroupHealed}).
 */
public class FailureGrouper {

    public static class Group {
        public final String key;
        public final List<FailureRecord> failures = new ArrayList<>();
        public CodeLocator.CodeLocation representativeLocation;

        Group(String key) { this.key = key; }
    }

    public static class Entry {
        public final FailureRecord failure;
        public final CodeLocator.CodeLocation location; // may be null if unresolved

        public Entry(FailureRecord failure, CodeLocator.CodeLocation location) {
            this.failure = failure;
            this.location = location;
        }
    }

    public List<Group> group(List<Entry> entries) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Entry e : entries) {
            String key = signature(e.failure, e.location);
            Group g = groups.computeIfAbsent(key, Group::new);
            g.failures.add(e.failure);
            if (g.representativeLocation == null) g.representativeLocation = e.location;
        }
        return new ArrayList<>(groups.values());
    }

    /** Same Page Object file + method + a normalized failure message = treated as the same root cause. */
    public static String signature(FailureRecord f, CodeLocator.CodeLocation loc) {
        String file = loc != null ? loc.pageObjectFile.toString() : "(unresolved:" + f.step + ")";
        String method = loc != null ? loc.methodName : "(unresolved)";
        return file + "::" + method + "::" + normalize(f.actual);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase().replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }
}
