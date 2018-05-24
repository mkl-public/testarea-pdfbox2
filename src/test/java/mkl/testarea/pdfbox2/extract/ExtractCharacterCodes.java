package mkl.testarea.pdfbox2.extract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author mkl
 */
public class ExtractCharacterCodes {
    final static File RESULT_FOLDER = new File("target/test-outputs", "extract");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        RESULT_FOLDER.mkdirs();
    }

    /**
     * <a href="https://stackoverflow.com/questions/50149033/how-to-get-the-glyphs-of-a-pdf-file-using-java-scala">
     * How to get the glyphs of a pdf file using java/scala?
     * </a>
     * <br/>
     * <a href="https://1drv.ms/b/s!AmHcFaD-gMGyhipy6feWmHK7Ea-P">
     * singNepChar.pdf
     * </a>
     * <p>
     * This test shows how to access the character codes of the extracted text.
     * </p>
     */
    @Test
    public void testExtractFromSingNepChar() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("singNepChar.pdf")    )
        {
            PDDocument document = PDDocument.load(resource);
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    for (TextPosition textPosition : textPositions) {
                        writeString(String.format("%s%s", textPosition.getUnicode(), Arrays.toString(textPosition.getCharacterCodes())));
                    }
                }
                
            };
            //stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            System.out.printf("\n*\n* singNepChar.pdf\n*\n%s\n", text);
            Files.write(new File(RESULT_FOLDER, "singNepChar.txt").toPath(), Collections.singleton(text));
        }
    }

}