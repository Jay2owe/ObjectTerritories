# Changelog

## Unreleased

## [0.3.2] - 2026-09-30

Faster 3D territory runs. Measurement outputs (tables, maps, density values)
are unchanged.

### Changed

- Embeds `territories-core` 0.2.2, which assigns 3D territories tile by tile
  instead of searching for the nearest object from every voxel. On the
  synthetic 3D benchmark (384 x 384 x 64 stack, 2 x 800 objects, two-region
  mask, 1,000 permutations) a whole territory run takes about 2.0 s instead of
  3.1 s, and 4.4 s instead of 6.6 s in a second paired run (about 1.5x), with
  the default number of workers; with one worker, about 4.3-6.0 s instead of
  10.5-12.9 s (about 2.3x). Density-map runs are not affected. Outputs are
  bit-identical to 0.3.1: the golden gate and all six synthetic-benchmark
  digests are unchanged.

## [0.3.1] - 2026-09-29

Found by automating the GUI checks of `scripts/fiji-smoke/README.md` in a
real Fiji window, and by a review of the 0.3.0 changes. Measurement outputs
(tables, maps, density values) are unchanged.

### Fixed

- In Fiji, density maps now open with the viridis lookup table and 3D
  territory stacks with glasbey. 0.3.0 looked these up as ImageJ 1 commands,
  which they are not in current Fiji, so it always fell back to `Fire` and
  `3-3-2 RGB`. The tables are now read from Fiji's `luts` folder; the fallbacks
  remain for plain ImageJ.
- The count and size density maps of one label type opened as two windows
  with the same title (for example `A_Field_Density` twice). Their titles now
  name the weighting (`A_Field_object_count_Density`,
  `A_Field_object_size_Density`), as the saved file names already did.
- Two region ROIs with the same name no longer make the second region's
  Objects, Interactions and Regularity tables replace the first's on screen;
  repeated names are shown as `name (2)`. Saved files were already distinct.
- Escape now stops a run part-way through a step, within a fraction of a
  second, instead of after the density map or 3D territory assignment in
  progress had finished (which could take minutes on large images). The
  engine polls for Escape per image row, slice, kernel, object and
  permutation; in the GUI check a 2400 x 2400 density map that used to run
  on for 8.9 s after Escape now stops within milliseconds.
- Pressing Escape during the last step of a run now cancels it. Previously
  that step finished and the results were shown and saved anyway.
- The Macro Recorder no longer keeps a `run("Object Territories", ...)` line
  for a dialog run that fails or is cancelled with Escape, or whose dialog
  values are rejected; a failed batch records nothing either. Lines for runs
  that complete are unchanged and replay as before.
- A region ROI file that cannot be read (for example one that was moved)
  now gives a message instead of an exception window.
- The status bar no longer reads "done (press Esc to stop)" after a run.
- A folder batch keeps files of one sample together when their extensions
  differ only in case or `tif`/`tiff` (`S1_nuclei.TIF` with
  `S1_microglia.tif`). Since the default pattern became case-insensitive in
  0.3.0 they were split into separate one-type samples.
- The dialog's random seed is a text field, so seeds beyond 2^53 are used
  exactly as typed instead of being rounded.

### Changed

- Embeds `territories-core` 0.2.1, which adds the cancellation check the
  long loops poll. Outputs are bit-identical to 0.3.0: the golden gate and
  all six synthetic-benchmark digests are unchanged.
- Java API: `ProgressMonitor.isCancelled()` is now also polled during each
  step, many times and from worker threads, so implementations must be
  thread-safe and cheap. A monitor that never cancels behaves as before.

## [0.3.0] - 2026-09-29

### Added

- Progress in Fiji's status bar and Escape to stop, for single runs and folder
  batches. Java callers get the same through the new
  `territories.api.ProgressMonitor`, `AnalysisCancelledException`, the
  overloads `ObjectTerritories.analyze(parameters, monitor)` /
  `analyze3D(parameters, monitor)` and
  `ObjectTerritoriesBatchRunner.run(parameters, monitor)`. The one-argument
  methods are unchanged and give identical output. A stopped batch records
  the remaining groups as `CANCELLED` and still saves its manifest.
- 3D folder batch with per-sample region-mask stacks (`dimensions=3D`; the
  group file whose type is `region_mask_type`, default `mask`, is the region
  mask). Java callers use `ObjectTerritoriesBatchParameters.builder3D(...)`.
  2D batches and macros recorded before this change behave exactly as before.

### Changed

- Extracted the territory, density and interaction engine into
  `io.github.jay2owe:territories-core` (0.2.0 in this release), so sibling
  plugins can compile it in without the user installing Object Territories.
  The core is shaded into
  this plugin's single JAR and relocated back onto `territories.core`, the
  package it already occupied, so **every published Java signature in the
  shipped JAR is unchanged**.
