package territories.api;

import sc.fiji.territories.core.SpatialObject2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete side-effect-free result returned by the public Java API. */
public final class ObjectTerritoriesResult {

    private final List<SpatialObject2D> objects;
    private final List<RegionAnalysisResult> regions;
    private final List<String> warnings;
    private final int imageWidth;
    private final int imageHeight;
    private final double pixelWidth;
    private final double pixelHeight;
    private final String spatialUnit;

    ObjectTerritoriesResult(
            List<SpatialObject2D> objects,
            List<RegionAnalysisResult> regions,
            List<String> warnings,
            int imageWidth,
            int imageHeight,
            double pixelWidth,
            double pixelHeight,
            String spatialUnit) {
        this.objects = Collections.unmodifiableList(new ArrayList<SpatialObject2D>(objects));
        this.regions = Collections.unmodifiableList(new ArrayList<RegionAnalysisResult>(regions));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.spatialUnit = spatialUnit;
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

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public double getPixelWidth() {
        return pixelWidth;
    }

    public double getPixelHeight() {
        return pixelHeight;
    }

    public String getSpatialUnit() {
        return spatialUnit;
    }

    /** Closes every density image owned by this result. */
    public void closeDensityImages() {
        for (RegionAnalysisResult region : regions) {
            for (sc.fiji.territories.core.DensityResult density : region.getDensityResults()) {
                density.getDensityMap().close();
                density.getDensityMap().flush();
            }
        }
    }
}
