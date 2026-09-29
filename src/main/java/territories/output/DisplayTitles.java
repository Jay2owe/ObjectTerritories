package territories.output;

import sc.fiji.territories.core.DensityWeighting;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Window titles for one interactive display of results. ImageJ refills a
 * results table that has the title of one already on screen, and two images
 * with one title cannot be told apart (or picked by a macro), so every title
 * shown by one run is made distinct here.
 */
final class DisplayTitles {

    private final Set<String> regions = new HashSet<String>();
    private final Set<String> images = new HashSet<String>();

    /** A region name for window titles; repeats become "name (2)", "name (3)". */
    String region(String name) {
        return unique(name, regions);
    }

    /** An image title not used before in this display. */
    String image(String title) {
        return unique(title, images);
    }

    /**
     * Names the weighting in a density-map title, as the saved file names do:
     * {@code A_Field_Density} becomes {@code A_Field_object_count_Density}.
     * Without it the count and size maps of one type share a title.
     */
    static String densityTitle(String title, DensityWeighting weighting) {
        String tag = "_" + weighting.name().toLowerCase(Locale.ROOT);
        int at = title.lastIndexOf("_Density");
        return at < 0 ? title + tag : title.substring(0, at) + tag + title.substring(at);
    }

    private static String unique(String value, Set<String> used) {
        String base = value == null || value.isEmpty() ? "Result" : value;
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }
}
