# Changelog

## Unreleased

- Added: progress in Fiji's status bar and Escape to stop, for single runs
  and folder batches. Java callers get the same through the new
  `territories.api.ProgressMonitor`, `AnalysisCancelledException`, the
  overloads `ObjectTerritories.analyze(parameters, monitor)` /
  `analyze3D(parameters, monitor)` and `ObjectTerritoriesBatchRunner.run(
  parameters, monitor)`. The one-argument methods are unchanged and give
  identical output. A stopped batch records the remaining groups as
  `CANCELLED` and still saves its manifest.
- Added: 3D folder batch with per-sample region-mask stacks
  (`dimensions=3D`; the group file whose type is `region_mask_type`, default
  `mask`, is the region mask). Java callers use
  `ObjectTerritoriesBatchParameters.builder3D(...)`. 2D batches and macros
  recorded before this change behave exactly as before.
- Fixed: the batch command now runs headless in Fiji. Fiji's headless dialog
  ignores folder and file fields, so every later text setting was read from
  the wrong field and the run failed before discovering any file.
- Fixed: a headless run that fails now logs one
  `[Object Territories] ERROR: <message>` line and stops the calling macro.
  Previously Fiji printed the full stack trace to standard output and the
  macro carried on as if the command had succeeded.
- Fixed: result display no longer fails with "Unrecognized command" in plain
  ImageJ, where the Fiji lookup tables `mpl-viridis` and `glasbey` do not
  exist; it falls back to ImageJ's built-in `Fire` and `3-3-2 RGB`.
- Fixed: the "not spatially calibrated" warning is logged on every run,
  including headless and hidden-results runs, and recorded in the batch
  manifest for processed samples; it used to appear only with result windows.
- Fixed: bad input to the batch command shows a message instead of a
  stack-trace window.
- Fixed: the batch preview's Back button returns to the settings with the
  values just entered; it used to end the command.
- Fixed: the default batch filename pattern is case-insensitive, so `.TIF`
  files are found. Recorded macros keep the pattern they recorded.
- Fixed: leaving the 2D region ROI field empty in the dialog now says
  "choose a region ROI .roi or .zip file for 2D label images" instead of
  opening an exception window with a stack trace.
- Fixed: an image title (or path) containing `[`, `]` or a quote now gives a
  clear "rename the image" message instead of an internal-error stack trace.
- Fixed: when two open images share a title, the command no longer silently
  analyses the first one; it asks for unique titles (dialog and macro paths).
- Fixed: the "no images open" message no longer says only 2D images are
  accepted; 3D stacks are too.
- Fixed: a run with result windows hidden and no output directory is rejected
  up front instead of computing everything and discarding it.
- Fixed: permutation counts above 1,000,000 are rejected with a message
  (`ObjectTerritoriesParameters.MAX_PERMUTATIONS`) instead of running out of
  memory; applies to the dialog, macros, the batch and the Java builders.
- Fixed: RGB colour label images and region masks are rejected; their packed
  colours were previously read as label values.
- Fixed: 3D stacks of more than 2,147,483,639 voxels are rejected with a
  message instead of failing inside the engine's array indexing.
- Fixed: 2D density maps now have the same memory guard as 3D, so a request
  that cannot fit in Fiji's memory is refused with an estimate instead of
  crashing part-way through.
- Fixed: the batch manifest is now saved as `Batch_Manifest.csv` in the output
  directory on every run, with a new `Dimensions` column; previously it was
  only shown in a window, so headless batches lost every per-sample outcome.
- Build aligned with current Fiji: parent `pom-scijava` 43.0.0 (ImageJ
  1.54p, the version Fiji ships), a Maven wrapper, and a green CI that builds
  both pinned cores first. The golden-master gate is bit-identical under the
  new parent. The jar manifest no longer carries a `Class-Path` line naming
  jars that are shaded in and never present in Fiji.
- Extracted the territory, density and interaction engine into
  `io.github.jay2owe:territories-core:0.1.0`, so sibling plugins can compile it
  in without the user installing Object Territories. The core is shaded into
  this plugin's single JAR and relocated back onto `territories.core`, the
  package it already occupied, so **every published Java signature in the
  shipped JAR is unchanged**.
- Gated by a golden-master equivalence harness
  (`src/test/java/territories/equivalence/`) captured from the pre-extraction
  build: 706 two-dimensional, 444 three-dimensional and 71 rejection-message
  cases across the full option cross-product. **Zero differences, compared as
  raw IEEE-754 bit patterns.**
- One narrow source-compatibility change for Java callers, unavoidable and
  deliberate: `DensityResult.getWeighting()` and `getBoundaryMode()` (and their
  3D counterparts) now return `territories.core.DensityWeighting` and
  `territories.core.DensityBoundaryMode` rather than the `territories.api`
  enumerations of the same names. The `territories.api` enumerations are
  unchanged and remain what every parameter setter and macro option takes.
  Macro users are unaffected.

## [0.2.0] - 2026-08-07

- Added a first-class 2D folder-batch command and Java API with regex-based
  grouping, recursive discovery, preview, per-sample output, and a manifest.
- Adopted `io.github.jay2owe:oc3d-core:0.1.0` for the shared folder and regex
  mechanics. It is privately relocated to `territories.internal.core` inside
  the single installable plugin JAR.
- Added an exact-tag CI bootstrap plus isolated packaged-runtime checks for the
  core, JTS, ImageJ exclusion, plugin entries, licence, and build provenance.

[0.2.0]: https://github.com/Jay2owe/ObjectTerritories/releases/tag/v0.2.0
