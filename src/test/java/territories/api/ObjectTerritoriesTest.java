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
        assertEquals(8, field.getTerritories().getCells().size());
        assertNotNull(field.getInteractions());
        assertEquals(4, field.getDensityResults().size());
        for (DensityResult density : field.getDensityResults()) {
            assertFalse(density.getDensityMap().isVisible());
        }
        result.closeDensityImages();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void legacyObjectAreaSelectionProducesCanonicalSizeDensity() {
        ImagePlus labels = labels("Cells", new int[][] {{1, 1}});
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.DENSITY)
                        .densityWeightingSelection(DensityWeightingSelection.OBJECT_AREA)
                        .build());

        assertEquals(1, result.getRegions().get(0).getDensityResults().size());
        assertEquals(
                DensityWeighting.OBJECT_SIZE,
                result.getRegions().get(0).getDensityResults().get(0).getWeighting());
        result.closeDensityImages();
    }

    @Test
    public void edgeExclusionRemovesIncidentEdgesFromInteractionSummary() {
        ImagePlus labels = labels("Cells", new int[][] {{2, 4}, {6, 4}});
        ObjectTerritoriesParameters.Builder base =
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(0, 0, 8, 8))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10);

        ObjectTerritoriesResult included = ObjectTerritories.analyze(
                base.edgeCellPolicy(EdgeCellPolicy.INCLUDE_FLAGGED).build());
        ObjectTerritoriesResult excluded = ObjectTerritories.analyze(
                base.edgeCellPolicy(EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES).build());

        assertEquals(1, included.getRegions().get(0).getInteractions().getCounts()[0][0]);
        assertEquals(0, excluded.getRegions().get(0).getInteractions().getCounts()[0][0]);
        assertEquals(
                0,
                excluded.getRegions().get(0).getTerritories()
                        .getRegularity().getIncludedObjects());
        included.closeDensityImages();
        excluded.closeDensityImages();
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
