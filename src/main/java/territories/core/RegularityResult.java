package territories.core;

/** Region-level Voronoi and nearest-neighbour regularity measurements. */
public final class RegularityResult {

    private final int includedObjects;
    private final double territoryAreaCoefficientOfVariation;
    private final double nearestNeighborMean;
    private final double nearestNeighborStandardDeviation;
    private final double nearestNeighborRegularityRatio;

    RegularityResult(
            int includedObjects,
            double territoryAreaCoefficientOfVariation,
            double nearestNeighborMean,
            double nearestNeighborStandardDeviation,
            double nearestNeighborRegularityRatio) {
        this.includedObjects = includedObjects;
        this.territoryAreaCoefficientOfVariation = territoryAreaCoefficientOfVariation;
        this.nearestNeighborMean = nearestNeighborMean;
        this.nearestNeighborStandardDeviation = nearestNeighborStandardDeviation;
        this.nearestNeighborRegularityRatio = nearestNeighborRegularityRatio;
    }

    public int getIncludedObjects() {
        return includedObjects;
    }

    public double getTerritoryAreaCoefficientOfVariation() {
        return territoryAreaCoefficientOfVariation;
    }

    public double getNearestNeighborMean() {
        return nearestNeighborMean;
    }

    public double getNearestNeighborStandardDeviation() {
        return nearestNeighborStandardDeviation;
    }

    public double getNearestNeighborRegularityRatio() {
        return nearestNeighborRegularityRatio;
    }
}

