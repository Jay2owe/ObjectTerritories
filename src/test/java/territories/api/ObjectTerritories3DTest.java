package territories.api;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ObjectTerritories3DTest {

    @Test
    public void publicApiRunsGenuineThreeDimensionalAnalysis() {
        ImagePlus labels = labels();
        ImagePlus mask = mask();
        ObjectTerritoriesParameters3D parameters =
                ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(labels)
                        .regionMask(mask)
                        .analysisMode(AnalysisMode.BOTH)
                        .densityWeightingSelection(DensityWeightingSelection.BOTH)
                        .permutations(10)
                        .seed(4L)
                        .build();

        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(parameters);

        assertEquals(2, result.getObjects().size());
        assertEquals(1, result.getRegions().size());
        RegionAnalysisResult3D region = result.getRegions().get(0);
        assertNotNull(region.getTerritories());
        assertNotNull(region.getInteractions());
        assertEquals(1, region.getInteractions().getCounts()[0][0]);
        assertEquals(2, region.getDensityResults().size());
        result.closeGeneratedImages();
    }

    @Test
    public void edgeExclusionAlsoFiltersThreeDimensionalInteractions() {
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(
                ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(labels())
                        .regionMask(mask())
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .edgeCellPolicy(EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES)
                        .permutations(10)
                        .build());

        RegionAnalysisResult3D region = result.getRegions().get(0);
        assertEquals(0, region.getInteractions().getCounts()[0][0]);
        assertEquals(
                0,
                region.getTerritories().getRegularity().getIncludedObjects());
        result.closeGeneratedImages();
    }

    private static ImagePlus labels() {
        ImageStack stack = new ImageStack(7, 5);
        for (int z = 0; z < 5; z++) stack.addSlice(new ShortProcessor(7, 5));
        stack.getProcessor(2).set(1, 2, 1);
        stack.getProcessor(4).set(5, 2, 2);
        ImagePlus result = new ImagePlus("Cells", stack);
        calibrate(result);
        return result;
    }

    private static ImagePlus mask() {
        ImageStack stack = new ImageStack(7, 5);
        for (int z = 0; z < 5; z++) {
            ByteProcessor processor = new ByteProcessor(7, 5);
            processor.setValue(1);
            processor.fill();
            stack.addSlice(processor);
        }
        ImagePlus result = new ImagePlus("Brain", stack);
        calibrate(result);
        return result;
    }

    private static void calibrate(ImagePlus image) {
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 2.0;
        calibration.setUnit("um");
    }
}
