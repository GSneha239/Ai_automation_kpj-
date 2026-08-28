package com.kpj.pages.Emegency_Page;

import com.kpj.pages.Op_page.RegistrationPage;
import com.microsoft.playwright.Page;

/**
 * Emergency &gt; <b>Emergency Registration (Conscious)</b> — Page Object (route
 * {@code #/EmergencyRegistrationConsious}, the app's own spelling "Consious").
 *
 * <p>A conscious emergency patient gives their full details, so this screen is the SAME form as OP
 * Registration — identical {@code Registration.*}/{@code Visit.*} ng-models, the same element IDs
 * ({@code #txtFirstName}, {@code #PhotoData} …) and the same {@code IUDRegistration()} save that reaches the
 * "Do You Want To Save" confirm, the PDPA Consent modal and the RegistrationReport tabs. It therefore
 * <b>extends {@link RegistrationPage}</b> to inherit every section-fill / save / consent / report method, and
 * only overrides {@link #open(String)} to navigate to the emergency route instead of {@code #/VisitScreen}.</p>
 */
public class EmergencyRegistrationConscious extends RegistrationPage {

    /** In-app hash route for Emergency Registration (Conscious) — note the app's misspelling. */
    public static final String ROUTE = "#/EmergencyRegistrationConsious";

    public EmergencyRegistrationConscious(Page page) { super(page); }

    /** Open Emergency Registration (Conscious) and wait for the form + dropdown master-data. */
    @Override
    public boolean open(String baseUrl) {
        return openRoute(ROUTE);
    }
}
