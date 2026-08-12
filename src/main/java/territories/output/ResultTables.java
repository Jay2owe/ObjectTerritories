package territories.output;

import ij.measure.ResultsTable;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import sc.fiji.territories.core.DensityWeighting;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.InteractionMatrixResult;
import sc.fiji.territories.core.RegularityResult;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.TerritoryCell;
import sc.fiji.territories.core.TerritoryResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds standalone ImageJ tables without showing global result windows. */
public final class ResultTables {

    /**
     * ImageJ's {@link ResultsTable} formats values with three decimal places by
     * default, which silently truncates calibrated densities and areas in both
     * saved CSVs and displayed tables. Nine is the highest precision the
     * ImageJ formatter honours.
     */
    static final int EXPORT_PRECISION = 9;

    /**
     * Nine decimal places keep at least seven significant digits down to 1e-3.
     * Below that, fixed-point formatting starts discarding them — a calibrated
     * density of 6.3e-8 would be written as "0.000000063" — so such columns are
     * switched to scientific notation, which ImageJ selects for negative digits.
     */
    private static final double SCIENTIFIC_THRESHOLD = 1.0e-3;

    /**
     * ImageJ also abandons fixed-point above this magnitude, falling back to
     * three decimals — four significant digits. Volumes in nm^3 reach it
     * easily, so such columns need the same treatment as tiny ones.
     */
    private static final double LARGE_VALUE_THRESHOLD = 999999999999.0;

    private ResultTables() {
    }

    static ResultsTable newTable() {
        ResultsTable table = new ResultsTable();
        table.setPrecision(EXPORT_PRECISION);
        return table;
    }

    /**
     * Chooses a per-column format that preserves the analysed values. Columns
     * holding text (stored as NaN) and columns of comfortably large values keep
     * ImageJ's readable fixed-point rendering.
     */
    static void applyExportPrecision(ResultsTable table) {
        int lastColumn = table.getLastColumn();
        int rows = table.getCounter();
        for (int column = 0; column <= lastColumn; column++) {
            if (table.getColumnHeading(column) == null) continue;
            double smallest = Double.POSITIVE_INFINITY;
            double largest = 0.0;
            for (int row = 0; row < rows; row++) {
                double value = table.getValueAsDouble(column, row);
                if (!Double.isFinite(value) || value == 0.0) continue;
                smallest = Math.min(smallest, Math.abs(value));
                largest = Math.max(largest, Math.abs(value));
            }
            boolean tooSmall = Double.isFinite(smallest) && smallest < SCIENTIFIC_THRESHOLD;
            boolean tooLarge = largest > LARGE_VALUE_THRESHOLD;
            if (tooSmall || tooLarge) {
                table.setDecimalPlaces(column, -EXPORT_PRECISION);
            }
        }
    }

    public static ResultsTable objects(
            ObjectTerritoriesResult complete, RegionAnalysisResult region) {
        ResultsTable table = newTable();
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
                table.addValue("Local_Size_Density_LOO", areaDensity.get(object.getIndex()));
            }
        }
        applyExportPrecision(table);
        return table;
    }

    public static ResultsTable interactions(RegionAnalysisResult region) {
        ResultsTable table = newTable();
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
        applyExportPrecision(table);
        return table;
    }

    public static ResultsTable regularity(RegionAnalysisResult region) {
        ResultsTable table = newTable();
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
        applyExportPrecision(table);
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
