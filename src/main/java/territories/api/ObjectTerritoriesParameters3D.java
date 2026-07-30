package territories.api;

import ij.ImagePlus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable inputs and settings for genuine voxel-based 3D analysis. */
public final class ObjectTerritoriesParameters3D {

    private final List<ImagePlus> labelImages;
    private final ImagePlus regionMask;
    private final AnalysisMode analysisMode;
    private final RegionMode regionMode;
    private final EdgeCellPolicy edgeCellPolicy;
    private final DensityWeightingSelection densityWeightingSelection;
    private final DensityBoundaryMode densityBoundaryMode;
    private final double bandwidth;
    private final int permutations;
    private final long seed;

    private ObjectTerritoriesParameters3D(Builder builder) {
        this.labelImages = Collections.unmodifiableList(
                new ArrayList<ImagePlus>(builder.labelImages));
        this.regionMask = builder.regionMask;
        this.analysisMode = builder.analysisMode;
        this.regionMode = builder.regionMode;
        this.edgeCellPolicy = builder.edgeCellPolicy;
        this.densityWeightingSelection = builder.densityWeightingSelection;
        this.densityBoundaryMode = builder.densityBoundaryMode;
        this.bandwidth = builder.bandwidth;
        this.permutations = builder.permutations;
        this.seed = builder.seed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ImagePlus> getLabelImages() {
        return labelImages;
    }

    public ImagePlus getRegionMask() {
        return regionMask;
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

    public double getBandwidth() {
        return bandwidth;
    }

    public int getPermutations() {
        return permutations;
    }

    public long getSeed() {
        return seed;
    }

    public static final class Builder {
        private final List<ImagePlus> labelImages = new ArrayList<ImagePlus>();
        private ImagePlus regionMask;
        private AnalysisMode analysisMode = AnalysisMode.BOTH;
        private RegionMode regionMode = RegionMode.INDEPENDENT;
        private EdgeCellPolicy edgeCellPolicy = EdgeCellPolicy.INCLUDE_FLAGGED;
        private DensityWeightingSelection densityWeightingSelection =
                DensityWeightingSelection.BOTH;
        private DensityBoundaryMode densityBoundaryMode = DensityBoundaryMode.CORRECTED;
        private double bandwidth;
        private int permutations = ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS;
        private long seed = ObjectTerritoriesParameters.DEFAULT_SEED;

        private Builder() {
        }

        public Builder labelImages(List<ImagePlus> values) {
            if (values == null || values.contains(null)) {
                throw new IllegalArgumentException("label images must not contain null");
            }
            labelImages.clear();
            labelImages.addAll(values);
            return this;
        }

        public Builder addLabelImage(ImagePlus value) {
            if (value == null) throw new IllegalArgumentException("label image must not be null");
            labelImages.add(value);
            return this;
        }

        public Builder regionMask(ImagePlus value) {
            this.regionMask = value;
            return this;
        }

        public Builder analysisMode(AnalysisMode value) {
            this.analysisMode = require(value, "analysis mode");
            return this;
        }

        public Builder regionMode(RegionMode value) {
            this.regionMode = require(value, "region mode");
            return this;
        }

        public Builder edgeCellPolicy(EdgeCellPolicy value) {
            this.edgeCellPolicy = require(value, "edge-cell policy");
            return this;
        }

        public Builder densityWeightingSelection(DensityWeightingSelection value) {
            this.densityWeightingSelection = require(value, "density weighting");
            return this;
        }

        public Builder densityBoundaryMode(DensityBoundaryMode value) {
            this.densityBoundaryMode = require(value, "density boundary mode");
            return this;
        }

        public Builder bandwidth(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("bandwidth must be finite and at least zero");
            }
            this.bandwidth = value;
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

        public ObjectTerritoriesParameters3D build() {
            return new ObjectTerritoriesParameters3D(this);
        }

        private static <T> T require(T value, String name) {
            if (value == null) throw new IllegalArgumentException(name + " is required");
            return value;
        }
    }
}

