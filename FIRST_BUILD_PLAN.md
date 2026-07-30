# 04 — Object Territories

**Name settled 2026-07-30.** Names the stronger, more novel half; the density and hotspot engines
stay in the plugin but not in the name — the precedent set by plugin 03, where texture is a feature
family rather than a co-headline.

## Naming and identity

| Item | Value |
|---|---|
| Display name | Object Territories |
| Menu entry | `Plugins > Object Territories` |
| Macro command | `run("Object Territories", "...")` |
| GitHub repo | `github.com/Jay2owe/ObjectTerritories` |
| Update site | `https://sites.imagej.net/Object-Territories/` |
| Maven groupId | `io.github.jay2owe` |
| Maven artifactId | `Object_Territories` |
| Java package | `territories` |
| Entry class | `Object_Territories.java` |
| Built jar | `Object_Territories-<version>.jar` |
| Licence | BSD 3-Clause |

Citation line:

> Malcolm, J. (2026). Object Territories (v1.0.0) [Software].
> GitHub. https://github.com/Jay2owe/ObjectTerritories

Methods-section form:

> Voronoi territories and neighbourhood enrichment were computed using the Object Territories
> plugin (v1.0.0).

**Name collision check — CLEARED 2026-07-30**

| Namespace | Result |
|---|---|
| `imagej/list-of-update-sites` (329 sites) | free — zero hits for *voronoi*, *delaunay*, *tessellat*, *density*, *heatmap*, *neighbourhood*, *neighborhood*, *territor* |
| `https://sites.imagej.net/Object-Territories/` | free — HTTP 404 |
| `https://imagej.net/plugins/object-territories` | free — HTTP 404 |
| GitHub | free — no results |

## Boundary against plugin 02 (Object Proximity Analysis) — SETTLED

**Positions versus space.**

- **02 measures distances between object positions**, and tests point patterns over those positions
  — nearest neighbour in all modes, Ripley's K/L, G, F, pair correlation, and their bivariate forms.
- **04 partitions space** into territories and maps density over it.

Consequences, so neither plan drifts:

| Feature | Owner |
|---|---|
| Distance to region boundary / line ROIs | **02** |
| Nearest-neighbour distance in any mode | **02** |
| Ripley, G, F, pair correlation | **02** |
| Voronoi territory area and neighbour counts | **04** |
| Interaction matrix / neighbourhood enrichment | **04** |
| KDE density surface and hotspots | **04** |
| Per-object local density (feature column) | **04**, and feeds plugin 06 |

Both READMEs should state this line explicitly and link to each other, so a user landing on either
knows immediately whether they have the right plugin.

**Merged plugin** — supersedes the former separate "Voronoi Tessellation" and "Density and Hotspot
Maps" plans.
**Merged plugin** — supersedes the former separate "Voronoi Tessellation" and "Density and Hotspot
Maps" plans. Neither was thick enough alone; together they are a substantial release with one
coherent question.

## Goal

Describe how objects are organised across a field, by two complementary routes:

- **Territories** — partition the field into Voronoi cells, report each object's territory area and
  neighbour count, and test which object types neighbour which more often than chance.
- **Density** — turn object positions into a continuous kernel density surface, and identify
  hotspots and coldspots that exceed a randomised null.

One is discrete and per-object, the other continuous and per-field. Both answer "where do objects
sit relative to each other and to the tissue" — which is why they belong in one plugin.

## Case strength: 4 of 6 — REVISED UP 2026-07-30 after checking the incumbents

Unlike plugins 02 and 03, where checking the incumbents weakened the case, checking here
**strengthened** it. The gap is larger than the first draft assumed.

### Verified: the Fiji namespace is empty

Searched `imagej/list-of-update-sites` (commit `58b1ff6`, 329 sites) for all of: *voronoi*,
*delaunay*, *tessellat*, *density*, *heatmap*, *heat map*, *neighbourhood*, *neighborhood*,
*territor*, *architect*.

**Zero hits on every term.** No update site in the entire ImageJ ecosystem ships Voronoi
tessellation, Delaunay analysis, density mapping, or neighbourhood analysis.

### Verified: the built-in plugin is visualisation only

Fiji ships a Delaunay/Voronoi plugin (VIB repository), checked against
`imagej.net/plugins/delaunay-voronoi`:

