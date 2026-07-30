package territories.api;

/** Defines what each object's kernel contributes to a density map. */
public enum DensityWeighting {
    /** Every object contributes one unit, independent of its size. */
    OBJECT_COUNT,
    /** Every object contributes its calibrated two-dimensional area. */
    OBJECT_AREA
}

