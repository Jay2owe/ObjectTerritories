package territories.macro;

import territories.api.AnalysisMode;
import territories.api.DensityBoundaryMode;
import territories.api.DensityWeightingSelection;
import territories.api.EdgeCellPolicy;
import territories.api.ObjectTerritoriesParameters;
import territories.api.RegionMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure Java parser for {@code run("Object Territories", "...")} options. */
public final class MacroOptionsParser {

    private MacroOptionsParser() {
    }

    public static ObjectTerritoriesMacroOptions parse(String source) {
        Map<String, String> tokens = tokenize(source);
        rejectUnknown(tokens);

        List<String> labels = new ArrayList<String>();
        boolean missingSeen = false;
        for (int i = 1; i <= 5; i++) {
            String value = tokens.get("label" + i);
            if (value == null) {
                missingSeen = true;
            } else {
                if (missingSeen) {
                    throw new IllegalArgumentException("label image keys must be consecutive from label1");
                }
                labels.add(requireSafeValue(value, "label" + i));
            }
        }
        if (labels.isEmpty()) throw new IllegalArgumentException("label1 is required");

        String regions = requireSafeValue(required(tokens, "regions"), "regions");
        AnalysisMode mode = enumValue(tokens, "mode", AnalysisMode.class, AnalysisMode.BOTH);
        RegionMode regionMode = enumValue(tokens, "region_mode", RegionMode.class, RegionMode.INDEPENDENT);
        EdgeCellPolicy edgePolicy = enumValue(
                tokens, "edge_cells", EdgeCellPolicy.class, EdgeCellPolicy.INCLUDE_FLAGGED);
        DensityWeightingSelection weighting = enumValue(
                tokens, "density_weighting", DensityWeightingSelection.class,
                DensityWeightingSelection.BOTH);
        DensityBoundaryMode boundary = enumValue(
                tokens, "boundary", DensityBoundaryMode.class, DensityBoundaryMode.CORRECTED);
        double bandwidth = bandwidth(tokens.get("bandwidth"));
        int permutations = integer(
                tokens.get("permutations"),
                ObjectTerritoriesParameters.DEFAULT_PERMUTATIONS,
                1,
                "permutations");
        long seed = longValue(tokens.get("seed"), ObjectTerritoriesParameters.DEFAULT_SEED, "seed");
        String output = tokens.containsKey("output")
                ? requireSafeValue(tokens.get("output"), "output") : null;
        boolean hideResults = tokens.containsKey("hide_results");

        return new ObjectTerritoriesMacroOptions(
                labels, regions, mode, regionMode, edgePolicy, weighting,
                boundary, bandwidth, permutations, seed, output, hideResults);
    }

    private static Map<String, String> tokenize(String source) {
        if (source == null) throw new IllegalArgumentException("macro options must not be null");
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        int cursor = 0;
        while (cursor < source.length()) {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor == source.length()) break;

            int keyStart = cursor;
            while (cursor < source.length() && isKeyCharacter(source.charAt(cursor))) cursor++;
            if (cursor == keyStart) {
                throw new IllegalArgumentException("expected an option near: " + source.substring(keyStart));
            }
            String key = source.substring(keyStart, cursor).toLowerCase(Locale.ROOT);
            if (cursor == source.length() || Character.isWhitespace(source.charAt(cursor))) {
                if (values.put(key, "true") != null) {
                    throw new IllegalArgumentException("duplicate macro option: " + key);
                }
                continue;
            }
            if (source.charAt(cursor) != '=') {
                throw new IllegalArgumentException("expected key=value near: " + source.substring(keyStart));
            }
            cursor++;
            if (cursor >= source.length()) throw new IllegalArgumentException(key + " has no value");

            String value;
            if (source.charAt(cursor) == '[') {
                int end = source.indexOf(']', cursor + 1);
                if (end < 0) throw new IllegalArgumentException(key + " has an unclosed bracketed value");
                value = source.substring(cursor + 1, end);
                cursor = end + 1;
                if (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))) {
                    throw new IllegalArgumentException("unexpected text after " + key);
                }
            } else {
                int valueStart = cursor;
                while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))) cursor++;
                value = source.substring(valueStart, cursor);
            }
            if (values.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate macro option: " + key);
            }
        }
        return values;
    }

    private static boolean isKeyCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static void rejectUnknown(Map<String, String> values) {
        for (String key : values.keySet()) {
            if (key.matches("label[1-5]")
                    || key.equals("regions")
                    || key.equals("mode")
                    || key.equals("region_mode")
                    || key.equals("edge_cells")
                    || key.equals("density_weighting")
                    || key.equals("boundary")
                    || key.equals("bandwidth")
                    || key.equals("permutations")
                    || key.equals("seed")
                    || key.equals("output")
                    || key.equals("hide_results")) {
                continue;
            }
            throw new IllegalArgumentException("unknown macro option: " + key);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String requireSafeValue(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        if (value.indexOf('[') >= 0 || value.indexOf(']') >= 0
                || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(key + " contains unsafe characters");
        }
        return value;
    }

    private static double bandwidth(String value) {
        if (value == null || value.equalsIgnoreCase("auto")) return 0.0;
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("bandwidth must be 'auto' or a positive number", error);
        }
        if (!Double.isFinite(parsed) || parsed <= 0.0) {
            throw new IllegalArgumentException("bandwidth must be 'auto' or a positive finite number");
        }
        return parsed;
    }

    private static int integer(String value, int defaultValue, int minimum, String key) {
        if (value == null) return defaultValue;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum) throw new IllegalArgumentException(key + " must be at least " + minimum);
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
    }

    private static long longValue(String value, long defaultValue, String key) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Map<String, String> values, String key, Class<E> type, E defaultValue) {
        String value = values.get(key);
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(key + " has unsupported value: " + value, error);
        }
    }
}
