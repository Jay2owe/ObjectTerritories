// A label image that is not open must be named in a clear log line.
work = getArgument();
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);
run("Object Territories",
    "label1=[Not open] regions=[" + work + "region.roi] output=[" + work + "err2]");
print("SMOKE UNEXPECTED: a missing image was accepted");
