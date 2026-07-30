package territories.io;

import ij.gui.Roi;
import ij.io.RoiEncoder;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class RegionRoiLoaderTest {

    @Test
    public void loadsAndSortsAreaRoisWithoutRoiManager() throws Exception {
        File zip = File.createTempFile("object-territories-regions", ".zip");
        zip.deleteOnExit();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            write(output, "zeta.roi", new Roi(0, 0, 4, 4));
            write(output, "alpha.roi", new Roi(4, 0, 4, 4));
        }

        List<Roi> rois = RegionRoiLoader.load(zip);

        assertEquals(2, rois.size());
        assertEquals("alpha", rois.get(0).getName());
        assertEquals("zeta", rois.get(1).getName());
    }

    private static void write(ZipOutputStream output, String name, Roi roi) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        new RoiEncoder(encoded).write(roi);
        output.putNextEntry(new ZipEntry(name));
        output.write(encoded.toByteArray());
        output.closeEntry();
    }
}

