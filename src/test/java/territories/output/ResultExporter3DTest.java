package territories.output;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import territories.api.AnalysisMode;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult3D;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultExporter3DTest {

    @Test
    public void writesVolumetricTablesAndStacks() throws Exception {
        ImageStack labelsStack = new ImageStack(5, 5);
        ImageStack maskStack = new ImageStack(5, 5);
        for (int z = 0; z < 4; z++) {
            labelsStack.addSlice(new ShortProcessor(5, 5));
            ByteProcessor maskSlice = new ByteProcessor(5, 5);
            maskSlice.setValue(1);
            maskSlice.fill();
            maskStack.addSlice(maskSlice);
        }
        labelsStack.getProcessor(1).set(1, 2, 1);
        labelsStack.getProcessor(4).set(3, 2, 2);
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(
                ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(new ImagePlus("Cells", labelsStack))
                        .regionMask(new ImagePlus("Brain", maskStack))
                        .analysisMode(AnalysisMode.BOTH)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-export-3d-" + System.nanoTime());

        ResultExporter3D.save(result, output);

        assertTrue(new File(output, "Objects/Brain_Objects_3D.csv").isFile());
        assertTrue(new File(output, "Interactions/Brain_Interactions_3D.csv").isFile());
        assertTrue(new File(output, "Maps/Brain_Territories_3D.tif").isFile());
        assertTrue(new File(output, "Density").isDirectory());
        result.closeGeneratedImages();
    }

    /**
     * TERRITORIES mode writes the regularity table and the label stack.
     *
     * <p>This covers the mode, not the exporter's null-gating: no input through
     * {@code analyze3D} can tell the two guards apart, because it always sets
     * territories and interactions together. ExporterGatingTest pins the
     * gating itself by assembling the mismatched result directly.
     */
    @Test
    public void territoriesModeWritesRegularityAndMap() throws Exception {
        ImageStack labelsStack = new ImageStack(5, 5);
        ImageStack maskStack = new ImageStack(5, 5);
        for (int z = 0; z < 4; z++) {
            labelsStack.addSlice(new ShortProcessor(5, 5));
            ByteProcessor maskSlice = new ByteProcessor(5, 5);
            maskSlice.setValue(1);
            maskSlice.fill();
            maskStack.addSlice(maskSlice);
        }
        labelsStack.getProcessor(1).set(1, 2, 1);
        labelsStack.getProcessor(4).set(3, 2, 2);
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(
                ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(new ImagePlus("Cells", labelsStack))
                        .regionMask(new ImagePlus("Brain", maskStack))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-3d-territories-" + System.nanoTime());

        ResultExporter3D.save(result, output);

        assertTrue(new File(output, "Interactions/Brain_Regularity_3D.csv").isFile());
        assertTrue(new File(output, "Maps/Brain_Territories_3D.tif").isFile());
        result.closeGeneratedImages();
    }

    /**
     * Volumes at ordinary confocal calibration land where ImageJ's three
     * decimal places bite hardest — above 1e-3, so it does not rescue itself
     * with scientific notation, and small enough that rounding is a percent.
     */
    @Test
    public void exportedVolumesMatchTheApi() throws Exception {
        ImageStack labelsStack = new ImageStack(5, 5);
        ImageStack maskStack = new ImageStack(5, 5);
        for (int z = 0; z < 4; z++) {
            labelsStack.addSlice(new ShortProcessor(5, 5));
            ByteProcessor maskSlice = new ByteProcessor(5, 5);
            maskSlice.setValue(1);
            maskSlice.fill();
            maskStack.addSlice(maskSlice);
        }
        labelsStack.getProcessor(1).set(1, 2, 1);
        labelsStack.getProcessor(4).set(3, 2, 2);
        ImagePlus mask = new ImagePlus("Brain", maskStack);
        mask.getCalibration().pixelWidth = 0.325;
        mask.getCalibration().pixelHeight = 0.325;
        mask.getCalibration().pixelDepth = 0.5;
        mask.getCalibration().setUnit("um");
        ImagePlus labels = new ImagePlus("Cells", labelsStack);
        labels.setCalibration(mask.getCalibration());
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(
                ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(labels)
                        .regionMask(mask)
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(10)
                        .build());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-3d-precision-" + System.nanoTime());

        ResultExporter3D.save(result, output);

        double expected = result.getObjects().get(0).getVolume();
        // One 0.325 x 0.325 x 0.5 um voxel: three decimals would write 0.053.
        assertEquals(0.0528125, expected, 1.0e-12);
        assertEquals(
                expected,
                firstValue(
                        new File(output, "Objects/Brain_Objects_3D.csv"), "Object_Volume"),
                1.0e-9);
        result.closeGeneratedImages();
    }

    private static double firstValue(File csv, String column) throws Exception {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(csv.toPath());
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
}

