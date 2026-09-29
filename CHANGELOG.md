# Changelog

## Unreleased

- Added: 3D folder batch with per-sample region-mask stacks
  (`dimensions=3D`; the group file whose type is `region_mask_type`, default
  `mask`, is the region mask). Java callers use
  `ObjectTerritoriesBatchParameters.builder3D(...)`. 2D batches and macros
  recorded before this change behave exactly as before.
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
