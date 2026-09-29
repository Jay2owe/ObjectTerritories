package territories.batch;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import sc.fiji.oc3d.core.io.RegexGroupDiscovery;
import territories.api.AnalysisCancelledException;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.ProgressMonitor;
import territories.io.RegionRoiLoader;
import territories.output.ResultExporter;
import territories.output.ResultExporter3D;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Discovers and executes 2D and 3D Object Territories folder batches. */
public final class ObjectTerritoriesBatchRunner {

    /** File name of the per-run manifest written to the output directory. */
    public static final String MANIFEST_FILE_NAME = "Batch_Manifest.csv";

    private static final int MAX_LABEL_TYPES = 5;
    private static final String[] MANIFEST_COLUMNS = {
            "Folder", "Group", "Dimensions", "Status", "Label_Types", "Output", "Message"};

    private ObjectTerritoriesBatchRunner() {
    }

    /** Returns a deterministic, non-mutating preview of the groups that would run. */
    public static String preview(ObjectTerritoriesBatchParameters parameters) {
        Compiled compiled = validate(parameters);
        Map<String, Map<String, List<File>>> folders = discover(parameters, compiled.pattern);
        if (folders.isEmpty()) return "No matching files found.";

        int totalGroups = 0;
        int runnableGroups = 0;
        int totalFiles = 0;
        for (Map<String, List<File>> groups : folders.values()) {
            totalGroups += groups.size();
            for (List<File> files : groups.values()) {
                totalFiles += files.size();
                if (problem(parameters, split(parameters, compiled, files)) == null) {
                    runnableGroups++;
                }
            }
        }

        StringBuilder text = new StringBuilder();
        text.append(folders.size()).append(" folder(s), ")
                .append(totalGroups).append(" group(s), ")
                .append(runnableGroups).append(" runnable, ")
                .append(totalFiles).append(" files");
        if (parameters.isThreeDimensional()) {
            text.append(" (3D; region-mask type '")
                    .append(parameters.getRegionMaskType()).append("')");
        }
        text.append("\n\n");
        for (Map.Entry<String, Map<String, List<File>>> folder : folders.entrySet()) {
            text.append(folder.getKey().isEmpty() ? "(root)" : folder.getKey() + "/")
                    .append('\n');
            for (Map.Entry<String, List<File>> group : folder.getValue().entrySet()) {
                List<File> files = group.getValue();
                GroupFiles split = split(parameters, compiled, files);
                String problem = problem(parameters, split);
                text.append("  ").append(group.getKey()).append("  (")
                        .append(split.labels.size()).append(" label type(s)")
                        .append(parameters.isThreeDimensional()
                                ? " + " + split.masks.size() + " mask" : "")
                        .append(problem == null ? "" : " - " + problem)
                        .append(")\n");
                for (File file : files) {
                    String tag = split.masks.contains(file)
                            ? "mask"
                            : typeName(file, compiled.pattern, compiled.typeCaptureGroup);
                    text.append("    [").append(tag).append("] ")
                            .append(file.getName()).append('\n');
                }
            }
        }
        return text.toString();
    }

    /**
     * Runs every valid group and saves each sample beneath the configured output directory.
     * A failed group is recorded in the manifest and does not prevent later groups from running.
     * The manifest is always written to {@value #MANIFEST_FILE_NAME} in the output directory.
     */
    public static ObjectTerritoriesBatchResult run(
            ObjectTerritoriesBatchParameters parameters) throws IOException {
        return run(parameters, ProgressMonitor.NONE);
    }

