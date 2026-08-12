package territories.api;

import ij.gui.Roi;
import org.junit.Test;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.RegionFactory;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.SpatialRegion2D;
import sc.fiji.territories.core.TerritoryEngine;
import sc.fiji.territories.core.TerritoryResult;
import territories.output.ResultExporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Pins the exporter's null-gating, which {@link ObjectTerritories#analyze} alone
 * cannot exercise because it always sets territories and interactions together.
 * This test lives in {@code territories.api} so it can assemble the mismatched
 * result the package-private constructors otherwise keep out of reach.
 */
public class ExporterGatingTest {

    @Test
    public void regularityFollowsTerritoriesRatherThanInteractions() throws Exception {
        List<SpatialObject2D> objects = Arrays.asList(
                new SpatialObject2D(0, 0, "Cells", 1L, 2.5, 2.5, 1.0),
                new SpatialObject2D(1, 0, "Cells", 2L, 6.5, 6.5, 1.0));
        SpatialRegion2D region = RegionFactory.create(
                Arrays.asList(new Roi(0, 0, 8, 8)),
                sc.fiji.territories.core.RegionMode.INDEPENDENT, 1.0, 1.0, 8, 8).get(0);
        TerritoryResult territories = TerritoryEngine.analyze(
                objects, region, sc.fiji.territories.core.EdgeCellPolicy.INCLUDE_FLAGGED);

        // Territories without interactions: the state the exporter must not
        // couple, even though the analysis never produces it today.
        RegionAnalysisResult analysis = new RegionAnalysisResult(
                "Field", territories, null, new ArrayList<DensityResult>());
        ObjectTerritoriesResult result = new ObjectTerritoriesResult(
                objects,
                Collections.singletonList(analysis),
                new ArrayList<String>(),
                8, 8, 1.0, 1.0, "um");
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-gating-" + System.nanoTime());

        ResultExporter.save(result, output);

        assertTrue(
                "regularity CSV must be written whenever territories exist",
                new File(output, "Interactions/Field_Regularity.csv").isFile());
        assertTrue(
                "territory map must be written whenever territories exist",
                new File(output, "Maps/Field_Territories.tif").isFile());
        assertTrue(
                "no interactions CSV without interactions",
                !new File(output, "Interactions/Field_Interactions.csv").isFile());
    }

    @Test
    public void volumetricRegularityAlsoFollowsTerritories() throws Exception {
        ij.ImageStack maskStack = new ij.ImageStack(5, 5);
        for (int z = 0; z < 4; z++) {
            ij.process.ByteProcessor slice = new ij.process.ByteProcessor(5, 5);
            slice.setValue(1);
            slice.fill();
            maskStack.addSlice(slice);
        }
        sc.fiji.territories.core.RegionMask3D mask = sc.fiji.territories.core.RegionMaskFactory3D.create(
                new ij.ImagePlus("Brain", maskStack), sc.fiji.territories.core.RegionMode.UNION).get(0);
        List<sc.fiji.territories.core.SpatialObject3D> objects = Arrays.asList(
                new sc.fiji.territories.core.SpatialObject3D(0, 0, "Cells", 1L, 1.5, 2.5, 0.5, 1.0),
                new sc.fiji.territories.core.SpatialObject3D(1, 0, "Cells", 2L, 3.5, 2.5, 3.5, 1.0));
        sc.fiji.territories.core.TerritoryResult3D territories3D =
                sc.fiji.territories.core.TerritoryEngine3D.analyze(
                        objects, mask, sc.fiji.territories.core.EdgeCellPolicy.INCLUDE_FLAGGED);

        RegionAnalysisResult3D analysis = new RegionAnalysisResult3D(
                "Brain", territories3D, null,
                new ArrayList<sc.fiji.territories.core.DensityResult3D>());
        ObjectTerritoriesResult3D result = new ObjectTerritoriesResult3D(
                objects, Collections.singletonList(analysis), new ArrayList<String>());
        File output = new File(
                System.getProperty("java.io.tmpdir"),
                "object-territories-gating-3d-" + System.nanoTime());

        territories.output.ResultExporter3D.save(result, output);

        assertTrue(
                "3D regularity CSV must be written whenever territories exist",
                new File(output, "Interactions/Brain_Regularity_3D.csv").isFile());
        assertTrue(
                "3D territory stack must be written whenever territories exist",
                new File(output, "Maps/Brain_Territories_3D.tif").isFile());
        result.closeGeneratedImages();
    }
}
