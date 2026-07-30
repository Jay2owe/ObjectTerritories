package territories.api;

import territories.core.DensityResult3D;
import territories.core.InteractionMatrixResult;
import territories.core.TerritoryResult3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All requested outputs for one volumetric mask region. */
public final class RegionAnalysisResult3D {

    private final String regionName;
    private final TerritoryResult3D territories;
    private final InteractionMatrixResult interactions;
    private final List<DensityResult3D> densityResults;

    RegionAnalysisResult3D(
            String regionName,
            TerritoryResult3D territories,
            InteractionMatrixResult interactions,
            List<DensityResult3D> densityResults) {
        this.regionName = regionName;
        this.territories = territories;
        this.interactions = interactions;
        this.densityResults = Collections.unmodifiableList(
                new ArrayList<DensityResult3D>(densityResults));
    }

    public String getRegionName() {
        return regionName;
    }

    public TerritoryResult3D getTerritories() {
        return territories;
    }

    public InteractionMatrixResult getInteractions() {
        return interactions;
    }

    public List<DensityResult3D> getDensityResults() {
        return densityResults;
    }
}