    /**
     * As {@link #run(ObjectTerritoriesBatchParameters)}, reporting one step per
     * sample. When the monitor cancels, the sample in progress and every later
     * group are recorded as CANCELLED and the manifest is still saved.
     */
    public static ObjectTerritoriesBatchResult run(
            ObjectTerritoriesBatchParameters parameters,
            ProgressMonitor monitor) throws IOException {
        if (monitor == null) throw new IllegalArgumentException("progress monitor must not be null");
        Compiled compiled = validate(parameters);
        Files.createDirectories(parameters.getOutputDirectory().toPath());
        List<Roi> regions = parameters.isThreeDimensional()
                ? null : RegionRoiLoader.load(parameters.getRegionSource());
        Map<String, Map<String, List<File>>> folders = discover(parameters, compiled.pattern);

        ResultsTable manifest = new ResultsTable();
        ArrayList<String> processed = new ArrayList<String>();
        int skipped = 0;
        int errors = 0;
        int cancelled = 0;
        boolean stopping = false;
        int totalGroups = 0;
        for (Map<String, List<File>> groups : folders.values()) totalGroups += groups.size();
        int groupNumber = 0;
        Map<String, Set<String>> usedOutputNames = new LinkedHashMap<String, Set<String>>();
        String dimensions = parameters.isThreeDimensional() ? "3D" : "2D";
        File manifestFile = new File(parameters.getOutputDirectory(), MANIFEST_FILE_NAME);

        try {
            for (Map.Entry<String, Map<String, List<File>>> folder : folders.entrySet()) {
                String relativeFolder = folder.getKey();
                for (Map.Entry<String, List<File>> group : folder.getValue().entrySet()) {
                    String groupKey = group.getKey();
                    GroupFiles split = split(parameters, compiled, group.getValue());
                    int labelCount = split.labels.size();
                    groupNumber++;
                    if (!stopping && monitor.isCancelled()) stopping = true;
                    if (stopping) {
                        cancelled++;
                        addManifest(
                                manifest, relativeFolder, groupKey, dimensions, "CANCELLED",
                                labelCount, "", "The batch was cancelled before this group ran.");
                        continue;
                    }
                    if (labelCount > MAX_LABEL_TYPES) {
                        skipped++;
                        addManifest(
                                manifest, relativeFolder, groupKey, dimensions, "SKIPPED",
                                labelCount, "", "A sample can contain at most five label types.");
                        continue;
                    }

                    String outputName = uniqueOutputName(
                            relativeFolder, groupKey, usedOutputNames);
                    File folderOutput = relativeFolder.isEmpty()
                            ? parameters.getOutputDirectory()
                            : new File(parameters.getOutputDirectory(),
                                    slashToPlatform(relativeFolder));
                    File sampleOutput = new File(folderOutput, outputName);
                    String sampleKey = relativeFolder.isEmpty()
                            ? groupKey : relativeFolder + "/" + groupKey;

                    String prefix = "Sample " + groupNumber + "/" + totalGroups + ": "
                            + RegexGroupDiscovery.groupDisplayName(groupKey);
                    monitor.update(prefix, groupNumber - 1, totalGroups);
                    ProgressMonitor sampleMonitor = sampleMonitor(
                            monitor, prefix, groupNumber - 1, totalGroups);
                    try {
                        List<String> warnings = parameters.isThreeDimensional()
                                ? runSample3D(parameters, compiled, groupKey, split,
                                        sampleOutput, sampleMonitor)
                                : runSample2D(parameters, compiled, groupKey, split, regions,
                                        sampleOutput, sampleMonitor);
                        processed.add(sampleKey);
                        addManifest(
                                manifest, relativeFolder, groupKey, dimensions, "PROCESSED",
                                labelCount, sampleOutput.getAbsolutePath(),
                                join(warnings));
                    } catch (AnalysisCancelledException stop) {
                        stopping = true;
                        cancelled++;
                        addManifest(
                                manifest, relativeFolder, groupKey, dimensions, "CANCELLED",
                                labelCount, "", "The batch was cancelled while this group ran; "
                                        + "nothing was saved for it.");
                    } catch (Exception error) {
                        errors++;
                        addManifest(
                                manifest, relativeFolder, groupKey, dimensions, "ERROR",
                                labelCount, sampleOutput.getAbsolutePath(), message(error));
                    }
                }
            }
        } finally {
            saveManifest(manifest, manifestFile);
        }
        if (!stopping) monitor.update("done", totalGroups, totalGroups);
        return new ObjectTerritoriesBatchResult(
                processed, skipped, errors, cancelled, manifest, manifestFile);
    }

    private static ProgressMonitor sampleMonitor(
            final ProgressMonitor batch, final String prefix, final int done, final int total) {
        return new ProgressMonitor() {
            @Override
            public void update(String step, int stepDone, int stepTotal) {
                batch.update(prefix + " - " + step, done, total);
            }

            @Override
            public boolean isCancelled() {
                return batch.isCancelled();
            }
        };
    }

    private static String join(List<String> warnings) {
        StringBuilder text = new StringBuilder();
        for (String warning : warnings) {
            if (text.length() > 0) text.append("; ");
            text.append(warning);
        }
        return text.toString();
    }

    private static List<String> runSample2D(
            ObjectTerritoriesBatchParameters parameters,
            Compiled compiled,
            String groupKey,
            GroupFiles split,
            List<Roi> regions,
            File sampleOutput,
            ProgressMonitor monitor) throws IOException {
        ArrayList<ImagePlus> labels = new ArrayList<ImagePlus>();
        ObjectTerritoriesResult result = null;
        try {
            openLabels(split.labels, compiled, groupKey, labels);
            ObjectTerritoriesParameters analysis = ObjectTerritoriesParameters.builder()
                    .labelImages(labels)
                    .regions(regions)
                    .analysisMode(parameters.getAnalysisMode())
                    .regionMode(parameters.getRegionMode())
                    .edgeCellPolicy(parameters.getEdgeCellPolicy())
                    .densityWeightingSelection(parameters.getDensityWeightingSelection())
                    .densityBoundaryMode(parameters.getDensityBoundaryMode())
                    .bandwidthMicrons(parameters.getBandwidthMicrons())
                    .permutations(parameters.getPermutations())
                    .seed(parameters.getSeed())
                    .build();
            result = ObjectTerritories.analyze(analysis, monitor);
            ResultExporter.save(result, sampleOutput);
            return result.getWarnings();
        } finally {
            if (result != null) result.closeDensityImages();
            closeAll(labels);
        }
    }

