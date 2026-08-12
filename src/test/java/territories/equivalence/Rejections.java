package territories.equivalence;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Calibration;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import territories.api.AnalysisMode;
import sc.fiji.territories.core.DensityBoundaryMode;
import sc.fiji.territories.core.DensityWeighting;
import sc.fiji.territories.core.EdgeCellPolicy;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import sc.fiji.territories.core.RegionMode;
import sc.fiji.territories.core.DensityEngine;
import sc.fiji.territories.core.InteractionEngine;
import sc.fiji.territories.core.LabelObjectExtractor;
import sc.fiji.territories.core.LabelObjectExtractor3D;
import sc.fiji.territories.core.NeighborhoodCell;
import sc.fiji.territories.core.RegionFactory;
import sc.fiji.territories.core.RegionMaskFactory3D;
import sc.fiji.territories.core.SpatialObject2D;
import sc.fiji.territories.core.SpatialObject3D;
import sc.fiji.territories.core.SpatialRegion2D;
import sc.fiji.territories.core.TerritoryEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every documented rejection, asserted by its exact message text.
 *
 * <p>A rejection message is output. Users read it, macros are written against
 * it, and the plugin presents it verbatim, so an extraction that changes one
 * has moved an output. Both layers are covered: the public API's validation
 * and the engine's own guards, because it is the engine that is moving.
 *
 * <p>Two documented rejections are deliberately absent, and neither can be
 * pinned honestly here:
 * <ul>
 * <li>the 3D output-memory guard ("requested 3D outputs need approximately
 * …") compares against {@code Runtime.maxMemory()}, so its message and even
 * whether it fires depend on the JVM the test runs in;</li>
 * <li>the tessellation failure ("could not tessellate region …") quotes a JTS
 * exception message and needs geometry degenerate enough that JTS, not this
 * code, decides the wording.</li>
 * </ul>
 */
final class Rejections {

    private Rejections() {
    }

    interface Rejection {
        String getName();

        void run() throws Exception;
    }

    static List<Rejection> all() {
        ArrayList<Rejection> cases = new ArrayList<Rejection>();

        // -- public API, 2D -------------------------------------------------

        cases.add(new Case("reject-2d-null-parameters") {
            @Override
            public void run() {
                ObjectTerritories.analyze(null);
            }
        });
        cases.add(new Case("reject-2d-no-label-images") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-six-label-images") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.Builder builder =
                        ObjectTerritoriesParameters.builder()
                                .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8));
                for (int i = 0; i < 6; i++) {
                    builder.addLabelImage(oneObject("Cells " + i));
                }
                ObjectTerritories.analyze(builder.build());
            }
        });
        cases.add(new Case("reject-2d-no-regions") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-stack-supplied-as-2d-label") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(Fixtures.labels3D("Stack", 8, 8, 3, new int[][] {
                                {1, 3, 3, 1, 2, 2, 1}
                        }, 1.0, 1.0, 1.0, "pixel"))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-mismatched-dimensions") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells A"))
                        .addLabelImage(Fixtures.labels2D("Cells B", 12, 8, new int[][] {
                                {1, 3, 3, 2, 2}
                        }, 1.0, 1.0, "pixel"))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-mismatched-calibration") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells A"))
                        .addLabelImage(Fixtures.labels2D("Cells B", 8, 8, new int[][] {
                                {1, 3, 3, 2, 2}
                        }, 0.5, 0.5, "micron"))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-nonzero-calibration-origin") {
            @Override
            public void run() {
                ImagePlus labels = oneObject("Cells");
                labels.getCalibration().xOrigin = 3.0;
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(labels)
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-duplicate-titles") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .addLabelImage(oneObject("Cells"))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-non-integer-label") {
            @Override
            public void run() {
                float[] pixels = new float[64];
                pixels[27] = 1.5f;
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(Fixtures.floatLabels2D("Cells", 8, 8, pixels))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-negative-label") {
            @Override
            public void run() {
                float[] pixels = new float[64];
                pixels[27] = -2.0f;
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(Fixtures.floatLabels2D("Cells", 8, 8, pixels))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-region-outside-image") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .addRegion(Fixtures.rectangle("Far", 40, 40, 8, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-region-touching-border-only") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .addRegion(Fixtures.rectangle("Edge", 8, 0, 6, 8))
                        .build());
            }
        });
        cases.add(new Case("reject-2d-union-with-no-overlapping-region") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .addRegion(Fixtures.rectangle("Far", 40, 40, 8, 8))
                        .regionMode(territories.api.RegionMode.UNION)
                        .build());
            }
        });
        cases.add(new Case("reject-2d-line-region") {
            @Override
            public void run() {
                Roi line = new Line(1, 1, 6, 6);
                line.setName("Trace");
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells"))
                        .addRegion(line)
                        .build());
            }
        });
        cases.add(new Case("reject-2d-identical-centroids") {
            @Override
            public void run() {
                ObjectTerritories.analyze(ObjectTerritoriesParameters.builder()
                        .addLabelImage(oneObject("Cells A"))
                        .addLabelImage(oneObject("Cells B"))
                        .addRegion(Fixtures.rectangle("Field", 0, 0, 8, 8))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(3)
                        .build());
            }
        });

        // -- public API, 3D -------------------------------------------------

        cases.add(new Case("reject-3d-six-label-images") {
            @Override
            public void run() {
                ObjectTerritoriesParameters3D.Builder builder =
                        ObjectTerritoriesParameters3D.builder()
                                .regionMask(volumeMask("Mask"));
                for (int i = 0; i < 6; i++) {
                    builder.addLabelImage(volume("Cells " + i, 1, 1, 1));
                }
                ObjectTerritories.analyze3D(builder.build());
            }
        });
        cases.add(new Case("reject-3d-missing-mask") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells", 1, 1, 1))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-flat-label-image") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(oneObject("Flat"))
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-mismatched-dimensions") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells A", 1, 1, 1))
                        .addLabelImage(Fixtures.labels3D("Cells B", 12, 8, 4, new int[][] {
                                {1, 1, 1, 1, 2, 2, 2}
                        }, 1.0, 1.0, 1.0, "pixel"))
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-mismatched-calibration") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells A", 1, 1, 1))
                        .addLabelImage(Fixtures.labels3D("Cells B", 8, 8, 4, new int[][] {
                                {1, 4, 4, 1, 2, 2, 2}
                        }, 0.5, 0.5, 0.5, "micron"))
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-nonzero-calibration-origin") {
            @Override
            public void run() {
                ImagePlus labels = volume("Cells", 1, 1, 1);
                labels.getCalibration().zOrigin = 2.0;
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(labels)
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-mask-also-used-as-label") {
            @Override
            public void run() {
                ImagePlus shared = volumeMask("Mask");
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(shared)
                        .regionMask(shared)
                        .build());
            }
        });
        cases.add(new Case("reject-3d-duplicate-titles") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells", 1, 1, 1))
                        .addLabelImage(volume("Cells", 5, 5, 1))
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-empty-mask") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells", 1, 1, 1))
                        .regionMask(Fixtures.labels3D("Mask", 8, 8, 4, new int[0][],
                                1.0, 1.0, 1.0, "pixel"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-negative-mask-value") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells", 1, 1, 1))
                        .regionMask(Fixtures.floatVolume("Mask", 8, 8, 4, 2, 3, 1, -4.0f))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-non-integer-label") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(Fixtures.floatVolume("Cells", 8, 8, 4, 2, 3, 1, 2.5f))
                        .regionMask(volumeMask("Mask"))
                        .build());
            }
        });
        cases.add(new Case("reject-3d-identical-centroids") {
            @Override
            public void run() {
                ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                        .addLabelImage(volume("Cells A", 1, 1, 1))
                        .addLabelImage(volume("Cells B", 1, 1, 1))
                        .regionMask(volumeMask("Mask"))
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(3)
                        .build());
            }
        });

        // -- parameter builders ---------------------------------------------

        cases.add(new Case("reject-builder-2d-zero-permutations") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.builder().permutations(0);
            }
        });
        cases.add(new Case("reject-builder-2d-negative-bandwidth") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.builder().bandwidthMicrons(-1.0);
            }
        });
        cases.add(new Case("reject-builder-2d-null-label-image") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.builder().addLabelImage(null);
            }
        });
        cases.add(new Case("reject-builder-2d-null-region") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.builder().addRegion(null);
            }
        });
        cases.add(new Case("reject-builder-2d-null-analysis-mode") {
            @Override
            public void run() {
                ObjectTerritoriesParameters.builder().analysisMode(null);
            }
        });
        cases.add(new Case("reject-builder-3d-null-analysis-mode") {
            @Override
            public void run() {
                ObjectTerritoriesParameters3D.builder().analysisMode(null);
            }
        });
        cases.add(new Case("reject-builder-3d-null-label-list") {
            @Override
            public void run() {
                ObjectTerritoriesParameters3D.builder().labelImages(null);
            }
        });
        cases.add(new Case("reject-builder-3d-negative-bandwidth") {
            @Override
            public void run() {
                ObjectTerritoriesParameters3D.builder().bandwidth(Double.NaN);
            }
        });

        // -- engine guards, called directly ---------------------------------

        cases.add(new Case("reject-engine-territory-null-objects") {
            @Override
            public void run() {
                TerritoryEngine.analyze(null, region(), EdgeCellPolicy.INCLUDE_FLAGGED);
            }
        });
        cases.add(new Case("reject-engine-territory-null-region") {
            @Override
            public void run() {
                TerritoryEngine.analyze(
                        Collections.<SpatialObject2D>emptyList(), null,
                        EdgeCellPolicy.INCLUDE_FLAGGED);
            }
        });
        cases.add(new Case("reject-engine-territory-null-policy") {
            @Override
            public void run() {
                TerritoryEngine.analyze(
                        Collections.<SpatialObject2D>emptyList(), region(), null);
            }
        });
        cases.add(new Case("reject-engine-interaction-null-cells") {
            @Override
            public void run() {
                InteractionEngine.analyze(null, Arrays.asList("A"), 10, 1L);
            }
        });
        cases.add(new Case("reject-engine-interaction-no-types") {
            @Override
            public void run() {
                InteractionEngine.analyze(
                        Collections.<NeighborhoodCell>emptyList(),
                        Collections.<String>emptyList(), 10, 1L);
            }
        });
        cases.add(new Case("reject-engine-interaction-null-type-name") {
            @Override
            public void run() {
                InteractionEngine.analyze(
                        Collections.<NeighborhoodCell>emptyList(),
                        Arrays.asList("A", null), 10, 1L);
            }
        });
        cases.add(new Case("reject-engine-interaction-zero-permutations") {
            @Override
            public void run() {
                InteractionEngine.analyze(
                        Collections.<NeighborhoodCell>emptyList(),
                        Arrays.asList("A"), 0, 1L);
            }
        });
        cases.add(new Case("reject-engine-regions-empty-list") {
            @Override
            public void run() {
                RegionFactory.create(
                        Collections.<Roi>emptyList(), RegionMode.INDEPENDENT,
                        1.0, 1.0, 8, 8);
            }
        });
        cases.add(new Case("reject-engine-regions-null-mode") {
            @Override
            public void run() {
                RegionFactory.create(
                        Arrays.asList(Fixtures.rectangle("Field", 0, 0, 8, 8)), null,
                        1.0, 1.0, 8, 8);
            }
        });
        cases.add(new Case("reject-engine-regions-bad-calibration") {
            @Override
            public void run() {
                RegionFactory.create(
                        Arrays.asList(Fixtures.rectangle("Field", 0, 0, 8, 8)),
                        RegionMode.INDEPENDENT, 0.0, 1.0, 8, 8);
            }
        });
        cases.add(new Case("reject-engine-regions-bad-image-size") {
            @Override
            public void run() {
                RegionFactory.create(
                        Arrays.asList(Fixtures.rectangle("Field", 0, 0, 8, 8)),
                        RegionMode.INDEPENDENT, 1.0, 1.0, 0, 8);
            }
        });
        cases.add(new Case("reject-engine-density-null-objects") {
            @Override
            public void run() {
                DensityEngine.generate(
                        null, region(), "Cells", 8, 8, 1.0, 1.0, "pixel", 1.0,
                        DensityWeighting.OBJECT_COUNT, DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-density-empty-type-name") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "  ",
                        8, 8, 1.0, 1.0, "pixel", 1.0,
                        DensityWeighting.OBJECT_COUNT, DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-density-bad-image-size") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "Cells",
                        0, 8, 1.0, 1.0, "pixel", 1.0,
                        DensityWeighting.OBJECT_COUNT, DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-density-bad-calibration") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "Cells",
                        8, 8, -1.0, 1.0, "pixel", 1.0,
                        DensityWeighting.OBJECT_COUNT, DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-density-null-weighting") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "Cells",
                        8, 8, 1.0, 1.0, "pixel", 1.0, null,
                        DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-density-null-boundary-mode") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "Cells",
                        8, 8, 1.0, 1.0, "pixel", 1.0,
                        DensityWeighting.OBJECT_COUNT, null);
            }
        });
        cases.add(new Case("reject-engine-density-negative-bandwidth") {
            @Override
            public void run() {
                DensityEngine.generate(
                        Collections.<SpatialObject2D>emptyList(), region(), "Cells",
                        8, 8, 1.0, 1.0, "pixel", -1.0,
                        DensityWeighting.OBJECT_COUNT, DensityBoundaryMode.CORRECTED);
            }
        });
        cases.add(new Case("reject-engine-mask-null") {
            @Override
            public void run() {
                RegionMaskFactory3D.create(null, RegionMode.INDEPENDENT);
            }
        });
        cases.add(new Case("reject-engine-mask-null-mode") {
            @Override
            public void run() {
                RegionMaskFactory3D.create(volumeMask("Mask"), null);
            }
        });
        cases.add(new Case("reject-engine-mask-single-slice") {
            @Override
            public void run() {
                RegionMaskFactory3D.create(oneObject("Flat"), RegionMode.INDEPENDENT);
            }
        });
        cases.add(new Case("reject-engine-extractor-null-image") {
            @Override
            public void run() {
                LabelObjectExtractor.extract(null, 0, 0);
            }
        });
        cases.add(new Case("reject-engine-extractor-stack-supplied") {
            @Override
            public void run() {
                LabelObjectExtractor.extract(volume("Cells", 1, 1, 1), 0, 0);
            }
        });
        cases.add(new Case("reject-engine-extractor-negative-type-index") {
            @Override
            public void run() {
                LabelObjectExtractor.extract(oneObject("Cells"), -1, 0);
            }
        });
        cases.add(new Case("reject-engine-extractor-negative-first-index") {
            @Override
            public void run() {
                LabelObjectExtractor.extract(oneObject("Cells"), 0, -1);
            }
        });
        cases.add(new Case("reject-engine-extractor3d-flat-image") {
            @Override
            public void run() {
                LabelObjectExtractor3D.extract(oneObject("Flat"), 0, 0);
            }
        });
        cases.add(new Case("reject-engine-extractor3d-negative-index") {
            @Override
            public void run() {
                LabelObjectExtractor3D.extract(volume("Cells", 1, 1, 1), -1, 0);
            }
        });

        // -- value types ----------------------------------------------------

        cases.add(new Case("reject-value-object2d-zero-label") {
            @Override
            public void run() {
                new SpatialObject2D(0, 0, "Cells", 0L, 1.0, 1.0, 1.0);
            }
        });
        cases.add(new Case("reject-value-object2d-zero-area") {
            @Override
            public void run() {
                new SpatialObject2D(0, 0, "Cells", 1L, 1.0, 1.0, 0.0);
            }
        });
        cases.add(new Case("reject-value-object2d-empty-type-name") {
            @Override
            public void run() {
                new SpatialObject2D(0, 0, "  ", 1L, 1.0, 1.0, 1.0);
            }
        });
        cases.add(new Case("reject-value-object2d-nonfinite-centroid") {
            @Override
            public void run() {
                new SpatialObject2D(0, 0, "Cells", 1L, Double.NaN, 1.0, 1.0);
            }
        });
        cases.add(new Case("reject-value-object3d-negative-index") {
            @Override
            public void run() {
                new SpatialObject3D(-1, 0, "Cells", 1L, 1.0, 1.0, 1.0, 1.0);
            }
        });
        cases.add(new Case("reject-value-object3d-zero-volume") {
            @Override
            public void run() {
                new SpatialObject3D(0, 0, "Cells", 1L, 1.0, 1.0, 1.0, 0.0);
            }
        });
        cases.add(new Case("reject-value-region2d-empty-name") {
            @Override
            public void run() {
                new SpatialRegion2D("  ", new GeometryFactory().toGeometry(
                        new Envelope(0.0, 4.0, 0.0, 4.0)));
            }
        });
        cases.add(new Case("reject-value-region2d-zero-dimensional") {
            @Override
            public void run() {
                new SpatialRegion2D("Point", new GeometryFactory().createPoint(
                        new Coordinate(1.0, 1.0)));
            }
        });

        return cases;
    }

    // ------------------------------------------------------------------

    private static SpatialRegion2D region() {
        return new SpatialRegion2D(
                "Field", new GeometryFactory().toGeometry(new Envelope(0.0, 8.0, 0.0, 8.0)));
    }

    private static ImagePlus oneObject(String title) {
        return Fixtures.labels2D(title, 8, 8, new int[][] {{1, 3, 3, 2, 2}},
                1.0, 1.0, "pixel");
    }

    private static ImagePlus volume(String title, int x, int y, int z) {
        return Fixtures.labels3D(title, 8, 8, 4, new int[][] {{1, x, y, z, 2, 2, 2}},
                1.0, 1.0, 1.0, "pixel");
    }

    private static ImagePlus volumeMask(String title) {
        ImagePlus mask = Fixtures.labels3D(title, 8, 8, 4, new int[][] {
                {1, 0, 0, 0, 8, 8, 4}
        }, 1.0, 1.0, 1.0, "pixel");
        Calibration calibration = mask.getCalibration();
        calibration.setUnit("pixel");
        return mask;
    }

    private abstract static class Case implements Rejection {
        private final String name;

        Case(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
