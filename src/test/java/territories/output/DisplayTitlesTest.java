package territories.output;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ShortProcessor;
import org.junit.Test;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.DensityWeighting;
import territories.api.AnalysisMode;
import territories.api.DensityWeightingSelection;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import territories.api.RegionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Found by the GUI check of 0.3.0: the count and size density maps of one
 * label type opened as two windows with the same title, and two ROIs with one
 * name made the second region's tables replace the first's on screen.
 */
public class DisplayTitlesTest {

    @Test
    public void densityTitlesNameTheWeighting() {
        assertEquals("A_Field_object_count_Density",
                DisplayTitles.densityTitle("A_Field_Density", DensityWeighting.OBJECT_COUNT));
        assertEquals("Cells_Region_1_object_size_Density_3D",
                DisplayTitles.densityTitle("Cells_Region_1_Density_3D", DensityWeighting.OBJECT_SIZE));
        assertEquals("custom_object_size",
                DisplayTitles.densityTitle("custom", DensityWeighting.OBJECT_SIZE));
    }

    @Test
    public void repeatedRegionNamesAndImageTitlesAreNumbered() {
        DisplayTitles titles = new DisplayTitles();
        assertEquals("SCN", titles.region("SCN"));
        assertEquals("SCN (2)", titles.region("SCN"));
        assertEquals("SCN (3)", titles.region("SCN"));
        assertEquals("Other", titles.region("Other"));
        // Images are counted separately from regions.
        assertEquals("SCN", titles.image("SCN"));
        assertEquals("SCN (2)", titles.image("SCN"));
        assertEquals("Result", titles.region(""));
    }

    @Test
    public void everyDensityMapOfARealRunGetsItsOwnTitle() {
        ShortProcessor a = new ShortProcessor(16, 16);
        a.set(2, 2, 1);
        a.set(12, 4, 2);
        ShortProcessor b = new ShortProcessor(16, 16);
        b.set(6, 11, 1);
        b.set(13, 13, 2);
        Roi first = new Roi(0, 0, 16, 16);
        first.setName("SCN");
        Roi second = new Roi(0, 0, 16, 16);
        second.setName("SCN");
        ObjectTerritoriesResult result = ObjectTerritories.analyze(
                ObjectTerritoriesParameters.builder()
                        .addLabelImage(new ImagePlus("A", a))
                        .addLabelImage(new ImagePlus("B", b))
                        .addRegion(first)
                        .addRegion(second)
                        .regionMode(RegionMode.INDEPENDENT)
                        .analysisMode(AnalysisMode.DENSITY)
                        .densityWeightingSelection(DensityWeightingSelection.BOTH)
                        .bandwidthMicrons(3.0)
                        .build());
        try {
            DisplayTitles titles = new DisplayTitles();
            List<String> regions = new ArrayList<String>();
            List<String> images = new ArrayList<String>();
            for (RegionAnalysisResult region : result.getRegions()) {
                regions.add(titles.region(region.getRegionName()));
                for (DensityResult density : region.getDensityResults()) {
                    images.add(titles.image(DisplayTitles.densityTitle(
                            density.getDensityMap().getTitle(), density.getWeighting())));
                }
            }
            assertEquals(2, regions.size());
            assertEquals(2, new HashSet<String>(regions).size());
            assertEquals(8, images.size());
            assertEquals(8, new HashSet<String>(images).size());
            assertEquals("A_SCN_object_count_Density", images.get(0));
            assertEquals("A_SCN_object_size_Density", images.get(1));
            assertEquals("A_SCN_object_count_Density (2)", images.get(4));
        } finally {
            result.closeDensityImages();
        }
    }
}
