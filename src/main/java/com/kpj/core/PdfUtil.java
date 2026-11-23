package com.kpj.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Rasterizes a PDF's first page to a PNG so server-generated PDF reports (e.g. the MRD file label at
 * {@code frmMRD.aspx?IsLabel=true}) can be embedded in the HTML report. Chromium's native PDF viewer
 * screenshots as a blank gray page, so instead we fetch the PDF bytes and render page 1 ourselves.
 */
public final class PdfUtil {
    private PdfUtil() {}

    /** Every rasterized page is rendered to about this width, so report images share one scale. */
    private static final int TARGET_WIDTH_PX = 1920;
    private static final float MIN_DPI = 96f, MAX_DPI = 600f;

    /**
     * Render page 1 of the given PDF bytes to a PNG byte[]. Returns null on failure.
     *
     * <p>The DPI is chosen so the page comes out ~{@value #TARGET_WIDTH_PX}px wide instead of a fixed 150: a
     * wrist band / patient label is a physically tiny page, so at 150 DPI it rasterized to 704x195 and sat in the
     * report beside 1920x1075 screenshots looking blown up. A PDF is vector, so re-rendering at a higher DPI is
     * SHARP — this is not an upscale.</p>
     */
    public static byte[] firstPageToPng(byte[] pdf) {
        if (pdf == null || pdf.length == 0) return null;
        try (PDDocument doc = PDDocument.load(pdf)) {
            if (doc.getNumberOfPages() == 0) return null;
            PDFRenderer renderer = new PDFRenderer(doc);
            float widthPoints = doc.getPage(0).getMediaBox().getWidth();     // 1pt = 1/72 inch
            float dpi = widthPoints > 0 ? (72f * TARGET_WIDTH_PX / widthPoints) : 150f;
            dpi = Math.max(MIN_DPI, Math.min(MAX_DPI, dpi));
            BufferedImage img = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            System.out.println("PdfUtil.firstPageToPng: " + e.getMessage());
            return null;
        }
    }
}