- Operates on **point selections**, not label images.
- Produces **visual output** — draws the diagram, replaces the point selection with a line ROI, or
  creates an overlay.
- Quantitative output is limited to an optional **mean distance** value.
- **No territory areas. No neighbour counts. No spatial statistics. No permutation testing. No
  multi-channel or multi-type interaction analysis. No batch.**

So the built-in command draws the picture; it does not measure it. Everything this plugin exists to
produce — per-object territory area, neighbour counts and composition, a permutation-tested
interaction matrix, regularity indices — is absent from the ecosystem entirely.

### What that means for the pitch

The interaction matrix is the strongest single item: it is the same construct spatial
transcriptomics calls neighbourhood enrichment, and there is no ImageJ implementation at all.
Reviewers familiar with Squidpy-style neighbourhood-enrichment plots will recognise it immediately,
and the honest claim is simply "this does not exist in Fiji" — no hedging required, unlike plugins
02 and 03.

Density is the weaker half and should be positioned as supporting. A KDE surface is something users
can already approximate with a Gaussian blur; what they cannot do is test it. The differentiator
there is the **hotspot significance test**, not the map.

The two halves belong together because they share the region-ROI input, the randomisation
machinery, and the map-output layer — and because separately each is a thin release.

## Inputs needed

| Input | Required | Notes |
|---|---|---|
| 1–5 label images | yes | multiple channels become the "types" in the interaction matrix |
| ROI `.zip` region set | **load-bearing** | bounds the tessellation and defines the density domain; edge Voronoi cells are otherwise unbounded and density outside the object envelope is undefined |
| Voxel calibration | important | territory areas in µm², bandwidth as a physical distance |
| Raw intensity images | no | not used |

Boundary handling is the scientifically critical input concern for both halves. Edge cells and
edge density must be flagged or excluded by explicit user choice, never silently reported.

## Outputs

**Territory half**

- Per-object table: territory area, neighbour count, neighbour labels, neighbour channel
  composition, edge-cell flag.
- Interaction matrix: observed adjacency counts per channel pair with two-tailed permutation *p*.
- Regularity index: territory-area coefficient of variation and mean NN distance / SD — the
  standard mosaic readout.
- Tessellation overlay coloured by area or by channel.

**Density half**

- Calibrated density map per channel with a perceptually uniform LUT and calibration bar.
- Difference map between two channels — where is A dense relative to B?
- Hotspot and coldspot masks against a randomised null, with a hotspot table (area, peak density,
  object count, centroid).
- Per-object local density column, which feeds plugin 06 as a feature.

Auto-save tree: `Objects/`, `Interactions/`, `Density/`, `Hotspots/`, `Maps/`.

## Functionality to match (the CPC standard)

All ten points. Specific to this plugin:

- Permutation and simulation counts settable, seed settable and **recorded in the output**.
- KDE bandwidth settable with an automatic default, and the chosen value always written out — an
  unstated bandwidth makes a density map uninterpretable.
- Batch must keep density scaling comparable across images (explicit shared-scale mode), and must
  aggregate interaction matrices by summing counts and re-testing, never by averaging *p* values.
- Edge handling is a visible user choice, not a default.

## Reference style from CPC

Same chassis as the rest of the family — see `00_PORTFOLIO_OVERVIEW.md`. The Analysis section
becomes two grouped blocks (Territories / Density), each independently toggleable, sharing the
region-ROI picker, permutation count and seed. Image outputs are more prominent here than anywhere
else in the family, so the `Maps/` tree needs consistent, predictable naming.

## Source material in FLASH

Internal FLASH classes, not standalone plugins.

| FLASH source | Lines | What it gives |
|---|---|---|
| `spatial/VoronoiAnalysis.java` | 269 | `compute()` → per-object territory area, neighbour count, neighbour indices; `computeInteractionMatrix()` with permutation *p* values |
| `spatial/DensityHeatmapGenerator.java` | 334 | KDE engine, bandwidth handling, LUT application |
| `analyses/SpatialAnalysis.java` L4986 `runDensityHeatmaps()` | — | exposure pattern; bandwidth 0 means auto |
| `analyses/SpatialAnalysis.java` `doVoronoi` + `jtsLock` | — | JTS soft-gating |
| `intensity/spatial/HotspotScanAnalysis.java` | 397 | hotspot scanning and significance pattern (written for intensity; adapts to object density) |
| `intensity/spatial/NullModelAnalysis.java` | 150 | randomised-null pattern |
| `spatial/SpatialStatistics.java` `monteCarloEnvelopes` | — | randomisation machinery shared with plugin 02 |
| `runtime/DependencyRegistry.java` | — | optional-dependency gating pattern |

