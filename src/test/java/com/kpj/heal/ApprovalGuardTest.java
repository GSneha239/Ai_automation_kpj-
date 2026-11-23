package com.kpj.heal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static guards on the shape of the approval path, so a future edit cannot quietly add a way to modify source without a human's approval.
 * They read production source text only; nothing is executed.
 */
class ApprovalGuardTest {

    private static final Path HEAL = Paths.get("src/main/java/com/kpj/heal");

    private static String src(String file) throws IOException { return Files.readString(HEAL.resolve(file)); }

    private static int count(String text, Pattern p) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    void onlySelfHealingAgent_createsAPatchApplier_andCallsConfirmAndApply_exactlyTwice() throws IOException {
        List<String> creators = new ArrayList<>(), callers = new ArrayList<>();
        try (Stream<Path> s = Files.list(HEAL)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                String t = Files.readString(p);
                String name = p.getFileName().toString();
                if (t.contains("new PatchApplier(")) creators.add(name + "x" + count(t, Pattern.compile("new PatchApplier\\(")));
                if (!name.equals("PatchApplier.java") && t.contains(".confirmAndApply(")) callers.add(name + "x" + count(t, Pattern.compile("\\.confirmAndApply\\(")));
            }
        }
        assertEquals(List.of("SelfHealingAgent.javax2"), creators, "one applier per approval path (interactive, apply-pending)");
        assertEquals(List.of("SelfHealingAgent.javax2"), callers, "the interactive stdin prompt and apply-pending are the only apply calls");
    }

    @Test
    void theOrchestrator_neverAppliesAnything_orPassesAnApproval() throws IOException {
        String t = src("HealingWorkflowOrchestrator.java");
        assertFalse(t.contains("PatchApplier"), "no patching code is reachable from the orchestrator");
        assertFalse(Pattern.compile("\\{\\s*\"apply-pending\"").matcher(t).find(), "it never invokes apply-pending");
        assertFalse(t.contains("confirmAndApply") || t.contains("applyPendingMode"));
    }

    @Test
    void applyPending_approvalComesOnlyFromTheWordApproved_andTheApplyIsBehindTheValidationGate() throws IOException {
        String t = src("SelfHealingAgent.java");
        assertTrue(t.contains("boolean approved = \"approved\".equalsIgnoreCase(decision);"), "approval is exactly the word 'approved'");
        assertEquals(1, count(t, Pattern.compile("\"approved\"\\.equalsIgnoreCase")), "and nowhere else is approval decided");
        int gate = t.indexOf("validateBeforeApply(ctx, session, pending, approvalHash");
        int apply = t.indexOf(".confirmAndApply(Paths.get(pending.affectedFile)");
        int guard = t.indexOf("if (refusal == null) {", gate);
        assertTrue(gate > 0 && guard > gate && apply > guard, "validate -> 'if (refusal == null)' -> apply: " + gate + "," + guard + "," + apply);
        // the approval itself must still be passed to the applier (a decline passes false and writes nothing)
        assertTrue(t.contains("pending.reason, pending.confidence, approved);"));
    }

    @Test
    void theInteractivePrompt_acceptsOnlyAnExactY() throws IOException {
        String t = src("PatchApplier.java");
        assertTrue(t.contains("boolean approved = \"y\".equals(answer) || \"Y\".equals(answer);"));
        assertEquals(1, count(t, Pattern.compile("boolean approved\\s*=")), "no other way to become approved inside the applier");
    }

    @Test
    void sourceFilesAreWrittenOnlyByThePatchApplierAndItsRestore() throws IOException {
        // the only production writes to an arbitrary (source) path: PatchApplier's patch + restore, and BackupManager's restore
        List<String> writers = new ArrayList<>();
        // (other classes write their OWN log/report files, which they happen to call "file"; only these two names are a target source file)
        Pattern write = Pattern.compile("Files\\.write(String)?\\((patchedFile|targetFile)\\b");
        try (Stream<Path> s = Files.list(HEAL)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                if (write.matcher(Files.readString(p)).find()) writers.add(p.getFileName().toString());
            }
        }
        assertEquals(List.of("BackupManager.java", "PatchApplier.java"), writers);
    }
}
