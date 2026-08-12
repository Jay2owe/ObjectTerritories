package territories.api;

/**
 * Translates this plugin's published option vocabulary into the engine's.
 *
 * <p>The engine lives in {@code territories-core} and cannot depend on the
 * plugin, so it declares its own copies of the four enumerations it reads.
 * The published ones stay here, unchanged: they are what every macro option,
 * every {@code ObjectTerritoriesParameters} setter and every Java caller
 * already names, and relocating a documented public API breaks callers.
 *
 * <p>Each mapping switches on the constant rather than matching by
 * {@code name()} or {@code ordinal()}, so adding a value on either side is a
 * compile error here instead of a silent mistranslation.
 */
final class EngineOptions {

    private EngineOptions() {
    }

    static sc.fiji.territories.core.EdgeCellPolicy engine(EdgeCellPolicy value) {
        switch (value) {
            case INCLUDE_FLAGGED:
                return sc.fiji.territories.core.EdgeCellPolicy.INCLUDE_FLAGGED;
            case EXCLUDE_FROM_SUMMARIES:
                return sc.fiji.territories.core.EdgeCellPolicy.EXCLUDE_FROM_SUMMARIES;
            default:
                throw new IllegalArgumentException("unsupported edge-cell policy: " + value);
        }
    }

    static sc.fiji.territories.core.RegionMode engine(RegionMode value) {
        switch (value) {
            case INDEPENDENT:
                return sc.fiji.territories.core.RegionMode.INDEPENDENT;
            case UNION:
                return sc.fiji.territories.core.RegionMode.UNION;
            default:
                throw new IllegalArgumentException("unsupported region mode: " + value);
        }
    }

    @SuppressWarnings("deprecation")
    static sc.fiji.territories.core.DensityWeighting engine(DensityWeighting value) {
        switch (value) {
            case OBJECT_COUNT:
                return sc.fiji.territories.core.DensityWeighting.OBJECT_COUNT;
            case OBJECT_SIZE:
                return sc.fiji.territories.core.DensityWeighting.OBJECT_SIZE;
            case OBJECT_AREA:
                return sc.fiji.territories.core.DensityWeighting.OBJECT_AREA;
            default:
                throw new IllegalArgumentException("unsupported density weighting: " + value);
        }
    }

    static sc.fiji.territories.core.DensityBoundaryMode engine(DensityBoundaryMode value) {
        switch (value) {
            case CORRECTED:
                return sc.fiji.territories.core.DensityBoundaryMode.CORRECTED;
            case CLIPPED:
                return sc.fiji.territories.core.DensityBoundaryMode.CLIPPED;
            default:
                throw new IllegalArgumentException("unsupported density boundary mode: " + value);
        }
    }
}
