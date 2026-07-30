package territories.core;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import territories.api.RegionMode;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/** Converts ImageJ ROIs into valid calibrated JTS analysis regions. */
public final class RegionFactory {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private RegionFactory() {
    }

    public static List<SpatialRegion2D> create(
            List<Roi> rois, RegionMode mode, double pixelWidth, double pixelHeight) {
        if (rois == null || rois.isEmpty()) {
            throw new IllegalArgumentException("at least one region ROI is required");
        }
        if (mode == null) throw new IllegalArgumentException("region mode must not be null");
        if (!Double.isFinite(pixelWidth) || pixelWidth <= 0.0
                || !Double.isFinite(pixelHeight) || pixelHeight <= 0.0) {
            throw new IllegalArgumentException("pixel calibration must be positive and finite");
        }

        ArrayList<SpatialRegion2D> independent = new ArrayList<SpatialRegion2D>(rois.size());
        ArrayList<Geometry> geometries = new ArrayList<Geometry>(rois.size());
        for (int i = 0; i < rois.size(); i++) {
            Roi roi = rois.get(i);
            if (roi == null) throw new IllegalArgumentException("region ROI " + (i + 1) + " is null");
            Geometry geometry = toGeometry(roi, pixelWidth, pixelHeight);
            String name = roi.getName();
            if (name == null || name.trim().isEmpty()) name = "Region_" + (i + 1);
            independent.add(new SpatialRegion2D(name, geometry));
            geometries.add(geometry);
        }
        if (mode == RegionMode.INDEPENDENT) return independent;

        Geometry union = UnaryUnionOp.union(geometries);
        if (union == null || union.isEmpty()) {
            throw new IllegalArgumentException("the union of the region ROIs is empty");
        }
        ArrayList<SpatialRegion2D> result = new ArrayList<SpatialRegion2D>(1);
        result.add(new SpatialRegion2D("All_Regions", union));
        return result;
    }

    private static Geometry toGeometry(Roi roi, double pixelWidth, double pixelHeight) {
        if (!roi.isArea()) {
            throw new IllegalArgumentException("ROI '" + roi.getName() + "' is not an area ROI");
        }
        ShapeRoi shapeRoi = new ShapeRoi(roi);
        Shape shape = shapeRoi.getShape();
        if (shape == null) throw new IllegalArgumentException("ROI has no two-dimensional shape");
        Geometry pixels = ShapeReader.read(shape, 0.25, GEOMETRY_FACTORY);
        if (pixels == null || pixels.isEmpty()) {
            throw new IllegalArgumentException("ROI '" + roi.getName() + "' has no area");
        }
        Geometry imageCoordinates = AffineTransformation.scaleInstance(1.0, -1.0)
                .transform(pixels);
        Geometry positioned = AffineTransformation.translationInstance(
                shapeRoi.getXBase(), shapeRoi.getYBase()).transform(imageCoordinates);
        Geometry calibrated = AffineTransformation.scaleInstance(pixelWidth, pixelHeight)
                .transform(positioned);
        if (!calibrated.isValid()) calibrated = calibrated.buffer(0.0);
        if (calibrated.isEmpty() || calibrated.getArea() <= 0.0) {
            throw new IllegalArgumentException("ROI '" + roi.getName() + "' has no valid area");
        }
        return calibrated;
    }
}
