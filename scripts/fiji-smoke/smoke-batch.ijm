// Folder batches in 2D (shared .roi) and 3D (per-sample *_mask stacks).
work = getArgument();
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);

function manifestRows(path, name, expectedRows) {
    if (!File.exists(path)) {
        print("SMOKE FAIL " + name + ": Batch_Manifest.csv missing");
        return false;
    }
    lines = split(File.openAsString(path), "\n");
    if (lines.length != expectedRows + 1) {
        print("SMOKE FAIL " + name + ": expected " + expectedRows + " manifest rows, found " + (lines.length - 1));
        return false;
    }
    for (i = 1; i < lines.length; i++) {
        if (indexOf(lines[i], "PROCESSED") < 0) {
            print("SMOKE FAIL " + name + ": row not processed: " + lines[i]);
            return false;
        }
    }
    return true;
}

common = "analysis=BOTH multiple_region_rois=INDEPENDENT edge_cells=INCLUDE_FLAGGED " +
    "density_weighting=BOTH density_boundary=CORRECTED bandwidth_0_is_automatic=0 " +
    "permutations=50 random_seed=12345";

// The default pattern, recorded exactly as the dialog records it. The .TIF
// sample checks that the default is case-insensitive.
out2d = work + "batch2d-out/";
run("Object Territories Batch...",
    "input_folder=[" + work + "batch2d] filename_regex=[(?i)(.+)_([^_]+)\\.(?:tif|tiff)$] " +
    "label_type_capture_group=2 dimensions=2D region_mask_type=mask " +
    "region_roi_file_or_zip=[" + work + "region.roi] " + common +
    " output_directory=[" + out2d + "]");
if (manifestRows(out2d + "Batch_Manifest.csv", "batch2d", 2)
        && File.exists(out2d + "s1/Objects/Field_Objects.csv")
        && File.exists(out2d + "s2/Objects/Field_Objects.csv")) {
    print("SMOKE PASS batch2d");
} else {
    print("SMOKE FAIL batch2d: sample outputs missing");
}

out3d = work + "batch3d-out/";
run("Object Territories Batch...",
    "input_folder=[" + work + "batch3d] filename_regex=[(.+)_([^_]+)\\.tif] " +
    "label_type_capture_group=2 dimensions=3D region_mask_type=mask " +
    "region_roi_file_or_zip=[] " + common +
    " output_directory=[" + out3d + "]");
density3d = getFileList(out3d + "b2/Density/");
if (manifestRows(out3d + "Batch_Manifest.csv", "batch3d", 2)
        && File.exists(out3d + "b1/Maps")
        && density3d.length == 4) {
    print("SMOKE PASS batch3d");
} else {
    print("SMOKE FAIL batch3d: sample outputs missing");
}
