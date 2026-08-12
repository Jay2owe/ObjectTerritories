package territories.equivalence;

import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.RegionMode;

import java.util.ArrayList;
import java.util.List;

/**
 * One point in the documented configuration space.
 *
 * <p>{@link #cross()} is the complete legal cross-product of every enumerated
 * option; {@link #canonical()} is the smaller curated set whose full dumps are
 * kept as readable goldens.
 */
final class Config {

    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidth;
    private final int permutations;
    private final long seed;

    Config(
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgeCellPolicy,
            DensityWeightingSelection densityWeightingSelection,
            DensityBoundaryMode densityBoundaryMode,
            double bandwidth,
            int permutations,
            long seed) {
        this.analysisMode = analysisMode;
        this.regionMode = regionMode;
        this.edgeCellPolicy = edgeCellPolicy;
        this.densityWeightingSelection = densityWeightingSelection;
        this.densityBoundaryMode = densityBoundaryMode;
        this.bandwidth = bandwidth;
        this.permutations = permutations;
        this.seed = seed;
    }

    String getName() {
        return "M-" + analysisMode
                + "_R-" + regionMode
                + "_E-" + edgeCellPolicy
                + "_W-" + densityWeightingSelection
                + "_B-" + densityBoundaryMode
                + "_H-" + (bandwidth > 0.0 ? String.valueOf(bandwidth) : "auto")
                + "_P" + permutations
                + "_S" + seed;
    }

    ObjectTerritoriesParameters.Builder apply(ObjectTerritoriesParameters.Builder builder) {
        return builder
                .analysisMode(analysisMode)
                .regionMode(regionMode)
                .edgeCellPolicy(edgeCellPolicy)
                .densityWeightingSelection(densityWeightingSelection)
                .densityBoundaryMode(densityBoundaryMode)
                .bandwidthMicrons(bandwidth)
                .permutations(permutations)
                .seed(seed);
    }

    ObjectTerritoriesParameters3D.Builder apply(ObjectTerritoriesParameters3D.Builder builder) {
        return builder
                .analysisMode(analysisMode)
                .regionMode(regionMode)
                .edgeCellPolicy(edgeCellPolicy)
                .densityWeightingSelection(densityWeightingSelection)
                .densityBoundaryMode(densityBoundaryMode)
                .bandwidth(bandwidth)
                .permutations(permutations)
                .seed(seed);
    }

    /** Every legal combination of the five documented option enumerations. */
    @SuppressWarnings("deprecation")
    static List<Config> cross() {
        AnalysisMode[] modes = AnalysisMode.values();
        RegionMode[] regionModes = RegionMode.values();
        EdgeCellPolicy[] edgePolicies = EdgeCellPolicy.values();
        DensityWeightingSelection[] weightings = DensityWeightingSelection.values();
        DensityBoundaryMode[] boundaries = DensityBoundaryMode.values();
        double[] bandwidths = new double[] {0.0, 2.0};

        ArrayList<Config> result = new ArrayList<Config>();
        for (int a = 0; a < modes.length; a++) {
            for (int b = 0; b < regionModes.length; b++) {
                for (int c = 0; c < edgePolicies.length; c++) {
                    for (int d = 0; d < weightings.length; d++) {
                        for (int e = 0; e < boundaries.length; e++) {
                            for (int f = 0; f < bandwidths.length; f++) {
                                result.add(new Config(
                                        modes[a], regionModes[b], edgePolicies[c],
                                        weightings[d], boundaries[e], bandwidths[f],
                                        17, 12345L));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * A curated set covering every option value at least once, plus the
     * permutation-count and seed variation the cross-product holds fixed.
     */
    @SuppressWarnings("deprecation")
    static List<Config> canonical() {
        ArrayList<Config> result = new ArrayList<Config>();
        result.add(new Config(
                AnalysisMode.BOTH, RegionMode.INDEPENDENT, EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.BOTH, DensityBoundaryMode.CORRECTED,
                0.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.BOTH, RegionMode.UNION, EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.BOTH, DensityBoundaryMode.CORRECTED,
                0.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.BOTH, RegionMode.INDEPENDENT,
                EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES,
                DensityWeightingSelection.BOTH, DensityBoundaryMode.CLIPPED,
                2.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.TERRITORIES, RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.BOTH, DensityBoundaryMode.CORRECTED,
                0.0, 1, 12345L));
        result.add(new Config(
                AnalysisMode.TERRITORIES, RegionMode.UNION,
                EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES,
                DensityWeightingSelection.BOTH, DensityBoundaryMode.CORRECTED,
                0.0, 101, 7L));
        result.add(new Config(
                AnalysisMode.DENSITY, RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.OBJECT_COUNT, DensityBoundaryMode.CORRECTED,
                0.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.DENSITY, RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.OBJECT_SIZE, DensityBoundaryMode.CLIPPED,
                0.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.DENSITY, RegionMode.UNION, EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.OBJECT_AREA, DensityBoundaryMode.CORRECTED,
                2.0, 17, 12345L));
        result.add(new Config(
                AnalysisMode.BOTH, RegionMode.INDEPENDENT, EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.OBJECT_COUNT, DensityBoundaryMode.CLIPPED,
                2.0, 3, 99L));
        result.add(new Config(
                AnalysisMode.BOTH, RegionMode.UNION,
                EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES,
                DensityWeightingSelection.OBJECT_SIZE, DensityBoundaryMode.CORRECTED,
                2.0, 250, 12345L));
        return result;
    }
}
