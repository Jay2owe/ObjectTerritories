package territories.api;

import ij.ImagePlus;
import ij.measure.Calibration;
import territories.core.DensityEngine;
import territories.core.DensityResult;
import territories.core.InteractionEngine;
import territories.core.InteractionMatrixResult;
import territories.core.LabelObjectExtractor;
import territories.core.RegionFactory;
import territories.core.SpatialObject2D;
import territories.core.SpatialRegion2D;
import territories.core.TerritoryEngine;
import territories.core.TerritoryResult;

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
                        territoryResult.getCells(),
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
        return new ObjectTerritoriesResult(objects, analyses, warnings);
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

    private static List<DensityWeighting> concreteWeightings(
            DensityWeightingSelection selection) {
        ArrayList<DensityWeighting> result = new ArrayList<DensityWeighting>(2);
        if (selection == DensityWeightingSelection.OBJECT_COUNT
                || selection == DensityWeightingSelection.BOTH) {
            result.add(DensityWeighting.OBJECT_COUNT);
        }
        if (selection == DensityWeightingSelection.OBJECT_AREA
                || selection == DensityWeightingSelection.BOTH) {
            result.add(DensityWeighting.OBJECT_AREA);
        }
        return result;
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

