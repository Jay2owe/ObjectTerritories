// A bad macro option must end in one clear log line, not a stack trace.
work = getArgument();
if (!endsWith(work, "/")) work = work + "/";
setBatchMode(true);
open(work + "single/A.tif");
run("Object Territories",
    "label1=[A.tif] regions=[" + work + "region.roi] permutations=0 output=[" + work + "err1]");
print("SMOKE UNEXPECTED: the bad option was accepted");
