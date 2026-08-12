package territories.equivalence;

import org.junit.Test;
import territories.api.ObjectTerritories;
import territories.api.ObjectTerritoriesParameters;
import territories.api.ObjectTerritoriesParameters3D;
import territories.api.ObjectTerritoriesResult;
import territories.api.ObjectTerritoriesResult3D;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The extraction gate.
 *
 * <p>Extracting an engine into a reusable module is a refactor, so the outputs
 * must not move. This test pins every field of every documented output across
 * the whole configuration space against goldens captured from the build that
 * existed before the first class moved.
 *
 * <p><b>Tier contract.</b> Everything here is <b>Tier 1 — bit-identical, no
 * tolerance</b>, floating-point territory areas included. That is not an
 * ambitious choice, it is the only correct one: the extraction moves the same
 * arithmetic, in the same traversal order, compiled against the same JTS
 * build, so any change in the last bit of a polygon area means code changed,
 * not that floating point drifted. Doubles are therefore compared as raw
 * IEEE-754 bit patterns rather than as rounded decimal text, and no Tier 2
 * tolerance band is declared for any column. A tolerance chosen after seeing a
 * difference is not a gate.
 */
public class GoldenEquivalenceTest {

    @Test
    public void twoDimensionalCorpusMatchesPreExtractionGoldens() throws Exception {
        Golden golden = new Golden("2d");
        List<Fixtures.Case2D> cases = Fixtures.all2D();
        List<Config> canonical = Config.canonical();
        List<Config> cross = Config.cross();
        List<String> sweep = Fixtures.sweep2D();

        for (int c = 0; c < cases.size(); c++) {
            Fixtures.Case2D fixture = cases.get(c);
            for (int i = 0; i < canonical.size(); i++) {
                run2D(golden, "canon", fixture, canonical.get(i), true);
            }
            if (!sweep.contains(fixture.getName())) continue;
            for (int i = 0; i < cross.size(); i++) {
                run2D(golden, "cross", fixture, cross.get(i), false);
            }
        }

        assertNoDifferences(golden);
    }

    @Test
    public void threeDimensionalCorpusMatchesPreExtractionGoldens() throws Exception {
        Golden golden = new Golden("3d");
        List<Fixtures.Case3D> cases = Fixtures.all3D();
        List<Config> canonical = Config.canonical();
        List<Config> cross = Config.cross();
        List<String> sweep = Fixtures.sweep3D();

        for (int c = 0; c < cases.size(); c++) {
            Fixtures.Case3D fixture = cases.get(c);
            for (int i = 0; i < canonical.size(); i++) {
                run3D(golden, "canon", fixture, canonical.get(i), true);
            }
            if (!sweep.contains(fixture.getName())) continue;
            for (int i = 0; i < cross.size(); i++) {
                run3D(golden, "cross", fixture, cross.get(i), false);
            }
        }

        assertNoDifferences(golden);
    }

    /**
     * A comparison that cannot fail is not evidence. This drives one corpus
     * case away from its golden on purpose and requires the differ to notice.
     */
    @Test
    public void negativeControlDetectsAMovedOutput() {
        Fixtures.Case2D fixture = Fixtures.all2D().get(3);
        Config config = Config.canonical().get(0);
        String unchanged = dump2D(fixture, config);

        Fixtures.Case2D perturbed = new Fixtures.Case2D(
                fixture.getName(),
                java.util.Arrays.asList(
                        Fixtures.labels2D("Cells A", 32, 32, new int[][] {
                                {1, 3, 3, 3, 3}, {2, 24, 4, 3, 3},
                                {3, 6, 24, 3, 3}, {4, 25, 25, 2, 2}
                        }, 1.0, 1.0, "pixel"),
                        Fixtures.labels2D("Cells B", 32, 32, new int[][] {
                                {1, 14, 6, 3, 3}, {2, 7, 14, 3, 3},
                                {3, 22, 15, 3, 3}, {4, 15, 22, 3, 3}
                        }, 1.0, 1.0, "pixel")),
                fixture.getRegions());
        String moved = dump2D(perturbed, config);

        assertFalse(
                "one object moved by a single pixel must change the dump",
                unchanged.equals(moved));
        assertFalse(Golden.digest(unchanged).equals(Golden.digest(moved)));
    }

