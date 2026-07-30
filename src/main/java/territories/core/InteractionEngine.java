package territories.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Permutation-tested interaction matrix for an undirected Voronoi graph. */
public final class InteractionEngine {

    private InteractionEngine() {
    }

    public static InteractionMatrixResult analyze(
            List<TerritoryCell> cells,
            List<String> typeNames,
            int permutations,
            long seed) {
        if (cells == null) throw new IllegalArgumentException("cells must not be null");
        if (typeNames == null || typeNames.isEmpty()) {
            throw new IllegalArgumentException("at least one type name is required");
        }
        if (typeNames.contains(null)) {
            throw new IllegalArgumentException("type names must not contain null");
        }
        if (permutations < 1) throw new IllegalArgumentException("permutations must be at least 1");

        int typeCount = typeNames.size();
        Map<Integer, Integer> localByGlobal = new HashMap<Integer, Integer>();
        int[] observedTypes = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            TerritoryCell cell = cells.get(i);
            if (cell == null) throw new IllegalArgumentException("cells must not contain null");
            int type = cell.getObject().getTypeIndex();
            if (type < 0 || type >= typeCount) {
                throw new IllegalArgumentException("object type index is outside the type-name list");
            }
            observedTypes[i] = type;
            localByGlobal.put(cell.getObject().getIndex(), i);
        }

        List<Edge> edges = edges(cells, localByGlobal);
        int[][] observed = count(edges, observedTypes, typeCount);
        int[][][] nullCounts = new int[permutations][typeCount][typeCount];
        int[] shuffled = observedTypes.clone();
        Random random = new Random(seed);
        for (int p = 0; p < permutations; p++) {
            shuffle(shuffled, random);
            nullCounts[p] = count(edges, shuffled, typeCount);
        }

        double[][] expected = new double[typeCount][typeCount];
        double[][] standardDeviation = new double[typeCount][typeCount];
        for (int a = 0; a < typeCount; a++) {
            for (int b = 0; b < typeCount; b++) {
                double sum = 0.0;
                for (int p = 0; p < permutations; p++) sum += nullCounts[p][a][b];
                double mean = sum / permutations;
                expected[a][b] = mean;
                if (permutations > 1) {
                    double squared = 0.0;
                    for (int p = 0; p < permutations; p++) {
                        double delta = nullCounts[p][a][b] - mean;
                        squared += delta * delta;
                    }
                    standardDeviation[a][b] = Math.sqrt(squared / (permutations - 1));
                }
            }
        }

        double[][] zScores = new double[typeCount][typeCount];
        double[][] pValues = new double[typeCount][typeCount];
        for (int a = 0; a < typeCount; a++) {
            for (int b = 0; b < typeCount; b++) {
                double deviation = Math.abs(observed[a][b] - expected[a][b]);
                int equallyOrMoreExtreme = 0;
                for (int p = 0; p < permutations; p++) {
                    if (Math.abs(nullCounts[p][a][b] - expected[a][b]) + 1.0e-12 >= deviation) {
                        equallyOrMoreExtreme++;
                    }
                }
                pValues[a][b] = (equallyOrMoreExtreme + 1.0) / (permutations + 1.0);
                double sd = standardDeviation[a][b];
                if (sd > 0.0) {
                    zScores[a][b] = (observed[a][b] - expected[a][b]) / sd;
                } else if (observed[a][b] == expected[a][b]) {
                    zScores[a][b] = 0.0;
                } else {
                    zScores[a][b] = observed[a][b] > expected[a][b]
                            ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
            }
        }
        return new InteractionMatrixResult(
                typeNames, observed, expected, zScores, pValues, permutations, seed);
    }

    private static List<Edge> edges(
            List<TerritoryCell> cells, Map<Integer, Integer> localByGlobal) {
        ArrayList<Edge> result = new ArrayList<Edge>();
        for (int local = 0; local < cells.size(); local++) {
            int global = cells.get(local).getObject().getIndex();
            for (Integer neighborGlobal : cells.get(local).getNeighborObjectIndices()) {
                Integer neighborLocal = localByGlobal.get(neighborGlobal);
                if (neighborLocal != null && global < neighborGlobal) {
                    result.add(new Edge(local, neighborLocal));
                }
            }
        }
        return result;
    }

    private static int[][] count(List<Edge> edges, int[] types, int typeCount) {
        int[][] result = new int[typeCount][typeCount];
        for (Edge edge : edges) {
            int first = types[edge.first];
            int second = types[edge.second];
            result[first][second]++;
            if (first != second) result[second][first]++;
        }
        return result;
    }

    private static void shuffle(int[] values, Random random) {
        for (int i = values.length - 1; i > 0; i--) {
            int selected = random.nextInt(i + 1);
            int value = values[i];
            values[i] = values[selected];
            values[selected] = value;
        }
    }

    private static final class Edge {
        private final int first;
        private final int second;

        private Edge(int first, int second) {
            this.first = first;
            this.second = second;
        }
    }
}

