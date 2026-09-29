package territories.api;

import ij.ImagePlus;
import ij.measure.Calibration;
import sc.fiji.territories.core.DensityEngine;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.DensityEngine3D;
import sc.fiji.territories.core.DensityResult3D;
import sc.fiji.territories.core.InteractionEngine;
import sc.fiji.territories.core.InteractionMatrixResult;
import sc.fiji.territories.core.LabelObjectExtractor;
import sc.fiji.territories.core.LabelObjectExtractor3D;
import sc.fiji.territories.core.NeighborhoodCell;
import sc.fiji.territories.core.RegionFactory;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.SpatialObject3D;
import sc.fiji.territories.core.SpatialRegion2D;
import sc.fiji.territories.core.RegionMask3D;
import sc.fiji.territories.core.RegionMaskFactory3D;
import sc.fiji.territories.core.TerritoryEngine;
import sc.fiji.territories.core.TerritoryEngine3D;
import sc.fiji.territories.core.TerritoryResult;
import sc.fiji.territories.core.TerritoryResult3D;

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
        return analyze(parameters, ProgressMonitor.NONE);
    }

    /**
     * Runs a 2D analysis, reporting each step to {@code monitor} and stopping
     * with {@link AnalysisCancelledException} if it asks to. The results are
     * identical to {@link #analyze(ObjectTerritoriesParameters)}.
     */
    public static ObjectTerritoriesResult analyze(
            ObjectTerritoriesParameters parameters, ProgressMonitor monitor) {
        validate(parameters);
        if (monitor == null) throw new IllegalArgumentException("progress monitor must not be null");
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
                parameters.getRegions(), EngineOptions.engine(parameters.getRegionMode()),
                pixelWidth, pixelHeight,
                reference.getWidth(), reference.getHeight());
        assertReasonable2DOutputMemory(
                (long) reference.getWidth() * reference.getHeight(),
                regions.size(),
                typeNames.size(),
                parameters.getAnalysisMode(),
                parameters.getDensityWeightingSelection(),
                Runtime.getRuntime().maxMemory());
        ArrayList<RegionAnalysisResult> analyses =
                new ArrayList<RegionAnalysisResult>(regions.size());
        List<DensityWeighting> weightings =
                concreteWeightings(parameters.getDensityWeightingSelection());
        int total = stepCount(
                parameters.getAnalysisMode(), regions.size(), typeNames.size(), weightings.size());
        int done = 0;
        ArrayList<ImagePlus> produced = new ArrayList<ImagePlus>();
        for (SpatialRegion2D region : regions) {
            TerritoryResult territoryResult = null;
            InteractionMatrixResult interactionResult = null;
            if (parameters.getAnalysisMode() != AnalysisMode.DENSITY) {
                checkCancelled(monitor, produced);
                monitor.update("region " + region.getName() + ": territories", done, total);
                territoryResult = TerritoryEngine.analyze(
                        objects, region, EngineOptions.engine(parameters.getEdgeCellPolicy()));
                interactionResult = InteractionEngine.analyze(
                        summaryCells(
                                territoryResult.getCells(),
                                parameters.getEdgeCellPolicy()),
                        typeNames,
                        parameters.getPermutations(),
                        parameters.getSeed());
                done++;
            }

            ArrayList<DensityResult> densityResults = new ArrayList<DensityResult>();
            if (parameters.getAnalysisMode() != AnalysisMode.TERRITORIES) {
                for (String typeName : typeNames) {
                    for (DensityWeighting weighting : weightings) {
                        checkCancelled(monitor, produced);
                        monitor.update(densityStep(region.getName(), typeName, weighting),
                                done, total);
                        DensityResult density = DensityEngine.generate(
                                objects,
                                region,
                                typeName,
                                reference.getWidth(),
                                reference.getHeight(),
                                pixelWidth,
                                pixelHeight,
                                unit,
                                parameters.getBandwidthMicrons(),
                                EngineOptions.engine(weighting),
                                EngineOptions.engine(parameters.getDensityBoundaryMode()));
                        produced.add(density.getDensityMap());
                        densityResults.add(density);
                        done++;
                    }
                }
            }
            analyses.add(new RegionAnalysisResult(
                    region.getName(), territoryResult, interactionResult, densityResults));
        }

        // Escape pressed during the last step must still win: the caller
        // would otherwise show and save a result the user asked to abandon.
        checkCancelled(monitor, produced);
        monitor.update("done", total, total);
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
        return analyze3D(parameters, ProgressMonitor.NONE);
    }

    /**
     * Runs a 3D analysis, reporting each step to {@code monitor} and stopping
     * with {@link AnalysisCancelledException} if it asks to. The results are
     * identical to {@link #analyze3D(ObjectTerritoriesParameters3D)}.
     */
    public static ObjectTerritoriesResult3D analyze3D(
            ObjectTerritoriesParameters3D parameters, ProgressMonitor monitor) {
        validate3D(parameters);
        if (monitor == null) throw new IllegalArgumentException("progress monitor must not be null");
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
                parameters.getRegionMask(), EngineOptions.engine(parameters.getRegionMode()));
        assertReasonable3DOutputMemory(parameters, regions.size(), typeNames.size());
        ArrayList<RegionAnalysisResult3D> analyses =
                new ArrayList<RegionAnalysisResult3D>(regions.size());
        List<DensityWeighting> weightings =
                concreteWeightings(parameters.getDensityWeightingSelection());
        int total = stepCount(
                parameters.getAnalysisMode(), regions.size(), typeNames.size(), weightings.size());
        int done = 0;
        ArrayList<ImagePlus> produced = new ArrayList<ImagePlus>();
        for (RegionMask3D region : regions) {
            TerritoryResult3D territoryResult = null;
            InteractionMatrixResult interactionResult = null;
            if (parameters.getAnalysisMode() != AnalysisMode.DENSITY) {
                checkCancelled(monitor, produced);
                monitor.update("region " + region.getName() + ": territories", done, total);
                territoryResult = TerritoryEngine3D.analyze(
                        objects, region, EngineOptions.engine(parameters.getEdgeCellPolicy()));
                produced.add(territoryResult.getTerritoryLabels());
                interactionResult = InteractionEngine.analyze(
                        summaryCells(
                                territoryResult.getCells(),
                                parameters.getEdgeCellPolicy()),
                        typeNames,
                        parameters.getPermutations(),
                        parameters.getSeed());
                done++;
            }
            ArrayList<DensityResult3D> densityResults =
                    new ArrayList<DensityResult3D>();
            if (parameters.getAnalysisMode() != AnalysisMode.TERRITORIES) {
                for (String typeName : typeNames) {
                    for (DensityWeighting weighting : weightings) {
                        checkCancelled(monitor, produced);
                        monitor.update(densityStep(region.getName(), typeName, weighting),
                                done, total);
                        DensityResult3D density = DensityEngine3D.generate(
                                objects,
                                region,
                                typeName,
                                parameters.getBandwidth(),
                                EngineOptions.engine(weighting),
                                EngineOptions.engine(parameters.getDensityBoundaryMode()));
                        produced.add(density.getDensityVolume());
                        densityResults.add(density);
                        done++;
                    }
                }
            }
            analyses.add(new RegionAnalysisResult3D(
                    region.getName(), territoryResult, interactionResult, densityResults));
        }

        checkCancelled(monitor, produced);
        monitor.update("done", total, total);
        ArrayList<String> warnings = new ArrayList<String>();
        String unit = parameters.getRegionMask().getCalibration().getUnit();
        if (unit == null || unit.trim().isEmpty() || unit.equalsIgnoreCase("pixel")) {
            warnings.add(
                    "Stacks are not spatially calibrated; distances, areas, and volumes use pixels.");
        }
        return new ObjectTerritoriesResult3D(objects, analyses, warnings);
    }

    /** Steps reported to a monitor: territories per region plus one per density map. */
    static int stepCount(AnalysisMode mode, int regions, int types, int weightings) {
        int perRegion = 0;
        if (mode != AnalysisMode.DENSITY) perRegion++;
        if (mode != AnalysisMode.TERRITORIES) perRegion += types * weightings;
        return regions * perRegion;
    }

    private static String densityStep(String region, String type, DensityWeighting weighting) {
        return "region " + region + ": density " + type + " ("
                + weighting.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ")";
    }

    /**
     * Stops between steps when asked. Images produced so far belong to a
     * result the caller will never receive, so they are closed here.
     */
    private static void checkCancelled(ProgressMonitor monitor, List<ImagePlus> produced) {
        if (!monitor.isCancelled()) return;
        for (ImagePlus image : produced) {
            if (image == null) continue;
            image.close();
            image.flush();
        }
        produced.clear();
        throw new AnalysisCancelledException();
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
            rejectRgb(image, "label image");
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
                        "non-zero calibration origins are not supported in version 0.2");
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
        long voxels = (long) reference.getWidth() * reference.getHeight()
                * reference.getStackSize();
        if (voxels > MAX_VOXELS) {
            throw new IllegalArgumentException(
                    "the 3D stacks have " + voxels + " voxels; at most " + MAX_VOXELS
                            + " voxels per stack can be analysed (Java array limit); "
                            + "crop or downsample the stacks");
        }
    }

    /** Largest voxel count the core's int-indexed volume arrays can hold. */
    static final long MAX_VOXELS = Integer.MAX_VALUE - 8L;

    private static void rejectRgb(ImagePlus image, String role) {
        if (image.getType() == ImagePlus.COLOR_RGB) {
            throw new IllegalArgumentException(
                    role + " '" + image.getTitle() + "' is RGB colour, whose packed colours "
                            + "cannot be read as labels; convert it to an 8-, 16- or 32-bit "
                            + "label image");
        }
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
        rejectRgb(image, role);
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
                    "non-zero calibration origins are not supported in version 0.2");
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

    /**
     * 2D counterpart of the 3D guard: every region x type x weighting density
     * map is a float image the caller receives, plus one int component array
     * per map while it is computed. Without the guard a large field with
     * several regions ends in an out-of-memory crash part-way through.
     */
    static void assertReasonable2DOutputMemory(
            long pixels,
            int regionCount,
            int typeCount,
            AnalysisMode analysisMode,
            DensityWeightingSelection weighting,
            long maxMemory) {
        if (analysisMode == AnalysisMode.TERRITORIES) return;
        int weightCount = weighting == DensityWeightingSelection.BOTH ? 2 : 1;
        long maps = (long) regionCount * typeCount * weightCount;
        long estimatedBytes;
        try {
            estimatedBytes = Math.addExact(
                    Math.multiplyExact(Math.multiplyExact(pixels, maps), 4L),
                    Math.multiplyExact(pixels, 4L));
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("requested 2D density maps exceed Java array limits");
        }
        long safeBudget = (long) (maxMemory * 0.65);
        if (estimatedBytes > safeBudget) {
            throw new IllegalArgumentException(
                    "requested 2D density maps need approximately "
                            + humanBytes(estimatedBytes)
                            + " (" + maps + " maps); reduce regions/types, choose one density "
                            + "weighting, run territories only, or increase Fiji's maximum memory");
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
