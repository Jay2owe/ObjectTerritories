package territories;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.RegionMode;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Entry-point error paths: every bad input ends in a clear message, never a stack trace. */
public class PluginCommandTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void badOptionValueIsExplained() {
        expect("permutations must be an integer",
                () -> Object_Territories.runMacro(
                        "label1=A regions=R permutations=many", false, images()));
    }

    @Test
    public void unknownOptionKeyIsExplained() {
        expect("unknown macro option: mystery",
                () -> Object_Territories.runMacro(
                        "label1=A regions=R mystery=1", false, images()));
    }

    @Test
    public void missingLabelImageIsExplained() {
        expect("label image is not open: Nuclei",
                () -> Object_Territories.runMacro(
                        "label1=Nuclei regions=R output=[" + temporary.getRoot() + "]",
                        false, images()));
    }

    @Test
    public void duplicateOpenTitlesAreRejectedInMacroAndDialogPaths() {
        List<ImagePlus> open = Arrays.asList(labels("Cells"), labels("Cells"), labels("Other"));
        Function<String, ImagePlus> resolver =
                title -> Object_Territories.uniqueImage(title, open);
        expect("two or more open images are titled 'Cells'",
                () -> Object_Territories.runMacro(
                        "label1=Cells regions=R output=[" + temporary.getRoot() + "]",
                        false, resolver));
        expect("two or more open images are titled 'Cells'",
                () -> dialog(Collections.singletonList("Cells"), "r.roi", true, "", resolver));
        // A unique title still resolves.
        assertTrue(Object_Territories.uniqueImage("Other", open) == open.get(2));
    }

    @Test
    public void hiddenResultsWithoutOutputAreRejectedBeforeAnyWork() {
        expect(Object_Territories.NOTHING_KEPT,
                () -> Object_Territories.runMacro(
                        "label1=A regions=R hide_results", false, images("A")));
        expect(Object_Territories.NOTHING_KEPT,
                () -> dialog(Collections.singletonList("A"), "r.roi", false, " ", images("A")));
    }

    @Test
    public void headlessRunWithoutOutputIsExplained() {
        expect("headless execution requires output=[directory]",
                () -> Object_Territories.runMacro("label1=A regions=R", true, images("A")));
    }

    @Test
    public void emptyRoiPathInTheDialogIsExplained() {
        expect("choose a region ROI .roi or .zip file for 2D label images",
                () -> dialog(Collections.singletonList("A"), "", true, "", images("A")));
        expect("choose a region ROI .roi or .zip file for 2D label images",
                () -> dialog(Collections.singletonList("A"), "   ", true, "", images("A")));
    }

    @Test
    public void unrecordableTitleIsBadInputNotAnInternalFault() {
        expect("rename the image; ImageJ macros cannot record titles containing [ ] or quotes",
                () -> dialog(Collections.singletonList("Cells [1]"), "r.roi", true, "",
                        images("Cells [1]")));
    }

    @Test
    public void dialogRejectsPermutationsAboveTheCap() {
        expect("permutations must be at most 1000000",
                () -> Object_Territories.dialogModel(
                        Collections.singletonList("A"), null, "r.roi",
                        AnalysisMode.BOTH, RegionMode.INDEPENDENT,
                        EdgeCellPolicy.INCLUDE_FLAGGED, DensityWeightingSelection.BOTH,
                        DensityBoundaryMode.CORRECTED, 0.0, 1000001, "1", "", true,
                        images("A")));
    }

    /** A numeric field read seeds as doubles: 9007199254740993 ran as ...992. */
    @Test
    public void dialogKeepsLargeSeedsExactly() {
        String options = Object_Territories.dialogModel(
                Collections.singletonList("A"), null, "r.roi",
                AnalysisMode.BOTH, RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED, DensityWeightingSelection.BOTH,
                DensityBoundaryMode.CORRECTED, 0.0, 10, " 9007199254740993 ", "", true,
                images("A")).toMacroOptionString();
        assertTrue(options, options.contains("seed=9007199254740993"));
        expect("random seed must be a whole number",
                () -> Object_Territories.dialogModel(
                        Collections.singletonList("A"), null, "r.roi",
                        AnalysisMode.BOTH, RegionMode.INDEPENDENT,
                        EdgeCellPolicy.INCLUDE_FLAGGED, DensityWeightingSelection.BOTH,
                        DensityBoundaryMode.CORRECTED, 0.0, 10, "1.5", "", true,
                        images("A")));
    }

    /**
     * Rethrowing in headless Fiji printed a full stack trace to stdout and let
     * the calling macro continue; the failure must instead be ImageJ's silent
     * "Macro canceled" signal, after one logged ERROR line.
     */
    @Test
    public void headlessFailureEndsTheMacroWithImageJsSilentCancelSignal() {
        RuntimeException bad = HeadlessFailure.abort(
                "Object Territories", new IllegalArgumentException("permutations must be at least 1"));
        assertTrue(ij.Macro.MACRO_CANCELED.equals(bad.getMessage()));
        RuntimeException stopped = HeadlessFailure.cancelled("Object Territories");
        assertTrue(ij.Macro.MACRO_CANCELED.equals(stopped.getMessage()));
        assertTrue("NullPointerException".equals(
                HeadlessFailure.message(new NullPointerException())));
    }

    @Test
    public void validHeadlessMacroRunSavesResults() throws Exception {
        File regions = temporary.newFile("field.roi");
        Roi roi = new Roi(0, 0, 16, 16);
        roi.setName("Field");
        new RoiEncoder(regions.getAbsolutePath()).write(roi);
        File output = temporary.newFolder("out");

        Object_Territories.runMacro(
                "label1=A regions=[" + regions.getAbsolutePath() + "] mode=territories "
                        + "permutations=5 output=[" + output.getAbsolutePath() + "]",
                true, images("A"));

        assertTrue(new File(new File(output, "Objects"), "Field_Objects.csv").isFile());
    }

    // ------------------------------------------------------------------

    private static Object dialog(
            List<String> titles,
            String regionPath,
            boolean showResults,
            String output,
            Function<String, ImagePlus> resolver) {
        return Object_Territories.dialogModel(
                titles, null, regionPath, AnalysisMode.BOTH, RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED, DensityWeightingSelection.BOTH,
                DensityBoundaryMode.CORRECTED, 0.0, 10, "1", output, showResults, resolver);
    }

    private static Function<String, ImagePlus> images(String... titles) {
        Map<String, ImagePlus> open = new HashMap<String, ImagePlus>();
        for (String title : titles) open.put(title, labels(title));
        return open::get;
    }

    private static ImagePlus labels(String title) {
        ShortProcessor pixels = new ShortProcessor(16, 16);
        pixels.set(3, 3, 1);
        pixels.set(11, 12, 2);
        return new ImagePlus(title, pixels);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void expect(String fragment, Action action) {
        try {
            action.run();
            fail("expected a rejection containing: " + fragment);
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(fragment));
        } catch (Exception error) {
            throw new AssertionError("expected IllegalArgumentException, got " + error, error);
        }
    }
}
