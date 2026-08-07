package territories.batch;

import ij.measure.ResultsTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Summary and manifest returned by a completed folder batch. */
public final class ObjectTerritoriesBatchResult {

    private final List<String> processedSamples;
    private final int skippedGroups;
    private final int errorGroups;
    private final ResultsTable manifest;

    ObjectTerritoriesBatchResult(
            List<String> processedSamples,
            int skippedGroups,
            int errorGroups,
            ResultsTable manifest) {
        this.processedSamples = Collections.unmodifiableList(
                new ArrayList<String>(processedSamples));
        this.skippedGroups = skippedGroups;
        this.errorGroups = errorGroups;
        this.manifest = manifest;
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

    public ResultsTable getManifest() {
        return manifest;
    }
}
