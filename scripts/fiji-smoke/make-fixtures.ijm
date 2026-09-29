// Builds every synthetic input the smoke macros need, under the work
// directory passed as the macro argument. No binary fixtures are stored.
work = getArgument();
if (work == "") exit("usage: make-fixtures.ijm <work-dir>");
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);

function calibrate() {
    run("Properties...", "channels=1 slices=" + nSlices + " frames=1 unit=um pixel_width=0.5 pixel_height=0.5 voxel_depth=2");
}

function blob(label, x, y) {
    setColor(label);
    fillRect(x, y, 3, 3);
}

// Two 2D label images, two object types, eight objects each.
function labels2D(title, offset, path) {
    newImage(title, "16-bit black", 200, 200, 1);
    label = 1;
    for (row = 0; row < 3; row++) {
        for (column = 0; column < 3; column++) {
            if (row * 3 + column == 8) continue;
            blob(label, 20 + column * 60 + offset, 20 + row * 60 + offset);
            label++;
        }
    }
    calibrate();
    saveAs("Tiff", path);
    close();
}

// A 3D label stack with objects on several slices, and its region mask.
function labels3D(path, shift) {
    newImage("labels3d", "16-bit black", 40, 40, 6);
    label = 1;
    for (z = 1; z <= 6; z += 2) {
        setSlice(z);
        blob(label, 5 + shift, 5 + z * 4);
        label++;
        blob(label, 28 - shift, 30 - z * 3);
        label++;
    }
    calibrate();
    saveAs("Tiff", path);
    close();
}

function mask3D(path) {
    newImage("mask3d", "8-bit black", 40, 40, 6);
    setColor(1);
    for (z = 1; z <= 6; z++) {
        setSlice(z);
        fillRect(0, 0, 40, 40);
    }
    calibrate();
    saveAs("Tiff", path);
    close();
}

File.makeDirectory(work + "single");
labels2D("A", 0, work + "single/A.tif");
labels2D("B", 25, work + "single/B.tif");

// The shared 2D region: one named rectangle saved as an ImageJ .roi file.
newImage("roi-canvas", "8-bit black", 200, 200, 1);
makeRectangle(5, 5, 190, 190);
Roi.setName("Field");
saveAs("Selection", work + "region.roi");
close();

labels3D(work + "single/cells3d.tif", 0);
labels3D(work + "single/plaques3d.tif", 3);
mask3D(work + "single/mask3d.tif");

File.makeDirectory(work + "batch2d");
labels2D("s1_A", 0, work + "batch2d/s1_A.tif");
labels2D("s1_B", 25, work + "batch2d/s1_B.tif");
labels2D("s2_A", 10, work + "batch2d/s2_A.TIF");
labels2D("s2_B", 35, work + "batch2d/s2_B.TIF");

File.makeDirectory(work + "batch3d");
labels3D(work + "batch3d/b1_Cells.tif", 0);
mask3D(work + "batch3d/b1_mask.tif");
labels3D(work + "batch3d/b2_Cells.tif", 2);
labels3D(work + "batch3d/b2_Plaques.tif", 5);
mask3D(work + "batch3d/b2_mask.tif");

print("SMOKE PASS fixtures");
