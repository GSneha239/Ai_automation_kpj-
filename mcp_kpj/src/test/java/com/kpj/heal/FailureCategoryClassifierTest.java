package com.kpj.heal;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safe, LOCAL, SYNTHETIC tests for the dropdown rule of {@link FailureCategoryClassifier}. Failure evidence and the
 * located Page Object method are constructed in memory: no browser, no files, no DevHIS.
 *
 * <p>Central guarantee: a message about choosing a grid ROW ("No appointment to select") is not called a dropdown failure
 * merely because it contains the word "select" on a screen that also has filter dropdowns - while genuine dropdown
 * evidence ("dropdown", "option", a select that was not selected) is still classified as a dropdown failure.</p>
 */
class FailureCategoryClassifierTest {

    /** The Appointment List's filter dropdowns - the reason a "select evidence" flag exists on that screen at all. */
    private static final String SCREEN_SELECTS =
            "PatientData.LocationID:KPJ DAMANSARA SPECIALIST HOSPITAL;PatientData.DepartmentID:-Select-;PatientData.DoctorID:-Select-";
    private static final String SCREEN_SELECT_OPTIONS =
            "PatientData.DepartmentID:-Select-~CARDIOLOGY~ORTHOPAEDICS;PatientData.DoctorID:-Select-~Dr A~Dr B";

    private static FailureRecord failure(String step, String expected, String actual, boolean withSelectEvidence) {
        FailureRecord f = new FailureRecord("SYN_TEST", "Synthetic page", step, expected, actual);
        f.url = "https://example.invalid/#/AppointmentList";          // never contacted - just evidence text
        f.buttons = "Search|Request MRD File|View App History";
        f.headings = "Appointment Details";
        if (withSelectEvidence) { f.selects = SCREEN_SELECTS; f.selectOptions = SCREEN_SELECT_OPTIONS; }
        return f;
    }

    /** The located method: the one-line delegating overload CodeLocator resolves today - no select control in it. */
    private static CodeLocator.CodeLocation rowHelper() {
        return new CodeLocator.CodeLocation(
                Paths.get("src/test/java/com/kpj/tests/Syn/SynTest.java"),
                Paths.get("src/test/java/com/kpj/pages/Syn/SynListPage.java"),
                "listPage", "selectFirstAppointment",
                "    public String selectFirstAppointment() { return selectFirstAppointment(0); }\n", "");
    }

    private static CodeLocator.CodeLocation dropdownHelper() {
        return new CodeLocator.CodeLocation(
                Paths.get("src/test/java/com/kpj/tests/Syn/SynTest.java"),
                Paths.get("src/test/java/com/kpj/pages/Syn/SynListPage.java"),
                "listPage", "chooseStore",
                "    public String chooseStore() { return \"x\"; }\n", "");
    }

    private static FailureCategoryClassifier.Result classify(FailureRecord f, CodeLocator.CodeLocation loc) {
        return new FailureCategoryClassifier().classify(f, loc);
    }

    // ------------------------------------------------------------------ the appointment-row false positive

    @Test
    void noAppointmentToSelect_isNotADropdownFailure() {
        FailureCategoryClassifier.Result r = classify(
                failure("History · Select an appointment", "One appointment selected", "No appointment to select", true), rowHelper());
        assertTrue(r.category != FailureCategory.DROPDOWN_FAILURE, "a grid-row selection message is not a dropdown failure");
        assertEquals(FailureCategory.UNKNOWN_NEEDS_MANUAL_INVESTIGATION, r.category, "the cause stays undetermined");
        assertFalse(r.selfHealingEligible, "and nothing is made eligible for healing by this change");
    }

    @Test
    void theUndeterminedRootCauseDoesNotMentionADropdown() {
        FailureCategoryClassifier.Result r = classify(
                failure("History · Select an appointment", "One appointment selected", "No appointment to select", true), rowHelper());
        assertFalse(r.rootCause.toLowerCase().contains("dropdown"), "no unsupported dropdown story is told");
        assertTrue(r.rootCause.contains("No deterministic rule matched"), "root cause is reported as not established");
    }

    @Test
    void otherNoThingToSelectWordings_areNotDropdownFailuresEither() {
        for (String actual : new String[]{"No row to select", "No patient record to select", "NO APPOINTMENT TO SELECT"}) {
            FailureCategoryClassifier.Result r = classify(failure("Select a row", "A row is selected", actual, true), rowHelper());
            assertTrue(r.category != FailureCategory.DROPDOWN_FAILURE, "'" + actual + "' must not be a dropdown failure");
        }
    }

    // ------------------------------------------------------------------ genuine dropdown failures are preserved

    @Test
    void aSelectThatWasNotSelected_isStillADropdownFailure() {
        FailureCategoryClassifier.Result r = classify(
                failure("Select Store", "A Store is selected", "Store NOT selected (no-choices)", true), dropdownHelper());
        assertEquals(FailureCategory.DROPDOWN_FAILURE, r.category);
        assertFalse(r.selfHealingEligible);
    }

    @Test
    void aMissingOptionInADropdown_isStillADropdownFailure() {
        FailureCategoryClassifier.Result r = classify(
                failure("Select Department", "Department is chosen", "The Department dropdown does not list the option Cardiology", true),
                dropdownHelper());
        assertEquals(FailureCategory.DROPDOWN_FAILURE, r.category);
    }

    @Test
    void noOptionToSelect_isStillADropdownFailure_becauseItNamesAnOption() {
        FailureCategoryClassifier.Result r = classify(
                failure("Select Doctor", "A Doctor is chosen", "No option to select", true), dropdownHelper());
        assertEquals(FailureCategory.DROPDOWN_FAILURE, r.category, "the guard only removes the bare word 'select'");
    }

    // ------------------------------------------------------------------ unchanged behaviour

    @Test
    void withoutAnySelectEvidence_theSelectWordNeverMakesADropdownFailure() {
        FailureCategoryClassifier.Result r = classify(
                failure("Select Store", "A Store is selected", "Store NOT selected (no-choices)", false), dropdownHelper());
        assertTrue(r.category != FailureCategory.DROPDOWN_FAILURE, "this rule has always required select evidence");
    }
}
