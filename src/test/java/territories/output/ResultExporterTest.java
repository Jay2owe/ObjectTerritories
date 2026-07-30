package territories.output;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ShortProcessor;
import org.junit.Test;
import territories.api.AnalysisMode;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesResult;

import java.io.File;

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
        assertTrue(new File(output, "Maps").isDirectory());
        result.closeDensityImages();
    }
}

