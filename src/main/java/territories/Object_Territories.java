package territories;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.RegionMode;
import territories.io.RegionRoiLoader;
import territories.macro.MacroOptionsParser;
import territories.macro.ObjectTerritoriesMacroOptions;
import territories.output.ResultExporter;
import territories.output.ResultExporter3D;
import territories.output.ResultPresenter;
import territories.output.ResultPresenter3D;
import territories.ui.ObjectTerritoriesDialogModel;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Fiji/ImageJ entry point for interactive, recorded macro, and headless use. */
public final class Object_Territories implements PlugIn {

    private static final String COMMAND_NAME = "Object Territories";
    private static final String NONE = "<none>";

    @Override
    public void run(String argument) {
        boolean headless = GraphicsEnvironment.isHeadless();
        try {
            String macroOptions = Macro.getOptions();
            if ((macroOptions == null || macroOptions.trim().isEmpty())
                    && argument != null && argument.indexOf('=') >= 0) {
                macroOptions = argument;
            }
            if (macroOptions != null && !macroOptions.trim().isEmpty()) {
                execute(MacroOptionsParser.parse(macroOptions), headless);
                return;
            }
            if (headless) {
                throw new IllegalArgumentException(
                        "headless execution requires explicit macro options and an output directory");
            }
            ObjectTerritoriesDialogModel model = showDialog();
            if (model == null) return;
            ObjectTerritoriesMacroOptions options = model.toMacroOptions();
            if (Recorder.record) {
                Recorder.setCommand(null);
                Recorder.recordString(
                        "run(\"" + COMMAND_NAME + "\", \""
                                + options.toMacroOptions() + "\");\n");
            }
            execute(options, false);
        } catch (Exception error) {
            if (headless) {
                IJ.log("[Object Territories] ERROR: " + error.getMessage());
                throw error instanceof RuntimeException
                        ? (RuntimeException) error
                        : new IllegalStateException(error);
            }
            IJ.handleException(error);
        }
    }

    private static void execute(ObjectTerritoriesMacroOptions options, boolean headless)
            throws Exception {
        if (headless && options.getOutputDirectory() == null) {
            throw new IllegalArgumentException(
                    "headless execution requires output=[directory]");
        }
        List<ImagePlus> labels = new ArrayList<ImagePlus>();
        for (String title : options.getLabelTitles()) {
            ImagePlus image = WindowManager.getImage(title);
            if (image == null) {
                throw new IllegalArgumentException("label image is not open: " + title);
            }
            labels.add(image);
        }
        if (options.isThreeDimensional()) {
            execute3D(options, labels, headless);
            return;
        }

        ObjectTerritoriesParameters parameters = ObjectTerritoriesParameters.builder()
                .labelImages(labels)
                .regions(RegionRoiLoader.load(new File(options.getRoiZipPath())))
                .analysisMode(options.getAnalysisMode())
                .regionMode(options.getRegionMode())
                .edgeCellPolicy(options.getEdgeCellPolicy())
                .densityWeightingSelection(options.getDensityWeightingSelection())
                .densityBoundaryMode(options.getDensityBoundaryMode())
                .bandwidthMicrons(options.getBandwidthMicrons())
                .permutations(options.getPermutations())
                .seed(options.getSeed())
                .build();
        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters);

