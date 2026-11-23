package com.kpj.heal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests for {@link CodeLocator}'s Page Object mapping. Test and Page Object sources are written
 * into a temp folder and the locator is pointed at it, so nothing touches the real source tree, a browser or DevHIS.
 *
 * <p>Focus: a Page Object created through a factory method ({@code newListPage()}) must map exactly like a directly
 * constructed one, while direct-constructor mapping and the "refuse rather than guess" cases stay unchanged.</p>
 */
class CodeLocatorTest {

    @TempDir
    Path root;

    private static final String STEP = "Select an appointment";

    private void write(String relPath, String content) throws IOException {
        Path p = root.resolve(relPath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private void pageObject(String simple, String methods) throws IOException {
        write("com/kpj/pages/Syn/" + simple + ".java",
                "package com.kpj.pages.Syn;\npublic class " + simple + " {\n    public " + simple + "(Object page) { }\n" + methods + "}\n");
    }

    private CodeLocator.CodeLocation locate(String testClassSimple) {
        return new CodeLocator(root).locate("com.kpj.tests.Syn." + testClassSimple, STEP);
    }

    // ------------------------------------------------------------------ factory-created page objects

    @Test
    void factoryCreatedPageObject_isMappedToItsMethod() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynFactoryTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynFactoryTest {\n"
                        + "    private SynListPage listPage;\n"
                        + "    protected SynListPage newListPage() { return new SynListPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = newListPage();\n"
                        + "        String appt = listPage.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"desc\", \"expected\", \"actual\", \"PASS\");\n"
                        + "    }\n}\n");
        CodeLocator.CodeLocation loc = locate("SynFactoryTest");
        assertNotNull(loc, "a factory-created page object must now resolve");
        assertEquals("listPage", loc.variableName);
        assertEquals("selectFirst", loc.methodName);
        assertTrue(loc.pageObjectFile.toString().endsWith("SynListPage.java"));
        assertTrue(loc.methodSource.contains("selectFirst"));
    }

    @Test
    void fullyQualifiedFactory_isMapped() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynFqnFactoryTest.java",
                "package com.kpj.tests.Syn;\npublic class SynFqnFactoryTest {\n"
                        + "    protected com.kpj.pages.Syn.SynListPage newListPage() { return new com.kpj.pages.Syn.SynListPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = newListPage();\n"
                        + "        listPage.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        CodeLocator.CodeLocation loc = locate("SynFqnFactoryTest");
        assertNotNull(loc);
        assertEquals("selectFirst", loc.methodName);
    }

    // ------------------------------------------------------------------ existing behaviour must not change

    @Test
    void directDeclaration_isStillMapped() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynDirectTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynDirectTest {\n"
                        + "    protected void body() {\n"
                        + "        SynListPage lp = new SynListPage(page);\n"
                        + "        lp.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        CodeLocator.CodeLocation loc = locate("SynDirectTest");
        assertNotNull(loc);
        assertEquals("lp", loc.variableName);
        assertEquals("selectFirst", loc.methodName);
    }

    @Test
    void directDeclarationWinsOverAFactoryAssignmentForTheSameVariable() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        pageObject("SynOtherPage", "    public String selectFirst() { return \"other\"; }\n");
        write("com/kpj/tests/Syn/SynBothTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\nimport com.kpj.pages.Syn.SynOtherPage;\npublic class SynBothTest {\n"
                        + "    protected SynOtherPage newOther() { return new SynOtherPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        SynListPage p = new SynListPage(page);\n"
                        + "        p = newOther();\n"
                        + "        p.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        CodeLocator.CodeLocation loc = locate("SynBothTest");
        assertNotNull(loc);
        assertTrue(loc.pageObjectFile.toString().endsWith("SynListPage.java"), "the direct declaration keeps priority");
    }

    // ------------------------------------------------------------------ refuse rather than guess

    @Test
    void factoryWhoseReturnTypeDiffersFromTheConstructedType_isNotMapped() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynMismatchTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynMismatchTest {\n"
                        + "    protected Object newListPage() { return new SynListPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = newListPage();\n"
                        + "        listPage.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        assertNull(locate("SynMismatchTest"), "an inexact factory shape is never guessed at");
    }

    @Test
    void assignmentFromANonFactoryMethod_isNotMapped() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynNonFactoryTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynNonFactoryTest {\n"
                        + "    protected SynListPage findIt() { return lookup(); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = findIt();\n"
                        + "        listPage.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        assertNull(locate("SynNonFactoryTest"));
    }

    @Test
    void stepWithNoPageObjectCallNearby_isStillUnmapped() throws IOException {
        pageObject("SynListPage", "    public String selectFirst() { return \"x\"; }\n");
        write("com/kpj/tests/Syn/SynNoCallTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynNoCallTest {\n"
                        + "    protected SynListPage newListPage() { return new SynListPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = newListPage();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        assertNull(locate("SynNoCallTest"), "no call on a page object variable near the step means nothing to map");
    }

    // ------------------------------------------------------------------ documented limitation (not changed here)

    @Test
    void knownLimitation_overloadedMethod_resolvesToTheFirstDeclaration() throws IOException {
        pageObject("SynListPage",
                "    public String selectFirst() { return selectFirst(0); }\n"
                        + "    public String selectFirst(int skip) {\n        String js = \"ROW_SELECTOR_JS_MARKER\";\n        return js;\n    }\n");
        write("com/kpj/tests/Syn/SynOverloadTest.java",
                "package com.kpj.tests.Syn;\nimport com.kpj.pages.Syn.SynListPage;\npublic class SynOverloadTest {\n"
                        + "    protected SynListPage newListPage() { return new SynListPage(page); }\n"
                        + "    protected void body() {\n"
                        + "        listPage = newListPage();\n"
                        + "        listPage.selectFirst();\n"
                        + "        step(\"" + STEP + "\", \"d\", \"e\", \"a\", \"PASS\");\n"
                        + "    }\n}\n");
        CodeLocator.CodeLocation loc = locate("SynOverloadTest");
        assertNotNull(loc);
        // Documents CURRENT behaviour: the one-line delegating overload is returned, not the overload that holds the
        // real logic. If extractMethod() is ever taught to follow delegation, this expectation should be updated.
        assertTrue(loc.methodSource.contains("return selectFirst(0)"));
        assertFalse(loc.methodSource.contains("ROW_SELECTOR_JS_MARKER"));
    }

    @Test
    void defaultConstructor_stillTargetsTheRealSourceRoot() {
        assertNull(new CodeLocator().locate("does.not.Exist", "any step"), "no such test source under src/test/java");
    }
}
