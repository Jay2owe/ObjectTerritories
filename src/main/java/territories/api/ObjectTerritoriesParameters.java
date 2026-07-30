package territories.api;

import ij.ImagePlus;
import ij.gui.Roi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable inputs and scientific settings for an Object Territories analysis.
 *
 * <p>The API does not mutate the supplied images or ROIs. Version 0.1 accepts
 * one-slice label images; the contract leaves dimensional expansion to a future
 * release without changing the result types.
 */
public final class ObjectTerritoriesParameters {

    public static final int DEFAULT_PERMUTATIONS = 1000;
    public static final long DEFAULT_SEED = 12345L;

    private final List<ImagePlus> labelImages;
    private final List<Roi> regions;
    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidthMicrons;
    private final int permutations;
    private final long seed;

    private ObjectTerritoriesParameters(Builder builder) {
        this.labelImages = immutableCopy(builder.labelImages);
        this.regions = immutableCopy(builder.regions);
        this.analysisMode = builder.analysisMode;
        this.regionMode = builder.regionMode;
        this.edgeCellPolicy = builder.edgeCellPolicy;
        this.densityWeightingSelection = builder.densityWeightingSelection;
        this.densityBoundaryMode = builder.densityBoundaryMode;
        this.bandwidthMicrons = builder.bandwidthMicrons;
        this.permutations = builder.permutations;
        this.seed = builder.seed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ImagePlus> getLabelImages() {
        return labelImages;
    }

    public List<Roi> getRegions() {
        return regions;
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

    /**
     * Returns the manual bandwidth in microns, or {@code 0} for automatic
     * bandwidth selection.
     */
    public double getBandwidthMicrons() {
        return bandwidthMicrons;
    }

    public int getPermutations() {
        return permutations;
    }

    public long getSeed() {
        return seed;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static final class Builder {
        private List<ImagePlus> labelImages = new ArrayList<ImagePlus>();
        private List<Roi> regions = new ArrayList<Roi>();
        private AnalysisMode analysisMode = AnalysisMode.BOTH;
        private RegionMode regionMode = RegionMode.INDEPENDENT;
        private EdgeCellPolicy edgeCellPolicy = EdgeCellPolicy.INCLUDE_FLAGGED;
        private DensityWeightingSelection densityWeightingSelection =
                DensityWeightingSelection.BOTH;
        private DensityBoundaryMode densityBoundaryMode = DensityBoundaryMode.CORRECTED;
        private double bandwidthMicrons;
        private int permutations = DEFAULT_PERMUTATIONS;
        private long seed = DEFAULT_SEED;

        private Builder() {
        }

        public Builder labelImages(List<ImagePlus> value) {
            this.labelImages = requireList(value, "labelImages");
            return this;
        }

        public Builder addLabelImage(ImagePlus value) {
            if (value == null) throw new IllegalArgumentException("label image must not be null");
            this.labelImages.add(value);
            return this;
        }

        public Builder regions(List<Roi> value) {
            this.regions = requireList(value, "regions");
            return this;
        }

        public Builder addRegion(Roi value) {
            if (value == null) throw new IllegalArgumentException("region ROI must not be null");
            this.regions.add(value);
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
                throw new IllegalArgumentException("bandwidthMicrons must be finite and at least zero");
            }
            this.bandwidthMicrons = value;
            return this;
        }

        public Builder permutations(int value) {
            if (value < 1) throw new IllegalArgumentException("permutations must be at least 1");
            this.permutations = value;
            return this;
        }

        public Builder seed(long value) {
            this.seed = value;
            return this;
        }

        public ObjectTerritoriesParameters build() {
            return new ObjectTerritoriesParameters(this);
        }

        private static <T> ArrayList<T> requireList(List<T> value, String name) {
            if (value == null) throw new IllegalArgumentException(name + " must not be null");
            ArrayList<T> copy = new ArrayList<T>(value);
            if (copy.contains(null)) throw new IllegalArgumentException(name + " must not contain null");
            return copy;
        }

        private static <T> T require(T value, String name) {
            if (value == null) throw new IllegalArgumentException(name + " must not be null");
            return value;
        }
    }
}
