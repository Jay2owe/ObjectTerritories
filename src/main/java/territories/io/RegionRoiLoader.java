package territories.io;

import ij.gui.Roi;
import ij.io.RoiDecoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads area ROIs from an ImageJ ROI file or ROI zip without opening RoiManager. */
public final class RegionRoiLoader {

    private static final int MAX_ROIS = 10000;
    private static final int MAX_ROI_BYTES = 16 * 1024 * 1024;

    private RegionRoiLoader() {
    }

    public static List<Roi> load(File source) throws IOException {
        if (source == null) throw new IllegalArgumentException("ROI source must not be null");
        if (!source.isFile()) throw new IOException("ROI source does not exist: " + source);
        String lower = source.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".roi")) {
            Roi roi = new RoiDecoder(source.getAbsolutePath()).getRoi();
            validateArea(roi, source.getName());
            return Collections.singletonList(roi);
        }
        if (!lower.endsWith(".zip")) {
            throw new IOException("region source must be an ImageJ .roi file or ROI .zip");
        }

        ArrayList<Roi> result = new ArrayList<Roi>();
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".roi")) {
                    continue;
                }
                if (result.size() >= MAX_ROIS) {
                    throw new IOException("ROI zip contains more than " + MAX_ROIS + " ROIs");
                }
                byte[] encoded = readEntry(input);
                String name = baseName(entry.getName());
                Roi roi = new RoiDecoder(encoded, name).getRoi();
                if (roi.getName() == null || roi.getName().trim().isEmpty()) roi.setName(name);
                validateArea(roi, entry.getName());
                result.add(roi);
            }
        }
        if (result.isEmpty()) throw new IOException("ROI zip contains no area ROI files");
        result.sort(Comparator.comparing(
                roi -> roi.getName() == null ? "" : roi.getName(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static byte[] readEntry(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_ROI_BYTES) {
                throw new IOException("an ROI entry exceeds " + MAX_ROI_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateArea(Roi roi, String sourceName) throws IOException {
        if (roi == null) throw new IOException("could not decode ROI: " + sourceName);
        if (!roi.isArea()) throw new IOException("region ROI is not an area: " + sourceName);
    }

    private static String baseName(String path) {
        String normalised = path.replace('\\', '/');
        int slash = normalised.lastIndexOf('/');
        String name = slash >= 0 ? normalised.substring(slash + 1) : normalised;
        return name.substring(0, name.length() - 4);
    }
}

