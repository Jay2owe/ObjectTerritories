package territories.api;

/** Selects which density map families an analysis produces. */
public enum DensityWeightingSelection {
    OBJECT_COUNT,
    OBJECT_SIZE,
    BOTH,
    /**
     * Compatibility alias for {@link #OBJECT_SIZE}.
     *
     * @deprecated Use {@link #OBJECT_SIZE}; in 3D the size is volume.
     */
    @Deprecated
    OBJECT_AREA
}
