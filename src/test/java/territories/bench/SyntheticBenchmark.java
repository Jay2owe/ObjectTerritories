package territories.bench;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import sc.fiji.territories.core.DensityResult;
import sc.fiji.territories.core.DensityResult3D;
import territories.api.AnalysisMode;
import territories.api.DensityWeightingSelection;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionAnalysisResult;
import territories.api.RegionAnalysisResult3D;
import territories.output.ResultExporter;
import territories.output.ResultExporter3D;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reproducible speed benchmark on synthetic inputs, with output digests.
 *
 * <p>Skipped unless {@code -Dterritories.benchmark=true}; not matched by the
 * default test pattern. Each case prints the median wall time of the analysis
 * call over {@code territories.benchmark.reps} runs (default 3) and two
 * SHA-256 digests: one over every density pixel as raw float bits, one over
 * every exported file (CSV text and TIFF bytes). Equal digests before and
 * after a change prove the outputs are bit-identical at full size.
 *
 * <p>The build's test JVM is capped at 512 MB, below what the 3D cases need,
 * so the test re-runs this class in a child JVM with
 * {@code -Xmx${territories.benchmark.heap}} (default 4g), passing the
 * benchmark and {@code territories.parallelism} properties through.
 *
 * <pre>
 * ./mvnw -B -q test -Dtest=SyntheticBenchmark -Dterritories.benchmark=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false [-Dterritories.benchmark.cases=2d-density-auto,...]
 * </pre>
 */
public class SyntheticBenchmark {

    private static final String[] ALL_CASES = {
            "2d-density-auto", "2d-density-bw10", "2d-territories",
            "3d-territories", "3d-density-bw5", "3d-density-auto"};
    private static final String[] PASSED_PROPERTIES = {
            "territories.parallelism", "territories.benchmark.cases",
            "territories.benchmark.reps"};

