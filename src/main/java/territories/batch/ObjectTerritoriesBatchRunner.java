package territories.batch;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import sc.fiji.oc3d.core.io.RegexGroupDiscovery;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesResult;
import territories.io.RegionRoiLoader;
import territories.output.ResultExporter;

import java.io.File;
import java.io.IOException;
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

/** Discovers and executes two-dimensional Object Territories folder batches. */
public final class ObjectTerritoriesBatchRunner {

    private static final int MAX_LABEL_TYPES = 5;

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
                if (files.size() <= MAX_LABEL_TYPES) runnableGroups++;
            }
        }

        StringBuilder text = new StringBuilder();
        text.append(folders.size()).append(" folder(s), ")
                .append(totalGroups).append(" group(s), ")
                .append(runnableGroups).append(" runnable, ")
                .append(totalFiles).append(" files\n\n");
        for (Map.Entry<String, Map<String, List<File>>> folder : folders.entrySet()) {
            text.append(folder.getKey().isEmpty() ? "(root)" : folder.getKey() + "/")
                    .append('\n');
            for (Map.Entry<String, List<File>> group : folder.getValue().entrySet()) {
                List<File> files = group.getValue();
                text.append("  ").append(group.getKey()).append("  (")
                        .append(files.size()).append(" label type(s)")
                        .append(files.size() > MAX_LABEL_TYPES ? " - SKIP: maximum is 5" : "")
                        .append(")\n");
                for (File file : files) {
                    text.append("    [")
                            .append(typeName(file, compiled.pattern, compiled.typeCaptureGroup))
                            .append("] ").append(file.getName()).append('\n');
                }
            }
        }
        return text.toString();
    }

    /**
     * Runs every valid group and saves each sample beneath the configured output directory.
     * A failed group is recorded in the manifest and does not prevent later groups from running.
     */
    public static ObjectTerritoriesBatchResult run(
            ObjectTerritoriesBatchParameters parameters) throws IOException {
        Compiled compiled = validate(parameters);
        Files.createDirectories(parameters.getOutputDirectory().toPath());
        List<Roi> regions = RegionRoiLoader.load(parameters.getRegionSource());
        Map<String, Map<String, List<File>>> folders = discover(parameters, compiled.pattern);

        ResultsTable manifest = new ResultsTable();
        ArrayList<String> processed = new ArrayList<String>();
        int skipped = 0;
        int errors = 0;
        Map<String, Set<String>> usedOutputNames = new LinkedHashMap<String, Set<String>>();

        for (Map.Entry<String, Map<String, List<File>>> folder : folders.entrySet()) {
            String relativeFolder = folder.getKey();
            for (Map.Entry<String, List<File>> group : folder.getValue().entrySet()) {
                String groupKey = group.getKey();
                List<File> files = group.getValue();
                if (files.size() > MAX_LABEL_TYPES) {
                    skipped++;
                    addManifest(
                            manifest, relativeFolder, groupKey, "SKIPPED", files.size(), "",
                            "A sample can contain at most five label types.");
                    continue;
                }

                String outputName = uniqueOutputName(
                        relativeFolder, groupKey, usedOutputNames);
                File folderOutput = relativeFolder.isEmpty()
                        ? parameters.getOutputDirectory()
                        : new File(parameters.getOutputDirectory(), slashToPlatform(relativeFolder));
                File sampleOutput = new File(folderOutput, outputName);
                String sampleKey = relativeFolder.isEmpty()
                        ? groupKey : relativeFolder + "/" + groupKey;

                ArrayList<ImagePlus> labels = new ArrayList<ImagePlus>();
                ObjectTerritoriesResult result = null;
                try {
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
                    result = ObjectTerritories.analyze(analysis);
                    ResultExporter.save(result, sampleOutput);
                    processed.add(sampleKey);
                    addManifest(
                            manifest, relativeFolder, groupKey, "PROCESSED", files.size(),
                            sampleOutput.getAbsolutePath(), "");
                } catch (Exception error) {
                    errors++;
                    addManifest(
                            manifest, relativeFolder, groupKey, "ERROR", files.size(),
                            sampleOutput.getAbsolutePath(), message(error));
                } finally {
                    if (result != null) result.closeDensityImages();
                    for (ImagePlus label : labels) {
                        label.close();
                        label.flush();
                    }
                }
            }
        }
        return new ObjectTerritoriesBatchResult(processed, skipped, errors, manifest);
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
        File regionSource = parameters.getRegionSource();
        if (regionSource == null || !regionSource.isFile()) {
            throw new IllegalArgumentException(
                    "Region ROI source does not exist: " + regionSource);
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
            String status,
            int labelCount,
            String output,
            String detail) {
        table.incrementCounter();
        table.addValue("Folder", folder.isEmpty() ? "." : folder);
        table.addValue("Group", group);
        table.addValue("Status", status);
        table.addValue("Label_Types", labelCount);
        table.addValue("Output", output);
        table.addValue("Message", detail);
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
