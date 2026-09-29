// One genuine 3D run: two label stacks and an open region-mask stack.
work = getArgument();
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);
open(work + "single/cells3d.tif");
open(work + "single/plaques3d.tif");
open(work + "single/mask3d.tif");
out = work + "out3d/";
run("Object Territories",
    "mode=both label1=[cells3d.tif] label2=[plaques3d.tif] region_mask=[mask3d.tif] " +
    "region_mode=independent edge_cells=include_flagged density_weighting=both " +
    "boundary=corrected bandwidth=auto permutations=50 seed=12345 " +
    "output=[" + out + "] hide_results");

ok = true;
objects = getFileList(out + "Objects/");
maps = getFileList(out + "Maps/");
density = getFileList(out + "Density/");
interactions = getFileList(out + "Interactions/");
if (objects.length != 1 || !endsWith(objects[0], "_Objects_3D.csv")) {
    print("SMOKE FAIL 3d: objects table missing");
    ok = false;
}
if (maps.length != 1 || !endsWith(maps[0], "_Territories_3D.tif")) {
    print("SMOKE FAIL 3d: territory stack missing");
    ok = false;
}
if (interactions.length != 2) {
    print("SMOKE FAIL 3d: expected interactions and regularity tables, found " + interactions.length);
    ok = false;
}
if (density.length != 4) {
    print("SMOKE FAIL 3d: expected 4 density stacks, found " + density.length);
    ok = false;
}
if (ok) {
    open(out + "Maps/" + maps[0]);
    if (nSlices != 6) {
        print("SMOKE FAIL 3d: territory stack has " + nSlices + " slices, expected 6");
        ok = false;
    }
}
if (ok) print("SMOKE PASS 3d");
