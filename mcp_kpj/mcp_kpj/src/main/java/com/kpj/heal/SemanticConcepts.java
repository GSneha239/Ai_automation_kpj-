package com.kpj.heal;

import java.util.*;

/**
 * Shared "business concept" classification — the single source of truth both {@link SemanticValidator} (the
 * final safety gate) and {@link PatchProposer} (candidate generation) use, so generation and validation never
 * disagree about what counts as the same business concept. Deliberately small, explicit, maintainable — no
 * ML/LLM involved, matching this codebase's "deterministic preferred" convention.
 *
 * <p>Extracted from {@link SemanticValidator} specifically so {@link PatchProposer} can use the SAME
 * concept-matching logic to guide candidate GENERATION (requirement: "prefer structurally and semantically
 * equivalent replacements over textual similarity") rather than only checking it after the fact — a
 * generation rule that already reasons about business concepts finds fewer wrong candidates AND more right
 * ones than one that only ever ranks by raw string distance.</p>
 */
public final class SemanticConcepts {

    private SemanticConcepts() {}

    /** Small, explicit, maintainable "business concept" dictionary — deliberately coarse. A field name is
     *  classified into every bucket whose keyword appears as one of its tokens; two fields are semantically
     *  related if they share at least one bucket. */
    private static final Map<String, Set<String>> CONCEPTS = new LinkedHashMap<>();
    static {
        CONCEPTS.put("name", Set.of("name", "first", "last", "family", "given", "full", "fullname"));
        CONCEPTS.put("date", Set.of("date", "dob", "birth", "day", "month", "year", "expiry", "validity"));
        CONCEPTS.put("identifier", Set.of("id", "code", "no", "number", "num"));
        CONCEPTS.put("type", Set.of("type", "category", "class", "kind"));
        CONCEPTS.put("contact", Set.of("email", "mobile", "phone", "contact", "fax"));
        CONCEPTS.put("address", Set.of("address", "city", "district", "postcode", "country", "state"));
        CONCEPTS.put("demographic", Set.of("gender", "nationality", "religion", "race", "title", "marital", "language"));
        CONCEPTS.put("relation", Set.of("kin", "guarantor", "relation", "nok", "emergency"));
        CONCEPTS.put("clinical", Set.of("doctor", "department", "ward", "bed", "diagnosis", "visit", "admission", "consultation", "specialist", "physician", "pathologist", "radiologist"));
        CONCEPTS.put("financial", Set.of("payor", "amount", "charge", "bill", "invoice", "insurance", "sponsor", "guarantee", "payer"));
        CONCEPTS.put("status", Set.of("status", "flag", "active", "enabled", "disabled"));
        CONCEPTS.put("document", Set.of("passport", "nric", "ic", "license", "document"));
        CONCEPTS.put("schedule", Set.of("slot", "schedule", "time", "session", "duration"));
    }

    /** Business-concept buckets a piece of text belongs to (may be more than one, may be empty if nothing
     *  recognized). Splits on non-letters AND camelCase boundaries before matching, so "SelectedMonth" and
     *  "selected_month" classify identically. */
    public static Set<String> conceptsFor(String text) {
        Set<String> concepts = new LinkedHashSet<>();
        for (String tok : tokensOf(text)) {
            for (Map.Entry<String, Set<String>> e : CONCEPTS.entrySet()) {
                if (e.getValue().contains(tok)) concepts.add(e.getKey());
            }
        }
        return concepts;
    }

    /** True only when BOTH sides recognize at least one concept and they share one — silence (no recognized
     *  keyword on either/both sides) is never treated as agreement or disagreement by this method; callers
     *  that need a three-way answer should call {@link #conceptsFor} directly. */
    public static boolean shareConcept(String a, String b) {
        Set<String> ca = conceptsFor(a), cb = conceptsFor(b);
        return !ca.isEmpty() && !cb.isEmpty() && !Collections.disjoint(ca, cb);
    }

    /** Splits on non-letters AND camelCase boundaries, lowercases, drops single-letter noise. */
    public static Set<String> tokensOf(String s) {
        if (s == null) return Set.of();
        String cleaned = s.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("[^A-Za-z]+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String t : cleaned.toLowerCase().split("\\s+")) {
            if (t.length() > 1) tokens.add(t);
        }
        return tokens;
    }

    /** The namespace prefix of a "Namespace.Field" literal, INCLUDING the trailing dot (e.g.
     *  "Registration.FirstName" -&gt; "Registration."), or {@code null} if the string has no dot. */
    public static String namespaceOf(String s) {
        if (s == null) return null;
        int i = s.indexOf('.');
        return i > 0 ? s.substring(0, i + 1) : null;
    }

    /** The field part after the LAST dot of a "Namespace.Field" literal (or the whole string if there is no
     *  dot) — used so the namespace itself never pollutes concept classification of the field name. */
    public static String fieldPart(String s) {
        if (s == null) return "";
        int i = s.lastIndexOf('.');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    /** Isolates the actual changed span between two strings via common-prefix/common-suffix stripping, snapped
     *  to word boundaries so two different words that happen to share a leading/trailing letter run (e.g.
     *  "First"/"Family" both starting with "F") never get truncated into a misleading fragment. Deterministic
     *  rules and LLM proposals alike overwhelmingly change one small literal within an otherwise-identical
     *  line, so this reliably yields the "old field" / "new field" pair without needing to parse the
     *  surrounding code. */
    public static String[] diffCore(String oldSnippet, String newSnippet) {
        int maxLen = Math.min(oldSnippet.length(), newSnippet.length());
        int prefixLen = 0;
        while (prefixLen < maxLen && oldSnippet.charAt(prefixLen) == newSnippet.charAt(prefixLen)) prefixLen++;
        while (prefixLen > 0 && !isWordBoundary(oldSnippet, prefixLen)) prefixLen--;

        int oldEnd = oldSnippet.length(), newEnd = newSnippet.length();
        int suffixLen = 0;
        int maxSuffix = maxLen - prefixLen;
        while (suffixLen < maxSuffix
                && oldSnippet.charAt(oldEnd - 1 - suffixLen) == newSnippet.charAt(newEnd - 1 - suffixLen)) suffixLen++;
        while (suffixLen > 0 && !isWordBoundary(oldSnippet, oldEnd - suffixLen)) suffixLen--;

        String oldCore = oldSnippet.substring(prefixLen, oldEnd - suffixLen);
        String newCore = newSnippet.substring(prefixLen, newEnd - suffixLen);
        return new String[]{oldCore, newCore};
    }

    /** True if cutting {@code s} right before index {@code idx} falls between two words rather than inside
     *  one — a non-letter transition, or a lowercase-to-uppercase camelCase step. Two uppercase or two
     *  lowercase letters in a row are always considered mid-word. */
    private static boolean isWordBoundary(String s, int idx) {
        if (idx <= 0 || idx >= s.length()) return true;
        char before = s.charAt(idx - 1), at = s.charAt(idx);
        if (!Character.isLetter(before) || !Character.isLetter(at)) return true;
        return Character.isLowerCase(before) && Character.isUpperCase(at);
    }
}
