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
