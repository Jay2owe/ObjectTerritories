package territories.core;

import ij.gui.Roi;
import org.junit.Test;
import territories.api.RegionMode;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegionFactoryTest {

    @Test
    public void createsIndependentCalibratedRegions() {
        Roi first = new Roi(0, 0, 10, 5);
        first.setName("SCN");
        Roi second = new Roi(10, 0, 5, 5);
        second.setName("LH");

        List<SpatialRegion2D> regions = RegionFactory.create(
                Arrays.asList(first, second), RegionMode.INDEPENDENT, 2.0, 3.0);

        assertEquals(2, regions.size());
        assertEquals("SCN", regions.get(0).getName());
        assertEquals(300.0, regions.get(0).getGeometry().getArea(), 1.0e-9);
    }

    @Test
    public void unionsTouchingRegions() {
        Roi first = new Roi(0, 0, 10, 5);
        Roi second = new Roi(10, 0, 5, 5);

        List<SpatialRegion2D> regions = RegionFactory.create(
                Arrays.asList(first, second), RegionMode.UNION, 1.0, 1.0);

        assertEquals(1, regions.size());
        assertEquals("All_Regions", regions.get(0).getName());
        assertEquals(75.0, regions.get(0).getGeometry().getArea(), 1.0e-9);
    }
}

