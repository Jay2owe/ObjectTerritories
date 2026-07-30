package territories.output;

import ij.io.FileSaver;
import ij.measure.ResultsTable;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import territories.core.DensityResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Writes the analysis tree without relying on interactive ImageJ windows. */
public final class ResultExporter {

    private ResultExporter() {
    }

    public static void save(ObjectTerritoriesResult result, File outputRoot) throws IOException {
        if (result == null) throw new IllegalArgumentException("result must not be null");
        if (outputRoot == null) throw new IllegalArgumentException("output root must not be null");
        File objectsDirectory = directory(outputRoot, "Objects");
        File interactionsDirectory = directory(outputRoot, "Interactions");
        File densityDirectory = directory(outputRoot, "Density");
        directory(outputRoot, "Maps");

        for (RegionAnalysisResult region : result.getRegions()) {
            String regionName = safe(region.getRegionName());
            saveTable(
                    ResultTables.objects(result, region),
                    new File(objectsDirectory, regionName + "_Objects.csv"));
            if (region.getInteractions() != null) {
                saveTable(
                        ResultTables.interactions(region),
                        new File(interactionsDirectory, regionName + "_Interactions.csv"));
                saveTable(
                        ResultTables.regularity(region),
                        new File(interactionsDirectory, regionName + "_Regularity.csv"));
            }
            for (DensityResult density : region.getDensityResults()) {
                String name = regionName + "_" + safe(density.getTypeName())
                        + "_" + density.getWeighting().name().toLowerCase(java.util.Locale.ROOT)
                        + "_bw-" + safe(Double.toString(density.getBandwidthMicrons()));
                File destination = new File(densityDirectory, name + ".tif");
                if (!new FileSaver(density.getDensityMap()).saveAsTiff(destination.getAbsolutePath())) {
                    throw new IOException("could not save density map: " + destination);
                }
            }
        }
    }

    private static File directory(File parent, String name) throws IOException {
        File result = new File(parent, name);
        Files.createDirectories(result.toPath());
        return result;
    }

    private static void saveTable(ResultsTable table, File destination) throws IOException {
        table.saveAs(destination.getAbsolutePath());
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}

