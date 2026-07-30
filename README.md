# Object Territories

Object Territories is a Fiji/ImageJ plugin for bounded Voronoi territory analysis,
neighbourhood interactions, and kernel density maps from labelled objects.

This repository currently contains the in-development 2D v0.1 build.

## Current functionality

- 1–5 two-dimensional label images, with each image treated as one object type.
- ImageJ region `.roi` files and ROI `.zip` sets.
- Independent analysis of named ROIs or analysis of their geometric union.
- Voronoi cells clipped to arbitrary region shapes, with boundary cells flagged.
- Per-object territory area, neighbours, and neighbour identities.
- Observed, expected, z-score, and two-sided permutation interaction matrices.
- Territory-area coefficient of variation and mean nearest-neighbour distance/SD.
- Corrected or uncorrected Gaussian kernel density estimation (KDE).
- Both object-count and object-area-weighted density maps.
- Leave-one-out local density for every object.
- Interactive, recorded macro, headless, and public Java API paths.
- CSV/TIFF auto-save under `Objects/`, `Interactions/`, `Density/`, and `Maps/`.

True 3D analysis is not yet implemented. A future 3D engine will use volumetric
territories and masks; the plugin will not silently project 3D data into 2D.

## Fiji command

```text
Plugins > Object Territories
```

JTS (Java Topology Suite) is bundled and internally renamed in the plugin JAR,
so users do not need to install a separate geometry library.

## ImageJ macros

Minimal interactive-result run:

```ijm
run("Object Territories",
    "label1=[Cells A] regions=[C:/data/regions.zip]");
```

Headless or batch-style auto-save:

```ijm
run("Object Territories",
    "mode=both label1=[Cells A] label2=[Cells B] " +
    "regions=[C:/data/regions.zip] region_mode=independent " +
    "edge_cells=include_flagged density_weighting=both " +
    "boundary=corrected bandwidth=auto permutations=1000 seed=12345 " +
    "output=[C:/results/sample-01] hide_results");
```

| Option | Values | Default |
|---|---|---|
| `mode` | `territories`, `density`, `both` | `both` |
| `label1`…`label5` | titles of open label images | `label1` required |
| `regions` | ImageJ `.roi` or ROI `.zip` path | required |
| `region_mode` | `independent`, `union` | `independent` |
| `edge_cells` | `include_flagged`, `exclude_from_summaries` | `include_flagged` |
| `density_weighting` | `object_count`, `object_area`, `both` | `both` |
| `boundary` | `corrected`, `clipped` | `corrected` |
| `bandwidth` | positive physical distance or `auto` | `auto` |
| `permutations` | positive integer | `1000` |
| `seed` | integer | `12345` |
| `output` | auto-save directory | none |
| `hide_results` | suppress result windows | off |

Headless runs require `output=[directory]`.

## Java API

```java
ObjectTerritoriesParameters parameters = ObjectTerritoriesParameters.builder()
    .addLabelImage(labelsA)
    .addLabelImage(labelsB)
    .regions(regionRois)
    .analysisMode(AnalysisMode.BOTH)
    .densityWeightingSelection(DensityWeightingSelection.BOTH)
    .permutations(1000)
    .seed(12345L)
    .build();

ObjectTerritoriesResult result = ObjectTerritories.analyze(parameters);
try {
    // Read region results, tables, geometries, and density images.
} finally {
    result.closeDensityImages();
}
```

The Java API does not show windows, write files, mutate input images, or use
ImageJ’s global Results table. The caller owns returned density images.

See `FIRST_BUILD_PLAN.md` for the full product scope.
