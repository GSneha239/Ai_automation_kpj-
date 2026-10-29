package com.kpj.heal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, SYNTHETIC tests of the audit trail's redaction: actual secrets (secret-named assignments, bearer tokens, JWTs, opaque keys,
 * literals typed into secret-named fields) are replaced by their value only, while ordinary text that merely CONTAINS a word such
 * as "auth" or "credentials" - e.g. the Failure Analysis category "Authentication/Permission Issue" - is kept intact.
 */
class AuditRedactionTest {

    @TempDir
    Path tmp;

    private static String s(String in) { return HealingAuditTrail.sanitize(in); }

    // ------------------------------------------------------------------ legitimate text is kept

    @Test
    void failureAnalysisCategoriesAndExplanations_areNotMasked() {
        for (FailureCategory c : FailureCategory.values()) {
            assertEquals(c.displayName(), s(c.displayName()), c.name());
        }
        String[] legit = {
                "Authentication/Permission Issue",
                "Failure Analysis marks this step not eligible for automatic healing (category: Authentication/Permission Issue)",
                "Authentication/permission failures are never automated - credentials and login logic are never touched.",
                "Verify test account credentials, permissions, and counter/organization selection with the environment/admin team.",
                "An approval covers only the proposal it names; the author of the change authorizes it",
                "the failure is now classified as an infrastructure, sign-in or network problem - never auto-fixed.",
                "Enter password",
                "Token expired banner is shown",
                "Cookie consent banner dismissed",
        };
        for (String text : legit) assertEquals(text, s(text), "must not be masked: " + text);
    }

    @Test
    void ordinaryIdentifiersPathsAndLongNames_areKept() {
        String[] legit = {
                "TC06_AppointmentList_20261023_141505",
                "C:\\Users\\Asus\\AppData\\Local\\Temp\\junit3998035897708603635\\src\\SynPage.java",
                "D:/Hanodale Project/Ai_automation_kpj_updated_22Oct/Ai_automation_kpj-/mcp_kpj/src/test/java/com/kpj/tests/Op_Page/Appointment/AppointmentListTest.java",
                "ApplicationConfigurationLocationHospitalLocationRegistrationManagerImplementation",   // 80 letters, no digits
                "SYN_TEST__Probe_Location_selector.properties",
                "expected=Login page shown | actual=Login page shown",
        };
        for (String text : legit) assertEquals(text, s(text), "must not be masked: " + text);
    }

    // ------------------------------------------------------------------ actual secrets are removed

    private static void assertRedacted(String input, String secret) {
        String out = s(input);
        assertFalse(out.contains(secret), "secret must not survive: " + out);
        assertTrue(out.contains("[REDACTED]"), "value is replaced by a marker: " + out);
    }

    @Test
    void secretNamedAssignments_loseTheirValue_butKeepTheirName() {
        assertRedacted("String password = \"Hunter2!\";", "Hunter2!");
        assertTrue(s("String password = \"Hunter2!\";").startsWith("String password = [REDACTED]"));
        assertRedacted("{\"apiKey\": \"abc123XYZ\"}", "abc123XYZ");
        assertRedacted("token=abcdef123456", "abcdef123456");
        assertRedacted("client_secret: s3cr3t-value", "s3cr3t-value");
        assertRedacted("PWD='open sesame'", "open sesame");
        assertRedacted("Set-Cookie: sessionid=zzz999", "zzz999");
        assertTrue(s("login failed for user; password = abc").contains("login failed for user"), "surrounding text survives");
    }

    @Test
    void bearerTokensJwtsAndOpaqueKeys_areRemoved() {
        assertRedacted("Authorization: Bearer abcdefghijklmnop1234", "abcdefghijklmnop1234");
        assertRedacted("header was eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fw", "eyJhbGciOiJIUzI1NiJ9");
        assertRedacted("key sk9f8a7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1", "sk9f8a7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1");   // mixed letters+digits, 40 chars
        assertRedacted("hash " + "ab12".repeat(16), "ab12ab12ab12ab12ab12ab12ab12ab12");                                      // 64 hex chars
    }

    @Test
    void literalsTypedIntoSecretNamedFields_areRemoved() {
        String out = s("page.fill(\"#password\", \"S3cret!pw\");");
        assertFalse(out.contains("S3cret!pw"), out);
        assertTrue(out.contains("#password"), "the field name is kept: " + out);
        assertFalse(s("fill('apiKeyInput', 'live_abc')").contains("live_abc"));
    }

    // ------------------------------------------------------------------ the audit file itself

    @Test
    void theAuditTrail_keepsLegitimateReasons_andNeverWritesASecret() throws IOException {
        HealingAttemptRecord r = new HealingAttemptRecord();
        r.testId = "T1";
        r.step = "Login step";
        r.finalStatus = "NEEDS_TRIAGE";
        r.reason = "Failure Analysis marks this step not eligible for automatic healing (category: Authentication/Permission Issue)";
        r.newSnippet = "String password = \"Hunter2!\";";
        new HealingAuditTrail(tmp.toString()).append(r);
        String file = Files.readString(tmp.resolve("failures").resolve("healing-history.tsv"));
        assertTrue(file.contains("category: Authentication/Permission Issue"), "the category reaches the audit trail");
        assertFalse(file.contains("Hunter2!"), "the secret never does");
        assertTrue(file.contains("String password = [REDACTED]"));
    }
}
