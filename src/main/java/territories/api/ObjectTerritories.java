package territories.api;

import ij.ImagePlus;
import ij.measure.Calibration;
import territories.core.DensityEngine;
import territories.core.DensityResult;
import territories.core.DensityEngine3D;
import territories.core.DensityResult3D;
import territories.core.InteractionEngine;
import territories.core.InteractionMatrixResult;
import territories.core.LabelObjectExtractor;
import territories.core.LabelObjectExtractor3D;
import territories.core.NeighborhoodCell;
import territories.core.RegionFactory;
import territories.core.SpatialObject2D;
import territories.core.SpatialObject3D;
import territories.core.SpatialRegion2D;
import territories.core.RegionMask3D;
import territories.core.RegionMaskFactory3D;
import territories.core.TerritoryEngine;
import territories.core.TerritoryEngine3D;
import territories.core.TerritoryResult;
import territories.core.TerritoryResult3D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public Java facade for Object Territories.
 *
 * <p>This method does not show windows, write files, mutate input images, or
 * use ImageJ's global Results table. Density images returned in the result are
 * owned by the caller.
 */
public final class ObjectTerritories {

    private static final double CALIBRATION_TOLERANCE = 1.0e-12;

    private ObjectTerritories() {
    }

    public static ObjectTerritoriesResult analyze(ObjectTerritoriesParameters parameters) {
        validate(parameters);
        List<ImagePlus> labelImages = parameters.getLabelImages();
        ImagePlus reference = labelImages.get(0);
        Calibration calibration = reference.getCalibration();
        double pixelWidth = calibratedSize(calibration.pixelWidth);
        double pixelHeight = calibratedSize(calibration.pixelHeight);
        String unit = calibration.getUnit();

        ArrayList<SpatialObject2D> objects = new ArrayList<SpatialObject2D>();
        ArrayList<String> typeNames = new ArrayList<String>(labelImages.size());
        int firstIndex = 0;
        for (int type = 0; type < labelImages.size(); type++) {
            ImagePlus image = labelImages.get(type);
            typeNames.add(image.getTitle());
            List<SpatialObject2D> extracted =
                    LabelObjectExtractor.extract(image, type, firstIndex);
            objects.addAll(extracted);
            firstIndex += extracted.size();
        }

        List<SpatialRegion2D> regions = RegionFactory.create(
                parameters.getRegions(), parameters.getRegionMode(), pixelWidth, pixelHeight);
        ArrayList<RegionAnalysisResult> analyses =
                new ArrayList<RegionAnalysisResult>(regions.size());
        for (SpatialRegion2D region : regions) {
            TerritoryResult territoryResult = null;
            InteractionMatrixResult interactionResult = null;
            if (parameters.getAnalysisMode() != AnalysisMode.DENSITY) {
                territoryResult = TerritoryEngine.analyze(
                        objects, region, parameters.getEdgeCellPolicy());
                interactionResult = InteractionEngine.analyze(
                        summaryCells(
                                territoryResult.getCells(),
                                parameters.getEdgeCellPolicy()),
                        typeNames,
                        parameters.getPermutations(),
                        parameters.getSeed());
            }

            ArrayList<DensityResult> densityResults = new ArrayList<DensityResult>();
            if (parameters.getAnalysisMode() != AnalysisMode.TERRITORIES) {
                for (String typeName : typeNames) {
                    for (DensityWeighting weighting :
                            concreteWeightings(parameters.getDensityWeightingSelection())) {
                        densityResults.add(DensityEngine.generate(
                                objects,
                                region,
                                typeName,
                                reference.getWidth(),
                                reference.getHeight(),
                                pixelWidth,
                                pixelHeight,
                                unit,
                                parameters.getBandwidthMicrons(),
                                weighting,
                                parameters.getDensityBoundaryMode()));
                    }
                }
            }
            analyses.add(new RegionAnalysisResult(
                    region.getName(), territoryResult, interactionResult, densityResults));
        }

        ArrayList<String> warnings = new ArrayList<String>();
        if (unit == null || unit.trim().isEmpty() || unit.equalsIgnoreCase("pixel")) {
            warnings.add("Images are not spatially calibrated; distances and areas are in pixels.");
        }
        return new ObjectTerritoriesResult(
                objects,
                analyses,
                warnings,
                reference.getWidth(),
                reference.getHeight(),
                pixelWidth,
                pixelHeight,
                unit);
    }

