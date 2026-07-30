package territories.output;

import java.util.Locale;
import java.util.Set;

/** Creates deterministic filesystem-safe names without silent collisions. */
final class SafeOutputNames {

    private SafeOutputNames() {
    }

    static String unique(String value, Set<String> usedCaseInsensitiveNames) {
        String base = safe(value);
        String candidate = base;
        int suffix = 2;
        while (!usedCaseInsensitiveNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    static String safe(String value) {
        String result = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return result.isEmpty() ? "Result" : result;
    }
}
