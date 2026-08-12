package territories.output;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ShortProcessor;
import org.junit.Test;
import territories.api.AnalysisMode;
import territories.api.DensityWeightingSelection;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesResult;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultExporterTest {

    @Test
    public void writesExpectedHeadlessOutputTree() throws Exception {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(1, 1, 1);
        processor.set(6, 1, 2);
        processor.set(1, 6, 3);
        processor.set(6, 6, 4);
        ImagePlus labels = new ImagePlus("Cells", processor);
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.BOTH)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-export-" + System.nanoTime());

        ResultExporter.save(result, output);

        assertTrue(new File(output, "Objects/Region_1_Objects.csv").isFile());
        assertTrue(new File(output, "Interactions/Region_1_Interactions.csv").isFile());
        assertTrue(new File(output, "Density").isDirectory());
        File mapFile = new File(output, "Maps/Region_1_Territories.tif");
        assertTrue(mapFile.isFile());
        ImagePlus map = ij.IJ.openImage(mapFile.getAbsolutePath());
        assertTrue(map != null);
        assertTrue(map.getBitDepth() == 24);
        assertTrue(map.getWidth() == 8 && map.getHeight() == 8);
        map.close();
        result.closeDensityImages();
    }

    /**
     * ImageJ tables default to three decimal places, which rounds calibrated
     * areas and densities away. Exported numbers must match what the API
     * returned for the same run.
     */
    @Test
    public void exportedValuesMatchTheApiBeyondThreeDecimalPlaces() throws Exception {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(1, 1, 1);
        processor.set(6, 1, 2);
        processor.set(1, 6, 3);
        processor.set(6, 6, 4);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().pixelWidth = 0.325;
        labels.getCalibration().pixelHeight = 0.325;
        labels.getCalibration().setUnit("um");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-precision-" + System.nanoTime());

        ResultExporter.save(result, output);

        double expectedArea = result.getObjects().get(0).getArea();
        // One 0.325 um pixel: 0.105625, which three decimal places destroys.
        assertEquals(0.105625, expectedArea, 1.0e-12);
        assertEquals(
                expectedArea,
                firstValue(new File(output, "Objects/Region_1_Objects.csv"), "Object_Area"),
                1.0e-9);
        double expectedTerritory =
                result.getRegions().get(0).getTerritories().getCells().get(0).getArea();
        assertEquals(
                expectedTerritory,
                firstValue(new File(output, "Objects/Region_1_Objects.csv"), "Territory_Area"),
                1.0e-9);
        result.closeDensityImages();
    }

    /**
     * Fixed-point formatting keeps significant digits only down to about 1e-3.
     * A slide scan calibrated in millimetres puts object areas far below that,
     * where three or nine decimal places both destroy the value.
     */
    @Test
    public void exportedValuesSurviveSmallMagnitudes() throws Exception {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(1, 1, 1);
        processor.set(6, 6, 2);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().pixelWidth = 0.00025;
        labels.getCalibration().pixelHeight = 0.00025;
        labels.getCalibration().setUnit("mm");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-small-" + System.nanoTime());

        ResultExporter.save(result, output);

        double expectedArea = result.getObjects().get(0).getArea();
        // One 0.00025 mm pixel: 6.25e-8 mm^2.
        assertEquals(6.25e-8, expectedArea, 1.0e-20);
        assertEquals(
                expectedArea,
                firstValue(new File(output, "Objects/Region_1_Objects.csv"), "Object_Area"),
                1.0e-16);
        result.closeDensityImages();
    }

    /**
     * ImageJ abandons fixed-point above ~1e12 and falls back to four
     * significant digits. Areas and volumes in nanometres reach that easily.
     */
    @Test
    public void exportedValuesSurviveLargeMagnitudes() throws Exception {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(1, 1, 1);
        processor.set(6, 6, 2);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().pixelWidth = 537123.0;
        labels.getCalibration().pixelHeight = 511277.0;
        labels.getCalibration().setUnit("nm");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-large-" + System.nanoTime());

        ResultExporter.save(result, output);

        double expected =
                result.getRegions().get(0).getTerritories().getCells().get(0).getArea();
        assertTrue("expected a value above the fixed-point ceiling", expected > 1.0e12);
        assertEquals(
                expected,
                firstValue(new File(output, "Objects/Region_1_Objects.csv"), "Territory_Area"),
                Math.abs(expected) * 1.0e-9);
        result.closeDensityImages();
    }

    private static double firstValue(File csv, String column) throws Exception {
        java.util.List<String> lines =
                java.nio.file.Files.readAllLines(csv.toPath());
        assertTrue("expected a header and at least one row", lines.size() >= 2);
        String[] headers = lines.get(0).split(",", -1);
        int index = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equals(column)) {
                index = i;
                break;
            }
        }
        assertTrue("column not found: " + column, index >= 0);
        String[] values = lines.get(1).split(",", -1);
        assertTrue("row is missing column " + column, index < values.length);
        return Double.parseDouble(values[index].trim());
    }

    @Test
    public void disambiguatesSanitizedRegionNames() throws Exception {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(1, 1, 1);
        processor.set(6, 6, 2);
        Roi first = new Roi(0, 0, 4, 8);
        first.setName("A/B");
        Roi second = new Roi(4, 0, 4, 8);
        second.setName("A B");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(new ImagePlus("Cells", processor))
                        .regions(Arrays.asList(first, second))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-region-names-" + System.nanoTime());

        ResultExporter.save(result, output);

        assertTrue(new File(output, "Objects/A_B_Objects.csv").isFile());
        assertTrue(new File(output, "Objects/A_B_2_Objects.csv").isFile());
        assertTrue(new File(output, "Maps/A_B_Territories.tif").isFile());
        assertTrue(new File(output, "Maps/A_B_2_Territories.tif").isFile());
        result.closeDensityImages();
    }

    @Test
    public void disambiguatesSanitizedTypeNamesInDensityExports() throws Exception {
        ShortProcessor first = new ShortProcessor(8, 8);
        ShortProcessor second = new ShortProcessor(8, 8);
        first.set(1, 1, 1);
        second.set(6, 6, 1);
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .labelImages(Arrays.asList(
                                new ImagePlus("A/B", first),
                                new ImagePlus("A B", second)))
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.DENSITY)
                        .densityWeightingSelection(
                                DensityWeightingSelection.OBJECT_COUNT)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-type-names-" + System.nanoTime());

        ResultExporter.save(result, output);

        File[] densityFiles = new File(output, "Density").listFiles(
                (directory, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".tif"));
        assertTrue(densityFiles != null && densityFiles.length == 2);
        result.closeDensityImages();
    }
}
