package territories.equivalence;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic corpus for the golden-master equivalence gate.
 *
 * <p>Every fixture is built from integer literals, so the same bytes are
 * produced on any machine and no binary is committed. Nothing here touches
 * global ImageJ state.
 */
final class Fixtures {

    private Fixtures() {
    }

    // ------------------------------------------------------------------
    // 2D corpus
    // ------------------------------------------------------------------

    /** Returns every two-dimensional case, in a fixed order. */
    static List<Case2D> all2D() {
        ArrayList<Case2D> cases = new ArrayList<Case2D>();

        cases.add(new Case2D(
                "2d-empty",
                Arrays.asList(labels2D("Cells", 24, 24, new int[0][], 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 2, 2, 20, 20))));

        cases.add(new Case2D(
                "2d-single-object",
                Arrays.asList(labels2D("Cells", 24, 24, new int[][] {
                        {1, 11, 11, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 0, 0, 24, 24))));

        cases.add(new Case2D(
                "2d-single-type-grid",
                Arrays.asList(labels2D("Cells", 32, 32, new int[][] {
                        {1, 4, 4, 3, 3}, {2, 20, 5, 3, 3},
                        {3, 5, 21, 3, 3}, {4, 21, 20, 3, 3},
                        {5, 13, 13, 3, 3}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 0, 0, 32, 32))));

        cases.add(new Case2D(
                "2d-two-types",
                Arrays.asList(
                        labels2D("Cells A", 32, 32, new int[][] {
                                {1, 3, 3, 3, 3}, {2, 24, 4, 3, 3},
                                {3, 6, 24, 3, 3}, {4, 25, 25, 2, 2}
                        }, 1.0, 1.0, "pixel"),
                        labels2D("Cells B", 32, 32, new int[][] {
                                {1, 14, 6, 3, 3}, {2, 7, 14, 3, 3},
                                {3, 22, 15, 3, 3}, {4, 15, 23, 3, 3}
                        }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 0, 0, 32, 32))));

        cases.add(new Case2D(
                "2d-three-types-calibrated",
                Arrays.asList(
                        labels2D("Type A", 30, 26, new int[][] {
                                {1, 3, 3, 2, 2}, {2, 24, 3, 2, 2}, {3, 12, 11, 2, 2}
                        }, 0.325, 0.325, "micron"),
                        labels2D("Type B", 30, 26, new int[][] {
                                {1, 4, 19, 2, 2}, {2, 24, 19, 2, 2}
                        }, 0.325, 0.325, "micron"),
                        labels2D("Type C", 30, 26, new int[][] {
                                {1, 15, 3, 2, 2}, {2, 15, 20, 2, 2}, {3, 22, 11, 2, 2}
                        }, 0.325, 0.325, "micron")),
                Arrays.asList(rectangle("Cortex", 1, 1, 28, 24))));

        cases.add(new Case2D(
                "2d-anisotropic-calibration",
                Arrays.asList(labels2D("Cells", 28, 28, new int[][] {
                        {1, 4, 4, 2, 2}, {2, 21, 5, 2, 2},
                        {3, 5, 20, 2, 2}, {4, 20, 21, 2, 2}
                }, 0.5, 1.75, "micron")),
                Arrays.asList(rectangle("Field", 0, 0, 28, 28))));

        cases.add(new Case2D(
                "2d-objects-on-region-boundary",
                Arrays.asList(labels2D("Cells", 24, 24, new int[][] {
                        {1, 0, 0, 2, 2}, {2, 22, 0, 2, 2},
                        {3, 0, 22, 2, 2}, {4, 22, 22, 2, 2},
                        {5, 11, 0, 2, 2}, {6, 0, 11, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 0, 0, 24, 24))));

        cases.add(new Case2D(
                "2d-multiple-named-regions",
                Arrays.asList(
                        labels2D("Cells A", 40, 24, new int[][] {
                                {1, 3, 4, 2, 2}, {2, 12, 5, 2, 2},
                                {3, 24, 4, 2, 2}, {4, 34, 6, 2, 2}
                        }, 1.0, 1.0, "pixel"),
                        labels2D("Cells B", 40, 24, new int[][] {
                                {1, 6, 15, 2, 2}, {2, 16, 16, 2, 2},
                                {3, 28, 15, 2, 2}, {4, 35, 17, 2, 2}
                        }, 1.0, 1.0, "pixel")),
                Arrays.asList(
                        rectangle("Left", 0, 0, 20, 24),
                        rectangle("Right", 20, 0, 20, 24))));

        cases.add(new Case2D(
                "2d-disjoint-regions",
                Arrays.asList(labels2D("Cells", 40, 20, new int[][] {
                        {1, 3, 4, 2, 2}, {2, 11, 12, 2, 2},
                        {3, 27, 4, 2, 2}, {4, 34, 12, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(
                        rectangle("West", 0, 0, 16, 20),
                        rectangle("East", 24, 0, 16, 20))));

        cases.add(new Case2D(
                "2d-holed-region",
                Arrays.asList(labels2D("Cells", 32, 32, new int[][] {
                        {1, 3, 3, 2, 2}, {2, 26, 3, 2, 2},
                        {3, 3, 26, 2, 2}, {4, 26, 26, 2, 2},
                        {5, 15, 3, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(holed("Ring", 0, 0, 32, 32, 12, 12, 8, 8))));

        cases.add(new Case2D(
                "2d-oval-region",
                Arrays.asList(labels2D("Cells", 32, 32, new int[][] {
                        {1, 15, 6, 2, 2}, {2, 8, 15, 2, 2},
                        {3, 22, 15, 2, 2}, {4, 15, 22, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(oval("Disc", 2, 2, 28, 28))));

        cases.add(new Case2D(
                "2d-region-overhanging-image",
                Arrays.asList(labels2D("Cells", 24, 24, new int[][] {
                        {1, 4, 4, 2, 2}, {2, 17, 5, 2, 2},
                        {3, 5, 17, 2, 2}, {4, 18, 18, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Overhang", -8, -8, 40, 40))));

        cases.add(new Case2D(
                "2d-all-objects-one-type-dense",
                Arrays.asList(labels2D("Cells", 30, 30, new int[][] {
                        {1, 3, 3, 2, 2}, {2, 10, 3, 2, 2}, {3, 17, 3, 2, 2}, {4, 24, 3, 2, 2},
                        {5, 3, 11, 2, 2}, {6, 11, 12, 2, 2}, {7, 18, 11, 2, 2}, {8, 25, 12, 2, 2},
                        {9, 3, 20, 2, 2}, {10, 10, 21, 2, 2}, {11, 18, 20, 2, 2}, {12, 25, 21, 2, 2}
                }, 1.0, 1.0, "pixel")),
                Arrays.asList(rectangle("Field", 0, 0, 30, 30))));

        return cases;
    }

    /** The 2D fixtures that carry the full configuration cross-product. */
    static List<String> sweep2D() {
        return Arrays.asList(
                "2d-two-types",
                "2d-multiple-named-regions",
                "2d-three-types-calibrated");
    }

    // ------------------------------------------------------------------
    // 3D corpus
    // ------------------------------------------------------------------

    static List<Case3D> all3D() {
        ArrayList<Case3D> cases = new ArrayList<Case3D>();

        cases.add(new Case3D(
                "3d-single-object",
                Arrays.asList(labels3D("Cells", 16, 16, 6, new int[][] {
                        {1, 7, 7, 2, 2, 2, 2}
                }, 1.0, 1.0, 1.0, "pixel")),
                mask3D("Mask", 16, 16, 6, new int[][] {
                        {1, 2, 2, 1, 12, 12, 4}
                }, 1.0, 1.0, 1.0, "pixel")));

        cases.add(new Case3D(
                "3d-two-types",
                Arrays.asList(
                        labels3D("Cells A", 20, 20, 6, new int[][] {
                                {1, 3, 3, 1, 2, 2, 2}, {2, 15, 4, 2, 2, 2, 2}
                        }, 1.0, 1.0, 1.0, "pixel"),
                        labels3D("Cells B", 20, 20, 6, new int[][] {
                                {1, 4, 15, 1, 2, 2, 2}, {2, 15, 15, 3, 2, 2, 2}
                        }, 1.0, 1.0, 1.0, "pixel")),
                mask3D("Mask", 20, 20, 6, new int[][] {
                        {1, 1, 1, 0, 18, 18, 6}
                }, 1.0, 1.0, 1.0, "pixel")));

        cases.add(new Case3D(
                "3d-multi-label-mask",
                Arrays.asList(
                        labels3D("Cells A", 24, 16, 6, new int[][] {
                                {1, 3, 3, 1, 2, 2, 2}, {2, 17, 4, 1, 2, 2, 2}
                        }, 1.0, 1.0, 1.0, "pixel"),
                        labels3D("Cells B", 24, 16, 6, new int[][] {
                                {1, 5, 10, 2, 2, 2, 2}, {2, 18, 10, 2, 2, 2, 2}
                        }, 1.0, 1.0, 1.0, "pixel")),
                mask3D("Mask", 24, 16, 6, new int[][] {
                        {1, 0, 0, 0, 12, 16, 6},
                        {2, 12, 0, 0, 12, 16, 6}
                }, 1.0, 1.0, 1.0, "pixel")));

        cases.add(new Case3D(
                "3d-anisotropic",
                Arrays.asList(labels3D("Cells", 18, 18, 5, new int[][] {
                        {1, 3, 3, 1, 2, 2, 1}, {2, 13, 4, 1, 2, 2, 1},
                        {3, 4, 13, 3, 2, 2, 1}, {4, 13, 13, 3, 2, 2, 1}
                }, 0.2, 0.2, 1.0, "micron")),
                mask3D("Mask", 18, 18, 5, new int[][] {
                        {1, 0, 0, 0, 18, 18, 5}
                }, 0.2, 0.2, 1.0, "micron")));

        cases.add(new Case3D(
                "3d-objects-on-mask-boundary",
                Arrays.asList(labels3D("Cells", 16, 16, 6, new int[][] {
                        {1, 0, 0, 0, 2, 2, 2}, {2, 14, 0, 0, 2, 2, 2},
                        {3, 0, 14, 4, 2, 2, 2}, {4, 14, 14, 4, 2, 2, 2}
                }, 1.0, 1.0, 1.0, "pixel")),
                mask3D("Mask", 16, 16, 6, new int[][] {
                        {1, 0, 0, 0, 16, 16, 6}
                }, 1.0, 1.0, 1.0, "pixel")));

        cases.add(new Case3D(
                "3d-disjoint-mask-components",
                Arrays.asList(labels3D("Cells", 26, 12, 6, new int[][] {
                        {1, 2, 3, 1, 2, 2, 2}, {2, 6, 6, 1, 2, 2, 2},
                        {3, 19, 3, 1, 2, 2, 2}, {4, 22, 6, 1, 2, 2, 2}
                }, 1.0, 1.0, 1.0, "pixel")),
                mask3D("Mask", 26, 12, 6, new int[][] {
                        {1, 0, 0, 0, 10, 12, 6},
                        {1, 16, 0, 0, 10, 12, 6}
                }, 1.0, 1.0, 1.0, "pixel")));

        return cases;
    }

    /** The 3D fixtures that carry the full configuration cross-product. */
    static List<String> sweep3D() {
        return Arrays.asList("3d-two-types", "3d-multi-label-mask");
    }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    /** Each block is {label, x, y, width, height}. */
    static ImagePlus labels2D(
            String title,
            int width,
            int height,
            int[][] blocks,
            double pixelWidth,
            double pixelHeight,
            String unit) {
        ShortProcessor processor = new ShortProcessor(width, height);
        for (int i = 0; i < blocks.length; i++) {
            int[] block = blocks[i];
            for (int y = block[2]; y < block[2] + block[4]; y++) {
                for (int x = block[1]; x < block[1] + block[3]; x++) {
                    processor.set(x, y, block[0]);
                }
            }
        }
        ImagePlus image = new ImagePlus(title, processor);
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = pixelWidth;
        calibration.pixelHeight = pixelHeight;
        calibration.setUnit(unit);
        return image;
    }

    /** Each block is {label, x, y, z, width, height, depth}. */
    static ImagePlus labels3D(
            String title,
            int width,
            int height,
            int depth,
            int[][] blocks,
            double pixelWidth,
            double pixelHeight,
            double pixelDepth,
            String unit) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice(new ShortProcessor(width, height));
        }
        for (int i = 0; i < blocks.length; i++) {
            int[] block = blocks[i];
            for (int z = block[3]; z < block[3] + block[6]; z++) {
                ImageProcessor processor = stack.getProcessor(z + 1);
                for (int y = block[2]; y < block[2] + block[5]; y++) {
                    for (int x = block[1]; x < block[1] + block[4]; x++) {
                        processor.set(x, y, block[0]);
                    }
                }
            }
        }
        ImagePlus image = new ImagePlus(title, stack);
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = pixelWidth;
        calibration.pixelHeight = pixelHeight;
        calibration.pixelDepth = pixelDepth;
        calibration.setUnit(unit);
        return image;
    }

    static ImagePlus mask3D(
            String title,
            int width,
            int height,
            int depth,
            int[][] blocks,
            double pixelWidth,
            double pixelHeight,
            double pixelDepth,
            String unit) {
        return labels3D(
                title, width, height, depth, blocks,
                pixelWidth, pixelHeight, pixelDepth, unit);
    }

    static Roi rectangle(String name, int x, int y, int width, int height) {
        Roi roi = new Roi(x, y, width, height);
        roi.setName(name);
        return roi;
    }

    static Roi oval(String name, int x, int y, int width, int height) {
        Roi roi = new OvalRoi(x, y, width, height);
        roi.setName(name);
        return roi;
    }

    /** A rectangle with a rectangular hole cut out of it. */
    static Roi holed(
            String name,
            int x,
            int y,
            int width,
            int height,
            int holeX,
            int holeY,
            int holeWidth,
            int holeHeight) {
        ShapeRoi outer = new ShapeRoi(new Roi(x, y, width, height));
        ShapeRoi hole = new ShapeRoi(new Roi(holeX, holeY, holeWidth, holeHeight));
        ShapeRoi result = outer.not(hole);
        result.setName(name);
        return result;
    }

    /** A 32-bit 2D image, used only by the rejection corpus. */
    static ImagePlus floatLabels2D(String title, int width, int height, float[] pixels) {
        ImagePlus image = new ImagePlus(
                title, new FloatProcessor(width, height, pixels, null));
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = 1.0;
        calibration.pixelHeight = 1.0;
        calibration.setUnit("pixel");
        return image;
    }

    /**
     * A 32-bit 3D stack whose voxel at {@code (x, y, z)} carries {@code value},
     * used only by the rejection corpus — a short stack cannot hold the
     * negative and fractional labels those cases need.
     */
    static ImagePlus floatVolume(
            String title,
            int width,
            int height,
            int depth,
            int x,
            int y,
            int z,
            float value) {
        ImageStack stack = new ImageStack(width, height);
        for (int slice = 0; slice < depth; slice++) {
            stack.addSlice(new FloatProcessor(width, height));
        }
        stack.getProcessor(z + 1).setf(x, y, value);
        ImagePlus image = new ImagePlus(title, stack);
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = 1.0;
        calibration.pixelHeight = 1.0;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("pixel");
        return image;
    }

    // ------------------------------------------------------------------
    // Case records
    // ------------------------------------------------------------------

    static final class Case2D {
        private final String name;
        private final List<ImagePlus> labelImages;
        private final List<Roi> regions;

        Case2D(String name, List<ImagePlus> labelImages, List<Roi> regions) {
            this.name = name;
            this.labelImages = labelImages;
            this.regions = regions;
        }

        String getName() {
            return name;
        }

        List<ImagePlus> getLabelImages() {
            return labelImages;
        }

        List<Roi> getRegions() {
            return regions;
        }
    }

    static final class Case3D {
        private final String name;
        private final List<ImagePlus> labelImages;
        private final ImagePlus regionMask;

        Case3D(String name, List<ImagePlus> labelImages, ImagePlus regionMask) {
            this.name = name;
            this.labelImages = labelImages;
            this.regionMask = regionMask;
        }

        String getName() {
            return name;
        }

        List<ImagePlus> getLabelImages() {
            return labelImages;
        }

        ImagePlus getRegionMask() {
            return regionMask;
        }
    }
}
