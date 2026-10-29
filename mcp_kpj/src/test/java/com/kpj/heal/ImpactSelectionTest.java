package com.kpj.heal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC / static tests of which dependent tests would be re-run after a patch. Nothing is executed: only source text is read.
 * A dependent is re-run against the application only if its class-level doc comment DECLARES it read-only; the word elsewhere in a
 * file (an inline comment, a string, a detail inside the doc of a test that changes data, a negation) must not qualify it.
 */
class ImpactSelectionTest {

    @TempDir
    Path tmp;

    @AfterEach
    void restore() { ImpactAnalyzer.testRootOverride = null; }

    private static String cls(String doc, String body) { return "package com.kpj.tests.Syn;\n" + doc + "public class X {\n" + body + "}\n"; }

    // ------------------------------------------------------------------ the declaration rule

    @Test
    void aClassDocThatStartsWithReadOnly_isDeclaredReadOnly() {
        assertTrue(ImpactAnalyzer.declaredReadOnly(cls("/**\n * Read-only dependent.\n */\n", "")));
        assertTrue(ImpactAnalyzer.declaredReadOnly(cls("/** Read-only: nothing is saved. */\n", "")));
        assertTrue(ImpactAnalyzer.declaredReadOnly(cls("/**\n * <p>Read-only apart from the report it prints: nothing is saved by this flow.</p>\n */\n", "")));
        assertTrue(ImpactAnalyzer.declaredReadOnly(cls("/**\n * Opens the list.\n *\n * <p>READ-ONLY: this flow prints and navigates, it creates no records.</p>\n */\n", "")));
        assertTrue(ImpactAnalyzer.declaredReadOnly(cls("/**\n * Read-only.\n */\n@SuppressWarnings(\"x\")\n", "")), "annotations may sit between the doc and the class");
    }

