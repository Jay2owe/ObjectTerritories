package territories;

import ij.IJ;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
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

    @Override
    public void run(String argument) {
        try {
            GenericDialog dialog = new GenericDialog(COMMAND_NAME);
            dialog.addMessage(
                    "Group 1-5 matching label images per sample.\n"
                            + "2D: the selected region ROI set is applied to every sample.\n"
                            + "3D: each group also holds one region-mask stack, the file whose\n"
                            + "label type equals the region-mask type (the ROI field is ignored).");
            dialog.addDirectoryField("Input_folder", defaultDirectory());
            dialog.addStringField(
                    "Filename_regex",
                    "(.+)_([^_]+)\\.(?:tif|tiff)$",
                    48);
            dialog.addNumericField("Label_type_capture_group", 2, 0);
            dialog.addCheckbox("Recursive", true);
            dialog.addChoice("Dimensions", new String[]{"2D", "3D"}, "2D");
            dialog.addStringField(
                    "Region_mask_type",
                    ObjectTerritoriesBatchParameters.DEFAULT_REGION_MASK_TYPE,
                    12);
            dialog.addFileField("Region_ROI_file_or_zip", "", 48);
            dialog.addChoice("Analysis", names(AnalysisMode.values()), AnalysisMode.BOTH.name());
            dialog.addChoice(
                    "Multiple_region_ROIs", names(RegionMode.values()), RegionMode.INDEPENDENT.name());
            dialog.addChoice(
                    "Edge_cells", names(EdgeCellPolicy.values()),
                    EdgeCellPolicy.INCLUDE_FLAGGED.name());
            dialog.addChoice(
                    "Density_weighting", names(DensityWeightingSelection.values()),
                    DensityWeightingSelection.BOTH.name());
            dialog.addChoice(
                    "Density_boundary", names(DensityBoundaryMode.values()),
                    DensityBoundaryMode.CORRECTED.name());
            dialog.addNumericField("Bandwidth_0_is_automatic", 0.0, 3);
            dialog.addNumericField(
                    "Permutations", ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS, 0);
            dialog.addStringField(
                    "Random_seed", Long.toString(ObjectTerritoriesParameters.DEFAULT_SEED), 18);
            dialog.addDirectoryField("Output_directory", defaultOutputDirectory());
            dialog.addCheckbox("Show_manifest", true);
            dialog.showDialog();
            if (dialog.wasCanceled()) return;

            File input = new File(dialog.getNextString().trim());
            String regex = dialog.getNextString();
            int typeGroup = wholeNumber(
                    dialog.getNextNumber(), "Label type capture group", 1);
            boolean recursive = dialog.getNextBoolean();
            boolean threeDimensional = "3D".equals(dialog.getNextChoice());
            String regionMaskType = dialog.getNextString().trim();
            String regionPath = dialog.getNextString().trim();
            AnalysisMode analysisMode = AnalysisMode.valueOf(dialog.getNextChoice());
            RegionMode regionMode = RegionMode.valueOf(dialog.getNextChoice());
            EdgeCellPolicy edgePolicy = EdgeCellPolicy.valueOf(dialog.getNextChoice());
            DensityWeightingSelection weighting =
                    DensityWeightingSelection.valueOf(dialog.getNextChoice());
            DensityBoundaryMode boundary = DensityBoundaryMode.valueOf(dialog.getNextChoice());
            double bandwidth = dialog.getNextNumber();
            int permutations = wholeNumber(dialog.getNextNumber(), "Permutations", 1);
            long seed = wholeLong(dialog.getNextString(), "Random seed");
            File output = new File(dialog.getNextString().trim());
            boolean showManifest = dialog.getNextBoolean();

            ObjectTerritoriesBatchParameters.Builder builder = threeDimensional
                    ? ObjectTerritoriesBatchParameters.builder3D(
                            input, regex, typeGroup, regionMaskType, output)
                    : ObjectTerritoriesBatchParameters.builder(
                            input, regex, typeGroup, new File(regionPath), output);
            ObjectTerritoriesBatchParameters parameters = builder
                            .recursive(recursive)
                            .analysisMode(analysisMode)
                            .regionMode(regionMode)
                            .edgeCellPolicy(edgePolicy)
                            .densityWeightingSelection(weighting)
                            .densityBoundaryMode(boundary)
                            .bandwidthMicrons(bandwidth)
                            .permutations(permutations)
                            .seed(seed)
                            .build();

            String preview = ObjectTerritoriesBatchRunner.preview(parameters);
            if (Macro.getOptions() == null && !GraphicsEnvironment.isHeadless()) {
                GenericDialog confirmation = new GenericDialog(COMMAND_NAME + " Preview");
                confirmation.addMessage(
                        "Review the label groups below. Groups containing more than five files are skipped.");
                confirmation.addTextAreas(preview, null, 24, 80);
                confirmation.enableYesNoCancel("Run batch", "Back");
                confirmation.showDialog();
                if (confirmation.wasCanceled() || !confirmation.wasOKed()) return;
            } else {
                IJ.log(preview);
            }

            ObjectTerritoriesBatchResult result = ObjectTerritoriesBatchRunner.run(parameters);
            IJ.log("Object Territories batch complete: "
                    + result.getProcessedGroups() + " processed, "
                    + result.getSkippedGroups() + " skipped, "
                    + result.getErrorGroups() + " errors. Manifest: "
                    + result.getManifestFile().getAbsolutePath());
            if (showManifest && !GraphicsEnvironment.isHeadless()) {
                result.getManifest().show("Object Territories Batch Manifest");
            }
        } catch (Exception error) {
            if (GraphicsEnvironment.isHeadless()) {
                IJ.log("[" + COMMAND_NAME + "] ERROR: " + error.getMessage());
                throw error instanceof RuntimeException
                        ? (RuntimeException) error : new IllegalStateException(error);
            }
            IJ.handleException(error);
        }
    }

    private static String[] names(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name();
        }
        return names;
    }

    private static int wholeNumber(double value, String label, int minimum) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < minimum || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    label + " must be a whole number of at least " + minimum + ".");
        }
        return (int) value;
    }

    private static long wholeLong(String value, String label) {
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
