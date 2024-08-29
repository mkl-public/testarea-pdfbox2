package mkl.testarea.pdfbox2.content;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.BeforeClass;
import org.junit.Test;

public class DrawPolygons {
    final static File RESULT_FOLDER = new File("target/test-outputs", "content");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        RESULT_FOLDER.mkdirs();
    }

    /**
     * <a href="https://stackoverflow.com/questions/78926944/there-is-a-stroke-parameter-for-define-where-draw-stroke-on-pdfbox">
     * There is a stroke parameter for define where draw stroke on PDFBOX?
     * </a>
     * <p>
     * This shows how to achieve an effect similar to <code>StrokeType.INSIDE</code>
     * and <code>StrokeType.OUTSIDE</code> in JavaFX when drawing a polygon.
     * </p>
     */
    @Test
    public void testDrawForGuyard() throws IOException {
        List<Consumer<PDPageContentStream>> examples = List.of(DrawPolygons::drawPolygonCenterStroke,
                DrawPolygons::drawPolygonInsideStroke, DrawPolygons::drawPolygonOutsideStroke);

        try (   PDDocument document = new PDDocument(); ) {
            for (Consumer<PDPageContentStream> example : examples) {
                PDPage page = new PDPage(new PDRectangle(0, 0, 300, 300));
                page.setResources(new PDResources());
                document.addPage(page);

                try (   PDPageContentStream canvas = new PDPageContentStream(document, page)) {
                    canvas.setNonStrokingColor(.5f, .5f, 1f);
                    canvas.setStrokingColor(0f, 0f, 0f);
                    canvas.setLineWidth(30);

                    example.accept(canvas);
                }
            }

            document.save(new File(RESULT_FOLDER, "DrawPolygonsForGuyard.pdf"));
        }
    }

    /** @see #testDrawForGuyard() */
    static void drawPolygonCenterStroke(PDPageContentStream canvas) {
        try {
            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.closeAndFillAndStroke();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /** @see #testDrawForGuyard() */
    static void drawPolygonInsideStroke(PDPageContentStream canvas) {
        try {
            canvas.saveGraphicsState();

            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.closePath();
            canvas.clip();

            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.closeAndFillAndStroke();

            canvas.restoreGraphicsState();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /** @see #testDrawForGuyard() */
    static void drawPolygonOutsideStroke(PDPageContentStream canvas) {
        try {
            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.fill();

            canvas.saveGraphicsState();

            canvas.addRect(0, 0, 300, 300);
            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.closePath();
            canvas.clipEvenOdd();

            canvas.moveTo(50, 150);
            canvas.lineTo(150, 50);
            canvas.lineTo(250, 150);
            canvas.lineTo(150, 250);
            canvas.closeAndStroke();

            canvas.restoreGraphicsState();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
}
