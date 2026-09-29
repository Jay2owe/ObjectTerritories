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
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.TerritoryCell;

/** Interactive-only display of tables, overlays, and calibrated density maps. */
public final class ResultPresenter {

    private ResultPresenter() {
    }

    public static void show(ObjectTerritoriesResult result, ImagePlus referenceImage) {
        DisplayTitles titles = new DisplayTitles();
        for (RegionAnalysisResult region : result.getRegions()) {
            String regionName = titles.region(region.getRegionName());
            String suffix = " - " + regionName;
            ResultTables.objects(result, region).show("Object Territories Objects" + suffix);
            if (region.getInteractions() != null) {
                ResultTables.interactions(region).show("Object Territories Interactions" + suffix);
            }
            if (region.getTerritories() != null) {
                ResultTables.regularity(region).show("Object Territories Regularity" + suffix);
                showTerritoryOverlay(region, regionName, referenceImage);
            }
            for (DensityResult density : region.getDensityResults()) {
                ImagePlus image = density.getDensityMap();
                image.setTitle(titles.image(
                        DisplayTitles.densityTitle(image.getTitle(), density.getWeighting())));
                LookupTables.apply(image, LookupTables.DENSITY, LookupTables.DENSITY_FALLBACK);
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
            RegionAnalysisResult region, String regionName, ImagePlus referenceImage) {
        if (referenceImage == null || region.getTerritories() == null) return;
        ImagePlus display = referenceImage.duplicate();
        display.setTitle("Object Territories - " + regionName);
        Overlay overlay = new Overlay();
        double pixelWidth = calibrated(referenceImage.getCalibration().pixelWidth);
        double pixelHeight = calibrated(referenceImage.getCalibration().pixelHeight);
        ShapeWriter writer = new ShapeWriter();
        for (TerritoryCell cell : region.getTerritories().getCells()) {
            Geometry pixels = AffineTransformation.scaleInstance(
                    1.0 / pixelWidth, 1.0 / pixelHeight).transform(cell.getGeometry());
            ShapeRoi roi = new ShapeRoi(writer.toShape(pixels));
            int type = cell.getObject().getTypeIndex();
            roi.setStrokeColor(TerritoryMapRenderer.colorForType(type));
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
