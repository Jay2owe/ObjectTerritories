package territories.api;

import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import territories.output.ResultExporter;
import territories.output.ResultExporter3D;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProgressMonitorTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private final List<ImagePlus> closed = Collections.synchronizedList(new ArrayList<ImagePlus>());
    private final ImageListener closeRecorder = new ImageListener() {
        @Override
        public void imageOpened(ImagePlus image) {
        }

        @Override
        public void imageClosed(ImagePlus image) {
            closed.add(image);
        }

        @Override
        public void imageUpdated(ImagePlus image) {
        }
    };

    @After
    public void removeListener() {
        ImagePlus.removeImageListener(closeRecorder);
    }

    @Test
    public void stepCountsFollowTheAnalysisMode() {
        assertEquals(5, steps(AnalysisMode.BOTH, DensityWeightingSelection.BOTH));
        assertEquals(1, steps(AnalysisMode.TERRITORIES, DensityWeightingSelection.BOTH));
        assertEquals(4, steps(AnalysisMode.DENSITY, DensityWeightingSelection.BOTH));
        assertEquals(3, steps(AnalysisMode.BOTH, DensityWeightingSelection.OBJECT_COUNT));
    }

    @Test
    public void progressReportsEveryStepAndFinishesAtTheTotal() {
        Recording monitor = new Recording(Integer.MAX_VALUE);
        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters(AnalysisMode.BOTH,
                DensityWeightingSelection.BOTH), monitor);
        result.closeDensityImages();
        // Five steps announced plus the final "done".
        assertEquals(6, monitor.steps.size());
        assertTrue(monitor.steps.get(0), monitor.steps.get(0).contains("territories"));
        assertTrue(monitor.steps.get(1), monitor.steps.get(1).contains("density A (object count)"));
        assertEquals("done", monitor.steps.get(5));
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), monitor.done);
        for (int total : monitor.totals) assertEquals(5, total);
    }

    @Test
    public void cancellationStopsBetweenStepsAndClosesImagesAlreadyMade() {
        ImagePlus.addImageListener(closeRecorder);
        // Two density maps are made, then the third check cancels.
        Recording monitor = new Recording(2);
        try {
            ObjectTerritories.analyze(
                    parameters(AnalysisMode.DENSITY, DensityWeightingSelection.BOTH), monitor);
            fail("expected cancellation");
        } catch (AnalysisCancelledException expected) {
            assertEquals("Object Territories was cancelled", expected.getMessage());
        }
        drainEvents();
        assertEquals(2, monitor.steps.size());
        assertEquals(2, closed.size());
        // The two closed images are the density maps (32-bit), not the inputs.
        for (ImagePlus image : closed) assertEquals(32, image.getBitDepth());
    }

    @Test
    public void escapeDuringTheLastStepStillCancelsAndClosesEveryMap() {
        ImagePlus.addImageListener(closeRecorder);
        // All four density steps run; the check after the last one cancels.
        // Before 0.3.1 that check did not exist and the run returned results.
        Recording monitor = new Recording(4);
        try {
            ObjectTerritories.analyze(
                    parameters(AnalysisMode.DENSITY, DensityWeightingSelection.BOTH), monitor);
            fail("expected cancellation after the last step");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertEquals(4, monitor.steps.size());
            assertEquals(4, closed.size());
        }
    }

    @Test
    public void threeDimensionalEscapeDuringTheLastStepStillCancels() {
        ImagePlus.addImageListener(closeRecorder);
        // Territories plus two density volumes (count, size), then cancel.
        Recording monitor = new Recording(3);
        try {
            ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                    .addLabelImage(labels3D())
                    .regionMask(mask3D())
                    .bandwidth(1.0)
                    .permutations(5)
                    .build(), monitor);
            fail("expected cancellation after the last step");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertEquals(3, monitor.steps.size());
            assertEquals(3, closed.size());
        }
    }

    @Test
    public void cancellationBeforeAnyStepMakesNothing() {
        ImagePlus.addImageListener(closeRecorder);
        try {
            ObjectTerritories.analyze(
                    parameters(AnalysisMode.BOTH, DensityWeightingSelection.BOTH),
                    new Recording(0));
            fail("expected cancellation");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertTrue(closed.isEmpty());
        }
    }

    @Test
    public void threeDimensionalCancellationClosesTerritoryAndDensityStacks() {
        ImagePlus.addImageListener(closeRecorder);
        // Territories (1 stack) and one density volume are made, then cancel.
        Recording monitor = new Recording(2);
        try {
            ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                    .addLabelImage(labels3D())
                    .regionMask(mask3D())
                    .bandwidth(1.0)
                    .permutations(5)
                    .build(), monitor);
            fail("expected cancellation");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertEquals(2, closed.size());
        }
    }

    @Test
    public void monitorOverloadGivesIdenticalOutputToTheOneArgumentMethod() throws Exception {
        ObjectTerritoriesParameters parameters =
                parameters(AnalysisMode.BOTH, DensityWeightingSelection.BOTH);
        ObjectTerritoriesResult plain = ObjectTerritories.analyze(parameters);
        ObjectTerritoriesResult none = ObjectTerritories.analyze(parameters, ProgressMonitor.NONE);
        ObjectTerritoriesResult watched = ObjectTerritories.analyze(
                parameters, new Recording(Integer.MAX_VALUE));
        File a = temporary.newFolder("plain");
        File b = temporary.newFolder("none");
        File c = temporary.newFolder("watched");
        ResultExporter.save(plain, a);
        ResultExporter.save(none, b);
        ResultExporter.save(watched, c);
        for (int region = 0; region < plain.getRegions().size(); region++) {
            for (int map = 0; map < plain.getRegions().get(region).getDensityResults().size(); map++) {
                float[] expected = (float[]) plain.getRegions().get(region).getDensityResults()
                        .get(map).getDensityMap().getProcessor().getPixels();
                assertArrayEquals(expected, (float[]) none.getRegions().get(region)
                        .getDensityResults().get(map).getDensityMap().getProcessor().getPixels(), 0f);
                assertArrayEquals(expected, (float[]) watched.getRegions().get(region)
                        .getDensityResults().get(map).getDensityMap().getProcessor().getPixels(), 0f);
            }
        }
        plain.closeDensityImages();
        none.closeDensityImages();
        watched.closeDensityImages();
        assertSameFiles(a, b);
        assertSameFiles(a, c);
    }

    @Test
    public void threeDimensionalMonitorOverloadGivesIdenticalOutput() throws Exception {
        ObjectTerritoriesParameters3D parameters = ObjectTerritoriesParameters3D.builder()
                .addLabelImage(labels3D())
                .regionMask(mask3D())
                .bandwidth(1.0)
                .permutations(5)
                .build();
        ObjectTerritoriesResult3D plain = ObjectTerritories.analyze3D(parameters);
        ObjectTerritoriesResult3D none = ObjectTerritories.analyze3D(parameters, ProgressMonitor.NONE);
        File a = temporary.newFolder("plain3d");
        File b = temporary.newFolder("none3d");
        ResultExporter3D.save(plain, a);
        ResultExporter3D.save(none, b);
        plain.closeGeneratedImages();
        none.closeGeneratedImages();
        assertSameFiles(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullMonitorIsRejected() {
        ObjectTerritories.analyze(parameters(AnalysisMode.BOTH, DensityWeightingSelection.BOTH), null);
    }

    // ------------------------------------------------------------------

    /** ImagePlus delivers close events on the AWT event queue; wait for them. */
    private static void drainEvents() {
        try {
            java.awt.EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                }
            });
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static int steps(AnalysisMode mode, DensityWeightingSelection weighting) {
        Recording monitor = new Recording(Integer.MAX_VALUE);
        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters(mode, weighting), monitor);
        result.closeDensityImages();
        return monitor.totals.get(0);
    }

    private static ObjectTerritoriesParameters parameters(
            AnalysisMode mode, DensityWeightingSelection weighting) {
        ShortProcessor a = new ShortProcessor(16, 16);
        a.set(2, 2, 1);
        a.set(12, 4, 2);
        ShortProcessor b = new ShortProcessor(16, 16);
        b.set(6, 11, 1);
        b.set(13, 13, 2);
        Roi field = new Roi(0, 0, 16, 16);
        field.setName("Field");
        return ObjectTerritoriesParameters.builder()
                .addLabelImage(new ImagePlus("A", a))
                .addLabelImage(new ImagePlus("B", b))
                .addRegion(field)
                .analysisMode(mode)
                .densityWeightingSelection(weighting)
                .bandwidthMicrons(3.0)
                .permutations(10)
                .build();
    }

    private static ImagePlus labels3D() {
        ImageStack stack = new ImageStack(7, 5);
        for (int z = 0; z < 5; z++) stack.addSlice(new ShortProcessor(7, 5));
        stack.getProcessor(2).set(1, 2, 1);
        stack.getProcessor(4).set(5, 2, 2);
        return new ImagePlus("Cells", stack);
    }

    private static ImagePlus mask3D() {
        ImageStack stack = new ImageStack(7, 5);
        for (int z = 0; z < 5; z++) {
            ByteProcessor processor = new ByteProcessor(7, 5);
            processor.setValue(1);
            processor.fill();
            stack.addSlice(processor);
        }
        return new ImagePlus("Brain", stack);
    }

    private static void assertSameFiles(File expected, File actual) throws Exception {
        List<String> names = new ArrayList<String>();
        list(expected, "", names);
        List<String> other = new ArrayList<String>();
        list(actual, "", other);
        assertEquals(names, other);
        assertTrue(!names.isEmpty());
        for (String name : names) {
            assertArrayEquals(name,
                    Files.readAllBytes(new File(expected, name).toPath()),
                    Files.readAllBytes(new File(actual, name).toPath()));
        }
    }

    private static void list(File directory, String prefix, List<String> names) {
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) list(child, prefix + child.getName() + "/", names);
            else names.add(prefix + child.getName());
        }
    }

    /** Records every update; cancels once {@code allowed} steps have been let through. */
    private static final class Recording implements ProgressMonitor {
        private final int allowed;
        private int checks;
        final List<String> steps = new ArrayList<String>();
        final List<Integer> done = new ArrayList<Integer>();
        final List<Integer> totals = new ArrayList<Integer>();

        Recording(int allowed) {
            this.allowed = allowed;
        }

        @Override
        public void update(String step, int stepDone, int total) {
            steps.add(step);
            done.add(stepDone);
            totals.add(total);
        }

        @Override
        public boolean isCancelled() {
            return checks++ >= allowed;
        }
    }
}
