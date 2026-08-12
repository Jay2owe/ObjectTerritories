package territories.output;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import territories.api.RegionAnalysisResult;
import sc.fiji.territories.core.TerritoryCell;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/** Produces a headless-safe, calibrated raster rendering of 2D territories. */
final class TerritoryMapRenderer {

    private static final Color[] TYPE_COLORS = {
            new Color(0, 114, 178),
            new Color(213, 94, 0),
            new Color(0, 158, 115),
            new Color(204, 121, 167),
            new Color(230, 159, 0)
    };

    private TerritoryMapRenderer() {
    }

    static ImagePlus render(
            RegionAnalysisResult region,
            int width,
            int height,
            double pixelWidth,
            double pixelHeight,
            String spatialUnit) {
        if (region == null || region.getTerritories() == null) {
            throw new IllegalArgumentException("a territory result is required");
        }
        BufferedImage canvas = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            ShapeWriter writer = new ShapeWriter();
            for (TerritoryCell cell : region.getTerritories().getCells()) {
                Geometry pixels = AffineTransformation.scaleInstance(
                        1.0 / pixelWidth, 1.0 / pixelHeight)
                        .transform(cell.getGeometry());
                Shape shape = writer.toShape(pixels);
                Color color = colorForType(cell.getObject().getTypeIndex());
                graphics.setColor(new Color(
                        color.getRed(), color.getGreen(), color.getBlue(), 45));
                graphics.fill(shape);
                graphics.setColor(color);
                graphics.setStroke(new BasicStroke(
                        cell.isEdgeCell() ? 2.0f : 1.0f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                graphics.draw(shape);
            }
        } finally {
            graphics.dispose();
        }

        ImagePlus image = new ImagePlus(
                safe(region.getRegionName()) + "_Territories",
                new ColorProcessor(canvas));
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = pixelWidth;
        calibration.pixelHeight = pixelHeight;
        calibration.setUnit(
                spatialUnit == null || spatialUnit.trim().isEmpty()
                        ? "pixel" : spatialUnit);
        return image;
    }

    static Color colorForType(int typeIndex) {
        return TYPE_COLORS[Math.floorMod(typeIndex, TYPE_COLORS.length)];
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
