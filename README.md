# Object Territories

Object Territories is a Fiji/ImageJ plugin for bounded Voronoi territory analysis,
neighbourhood interactions, and kernel density maps from labelled objects.

This repository currently contains the in-development 2D and genuine-3D v0.1 build.

## Current functionality

- 1–5 matching 2D label images or 3D label stacks, with each image treated as
  one object type.
- ImageJ region `.roi` files and ROI `.zip` sets.
- Independent analysis of named ROIs or analysis of their geometric union.
- Voronoi cells clipped to arbitrary region shapes, with boundary cells flagged.
- Per-object territory area, neighbours, and neighbour identities.
- Observed, expected, z-score, and two-sided permutation interaction matrices.
- Territory-area coefficient of variation and mean nearest-neighbour distance/SD.
- Calibrated 3D voxel territories clipped to a labelled region-mask stack.
- Face-sharing (6-connected) 3D territory neighbours and interaction matrices.
- Corrected or uncorrected Gaussian kernel density estimation (KDE) in 2D or 3D.
- Both object-count and object-size-weighted density maps (area in 2D, volume
  in 3D).
- Leave-one-out local density for every object.
- Interactive, recorded macro, headless, and public Java API paths.
- CSV/TIFF auto-save under `Objects/`, `Interactions/`, `Density/`, and `Maps/`.

3D territories are voxel-resolved rather than projected. Physical x/y/z
calibration is used for nearest-centroid assignment, including anisotropic
z-spacing. Two territories are neighbours only when their voxels share a face.
The result is genuinely volumetric but, like any voxel analysis, its precision
depends on image resolution.

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

Genuine 3D run using an open region-mask stack:

```ijm
run("Object Territories",
    "mode=both label1=[Cells A 3D] label2=[Cells B 3D] " +
    "region_mask=[Brain regions 3D] region_mode=independent " +
    "edge_cells=include_flagged density_weighting=both " +
    "boundary=corrected bandwidth=auto permutations=1000 seed=12345 " +
    "output=[C:/results/sample-01-3D] hide_results");
```

| Option | Values | Default |
|---|---|---|
| `mode` | `territories`, `density`, `both` | `both` |
| `label1`…`label5` | titles of open label images | `label1` required |
| `regions` | ImageJ `.roi` or ROI `.zip` path for 2D | one region input required |
| `region_mask` | title of an open positive-integer 3D mask stack | one region input required |
| `region_mode` | `independent`, `union` | `independent` |
| `edge_cells` | `include_flagged`, `exclude_from_summaries` | `include_flagged` |
| `density_weighting` | `object_count`, `object_size`, `both` | `both` |
| `boundary` | `corrected`, `clipped` | `corrected` |
| `bandwidth` | positive physical distance or `auto` | `auto` |
| `permutations` | positive integer | `1000` |
| `seed` | integer | `12345` |
| `output` | auto-save directory | none |
| `hide_results` | suppress result windows | off |

Headless runs require `output=[directory]`.
Supply exactly one of `regions` or `region_mask`.

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

For 3D, use `ObjectTerritoriesParameters3D` and
`ObjectTerritories.analyze3D(...)`; the matching positive-integer mask stack
defines independent labelled volumes or their union.

See `FIRST_BUILD_PLAN.md` for the full product scope.
