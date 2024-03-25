package mkl.testarea.pdfbox2.merge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author mkl
 */
public class OptimizeAfterMerge {
    final static File RESULT_FOLDER = new File("target/test-outputs", "merge");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        RESULT_FOLDER.mkdirs();
    }

    /**
     * <a href="https://stackoverflow.com/questions/53420344/ho-to-reduce-the-size-of-merged-pdf-a1-b-files-with-pdfbox-or-other-java-library">
     * Ho to reduce the size of merged PDF A1/b Files with pdfbox or other java library
     * </a>
     * <br/>
     * <a href="https://datentransfer.sparkassenverlag.de/my/transfers/5q8eskgne52npemx8kid7728zk1hq3f993dfat8his">
     * dummy.pdf
     * </a>
     * <p>
     * This test applies the method {@link #optimize(PDDocument)} to the
     * document supplied by the OP to demonstrate how to remove identical
     * duplicates (in particular streams) using PDFBox.
     * </p>
     */
    @Test
    public void testOptimizeDummy() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("dummy.pdf")  ) {
            PDDocument pdDocument = PDDocument.load(resource);

            optimize(pdDocument);

            pdDocument.save(new File(RESULT_FOLDER, "dummy-optimized.pdf"));
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/54978922/why-compress-pdf-programatically-is-undocumented-and-difficult">
     * why compress pdf programatically is undocumented and difficult
     * </a>
     * <br/>
     * <a href="https://drive.google.com/open?id=1K5gPlB1JbytWj7KMD4V09aU2R6jFoym-">
     * merged.pdf
     * </a> as "mergedBee.pdf".
     * <p>
     * {@link #optimize(PDDocument)} compresses the 1.4MB source file
     * to 250KB. 
     * </p>
     */
    @Test
    public void testOptimizeMergedBee() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("mergedBee.pdf")  ) {
            PDDocument pdDocument = PDDocument.load(resource);

            optimize(pdDocument);

            pdDocument.save(new File(RESULT_FOLDER, "mergedBee-optimized.pdf"));
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/53420344/ho-to-reduce-the-size-of-merged-pdf-a1-b-files-with-pdfbox-or-other-java-library">
     * Ho to reduce the size of merged PDF A1/b Files with pdfbox or other java library
     * </a>
     * <p>
     * This method attempts to identify identical objects in a PDF
     * and remove all but a single instance of them. This is meant
     * to target in particular streams but other complex objects
     * (arrays and dictionaries) are also affected.
     * </p>
     * <p>
     * This method might be a bit over-eager in its removal. E.g.
     * in case of separate pages Adobe Reader expects different
     * objects for different pages. This method, though, might
     * collaps identically built pages to a single objects. Thus,
     * this method probably needs to be tamed a bit.
     * </p>
     * @see #testOptimizeDummy()
     */
    public void optimize(PDDocument pdDocument) throws IOException {

        // differ page a bit to reassure we handle multiple identical page objets
        for (int i = 0; i < pdDocument.getPages().getCount(); i++) {
            PDPage page = pdDocument.getPages().get(i);
            COSDictionary customDict = new COSDictionary();
            customDict.setInt("CustomCounter", i + 1);
            page.getCOSObject().setItem("CustomCounterEntry", customDict);
        }
        
        Map<COSBase, Collection<Reference>> complexObjects = findComplexObjects(pdDocument);
        for (int pass = 0; ; pass++) {
            int merges = mergeDuplicates(complexObjects);
            if (merges <= 0) {
                System.out.printf("Pass %d - No merged objects\n\n", pass);
                break;
            }
            System.out.printf("Pass %d - Merged objects: %d\n\n", pass, merges);
        }
    }

    /**
     * Helper method for {@link #optimize(PDDocument)} - the method
     * collects complex objects (arrays, dictionaries, streams) in
     * a document and the references to it.
     * 
     * @see #testOptimizeDummy()
     */
    Map<COSBase, Collection<Reference>> findComplexObjects(PDDocument pdDocument) {
        COSDictionary catalogDictionary = pdDocument.getDocumentCatalog().getCOSObject();
        Map<COSBase, Collection<Reference>> incomingReferences = new HashMap<>();
        incomingReferences.put(catalogDictionary, new ArrayList<>());

        Set<COSBase> lastPass = Collections.<COSBase>singleton(catalogDictionary);
        Set<COSBase> thisPass = new HashSet<>();
        while(!lastPass.isEmpty()) {
            for (COSBase object : lastPass) {
                if (object instanceof COSArray) {
                    COSArray array = (COSArray) object;
                    for (int i = 0; i < array.size(); i++) {
                        addTarget(new ArrayReference(array, i), incomingReferences, thisPass);
                    }
                } else if (object instanceof COSDictionary) {
                    COSDictionary dictionary = (COSDictionary) object;
                    for (COSName key : dictionary.keySet()) {
                        addTarget(new DictionaryReference(dictionary, key), incomingReferences, thisPass);
                    }
                }
            }
            lastPass = thisPass;
            thisPass = new HashSet<>();
        }
        return incomingReferences;
    }

    /**
     * Helper method for {@link #findComplexObjects(PDDocument)} -
     * the method adds the given reference to its targets entry in
     * the mapping, also adding the target to the set if there was
     * no mapping before for the target (i.e. the target object has
     * not been analyzed yet and is newly found in this pass).
     * 
     * @see #testOptimizeDummy()
     */
    void addTarget(Reference reference, Map<COSBase, Collection<Reference>> incomingReferences, Set<COSBase> thisPass) {
        COSBase object = reference.getTo();
        if (object instanceof COSArray || object instanceof COSDictionary) {
            Collection<Reference> incoming = incomingReferences.get(object);
            if (incoming == null) {
                incoming = new ArrayList<>();
                incomingReferences.put(object, incoming);
                thisPass.add(object);
            }
            incoming.add(reference);
        }
    }

    /**
     * Helper method for {@link #optimize(PDDocument)} - this method
     * identifies duplicate candidates by their hash value and then 
     * forwards runs of objects with the same hash value to the method
     * {@link #mergeRun(Map, List)} for detailed comparison and actual
     * merging.
     * 
     * #see {@link #testOptimizeDummy()}
     */
    int mergeDuplicates(Map<COSBase, Collection<Reference>> complexObjects) throws IOException {
        List<HashOfCOSBase> hashes = new ArrayList<>(complexObjects.size());
        for (COSBase object : complexObjects.keySet()) {
            hashes.add(new HashOfCOSBase(object));
        }
        Collections.sort(hashes);

        int removedDuplicates = 0;
        if (!hashes.isEmpty()) {
            int runStart = 0;
            int runHash = hashes.get(0).hash;
            for (int i = 1; i < hashes.size(); i++) {
                int hash = hashes.get(i).hash;
                if (hash != runHash) {
                    int runSize = i - runStart;
                    if (runSize != 1) {
                        System.out.printf("Equal hash %d for %d elements.\n", runHash, runSize);
                        removedDuplicates += mergeRun(complexObjects, hashes.subList(runStart, i));
                    }
                    runHash = hash;
                    runStart = i;
                }
            }
            int runSize = hashes.size() - runStart;
            if (runSize != 1) {
                System.out.printf("Equal hash %d for %d elements.\n", runHash, runSize);
                removedDuplicates += mergeRun(complexObjects, hashes.subList(runStart, hashes.size()));
            }
        }
        return removedDuplicates;
    }

    /**
     * Helper method for {@link #mergeDuplicates(Map)} - this method
     * compares the objects in the list for actual equality and then
     * merges actual equals.
     * 
     * @see #testOptimizeDummy()
     */
    int mergeRun(Map<COSBase, Collection<Reference>> complexObjects, List<HashOfCOSBase> run) {
        int removedDuplicates = 0;

        List<List<COSBase>> duplicateSets = new ArrayList<>();
        for (HashOfCOSBase entry : run) {
            COSBase element = entry.object;
            for (List<COSBase> duplicateSet : duplicateSets) {
                if (equals(element, duplicateSet.get(0))) {
                    duplicateSet.add(element);
                    element = null;
                    break;
                }
            }
            if (element != null) {
                List<COSBase> duplicateSet = new ArrayList<>();
                duplicateSet.add(element);
                duplicateSets.add(duplicateSet);
            }
        }

        System.out.printf("Identified %d set(s) of identical objects in run.\n", duplicateSets.size());

        for (List<COSBase> duplicateSet : duplicateSets) {
            if (duplicateSet.size() > 1) {
                COSBase surviver = duplicateSet.remove(0);
                Collection<Reference> surviverReferences = complexObjects.get(surviver);
                for (COSBase object : duplicateSet) {
                    Collection<Reference> references = complexObjects.get(object);
                    for (Reference reference : references) {
                        reference.setTo(surviver);
                        surviverReferences.add(reference);
                    }
                    complexObjects.remove(object);
                    removedDuplicates++;
                }
                surviver.setDirect(false);
            }
        }

        return removedDuplicates;
    }

    /**
     * Helper method for {@link #mergeRun(Map, List)} - this method
     * checks whether two PDF objects actually are equal.
     * 
     * @see #testOptimizeDummy()
     */
    boolean equals(COSBase a, COSBase b) {
        if (a instanceof COSArray) {
            if (b instanceof COSArray) {
                COSArray aArray = (COSArray) a;
                COSArray bArray = (COSArray) b;
                if (aArray.size() == bArray.size()) {
                    for (int i=0; i < aArray.size(); i++) {
                        if (!resolve(aArray.get(i)).equals(resolve(bArray.get(i))))
                            return false;
                    }
                    return true;
                }
            }
        } else if (a instanceof COSDictionary) {
            if (b instanceof COSDictionary) {
                COSDictionary aDict = (COSDictionary) a;
                COSDictionary bDict = (COSDictionary) b;
                Set<COSName> keys = aDict.keySet();
                // As proposed by jchobantonov on github, we can compare dictionary sizes
                // here instead of the dictionaries themselves as we compare the values
                // key by key in the body of the if.
                if (keys.size() == bDict.keySet().size()) {
                    for (COSName key : keys) {
                        if (!resolve(aDict.getItem(key)).equals(bDict.getItem(key)))
                            return false;
                    }
                    // In case of COSStreams we strictly speaking should
                    // also compare the stream contents here. But apparently
                    // their hashes coincide well enough for the original
                    // hashing equality, so let's just assume...
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Helper method in the context of {@link #optimize(PDDocument)} -
     * this method resolves references in the given object until
     * hitting a direct object or null.
     * 
     * @see #testOptimizeDummy()
     */
    static COSBase resolve(COSBase object) {
        while (object instanceof COSObject)
            object = ((COSObject)object).getObject();
        return object;
    }

    /**
     * Helper interface in the context of {@link OptimizeAfterMerge#optimize(PDDocument)}
     * - it models a reference from one complex object to another.
     */
    interface Reference {
        public COSBase getFrom();

        public COSBase getTo();
        public void setTo(COSBase to);
    }

    /**
     * Helper class in the context of {@link OptimizeAfterMerge#optimize(PDDocument)}
     * - it models a reference from an array to another complex object.
     */
    static class ArrayReference implements Reference {
        public ArrayReference(COSArray array, int index) {
            this.from = array;
            this.index = index;
        }

        @Override
        public COSBase getFrom() {
            return from;
        }

        @Override
        public COSBase getTo() {
            return resolve(from.get(index));
        }

        @Override
        public void setTo(COSBase to) {
            from.set(index, to);
        }

        final COSArray from;
        final int index;
    }

    /**
     * Helper class in the context of {@link OptimizeAfterMerge#optimize(PDDocument)}
     * - it models a reference from a dictionary to another complex object.
     */
    static class DictionaryReference implements Reference {
        public DictionaryReference(COSDictionary dictionary, COSName key) {
            this.from = dictionary;
            this.key = key;
        }

        @Override
        public COSBase getFrom() {
            return from;
        }

        @Override
        public COSBase getTo() {
            return resolve(from.getDictionaryObject(key));
        }

        @Override
        public void setTo(COSBase to) {
            from.setItem(key, to);
        }

        final COSDictionary from;
        final COSName key;
    }

    /**
     * Helper class in the context of {@link OptimizeAfterMerge#optimize(PDDocument)}
     * - it is a comparable container of a COSBase and its hash value.
     */
    static class HashOfCOSBase implements Comparable<HashOfCOSBase> {
        public HashOfCOSBase(COSBase object) throws IOException {
            this.object = object;
            this.hash = calculateHash(object);
        }

        int calculateHash(COSBase object) throws IOException {
            if (object instanceof COSArray) {
                int result = 1;
                for (COSBase member : (COSArray)object)
                    result = 31 * result + member.hashCode();
                return result;
            } else if (object instanceof COSDictionary) {
                int result = 3;
                for (Map.Entry<COSName, COSBase> entry : ((COSDictionary)object).entrySet())
                    result += entry.hashCode();
                if (object instanceof COSStream) {
                    try (   InputStream data = ((COSStream)object).createRawInputStream()   ) {
                        MessageDigest md = MessageDigest.getInstance("MD5");
                        byte[] buffer = new byte[8192];
                        int bytesRead = 0;
                        while((bytesRead = data.read(buffer)) >= 0)
                            md.update(buffer, 0, bytesRead);
                        result = 31 * result + Arrays.hashCode(md.digest());
                    } catch (NoSuchAlgorithmException e) {
                        throw new IOException(e);
                    }
                }
                return result;
            } else {
                throw new IllegalArgumentException(String.format("Unknown complex COSBase type %s", object.getClass().getName()));
            }
        }

        final COSBase object;
        final int hash;

        @Override
        public int compareTo(HashOfCOSBase o) {
            int result = Integer.compare(hash,  o.hash);
            if (result == 0)
                result = Integer.compare(hashCode(), o.hashCode());
            return result;
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/53420344/ho-to-reduce-the-size-of-merged-pdf-a1-b-files-with-pdfbox-or-other-java-library">
     * Ho to reduce the size of merged PDF A1/b Files with pdfbox or other java library
     * </a>
     * <br/>
     * <a href="https://datentransfer.sparkassenverlag.de/my/transfers/5q8eskgne52npemx8kid7728zk1hq3f993dfat8his">
     * dummy.pdf
     * </a>
     * <p>
     * This is the code the OP himself posted as his solution. This only works if
     * (a) all font programs embedded for the same name indeed are identical, and
     * if (b) all fonts to consider are in the immediate page resources, not the
     * resources of some referred to xobject or pattern. If those conditions are
     * fulfilled, though, it very likely is much faster than the approach in
     * {@link #optimize(PDDocument)}. For the example file provided by the OP,
     * its result is nearly as small.
     * </p>
     */
    @Test
    public void testOptimizeLikeSchowaveDummy() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("dummy.pdf")  ) {
            PDDocument doc = PDDocument.load(resource);

            Map<String, COSBase> fontFileCache = new HashMap<>();
            for (int pageNumber = 0; pageNumber < doc.getNumberOfPages(); pageNumber++) {
                final PDPage page = doc.getPage(pageNumber);
                COSDictionary pageDictionary = (COSDictionary) page.getResources().getCOSObject().getDictionaryObject(COSName.FONT);
                for (COSName currentFont : pageDictionary.keySet()) {
                    COSDictionary fontDictionary = (COSDictionary) pageDictionary.getDictionaryObject(currentFont);
                    for (COSName actualFont : fontDictionary.keySet()) {
                        COSBase actualFontDictionaryObject = fontDictionary.getDictionaryObject(actualFont);
                        if (actualFontDictionaryObject instanceof COSDictionary) {
                            COSDictionary fontFile = (COSDictionary) actualFontDictionaryObject;
                            if (fontFile.getItem(COSName.FONT_NAME) instanceof COSName) {
                                COSName fontName = (COSName) fontFile.getItem(COSName.FONT_NAME);
                                fontFileCache.computeIfAbsent(fontName.getName(), key -> fontFile.getItem(COSName.FONT_FILE2));
                                fontFile.setItem(COSName.FONT_FILE2, fontFileCache.get(fontName.getName()));
                            }
                        }
                    }
                }
            }

            doc.save(new File(RESULT_FOLDER, "dummy-optimized-like-schowave.pdf"));
        }
    }
}
