package territories.output;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.LUT;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Found by the GUI check of 0.3.0: in Fiji, mpl-viridis and glasbey are LUT
 * files, not ImageJ 1 commands, so the command test always failed and the
 * results opened with the Fire and 3-3-2 RGB fallbacks instead.
 */
public class LookupTablesTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void lutFileIsAppliedToEverySliceWithoutAnyCommand() throws Exception {
        File luts = temporary.newFolder("luts");
        byte[] raw = new byte[768];
        for (int i = 0; i < 256; i++) {
            raw[i] = (byte) i;              // red ramps up
            raw[256 + i] = (byte) (255 - i); // green ramps down
            raw[512 + i] = (byte) 7;        // blue constant
        }
        Files.write(new File(luts, "test-lut.lut").toPath(), raw);
        ImageStack stack = new ImageStack(4, 3);
        for (int z = 0; z < 3; z++) stack.addSlice(new FloatProcessor(4, 3));
        ImagePlus image = new ImagePlus("density", stack);

        assertEquals("test-lut", LookupTables.apply(image, luts, "test-lut", "No Such Command"));

        for (int slice = 1; slice <= 3; slice++) {
            image.setSlice(slice);
            LUT lut = image.getProcessor().getLut();
            byte[] reds = new byte[256];
            byte[] greens = new byte[256];
            byte[] blues = new byte[256];
            lut.getReds(reds);
            lut.getGreens(greens);
            lut.getBlues(blues);
            assertEquals((byte) 200, reds[200]);
            assertEquals((byte) 55, greens[200]);
            assertEquals((byte) 7, blues[200]);
        }
        byte[] expected = new byte[256];
        for (int i = 0; i < 256; i++) expected[i] = (byte) i;
        byte[] stackReds = new byte[256];
        ((java.awt.image.IndexColorModel) image.getStack().getColorModel()).getReds(stackReds);
        assertArrayEquals(expected, stackReds);
    }

    /** glasbey.lut is a text table ("Index Red Green Blue"), not raw bytes. */
    @Test
    public void textLutFilesSuchAsGlasbeyAreRead() throws Exception {
        File luts = temporary.newFolder("text-luts");
        StringBuilder text = new StringBuilder("Index\tRed\tGreen\tBlue\n");
        for (int i = 0; i < 256; i++) {
            text.append(i).append('\t').append(255 - i).append('\t').append(i % 7)
                    .append('\t').append(3).append('\n');
        }
        Files.write(new File(luts, "glasbey-like.lut").toPath(),
                text.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        ImagePlus image = new ImagePlus("territories", new FloatProcessor(4, 3));

        assertEquals("glasbey-like",
                LookupTables.apply(image, luts, "glasbey-like", "No Such Command"));

        LUT lut = image.getProcessor().getLut();
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        assertEquals((byte) 245, reds[10]);
        assertEquals((byte) 3, greens[10]);
    }

    @Test
    public void missingFileAndMissingCommandsLeaveTheImageAlone() throws Exception {
        File empty = temporary.newFolder("no-luts");
        ImagePlus image = new ImagePlus("density", new FloatProcessor(4, 3));
        assertNull(LookupTables.load(empty, "mpl-viridis"));
        assertNull(LookupTables.load(null, "mpl-viridis"));
        assertNull(LookupTables.apply(image, empty, "No Such LUT", "No Such Command"));
    }
}
