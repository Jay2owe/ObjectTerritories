// One recorded-style 2D run: two label images, a .roi region, auto-save.
work = getArgument();
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);
open(work + "single/A.tif");
open(work + "single/B.tif");
out = work + "out2d/";
run("Object Territories",
    "mode=both label1=[A.tif] label2=[B.tif] regions=[" + work + "region.roi] " +
    "region_mode=independent edge_cells=include_flagged density_weighting=both " +
    "boundary=corrected bandwidth=auto permutations=50 seed=12345 " +
    "output=[" + out + "] hide_results");

ok = true;
expected = newArray(
    "Objects/Field_Objects.csv",
    "Interactions/Field_Interactions.csv",
    "Interactions/Field_Regularity.csv",
    "Maps/Field_Territories.tif");
for (i = 0; i < expected.length; i++) {
    if (!File.exists(out + expected[i])) {
        print("SMOKE FAIL 2d: missing " + expected[i]);
        ok = false;
    }
}
density = getFileList(out + "Density/");
if (density.length != 4) {
    print("SMOKE FAIL 2d: expected 4 density maps, found " + density.length);
    ok = false;
}
objects = split(File.openAsString(out + "Objects/Field_Objects.csv"), "\n");
if (objects.length != 17) {
    print("SMOKE FAIL 2d: expected 16 object rows plus a header, found " + objects.length + " lines");
    ok = false;
}
if (ok) print("SMOKE PASS 2d");