    public static ObjectTerritoriesResult3D analyze3D(
            ObjectTerritoriesParameters3D parameters) {
        validate3D(parameters);
        List<ImagePlus> labelImages = parameters.getLabelImages();
        ArrayList<SpatialObject3D> objects = new ArrayList<SpatialObject3D>();
        ArrayList<String> typeNames = new ArrayList<String>(labelImages.size());
        int firstIndex = 0;
        for (int type = 0; type < labelImages.size(); type++) {
            ImagePlus image = labelImages.get(type);
            typeNames.add(image.getTitle());
            List<SpatialObject3D> extracted =
                    LabelObjectExtractor3D.extract(image, type, firstIndex);
            objects.addAll(extracted);
            firstIndex += extracted.size();
        }

        List<RegionMask3D> regions = RegionMaskFactory3D.create(
                parameters.getRegionMask(), parameters.getRegionMode());
        assertReasonable3DOutputMemory(parameters, regions.size(), typeNames.size());
        ArrayList<RegionAnalysisResult3D> analyses =
                new ArrayList<RegionAnalysisResult3D>(regions.size());
        for (RegionMask3D region : regions) {
            TerritoryResult3D territoryResult = null;
            InteractionMatrixResult interactionResult = null;
            if (parameters.getAnalysisMode() != AnalysisMode.DENSITY) {
                territoryResult = TerritoryEngine3D.analyze(
                        objects, region, parameters.getEdgeCellPolicy());
                interactionResult = InteractionEngine.analyze(
                        summaryCells(
                                territoryResult.getCells(),
                                parameters.getEdgeCellPolicy()),
                        typeNames,
                        parameters.getPermutations(),
                        parameters.getSeed());
            }
            ArrayList<DensityResult3D> densityResults =
                    new ArrayList<DensityResult3D>();
            if (parameters.getAnalysisMode() != AnalysisMode.TERRITORIES) {
                for (String typeName : typeNames) {
                    for (DensityWeighting weighting :
                            concreteWeightings(parameters.getDensityWeightingSelection())) {
                        densityResults.add(DensityEngine3D.generate(
                                objects,
                                region,
                                typeName,
                                parameters.getBandwidth(),
                                weighting,
                                parameters.getDensityBoundaryMode()));
                    }
                }
            }
            analyses.add(new RegionAnalysisResult3D(
                    region.getName(), territoryResult, interactionResult, densityResults));
        }

        ArrayList<String> warnings = new ArrayList<String>();
        String unit = parameters.getRegionMask().getCalibration().getUnit();
        if (unit == null || unit.trim().isEmpty() || unit.equalsIgnoreCase("pixel")) {
            warnings.add(
                    "Stacks are not spatially calibrated; distances, areas, and volumes use pixels.");
        }
        return new ObjectTerritoriesResult3D(objects, analyses, warnings);
    }

