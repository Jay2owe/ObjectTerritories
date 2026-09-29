package territories.batch;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.RoiEncoder;
import ij.process.ByteProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import territories.api.AnalysisMode;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectTerritoriesBatchRunnerTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void previewUsesCoreGroupingRecursivelyAndExcludesOutputTree() throws Exception {
        File input = temporary.newFolder("input");
        File nested = new File(input, "nested");
        assertTrue(nested.mkdir());
        File output = new File(input, "results");
        assertTrue(output.mkdir());
        File regions = regionFile();
        touch(new File(input, "sample_A.tif"));
        touch(new File(input, "sample_B.tif"));
        touch(new File(nested, "other_A.tif"));
        touch(new File(output, "ignored_A.tif"));

        ObjectTerritoriesBatchParameters parameters = parameters(
                input, regions, output, "(.+)_([A-Z])\\.tif");
        String preview = ObjectTerritoriesBatchRunner.preview(parameters);

        assertTrue(preview.contains("2 folder(s), 2 group(s), 2 runnable, 3 files"));
        assertTrue(preview.contains("[A] sample_A.tif"));
        assertTrue(preview.contains("nested/"));
        assertFalse(preview.contains("ignored_A.tif"));
    }

    @Test
    public void runsValidGroupAndSkipsMoreThanFiveLabelTypes() throws Exception {
        File input = temporary.newFolder("labels");
        File output = temporary.newFolder("output");
        File regions = regionFile();
        saveLabel(new File(input, "sample_A.tif"), 2, 2, 7, 7);
        saveLabel(new File(input, "sample_B.tif"), 2, 7, 7, 2);
        for (char type = 'A'; type <= 'F'; type++) {
            touch(new File(input, "too_many_" + type + ".tif"));
        }

        ObjectTerritoriesBatchParameters parameters =
                ObjectTerritoriesBatchParameters.builder(
                                input,
                                "(.+)_([A-F])\\.tif",
                                2,
                                regions,
                                output)
                        .recursive(false)
                        .analysisMode(AnalysisMode.TERRITORIES)
                        .permutations(5)
                        .build();

        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(parameters);

        assertEquals(1, result.getProcessedGroups());
        assertEquals(1, result.getSkippedGroups());
        assertEquals(0, result.getErrorGroups());
        assertEquals("PROCESSED", result.getManifest().getStringValue("Status", 0));
        assertEquals("SKIPPED", result.getManifest().getStringValue("Status", 1));
        File objects = new File(new File(output, "sample"), "Objects");
        assertTrue(objects.isDirectory());
        File[] csvFiles = objects.listFiles((directory, name) -> name.endsWith(".csv"));
        assertTrue(csvFiles != null && csvFiles.length == 1);
    }

    @Test
    public void legacyTwoDimensionalBuilderWritesTheSameFilesAsVersion020() throws Exception {
        File input = temporary.newFolder("legacy-labels");
        File output = temporary.newFolder("legacy-output");
        File regions = regionFile();
        saveLabel(new File(input, "sample_A.tif"), 2, 2, 7, 7);
        saveLabel(new File(input, "sample_B.tif"), 2, 7, 7, 2);

        ObjectTerritoriesBatchParameters parameters =
                ObjectTerritoriesBatchParameters.builder(
                                input, "(.+)_([AB])\\.tif", 2, regions, output)
                        .recursive(false)
                        .analysisMode(AnalysisMode.BOTH)
                        .bandwidthMicrons(3.0)
                        .permutations(5)
                        .build();
        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(parameters);
        assertEquals(1, result.getProcessedGroups());

        java.util.List<String> sampleFiles = relativeFiles(new File(output, "sample"));
        assertEquals(java.util.Arrays.asList(LEGACY_2D_SAMPLE_FILES), sampleFiles);
    }

    /** Files one 2D sample produced before 3D batch mode existed (0.2.0 layout). */
    private static final String[] LEGACY_2D_SAMPLE_FILES = {
            "Density/full_A_object_count_bw-3.0.tif",
            "Density/full_A_object_size_bw-3.0.tif",
            "Density/full_B_object_count_bw-3.0.tif",
            "Density/full_B_object_size_bw-3.0.tif",
            "Interactions/full_Interactions.csv",
            "Interactions/full_Regularity.csv",
            "Maps/full_Territories.tif",
            "Objects/full_Objects.csv",
    };

    private static java.util.List<String> relativeFiles(File root) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        collect(root, "", names);
        java.util.Collections.sort(names);
        return names;
    }

    private static void collect(File directory, String prefix, java.util.List<String> names) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, prefix + child.getName() + "/", names);
            } else {
                names.add(prefix + child.getName());
            }
        }
    }

    @Test
    public void threeDimensionalBatchUsesEachGroupsOwnRegionMask() throws Exception {
        File input = temporary.newFolder("stacks");
        File output = temporary.newFolder("stacks-output");
        saveLabelStack(new File(input, "brain1_Cells.tif"), 1, 2, 1, 5, 2, 3);
        saveLabelStack(new File(input, "brain1_Plaques.tif"), 3, 1, 2, 4, 3, 4);
        saveMaskStack(new File(input, "brain1_Mask.tif"));
        // No mask: must be an ERROR row, not a crash, and not stop the batch.
        saveLabelStack(new File(input, "brain2_Cells.tif"), 1, 2, 1, 5, 2, 3);
        // Six label stacks plus a mask: over the limit, which excludes the mask.
        for (char type = 'A'; type <= 'F'; type++) {
            touch(new File(input, "brain3_" + type + ".tif"));
        }
        touch(new File(input, "brain3_mask.tif"));

        ObjectTerritoriesBatchParameters parameters =
                ObjectTerritoriesBatchParameters.builder3D(
                                input, "(.+)_([^_]+)\\.tif", 2, "mask", output)
                        .recursive(false)
                        .analysisMode(AnalysisMode.BOTH)
                        .bandwidthMicrons(2.0)
                        .permutations(5)
                        .build();
        assertTrue(parameters.isThreeDimensional());
        assertEquals(null, parameters.getRegionSource());

        String preview = ObjectTerritoriesBatchRunner.preview(parameters);
        assertTrue(preview, preview.contains("3 group(s), 1 runnable, 11 files"));
        assertTrue(preview, preview.contains("[mask] brain1_Mask.tif"));
        assertTrue(preview, preview.contains("2 label type(s) + 1 mask)"));
        assertTrue(preview, preview.contains("SKIP/ERROR: no mask"));
        assertTrue(preview, preview.contains("6 label type(s) + 1 mask - SKIP: maximum is 5"));

        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(parameters);

        assertEquals(1, result.getProcessedGroups());
        assertEquals(1, result.getSkippedGroups());
        assertEquals(1, result.getErrorGroups());
        ij.measure.ResultsTable manifest = result.getManifest();
        assertEquals("PROCESSED", manifest.getStringValue("Status", 0));
        assertEquals("3D", manifest.getStringValue("Dimensions", 0));
        assertEquals(2.0, manifest.getValue("Label_Types", 0), 0.0);
        assertEquals("ERROR", manifest.getStringValue("Status", 1));
        assertEquals("no region-mask file (type 'mask') in group brain2_*.tif",
                manifest.getStringValue("Message", 1));
        assertEquals("SKIPPED", manifest.getStringValue("Status", 2));
        assertEquals(6.0, manifest.getValue("Label_Types", 2), 0.0);

        File sample = new File(output, "brain1");
        java.util.List<String> files = relativeFiles(sample);
        // The mask's own title (its type capture) names the single region.
        assertTrue(files.toString(), files.contains("Objects/Mask_Objects_3D.csv"));
        assertTrue(files.toString(), files.contains("Maps/Mask_Territories_3D.tif"));
        assertTrue(files.toString(), files.contains("Interactions/Mask_Interactions_3D.csv"));
        assertTrue(files.toString(),
                files.contains("Density/Mask_Cells_object_count_bw-2.0_Density_3D.tif"));
        assertEquals(4, countUnder(files, "Density/"));
        assertTrue(result.getManifestFile().isFile());
    }

    @Test
    public void manifestIsSavedWithOneRowPerGroupWhenAGroupErrors() throws Exception {
        File input = temporary.newFolder("mixed");
        File output = temporary.newFolder("mixed-output");
        File regions = regionFile();
        saveLabel(new File(input, "good_A.tif"), 2, 2, 7, 7);
        saveLabel(new File(input, "good_B.tif"), 2, 7, 7, 2);
        // Not a readable image: ImageJ cannot open it, so the group errors.
        java.nio.file.Files.write(new File(input, "broken_A.tif").toPath(),
                "not a tiff".getBytes("UTF-8"));

        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(
                parameters(input, regions, output, "(.+)_([AB])\\.tif"));

        assertEquals(1, result.getProcessedGroups());
        assertEquals(1, result.getErrorGroups());
        File manifest = new File(output, "Batch_Manifest.csv");
        assertEquals(manifest.getAbsoluteFile(), result.getManifestFile().getAbsoluteFile());
        assertTrue(manifest.isFile());
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
                manifest.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(lines.toString(), 3, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("Folder,Group,Dimensions,Status"));
        assertTrue(lines.get(1), lines.get(1).contains("ERROR"));
        assertTrue(lines.get(1), lines.get(1).contains("broken"));
        assertTrue(lines.get(2), lines.get(2).contains("PROCESSED"));
        assertTrue(lines.get(2), lines.get(2).contains("2D"));
    }

    @Test
    public void manifestIsSavedEvenWhenNothingMatches() throws Exception {
        File input = temporary.newFolder("empty-input");
        File output = temporary.newFolder("empty-output");
        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(
                parameters(input, regionFile(), output, "(.+)_([AB])\\.tif"));
        assertEquals(0, result.getProcessedGroups());
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
                result.getManifestFile().toPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("Folder,Group,Dimensions,Status"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void threeDimensionalBatchRejectsBlankMaskType() throws Exception {
        ObjectTerritoriesBatchRunner.preview(ObjectTerritoriesBatchParameters.builder3D(
                temporary.newFolder("blank-mask"), "(.+)_(.+)\\.tif", 2, " ",
                temporary.newFolder("blank-mask-output")).build());
    }

    @Test
    public void cancellingAfterTheFirstSampleRecordsTheRestAndSavesTheManifest() throws Exception {
        File input = temporary.newFolder("cancel-input");
        File output = temporary.newFolder("cancel-output");
        File regions = regionFile();
        for (String sample : new String[]{"s1", "s2", "s3"}) {
            saveLabel(new File(input, sample + "_A.tif"), 2, 2, 7, 7);
            saveLabel(new File(input, sample + "_B.tif"), 2, 7, 7, 2);
        }
        final java.util.List<String> steps = new java.util.ArrayList<String>();
        territories.api.ProgressMonitor stopAfterFirst = new territories.api.ProgressMonitor() {
            private boolean cancelled;

            @Override
            public void update(String step, int done, int total) {
                steps.add(step);
                if (step.startsWith("Sample 1/3") && step.endsWith(" - done")) cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };

        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(
                parameters(input, regions, output, "(.+)_([AB])\\.tif"), stopAfterFirst);

        assertEquals(1, result.getProcessedGroups());
        assertEquals(2, result.getCancelledGroups());
        ij.measure.ResultsTable manifest = result.getManifest();
        assertEquals(3, manifest.size());
        assertEquals("PROCESSED", manifest.getStringValue("Status", 0));
        assertEquals("CANCELLED", manifest.getStringValue("Status", 1));
        assertEquals("CANCELLED", manifest.getStringValue("Status", 2));
        assertTrue(steps.toString(), steps.get(0).startsWith("Sample 1/3: s1"));
        assertFalse(new File(output, "s2").exists());
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
                new File(output, "Batch_Manifest.csv").toPath(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertTrue(lines.get(3), lines.get(3).contains("CANCELLED"));
    }

    @Test
    public void uncalibratedWarningIsRecordedForProcessedSamples() throws Exception {
        File input = temporary.newFolder("warn-input");
        File output = temporary.newFolder("warn-output");
        saveLabel(new File(input, "w_A.tif"), 2, 2, 7, 7);
        ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(
                parameters(input, regionFile(), output, "(.+)_([AB])\\.tif"));
        assertEquals("PROCESSED", result.getManifest().getStringValue("Status", 0));
        assertTrue(result.getManifest().getStringValue("Message", 0),
                result.getManifest().getStringValue("Message", 0).contains("not spatially calibrated"));
    }

    @Test
    public void defaultRegexMatchesUpperCaseExtensions() {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(territories.Object_Territories_Batch.DEFAULT_REGEX)
                .matcher("Sample01_Cells.TIF");
        assertTrue(matcher.matches());
        assertEquals("Cells", matcher.group(2));
    }

    private static int countUnder(java.util.List<String> files, String prefix) {
        int count = 0;
        for (String file : files) {
            if (file.startsWith(prefix)) count++;
        }
        return count;
    }

    private static void saveLabelStack(
            File file, int x1, int y1, int z1, int x2, int y2, int z2) {
        ij.ImageStack stack = new ij.ImageStack(7, 5);
        for (int z = 0; z < 5; z++) stack.addSlice(new ij.process.ShortProcessor(7, 5));
        stack.getProcessor(z1 + 1).set(x1, y1, 1);
        stack.getProcessor(z2 + 1).set(x2, y2, 2);
        saveStack(file, new ImagePlus(file.getName(), stack));
    }

    private static void saveMaskStack(File file) {
        ij.ImageStack stack = new ij.ImageStack(7, 5);
        for (int z = 0; z < 5; z++) {
            ByteProcessor processor = new ByteProcessor(7, 5);
            processor.setValue(1);
            processor.fill();
            stack.addSlice(processor);
        }
        saveStack(file, new ImagePlus(file.getName(), stack));
    }

    private static void saveStack(File file, ImagePlus image) {
        ij.measure.Calibration calibration = image.getCalibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 2.0;
        calibration.setUnit("um");
        try {
            assertTrue(new FileSaver(image).saveAsTiffStack(file.getAbsolutePath()));
        } finally {
            image.close();
            image.flush();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCaptureGroupOutsideTheRegex() throws Exception {
        File input = temporary.newFolder("bad-group-input");
        File output = temporary.newFolder("bad-group-output");
        ObjectTerritoriesBatchParameters parameters =
                ObjectTerritoriesBatchParameters.builder(
                                input, "(.+)\\.tif", 2, regionFile(), output)
                        .build();

        ObjectTerritoriesBatchRunner.preview(parameters);
    }

    private ObjectTerritoriesBatchParameters parameters(
            File input, File regions, File output, String regex) {
        return ObjectTerritoriesBatchParameters.builder(input, regex, 2, regions, output)
                .recursive(true)
                .analysisMode(AnalysisMode.TERRITORIES)
                .permutations(5)
                .build();
    }

    private File regionFile() throws Exception {
        File file = temporary.newFile("regions-" + System.nanoTime() + ".roi");
        Roi roi = new Roi(0, 0, 10, 10);
        roi.setName("full");
        new RoiEncoder(file.getAbsolutePath()).write(roi);
        return file;
    }

    private static void saveLabel(
            File file, int firstX, int firstY, int secondX, int secondY) {
        ByteProcessor pixels = new ByteProcessor(10, 10);
        pixels.set(firstX, firstY, 1);
        pixels.set(secondX, secondY, 2);
        ImagePlus image = new ImagePlus(file.getName(), pixels);
        try {
            assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        } finally {
            image.close();
            image.flush();
        }
    }

    private static void touch(File file) throws Exception {
        assertTrue(file.createNewFile());
    }
}
