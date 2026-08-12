package territories.output;

import ij.io.FileSaver;
import ij.measure.ResultsTable;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import sc.fiji.territories.core.DensityResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

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
        File mapsDirectory = directory(outputRoot, "Maps");
        Set<String> regionNames = new HashSet<String>();
        Set<String> densityNames = new HashSet<String>();

        for (RegionAnalysisResult region : result.getRegions()) {
            String regionName = SafeOutputNames.unique(
                    region.getRegionName(), regionNames);
            saveTable(
                    ResultTables.objects(result, region),
                    new File(objectsDirectory, regionName + "_Objects.csv"));
            if (region.getInteractions() != null) {
                saveTable(
                        ResultTables.interactions(region),
                        new File(interactionsDirectory, regionName + "_Interactions.csv"));
            }
            if (region.getTerritories() != null) {
                saveTable(
                        ResultTables.regularity(region),
                        new File(interactionsDirectory, regionName + "_Regularity.csv"));
                ij.ImagePlus map = TerritoryMapRenderer.render(
                        region,
                        result.getImageWidth(),
                        result.getImageHeight(),
                        result.getPixelWidth(),
                        result.getPixelHeight(),
                        result.getSpatialUnit());
                try {
                    File destination = new File(
                            mapsDirectory, regionName + "_Territories.tif");
                    if (!new FileSaver(map).saveAsTiff(destination.getAbsolutePath())) {
                        throw new IOException(
                                "could not save territory map: " + destination);
                    }
                } finally {
                    map.close();
                    map.flush();
                }
            }
            for (DensityResult density : region.getDensityResults()) {
                String requestedName = regionName + "_"
                        + SafeOutputNames.safe(density.getTypeName())
                        + "_" + density.getWeighting().name().toLowerCase(java.util.Locale.ROOT)
                        + "_bw-" + safe(Double.toString(density.getBandwidthMicrons()));
                String name = SafeOutputNames.unique(requestedName, densityNames);
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
        return SafeOutputNames.safe(value);
    }
}
