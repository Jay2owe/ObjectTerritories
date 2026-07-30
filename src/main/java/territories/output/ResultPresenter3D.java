package territories.output;

import ij.IJ;
import ij.ImagePlus;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult3D;
import territories.core.DensityResult3D;

/** Interactive display for volumetric territory and density stacks. */
public final class ResultPresenter3D {

    private ResultPresenter3D() {
    }

    public static void show(ObjectTerritoriesResult3D result) {
        for (String warning : result.getWarnings()) IJ.log("[Object Territories] " + warning);
        for (RegionAnalysisResult3D region : result.getRegions()) {
            String suffix = " - " + region.getRegionName();
            ResultTables3D.objects(result, region)
                    .show("Object Territories 3D Objects" + suffix);
            if (region.getInteractions() != null) {
                ResultTables3D.interactions(region)
                        .show("Object Territories 3D Interactions" + suffix);
                ResultTables3D.regularity(region)
                        .show("Object Territories 3D Regularity" + suffix);
                ImagePlus territories = region.getTerritories().getTerritoryLabels();
                IJ.run(territories, "glasbey", "");
                territories.show();
            }
            for (DensityResult3D density : region.getDensityResults()) {
                ImagePlus image = density.getDensityVolume();
                IJ.run(image, "mpl-viridis", "");
                image.resetDisplayRange();
                image.show();
                IJ.run(
                        image,
                        "Calibration Bar...",
                        "location=[Upper Right] fill=White label=Black number=5 decimal=3 font=12 zoom=1 overlay");
            }
        }
    }
}

