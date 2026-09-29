package territories.batch;

import ij.measure.ResultsTable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Summary and manifest returned by a completed folder batch. */
public final class ObjectTerritoriesBatchResult {

    private final List<String> processedSamples;
    private final int skippedGroups;
    private final int errorGroups;
    private final ResultsTable manifest;
    private final File manifestFile;

    ObjectTerritoriesBatchResult(
            List<String> processedSamples,
            int skippedGroups,
            int errorGroups,
            ResultsTable manifest,
            File manifestFile) {
        this.processedSamples = Collections.unmodifiableList(
                new ArrayList<String>(processedSamples));
        this.skippedGroups = skippedGroups;
        this.errorGroups = errorGroups;
        this.manifest = manifest;
        this.manifestFile = manifestFile;
    }

    public List<String> getProcessedSamples() {
        return processedSamples;
    }

    public int getProcessedGroups() {
        return processedSamples.size();
    }

    public int getSkippedGroups() {
        return skippedGroups;
    }

    public int getErrorGroups() {
        return errorGroups;
    }

    /** One row per discovered group: folder, group, dimensions, status, output, message. */
    public ResultsTable getManifest() {
        return manifest;
    }

    /** The saved copy of {@link #getManifest()}, {@code Batch_Manifest.csv} in the output directory. */
    public File getManifestFile() {
        return manifestFile;
    }
}
