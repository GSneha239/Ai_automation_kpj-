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
     * Render EVERY page (at most {@code maxPages}) of the PDF to a PNG, each about {@code widthPx} wide. Used to show a whole
     * multi-page report in one screenshot (Chromium's PDF viewer only shows the page in view). Empty list on failure.
     */
    public static java.util.List<byte[]> pagesToPng(byte[] pdf, int maxPages, int widthPx) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        if (pdf == null || pdf.length == 0) return out;
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int n = Math.min(doc.getNumberOfPages(), Math.max(1, maxPages));
            for (int p = 0; p < n; p++) {
                float widthPoints = doc.getPage(p).getMediaBox().getWidth();
                float dpi = widthPoints > 0 ? (72f * widthPx / widthPoints) : 100f;
                dpi = Math.max(60f, Math.min(MAX_DPI, dpi));
                BufferedImage img = renderer.renderImageWithDPI(p, dpi, ImageType.RGB);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", bos);
                out.add(bos.toByteArray());
            }
        } catch (Exception e) {
            System.out.println("PdfUtil.pagesToPng: " + e.getMessage());
        }
        return out;
    }

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
