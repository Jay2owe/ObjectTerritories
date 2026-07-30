package territories.core;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeighting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-dimensional Gaussian kernel density estimation in calibrated units.
 *
 * <p>Count maps integrate to the number of admitted objects. Area-weighted maps
 * integrate to their summed calibrated areas. Corrected mode renormalises each
 * truncated kernel over the supplied region, preventing artificial edge loss.
 */
public final class DensityEngine {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double KERNEL_RADIUS_IN_SIGMAS = 3.0;

    private DensityEngine() {
    }

    public static DensityResult generate(
            List<SpatialObject2D> allObjects,
            SpatialRegion2D region,
            String typeName,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight,
            String spatialUnit,
            double requestedBandwidthMicrons,
            DensityWeighting weighting,
            DensityBoundaryMode boundaryMode) {
        if (allObjects == null) throw new IllegalArgumentException("objects must not be null");
        if (region == null) throw new IllegalArgumentException("region must not be null");
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("type name must not be empty");
        }
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("density image dimensions are invalid");
        }
        if (!Double.isFinite(pixelWidth) || pixelWidth <= 0.0
                || !Double.isFinite(pixelHeight) || pixelHeight <= 0.0) {
            throw new IllegalArgumentException("pixel calibration must be positive and finite");
        }
        if (weighting == null) throw new IllegalArgumentException("density weighting is required");
        if (boundaryMode == null) throw new IllegalArgumentException("boundary mode is required");
        if (!Double.isFinite(requestedBandwidthMicrons) || requestedBandwidthMicrons < 0.0) {
            throw new IllegalArgumentException("bandwidth must be finite and at least zero");
        }

        Geometry domain = region.geometryReference();
        PreparedGeometry preparedDomain = PreparedGeometryFactory.prepare(domain);
        List<SpatialObject2D> objects = admittedObjects(allObjects, typeName, preparedDomain);
        double bandwidth = requestedBandwidthMicrons > 0.0
                ? requestedBandwidthMicrons
                : automaticBandwidth(objects, pixelWidth, pixelHeight);

        boolean[] mask = rasterMask(
                preparedDomain, width, height, pixelWidth, pixelHeight);
        float[] pixels = new float[width * height];
        for (int i = 0; i < pixels.length; i++) {
            if (!mask[i]) pixels[i] = Float.NaN;
        }

        ArrayList<Kernel> kernels = new ArrayList<Kernel>(objects.size());
        for (SpatialObject2D object : objects) {
            Kernel kernel = kernel(
                    object, mask, width, height, pixelWidth, pixelHeight,
                    bandwidth, weighting, boundaryMode);
            if (kernel != null) kernels.add(kernel);
        }
        for (Kernel kernel : kernels) {
            accumulate(kernel, pixels, mask, width, height, pixelWidth, pixelHeight, bandwidth);
        }

        FloatProcessor processor = new FloatProcessor(width, height, pixels);
        String title = safe(typeName) + "_" + safe(region.getName()) + "_Density";
        ImagePlus image = new ImagePlus(title, processor);
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = pixelWidth;
        calibration.pixelHeight = pixelHeight;
        calibration.setUnit(
                spatialUnit == null || spatialUnit.trim().isEmpty() ? "um" : spatialUnit);

        Map<Integer, Double> localDensity = localDensity(objects, kernels, bandwidth);
        return new DensityResult(
                region.getName(), typeName, weighting, boundaryMode,
                bandwidth, image, localDensity);
    }

    private static List<SpatialObject2D> admittedObjects(
            List<SpatialObject2D> allObjects,
            String typeName,
            PreparedGeometry domain) {
        ArrayList<SpatialObject2D> result = new ArrayList<SpatialObject2D>();
        for (SpatialObject2D object : allObjects) {
            if (object == null) throw new IllegalArgumentException("objects must not contain null");
            if (!object.getTypeName().equals(typeName)) continue;
            Point point = GEOMETRY_FACTORY.createPoint(
                    new Coordinate(object.getCentroidX(), object.getCentroidY()));
            if (domain.covers(point)) result.add(object);
        }
        return result;
    }

    private static boolean[] rasterMask(
            PreparedGeometry domain,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight) {
        boolean[] result = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            double physicalY = (y + 0.5) * pixelHeight;
            for (int x = 0; x < width; x++) {
                double physicalX = (x + 0.5) * pixelWidth;
                result[y * width + x] = domain.covers(GEOMETRY_FACTORY.createPoint(
                        new Coordinate(physicalX, physicalY)));
            }
        }
        return result;
    }

    private static Kernel kernel(
            SpatialObject2D object,
            boolean[] mask,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight,
            double bandwidth,
            DensityWeighting weighting,
            DensityBoundaryMode boundaryMode) {
        Bounds bounds = bounds(object, width, height, pixelWidth, pixelHeight, bandwidth);
        double weight = weighting == DensityWeighting.OBJECT_COUNT ? 1.0 : object.getArea();
        double denominator;
        if (boundaryMode == DensityBoundaryMode.CORRECTED) {
            double supported = gaussianSum(
                    object, bounds, mask, width, pixelWidth, pixelHeight, bandwidth);
            if (supported <= 0.0) return null;
            denominator = supported * pixelWidth * pixelHeight;
        } else {
            denominator = 2.0 * Math.PI * bandwidth * bandwidth;
        }
        return new Kernel(object, bounds, weight / denominator);
    }

    private static void accumulate(
            Kernel kernel,
            float[] pixels,
            boolean[] mask,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight,
            double bandwidth) {
        double inverseTwoBandwidthSquared = 1.0 / (2.0 * bandwidth * bandwidth);
        SpatialObject2D object = kernel.object;
        for (int y = kernel.bounds.minimumY; y <= kernel.bounds.maximumY; y++) {
            double dy = (y + 0.5) * pixelHeight - object.getCentroidY();
            for (int x = kernel.bounds.minimumX; x <= kernel.bounds.maximumX; x++) {
                int index = y * width + x;
                if (!mask[index]) continue;
                double dx = (x + 0.5) * pixelWidth - object.getCentroidX();
                double gaussian = Math.exp(-(dx * dx + dy * dy) * inverseTwoBandwidthSquared);
                pixels[index] += (float) (gaussian * kernel.scale);
            }
        }
    }

    private static double gaussianSum(
            SpatialObject2D object,
            Bounds bounds,
            boolean[] mask,
            int width,
            double pixelWidth,
            double pixelHeight,
            double bandwidth) {
        double inverseTwoBandwidthSquared = 1.0 / (2.0 * bandwidth * bandwidth);
        double result = 0.0;
        for (int y = bounds.minimumY; y <= bounds.maximumY; y++) {
            double dy = (y + 0.5) * pixelHeight - object.getCentroidY();
            for (int x = bounds.minimumX; x <= bounds.maximumX; x++) {
                if (!mask[y * width + x]) continue;
                double dx = (x + 0.5) * pixelWidth - object.getCentroidX();
                result += Math.exp(-(dx * dx + dy * dy) * inverseTwoBandwidthSquared);
            }
        }
        return result;
    }

    private static Map<Integer, Double> localDensity(
            List<SpatialObject2D> objects, List<Kernel> kernels, double bandwidth) {
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<Integer, Double>();
        double inverseTwoBandwidthSquared = 1.0 / (2.0 * bandwidth * bandwidth);
        double radius = KERNEL_RADIUS_IN_SIGMAS * bandwidth;
        for (SpatialObject2D selected : objects) {
            double density = 0.0;
            for (Kernel kernel : kernels) {
                if (kernel.object.getIndex() == selected.getIndex()) continue;
                double dx = selected.getCentroidX() - kernel.object.getCentroidX();
                double dy = selected.getCentroidY() - kernel.object.getCentroidY();
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) continue;
                density += Math.exp(-(dx * dx + dy * dy) * inverseTwoBandwidthSquared)
                        * kernel.scale;
            }
            result.put(selected.getIndex(), density);
        }
        return result;
    }

    static double automaticBandwidth(
            List<SpatialObject2D> objects, double pixelWidth, double pixelHeight) {
        double minimum = Math.max(pixelWidth, pixelHeight);
        if (objects.size() < 2) return minimum * 3.0;
        double meanX = 0.0;
        double meanY = 0.0;
        for (SpatialObject2D object : objects) {
            meanX += object.getCentroidX();
            meanY += object.getCentroidY();
        }
        meanX /= objects.size();
        meanY /= objects.size();
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (SpatialObject2D object : objects) {
            double dx = object.getCentroidX() - meanX;
            double dy = object.getCentroidY() - meanY;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        double scale = (
                Math.sqrt(varianceX / (objects.size() - 1))
                        + Math.sqrt(varianceY / (objects.size() - 1))) / 2.0;
        double scottFactor = Math.pow(objects.size(), -1.0 / 6.0);
        double selected = scale * scottFactor;
        return Double.isFinite(selected) && selected >= minimum ? selected : minimum;
    }

    private static Bounds bounds(
            SpatialObject2D object,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight,
            double bandwidth) {
        double radius = KERNEL_RADIUS_IN_SIGMAS * bandwidth;
        int minimumX = Math.max(0, (int) Math.floor((object.getCentroidX() - radius) / pixelWidth));
        int maximumX = Math.min(
                width - 1, (int) Math.ceil((object.getCentroidX() + radius) / pixelWidth));
        int minimumY = Math.max(0, (int) Math.floor((object.getCentroidY() - radius) / pixelHeight));
        int maximumY = Math.min(
                height - 1, (int) Math.ceil((object.getCentroidY() + radius) / pixelHeight));
        return new Bounds(minimumX, maximumX, minimumY, maximumY);
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static final class Bounds {
        private final int minimumX;
        private final int maximumX;
        private final int minimumY;
        private final int maximumY;

        private Bounds(int minimumX, int maximumX, int minimumY, int maximumY) {
            this.minimumX = minimumX;
            this.maximumX = maximumX;
            this.minimumY = minimumY;
            this.maximumY = maximumY;
        }
    }

    private static final class Kernel {
        private final SpatialObject2D object;
        private final Bounds bounds;
        private final double scale;

        private Kernel(SpatialObject2D object, Bounds bounds, double scale) {
            this.object = object;
            this.bounds = bounds;
            this.scale = scale;
        }
    }
}