    @Test
    public void run() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("territories.benchmark"));
        List<String> command = new ArrayList<String>();
        command.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getPath());
        command.add("-Xmx" + System.getProperty("territories.benchmark.heap", "4g"));
        command.add("-Djava.awt.headless=true");
        for (String key : PASSED_PROPERTIES) {
            String value = System.getProperty(key);
            if (value != null) command.add("-D" + key + "=" + value);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(SyntheticBenchmark.class.getName());
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        BufferedReader output = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = output.readLine()) != null) System.out.println(line);
        Assert.assertEquals("benchmark JVM exit code", 0, child.waitFor());
    }

    public static void main(String[] args) throws Exception {
        benchmark();
        System.exit(0);
    }

    private static void benchmark() throws Exception {
        int reps = Integer.getInteger("territories.benchmark.reps", 3);
        List<String> cases = Arrays.asList(System.getProperty(
                "territories.benchmark.cases", String.join(",", ALL_CASES)).split(","));
        System.out.println("SyntheticBenchmark: " + Runtime.getRuntime().availableProcessors()
                + " CPUs, java " + System.getProperty("java.version")
                + ", territories.parallelism=" + System.getProperty("territories.parallelism", "auto")
                + ", heap " + (Runtime.getRuntime().maxMemory() >> 20) + " MB, reps=" + reps);
        List<ImagePlus> labels2D = null;
        List<ImagePlus> labels3D = null;
        ImagePlus mask3D = null;
        for (String name : cases) {
            if (name.startsWith("2d") && labels2D == null) labels2D = labels2D(1024, 1000);
            if (name.startsWith("3d") && labels3D == null) {
                labels3D = labels3D(800);
                mask3D = mask3D();
            }
            if (name.equals("2d-density-auto")) {
                run2D(name, labels2D, AnalysisMode.DENSITY, 0.0, reps);
            } else if (name.equals("2d-density-bw10")) {
                run2D(name, labels2D, AnalysisMode.DENSITY, 10.0, reps);
            } else if (name.equals("2d-territories")) {
                run2D(name, labels2D, AnalysisMode.TERRITORIES, 0.0, reps);
            } else if (name.equals("3d-territories")) {
                run3D(name, labels3D, mask3D, AnalysisMode.TERRITORIES, 0.0, reps);
            } else if (name.equals("3d-density-bw5")) {
                run3D(name, labels3D, mask3D, AnalysisMode.DENSITY, 5.0, reps);
            } else if (name.equals("3d-density-auto")) {
                run3D(name, labels3D, mask3D, AnalysisMode.DENSITY, 0.0, reps);
            } else {
                throw new IllegalArgumentException("unknown benchmark case: " + name);
            }
        }
    }

    private static void run2D(
            String name, List<ImagePlus> labels, AnalysisMode mode, double bandwidth, int reps)
            throws Exception {
        Roi roi = new Roi(10, 10, 1024 - 20, 1024 - 20);
        roi.setName("R");
        ObjectTerritoriesParameters parameters = ObjectTerritoriesParameters.builder()
                .labelImages(labels)
                .regions(Collections.singletonList(roi))
                .analysisMode(mode)
                .densityWeightingSelection(DensityWeightingSelection.BOTH)
                .bandwidthMicrons(bandwidth)
                .permutations(1000)
                .seed(12345L)
                .build();
        double[] seconds = new double[reps];
        String pixelDigest = null;
        String fileDigest = null;
        for (int rep = 0; rep < reps; rep++) {
            long start = System.nanoTime();
            ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters);
            seconds[rep] = (System.nanoTime() - start) / 1e9;
            if (rep == 0) {
                MessageDigest pixels = MessageDigest.getInstance("SHA-256");
                for (RegionAnalysisResult region : result.getRegions()) {
                    for (DensityResult density : region.getDensityResults()) {
                        addFloats(pixels, (float[]) density.getDensityMap().getProcessor().getPixels());
                    }
                }
                pixelDigest = hex(pixels.digest());
                File folder = Files.createTempDirectory("ot-bench-" + name).toFile();
                ResultExporter.save(result, folder);
                fileDigest = digestFiles(folder);
                delete(folder);
            }
            result.closeDensityImages();
        }
        report(name, seconds, pixelDigest, fileDigest);
    }

    private static void run3D(
            String name, List<ImagePlus> labels, ImagePlus mask, AnalysisMode mode,
            double bandwidth, int reps) throws Exception {
        ObjectTerritoriesParameters3D parameters = ObjectTerritoriesParameters3D.builder()
                .labelImages(labels)
                .regionMask(mask)
                .analysisMode(mode)
                .densityWeightingSelection(DensityWeightingSelection.BOTH)
                .bandwidth(bandwidth)
                .permutations(1000)
                .seed(12345L)
                .build();
        double[] seconds = new double[reps];
        String pixelDigest = null;
        String fileDigest = null;
        for (int rep = 0; rep < reps; rep++) {
            long start = System.nanoTime();
            ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(parameters);
            seconds[rep] = (System.nanoTime() - start) / 1e9;
            if (rep == 0) {
                MessageDigest pixels = MessageDigest.getInstance("SHA-256");
                for (RegionAnalysisResult3D region : result.getRegions()) {
                    for (DensityResult3D density : region.getDensityResults()) {
                        ImageStack stack = density.getDensityVolume().getStack();
                        for (int z = 1; z <= stack.getSize(); z++) {
                            addFloats(pixels, (float[]) stack.getPixels(z));
                        }
                    }
                }
                pixelDigest = hex(pixels.digest());
                File folder = Files.createTempDirectory("ot-bench-" + name).toFile();
                ResultExporter3D.save(result, folder);
                fileDigest = digestFiles(folder);
                delete(folder);
            }
            result.closeGeneratedImages();
        }
        report(name, seconds, pixelDigest, fileDigest);
    }

    private static void report(String name, double[] seconds, String pixels, String files) {
        double[] sorted = seconds.clone();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        StringBuilder all = new StringBuilder();
        for (double value : seconds) {
            if (all.length() > 0) all.append(", ");
            all.append(String.format(Locale.ROOT, "%.2f", value));
        }
        System.out.println(String.format(Locale.ROOT,
                "BENCH %-16s median %8.2f s  runs [%s]  pixels %s  files %s",
                name, median, all, pixels.substring(0, 16), files.substring(0, 16)));
    }

    // -- synthetic inputs, identical to the planning harness ----------------

    private static List<ImagePlus> labels2D(int size, int perType) {
        Random random = new Random(1);
        List<ImagePlus> labels = new ArrayList<ImagePlus>();
        for (int type = 0; type < 2; type++) {
            ShortProcessor pixels = new ShortProcessor(size, size);
            for (int i = 1; i <= perType; i++) {
                int cx = 5 + random.nextInt((size - 10) / 2) * 2 + type;
                int cy = 5 + random.nextInt(size - 10);
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (pixels.get(cx + dx, cy + dy) == 0) pixels.set(cx + dx, cy + dy, i);
                    }
                }
            }
            ImagePlus image = new ImagePlus("T" + type, pixels);
            Calibration calibration = image.getCalibration();
            calibration.pixelWidth = 0.5;
            calibration.pixelHeight = 0.5;
            calibration.setUnit("um");
            labels.add(image);
        }
        return labels;
    }

    private static List<ImagePlus> labels3D(int perType) {
        int w = 384;
        int h = 384;
        int d = 64;
        Random random = new Random(2);
        List<ImagePlus> labels = new ArrayList<ImagePlus>();
        for (int type = 0; type < 2; type++) {
            ImageStack stack = new ImageStack(w, h);
            for (int z = 0; z < d; z++) stack.addSlice(new ShortProcessor(w, h));
            for (int i = 1; i <= perType; i++) {
                int cx = 3 + random.nextInt((w - 6) / 2) * 2 + type;
                int cy = 3 + random.nextInt(h - 6);
                int cz = 2 + random.nextInt(d - 4);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            ShortProcessor pixels = (ShortProcessor) stack.getProcessor(cz + dz + 1);
                            if (pixels.get(cx + dx, cy + dy) == 0) pixels.set(cx + dx, cy + dy, i);
                        }
                    }
                }
            }
            ImagePlus image = new ImagePlus("T" + type, stack);
            calibrate3D(image);
            labels.add(image);
        }
        return labels;
    }

    private static ImagePlus mask3D() {
        int w = 384;
        int h = 384;
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < 64; z++) {
            ByteProcessor pixels = new ByteProcessor(w, h);
            pixels.setValue(1);
            pixels.fill();
            pixels.setValue(2);
            pixels.setRoi(w / 2, 0, w / 2, h);
            pixels.fill();
            pixels.resetRoi();
            stack.addSlice(pixels);
        }
        ImagePlus mask = new ImagePlus("Mask", stack);
        calibrate3D(mask);
        return mask;
    }

    private static void calibrate3D(ImagePlus image) {
        Calibration calibration = image.getCalibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 2.0;
        calibration.setUnit("um");
    }

    // -- digests --------------------------------------------------------------

    private static void addFloats(MessageDigest digest, float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        for (float value : values) buffer.putInt(Float.floatToRawIntBits(value));
        digest.update(buffer.array());
    }

    private static String digestFiles(File root) throws Exception {
        List<String> names = new ArrayList<String>();
        collect(root, "", names);
        Collections.sort(names);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String name : names) {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(new File(root, name).toPath()));
        }
        return hex(digest.digest());
    }

    private static void collect(File directory, String prefix, List<String> names) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, prefix + child.getName() + "/", names);
            else names.add(prefix + child.getName());
        }
    }

    private static void delete(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        Files.deleteIfExists(file.toPath());
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value));
        return text.toString();
    }
}
