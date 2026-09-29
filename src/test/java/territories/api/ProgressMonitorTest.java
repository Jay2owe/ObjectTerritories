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
    public void densityStepStopsPartWayThrough() {
        ImagePlus.addImageListener(closeRecorder);
        // Before 0.3.1 the engine never polled, so a step always ran to the end
        // and made its map; now the tenth poll inside the first step stops it.
        MidStep monitor = new MidStep(10);
        try {
            ObjectTerritories.analyze(
                    parameters(AnalysisMode.DENSITY, DensityWeightingSelection.BOTH), monitor);
            fail("expected cancellation inside the first density step");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertEquals(1, monitor.steps.size());
            assertTrue(monitor.steps.get(0), monitor.steps.get(0).contains("density"));
            assertTrue("no density map is finished", closed.isEmpty());
        }
    }

    @Test
    public void threeDimensionalTerritoryStepStopsPartWayThrough() {
        ImagePlus.addImageListener(closeRecorder);
        MidStep monitor = new MidStep(3);
        try {
            ObjectTerritories.analyze3D(ObjectTerritoriesParameters3D.builder()
                    .addLabelImage(spacedLabels3D(160, 160, 24))
                    .regionMask(filledMask3D(160, 160, 24))
                    .permutations(5)
                    .build(), monitor);
            fail("expected cancellation inside the territory step");
        } catch (AnalysisCancelledException expected) {
            drainEvents();
            assertEquals(1, monitor.steps.size());
            assertTrue(monitor.steps.get(0), monitor.steps.get(0).contains("territories"));
            assertTrue("no territory stack is finished", closed.isEmpty());
        }
    }

    /**
     * The Escape check of the GUI kit, without the GUI: a density map that
     * takes seconds is stopped part-way, well within a second of the request.
     */
    @Test(timeout = 180000)
    public void escapeDuringALongDensityStepStopsItWithinASecond() throws Exception {
        ImagePlus.addImageListener(closeRecorder);
        final ObjectTerritoriesParameters parameters = longDensityRun();
        final MidStep monitor = new MidStep(Integer.MAX_VALUE);
        final Throwable[] outcome = new Throwable[1];
        final long[] endedAt = new long[1];
        Thread run = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ObjectTerritories.analyze(parameters, monitor).closeDensityImages();
                } catch (Throwable error) {
                    outcome[0] = error;
                } finally {
                    endedAt[0] = System.nanoTime();
                }
            }
        }, "long-density-run");
        run.start();
        while (monitor.steps.isEmpty() && run.isAlive()) Thread.sleep(2);
        Thread.sleep(400);
        assertTrue("the density step ended within 400 ms; the fixture is too small",
                run.isAlive() && closed.isEmpty());
        monitor.fire();
        run.join(60000);
        assertTrue("the run is still going 60 s after the cancel", !run.isAlive());
        long latencyMillis = (endedAt[0] - monitor.firedAt()) / 1000000L;
        System.out.println("[ProgressMonitorTest] cancelled "
                + (monitor.firedAt() - monitor.stepStartedAt) / 1000000L
                + " ms into the density step; stopped " + latencyMillis + " ms after the request");
        assertTrue("expected AnalysisCancelledException, got " + outcome[0],
                outcome[0] instanceof AnalysisCancelledException);
        drainEvents();
        assertEquals(1, monitor.steps.size());
        assertTrue("the step was stopped before its map was made", closed.isEmpty());
        assertTrue("stopped " + latencyMillis + " ms after the request", latencyMillis < 1000);
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

    /** About 1,600 objects on 1600 x 1600 pixels, 40 um bandwidth: seconds per map. */
    private static ObjectTerritoriesParameters longDensityRun() {
        int size = 1600;
        ShortProcessor labels = new ShortProcessor(size, size);
        int label = 1;
        for (int y = 10; y < size - 10; y += 40) {
            for (int x = 10; x < size - 10; x += 40) {
                labels.setValue(label++);
                labels.fill(new Roi(x, y, 4, 4));
            }
        }
        ImagePlus image = new ImagePlus("Big", labels);
        image.getCalibration().pixelWidth = 0.5;
        image.getCalibration().pixelHeight = 0.5;
        image.getCalibration().setUnit("micron");
        Roi field = new Roi(2, 2, size - 4, size - 4);
        field.setName("Field");
        return ObjectTerritoriesParameters.builder()
                .addLabelImage(image)
                .addRegion(field)
                .analysisMode(AnalysisMode.DENSITY)
                .densityWeightingSelection(DensityWeightingSelection.OBJECT_COUNT)
                .bandwidthMicrons(40.0)
                .build();
    }

    private static ImagePlus spacedLabels3D(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        int label = 1;
        for (int z = 0; z < depth; z++) {
            ShortProcessor processor = new ShortProcessor(width, height);
            if (z % 6 == 3) {
                for (int y = 5; y < height; y += 20) {
                    for (int x = 5 + z; x < width; x += 20) processor.set(x, y, label++);
                }
            }
            stack.addSlice(processor);
        }
        return new ImagePlus("Spaced", stack);
    }

    private static ImagePlus filledMask3D(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            ByteProcessor processor = new ByteProcessor(width, height);
            processor.setValue(1);
            processor.fill();
            stack.addSlice(processor);
        }
        return new ImagePlus("Filled", stack);
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

    /**
     * Records every update; cancels once {@code allowed} steps have been let
     * through. Only the between-step polls are counted: since 0.3.1 the
     * engines also poll inside every step, from worker threads, and those
     * polls simply repeat the last between-step answer.
     */
    private static final class Recording implements ProgressMonitor {
        private final int allowed;
        private int checks;
        private volatile boolean cancelled;
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
            if (!betweenSteps()) return cancelled;
            if (checks++ >= allowed) cancelled = true;
            return cancelled;
        }

        private static boolean betweenSteps() {
            for (StackTraceElement frame : new Throwable().getStackTrace()) {
                if (frame.getClassName().equals(ObjectTerritories.class.getName())
                        && frame.getMethodName().equals("checkCancelled")) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Never cancels between steps; inside a step it cancels at the
     * {@code afterPolls}-th engine poll, or once {@link #fire()} is called.
     */
    private static final class MidStep implements ProgressMonitor {
        private final int afterPolls;
        private final java.util.concurrent.atomic.AtomicInteger polls =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile boolean fired;
        private volatile long firedAt;
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        volatile long stepStartedAt;

        MidStep(int afterPolls) {
            this.afterPolls = afterPolls;
        }

        void fire() {
            firedAt = System.nanoTime();
            fired = true;
        }

        long firedAt() {
            return firedAt;
        }

        int polls() {
            return polls.get();
        }

        @Override
        public void update(String step, int stepDone, int total) {
            steps.add(step);
            stepStartedAt = System.nanoTime();
        }

        @Override
        public boolean isCancelled() {
            if (Recording.betweenSteps()) return false;
            if (fired) return true;
            if (polls.incrementAndGet() >= afterPolls) {
                fire();
                return true;
            }
            return false;
        }
    }
}