## New beyond FLASH

1. **Standalone existence and batch mode** for both halves.
2. **Hotspot significance testing** — FLASH produces density maps but never tests them. An untested
   hotspot is a blurred image; a tested one is a result.
3. **Difference maps** between channels, with their own null model.
4. **Delaunay neighbourhood graph metrics** — free once the tessellation exists: degree
   distribution, edge lengths, graph clustering coefficient.
5. **Regularity index** for mosaic analysis.
6. **Territory-based colocalisation** — does a partner object fall inside this object's *territory*
   rather than inside the object itself? A defensible association measure for sparse markers that
   never overlap, and a genuine contribution back into plugin 06.
7. **Per-object local density** written back as a feature column.
8. **Comparable scaling across a batch.**

v0.1.0: items 1, 5, 7. v0.2.0: items 2, 3, 4, 8. v0.3.0: item 6.
Item 2 should be pulled into v0.1.0 if the schedule allows — it is most of the density half's case.

## Dependencies

**JTS (`jts-core`)** for the tessellation — the only third-party requirement anywhere in the family.
Options: shade it into the jar (~1–2 MB, preserves the single-jar install), depend on Fiji's copy
with soft-gating, or write a bounded 2D Fortune's algorithm (~500 lines, testable).

Recommendation: **shade JTS.** The density half stays `ij`-only regardless, so if shading proves
awkward the plugin can ship density-first with territories following.

## Pros

- Merged, it is a real release: two engines, one question, shared inputs and machinery.
- Both engines already exist and are small.
- Strongest visual outputs in the family — overlays and heatmaps sell themselves.
- Shares null-model machinery with plugin 02, so building it after 02 is cheap.
- Territory-based colocalisation is a novel idea no existing coloc tool offers.

## Cons

- Voronoi is fundamentally 2D here. 3D tissue users will ask, and "we tessellate a projection" is a
  real limitation that must be stated plainly.
- Edge effects are severe for both halves and unavoidable: in small fields, most objects may be
  edge objects.
- Voronoi needs reasonably dense, well-separated objects — on sparse or heavily clustered data a
  few huge cells dominate and the output misleads.
- KDE bandwidth drives the entire appearance of the result, and users will tune until the figure
  looks right. Always recording the value is a mitigation, not a cure.
- 3D density maps are memory-heavy and hard to display; likely 2D-only in practice.
- Two halves under one name reintroduces a mild version of the "does too much" risk — the naming
  pass must find a name that covers both without being vague.

## First build (v0.1.0) scope

**Scope override — 2026-07-30:** true voxel-based 3D territories and density
were subsequently requested for v0.1.0. This supersedes “3D anything” in the
original out-of-scope sentence below. Three-dimensional inputs use matching
label stacks plus a positive-integer region-mask stack; they are never silently
projected into 2D.

In: label + region-ROI input, 2D Voronoi bounded by the region, per-object territory area and
neighbour counts, interaction matrix with permutation testing, regularity index, edge-cell
handling, tessellation overlay, 2D KDE with manual and automatic bandwidth, calibrated density maps
with calibration bar, per-object local density, batch with shared density scaling and matrix
aggregation, auto-save, macro options, Java API, JUnit tests against known configurations (a
regular lattice has known territory areas, neighbour counts and density).

Out: hotspot significance, difference maps, Delaunay metrics beyond neighbour counts,
territory-based colocalisation, 3D anything.

## Open questions

- Shade JTS or write Fortune's algorithm?
- Should the two halves be independently runnable from the macro API (they should) and independently
  named in the menu (probably one entry with a mode)?
- Object-count density or volume-weighted density? They answer different questions; both defensible.
- For 3D input, project to 2D or refuse? Projecting silently is the worst option.
