package territories.api;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ShortProcessor;
import org.junit.Test;
import sc.fiji.territories.core.DensityResult;

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
        // The published selection is this package's enum; what a DensityResult
        // reports is the engine's, because DensityResult is the engine's type.
        // The deprecated OBJECT_AREA selection still canonicalises to
        // OBJECT_SIZE, which is what this test exists to pin.
        assertEquals(
                sc.fiji.territories.core.DensityWeighting.OBJECT_SIZE,
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

    /**
     * A ROI drawn against a larger montage must not claim territory over
     * pixels that were never imaged. The tessellation has to partition the
     * calibrated image rectangle exactly, under anisotropic spacing.
     */
    @Test
    public void oversizedRegionIsClippedToTheCalibratedImage() {
        ShortProcessor processor = new ShortProcessor(8, 8);
        processor.set(2, 2, 1);
        processor.set(5, 5, 2);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().pixelWidth = 0.25;
        labels.getCalibration().pixelHeight = 3.0;
        labels.getCalibration().setUnit("um");

        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new Roi(-20, -20, 100, 100))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());

        double total = 0.0;
        for (sc.fiji.territories.core.TerritoryCell cell
                : result.getRegions().get(0).getTerritories().getCells()) {
            total += cell.getArea();
        }
        // 8 x 0.25 um by 8 x 3.0 um = 2.0 x 24.0 um.
        assertEquals(48.0, total, 1.0e-8);
        result.closeDensityImages();
    }

    /**
     * A traced region overhanging the image, with part of its outline lying on
     * the image edge, must analyse rather than fail inside the geometry layer.
     */
    @Test
    public void compositeRegionOverhangingTheImageStillAnalyses() {
        ShortProcessor processor = new ShortProcessor(32, 32);
        processor.set(8, 6, 1);
        processor.set(16, 8, 2);
        processor.set(24, 10, 3);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().setUnit("um");
        int[] xs = {4, 20, 20, 40, 40, 32, 32, 4};
        int[] ys = {4, 4, 12, 12, 20, 20, 12, 12};

        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new ij.gui.PolygonRoi(xs, ys, xs.length, Roi.POLYGON))
                        .analysisMode(AnalysisMode.BOTH)
                        .permutations(10)
                        .build());

        double total = 0.0;
        for (sc.fiji.territories.core.TerritoryCell cell
                : result.getRegions().get(0).getTerritories().getCells()) {
            total += cell.getArea();
        }
        assertEquals(128.0, total, 1.0e-8);
        result.closeDensityImages();
    }

    /**
     * A notched outline puts Voronoi cell boundaries exactly on the region
     * wall, where the per-cell clip returns a line beside the polygon. Objects
     * segmented on an integer pixel grid hit that collinearity readily.
     */
    @Test
    public void notchedRegionWithCollinearCellBoundaryStillAnalyses() {
        ShortProcessor processor = new ShortProcessor(20, 20);
        processor.set(0, 9, 1);
        processor.set(15, 9, 2);
        ImagePlus labels = new ImagePlus("Cells", processor);
        labels.getCalibration().setUnit("um");
        int[] xs = {0, 20, 20, 12, 12, 8, 8, 0};
        int[] ys = {0, 0, 20, 20, 8, 8, 20, 20};

        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(new ij.gui.PolygonRoi(xs, ys, xs.length, Roi.POLYGON))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());

        double total = 0.0;
        for (sc.fiji.territories.core.TerritoryCell cell
                : result.getRegions().get(0).getTerritories().getCells()) {
            total += cell.getArea();
        }
        assertEquals(2, result.getRegions().get(0).getTerritories().getCells().size());
        // 20x20 field less the 4x12 notch.
        assertEquals(352.0, total, 1.0e-8);
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
