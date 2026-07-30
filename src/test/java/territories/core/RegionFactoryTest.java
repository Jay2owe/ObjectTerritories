package territories.core;

import ij.gui.Roi;
import org.junit.Test;
import territories.api.RegionMode;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        assertEquals(0.0, regions.get(0).getGeometry().getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(15.0, regions.get(0).getGeometry().getEnvelopeInternal().getMaxY(), 0.0);
        assertTrue(regions.get(0).getGeometry().covers(
                new org.locationtech.jts.geom.GeometryFactory().createPoint(
                        new org.locationtech.jts.geom.Coordinate(1.0, 1.5))));
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

    @Test
    public void preservesRoiPositionAfterShapeConversion() {
        Roi roi = new Roi(2, 3, 4, 5);

        SpatialRegion2D region = RegionFactory.create(
                Arrays.asList(roi), RegionMode.INDEPENDENT, 2.0, 3.0).get(0);

        assertEquals(4.0, region.getGeometry().getEnvelopeInternal().getMinX(), 0.0);
        assertEquals(12.0, region.getGeometry().getEnvelopeInternal().getMaxX(), 0.0);
        assertEquals(9.0, region.getGeometry().getEnvelopeInternal().getMinY(), 0.0);
        assertEquals(24.0, region.getGeometry().getEnvelopeInternal().getMaxY(), 0.0);
    }
}
