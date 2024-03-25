package mkl.testarea.pdfbox2.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.ByteStreams;

/**
 * @author mkl
 */
public class AddImage {
    final static File RESULT_FOLDER = new File("target/test-outputs", "content");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        RESULT_FOLDER.mkdirs();
    }

    /**
     * <a href="https://stackoverflow.com/questions/49958604/draw-image-at-mid-position-using-pdfbox-java">
     * Draw image at mid position using pdfbox Java
     * </a>
     * <p>
     * This is the OP's original code. It mirrors the image.
     * This can be fixed as shown in {@link #testImageAppendNoMirror()}.
     * </p>
     */
    @Test
    public void testImageAppendLikeShanky() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("/mkl/testarea/pdfbox2/sign/test.pdf");
                InputStream imageResource = getClass().getResourceAsStream("Willi-1.jpg")   )
        {
            PDDocument doc = PDDocument.load(resource);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, ByteStreams.toByteArray(imageResource), "Willi");

            int w = pdImage.getWidth();
            int h = pdImage.getHeight();

            PDPage page = doc.getPage(0);
            PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true);

            float x_pos = page.getCropBox().getWidth();
            float y_pos = page.getCropBox().getHeight();

            float x_adjusted = ( x_pos - w ) / 2;
            float y_adjusted = ( y_pos - h ) / 2;

            Matrix mt = new Matrix(1f, 0f, 0f, -1f, page.getCropBox().getLowerLeftX(), page.getCropBox().getUpperRightY());
            contentStream.transform(mt);
            contentStream.drawImage(pdImage, x_adjusted, y_adjusted, w, h);
            contentStream.close();

            doc.save(new File(RESULT_FOLDER, "test-with-image-shanky.pdf"));
            doc.close();

        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/49958604/draw-image-at-mid-position-using-pdfbox-java">
     * Draw image at mid position using pdfbox Java
     * </a>
     * <p>
     * This is a fixed version of the the OP's original code, cf.
     * {@link #testImageAppendLikeShanky()}. It does not mirrors the image.
     * </p>
     */
    @Test
    public void testImageAppendNoMirror() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("/mkl/testarea/pdfbox2/sign/test.pdf");
                InputStream imageResource = getClass().getResourceAsStream("Willi-1.jpg")   )
        {
            PDDocument doc = PDDocument.load(resource);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, ByteStreams.toByteArray(imageResource), "Willi");

            int w = pdImage.getWidth();
            int h = pdImage.getHeight();

            PDPage page = doc.getPage(0);
            PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true);

            float x_pos = page.getCropBox().getWidth();
            float y_pos = page.getCropBox().getHeight();

            float x_adjusted = ( x_pos - w ) / 2 + page.getCropBox().getLowerLeftX();
            float y_adjusted = ( y_pos - h ) / 2 + page.getCropBox().getLowerLeftY();

            contentStream.drawImage(pdImage, x_adjusted, y_adjusted, w, h);
            contentStream.close();

            doc.save(new File(RESULT_FOLDER, "test-with-image-no-mirror.pdf"));
            doc.close();

        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/50988007/clip-an-image-with-pdfbox">
     * Clip an image with PDFBOX
     * </a>
     * <p>
     * This test demonstrates how to clip an image and frame the clipping area.
     * </p>
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testImageAddClipped() throws IOException {
        try (   InputStream imageResource = getClass().getResourceAsStream("Willi-1.jpg")   )
        {
            PDDocument doc = new PDDocument();
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, ByteStreams.toByteArray(imageResource), "Willi");

            int w = pdImage.getWidth();
            int h = pdImage.getHeight();

            PDPage page = new PDPage();
            doc.addPage(page);
            PDRectangle cropBox = page.getCropBox();
            PDPageContentStream contentStream = new PDPageContentStream(doc, page);

            contentStream.setStrokingColor(25, 200, 25);
            contentStream.setLineWidth(4);
            contentStream.moveTo(cropBox.getLowerLeftX(), cropBox.getLowerLeftY() + h/2);
            contentStream.lineTo(cropBox.getLowerLeftX() + w/3, cropBox.getLowerLeftY() + 2*h/3);
            contentStream.lineTo(cropBox.getLowerLeftX() + w, cropBox.getLowerLeftY() + h/2);
            contentStream.lineTo(cropBox.getLowerLeftX() + w/3, cropBox.getLowerLeftY() + h/3);
            contentStream.closePath();
            //contentStream.clip();
            contentStream.appendRawCommands("W ");
            contentStream.stroke();

            contentStream.drawImage(pdImage, cropBox.getLowerLeftX(), cropBox.getLowerLeftY(), w, h);

            contentStream.close();

            doc.save(new File(RESULT_FOLDER, "image-clipped.pdf"));
            doc.close();
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/78216277/draw-transparent-png-image-to-pdf-using-pdfbox-and-seeing-gray-halo-around-the-e">
     * Draw transparent png image to pdf using pdfbox and seeing gray halo around the edges
     * </a>
     * <br/>
     * <a href="https://i.stack.imgur.com/ZmX0D.png">
     * Glow.png
     * </a>
     * <br/>
     * GlowColorExtended.png (Glow.png with light pink instead of black in the transparent areas)
     * <p>
     * The rendering artifacts don't occur for the image in which the light pink continues into
     * the transparent areas instead of being replaced by black there.
     * </p>
     */
    @Test
    public void testImageWithTransparency() throws IOException {
        PDDocument doc = new PDDocument();
        PDImageXObject glowImage;
        PDImageXObject glowColorExtendedImage;
        try (InputStream imageResource = getClass().getResourceAsStream("Glow.png")) {
            glowImage = PDImageXObject.createFromByteArray(doc, ByteStreams.toByteArray(imageResource), "Glow");
        }
        try (InputStream imageResource = getClass().getResourceAsStream("GlowColorExtended.png")) {
            glowColorExtendedImage = PDImageXObject.createFromByteArray(doc, ByteStreams.toByteArray(imageResource), "GlowColorExtended");
        }

        PDPage page = new PDPage();
        doc.addPage(page);
        PDRectangle cropBox = page.getCropBox();
        PDPageContentStream contentStream = new PDPageContentStream(doc, page);

        contentStream.drawImage(glowImage, cropBox.getLowerLeftX(), cropBox.getLowerLeftY() + cropBox.getHeight()/2, 1300*0.3078f, 874*0.3078f);

        contentStream.drawImage(glowColorExtendedImage, cropBox.getLowerLeftX(), cropBox.getLowerLeftY(), 1300*0.3078f, 874*0.3078f);

        contentStream.close();

        doc.save(new File(RESULT_FOLDER, "image-withTransparency.pdf"));
        doc.close();
    }
}
