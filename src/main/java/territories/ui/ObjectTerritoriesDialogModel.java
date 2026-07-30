package territories.ui;

import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.RegionMode;
import territories.macro.ObjectTerritoriesMacroOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Swing-free dialog state with validation and recorder serialisation. */
public final class ObjectTerritoriesDialogModel {

    private final List<String> labelTitles;
    private final String regionPath;
    private final String regionMaskTitle;
    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidthMicrons;
    private final int permutations;
    private final long seed;
    private final String outputDirectory;
    private final boolean showResults;

    public ObjectTerritoriesDialogModel(
            List<String> labelTitles,
            String regionPath,
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgeCellPolicy,
            DensityWeightingSelection densityWeightingSelection,
            DensityBoundaryMode densityBoundaryMode,
            double bandwidthMicrons,
            int permutations,
            long seed,
            String outputDirectory,
            boolean showResults) {
        this(
                labelTitles, regionPath, null, analysisMode, regionMode,
                edgeCellPolicy, densityWeightingSelection, densityBoundaryMode,
                bandwidthMicrons, permutations, seed, outputDirectory, showResults);
    }

    public ObjectTerritoriesDialogModel(
            List<String> labelTitles,
            String regionPath,
            String regionMaskTitle,
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgeCellPolicy,
            DensityWeightingSelection densityWeightingSelection,
            DensityBoundaryMode densityBoundaryMode,
            double bandwidthMicrons,
            int permutations,
            long seed,
            String outputDirectory,
            boolean showResults) {
        this.labelTitles = Collections.unmodifiableList(
                new ArrayList<String>(labelTitles));
        this.regionPath = regionPath;
        this.regionMaskTitle = regionMaskTitle;
        this.analysisMode = analysisMode;
        this.regionMode = regionMode;
        this.edgeCellPolicy = edgeCellPolicy;
        this.densityWeightingSelection = densityWeightingSelection;
        this.densityBoundaryMode = densityBoundaryMode;
        this.bandwidthMicrons = bandwidthMicrons;
        this.permutations = permutations;
        this.seed = seed;
        this.outputDirectory = outputDirectory;
        this.showResults = showResults;
        toMacroOptions();
    }

    public ObjectTerritoriesMacroOptions toMacroOptions() {
        return ObjectTerritoriesMacroOptions.create(
                labelTitles,
                regionPath,
                regionMaskTitle,
                analysisMode,
                regionMode,
                edgeCellPolicy,
                densityWeightingSelection,
                densityBoundaryMode,
                bandwidthMicrons,
                permutations,
                seed,
                outputDirectory,
                !showResults);
    }

    public String toMacroOptionString() {
        return toMacroOptions().toMacroOptions();
    }
}
