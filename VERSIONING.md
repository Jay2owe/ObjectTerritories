# Versioning

Object Territories uses semantic versioning:

- patch releases fix behaviour without intentionally changing outputs;
- minor releases add backward-compatible measurements, options or commands;
- major releases may change defaults, column meaning, file layout, macro
  options or the Java API.

During development, Maven builds use `-SNAPSHOT`. A release removes that
suffix, updates `CHANGELOG.md` and `CITATION.cff`, and tags the matching
version as `vX.Y.Z`; the GitHub Release for that tag carries the jar.

Scientific output changes must be called out explicitly in `CHANGELOG.md`,
including any change to territory assignment, neighbour rules, the
permutation test, density normalisation or bandwidth selection, column
definitions, or anything that can alter object counts. The golden-master
gate (`src/test/java/territories/equivalence/`) fails on any such change;
its goldens are never regenerated to hide a difference.

The embedded engine, `territories-core`, is versioned separately. A plugin
release names the core version it bundles in its changelog entry.
