# Object Territories

Object Territories is a Fiji/ImageJ plugin for bounded Voronoi territory analysis,
neighbourhood interactions, and kernel density maps from labelled objects.

The first build is under active development. Its planned public command is:

```text
Plugins > Object Territories
```

The implementation is deliberately split into:

- a dialog and ImageJ macro entry point;
- a side-effect-free public Java API;
- geometry and density engines with focused unit tests.

See `FIRST_BUILD_PLAN.md` for the current scientific scope.

