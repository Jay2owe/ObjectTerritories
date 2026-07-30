package territories.api;

import territories.core.DensityResult;
import territories.core.InteractionMatrixResult;
import territories.core.TerritoryResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All requested outputs for one independent or unioned analysis region. */
public final class RegionAnalysisResult {

    private final String regionName;
    private final TerritoryResult territories;
    private final InteractionMatrixResult interactions;
    private final List<DensityResult> densityResults;

    RegionAnalysisResult(
            String regionName,
            TerritoryResult territories,
            InteractionMatrixResult interactions,
            List<DensityResult> densityResults) {
        this.regionName = regionName;
        this.territories = territories;
        this.interactions = interactions;
        this.densityResults = Collections.unmodifiableList(
                new ArrayList<DensityResult>(densityResults));
    }

    public String getRegionName() {
        return regionName;
    }

    /** Returns {@code null} when territory analysis was not requested. */
    public TerritoryResult getTerritories() {
        return territories;
    }

    /** Returns {@code null} when territory analysis was not requested. */
    public InteractionMatrixResult getInteractions() {
        return interactions;
    }

    public List<DensityResult> getDensityResults() {
        return densityResults;
    }
}