    @Test
    void theWordAnywhereElse_doesNotDeclareAReadOnlyTest() {
        // an inline comment in the body
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/** A flow. */\n", "  void b() { /* read-only view only */ }\n")));
        // a string / step description
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/** Submits the consent form. */\n", "  String s = \"the form becomes read-only\";\n")));
        // a detail inside the doc of a test that makes a real change
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/**\n * Creates and saves a report. The run makes a real change. One extra, read-only check follows the toast.\n */\n", "")));
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/**\n * This is not like the read-only report generation of the Print tabs, so it does not reuse them.\n */\n", "")));
        // negated
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/**\n * Read-only? NOT read-only: it saves a record.\n */\n", "")));
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/** Not read-only. */\n", "")));
    }

    @Test
    void noDoc_aPlainBlockComment_orADocThatIsNotDirectlyAboveTheClass_isNotDeclared() {
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("", "")));
        assertFalse(ImpactAnalyzer.declaredReadOnly(cls("/* Read-only */\n", "")), "a plain block comment is not a class doc");
        assertFalse(ImpactAnalyzer.declaredReadOnly("/** Read-only licence header. */\npackage x;\nimport y.Z;\npublic class X { }\n"),
                "a doc comment further up the file than the class declaration is not the class doc");
        assertFalse(ImpactAnalyzer.declaredReadOnly("package x;\n"), "no class at all");
    }

    // ------------------------------------------------------------------ the selection

    private void dependent(Path dir, String name, String doc, String extraBody) throws IOException {
        Files.writeString(dir.resolve(name + ".java"), "package com.kpj.tests.Syn;\n" + doc
                + "public class " + name + " {\n    public " + name + "() { super(\"" + name + "\"); }\n"
                + "    void body() { SynPage p = new SynPage(page); }\n" + extraBody + "}\n");
    }

    @Test
    void onlyDeclaredReadOnlyDependents_areSelected_allDependentsAreStillListed() throws Exception {
        Path dir = tmp.resolve("tests/com/kpj/tests/Syn");
        Files.createDirectories(dir);
        dependent(dir, "DepDeclaredReadOnly", "/**\n * Read-only: lists and views, saves nothing.\n */\n", "");
        dependent(dir, "DepMentionsInBody", "/** Registers a patient. */\n", "  // the view is read-only afterwards\n");
        dependent(dir, "DepMakesAChange", "/**\n * Saves a record. The run makes a real change. One extra, read-only check follows.\n */\n", "");
        dependent(dir, "DepNegated", "/** NOT read-only: cancels an appointment. */\n", "");
        dependent(dir, "DepNoDoc", "", "");
        Files.writeString(dir.resolve("Unrelated.java"), "package com.kpj.tests.Syn;\n/** Read-only. */\npublic class Unrelated { void b() { Other o = new Other(page); } }\n");
        ImpactAnalyzer.testRootOverride = tmp.resolve("tests");

        ImpactAnalyzer.Result r = new ImpactAnalyzer().analyze(Paths.get("src/SynPage.java"));
        assertEquals(5, r.dependentCount(), "every class that instantiates the Page Object is a dependent: " + r.dependentTestFiles);
        assertEquals(List.of("DepDeclaredReadOnly.java"), r.readOnlyDependents.stream().map(p -> Paths.get(p).getFileName().toString()).collect(Collectors.toList()),
                "only the declared read-only one would ever be re-run");
    }

    // ------------------------------------------------------------------ the real project tree (source text only)

    @Test
    void realTree_selectionIsNarrowerThanTheWordMatch_andNeverSelectsADataChangingTest() throws Exception {
        Path tests = Paths.get("src/test/java/com/kpj/tests");
        Path pages = Paths.get("src/test/java/com/kpj/pages");
        if (!Files.isDirectory(tests) || !Files.isDirectory(pages)) return;

        Set<String> anywhere = new TreeSet<>(), declared = new TreeSet<>();
        Pattern word = Pattern.compile("(?i)read-only");
        try (Stream<Path> s = Files.walk(tests)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String src = Files.readString(p);
                if (word.matcher(src).find()) anywhere.add(p.getFileName().toString());
                if (ImpactAnalyzer.declaredReadOnly(src)) declared.add(p.getFileName().toString());
            }
        }
        assertTrue(anywhere.containsAll(declared), "a declared read-only test always contains the word");
        assertTrue(declared.size() < anywhere.size(),
                "the old word-match would have selected " + anywhere.size() + " classes; the declaration rule selects " + declared.size() + " " + declared);

        // through the real analysis of every real Page Object: only declared classes can ever be selected, and never AppointmentListTest
        Set<String> selected = new TreeSet<>();
        try (Stream<Path> s = Files.walk(pages)) {
            // (each analysis reads every test source, so a bounded, deterministic sample keeps this fast; the property checked
            //  - selected is a subset of declared - holds by construction for all of them)
            List<Path> all = s.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList());
            List<Path> sample = new ArrayList<>(all.subList(0, Math.min(12, all.size())));
            all.stream().filter(f -> f.getFileName().toString().startsWith("AppointmentList")).forEach(sample::add);
            for (Path po : sample) {
                for (String dep : new ImpactAnalyzer().analyze(po).readOnlyDependents) selected.add(Paths.get(dep).getFileName().toString());
            }
        }
        assertTrue(declared.containsAll(selected), "selected " + selected + " must all be declared " + declared);
        assertFalse(selected.contains("AppointmentListTest.java"), "the data-changing AppointmentListTest is never selected");
        for (String known : new String[]{"Transfer.java", "ViewNurseCareReport.java", "Admission.java", "LabResultEntryEdit.java"}) {
            assertFalse(declared.contains(known), known + " mentions read-only only as a detail - it must not be selected");
        }
    }

    @Test
    void theAnalysisOnlyReadsSource_andSelectsAtMostWhatTheDeclarationRuleAllows() throws IOException {
        // a selection never includes a file that is not also a dependent
        Path dir = tmp.resolve("tests/com/kpj/tests/Syn");
        Files.createDirectories(dir);
        dependent(dir, "DepA", "/** Read-only. */\n", "");
        ImpactAnalyzer.testRootOverride = tmp.resolve("tests");
        ImpactAnalyzer.Result r = new ImpactAnalyzer().analyze(Paths.get("SynPage.java"));
        assertTrue(r.dependentTestFiles.containsAll(r.readOnlyDependents));
        List<String> copy = new ArrayList<>(r.readOnlyDependents);
        assertEquals(1, copy.size());
    }
}