    // Keep the shared settings in the same order as runSample2D so the two
    // dimensionalities cannot drift apart.
    private static List<String> runSample3D(
            ObjectTerritoriesBatchParameters parameters,
            Compiled compiled,
            String groupKey,
            GroupFiles split,
            File sampleOutput,
            ProgressMonitor monitor) throws IOException {
        String maskType = parameters.getRegionMaskType();
        if (split.masks.isEmpty()) {
            throw new IllegalArgumentException(
                    "no region-mask file (type '" + maskType + "') in group " + groupKey);
        }
        if (split.masks.size() > 1) {
            throw new IllegalArgumentException(
                    "more than one region-mask file (type '" + maskType + "') in group "
                            + groupKey);
        }
        ArrayList<ImagePlus> opened = new ArrayList<ImagePlus>();
        ObjectTerritoriesResult3D result = null;
        try {
            ArrayList<ImagePlus> labels = new ArrayList<ImagePlus>();
            openLabels(split.labels, compiled, groupKey, labels);
            opened.addAll(labels);
            File maskFile = split.masks.get(0);
            ImagePlus mask = IJ.openImage(maskFile.getAbsolutePath());
            if (mask == null) {
                throw new IOException("Could not open region-mask stack: " + maskFile);
            }
            opened.add(mask);
            mask.setTitle(typeName(maskFile, compiled.pattern, compiled.typeCaptureGroup));

            ObjectTerritoriesParameters3D analysis = ObjectTerritoriesParameters3D.builder()
                    .labelImages(labels)
                    .regionMask(mask)
                    .analysisMode(parameters.getAnalysisMode())
                    .regionMode(parameters.getRegionMode())
                    .edgeCellPolicy(parameters.getEdgeCellPolicy())
                    .densityWeightingSelection(parameters.getDensityWeightingSelection())
                    .densityBoundaryMode(parameters.getDensityBoundaryMode())
                    .bandwidth(parameters.getBandwidthMicrons())
                    .permutations(parameters.getPermutations())
                    .seed(parameters.getSeed())
                    .build();
            result = ObjectTerritories.analyze3D(analysis, monitor);
            ResultExporter3D.save(result, sampleOutput);
            return result.getWarnings();
        } finally {
            if (result != null) result.closeGeneratedImages();
            closeAll(opened);
        }
    }

