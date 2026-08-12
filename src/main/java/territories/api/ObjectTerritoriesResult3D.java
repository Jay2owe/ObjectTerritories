package territories.api;

import sc.fiji.territories.core.DensityResult3D;
import sc.fiji.territories.core.SpatialObject3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete genuine-3D result returned by the public Java API. */
public final class ObjectTerritoriesResult3D {

    private final List<SpatialObject3D> objects;
    private final List<RegionAnalysisResult3D> regions;
    private final List<String> warnings;

    ObjectTerritoriesResult3D(
            List<SpatialObject3D> objects,
            List<RegionAnalysisResult3D> regions,
            List<String> warnings) {
        this.objects = Collections.unmodifiableList(new ArrayList<SpatialObject3D>(objects));
        this.regions = Collections.unmodifiableList(
                new ArrayList<RegionAnalysisResult3D>(regions));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public List<SpatialObject3D> getObjects() {
        return objects;
    }

    public List<RegionAnalysisResult3D> getRegions() {
        return regions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** Closes all generated territory-label and density stacks. */
    public void closeGeneratedImages() {
        for (RegionAnalysisResult3D region : regions) {
            if (region.getTerritories() != null) {
                region.getTerritories().getTerritoryLabels().close();
                region.getTerritories().getTerritoryLabels().flush();
            }
            for (DensityResult3D density : region.getDensityResults()) {
                density.getDensityVolume().close();
                density.getDensityVolume().flush();
            }
        }
    }
}

