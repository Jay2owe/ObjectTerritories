package territories.batch;

import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritoriesParameters;
import territories.api.RegionMode;

import java.io.File;

/**
 * Immutable configuration for an Object Territories folder batch.
 *
 * <p>A 2D batch ({@link #builder}) applies one shared region ROI set to every
 * sample. A 3D batch ({@link #builder3D}) takes each sample's region mask from
 * its own group: the file whose type capture equals the region-mask type
 * (default {@value #DEFAULT_REGION_MASK_TYPE}, case-insensitive) is the
 * region-mask stack and the other files are label stacks.</p>
 */
public final class ObjectTerritoriesBatchParameters {

    /** Type token that marks the region-mask stack in a 3D batch group. */
    public static final String DEFAULT_REGION_MASK_TYPE = "mask";

    private final File inputFolder;
    private final String filenameRegex;
    private final int typeCaptureGroup;
    private final boolean recursive;
    private final File regionSource;
    private final boolean threeDimensional;
    private final String regionMaskType;
    private final File outputDirectory;
    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidthMicrons;
    private final int permutations;
    private final long seed;

    private ObjectTerritoriesBatchParameters(Builder builder) {
        this.inputFolder = builder.inputFolder;
        this.filenameRegex = builder.filenameRegex;
        this.typeCaptureGroup = builder.typeCaptureGroup;
        this.recursive = builder.recursive;
        this.regionSource = builder.regionSource;
        this.threeDimensional = builder.threeDimensional;
        this.regionMaskType = builder.regionMaskType;
        this.outputDirectory = builder.outputDirectory;
        this.analysisMode = builder.analysisMode;
        this.regionMode = builder.regionMode;
        this.edgeCellPolicy = builder.edgeCellPolicy;
        this.densityWeightingSelection = builder.densityWeightingSelection;
        this.densityBoundaryMode = builder.densityBoundaryMode;
        this.bandwidthMicrons = builder.bandwidthMicrons;
        this.permutations = builder.permutations;
        this.seed = builder.seed;
    }

    /** Starts a 2D batch that applies one region ROI file or ROI zip to every sample. */
    public static Builder builder(
            File inputFolder,
            String filenameRegex,
            int typeCaptureGroup,
            File regionSource,
            File outputDirectory) {
        return new Builder(
                inputFolder, filenameRegex, typeCaptureGroup, regionSource, false, null,
                outputDirectory);
    }

    /**
     * Starts a 3D batch: every group holds 1-5 label stacks plus one region-mask
     * stack, identified by its type capture equalling {@code regionMaskType}
     * (case-insensitive; {@code null} means {@value #DEFAULT_REGION_MASK_TYPE}).
     */
    public static Builder builder3D(
            File inputFolder,
            String filenameRegex,
            int typeCaptureGroup,
            String regionMaskType,
            File outputDirectory) {
        return new Builder(
                inputFolder, filenameRegex, typeCaptureGroup, null, true,
                regionMaskType == null ? DEFAULT_REGION_MASK_TYPE : regionMaskType,
                outputDirectory);
    }

    public File getInputFolder() {
        return inputFolder;
    }

    public String getFilenameRegex() {
        return filenameRegex;
    }

    public int getTypeCaptureGroup() {
        return typeCaptureGroup;
    }

    public boolean isRecursive() {
        return recursive;
    }

    /** The shared 2D region ROI file; {@code null} in a 3D batch. */
    public File getRegionSource() {
        return regionSource;
    }

    public boolean isThreeDimensional() {
        return threeDimensional;
    }

    /** The type token of each group's region-mask stack; {@code null} in a 2D batch. */
    public String getRegionMaskType() {
        return regionMaskType;
    }

    public File getOutputDirectory() {
        return outputDirectory;
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

    public static final class Builder {
        private final File inputFolder;
        private final String filenameRegex;
        private final int typeCaptureGroup;
        private final File regionSource;
        private final boolean threeDimensional;
        private final String regionMaskType;
        private final File outputDirectory;
        private boolean recursive = true;
        private AnalysisMode analysisMode = AnalysisMode.BOTH;
        private RegionMode regionMode = RegionMode.INDEPENDENT;
        private EdgeCellPolicy edgeCellPolicy = EdgeCellPolicy.INCLUDE_FLAGGED;
        private DensityWeightingSelection densityWeightingSelection =
                DensityWeightingSelection.BOTH;
        private DensityBoundaryMode densityBoundaryMode = DensityBoundaryMode.CORRECTED;
        private double bandwidthMicrons;
        private int permutations = ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS;
        private long seed = ObjectTerritoriesParameters.DEFAULT_SEED;

        private Builder(
                File inputFolder,
                String filenameRegex,
                int typeCaptureGroup,
                File regionSource,
                boolean threeDimensional,
                String regionMaskType,
                File outputDirectory) {
            this.inputFolder = inputFolder;
            this.filenameRegex = filenameRegex;
            this.typeCaptureGroup = typeCaptureGroup;
            this.regionSource = regionSource;
            this.threeDimensional = threeDimensional;
            this.regionMaskType = regionMaskType;
            this.outputDirectory = outputDirectory;
        }

        public Builder recursive(boolean value) {
            this.recursive = value;
            return this;
        }

        public Builder analysisMode(AnalysisMode value) {
            this.analysisMode = require(value, "analysisMode");
            return this;
        }

        public Builder regionMode(RegionMode value) {
            this.regionMode = require(value, "regionMode");
            return this;
        }

        public Builder edgeCellPolicy(EdgeCellPolicy value) {
            this.edgeCellPolicy = require(value, "edgeCellPolicy");
            return this;
        }

        public Builder densityWeightingSelection(DensityWeightingSelection value) {
            this.densityWeightingSelection = require(value, "densityWeightingSelection");
            return this;
        }

        public Builder densityBoundaryMode(DensityBoundaryMode value) {
            this.densityBoundaryMode = require(value, "densityBoundaryMode");
            return this;
        }

        public Builder bandwidthMicrons(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "bandwidthMicrons must be finite and at least zero");
            }
            this.bandwidthMicrons = value;
            return this;
        }

        public Builder permutations(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("permutations must be at least 1");
            }
            if (value > ObjectTerritoriesParameters.MAX_PERMUTATIONS) {
                throw new IllegalArgumentException("permutations must be at most "
                        + ObjectTerritoriesParameters.MAX_PERMUTATIONS);
            }
            this.permutations = value;
            return this;
        }

        public Builder seed(long value) {
            this.seed = value;
            return this;
        }

        public ObjectTerritoriesBatchParameters build() {
            return new ObjectTerritoriesBatchParameters(this);
        }

        private static <T> T require(T value, String name) {
            if (value == null) throw new IllegalArgumentException(name + " must not be null");
            return value;
        }
    }
}
