package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ImageManagement — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Image Management</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Image Management</b>, clicking
 *       <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b>, select <b>Image Category*</b>, upload an <b>Image</b>, enter
 *       <b>Image Name*</b> and <b>Icon Name*</b>, and upload an <b>Icon Image</b>.</li>
 *   <li>Click the inner <b>Add</b> to append the row to the detail grid.</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Save again.</li>
 * </ol>
 */
public class ImageManagement extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    /** Both uploads use a real image from the machine's Screenshots folder (see DevHisBase.ATTACH_DIR). */
    private static final String IMAGE_PATH = ATTACH.toString();
    private static final String ICON_IMAGE_PATH = ATTACH2.toString();

    public ImageManagement() { super("ApplicationConfig_Nursing_ImageManagement"); }

    public static void main(String[] args) {
        ImageManagement t = new ImageManagement();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Image Management",
                "Application Configuration > Nursing > Image Management",
                "Click Add, enter Code + Remark + Image Category + Image (upload) + Image Name + Icon Name + "
                        + "Icon Image (upload), click Add (detail row), click Save; wait for the success toast. On "
                        + "'already exists', change the details and Save again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ImageManagement im =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ImageManagement(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = im.navigateViaMenu();
        String landed = im.currentScreen();
        step(page, "Open Image Management screen", "Application Configuration -> Nursing -> Image Management",
                "The Image Management screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Image Management but the app opened: " + landed + "\n" + im.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = im.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark / image form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code / Remark.
        String codeRemark = im.fillCodeAndRemark(0);
        boolean codeOk = codeRemark.contains("Code=IM") && !codeRemark.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", codeRemark, codeOk ? "PASS" : "FAIL");

        // 4) Image Category.
        String category = im.selectImageCategory();
        boolean catOk = category != null && !category.startsWith("(no");
        step(page, "Select Image Category", "Select Image Category*",
                "An Image Category is selected", category, catOk ? "PASS" : "MANUAL");

        // 5) Upload Image.
        String imgUpload = im.uploadImage(IMAGE_PATH);
        boolean imgUploadOk = imgUpload != null && imgUpload.startsWith("Image uploaded");
        step(page, "Upload Image", "Upload an Image from " + IMAGE_PATH,
                "The Image is attached", imgUpload, imgUploadOk ? "PASS" : "MANUAL");

        // 6) Image Name + Icon Name.
        String names = im.fillImageAndIconName(0);
        boolean namesOk = names != null && !names.contains("(no field)");
        step(page, "Enter Image Name and Icon Name", "Enter Image Name* and Icon Name*",
                "Image Name and Icon Name are entered", names, namesOk ? "PASS" : "FAIL");

        // 7) Upload Icon Image.
        String iconUpload = im.uploadIconImage(ICON_IMAGE_PATH);
        boolean iconUploadOk = iconUpload != null && iconUpload.startsWith("Icon Image uploaded");
        step(page, "Upload Icon Image", "Upload an Icon Image from " + ICON_IMAGE_PATH,
                "The Icon Image is attached", iconUpload, iconUploadOk ? "PASS" : "MANUAL");

        byte[] filledPng = null;
        try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("ImageManagement: filled-form screenshot failed - " + e.getMessage()); }

        // 8) Click the inner Add (appends the row to the detail grid).
        String innerAdd = im.clickInnerAdd();
        boolean innerAddOk = innerAdd != null && innerAdd.equals("inner Add clicked");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Click Add (detail row)", "Click the inner Add to append the row to the detail grid",
                    "A row is added to the detail grid", innerAdd, innerAddOk ? "PASS" : "MANUAL");
        } else {
            step(page, "Click Add (detail row)", "Click the inner Add to append the row to the detail grid",
                    "A row is added to the detail grid", innerAdd, innerAddOk ? "PASS" : "MANUAL");
        }

        // 9) Save — retry with different Code/Remark on "already exists".
        String toast = "";
        boolean ok = false, exists = false;
        int used = 1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                im.fillCodeAndRemark(attempt);
                used = attempt + 1;
            }
            toast = im.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("ImageManagement: attempt " + used + " already exists — changing the details");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + im.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + im.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (im.toastPng != null && im.toastPng.length > 0) {
            step(im.toastPng, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Image Management Code", im.lastCode);
        addSummary("Remark", im.lastRemark);
        addSummary("Image Category", im.lastCategory);
        addSummary("Image Name", im.lastImageName);
        addSummary("Icon Name", im.lastIconName);
        addSummary("Field models", im.lastCodeModel + " / " + im.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
