package territories.output;

import ij.io.FileSaver;
import ij.measure.ResultsTable;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult3D;
import territories.core.DensityResult3D;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** Headless-safe CSV and TIFF-stack export for genuine 3D results. */
public final class ResultExporter3D {

    private ResultExporter3D() {
    }

    public static void save(ObjectTerritoriesResult3D result, File outputRoot)
            throws IOException {
        if (result == null || outputRoot == null) {
            throw new IllegalArgumentException("result and output root are required");
        }
        File objectsDirectory = directory(outputRoot, "Objects");
        File interactionsDirectory = directory(outputRoot, "Interactions");
        File densityDirectory = directory(outputRoot, "Density");
        File mapsDirectory = directory(outputRoot, "Maps");
        Set<String> regionNames = new HashSet<String>();
        Set<String> densityNames = new HashSet<String>();
        for (RegionAnalysisResult3D region : result.getRegions()) {
            String regionName = SafeOutputNames.unique(
                    region.getRegionName(), regionNames);
            saveTable(
                    ResultTables3D.objects(result, region),
                    new File(objectsDirectory, regionName + "_Objects_3D.csv"));
            if (region.getInteractions() != null) {
                saveTable(
                        ResultTables3D.interactions(region),
                        new File(interactionsDirectory, regionName + "_Interactions_3D.csv"));
                saveTable(
                        ResultTables3D.regularity(region),
                        new File(interactionsDirectory, regionName + "_Regularity_3D.csv"));
                saveImage(
                        region.getTerritories().getTerritoryLabels(),
                        new File(mapsDirectory, regionName + "_Territories_3D.tif"));
            }
            for (DensityResult3D density : region.getDensityResults()) {
                String requestedName = regionName + "_"
                        + SafeOutputNames.safe(density.getTypeName())
                        + "_" + density.getWeighting().name().toLowerCase(java.util.Locale.ROOT)
                        + "_bw-" + safe(Double.toString(density.getBandwidth()))
                        + "_Density_3D";
                String name = SafeOutputNames.unique(requestedName, densityNames);
                saveImage(
                        density.getDensityVolume(),
                        new File(densityDirectory, name + ".tif"));
            }
        }
    }

    private static void saveImage(ij.ImagePlus image, File destination)
            throws IOException {
        if (!new FileSaver(image).saveAsTiffStack(destination.getAbsolutePath())) {
            throw new IOException("could not save 3D image: " + destination);
        }
    }

    private static File directory(File parent, String name) throws IOException {
        File result = new File(parent, name);
        Files.createDirectories(result.toPath());
        return result;
    }

    private static void saveTable(ResultsTable table, File destination)
            throws IOException {
        table.saveAs(destination.getAbsolutePath());
    }

    private static String safe(String value) {
        return SafeOutputNames.safe(value);
    }
}
