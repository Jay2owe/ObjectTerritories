package territories.ui;

import org.junit.Test;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.RegionMode;
import territories.macro.MacroOptionsParser;
import territories.macro.ObjectTerritoriesMacroOptions;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObjectTerritoriesDialogModelTest {

    @Test
    public void dialogStateRoundTripsThroughRecorderGrammar() {
        ObjectTerritoriesDialogModel model = new ObjectTerritoriesDialogModel(
                Arrays.asList("A labels", "B labels"),
                "C:\\regions\\brain.zip",
                AnalysisMode.BOTH,
                RegionMode.INDEPENDENT,
                EdgeCellPolicy.INCLUDE_FLAGGED,
                DensityWeightingSelection.BOTH,
                DensityBoundaryMode.CORRECTED,
                0.0,
                1000,
                88L,
                "C:\\results",
                false);

        ObjectTerritoriesMacroOptions parsed =
                MacroOptionsParser.parse(model.toMacroOptionString());
        assertEquals(Arrays.asList("A labels", "B labels"), parsed.getLabelTitles());
        assertEquals(88L, parsed.getSeed());
        assertTrue(parsed.isHideResults());
        assertEquals("C:/results", parsed.getOutputDirectory());
    }
}