- One narrow source-compatibility change for Java callers, unavoidable and
  deliberate: `DensityResult.getWeighting()` and `getBoundaryMode()` (and their
  3D counterparts) now return `territories.core.DensityWeighting` and
  `territories.core.DensityBoundaryMode` rather than the `territories.api`
  enumerations of the same names. The `territories.api` enumerations are
  unchanged and remain what every parameter setter and macro option takes.
  Macro users are unaffected.

### Fixed

- The batch command now runs headless in Fiji. Fiji's headless dialog ignores
  folder and file fields, so every later text setting was read from the wrong
  field and the run failed before discovering any file.
- A headless run that fails now logs one `[Object Territories] ERROR:
  <message>` line and stops the calling macro. Previously Fiji printed the
  full stack trace to standard output and the macro carried on as if the
  command had succeeded.
- Result display no longer fails with "Unrecognized command" in plain ImageJ,
  where the Fiji lookup tables `mpl-viridis` and `glasbey` do not exist; it
  falls back to ImageJ's built-in `Fire` and `3-3-2 RGB`.
- The "not spatially calibrated" warning is logged on every run, including
  headless and hidden-results runs, and recorded in the batch manifest for
  processed samples; it used to appear only with result windows.
- Bad input to the batch command shows a message instead of a stack-trace
  window.
- The batch preview's Back button returns to the settings with the values just
  entered; it used to end the command.
- The default batch filename pattern is case-insensitive, so `.TIF` files are
  found. Recorded macros keep the pattern they recorded.
- Leaving the 2D region ROI field empty in the dialog now says "choose a
  region ROI .roi or .zip file for 2D label images" instead of opening an
  exception window with a stack trace.
- An image title (or path) containing `[`, `]` or a quote now gives a clear
  "rename the image" message instead of an internal-error stack trace.
- When two open images share a title, the command no longer silently analyses
  the first one; it asks for unique titles (dialog and macro paths).
- The "no images open" message no longer says only 2D images are accepted; 3D
  stacks are too.
- A run with result windows hidden and no output directory is rejected up
  front instead of computing everything and discarding it.
- Permutation counts above 1,000,000 are rejected with a message
  (`ObjectTerritoriesParameters.MAX_PERMUTATIONS`) instead of running out of
  memory; applies to the dialog, macros, the batch and the Java builders.
- RGB colour label images and region masks are rejected; their packed colours
  were previously read as label values.
- 3D stacks of more than 2,147,483,639 voxels are rejected with a message
  instead of failing inside the engine's array indexing.
- 2D density maps now have the same memory guard as 3D, so a request that
  cannot fit in Fiji's memory is refused with an estimate instead of crashing
  part-way through.
- The batch manifest is now saved as `Batch_Manifest.csv` in the output
  directory on every run, with a new `Dimensions` column; previously it was
  only shown in a window, so headless batches lost every per-sample outcome.

### Performance

- Density maps computed in parallel (territories-core 0.2.0); 47.6 s -> 8.9 s
  for a 1024 x 1024 image and 217 s -> 48 s for a 384 x 384 x 64 stack with
  automatic-bandwidth density maps on the synthetic benchmark; outputs
  bit-identical to 0.2.0 (golden gate and benchmark digests).

### Build

- Build aligned with current Fiji: parent `pom-scijava` 43.0.0 (ImageJ
  1.54p, the version Fiji ships), a Maven wrapper, and a green CI that builds
  both pinned cores first. The golden-master gate is bit-identical under the
  new parent. The jar manifest no longer carries a `Class-Path` line naming
  jars that are shaded in and never present in Fiji.
- Gated by a golden-master equivalence harness
  (`src/test/java/territories/equivalence/`) captured from the pre-extraction
  build: 706 two-dimensional, 444 three-dimensional and 71 rejection-message
  cases across the full option cross-product. **Zero differences, compared as
  raw IEEE-754 bit patterns.**
- A headless Fiji smoke test (`scripts/fiji-smoke/`) installs the built jar
  into a throwaway Fiji and runs both commands, 2D and 3D, end to end.

## [0.2.0] - 2026-08-07

- Added a first-class 2D folder-batch command and Java API with regex-based
  grouping, recursive discovery, preview, per-sample output, and a manifest.
- Adopted `io.github.jay2owe:oc3d-core:0.1.0` for the shared folder and regex
  mechanics. It is privately relocated to `territories.internal.core` inside
  the single installable plugin JAR.
- Added an exact-tag CI bootstrap plus isolated packaged-runtime checks for the
  core, JTS, ImageJ exclusion, plugin entries, licence, and build provenance.

[0.3.2]: https://github.com/Jay2owe/ObjectTerritories/releases/tag/v0.3.2
[0.3.1]: https://github.com/Jay2owe/ObjectTerritories/releases/tag/v0.3.1
[0.3.0]: https://github.com/Jay2owe/ObjectTerritories/releases/tag/v0.3.0
[0.2.0]: https://github.com/Jay2owe/ObjectTerritories/releases/tag/v0.2.0
