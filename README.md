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
- Regex-grouped recursive 2D and 3D folder batches with a preview and a saved
  per-sample manifest (`Batch_Manifest.csv`).
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

Both commands show progress in Fiji's status bar; press Escape to stop a run
between steps (a stopped batch still saves its manifest).

JTS (Java Topology Suite) is bundled and internally renamed in the plugin JAR,
so users do not need to install a separate geometry library.

The folder-batch discovery supplied by `oc3d-core` 0.1.0 is also bundled and
renamed under `territories.internal.core`. The analysis engine itself lives in
`io.github.jay2owe:territories-core` 0.2.0 and is bundled under
`territories.core`, the package it has always occupied here. Users still
install only the Object Territories JAR; none of JTS, `oc3d-core` or
`territories-core` should be copied into Fiji separately.

## Folder batch

`Object Territories Batch` groups matching label images by a filename regular
expression. Select the capture group that represents the label type; all other
captures identify the sample. For example,
`(.+)_([^_]+)\.(?:tif|tiff)$` with capture group 2 groups
`sample01_A.tif` and `sample01_B.tif` as one two-type analysis.

The preview shows every discovered file before processing. Recursive discovery
is deterministic, avoids directory cycles, and excludes the selected output
tree so a later run cannot consume its own results. Groups of one to five label
types run; larger groups are reported as skipped. Each sample is written to its
own output folder.

Every outcome (processed, skipped or error, with the reason) is recorded in
`Batch_Manifest.csv` in the output directory, written at the end of every run,
including runs in which some groups failed.

**2D** (`dimensions=2D`, the default): one selected `.roi` or ROI `.zip` region
set is applied to every sample.

**3D** (`dimensions=3D`): each group also carries its own region-mask stack.
The file whose label-type capture equals `region_mask_type` (default `mask`,
compared case-insensitively) is the positive-integer region mask; the other one
to five files are label stacks. For example `brain1_Cells.tif`,
`brain1_Plaques.tif` and `brain1_mask.tif` form one two-type 3D sample. A group
with no mask file, or more than one, is recorded as an error; the five-type
limit does not count the mask. The region ROI field is ignored in 3D.

```ijm
run("Object Territories Batch...",
    "input_folder=[C:/data/stacks] filename_regex=[(.+)_([^_]+)\\.(?:tif|tiff)$] " +
    "label_type_capture_group=2 recursive dimensions=3D region_mask_type=mask " +
    "region_roi_file_or_zip=[] analysis=BOTH multiple_region_rois=INDEPENDENT " +
    "edge_cells=INCLUDE_FLAGGED density_weighting=BOTH density_boundary=CORRECTED " +
    "bandwidth_0_is_automatic=0 permutations=1000 random_seed=12345 " +
    "output_directory=[C:/results/batch-3D]");
```

Macros recorded before 3D batch existed omit `dimensions` and still run as 2D.
Use ImageJ's Macro Recorder while running the batch command to capture its
complete replayable options.

The same workflow is available to Java callers through
`territories.batch.ObjectTerritoriesBatchParameters` (`builder(...)` for 2D,
`builder3D(...)` for 3D), `ObjectTerritoriesBatchRunner.preview(...)`, and
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

## Building with the shared cores

The plugin declares `io.github.jay2owe:oc3d-core:0.1.0` and
`io.github.jay2owe:territories-core:0.2.0`. Neither is fetched from a public
Maven repository, so a clean build must install both into the same local
repository first:

```text
./mvnw -f ../oc3d-core/pom.xml clean install
./mvnw -f ../territories-core/pom.xml clean install
./mvnw clean verify
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

Density maps, 3D territory assignment and the interaction null-model
permutations run on a bounded pool of worker threads. Results are
bit-identical to a serial run: each density pixel receives its kernel
contributions in the same order, and permutations keep the original seeded
shuffle sequence and result order. The automatic limit is eight workers. Set
the JVM system property `territories.parallelism` to a positive integer to
override it, or to `1` to use the serial reference path.

On a 16-thread workstation, a 1024 x 1024 image with 2 x 1000 objects and
automatic-bandwidth density maps takes about 9 s instead of 48 s, and a
384 x 384 x 64 stack with 2 x 800 objects about 48 s instead of 217 s.

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
