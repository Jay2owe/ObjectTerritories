package territories.api;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ShortProcessor;
import org.junit.Test;
import territories.core.DensityResult;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ObjectTerritoriesTest {

    @Test
    public void facadeRunsBothAnalysesWithoutUsingGlobalImageState() {
        ImagePlus first = labels("A", new int[][] {
                {0, 0}, {7, 0}, {0, 7}, {7, 7}
        });
        ImagePlus second = labels("B", new int[][] {
                {3, 0}, {0, 3}, {7, 3}, {3, 7}
        });
        Roi region = new Roi(0, 0, 8, 8);
        region.setName("Field");
        ObjectTerritoriesParameters parameters = ObjectTerritoriesParameters.builder()
                .labelImages(Arrays.asList(first, second))
                .addRegion(region)
                .analysisMode(AnalysisMode.BOTH)
                .densityWeightingSelection(DensityWeightingSelection.BOTH)
                .permutations(20)
                .seed(19L)
                .build();

        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters);

        assertEquals(8, result.getObjects().size());
        assertEquals(1, result.getRegions().size());
        RegionAnalysisResult field = result.getRegions().get(0);
        assertNotNull(field.getTerritories());
        assertNotNull(field.getInteractions());
        assertEquals(4, field.getDensityResults().size());
        for (DensityResult density : field.getDensityResults()) {
            assertFalse(density.getDensityMap().isVisible());
        }
        result.closeDensityImages();
    }

    private static ImagePlus labels(String title, int[][] points) {
        ShortProcessor processor = new ShortProcessor(8, 8);
        for (int i = 0; i < points.length; i++) {
            processor.set(points[i][0], points[i][1], i + 1);
        }
        ImagePlus result = new ImagePlus(title, processor);
        Calibration calibration = result.getCalibration();
        calibration.pixelWidth = 1.0;
        calibration.pixelHeight = 1.0;
        calibration.setUnit("um");
        return result;
    }
}

