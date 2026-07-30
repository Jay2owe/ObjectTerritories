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
}

