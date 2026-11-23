package com.kpj.heal;

/**
 * The 12 business-facing failure categories the Failure Analysis Agent classifies every failed step into -
 * distinct from, and richer than, {@link FailureClassifier}'s internal 5-way technical classification (which
 * only decides whether an automatic patch may ever be attempted). These categories are written for a human -
 * a project manager, manual tester, developer, or automation tester - reading a report without needing to
 * read a Java stack trace.
 */
public enum FailureCategory {
    MISSING_TEST_DATA("Missing Test Data"),
    TEST_DATA_ISSUE("Test Data Issue"),
    DROPDOWN_FAILURE("Dropdown Failure"),
    BUTTON_UI_FAILURE("Button/UI Failure"),
    LOCATOR_SELECTOR_FAILURE("Locator/Selector Failure"),
    VALIDATION_FAILURE("Validation Failure"),
    APPLICATION_BUG("Application Bug"),
    ENVIRONMENT_CONFIGURATION_ISSUE("Environment/Configuration Issue"),
    AUTHENTICATION_PERMISSION_ISSUE("Authentication/Permission Issue"),
    NETWORK_INFRASTRUCTURE_ISSUE("Network/Infrastructure Issue"),
    AUTOMATION_SCRIPT_ISSUE("Automation Script Issue"),
    UNKNOWN_NEEDS_MANUAL_INVESTIGATION("Unknown / Needs Manual Investigation");

    private final String displayName;

    FailureCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
