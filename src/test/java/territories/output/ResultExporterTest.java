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
