package territories.macro;

import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritoriesParameters;
import territories.api.RegionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed, UI-independent ImageJ macro settings. */
public final class ObjectTerritoriesMacroOptions {

    private final List<String> labelTitles;
    private final String roiZipPath;
    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidthMicrons;
    private final int permutations;
    private final long seed;
    private final String outputDirectory;
    private final boolean hideResults;

    ObjectTerritoriesMacroOptions(
            List<String> labelTitles,
            String roiZipPath,
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgeCellPolicy,
            DensityWeightingSelection densityWeightingSelection,
            DensityBoundaryMode densityBoundaryMode,
            double bandwidthMicrons,
            int permutations,
            long seed,
            String outputDirectory,
            boolean hideResults) {
        this.labelTitles = Collections.unmodifiableList(new ArrayList<String>(labelTitles));
        this.roiZipPath = roiZipPath;
        this.analysisMode = analysisMode;
        this.regionMode = regionMode;
        this.edgeCellPolicy = edgeCellPolicy;
        this.densityWeightingSelection = densityWeightingSelection;
        this.densityBoundaryMode = densityBoundaryMode;
        this.bandwidthMicrons = bandwidthMicrons;
        this.permutations = permutations;
        this.seed = seed;
        this.outputDirectory = outputDirectory;
        this.hideResults = hideResults;
    }

    public List<String> getLabelTitles() {
        return labelTitles;
    }

    public String getRoiZipPath() {
        return roiZipPath;
    }

    public AnalysisMode getAnalysisMode() {
        return analysisMode;
    }

    public RegionMode getRegionMode() {
        return regionMode;
    }

    public EdgeCellPolicy getEdgeCellPolicy() {
        return edgeCellPolicy;
    }

    public DensityWeightingSelection getDensityWeightingSelection() {
        return densityWeightingSelection;
    }

    public DensityBoundaryMode getDensityBoundaryMode() {
        return densityBoundaryMode;
    }

    public double getBandwidthMicrons() {
        return bandwidthMicrons;
    }

    public int getPermutations() {
        return permutations;
    }

    public long getSeed() {
        return seed;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public boolean isHideResults() {
        return hideResults;
    }

    public static ObjectTerritoriesMacroOptions create(
            List<String> labelTitles,
            String roiZipPath,
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgeCellPolicy,
            DensityWeightingSelection densityWeightingSelection,
            DensityBoundaryMode densityBoundaryMode,
            double bandwidthMicrons,
            int permutations,
            long seed,
            String outputDirectory,
            boolean hideResults) {
        ObjectTerritoriesMacroOptions options = new ObjectTerritoriesMacroOptions(
                labelTitles, roiZipPath, analysisMode, regionMode, edgeCellPolicy,
                densityWeightingSelection, densityBoundaryMode, bandwidthMicrons,
                permutations, seed, blankToNull(outputDirectory), hideResults);
        return MacroOptionsParser.parse(options.toMacroOptions());
    }

    /** Serialises complete, replayable ImageJ macro options. */
    public String toMacroOptions() {
        StringBuilder result = new StringBuilder();
        append(result, "mode", lower(analysisMode));
        for (int i = 0; i < labelTitles.size(); i++) {
            appendBracketed(result, "label" + (i + 1), labelTitles.get(i));
        }
        appendBracketed(result, "regions", slashPath(roiZipPath));
        append(result, "region_mode", lower(regionMode));
        append(result, "edge_cells", lower(edgeCellPolicy));
        append(result, "density_weighting", lower(densityWeightingSelection));
        append(result, "boundary", lower(densityBoundaryMode));
        append(result, "bandwidth", bandwidthMicrons == 0.0 ? "auto" : Double.toString(bandwidthMicrons));
        append(result, "permutations", Integer.toString(permutations));
        append(result, "seed", Long.toString(seed));
        if (outputDirectory != null) {
            appendBracketed(result, "output", slashPath(outputDirectory));
        }
        if (hideResults) {
            if (result.length() > 0) result.append(' ');
            result.append("hide_results");
        }
        return result.toString();
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void append(StringBuilder target, String key, String value) {
        if (target.length() > 0) target.append(' ');
        target.append(key).append('=').append(value);
    }

    private static void appendBracketed(StringBuilder target, String key, String value) {
        validateBracketed(value, key);
        append(target, key, "[" + value + "]");
    }

    private static void validateBracketed(String value, String key) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(key + " must not be empty");
        }
        if (value.indexOf('[') >= 0 || value.indexOf(']') >= 0
                || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalStateException(key + " contains characters that cannot be recorded safely");
        }
    }

    private static String slashPath(String value) {
        return value == null ? null : value.replace('\\', '/');
    }

    static ObjectTerritoriesMacroOptions defaults(List<String> labels, String regions) {
        return new ObjectTerritoriesMacroOptions(
                labels,
                regions,
                AnalysisMode.BOTH,
                RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.BOTH,
                DensityBoundaryMode.CORRECTED,
                0.0,
                ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS,
                ObjectTerritoriesParameters.DEFAULT_SEED,
                null,
                false);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