    @Test
    public void documentedRejectionsMatchPreExtractionGoldens() throws Exception {
        Golden golden = new Golden("rejections");
        List<Rejections.Rejection> rejections = Rejections.all();
        for (int i = 0; i < rejections.size(); i++) {
            Rejections.Rejection rejection = rejections.get(i);
            String observed;
            try {
                rejection.run();
                observed = "NOTHING THROWN";
            } catch (RuntimeException thrown) {
                observed = thrown.getClass().getName() + ": " + thrown.getMessage();
            } catch (Exception thrown) {
                observed = thrown.getClass().getName() + ": " + thrown.getMessage();
            }
            assertFalse(
                    "rejection case '" + rejection.getName() + "' threw nothing",
                    "NOTHING THROWN".equals(observed));
            golden.record(rejection.getName(), observed, true);
        }
        assertTrue("the rejection corpus shrank", golden.size() >= 28);
        assertNoDifferences(golden);
    }

    // ------------------------------------------------------------------

    private static void assertNoDifferences(Golden golden) throws Exception {
        List<String> failures = golden.verifyOrCapture();
        if (failures.isEmpty()) return;
        StringBuilder message = new StringBuilder();
        message.append(failures.size()).append(" of ").append(golden.size())
                .append(" cases moved (Tier 1 — no tolerance):\n");
        for (int i = 0; i < failures.size() && i < 25; i++) {
            message.append("  ").append(failures.get(i)).append('\n');
        }
        if (failures.size() > 25) {
            message.append("  ... and ").append(failures.size() - 25).append(" more\n");
        }
        fail(message.toString());
    }

    private static void run2D(
            Golden golden,
            String prefix,
            Fixtures.Case2D fixture,
            Config config,
            boolean keepDump) {
        golden.record(
                prefix + "__" + fixture.getName() + "__" + config.getName(),
                dump2D(fixture, config),
                keepDump);
    }

    private static void run3D(
            Golden golden,
            String prefix,
            Fixtures.Case3D fixture,
            Config config,
            boolean keepDump) {
        golden.record(
                prefix + "__" + fixture.getName() + "__" + config.getName(),
                dump3D(fixture, config),
                keepDump);
    }

    private static String dump2D(Fixtures.Case2D fixture, Config config) {
        ObjectTerritoriesParameters parameters = config.apply(
                        ObjectTerritoriesParameters.builder()
                                .labelImages(fixture.getLabelImages())
                                .regions(fixture.getRegions()))
                .build();
        Dump dump = new Dump();
        dump.line("case " + fixture.getName() + " " + config.getName());
        ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters);
        try {
            dump.write2D(result);
        } finally {
            result.closeDensityImages();
        }
        return dump.toString();
    }

    private static String dump3D(Fixtures.Case3D fixture, Config config) {
        ObjectTerritoriesParameters3D parameters = config.apply(
                        ObjectTerritoriesParameters3D.builder()
                                .labelImages(fixture.getLabelImages())
                                .regionMask(fixture.getRegionMask()))
                .build();
        Dump dump = new Dump();
        dump.line("case " + fixture.getName() + " " + config.getName());
        ObjectTerritoriesResult3D result = ObjectTerritories.analyze3D(parameters);
        try {
            dump.write3D(result);
        } finally {
            result.closeGeneratedImages();
        }
        return dump.toString();
    }

    @Test
    public void corpusCoversTheWholeDocumentedConfigurationSpace() {
        assertEquals(3 * 2 * 2 * 4 * 2 * 2, Config.cross().size());
        assertEquals(13, Fixtures.all2D().size());
        assertEquals(6, Fixtures.all3D().size());
    }
}
