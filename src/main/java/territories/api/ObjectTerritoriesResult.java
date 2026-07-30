package territories.api;

import territories.core.SpatialObject2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete side-effect-free result returned by the public Java API. */
public final class ObjectTerritoriesResult {

    private final List<SpatialObject2D> objects;
    private final List<RegionAnalysisResult> regions;
    private final List<String> warnings;

    ObjectTerritoriesResult(
            List<SpatialObject2D> objects,
            List<RegionAnalysisResult> regions,
            List<String> warnings) {
        this.objects = Collections.unmodifiableList(new ArrayList<SpatialObject2D>(objects));
        this.regions = Collections.unmodifiableList(new ArrayList<RegionAnalysisResult>(regions));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public List<SpatialObject2D> getObjects() {
        return objects;
    }

    public List<RegionAnalysisResult> getRegions() {
        return regions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** Closes every density image owned by this result. */
    public void closeDensityImages() {
        for (RegionAnalysisResult region : regions) {
            for (territories.core.DensityResult density : region.getDensityResults()) {
                density.getDensityMap().close();
                density.getDensityMap().flush();
            }
        }
    }
}

