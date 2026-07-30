package territories.output;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.ShapeRoi;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import territories.api.ObjectTerritoriesResult;
import territories.api.RegionAnalysisResult;
import territories.core.DensityResult;
import territories.core.TerritoryCell;

import java.awt.Color;

/** Interactive-only display of tables, overlays, and calibrated density maps. */
public final class ResultPresenter {

    private static final Color[] TYPE_COLORS = {
            new Color(0, 114, 178),
            new Color(213, 94, 0),
            new Color(0, 158, 115),
            new Color(204, 121, 167),
            new Color(230, 159, 0)
    };

    private ResultPresenter() {
    }

    public static void show(ObjectTerritoriesResult result, ImagePlus referenceImage) {
        for (String warning : result.getWarnings()) IJ.log("[Object Territories] " + warning);
        for (RegionAnalysisResult region : result.getRegions()) {
            String suffix = " - " + region.getRegionName();
            ResultTables.objects(result, region).show("Object Territories Objects" + suffix);
            if (region.getInteractions() != null) {
                ResultTables.interactions(region).show("Object Territories Interactions" + suffix);
                ResultTables.regularity(region).show("Object Territories Regularity" + suffix);
                showTerritoryOverlay(region, referenceImage);
            }
            for (DensityResult density : region.getDensityResults()) {
                ImagePlus image = density.getDensityMap();
                IJ.run(image, "mpl-viridis", "");
                image.resetDisplayRange();
                image.show();
                IJ.run(
                        image,
                        "Calibration Bar...",
                        "location=[Upper Right] fill=White label=Black number=5 decimal=3 font=12 zoom=1 overlay");
            }
        }
    }

    private static void showTerritoryOverlay(
            RegionAnalysisResult region, ImagePlus referenceImage) {
        if (referenceImage == null || region.getTerritories() == null) return;
        ImagePlus display = referenceImage.duplicate();
        display.setTitle("Object Territories - " + region.getRegionName());
        Overlay overlay = new Overlay();
        double pixelWidth = calibrated(referenceImage.getCalibration().pixelWidth);
        double pixelHeight = calibrated(referenceImage.getCalibration().pixelHeight);
        ShapeWriter writer = new ShapeWriter();
        for (TerritoryCell cell : region.getTerritories().getCells()) {
            Geometry pixels = AffineTransformation.scaleInstance(
                    1.0 / pixelWidth, 1.0 / pixelHeight).transform(cell.getGeometry());
            ShapeRoi roi = new ShapeRoi(writer.toShape(pixels));
            int type = cell.getObject().getTypeIndex();
            roi.setStrokeColor(TYPE_COLORS[type % TYPE_COLORS.length]);
            roi.setStrokeWidth(cell.isEdgeCell() ? 2.0 : 1.0);
            roi.setName(
                    cell.getObject().getTypeName() + ":" + cell.getObject().getLabel());
            overlay.add(roi);
        }
        display.setOverlay(overlay);
        display.show();
    }

    private static double calibrated(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }
}

