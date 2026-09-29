package territories.api;

import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.TerritoryCell;
import territories.batch.ObjectTerritoriesBatchParameters;
import territories.output.ResultExporter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Edge cases a Fiji user reaches with ordinary data: empty, single, huge and odd images. */
public class EdgeCaseTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void allZeroLabelImageGivesZeroObjectsAndHeaderOnlyTables() throws Exception {
        ImagePlus empty = new ImagePlus("Empty", new ShortProcessor(16, 16));
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(empty)
                        .addRegion(field(16))
                        .permutations(5)
                        .build());
        try {
            assertEquals(0, result.getObjects().size());
            RegionAnalysisResult region = result.getRegions().get(0);
            assertEquals(0, region.getTerritories().getCells().size());
            File output = temporary.newFolder("empty-output");
            ResultExporter.save(result, output);
            List<String> objects = Files.readAllLines(
                    new File(new File(output, "Objects"), "Field_Objects.csv").toPath(),
                    StandardCharsets.UTF_8);
            assertTrue(objects.toString(), objects.size() <= 1);
        } finally {
            result.closeDensityImages();
        }
    }

    @Test
    public void singleObjectOwnsTheWholeRegion() {
        ShortProcessor pixels = new ShortProcessor(16, 16);
        pixels.set(5, 7, 1);
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(new ImagePlus("Cells", pixels))
                        .addRegion(field(16))
                        .permutations(5)
                        .build());
        try {
            assertEquals(1, result.getObjects().size());
            List<TerritoryCell> cells = result.getRegions().get(0).getTerritories().getCells();
            assertEquals(1, cells.size());
            assertEquals(256.0, cells.get(0).getArea(), 1.0e-9);
            assertEquals(2, result.getRegions().get(0).getDensityResults().size());
        } finally {
            result.closeDensityImages();
        }
    }

    @Test
    public void everyObjectOutsideTheRegionGivesAnEmptyRegionResult() {
        ShortProcessor pixels = new ShortProcessor(16, 16);
        pixels.set(14, 14, 1);
        pixels.set(15, 12, 2);
        Roi corner = new Roi(0, 0, 6, 6);
        corner.setName("Corner");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(new ImagePlus("Cells", pixels))
                        .addRegion(corner)
                        .permutations(5)
                        .build());
        try {
            assertEquals(2, result.getObjects().size());
            assertEquals(0, result.getRegions().get(0).getTerritories().getCells().size());
        } finally {
            result.closeDensityImages();
        }
    }

    @Test
    public void sixteenBitLabel65535IsOneObject() {
        ShortProcessor pixels = new ShortProcessor(16, 16);
        pixels.set(3, 3, 65535);
        pixels.set(10, 10, 1);
        ObjectTerritoriesResult result = analyzeTerritories(new ImagePlus("Cells", pixels));
        assertEquals(2, result.getObjects().size());
        assertTrue(hasLabel(result.getObjects(), 65535L));
    }

    @Test
    public void thirtyTwoBitLabelOneMillionIsOneObject() {
        FloatProcessor pixels = new FloatProcessor(16, 16);
        pixels.setf(3, 3, 1000000f);
        pixels.setf(10, 10, 1f);
        ObjectTerritoriesResult result = analyzeTerritories(new ImagePlus("Cells", pixels));
        assertEquals(2, result.getObjects().size());
        assertTrue(hasLabel(result.getObjects(), 1000000L));
    }

    @Test
    public void eightAndSixteenBitLabelsGiveIdenticalResults() {
        ByteProcessor bytes = new ByteProcessor(16, 16);
        ShortProcessor shorts = new ShortProcessor(16, 16);
        int[][] points = {{2, 2}, {12, 3}, {7, 9}, {3, 13}};
        for (int i = 0; i < points.length; i++) {
            bytes.set(points[i][0], points[i][1], i + 1);
            shorts.set(points[i][0], points[i][1], i + 1);
        }
        ObjectTerritoriesResult eight = analyzeBoth(new ImagePlus("Cells", bytes));
        ObjectTerritoriesResult sixteen = analyzeBoth(new ImagePlus("Cells", shorts));
        try {
            List<TerritoryCell> a = eight.getRegions().get(0).getTerritories().getCells();
            List<TerritoryCell> b = sixteen.getRegions().get(0).getTerritories().getCells();
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(Double.doubleToLongBits(a.get(i).getArea()),
                        Double.doubleToLongBits(b.get(i).getArea()));
            }
            List<DensityResult> da = eight.getRegions().get(0).getDensityResults();
            List<DensityResult> db = sixteen.getRegions().get(0).getDensityResults();
            for (int i = 0; i < da.size(); i++) {
                assertArrayEquals(
                        (float[]) da.get(i).getDensityMap().getProcessor().getPixels(),
                        (float[]) db.get(i).getDensityMap().getProcessor().getPixels(),
                        0.0f);
            }
        } finally {
            eight.closeDensityImages();
            sixteen.closeDensityImages();
        }
    }

    @Test
    public void rgbLabelImageIsRejected() {
        expect("RGB", () -> ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                .addLabelImage(new ImagePlus("Colour", new ColorProcessor(16, 16)))
                .addRegion(field(16))
                .build()));
    }

    @Test
    public void rgbThreeDimensionalLabelAndMaskAreRejected() {
        expect("RGB", () -> ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                .addLabelImage(stack("Colour", true))
                .regionMask(stack("Mask", false))
                .build()));
        expect("RGB", () -> ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                .addLabelImage(stack("Cells", false))
                .regionMask(stack("Colour mask", true))
                .build()));
    }

    @Test
    public void permutationsAboveTheCapAreRejectedEverywhere() {
        int tooMany = ObjectTerritoriesParameters.MAX_PERMUTATIONS + 1;
        assertEquals(1000000, ObjectTerritoriesParameters.MAX_PERMUTATIONS);
        expect("permutations must be at most 1000000",
                () -> ObjectTerritoriesParameters.builder().permutations(tooMany));
        expect("permutations must be at most 1000000",
                () -> ObjectTerritoriesParameters3D.builder().permutations(tooMany));
        expect("permutations must be at most 1000000",
                () -> ObjectTerritoriesBatchParameters.builder(
                        new File("in"), "(.+)_(.+)", 2, new File("r.roi"), new File("out"))
                        .permutations(tooMany));
        expect("permutations must be at most 1000000",
                () -> territories.macro.MacroOptionsParser.parse(
                        "label1=A regions=R permutations=" + tooMany));
        // The cap itself is allowed, and the pinned lower-bound text is unchanged.
        ObjectTerritoriesParameters.builder().permutations(ObjectTerritoriesParameters.MAX_PERMUTATIONS);
        expect("permutations must be at least 1",
                () -> ObjectTerritoriesParameters.builder().permutations(0));
    }

    @Test
    public void volumesAboveJavaArrayLimitsAreRejectedBeforeAnalysis() {
        // 64 x 64 x 524,289 = 2,147,487,744 voxels, just over Integer.MAX_VALUE.
        int depth = 524289;
        expect("voxels", () -> ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                .addLabelImage(new ImagePlus("Cells", new HugeStack(64, 64, depth)))
                .regionMask(new ImagePlus("Mask", new HugeStack(64, 64, depth)))
                .build()));
    }

    @Test
    public void twoDimensionalDensityMemoryGuardRejectsOversizedRequests() {
        // 20,000 x 20,000 pixels, 5 regions x 5 types x 2 weightings = 80 GB of maps.
        expect("density maps need approximately",
                () -> ObjectTerritories.assertReasonable2DOutputMemory(
                        20000L * 20000L, 5, 5, AnalysisMode.BOTH,
                        DensityWeightingSelection.BOTH, 4L << 30));
        // Territories only: no density maps, nothing to guard.
        ObjectTerritories.assertReasonable2DOutputMemory(
                20000L * 20000L, 5, 5, AnalysisMode.TERRITORIES,
                DensityWeightingSelection.BOTH, 4L << 30);
        // An ordinary request fits comfortably.
        ObjectTerritories.assertReasonable2DOutputMemory(
                1024L * 1024L, 1, 2, AnalysisMode.BOTH,
                DensityWeightingSelection.BOTH, 4L << 30);
    }

    // ------------------------------------------------------------------

    private static ObjectTerritoriesResult analyzeTerritories(ImagePlus labels) {
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(field(16))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(5)
                        .build());
        result.closeDensityImages();
        return result;
    }

    private static ObjectTerritoriesResult analyzeBoth(ImagePlus labels) {
        return ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(field(16))
                        .analysisMode(AnalysisMode.BOTH)
                        .bandwidthMicrons(3.0)
                        .permutations(5)
                        .build());
    }

    private static boolean hasLabel(List<SpatialObject2D> objects, long label) {
        for (SpatialObject2D object : objects) {
            if (object.getLabel() == label) return true;
        }
        return false;
    }

    private static Roi field(int size) {
        Roi roi = new Roi(0, 0, size, size);
        roi.setName("Field");
        return roi;
    }

    private static ImagePlus stack(String title, boolean rgb) {
        ImageStack stack = new ImageStack(6, 6);
        for (int z = 0; z < 3; z++) {
            ImageProcessor processor = rgb ? new ColorProcessor(6, 6) : new ByteProcessor(6, 6);
            processor.setColor(1);
            processor.fill();
            stack.addSlice(processor);
        }
        return new ImagePlus(title, stack);
    }

    private static void expect(String fragment, Runnable action) {
        try {
            action.run();
            fail("expected a rejection containing: " + fragment);
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(fragment));
        }
    }

    /** A virtual stack whose slices are made on demand, so its size costs no memory. */
    private static final class HugeStack extends VirtualStack {
        private final int depth;

        HugeStack(int width, int height, int depth) {
            super(width, height, null, null);
            this.depth = depth;
        }

        @Override
        public int getSize() {
            return depth;
        }

        @Override
        public ImageProcessor getProcessor(int n) {
            ByteProcessor processor = new ByteProcessor(getWidth(), getHeight());
            processor.set(0, 0, 1);
            return processor;
        }
    }
}
