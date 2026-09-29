package territories;

import ij.IJ;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritoriesParameters;
import territories.api.RegionMode;
import territories.batch.ObjectTerritoriesBatchParameters;
import territories.batch.ObjectTerritoriesBatchResult;
import territories.batch.ObjectTerritoriesBatchRunner;

import java.awt.GraphicsEnvironment;
import java.io.File;

/** Fiji entry point for the regex-grouped 2D and 3D folder batch. */
public final class Object_Territories_Batch implements PlugIn {

    private static final String COMMAND_NAME = "Object Territories Batch";

    /**
     * Case-insensitive so that {@code .TIF} files, common from microscope
     * software, are found. Recorded macros keep whatever regex they recorded.
     */
    public static final String DEFAULT_REGEX = "(?i)(.+)_([^_]+)\\.(?:tif|tiff)$";

    @Override
    public void run(String argument) {
        boolean headless = GraphicsEnvironment.isHeadless();
        try {
            boolean interactive = Macro.getOptions() == null && !headless;
            Settings settings = Settings.defaults();
            while (true) {
                settings = showSettings(settings);
                if (settings == null) return;
                ObjectTerritoriesBatchParameters parameters = settings.toParameters();
                String preview = ObjectTerritoriesBatchRunner.preview(parameters);
                if (interactive) {
                    GenericDialog confirmation = new GenericDialog(COMMAND_NAME + " Preview");
                    confirmation.addMessage(
                            "Review the groups below. Groups with more than five label types "
                                    + "are skipped.\nPress Esc during the run to stop after "
                                    + "the current step.");
                    confirmation.addTextAreas(preview, null, 24, 80);
                    confirmation.enableYesNoCancel("Run batch", "Back");
                    confirmation.showDialog();
                    if (confirmation.wasCanceled()) return;
                    if (!confirmation.wasOKed()) {
                        // Back: reopen the settings with the values just entered,
                        // and forget the options recorded for the abandoned attempt.
                        if (Recorder.record) Recorder.resetCommandOptions();
                        continue;
                    }
                } else {
                    IJ.log(preview);
                }
                runBatch(parameters, settings.showManifest, headless);
                return;
            }
        } catch (Exception error) {
            if (headless) {
                IJ.log("[" + COMMAND_NAME + "] ERROR: " + error.getMessage());
                throw error instanceof RuntimeException
                        ? (RuntimeException) error : new IllegalStateException(error);
            }
            // Bad input is explained in its message; the trace stays in the Log
            // for anyone filing a report, but no stack-trace window opens.
            if (error instanceof IllegalArgumentException) {
                IJ.log("[" + COMMAND_NAME + "] " + stackTrace(error));
                IJ.error(COMMAND_NAME, error.getMessage());
            } else {
                IJ.handleException(error);
            }
        }
    }

    private static void runBatch(
            ObjectTerritoriesBatchParameters parameters,
            boolean showManifest,
            boolean headless) throws Exception {
        IJ.resetEscape();
        ObjectTerritoriesBatchResult result;
        try {
            result = ObjectTerritoriesBatchRunner.run(
                    parameters, new ImageJProgress(COMMAND_NAME));
        } finally {
            IJ.showProgress(1.0);
        }
        String summary = "Object Territories batch "
                + (result.getCancelledGroups() > 0 ? "cancelled: " : "complete: ")
                + result.getProcessedGroups() + " processed, "
                + result.getSkippedGroups() + " skipped, "
                + result.getErrorGroups() + " errors"
                + (result.getCancelledGroups() > 0
                        ? ", " + result.getCancelledGroups() + " cancelled" : "")
                + ". Manifest: " + result.getManifestFile().getAbsolutePath();
        IJ.log(summary);
        if (result.getCancelledGroups() > 0) {
            IJ.resetEscape();
            IJ.showStatus(COMMAND_NAME + " cancelled");
        }
        if (showManifest && !headless) {
            result.getManifest().show("Object Territories Batch Manifest");
        }
    }

