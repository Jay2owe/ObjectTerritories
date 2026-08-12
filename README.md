# Object Territories

Object Territories is a Fiji/ImageJ plugin for bounded Voronoi territory analysis,
neighbourhood interactions, and kernel density maps from labelled objects.

This repository contains the 2D and genuine-3D v0.2 build.

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
- Regex-grouped recursive 2D folder batches with a preview and per-sample manifest.
- CSV/TIFF auto-save under `Objects/`, `Interactions/`, `Density/`, and `Maps/`.

3D territories are voxel-resolved rather than projected. Physical x/y/z
calibration is used for nearest-centroid assignment, including anisotropic
z-spacing. Two territories are neighbours only when their voxels share a face.
The result is genuinely volumetric but, like any voxel analysis, its precision
depends on image resolution.

## Fiji command

```text
Plugins > Object Territories
Plugins > Object Territories Batch...
```

JTS (Java Topology Suite) is bundled and internally renamed in the plugin JAR,
so users do not need to install a separate geometry library.

The folder-batch discovery supplied by `oc3d-core` 0.1.0 is also bundled and
renamed under `territories.internal.core`. The analysis engine itself lives in
`io.github.jay2owe:territories-core` 0.1.0 and is bundled under
`territories.core`, the package it has always occupied here. Users still
install only the Object Territories JAR; none of JTS, `oc3d-core` or
`territories-core` should be copied into Fiji separately.

## Folder batch

`Object Territories Batch` groups matching 2D label images by a filename regular
expression. Select the capture group that represents the label type; all other
captures identify the sample. For example,
`(.+)_([^_]+)\.(?:tif|tiff)$` with capture group 2 groups
`sample01_A.tif` and `sample01_B.tif` as one two-type analysis.

The preview shows every discovered file before processing. Recursive discovery
is deterministic, avoids directory cycles, and excludes the selected output
tree so a later run cannot consume its own results. Groups of one to five label
types run; larger groups are reported as skipped. Each sample is written to its
own output folder and every outcome is recorded in the batch manifest.

The current batch path is deliberately 2D and applies one selected `.roi` or ROI
`.zip` region set to every sample. The existing command and Java API continue to
support genuine 3D runs one at a time. Use ImageJ's Macro Recorder while running
the batch command to capture its complete replayable options.

The same workflow is available to Java callers through
`territories.batch.ObjectTerritoriesBatchParameters`,
`ObjectTerritoriesBatchRunner.preview(...)`, and
`ObjectTerritoriesBatchRunner.run(...)`.

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

## Building with the shared cores

The plugin declares `io.github.jay2owe:oc3d-core:0.1.0` and
`io.github.jay2owe:territories-core:0.1.0`. Neither is fetched from a public
Maven repository, so a clean build must install both into the same local
repository first:

```text
mvn -f ../oc3d-core/pom.xml clean install
mvn -f ../Cores/territories-core/pom.xml clean install
mvn clean verify
```

`verify` opens the packaged plugin through an isolated class loader to prove
that the private cores and JTS are present, that no unrelocated copy of any of
them survives, and that ImageJ itself is not bundled.

## Equivalence gate

`src/test/java/territories/equivalence/` re-runs the whole documented option
space and compares every field against goldens captured before the engine was
extracted, held in `golden/pre-extraction/`. Those goldens are **immutable**: a
golden later found to be wrong is a bug report against the shipped plugin, to
be fixed as its own change with its own release note, never by regenerating
them to make a difference disappear.

```text
mvn -o test -Dtest=GoldenEquivalenceTest                        # verify
mvn -o test -Dterritories.golden.dump=<case-name>               # print one case
```

Everything in the gate is compared as raw IEEE-754 bit patterns with no
tolerance, floating-point territory areas included.
## Parallel execution

Interaction null-model permutations use a bounded worker pool while preserving the original seeded
shuffle sequence and result order exactly. The automatic limit is eight workers. Set the JVM system
property `territories.parallelism` to a positive integer to override it, or to `1` to use the serial
reference path.

## Licence

BSD 3-Clause. See `LICENSE`. Attribution and third-party notices are in
`NOTICE`; both ship inside the jar under `META-INF/`.

JTS (Java Topology Suite) is bundled and relocated, so the distributed jar
carries JTS bytecode. JTS is dual-licensed **EPL 2.0 / EDL 1.0** and the
consumer picks. This plugin takes it under **EDL 1.0**, which is the BSD
3-Clause licence in substance, and therefore ships the combined jar under
plain BSD-3-Clause with the JTS notice retained at
`src/main/resources/META-INF/licenses/JTS-LICENSE.txt`. `territories-core`
carries the same file at the same path, so a consumer that shades the core
inherits the notice rather than having to remember it.

Nothing on any code path links GPL.
