package territories.equivalence;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult;
import territories.api.RegionAnalysisResult3D;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.DensityResult3D;
import sc.fiji.territories.core.InteractionMatrixResult;
import sc.fiji.territories.core.RegularityResult;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.SpatialObject3D;
import sc.fiji.territories.core.TerritoryCell;
import sc.fiji.territories.core.TerritoryCell3D;
import sc.fiji.territories.core.TerritoryResult;
import sc.fiji.territories.core.TerritoryResult3D;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic textual rendering of a complete analysis result.
 *
 * <p>Every floating-point value is written as its raw IEEE-754 bit pattern in
 * hexadecimal, never as decimal text. The gate is bit-identity: a decimal
 * rendering hides the last bits, and it also makes {@code NaN} — which several
 * summary fields legitimately carry — indistinguishable from any other
 * {@code NaN}. Generated images are reduced to a digest of their complete raw
 * pixel bits plus enough summary values to localise a difference.
 */
final class Dump {

    private final StringBuilder text = new StringBuilder(8192);

    Dump() {
    }

    @Override
    public String toString() {
        return text.toString();
    }

    void line(String value) {
        text.append(value).append('\n');
    }

    // ------------------------------------------------------------------
    // 2D
    // ------------------------------------------------------------------

    void write2D(ObjectTerritoriesResult result) {
        line("image " + result.getImageWidth() + " " + result.getImageHeight()
                + " " + bits(result.getPixelWidth()) + " " + bits(result.getPixelHeight())
                + " unit=" + quote(result.getSpatialUnit()));
        line("warnings " + result.getWarnings().size());
        for (int i = 0; i < result.getWarnings().size(); i++) {
            line("  warning[" + i + "] " + quote(result.getWarnings().get(i)));
        }
        line("objects " + result.getObjects().size());
        for (int i = 0; i < result.getObjects().size(); i++) {
            SpatialObject2D object = result.getObjects().get(i);
            line("  object " + object.getIndex()
                    + " type=" + object.getTypeIndex()
                    + " name=" + quote(object.getTypeName())
                    + " label=" + object.getLabel()
                    + " cx=" + bits(object.getCentroidX())
                    + " cy=" + bits(object.getCentroidY())
                    + " area=" + bits(object.getArea()));
        }
        line("regions " + result.getRegions().size());
        for (int i = 0; i < result.getRegions().size(); i++) {
            RegionAnalysisResult region = result.getRegions().get(i);
            line("  region[" + i + "] " + quote(region.getRegionName()));
            writeTerritories(region.getTerritories());
            writeInteractions(region.getInteractions());
            writeDensities(region.getDensityResults());
        }
    }

    private void writeTerritories(TerritoryResult territories) {
        if (territories == null) {
            line("    territories absent");
            return;
        }
        line("    territories " + quote(territories.getRegionName())
                + " cells=" + territories.getCells().size());
        for (int i = 0; i < territories.getCells().size(); i++) {
            TerritoryCell cell = territories.getCells().get(i);
            line("      cell object=" + cell.getObject().getIndex()
                    + " label=" + cell.getObject().getLabel()
                    + " area=" + bits(cell.getArea())
                    + " edge=" + cell.isEdgeCell()
                    + " neighbors=" + integers(cell.getNeighborObjectIndices())
                    + " geometry=" + geometry(cell));
        }
        writeRegularity(territories.getRegularity());
    }

    private void writeRegularity(RegularityResult regularity) {
        line("      regularity included=" + regularity.getIncludedObjects()
                + " sizeCv=" + bits(regularity.getTerritorySizeCoefficientOfVariation())
                + " areaCv=" + bits(regularity.getTerritoryAreaCoefficientOfVariation())
                + " nnMean=" + bits(regularity.getNearestNeighborMean())
                + " nnSd=" + bits(regularity.getNearestNeighborStandardDeviation())
                + " nnRatio=" + bits(regularity.getNearestNeighborRegularityRatio()));
    }

    private void writeInteractions(InteractionMatrixResult interactions) {
        if (interactions == null) {
            line("    interactions absent");
            return;
        }
        List<String> types = interactions.getTypes();
        int[][] counts = interactions.getCounts();
        double[][] expected = interactions.getExpectedCounts();
        double[][] zScores = interactions.getZScores();
        double[][] pValues = interactions.getTwoSidedPValues();
        line("    interactions types=" + types.size()
                + " permutations=" + interactions.getPermutations()
                + " seed=" + interactions.getSeed());
        for (int a = 0; a < types.size(); a++) {
            for (int b = 0; b < types.size(); b++) {
                line("      pair " + quote(types.get(a)) + " " + quote(types.get(b))
                        + " observed=" + counts[a][b]
                        + " expected=" + bits(expected[a][b])
                        + " z=" + bits(zScores[a][b])
                        + " p=" + bits(pValues[a][b]));
            }
        }
    }

    private void writeDensities(List<DensityResult> densities) {
        line("    densities " + densities.size());
        for (int i = 0; i < densities.size(); i++) {
            DensityResult density = densities.get(i);
            line("      density region=" + quote(density.getRegionName())
                    + " type=" + quote(density.getTypeName())
                    + " weighting=" + density.getWeighting()
                    + " boundary=" + density.getBoundaryMode()
                    + " bandwidth=" + bits(density.getBandwidthMicrons()));
            writeLocalDensity(density.getLocalDensityByObjectIndex());
            line("        map " + image(density.getDensityMap()));
        }
    }

    // ------------------------------------------------------------------
    // 3D
    // ------------------------------------------------------------------

