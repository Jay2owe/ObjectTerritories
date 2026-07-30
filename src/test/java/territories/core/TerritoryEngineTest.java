package territories.core;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Geometry;
import territories.api.EdgeCellPolicy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerritoryEngineTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Test
    public void regularThreeByThreeLatticeHasKnownCentralCell() {
        List<SpatialObject2D> objects = new ArrayList<SpatialObject2D>();
        int index = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                objects.add(object(index, x * 3.0 + 1.5, y * 3.0 + 1.5));
                index++;
            }
        }
        SpatialRegion2D region = rectangle("Field", 0.0, 0.0, 9.0, 9.0);

        TerritoryResult result = TerritoryEngine.analyze(
                objects, region, EdgeCellPolicy.INCLUDE_FLAGGED);

        assertEquals(9, result.getCells().size());
        TerritoryCell centre = result.getCells().get(4);
        assertEquals(9.0, centre.getArea(), 1.0e-8);
        assertEquals(4, centre.getNeighborObjectIndices().size());
        assertFalse(centre.isEdgeCell());
        assertTrue(result.getCells().get(0).isEdgeCell());
        assertEquals(0.0,
                result.getRegularity().getTerritoryAreaCoefficientOfVariation(), 1.0e-8);
        assertEquals(3.0, result.getRegularity().getNearestNeighborMean(), 1.0e-8);
        assertEquals(Double.POSITIVE_INFINITY,
                result.getRegularity().getNearestNeighborRegularityRatio(), 0.0);
    }

    @Test
    public void independentClippingDoesNotCreatePointOnlyNeighbors() {
        List<SpatialObject2D> objects = new ArrayList<SpatialObject2D>();
        objects.add(object(0, 2.5, 2.5));
        objects.add(object(1, 7.5, 2.5));
        objects.add(object(2, 2.5, 7.5));
        objects.add(object(3, 7.5, 7.5));

        TerritoryResult result = TerritoryEngine.analyze(
                objects,
                rectangle("Square", 0.0, 0.0, 10.0, 10.0),
                EdgeCellPolicy.INCLUDE_FLAGGED);

        for (TerritoryCell cell : result.getCells()) {
            assertEquals(25.0, cell.getArea(), 1.0e-8);
            assertEquals(2, cell.getNeighborObjectIndices().size());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCoincidentCentroids() {
        List<SpatialObject2D> objects = new ArrayList<SpatialObject2D>();
        objects.add(object(0, 2.0, 2.0));
        objects.add(new SpatialObject2D(1, 1, "Other", 2, 2.0, 2.0, 1.0));
        TerritoryEngine.analyze(
                objects,
                rectangle("Square", 0.0, 0.0, 10.0, 10.0),
                EdgeCellPolicy.INCLUDE_FLAGGED);
    }

    @Test
    public void disconnectedUnionIsTessellatedPerComponent() {
        Geometry first = rectangle("First", 0.0, 0.0, 10.0, 10.0).getGeometry();
        Geometry second = rectangle("Second", 20.0, 0.0, 30.0, 10.0).getGeometry();
        SpatialRegion2D union = new SpatialRegion2D("Union", first.union(second));
        List<SpatialObject2D> objects = new ArrayList<SpatialObject2D>();
        objects.add(object(0, 5.0, 5.0));
        objects.add(object(1, 25.0, 5.0));

        TerritoryResult result = TerritoryEngine.analyze(
                objects, union, EdgeCellPolicy.INCLUDE_FLAGGED);

        assertEquals(2, result.getCells().size());
        assertEquals(100.0, result.getCells().get(0).getArea(), 1.0e-8);
        assertEquals(100.0, result.getCells().get(1).getArea(), 1.0e-8);
        assertTrue(result.getCells().get(0).getNeighborObjectIndices().isEmpty());
        assertTrue(result.getCells().get(1).getNeighborObjectIndices().isEmpty());
    }

    @Test
    public void pointTouchingComponentsOwnBoundaryObjectOnlyOnce() {
        Geometry first = rectangle("First", 0.0, 0.0, 1.0, 1.0).getGeometry();
        Geometry second = rectangle("Second", 1.0, 1.0, 2.0, 2.0).getGeometry();
        SpatialRegion2D union = new SpatialRegion2D("Point touch", first.union(second));

        TerritoryResult result = TerritoryEngine.analyze(
                java.util.Collections.singletonList(object(0, 1.0, 1.0)),
                union,
                EdgeCellPolicy.INCLUDE_FLAGGED);

        assertEquals(1, result.getCells().size());
        assertEquals(0, result.getCells().get(0).getObject().getIndex());
        assertEquals(1.0, result.getCells().get(0).getArea(), 1.0e-8);
    }

    private static SpatialObject2D object(int index, double x, double y) {
        return new SpatialObject2D(index, 0, "Cells", index + 1L, x, y, 1.0);
    }

    private static SpatialRegion2D rectangle(
            String name, double minX, double minY, double maxX, double maxY) {
        return new SpatialRegion2D(name, GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        }));
    }
}
