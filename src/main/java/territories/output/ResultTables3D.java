package territories.output;

import ij.measure.ResultsTable;
import territories.api.DensityWeighting;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult3D;
import territories.core.DensityResult3D;
import territories.core.InteractionMatrixResult;
import territories.core.RegularityResult;
import territories.core.SpatialObject3D;
import territories.core.TerritoryCell3D;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds standalone tables for genuine 3D results. */
public final class ResultTables3D {

    private ResultTables3D() {
    }

    public static ResultsTable objects(
            ObjectTerritoriesResult3D complete, RegionAnalysisResult3D region) {
        ResultsTable table = new ResultsTable();
        Map<Integer, TerritoryCell3D> cells = new HashMap<Integer, TerritoryCell3D>();
        if (region.getTerritories() != null) {
            for (TerritoryCell3D cell : region.getTerritories().getCells()) {
                cells.put(cell.getObject().getIndex(), cell);
            }
        }
        Map<Integer, Double> countDensity = new HashMap<Integer, Double>();
        Map<Integer, Double> sizeDensity = new HashMap<Integer, Double>();
        Map<Integer, Boolean> included = new HashMap<Integer, Boolean>();
        for (DensityResult3D density : region.getDensityResults()) {
            Map<Integer, Double> destination =
                    density.getWeighting() == DensityWeighting.OBJECT_COUNT
                            ? countDensity : sizeDensity;
            destination.putAll(density.getLocalDensityByObjectIndex());
            for (Integer index : density.getLocalDensityByObjectIndex().keySet()) {
                included.put(index, Boolean.TRUE);
            }
        }
        for (Integer index : cells.keySet()) included.put(index, Boolean.TRUE);

        Map<Integer, SpatialObject3D> byIndex = new LinkedHashMap<Integer, SpatialObject3D>();
        for (SpatialObject3D object : complete.getObjects()) byIndex.put(object.getIndex(), object);
        for (SpatialObject3D object : complete.getObjects()) {
            if (!included.containsKey(object.getIndex())) continue;
            TerritoryCell3D cell = cells.get(object.getIndex());
            table.incrementCounter();
            table.addValue("Region", region.getRegionName());
            table.addValue("Type", object.getTypeName());
            table.addValue("Label", object.getLabel());
            table.addValue("Centroid_X", object.getCentroidX());
            table.addValue("Centroid_Y", object.getCentroidY());
            table.addValue("Centroid_Z", object.getCentroidZ());
            table.addValue("Object_Volume", object.getVolume());
            if (cell != null) {
                table.addValue("Territory_Voxels", cell.getVoxelCount());
                table.addValue("Territory_Volume", cell.getVolume());
                table.addValue("Neighbor_Count", cell.getNeighborObjectIndices().size());
                table.addValue("Neighbor_Objects", neighborText(cell, byIndex));
                table.addValue("Edge_Cell", cell.isEdgeCell() ? 1 : 0);
            }
            if (countDensity.containsKey(object.getIndex())) {
                table.addValue("Local_Count_Density_LOO", countDensity.get(object.getIndex()));
            }
            if (sizeDensity.containsKey(object.getIndex())) {
                table.addValue("Local_Size_Density_LOO", sizeDensity.get(object.getIndex()));
            }
        }
        return table;
    }

    public static ResultsTable interactions(RegionAnalysisResult3D region) {
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
                table.addValue("Observed_Face_Adjacencies", counts[first][second]);
                table.addValue("Expected_Face_Adjacencies", expected[first][second]);
                table.addValue("Z_Score", zScores[first][second]);
                table.addValue("Two_Sided_P", pValues[first][second]);
                table.addValue("Permutations", interactions.getPermutations());
                table.addValue("Seed", Long.toString(interactions.getSeed()));
            }
        }
        return table;
    }

    public static ResultsTable regularity(RegionAnalysisResult3D region) {
        ResultsTable table = new ResultsTable();
        if (region.getTerritories() == null) return table;
        RegularityResult regularity = region.getTerritories().getRegularity();
        table.incrementCounter();
        table.addValue("Region", region.getRegionName());
        table.addValue("Included_Objects", regularity.getIncludedObjects());
        table.addValue(
                "Territory_Volume_CV",
                regularity.getTerritorySizeCoefficientOfVariation());
        table.addValue("NN_Mean_3D", regularity.getNearestNeighborMean());
        table.addValue("NN_SD_3D", regularity.getNearestNeighborStandardDeviation());
        table.addValue("NN_Mean_Over_SD_3D", regularity.getNearestNeighborRegularityRatio());
        return table;
    }

    private static String neighborText(
            TerritoryCell3D cell, Map<Integer, SpatialObject3D> byIndex) {
        StringBuilder result = new StringBuilder();
        for (Integer index : cell.getNeighborObjectIndices()) {
            SpatialObject3D object = byIndex.get(index);
            if (object == null) continue;
            if (result.length() > 0) result.append(';');
            result.append(object.getTypeName()).append(':').append(object.getLabel());
        }
        return result.toString();
    }
}

