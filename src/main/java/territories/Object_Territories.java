package territories;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import territories.api.AnalysisCancelledException;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;
import territories.api.ProgressMonitor;
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
import java.util.function.Function;

/** Fiji/ImageJ entry point for interactive, recorded macro, and headless use. */
public final class Object_Territories implements PlugIn {

    private static final String COMMAND_NAME = "Object Territories";
    private static final String NONE = "<none>";

    @Override
    public void run(String argument) {
        boolean headless = GraphicsEnvironment.isHeadless();
        RecordedRun recorded = null;
        try {
            String macroOptions = Macro.getOptions();
            if ((macroOptions == null || macroOptions.trim().isEmpty())
                    && argument != null && argument.indexOf('=') >= 0) {
                macroOptions = argument;
            }
            if (macroOptions != null && !macroOptions.trim().isEmpty()) {
                ObjectTerritoriesMacroOptions options = MacroOptionsParser.parse(macroOptions);
                IJ.resetEscape();
                execute(options, headless, Object_Territories::uniqueOpenImage,
                        new ImageJProgress(COMMAND_NAME));
                IJ.showProgress(1.0);
                return;
            }
            if (headless) {
                throw new IllegalArgumentException(
                        "headless execution requires explicit macro options and an output directory");
            }
            ObjectTerritoriesDialogModel model = showDialog();
            if (model == null) return;
            ObjectTerritoriesMacroOptions options = model.toMacroOptions();
            recorded = RecordedRun.record(
                    "run(\"" + COMMAND_NAME + "\", \"" + options.toMacroOptions() + "\");\n");
            IJ.resetEscape();
            execute(options, false, Object_Territories::uniqueOpenImage,
                    new ImageJProgress(COMMAND_NAME));
            IJ.showProgress(1.0);
        } catch (AnalysisCancelledException cancelled) {
            // Escape is a request, not an error: no dialog, no windows, no files,
            // and no macro line for a run that made nothing.
            forgetRecording(recorded);
            IJ.resetEscape();
            IJ.showProgress(1.0);
            IJ.showStatus(COMMAND_NAME + " cancelled");
            if (headless) throw HeadlessFailure.cancelled(COMMAND_NAME);
        } catch (Exception error) {
            forgetRecording(recorded);
            if (headless) throw HeadlessFailure.abort(COMMAND_NAME, error);
            // Bad input is explained in the message, so lead with that rather
            // than a stack dump. Not every IllegalArgumentException is bad
            // input though — some report an internal geometry fault — so keep
            // the trace in the Log window for anyone filing a report. A file
            // that cannot be read (IOException) is explained the same way.
            if (error instanceof IllegalArgumentException
                    || error instanceof java.io.IOException) {
                IJ.log("[Object Territories] " + stackTrace(error));
                IJ.error(COMMAND_NAME, error.getMessage());
            } else {
                IJ.handleException(error);
            }
        }
    }

    /**
     * A run that did not complete leaves nothing in the Macro Recorder: the
     * line this command recorded is taken back, and ImageJ's own pending
     * line (recorded when the command ends, for example after the dialog
     * rejected its values) is dropped.
     */
    private static void forgetRecording(RecordedRun recorded) {
        if (recorded != null) recorded.takeBack();
        if (Recorder.record) Recorder.setCommand(null);
    }