    private static void validate(ObjectTerritoriesParameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("parameters must not be null");
        List<ImagePlus> labels = parameters.getLabelImages();
        if (labels.isEmpty() || labels.size() > 5) {
            throw new IllegalArgumentException("supply between one and five label images");
        }
        if (parameters.getRegions().isEmpty()) {
            throw new IllegalArgumentException("at least one region ROI is required");
        }

        ImagePlus reference = labels.get(0);
        if (reference == null) throw new IllegalArgumentException("label images must not contain null");
        Calibration referenceCalibration = reference.getCalibration();
        Set<String> names = new HashSet<String>();
        for (ImagePlus image : labels) {
            if (image == null) throw new IllegalArgumentException("label images must not contain null");
            if (image.getStackSize() != 1) {
                throw new IllegalArgumentException(
                        "label image '" + image.getTitle() + "' is not two-dimensional");
            }
            if (image.getWidth() != reference.getWidth()
                    || image.getHeight() != reference.getHeight()) {
                throw new IllegalArgumentException("all label images must have identical dimensions");
            }
            Calibration calibration = image.getCalibration();
            if (!same(calibratedSize(referenceCalibration.pixelWidth),
                    calibratedSize(calibration.pixelWidth))
                    || !same(calibratedSize(referenceCalibration.pixelHeight),
                    calibratedSize(calibration.pixelHeight))
                    || !safeUnit(referenceCalibration.getUnit()).equals(
                    safeUnit(calibration.getUnit()))) {
                throw new IllegalArgumentException(
                        "all label images must have identical spatial calibration");
            }
            if (Math.abs(calibration.xOrigin) > CALIBRATION_TOLERANCE
                    || Math.abs(calibration.yOrigin) > CALIBRATION_TOLERANCE) {
                throw new IllegalArgumentException(
                        "non-zero calibration origins are not supported in version 0.1");
            }
            String name = image.getTitle();
            if (name == null || name.trim().isEmpty() || !names.add(name)) {
                throw new IllegalArgumentException("label image titles must be non-empty and unique");
            }
        }
    }

    private static void validate3D(ObjectTerritoriesParameters3D parameters) {
        if (parameters == null) throw new IllegalArgumentException("parameters must not be null");
        List<ImagePlus> labels = parameters.getLabelImages();
        if (labels.isEmpty() || labels.size() > 5) {
            throw new IllegalArgumentException("supply between one and five 3D label images");
        }
        ImagePlus mask = parameters.getRegionMask();
        if (mask == null) throw new IllegalArgumentException("a 3D region-mask image is required");
        ImagePlus reference = labels.get(0);
        if (reference == null) throw new IllegalArgumentException("label images must not contain null");
        Calibration referenceCalibration = reference.getCalibration();
        Set<String> names = new HashSet<String>();
        for (ImagePlus image : labels) {
            validateVolumeShape(image, reference, "label image");
            validateVolumeCalibration(image.getCalibration(), referenceCalibration);
            if (image == mask) {
                throw new IllegalArgumentException(
                        "the region mask must be separate from the object label images");
            }
            String name = image.getTitle();
            if (name == null || name.trim().isEmpty() || !names.add(name)) {
                throw new IllegalArgumentException(
                        "3D label image titles must be non-empty and unique");
            }
        }
        validateVolumeShape(mask, reference, "region mask");
        validateVolumeCalibration(mask.getCalibration(), referenceCalibration);
    }

    private static void validateVolumeShape(
            ImagePlus image, ImagePlus reference, String role) {
        if (image == null) throw new IllegalArgumentException(role + " must not be null");
        if (image.getNChannels() != 1 || image.getNFrames() != 1
                || image.getStackSize() < 2) {
            throw new IllegalArgumentException(
                    role + " '" + image.getTitle() + "' must be one 3D z stack");
        }
        if (image.getWidth() != reference.getWidth()
                || image.getHeight() != reference.getHeight()
                || image.getStackSize() != reference.getStackSize()) {
            throw new IllegalArgumentException(
                    "all 3D label images and the region mask must have identical dimensions");
        }
    }

