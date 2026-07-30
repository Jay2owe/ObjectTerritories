package territories.output;

import ij.measure.ResultsTable;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import territories.api.DensityWeighting;
import territories.core.DensityResult;
import territories.core.InteractionMatrixResult;
import territories.core.RegularityResult;
import territories.core.SpatialObject2D;
import territories.core.TerritoryCell;
import territories.core.TerritoryResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds standalone ImageJ tables without showing global result windows. */
public final class ResultTables {

    private ResultTables() {
    }

    public static ResultsTable objects(
            ObjectTerritoriesResult complete, RegionAnalysisResult region) {
        ResultsTable table = new ResultsTable();
        Map<Integer, TerritoryCell> cells = new HashMap<Integer, TerritoryCell>();
        TerritoryResult territories = region.getTerritories();
        if (territories != null) {
            for (TerritoryCell cell : territories.getCells()) {
                cells.put(cell.getObject().getIndex(), cell);
            }
        }
        Map<Integer, Double> countDensity = new HashMap<Integer, Double>();
        Map<Integer, Double> areaDensity = new HashMap<Integer, Double>();
        Map<Integer, Boolean> included = new HashMap<Integer, Boolean>();
        for (DensityResult density : region.getDensityResults()) {
            Map<Integer, Double> destination =
                    density.getWeighting() == DensityWeighting.OBJECT_COUNT
                            ? countDensity : areaDensity;
            destination.putAll(density.getLocalDensityByObjectIndex());
            for (Integer index : density.getLocalDensityByObjectIndex().keySet()) {
                included.put(index, Boolean.TRUE);
            }
        }
        included.putAll(markCells(cells));

        Map<Integer, SpatialObject2D> byIndex = new LinkedHashMap<Integer, SpatialObject2D>();
        for (SpatialObject2D object : complete.getObjects()) byIndex.put(object.getIndex(), object);
        for (SpatialObject2D object : complete.getObjects()) {
            if (!included.containsKey(object.getIndex())) continue;
            TerritoryCell cell = cells.get(object.getIndex());
            table.incrementCounter();
            table.addValue("Region", region.getRegionName());
            table.addValue("Type", object.getTypeName());
            table.addValue("Label", object.getLabel());
            table.addValue("Centroid_X", object.getCentroidX());
            table.addValue("Centroid_Y", object.getCentroidY());
            table.addValue("Object_Area", object.getArea());
            if (cell != null) {
                table.addValue("Territory_Area", cell.getArea());
                table.addValue("Neighbor_Count", cell.getNeighborObjectIndices().size());
                table.addValue("Neighbor_Objects", neighborText(cell, byIndex));
                table.addValue("Edge_Cell", cell.isEdgeCell() ? 1 : 0);
            }
            if (countDensity.containsKey(object.getIndex())) {
                table.addValue("Local_Count_Density_LOO", countDensity.get(object.getIndex()));
            }
            if (areaDensity.containsKey(object.getIndex())) {
                table.addValue("Local_Area_Density_LOO", areaDensity.get(object.getIndex()));
            }
        }
        return table;
    }

    public static ResultsTable interactions(RegionAnalysisResult region) {
        ResultsTable table = new ResultsTable();
        InteractionMatrixResult interactions = region.getInteractions();
        if (interactions == null) return table;
        List<String> types = interactions.getTypes();
        int[][] counts = interactions.getCounts();
        double[][] expected = interactions.getExpectedCounts();
        double[][] zScores = interactions.getZScores();
        double[][] pValues = interactions.getTwoSidedPValues();
        for (int first = 0; first < types.size(); first++) {
            for (int second = 0; second < types.size(); second++) {
                table.incrementCounter();
                table.addValue("Region", region.getRegionName());
                table.addValue("Type_A", types.get(first));
                table.addValue("Type_B", types.get(second));
                table.addValue("Observed_Edges", counts[first][second]);
                table.addValue("Expected_Edges", expected[first][second]);
                table.addValue("Z_Score", zScores[first][second]);
                table.addValue("Two_Sided_P", pValues[first][second]);
                table.addValue("Permutations", interactions.getPermutations());
                table.addValue("Seed", Long.toString(interactions.getSeed()));
            }
        }
        return table;
    }

    public static ResultsTable regularity(RegionAnalysisResult region) {
        ResultsTable table = new ResultsTable();
        if (region.getTerritories() == null) return table;
        RegularityResult regularity = region.getTerritories().getRegularity();
        table.incrementCounter();
        table.addValue("Region", region.getRegionName());
        table.addValue("Included_Objects", regularity.getIncludedObjects());
        table.addValue(
                "Territory_Area_CV",
                regularity.getTerritoryAreaCoefficientOfVariation());
        table.addValue("NN_Mean", regularity.getNearestNeighborMean());
        table.addValue("NN_SD", regularity.getNearestNeighborStandardDeviation());
        table.addValue("NN_Mean_Over_SD", regularity.getNearestNeighborRegularityRatio());
        return table;
    }

    private static Map<Integer, Boolean> markCells(Map<Integer, TerritoryCell> cells) {
        HashMap<Integer, Boolean> result = new HashMap<Integer, Boolean>();
        for (Integer index : cells.keySet()) result.put(index, Boolean.TRUE);
        return result;
    }

    private static String neighborText(
            TerritoryCell cell, Map<Integer, SpatialObject2D> byIndex) {
        StringBuilder result = new StringBuilder();
        for (Integer index : cell.getNeighborObjectIndices()) {
            SpatialObject2D object = byIndex.get(index);
            if (object == null) continue;
            if (result.length() > 0) result.append(';');
            result.append(object.getTypeName()).append(':').append(object.getLabel());
        }
        return result.toString();
    }
}
