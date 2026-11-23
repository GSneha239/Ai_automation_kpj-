package com.kpj.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3 Step 4 — hand-rolled parser for the FIXED, flat JSON contract the LLM must return:
 * <pre>
 * {
 *   "rootCause": "...", "explanation": "...", "evidence": ["...", "..."], "affectedFiles": ["..."],
 *   "oldSnippet": "...", "newSnippet": "...", "suggestedFix": "...", "confidence": 0.0, "risk": "LOW|MEDIUM|HIGH",
 *   "semanticJustification": "...", "expectedBehaviorAfterFix": "..."
 * }
 * </pre>
 * Deliberately not a general-purpose JSON library (no new project dependency) — the schema is small, flat,
 * and known in advance, so targeted field extraction is sufficient and keeps this file auditable. Never
 * throws on malformed input; returns {@code null} instead, which {@link LlmFallbackProposer} treats as an
 * automatic rejection (no proposal), exactly like an LLM call that failed outright.
 */
public class LlmResponseParser {

    public static class ParsedLlmResponse {
        public String rootCause = "";
        public String explanation = "";
        public List<String> evidence = new ArrayList<>();
        public List<String> affectedFiles = new ArrayList<>();
        public String oldSnippet = "";
        public String newSnippet = "";
        public String suggestedFix = "";
        public double confidence = -1; // sentinel: "missing" — LlmProposalValidator rejects a negative value
        public String risk = "";
        /** Requirement (structured healing proposal) — the LLM's own account of why old/new are the SAME
         *  business field/concept and how the fix addresses the observed failure. Informational only: the
         *  authoritative semantic gate is {@link SemanticValidator}, applied uniformly to every candidate
         *  regardless of what the LLM claims about its own reasoning here. */
        public String semanticJustification = "";
        public String expectedBehaviorAfterFix = "";
    }

    /** @return the parsed response, or {@code null} if the input contains no plausible balanced JSON object. */
    public ParsedLlmResponse parse(String raw) {
        if (raw == null) return null;
        String json = extractJsonObject(raw);
        if (json == null) return null;

        ParsedLlmResponse r = new ParsedLlmResponse();
        r.rootCause = extractString(json, "rootCause");
        r.explanation = extractString(json, "explanation");
        r.evidence = extractStringArray(json, "evidence");
        r.affectedFiles = extractStringArray(json, "affectedFiles");
        r.oldSnippet = extractString(json, "oldSnippet");
        r.newSnippet = extractString(json, "newSnippet");
        r.suggestedFix = extractString(json, "suggestedFix");
        r.confidence = extractNumber(json, "confidence");
        r.risk = extractString(json, "risk");
        r.semanticJustification = extractString(json, "semanticJustification");
        r.expectedBehaviorAfterFix = extractString(json, "expectedBehaviorAfterFix");
        return r;
    }

    /** Finds the first balanced {@code {...}} span in the raw text (quote-aware, so braces inside string
     *  values don't break the count) — tolerates a response wrapped in stray prose/markdown fences around
     *  the JSON object, without attempting to interpret anything outside it. */
    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false, escape = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return raw.substring(start, i + 1); }
        }
        return null; // unbalanced — reject rather than guess at a truncated object
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return "";
        return unescape(m.group(1));
    }

    private static List<String> extractStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (!m.find()) return out;
        Matcher em = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(m.group(1));
        while (em.find()) out.add(unescape(em.group(1)));
        return out;
    }

    private static double extractNumber(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        if (!m.find()) return -1;
        try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return -1; }
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