    void write3D(ObjectTerritoriesResult3D result) {
        line("warnings " + result.getWarnings().size());
        for (int i = 0; i < result.getWarnings().size(); i++) {
            line("  warning[" + i + "] " + quote(result.getWarnings().get(i)));
        }
        line("objects " + result.getObjects().size());
        for (int i = 0; i < result.getObjects().size(); i++) {
            SpatialObject3D object = result.getObjects().get(i);
            line("  object " + object.getIndex()
                    + " type=" + object.getTypeIndex()
                    + " name=" + quote(object.getTypeName())
                    + " label=" + object.getLabel()
                    + " cx=" + bits(object.getCentroidX())
                    + " cy=" + bits(object.getCentroidY())
                    + " cz=" + bits(object.getCentroidZ())
                    + " volume=" + bits(object.getVolume()));
        }
        line("regions " + result.getRegions().size());
        for (int i = 0; i < result.getRegions().size(); i++) {
            RegionAnalysisResult3D region = result.getRegions().get(i);
            line("  region[" + i + "] " + quote(region.getRegionName()));
            writeTerritories3D(region.getTerritories());
            writeInteractions(region.getInteractions());
            writeDensities3D(region.getDensityResults());
        }
    }

    private void writeTerritories3D(TerritoryResult3D territories) {
        if (territories == null) {
            line("    territories absent");
            return;
        }
        line("    territories " + quote(territories.getRegionName())
                + " cells=" + territories.getCells().size());
        for (int i = 0; i < territories.getCells().size(); i++) {
            TerritoryCell3D cell = territories.getCells().get(i);
            line("      cell object=" + cell.getObject().getIndex()
                    + " label=" + cell.getObject().getLabel()
                    + " voxels=" + cell.getVoxelCount()
                    + " volume=" + bits(cell.getVolume())
                    + " edge=" + cell.isEdgeCell()
                    + " neighbors=" + integers(cell.getNeighborObjectIndices()));
        }
        writeRegularity(territories.getRegularity());
        line("      labels " + image(territories.getTerritoryLabels()));
    }

    private void writeDensities3D(List<DensityResult3D> densities) {
        line("    densities " + densities.size());
        for (int i = 0; i < densities.size(); i++) {
            DensityResult3D density = densities.get(i);
            line("      density region=" + quote(density.getRegionName())
                    + " type=" + quote(density.getTypeName())
                    + " weighting=" + density.getWeighting()
                    + " boundary=" + density.getBoundaryMode()
                    + " bandwidth=" + bits(density.getBandwidth()));
            writeLocalDensity(density.getLocalDensityByObjectIndex());
            line("        volume " + image(density.getDensityVolume()));
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    private void writeLocalDensity(Map<Integer, Double> localDensity) {
        StringBuilder builder = new StringBuilder("        loo ");
        builder.append(localDensity.size());
        Iterator<Map.Entry<Integer, Double>> entries =
                localDensity.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Double> entry = entries.next();
            builder.append(' ').append(entry.getKey()).append('=')
                    .append(bits(entry.getValue().doubleValue()));
        }
        line(builder.toString());
    }

    /**
     * Renders a generated image as a digest over every pixel's raw bits, plus
     * the summary values needed to say roughly where a digest mismatch lies.
     */
    static String image(ImagePlus image) {
        if (image == null) return "absent";
        int width = image.getWidth();
        int height = image.getHeight();
        int depth = image.getStackSize();
        Calibration calibration = image.getCalibration();
        MessageDigest digest = digest();
        byte[] word = new byte[4];
        long finiteCount = 0;
        long nanCount = 0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        ImageStack stack = image.getStack();
        for (int z = 1; z <= depth; z++) {
            ImageProcessor processor = stack.getProcessor(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float value = processor.getf(x, y);
                    int raw = Float.floatToRawIntBits(value);
                    word[0] = (byte) (raw >>> 24);
                    word[1] = (byte) (raw >>> 16);
                    word[2] = (byte) (raw >>> 8);
                    word[3] = (byte) raw;
                    digest.update(word);
                    if (Float.isNaN(value)) {
                        nanCount++;
                    } else {
                        finiteCount++;
                        minimum = Math.min(minimum, value);
                        maximum = Math.max(maximum, value);
                        sum += value;
                    }
                }
            }
        }
        return "title=" + quote(image.getTitle())
                + " w=" + width + " h=" + height + " d=" + depth
                + " cal=" + bits(calibration.pixelWidth)
                + "," + bits(calibration.pixelHeight)
                + "," + bits(calibration.pixelDepth)
                + " unit=" + quote(calibration.getUnit())
                + " finite=" + finiteCount + " nan=" + nanCount
                + " min=" + bits(minimum) + " max=" + bits(maximum)
                + " sum=" + bits(sum)
                + " sha256=" + hex(digest.digest());
    }

    static String bits(double value) {
        return String.format("%016x", Long.valueOf(Double.doubleToRawLongBits(value)));
    }

    static String quote(String value) {
        if (value == null) return "<null>";
        return "[" + value + "]";
    }

    private static String integers(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(values.get(i));
        }
        return builder.append('}').toString();
    }

    /**
     * Digests a territory polygon through its well-known-text form. The clipped
     * Voronoi cell is the plugin's headline output, and its exact vertices —
     * not merely its area — are what the overlay and the exported map are drawn
     * from, so they belong in the gate.
     */
    private static String geometry(TerritoryCell cell) {
        MessageDigest digest = digest();
        digest.update(utf8(cell.getGeometry().toText()));
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required", missing);
        }
    }

    static byte[] utf8(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is required", impossible);
        }
    }

    static String hex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (int i = 0; i < value.length; i++) {
            builder.append(String.format("%02x", Integer.valueOf(value[i] & 0xff)));
        }
        return builder.toString();
    }
}