        boolean keepImages = !headless && !options.isHideResults();
        try {
            if (options.getOutputDirectory() != null) {
                ResultExporter.save(result, new File(options.getOutputDirectory()));
            }
            if (keepImages) ResultPresenter.show(result, labels.get(0));
        } finally {
            if (!keepImages) result.closeDensityImages();
        }
    }

    private static void execute3D(
            ObjectTerritoriesMacroOptions options,
            List<ImagePlus> labels,
            boolean headless) throws Exception {
        ImagePlus mask = WindowManager.getImage(options.getRegionMaskTitle());
        if (mask == null) {
            throw new IllegalArgumentException(
                    "3D region-mask image is not open: " + options.getRegionMaskTitle());
        }
        ObjectTerritoriesParameters3D parameters =
                ObjectTerritoriesParameters3D.builder()
                        .labelImages(labels)
                        .regionMask(mask)
                        .analysisMode(options.getAnalysisMode())
                        .regionMode(options.getRegionMode())
                        .edgeCellPolicy(options.getEdgeCellPolicy())
                        .densityWeightingSelection(options.getDensityWeightingSelection())
                        .densityBoundaryMode(options.getDensityBoundaryMode())
                        .bandwidth(options.getBandwidthMicrons())
                        .permutations(options.getPermutations())
                        .seed(options.getSeed())
                        .build();
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(parameters);
        boolean keepImages = !headless && !options.isHideResults();
        try {
            if (options.getOutputDirectory() != null) {
                ResultExporter3D.save(result, new File(options.getOutputDirectory()));
            }
            if (keepImages) ResultPresenter3D.show(result);
        } finally {
            if (!keepImages) result.closeGeneratedImages();
        }
    }

    private static ObjectTerritoriesDialogModel showDialog() {
        String[] imageChoices = openImageChoices();
        if (imageChoices.length == 1) {
            IJ.error(COMMAND_NAME, "Open at least one 2D label image first.");
            return null;
        }
        ImagePlus current = WindowManager.getCurrentImage();
        String firstDefault = current == null ? imageChoices[1] : current.getTitle();

        GenericDialog dialog = new GenericDialog(COMMAND_NAME);
        dialog.addMessage("Select 1-5 label images. Each image defines one object type.");
        for (int i = 0; i < 5; i++) {
            dialog.addChoice(
                    "Label image " + (i + 1),
                    imageChoices,
                    i == 0 ? firstDefault : NONE);
        }
        dialog.addChoice("3D region mask (for stacks)", imageChoices, NONE);
        dialog.addFileField("2D region ROI .zip or .roi", "");
        dialog.addChoice(
                "Analysis",
                new String[] {"Territories and density", "Territories only", "Density only"},
                "Territories and density");
        dialog.addChoice(
                "Multiple region ROIs",
                new String[] {"Analyse independently", "Combine as one union"},
                "Analyse independently");
        dialog.addChoice(
                "Edge Voronoi cells",
                new String[] {"Include and flag", "Exclude from summaries"},
                "Include and flag");
        dialog.addChoice(
                "Density maps",
                new String[] {"Object count and object size", "Object count only", "Object size only"},
                "Object count and object size");
        dialog.addChoice(
                "Density boundary",
                new String[] {"Corrected", "Clipped (biased near edges)"},
                "Corrected");
        dialog.addNumericField("Bandwidth (0 = automatic)", 0.0, 3);
        dialog.addNumericField(
                "Permutations", ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS, 0);
        dialog.addNumericField("Random seed", ObjectTerritoriesParameters.DEFAULT_SEED, 0);
        dialog.addDirectoryField("Auto-save directory (optional)", "");
        dialog.addCheckbox("Show result windows", true);
        dialog.showDialog();
        if (dialog.wasCanceled()) return null;

        ArrayList<String> titles = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            String title = dialog.getNextChoice();
            if (!NONE.equals(title)) titles.add(title);
        }
        String regionMaskTitle = dialog.getNextChoice();
        if (NONE.equals(regionMaskTitle)) regionMaskTitle = null;
        String regionPath = dialog.getNextString();
        AnalysisMode analysisMode = analysisMode(dialog.getNextChoiceIndex());
        RegionMode regionMode = dialog.getNextChoiceIndex() == 0
                ? RegionMode.INDEPENDENT : RegionMode.UNION;
        EdgeCellPolicy edgePolicy = dialog.getNextChoiceIndex() == 0
                ? EdgeCellPolicy.INCLUDE_FLAGGED : EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES;
        DensityWeightingSelection weighting = weighting(dialog.getNextChoiceIndex());
        DensityBoundaryMode boundary = dialog.getNextChoiceIndex() == 0
                ? DensityBoundaryMode.CORRECTED : DensityBoundaryMode.CLIPPED;
        double bandwidth = dialog.getNextNumber();
        double permutationValue = dialog.getNextNumber();
        double seedValue = dialog.getNextNumber();
        String outputDirectory = dialog.getNextString();
        boolean showResults = dialog.getNextBoolean();

        if (titles.isEmpty()) throw new IllegalArgumentException("select at least one label image");
        ImagePlus firstLabel = WindowManager.getImage(titles.get(0));
        boolean threeDimensional = firstLabel != null && firstLabel.getStackSize() > 1;
        if (threeDimensional && regionMaskTitle == null) {
            throw new IllegalArgumentException(
                    "3D label stacks require an open 3D region-mask image");
        }
        if (!threeDimensional && regionMaskTitle != null) {
            throw new IllegalArgumentException(
                    "a 3D region mask can only be used with 3D label stacks");
        }
        if (threeDimensional) {
            regionPath = null;
        } else {
            regionMaskTitle = null;
        }
        if (!isWhole(permutationValue) || permutationValue < 1 || permutationValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("permutations must be a positive whole number");
        }
        if (!isWhole(seedValue) || seedValue < Long.MIN_VALUE || seedValue > Long.MAX_VALUE) {
            throw new IllegalArgumentException("random seed must be a whole number");
        }
        return new ObjectTerritoriesDialogModel(
                titles,
                regionPath,
                regionMaskTitle,
                analysisMode,
                regionMode,
                edgePolicy,
                weighting,
                boundary,
                bandwidth,
                (int) permutationValue,
                (long) seedValue,
                outputDirectory,
                showResults);
    }

    private static String[] openImageChoices() {
        int[] identifiers = WindowManager.getIDList();
        if (identifiers == null) return new String[] {NONE};
        String[] result = new String[identifiers.length + 1];
        result[0] = NONE;
        for (int i = 0; i < identifiers.length; i++) {
            ImagePlus image = WindowManager.getImage(identifiers[i]);
            result[i + 1] = image == null ? "" : image.getTitle();
        }
        return result;
    }

    private static AnalysisMode analysisMode(int index) {
        if (index == 1) return AnalysisMode.TERRITORIES;
        if (index == 2) return AnalysisMode.DENSITY;
        return AnalysisMode.BOTH;
    }

    private static DensityWeightingSelection weighting(int index) {
        if (index == 1) return DensityWeightingSelection.OBJECT_COUNT;
        if (index == 2) return DensityWeightingSelection.OBJECT_SIZE;
        return DensityWeightingSelection.BOTH;
    }

    private static boolean isWhole(double value) {
        return Double.isFinite(value) && value == Math.rint(value);
    }
}
