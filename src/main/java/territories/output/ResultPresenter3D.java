package territories.output;

import ij.IJ;
import ij.ImagePlus;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult3D;
import sc.fiji.territories.core.DensityResult3D;

/** Interactive display for volumetric territory and density stacks. */
public final class ResultPresenter3D {

    private ResultPresenter3D() {
    }

    public static void show(ObjectTerritoriesResult3D result) {
        for (RegionAnalysisResult3D region : result.getRegions()) {
            String suffix = " - " + region.getRegionName();
            ResultTables3D.objects(result, region)
                    .show("Object Territories 3D Objects" + suffix);
            if (region.getInteractions() != null) {
                ResultTables3D.interactions(region)
                        .show("Object Territories 3D Interactions" + suffix);
            }
            if (region.getTerritories() != null) {
                ResultTables3D.regularity(region)
                        .show("Object Territories 3D Regularity" + suffix);
                ImagePlus territories = region.getTerritories().getTerritoryLabels();
                if (commandExists("glasbey")) {
                    IJ.run(territories, "glasbey", "");
                } else if (commandExists("3-3-2 RGB")) {
                    IJ.run(territories, "3-3-2 RGB", "");
                }
                territories.show();
            }
            for (DensityResult3D density : region.getDensityResults()) {
                ImagePlus image = density.getDensityVolume();
                if (commandExists("mpl-viridis")) {
                    IJ.run(image, "mpl-viridis", "");
                } else if (commandExists("Fire")) {
                    IJ.run(image, "Fire", "");
                }
                image.resetDisplayRange();
                image.show();
                IJ.run(
                        image,
                        "Calibration Bar...",
                        "location=[Upper Right] fill=White label=Black number=5 decimal=3 font=12 zoom=1 overlay");
            }
        }
    }

    /**
     * Fiji's lookup tables (mpl-viridis, glasbey) are not commands in plain
     * ImageJ, where running one raises "Unrecognized command".
     */
    static boolean commandExists(String command) {
        java.util.Hashtable<?, ?> commands = ij.Menus.getCommands();
        return commands != null && commands.get(command) != null;
    }
}