    private static void validateVolumeCalibration(
            Calibration calibration, Calibration reference) {
        if (!same(calibratedSize(reference.pixelWidth), calibratedSize(calibration.pixelWidth))
                || !same(calibratedSize(reference.pixelHeight), calibratedSize(calibration.pixelHeight))
                || !same(calibratedSize(reference.pixelDepth), calibratedSize(calibration.pixelDepth))
                || !safeUnit(reference.getUnit()).equals(safeUnit(calibration.getUnit()))) {
            throw new IllegalArgumentException(
                    "all 3D label images and the region mask must have identical calibration");
        }
        if (Math.abs(calibration.xOrigin) > CALIBRATION_TOLERANCE
                || Math.abs(calibration.yOrigin) > CALIBRATION_TOLERANCE
                || Math.abs(calibration.zOrigin) > CALIBRATION_TOLERANCE) {
            throw new IllegalArgumentException(
                    "non-zero calibration origins are not supported in version 0.1");
        }
    }

    private static List<DensityWeighting> concreteWeightings(
            DensityWeightingSelection selection) {
        ArrayList<DensityWeighting> result = new ArrayList<DensityWeighting>(2);
        if (selection == DensityWeightingSelection.OBJECT_COUNT
                || selection == DensityWeightingSelection.BOTH) {
            result.add(DensityWeighting.OBJECT_COUNT);
        }
        if (selection == DensityWeightingSelection.OBJECT_SIZE
                || selection == DensityWeightingSelection.OBJECT_AREA
                || selection == DensityWeightingSelection.BOTH) {
            result.add(DensityWeighting.OBJECT_SIZE);
        }
        return result;
    }

    private static <T extends NeighborhoodCell> List<T> summaryCells(
            List<T> cells, EdgeCellPolicy edgeCellPolicy) {
        if (edgeCellPolicy == EdgeCellPolicy.INCLUDE_FLAGGED) return cells;
        ArrayList<T> result = new ArrayList<T>();
        for (T cell : cells) {
            if (!cell.isEdgeCell()) result.add(cell);
        }
        return result;
    }

    private static void assertReasonable3DOutputMemory(
            ObjectTerritoriesParameters3D parameters,
            int regionCount,
            int typeCount) {
        long voxels = (long) parameters.getRegionMask().getWidth()
                * parameters.getRegionMask().getHeight()
                * parameters.getRegionMask().getStackSize();
        int densityWeightCount =
                parameters.getDensityWeightingSelection() == DensityWeightingSelection.BOTH
                        ? 2 : 1;
        long generatedVolumes = 0;
        if (parameters.getAnalysisMode() != AnalysisMode.DENSITY) {
            generatedVolumes += regionCount;
        }
        if (parameters.getAnalysisMode() != AnalysisMode.TERRITORIES) {
            generatedVolumes += (long) regionCount * typeCount * densityWeightCount;
        }
        long estimatedOutputBytes;
        try {
            estimatedOutputBytes = Math.multiplyExact(
                    Math.multiplyExact(voxels, generatedVolumes), 4L);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("requested 3D outputs exceed Java array limits");
        }
        long estimatedWorkingBytes;
        try {
            estimatedWorkingBytes = Math.addExact(
                    estimatedOutputBytes, Math.multiplyExact(voxels, 12L));
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("requested 3D analysis exceeds Java array limits");
        }
        long safeBudget = (long) (Runtime.getRuntime().maxMemory() * 0.65);
        if (estimatedWorkingBytes > safeBudget) {
            throw new IllegalArgumentException(
                    "requested 3D outputs need approximately "
                            + humanBytes(estimatedWorkingBytes)
                            + " including core working arrays; reduce regions/types, choose one density "
                            + "weighting, or increase Fiji's maximum memory");
        }
    }

    private static String humanBytes(long bytes) {
        double gibibytes = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gibibytes >= 1.0) {
            return String.format(java.util.Locale.ROOT, "%.2f GiB", gibibytes);
        }
        return String.format(
                java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static double calibratedSize(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static boolean same(double first, double second) {
        return Math.abs(first - second)
                <= CALIBRATION_TOLERANCE * Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    }

    private static String safeUnit(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