    private static String stackTrace(Throwable error) {
        java.io.StringWriter target = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(target));
        return target.toString();
    }

    /** Rejection when a run would compute results and keep none of them. */
    static final String NOTHING_KEPT =
            "nothing would be kept: result windows are hidden and no output directory is set; "
                    + "show the result windows or choose an auto-save directory";

    /** Parses macro options and runs them; the seam unit tests drive headlessly. */
    static void runMacro(
            String macroOptions,
            boolean headless,
            Function<String, ImagePlus> resolveTitle) throws Exception {
        execute(MacroOptionsParser.parse(macroOptions), headless, resolveTitle);
    }

    static void execute(
            ObjectTerritoriesMacroOptions options,
            boolean headless,
            Function<String, ImagePlus> resolveTitle) throws Exception {
        execute(options, headless, resolveTitle, ProgressMonitor.NONE);
    }

    static void execute(
            ObjectTerritoriesMacroOptions options,
            boolean headless,
            Function<String, ImagePlus> resolveTitle,
            ProgressMonitor monitor) throws Exception {
        if (headless && options.getOutputDirectory() == null) {
            throw new IllegalArgumentException(
                    "headless execution requires output=[directory]");
        }
        if (options.isHideResults() && options.getOutputDirectory() == null) {
            throw new IllegalArgumentException(NOTHING_KEPT);
        }
        List<ImagePlus> labels = new ArrayList<ImagePlus>();
        for (String title : options.getLabelTitles()) {
            ImagePlus image = resolveTitle.apply(title);
            if (image == null) {
                throw new IllegalArgumentException("label image is not open: " + title);
            }
            labels.add(image);
        }
        if (options.isThreeDimensional()) {
            execute3D(options, labels, headless, resolveTitle, monitor);
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
        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters, monitor);
        logWarnings(result.getWarnings());

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
            boolean headless,
            Function<String, ImagePlus> resolveTitle,
            ProgressMonitor monitor) throws Exception {
        ImagePlus mask = resolveTitle.apply(options.getRegionMaskTitle());
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
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(parameters, monitor);
        logWarnings(result.getWarnings());
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

    /**
     * Warnings such as "not spatially calibrated" change how every number is
     * read, so they are logged on every path, headless and hidden runs included.
     */
    private static void logWarnings(List<String> warnings) {
        for (String warning : warnings) IJ.log("[Object Territories] " + warning);
    }

    /**
     * Finds the one open image with this title. WindowManager.getImage(title)
     * silently returns the first of several same-titled images, so choosing
     * the second one in the dialog would analyse the wrong image.
     */
    static ImagePlus uniqueOpenImage(String title) {
        int[] identifiers = WindowManager.getIDList();
        ArrayList<ImagePlus> open = new ArrayList<ImagePlus>();
        if (identifiers != null) {
            for (int identifier : identifiers) {
                ImagePlus image = WindowManager.getImage(identifier);
                if (image != null) open.add(image);
            }
        }
        ImagePlus found = uniqueImage(title, open);
        return found != null ? found : WindowManager.getImage(title);
    }

    static ImagePlus uniqueImage(String title, List<ImagePlus> candidates) {
        ImagePlus found = null;
        int matches = 0;
        for (ImagePlus image : candidates) {
            if (image != null && title != null && title.equals(image.getTitle())) {
                matches++;
                found = image;
            }
        }
        if (matches > 1) {
            throw new IllegalArgumentException(
                    "two or more open images are titled '" + title
                            + "'; rename them so each title is unique");
        }
        return found;
    }

    private static ObjectTerritoriesDialogModel showDialog() {
        String[] imageChoices = openImageChoices();
        if (imageChoices.length == 1) {
            IJ.error(COMMAND_NAME,
                    "Open at least one label image (a 2D image or a 3D stack) first.");
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
        // A text field: a numeric one is read as a double and would silently
        // round seeds beyond 2^53 (the batch dialog does the same).
        dialog.addStringField(
                "Random seed", Long.toString(ObjectTerritoriesParameters.DEFAULT_SEED), 20);
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
        String seedText = dialog.getNextString();
        String outputDirectory = dialog.getNextString();
        boolean showResults = dialog.getNextBoolean();

        return dialogModel(
                titles, regionMaskTitle, regionPath, analysisMode, regionMode, edgePolicy,
                weighting, boundary, bandwidth, permutationValue, seedText,
                outputDirectory, showResults, Object_Territories::uniqueOpenImage);
    }

    /** Validates the dialog's choices; separate from the Swing code so tests can reach it. */
    static ObjectTerritoriesDialogModel dialogModel(
            List<String> titles,
            String regionMaskTitle,
            String regionPath,
            AnalysisMode analysisMode,
            RegionMode regionMode,
            EdgeCellPolicy edgePolicy,
            DensityWeightingSelection weighting,
            DensityBoundaryMode boundary,
            double bandwidth,
            double permutationValue,
            String seedText,
            String outputDirectory,
            boolean showResults,
            Function<String, ImagePlus> resolveTitle) {
        if (titles.isEmpty()) throw new IllegalArgumentException("select at least one label image");
        for (String title : titles) {
            if (resolveTitle.apply(title) == null) {
                throw new IllegalArgumentException("label image is not open: " + title);
            }
        }
        if (regionMaskTitle != null && resolveTitle.apply(regionMaskTitle) == null) {
            throw new IllegalArgumentException(
                    "3D region-mask image is not open: " + regionMaskTitle);
        }
        ImagePlus firstLabel = resolveTitle.apply(titles.get(0));
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
            if (regionPath == null || regionPath.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "choose a region ROI .roi or .zip file for 2D label images");
            }
        }
        if (!isWhole(permutationValue) || permutationValue < 1 || permutationValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("permutations must be a positive whole number");
        }
        if (permutationValue > ObjectTerritoriesParameters.MAX_PERMUTATIONS) {
            throw new IllegalArgumentException(
                    "permutations must be at most " + ObjectTerritoriesParameters.MAX_PERMUTATIONS);
        }
        long seed = parseSeed(seedText);
        if (!showResults && (outputDirectory == null || outputDirectory.trim().isEmpty())) {
            throw new IllegalArgumentException(NOTHING_KEPT);
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
                seed,
                outputDirectory,
                showResults);
    }

    static long parseSeed(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("random seed must be a whole number", error);
        }
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