    private static void openLabels(
            List<File> files,
            Compiled compiled,
            String groupKey,
            List<ImagePlus> labels) throws IOException {
        Set<String> typeNames = new HashSet<String>();
        for (File file : files) {
            String type = typeName(file, compiled.pattern, compiled.typeCaptureGroup);
            if (type.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "The type capture group is empty for " + file.getName());
            }
            if (!typeNames.add(type)) {
                throw new IllegalArgumentException(
                        "Duplicate label type '" + type + "' in group " + groupKey);
            }
            ImagePlus image = IJ.openImage(file.getAbsolutePath());
            if (image == null) {
                throw new IOException("Could not open label image: " + file);
            }
            image.setTitle(type);
            labels.add(image);
        }
    }

    private static void closeAll(List<ImagePlus> images) {
        for (ImagePlus image : images) {
            image.close();
            image.flush();
        }
    }

    /** Splits a group into label files and (3D only) region-mask files. */
    private static GroupFiles split(
            ObjectTerritoriesBatchParameters parameters, Compiled compiled, List<File> files) {
        GroupFiles split = new GroupFiles();
        String maskType = parameters.isThreeDimensional()
                ? parameters.getRegionMaskType().trim() : null;
        for (File file : files) {
            String type = typeName(file, compiled.pattern, compiled.typeCaptureGroup);
            if (maskType != null && type.trim().equalsIgnoreCase(maskType)) {
                split.masks.add(file);
            } else {
                split.labels.add(file);
            }
        }
        return split;
    }

    /** Why a group cannot run, as shown in the preview, or {@code null} if it can. */
    private static String problem(ObjectTerritoriesBatchParameters parameters, GroupFiles split) {
        if (split.labels.size() > MAX_LABEL_TYPES) return "SKIP: maximum is 5";
        if (!parameters.isThreeDimensional()) return null;
        if (split.masks.isEmpty()) return "SKIP/ERROR: no mask";
        if (split.masks.size() > 1) return "ERROR: more than one mask";
        if (split.labels.isEmpty()) return "ERROR: no label stacks";
        return null;
    }

    private static Map<String, Map<String, List<File>>> discover(
            ObjectTerritoriesBatchParameters parameters, Pattern pattern) {
        Set<File> excluded = Collections.singleton(parameters.getOutputDirectory());
        return RegexGroupDiscovery.findGroupsRecursive(
                parameters.getInputFolder(),
                pattern,
                parameters.getTypeCaptureGroup(),
                parameters.isRecursive(),
                RegexGroupDiscovery.GroupOrder.FILENAME_IGNORE_CASE,
                excluded);
    }

    private static Compiled validate(ObjectTerritoriesBatchParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Batch parameters are required.");
        }
        File input = parameters.getInputFolder();
        if (input == null || !input.isDirectory()) {
            throw new IllegalArgumentException("Input folder does not exist: " + input);
        }
        String regex = parameters.getFilenameRegex();
        if (regex == null || regex.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename regex must not be blank.");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException error) {
            throw new IllegalArgumentException("Invalid filename regex: " + error.getDescription(), error);
        }
        int typeCaptureGroup = parameters.getTypeCaptureGroup();
        int groupCount = pattern.matcher("").groupCount();
        if (typeCaptureGroup < 1 || typeCaptureGroup > groupCount) {
            throw new IllegalArgumentException(
                    "Type capture group must be between 1 and " + groupCount + ".");
        }
        if (parameters.isThreeDimensional()) {
            String maskType = parameters.getRegionMaskType();
            if (maskType == null || maskType.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Region-mask type must not be blank in a 3D batch.");
            }
        } else {
            File regionSource = parameters.getRegionSource();
            if (regionSource == null || !regionSource.isFile()) {
                throw new IllegalArgumentException(
                        "Region ROI source does not exist: " + regionSource);
            }
        }
        File output = parameters.getOutputDirectory();
        if (output == null) {
            throw new IllegalArgumentException("Output directory is required.");
        }
        try {
            if (input.getCanonicalFile().equals(output.getCanonicalFile())) {
                throw new IllegalArgumentException(
                        "Output directory must not be the input folder itself.");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not resolve input and output paths.", error);
        }
        return new Compiled(pattern, typeCaptureGroup);
    }

    private static String typeName(File file, Pattern pattern, int captureGroup) {
        Matcher matcher = pattern.matcher(file.getName());
        if (!matcher.matches() || matcher.start(captureGroup) < 0) {
            return "<missing>";
        }
        String value = matcher.group(captureGroup);
        return value == null ? "<missing>" : value;
    }

    private static String uniqueOutputName(
            String relativeFolder,
            String groupKey,
            Map<String, Set<String>> usedByFolder) {
        Set<String> used = usedByFolder.get(relativeFolder);
        if (used == null) {
            used = new HashSet<String>();
            usedByFolder.put(relativeFolder, used);
        }
        String base = safe(RegexGroupDiscovery.groupDisplayName(groupKey));
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static String safe(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        // oc3d-core 0.1.0 trims separators before removing the extension, so
        // sample_*.tif becomes sample_. Finish that presentation-only cleanup
        // here while keeping the shared grouping mechanics pinned and intact.
        safe = safe.replaceAll("^[_\\-.]+|[_\\-.]+$", "");
        return safe.isEmpty() ? "batch" : safe;
    }

    private static String slashToPlatform(String relative) {
        return relative.replace('/', File.separatorChar);
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private static void addManifest(
            ResultsTable table,
            String folder,
            String group,
            String dimensions,
            String status,
            int labelCount,
            String output,
            String detail) {
        table.incrementCounter();
        table.addValue("Folder", folder.isEmpty() ? "." : folder);
        table.addValue("Group", group);
        table.addValue("Dimensions", dimensions);
        table.addValue("Status", status);
        table.addValue("Label_Types", labelCount);
        table.addValue("Output", output);
        table.addValue("Message", detail);
    }

    private static void saveManifest(ResultsTable manifest, File destination)
            throws IOException {
        if (manifest.size() == 0) {
            // An empty ResultsTable has no columns to write; keep the header so
            // the file is always a readable CSV.
            Files.write(destination.toPath(),
                    (String.join(",", MANIFEST_COLUMNS) + "\n")
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        manifest.saveAs(destination.getAbsolutePath());
    }

    private static final class GroupFiles {
        private final List<File> labels = new ArrayList<File>();
        private final List<File> masks = new ArrayList<File>();
    }

    private static final class Compiled {
        private final Pattern pattern;
        private final int typeCaptureGroup;

        private Compiled(Pattern pattern, int typeCaptureGroup) {
            this.pattern = pattern;
            this.typeCaptureGroup = typeCaptureGroup;
        }
    }
}