    /** Shows the settings dialog pre-filled from {@code previous}; null when cancelled. */
    private static Settings showSettings(Settings previous) {
        GenericDialog dialog = new GenericDialog(COMMAND_NAME);
        dialog.addMessage(
                "Group 1-5 matching label images per sample.\n"
                        + "2D: the selected region ROI set is applied to every sample.\n"
                        + "3D: each group also holds one region-mask stack, the file whose\n"
                        + "label type equals the region-mask type (the ROI field is ignored).");
        dialog.addDirectoryField("Input_folder", previous.input);
        dialog.addStringField("Filename_regex", previous.regex, 48);
        dialog.addNumericField("Label_type_capture_group", previous.typeGroup, 0);
        dialog.addCheckbox("Recursive", previous.recursive);
        dialog.addChoice("Dimensions", new String[]{"2D", "3D"},
                previous.threeDimensional ? "3D" : "2D");
        dialog.addStringField("Region_mask_type", previous.regionMaskType, 12);
        dialog.addFileField("Region_ROI_file_or_zip", previous.regionPath, 48);
        dialog.addChoice("Analysis", names(AnalysisMode.values()), previous.analysisMode.name());
        dialog.addChoice(
                "Multiple_region_ROIs", names(RegionMode.values()), previous.regionMode.name());
        dialog.addChoice(
                "Edge_cells", names(EdgeCellPolicy.values()), previous.edgePolicy.name());
        dialog.addChoice(
                "Density_weighting", names(DensityWeightingSelection.values()),
                previous.weighting.name());
        dialog.addChoice(
                "Density_boundary", names(DensityBoundaryMode.values()),
                previous.boundary.name());
        dialog.addNumericField("Bandwidth_0_is_automatic", previous.bandwidth, 3);
        dialog.addNumericField("Permutations", previous.permutations, 0);
        dialog.addStringField("Random_seed", previous.seed, 18);
        dialog.addDirectoryField("Output_directory", previous.output);
        dialog.addCheckbox("Show_manifest", previous.showManifest);
        dialog.showDialog();
        if (dialog.wasCanceled()) return null;

        Settings next = new Settings();
        next.input = dialog.getNextString().trim();
        next.regex = dialog.getNextString();
        next.typeGroup = dialog.getNextNumber();
        next.recursive = dialog.getNextBoolean();
        next.threeDimensional = "3D".equals(dialog.getNextChoice());
        next.regionMaskType = dialog.getNextString().trim();
        next.regionPath = dialog.getNextString().trim();
        next.analysisMode = AnalysisMode.valueOf(dialog.getNextChoice());
        next.regionMode = RegionMode.valueOf(dialog.getNextChoice());
        next.edgePolicy = EdgeCellPolicy.valueOf(dialog.getNextChoice());
        next.weighting = DensityWeightingSelection.valueOf(dialog.getNextChoice());
        next.boundary = DensityBoundaryMode.valueOf(dialog.getNextChoice());
        next.bandwidth = dialog.getNextNumber();
        next.permutations = dialog.getNextNumber();
        next.seed = dialog.getNextString().trim();
        next.output = dialog.getNextString().trim();
        next.showManifest = dialog.getNextBoolean();
        return next;
    }

    /** Everything the settings dialog holds, so Back can reopen it unchanged. */
    static final class Settings {
        String input;
        String regex;
        double typeGroup;
        boolean recursive;
        boolean threeDimensional;
        String regionMaskType;
        String regionPath;
        AnalysisMode analysisMode;
        RegionMode regionMode;
        EdgeCellPolicy edgePolicy;
        DensityWeightingSelection weighting;
        DensityBoundaryMode boundary;
        double bandwidth;
        double permutations;
        String seed;
        String output;
        boolean showManifest;

        static Settings defaults() {
            Settings settings = new Settings();
            settings.input = defaultDirectory();
            settings.regex = DEFAULT_REGEX;
            settings.typeGroup = 2;
            settings.recursive = true;
            settings.threeDimensional = false;
            settings.regionMaskType = ObjectTerritoriesBatchParameters.DEFAULT_REGION_MASK_TYPE;
            settings.regionPath = "";
            settings.analysisMode = AnalysisMode.BOTH;
            settings.regionMode = RegionMode.INDEPENDENT;
            settings.edgePolicy = EdgeCellPolicy.INCLUDE_FLAGGED;
            settings.weighting = DensityWeightingSelection.BOTH;
            settings.boundary = DensityBoundaryMode.CORRECTED;
            settings.bandwidth = 0.0;
            settings.permutations = ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS;
            settings.seed = Long.toString(ObjectTerritoriesParameters.DEFAULT_SEED);
            settings.output = defaultOutputDirectory();
            settings.showManifest = true;
            return settings;
        }

        ObjectTerritoriesBatchParameters toParameters() {
            File inputFolder = new File(input);
            File outputFolder = new File(output);
            int group = wholeNumber(typeGroup, "Label type capture group", 1);
            ObjectTerritoriesBatchParameters.Builder builder = threeDimensional
                    ? ObjectTerritoriesBatchParameters.builder3D(
                            inputFolder, regex, group, regionMaskType, outputFolder)
                    : ObjectTerritoriesBatchParameters.builder(
                            inputFolder, regex, group, new File(regionPath), outputFolder);
            return builder
                    .recursive(recursive)
                    .analysisMode(analysisMode)
                    .regionMode(regionMode)
                    .edgeCellPolicy(edgePolicy)
                    .densityWeightingSelection(weighting)
                    .densityBoundaryMode(boundary)
                    .bandwidthMicrons(bandwidth)
                    .permutations(wholeNumber(permutations, "Permutations", 1))
                    .seed(wholeLong(seed, "Random seed"))
                    .build();
        }
    }

    private static String stackTrace(Throwable error) {
        java.io.StringWriter target = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(target));
        return target.toString();
    }

    private static String[] names(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name();
        }
        return names;
    }

    static int wholeNumber(double value, String label, int minimum) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < minimum || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    label + " must be a whole number of at least " + minimum + ".");
        }
        return (int) value;
    }

    static long wholeLong(String value, String label) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be a whole number.", error);
        }
    }

    private static String defaultDirectory() {
        String home = IJ.getDirectory("home");
        return home == null ? "" : home;
    }

    private static String defaultOutputDirectory() {
        String home = defaultDirectory();
        return home.isEmpty() ? "" : new File(home, "Object Territories Batch").getPath();
    }
}
