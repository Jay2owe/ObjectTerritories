package territories.output;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.LutLoader;
import ij.process.LUT;

import java.io.File;

/**
 * Applies Fiji's lookup tables to result images.
 *
 * <p>Fiji's tables (mpl-viridis, glasbey) are files in its {@code luts}
 * folder. They are not ImageJ 1 commands in current Fiji, where the Lookup
 * Tables menu is built by ImageJ2, so 0.3.0's "is there a command called
 * mpl-viridis?" test always failed there and every density map opened with
 * the Fire fallback and every 3D territory stack with 3-3-2 RGB. The file is
 * now read directly; the command and then the built-in fallback are used only
 * where the file is missing (plain ImageJ).
 */
final class LookupTables {

    static final String DENSITY = "mpl-viridis";
    static final String DENSITY_FALLBACK = "Fire";
    static final String TERRITORIES = "glasbey";
    static final String TERRITORIES_FALLBACK = "3-3-2 RGB";

    private LookupTables() {
    }

    /** Applies {@code name}, else {@code fallback}; returns the one applied, or null. */
    static String apply(ImagePlus image, String name, String fallback) {
        return apply(image, lutDirectory(), name, fallback);
    }

    static String apply(ImagePlus image, File lutDirectory, String name, String fallback) {
        LUT lut = load(lutDirectory, name);
        if (lut != null) {
            setLut(image, lut);
            return name;
        }
        if (commandExists(name)) {
            IJ.run(image, name, "");
            return name;
        }
        if (commandExists(fallback)) {
            IJ.run(image, fallback, "");
            return fallback;
        }
        return null;
    }

    /**
     * Reads {@code <directory>/<name>.lut} in any format ImageJ knows (raw
     * 768 bytes like mpl-viridis, or a text table like glasbey); null when
     * absent or unreadable, without an error dialog.
     */
    static LUT load(File directory, String name) {
        if (directory == null) return null;
        File file = new File(directory, name + ".lut");
        if (!file.isFile()) return null;
        return LutLoader.openLut("noerror:" + file.getAbsolutePath());
    }

    /** Sets the table on the image and every slice of its stack. */
    static void setLut(ImagePlus image, LUT lut) {
        image.getProcessor().setLut(lut);
        if (image.getStackSize() > 1) image.getStack().setColorModel(lut);
        image.setProcessor(image.getProcessor());
    }

    private static File lutDirectory() {
        String directory = IJ.getDirectory("luts");
        return directory == null ? null : new File(directory);
    }

    private static boolean commandExists(String command) {
        java.util.Hashtable<?, ?> commands = ij.Menus.getCommands();
        return commands != null && commands.get(command) != null;
    }
}
