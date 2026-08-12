package territories.macro;

import org.junit.Test;
import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.RegionMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MacroOptionsParserTest {

    @Test
    public void parsesMinimalOptionsWithSharedDefaults() {
        ObjectTerritoriesMacroOptions parsed = MacroOptionsParser.parse(
                "label1=[Cells A] regions=[C:/data/regions.zip]");

        assertEquals(1, parsed.getLabelTitles().size());
        assertEquals("Cells A", parsed.getLabelTitles().get(0));
        assertEquals(AnalysisMode.BOTH, parsed.getAnalysisMode());
        assertEquals(RegionMode.INDEPENDENT, parsed.getRegionMode());
        assertEquals(EdgeCellPolicy.INCLUDE_FLAGGED, parsed.getEdgeCellPolicy());
        assertEquals(DensityWeightingSelection.BOTH, parsed.getDensityWeightingSelection());
        assertEquals(DensityBoundaryMode.CORRECTED, parsed.getDensityBoundaryMode());
        assertEquals(0.0, parsed.getBandwidthMicrons(), 0.0);
        assertEquals(1000, parsed.getPermutations());
        assertEquals(12345L, parsed.getSeed());
        assertEquals(null, parsed.getOutputDirectory());
        assertTrue(!parsed.isHideResults());
    }

    @Test
    public void parsesAllScientificSettings() {
        ObjectTerritoriesMacroOptions parsed = MacroOptionsParser.parse(
                "mode=territories label1=A label2=B regions=regions.zip "
                + "region_mode=union edge_cells=exclude_from_summaries "
                + "density_weighting=object_size boundary=clipped bandwidth=12.5 "
                + "permutations=2500 seed=-9 output=[C:/results folder] hide_results");

        assertEquals(2, parsed.getLabelTitles().size());
        assertEquals(AnalysisMode.TERRITORIES, parsed.getAnalysisMode());
        assertEquals(RegionMode.UNION, parsed.getRegionMode());
        assertEquals(EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES, parsed.getEdgeCellPolicy());
        assertEquals(
                DensityWeightingSelection.OBJECT_SIZE,
                parsed.getDensityWeightingSelection());
        assertEquals(DensityBoundaryMode.CLIPPED, parsed.getDensityBoundaryMode());
        assertEquals(12.5, parsed.getBandwidthMicrons(), 0.0);
        assertEquals(2500, parsed.getPermutations());
        assertEquals(-9L, parsed.getSeed());
        assertEquals("C:/results folder", parsed.getOutputDirectory());
        assertTrue(parsed.isHideResults());
    }

    /**
     * ImageJ records a ticked checkbox as a bare key, but a user writing the
     * value out explicitly must get what they asked for.
     */
    @Test
    public void honoursExplicitCheckboxValues() {
        assertTrue(MacroOptionsParser.parse(
                "label1=A regions=r.zip hide_results").isHideResults());
        assertTrue(MacroOptionsParser.parse(
                "label1=A regions=r.zip hide_results=true").isHideResults());
        assertTrue(!MacroOptionsParser.parse(
                "label1=A regions=r.zip hide_results=false").isHideResults());
        assertTrue(!MacroOptionsParser.parse(
                "label1=A regions=r.zip hide_results=no").isHideResults());
        assertTrue(!MacroOptionsParser.parse(
                "label1=A regions=r.zip").isHideResults());
        try {
            MacroOptionsParser.parse("label1=A regions=r.zip hide_results=maybe");
            fail("expected an unparseable checkbox value to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("hide_results"));
        }
    }

    @Test
    public void parsesGenuineThreeDimensionalMaskInput() {
        ObjectTerritoriesMacroOptions parsed = MacroOptionsParser.parse(
                "label1=[Cells 3D] region_mask=[Brain mask] hide_results");

        assertTrue(parsed.isThreeDimensional());
        assertEquals("Brain mask", parsed.getRegionMaskTitle());
        assertEquals(null, parsed.getRoiZipPath());
    }

    @Test
    public void acceptsLegacyObjectAreaWeightingAsSizeAlias() {
        ObjectTerritoriesMacroOptions parsed = MacroOptionsParser.parse(
                "label1=[Cells] regions=[C:/regions.zip] density_weighting=object_area");

        assertEquals(
                DensityWeightingSelection.OBJECT_AREA,
                parsed.getDensityWeightingSelection());
    }

    @Test
    public void serialisationRoundTripsAndNormalisesWindowsPath() {
        ObjectTerritoriesMacroOptions parsed = MacroOptionsParser.parse(
                "label1=[Cells A] regions=[C:\\data folder\\regions.zip]");

        String serialised = parsed.toMacroOptions();
        assertTrue(serialised.contains("label1=[Cells A]"));
        assertTrue(serialised.contains("regions=[C:/data folder/regions.zip]"));
        ObjectTerritoriesMacroOptions reparsed = MacroOptionsParser.parse(serialised);
        assertEquals(parsed.getLabelTitles(), reparsed.getLabelTitles());
        assertEquals("C:/data folder/regions.zip", reparsed.getRoiZipPath());
    }

    @Test
    public void rejectsUnknownAndNonConsecutiveLabels() {
        expectFailure("label1=A regions=R mystery=true", "unknown macro option");
        expectFailure("label1=A label3=C regions=R", "consecutive");
        expectFailure(
                "label1=A regions=R region_mask=M",
                "exactly one");
    }

    @Test
    public void rejectsInvalidNumbersAndUnsafeValues() {
        expectFailure("label1=A regions=R bandwidth=0", "positive");
        expectFailure("label1=A regions=R permutations=no", "integer");
        expectFailure("label1=[bad\"title] regions=R", "unsafe");
    }

    private static void expectFailure(String source, String expectedText) {
        try {
            MacroOptionsParser.parse(source);
            fail("Expected parsing to fail");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(expectedText));
        }
    }
}
